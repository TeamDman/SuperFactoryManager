//! https://docs.modrinth.com/api/operations/getprojectversions/
//! https://docs.modrinth.com/api/operations/createversion/
//! https://modrinth.com/mod/super-factory-manager/versions Project ID - aecUorJQ

use crate::cli::jar::get_jar_dir;
use crate::cli::repo_root::get_repo_root;
use crate::mc_version_filter::McVersionFilter;
use crate::worktree::parse_version;
use eyre::Context;
use facet::Facet;
use figue as args;
use reqwest::blocking::Client;
use reqwest::blocking::multipart;
use reqwest::header::AUTHORIZATION;
use reqwest::header::HeaderMap;
use reqwest::header::HeaderValue;
use reqwest::header::USER_AGENT;
use sha1::Digest;
use sha1::Sha1;
use std::collections::BTreeSet;
use std::ffi::OsStr;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use std::time::Duration;
use tracing::debug;

const MODRINTH_API_ROOT: &str = "https://api.modrinth.com/v2";
const MODRINTH_DEFAULT_PROJECT_ID: &str = "aecUorJQ";
const MODRINTH_TOKEN_ENV_VAR: &str = "MODRINTH_TOKEN";
const DEFAULT_OP_SECRET_REFERENCE: &str = "op://Private/Modrinth SFM API token/credential";
const MODRINTH_VERSIONS_URL_PREFIX: &str = "https://modrinth.com/mod";

const ANSI_RESET: &str = "\x1b[0m";
const ANSI_BOLD_CYAN: &str = "\x1b[1;36m";
const ANSI_BOLD_YELLOW: &str = "\x1b[1;33m";
const ANSI_BOLD_GREEN: &str = "\x1b[1;32m";
const ANSI_BOLD_BLUE: &str = "\x1b[1;34m";
const ANSI_BOLD_MAGENTA: &str = "\x1b[1;35m";
const ANSI_BOLD_WHITE: &str = "\x1b[1;37m";
const ANSI_DIM: &str = "\x1b[2m";

fn style(text: &str, ansi: &str) -> String {
    format!("{ansi}{text}{ANSI_RESET}")
}

fn prompt_yes_no(message: &str) -> eyre::Result<bool> {
    print!("{message} ");
    std::io::stdout()
        .flush()
        .wrap_err("Failed to flush prompt to stdout")?;

    let mut input = String::new();
    std::io::stdin()
        .read_line(&mut input)
        .wrap_err("Failed to read confirmation response")?;

    let normalized = input.trim().to_ascii_lowercase();
    Ok(matches!(normalized.as_str(), "y" | "yes"))
}

fn modrinth_versions_url(project_id: &str) -> String {
    format!("{MODRINTH_VERSIONS_URL_PREFIX}/{project_id}/versions")
}

/// Modrinth release-related commands.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum ModrinthCommand {
    /// Release metadata validation and upload operations
    Release {
        /// Release subcommand
        #[facet(args::subcommand)]
        command: ModrinthReleaseCommand,
    },
}

