use facet::Facet;

#[derive(Clone, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[facet(transparent)]
pub(crate) struct ArtifactPurpose(pub String);

impl From<&str> for ArtifactPurpose {
    fn from(value: &str) -> Self {
        Self(value.to_string())
    }
}

impl From<String> for ArtifactPurpose {
    fn from(value: String) -> Self {
        Self(value)
    }
}

impl std::fmt::Display for ArtifactPurpose {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        self.0.fmt(f)
    }
}
