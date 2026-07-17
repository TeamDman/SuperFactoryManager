use super::AuditRules;
use super::AuditWarningReport;
use super::AuditedSourceFile;
use super::BranchSourceAuditReport;
use super::SourceAuditOptions;
use super::SourceAuditReport;
use super::SourceLanguage;
use super::SourceLineCount;
use super::SourceProblem;
use super::VersionSurfaceAuditReport;
use super::audit_java_font_render_surface;
use super::audit_version_surfaces;
use crate::branch_targets::WorktreeTarget;
use crate::branch_targets::discover_worktree_targets;
use crate::branch_targets::select_required_worktree_targets;
use crate::terminal_output::stdout_blank_line;
use crate::terminal_output::stdout_line;
use color_eyre::owo_colors::OwoColorize;
use eyre::Context;
use facet_pretty::FacetPretty as _;
use gix::bstr::ByteSlice;
use std::path::Path;
use std::path::PathBuf;

const SFM_PRODUCTION_JAVA_PREFIX: &str = "platform/minecraft/src/main/java/ca/teamdman/sfm/";
const SFM_AUDIT_RULES_PATH: &str = "platform/minecraft/sfm.audit_rules";

#[derive(Debug)]
pub struct SourceAuditCommand {
    options: SourceAuditOptions,
}

impl SourceAuditCommand {
    #[must_use]
    pub fn new(options: SourceAuditOptions) -> Self {
        Self { options }
    }

    /// # Errors
    ///
    /// Returns an error if worktree selection, index loading, source reading, or output writing fails.
    pub fn invoke(self) -> eyre::Result<()> {
        let targets = select_required_worktree_targets(&self.options.branch)?;
        let mut report = SourceAuditReport::default();

        for target in &targets {
            let branch_report = self.audit_target(target)?;
            report.push_branch(branch_report);
        }

        stdout_blank_line()?;
        stdout_line("Source audit by branch:")?;
        for branch in &report.branches {
            stdout_line(format!("  {}", branch.summary_line()))?;
        }

        stdout_blank_line()?;
        stdout_line(report.final_summary_line())?;
        emit_audit_warning_summary(&report)?;

        if self.options.version_surfaces {
            let version_targets = include_version_surface_baseline(targets)?;
            let version_report = audit_version_surfaces(&version_targets)?;
            emit_version_surface_report(&version_report)?;
        }

        Ok(())
    }

    fn audit_target(&self, target: &WorktreeTarget) -> eyre::Result<BranchSourceAuditReport> {
        let branch = target.branch.as_str();
        let worktree_path = target.worktree_path.as_path();
        let repo = gix::discover(worktree_path).wrap_err_with(|| {
            format!(
                "Failed to discover git repository at {}",
                worktree_path.display()
            )
        })?;
        let index = repo.index().wrap_err_with(|| {
            format!(
                "Failed to load git index for {branch} at {}",
                worktree_path.display()
            )
        })?;

        let mut report = BranchSourceAuditReport::new(branch);
        let font_rules = self
            .options
            .font_render_surface
            .then(|| AuditRules::load(&worktree_path.join(SFM_AUDIT_RULES_PATH)))
            .transpose()?;
        for entry in index.entries() {
            if entry.stage() != gix::index::entry::Stage::Unconflicted {
                continue;
            }
            let repo_path = entry.path(&index).to_str_lossy().into_owned();
            let Some(language) = SourceLanguage::from_repo_path(&repo_path) else {
                continue;
            };
            if !self.options.includes_language(language) {
                continue;
            }

            let fs_path = repo_path_to_filesystem_path(worktree_path, &repo_path);
            let content = match std::fs::read_to_string(&fs_path) {
                Ok(content) => content,
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                    tracing::debug!(path = %fs_path.display(), "skipping tracked source deleted from working tree");
                    continue;
                }
                Err(error) => {
                    return Err(error).wrap_err_with(|| {
                        format!("Failed to read tracked source file {}", fs_path.display())
                    });
                }
            };
            let line_count = SourceLineCount::from_text(&content);
            report.push_file(AuditedSourceFile::new(&repo_path, language, line_count));

            if let Some(max_lines) = self.options.max_lines
                && max_lines.is_exceeded_by(line_count)
            {
                report.push_problem(SourceProblem::large_file(
                    branch, &repo_path, language, line_count, max_lines,
                ));
            }

            if language == SourceLanguage::Java && repo_path.starts_with(SFM_PRODUCTION_JAVA_PREFIX)
            {
                audit_direct_mod_event_annotations(
                    &mut report,
                    branch,
                    &repo_path,
                    line_count,
                    &content,
                );
            }

            if language == SourceLanguage::Java
                && is_sfm_java_source(&repo_path)
                && let Some(font_rules) = &font_rules
            {
                audit_java_font_render_surface(
                    &mut report,
                    branch,
                    &repo_path,
                    line_count,
                    &content,
                    font_rules,
                )?;
            }
        }

