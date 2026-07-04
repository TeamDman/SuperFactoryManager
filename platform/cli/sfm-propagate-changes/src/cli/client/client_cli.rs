use super::ClientAddArgs;
use super::ClientGetInstancesDirArgs;
use super::ClientGetLauncherArgs;
use super::ClientLaunchArgs;
use super::ClientListArgs;
use super::ClientOpenArgs;
use super::ClientRemoveArgs;
use super::ClientSetInstancesDirArgs;
use super::ClientSetLauncherArgs;
use super::ClientSyncArgs;
use crate::branch_targets::select_required_minecraft_versions;
use crate::branch_targets::select_required_worktree_targets;
use crate::cli::jar::BranchSelector;
use crate::cli::jar::JarUpdateClientsArgs;
use crate::cli::jar::resolve_client_mods_dir;
use crate::paths::APP_HOME;
use crate::prism::PrismComponent;
use crate::prism::PrismInstancePlan;
use crate::prism::PrismLoaderSelection;
use crate::terminal_output::stdout_line;
use crate::worktree::parse_version;
use eyre::Context;
use facet::Facet;
use figue as args;
use glob::Pattern;
#[cfg(windows)]
use std::os::windows::process::CommandExt;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use tracing::debug;
use tracing::info;
use tracing::warn;

const CLIENT_TARGETS_FILE: &str = "client_targets.tsv";
const CLIENT_LAUNCHER_FILE: &str = "client_launcher.txt";
const CLIENT_INSTANCES_DIR_FILE: &str = "client_instances_dir.txt";

#[cfg(windows)]
const DETACHED_PROCESS: u32 = 0x0000_0008;

#[derive(Debug, Clone)]
pub struct ClientTarget {
    pub path: PathBuf,
    pub mc_version: String,
}

/// Arguments for client instance tracking and management commands.
#[derive(Facet, Debug)]
pub struct ClientArgs {
    /// Client subcommand.
    #[facet(args::subcommand)]
    pub command: ClientCommand,
}

impl ClientArgs {
    /// # Errors
    ///
    /// Returns an error if the selected client command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// Client instance tracking and management commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum ClientCommand {
    /// Track client directories matching a glob pattern
    Add(ClientAddArgs),
    /// Untrack client directories matching a glob pattern
    Remove(ClientRemoveArgs),
    /// List tracked client directories matching a glob pattern
    List(ClientListArgs),
    /// Set the launcher executable path used by `client open` and `client launch`
    #[facet(rename = "set-launcher")]
    SetLauncher(ClientSetLauncherArgs),
    /// Show the configured launcher executable path
    #[facet(rename = "get-launcher")]
    GetLauncher(ClientGetLauncherArgs),
    /// Set the Prism Launcher instances directory managed by `client sync`
    #[facet(rename = "set-instances-dir")]
    SetInstancesDir(ClientSetInstancesDirArgs),
    /// Show the configured Prism Launcher instances directory
    #[facet(rename = "get-instances-dir")]
    GetInstancesDir(ClientGetInstancesDirArgs),
    /// Create, track, and update SFM verification client instances
    Sync(ClientSyncArgs),
    /// Open configured client launcher detached without launching an instance
    Open(ClientOpenArgs),
    /// Launch tracked Prism client instances and wait for each launcher invocation
    Launch(ClientLaunchArgs),
}

impl ClientCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            ClientCommand::Add(args) => args.invoke(),
            ClientCommand::Remove(args) => args.invoke(),
            ClientCommand::List(args) => args.invoke(),
            ClientCommand::SetLauncher(args) => args.invoke(),
            ClientCommand::GetLauncher(args) => args.invoke(),
            ClientCommand::SetInstancesDir(args) => args.invoke(),
            ClientCommand::GetInstancesDir(args) => args.invoke(),
            ClientCommand::Sync(args) => args.invoke(),
            ClientCommand::Open(args) => args.invoke(),
            ClientCommand::Launch(args) => args.invoke(),
        }
    }
}

/// Load tracked clients from persistence.
///
/// # Errors
///
/// Returns an error if persistence cannot be read.
pub fn load_client_targets() -> eyre::Result<Vec<ClientTarget>> {
    load_targets(CLIENT_TARGETS_FILE)
}

pub(super) fn save_client_targets(targets: &[ClientTarget]) -> eyre::Result<()> {
    save_targets(CLIENT_TARGETS_FILE, targets)
}

