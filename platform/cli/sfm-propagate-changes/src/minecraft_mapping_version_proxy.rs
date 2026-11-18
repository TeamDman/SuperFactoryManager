use crate::branch_targets::MinecraftVersion;
use crate::minecraft_date_version_pair_proxy::MinecraftDateVersionPairProxy;
use crate::minecraft_mapping_version::MinecraftMappingVersion;
use facet::Facet;
use std::convert::Infallible;
use std::str::FromStr;

/// Optional string representation of the project's supported mapping versions.
#[derive(Clone, Debug, Facet)]
#[facet(transparent, traits(FromStr))]
pub struct MinecraftMappingVersionProxy(Option<String>);

impl MinecraftMappingVersionProxy {
    fn parse_mapping(value: &str) -> Result<MinecraftMappingVersion, String> {
        let mut parts = value.split('-');
        let first = parts.next().unwrap_or_default();
        match (parts.next(), parts.next(), parts.next()) {
            (None, None, None) => MinecraftVersion::parse(first)
                .map(MinecraftMappingVersion::Official)
                .map_err(|error| format!("invalid official mapping version: {error}")),
            (Some(second), None, None) => {
                let pair =
                    MinecraftDateVersionPairProxy::parse_pair(value).or_else(|first_error| {
                        MinecraftDateVersionPairProxy::parse_pair(&format!("{second}-{first}"))
                            .map_err(|second_error| {
                                format!(
                                    "invalid date-version pair: {first_error}; invalid version-date pair: {second_error}"
                                )
                            })
                    })?;
                Ok(MinecraftMappingVersion::Parchment(pair))
            }
            (Some(date), Some(target), None) => {
                let mappings =
                    MinecraftDateVersionPairProxy::parse_pair(&format!("{date}-{first}"))?;
                let target = MinecraftVersion::parse(target).map_err(|error| {
                    format!("invalid mapping target Minecraft version: {error}")
                })?;
                Ok(MinecraftMappingVersion::CrossVersionParchment { mappings, target })
            }
            _ => Err("mapping version has too many components".to_owned()),
        }
    }

    fn format_mapping(value: &MinecraftMappingVersion) -> Result<String, String> {
        match value {
            MinecraftMappingVersion::Official(version) => Ok(version.to_string()),
            MinecraftMappingVersion::Parchment(mappings) => {
                MinecraftDateVersionPairProxy::format_pair(mappings)
            }
            MinecraftMappingVersion::CrossVersionParchment { mappings, target } => {
                let date_source = MinecraftDateVersionPairProxy::format_pair(mappings)?;
                let (date, source) = date_source
                    .split_once('-')
                    .ok_or_else(|| "formatted mapping pair has no separator".to_owned())?;
                Ok(format!("{source}-{date}-{target}"))
            }
        }
    }
}

impl FromStr for MinecraftMappingVersionProxy {
    type Err = Infallible;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        Ok(Self(Some(value.to_owned())))
    }
}

impl TryFrom<MinecraftMappingVersionProxy> for Option<MinecraftMappingVersion> {
    type Error = String;

    fn try_from(value: MinecraftMappingVersionProxy) -> Result<Self, Self::Error> {
        value
            .0
            .as_deref()
            .map(MinecraftMappingVersionProxy::parse_mapping)
            .transpose()
    }
}

impl TryFrom<&Option<MinecraftMappingVersion>> for MinecraftMappingVersionProxy {
    type Error = String;

