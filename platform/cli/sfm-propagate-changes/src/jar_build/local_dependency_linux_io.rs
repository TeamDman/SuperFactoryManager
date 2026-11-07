//! Linux cache namespace capabilities. Locks are cooperative; retained FDs do
//! not exclude same-user writes. Consumers must recheck namespace and contents.
use crate::cancellation::CancellationToken;
use crate::file_identity::file_identity;
use eyre::Result;
use eyre::ensure;
use sha2::Digest as _;
use std::ffi::CString;
use std::fs::File;
use std::fs::OpenOptions;
use std::io::Write;
use std::os::fd::AsRawFd as _;
use std::os::unix::fs::DirBuilderExt as _;
use std::os::unix::fs::FileExt as _;
use std::os::unix::fs::MetadataExt as _;
use std::os::unix::fs::OpenOptionsExt as _;
use std::os::unix::fs::PermissionsExt as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;
use std::time::Instant;

unsafe extern "C" {
    fn linkat(
        oldfd: i32,
        old: *const std::ffi::c_char,
        newfd: i32,
        new: *const std::ffi::c_char,
        flags: i32,
    ) -> i32;
}

#[derive(Clone, Debug)]
pub(crate) struct Directory {
    pub path: PathBuf,
    file: Arc<File>,
    parent: Option<Arc<Directory>>,
}

impl Directory {
    pub fn open(path: &Path) -> Result<Self> {
        ensure!(path.is_absolute(), "cache directory must be absolute");
        let file = OpenOptions::new()
            .read(true)
            .custom_flags(0o600_000)
            .open("/")?;
        let mut current = Self {
            path: PathBuf::from("/"),
            file: Arc::new(file),
            parent: None,
        };
        for part in path.components() {
            match part {
                Component::RootDir => (),
                Component::Normal(name) => {
                    current = current.child(
                        name.to_str()
                            .ok_or_else(|| eyre::eyre!("non-Unicode cache path"))?,
                        false,
                    )?;
                }
                _ => eyre::bail!("non-normal cache directory"),
            }
        }
        current.recheck()?;
        Ok(current)
    }

    pub fn access(&self, name: &str) -> PathBuf {
        PathBuf::from(format!("/proc/self/fd/{}", self.file.as_raw_fd())).join(name)
    }

    pub fn child(&self, name: &str, create: bool) -> Result<Self> {
        leaf(name)?;
        self.recheck()?;
        if create {
            match std::fs::DirBuilder::new()
                .mode(0o700)
                .create(self.access(name))
            {
                Ok(()) => self.sync()?,
                Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => (),
                Err(error) => return Err(error.into()),
            }
        }
        let file = OpenOptions::new()
            .read(true)
            .custom_flags(0o600_000)
            .open(self.access(name))?;
        ensure!(
            file.metadata()?.is_dir(),
            "cache ancestor is not a directory"
        );
        let next = Self {
            path: self.path.join(name),
            file: Arc::new(file),
            parent: Some(Arc::new(self.clone())),
        };
        next.recheck()?;
        Ok(next)
    }

    pub fn recheck(&self) -> Result<()> {
        if let Some(parent) = &self.parent {
            parent.recheck()?;
            let name = self
                .path
                .file_name()
                .and_then(|n| n.to_str())
                .ok_or_else(|| eyre::eyre!("cache ancestor has no leaf"))?;
            let current = OpenOptions::new()
                .read(true)
                .custom_flags(0o600_000)
                .open(parent.access(name))?;
            ensure!(
                file_identity(&current)? == file_identity(&self.file)?,
                "cache directory ancestry was replaced"
            );
        }
        ensure!(
            self.file.metadata()?.is_dir(),
            "held cache directory changed type"
        );
        Ok(())
    }

