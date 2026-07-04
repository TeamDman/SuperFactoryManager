#![allow(clippy::doc_markdown)]

use super::curseforge_cli::POPULAR_DOWNLOAD_THRESHOLD;
use super::curseforge_cli::fetch_project_files;
use super::curseforge_cli::latest_two_release_files_for_mc;
use super::curseforge_cli::resolve_project_id;
use crate::branch_targets::MinecraftVersion;
use crate::branch_targets::discover_worktree_targets;
use crate::curseforge::CurseforgeApiSecret;
use crate::curseforge::CurseforgeHttpClient;
use crate::curseforge::CurseforgeProjectFileId;
use crate::curseforge::CurseforgeProjectId;
use crate::paths::APP_HOME;
use eyre::Context;
use facet::Facet;
use figue as args;
use reqwest::blocking::Client;
use std::collections::BTreeSet;
use std::path::PathBuf;
use std::time::Duration;
use tracing::info;

pub(super) const CURSEFORGE_POPULAR_BRANCHES_FILE: &str =
    "curseforge_popular_minecraft_versions.txt";
const CURSEFORGE_PUBLIC_API_ROOT: &str = "https://www.curseforge.com/api/v1";

/// Arguments for locally cached CurseForge popularity operations.
#[derive(Facet, Debug)]
pub struct CurseforgePopularArgs {
    /// Popularity subcommand.
    #[facet(args::subcommand)]
    pub command: CurseforgePopularCommand,
}

impl CurseforgePopularArgs {
    /// # Errors
    ///
    /// Returns an error if the selected popularity command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// Locally cached CurseForge popularity subcommands.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgePopularCommand {
    /// Add a Minecraft version to the local popular cache.
    Add(CurseforgePopularAddArgs),
    /// Remove a Minecraft version from the local popular cache.
    Remove(CurseforgePopularRemoveArgs),
    /// List the local popular cache.
    List(CurseforgePopularListArgs),
    /// Sync the local popular cache from CurseForge public per-file download counts.
    Sync(CurseforgePopularSyncArgs),
}

impl CurseforgePopularCommand {
    /// # Errors
    ///
    /// Returns an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Add(args) => args.invoke(),
            Self::Remove(args) => args.invoke(),
            Self::List(args) => args.invoke(),
            Self::Sync(args) => args.invoke(),
        }
    }
}

/// Arguments for adding a Minecraft version to the local popular cache.
#[derive(Facet, Debug)]
pub struct CurseforgePopularAddArgs {
    /// Minecraft version to mark popular.
    #[facet(args::positional)]
    pub minecraft_version: String,
}

impl CurseforgePopularAddArgs {
    /// # Errors
    ///
    /// Returns an error if the cache cannot be updated.
    pub fn invoke(self) -> eyre::Result<()> {
        let mut versions = read_popular_minecraft_versions()?;
        versions.insert(MinecraftVersion::parse(&self.minecraft_version)?);
        write_popular_minecraft_versions(&versions)?;
        info!("Popular Minecraft versions:");
        print_popular_versions(&versions);
        Ok(())
    }
}

/// Arguments for removing a Minecraft version from the local popular cache.
#[derive(Facet, Debug)]
pub struct CurseforgePopularRemoveArgs {
    /// Minecraft version to unmark popular.
    #[facet(args::positional)]
    pub minecraft_version: String,
}

impl CurseforgePopularRemoveArgs {
    /// # Errors
    ///
    /// Returns an error if the cache cannot be updated.
    pub fn invoke(self) -> eyre::Result<()> {
        let mut versions = read_popular_minecraft_versions()?;
        versions.remove(&MinecraftVersion::parse(&self.minecraft_version)?);
        write_popular_minecraft_versions(&versions)?;
        info!("Popular Minecraft versions:");
        print_popular_versions(&versions);
        Ok(())
    }
}

/// Arguments for listing the local popular cache.
#[derive(Facet, Debug)]
pub struct CurseforgePopularListArgs;

impl CurseforgePopularListArgs {
    /// # Errors
    ///
    /// Returns an error if the cache cannot be read.
    pub fn invoke(self) -> eyre::Result<()> {
        let versions = read_popular_minecraft_versions()?;
        if versions.is_empty() {
            info!(
                "No popular Minecraft versions cached. Use `sfm-propagate-changes curseforge popular add <version>` or `sync`."
            );
            return Ok(());
        }
        info!("Popular Minecraft versions:");
        print_popular_versions(&versions);
        Ok(())
    }
}

/// Arguments for syncing the local popular cache from CurseForge.
#[derive(Facet, Debug)]
pub struct CurseforgePopularSyncArgs {
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

    /// Minimum public downloads across the latest two release files for a Minecraft version.
    #[facet(default = POPULAR_DOWNLOAD_THRESHOLD, args::named)]
    pub threshold: u64,
}

impl CurseforgePopularSyncArgs {
    /// # Errors
    ///
    /// Returns an error if CurseForge cannot be queried or the cache cannot be updated.
    pub fn invoke(self) -> eyre::Result<()> {
        sync_popular_minecraft_versions(
            self.project,
            self.api_key,
            self.token,
            self.op_secret,
            self.threshold,
        )
    }
}

