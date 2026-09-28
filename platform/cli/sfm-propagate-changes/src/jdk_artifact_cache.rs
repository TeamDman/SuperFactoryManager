//! Checksum-verified, local ZIP cache for exact JBRSDK pins.
//! Runtime selection and activation are deliberately separate from acquisition.

use crate::artifact_lock::ArtifactLock;
use crate::toolchain_lockfile_schema::version::v4::JdkArtifactV4;
use crate::toolchain_lockfile_schema::version::v4::JdkPinV4;
use eyre::Context;
use sha2::Digest;
use sha2::Sha512;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs::File;
use std::fs::OpenOptions;
use std::fs::{self};
use std::io::Read;
use std::io::Seek;
use std::io::Write;
use std::io::{self};
use std::path::Path;
use std::path::PathBuf;
use std::time::Duration;
use zip::ZipArchive;

const MAX_ARCHIVE_BYTES: u64 = 2 * 1024 * 1024 * 1024;
const MAX_EXTRACTED_BYTES: u64 = 8 * 1024 * 1024 * 1024;
const MAX_ZIP_ENTRIES: usize = 100_000;
const RECEIPT_NAME: &str = ".sfm-jbrsdk-receipt";

/// Acquire an exact ZIP artifact in a caller-selected cache root. This does not
/// choose a pin, install a runtime into the user's JDK directory, or run Java.
pub(crate) fn acquire_pinned_jbrsdk_zip(
    pin: &JdkPinV4,
    artifact: &JdkArtifactV4,
    cache_root: &Path,
    offline: bool,
) -> eyre::Result<PathBuf> {
    acquire_pinned_jbrsdk_zip_with(pin, artifact, cache_root, offline, fetch_zip_url)
}

fn acquire_pinned_jbrsdk_zip_with(
    pin: &JdkPinV4,
    artifact: &JdkArtifactV4,
    cache_root: &Path,
    offline: bool,
    fetch: impl FnOnce(&str, &mut File) -> eyre::Result<()>,
) -> eyre::Result<PathBuf> {
    verify_zip_identity(pin, artifact)?;
    let sha = artifact.sha512.to_ascii_lowercase();
    let archive_dir = cache_root.join("archives");
    let install_dir = cache_root.join("installs");
    let lock_dir = cache_root.join("locks");
    ensure_safe_cache_dir(cache_root)?;
    ensure_safe_cache_dir(&archive_dir)?;
    ensure_safe_cache_dir(&install_dir)?;
    ensure_safe_cache_dir(&lock_dir)?;
    let _lock = ArtifactLock::acquire(
        lock_dir.join(format!("{sha}.lock")),
        format!("JBRSDK {} {}", pin.version, artifact.platform),
    )?;

    let archive = archive_dir.join(format!("{sha}.zip"));
    match fs::symlink_metadata(&archive) {
        Ok(metadata) if metadata.file_type().is_file() && !is_reparse_point(&metadata) => (),
        Ok(_) => eyre::bail!(
            "JBRSDK cache archive is not a regular file: {}",
            archive.display()
        ),
        Err(error) if error.kind() == io::ErrorKind::NotFound => {
            if offline {
                eyre::bail!(
                    "JBRSDK ZIP {} is not cached and offline mode forbids downloading it",
                    artifact.url
                );
            }
            let mut temporary = tempfile::Builder::new()
                .prefix(".sfm-jbrsdk-archive.")
                .suffix(".tmp")
                .tempfile_in(&archive_dir)?;
            fetch(&artifact.url, temporary.as_file_mut())?;
            temporary.as_file_mut().flush()?;
            temporary.as_file_mut().sync_all()?;
            verify_archive_sha512(temporary.as_file_mut(), &sha)?;
            match temporary.persist_noclobber(&archive) {
                Ok(_) => (),
                Err(error) if error.error.kind() == io::ErrorKind::AlreadyExists => (),
                Err(error) => return Err(error.error).wrap_err("Failed to publish JBRSDK ZIP"),
            }
        }
        Err(error) => return Err(error).wrap_err("Failed to inspect JBRSDK ZIP cache"),
    }
    // The same file handle is hashed, parsed, and extracted. Reopening after
    // verification would allow replacement of the path between those steps.
    let mut verified_zip = open_verified_archive(&archive, &sha)?;

    let installed = install_dir.join(&sha);
    let receipt = receipt(pin, artifact);
    match fs::symlink_metadata(&installed) {
        Ok(_) => {
            verify_installed_home(&installed, artifact, &receipt, &mut verified_zip)?;
            return Ok(installed);
        }
        Err(error) if error.kind() == io::ErrorKind::NotFound => (),
        Err(error) => return Err(error).wrap_err("Failed to inspect cached JBRSDK installation"),
    }

    let stage = tempfile::Builder::new()
        .prefix(".sfm-jbrsdk-install.")
        .tempdir_in(&install_dir)?;
    let payload = stage.path().join("payload");
    fs::create_dir(&payload)?;
    extract_zip_safely(&mut verified_zip, &payload)?;
    let home = locate_jdk_home(&payload, artifact)?;
    write_receipt(&home, &receipt)?;
    verify_installed_home(&home, artifact, &receipt, &mut verified_zip)?;
    fs::rename(&home, &installed).wrap_err_with(|| {
        format!(
            "Failed to publish JBRSDK installation {}",
            installed.display()
        )
    })?;
    Ok(installed)
}