    pub fn require_owner(&self) -> Result<()> {
        unsafe extern "C" {
            fn geteuid() -> u32;
        }
        // SAFETY: geteuid takes no pointers and reads the process credential.
        let uid = unsafe { geteuid() };
        let metadata = self.file.metadata()?;
        ensure!(
            metadata.uid() == uid && metadata.mode() & 0o022 == 0,
            "dependency cache directory is foreign-owned or group/world writable"
        );
        Ok(())
    }

    pub fn sync(&self) -> Result<()> {
        self.file.sync_all()?;
        Ok(())
    }

    pub fn read(&self, name: &str) -> Result<File> {
        leaf(name)?;
        self.recheck()?;
        let file = OpenOptions::new()
            .read(true)
            .custom_flags(0o404_000)
            .open(self.access(name))?;
        regular_single(&file)?;
        Ok(file)
    }

    pub fn publish(&self, name: &str, bytes: &[u8], cancel: &CancellationToken) -> Result<()> {
        self.publish_with(
            name,
            |file| {
                for chunk in bytes.chunks(64 * 1024) {
                    cancel.bail_if_cancelled()?;
                    file.write_all(chunk)?;
                }
                Ok(())
            },
            cancel,
        )
    }

    pub fn publish_with(
        &self,
        name: &str,
        write: impl FnOnce(&mut File) -> Result<()>,
        cancel: &CancellationToken,
    ) -> Result<()> {
        leaf(name)?;
        self.recheck()?;
        cancel.bail_if_cancelled()?;
        // O_TMPFILE: no mutable staging pathname and no pathname cleanup. The
        // required filesystem capability fails closed; never use a named fallback.
        let mut file = OpenOptions::new()
            .read(true)
            .write(true)
            .custom_flags(0o2020_0000)
            .mode(0o600)
            .open(self.access(""))?;
        write(&mut file)?;
        ensure!(
            file.metadata()?.is_file() && file.metadata()?.nlink() == 0,
            "anonymous cache staging changed"
        );
        file.set_permissions(std::fs::Permissions::from_mode(0o400))?;
        file.sync_all()?;
        cancel.bail_if_cancelled()?;
        self.recheck()?;
        let old = CString::new(format!("/proc/self/fd/{}", file.as_raw_fd()))?;
        let new = CString::new(name)?;
        // SAFETY: all strings and retained descriptors remain live. Only our
        // procfs FD is followed. linkat atomically refuses occupied names.
        let result = unsafe {
            linkat(
                -100,
                old.as_ptr(),
                self.file.as_raw_fd(),
                new.as_ptr(),
                0x400,
            )
        };
        ensure!(
            result == 0,
            "create-only cache publication failed: {}",
            std::io::Error::last_os_error()
        );
        self.sync()?;
        self.recheck()?;
        Ok(())
    }

    pub fn lock(&self, name: &str, cancel: &CancellationToken) -> Result<Lease> {
        self.lock_after_open(name, cancel, || ())
    }

    fn lock_after_open(
        &self,
        name: &str,
        cancel: &CancellationToken,
        opened: impl FnOnce(),
    ) -> Result<Lease> {
        leaf(name)?;
        self.recheck()?;
        let file = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .mode(0o600)
            .custom_flags(0o404_000)
            .open(self.access(name))?;
        regular_single(&file)?;
        ensure!(
            file.metadata()?.len() <= 64 * 1024,
            "cache lock exceeds byte bound"
        );
        let lease = Lease {
            directory: self.clone(),
            name: name.to_owned(),
            file,
        };
        opened();
        let deadline = Instant::now() + Duration::from_secs(300);
        loop {
            cancel.bail_if_cancelled()?;
            lease.recheck()?;
            match lease.file.try_lock() {
                Ok(()) => break,
                Err(std::fs::TryLockError::WouldBlock) => {
                    ensure!(
                        Instant::now() < deadline,
                        "cache producer lock wait exceeded five minutes; drain its owner without deleting the lock"
                    );
                    std::thread::sleep(Duration::from_millis(25));
                }
                Err(error) => return Err(error.into()),
            }
        }
        lease.recheck()?;
        Ok(lease)
    }
}

