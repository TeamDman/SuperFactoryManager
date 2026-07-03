use crate::jar_build::ArtifactLockfile;
use crate::toolchain_lockfile_schema::preflight_document::PreflightDocument;
use crate::toolchain_lockfile_schema::version::v1::ArtifactLockfileV1;
use crate::toolchain_lockfile_schema::version::v2::ArtifactLockfileV2;
use eyre::Context;

pub(crate) const LATEST_SCHEMA_VERSION: u32 = 2;

pub(crate) fn upgrade_to_latest(input: &str) -> eyre::Result<ArtifactLockfile> {
    let preflight: PreflightDocument = facet_json::from_str(input)
        .wrap_err("failed to parse toolchain lockfile schema preflight")?;

    match preflight.schema_version {
        1 => {
            if input.contains("\"weak\"") {
                eyre::bail!(
                    "toolchain lockfile declares schema_version 1 but contains v2-only field `weak`; update schema_version to {LATEST_SCHEMA_VERSION}"
                );
            }
            let lockfile: ArtifactLockfileV1 = facet_json::from_str(input)
                .wrap_err("failed to parse toolchain lockfile schema v1")?;
            Ok(lockfile.upgrade().into_latest())
        }
        LATEST_SCHEMA_VERSION => {
            let lockfile: ArtifactLockfileV2 = facet_json::from_str(input)
                .wrap_err("failed to parse toolchain lockfile schema v2")?;
            Ok(lockfile.into_latest())
        }
        version if version > LATEST_SCHEMA_VERSION => eyre::bail!(
            "toolchain lockfile schema_version {version} is newer than supported schema_version {LATEST_SCHEMA_VERSION}"
        ),
        version => eyre::bail!(
            "toolchain lockfile schema_version {version} is older than the first supported schema_version 1"
        ),
    }
}
