use crate::github_username::GitHubUsername;
use crate::mod_authors::ModAuthors;
use facet::Facet;
use std::convert::Infallible;
use std::str::FromStr;

/// A comma-separated wire representation of the typed mod-author list.
///
/// [Loader authors](https://docs.neoforged.net/docs/gettingstarted/modfiles/)
/// are display text. SFM chooses the narrower username list whose entries follow
/// [GitHub's username grammar](https://github.com/signup). Separator whitespace is
/// ignored; output uses bare commas. Empty entries and quoted fields are rejected.
/// GitHub usernames cannot contain commas or quotes, so general quoted CSV would
/// add no author identities to this contract. The core list stays [`ModAuthors`].
#[derive(Clone, Debug, Facet)]
#[facet(transparent, traits(FromStr))]
pub struct ModAuthorsCsvProxy(String);

impl FromStr for ModAuthorsCsvProxy {
    type Err = Infallible;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        Ok(Self(value.to_owned()))
    }
}

impl TryFrom<ModAuthorsCsvProxy> for ModAuthors {
    type Error = String;

    fn try_from(value: ModAuthorsCsvProxy) -> Result<Self, Self::Error> {
        let authors = value
            .0
            .split(',')
            .map(|entry| {
                GitHubUsername::parse(entry.trim())
                    .map_err(|error| format!("invalid mod author: {error}"))
            })
            .collect::<Result<Vec<_>, _>>()?;
        Self::new(authors).map_err(|error| error.to_string())
    }
}

impl From<&ModAuthors> for ModAuthorsCsvProxy {
    fn from(value: &ModAuthors) -> Self {
        Self(
            value
                .as_slice()
                .iter()
                .map(GitHubUsername::as_str)
                .collect::<Vec<_>>()
                .join(","),
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::raw_gradle_properties_document::RawGradlePropertiesDocument;
    use eyre::Result;

    #[derive(Debug, Facet)]
    struct Fixture {
        #[facet(opaque, proxy = ModAuthorsCsvProxy)]
        mod_authors: ModAuthors,
    }

    #[test]
    fn comma_codec_keeps_typed_names_and_serializes_in_credit_order() -> Result<()> {
        let proxy: ModAuthorsCsvProxy = "TeamDman, another-author".parse()?;
        let authors = ModAuthors::try_from(proxy).map_err(|error| eyre::eyre!(error))?;
        assert_eq!(authors.as_slice()[0].as_str(), "TeamDman");
        assert_eq!(authors.as_slice()[1].as_str(), "another-author");
        assert_eq!(
            ModAuthorsCsvProxy::from(&authors).0,
            "TeamDman,another-author"
        );
        Ok(())
    }

    #[test]
    fn rejects_empty_fields_and_nonusername_display_text() -> Result<()> {
        for wire in [
            "",
            " ",
            ",",
            "TeamDman,",
            ",TeamDman",
            "TeamDman,,other",
            "Fixture author",
            "\"TeamDman\"",
        ] {
            let proxy: ModAuthorsCsvProxy = wire.parse()?;
            assert!(ModAuthors::try_from(proxy).is_err(), "{wire:?}");
        }
        Ok(())
    }

    #[test]
    fn properties_and_json_use_the_proxy_without_erasing_the_domain_list() -> Result<()> {
        let wire = "mod_authors=TeamDman, another-author\n";
        let raw = RawGradlePropertiesDocument::parse(wire)?;
        assert_eq!(
            raw.properties()["mod_authors"].as_str(),
            "TeamDman, another-author"
        );
        let fixture = raw.deserialize::<Fixture>()?;
        let json = facet_json::to_string(&fixture)?;
        assert_eq!(json, r#"{"mod_authors":"TeamDman,another-author"}"#);
        let reparsed: Fixture = facet_json::from_str(&json)?;
        assert_eq!(reparsed.mod_authors, fixture.mod_authors);
        assert!(
            RawGradlePropertiesDocument::parse("")?
                .deserialize::<Fixture>()
                .is_err()
        );
        assert!(facet_json::from_str::<Fixture>(r#"{"mod_authors":"Fixture author"}"#).is_err());
        Ok(())
    }
}
