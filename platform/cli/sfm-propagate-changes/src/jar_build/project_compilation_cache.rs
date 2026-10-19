//! Locally recorded compilation output integrity, separate from fresh NFRT evidence.
//! This detects stale/edited/missing/extra output bytes; a writable receipt does
//! not authenticate production against a writer replacing receipt and payload.
//! Callers hold the project producer lock. Interrupted manifest writes are misses.
#[path = "compilation_output_entry.rs"]
mod compilation_output_entry;
#[path = "compilation_output_manifest.rs"]
mod compilation_output_manifest;
#[cfg(test)]
#[path = "project_compilation_cache_tests.rs"]
mod tests;

use compilation_output_entry::CompilationOutputEntry;
use compilation_output_manifest::CompilationOutputManifest;
use eyre::Result;
use eyre::ensure;
use sha2::Digest;
use sha2::Sha256;
use std::fs;
use std::io::Read;
use std::io::Write;
#[cfg(windows)]
use std::os::windows::fs::MetadataExt as _;
use std::path::Path;
use std::path::PathBuf;

const MAX_FILES: usize = 32_768;
const MAX_FILE_BYTES: u64 = 128 * 1024 * 1024;
const MAX_TOTAL_BYTES: u64 = 1024 * 1024 * 1024;
const MAX_MANIFEST_BYTES: u64 = 8 * 1024 * 1024;

fn manifest_path(state: &Path) -> PathBuf {
    state.with_extension("outputs.json")
}

pub(super) fn record(
    cache: &Path,
    state: &Path,
    fingerprint: &str,
    roots: &[(&str, &Path)],
    check: impl Fn() -> Result<()>,
) -> Result<()> {
    check()?;
    checked_location(
        cache,
        state
            .parent()
            .ok_or_else(|| eyre::eyre!("Missing state parent"))?,
    )?;
    let manifest = CompilationOutputManifest {
        schema: "sfm:local_compilation_outputs@1".to_owned(),
        input_fingerprint: fingerprint.to_owned(),
        outputs: inventory(cache, roots, &check)?,
    };
    let text = facet_json::to_string(&manifest)?;
    ensure!(
        text.len() as u64 <= MAX_MANIFEST_BYTES,
        "Compilation inventory is oversized"
    );
    let path = manifest_path(state);
    check()?;
    write_record(cache, &path, text.as_bytes())?;
    write_record(cache, state, fingerprint.as_bytes())?;
    Ok(())
}

pub(super) fn write_record(cache: &Path, path: &Path, bytes: &[u8]) -> Result<()> {
    checked_location(
        cache,
        path.parent()
            .ok_or_else(|| eyre::eyre!("Missing record parent"))?,
    )?;
    let mut options = fs::OpenOptions::new();
    options.write(true).create(true);
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt as _;
        options.share_mode(1).custom_flags(0x0020_0000)
    };
    #[cfg(target_os = "linux")]
    {
        use std::os::unix::fs::OpenOptionsExt as _;
        options.custom_flags(0o400_000)
    };
    let mut output = options.open(path)?;
    ensure!(
        regular(&output.metadata()?),
        "Unsafe compilation manifest destination"
    );
    single_link(&output)?;
    checked_location(cache, path)?;
    output.set_len(0)?;
    output.write_all(bytes)?;
    output.flush()?;
    Ok(())
}

pub(super) fn invalidate(cache: &Path, state: &Path, check: impl Fn() -> Result<()>) -> Result<()> {
    check()?;
    write_record(cache, state, b"sfm:unbound_java_inputs@1")
}

pub(super) fn matches(
    cache: &Path,
    state: &Path,
    fingerprint: &str,
    roots: &[(&str, &Path)],
    check: impl Fn() -> Result<()>,
) -> Result<bool> {
    check()?;
    let result = checked_matches(cache, state, fingerprint, roots, &check);
    // Cancellation remains an error; damaged old output inventories are misses.
    check()?;
    match result {
        Ok(hit) => Ok(hit),
        Err(error) => {
            tracing::debug!(%error, "Local javac output inventory miss");
            Ok(false)
        }
    }
}

