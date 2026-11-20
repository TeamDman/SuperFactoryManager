use super::catalog_owned_project::collect_catalog_project_from_metadata;
use super::catalog_owned_project::read_checked;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CORE_METADATA_PATH;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::CoreSelection;
use super::core_inputs::MAX_CORE_METADATA_BYTES;
use super::projection_environment::ProjectionEnvironment;
use eyre::Result;
use std::collections::BTreeSet;
use std::str::from_utf8;
use std::sync::Arc;

/// Catalog and project-input bytes captured and validated together for selection.
pub(crate) struct CatalogProjectMetadata {
    pub(super) catalog: Arc<CoreCatalog>,
    pub(super) bytes: Arc<[u8]>,
    inputs: CoreProjectInputs,
}

impl CatalogProjectMetadata {
    pub(crate) fn load(catalog: CoreCatalog) -> Result<Self> {
        let bytes = read_checked(
            &catalog.repo_root,
            CORE_METADATA_PATH,
            MAX_CORE_METADATA_BYTES,
        )?;
        let inputs = CoreProjectInputs::from_json(from_utf8(&bytes)?, &catalog.feature_registry)?;
        Ok(Self {
            catalog: Arc::new(catalog),
            bytes: bytes.into(),
            inputs,
        })
    }

    pub(super) fn select(
        &self,
        context: &ProjectionContext,
        source_inventory: &BTreeSet<String>,
    ) -> Result<CoreSelection> {
        self.inputs
            .select_from_validated_metadata(context, source_inventory)
    }

    pub(crate) fn check_projection(&self, projection_key: &str) -> Result<()> {
        collect_catalog_project_from_metadata(self, projection_key, false)?
            .check_generated_files()?;
        Ok(())
    }

    pub(crate) fn projection_environment(&self, key: &str) -> Result<ProjectionEnvironment> {
        Ok(self.catalog.catalog.entry(key)?.environment)
    }
}
