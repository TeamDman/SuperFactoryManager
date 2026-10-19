use super::source_artifact::SourceArtifact;
use super::source_dependency::SourceDependency;
use facet::Facet;
#[derive(Clone, Debug, Facet)]
pub(super) struct SourceLock {
    pub artifacts: Vec<SourceArtifact>,
    #[facet(default)]
    pub dependencies: Vec<SourceDependency>,
}
