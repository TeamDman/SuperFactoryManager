use facet::Facet;
use std::fmt;
use std::str::FromStr;

#[derive(Clone, Copy, Debug, Default, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ErrorAction {
    #[default]
    Bail,
    Continue,
}

impl ErrorAction {
    #[must_use]
    pub fn should_continue(self) -> bool {
        self == Self::Continue
    }
}

impl fmt::Display for ErrorAction {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Bail => f.write_str("bail"),
            Self::Continue => f.write_str("continue"),
        }
    }
}

impl FromStr for ErrorAction {
    type Err = eyre::Report;

    fn from_str(input: &str) -> Result<Self, Self::Err> {
        match input.trim().to_ascii_lowercase().as_str() {
            "bail" => Ok(Self::Bail),
            "continue" => Ok(Self::Continue),
            _ => eyre::bail!("Invalid --error-action '{input}'. Expected 'bail' or 'continue'."),
        }
    }
}