fn checked_matches(
    cache: &Path,
    state: &Path,
    fingerprint: &str,
    roots: &[(&str, &Path)],
    check: &impl Fn() -> Result<()>,
) -> Result<bool> {
    let state_file = open_read(state)?;
    let state_metadata = state_file.metadata()?;
    ensure!(
        regular(&state_metadata) && state_metadata.len() <= 4096,
        "Unsafe compiler input state"
    );
    let mut state_bytes = Vec::new();
    state_file.take(4097).read_to_end(&mut state_bytes)?;
    if state_bytes != fingerprint.as_bytes() {
        return Ok(false);
    }
    let path = manifest_path(state);
    checked_location(cache, &path)?;
    let file = open_read(&path)?;
    let metadata = file.metadata()?;
    ensure!(
        regular(&metadata) && metadata.len() <= MAX_MANIFEST_BYTES,
        "Unsafe compilation inventory"
    );
    let mut bytes = Vec::new();
    file.take(MAX_MANIFEST_BYTES + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 == metadata.len(),
        "Compilation inventory changed during read"
    );
    let manifest: CompilationOutputManifest = facet_json::from_slice(&bytes)?;
    if manifest.schema != "sfm:local_compilation_outputs@1"
        || manifest.input_fingerprint != fingerprint
        || manifest.outputs.len() > MAX_FILES
    {
        return Ok(false);
    }
    let actual = inventory(cache, roots, check)?;
    Ok(actual == manifest.outputs)
}

fn checked_location(cache: &Path, path: &Path) -> Result<()> {
    ensure!(
        fs::canonicalize(path)?.starts_with(fs::canonicalize(cache)?),
        "Compilation output escaped cache"
    );
    Ok(())
}

fn regular(metadata: &fs::Metadata) -> bool {
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt as _;
        metadata.is_file() && metadata.file_attributes() & 0x400 == 0
    }
    #[cfg(not(windows))]
    {
        metadata.is_file() && !metadata.is_symlink()
    }
}

pub(super) fn open_read(path: &Path) -> Result<fs::File> {
    let mut options = fs::OpenOptions::new();
    options.read(true);
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt as _;
        options.share_mode(1).custom_flags(0x0020_0000)
    };
    #[cfg(target_os = "linux")]
    {
        use std::os::unix::fs::OpenOptionsExt as _;
        options.custom_flags(0o400_000)
    };
    let file = options.open(path)?;
    ensure!(regular(&file.metadata()?), "Unsafe compilation input");
    single_link(&file)?;
    Ok(file)
}

#[cfg(windows)]
fn single_link(file: &fs::File) -> Result<()> {
    use std::os::windows::io::AsRawHandle as _;
    use windows::Win32::Foundation::HANDLE;
    use windows::Win32::Storage::FileSystem::{
        FILE_STANDARD_INFO, FileStandardInfo, GetFileInformationByHandleEx,
    };
    let mut information = FILE_STANDARD_INFO::default();
    // SAFETY: the live file owns this handle and the initialized output
    // buffer has exactly the FILE_STANDARD_INFO size requested here.
    unsafe {
        GetFileInformationByHandleEx(
            HANDLE(file.as_raw_handle()),
            FileStandardInfo,
            std::ptr::from_mut(&mut information).cast(),
            u32::try_from(std::mem::size_of_val(&information))?,
        )
    }?;
    ensure!(
        information.NumberOfLinks == 1,
        "Hardlinked compiler cache record or output"
    );
    Ok(())
}

#[cfg(unix)]
fn single_link(file: &fs::File) -> Result<()> {
    use std::os::unix::fs::MetadataExt as _;
    ensure!(
        file.metadata()?.nlink() == 1,
        "Hardlinked compiler cache record or output"
    );
    Ok(())
}

