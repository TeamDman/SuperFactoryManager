//! Successful build outputs owned independently of compiler staging and live JVMs.
//!
//! Snapshots are append-only by convention. Every consumer verifies the exact
//! receipt and BLAKE3 inventory before using output bytes; a caller must hold the
//! projection build lock while publishing a successful compiler result.
use super::json_path::JsonOptionalPath;
use super::json_path::JsonPath;
use super::nfrt_preparation_phase::NfrtPreparationPhase;

#[cfg(windows)]
#[path = "build_snapshot_source_capture.rs"]
mod source_capture;
use crate::cancellation::CancellationToken;
use crate::jdk::ResolvedJava;
use crate::source_projection::candidate_lock::is_reparse;
use eyre::Result;
use eyre::WrapErr as _;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::ffi::OsStr;
use std::fs;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;

const SCHEMA: &str = "sfm:successful_build_snapshot@1";
const MAX_FILES: usize = 100_000;
const MAX_FILE_BYTES: u64 = 64 * 1024 * 1024;
const MAX_TOTAL_BYTES: u64 = 512 * 1024 * 1024;
const ROOT_NAMES: [&str; 6] = [
    "classes",
    "staged-resources",
    "gametest/classes",
    "gametest/resources",
    "datagen/classes",
    "datagen/resources",
];

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub(crate) struct BuildSnapshotReceipt {
    pub(crate) schema: String,
    #[facet(proxy = JsonPath)]
    pub(crate) snapshot_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    pub(crate) minecraft_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    pub(crate) cache_dir: PathBuf,
    pub(crate) snapshot_id: String,
    pub(crate) minecraft_version: String,
    pub(crate) java_major: u32,
    pub(crate) sdk_identity: String,
    pub(crate) runtime_contract: String,
    #[facet(proxy = JsonPath)]
    pub(crate) java_executable: PathBuf,
    #[facet(proxy = JsonOptionalPath)]
    pub(crate) java_home: Option<PathBuf>,
    pub(crate) java_version_output: String,
    pub(crate) java_selection: String,
    pub(crate) java_pin_url: Option<String>,
    pub(crate) java_pin_sha512: Option<String>,
    pub(crate) files: BTreeMap<String, String>,
    /// Includes empty directories so adding an unrecorded directory is detected.
    pub(crate) directories: BTreeSet<String>,
}

impl BuildSnapshotReceipt {
    #[cfg(test)]
    pub(crate) fn publish(
        minecraft_dir: &Path,
        cache_dir: &Path,
        minecraft_version: &str,
        java: &ResolvedJava,
        project_dir: &Path,
        sdk_identity: &str,
    ) -> Result<Self> {
        Self::publish_cancellable(
            minecraft_dir,
            cache_dir,
            minecraft_version,
            java,
            project_dir,
            (sdk_identity, "fixture-runtime-contract"),
            &CancellationToken::new(),
        )
    }

    pub(crate) fn publish_cancellable(
        minecraft_dir: &Path,
        cache_dir: &Path,
        minecraft_version: &str,
        java: &ResolvedJava,
        project_dir: &Path,
        identities: (&str, &str),
        cancellation_token: &CancellationToken,
    ) -> Result<Self> {
        cancellation_token.bail_if_cancelled()?;
        let (sdk_identity, runtime_contract) = identities;
        let minecraft_dir = canonical_directory(minecraft_dir)?;
        let cache_dir = canonical_directory(cache_dir)?;
        ensure!(
            !checked_descendant(&minecraft_dir, &cache_dir)?
                .as_os_str()
                .is_empty(),
            "build cache must be owned by the selected Minecraft projection"
        );
        ensure!(
            same_path_spelling(
                &canonical_directory(project_dir)?,
                &cache_dir.join("project")
            ),
            "build snapshot requires the selected cache project directory"
        );
        let snapshots = cache_dir.join("build-snapshots");
        ensure_owned_directory(&cache_dir, &snapshots)?;
        let staging = tempfile::Builder::new()
            .prefix("snapshot-")
            .tempdir_in(&snapshots)?;
        let snapshot_dir = canonical_directory(staging.path())?;
        let snapshot_id = snapshot_dir
            .file_name()
            .and_then(|name| name.to_str())
            .ok_or_else(|| eyre::eyre!("snapshot name is not UTF-8"))?
            .to_owned();
        let mut receipt = Self {
            schema: SCHEMA.to_owned(),
            snapshot_dir,
            minecraft_dir,
            cache_dir,
            snapshot_id,
            minecraft_version: minecraft_version.to_owned(),
            java_major: java.major_version,
            sdk_identity: sdk_identity.to_owned(),
            runtime_contract: runtime_contract.to_owned(),
            java_executable: java.executable.clone(),
            java_home: java.home.clone(),
            java_version_output: java.version_output.clone(),
            java_selection: java.selection.clone(),
            java_pin_url: java.pin_url.clone(),
            java_pin_sha512: java.pin_sha512.clone(),
            files: BTreeMap::new(),
            directories: BTreeSet::from(["project".to_owned()]),
        };
        ensure_owned_directory(&receipt.snapshot_dir, &receipt.snapshot_dir.join("project"))?;
        let mut total = 0;
        let mut capture_phase = NfrtPreparationPhase::start("snapshot.capture_and_copy");
        #[cfg(windows)]
        let mut source_capture =
            source_capture::SnapshotSourceCapture::prepare(project_dir, cancellation_token)?;
        #[cfg(windows)]
        tracing::info!(
            phase = "snapshot.source_capture",
            retained_source = source_capture.is_some(),
            rust_pid = std::process::id(),
            "snapshot.source_capture"
        );
        #[cfg(windows)]
        if let Some(capture) = &mut source_capture {
            capture.copy_into(&mut receipt, &mut total, cancellation_token)?;
        } else {
            for root in ROOT_NAMES {
                cancellation_token.bail_if_cancelled()?;
                receipt.copy_root(project_dir, root, &mut total, cancellation_token)?;
            }
        }
        #[cfg(not(windows))]
        for root in ROOT_NAMES {
            cancellation_token.bail_if_cancelled()?;
            receipt.copy_root(project_dir, root, &mut total, cancellation_token)?;
        }
        capture_phase.work(u64::try_from(receipt.files.len())?, total);
        capture_phase.success();
        drop(capture_phase);
        receipt.validate()?;
        let raw = receipt.json_bytes()?;
        cancellation_token.bail_if_cancelled()?;
        write_new(&receipt.receipt_path(), &raw)?;
        NfrtPreparationPhase::run("snapshot.destination_verification", || receipt.recheck())?;
        // Keep the complete snapshot before making it discoverable. Failure to
        // replace latest leaves the previous success intact and an unused copy.
        cancellation_token.bail_if_cancelled()?;
        let _snapshot_dir = staging.keep();
        NfrtPreparationPhase::run("snapshot.publish_latest", || {
            receipt.publish_latest(&raw, cancellation_token)
        })?;
        // Captured Windows source objects stay held through final publication.
        #[cfg(windows)]
        drop(source_capture);
        Ok(receipt)
    }

