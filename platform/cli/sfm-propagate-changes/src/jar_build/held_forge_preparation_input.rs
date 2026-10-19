//! Windows leaf authority retained through preparation production or materialization.

#[path = "forge_preparation_input_hashes.rs"]
mod hashes;

use super::ContentHash;
use super::ContentHashAlgorithm;
use super::ForgePreparationCapture;
use super::ForgePreparationInputRequest;
use crate::cancellation::CancellationToken;
use crate::file_identity::FileIdentity;
use crate::source_projection::candidate_lock::is_reparse;
use crate::source_projection::core_input_leases::DirectoryLeases;
use eyre::Result;
use eyre::ensure;
use hashes::ForgePreparationInputHashes;
use sha2::Digest as _;
use sha2::Sha256;
use std::fs;
use std::fs::File;
use std::io::Read as _;
use std::os::windows::fs::OpenOptionsExt as _;
use std::path::Path;
use std::path::PathBuf;
use std::time::SystemTime;
use windows::Win32::Storage::FileSystem::FILE_FLAG_OPEN_REPARSE_POINT;
use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;

pub(super) struct HeldForgePreparationInput {
    pub(super) role: String,
    pub(super) path: PathBuf,
    pub(super) bytes: u64,
    pub(super) sha256: String,
    file: File,
    identity: FileIdentity,
    modified: SystemTime,
}

impl HeldForgePreparationInput {
    pub(super) fn capture(
        request: &ForgePreparationInputRequest,
        directories: &mut DirectoryLeases,
        cancellation: &CancellationToken,
    ) -> Result<Option<ForgePreparationCapture>> {
        cancellation.bail_if_cancelled()?;
        let parent = request
            .path
            .parent()
            .ok_or_else(|| eyre::eyre!("Input has no parent"))?;
        let name = request
            .path
            .file_name()
            .and_then(|name| name.to_str())
            .ok_or_else(|| eyre::eyre!("Input leaf is not Unicode"))?;
        let Some(path) = directories.prepare_if_available(parent, name)? else {
            return Ok(None);
        };
        let file = match open_read(&path) {
            Ok(file) => file,
            Err(error) if matches!(error.raw_os_error(), Some(5 | 32)) => return Ok(None),
            Err(error) => return Err(error.into()),
        };
        let metadata = file.metadata()?;
        ensure!(
            metadata.is_file() && !is_reparse(&metadata),
            "Unsafe preparation input"
        );
        if metadata.len() > request.limit || !single_link(&file)? {
            return Ok(None);
        }
        if let Some(bytes) = request.exact_bytes {
            ensure!(
                metadata.len() == bytes,
                "Preparation input size differs from its pin"
            );
        }
        let identity = crate::file_identity::file_identity(&file)?;
        let modified = metadata.modified()?;
        let hashes = read_hashes(&file, metadata.len(), request, cancellation)?;
        for expected in &request.expected {
            ensure!(
                hashes
                    .actual
                    .iter()
                    .find(|actual| actual.algorithm == expected.algorithm)
                    == Some(expected),
                "Preparation input differs from its captured/original pin"
            );
        }
        let captured = Self {
            role: request.role.clone(),
            path,
            bytes: metadata.len(),
            sha256: hashes.sha256,
            file,
            identity,
            modified,
        };
        captured.recheck()?;
        Ok(Some(ForgePreparationCapture {
            input: captured,
            retained: hashes.retained,
        }))
    }

    pub(super) fn recheck(&self) -> Result<()> {
        let held = self.file.metadata()?;
        let reopened = open_read(&self.path)?;
        let current = reopened.metadata()?;
        ensure!(
            held.is_file()
                && current.is_file()
                && !is_reparse(&held)
                && !is_reparse(&current)
                && held.len() == self.bytes
                && current.len() == self.bytes
                && held.modified()? == self.modified
                && current.modified()? == self.modified
                && crate::file_identity::file_identity(&self.file)? == self.identity
                && crate::file_identity::file_identity(&reopened)? == self.identity
                && single_link(&self.file)?
                && single_link(&reopened)?,
            "Held preparation input changed"
        );
        Ok(())
    }
}

fn open_read(path: &Path) -> std::io::Result<File> {
    fs::OpenOptions::new()
        .read(true)
        .share_mode(FILE_SHARE_READ.0)
        .custom_flags(FILE_FLAG_OPEN_REPARSE_POINT.0)
        .open(path)
}

fn single_link(file: &File) -> Result<bool> {
    use std::os::windows::io::AsRawHandle as _;
    use windows::Win32::Foundation::HANDLE;
    use windows::Win32::Storage::FileSystem::{
        FILE_STANDARD_INFO, FileStandardInfo, GetFileInformationByHandleEx,
    };
    let mut information = FILE_STANDARD_INFO::default();
    // SAFETY: file owns the live handle; the output buffer is sized for this class.
    unsafe {
        GetFileInformationByHandleEx(
            HANDLE(file.as_raw_handle()),
            FileStandardInfo,
            std::ptr::from_mut(&mut information).cast(),
            u32::try_from(std::mem::size_of_val(&information))?,
        )
    }?;
    Ok(information.NumberOfLinks == 1)
}

fn read_hashes(
    file: &File,
    bytes: u64,
    request: &ForgePreparationInputRequest,
    cancellation: &CancellationToken,
) -> Result<ForgePreparationInputHashes> {
    let mut reader = file;
    let mut sha256 = Sha256::new();
    let mut sha1 = request
        .expected
        .iter()
        .any(|pin| pin.algorithm == ContentHashAlgorithm::Sha1)
        .then(sha1::Sha1::new);
    let mut blake3 = request
        .expected
        .iter()
        .any(|pin| pin.algorithm == ContentHashAlgorithm::Blake3)
        .then(blake3::Hasher::new);
    let mut retained = request.retain_bytes.then(Vec::new);
    let mut read = 0_u64;
    let mut buffer = vec![0_u8; 64 * 1024];
    loop {
        cancellation.bail_if_cancelled()?;
        let count = reader.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        read = read
            .checked_add(u64::try_from(count)?)
            .ok_or_else(|| eyre::eyre!("Preparation input size overflow"))?;
        ensure!(read <= bytes, "Preparation input grew during capture");
        sha256.update(&buffer[..count]);
        if let Some(sha1) = &mut sha1 {
            sha1.update(&buffer[..count]);
        }
        if let Some(blake3) = &mut blake3 {
            blake3.update(&buffer[..count]);
        }
        if let Some(retained) = &mut retained {
            retained.extend_from_slice(&buffer[..count]);
        }
    }
    ensure!(read == bytes, "Preparation input shortened during capture");
    let mut actual = Vec::new();
    if let Some(sha1) = sha1 {
        let mut value = [0; 20];
        value.copy_from_slice(&sha1.finalize());
        actual.push(ContentHash {
            algorithm: ContentHashAlgorithm::Sha1,
            value,
        });
    }
    if let Some(blake3) = blake3 {
        let mut value = [0; 20];
        blake3.finalize_xof().fill(&mut value);
        actual.push(ContentHash {
            algorithm: ContentHashAlgorithm::Blake3,
            value,
        });
    }
    Ok(ForgePreparationInputHashes {
        sha256: format!("{:x}", sha256.finalize()),
        actual,
        retained,
    })
}