/// Modrinth release subcommands.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum ModrinthReleaseCommand {
    /// Verify computed release metadata against historical project versions
    Check {
        /// Minecraft version filter expression list, comma-separated (example: "=1.20.1" or ">=1.19.2,<=1.21.1")
        #[facet(default, args::named)]
        mc: Option<String>,
        /// Modrinth project id/slug (defaults to Super Factory Manager)
        #[facet(default, args::named)]
        project: Option<String>,
    },
    /// Validate remote downloadable jars against local release jars by hash
    Validate {
        /// Minecraft version filter expression list, comma-separated (example: "=1.20.1" or ">=1.19.2,<=1.21.1")
        #[facet(default, args::named)]
        mc: Option<String>,
        /// Modrinth project id/slug (defaults to Super Factory Manager)
        #[facet(default, args::named)]
        project: Option<String>,
    },
    /// Create new Modrinth versions for each release jar
    Now {
        /// Modrinth project id/slug (defaults to Super Factory Manager)
        #[facet(default, args::named)]
        project: Option<String>,
        /// Modrinth API token; if omitted, MODRINTH_TOKEN is used, then 1Password lookup
        #[facet(default, args::named)]
        token: Option<String>,
        /// 1Password secret reference used when token is omitted
        #[facet(default, args::named, rename = "op-secret")]
        op_secret: Option<String>,
        /// Print planned uploads and metadata without uploading
        #[facet(default, args::named)]
        dry_run: bool,
    },
    /// Amend changelog for latest version per MC in current release jars
    Amend {
        /// Modrinth project id/slug (defaults to Super Factory Manager)
        #[facet(default, args::named)]
        project: Option<String>,
        /// Modrinth API token; if omitted, MODRINTH_TOKEN is used, then 1Password lookup
        #[facet(default, args::named)]
        token: Option<String>,
        /// 1Password secret reference used when token is omitted
        #[facet(default, args::named, rename = "op-secret")]
        op_secret: Option<String>,
    },
}

impl ModrinthCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Release { command } => command.invoke(),
        }
    }
}

impl ModrinthReleaseCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Check { mc, project } => check_release_metadata(mc, project),
            Self::Validate { mc, project } => validate_release_hashes(mc, project),
            Self::Now {
                project,
                token,
                op_secret,
                dry_run,
            } => release_now(project, token, op_secret, dry_run),
            Self::Amend {
                project,
                token,
                op_secret,
            } => release_amend(project, token, op_secret),
        }
    }
}

#[derive(Facet, Debug, Clone)]
struct ModrinthProjectVersion {
    id: String,
    #[facet(default)]
    name: Option<String>,
    #[facet(default, rename = "version_number")]
    version_number: Option<String>,
    #[facet(default, rename = "game_versions")]
    game_versions: Vec<String>,
    #[facet(default)]
    loaders: Vec<String>,
    #[facet(default, rename = "date_published")]
    date_published: Option<String>,
    #[facet(default)]
    files: Vec<ModrinthProjectVersionFile>,
}

#[derive(Facet, Debug, Clone)]
struct ModrinthProjectVersionFile {
    filename: String,
    url: String,
    #[facet(default)]
    primary: Option<bool>,
    #[facet(default)]
    hashes: ModrinthProjectVersionFileHashes,
}

#[derive(Facet, Debug, Clone, Default)]
struct ModrinthProjectVersionFileHashes {
    #[facet(default)]
    sha1: Option<String>,
}

#[derive(Facet, Debug, Clone)]
struct ModrinthCreateVersionPayload {
    name: String,
    #[facet(rename = "version_number")]
    version_number: String,
    changelog: String,
    dependencies: Vec<ModrinthDependencyPayload>,
    #[facet(rename = "game_versions")]
    game_versions: Vec<String>,
    #[facet(rename = "version_type")]
    version_type: String,
    loaders: Vec<String>,
    featured: bool,
    #[facet(rename = "project_id")]
    project_id: String,
    #[facet(rename = "file_parts")]
    file_parts: Vec<String>,
}

#[derive(Facet, Debug, Clone)]
struct ModrinthDependencyPayload {
    #[facet(default, rename = "project_id")]
    project_id: Option<String>,
    #[facet(default, rename = "version_id")]
    version_id: Option<String>,
    #[facet(default, rename = "file_name")]
    file_name: Option<String>,
    #[facet(rename = "dependency_type")]
    dependency_type: String,
}

#[derive(Facet, Debug, Clone)]
struct ModrinthCreateVersionResponse {
    id: String,
}

#[derive(Debug, Clone)]
struct ModrinthReleasePlan {
    jar_path: PathBuf,
    mc_version: String,
    display_name: String,
    version_number: String,
    game_versions: Vec<String>,
    loaders: Vec<String>,
}

