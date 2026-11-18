use crate::software_license::SoftwareLicense;
use eyre::Result;
use facet::Facet;
use std::fmt;
use std::str::FromStr;

/// A mod's parsed license declaration.
///
/// [Loader metadata](https://docs.neoforged.net/docs/gettingstarted/modfiles/)
/// requires a license, recommending an SPDX identifier or license link.
/// [`SoftwareLicense`] preserves that broad contract; SFM's concrete witness
/// separately requires the [MPL 2.0 identity](https://spdx.org/licenses/MPL-2.0.html).
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(opaque, proxy = String, traits(FromStr))]
pub struct ModSoftwareLicense(SoftwareLicense);

impl ModSoftwareLicense {
    #[must_use]
    pub const fn new(license: SoftwareLicense) -> Self {
        Self(license)
    }
    /// # Errors
    /// Rejects invalid license declarations through the owned license parser.
    pub fn parse(value: &str) -> Result<Self> {
        Ok(Self(SoftwareLicense::parse(value)?))
    }
    #[must_use]
    pub const fn license(&self) -> &SoftwareLicense {
        &self.0
    }
}
impl FromStr for ModSoftwareLicense {
    type Err = eyre::Report;
    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}
impl fmt::Display for ModSoftwareLicense {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.0.fmt(f)
    }
}

impl TryFrom<String> for ModSoftwareLicense {
    type Error = eyre::Report;
    fn try_from(value: String) -> eyre::Result<Self> {
        Self::parse(&value)
    }
}
impl From<&ModSoftwareLicense> for String {
    fn from(value: &ModSoftwareLicense) -> Self {
        value.to_string()
    }
}
