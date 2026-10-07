//! Invocation input store; retained bytes alone do not authorize execution.
//! Invocation-local immutable input snapshots and retained directory leases.
//! Reuses Windows ancestry leases and keeps read-shared files alive until drop.
//! Failure leaves the fresh directory for diagnosis; never overwrites or cleans.

use super::core_input_leases::DirectoryLeases;
use super::nfrt_producer_output_receipt::SealedNfrtProducerOutput;
use super::nfrt_project_owner::NfrtProjectOwner;
use super::provenance::sha256;
use crate::jar_build::nfrt_launch_contract::PreparedNfrtInvocationInput;
use eyre::Result;
use eyre::ensure;
use sha2::Digest;
use sha2::Sha256;
use std::fs::File;
use std::fs::OpenOptions;
use std::fs::{self};
use std::io::Read;
use std::io::Write;
use std::os::windows::fs::MetadataExt;
use std::os::windows::fs::OpenOptionsExt;
use std::path::Path;
use std::path::PathBuf;
use windows::Win32::Storage::FileSystem::FILE_ATTRIBUTE_REPARSE_POINT;
use windows::Win32::Storage::FileSystem::FILE_FLAG_OPEN_REPARSE_POINT;
use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;

const MAX_INPUTS: usize = 8192;
const MAX_BYTES: usize = 1024 * 1024 * 1024;

pub(crate) struct NfrtInputStore {
    root: PathBuf,
    contract_identity: String,
    project_preparation_identity: String,
    workspaces_declared: bool,
    generated: std::collections::BTreeMap<String, String>,
    // Retain file and directory sharing exclusions for the entire invocation.
    files: Vec<File>,
    leases: DirectoryLeases,
}

impl NfrtInputStore {
    pub(crate) fn create(
        project: &dyn NfrtProjectOwner,
        prepared: &PreparedNfrtInvocationInput,
    ) -> Result<Self> {
        project.recheck_source()?;
        let receipt = prepared.receipt();
        ensure!(
            receipt.preparation_identity == project.preparation_identity()?,
            "NFRT input store belongs to another checked project"
        );
        ensure!(
            facet_json::to_string(receipt)? == prepared.serialized(),
            "NFRT serialized contract differs from its private prepared receipt"
        );
        ensure!(
            !receipt.native_execution_enabled && !receipt.os_filesystem_attested,
            "Input transfer cannot claim native execution or prior filesystem attestation"
        );
        ensure!(
            receipt.fresh_work_root_required
                && !receipt.intermediate_cache_restore
                && !receipt.intermediate_cache_persistence
                && !receipt.launcher_probing,
            "NFRT input store requires the closed-input fresh-work contract"
        );
        let token = &receipt.invocation_id;
        validate_snapshots(prepared)?;

        let project_root = project.project().project_root();
        let (root, mut leases) = create_owned_root(project_root, token)?;
        let mut files = Vec::with_capacity(receipt.inputs.len() + 1);
        for (entry, bytes) in receipt.inputs.iter().zip(prepared.snapshots()) {
            let path = leases.prepare(&root, &entry.snapshot_relative_path)?;
            files.push(write_and_hold(&path, bytes, &entry.full_sha256)?);
        }
        let contract_path = leases.prepare(&root, "contract.json")?;
        files.push(write_and_hold(
            &contract_path,
            prepared.serialized().as_bytes(),
            &prepared.contract_identity(),
        )?);
        project.recheck_source()?;
        Ok(Self {
            root,
            contract_identity: prepared.contract_identity(),
            project_preparation_identity: project.preparation_identity()?,
            workspaces_declared: false,
            generated: std::collections::BTreeMap::new(),
            files,
            leases,
        })
    }

    pub(crate) fn root(&self) -> &Path {
        &self.root
    }

    pub(crate) fn contract_identity(&self) -> &str {
        &self.contract_identity
    }

