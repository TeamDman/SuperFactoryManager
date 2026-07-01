use super::AuditedSourceFile;
use super::SourceLanguage;
use super::SourceProblem;
use color_eyre::owo_colors::OwoColorize;
use std::collections::BTreeMap;

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct BranchSourceAuditReport {
    pub branch: String,
    pub audited_files: Vec<AuditedSourceFile>,
    pub problems: Vec<SourceProblem>,
}

impl BranchSourceAuditReport {
    #[must_use]
    pub fn new(branch: impl Into<String>) -> Self {
        Self {
            branch: branch.into(),
            audited_files: Vec::new(),
            problems: Vec::new(),
        }
    }

    pub fn push_file(&mut self, file: AuditedSourceFile) {
        self.audited_files.push(file);
    }

    pub fn push_problem(&mut self, problem: SourceProblem) {
        self.problems.push(problem);
        self.problems.sort_by(|left, right| {
            right
                .line_count
                .cmp(&left.line_count)
                .then_with(|| left.detected.path.cmp(&right.detected.path))
        });
    }

    #[must_use]
    pub fn count_by_language(&self) -> BTreeMap<SourceLanguage, usize> {
        let mut counts = BTreeMap::new();
        for file in &self.audited_files {
            *counts.entry(file.language).or_insert(0) += 1;
        }
        counts
    }

    #[must_use]
    pub fn largest_file(&self) -> Option<&AuditedSourceFile> {
        self.audited_files.iter().max_by(|left, right| {
            left.line_count
                .cmp(&right.line_count)
                .then_with(|| right.repo_path.cmp(&left.repo_path))
        })
    }

    #[must_use]
    pub fn summary_line(&self) -> String {
        let counts = self.count_by_language();
        let rust = counts
            .get(&SourceLanguage::Rust)
            .copied()
            .unwrap_or_default();
        let java = counts
            .get(&SourceLanguage::Java)
            .copied()
            .unwrap_or_default();
        let warnings = self.problems.len();
        let warning_cell = if warnings == 0 {
            warnings.to_string().green().to_string()
        } else {
            warnings.to_string().yellow().bold().to_string()
        };
        let largest = self.largest_file().map_or_else(
            || "none".dimmed().to_string(),
            |file| format!("{} lines {}", file.line_count, file.repo_path),
        );

        format!(
            "{} rust={} java={} warnings={} largest={}",
            self.branch.as_str().cyan().bold(),
            rust.to_string().cyan(),
            java.to_string().cyan(),
            warning_cell,
            largest
        )
    }
}

#[cfg(test)]
mod tests {
    use super::BranchSourceAuditReport;
    use crate::source_audit::AuditedSourceFile;
    use crate::source_audit::SourceLanguage;
    use crate::source_audit::SourceLineCount;

    #[test]
    fn aggregates_counts_by_language() {
        let mut report = BranchSourceAuditReport::new("1.19.2");
        report.push_file(AuditedSourceFile::new(
            "a.rs",
            SourceLanguage::Rust,
            SourceLineCount(1),
        ));
        report.push_file(AuditedSourceFile::new(
            "b.java",
            SourceLanguage::Java,
            SourceLineCount(2),
        ));
        report.push_file(AuditedSourceFile::new(
            "c.rs",
            SourceLanguage::Rust,
            SourceLineCount(3),
        ));

        let counts = report.count_by_language();
        assert_eq!(counts.get(&SourceLanguage::Rust), Some(&2));
        assert_eq!(counts.get(&SourceLanguage::Java), Some(&1));
        assert_eq!(
            report.largest_file().map(|file| file.repo_path.as_str()),
            Some("c.rs")
        );
    }
}
