use facet::Facet;
use std::fmt;

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
pub struct DetectedSourceLocation {
    pub branch: String,
    pub path: String,
    pub line: usize,
    pub column: usize,
}

impl DetectedSourceLocation {
    #[must_use]
    pub fn new(
        branch: impl Into<String>,
        path: impl Into<String>,
        line: usize,
        column: usize,
    ) -> Self {
        Self {
            branch: branch.into(),
            path: path.into(),
            line,
            column,
        }
    }
}

impl fmt::Display for DetectedSourceLocation {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "{}:{}:{}:{}",
            self.branch, self.path, self.line, self.column
        )
    }
}