    /// Materialize the CLI's embedded adapter in this fresh invocation, never
    /// adopt compiled classes or Java source from an external fixture/cache.
    pub(crate) fn publish_host_sources(
        &mut self,
        sources: &[(&str, &[u8])],
    ) -> Result<(Vec<PathBuf>, PathBuf)> {
        ensure!(
            !self.workspaces_declared,
            "Host compilation follows graph execution"
        );
        ensure!(
            sources.len() == 14,
            "Incomplete embedded host source bundle"
        );
        let mut names = std::collections::BTreeSet::new();
        let mut total = 0_usize;
        for (name, bytes) in sources {
            let stem = name
                .strip_suffix(".java")
                .ok_or_else(|| eyre::eyre!("Host source is not Java"))?;
            ensure!(
                !stem.is_empty()
                    && stem.len() <= 128
                    && stem.bytes().all(|b| b.is_ascii_alphanumeric())
                    && names.insert(name.to_ascii_lowercase())
                    && !bytes.is_empty(),
                "Unsafe, empty or duplicated host source"
            );
            total = total
                .checked_add(bytes.len())
                .ok_or_else(|| eyre::eyre!("Host source byte overflow"))?;
            ensure!(total <= 2 * 1024 * 1024, "Oversized host source bundle");
        }
        for relative in ["adapter", "adapter/src", "adapter/classes"] {
            let path = self.leases.prepare(&self.root, relative)?;
            fs::create_dir(&path)?;
            self.leases.prepare(&path, "reserved-leaf")?;
        }
        let mut paths = Vec::with_capacity(sources.len());
        for (name, bytes) in sources {
            let path = self
                .leases
                .prepare(&self.root, &format!("adapter/src/{name}"))?;
            self.files
                .push(write_and_hold(&path, bytes, &sha256(bytes))?);
            paths.push(path);
        }
        Ok((paths, self.root.join("adapter/classes")))
    }

