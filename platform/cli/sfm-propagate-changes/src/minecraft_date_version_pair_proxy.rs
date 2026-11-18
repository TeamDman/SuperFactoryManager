use crate::branch_targets::MinecraftVersion;
use chrono::NaiveDate;
use facet::Facet;
use std::convert::Infallible;
use std::str::FromStr;

/// Optional string representation of a calendar date and Minecraft version pair.
#[derive(Clone, Debug, Facet)]
#[facet(transparent, traits(FromStr))]
pub struct MinecraftDateVersionPairProxy(Option<String>);

impl MinecraftDateVersionPairProxy {
    pub(crate) fn parse_pair(value: &str) -> Result<(NaiveDate, MinecraftVersion), String> {
        let (date, version) = value
            .split_once('-')
            .ok_or_else(|| "mapping version needs YYYY.MM.DD-MinecraftVersion".to_owned())?;
        if date.len() != 10
            || !date.bytes().enumerate().all(|(index, byte)| {
                if matches!(index, 4 | 7) {
                    byte == b'.'
                } else {
                    byte.is_ascii_digit()
                }
            })
        {
            return Err("mapping version date must use YYYY.MM.DD".to_owned());
        }
        let date = NaiveDate::parse_from_str(date, "%Y.%m.%d")
            .map_err(|error| format!("invalid mapping version date: {error}"))?;
        let version = MinecraftVersion::parse(version)
            .map_err(|error| format!("invalid mapping Minecraft version: {error}"))?;
        Ok((date, version))
    }

    pub(crate) fn format_pair(pair: &(NaiveDate, MinecraftVersion)) -> Result<String, String> {
        let (date, version) = pair;
        let date = date.format("%Y.%m.%d").to_string();
        if date.len() != 10 {
            return Err("mapping version date must have a four-digit unsigned year".to_owned());
        }
        Ok(format!("{date}-{version}"))
    }
}

impl FromStr for MinecraftDateVersionPairProxy {
    type Err = Infallible;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        Ok(Self(Some(value.to_owned())))
    }
}

impl TryFrom<MinecraftDateVersionPairProxy> for Option<(NaiveDate, MinecraftVersion)> {
    type Error = String;

    fn try_from(value: MinecraftDateVersionPairProxy) -> Result<Self, Self::Error> {
        value
            .0
            .as_deref()
            .map(MinecraftDateVersionPairProxy::parse_pair)
            .transpose()
    }
}

impl TryFrom<&Option<(NaiveDate, MinecraftVersion)>> for MinecraftDateVersionPairProxy {
    type Error = String;

    fn try_from(value: &Option<(NaiveDate, MinecraftVersion)>) -> Result<Self, Self::Error> {
        let wire = value.as_ref().map(Self::format_pair).transpose()?;
        Ok(Self(wire))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::raw_gradle_properties_document::RawGradlePropertiesDocument;
    use eyre::Result;

    // The business field remains a tuple; only its wire representation has a proxy type.
    #[derive(Debug, Facet)]
    struct Fixture {
        #[facet(default, opaque, proxy = MinecraftDateVersionPairProxy)]
        mapping_version: Option<(NaiveDate, MinecraftVersion)>,
    }

    #[test]
    fn dates_and_minecraft_versions_round_trip_through_the_proxy() -> Result<()> {
        for wire in [
            "2022.11.27-1.19.2",
            "2024.02.29-1.20",
            "2024.02.29-1.20.0",
            "2026.11.12-26.1.2",
        ] {
            let proxy: MinecraftDateVersionPairProxy = wire.parse()?;
            let pair = Option::<(NaiveDate, MinecraftVersion)>::try_from(proxy)
                .map_err(|error| eyre::eyre!(error))?;
            let encoded = MinecraftDateVersionPairProxy::try_from(&pair)
                .map_err(|error| eyre::eyre!(error))?;
            assert_eq!(encoded.0.as_deref(), Some(wire));
        }
        Ok(())
    }

    #[test]
    fn reverse_conversion_rejects_dates_outside_the_wire_year_format() -> Result<()> {
        for year in [-1, 10_000] {
            let pair = Some((
                NaiveDate::from_ymd_opt(year, 1, 1).unwrap(),
                MinecraftVersion::parse("1.19.2")?,
            ));
            assert!(MinecraftDateVersionPairProxy::try_from(&pair).is_err());
        }
        assert!(
            MinecraftDateVersionPairProxy::try_from(&None::<(NaiveDate, MinecraftVersion)>)
                .map_err(|error| eyre::eyre!(error))?
                .0
                .is_none()
        );
        Ok(())
    }

    #[test]
    fn conversion_rejects_malformed_dates_and_versions() -> Result<()> {
        for wire in [
            "",
            "2022.11.27",
            "2022.11.27-",
            "2022.11.27-1",
            "2022.11.27-1.19.2-snapshot",
            "2022.11.27-1..19",
            "2022.11.27-1.19.2-extra",
            "2022.11.27-4294967296.1",
            "2022.11.27-1.19.x",
            "2022.1.27-1.19.2",
            "2022.11.7-1.19.2",
            "2022-11-27-1.19.2",
            "2022/11/27-1.19.2",
            "2022.13.27-1.19.2",
            "2022.11.31-1.19.2",
            "2023.02.29-1.19.2",
            " 2022.11.27-1.19.2",
        ] {
            let proxy: MinecraftDateVersionPairProxy = wire.parse()?;
            assert!(
                Option::<(NaiveDate, MinecraftVersion)>::try_from(proxy).is_err(),
                "{wire:?}"
            );
        }
        Ok(())
    }

    #[test]
    fn properties_facet_conversion_produces_a_typed_tuple() -> Result<()> {
        let raw = RawGradlePropertiesDocument::parse("mapping_version=2022.11.27-1.19.2\n")?;
        assert_eq!(
            raw.properties()["mapping_version"].as_str(),
            "2022.11.27-1.19.2"
        );
        let fixture = raw.deserialize::<Fixture>()?;
        let (date, version) = fixture
            .mapping_version
            .ok_or_else(|| eyre::eyre!("expected date/version pair"))?;
        assert_eq!(date, NaiveDate::from_ymd_opt(2022, 11, 27).unwrap());
        assert_eq!(version.as_str(), "1.19.2");
        let raw = RawGradlePropertiesDocument::parse("mapping_version=2023.02.29-1.19.2\n")?;
        assert!(raw.deserialize::<Fixture>().is_err());
        assert!(
            RawGradlePropertiesDocument::parse("")?
                .deserialize::<Fixture>()?
                .mapping_version
                .is_none()
        );
        Ok(())
    }

    #[test]
    fn json_preserves_string_null_and_missing_field_semantics() -> Result<()> {
        let fixture: Fixture = facet_json::from_str(r#"{"mapping_version":"2022.11.27-1.19.2"}"#)?;
        assert_eq!(
            facet_json::to_string(&fixture)?,
            r#"{"mapping_version":"2022.11.27-1.19.2"}"#
        );
        for json in [r#"{}"#, r#"{"mapping_version":null}"#] {
            let fixture: Fixture = facet_json::from_str(json)?;
            assert!(fixture.mapping_version.is_none());
            let encoded = facet_json::to_string(&fixture)?;
            let reparsed: Fixture = facet_json::from_str(&encoded)?;
            assert!(reparsed.mapping_version.is_none());
        }
        assert!(
            facet_json::from_str::<Fixture>(r#"{"mapping_version":"2023.02.29-1.19.2"}"#).is_err()
        );
        Ok(())
    }
}
