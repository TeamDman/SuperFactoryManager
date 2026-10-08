//! Raw, local Git objects used only as immutable source-comparison oracles.
//!
//! Opening a repository does not consult global configuration or Git environment
//! overrides. No checkout, index, attributes, filters, fetch or subprocess is used.

use super::provenance::sha256;
use eyre::Context;
use eyre::Result;
use eyre::bail;
use eyre::ensure;
use facet::Facet;
use gix::bstr::ByteSlice;
use gix::hash::ObjectId;
use gix::objs::Kind;
use std::cell::RefCell;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
use std::sync::Arc;

const MAX_BLOB_BYTES: u64 = 64 * 1024 * 1024;
const MAX_SELECTED_BYTES: u64 = 512 * 1024 * 1024;
const MAX_METADATA_BYTES: u64 = 8 * 1024 * 1024;
const MAX_TREE_ENTRIES: usize = 100_000;
const MAX_TREE_DEPTH: usize = 128;
const MAX_PATH_BYTES: usize = 4096;
const MAX_TAG_DEPTH: usize = 16;

/// A repository opened for raw oracle reads, with replacement objects disabled.
pub struct OracleGitRepository {
    repository: gix::Repository,
    verified_headers: RefCell<BTreeMap<(String, u8), u64>>,
}

#[derive(Clone, Debug, Eq, PartialEq, Facet)]
pub struct GitRevision {
    pub reference: String,
    pub commit: String,
    pub tree: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GitFile {
    pub oid: String,
    pub mode: u32,
    pub bytes: Vec<u8>,
}

#[derive(Clone, Debug, Eq, PartialEq, Facet)]
#[facet(deny_unknown_fields)]
pub struct GitFileDescriptor {
    pub oid: String,
    pub mode: u32,
    pub size: u64,
}

#[derive(Clone, Debug, Eq, PartialEq, Facet)]
#[facet(deny_unknown_fields)]
pub struct GitInventoryDescriptor {
    pub selected_root: String,
    pub trees: BTreeSet<String>,
    pub files: BTreeMap<String, GitFileDescriptor>,
}

/// Structurally checked, immutable inventory shared within one invocation.
/// No decoder or mutable accessor can construct or modify this proof. Object
/// availability remains a separate live check, not a fact certified here.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ValidatedGitInventory {
    descriptor: Arc<GitInventoryDescriptor>,
    identity: String,
}

impl ValidatedGitInventory {
    /// Check all descriptor bounds and paths before sealing its owned contents.
    ///
    /// # Errors
    /// Rejects every malformed descriptor rejected by the uncached validator.
    pub fn new(descriptor: GitInventoryDescriptor) -> Result<Self> {
        descriptor.validate()?;
        let identity = descriptor.identity();
        Ok(Self {
            descriptor: Arc::new(descriptor),
            identity,
        })
    }

    #[must_use]
    pub fn descriptor(&self) -> &GitInventoryDescriptor {
        &self.descriptor
    }

    /// Sharing retains the seal's owner; `Arc::make_mut` on the returned handle
    /// can only create a detached copy, never change the sealed descriptor.
    pub(super) fn shared_descriptor(&self) -> Arc<GitInventoryDescriptor> {
        Arc::clone(&self.descriptor)
    }

    #[must_use]
    pub fn identity(&self) -> &str {
        &self.identity
    }
}

impl GitInventoryDescriptor {
    /// Exact pinned inventory identity, including paths, modes, OIDs and sizes.
    #[must_use]
    pub fn identity(&self) -> String {
        let mut bytes = Vec::new();
        for (path, file) in &self.files {
            bytes.extend_from_slice(
                format!("{path}\0{:o}\0{}\0{}\n", file.mode, file.oid, file.size).as_bytes(),
            );
        }
        sha256(&bytes)
    }

    /// Validate bounded portable descriptors independently of cache provenance.
    ///
    /// # Errors
    /// Rejects malformed IDs, unsafe paths, unsupported modes and size limits.
    pub fn validate(&self) -> Result<()> {
        ensure!(
            !self.trees.is_empty()
                && self.trees.len() <= MAX_TREE_ENTRIES
                && self.files.len() <= MAX_TREE_ENTRIES,
            "invalid cached Git inventory size"
        );
        ensure!(
            self.trees.contains(&self.selected_root),
            "cached inventory omits its root tree"
        );
        for oid in self
            .trees
            .iter()
            .chain(self.files.values().map(|file| &file.oid))
        {
            ensure!(
                oid.len() == 40
                    && oid
                        .bytes()
                        .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
                "invalid cached Git object ID"
            );
        }
        let mut names = BTreeSet::new();
        let mut total = 0_u64;
        for (path, file) in &self.files {
            validate_prefix(path)?;
            ensure!(
                !path.is_empty()
                    && path.len() <= MAX_PATH_BYTES
                    && names.insert(path.to_uppercase()),
                "unsafe/colliding cached Git path"
            );
            ensure!(
                matches!(file.mode, 0o100_644 | 0o100_755) && file.size <= MAX_BLOB_BYTES,
                "invalid cached Git file descriptor"
            );
            total = total
                .checked_add(file.size)
                .ok_or_else(|| eyre::eyre!("cached Git inventory byte overflow"))?;
            ensure!(
                total <= MAX_SELECTED_BYTES,
                "cached Git inventory exceeds byte limit"
            );
        }
        Ok(())
    }
}

