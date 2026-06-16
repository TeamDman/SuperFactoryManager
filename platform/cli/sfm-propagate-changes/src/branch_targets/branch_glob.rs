#[cfg(test)]
use super::exact_branch::arbitrary_safe_branch_text;
use glob::Pattern;
use std::fmt;
use std::ops::Deref;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct BranchGlob(pub String);

impl BranchGlob {
    /// Build a validated branch glob.
    ///
    /// # Errors
    ///
    /// Returns an error if `glob` is not a valid glob pattern.
    pub fn new(glob: impl Into<String>) -> eyre::Result<Self> {
        let glob = glob.into();
        Pattern::new(&glob).map_err(|err| eyre::eyre!("Invalid branch glob '{glob}': {err}"))?;
        Ok(Self(glob))
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Display for BranchGlob {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl AsRef<str> for BranchGlob {
    fn as_ref(&self) -> &str {
        &self.0
    }
}

impl Deref for BranchGlob {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

#[cfg(test)]
impl<'a> arbitrary::Arbitrary<'a> for BranchGlob {
    fn arbitrary(u: &mut arbitrary::Unstructured<'a>) -> arbitrary::Result<Self> {
        let glob = arbitrary_safe_branch_text(u, true)?;
        Self::new(glob).map_err(|_err| arbitrary::Error::IncorrectFormat)
    }
}
