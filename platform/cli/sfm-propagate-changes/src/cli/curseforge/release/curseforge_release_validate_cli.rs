#![allow(clippy::doc_markdown)]

use super::super::curseforge_cli::download_sha1;
use super::super::curseforge_cli::fetch_project_files;
use super::super::curseforge_cli::filter_release_jars_by_branch;
use super::super::curseforge_cli::find_latest_historical_file_for_mc;
use super::super::curseforge_cli::find_sha1_hash;
use super::super::curseforge_cli::get_ordered_release_jars;
use super::super::curseforge_cli::historical_mod_version;
use super::super::curseforge_cli::parse_mc_version_from_jar_name;
use super::super::curseforge_cli::read_mod_version;
use super::super::curseforge_cli::resolve_project_id;
use super::super::curseforge_cli::sha1_hex;
use crate::cli::jar::BranchSelector;
use crate::cli::jar::get_jar_dir;
use crate::cli::repo_root::get_repo_root;
use crate::curseforge::CurseforgeApiSecret;
use crate::curseforge::CurseforgeHttpClient;
use color_eyre::owo_colors::OwoColorize;
use eyre::Context;
use facet::Facet;
use figue as args;
use tracing::info;

/// Arguments for validating remote CurseForge files against local release jars.
#[derive(Facet, Debug)]
pub struct CurseforgeReleaseValidateArgs {
    /// Branch selector expression.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// CurseForge project ID (defaults to configured default project).
    #[facet(default, args::named)]
    pub project: Option<u64>,

    /// CurseForge Core API key; if omitted, CURSEFORGE_CORE_API_KEY is used.
    #[facet(default, args::named)]
    pub api_key: Option<String>,

    /// CurseForge API token; if omitted, CURSEFORGE_API_TOKEN is used, then 1Password lookup.
    #[facet(default, args::named)]
    pub token: Option<String>,

    /// 1Password secret reference used when credentials are omitted.
    #[facet(default, args::named)]
    pub op_secret: Option<String>,
}

impl CurseforgeReleaseValidateArgs {
    /// # Errors
    ///
    /// Returns an error if remote file hashes do not match local release jars.
    pub fn invoke(self) -> eyre::Result<()> {
        validate_release_hashes(
            self.branch,
            self.project,
            self.api_key,
            self.token,
            self.op_secret,
        )
    }
}

fn validate_release_hashes(
    branch: BranchSelector,
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    let branch_query = branch.into_query()?;
    let project_id = resolve_project_id(project)?;

    let repo_root = get_repo_root()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let jar_dir = get_jar_dir()?;

    let mod_version = read_mod_version(&gradle_properties)?;
    let all_jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
    let jars = filter_release_jars_by_branch(all_jars, &branch_query)?;

    let (core_key, credential_source) =
        CurseforgeApiSecret::resolve_core(api_key, token, op_secret)?;
    let core_client = CurseforgeHttpClient::new_core_api(&core_key)?;
    let project_files = fetch_project_files(&core_client, project_id, &credential_source)?;

    info!("{} {}", "Project ID:".cyan().bold(), project_id);
    info!("{} {}", "Mod version:".cyan().bold(), mod_version);
    info!("{} {}", "Jars checked:".cyan().bold(), jars.len());

    for jar in jars {
        let mc_version = parse_mc_version_from_jar_name(&jar)?;
        let local_bytes = std::fs::read(&jar)
            .wrap_err_with(|| format!("Failed to read local jar for hashing: {}", jar.display()))?;
        let local_sha1 = sha1_hex(&local_bytes);

        let historical = find_latest_historical_file_for_mc(&project_files, &mc_version)?;
        let historical_version = historical_mod_version(historical).ok_or_else(|| {
            eyre::eyre!(
                "Could not parse mod version from latest historical file {} for MC {}",
                historical.id,
                mc_version
            )
        })?;

        if historical_version != mod_version {
            eyre::bail!(
                "Latest historical CurseForge file for MC {} was {}, expected {} (file id {}).",
                mc_version,
                historical_version,
                mod_version,
                historical.id
            );
        }

        let remote_sha1 = find_sha1_hash(&historical.hashes).ok_or_else(|| {
            eyre::eyre!(
                "CurseForge file {} did not include a sha1 hash in metadata",
                historical.id
            )
        })?;

        if local_sha1 != remote_sha1 {
            eyre::bail!(
                "Hash mismatch (local vs remote metadata) for MC {} (file id {}).\nlocal sha1: {}\nremote sha1: {}",
                mc_version,
                historical.id,
                local_sha1,
                remote_sha1
            );
        }

        let download_url = historical.download_url.as_deref().ok_or_else(|| {
            eyre::eyre!(
                "CurseForge file {} did not include a download URL",
                historical.id
            )
        })?;

        let downloaded_sha1 = download_sha1(&core_client, download_url)?;
        if downloaded_sha1 != local_sha1 {
            eyre::bail!(
                "Hash mismatch (local vs downloaded) for MC {} (file id {}).\nlocal sha1: {}\ndownloaded sha1: {}",
                mc_version,
                historical.id,
                local_sha1,
                downloaded_sha1
            );
        }

        let file_label = historical
            .file_name
            .as_deref()
            .unwrap_or("<unknown>")
            .to_string();

        info!(
            "{} {} {} {} {}",
            "✓".green().bold(),
            mc_version.blue().bold(),
            "hash validated".dimmed(),
            historical.id,
            format!("({file_label})").dimmed()
        );
    }

    info!(
        "{}",
        "Hash validation passed: downloaded CurseForge files match local release jars."
            .green()
            .bold()
    );

    Ok(())
}
