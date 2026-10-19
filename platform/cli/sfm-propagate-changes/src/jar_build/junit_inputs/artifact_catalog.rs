use super::source_artifact::SourceArtifact;
use facet::Facet;

/// Artifacts-only view of the reviewed schema-4 document; its dependency rows
/// deliberately are not interpreted as legacy schema-2 dependencies.
#[derive(Clone, Debug, Facet)]
pub(super) struct ArtifactCatalog {
    pub artifacts: Vec<SourceArtifact>,
}
