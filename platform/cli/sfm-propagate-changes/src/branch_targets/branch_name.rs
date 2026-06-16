use super::MinecraftVersion;
use facet::Facet;
use std::fmt;
use std::ops::Deref;

#[derive(Clone, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[facet(transparent)]
pub struct BranchName(pub String);

impl BranchName {
    pub fn new(name: impl Into<String>) -> Self {
        Self(name.into())
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }

    #[must_use]
    pub fn is_core_branch(&self) -> bool {
        MinecraftVersion::is_core_branch_text(&self.0)
    }
}

impl fmt::Display for BranchName {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl AsRef<str> for BranchName {
    fn as_ref(&self) -> &str {
        &self.0
    }
}

impl Deref for BranchName {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl From<&str> for BranchName {
    fn from(value: &str) -> Self {
        Self::new(value)
    }
}

impl From<String> for BranchName {
    fn from(value: String) -> Self {
        Self::new(value)
    }
}