    pub(crate) fn load(receipt_path: &Path, expected_minecraft_dir: &Path) -> Result<Self> {
        let raw = read_bounded(receipt_path, MAX_FILE_BYTES)?;
        let receipt: Self = facet_json::from_slice(&raw)
            .wrap_err("cannot parse strict successful-build snapshot receipt")?;
        ensure!(
            same_path_spelling(
                &receipt.minecraft_dir,
                &canonical_directory(expected_minecraft_dir)?
            ),
            "snapshot belongs to another Minecraft projection"
        );
        receipt.validate()?;
        let path = checked_file(receipt_path)?;
        ensure!(
            same_path_spelling(&path, &receipt.receipt_path())
                || same_path_spelling(
                    &path,
                    &receipt.cache_dir.join("state/latest-successful-build.json")
                ),
            "snapshot receipt must be its owned receipt or latest-successful-build copy"
        );
        ensure!(
            raw == receipt.json_bytes()?,
            "snapshot receipt encoding changed"
        );
        receipt.recheck()?;
        Ok(receipt)
    }

    pub(crate) fn recheck(&self) -> Result<()> {
        self.validate()?;
        ensure!(
            read_bounded(&self.receipt_path(), MAX_FILE_BYTES)? == self.json_bytes()?,
            "successful-build snapshot receipt changed"
        );
        let (files, directories) = inventory(&self.snapshot_dir, true, None)?;
        ensure!(
            files == self.files,
            "successful-build snapshot file inventory changed"
        );
        ensure!(
            directories == self.directories,
            "successful-build snapshot directory inventory changed"
        );
        Ok(())
    }

    pub(crate) fn receipt_path(&self) -> PathBuf {
        self.snapshot_dir.join("receipt.json")
    }

    pub(crate) fn class_directories(&self) -> Result<Vec<PathBuf>> {
        self.recheck()?;
        Ok(["classes", "gametest/classes"]
            .into_iter()
            .filter(|root| self.directories.contains(&format!("project/{root}")))
            .map(|root| self.snapshot_dir.join("project").join(root))
            .collect())
    }

    pub(crate) fn has_root(&self, name: &str) -> bool {
        ROOT_NAMES.contains(&name) && self.directories.contains(&format!("project/{name}"))
    }

    pub(crate) fn java(&self) -> ResolvedJava {
        ResolvedJava {
            executable: self.java_executable.clone(),
            home: self.java_home.clone(),
            version_output: self.java_version_output.clone(),
            major_version: self.java_major,
            selection: self.java_selection.clone(),
            pin_url: self.java_pin_url.clone(),
            pin_sha512: self.java_pin_sha512.clone(),
        }
    }

    /// Merge admitted output roots into a new or checked empty session directory.
    /// Later roots intentionally replace same-named files from earlier roots.
    pub(crate) fn materialize_roots(&self, destination: &Path, root_names: &[&str]) -> Result<()> {
        self.recheck()?;
        ensure!(!root_names.is_empty(), "runtime snapshot roots are empty");
        for root in root_names {
            ensure!(
                ROOT_NAMES.contains(root),
                "unsupported snapshot root: {root}"
            );
            ensure!(
                self.directories.contains(&format!("project/{root}")),
                "snapshot root is absent: {root}"
            );
        }
        let destination = self.prepare_materialization(destination)?;
        let mut expected = BTreeMap::new();
        let mut expected_directories = BTreeSet::new();
        for root in root_names {
            self.materialize_root(&destination, root, &mut expected, &mut expected_directories)?;
        }
        let (files, directories) = inventory(&destination, false, None)?;
        ensure!(
            files == expected,
            "materialized session file inventory changed"
        );
        ensure!(
            directories == expected_directories,
            "materialized session directory inventory changed"
        );
        self.recheck()?;
        Ok(())
    }

    fn json_bytes(&self) -> Result<Vec<u8>> {
        let mut raw = facet_json::to_string_pretty(self)?.into_bytes();
        raw.push(b'\n');
        ensure!(
            u64::try_from(raw.len())? <= MAX_FILE_BYTES,
            "snapshot receipt exceeds byte limit"
        );
        Ok(raw)
    }

