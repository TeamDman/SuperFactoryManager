//! Linux SDK image identity and boundary revalidation, not write exclusion.
//! Direct contained SDK symlinks are bound, never followed outside the image.
use crate::cancellation::CancellationToken;
use crate::file_identity::file_identity;
use crate::jar_build::local_dependency_cache::linux_io::Directory;
use crate::jar_build::local_dependency_cache::linux_io::HeldFile;
use crate::source_projection::nfrt_tool_sdk::NfrtToolSdk;
use eyre::Result;
use eyre::ensure;
use sha2::Digest as _;
use std::collections::BTreeMap;
use std::fs;
use std::fs::File;
use std::fs::OpenOptions;
use std::os::unix::fs::MetadataExt as _;
use std::os::unix::fs::OpenOptionsExt as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;

const MAX_ENTRIES: usize = 16_384;
const MAX_FILE: u64 = 1024 * 1024 * 1024;
const MAX_BYTES: u64 = 2 * 1024 * 1024 * 1024;

#[derive(Debug)]
pub(crate) struct LocalDependencyImage {
    identity: String,
    home: PathBuf,
    sdk_identity: String,
    files: BTreeMap<String, HeldFile>,
    links: BTreeMap<String, File>,
    directories: BTreeMap<String, Directory>,
}

impl LocalDependencyImage {
    pub(crate) fn capture(sdk: &NfrtToolSdk, cancel: &CancellationToken) -> Result<Self> {
        let home = sdk
            .executable()
            .parent()
            .and_then(Path::parent)
            .ok_or_else(|| eyre::eyre!("SDK lacks home"))?;
        Self::capture_home(home, sdk.identity(), cancel)
    }

    pub(crate) fn capture_home(
        home: &Path,
        sdk_identity: &str,
        cancel: &CancellationToken,
    ) -> Result<Self> {
        let image = Self::scan(home, sdk_identity, cancel)?;
        image.recheck(cancel)?;
        tracing::info!(
            files = image.files.len(),
            "Linux SDK execution image fully hashed and rechecked; no mandatory write exclusion claimed"
        );
        Ok(image)
    }

    pub(crate) fn identity(&self) -> &str {
        &self.identity
    }

    pub(crate) fn recheck(&self, cancel: &CancellationToken) -> Result<()> {
        // Rescan membership and every file's complete content. A Linux file
        // descriptor does not provide FILE_SHARE_READ's Windows exclusions.
        let current = Self::scan(&self.home, &self.sdk_identity, cancel)?;
        ensure!(
            current.identity == self.identity
                && current.files.len() == self.files.len()
                && current.links.len() == self.links.len()
                && current.directories.len() == self.directories.len(),
            "SDK image membership or contents changed"
        );
        for (name, held) in &self.files {
            let now = current
                .files
                .get(name)
                .ok_or_else(|| eyre::eyre!("SDK image lost file"))?;
            ensure!(
                file_identity(&held.file)? == file_identity(&now.file)?,
                "SDK image file was replaced"
            );
            held.recheck(cancel)?;
        }
        for (name, held) in &self.links {
            ensure!(
                file_identity(held)?
                    == file_identity(
                        current
                            .links
                            .get(name)
                            .ok_or_else(|| eyre::eyre!("SDK image lost link"))?
                    )?,
                "SDK image link was replaced"
            );
        }
        for held in self.directories.values() {
            held.recheck()?;
        }
        Ok(())
    }

