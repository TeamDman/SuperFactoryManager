//! Explicit grep-able source view, separate from NFRT invocation snapshots.
//! Partial trees are retained but never selected without a complete head.
use super::super::linux_io::Directory;
use super::super::linux_io::HeldFile;
use super::LocalDependencyCache;
use crate::cancellation::CancellationToken;
use crate::source_archive::MAX_ENTRIES;
use crate::source_archive::MAX_ENTRY_BYTES;
use crate::source_archive::MAX_TOTAL_BYTES;
use eyre::Result;
use eyre::ensure;
use std::collections::HashSet;
use std::fs;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;

impl LocalDependencyCache {
    pub(crate) fn materialize_sources(
        &self,
        sources: &Path,
        cancel: &CancellationToken,
    ) -> Result<PathBuf> {
        self.source_root(sources, cancel)?;
        let held = self
            .held
            .iter()
            .find(|h| h.name == "sources.jar")
            .ok_or_else(|| eyre::eyre!("source archive not held"))?;
        let entry = &held.directory;
        entry.require_owner()?;
        let lease = entry.lock("source-filetree.lock", cancel)?;
        let pointer_name = "source-filetree.json";
        if fs::symlink_metadata(entry.access(pointer_name)).is_ok() {
            let pointer = HeldFile::capture(entry, pointer_name, 4096, cancel)?;
            let name: String = facet_json::from_slice(&pointer.content(cancel)?)?;
            ensure!(valid_tree(&name), "source filetree head is invalid");
            let tree = entry.child(&name, false)?;
            tree.require_owner()?;
            ensure!(
                crate::source_archive::extracted_tree_matches_archive(
                    &entry.access("sources.jar"),
                    &tree.access("")
                )?,
                "existing source filetree differs from verified archive; no replacement performed"
            );
            self.recheck(cancel)?;
            lease.recheck()?;
            return Ok(tree.path);
        }
        let name = format!("source-tree-{:032x}", rand::random::<u128>());
        let tree = entry.child(&name, true)?;
        tree.require_owner()?;
        extract_tree(entry, &tree, cancel)?;
        ensure!(
            crate::source_archive::extracted_tree_matches_archive(
                &entry.access("sources.jar"),
                &tree.access("")
            )?,
            "new source filetree differs from verified archive"
        );
        self.recheck(cancel)?;
        tree.recheck()?;
        tree.sync()?;
        lease.recheck()?;
        entry.publish(
            pointer_name,
            facet_json::to_string(&name)?.as_bytes(),
            cancel,
        )?;
        self.recheck(cancel)?;
        lease.recheck()?;
        Ok(tree.path)
    }
}

fn valid_tree(name: &str) -> bool {
    name.starts_with("source-tree-")
        && name.len() == 44
        && name
            .trim_start_matches("source-tree-")
            .bytes()
            .all(|b| b.is_ascii_hexdigit())
}

fn extract_tree(entry: &Directory, tree: &Directory, cancel: &CancellationToken) -> Result<()> {
    let mut archive = zip::ZipArchive::new(entry.read("sources.jar")?)?;
    ensure!(
        archive.len() <= MAX_ENTRIES,
        "source archive exceeds entry limit"
    );
    let mut seen = HashSet::new();
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
        for (position, component) in components.iter().enumerate() {
            let Component::Normal(part) = component else {
                eyre::bail!("non-normal source archive path");
            };
            let part = part
                .to_str()
                .ok_or_else(|| eyre::eyre!("non-Unicode source entry"))?;
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
