#![allow(clippy::doc_markdown)]

use super::GithubReleaseAmendArgs;
use super::GithubReleaseArgs;
use super::GithubReleaseNowArgs;
use crate::branch_targets::select_required_minecraft_versions;
use crate::cli::jar::BranchSelector;
use crate::cli::jar::get_jar_dir;
use crate::cli::repo_root::get_repo_root;
use crate::terminal_output::stdout_prompt;
use crate::worktree::parse_version;
use eyre::Context;
use facet::Facet;
use figue as args;
use std::ffi::OsStr;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use tracing::info;

const DEFAULT_REPO: &str = "TeamDman/SuperFactoryManager";

/// Arguments for GitHub commands.
#[derive(Facet, Debug)]
pub struct GithubArgs {
    /// GitHub subcommand.
    #[facet(args::subcommand)]
    pub command: GithubCommand,
}

impl GithubArgs {
    /// # Errors
    ///
    /// Returns an error if the selected GitHub command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum GithubCommand {
    /// GitHub release operations
    Release(GithubReleaseArgs),
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum GithubReleaseCommand {
    /// Create or update a GitHub release from jars in the configured jar directory
    Now(GithubReleaseNowArgs),
    /// Update title and notes for an existing GitHub release without touching assets
    Amend(GithubReleaseAmendArgs),
}

impl GithubCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Release(args) => args.invoke(),
        }
    }
}

impl GithubReleaseCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Now(args) => args.invoke(),
            Self::Amend(args) => args.invoke(),
        }
    }
}

#[derive(Debug, Clone)]
struct ReleaseJar {
    path: PathBuf,
    filename: String,
    mc_version: String,
    sort_key: (u32, u32, u32),
}

#[derive(Debug, Clone)]
struct ReleaseTag {
    tag: String,
    mc_version: String,
    sort_key: (u32, u32, u32),
}

pub(super) fn release_now(
    branch: BranchSelector,
    repo: Option<String>,
    dry_run: bool,
    yes: bool,
) -> eyre::Result<()> {
    let repo = resolve_repo(repo)?;
    let repo_root = get_repo_root()?;
    let jar_dir = get_jar_dir()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let changelog_path = repo_root
        .join("platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml");

    let mod_version = read_mod_version(&gradle_properties)?;
    let release_title = format!("v{mod_version}");
    let all_jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
    let branch_query = branch.into_query()?;
    let branch_filter_text = branch_query.to_string();
    let jars = filter_release_jars_by_branch(all_jars, &branch_query)?;
    let release_tag = select_release_tag(&repo_root, &mod_version, &jars, &branch_query)?;
    let notes = read_changelog_section(&changelog_path, &mod_version)?;
    let notes_file = write_notes_file(&mod_version, &notes)?;

    // todo(2026-06-16) see other todo note about tracing-subscriber possibly mangling our presentation here - this needs manual testing by me
    info!("Repo:          {repo}");
    info!("Mod version:   {mod_version}");
    info!("Release title: {release_title}");
    info!("Release tag:   {release_tag}");
    info!("Jar dir:       {}", jar_dir.display());
    info!("Branch filter: {branch_filter_text}");
    if dry_run {
        info!("Mode:          dry-run (no GitHub calls)");
    }
    info!("Assets:");
    for jar in &jars {
        info!(" - {}", jar.filename);
    }

    if dry_run {
        info!("Notes file:    {}", notes_file.display());
        return Ok(());
    }

    let release_exists = github_release_exists(&repo, &release_tag)?;
    let action_description = if release_exists {
        "update the existing release and upload selected assets with --clobber"
    } else {
        "create a new release and upload selected assets"
    };

    if !yes {
        let prompt = format!("Proceed to {action_description} for tag {release_tag}? (y/N)");
        if !prompt_yes_no(&prompt)? {
            info!("Aborting GitHub release step.");
            return Ok(());
        }
    }

    if release_exists {
        info!("Release for {release_tag} exists, updating title/notes and uploading assets...");
        run_gh([
            "release",
            "edit",
            &release_tag,
            "--repo",
            &repo,
            "--title",
            &release_title,
            "--notes-file",
            &notes_file.to_string_lossy(),
        ])?;
        upload_release_assets(&repo, &release_tag, &jars)?;
    } else {
        info!("Creating release for {release_tag}...");
        create_release(&repo, &release_tag, &release_title, &notes_file, &jars)?;
    }

    info!("GitHub release step complete.");
    Ok(())
}