struct BlobEntry {
    oid: ObjectId,
    mode: u32,
    size: u64,
}

#[derive(Default)]
struct Inventory {
    blobs: BTreeMap<String, BlobEntry>,
    entry_count: usize,
    total_bytes: u64,
    trees: BTreeSet<String>,
}

impl OracleGitRepository {
    /// Open exactly this worktree, git directory or bare repository. No ancestor
    /// discovery or environment-based Git directory redirection is performed.
    ///
    /// # Errors
    /// Fails when the repository or its local configuration cannot be read.
    pub fn open(path: &Path) -> Result<Self> {
        let options = gix::open::Options::isolated()
            .strict_config(true)
            .config_overrides([
                "gitoxide.objects.noReplace=true",
                "gitoxide.objects.allocLimit=67108864",
            ]);
        let mut repository = gix::open_opts(path, options)
            .wrap_err_with(|| format!("Cannot open local oracle repository {}", path.display()))?;
        // A repository-local namespace must not reinterpret an explicit full ref.
        let _ = repository.clear_namespace();
        Ok(Self {
            repository,
            verified_headers: RefCell::new(BTreeMap::new()),
        })
    }

    /// Actual worktree Git-state directory, which may be outside the checkout.
    #[must_use]
    pub fn git_dir(&self) -> &Path {
        self.repository.git_dir()
    }

    /// Shared Git-state directory, or the worktree Git directory when not linked.
    #[must_use]
    pub fn common_dir(&self) -> &Path {
        self.repository.common_dir()
    }

    /// Resolve an exact local branch or release tag, peeling annotated tags to
    /// commits. Revision expressions, abbreviated refs and symbolic aliases fail.
    ///
    /// # Errors
    /// Fails for invalid/missing refs, non-commit targets, missing objects or limits.
    pub fn resolve_reference(&self, full_ref: &str) -> Result<GitRevision> {
        ensure!(
            full_ref.starts_with("refs/heads/") || full_ref.starts_with("refs/tags/"),
            "Oracle reference must be a full refs/heads/ or refs/tags/ name: {full_ref:?}"
        );
        let _: &gix::refs::FullNameRef = full_ref
            .try_into()
            .wrap_err_with(|| format!("Invalid full oracle reference {full_ref:?}"))?;
        let reference = self
            .repository
            .find_reference(full_ref)
            .wrap_err_with(|| format!("Cannot read exact oracle reference {full_ref}"))?;
        ensure!(
            reference.name().as_bstr() == full_ref.as_bytes().as_bstr(),
            "Oracle reference resolved to a different name: {full_ref}"
        );
        let mut oid = reference
            .try_id()
            .ok_or_else(|| eyre::eyre!("Symbolic oracle reference is unsupported: {full_ref}"))?
            .detach();
        for _ in 0..MAX_TAG_DEPTH {
            let header = self
                .repository
                .find_header(oid)
                .wrap_err_with(|| format!("Cannot read oracle object {oid} for {full_ref}"))?;
            ensure!(
                header.size() <= MAX_METADATA_BYTES,
                "Oracle revision object {oid} exceeds metadata limit {MAX_METADATA_BYTES}"
            );
            match header.kind() {
                Kind::Commit => return self.revision_for_commit(full_ref, oid),
                Kind::Tag => {
                    oid = self
                        .repository
                        .find_tag(oid)?
                        .target_id()
                        .wrap_err("Cannot decode oracle tag target")?
                        .detach();
                }
                kind => bail!("Oracle reference {full_ref} targets {kind:?}, not a commit"),
            }
        }
        bail!("Oracle tag chain for {full_ref} exceeds {MAX_TAG_DEPTH} objects")
    }

    /// Resolve only a full commit object ID. Tags and arbitrary revision syntax
    /// are not accepted here; a frozen pin must identify the actual commit.
    ///
    /// # Errors
    /// Fails for abbreviated/invalid IDs, missing objects or non-commit targets.
    pub fn resolve_commit(&self, full_oid: &str) -> Result<GitRevision> {
        let oid = self.parse_commit_id(full_oid)?;
        self.revision_for_commit(full_oid, oid)
    }

    /// Return all regular files below an explicit Git tree prefix. Paths are
    /// relative to that prefix; an empty prefix selects the repository root.
    ///
    /// # Errors
    /// Fails for unsafe paths, missing objects, unsupported modes or read limits.
    pub fn files_at_commit(&self, commit: &str, prefix: &str) -> Result<BTreeMap<String, GitFile>> {
        self.files_at_commit_filtered(commit, prefix, |_| true)
    }

