//! Invocation-held explicit SDK identity for the NFRT process host.
//! Same four execution inputs as named Compile, now held against replacement.
//! Pin acquisition remains the existing JDK resolver's responsibility.

use super::core_input_leases::DirectoryLeases;
use super::nfrt_file_open::open_read;
use super::nfrt_file_open::regular;
use crate::file_identity::FileIdentity;
use crate::file_identity::file_identity;
use crate::jar_build::nfrt_launch_contract::PreparedNfrtInvocationInput;
use crate::jar_build::nfrt_preparation_phase::NfrtPreparationPhase;
use crate::jdk::ResolvedJava;
use eyre::Result;
use eyre::ensure;
use sha2::Digest;
use sha2::Sha256;
use std::fmt::Write as _;
use std::fs::File;
use std::io::Read;
use std::io::Seek;
use std::io::SeekFrom;
use std::path::Path;
use std::path::PathBuf;

struct SdkFile {
    file: File,
    identity: FileIdentity,
    bytes: u64,
    digest: String,
    path: PathBuf,
}

pub(crate) struct NfrtToolSdk {
    executable: PathBuf,
    files: Vec<SdkFile>,
    identity: String,
    _ancestry: DirectoryLeases,
}

impl NfrtToolSdk {
    pub(crate) fn capture(
        selected: &ResolvedJava,
        prepared: &PreparedNfrtInvocationInput,
    ) -> Result<Self> {
        let mut observation = NfrtPreparationPhase::start("sdk.capture");
        let sdk = &prepared.receipt().tool_sdk;
        ensure!(
            selected.major_version == sdk.major
                && selected.major_version >= u32::from(prepared.receipt().minimum_tool_jvm)
                && selected.pin_url == sdk.original_archive_url
                && selected.pin_sha512 == sdk.original_archive_sha512,
            "Actual SDK selection differs from the prepared invocation"
        );
        let home = selected
            .home
            .as_deref()
            .ok_or_else(|| eyre::eyre!("NFRT requires an explicit SDK home"))?;
        ensure!(home.is_absolute(), "SDK home is not absolute");
        let mut ancestry = DirectoryLeases::default();
        let mut files = Vec::new();
        let mut evidence = format!(
            "sfm:nfrt_held_sdk@1\n{}\n{}\n",
            home.display(),
            selected.major_version
        );
        let java = if cfg!(windows) {
            "bin/java.exe"
        } else {
            "bin/java"
        };
        let javac = if cfg!(windows) {
            "bin/javac.exe"
        } else {
            "bin/javac"
        };
        for relative in [java, javac, "release", "lib/modules"] {
            let path = ancestry.prepare(home, relative)?;
            let mut file = open_read(&path)?;
            let metadata = file.metadata()?;
            ensure!(
                metadata.is_file()
                    && metadata.len() > 0
                    && metadata.len() <= 1024 * 1024 * 1024
                    && regular(&metadata),
                "SDK execution input is nonregular, empty, oversized or a reparse point"
            );
            let identity = file_identity(&file)?;
            let digest = digest_file(&mut file, metadata.len())?;
            if relative == java {
                ensure!(
                    path == selected.executable
                        && metadata.len() == sdk.supplied_executable_bytes as u64
                        && digest == sdk.supplied_executable_sha256,
                    "Actual Java executable changed after transfer preparation"
                );
            }
            if relative == "release" {
                ensure!(
                    metadata.len() <= 128 * 1024,
                    "Oversized SDK release metadata"
                );
                file.seek(SeekFrom::Start(0))?;
                let mut text = String::new();
                file.read_to_string(&mut text)?;
                let version = text
                    .lines()
                    .find_map(|line| line.strip_prefix("JAVA_VERSION=\""))
                    .ok_or_else(|| eyre::eyre!("Missing SDK JAVA_VERSION"))?;
                let major = version
                    .split('.')
                    .next()
                    .ok_or_else(|| eyre::eyre!("Missing SDK major"))?
                    .parse::<u32>()?;
                ensure!(
                    major == sdk.major,
                    "SDK release metadata is another Java major"
                );
            }
            writeln!(evidence, "{relative}={digest}")?;
            files.push(SdkFile {
                path,
                file,
                identity,
                bytes: metadata.len(),
                digest,
            });
        }
        observation.work(
            files.len() as u64,
            files.iter().map(|entry: &SdkFile| entry.bytes).sum(),
        );
        observation.success();
        Ok(Self {
            executable: selected.executable.clone(),
            files,
            identity: crate::source_projection::provenance::sha256(evidence.as_bytes()),
            _ancestry: ancestry,
        })
    }

