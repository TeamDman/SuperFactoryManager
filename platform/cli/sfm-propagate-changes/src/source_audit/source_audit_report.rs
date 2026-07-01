use super::BranchSourceAuditReport;
use super::SourceLanguage;
use super::SourceProblem;
use color_eyre::owo_colors::OwoColorize;
use std::collections::BTreeMap;

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct SourceAuditReport {
    pub branches: Vec<BranchSourceAuditReport>,
}

impl SourceAuditReport {
    pub fn push_branch(&mut self, report: BranchSourceAuditReport) {
        self.branches.push(report);
    }

    #[must_use]
    pub fn total_files_by_language(&self) -> BTreeMap<SourceLanguage, usize> {
        let mut totals = BTreeMap::new();
        for branch in &self.branches {
            for (language, count) in branch.count_by_language() {
                *totals.entry(language).or_insert(0) += count;
            }
        }
        totals
    }

    #[must_use]
    pub fn problems(&self) -> Vec<&SourceProblem> {
        let mut problems = self
            .branches
            .iter()
            .flat_map(|branch| branch.problems.iter())
            .collect::<Vec<_>>();
        problems.sort_by(|left, right| {
            right
                .line_count
                .cmp(&left.line_count)
                .then_with(|| left.detected.branch.cmp(&right.detected.branch))
                .then_with(|| left.detected.path.cmp(&right.detected.path))
        });
        problems
    }

    #[must_use]
    pub fn final_summary_line(&self) -> String {
        let totals = self.total_files_by_language();
        let rust = totals
            .get(&SourceLanguage::Rust)
            .copied()
            .unwrap_or_default();
        let java = totals
            .get(&SourceLanguage::Java)
            .copied()
            .unwrap_or_default();
        let warnings = self.problems().len();
        let warning_cell = if warnings == 0 {
            warnings.to_string().green().to_string()
        } else {
            warnings.to_string().yellow().bold().to_string()
        };

        format!(
            "Source audit totals: branches={} rust={} java={} warnings={}",
            self.branches.len().to_string().cyan().bold(),
            rust.to_string().cyan(),
            java.to_string().cyan(),
            warning_cell
        )
    }
}
