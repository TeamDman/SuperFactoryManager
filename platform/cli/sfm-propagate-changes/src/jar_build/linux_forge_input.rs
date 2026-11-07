//! Linux producer input authority: anchored no-follow paths plus full byte rechecks.
#[path = "forge_preparation_input_hashes.rs"]
mod hashes;
use super::{
    ContentHash, ContentHashAlgorithm, ForgePreparationCapture, ForgePreparationInputRequest,
};
use crate::cancellation::CancellationToken;
use crate::jar_build::local_dependency_cache::linux_io::{Directory, HeldFile};
use crate::source_projection::core_input_leases::DirectoryLeases;
use eyre::{Result, ensure};
use hashes::ForgePreparationInputHashes;
use sha2::{Digest as _, Sha256};
use std::fs::File;
use std::io::Read as _;
use std::os::unix::fs::MetadataExt as _;
use std::path::PathBuf;
unsafe extern "C" {
    fn geteuid() -> u32;
}
fn current_uid() -> u32 {
    // SAFETY: geteuid has no pointer arguments and reads our credential.
    unsafe { geteuid() }
}

pub(super) struct HeldForgePreparationInput {
    pub(super) role: String,
    pub(super) path: PathBuf,
    pub(super) bytes: u64,
    pub(super) sha256: String,
    held: HeldFile,
    cancel: CancellationToken,
}
impl HeldForgePreparationInput {
    pub(super) fn capture(
        request: &ForgePreparationInputRequest,
        _directories: &mut DirectoryLeases,
        cancel: &CancellationToken,
    ) -> Result<Option<ForgePreparationCapture>> {
        let parent = Directory::open(
            request
                .path
                .parent()
                .ok_or_else(|| eyre::eyre!("input has no parent"))?,
        )?;
        parent.require_owner()?;
        let name = request
            .path
            .file_name()
            .and_then(|s| s.to_str())
            .ok_or_else(|| eyre::eyre!("invalid input leaf"))?;
        let held = HeldFile::capture(&parent, name, request.limit, cancel)?;
        ensure!(
            held.file.metadata()?.uid() == current_uid()
                && held.file.metadata()?.mode() & 0o022 == 0,
            "producer input owner or mode is unsafe"
        );
        if let Some(size) = request.exact_bytes {
            ensure!(held.bytes == size, "producer input size differs from pin");
        }
        let hashes = read_hashes(&held.file, held.bytes, request, cancel)?;
        for expected in &request.expected {
            ensure!(
                hashes.actual.iter().any(|actual| actual == expected),
                "producer input differs from original pin"
            );
        }
        let input = Self {
            role: request.role.clone(),
            path: request.path.clone(),
            bytes: held.bytes,
            sha256: hashes.sha256,
            held,
            cancel: cancel.clone(),
        };
        input.recheck()?;
        Ok(Some(ForgePreparationCapture {
            input,
            retained: hashes.retained,
        }))
    }
    pub(super) fn recheck(&self) -> Result<()> {
        self.held.directory.require_owner()?;
        ensure!(
            self.held.file.metadata()?.uid() == current_uid()
                && self.held.file.metadata()?.mode() & 0o022 == 0,
            "producer input owner or mode changed"
        );
        self.held.recheck(&self.cancel)
    }
}
fn read_hashes(
    file: &File,
    bytes: u64,
    request: &ForgePreparationInputRequest,
    cancellation: &CancellationToken,
) -> Result<ForgePreparationInputHashes> {
    #[cfg(test)]
    crate::jar_build::verification_metrics::capture(bytes);
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