pub(super) fn add_clients(glob_pattern: &str) -> eyre::Result<()> {
    let matched_dirs = expand_directories(glob_pattern)?;

    if matched_dirs.is_empty() {
        eyre::bail!("No directories matched glob: {glob_pattern}");
    }

    let mut targets = load_client_targets()?;
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

        targets.push(ClientTarget {
            path: dir,
            mc_version,
        });
        added += 1;
    }

    targets.sort_by(|a, b| a.path.cmp(&b.path));
    save_client_targets(&targets)?;

    info!("Added {added} client target(s), skipped {skipped}.");
    Ok(())
}

pub(super) fn remove_clients(glob_pattern: &str) -> eyre::Result<()> {
    let mut targets = load_client_targets()?;
    let before = targets.len();

    let matcher = build_matcher(glob_pattern)?;
    targets.retain(|target| !matcher.matches(&normalize_for_match(&target.path)));

    let removed = before.saturating_sub(targets.len());
    save_client_targets(&targets)?;

    info!("Removed {removed} client target(s).");
    Ok(())
}

pub(super) fn list_clients(glob_pattern: &str) -> eyre::Result<()> {
    let targets = load_client_targets()?;
    let matcher = build_matcher(glob_pattern)?;

    let filtered: Vec<ClientTarget> = targets
        .into_iter()
        .filter(|target| matcher.matches(&normalize_for_match(&target.path)))
        .collect();

    if filtered.is_empty() {
        info!("No tracked clients match {glob_pattern}.");
        return Ok(());
    }

    for target in filtered {
        info!("{}\t{}", target.mc_version, target.path.display());
    }

    Ok(())
}

pub(super) fn set_launcher(path: &Path) -> eyre::Result<()> {
    let canonical = dunce::canonicalize(path)
        .wrap_err_with(|| format!("Failed to canonicalize launcher path: {}", path.display()))?;

    if !canonical.is_file() {
        eyre::bail!("Launcher path is not a file: {}", canonical.display());
    }

    APP_HOME.ensure_dir()?;
    let launcher_file = APP_HOME.file_path(CLIENT_LAUNCHER_FILE);

    std::fs::write(&launcher_file, canonical.display().to_string())
        .wrap_err_with(|| format!("Failed to write launcher file: {}", launcher_file.display()))?;

    info!(path = %canonical.display(), "Set client launcher path");
    Ok(())
}

pub(super) fn launch_clients(branch: BranchSelector) -> eyre::Result<()> {
    let launcher = get_launcher_path()?;
    let instances_dir = get_instances_dir_path()?;
    let prism_data_dir = instances_dir.parent().ok_or_else(|| {
        eyre::eyre!(
            "Configured client instances directory has no parent data directory: {}",
            instances_dir.display()
        )
    })?;
    let mut targets = load_client_targets()?;

    if targets.is_empty() {
        info!("No tracked clients. Use `sfm-propagate-changes client sync --branch <selector>`.");
        return Ok(());
    }

    apply_branch_filter(&mut targets, branch)?;

    if targets.is_empty() {
        info!("No tracked clients match the selected --branch filter.");
        return Ok(());
    }

    let prism_data_dir_text = prism_data_dir.to_string_lossy().to_string();

    for target in targets {
        let instance_id = target
            .path
            .file_name()
            .and_then(|name| name.to_str())
            .ok_or_else(|| {
                eyre::eyre!(
                    "Unable to infer Prism instance id from path: {}",
                    target.path.display()
                )
            })?;

        info!(
            path = %target.path.display(),
            mc_version = %target.mc_version,
            instance_id,
            "Launching client"
        );

        let args = [
            "--dir",
            prism_data_dir_text.as_str(),
            "--launch",
            instance_id,
        ];
        debug!(
            executable = %launcher.display(),
            args = ?args,
            "Executing Prism launcher command"
        );

        let status = Command::new(&launcher)
            .args(args)
            .status()
            .wrap_err_with(|| {
                format!(
                    "Failed to launch Prism instance {instance_id} with launcher: {}",
                    launcher.display()
                )
            })?;

        if !status.success() {
            eyre::bail!(
                "Client launcher exited unsuccessfully for {} with status {:?}",
                target.path.display(),
                status.code()
            );
        }

        debug!(
            executable = %launcher.display(),
            instance_id,
            status = ?status.code(),
            "Prism launcher command completed"
        );
    }

    info!(
        "All selected Prism launch commands completed successfully. Prism may keep Minecraft running after the CLI handoff exits."
    );
    Ok(())
}

