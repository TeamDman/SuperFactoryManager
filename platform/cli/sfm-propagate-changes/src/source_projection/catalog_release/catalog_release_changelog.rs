use super::CatalogReleaseVersion;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;

/// Exact release section; its digest is distinct from the whole selected file.
#[derive(Debug, Facet)]
pub struct CatalogReleaseChangelog {
    content: String,
    sha256: String,
}

impl CatalogReleaseChangelog {
    pub(super) fn parse(bytes: &[u8], version: &CatalogReleaseVersion) -> Result<Self> {
        let text =
            std::str::from_utf8(bytes).wrap_err("selected release changelog is not UTF-8")?;
        ensure!(
            text.lines().all(|line| !line.contains('\r')),
            "release changelog must use LF or CRLF line endings"
        );
        let heading = format!("---- {} ----", version.as_str());
        let mut offset = 0;
        let mut start = None;
        let mut body_start = 0;
        let mut end = text.len();
        for line in text.split_inclusive('\n') {
            if line.trim() == heading {
                ensure!(
                    start.is_none(),
                    "selected changelog repeats release heading {heading}"
                );
                start = Some(offset);
                body_start = offset + line.len();
            } else if start.is_some() && end == text.len() && Self::is_version_heading(line) {
                end = offset;
            }
            offset += line.len();
        }
        let start = start.ok_or_else(|| {
            eyre::eyre!("selected changelog needs exact release heading {heading}")
        })?;
        ensure!(
            body_start <= end && !text[body_start..end].trim().is_empty(),
            "selected release changelog section is empty"
        );
        let content = text[start..end].to_owned();
        Ok(Self {
            sha256: sha256(content.as_bytes()),
            content,
        })
    }

    fn is_version_heading(line: &str) -> bool {
        line.trim()
            .strip_prefix("---- ")
            .and_then(|body| body.strip_suffix(" ----"))
            .and_then(|body| body.split_whitespace().next())
            .is_some_and(|version| {
                let release = version.split(['-', '+']).next().unwrap_or_default();
                CatalogReleaseVersion::parse(release).is_ok()
            })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn exact_release_heading_and_nonempty_section_are_required() {
        let version = CatalogReleaseVersion::parse("4.35.0").unwrap();
        for content in [
            "---- 4.35.0 PRE ----\n-- preview\n",
            "---- 4.35.0 ----\n\n---- 4.34.0 ----\n-- old\n",
            "---- 4.35.0 ----\n-- one\n---- 4.35.0 ----\n-- two\n",
        ] {
            assert!(CatalogReleaseChangelog::parse(content.as_bytes(), &version).is_err());
        }
        let content = "NAME \"Changelog\"\r\n---- 4.35.0 ----\r\n-- changed\r\n---- Thank you @author ----\r\n-- retained\r\n---- 4.34.0 PRE ----\r\n-- old\r\n";
        let section = CatalogReleaseChangelog::parse(content.as_bytes(), &version).unwrap();
        assert!(section.content.starts_with("---- 4.35.0 ----\r\n"));
        assert!(section.content.contains("Thank you"));
        assert!(!section.content.contains("4.34.0"));
        assert_eq!(section.sha256, sha256(section.content.as_bytes()));
        for next in ["4.34.0-rc.1", "4.34.0+build", "4.34.0-rc.1+build"] {
            let source = format!("---- 4.35.0 ----\n-- selected\n---- {next} ----\n-- old body\n");
            let section = CatalogReleaseChangelog::parse(source.as_bytes(), &version).unwrap();
            assert!(!section.content.contains("old body"));
            assert!(!section.content.contains(next));
        }
        assert!(
            CatalogReleaseChangelog::parse(
                b"---- 4.35.0 ----\n-- selected\r---- 4.34.0 ----",
                &version
            )
            .is_err()
        );
    }
}
