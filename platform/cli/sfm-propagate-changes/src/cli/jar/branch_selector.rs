use crate::branch_targets::BranchConjunction;
use crate::branch_targets::BranchQuery;
use facet::Facet;
use std::fmt;
use std::ops::Deref;

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(proxy = String)]
pub struct BranchSelector(pub String);

impl From<String> for BranchSelector {
    fn from(value: String) -> Self {
        Self(value)
    }
}

impl From<&BranchSelector> for String {
    fn from(value: &BranchSelector) -> Self {
        value.0.clone()
    }
}

impl BranchSelector {
    /// Parse this CLI selector into the branch query model.
    ///
    /// # Errors
    ///
    /// Returns an error when the selector expression is invalid.
    pub fn into_query(self) -> eyre::Result<BranchQuery> {
        let trimmed = self.0.trim();
        if let Some(rest) = strip_popular_prefix(trimmed) {
            let popular_query = crate::cli::curseforge::resolve_cached_popular_branch_query()?;
            let rest = rest.trim();
            if rest.is_empty() {
                return Ok(popular_query);
            }
            let rest = rest.strip_prefix("AND").unwrap_or(rest).trim();
            let constraint_query = BranchQuery::parse(rest)?;
            return Ok(and_queries(&popular_query, &constraint_query));
        }
        BranchQuery::parse(trimmed)
    }
}

fn strip_popular_prefix(input: &str) -> Option<&str> {
    let (prefix, rest) = input.split_at_checked("popular".len())?;
    if !prefix.eq_ignore_ascii_case("popular") {
        return None;
    }
    if rest.is_empty()
        || rest.starts_with(char::is_whitespace)
        || rest.starts_with('<')
        || rest.starts_with('>')
        || rest.starts_with('=')
    {
        return Some(rest);
    }
    None
}

fn and_queries(left: &BranchQuery, right: &BranchQuery) -> BranchQuery {
    let mut alternatives = Vec::new();
    for left_alternative in &left.alternatives {
        for right_alternative in &right.alternatives {
            let mut rules =
                Vec::with_capacity(left_alternative.rules.len() + right_alternative.rules.len());
            rules.extend(left_alternative.rules.iter().cloned());
            rules.extend(right_alternative.rules.iter().cloned());
            alternatives.push(BranchConjunction { rules });
        }
    }
    BranchQuery { alternatives }
}

impl fmt::Display for BranchSelector {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl AsRef<str> for BranchSelector {
    fn as_ref(&self) -> &str {
        &self.0
    }
}

impl Deref for BranchSelector {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn popular_prefix_requires_keyword_boundary() {
        assert_eq!(strip_popular_prefix("popular"), Some(""));
        assert_eq!(
            strip_popular_prefix("popular >= 1.20.4"),
            Some(" >= 1.20.4")
        );
        assert_eq!(strip_popular_prefix("popular>=1.20.4"), Some(">=1.20.4"));
        assert_eq!(strip_popular_prefix("popularity"), None);
    }

    #[test]
    fn and_queries_cross_products_alternatives() {
        let left = BranchQuery::parse("1.19.2 OR 1.20.4").expect("query should parse");
        let right = BranchQuery::parse(">=1.20.4").expect("query should parse");

        assert_eq!(
            and_queries(&left, &right).to_string(),
            "1.19.2 AND >=1.20.4 OR 1.20.4 AND >=1.20.4"
        );
    }
}