pub(super) fn release_amend(
    branch: BranchSelector,
    repo: Option<String>,
    dry_run: bool,
    yes: bool,
) -> eyre::Result<()> {
    let repo = resolve_repo(repo)?;
    let repo_root = get_repo_root()?;
    let jar_dir = get_jar_dir()?;
    let gradle_properties = repo_root.join("platform/minecraft/gradle.properties");
    let changelog_path = repo_root
        .join("platform/minecraft/src/main/resources/assets/sfm/template_programs/changelog.sfml");

    let mod_version = read_mod_version(&gradle_properties)?;
    let release_title = format!("v{mod_version}");
    let all_jars = get_ordered_release_jars(&jar_dir, &mod_version)?;
    let branch_query = branch.into_query()?;
    let branch_filter_text = branch_query.to_string();
    let jars = filter_release_jars_by_branch(all_jars, &branch_query)?;
    let release_tag = select_release_tag(&repo_root, &mod_version, &jars, &branch_query)?;
    let notes = read_changelog_section(&changelog_path, &mod_version)?;
    let notes_file = write_notes_file(&mod_version, &notes)?;

    info!("Repo:          {repo}");
    info!("Mod version:   {mod_version}");
    info!("Release title: {release_title}");
    info!("Release tag:   {release_tag}");
    info!("Jar dir:       {}", jar_dir.display());
    info!("Branch filter: {branch_filter_text}");
    if dry_run {
        info!("Mode:          dry-run (no GitHub mutations)");
    }
    info!("Assets used for target selection:");
    for jar in &jars {
        info!(" - {}", jar.filename);
    }

    if !github_release_exists(&repo, &release_tag)? {
        eyre::bail!(
            "GitHub release for tag {} does not exist in repo {}",
            release_tag,
            repo
        );
    }

    if dry_run {
        info!("Notes file:    {}", notes_file.display());
        info!("Dry-run complete: remote GitHub release exists; no release amended.");
        return Ok(());
    }

    if !yes {
        let prompt = format!("Proceed to amend title/notes for tag {release_tag}? (y/N)");
        if !prompt_yes_no(&prompt)? {
            info!("Aborting GitHub release amend.");
            return Ok(());
        }
    }

    info!("Amending release title/notes for {release_tag}...");
    run_gh([
        "release",
        "edit",
        &release_tag,
        "--repo",
        &repo,
        "--title",
        &release_title,
        "--notes-file",
        &notes_file.to_string_lossy(),
    ])?;

    info!("GitHub release amend complete.");
    Ok(())
}

fn resolve_repo(repo: Option<String>) -> eyre::Result<String> {
    match repo {
        Some(value) => {
            let trimmed = value.trim().to_string();
            if trimmed.is_empty() {
                eyre::bail!("Provided --repo was empty");
            }
            Ok(trimmed)
        }
        None => Ok(DEFAULT_REPO.to_string()),
    }
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

    // Intentionally include the changelog preamble with the SFM links, matching the old
    // github-release.ps1 behavior and the desired GitHub release notes shape.
    Ok(lines[..end_index].join("\n").trim().to_string())
}

fn write_notes_file(mod_version: &str, notes: &str) -> eyre::Result<PathBuf> {
    let notes_file = std::env::temp_dir().join(format!("sfm-release-notes-{mod_version}.md"));
    std::fs::write(&notes_file, notes)
        .wrap_err_with(|| format!("Failed to write release notes: {}", notes_file.display()))?;
    Ok(notes_file)
}

