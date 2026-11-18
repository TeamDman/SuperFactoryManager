use crate::semver_version_bound::SemverVersionBound;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::fmt;
use std::str::FromStr;

/// One bracketed project version interval or exact `[version]`.
///
/// Endpoints retain literal version tokens, including qualifiers and partial or
/// four-component project versions. This type establishes syntax, not ordering,
/// nonemptiness or artifact admission.
/// Unions are unsupported; `(,)` explicitly represents two unbounded ends.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(opaque, traits(FromStr))]
pub struct SemverVersionRange {
    lower: SemverVersionBound,
    upper: SemverVersionBound,
}

impl SemverVersionRange {
    /// Parse one complete interval, allowing whitespace around its endpoints.
    ///
    /// # Errors
    /// Rejects malformed delimiters, unions, trailing text, empty exact versions,
    /// closed infinite ends and whitespace/control/delimiters within a version.
    pub fn parse(value: &str) -> Result<Self> {
        ensure!(value.len() >= 3, "version range needs complete delimiters");
        let opening = value.as_bytes()[0];
        let closing = value.as_bytes()[value.len() - 1];
        ensure!(
            matches!(opening, b'[' | b'(') && matches!(closing, b']' | b')'),
            "version range must be one complete interval or exact [version]"
        );
        let inner = &value[1..value.len() - 1];
        if let Some((lower, upper)) = inner.split_once(',') {
            ensure!(
                !upper.contains(','),
                "version range unions and multiple interval separators are unsupported"
            );
            return Ok(Self {
                lower: Self::parse_bound(lower, opening == b'[')?,
                upper: Self::parse_bound(upper, closing == b']')?,
            });
        }
        ensure!(
            opening == b'[' && closing == b']',
            "a single-version version range must use [version]"
        );
        let version = Self::parse_endpoint(inner)?;
        Ok(Self {
            lower: SemverVersionBound::Included(version.clone()),
            upper: SemverVersionBound::Included(version),
        })
    }

    #[must_use]
    pub fn lower(&self) -> &SemverVersionBound {
        &self.lower
    }

    #[must_use]
    pub fn upper(&self) -> &SemverVersionBound {
        &self.upper
    }

    fn parse_bound(value: &str, inclusive: bool) -> Result<SemverVersionBound> {
        if value.trim().is_empty() {
            ensure!(!inclusive, "an infinite version endpoint must be open");
            return Ok(SemverVersionBound::Unbounded);
        }
        let version = Self::parse_endpoint(value)?;
        Ok(if inclusive {
            SemverVersionBound::Included(version)
        } else {
            SemverVersionBound::Excluded(version)
        })
    }

    fn parse_endpoint(value: &str) -> Result<String> {
        let version = value.trim();
        ensure!(
            !version.is_empty()
                && !version.chars().any(|character| {
                    character.is_whitespace()
                        || character.is_control()
                        || matches!(character, '[' | ']' | '(' | ')' | ',')
                }),
            "version endpoint must be one nonempty version token"
        );
        Ok(version.to_owned())
    }
}

impl FromStr for SemverVersionRange {
    type Err = eyre::Report;

    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}

/// Canonical syntax only; source-document whitespace is not retained here.
impl fmt::Display for SemverVersionRange {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        if let (SemverVersionBound::Included(lower), SemverVersionBound::Included(upper)) =
            (&self.lower, &self.upper)
            && lower == upper
        {
            return write!(f, "[{lower}]");
        }
        let (opening, lower) = match &self.lower {
            SemverVersionBound::Included(value) => ('[', value.as_str()),
            SemverVersionBound::Excluded(value) => ('(', value.as_str()),
            SemverVersionBound::Unbounded => ('(', ""),
        };
        let (upper, closing) = match &self.upper {
            SemverVersionBound::Included(value) => (value.as_str(), ']'),
            SemverVersionBound::Excluded(value) => (value.as_str(), ')'),
            SemverVersionBound::Unbounded => ("", ')'),
        };
        write!(f, "{opening}{lower},{upper}{closing}")
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use SemverVersionBound::Excluded;
    use SemverVersionBound::Included;
    use SemverVersionBound::Unbounded;

    #[test]
    fn exact_version_is_two_equal_included_ends() -> Result<()> {
        let range: SemverVersionRange = "[1.21.1]".parse()?;
        assert_eq!(range.lower(), &Included("1.21.1".to_owned()));
        assert_eq!(range.upper(), &Included("1.21.1".to_owned()));
        assert_eq!(range.to_string(), "[1.21.1]");
        Ok(())
    }

