use facet::Facet;

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(super) enum ArtifactAuditIssueKind {
    LockfileMissing,
    ArtifactMissing,
    ArtifactHashMismatch,
    ProvenanceHashMismatch,
    ProvenanceMismatch,
    OriginalSourceMissing,
    OriginalSourceHashMismatch,
    SourceGitMissing,
    SourceGitMismatch,
    NonPortableProvenance,
    DependencyArtifactMissing,
}

impl ArtifactAuditIssueKind {
    pub(super) const fn label(self) -> &'static str {
        match self {
            Self::LockfileMissing => "lockfile-missing",
            Self::ArtifactMissing => "artifact-missing",
            Self::ArtifactHashMismatch => "artifact-hash-mismatch",
            Self::ProvenanceHashMismatch => "provenance-hash-mismatch",
            Self::ProvenanceMismatch => "provenance-mismatch",
            Self::OriginalSourceMissing => "original-source-missing",
            Self::OriginalSourceHashMismatch => "original-source-hash-mismatch",
            Self::SourceGitMissing => "source-git-missing",
            Self::SourceGitMismatch => "source-git-mismatch",
            Self::NonPortableProvenance => "non-portable-provenance",
            Self::DependencyArtifactMissing => "dependency-artifact-missing",
        }
    }
}
