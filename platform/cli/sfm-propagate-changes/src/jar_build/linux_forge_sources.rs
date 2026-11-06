//! Persistent source view of the held Forge production. No project-local copy.
use super::{Store, exists, fresh_directory, valid_generation};
use crate::cancellation::CancellationToken;
use crate::file_identity::{FileIdentity, file_identity};
use crate::jar_build::local_dependency_cache::linux_io::{Directory, HeldFile, readonly_owned};
use eyre::{Result, ensure};
use std::collections::BTreeSet;
use std::fs;
use std::io::{Read as _, Write as _};
use std::path::{Path, PathBuf};

use crate::source_archive::{MAX_ENTRIES, MAX_ENTRY_BYTES, MAX_TOTAL_BYTES};
use std::collections::HashSet;
use std::path::Component;
impl Store {
    pub(in crate::jar_build::engine) fn materialize_sources(
        &self,
        cancel: &CancellationToken,
    ) -> Result<PathBuf> {
        self.recheck(cancel)?;
        let entry = self
            .entry
            .as_ref()
            .ok_or_else(|| eyre::eyre!("source production not held"))?;
        let archive = entry.access("deobfuscated_sources.jar");
        ensure!(
            self.roles.iter().any(|s| s == "deobfuscated_sources.jar"),
            "production has no deobfuscated source role"
        );
        let views = self.directory.child("source-views", true)?;
        views.require_owner()?;
        let lease = views.lock("producer.lock", cancel)?;
        if exists(&views, "current.json")? {
            let head = HeldFile::capture(&views, "current.json", 4096, cancel)?;
            let name: String = facet_json::from_slice(&head.content(cancel)?)?;
            ensure!(
                valid_generation(&name, "source-tree-"),
                "invalid source-view head"
            );
            let tree = views.child(&name, false)?;
            tree.require_owner()?;
            verify_tree(&tree, &archive, cancel, || Ok(()))?;
            head.recheck(cancel)?;
            tree.recheck()?;
            self.recheck(cancel)?;
            lease.recheck()?;
            *self
                .source_tree
                .lock()
                .map_err(|_poisoned| eyre::eyre!("source tree hold poisoned"))? =
                Some(tree.clone());
            self.recheck(cancel)?;
            return Ok(tree.path);
        }
        let name = format!("source-tree-{:032x}", rand::random::<u128>());
        let tree = fresh_directory(&views, &name)?;
        tree.require_owner()?;
        extract_tree(entry, &tree, cancel)?;
        verify_tree(&tree, &archive, cancel, || Ok(()))?;
        self.recheck(cancel)?;
        tree.recheck()?;
        tree.sync()?;
        lease.recheck()?;
        views.publish(
            "current.json",
            facet_json::to_string(&name)?.as_bytes(),
            cancel,
        )?;
        self.recheck(cancel)?;
        lease.recheck()?;
        tree.recheck()?;
        *self
            .source_tree
            .lock()
            .map_err(|_poisoned| eyre::eyre!("source tree hold poisoned"))? = Some(tree.clone());
        self.recheck(cancel)?;
        Ok(tree.path)
    }
}

pub(super) fn verify_tree(
    tree: &Directory,
    archive: &Path,
    cancel: &CancellationToken,
    validated: impl FnOnce() -> Result<()>,
) -> Result<()> {
    verify_tree_at_boundaries(tree, archive, cancel, validated, || Ok(()))
}

pub(super) fn verify_tree_at_boundaries(
    tree: &Directory,
    archive: &Path,
    cancel: &CancellationToken,
    validated: impl FnOnce() -> Result<()>,
    files_checked: impl FnOnce() -> Result<()>,
) -> Result<()> {
    let (directories, files, membership) = inventory_tree(tree, cancel)?;
    ensure!(
        crate::source_archive::extracted_tree_matches_archive(archive, &tree.access(""))?,
        "Forge source tree differs from its verified archive; no repair performed"
    );
    validated()?;
    for directory in &directories {
        directory.recheck()?;
        directory.require_owner()?;
    }
    for file in &files {
        file.recheck(cancel)?;
    }
    files_checked()?;
    let (current_directories, current_files, current) = inventory_tree(tree, cancel)?;
    ensure!(
        current == membership
            && file_identities(&files) == file_identities(&current_files)
            && directories.iter().map(|d| &d.path).collect::<BTreeSet<_>>()
                == current_directories
                    .iter()
                    .map(|d| &d.path)
                    .collect::<BTreeSet<_>>(),
        "source tree type, identity, membership or contents changed during validation"
    );
    for directory in &directories {
        directory.recheck()?;
    }
    tree.recheck()?;
    Ok(())
}

fn file_identities(
    files: &[TreeFile],
) -> std::collections::BTreeMap<PathBuf, (&FileIdentity, u64, &str)> {
    files
        .iter()
        .map(|file| {
            (
                file.directory.path.join(&file.name),
                (&file.identity, file.bytes, file.digest.as_str()),
            )
        })
        .collect()
}

