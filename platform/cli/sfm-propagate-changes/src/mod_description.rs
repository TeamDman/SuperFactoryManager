use crate::sfm_mod_description::SfmModDescription;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::fmt;
use std::str::FromStr;

/// Human-readable mod description text, including decoded multiline content.
///
/// [Loader metadata](https://docs.neoforged.net/docs/gettingstarted/modfiles/)
/// supports multiline descriptions. SFM additionally requires meaningful nonempty
/// text and rejects NUL characters. Unicode, quotes and line endings are preserved;
/// TOML and JSON escaping belongs to their respective encoders.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(opaque, proxy = String, traits(FromStr))]
pub struct ModDescription(String);

impl ModDescription {
    /// # Errors
    /// Rejects whitespace-only descriptions and NUL characters.
    pub fn parse(value: &str) -> Result<Self> {
        ensure!(
            !value.trim().is_empty(),
            "mod description must contain display text"
        );
        ensure!(
            !value.contains('\0'),
            "mod description cannot contain NUL characters"
        );
        Ok(Self(value.to_owned()))
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl From<SfmModDescription> for ModDescription {
    fn from(_: SfmModDescription) -> Self {
        Self(SfmModDescription::VALUE.to_owned())
    }
}

impl FromStr for ModDescription {
    type Err = eyre::Report;

    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}

impl fmt::Display for ModDescription {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl TryFrom<String> for ModDescription {
    type Error = eyre::Report;
    fn try_from(value: String) -> eyre::Result<Self> {
        Self::parse(&value)
    }
}
impl From<&ModDescription> for String {
    fn from(value: &ModDescription) -> Self {
        value.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn descriptions_preserve_unicode_quotes_and_multiline_content() -> Result<()> {
        for value in [
            "Adds a certain magical factory manager to the game.\nAutomation is within your grasp!",
            "\r\n  工場 \"管理者\"\r\nSecond line\n",
            "Description with ''' quotes and \\ escapes",
        ] {
            let description: ModDescription = value.parse()?;
            assert_eq!(description.as_str(), value);
            assert_eq!(description.to_string(), value);
        }
        Ok(())
    }

    #[test]
    fn empty_and_whitespace_only_descriptions_are_rejected() {
        for value in ["", " ", "\t\r\n", "\u{2003}"] {
            assert!(ModDescription::parse(value).is_err(), "{value:?}");
        }
    }

    #[test]
    fn nul_characters_are_rejected() {
        for value in ["\0", "description\0", "first\nsecond\0line"] {
            assert!(ModDescription::parse(value).is_err(), "{value:?}");
        }
    }
}
