//! Owned data layout for historical `JUnit` filesystem fixtures.
//! Selected source bytes remain test data, never new product dependencies.
use super::BuildPlan;
use crate::cancellation::CancellationToken;
use crate::jar_build::build_snapshot_receipt::{
    checked_descendant, ensure_owned_directory, read_bounded,
};
use crate::source_projection::candidate_lock::{checked_directory, is_reparse};
use crate::source_projection::provenance::sha256;
use eyre::{Result, ensure};
use facet::Facet;
use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

const MAX_FILE: u64 = 8 * 1024 * 1024;
const MAX_TOTAL: u64 = 256 * 1024 * 1024;
const MAX_ENTRIES: usize = 32_768;
const MANIFEST: &str = "fixture-inputs.json";
const GENERATED: &str = "build/generated-src/antlr/main";

#[derive(Clone, Debug, Eq, PartialEq, Facet)]
struct Entry {
    bytes: u64,
    sha256: String,
}

#[derive(Clone, Debug, Eq, PartialEq, Facet)]
struct Manifest {
    schema: String,
    source_owner_sha256: String,
    generated_source_present: bool,
    directories: BTreeSet<String>,
    files: BTreeMap<String, Entry>,
}

struct Snapshot {
    manifest: Manifest,
    payload: BTreeMap<String, Vec<u8>>,
    total: u64,
}

impl Snapshot {
    fn new(owner: String) -> Self {
        Self {
            manifest: Manifest {
                schema: "sfm:junit_fixture_snapshot@1".to_owned(),
                source_owner_sha256: owner,
                generated_source_present: false,
                directories: BTreeSet::new(),
                files: BTreeMap::new(),
            },
            payload: BTreeMap::new(),
            total: 0,
        }
    }

    fn directory(&mut self, relative: &str) -> Result<()> {
        let mut path = Some(Path::new(relative));
        while let Some(current) = path.filter(|path| !path.as_os_str().is_empty()) {
            self.manifest.directories.insert(portable(current)?);
            path = current.parent();
        }
        ensure!(
            self.manifest.directories.len() + self.payload.len() <= MAX_ENTRIES,
            "JUnit fixture entry budget exceeded"
        );
        Ok(())
    }

    fn file(&mut self, relative: &str, bytes: Vec<u8>) -> Result<()> {
        ensure!(
            bytes.len() as u64 <= MAX_FILE,
            "JUnit fixture input exceeds the single-file bound"
        );
        self.total = self
            .total
            .checked_add(bytes.len() as u64)
            .ok_or_else(|| eyre::eyre!("JUnit fixture byte count overflow"))?;
        ensure!(
            self.total <= MAX_TOTAL,
            "JUnit fixture total byte budget exceeded"
        );
        let parent = Path::new(relative)
            .parent()
            .ok_or_else(|| eyre::eyre!("JUnit fixture input lacks a parent"))?;
        self.directory(&portable(parent)?)?;
        let entry = Entry {
            bytes: bytes.len() as u64,
            sha256: sha256(&bytes),
        };
        ensure!(
            self.manifest
                .files
                .insert(relative.to_owned(), entry)
                .is_none(),
            "Duplicate JUnit fixture output"
        );
        self.payload.insert(relative.to_owned(), bytes);
        ensure!(
            self.manifest.directories.len() + self.payload.len() <= MAX_ENTRIES,
            "JUnit fixture entry budget exceeded"
        );
        Ok(())
    }

