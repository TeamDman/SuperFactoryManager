//! Source ownership for the retained `NeoForm` host, separate from tool recipes.
//!
//! This interface cannot select dependencies, construct an invocation transfer,
//! or authorize a JVM launch. Development source identity remains bound to its
//! actual schema-4 profile rather than a borrowed released source owner.

use super::catalog_owned_project::CatalogOwnedProject;
use super::frozen_recipe_project::FrozenRecipeProject;
use super::native_project_target::NATIVE_DEPENDENCY_PROFILE;
use super::native_project_target::NativeProjectTarget;
use super::projection_catalog::ProjectionEnvironment;
use super::provenance::sha256;
use eyre::Result;
use eyre::ensure;

mod sealed {
    pub trait Sealed {}
}

/// Only checked source-owner implementations may enter the retained host.
pub(crate) trait NfrtProjectOwner: sealed::Sealed {
    fn project(&self) -> &CatalogOwnedProject;
    fn preparation_identity(&self) -> Result<String>;
    fn recheck_source(&self) -> Result<()>;
}

impl sealed::Sealed for FrozenRecipeProject {}

impl NfrtProjectOwner for FrozenRecipeProject {
    fn project(&self) -> &CatalogOwnedProject {
        self.project()
    }

    fn preparation_identity(&self) -> Result<String> {
        self.receipt().preparation_identity()
    }

    fn recheck_source(&self) -> Result<()> {
        self.recheck(false)
    }
}

/// Borrowed source capability; dependency/tool admission is a separate gate.
pub(crate) struct DevelopmentNfrtSourceOwner<'a> {
    project: &'a CatalogOwnedProject,
    target: NativeProjectTarget,
    identity: String,
}

impl<'a> DevelopmentNfrtSourceOwner<'a> {
    /// Bind only a genuine development context and its explicit native profile.
    /// No cache creation, synchronization, acquisition or execution occurs.
    pub(crate) fn from_checked(project: &'a CatalogOwnedProject, profile: &str) -> Result<Self> {
        let receipt = project.receipt();
        ensure!(
            receipt.environment == ProjectionEnvironment::Dev
                && receipt.loader == "neoforge"
                && profile == NATIVE_DEPENDENCY_PROFILE,
            "NeoForm development source requires its own development rust-toolchain profile"
        );
        let expected = match receipt.target_id.as_str() {
            "1.20.2" => ("1.20.2", 17),
            "1.20.3" => ("1.20.3", 17),
            "1.20.4" => ("1.20.4", 17),
            "1.21.0" => ("1.21", 21),
            "1.21.1" => ("1.21.1", 21),
            "26.1.2" => ("26.1.2", 25),
            _ => eyre::bail!("NeoForm development source has no captured target"),
        };
        ensure!(
            receipt.minecraft_version == expected.0 && receipt.java_major == expected.1,
            "NeoForm development source target/version/compiler mismatch"
        );
        let target = NativeProjectTarget::from_checked_project(project, profile)?;
        let identity = sha256(
            facet_json::to_string(&(
                "sfm:development_nfrt_source_owner@1",
                project.ownership_identity()?,
                &target.receipt,
            ))?
            .as_bytes(),
        );
        Ok(Self {
            project,
            target,
            identity,
        })
    }

    pub(crate) fn target(&self) -> &NativeProjectTarget {
        &self.target
    }
}

impl sealed::Sealed for DevelopmentNfrtSourceOwner<'_> {}

