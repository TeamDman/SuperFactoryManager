use facet::Facet;
use std::fmt;
#[derive(Clone, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[facet(transparent)]
pub(crate) struct ArtifactId(pub String);

impl From<&str> for ArtifactId {
    fn from(value: &str) -> Self {
        Self(value.to_string())
    }
}

impl From<String> for ArtifactId {
    fn from(value: String) -> Self {
        Self(value)
    }
}

impl fmt::Display for ArtifactId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.0.fmt(f)
    }
}
