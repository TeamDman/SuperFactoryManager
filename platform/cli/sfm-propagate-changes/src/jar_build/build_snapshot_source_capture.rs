//! Single-read Windows source capture for a successful-build snapshot.
//!
//! Holds captured objects, not a closed live namespace: newly created children
//! are outside the enumerated set. The destination still receives full validation.
use super::BuildSnapshotReceipt;
use super::MAX_FILE_BYTES;
use super::MAX_FILES;
use super::ROOT_NAMES;
use super::add_bytes;
use super::add_parents;
use super::canonical_directory;
use super::digest;
use super::ensure_owned_directory;
use super::open_read;
use super::relative_spelling;
use super::relative_string;
use super::write_new;
use crate::cancellation::CancellationToken;
use crate::file_identity::file_identity;
use crate::source_projection::candidate_lock::is_reparse;
use crate::source_projection::core_input_leases::DirectoryLeases;
use crate::source_projection::core_input_leases::MAX_DIRECTORY_LEASES;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeSet;
use std::fs;
use std::fs::File;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;

pub(super) struct SnapshotSourceCapture {
    project: PathBuf,
    files: Vec<String>,
    directories: BTreeSet<String>,
    ancestry: DirectoryLeases,
    held_files: Vec<File>,
    #[cfg(test)]
    payload_reads: usize,
}

impl SnapshotSourceCapture {
    pub(super) fn prepare(
        project: &Path,
        cancellation: &CancellationToken,
    ) -> Result<Option<Self>> {
        Self::prepare_bounded(project, cancellation, MAX_DIRECTORY_LEASES)
    }

    fn prepare_bounded(
        project: &Path,
        cancellation: &CancellationToken,
        lease_limit: usize,
    ) -> Result<Option<Self>> {
        cancellation.bail_if_cancelled()?;
        let mut capture = Self {
            project: canonical_directory(project)?,
            files: Vec::new(),
            directories: BTreeSet::new(),
            ancestry: DirectoryLeases::default(),
            held_files: Vec::new(),
            #[cfg(test)]
            payload_reads: 0,
        };
        let mut metadata_bytes = 0;
        for root in ROOT_NAMES {
            capture.enumerate_root(root, &mut metadata_bytes, cancellation)?;
        }
        // Match prepare's actual key spelling and all ancestors above project,
        // including empty selected directories. Capacity fallback has no copy effects.
        let mut needed = BTreeSet::new();
        for ancestor in capture.project.ancestors() {
            cancellation.bail_if_cancelled()?;
            needed.insert(ancestor.to_path_buf());
        }
        for relative in &capture.directories {
            cancellation.bail_if_cancelled()?;
            let mut directory = capture.project.clone();
            for part in relative.split('/') {
                cancellation.bail_if_cancelled()?;
                directory.push(part);
                needed.insert(directory.clone());
            }
        }
        if needed.len() > lease_limit.min(MAX_DIRECTORY_LEASES) {
            return Ok(None);
        }
        cancellation.bail_if_cancelled()?;
        if capture
            .ancestry
            .prepare_if_available(&capture.project, "snapshot-reserved-leaf")?
            .is_none()
        {
            return Ok(None);
        }
        for relative in &capture.directories {
            cancellation.bail_if_cancelled()?;
            if capture
                .ancestry
                .prepare_if_available(
                    &capture.project,
                    &format!("{relative}/snapshot-reserved-leaf"),
                )?
                .is_none()
            {
                return Ok(None);
            }
        }
        capture.files.sort();
        Ok(Some(capture))
    }