    fn tree(
        &mut self,
        source: &Path,
        prefix: &str,
        required: bool,
        cancel: &CancellationToken,
    ) -> Result<bool> {
        match fs::symlink_metadata(source) {
            Err(error) if error.kind() == std::io::ErrorKind::NotFound && !required => {
                let mut parent = source.parent();
                while let Some(directory) = parent {
                    match fs::symlink_metadata(directory) {
                        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                            parent = directory.parent();
                        }
                        result => {
                            result?;
                            checked_directory(directory)?;
                            break;
                        }
                    }
                }
                return Ok(false);
            }
            result => {
                result?;
            }
        }
        let source = checked_directory(source)?;
        self.directory(prefix)?;
        for entry in walkdir::WalkDir::new(&source)
            .follow_links(false)
            .min_depth(1)
            .max_depth(129)
            .sort_by_file_name()
        {
            cancel.bail_if_cancelled()?;
            let entry = entry?;
            ensure!(
                entry.depth() <= 128,
                "JUnit fixture source nesting budget exceeded"
            );
            let metadata = fs::symlink_metadata(entry.path())?;
            ensure!(
                !is_reparse(&metadata),
                "JUnit fixture source contains a link or reparse point"
            );
            let relative = format!(
                "{prefix}/{}",
                portable(entry.path().strip_prefix(&source)?)?
            );
            if metadata.is_dir() {
                self.directory(&relative)?;
            } else {
                ensure!(
                    metadata.is_file(),
                    "JUnit fixture source is not a regular file"
                );
                self.file(&relative, read_bounded(entry.path(), MAX_FILE)?)?;
            }
        }
        Ok(true)
    }

    fn selected_directories(&mut self, project: &Path, cancel: &CancellationToken) -> Result<()> {
        let source = checked_directory(&project.join("src"))?;
        self.directory("src")?;
        for entry in walkdir::WalkDir::new(&source)
            .follow_links(false)
            .min_depth(1)
            .max_depth(129)
            .sort_by_file_name()
        {
            cancel.bail_if_cancelled()?;
            let entry = entry?;
            ensure!(
                entry.depth() <= 128,
                "JUnit selected source nesting budget exceeded"
            );
            let metadata = fs::symlink_metadata(entry.path())?;
            ensure!(
                !is_reparse(&metadata),
                "JUnit selected source directory contains a link"
            );
            let relative = format!("src/{}", portable(entry.path().strip_prefix(&source)?)?);
            if metadata.is_dir() {
                self.directory(&relative)?;
            } else {
                ensure!(
                    metadata.is_file() && self.payload.contains_key(&relative),
                    "JUnit fixture encountered an unselected source file"
                );
            }
        }
        Ok(())
    }

    fn validate(&self, owner: &Path) -> Result<String> {
        let mut names = BTreeSet::new();
        for relative in self
            .manifest
            .directories
            .iter()
            .chain(self.manifest.files.keys())
        {
            ensure!(
                !relative.contains('\\'),
                "JUnit fixture paths must use portable separators"
            );
            checked_descendant(owner, &owner.join(relative))?;
            ensure!(
                names.insert(relative.to_ascii_lowercase()),
                "JUnit fixture contains a file/directory or case alias"
            );
        }
        let text = facet_json::to_string(&self.manifest)?;
        ensure!(
            text.len() as u64 <= MAX_FILE,
            "JUnit fixture manifest exceeds its bound"
        );
        Ok(text)
    }
}

fn portable(path: &Path) -> Result<String> {
    let text = path
        .to_str()
        .ok_or_else(|| eyre::eyre!("JUnit fixture path must be Unicode"))?;
    #[cfg(not(windows))]
    ensure!(
        !text.contains('\\'),
        "JUnit fixture path contains a nonportable backslash"
    );
    Ok(text.replace('\\', "/"))
}

fn selected(plan: &BuildPlan) -> bool {
    let key = plan.target_label();
    let version = key
        .strip_prefix("sfm-4.34.0/mc-")
        .or_else(|| key.strip_prefix("sfm-4.35.0/mc-"));
    version.is_some_and(|version| {
        crate::source_projection::projection_catalog::SUPPORTED_TARGETS
            .iter()
            .any(|(target, _)| *target == version)
    }) && plan.named_project().is_some_and(|project| {
        project.project().receipt().environment
            == crate::source_projection::projection_catalog::ProjectionEnvironment::Release
    })
}

fn capture(plan: &BuildPlan, cancel: &CancellationToken) -> Result<Snapshot> {
    cancel.bail_if_cancelled()?;
    plan.recheck_named_inputs()?;
    let project = plan
        .named_project()
        .ok_or_else(|| eyre::eyre!("JUnit fixture requires its retained catalog owner"))?
        .project();
    let mut snapshot = Snapshot::new(sha256(facet_json::to_string(project.receipt())?.as_bytes()));
    for input in project.retained_selected_inputs()? {
        cancel.bail_if_cancelled()?;
        if input.output_path().starts_with("src/") {
            snapshot.file(input.output_path(), input.output_bytes().to_vec())?;
        }
    }
    ensure!(
        snapshot
            .payload
            .keys()
            .any(|path| path.starts_with("src/main/java/")),
        "JUnit fixture has no selected main source tree"
    );
    snapshot.selected_directories(project.project_root(), cancel)?;
    snapshot.tree(
        &project.repo_root().join("examples"),
        "examples",
        true,
        cancel,
    )?;
    snapshot.manifest.generated_source_present = snapshot.tree(
        &project.project_root().join(GENERATED),
        GENERATED,
        false,
        cancel,
    )?;
    plan.recheck_named_inputs()?;
    Ok(snapshot)
}