fn resolve_project_id(project: Option<String>) -> eyre::Result<String> {
    match project {
        Some(project_id) => {
            let trimmed = project_id.trim().to_string();
            if trimmed.is_empty() {
                eyre::bail!("Provided --project was empty");
            }
            Ok(trimmed)
        }
        None => Ok(MODRINTH_DEFAULT_PROJECT_ID.to_string()),
    }
}

fn resolve_token(token: Option<String>, op_secret: Option<String>) -> eyre::Result<String> {
    if let Some(value) = token {
        let trimmed = value.trim().to_string();
        if trimmed.is_empty() {
            eyre::bail!("Provided --token was empty");
        }
        return Ok(trimmed);
    }

    if let Ok(env_token) = std::env::var(MODRINTH_TOKEN_ENV_VAR) {
        let trimmed = env_token.trim().to_string();
        if !trimmed.is_empty() {
            return Ok(trimmed);
        }
    }

    let secret_reference = op_secret.unwrap_or_else(|| DEFAULT_OP_SECRET_REFERENCE.to_string());
    read_secret_from_1password(&secret_reference, "token")
}

fn read_secret_from_1password(secret_reference: &str, what: &str) -> eyre::Result<String> {
    let output = Command::new("op")
        .args(["read", secret_reference, "--no-newline"])
        .output()
        .wrap_err("Failed to run 1Password CLI (`op`)")?;

    if !output.status.success() {
        eyre::bail!(
            "Failed to read {what} from 1Password secret '{}': {}",
            secret_reference,
            String::from_utf8_lossy(&output.stderr)
        );
    }

    let value = String::from_utf8_lossy(&output.stdout).trim().to_string();
    if value.is_empty() {
        eyre::bail!(
            "1Password returned an empty {what} for secret '{}'",
            secret_reference
        );
    }

    Ok(value)
}

fn build_http_client(token: Option<&str>) -> eyre::Result<Client> {
    let mut headers = HeaderMap::new();
    headers.insert(
        USER_AGENT,
        HeaderValue::from_static("sfm-propagate-changes/modrinth"),
    );

    if let Some(token_value) = token {
        headers.insert(
            AUTHORIZATION,
            HeaderValue::from_str(token_value).wrap_err("Invalid Modrinth token for header")?,
        );
    }

    Client::builder()
        .default_headers(headers)
        .timeout(Duration::from_secs(120))
        .build()
        .wrap_err("Failed to build Modrinth HTTP client")
}

fn check_release_metadata(mc: Option<String>, project: Option<String>) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;

    let repo_root = get_repo_root()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let jar_dir = get_jar_dir()?;

    let mod_version = read_mod_version(&gradle_properties)?;
    let all_jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
    let jars = filter_release_jars_by_mc(all_jars, mc.as_deref())?;

    let plans = build_release_plans(&jars, &mod_version)?;
    let client = build_http_client(None)?;
    let existing_versions = fetch_project_versions(&client, &project_id)?;

    println!("{} {}", style("Project ID:", ANSI_BOLD_CYAN), project_id);
    println!("{} {}", style("Mod version:", ANSI_BOLD_CYAN), mod_version);
    println!("{} {}", style("Jars checked:", ANSI_BOLD_CYAN), plans.len());

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

        println!(
            "{} {} {} {} {}",
            style("✓", ANSI_BOLD_GREEN),
            style(&plan.mc_version, ANSI_BOLD_BLUE),
            style("matches historical version", ANSI_DIM),
            &historical.id,
            style(
                &format!(
                    "(loaders: {}; game versions: {})",
                    plan.loaders.join(", "),
                    plan.game_versions.join(", ")
                ),
                ANSI_DIM
            )
        );
    }

    println!(
        "{}",
        style(
            "Metadata check passed: computed metadata matches historical Modrinth versions.",
            ANSI_BOLD_GREEN
        )
    );

    Ok(())
}

