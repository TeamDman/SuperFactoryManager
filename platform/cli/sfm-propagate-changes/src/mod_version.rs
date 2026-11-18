use eyre::Result;
use eyre::WrapErr;
use facet::Facet;
use semver::Version;
use std::fmt;
use std::str::FromStr;

/// SFM's general mod version, retaining `SemVer` prerelease and build data.
///
/// The [SemVer specification](https://semver.org/spec/v2.0.0.html) supplies its
/// grammar. Strict `SemVer` is an SFM policy: [NeoForge's version guidance](https://docs.neoforged.net/docs/gettingstarted/versioning/#mods)
/// permits other mod-version schemes. The selected parser uses `u64` numeric
/// components. Parsing does not establish release or publication eligibility.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(opaque, proxy = String, traits(FromStr))]
pub struct ModVersion(#[facet(opaque)] Version);

impl ModVersion {
    #[must_use]
    pub const fn new(version: Version) -> Self {
        Self(version)
    }
    /// # Errors
    /// Rejects non-SemVer input, including partial versions and leading zeroes.
    pub fn parse(value: &str) -> Result<Self> {
        Ok(Self(Version::parse(value).wrap_err("invalid mod SemVer")?))
    }
    #[must_use]
    pub const fn version(&self) -> &Version {
        &self.0
    }
}
impl FromStr for ModVersion {
    type Err = eyre::Report;
    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}
impl fmt::Display for ModVersion {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.0.fmt(f)
    }
}

impl TryFrom<String> for ModVersion {
    type Error = eyre::Report;
    fn try_from(value: String) -> eyre::Result<Self> {
        Self::parse(&value)
    }
}
impl From<&ModVersion> for String {
    fn from(value: &ModVersion) -> Self {
        value.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn general_versions_preserve_prerelease_and_build_separately_from_release_policy() -> Result<()>
    {
        let value = ModVersion::parse("4.35.0-dev.1+build.7")?;
        assert_eq!(value.version().major, 4);
        assert_eq!(value.version().pre.as_str(), "dev.1");
        assert_eq!(value.version().build.as_str(), "build.7");
        assert_eq!(value.to_string(), "4.35.0-dev.1+build.7");
        for invalid in [
            "4.35",
            "04.35.0",
            "4.35.0-dev.01",
            "4.35.0.",
            "4.35.0 ",
            " 4.35.0",
        ] {
            assert!(ModVersion::parse(invalid).is_err(), "{invalid}");
        }
        Ok(())
    }
}
