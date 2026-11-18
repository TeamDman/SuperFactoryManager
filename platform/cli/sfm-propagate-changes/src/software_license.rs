use crate::software_license_declaration::SoftwareLicenseDeclaration;
use eyre::Result;
use facet::Facet;
use std::fmt;
use std::str::FromStr;

/// A software-license declaration with a recognized MPL 2.0 identity.
///
/// [NeoForge](https://docs.neoforged.net/docs/gettingstarted/modfiles/) permits
/// general license text; SPDX identifiers are recommended. The existing SFM
/// label and the [SPDX MPL-2.0 identifier](https://spdx.org/licenses/MPL-2.0.html)
/// denote the same license here. Other declarations are retained explicitly;
/// this type does not claim to parse the complete SPDX expression language.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[repr(u8)]
pub enum SoftwareLicense {
    Mpl2_0,
    Declared(SoftwareLicenseDeclaration),
}

impl SoftwareLicense {
    /// # Errors
    /// Rejects empty, padded, multiline or NUL-containing license declarations.
    pub fn parse(value: &str) -> Result<Self> {
        Ok(match value {
            "MPL-2.0" | "Mozilla Public License Version 2.0" | "Mozilla Public License 2.0" => {
                Self::Mpl2_0
            }
            other => Self::Declared(SoftwareLicenseDeclaration::parse(other)?),
        })
    }

    #[must_use]
    pub fn spdx_id(&self) -> Option<&'static str> {
        match self {
            Self::Mpl2_0 => Some("MPL-2.0"),
            Self::Declared(_) => None,
        }
    }
}

impl FromStr for SoftwareLicense {
    type Err = eyre::Report;
    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}

impl fmt::Display for SoftwareLicense {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Mpl2_0 => f.write_str("MPL-2.0"),
            Self::Declared(value) => value.fmt(f),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn mpl_aliases_establish_one_identity_while_other_terms_remain_declared() -> Result<()> {
        for value in [
            "MPL-2.0",
            "Mozilla Public License Version 2.0",
            "Mozilla Public License 2.0",
        ] {
            let license = SoftwareLicense::parse(value)?;
            assert_eq!(license, SoftwareLicense::Mpl2_0);
            assert_eq!(license.spdx_id(), Some("MPL-2.0"));
        }
        assert_eq!(
            SoftwareLicense::parse("All Rights Reserved")?,
            SoftwareLicense::Declared(SoftwareLicenseDeclaration::parse("All Rights Reserved")?)
        );
        for value in ["", " MPL-2.0", "MPL-2.0 ", "MPL\n2", "MPL\0"] {
            assert!(SoftwareLicense::parse(value).is_err());
        }
        Ok(())
    }
}
