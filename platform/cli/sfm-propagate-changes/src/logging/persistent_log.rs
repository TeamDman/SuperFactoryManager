//! Additive run files; only the small latest pointer is replaced.
use super::LoggingConfig;
use crate::cancellation::CancellationToken;
use crate::file_identity::file_identity;
use chrono::Utc;
use eyre::{Result, WrapErr as _, ensure};
use facet::Facet;
use std::fs::{self, File, OpenOptions};
use std::io::{Read as _, Seek as _, SeekFrom, Write as _};
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

pub(super) struct PreparedLog {
    pub(super) path: PathBuf,
    pub(super) file: File,
}

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct LatestLog {
    schema: String,
    file: String,
    started_utc: String,
    pid: u32,
}

#[cfg(unix)]
use std::os::unix::fs::OpenOptionsExt as _;
#[cfg(windows)]
use std::os::windows::fs::OpenOptionsExt as _;

fn options() -> OpenOptions {
    let mut options = OpenOptions::new();
    #[cfg(unix)]
    options.mode(0o600);
    #[cfg(target_os = "linux")]
    options.custom_flags(0o400_000); // O_NOFOLLOW for the final component.
    #[cfg(windows)]
    options.custom_flags(0x0020_0000); // FILE_FLAG_OPEN_REPARSE_POINT.
    options
}

fn regular(path: &Path, file: &File) -> Result<()> {
    let metadata = fs::symlink_metadata(path)?;
    ensure!(
        metadata.is_file() && !metadata.file_type().is_symlink() && file.metadata()?.is_file(),
        "log path is not a regular file: {}",
        path.display()
    );
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt as _;
        ensure!(
            metadata.file_attributes() & 0x400 == 0
                && file.metadata()?.file_attributes() & 0x400 == 0,
            "log path is a reparse point"
        );
    }
    let current = options().read(true).open(path)?;
    ensure!(
        file_identity(file)? == file_identity(&current)?,
        "log pathname changed: {}",
        path.display()
    );
    Ok(())
}

pub(super) fn lock(file: &File, cancellation: Option<&CancellationToken>) -> std::io::Result<()> {
    let started = Instant::now();
    loop {
        if cancellation.is_some_and(CancellationToken::is_cancelled) {
            return Err(std::io::Error::new(
                std::io::ErrorKind::Interrupted,
                "cancelled while initializing log discovery",
            ));
        }
        match file.try_lock() {
            Ok(()) => return Ok(()),
            Err(std::fs::TryLockError::WouldBlock)
                if started.elapsed() < Duration::from_secs(2) =>
            {
                std::thread::sleep(Duration::from_millis(10));
            }
            Err(error) => return Err(std::io::Error::other(error)),
        }
    }
}

pub(super) fn prepare(
    config: &LoggingConfig,
    cancellation: &CancellationToken,
) -> Result<Option<PreparedLog>> {
    cancellation.bail_if_cancelled()?;
    ensure!(
        config.json_log_path.is_none() || config.json_log_directory.is_none(),
        "conflicting file log destinations"
    );
    if let Some(directory) = &config.json_log_directory {
        return unique(directory, config.private_log_directory, cancellation).map(Some);
    }
    let Some(path) = &config.json_log_path else {
        return Ok(None);
    };
    if let Some(parent) = path.parent().filter(|p| !p.as_os_str().is_empty()) {
        fs::create_dir_all(parent)?;
    }
    // Coordinate an explicit latest.json destination with discovery writers.
    // A normal log there is preserved; a recognized pointer cannot become a log.
    let _metadata_lease = if path.file_name().is_some_and(|n| n == "latest.json") {
        Some(latest_lock(
            path.parent()
                .filter(|p| !p.as_os_str().is_empty())
                .unwrap_or_else(|| Path::new(".")),
            cancellation,
        )?)
    } else {
        None
    };
    let file = options()
        .read(true)
        .append(true)
        .create(true)
        .open(path)
        .wrap_err_with(|| format!("could not append persistent log {}", path.display()))?;
    regular(path, &file)?;
    if file.metadata()?.len() <= 4096 {
        let mut bytes = Vec::new();
        (&file).take(4097).read_to_end(&mut bytes)?;
        let recognized = facet_json::from_slice::<LatestLog>(&bytes)
            .is_ok_and(|p| p.schema == "sfm:run_log_pointer@1" && valid_name(&p.file));
        ensure!(
            !recognized,
            "SFM latest.json discovery metadata cannot be used as an explicit log file"
        );
    }
    lock(&file, Some(cancellation))?;
    regular(path, &file)?;
    // Keep a killed writer's bytes, but do not concatenate a new JSON event
    // onto its incomplete final line. This uses the same record lock.
    if file.metadata()?.len() > 0 {
        let mut reader = &file;
        reader.seek(SeekFrom::End(-1))?;
        let mut last = [0];
        reader.read_exact(&mut last)?;
        if last[0] != b'\n' {
            (&file).write_all(b"\n")?;
        }
    }
    file.unlock()?;
    Ok(Some(PreparedLog {
        path: path.clone(),
        file,
    }))
}