        Ok(report)
    }
}

fn emit_audit_warning_summary(report: &SourceAuditReport) -> eyre::Result<()> {
    let warning_report = AuditWarningReport::from_warnings(
        report
            .problems()
            .into_iter()
            .map(SourceProblem::audit_warning),
    );
    if warning_report.warning_count == 0 {
        return Ok(());
    }

    stdout_blank_line()?;
    stdout_line(format!(
        "{} {} warning(s) in {} group(s); showing at most {} location(s) per group.",
        "Audit warnings:".yellow().bold(),
        warning_report.warning_count.to_string().yellow().bold(),
        warning_report.group_count.to_string().cyan(),
        AuditWarningReport::examples_per_group().to_string().cyan(),
    ))?;
    for group in warning_report.groups {
        stdout_blank_line()?;
        let omitted_occurrences = if group.omitted_occurrence_count == 0 {
            String::new()
        } else {
            format!(
                " ({} additional occurrence(s) omitted)",
                group.omitted_occurrence_count.to_string().dimmed()
            )
        };
        stdout_line(format!(
            "{} {} — {} occurrence(s){}",
            "WARN".yellow().bold(),
            group.key.title().yellow().bold(),
            group.occurrence_count.to_string().cyan(),
            omitted_occurrences,
        ))?;
        stdout_line(group.header().pretty())?;
        for warning in &group.examples {
            stdout_line(format!(
                "  {} {} {}",
                "at".dimmed(),
                warning.location.cyan(),
                format!("— {}", warning.compact_detail()).dimmed(),
            ))?;
        }
    }
    if warning_report.omitted_group_count != 0 {
        stdout_blank_line()?;
        stdout_line(format!(
            "{} {} additional warning group(s) omitted.",
            "…".dimmed(),
            warning_report.omitted_group_count.to_string().dimmed(),
        ))?;
    }
    Ok(())
}

fn is_sfm_java_source(repo_path: &str) -> bool {
    repo_path.starts_with("platform/minecraft/src/") && repo_path.contains("/java/ca/teamdman/sfm/")
}

fn audit_direct_mod_event_annotations(
    report: &mut BranchSourceAuditReport,
    branch: &str,
    repo_path: &str,
    line_count: SourceLineCount,
    content: &str,
) {
    for (line_index, line) in content.lines().enumerate() {
        let trimmed = line.trim_start();
        let Some(annotation) = direct_mod_event_annotation_name(trimmed) else {
            continue;
        };
        let column = line.len() - trimmed.len() + 1;
        report.push_problem(SourceProblem::direct_mod_event_annotation(
            branch,
            repo_path,
            line_count,
            annotation,
            line_index + 1,
            column,
        ));
    }
}

fn direct_mod_event_annotation_name(line: &str) -> Option<&'static str> {
    let annotation = line.trim_start().strip_prefix('@')?;
    let name = annotation
        .split(|character: char| {
            !(character.is_ascii_alphanumeric() || character == '_' || character == '.')
        })
        .next()?;
    match name.rsplit('.').next()? {
        "EventBusSubscriber" => Some("EventBusSubscriber"),
        "SubscribeEvent" => Some("SubscribeEvent"),
        _ => None,
    }
}

fn include_version_surface_baseline(
    mut targets: Vec<WorktreeTarget>,
) -> eyre::Result<Vec<WorktreeTarget>> {
    if targets
        .iter()
        .any(|target| target.branch.as_str() == "1.19.2")
    {
        return Ok(targets);
    }

    let baseline = discover_worktree_targets()?
        .into_iter()
        .find(|target| target.branch.as_str() == "1.19.2")
        .ok_or_else(|| eyre::eyre!("Version-surface audit requires the 1.19.2 worktree."))?;
    targets.push(baseline);
    Ok(targets)
}

