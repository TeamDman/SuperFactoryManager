//! https://docs.curseforge.com/rest-api/#get-mod
//! https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-api
//! https://www.curseforge.com/minecraft/mc-mods/super-factory-manager Project ID - 306935

use crate::cli::jar::get_jar_dir;
use crate::cli::repo_root::get_repo_root;
use crate::mc_version_filter::McVersionFilter;
use crate::paths::APP_HOME;
use crate::worktree::parse_version;
use chrono::DateTime;
use chrono::Utc;
use eyre::Context;
use facet::Facet;
use figue as args;
use reqwest::blocking::Client;
use reqwest::blocking::multipart;
use reqwest::header::HeaderMap;
use reqwest::header::HeaderValue;
use reqwest::header::USER_AGENT;
use sha1::Digest;
use sha1::Sha1;
use std::collections::BTreeSet;
use std::collections::HashMap;
use std::ffi::OsStr;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use std::time::Duration;
use tracing::debug;

const CURSEFORGE_API_ROOT: &str = "https://minecraft.curseforge.com/api";
const CURSEFORGE_CORE_API_ROOT: &str = "https://api.curseforge.com/v1";
const CURSEFORGE_DEFAULT_PROJECT_FILE: &str = "curseforge_project_id.txt";
const CURSEFORGE_DEFAULT_PROJECT_ID: u64 = 306_935;
const CURSEFORGE_TOKEN_ENV_VAR: &str = "CURSEFORGE_API_TOKEN";
const CURSEFORGE_CORE_API_KEY_ENV_VAR: &str = "CURSEFORGE_CORE_API_KEY";
const DEFAULT_OP_SECRET_REFERENCE: &str = "op://Private/CurseForge SFM Upload token/credential";
const DEFAULT_OP_CORE_API_KEY_SECRET_REFERENCE: &str =
    "op://Private/SFM CurseForge studios token/credential";
const DEFAULT_AMEND_SAFETY_AGE: &str = "30m";
const CURSEFORGE_AUTHORS_FILES_URL_PREFIX: &str = "https://authors.curseforge.com/#/projects";

const ANSI_RESET: &str = "\x1b[0m";
const ANSI_BOLD_CYAN: &str = "\x1b[1;36m";
const ANSI_BOLD_YELLOW: &str = "\x1b[1;33m";
const ANSI_BOLD_GREEN: &str = "\x1b[1;32m";
const ANSI_BOLD_BLUE: &str = "\x1b[1;34m";
const ANSI_BOLD_MAGENTA: &str = "\x1b[1;35m";
const ANSI_BOLD_RED: &str = "\x1b[1;31m";
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

fn colorize_metadata_name(name: &str, mc_version: &str) -> String {
    if name == mc_version {
        return style(name, ANSI_BOLD_BLUE);
    }

    match name {
        "NeoForge" => style(name, ANSI_BOLD_RED),
        "Forge" => style(name, ANSI_BOLD_YELLOW),
        "Java 17" => style(name, ANSI_BOLD_GREEN),
        "Java 21" => style(name, ANSI_BOLD_CYAN),
        _ => name.to_string(),
    }
}

fn curseforge_files_url(project_id: u64) -> String {
    format!("{CURSEFORGE_AUTHORS_FILES_URL_PREFIX}/{project_id}/files")
}

/// CurseForge release and file related commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeCommand {
    /// Project-related operations
    Project {
        /// Project subcommand
        #[facet(args::subcommand)]
        command: CurseforgeProjectCommand,
    },
    /// Minecraft metadata operations
    Minecraft {
        /// Minecraft subcommand
        #[facet(args::subcommand)]
        command: CurseforgeMinecraftCommand,
    },
    /// Release metadata validation and upload operations
    Release {
        /// Release subcommand
        #[facet(args::subcommand)]
        command: CurseforgeReleaseCommand,
    },
}

/// CurseForge release subcommands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeReleaseCommand {
    /// Verify computed release metadata against historical project uploads
    Check {
        /// Minecraft version filter expression list, comma-separated (example: "=1.20.1" or ">=1.19.2,<=1.21.1")
        #[facet(default, args::named)]
        mc: Option<String>,
        /// CurseForge project ID (defaults to configured default project)
        #[facet(default, args::named)]
        project: Option<u64>,
        /// CurseForge Core API key; if omitted, CURSEFORGE_CORE_API_KEY is used
        #[facet(default, args::named, rename = "api-key")]
        api_key: Option<String>,
        /// CurseForge API token; if omitted, CURSEFORGE_API_TOKEN is used, then 1Password lookup
        #[facet(default, args::named)]
        token: Option<String>,
        /// 1Password secret reference used when credentials are omitted
        #[facet(default, args::named, rename = "op-secret")]
        op_secret: Option<String>,
    },
    /// Validate remote downloadable files against local release jars by hash
    Validate {
        /// Minecraft version filter expression list, comma-separated (example: "=1.20.1" or ">=1.19.2,<=1.21.1")
        #[facet(default, args::named)]
        mc: Option<String>,
        /// CurseForge project ID (defaults to configured default project)
        #[facet(default, args::named)]
        project: Option<u64>,
        /// CurseForge Core API key; if omitted, CURSEFORGE_CORE_API_KEY is used
        #[facet(default, args::named, rename = "api-key")]
        api_key: Option<String>,
        /// CurseForge API token; if omitted, CURSEFORGE_API_TOKEN is used, then 1Password lookup
        #[facet(default, args::named)]
        token: Option<String>,
        /// 1Password secret reference used when credentials are omitted
        #[facet(default, args::named, rename = "op-secret")]
        op_secret: Option<String>,
    },
    /// Upload each release jar to CurseForge according to release-process rules
    Now {
        /// CurseForge project ID (defaults to configured default project)
        #[facet(default, args::named)]
        project: Option<u64>,
        /// CurseForge API token; if omitted, CURSEFORGE_API_TOKEN is used, then 1Password lookup
        #[facet(default, args::named)]
        token: Option<String>,
        /// 1Password secret reference used for Core API key lookup
        #[facet(default, args::named, rename = "op-secret")]
        op_secret: Option<String>,
        /// Print planned uploads and metadata without uploading
        #[facet(default, args::named)]
        dry_run: bool,
    },
    /// Amend changelog for latest file per MC version in current release jars
    Amend {
        /// CurseForge project ID (defaults to configured default project)
        #[facet(default, args::named)]
        project: Option<u64>,
        /// CurseForge Core API key; if omitted, CURSEFORGE_CORE_API_KEY is used
        #[facet(default, args::named, rename = "api-key")]
        api_key: Option<String>,
        /// CurseForge API token; if omitted, CURSEFORGE_API_TOKEN is used, then 1Password lookup
        #[facet(default, args::named)]
        token: Option<String>,
        /// 1Password secret reference used when credentials are omitted
        #[facet(default, args::named, rename = "op-secret")]
        op_secret: Option<String>,
        /// Refuse amending files older than this age (examples: 30m, 2h, 45s)
        #[facet(default, args::named, rename = "safety-age")]
        safety_age: Option<String>,
    },
}