    fn prepare_materialization(&self, destination: &Path) -> Result<PathBuf> {
        let slots = self.minecraft_dir.join("build/sfm-runtime/slots");
        let owner = if relative_spelling(&self.cache_dir, destination).is_some() {
            &self.cache_dir
        } else {
            &slots
        };
        let relative = checked_descendant(owner, destination)?;
        ensure!(
            !relative.as_os_str().is_empty(),
            "session output cannot replace its owner"
        );
        ensure_owned_directory(&self.minecraft_dir, owner)?;
        ensure_owned_directory(owner, destination)?;
        ensure!(
            fs::read_dir(destination)?.next().is_none(),
            "session output directory must be empty"
        );
        canonical_directory(destination)
    }

    fn validate(&self) -> Result<()> {
        ensure!(
            self.schema == SCHEMA,
            "unsupported successful-build snapshot schema"
        );
        ensure!(
            canonical_directory(&self.minecraft_dir)? == self.minecraft_dir
                && canonical_directory(&self.cache_dir)? == self.cache_dir
                && !checked_descendant(&self.minecraft_dir, &self.cache_dir)?
                    .as_os_str()
                    .is_empty(),
            "snapshot cache is outside its canonical Minecraft projection"
        );
        ensure!(
            self.snapshot_id.starts_with("snapshot-")
                && self.snapshot_id.len() > 9
                && safe_label(&self.snapshot_id),
            "invalid build snapshot ID"
        );
        safe_relative(&self.snapshot_id)?;
        ensure!(
            same_path_spelling(
                &self.snapshot_dir,
                &self
                    .cache_dir
                    .join("build-snapshots")
                    .join(&self.snapshot_id),
            ) && canonical_directory(&self.snapshot_dir)? == self.snapshot_dir,
            "snapshot directory is outside its canonical build-snapshot owner"
        );
        ensure!(
            safe_label(&self.minecraft_version)
                && self.java_major > 0
                && self.java_executable.is_absolute()
                && self
                    .java_home
                    .as_ref()
                    .is_none_or(|home| home.is_absolute())
                && !self.sdk_identity.is_empty()
                && self.sdk_identity.len() <= 1024
                && !self.runtime_contract.is_empty()
                && self.runtime_contract.len() <= 4 * 1024 * 1024,
            "snapshot runtime identity is invalid"
        );
        ensure!(
            !self.files.is_empty()
                && self.files.len() < MAX_FILES
                && self.directories.len() <= MAX_FILES,
            "snapshot inventory exceeds entry limit or is empty"
        );
        ensure!(
            self.files
                .keys()
                .any(|path| path.starts_with("project/classes/")
                    && Path::new(path).extension() == Some(OsStr::new("class"))),
            "successful snapshot requires main classes"
        );
        for (path, hash) in &self.files {
            safe_relative(path)?;
            ensure!(
                allowed_output(path, false),
                "unsupported snapshot output: {path}"
            );
            ensure!(
                hash.len() == 64
                    && hash
                        .bytes()
                        .all(|byte| byte.is_ascii_hexdigit() && !byte.is_ascii_uppercase()),
                "invalid snapshot BLAKE3 digest"
            );
        }
        for path in &self.directories {
            safe_relative(path)?;
            ensure!(
                allowed_output(path, true),
                "unsupported snapshot directory: {path}"
            );
        }
        Ok(())
    }

    fn copy_root(
        &mut self,
        project: &Path,
        root: &str,
        total: &mut u64,
        cancellation_token: &CancellationToken,
    ) -> Result<()> {
        cancellation_token.bail_if_cancelled()?;
        let source = project.join(root);
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
            relative_spelling(&self.cache_dir.join("project"), &source).is_some(),
            "build output escaped project owner"
        );
        let (files, directories) = inventory(&source, false, Some(cancellation_token))?;
        let prefix = format!("project/{root}");
        let target = self.snapshot_dir.join(&prefix);
        ensure_owned_directory(&self.snapshot_dir, &target)?;
        add_parents(&mut self.directories, &prefix);
        for relative in directories {
            cancellation_token.bail_if_cancelled()?;
            let path = format!("{prefix}/{relative}");
            ensure_owned_directory(&self.snapshot_dir, &self.snapshot_dir.join(&path))?;
            self.directories.insert(path);
        }
        for (relative, hash) in files {
            cancellation_token.bail_if_cancelled()?;
            let raw = read_bounded(&source.join(&relative), MAX_FILE_BYTES)?;
            ensure!(
                digest(&raw) == hash,
                "build output changed while snapshotting"
            );
            add_bytes(total, raw.len())?;
            let path = format!("{prefix}/{relative}");
            write_new(&self.snapshot_dir.join(&path), &raw)?;
            self.files.insert(path, hash);
            ensure!(
                self.files.len() < MAX_FILES,
                "snapshot file count exceeds limit"
            );
        }
        ensure!(
            self.directories.len() <= MAX_FILES,
            "snapshot directory count exceeds limit"
        );
        Ok(())
    }

    fn materialize_root(
        &self,
        destination: &Path,
        root: &str,
        expected: &mut BTreeMap<String, String>,
        expected_directories: &mut BTreeSet<String>,
    ) -> Result<()> {
        let prefix = format!("project/{root}/");
        for path in &self.directories {
            if let Some(relative) = path.strip_prefix(&prefix) {
                ensure_owned_directory(destination, &destination.join(relative))?;
                expected_directories.insert(relative.to_owned());
            }
        }
        for (path, hash) in &self.files {
            let Some(relative) = path.strip_prefix(&prefix) else {
                continue;
            };
            let raw = read_bounded(&self.snapshot_dir.join(path), MAX_FILE_BYTES)?;
            ensure!(
                digest(&raw) == *hash,
                "snapshot output changed before session copy"
            );
            let target = destination.join(relative);
            if expected.contains_key(relative) {
                // Only a file created by an earlier admitted root may be replaced.
                let previous = read_bounded(&target, MAX_FILE_BYTES)?;
                ensure!(
                    digest(&previous) == expected[relative],
                    "earlier session output changed"
                );
                fs::remove_file(&target)?;
            }
            write_new(&target, &raw)?;
            expected.insert(relative.to_owned(), hash.clone());
        }
        Ok(())
    }

    fn publish_latest(&self, raw: &[u8], cancellation_token: &CancellationToken) -> Result<()> {
        cancellation_token.bail_if_cancelled()?;
        let state = self.cache_dir.join("state");
        ensure_owned_directory(&self.cache_dir, &state)?;
        let latest = state.join("latest-successful-build.json");
        match fs::symlink_metadata(&latest) {
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            result => {
                let metadata = result?;
                ensure!(
                    metadata.is_file() && !is_reparse(&metadata),
                    "latest success is a link or nonregular file"
                );
            }
        }
        let mut temporary = tempfile::NamedTempFile::new_in(&state)?;
        temporary.write_all(raw)?;
        temporary.as_file().sync_all()?;
        ensure_owned_directory(&self.cache_dir, &state)?;
        cancellation_token.bail_if_cancelled()?;
        temporary.persist(&latest).map_err(|error| error.error)?;
        Ok(())
    }
}

