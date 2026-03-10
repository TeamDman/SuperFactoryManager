use crate::mc_version_filter::McVersionFilter;
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

/// Server instance tracking and management commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum ServerCommand {
    /// Track server directories matching a glob pattern
    Add {
        /// Glob pattern for server directories
        #[facet(args::positional)]
        glob: String,
    },
    /// Untrack server directories matching a glob pattern
    Remove {
        /// Glob pattern for tracked server directories
        #[facet(args::positional)]
        glob: String,
    },
    /// List tracked server directories matching a glob pattern
    List {
        /// Glob pattern for tracked server directories
        #[facet(default, args::positional)]
        glob: Option<String>,
        /// Minecraft version filter expression for tracked servers (examples: `>=1.21.0`, `<1.20`, `=1.20.4`, `=1.19.2 OR =1.21.1`).
        #[facet(default, args::named)]
        mc: Option<String>,
    },
    /// Launch tracked servers by running each `run.bat` and waiting for successful exit
    Launch {
        /// Minecraft version filter expression for tracked servers (examples: `>=1.21.0`, `<1.20`, `=1.20.4`, `=1.19.2 OR =1.21.1`).
        #[facet(default, args::named)]
        mc: Option<String>,
    },
}

impl ServerCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            ServerCommand::Add { glob } => super::server_add_command::invoke(glob),
            ServerCommand::Remove { glob } => super::server_remove_command::invoke(glob),
            ServerCommand::List { glob, mc } => super::server_list_command::invoke(glob, mc),
            ServerCommand::Launch { mc } => super::server_launch_command::invoke(mc),
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

    println!("Added {added} server target(s), skipped {skipped}.");
    Ok(())
}

pub(super) fn remove_servers(glob_pattern: &str) -> eyre::Result<()> {
    let mut targets = load_server_targets()?;
    let before = targets.len();

    let matcher = build_matcher(glob_pattern)?;
    targets.retain(|target| !matcher.matches(&normalize_for_match(&target.path)));

    let removed = before.saturating_sub(targets.len());
    save_server_targets(&targets)?;

    println!("Removed {removed} server target(s).");
    Ok(())
}

pub(super) fn list_servers(glob_pattern: &str, mc_filter: Option<&str>) -> eyre::Result<()> {
    let mut targets = load_server_targets()?;
    let matcher = build_matcher(glob_pattern)?;

    apply_mc_filter(&mut targets, mc_filter)?;

    let filtered: Vec<ServerTarget> = targets
        .into_iter()
        .filter(|target| matcher.matches(&normalize_for_match(&target.path)))
        .collect();

    if filtered.is_empty() {
        if mc_filter.is_some() {
            println!("No tracked servers match the requested filters.");
        } else {
            println!("No tracked servers match {glob_pattern}.");
        }
        return Ok(());
    }

    for target in filtered {
        println!("{}\t{}", target.mc_version, target.path.display());
    }

    Ok(())
}

pub(super) fn launch_servers(mc_filter: Option<&str>) -> eyre::Result<()> {
    let mut targets = load_server_targets()?;

    if targets.is_empty() {
        println!("No tracked servers. Use `sfm-propagate-changes server add <glob>`.");
        return Ok(());
    }

    apply_mc_filter(&mut targets, mc_filter)?;

    if targets.is_empty() {
        if mc_filter.is_some() {
            println!("No tracked servers match the requested --mc filter.");
        } else {
            println!("No tracked servers.");
        }
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

        info!(
            path = %target.path.display(),
            mc_version = %target.mc_version,
            "Launching server"
        );

        let status = if cfg!(windows) {
            Command::new("cmd")
                .args(["/C", "run.bat"])
                .current_dir(&target.path)
                .status()
                .wrap_err_with(|| {
                    format!(
                        "Failed to execute run.bat for server target: {}",
                        target.path.display()
                    )
                })?
        } else {
            Command::new(&run_bat)
                .current_dir(&target.path)
                .status()
                .wrap_err_with(|| {
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

    println!("All selected servers exited successfully.");
    Ok(())
}

fn apply_mc_filter(targets: &mut Vec<ServerTarget>, mc_filter: Option<&str>) -> eyre::Result<()> {
    let parsed_filter = mc_filter.map(McVersionFilter::parse).transpose()?;

    if let Some(filter) = parsed_filter {
        targets.retain(|target| {
            if let Some(matches) = filter.matches_version_text(&target.mc_version) {
                matches
            } else {
                warn!(
                    mc_version = %target.mc_version,
                    path = %target.path.display(),
                    "Skipping tracked server with non-version mc value for --mc filter"
                );
                false
            }
        });
    }

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
