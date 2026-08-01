use crate::jar_build::ArtifactLockfile;
use crate::toolchain_lockfile_schema::preflight_document::PreflightDocument;
use crate::toolchain_lockfile_schema::version::v1::ArtifactLockfileV1;
use crate::toolchain_lockfile_schema::version::v2::ArtifactLockfileV2;
use crate::toolchain_lockfile_schema::version::v2_migration::MigrationDiagnostic;
use crate::toolchain_lockfile_schema::version::v3::ArtifactLockfileV3;
use crate::toolchain_lockfile_schema::version::v3::SCHEMA_VERSION as V3_SCHEMA_VERSION;
use crate::toolchain_lockfile_schema::version::v4::ArtifactLockfileV4;
use crate::toolchain_lockfile_schema::version::v4::SCHEMA_VERSION as V4_SCHEMA_VERSION;
use eyre::Context;

pub(crate) const ENGINE_SCHEMA_VERSION: u32 = 2;
pub(crate) const LATEST_SCHEMA_VERSION: u32 = V4_SCHEMA_VERSION;

pub(crate) enum ToolchainLockfileDocument {
    V1(ArtifactLockfileV1),
    V2 {
        lockfile: ArtifactLockfileV2,
        migration_diagnostics: Vec<MigrationDiagnostic>,
    },
    V3(ArtifactLockfileV3),
    V4(ArtifactLockfileV4),
}

pub(crate) enum MigrationAnalysis {
    Legacy {
        source_schema_version: u32,
        diagnostics: Vec<MigrationDiagnostic>,
        candidate: Option<ArtifactLockfileV3>,
    },
    LegacyV3 {
        candidate: ArtifactLockfileV4,
    },
    CurrentV4(ArtifactLockfileV4),
}

pub(crate) fn parse_document(input: &str) -> eyre::Result<ToolchainLockfileDocument> {
    let preflight: PreflightDocument = facet_json::from_str(input)
        .wrap_err("failed to parse toolchain lockfile schema preflight")?;

    match preflight.schema_version {
        1 => {
            if input.contains("\"weak\"") {
                eyre::bail!(
                    "toolchain lockfile declares schema_version 1 but contains v2-only field `weak`; update schema_version to {ENGINE_SCHEMA_VERSION}"
                );
            }
            let lockfile: ArtifactLockfileV1 = facet_json::from_str(input)
                .wrap_err("failed to parse toolchain lockfile schema v1")?;
            Ok(ToolchainLockfileDocument::V1(lockfile))
        }
        ENGINE_SCHEMA_VERSION => {
            let lockfile: ArtifactLockfileV2 = facet_json::from_str(input)
                .wrap_err("failed to parse toolchain lockfile schema v2")?;
            let migration_diagnostics = lockfile.migration_diagnostics();
            Ok(ToolchainLockfileDocument::V2 {
                lockfile,
                migration_diagnostics,
            })
        }
        V3_SCHEMA_VERSION => {
            let lockfile: ArtifactLockfileV3 = facet_json::from_str(input)
                .wrap_err("failed to parse toolchain lockfile schema v3")?;
            lockfile.validate()?;
            Ok(ToolchainLockfileDocument::V3(lockfile))
        }
        V4_SCHEMA_VERSION => {
            let lockfile: ArtifactLockfileV4 = facet_json::from_str(input)
                .wrap_err("failed to parse toolchain lockfile schema v4")?;
            lockfile.validate()?;
            Ok(ToolchainLockfileDocument::V4(lockfile))
        }
        version if version > LATEST_SCHEMA_VERSION => eyre::bail!(
            "toolchain lockfile schema_version {version} is newer than supported schema_version {LATEST_SCHEMA_VERSION}"
        ),
        version => eyre::bail!(
            "toolchain lockfile schema_version {version} is older than the first supported schema_version 1"
        ),
    }
}

