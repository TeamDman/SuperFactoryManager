use crate::worktree::parse_version;

#[derive(Debug, Clone, Copy)]
enum McVersionFilterOp {
    Lt,
    Lte,
    Gt,
    Gte,
    Eq,
}

#[derive(Debug, Clone, Copy)]
pub struct McVersionFilterClause {
    op: McVersionFilterOp,
    version: (u32, u32, u32),
}

#[derive(Debug, Clone)]
pub struct McVersionFilter(pub Vec<Vec<McVersionFilterClause>>);

#[derive(Debug, Clone, Copy)]
enum McVersionLogicalOp {
    And,
    Or,
}

impl McVersionFilterClause {
    fn parse(input: &str) -> eyre::Result<Self> {
        let trimmed = input.trim();
        let (op, version_text) = if let Some(rest) = trimmed.strip_prefix(">=") {
            (McVersionFilterOp::Gte, rest)
        } else if let Some(rest) = trimmed.strip_prefix("<=") {
            (McVersionFilterOp::Lte, rest)
        } else if let Some(rest) = trimmed.strip_prefix("==") {
            (McVersionFilterOp::Eq, rest)
        } else if let Some(rest) = trimmed.strip_prefix('>') {
            (McVersionFilterOp::Gt, rest)
        } else if let Some(rest) = trimmed.strip_prefix('<') {
            (McVersionFilterOp::Lt, rest)
        } else if let Some(rest) = trimmed.strip_prefix('=') {
            (McVersionFilterOp::Eq, rest)
        } else {
            (McVersionFilterOp::Eq, trimmed)
        };

        let version_text = version_text.trim();
        let version = parse_version(version_text)
            .ok_or_else(|| eyre::eyre!("Invalid mc version expression: '{input}'"))?;

        Ok(Self { op, version })
    }

    fn matches(self, value: (u32, u32, u32)) -> bool {
        match self.op {
            McVersionFilterOp::Lt => value < self.version,
            McVersionFilterOp::Lte => value <= self.version,
            McVersionFilterOp::Gt => value > self.version,
            McVersionFilterOp::Gte => value >= self.version,
            McVersionFilterOp::Eq => value == self.version,
        }
    }
}

impl McVersionFilter {
    /// Parse an MC version filter expression.
    ///
    /// Commas and `AND` combine clauses conjunctively.
    /// `|` and `OR` combine clause groups disjunctively.
    ///
    /// Examples: `>=1.19.2`, `>=1.19.2,<=1.21.1`, `=1.19.2 OR =1.21.1`
    ///
    /// # Errors
    ///
    /// Returns an error if the expression contains an invalid operator or version.
    pub fn parse(input: &str) -> eyre::Result<Self> {
        let groups = split_expression(input, McVersionLogicalOp::Or);
        if groups.is_empty() {
            eyre::bail!("Invalid mc version expression: '{input}'");
        }

        let mut parsed_groups = Vec::new();

        for group in groups {
            if group.trim().is_empty() {
                eyre::bail!("Invalid mc version expression: '{input}'");
            }

            let clauses = split_expression(group, McVersionLogicalOp::And);
            if clauses.is_empty() || clauses.iter().any(|clause| clause.trim().is_empty()) {
                eyre::bail!("Invalid mc version expression: '{input}'");
            }

            let parsed_clauses: eyre::Result<Vec<McVersionFilterClause>> = clauses
                .into_iter()
                .map(str::trim)
                .map(McVersionFilterClause::parse)
                .collect();

            parsed_groups.push(parsed_clauses?);
        }

        Ok(Self(parsed_groups))
    }

    #[must_use]
    pub fn matches_parsed(&self, value: (u32, u32, u32)) -> bool {
        self.0
            .iter()
            .any(|group| group.iter().all(|clause| clause.matches(value)))
    }