fn validate_release_hashes(mc: Option<String>, project: Option<String>) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;

    let repo_root = get_repo_root()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let jar_dir = get_jar_dir()?;

    let mod_version = read_mod_version(&gradle_properties)?;
    let all_jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
    let jars = filter_release_jars_by_mc(all_jars, mc.as_deref())?;

    let client = build_http_client(None)?;
    let existing_versions = fetch_project_versions(&client, &project_id)?;

    println!("{} {}", style("Project ID:", ANSI_BOLD_CYAN), project_id);
    println!("{} {}", style("Mod version:", ANSI_BOLD_CYAN), mod_version);
    println!("{} {}", style("Jars checked:", ANSI_BOLD_CYAN), jars.len());

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

        println!(
            "{} {} {} {} {}",
            style("✓", ANSI_BOLD_GREEN),
            style(&mc_version, ANSI_BOLD_BLUE),
            style("hash validated", ANSI_DIM),
            historical.id,
            style(&format!("({})", remote_file.filename), ANSI_DIM)
        );
    }

    println!(
        "{}",
        style(
            "Hash validation passed: downloaded Modrinth files match local release jars.",
            ANSI_BOLD_GREEN
        )
    );

    Ok(())
}

fn release_now(
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
    let jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
    let plans = build_release_plans(&jars, &mod_version)?;

    println!("{} {}", style("Project ID:", ANSI_BOLD_CYAN), project_id);
    println!("{} {}", style("Mod version:", ANSI_BOLD_CYAN), mod_version);
    println!(
        "{} {}",
        style("Jar dir:", ANSI_BOLD_CYAN),
        jar_dir.display()
    );
    if dry_run {
        println!(
            "{} {}",
            style("Mode:", ANSI_BOLD_YELLOW),
            style("dry-run (no uploads)", ANSI_BOLD_YELLOW)
        );
    }
    println!("{}", style("Changelog:", ANSI_BOLD_CYAN));
    println!("{changelog_section}");

    println!("{}", style("Metadata preflight:", ANSI_BOLD_CYAN));
    for plan in &plans {
        println!(
            "  {} {} {} {} {} {}",
            style("MC", ANSI_DIM),
            style(&plan.mc_version, ANSI_BOLD_BLUE),
            style("=>", ANSI_DIM),
            style("loaders", ANSI_DIM),
            plan.loaders.join(", "),
            style(
                &format!("(game versions: {})", plan.game_versions.join(", ")),
                ANSI_DIM
            )
        );
    }

    let action = if dry_run {
        "run the dry-run release preview"
    } else {
        "create Modrinth versions"
    };

    let prompt = format!(
        "{} {} {}",
        style("Proceed to", ANSI_BOLD_YELLOW),
        style(action, ANSI_BOLD_YELLOW),
        style("? (y/N)", ANSI_BOLD_YELLOW)
    );
    if !prompt_yes_no(&prompt)? {
        println!("{}", style("Aborted release-now.", ANSI_BOLD_YELLOW));
        println!("{}", modrinth_versions_url(&project_id));
        return Ok(());
    }

    let client = if dry_run {
        None
    } else {
        let token_value = resolve_token(token, op_secret)?;
        Some(build_http_client(Some(&token_value))?)
    };

    for plan in plans {
        let jar_name = plan
            .jar_path
            .file_name()
            .and_then(OsStr::to_str)
            .map(ToString::to_string)
            .ok_or_else(|| eyre::eyre!("Invalid jar filename: {}", plan.jar_path.display()))?;

        println!(
            "{} {} {} {}",
            style("Uploading", ANSI_BOLD_WHITE),
            style(&jar_name, ANSI_BOLD_MAGENTA),
            style("for MC", ANSI_DIM),
            style(&plan.mc_version, ANSI_BOLD_BLUE)
        );

        if dry_run {
            println!(
                "  {} {}",
                style("metadata:", ANSI_DIM),
                style(
                    &format!(
                        "version_number={}, loaders=[{}], game_versions=[{}]",
                        plan.version_number,
                        plan.loaders.join(", "),
                        plan.game_versions.join(", ")
                    ),
                    ANSI_DIM
                )
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

        println!(
            "  {} {}",
            style("created version id", ANSI_BOLD_GREEN),
            style(&upload_id, ANSI_BOLD_GREEN)
        );
    }

    if !dry_run {
        println!(
            "{}",
            style("Modrinth release upload complete.", ANSI_BOLD_GREEN)
        );
    }
    println!("{}", modrinth_versions_url(&project_id));

    Ok(())
}

fn release_amend(
    project: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;

    let repo_root = get_repo_root()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let jar_dir = get_jar_dir()?;

    let mod_version = read_mod_version(&gradle_properties)?;
    let wrapped_changelog = compute_wrapped_release_changelog(&repo_root, &mod_version)?;

    let token_value = resolve_token(token, op_secret)?;
    let client = build_http_client(Some(&token_value))?;

    let jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
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
        amend_targets.push((mc_version, version.id.clone(), jar_name));
    }

    println!("{} {}", style("Project ID:", ANSI_BOLD_CYAN), project_id);
    println!("{} {}", style("Mod version:", ANSI_BOLD_CYAN), mod_version);
    println!("{}", style("Amend targets:", ANSI_BOLD_CYAN));
    for (mc_version, version_id, jar_name) in &amend_targets {
        println!(
            "  {} {} {} {} {} {}",
            style("MC", ANSI_DIM),
            style(mc_version, ANSI_BOLD_BLUE),
            style("version id", ANSI_DIM),
            version_id,
            style("name", ANSI_DIM),
            jar_name
        );
    }

    let prompt = format!(
        "{} {}",
        style(
            "Proceed to amend changelog on these versions?",
            ANSI_BOLD_YELLOW
        ),
        style("(y/N)", ANSI_BOLD_YELLOW)
    );
    if !prompt_yes_no(&prompt)? {
        println!("{}", style("Aborted release amend.", ANSI_BOLD_YELLOW));
        return Ok(());
    }

    for (mc_version, version_id, jar_name) in &amend_targets {
        println!(
            "{} {} {} {} {} {}",
            style("Amending version", ANSI_BOLD_WHITE),
            style(version_id, ANSI_BOLD_MAGENTA),
            style("for MC", ANSI_DIM),
            style(mc_version, ANSI_BOLD_BLUE),
            style("as", ANSI_DIM),
            jar_name
        );

        amend_version_changelog(&client, version_id, &wrapped_changelog)?;
    }

    println!(
        "{}",
        style(
            "Modrinth release changelog amend complete.",
            ANSI_BOLD_GREEN
        )
    );

    Ok(())
}

#[derive(Facet, Debug, Clone)]
struct ModrinthAmendVersionPayload {
    changelog: String,
}

fn amend_version_changelog(client: &Client, version_id: &str, changelog: &str) -> eyre::Result<()> {
    let payload = ModrinthAmendVersionPayload {
        changelog: changelog.to_string(),
    };
    let body = facet_json::to_string(&payload)
        .wrap_err("Failed to encode Modrinth amend changelog payload JSON")?;

    let url = format!("{MODRINTH_API_ROOT}/version/{version_id}");
    let response = client
        .patch(&url)
        .header("Content-Type", "application/json")
        .body(body)
        .send()
        .wrap_err_with(|| format!("Failed to amend Modrinth version {version_id}"))?;

    let status = response.status();
    let response_body = response
        .text()
        .wrap_err("Failed to read amend version response body")?;

    debug!(response_body, url, ?status);

    if !status.is_success() {
        eyre::bail!(
            "Modrinth amend failed for version {} ({status}): {}",
            version_id,
            response_body
        );
    }

    Ok(())
}

fn create_project_version(
    client: &Client,
    project_id: &str,
    plan: &ModrinthReleasePlan,
    changelog_section: &str,
) -> eyre::Result<String> {
    let payload = ModrinthCreateVersionPayload {
        name: plan.display_name.clone(),
        version_number: plan.version_number.clone(),
        changelog: changelog_section.to_string(),
        dependencies: Vec::new(),
        game_versions: plan.game_versions.clone(),
        version_type: "release".to_string(),
        loaders: plan.loaders.clone(),
        featured: false,
        project_id: project_id.to_string(),
        file_parts: vec!["file".to_string()],
    };

    let payload_json = facet_json::to_string(&payload)
        .wrap_err("Failed to encode Modrinth upload metadata JSON")?;

    let form = multipart::Form::new()
        .text("data", payload_json)
        .file("file", &plan.jar_path)
        .wrap_err_with(|| {
            format!(
                "Failed to attach jar file to Modrinth upload form: {}",
                plan.jar_path.display()
            )
        })?;

    let url = format!("{MODRINTH_API_ROOT}/version");
    let response = client.post(&url).multipart(form).send().wrap_err_with(|| {
        format!(
            "Failed to upload jar to Modrinth: {}",
            plan.jar_path.display()
        )
    })?;

    let status = response.status();
    let body = response
        .text()
        .wrap_err("Failed to read upload response from Modrinth")?;

    debug!(body, url, ?status);

    if !status.is_success() {
        eyre::bail!(
            "Modrinth upload failed for {} ({status}): {body}",
            plan.jar_path.display()
        );
    }

    let parsed: ModrinthCreateVersionResponse =
        facet_json::from_str(&body).wrap_err("Failed to parse Modrinth upload response JSON")?;
    Ok(parsed.id)
}

fn fetch_project_versions(
    client: &Client,
    project_id: &str,
) -> eyre::Result<Vec<ModrinthProjectVersion>> {
    let url = format!("{MODRINTH_API_ROOT}/project/{project_id}/version");
    let response = client
        .get(&url)
        .send()
        .wrap_err_with(|| format!("Failed to query Modrinth project versions: {url}"))?;

    let status = response.status();
    let body = response
        .text()
        .wrap_err("Failed to read Modrinth project versions response")?;

    debug!(body, url, ?status);

    if !status.is_success() {
        eyre::bail!(
            "Modrinth project versions API failed for project {} ({status}): {body}",
            project_id
        );
    }

    facet_json::from_str(&body).wrap_err("Failed to parse Modrinth project versions response JSON")
}

fn find_latest_historical_version_for_mc<'a>(
    versions: &'a [ModrinthProjectVersion],
    mc_version: &str,
) -> eyre::Result<&'a ModrinthProjectVersion> {
    versions
        .iter()
        .filter(|version| {
            version
                .game_versions
                .iter()
                .any(|value| value == mc_version)
        })
        .max_by(|left, right| {
            let left_key = (
                left.date_published.as_deref().unwrap_or_default(),
                left.id.as_str(),
            );
            let right_key = (
                right.date_published.as_deref().unwrap_or_default(),
                right.id.as_str(),
            );
            left_key.cmp(&right_key)
        })
        .ok_or_else(|| eyre::eyre!("No historical Modrinth version found for MC {mc_version}"))
}

