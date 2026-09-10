//! Bounded read-only IO for review captures. Never changes Git refs, objects or index.

use crate::cancellation::CancellationToken;
use eyre::Context as _;
use eyre::eyre;
use std::fs::File;
use std::fs::Metadata;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Path;
use std::process::Command;
use std::process::Stdio;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::time::Duration;
use std::time::Instant;

pub(crate) const FILE_LIMIT: u64 = 64 * 1024 * 1024;
pub(crate) const TEXT_LIMIT: usize = 4 * 1024 * 1024;
pub(crate) const TOTAL_LIMIT: u64 = 256 * 1024 * 1024;

pub(crate) struct CaptureBudget {
    cancellation: CancellationToken,
    deadline: Instant,
}

impl CaptureBudget {
    pub(crate) fn new(cancellation: &CancellationToken) -> Self {
        Self::with_timeout(cancellation, Duration::from_mins(2))
    }

    pub(crate) fn with_timeout(cancellation: &CancellationToken, timeout: Duration) -> Self {
        Self {
            cancellation: cancellation.clone(),
            deadline: Instant::now() + timeout,
        }
    }

    pub(crate) fn check(&self) -> eyre::Result<()> {
        self.cancellation.bail_if_cancelled()?;
        if Instant::now() >= self.deadline {
            return Err(eyre!(
                "working-tree observation exceeded its deadline; choose a narrower scope"
            ));
        }
        Ok(())
    }

    pub(crate) fn git(
        &self,
        root: &Path,
        args: &[&str],
        input: Option<Vec<u8>>,
        limit: u64,
    ) -> eyre::Result<Vec<u8>> {
        self.check()?;
        let mut child = Command::new("git")
            .arg("--no-pager")
            .arg("--literal-pathspecs")
            .args([
                "-c",
                "core.fsmonitor=false",
                "-c",
                "core.untrackedCache=false",
            ])
            .arg("-C")
            .arg(root)
            .args(args)
            .env("GIT_OPTIONAL_LOCKS", "0")
            .env("GIT_TERMINAL_PROMPT", "0")
            .env("GIT_NO_LAZY_FETCH", "1")
            .env("GIT_NO_REPLACE_OBJECTS", "1")
            .stdin(if input.is_some() {
                Stdio::piped()
            } else {
                Stdio::null()
            })
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .wrap_err("launch read-only capture Git command")?;
        let overflow = Arc::new(AtomicBool::new(false));
        let out_flag = Arc::clone(&overflow);
        let err_flag = Arc::clone(&overflow);
        let stdout = child
            .stdout
            .take()
            .ok_or_else(|| eyre!("capture Git stdout unavailable"))?;
        let stderr = child
            .stderr
            .take()
            .ok_or_else(|| eyre!("capture Git stderr unavailable"))?;
        let out = std::thread::spawn(move || read_pipe(stdout, limit, &out_flag));
        let err = std::thread::spawn(move || read_pipe(stderr, 1024 * 1024, &err_flag));
        let writer = input.map(|input| {
            let mut stdin = child.stdin.take().expect("piped stdin requested");
            std::thread::spawn(move || stdin.write_all(&input))
        });
        let status = loop {
            if let Err(error) = self.check() {
                let _ = child.kill();
                let _ = child.wait();
                break Err(error);
            }
            if overflow.load(Ordering::Acquire) {
                let _ = child.kill();
                let _ = child.wait();
                break Err(eyre!("capture Git output exceeded its bound"));
            }
            match child.try_wait() {
                Ok(Some(status)) => break Ok(status),
                Ok(None) => std::thread::sleep(Duration::from_millis(5)),
                Err(error) => {
                    let _ = child.kill();
                    let _ = child.wait();
                    break Err(error.into());
                }
            }
        };
        let output = out
            .join()
            .map_err(|_panic| eyre!("capture stdout reader panicked"))?;
        let errors = err
            .join()
            .map_err(|_panic| eyre!("capture stderr reader panicked"))?;
        let written = writer.map(|writer| {
            writer
                .join()
                .map_err(|_panic| eyre!("capture stdin writer panicked"))
        });
        let status = status?;
        let output = output?;
        let errors = errors?;
        if !status.success() {
            return Err(eyre!(
                "capture Git {:?} failed: {}",
                args.first(),
                String::from_utf8_lossy(&errors)
            ));
        }
        if let Some(written) = written {
            written??;
        }
        Ok(output)
    }
}

fn read_pipe(
    pipe: impl std::io::Read,
    limit: u64,
    overflow: &AtomicBool,
) -> std::io::Result<Vec<u8>> {
    let mut bytes = Vec::new();
    pipe.take(limit + 1).read_to_end(&mut bytes)?;
    if bytes.len() as u64 > limit {
        overflow.store(true, Ordering::Release);
        return Err(std::io::Error::other("capture pipe byte limit exceeded"));
    }
    Ok(bytes)
}

#[derive(Debug)]
pub(crate) enum DiskFile {
    Missing,
    Unsupported(String),
    Regular { bytes: Vec<u8>, executable: bool },
}