    #[must_use]
    pub fn matches_version_text(&self, value: &str) -> Option<bool> {
        let parsed = parse_version(value)?;
        Some(self.matches_parsed(parsed))
    }
}

fn split_expression(input: &str, op: McVersionLogicalOp) -> Vec<&str> {
    let bytes = input.as_bytes();
    let mut parts = Vec::new();
    let mut part_start = 0usize;
    let mut index = 0usize;

    while index < bytes.len() {
        if let Some(separator_len) = match_separator(input, index, op) {
            parts.push(&input[part_start..index]);
            index += separator_len;
            part_start = index;
            continue;
        }

        index += 1;
    }

    parts.push(&input[part_start..]);
    parts
}

fn match_separator(input: &str, index: usize, op: McVersionLogicalOp) -> Option<usize> {
    match op {
        McVersionLogicalOp::And => {
            let bytes = input.as_bytes();
            match bytes.get(index) {
                Some(b'&') if bytes.get(index + 1) == Some(&b'&') => Some(2),
                Some(b',' | b'&') => Some(1),
                _ if matches_keyword(input, index, "AND") => Some(3),
                _ => None,
            }
        }
        McVersionLogicalOp::Or => {
            let bytes = input.as_bytes();
            match bytes.get(index) {
                Some(b'|') if bytes.get(index + 1) == Some(&b'|') => Some(2),
                Some(b'|') => Some(1),
                _ if matches_keyword(input, index, "OR") => Some(2),
                _ => None,
            }
        }
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
    byte.is_none_or(|byte| !byte.is_ascii_alphanumeric())
}

#[cfg(test)]
mod tests {
    use super::McVersionFilter;

    #[test]
    fn supports_existing_and_syntax() {
        let filter = McVersionFilter::parse(">=1.19.2,<=1.21.1").expect("filter should parse");

        assert!(
            filter
                .matches_version_text("1.19.2")
                .expect("version should parse")
        );
        assert!(
            filter
                .matches_version_text("1.20.4")
                .expect("version should parse")
        );
        assert!(
            filter
                .matches_version_text("1.21.1")
                .expect("version should parse")
        );
        assert!(
            !filter
                .matches_version_text("1.19.1")
                .expect("version should parse")
        );
        assert!(
            !filter
                .matches_version_text("1.21.2")
                .expect("version should parse")
        );
    }

    #[test]
    fn supports_symbol_or_syntax() {
        let filter =
            McVersionFilter::parse("=1.19.2|=1.20.1|=1.21.1").expect("filter should parse");

        assert!(
            filter
                .matches_version_text("1.19.2")
                .expect("version should parse")
        );
        assert!(
            filter
                .matches_version_text("1.20.1")
                .expect("version should parse")
        );
        assert!(
            filter
                .matches_version_text("1.21.1")
                .expect("version should parse")
        );
        assert!(
            !filter
                .matches_version_text("1.20.2")
                .expect("version should parse")
        );
    }

    #[test]
    fn supports_word_and_or_syntax_with_and_precedence() {
        let filter =
            McVersionFilter::parse(">=1.20 AND <1.21 OR =1.21.1").expect("filter should parse");

        assert!(
            filter
                .matches_version_text("1.20")
                .expect("version should parse")
        );
        assert!(
            filter
                .matches_version_text("1.20.4")
                .expect("version should parse")
        );
        assert!(
            filter
                .matches_version_text("1.21.1")
                .expect("version should parse")
        );
        assert!(
            !filter
                .matches_version_text("1.19.4")
                .expect("version should parse")
        );
        assert!(
            !filter
                .matches_version_text("1.21")
                .expect("version should parse")
        );
    }

    #[test]
    fn rejects_empty_expression_groups() {
        assert!(McVersionFilter::parse("=1.20 OR ").is_err());
        assert!(McVersionFilter::parse("|=1.20").is_err());
        assert!(McVersionFilter::parse(">=1.20 AND ").is_err());
    }
}
