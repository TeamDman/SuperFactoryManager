use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::fmt;
use std::str::FromStr;

/// A mod identifier using the portable syntax required by older Forge targets.
///
/// [Forge's loader](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.19.x/fmlloader/src/main/java/net/minecraftforge/fml/loading/moddiscovery/ModInfo.java)
/// requires `^[a-z][a-z0-9_]{1,63}$`: 2-64 ASCII characters, beginning with a
/// lowercase letter. [Current NeoForge](https://raw.githubusercontent.com/neoforged/FancyModLoader/main/loader/src/main/java/net/neoforged/fml/loading/moddiscovery/ModInfo.java)
/// also permits dotted identifiers. This type keeps the older portable contract
/// used across SFM's supported loaders; it does not establish a particular mod's identity.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(opaque, proxy = String, traits(FromStr))]
pub struct ModId(String);

impl ModId {
    /// # Errors
    /// Rejects values outside the portable loader identifier syntax.
    pub fn parse(value: &str) -> Result<Self> {
        ensure!(
            (2..=64).contains(&value.len())
                && value.as_bytes()[0].is_ascii_lowercase()
                && value.bytes().all(|byte| {
                    byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'_'
                }),
            "mod ID must be 2-64 ASCII characters, start with a lowercase letter and contain only lowercase letters, digits or underscores"
        );
        Ok(Self(value.to_owned()))
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl FromStr for ModId {
    type Err = eyre::Report;

    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}

impl fmt::Display for ModId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl TryFrom<String> for ModId {
    type Error = eyre::Report;
    fn try_from(value: String) -> eyre::Result<Self> {
        Self::parse(&value)
    }
}
impl From<&ModId> for String {
    fn from(value: &ModId) -> Self {
        value.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn identifiers_retain_their_spelling_without_imposing_sfm_identity() -> Result<()> {
        for value in ["sfm", "a1", "mod_name", "other", "another", "decoy"] {
            let id: ModId = value.parse()?;
            assert_eq!(id.as_str(), value);
            assert_eq!(id.to_string(), value);
        }
        Ok(())
    }

    #[test]
    fn portable_length_boundaries_are_enforced() {
        for value in ["".to_owned(), "a".to_owned(), "a".repeat(65)] {
            assert!(ModId::parse(&value).is_err(), "{value:?}");
        }
        assert!(ModId::parse("ab").is_ok());
        assert!(ModId::parse(&"a".repeat(64)).is_ok());
    }

    #[test]
    fn invalid_initial_characters_and_nonportable_characters_are_rejected() {
        for value in [
            "SFM", "1mod", "_mod", "my-mod", "my.mod", "my mod", "sfm\n", " sfm", "sfm ", "sfm\0",
            "möd", "δelta",
        ] {
            assert!(ModId::parse(value).is_err(), "{value:?}");
        }
    }
}
