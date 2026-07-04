#![allow(clippy::doc_markdown)]

use super::super::curseforge_cli::fetch_project_files;
use super::super::curseforge_cli::resolve_project_id;
use super::super::read_popular_minecraft_versions;
use crate::curseforge::CurseforgeApiSecret;
use crate::curseforge::CurseforgeHttpClient;
use crate::curseforge::CurseforgeProjectFileItem;
use facet::Facet;
use figue as args;
use tracing::info;

/// Arguments for listing CurseForge project files.
#[derive(Facet, Debug)]
pub struct CurseforgeProjectFileListArgs {
    /// CurseForge project ID (defaults to configured default project).
    #[facet(default, args::named)]
    pub project: Option<u64>,

    /// CurseForge Core API key; if omitted, CURSEFORGE_CORE_API_KEY is used.
    #[facet(default, args::named, rename = "api-key")]
    pub api_key: Option<String>,

    /// CurseForge API token; if omitted, CURSEFORGE_API_TOKEN is used, then 1Password lookup.
    #[facet(default, args::named)]
    pub token: Option<String>,

    /// 1Password secret reference used when token is omitted.
    #[facet(default, args::named, rename = "op-secret")]
    pub op_secret: Option<String>,

    /// Only show files matching the locally cached popular Minecraft versions.
    #[facet(default = false, args::named)]
    pub popular: bool,
}

impl CurseforgeProjectFileListArgs {
    /// # Errors
    ///
    /// Returns an error if CurseForge project files cannot be queried.
    pub fn invoke(self) -> eyre::Result<()> {
        list_project_files(
            self.project,
            self.api_key,
            self.token,
            self.op_secret,
            self.popular,
        )
    }
}

fn list_project_files(
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
    popular: bool,
) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;
    let (key, credential_source) = CurseforgeApiSecret::resolve_core(api_key, token, op_secret)?;
    let client = CurseforgeHttpClient::new_core_api(&key)?;
    let mut files = fetch_project_files(&client, project_id, &credential_source)?;

    if popular {
        let popular_versions = read_popular_minecraft_versions()?;
        files.retain(|file| {
            file.game_versions.iter().any(|version| {
                popular_versions
                    .iter()
                    .any(|popular_version| popular_version.as_str() == version)
            })
        });
    }

    if files.is_empty() {
        info!("No files found for project {project_id}.");
        return Ok(());
    }

    info!("Project {project_id} files:");
    info!("id\tdownloads\tfile_name\tdisplay_name\trelease_type\tfile_status\tgame_versions");
    for file in &files {
        print_file(file);
    }

    Ok(())
}

fn print_file(file: &CurseforgeProjectFileItem) {
    let file_name = file
        .file_name
        .clone()
        .unwrap_or_else(|| "<unknown>".to_string());
    let display_name = file
        .display_name
        .as_deref()
        .or(Some(file_name.as_str()))
        .unwrap_or("<unnamed>");
    let release_type = file
        .release_type
        .map_or_else(|| "<unknown>".to_string(), |value| value.to_string());
    let file_status = file
        .file_status
        .map_or_else(|| "<unknown>".to_string(), |value| value.to_string());
    let game_versions = if file.game_versions.is_empty() {
        "<none>".to_string()
    } else {
        file.game_versions.join(",")
    };
    info!(
        "{}\t{}\t{}\t{}\t{}\t{}\t{}",
        file.id,
        file.download_count,
        file_name,
        display_name,
        release_type,
        file_status,
        game_versions
    );
}
