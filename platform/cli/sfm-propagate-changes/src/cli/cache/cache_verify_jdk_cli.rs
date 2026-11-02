//! Explicit expensive audit of a selected projection's managed JBRSDK cache.

use crate::source_projection::core_catalog::CoreCatalog;
use crate::terminal_output::stdout_line;
use crate::toolchain_lockfile_schema::read_jdk_pins;
use eyre::Context as _;
use facet::Facet;
use figue as args;
use std::fs;
use std::path::PathBuf;

#[derive(Facet, Debug)]
pub struct CacheVerifyJdkArgs {
    /// Existing catalogued projection root whose lock selects the SDK.
    #[facet(args::named)]
    pub project_root: PathBuf,
    /// Existing managed cache root; defaults to this checkout's sfm-toolchain-data.
    #[facet(default, args::named)]
    pub cache_root: Option<PathBuf>,
}

impl CacheVerifyJdkArgs {
    /// Rehash the archive and compare every installed file. This never repairs or deletes.
    pub fn invoke(self) -> eyre::Result<()> {
        let (catalog, selected_key) = CoreCatalog::for_project(&self.project_root)?;
        let project = catalog
            .repo_root
            .join(catalog.catalog.project_dir(&selected_key)?);
        let lock_path = project.join("sfm-toolchain.lock.json");
        let input = fs::read_to_string(&lock_path)
            .wrap_err_with(|| format!("Failed to read {}", lock_path.display()))?;
        let pins = read_jdk_pins(&input)?
            .ok_or_else(|| eyre::eyre!("Selected projection has no pinned JBRSDK catalog"))?;
        let root = self
            .cache_root
            .unwrap_or_else(|| catalog.repo_root.join("sfm-toolchain-data"));
        let sdk_cache = root.join("minecraft-toolchain").join("jbrsdk");
        let platform = crate::jdk::host_jbrsdk_platform()
            .ok_or_else(|| eyre::eyre!("No pinned JBRSDK platform mapping for this host"))?;
        let mut count = 0usize;
        for pin in &pins {
            for artifact in pin
                .artifacts
                .iter()
                .filter(|artifact| artifact.platform == platform)
            {
                crate::jdk_artifact_cache::audit_pinned_jbrsdk_zip(pin, artifact, &sdk_cache)
                    .wrap_err_with(|| {
                        format!("JBRSDK audit failed for {} {}", pin.version, platform)
                    })?;
                count += 1;
            }
        }
        eyre::ensure!(
            count > 0,
            "Selected projection has no JBRSDK pin for {platform}"
        );
        stdout_line(format!(
            "Verified {count} pinned JBRSDK archive/install pair(s) for {selected_key}"
        ))
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn parses_explicit_jdk_audit() {
        let parsed = figue::from_slice::<super::super::CacheArgs>(&[
            "verify-jdk",
            "--project-root",
            "C:/project",
        ])
        .into_result()
        .expect("cache verify-jdk should parse")
        .get_silent();
        assert!(matches!(
            parsed.command,
            super::super::CacheCommand::VerifyJdk(_)
        ));
    }
}
