//! Additional selected-SDK execution inputs held for dependency-cache reuse.
//! Binds bin/lib/conf, not a claim about arbitrary outside-SDK native loading.

use super::nfrt_preparation_phase::NfrtPreparationPhase;
use crate::cancellation::CancellationToken;
use crate::source_projection::nfrt_tool_sdk::NfrtToolSdk;
use eyre::Result;
use eyre::ensure;
use sha2::Digest;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::fs;
use std::fs::File;
use std::io::Read;
use std::path::Path;
use std::path::PathBuf;

const MAX_ENTRIES: u64 = 16_384;
const MAX_FILE_BYTES: u64 = 1024 * 1024 * 1024;
const MAX_TOTAL_BYTES: u64 = 2 * 1024 * 1024 * 1024;

#[derive(Debug)]
pub(super) struct LocalDependencyImage {
    identity: String,
    home: PathBuf,
    inventory: BTreeMap<String, bool>,
    input_count: u64,
    input_bytes: u64,
    files: Vec<File>,
    directories: BTreeMap<PathBuf, File>,
}

impl LocalDependencyImage {
    pub(super) fn capture(sdk: &NfrtToolSdk, cancel: &CancellationToken) -> Result<Self> {
        let home = sdk
            .executable()
            .parent()
            .and_then(Path::parent)
            .ok_or_else(|| eyre::eyre!("Selected dependency SDK has no home"))?;
        Self::capture_home(home, sdk.identity(), cancel)
    }

    pub(super) fn identity(&self) -> &str {
        &self.identity
    }

    #[cfg(windows)]
    pub(super) fn capture_home(
        home: &Path,
        sdk_identity: &str,
        cancel: &CancellationToken,
    ) -> Result<Self> {
        let mut phase = NfrtPreparationPhase::start("local_dependency.sdk_image_capture");
        cancel.bail_if_cancelled()?;
        ensure!(
            home.is_absolute(),
            "Dependency SDK image home is not absolute"
        );
        let mut image = Self {
            identity: String::new(),
            home: home.to_owned(),
            inventory: BTreeMap::new(),
            input_count: 0,
            input_bytes: 0,
            files: Vec::new(),
            directories: BTreeMap::new(),
        };
        image.hold_ancestors(home)?;
        let mut hash = Sha256::new();
        append(&mut hash, b"sfm:local_dependency_sdk_image@2");
        append(&mut hash, std::env::consts::OS.as_bytes());
        append(&mut hash, std::env::consts::ARCH.as_bytes());
        // The existing SDK owner retains and authenticates java/javac/release/modules.
        append(&mut hash, sdk_identity.as_bytes());
        let entries_seen = image.capture_selected(&mut hash, cancel)?;
        image.identity = format!("sha256:{:x}", hash.finalize());
        // Directory read leases protect existing names, not new child creation.
        // Re-enumerate after the complete capture before admitting this image.
        image.recheck(cancel)?;
        phase.work(image.input_count, image.input_bytes);
        phase.success();
        tracing::info!(
            input_count = image.input_count,
            input_bytes = image.input_bytes,
            entry_count = entries_seen,
            "local_dependency_sdk_image_verified"
        );
        Ok(image)
    }

    #[cfg(windows)]
    fn capture_selected(&mut self, hash: &mut Sha256, cancel: &CancellationToken) -> Result<u64> {
        let mut pending = Vec::new();
        for relative in ["conf", "lib", "bin"] {
            let path = self.home.join(relative);
            self.hold_ancestors(&path)?;
            self.inventory.insert(relative.to_owned(), true);
            append(hash, b"directory");
            append(hash, relative.as_bytes());
            pending.push(path);
        }
        let mut entries_seen = 3u64;
        while let Some(directory) = pending.pop() {
            cancel.bail_if_cancelled()?;
            let entries = selected_entries(&directory, &mut entries_seen, cancel)?;
            // Reverse push preserves sorted traversal order with this LIFO stack.
            let mut child_directories = Vec::new();
            for entry in entries {
                cancel.bail_if_cancelled()?;
                let path = entry.path();
                let relative = relative_name(&self.home, &path)?;
                if excluded(&relative) {
                    continue;
                }
                let metadata = fs::symlink_metadata(&path)?;
                reject_reparse(&metadata)?;
                self.inventory.insert(relative.clone(), metadata.is_dir());
                if metadata.is_dir() {
                    self.hold_ancestors(&path)?;
                    append(hash, b"directory");
                    append(hash, relative.as_bytes());
                    child_directories.push(path);
                } else {
                    ensure!(metadata.is_file(), "SDK image entry is not a regular file");
                    self.capture_file(&path, &relative, hash, cancel)?;
                }
            }
            pending.extend(child_directories.into_iter().rev());
        }
        Ok(entries_seen)
    }