    fn enumerate_root(
        &mut self,
        root: &str,
        metadata_bytes: &mut u64,
        cancellation: &CancellationToken,
    ) -> Result<()> {
        cancellation.bail_if_cancelled()?;
        let source = self.project.join(root);
        match fs::symlink_metadata(&source) {
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
            result => {
                let metadata = result?;
                ensure!(
                    metadata.is_dir() && !is_reparse(&metadata),
                    "build output root is a link or non-directory"
                );
            }
        }
        let source = canonical_directory(&source)?;
        ensure!(
            relative_spelling(&self.project, &source).is_some(),
            "build output escaped project owner"
        );
        add_parents(&mut self.directories, root);
        // The receipt also records its enclosing "project" directory.
        ensure!(
            self.directories.len() < MAX_FILES,
            "snapshot directory count exceeds limit"
        );
        let mut names = BTreeSet::new();
        let mut file_count = 0;
        let mut directory_count = 0;
        for entry in walkdir::WalkDir::new(&source)
            .follow_links(false)
            .min_depth(1)
            .max_depth(129)
        {
            cancellation.bail_if_cancelled()?;
            let entry = entry?;
            let relative = relative_string(&source, entry.path())?;
            ensure!(
                names.insert(relative.to_lowercase()),
                "snapshot tree has ambiguous case-folded paths"
            );
            let metadata = fs::symlink_metadata(entry.path())?;
            ensure!(
                !is_reparse(&metadata),
                "snapshot tree contains a link or reparse point"
            );
            let selected = format!("{root}/{relative}");
            if metadata.is_dir() {
                directory_count += 1;
                ensure!(
                    directory_count <= MAX_FILES,
                    "snapshot directory count exceeds limit"
                );
                self.directories.insert(selected);
                ensure!(
                    self.directories.len() < MAX_FILES,
                    "snapshot directory count exceeds limit"
                );
            } else {
                ensure!(
                    metadata.is_file(),
                    "snapshot tree contains a nonregular file"
                );
                ensure!(
                    metadata.len() <= MAX_FILE_BYTES,
                    "snapshot input exceeds byte limit"
                );
                add_bytes(metadata_bytes, usize::try_from(metadata.len())?)?;
                file_count += 1;
                ensure!(file_count <= MAX_FILES, "snapshot file count exceeds limit");
                self.files.push(selected);
                ensure!(
                    self.files.len() < MAX_FILES,
                    "snapshot file count exceeds limit"
                );
            }
        }
        Ok(())
    }

    pub(super) fn copy_into(
        &mut self,
        receipt: &mut BuildSnapshotReceipt,
        total: &mut u64,
        cancellation: &CancellationToken,
    ) -> Result<()> {
        for relative in &self.directories {
            cancellation.bail_if_cancelled()?;
            let selected = format!("project/{relative}");
            ensure_owned_directory(&receipt.snapshot_dir, &receipt.snapshot_dir.join(&selected))?;
            add_parents(&mut receipt.directories, &selected);
        }
        for relative in &self.files {
            cancellation.bail_if_cancelled()?;
            let path = self.ancestry.prepare(&self.project, relative)?;
            let (file, raw) = Self::read_source(&path, cancellation)?;
            #[cfg(test)]
            {
                self.payload_reads += 1;
            }
            let hash = digest(&raw);
            add_bytes(total, raw.len())?;
            let selected = format!("project/{relative}");
            write_new(&receipt.snapshot_dir.join(&selected), &raw)?;
            receipt.files.insert(selected, hash);
            self.held_files.push(file);
            ensure!(
                receipt.files.len() < MAX_FILES,
                "snapshot file count exceeds limit"
            );
        }
        ensure!(
            receipt.directories.len() <= MAX_FILES,
            "snapshot directory count exceeds limit"
        );
        Ok(())
    }

    fn read_source(path: &Path, cancellation: &CancellationToken) -> Result<(File, Vec<u8>)> {
        let mut file = open_read(path)?;
        let before = file.metadata()?;
        ensure!(
            before.is_file() && !is_reparse(&before),
            "snapshot input is not a regular non-reparse file"
        );
        ensure!(
            before.len() <= MAX_FILE_BYTES,
            "snapshot input exceeds byte limit"
        );
        let identity = file_identity(&file)?;
        let mut raw = Vec::new();
        (&mut file).take(MAX_FILE_BYTES + 1).read_to_end(&mut raw)?;
        cancellation.bail_if_cancelled()?;
        let after = file.metadata()?;
        ensure!(
            u64::try_from(raw.len())? <= MAX_FILE_BYTES,
            "snapshot input exceeds byte limit"
        );
        ensure!(
            after.len() == before.len()
                && u64::try_from(raw.len())? == before.len()
                && after.modified()? == before.modified()?,
            "snapshot input changed while reading"
        );
        ensure!(
            file_identity(&file)? == identity && file_identity(&open_read(path)?)? == identity,
            "snapshot input path was replaced while reading"
        );
        Ok((file, raw))
    }
}

#[cfg(test)]
#[path = "build_snapshot_source_capture_tests.rs"]
mod tests;
