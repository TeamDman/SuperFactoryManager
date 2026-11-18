use crate::file_identity::FileIdentity;
use crate::file_identity::file_identity;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::candidate_lock::is_reparse;
#[cfg(windows)]
use crate::source_projection::core_input_leases::DirectoryLeases;
use eyre::Result;
use eyre::ensure;
use std::fmt::Debug;
use std::fmt::Formatter;
use std::fmt::Result as FmtResult;
use std::fs::File;
use std::fs::OpenOptions;
use std::io::Read as _;
#[cfg(windows)]
use std::os::windows::fs::OpenOptionsExt as _;
use std::path::Path;
use std::path::PathBuf;
#[cfg(windows)]
use windows::Win32::Storage::FileSystem::FILE_FLAG_OPEN_REPARSE_POINT;
#[cfg(windows)]
use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;

/// One retained bounded regular-file observation. Windows holds read-only file
/// sharing and parent leases; other platforms recheck content and object identity.
pub(super) struct CatalogReleaseArtifactFile {
    root: PathBuf,
    relative: String,
    held: File,
    identity: FileIdentity,
    bytes: Vec<u8>,
    limit: u64,
    #[cfg(windows)]
    _parents: DirectoryLeases,
}

impl Debug for CatalogReleaseArtifactFile {
    fn fmt(&self, f: &mut Formatter<'_>) -> FmtResult {
        f.debug_struct("CatalogReleaseArtifactFile")
            .field("relative", &self.relative)
            .field("identity", &self.identity)
            .field("bytes", &self.bytes.len())
            .finish_non_exhaustive()
    }
}

impl CatalogReleaseArtifactFile {
    pub(super) fn read(root: &Path, relative: &str, limit: u64) -> Result<Self> {
        let root = checked_directory(root)?;
        #[cfg(windows)]
        let mut parents = DirectoryLeases::default();
        #[cfg(windows)]
        parents.prepare(&root, relative)?;
        let path = checked_file(&root, relative)?;
        let mut held = Self::open(&path)?;
        let before = held.metadata()?;
        ensure!(
            before.is_file() && !is_reparse(&before),
            "release artifact is not a regular file"
        );
        ensure!(
            before.len() <= limit,
            "release artifact exceeds its byte budget"
        );
        let identity = file_identity(&held)?;
        let mut bytes = Vec::new();
        (&mut held).take(limit + 1).read_to_end(&mut bytes)?;
        let after = held.metadata()?;
        let reopened = Self::open(&checked_file(&root, relative)?)?;
        ensure!(
            u64::try_from(bytes.len())? == before.len()
                && after.len() == before.len()
                && after.modified()? == before.modified()?
                && file_identity(&reopened)? == identity
                && file_identity(&held)? == identity,
            "release artifact changed while being read"
        );
        Ok(Self {
            root,
            relative: relative.to_owned(),
            held,
            identity,
            bytes,
            limit,
            #[cfg(windows)]
            _parents: parents,
        })
    }

    fn open(path: &Path) -> Result<File> {
        let mut options = OpenOptions::new();
        options.read(true);
        #[cfg(windows)]
        options
            .share_mode(FILE_SHARE_READ.0)
            .custom_flags(FILE_FLAG_OPEN_REPARSE_POINT.0);
        Ok(options.open(path)?)
    }

    pub(super) fn bytes(&self) -> &[u8] {
        &self.bytes
    }

    pub(super) fn recheck(&self) -> Result<()> {
        let current = Self::read(&self.root, &self.relative, self.limit)?;
        ensure!(
            current.identity == self.identity
                && file_identity(&self.held)? == self.identity
                && current.bytes == self.bytes,
            "release artifact bytes or file identity changed"
        );
        Ok(())
    }
}