fn prepare_directory(directory: &Path, private: bool) -> Result<()> {
    let mut builder = fs::DirBuilder::new();
    builder.recursive(true);
    #[cfg(unix)]
    if private {
        use std::os::unix::fs::DirBuilderExt as _;
        builder.mode(0o700);
    }
    builder.create(directory)?;
    let metadata = fs::symlink_metadata(directory)?;
    ensure!(
        metadata.is_dir() && !metadata.file_type().is_symlink(),
        "log directory is not a real directory"
    );
    #[cfg(unix)]
    if private {
        use std::os::unix::fs::MetadataExt as _;
        unsafe extern "C" {
            fn geteuid() -> u32;
        }
        // SAFETY: geteuid takes no pointers and reads this process credential.
        let uid = unsafe { geteuid() };
        ensure!(
            metadata.uid() == uid && metadata.mode() & 0o022 == 0,
            "automatic log directory must be user-owned and not group/world writable; existing permissions are preserved"
        );
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt as _;
        ensure!(
            metadata.file_attributes() & 0x400 == 0,
            "log directory is a reparse point"
        );
        let _ = private; // Windows uses the application-home inherited ACL.
    }
    Ok(())
}

fn unique(
    directory: &Path,
    private: bool,
    cancellation: &CancellationToken,
) -> Result<PreparedLog> {
    prepare_directory(directory, private)?;
    let now = Utc::now();
    create_unique(directory, cancellation, || {
        format!(
            "run-{}-{}-{:032x}.ndjson",
            now.format("%Y-%m-%d_%H-%M-%S%.9f"),
            std::process::id(),
            rand::random::<u128>()
        )
    })
}

fn create_unique(
    directory: &Path,
    cancellation: &CancellationToken,
    mut name: impl FnMut() -> String,
) -> Result<PreparedLog> {
    for _ in 0..32 {
        cancellation.bail_if_cancelled()?;
        let filename = name();
        ensure!(valid_name(&filename), "invalid generated run log name");
        let path = directory.join(&filename);
        let file = match options()
            .read(true)
            .append(true)
            .create_new(true)
            .open(&path)
        {
            Ok(file) => file,
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => continue,
            Err(error) => {
                return Err(error)
                    .wrap_err_with(|| format!("could not create run log {}", path.display()));
            }
        };
        regular(&path, &file)?;
        file.sync_all()?;
        publish_latest(directory, &filename, cancellation).wrap_err_with(|| {
            format!(
                "could not update latest.json; allocated run log retained at {}",
                path.display()
            )
        })?;
        return Ok(PreparedLog { path, file });
    }
    Err(eyre::eyre!(
        "run log name collisions exceeded bounded retry limit"
    ))
}

fn valid_name(name: &str) -> bool {
    name.starts_with("run-")
        && name.ends_with(".ndjson")
        && !name.contains("..")
        && name.len() <= 240
        && name
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"-_.".contains(&b))
}

fn latest_lock(directory: &Path, cancellation: &CancellationToken) -> Result<File> {
    let lock_path = directory.join(".latest.lock");
    let lock_file = match options()
        .read(true)
        .write(true)
        .create_new(true)
        .open(&lock_path)
    {
        Ok(file) => file,
        Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {
            options().read(true).write(true).open(&lock_path)?
        }
        Err(error) => return Err(error.into()),
    };
    regular(&lock_path, &lock_file)?;
    lock(&lock_file, Some(cancellation))?;
    regular(&lock_path, &lock_file)?; // The locked inode must still name the shared lock.
    Ok(lock_file)
}

fn publish_latest(
    directory: &Path,
    filename: &str,
    cancellation: &CancellationToken,
) -> Result<()> {
    let lock_file = latest_lock(directory, cancellation)?;
    let latest = directory.join("latest.json");
    let old = match fs::symlink_metadata(&latest) {
        Ok(_) => {
            let file = options().read(true).open(&latest)?;
            regular(&latest, &file)?;
            ensure!(
                file.metadata()?.len() <= 4096,
                "unrecognized latest.json metadata preserved"
            );
            let mut bytes = Vec::new();
            (&file).take(4097).read_to_end(&mut bytes)?;
            // A parser error may quote arbitrary existing private bytes.
            let pointer: LatestLog = match facet_json::from_slice(&bytes) {
                Ok(pointer) => pointer,
                Err(_) => return Err(eyre::eyre!("unrecognized latest.json metadata preserved")),
            };
            ensure!(
                pointer.schema == "sfm:run_log_pointer@1" && valid_name(&pointer.file),
                "unrecognized latest.json metadata preserved"
            );
            Some(file)
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => None,
        Err(error) => return Err(error.into()),
    };
    let stage = directory.join(format!(
        ".latest-{}-{:032x}.json",
        std::process::id(),
        rand::random::<u128>()
    ));
    let mut staged = options()
        .read(true)
        .write(true)
        .create_new(true)
        .open(&stage)?;
    let pointer = LatestLog {
        schema: "sfm:run_log_pointer@1".to_owned(),
        file: filename.to_owned(),
        started_utc: Utc::now().to_rfc3339(),
        pid: std::process::id(),
    };
    writeln!(staged, "{}", facet_json::to_string(&pointer)?)?;
    staged.sync_all()?;
    regular(&stage, &staged)?;
    if let Some(old) = old.as_ref() {
        regular(&latest, old)?;
    }
    cancellation.bail_if_cancelled()?;
    fs::rename(&stage, &latest)?; // Only pointer metadata moves; run files never do.
    #[cfg(unix)]
    File::open(directory)?.sync_all()?;
    lock_file.unlock()?;
    Ok(())
}

#[cfg(test)]
#[path = "persistent_log_tests.rs"]
mod tests;