fn verify_zip_identity(pin: &JdkPinV4, artifact: &JdkArtifactV4) -> eyre::Result<()> {
    if artifact.url.ends_with(".tar.gz") {
        eyre::bail!(
            "JBRSDK tar.gz acquisition is not supported by this ZIP-only cache; an exact ZIP artifact and checksum must be pinned separately"
        );
    }
    let version_parts = pin.version.split('.').collect::<Vec<_>>();
    let build_parts = pin
        .build
        .strip_prefix('b')
        .and_then(|value| value.split_once('.'));
    if pin.major == 0
        || version_parts.len() < 3
        || version_parts
            .iter()
            .any(|part| part.is_empty() || !part.bytes().all(|byte| byte.is_ascii_digit()))
        || version_parts[0].parse::<u32>().ok() != Some(pin.major)
        || !build_parts.is_some_and(|(family, revision)| {
            !family.is_empty()
                && !revision.is_empty()
                && family.bytes().all(|byte| byte.is_ascii_digit())
                && revision.bytes().all(|byte| byte.is_ascii_digit())
        })
        || pin.vendor != "JetBrains"
        || pin.flavor != "jbrsdk"
        || artifact.platform.is_empty()
        || !artifact
            .platform
            .bytes()
            .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'-')
    {
        eyre::bail!("Invalid exact JBRSDK ZIP identity");
    }
    let expected_url = format!(
        "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-{}-{}-{}.zip",
        pin.version, artifact.platform, pin.build
    );
    if artifact.url != expected_url {
        eyre::bail!("JBRSDK ZIP URL does not match the exact pinned SDK identity");
    }
    if artifact.sha512.len() != 128 || !artifact.sha512.bytes().all(|byte| byte.is_ascii_hexdigit())
    {
        eyre::bail!("JBRSDK ZIP needs a 128-character SHA-512 digest");
    }
    Ok(())
}

fn ensure_safe_cache_dir(path: &Path) -> eyre::Result<()> {
    if !path.is_absolute() {
        eyre::bail!("JBRSDK cache path must be absolute: {}", path.display());
    }
    for component_path in path.ancestors().collect::<Vec<_>>().into_iter().rev() {
        match fs::symlink_metadata(component_path) {
            Ok(metadata) => {
                if !metadata.file_type().is_dir() || is_reparse_point(&metadata) {
                    eyre::bail!(
                        "JBRSDK cache directory is not a real directory: {}",
                        component_path.display()
                    );
                }
            }
            Err(error) if error.kind() == io::ErrorKind::NotFound => {
                fs::create_dir(component_path).wrap_err_with(|| {
                    format!(
                        "Failed to create JBRSDK cache directory {}",
                        component_path.display()
                    )
                })?;
            }
            Err(error) => {
                return Err(error).wrap_err_with(|| {
                    format!(
                        "Failed to inspect JBRSDK cache directory {}",
                        component_path.display()
                    )
                });
            }
        }
    }
    Ok(())
}

fn is_reparse_point(metadata: &fs::Metadata) -> bool {
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        metadata.file_attributes() & 0x400 != 0
    }
    #[cfg(not(windows))]
    {
        metadata.file_type().is_symlink()
    }
}

fn fetch_zip_url(url: &str, destination: &mut File) -> eyre::Result<()> {
    let client = reqwest::blocking::Client::builder()
        .user_agent(concat!("sfm-propagate-changes/", env!("CARGO_PKG_VERSION")))
        .connect_timeout(Duration::from_secs(30))
        .timeout(Duration::from_mins(30))
        .build()?;
    let mut response = client.get(url).send()?.error_for_status()?;
    let mut buffer = vec![0_u8; 128 * 1024].into_boxed_slice();
    let mut size = 0_u64;
    loop {
        let read = response.read(&mut buffer)?;
        if read == 0 {
            return Ok(());
        }
        size = size
            .checked_add(read as u64)
            .ok_or_else(|| eyre::eyre!("JBRSDK ZIP is too large"))?;
        if size > MAX_ARCHIVE_BYTES {
            eyre::bail!("JBRSDK ZIP exceeds the 2 GiB download limit");
        }
        destination.write_all(&buffer[..read])?;
    }
}

