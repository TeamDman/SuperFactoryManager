use crate::cli::client::load_client_targets;
use crate::cli::server::load_server_targets;
use crate::paths::APP_HOME;
use crate::worktree::get_sorted_worktrees;
use eyre::Context;
use facet::Facet;
use figue as args;
use std::ffi::OsStr;
use std::path::Path;
use std::path::PathBuf;
use tracing::info;
use tracing::warn;

const JAR_DIR_FILE: &str = "jar_dir.txt";
const LEGACY_JARS_DIR_FILE: &str = "jars_dir.txt";

/// Jar directory and release artifact related commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum JarCommand {
    /// Jar directory related commands
    Dir {
        /// Directory subcommand
        #[facet(args::subcommand)]
        command: JarDirCommand,
    },
    /// Remove jars from the configured jar directory while keeping the directory
    Clean,
    /// Collect jars from each MC version based on that version's `mod_version`
    Collect,
    /// List jars in the configured jar directory
    List,
    /// Remove old SFM jar(s) and copy tracked-version jar to each tracked client mods folder
    #[facet(rename = "update-clients")]
    UpdateClients,
    /// Remove old SFM jar(s) and copy tracked-version jar to each tracked server mods folder
    #[facet(rename = "update-servers")]
    UpdateServers,
}

/// Jar directory commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum JarDirCommand {
    /// Set the jar directory path
    Set {
        /// The path to the jar directory
        #[facet(args::positional)]
        path: PathBuf,
    },
    /// Show the current jar directory path
    Show,
    /// Open the jar directory in the file explorer
    Open,
}

impl JarCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            JarCommand::Dir { command } => command.invoke(),
            JarCommand::Clean => clean_jars(),
            JarCommand::Collect => collect_jars(),
            JarCommand::List => list_jars(),
            JarCommand::UpdateClients => update_clients(),
            JarCommand::UpdateServers => update_servers(),
        }
    }
}

impl JarDirCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            JarDirCommand::Set { path } => {
                let canonical = dunce::canonicalize(&path)
                    .or_else(|_| {
                        std::fs::create_dir_all(&path)?;
                        dunce::canonicalize(&path)
                    })
                    .wrap_err_with(|| {
                        format!("Failed to create/canonicalize jar dir: {}", path.display())
                    })?;

                APP_HOME.ensure_dir()?;
                let jar_dir_file = APP_HOME.file_path(JAR_DIR_FILE);
                std::fs::write(&jar_dir_file, canonical.display().to_string())
                    .wrap_err("Failed to write jar dir file")?;

                info!("Set jar directory to: {}", canonical.display());
                Ok(())
            }
            JarDirCommand::Show => {
                let path = get_jar_dir()?;
                println!("{}", path.display());
                Ok(())
            }
            JarDirCommand::Open => {
                let path = get_jar_dir()?;
                if !path.exists() {
                    std::fs::create_dir_all(&path)?;
                }
                open::that(&path)?;
                Ok(())
            }
        }
    }
}

/// Get the configured jar directory path.
///
/// # Errors
///
/// This function will return an error if jar directory has not been set or if the file cannot be read.
pub fn get_jar_dir() -> eyre::Result<PathBuf> {
    let jar_dir_file = APP_HOME.file_path(JAR_DIR_FILE);
    let legacy_jar_dir_file = APP_HOME.file_path(LEGACY_JARS_DIR_FILE);

    let source_file = if jar_dir_file.exists() {
        jar_dir_file
    } else if legacy_jar_dir_file.exists() {
        legacy_jar_dir_file
    } else {
        eyre::bail!("Jar dir not set. Use `sfm-propagate-changes jar dir set <path>` to set it.");
    };

    let content = std::fs::read_to_string(&source_file).wrap_err("Failed to read jar dir file")?;
    let path = PathBuf::from(content.trim());

    Ok(path)
}