fn select_download_file<'a>(
    version: &'a ModrinthProjectVersion,
    mc_version: &str,
    mod_version: &str,
) -> eyre::Result<&'a ModrinthProjectVersionFile> {
    let expected_marker = format!("-MC{mc_version}-");
    let expected_suffix = format!("-{mod_version}.jar");

    if let Some(file) = version.files.iter().find(|file| {
        file.filename.contains(&expected_marker) && file.filename.ends_with(&expected_suffix)
    }) {
        return Ok(file);
    }

    if let Some(file) = version
        .files
        .iter()
        .find(|file| file.primary.unwrap_or(false))
    {
        return Ok(file);
    }

    version.files.first().ok_or_else(|| {
        eyre::eyre!(
            "Modrinth version {} does not contain any downloadable files",
            version.id
        )
    })
}

fn download_sha1(client: &Client, url: &str) -> eyre::Result<String> {
    let response = client
        .get(url)
        .send()
        .wrap_err_with(|| format!("Failed to download file from {url}"))?;

    let status = response.status();
    if !status.is_success() {
        let body = response
            .text()
            .unwrap_or_else(|_| "<failed to read response body>".to_string());
        eyre::bail!("Failed to download file from {} ({status}): {}", url, body);
    }

    let bytes = response
        .bytes()
        .wrap_err_with(|| format!("Failed to read downloaded bytes from {url}"))?;
    Ok(sha1_hex(bytes.as_ref()))
}

