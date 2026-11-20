//! Read the one authoritative catalog and its core-owned feature registry.
//!
//! No legacy manifest, imported snapshot, Git object or generated source root
//! participates in loading a named projection's explicit template context.

use super::context::ProjectionContext;
use super::core_features::FEATURE_DEFINITIONS_PATH;
#[cfg(test)]
use super::projection_definition::ProjectionDefinition;
use super::projection_definition::SUPPORTED_TARGETS;
use super::projection_definitions::CATALOG_PATH;
use super::projection_definitions::ProjectionDefinitions;
#[cfg(test)]
use super::projection_environment::ProjectionEnvironment;
use super::provenance::sha256;
use super::sfm_projection_feature_registry::SfmProjectionFeatureRegistry;
pub use super::sfm_repo_path::MAX_CATALOG_INPUT_BYTES;
use super::sfm_repo_path::SfmRepoPath;
pub(crate) use super::sfm_repo_path::read_bounded_input;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use std::collections::BTreeMap;
#[cfg(test)]
use std::fs;
use std::path::Path;
#[cfg(test)]
use std::path::PathBuf;
use std::str::from_utf8;
use std::sync::OnceLock;

#[derive(Debug)]
pub struct CoreCatalog {
    pub repo_root: SfmRepoPath,
    pub catalog: ProjectionDefinitions,
    pub feature_registry: SfmProjectionFeatureRegistry,
    catalog_bytes: Box<[u8]>,
    feature_definitions_bytes: Box<[u8]>,
    catalog_sha256: OnceLock<String>,
    feature_definitions_sha256: OnceLock<String>,
}

impl CoreCatalog {
    /// Load an explicit checkout or discover the Git worktree containing the invocation.
    ///
    /// # Errors
    /// Returns an error if discovery fails or the checkout's catalog is invalid.
    pub(crate) fn load_for_invocation(
        repo_root: Option<&Path>,
        invocation_dir: &Path,
    ) -> Result<Self> {
        Self::load_path(SfmRepoPath::discover(repo_root, invocation_dir)?)
    }

    /// Resolve an existing projection through its containing checkout's catalog.
    /// No generated-history file or version inferred from a directory name is used.
    ///
    /// # Errors
    /// Rejects an unsafe root, missing/invalid catalog, or a directory that is not
    /// exactly a catalogued projection root.
    pub fn for_project(project_root: &Path) -> Result<(Self, String)> {
        let (repo_root, project_root) = SfmRepoPath::containing_projections_file(project_root)?;
        let loaded = Self::load_path(repo_root)?;
        let key = loaded
            .catalog
            .key_for_project(&loaded.repo_root, &project_root)?
            .to_owned();
        Ok((loaded, key))
    }

    /// Load explicit projection selectors and validate every catalog entry.
    ///
    /// # Errors
    /// Rejects unsafe/reparse roots, oversized or malformed metadata, invalid
    /// keys/features, unsupported contexts and missing feature prerequisites.
    pub fn load(repo_root: &Path, invocation_dir: &Path) -> Result<Self> {
        Self::load_path(SfmRepoPath::resolve(repo_root, invocation_dir)?)
    }

    /// Read and validate the authored projection metadata at the checked root.
    ///
    /// # Errors
    /// Rejects invalid, oversized, or inaccessible metadata.
    #[tracing::instrument(level = "info", skip_all, name = "projection_catalog_load")]
    pub fn load_path(repo_root: SfmRepoPath) -> Result<Self> {
        let registry_bytes =
            repo_root.read_metadata(FEATURE_DEFINITIONS_PATH, MAX_CATALOG_INPUT_BYTES)?;
        let catalog_bytes = repo_root.read_metadata(CATALOG_PATH, MAX_CATALOG_INPUT_BYTES)?;
        Self::from_captured_metadata(repo_root, catalog_bytes, registry_bytes)
    }

    pub(crate) fn from_captured_metadata(
        repo_root: SfmRepoPath,
        catalog_bytes: Vec<u8>,
        registry_bytes: Vec<u8>,
    ) -> Result<Self> {
        ensure!(
            catalog_bytes.len() as u64 <= MAX_CATALOG_INPUT_BYTES
                && registry_bytes.len() as u64 <= MAX_CATALOG_INPUT_BYTES,
            "catalog metadata exceeds the {MAX_CATALOG_INPUT_BYTES}-byte limit"
        );
        let registry_text =
            from_utf8(&registry_bytes).wrap_err("core feature definitions are not UTF-8")?;
        let feature_registry = SfmProjectionFeatureRegistry::from_json(registry_text)?;
        let catalog_text =
            from_utf8(&catalog_bytes).wrap_err("projections catalog is not UTF-8")?;
        let catalog = ProjectionDefinitions::from_json(catalog_text, &feature_registry)?;
        for (key, entry) in &catalog.0 {
            feature_registry
                .validate_entry(entry)
                .wrap_err_with(|| format!("projection `{key}`"))?;
        }
        Ok(Self {
            repo_root,
            catalog,
            feature_registry,
            catalog_bytes: catalog_bytes.into_boxed_slice(),
            feature_definitions_bytes: registry_bytes.into_boxed_slice(),
            catalog_sha256: OnceLock::new(),
            feature_definitions_sha256: OnceLock::new(),
        })
    }

    #[must_use]
    pub(crate) fn catalog_bytes(&self) -> &[u8] {
        &self.catalog_bytes
    }

    #[must_use]
    pub(crate) fn feature_definitions_bytes(&self) -> &[u8] {
        &self.feature_definitions_bytes
    }

    /// SHA-256 provenance for the exact catalog bytes parsed at load time.
    #[must_use]
    pub fn catalog_sha256(&self) -> &str {
        self.catalog_sha256
            .get_or_init(|| sha256(self.catalog_bytes()))
    }