fn get_ordered_release_jars(jar_dir: &Path, mod_version: &str) -> eyre::Result<Vec<ReleaseJar>> {
    if !jar_dir.is_dir() {
        eyre::bail!("Jar directory does not exist: {}", jar_dir.display());
    }

    let suffix = format!("-{mod_version}.jar");
    let mut jars: Vec<ReleaseJar> = std::fs::read_dir(jar_dir)?
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
            let filename = path
                .file_name()
                .and_then(OsStr::to_str)
                .map(ToString::to_string)
                .ok_or_else(|| eyre::eyre!("Invalid jar filename: {}", path.display()))?;
            let mc_version = parse_mc_version_from_jar_name(&filename)?;
            let sort_key = parse_version(&mc_version).ok_or_else(|| {
                eyre::eyre!("Unsupported MC version marker in jar name: {filename}")
            })?;

            Ok(ReleaseJar {
                path,
                filename,
                mc_version,
                sort_key,
            })
        })
        .collect::<eyre::Result<Vec<_>>>()?;

    if jars.is_empty() {
        eyre::bail!(
            "No jar files found in {} for mod version {}",
            jar_dir.display(),
            mod_version
        );
    }

    jars.sort_by(|left, right| {
        let left_key = (left.sort_key, left.filename.as_str());
        let right_key = (right.sort_key, right.filename.as_str());
        left_key.cmp(&right_key)
    });

    Ok(jars)
}

fn filter_release_jars_by_branch(
    jars: Vec<ReleaseJar>,
    branch_query: &crate::branch_targets::BranchQuery,
) -> eyre::Result<Vec<ReleaseJar>> {
    let versions = select_required_minecraft_versions(branch_query)?;
    let filtered: Vec<ReleaseJar> = jars
        .into_iter()
        .filter(|jar| {
            versions
                .iter()
                .any(|version| version.as_str() == jar.mc_version)
        })
        .collect();

    if filtered.is_empty() {
        eyre::bail!("No release jars matched --branch '{branch_query}'.");
    }

    Ok(filtered)
}

fn select_release_tag(
    repo_root: &Path,
    mod_version: &str,
    jars: &[ReleaseJar],
    branch_query: &crate::branch_targets::BranchQuery,
) -> eyre::Result<String> {
    let tags = get_release_tags(repo_root, mod_version)?;
    let selected_mc_versions: Vec<&str> = jars.iter().map(|jar| jar.mc_version.as_str()).collect();

    let mut candidates: Vec<&ReleaseTag> = tags
        .iter()
        .filter(|tag| selected_mc_versions.contains(&tag.mc_version.as_str()))
        .collect();

    if candidates.is_empty() {
        let selected = selected_mc_versions.join(", ");
        eyre::bail!(
            "No local git tags found for mod version {} and selected MC version(s): {}",
            mod_version,
            selected
        );
    }

    candidates.sort_by(|left, right| {
        let left_key = (left.sort_key, left.tag.as_str());
        let right_key = (right.sort_key, right.tag.as_str());
        left_key.cmp(&right_key)
    });

    let selected = candidates
        .last()
        .ok_or_else(|| eyre::eyre!("internal error: empty release tag candidates"))?;

    if jars.len() == 1 && branch_query.to_string() != "core" {
        let exact_tag = format!("{mod_version}-{}", jars[0].mc_version);
        if selected.tag != exact_tag {
            eyre::bail!(
                "Expected exact local tag {} for selected jar {}, but selected {}",
                exact_tag,
                jars[0].filename,
                selected.tag
            );
        }
    }

    Ok(selected.tag.clone())
}

