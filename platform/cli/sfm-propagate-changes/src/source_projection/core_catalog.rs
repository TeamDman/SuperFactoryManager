//! Read the one authoritative catalog and its core-owned feature registry.
//!
//! No legacy manifest, imported snapshot, Git object or generated source root
//! participates in loading a named projection's explicit template context.

use super::candidate_lock::checked_directory;
use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_features::CoreFeatureDefinitions;
use super::core_features::FEATURE_DEFINITIONS_PATH;
use super::projection_catalog::CATALOG_PATH;
use super::projection_catalog::ProjectionCatalog;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::provenance::sha256;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;

pub const MAX_CATALOG_INPUT_BYTES: u64 = 1024 * 1024;

#[derive(Debug)]
pub struct CoreCatalog {
    pub repo_root: PathBuf,
    pub catalog: ProjectionCatalog,
    pub definitions: CoreFeatureDefinitions,
    pub registered_features: BTreeSet<String>,
    pub catalog_sha256: String,
    pub feature_definitions_sha256: String,
}

impl CoreCatalog {
    /// Load explicit projection selectors and validate every catalog entry.
    ///
    /// # Errors
    /// Rejects unsafe/reparse roots, oversized or malformed metadata, invalid
    /// keys/features, unsupported contexts and missing feature prerequisites.
    pub fn load(repo_root: &Path, invocation_dir: &Path) -> Result<Self> {
        let candidate = if repo_root == Path::new(".") {
            invocation_dir.to_path_buf()
        } else if repo_root.is_absolute() {
            repo_root.to_path_buf()
        } else {
            invocation_dir.join(repo_root)
        };
        let repo_root = checked_directory(&candidate).wrap_err("invalid source repository root")?;
        let registry_bytes = read_bounded_catalog_input(&repo_root, FEATURE_DEFINITIONS_PATH)?;
        let registry_text = std::str::from_utf8(&registry_bytes)
            .wrap_err("core feature definitions are not UTF-8")?;
        let definitions = CoreFeatureDefinitions::from_json(registry_text)?;
        let registered_features = definitions.registered_names();
        let catalog_bytes = read_bounded_catalog_input(&repo_root, CATALOG_PATH)?;
        let catalog_text =
            std::str::from_utf8(&catalog_bytes).wrap_err("projections catalog is not UTF-8")?;
        let catalog = ProjectionCatalog::from_json(catalog_text, &registered_features)?;
        for (key, entry) in &catalog.0 {
            definitions
                .validate_entry(entry)
                .wrap_err_with(|| format!("projection `{key}`"))?;
        }
        Ok(Self {
            repo_root,
            catalog,
            definitions,
            registered_features,
            catalog_sha256: sha256(&catalog_bytes),
            feature_definitions_sha256: sha256(&registry_bytes),
        })
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
            features: entry.feature_flags(&self.registered_features)?,
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
    let path = checked_file(root, relative)?;
    let file =
        fs::File::open(&path).wrap_err_with(|| format!("cannot open source input `{relative}`"))?;
    ensure!(
        file.metadata()?.len() <= MAX_CATALOG_INPUT_BYTES,
        "source input `{relative}` exceeds the {MAX_CATALOG_INPUT_BYTES}-byte limit"
    );
    let mut bytes = Vec::new();
    file.take(MAX_CATALOG_INPUT_BYTES + 1)
        .read_to_end(&mut bytes)
        .wrap_err_with(|| format!("cannot read source input `{relative}`"))?;
    ensure!(
        bytes.len() as u64 <= MAX_CATALOG_INPUT_BYTES,
        "source input `{relative}` exceeds the {MAX_CATALOG_INPUT_BYTES}-byte limit"
    );
    Ok(bytes)
}

#[cfg(test)]
mod tests {
    use super::*;

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