/// CurseForge project subcommands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeProjectCommand {
    /// Project default configuration commands
    Default {
        /// Default subcommand
        #[facet(args::subcommand)]
        command: CurseforgeProjectDefaultCommand,
    },
    /// Project file operations
    File {
        /// File subcommand
        #[facet(args::subcommand)]
        command: CurseforgeProjectFileCommand,
    },
}

/// CurseForge project default subcommands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeProjectDefaultCommand {
    /// Set default project ID
    Set {
        /// Project ID to persist as default
        #[facet(args::positional)]
        project: u64,
    },
    /// Show default project ID (falls back to built-in default)
    Show,
}

/// CurseForge project file subcommands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeProjectFileCommand {
    /// List files for a project
    List {
        /// CurseForge project ID (defaults to configured default project)
        #[facet(default, args::named)]
        project: Option<u64>,
        /// CurseForge Core API key; if omitted, CURSEFORGE_CORE_API_KEY is used
        #[facet(default, args::named, rename = "api-key")]
        api_key: Option<String>,
        /// CurseForge API token; if omitted, CURSEFORGE_API_TOKEN is used, then 1Password lookup
        #[facet(default, args::named)]
        token: Option<String>,
        /// 1Password secret reference used when token is omitted
        #[facet(default, args::named, rename = "op-secret")]
        op_secret: Option<String>,
    },
}

/// CurseForge minecraft subcommands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeMinecraftCommand {
    /// Minecraft version operations
    Version {
        /// Version subcommand
        #[facet(args::subcommand)]
        command: CurseforgeMinecraftVersionCommand,
    },
}

/// CurseForge minecraft version subcommands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeMinecraftVersionCommand {
    /// List Minecraft game versions from CurseForge
    List {
        /// Minecraft version filter expression list, comma-separated (example: ">=1.19.2,<=1.21.1")
        #[facet(default, args::named)]
        mc: Option<String>,
        /// CurseForge API token (optional for this endpoint)
        #[facet(default, args::named)]
        token: Option<String>,
        /// 1Password secret reference used when token is omitted and env var is missing
        #[facet(default, args::named, rename = "op-secret")]
        op_secret: Option<String>,
    },
}

impl CurseforgeCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Project { command } => super::curseforge_project_command::invoke(command),
            Self::Minecraft { command } => super::curseforge_minecraft_command::invoke(command),
            Self::Release { command } => super::curseforge_release_command::invoke(command),
        }
    }
}

impl CurseforgeReleaseCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Check {
                mc,
                project,
                api_key,
                token,
                op_secret,
            } => super::curseforge_release_check_command::invoke(
                mc, project, api_key, token, op_secret,
            ),
            Self::Validate {
                mc,
                project,
                api_key,
                token,
                op_secret,
            } => super::curseforge_release_validate_command::invoke(
                mc, project, api_key, token, op_secret,
            ),
            Self::Now {
                project,
                token,
                op_secret,
                dry_run,
            } => super::curseforge_release_now_command::invoke(project, token, op_secret, dry_run),
            Self::Amend {
                project,
                api_key,
                token,
                op_secret,
                safety_age,
            } => super::curseforge_release_amend_command::invoke(
                project, api_key, token, op_secret, safety_age,
            ),
        }
    }
}

impl CurseforgeProjectCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Default { command } => super::curseforge_project_default_command::invoke(command),
            Self::File { command } => super::curseforge_project_file_command::invoke(command),
        }
    }
}

impl CurseforgeProjectDefaultCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Set { project } => super::curseforge_project_default_command::invoke_set(project),
            Self::Show => super::curseforge_project_default_command::invoke_show(),
        }
    }
}

impl CurseforgeProjectFileCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::List {
                project,
                api_key,
                token,
                op_secret,
            } => super::curseforge_project_file_command::invoke_list(project, api_key, token, op_secret),
        }
    }
}

#[derive(Facet, Debug, Clone)]
struct CurseforgeProjectFileItem {
    id: u64,
    #[facet(default, rename = "fileName")]
    file_name: Option<String>,
    #[facet(default, rename = "displayName")]
    display_name: Option<String>,
    #[facet(default, rename = "releaseType")]
    release_type: Option<u32>,
    #[facet(default, rename = "fileStatus")]
    file_status: Option<u32>,
    #[facet(default, rename = "gameVersions")]
    game_versions: Vec<String>,
    #[facet(default, rename = "downloadUrl")]
    download_url: Option<String>,
    #[facet(default, rename = "fileDate")]
    file_date: Option<String>,
    #[facet(default)]
    hashes: Vec<CurseforgeProjectFileHash>,
}

#[derive(Facet, Debug, Clone)]
struct CurseforgeProjectFileHash {
    #[facet(default)]
    algo: Option<u32>,
    #[facet(default)]
    value: Option<String>,
}

#[derive(Facet, Debug, Clone)]
struct CurseforgeProjectFileListEnvelope {
    #[facet(default)]
    data: Vec<CurseforgeProjectFileItem>,
}

#[derive(Facet, Debug, Clone)]
struct CurseforgeUploadResponse {
    id: u64,
}

#[derive(Facet, Debug, Clone)]
struct CurseforgeGameVersion {
    id: u64,
    #[facet(default, rename = "gameVersionTypeID")]
    game_version_type_id: Option<u64>,
    name: String,
    #[facet(default)]
    slug: Option<String>,
}

