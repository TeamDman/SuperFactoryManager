#![allow(clippy::doc_markdown)]

//! https://docs.curseforge.com/rest-api/#get-mod
//! https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-api
//! https://www.curseforge.com/minecraft/mc-mods/super-factory-manager Project ID - 306935

use crate::branch_targets::BranchQuery;
use crate::branch_targets::MinecraftVersion;
use crate::branch_targets::select_required_minecraft_versions;
use crate::curseforge::CurseforgeAmendFilePayload;
use crate::curseforge::CurseforgeGameVersion;
use crate::curseforge::CurseforgeGameVersionId;
use crate::curseforge::CurseforgeProjectFileHash;
use crate::curseforge::CurseforgeProjectFileId;
use crate::curseforge::CurseforgeProjectFileItem;
use crate::curseforge::CurseforgeProjectFileListEnvelope;
use crate::curseforge::CurseforgeProjectId;
use crate::curseforge::CurseforgeUploadResponse;
use crate::curseforge::ResolvedMetadataPlan;
use crate::curseforge::UploadMetadata;
use crate::curseforge::UploadPlan;
use crate::paths::APP_HOME;
use crate::terminal_output::stdout_prompt;
use crate::worktree::parse_version;
use chrono::DateTime;
use chrono::Utc;
use color_eyre::owo_colors::OwoColorize;
use eyre::Context;
use reqwest::blocking::Client;
use reqwest::blocking::multipart;
use sha1::Digest;
use sha1::Sha1;
use std::collections::BTreeSet;
use std::collections::HashMap;
use std::ffi::OsStr;
use std::fmt::Write as _;
use std::path::Path;
use std::path::PathBuf;
use std::time::Duration;
use tracing::debug;

pub(super) const CURSEFORGE_API_ROOT: &str = "https://minecraft.curseforge.com/api";
pub(super) const CURSEFORGE_CORE_API_ROOT: &str = "https://api.curseforge.com/v1";
pub(super) const CURSEFORGE_DEFAULT_PROJECT_FILE: &str = "curseforge_project_id.txt";
pub(super) const CURSEFORGE_DEFAULT_PROJECT_ID: u64 = 306_935;
pub(super) const DEFAULT_AMEND_SAFETY_AGE: &str = "30m";
pub(super) const POPULAR_DOWNLOAD_THRESHOLD: u64 = 1_000;
pub(super) const CURSEFORGE_AUTHORS_FILES_URL_PREFIX: &str =
    "https://authors.curseforge.com/#/projects";

pub(super) fn prompt_yes_no(message: &str) -> eyre::Result<bool> {
    stdout_prompt(format!("{message} "))?;

    let mut input = String::new();
    std::io::stdin()
        .read_line(&mut input)
        .wrap_err("Failed to read confirmation response")?;

    let normalized = input.trim().to_ascii_lowercase();
    Ok(matches!(normalized.as_str(), "y" | "yes"))
}

pub(super) fn colorize_metadata_name(name: &str, mc_version: &str) -> String {
    // todo(2026-06-16) shouldn't we have an enum for the known variants with an Other(String) escape hatch where this fn would be an instance method?
    if name == mc_version {
        return name.blue().bold().to_string();
    }

    match name {
        "NeoForge" => name.red().bold().to_string(),
        "Forge" => name.yellow().bold().to_string(),
        "Java 17" => name.green().bold().to_string(),
        "Java 21" | "Java 25" => name.cyan().bold().to_string(),
        _ => name.to_string(),
    }
}

pub(super) fn curseforge_files_url(project_id: CurseforgeProjectId) -> String {
    format!("{CURSEFORGE_AUTHORS_FILES_URL_PREFIX}/{project_id}/files")
}

pub(super) fn get_default_project_id() -> eyre::Result<CurseforgeProjectId> {
    let path = APP_HOME.file_path(CURSEFORGE_DEFAULT_PROJECT_FILE);
    if !path.exists() {
        return Ok(CurseforgeProjectId(CURSEFORGE_DEFAULT_PROJECT_ID));
    }

    let content = std::fs::read_to_string(&path)
        .wrap_err_with(|| format!("Failed to read default project file: {}", path.display()))?;

    let trimmed = content.trim();
    if trimmed.is_empty() {
        return Ok(CurseforgeProjectId(CURSEFORGE_DEFAULT_PROJECT_ID));
    }

    trimmed
        .parse::<u64>()
        .map(CurseforgeProjectId)
        .wrap_err_with(|| format!("Invalid project id in default project file: '{trimmed}'"))
}

