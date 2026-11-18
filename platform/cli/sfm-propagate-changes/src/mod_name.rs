use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::fmt;
use std::str::FromStr;

/// A human-readable mod display name, preserving its Unicode text and spelling.
///
/// [Loader metadata](https://docs.neoforged.net/docs/gettingstarted/modfiles/)
/// defines `displayName` as display text, not as a mod identifier. Requiring
/// meaningful, nonempty, single-line text is SFM policy. Quotes and non-ASCII text
/// are allowed; their escaping belongs to the destination encoder.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(opaque, proxy = String, traits(FromStr))]
pub struct ModName(String);

impl ModName {
    /// # Errors
    /// Rejects whitespace-only names, line breaks and NUL characters.
    pub fn parse(value: &str) -> Result<Self> {
        ensure!(
            !value.trim().is_empty(),
            "mod name must contain display text"
        );
        ensure!(
            !value.contains(['\r', '\n', '\0']),
            "mod name must be a single line without NUL characters"
        );
        Ok(Self(value.to_owned()))
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl FromStr for ModName {
    type Err = eyre::Report;

    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}

impl fmt::Display for ModName {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl TryFrom<String> for ModName {
    type Error = eyre::Report;
    fn try_from(value: String) -> eyre::Result<Self> {
        Self::parse(&value)
    }
}
impl From<&ModName> for String {
    fn from(value: &ModName) -> Self {
        value.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn display_names_preserve_unicode_quotes_and_surrounding_spaces() -> Result<()> {
        for value in [
            "Super Factory Manager (SFM)",
            "工場 \"管理者\"",
            "  Mod name  ",
        ] {
            let name: ModName = value.parse()?;
            assert_eq!(name.as_str(), value);
            assert_eq!(name.to_string(), value);
        }
        Ok(())
    }

    #[test]
    fn empty_and_whitespace_only_names_are_rejected() {
        for value in ["", " ", "\t", "\u{2003}"] {
            assert!(ModName::parse(value).is_err(), "{value:?}");
        }
    }

    #[test]
    fn line_breaks_and_nul_are_rejected() {
        for value in ["line\nnext", "line\rnext", "line\r\nnext", "mod\0name"] {
            assert!(ModName::parse(value).is_err(), "{value:?}");
        }
    }
}
