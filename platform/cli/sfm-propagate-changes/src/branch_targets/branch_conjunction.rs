use super::BranchRule;
use super::WorktreeTarget;
use std::fmt;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct BranchConjunction {
    pub rules: Vec<BranchRule>,
}

impl BranchConjunction {
    #[must_use]
    pub fn matches(&self, target: &WorktreeTarget) -> bool {
        self.rules.iter().all(|rule| rule.matches(target))
    }
}

impl fmt::Display for BranchConjunction {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        for (index, rule) in self.rules.iter().enumerate() {
            if index > 0 {
                f.write_str(" AND ")?;
            }
            write!(f, "{rule}")?;
        }
        Ok(())
    }
}

#[cfg(test)]
impl<'a> arbitrary::Arbitrary<'a> for BranchConjunction {
    fn arbitrary(u: &mut arbitrary::Unstructured<'a>) -> arbitrary::Result<Self> {
        let length = u.int_in_range(1..=3)?;
        let mut rules = Vec::with_capacity(length);
        for _ in 0..length {
            rules.push(BranchRule::arbitrary(u)?);
        }
        Ok(Self { rules })
    }
}
