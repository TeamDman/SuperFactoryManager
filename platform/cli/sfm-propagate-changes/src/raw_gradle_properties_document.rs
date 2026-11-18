use crate::gradle_properties_key::GradlePropertiesKey;
use crate::gradle_properties_value::GradlePropertiesValue;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Def;
use facet::Facet;
use facet::Partial;
use std::collections::BTreeMap;
use std::collections::btree_map::Entry;

/// Unique literal property keys and decoded values, before SFM schema parsing.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RawGradlePropertiesDocument {
    properties: BTreeMap<GradlePropertiesKey, GradlePropertiesValue>,
}

impl RawGradlePropertiesDocument {
    /// # Errors
    /// Rejects invalid UTF-8, unsupported property syntax and duplicate keys.
    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        Self::parse(std::str::from_utf8(bytes).wrap_err("Gradle properties are not UTF-8")?)
    }

    /// # Errors
    /// Rejects unsupported property syntax and duplicate keys without overwriting.
    pub fn parse(source: &str) -> Result<Self> {
        let mut properties = BTreeMap::new();
        for line in source.split_inclusive('\n') {
            let Some((key, value)) = Self::parse_line(line)? else {
                continue;
            };
            match properties.entry(key) {
                Entry::Vacant(entry) => {
                    entry.insert(value);
                }
                Entry::Occupied(entry) => {
                    eyre::bail!("duplicate Gradle property {}", entry.key().as_str());
                }
            }
        }
        Ok(Self { properties })
    }

    pub(crate) fn parse_line(
        line: &str,
    ) -> Result<Option<(GradlePropertiesKey, GradlePropertiesValue)>> {
        let body = line
            .strip_suffix('\n')
            .map_or(line, |body| body.strip_suffix('\r').unwrap_or(body));
        ensure!(
            !body.contains(['\r', '\n']),
            "Gradle properties cannot contain bare CR separators or embedded newlines"
        );
        let trimmed = body.trim_start();
        if trimmed.is_empty() || trimmed.starts_with(['#', '!']) {
            return Ok(None);
        }
        let (key, value) = body
            .split_once('=')
            .ok_or_else(|| eyre::eyre!("Gradle properties need literal key=value lines"))?;
        Ok(Some((
            GradlePropertiesKey::parse(key)?,
            GradlePropertiesValue::parse(value)?,
        )))
    }

    #[must_use]
    pub fn properties(&self) -> &BTreeMap<GradlePropertiesKey, GradlePropertiesValue> {
        &self.properties
    }

    /// Populate a named schema using Facet fields, proxies and scalar parsers.
    ///
    /// # Errors
    /// Rejects unknown/aliased duplicate fields, invalid typed values and missing fields.
    pub fn deserialize<T: Facet<'static>>(&self) -> Result<T> {
        let mut partial = Partial::alloc::<T>()?;
        for (key, value) in &self.properties {
            let index = partial
                .field_index(key.as_str())
                .ok_or_else(|| eyre::eyre!("unknown Gradle property {}", key.as_str()))?;
            ensure!(
                !partial.is_field_set(index)?,
                "duplicate Gradle property field {}",
                key.as_str()
            );
            partial = partial.begin_nth_field(index)?;
            let proxied = partial
                .parent_field()
                .is_some_and(|field| field.proxy().is_some());
            if proxied {
                partial = partial.begin_custom_deserialization()?;
            }
            let optional = !proxied && matches!(partial.shape().def, Def::Option(_));
            if optional {
                partial = partial.begin_some()?;
            }
            partial = partial
                .parse_from_str(value.as_str())
                .wrap_err_with(|| format!("cannot parse Gradle property {}", key.as_str()))?;
            if optional {
                partial = partial.end()?;
            }
            if proxied {
                partial = partial
                    .end()
                    .wrap_err_with(|| format!("cannot convert Gradle property {}", key.as_str()))?;
            }
            partial = partial.end()?;
        }
        Ok(partial
            .build()
            .wrap_err("Gradle properties are missing required fields")?
            .materialize::<T>()?)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn raw_map_keeps_literal_dotted_keys_and_decodes_values() -> Result<()> {
        let raw = RawGradlePropertiesDocument::parse(
            "# settings\r\norg.gradle.daemon=false\r\nmod_description=First\\nSecond\nextra.key=value",
        )?;
        assert_eq!(raw.properties().len(), 3);
        assert_eq!(raw.properties()["org.gradle.daemon"].as_str(), "false");
        assert_eq!(
            raw.properties()["mod_description"].as_str(),
            "First\nSecond"
        );
        assert_eq!(raw.properties()["extra.key"].as_str(), "value");
        assert!(!raw.properties().contains_key("org_gradle_daemon"));
        Ok(())
    }

    #[test]
    fn domain_values_stay_strings_until_facet_conversion() -> Result<()> {
        use crate::gradle_dot_properties_file_content::GradleDotPropertiesFileContent;
        use crate::gradle_dot_properties_file_content::test_content;

        let valid = test_content("mod_id=sfm\nmod_version=4.35.0\nminecraft_version=1.21\n");
        for (text, key, value) in [
            (
                valid.replace(
                    "minecraft_version_range=[1,)",
                    "minecraft_version_range=not-a-range",
                ),
                "minecraft_version_range",
                "not-a-range",
            ),
            (
                valid.replace("org.gradle.daemon=false", "org.gradle.daemon=not-a-bool"),
                "org.gradle.daemon",
                "not-a-bool",
            ),
            (
                format!("{valid}pack_format_number=not-a-number\n"),
                "pack_format_number",
                "not-a-number",
            ),
        ] {
            let raw = RawGradlePropertiesDocument::parse(&text)?;
            assert_eq!(raw.properties()[key].as_str(), value);
            assert!(raw.deserialize::<GradleDotPropertiesFileContent>().is_err());
        }
        Ok(())
    }

    #[test]
    fn renamed_fields_accept_aliases_but_cannot_be_populated_twice() -> Result<()> {
        #[derive(Facet)]
        struct Setting {
            #[facet(rename = "org.gradle.daemon", alias = "daemon")]
            enabled: bool,
        }
        for key in ["org.gradle.daemon", "daemon"] {
            let raw = RawGradlePropertiesDocument::parse(&format!("{key}=true"))?;
            assert!(raw.deserialize::<Setting>()?.enabled);
        }
        let raw = RawGradlePropertiesDocument::parse("org.gradle.daemon=false\ndaemon=true")?;
        let error = raw
            .deserialize::<Setting>()
            .err()
            .expect("aliased duplicate");
        assert!(
            error
                .to_string()
                .contains("duplicate Gradle property field"),
            "{error}"
        );
        Ok(())
    }

    #[test]
    fn raw_map_rejects_every_duplicate_even_when_values_match() {
        for text in [
            "mod_id=sfm\nmod_id=sfm\n",
            "org.gradle.daemon=false\norg.gradle.daemon=true\n",
            "other=one\nother=two\n",
        ] {
            let error = RawGradlePropertiesDocument::parse(text).unwrap_err();
            assert!(
                error.to_string().contains("duplicate Gradle property"),
                "{error}"
            );
        }
    }
}
