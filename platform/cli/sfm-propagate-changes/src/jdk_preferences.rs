//! Machine-local logical JDK names. A registration is a locator, never proof
//! that a runtime matches a selected lockfile pin or archive digest.

use crate::paths::AppHome;
use eyre::{Context, ensure};
use facet::Facet;
use std::collections::BTreeSet;
use std::fs::{self, File, OpenOptions};
use std::io::{ErrorKind, Write};
use std::path::{Path, PathBuf};

const REGISTRY_FILE: &str = "jdk-preferences-v1.json";
const MUTATION_LOCK: &str = ".jdk-preferences-v1.lock";
const MAX_REGISTRY_BYTES: u64 = 1_048_576;

#[derive(Clone, Debug, Facet, PartialEq, Eq)]
pub struct JdkPreference {
    pub id: String,
    pub home: String,
    pub major: u32,
    pub vendor: String,
    pub version: String,
    pub build: String,
}

#[derive(Clone, Debug, Facet)]
struct RegistryDocument {
    schema_version: u32,
    entries: Vec<JdkPreference>,
}

impl Default for RegistryDocument {
    fn default() -> Self {
        Self {
            schema_version: 1,
            entries: Vec::new(),
        }
    }
}

#[derive(Debug)]
struct RegistryStore {
    root: PathBuf,
}

impl RegistryStore {
    fn new(root: PathBuf) -> Self {
        Self { root }
    }

    fn machine_local() -> eyre::Result<Self> {
        // Resolve explicitly: APP_HOME's fallback-to-CWD is unsuitable for
        // machine preferences when platform config discovery fails.
        Ok(Self::new(AppHome::resolve()?.0))
    }

    fn path(&self) -> PathBuf {
        self.root.join(REGISTRY_FILE)
    }

    fn read(&self) -> eyre::Result<RegistryDocument> {
        let path = self.path();
        let metadata = match fs::symlink_metadata(&path) {
            Ok(metadata) => metadata,
            Err(error) if error.kind() == ErrorKind::NotFound => {
                return Ok(RegistryDocument::default());
            }
            Err(error) => {
                return Err(error).wrap_err_with(|| format!("Cannot inspect {}", path.display()));
            }
        };
        ensure!(
            metadata.is_file() && !metadata.file_type().is_symlink(),
            "JDK preference registry is not a regular file: {}",
            path.display()
        );
        ensure!(
            metadata.len() <= MAX_REGISTRY_BYTES,
            "JDK preference registry is too large: {}",
            path.display()
        );
        let raw = fs::read_to_string(&path)
            .wrap_err_with(|| format!("Cannot read JDK preferences at {}", path.display()))?;
        let document: RegistryDocument = facet_json::from_str(&raw).wrap_err_with(|| {
            format!(
                "Invalid JDK preferences at {}; no change was made",
                path.display()
            )
        })?;
        ensure!(
            document.schema_version == 1,
            "Unsupported JDK preference schema at {}",
            path.display()
        );
        let mut seen = BTreeSet::new();
        for entry in &document.entries {
            validate_id(&entry.id)?;
            ensure!(
                seen.insert(&entry.id),
                "Duplicate JDK preference ID `{}`",
                entry.id
            );
            ensure!(
                Path::new(&entry.home).is_absolute()
                    && !entry.vendor.is_empty()
                    && !entry.version.is_empty()
                    && !entry.build.is_empty()
                    && entry.major > 0,
                "Invalid JDK preference record for `{}`",
                entry.id
            );
        }
        Ok(document)
    }

    fn write(&self, document: &RegistryDocument) -> eyre::Result<()> {
        let mut raw = facet_json::to_string_pretty(document)?.into_bytes();
        raw.push(b'\n');
        ensure!(
            raw.len() <= MAX_REGISTRY_BYTES as usize,
            "JDK preference registry is too large"
        );
        let mut staged = tempfile::Builder::new()
            .prefix(".jdk-preferences-v1.")
            .suffix(".tmp")
            .tempfile_in(&self.root)?;
        staged.write_all(&raw)?;
        staged.as_file_mut().sync_all()?;
        staged
            .persist(self.path())
            .map_err(|error| error.error)
            .wrap_err("Cannot atomically publish JDK preferences")?;
        Ok(())
    }