#[derive(Facet, Debug, Clone)]
struct UploadMetadata {
    changelog: String,
    #[facet(rename = "changelogType")]
    changelog_type: String,
    #[facet(rename = "displayName")]
    display_name: String,
    #[facet(rename = "gameVersions")]
    game_versions: Vec<u64>,
    #[facet(rename = "releaseType")]
    release_type: String,
}

#[derive(Facet, Debug, Clone)]
struct CurseforgeAmendFilePayload {
    #[facet(rename = "fileID")]
    file_id: u64,
    changelog: String,
    #[facet(rename = "changelogType")]
    changelog_type: String,
    #[facet(rename = "displayName")]
    display_name: String,
}

fn set_default_project_id(project: u64) -> eyre::Result<()> {
    APP_HOME.ensure_dir()?;
    let path = APP_HOME.file_path(CURSEFORGE_DEFAULT_PROJECT_FILE);
    std::fs::write(&path, project.to_string())
        .wrap_err_with(|| format!("Failed to write default project file: {}", path.display()))?;

    println!("Default CurseForge project set to {project}.");
    Ok(())
}

fn get_default_project_id() -> eyre::Result<u64> {
    let path = APP_HOME.file_path(CURSEFORGE_DEFAULT_PROJECT_FILE);
    if !path.exists() {
        return Ok(CURSEFORGE_DEFAULT_PROJECT_ID);
    }

    let content = std::fs::read_to_string(&path)
        .wrap_err_with(|| format!("Failed to read default project file: {}", path.display()))?;

    let trimmed = content.trim();
    if trimmed.is_empty() {
        return Ok(CURSEFORGE_DEFAULT_PROJECT_ID);
    }

    trimmed
        .parse::<u64>()
        .wrap_err_with(|| format!("Invalid project id in default project file: '{trimmed}'"))
}