    /// Seal the completed javac output into one immutable classpath JAR.
    /// Java must not load from the writable compiler output directory.
    pub(crate) fn publish_host_jar(&mut self, sources: &[(&str, &[u8])]) -> Result<PathBuf> {
        let mut directory = self.root.join("adapter/classes");
        for component in ["ca", "teamdman", "sfm", "toolchain", "nfrt"] {
            self.leases.prepare(&directory, "reserved-leaf")?;
            let children = fs::read_dir(&directory)?.collect::<std::io::Result<Vec<_>>>()?;
            ensure!(
                children.len() == 1 && children[0].file_name() == component,
                "Host compiler emitted an unexpected package tree"
            );
            directory.push(component);
        }
        self.leases.prepare(&directory, "reserved-leaf")?;
        let expected = sources
            .iter()
            .map(|(name, _)| {
                name.strip_suffix(".java")
                    .ok_or_else(|| eyre::eyre!("Invalid embedded source name"))
            })
            .collect::<Result<std::collections::BTreeSet<_>>>()?;
        let mut classes = fs::read_dir(&directory)?.collect::<std::io::Result<Vec<_>>>()?;
        ensure!(
            (14..=256).contains(&classes.len()),
            "Invalid compiled host class count"
        );
        classes.sort_by_key(fs::DirEntry::file_name);
        let mut top_level = std::collections::BTreeSet::new();
        let mut archive = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
        let mut total = 0_usize;
        for class in classes {
            let name = class
                .file_name()
                .into_string()
                .map_err(|name| eyre::eyre!("Host class name is not UTF-8: {name:?}"))?;
            let stem = name
                .strip_suffix(".class")
                .ok_or_else(|| eyre::eyre!("Host compiler emitted a non-class file"))?;
            ensure!(
                stem.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'$')
                    && expected.contains(stem.split('$').next().unwrap_or_default()),
                "Host compiler emitted a foreign class"
            );
            if !stem.contains('$') {
                top_level.insert(stem.to_owned());
            }
            let mut file = OpenOptions::new()
                .read(true)
                .share_mode(FILE_SHARE_READ.0)
                .custom_flags(FILE_FLAG_OPEN_REPARSE_POINT.0)
                .open(class.path())?;
            let metadata = file.metadata()?;
            ensure!(
                metadata.is_file()
                    && metadata.len() > 0
                    && metadata.len() <= 1024 * 1024
                    && metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 == 0,
                "Compiled host class is not a bounded regular file"
            );
            let identity = crate::file_identity::file_identity(&file)?;
            let mut bytes = Vec::new();
            (&mut file).take(1024 * 1024 + 1).read_to_end(&mut bytes)?;
            ensure!(
                bytes.len() as u64 == metadata.len()
                    && bytes.starts_with(&[0xca, 0xfe, 0xba, 0xbe])
                    && crate::file_identity::file_identity(&file)? == identity
                    && file.metadata()?.len() == metadata.len(),
                "Compiled host class changed during sealing"
            );
            total = total
                .checked_add(bytes.len())
                .ok_or_else(|| eyre::eyre!("Host class byte overflow"))?;
            ensure!(
                total <= 16 * 1024 * 1024,
                "Compiled host bundle is oversized"
            );
            archive.start_file(
                format!("ca/teamdman/sfm/toolchain/nfrt/{name}"),
                zip::write::SimpleFileOptions::default()
                    .compression_method(zip::CompressionMethod::Stored),
            )?;
            archive.write_all(&bytes)?;
            self.files.push(file);
        }
        ensure!(
            top_level
                .iter()
                .map(String::as_str)
                .collect::<std::collections::BTreeSet<_>>()
                == expected,
            "Host compiler omitted an embedded top-level class"
        );
        let bytes = archive.finish()?.into_inner();
        let path = self
            .leases
            .prepare(&self.root, "adapter/host-adapter.jar")?;
        self.files
            .push(write_and_hold(&path, &bytes, &sha256(&bytes))?);
        Ok(path)
    }

    /// Snapshot the source-owned access transformer, never a physical-file guess.
    /// This descriptor is bound to both the original contract and current source
    /// ownership. Empty/absent inputs remain explicit; no inferred AT is added.
    pub(crate) fn publish_project_inputs(&mut self, project: &dyn NfrtProjectOwner) -> Result<()> {
        const AT: &str = "src/main/resources/META-INF/accesstransformer.cfg";
        #[derive(facet::Facet)]
        struct ProjectInput {
            source_output: String,
            snapshot_relative_path: String,
            bytes: usize,
            sha256: String,
        }
        #[derive(facet::Facet)]
        struct Descriptor {
            schema: String,
            contract_identity: String,
            project_preparation_identity: String,
            inputs: Vec<ProjectInput>,
        }
        project.recheck_source()?;
        ensure!(
            project.preparation_identity()? == self.project_preparation_identity,
            "Project input belongs to another invocation source owner"
        );
        let mut inputs = Vec::new();
        if project.project().receipt().files.contains_key(AT) {
            let selected = project.project().selected_input(AT)?;
            let bytes = selected.output_bytes();
            ensure!(
                bytes.len() <= 128 * 1024,
                "Oversized project access transformer"
            );
            let relative = "inputs/project/accesstransformer.cfg";
            let parent = self.root.join("inputs/project");
            fs::create_dir(&parent)?;
            let path = self.leases.prepare(&self.root, relative)?;
            let hash = sha256(bytes);
            self.files.push(write_and_hold(&path, bytes, &hash)?);
            inputs.push(ProjectInput {
                source_output: AT.to_owned(),
                snapshot_relative_path: relative.to_owned(),
                bytes: bytes.len(),
                sha256: hash,
            });
        }
        let descriptor = Descriptor {
            schema: "sfm:nfrt_project_inputs@1".to_owned(),
            contract_identity: self.contract_identity.clone(),
            project_preparation_identity: project.preparation_identity()?,
            inputs,
        };
        let text = facet_json::to_string(&descriptor)?;
        let path = self.leases.prepare(&self.root, "project-inputs.json")?;
        self.files.push(write_and_hold(
            &path,
            text.as_bytes(),
            &sha256(text.as_bytes()),
        )?);
        project.recheck_source()?;
        Ok(())
    }

    /// Publish only immutable copies sealed for this invocation's contract.
    /// Existing paths are never adopted. Repeated consumers reuse our retained
    /// file handle only after the entire batch has passed identity checks.
    pub(crate) fn publish_generated(
        &mut self,
        outputs: &[SealedNfrtProducerOutput],
    ) -> Result<Vec<PathBuf>> {
        ensure!(
            self.workspaces_declared,
            "Generated outputs require the owned graph workspaces"
        );
        ensure!(outputs.len() <= 256, "Oversized generated consumer batch");
        let mut batch = std::collections::BTreeMap::new();
        let mut total = 0_usize;
        for output in outputs {
            output.recheck()?;
            let receipt = output.receipt();
            ensure!(
                receipt.scope.prepared_contract_sha256 == self.contract_identity,
                "Generated output belongs to another prepared invocation"
            );
            let name = receipt
                .snapshot_relative_path
                .strip_prefix("inputs/generated/")
                .ok_or_else(|| eyre::eyre!("Generated output is outside the snapshot root"))?;
            let slot = name
                .strip_suffix(".bin")
                .ok_or_else(|| eyre::eyre!("Generated output has another suffix"))?;
            ensure!(
                slot.len() == 4 && slot.bytes().all(|byte| byte.is_ascii_digit()),
                "Generated output has a noncanonical slot"
            );
            if let Some(previous) = self.generated.get(&receipt.snapshot_relative_path) {
                ensure!(
                    previous == &receipt.full_sha256,
                    "Previously published generated slot changed"
                );
            }
            if let Some(previous) = batch.insert(
                receipt.snapshot_relative_path.clone(),
                receipt.full_sha256.clone(),
            ) {
                ensure!(
                    previous == receipt.full_sha256,
                    "Conflicting generated consumers"
                );
            } else {
                total = total
                    .checked_add(receipt.bytes)
                    .ok_or_else(|| eyre::eyre!("Generated byte overflow"))?;
                ensure!(total <= MAX_BYTES, "Generated batch is oversized");
            }
        }
        let mut paths = Vec::with_capacity(outputs.len());
        for output in outputs {
            let receipt = output.receipt();
            let path = self
                .leases
                .prepare(&self.root, &receipt.snapshot_relative_path)?;
            if !self.generated.contains_key(&receipt.snapshot_relative_path) {
                let file = write_and_hold(&path, output.bytes(), &receipt.full_sha256)?;
                self.files.push(file);
                self.generated.insert(
                    receipt.snapshot_relative_path.clone(),
                    receipt.full_sha256.clone(),
                );
            }
            paths.push(path);
        }
        Ok(paths)
    }

    /// Preflight the complete fresh graph before any directory creation.
    /// A failed creation poisons this declaration; retained directories are
    /// diagnostic evidence, never reusable invocation workspaces.
    pub(crate) fn create_workspaces(&mut self, node_ids: &[String]) -> Result<()> {
        ensure!(
            !self.workspaces_declared,
            "Workspace graph already declared"
        );
        ensure!(
            !node_ids.is_empty() && node_ids.len() <= 256,
            "Invalid workspace graph size"
        );
        let mut names = std::collections::BTreeSet::new();
        let mut paths = Vec::with_capacity(node_ids.len());
        for id in node_ids {
            ensure!(
                !id.is_empty()
                    && id.len() <= 128
                    && id
                        .bytes()
                        .all(|byte| byte.is_ascii_alphanumeric() || byte == b'_' || byte == b'-'),
                "Unsafe workspace node ID"
            );
            let folded = id.to_ascii_lowercase();
            ensure!(
                !["con", "prn", "aux", "nul"].contains(&folded.as_str())
                    && !(folded.len() == 4
                        && (folded.starts_with("com") || folded.starts_with("lpt"))
                        && matches!(folded.as_bytes()[3], b'1'..=b'9')),
                "Reserved Windows workspace node ID"
            );
            ensure!(
                names.insert(folded),
                "Duplicate or case-aliased workspace node ID"
            );
            let path = self.leases.prepare(&self.root, &format!("work/{id}"))?;
            match fs::symlink_metadata(&path) {
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => (),
                Err(error) => return Err(error.into()),
                Ok(_) => eyre::bail!("Workspace path already exists"),
            }
            paths.push(path);
        }
        self.workspaces_declared = true;
        for path in paths {
            fs::create_dir(&path)?;
            self.leases.prepare(&path, "reserved-leaf")?;
        }
        Ok(())
    }
}

