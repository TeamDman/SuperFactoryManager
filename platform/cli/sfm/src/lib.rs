pub mod cli;
pub mod client;
pub mod discovery;
pub mod explorer;
pub mod output;
pub mod protocol;

use crate::cli::Cli;

/// Parse and execute the live-game control CLI.
///
/// # Errors
///
/// Returns an error when initialization, parsing, discovery, transport, or
/// output rendering fails.
///
/// # Panics
///
/// Panics only if the compile-time Figue schema for [`Cli`] is invalid.
pub fn main() -> eyre::Result<std::process::ExitCode> {
    color_eyre::install()?;

    let cli: Cli = figue::Driver::new(
        figue::builder::<Cli>()
            .expect("SFM control CLI schema should be valid")
            .cli(|driver| driver.args(std::env::args().skip(1)))
            .help(|help| help.version(env!("CARGO_PKG_VERSION")))
            .build(),
    )
    .run()
    .unwrap();

    #[cfg(windows)]
    {
        let _ = teamy_windows::console::enable_ansi_support();
        teamy_windows::string::warn_if_utf8_not_enabled();
    }

    let requested_format = cli.output_format;
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?;
    let output = runtime.block_on(cli.invoke())?;
    let exit_code = output.exit_code();
    output.emit(requested_format)?;
    Ok(std::process::ExitCode::from(exit_code))
}