fn list_jar_files_sorted(path: &Path) -> eyre::Result<Vec<PathBuf>> {
    if !path.exists() {
        return Ok(Vec::new());
    }

    let mut jars: Vec<PathBuf> = std::fs::read_dir(path)?
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|entry_path| {
            entry_path.is_file()
                && entry_path
                    .extension()
                    .is_some_and(|extension| extension == OsStr::new("jar"))
        })
        .collect();

    jars.sort_by(|a, b| {
        let a_name = a.file_name().and_then(OsStr::to_str).unwrap_or_default();
        let b_name = b.file_name().and_then(OsStr::to_str).unwrap_or_default();
        a_name.cmp(b_name)
    });

    Ok(jars)
}

fn clean_jars() -> eyre::Result<()> {
    let jar_dir = get_jar_dir()?;
    std::fs::create_dir_all(&jar_dir)?;

    let jars = list_jar_files_sorted(&jar_dir)?;

    for jar in &jars {
        std::fs::remove_file(jar)
            .wrap_err_with(|| format!("Failed to remove jar: {}", jar.display()))?;
    }

    info!("Removed {} jar(s) from {}", jars.len(), jar_dir.display());
    Ok(())
}

fn list_jars() -> eyre::Result<()> {
    let jar_dir = get_jar_dir()?;
    let jars = list_jar_files_sorted(&jar_dir)?;

    if jars.is_empty() {
        println!("No jars found in {}", jar_dir.display());
        return Ok(());
    }

    for jar in jars {
        if let Some(name) = jar.file_name().and_then(OsStr::to_str) {
            println!("{name}");
        } else {
            println!("{}", jar.display());
        }
    }

    Ok(())
}

fn collect_jars() -> eyre::Result<()> {
    let jar_dir = get_jar_dir()?;
    std::fs::create_dir_all(&jar_dir)?;

    let worktrees = get_sorted_worktrees()?;

    if worktrees.is_empty() {
        println!("No worktrees found.");
        return Ok(());
    }

    let mut copied = 0usize;
    let mut skipped = 0usize;

    for wt in worktrees {
        let gradle_properties = wt
            .path
            .join("platform")
            .join("minecraft")
            .join("gradle.properties");
        let libs_dir = wt
            .path
            .join("platform")
            .join("minecraft")
            .join("build")
            .join("libs");

        if !gradle_properties.exists() {
            warn!(
                branch = %wt.branch,
                path = %gradle_properties.display(),
                "Skipping worktree: missing gradle.properties"
            );
            skipped += 1;
            continue;
        }

        if !libs_dir.exists() {
            warn!(
                branch = %wt.branch,
                path = %libs_dir.display(),
                "Skipping worktree: missing build/libs"
            );
            skipped += 1;
            continue;
        }

        let mod_version = read_mod_version(&gradle_properties).wrap_err_with(|| {
            format!(
                "Failed to read mod_version for branch {} from {}",
                wt.branch,
                gradle_properties.display()
            )
        })?;

        let Some(jar) = pick_jar_for_mod_version(&libs_dir, &mod_version)? else {
            warn!(
                branch = %wt.branch,
                mod_version = %mod_version,
                path = %libs_dir.display(),
                "Skipping worktree: no matching jar for mod_version"
            );
            skipped += 1;
            continue;
        };

        let destination = jar_dir.join(
            jar.file_name()
                .ok_or_else(|| eyre::eyre!("Jar filename missing: {}", jar.display()))?,
        );

        std::fs::copy(&jar, &destination).wrap_err_with(|| {
            format!(
                "Failed to copy jar from {} to {}",
                jar.display(),
                destination.display()
            )
        })?;

        info!(
            branch = %wt.branch,
            mod_version = %mod_version,
            source = %jar.display(),
            dest = %destination.display(),
            "Collected jar"
        );
        copied += 1;
    }

    println!(
        "Collected {copied} jar(s) into {} (skipped {skipped}).",
        jar_dir.display()
    );

    Ok(())
}

fn update_clients() -> eyre::Result<()> {
    let targets = load_client_targets()?;
    if targets.is_empty() {
        println!("No tracked clients. Use `sfm-propagate-changes client add <glob>`.");
        return Ok(());
    }

    let mut updated = 0usize;
    let mut skipped = 0usize;

    for target in targets {
        let Some(jar) = find_best_jar_for_mc_version(&target.mc_version)? else {
            warn!(
                path = %target.path.display(),
                mc_version = %target.mc_version,
                "Skipping client target: no matching jar found"
            );
            skipped += 1;
            continue;
        };

        let mods_dir = target.path.join(".minecraft").join("mods");
        update_mods_folder_with_jar(&mods_dir, &jar)?;
        updated += 1;
    }

    println!("Updated {updated} client target(s), skipped {skipped}.");
    Ok(())
}

