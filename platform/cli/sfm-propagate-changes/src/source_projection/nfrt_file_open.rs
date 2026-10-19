//! Platform-specific regular file opens for invocation-held NFRT inputs.
use eyre::Result;
use std::fs::File;
use std::fs::Metadata;
use std::fs::OpenOptions;
#[cfg(target_os = "linux")]
use std::os::unix::fs::OpenOptionsExt as _;
#[cfg(windows)]
use std::os::windows::fs::MetadataExt as _;
#[cfg(windows)]
use std::os::windows::fs::OpenOptionsExt as _;
use std::path::Path;

pub(super) fn open_read(path: &Path) -> Result<File> {
    let mut options = OpenOptions::new();
    options.read(true);
    #[cfg(windows)]
    options.share_mode(1).custom_flags(0x0020_0000);
    #[cfg(target_os = "linux")]
    options.custom_flags(0o400_000);
    Ok(options.open(path)?)
}
pub(super) fn open_new(path: &Path) -> Result<File> {
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(windows)]
    options.custom_flags(0x0020_0000);
    #[cfg(target_os = "linux")]
    options.custom_flags(0o400_000).mode(0o600);
    Ok(options.open(path)?)
}
pub(super) fn regular(metadata: &Metadata) -> bool {
    #[cfg(windows)]
    {
        metadata.is_file() && metadata.file_attributes() & 0x400 == 0
    }
    #[cfg(target_os = "linux")]
    {
        metadata.is_file()
    }
}
#[cfg(all(test, target_os = "linux"))]
mod tests {
    use super::*;
    #[test]
    fn linux_file_open_refuses_symlink_and_existing_output() -> Result<()> {
        let temp = tempfile::tempdir()?;
        std::fs::write(temp.path().join("file"), b"input")?;
        std::os::unix::fs::symlink("file", temp.path().join("link"))?;
        assert!(open_read(&temp.path().join("link")).is_err());
        assert!(open_new(&temp.path().join("file")).is_err());
        assert!(regular(&open_read(&temp.path().join("file"))?.metadata()?));
        Ok(())
    }
}
