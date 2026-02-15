use crate::cli::args::CliCommand;
use crate::cli::args::ConfigCommands;
use crate::cli::args::LanguageFileCommands;
use crate::cli::args::RepoCommands;
use crate::cli::args::RootCommands;
use crate::command::language_file::language_file_open_command::LanguageFileOpenCommand;
use crate::command::StandaloneCommand;
use crate::command::language_file::language_file_check_command::LanguageFileCheckCommand;
use crate::command::language_file::language_file_list_command::LanguageFileListCommand;
use crate::config::TranslationHelperConfig;
use crate::config::get_root_dir;
use crate::domain_object::repository::discover_repositories;
use eye_config::persistable_state::PersistableState;
use serde_json::json;
use std::env;
use tracing::info;

pub async fn handle_command(command: CliCommand) -> eyre::Result<()> {
    match command {
        CliCommand::LanguageFile { file_command } => match file_command {
            LanguageFileCommands::List => {
                let rtn = LanguageFileListCommand.execute().await?;
                let json_output = serde_json::to_string_pretty(&rtn)?;
                println!("{json_output}");
            }
            LanguageFileCommands::Check => {
                let rtn = LanguageFileCheckCommand.execute().await?;
                let json_output = serde_json::to_string_pretty(&rtn)?;
                println!("{json_output}");
                info!("Found {} problems in language files", rtn.problems.len());
            }
            LanguageFileCommands::Open => {
                LanguageFileOpenCommand.execute().await?;
            }
        },
        CliCommand::Config { config_command } => match config_command {
            ConfigCommands::Show => {
                let config = TranslationHelperConfig::load().await?;
                info!("Configuration:");
                let config_json = serde_json::to_string_pretty(&config)?;
                println!("{config_json}");
            }
            ConfigCommands::Root { root_command } => match root_command {
                RootCommands::Set { path } => {
                    let mut config = TranslationHelperConfig::load().await?;
                    let absolute_path = if path.is_absolute() {
                        path.canonicalize()?
                    } else {
                        env::current_dir()?.join(path).canonicalize()?
                    };
                    config.root_dir = Some(absolute_path.clone());
                    config.save().await?;
                    info!("Root directory set to: {}", absolute_path.display());
                    println!("{}", absolute_path.display());
                }
            },
        },
        CliCommand::Repo { repo_command } => match repo_command {
            RepoCommands::List => {
                let root_dir = get_root_dir().await?;
                let repos = discover_repositories(&root_dir)?
                    .into_iter()
                    .map(|(repo, game_version)| {
                        json!({
                            "repo_path": repo,
                            "game_version": game_version
                        })
                    })
                    .collect::<Vec<_>>();
                info!("Discovered {} repos", repos.len());
                let json_output = serde_json::to_string_pretty(&repos)?;
                println!("{json_output}");
            }
        },
    }
    Ok(())
}