    #[cfg(not(windows))]
    fn capture_home(_: &Path, _: &str, _: &CancellationToken) -> Result<Self> {
        eyre::bail!("Local dependency SDK image holding is Windows-only")
    }

    /// Verify selected membership without rereading or hashing file contents.
    /// This detects changes at the check boundary, not future child creation.
    #[cfg(windows)]
    pub(super) fn recheck(&self, cancel: &CancellationToken) -> Result<()> {
        let mut phase =
            NfrtPreparationPhase::start("local_dependency.sdk_image_membership_recheck");
        cancel.bail_if_cancelled()?;
        for directory in self.directories.values() {
            cancel.bail_if_cancelled()?;
            let metadata = directory.metadata()?;
            reject_reparse(&metadata)?;
            ensure!(metadata.is_dir(), "Held SDK image ancestor changed type");
        }
        for file in &self.files {
            cancel.bail_if_cancelled()?;
            let metadata = file.metadata()?;
            reject_reparse(&metadata)?;
            ensure!(metadata.is_file(), "Held SDK image input changed type");
            reject_hardlink(file)?;
        }
        let mut current = BTreeMap::new();
        let mut pending = Vec::new();
        for relative in ["conf", "lib", "bin"] {
            let path = self.home.join(relative);
            let metadata = fs::symlink_metadata(&path)?;
            reject_reparse(&metadata)?;
            ensure!(metadata.is_dir(), "Selected SDK image root changed type");
            ensure!(
                self.inventory.get(relative) == Some(&true),
                "SDK image root was not captured"
            );
            current.insert(relative.to_owned(), true);
            pending.push(path);
        }
        let mut entries_seen = 3u64;
        while let Some(directory) = pending.pop() {
            let entries = selected_entries(&directory, &mut entries_seen, cancel)?;
            let mut child_directories = Vec::new();
            for entry in entries {
                cancel.bail_if_cancelled()?;
                let path = entry.path();
                let relative = relative_name(&self.home, &path)?;
                if excluded(&relative) {
                    continue;
                }
                let metadata = fs::symlink_metadata(&path)?;
                reject_reparse(&metadata)?;
                ensure!(
                    metadata.is_dir() || metadata.is_file(),
                    "SDK image entry changed to a nonregular type"
                );
                let is_directory = metadata.is_dir();
                ensure!(
                    self.inventory.get(&relative) == Some(&is_directory),
                    "SDK image gained a selected entry or changed an entry type: {relative}"
                );
                current.insert(relative, is_directory);
                if is_directory {
                    ensure!(
                        self.directories.contains_key(&path),
                        "SDK image directory lacks its captured lease"
                    );
                    child_directories.push(path);
                }
            }
            pending.extend(child_directories.into_iter().rev());
        }
        ensure!(
            current == self.inventory,
            "SDK image selected membership changed"
        );
        phase.work(u64::try_from(current.len())?, 0);
        phase.success();
        Ok(())
    }

    #[cfg(not(windows))]
    pub(super) fn recheck(&self, _: &CancellationToken) -> Result<()> {
        eyre::bail!("Local dependency SDK image holding is Windows-only")
    }

