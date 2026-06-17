use super::modrinth_cli::build_release_plans;
use super::modrinth_cli::compute_wrapped_release_changelog;
use super::modrinth_cli::create_project_version;
use super::modrinth_cli::filter_release_jars_by_branch;
use super::modrinth_cli::get_ordered_release_jars;
use super::modrinth_cli::modrinth_versions_url;
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

/// Arguments for creating new Modrinth versions for each release jar.
#[derive(Facet, Debug)]
pub struct ModrinthReleaseNowArgs {
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

    /// Print planned uploads and metadata without uploading.
    #[facet(default, args::named, rename = "dry-run")]
    pub dry_run: bool,
}

impl ModrinthReleaseNowArgs {
    /// # Errors
    ///
    /// Returns an error if release metadata cannot be computed or uploaded.
    pub fn invoke(self) -> eyre::Result<()> {
        release_now(
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
    reason = "release flow is intentionally linear"
)]
fn release_now(
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
    let changelog_section = compute_wrapped_release_changelog(&repo_root, &mod_version)?;
    let all_jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
    let branch_query = branch.into_query()?;
    let jars = filter_release_jars_by_branch(all_jars, &branch_query)?;
    let plans = build_release_plans(&jars, &mod_version)?;

    info!("{} {}", "Project ID:".cyan().bold(), project_id);
    info!("{} {}", "Mod version:".cyan().bold(), mod_version);
    info!("{} {}", "Jar dir:".cyan().bold(), jar_dir.display());
    if dry_run {
        info!(
            "{} {}",
            "Mode:".yellow().bold(),
            "dry-run (no uploads)".yellow().bold()
        );
    }
    info!("{}", "Changelog:".cyan().bold());
    info!("{changelog_section}");

    info!("{}", "Metadata preflight:".cyan().bold());
    for plan in &plans {
        info!(
            "  {} {} {} {} {} {}",
            "MC".dimmed(),
            plan.mc_version.blue().bold(),
            "=>".dimmed(),
            "loaders".dimmed(),
            plan.loaders.join(", "),
            format!("(game versions: {})", plan.game_versions.join(", ")).dimmed()
        );
    }

    let action = if dry_run {
        "run the dry-run release preview"
    } else {
        "create Modrinth versions"
    };

    let prompt = format!(
        "{} {} {}",
        "Proceed to".yellow().bold(),
        action.yellow().bold(),
        "? (y/N)".yellow().bold()
    );
    if !prompt_yes_no(&prompt)? {
        info!("{}", "Aborted release-now.".yellow().bold());
        info!("{}", modrinth_versions_url(&project_id));
        return Ok(());
    }

    let client = if dry_run {
        None
    } else {
        let token_value = ModrinthApiSecret::resolve(token, op_secret)?;
        Some(ModrinthHttpClient::new(Some(&token_value))?)
    };

    for plan in plans {
        let jar_name = plan
            .jar_path
            .file_name()
            .and_then(OsStr::to_str)
            .map(ToString::to_string)
            .ok_or_else(|| eyre::eyre!("Invalid jar filename: {}", plan.jar_path.display()))?;

        info!(
            "{} {} {} {}",
            "Uploading".white().bold(),
            jar_name.magenta().bold(),
            "for MC".dimmed(),
            plan.mc_version.blue().bold()
        );

        if dry_run {
            info!(
                "  {} {}",
                "metadata:".dimmed(),
                format!(
                    "version_number={}, loaders=[{}], game_versions=[{}]",
                    plan.version_number,
                    plan.loaders.join(", "),
                    plan.game_versions.join(", ")
                )
                .dimmed()
            );
            continue;
        }

        let upload_id = create_project_version(
            client
                .as_ref()
                .ok_or_else(|| eyre::eyre!("internal error: missing Modrinth client for upload"))?,
            &project_id,
            &plan,
            &changelog_section,
        )?;

        info!(
            "  {} {}",
            "created version id".green().bold(),
            upload_id.green().bold()
        );
    }

    if !dry_run {
        info!("{}", "Modrinth release upload complete.".green().bold());
    }
    info!("{}", modrinth_versions_url(&project_id));

    Ok(())
}
