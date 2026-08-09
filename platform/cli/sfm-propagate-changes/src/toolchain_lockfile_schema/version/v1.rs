use crate::jar_build::ArtifactSource;
use crate::jar_build::DependencyLockEntry;
use crate::jar_build::Repository;
use crate::jar_build::SourceBuildProvenance;
use crate::jar_build::SourceGitProvenance;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::json_path::JsonOptionalPath;
use crate::jar_build::json_path::JsonPath;
use crate::toolchain_lockfile_schema::ENGINE_SCHEMA_VERSION;
use crate::toolchain_lockfile_schema::version::v2::ArtifactLockEntryV2;
use crate::toolchain_lockfile_schema::version::v2::ArtifactLockfileV2;
use facet::Facet;
use std::path::PathBuf;

#[derive(Clone, Debug, Facet)]
pub(crate) struct ArtifactLockfileV1 {
    schema_version: u32,
    minecraft_version: String,
    #[facet(proxy = JsonPath)]
    maven_cache_dir: PathBuf,
    allow_local_artifact_cache: bool,
    repositories: Vec<Repository>,
    dependencies: Vec<DependencyLockEntry>,
    artifacts: Vec<ArtifactLockEntryV1>,
}

#[derive(Clone, Debug, Facet)]
struct ArtifactLockEntryV1 {
    coordinate: Option<String>,
    source: ArtifactSource,
    repository: Option<String>,
    url: Option<String>,
    #[facet(proxy = JsonPath)]
    cache_path: PathBuf,
    #[facet(proxy = JsonOptionalPath)]
    original_path: Option<PathBuf>,
    #[facet(default)]
    #[facet(proxy = JsonOptionalPath)]
    source_relative_path: Option<PathBuf>,
    #[facet(default)]
    source_git: Option<SourceGitProvenance>,
    #[facet(default)]
    source_build: Option<SourceBuildProvenance>,
    #[facet(alias = "sha1")]
    hash: ContentHash,
}

impl ArtifactLockfileV1 {
    pub(crate) fn upgrade(self) -> ArtifactLockfileV2 {
        let Self {
            minecraft_version,
            maven_cache_dir,
            allow_local_artifact_cache,
            repositories,
            dependencies,
            artifacts,
            ..
        } = self;

        ArtifactLockfileV2 {
            schema_version: ENGINE_SCHEMA_VERSION,
            minecraft_version,
            maven_cache_dir,
            allow_local_artifact_cache,
            repositories,
            dependencies,
            artifacts: artifacts
                .into_iter()
                .map(ArtifactLockEntryV1::upgrade)
                .collect(),
            migration_hints: None,
        }
    }
}

impl ArtifactLockEntryV1 {
    fn upgrade(self) -> ArtifactLockEntryV2 {
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
        } = self;

        ArtifactLockEntryV2 {
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
            weak: None,
        }
    }
}
