use super::ServerAddArgs;
use super::ServerLaunchArgs;
use super::ServerListArgs;
use super::ServerRemoveArgs;
use crate::branch_targets::MinecraftVersion;
use crate::branch_targets::select_required_minecraft_versions;
use crate::cli::jar::BranchSelector;
use crate::jdk::resolve_exact_java;
use crate::paths::APP_HOME;
use crate::worktree::parse_version;
use eyre::Context;
use facet::Facet;
use figue as args;
use glob::Pattern;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use tracing::info;
use tracing::warn;

const SERVER_TARGETS_FILE: &str = "server_targets.tsv";

#[derive(Debug, Clone)]
pub struct ServerTarget {
    pub path: PathBuf,
    pub mc_version: String,
}

/// Arguments for server instance tracking and management commands.
#[derive(Facet, Debug)]
pub struct ServerArgs {
    /// Server subcommand.
    #[facet(args::subcommand)]
    pub command: ServerCommand,
}

impl ServerArgs {
    /// # Errors
    ///
    /// Returns an error if the selected server command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// Server instance tracking and management commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum ServerCommand {
    /// Track server directories matching a glob pattern
    Add(ServerAddArgs),
    /// Untrack server directories matching a glob pattern
    Remove(ServerRemoveArgs),
    /// List tracked server directories matching a glob pattern
    List(ServerListArgs),
    /// Launch tracked servers by running each `run.bat` and waiting for successful exit
    Launch(ServerLaunchArgs),
}

impl ServerCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            ServerCommand::Add(args) => args.invoke(),
            ServerCommand::Remove(args) => args.invoke(),
            ServerCommand::List(args) => args.invoke(),
            ServerCommand::Launch(args) => args.invoke(),
        }
    }
}

/// Load tracked servers from persistence.
///
/// # Errors
///
/// Returns an error if persistence cannot be read.
pub fn load_server_targets() -> eyre::Result<Vec<ServerTarget>> {
    load_targets(SERVER_TARGETS_FILE)
}

pub(super) fn save_server_targets(targets: &[ServerTarget]) -> eyre::Result<()> {
    save_targets(SERVER_TARGETS_FILE, targets)
}

pub(super) fn add_servers(glob_pattern: &str) -> eyre::Result<()> {
    let matched_dirs = expand_directories(glob_pattern)?;

    if matched_dirs.is_empty() {
        eyre::bail!("No directories matched glob: {glob_pattern}");
    }

    let mut targets = load_server_targets()?;
    let mut added = 0usize;
    let mut skipped = 0usize;

    for dir in matched_dirs {
        let Some(mc_version) = determine_mc_version_from_dir_name(&dir) else {
            warn!(
                path = %dir.display(),
                "Skipping directory: unable to infer MC version from directory name"
            );
            skipped += 1;
            continue;
        };

        if targets.iter().any(|target| target.path == dir) {
            skipped += 1;
            continue;
        }

        targets.push(ServerTarget {
            path: dir,
            mc_version,
        });
        added += 1;
    }

    targets.sort_by(|a, b| a.path.cmp(&b.path));
    save_server_targets(&targets)?;

    info!("Added {added} server target(s), skipped {skipped}.");
    Ok(())
}

pub(super) fn remove_servers(glob_pattern: &str) -> eyre::Result<()> {
    let mut targets = load_server_targets()?;
    let before = targets.len();

    let matcher = build_matcher(glob_pattern)?;
    targets.retain(|target| !matcher.matches(&normalize_for_match(&target.path)));

    let removed = before.saturating_sub(targets.len());
    save_server_targets(&targets)?;

    info!("Removed {removed} server target(s).");
    Ok(())
}

pub(super) fn list_servers(glob_pattern: &str, branch: BranchSelector) -> eyre::Result<()> {
    let mut targets = load_server_targets()?;
    let matcher = build_matcher(glob_pattern)?;

    apply_branch_filter(&mut targets, branch)?;

    let filtered: Vec<ServerTarget> = targets
        .into_iter()
        .filter(|target| matcher.matches(&normalize_for_match(&target.path)))
        .collect();

    if filtered.is_empty() {
        info!("No tracked servers match {glob_pattern} and the selected branch filter.");
        return Ok(());
    }

    for target in filtered {
        info!("{}\t{}", target.mc_version, target.path.display());
    }

    Ok(())
}

pub(super) fn launch_servers(branch: BranchSelector) -> eyre::Result<()> {
    let mut targets = load_server_targets()?;

    if targets.is_empty() {
        info!("No tracked servers. Use `sfm-propagate-changes server add <glob>`.");
        return Ok(());
    }

    apply_branch_filter(&mut targets, branch)?;

    if targets.is_empty() {
        info!("No tracked servers match the selected --branch filter.");
        return Ok(());
    }

    for target in targets {
        let run_bat = target.path.join("run.bat");
        if !run_bat.exists() {
            eyre::bail!(
                "Missing run.bat for server target: {}",
                target.path.display()
            );
        }

        let java = resolve_exact_java(required_server_java_release(&target.mc_version)?)?;
        let path = prepend_java_to_path(&java.executable)?;
        info!(
            path = %target.path.display(),
            mc_version = %target.mc_version,
            java = %java.executable.display(),
            "Launching server"
        );
        let status = if cfg!(windows) {
            let mut command = Command::new("cmd");
            command.args(["/C", "run.bat"]);
            command.current_dir(&target.path);
            command.env("PATH", &path);
            if let Some(home) = &java.home {
                command.env("JAVA_HOME", home);
            }
            command.status().wrap_err_with(|| {
                format!(
                    "Failed to execute run.bat for server target: {}",
                    target.path.display()
                )
            })?
        } else {
            let mut command = Command::new(&run_bat);
            command.current_dir(&target.path);
            command.env("PATH", &path);
            if let Some(home) = &java.home {
                command.env("JAVA_HOME", home);
            }
            command.status().wrap_err_with(|| {
                format!(
                    "Failed to execute run.bat for server target: {}",
                    target.path.display()
                )
            })?
        };

        if !status.success() {
            eyre::bail!(
                "Server exited unsuccessfully for {} with status {:?}",
                target.path.display(),
                status.code()
            );
        }
    }

    info!("All selected servers exited successfully.");
    Ok(())
}