struct TreeFile {
    directory: Directory,
    name: String,
    identity: FileIdentity,
    digest: String,
    bytes: u64,
}
impl TreeFile {
    fn capture(file: HeldFile) -> Result<Self> {
        readonly_owned(&file.file)?;
        Ok(Self {
            identity: file_identity(&file.file)?,
            directory: file.directory,
            name: file.name,
            digest: file.digest,
            bytes: file.bytes,
        })
    }
    fn recheck(&self, cancel: &CancellationToken) -> Result<()> {
        let current = HeldFile::capture(&self.directory, &self.name, MAX_ENTRY_BYTES, cancel)?;
        readonly_owned(&current.file)?;
        ensure!(
            file_identity(&current.file)? == self.identity
                && current.digest == self.digest
                && current.bytes == self.bytes,
            "source tree leaf changed during validation"
        );
        Ok(())
    }
}
fn inventory_tree(
    tree: &Directory,
    cancel: &CancellationToken,
) -> Result<(Vec<Directory>, Vec<TreeFile>, BTreeSet<PathBuf>)> {
    let mut pending = vec![tree.clone()];
    let mut directories = Vec::new();
    let mut files = Vec::new();
    let mut membership = BTreeSet::new();
    let mut total = 0u64;
    while let Some(directory) = pending.pop() {
        cancel.bail_if_cancelled()?;
        directory.recheck()?;
        directory.require_owner()?;
        ensure!(
            directory
                .path
                .strip_prefix(&tree.path)?
                .components()
                .count()
                <= 64,
            "source tree is too deep"
        );
        for item in fs::read_dir(directory.access(""))? {
            cancel.bail_if_cancelled()?;
            let name = item?
                .file_name()
                .into_string()
                .map_err(|_non_unicode| eyre::eyre!("non-Unicode source tree entry"))?;
            let metadata = fs::symlink_metadata(directory.access(&name))?;
            ensure!(
                membership.insert(directory.path.join(&name)) && membership.len() <= MAX_ENTRIES,
                "source tree membership exceeds bound"
            );
            if metadata.is_dir() {
                pending.push(directory.child(&name, false)?);
            } else {
                let file = HeldFile::capture(&directory, &name, MAX_ENTRY_BYTES, cancel)?;
                crate::jar_build::local_dependency_cache::linux_io::readonly_owned(&file.file)?;
                total = total
                    .checked_add(file.bytes)
                    .ok_or_else(|| eyre::eyre!("source tree size overflow"))?;
                ensure!(total <= MAX_TOTAL_BYTES, "source tree exceeds byte bound");
                files.push(TreeFile::capture(file)?);
            }
        }
        directories.push(directory);
    }
    Ok((directories, files, membership))
}
fn extract_tree(entry: &Directory, tree: &Directory, cancel: &CancellationToken) -> Result<()> {
    let mut archive = zip::ZipArchive::new(entry.read("deobfuscated_sources.jar")?)?;
    ensure!(
        archive.len() <= MAX_ENTRIES,
        "source archive exceeds entry limit"
    );
    let mut seen = HashSet::new();
    let mut members = HashSet::new();
    let mut total = 0u64;
    for index in 0..archive.len() {
        cancel.bail_if_cancelled()?;
        let mut input = archive.by_index(index)?;
        let relative = crate::source_archive::safe_entry_path(input.name())?.to_owned();
        crate::source_archive::reject_special_entry(input.name(), input.unix_mode())?;
        ensure!(
            seen.insert(relative.clone()),
            "duplicate source archive entry"
        );
        ensure!(
            input.size() <= MAX_ENTRY_BYTES,
            "source archive entry exceeds byte bound"
        );
        total = total
            .checked_add(input.size())
            .ok_or_else(|| eyre::eyre!("source archive size overflow"))?;
        ensure!(
            total <= MAX_TOTAL_BYTES,
            "source archive exceeds total byte bound"
        );
        let mut parent = tree.clone();
        let components = relative.components().collect::<Vec<_>>();
        ensure!(components.len() <= 64, "source archive path is too deep");
        let mut member = PathBuf::new();
        for (position, component) in components.iter().enumerate() {
            let Component::Normal(part) = component else {
                eyre::bail!("non-normal source archive path");
            };
            let part = part
                .to_str()
                .ok_or_else(|| eyre::eyre!("non-Unicode source entry"))?;
            member.push(part);
            members.insert(member.clone());
            ensure!(
                members.len() <= MAX_ENTRIES,
                "expanded source tree exceeds membership bound"
            );
            if position + 1 < components.len() || input.is_dir() {
                parent = parent.child(part, true)?;
                parent.require_owner()?;
            } else {
                let expected = input.size();
                parent.publish_with(
                    part,
                    |file| {
                        let mut total = 0u64;
                        let mut buffer = vec![0; 64 * 1024];
                        loop {
                            cancel.bail_if_cancelled()?;
                            let count = input.read(&mut buffer)?;
                            if count == 0 {
                                break;
                            }
                            total += u64::try_from(count)?;
                            ensure!(total <= expected, "source entry grew during extraction");
                            file.write_all(&buffer[..count])?;
                        }
                        ensure!(total == expected, "source archive length differs");
                        Ok(())
                    },
                    cancel,
                )?;
            }
        }
    }
    Ok(())
}
