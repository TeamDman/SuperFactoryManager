use eyre::bail;
use serde::Deserialize;
use serde::Serialize;
use std::str::FromStr;

#[derive(Clone, Debug, Eq, PartialEq, Hash)]
pub struct GameVersion {
    pub major: u16,
    pub minor: u16,
    pub patch: Option<u16>,
    pub pre_release: Option<String>,
}

impl std::fmt::Display for GameVersion {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let version_str = if let Some(patch) = self.patch {
            format!("{}.{}.{}", self.major, self.minor, patch)
        } else {
            format!("{}.{}", self.major, self.minor)
        };

        if let Some(pre_release) = &self.pre_release {
            write!(f, "{version_str}-{pre_release}")
        } else {
            write!(f, "{version_str}")
        }
    }
}

impl FromStr for GameVersion {
    type Err = eyre::Error;

    fn from_str(version: &str) -> Result<Self, Self::Err> {
        let (version, pre_release) = version
            .split_once("-")
            .map(|(v, p)| (v, Some(p.to_string())))
            .unwrap_or((version, None));

        let parts = version.split('.').collect::<Vec<&str>>();

        match parts.as_slice() {
            [major, minor, patch] => Ok(GameVersion {
                major: major.parse()?,
                minor: minor.parse()?,
                patch: Some(patch.parse()?),
                pre_release,
            }),
            [major, minor] => Ok(GameVersion {
                major: major.parse()?,
                minor: minor.parse()?,
                patch: None,
                pre_release,
            }),
            _ => bail!("Invalid version format: {}", version),
        }
    }
}
impl Serialize for GameVersion {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: serde::Serializer,
    {
        serializer.serialize_str(&self.to_string())
    }
}

impl<'de> Deserialize<'de> for GameVersion {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: serde::Deserializer<'de>,
    {
        let version_str = String::deserialize(deserializer)?;
        GameVersion::from_str(&version_str).map_err(serde::de::Error::custom)
    }
}
#[cfg(test)]
mod test {
    use super::GameVersion;
    use serde_json;
    use std::str::FromStr;

    #[test]
    fn it_works() -> eyre::Result<()> {
        let versions = [
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "1.21.5",
        ];
        for version in versions {
            let game_version = GameVersion::from_str(version)?;
            assert_eq!(game_version.major, 1);
            assert!(game_version.minor >= 19 && game_version.minor <= 21);
        }
        Ok(())
    }

    #[test]
    fn test_serialization() -> eyre::Result<()> {
        let v1 = GameVersion::from_str("1.19.2")?;
        let json = serde_json::to_string(&v1)?;
        assert_eq!(json, "\"1.19.2\"");

        let v2 = GameVersion::from_str("1.20")?;
        let json = serde_json::to_string(&v2)?;
        assert_eq!(json, "\"1.20\"");

        let v3 = GameVersion::from_str("1.21.0-alpha")?;
        let json = serde_json::to_string(&v3)?;
        assert_eq!(json, "\"1.21.0-alpha\"");

        Ok(())
    }

    #[test]
    fn test_deserialization() -> eyre::Result<()> {
        let json = "\"1.19.4\"";
        let v: GameVersion = serde_json::from_str(json)?;
        assert_eq!(v.major, 1);
        assert_eq!(v.minor, 19);
        assert_eq!(v.patch, Some(4));
        assert!(v.pre_release.is_none());

        let json = "\"1.20.1-beta\"";
        let v: GameVersion = serde_json::from_str(json)?;
        assert_eq!(v.major, 1);
        assert_eq!(v.minor, 20);
        assert_eq!(v.patch, Some(1));
        assert_eq!(v.pre_release.as_deref(), Some("beta"));

        let json = "\"1.21\"";
        let v: GameVersion = serde_json::from_str(json)?;
        assert_eq!(v.major, 1);
        assert_eq!(v.minor, 21);
        assert_eq!(v.patch, None);
        assert!(v.pre_release.is_none());

        Ok(())
    }

    #[test]
    fn test_invalid_versions() {
        let invalid_versions = ["", "1", "1.2.3.4", "a.b.c", "1.20.1.2"];
        for version in invalid_versions {
            assert!(
                GameVersion::from_str(version).is_err(),
                "Should fail for {}",
                version
            );
        }
    }

    #[test]
    fn test_pre_release_parsing() -> eyre::Result<()> {
        let v = GameVersion::from_str("1.21.0-rc1")?;
        assert_eq!(v.major, 1);
        assert_eq!(v.minor, 21);
        assert_eq!(v.patch, Some(0));
        assert_eq!(v.pre_release.as_deref(), Some("rc1"));

        let v = GameVersion::from_str("1.21-beta")?;
        assert_eq!(v.major, 1);
        assert_eq!(v.minor, 21);
        assert_eq!(v.patch, None);
        assert_eq!(v.pre_release.as_deref(), Some("beta"));

        Ok(())
    }
}
