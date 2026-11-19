use crate::cli::InvocationHomes;
use crate::paths::AppHome;
use crate::paths::CACHE_ROOT_FILE;
use crate::paths::CACHE_ROOT_MARKER;
use crate::paths::CACHE_ROOT_MARKER_CONTENT;
use crate::paths::InvocationDirectory;
use crate::paths::SelectedCacheHome;
use crate::terminal_output::stdout_line;
use eyre::Context;
use eyre::bail;
use eyre::ensure;
use facet::Facet;
use figue as args;
use std::fs::OpenOptions;
use std::fs::read_dir;
use std::fs::read_to_string;
use std::fs::remove_file;
use std::fs::symlink_metadata;
use std::io::Write;
#[cfg(windows)]
use std::os::windows::fs::MetadataExt;
use std::path::Path;
use std::path::PathBuf;

#[derive(Facet, Debug)]
pub struct CacheRootArgs {
    #[facet(args::subcommand)]
    pub command: CacheRootCommand,
}

impl CacheRootArgs {
    /// # Errors
    /// Returns an error if the selected cache-root command fails.
    pub fn invoke_in(
        self,
        invocation_dir: &InvocationDirectory<'_>,
        homes: &InvocationHomes,
    ) -> eyre::Result<()> {
        self.command.invoke_in(invocation_dir, homes)
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
    /// # Errors
    /// Returns an error if the selected cache-root command fails.
    pub fn invoke_in(
        self,
        invocation_dir: &InvocationDirectory<'_>,
        homes: &InvocationHomes,
    ) -> eyre::Result<()> {
        match self {
            Self::Show(args) => args.invoke(homes.selected_cache_home()?),
            Self::Set(args) => args.invoke_in(invocation_dir, homes.app_home()),
            Self::Unset(args) => args.invoke(homes.app_home()),
        }
    }
}

#[derive(Facet, Debug)]
pub struct CacheRootShowArgs;

impl CacheRootShowArgs {
    /// # Errors
    /// Returns an error if the selected cache root cannot be displayed.
    pub fn invoke(self, selection: &SelectedCacheHome) -> eyre::Result<()> {
        stdout_line(format!(
            "{} ({})",
            selection.home.0.display(),
            selection.source.label()
        ))
    }
}

#[derive(Facet, Debug)]
pub struct CacheRootSetArgs {
    /// Existing cache root, preferably on a spacious local volume
    #[facet(args::positional)]
    pub path: PathBuf,
}

impl CacheRootSetArgs {
    /// # Errors
    /// Returns an error if the requested root fails validation or cannot be persisted.
    pub fn invoke_in(
        self,
        invocation_dir: &InvocationDirectory<'_>,
        app_home: &AppHome,
    ) -> eyre::Result<()> {
        let requested = invocation_dir.resolve(&self.path)?;
        let requested_metadata = symlink_metadata(&requested)?;
        ensure!(
            !requested_metadata.file_type().is_symlink(),
            "Cache root must not be a link: {}",
            requested.display()
        );
        #[cfg(windows)]
        ensure!(
            requested_metadata.file_attributes() & 0x400 == 0,
            "Cache root must not be a reparse point: {}",
            requested.display()
        );
        let root = dunce::canonicalize(&requested)
            .wrap_err_with(|| format!("Cache root must exist: {}", requested.display()))?;
        validate_cache_root(&root)?;
        let marker = root.join(CACHE_ROOT_MARKER);
        if !marker.exists() {
            let mut marker_file = OpenOptions::new()
                .write(true)
                .create_new(true)
                .open(&marker)
                .wrap_err_with(|| format!("Cannot mark cache root: {}", root.display()))?;
            marker_file.write_all(CACHE_ROOT_MARKER_CONTENT.as_bytes())?;
            marker_file.sync_all()?;
        }
        app_home.ensure_dir()?;
        let config = app_home.file_path(CACHE_ROOT_FILE);
        let mut staged = tempfile::NamedTempFile::new_in(&app_home.0)?;
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
    /// # Errors
    /// Returns an error if the persisted setting cannot be removed or reported.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        let config = app_home.file_path(CACHE_ROOT_FILE);
        if config.exists() {
            remove_file(&config)?;
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
    let metadata = symlink_metadata(root)?;
    ensure!(
        !metadata.file_type().is_symlink(),
        "Cache root must not be a link: {}",
        root.display()
    );
    #[cfg(windows)]
    ensure!(
        metadata.file_attributes() & 0x400 == 0,
        "Cache root must not be a reparse point: {}",
        root.display()
    );
    let marker = root.join(CACHE_ROOT_MARKER);
    if marker.exists() {
        ensure!(
            read_to_string(&marker)? == CACHE_ROOT_MARKER_CONTENT,
            "Cache root has an unrecognized ownership marker: {}",
            marker.display()
        );
    } else if read_dir(root)?.next().is_some() {
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
    fn relative_cache_root_is_persisted_from_the_invocation_directory() -> eyre::Result<()> {
        let fixture = tempfile::tempdir()?;
        let invocation_dir = InvocationDirectory::new(fixture.path())?;
        let cache = fixture.path().join("cache");
        std::fs::create_dir(&cache)?;
        let app_home = AppHome(fixture.path().join("settings"));
        CacheRootSetArgs {
            path: PathBuf::from("cache"),
        }
        .invoke_in(&invocation_dir, &app_home)?;
        assert_eq!(
            read_to_string(cache.join(CACHE_ROOT_MARKER))?,
            CACHE_ROOT_MARKER_CONTENT
        );
        assert_eq!(
            PathBuf::from(read_to_string(app_home.file_path(CACHE_ROOT_FILE))?.trim()),
            dunce::canonicalize(cache)?
        );
        Ok(())
    }

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