pub(super) fn resolve_project_id(project: Option<u64>) -> eyre::Result<CurseforgeProjectId> {
    match project {
        Some(project_id) => Ok(CurseforgeProjectId(project_id)),
        None => get_default_project_id(),
    }
}

pub(super) fn fetch_project_files(
    client: &Client,
    project_id: CurseforgeProjectId,
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

pub(crate) fn latest_two_release_files_for_mc<'a>(
    files: &'a [CurseforgeProjectFileItem],
    mc_version: &MinecraftVersion,
) -> Vec<&'a CurseforgeProjectFileItem> {
    let mut release_files = files
        .iter()
        .filter(|file| {
            file.game_versions
                .iter()
                .any(|version| version == mc_version.as_str())
        })
        .filter_map(|file| {
            let version = historical_mod_version_parts(file)?;
            Some((version, file))
        })
        .collect::<Vec<_>>();

    release_files.sort_by(|(left_version, left_file), (right_version, right_file)| {
        right_version
            .cmp(left_version)
            .then_with(|| right_file.id.cmp(&left_file.id))
    });

    let mut latest_files = Vec::new();
    let mut seen_versions = BTreeSet::new();
    for (version, file) in release_files {
        if !seen_versions.insert(version) {
            continue;
        }
        latest_files.push(file);
        if latest_files.len() == 2 {
            break;
        }
    }

    latest_files
}

fn historical_mod_version_parts(file: &CurseforgeProjectFileItem) -> Option<Vec<u32>> {
    historical_mod_version(file).and_then(|version| parse_dotted_u32_version(&version))
}

fn parse_dotted_u32_version(version: &str) -> Option<Vec<u32>> {
    let mut parts = Vec::new();
    for part in version.split('.') {
        parts.push(part.parse().ok()?);
    }
    (!parts.is_empty()).then_some(parts)
}

pub(super) fn build_resolved_metadata_plans(
    jars: &[PathBuf],
    game_version_index: &HashMap<String, Vec<CurseforgeGameVersion>>,
) -> eyre::Result<Vec<ResolvedMetadataPlan>> {
    let mut plans = Vec::with_capacity(jars.len());

    for jar in jars {
        let mc_version = parse_mc_version_from_jar_name(jar)?;
        let metadata_names = game_version_names_for_release(&mc_version)?;
        let game_version_ids =
            CurseforgeGameVersionId::resolve_game_version_ids(game_version_index, &metadata_names)?;

        plans.push(ResolvedMetadataPlan {
            jar_path: jar.clone(),
            mc_version,
            metadata_names,
            game_version_ids,
        });
    }

    Ok(plans)
}

pub(super) fn build_upload_plans(
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

pub(super) fn filter_release_jars_by_branch(
    jars: Vec<PathBuf>,
    branch_query: &BranchQuery,
) -> eyre::Result<Vec<PathBuf>> {
    let selected_versions = select_required_minecraft_versions(branch_query)?;
    let mut filtered = Vec::new();
    for jar in jars {
        let mc_version = parse_mc_version_from_jar_name(&jar)?; // todo(2026-06-16) should we have a SfmJarName newtype?
        if selected_versions
            .iter()
            .any(|selected| selected.as_str() == mc_version)
        {
            filtered.push(jar);
        }
    }

    if filtered.is_empty() {
        eyre::bail!("No release jars matched --branch '{branch_query}'.");
    }

    Ok(filtered)
}

pub(super) fn is_java_metadata_name(value: &str) -> bool {
    value.starts_with("Java ")
}

pub(super) fn to_comparison_name_set(values: &[String]) -> BTreeSet<String> {
    values
        .iter()
        .filter(|value| !is_java_metadata_name(value))
        .cloned()
        .collect()
}

pub(super) fn find_latest_historical_file_for_mc<'a>(
    files: &'a [CurseforgeProjectFileItem],
    mc_version: &str,
) -> eyre::Result<&'a CurseforgeProjectFileItem> {
    files
        .iter()
        .filter(|file| file.game_versions.iter().any(|value| value == mc_version))
        .max_by_key(|file| file.id)
        .ok_or_else(|| eyre::eyre!("No historical project file found for MC {}", mc_version))
}

