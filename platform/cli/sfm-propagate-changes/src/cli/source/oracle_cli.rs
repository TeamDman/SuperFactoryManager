//! Compare fresh core projections with pinned local Git oracles.
//!
//! Status, next and diff never mutate authored or manifested sources. Next may
//! update verified comparison-cache state. Freeze previews bindings unless
//! --apply records them; --refresh makes replacement of existing pins clear.

use crate::cancellation::CancellationToken;
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
    use crate::cli::cli::Cli;
    use crate::cli::cli::Command;
    use crate::cli::output::OutputFormat;
    use crate::cli::source::SourceArgs;
    use crate::cli::source::SourceCommand;

    fn parse(arguments: &[&str]) -> SourceOracleCommand {
        let cli = figue::from_slice::<Cli>(arguments)
            .into_result()
            .expect("oracle command should parse")
            .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::Oracle(oracle),
        }) = cli.command
        else {
            panic!("expected source oracle command");
        };
        oracle.command
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
                .invoke_in(&CancellationToken::new(), directory.path())
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
        .invoke_in(&cancellation, Path::new("."));
        assert!(result.is_err());
    }

    #[test]
    fn oracle_draft_requires_new_explicit_destination_and_review_is_optional() {
        let SourceOracleCommand::Draft(args) = parse(&[
            "source",
            "oracle",
            "draft",
            "--file",
            "src/main/java/example/A.java",
            "--output-dir",
            "draft-one",
        ]) else {
            panic!("expected staged draft");
        };
        assert_eq!(args.repo_root, PathBuf::from("."));
        assert_eq!(args.output_dir, PathBuf::from("draft-one"));
        assert!(args.review.is_none());
        assert!(args.projection.is_empty());
        let SourceOracleCommand::Draft(args) = parse(&[
            "source",
            "oracle",
            "draft",
            "--repo-root",
            "repository",
            "--file",
            "src/main/java/example/A.java",
            "--output-dir",
            "draft-two",
            "--review",
            "reviewed-ownership.json",
            "--projection",
            "sfm-4.34.0/mc-1.19.2",
            "--projection",
            "sfm-dev/mc-1.19.2",
        ]) else {
            panic!("expected explicitly reviewed draft");
        };
        assert_eq!(args.repo_root, PathBuf::from("repository"));
        assert_eq!(args.review, Some(PathBuf::from("reviewed-ownership.json")));
        assert_eq!(
            args.projection,
            ["sfm-4.34.0/mc-1.19.2", "sfm-dev/mc-1.19.2"]
        );
        for arguments in [
            vec!["source", "oracle", "draft", "--file", "src/A.java"],
            vec!["source", "oracle", "draft", "--output-dir", "draft"],
        ] {
            assert!(figue::from_slice::<Cli>(&arguments).into_result().is_err());
        }
    }

    #[test]
    fn oracle_freeze_defaults_to_preview_and_refresh_does_not_imply_apply() {
        let SourceOracleCommand::Freeze(args) = parse(&["source", "oracle", "freeze"]) else {
            panic!("expected freeze");
        };
        assert_eq!(args.repo_root, PathBuf::from("."));
        assert!(!args.apply && !args.refresh);

        let SourceOracleCommand::Freeze(args) = parse(&["source", "oracle", "freeze", "--refresh"])
        else {
            panic!("expected freeze preview refresh");
        };
        assert!(!args.apply && args.refresh);
    }

    #[test]
    fn oracle_freeze_requires_explicit_apply_and_retains_named_root() {
        let SourceOracleCommand::Freeze(args) = parse(&[
            "source",
            "oracle",
            "freeze",
            "--repo-root",
            "../repository with spaces",
            "--apply",
            "--refresh",
        ]) else {
            panic!("expected freeze apply refresh");
        };
        assert_eq!(args.repo_root, PathBuf::from("../repository with spaces"));
        assert!(args.apply && args.refresh);
    }

    #[test]
    fn oracle_status_filter_and_next_default_root_parse() {
        let SourceOracleCommand::Status(all) = parse(&["source", "oracle", "status"]) else {
            panic!("expected status");
        };
        assert_eq!(all.repo_root, PathBuf::from("."));
        assert!(all.projection.is_none());

        let SourceOracleCommand::Status(one) = parse(&[
            "source",
            "oracle",
            "status",
            "--projection",
            "sfm-4.34.0/mc-1.19.2",
            "--repo-root",
            "repository",
        ]) else {
            panic!("expected filtered status");
        };
        assert_eq!(one.projection.as_deref(), Some("sfm-4.34.0/mc-1.19.2"));
        assert_eq!(one.repo_root, PathBuf::from("repository"));

        let SourceOracleCommand::Next(args) = parse(&["source", "oracle", "next"]) else {
            panic!("expected next");
        };
        assert_eq!(args.repo_root, PathBuf::from("."));
        assert!(!args.rebuild_index);
        let SourceOracleCommand::Next(rebuild) =
            parse(&["source", "oracle", "next", "--rebuild-index"])
        else {
            panic!("expected explicit index rebuild");
        };
        assert!(rebuild.rebuild_index);
    }

    #[test]
    fn oracle_diff_accepts_explicit_project_relative_source_and_build_paths() {
        for file in ["src/main/java/example/A.java", "build.gradle"] {
            let SourceOracleCommand::Diff(args) = parse(&[
                "source",
                "oracle",
                "diff",
                "--projection",
                "sfm-dev/mc-1.19.2",
                "--file",
                file,
            ]) else {
                panic!("expected diff");
            };
            assert_eq!(args.repo_root, PathBuf::from("."));
            assert_eq!(args.projection, "sfm-dev/mc-1.19.2");
            assert_eq!(args.file, file);
        }
    }

    #[test]
    fn oracle_non_repository_options_preserve_the_default_root() {
        let SourceOracleCommand::Freeze(args) = parse(&["source", "oracle", "freeze", "--refresh"])
        else {
            panic!("expected freeze preview refresh");
        };
        assert_eq!(args.repo_root, PathBuf::from("."));
        assert!(!args.apply && args.refresh);

        let SourceOracleCommand::Status(args) = parse(&[
            "source",
            "oracle",
            "status",
            "--projection",
            "sfm-4.34.0/mc-1.19.2",
        ]) else {
            panic!("expected filtered status with default root");
        };
        assert_eq!(args.repo_root, PathBuf::from("."));
        assert_eq!(args.projection.as_deref(), Some("sfm-4.34.0/mc-1.19.2"));
    }

    #[test]
    fn oracle_next_and_diff_retain_explicit_named_roots() {
        let repository = "../repository with spaces";
        let SourceOracleCommand::Next(args) =
            parse(&["source", "oracle", "next", "--repo-root", repository])
        else {
            panic!("expected next with explicit root");
        };
        assert_eq!(args.repo_root, PathBuf::from(repository));

        let SourceOracleCommand::Diff(args) = parse(&[
            "source",
            "oracle",
            "diff",
            "--repo-root",
            repository,
            "--projection",
            "sfm-dev/mc-1.19.2",
            "--file",
            "build.gradle",
        ]) else {
            panic!("expected diff with explicit root");
        };
        assert_eq!(args.repo_root, PathBuf::from(repository));
        assert_eq!(args.projection, "sfm-dev/mc-1.19.2");
        assert_eq!(args.file, "build.gradle");
    }

    #[test]
    fn oracle_diff_requires_context_and_file_and_read_commands_reject_writes() {
        for arguments in [
            vec!["source", "oracle", "diff"],
            vec!["source", "oracle", "diff", "--projection", "example"],
            vec!["source", "oracle", "diff", "--file", "build.gradle"],
            vec!["source", "oracle", "diff", "--repo-root", "repository"],
            vec![
                "source",
                "oracle",
                "diff",
                "--repo-root",
                "repository",
                "--projection",
                "example",
            ],
            vec![
                "source",
                "oracle",
                "diff",
                "--repo-root",
                "repository",
                "--file",
                "build.gradle",
            ],
            vec!["source", "oracle", "status", "--apply"],
            vec!["source", "oracle", "next", "--apply"],
            vec!["source", "oracle", "status", "--output-root", "generated"],
        ] {
            assert!(
                figue::from_slice::<Cli>(&arguments).into_result().is_err(),
                "unexpectedly accepted {arguments:?}"
            );
        }
    }

    #[test]
    fn oracle_seed_defaults_to_preview_and_retains_explicit_aliases_and_plan() {
        let SourceOracleCommand::SeedMissing(preview) = parse(&[
            "source",
            "oracle",
            "seed-missing",
            "--projection",
            "sfm-4.34.0/mc-1.19.2",
            "--file",
            "Run-SelectedGameTests.ps1",
        ]) else {
            panic!("expected seed preview");
        };
        assert_eq!(preview.repo_root, PathBuf::from("."));
        assert!(!preview.apply && preview.reviewed_plan.is_none());
        assert!(preview.input.is_none() && preview.also_output.is_empty());
        let SourceOracleCommand::SeedMissing(apply) = parse(&[
            "source",
            "oracle",
            "seed-missing",
            "--repo-root",
            "repository",
            "--projection",
            "sfm-4.34.0/mc-1.19.2",
            "--file",
            "intellij-java-code-style.xml",
            "--input",
            "build/shared/intellij-java-code-style.xml",
            "--also-output",
            "codestyles/Default.xml",
            "--apply",
            "--reviewed-plan",
            "sha256:reviewed",
        ]) else {
            panic!("expected explicit seed apply");
        };
        assert_eq!(apply.repo_root, PathBuf::from("repository"));
        assert_eq!(apply.also_output, ["codestyles/Default.xml"]);
        assert_eq!(
            apply.input.as_deref(),
            Some("build/shared/intellij-java-code-style.xml")
        );
        assert!(apply.apply);
        assert_eq!(apply.reviewed_plan.as_deref(), Some("sha256:reviewed"));
    }
}