    fn lock(&self) -> eyre::Result<MutationLock> {
        fs::create_dir_all(&self.root).wrap_err_with(|| {
            format!(
                "Cannot create JDK preference directory {}",
                self.root.display()
            )
        })?;
        let path = self.root.join(MUTATION_LOCK);
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&path)
            .wrap_err_with(|| {
                format!(
                    "JDK preferences are being edited or a stale lock needs inspection: {}",
                    path.display()
                )
            })?;
        writeln!(file, "pid={}", std::process::id())?;
        file.sync_all()?;
        Ok(MutationLock {
            path,
            file: Some(file),
        })
    }

    fn add(&self, id: &str, home: &Path) -> eyre::Result<JdkPreference> {
        validate_id(id)?;
        let record = inspect_home(id, home)?;
        let _lock = self.lock()?;
        let mut document = self.read()?;
        ensure!(
            !document.entries.iter().any(|entry| entry.id == id),
            "JDK preference `{id}` already exists; remove it before selecting a different home"
        );
        document.entries.push(record.clone());
        document
            .entries
            .sort_by(|left, right| left.id.cmp(&right.id));
        self.write(&document)?;
        Ok(record)
    }

    fn remove(&self, id: &str) -> eyre::Result<JdkPreference> {
        validate_id(id)?;
        let _lock = self.lock()?;
        let mut document = self.read()?;
        let position = document
            .entries
            .iter()
            .position(|entry| entry.id == id)
            .ok_or_else(|| eyre::eyre!("No JDK preference named `{id}`"))?;
        let removed = document.entries.remove(position);
        self.write(&document)?;
        Ok(removed)
    }

    fn show(&self, id: &str) -> eyre::Result<Option<JdkPreference>> {
        validate_id(id)?;
        Ok(self
            .read()?
            .entries
            .into_iter()
            .find(|entry| entry.id == id))
    }

    fn list(&self) -> eyre::Result<Vec<JdkPreference>> {
        Ok(self.read()?.entries)
    }
}

struct MutationLock {
    path: PathBuf,
    file: Option<File>,
}

impl Drop for MutationLock {
    fn drop(&mut self) {
        drop(self.file.take());
        let _ = fs::remove_file(&self.path);
    }
}

fn validate_id(id: &str) -> eyre::Result<()> {
    ensure!(
        id.len() <= 32
            && id.len() >= 2
            && id.as_bytes()[0].is_ascii_lowercase()
            && id
                .bytes()
                .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'-'),
        "JDK logical ID must be 2-32 lowercase ASCII letters, digits or hyphens, starting with a letter"
    );
    Ok(())
}

fn release_property<'a>(release: &'a str, key: &str) -> Option<&'a str> {
    release.lines().find_map(|line| {
        let (name, value) = line.split_once('=')?;
        (name == key).then(|| value.trim().trim_matches('"'))
    })
}

fn inspect_home(id: &str, requested: &Path) -> eyre::Result<JdkPreference> {
    ensure!(
        requested.is_dir(),
        "JDK home is not a directory: {}",
        requested.display()
    );
    let home = dunce::canonicalize(requested)
        .wrap_err_with(|| format!("Cannot resolve JDK home {}", requested.display()))?;
    let runtime = crate::jdk::resolve_java(Some(&home), 1)?;
    let version = runtime
        .version_output
        .split('"')
        .nth(1)
        .ok_or_else(|| eyre::eyre!("Cannot read JDK version at {}", home.display()))?;
    let release_path = home.join("release");
    let release = fs::read_to_string(&release_path).wrap_err_with(|| {
        format!(
            "JDK home lacks readable release metadata: {}",
            release_path.display()
        )
    })?;
    let declared_version = release_property(&release, "JAVA_VERSION")
        .ok_or_else(|| eyre::eyre!("JDK release metadata lacks JAVA_VERSION"))?;
    ensure!(
        version == declared_version,
        "JDK release version {declared_version} differs from java -version {version}"
    );
    let vendor = release_property(&release, "IMPLEMENTOR")
        .filter(|value| !value.is_empty())
        .ok_or_else(|| eyre::eyre!("JDK release metadata lacks IMPLEMENTOR"))?;
    let build = release_property(&release, "IMPLEMENTOR_VERSION")
        .or_else(|| release_property(&release, "JAVA_RUNTIME_VERSION"))
        .or_else(|| runtime.version_output.lines().nth(1))
        .filter(|value| !value.is_empty())
        .ok_or_else(|| eyre::eyre!("Cannot discover JDK runtime build"))?;
    Ok(JdkPreference {
        id: id.to_owned(),
        home: home.to_string_lossy().into_owned(),
        major: runtime.major_version,
        vendor: vendor.to_owned(),
        version: version.to_owned(),
        build: build.to_owned(),
    })
}

