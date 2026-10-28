use crate::paths::{
    APP_CACHE_ENV_VAR, APP_HOME, CACHE_ROOT_FILE, CACHE_ROOT_MARKER, CACHE_ROOT_MARKER_CONTENT,
    CacheHome,
};
use crate::terminal_output::stdout_line;
use eyre::{Context, bail, ensure};
use facet::Facet;
use figue as args;
use std::io::Write;
use std::path::{Path, PathBuf};

#[derive(Facet, Debug)]
pub struct CacheRootArgs {
    #[facet(args::subcommand)]
    pub command: CacheRootCommand,
}

impl CacheRootArgs {
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CacheRootCommand {
    /// Show the effective shared cache root and where it came from
    Show(CacheRootShowArgs),
    /// Persist an existing empty or SFM-owned directory as the shared cache root
    Set(CacheRootSetArgs),
    /// Remove the persisted choice; retain all cached files
    Unset(CacheRootUnsetArgs),
}

impl CacheRootCommand {
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Show(args) => args.invoke(),
            Self::Set(args) => args.invoke(),
            Self::Unset(args) => args.invoke(),
        }
    }
}

#[derive(Facet, Debug)]
pub struct CacheRootShowArgs;

impl CacheRootShowArgs {
    pub fn invoke(self) -> eyre::Result<()> {
        let source = if std::env::var_os(APP_CACHE_ENV_VAR).is_some() {
            APP_CACHE_ENV_VAR
        } else if APP_HOME.file_path(CACHE_ROOT_FILE).exists() {
            "persistent setting"
        } else {
            "platform user cache default"
        };
        stdout_line(format!("{} ({source})", CacheHome::resolve()?.0.display()))
    }
}

#[derive(Facet, Debug)]
pub struct CacheRootSetArgs {
    /// Existing cache root, preferably on a spacious local volume
    #[facet(args::positional)]
    pub path: PathBuf,
}

impl CacheRootSetArgs {
    pub fn invoke(self) -> eyre::Result<()> {
        let requested_metadata = std::fs::symlink_metadata(&self.path)?;
        ensure!(
            !requested_metadata.file_type().is_symlink(),
            "Cache root must not be a link: {}",
            self.path.display()
        );
        #[cfg(windows)]
        {
            use std::os::windows::fs::MetadataExt;
            ensure!(
                requested_metadata.file_attributes() & 0x400 == 0,
                "Cache root must not be a reparse point: {}",
                self.path.display()
            );
        }
        let root = dunce::canonicalize(&self.path)
            .wrap_err_with(|| format!("Cache root must exist: {}", self.path.display()))?;
        validate_cache_root(&root)?;
        let marker = root.join(CACHE_ROOT_MARKER);
        if !marker.exists() {
            let mut marker_file = std::fs::OpenOptions::new()
                .write(true)
                .create_new(true)
                .open(&marker)
                .wrap_err_with(|| format!("Cannot mark cache root: {}", root.display()))?;
            marker_file.write_all(CACHE_ROOT_MARKER_CONTENT.as_bytes())?;
            marker_file.sync_all()?;
        }
        APP_HOME.ensure_dir()?;
        let config = APP_HOME.file_path(CACHE_ROOT_FILE);
        let mut staged = tempfile::NamedTempFile::new_in(&APP_HOME.0)?;
        writeln!(staged, "{}", root.display())?;
        staged.as_file_mut().sync_all()?;
        staged
            .persist(&config)
            .map_err(|error| error.error)
            .wrap_err_with(|| format!("Cannot save cache root setting: {}", config.display()))?;
        stdout_line(format!(
            "{} (persistent setting; cached files retained)",
            root.display()
        ))
    }
}

#[derive(Facet, Debug)]
pub struct CacheRootUnsetArgs;

impl CacheRootUnsetArgs {
    pub fn invoke(self) -> eyre::Result<()> {
        let config = APP_HOME.file_path(CACHE_ROOT_FILE);
        if config.exists() {
            std::fs::remove_file(&config)?;
        }
        stdout_line("Persistent cache root unset; cached files retained")
    }
}

fn validate_cache_root(root: &Path) -> eyre::Result<()> {
    ensure!(
        root.is_absolute() && root.is_dir() && root.parent().is_some(),
        "Cache root must be an existing directory below a volume root: {}",
        root.display()
    );
    let metadata = std::fs::symlink_metadata(root)?;
    ensure!(
        !metadata.file_type().is_symlink(),
        "Cache root must not be a link: {}",
        root.display()
    );
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        ensure!(
            metadata.file_attributes() & 0x400 == 0,
            "Cache root must not be a reparse point: {}",
            root.display()
        );
    }
    let marker = root.join(CACHE_ROOT_MARKER);
    if marker.exists() {
        ensure!(
            std::fs::read_to_string(&marker)? == CACHE_ROOT_MARKER_CONTENT,
            "Cache root has an unrecognized ownership marker: {}",
            marker.display()
        );
    } else if std::fs::read_dir(root)?.next().is_some() {
        bail!(
            "Cache root is nonempty and has no SFM ownership marker: {}",
            root.display()
        );
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_root_is_allowed_and_nonempty_unowned_root_is_refused() {
        let directory = tempfile::tempdir().unwrap();
        validate_cache_root(directory.path()).unwrap();
        std::fs::write(directory.path().join("user.txt"), "preserve").unwrap();
        assert!(validate_cache_root(directory.path()).is_err());
    }

    #[test]
    fn owned_root_is_allowed_but_unknown_marker_is_refused() {
        let directory = tempfile::tempdir().unwrap();
        let marker = directory.path().join(CACHE_ROOT_MARKER);
        std::fs::write(&marker, CACHE_ROOT_MARKER_CONTENT).unwrap();
        std::fs::write(directory.path().join("minecraft-toolchain"), "cached").unwrap();
        validate_cache_root(directory.path()).unwrap();
        std::fs::write(&marker, "unrelated").unwrap();
        assert!(validate_cache_root(directory.path()).is_err());
    }
}