fn update_servers() -> eyre::Result<()> {
    let targets = load_server_targets()?;
    if targets.is_empty() {
        println!("No tracked servers. Use `sfm-propagate-changes server add <glob>`.");
        return Ok(());
    }

    let mut updated = 0usize;
    let mut skipped = 0usize;

    for target in targets {
        let Some(jar) = find_best_jar_for_mc_version(&target.mc_version)? else {
            warn!(
                path = %target.path.display(),
                mc_version = %target.mc_version,
                "Skipping server target: no matching jar found"
            );
            skipped += 1;
            continue;
        };

        let mods_dir = target.path.join("mods");
        update_mods_folder_with_jar(&mods_dir, &jar)?;
        updated += 1;
    }

    println!("Updated {updated} server target(s), skipped {skipped}.");
    Ok(())
}

fn update_mods_folder_with_jar(mods_dir: &Path, jar: &Path) -> eyre::Result<()> {
    std::fs::create_dir_all(mods_dir)?;

    for existing in std::fs::read_dir(mods_dir)?
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| {
            path.is_file()
                && path
                    .file_name()
                    .and_then(OsStr::to_str)
                    .is_some_and(|name| name.contains("Super Factory Manager"))
                && path
                    .extension()
                    .is_some_and(|extension| extension == OsStr::new("jar"))
        })
    {
        std::fs::remove_file(&existing)
            .wrap_err_with(|| format!("Failed to remove old jar: {}", existing.display()))?;
    }

    let destination = mods_dir.join(
        jar.file_name()
            .ok_or_else(|| eyre::eyre!("Jar filename missing: {}", jar.display()))?,
    );

    std::fs::copy(jar, &destination).wrap_err_with(|| {
        format!(
            "Failed to copy jar from {} to {}",
            jar.display(),
            destination.display()
        )
    })?;

    info!(
        source = %jar.display(),
        destination = %destination.display(),
        "Updated mods folder with jar"
    );

    Ok(())
}

fn find_best_jar_for_mc_version(mc_version: &str) -> eyre::Result<Option<PathBuf>> {
    let jar_dir = get_jar_dir()?;
    if !jar_dir.exists() {
        return Ok(None);
    }

    let needle = format!("-MC{mc_version}-");

    let mut matches: Vec<PathBuf> = std::fs::read_dir(&jar_dir)?
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
                    .is_some_and(|name| name.contains(&needle))
        })
        .collect();

    matches.sort_by(|a, b| {
        let a_name = a.file_name().and_then(OsStr::to_str).unwrap_or_default();
        let b_name = b.file_name().and_then(OsStr::to_str).unwrap_or_default();
        a_name.cmp(b_name)
    });

    Ok(matches.into_iter().last())
}

fn read_mod_version(gradle_properties: &Path) -> eyre::Result<String> {
    let content = std::fs::read_to_string(gradle_properties)
        .wrap_err("Failed to read gradle.properties for mod_version")?;

    let mod_version = content
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .filter(|line| !line.starts_with('#'))
        .find_map(|line| line.strip_prefix("mod_version=").map(str::trim))
        .ok_or_else(|| eyre::eyre!("mod_version not found"))?;

    if mod_version.is_empty() {
        eyre::bail!("mod_version was empty");
    }

    Ok(mod_version.to_string())
}

fn pick_jar_for_mod_version(libs_dir: &Path, mod_version: &str) -> eyre::Result<Option<PathBuf>> {
    let suffix = format!("-{mod_version}.jar");

    let mut candidates: Vec<PathBuf> = std::fs::read_dir(libs_dir)?
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
        .collect();

    candidates.sort_by(|a, b| {
        let a_name = a.file_name().and_then(OsStr::to_str).unwrap_or_default();
        let b_name = b.file_name().and_then(OsStr::to_str).unwrap_or_default();
        a_name.cmp(b_name)
    });

    Ok(candidates.into_iter().next())
}
