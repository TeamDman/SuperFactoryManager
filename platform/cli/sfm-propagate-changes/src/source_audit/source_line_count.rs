use std::fmt;

#[derive(Clone, Copy, Debug, Default, Eq, Ord, PartialEq, PartialOrd)]
pub struct SourceLineCount(pub usize);

impl SourceLineCount {
    #[must_use]
    pub fn from_text(text: &str) -> Self {
        if text.is_empty() {
            Self(0)
        } else {
            Self(text.lines().count())
        }
    }
}

impl fmt::Display for SourceLineCount {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[cfg(test)]
mod tests {
    use super::SourceLineCount;

    #[test]
    fn counts_lines_with_and_without_trailing_newline() {
        assert_eq!(SourceLineCount::from_text("").0, 0);
        assert_eq!(SourceLineCount::from_text("one").0, 1);
        assert_eq!(SourceLineCount::from_text("one\n").0, 1);
        assert_eq!(SourceLineCount::from_text("one\ntwo").0, 2);
    }
}
