//! Admission and held SDK for the exact retained Vox Cargo producer.
use super::engine::{SourceBuildProvenance, SourceBuildSystem};
use super::vox_producer_contract as policy;
use crate::cancellation::CancellationToken;
use crate::jdk_artifact_cache::VerifiedPinnedJbrsdk;
use crate::toolchain_lockfile_schema::version::v4::{JdkArtifactV4, JdkPinV4};
use eyre::{Result, ensure};
use facet::Facet;
use std::collections::BTreeMap;
use std::path::{Path, PathBuf};
use std::process::Command;

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct Spec {
    schema: String,
    coordinate: String,
    expected_release: String,
    platform_releases: BTreeMap<String, String>,
    source_remote: String,
    source_commit: String,
    pin: JdkPinV4,
}

pub(super) struct PreparedSdk {
    spec: Spec,
    selection: policy::Selection,
}

pub(super) struct SelectedSdk {
    image: VerifiedPinnedJbrsdk,
    root: PathBuf,
    pin: JdkPinV4,
    artifact: JdkArtifactV4,
    selection: policy::Selection,
}

fn checked_spec() -> Result<Spec> {
    let spec: Spec = facet_json::from_str(include_str!("vox_source_build_jdk.json"))?;
    ensure!(
        spec.schema == "sfm:source_build_jdk_pin@2"
            && spec.coordinate == policy::COORDINATE
            && spec.expected_release == policy::WINDOWS_RUNTIME
            && spec.source_remote == policy::REMOTE
            && spec.source_commit == policy::COMMIT
            && spec.platform_releases.len() == 2
            && spec.pin.artifacts.len() == 2,
        "Vox SDK catalogue differs from its reviewed exact source/recipe"
    );
    for platform in ["windows-x64", "linux-x64"] {
        let expected = policy::expected(platform).map_err(|error| eyre::eyre!(error))?;
        ensure!(
            spec.platform_releases.get(platform).map(String::as_str) == Some(expected.runtime),
            "Vox SDK catalogue runtime differs from its exact platform identity"
        );
        let artifacts = spec
            .pin
            .artifacts
            .iter()
            .filter(|a| a.platform == platform)
            .collect::<Vec<_>>();
        ensure!(
            artifacts.len() == 1,
            "Vox SDK catalogue repeats or lacks a platform artifact"
        );
        let artifact = artifacts[0];
        policy::verify_pin(
            expected,
            &policy::PinIdentity {
                major: spec.pin.major,
                vendor: &spec.pin.vendor,
                version: &spec.pin.version,
                build: &spec.pin.build,
                flavor: &spec.pin.flavor,
                platform: &artifact.platform,
                url: &artifact.url,
                sha512: &artifact.sha512,
            },
        )
        .map_err(|error| eyre::eyre!(error))?;
    }
    Ok(spec)
}

/// No checkout, acquisition, Java probe or child environment mutation here.
pub(super) fn preflight(
    coordinate: &str,
    remote: &str,
    commit: &str,
    source: &SourceBuildProvenance,
) -> Result<Option<PreparedSdk>> {
    if !policy::needs_contract(coordinate, &source.environment)
        .map_err(|error| eyre::eyre!(error))?
    {
        return Ok(None);
    }
    ensure!(
        source.build_system == SourceBuildSystem::CargoCommand,
        "Vox SDK recipe requires the exact Cargo producer"
    );
    let spec = checked_spec()?;
    let selection = policy::select(
        crate::jdk::host_jbrsdk_platform().unwrap_or("unsupported-host"),
        &policy::Recipe {
            coordinate,
            remote,
            commit,
            tasks: &source.tasks,
            output: &source.output_path,
            environment: &source.environment,
        },
    )
    .map_err(|error| eyre::eyre!(error))?;
    if let Some(name) = policy::unexpected_ambient(|name| std::env::var_os(name)) {
        eyre::bail!(
            "Pinned Vox producer refuses ambient {name}; its value is not logged or erased"
        );
    }
    Ok(Some(PreparedSdk { spec, selection }))
}

impl PreparedSdk {
    pub(super) fn require_trust_support(&self, external_trust: bool) -> Result<()> {
        policy::require_supported_trust(external_trust).map_err(|error| eyre::eyre!(error))
    }

    pub(super) fn acquire(
        self,
        maven_cache: &Path,
        cancel: &CancellationToken,
    ) -> Result<SelectedSdk> {
        cancel.bail_if_cancelled()?;
        let artifact = self
            .spec
            .pin
            .artifacts
            .iter()
            .find(|artifact| artifact.platform == self.selection.platform)
            .ok_or_else(|| eyre::eyre!("Vox SDK selected artifact disappeared"))?
            .clone();
        let pin = self.spec.pin;
        let root = maven_cache
            .parent()
            .ok_or_else(|| eyre::eyre!("Source-build Maven cache has no parent"))?
            .join("jbrsdk");
        let image = crate::jdk_artifact_cache::install_verified_pinned_jbrsdk(
            &pin,
            &artifact,
            &root,
            false,
            crate::jdk_install_control::InstallControl {
                extraction_bytes_per_second: None,
                cancellation: Some(cancel),
            },
        )?;
        let mut sdk = SelectedSdk {
            image,
            root,
            pin,
            artifact,
            selection: self.selection,
        };
        sdk.recheck(cancel)?;
        crate::jdk::resolve_pinned_java_from_verified_home(
            sdk.image.home(),
            &sdk.pin,
            &sdk.artifact,
        )?;
        sdk.recheck(cancel)?;
        cancel.bail_if_cancelled()?;
        Ok(sdk)
    }
}

impl SelectedSdk {
    pub(super) fn configure(
        &mut self,
        command: &mut Command,
        cancel: &CancellationToken,
    ) -> Result<()> {
        self.recheck(cancel)?;
        if let Some(name) = policy::unexpected_ambient(|name| std::env::var_os(name)) {
            eyre::bail!(
                "Pinned Vox producer refuses ambient {name}; its value is not logged or erased"
            );
        }
        // These are declarations in the SFM recipe, not controls understood by
        // this exact Vox source. Only its supported effective key reaches it.
        policy::configure_child(self.selection, self.image.home(), command)
            .map_err(|error| eyre::eyre!(error))?;
        tracing::info!(
            producer_variant = self.selection.variant.name(),
            producer_platform = self.selection.platform,
            producer_runtime = self.selection.runtime,
            producer_archive_sha512 = self.selection.archive_sha512,
            producer_source_commit = policy::COMMIT,
            "prepared exact Vox child SDK selection; not a producer completion or JAR byte-equivalence receipt"
        );
        Ok(())
    }

    pub(super) fn recheck(&mut self, cancel: &CancellationToken) -> Result<()> {
        self.image.recheck_for_controlled(
            &self.root,
            &self.pin,
            &self.artifact,
            crate::jdk_install_control::InstallControl {
                extraction_bytes_per_second: None,
                cancellation: Some(cancel),
            },
        )?;
        let release = std::fs::read_to_string(self.image.home().join("release"))?;
        policy::verify_runtime(self.selection, &release).map_err(|error| eyre::eyre!(error))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn embedded_vox_catalogue_preserves_exact_source_and_platform_archive_pins() -> Result<()> {
        let spec = checked_spec()?;
        assert_eq!(spec.source_commit, policy::COMMIT);
        assert_eq!(spec.pin.artifacts.len(), 2);
        Ok(())
    }
}
