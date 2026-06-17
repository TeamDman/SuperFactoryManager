use super::modrinth_cli::amend_version_changelog;
use super::modrinth_cli::compute_wrapped_release_changelog;
use super::modrinth_cli::fetch_project_versions;
use super::modrinth_cli::filter_release_jars_by_branch;
use super::modrinth_cli::find_latest_historical_version_for_mc;
use super::modrinth_cli::get_ordered_release_jars;
use super::modrinth_cli::parse_mc_version_from_jar_name;
use super::modrinth_cli::prompt_yes_no;
use super::modrinth_cli::read_mod_version;
use super::modrinth_cli::resolve_project_id;
use crate::cli::jar::BranchSelector;
use crate::cli::jar::get_jar_dir;
use crate::cli::repo_root::get_repo_root;
use crate::modrinth::ModrinthApiSecret;
use crate::modrinth::ModrinthHttpClient;
use color_eyre::owo_colors::OwoColorize;
use facet::Facet;
use figue as args;
use std::ffi::OsStr;
use tracing::info;

/// Arguments for amending Modrinth changelogs for current release jars.
#[derive(Facet, Debug)]
pub struct ModrinthReleaseAmendArgs {
    /// Branch selector used to choose release jar Minecraft versions. Defaults to `core`.
    #[facet(default, args::named)]
    pub branch: BranchSelector,

    /// Modrinth project id/slug (defaults to Super Factory Manager).
    #[facet(default, args::named)]
    pub project: Option<String>,

    /// Modrinth API token; if omitted, `MODRINTH_TOKEN` is used, then 1Password lookup.
    #[facet(default, args::named)]
    pub token: Option<String>,

    /// 1Password secret reference used when token is omitted.
    #[facet(default, args::named, rename = "op-secret")]
    pub op_secret: Option<String>,

    /// Resolve remote target and print the amend plan without updating Modrinth.
    #[facet(default, args::named, rename = "dry-run")]
    pub dry_run: bool,
}

impl ModrinthReleaseAmendArgs {
    /// # Errors
    ///
    /// Returns an error if the remote targets cannot be resolved or amended.
    pub fn invoke(self) -> eyre::Result<()> {
        release_amend(
            self.branch,
            self.project,
            self.token,
            self.op_secret,
            self.dry_run,
        )
    }
}

#[expect(
    clippy::too_many_lines,
    reason = "amend flow is clearer when kept in release-operation order"
)]
fn release_amend(
    branch: BranchSelector,
    project: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
    dry_run: bool,
) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;

    let repo_root = get_repo_root()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let jar_dir = get_jar_dir()?;

    let mod_version = read_mod_version(&gradle_properties)?;
    let wrapped_changelog = compute_wrapped_release_changelog(&repo_root, &mod_version)?;

    let token_value = if dry_run {
        None
    } else {
        Some(ModrinthApiSecret::resolve(token, op_secret)?)
    };
    let client = ModrinthHttpClient::new(token_value.as_ref())?;

    let all_jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
    let branch_query = branch.into_query()?;
    let jars = filter_release_jars_by_branch(all_jars, &branch_query)?;
    let mut target_versions: Vec<(String, String)> = Vec::new();
    for jar in jars {
        let mc_version = parse_mc_version_from_jar_name(&jar)?;
        let jar_name = jar
            .file_name()
            .and_then(OsStr::to_str)
            .map(ToString::to_string)
            .ok_or_else(|| eyre::eyre!("Invalid jar filename: {}", jar.display()))?;

        if target_versions
            .iter()
            .all(|(existing_mc, _)| existing_mc != &mc_version)
        {
            target_versions.push((mc_version, jar_name));
        }
    }

    let versions = fetch_project_versions(&client, &project_id)?;
    let mut amend_targets: Vec<(String, String, String)> = Vec::new();
    for (mc_version, jar_name) in target_versions {
        let version = find_latest_historical_version_for_mc(&versions, &mc_version)?;
        let historical_version_number =
            version.version_number.as_deref().unwrap_or_default().trim();
        if historical_version_number != mod_version {
            eyre::bail!(
                "Refusing to amend Modrinth version for MC {} because the latest remote version was {}, expected {} (version id {}).",
                mc_version,
                if historical_version_number.is_empty() {
                    "<missing>"
                } else {
                    historical_version_number
                },
                mod_version,
                version.id
            );
        }
        amend_targets.push((mc_version, version.id.clone(), jar_name));
    }

    info!("{} {}", "Project ID:".cyan().bold(), project_id);
    info!("{} {}", "Mod version:".cyan().bold(), mod_version);
    if dry_run {
        info!(
            "{} {}",
            "Mode:".yellow().bold(),
            "dry-run (no Modrinth mutations)".yellow().bold()
        );
    }
    info!("{}", "Amend targets:".cyan().bold());
    for (mc_version, version_id, jar_name) in &amend_targets {
        info!(
            "  {} {} {} {} {} {}",
            "MC".dimmed(),
            mc_version.blue().bold(),
            "version id".dimmed(),
            version_id,
            "name".dimmed(),
            jar_name
        );
    }

    if dry_run {
        info!(
            "{}",
            "Dry-run complete: remote Modrinth targets resolved; no versions amended."
                .green()
                .bold()
        );
        return Ok(());
    }

    let prompt = format!(
        "{} {}",
        "Proceed to amend changelog on these versions?"
            .yellow()
            .bold(),
        "(y/N)".yellow().bold()
    );
    if !prompt_yes_no(&prompt)? {
        info!("{}", "Aborted release amend.".yellow().bold());
        return Ok(());
    }

    for (mc_version, version_id, jar_name) in &amend_targets {
        info!(
            "{} {} {} {} {} {}",
            "Amending version".white().bold(),
            version_id.magenta().bold(),
            "for MC".dimmed(),
            mc_version.blue().bold(),
            "as".dimmed(),
            jar_name
        );

        amend_version_changelog(&client, version_id, &wrapped_changelog)?;
    }

    info!(
        "{}",
        "Modrinth release changelog amend complete.".green().bold()
    );

    Ok(())
}