    #[cfg(windows)]
    fn hold_ancestors(&mut self, path: &Path) -> Result<()> {
        use std::os::windows::fs::OpenOptionsExt as _;
        for ancestor in path.ancestors().collect::<Vec<_>>().into_iter().rev() {
            if self.directories.contains_key(ancestor) {
                continue;
            }
            ensure!(
                self.directories.len() < usize::try_from(MAX_ENTRIES)?,
                "SDK image exceeds its directory lease bound"
            );
            let file = fs::OpenOptions::new()
                .access_mode(0x0001 | 0x0080)
                .share_mode(1)
                .custom_flags(0x0200_0000 | 0x0020_0000)
                .open(ancestor)?;
            let metadata = file.metadata()?;
            reject_reparse(&metadata)?;
            ensure!(metadata.is_dir(), "SDK image ancestor is not a directory");
            self.directories.insert(ancestor.to_owned(), file);
        }
        Ok(())
    }

    #[cfg(windows)]
    fn capture_file(
        &mut self,
        path: &Path,
        relative: &str,
        hash: &mut Sha256,
        cancel: &CancellationToken,
    ) -> Result<()> {
        use crate::file_identity::file_identity;
        use std::os::windows::fs::OpenOptionsExt as _;
        let mut file = fs::OpenOptions::new()
            .read(true)
            .share_mode(1)
            .custom_flags(0x0020_0000)
            .open(path)?;
        let before = file.metadata()?;
        reject_reparse(&before)?;
        ensure!(
            before.is_file() && before.len() <= MAX_FILE_BYTES,
            "SDK image file is not regular or exceeds its byte bound"
        );
        reject_hardlink(&file)?;
        let identity = file_identity(&file)?;
        let next_total = self
            .input_bytes
            .checked_add(before.len())
            .ok_or_else(|| eyre::eyre!("SDK image byte count overflow"))?;
        ensure!(
            next_total <= MAX_TOTAL_BYTES,
            "SDK image exceeds its total byte bound"
        );
        let mut digest = Sha256::new();
        let mut buffer = vec![0u8; 64 * 1024].into_boxed_slice();
        let mut read = 0u64;
        loop {
            cancel.bail_if_cancelled()?;
            let count = file.read(&mut buffer)?;
            if count == 0 {
                break;
            }
            read = read
                .checked_add(u64::try_from(count)?)
                .ok_or_else(|| eyre::eyre!("SDK image read length overflow"))?;
            ensure!(read <= before.len(), "SDK image file grew while reading");
            digest.update(&buffer[..count]);
        }
        ensure!(
            read == before.len()
                && file.metadata()?.len() == read
                && file_identity(&file)? == identity,
            "SDK image file changed while reading"
        );
        append(hash, b"file");
        append(hash, relative.as_bytes());
        append(hash, &read.to_le_bytes());
        append(hash, &digest.finalize());
        self.input_count += 1;
        self.input_bytes = next_total;
        self.files.push(file);
        Ok(())
    }
}

#[cfg(windows)]
fn selected_entries(
    directory: &Path,
    seen: &mut u64,
    cancel: &CancellationToken,
) -> Result<Vec<fs::DirEntry>> {
    let mut entries = Vec::new();
    for entry in fs::read_dir(directory)? {
        cancel.bail_if_cancelled()?;
        *seen = seen
            .checked_add(1)
            .ok_or_else(|| eyre::eyre!("SDK image entry count overflow"))?;
        ensure!(*seen <= MAX_ENTRIES, "SDK image exceeds its entry bound");
        entries.push(entry?);
    }
    entries.sort_by_key(fs::DirEntry::file_name);
    Ok(entries)
}

#[cfg(windows)]
fn relative_name(home: &Path, path: &Path) -> Result<String> {
    Ok(path
        .strip_prefix(home)?
        .to_str()
        .ok_or_else(|| eyre::eyre!("SDK image path is not UTF-8"))?
        .replace('\\', "/"))
}

#[cfg(windows)]
fn excluded(relative: &str) -> bool {
    matches!(
        relative.to_ascii_lowercase().as_str(),
        "bin/java.exe" | "bin/javac.exe" | "lib/modules" | "lib/src.zip"
    )
}

