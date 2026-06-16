use crate::branch_targets::BranchQuery;
use facet::Facet;
use std::fmt;
use std::ops::Deref;

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(transparent)]
pub struct BranchSelector(pub String);

impl BranchSelector {
    /// Parse this CLI selector into the branch query model.
    ///
    /// # Errors
    ///
    /// Returns an error when the selector expression is invalid.
    pub fn into_query(self) -> eyre::Result<BranchQuery> {
        BranchQuery::parse(&self.0)
    }
}

impl Default for BranchSelector {
    fn default() -> Self {
        Self("core".to_string())
    }
}

impl fmt::Display for BranchSelector {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl AsRef<str> for BranchSelector {
    fn as_ref(&self) -> &str {
        &self.0
    }
}

impl Deref for BranchSelector {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}