#[derive(Debug)]
pub(crate) struct Lease {
    directory: Directory,
    name: String,
    file: File,
}
impl Lease {
    pub fn recheck(&self) -> Result<()> {
        let current = self.directory.read(&self.name)?;
        regular_single(&self.file)?;
        ensure!(
            file_identity(&current)? == file_identity(&self.file)?,
            "cache producer lock pathname was replaced"
        );
        Ok(())
    }
}

#[derive(Debug)]
pub(crate) struct HeldFile {
    pub directory: Directory,
    pub name: String,
    pub file: File,
    pub bytes: u64,
    pub digest: String,
}
impl HeldFile {
    pub fn capture(
        directory: &Directory,
        name: &str,
        bound: u64,
        cancel: &CancellationToken,
    ) -> Result<Self> {
        let file = directory.read(name)?;
        let bytes = file.metadata()?.len();
        ensure!(bytes <= bound, "cache file exceeds byte bound");
        let digest = hash(&file, bytes, cancel)?;
        let held = Self {
            directory: directory.clone(),
            name: name.to_owned(),
            file,
            bytes,
            digest,
        };
        held.recheck(cancel)?;
        Ok(held)
    }
    pub fn recheck(&self, cancel: &CancellationToken) -> Result<()> {
        self.recheck_after_open(cancel, || ())
    }

    fn recheck_after_open(&self, cancel: &CancellationToken, opened: impl FnOnce()) -> Result<()> {
        cancel.bail_if_cancelled()?;
        self.check_namespace()?;
        opened();
        ensure!(
            hash(&self.file, self.bytes, cancel)? == self.digest,
            "cache file contents changed"
        );
        self.check_namespace()
    }

    pub(crate) fn check_namespace(&self) -> Result<()> {
        let current = self.directory.read(&self.name)?;
        regular_single(&self.file)?;
        ensure!(
            file_identity(&current)? == file_identity(&self.file)?
                && current.metadata()?.len() == self.bytes
                && self.file.metadata()?.len() == self.bytes,
            "cache file pathname or length changed"
        );
        self.directory.recheck()
    }
    pub fn content(&self, cancel: &CancellationToken) -> Result<Vec<u8>> {
        self.recheck(cancel)?;
        let mut bytes = vec![0; usize::try_from(self.bytes)?];
        self.file.read_exact_at(&mut bytes, 0)?;
        ensure!(
            format!("sha256:{:x}", sha2::Sha256::digest(&bytes)) == self.digest,
            "cache metadata changed during read"
        );
        self.check_namespace()?;
        Ok(bytes)
    }
}

pub(crate) fn hash(file: &File, expected: u64, cancel: &CancellationToken) -> Result<String> {
    #[cfg(test)]
    crate::jar_build::verification_metrics::hash(expected);
    let before = file.metadata()?;
    ensure!(
        before.is_file() && before.len() == expected,
        "cache file length changed"
    );
    let mut offset = 0;
    let mut digest = sha2::Sha256::new();
    let mut buffer = vec![0; 64 * 1024];
    loop {
        cancel.bail_if_cancelled()?;
        let count = file.read_at(&mut buffer, offset)?;
        if count == 0 {
            break;
        }
        offset += u64::try_from(count)?;
        ensure!(offset <= expected, "cache file grew during read");
        digest.update(&buffer[..count]);
    }
    let after = file.metadata()?;
    ensure!(
        offset == expected
            && before.len() == after.len()
            && before.modified()? == after.modified()?,
        "cache file changed during read"
    );
    Ok(format!("sha256:{:x}", digest.finalize()))
}

pub(crate) fn regular_single(file: &File) -> Result<()> {
    let meta = file.metadata()?;
    ensure!(
        meta.is_file() && meta.nlink() == 1,
        "cache input is nonregular or has hardlink aliases"
    );
    Ok(())
}