fn resolve_project_id(project: Option<u64>) -> eyre::Result<u64> {
    match project {
        Some(project_id) => Ok(project_id),
        None => get_default_project_id(),
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

    if let Ok(env_token) = std::env::var(CURSEFORGE_TOKEN_ENV_VAR) {
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

fn build_http_client(token: &str) -> eyre::Result<Client> {
    let mut headers = HeaderMap::new();
    headers.insert(
        "X-Api-Token",
        HeaderValue::from_str(token).wrap_err("Invalid API token for header")?,
    );
    headers.insert(
        USER_AGENT,
        HeaderValue::from_static("sfm-propagate-changes/curseforge"),
    );

    Client::builder()
        .default_headers(headers)
        .timeout(Duration::from_secs(120))
        .build()
        .wrap_err("Failed to build HTTP client")
}

fn resolve_core_api_key(
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<(String, String)> {
    if let Some(value) = api_key {
        let trimmed = value.trim().to_string();
        if trimmed.is_empty() {
            eyre::bail!("Provided --api-key was empty");
        }
        return Ok((trimmed, "--api-key".to_string()));
    }

    if let Ok(env_key) = std::env::var(CURSEFORGE_CORE_API_KEY_ENV_VAR) {
        let trimmed = env_key.trim().to_string();
        if !trimmed.is_empty() {
            return Ok((trimmed, CURSEFORGE_CORE_API_KEY_ENV_VAR.to_string()));
        }
    }

    if let Some(secret_reference) = op_secret {
        return read_secret_from_1password(&secret_reference, "Core API key")
            .map(|value| (value, format!("1Password ({secret_reference})")));
    }

    if let Ok(value) =
        read_secret_from_1password(DEFAULT_OP_CORE_API_KEY_SECRET_REFERENCE, "Core API key")
    {
        return Ok((
            value,
            format!("1Password ({DEFAULT_OP_CORE_API_KEY_SECRET_REFERENCE})"),
        ));
    }

    if let Ok(value) = read_secret_from_1password(DEFAULT_OP_SECRET_REFERENCE, "Core API key") {
        return Ok((value, format!("1Password ({DEFAULT_OP_SECRET_REFERENCE})")));
    }

    if let Some(value) = token {
        let trimmed = value.trim().to_string();
        if trimmed.is_empty() {
            eyre::bail!("Provided --token was empty");
        }
        return Ok((trimmed, "--token".to_string()));
    }

    if let Ok(env_token) = std::env::var(CURSEFORGE_TOKEN_ENV_VAR) {
        let trimmed = env_token.trim().to_string();
        if !trimmed.is_empty() {
            return Ok((trimmed, CURSEFORGE_TOKEN_ENV_VAR.to_string()));
        }
    }

    eyre::bail!(
        "Could not resolve CurseForge Core API key. Tried --api-key, {CURSEFORGE_CORE_API_KEY_ENV_VAR}, and 1Password defaults:\n\
         - {DEFAULT_OP_CORE_API_KEY_SECRET_REFERENCE}\n\
         - {DEFAULT_OP_SECRET_REFERENCE}"
    )
}

fn build_core_http_client(api_key: &str) -> eyre::Result<Client> {
    let mut headers = HeaderMap::new();
    headers.insert(
        "x-api-key",
        HeaderValue::from_str(api_key).wrap_err("Invalid Core API key for header")?,
    );
    headers.insert(
        USER_AGENT,
        HeaderValue::from_static("sfm-propagate-changes/curseforge"),
    );

    Client::builder()
        .default_headers(headers)
        .timeout(Duration::from_secs(120))
        .build()
        .wrap_err("Failed to build Core API HTTP client")
}

fn list_minecraft_versions(
    mc_filter_text: &str,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    let token_value = resolve_token(token, op_secret)?;
    let client = build_http_client(&token_value)?;

    let filter = McVersionFilter::parse(mc_filter_text)?;
    let mut rows: Vec<(u64, String, String, (u32, u32, u32))> = fetch_game_versions(&client)?
        .into_iter()
        .filter_map(|version| {
            let parsed = parse_version(&version.name)?;
            if filter.matches_parsed(parsed) {
                Some((
                    version.id,
                    version.name,
                    version.slug.unwrap_or_default(),
                    parsed,
                ))
            } else {
                None
            }
        })
        .collect();

    rows.sort_by(|left, right| left.3.cmp(&right.3).then(left.1.cmp(&right.1)));

    if rows.is_empty() {
        println!("No Minecraft game versions matched --mc '{mc_filter_text}'.");
        return Ok(());
    }

    println!("CurseForge Minecraft versions matching --mc '{mc_filter_text}':");
    println!("id\tname\tslug");
    for (id, name, slug, _) in rows {
        println!("{id}\t{name}\t{slug}");
    }

    Ok(())
}

fn list_project_files(
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;
    let (key, credential_source) = resolve_core_api_key(api_key, token, op_secret)?;
    let client = build_core_http_client(&key)?;
    let files = fetch_project_files(&client, project_id, &credential_source)?;

    if files.is_empty() {
        println!("No files found for project {project_id}.");
        return Ok(());
    }

    println!("Project {project_id} files:");
    println!("id\tfile_name\tdisplay_name\trelease_type\tfile_status\tgame_versions");
    for file in files {
        let file_name = file
            .file_name
            .clone()
            .unwrap_or_else(|| "<unknown>".to_string());
        let display = file
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
        println!(
            "{}\t{}\t{}\t{}\t{}\t{}",
            file.id, file_name, display, release_type, file_status, game_versions
        );
    }

    Ok(())
}

fn fetch_project_files(
    client: &Client,
    project_id: u64,
    credential_source: &str,
) -> eyre::Result<Vec<CurseforgeProjectFileItem>> {
    let url = format!("{CURSEFORGE_CORE_API_ROOT}/mods/{project_id}/files");
    let response = client
        .get(&url)
        .send()
        .wrap_err_with(|| format!("Failed to query project files: {url}"))?;

    let status = response.status();
    let body = response
        .text()
        .wrap_err("Failed to read project files response")?;
    debug!(body, url, ?status);

    if !status.is_success() {
        if status == reqwest::StatusCode::FORBIDDEN {
            eyre::bail!(
                "CurseForge project files API failed (403 Forbidden) using credential from: {credential_source}.\n\
                 Ensure this is a CurseForge Core API key (x-api-key), not the upload token.\n\
                 You can override with --api-key or --op-secret '<core-api-key-secret-ref>'."
            );
        }

        eyre::bail!("CurseForge project files API failed ({status}): {body}",);
    }

    if let Ok(items) = facet_json::from_str::<Vec<CurseforgeProjectFileItem>>(&body) {
        return Ok(items);
    }

    let envelope: CurseforgeProjectFileListEnvelope =
        facet_json::from_str(&body).wrap_err("Failed to parse project files response JSON")?;
    Ok(envelope.data)
}

fn release_now(
    project: Option<u64>,
    token: Option<String>,
    op_secret: Option<String>,
    dry_run: bool,
) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;

    let repo_root = get_repo_root()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let changelog_path = repo_root
        .join("platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml");
    let jar_dir = get_jar_dir()?;

    let mod_version = read_mod_version(&gradle_properties)?;
    let changelog_section = read_changelog_section(&changelog_path, &mod_version)?;
    let jars = get_ordered_release_jars(&jar_dir, &mod_version)?;

    let game_version_index = if dry_run {
        None
    } else {
        let token_value = resolve_token(token, op_secret)?;
        let client = build_http_client(&token_value)?;
        let game_versions = fetch_game_versions(&client)?;
        Some((client, build_game_version_index(&game_versions)))
    };

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

    let upload_plans = if dry_run {
        None
    } else {
        let (_, index) = game_version_index
            .as_ref()
            .ok_or_else(|| eyre::eyre!("internal error: missing game version index"))?;
        Some(build_upload_plans(
            &jars,
            &mod_version,
            &changelog_section,
            index,
        )?)
    };

    if let Some(plans) = &upload_plans {
        println!("{}", style("Metadata preflight:", ANSI_BOLD_CYAN));
        for plan in plans {
            let metadata_pairs: Vec<String> = plan
                .metadata_names
                .iter()
                .zip(plan.metadata.game_versions.iter())
                .map(|(name, id)| format!("{name}:{id}"))
                .collect();
            println!(
                "  {} {} {} {}",
                style("MC", ANSI_DIM),
                style(&plan.mc_version, ANSI_BOLD_BLUE),
                style("=>", ANSI_DIM),
                metadata_pairs.join(", ")
            );
        }
    }

    let action = if dry_run {
        "run the dry-run release preview"
    } else {
        "upload files to CurseForge"
    };
    let prompt = format!(
        "{} {} {}",
        style("Proceed to", ANSI_BOLD_YELLOW),
        style(action, ANSI_BOLD_YELLOW),
        style("? (y/N)", ANSI_BOLD_YELLOW)
    );
    if !prompt_yes_no(&prompt)? {
        println!("{}", style("Aborted release-now.", ANSI_BOLD_YELLOW));
        println!("{}", curseforge_files_url(project_id));
        return Ok(());
    }

    for jar in jars {
        let mc_version = parse_mc_version_from_jar_name(&jar)?;

        let jar_name = jar
            .file_name()
            .and_then(OsStr::to_str)
            .map(ToString::to_string)
            .ok_or_else(|| eyre::eyre!("Invalid jar filename: {}", jar.display()))?;

        println!(
            "{} {} {} {}",
            style("Uploading", ANSI_BOLD_WHITE),
            style(&jar_name, ANSI_BOLD_MAGENTA),
            style("for MC", ANSI_DIM),
            style(&mc_version, ANSI_BOLD_BLUE)
        );
        if dry_run {
            let game_version_names = game_version_names_for_release(&mc_version)?;
            let colored_metadata_names: Vec<String> = game_version_names
                .iter()
                .map(|name| colorize_metadata_name(name, &mc_version))
                .collect();
            println!(
                "  {} {}",
                style("metadata names:", ANSI_DIM),
                colored_metadata_names.join(", ")
            );
            continue;
        }

        let plan = upload_plans
            .as_ref()
            .and_then(|plans| plans.iter().find(|plan| plan.jar_path == jar))
            .ok_or_else(|| {
                eyre::eyre!("internal error: missing upload plan for {}", jar.display())
            })?;

        let (client, _) = game_version_index.as_ref().ok_or_else(|| {
            eyre::eyre!("internal error: missing CurseForge client for non-dry-run upload")
        })?;

        let uploaded_id = upload_project_file(client, project_id, &jar, &plan.metadata)?;
        amend_file_changelog(
            client,
            project_id,
            uploaded_id,
            &plan.metadata.display_name,
            &plan.metadata.changelog,
        )?;
        println!(
            "  {} {}",
            style("uploaded file id", ANSI_BOLD_GREEN),
            style(&uploaded_id.to_string(), ANSI_BOLD_GREEN)
        );
    }

    if !dry_run {
        println!(
            "{}",
            style("CurseForge release upload complete.", ANSI_BOLD_GREEN)
        );
    }
    println!("{}", curseforge_files_url(project_id));

    Ok(())
}

#[derive(Debug, Clone)]
struct UploadPlan {
    jar_path: PathBuf,
    mc_version: String,
    metadata_names: Vec<String>,
    metadata: UploadMetadata,
}

#[derive(Debug, Clone)]
struct ResolvedMetadataPlan {
    jar_path: PathBuf,
    mc_version: String,
    metadata_names: Vec<String>,
    game_version_ids: Vec<u64>,
}

fn build_resolved_metadata_plans(
    jars: &[PathBuf],
    game_version_index: &HashMap<String, Vec<CurseforgeGameVersion>>,
) -> eyre::Result<Vec<ResolvedMetadataPlan>> {
    let mut plans = Vec::with_capacity(jars.len());

    for jar in jars {
        let mc_version = parse_mc_version_from_jar_name(jar)?;
        let metadata_names = game_version_names_for_release(&mc_version)?;
        let game_version_ids = resolve_game_version_ids(game_version_index, &metadata_names)?;

        plans.push(ResolvedMetadataPlan {
            jar_path: jar.clone(),
            mc_version,
            metadata_names,
            game_version_ids,
        });
    }

    Ok(plans)
}

fn build_upload_plans(
    jars: &[PathBuf],
    mod_version: &str,
    changelog_section: &str,
    game_version_index: &HashMap<String, Vec<CurseforgeGameVersion>>,
) -> eyre::Result<Vec<UploadPlan>> {
    let resolved = build_resolved_metadata_plans(jars, game_version_index)?;
    let mut plans = Vec::with_capacity(resolved.len());

    for plan in resolved {
        let mc_version = plan.mc_version;
        let metadata_names = plan.metadata_names;
        let game_version_ids = plan.game_version_ids;

        let display_name = format!("Super Factory Manager MC{mc_version} v{mod_version}");
        let metadata = UploadMetadata {
            changelog: changelog_section.to_string(),
            changelog_type: "markdown".to_string(),
            display_name,
            game_versions: game_version_ids,
            release_type: "release".to_string(),
        };

        plans.push(UploadPlan {
            jar_path: plan.jar_path,
            mc_version,
            metadata_names,
            metadata,
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

fn is_java_metadata_name(value: &str) -> bool {
    value.starts_with("Java ")
}

fn to_comparison_name_set(values: &[String]) -> BTreeSet<String> {
    values
        .iter()
        .filter(|value| !is_java_metadata_name(value))
        .cloned()
        .collect()
}

fn find_latest_historical_file_for_mc<'a>(
    files: &'a [CurseforgeProjectFileItem],
    mc_version: &str,
) -> eyre::Result<&'a CurseforgeProjectFileItem> {
    files
        .iter()
        .filter(|file| file.game_versions.iter().any(|value| value == mc_version))
        .max_by_key(|file| file.id)
        .ok_or_else(|| eyre::eyre!("No historical project file found for MC {}", mc_version))
}

fn parse_mod_version_from_release_name(name: &str) -> Option<String> {
    let trimmed = name.trim();
    if let Some(without_jar) = trimmed.strip_suffix(".jar") {
        let version = without_jar.rsplit('-').next()?;
        if version
            .chars()
            .all(|character| character.is_ascii_digit() || character == '.')
        {
            return Some(version.to_string());
        }
    }

    if let Some((_, remainder)) = trimmed.rsplit_once('v') {
        let version = remainder.trim();
        if version
            .chars()
            .all(|character| character.is_ascii_digit() || character == '.')
        {
            return Some(version.to_string());
        }
    }

    None
}

fn historical_mod_version(file: &CurseforgeProjectFileItem) -> Option<String> {
    file.file_name
        .as_deref()
        .and_then(parse_mod_version_from_release_name)
        .or_else(|| {
            file.display_name
                .as_deref()
                .and_then(parse_mod_version_from_release_name)
        })
}

fn check_minecraft_version_metadata(
    mc: Option<String>,
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;

    let repo_root = get_repo_root()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let jar_dir = get_jar_dir()?;

    let mod_version = read_mod_version(&gradle_properties)?;
    let all_jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
    let jars = filter_release_jars_by_mc(all_jars, mc.as_deref())?;

    let token_value = resolve_token(token.clone(), op_secret.clone())?;
    let upload_client = build_http_client(&token_value)?;
    let game_versions = fetch_game_versions(&upload_client)?;
    let game_version_index = build_game_version_index(&game_versions);
    let metadata_plans = build_resolved_metadata_plans(&jars, &game_version_index)?;

    let (core_key, credential_source) = resolve_core_api_key(api_key, token, op_secret)?;
    let core_client = build_core_http_client(&core_key)?;
    let project_files = fetch_project_files(&core_client, project_id, &credential_source)?;

    println!("{} {}", style("Project ID:", ANSI_BOLD_CYAN), project_id);
    println!("{} {}", style("Mod version:", ANSI_BOLD_CYAN), mod_version);
    println!(
        "{} {}",
        style("Jars checked:", ANSI_BOLD_CYAN),
        metadata_plans.len()
    );

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

        println!(
            "{} {} {} {} {}",
            style("✓", ANSI_BOLD_GREEN),
            style(&plan.mc_version, ANSI_BOLD_BLUE),
            style("matches historical file", ANSI_DIM),
            historical.id,
            style(&format!("({})", id_pairs.join(", ")), ANSI_DIM)
        );
    }

    println!(
        "{}",
        style(
            "Metadata check passed: computed metadata matches historical uploads.",
            ANSI_BOLD_GREEN
        )
    );

    Ok(())
}

fn validate_release_hashes(
    mc: Option<String>,
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;

    let repo_root = get_repo_root()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let jar_dir = get_jar_dir()?;

    let mod_version = read_mod_version(&gradle_properties)?;
    let all_jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
    let jars = filter_release_jars_by_mc(all_jars, mc.as_deref())?;

    let (core_key, credential_source) = resolve_core_api_key(api_key, token, op_secret)?;
    let core_client = build_core_http_client(&core_key)?;
    let project_files = fetch_project_files(&core_client, project_id, &credential_source)?;

    println!("{} {}", style("Project ID:", ANSI_BOLD_CYAN), project_id);
    println!("{} {}", style("Mod version:", ANSI_BOLD_CYAN), mod_version);
    println!("{} {}", style("Jars checked:", ANSI_BOLD_CYAN), jars.len());

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

        println!(
            "{} {} {} {} {}",
            style("✓", ANSI_BOLD_GREEN),
            style(&mc_version, ANSI_BOLD_BLUE),
            style("hash validated", ANSI_DIM),
            historical.id,
            style(&format!("({})", file_label), ANSI_DIM)
        );
    }

    println!(
        "{}",
        style(
            "Hash validation passed: downloaded CurseForge files match local release jars.",
            ANSI_BOLD_GREEN
        )
    );

    Ok(())
}

fn parse_safety_age(value: &str) -> eyre::Result<Duration> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        eyre::bail!("--safety-age cannot be empty");
    }

    let mut chars = trimmed.chars();
    let unit = chars
        .next_back()
        .ok_or_else(|| eyre::eyre!("Invalid --safety-age value: {trimmed}"))?;
    let amount_text = chars.as_str();
    let amount: u64 = amount_text
        .parse()
        .wrap_err_with(|| format!("Invalid --safety-age amount: {amount_text}"))?;

    let seconds = match unit {
        's' => amount,
        'm' => amount
            .checked_mul(60)
            .ok_or_else(|| eyre::eyre!("--safety-age is too large"))?,
        'h' => amount
            .checked_mul(60)
            .and_then(|mins| mins.checked_mul(60))
            .ok_or_else(|| eyre::eyre!("--safety-age is too large"))?,
        _ => eyre::bail!(
            "Invalid --safety-age unit '{unit}'. Supported units: s, m, h (example: 30m)."
        ),
    };

    Ok(Duration::from_secs(seconds))
}

fn parse_file_age(file_date: Option<&str>, now: DateTime<Utc>) -> eyre::Result<Duration> {
    let Some(file_date) = file_date else {
        eyre::bail!("Cannot determine age for historical file: missing fileDate");
    };
    let parsed = DateTime::parse_from_rfc3339(file_date)
        .wrap_err_with(|| format!("Invalid CurseForge fileDate: {file_date}"))?
        .with_timezone(&Utc);
    let age = now.signed_duration_since(parsed);
    if age.num_seconds() <= 0 {
        return Ok(Duration::from_secs(0));
    }
    age.to_std()
        .wrap_err_with(|| format!("Invalid age computed from fileDate: {file_date}"))
}

fn format_age(value: Duration) -> String {
    let seconds = value.as_secs();
    if seconds >= 3600 {
        format!("{}h{}m", seconds / 3600, (seconds % 3600) / 60)
    } else if seconds >= 60 {
        format!("{}m{}s", seconds / 60, seconds % 60)
    } else {
        format!("{seconds}s")
    }
}

fn release_amend(
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
    safety_age: Option<String>,
) -> eyre::Result<()> {
    let project_id = resolve_project_id(project)?;

    let repo_root = get_repo_root()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let changelog_path = repo_root
        .join("platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml");
    let jar_dir = get_jar_dir()?;

    let mod_version = read_mod_version(&gradle_properties)?;
    let changelog_section = read_changelog_section(&changelog_path, &mod_version)?;
    let wrapped_changelog = format!("```\n{}\n```", changelog_section.trim());

    let token_value = resolve_token(token.clone(), op_secret.clone())?;
    let upload_client = build_http_client(&token_value)?;

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

    let (core_key, credential_source) = resolve_core_api_key(api_key, token, op_secret)?;
    let client = build_core_http_client(&core_key)?;
    let files = fetch_project_files(&client, project_id, &credential_source)?;

    let safety_age_text = safety_age.unwrap_or_else(|| DEFAULT_AMEND_SAFETY_AGE.to_string());
    let max_file_age = parse_safety_age(&safety_age_text)?;
    let now = Utc::now();

    let mut file_targets: Vec<(String, u64, String, String, Duration)> = Vec::new();
    for (mc_version, jar_name) in target_versions {
        let file = find_latest_historical_file_for_mc(&files, &mc_version)?;
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

    println!("{} {}", style("Project ID:", ANSI_BOLD_CYAN), project_id);
    println!("{} {}", style("Mod version:", ANSI_BOLD_CYAN), mod_version);
    println!(
        "{} {}",
        style("Safety age:", ANSI_BOLD_CYAN),
        style(&safety_age_text, ANSI_BOLD_CYAN)
    );
    println!("{}", style("Amend targets:", ANSI_BOLD_CYAN));
    for (mc_version, file_id, old_name, jar_name, file_age) in &file_targets {
        println!(
            "  {} {} {} {} {} {} {} {} {} {}",
            style("MC", ANSI_DIM),
            style(mc_version, ANSI_BOLD_BLUE),
            style("file id", ANSI_DIM),
            file_id,
            style("age", ANSI_DIM),
            style(&format_age(*file_age), ANSI_BOLD_YELLOW),
            style("old", ANSI_DIM),
            old_name,
            style("new", ANSI_DIM),
            jar_name
        );
    }

    let prompt = format!(
        "{} {}",
        style(
            "Proceed to amend changelog on these files?",
            ANSI_BOLD_YELLOW
        ),
        style("(y/N)", ANSI_BOLD_YELLOW)
    );
    if !prompt_yes_no(&prompt)? {
        println!("{}", style("Aborted release amend.", ANSI_BOLD_YELLOW));
        return Ok(());
    }

    for (mc_version, file_id, old_name, jar_name, file_age) in &file_targets {
        println!(
            "{} {} {} {} {} {} {} {} {} {}",
            style("Amending file", ANSI_BOLD_WHITE),
            style(&file_id.to_string(), ANSI_BOLD_MAGENTA),
            style("for MC", ANSI_DIM),
            style(mc_version, ANSI_BOLD_BLUE),
            style("age", ANSI_DIM),
            style(&format_age(*file_age), ANSI_BOLD_YELLOW),
            style("old", ANSI_DIM),
            old_name,
            style("new", ANSI_DIM),
            jar_name
        );
        amend_file_changelog(
            &upload_client,
            project_id,
            *file_id,
            jar_name,
            &wrapped_changelog,
        )?;
    }

    println!(
        "{}",
        style(
            "CurseForge release changelog amend complete.",
            ANSI_BOLD_GREEN
        )
    );

    Ok(())
}

fn amend_file_changelog(
    client: &Client,
    project_id: u64,
    file_id: u64,
    display_name: &str,
    changelog: &str,
) -> eyre::Result<()> {
    let payload = CurseforgeAmendFilePayload {
        file_id,
        changelog: changelog.to_string(),
        changelog_type: "markdown".to_string(),
        display_name: display_name.to_string(),
    };
    let body = facet_json::to_string(&payload)
        .wrap_err("Failed to encode amend changelog payload JSON")?;

    let form = multipart::Form::new().text("metadata", body);

    let url = format!("{CURSEFORGE_API_ROOT}/projects/{project_id}/update-file");
    let response = client
        .post(&url)
        .multipart(form)
        .send()
        .wrap_err_with(|| format!("Failed to amend CurseForge file {file_id}"))?;

    let status = response.status();
    let response_body = response
        .text()
        .wrap_err("Failed to read amend file response body")?;

    if !status.is_success() {
        eyre::bail!(
            "CurseForge amend failed for file {} ({status}): {response_body}",
            file_id
        );
    }

    let _parsed: CurseforgeUploadResponse =
        facet_json::from_str(&response_body).wrap_err("Failed to parse amend response JSON")?;

    Ok(())
}

fn upload_project_file(
    client: &Client,
    project_id: u64,
    jar_path: &Path,
    metadata: &UploadMetadata,
) -> eyre::Result<u64> {
    let metadata_json =
        facet_json::to_string(metadata).wrap_err("Failed to encode upload metadata JSON")?;

    let form = multipart::Form::new()
        .text("metadata", metadata_json)
        .file("file", jar_path)
        .wrap_err_with(|| {
            format!(
                "Failed to attach jar file to upload form: {}",
                jar_path.display()
            )
        })?;

    let url = format!("{CURSEFORGE_API_ROOT}/projects/{project_id}/upload-file");
    let response =
        client.post(&url).multipart(form).send().wrap_err_with(|| {
            format!("Failed to upload jar to CurseForge: {}", jar_path.display())
        })?;

    let status = response.status();
    let body = response
        .text()
        .wrap_err("Failed to read upload response from CurseForge")?;

    if !status.is_success() {
        eyre::bail!(
            "CurseForge upload failed for {} ({status}): {body}",
            jar_path.display()
        );
    }

    let parsed: CurseforgeUploadResponse =
        facet_json::from_str(&body).wrap_err("Failed to parse upload response JSON")?;
    Ok(parsed.id)
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

fn fetch_game_versions(client: &Client) -> eyre::Result<Vec<CurseforgeGameVersion>> {
    let url = format!("{CURSEFORGE_API_ROOT}/game/versions");
    let response = client
        .get(&url)
        .send()
        .wrap_err_with(|| format!("Failed to query game versions: {url}"))?;

    let status = response.status();
    let body = response
        .text()
        .wrap_err("Failed to read game versions response")?;

    debug!(body, url, ?status);

    if !status.is_success() {
        eyre::bail!("CurseForge game versions API failed ({status}): {body}");
    }

    facet_json::from_str(&body).wrap_err("Failed to parse game versions response JSON")
}

fn find_sha1_hash(hashes: &[CurseforgeProjectFileHash]) -> Option<String> {
    hashes.iter().find_map(|hash| {
        let is_sha1 = hash.algo == Some(1);
        if !is_sha1 {
            return None;
        }

        hash.value
            .as_deref()
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(|value| value.to_ascii_lowercase())
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

fn build_game_version_index(
    game_versions: &[CurseforgeGameVersion],
) -> HashMap<String, Vec<CurseforgeGameVersion>> {
    let mut map: HashMap<String, Vec<CurseforgeGameVersion>> =
        HashMap::with_capacity(game_versions.len());
    for version in game_versions {
        let name_key = normalize_version_key(&version.name);
        map.entry(name_key).or_default().push(version.clone());
    }
    map
}

fn format_candidates(candidates: &[CurseforgeGameVersion]) -> String {
    candidates
        .iter()
        .map(|candidate| {
            let type_id = candidate
                .game_version_type_id
                .map_or_else(|| "<none>".to_string(), |id| id.to_string());
            format!(
                "id={},type={},slug={}",
                candidate.id,
                type_id,
                candidate.slug.clone().unwrap_or_default()
            )
        })
        .collect::<Vec<_>>()
        .join("; ")
}

fn resolve_exact_type_id(
    game_version_index: &HashMap<String, Vec<CurseforgeGameVersion>>,
    name: &str,
    required_type_id: u64,
) -> eyre::Result<u64> {
    let key = normalize_version_key(name);
    let candidates = game_version_index
        .get(&key)
        .ok_or_else(|| eyre::eyre!("Could not find required game version '{name}'"))?;

    let matches: Vec<&CurseforgeGameVersion> = candidates
        .iter()
        .filter(|candidate| {
            candidate.name == name && candidate.game_version_type_id == Some(required_type_id)
        })
        .collect();

    match matches.as_slice() {
        [single] => Ok(single.id),
        [] => eyre::bail!(
            "Could not resolve required game version '{}' with type id {}. Candidates: {}",
            name,
            required_type_id,
            format_candidates(candidates)
        ),
        _ => {
            let summary = matches
                .iter()
                .map(|candidate| candidate.id.to_string())
                .collect::<Vec<_>>()
                .join(", ");
            eyre::bail!(
                "Ambiguous game version '{}' with type id {}. Matching IDs: {}",
                name,
                required_type_id,
                summary
            )
        }
    }
}

fn resolve_minecraft_version_id(
    game_version_index: &HashMap<String, Vec<CurseforgeGameVersion>>,
    minecraft_version: &str,
) -> eyre::Result<u64> {
    let key = normalize_version_key(minecraft_version);
    let candidates = game_version_index.get(&key).ok_or_else(|| {
        eyre::eyre!("Could not find required Minecraft version '{minecraft_version}'")
    })?;

    let matches: Vec<&CurseforgeGameVersion> = candidates
        .iter()
        .filter(|candidate| {
            candidate.name == minecraft_version
                && candidate
                    .game_version_type_id
                    .is_some_and(|type_id| type_id != 1 && type_id != 615)
                && parse_version(&candidate.name).is_some()
        })
        .collect();

    match matches.as_slice() {
        [single] => Ok(single.id),
        [] => eyre::bail!(
            "Could not resolve required Minecraft version '{}'. Candidates: {}",
            minecraft_version,
            format_candidates(candidates)
        ),
        _ => {
            let summary = matches
                .iter()
                .map(|candidate| {
                    let type_id = candidate
                        .game_version_type_id
                        .map_or_else(|| "<none>".to_string(), |id| id.to_string());
                    format!("{}(type={})", candidate.id, type_id)
                })
                .collect::<Vec<_>>()
                .join(", ");
            eyre::bail!(
                "Ambiguous Minecraft version '{}'. Matching IDs: {}",
                minecraft_version,
                summary
            )
        }
    }
}

fn resolve_game_version_ids(
    game_version_index: &HashMap<String, Vec<CurseforgeGameVersion>>,
    names: &[String],
) -> eyre::Result<Vec<u64>> {
    let mut output = Vec::with_capacity(names.len());
    for name in names {
        let id = match name.as_str() {
            "Forge" => resolve_exact_type_id(game_version_index, "Forge", 68_441)?,
            "NeoForge" => resolve_exact_type_id(game_version_index, "NeoForge", 68_441)?,
            "Client" => resolve_exact_type_id(game_version_index, "Client", 75_208)?,
            "Server" => resolve_exact_type_id(game_version_index, "Server", 75_208)?,
            "Java 17" => resolve_exact_type_id(game_version_index, "Java 17", 2)?,
            "Java 21" => resolve_exact_type_id(game_version_index, "Java 21", 2)?,
            minecraft if parse_version(minecraft).is_some() => {
                resolve_minecraft_version_id(game_version_index, minecraft)?
            }
            _ => eyre::bail!("Unsupported release metadata name '{name}'"),
        };

        if !output.contains(&id) {
            output.push(id);
        }
    }
    Ok(output)
}

fn normalize_version_key(input: &str) -> String {
    input.trim().to_ascii_lowercase().replace([' ', '_'], "-")
}

fn game_version_names_for_release(mc_version: &str) -> eyre::Result<Vec<String>> {
    let parsed = parse_version(mc_version)
        .ok_or_else(|| eyre::eyre!("Could not parse MC version '{mc_version}'"))?;

    let mut names = vec![
        "Client".to_string(),
        "Server".to_string(),
        mc_version.to_string(),
    ];
    names.extend(loader_names_for(parsed).into_iter().map(str::to_string));
    names.push(java_version_name_for(parsed).to_string());
    Ok(names)
}

fn loader_names_for(parsed: (u32, u32, u32)) -> Vec<&'static str> {
    let v_1_20_0 = (1, 20, 0);
    let v_1_20_1 = (1, 20, 1);

    if parsed <= v_1_20_0 {
        return vec!["Forge"];
    }

    if parsed == v_1_20_1 {
        return vec!["Forge", "NeoForge"];
    }

    vec!["NeoForge"]
}

fn java_version_name_for(parsed: (u32, u32, u32)) -> &'static str {
    let v_1_20_4 = (1, 20, 4);
    if parsed <= v_1_20_4 {
        "Java 17"
    } else {
        "Java 21"
    }
}

impl CurseforgeMinecraftCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Version { command } => super::curseforge_minecraft_version_command::invoke(command),
        }
    }
}

impl CurseforgeMinecraftVersionCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::List {
                mc,
                token,
                op_secret,
            } => super::curseforge_minecraft_version_command::invoke_list(mc, token, op_secret),
        }
    }
}

