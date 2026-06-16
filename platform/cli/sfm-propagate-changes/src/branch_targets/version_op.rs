use std::cmp::Ordering;
use std::fmt;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
#[cfg_attr(test, derive(arbitrary::Arbitrary))]
pub enum VersionOp {
    Lt,
    Lte,
    Eq,
    Gte,
    Gt,
}

impl VersionOp {
    #[must_use]
    pub fn parse_prefix(input: &str) -> Option<(Self, &str)> {
        if let Some(rest) = input.strip_prefix(">=") {
            Some((Self::Gte, rest))
        } else if let Some(rest) = input.strip_prefix("<=") {
            Some((Self::Lte, rest))
        } else if let Some(rest) = input.strip_prefix("==") {
            Some((Self::Eq, rest))
        } else if let Some(rest) = input.strip_prefix('>') {
            Some((Self::Gt, rest))
        } else if let Some(rest) = input.strip_prefix('<') {
            Some((Self::Lt, rest))
        } else if let Some(rest) = input.strip_prefix('=') {
            Some((Self::Eq, rest))
        } else {
            None
        }
    }

    #[must_use]
    pub fn matches(self, ordering: Ordering) -> bool {
        match self {
            Self::Lt => ordering == Ordering::Less,
            Self::Lte => matches!(ordering, Ordering::Less | Ordering::Equal),
            Self::Eq => ordering == Ordering::Equal,
            Self::Gte => matches!(ordering, Ordering::Greater | Ordering::Equal),
            Self::Gt => ordering == Ordering::Greater,
        }
    }
}

impl fmt::Display for VersionOp {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(match self {
            Self::Lt => "<",
            Self::Lte => "<=",
            Self::Eq => "=",
            Self::Gte => ">=",
            Self::Gt => ">",
        })
    }
}
