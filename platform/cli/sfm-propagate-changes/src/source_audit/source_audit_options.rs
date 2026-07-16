use super::SourceLanguage;
use super::SourceLineLimit;
use crate::branch_targets::BranchQuery;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SourceAuditOptions {
    pub branch: BranchQuery,
    pub languages: Vec<SourceLanguage>,
    /// When set, warn for tracked source files that exceed this line count.
    pub max_lines: Option<SourceLineLimit>,
    pub version_surfaces: bool,
    pub font_render_surface: bool,
}

impl SourceAuditOptions {
    #[must_use]
    pub fn includes_language(&self, language: SourceLanguage) -> bool {
        self.languages.is_empty() || self.languages.contains(&language)
    }
}