fn sha1_hex(bytes: &[u8]) -> String {
    let mut hasher = Sha1::new();
    hasher.update(bytes);
    let digest = hasher.finalize();
    digest.iter().map(|byte| format!("{byte:02x}")).collect()
}

fn to_normalized_set(values: &[String]) -> BTreeSet<String> {
    values
        .iter()
        .map(|value| value.trim().to_ascii_lowercase())
        .filter(|value| !value.is_empty())
        .collect()
}

fn build_release_plans(
    jars: &[PathBuf],
    mod_version: &str,
) -> eyre::Result<Vec<ModrinthReleasePlan>> {
    let mut plans = Vec::with_capacity(jars.len());

    for jar in jars {
        let mc_version = parse_mc_version_from_jar_name(jar)?;
        let parsed = parse_version(&mc_version)
            .ok_or_else(|| eyre::eyre!("Could not parse MC version '{}'", mc_version))?;

        let display_name = format!("Super Factory Manager MC{mc_version} v{mod_version}");
        let game_versions = vec![mc_version.clone()];
        let loaders = loader_slugs_for(parsed)
            .into_iter()
            .map(str::to_string)
            .collect();

        plans.push(ModrinthReleasePlan {
            jar_path: jar.clone(),
            mc_version,
            display_name,
            version_number: mod_version.to_string(),
            game_versions,
            loaders,
        });
    }

    Ok(plans)
}

