use facet::Facet;
use std::fmt;

#[derive(Clone, Copy, Debug, Default, Eq, Facet, PartialEq)]
#[repr(u8)]
pub enum Parallelism {
    #[default]
    Sequential,
    Parallel {
        limit: usize,
    },
}

impl Parallelism {
    pub const DEFAULT_LIMIT: usize = 10;

    /// Convert the optional CLI value into execution parallelism.
    ///
    /// # Errors
    ///
    /// Returns an error if the requested parallelism is zero.
    pub fn from_cli(value: Option<usize>) -> eyre::Result<Self> {
        match value {
            None => Ok(Self::Sequential),
            Some(0) => eyre::bail!("--parallel must be greater than zero"),
            Some(limit) => Ok(Self::Parallel { limit }),
        }
    }

    #[must_use]
    pub const fn limit(self) -> Option<usize> {
        match self {
            Self::Sequential => None,
            Self::Parallel { limit } => Some(limit),
        }
    }

    #[must_use]
    pub const fn is_parallel(self) -> bool {
        matches!(self, Self::Parallel { .. })
    }
}

impl fmt::Display for Parallelism {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Sequential => f.write_str("sequential"),
            Self::Parallel { limit } => write!(f, "parallel({limit})"),
        }
    }
}
