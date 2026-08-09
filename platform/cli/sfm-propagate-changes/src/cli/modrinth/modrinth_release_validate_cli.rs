use super::modrinth_cli::download_sha1;
use super::modrinth_cli::fetch_project_versions;
use super::modrinth_cli::filter_release_jars_by_branch;
use super::modrinth_cli::find_latest_historical_version_for_mc;
use super::modrinth_cli::get_ordered_release_jars;
use super::modrinth_cli::parse_mc_version_from_jar_name;
use super::modrinth_cli::read_mod_version;
use super::modrinth_cli::resolve_project_id;
use super::modrinth_cli::select_download_file;
use super::modrinth_cli::sha1_hex;
use crate::cli::jar::BranchSelector;
use crate::cli::jar::get_jar_dir;
use crate::cli::repo_root::get_repo_root;
use crate::modrinth::ModrinthHttpClient;
use color_eyre::owo_colors::OwoColorize;
use eyre::Context;
use facet::Facet;
use figue as args;
use tracing::info;

/// Arguments for validating remote Modrinth jars against local release jars.
#[derive(Facet, Debug)]
pub struct ModrinthReleaseValidateArgs {
    /// Branch selector used to choose release jar Minecraft versions.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// Modrinth project id/slug (defaults to Super Factory Manager).
    #[facet(default, args::named)]
    pub project: Option<String>,
}

impl ModrinthReleaseValidateArgs {
    /// # Errors
    ///
    /// Returns an error if remote hashes do not match local release jars.
    pub fn invoke(self) -> eyre::Result<()> {
        validate_release_hashes(self.branch, self.project)
    }
}

fn validate_release_hashes(branch: BranchSelector, project: Option<String>) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;

    let repo_root = get_repo_root()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let jar_dir = get_jar_dir()?;

    let mod_version = read_mod_version(&gradle_properties)?;
    let all_jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
    let branch_query = branch.into_query()?;
    let jars = filter_release_jars_by_branch(all_jars, &branch_query)?;

    let client = ModrinthHttpClient::new(None)?;
    let existing_versions = fetch_project_versions(&client, &project_id)?;

    info!("{} {}", "Project ID:".cyan().bold(), project_id);
    info!("{} {}", "Mod version:".cyan().bold(), mod_version);
    info!("{} {}", "Jars checked:".cyan().bold(), jars.len());

    for jar in jars {
        let mc_version = parse_mc_version_from_jar_name(&jar)?;
        let local_bytes = std::fs::read(&jar)
            .wrap_err_with(|| format!("Failed to read local jar for hashing: {}", jar.display()))?;
        let local_sha1 = sha1_hex(&local_bytes);

        let historical = find_latest_historical_version_for_mc(&existing_versions, &mc_version)?;
        let historical_version_number = historical
            .version_number
            .as_deref()
            .unwrap_or_default()
            .trim();
        if historical_version_number != mod_version {
            eyre::bail!(
                "Latest historical Modrinth version for MC {} was {}, expected {} (version id {}).",
                mc_version,
                if historical_version_number.is_empty() {
                    "<missing>"
                } else {
                    historical_version_number
                },
                mod_version,
                historical.id
            );
        }

        let remote_file = select_download_file(historical, &mc_version, &mod_version)?;
        let remote_sha1 = remote_file
            .hashes
            .sha1
            .as_deref()
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .ok_or_else(|| {
                eyre::eyre!(
                    "Modrinth version {} file {} did not contain a sha1 hash",
                    historical.id,
                    remote_file.filename
                )
            })?
            .to_ascii_lowercase();

        if local_sha1 != remote_sha1 {
            eyre::bail!(
                "Hash mismatch (local vs remote metadata) for MC {} (version id {}, file {}).\nlocal sha1: {}\nremote sha1: {}",
                mc_version,
                historical.id,
                remote_file.filename,
                local_sha1,
                remote_sha1
            );
        }

        let downloaded_sha1 = download_sha1(&client, &remote_file.url)?;
        if downloaded_sha1 != local_sha1 {
            eyre::bail!(
                "Hash mismatch (local vs downloaded) for MC {} (version id {}, file {}).\nlocal sha1: {}\ndownloaded sha1: {}",
                mc_version,
                historical.id,
                remote_file.filename,
                local_sha1,
                downloaded_sha1
            );
        }

        info!(
            "{} {} {} {} {}",
            "✓".green().bold(),
            mc_version.blue().bold(),
            "hash validated".dimmed(),
            historical.id,
            format!("({})", remote_file.filename).dimmed()
        );
    }

    info!(
        "{}",
        "Hash validation passed: downloaded Modrinth files match local release jars."
            .green()
            .bold()
    );

    Ok(())
}
