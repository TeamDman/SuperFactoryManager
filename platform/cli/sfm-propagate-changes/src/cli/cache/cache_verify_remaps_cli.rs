//! Explicit expensive audit of every managed dependency remap entry.

use super::cache_report_cli::checked_existing_root;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::terminal_output::stdout_line;
use eyre::ensure;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

#[derive(Facet, Debug)]
pub struct CacheVerifyRemapsArgs {
    /// Existing catalogued projection in the checkout that owns the cache.
    #[facet(args::named)]
    pub project_root: PathBuf,
    /// Existing managed cache root; defaults to this checkout's sfm-toolchain-data.
    #[facet(default, args::named)]
    pub cache_root: Option<PathBuf>,
    /// Audit only this exact 64-hex recipe key; by default audit every key.
    #[facet(default, args::named)]
    pub key: Option<String>,
}

impl CacheVerifyRemapsArgs {
    /// Rehash complete outputs. Never publish a seal, repair or delete.
    pub fn invoke(self) -> eyre::Result<()> {
        let (catalog, _) = CoreCatalog::for_project(&self.project_root)?;
        let logical = self
            .cache_root
            .unwrap_or_else(|| catalog.repo_root.join("sfm-toolchain-data"));
        let physical = checked_existing_root(&logical)?;
        let root = physical.join("dependency-remap-v1");
        #[cfg(windows)]
        {
            let summary =
                crate::jar_build::engine::audit_managed_remaps(&root, self.key.as_deref())?;
            stdout_line(format!(
                "Remap audit: {} keys, {} verified, {} incomplete, {} damaged, {} scratch roots excluded",
                summary.keys,
                summary.verified,
                summary.incomplete.len(),
                summary.damaged.len(),
                summary.scratch_roots
            ))?;
            for path in &summary.incomplete {
                stdout_line(format!("  incomplete: {}", path.display()))?;
            }
            for (path, reason) in &summary.damaged {
                stdout_line(format!("  damaged: {}: {reason}", path.display()))?;
            }
            ensure!(
                summary.incomplete.is_empty() && summary.damaged.is_empty(),
                "Remap audit found incomplete or damaged entries"
            );
            Ok(())
        }
        #[cfg(not(windows))]
        {
            let _ = (root, self.key);
            eyre::bail!("Managed dependency remaps are currently supported on Windows")
        }
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn parses_explicit_remap_audit() {
        let parsed = figue::from_slice::<super::super::CacheArgs>(&[
            "verify-remaps",
            "--project-root",
            "C:/project",
        ])
        .into_result()
        .expect("cache verify-remaps should parse")
        .get_silent();
        assert!(matches!(
            parsed.command,
            super::super::CacheCommand::VerifyRemaps(_)
        ));
    }
}