    fn try_from(value: &Option<MinecraftMappingVersion>) -> Result<Self, Self::Error> {
        value
            .as_ref()
            .map(Self::format_mapping)
            .transpose()
            .map(Self)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::raw_gradle_properties_document::RawGradlePropertiesDocument;
    use chrono::NaiveDate;
    use eyre::Result;

    #[derive(Debug, Facet)]
    struct Fixture {
        #[facet(default, opaque, proxy = MinecraftMappingVersionProxy)]
        mapping_version: Option<MinecraftMappingVersion>,
    }

    #[test]
    fn authored_forms_keep_typed_data_and_round_trip() -> Result<()> {
        let cases = [
            (
                "1.20.1",
                MinecraftMappingVersion::Official(MinecraftVersion::parse("1.20.1")?),
            ),
            (
                "2022.11.27-1.19.2",
                MinecraftMappingVersion::Parchment((
                    NaiveDate::from_ymd_opt(2022, 11, 27).unwrap(),
                    MinecraftVersion::parse("1.19.2")?,
                )),
            ),
            (
                "1.19.3-2023.03.12-1.19.4",
                MinecraftMappingVersion::CrossVersionParchment {
                    mappings: (
                        NaiveDate::from_ymd_opt(2023, 3, 12).unwrap(),
                        MinecraftVersion::parse("1.19.3")?,
                    ),
                    target: MinecraftVersion::parse("1.19.4")?,
                },
            ),
        ];
        for (wire, expected) in cases {
            let proxy: MinecraftMappingVersionProxy = wire.parse()?;
            let value = Option::<MinecraftMappingVersion>::try_from(proxy)
                .map_err(|error| eyre::eyre!(error))?;
            assert_eq!(value, Some(expected));
            let encoded = MinecraftMappingVersionProxy::try_from(&value)
                .map_err(|error| eyre::eyre!(error))?;
            assert_eq!(encoded.0.as_deref(), Some(wire));
        }
        Ok(())
    }

    #[test]
    fn version_date_spelling_keeps_the_version_and_serializes_date_first() -> Result<()> {
        let proxy: MinecraftMappingVersionProxy = "1.19.2-2022.11.27".parse()?;
        let value = Option::<MinecraftMappingVersion>::try_from(proxy)
            .map_err(|error| eyre::eyre!(error))?;
        assert_eq!(
            value,
            Some(MinecraftMappingVersion::Parchment((
                NaiveDate::from_ymd_opt(2022, 11, 27).unwrap(),
                MinecraftVersion::parse("1.19.2")?,
            )))
        );
        let encoded =
            MinecraftMappingVersionProxy::try_from(&value).map_err(|error| eyre::eyre!(error))?;
        assert_eq!(encoded.0.as_deref(), Some("2022.11.27-1.19.2"));
        Ok(())
    }

    #[test]
    fn raw_strings_survive_but_invalid_mapping_values_fail_typed_conversion() -> Result<()> {
        for wire in [
            "",
            "1",
            "2022.11.27-",
            "2023.02.29-1.19.2",
            "1.19.3-2023.02.29-1.19.4",
            "source-2023.03.12-1.19.4",
            "1.19.3-2023.03.12-target",
            "1.19.3-2023.03.12-1.19.4-extra",
            "2023.03.12-1.19.x",
        ] {
            let raw = RawGradlePropertiesDocument::parse(&format!("mapping_version={wire}\n"))?;
            assert_eq!(raw.properties()["mapping_version"].as_str(), wire);
            assert!(raw.deserialize::<Fixture>().is_err(), "{wire:?}");
        }
        Ok(())
    }

    #[test]
    fn facet_proxy_preserves_cross_version_values_and_optional_fields() -> Result<()> {
        let raw = RawGradlePropertiesDocument::parse("mapping_version=1.19.3-2023.03.12-1.19.4\n")?;
        let fixture = raw.deserialize::<Fixture>()?;
        let json = facet_json::to_string(&fixture)?;
        assert_eq!(json, r#"{"mapping_version":"1.19.3-2023.03.12-1.19.4"}"#);
        let reparsed: Fixture = facet_json::from_str(&json)?;
        assert_eq!(reparsed.mapping_version, fixture.mapping_version);
        assert!(
            RawGradlePropertiesDocument::parse("")?
                .deserialize::<Fixture>()?
                .mapping_version
                .is_none()
        );
        for json in [r#"{}"#, r#"{"mapping_version":null}"#] {
            let fixture: Fixture = facet_json::from_str(json)?;
            assert!(fixture.mapping_version.is_none());
            let encoded = facet_json::to_string(&fixture)?;
            let reparsed: Fixture = facet_json::from_str(&encoded)?;
            assert!(reparsed.mapping_version.is_none());
        }
        let proxy = MinecraftMappingVersionProxy::try_from(&None::<MinecraftMappingVersion>)
            .map_err(|error| eyre::eyre!(error))?;
        assert!(proxy.0.is_none());
        Ok(())
    }
}
