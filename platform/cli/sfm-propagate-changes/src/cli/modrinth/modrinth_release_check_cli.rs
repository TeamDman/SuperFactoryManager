use super::modrinth_cli::build_release_plans;
use super::modrinth_cli::fetch_project_versions;
use super::modrinth_cli::filter_release_jars_by_branch;
use super::modrinth_cli::find_latest_historical_version_for_mc;
use super::modrinth_cli::get_ordered_release_jars;
use super::modrinth_cli::read_mod_version;
use super::modrinth_cli::resolve_project_id;
use super::modrinth_cli::to_normalized_set;
use crate::cli::jar::BranchSelector;
use crate::cli::jar::get_jar_dir;
use crate::cli::repo_root::get_repo_root;
use crate::modrinth::ModrinthHttpClient;
use color_eyre::owo_colors::OwoColorize;
use facet::Facet;
use figue as args;
use tracing::info;

/// Arguments for checking computed Modrinth release metadata.
#[derive(Facet, Debug)]
pub struct ModrinthReleaseCheckArgs {
    /// Branch selector used to choose release jar Minecraft versions.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// Modrinth project id/slug (defaults to Super Factory Manager).
    #[facet(default, args::named)]
    pub project: Option<String>,
}

impl ModrinthReleaseCheckArgs {
    /// # Errors
    ///
    /// Returns an error if release metadata does not match historical project versions.
    pub fn invoke(self) -> eyre::Result<()> {
        check_release_metadata(self.branch, self.project)
    }
}

#[expect(
    clippy::too_many_lines,
    reason = "metadata diff is easiest to read linearly"
)]
fn check_release_metadata(branch: BranchSelector, project: Option<String>) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;

    let repo_root = get_repo_root()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let jar_dir = get_jar_dir()?;

    let mod_version = read_mod_version(&gradle_properties)?;
    let all_jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
    let branch_query = branch.into_query()?;
    let jars = filter_release_jars_by_branch(all_jars, &branch_query)?;

    let plans = build_release_plans(&jars, &mod_version)?;
    let client = ModrinthHttpClient::new(None)?;
    let existing_versions = fetch_project_versions(&client, &project_id)?;

    info!("{} {}", "Project ID:".cyan().bold(), project_id);
    info!("{} {}", "Mod version:".cyan().bold(), mod_version);
    info!("{} {}", "Jars checked:".cyan().bold(), plans.len());

    for plan in plans {
        let historical =
            find_latest_historical_version_for_mc(&existing_versions, &plan.mc_version)?;

        if historical
            .version_number
            .as_deref()
            .is_some_and(|version| version.trim() == mod_version)
        {
            eyre::bail!(
                "Latest historical Modrinth version already matches current mod version for MC {} (version id {}, version number {}).\nThis release appears to already be published.",
                plan.mc_version,
                historical.id,
                mod_version
            );
        }

        let expected_loaders = to_normalized_set(&plan.loaders);
        let historical_loaders = to_normalized_set(&historical.loaders);
        if expected_loaders != historical_loaders {
            let missing: Vec<String> = expected_loaders
                .difference(&historical_loaders)
                .cloned()
                .collect();
            let unexpected: Vec<String> = historical_loaders
                .difference(&expected_loaders)
                .cloned()
                .collect();
            eyre::bail!(
                "Historical loader metadata mismatch for MC {} (version id {}).\nexpected: {}\nhistorical: {}\nmissing: {}\nunexpected: {}",
                plan.mc_version,
                historical.id,
                plan.loaders.join(", "),
                historical.loaders.join(", "),
                if missing.is_empty() {
                    "<none>".to_string()
                } else {
                    missing.join(", ")
                },
                if unexpected.is_empty() {
                    "<none>".to_string()
                } else {
                    unexpected.join(", ")
                }
            );
        }

        let expected_versions = to_normalized_set(&plan.game_versions);
        let historical_versions = to_normalized_set(&historical.game_versions);
        if expected_versions != historical_versions {
            let missing: Vec<String> = expected_versions
                .difference(&historical_versions)
                .cloned()
                .collect();
            let unexpected: Vec<String> = historical_versions
                .difference(&expected_versions)
                .cloned()
                .collect();
            eyre::bail!(
                "Historical game-version metadata mismatch for MC {} (version id {}).\nexpected: {}\nhistorical: {}\nmissing: {}\nunexpected: {}",
                plan.mc_version,
                historical.id,
                plan.game_versions.join(", "),
                historical.game_versions.join(", "),
                if missing.is_empty() {
                    "<none>".to_string()
                } else {
                    missing.join(", ")
                },
                if unexpected.is_empty() {
                    "<none>".to_string()
                } else {
                    unexpected.join(", ")
                }
            );
        }

        info!(
            "{} {} {} {} {}",
            "✓".green().bold(),
            plan.mc_version.blue().bold(),
            "matches historical version".dimmed(),
            &historical.id,
            format!(
                "(loaders: {}; game versions: {})",
                plan.loaders.join(", "),
                plan.game_versions.join(", ")
            )
            .dimmed()
        );
    }

    info!(
        "{}",
        "Metadata check passed: computed metadata matches historical Modrinth versions."
            .green()
            .bold()
    );

    Ok(())
}
