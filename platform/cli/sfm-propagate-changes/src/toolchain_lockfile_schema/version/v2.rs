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
