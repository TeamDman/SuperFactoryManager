use super::AuditWarning;
use super::AuditWarningCategory;
use super::AuditWarningDetail;
use super::DetectedSourceLocation;
use super::ProblemEmitterLocation;
use super::SourceLanguage;
use super::SourceLineCount;
use super::SourceLineLimit;
use facet::Facet;
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
        call_site: JavaCallSite,
    },
    UnresolvedCall {
        rule: String,
        call_site: JavaCallSite,
    },
    ParseFailure {
        parser: &'static str,
    },
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct JavaCallSite {
    pub class_name: String,
    pub method_declaration: String,
    pub method_returns_value: bool,
    pub receiver_expression: String,
    pub member: String,
    /// The exact Java expression that invoked the audited member, excluding the trailing semicolon.
    pub call_expression: String,
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
    pub(crate) fn audit_warning(&self) -> AuditWarning {
        match &self.kind {
            SourceProblemKind::LargeFile { line_limit } => AuditWarning {
                category: AuditWarningCategory::SourceFileTooLarge,
                location: self.detected.clone(),
                language: self.language,
                detail: AuditWarningDetail::SourceFileTooLarge {
                    line_count: self.line_count.0,
                    maximum_line_count: line_limit.0,
                },
            },
            SourceProblemKind::DirectModEventAnnotation { annotation } => AuditWarning {
                category: AuditWarningCategory::DirectModEventAnnotation,
                location: self.detected.clone(),
                language: self.language,
                detail: AuditWarningDetail::DirectModEventAnnotation {
                    annotation: (*annotation).to_owned(),
                    required_replacement: "SFMSubscribeEvent".to_owned(),
                },
            },
            SourceProblemKind::AuditRule(AuditRuleDiagnostic::Violation {
                rule,
                forbidden_call,
                call_site,
            }) => AuditWarning {
                category: AuditWarningCategory::AuditRuleViolation,
                location: self.detected.clone(),
                language: self.language,
                detail: AuditWarningDetail::AuditRuleViolation {
                    rule: rule.clone(),
                    callee: forbidden_call.clone(),
                    call_site: call_site.clone(),
                },
            },
            SourceProblemKind::AuditRule(AuditRuleDiagnostic::UnresolvedCall {
                rule,
                call_site,
            }) => AuditWarning {
                category: AuditWarningCategory::UnresolvedAuditRuleCall,
                location: self.detected.clone(),
                language: self.language,
                detail: AuditWarningDetail::UnresolvedAuditRuleCall {
                    rule: rule.clone(),
                    call_site: call_site.clone(),
                },
            },
            SourceProblemKind::AuditRule(AuditRuleDiagnostic::ParseFailure { parser }) => {
                AuditWarning {
                    category: AuditWarningCategory::AuditRuleParseFailure,
                    location: self.detected.clone(),
                    language: self.language,
                    detail: AuditWarningDetail::AuditRuleParseFailure {
                        parser: (*parser).to_owned(),
                    },
                }
            }
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
    fn converts_direct_mod_event_annotation_to_a_structured_warning() {
        let problem = SourceProblem::direct_mod_event_annotation(
            "1.19.2",
            "platform/minecraft/src/main/java/ca/teamdman/sfm/EventHandler.java",
            SourceLineCount(8),
            "SubscribeEvent",
            6,
            5,
        );

        let warning = problem.audit_warning();
        assert_eq!(warning.location.line, 6);
        assert_eq!(warning.location.column, 5);
        assert_eq!(
            warning.location.path,
            "platform/minecraft/src/main/java/ca/teamdman/sfm/EventHandler.java"
        );
    }
}
