use super::DetectedSourceLocation;
use super::ProblemEmitterLocation;
use super::SourceLanguage;
use super::SourceLineCount;
use super::SourceLineLimit;
use color_eyre::owo_colors::OwoColorize;
use std::panic::Location;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SourceProblem {
    pub language: SourceLanguage,
    pub line_count: SourceLineCount,
    pub line_limit: SourceLineLimit,
    pub detected: DetectedSourceLocation,
    pub emitted_by: ProblemEmitterLocation,
}

impl SourceProblem {
    #[track_caller]
    #[must_use]
    pub fn large_file(
        branch: &str,
        repo_path: &str,
        language: SourceLanguage,
        line_count: SourceLineCount,
        line_limit: SourceLineLimit,
    ) -> Self {
        Self {
            language,
            line_count,
            line_limit,
            detected: DetectedSourceLocation::new(
                branch,
                repo_path,
                line_limit.first_excess_line(),
                1,
            ),
            emitted_by: ProblemEmitterLocation::from_caller(Location::caller()),
        }
    }

    #[must_use]
    pub fn warning_line(&self) -> String {
        format!(
            "{} source file too large: lang={} lines={} max={} detected={} emitted-by={}",
            "WARN".yellow().bold(),
            self.language,
            self.line_count,
            self.line_limit,
            self.detected,
            self.emitted_by
        )
    }
}

#[cfg(test)]
mod tests {
    use super::SourceProblem;
    use crate::source_audit::SourceLanguage;
    use crate::source_audit::SourceLineCount;
    use crate::source_audit::SourceLineLimit;

    fn emit_problem_from_helper() -> SourceProblem {
        SourceProblem::large_file(
            "1.19.2",
            "platform/cli/src/lib.rs",
            SourceLanguage::Rust,
            SourceLineCount(1001),
            SourceLineLimit(1000),
        )
    }

    #[test]
    fn captures_detected_location_and_emitter_callsite() {
        let problem = emit_problem_from_helper();
        assert_eq!(problem.detected.line, 1001);
        assert_eq!(problem.detected.column, 1);
        assert!(problem.emitted_by.file.ends_with("source_problem.rs"));
        assert!(problem.emitted_by.line > 0);
        assert!(problem.emitted_by.column > 0);
    }
}
