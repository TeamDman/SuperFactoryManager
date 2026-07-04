#![allow(clippy::doc_markdown)]

use super::super::curseforge_cli::build_resolved_metadata_plans;
use super::super::curseforge_cli::fetch_game_versions;
use super::super::curseforge_cli::fetch_project_files;
use super::super::curseforge_cli::filter_release_jars_by_branch;
use super::super::curseforge_cli::find_latest_historical_file_for_mc;
use super::super::curseforge_cli::get_ordered_release_jars;
use super::super::curseforge_cli::historical_mod_version;
use super::super::curseforge_cli::read_mod_version;
use super::super::curseforge_cli::resolve_project_id;
use super::super::curseforge_cli::to_comparison_name_set;
use crate::cli::jar::BranchSelector;
use crate::cli::jar::get_jar_dir;
use crate::cli::repo_root::get_repo_root;
use crate::curseforge::CurseforgeApiSecret;
use crate::curseforge::CurseforgeGameVersionId;
use crate::curseforge::CurseforgeHttpClient;
use color_eyre::owo_colors::OwoColorize;
use facet::Facet;
use figue as args;
use tracing::info;

/// Arguments for checking computed CurseForge release metadata.
#[derive(Facet, Debug)]
pub struct CurseforgeReleaseCheckArgs {
    /// Branch selector expression.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// CurseForge project ID (defaults to configured default project).
    #[facet(default, args::named)]
    pub project: Option<u64>,

    /// CurseForge Core API key; if omitted, CURSEFORGE_CORE_API_KEY is used.
    #[facet(default, args::named, rename = "api-key")]
    pub api_key: Option<String>,

    /// CurseForge API token; if omitted, CURSEFORGE_API_TOKEN is used, then 1Password lookup.
    #[facet(default, args::named)]
    pub token: Option<String>,

    /// 1Password secret reference used when credentials are omitted.
    #[facet(default, args::named, rename = "op-secret")]
    pub op_secret: Option<String>,
}

impl CurseforgeReleaseCheckArgs {
    /// # Errors
    ///
    /// Returns an error if computed metadata does not match historical uploads.
    pub fn invoke(self) -> eyre::Result<()> {
        check_minecraft_version_metadata(
            self.branch,
            self.project,
            self.api_key,
            self.token,
            self.op_secret,
        )
    }
}

fn check_minecraft_version_metadata(
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

    let token_value = CurseforgeApiSecret::resolve(token.clone(), op_secret.clone())?;
    let upload_client = CurseforgeHttpClient::new(&token_value)?;
    let game_versions = fetch_game_versions(&upload_client)?;
    let game_version_index = CurseforgeGameVersionId::build_index(&game_versions);
    let metadata_plans = build_resolved_metadata_plans(&jars, &game_version_index)?;

    let (core_key, credential_source) =
        CurseforgeApiSecret::resolve_core(api_key, token, op_secret)?;
    let core_client = CurseforgeHttpClient::new_core_api(&core_key)?;
    let project_files = fetch_project_files(&core_client, project_id, &credential_source)?;

    info!("{} {}", "Project ID:".cyan().bold(), project_id);
    info!("{} {}", "Mod version:".cyan().bold(), mod_version);
    info!("{} {}", "Jars checked:".cyan().bold(), metadata_plans.len());

    for plan in metadata_plans {
        let historical = find_latest_historical_file_for_mc(&project_files, &plan.mc_version)?;
        let historical_version = historical_mod_version(historical).ok_or_else(|| {
            eyre::eyre!(
                "Could not parse mod version from latest historical file {} for MC {}",
                historical.id,
                plan.mc_version
            )
        })?;

        if historical_version == mod_version {
            eyre::bail!(
                "Latest historical file already matches current mod version for MC {} (file id {}, version {}).\nThis release appears to already be published.",
                plan.mc_version,
                historical.id,
                mod_version
            );
        }

        let expected = to_comparison_name_set(&plan.metadata_names);
        let historical_set = to_comparison_name_set(&historical.game_versions);

        if expected != historical_set {
            let missing: Vec<String> = expected.difference(&historical_set).cloned().collect();
            let unexpected: Vec<String> = historical_set.difference(&expected).cloned().collect();
            eyre::bail!(
                "Historical metadata mismatch for MC {} (file id {}).\nexpected: {}\nhistorical: {}\nmissing: {}\nunexpected: {}",
                plan.mc_version,
                historical.id,
                plan.metadata_names.join(", "),
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

        let id_pairs: Vec<String> = plan
            .metadata_names
            .iter()
            .zip(plan.game_version_ids.iter())
            .map(|(name, id)| format!("{name}:{id}"))
            .collect();

        info!(
            "{} {} {} {} {}",
            "✓".green().bold(),
            plan.mc_version.blue().bold(),
            "matches historical file".dimmed(),
            historical.id,
            format!("({})", id_pairs.join(", ")).dimmed()
        );
    }

    info!(
        "{}",
        "Metadata check passed: computed metadata matches historical uploads."
            .green()
            .bold()
    );

    Ok(())
}
