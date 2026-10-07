//! Invocation-held explicit SDK identity for the NFRT process host.
//! Same four execution inputs as named Compile, now held against replacement.
//! Pin acquisition remains the existing JDK resolver's responsibility.

use super::core_input_leases::DirectoryLeases;
use crate::file_identity::FileIdentity;
use crate::file_identity::file_identity;
use crate::jar_build::nfrt_launch_contract::PreparedNfrtInvocationInput;
use crate::jdk::ResolvedJava;
use eyre::Result;
use eyre::ensure;
use sha2::Digest;
use sha2::Sha256;
use std::fmt::Write as _;
use std::fs::File;
use std::fs::OpenOptions;
use std::io::Read;
use std::io::Seek;
use std::io::SeekFrom;
use std::os::windows::fs::MetadataExt;
use std::os::windows::fs::OpenOptionsExt;
use std::path::Path;
use std::path::PathBuf;
use windows::Win32::Storage::FileSystem::FILE_ATTRIBUTE_REPARSE_POINT;
use windows::Win32::Storage::FileSystem::FILE_FLAG_OPEN_REPARSE_POINT;
use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;

struct SdkFile {
    file: File,
    identity: FileIdentity,
    bytes: u64,
    digest: String,
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
        for relative in ["bin/java.exe", "bin/javac.exe", "release", "lib/modules"] {
            let path = ancestry.prepare(home, relative)?;
            let mut file = OpenOptions::new()
                .read(true)
                .share_mode(FILE_SHARE_READ.0)
                .custom_flags(FILE_FLAG_OPEN_REPARSE_POINT.0)
                .open(&path)?;
            let metadata = file.metadata()?;
            ensure!(
                metadata.is_file()
                    && metadata.len() > 0
                    && metadata.len() <= 1024 * 1024 * 1024
                    && metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 == 0,
                "SDK execution input is nonregular, empty, oversized or a reparse point"
            );
            let identity = file_identity(&file)?;
            let digest = digest_file(&mut file, metadata.len())?;
            if relative == "bin/java.exe" {
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
                file,
                identity,
                bytes: metadata.len(),
                digest,
            });
        }
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
        for entry in &mut self.files {
            ensure!(
                file_identity(&entry.file)? == entry.identity
                    && entry.file.metadata()?.len() == entry.bytes
                    && digest_file(&mut entry.file, entry.bytes)? == entry.digest,
                "SDK execution input changed while invocation was live"
            );
        }
        Ok(())
    }
}

fn digest_file(file: &mut File, expected: u64) -> Result<String> {
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
    Ok(format!("sha256:{:x}", hash.finalize()))
}