pub(crate) fn readonly_owned(file: &File) -> Result<()> {
    unsafe extern "C" {
        fn geteuid() -> u32;
    }
    // SAFETY: geteuid has no pointer arguments and reads process credentials.
    let uid = unsafe { geteuid() };
    let metadata = file.metadata()?;
    ensure!(
        metadata.uid() == uid && metadata.mode() & 0o222 == 0,
        "durable cache input is foreign-owned or writable"
    );
    Ok(())
}

fn leaf(name: &str) -> Result<()> {
    ensure!(
        !name.is_empty()
            && name != "."
            && name != ".."
            && name.len() <= 255
            && !name.contains(['/', '\\', '\0']),
        "unsafe cache leaf"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn linux_cache_detects_leaf_replacement_during_long_verification() -> Result<()> {
        let temp = tempfile::tempdir()?;
        let directory = Directory::open(temp.path())?;
        let cancel = CancellationToken::new();
        directory.publish("classes.jar", b"original", &cancel)?;
        let held = HeldFile::capture(&directory, "classes.jar", 32, &cancel)?;
        let result = held.recheck_after_open(&cancel, || {
            std::fs::rename(
                temp.path().join("classes.jar"),
                temp.path().join("retained.jar"),
            )
            .unwrap();
            std::fs::write(temp.path().join("classes.jar"), b"mutated!").unwrap();
        });
        assert!(result.is_err());
        assert_eq!(std::fs::read(temp.path().join("classes.jar"))?, b"mutated!");
        assert_eq!(
            std::fs::read(temp.path().join("retained.jar"))?,
            b"original"
        );
        Ok(())
    }

    #[test]
    fn linux_cache_waiter_refuses_lock_inode_replacement() -> Result<()> {
        let temp = tempfile::tempdir()?;
        let directory = Directory::open(temp.path())?;
        let cancel = CancellationToken::new();
        let first = directory.lock("producer.lock", &cancel)?;
        let (ready, receive) = std::sync::mpsc::sync_channel(1);
        let (go, proceed) = std::sync::mpsc::sync_channel(1);
        let worker_dir = directory.clone();
        let worker = std::thread::spawn(move || {
            worker_dir
                .lock_after_open("producer.lock", &CancellationToken::new(), || {
                    ready.send(()).unwrap();
                    proceed.recv_timeout(Duration::from_secs(3)).unwrap();
                })
                .map(drop)
        });
        receive.recv_timeout(Duration::from_secs(3))?;
        std::fs::rename(
            temp.path().join("producer.lock"),
            temp.path().join("old.lock"),
        )?;
        std::fs::write(temp.path().join("producer.lock"), b"")?;
        let second = directory.lock("producer.lock", &cancel)?;
        go.send(())?;
        assert!(worker.join().unwrap().is_err());
        assert!(first.recheck().is_err());
        second.recheck()?;
        Ok(())
    }

    #[test]
    fn linux_cache_publication_detects_ancestry_swap_and_destination_race() -> Result<()> {
        for attack in ["ancestry", "destination", "cancel"] {
            let temp = tempfile::tempdir()?;
            let path = temp.path().join("entry");
            std::fs::create_dir(&path)?;
            let directory = Directory::open(&path)?;
            let cancel = CancellationToken::new();
            let result = directory.publish_with(
                "output",
                |file| {
                    file.write_all(b"verified bytes")?;
                    match attack {
                        "ancestry" => {
                            std::fs::rename(&path, temp.path().join("moved"))?;
                            std::fs::create_dir(&path)?;
                        }
                        "destination" => std::fs::write(path.join("output"), b"foreign")?,
                        _ => cancel.request_cancel("after staging write"),
                    }
                    Ok(())
                },
                &cancel,
            );
            assert!(result.is_err());
            if attack == "destination" {
                assert_eq!(std::fs::read(path.join("output"))?, b"foreign");
            } else {
                assert!(!path.join("output").exists());
            }
        }
        Ok(())
    }
}