fn filter_release_jars_by_mc(
    jars: Vec<PathBuf>,
    mc_filter_text: Option<&str>,
) -> eyre::Result<Vec<PathBuf>> {
    let Some(filter_text) = mc_filter_text else {
        return Ok(jars);
    };

    let filter = McVersionFilter::parse(filter_text)?;
    let mut filtered = Vec::new();
    for jar in jars {
        let mc_version = parse_mc_version_from_jar_name(&jar)?;
        let parsed = parse_version(&mc_version).ok_or_else(|| {
            eyre::eyre!(
                "Could not parse MC version '{}' from {}",
                mc_version,
                jar.display()
            )
        })?;
        if filter.matches_parsed(parsed) {
            filtered.push(jar);
        }
    }

    if filtered.is_empty() {
        eyre::bail!("No release jars matched --mc '{filter_text}'.");
    }

    Ok(filtered)
}

fn loader_slugs_for(parsed: (u32, u32, u32)) -> Vec<&'static str> {
    let v_1_20_0 = (1, 20, 0);
    let v_1_20_1 = (1, 20, 1);

    if parsed <= v_1_20_0 {
        return vec!["forge"];
    }

    if parsed == v_1_20_1 {
        return vec!["forge", "neoforge"];
    }

    vec!["neoforge"]
}

fn read_mod_version(gradle_properties: &Path) -> eyre::Result<String> {
    let content = std::fs::read_to_string(gradle_properties)
        .wrap_err_with(|| format!("Failed to read {}", gradle_properties.display()))?;

    let mod_version = content
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .filter(|line| !line.starts_with('#'))
        .find_map(|line| line.strip_prefix("mod_version=").map(str::trim))
        .ok_or_else(|| eyre::eyre!("mod_version not found in {}", gradle_properties.display()))?;

    if mod_version.is_empty() {
        eyre::bail!("mod_version was empty in {}", gradle_properties.display());
    }

    Ok(mod_version.to_string())
}

