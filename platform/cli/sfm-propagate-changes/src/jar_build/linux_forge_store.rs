//! Fresh MCP/Forge/remap productions in a separate Linux developer-cache namespace.
//! No retained legacy output is imported. Open descriptors and repeated content
//! checks detect boundary changes; they are not exclusion against a same-user writer.
use crate::jar_build::local_dependency_cache::linux_io::{Directory, HeldFile, Lease};
#[path = "linux_forge_sources.rs"]
mod sources;
#[cfg(test)]
#[path = "linux_forge_store_tests.rs"]
mod tests;
use crate::cancellation::CancellationToken;
use eyre::{Result, ensure};
use facet::Facet;
use std::collections::BTreeSet;
use std::fs;
use std::io::{Read as _, Write as _};
use std::os::unix::fs::MetadataExt as _;
use std::path::{Path, PathBuf};

unsafe extern "C" {
    fn geteuid() -> u32;
}
fn current_uid() -> u32 {
    // SAFETY: geteuid has no pointer arguments and reads our credential.
    unsafe { geteuid() }
}

const OUTPUT_BOUND: u64 = 512 * 1024 * 1024;
const TOTAL_BOUND: u64 = 2 * 1024 * 1024 * 1024;

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct Output {
    role: String,
    bytes: u64,
    sha256: String,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct Production {
    schema: String,
    kind: String,
    key: String,
    attempt: String,
    completion: String,
    outputs: Vec<Output>,
}

#[derive(Debug)]
pub(super) struct Store {
    directory: Directory,
    kind_directory: Directory,
    key: String,
    kind: String,
    lease: Option<Lease>,
    held: Vec<HeldFile>,
    entry: Option<Directory>,
    source_tree: std::sync::Mutex<Option<Directory>>,
    roles: Vec<String>,
    pub(super) fresh: bool,
}

impl Store {
    pub(super) fn begin(
        root: &Path,
        kind: &str,
        key: &str,
        roles: &[&str],
        cancel: &CancellationToken,
    ) -> Result<Self> {
        ensure!(
            matches!(kind, "mcp" | "forge" | "remap"),
            "unknown Linux preparation kind"
        );
        let digest = key
            .strip_prefix("sha256:")
            .ok_or_else(|| eyre::eyre!("invalid Linux preparation key"))?;
        ensure!(
            digest.len() == 64 && digest.bytes().all(|b| b.is_ascii_hexdigit()),
            "invalid Linux preparation key"
        );
        ensure!(
            !roles.is_empty()
                && roles.len() <= 5
                && roles.iter().copied().collect::<BTreeSet<_>>().len() == roles.len(),
            "invalid production roles"
        );
        ensure!(
            roles.iter().all(|r| !r.is_empty()
                && !r.contains(['/', '\\', '\0'])
                && !matches!(*r, "." | ".." | "production.json")),
            "unsafe production role"
        );
        let root = Directory::open(root)?.child("forge-linux-v1", true)?;
        root.require_owner()?;
        // Initialization is serialized before checking whether a new namespace
        // is empty. The sole lock is never authority for preexisting payloads.
        let initialize = root.lock("initialize.lock", cancel)?;
        let marker = b"sfm:linux_forge_fresh_productions@1\n";
        if !exists(&root, "owner.json")? {
            ensure!(
                fs::read_dir(root.access(""))?
                    .all(|e| e.is_ok_and(|e| e.file_name() == "initialize.lock")),
                "nonempty unmarked Forge namespace is not adopted"
            );
            root.publish("owner.json", marker, cancel)?;
        }
        let owner = HeldFile::capture(&root, "owner.json", 4096, cancel)?;
        ensure!(
            owner.content(cancel)? == marker,
            "Forge namespace owner differs"
        );
        initialize.recheck()?;
        drop(initialize);
        let kind_directory = root.child(kind, true)?;
        kind_directory.require_owner()?;
        let directory = kind_directory.child(digest, true)?;
        directory.require_owner()?;
        let lease = directory.lock("producer.lock", cancel)?;
        Ok(Self {
            directory,
            kind_directory,
            key: key.to_owned(),
            kind: kind.to_owned(),
            lease: Some(lease),
            held: vec![owner],
            entry: None,
            source_tree: std::sync::Mutex::new(None),
            roles: roles.iter().map(|s| (*s).to_owned()).collect(),
            fresh: false,
        })
    }

    pub(super) fn recheck(&self, cancel: &CancellationToken) -> Result<()> {
        cancel.bail_if_cancelled()?;
        self.directory.recheck()?;
        self.directory.require_owner()?;
        self.kind_directory.require_owner()?;
        if let Some(lease) = &self.lease {
            lease.recheck()?;
        }
        for held in &self.held {
            held.directory.require_owner()?;
            held.recheck(cancel)?;
            crate::jar_build::local_dependency_cache::linux_io::readonly_owned(&held.file)?;
        }
        if let Some(entry) = &self.entry {
            self.require_membership(entry)?;
        }
        if let Some(tree) = self
            .source_tree
            .lock()
            .map_err(|_poisoned| eyre::eyre!("source tree hold poisoned"))?
            .as_ref()
        {
            sources::verify_tree(
                tree,
                &self
                    .entry
                    .as_ref()
                    .ok_or_else(|| eyre::eyre!("source archive not held"))?
                    .access("deobfuscated_sources.jar"),
                cancel,
                || Ok(()),
            )?;
        }
        Ok(())
    }

    fn require_membership(&self, entry: &Directory) -> Result<()> {
        let expected = self
            .roles
            .iter()
            .map(String::as_str)
            .chain(["production.json"])
            .collect::<BTreeSet<_>>();
        for item in fs::read_dir(entry.access(""))? {
            let name = item?
                .file_name()
                .into_string()
                .map_err(|_non_unicode| eyre::eyre!("non-Unicode production member"))?;
            ensure!(
                expected.contains(name.as_str()),
                "unexpected production payload member"
            );
        }
        Ok(())
    }

    pub(super) fn lookup(&mut self, cancel: &CancellationToken) -> Result<bool> {
        self.recheck(cancel)?;
        if !exists(&self.directory, "current.json")? {
            return Ok(false);
        }
        let head = HeldFile::capture(&self.directory, "current.json", 4096, cancel)?;
        let name: String = facet_json::from_slice(&head.content(cancel)?)?;
        ensure!(valid_generation(&name, "entry-"), "invalid production head");
        let entry = self.directory.child(&name, false)?;
        entry.require_owner()?;
        self.require_membership(&entry)?;
        let manifest = HeldFile::capture(&entry, "production.json", 65536, cancel)?;
        let production: Production = facet_json::from_slice(&manifest.content(cancel)?)?;
        ensure!(
            production.schema == "sfm:linux_forge_production@1"
                && production.kind == self.kind
                && production.key == self.key
                && production.completion == "successful_owned_execution"
                && valid_generation(&production.attempt, "attempt-")
                && production.outputs.len() == self.roles.len(),
            "production authority differs"
        );
        let mut captured_files = vec![head, manifest];
        let mut total = 0u64;
        for (output, role) in production.outputs.iter().zip(&self.roles) {
            ensure!(&output.role == role, "production output role differs");
            let file = HeldFile::capture(&entry, role, OUTPUT_BOUND, cancel)?;
            ensure!(
                file.bytes == output.bytes && file.digest == output.sha256,
                "production payload differs; no repair or legacy fallback performed"
            );
            total = total
                .checked_add(file.bytes)
                .ok_or_else(|| eyre::eyre!("production size overflow"))?;
            ensure!(total <= TOTAL_BOUND, "production payload exceeds bound");
            captured_files.push(file);
        }
        self.held.extend(captured_files);
        self.entry = Some(entry);
        self.recheck(cancel)?;
        Ok(true)
    }

    /// Only this owned fresh attempt can reach publication. The callback must
    /// return successful completion, and input authority is rechecked on both
    /// sides by the caller. Interrupted attempts remain unselected evidence.
    pub(super) fn produce(
        &mut self,
        cancel: &CancellationToken,
        produce: impl FnOnce(&Directory) -> Result<Vec<PathBuf>>,
        check: impl Fn() -> Result<()>,
    ) -> Result<()> {
        self.recheck(cancel)?;
        ensure!(
            self.lease.is_some()
                && self.entry.is_none()
                && !exists(&self.directory, "current.json")?,
            "fresh production requires an absent new-generation head"
        );
        let attempt_name = format!("attempt-{:032x}", rand::random::<u128>());
        let attempt = fresh_directory(&self.directory, &attempt_name)?;
        attempt.require_owner()?;
        check()?;
        let paths = produce(&attempt)?;
        check()?;
        attempt.recheck()?;
        ensure!(
            paths.len() == self.roles.len(),
            "fresh production output count differs"
        );
        let entry = fresh_directory(
            &self.directory,
            &format!("entry-{:032x}", rand::random::<u128>()),
        )?;
        entry.require_owner()?;
        let mut outputs = Vec::new();
        let mut source_holds = Vec::new();
        let mut total = 0u64;
        for (role, path) in self.roles.iter().zip(paths) {
            let (output, input) =
                copy_output(&attempt, &entry, role, &path, TOTAL_BOUND - total, cancel)?;
            total = total
                .checked_add(input.bytes)
                .ok_or_else(|| eyre::eyre!("production size overflow"))?;
            ensure!(total <= TOTAL_BOUND, "production payload exceeds bound");
            outputs.push(output);
            source_holds.push(input);
        }
        check()?;
        attempt.recheck()?;
        self.recheck(cancel)?;
        let production = Production {
            schema: "sfm:linux_forge_production@1".into(),
            kind: self.kind.clone(),
            key: self.key.clone(),
            attempt: attempt_name,
            completion: "successful_owned_execution".into(),
            outputs,
        };
        entry.publish(
            "production.json",
            facet_json::to_string(&production)?.as_bytes(),
            cancel,
        )?;
        check()?;
        for source in &source_holds {
            source.recheck(cancel)?;
        }
        for output in &production.outputs {
            let published = HeldFile::capture(&entry, &output.role, OUTPUT_BOUND, cancel)?;
            crate::jar_build::local_dependency_cache::linux_io::readonly_owned(&published.file)?;
            ensure!(
                published.digest == output.sha256 && published.bytes == output.bytes,
                "production changed before head publication"
            );
        }
        entry.recheck()?;
        self.directory.publish(
            "current.json",
            facet_json::to_string(
                entry
                    .path
                    .file_name()
                    .and_then(|s| s.to_str())
                    .ok_or_else(|| eyre::eyre!("invalid entry name"))?,
            )?
            .as_bytes(),
            cancel,
        )?;
        ensure!(
            self.lookup(cancel)?,
            "completed production cannot be reopened"
        );
        self.fresh = true;
        Ok(())
    }

    pub(super) fn path(&self, role: &str) -> Result<PathBuf> {
        ensure!(self.roles.iter().any(|r| r == role), "unknown output role");
        Ok(self
            .entry
            .as_ref()
            .ok_or_else(|| eyre::eyre!("production is not selected"))?
            .path
            .join(role))
    }

    pub(super) fn release_producer(&mut self) {
        drop(self.lease.take());
    }
}

fn exists(directory: &Directory, name: &str) -> Result<bool> {
    match fs::symlink_metadata(directory.access(name)) {
        Ok(_) => Ok(true),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(false),
        Err(e) => Err(e.into()),
    }
}

fn valid_generation(name: &str, prefix: &str) -> bool {
    name.strip_prefix(prefix)
        .is_some_and(|s| s.len() == 32 && s.bytes().all(|b| b.is_ascii_hexdigit()))
}

fn fresh_directory(parent: &Directory, name: &str) -> Result<Directory> {
    use std::os::unix::fs::DirBuilderExt as _;
    ensure!(
        valid_generation(name, "attempt-")
            || valid_generation(name, "entry-")
            || valid_generation(name, "source-tree-"),
        "invalid fresh generation name"
    );
    parent.recheck()?;
    parent.require_owner()?;
    fs::DirBuilder::new()
        .mode(0o700)
        .create(parent.access(name))?;
    parent.sync()?;
    let directory = parent.child(name, false)?;
    directory.require_owner()?;
    ensure!(
        fs::read_dir(directory.access(""))?.next().is_none(),
        "fresh generation is not empty"
    );
    Ok(directory)
}

fn copy_output(
    attempt: &Directory,
    entry: &Directory,
    role: &str,
    path: &Path,
    remaining: u64,
    cancel: &CancellationToken,
) -> Result<(Output, HeldFile)> {
    let relative = path.strip_prefix(&attempt.path)?;
    let mut parent = attempt.clone();
    for part in relative
        .parent()
        .ok_or_else(|| eyre::eyre!("output has no parent"))?
        .components()
    {
        let std::path::Component::Normal(part) = part else {
            eyre::bail!("non-normal fresh output");
        };
        parent = parent.child(
            part.to_str()
                .ok_or_else(|| eyre::eyre!("invalid output directory"))?,
            false,
        )?;
        parent.require_owner()?;
    }
    let name = path
        .file_name()
        .and_then(|s| s.to_str())
        .ok_or_else(|| eyre::eyre!("invalid output leaf"))?;
    let input = HeldFile::capture(&parent, name, OUTPUT_BOUND, cancel)?;
    ensure!(
        input.bytes <= remaining,
        "production payload exceeds aggregate bound"
    );
    // Producer outputs may be writable, but must be ours and cannot be
    // group/world writable. Published anonymous copies are read-only.
    ensure!(
        input.file.metadata()?.uid() == current_uid(),
        "output owner differs"
    );
    ensure!(
        input.file.metadata()?.mode() & 0o022 == 0,
        "unsafe producer output mode"
    );
    entry.publish_with(
        role,
        |file| {
            let mut source = parent.read(name)?;
            let mut copied = 0u64;
            let mut buffer = vec![0; 64 * 1024];
            loop {
                cancel.bail_if_cancelled()?;
                let count = source.read(&mut buffer)?;
                if count == 0 {
                    break;
                }
                copied += u64::try_from(count)?;
                ensure!(
                    copied <= input.bytes,
                    "fresh output grew during publication"
                );
                file.write_all(&buffer[..count])?;
            }
            ensure!(
                copied == input.bytes,
                "fresh output length changed during publication"
            );
            input.recheck(cancel)?;
            Ok(())
        },
        cancel,
    )?;
    let published = HeldFile::capture(entry, role, OUTPUT_BOUND, cancel)?;
    ensure!(
        published.digest == input.digest && published.bytes == input.bytes,
        "fresh output changed during publication"
    );
    let output = Output {
        role: role.to_owned(),
        bytes: input.bytes,
        sha256: input.digest.clone(),
    };
    Ok((output, input))
}
