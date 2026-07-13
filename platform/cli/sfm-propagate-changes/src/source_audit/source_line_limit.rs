use super::SourceLineCount;
use facet::Facet;
use std::fmt;

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(proxy = usize)]
pub struct SourceLineLimit(pub usize);

impl From<usize> for SourceLineLimit {
    fn from(value: usize) -> Self {
        Self(value)
    }
}

impl From<&SourceLineLimit> for usize {
    fn from(value: &SourceLineLimit) -> Self {
        value.0
    }
}

impl SourceLineLimit {
    #[must_use]
    pub fn is_exceeded_by(self, count: SourceLineCount) -> bool {
        count.0 > self.0
    }

    #[must_use]
    pub fn first_excess_line(self) -> usize {
        self.0 + 1
    }
}

impl Default for SourceLineLimit {
    fn default() -> Self {
        Self(1000)
    }
}

impl fmt::Display for SourceLineLimit {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[cfg(test)]
mod tests {
    use super::SourceLineCount;
    use super::SourceLineLimit;

    #[test]
    fn threshold_is_strictly_greater_than_limit() {
        let limit = SourceLineLimit(1000);
        assert!(!limit.is_exceeded_by(SourceLineCount(1000)));
        assert!(limit.is_exceeded_by(SourceLineCount(1001)));
    }
}
