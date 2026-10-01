pub mod artifact_lock;
pub mod branch_targets;
pub mod cancellation;
pub mod cli;
pub mod colour;
pub mod curseforge;
pub(crate) mod dependency_classfile_stubs;
pub(crate) mod dependency_inventory;
pub(crate) mod dependency_locked_sources;
pub(crate) mod dependency_sources;
mod file_identity;
pub mod jar_build;
pub mod java_analysis;
pub(crate) mod java_source_catalog;
pub mod jdk;
pub(crate) mod jdk_artifact_cache;
pub mod logging;
pub mod modrinth;
pub mod one_password;
pub mod panic;
pub mod paths;
pub(crate) mod payload_fetcher;
pub mod prism;
pub mod propagate;
pub mod release_review_capture;
mod release_review_capture_io;
pub mod release_review_git;
pub mod release_review_java_diff;
pub mod release_review_ledger;
pub mod release_review_ledger_resolve;
pub mod release_review_materialize;
pub mod release_review_source_probe;
pub mod release_review_surface_v1;
pub mod release_review_text_diff;
pub mod release_review_v1;
pub mod release_review_working_tree;
pub mod release_review_working_tree_materialize;
pub mod review_session_v1;
pub mod review_session_v2;
pub mod sfm_path;
pub mod source_archive;
pub mod source_audit;
pub mod source_cache;
pub(crate) mod source_decompile;
pub mod source_git;
pub(crate) mod source_maven;
pub mod source_projection;
pub(crate) mod source_provider;
pub mod state;
pub mod syntax_highlight;
pub mod terminal_output;
pub mod toolchain_lockfile_schema;
pub(crate) mod toolchain_lockfile_write;
pub mod worktree;

// Allocation profiling is a production diagnostic, not a unit-test allocator.
// The Tracy wrapper starts its client on the first allocation and retains
// allocation/callstack events without a collecting profiler. A long libtest
// process can therefore exhaust memory solely from instrumentation. Keep the
// all-feature production build profiled, but run behavioral unit tests with
// Rust's ordinary allocator; this does not remove any tested feature code.
#[cfg(all(feature = "tracy_memory", not(test)))]
#[global_allocator]
static TRACY_ALLOCATOR: tracy_client::ProfiledAllocator<std::alloc::System> =
    tracy_client::ProfiledAllocator::new(std::alloc::System, 100);

use crate::cli::Cli;
use chrono::DateTime;
use chrono::Local;
use chrono::Utc;

/// Version string combining package version, git revision, and build time.
fn version() -> String {
    let built_at = option_env!("BUILD_TIMESTAMP_UNIX")
        .and_then(|value| value.parse::<i64>().ok())
        .and_then(|timestamp| DateTime::<Utc>::from_timestamp(timestamp, 0))
        .map_or_else(
            || "unknown build time".to_string(),
            |timestamp| {
                timestamp
                    .with_timezone(&Local)
                    .format("%Y-%m-%d %H:%M:%S %Z")
                    .to_string()
            },
        );

    format!(
        "{} (rev {}, built {})",
        env!("CARGO_PKG_VERSION"),
        env!("GIT_REVISION"),
        built_at,
    )
}

fn reject_stage_log_file_before_logging(cli: &Cli) -> eyre::Result<()> {
    if cli.global_args.log_file.is_some()
        && matches!(
            &cli.command,
            cli::Command::Source(cli::source::SourceArgs {
                command: cli::source::SourceCommand::FrozenPresetStage(_),
            })
        )
    {
        eyre::bail!("source frozen-preset-stage rejects global --log-file before logging starts");
    }
    Ok(())
}

/// Entrypoint for the program.
///
/// # Errors
///
/// This function will return an error if `color_eyre` installation, CLI parsing, logging initialization, or command execution fails.
///
/// # Panics
///
/// Panics if the CLI schema is invalid (should never happen with correct code).
pub fn main() -> eyre::Result<std::process::ExitCode> {
    if java_analysis::run_live_definition_worker_from_env()? {
        return Ok(std::process::ExitCode::SUCCESS);
    }
    if java_analysis::run_dependency_index_worker_from_env()? {
        return Ok(std::process::ExitCode::SUCCESS);
    }
    // Install color_eyre for better error reports
    color_eyre::install()?;
    let cancellation_token = cancellation::install_ctrlc_handler()?;

    let version = version();

    // Parse command line arguments using figue
    // unwrap() handles --help, --version, completions, and errors with proper exit codes
    let cli: Cli = figue::Driver::new(
        figue::builder::<Cli>()
            .expect("schema should be valid")
            .cli(|c| c.args(std::env::args().skip(1)))
            .help(|h| h.version(version))
            .build(),
    )
    .run()
    .unwrap();

    // Candidate-only staging must not let a global log path write elsewhere
    // before the command has checked its external checkout boundary.
    reject_stage_log_file_before_logging(&cli)?;

    // Initialize logging
    logging::init_logging(&cli.logging_config()?, &cancellation_token)?;

    #[cfg(windows)]
    {
        // Enable ANSI support on Windows
        // This fails in a pipe scenario, so we ignore the error
        let _ = teamy_windows::console::enable_ansi_support();

        // Warn if UTF-8 is not enabled on Windows
        #[cfg(windows)]
        teamy_windows::string::warn_if_utf8_not_enabled();
    };

    // Invoke whatever command was requested and render typed output once.
    let requested_output_format = cli.global_args.output_format;
    let output = cli.invoke(cancellation_token)?;
    let exit_code = output.exit_code();
    output.emit(requested_output_format)?;
    Ok(std::process::ExitCode::from(exit_code))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    fn stage_cli(log_file: Option<PathBuf>) -> Cli {
        Cli {
            global_args: cli::global_args::GlobalArgs {
                log_file,
                ..Default::default()
            },
            command: cli::Command::Source(cli::source::SourceArgs {
                command: cli::source::SourceCommand::FrozenPresetStage(
                    cli::source::FrozenPresetStageArgs {
                        repo_root: PathBuf::from("authored"),
                        candidate_root: PathBuf::from("candidate"),
                        preview: PathBuf::from("preview.json"),
                        preview_sha256: "sha256:reviewed".to_owned(),
                        apply: false,
                    },
                ),
            }),
            builtins: figue::FigueBuiltins::default(),
        }
    }

    #[test]
    fn frozen_stage_rejects_global_log_file_before_logging_initializes() {
        let cli = stage_cli(Some(PathBuf::from("outside-candidate.ndjson")));
        let error = reject_stage_log_file_before_logging(&cli).unwrap_err();
        assert!(error.to_string().contains("rejects global --log-file"));
        assert!(reject_stage_log_file_before_logging(&stage_cli(None)).is_ok());
    }
}
