//! Unix invocation ancestry holds. Paths are no-follow and identities are rechecked.
//! Unix cannot deny rename by another writer; replacement is detected before reuse.
use super::promotion::validate_relative_path;
use crate::file_identity::FileIdentity;
use crate::file_identity::file_identity;
use eyre::Result;
use eyre::ensure;
use std::collections::HashMap;
use std::fs::File;
use std::fs::OpenOptions;
use std::os::unix::fs::OpenOptionsExt as _;
use std::path::Path;
use std::path::PathBuf;

#[derive(Default)]
pub(super) struct DirectoryLeases {
    directories: HashMap<PathBuf, (File, FileIdentity)>,
}
impl DirectoryLeases {
    pub(super) fn prepare(&mut self, root: &Path, relative: &str) -> Result<PathBuf> {
        ensure!(root.is_absolute(), "core input lease root must be absolute");
        validate_relative_path(relative)?;
        for directory in root.ancestors().collect::<Vec<_>>().into_iter().rev() {
            self.hold(directory)?;
        }
        let mut path = root.to_owned();
        let mut parts = relative.split('/').peekable();
        while let Some(part) = parts.next() {
            path.push(part);
            if parts.peek().is_some() {
                self.hold(&path)?;
            }
        }
        // Recheck all held path spellings after traversal, including reused ancestors.
        for (path, (file, identity)) in &self.directories {
            let current = open_directory(path)?;
            ensure!(
                file_identity(&current)? == *identity && file_identity(file)? == *identity,
                "held Unix ancestry was replaced"
            );
        }
        Ok(path)
    }
    fn hold(&mut self, path: &Path) -> Result<()> {
        let current = open_directory(path)?;
        let identity = file_identity(&current)?;
        if let Some((held, previous)) = self.directories.get(path) {
            ensure!(
                *previous == identity && file_identity(held)? == identity,
                "held Unix directory was replaced"
            );
        } else {
            ensure!(
                self.directories.len() < 16_384,
                "directory lease limit exceeded"
            );
            self.directories
                .insert(path.to_owned(), (current, identity));
        }
        Ok(())
    }
}
fn open_directory(path: &Path) -> Result<File> {
    // Linux O_NOFOLLOW | O_DIRECTORY. Rust sets close-on-exec on the retained FD.
    let file = OpenOptions::new()
        .read(true)
        .custom_flags(0o400_000 | 0o200_000)
        .open(path)?;
    ensure!(
        file.metadata()?.is_dir(),
        "ancestry input is not a directory"
    );
    Ok(file)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn unix_ancestry_refuses_symlinks_and_detects_replacement() -> Result<()> {
        let temp = tempfile::tempdir()?;
        std::fs::create_dir(temp.path().join("original"))?;
        std::os::unix::fs::symlink("original", temp.path().join("link"))?;
        let mut held = DirectoryLeases::default();
        assert!(held.prepare(temp.path(), "link/file").is_err());
        held.prepare(temp.path(), "original/file")?;
        std::fs::rename(temp.path().join("original"), temp.path().join("moved"))?;
        std::fs::create_dir(temp.path().join("original"))?;
        assert!(held.prepare(temp.path(), "original/file").is_err());
        Ok(())
    }
}
