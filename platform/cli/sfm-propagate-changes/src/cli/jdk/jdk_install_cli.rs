//! Install only the exact SDK selected by a checked catalog project.
use crate::cancellation::CancellationToken;
use crate::jdk::{JdkSource, host_jbrsdk_platform, select_jdk_source};
use crate::jdk_install_control::InstallControl;
use crate::source_projection::catalog_owned_project::collect_catalog_project;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::source_projection::provenance::sha256;
use crate::toolchain_lockfile_schema::read_jdk_pins;
use eyre::ensure;
use facet::Facet;
use figue as args;
use std::path::{Component, Path, PathBuf};

#[derive(Facet, Debug)]
pub struct JdkInstallArgs {
    /// Current catalogued projection containing the exact authored SDK pin.
    #[facet(args::named)]
    pub project_root: PathBuf,
    /// Exact SDK major to select from that project's lock.
    #[facet(args::named)]
    pub major: u32,
    /// Absolute external SDK cache root; SDK data is under minecraft-toolchain/jbrsdk.
    #[facet(args::named)]
    pub cache_root: PathBuf,
    /// Require the exact managed archive to exist; never download in this mode.
    #[facet(default = false, args::named)]
    pub offline: bool,
    /// Optional Linux tar regular-file write cap in bytes/sec (1..1073741824).
    #[facet(default, args::named)]
    pub extract_bytes_per_second: Option<u64>,
}

#[derive(Facet, Debug)]
pub(crate) struct JdkInstallReport {
    schema: &'static str,
    projection: String,
    source_lock_sha256: String,
    major: u32,
    platform: String,
    archive_url: String,
    archive_sha512: String,
    cache_root: String,
    home: String,
    offline: bool,
    extraction_bytes_per_second: Option<u64>,
    validation: &'static str,
    runtime_execution: &'static str,
}

impl JdkInstallArgs {
    pub(crate) fn install(
        &self,
        cancel: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<JdkInstallReport> {
        ensure!(
            cfg!(target_os = "linux"),
            "SDK-only installation currently requires Linux; existing Windows SDK workflows are unchanged"
        );
        cancel.bail_if_cancelled()?;
        let project_root = if self.project_root.is_absolute() {
            self.project_root.clone()
        } else {
            invocation_dir.join(&self.project_root)
        };
        let (catalog, key) = CoreCatalog::for_project(&project_root)?;
        let project =
            collect_catalog_project(&catalog.repo_root, invocation_dir, &key)?.check_current()?;
        let selected = project.selected_input("sfm-toolchain.lock.json")?;
        let raw = selected.require_exact_copy()?;
        let pins = read_jdk_pins(std::str::from_utf8(raw)?)?
            .ok_or_else(|| eyre::eyre!("selected project has no pinned SDK catalog"))?;
        let platform =
            host_jbrsdk_platform().ok_or_else(|| eyre::eyre!("unsupported SDK platform"))?;
        let JdkSource::Pinned { pin, artifact } =
            select_jdk_source(None, Some(&pins), self.major, platform)?
        else {
            eyre::bail!("SDK installation requires an exact project pin");
        };
        let control = InstallControl {
            extraction_bytes_per_second: self.extract_bytes_per_second,
            cancellation: Some(cancel),
        };
        control.validate(platform.starts_with("linux-") && artifact.url.ends_with(".tar.gz"))?;
        external_root(&self.cache_root, project.repo_root())?;
        let cache = self.cache_root.join("minecraft-toolchain/jbrsdk");
        external_cache(&cache, project.repo_root())?;
        project.recheck()?;
        let mut image = crate::jdk_artifact_cache::install_verified_pinned_jbrsdk(
            pin,
            artifact,
            &cache,
            self.offline,
            control,
        )?;
        cancel.bail_if_cancelled()?;
        image.recheck_for_controlled(&cache, pin, artifact, control)?;
        project.recheck()?;
        external_root(&self.cache_root, project.repo_root())?;
        external_cache(&cache, project.repo_root())?;
        cancel.bail_if_cancelled()?;
        Ok(JdkInstallReport {
            schema: "sfm:managed_sdk_install@1",
            projection: key,
            source_lock_sha256: sha256(raw),
            major: pin.major,
            platform: platform.to_owned(),
            archive_url: artifact.url.clone(),
            archive_sha512: artifact.sha512.to_ascii_lowercase(),
            cache_root: self.cache_root.display().to_string(),
            home: image.home().display().to_string(),
            offline: self.offline,
            extraction_bytes_per_second: self.extract_bytes_per_second,
            validation: "managed_archive_receipt_full_tree_and_completion_seal",
            runtime_execution: "not_run",
        })
    }
}

fn external_cache(cache: &Path, repository: &Path) -> eyre::Result<()> {
    for directory in [
        cache.to_path_buf(),
        cache.join("archives"),
        cache.join("installs"),
        cache.join("locks"),
    ] {
        external_root(&directory, repository)?;
    }
    Ok(())
}

fn external_root(root: &Path, repository: &Path) -> eyre::Result<()> {
    ensure!(
        root.is_absolute() && root.parent().is_some(),
        "SDK cache root must be an absolute non-root path"
    );
    ensure!(
        root.components().all(|part| matches!(
            part,
            Component::Normal(_) | Component::RootDir | Component::Prefix(_)
        )),
        "SDK cache root requires normal path spelling"
    );
    ensure!(
        !root.starts_with(repository) && !repository.starts_with(root),
        "SDK cache root must be outside the source repository"
    );
    for ancestor in root.ancestors() {
        match std::fs::symlink_metadata(ancestor) {
            Ok(_) => {
                let checked =
                    crate::source_projection::candidate_lock::checked_directory(ancestor)?;
                ensure!(
                    checked == ancestor,
                    "SDK cache root requires exact canonical spelling"
                );
                for marker in [".sfm-native-cache-root.json", ".sfm-jbrsdk-receipt"] {
                    ensure!(
                        std::fs::symlink_metadata(ancestor.join(marker))
                            .is_err_and(|error| error.kind() == std::io::ErrorKind::NotFound),
                        "external SDK cache cannot be inside a native build root or installed SDK"
                    );
                }
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => (),
            Err(error) => return Err(error.into()),
        }
    }
    Ok(())
}

#[cfg(all(test, target_os = "linux"))]
#[path = "jdk_install_tests.rs"]
mod tests;