fn open_verified_archive(path: &Path, expected: &str) -> eyre::Result<ZipArchive<File>> {
    let metadata = fs::symlink_metadata(path)?;
    if !metadata.file_type().is_file() || is_reparse_point(&metadata) {
        eyre::bail!("JBRSDK ZIP is not a regular file: {}", path.display());
    }
    let mut options = OpenOptions::new();
    options.read(true);
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt;
        // Hold the verified archive against concurrent writes or replacement.
        options.share_mode(0x1) // FILE_SHARE_READ
    };
    let mut file = options.open(path)?;
    verify_archive_sha512(&mut file, expected)
        .map_err(|error| eyre::eyre!("Failed to verify JBRSDK ZIP {}: {error}", path.display()))?;
    file.rewind()?;
    Ok(ZipArchive::new(file)?)
}

fn verify_archive_sha512(file: &mut File, expected: &str) -> eyre::Result<()> {
    let metadata = file.metadata()?;
    if !metadata.file_type().is_file() || metadata.len() > MAX_ARCHIVE_BYTES {
        eyre::bail!("JBRSDK ZIP is not a regular file within the download limit");
    }
    file.rewind()?;
    let mut digest = Sha512::new();
    let mut buffer = vec![0_u8; 128 * 1024].into_boxed_slice();
    loop {
        let read = file.read(&mut buffer)?;
        if read == 0 {
            break;
        }
        digest.update(&buffer[..read]);
    }
    if format!("{:x}", digest.finalize()) != expected {
        eyre::bail!("JBRSDK ZIP SHA-512 mismatch");
    }
    Ok(())
}

fn extract_zip_safely(zip: &mut ZipArchive<File>, destination: &Path) -> eyre::Result<()> {
    if zip.len() > MAX_ZIP_ENTRIES {
        eyre::bail!("JBRSDK ZIP has too many entries");
    }
    let mut seen = BTreeSet::new();
    let mut extracted_bytes = 0_u64;
    for index in 0..zip.len() {
        let mut entry = zip.by_index(index)?;
        let (relative, key) = safe_zip_name(entry.name())?;
        if entry.enclosed_name().is_none() || !seen.insert(key) {
            eyre::bail!(
                "JBRSDK ZIP has an unsafe or duplicate path: {}",
                entry.name()
            );
        }
        if entry.is_symlink()
            || entry.unix_mode().is_some_and(|mode| {
                let kind = mode & 0o170_000;
                kind != 0 && kind != if entry.is_dir() { 0o040_000 } else { 0o100_000 }
            })
        {
            eyre::bail!("JBRSDK ZIP contains a non-file entry: {}", entry.name());
        }
        let output = destination.join(relative);
        if entry.is_dir() {
            fs::create_dir_all(&output)?;
            continue;
        }
        let remaining = MAX_EXTRACTED_BYTES.saturating_sub(extracted_bytes);
        if entry.size() > remaining {
            eyre::bail!("JBRSDK ZIP exceeds the 8 GiB extraction limit");
        }
        if let Some(parent) = output.parent() {
            fs::create_dir_all(parent)?;
        }
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&output)?;
        let copied = io::copy(&mut (&mut entry).take(remaining + 1), &mut file)?;
        if copied != entry.size() {
            eyre::bail!("JBRSDK ZIP entry has an invalid size: {}", entry.name());
        }
        extracted_bytes += copied;
        file.sync_all()?;
        #[cfg(unix)]
        if let Some(mode) = entry.unix_mode() {
            use std::os::unix::fs::PermissionsExt;
            let permissions = mode & 0o777;
            if permissions != 0 {
                fs::set_permissions(&output, fs::Permissions::from_mode(permissions))?;
            }
        }
    }
    Ok(())
}