/// Create/check only normal directory descendants; never traverse a link or reparse point.
pub(crate) fn ensure_owned_directory(owner: &Path, path: &Path) -> Result<()> {
    let owner = canonical_directory(owner)?;
    let relative = checked_descendant(&owner, path)?;
    let mut current = owner;
    for part in relative.components() {
        current.push(part);
        match fs::symlink_metadata(&current) {
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                match fs::create_dir(&current) {
                    Ok(()) => {}
                    Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
                    Err(error) => return Err(error.into()),
                }
            }
            result => {
                result?;
            }
        }
        let metadata = fs::symlink_metadata(&current)?;
        ensure!(
            metadata.is_dir() && !is_reparse(&metadata),
            "owned directory is a link or non-directory"
        );
    }
    ensure!(
        same_path_spelling(&canonical_directory(path)?, path),
        "owned directory spelling is not canonical: {}",
        path.display()
    );
    Ok(())
}

/// Read a bounded, regular file without following a leaf link or Windows reparse point.
pub(crate) fn read_bounded(path: &Path, limit: u64) -> Result<Vec<u8>> {
    let path = checked_file(path)?;
    let mut file = open_read(&path)?;
    let metadata = file.metadata()?;
    ensure!(
        metadata.is_file() && !is_reparse(&metadata),
        "snapshot input is not a regular non-reparse file"
    );
    ensure!(metadata.len() <= limit, "snapshot input exceeds byte limit");
    let identity = crate::file_identity::file_identity(&file)?;
    let mut raw = Vec::new();
    std::io::Read::by_ref(&mut file)
        .take(
            limit
                .checked_add(1)
                .ok_or_else(|| eyre::eyre!("read limit overflow"))?,
        )
        .read_to_end(&mut raw)?;
    ensure!(
        u64::try_from(raw.len())? <= limit,
        "snapshot input exceeds byte limit"
    );
    let after = file.metadata()?;
    ensure!(
        after.len() == metadata.len()
            && u64::try_from(raw.len())? == metadata.len()
            && after.modified()? == metadata.modified()?,
        "snapshot input changed while reading"
    );
    checked_file(&path)?;
    ensure!(
        crate::file_identity::file_identity(&open_read(&path)?)? == identity,
        "snapshot input path was replaced while reading"
    );
    Ok(raw)
}

fn inventory(
    root: &Path,
    skip_receipt: bool,
    cancellation_token: Option<&CancellationToken>,
) -> Result<(BTreeMap<String, String>, BTreeSet<String>)> {
    let root = canonical_directory(root)?;
    let mut files = BTreeMap::new();
    let mut directories = BTreeSet::new();
    let mut names = BTreeSet::new();
    let mut file_count = 0;
    let mut total = 0;
    for entry in walkdir::WalkDir::new(&root)
        .follow_links(false)
        .min_depth(1)
        .max_depth(129)
    {
        if let Some(cancellation_token) = cancellation_token {
            cancellation_token.bail_if_cancelled()?;
        }
        let entry = entry?;
        let relative = relative_string(&root, entry.path())?;
        ensure!(
            names.insert(relative.to_lowercase()),
            "snapshot tree has ambiguous case-folded paths"
        );
        let metadata = fs::symlink_metadata(entry.path())?;
        ensure!(
            !is_reparse(&metadata),
            "snapshot tree contains a link or reparse point"
        );
        if metadata.is_dir() {
            directories.insert(relative);
            ensure!(
                directories.len() <= MAX_FILES,
                "snapshot directory count exceeds limit"
            );
        } else {
            ensure!(
                metadata.is_file(),
                "snapshot tree contains a nonregular file"
            );
            let raw = read_bounded(entry.path(), MAX_FILE_BYTES)?;
            add_bytes(&mut total, raw.len())?;
            file_count += 1;
            ensure!(file_count <= MAX_FILES, "snapshot file count exceeds limit");
            if !(skip_receipt && relative == "receipt.json") {
                files.insert(relative, digest(&raw));
            }
        }
    }
    Ok((files, directories))
}

fn add_bytes(total: &mut u64, bytes: usize) -> Result<()> {
    *total = total
        .checked_add(u64::try_from(bytes)?)
        .ok_or_else(|| eyre::eyre!("snapshot byte count overflow"))?;
    ensure!(
        *total <= MAX_TOTAL_BYTES,
        "snapshot total exceeds byte limit"
    );
    Ok(())
}

fn digest(raw: &[u8]) -> String {
    blake3::hash(raw).to_hex().to_string()
}