fn emit_version_surface_report(report: &VersionSurfaceAuditReport) -> eyre::Result<()> {
    stdout_blank_line()?;
    stdout_line(format!(
        "Version surface audit: baseline={} branches={} cli-warnings={} java-warnings={}",
        report.baseline_branch,
        report.branches.len(),
        report.cli_warning_count(),
        report.java_warning_count()
    ))?;
    for branch in &report.branches {
        stdout_line(format!(
            "  {} cli-warnings={} java-warnings={}",
            branch.branch,
            branch.cli_warning_count(),
            branch.unbounded_java_changes.len()
        ))?;
        if !branch.cli_source_matches_baseline {
            stdout_line(format!(
                "  WARN CLI source differs from 1.19.2: branch={}",
                branch.branch
            ))?;
        }
        for commit in branch.cli_commits.iter().take(10) {
            stdout_line(format!(
                "  WARN CLI change outside 1.19.2: branch={} commit={} {}",
                branch.branch, commit.id, commit.subject
            ))?;
        }
        for change in branch.unbounded_java_changes.iter().take(20) {
            stdout_line(format!(
                "  WARN Java change outside @MCVersionDependentBehaviour: branch={} path={} base={} target={}",
                branch.branch, change.path, change.base_range, change.target_range
            ))?;
        }
        let shown_cli_warnings =
            branch.cli_commits.len().min(10) + usize::from(!branch.cli_source_matches_baseline);
        let omitted_cli = branch
            .cli_warning_count()
            .saturating_sub(shown_cli_warnings);
        let omitted_java = branch.unbounded_java_changes.len().saturating_sub(20);
        if omitted_cli > 0 || omitted_java > 0 {
            stdout_line(format!(
                "  ... {omitted_cli} CLI and {omitted_java} Java warnings omitted"
            ))?;
        }
    }
    Ok(())
}

fn repo_path_to_filesystem_path(worktree_path: &Path, repo_path: &str) -> PathBuf {
    repo_path
        .split('/')
        .fold(worktree_path.to_path_buf(), |path, component| {
            path.join(component)
        })
}

#[cfg(test)]
mod tests {
    use super::SourceAuditCommand;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::BranchQuery;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::source_audit::AuditWarningDetail;
    use crate::source_audit::SourceAuditOptions;
    use crate::source_audit::SourceLineLimit;
    use eyre::Context;
    use std::fs;
    use std::process::Command;

    #[test]
    fn gix_index_scan_audits_tracked_files_and_ignores_untracked_files() -> eyre::Result<()> {
        let temp = tempfile::tempdir()?;
        let root = temp.path();
        fs::write(root.join("tracked.rs"), "fn tracked() {}\n")?;
        fs::write(root.join("untracked.rs"), "fn untracked() {}\n")?;
        run_git(root, &["init"])?;
        run_git(root, &["add", "tracked.rs"])?;

        let command = SourceAuditCommand::new(SourceAuditOptions {
            branch: BranchQuery::parse("*")?,
            languages: Vec::new(),
            max_lines: Some(SourceLineLimit(1)),
            version_surfaces: false,
            font_render_surface: false,
        });
        let target = WorktreeTarget::from_parts(
            BranchName::from("1.19.2"),
            WorktreePath::from(root.to_path_buf()),
        )?;

        let report = command.audit_target(&target)?;
        let audited_paths = report
            .audited_files
            .iter()
            .map(|file| file.repo_path.as_str())
            .collect::<Vec<_>>();

        assert_eq!(audited_paths, vec!["tracked.rs"]);
        Ok(())
    }

    #[test]
    fn gix_index_scan_skips_tracked_files_deleted_from_the_working_tree() -> eyre::Result<()> {
        let temp = tempfile::tempdir()?;
        let root = temp.path();
        let deleted_path = root.join("deleted.rs");
        fs::write(&deleted_path, "fn deleted() {}\n")?;
        run_git(root, &["init"])?;
        run_git(root, &["add", "deleted.rs"])?;
        fs::remove_file(deleted_path)?;

        let command = SourceAuditCommand::new(SourceAuditOptions {
            branch: BranchQuery::parse("*")?,
            languages: Vec::new(),
            max_lines: None,
            version_surfaces: false,
            font_render_surface: false,
        });
        let target = WorktreeTarget::from_parts(
            BranchName::from("1.19.2"),
            WorktreePath::from(root.to_path_buf()),
        )?;

        let report = command.audit_target(&target)?;
        assert!(report.audited_files.is_empty());
        Ok(())
    }

    #[test]
    fn source_size_warnings_require_an_explicit_max_lines_option() -> eyre::Result<()> {
        let temp = tempfile::tempdir()?;
        let root = temp.path();
        fs::write(root.join("tracked.rs"), "fn first() {}\nfn second() {}\n")?;
        run_git(root, &["init"])?;
        run_git(root, &["add", "tracked.rs"])?;
        let target = WorktreeTarget::from_parts(
            BranchName::from("1.19.2"),
            WorktreePath::from(root.to_path_buf()),
        )?;

        let no_limit_report = SourceAuditCommand::new(SourceAuditOptions {
            branch: BranchQuery::parse("*")?,
            languages: Vec::new(),
            max_lines: None,
            version_surfaces: false,
            font_render_surface: false,
        })
        .audit_target(&target)?;
        assert!(no_limit_report.problems.is_empty());

        let limited_report = SourceAuditCommand::new(SourceAuditOptions {
            branch: BranchQuery::parse("*")?,
            languages: Vec::new(),
            max_lines: Some(SourceLineLimit(1)),
            version_surfaces: false,
            font_render_surface: false,
        })
        .audit_target(&target)?;
        assert_eq!(limited_report.problems.len(), 1);
        assert!(matches!(
            limited_report.problems[0].audit_warning().detail,
            AuditWarningDetail::SourceFileTooLarge {
                line_count: 2,
                maximum_line_count: 1,
            }
        ));
        Ok(())
    }

