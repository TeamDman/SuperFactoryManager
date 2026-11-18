use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::fmt;
use std::str::FromStr;

/// Nonempty license terms declared in mod metadata, without asserting SPDX identity.
///
/// [NeoForge's license field](https://docs.neoforged.net/docs/gettingstarted/modfiles/)
/// permits general text. Requiring unpadded single-line text and rejecting NUL is
/// SFM's declaration policy, not proof of legal validity or SPDX registration.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(opaque, proxy = String, traits(FromStr))]
pub struct SoftwareLicenseDeclaration(String);
impl SoftwareLicenseDeclaration {
    /// # Errors
    /// Rejects empty, padded, multiline or NUL-containing declarations.
    pub fn parse(value: &str) -> Result<Self> {
        ensure!(
            !value.is_empty() && value == value.trim() && !value.contains(['\r', '\n', '\0']),
            "software license must be a nonempty unpadded single-line declaration"
        );
        Ok(Self(value.to_owned()))
    }
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}
impl FromStr for SoftwareLicenseDeclaration {
    type Err = eyre::Report;
    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}
impl fmt::Display for SoftwareLicenseDeclaration {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl TryFrom<String> for SoftwareLicenseDeclaration {
    type Error = eyre::Report;
    fn try_from(value: String) -> eyre::Result<Self> {
        Self::parse(&value)
    }
}
impl From<&SoftwareLicenseDeclaration> for String {
    fn from(value: &SoftwareLicenseDeclaration) -> Self {
        value.to_string()
    }
}
