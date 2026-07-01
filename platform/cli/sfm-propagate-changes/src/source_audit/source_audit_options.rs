use super::SourceLanguage;
use super::SourceLineLimit;
use crate::branch_targets::BranchQuery;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SourceAuditOptions {
    pub branch: BranchQuery,
    pub languages: Vec<SourceLanguage>,
    pub max_lines: SourceLineLimit,
}

impl SourceAuditOptions {
    #[must_use]
    pub fn includes_language(&self, language: SourceLanguage) -> bool {
        self.languages.is_empty() || self.languages.contains(&language)
    }
}
