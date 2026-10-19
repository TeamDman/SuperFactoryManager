//! Explicit tool-owned native cache roots; no arbitrary directory adoption.
//! Windows directory/file guards remain live through the returned owner.

use super::super::build_snapshot_receipt::ensure_owned_directory;
#[cfg(any(windows, target_os = "linux"))]
use super::super::local_dependency_cache::lease_ancestors;
#[cfg(any(windows, target_os = "linux"))]
use super::super::local_dependency_cache::open_read;
use crate::cancellation::CancellationToken;
use crate::source_projection::candidate_lock::is_reparse;
use eyre::Result;
use eyre::WrapErr as _;
use eyre::ensure;
use std::fs;
use std::fs::File;
use std::fs::OpenOptions;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;
use std::sync::Mutex;
use std::time::Duration;
use std::time::Instant;

const MARKER: &str = ".sfm-native-cache-root.json";
const SCHEMA: &str = "sfm:native_cache_root@1";
const MAX_MARKER_BYTES: u64 = 64 * 1024;
const MAX_GUARDS: usize = 16_384;
const CLAIM_WAIT: Duration = Duration::from_secs(2);

#[derive(Debug)]
pub(super) struct NativeCacheRoot {
    path: PathBuf,
    guards: Mutex<Vec<File>>,
}

impl NativeCacheRoot {
    /// Validate without creating any file or directory.
    pub(super) fn preflight(
        path: &Path,
        repository: &Path,
        source_project: &Path,
        java_home: &Path,
    ) -> Result<PathBuf> {
        ensure!(
            cfg!(any(windows, target_os = "linux")),
            "Native cache root override is unsupported on this platform"
        );
        let checked = validate_location(path, repository, source_project, java_home)?;
        let _parents = lease_ancestors(checked.parent().expect("validated root has parent"))?;
        if checked.try_exists()? {
            let _root = lease_ancestors(&checked)?;
            let _marker = wait_for_marker(&checked, &CancellationToken::new())?;
        }
        Ok(checked)
    }

    pub(super) fn acquire(
        path: &Path,
        repository: &Path,
        source_project: &Path,
        java_home: &Path,
        cancel: &CancellationToken,
    ) -> Result<Self> {
        cancel.bail_if_cancelled()?;
        ensure!(
            cfg!(any(windows, target_os = "linux")),
            "Native cache root override is unsupported on this platform"
        );
        let checked = validate_location(path, repository, source_project, java_home)?;
        let parent = checked.parent().expect("validated root has parent");
        // Hold existing parents before attempting the single new leaf creation.
        let mut guards = lease_ancestors(parent)?;
        ensure!(
            validate_location(path, repository, source_project, java_home)? == checked,
            "Native cache root changed before ownership claim"
        );
        cancel.bail_if_cancelled()?;
        let created = match fs::create_dir(&checked) {
            Ok(()) => true,
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => false,
            Err(error) => return Err(error.into()),
        };
        guards.extend(lease_ancestors(&checked)?);
        if created {
            // Only the creator may write a marker. Existing unmarked roots are
            // observed read-only, including a cooperative creator's short race.
            cancel.bail_if_cancelled()?;
            write_marker(&checked)?;
        }
        guards.push(wait_for_marker(&checked, cancel)?);
        cancel.bail_if_cancelled()?;
        Ok(Self {
            path: checked,
            guards: Mutex::new(guards),
        })
    }

    pub(super) fn path(&self) -> &Path {
        &self.path
    }

    pub(super) fn prepare_directory(
        &self,
        relative: &str,
        cancel: &CancellationToken,
    ) -> Result<PathBuf> {
        cancel.bail_if_cancelled()?;
        ensure!(
            !relative.is_empty() && relative.len() <= 4096 && !relative.contains('\\'),
            "Native cache descendant requires a bounded forward-slash relative path"
        );
        let parts = relative.split('/').collect::<Vec<_>>();
        ensure!(parts.len() <= 32, "Native cache descendant is too deep");
        for part in &parts {
            validate_component(part)?;
        }
        let mut guards = self
            .guards
            .lock()
            .map_err(|error| eyre::eyre!("Native cache root guard mutex was poisoned: {error}"))?;
        cancel.bail_if_cancelled()?;
        let mut selected = self.path.clone();
        for part in parts {
            cancel.bail_if_cancelled()?;
            selected.push(part);
            ensure_owned_directory(&self.path, &selected)?;
            let next = lease_ancestors(&selected)?;
            ensure!(
                guards.len().saturating_add(next.len()) <= MAX_GUARDS,
                "Native cache root exceeds its retained guard bound"
            );
            guards.extend(next);
        }
        Ok(selected)
    }
}