pub(crate) fn resolve_cached_popular_branch_query()
-> eyre::Result<crate::branch_targets::BranchQuery> {
    let versions = read_popular_minecraft_versions()?;
    if versions.is_empty() {
        eyre::bail!(
            "No popular Minecraft versions cached. Use `sfm-propagate-changes curseforge popular add <version>` or `sfm-propagate-changes curseforge popular sync` first."
        );
    }
    let predicates = versions.iter().map(ToString::to_string).collect::<Vec<_>>();
    crate::branch_targets::BranchQuery::parse(&predicates.join(" OR "))
}

pub(crate) fn read_popular_minecraft_versions() -> eyre::Result<BTreeSet<MinecraftVersion>> {
    let path = popular_versions_path();
    if !path.is_file() {
        return Ok(BTreeSet::new());
    }
    let content = std::fs::read_to_string(&path)
        .wrap_err_with(|| format!("Failed to read popular cache: {}", path.display()))?;
    content
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(MinecraftVersion::parse)
        .collect()
}

fn write_popular_minecraft_versions(versions: &BTreeSet<MinecraftVersion>) -> eyre::Result<()> {
    APP_HOME.ensure_dir()?;
    let path = popular_versions_path();
    let mut content = String::new();
    content.push_str("# Managed by `sfm-propagate-changes curseforge popular`.\n");
    content.push_str("# One Minecraft version per line.\n");
    for version in versions {
        content.push_str(version.as_str());
        content.push('\n');
    }
    std::fs::write(&path, content)
        .wrap_err_with(|| format!("Failed to write popular cache: {}", path.display()))
}

fn popular_versions_path() -> PathBuf {
    APP_HOME.file_path(CURSEFORGE_POPULAR_BRANCHES_FILE)
}

fn sync_popular_minecraft_versions(
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
    threshold: u64,
) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;
    let (key, credential_source) = CurseforgeApiSecret::resolve_core(api_key, token, op_secret)?;
    let core_client = CurseforgeHttpClient::new_core_api(&key)?;
    let public_client = Client::builder()
        .timeout(Duration::from_mins(2))
        .build()
        .wrap_err("Failed to build CurseForge public API HTTP client")?;
    let files = fetch_project_files(&core_client, project_id, &credential_source)?;
    let target_versions = discover_worktree_targets()?
        .into_iter()
        .filter_map(|target| target.mc_version)
        .collect::<BTreeSet<_>>();

    let mut popular_versions = BTreeSet::new();
    info!("minecraft_version\tpopular_downloads\tfile_id\tfile_name");
    for mc_version in target_versions {
        let candidate_files = latest_two_release_files_for_mc(&files, &mc_version);
        let mut best: Option<(u64, CurseforgeProjectFileId, String)> = None;
        for file in candidate_files {
            let downloads = fetch_public_file_downloads(&public_client, project_id, file.id)?;
            let file_name = file
                .file_name
                .clone()
                .unwrap_or_else(|| "<unknown>".to_string());
            if best
                .as_ref()
                .is_none_or(|(best_downloads, _, _)| downloads > *best_downloads)
            {
                best = Some((downloads, file.id, file_name));
            }
        }

        let Some((downloads, file_id, file_name)) = best else {
            continue;
        };
        info!("{mc_version}\t{downloads}\t{file_id}\t{file_name}");
        if downloads >= threshold {
            popular_versions.insert(mc_version);
        }
    }

    write_popular_minecraft_versions(&popular_versions)?;
    info!("Synced popular Minecraft versions with threshold {threshold}:");
    print_popular_versions(&popular_versions);
    Ok(())
}

fn fetch_public_file_downloads(
    client: &Client,
    project_id: CurseforgeProjectId,
    file_id: CurseforgeProjectFileId,
) -> eyre::Result<u64> {
    let url = format!("{CURSEFORGE_PUBLIC_API_ROOT}/mods/{project_id}/files/{file_id}");
    let response = client
        .get(&url)
        .send()
        .wrap_err_with(|| format!("Failed to query public CurseForge file: {url}"))?;
    let status = response.status();
    let body = response
        .text()
        .wrap_err("Failed to read public CurseForge file response")?;
    if !status.is_success() {
        eyre::bail!("CurseForge public file API failed ({status}): {body}");
    }
    let envelope: CurseforgePublicFileEnvelope =
        facet_json::from_str(&body).wrap_err("Failed to parse public CurseForge file response")?;
    Ok(envelope.data.total_downloads)
}

fn print_popular_versions(versions: &BTreeSet<MinecraftVersion>) {
    for version in versions {
        info!("{version}");
    }
}

#[derive(Facet)]
struct CurseforgePublicFileEnvelope {
    data: CurseforgePublicFile,
}

#[derive(Facet)]
struct CurseforgePublicFile {
    #[facet(rename = "totalDownloads")]
    total_downloads: u64,
}