    #[test]
    fn interval_delimiters_determine_each_endpoint_inclusion() -> Result<()> {
        for (text, lower, upper) in [
            ("[1,2]", Included("1".to_owned()), Included("2".to_owned())),
            ("[1,2)", Included("1".to_owned()), Excluded("2".to_owned())),
            ("(1,2]", Excluded("1".to_owned()), Included("2".to_owned())),
            ("(1,2)", Excluded("1".to_owned()), Excluded("2".to_owned())),
        ] {
            let range = SemverVersionRange::parse(text)?;
            assert_eq!(range.lower(), &lower);
            assert_eq!(range.upper(), &upper);
        }
        Ok(())
    }

    #[test]
    fn missing_endpoints_are_explicitly_unbounded() -> Result<()> {
        for (text, lower, upper) in [
            ("[37,)", Included("37".to_owned()), Unbounded),
            ("(37,)", Excluded("37".to_owned()), Unbounded),
            ("(,4]", Unbounded, Included("4".to_owned())),
            ("(,4)", Unbounded, Excluded("4".to_owned())),
            ("(,)", Unbounded, Unbounded),
        ] {
            let range = SemverVersionRange::parse(text)?;
            assert_eq!(range.lower(), &lower);
            assert_eq!(range.upper(), &upper);
        }
        Ok(())
    }

    #[test]
    fn qualifiers_and_four_numeric_components_remain_literal_tokens() -> Result<()> {
        let range = SemverVersionRange::parse("[26.1.2.29-beta, 26.1.2.72)")?;
        assert_eq!(range.lower(), &Included("26.1.2.29-beta".to_owned()));
        assert_eq!(range.upper(), &Excluded("26.1.2.72".to_owned()));
        assert_eq!(range.to_string(), "[26.1.2.29-beta,26.1.2.72)");
        let beta = SemverVersionRange::parse("[21.0.0-beta,)")?;
        assert_eq!(beta.lower(), &Included("21.0.0-beta".to_owned()));
        // Parsing deliberately does not decide numeric or qualifier ordering.
        assert!(SemverVersionRange::parse("[2,1)").is_ok());
        Ok(())
    }

    #[test]
    fn incomplete_ranges_unions_and_trailing_text_are_rejected() {
        for text in [
            "",
            "1.21.1",
            "[1",
            "1]",
            "[1,2",
            "[1)",
            "(1]",
            "(1)",
            "[]",
            "[ ]",
            "[,2)",
            "[1,]",
            "[,]",
            "[1,2,3]",
            "[1],[2]",
            "[1,2),(3,4]",
            "[1,2)trailing",
            "[1,2)trailing)",
            "[1,2)\n",
            " [1,2)",
        ] {
            assert!(SemverVersionRange::parse(text).is_err(), "{text:?}");
        }
    }

    #[test]
    fn endpoint_tokens_cannot_hide_whitespace_or_range_delimiters() {
        for text in [
            "[21.0.0-beta preview,)",
            "[21.0.0-beta\0,)",
            "[21.0.0-(beta),)",
            "[[21.0.0-beta],)",
            "[1,2]suffix]",
        ] {
            assert!(SemverVersionRange::parse(text).is_err(), "{text:?}");
        }
    }

    #[test]
    fn canonical_display_round_trips_the_structured_bounds() -> Result<()> {
        for text in [
            "[1]",
            "[1,1]",
            "(,)",
            "(, 4]",
            "[1.19.2, 1.20)",
            "[21.0.0-beta,)",
        ] {
            let range = SemverVersionRange::parse(text)?;
            assert_eq!(range.to_string().parse::<SemverVersionRange>()?, range);
        }
        Ok(())
    }

    #[test]
    fn reflection_constructs_ranges_through_the_parser() -> Result<()> {
        let partial = facet::Partial::alloc::<SemverVersionRange>()?;
        assert!(partial.field_index("lower").is_none());
        assert!(partial.field_index("upper").is_none());
        let range = partial
            .parse_from_str("[21.0.0-beta,)")?
            .build()?
            .materialize::<SemverVersionRange>()?;
        assert_eq!(range.lower(), &Included("21.0.0-beta".to_owned()));
        assert_eq!(range.upper(), &Unbounded);
        Ok(())
    }

    #[test]
    fn facet_registers_the_from_str_parser() {
        assert!(SemverVersionRange::SHAPE.is_from_str());
    }
}