    pub(crate) fn executable(&self) -> &Path {
        &self.executable
    }
    pub(crate) fn identity(&self) -> &str {
        &self.identity
    }

    pub(crate) fn recheck(&mut self) -> Result<()> {
        let mut observation = NfrtPreparationPhase::start("sdk.recheck");
        for entry in &mut self.files {
            let current = open_read(&entry.path)?;
            let current_metadata = current.metadata()?;
            let retained_metadata = entry.file.metadata()?;
            ensure!(
                file_identity(&current)? == entry.identity
                    && file_identity(&entry.file)? == entry.identity
                    && regular(&current_metadata)
                    && regular(&retained_metadata)
                    && current_metadata.len() == entry.bytes
                    && retained_metadata.len() == entry.bytes,
                "SDK execution input changed while invocation was live"
            );
            // Windows open_read retains FILE_SHARE_READ only: existing writers
            // and writable mappings prevent capture, and later write/delete
            // opens are denied until these files and their ancestry are dropped.
            // Linux has no mandatory exclusion and must revalidate content.
            if !cfg!(windows) {
                ensure!(
                    digest_file(&mut entry.file, entry.bytes)? == entry.digest,
                    "SDK execution input changed while invocation was live"
                );
            }
        }
        let input_bytes = self.files.iter().map(|entry| entry.bytes).sum::<u64>();
        tracing::info!(
            phase = "sdk.recheck",
            retained_files = self.files.len(),
            input_bytes,
            content_hash_bytes = if cfg!(windows) { 0 } else { input_bytes },
            verification = if cfg!(windows) {
                "retained_windows_lease_identity"
            } else {
                "full_content_digest"
            },
            "NFRT SDK verification work"
        );
        observation.work(self.files.len() as u64, input_bytes);
        observation.success();
        Ok(())
    }
}

#[cfg(test)]
std::thread_local! {
    static SDK_DIGEST_CALLS: std::cell::Cell<usize> = const { std::cell::Cell::new(0) };
}

fn digest_file(file: &mut File, expected: u64) -> Result<String> {
    #[cfg(test)]
    SDK_DIGEST_CALLS.with(|calls| calls.set(calls.get() + 1));
    let mut observation = NfrtPreparationPhase::start("sdk.digest_file");
    file.seek(SeekFrom::Start(0))?;
    let mut hash = Sha256::new();
    let mut total = 0u64;
    let mut buffer = vec![0u8; 64 * 1024];
    loop {
        let count = file.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        total += count as u64;
        ensure!(total <= expected, "SDK input grew during hashing");
        hash.update(&buffer[..count]);
    }
    ensure!(total == expected, "SDK input size changed during hashing");
    let digest = format!("sha256:{:x}", hash.finalize());
    observation.work(1, total);
    observation.success();
    Ok(digest)
}

#[cfg(all(test, windows))]
mod tests {
    use super::*;
    use std::fs;
    use std::os::windows::fs::OpenOptionsExt as _;

