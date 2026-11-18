use crate::raw_gradle_properties_document::RawGradlePropertiesDocument;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use std::str::from_utf8;

/// Original source for an explicit, byte-preserving `mod_version` edit.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GradlePropertiesVersionEdit {
    source: String,
}

impl GradlePropertiesVersionEdit {
    /// # Errors
    /// Rejects invalid UTF-8 and unsupported property syntax.
    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        Self::parse(
            from_utf8(bytes)
                .wrap_err("Gradle properties are not UTF-8")?
                .to_owned(),
        )
    }

    /// # Errors
    /// Rejects unsupported property syntax without requiring a complete SFM schema.
    pub fn parse(source: String) -> Result<Self> {
        for line in source.split_inclusive('\n') {
            let _ = RawGradlePropertiesDocument::parse_line(line)?;
        }
        Ok(Self { source })
    }

    /// # Errors
    /// Rejects missing/duplicate/padded version entries or a nonliteral replacement.
    pub fn with_mod_version(&self, version: &str) -> Result<Self> {
        ensure!(
            !version.is_empty()
                && version == version.trim()
                && !version.contains(['\\', '$', '\r', '\n']),
            "mod_version replacement must be a nonempty literal value"
        );
        let mut offset = 0;
        let mut version_span = None;
        for line in self.source.split_inclusive('\n') {
            if let Some((key, _)) = RawGradlePropertiesDocument::parse_line(line)?
                && key.as_str() == "mod_version"
            {
                ensure!(
                    version_span.is_none(),
                    "Gradle properties contain duplicate mod_version entries"
                );
                let start = offset + key.as_str().len() + 1;
                let end = offset + line.trim_end_matches(['\r', '\n']).len();
                let original = &self.source[start..end];
                ensure!(
                    !original.is_empty() && original == original.trim(),
                    "mod_version needs a nonempty unpadded original value"
                );
                version_span = Some(start..end);
            }
            offset += line.len();
        }
        let version_span = version_span
            .ok_or_else(|| eyre::eyre!("Gradle properties need one mod_version entry"))?;
        let mut source = self.source.clone();
        source.replace_range(version_span, version);
        Self::parse(source)
    }

    #[must_use]
    pub fn as_bytes(&self) -> &[u8] {
        self.source.as_bytes()
    }

    #[must_use]
    pub fn into_bytes(self) -> Vec<u8> {
        self.source.into_bytes()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_edits_preserve_comments_unicode_line_endings_and_escapes() -> Result<()> {
        for (before, after) in [
            (
                "# café\r\nmod_version=4.34.0\r\nmod_description=First\\nSecond",
                "# café\r\nmod_version=9.99.99-fixture\r\nmod_description=First\\nSecond",
            ),
            (
                "! 日本語\nmod_version=4.34.0\nother=one\r\nother=two\n",
                "! 日本語\nmod_version=9.99.99-fixture\nother=one\r\nother=two\n",
            ),
            ("mod_version=4.34.0", "mod_version=9.99.99-fixture"),
        ] {
            let document = GradlePropertiesVersionEdit::from_bytes(before.as_bytes())?;
            let updated = document.with_mod_version("9.99.99-fixture")?;
            assert_eq!(updated.into_bytes(), after.as_bytes());
            assert_eq!(document.into_bytes(), before.as_bytes());
        }
        Ok(())
    }

    #[test]
    fn ambiguous_physical_syntax_cannot_hide_properties() {
        assert!(GradlePropertiesVersionEdit::from_bytes(&[0xff]).is_err());
        for text in [
            "mod_version=4.35.0\r",
            "other=x\rmod_version=4.35.0\n",
            "other=continued\\\nmod_version=4.35.0\n",
            "mod_version:4.35.0\n",
            "mod_version 4.35.0\n",
            r"mod\_version=4.35.0",
            r"mod_\u0076ersion=4.35.0",
            " mod_version=4.35.0",
            "mod_version =4.35.0",
        ] {
            assert!(
                GradlePropertiesVersionEdit::parse(text.to_owned()).is_err(),
                "{text:?}"
            );
        }
    }

    #[test]
    fn invalid_replacement_cannot_change_source() -> Result<()> {
        let before = b"mod_version=4.35.0\r\nother=ok\r\n";
        let document = GradlePropertiesVersionEdit::from_bytes(before)?;
        for value in [
            "",
            " 4.35.0",
            "4.35.0 ",
            "4.35.0\nmod_id=other",
            "4.35.0\rmod_id=other",
            "${version}",
        ] {
            assert!(document.with_mod_version(value).is_err());
            assert_eq!(document.as_bytes(), before);
        }
        for value in [
            "other=ok",
            "mod_version=\n",
            "mod_version= padded\n",
            "mod_version=one\nmod_version=two\n",
        ] {
            assert!(
                GradlePropertiesVersionEdit::parse(value.to_owned())?
                    .with_mod_version("4.35.0")
                    .is_err()
            );
        }
        Ok(())
    }
}
