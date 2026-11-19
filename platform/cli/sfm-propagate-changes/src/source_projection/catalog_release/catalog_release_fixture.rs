use super::CatalogReleasePlan;
use super::CatalogReleaseRequest;
use super::CatalogReleaseVersion;
use super::catalog_release_plan::CHANGELOG;
use crate::cancellation::CancellationToken;
use crate::gradle_dot_properties_file_content::test_content;
use crate::source_projection::catalog_owned_project::tests::Fixture;
use crate::source_projection::core_features::CoreFeatureDefinition;
use crate::source_projection::core_features::FEATURE_DEFINITIONS_PATH;
use crate::source_projection::core_inputs::CORE_ROOT;
use crate::source_projection::projection_catalog::CATALOG_PATH;
use crate::source_projection::projection_catalog::ProjectionCatalog;
use crate::source_projection::projection_catalog::SUPPORTED_TARGETS;
use crate::source_projection::sfm_projection_feature_registry::SfmProjectionFeatureRegistry;
use eyre::Result;
use std::collections::BTreeMap;
use std::fs;
use std::path::Path;
use std::path::PathBuf;

pub(super) const RELEASE_FEATURE: &str = "release_version_4_35_0";
pub(super) const OTHER_RELEASE_FEATURE: &str = "release_version_4_36_0";
pub(super) const SECTION: &str = "---- 4.35.0 ----\r\n-- Selected release change.\r\n\r\n";

pub(super) struct CatalogReleaseFixture {
    pub(super) owned: Fixture,
    target: String,
    upstream: String,
    pub(super) key: String,
    pub(super) dev_key: String,
}

impl CatalogReleaseFixture {
    pub(super) fn new(target: &str) -> Result<Self> {
        let slot = SUPPORTED_TARGETS
            .iter()
            .position(|(id, _)| *id == target)
            .unwrap();
        let upstream = SUPPORTED_TARGETS[slot].1.to_owned();
        let mut fixture = Self {
            owned: Fixture::new_for_target(target),
            target: target.to_owned(),
            upstream,
            key: Fixture::key(slot, "release"),
            dev_key: Fixture::key(slot, "dev"),
        };
        fixture.set_properties(&format!(
            "mod_id=sfm\nmod_version=4.35.0\nminecraft_version={}\n",
            fixture.upstream,
        ))?;
        let loader_metadata = if matches!(target, "1.21.0" | "1.21.1" | "26.1.2") {
            "neoforge.mods.toml"
        } else {
            "mods.toml"
        };
        // Resources belong to the fixture's existing src/... source rule;
        // project_files is reserved for non-source build inputs.
        let loader_metadata = fixture
            .repository()
            .join(CORE_ROOT)
            .join("src/main/resources/META-INF")
            .join(loader_metadata);
        fs::create_dir_all(loader_metadata.parent().unwrap())?;
        fs::write(
            loader_metadata,
            b"modLoader=\"javafml\"\n[[mods]]\nmodId=\"${mod_id}\"\nversion=\"${mod_version}\"\n",
        )?;
        let changelog = fixture.repository().join(CORE_ROOT).join(CHANGELOG);
        fs::create_dir_all(changelog.parent().unwrap())?;
        fs::write(
            changelog,
            format!(
                "NAME \"Changelog\"\r\n-- Preamble stays outside release notes.\r\n{SECTION}---- 4.34.0 ----\r\n-- Historical change.\r\n",
            ),
        )?;
        let definitions_path = fixture.repository().join(FEATURE_DEFINITIONS_PATH);
        let mut definitions =
            SfmProjectionFeatureRegistry::from_json(&fs::read_to_string(&definitions_path)?)?;
        for name in [RELEASE_FEATURE, OTHER_RELEASE_FEATURE] {
            definitions.0.insert(
                name.to_owned(),
                CoreFeatureDefinition {
                    supported_targets: vec![target.to_owned()],
                    requires: vec![],
                },
            );
        }
        fs::write(
            definitions_path,
            facet_json::to_string_pretty(&definitions)?,
        )?;
        fixture.set_features(&fixture.key, &[RELEASE_FEATURE])?;
        fixture.set_features(&fixture.dev_key, &[RELEASE_FEATURE])?;
        Ok(fixture)
    }

    pub(super) fn repository(&self) -> &Path {
        self.owned.repository()
    }

    pub(super) fn root(&self) -> PathBuf {
        self.repository()
            .join("platform/minecraft/projections")
            .join(&self.key)
    }

    pub(super) fn set_properties(&mut self, bytes: &str) -> Result<()> {
        let bytes = test_content(bytes);
        self.owned.set_project_file_for(
            &self.target,
            "gradle.properties",
            "build/release-fixture/gradle.properties",
            bytes.as_bytes(),
            false,
        )
    }

    pub(super) fn set_features(&self, key: &str, features: &[&str]) -> Result<()> {
        let definitions = SfmProjectionFeatureRegistry::from_json(&fs::read_to_string(
            self.repository().join(FEATURE_DEFINITIONS_PATH),
        )?)?;
        let path = self.repository().join(CATALOG_PATH);
        let mut catalog = ProjectionCatalog::from_json(&fs::read_to_string(&path)?, &definitions)?;
        catalog.0.get_mut(key).unwrap().features =
            features.iter().map(|name| (*name).to_owned()).collect();
        fs::write(path, catalog.to_json(&definitions)?)?;
        Ok(())
    }

    pub(super) fn publish(&self) {
        assert_eq!(
            self.owned.publish(&self.key),
            fs::canonicalize(self.root()).unwrap()
        );
    }

    pub(super) fn request(&self, key: &str) -> Result<CatalogReleaseRequest> {
        CatalogReleaseRequest::new(
            self.repository(),
            key.to_owned(),
            CatalogReleaseVersion::parse("4.35.0")?,
        )
    }

    pub(super) fn prepare(&self) -> Result<CatalogReleasePlan> {
        CatalogReleasePlan::prepare(&self.request(&self.key)?, &CancellationToken::new())
    }

    pub(super) fn assert_rejected_without_writes(&self, diagnostic: &str) -> Result<()> {
        let before = inventory(self.repository())?;
        let error = self.prepare().unwrap_err().to_string();
        assert!(error.contains(diagnostic), "{error}");
        assert_eq!(inventory(self.repository())?, before);
        Ok(())
    }
}

// Include directories, so creating even an empty cache/build root is detected.
pub(super) fn inventory(root: &Path) -> Result<BTreeMap<PathBuf, Option<Vec<u8>>>> {
    walkdir::WalkDir::new(root)
        .into_iter()
        .map(|entry| {
            let entry = entry?;
            let relative = entry.path().strip_prefix(root)?.to_path_buf();
            let bytes = if entry.file_type().is_dir() {
                None
            } else {
                Some(fs::read(entry.path())?)
            };
            Ok((relative, bytes))
        })
        .collect()
}