#[cfg(windows)]
fn reject_reparse(metadata: &fs::Metadata) -> Result<()> {
    use std::os::windows::fs::MetadataExt as _;
    ensure!(
        !metadata.is_symlink() && metadata.file_attributes() & 0x400 == 0,
        "SDK image contains a reparse point"
    );
    Ok(())
}

#[cfg(windows)]
fn reject_hardlink(file: &File) -> Result<()> {
    use std::os::windows::io::AsRawHandle as _;
    use windows::Win32::Foundation::HANDLE;
    use windows::Win32::Storage::FileSystem::BY_HANDLE_FILE_INFORMATION;
    use windows::Win32::Storage::FileSystem::GetFileInformationByHandle;
    let mut information = BY_HANDLE_FILE_INFORMATION::default();
    // SAFETY: file retains a live handle; information is a correctly sized output.
    unsafe { GetFileInformationByHandle(HANDLE(file.as_raw_handle()), &raw mut information) }?;
    ensure!(
        information.nNumberOfLinks == 1,
        "SDK image file has hardlink aliases"
    );
    Ok(())
}

#[cfg(windows)]
fn append(hash: &mut Sha256, bytes: &[u8]) {
    hash.update((bytes.len() as u64).to_le_bytes());
    hash.update(bytes);
}

#[cfg(all(test, windows))]
mod tests {
    use super::*;
    use crate::source_projection::provenance::sha256;

    fn prepare_home(home: &Path) {
        for relative in ["bin", "lib", "conf"] {
            fs::create_dir(home.join(relative)).unwrap();
        }
        fs::write(home.join("bin/java.exe"), b"core held separately").unwrap();
        fs::write(home.join("lib/modules"), b"core held separately").unwrap();
        fs::write(home.join("bin/jvm.dll"), b"native runtime").unwrap();
        fs::write(home.join("conf/jvm.cfg"), b"configuration").unwrap();
    }

