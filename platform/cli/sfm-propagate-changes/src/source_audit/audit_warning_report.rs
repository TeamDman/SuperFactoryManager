use super::DetectedSourceLocation;
use super::SourceLanguage;
use facet::Facet;
use std::collections::BTreeMap;

const EXAMPLES_PER_GROUP: usize = 3;
const GROUPS_TO_RENDER: usize = 12;
const DETAIL_CHARACTER_LIMIT: usize = 240;

/// A user-facing audit warning with a precise source location and structured detail.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct AuditWarning {
    pub category: AuditWarningCategory,
    pub location: DetectedSourceLocation,
    pub language: SourceLanguage,
    pub detail: AuditWarningDetail,
}

impl AuditWarning {
    #[must_use]
    pub fn compact_detail(&self) -> String {
        match &self.detail {
            AuditWarningDetail::SourceFileTooLarge {
                line_count,
                maximum_line_count,
            } => format!("lines={line_count} maximum_line_count={maximum_line_count}"),
            AuditWarningDetail::DirectModEventAnnotation {
                annotation,
                required_replacement,
            } => format!("annotation=@{annotation} required_replacement=@{required_replacement}"),
            AuditWarningDetail::AuditRuleViolation { callee, caller, .. } => format!(
                "callee={} caller={}",
                compact_text(callee),
                compact_text(caller),
            ),
            AuditWarningDetail::UnresolvedAuditRuleCall {
                receiver, caller, ..
            } => format!(
                "receiver={} caller={}",
                compact_text(receiver),
                compact_text(caller),
            ),
            AuditWarningDetail::AuditRuleParseFailure { parser } => {
                format!("parser={parser}")
            }
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[repr(u8)]
pub(crate) enum AuditWarningCategory {
    SourceFileTooLarge,
    DirectModEventAnnotation,
    AuditRuleViolation,
    UnresolvedAuditRuleCall,
    AuditRuleParseFailure,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[repr(C)]
pub(crate) enum AuditWarningDetail {
    SourceFileTooLarge {
        line_count: usize,
        maximum_line_count: usize,
    },
    DirectModEventAnnotation {
        annotation: String,
        required_replacement: String,
    },
    AuditRuleViolation {
        rule: String,
        callee: String,
        caller: String,
    },
    UnresolvedAuditRuleCall {
        rule: String,
        member: String,
        receiver: String,
        caller: String,
    },
    AuditRuleParseFailure {
        parser: String,
    },
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[repr(C)]
pub(crate) enum AuditWarningGroupKey {
    SourceFileTooLarge { maximum_line_count: usize },
    DirectModEventAnnotation { annotation: String },
    AuditRuleViolation { rule: String },
    UnresolvedAuditRuleCall { rule: String, member: String },
    AuditRuleParseFailure { parser: String },
}

impl AuditWarningGroupKey {
    fn from_warning(warning: &AuditWarning) -> Self {
        match &warning.detail {
            AuditWarningDetail::SourceFileTooLarge {
                maximum_line_count, ..
            } => Self::SourceFileTooLarge {
                maximum_line_count: *maximum_line_count,
            },
            AuditWarningDetail::DirectModEventAnnotation { annotation, .. } => {
                Self::DirectModEventAnnotation {
                    annotation: annotation.clone(),
                }
            }
            AuditWarningDetail::AuditRuleViolation { rule, .. } => {
                Self::AuditRuleViolation { rule: rule.clone() }
            }
            AuditWarningDetail::UnresolvedAuditRuleCall { rule, member, .. } => {
                Self::UnresolvedAuditRuleCall {
                    rule: rule.clone(),
                    member: member.clone(),
                }
            }
            AuditWarningDetail::AuditRuleParseFailure { parser } => Self::AuditRuleParseFailure {
                parser: parser.clone(),
            },
        }
    }

    #[must_use]
    pub fn title(&self) -> &'static str {
        match self {
            Self::SourceFileTooLarge { .. } => "Source file too large",
            Self::DirectModEventAnnotation { .. } => "Direct mod event annotation",
            Self::AuditRuleViolation { .. } => "Audit rule violation",
            Self::UnresolvedAuditRuleCall { .. } => "Unresolved audit rule call",
            Self::AuditRuleParseFailure { .. } => "Audit rule parse failure",
        }
    }
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct AuditWarningGroup {
    pub key: AuditWarningGroupKey,
    pub occurrence_count: usize,
    pub examples: Vec<AuditWarning>,
    pub omitted_occurrence_count: usize,
}

impl AuditWarningGroup {
    #[must_use]
    pub fn header(&self) -> AuditWarningGroupHeader {
        AuditWarningGroupHeader {
            key: self.key.clone(),
            occurrence_count: self.occurrence_count,
            omitted_occurrence_count: self.omitted_occurrence_count,
        }
    }
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct AuditWarningGroupHeader {
    pub key: AuditWarningGroupKey,
    pub occurrence_count: usize,
    pub omitted_occurrence_count: usize,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct AuditWarningReport {
    pub warning_count: usize,
    pub group_count: usize,
    pub groups: Vec<AuditWarningGroup>,
    pub omitted_group_count: usize,
}

fn compact_text(value: &str) -> String {
    let compacted = value.split_whitespace().collect::<Vec<_>>().join(" ");
    if compacted.chars().count() <= DETAIL_CHARACTER_LIMIT {
        return compacted;
    }
    let prefix = compacted
        .chars()
        .take(DETAIL_CHARACTER_LIMIT.saturating_sub(1))
        .collect::<String>();
    format!("{prefix}…")
}

impl AuditWarningReport {
    #[must_use]
    pub fn from_warnings(warnings: impl IntoIterator<Item = AuditWarning>) -> Self {
        let warnings = warnings.into_iter().collect::<Vec<_>>();
        let warning_count = warnings.len();
        let mut warnings_by_group = BTreeMap::<AuditWarningGroupKey, Vec<AuditWarning>>::new();
        for warning in warnings {
            warnings_by_group
                .entry(AuditWarningGroupKey::from_warning(&warning))
                .or_default()
                .push(warning);
        }

        let group_count = warnings_by_group.len();
        let mut groups = warnings_by_group
            .into_iter()
            .map(|(key, mut warnings)| {
                warnings.sort_by(|left, right| {
                    left.location
                        .branch
                        .cmp(&right.location.branch)
                        .then_with(|| left.location.path.cmp(&right.location.path))
                        .then_with(|| left.location.line.cmp(&right.location.line))
                        .then_with(|| left.location.column.cmp(&right.location.column))
                });
                let occurrence_count = warnings.len();
                let examples = warnings
                    .into_iter()
                    .take(EXAMPLES_PER_GROUP)
                    .collect::<Vec<_>>();
                AuditWarningGroup {
                    key,
                    occurrence_count,
                    omitted_occurrence_count: occurrence_count - examples.len(),
                    examples,
                }
            })
            .collect::<Vec<_>>();
        groups.sort_by(|left, right| {
            right
                .occurrence_count
                .cmp(&left.occurrence_count)
                .then_with(|| left.key.cmp(&right.key))
        });

        let omitted_group_count = group_count.saturating_sub(GROUPS_TO_RENDER);
        groups.truncate(GROUPS_TO_RENDER);
        Self {
            warning_count,
            group_count,
            groups,
            omitted_group_count,
        }
    }

    #[must_use]
    pub const fn examples_per_group() -> usize {
        EXAMPLES_PER_GROUP
    }
}

#[cfg(test)]
mod tests {
    use super::AuditWarning;
    use super::AuditWarningCategory;
    use super::AuditWarningDetail;
    use super::AuditWarningReport;
    use super::DETAIL_CHARACTER_LIMIT;
    use super::compact_text;
    use crate::source_audit::DetectedSourceLocation;
    use crate::source_audit::SourceLanguage;

    fn unresolved_warning(branch: &str, line: usize, member: &str) -> AuditWarning {
        AuditWarning {
            category: AuditWarningCategory::UnresolvedAuditRuleCall,
            location: DetectedSourceLocation::new(branch, "src/Test.java", line, 9),
            language: SourceLanguage::Java,
            detail: AuditWarningDetail::UnresolvedAuditRuleCall {
                rule: "DENY CALL example.Type draw *".to_owned(),
                member: member.to_owned(),
                receiver: "receiver".to_owned(),
                caller: "caller".to_owned(),
            },
        }
    }

    #[test]
    fn groups_similar_warnings_and_truncates_examples() {
        let report = AuditWarningReport::from_warnings([
            unresolved_warning("1.19.2", 10, "draw"),
            unresolved_warning("1.19.2", 20, "draw"),
            unresolved_warning("1.20", 30, "draw"),
            unresolved_warning("1.20", 40, "draw"),
            unresolved_warning("1.20", 50, "text"),
        ]);

        assert_eq!(report.warning_count, 5);
        assert_eq!(report.group_count, 2);
        assert_eq!(report.groups[0].occurrence_count, 4);
        assert_eq!(
            report.groups[0].examples.len(),
            AuditWarningReport::examples_per_group()
        );
        assert_eq!(report.groups[0].omitted_occurrence_count, 1);
        assert_eq!(report.groups[1].occurrence_count, 1);
    }

    #[test]
    fn compacts_whitespace_and_truncates_long_context() {
        let input = format!("receiver\n\t{}", "x".repeat(DETAIL_CHARACTER_LIMIT));
        let compacted = compact_text(&input);

        assert!(compacted.starts_with("receiver x"));
        assert!(compacted.ends_with('…'));
        assert_eq!(compacted.chars().count(), DETAIL_CHARACTER_LIMIT);
    }
}