fn required_server_java_release(mc_version: &str) -> eyre::Result<u32> {
    let version = MinecraftVersion::parse(mc_version)?;
    if version >= MinecraftVersion::parse("26.0.0")? {
        Ok(25)
    } else if version >= MinecraftVersion::parse("1.21")? {
        Ok(21)
    } else {
        Ok(17)
    }
}

fn prepend_java_to_path(java_executable: &Path) -> eyre::Result<String> {
    let Some(java_bin) = java_executable.parent() else {
        eyre::bail!(
            "Java executable has no parent directory: {}",
            java_executable.display()
        );
    };
    let current_path = std::env::var_os("PATH").unwrap_or_default();
    let mut paths = std::env::split_paths(&current_path).collect::<Vec<_>>();
    paths.insert(0, java_bin.to_path_buf());
    std::env::join_paths(paths)
        .wrap_err_with(|| format!("Failed to prepend Java bin to PATH: {}", java_bin.display()))
        .map(|path| path.to_string_lossy().into_owned())
}

fn apply_branch_filter(
    targets: &mut Vec<ServerTarget>,
    branch: BranchSelector,
) -> eyre::Result<()> {
    let query = branch.into_query()?;
    let versions = select_required_minecraft_versions(&query)?;
    targets.retain(|target| {
        versions
            .iter()
            .any(|version| version.as_str() == target.mc_version)
    });

    Ok(())
}

fn load_targets(file_name: &str) -> eyre::Result<Vec<ServerTarget>> {
    let path = APP_HOME.file_path(file_name);
    if !path.exists() {
        return Ok(Vec::new());
    }

    let content = std::fs::read_to_string(&path)
        .wrap_err_with(|| format!("Failed to read targets file: {}", path.display()))?;

    let mut out = Vec::new();
    for line in content
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty())
    {
        let Some((mc_version, path_text)) = line.split_once('\t') else {
            continue;
        };
        out.push(ServerTarget {
            path: PathBuf::from(path_text),
            mc_version: mc_version.to_string(),
        });
    }

    Ok(out)
}

fn save_targets(file_name: &str, targets: &[ServerTarget]) -> eyre::Result<()> {
    APP_HOME.ensure_dir()?;

    let mut lines = Vec::with_capacity(targets.len());
    for target in targets {
        lines.push(format!("{}\t{}", target.mc_version, target.path.display()));
    }

    let body = if lines.is_empty() {
        String::new()
    } else {
        format!("{}\n", lines.join("\n"))
    };

    let path = APP_HOME.file_path(file_name);
    std::fs::write(&path, body)
        .wrap_err_with(|| format!("Failed to write targets file: {}", path.display()))?;

    info!(path = %path.display(), count = targets.len(), "Saved tracked targets");
    Ok(())
}

pub(super) fn expand_directories(glob_pattern: &str) -> eyre::Result<Vec<PathBuf>> {
    let mut out = Vec::new();

    for entry in glob::glob(glob_pattern)
        .wrap_err_with(|| format!("Invalid glob pattern: {glob_pattern}"))?
    {
        let candidate = entry.wrap_err("Failed to resolve glob match")?;
        if !candidate.is_dir() {
            continue;
        }

        let canonical = dunce::canonicalize(&candidate)
            .wrap_err_with(|| format!("Failed to canonicalize path: {}", candidate.display()))?;
        out.push(canonical);
    }

    out.sort();
    out.dedup();
    Ok(out)
}

pub(super) fn build_matcher(glob_pattern: &str) -> eyre::Result<Pattern> {
    let normalized = glob_pattern.replace('\\', "/");
    Pattern::new(&normalized)
        .wrap_err_with(|| format!("Invalid glob pattern for remove/list: {glob_pattern}"))
}

pub(super) fn determine_mc_version_from_dir_name(dir_path: &Path) -> Option<String> {
    let file_name = dir_path.file_name()?.to_str()?;
    let token = extract_version_token(file_name)?;

    let (major, minor, patch_version) = parse_version(&token)?;
    Some(if patch_version == 0 {
        format!("{major}.{minor}")
    } else {
        format!("{major}.{minor}.{patch_version}")
    })
}

fn extract_version_token(input: &str) -> Option<String> {
    let mut current = String::new();
    let mut tokens = Vec::new();

    for ch in input.chars() {
        if ch.is_ascii_digit() || ch == '.' {
            current.push(ch);
        } else if !current.is_empty() {
            tokens.push(current.clone());
            current.clear();
        }
    }

    if !current.is_empty() {
        tokens.push(current);
    }

    tokens
        .into_iter()
        .find(|token| parse_version(token).is_some())
}

pub(super) fn normalize_for_match(path: &Path) -> String {
    path.to_string_lossy().replace('\\', "/")
}

#[cfg(test)]
mod tests {
    use super::required_server_java_release;

    #[test]
    fn server_java_release_tracks_minecraft_lines() {
        assert_eq!(required_server_java_release("1.20.4").unwrap(), 17);
        assert_eq!(required_server_java_release("1.21").unwrap(), 21);
        assert_eq!(required_server_java_release("1.21.1").unwrap(), 21);
        assert_eq!(required_server_java_release("26.1.2").unwrap(), 25);
    }
}
