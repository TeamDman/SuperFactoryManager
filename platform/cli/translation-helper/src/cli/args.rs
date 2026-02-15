use crate::cli::global_args::GlobalArgs;
use clap::Parser;
use clap::Subcommand;

#[derive(Parser)]
#[command(name = "translation-helper")]
#[command(version = "0.1.0")]
#[command(about = "A CLI tool to help with translation file management")]
pub struct Cli {
    #[command(flatten)]
    pub global: GlobalArgs,

    #[command(subcommand)]
    pub command: CliCommand,
}

#[derive(Subcommand)]
pub enum CliCommand {
    /// Commands for working with translation files
    #[command(alias = "lang")]
    LanguageFile {
        #[command(subcommand)]
        file_command: LanguageFileCommands,
    },
    /// Configuration commands
    Config {
        #[command(subcommand)]
        config_command: ConfigCommands,
    },
    /// Repository commands
    Repo {
        #[command(subcommand)]
        repo_command: RepoCommands,
    },
}

#[derive(Subcommand)]
pub enum LanguageFileCommands {
    /// List translation files
    List,
    /// Validates assumptions about translation files
    Check,
    /// Opens the files in vscode
    Open,
}

#[derive(Subcommand)]
pub enum ConfigCommands {
    /// Show the current configuration
    Show,
    /// Root directory configuration
    Root {
        #[command(subcommand)]
        root_command: RootCommands,
    },
}

#[derive(Subcommand)]
pub enum RootCommands {
    /// Set the root directory
    Set {
        /// Path to set as root directory
        path: std::path::PathBuf,
    },
}

#[derive(Subcommand)]
pub enum RepoCommands {
    /// List repositories adjacent to the root directory
    List,
}
