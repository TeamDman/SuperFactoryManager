//! Invocation-local Windows ancestry leases, never persisted metadata.
//!
//! Holding read-shared directory handles prevents replacement, rename and
//! write-access reparse mutation until the snapshot is dropped. Children still
//! acquire fresh bytes through no-follow leaf opens. No timestamps are reused.

use super::promotion::validate_relative_path;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use std::collections::HashMap;
use std::os::windows::ffi::OsStrExt as _;
use std::path::Path;
use std::path::PathBuf;
use windows::Win32::Foundation::HANDLE;
use windows::Win32::Storage::FileSystem::CreateFileW;
use windows::Win32::Storage::FileSystem::FILE_ATTRIBUTE_DIRECTORY;
use windows::Win32::Storage::FileSystem::FILE_ATTRIBUTE_REPARSE_POINT;
use windows::Win32::Storage::FileSystem::FILE_ATTRIBUTE_TAG_INFO;
use windows::Win32::Storage::FileSystem::FILE_FLAG_BACKUP_SEMANTICS;
use windows::Win32::Storage::FileSystem::FILE_FLAG_OPEN_REPARSE_POINT;
use windows::Win32::Storage::FileSystem::FILE_LIST_DIRECTORY;
use windows::Win32::Storage::FileSystem::FILE_READ_ATTRIBUTES;
use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;
use windows::Win32::Storage::FileSystem::FileAttributeTagInfo;
use windows::Win32::Storage::FileSystem::GetFileInformationByHandleEx;
use windows::Win32::Storage::FileSystem::OPEN_EXISTING;
use windows::core::Owned;
use windows::core::PCWSTR;

const MAX_DIRECTORY_LEASES: usize = 16_384;

#[derive(Default)]
pub(super) struct DirectoryLeases {
    directories: HashMap<PathBuf, Owned<HANDLE>>,
}

impl DirectoryLeases {
    #[cfg_attr(
        feature = "tracy",
        tracing::instrument(level = "info", skip_all, name = "oracle_lease_input_ancestors")
    )]
    pub(super) fn prepare(&mut self, root: &Path, relative: &str) -> Result<PathBuf> {
        ensure!(root.is_absolute(), "core input lease root must be absolute");
        validate_relative_path(relative)?;
        // Acquire top-down: once a parent is held, the next component cannot
        // be redirected by renaming/replacing that parent during acquisition.
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
        Ok(path)
    }

    fn hold(&mut self, path: &Path) -> Result<()> {
        if let Some(handle) = self.directories.get(path) {
            // Sharing guards namespace replacement; still query the held
            // object's current attributes instead of treating metadata as fixed.
            return validate_directory(handle);
        }
        #[cfg(feature = "tracy")]
        let _span = tracing::info_span!("oracle_lease_new_directory").entered();
        ensure!(
            self.directories.len() < MAX_DIRECTORY_LEASES,
            "core input ancestry exceeds the directory lease limit"
        );
        let mut wide = path.as_os_str().encode_wide().collect::<Vec<_>>();
        ensure!(!wide.contains(&0), "core directory contains a NUL");
        if wide.len() >= 260 && !wide.starts_with(&[92, 92, 63, 92]) {
            for character in &mut wide {
                if *character == 47 {
                    *character = 92;
                }
            }
            if wide.starts_with(&[92, 92]) {
                wide.splice(0..2, [92, 92, 63, 92, 85, 78, 67, 92]);
            } else {
                ensure!(
                    wide.get(1) == Some(&58) && wide.get(2) == Some(&92),
                    "unsupported long absolute core directory"
                );
                wide.splice(0..0, [92, 92, 63, 92]);
            }
        }
        wide.push(0);
        // SAFETY: the UTF-16 buffer lives through this synchronous call.
        let handle = unsafe {
            CreateFileW(
                PCWSTR(wide.as_ptr()),
                // Attribute-only handles do not enforce share exclusions.
                // Directory-list access makes this a real read lease.
                FILE_READ_ATTRIBUTES.0 | FILE_LIST_DIRECTORY.0,
                FILE_SHARE_READ,
                None,
                OPEN_EXISTING,
                FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT,
                None,
            )
        }
        .wrap_err_with(|| format!("cannot lease core directory '{}'", path.display()))?;
        // SAFETY: CreateFileW succeeded; this uniquely owned handle must be
        // closed. Transfer ownership immediately without another fallible call.
        let handle = unsafe { Owned::new(handle) };
        validate_directory(&handle)?;
        self.directories.insert(path.to_owned(), handle);
        Ok(())
    }
}

