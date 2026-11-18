use crate::java_identifier::JavaIdentifier;
use eyre::Result;
use facet::Facet;
use std::fmt;
use std::str::FromStr;

/// A nonempty dotted sequence of Java identifiers, rather than several packages.
///
/// Follows the package grammar in [JLS 7.4](https://docs.oracle.com/javase/specs/jls/se21/html/jls-7.html#jls-7.4)
/// and the identifier rules owned by [`JavaIdentifier`]. This proves syntax, not
/// domain ownership, package existence or module/export accessibility. Components
/// retain case and Unicode; identifier-ignorable characters are canonicalized by
/// the identifier type. A target JDK can have an older Unicode table.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(opaque, proxy = String, traits(FromStr))]
pub struct JavaPackageName(Box<[JavaIdentifier]>);

impl JavaPackageName {
    /// # Errors
    /// Rejects empty names/segments or any component that is not a Java identifier.
    pub fn parse(value: &str) -> Result<Self> {
        let segments = value
            .split('.')
            .map(JavaIdentifier::parse)
            .collect::<Result<Vec<_>>>()?;
        Ok(Self(segments.into_boxed_slice()))
    }

    #[must_use]
    pub fn segments(&self) -> &[JavaIdentifier] {
        &self.0
    }
}

impl FromStr for JavaPackageName {
    type Err = eyre::Report;

    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}

impl fmt::Display for JavaPackageName {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        for (index, segment) in self.0.iter().enumerate() {
            if index != 0 {
                f.write_str(".")?;
            }
            write!(f, "{segment}")?;
        }
        Ok(())
    }
}

impl TryFrom<String> for JavaPackageName {
    type Error = eyre::Report;
    fn try_from(value: String) -> eyre::Result<Self> {
        Self::parse(&value)
    }
}
impl From<&JavaPackageName> for String {
    fn from(value: &JavaPackageName) -> Self {
        value.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn package_components_are_parsed_and_variable_length() -> Result<()> {
        let name = JavaPackageName::parse("ca.teamdman.sfm")?;
        assert_eq!(name.segments().len(), 3);
        assert_eq!(name.segments()[1].as_str(), "teamdman");
        assert_eq!(name.to_string(), "ca.teamdman.sfm");
        assert_eq!(
            JavaPackageName::parse("π.名前.module")?.to_string(),
            "π.名前.module"
        );
        assert_eq!(JavaPackageName::parse("single")?.segments().len(), 1);
        Ok(())
    }

    #[test]
    fn empty_components_and_reserved_identifiers_are_rejected() {
        for value in [
            "", ".name", "name.", "a..b", "a.class", "a.true", "a._", "a.2b", "a.b-c", "a. b",
        ] {
            assert!(JavaPackageName::parse(value).is_err(), "{value:?}");
        }
    }
}
