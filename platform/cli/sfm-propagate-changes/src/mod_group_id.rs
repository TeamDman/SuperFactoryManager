use crate::java_package_name::JavaPackageName;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::fmt;
use std::str::FromStr;

/// A mod's Maven group using SFM's portable lowercase ASCII package convention.
///
/// [Maven recommends Java package-style groups](https://maven.apache.org/guides/mini/guide-naming-conventions.html).
/// The generic [`JavaPackageName`] accepts Unicode Java identifiers; this mod
/// metadata type deliberately narrows each segment to `[a-z][a-z0-9_]*` for the
/// supported JDK range. This is an SFM policy, not a universal Maven restriction.
/// Parsing does not prove ownership of a reversed domain. The concrete SFM group
/// is a separate value, so other syntactically valid groups remain representable.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(opaque, proxy = String, traits(FromStr))]
pub struct ModGroupId(JavaPackageName);

impl ModGroupId {
    /// # Errors
    /// Rejects names outside the portable ASCII policy or Java package grammar.
    pub fn parse(value: &str) -> Result<Self> {
        ensure!(
            value.split('.').all(|segment| {
                let mut bytes = segment.bytes();
                bytes.next().is_some_and(|byte| byte.is_ascii_lowercase())
                    && bytes.all(|byte| {
                        byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'_'
                    })
            }),
            "mod group ID needs lowercase ASCII package segments"
        );
        Ok(Self(JavaPackageName::parse(value)?))
    }

    #[must_use]
    pub fn package_name(&self) -> &JavaPackageName {
        &self.0
    }
}

impl FromStr for ModGroupId {
    type Err = eyre::Report;

    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}

impl fmt::Display for ModGroupId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        fmt::Display::fmt(&self.0, f)
    }
}

impl TryFrom<String> for ModGroupId {
    type Error = eyre::Report;
    fn try_from(value: String) -> eyre::Result<Self> {
        Self::parse(&value)
    }
}
impl From<&ModGroupId> for String {
    fn from(value: &ModGroupId) -> Self {
        value.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generic_groups_have_typed_package_components() -> Result<()> {
        let group = ModGroupId::parse("ca.teamdman.sfm")?;
        assert_eq!(group.package_name().segments().len(), 3);
        assert_eq!(group.to_string(), "ca.teamdman.sfm");
        assert_eq!(ModGroupId::parse("example.sfm")?.to_string(), "example.sfm");
        assert_eq!(ModGroupId::parse("single")?.to_string(), "single");
        Ok(())
    }

    #[test]
    fn original_nonportable_spelling_is_rejected_before_canonicalization() {
        for value in [
            "",
            "ca..sfm",
            "ca.TeamDman.sfm",
            "ca.π.sfm",
            "ca.team\u{200d}dman.sfm",
            "ca.team\u{0000}dman.sfm",
            "ca.class.sfm",
            "ca._private.sfm",
            "ca.team-dman.sfm",
        ] {
            assert!(ModGroupId::parse(value).is_err(), "{value:?}");
        }
    }
}