#[cfg(not(any(windows, unix)))]
fn single_link(_file: &fs::File) -> Result<()> {
    Ok(())
}

fn inventory(
    cache: &Path,
    roots: &[(&str, &Path)],
    check: &impl Fn() -> Result<()>,
) -> Result<Vec<CompilationOutputEntry>> {
    let started = std::time::Instant::now();
    let mut paths = std::collections::BTreeMap::new();
    let mut visited = 0_usize;
    for (name, root) in roots {
        ensure!(
            !name.is_empty() && name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_'),
            "Unsafe compilation output label"
        );
        checked_location(cache, root)?;
        collect(root, root, name, &mut paths, &mut visited, check)?;
    }
    let mut buffer = vec![0_u8; 64 * 1024];
    let mut entries = Vec::with_capacity(paths.len());
    let mut total = 0_u64;
    for (relative_path, path) in paths {
        check()?;
        let mut file = open_read(&path)?;
        let metadata = file.metadata()?;
        ensure!(
            regular(&metadata) && metadata.len() <= MAX_FILE_BYTES,
            "Unsafe or oversized compilation output"
        );
        ensure!(
            total
                .checked_add(metadata.len())
                .is_some_and(|bytes| bytes <= MAX_TOTAL_BYTES),
            "Compilation outputs are oversized"
        );
        let mut bytes = 0_u64;
        let mut hash = Sha256::new();
        loop {
            check()?;
            let count = file.read(&mut buffer)?;
            if count == 0 {
                break;
            }
            bytes += count as u64;
            ensure!(
                bytes <= metadata.len(),
                "Compilation output grew during inventory"
            );
            hash.update(&buffer[..count]);
        }
        ensure!(
            bytes == metadata.len() && file.metadata()?.len() == bytes,
            "Compilation output changed during inventory"
        );
        total += bytes;
        ensure!(
            total <= MAX_TOTAL_BYTES,
            "Compilation outputs are oversized"
        );
        entries.push(CompilationOutputEntry {
            relative_path,
            bytes,
            sha256: format!("{:x}", hash.finalize()),
        });
    }
    tracing::info!(
        phase = "project_compilation.output_inventory",
        elapsed_us = u64::try_from(started.elapsed().as_micros()).unwrap_or(u64::MAX),
        files = entries.len(),
        bytes = total,
        measurement = "wall_duration",
        "Verified local compilation output inventory"
    );
    Ok(entries)
}

fn collect(
    root: &Path,
    directory: &Path,
    label: &str,
    paths: &mut std::collections::BTreeMap<String, PathBuf>,
    visited: &mut usize,
    check: &impl Fn() -> Result<()>,
) -> Result<()> {
    check()?;
    ensure!(
        directory.strip_prefix(root)?.components().count() <= 64,
        "Compilation output tree is too deep"
    );
    let metadata = fs::symlink_metadata(directory)?;
    #[cfg(windows)]
    ensure!(
        metadata.file_attributes() & 0x400 == 0,
        "Reparse compilation output directory"
    );
    ensure!(
        metadata.is_dir() && !metadata.is_symlink(),
        "Unsafe compilation output directory"
    );
    for entry in fs::read_dir(directory)? {
        check()?;
        *visited += 1;
        ensure!(*visited <= MAX_FILES, "Too many compilation output entries");
        let path = entry?.path();
        let metadata = fs::symlink_metadata(&path)?;
        if metadata.is_dir() {
            collect(root, &path, label, paths, visited, check)?;
        } else {
            ensure!(regular(&metadata), "Unsafe compilation output entry");
            let relative = path
                .strip_prefix(root)?
                .to_str()
                .ok_or_else(|| eyre::eyre!("Non-UTF8 compilation output"))?
                .replace('\\', "/");
            ensure!(
                paths.insert(format!("{label}/{relative}"), path).is_none(),
                "Duplicate compilation output"
            );
            ensure!(paths.len() <= MAX_FILES, "Too many compilation outputs");
        }
    }
    Ok(())
}
