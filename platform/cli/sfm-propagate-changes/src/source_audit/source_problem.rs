use super::DetectedSourceLocation;
use super::ProblemEmitterLocation;
use super::SourceLanguage;
use super::SourceLineCount;
use super::SourceLineLimit;
use color_eyre::owo_colors::OwoColorize;
use std::panic::Location;

#[derive(Clone, Debug, Eq, PartialEq)]
enum SourceProblemKind {
    LargeFile { line_limit: SourceLineLimit },
    DirectModEventAnnotation { annotation: &'static str },
    AuditRule(AuditRuleDiagnostic),
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) enum AuditRuleDiagnostic {
    Violation {
        rule: String,
        forbidden_call: String,
        caller_context: String,
    },
    UnresolvedCall {
        rule: String,
        member: String,
        receiver_expression: String,
        caller_context: String,
    },
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SourceProblem {
    kind: SourceProblemKind,
    pub language: SourceLanguage,
    pub line_count: SourceLineCount,
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
            kind: SourceProblemKind::LargeFile { line_limit },
            language,
            line_count,
            detected: DetectedSourceLocation::new(
                branch,
                repo_path,
                line_limit.first_excess_line(),
                1,
            ),
            emitted_by: ProblemEmitterLocation::from_caller(Location::caller()),
        }
    }

    #[track_caller]
    #[must_use]
    pub fn direct_mod_event_annotation(
        branch: &str,
        repo_path: &str,
        line_count: SourceLineCount,
        annotation: &'static str,
        line: usize,
        column: usize,
    ) -> Self {
        Self {
            kind: SourceProblemKind::DirectModEventAnnotation { annotation },
            language: SourceLanguage::Java,
            line_count,
            detected: DetectedSourceLocation::new(branch, repo_path, line, column),
            emitted_by: ProblemEmitterLocation::from_caller(Location::caller()),
        }
    }

    #[track_caller]
    #[must_use]
    pub(crate) fn audit_rule(
        branch: &str,
        repo_path: &str,
        line_count: SourceLineCount,
        line: usize,
        column: usize,
        diagnostic: AuditRuleDiagnostic,
    ) -> Self {
        Self {
            kind: SourceProblemKind::AuditRule(diagnostic),
            language: SourceLanguage::Java,
            line_count,
            detected: DetectedSourceLocation::new(branch, repo_path, line, column),
            emitted_by: ProblemEmitterLocation::from_caller(Location::caller()),
        }
    }

    #[must_use]
    pub fn warning_line(&self) -> String {
        match &self.kind {
            SourceProblemKind::LargeFile { line_limit } => format!(
                "{} source file too large: lang={} lines={} max={} detected={} emitted-by={}",
                "WARN".yellow().bold(),
                self.language,
                self.line_count,
                line_limit,
                self.detected,
                self.emitted_by
            ),
            SourceProblemKind::DirectModEventAnnotation { annotation } => format!(
                "{} direct mod event annotation: annotation=@{} replacement=@SFMSubscribeEvent lang={} detected={} emitted-by={}",
                "WARN".yellow().bold(),
                annotation,
                self.language,
                self.detected,
                self.emitted_by
            ),
            SourceProblemKind::AuditRule(AuditRuleDiagnostic::Violation {
                rule,
                forbidden_call,
                caller_context,
            }) => format!(
                "{} audit rule violation: rule={rule:?} callee={forbidden_call:?} caller={caller_context:?} lang={} detected={} emitted-by={}",
                "WARN".yellow().bold(),
                self.language,
                self.detected,
                self.emitted_by
            ),
            SourceProblemKind::AuditRule(AuditRuleDiagnostic::UnresolvedCall {
                rule,
                member,
                receiver_expression,
                caller_context,
            }) => format!(
                "{} unresolved audit rule call: rule={rule:?} member={member} receiver={receiver_expression:?} caller={caller_context:?} lang={} detected={} emitted-by={}",
                "WARN".yellow().bold(),
                self.language,
                self.detected,
                self.emitted_by
            ),
        }
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

    #[test]
    fn renders_direct_mod_event_annotation_with_the_required_replacement() {
        let problem = SourceProblem::direct_mod_event_annotation(
            "1.19.2",
            "platform/minecraft/src/main/java/ca/teamdman/sfm/EventHandler.java",
            SourceLineCount(8),
            "SubscribeEvent",
            6,
            5,
        );

        let warning = problem.warning_line();
        assert!(warning.contains("annotation=@SubscribeEvent"));
        assert!(warning.contains("replacement=@SFMSubscribeEvent"));
        assert!(warning.contains(
            "1.19.2:platform/minecraft/src/main/java/ca/teamdman/sfm/EventHandler.java:6:5"
        ));
    }
}