    /// Read one pinned path without inventorying sibling subtrees.
    ///
    /// # Errors
    /// Rejects unsafe paths, unsupported modes and unavailable or oversized objects.
    /// An absent path returns `None`; a corrupt object never means absence.
    pub fn file_at_commit(&self, commit: &str, path: &str) -> Result<Option<GitFile>> {
        validate_prefix(path)?;
        ensure!(
            !path.is_empty() && path.len() <= MAX_PATH_BYTES,
            "invalid oracle file path"
        );
        let revision = self.resolve_commit(commit)?;
        let mut current = ObjectId::from_hex(revision.tree.as_bytes())?;
        let components = path.split('/').collect::<Vec<_>>();
        ensure!(
            components.len() <= MAX_TREE_DEPTH,
            "oracle path exceeds depth limit"
        );
        for (index, component) in components.iter().enumerate() {
            self.checked_header(current, Kind::Tree, MAX_METADATA_BYTES)?;
            let tree = self.repository.find_tree(current)?;
            let mut names = BTreeSet::new();
            let mut found = None;
            for entry in tree.iter() {
                let entry = entry?;
                let name = checked_name(entry.filename().as_bytes())?;
                ensure!(
                    names.insert(name.to_uppercase()) && names.len() <= MAX_TREE_ENTRIES,
                    "invalid/colliding oracle tree names"
                );
                if name == *component {
                    found = Some((entry.object_id(), u32::from(entry.mode().value())));
                }
            }
            let Some((oid, mode)) = found else {
                return Ok(None);
            };
            if index + 1 == components.len() {
                ensure!(
                    matches!(mode, 0o100_644 | 0o100_755),
                    "unsupported oracle file mode {mode:o}"
                );
                let size = self.checked_header(oid, Kind::Blob, MAX_BLOB_BYTES)?;
                let mut blob = self.repository.find_blob(oid)?;
                ensure!(blob.data.len() as u64 == size, "oracle blob size changed");
                return Ok(Some(GitFile {
                    oid: oid.to_string(),
                    mode,
                    bytes: blob.take_data(),
                }));
            }
            ensure!(mode == 0o040_000, "oracle path includes a non-directory");
            current = oid;
        }
        unreachable!("nonempty path returns at its last component")
    }

    /// Apply the caller's declared project scope before loading any blobs.
    /// `include` sees prefix-relative leaf paths, including unsupported modes so
    /// in-scope symlinks/gitlinks fail. Traversed tree names must always be safe.
    /// No built-in file or directory exclusion silently changes the oracle.
    ///
    /// # Errors
    /// Fails for unsafe paths, missing selected objects, unsupported selected
    /// modes or read limits. Excluded blob objects are not opened.
    pub fn files_at_commit_filtered(
        &self,
        commit: &str,
        prefix: &str,
        include: impl Fn(&str) -> bool,
    ) -> Result<BTreeMap<String, GitFile>> {
        validate_prefix(prefix)?;
        let revision = self.resolve_commit(commit)?;
        let root_id = ObjectId::from_hex(revision.tree.as_bytes())?;
        let selected_root = self.tree_below_prefix(root_id, prefix)?;
        let mut inventory = Inventory::default();
        self.inventory_tree(selected_root, "", 0, &include, &mut inventory)?;

        // Complete structural/scope/size validation before copying blob content.
        inventory
            .blobs
            .into_iter()
            .map(|(path, entry)| {
                let mut blob = self.repository.find_blob(entry.oid).wrap_err_with(|| {
                    format!("Cannot read oracle blob {} for {path}", entry.oid)
                })?;
                ensure!(
                    u64::try_from(blob.data.len())? == entry.size,
                    "Oracle blob {} changed size while reading {path}",
                    entry.oid
                );
                Ok((
                    path,
                    GitFile {
                        oid: entry.oid.to_string(),
                        mode: entry.mode,
                        bytes: blob.take_data(),
                    },
                ))
            })
            .collect()
    }

    /// Capture immutable descriptors without loading every selected blob.
    ///
    /// # Errors
    /// Rejects unsafe prefixes, unavailable objects and invalid tree inventories.
    pub fn describe_at_commit_filtered(
        &self,
        commit: &str,
        prefix: &str,
        include: impl Fn(&str) -> bool,
    ) -> Result<GitInventoryDescriptor> {
        validate_prefix(prefix)?;
        let revision = self.resolve_commit(commit)?;
        let selected_root =
            self.tree_below_prefix(ObjectId::from_hex(revision.tree.as_bytes())?, prefix)?;
        let mut inventory = Inventory::default();
        self.inventory_tree(selected_root, "", 0, &include, &mut inventory)?;
        Ok(GitInventoryDescriptor {
            selected_root: selected_root.to_string(),
            trees: inventory.trees,
            files: inventory
                .blobs
                .into_iter()
                .map(|(path, file)| {
                    (
                        path,
                        GitFileDescriptor {
                            oid: file.oid.to_string(),
                            mode: file.mode,
                            size: file.size,
                        },
                    )
                })
                .collect(),
        })
    }

    /// Recheck object availability and sizes, not timestamps or historical counts.
    /// Each immutable header is verified at most once in this invocation.
    ///
    /// # Errors
    /// Rejects invalid descriptors, changed roots, missing objects and size drift.
    pub fn verify_cached_inventory(
        &self,
        commit: &str,
        prefix: &str,
        inventory: &GitInventoryDescriptor,
    ) -> Result<()> {
        inventory.validate()?;
        self.verify_inventory_objects(commit, prefix, inventory)
    }

    /// Recheck live Git objects without repeating immutable structural checks.
    ///
    /// # Errors
    /// Rejects changed roots, missing objects and size drift on every call.
    pub fn verify_validated_inventory(
        &self,
        commit: &str,
        prefix: &str,
        inventory: &ValidatedGitInventory,
    ) -> Result<()> {
        self.verify_inventory_objects(commit, prefix, inventory.descriptor())
    }