    #[test]
    fn image_identity_changes_for_native_or_configuration_bytes() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        prepare_home(fixture.path());
        let cancel = CancellationToken::new();
        let first =
            LocalDependencyImage::capture_home(fixture.path(), &sha256(b"core-sdk"), &cancel)?;
        let identity = first.identity().to_owned();
        assert_eq!(first.input_count, 2);
        assert!(
            fs::OpenOptions::new()
                .write(true)
                .open(fixture.path().join("bin/jvm.dll"))
                .is_err()
        );
        assert!(fs::rename(fixture.path().join("bin"), fixture.path().join("moved")).is_err());
        drop(first);
        fs::write(
            fixture.path().join("bin/jvm.dll"),
            b"different native runtime",
        )?;
        let changed =
            LocalDependencyImage::capture_home(fixture.path(), &sha256(b"core-sdk"), &cancel)?;
        assert_ne!(changed.identity(), identity);
        let native_identity = changed.identity().to_owned();
        drop(changed);
        fs::write(
            fixture.path().join("conf/jvm.cfg"),
            b"different configuration",
        )?;
        let configured =
            LocalDependencyImage::capture_home(fixture.path(), &sha256(b"core-sdk"), &cancel)?;
        assert_ne!(configured.identity(), native_identity);
        Ok(())
    }

    #[test]
    fn image_rejects_hardlinks_without_accepting_equal_bytes() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        prepare_home(fixture.path());
        fs::hard_link(
            fixture.path().join("bin/jvm.dll"),
            fixture.path().join("bin/alias.dll"),
        )?;
        assert!(
            LocalDependencyImage::capture_home(
                fixture.path(),
                &sha256(b"core-sdk"),
                &CancellationToken::new()
            )
            .is_err()
        );
        Ok(())
    }

    #[test]
    fn image_is_deterministic_and_ignores_unselected_documentation() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        prepare_home(fixture.path());
        fs::create_dir(fixture.path().join("docs"))?;
        fs::write(fixture.path().join("docs/notes.txt"), b"one")?;
        let cancel = CancellationToken::new();
        let first =
            LocalDependencyImage::capture_home(fixture.path(), &sha256(b"core-sdk"), &cancel)?;
        let identity = first.identity().to_owned();
        drop(first);
        fs::write(fixture.path().join("docs/notes.txt"), b"two")?;
        let second =
            LocalDependencyImage::capture_home(fixture.path(), &sha256(b"core-sdk"), &cancel)?;
        assert_eq!(second.identity(), identity);
        Ok(())
    }

    #[test]
    fn membership_recheck_rejects_new_native_and_configuration_files() -> Result<()> {
        for relative in [
            "bin/new-native.dll",
            "lib/new-runtime.dll",
            "conf/new-config.cfg",
        ] {
            let fixture = tempfile::tempdir()?;
            prepare_home(fixture.path());
            let cancel = CancellationToken::new();
            let image =
                LocalDependencyImage::capture_home(fixture.path(), &sha256(b"core-sdk"), &cancel)?;
            image.recheck(&cancel)?;
            // Existing directory leases intentionally do not prevent this write.
            fs::write(fixture.path().join(relative), b"new execution input")?;
            assert!(
                image.recheck(&cancel).is_err(),
                "accepted newly added {relative}"
            );
        }
        Ok(())
    }

    #[test]
    fn membership_recheck_rejects_new_selected_directories() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        prepare_home(fixture.path());
        let cancel = CancellationToken::new();
        let image =
            LocalDependencyImage::capture_home(fixture.path(), &sha256(b"core-sdk"), &cancel)?;
        fs::create_dir(fixture.path().join("lib/new-native-search-directory"))?;
        assert!(image.recheck(&cancel).is_err());
        Ok(())
    }

    #[test]
    fn final_membership_check_detects_addition_after_initial_capture_walk() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        prepare_home(fixture.path());
        let cancel = CancellationToken::new();
        let mut image = LocalDependencyImage {
            identity: String::new(),
            home: fixture.path().to_owned(),
            inventory: BTreeMap::new(),
            input_count: 0,
            input_bytes: 0,
            files: Vec::new(),
            directories: BTreeMap::new(),
        };
        image.hold_ancestors(fixture.path())?;
        image.capture_selected(&mut Sha256::new(), &cancel)?;
        // Fault injection at the exact boundary used by capture_home's final recheck.
        fs::write(
            fixture.path().join("conf/added-during-capture.cfg"),
            b"new config",
        )?;
        assert!(image.recheck(&cancel).is_err());
        Ok(())
    }

    #[test]
    fn membership_recheck_keeps_documentation_and_source_archive_excluded() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        prepare_home(fixture.path());
        let cancel = CancellationToken::new();
        let image =
            LocalDependencyImage::capture_home(fixture.path(), &sha256(b"core-sdk"), &cancel)?;
        let identity = image.identity().to_owned();
        fs::create_dir(fixture.path().join("docs"))?;
        fs::write(
            fixture.path().join("docs/new-document.txt"),
            b"documentation",
        )?;
        fs::write(fixture.path().join("lib/src.zip"), b"source archive")?;
        image.recheck(&cancel)?;
        drop(image);
        let after =
            LocalDependencyImage::capture_home(fixture.path(), &sha256(b"core-sdk"), &cancel)?;
        assert_eq!(after.identity(), identity);
        Ok(())
    }

    #[test]
    fn membership_recheck_observes_cancellation_and_the_entry_bound() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        prepare_home(fixture.path());
        let image = LocalDependencyImage::capture_home(
            fixture.path(),
            &sha256(b"core-sdk"),
            &CancellationToken::new(),
        )?;
        let cancel = CancellationToken::new();
        cancel.request_cancel("stop SDK image membership recheck");
        assert!(
            image
                .recheck(&cancel)
                .unwrap_err()
                .to_string()
                .contains("stop SDK image membership recheck")
        );
        let mut seen = MAX_ENTRIES;
        assert!(
            selected_entries(
                &fixture.path().join("bin"),
                &mut seen,
                &CancellationToken::new()
            )
            .is_err()
        );
        Ok(())
    }
}