fn read_changelog_section(changelog_path: &Path, mod_version: &str) -> eyre::Result<String> {
    let lines: Vec<String> = std::fs::read_to_string(changelog_path)
        .wrap_err_with(|| format!("Failed to read changelog: {}", changelog_path.display()))?
        .lines()
        .map(ToString::to_string)
        .collect();

    if lines.is_empty() {
        eyre::bail!("Changelog file was empty: {}", changelog_path.display());
    }

    let heading_prefix = format!("---- {mod_version} ");
    let start_index = lines
        .iter()
        .position(|line| line.trim().starts_with(&heading_prefix))
        .ok_or_else(|| {
            eyre::eyre!(
                "Could not find changelog heading for version {mod_version} in {}",
                changelog_path.display()
            )
        })?;

    let mut end_index = lines.len();
    for (index, line) in lines.iter().enumerate().skip(start_index + 1) {
        let trimmed = line.trim();
        if trimmed.starts_with("---- ") {
            let maybe_version = trimmed
                .trim_start_matches("---- ")
                .split(' ')
                .next()
                .unwrap_or_default();
            if parse_version(maybe_version).is_some() {
                end_index = index;
                break;
            }
        }
    }

    Ok(lines[0..end_index].join("\n").trim().to_string())
}

fn get_ordered_release_jars(jar_dir: &Path, mod_version: &str) -> eyre::Result<Vec<PathBuf>> {
    if !jar_dir.is_dir() {
        eyre::bail!("Jar directory does not exist: {}", jar_dir.display());
    }

    let suffix = format!("-{mod_version}.jar");
    let mut entries: Vec<(PathBuf, (u32, u32, u32), String)> = std::fs::read_dir(jar_dir)?
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| {
            path.is_file()
                && path
                    .extension()
                    .is_some_and(|extension| extension == OsStr::new("jar"))
                && path
                    .file_name()
                    .and_then(OsStr::to_str)
                    .is_some_and(|name| name.ends_with(&suffix))
        })
        .map(|path| {
            let name = path
                .file_name()
                .and_then(OsStr::to_str)
                .map(ToString::to_string)
                .unwrap_or_default();
            let version = parse_mc_version_from_jar_name(&path)
                .ok()
                .and_then(|value| parse_version(&value))
                .unwrap_or((0, 0, 0));
            (path, version, name)
        })
        .collect();

    if entries.is_empty() {
        eyre::bail!(
            "No jar files found in {} for mod version {}",
            jar_dir.display(),
            mod_version
        );
    }

    entries.sort_by(|a, b| {
        let left = (a.1, &a.2);
        let right = (b.1, &b.2);
        left.cmp(&right)
    });

    Ok(entries.into_iter().map(|entry| entry.0).collect())
}

fn parse_mc_version_from_jar_name(jar_path: &Path) -> eyre::Result<String> {
    let file_name = jar_path
        .file_name()
        .and_then(OsStr::to_str)
        .ok_or_else(|| eyre::eyre!("Invalid jar filename: {}", jar_path.display()))?;

    let marker = "-MC";
    let start = file_name
        .find(marker)
        .ok_or_else(|| eyre::eyre!("Could not find '-MC' marker in jar name: {file_name}"))?
        + marker.len();
    let remainder = &file_name[start..];
    let end = remainder.find('-').ok_or_else(|| {
        eyre::eyre!("Could not find end of MC version marker in jar name: {file_name}")
    })?;

    let version = &remainder[..end];
    if parse_version(version).is_none() {
        eyre::bail!("Unsupported MC version marker in jar name: {file_name}");
    }

    Ok(version.to_string())
}

fn compute_wrapped_release_changelog(repo_root: &Path, mod_version: &str) -> eyre::Result<String> {
    let changelog_path = repo_root
        .join("platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml");
    let changelog_section = read_changelog_section(&changelog_path, mod_version)?;
    Ok(format!("```\n{}\n```", changelog_section.trim()))
}