    fn verify_inventory_objects(
        &self,
        commit: &str,
        prefix: &str,
        inventory: &GitInventoryDescriptor,
    ) -> Result<()> {
        validate_prefix(prefix)?;
        let revision = self.resolve_commit(commit)?;
        let root = self.tree_below_prefix(ObjectId::from_hex(revision.tree.as_bytes())?, prefix)?;
        ensure!(
            root.to_string() == inventory.selected_root,
            "cached Git inventory root differs from pinned tree"
        );
        for oid in &inventory.trees {
            self.cached_header(oid, 1, Kind::Tree, MAX_METADATA_BYTES)?;
        }
        for file in inventory.files.values() {
            ensure!(
                self.cached_header(&file.oid, 2, Kind::Blob, MAX_BLOB_BYTES)? == file.size,
                "cached Git blob size differs from current object"
            );
        }
        Ok(())
    }

    fn cached_header(&self, oid: &str, kind_key: u8, kind: Kind, maximum: u64) -> Result<u64> {
        let key = (oid.to_owned(), kind_key);
        if let Some(size) = self.verified_headers.borrow().get(&key) {
            return Ok(*size);
        }
        let size = self.checked_header(ObjectId::from_hex(oid.as_bytes())?, kind, maximum)?;
        self.verified_headers.borrow_mut().insert(key, size);
        Ok(size)
    }

    /// Load one descriptor's bytes only when a comparison cell needs them.
    ///
    /// # Errors
    /// Rejects invalid modes/sizes, missing blobs and changed object sizes.
    pub fn read_described_file(&self, descriptor: &GitFileDescriptor) -> Result<Vec<u8>> {
        ensure!(
            matches!(descriptor.mode, 0o100_644 | 0o100_755) && descriptor.size <= MAX_BLOB_BYTES,
            "invalid Git file descriptor"
        );
        let oid = ObjectId::from_hex(descriptor.oid.as_bytes())?;
        ensure!(
            self.checked_header(oid, Kind::Blob, MAX_BLOB_BYTES)? == descriptor.size,
            "Git descriptor size changed"
        );
        let mut blob = self.repository.find_blob(oid)?;
        ensure!(
            blob.data.len() as u64 == descriptor.size,
            "Git blob size changed while reading"
        );
        Ok(blob.take_data())
    }

    fn parse_commit_id(&self, full_oid: &str) -> Result<ObjectId> {
        ensure!(
            full_oid.len() == self.repository.object_hash().len_in_hex()
                && full_oid.bytes().all(|byte| byte.is_ascii_hexdigit()),
            "Oracle commit must be a full hexadecimal object ID: {full_oid:?}"
        );
        ObjectId::from_hex(full_oid.as_bytes()).wrap_err("Invalid oracle commit object ID")
    }

    fn revision_for_commit(&self, reference: &str, oid: ObjectId) -> Result<GitRevision> {
        self.checked_header(oid, Kind::Commit, MAX_METADATA_BYTES)?;
        let commit = self.repository.find_commit(oid)?;
        let tree = commit
            .tree_id()
            .wrap_err_with(|| format!("Cannot decode oracle commit {oid}"))?
            .detach();
        self.checked_header(tree, Kind::Tree, MAX_METADATA_BYTES)?;
        Ok(GitRevision {
            reference: reference.to_owned(),
            commit: oid.to_string(),
            tree: tree.to_string(),
        })
    }

    fn checked_header(&self, oid: ObjectId, kind: Kind, maximum: u64) -> Result<u64> {
        let header = self
            .repository
            .find_header(oid)
            .wrap_err_with(|| format!("Cannot read raw oracle object {oid}"))?;
        ensure!(
            header.kind() == kind,
            "Oracle object {oid} is {:?}, expected {kind:?}",
            header.kind()
        );
        ensure!(
            header.size() <= maximum,
            "Oracle {kind:?} object {oid} is {} bytes, exceeding limit {maximum}",
            header.size()
        );
        Ok(header.size())
    }

    fn tree_below_prefix(&self, root: ObjectId, prefix: &str) -> Result<ObjectId> {
        let mut current = root;
        if prefix.is_empty() {
            return Ok(current);
        }
        for component in prefix.split('/') {
            self.checked_header(current, Kind::Tree, MAX_METADATA_BYTES)?;
            let tree = self.repository.find_tree(current)?;
            let mut found = None;
            let mut names = BTreeSet::new();
            for entry in tree.iter() {
                let entry = entry.wrap_err("Cannot decode oracle prefix tree")?;
                let name = checked_name(entry.filename().as_bytes())?;
                ensure!(
                    names.insert(name.to_uppercase()),
                    "Oracle prefix has duplicate/colliding name {name:?}"
                );
                if name == component {
                    ensure!(
                        u32::from(entry.mode().value()) == 0o040_000,
                        "Oracle prefix {prefix:?} includes a non-directory component {component:?}"
                    );
                    found = Some(entry.object_id());
                }
            }
            current = found
                .ok_or_else(|| eyre::eyre!("Oracle tree does not contain prefix {prefix:?}"))?;
        }
        Ok(current)
    }

