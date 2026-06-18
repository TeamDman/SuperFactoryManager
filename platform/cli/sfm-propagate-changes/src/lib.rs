pub mod artifact_lock;
pub mod branch_targets;
pub mod cancellation;
pub mod cli;
pub mod colour;
pub mod curseforge;
pub mod jar_build;
pub mod jdk;
pub mod logging;
pub mod modrinth;
pub mod one_password;
pub mod paths;
pub mod propagate;
pub mod sfm_path;
pub mod state;
pub mod terminal_output;
pub mod worktree;

#[cfg(feature = "tracy_memory")]
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

/// Entrypoint for the program.
///
/// # Errors
///
/// This function will return an error if `color_eyre` installation, CLI parsing, logging initialization, or command execution fails.
///
/// # Panics
///
/// Panics if the CLI schema is invalid (should never happen with correct code).
pub fn main() -> eyre::Result<()> {
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

    // Invoke whatever command was requested
    cli.invoke(cancellation_token)
}
