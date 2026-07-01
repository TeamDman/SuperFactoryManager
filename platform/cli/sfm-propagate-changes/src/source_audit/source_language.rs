use facet::Facet;
use std::fmt;
use std::path::Path;
use std::str::FromStr;

#[derive(Clone, Copy, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[repr(u8)]
#[facet(rename_all = "kebab-case")]
pub enum SourceLanguage {
    Rust,
    Java,
}

impl SourceLanguage {
    #[must_use]
    pub fn from_repo_path(path: &str) -> Option<Self> {
        let extension = Path::new(path).extension()?;
        if extension.eq_ignore_ascii_case("rs") {
            Some(Self::Rust)
        } else if extension.eq_ignore_ascii_case("java") {
            Some(Self::Java)
        } else {
            None
        }
    }

    #[must_use]
    pub fn label(self) -> &'static str {
        match self {
            Self::Rust => "rust",
            Self::Java => "java",
        }
    }
}

impl fmt::Display for SourceLanguage {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.label())
    }
}

impl FromStr for SourceLanguage {
    type Err = eyre::Error;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        match value {
            "rust" | "rs" => Ok(Self::Rust),
            "java" => Ok(Self::Java),
            _ => eyre::bail!("Unsupported source language '{value}'. Expected rust or java."),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::SourceLanguage;

    #[test]
    fn detects_language_from_repo_path() {
        assert_eq!(
            SourceLanguage::from_repo_path("platform/cli/src/main.rs"),
            Some(SourceLanguage::Rust)
        );
        assert_eq!(
            SourceLanguage::from_repo_path("platform/minecraft/src/Main.java"),
            Some(SourceLanguage::Java)
        );
        assert_eq!(SourceLanguage::from_repo_path("README.md"), None);
    }
}