fn validate_snapshots(prepared: &PreparedNfrtInvocationInput) -> Result<()> {
    let entries = &prepared.receipt().inputs;
    let snapshots = prepared.snapshots();
    ensure!(
        !entries.is_empty() && entries.len() <= MAX_INPUTS && entries.len() == snapshots.len(),
        "Invalid NFRT snapshot cardinality"
    );
    let mut total = 0_usize;
    for (index, (entry, bytes)) in entries.iter().zip(snapshots).enumerate() {
        ensure!(
            entry.snapshot_relative_path
                == crate::jar_build::nfrt_launch_contract::original_snapshot_path(
                    index,
                    entry.coordinate.as_deref()
                ),
            "NFRT input has a non-generated or reordered snapshot path"
        );
        ensure!(
            !bytes.is_empty() && bytes.len() == entry.bytes && sha256(bytes) == entry.full_sha256,
            "NFRT input snapshot identity changed"
        );
        total = total
            .checked_add(bytes.len())
            .ok_or_else(|| eyre::eyre!("NFRT byte count overflow"))?;
        ensure!(total <= MAX_BYTES, "NFRT snapshot batch is oversized");
    }
    Ok(())
}

fn create_owned_root(project_root: &Path, token: &str) -> Result<(PathBuf, DirectoryLeases)> {
    ensure!(
        !token.is_empty()
            && token.len() <= 128
            && token
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_'),
        "Unsafe NFRT invocation token"
    );
    let mut leases = DirectoryLeases::default();
    let mut parent = project_root.to_owned();
    // Acquire each parent before creating or accepting its next child.
    // No create_dir_all traversal can silently follow a reparse point.
    leases.prepare(&parent, "reserved-leaf")?;
    for component in ["build", "sfm-toolchain", "nfrt-invocations"] {
        let next = parent.join(component);
        match fs::create_dir(&next) {
            Ok(()) => (),
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => (),
            Err(error) => return Err(error.into()),
        }
        leases.prepare(&next, "reserved-leaf")?;
        parent = next;
    }
    let root = parent.join(format!("nfrt-{token}"));
    // Existing invocations, even apparently complete ones, are refused.
    fs::create_dir(&root)?;
    leases.prepare(&root, "reserved-leaf")?;
    // Rust is the sole creator. Java binds this root without creating it.
    for relative in [
        "home",
        "work",
        "inputs",
        "inputs/artifacts",
        "inputs/generated",
    ] {
        let directory = root.join(relative);
        fs::create_dir(&directory)?;
        leases.prepare(&directory, "reserved-leaf")?;
    }
    Ok((root, leases))
}