pub(super) fn get_launcher() -> eyre::Result<()> {
    let launcher = get_launcher_path()?;
    stdout_line(launcher.display())
}

pub(super) fn open_client_launcher() -> eyre::Result<()> {
    let launcher = get_launcher_path()?;

    let mut command = Command::new(&launcher);

    #[cfg(windows)]
    command.creation_flags(DETACHED_PROCESS);

    command
        .spawn()
        .wrap_err_with(|| format!("Failed to open client launcher: {}", launcher.display()))?;

    info!(path = %launcher.display(), "Opened client launcher detached");
    Ok(())
}

pub(super) fn set_instances_dir(path: &Path) -> eyre::Result<()> {
    std::fs::create_dir_all(path)
        .wrap_err_with(|| format!("Failed to create instances directory: {}", path.display()))?;
    let canonical = dunce::canonicalize(path).wrap_err_with(|| {
        format!(
            "Failed to canonicalize instances directory: {}",
            path.display()
        )
    })?;

    if !canonical.is_dir() {
        eyre::bail!("Instances path is not a directory: {}", canonical.display());
    }

    APP_HOME.ensure_dir()?;
    let instances_dir_file = APP_HOME.file_path(CLIENT_INSTANCES_DIR_FILE);

    std::fs::write(&instances_dir_file, canonical.display().to_string()).wrap_err_with(|| {
        format!(
            "Failed to write instances directory file: {}",
            instances_dir_file.display()
        )
    })?;

    info!(path = %canonical.display(), "Set client instances directory");
    Ok(())
}

pub(super) fn get_instances_dir() -> eyre::Result<()> {
    let instances_dir = get_instances_dir_path()?;
    stdout_line(instances_dir.display())
}

pub(super) fn sync_clients(
    branch: BranchSelector,
    loader_selection: PrismLoaderSelection,
) -> eyre::Result<()> {
    let prism_instances_root = get_instances_dir_path()?;
    let query = branch.into_query()?;
    let targets_to_sync = select_required_worktree_targets(&query)?;
    let mut targets = load_client_targets()?;
    let mut created = 0usize;
    let mut added = 0usize;
    let mut existing = 0usize;

    for target in targets_to_sync {
        let Some(version) = target.mc_version.as_ref() else {
            continue;
        };
        let mc_version = version.to_string();
        let instance_dir = prism_instances_root.join(format!("sfm-{mc_version}"));
        let mods_dir = resolve_client_mods_dir(&instance_dir, &mc_version)?;
        let instance_plan = crate::prism::instance_plan_for_target(&target, loader_selection)?;

        if instance_dir.exists() {
            existing += 1;
        } else {
            created += 1;
        }

        std::fs::create_dir_all(&mods_dir).wrap_err_with(|| {
            format!(
                "Failed to create client instance mods directory: {}",
                mods_dir.display()
            )
        })?;
        write_prism_instance_metadata(&instance_dir, &instance_plan)?;

        if !targets.iter().any(|target| target.path == instance_dir) {
            targets.push(ClientTarget {
                path: instance_dir,
                mc_version,
            });
            added += 1;
        }
    }

    targets.sort_by(|a, b| a.path.cmp(&b.path));
    save_client_targets(&targets)?;
    JarUpdateClientsArgs.invoke()?;

    info!(
        "Synchronized client instances: created {created}, existing {existing}, newly tracked {added}."
    );
    Ok(())
}

fn write_prism_instance_metadata(
    instance_dir: &Path,
    plan: &PrismInstancePlan,
) -> eyre::Result<()> {
    let mc_version = &plan.minecraft_version;
    let instance_name = format!("sfm-{mc_version}");
    let java_path = prism_path(&javaw_path(plan));
    let java_home = plan
        .java
        .home
        .as_ref()
        .map_or_else(String::new, |home| prism_path(home));
    let instance_cfg = format!(
        "[General]\nConfigVersion=1.3\nInstanceType=OneSix\niconKey=default\nname={instance_name}\nManagedPack=false\nOverrideJavaLocation=true\nJavaPath={java_path}\nJavaArchitecture=64\nJavaRealArchitecture=amd64\nJavaTimestamp=0\nJavaVersion={java_version}\nJavaHome={java_home}\n\n",
        java_version = plan.java.major_version
    );
    let mmc_pack = write_mmc_pack_json(plan);

    std::fs::write(instance_dir.join("instance.cfg"), instance_cfg)
        .wrap_err_with(|| format!("Failed to write instance.cfg in {}", instance_dir.display()))?;
    std::fs::write(instance_dir.join("mmc-pack.json"), mmc_pack).wrap_err_with(|| {
        format!(
            "Failed to write mmc-pack.json in {}",
            instance_dir.display()
        )
    })?;

    Ok(())
}

