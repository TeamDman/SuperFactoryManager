use super::catalog_release_artifact_receipt::CatalogReleaseArtifactReceipt;
use super::catalog_release_artifact_receipt::MAX_RECEIPT_BYTES;
use crate::cancellation::CancellationToken;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::core_input_leases::DirectoryLeases;
use eyre::Result;
use eyre::ensure;
use std::fs::File;
use std::fs::OpenOptions;
use std::io::Write as _;
use std::os::windows::fs::OpenOptionsExt as _;
use std::path::Path;
use windows::Win32::Storage::FileSystem::FILE_FLAG_OPEN_REPARSE_POINT;

/// A create-only receipt leaf whose Windows handle denies readers and writers
/// until close. Existing ancestor leases stay held throughout publication.
///
/// The name is visible while pending. Failure can retain partial or complete
/// bytes; independent release admission must verify the full record and JAR.
/// See [exclusive sharing](https://doc.rust-lang.org/std/os/windows/fs/trait.OpenOptionsExt.html#tymethod.share_mode)
/// and [atomic creation](https://doc.rust-lang.org/std/fs/struct.OpenOptions.html#method.create_new).
pub(super) struct PendingCatalogReleaseReceipt {
    file: File,
    _parents: DirectoryLeases,
}

impl PendingCatalogReleaseReceipt {
    pub(super) fn new(root: &Path, jar_relative: &str) -> Result<Self> {
        CatalogReleaseArtifactReceipt::validate_relative(jar_relative)?;
        let root = checked_directory(root)?;
        let mut parents = DirectoryLeases::default();
        let destination = parents.prepare(
            &root,
            &CatalogReleaseArtifactReceipt::sidecar_relative(jar_relative),
        )?;
        let file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .share_mode(0)
            .custom_flags(FILE_FLAG_OPEN_REPARSE_POINT.0)
            .open(destination)?;
        Ok(Self {
            file,
            _parents: parents,
        })
    }

    pub(super) fn finish(mut self, bytes: &[u8], cancellation: &CancellationToken) -> Result<()> {
        ensure!(
            u64::try_from(bytes.len())? <= MAX_RECEIPT_BYTES,
            "release receipt exceeds byte limit"
        );
        cancellation.bail_if_cancelled()?;
        self.file.write_all(bytes)?;
        self.file.sync_all()?;
        cancellation.bail_if_cancelled()?;
        // Closing grants readers access only after the complete record has been
        // flushed. A cancelled/error exit can also expose complete correct bytes;
        // the record remains a local observation, never a publication permit.
        drop(self.file);
        Ok(())
    }

    #[cfg(test)]
    pub(super) fn write_partial_for_test(&mut self, bytes: &[u8]) -> Result<()> {
        self.file.write_all(bytes)?;
        Ok(())
    }
}
