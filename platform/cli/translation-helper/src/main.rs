
pub mod cli;
pub mod config;
pub mod command;
pub mod domain_object;

use clap::Parser;
use cli::args::Cli;
use cli::init_tracing::init_tracing;

use crate::cli::handle::handle_command;

#[tokio::main]
pub async fn main() -> eyre::Result<()> {
    color_eyre::install()?;

    let cli = Cli::parse();

    // Initialize tracing with the global args
    init_tracing(&cli.global, std::io::stderr)?;

    handle_command(cli.command).await?;

    Ok(())
}