fn write_mmc_pack_json(plan: &PrismInstancePlan) -> String {
    let mut components = Vec::new();
    components.push(format!(
        r#"        {{
            "cachedName": "Minecraft",
            "cachedVersion": "{mc_version}",
            "important": true,
            "uid": "net.minecraft",
            "version": "{mc_version}"
        }}"#,
        mc_version = plan.minecraft_version
    ));

    if let Some(lwjgl) = &plan.lwjgl {
        components.push(format_prism_component(lwjgl));
    }

    components.push(format!(
        r#"        {{
            "cachedName": "{loader_name}",
            "cachedRequires": [
                {{
                    "equals": "{mc_version}",
                    "uid": "net.minecraft"
                }}
            ],
            "cachedVersion": "{loader_version}",
            "uid": "{loader_uid}",
            "version": "{loader_version}"
        }}"#,
        mc_version = plan.minecraft_version,
        loader_name = plan.loader.cached_name,
        loader_uid = plan.loader.uid,
        loader_version = plan.loader.version
    ));

    format!(
        r#"{{
    "components": [
{components}
    ],
    "formatVersion": 1
}}
"#,
        components = components.join(",\n")
    )
}

fn format_prism_component(component: &PrismComponent) -> String {
    let dependency_only = if component.dependency_only {
        "\n            \"dependencyOnly\": true,"
    } else {
        ""
    };
    format!(
        r#"        {{{dependency_only}
            "cachedName": "{cached_name}",
            "cachedVersion": "{version}",
            "uid": "{uid}",
            "version": "{version}"
        }}"#,
        cached_name = component.cached_name,
        uid = component.uid,
        version = component.version
    )
}

fn javaw_path(plan: &PrismInstancePlan) -> PathBuf {
    plan.java.home.as_ref().map_or_else(
        || plan.java.executable.clone(),
        |home| {
            home.join("bin")
                .join(if cfg!(windows) { "javaw.exe" } else { "java" })
        },
    )
}

fn prism_path(path: &Path) -> String {
    path.to_string_lossy().replace('\\', "/")
}

pub(super) fn get_launcher_path() -> eyre::Result<PathBuf> {
    let launcher_file = APP_HOME.file_path(CLIENT_LAUNCHER_FILE);
    if !launcher_file.exists() {
        eyre::bail!(
            "Client launcher not set. Use `sfm-propagate-changes client set-launcher <path>` first."
        );
    }

    let content = std::fs::read_to_string(&launcher_file)
        .wrap_err_with(|| format!("Failed to read launcher file: {}", launcher_file.display()))?;
    let path = PathBuf::from(content.trim());

    if !path.exists() {
        eyre::bail!(
            "Configured client launcher does not exist: {}. Use `sfm-propagate-changes client set-launcher <path>` to update it.",
            path.display()
        );
    }

    Ok(path)
}

pub(super) fn get_instances_dir_path() -> eyre::Result<PathBuf> {
    let instances_dir_file = APP_HOME.file_path(CLIENT_INSTANCES_DIR_FILE);
    if !instances_dir_file.exists() {
        eyre::bail!(
            "Client instances directory not set. Use `sfm-propagate-changes client set-instances-dir <path>` first."
        );
    }

    let content = std::fs::read_to_string(&instances_dir_file).wrap_err_with(|| {
        format!(
            "Failed to read instances directory file: {}",
            instances_dir_file.display()
        )
    })?;
    let path = PathBuf::from(content.trim());

    if !path.exists() {
        eyre::bail!(
            "Configured client instances directory does not exist: {}. Use `sfm-propagate-changes client set-instances-dir <path>` to update it.",
            path.display()
        );
    }

    if !path.is_dir() {
        eyre::bail!(
            "Configured client instances path is not a directory: {}",
            path.display()
        );
    }

    Ok(path)
}

fn apply_branch_filter(
    targets: &mut Vec<ClientTarget>,
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

fn load_targets(file_name: &str) -> eyre::Result<Vec<ClientTarget>> {
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
        out.push(ClientTarget {
            path: PathBuf::from(path_text),
            mc_version: mc_version.to_string(),
        });
    }

    Ok(out)
}

fn save_targets(file_name: &str, targets: &[ClientTarget]) -> eyre::Result<()> {
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
