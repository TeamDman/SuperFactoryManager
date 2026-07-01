use facet::Facet;
use std::fmt;
use std::str::FromStr;

#[derive(Clone, Copy, Debug, Default, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SourceOutputLayout {
    #[default]
    Jar,
    Filetree,
}

impl fmt::Display for SourceOutputLayout {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Jar => f.write_str("jar"),
            Self::Filetree => f.write_str("filetree"),
        }
    }
}

impl FromStr for SourceOutputLayout {
    type Err = eyre::Report;

    fn from_str(input: &str) -> Result<Self, Self::Err> {
        match input.trim().to_ascii_lowercase().as_str() {
            "jar" => Ok(Self::Jar),
            "filetree" => Ok(Self::Filetree),
            _ => eyre::bail!("Invalid --layout '{input}'. Expected 'jar' or 'filetree'."),
        }
    }
}