    #[expect(
        clippy::too_many_lines,
        reason = "Keep bounded SDK traversal, contained-link handling and image identity construction visible together."
    )]
    fn scan(home: &Path, sdk_identity: &str, cancel: &CancellationToken) -> Result<Self> {
        let root = Directory::open(home)?;
        let mut image = Self {
            identity: String::new(),
            home: home.to_owned(),
            sdk_identity: sdk_identity.to_owned(),
            files: BTreeMap::new(),
            links: BTreeMap::new(),
            directories: BTreeMap::new(),
        };
        image.directories.insert(String::new(), root.clone());
        let mut inventory = BTreeMap::new();
        inventory.insert(
            String::new(),
            format!(
                "directory:{:o}",
                fs::metadata(root.access(""))?.mode() & 0o7777
            ),
        );
        // Linux descriptors do not exclude writes to the core SDK. Keep these
        // bytes in this image as well as the outer SDK owner so the consumer's
        // pre-record recheck cannot commit javac outputs after a core edit.
        let release = HeldFile::capture(&root, "release", MAX_FILE, cancel)?;
        inventory.insert(
            "release".to_owned(),
            format!(
                "file::{}:{}:{:o}",
                release.bytes,
                release.digest,
                release.file.metadata()?.mode() & 0o7777
            ),
        );
        let mut total = release.bytes;
        image.files.insert("release".to_owned(), release);
        let mut pending = Vec::new();
        for name in ["bin", "conf", "lib"] {
            let directory = root.child(name, false)?;
            inventory.insert(
                name.to_owned(),
                format!(
                    "directory:{:o}",
                    fs::metadata(directory.access(""))?.mode() & 0o7777
                ),
            );
            image.directories.insert(name.to_owned(), directory.clone());
            pending.push((
                name.to_owned(),
                directory.clone(),
                vec![directory.path.clone()],
            ));
        }
        let mut seen = 4usize;
        while let Some((relative, directory, ancestors)) = pending.pop() {
            cancel.bail_if_cancelled()?;
            directory.recheck()?;
            let mut names = Vec::new();
            for entry in fs::read_dir(directory.access(""))? {
                cancel.bail_if_cancelled()?;
                seen += 1;
                ensure!(seen <= MAX_ENTRIES, "SDK image exceeds entry bound");
                names.push(
                    entry?
                        .file_name()
                        .into_string()
                        .map_err(|error| eyre::eyre!("SDK filename is not Unicode: {error:?}"))?,
                );
            }
            names.sort();
            for name in names {
                let logical = format!("{relative}/{name}");
                if logical == "lib/src.zip" {
                    continue;
                }
                let metadata = fs::symlink_metadata(directory.access(&name))?;
                let (parent, leaf, kind) = if metadata.is_symlink() {
                    let link = OpenOptions::new()
                        .read(true)
                        .custom_flags(0o1040_0000)
                        .open(directory.access(&name))?;
                    ensure!(link.metadata()?.is_symlink(), "SDK link changed type");
                    let text = fs::read_link(directory.access(&name))?;
                    let target = contained_target(home, &directory.path, &text)?;
                    let parent = Directory::open(
                        target
                            .parent()
                            .ok_or_else(|| eyre::eyre!("SDK link target has no parent"))?,
                    )?;
                    let leaf = target
                        .file_name()
                        .and_then(|n| n.to_str())
                        .ok_or_else(|| eyre::eyre!("SDK link target has no leaf"))?
                        .to_owned();
                    let target_meta = fs::symlink_metadata(parent.access(&leaf))?;
                    ensure!(
                        !target_meta.is_symlink(),
                        "SDK link chains are not admitted"
                    );
                    image.links.insert(logical.clone(), link);
                    let kind = format!(
                        "link:{}:{}",
                        text.display(),
                        target.strip_prefix(home)?.display()
                    );
                    (parent, leaf, kind)
                } else {
                    (directory.clone(), name, String::new())
                };
                let metadata = fs::symlink_metadata(parent.access(&leaf))?;
                if metadata.is_dir() {
                    let child = parent.child(&leaf, false)?;
                    ensure!(!ancestors.contains(&child.path), "SDK directory link cycle");
                    inventory.insert(
                        logical.clone(),
                        format!("directory:{kind}:{:o}", metadata.mode() & 0o7777),
                    );
                    image.directories.insert(logical.clone(), child.clone());
                    let mut chain = ancestors.clone();
                    chain.push(child.path.clone());
                    pending.push((logical, child, chain));
                } else {
                    ensure!(metadata.is_file(), "SDK entry is not regular");
                    let held = HeldFile::capture(&parent, &leaf, MAX_FILE, cancel)?;
                    total = total
                        .checked_add(held.bytes)
                        .ok_or_else(|| eyre::eyre!("SDK bytes overflow"))?;
                    ensure!(total <= MAX_BYTES, "SDK image exceeds total byte bound");
                    inventory.insert(
                        logical.clone(),
                        format!(
                            "file:{kind}:{}:{}:{:o}",
                            held.bytes,
                            held.digest,
                            metadata.mode() & 0o7777
                        ),
                    );
                    image.files.insert(logical, held);
                }
            }
        }
        root.recheck()?;
        let encoded = facet_json::to_string(&(
            "sfm:linux_dependency_sdk_image@1",
            std::env::consts::ARCH,
            sdk_identity,
            inventory,
        ))?;
        image.identity = format!("sha256:{:x}", sha2::Sha256::digest(encoded.as_bytes()));
        Ok(image)
    }
}

fn contained_target(home: &Path, directory: &Path, text: &Path) -> Result<PathBuf> {
    ensure!(!text.is_absolute(), "absolute SDK link is not admitted");
    let mut target = directory.to_owned();
    for component in text.components() {
        match component {
            Component::CurDir => (),
            Component::ParentDir => {
                ensure!(target != home && target.pop(), "SDK link escapes home");
            }
            Component::Normal(name) => target.push(name),
            _ => eyre::bail!("unsupported SDK link target"),
        }
    }
    ensure!(
        target.starts_with(home) && target != home,
        "SDK link escapes home"
    );
    Ok(target)
}