    /// SHA-256 provenance for the exact feature registry bytes parsed at load time.
    #[must_use]
    pub fn feature_definitions_sha256(&self) -> &str {
        self.feature_definitions_sha256
            .get_or_init(|| sha256(self.feature_definitions_bytes()))
    }

    /// Resolve the exact typed Liquid context of one safe catalog key.
    ///
    /// # Errors
    /// Rejects an unknown key or an invalid/unsupported entry. Directory names
    /// are never used to infer Minecraft version or feature defaults.
    pub fn context(&self, key: &str) -> Result<ProjectionContext> {
        let entry = self.catalog.entry(key)?;
        let target = entry.target_id()?;
        let mut targets = SUPPORTED_TARGETS
            .iter()
            .map(|(id, _)| (format!("mc_{}", id.replace('.', "_")), *id == target))
            .collect::<BTreeMap<_, _>>();
        let forge_loader = matches!(target, "1.19.2" | "1.19.4" | "1.20");
        targets.insert("forge".to_owned(), forge_loader);
        targets.insert("neoforge".to_owned(), !forge_loader);
        Ok(ProjectionContext {
            minecraft_version: entry.minecraft_version.clone(),
            preset: key.to_owned(),
            environment: entry.environment.as_str().to_owned(),
            projection_key: key.to_owned(),
            features: entry.feature_flags(&self.feature_registry)?,
            targets,
        })
    }
}

/// Read one bounded checked metadata or single-file preview input.
///
/// # Errors
/// Rejects escaping/reparse paths, non-files, failed I/O and inputs over 1 MiB,
/// including growth observed after the initial metadata inspection.
pub fn read_bounded_catalog_input(root: &Path, relative: &str) -> Result<Vec<u8>> {
    read_bounded_input(root, relative, MAX_CATALOG_INPUT_BYTES)
}

/// Minimal catalog fixture for consumers of generated projects; no source or
/// generation-history snapshots are involved.
#[cfg(test)]
pub(crate) fn write_project_catalog_fixture(
    repo: &Path,
    key: &str,
    version: &str,
) -> Result<PathBuf> {
    let registry = repo.join(FEATURE_DEFINITIONS_PATH);
    fs::create_dir_all(registry.parent().unwrap())?;
    fs::write(registry, "{}")?;
    let catalog = ProjectionDefinitions(BTreeMap::from([(
        key.to_owned(),
        ProjectionDefinition {
            minecraft_version: version.to_owned(),
            environment: ProjectionEnvironment::Release,
            features: vec![],
        },
    )]));
    fs::write(
        repo.join(CATALOG_PATH),
        catalog.to_json(&SfmProjectionFeatureRegistry::test_registry([]))?,
    )?;
    let root = repo.join(catalog.project_dir(key)?);
    fs::create_dir_all(&root)?;
    Ok(root)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn project_lookup_uses_catalog_values_without_generation_history() {
        let temp = tempfile::tempdir().unwrap();
        let registry = temp.path().join(FEATURE_DEFINITIONS_PATH);
        fs::create_dir_all(registry.parent().unwrap()).unwrap();
        fs::write(
            registry,
            r#"{"echo":{"supported_targets":["1.21.0"],"requires":[]}}"#,
        )
        .unwrap();
        fs::write(temp.path().join(CATALOG_PATH),
            r#"{"custom/mc-1.19.2":{"minecraft_version":"1.21","environment":"dev","features":[]}}"#).unwrap();
        let project = temp
            .path()
            .join("platform/minecraft/projections/custom/mc-1.19.2");
        fs::create_dir_all(project.join("src")).unwrap();
        let catalog_path = temp.path().join(CATALOG_PATH);
        let original_catalog = fs::read(&catalog_path).unwrap();
        let (loaded, key) = CoreCatalog::for_project(&project).unwrap();
        assert!(loaded.catalog_sha256.get().is_none());
        assert!(loaded.feature_definitions_sha256.get().is_none());
        assert_eq!(key, "custom/mc-1.19.2");
        assert_eq!(loaded.context(&key).unwrap().minecraft_version, "1.21");
        assert!(CoreCatalog::for_project(&project.join("src")).is_err());
        fs::remove_file(catalog_path).unwrap();
        assert_eq!(loaded.catalog_sha256(), sha256(&original_catalog));
        assert!(loaded.feature_definitions_sha256.get().is_none());
        assert!(CoreCatalog::for_project(&project).is_err());
    }

    #[test]
    fn catalog_values_not_nested_key_spelling_choose_runtime_and_flags() {
        let temp = tempfile::tempdir().unwrap();
        let registry = temp.path().join(FEATURE_DEFINITIONS_PATH);
        fs::create_dir_all(registry.parent().unwrap()).unwrap();
        fs::write(
            registry,
            r#"{"echo":{"supported_targets":["1.21.0"],"requires":[]}}"#,
        )
        .unwrap();
        fs::write(
            temp.path().join(CATALOG_PATH),
            r#"{"custom/mc-1.19.2":{"minecraft_version":"1.21","environment":"dev","features":[]}}"#,
        )
        .unwrap();
        let loaded = CoreCatalog::load(Path::new("."), temp.path()).unwrap();
        let context = loaded.context("custom/mc-1.19.2").unwrap();
        assert_eq!(context.minecraft_version, "1.21");
        assert_eq!(context.environment, "dev");
        assert!(!context.features["echo"]);
        assert!(context.targets["mc_1_21_0"]);
        assert!(context.targets["neoforge"]);
        assert!(!context.targets["forge"]);
        assert!(loaded.context("unknown").is_err());
    }
}