fn write_and_hold(path: &Path, bytes: &[u8], expected: &str) -> Result<File> {
    let mut output = OpenOptions::new()
        .write(true)
        .create_new(true)
        .custom_flags(FILE_FLAG_OPEN_REPARSE_POINT.0)
        .open(path)?;
    output.write_all(bytes)?;
    output.sync_all()?;
    drop(output);
    // Reopen after writing, verify through this held handle, then retain it.
    // A concurrent replacement in the gap must match the full expected bytes.
    let mut input = OpenOptions::new()
        .read(true)
        .share_mode(FILE_SHARE_READ.0)
        .custom_flags(FILE_FLAG_OPEN_REPARSE_POINT.0)
        .open(path)?;
    let metadata = input.metadata()?;
    ensure!(
        metadata.is_file()
            && metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 == 0
            && metadata.len() == u64::try_from(bytes.len())?,
        "NFRT snapshot is not the expected regular file"
    );
    let mut hash = Sha256::new();
    let mut total = 0_usize;
    let mut buffer = vec![0_u8; 64 * 1024];
    loop {
        let count = input.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        total = total
            .checked_add(count)
            .ok_or_else(|| eyre::eyre!("NFRT snapshot read overflow"))?;
        ensure!(total <= bytes.len(), "NFRT snapshot grew while reading");
        hash.update(&buffer[..count]);
    }
    ensure!(
        total == bytes.len() && format!("sha256:{:x}", hash.finalize()) == expected,
        "NFRT snapshot held-handle hash mismatch"
    );
    Ok(input)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn embedded_host_jar_guards_membership_and_holds_its_classpath_bytes() -> Result<()> {
        // Header-only class fixtures exercise filesystem/ZIP guards, not JVM
        // validity. Actual Java compilation/loading has its separate real run.
        for variant in ["complete", "missing", "foreign"] {
            let fixture = tempfile::tempdir()?;
            let (root, leases) = create_owned_root(fixture.path(), "host-bundle")?;
            let mut store = NfrtInputStore {
                root,
                contract_identity: format!("sha256:{}", "0".repeat(64)),
                project_preparation_identity: format!("sha256:{}", "1".repeat(64)),
                workspaces_declared: false,
                generated: std::collections::BTreeMap::new(),
                files: Vec::new(),
                leases,
            };
            let names = (0..14)
                .map(|index| format!("SFMFixture{index}.java"))
                .collect::<Vec<_>>();
            let sources = names
                .iter()
                .map(|name| (name.as_str(), b"fixture only".as_slice()))
                .collect::<Vec<_>>();
            let (_, mut directory) = store.publish_host_sources(&sources)?;
            for component in ["ca", "teamdman", "sfm", "toolchain", "nfrt"] {
                directory.push(component);
                fs::create_dir(&directory)?;
            }
            for index in 0..if variant == "missing" { 13 } else { 14 } {
                fs::write(
                    directory.join(format!("SFMFixture{index}.class")),
                    [0xca, 0xfe, 0xba, 0xbe, 0],
                )?;
            }
            if variant == "foreign" {
                fs::write(directory.join("Foreign.class"), [0xca, 0xfe, 0xba, 0xbe, 0])?;
            }
            let result = store.publish_host_jar(&sources);
            if variant == "complete" {
                let path = result?;
                let archive = zip::ZipArchive::new(File::open(&path)?)?;
                assert_eq!(archive.len(), 14);
                assert!(OpenOptions::new().write(true).open(&path).is_err());
                assert!(
                    store.publish_host_jar(&sources).is_err(),
                    "Existing sealed JAR must not be overwritten"
                );
                drop(archive);
            } else {
                assert!(
                    result.is_err(),
                    "Incomplete/foreign classes were admitted: {variant}"
                );
                assert!(!store.root().join("adapter/host-adapter.jar").exists());
            }
            drop(store);
        }
        Ok(())
    }

    #[test]
    fn fresh_root_is_complete_leased_and_never_adopts_previous_invocations() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        let (root, mut leases) = create_owned_root(fixture.path(), "proof-1")?;
        for relative in ["home", "work", "inputs/artifacts", "inputs/generated"] {
            ensure!(
                root.join(relative).is_dir(),
                "Invocation layout is incomplete"
            );
        }
        let path = leases.prepare(&root, "inputs/generated/frame.jar")?;
        let bytes = b"sealed producer";
        let held = write_and_hold(&path, bytes, &sha256(bytes))?;
        ensure!(
            fs::read(&path)? == bytes,
            "Held ancestry blocked child reading"
        );
        ensure!(
            fs::rename(&root, root.with_file_name("moved-root")).is_err(),
            "Root was replaceable"
        );
        ensure!(
            create_owned_root(fixture.path(), "proof-1").is_err(),
            "Live root was adopted"
        );
        drop(held);
        drop(leases);
        ensure!(
            create_owned_root(fixture.path(), "proof-1").is_err(),
            "Retained root was adopted"
        );
        ensure!(fs::read(&path)? == bytes, "Refusal changed retained input");
        fs::rename(&root, root.with_file_name("moved-root"))?;
        Ok(())
    }

    #[test]
    fn invalid_invocation_tokens_fail_before_creating_build_tree() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        for token in [
            "",
            "../escape",
            "a/b",
            "a\\b",
            ".",
            "with space",
            "with:colon",
        ] {
            ensure!(
                create_owned_root(fixture.path(), token).is_err(),
                "Unsafe token was accepted"
            );
            ensure!(
                !fixture.path().join("build").exists(),
                "Invalid token had write effects"
            );
        }
        ensure!(
            create_owned_root(fixture.path(), &"x".repeat(129)).is_err(),
            "Oversized token was accepted"
        );
        ensure!(
            !fixture.path().join("build").exists(),
            "Oversized token had write effects"
        );
        Ok(())
    }

    #[test]
    fn non_directory_build_parent_is_refused_without_changing_it() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        let path = fixture.path().join("build");
        fs::write(&path, b"not a directory")?;
        ensure!(
            create_owned_root(fixture.path(), "proof").is_err(),
            "File parent was accepted"
        );
        ensure!(
            fs::read(&path)? == b"not a directory",
            "Refusal changed file parent"
        );
        Ok(())
    }

    #[test]
    fn snapshot_allows_child_reads_but_blocks_write_rename_and_delete_until_drop() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        let path = fixture.path().join("snapshot.bin");
        let bytes = b"verified input";
        let held = write_and_hold(&path, bytes, &sha256(bytes))?;
        ensure!(fs::read(&path)? == bytes, "Child read was blocked");
        ensure!(
            OpenOptions::new().write(true).open(&path).is_err(),
            "Concurrent write was allowed"
        );
        ensure!(
            fs::rename(&path, fixture.path().join("moved.bin")).is_err(),
            "Rename was allowed"
        );
        ensure!(fs::remove_file(&path).is_err(), "Delete was allowed");
        drop(held);
        ensure!(
            OpenOptions::new().write(true).open(&path).is_ok(),
            "Sharing exclusion outlived its owner"
        );
        Ok(())
    }

    #[test]
    fn repeat_does_not_adopt_or_overwrite_an_existing_snapshot() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        let path = fixture.path().join("snapshot.bin");
        let bytes = b"original";
        let held = write_and_hold(&path, bytes, &sha256(bytes))?;
        ensure!(
            write_and_hold(&path, b"replacement", &sha256(b"replacement")).is_err(),
            "Existing snapshot was adopted"
        );
        ensure!(fs::read(&path)? == bytes, "Existing snapshot changed");
        drop(held);
        ensure!(
            write_and_hold(&path, bytes, &sha256(bytes)).is_err(),
            "Identical existing bytes bypassed create-only semantics"
        );
        Ok(())
    }

    #[test]
    fn mismatched_digest_refuses_a_store_handle_and_preserves_diagnostic_bytes() -> Result<()> {
        let fixture = tempfile::tempdir()?;
        let path = fixture.path().join("snapshot.bin");
        ensure!(
            write_and_hold(&path, b"actual", &sha256(b"different")).is_err(),
            "Wrong digest produced a held snapshot"
        );
        ensure!(
            fs::read(&path)? == b"actual",
            "Failure cleaned or changed diagnostic input"
        );
        Ok(())
    }
}
