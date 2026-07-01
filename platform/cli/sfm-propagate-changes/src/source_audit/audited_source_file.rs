use super::SourceLanguage;
use super::SourceLineCount;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct AuditedSourceFile {
    pub repo_path: String,
    pub language: SourceLanguage,
    pub line_count: SourceLineCount,
}

impl AuditedSourceFile {
    #[must_use]
    pub fn new(
        repo_path: impl Into<String>,
        language: SourceLanguage,
        line_count: SourceLineCount,
    ) -> Self {
        Self {
            repo_path: repo_path.into(),
            language,
            line_count,
        }
    }
}