pub(crate) fn upgrade_to_latest(input: &str) -> eyre::Result<ArtifactLockfile> {
    match parse_document(input)? {
        ToolchainLockfileDocument::V1(lockfile) => Ok(lockfile.upgrade().into_latest()),
        ToolchainLockfileDocument::V2 {
            lockfile,
            migration_diagnostics,
        } => {
            let _diagnostic_count = migration_diagnostics.len();
            Ok(lockfile.into_latest())
        }
        ToolchainLockfileDocument::V3(lockfile) => eyre::bail!(
            "schema_version {} is valid but cannot be consumed by the legacy schema_version {ENGINE_SCHEMA_VERSION} engine",
            lockfile.schema_version
        ),
        ToolchainLockfileDocument::V4(lockfile) => eyre::bail!(
            "schema_version {} is valid but cannot be consumed by the legacy schema_version {ENGINE_SCHEMA_VERSION} engine",
            lockfile.schema_version
        ),
    }
}

pub(crate) fn analyze_migration(input: &str) -> eyre::Result<MigrationAnalysis> {
    match parse_document(input)? {
        ToolchainLockfileDocument::V1(lockfile) => {
            let normalized = lockfile.upgrade();
            Ok(MigrationAnalysis::Legacy {
                source_schema_version: 1,
                diagnostics: normalized.migration_diagnostics(),
                candidate: None,
            })
        }
        ToolchainLockfileDocument::V2 {
            lockfile,
            migration_diagnostics,
        } => {
            let candidate = if migration_diagnostics.is_empty() {
                let candidate = lockfile.migrate_to_v3()?;
                Some(candidate.refresh_derived_state(&candidate)?)
            } else {
                None
            };
            Ok(MigrationAnalysis::Legacy {
                source_schema_version: ENGINE_SCHEMA_VERSION,
                diagnostics: migration_diagnostics,
                candidate,
            })
        }
        ToolchainLockfileDocument::V3(lockfile) => Ok(MigrationAnalysis::LegacyV3 {
            candidate: ArtifactLockfileV4::from_v3(lockfile),
        }),
        ToolchainLockfileDocument::V4(lockfile) => Ok(MigrationAnalysis::CurrentV4(lockfile)),
    }
}

pub(crate) fn read_current(input: &str) -> eyre::Result<ArtifactLockfileV3> {
    match parse_document(input)? {
        ToolchainLockfileDocument::V3(lockfile) => Ok(lockfile),
        ToolchainLockfileDocument::V4(lockfile) => lockfile.effective_lockfile("rust-toolchain"),
        ToolchainLockfileDocument::V1(_) | ToolchainLockfileDocument::V2 { .. } => eyre::bail!(
            "dependency commands require schema version 3 or 4; run dependency migrate --branch <branch> first"
        ),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const MINIMAL_V1: &str = r#"{
        "schema_version": 1,
        "minecraft_version": "1.19.2",
        "maven_cache_dir": "$sfm-cache/maven",
        "allow_local_artifact_cache": false,
        "repositories": [],
        "dependencies": [],
        "artifacts": []
    }"#;

    #[test]
    fn minimal_v1_normalizes_and_requests_v2_migration_hints() {
        let MigrationAnalysis::Legacy {
            source_schema_version,
            diagnostics,
            candidate,
        } = analyze_migration(MINIMAL_V1).expect("v1 analysis should succeed")
        else {
            panic!("expected legacy migration analysis");
        };

        assert_eq!(source_schema_version, 1);
        assert!(candidate.is_none());
        assert_eq!(diagnostics.len(), 1);
        assert_eq!(diagnostics[0].path, "migration_hints");
        assert!(diagnostics[0].remediation.contains("migrate --check"));
    }

    #[test]
    fn future_schema_version_is_rejected_before_version_parse() {
        let Err(error) = parse_document(r#"{"schema_version": 5}"#) else {
            panic!("future schema should fail");
        };

        assert!(
            error
                .to_string()
                .contains("newer than supported schema_version 4")
        );
    }

    #[test]
    fn checked_in_v4_defaults_rust_commands_to_the_full_profile() {
        let input = include_str!("../../../../minecraft/sfm-toolchain.lock.json");
        let lockfile = read_current(input).expect("checked-in v4 lockfile should project");
        assert!(lockfile.dependencies.iter().any(|dependency| {
            dependency.id == "vox-java"
                && dependency
                    .components
                    .iter()
                    .any(|component| component.id == "main")
        }));
    }
}
