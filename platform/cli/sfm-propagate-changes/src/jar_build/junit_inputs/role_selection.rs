use super::MAX_METADATA_BYTES;
use super::Path;
use super::PreflightReport;
use super::identity;
use super::read_input;
use super::sha256;
use crate::source_projection::context::ProjectionContext;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::source_projection::core_catalog::MAX_CATALOG_INPUT_BYTES;
use crate::source_projection::core_features::FEATURE_DEFINITIONS_PATH;
use crate::source_projection::core_inputs::CORE_METADATA_PATH;
use crate::source_projection::core_inputs::CoreProjectInputs;
use crate::source_projection::core_inputs::CoreSelection;
use crate::source_projection::core_inputs::select_core_inputs;
use crate::source_projection::projection_definitions::CATALOG_PATH;
use crate::source_projection::sfm_repo_path::SfmRepoPath;
use std::collections::BTreeSet;
use std::str::from_utf8;

#[derive(Debug)]
pub(super) struct RoleSelection {
    catalog: CoreCatalog,
    metadata: CoreProjectInputs,
    metadata_sha256: String,
}

impl RoleSelection {
    pub(super) fn load(report: &mut PreflightReport, repo: &Path) -> eyre::Result<Self> {
        let repo_root = SfmRepoPath::resolve(repo, repo)?;
        let catalog_raw = read_input(report, &repo_root, CATALOG_PATH, MAX_CATALOG_INPUT_BYTES)?;
        let features_raw = read_input(
            report,
            &repo_root,
            FEATURE_DEFINITIONS_PATH,
            MAX_CATALOG_INPUT_BYTES,
        )?;
        let catalog = CoreCatalog::from_captured_metadata(repo_root, catalog_raw, features_raw)?;
        let raw = read_input(
            report,
            &catalog.repo_root,
            CORE_METADATA_PATH,
            MAX_METADATA_BYTES,
        )?;
        let metadata = CoreProjectInputs::from_json(from_utf8(&raw)?, &catalog.feature_registry)?;
        Ok(Self {
            catalog,
            metadata,
            metadata_sha256: sha256(&raw),
        })
    }

    pub(super) fn for_target(
        &self,
        target: &str,
    ) -> eyre::Result<(ProjectionContext, CoreSelection, String)> {
        let context = self.catalog.context(&format!("sfm-4.35.0/mc-{target}"))?;
        let selection = select_core_inputs(&self.metadata, &context, &BTreeSet::new())?;
        let selection_identity = identity(&(
            self.catalog.catalog_sha256().to_owned(),
            self.catalog.feature_definitions_sha256().to_owned(),
            self.metadata_sha256.clone(),
            identity(&context)?,
        ))?;
        Ok((context, selection, selection_identity))
    }
}
