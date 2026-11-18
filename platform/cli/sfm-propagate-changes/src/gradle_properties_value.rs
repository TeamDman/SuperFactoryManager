use eyre::Result;
use eyre::ensure;
use std::str::Chars;
/// A decoded property value, including any escaped whitespace or newlines.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GradlePropertiesValue(String);

impl GradlePropertiesValue {
    /// # Errors
    /// Rejects physical line breaks, continued lines and malformed value escapes.
    pub fn parse(encoded: &str) -> Result<Self> {
        ensure!(
            !encoded.contains(['\r', '\n']),
            "Gradle property values need one physical line"
        );
        let value = encoded.trim_start_matches([' ', '\t', '\u{000c}']);
        let mut output = String::with_capacity(value.len());
        let mut chars = value.chars();
        while let Some(character) = chars.next() {
            if character != '\\' {
                output.push(character);
                continue;
            }
            let escaped = chars.next().ok_or_else(|| {
                eyre::eyre!(
                    "Gradle properties cannot contain continued lines or unterminated value escapes"
                )
            })?;
            output.push(match escaped {
                'n' => '\n',
                'r' => '\r',
                't' => '\t',
                'f' => '\u{000c}',
                'u' => Self::decode_unicode(&mut chars)?,
                ordinary => ordinary,
            });
        }
        Ok(Self(output))
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }

    fn decode_unicode(chars: &mut Chars<'_>) -> Result<char> {
        let first = Self::unicode_unit(chars)?;
        let mut units = vec![first];
        if (0xd800..=0xdbff).contains(&first) {
            ensure!(
                chars.next() == Some('\\') && chars.next() == Some('u'),
                "Gradle Unicode high surrogate needs a low-surrogate escape"
            );
            units.push(Self::unicode_unit(chars)?);
        }
        let decoded = char::decode_utf16(units).collect::<Result<Vec<_>, _>>()?;
        ensure!(
            decoded.len() == 1,
            "Gradle Unicode escape must encode one character"
        );
        Ok(decoded[0])
    }

    fn unicode_unit(chars: &mut Chars<'_>) -> Result<u16> {
        let mut unit = 0;
        for _ in 0..4 {
            let digit = chars
                .next()
                .and_then(|character| character.to_digit(16))
                .ok_or_else(|| {
                    eyre::eyre!("Gradle Unicode escape needs four hexadecimal digits")
                })?;
            unit = (unit << 4) | u16::try_from(digit)?;
        }
        Ok(unit)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn values_decode_properties_escapes_and_unicode() -> Result<()> {
        let property =
            GradlePropertiesValue::parse(r"First\nSecond\tline\r\f\u0021\uD83D\uDE00\\")?;
        assert_eq!(property.as_str(), "First\nSecond\tline\r\u{000c}!😀\\");
        Ok(())
    }

    #[test]
    fn delimiter_whitespace_is_skipped_but_escaped_space_is_a_value() -> Result<()> {
        assert_eq!(
            GradlePropertiesValue::parse(" \t\u{000c}SFM ")?.as_str(),
            "SFM "
        );
        assert_eq!(GradlePropertiesValue::parse(r"\ SFM")?.as_str(), " SFM");
        Ok(())
    }

    #[test]
    fn invalid_or_unfinished_unicode_escapes_are_errors() {
        for text in [
            r"\u12",
            r"\uXXXX",
            r"\uD800",
            r"\uDC00",
            r"\uD800\u0041",
            "end\\",
            "first\nsecond",
            "first\rsecond",
        ] {
            assert!(GradlePropertiesValue::parse(text).is_err(), "{text}");
        }
    }
}