fn safe_zip_name(name: &str) -> eyre::Result<(PathBuf, String)> {
    let name = name.strip_suffix('/').unwrap_or(name);
    if name.is_empty() || name.len() > 4096 || name.contains('\\') {
        eyre::bail!("Unsafe JBRSDK ZIP path: {name}");
    }
    let mut path = PathBuf::new();
    let mut normalized = Vec::new();
    for component in name.split('/') {
        let stem = component.split('.').next().unwrap_or_default();
        let upper = stem.to_ascii_uppercase();
        let reserved = matches!(upper.as_str(), "CON" | "PRN" | "AUX" | "NUL")
            || (upper.len() == 4
                && (upper.starts_with("COM") || upper.starts_with("LPT"))
                && upper.as_bytes()[3].is_ascii_digit()
                && upper.as_bytes()[3] != b'0');
        if component.is_empty()
            || component == "."
            || component == ".."
            || component.ends_with([' ', '.'])
            || component
                .chars()
                .any(|ch| ch <= '\u{1f}' || "<>:\"|?*".contains(ch))
            || reserved
        {
            eyre::bail!("Unsafe JBRSDK ZIP path: {name}");
        }
        path.push(component);
        normalized.push(component.to_lowercase());
    }
    Ok((path, normalized.join("/")))
}

fn locate_jdk_home(payload: &Path, artifact: &JdkArtifactV4) -> eyre::Result<PathBuf> {
    if has_jdk_executables(payload, artifact) {
        return Ok(payload.to_path_buf());
    }
    let children = fs::read_dir(payload)?.collect::<Result<Vec<_>, _>>()?;
    if children.len() == 1 && children[0].file_type()?.is_dir() {
        let home = children[0].path();
        if has_jdk_executables(&home, artifact) {
            return Ok(home);
        }
    }
    eyre::bail!("JBRSDK ZIP does not contain a single SDK home with java and javac")
}

fn has_jdk_executables(home: &Path, artifact: &JdkArtifactV4) -> bool {
    let suffix = if artifact.platform.starts_with("windows-") {
        ".exe"
    } else {
        ""
    };
    is_real_dir(&home.join("bin"))
        && is_real_file(&home.join("bin").join(format!("java{suffix}")))
        && is_real_file(&home.join("bin").join(format!("javac{suffix}")))
}

fn is_real_dir(path: &Path) -> bool {
    fs::symlink_metadata(path)
        .is_ok_and(|metadata| metadata.file_type().is_dir() && !is_reparse_point(&metadata))
}

fn is_real_file(path: &Path) -> bool {
    fs::symlink_metadata(path)
        .is_ok_and(|metadata| metadata.file_type().is_file() && !is_reparse_point(&metadata))
}

fn receipt(pin: &JdkPinV4, artifact: &JdkArtifactV4) -> String {
    format!(
        "sfm-jbrsdk-cache-v1\nmajor={}\nvendor={}\nversion={}\nbuild={}\nflavor={}\nplatform={}\nurl={}\nsha512={}\n",
        pin.major,
        pin.vendor,
        pin.version,
        pin.build,
        pin.flavor,
        artifact.platform,
        artifact.url,
        artifact.sha512.to_ascii_lowercase()
    )
}

fn write_receipt(home: &Path, expected: &str) -> eyre::Result<()> {
    let path = home.join(RECEIPT_NAME);
    let mut file = OpenOptions::new().write(true).create_new(true).open(path)?;
    file.write_all(expected.as_bytes())?;
    file.sync_all()?;
    Ok(())
}

fn verify_installed_home(
    home: &Path,
    artifact: &JdkArtifactV4,
    expected: &str,
    zip: &mut ZipArchive<File>,
) -> eyre::Result<()> {
    if !is_real_dir(home) || !has_jdk_executables(home, artifact) {
        eyre::bail!(
            "Cached JBRSDK installation is incomplete or unsafe: {}",
            home.display()
        );
    }
    let receipt_path = home.join(RECEIPT_NAME);
    if !is_real_file(&receipt_path) || fs::read_to_string(&receipt_path)? != expected {
        eyre::bail!(
            "Cached JBRSDK installation has no matching checksum receipt: {}",
            home.display()
        );
    }
    verify_install_matches_zip(home, artifact, zip)?;
    Ok(())
}

