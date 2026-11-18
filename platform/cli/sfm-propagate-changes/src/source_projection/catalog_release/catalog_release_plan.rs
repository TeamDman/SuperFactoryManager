use super::CatalogReleaseArtifactReadiness;
use super::CatalogReleaseChangelog;
use super::CatalogReleaseProviderReadiness;
use super::CatalogReleaseRequest;
use super::catalog_release_properties::CatalogReleaseProperties;
use crate::cancellation::CancellationToken;
use crate::sfm_gradle_dot_properties_file_content::SfmGradleDotPropertiesFileContent;
use crate::source_projection::catalog_owned_project::CatalogOwnedFileReceipt;
use crate::source_projection::catalog_owned_project::CatalogOwnedProject;
use crate::source_projection::catalog_owned_project::collect_catalog_project;
use crate::source_projection::projection_catalog::ProjectionEnvironment;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;

pub(super) const CHANGELOG: &str = "src/main/resources/assets/sfm/template_programs/changelog.sfml";

/// A portable metadata observation, without artifact or publication authority.
#[derive(Debug, Facet)]
pub struct CatalogReleasePlan {
    schema: String,
    scope: String,
    tool_package_name: String,
    tool_package_version: String,
    tool_repository: String,
    /// Build-time abbreviated base hint; it does not identify uncommitted code.
    tool_base_revision: String,
    tool_source_identity: Option<String>,
    projection_key: String,
    environment: ProjectionEnvironment,
    project_dir: String,
    target_id: String,
    minecraft_version: String,
    package_loader: String,
    java_major: u16,
    mod_version: String,
    release_feature: String,
    catalog_sha256: String,
    feature_definitions_sha256: String,
    project_inputs_sha256: String,
    ownership_identity: String,
    inputs: BTreeMap<String, CatalogOwnedFileReceipt>,
    changelog: CatalogReleaseChangelog,
    metadata_verified: bool,
    artifact_readiness: CatalogReleaseArtifactReadiness,
    provider_readiness: CatalogReleaseProviderReadiness,
    publication_authorized: bool,
    source_writes_performed: bool,
    network_requests_performed: u64,
}

impl CatalogReleasePlan {
    /// Prepare only source metadata using strict current catalog ownership.
    ///
    /// # Errors
    /// Rejects non-release or ambiguous feature selections, stale generated
    /// inputs, metadata/version disagreements, invalid notes and cancellation.
    #[tracing::instrument(name = "catalog_release.plan", skip_all)]
    pub fn prepare(
        request: &CatalogReleaseRequest,
        cancellation: &CancellationToken,
    ) -> Result<Self> {
        cancellation.bail_if_cancelled()?;
        let collected = collect_catalog_project(
            request.repo_root(),
            request.repo_root(),
            request.projection(),
        )?;
        ensure!(
            collected.identity().environment == ProjectionEnvironment::Release,
            "release planning requires environment=release"
        );
        let release_feature = request.expected_mod_version().required_feature();
        let enabled = collected
            .context()
            .features
            .iter()
            .filter(|(name, enabled)| name.starts_with("release_version_") && **enabled)
            .map(|(name, _)| name.as_str())
            .collect::<Vec<_>>();
        ensure!(
            enabled == [release_feature.as_str()],
            "release projection must enable exactly the expected release feature {release_feature}"
        );
        cancellation.bail_if_cancelled()?;
        let project = collected.check_current()?;
        let gradle_properties = SfmGradleDotPropertiesFileContent::from_bytes(
            project.selected_input("gradle.properties")?.output_bytes(),
        )?;
        let properties = CatalogReleaseProperties::from_gradle_properties(&gradle_properties)?;
        ensure!(
            &properties.mod_version == request.expected_mod_version(),
            "selected mod_version disagrees with expected mod version"
        );
        let owner = project.receipt();
        ensure!(
            properties.minecraft_version == owner.minecraft_version,
            "selected minecraft_version disagrees with catalog upstream version"
        );
        let changelog = CatalogReleaseChangelog::parse(
            project.selected_input(CHANGELOG)?.output_bytes(),
            &properties.mod_version,
        )?;
        cancellation.bail_if_cancelled()?;
        project.recheck()?;
        cancellation.bail_if_cancelled()?;
        Self::from_checked_project(&project, &properties, changelog, release_feature)
    }

    pub(crate) fn projection_key(&self) -> &str {
        &self.projection_key
    }
    pub(crate) fn project_dir(&self) -> &str {
        &self.project_dir
    }
    pub(crate) fn target_id(&self) -> &str {
        &self.target_id
    }
    pub(crate) fn minecraft_version(&self) -> &str {
        &self.minecraft_version
    }
    pub(crate) fn mod_version(&self) -> &str {
        &self.mod_version
    }
    pub(crate) fn package_loader(&self) -> &str {
        &self.package_loader
    }
    pub(crate) fn java_major(&self) -> u16 {
        self.java_major
    }
    pub(crate) fn ownership_identity(&self) -> &str {
        &self.ownership_identity
    }
    pub(crate) fn changelog(&self) -> &CatalogReleaseChangelog {
        &self.changelog
    }

    fn from_checked_project(
        project: &CatalogOwnedProject,
        properties: &CatalogReleaseProperties,
        changelog: CatalogReleaseChangelog,
        release_feature: String,
    ) -> Result<Self> {
        let owner = project.receipt();
        let inputs = ["gradle.properties", CHANGELOG, "sfm-toolchain.lock.json"]
            .into_iter()
            .map(|output| {
                let receipt = owner.files.get(output).ok_or_else(|| {
                    eyre::eyre!("release metadata output is not selected: {output}")
                })?;
                Ok((output.to_owned(), receipt.clone()))
            })
            .collect::<Result<BTreeMap<_, _>>>()?;
        Ok(Self {
            schema: "sfm:catalog_release_plan@1".to_owned(),
            scope: "read_only_source_metadata_not_artifact_or_publication_qualification".to_owned(),
            tool_package_name: env!("CARGO_PKG_NAME").to_owned(),
            tool_package_version: env!("CARGO_PKG_VERSION").to_owned(),
            tool_repository: env!("CARGO_PKG_REPOSITORY").to_owned(),
            tool_base_revision: env!("GIT_REVISION").to_owned(),
            tool_source_identity: None,
            projection_key: owner.projection_key.clone(),
            environment: owner.environment,
            project_dir: owner.project_dir.clone(),
            target_id: owner.target_id.clone(),
            minecraft_version: owner.minecraft_version.clone(),
            package_loader: owner.loader.clone(),
            java_major: owner.java_major,
            mod_version: properties.mod_version.as_str().to_owned(),
            release_feature,
            catalog_sha256: owner.catalog_sha256.clone(),
            feature_definitions_sha256: owner.feature_definitions_sha256.clone(),
            project_inputs_sha256: owner.project_inputs_sha256.clone(),
            ownership_identity: project.ownership_identity()?,
            inputs,
            changelog,
            metadata_verified: true,
            artifact_readiness: CatalogReleaseArtifactReadiness::NotAssessed,
            provider_readiness: CatalogReleaseProviderReadiness::NotPrepared,
            publication_authorized: false,
            source_writes_performed: false,
            network_requests_performed: 0,
        })
    }
}