fn validate_location(
    path: &Path,
    repository: &Path,
    source_project: &Path,
    java_home: &Path,
) -> Result<PathBuf> {
    validate_absolute_spelling(path)?;
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Native cache root has no parent"))?;
    ensure!(
        path.file_name().is_some(),
        "Native cache root cannot be a filesystem root"
    );
    let parent =
        dunce::canonicalize(parent).wrap_err("Native cache root parent must already exist")?;
    ensure!(
        parent.is_dir(),
        "Native cache root parent is not a directory"
    );
    let expected = match fs::symlink_metadata(path) {
        Ok(metadata) => {
            ensure!(
                metadata.is_dir() && !is_reparse(&metadata),
                "Native cache root is a link or non-directory"
            );
            dunce::canonicalize(path)?
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            parent.join(path.file_name().expect("validated leaf"))
        }
        Err(error) => return Err(error.into()),
    };
    ensure!(
        expected.as_os_str() == path.as_os_str(),
        "Native cache root must use exact normal canonical spelling"
    );
    let repository = canonical_directory(repository)?;
    let source_project = logical_source_project(source_project)?;
    let java_home = canonical_directory(java_home)?;
    ensure!(
        !is_ancestor_or_equal(&expected, &repository),
        "Native cache root cannot equal or contain the repository"
    );
    ensure!(
        !overlaps(&expected, &source_project),
        "Native cache root must not overlap the source project"
    );
    ensure!(
        !overlaps(&expected, &java_home),
        "Native cache root must not overlap the selected SDK"
    );
    Ok(expected)
}

fn canonical_directory(path: &Path) -> Result<PathBuf> {
    ensure!(
        path.is_absolute(),
        "Protected native input root must be absolute"
    );
    let checked = dunce::canonicalize(path)?;
    ensure!(
        checked.is_dir(),
        "Protected native input root is not a directory"
    );
    Ok(checked)
}

fn logical_source_project(path: &Path) -> Result<PathBuf> {
    // Catalog joins are trusted inputs and can retain interior '/' on Windows.
    // Normalize only this protected input; explicit output spelling stays exact.
    let path = dunce::simplified(path);
    #[cfg(windows)]
    let normalized = PathBuf::from(
        path.to_str()
            .ok_or_else(|| eyre::eyre!("Source project path is not Unicode"))?
            .replace('/', "\\"),
    );
    #[cfg(windows)]
    let path = normalized.as_path();
    validate_absolute_spelling(path)?;
    let mut existing = path.to_owned();
    let mut tail = Vec::new();
    loop {
        match fs::symlink_metadata(&existing) {
            Ok(metadata) => {
                ensure!(
                    metadata.is_dir() && !is_reparse(&metadata),
                    "Source project ancestor is a link or non-directory"
                );
                break;
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                ensure!(
                    tail.len() < 128,
                    "Source project has too many missing components"
                );
                tail.push(
                    existing
                        .file_name()
                        .ok_or_else(|| eyre::eyre!("Source project has no existing ancestor"))?
                        .to_owned(),
                );
                existing = existing
                    .parent()
                    .ok_or_else(|| eyre::eyre!("Source project has no existing parent"))?
                    .to_owned();
            }
            Err(error) => return Err(error.into()),
        }
    }
    let _parents = lease_ancestors(&existing)?;
    let mut expected = dunce::canonicalize(&existing)?;
    for part in tail.into_iter().rev() {
        expected.push(part);
    }
    ensure!(
        expected.as_os_str() == path.as_os_str(),
        "Source project requires normal canonical spelling for cache-root preflight"
    );
    Ok(expected)
}

fn validate_absolute_spelling(path: &Path) -> Result<()> {
    let text = path
        .to_str()
        .ok_or_else(|| eyre::eyre!("Native cache root is not valid Unicode"))?;
    ensure!(
        path.is_absolute() && !text.is_empty() && !text.chars().any(char::is_control),
        "Native cache root requires an absolute normal path without control characters"
    );
    for component in path.components() {
        match component {
            Component::Normal(value) => validate_component(
                value
                    .to_str()
                    .ok_or_else(|| eyre::eyre!("Native cache path component is not Unicode"))?,
            )?,
            Component::RootDir => (),
            #[cfg(windows)]
            Component::Prefix(prefix) => {
                use std::path::Prefix;
                ensure!(
                    matches!(prefix.kind(), Prefix::Disk(_) | Prefix::UNC(_, _)),
                    "Native cache root rejects verbatim and device namespace aliases"
                );
            }
            _ => eyre::bail!("Native cache root rejects dot and traversal components"),
        }
    }
    Ok(())
}