#[cfg_attr(
    feature = "tracy",
    tracing::instrument(level = "info", skip_all, name = "oracle_validate_leased_ancestor")
)]
fn validate_directory(handle: &Owned<HANDLE>) -> Result<()> {
    let mut information = FILE_ATTRIBUTE_TAG_INFO::default();
    // SAFETY: information is the correctly sized writable result buffer
    // for FileAttributeTagInfo; the owned handle remains live throughout.
    unsafe {
        GetFileInformationByHandleEx(
            **handle,
            FileAttributeTagInfo,
            std::ptr::from_mut(&mut information).cast(),
            u32::try_from(std::mem::size_of_val(&information))?,
        )
    }
    .wrap_err("cannot validate leased core directory")?;
    ensure!(
        information.FileAttributes & FILE_ATTRIBUTE_DIRECTORY.0 != 0
            && information.FileAttributes & FILE_ATTRIBUTE_REPARSE_POINT.0 == 0,
        "core input ancestor is a reparse point or non-directory"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn held_ancestors_block_rename_and_release_on_drop_without_blocking_file_edits() {
        let fixture = tempfile::tempdir().unwrap();
        let root = fixture.path().join("root");
        let directory = root.join("nested");
        fs::create_dir_all(&directory).unwrap();
        let file = directory.join("source.java");
        fs::write(&file, b"before").unwrap();
        let mut leases = DirectoryLeases::default();
        assert_eq!(leases.prepare(&root, "nested/source.java").unwrap(), file);
        let count = leases.directories.len();
        leases.prepare(&root, "nested/other.java").unwrap();
        assert_eq!(leases.directories.len(), count);
        assert!(fs::rename(&directory, root.join("moved")).is_err());
        assert!(fs::rename(&root, fixture.path().join("moved-root")).is_err());
        let write_directory = || {
            use std::os::windows::fs::OpenOptionsExt as _;
            use windows::Win32::Storage::FileSystem::FILE_SHARE_DELETE;
            use windows::Win32::Storage::FileSystem::FILE_SHARE_WRITE;
            use windows::Win32::Storage::FileSystem::FILE_WRITE_DATA;
            fs::OpenOptions::new()
                .access_mode(FILE_WRITE_DATA.0)
                .share_mode((FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE).0)
                .custom_flags((FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT).0)
                .open(&directory)
        };
        assert!(write_directory().is_err());
        fs::write(&file, b"edited").unwrap();
        assert_eq!(fs::read(&file).unwrap(), b"edited");
        let new_file = directory.join("new.java");
        fs::write(&new_file, b"created during lease").unwrap();
        fs::remove_file(&new_file).unwrap();
        drop(leases);
        drop(write_directory().unwrap());
        fs::rename(&directory, root.join("moved")).unwrap();
        fs::rename(&root, fixture.path().join("moved-root")).unwrap();
    }

    #[test]
    fn ancestry_rejects_wrong_type_traversal_and_missing_directories() {
        let fixture = tempfile::tempdir().unwrap();
        fs::write(fixture.path().join("file"), b"data").unwrap();
        let mut leases = DirectoryLeases::default();
        assert!(leases.prepare(fixture.path(), "file/child").is_err());
        assert!(leases.prepare(fixture.path(), "../escape").is_err());
        assert!(leases.prepare(fixture.path(), "missing/child").is_err());
    }

    #[test]
    fn long_unicode_ancestry_remains_supported() {
        let fixture = tempfile::tempdir().unwrap();
        let mut root = fixture.path().to_owned();
        for index in 0..8 {
            root.push(format!("unicode-\u{03bb}-{index}-{}", "x".repeat(24)));
        }
        fs::create_dir_all(&root).unwrap();
        fs::write(root.join("source.java"), b"source").unwrap();
        let mut leases = DirectoryLeases::default();
        let file = leases.prepare(&root, "source.java").unwrap();
        assert_eq!(fs::read(file).unwrap(), b"source");
    }

    #[test]
    fn junction_ancestors_and_leaf_reparse_points_are_rejected() {
        use std::os::windows::process::CommandExt as _;
        use windows::Win32::System::Threading::CREATE_NO_WINDOW;
        let fixture = tempfile::tempdir().unwrap();
        let root = fixture.path().join(super::super::core_inputs::CORE_ROOT);
        let outside = fixture.path().join("outside");
        fs::create_dir_all(root.join("src")).unwrap();
        fs::create_dir(&outside).unwrap();
        fs::write(outside.join("source.java"), b"must not read").unwrap();
        // cmd's mklink interprets forward slashes as switches; normalize the
        // private fixture argument through Path components without lossy text.
        let link = root.join("src/junction").components().collect::<PathBuf>();
        let output = std::process::Command::new("cmd.exe")
            .args(["/d", "/c", "mklink", "/J"])
            .arg(&link)
            .arg(&outside)
            .creation_flags(CREATE_NO_WINDOW.0)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "cannot create private junction fixture: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        let mut leases = DirectoryLeases::default();
        let error = leases
            .prepare(&root, "src/junction/source.java")
            .err()
            .unwrap();
        assert!(format!("{error:#}").contains("reparse point"));
        assert!(
            super::super::core_inputs::CoreInputSnapshot::default()
                .read(&root, "src/junction")
                .is_err()
        );
        let error = super::super::core_inputs::discover_core_source_files(&root)
            .err()
            .unwrap();
        assert!(format!("{error:#}").contains("reparse point"));
    }
}
