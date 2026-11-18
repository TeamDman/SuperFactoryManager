use eyre::Result;
use eyre::ensure;

/// A literal three-part release version, without a prerelease or build suffix.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CatalogReleaseVersion(String);

impl CatalogReleaseVersion {
    /// # Errors
    /// Rejects noncanonical versions, suffixes and components outside u32.
    pub fn parse(value: &str) -> Result<Self> {
        let parts = value.split('.').collect::<Vec<_>>();
        ensure!(
            parts.len() == 3
                && parts.iter().all(|part| {
                    !part.is_empty()
                        && part.bytes().all(|byte| byte.is_ascii_digit())
                        && (part.len() == 1 || !part.starts_with('0'))
                        && part.parse::<u32>().is_ok()
                }),
            "expected mod version must be a canonical major.minor.patch release"
        );
        Ok(Self(value.to_owned()))
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }

    /// The registry must explicitly declare and the projection must enable it.
    #[must_use]
    pub fn required_feature(&self) -> String {
        format!("release_version_{}", self.0.replace('.', "_"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn release_versions_cannot_smuggle_feature_names_or_prerelease_metadata() {
        assert_eq!(
            CatalogReleaseVersion::parse("4.35.0")
                .unwrap()
                .required_feature(),
            "release_version_4_35_0"
        );
        for value in [
            "",
            "4.35",
            "4.35.0 PRE",
            "4.35.0-dev",
            "4.35.0+local",
            "04.35.0",
            "4.35.00",
            " 4.35.0",
            "4.35.0\n",
            "4.35._",
            "4294967296.0.0",
        ] {
            assert!(CatalogReleaseVersion::parse(value).is_err(), "{value:?}");
        }
    }
}
