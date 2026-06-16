use std::fmt;
use std::ops::Deref;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ExactBranch(pub String);

impl ExactBranch {
    pub fn new(branch: impl Into<String>) -> Self {
        Self(branch.into())
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Display for ExactBranch {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl AsRef<str> for ExactBranch {
    fn as_ref(&self) -> &str {
        &self.0
    }
}

impl Deref for ExactBranch {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl From<&str> for ExactBranch {
    fn from(value: &str) -> Self {
        Self::new(value)
    }
}

impl From<String> for ExactBranch {
    fn from(value: String) -> Self {
        Self::new(value)
    }
}

#[cfg(test)]
impl<'a> arbitrary::Arbitrary<'a> for ExactBranch {
    fn arbitrary(u: &mut arbitrary::Unstructured<'a>) -> arbitrary::Result<Self> {
        let branch = arbitrary_safe_branch_text(u, false)?;
        Ok(Self(branch))
    }
}

#[cfg(test)]
pub(super) fn arbitrary_safe_branch_text(
    unstructured: &mut arbitrary::Unstructured<'_>,
    include_glob: bool,
) -> arbitrary::Result<String> {
    const SAFE_CHARS: &[u8] = b"abcdefghijklmnopqrstuvwxyz0123456789/._-";
    let length = unstructured.int_in_range(1..=24)?;
    let mut text = String::new();
    for index in 0..length {
        if include_glob && index == length / 2 {
            text.push('*');
            continue;
        }
        let char_index = unstructured.int_in_range(0..=SAFE_CHARS.len() - 1)?;
        text.push(char::from(SAFE_CHARS[char_index]));
    }

    if matches!(text.as_str(), "core" | "AND" | "OR" | "and" | "or" | "*") {
        text.push_str("-branch");
    }
    Ok(text)
}
