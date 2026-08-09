use super::BranchConjunction;
use super::BranchRule;
use super::WorktreeTarget;
use super::branch_logical_op::BranchLogicalOp;
use std::fmt;
use std::str::FromStr;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct BranchQuery {
    pub alternatives: Vec<BranchConjunction>,
}

impl BranchQuery {
    #[must_use]
    pub fn core() -> Self {
        Self {
            alternatives: vec![BranchConjunction {
                rules: vec![BranchRule::Core],
            }],
        }
    }

    /// Parse a branch query string into typed OR-of-AND rules.
    ///
    /// # Errors
    ///
    /// Returns an error if the query is empty or contains an invalid rule.
    pub fn parse(input: &str) -> eyre::Result<Self> {
        let trimmed = input.trim();
        if trimmed.is_empty() {
            eyre::bail!("Branch query cannot be empty");
        }

        let alternatives = split_expression(trimmed, BranchLogicalOp::Or)
            .into_iter()
            .map(|alternative| parse_conjunction(alternative, trimmed))
            .collect::<eyre::Result<Vec<_>>>()?;

        if alternatives.is_empty() {
            eyre::bail!("Branch query cannot be empty");
        }

        Ok(Self { alternatives })
    }

    #[must_use]
    pub fn matches(&self, target: &WorktreeTarget) -> bool {
        self.alternatives
            .iter()
            .any(|alternative| alternative.matches(target))
    }

    #[must_use]
    pub fn filter_targets<'a>(
        &self,
        targets: impl IntoIterator<Item = &'a WorktreeTarget>,
    ) -> Vec<&'a WorktreeTarget> {
        targets
            .into_iter()
            .filter(|target| self.matches(target))
            .collect()
    }
}

impl Default for BranchQuery {
    fn default() -> Self {
        Self::core()
    }
}

impl fmt::Display for BranchQuery {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        for (index, alternative) in self.alternatives.iter().enumerate() {
            if index > 0 {
                f.write_str(" OR ")?;
            }
            write!(f, "{alternative}")?;
        }
        Ok(())
    }
}

impl FromStr for BranchQuery {
    type Err = eyre::Report;

    fn from_str(input: &str) -> Result<Self, Self::Err> {
        Self::parse(input)
    }
}

#[cfg(test)]
impl<'a> arbitrary::Arbitrary<'a> for BranchQuery {
    fn arbitrary(u: &mut arbitrary::Unstructured<'a>) -> arbitrary::Result<Self> {
        let length = u.int_in_range(1..=4)?;
        let mut alternatives = Vec::with_capacity(length);
        for _ in 0..length {
            alternatives.push(BranchConjunction::arbitrary(u)?);
        }
        Ok(Self { alternatives })
    }
}

fn parse_conjunction(input: &str, full_query: &str) -> eyre::Result<BranchConjunction> {
    if input.trim().is_empty() {
        eyre::bail!("Invalid branch query: '{full_query}'");
    }

    let rules = split_expression(input, BranchLogicalOp::And)
        .into_iter()
        .map(str::trim)
        .map(|rule| {
            if rule.is_empty() {
                eyre::bail!("Invalid branch query: '{full_query}'");
            }
            BranchRule::parse(rule)
        })
        .collect::<eyre::Result<Vec<_>>>()?;

    if rules.is_empty() {
        eyre::bail!("Invalid branch query: '{full_query}'");
    }

    Ok(BranchConjunction { rules })
}

fn split_expression(input: &str, op: BranchLogicalOp) -> Vec<&str> {
    let bytes = input.as_bytes();
    let mut parts = Vec::new();
    let mut part_start = 0usize;
    let mut index = 0usize;

    while index < bytes.len() {
        if let Some(separator_len) = op.match_separator(input, index) {
            parts.push(&input[part_start..index]);
            index += separator_len.get();
            part_start = index;
            continue;
        }

        index += 1;
    }

    parts.push(&input[part_start..]);
    parts
}
