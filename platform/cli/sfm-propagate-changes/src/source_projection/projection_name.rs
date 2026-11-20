use eyre::Result;
use eyre::ensure;
use facet::Facet;

/// An exact, portable projection name from `projections.json`.
#[derive(Clone, Debug, Facet)]
#[facet(opaque, proxy = String)]
pub struct ProjectionName(String);

impl ProjectionName {
    /// # Errors
    /// Rejects nonportable projection names.
    pub fn parse(value: &str) -> Result<Self> {
        Self::try_from(value.to_owned())
    }

    /// Accept only a portable relative path, irrespective of the host OS.
    /// ASCII avoids locale-dependent case and Unicode-normalization aliases.
    ///
    /// # Errors
    ///
    /// Rejects absolute/device/traversal paths, invalid Windows filename characters,
    /// backslashes, controls/non-ASCII, empty segments, and trailing dots/spaces.
    pub fn validate(key: &str) -> Result<()> {
        ensure!(
            !key.is_empty()
                && key.is_ascii()
                && !key.bytes().any(|byte| byte.is_ascii_control()
                    || matches!(byte, b'\\' | b'<' | b'>' | b':' | b'"' | b'|' | b'?' | b'*'))
                && key.split('/').all(|component| {
                    !component.is_empty()
                        && component != "."
                        && component != ".."
                        && component.len() <= 255
                        && !component.ends_with('.')
                        && !component.ends_with(' ')
                        && !is_windows_device_name(component)
                }),
            "projection key `{key}` must be a portable, exact, ASCII relative path without traversal, device names, backslashes or trailing dots/spaces"
        );
        Ok(())
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl TryFrom<String> for ProjectionName {
    type Error = eyre::Report;

    fn try_from(value: String) -> Result<Self> {
        Self::validate(&value)?;
        Ok(Self(value))
    }
}

impl From<&ProjectionName> for String {
    fn from(name: &ProjectionName) -> Self {
        name.0.clone()
    }
}

fn is_windows_device_name(component: &str) -> bool {
    let stem = component
        .split('.')
        .next()
        .unwrap_or_default()
        .trim_end_matches(' ');
    ["CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$", "CLOCK$"]
        .iter()
        .any(|device| stem.eq_ignore_ascii_case(device))
        || (stem.len() == 4
            && (stem.as_bytes()[..3].eq_ignore_ascii_case(b"COM")
                || stem.as_bytes()[..3].eq_ignore_ascii_case(b"LPT"))
            && matches!(stem.as_bytes()[3], b'1'..=b'9'))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn path_validation_is_host_independent() {
        for invalid in [
            "",
            "/absolute",
            "//server/share",
            "C:/drive",
            "C:drive",
            "../escape",
            "a/../b",
            "a/./b",
            "a//b",
            "a/",
            "a\\b",
            "\\\\server\\share",
            "a\0b",
            "a\nb",
            "a\tb",
            "a/ends.",
            "a/ends ",
            "a/<b",
            "a/quo\"te",
            "a/pipe|",
            "a/quest?",
            "a/star*",
            "a/col:on",
            "a/caf\u{e9}",
        ] {
            assert!(
                ProjectionName::validate(invalid).is_err(),
                "accepted {invalid:?}"
            );
        }
        for valid in [
            "a",
            "sfm-4.34.0/mc-1.19.2",
            "custom/one more/alpha_beta",
            ".local/example",
            "one/two/three",
        ] {
            ProjectionName::validate(valid).unwrap();
        }
        assert!(ProjectionName::validate(&"a".repeat(256)).is_err());
    }

    #[test]
    fn allocation_free_device_check_matches_original_case_and_suffix_rules() {
        for base in [
            "CON",
            "PRN",
            "AUX",
            "NUL",
            "CONIN$",
            "CONOUT$",
            "CLOCK$",
            "COM1",
            "COM9",
            "COM0",
            "COM10",
            "LPT1",
            "LPT9",
            "LPT0",
            "console",
            "auxiliary",
            "a",
            "",
        ] {
            for mask in 0..(1_usize << base.len()) {
                let spelling = base
                    .bytes()
                    .enumerate()
                    .map(|(index, byte)| {
                        if mask & (1 << index) == 0 {
                            byte.to_ascii_lowercase()
                        } else {
                            byte.to_ascii_uppercase()
                        }
                    })
                    .map(char::from)
                    .collect::<String>();
                for suffix in ["", ".txt", " .txt", "  .java", "x", "0", "$", "/other"] {
                    let candidate = format!("{spelling}{suffix}");
                    let upper = candidate
                        .split('.')
                        .next()
                        .unwrap_or_default()
                        .trim_end_matches(' ')
                        .to_ascii_uppercase();
                    let old = matches!(
                        upper.as_str(),
                        "CON" | "PRN" | "AUX" | "NUL" | "CONIN$" | "CONOUT$" | "CLOCK$"
                    ) || (upper.len() == 4
                        && (upper.starts_with("COM") || upper.starts_with("LPT"))
                        && matches!(upper.as_bytes()[3], b'1'..=b'9'));
                    assert_eq!(is_windows_device_name(&candidate), old, "{candidate}");
                }
            }
        }
    }

    #[test]
    fn rejects_windows_device_names_with_extensions_and_case_variants() {
        for device in [
            "CON",
            "con.txt",
            "PRN",
            "Aux.java",
            "nul",
            "COM1",
            "com9.gradle",
            "LPT1",
            "lpt9",
            "CON .txt",
            "CONIN$",
            "conout$",
            "CLOCK$",
        ] {
            assert!(
                ProjectionName::validate(&format!("safe/{device}/project")).is_err(),
                "accepted {device}"
            );
        }
        for ordinary in ["COM0", "COM10", "LPT0", "console", "auxiliary"] {
            ProjectionName::validate(&format!("safe/{ordinary}/project")).unwrap();
        }
    }
}
