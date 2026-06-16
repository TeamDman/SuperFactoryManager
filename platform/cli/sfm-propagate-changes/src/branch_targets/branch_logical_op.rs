#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BranchLogicalOp {
    And,
    Or,
}

impl BranchLogicalOp {
    pub(super) fn match_separator(self, input: &str, index: usize) -> Option<SeparatorByteLength> {
        let bytes = input.as_bytes();
        match self {
            Self::And => match bytes.get(index) {
                Some(b'&') if bytes.get(index + 1) == Some(&b'&') => {
                    Some(SeparatorByteLength::new(2))
                }
                Some(b'&') => Some(SeparatorByteLength::new(1)),
                _ if matches_keyword(input, index, "AND") => Some(SeparatorByteLength::new(3)),
                _ => None,
            },
            Self::Or => match bytes.get(index) {
                Some(b'|') if bytes.get(index + 1) == Some(&b'|') => {
                    Some(SeparatorByteLength::new(2))
                }
                Some(b'|' | b',') => Some(SeparatorByteLength::new(1)),
                _ if matches_keyword(input, index, "OR") => Some(SeparatorByteLength::new(2)),
                _ => None,
            },
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) struct SeparatorByteLength(usize);

impl SeparatorByteLength {
    /// Number of bytes to advance after matching an ASCII separator token.
    const fn new(value: usize) -> Self {
        Self(value)
    }

    pub const fn get(self) -> usize {
        self.0
    }
}

fn matches_keyword(input: &str, index: usize, keyword: &str) -> bool {
    let Some(candidate) = input.get(index..index + keyword.len()) else {
        return false;
    };

    candidate.eq_ignore_ascii_case(keyword)
        && is_keyword_boundary(input.as_bytes().get(index.wrapping_sub(1)).copied())
        && is_keyword_boundary(input.as_bytes().get(index + keyword.len()).copied())
}

fn is_keyword_boundary(byte: Option<u8>) -> bool {
    byte.is_none_or(|byte| byte.is_ascii_whitespace())
}