    #[test]
    fn reports_direct_mod_event_annotations_but_allows_sfm_wrapper() -> eyre::Result<()> {
        let temp = tempfile::tempdir()?;
        let root = temp.path();
        let java_path =
            root.join("platform/minecraft/src/main/java/ca/teamdman/sfm/EventHandler.java");
        fs::create_dir_all(
            java_path
                .parent()
                .expect("Java source should have a parent"),
        )?;
        fs::write(
            &java_path,
            r"
            package ca.teamdman.sfm;

            @SFMSubscribeEvent
            class WrappedHandler {}

            @Mod.EventBusSubscriber
            class DirectBusHandler {
                @SubscribeEvent
                void onEvent() {}
            }

            // @SubscribeEvent is not an annotation.
            ",
        )?;
        run_git(root, &["init"])?;
        run_git(root, &["add", "platform"])?;

        let command = SourceAuditCommand::new(SourceAuditOptions {
            branch: BranchQuery::parse("*")?,
            languages: Vec::new(),
            max_lines: None,
            version_surfaces: false,
            font_render_surface: false,
        });
        let target = WorktreeTarget::from_parts(
            BranchName::from("1.19.2"),
            WorktreePath::from(root.to_path_buf()),
        )?;

        let report = command.audit_target(&target)?;
        assert_eq!(report.problems.len(), 2);
        let warnings = report
            .problems
            .iter()
            .map(|problem| problem.audit_warning())
            .collect::<Vec<_>>();
        assert!(warnings.iter().any(|warning| matches!(
            &warning.detail,
            AuditWarningDetail::DirectModEventAnnotation { annotation, .. }
                if annotation == "EventBusSubscriber"
        )));
        assert!(warnings.iter().any(|warning| matches!(
            &warning.detail,
            AuditWarningDetail::DirectModEventAnnotation { annotation, .. }
                if annotation == "SubscribeEvent"
        )));
        assert!(warnings.iter().all(|warning| matches!(
            &warning.detail,
            AuditWarningDetail::DirectModEventAnnotation {
                required_replacement,
                ..
            } if required_replacement == "SFMSubscribeEvent"
        )));
        Ok(())
    }

    #[test]
    fn applies_the_shared_rule_file_to_gametest_java_sources() -> eyre::Result<()> {
        let temp = tempfile::tempdir()?;
        let root = temp.path();
        let rules_path = root.join("platform/minecraft/sfm.audit_rules");
        fs::create_dir_all(rules_path.parent().expect("rules should have a parent"))?;
        fs::write(
            &rules_path,
            "DENY CALL net.minecraft.client.gui.GuiGraphics drawString *\n",
        )?;
        let java_path =
            root.join("platform/minecraft/src/gametest/java/ca/teamdman/sfm/CaptionPuppet.java");
        fs::create_dir_all(java_path.parent().expect("source should have a parent"))?;
        fs::write(
            &java_path,
            r#"
            package ca.teamdman.sfm;
            import net.minecraft.client.gui.GuiGraphics;
            final class CaptionPuppet {
                void capture(GuiGraphics graphics) {
                    graphics.drawString(null, "caption", 0, 0, 0);
                }
            }
            "#,
        )?;
        run_git(root, &["init"])?;
        run_git(root, &["add", "platform"])?;

        let command = SourceAuditCommand::new(SourceAuditOptions {
            branch: BranchQuery::parse("*")?,
            languages: Vec::new(),
            max_lines: None,
            version_surfaces: false,
            font_render_surface: true,
        });
        let target = WorktreeTarget::from_parts(
            BranchName::from("1.19.2"),
            WorktreePath::from(root.to_path_buf()),
        )?;

        let report = command.audit_target(&target)?;
        assert_eq!(report.problems.len(), 1);
        let warning = report.problems[0].audit_warning();
        assert!(warning.location.path.ends_with("CaptionPuppet.java"));
        assert!(matches!(
            warning.detail,
            AuditWarningDetail::AuditRuleViolation { callee, .. }
                if callee.contains("GuiGraphics drawString")
        ));
        Ok(())
    }

    fn run_git(cwd: &std::path::Path, args: &[&str]) -> eyre::Result<()> {
        let output = Command::new("git")
            .args(args)
            .current_dir(cwd)
            .output()
            .wrap_err_with(|| format!("Failed to run git {}", args.join(" ")))?;
        if !output.status.success() {
            eyre::bail!(
                "git {} failed: {}",
                args.join(" "),
                String::from_utf8_lossy(&output.stderr)
            );
        }
        Ok(())
    }
}
