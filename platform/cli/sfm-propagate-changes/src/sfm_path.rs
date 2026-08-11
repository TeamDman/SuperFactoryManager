//! Path type for serialization with facet/styx.
//!
//! `SfmPath` wraps a path and serializes as a String, working around
//! the lack of native `PathBuf` support in facet serialization.

use facet::Facet;
use std::convert::Infallible;
use std::ffi::OsStr;
use std::ffi::OsString;
use std::path::Path;
use std::path::PathBuf;

/// A path that serializes as a String for facet/styx compatibility.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Facet)]
#[facet(proxy = String)]
pub struct SfmPath(pub PathBuf);

impl SfmPath {
    /// Create a new `SfmPath` from a `PathBuf`.
    pub fn new(path: impl Into<PathBuf>) -> Self {
        Self(path.into())
    }

    /// Get the inner `PathBuf`.
    #[must_use]
    pub fn into_inner(self) -> PathBuf {
        self.0
    }
}

impl From<PathBuf> for SfmPath {
    fn from(path: PathBuf) -> Self {
        Self(path)
    }
}

impl From<&Path> for SfmPath {
    fn from(path: &Path) -> Self {
        Self(path.to_path_buf())
    }
}

impl From<String> for SfmPath {
    fn from(s: String) -> Self {
        Self(PathBuf::from(s))
    }
}

impl From<&str> for SfmPath {
    fn from(s: &str) -> Self {
        Self(PathBuf::from(s))
    }
}

impl From<OsString> for SfmPath {
    fn from(s: OsString) -> Self {
        Self(PathBuf::from(s))
    }
}

impl From<&OsStr> for SfmPath {
    fn from(s: &OsStr) -> Self {
        Self(PathBuf::from(s))
    }
}

impl From<SfmPath> for PathBuf {
    fn from(path: SfmPath) -> Self {
        path.0
    }
}

impl AsRef<Path> for SfmPath {
    fn as_ref(&self) -> &Path {
        &self.0
    }
}

impl std::ops::Deref for SfmPath {
    type Target = Path;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl std::fmt::Display for SfmPath {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0.display())
    }
}

// ============================================================================
// Facet Proxy Conversions
// ============================================================================

/// Serialization: `&SfmPath` -> `String`
#[expect(clippy::infallible_try_from, reason = "Required by facet proxy")]
impl TryFrom<&SfmPath> for String {
    type Error = Infallible;
    fn try_from(path: &SfmPath) -> Result<Self, Self::Error> {
        Ok(path.0.to_string_lossy().into_owned())
    }
}