fn get_release_tags(repo_root: &Path, mod_version: &str) -> eyre::Result<Vec<ReleaseTag>> {
    let pattern = format!("{mod_version}-*");
    let output = Command::new("git")
        .args(["tag", "--list", &pattern])
        .current_dir(repo_root)
        .output()
        .wrap_err("Failed to run git tag")?;

    if !output.status.success() {
        eyre::bail!(
            "git tag failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
    }

    let prefix = format!("{mod_version}-");
    let mut tags = Vec::new();
    for line in String::from_utf8_lossy(&output.stdout).lines() {
        let tag = line.trim();
        let Some(mc_version) = tag.strip_prefix(&prefix) else {
            continue;
        };
        let Some(sort_key) = parse_version(mc_version) else {
            continue;
        };
        tags.push(ReleaseTag {
            tag: tag.to_string(),
            mc_version: mc_version.to_string(),
            sort_key,
        });
    }

    if tags.is_empty() {
        eyre::bail!("No local git tags found matching {pattern}");
    }

    Ok(tags)
}

fn parse_mc_version_from_jar_name(filename: &str) -> eyre::Result<String> {
    let marker = "-MC";
    let start = filename
        .find(marker)
        .ok_or_else(|| eyre::eyre!("Could not find '-MC' marker in jar name: {filename}"))?
        + marker.len();
    let remainder = &filename[start..];
    let end = remainder.find('-').ok_or_else(|| {
        eyre::eyre!("Could not find end of MC version marker in jar name: {filename}")
    })?;
    let mc_version = &remainder[..end];

    if parse_version(mc_version).is_none() {
        eyre::bail!("Unsupported MC version marker in jar name: {filename}");
    }

    Ok(mc_version.to_string())
}

fn prompt_yes_no(message: &str) -> eyre::Result<bool> {
    stdout_prompt(format!("{message} "))?;

    let mut input = String::new();
    std::io::stdin()
        .read_line(&mut input)
        .wrap_err("Failed to read confirmation response")?;

    let normalized = input.trim().to_ascii_lowercase();
    Ok(matches!(normalized.as_str(), "y" | "yes"))
}

fn github_release_exists(repo: &str, release_tag: &str) -> eyre::Result<bool> {
    let output = Command::new("gh")
        .args(["release", "view", release_tag, "--repo", repo])
        .output()
        .wrap_err("Failed to run GitHub CLI (`gh`)")?;

    Ok(output.status.success())
}

fn upload_release_assets(repo: &str, release_tag: &str, jars: &[ReleaseJar]) -> eyre::Result<()> {
    let mut args = vec![
        "release".to_string(),
        "upload".to_string(),
        release_tag.to_string(),
    ];
    args.extend(jars.iter().map(|jar| {
        let path = jar.path.to_string_lossy();
        format!("{path}#{}", jar.filename)
    }));
    args.extend([
        "--repo".to_string(),
        repo.to_string(),
        "--clobber".to_string(),
    ]);

    run_gh(args)
}

fn create_release(
    repo: &str,
    release_tag: &str,
    release_title: &str,
    notes_file: &Path,
    jars: &[ReleaseJar],
) -> eyre::Result<()> {
    let mut args = vec![
        "release".to_string(),
        "create".to_string(),
        release_tag.to_string(),
    ];
    args.extend(jars.iter().map(|jar| {
        let path = jar.path.to_string_lossy();
        format!("{path}#{}", jar.filename)
    }));
    args.extend([
        "--repo".to_string(),
        repo.to_string(),
        "--title".to_string(),
        release_title.to_string(),
        "--notes-file".to_string(),
        notes_file.to_string_lossy().to_string(),
    ]);

    run_gh(args)
}

fn run_gh<I, S>(args: I) -> eyre::Result<()>
where
    I: IntoIterator<Item = S>,
    S: AsRef<OsStr>,
{
    let output = Command::new("gh")
        .args(args)
        .output()
        .wrap_err("Failed to run GitHub CLI (`gh`)")?;

    if !output.status.success() {
        eyre::bail!(
            "GitHub CLI command failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
    }

    let stdout = String::from_utf8_lossy(&output.stdout);
    if !stdout.trim().is_empty() {
        info!("{}", stdout.trim());
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::parse_mc_version_from_jar_name;

    #[test]
    fn parses_mc_version_from_release_jar_name() {
        let version =
            parse_mc_version_from_jar_name("Super Factory Manager (SFM)-MC26.1.2-4.33.0.jar")
                .expect("jar name should parse");

        assert_eq!(version, "26.1.2");
    }

    #[test]
    fn rejects_non_sfm_release_jar_name() {
        let _ = parse_mc_version_from_jar_name("example.jar").unwrap_err();
    }
}