/// The receipt binds an installation to a pin, but it cannot authenticate the
/// installed files: both live in the writable cache. Compare every file to the
/// ZIP that was SHA-512 verified on the same open handle instead.
fn verify_install_matches_zip(
    home: &Path,
    artifact: &JdkArtifactV4,
    zip: &mut ZipArchive<File>,
) -> eyre::Result<()> {
    let prefix = archive_home_prefix(zip, artifact)?;
    let mut expected = BTreeMap::<PathBuf, bool>::new();
    expected.insert(PathBuf::from(RECEIPT_NAME), true);
    let mut extracted_bytes = 0_u64;
    for index in 0..zip.len() {
        let mut entry = zip.by_index(index)?;
        let (archived_path, _) = safe_zip_name(entry.name())?;
        let relative = archived_path.strip_prefix(&prefix).wrap_err_with(|| {
            format!(
                "JBRSDK ZIP has content outside its SDK home: {}",
                entry.name()
            )
        })?;
        if relative.as_os_str().is_empty() {
            if !entry.is_dir() {
                eyre::bail!("JBRSDK ZIP SDK home is not a directory");
            }
            continue;
        }
        if entry.enclosed_name().is_none() || entry.is_symlink() {
            eyre::bail!("JBRSDK ZIP has an unsafe entry: {}", entry.name());
        }
        record_expected_path(&mut expected, relative, !entry.is_dir())?;
        if entry.is_dir() {
            continue;
        }
        extracted_bytes = extracted_bytes
            .checked_add(entry.size())
            .filter(|size| *size <= MAX_EXTRACTED_BYTES)
            .ok_or_else(|| eyre::eyre!("JBRSDK ZIP exceeds the 8 GiB extraction limit"))?;
        compare_installed_file(&mut entry, &home.join(relative))?;
    }
    let actual = installed_paths(home, expected.len())?;
    if actual != expected {
        eyre::bail!(
            "Cached JBRSDK installation does not match verified ZIP: {}",
            home.display()
        );
    }
    Ok(())
}

fn archive_home_prefix(
    zip: &mut ZipArchive<File>,
    artifact: &JdkArtifactV4,
) -> eyre::Result<PathBuf> {
    let java_name = if artifact.platform.starts_with("windows-") {
        "java.exe"
    } else {
        "java"
    };
    let mut prefix = None;
    for index in 0..zip.len() {
        let entry = zip.by_index(index)?;
        if entry.is_dir() {
            continue;
        }
        let (path, _) = safe_zip_name(entry.name())?;
        if path.file_name().is_some_and(|name| name == java_name)
            && path
                .parent()
                .and_then(Path::file_name)
                .is_some_and(|name| name == "bin")
        {
            let candidate = path
                .parent()
                .and_then(Path::parent)
                .unwrap_or_else(|| Path::new(""));
            if prefix.replace(candidate.to_path_buf()).is_some() {
                eyre::bail!("JBRSDK ZIP contains more than one SDK home");
            }
        }
    }
    prefix.ok_or_else(|| eyre::eyre!("JBRSDK ZIP has no SDK home"))
}

fn record_expected_path(
    expected: &mut BTreeMap<PathBuf, bool>,
    relative: &Path,
    file: bool,
) -> eyre::Result<()> {
    match expected.get(relative) {
        Some(false) if !file => (), // An implicit parent may precede a ZIP directory entry.
        Some(_) => eyre::bail!("JBRSDK ZIP contains a duplicate installed path"),
        None => {
            expected.insert(relative.to_path_buf(), file);
        }
    }
    let mut parent = relative.parent();
    while let Some(path) = parent {
        if path.as_os_str().is_empty() {
            break;
        }
        if expected.insert(path.to_path_buf(), false) == Some(true) {
            eyre::bail!("JBRSDK ZIP contains a file/directory collision");
        }
        parent = path.parent();
    }
    Ok(())
}

fn compare_installed_file(entry: &mut zip::read::ZipFile<'_>, path: &Path) -> eyre::Result<()> {
    let mismatch = || {
        format!(
            "Cached JBRSDK installation does not match verified ZIP: {}",
            path.display()
        )
    };
    let metadata = fs::symlink_metadata(path).wrap_err_with(mismatch)?;
    if !metadata.file_type().is_file()
        || is_reparse_point(&metadata)
        || metadata.len() != entry.size()
    {
        eyre::bail!("{}", mismatch());
    }
    let mut file = File::open(path).wrap_err_with(mismatch)?;
    let mut archived = vec![0_u8; 128 * 1024].into_boxed_slice();
    let mut installed = vec![0_u8; 128 * 1024].into_boxed_slice();
    let mut remaining = entry.size();
    while remaining > 0 {
        let len = usize::try_from(remaining)
            .unwrap_or(archived.len())
            .min(archived.len());
        entry
            .read_exact(&mut archived[..len])
            .wrap_err_with(mismatch)?;
        file.read_exact(&mut installed[..len])
            .wrap_err_with(mismatch)?;
        if archived[..len] != installed[..len] {
            eyre::bail!("{}", mismatch());
        }
        remaining -= len as u64;
    }
    let mut one = [0_u8; 1];
    if entry.read(&mut one).wrap_err_with(mismatch)? != 0
        || file.read(&mut one).wrap_err_with(mismatch)? != 0
    {
        eyre::bail!("{}", mismatch());
    }
    Ok(())
}

