use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use regex::Regex;
use std::fmt;
use std::str::FromStr;
use std::sync::LazyLock;

static IDENTIFIER: LazyLock<Result<Regex, regex::Error>> = LazyLock::new(|| {
    Regex::new(
        r"\A[\p{L}\p{Nl}\p{Sc}\p{Pc}][\p{L}\p{Nl}\p{Sc}\p{Pc}\p{Mn}\p{Mc}\p{Nd}\p{Cf}\x{0000}-\x{0008}\x{000E}-\x{001B}\x{007F}-\x{009F}]*\z",
    )
});
static IGNORABLE: LazyLock<Result<Regex, regex::Error>> =
    LazyLock::new(|| Regex::new(r"[\p{Cf}\x{0000}-\x{0008}\x{000E}-\x{001B}\x{007F}-\x{009F}]"));

/// A Java identifier, with identifier-ignorable characters removed for identity.
///
/// Uses the letter, letter-number, currency and connector categories for starts;
/// parts also permit combining marks, decimal digits and ignorable characters.
/// Reserved keywords and boolean/null literals are excluded. Contextual keywords
/// remain valid identifiers where the package grammar allows them.
///
/// These rules follow [JLS 3.8](https://docs.oracle.com/javase/specs/jls/se21/html/jls-3.html#jls-3.8)
/// and [`Character.isJavaIdentifierPart`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Character.html#isJavaIdentifierPart(int)).
/// Unicode categories use the locked regex tables (Unicode 16.0); older target
/// JDKs can differ for newly assigned characters. SFM's group type separately
/// requires portable ASCII. No Unicode normalization or case folding is applied.
#[derive(Clone, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[facet(opaque, proxy = String, traits(FromStr))]
pub struct JavaIdentifier(String);

impl JavaIdentifier {
    /// # Errors
    /// Rejects invalid start/part characters, reserved keywords and literals.
    pub fn parse(value: &str) -> Result<Self> {
        ensure!(
            IDENTIFIER
                .as_ref()
                .wrap_err("invalid Java identifier category pattern")?
                .is_match(value),
            "invalid Java identifier: {value:?}"
        );
        let canonical = IGNORABLE
            .as_ref()
            .wrap_err("invalid Java identifier-ignorable pattern")?
            .replace_all(value, "")
            .into_owned();
        ensure!(
            !matches!(
                canonical.as_str(),
                "abstract"
                    | "assert"
                    | "boolean"
                    | "break"
                    | "byte"
                    | "case"
                    | "catch"
                    | "char"
                    | "class"
                    | "const"
                    | "continue"
                    | "default"
                    | "do"
                    | "double"
                    | "else"
                    | "enum"
                    | "extends"
                    | "final"
                    | "finally"
                    | "float"
                    | "for"
                    | "goto"
                    | "if"
                    | "implements"
                    | "import"
                    | "instanceof"
                    | "int"
                    | "interface"
                    | "long"
                    | "native"
                    | "new"
                    | "package"
                    | "private"
                    | "protected"
                    | "public"
                    | "return"
                    | "short"
                    | "static"
                    | "strictfp"
                    | "super"
                    | "switch"
                    | "synchronized"
                    | "this"
                    | "throw"
                    | "throws"
                    | "transient"
                    | "try"
                    | "void"
                    | "volatile"
                    | "while"
                    | "_"
                    | "true"
                    | "false"
                    | "null"
            ),
            "Java identifier cannot be a reserved keyword or literal: {value:?}"
        );
        Ok(Self(canonical))
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl FromStr for JavaIdentifier {
    type Err = eyre::Report;

    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}

impl fmt::Display for JavaIdentifier {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl TryFrom<String> for JavaIdentifier {
    type Error = eyre::Report;
    fn try_from(value: String) -> eyre::Result<Self> {
        Self::parse(&value)
    }
}
impl From<&JavaIdentifier> for String {
    fn from(value: &JavaIdentifier) -> Self {
        value.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unicode_java_categories_and_contextual_keywords_are_supported() -> Result<()> {
        for value in [
            "name",
            "_name",
            "$name",
            "π",
            "名",
            "Ⅳ",
            "€amount",
            "a\u{0301}",
            "a\u{0661}",
            "record",
            "var",
            "module",
            "yield",
        ] {
            assert_eq!(JavaIdentifier::parse(value)?.as_str(), value);
        }
        Ok(())
    }

    #[test]
    fn reserved_words_literals_and_invalid_identifiers_are_rejected() {
        for value in [
            "",
            "1name",
            "a-b",
            "a.b",
            "a b",
            "class",
            "const",
            "goto",
            "_",
            "true",
            "false",
            "null",
            "\u{0301}a",
            "a\n",
            "name ",
            "c\u{0000}lass",
        ] {
            assert!(JavaIdentifier::parse(value).is_err(), "{value:?}");
        }
    }

    #[test]
    fn ignorable_characters_do_not_create_a_different_identifier() -> Result<()> {
        assert_eq!(
            JavaIdentifier::parse("na\u{200d}me")?,
            JavaIdentifier::parse("name")?
        );
        assert_eq!(JavaIdentifier::parse("na\u{0000}me")?.as_str(), "name");
        assert_ne!(
            JavaIdentifier::parse("Name")?,
            JavaIdentifier::parse("name")?
        );
        assert_ne!(
            JavaIdentifier::parse("á")?,
            JavaIdentifier::parse("a\u{0301}")?
        );
        Ok(())
    }
}