    fn inventory_tree(
        &self,
        oid: ObjectId,
        parent: &str,
        depth: usize,
        include: &impl Fn(&str) -> bool,
        inventory: &mut Inventory,
    ) -> Result<()> {
        ensure!(
            depth <= MAX_TREE_DEPTH,
            "Oracle tree exceeds depth limit {MAX_TREE_DEPTH}"
        );
        self.checked_header(oid, Kind::Tree, MAX_METADATA_BYTES)?;
        inventory.trees.insert(oid.to_string());
        let tree = self.repository.find_tree(oid)?;
        let mut names = BTreeSet::new();
        for entry in tree.iter() {
            let entry = entry.wrap_err_with(|| format!("Cannot decode oracle tree {oid}"))?;
            inventory.entry_count += 1;
            ensure!(
                inventory.entry_count <= MAX_TREE_ENTRIES,
                "Oracle tree exceeds entry limit {MAX_TREE_ENTRIES}"
            );
            let name = checked_name(entry.filename().as_bytes())?;
            ensure!(
                names.insert(name.to_uppercase()),
                "Oracle tree has duplicate/colliding name {name:?} below {parent:?}"
            );
            let path = if parent.is_empty() {
                name.to_owned()
            } else {
                format!("{parent}/{name}")
            };
            ensure!(
                path.len() <= MAX_PATH_BYTES,
                "Oracle path exceeds {MAX_PATH_BYTES} bytes"
            );
            let mode = u32::from(entry.mode().value());
            if mode == 0o040_000 {
                self.inventory_tree(entry.object_id(), &path, depth + 1, include, inventory)?;
            } else if include(&path) {
                ensure!(
                    matches!(mode, 0o100_644 | 0o100_755),
                    "Unsupported oracle mode {mode:o} for {path}; symlinks and gitlinks are not source files"
                );
                let size = self.checked_header(entry.object_id(), Kind::Blob, MAX_BLOB_BYTES)?;
                inventory.total_bytes = inventory
                    .total_bytes
                    .checked_add(size)
                    .ok_or_else(|| eyre::eyre!("Oracle selected byte count overflow"))?;
                ensure!(
                    inventory.total_bytes <= MAX_SELECTED_BYTES,
                    "Oracle selected content exceeds {MAX_SELECTED_BYTES} bytes; use an explicit narrower scope"
                );
                ensure!(
                    inventory
                        .blobs
                        .insert(
                            path.clone(),
                            BlobEntry {
                                oid: entry.object_id(),
                                mode,
                                size
                            }
                        )
                        .is_none(),
                    "Duplicate oracle file {path}"
                );
            }
        }
        Ok(())
    }
}

fn validate_prefix(prefix: &str) -> Result<()> {
    ensure!(
        prefix.len() <= MAX_PATH_BYTES,
        "Oracle prefix exceeds {MAX_PATH_BYTES} bytes"
    );
    if !prefix.is_empty() {
        ensure!(
            prefix.split('/').count() <= MAX_TREE_DEPTH,
            "Oracle prefix exceeds depth limit {MAX_TREE_DEPTH}"
        );
        for component in prefix.split('/') {
            checked_name(component.as_bytes())?;
        }
    }
    Ok(())
}