fn add_parents(directories: &mut BTreeSet<String>, relative: &str) {
    let mut current = String::new();
    for component in relative.split('/') {
        if !current.is_empty() {
            current.push('/');
        }
        current.push_str(component);
        directories.insert(current.clone());
    }
}

fn allowed_output(path: &str, directory: bool) -> bool {
    if directory && matches!(path, "project" | "project/gametest" | "project/datagen") {
        return true;
    }
    ROOT_NAMES.iter().any(|root| {
        let prefix = format!("project/{root}");
        (directory && path == prefix)
            || path
                .strip_prefix(&prefix)
                .is_some_and(|suffix| suffix.starts_with('/'))
    })
}

fn safe_label(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 128
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'-' | b'_'))
}

fn safe_relative(relative: &str) -> Result<()> {
    ensure!(
        !relative.is_empty() && relative.len() <= 4096 && relative.split('/').count() <= 128,
        "snapshot relative path exceeds limit or is empty"
    );
    for name in relative.split('/') {
        ensure!(
            !name.is_empty()
                && name != "."
                && name != ".."
                && name.len() <= 255
                && !name.ends_with(['.', ' '])
                && !name
                    .chars()
                    .any(|character| character.is_control() || "\\:<>\"|?*".contains(character)),
            "unsafe snapshot relative path"
        );
        let device = name
            .split('.')
            .next()
            .unwrap_or_default()
            .trim_end_matches(' ')
            .to_ascii_uppercase();
        let numbered_device = device
            .strip_prefix("COM")
            .or_else(|| device.strip_prefix("LPT"));
        ensure!(
            !matches!(
                device.as_str(),
                "CON" | "PRN" | "AUX" | "NUL" | "CONIN$" | "CONOUT$" | "CLOCK$"
            ) && !numbered_device.is_some_and(|number| matches!(
                number,
                "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" | "¹" | "²" | "³"
            )),
            "snapshot path contains a reserved Windows device name"
        );
    }
    Ok(())
}

fn relative_string(owner: &Path, path: &Path) -> Result<String> {
    let relative = checked_descendant(owner, path)?;
    let relative = portable_relative(&relative)?;
    safe_relative(&relative)?;
    Ok(relative)
}

pub(crate) fn checked_descendant(owner: &Path, path: &Path) -> Result<PathBuf> {
    ensure!(path.is_absolute(), "snapshot path must be absolute");
    ensure!(
        !path
            .components()
            .any(|part| matches!(part, Component::ParentDir | Component::CurDir)),
        "snapshot path traverses a dot component"
    );
    let relative = relative_spelling(owner, path)
        .ok_or_else(|| eyre::eyre!("snapshot path is outside its owner"))?;
    if !relative.as_os_str().is_empty() {
        safe_relative(&portable_relative(&relative)?)?;
    }
    Ok(relative)
}

// Windows canonicalization retains a verbatim prefix only for long paths.
// Compare that prefix representation without changing case or any path component.
pub(crate) fn same_path_spelling(left: &Path, right: &Path) -> bool {
    relative_spelling(left, right).is_some_and(|relative| relative.as_os_str().is_empty())
}

pub(crate) fn relative_spelling(owner: &Path, path: &Path) -> Option<PathBuf> {
    let mut owner_parts = owner.components();
    let mut path_parts = path.components();
    if !same_initial_component(owner_parts.next(), path_parts.next()) {
        return None;
    }
    for owner_part in owner_parts {
        if Some(owner_part) != path_parts.next() {
            return None;
        }
    }
    Some(path_parts.as_path().to_owned())
}

fn same_initial_component(left: Option<Component<'_>>, right: Option<Component<'_>>) -> bool {
    if left == right {
        return true;
    }
    #[cfg(windows)]
    {
        use std::path::Prefix;
        if let (Some(Component::Prefix(left)), Some(Component::Prefix(right))) = (left, right) {
            return match (left.kind(), right.kind()) {
                (Prefix::Disk(left), Prefix::VerbatimDisk(right))
                | (Prefix::VerbatimDisk(left), Prefix::Disk(right)) => left == right,
                (
                    Prefix::UNC(left_server, left_share),
                    Prefix::VerbatimUNC(right_server, right_share),
                )
                | (
                    Prefix::VerbatimUNC(left_server, left_share),
                    Prefix::UNC(right_server, right_share),
                ) => left_server == right_server && left_share == right_share,
                _ => false,
            };
        }
    }
    false
}

fn portable_relative(path: &Path) -> Result<String> {
    let mut names = Vec::new();
    for component in path.components() {
        let Component::Normal(name) = component else {
            eyre::bail!("unsafe snapshot path component")
        };
        names.push(
            name.to_str()
                .ok_or_else(|| eyre::eyre!("snapshot path is not UTF-8"))?,
        );
    }
    Ok(names.join("/"))
}

fn canonical_directory(path: &Path) -> Result<PathBuf> {
    ensure!(path.is_absolute(), "snapshot directory must be absolute");
    ensure!(
        !path
            .components()
            .any(|part| matches!(part, Component::ParentDir | Component::CurDir)),
        "snapshot directory traverses a dot component"
    );
    for ancestor in path.ancestors() {
        let metadata = fs::symlink_metadata(ancestor).wrap_err_with(|| {
            format!("cannot inspect snapshot directory {}", ancestor.display())
        })?;
        ensure!(
            metadata.is_dir() && !is_reparse(&metadata),
            "snapshot ancestry contains a link, reparse point or non-directory"
        );
    }
    Ok(dunce::canonicalize(path)?)
}

