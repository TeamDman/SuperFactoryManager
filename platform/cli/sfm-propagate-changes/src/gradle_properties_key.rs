use eyre::Result;
use eyre::ensure;
use std::borrow::Borrow;

/// A literal key in SFM's key=value property syntax.
#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct GradlePropertiesKey(String);

impl GradlePropertiesKey {
    /// # Errors
    /// Rejects empty, escaped or whitespace-containing keys and other separators.
    pub fn parse(key: &str) -> Result<Self> {
        ensure!(
            !key.is_empty()
                && key
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'.' | b'-')),
            "Gradle properties need literal key=value lines without escaped keys or key whitespace"
        );
        Ok(Self(key.to_owned()))
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl Borrow<str> for GradlePropertiesKey {
    fn borrow(&self) -> &str {
        self.as_str()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn keys_retain_external_spelling() -> Result<()> {
        for key in [
            "mod_id",
            "org.gradle.jvmargs",
            "custom-key",
            "pack_format_number",
        ] {
            assert_eq!(GradlePropertiesKey::parse(key)?.as_str(), key);
        }
        assert_ne!(
            GradlePropertiesKey::parse("org.gradle.daemon")?,
            GradlePropertiesKey::parse("org_gradle_daemon")?
        );
        Ok(())
    }

    #[test]
    fn keys_reject_ambiguous_spellings() {
        for key in [
            "", " mod_id", "mod_id ", "mod:id", "mod=id", r"mod\_id", "möd_id", "mod\nid",
        ] {
            assert!(GradlePropertiesKey::parse(key).is_err(), "{key:?}");
        }
    }
}