pub(super) fn invoke_release_check(
    mc: Option<String>,
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    check_minecraft_version_metadata(mc, project, api_key, token, op_secret)
}

pub(super) fn invoke_release_validate(
    mc: Option<String>,
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    validate_release_hashes(mc, project, api_key, token, op_secret)
}

pub(super) fn invoke_release_now(
    project: Option<u64>,
    token: Option<String>,
    op_secret: Option<String>,
    dry_run: bool,
) -> eyre::Result<()> {
    release_now(project, token, op_secret, dry_run)
}

pub(super) fn invoke_release_amend(
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
    safety_age: Option<String>,
) -> eyre::Result<()> {
    release_amend(project, api_key, token, op_secret, safety_age)
}

pub(super) fn invoke_project_default_set(project: u64) -> eyre::Result<()> {
    set_default_project_id(project)
}

pub(super) fn invoke_project_default_show() -> eyre::Result<()> {
    println!("{}", get_default_project_id()?);
    Ok(())
}

pub(super) fn invoke_project_file_list(
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    list_project_files(project, api_key, token, op_secret)
}

pub(super) fn invoke_minecraft_version_list(
    mc: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    let mc = mc.unwrap_or_else(|| ">=1.19.2,<=1.21.1".to_string());
    list_minecraft_versions(&mc, token, op_secret)
}

#[cfg(test)]
mod tests {
    use super::format_age;
    use super::parse_safety_age;
    use std::time::Duration;

    #[test]
    fn parse_safety_age_supports_seconds_minutes_and_hours() {
        assert_eq!(parse_safety_age("45s").unwrap(), Duration::from_secs(45));
        assert_eq!(parse_safety_age("30m").unwrap(), Duration::from_secs(1800));
        assert_eq!(parse_safety_age("2h").unwrap(), Duration::from_secs(7200));
    }

    #[test]
    fn parse_safety_age_rejects_invalid_inputs() {
        assert!(parse_safety_age("").is_err());
        assert!(parse_safety_age("30").is_err());
        assert!(parse_safety_age("30x").is_err());
    }

    #[test]
    fn format_age_uses_readable_units() {
        assert_eq!(format_age(Duration::from_secs(5)), "5s");
        assert_eq!(format_age(Duration::from_secs(90)), "1m30s");
        assert_eq!(format_age(Duration::from_secs(7260)), "2h1m");
    }
}
