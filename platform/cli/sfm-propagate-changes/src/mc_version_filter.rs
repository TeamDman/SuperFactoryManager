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
pub struct McVersionFilter(pub Vec<McVersionFilterClause>);

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
    /// Parse one or more comma-separated MC version clauses.
    ///
    /// Examples: `>=1.19.2`, `>=1.19.2,<=1.21.1`, `=1.20.4`
    pub fn parse(input: &str) -> eyre::Result<Self> {
        let clauses: eyre::Result<Vec<McVersionFilterClause>> = input
            .split(',')
            .map(str::trim)
            .filter(|part| !part.is_empty())
            .map(McVersionFilterClause::parse)
            .collect();

        let clauses = clauses?;
        if clauses.is_empty() {
            eyre::bail!("Invalid mc version expression: '{input}'");
        }

        Ok(Self(clauses))
    }

    #[must_use]
    pub fn matches_parsed(&self, value: (u32, u32, u32)) -> bool {
        self.0.iter().all(|clause| clause.matches(value))
    }

    #[must_use]
    pub fn matches_version_text(&self, value: &str) -> Option<bool> {
        let parsed = parse_version(value)?;
        Some(self.matches_parsed(parsed))
    }
}
