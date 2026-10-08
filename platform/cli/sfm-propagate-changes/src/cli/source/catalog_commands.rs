//! Public source commands use the authoritative Liquid catalog.

use super::core_project_cli::CoreProjectArgs;
use super::oracle_cli::SourceOracleArgs;
use super::projection_catalog_cli::SourceListArgs;
use super::projection_catalog_cli::SourceRenderArgs;
use super::projection_catalog_cli::SourceShowArgs;
use super::simplify_cli::SimplifyArgs;
use super::source_cli::LegacySourceArgs;
use super::source_trace_cli::SourceTraceArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use eyre::Result;
use facet::Facet;
use figue::{self as args};
use std::path::Path;

#[derive(Debug, Facet)]
pub struct SourceArgs {
    #[facet(args::subcommand)]
    pub command: SourceCommand,
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum SourceCommand {
    /// Compare pinned Git oracles with current core-rendered projections.
    Oracle(SourceOracleArgs),
    /// Generate, check and build projects from the Liquid core.
    Project(CoreProjectArgs),
    /// List named projections and their explicit feature selections.
    List(SourceListArgs),
    /// Inspect one named projection.
    Show(SourceShowArgs),
    /// Render one core-owned source file.
    Render(SourceRenderArgs),
    /// Inspect one generated file's ownership and edit state.
    Trace(SourceTraceArgs),
    /// Find whitespace-only candidate regions in manifested Java; never rewrite.
    Simplify(SimplifyArgs),
    /// Historical snapshot/preset operations for pre-consolidation checkouts.
    Legacy(LegacySourceArgs),
}

impl SourceArgs {
    /// Dispatch current catalog commands or explicitly requested legacy tooling.
    ///
    /// # Errors
    /// Returns invalid-input, contributor-conflict and command execution errors.
    pub fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        match self.command {
            SourceCommand::Oracle(args) => args.invoke_in(cancellation, invocation_dir),
            SourceCommand::Project(args) => args.invoke_in(cancellation, invocation_dir),
            SourceCommand::List(args) => args.invoke_in(invocation_dir),
            SourceCommand::Show(args) => args.invoke_in(invocation_dir),
            SourceCommand::Render(args) => args.invoke_in(invocation_dir),
            SourceCommand::Trace(args) => args.invoke_in(),
            SourceCommand::Simplify(args) => args.invoke_in(cancellation, invocation_dir),
            SourceCommand::Legacy(args) => args.invoke_in(cancellation, invocation_dir),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;

    #[test]
    fn historical_import_requires_explicit_legacy_namespace() {
        assert!(
            figue::from_slice::<Cli>(&["source", "import-release", "--repo-root", "."])
                .into_result()
                .is_err()
        );
        let cli =
            figue::from_slice::<Cli>(&["source", "legacy", "import-release", "--repo-root", "."])
                .into_result()
                .unwrap()
                .get_silent();
        assert!(matches!(
            cli.command,
            Command::Source(SourceArgs {
                command: SourceCommand::Legacy(_)
            })
        ));
    }

    #[test]
    fn legacy_import_cannot_recreate_snapshots_in_catalog_repository() {
        let temp = tempfile::tempdir().unwrap();
        std::fs::create_dir_all(temp.path().join("platform/minecraft")).unwrap();
        std::fs::write(
            temp.path().join("platform/minecraft/projections.json"),
            b"{}",
        )
        .unwrap();
        let args = LegacySourceArgs {
            command: super::super::source_cli::LegacySourceCommand::ImportRelease(
                super::super::source_cli::SourceImportReleaseArgs {
                    repo_root: temp.path().to_path_buf(),
                },
            ),
        };
        let error = args
            .invoke_in(&CancellationToken::new(), temp.path())
            .unwrap_err();
        assert!(error.to_string().contains("use source project"));
        assert!(
            !temp
                .path()
                .join("platform/minecraft/release-baselines")
                .exists()
        );
    }
}