fn checked_name(bytes: &[u8]) -> Result<&str> {
    let name = std::str::from_utf8(bytes).wrap_err("Oracle path contains a non-UTF-8 name")?;
    ensure!(
        !name.is_empty() && name != "." && name != "..",
        "Unsafe oracle path component {name:?}"
    );
    ensure!(
        name.encode_utf16().count() <= 255,
        "Oracle path component exceeds the Windows filename limit"
    );
    ensure!(
        !name
            .chars()
            .any(|ch| ch.is_control() || "\\/:*?\"<>|".contains(ch)),
        "Oracle path component is not safely representable on Windows: {name:?}"
    );
    ensure!(
        !name.ends_with([' ', '.']) && !name.eq_ignore_ascii_case(".git"),
        "Unsafe oracle path component {name:?}"
    );
    let stem = name
        .split('.')
        .next()
        .unwrap_or_default()
        .trim_end_matches(' ')
        .to_ascii_uppercase();
    let reserved = matches!(
        stem.as_str(),
        "CON" | "PRN" | "AUX" | "NUL" | "CONIN$" | "CONOUT$"
    ) || ((stem.starts_with("COM") || stem.starts_with("LPT"))
        && matches!(
            stem.get(3..),
            Some("1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" | "¹" | "²" | "³")
        ));
    ensure!(!reserved, "Reserved Windows oracle path component {name:?}");
    Ok(name)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use std::process::Command;
    use std::process::Stdio;

    struct Fixture {
        temp: tempfile::TempDir,
    }

    impl Fixture {
        fn new() -> Self {
            let fixture = Self {
                temp: tempfile::tempdir().unwrap(),
            };
            fixture.git(&["init", "--quiet"], None);
            fixture
        }

        fn git(&self, args: &[&str], input: Option<&[u8]>) -> String {
            let mut command = Command::new("git");
            command
                .arg("-C")
                .arg(self.temp.path())
                .args([
                    "-c",
                    "user.name=Oracle fixture",
                    "-c",
                    "user.email=oracle@example.invalid",
                    "-c",
                    "commit.gpgSign=false",
                    "-c",
                    "tag.gpgSign=false",
                ])
                .args(args)
                .env_remove("GIT_DIR")
                .env_remove("GIT_WORK_TREE")
                .env_remove("GIT_INDEX_FILE")
                .env_remove("GIT_NAMESPACE")
                .env("GIT_AUTHOR_NAME", "Oracle fixture")
                .env("GIT_AUTHOR_EMAIL", "oracle@example.invalid")
                .env("GIT_COMMITTER_NAME", "Oracle fixture")
                .env("GIT_COMMITTER_EMAIL", "oracle@example.invalid")
                .stdin(if input.is_some() {
                    Stdio::piped()
                } else {
                    Stdio::null()
                })
                .stdout(Stdio::piped())
                .stderr(Stdio::piped());
            let mut child = command.spawn().unwrap();
            if let Some(bytes) = input {
                child.stdin.take().unwrap().write_all(bytes).unwrap();
            }
            let output = child.wait_with_output().unwrap();
            assert!(
                output.status.success(),
                "git {args:?}: {}",
                String::from_utf8_lossy(&output.stderr)
            );
            String::from_utf8(output.stdout).unwrap().trim().to_owned()
        }

        fn object(&self, kind: &str, bytes: &[u8]) -> String {
            self.git(
                &["hash-object", "--literally", "-t", kind, "-w", "--stdin"],
                Some(bytes),
            )
        }

        fn tree(&self, entries: &[(u32, &[u8], &str)]) -> String {
            let mut bytes = Vec::new();
            for (mode, name, oid) in entries {
                bytes.extend_from_slice(format!("{mode:o} ").as_bytes());
                bytes.extend_from_slice(name);
                bytes.push(0);
                bytes.extend_from_slice(ObjectId::from_hex(oid.as_bytes()).unwrap().as_bytes());
            }
            self.object("tree", &bytes)
        }

        fn commit(&self, tree: &str) -> String {
            self.git(&["commit-tree", tree, "-m", "fixture"], None)
        }

        fn reader(&self) -> OracleGitRepository {
            OracleGitRepository::open(self.temp.path()).unwrap()
        }
    }

    #[test]
    fn direct_file_read_is_pinned_and_does_not_walk_unrelated_subtrees() {
        let fixture = Fixture::new();
        let blob = fixture.object("blob", b"class Example {}\n");
        let nested = fixture.tree(&[(0o100644, b"Example.java", &blob)]);
        let missing = "1111111111111111111111111111111111111111";
        let root = fixture.tree(&[
            (0o040000, b"src", &nested),
            (0o040000, b"unrelated", missing),
        ]);
        let commit = fixture.commit(&root);
        let reader = fixture.reader();
        assert_eq!(
            reader
                .file_at_commit(&commit, "src/Example.java")
                .unwrap()
                .unwrap()
                .bytes,
            b"class Example {}\n"
        );
        assert!(
            reader
                .file_at_commit(&commit, "src/Missing.java")
                .unwrap()
                .is_none()
        );
        assert!(
            reader
                .file_at_commit(&commit, "absent/Missing.java")
                .unwrap()
                .is_none()
        );
        assert!(
            reader
                .file_at_commit(&commit, "unrelated/Missing.java")
                .is_err()
        );
        for path in ["", "../src/Example.java", "src/Example.java/child"] {
            assert!(reader.file_at_commit(&commit, path).is_err());
        }
        assert!(reader.file_at_commit("HEAD", "src/Example.java").is_err());
    }

    #[test]
    fn direct_file_read_rejects_symlinks_and_missing_blobs() {
        let fixture = Fixture::new();
        let blob = fixture.object("blob", b"somewhere");
        let missing = "1111111111111111111111111111111111111111";
        let root = fixture.tree(&[(0o120000, b"link", &blob), (0o100644, b"missing", missing)]);
        let commit = fixture.commit(&root);
        let reader = fixture.reader();
        assert!(reader.file_at_commit(&commit, "link").is_err());
        assert!(reader.file_at_commit(&commit, "missing").is_err());
    }

    #[test]
    fn described_inventory_matches_raw_files_and_survives_serialization() {
        let fixture = Fixture::new();
        let blob = fixture.object("blob", b"class Example {}\n");
        let script = fixture.object("blob", b"#!/bin/sh\n");
        let nested = fixture.tree(&[(0o100644, b"Example.java", &blob)]);
        let root = fixture.tree(&[(0o100755, b"gradlew", &script), (0o040000, b"src", &nested)]);
        let commit = fixture.commit(&root);
        let reader = fixture.reader();
        let raw = reader.files_at_commit(&commit, "").unwrap();
        let described = reader
            .describe_at_commit_filtered(&commit, "", |_| true)
            .unwrap();
        let restored: GitInventoryDescriptor =
            facet_json::from_str(&facet_json::to_string(&described).unwrap()).unwrap();
        assert_eq!(restored, described);
        assert_eq!(restored.files.len(), raw.len());
        assert_eq!(restored.trees, BTreeSet::from([root, nested]));
        reader
            .verify_cached_inventory(&commit, "", &restored)
            .unwrap();
        let sealed = ValidatedGitInventory::new(restored.clone()).unwrap();
        reader
            .verify_validated_inventory(&commit, "", &sealed)
            .unwrap();
        let shared = sealed.clone();
        assert!(Arc::ptr_eq(&sealed.descriptor, &shared.descriptor));
        assert_eq!(sealed.identity(), restored.identity());
        let mut detached = sealed.shared_descriptor();
        Arc::make_mut(&mut detached)
            .files
            .values_mut()
            .next()
            .unwrap()
            .mode = 0o120000;
        assert!(detached.validate().is_err());
        assert_eq!(sealed.descriptor(), &restored);
        assert_eq!(sealed.identity(), restored.identity());
        let mut identity_bytes = Vec::new();
        for (path, file) in &raw {
            let descriptor = &restored.files[path];
            assert_eq!(descriptor.oid, file.oid);
            assert_eq!(descriptor.mode, file.mode);
            assert_eq!(descriptor.size, file.bytes.len() as u64);
            assert_eq!(reader.read_described_file(descriptor).unwrap(), file.bytes);
            identity_bytes.extend_from_slice(
                format!(
                    "{path}\0{:o}\0{}\0{}\n",
                    file.mode,
                    file.oid,
                    file.bytes.len()
                )
                .as_bytes(),
            );
        }
        assert_eq!(restored.identity(), sha256(&identity_bytes));
        let mut changed = restored.clone();
        changed.files.values_mut().next().unwrap().size += 1;
        assert!(
            reader
                .verify_cached_inventory(&commit, "", &changed)
                .is_err()
        );
        changed = restored;
        changed.files.values_mut().next().unwrap().mode = 0o120000;
        assert!(changed.validate().is_err());
        assert!(ValidatedGitInventory::new(changed).is_err());
        assert!(sealed.descriptor().validate().is_ok());
    }

    #[test]
    fn cached_inventory_rechecks_missing_blobs_and_nested_trees_after_restart() {
        for remove_tree in [false, true] {
            let fixture = Fixture::new();
            let blob = fixture.object("blob", b"example");
            let nested = fixture.tree(&[(0o100644, b"Example.java", &blob)]);
            let root = fixture.tree(&[(0o040000, b"src", &nested)]);
            let commit = fixture.commit(&root);
            let described = fixture
                .reader()
                .describe_at_commit_filtered(&commit, "", |_| true)
                .unwrap();
            let sealed = ValidatedGitInventory::new(described.clone()).unwrap();
            let oid = if remove_tree { &nested } else { &blob };
            let object = fixture
                .temp
                .path()
                .join(".git/objects")
                .join(&oid[..2])
                .join(&oid[2..]);
            assert!(object.starts_with(fixture.temp.path()));
            std::fs::remove_file(object).unwrap();
            assert!(
                fixture
                    .reader()
                    .verify_cached_inventory(&commit, "", &described)
                    .is_err()
            );
            assert!(
                fixture
                    .reader()
                    .verify_validated_inventory(&commit, "", &sealed)
                    .is_err()
            );
        }
    }

    #[test]
    fn full_refs_peel_tags_and_preserve_frozen_commit_after_branch_moves() {
        let fixture = Fixture::new();
        let blob = fixture.object("blob", b"release\r\n");
        let tree = fixture.tree(&[(0o100_644, b"build.gradle", &blob)]);
        let first = fixture.commit(&tree);
        fixture.git(&["update-ref", "refs/heads/1.19.2", &first], None);
        fixture.git(&["tag", "lightweight", &first], None);
        fixture.git(&["tag", "-a", "annotated", &first, "-m", "release"], None);
        let reader = fixture.reader();
        for reference in [
            "refs/heads/1.19.2",
            "refs/tags/lightweight",
            "refs/tags/annotated",
        ] {
            assert_eq!(
                reader.resolve_reference(reference).unwrap(),
                GitRevision {
                    reference: reference.to_owned(),
                    commit: first.clone(),
                    tree: tree.clone(),
                }
            );
        }
        for invalid in [
            "1.19.2",
            "HEAD",
            "refs/heads/1.19.2^",
            "refs/replace/test",
            "refs/tags/missing",
        ] {
            assert!(reader.resolve_reference(invalid).is_err(), "{invalid}");
        }
        assert!(reader.resolve_commit(&first[..12]).is_err());
        assert!(reader.resolve_commit(&tree).is_err());
        assert!(reader.resolve_commit(&"0".repeat(40)).is_err());
        let second_blob = fixture.object("blob", b"development\n");
        let second_tree = fixture.tree(&[(0o100_644, b"build.gradle", &second_blob)]);
        let second = fixture.commit(&second_tree);
        fixture.git(&["update-ref", "refs/heads/1.19.2", &second], None);
        assert_eq!(
            reader
                .resolve_reference("refs/heads/1.19.2")
                .unwrap()
                .commit,
            second
        );
        assert_eq!(
            reader.files_at_commit(&first, "").unwrap()["build.gradle"].bytes,
            b"release\r\n"
        );
    }

    #[test]
    fn raw_files_are_prefix_relative_binary_exact_and_retain_modes() {
        let fixture = Fixture::new();
        let binary = fixture.object("blob", &[0, 255, 13, 10]);
        let empty = fixture.object("blob", b"");
        let project = fixture.tree(&[
            (0o100_644, b"asset.bin", &binary),
            (0o100_755, b"gradlew", &empty),
        ]);
        let platform = fixture.tree(&[(0o040_000, b"minecraft", &project)]);
        let root = fixture.tree(&[(0o040_000, b"platform", &platform)]);
        let commit = fixture.commit(&root);
        let reader = fixture.reader();
        let files = reader
            .files_at_commit(&commit, "platform/minecraft")
            .unwrap();
        assert_eq!(
            files.keys().map(String::as_str).collect::<Vec<_>>(),
            ["asset.bin", "gradlew"]
        );
        assert_eq!(files["asset.bin"].bytes, [0, 255, 13, 10]);
        assert_eq!(files["asset.bin"].oid, binary);
        assert_eq!(files["gradlew"].mode, 0o100_755);
        assert!(files["gradlew"].bytes.is_empty());
        let filtered = reader
            .files_at_commit_filtered(&commit, "platform/minecraft", |path| path == "gradlew")
            .unwrap();
        assert_eq!(filtered.len(), 1);
        assert!(reader.files_at_commit(&commit, "platform/missing").is_err());
        for unsafe_prefix in [
            "/platform",
            "platform/",
            "../platform",
            "platform//minecraft",
            "C:/platform",
            "platform\\minecraft",
        ] {
            assert!(
                reader.files_at_commit(&commit, unsafe_prefix).is_err(),
                "{unsafe_prefix}"
            );
        }
    }

    #[test]
    fn replacement_objects_and_local_namespace_do_not_change_raw_pins() {
        let fixture = Fixture::new();
        let old_blob = fixture.object("blob", b"original");
        let old_tree = fixture.tree(&[(0o100_644, b"source.txt", &old_blob)]);
        let old_commit = fixture.commit(&old_tree);
        let new_blob = fixture.object("blob", b"replacement");
        let new_tree = fixture.tree(&[(0o100_644, b"source.txt", &new_blob)]);
        let new_commit = fixture.commit(&new_tree);
        fixture.git(&["replace", &old_commit, &new_commit], None);
        fixture.git(&["replace", &old_blob, &new_blob], None);
        fixture.git(&["update-ref", "refs/heads/1.19.2", &old_commit], None);
        fixture.git(&["config", "gitoxide.objects.noReplace", "false"], None);
        fixture.git(&["config", "gitoxide.core.refsNamespace", "wrong"], None);
        let reader = fixture.reader();
        assert_eq!(
            reader.resolve_reference("refs/heads/1.19.2").unwrap().tree,
            old_tree
        );
        assert_eq!(
            reader.files_at_commit(&old_commit, "").unwrap()["source.txt"].bytes,
            b"original"
        );
    }

    #[test]
    fn unsupported_modes_non_utf8_and_colliding_names_fail_explicitly() {
        let fixture = Fixture::new();
        let blob = fixture.object("blob", b"target");
        let empty_tree = fixture.tree(&[]);
        let target_commit = fixture.commit(&empty_tree);
        for entries in [
            vec![(0o120_000, b"link".as_slice(), blob.as_str())],
            vec![(0o160_000, b"module".as_slice(), target_commit.as_str())],
            vec![(0o100_644, &[255][..], blob.as_str())],
            vec![
                (0o100_644, b"A.java".as_slice(), blob.as_str()),
                (0o100_644, b"a.java".as_slice(), blob.as_str()),
            ],
            vec![(0o100_644, b"CON.txt".as_slice(), blob.as_str())],
            vec![(0o100_644, b"unsafe:stream".as_slice(), blob.as_str())],
            vec![(0o100_644, b"trailing.".as_slice(), blob.as_str())],
            vec![(0o100_644, b"..".as_slice(), blob.as_str())],
        ] {
            let tree = fixture.tree(&entries);
            let commit = fixture.commit(&tree);
            assert!(
                fixture.reader().files_at_commit(&commit, "").is_err(),
                "{entries:?}"
            );
        }
    }

    #[test]
    fn excluded_missing_blobs_are_not_loaded_but_selected_missing_objects_fail() {
        let fixture = Fixture::new();
        let missing = "1".repeat(40);
        let tree = fixture.tree(&[(0o100_644, b"excluded.txt", &missing)]);
        // Raw fixture construction deliberately allows a missing referenced blob.
        let raw_commit = format!(
            "tree {tree}\nauthor Oracle fixture <oracle@example.invalid> 0 +0000\ncommitter Oracle fixture <oracle@example.invalid> 0 +0000\n\nfixture\n"
        );
        let commit = fixture.object("commit", raw_commit.as_bytes());
        let reader = fixture.reader();
        assert!(reader.files_at_commit(&commit, "").is_err());
        assert!(
            reader
                .files_at_commit_filtered(&commit, "", |_| false)
                .unwrap()
                .is_empty()
        );
    }

    #[test]
    fn linked_worktree_uses_shared_refs_and_objects() {
        let fixture = Fixture::new();
        let blob = fixture.object("blob", b"shared");
        let tree = fixture.tree(&[(0o100_644, b"source.txt", &blob)]);
        let commit = fixture.commit(&tree);
        fixture.git(&["update-ref", "refs/heads/1.19.2", &commit], None);
        let linked = fixture.temp.path().join("linked");
        fixture.git(
            &[
                "worktree",
                "add",
                "--quiet",
                "--detach",
                linked.to_str().unwrap(),
                &commit,
            ],
            None,
        );
        let reader = OracleGitRepository::open(&linked).unwrap();
        assert_eq!(
            reader
                .resolve_reference("refs/heads/1.19.2")
                .unwrap()
                .commit,
            commit
        );
        assert_eq!(
            reader.files_at_commit(&commit, "").unwrap()["source.txt"].bytes,
            b"shared"
        );
    }
}
