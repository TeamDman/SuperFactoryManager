use crate::jar_build::ArtifactLockEntry;
use crate::jar_build::ArtifactLockfile;
use crate::jar_build::ArtifactSource;
use crate::jar_build::DependencyLockEntry;
use crate::jar_build::Repository;
use crate::jar_build::SourceBuildProvenance;
use crate::jar_build::SourceGitProvenance;
use crate::jar_build::WeakArtifactValidation;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::json_path::JsonOptionalPath;
use crate::jar_build::json_path::JsonPath;
use crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyKindV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyRoleV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyScopeV3;
use facet::Facet;
use std::path::PathBuf;

#[derive(Clone, Debug, Facet)]
pub(crate) struct ArtifactLockfileV2 {
    pub(crate) schema_version: u32,
    pub(crate) minecraft_version: String,
    #[facet(proxy = JsonPath)]
    pub(crate) maven_cache_dir: PathBuf,
    pub(crate) allow_local_artifact_cache: bool,
    pub(crate) repositories: Vec<Repository>,
    pub(crate) dependencies: Vec<DependencyLockEntry>,
    pub(crate) artifacts: Vec<ArtifactLockEntryV2>,
    #[facet(default)]
    pub(crate) migration_hints: Option<MigrationHintsV2>,
}

#[derive(Clone, Debug, Facet)]
pub(crate) struct MigrationHintsV2 {
    #[facet(default)]
    pub(crate) minecraft_dependency_id: Option<String>,
    #[facet(default)]
    pub(crate) loader_dependency_id: Option<String>,
    #[facet(default)]
    pub(crate) dependencies: Vec<DependencyMigrationHintV2>,
}

#[derive(Clone, Debug, Facet)]
pub(crate) struct DependencyMigrationHintV2 {
    pub(crate) id: String,
    #[facet(default)]
    pub(crate) kind: Option<DependencyKindV3>,
    #[facet(default)]
    pub(crate) role: Option<DependencyRoleV3>,
    #[facet(default)]
    pub(crate) display_name: Option<String>,
    #[facet(default)]
    pub(crate) project_url: Option<String>,
    #[facet(default)]
    pub(crate) notes: Option<String>,
    #[facet(default)]
    pub(crate) components: Vec<ComponentMigrationHintV2>,
}

#[derive(Clone, Debug, Facet)]
pub(crate) struct ComponentMigrationHintV2 {
    pub(crate) id: String,
    #[facet(default)]
    pub(crate) legacy_dependency_indices: Vec<usize>,
    #[facet(default)]
    pub(crate) acquisition: Option<ComponentAcquisitionV3>,
    #[facet(default)]
    pub(crate) legacy_artifact_index: Option<usize>,
    #[facet(default)]
    pub(crate) scopes: Option<Vec<DependencyScopeV3>>,
    #[facet(default)]
    pub(crate) artifact_treatment: Option<ArtifactTreatmentV3>,
    #[facet(default)]
    pub(crate) data_run_policy: Option<DataRunPolicyV3>,
}

#[derive(Clone, Debug, Facet)]
pub(crate) struct ArtifactLockEntryV2 {
    pub(crate) coordinate: Option<String>,
    pub(crate) source: ArtifactSource,
    pub(crate) repository: Option<String>,
    pub(crate) url: Option<String>,
    #[facet(proxy = JsonPath)]
    pub(crate) cache_path: PathBuf,
    #[facet(proxy = JsonOptionalPath)]
    pub(crate) original_path: Option<PathBuf>,
    #[facet(default)]
    #[facet(proxy = JsonOptionalPath)]
    pub(crate) source_relative_path: Option<PathBuf>,
    #[facet(default)]
    pub(crate) source_git: Option<SourceGitProvenance>,
    #[facet(default)]
    pub(crate) source_build: Option<SourceBuildProvenance>,
    #[facet(alias = "sha1")]
    pub(crate) hash: ContentHash,
    #[facet(default)]
    pub(crate) weak: Option<WeakArtifactValidation>,
}

impl ArtifactLockfileV2 {
    pub(crate) fn into_latest(self) -> ArtifactLockfile {
        let Self {
            schema_version,
            minecraft_version,
            maven_cache_dir,
            allow_local_artifact_cache,
            repositories,
            dependencies,
            artifacts,
            ..
        } = self;

        ArtifactLockfile {
            schema_version,
            minecraft_version,
            maven_cache_dir,
            allow_local_artifact_cache,
            repositories,
            dependencies,
            artifacts: artifacts
                .into_iter()
                .map(ArtifactLockEntryV2::into_latest)
                .collect(),
        }
    }
}

impl ArtifactLockEntryV2 {
    fn into_latest(self) -> ArtifactLockEntry {
        let Self {
            coordinate,
            source,
            repository,
            url,
            cache_path,
            original_path,
            source_relative_path,
            source_git,
            source_build,
            hash,
            weak,
        } = self;

        ArtifactLockEntry {
            coordinate,
            source,
            repository,
            url,
            cache_path,
            original_path,
            source_relative_path,
            source_git,
            source_build,
            hash,
            weak,
        }
    }
}
