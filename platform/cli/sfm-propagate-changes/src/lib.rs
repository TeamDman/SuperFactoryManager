pub mod cli;
pub mod logging;
pub mod paths;
pub mod propagate;
pub mod sfm_path;
pub mod state;
pub mod worktree;

use crate::cli::Cli;

/// Version string combining package version and git revision.
const VERSION: &str = concat!(
    env!("CARGO_PKG_VERSION"),
    " (rev ",
    env!("GIT_REVISION"),
    ")"
);

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

    // Parse command line arguments using figue
    // unwrap() handles --help, --version, completions, and errors with proper exit codes
    let cli: Cli = figue::Driver::new(
        figue::builder::<Cli>()
            .expect("schema should be valid")
            .cli(|c| c)
            .help(|h| h.version(VERSION))
            .build(),
    )
    .run()
    .unwrap();

    // Initialize logging
    logging::init_logging(&cli.logging_config()?)?;

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
    cli.invoke()
}