pub(super) fn parse_mod_version_from_release_name(name: &str) -> Option<String> {
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

pub(super) fn historical_mod_version(file: &CurseforgeProjectFileItem) -> Option<String> {
    file.file_name
        .as_deref()
        .and_then(parse_mod_version_from_release_name)
        .or_else(|| {
            file.display_name
                .as_deref()
                .and_then(parse_mod_version_from_release_name)
        })
}

pub(super) fn parse_safety_age(value: &str) -> eyre::Result<Duration> {
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

pub(super) fn parse_file_age(
    file_date: Option<&str>,
    now: DateTime<Utc>,
) -> eyre::Result<Duration> {
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

pub(super) fn format_age(value: Duration) -> String {
    let seconds = value.as_secs();
    if seconds >= 3600 {
        format!("{}h{}m", seconds / 3600, (seconds % 3600) / 60)
    } else if seconds >= 60 {
        format!("{}m{}s", seconds / 60, seconds % 60)
    } else {
        format!("{seconds}s")
    }
}

pub(super) fn amend_file_changelog(
    client: &Client,
    project_id: CurseforgeProjectId,
    file_id: CurseforgeProjectFileId,
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

pub(super) fn upload_project_file(
    client: &Client,
    project_id: CurseforgeProjectId,
    jar_path: &Path,
    metadata: &UploadMetadata,
) -> eyre::Result<CurseforgeProjectFileId> {
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

pub(super) fn read_mod_version(gradle_properties: &Path) -> eyre::Result<String> {
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

pub(super) fn read_changelog_section(
    changelog_path: &Path,
    mod_version: &str,
) -> eyre::Result<String> {
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

pub(super) fn get_ordered_release_jars(
    jar_dir: &Path,
    mod_version: &str,
) -> eyre::Result<Vec<PathBuf>> {
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

pub(super) fn parse_mc_version_from_jar_name(jar_path: &Path) -> eyre::Result<String> {
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

pub(super) fn fetch_game_versions(client: &Client) -> eyre::Result<Vec<CurseforgeGameVersion>> {
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

pub(super) fn find_sha1_hash(hashes: &[CurseforgeProjectFileHash]) -> Option<String> {
    hashes.iter().find_map(|hash| {
        let is_sha1 = hash.algo == Some(1);
        if !is_sha1 {
            return None;
        }

        hash.value
            .as_deref()
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(str::to_ascii_lowercase)
    })
}

pub(super) fn download_sha1(client: &Client, url: &str) -> eyre::Result<String> {
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

pub(super) fn sha1_hex(bytes: &[u8]) -> String {
    let mut hasher = Sha1::new();
    hasher.update(bytes);
    let digest = hasher.finalize();
    let mut output = String::with_capacity(digest.len() * 2);
    for byte in digest {
        let _ = write!(output, "{byte:02x}");
    }
    output
}

pub(super) fn game_version_names_for_release(mc_version: &str) -> eyre::Result<Vec<String>> {
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

pub(super) fn loader_names_for(parsed: (u32, u32, u32)) -> Vec<&'static str> {
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

pub(super) fn java_version_name_for(parsed: (u32, u32, u32)) -> &'static str {
    let v_1_20_4 = (1, 20, 4);
    let v_26_0_0 = (26, 0, 0);
    if parsed <= v_1_20_4 {
        "Java 17"
    } else if parsed >= v_26_0_0 {
        "Java 25"
    } else {
        "Java 21"
    }
}

#[cfg(test)]
mod tests {
    use super::format_age;
    use super::java_version_name_for;
    use super::parse_safety_age;
    use std::time::Duration;

    #[test]
    fn parse_safety_age_supports_seconds_minutes_and_hours() {
        assert_eq!(parse_safety_age("45s").unwrap(), Duration::from_secs(45));
        assert_eq!(parse_safety_age("30m").unwrap(), Duration::from_mins(30));
        assert_eq!(parse_safety_age("2h").unwrap(), Duration::from_hours(2));
    }

    #[test]
    fn parse_safety_age_rejects_invalid_inputs() {
        let _ = parse_safety_age("").unwrap_err();
        let _ = parse_safety_age("30").unwrap_err();
        let _ = parse_safety_age("30x").unwrap_err();
    }

    #[test]
    fn format_age_uses_readable_units() {
        assert_eq!(format_age(Duration::from_secs(5)), "5s");
        assert_eq!(format_age(Duration::from_secs(90)), "1m30s");
        assert_eq!(format_age(Duration::from_mins(121)), "2h1m");
    }

    #[test]
    fn java_version_metadata_tracks_supported_minecraft_lines() {
        assert_eq!(java_version_name_for((1, 20, 4)), "Java 17");
        assert_eq!(java_version_name_for((1, 21, 1)), "Java 21");
        assert_eq!(java_version_name_for((26, 1, 2)), "Java 25");
    }
}