fn installed_paths(home: &Path, maximum: usize) -> eyre::Result<BTreeMap<PathBuf, bool>> {
    let mut paths = BTreeMap::new();
    let mut pending = vec![home.to_path_buf()];
    while let Some(directory) = pending.pop() {
        for child in fs::read_dir(&directory)? {
            let child = child?;
            let path = child.path();
            let metadata = fs::symlink_metadata(&path)?;
            if is_reparse_point(&metadata)
                || !(metadata.file_type().is_file() || metadata.file_type().is_dir())
            {
                eyre::bail!(
                    "Cached JBRSDK installation contains an unsafe path: {}",
                    path.display()
                );
            }
            let relative = path.strip_prefix(home)?.to_path_buf();
            paths.insert(relative, metadata.file_type().is_file());
            if paths.len() > maximum {
                eyre::bail!("Cached JBRSDK installation has unexpected extra paths");
            }
            if metadata.file_type().is_dir() {
                pending.push(path);
            }
        }
    }
    Ok(paths)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::jdk::JdkSource;
    use crate::jdk::select_jdk_source;
    use std::io::Cursor;
    use zip::ZipWriter;
    use zip::write::SimpleFileOptions;

    fn zip_bytes(entries: &[(&str, &[u8])]) -> Vec<u8> {
        let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
        for (name, contents) in entries {
            writer
                .start_file(*name, SimpleFileOptions::default())
                .unwrap();
            writer.write_all(contents).unwrap();
        }
        writer.finish().unwrap().into_inner()
    }

    fn sdk_zip() -> Vec<u8> {
        zip_bytes(&[
            ("sdk/bin/java.exe", b"java"),
            ("sdk/bin/javac.exe", b"javac"),
            ("sdk/lib/modules", b"runtime modules"),
        ])
    }

    fn pin_for(bytes: &[u8]) -> JdkPinV4 {
        JdkPinV4 {
            major: 17,
            vendor: "JetBrains".to_owned(),
            version: "17.0.14".to_owned(),
            build: "b1367.22".to_owned(),
            flavor: "jbrsdk".to_owned(),
            artifacts: vec![JdkArtifactV4 {
                platform: "windows-x64".to_owned(),
                url: "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.14-windows-x64-b1367.22.zip".to_owned(),
                sha512: format!("{:x}", Sha512::digest(bytes)),
            }],
        }
    }

    #[test]
    fn verified_zip_publishes_once_and_offline_reuses_exact_cache() {
        let bytes = sdk_zip();
        let pin = pin_for(&bytes);
        let root = tempfile::tempdir().unwrap();
        let home = acquire_pinned_jbrsdk_zip_with(
            &pin,
            &pin.artifacts[0],
            root.path(),
            false,
            |_, file| {
                file.write_all(&bytes)?;
                Ok(())
            },
        )
        .unwrap();
        assert_eq!(fs::read(home.join("bin/java.exe")).unwrap(), b"java");
        let reused =
            acquire_pinned_jbrsdk_zip_with(&pin, &pin.artifacts[0], root.path(), true, |_, _| {
                panic!("offline cache hit must not fetch")
            })
            .unwrap();
        assert_eq!(reused, home);
    }

    #[test]
    fn modified_cached_java_is_not_reused_offline() {
        let bytes = sdk_zip();
        let pin = pin_for(&bytes);
        let root = tempfile::tempdir().unwrap();
        let home = acquire_pinned_jbrsdk_zip_with(
            &pin,
            &pin.artifacts[0],
            root.path(),
            false,
            |_, file| {
                file.write_all(&bytes)?;
                Ok(())
            },
        )
        .unwrap();
        fs::write(home.join("bin/java.exe"), b"evil").unwrap();
        let error =
            acquire_pinned_jbrsdk_zip_with(&pin, &pin.artifacts[0], root.path(), true, |_, _| {
                panic!("offline cache hit must not fetch")
            })
            .unwrap_err();
        assert!(error.to_string().contains("does not match verified ZIP"));
    }

    #[test]
    fn modified_cached_modules_are_not_reused_offline() {
        let bytes = sdk_zip();
        let pin = pin_for(&bytes);
        let root = tempfile::tempdir().unwrap();
        let home = acquire_pinned_jbrsdk_zip_with(
            &pin,
            &pin.artifacts[0],
            root.path(),
            false,
            |_, file| {
                file.write_all(&bytes)?;
                Ok(())
            },
        )
        .unwrap();
        fs::write(home.join("lib/modules"), b"altered modules").unwrap();
        let error =
            acquire_pinned_jbrsdk_zip_with(&pin, &pin.artifacts[0], root.path(), true, |_, _| {
                panic!("offline cache hit must not fetch")
            })
            .unwrap_err();
        assert!(error.to_string().contains("does not match verified ZIP"));
    }

    #[test]
    fn unexpected_cached_library_is_not_reused_offline() {
        let bytes = sdk_zip();
        let pin = pin_for(&bytes);
        let root = tempfile::tempdir().unwrap();
        let home = acquire_pinned_jbrsdk_zip_with(
            &pin,
            &pin.artifacts[0],
            root.path(),
            false,
            |_, file| {
                file.write_all(&bytes)?;
                Ok(())
            },
        )
        .unwrap();
        fs::write(home.join("lib/extra.dll"), b"unexpected library").unwrap();
        let error =
            acquire_pinned_jbrsdk_zip_with(&pin, &pin.artifacts[0], root.path(), true, |_, _| {
                panic!("offline cache hit must not fetch")
            })
            .unwrap_err();
        assert!(error.to_string().contains("unexpected extra paths"));
    }

    #[test]
    fn offline_missing_zip_never_fetches_or_publishes() {
        let bytes = sdk_zip();
        let pin = pin_for(&bytes);
        let root = tempfile::tempdir().unwrap();
        let error =
            acquire_pinned_jbrsdk_zip_with(&pin, &pin.artifacts[0], root.path(), true, |_, _| {
                panic!("offline mode must not fetch")
            })
            .unwrap_err();
        assert!(
            error
                .to_string()
                .contains("offline mode forbids downloading")
        );
        assert!(
            !root
                .path()
                .join("archives")
                .join(format!("{}.zip", pin.artifacts[0].sha512))
                .exists()
        );
    }

    #[test]
    fn corrupt_download_and_cached_archive_fail_closed() {
        let bytes = sdk_zip();
        let pin = pin_for(&bytes);
        let root = tempfile::tempdir().unwrap();
        let error = acquire_pinned_jbrsdk_zip_with(
            &pin,
            &pin.artifacts[0],
            root.path(),
            false,
            |_, file| {
                file.write_all(b"wrong archive")?;
                Ok(())
            },
        )
        .unwrap_err();
        assert!(error.to_string().contains("SHA-512 mismatch"));
        let archive = root
            .path()
            .join("archives")
            .join(format!("{}.zip", pin.artifacts[0].sha512));
        assert!(!archive.exists());
        fs::write(&archive, b"corrupt cache").unwrap();
        let error =
            acquire_pinned_jbrsdk_zip_with(&pin, &pin.artifacts[0], root.path(), false, |_, _| {
                panic!("corrupt cache must fail, not silently refetch")
            })
            .unwrap_err();
        assert!(error.to_string().contains("SHA-512 mismatch"));
        assert!(
            !root
                .path()
                .join("installs")
                .join(&pin.artifacts[0].sha512)
                .exists()
        );
    }

    #[test]
    fn unsafe_zip_does_not_publish_partial_installation() {
        for name in [
            "../escape",
            "sdk/../../escape",
            "sdk/C:/escape",
            "sdk/bin/CON",
        ] {
            let bytes = zip_bytes(&[
                ("sdk/bin/java.exe", b"java"),
                ("sdk/bin/javac.exe", b"javac"),
                (name, b"bad"),
            ]);
            let pin = pin_for(&bytes);
            let root = tempfile::tempdir().unwrap();
            let error = acquire_pinned_jbrsdk_zip_with(
                &pin,
                &pin.artifacts[0],
                root.path(),
                false,
                |_, file| {
                    file.write_all(&bytes)?;
                    Ok(())
                },
            )
            .unwrap_err();
            assert!(
                error.to_string().contains("Unsafe JBRSDK ZIP path"),
                "{error:?}"
            );
            assert!(
                !root
                    .path()
                    .join("installs")
                    .join(&pin.artifacts[0].sha512)
                    .exists()
            );
        }
    }

    #[test]
    fn missing_platform_and_tarball_do_not_float_to_another_artifact() {
        let bytes = sdk_zip();
        let mut pin = pin_for(&bytes);
        let pins = vec![pin.clone()];
        assert!(
            select_jdk_source(None, Some(&pins), 17, "linux-x64")
                .unwrap_err()
                .to_string()
                .contains("No Java 17 JBRSDK artifact is pinned")
        );
        let JdkSource::Pinned { .. } =
            select_jdk_source(None, Some(&pins), 17, "windows-x64").unwrap()
        else {
            panic!("expected exact Windows artifact")
        };
        pin.artifacts[0].url = pin.artifacts[0].url.replace(".zip", ".tar.gz");
        let root = tempfile::tempdir().unwrap();
        let error =
            acquire_pinned_jbrsdk_zip_with(&pin, &pin.artifacts[0], root.path(), false, |_, _| {
                panic!("unsupported tarball must not fetch")
            })
            .unwrap_err();
        assert!(
            error
                .to_string()
                .contains("tar.gz acquisition is not supported")
        );
    }

    #[test]
    fn preexisting_non_directory_cache_component_is_rejected() {
        let bytes = sdk_zip();
        let pin = pin_for(&bytes);
        let root = tempfile::tempdir().unwrap();
        fs::write(root.path().join("archives"), b"not a directory").unwrap();
        let error =
            acquire_pinned_jbrsdk_zip_with(&pin, &pin.artifacts[0], root.path(), false, |_, _| {
                panic!("unsafe cache path must not fetch")
            })
            .unwrap_err();
        assert!(error.to_string().contains("not a real directory"));
        assert!(!root.path().join("installs").exists());
    }

    #[test]
    fn preexisting_symlinked_cache_component_is_rejected_when_supported() {
        let bytes = sdk_zip();
        let pin = pin_for(&bytes);
        let root = tempfile::tempdir().unwrap();
        let outside = tempfile::tempdir().unwrap();
        #[cfg(windows)]
        let link = std::os::windows::fs::symlink_dir(outside.path(), root.path().join("archives"));
        #[cfg(unix)]
        let link = std::os::unix::fs::symlink(outside.path(), root.path().join("archives"));
        if let Err(error) = link {
            #[cfg(windows)]
            if error.kind() == io::ErrorKind::PermissionDenied || error.raw_os_error() == Some(1314)
            {
                return; // Creating symlinks can require Developer Mode or a privilege.
            }
            panic!("failed to create test symlink: {error}");
        }
        let error =
            acquire_pinned_jbrsdk_zip_with(&pin, &pin.artifacts[0], root.path(), false, |_, _| {
                panic!("symlinked cache path must not fetch")
            })
            .unwrap_err();
        assert!(error.to_string().contains("not a real directory"));
        assert_eq!(fs::read_dir(outside.path()).unwrap().count(), 0);
    }

    #[test]
    #[ignore = "requires a separately downloaded, checksum-verified official JBRSDK17 ZIP"]
    fn official_jbrsdk17_zip_round_trips_through_cache() {
        let source = std::env::var_os("SFM_JBRSDK17_ZIP_TEST_ARCHIVE")
            .map(PathBuf::from)
            .expect("set SFM_JBRSDK17_ZIP_TEST_ARCHIVE to the official ZIP path");
        let pin = JdkPinV4 {
            major: 17,
            vendor: "JetBrains".to_owned(),
            version: "17.0.14".to_owned(),
            build: "b1367.22".to_owned(),
            flavor: "jbrsdk".to_owned(),
            artifacts: vec![JdkArtifactV4 {
                platform: "windows-x64".to_owned(),
                url: "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.14-windows-x64-b1367.22.zip".to_owned(),
                sha512: "3b5101101a46778c5b1cc572adef03bffceb338284d4e03d2fe70a72fb52fb2e1ee1d2e1c0b548c86e9092f776d3ca910669326531d1a5f8ab4521ad1b6d868c".to_owned(),
            }],
        };
        let root = tempfile::tempdir().unwrap();
        let home = acquire_pinned_jbrsdk_zip_with(
            &pin,
            &pin.artifacts[0],
            root.path(),
            false,
            |_, destination| {
                io::copy(&mut File::open(&source)?, destination)?;
                Ok(())
            },
        )
        .unwrap();
        assert!(home.join("bin/java.exe").is_file());
        assert!(home.join("bin/javac.exe").is_file());
        let resolved = crate::jdk::resolve_java_for_lockfile(
            None,
            Some(&[pin.clone()]),
            17,
            root.path(),
            true,
        )
        .unwrap();
        assert_eq!(resolved.major_version, 17);
        assert!(resolved.version_output.contains("JBR-17.0.14+1-1367.22"));
        assert_eq!(resolved.selection, "lockfile-pin");
        assert_eq!(
            resolved.pin_url.as_deref(),
            Some(pin.artifacts[0].url.as_str())
        );
        assert_eq!(
            resolved.pin_sha512.as_deref(),
            Some(pin.artifacts[0].sha512.as_str())
        );
    }
}