/// Return a registered machine-local JDK home as a locator only. Callers must
/// separately verify the selected lock's exact artifact and runtime contract.
///
/// # Errors
/// Fails on malformed IDs or corrupt/inaccessible machine-local preferences.
pub fn lookup_home(logical_id: &str) -> eyre::Result<Option<PathBuf>> {
    Ok(RegistryStore::machine_local()?
        .show(logical_id)?
        .map(|entry| PathBuf::from(entry.home)))
}

/// List registrations without claiming the JDK homes still exist or match a pin.
///
/// # Errors
/// Fails if the machine-local registry is corrupt or inaccessible.
pub fn list() -> eyre::Result<Vec<JdkPreference>> {
    RegistryStore::machine_local()?.list()
}

/// Display one stored locator without validating it against a lock.
///
/// # Errors
/// Fails on malformed IDs or corrupt/inaccessible preferences.
pub fn show(id: &str) -> eyre::Result<Option<JdkPreference>> {
    RegistryStore::machine_local()?.show(id)
}

/// Register an installed JDK; this never grants lockfile compatibility.
///
/// # Errors
/// Fails on invalid JDK identity, conflicting ID or unsafe storage state.
pub fn add(id: &str, home: &Path) -> eyre::Result<JdkPreference> {
    RegistryStore::machine_local()?.add(id, home)
}

/// Remove one mapping only. The JDK installation and cached files are retained.
///
/// # Errors
/// Fails on an absent ID or corrupt/inaccessible preferences.
pub fn remove(id: &str) -> eyre::Result<JdkPreference> {
    RegistryStore::machine_local()?.remove(id)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn example(id: &str, home: &Path) -> JdkPreference {
        JdkPreference {
            id: id.to_owned(),
            home: home.display().to_string(),
            major: 17,
            vendor: "JetBrains s.r.o.".to_owned(),
            version: "17.0.14".to_owned(),
            build: "JBRSDK-17.0.14+1-1367.22-nomod".to_owned(),
        }
    }

    #[test]
    fn registry_round_trip_and_mapping_only_removal() {
        let root = tempfile::tempdir().unwrap();
        let store = RegistryStore::new(root.path().join("config"));
        fs::create_dir_all(&store.root).unwrap();
        let jdk_home = root.path().join("installed-jdk");
        fs::create_dir_all(&jdk_home).unwrap();
        let mut document = RegistryDocument::default();
        document.entries.push(example("jbr17", &jdk_home));
        store.write(&document).unwrap();
        assert_eq!(
            store.show("jbr17").unwrap(),
            Some(document.entries[0].clone())
        );
        store.remove("jbr17").unwrap();
        assert!(store.show("jbr17").unwrap().is_none());
        assert!(
            jdk_home.is_dir(),
            "removing a preference must retain JDK bytes"
        );
    }

    #[test]
    fn conflicting_writer_and_corrupt_registry_fail_closed() {
        let root = tempfile::tempdir().unwrap();
        let store = RegistryStore::new(root.path().join("config"));
        let guard = store.lock().unwrap();
        assert!(store.lock().is_err());
        drop(guard);
        fs::write(store.path(), "{invalid json").unwrap();
        let before = fs::read(store.path()).unwrap();
        assert!(store.remove("jbr17").is_err());
        assert_eq!(fs::read(store.path()).unwrap(), before);
    }

    #[test]
    fn invalid_ids_and_missing_jdk_are_rejected() {
        for id in ["", "JBR17", "../jdk", "a", "jbr_17"] {
            assert!(validate_id(id).is_err());
        }
        validate_id("jbr17").unwrap();
        let root = tempfile::tempdir().unwrap();
        let store = RegistryStore::new(root.path().join("config"));
        assert!(store.add("jbr17", &root.path().join("missing")).is_err());
        assert!(!store.path().exists());
    }
}