#[derive(Facet)]
struct Pointer {
    schema: String,
    directory: String,
    manifest_sha256: String,
}

pub(super) struct JunitFixture {
    root: PathBuf,
    manifest: String,
    identity: String,
}

impl JunitFixture {
    pub(super) fn prepare(plan: &BuildPlan, cancel: &CancellationToken) -> Result<Option<Self>> {
        if !selected(plan) {
            return Ok(None);
        }
        let snapshot = capture(plan, cancel)?;
        let fixture = publish(&plan.cache_dir, &snapshot, cancel)?;
        fixture.recheck(plan, cancel)?;
        Ok(Some(fixture))
    }

    pub(super) fn root(&self) -> &Path {
        &self.root
    }
    pub(super) fn identity(&self) -> &str {
        &self.identity
    }

    pub(super) fn recheck(&self, plan: &BuildPlan, cancel: &CancellationToken) -> Result<()> {
        ensure!(selected(plan), "JUnit fixture owner scope changed");
        let current = capture(plan, cancel)?;
        ensure!(
            current.validate(plan.repository_root())? == self.manifest,
            "JUnit fixture source inputs or membership changed"
        );
        verify(&self.root, &current.manifest, &self.manifest, cancel)
    }
}

fn write_new(owner: &Path, path: &Path, bytes: &[u8]) -> Result<()> {
    checked_descendant(owner, path)?;
    ensure_owned_directory(
        owner,
        path.parent()
            .ok_or_else(|| eyre::eyre!("JUnit fixture destination lacks parent"))?,
    )?;
    let mut file = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(path)?;
    file.write_all(bytes)?;
    file.sync_all()?;
    Ok(())
}

fn publish(cache: &Path, snapshot: &Snapshot, cancel: &CancellationToken) -> Result<JunitFixture> {
    // Match native cache ownership's canonical Win32 spelling after rejecting
    // every reparse ancestor; Java also consumes this directory as its CWD.
    let cache = dunce::canonicalize(checked_directory(cache)?)?;
    let manifest = snapshot.validate(&cache)?;
    let identity = sha256(manifest.as_bytes());
    let root = cache.join("junit-fixtures");
    ensure_owned_directory(&cache, &root)?;
    let index = root.join(format!(
        "input-{}.json",
        identity.trim_start_matches("sha256:")
    ));
    let _lock = super::acquire_artifact_path_lock_cancellable(&index, cancel)?;
    let directory = if index.exists() {
        let pointer: Pointer = facet_json::from_slice(&read_bounded(&index, MAX_FILE)?)?;
        ensure!(
            pointer.schema == "sfm:junit_fixture_index@1"
                && pointer.manifest_sha256 == identity
                && pointer.directory.starts_with("fixture-")
                && !pointer.directory.contains(['/', '\\']),
            "JUnit fixture index differs from its selected inputs"
        );
        checked_descendant(&root, &root.join(&pointer.directory))?;
        root.join(pointer.directory)
    } else {
        let staging = tempfile::Builder::new()
            .prefix("fixture-")
            .tempdir_in(&root)?;
        for directory in &snapshot.manifest.directories {
            cancel.bail_if_cancelled()?;
            ensure_owned_directory(staging.path(), &staging.path().join(directory))?;
        }
        for (relative, bytes) in &snapshot.payload {
            cancel.bail_if_cancelled()?;
            write_new(staging.path(), &staging.path().join(relative), bytes)?;
        }
        write_new(
            staging.path(),
            &staging.path().join(MANIFEST),
            manifest.as_bytes(),
        )?;
        verify(staging.path(), &snapshot.manifest, &manifest, cancel)?;
        let directory = staging.keep();
        let pointer = Pointer {
            schema: "sfm:junit_fixture_index@1".to_owned(),
            directory: directory
                .file_name()
                .and_then(|value| value.to_str())
                .ok_or_else(|| eyre::eyre!("JUnit fixture directory is not Unicode"))?
                .to_owned(),
            manifest_sha256: identity.clone(),
        };
        let mut pending = tempfile::NamedTempFile::new_in(&root)?;
        pending.write_all(facet_json::to_string(&pointer)?.as_bytes())?;
        pending.as_file().sync_all()?;
        pending
            .persist_noclobber(&index)
            .map_err(|error| error.error)?;
        directory
    };
    verify(&directory, &snapshot.manifest, &manifest, cancel)?;
    Ok(JunitFixture {
        root: directory,
        manifest,
        identity,
    })
}