    fn write_fixture(home: &Path) -> Result<()> {
        fs::create_dir_all(home.join("bin"))?;
        fs::create_dir_all(home.join("lib"))?;
        for (relative, bytes) in [
            ("bin/java.exe", b"synthetic java".as_slice()),
            ("bin/javac.exe", b"synthetic javac".as_slice()),
            ("release", b"JAVA_VERSION=\"17.0.1\"\n".as_slice()),
            ("lib/modules", b"synthetic modules".as_slice()),
        ] {
            fs::write(home.join(relative), bytes)?;
        }
        Ok(())
    }

    // This private constructor supplies synthetic bytes, never executes a JDK,
    // and exercises the real SDK recheck and actual production open/lease code.
    fn held_fixture(home: &Path) -> Result<NfrtToolSdk> {
        let mut ancestry = DirectoryLeases::default();
        let mut files = Vec::new();
        let mut evidence = format!("sfm:nfrt_held_sdk@1\n{}\n17\n", home.display());
        for relative in ["bin/java.exe", "bin/javac.exe", "release", "lib/modules"] {
            let path = ancestry.prepare(home, relative)?;
            let mut file = open_read(&path)?;
            let bytes = file.metadata()?.len();
            let identity = file_identity(&file)?;
            let digest = digest_file(&mut file, bytes)?;
            writeln!(evidence, "{relative}={digest}")?;
            files.push(SdkFile {
                file,
                identity,
                bytes,
                digest,
                path,
            });
        }
        Ok(NfrtToolSdk {
            executable: home.join("bin/java.exe"),
            files,
            identity: crate::source_projection::provenance::sha256(evidence.as_bytes()),
            _ancestry: ancestry,
        })
    }

    #[test]
    fn windows_sdk_rechecks_reuse_initial_hashes_while_writers_and_rename_are_denied() -> Result<()>
    {
        let temp = tempfile::tempdir()?;
        let home = temp.path().join("sdk");
        write_fixture(&home)?;
        SDK_DIGEST_CALLS.with(|calls| calls.set(0));
        let mut sdk = held_fixture(&home)?;
        assert_eq!(SDK_DIGEST_CALLS.with(std::cell::Cell::get), 4);
        let identity = sdk.identity().to_owned();
        for relative in ["bin/java.exe", "bin/javac.exe", "release", "lib/modules"] {
            let path = home.join(relative);
            let original = fs::read(&path)?;
            assert!(
                fs::OpenOptions::new()
                    .write(true)
                    .share_mode(7)
                    .open(&path)
                    .is_err()
            );
            assert!(fs::rename(&path, path.with_extension("replacement")).is_err());
            assert!(fs::remove_file(&path).is_err());
            sdk.recheck()?;
            assert_eq!(fs::read(&path)?, original);
        }
        assert_eq!(sdk.identity(), identity);
        assert_eq!(SDK_DIGEST_CALLS.with(std::cell::Cell::get), 4);
        drop(sdk);
        fs::write(home.join("lib/modules"), b"changed modules after drop")?;
        let fresh = held_fixture(&home)?;
        assert_ne!(fresh.identity(), identity);
        assert_eq!(SDK_DIGEST_CALLS.with(std::cell::Cell::get), 8);
        Ok(())
    }

    #[test]
    fn windows_sdk_capture_fixture_refuses_a_preexisting_writer() -> Result<()> {
        let temp = tempfile::tempdir()?;
        let home = temp.path().join("sdk");
        write_fixture(&home)?;
        let writer = fs::OpenOptions::new()
            .read(true)
            .write(true)
            .share_mode(7)
            .open(home.join("lib/modules"))?;
        let error = match held_fixture(&home) {
            Ok(_) => eyre::bail!("preexisting writable SDK handle was admitted"),
            Err(error) => error,
        };
        assert_eq!(
            error
                .downcast_ref::<std::io::Error>()
                .and_then(std::io::Error::raw_os_error),
            Some(32)
        );
        drop(writer);
        held_fixture(&home)?.recheck()?;
        Ok(())
    }
}