impl NfrtProjectOwner for DevelopmentNfrtSourceOwner<'_> {
    fn project(&self) -> &CatalogOwnedProject {
        self.project
    }

    fn preparation_identity(&self) -> Result<String> {
        Ok(self.identity.clone())
    }

    fn recheck_source(&self) -> Result<()> {
        self.target.recheck_with_project(self.project)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::catalog_owned_project::tests::Fixture;
    use std::fs;

    #[test]
    fn all_six_captured_profiles_retain_their_actual_minecraft_alias_and_source_identity()
    -> Result<()> {
        let cases: [(&str, &str, &str, &[u8]); 6] = [
            (
                "1.20.2",
                "1.20.2",
                "20.2.86",
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/1.20.2/schema-4.json"
                ),
            ),
            (
                "1.20.3",
                "1.20.3",
                "20.3.8-beta",
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/1.20.3/schema-4.json"
                ),
            ),
            (
                "1.20.4",
                "1.20.4",
                "20.4.231",
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/1.20.4/schema-4.json"
                ),
            ),
            (
                "1.21.0",
                "1.21",
                "21.0.143",
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/1.21.0/schema-4.json"
                ),
            ),
            (
                "1.21.1",
                "1.21.1",
                "21.1.206",
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/1.21.1/schema-4.json"
                ),
            ),
            (
                "26.1.2",
                "26.1.2",
                "26.1.2.72",
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/26.1.2/schema-4.json"
                ),
            ),
        ];
        let mut fixture = Fixture::new();
        let mut identities = std::collections::BTreeSet::new();
        let mut dependency_identities = std::collections::BTreeSet::new();
        for (index, (target, minecraft, loader, raw)) in cases.into_iter().enumerate() {
            fixture.set_project_file_for(
                target,
                "sfm-toolchain.lock.json",
                &format!("build/proof/{target}/neoform-lock.json"),
                raw,
                false,
            )?;
            fixture.set_project_file_for(
                target,
                "gradle.properties",
                &format!("build/proof/{target}/neoform.properties"),
                format!(
                    "minecraft_version={minecraft}\nneo_version={loader}\nmod_version=4.34.0\n"
                )
                .as_bytes(),
                false,
            )?;
            let key = Fixture::key(index + 4, "dev");
            fixture.publish(&key);
            let project = fixture.collect(&key)?.check_current()?;
            let owner =
                DevelopmentNfrtSourceOwner::from_checked(&project, NATIVE_DEPENDENCY_PROFILE)?;
            owner.recheck_source()?;
            assert_eq!(owner.target.receipt.minecraft_version, minecraft);
            assert_eq!(owner.target.receipt.lockfile_sha256, sha256(raw));
            assert!(identities.insert(owner.preparation_identity()?));
            let dependencies = std::sync::Arc::new(
                super::super::development_nfrt_dependencies::DevelopmentNfrtDependencies::from_checked(&owner, false)?
            );
            dependencies.recheck_source(&owner)?;
            assert_eq!(dependencies.raw_source_lock_bytes(), raw);
            assert_eq!(dependencies.effective_profile().schema_version, 3);
            assert!(
                dependency_identities
                    .insert(dependencies.receipt().request_catalog_identity.clone())
            );
            let runtime =
                dependencies.coordinate("net.neoforged:neoform-runtime:2.0.19:all", false)?;
            let pin = dependencies.artifact_pin(&runtime)?;
            assert_eq!(
                dependencies.exact_url(pin.url.as_deref().unwrap(), false)?,
                runtime
            );
            assert!(
                dependencies
                    .coordinate("net.neoforged:neoform-runtime:2.+:all", false)
                    .is_err()
            );
            assert!(
                dependencies
                    .coordinate("net.neoforged:neoform-runtime:2.0.19:all", true)
                    .is_err()
            );
            assert!(
                dependencies
                    .exact_url("https://unrecorded.invalid/tool.jar", false)
                    .is_err()
            );
            assert!(
                dependencies
                    .verify_artifact_bytes(&runtime, b"wrong exact bytes", false)
                    .is_err()
            );
            let supplement = super::super::nfrt_child_identity_supplement::NfrtChildIdentitySupplement::from_development(
                std::sync::Arc::clone(&dependencies), false)?;
            assert_eq!(supplement.receipt().source_lock_sha256, sha256(raw));
            assert_eq!(
                supplement.receipt().original_request_catalog_identity,
                dependencies.receipt().request_catalog_identity
            );
            assert_eq!(
                supplement.receipt().recipe_id,
                format!("sfm:development-native-inputs/{target}@1")
            );
            assert!(!supplement.receipt().native_execution_enabled);
            assert!(!supplement.source_requests(false)?.is_empty());
            #[cfg(windows)]
            {
                let java = crate::jdk::ResolvedJava {
                    executable: "synthetic-java-not-opened".into(),
                    home: None,
                    version_output: "fixture".to_owned(),
                    major_version: u32::from(supplement.receipt().minimum_tool_jvm),
                    selection: "fixture-no-runtime-attestation".to_owned(),
                    pin_url: None,
                    pin_sha512: None,
                };
                let export =
                    crate::jar_build::nfrt_launch_contract::NfrtExportPlan::prepare_development(
                        &owner,
                        &dependencies,
                        &supplement,
                        &java,
                        b"fixture-java-no-runtime-attestation",
                        "development-six-target-proof",
                        false,
                    )?;
                let declaration = export.declaration();
                assert_eq!(declaration.environment, "dev");
                assert_eq!(
                    declaration.preparation_identity,
                    owner.preparation_identity()?
                );
                assert_eq!(declaration.source_lock_sha256, sha256(raw));
                assert_eq!(declaration.minecraft_version, minecraft);
                assert_eq!(declaration.recipe_id, supplement.receipt().recipe_id);
                assert!(!declaration.ordered_dependency_rows.is_empty());
                for row in &declaration.ordered_dependency_rows {
                    assert!(row.original_dependency_index.is_none());
                    let request = dependencies.coordinate(&row.resolved_coordinate, false)?;
                    assert_eq!(
                        dependencies.artifact_pin(&request)?.hash.to_string(),
                        row.artifact_hash
                    );
                }
                assert!(!declaration.native_execution_enabled);
                assert!(!declaration.os_filesystem_attested);
                assert!(!declaration.functions.is_empty());
            }
            assert!(super::super::nfrt_child_identity_supplement::NfrtChildIdentitySupplement::from_development(dependencies, true).is_err());
            assert!(!project.project_root().join("build").exists());
        }
        assert_eq!(identities.len(), 6);
        assert_eq!(dependency_identities.len(), 6);
        Ok(())
    }

    #[test]
    fn captured_development_source_keeps_profile_identity_and_rejects_later_edits() -> Result<()> {
        let mut fixture = Fixture::new();
        let raw = include_bytes!(
            "../../../../minecraft/core-liquid-template/build/lockfiles/1.20.2/schema-4.json"
        );
        fixture.set_project_file_for(
            "1.20.2",
            "sfm-toolchain.lock.json",
            "build/proof/dev-neoform-lock.json",
            raw,
            false,
        )?;
        fixture.set_project_file_for(
            "1.20.2",
            "gradle.properties",
            "build/proof/dev-neoform.properties",
            b"minecraft_version=1.20.2\nneo_version=20.2.86\nmod_version=4.34.0\n",
            false,
        )?;
        let key = Fixture::key(4, "dev");
        fixture.publish(&key);
        let project = fixture.collect(&key)?.check_current()?;
        let owner = DevelopmentNfrtSourceOwner::from_checked(&project, NATIVE_DEPENDENCY_PROFILE)?;
        owner.recheck_source()?;
        assert_eq!(owner.project().receipt().projection_key, key);
        assert_ne!(owner.preparation_identity()?, project.ownership_identity()?);
        assert_eq!(owner.target.receipt.lockfile_sha256, sha256(raw));
        assert!(!project.project_root().join("build").exists());
        assert!(DevelopmentNfrtSourceOwner::from_checked(&project, "gradle").is_err());
        fs::write(
            project.project_root().join("sfm-toolchain.lock.json"),
            b"{}",
        )?;
        assert!(owner.recheck_source().is_err());
        Ok(())
    }

    #[test]
    fn source_boundary_does_not_admit_release_or_forge_development() -> Result<()> {
        let fixture = Fixture::new();
        for key in [Fixture::key(4, "release"), Fixture::key(0, "dev")] {
            fixture.publish(&key);
            let project = fixture.collect(&key)?.check_current()?;
            assert!(
                DevelopmentNfrtSourceOwner::from_checked(&project, NATIVE_DEPENDENCY_PROFILE)
                    .is_err()
            );
            assert!(!project.project_root().join("build").exists());
        }
        Ok(())
    }
}