fn verify(root: &Path, manifest: &Manifest, text: &str, cancel: &CancellationToken) -> Result<()> {
    let root = checked_directory(root)?;
    ensure!(
        fixture_bytes(&root.join(MANIFEST))? == text.as_bytes(),
        "JUnit fixture manifest changed"
    );
    let mut files = BTreeSet::new();
    let mut directories = BTreeSet::new();
    for entry in walkdir::WalkDir::new(&root)
        .follow_links(false)
        .min_depth(1)
        .max_depth(129)
        .sort_by_file_name()
    {
        cancel.bail_if_cancelled()?;
        let entry = entry?;
        ensure!(
            entry.depth() <= 128,
            "JUnit fixture output nesting budget exceeded"
        );
        let metadata = fs::symlink_metadata(entry.path())?;
        ensure!(
            !is_reparse(&metadata),
            "JUnit fixture output contains a link or reparse point"
        );
        let relative = portable(entry.path().strip_prefix(&root)?)?;
        ensure!(
            files.len() + directories.len() <= MAX_ENTRIES,
            "JUnit fixture output entry budget exceeded"
        );
        if metadata.is_dir() {
            ensure!(
                manifest.directories.contains(&relative),
                "JUnit fixture contains an unexpected directory"
            );
            directories.insert(relative);
        } else if relative == MANIFEST {
            ensure!(
                metadata.is_file(),
                "JUnit fixture manifest is not a regular file"
            );
        } else {
            let expected = manifest
                .files
                .get(&relative)
                .ok_or_else(|| eyre::eyre!("JUnit fixture contains an unexpected file"))?;
            let bytes = fixture_bytes(entry.path())?;
            ensure!(
                bytes.len() as u64 == expected.bytes && sha256(&bytes) == expected.sha256,
                "JUnit fixture output bytes changed"
            );
            files.insert(relative);
        }
    }
    ensure!(
        files == manifest.files.keys().cloned().collect() && directories == manifest.directories,
        "JUnit fixture output membership changed"
    );
    Ok(())
}

fn fixture_bytes(path: &Path) -> Result<Vec<u8>> {
    let file = super::project_compilation_cache::open_read(path)?;
    ensure!(
        file.metadata()?.len() <= MAX_FILE,
        "JUnit fixture file exceeds its bound"
    );
    let mut bytes = Vec::new();
    file.take(MAX_FILE + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= MAX_FILE,
        "JUnit fixture file grew beyond its bound"
    );
    Ok(bytes)
}

#[derive(Facet)]
struct WorkingDirectory {
    schema: String,
    directory: String,
    fixture_snapshot_sha256: Option<String>,
}

pub(super) fn write_working_directory_record(
    cache: &Path,
    path: &Path,
    directory: &Path,
    fixture: Option<&JunitFixture>,
) -> Result<String> {
    let record = WorkingDirectory {
        schema: "sfm:junit_working_directory@1".to_owned(),
        directory: directory
            .to_str()
            .ok_or_else(|| eyre::eyre!("JUnit working directory is not Unicode"))?
            .to_owned(),
        fixture_snapshot_sha256: fixture.map(|fixture| fixture.identity().to_owned()),
    };
    let contents = facet_json::to_string(&record)?;
    super::project_compilation_cache::write_record(cache, path, contents.as_bytes())?;
    Ok(sha256(contents.as_bytes()))
}

#[cfg(test)]
#[path = "junit_fixture_tests.rs"]
mod tests;
