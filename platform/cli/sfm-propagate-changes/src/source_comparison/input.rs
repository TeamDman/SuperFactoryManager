use facet::Facet;

/// Explicit inputs; Git trees never imply historical catalog reconstruction.
#[derive(Clone, Debug, PartialEq, Eq, Facet)]
#[repr(u8)]
#[facet(deny_unknown_fields)]
pub enum ComparisonInput {
    Directory {
        root: String,
    },
    PinnedGit {
        repository: String,
        commit: String,
        prefix: String,
    },
    Projection {
        repository: String,
        projection: String,
    },
}
