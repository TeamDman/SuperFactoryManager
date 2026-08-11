#![allow(clippy::doc_markdown)]

use super::super::curseforge_cli::DEFAULT_AMEND_SAFETY_AGE;
use super::super::curseforge_cli::amend_file_changelog;
use super::super::curseforge_cli::fetch_project_files;
use super::super::curseforge_cli::filter_release_jars_by_branch;
use super::super::curseforge_cli::find_latest_historical_file_for_mc;
use super::super::curseforge_cli::format_age;
use super::super::curseforge_cli::get_ordered_release_jars;
use super::super::curseforge_cli::historical_mod_version;
use super::super::curseforge_cli::parse_file_age;
use super::super::curseforge_cli::parse_mc_version_from_jar_name;
use super::super::curseforge_cli::parse_safety_age;
use super::super::curseforge_cli::prompt_yes_no;
use super::super::curseforge_cli::read_changelog_section;
use super::super::curseforge_cli::read_mod_version;
use super::super::curseforge_cli::resolve_project_id;
use crate::cli::jar::BranchSelector;
use crate::cli::jar::get_jar_dir;
use crate::cli::repo_root::get_repo_root;
use crate::curseforge::CurseforgeApiSecret;
use crate::curseforge::CurseforgeHttpClient;
use crate::curseforge::CurseforgeProjectFileId;
use chrono::Utc;
use color_eyre::owo_colors::OwoColorize;
use facet::Facet;
use figue as args;
use std::ffi::OsStr;
use std::time::Duration;
use tracing::info;

/// Arguments for amending CurseForge changelogs for current release jars.
#[derive(Facet, Debug)]
pub struct CurseforgeReleaseAmendArgs {
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

    /// Refuse amending files older than this age (examples: 30m, 2h, 45s).
    #[facet(default, args::named)]
    pub safety_age: Option<String>,

    /// Resolve remote target and print the amend plan without updating CurseForge.
    #[facet(default, args::named)]
    pub dry_run: bool,
}

impl CurseforgeReleaseAmendArgs {
    /// # Errors
    ///
    /// Returns an error if release files cannot be resolved or amended.
    pub fn invoke(self) -> eyre::Result<()> {
        release_amend(
            self.branch,
            self.project,
            self.api_key,
            self.token,
            self.op_secret,
            self.safety_age,
            self.dry_run,
        )
    }
}

#[expect(clippy::too_many_lines, reason = "amend flow is intentionally linear")]
fn release_amend(
    branch: BranchSelector,
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
    safety_age: Option<String>,
    dry_run: bool,
) -> eyre::Result<()> {
    let branch_query = branch.into_query()?;
    let project_id = resolve_project_id(project)?;

    let repo_root = get_repo_root()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let changelog_path = repo_root
        .join("platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml");
    let jar_dir = get_jar_dir()?;

    let mod_version = read_mod_version(&gradle_properties)?;
    let changelog_section = read_changelog_section(&changelog_path, &mod_version)?;
    let wrapped_changelog = format!("```\n{}\n```", changelog_section.trim());

    let upload_client = if dry_run {
        None
    } else {
        let token_value = CurseforgeApiSecret::resolve(token.clone(), op_secret.clone())?;
        Some(CurseforgeHttpClient::new(&token_value)?)
    };

    let all_jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
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

    let (core_key, credential_source) =
        CurseforgeApiSecret::resolve_core(api_key, token, op_secret)?;
    let client = CurseforgeHttpClient::new_core_api(&core_key)?;
    let files = fetch_project_files(&client, project_id, &credential_source)?;

    let safety_age_text = safety_age.unwrap_or_else(|| DEFAULT_AMEND_SAFETY_AGE.to_string());
    let max_file_age = parse_safety_age(&safety_age_text)?;
    let now = Utc::now();

    let mut file_targets: Vec<(String, CurseforgeProjectFileId, String, String, Duration)> =
        Vec::new();
    for (mc_version, jar_name) in target_versions {
        let file = find_latest_historical_file_for_mc(&files, &mc_version)?;
        let historical_version = historical_mod_version(file).ok_or_else(|| {
            eyre::eyre!(
                "Could not parse mod version from latest historical file {} for MC {}",
                file.id,
                mc_version
            )
        })?;
        if historical_version != mod_version {
            eyre::bail!(
                "Refusing to amend CurseForge file for MC {} because the latest remote version was {}, expected {} (file id {}).",
                mc_version,
                historical_version,
                mod_version,
                file.id
            );
        }
        let old_name = file
            .display_name
            .clone()
            .or_else(|| file.file_name.clone())
            .unwrap_or_else(|| "<unknown>".to_string());
        let file_age = parse_file_age(file.file_date.as_deref(), now)?;
        if file_age > max_file_age {
            eyre::bail!(
                "Refusing to amend file {} for MC {} because it is {} old (safety-age is {}).",
                file.id,
                mc_version,
                format_age(file_age),
                safety_age_text
            );
        }
        file_targets.push((mc_version, file.id, old_name, jar_name, file_age));
    }

    info!("{} {}", "Project ID:".cyan().bold(), project_id);
    info!("{} {}", "Mod version:".cyan().bold(), mod_version);
    info!(
        "{} {}",
        "Safety age:".cyan().bold(),
        safety_age_text.cyan().bold()
    );
    if dry_run {
        info!(
            "{} {}",
            "Mode:".yellow().bold(),
            "dry-run (no CurseForge mutations)".yellow().bold()
        );
    }
    info!("{}", "Amend targets:".cyan().bold());
    for (mc_version, file_id, old_name, jar_name, file_age) in &file_targets {
        info!(
            "  {} {} {} {} {} {} {} {} {} {}",
            "MC".dimmed(),
            mc_version.blue().bold(),
            "file id".dimmed(),
            file_id,
            "age".dimmed(),
            format_age(*file_age).yellow().bold(),
            "old".dimmed(),
            old_name,
            "new".dimmed(),
            jar_name
        );
    }

    if dry_run {
        info!(
            "{}",
            "Dry-run complete: remote CurseForge targets resolved; no files amended."
                .green()
                .bold()
        );
        return Ok(());
    }

    let prompt = format!(
        "{} {}",
        "Proceed to amend changelog on these files?".yellow().bold(),
        "(y/N)".yellow().bold()
    );
    if !prompt_yes_no(&prompt)? {
        info!("{}", "Aborted release amend.".yellow().bold());
        return Ok(());
    }

    for (mc_version, file_id, old_name, jar_name, file_age) in &file_targets {
        info!(
            "{} {} {} {} {} {} {} {} {} {}",
            "Amending file".white().bold(),
            file_id.to_string().magenta().bold(),
            "for MC".dimmed(),
            mc_version.blue().bold(),
            "age".dimmed(),
            format_age(*file_age).yellow().bold(),
            "old".dimmed(),
            old_name,
            "new".dimmed(),
            jar_name
        );
        let upload_client = upload_client.as_ref().ok_or_else(|| {
            eyre::eyre!("internal error: missing CurseForge upload client for amend")
        })?;
        amend_file_changelog(
            upload_client,
            project_id,
            *file_id,
            jar_name,
            &wrapped_changelog,
        )?;
    }

    info!(
        "{}",
        "CurseForge release changelog amend complete."
            .green()
            .bold()
    );

    Ok(())
}
