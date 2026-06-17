#![allow(clippy::doc_markdown)]

//! https://docs.modrinth.com/api/operations/getprojectversions/
//! https://docs.modrinth.com/api/operations/createversion/
//! https://modrinth.com/mod/super-factory-manager/versions Project ID - aecUorJQ

use crate::branch_targets::select_required_minecraft_versions;
use crate::modrinth::ModrinthAmendVersionPayload;
use crate::modrinth::ModrinthCreateVersionPayload;
use crate::modrinth::ModrinthCreateVersionResponse;
use crate::modrinth::ModrinthProjectVersion;
use crate::modrinth::ModrinthProjectVersionFile;
use crate::modrinth::ModrinthReleasePlan;
use crate::terminal_output::stdout_prompt;
use crate::worktree::parse_version;
use eyre::Context;
use reqwest::blocking::Client;
use reqwest::blocking::multipart;
use sha1::Digest;
use sha1::Sha1;
use std::collections::BTreeSet;
use std::ffi::OsStr;
use std::fmt::Write as _;
use std::path::Path;
use std::path::PathBuf;
use tracing::debug;

pub(super) const MODRINTH_API_ROOT: &str = "https://api.modrinth.com/v2";
pub(super) const MODRINTH_DEFAULT_PROJECT_ID: &str = "aecUorJQ";
pub(super) const MODRINTH_VERSIONS_URL_PREFIX: &str = "https://modrinth.com/mod";

pub(super) fn prompt_yes_no(message: &str) -> eyre::Result<bool> {
    stdout_prompt(format!("{message} "))?;

    let mut input = String::new();
    std::io::stdin()
        .read_line(&mut input)
        .wrap_err("Failed to read confirmation response")?;

    let normalized = input.trim().to_ascii_lowercase();
    Ok(matches!(normalized.as_str(), "y" | "yes"))
}

pub(super) fn modrinth_versions_url(project_id: &str) -> String {
    format!("{MODRINTH_VERSIONS_URL_PREFIX}/{project_id}/versions")
}

pub(super) fn resolve_project_id(project: Option<String>) -> eyre::Result<String> {
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

pub(super) fn amend_version_changelog(
    client: &Client,
    version_id: &str,
    changelog: &str,
) -> eyre::Result<()> {
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

pub(super) fn create_project_version(
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

pub(super) fn fetch_project_versions(
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

pub(super) fn find_latest_historical_version_for_mc<'a>(
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

pub(super) fn select_download_file<'a>(
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

pub(super) fn to_normalized_set(values: &[String]) -> BTreeSet<String> {
    values
        .iter()
        .map(|value| value.trim().to_ascii_lowercase())
        .filter(|value| !value.is_empty())
        .collect()
}

pub(super) fn build_release_plans(
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

pub(super) fn filter_release_jars_by_branch(
    jars: Vec<PathBuf>,
    branch_query: &crate::branch_targets::BranchQuery,
) -> eyre::Result<Vec<PathBuf>> {
    let versions = select_required_minecraft_versions(branch_query)?;
    let mut filtered = Vec::new();
    for jar in jars {
        let mc_version = parse_mc_version_from_jar_name(&jar)?;
        if versions
            .iter()
            .any(|version| version.as_str() == mc_version)
        {
            filtered.push(jar);
        }
    }

    if filtered.is_empty() {
        eyre::bail!("No release jars matched --branch '{branch_query}'.");
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

pub(super) fn compute_wrapped_release_changelog(
    repo_root: &Path,
    mod_version: &str,
) -> eyre::Result<String> {
    let changelog_path = repo_root
        .join("platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml");
    let changelog_section = read_changelog_section(&changelog_path, mod_version)?;
    Ok(format!("```\n{}\n```", changelog_section.trim()))
}