fn validate_component(value: &str) -> Result<()> {
    ensure!(
        !value.is_empty()
            && value != "."
            && value != ".."
            && !value.ends_with(['.', ' '])
            && !value.chars().any(|character| character.is_control()
                || matches!(
                    character,
                    '/' | '\\' | ':' | '<' | '>' | '"' | '|' | '?' | '*'
                )),
        "Native cache root rejects unsafe or aliased path components"
    );
    let stem = value.split('.').next().unwrap_or(value).to_uppercase();
    let numbered_device = ["COM", "LPT"].iter().any(|prefix| {
        stem.strip_prefix(prefix).is_some_and(|suffix| {
            matches!(
                suffix,
                "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" | "¹" | "²" | "³"
            )
        })
    });
    ensure!(
        !matches!(
            stem.as_str(),
            "CON" | "PRN" | "AUX" | "NUL" | "CLOCK$" | "CONIN$" | "CONOUT$"
        ) && !numbered_device,
        "Native cache root rejects reserved device names"
    );
    Ok(())
}

fn is_ancestor_or_equal(parent: &Path, child: &Path) -> bool {
    #[cfg(windows)]
    {
        Path::new(&child.to_string_lossy().to_lowercase())
            .starts_with(parent.to_string_lossy().to_lowercase())
    }
    #[cfg(not(windows))]
    {
        child.starts_with(parent)
    }
}

fn overlaps(first: &Path, second: &Path) -> bool {
    is_ancestor_or_equal(first, second) || is_ancestor_or_equal(second, first)
}

fn marker_bytes(root: &Path) -> Result<Vec<u8>> {
    let spelling = root
        .to_str()
        .ok_or_else(|| eyre::eyre!("Native cache root is not Unicode"))?;
    let raw = facet_json::to_string(&(SCHEMA.to_owned(), spelling.to_owned()))?.into_bytes();
    ensure!(
        raw.len() as u64 <= MAX_MARKER_BYTES,
        "Native cache ownership marker exceeds its bound"
    );
    Ok(raw)
}

fn write_marker(root: &Path) -> Result<()> {
    let raw = marker_bytes(root)?;
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt as _;
        options.share_mode(0).custom_flags(0x0020_0000)
    };
    let mut file = options.open(root.join(MARKER))?;
    file.write_all(&raw)?;
    file.sync_all()?;
    Ok(())
}

fn read_marker(root: &Path) -> Result<Option<File>> {
    let mut file = open_read(&root.join(MARKER))?;
    let before = file.metadata()?.len();
    ensure!(
        before <= MAX_MARKER_BYTES,
        "Native cache ownership marker is oversized"
    );
    if before == 0 {
        return Ok(None);
    }
    let mut raw = Vec::new();
    (&mut file)
        .take(MAX_MARKER_BYTES + 1)
        .read_to_end(&mut raw)?;
    if raw.len() as u64 != before || file.metadata()?.len() != before {
        return Ok(None);
    }
    let Ok(marker) = facet_json::from_slice::<(String, String)>(&raw) else {
        return Ok(None);
    };
    ensure!(
        marker.0 == SCHEMA
            && raw == marker_bytes(root)?
            && raw.len() as u64 == before
            && file.metadata()?.len() == before,
        "Native cache ownership marker is noncanonical or belongs to another root"
    );
    Ok(Some(file))
}

fn wait_for_marker(root: &Path, cancel: &CancellationToken) -> Result<File> {
    let started = Instant::now();
    loop {
        cancel.bail_if_cancelled()?;
        match read_marker(root) {
            Ok(Some(file)) => return Ok(file),
            Ok(None) => {
                ensure!(
                    started.elapsed() < CLAIM_WAIT,
                    "Native cache root has no complete tool-owned marker; pending bytes preserved"
                );
            }
            Err(error) => {
                let transient = error
                    .downcast_ref::<std::io::Error>()
                    .is_some_and(|source| {
                        source.kind() == std::io::ErrorKind::NotFound
                            || (cfg!(windows) && matches!(source.raw_os_error(), Some(5 | 32 | 33)))
                    });
                if !transient || started.elapsed() >= CLAIM_WAIT {
                    return Err(error).wrap_err("Native cache root has no valid tool-owned marker; existing contents preserved");
                }
            }
        }
        std::thread::sleep(Duration::from_millis(20));
    }
}

#[cfg(not(any(windows, target_os = "linux")))]
fn lease_ancestors(_: &Path) -> Result<Vec<File>> {
    eyre::bail!("Native cache root override is unsupported on this platform")
}

#[cfg(not(any(windows, target_os = "linux")))]
fn open_read(_: &Path) -> Result<File> {
    eyre::bail!("Native cache root override is unsupported on this platform")
}

#[cfg(test)]
#[path = "native_cache_root_tests.rs"]
mod tests;
