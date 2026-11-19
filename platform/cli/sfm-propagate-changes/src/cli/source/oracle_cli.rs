//! Compare fresh core projections with pinned local Git oracles.
//!
//! Status, next and diff never mutate authored or manifested sources. Next may
//! update verified comparison-cache state. Freeze previews bindings unless
//! --apply records them; --refresh makes replacement of existing pins clear.

use crate::cancellation::CancellationToken;
use crate::cli::InvocationHomes;
use crate::cli::output::CliOutput;
use crate::source_projection::oracle;
use crate::source_projection::oracle_draft;
use crate::source_projection::oracle_seed;
use eyre::Result;
use facet::Facet;
use figue::{self as args};
use std::path::Path;
use std::path::PathBuf;

#[derive(Debug, Facet)]
pub struct SourceOracleArgs {
    #[facet(args::subcommand)]
    pub command: SourceOracleCommand,
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum SourceOracleCommand {
    /// Preview release/development Git pins; only --apply writes the binding file.
    Freeze(SourceOracleFreezeArgs),
    /// Compare fresh rendered files with pinned oracles and report burndown.
    Status(SourceOracleStatusArgs),
    /// Identify the first unresolved file in the required context priority order.
    Next(SourceOracleRepositoryArgs),
    /// Show one rendered/oracle difference without changing either source.
    Diff(SourceOracleDiffArgs),
    /// Preview or explicitly seed a missing core input from a pinned release blob.
    SeedMissing(SourceOracleSeedArgs),
    /// Stage pinned witnesses and ownership review, or an explicitly reviewed candidate.
    Draft(SourceOracleDraftArgs),
}

#[derive(Debug, Facet)]
pub struct SourceOracleRepositoryArgs {
    /// Repository root containing the core templates and local Git references.
    #[facet(default = PathBuf::from("."), args::named)]
    pub repo_root: PathBuf,
    /// Explicitly initialize/rebuild content-keyed comparison state (cold work).
    #[facet(default = false, args::named)]
    pub rebuild_index: bool,
}

#[derive(Debug, Facet)]
pub struct SourceOracleFreezeArgs {
    /// Repository root containing the core templates and local Git references.
    #[facet(default = PathBuf::from("."), args::named)]
    pub repo_root: PathBuf,
    /// Explicitly write the previewed binding file after all reference checks.
    #[facet(default = false, args::named)]
    pub apply: bool,
    /// Explicitly refresh existing pins; without --apply this remains a preview.
    #[facet(default = false, args::named)]
    pub refresh: bool,
}

#[derive(Debug, Facet)]
pub struct SourceOracleStatusArgs {
    /// Repository root containing the core templates and local Git references.
    #[facet(default = PathBuf::from("."), args::named)]
    pub repo_root: PathBuf,
    /// Inspect one exact catalog key; omitted means the entire required matrix.
    #[facet(default, args::named)]
    pub projection: Option<String>,
}

#[derive(Debug, Facet)]
pub struct SourceOracleDiffArgs {
    /// Repository root containing the core templates and local Git references.
    #[facet(default = PathBuf::from("."), args::named)]
    pub repo_root: PathBuf,
    /// Exact catalog projection key whose pinned oracle is compared.
    #[facet(args::named)]
    pub projection: String,
    /// Portable project-relative output path, including src/... or build inputs.
    #[facet(args::named)]
    pub file: String,
}

#[derive(Debug, Facet)]
pub struct SourceOracleDraftArgs {
    /// Repository root containing the authored core and pinned local Git oracles.
    #[facet(default = PathBuf::from("."), args::named)]
    pub repo_root: PathBuf,
    /// Exact project-relative output path witnessed across all required contexts.
    #[facet(args::named)]
    pub file: String,
    /// New explicit directory outside canonical Minecraft and Git state.
    #[facet(args::named)]
    pub output_dir: PathBuf,
    /// Exact required oracle witness key. Repeat to select a subset; omitted means all twenty.
    #[facet(default, args::named)]
    pub projection: Vec<String>,
    /// Explicit fingerprint-bound ownership review; omitted stages evidence only.
    #[facet(default, args::named)]
    pub review: Option<PathBuf>,
}

#[derive(Debug, Facet)]
struct OracleCommandError {
    schema: String,
    operation: String,
    message: String,
}

#[derive(Debug, Facet)]
pub struct SourceOracleSeedArgs {
    /// Repository root containing the authored core and pinned local Git oracles.
    #[facet(default = PathBuf::from("."), args::named)]
    pub repo_root: PathBuf,
    /// Exact previous-release projection key that supplies the source witness.
    #[facet(args::named)]
    pub projection: String,
    /// Missing project-relative output path to seed.
    #[facet(args::named)]
    pub file: String,
    /// Optional exact core-relative input path; otherwise src/... or build/shared/... .
    #[facet(default, args::named)]
    pub input: Option<String>,
    /// Additional missing output alias for the same bytes. Repeat for more aliases.
    #[facet(default, args::named)]
    pub also_output: Vec<String>,
    /// Explicitly publish the reviewed membership rules and create-only source.
    #[facet(default = false, args::named)]
    pub apply: bool,
    /// Exact `plan_identity` from the preview; required when applying.
    #[facet(default, args::named)]
    pub reviewed_plan: Option<String>,
}

fn command_output<T: Facet<'static> + 'static>(
    operation: &str,
    result: Result<T>,
    success_status: impl FnOnce(&T) -> u8,
) -> CliOutput {
    match result {
        Ok(report) => {
            let exit_code = success_status(&report);
            CliOutput::facet_with_status(report, exit_code)
        }
        Err(error) => CliOutput::facet_with_status(
            OracleCommandError {
                schema: "sfm:source_oracle_command_error@1".to_owned(),
                operation: operation.to_owned(),
                message: format!("{error:#}"),
            },
            2,
        ),
    }
}

impl SourceOracleArgs {
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
        homes: &InvocationHomes,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        Ok(match self.command {
            SourceOracleCommand::Freeze(args) => command_output(
                "freeze",
                oracle::freeze(
                    &args.repo_root,
                    invocation_dir,
                    args.apply,
                    args.refresh,
                    cancellation,
                ),
                |_| 0,
            ),
            SourceOracleCommand::Status(args) => {
                let report = oracle::status(
                    &args.repo_root,
                    invocation_dir,
                    args.projection.as_deref(),
                    cancellation,
                );
                command_output("status", report, oracle::OracleStatus::exit_code)
            }
            SourceOracleCommand::Next(args) => {
                let report = oracle::next_with_index(
                    &args.repo_root,
                    invocation_dir,
                    cancellation,
                    args.rebuild_index,
                    homes.cache_home()?,
                );
                command_output("next", report, oracle::OracleNext::exit_code)
            }
            SourceOracleCommand::Diff(args) => {
                let report = oracle::diff(
                    &args.repo_root,
                    invocation_dir,
                    &args.projection,
                    &args.file,
                    cancellation,
                );
                command_output("diff", report, oracle::OracleDiff::exit_code)
            }
            SourceOracleCommand::Draft(args) => command_output(
                "draft",
                oracle_draft::draft(
                    &args.repo_root,
                    invocation_dir,
                    &oracle_draft::OracleDraftRequest {
                        file: &args.file,
                        output_dir: &args.output_dir,
                        review: args.review.as_deref(),
                        projections: &args.projection,
                    },
                    cancellation,
                ),
                |_| 0,
            ),
            SourceOracleCommand::SeedMissing(args) => command_output(
                "seed-missing",
                oracle_seed::seed_missing(
                    &args.repo_root,
                    invocation_dir,
                    &oracle_seed::OracleSeedRequest {
                        projection: &args.projection,
                        file: &args.file,
                        input: args.input.as_deref(),
                        output_aliases: &args.also_output,
                        apply: args.apply,
                        reviewed_plan_identity: args.reviewed_plan.as_deref(),
                    },
                    cancellation,
                ),
                |_| 0,
            ),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::output::OutputFormat;
    use crate::paths::AppHome;
    use crate::paths::CacheHome;

    fn test_homes(root: &Path) -> InvocationHomes {
        InvocationHomes::new(
            AppHome(root.join("settings")),
            Ok(CacheHome(root.join("cache"))),
        )
    }

    #[test]
    fn oracle_service_errors_are_typed_invalid_comparisons_for_every_route() {
        let directory = tempfile::tempdir().unwrap();
        let repository = || SourceOracleRepositoryArgs {
            repo_root: PathBuf::from("missing-repository"),
            rebuild_index: false,
        };
        for (operation, command) in [
            (
                "freeze",
                SourceOracleCommand::Freeze(SourceOracleFreezeArgs {
                    repo_root: PathBuf::from("missing-repository"),
                    apply: false,
                    refresh: false,
                }),
            ),
            (
                "status",
                SourceOracleCommand::Status(SourceOracleStatusArgs {
                    repo_root: PathBuf::from("missing-repository"),
                    projection: None,
                }),
            ),
            ("next", SourceOracleCommand::Next(repository())),
            (
                "diff",
                SourceOracleCommand::Diff(SourceOracleDiffArgs {
                    repo_root: PathBuf::from("missing-repository"),
                    projection: "sfm-4.34.0/mc-1.19.2".to_owned(),
                    file: "build.gradle".to_owned(),
                }),
            ),
            (
                "seed-missing",
                SourceOracleCommand::SeedMissing(SourceOracleSeedArgs {
                    repo_root: PathBuf::from("missing-repository"),
                    projection: "sfm-4.34.0/mc-1.19.2".into(),
                    file: "Run-SelectedGameTests.ps1".into(),
                    input: None,
                    also_output: Vec::new(),
                    apply: false,
                    reviewed_plan: None,
                }),
            ),
            (
                "draft",
                SourceOracleCommand::Draft(SourceOracleDraftArgs {
                    repo_root: PathBuf::from("missing-repository"),
                    file: "src/main/java/example/A.java".into(),
                    output_dir: PathBuf::from("new-draft"),
                    review: None,
                    projection: Vec::new(),
                }),
            ),
        ] {
            let output = SourceOracleArgs { command }
                .invoke_in(
                    &CancellationToken::new(),
                    directory.path(),
                    &test_homes(directory.path()),
                )
                .expect("service errors must produce typed command output");
            assert_eq!(output.exit_code(), 2);
            let json = output
                .render(Some(OutputFormat::Json), false)
                .unwrap()
                .unwrap();
            let report: OracleCommandError = facet_json::from_str(&json).unwrap();
            assert_eq!(report.schema, "sfm:source_oracle_command_error@1");
            assert_eq!(report.operation, operation);
            assert!(!report.message.is_empty());
        }
        assert_eq!(std::fs::read_dir(directory.path()).unwrap().count(), 0);
    }

    #[test]
    fn oracle_output_preserves_valid_difference_status_and_entry_cancellation() {
        #[derive(Facet)]
        struct TestReport {
            matches: bool,
        }
        let output = command_output("status", Ok(TestReport { matches: false }), |_| 1);
        assert_eq!(output.exit_code(), 1);

        let cancellation = CancellationToken::new();
        cancellation.request_cancel("fixture cancellation");
        let result = SourceOracleArgs {
            command: SourceOracleCommand::Next(SourceOracleRepositoryArgs {
                repo_root: PathBuf::from("missing-repository"),
                rebuild_index: false,
            }),
        }
        .invoke_in(&cancellation, Path::new("."), &test_homes(Path::new(".")));
        assert!(result.is_err());
    }
}
