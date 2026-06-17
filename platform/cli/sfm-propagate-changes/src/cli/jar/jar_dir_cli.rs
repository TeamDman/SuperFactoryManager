use super::JarDirCleanArgs;
use super::JarDirOpenArgs;
use super::JarDirSetArgs;
use super::JarDirShowArgs;
use crate::paths::APP_HOME;
use eyre::Context;
use facet::Facet;
use figue as args;
use std::ffi::OsStr;
use std::path::Path;
use std::path::PathBuf;
use tracing::info;

const JAR_DIR_FILE: &str = "jar_dir.txt";
const LEGACY_JARS_DIR_FILE: &str = "jars_dir.txt";

/// Arguments for jar directory commands.
#[derive(Facet, Debug)]
pub struct JarDirArgs {
    /// Directory subcommand.
    #[facet(args::subcommand)]
    pub command: JarDirCommand,
}

impl JarDirArgs {
    /// # Errors
    ///
    /// Returns an error if the selected jar directory command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// Jar directory commands.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum JarDirCommand {
    /// Set the jar directory path.
    Set(JarDirSetArgs),
    /// Remove jars from the configured jar directory while keeping the directory.
    Clean(JarDirCleanArgs),
    /// Show the current jar directory path.
    Show(JarDirShowArgs),
    /// Open the jar directory in the file explorer.
    Open(JarDirOpenArgs),
}

impl JarDirCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            JarDirCommand::Set(args) => args.invoke(),
            JarDirCommand::Clean(args) => args.invoke(),
            JarDirCommand::Show(args) => args.invoke(),
            JarDirCommand::Open(args) => args.invoke(),
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

pub(super) fn list_jar_files_sorted(path: &Path) -> eyre::Result<Vec<PathBuf>> {
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

pub(super) fn set_jar_dir(path: &Path) -> eyre::Result<()> {
    let canonical = dunce::canonicalize(path)
        .or_else(|_| {
            std::fs::create_dir_all(path)?;
            dunce::canonicalize(path)
        })
        .wrap_err_with(|| format!("Failed to create/canonicalize jar dir: {}", path.display()))?;

    APP_HOME.ensure_dir()?;
    let jar_dir_file = APP_HOME.file_path(JAR_DIR_FILE);
    std::fs::write(&jar_dir_file, canonical.display().to_string())
        .wrap_err("Failed to write jar dir file")?;

    info!("Set jar directory to: {}", canonical.display());
    Ok(())
}

pub(super) fn clean_jars() -> eyre::Result<()> {
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