fn checked_file(path: &Path) -> Result<PathBuf> {
    ensure!(path.is_absolute(), "snapshot file must be absolute");
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("snapshot file has no parent"))?;
    let parent = canonical_directory(parent)?;
    let name = path
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| eyre::eyre!("snapshot file name is not UTF-8"))?;
    safe_relative(name)?;
    let canonical = parent.join(name);
    let metadata = fs::symlink_metadata(&canonical)?;
    ensure!(
        metadata.is_file() && !is_reparse(&metadata),
        "snapshot file is a link or nonregular file"
    );
    Ok(canonical)
}

fn open_read(path: &Path) -> Result<fs::File> {
    let mut options = fs::OpenOptions::new();
    options.read(true);
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt as _;
        options.share_mode(1).custom_flags(0x0020_0000)
    };
    #[cfg(target_os = "linux")]
    {
        use std::os::unix::fs::OpenOptionsExt as _;
        options.custom_flags(0o400_000);
    }
    Ok(options.open(path)?)
}

fn write_new(path: &Path, raw: &[u8]) -> Result<()> {
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("snapshot output has no parent"))?;
    canonical_directory(parent)?;
    safe_relative(
        path.file_name()
            .and_then(|name| name.to_str())
            .ok_or_else(|| eyre::eyre!("snapshot output name is not UTF-8"))?,
    )?;
    let mut options = fs::OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt as _;
        options.custom_flags(0x0020_0000)
    };
    #[cfg(target_os = "linux")]
    {
        use std::os::unix::fs::OpenOptionsExt as _;
        options.custom_flags(0o400_000);
    }
    let mut file = options.open(path)?;
    ensure!(
        file.metadata()?.is_file() && !is_reparse(&file.metadata()?),
        "snapshot output is not a regular file"
    );
    file.write_all(raw)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn java(root: &Path) -> ResolvedJava {
        ResolvedJava {
            executable: root.join("sdk/bin/java"),
            home: Some(root.join("sdk")),
            version_output: "JetBrains Runtime 17 fixture".to_owned(),
            major_version: 17,
            selection: "fixture".to_owned(),
            pin_url: None,
            pin_sha512: None,
        }
    }

    fn project(root: &Path, name: &str) -> Result<(PathBuf, PathBuf)> {
        let minecraft = root.join(name);
        let cache = minecraft.join("build/sfm-toolchain/native-project/fixture");
        fs::create_dir_all(cache.join("project/classes/example"))?;
        fs::create_dir_all(cache.join("project/staged-resources/empty"))?;
        fs::write(
            cache.join("project/classes/example/Main.class"),
            b"main class fixture",
        )?;
        fs::write(
            cache.join("project/staged-resources/config.txt"),
            b"main resource",
        )?;
        Ok((
            canonical_directory(&minecraft)?,
            canonical_directory(&cache)?,
        ))
    }

    fn publish(minecraft: &Path, cache: &Path) -> Result<BuildSnapshotReceipt> {
        BuildSnapshotReceipt::publish(
            minecraft,
            cache,
            "1.19.2",
            &java(minecraft),
            &cache.join("project"),
            "sdk-fixture",
        )
    }

    #[cfg(windows)]
    #[test]
    fn long_windows_paths_publish_recheck_and_materialize_exact_outputs() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let (minecraft, cache) = project(temporary.path(), "projection-long-paths")?;
        let relative = format!("{}/{}/Deep.class", "a".repeat(96), "b".repeat(96));
        let class = cache.join("project/classes").join(&relative);
        fs::create_dir_all(class.parent().unwrap())?;
        fs::write(&class, b"long nested class fixture")?;
        let receipt = publish(&minecraft, &cache)?;
        let captured = receipt.snapshot_dir.join("project/classes").join(&relative);
        assert!(captured.as_os_str().len() > 260);
        assert_eq!(read_bounded(&captured, 1024)?, b"long nested class fixture");
        let output = minecraft
            .join("build/sfm-runtime/slots/9001/launches")
            .join(format!("session-{}", "c".repeat(144)))
            .join("mod");
        receipt.materialize_roots(&output, &["classes", "staged-resources"])?;
        assert_eq!(
            read_bounded(&output.join(&relative), 1024)?,
            b"long nested class fixture"
        );
        assert_eq!(
            read_bounded(&output.join("config.txt"), 1024)?,
            b"main resource"
        );
        assert_eq!(
            BuildSnapshotReceipt::load(&receipt.receipt_path(), &minecraft)?,
            receipt
        );
        receipt.recheck()?;
        Ok(())
    }

    #[cfg(windows)]
    #[test]
    fn long_windows_owner_accepts_equivalent_prefix_but_rejects_aliases() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let owner = dunce::canonicalize(temporary.path())?
            .join("OwnedCase")
            .join("a".repeat(128))
            .join("b".repeat(128));
        fs::create_dir_all(&owner)?;
        let verbatim = canonical_directory(&owner)?;
        assert_ne!(owner, verbatim);
        let child = owner.join("NestedCase/Child");
        ensure_owned_directory(&verbatim, &child)?;
        assert_eq!(
            checked_descendant(&verbatim, &child)?,
            Path::new("NestedCase/Child")
        );
        assert!(ensure_owned_directory(&verbatim, &owner.join("nestedcase/Child")).is_err());
        assert!(checked_descendant(&verbatim, &owner.join("../escape")).is_err());
        assert!(checked_descendant(&verbatim, &owner.join("CON.txt")).is_err());
        assert!(checked_descendant(&verbatim, &owner.join("trailing.")).is_err());
        Ok(())
    }

    #[cfg(windows)]
    #[test]
    fn windows_prefix_equivalence_preserves_exact_component_spelling() {
        for (normal, verbatim) in [
            (r"C:\Owned\Nested", r"\\?\C:\Owned\Nested"),
            (
                r"\\server\share\Owned\Nested",
                r"\\?\UNC\server\share\Owned\Nested",
            ),
        ] {
            assert!(same_path_spelling(Path::new(normal), Path::new(verbatim)));
            assert!(same_path_spelling(Path::new(verbatim), Path::new(normal)));
        }
        for other in [
            r"\\?\C:\owned\Nested",
            r"\\.\C:\Owned\Nested",
            r"\\?\D:\Owned\Nested",
        ] {
            assert!(!same_path_spelling(
                Path::new(r"C:\Owned\Nested"),
                Path::new(other)
            ));
        }
        assert!(!same_path_spelling(
            Path::new(r"\\server\share\Owned"),
            Path::new(r"\\?\UNC\SERVER\share\Owned"),
        ));
    }

    #[test]
    fn publishes_and_loads_exact_outputs_without_test_classes() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let (minecraft, cache) = project(temporary.path(), "projection-a")?;
        fs::create_dir_all(cache.join("project/test/classes"))?;
        fs::write(
            cache.join("project/test/classes/Unit.class"),
            b"unit fixture",
        )?;
        let receipt = publish(&minecraft, &cache)?;
        let loaded = BuildSnapshotReceipt::load(
            &cache.join("state/latest-successful-build.json"),
            &minecraft,
        )?;
        assert_eq!(receipt, loaded);
        assert_eq!(receipt.java().executable, java(&minecraft).executable);
        assert_eq!(
            receipt.class_directories()?,
            vec![receipt.snapshot_dir.join("project/classes")]
        );
        assert!(!receipt.snapshot_dir.join("project/test").exists());
        assert!(
            receipt
                .directories
                .contains("project/staged-resources/empty")
        );
        Ok(())
    }

    #[test]
    fn materializes_in_order_and_never_reuses_an_existing_session_root() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let (minecraft, cache) = project(temporary.path(), "projection-a")?;
        fs::create_dir_all(cache.join("project/gametest/resources"))?;
        fs::write(
            cache.join("project/gametest/resources/config.txt"),
            b"gametest override",
        )?;
        let receipt = publish(&minecraft, &cache)?;
        let output = cache.join("sessions/one/mod-root");
        receipt.materialize_roots(
            &output,
            &["classes", "staged-resources", "gametest/resources"],
        )?;
        assert_eq!(fs::read(output.join("config.txt"))?, b"gametest override");
        assert_eq!(
            fs::read(output.join("example/Main.class"))?,
            b"main class fixture"
        );
        assert!(output.join("empty").is_dir());
        assert!(receipt.materialize_roots(&output, &["classes"]).is_err());
        Ok(())
    }

    #[test]
    fn same_minecraft_version_has_disjoint_projection_snapshots_and_success_state() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let (first_minecraft, first_cache) = project(temporary.path(), "projection-a")?;
        let (second_minecraft, second_cache) = project(temporary.path(), "projection-b")?;
        let barrier = std::sync::Barrier::new(2);
        let (first, second) = std::thread::scope(|scope| {
            let first = scope.spawn(|| {
                barrier.wait();
                publish(&first_minecraft, &first_cache)
            });
            let second = scope.spawn(|| {
                barrier.wait();
                publish(&second_minecraft, &second_cache)
            });
            (
                first.join().expect("first publisher panicked"),
                second.join().expect("second publisher panicked"),
            )
        });
        let first = first?;
        let second = second?;
        assert_eq!(first.minecraft_version, second.minecraft_version);
        assert!(!first.snapshot_dir.starts_with(&second_minecraft));
        assert!(!second.snapshot_dir.starts_with(&first_minecraft));
        assert!(BuildSnapshotReceipt::load(&first.receipt_path(), &second_minecraft).is_err());
        first.recheck()?;
        second.recheck()?;
        Ok(())
    }

    #[test]
    fn materializes_into_checked_empty_persistent_runtime_slot() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let (minecraft, cache) = project(temporary.path(), "projection-a")?;
        let receipt = publish(&minecraft, &cache)?;
        let output = minecraft.join("build/sfm-runtime/slots/2/launches/session-fixture/mod");
        ensure_owned_directory(&minecraft, &output)?;
        assert!(receipt.has_root("classes"));
        assert!(!receipt.has_root("gametest/classes"));
        assert!(!receipt.has_root("test/classes"));
        receipt.materialize_roots(&output, &["classes", "staged-resources"])?;
        assert_eq!(
            fs::read(output.join("example/Main.class"))?,
            b"main class fixture"
        );
        assert!(receipt.materialize_roots(&output, &["classes"]).is_err());
        receipt.recheck()?;
        Ok(())
    }

    #[test]
    fn failed_candidate_preserves_success_and_live_materialization() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let (minecraft, cache) = project(temporary.path(), "projection-a")?;
        let receipt = publish(&minecraft, &cache)?;
        let latest = cache.join("state/latest-successful-build.json");
        let original = fs::read(&latest)?;
        let live = cache.join("sessions/live/mod-root");
        receipt.materialize_roots(&live, &["classes"])?;
        fs::remove_file(cache.join("project/classes/example/Main.class"))?;
        assert!(publish(&minecraft, &cache).is_err());
        assert_eq!(fs::read(&latest)?, original);
        assert_eq!(
            fs::read(live.join("example/Main.class"))?,
            b"main class fixture"
        );
        receipt.recheck()?;
        Ok(())
    }

    #[test]
    fn cancelled_candidate_and_final_publication_preserve_latest_success() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let (minecraft, cache) = project(temporary.path(), "projection-a")?;
        let receipt = publish(&minecraft, &cache)?;
        let latest = cache.join("state/latest-successful-build.json");
        let original = fs::read(&latest)?;
        let token = CancellationToken::new();
        token.request_cancel("fixture cancelled before publication");
        assert!(
            BuildSnapshotReceipt::publish_cancellable(
                &minecraft,
                &cache,
                "1.19.2",
                &java(&minecraft),
                &cache.join("project"),
                ("sdk-fixture", "fixture-runtime-contract"),
                &token,
            )
            .is_err()
        );
        assert!(
            receipt
                .publish_latest(b"must never replace success", &token)
                .is_err()
        );
        assert_eq!(fs::read(&latest)?, original);
        receipt.recheck()?;
        Ok(())
    }

    #[test]
    fn rejects_oversized_candidate_without_changing_latest_success() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let (minecraft, cache) = project(temporary.path(), "projection-a")?;
        let receipt = publish(&minecraft, &cache)?;
        let latest = cache.join("state/latest-successful-build.json");
        let original = fs::read(&latest)?;
        let oversized = cache.join("project/classes/Large.class");
        fs::File::create(&oversized)?.set_len(MAX_FILE_BYTES + 1)?;
        assert!(publish(&minecraft, &cache).is_err());
        assert_eq!(fs::read(&latest)?, original);
        assert!(read_bounded(&oversized, MAX_FILE_BYTES).is_err());
        receipt.recheck()?;
        let mut total = MAX_TOTAL_BYTES;
        assert!(add_bytes(&mut total, 1).is_err());
        Ok(())
    }

    #[test]
    fn rejects_mutation_extra_files_and_empty_directories() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let (minecraft, cache) = project(temporary.path(), "projection-a")?;
        let receipt = publish(&minecraft, &cache)?;
        let class = receipt
            .snapshot_dir
            .join("project/classes/example/Main.class");
        fs::write(&class, b"changed class fixture")?;
        assert!(receipt.recheck().is_err());
        fs::write(&class, b"main class fixture")?;
        let extra = receipt.snapshot_dir.join("project/classes/Extra.class");
        fs::write(&extra, b"unexpected")?;
        assert!(receipt.recheck().is_err());
        fs::remove_file(extra)?;
        let extra_directory = receipt
            .snapshot_dir
            .join("project/classes/unrecorded-empty");
        fs::create_dir(&extra_directory)?;
        assert!(receipt.recheck().is_err());
        fs::remove_dir(extra_directory)?;
        receipt.recheck()?;
        Ok(())
    }

    #[test]
    fn rejects_unsafe_paths_changed_receipts_and_oversized_inputs() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let (minecraft, cache) = project(temporary.path(), "projection-a")?;
        let receipt = publish(&minecraft, &cache)?;
        for relative in [
            "../escape",
            "C:/escape",
            "classes\\evil",
            "/absolute",
            "empty//part",
            "CON.txt",
            "trailing.",
        ] {
            assert!(safe_relative(relative).is_err(), "{relative}");
        }
        assert!(ensure_owned_directory(&cache, &cache.join("../escape")).is_err());
        assert!(
            receipt
                .materialize_roots(&minecraft.join("outside-cache"), &["classes"])
                .is_err()
        );
        assert!(
            receipt
                .materialize_roots(&cache.join("sessions/test"), &["test/classes"])
                .is_err()
        );
        assert!(read_bounded(&receipt.receipt_path(), 1).is_err());
        let mut forged = receipt.clone();
        forged
            .files
            .insert("project/classes/../escape.class".to_owned(), "a".repeat(64));
        assert!(forged.recheck().is_err());
        fs::write(receipt.receipt_path(), b"{} trailing")?;
        assert!(receipt.recheck().is_err());
        assert!(BuildSnapshotReceipt::load(&receipt.receipt_path(), &minecraft).is_err());
        Ok(())
    }

    #[cfg(unix)]
    #[test]
    fn refuses_symlink_sources_snapshot_additions_and_owned_ancestors() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let (minecraft, cache) = project(temporary.path(), "projection-a")?;
        let receipt = publish(&minecraft, &cache)?;
        let link = cache.join("project/classes/Linked.class");
        std::os::unix::fs::symlink(cache.join("project/classes/example/Main.class"), &link)?;
        assert!(publish(&minecraft, &cache).is_err());
        fs::remove_file(&link)?;
        std::os::unix::fs::symlink(
            &minecraft,
            receipt.snapshot_dir.join("project/classes/link"),
        )?;
        assert!(receipt.recheck().is_err());
        let ancestor = cache.join("sessions-link");
        std::os::unix::fs::symlink(&minecraft, &ancestor)?;
        assert!(ensure_owned_directory(&cache, &ancestor.join("child")).is_err());
        Ok(())
    }

    #[cfg(windows)]
    #[test]
    fn refuses_windows_junction_sources_and_owned_ancestors() -> Result<()> {
        use std::os::windows::process::CommandExt as _;
        let temporary = tempfile::tempdir()?;
        let (minecraft, cache) = project(temporary.path(), "projection-a")?;
        let receipt = publish(&minecraft, &cache)?;
        let target = cache.join("junction-target");
        fs::create_dir(&target)?;
        let junction = cache
            .join("project/classes/linked")
            .components()
            .collect::<PathBuf>();
        let output = std::process::Command::new("cmd.exe")
            .args(["/d", "/c", "mklink", "/J"])
            .arg(&junction)
            .arg(&target)
            .creation_flags(0x0800_0000)
            .output()?;
        ensure!(
            output.status.success(),
            "private junction fixture failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        assert!(is_reparse(&fs::symlink_metadata(&junction)?));
        assert!(publish(&minecraft, &cache).is_err());
        assert!(ensure_owned_directory(&cache, &junction.join("child")).is_err());
        let slot = minecraft.join("build/sfm-runtime/slots/2/launches/session-fixture/mod");
        assert!(
            receipt
                .materialize_roots(&junction.join("session"), &["classes"])
                .is_err()
        );
        receipt.materialize_roots(&slot, &["classes"])?;
        receipt.recheck()?;
        Ok(())
    }
}