/// On Windows hold non-reparse ancestor handles without write/delete sharing
/// until the leaf read finishes. This closes the check/open junction-swap gap.
#[cfg(windows)]
pub(crate) fn read_disk(root: &Path, path: &str, budget: &CaptureBudget) -> eyre::Result<DiskFile> {
    use std::os::windows::fs::MetadataExt as _;
    use std::os::windows::fs::OpenOptionsExt as _;
    budget.check()?;
    if path.split('/').any(windows_device_component) {
        return Err(eyre!(
            "capture refuses reserved Windows device path: {path}"
        ));
    }
    let mut handles = Vec::new();
    let mut location = root.to_path_buf();
    let mut parts = path.split('/').peekable();
    // Root is canonicalized and verified against Git's top-level before capture.
    let root_handle = std::fs::OpenOptions::new()
        .read(true)
        .share_mode(1)
        .custom_flags(0x0200_0000 | 0x0020_0000)
        .open(root)
        .wrap_err("lock capture root against reparse swaps")?;
    if reparse(&root_handle.metadata()?) {
        return Err(eyre!("capture root changed to a reparse point"));
    }
    handles.push(root_handle);
    while let Some(part) = parts.next() {
        location.push(part);
        let leaf = parts.peek().is_none();
        let handle = match std::fs::OpenOptions::new()
            .read(true)
            .share_mode(1)
            .custom_flags(0x0200_0000 | 0x0020_0000)
            .open(&location)
        {
            Ok(handle) => handle,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                return Ok(DiskFile::Missing);
            }
            Err(error) => {
                return Err(error)
                    .wrap_err_with(|| format!("cannot safely read captured path {path}"));
            }
        };
        let before = handle.metadata()?;
        if reparse(&before) {
            return Ok(DiskFile::Unsupported(
                "Symlink/reparse source is not followed".into(),
            ));
        }
        if !leaf {
            if !before.is_dir() {
                return Ok(DiskFile::Missing);
            }
            handles.push(handle);
            continue;
        }
        if before.is_dir() {
            return Ok(DiskFile::Missing);
        } // The file was replaced by a directory; its children are separate paths.
        if !before.is_file() {
            return Err(eyre!("capture source is not a regular file: {path}"));
        }
        if before.len() > FILE_LIMIT {
            return Err(eyre!(
                "capture path {path} exceeds {FILE_LIMIT}-byte read limit"
            ));
        }
        let bytes = read_file(&handle, budget)?;
        let after = handle.metadata()?;
        if before.file_size() != after.file_size()
            || before.last_write_time() != after.last_write_time()
            || before.creation_time() != after.creation_time()
            || before.file_attributes() != after.file_attributes()
            || bytes.len() as u64 != before.len()
        {
            return Err(eyre!("capture source changed during read: {path}"));
        }
        return Ok(DiskFile::Regular {
            bytes,
            executable: false,
        });
    }
    Err(eyre!("capture file path is empty"))
}

#[cfg(windows)]
fn reparse(metadata: &Metadata) -> bool {
    use std::os::windows::fs::MetadataExt as _;
    metadata.file_attributes() & 0x400 != 0
}

#[cfg(windows)]
fn windows_device_component(part: &str) -> bool {
    let stem = part.split('.').next().unwrap_or(part).to_ascii_uppercase();
    matches!(
        stem.as_str(),
        "CON" | "PRN" | "AUX" | "NUL" | "CONIN$" | "CONOUT$"
    ) || ["COM", "LPT"].iter().any(|prefix| {
        stem.strip_prefix(prefix).is_some_and(|tail| {
            matches!(
                tail,
                "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" | "¹" | "²" | "³"
            )
        })
    })
}

#[cfg(all(test, windows))]
mod tests {
    use super::*;

    #[test]
    fn a_junction_cannot_supply_bytes_from_outside_the_captured_repository() {
        let root = tempfile::tempdir().unwrap();
        let outside = tempfile::tempdir().unwrap();
        std::fs::write(
            outside.path().join("sentinel.txt"),
            "outside capture authority",
        )
        .unwrap();
        let link = root.path().join("escape");
        let created = Command::new("cmd.exe")
            .args(["/d", "/c", "mklink", "/J"])
            .arg(&link)
            .arg(outside.path())
            .output()
            .unwrap();
        assert!(
            created.status.success(),
            "junction setup: {}",
            String::from_utf8_lossy(&created.stderr)
        );
        let result = read_disk(
            root.path(),
            "escape/sentinel.txt",
            &CaptureBudget::new(&CancellationToken::new()),
        );
        // Remove only this exact test junction, never its target directory.
        std::fs::remove_dir(&link).unwrap();
        assert!(matches!(result.unwrap(), DiskFile::Unsupported(_)));
        assert_eq!(
            std::fs::read_to_string(outside.path().join("sentinel.txt")).unwrap(),
            "outside capture authority"
        );
        for name in ["NUL", "con.txt", "COM1.java", "Lpt9", "AUX.json", "COM¹"] {
            assert!(windows_device_component(name));
            assert!(
                read_disk(
                    root.path(),
                    name,
                    &CaptureBudget::new(&CancellationToken::new())
                )
                .is_err()
            );
        }
        assert!(!windows_device_component("component.java"));
    }
}

#[cfg(windows)]
fn read_file(mut file: &File, budget: &CaptureBudget) -> eyre::Result<Vec<u8>> {
    let mut bytes = Vec::new();
    let mut chunk = vec![0u8; 64 * 1024];
    loop {
        budget.check()?;
        let count = file.read(&mut chunk)?;
        if count == 0 {
            return Ok(bytes);
        }
        if bytes.len() as u64 + count as u64 > FILE_LIMIT {
            return Err(eyre!("capture file grew beyond read limit"));
        }
        bytes.extend_from_slice(&chunk[..count]);
    }
}

#[cfg(not(windows))]
pub(crate) fn read_disk(
    _root: &Path,
    _path: &str,
    _budget: &CaptureBudget,
) -> eyre::Result<DiskFile> {
    Err(eyre!(
        "safe working-tree capture currently requires Windows handle-relative source protection"
    ))
}
