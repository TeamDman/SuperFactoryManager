//! A small exact export set held through a verified dependency-cache copy.
use super::make_directory;
use super::project_compilation_cache;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::core_input_leases::DirectoryLeases;
use crate::source_projection::core_input_leases::MAX_DIRECTORY_LEASES;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeSet;
use std::fs::File;
use std::io::Read as _;
use std::io::Seek as _;
use std::io::SeekFrom;
use std::path::Path;
use std::path::PathBuf;

const MAX_FILE_BYTES: u64 = 128 * 1024 * 1024;
const MAX_TOTAL_BYTES: u64 = 1024 * 1024 * 1024;

pub(super) struct HeldForgePreparationOutputs {
    files: Vec<(String, File, u64)>,
    _ancestry: DirectoryLeases,
}

impl HeldForgePreparationOutputs {
    pub(super) fn open<'a>(
        root: &Path,
        names: impl Iterator<Item = &'a str>,
        check: &impl Fn() -> Result<()>,
    ) -> Result<Option<Self>> {
        let paths = names
            .map(|name| (name, root.join(name)))
            .collect::<Vec<_>>();
        Self::open_paths(&paths, check)
    }

    pub(super) fn open_paths(
        paths: &[(&str, PathBuf)],
        check: &impl Fn() -> Result<()>,
    ) -> Result<Option<Self>> {
        check()?;
        ensure!(
            !paths.is_empty() && paths.len() <= 8,
            "Invalid dependency preparation export count"
        );
        let mut needed = BTreeSet::new();
        for (_, path) in paths {
            check()?;
            ensure!(path.is_absolute(), "Relative dependency preparation output");
            for ancestor in path
                .parent()
                .ok_or_else(|| eyre::eyre!("Output has no parent"))?
                .ancestors()
            {
                needed.insert(ancestor.to_path_buf());
            }
        }
        if needed.len() > MAX_DIRECTORY_LEASES {
            return Ok(None);
        }
        let mut ancestry = DirectoryLeases::default();
        let mut files = Vec::new();
        let mut names = BTreeSet::new();
        let mut total = 0_u64;
        for (name, path) in paths {
            check()?;
            ensure!(
                !name.is_empty()
                    && name
                        .bytes()
                        .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'_' | b'.')),
                "Invalid preparation export name"
            );
            ensure!(names.insert(*name), "Duplicate preparation export name");
            let parent = checked_directory(
                path.parent()
                    .ok_or_else(|| eyre::eyre!("Output has no parent"))?,
            )?;
            let leaf = path
                .file_name()
                .and_then(|name| name.to_str())
                .ok_or_else(|| eyre::eyre!("Non-UTF8 export leaf"))?;
            if ancestry.prepare_if_available(&parent, leaf)?.is_none() {
                return Ok(None);
            }
            let file = match project_compilation_cache::open_read(path) {
                Ok(file) => file,
                Err(error)
                    if error
                        .downcast_ref::<std::io::Error>()
                        .is_some_and(|error| matches!(error.raw_os_error(), Some(5 | 32))) =>
                {
                    return Ok(None);
                }
                Err(error) => return Err(error),
            };
            let bytes = file.metadata()?.len();
            if bytes > MAX_FILE_BYTES {
                return Ok(None);
            }
            let Some(next) = total
                .checked_add(bytes)
                .filter(|bytes| *bytes <= MAX_TOTAL_BYTES)
            else {
                return Ok(None);
            };
            total = next;
            files.push(((*name).to_owned(), file, bytes));
        }
        check()?;
        Ok(Some(Self {
            files,
            _ancestry: ancestry,
        }))
    }

    pub(super) fn copy_to(
        &self,
        cache: &Path,
        outputs: &[(&str, PathBuf)],
        check: &impl Fn() -> Result<()>,
    ) -> Result<bool> {
        check()?;
        ensure!(
            outputs.len() == self.files.len(),
            "Preparation export count changed"
        );
        let mut destination_ancestry = DirectoryLeases::default();
        // Admit every destination namespace before the first payload write.
        for ((name, _, _), (expected, destination)) in self.files.iter().zip(outputs) {
            check()?;
            ensure!(name == expected, "Preparation export order changed");
            let parent = make_directory(
                destination
                    .parent()
                    .ok_or_else(|| eyre::eyre!("Export destination has no parent"))?,
            )?;
            ensure!(
                parent.starts_with(checked_directory(cache)?),
                "Export destination escaped cache"
            );
            let leaf = destination
                .file_name()
                .and_then(|name| name.to_str())
                .ok_or_else(|| eyre::eyre!("Non-UTF8 export destination"))?;
            if destination_ancestry
                .prepare_if_available(&parent, leaf)?
                .is_none()
            {
                return Ok(false);
            }
        }
        let mut buffer = vec![0_u8; 64 * 1024];
        for ((_, source, expected_bytes), (_, destination)) in self.files.iter().zip(outputs) {
            check()?;
            let mut source = source.try_clone()?;
            source.seek(SeekFrom::Start(0))?;
            let mut bytes = Vec::with_capacity(usize::try_from(*expected_bytes)?);
            loop {
                check()?;
                let read = source.read(&mut buffer)?;
                if read == 0 {
                    break;
                }
                ensure!(
                    bytes.len() as u64 + read as u64 <= *expected_bytes,
                    "Preparation export grew while held"
                );
                bytes.extend_from_slice(&buffer[..read]);
            }
            ensure!(
                bytes.len() as u64 == *expected_bytes
                    && source.metadata()?.len() == *expected_bytes,
                "Preparation export changed while held"
            );
            project_compilation_cache::write_record(cache, destination, &bytes)?;
        }
        check()?;
        Ok(true)
    }
    pub(super) fn content_matches(
        &self,
        other: &Self,
        check: &impl Fn() -> Result<()>,
    ) -> Result<bool> {
        if self.files.len() != other.files.len() {
            return Ok(false);
        }
        let mut left_buffer = vec![0_u8; 64 * 1024];
        let mut right_buffer = vec![0_u8; 64 * 1024];
        for ((left_name, left, left_bytes), (right_name, right, right_bytes)) in
            self.files.iter().zip(&other.files)
        {
            check()?;
            if left_name != right_name || left_bytes != right_bytes {
                return Ok(false);
            }
            let mut left = left.try_clone()?;
            let mut right = right.try_clone()?;
            left.seek(SeekFrom::Start(0))?;
            right.seek(SeekFrom::Start(0))?;
            let mut remaining = *left_bytes;
            while remaining > 0 {
                check()?;
                let count = usize::try_from(remaining.min(left_buffer.len() as u64))?;
                left.read_exact(&mut left_buffer[..count])?;
                right.read_exact(&mut right_buffer[..count])?;
                if left_buffer[..count] != right_buffer[..count] {
                    return Ok(false);
                }
                remaining -= count as u64;
            }
        }
        check()?;
        Ok(true)
    }
}
