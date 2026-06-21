// todo(2026-06-16) file very large
use super::ArtifactAuditOptions;
use super::ArtifactId;
use super::ArtifactPurpose;
use super::BuildMode;
use super::BuildOptions;
use super::CompareOptions;
use super::RunKind;
use super::RunTestAction;
use super::RunTestOptions;
pub(super) use super::artifact_audit_issue_kind::ArtifactAuditIssueKind;
pub(super) use super::artifact_audit_report::ArtifactAuditReport;
pub(super) use super::artifact_audit_severity::ArtifactAuditSeverity;
use super::hash::ContentHash;
use super::hash::ContentHashAlgorithm;
use super::json_branch_name::JsonBranchName;
use super::json_minecraft_version::JsonMinecraftVersion;
use super::json_path::JsonOptionalPath;
use super::json_path::JsonPath;
pub(super) use super::target_artifact_audit_report::TargetArtifactAuditReport;
use crate::artifact_lock::ArtifactLock;
use crate::artifact_lock::ArtifactReadLock;
use crate::branch_targets::BranchName;
use crate::branch_targets::MinecraftVersion;
use crate::branch_targets::WorktreeTarget;
use crate::branch_targets::select_required_worktree_targets;
use crate::cancellation::CancellationToken;
use crate::colour::stable_color;
use crate::paths::CACHE_DIR;
use base64::Engine as _;
use base64::engine::general_purpose::STANDARD as BASE64_STANDARD;
use chrono::Local;
use color_eyre::owo_colors::OwoColorize;
use eyre::Context;
use facet::Facet;
use rayon::prelude::*;
use reqwest::StatusCode;
use reqwest::blocking::Client;
use std::cmp::Ordering;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::collections::VecDeque;
use std::fmt::Write as _;
use std::fs;
use std::fs::File;
use std::fs::OpenOptions;
use std::io::BufRead;
use std::io::BufReader;
use std::io::Cursor;
use std::io::Read;
use std::io::Seek;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use std::process::ExitStatus;
use std::process::Stdio;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::AtomicUsize;
use std::sync::atomic::Ordering as AtomicOrdering;
use std::sync::mpsc;
use std::thread;
use std::time::Duration;
use std::time::Instant;
use std::time::SystemTime;
use std::time::UNIX_EPOCH;
use tracing::info_span;
use tracing::instrument;
use zip::CompressionMethod;
use zip::ZipArchive;
use zip::ZipWriter;
use zip::write::SimpleFileOptions;

#[path = "resolve.rs"]
mod resolve;

use self::resolve::Resolver;
use self::resolve::maven_cache_path_for;

const VERSION_MANIFEST_URL: &str =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const NEOFORM_RUNTIME_COORDINATE: &str = "net.neoforged:neoform-runtime:2.0.19:all";
const PROJECT_COMPILE_ANNOTATION_COORDINATES: [(&str, &str); 2] = [
    (
        "project-compile-annotations",
        "org.jetbrains:annotations:24.0.1",
    ),
    (
        "project-compile-jsr305",
        "com.google.code.findbugs:jsr305:3.0.2",
    ),
];
const DOWNLOAD_RETRY_ATTEMPTS: usize = 3;

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %options.branch,
        mode = ?options.mode,
        refresh = options.refresh,
        explain_rebuild = options.explain_rebuild,
        dry_run = options.dry_run,
        allow_local_artifact_cache = options.allow_local_artifact_cache,
        require_portable_artifacts = options.require_portable_artifacts,
        error_action = %options.error_action,
        parallelism = %options.parallelism,
    )
)]
pub(crate) fn invoke_build(
    options: &BuildOptions,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let targets = resolve_build_targets(options)?;
    cancellation_token.bail_if_cancelled()?;
    let target_count = targets.len();
    let TargetExecutionSummary {
        plans,
        failures,
        reports,
    } = execute_build_targets(options, targets, cancellation_token)?;
    cancellation_token.bail_if_cancelled()?;

    write_requested_plan_outputs(&plans, options.plan_json.as_deref())?;
    finish_target_summary(
        build_action_name(options),
        target_count,
        plans.len(),
        &failures,
        &reports,
    )
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %options.branch,
        kind = kind.command_name(),
        refresh = options.refresh,
        explain_rebuild = options.explain_rebuild,
        dry_run = options.dry_run,
        allow_local_artifact_cache = options.allow_local_artifact_cache,
        require_portable_artifacts = options.require_portable_artifacts,
        error_action = %options.error_action,
        parallelism = %options.parallelism,
    )
)]
pub(crate) fn invoke_run(
    options: &BuildOptions,
    kind: RunKind,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let targets = resolve_build_targets(options)?;
    cancellation_token.bail_if_cancelled()?;
    let target_count = targets.len();
    let TargetExecutionSummary {
        plans,
        failures,
        reports,
    } = execute_run_targets(options, kind, targets, cancellation_token)?;
    cancellation_token.bail_if_cancelled()?;

    write_requested_plan_outputs(&plans, options.plan_json.as_deref())?;
    finish_target_summary(
        kind.command_name(),
        target_count,
        plans.len(),
        &failures,
        &reports,
    )
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %options.branch,
        action = ?test_options.action,
        filter = test_options.filter.as_deref().unwrap_or(""),
        no_capture = test_options.no_capture,
        refresh = options.refresh,
        explain_rebuild = options.explain_rebuild,
        dry_run = options.dry_run,
        allow_local_artifact_cache = options.allow_local_artifact_cache,
        require_portable_artifacts = options.require_portable_artifacts,
        error_action = %options.error_action,
        parallelism = %options.parallelism,
    )
)]
pub(crate) fn invoke_run_test(
    options: &BuildOptions,
    test_options: &RunTestOptions,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let targets = resolve_build_targets(options)?;
    cancellation_token.bail_if_cancelled()?;
    let target_count = targets.len();
    let TargetExecutionSummary {
        plans,
        failures,
        reports,
    } = execute_run_test_targets(options, test_options, targets, cancellation_token)?;
    cancellation_token.bail_if_cancelled()?;

    write_requested_plan_outputs(&plans, options.plan_json.as_deref())?;
    let action_name = match test_options.action {
        RunTestAction::Run => RunKind::Test.command_name(),
        RunTestAction::List => "runTest list",
        RunTestAction::Compile => "runCompile",
    };
    finish_target_summary(action_name, target_count, plans.len(), &failures, &reports)
}

fn execute_build_targets(
    options: &BuildOptions,
    targets: Vec<WorktreeTarget>,
    cancellation_token: &CancellationToken,
) -> eyre::Result<TargetExecutionSummary> {
    execute_targets(
        options,
        targets,
        "sfm_jar_build_target",
        cancellation_token,
        |options, target, cancellation_token| {
            let _target_span = tracing::info_span!(
                "sfm_jar_build_target",
                branch = %target.branch,
                worktree = %target.worktree_path.display(),
            )
            .entered();
            execute_build_target(options, target, cancellation_token)
        },
    )
}

fn execute_run_targets(
    options: &BuildOptions,
    kind: RunKind,
    targets: Vec<WorktreeTarget>,
    cancellation_token: &CancellationToken,
) -> eyre::Result<TargetExecutionSummary> {
    execute_targets(
        options,
        targets,
        "sfm_run_target",
        cancellation_token,
        |options, target, cancellation_token| {
            let _target_span = tracing::info_span!(
                "sfm_run_target",
                branch = %target.branch,
                worktree = %target.worktree_path.display(),
                kind = kind.command_name(),
            )
            .entered();
            execute_run_target(options, kind, target, cancellation_token)
        },
    )
}

fn execute_run_test_targets(
    options: &BuildOptions,
    test_options: &RunTestOptions,
    targets: Vec<WorktreeTarget>,
    cancellation_token: &CancellationToken,
) -> eyre::Result<TargetExecutionSummary> {
    execute_targets(
        options,
        targets,
        "sfm_run_test_target",
        cancellation_token,
        |options, target, cancellation_token| {
            let _target_span = tracing::info_span!(
                "sfm_run_test_target",
                branch = %target.branch,
                worktree = %target.worktree_path.display(),
            )
            .entered();
            execute_run_test_target(options, test_options, target, cancellation_token)
        },
    )
}

fn execute_targets(
    options: &BuildOptions,
    targets: Vec<WorktreeTarget>,
    action: &'static str,
    cancellation_token: &CancellationToken,
    execute: impl Fn(&BuildOptions, &WorktreeTarget, &CancellationToken) -> eyre::Result<BuildPlan>
    + Send
    + Sync,
) -> eyre::Result<TargetExecutionSummary> {
    let Some(limit) = options.parallelism.limit() else {
        return execute_targets_sequential(options, targets, cancellation_token, execute);
    };
    execute_targets_parallel(options, targets, action, limit, cancellation_token, execute)
}

fn execute_targets_sequential(
    options: &BuildOptions,
    targets: Vec<WorktreeTarget>,
    cancellation_token: &CancellationToken,
    execute: impl Fn(&BuildOptions, &WorktreeTarget, &CancellationToken) -> eyre::Result<BuildPlan>,
) -> eyre::Result<TargetExecutionSummary> {
    let mut plans = Vec::new();
    let mut failures = Vec::new();
    let mut reports = Vec::new();

    for target in targets {
        cancellation_token.bail_if_cancelled()?;
        let started_at = SystemTime::now();
        let started = Instant::now();
        let result = execute(options, &target, cancellation_token);
        let report =
            TargetExecutionReport::from_result(&target, started_at, started.elapsed(), &result);
        reports.push(report);
        match result {
            Ok(plan) => plans.push(plan),
            Err(error) => {
                tracing::error!(error = %error, "target_failed");
                failures.push(TargetFailure::new(&target, &error));
                if !options.error_action.should_continue() {
                    break;
                }
            }
        }
        cancellation_token.bail_if_cancelled()?;
    }
    cancellation_token.bail_if_cancelled()?;

    Ok(TargetExecutionSummary {
        plans,
        failures,
        reports,
    })
}

fn execute_targets_parallel(
    options: &BuildOptions,
    targets: Vec<WorktreeTarget>,
    action: &'static str,
    limit: usize,
    cancellation_token: &CancellationToken,
    execute: impl Fn(&BuildOptions, &WorktreeTarget, &CancellationToken) -> eyre::Result<BuildPlan>
    + Send
    + Sync,
) -> eyre::Result<TargetExecutionSummary> {
    execute_targets_parallel_with_cancellation(
        options,
        targets,
        action,
        limit,
        execute,
        cancellation_token,
    )
}

fn execute_targets_parallel_with_cancellation(
    options: &BuildOptions,
    targets: Vec<WorktreeTarget>,
    action: &'static str,
    limit: usize,
    execute: impl Fn(&BuildOptions, &WorktreeTarget, &CancellationToken) -> eyre::Result<BuildPlan>
    + Send
    + Sync,
    cancellation_token: &CancellationToken,
) -> eyre::Result<TargetExecutionSummary> {
    if targets.is_empty() {
        return Ok(TargetExecutionSummary::default());
    }

    let target_count = targets.len();
    let worker_count = limit.min(target_count);
    tracing::info!(
        action,
        worker_count,
        target_count,
        error_action = %options.error_action,
        "parallel target execution starting"
    );

    let queue = Arc::new(Mutex::new(
        targets.into_iter().enumerate().collect::<VecDeque<_>>(),
    ));
    let stop_starting = Arc::new(AtomicBool::new(false));
    let (sender, receiver) = mpsc::channel::<TargetExecutionResult>();

    thread::scope(|scope| {
        for worker_index in 0..worker_count {
            let queue = Arc::clone(&queue);
            let stop_starting = Arc::clone(&stop_starting);
            let sender = sender.clone();
            let execute = &execute;
            let cancellation_token = cancellation_token.clone();
            scope.spawn(move || {
                loop {
                    if stop_starting.load(AtomicOrdering::Acquire)
                        || cancellation_token.is_cancelled()
                    {
                        break;
                    }
                    let target = {
                        let mut queue = queue.lock().expect("target queue should not be poisoned");
                        queue.pop_front()
                    };
                    let Some((target_index, target)) = target else {
                        break;
                    };

                    let _worker_span = tracing::info_span!(
                        "sfm_parallel_worker",
                        worker = worker_index,
                        branch = %target.branch,
                        worktree = %target.worktree_path.display(),
                    )
                    .entered();
                    let started_at = SystemTime::now();
                    let started = Instant::now();
                    let result = execute(options, &target, &cancellation_token);
                    let report = TargetExecutionReport::from_result(
                        &target,
                        started_at,
                        started.elapsed(),
                        &result,
                    );
                    let failed = result.is_err();
                    if failed && !options.error_action.should_continue() {
                        stop_starting.store(true, AtomicOrdering::Release);
                    }
                    if sender
                        .send(TargetExecutionResult {
                            target_index,
                            target,
                            report,
                            result,
                        })
                        .is_err()
                    {
                        break;
                    }
                }
            });
        }
        drop(sender);

        let mut results = receiver.into_iter().collect::<Vec<_>>();
        results.sort_by_key(|result| result.target_index);

        let mut plans = Vec::new();
        let mut failures = Vec::new();
        let mut reports = Vec::new();
        for result in results {
            reports.push(result.report);
            match result.result {
                Ok(plan) => plans.push(plan),
                Err(error) => {
                    tracing::error!(
                        branch = %result.target.branch,
                        error = %error,
                        "target_failed"
                    );
                    failures.push(TargetFailure::new(&result.target, &error));
                }
            }
        }

        cancellation_token.bail_if_cancelled()?;

        Ok(TargetExecutionSummary {
            plans,
            failures,
            reports,
        })
    })
}

fn execute_build_target(
    options: &BuildOptions,
    target: &WorktreeTarget,
    cancellation_token: &CancellationToken,
) -> eyre::Result<BuildPlan> {
    cancellation_token.bail_if_cancelled()?;
    let plan = create_plan_for_target(options, target, cancellation_token)?;
    cancellation_token.bail_if_cancelled()?;
    write_last_plan_output(&plan)?;
    print_plan_summary(&plan);
    cancellation_token.bail_if_cancelled()?;

    match options.mode {
        BuildMode::Plan => write_artifact_lockfile(&plan)?,
        BuildMode::Build if options.dry_run => {
            tracing::info!(
                "Jar build dry-run: resolved plan and lockfile; skipped build execution."
            );
            tracing::info!("jar_build_dry_run_skip_execution");
            write_artifact_lockfile(&plan)?;
        }
        BuildMode::Build => {
            execute_build(
                &plan,
                options.explain_rebuild,
                BuildTarget::Jar,
                cancellation_token,
            )?;
            cancellation_token.bail_if_cancelled()?;
            write_artifact_lockfile(&plan)?;
        }
    }

    Ok(plan)
}

fn execute_run_target(
    options: &BuildOptions,
    kind: RunKind,
    target: &WorktreeTarget,
    cancellation_token: &CancellationToken,
) -> eyre::Result<BuildPlan> {
    cancellation_token.bail_if_cancelled()?;
    let plan = create_plan_for_target(options, target, cancellation_token)?;
    cancellation_token.bail_if_cancelled()?;
    write_last_plan_output(&plan)?;
    print_plan_summary(&plan);
    cancellation_token.bail_if_cancelled()?;
    execute_build(
        &plan,
        options.explain_rebuild,
        BuildTarget::Run,
        cancellation_token,
    )?;
    cancellation_token.bail_if_cancelled()?;
    write_artifact_lockfile(&plan)?;
    cancellation_token.bail_if_cancelled()?;
    execute_run(&plan, kind, options.dry_run, cancellation_token)?;
    Ok(plan)
}

fn execute_run_test_target(
    options: &BuildOptions,
    test_options: &RunTestOptions,
    target: &WorktreeTarget,
    cancellation_token: &CancellationToken,
) -> eyre::Result<BuildPlan> {
    cancellation_token.bail_if_cancelled()?;
    let plan = create_plan_for_target(options, target, cancellation_token)?;
    cancellation_token.bail_if_cancelled()?;
    write_last_plan_output(&plan)?;
    print_plan_summary(&plan);
    cancellation_token.bail_if_cancelled()?;
    execute_build(
        &plan,
        options.explain_rebuild,
        BuildTarget::Run,
        cancellation_token,
    )?;
    cancellation_token.bail_if_cancelled()?;
    write_artifact_lockfile(&plan)?;
    cancellation_token.bail_if_cancelled()?;
    execute_junit_tests(&plan, options.dry_run, test_options, cancellation_token)?;
    Ok(plan)
}

fn build_action_name(options: &BuildOptions) -> &'static str {
    match options.mode {
        BuildMode::Plan => "jar plan",
        BuildMode::Build => "jar build",
    }
}

fn finish_target_summary(
    action_name: &str,
    total: usize,
    succeeded: usize,
    failures: &[TargetFailure],
    reports: &[TargetExecutionReport],
) -> eyre::Result<()> {
    if total > 1 || !failures.is_empty() {
        tracing::info!(
            "{action_name} target summary: {succeeded}/{total} succeeded, {} failed.",
            failures.len()
        );
    }
    emit_target_report_matrix(action_name, reports);

    if failures.is_empty() {
        return Ok(());
    }

    tracing::info!("{}", target_report_separator());
    for failure in failures {
        tracing::info!(
            "Failed target {} ({}): {}",
            format_branch_name(&failure.branch),
            failure.worktree_path.display(),
            failure.error
        );
    }

    eyre::bail!(
        "{action_name} failed for {} of {total} target(s).",
        failures.len()
    );
}

fn emit_target_report_matrix(action_name: &str, reports: &[TargetExecutionReport]) {
    if reports.is_empty() {
        return;
    }

    let branch_width = reports
        .iter()
        .map(|report| report.branch.to_string().len())
        .max()
        .unwrap_or("branch".len())
        .max("branch".len());
    tracing::info!("{action_name} target report:");
    tracing::info!("{}", format_report_header(branch_width));
    for report in reports {
        let failed = report.bail_message.is_some();
        let duration = format_report_duration(report.duration);
        let message = report
            .bail_message
            .as_deref()
            .map(report_message)
            .unwrap_or_default();
        tracing::info!(
            "{}",
            format!(
                "{}  {}  {}  {}  {}  {}",
                format_branch_cell(&report.branch, branch_width),
                format_status_cell(failed),
                format!("{duration:>9}"),
                format_warning_count_cell(report.warning_count),
                format_error_count_cell(report.error_count),
                format_report_message_cell(&message, failed),
            )
        );
    }
}

fn format_report_header(branch_width: usize) -> String {
    format!(
        "{}  {}  {}  {}  {}  {}",
        format!("{:<branch_width$}", "branch", branch_width = branch_width).dimmed(),
        format!("{:<6}", "status").dimmed(),
        format!("{:>9}", "time").dimmed(),
        format!("{:>8}", "warnings").dimmed(),
        format!("{:>6}", "errors").dimmed(),
        "message".dimmed(),
    )
}

fn format_branch_name(branch: &BranchName) -> String {
    let branch = branch.to_string();
    branch.color(stable_color(&branch)).bold().to_string()
}

fn format_branch_cell(branch: &BranchName, width: usize) -> String {
    let branch = branch.to_string();
    format!("{branch:<width$}")
        .color(stable_color(&branch))
        .bold()
        .to_string()
}

fn format_status_cell(failed: bool) -> String {
    if failed {
        format!("{:<6}", "failed").red().bold().to_string()
    } else {
        format!("{:<6}", "ok").green().bold().to_string()
    }
}

fn format_warning_count_cell(count: usize) -> String {
    let text = format!("{count:>8}");
    if count == 0 {
        text.dimmed().to_string()
    } else {
        text.yellow().bold().to_string()
    }
}

fn format_error_count_cell(count: usize) -> String {
    let text = format!("{count:>6}");
    if count == 0 {
        text.dimmed().to_string()
    } else {
        text.red().bold().to_string()
    }
}

fn format_report_message_cell(message: &str, failed: bool) -> String {
    if failed {
        message.red().to_string()
    } else if message.is_empty() {
        message.dimmed().to_string()
    } else {
        message.to_string()
    }
}

fn target_report_separator() -> String {
    "-".repeat(96).dimmed().to_string()
}

fn format_report_duration(duration: Duration) -> String {
    let millis = duration.as_millis();
    if millis < 1_000 {
        return format!("{millis}ms");
    }
    if millis < 60_000 {
        let tenths = (millis + 50) / 100;
        return format!("{}.{:01}s", tenths / 10, tenths % 10);
    }
    let seconds = millis / 1_000;
    format!("{}m{:02}s", seconds / 60, seconds % 60)
}

fn report_message(message: &str) -> String {
    const MESSAGE_LIMIT: usize = 180;
    let joined = message
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .collect::<Vec<_>>()
        .join(" | ");
    if joined.chars().count() <= MESSAGE_LIMIT {
        return joined;
    }
    let mut truncated = joined
        .chars()
        .take(MESSAGE_LIMIT.saturating_sub(3))
        .collect::<String>();
    truncated.push_str("...");
    truncated
}

fn target_diagnostics(
    target: &WorktreeTarget,
    plan: Option<&BuildPlan>,
    started_at: SystemTime,
) -> TargetDiagnosticCounts {
    let cache_dir = plan.map_or_else(
        || target_toolchain_cache_dir(target),
        |plan| plan.cache_dir.clone(),
    );
    let mut diagnostics = scan_target_diagnostic_logs(&cache_dir, started_at);
    if let Some(plan) = plan {
        diagnostics.warnings += plan.warnings.len();
    }
    diagnostics
}

fn target_toolchain_cache_dir(target: &WorktreeTarget) -> PathBuf {
    target
        .worktree_path
        .join("platform")
        .join("minecraft")
        .join("build")
        .join("sfm-toolchain")
}

fn scan_target_diagnostic_logs(cache_dir: &Path, started_at: SystemTime) -> TargetDiagnosticCounts {
    let mut diagnostics = TargetDiagnosticCounts::default();
    let files = match collect_files_under(cache_dir) {
        Ok(files) => files,
        Err(error) => {
            tracing::debug!(
                cache = %cache_dir.display(),
                error = %error,
                "failed to scan target diagnostic logs"
            );
            return diagnostics;
        }
    };

    for path in files {
        if !is_report_diagnostic_log(&path) || !was_modified_for_target_report(&path, started_at) {
            continue;
        }
        let bytes = match fs::read(&path) {
            Ok(bytes) => bytes,
            Err(error) => {
                tracing::debug!(
                    path = %path.display(),
                    error = %error,
                    "failed to read target diagnostic log"
                );
                continue;
            }
        };
        let content = String::from_utf8_lossy(&bytes);
        diagnostics.add(diagnostic_counts_from_log_text(&content));
    }

    diagnostics
}

fn is_report_diagnostic_log(path: &Path) -> bool {
    if path
        .file_name()
        .and_then(|name| name.to_str())
        .is_some_and(|name| name.eq_ignore_ascii_case("console.log"))
    {
        return true;
    }
    path.extension()
        .and_then(|extension| extension.to_str())
        .is_some_and(|extension| extension.eq_ignore_ascii_case("log"))
}

fn was_modified_for_target_report(path: &Path, started_at: SystemTime) -> bool {
    let threshold = started_at
        .checked_sub(Duration::from_secs(2))
        .unwrap_or(started_at);
    let Ok(metadata) = fs::metadata(path) else {
        return true;
    };
    let Ok(modified) = metadata.modified() else {
        return true;
    };
    modified >= threshold
}

fn diagnostic_counts_from_log_text(content: &str) -> TargetDiagnosticCounts {
    let mut summary_counts = TargetDiagnosticCounts::default();
    let mut fallback_counts = TargetDiagnosticCounts::default();
    let mut has_warning_summary = false;
    let mut has_error_summary = false;

    for line in content.lines() {
        if let Some(count) = parse_diagnostic_summary(line, "warning", "warnings") {
            summary_counts.warnings += count;
            has_warning_summary = true;
        }
        if let Some(count) = parse_diagnostic_summary(line, "error", "errors") {
            summary_counts.errors += count;
            has_error_summary = true;
        }
        if line_looks_like_warning(line) {
            fallback_counts.warnings += 1;
        }
        if line_looks_like_error(line) {
            fallback_counts.errors += 1;
        }
    }

    TargetDiagnosticCounts {
        warnings: if has_warning_summary {
            summary_counts.warnings
        } else {
            fallback_counts.warnings
        },
        errors: if has_error_summary {
            summary_counts.errors
        } else {
            fallback_counts.errors
        },
    }
}

fn parse_diagnostic_summary(line: &str, singular: &str, plural: &str) -> Option<usize> {
    let mut previous_count = None;
    for token in line.split_whitespace() {
        let token = token.trim_matches(|ch: char| !ch.is_ascii_alphanumeric());
        if token.is_empty() {
            previous_count = None;
            continue;
        }
        if let Some(count) = parse_count_token(token) {
            previous_count = Some(count);
            continue;
        }
        let label = token.to_ascii_lowercase();
        if (label == singular || label == plural) && previous_count.is_some() {
            return previous_count;
        }
        previous_count = None;
    }
    None
}

fn parse_count_token(token: &str) -> Option<usize> {
    token
        .chars()
        .all(|ch| ch.is_ascii_digit())
        .then(|| token.parse().ok())
        .flatten()
}

fn line_looks_like_warning(line: &str) -> bool {
    let line = line.to_ascii_lowercase();
    line.contains("warning:") || line.contains("[warning]") || line.contains(" warn ")
}

fn line_looks_like_error(line: &str) -> bool {
    let line = line.to_ascii_lowercase();
    line.contains("error:") || line.contains("[error]") || line.contains(" error ")
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %options.branch,
        strict_manifest = options.strict_manifest,
        error_action = %options.error_action,
        parallelism = %options.parallelism,
    )
)]
pub(crate) fn invoke_compare(
    options: &CompareOptions,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let targets = select_required_worktree_targets(&options.branch)?;
    cancellation_token.bail_if_cancelled()?;
    if targets.len() == 1 {
        return invoke_single_target_compare(options, &targets[0], cancellation_token);
    }

    if options.gradle_jar.is_some() || options.rust_jar.is_some() {
        eyre::bail!(
            "--gradle-jar and --rust-jar overrides can only be used when --branch selects one target."
        );
    }

    let total = targets.len();
    let CompareExecutionSummary { reports, failures } =
        execute_compare_targets(options, targets, cancellation_token)?;
    cancellation_token.bail_if_cancelled()?;
    write_compare_reports(&reports, options.report_json.as_deref())?;
    finish_compare_summary(total, &reports, &failures)
}

fn execute_compare_targets(
    options: &CompareOptions,
    targets: Vec<WorktreeTarget>,
    cancellation_token: &CancellationToken,
) -> eyre::Result<CompareExecutionSummary> {
    let Some(limit) = options.parallelism.limit() else {
        return execute_compare_targets_sequential(options, targets, cancellation_token);
    };
    execute_compare_targets_parallel(options, targets, limit, cancellation_token)
}

fn execute_compare_targets_sequential(
    options: &CompareOptions,
    targets: Vec<WorktreeTarget>,
    cancellation_token: &CancellationToken,
) -> eyre::Result<CompareExecutionSummary> {
    let mut reports = Vec::new();
    let mut failures = Vec::new();
    for target in targets {
        cancellation_token.bail_if_cancelled()?;
        let _target_span = tracing::info_span!(
            "sfm_jar_compare_target",
            branch = %target.branch,
            worktree = %target.worktree_path.display(),
        )
        .entered();
        record_compare_result(
            &target,
            compare_target(options, &target),
            &mut reports,
            &mut failures,
        );
        cancellation_token.bail_if_cancelled()?;

        if !failures.is_empty() && !options.error_action.should_continue() {
            break;
        }
    }

    Ok(CompareExecutionSummary { reports, failures })
}

fn execute_compare_targets_parallel(
    options: &CompareOptions,
    targets: Vec<WorktreeTarget>,
    limit: usize,
    cancellation_token: &CancellationToken,
) -> eyre::Result<CompareExecutionSummary> {
    if targets.is_empty() {
        return Ok(CompareExecutionSummary::default());
    }

    let target_count = targets.len();
    let worker_count = limit.min(target_count);
    tracing::info!(
        action = "sfm_jar_compare_target",
        worker_count,
        target_count,
        error_action = %options.error_action,
        "parallel target execution starting"
    );

    let queue = Arc::new(Mutex::new(
        targets.into_iter().enumerate().collect::<VecDeque<_>>(),
    ));
    let stop_starting = Arc::new(AtomicBool::new(false));
    let (sender, receiver) = mpsc::channel::<CompareExecutionResult>();

    thread::scope(|scope| {
        for worker_index in 0..worker_count {
            let queue = Arc::clone(&queue);
            let stop_starting = Arc::clone(&stop_starting);
            let sender = sender.clone();
            let cancellation_token = cancellation_token.clone();
            scope.spawn(move || {
                loop {
                    if stop_starting.load(AtomicOrdering::Acquire)
                        || cancellation_token.is_cancelled()
                    {
                        break;
                    }
                    let target = {
                        let mut queue = queue.lock().expect("target queue should not be poisoned");
                        queue.pop_front()
                    };
                    let Some((target_index, target)) = target else {
                        break;
                    };

                    let _worker_span = tracing::info_span!(
                        "sfm_parallel_worker",
                        worker = worker_index,
                        branch = %target.branch,
                        worktree = %target.worktree_path.display(),
                    )
                    .entered();
                    let result = compare_target(options, &target);
                    let failed = result
                        .as_ref()
                        .map_or(true, |report| !report.report.matches);
                    if failed && !options.error_action.should_continue() {
                        stop_starting.store(true, AtomicOrdering::Release);
                    }
                    if sender
                        .send(CompareExecutionResult {
                            target_index,
                            target,
                            result,
                        })
                        .is_err()
                    {
                        break;
                    }
                }
            });
        }
        drop(sender);

        let mut results = receiver.into_iter().collect::<Vec<_>>();
        results.sort_by_key(|result| result.target_index);

        let mut reports = Vec::new();
        let mut failures = Vec::new();
        for result in results {
            record_compare_result(&result.target, result.result, &mut reports, &mut failures);
        }

        cancellation_token.bail_if_cancelled()?;

        Ok(CompareExecutionSummary { reports, failures })
    })
}

fn record_compare_result(
    target: &WorktreeTarget,
    result: eyre::Result<TargetJarCompareReport>,
    reports: &mut Vec<TargetJarCompareReport>,
    failures: &mut Vec<TargetFailure>,
) {
    match result {
        Ok(report) => {
            emit_compare_report(&report.report);
            if !report.report.matches {
                failures.push(TargetFailure::from_message(
                    target,
                    "Jar comparison found normalized differences.",
                ));
            }
            reports.push(report);
        }
        Err(error) => {
            tracing::error!(error = %error, "target_failed");
            failures.push(TargetFailure::new(target, &error));
        }
    }
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %target.branch,
        worktree = %target.worktree_path.display(),
    )
)]
fn invoke_single_target_compare(
    options: &CompareOptions,
    target: &WorktreeTarget,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let report = compare_target(options, target)?;
    cancellation_token.bail_if_cancelled()?;

    emit_compare_report(&report.report);
    let matches = report.report.matches;
    write_compare_reports(&[report], options.report_json.as_deref())?;

    if matches {
        Ok(())
    } else {
        eyre::bail!("Jar comparison found normalized differences.")
    }
}

fn compare_target(
    options: &CompareOptions,
    target: &WorktreeTarget,
) -> eyre::Result<TargetJarCompareReport> {
    let paths = resolve_compare_paths(options, target)?;
    let report = compare_jars(&paths.gradle_jar, &paths.rust_jar, options.strict_manifest)?;
    Ok(TargetJarCompareReport {
        branch_name: target.branch.clone(),
        worktree_path: target.worktree_path.as_path().to_path_buf(),
        report,
    })
}

fn finish_compare_summary(
    total: usize,
    reports: &[TargetJarCompareReport],
    failures: &[TargetFailure],
) -> eyre::Result<()> {
    if total > 1 || !failures.is_empty() {
        let matched = reports
            .iter()
            .filter(|report| report.report.matches)
            .count();
        tracing::info!(
            "jar compare target summary: {matched}/{total} matched, {} failed.",
            failures.len()
        );
    }

    if failures.is_empty() {
        return Ok(());
    }

    for failure in failures {
        tracing::info!(
            "Failed target {} ({}): {}",
            failure.branch,
            failure.worktree_path.display(),
            failure.error
        );
    }

    eyre::bail!(
        "jar compare failed for {} of {total} target(s).",
        failures.len()
    );
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %options.branch,
        require_portable_artifacts = options.require_portable_artifacts,
        error_action = %options.error_action,
        parallelism = %options.parallelism,
    )
)]
pub(crate) fn invoke_artifact_audit(
    options: &ArtifactAuditOptions,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let targets = select_required_worktree_targets(&options.branch)?;
    cancellation_token.bail_if_cancelled()?;
    let total = targets.len();
    let ArtifactAuditExecutionSummary { reports, failures } =
        execute_artifact_audit_targets(options, targets, cancellation_token)?;
    cancellation_token.bail_if_cancelled()?;
    write_artifact_audit_reports(&reports, options.report_json.as_deref())?;
    finish_artifact_audit_summary(total, &reports, &failures)
}

fn execute_artifact_audit_targets(
    options: &ArtifactAuditOptions,
    targets: Vec<WorktreeTarget>,
    cancellation_token: &CancellationToken,
) -> eyre::Result<ArtifactAuditExecutionSummary> {
    let Some(limit) = options.parallelism.limit() else {
        return execute_artifact_audit_targets_sequential(options, targets, cancellation_token);
    };
    execute_artifact_audit_targets_parallel(options, targets, limit, cancellation_token)
}

fn execute_artifact_audit_targets_sequential(
    options: &ArtifactAuditOptions,
    targets: Vec<WorktreeTarget>,
    cancellation_token: &CancellationToken,
) -> eyre::Result<ArtifactAuditExecutionSummary> {
    let mut reports = Vec::new();
    let mut failures = Vec::new();
    for target in targets {
        cancellation_token.bail_if_cancelled()?;
        let _target_span = tracing::info_span!(
            "sfm_jar_artifact_audit_target",
            branch = %target.branch,
            worktree = %target.worktree_path.display(),
        )
        .entered();
        record_artifact_audit_result(
            &target,
            audit_artifacts_for_target(options, &target),
            &mut reports,
            &mut failures,
        );
        cancellation_token.bail_if_cancelled()?;

        if !failures.is_empty() && !options.error_action.should_continue() {
            break;
        }
    }

    Ok(ArtifactAuditExecutionSummary { reports, failures })
}

fn execute_artifact_audit_targets_parallel(
    options: &ArtifactAuditOptions,
    targets: Vec<WorktreeTarget>,
    limit: usize,
    cancellation_token: &CancellationToken,
) -> eyre::Result<ArtifactAuditExecutionSummary> {
    if targets.is_empty() {
        return Ok(ArtifactAuditExecutionSummary::default());
    }

    let target_count = targets.len();
    let worker_count = limit.min(target_count);
    tracing::info!(
        action = "sfm_jar_artifact_audit_target",
        worker_count,
        target_count,
        error_action = %options.error_action,
        "parallel target execution starting"
    );

    let queue = Arc::new(Mutex::new(
        targets.into_iter().enumerate().collect::<VecDeque<_>>(),
    ));
    let stop_starting = Arc::new(AtomicBool::new(false));
    let (sender, receiver) = mpsc::channel::<ArtifactAuditExecutionResult>();

    thread::scope(|scope| {
        for worker_index in 0..worker_count {
            let queue = Arc::clone(&queue);
            let stop_starting = Arc::clone(&stop_starting);
            let sender = sender.clone();
            let cancellation_token = cancellation_token.clone();
            scope.spawn(move || {
                loop {
                    if stop_starting.load(AtomicOrdering::Acquire)
                        || cancellation_token.is_cancelled()
                    {
                        break;
                    }
                    let target = {
                        let mut queue = queue.lock().expect("target queue should not be poisoned");
                        queue.pop_front()
                    };
                    let Some((target_index, target)) = target else {
                        break;
                    };

                    let _worker_span = tracing::info_span!(
                        "sfm_parallel_worker",
                        worker = worker_index,
                        branch = %target.branch,
                        worktree = %target.worktree_path.display(),
                    )
                    .entered();
                    let result = audit_artifacts_for_target(options, &target);
                    let failed = result.as_ref().map_or(true, |report| !report.report.passed);
                    if failed && !options.error_action.should_continue() {
                        stop_starting.store(true, AtomicOrdering::Release);
                    }
                    if sender
                        .send(ArtifactAuditExecutionResult {
                            target_index,
                            target,
                            result,
                        })
                        .is_err()
                    {
                        break;
                    }
                }
            });
        }
        drop(sender);

        let mut results = receiver.into_iter().collect::<Vec<_>>();
        results.sort_by_key(|result| result.target_index);

        let mut reports = Vec::new();
        let mut failures = Vec::new();
        for result in results {
            record_artifact_audit_result(
                &result.target,
                result.result,
                &mut reports,
                &mut failures,
            );
        }

        cancellation_token.bail_if_cancelled()?;

        Ok(ArtifactAuditExecutionSummary { reports, failures })
    })
}

fn record_artifact_audit_result(
    target: &WorktreeTarget,
    result: eyre::Result<TargetArtifactAuditReport>,
    reports: &mut Vec<TargetArtifactAuditReport>,
    failures: &mut Vec<TargetFailure>,
) {
    match result {
        Ok(report) => {
            emit_artifact_audit_report(&report.report);
            if !report.report.passed {
                failures.push(TargetFailure::from_message(
                    target,
                    "Artifact audit found verification errors.",
                ));
            }
            reports.push(report);
        }
        Err(error) => {
            tracing::error!(error = %error, "target_failed");
            failures.push(TargetFailure::new(target, &error));
        }
    }
}

fn audit_artifacts_for_target(
    options: &ArtifactAuditOptions,
    target: &WorktreeTarget,
) -> eyre::Result<TargetArtifactAuditReport> {
    let worktree_path = target.worktree_path.as_path().to_path_buf();
    let minecraft_dir = worktree_path.join("platform").join("minecraft");
    let properties_path = minecraft_dir.join("gradle.properties");
    let properties = read_properties(&properties_path)?;
    let minecraft_version = required_property(&properties, "minecraft_version")?;
    let lockfile_path = minecraft_dir.join("sfm-toolchain.lock.json");
    let common_cache_dir = common_toolchain_cache_dir();
    let report = audit_artifact_lockfile(
        &lockfile_path,
        &minecraft_dir,
        &common_cache_dir,
        minecraft_version,
        options.require_portable_artifacts,
    )?;

    Ok(TargetArtifactAuditReport {
        branch_name: target.branch.clone(),
        worktree_path,
        report,
    })
}

fn audit_artifact_lockfile(
    lockfile_path: &Path,
    minecraft_dir: &Path,
    common_cache_dir: &Path,
    minecraft_version: &str,
    require_portable_artifacts: bool,
) -> eyre::Result<ArtifactAuditReport> {
    let mut report = ArtifactAuditReport::new(lockfile_path.to_path_buf());
    let Some(lockfile) = read_optional_artifact_lockfile(lockfile_path, minecraft_version)? else {
        report.push_error(
            ArtifactAuditIssueKind::LockfileMissing,
            None,
            None,
            lockfile_path,
            format!("Artifact lockfile is missing: {}", lockfile_path.display()),
        );
        report.finalize(require_portable_artifacts);
        return Ok(report);
    };

    report.total_artifacts = lockfile.artifacts.len();
    let locked_artifact_paths = lockfile
        .artifacts
        .iter()
        .map(|artifact| artifact.cache_path.clone())
        .collect::<BTreeSet<_>>();
    for dependency in &lockfile.dependencies {
        if !locked_artifact_paths.contains(&dependency.cache_path) {
            report.push_error(
                ArtifactAuditIssueKind::DependencyArtifactMissing,
                Some(dependency.resolved_notation.clone()),
                None,
                &dependency.cache_path,
                format!(
                    "Dependency {} points at {}, but no locked artifact records that cache path",
                    dependency.resolved_notation,
                    dependency.cache_path.display()
                ),
            );
        }
    }

    for artifact in &lockfile.artifacts {
        audit_locked_artifact(
            &mut report,
            artifact,
            minecraft_dir,
            common_cache_dir,
            require_portable_artifacts,
        )?;
    }

    report.finalize(require_portable_artifacts);
    Ok(report)
}

fn audit_locked_artifact(
    report: &mut ArtifactAuditReport,
    artifact: &ArtifactLockEntry,
    minecraft_dir: &Path,
    common_cache_dir: &Path,
    require_portable_artifacts: bool,
) -> eyre::Result<()> {
    if !artifact.source.is_fresh_slate_portable() {
        let message = format!(
            "Artifact {} has {} provenance",
            artifact_label(artifact),
            artifact.source.label()
        );
        if require_portable_artifacts {
            report.push_error(
                ArtifactAuditIssueKind::NonPortableProvenance,
                artifact.coordinate.clone(),
                Some(artifact.source.clone()),
                &artifact.cache_path,
                message,
            );
        } else {
            report.push_warning(
                ArtifactAuditIssueKind::NonPortableProvenance,
                artifact.coordinate.clone(),
                Some(artifact.source.clone()),
                &artifact.cache_path,
                message,
            );
        }
    }

    let cache_path =
        resolve_locked_artifact_path(&artifact.cache_path, minecraft_dir, common_cache_dir);
    if !cache_path.is_file() {
        report.push_error(
            ArtifactAuditIssueKind::ArtifactMissing,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            &cache_path,
            format!("Locked artifact is missing: {}", cache_path.display()),
        );
        return Ok(());
    }

    let _cache_read_lock = acquire_artifact_path_read_lock(&cache_path)?;
    let actual_hash = ContentHash::from_path(&cache_path, artifact.hash.algorithm)?;
    if actual_hash != artifact.hash {
        report.push_error(
            ArtifactAuditIssueKind::ArtifactHashMismatch,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            &cache_path,
            format!(
                "Locked artifact {} has content hash {}, but lockfile requires {}",
                cache_path.display(),
                actual_hash,
                artifact.hash
            ),
        );
        return Ok(());
    }
    report.verified_artifacts += 1;

    if let Some(provenance) = read_artifact_provenance(&cache_path)? {
        audit_artifact_provenance_sidecar(report, artifact, &cache_path, &provenance);
    }

    audit_original_source_artifact(report, artifact)?;
    Ok(())
}

fn audit_artifact_provenance_sidecar(
    report: &mut ArtifactAuditReport,
    artifact: &ArtifactLockEntry,
    cache_path: &Path,
    provenance: &ArtifactProvenance,
) {
    if provenance.hash != artifact.hash {
        report.push_error(
            ArtifactAuditIssueKind::ProvenanceHashMismatch,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            cache_path,
            format!(
                "Artifact provenance sidecar for {} records SHA-1 {}, but lockfile requires {}",
                cache_path.display(),
                provenance.hash,
                artifact.hash
            ),
        );
    }
    if provenance.source != artifact.source {
        report.push_error(
            ArtifactAuditIssueKind::ProvenanceMismatch,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            cache_path,
            format!(
                "Artifact provenance sidecar for {} records source {}, but lockfile requires {}",
                cache_path.display(),
                provenance.source.label(),
                artifact.source.label()
            ),
        );
    }
    if let (Some(sidecar_coordinate), Some(lock_coordinate)) =
        (&provenance.coordinate, &artifact.coordinate)
        && sidecar_coordinate != lock_coordinate
    {
        report.push_error(
            ArtifactAuditIssueKind::ProvenanceMismatch,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            cache_path,
            format!(
                "Artifact provenance sidecar for {} records coordinate {}, but lockfile requires {}",
                cache_path.display(),
                sidecar_coordinate,
                lock_coordinate
            ),
        );
    }
    if provenance.source_relative_path != artifact.source_relative_path {
        report.push_error(
            ArtifactAuditIssueKind::ProvenanceMismatch,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            cache_path,
            format!(
                "Artifact provenance sidecar for {} records source_relative_path {}, but lockfile requires {}",
                cache_path.display(),
                optional_path_label(provenance.source_relative_path.as_deref()),
                optional_path_label(artifact.source_relative_path.as_deref())
            ),
        );
    }
    if provenance.source_git != artifact.source_git {
        report.push_error(
            ArtifactAuditIssueKind::ProvenanceMismatch,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            cache_path,
            format!(
                "Artifact provenance sidecar for {} records source_git {:?}, but lockfile requires {:?}",
                cache_path.display(),
                provenance.source_git,
                artifact.source_git
            ),
        );
    }
    if provenance.source_build != artifact.source_build {
        report.push_error(
            ArtifactAuditIssueKind::ProvenanceMismatch,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            cache_path,
            format!(
                "Artifact provenance sidecar for {} records source_build {:?}, but lockfile requires {:?}",
                cache_path.display(),
                provenance.source_build,
                artifact.source_build
            ),
        );
    }
}

fn audit_original_source_artifact(
    report: &mut ArtifactAuditReport,
    artifact: &ArtifactLockEntry,
) -> eyre::Result<()> {
    if artifact.source != ArtifactSource::ExplicitSource {
        return Ok(());
    }

    let Some(original_path) = artifact.original_path.as_deref() else {
        report.push_error(
            ArtifactAuditIssueKind::OriginalSourceMissing,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            &artifact.cache_path,
            format!(
                "Explicit-source artifact {} has no original_path in the lockfile",
                artifact_label(artifact)
            ),
        );
        return Ok(());
    };

    if !original_path.is_file() {
        report.push_error(
            ArtifactAuditIssueKind::OriginalSourceMissing,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            original_path,
            format!(
                "Explicit-source artifact {} original source is missing: {}",
                artifact_label(artifact),
                original_path.display()
            ),
        );
        return Ok(());
    }

    let source_hash = ContentHash::from_path(original_path, artifact.hash.algorithm)?;
    if source_hash != artifact.hash {
        report.push_error(
            ArtifactAuditIssueKind::OriginalSourceHashMismatch,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            original_path,
            format!(
                "Explicit-source artifact {} original source has content hash {}, but lockfile requires {}",
                artifact_label(artifact),
                source_hash,
                artifact.hash
            ),
        );
    }

    let Some(locked_git) = &artifact.source_git else {
        report.push_warning(
            ArtifactAuditIssueKind::SourceGitMissing,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            original_path,
            format!(
                "Explicit-source artifact {} has no source_git provenance in the lockfile",
                artifact_label(artifact)
            ),
        );
        return Ok(());
    };
    let Some(current_git) = source_git_provenance(original_path) else {
        report.push_error(
            ArtifactAuditIssueKind::SourceGitMismatch,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            original_path,
            format!(
                "Explicit-source artifact {} source Git checkout could not be resolved from {}",
                artifact_label(artifact),
                original_path.display()
            ),
        );
        return Ok(());
    };
    if &current_git != locked_git {
        report.push_error(
            ArtifactAuditIssueKind::SourceGitMismatch,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            original_path,
            format!(
                "Explicit-source artifact {} source Git changed: lockfile branch={} commit={} dirty={} remote={}, current branch={} commit={} dirty={} remote={}",
                artifact_label(artifact),
                locked_git.branch,
                locked_git.commit,
                locked_git.dirty,
                locked_git.remote_url.as_deref().unwrap_or("<none>"),
                current_git.branch,
                current_git.commit,
                current_git.dirty,
                current_git.remote_url.as_deref().unwrap_or("<none>")
            ),
        );
    }

    Ok(())
}

fn resolve_locked_artifact_path(
    path: &Path,
    minecraft_dir: &Path,
    common_cache_dir: &Path,
) -> PathBuf {
    if let Ok(relative) = path.strip_prefix(Path::new("$sfm-cache")) {
        return common_cache_dir.join(relative);
    }
    if path.is_absolute() {
        return path.to_path_buf();
    }
    minecraft_dir.join(path)
}

fn artifact_label(artifact: &ArtifactLockEntry) -> String {
    artifact
        .coordinate
        .clone()
        .unwrap_or_else(|| artifact.cache_path.display().to_string())
}

fn optional_path_label(path: Option<&Path>) -> String {
    path.map_or_else(|| "<none>".to_string(), |path| path.display().to_string())
}

fn emit_artifact_audit_report(report: &ArtifactAuditReport) {
    tracing::info!(
        "Artifact audit: {} verified, {} error(s), {} warning(s), {} non-portable artifact(s).",
        report.verified_artifacts,
        report.error_count,
        report.warning_count,
        report.non_portable_artifacts
    );
    for issue in &report.issues {
        match issue.severity {
            ArtifactAuditSeverity::Error => tracing::error!(
                "{} [{}] {}",
                issue.kind.label(),
                issue.path.display(),
                issue.message
            ),
            ArtifactAuditSeverity::Warning => tracing::warn!(
                "{} [{}] {}",
                issue.kind.label(),
                issue.path.display(),
                issue.message
            ),
        }
    }
}

fn finish_artifact_audit_summary(
    total: usize,
    reports: &[TargetArtifactAuditReport],
    failures: &[TargetFailure],
) -> eyre::Result<()> {
    if total > 1 || !failures.is_empty() {
        let passed = reports.iter().filter(|report| report.report.passed).count();
        tracing::info!(
            "jar audit-artifacts target summary: {passed}/{total} passed, {} failed.",
            failures.len()
        );
    }

    if failures.is_empty() {
        return Ok(());
    }

    for failure in failures {
        tracing::info!(
            "Failed target {} ({}): {}",
            failure.branch,
            failure.worktree_path.display(),
            failure.error
        );
    }

    eyre::bail!(
        "jar audit-artifacts failed for {} of {total} target(s).",
        failures.len()
    );
}

fn write_artifact_audit_reports(
    reports: &[TargetArtifactAuditReport],
    requested_path: Option<&Path>,
) -> eyre::Result<()> {
    let Some(path) = requested_path else {
        return Ok(());
    };

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let json = if reports.len() == 1 {
        facet_json::to_string_pretty(&reports[0].report)?
    } else {
        facet_json::to_string_pretty(reports)?
    };
    fs::write(path, json).wrap_err_with(|| format!("Failed to write {}", path.display()))?;
    Ok(())
}

#[derive(Debug)]
struct ComparePaths {
    gradle_jar: PathBuf,
    rust_jar: PathBuf,
}

#[derive(Debug, Facet)]
struct TargetJarCompareReport {
    #[facet(proxy = JsonBranchName)]
    branch_name: BranchName,
    #[facet(proxy = JsonPath)]
    worktree_path: PathBuf,
    report: JarCompareReport,
}

#[derive(Debug, Default)]
struct CompareExecutionSummary {
    reports: Vec<TargetJarCompareReport>,
    failures: Vec<TargetFailure>,
}

#[derive(Debug)]
struct CompareExecutionResult {
    target_index: usize,
    target: WorktreeTarget,
    result: eyre::Result<TargetJarCompareReport>,
}

#[derive(Debug, Default)]
struct ArtifactAuditExecutionSummary {
    reports: Vec<TargetArtifactAuditReport>,
    failures: Vec<TargetFailure>,
}

#[derive(Debug)]
struct ArtifactAuditExecutionResult {
    target_index: usize,
    target: WorktreeTarget,
    result: eyre::Result<TargetArtifactAuditReport>,
}

#[derive(Debug, Default)]
struct TargetExecutionSummary {
    plans: Vec<BuildPlan>,
    failures: Vec<TargetFailure>,
    reports: Vec<TargetExecutionReport>,
}

#[derive(Debug)]
struct TargetExecutionResult {
    target_index: usize,
    target: WorktreeTarget,
    report: TargetExecutionReport,
    result: eyre::Result<BuildPlan>,
}

#[derive(Debug)]
struct TargetExecutionReport {
    branch: BranchName,
    duration: Duration,
    warning_count: usize,
    error_count: usize,
    bail_message: Option<String>,
}

impl TargetExecutionReport {
    fn from_result(
        target: &WorktreeTarget,
        started_at: SystemTime,
        duration: Duration,
        result: &eyre::Result<BuildPlan>,
    ) -> Self {
        let plan = result.as_ref().ok();
        let diagnostics = target_diagnostics(target, plan, started_at);
        Self {
            branch: target.branch.clone(),
            duration,
            warning_count: diagnostics.warnings,
            error_count: diagnostics.errors,
            bail_message: result.as_ref().err().map(std::string::ToString::to_string),
        }
    }
}

#[derive(Clone, Copy, Debug, Default)]
struct TargetDiagnosticCounts {
    warnings: usize,
    errors: usize,
}

impl TargetDiagnosticCounts {
    fn add(&mut self, other: Self) {
        self.warnings += other.warnings;
        self.errors += other.errors;
    }
}

#[derive(Debug)]
struct TargetFailure {
    branch: BranchName,
    worktree_path: PathBuf,
    error: String,
}

impl TargetFailure {
    fn new(target: &WorktreeTarget, error: &eyre::Report) -> Self {
        Self::from_message(target, error.to_string())
    }

    fn from_message(target: &WorktreeTarget, error: impl Into<String>) -> Self {
        Self {
            branch: target.branch.clone(),
            worktree_path: target.worktree_path.as_path().to_path_buf(),
            error: error.into(),
        }
    }
}

#[derive(Debug, Facet)]
struct BuildPlan {
    schema_version: u32,
    mode: String,
    #[facet(proxy = JsonBranchName)]
    branch_name: BranchName,
    #[facet(proxy = JsonMinecraftVersion)]
    minecraft_version: MinecraftVersion,
    #[facet(proxy = JsonPath)]
    worktree_path: PathBuf,
    #[facet(proxy = JsonPath)]
    minecraft_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    gradle_output_jar: PathBuf,
    #[facet(proxy = JsonPath)]
    rust_output_jar: PathBuf,
    #[facet(proxy = JsonPath)]
    cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    common_cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    state_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    maven_cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    minecraft_cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    minecraft_version_cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    minecraft_assets_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    minecraft_libraries_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    lockfile_path: PathBuf,
    #[facet(skip_serializing)]
    lockfile: Option<ArtifactLockfile>,
    java: JavaPlan,
    java_release: u32,
    refresh: bool,
    allow_local_artifact_cache: bool,
    #[facet(skip_serializing)]
    artifact_sources: Vec<PathBuf>,
    properties: BTreeMap<String, String>,
    repositories: Vec<Repository>,
    loader_toolchain: LoaderToolchainPlan,
    artifacts: Vec<ArtifactPlan>,
    minecraft: MinecraftPlan,
    forge_userdev: Option<ForgeUserdevPlan>,
    mcp_config: Option<McpConfigPlan>,
    dependencies: Vec<DependencyPlan>,
    graph: Vec<GraphNode>,
    artifact_portability: ArtifactPortabilityAudit,
    warnings: Vec<String>,
}

#[derive(Clone, Debug, Facet)]
struct Repository {
    name: String,
    url: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
enum LoaderToolchainKind {
    ForgeGradleForge,
    ForgeGradleNeoForgeGroup,
    NeoGradleUserdev,
}

#[derive(Clone, Debug, Facet)]
struct LoaderToolchainPlan {
    kind: LoaderToolchainKind,
    base_coordinate: String,
    userdev_coordinate: String,
    sources_coordinate: Option<String>,
    universal_coordinate: Option<String>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct MavenCoordinate {
    group: String,
    artifact: String,
    version: String,
    classifier: Option<String>,
    extension: String,
}

#[derive(Clone, Debug, Facet)]
struct ArtifactPlan {
    id: ArtifactId,
    coordinate: Option<String>,
    repository: Option<String>,
    url: Option<String>,
    #[facet(proxy = JsonPath)]
    cache_path: PathBuf,
    sha1: Option<ContentHash>,
    downloaded: bool,
    required_for: ArtifactPurpose,
    provenance: ArtifactProvenance,
}

#[derive(Clone, Debug, Facet)]
struct ArtifactProvenance {
    schema_version: u32,
    source: ArtifactSource,
    coordinate: Option<String>,
    repository: Option<String>,
    url: Option<String>,
    #[facet(proxy = JsonOptionalPath)]
    original_path: Option<PathBuf>,
    #[facet(default)]
    #[facet(proxy = JsonOptionalPath)]
    source_relative_path: Option<PathBuf>,
    #[facet(default)]
    source_git: Option<SourceGitProvenance>,
    #[facet(default)]
    source_build: Option<SourceBuildProvenance>,
    #[facet(alias = "sha1")]
    hash: ContentHash,
}

#[derive(Clone, Debug, Facet)]
struct ArtifactLockfile {
    schema_version: u32,
    minecraft_version: String,
    #[facet(proxy = JsonPath)]
    maven_cache_dir: PathBuf,
    allow_local_artifact_cache: bool,
    repositories: Vec<Repository>,
    dependencies: Vec<DependencyLockEntry>,
    artifacts: Vec<ArtifactLockEntry>,
}

#[derive(Clone, Debug, Facet)]
struct DependencyLockEntry {
    configuration: String,
    notation: String,
    resolved_notation: String,
    source: DependencySource,
    dynamic_version: bool,
    #[facet(proxy = JsonPath)]
    cache_path: PathBuf,
}

#[derive(Clone, Debug, Facet)]
struct ArtifactLockEntry {
    coordinate: Option<String>,
    source: ArtifactSource,
    repository: Option<String>,
    url: Option<String>,
    #[facet(proxy = JsonPath)]
    cache_path: PathBuf,
    #[facet(proxy = JsonOptionalPath)]
    original_path: Option<PathBuf>,
    #[facet(default)]
    #[facet(proxy = JsonOptionalPath)]
    source_relative_path: Option<PathBuf>,
    #[facet(default)]
    source_git: Option<SourceGitProvenance>,
    #[facet(default)]
    source_build: Option<SourceBuildProvenance>,
    #[facet(alias = "sha1")]
    hash: ContentHash,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
struct SourceGitProvenance {
    #[facet(proxy = JsonPath)]
    root: PathBuf,
    commit: String,
    branch: String,
    dirty: bool,
    #[facet(default)]
    remote_url: Option<String>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
struct SourceBuildProvenance {
    build_system: SourceBuildSystem,
    tasks: Vec<String>,
    environment: BTreeMap<String, String>,
    #[facet(proxy = JsonPath)]
    output_path: PathBuf,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
enum SourceBuildSystem {
    GradleWrapper,
}

#[derive(Clone, Debug, Default, Facet)]
struct ArtifactPortabilityAudit {
    fresh_slate_portable: bool,
    total_artifacts: usize,
    portable_artifacts: usize,
    non_portable_artifacts: usize,
    explicit_source_artifacts: usize,
    local_cache_artifacts: usize,
    unknown_cache_artifacts: usize,
    issues: Vec<ArtifactPortabilityIssue>,
}

#[derive(Clone, Debug, Facet)]
struct ArtifactPortabilityIssue {
    coordinate: Option<String>,
    source: ArtifactSource,
    #[facet(proxy = JsonPath)]
    cache_path: PathBuf,
    #[facet(proxy = JsonOptionalPath)]
    original_path: Option<PathBuf>,
    #[facet(default)]
    #[facet(proxy = JsonOptionalPath)]
    source_relative_path: Option<PathBuf>,
    #[facet(default)]
    source_git: Option<SourceGitProvenance>,
    #[facet(default)]
    source_build: Option<SourceBuildProvenance>,
    reason: String,
    remediation: String,
}

#[derive(Clone, Debug)]
struct ArtifactPortabilityInput {
    coordinate: Option<String>,
    source: ArtifactSource,
    cache_path: PathBuf,
    original_path: Option<PathBuf>,
    source_relative_path: Option<PathBuf>,
    source_git: Option<SourceGitProvenance>,
    source_build: Option<SourceBuildProvenance>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(super) enum ArtifactSource {
    RemoteMaven,
    RemoteHttp,
    #[facet(rename = "explicit-artifact-source")]
    ExplicitSource,
    #[facet(rename = "source-build")]
    SourceBuild,
    LocalM2Cache,
    LocalGradleModuleCache,
    ExistingSfmCacheUnknown,
}

impl ArtifactSource {
    pub(super) fn is_fresh_slate_portable(&self) -> bool {
        matches!(
            self,
            Self::RemoteMaven | Self::RemoteHttp | Self::SourceBuild
        )
    }

    fn is_local_cache(&self) -> bool {
        matches!(self, Self::LocalM2Cache | Self::LocalGradleModuleCache)
    }

    fn can_be_materialized_from_source(&self) -> bool {
        matches!(self, Self::ExplicitSource | Self::SourceBuild)
    }

    pub(super) fn label(&self) -> &'static str {
        match self {
            Self::RemoteMaven => "remote-maven",
            Self::RemoteHttp => "remote-http",
            Self::ExplicitSource => "explicit-artifact-source",
            Self::SourceBuild => "source-build",
            Self::LocalM2Cache => "local-m2-cache",
            Self::LocalGradleModuleCache => "local-gradle-module-cache",
            Self::ExistingSfmCacheUnknown => "existing-sfm-cache-unknown",
        }
    }
}

#[derive(Debug, Facet)]
struct MinecraftPlan {
    version_manifest: ArtifactPlan,
    version_json: ArtifactPlan,
    client_jar_url: String,
    server_jar_url: String,
    client_mappings_url: Option<String>,
    server_mappings_url: Option<String>,
    libraries_count: usize,
}

#[derive(Debug, Facet)]
struct ForgeUserdevPlan {
    artifact: ArtifactPlan,
    spec: Option<i64>,
    mcp: Option<String>,
    neo_form: Option<String>,
    sources: Option<String>,
    universal: Option<String>,
    binpatcher: Option<String>,
    patches: Option<String>,
    patches_original_prefix: Option<String>,
    patches_modified_prefix: Option<String>,
    access_transformers: Vec<String>,
    side_strippers: Vec<String>,
    modules: Vec<String>,
    libraries: Vec<String>,
    module_count: usize,
    library_count: usize,
    test_libraries: Vec<String>,
    run_configs: Vec<String>,
}

#[derive(Debug, Facet)]
struct McpConfigPlan {
    artifact: ArtifactPlan,
    joined_steps: Vec<String>,
    function_coordinates: BTreeMap<String, String>,
    function_count: usize,
    data_keys: Vec<String>,
    library_count: usize,
}

#[derive(Clone, Debug, Facet)]
struct DependencyPlan {
    configuration: String,
    notation: String,
    resolved_notation: String,
    source: DependencySource,
    #[facet(proxy = JsonPath)]
    cache_path: PathBuf,
    url: Option<String>,
    dynamic_version: bool,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[repr(u8)]
enum DependencySource {
    CurseMaven,
    Maven,
}

#[derive(Debug, Facet)]
struct GraphNode {
    id: String,
    kind: String,
    status: NodeStatus,
    inputs: Vec<String>,
    outputs: Vec<String>,
    rebuild_reason: String,
}

#[derive(Debug, Facet)]
struct JavaPlan {
    // todo(2026-06-17) this can probably be removed in favour of jdk.rs#ResolvedJava
    #[facet(proxy = JsonPath)]
    executable: PathBuf,
    #[facet(proxy = JsonOptionalPath)]
    home: Option<PathBuf>,
    version_output: String,
    major_version: u32,
}

#[derive(Debug, Facet)]
struct JarCompareReport {
    #[facet(proxy = JsonPath)]
    gradle_jar: PathBuf,
    #[facet(proxy = JsonPath)]
    rust_jar: PathBuf,
    strict_manifest: bool,
    matches: bool,
    total_gradle_entries: usize,
    total_rust_entries: usize,
    compared_entries: usize,
    missing_entries: Vec<String>,
    extra_entries: Vec<String>,
    changed_entries: Vec<ChangedEntry>,
    manifest: ManifestCompare,
}

#[derive(Debug, Facet)]
struct ChangedEntry {
    path: String,
    #[facet(alias = "gradle_sha1")]
    gradle_hash: ContentHash,
    #[facet(alias = "rust_sha1")]
    rust_hash: ContentHash,
}

#[derive(Debug, Facet)]
struct ManifestCompare {
    compared: bool,
    changed: bool,
    ignored_implementation_timestamp: bool,
    gradle_sha1: Option<ContentHash>,
    rust_sha1: Option<ContentHash>,
}

#[derive(Debug, Facet)]
struct JarJarMetadata {
    jars: Vec<JarJarMetadataEntry>,
}

#[derive(Debug, Facet)]
struct JarJarMetadataEntry {
    identifier: JarJarIdentifier,
    version: JarJarVersion,
    path: String,
    #[facet(rename = "isObfuscated")]
    is_obfuscated: bool,
}

#[derive(Debug, Facet)]
struct JarJarIdentifier {
    group: String,
    artifact: String,
}

#[derive(Debug, Facet)]
struct JarJarVersion {
    range: String,
    #[facet(rename = "artifactVersion")]
    artifact_version: String,
}

#[derive(Debug)]
struct NormalizedJar {
    entries: BTreeMap<String, ContentHash>,
    manifest_sha1: Option<ContentHash>,
    total_entries: usize,
}

#[derive(Debug, Eq, Facet, PartialEq)]
#[repr(u8)]
enum NodeStatus {
    Ready,
    Planned,
}

#[derive(Debug, Facet)]
struct MojangVersionManifest {
    #[facet(default)]
    versions: Vec<MojangManifestVersion>,
}

#[derive(Debug, Facet)]
struct MojangManifestVersion {
    id: String,
    url: String,
}

#[derive(Debug, Facet)]
struct MinecraftVersionJson {
    downloads: MinecraftDownloads,
    #[facet(default)]
    libraries: Vec<MinecraftLibrary>,
    #[facet(rename = "assetIndex", default)]
    asset_index: Option<MinecraftAssetIndex>,
}

#[derive(Debug, Facet)]
struct MinecraftDownloads {
    client: MinecraftDownload,
    server: MinecraftDownload,
    #[facet(default)]
    client_mappings: Option<MinecraftDownload>,
    #[facet(default)]
    server_mappings: Option<MinecraftDownload>,
}

#[derive(Debug, Facet)]
struct MinecraftDownload {
    url: String,
}

#[derive(Debug, Facet)]
struct MinecraftAssetIndex {
    id: String,
    url: String,
}

#[derive(Debug, Facet)]
struct MinecraftAssetIndexJson {
    #[facet(default)]
    objects: BTreeMap<String, MinecraftAssetObject>,
}

#[derive(Debug, Facet)]
struct MinecraftAssetObject {
    hash: ContentHash,
}

#[derive(Debug, Facet)]
struct MinecraftLibrary {
    #[facet(default)]
    downloads: Option<MinecraftLibraryDownloads>,
}

#[derive(Debug, Facet)]
struct MinecraftLibraryDownloads {
    #[facet(default)]
    artifact: Option<MinecraftLibraryArtifact>,
}

#[derive(Debug, Facet)]
struct MinecraftLibraryArtifact {
    url: String,
    path: String,
    #[facet(default)]
    sha1: Option<ContentHash>,
}

#[derive(Debug, Default, Facet)]
struct ForgeUserdevConfig {
    #[facet(default)]
    spec: Option<i64>,
    #[facet(default)]
    mcp: Option<String>,
    #[facet(rename = "neoForm", default)]
    neo_form: Option<String>,
    #[facet(default)]
    sources: Option<String>,
    #[facet(default)]
    universal: Option<String>,
    #[facet(default)]
    binpatcher: Option<ForgeBinpatcherConfig>,
    #[facet(default)]
    patches: Option<String>,
    #[facet(rename = "patchesOriginalPrefix", default)]
    patches_original_prefix: Option<String>,
    #[facet(rename = "patchesModifiedPrefix", default)]
    patches_modified_prefix: Option<String>,
    #[facet(default)]
    ats: Option<StringList>,
    #[facet(default)]
    sass: Option<StringList>,
    #[facet(default)]
    modules: Vec<String>,
    #[facet(default)]
    libraries: Vec<String>,
    #[facet(rename = "testLibraries", default)]
    test_libraries: Vec<String>,
    #[facet(default)]
    runs: BTreeMap<String, ForgeRunConfig>,
}

#[derive(Debug, Default, Facet)]
struct ForgeBinpatcherConfig {
    #[facet(default)]
    version: Option<String>,
}

#[derive(Debug, Facet)]
#[facet(untagged)]
#[repr(u8)]
enum StringList {
    One(String),
    Many(Vec<String>),
}

impl StringList {
    fn into_vec(self) -> Vec<String> {
        match self {
            Self::One(value) => vec![value],
            Self::Many(values) => values,
        }
    }
}

#[derive(Debug, Default, Facet)]
struct McpConfigJson {
    #[facet(default)]
    data: McpData,
    #[facet(default)]
    steps: McpSteps,
    #[facet(default)]
    functions: BTreeMap<String, McpFunction>,
    #[facet(default)]
    libraries: BTreeMap<String, Vec<String>>,
}

#[derive(Debug, Default, Facet)]
struct McpData {
    #[facet(default)]
    mappings: Option<String>,
    #[facet(default)]
    inject: Option<String>,
    #[facet(default)]
    patches: Option<McpPatchData>,
}

#[derive(Debug, Default, Facet)]
struct McpPatchData {
    #[facet(default)]
    client: Option<String>,
    #[facet(default)]
    joined: Option<String>,
    #[facet(default)]
    server: Option<String>,
}

#[derive(Debug, Default, Facet)]
struct McpSteps {
    #[facet(default)]
    joined: Vec<McpStep>,
}

#[derive(Debug, Default, Facet)]
struct McpStep {
    #[facet(default)]
    name: Option<String>,
    #[facet(rename = "type", default)]
    step_type: Option<String>,
}

#[derive(Debug, Default, Facet)]
struct McpFunction {
    #[facet(default)]
    version: Option<String>,
    #[facet(default)]
    args: Vec<String>,
    #[facet(default)]
    jvmargs: Vec<String>,
    #[facet(default)]
    repo: Option<String>,
}

impl McpPatchData {
    fn has_any_patch_root(&self) -> bool {
        self.client.is_some() || self.joined.is_some() || self.server.is_some()
    }
}

impl McpFunction {
    fn has_declared_config(&self) -> bool {
        self.version
            .as_deref()
            .is_some_and(|version| !version.is_empty())
            || !self.args.is_empty()
            || !self.jvmargs.is_empty()
            || self.repo.as_deref().is_some_and(|repo| !repo.is_empty())
    }
}

impl MavenCoordinate {
    fn parse(input: &str) -> eyre::Result<Self> {
        let (notation, extension) = input
            .split_once('@')
            .map_or((input, "jar"), |(left, right)| (left, right));
        let parts: Vec<&str> = notation.split(':').collect();
        match parts.as_slice() {
            [group, artifact, version] => Ok(Self {
                group: (*group).to_string(),
                artifact: (*artifact).to_string(),
                version: (*version).to_string(),
                classifier: None,
                extension: extension.to_string(),
            }),
            [group, artifact, version, classifier] => Ok(Self {
                group: (*group).to_string(),
                artifact: (*artifact).to_string(),
                version: (*version).to_string(),
                classifier: Some((*classifier).to_string()),
                extension: extension.to_string(),
            }),
            _ => eyre::bail!("Invalid Maven coordinate: {input}"),
        }
    }

    fn file_name(&self) -> String {
        let classifier = self
            .classifier
            .as_ref()
            .map_or_else(String::new, |classifier| format!("-{classifier}"));
        format!(
            "{}-{}{}.{}",
            self.artifact, self.version, classifier, self.extension
        )
    }

    fn with_classifier(&self, classifier: &str) -> Self {
        Self {
            group: self.group.clone(),
            artifact: self.artifact.clone(),
            version: self.version.clone(),
            classifier: Some(classifier.to_string()),
            extension: self.extension.clone(),
        }
    }

    fn with_extension(&self, extension: &str) -> Self {
        Self {
            group: self.group.clone(),
            artifact: self.artifact.clone(),
            version: self.version.clone(),
            classifier: self.classifier.clone(),
            extension: extension.to_string(),
        }
    }
}

impl std::fmt::Display for MavenCoordinate {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}:{}:{}", self.group, self.artifact, self.version)?;
        if let Some(classifier) = &self.classifier {
            write!(f, ":{classifier}")?;
        }
        if self.extension != "jar" {
            write!(f, "@{}", self.extension)?;
        }
        Ok(())
    }
}

fn resolve_build_targets(options: &BuildOptions) -> eyre::Result<Vec<WorktreeTarget>> {
    select_required_worktree_targets(&options.branch)
}

fn common_toolchain_cache_dir() -> PathBuf {
    CACHE_DIR.0.join("minecraft-toolchain")
}

#[expect(
    clippy::too_many_lines,
    reason = "The planner is a single orchestration pass over project inputs."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %options.branch,
        target = %target.branch,
        refresh = options.refresh,
        allow_local_artifact_cache = options.allow_local_artifact_cache,
        require_portable_artifacts = options.require_portable_artifacts,
    )
)]
fn create_plan_for_target(
    options: &BuildOptions,
    target: &WorktreeTarget,
    cancellation_token: &CancellationToken,
) -> eyre::Result<BuildPlan> {
    cancellation_token.bail_if_cancelled()?;
    let (worktree_path, minecraft_dir, properties_path) = {
        let _span = tracing::debug_span!("plan_resolve_project_paths").entered();
        let worktree_path = target.worktree_path.as_path().to_path_buf();
        let minecraft_dir = worktree_path.join("platform").join("minecraft");
        let properties_path = minecraft_dir.join("gradle.properties");
        (worktree_path, minecraft_dir, properties_path)
    };
    let properties = {
        let _span = tracing::debug_span!("plan_read_properties").entered();
        read_properties(&properties_path)?
    };

    let (
        minecraft_version,
        loader_version,
        mapping_channel,
        mapping_version,
        mod_name,
        mod_version,
    ) = {
        let _span = tracing::debug_span!("plan_read_project_settings").entered();
        let minecraft_version = required_property(&properties, "minecraft_version")?;
        let loader_version = required_property(&properties, "neo_version")?;
        let (mapping_channel, mapping_version) =
            resolve_mapping_settings(&properties, minecraft_version);
        let mod_name = required_property(&properties, "mod_name")?;
        let mod_version = required_property(&properties, "mod_version")?;
        (
            minecraft_version,
            loader_version,
            mapping_channel,
            mapping_version,
            mod_name,
            mod_version,
        )
    };
    let warnings = Vec::new();

    let (
        gradle_output_jar,
        rust_output_jar,
        cache_dir,
        common_cache_dir,
        state_dir,
        maven_cache_dir,
        minecraft_cache_dir,
        minecraft_version_cache_dir,
        minecraft_assets_dir,
        minecraft_libraries_dir,
        lockfile_path,
    ) = {
        let _span = tracing::debug_span!("plan_compute_cache_paths").entered();
        let gradle_output_jar =
            gradle_output_jar_path(&minecraft_dir, mod_name, minecraft_version, mod_version);
        let rust_output_jar =
            rust_output_jar_path(&minecraft_dir, mod_name, minecraft_version, mod_version);
        let cache_dir = minecraft_dir.join("build").join("sfm-toolchain");
        let common_cache_dir = common_toolchain_cache_dir();
        let state_dir = cache_dir.join("state");
        let maven_cache_dir = common_cache_dir.join("maven");
        let minecraft_cache_dir = common_cache_dir.join("minecraft");
        let minecraft_version_cache_dir =
            minecraft_cache_dir.join("versions").join(minecraft_version);
        let minecraft_assets_dir = minecraft_cache_dir.join("assets");
        let minecraft_libraries_dir = minecraft_cache_dir.join("libraries");
        let lockfile_path = minecraft_dir.join("sfm-toolchain.lock.json");
        (
            gradle_output_jar,
            rust_output_jar,
            cache_dir,
            common_cache_dir,
            state_dir,
            maven_cache_dir,
            minecraft_cache_dir,
            minecraft_version_cache_dir,
            minecraft_assets_dir,
            minecraft_libraries_dir,
            lockfile_path,
        )
    };
    {
        let _span = tracing::debug_span!("plan_create_cache_dirs").entered();
        fs::create_dir_all(&state_dir)?;
        fs::create_dir_all(&maven_cache_dir)?;
        fs::create_dir_all(&minecraft_version_cache_dir)?;
        fs::create_dir_all(&minecraft_assets_dir)?;
        fs::create_dir_all(&minecraft_libraries_dir)?;
    };
    cancellation_token.bail_if_cancelled()?;
    let existing_lockfile = {
        let _span = tracing::debug_span!("plan_read_lockfile", refresh = options.refresh).entered();
        read_optional_artifact_lockfile(&lockfile_path, minecraft_version)?
    };
    let lockfile = if options.refresh {
        None
    } else {
        existing_lockfile.clone()
    };

    let repositories = {
        let _span = tracing::debug_span!("plan_load_repositories").entered();
        repositories()
    };
    let resolver = {
        let _span = tracing::debug_span!(
            "plan_create_resolver",
            repository_count = repositories.len(),
            has_lockfile = lockfile.is_some(),
            has_materialization_lockfile = existing_lockfile.is_some(),
            artifact_source_count = options.artifact_sources.len(),
        )
        .entered();
        Resolver::new(
            maven_cache_dir.clone(),
            repositories.clone(),
            options.refresh,
            options.allow_local_artifact_cache,
            options.artifact_sources.clone(),
            lockfile.clone(),
            existing_lockfile.clone(),
            cancellation_token.clone(),
        )?
    };
    cancellation_token.bail_if_cancelled()?;

    let dependency_script = minecraft_dir
        .join("gradle")
        .join("dependencies")
        .join(minecraft_version)
        .join("dependencies.gradle");
    let dependencies = {
        let _span = tracing::debug_span!("plan_parse_dependency_script").entered();
        parse_dependency_script(&dependency_script, &properties)?
    };
    let loader_toolchain = {
        let _span = tracing::debug_span!(
            "plan_resolve_loader_toolchain",
            dependency_count = dependencies.len(),
        )
        .entered();
        resolve_loader_toolchain(&dependencies, minecraft_version, loader_version)?
    };
    cancellation_token.bail_if_cancelled()?;
    let (java_release, java) = {
        let _span = tracing::debug_span!(
            "plan_resolve_java",
            has_java_home = options.java_home.is_some(),
        )
        .entered();
        let java_release = read_java_toolchain_release(&minecraft_dir, minecraft_version)?;
        let required_java = required_java_runtime_major(&loader_toolchain, java_release);

        let java = {
            let jdk_resolution =
                crate::jdk::resolve_java(options.java_home.as_deref(), required_java)?;
            JavaPlan {
                executable: jdk_resolution.executable,
                home: jdk_resolution.home,
                version_output: jdk_resolution.version_output,
                major_version: jdk_resolution.major_version,
            }
        };
        (java_release, java)
    };

    let (forge_userdev, mcp_config) = {
        let _span =
            tracing::debug_span!("plan_resolve_loader_artifacts", loader_kind = ?loader_toolchain.kind)
                .entered();
        let forge_userdev_coordinate =
            MavenCoordinate::parse(&loader_toolchain.userdev_coordinate)?;
        let forge_userdev_artifact = resolver.resolve_artifact(
            ArtifactId::from("forge-userdev"),
            &forge_userdev_coordinate,
            ArtifactPurpose::from("Loader userdev configuration and patches"),
        )?;
        cancellation_token.bail_if_cancelled()?;
        let forge_userdev = read_forge_userdev(&forge_userdev_artifact)?;

        let mcp_config = if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
            None
        } else if let Some(mcp) = &forge_userdev.mcp {
            let mcp_coordinate = MavenCoordinate::parse(mcp)?;
            let mcp_artifact = resolver.resolve_artifact(
                ArtifactId::from("mcp-config"),
                &mcp_coordinate,
                ArtifactPurpose::from("MCPConfig clean-slate Minecraft pipeline"),
            )?;
            cancellation_token.bail_if_cancelled()?;
            Some(read_mcp_config(&mcp_artifact)?)
        } else {
            None
        };
        (forge_userdev, mcp_config)
    };

    let mut artifacts = Vec::new();
    artifacts.push(forge_userdev.artifact.clone());
    if let Some(mcp_config) = &mcp_config {
        artifacts.push(mcp_config.artifact.clone());
    }

    let core_coordinates = {
        let _span = tracing::debug_span!(
            "plan_select_core_artifacts",
            loader_kind = ?loader_toolchain.kind,
            mapping_channel = mapping_channel.as_str(),
        )
        .entered();
        core_coordinates(
            &loader_toolchain,
            &mapping_channel,
            &mapping_version,
            &forge_userdev,
            mcp_config.as_ref(),
            &dependencies,
        )?
    };
    {
        let _span = tracing::debug_span!(
            "plan_resolve_core_artifacts",
            artifact_count = core_coordinates.len(),
        )
        .entered();
        artifacts.extend(resolver.resolve_artifacts(core_coordinates)?);
    };

    let minecraft = {
        let _span = tracing::debug_span!("plan_resolve_minecraft_inputs").entered();
        resolve_minecraft_plan(
            &minecraft_cache_dir,
            &resolver.client,
            minecraft_version,
            cancellation_token,
        )?
    };
    artifacts.push(minecraft.version_manifest.clone());
    artifacts.push(minecraft.version_json.clone());
    cancellation_token.bail_if_cancelled()?;

    let dependency_plans = {
        let _span = tracing::debug_span!(
            "plan_resolve_project_dependencies",
            parsed_dependency_count = dependencies.len(),
            loader_kind = ?loader_toolchain.kind,
        )
        .entered();
        let dependency_plans = resolver.resolve_dependencies(
            dependencies
                .iter()
                .filter(|dependency| should_plan_project_dependency(&loader_toolchain, dependency))
                .map(|dependency| {
                    (
                        dependency.configuration.clone(),
                        dependency.coordinate.clone(),
                    )
                }),
        )?;
        if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
            let _span = tracing::debug_span!(
                "plan_resolve_transitive_runtime_dependencies",
                root_dependency_count = dependency_plans.len(),
            )
            .entered();
            add_transitive_runtime_dependency_plans(&resolver, dependency_plans)?
        } else {
            dependency_plans
        }
    };
    cancellation_token.bail_if_cancelled()?;

    let graph = {
        let _span = tracing::debug_span!(
            "plan_build_graph",
            dependency_count = dependency_plans.len(),
        )
        .entered();
        build_graph(
            minecraft_version,
            &rust_output_jar,
            &dependency_plans,
            &loader_toolchain,
        )
    };

    let mut plan = {
        let _span = tracing::debug_span!(
            "plan_assemble",
            artifact_count = artifacts.len(),
            dependency_count = dependency_plans.len(),
            graph_node_count = graph.len(),
        )
        .entered();
        BuildPlan {
            schema_version: 1,
            mode: match options.mode {
                BuildMode::Plan => "plan".to_string(),
                BuildMode::Build => "build".to_string(),
            },
            branch_name: target.branch.clone(),
            minecraft_version: MinecraftVersion::parse(minecraft_version)?,
            worktree_path,
            minecraft_dir,
            gradle_output_jar,
            rust_output_jar,
            cache_dir,
            common_cache_dir,
            state_dir,
            maven_cache_dir,
            minecraft_cache_dir,
            minecraft_version_cache_dir,
            minecraft_assets_dir,
            minecraft_libraries_dir,
            lockfile_path,
            lockfile,
            java,
            java_release,
            refresh: options.refresh,
            allow_local_artifact_cache: options.allow_local_artifact_cache,
            artifact_sources: options.artifact_sources.clone(),
            properties,
            repositories,
            loader_toolchain,
            artifacts,
            minecraft,
            forge_userdev: Some(forge_userdev),
            mcp_config,
            dependencies: dependency_plans,
            graph,
            artifact_portability: ArtifactPortabilityAudit::default(),
            warnings,
        }
    };
    {
        let _span = tracing::debug_span!(
            "plan_audit_artifact_portability",
            artifact_count = plan.artifacts.len(),
            dependency_count = plan.dependencies.len(),
        )
        .entered();
        plan.artifact_portability = artifact_portability_audit(&plan)?;
    };
    cancellation_token.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!(
            "plan_finalize",
            require_portable_artifacts = options.require_portable_artifacts,
            fresh_slate_portable = plan.artifact_portability.fresh_slate_portable,
        )
        .entered();
        if !plan.artifact_portability.fresh_slate_portable {
            plan.warnings.push(format!(
                "Artifact portability audit found {} non-portable artifact(s); use --require-portable-artifacts to make this a hard failure.",
                plan.artifact_portability.non_portable_artifacts
            ));
        }
        if options.require_portable_artifacts {
            enforce_portable_artifacts(&plan)?;
        }
    }
    Ok(plan)
}

#[expect(
    clippy::too_many_lines,
    reason = "Artifact portability audit is a linear report builder; splitting it would obscure the ordering."
)]
fn artifact_portability_audit(plan: &BuildPlan) -> eyre::Result<ArtifactPortabilityAudit> {
    let mut inputs = BTreeMap::<(Option<String>, PathBuf), ArtifactPortabilityInput>::new();
    for artifact in &plan.artifacts {
        push_artifact_portability_input(
            plan,
            &mut inputs,
            ArtifactPortabilityInput {
                coordinate: artifact
                    .provenance
                    .coordinate
                    .clone()
                    .or_else(|| artifact.coordinate.clone()),
                source: artifact.provenance.source.clone(),
                cache_path: artifact.cache_path.clone(),
                original_path: artifact.provenance.original_path.clone(),
                source_relative_path: artifact.provenance.source_relative_path.clone(),
                source_git: artifact.provenance.source_git.clone(),
                source_build: artifact.provenance.source_build.clone(),
            },
        );
    }
    let dependency_provenance = {
        let _span = tracing::debug_span!(
            "artifact_portability_dependency_inputs",
            dependencies = plan.dependencies.len()
        )
        .entered();
        plan.dependencies
            .par_iter()
            .map(|dependency| {
                let _span = tracing::debug_span!(
                    "artifact_portability_dependency_input",
                    coordinate = %dependency.resolved_notation,
                    cache_path = %dependency.cache_path.display()
                )
                .entered();
                let actual_hash =
                    ContentHash::from_path(&dependency.cache_path, ContentHashAlgorithm::Blake3)?;
                let provenance =
                    read_artifact_provenance(&dependency.cache_path)?.unwrap_or_else(|| {
                        artifact_provenance(
                            ArtifactSource::ExistingSfmCacheUnknown,
                            Some(dependency.resolved_notation.clone()),
                            None,
                            None,
                            None,
                            None,
                            actual_hash,
                        )
                    });

                eyre::Ok((dependency, provenance))
            })
            .collect::<Vec<eyre::Result<_>>>()
            .into_iter()
            .collect::<eyre::Result<Vec<_>>>()?
    };

    for (dependency, provenance) in dependency_provenance {
        push_artifact_portability_input(
            plan,
            &mut inputs,
            ArtifactPortabilityInput {
                coordinate: provenance
                    .coordinate
                    .clone()
                    .or_else(|| Some(dependency.resolved_notation.clone())),
                source: provenance.source,
                cache_path: dependency.cache_path.clone(),
                original_path: provenance.original_path,
                source_relative_path: provenance.source_relative_path,
                source_git: provenance.source_git,
                source_build: provenance.source_build,
            },
        );
    }

    let total_artifacts = inputs.len();
    let mut portable_artifacts = 0usize;
    let mut explicit_source_artifacts = 0usize;
    let mut local_cache_artifacts = 0usize;
    let mut unknown_cache_artifacts = 0usize;
    let mut issues = Vec::new();

    for input in inputs.into_values() {
        if input.source.is_fresh_slate_portable() {
            portable_artifacts += 1;
            continue;
        }

        match input.source {
            ArtifactSource::ExplicitSource => explicit_source_artifacts += 1,
            ArtifactSource::ExistingSfmCacheUnknown => unknown_cache_artifacts += 1,
            ArtifactSource::SourceBuild => {}
            ref source if source.is_local_cache() => local_cache_artifacts += 1,
            _ => {}
        }
        issues.push(ArtifactPortabilityIssue {
            coordinate: input.coordinate,
            source: input.source.clone(),
            cache_path: portable_cache_path(plan, &input.cache_path),
            original_path: input.original_path,
            source_relative_path: input.source_relative_path,
            source_git: input.source_git,
            source_build: input.source_build,
            reason: artifact_portability_reason(&input.source).to_string(),
            remediation: artifact_portability_remediation(&input.source).to_string(),
        });
    }

    let non_portable_artifacts = issues.len();
    Ok(ArtifactPortabilityAudit {
        fresh_slate_portable: non_portable_artifacts == 0,
        total_artifacts,
        portable_artifacts,
        non_portable_artifacts,
        explicit_source_artifacts,
        local_cache_artifacts,
        unknown_cache_artifacts,
        issues,
    })
}

fn push_artifact_portability_input(
    plan: &BuildPlan,
    inputs: &mut BTreeMap<(Option<String>, PathBuf), ArtifactPortabilityInput>,
    input: ArtifactPortabilityInput,
) {
    let key = (
        input.coordinate.clone(),
        portable_cache_path(plan, &input.cache_path),
    );
    inputs.entry(key).or_insert(input);
}

fn artifact_portability_reason(source: &ArtifactSource) -> &'static str {
    match source {
        ArtifactSource::ExplicitSource => {
            "artifact was imported from an explicit local artifact source"
        }
        ArtifactSource::SourceBuild => {
            "artifact can be recreated from recorded source build provenance"
        }
        ArtifactSource::LocalM2Cache => "artifact was bootstrapped from the local Maven cache",
        ArtifactSource::LocalGradleModuleCache => {
            "artifact was bootstrapped from the local Gradle module cache"
        }
        ArtifactSource::ExistingSfmCacheUnknown => {
            "artifact exists in the SFM cache without a provenance sidecar"
        }
        ArtifactSource::RemoteMaven | ArtifactSource::RemoteHttp => {
            "artifact can be resolved from configured remote provenance"
        }
    }
}

fn artifact_portability_remediation(source: &ArtifactSource) -> &'static str {
    match source {
        ArtifactSource::ExplicitSource => {
            "publish or host the artifact in a configured repository, provide the same source with --artifact-source, or add a reproducible source-build/import step"
        }
        ArtifactSource::LocalM2Cache | ArtifactSource::LocalGradleModuleCache => {
            "resolve the artifact from a configured remote repository or re-import it from an explicit source so the lockfile does not depend on Maven-created local caches"
        }
        ArtifactSource::ExistingSfmCacheUnknown => {
            "refresh the artifact from remote Maven/HTTP or import it from an explicit source so SFM records provenance"
        }
        ArtifactSource::SourceBuild | ArtifactSource::RemoteMaven | ArtifactSource::RemoteHttp => {
            "no remediation required"
        }
    }
}

fn enforce_portable_artifacts(plan: &BuildPlan) -> eyre::Result<()> {
    if plan.artifact_portability.fresh_slate_portable {
        return Ok(());
    }

    let mut message = format!(
        "Artifact portability audit failed for {}: {} non-portable artifact(s)",
        plan.branch_name, plan.artifact_portability.non_portable_artifacts
    );
    for issue in plan.artifact_portability.issues.iter().take(10) {
        let coordinate = issue.coordinate.as_deref().unwrap_or("<unknown>");
        write!(
            message,
            "\n- {coordinate} [{}] at {}: {}",
            issue.source.label(),
            issue.cache_path.display(),
            issue.remediation
        )?;
    }
    if plan.artifact_portability.issues.len() > 10 {
        write!(
            message,
            "\n- ... and {} more",
            plan.artifact_portability.issues.len() - 10
        )?;
    }
    eyre::bail!(message)
}

fn core_coordinates(
    loader_toolchain: &LoaderToolchainPlan,
    mapping_channel: &str,
    mapping_version: &str,
    userdev: &ForgeUserdevPlan,
    mcp_config: Option<&McpConfigPlan>,
    dependencies: &[ParsedDependency],
) -> eyre::Result<Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>> {
    let mut coordinates = Vec::new();

    if let Some(sources) = userdev
        .sources
        .as_deref()
        .or(loader_toolchain.sources_coordinate.as_deref())
    {
        let artifact_id = if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
            "neoforge-sources"
        } else {
            "forge-sources"
        };
        coordinates.push((
            ArtifactId::from(artifact_id),
            MavenCoordinate::parse(sources)?,
            ArtifactPurpose::from("Loader source patch application"),
        ));
    }

    if let Some(universal) = userdev
        .universal
        .as_deref()
        .or(loader_toolchain.universal_coordinate.as_deref())
    {
        let artifact_id = if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
            "neoforge-universal"
        } else {
            "forge-universal"
        };
        coordinates.push((
            ArtifactId::from(artifact_id),
            MavenCoordinate::parse(universal)?,
            ArtifactPurpose::from("Loader userdev resource merge"),
        ));
    }

    let neoform_coordinate = userdev.neo_form.as_ref().or_else(|| {
        (loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev)
            .then_some(userdev.mcp.as_ref())
            .flatten()
    });
    if let Some(neo_form) = neoform_coordinate {
        coordinates.push((
            ArtifactId::from("neoform-config"),
            MavenCoordinate::parse(neo_form)?,
            ArtifactPurpose::from("NeoForm clean-slate Minecraft pipeline"),
        ));
    }

    if mapping_channel == "parchment" {
        coordinates.push((
            ArtifactId::from("parchment-data"),
            parchment_coordinate(mapping_version)?,
            ArtifactPurpose::from("Parchment names layered over official mappings"),
        ));
    }

    add_userdev_library_coordinates(&mut coordinates, userdev)?;

    if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        coordinates.push((
            ArtifactId::from("tool-neoform-runtime"),
            MavenCoordinate::parse(NEOFORM_RUNTIME_COORDINATE)?,
            ArtifactPurpose::from("NeoForm Runtime userdev execution"),
        ));
        add_userdev_test_library_coordinates(&mut coordinates, userdev)?;
        add_project_tool_coordinates(&mut coordinates, dependencies)?;
        return Ok(coordinates);
    }

    if let Some(binpatcher) = &userdev.binpatcher {
        coordinates.push((
            ArtifactId::from("forge-binarypatcher"),
            MavenCoordinate::parse(binpatcher)?,
            ArtifactPurpose::from("Forge binary patch application"),
        ));
    }

    add_mcp_tool_coordinates(&mut coordinates, mcp_config)?;

    add_project_tool_coordinates(&mut coordinates, dependencies)?;

    Ok(coordinates)
}

fn add_userdev_library_coordinates(
    coordinates: &mut Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>,
    userdev: &ForgeUserdevPlan,
) -> eyre::Result<()> {
    let mut userdev_coordinates = userdev
        .libraries
        .iter()
        .chain(userdev.modules.iter())
        .cloned()
        .collect::<Vec<_>>();
    userdev_coordinates.sort();
    userdev_coordinates.dedup();

    for (index, coordinate) in userdev_coordinates.iter().enumerate() {
        coordinates.push((
            ArtifactId::from(format!("forge-userdev-library-{index}")),
            MavenCoordinate::parse(coordinate)?,
            ArtifactPurpose::from("Forge userdev compile classpath"),
        ));
    }
    Ok(())
}

fn add_userdev_test_library_coordinates(
    coordinates: &mut Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>,
    userdev: &ForgeUserdevPlan,
) -> eyre::Result<()> {
    for (index, coordinate) in userdev.test_libraries.iter().enumerate() {
        coordinates.push((
            ArtifactId::from(format!("forge-userdev-test-library-{index}")),
            MavenCoordinate::parse(coordinate)?,
            ArtifactPurpose::from("Forge userdev game-test runtime classpath"),
        ));
    }
    Ok(())
}

fn should_plan_project_dependency(
    loader_toolchain: &LoaderToolchainPlan,
    dependency: &ParsedDependency,
) -> bool {
    if dependency.coordinate.to_string() == loader_toolchain.base_coordinate {
        return false;
    }

    if !is_planned_project_dependency_configuration(&dependency.configuration) {
        return false;
    }

    true
}

fn is_planned_project_dependency_configuration(configuration: &str) -> bool {
    matches!(
        configuration,
        "implementation"
            | "compileOnly"
            | "runtimeOnly"
            | "jarJar"
            | "annotationProcessor"
            | "gametestImplementation"
            | "gametestCompileOnly"
            | "gametestRuntimeOnly"
    )
}

fn add_transitive_runtime_dependency_plans(
    resolver: &Resolver,
    mut dependencies: Vec<DependencyPlan>,
) -> eyre::Result<Vec<DependencyPlan>> {
    let mut seen = dependencies
        .iter()
        .map(|dependency| dependency.resolved_notation.clone())
        .collect::<BTreeSet<_>>();
    let mut queue = dependencies
        .iter()
        .filter(|dependency| is_runtime_transitive_root(&dependency.configuration))
        .filter_map(|dependency| MavenCoordinate::parse(&dependency.resolved_notation).ok())
        .collect::<Vec<_>>();

    while let Some(root) = queue.pop() {
        resolver.cancellation_token.bail_if_cancelled()?;
        for coordinate in resolver.resolve_pom_runtime_dependencies(&root)? {
            let key = coordinate.to_string();
            if !seen.insert(key) {
                continue;
            }
            let dependency = resolver.resolve_dependency("transitiveRuntime", &coordinate)?;
            queue.push(MavenCoordinate::parse(&dependency.resolved_notation)?);
            dependencies.push(dependency);
        }
    }

    Ok(dependencies)
}

fn is_runtime_transitive_root(configuration: &str) -> bool {
    matches!(
        configuration,
        "implementation" | "runtimeOnly" | "gametestImplementation" | "gametestRuntimeOnly"
    )
}

fn add_project_tool_coordinates(
    coordinates: &mut Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>,
    dependencies: &[ParsedDependency],
) -> eyre::Result<()> {
    add_project_compile_annotation_coordinates(coordinates)?;
    for dependency in dependencies {
        if dependency.configuration == "annotationProcessor" {
            coordinates.push((
                ArtifactId::from("mixin-annotation-processor"),
                dependency.coordinate.clone(),
                ArtifactPurpose::from("Mixin refmap generation"),
            ));
        } else if dependency.configuration == "antlr" {
            for (index, coordinate) in antlr_classpath_coordinates(&dependency.coordinate.version)?
                .into_iter()
                .enumerate()
            {
                let artifact_id = if index == 0 {
                    "antlr-tool".to_string()
                } else {
                    format!("antlr-tool-dependency-{index}")
                };
                coordinates.push((
                    ArtifactId::from(artifact_id),
                    MavenCoordinate::parse(&coordinate)?,
                    ArtifactPurpose::from("ANTLR grammar generation"),
                ));
            }
        }
    }
    Ok(())
}

fn add_project_compile_annotation_coordinates(
    coordinates: &mut Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>,
) -> eyre::Result<()> {
    for (artifact_id, coordinate) in PROJECT_COMPILE_ANNOTATION_COORDINATES {
        coordinates.push((
            ArtifactId::from(artifact_id),
            MavenCoordinate::parse(coordinate)?,
            ArtifactPurpose::from("Project compile annotations"),
        ));
    }
    Ok(())
}

fn add_mcp_tool_coordinates(
    coordinates: &mut Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>,
    mcp_config: Option<&McpConfigPlan>,
) -> eyre::Result<()> {
    for (id, function_name, fallback_coordinate, required_for) in [
        (
            "tool-installer-tools-1-2",
            "mergeMappings",
            "net.minecraftforge:installertools:1.2.0:fatjar",
            "MCPConfig MERGE_MAPPING function",
        ),
        (
            "tool-installer-tools-1-3",
            "bundleExtractJar",
            "net.minecraftforge:installertools:1.3.0:fatjar",
            "MCPConfig server bundle extraction",
        ),
        (
            "tool-forgeflower",
            "decompile",
            "net.minecraftforge:forgeflower:1.5.605.9",
            "MCPConfig decompile function",
        ),
        (
            "tool-mergetool-1-1-5",
            "merge",
            "net.minecraftforge:mergetool:1.1.5:fatjar",
            "MCPConfig client/server merge function",
        ),
        (
            "tool-fart",
            "rename",
            "net.minecraftforge:ForgeAutoRenamingTool:0.1.22:all",
            "MCPConfig rename and jar remapping",
        ),
        (
            "tool-diffpatch",
            "patch",
            "net.minecraftforge:DiffPatch:2.0.12:all",
            "MCPConfig and Forge source patch application",
        ),
        (
            "tool-access-transformers",
            "accessTransformers",
            "net.minecraftforge:accesstransformers:8.0.4:fatjar",
            "Forge access transformer application",
        ),
        (
            "tool-specialsource",
            "reobfuscate",
            "net.md-5:SpecialSource:1.11.0:shaded",
            "Forge-style jar reobfuscation",
        ),
    ] {
        coordinates.push((
            ArtifactId::from(id),
            MavenCoordinate::parse(&mcp_function_coordinate(
                mcp_config,
                function_name,
                fallback_coordinate,
            ))?,
            ArtifactPurpose::from(required_for),
        ));
    }

    Ok(())
}

fn mcp_function_coordinate(
    mcp_config: Option<&McpConfigPlan>,
    function_name: &str,
    fallback_coordinate: &str,
) -> String {
    mcp_config
        .and_then(|config| config.function_coordinates.get(function_name))
        .cloned()
        .unwrap_or_else(|| fallback_coordinate.to_string())
}

fn parchment_coordinate(mapping_version: &str) -> eyre::Result<MavenCoordinate> {
    let parts = mapping_version.split('-').collect::<Vec<_>>();
    let (mc_version, date) = match parts.as_slice() {
        [date, mc_version] if looks_like_parchment_date(date) => (*mc_version, *date),
        [mc_version, date] if looks_like_parchment_date(date) => (*mc_version, *date),
        [mc_version, date, _target_version] if looks_like_parchment_date(date) => {
            (*mc_version, *date)
        }
        _ => {
            eyre::bail!("Unsupported parchment mapping_version: {mapping_version}");
        }
    };
    MavenCoordinate::parse(&format!(
        "org.parchmentmc.data:parchment-{mc_version}:{date}@zip"
    ))
}

fn looks_like_parchment_date(value: &str) -> bool {
    let mut parts = value.split('.');
    let Some(year) = parts.next() else {
        return false;
    };
    year.len() == 4
        && year.chars().all(|character| character.is_ascii_digit())
        && parts.all(|part| {
            !part.is_empty() && part.chars().all(|character| character.is_ascii_digit())
        })
}

fn resolve_mapping_settings(
    properties: &BTreeMap<String, String>,
    minecraft_version: &str,
) -> (String, String) {
    if let (Some(channel), Some(version)) = (
        properties.get("mapping_channel"),
        properties.get("mapping_version"),
    ) {
        return (channel.clone(), version.clone());
    }

    if let (Some(parchment_minecraft), Some(parchment_version)) = (
        properties.get("neogradle.subsystems.parchment.minecraftVersion"),
        properties.get("neogradle.subsystems.parchment.mappingsVersion"),
    ) {
        return (
            "parchment".to_string(),
            format!("{parchment_version}-{parchment_minecraft}"),
        );
    }

    ("official".to_string(), minecraft_version.to_string())
}

fn resolve_loader_toolchain(
    dependencies: &[ParsedDependency],
    minecraft_version: &str,
    loader_version: &str,
) -> eyre::Result<LoaderToolchainPlan> {
    if let Some(dependency) = dependencies
        .iter()
        .find(|dependency| dependency.configuration == "minecraft")
    {
        let coordinate = dependency.coordinate.clone();
        let kind = if coordinate.group == "net.minecraftforge" && coordinate.artifact == "forge" {
            LoaderToolchainKind::ForgeGradleForge
        } else if coordinate.group == "net.neoforged" && coordinate.artifact == "forge" {
            LoaderToolchainKind::ForgeGradleNeoForgeGroup
        } else {
            eyre::bail!(
                "Unsupported ForgeGradle minecraft dependency: {}",
                coordinate
            );
        };

        return Ok(loader_toolchain_plan(kind, &coordinate));
    }

    if let Some(dependency) = dependencies.iter().find(|dependency| {
        dependency.coordinate.group == "net.neoforged"
            && dependency.coordinate.artifact == "neoforge"
    }) {
        return Ok(loader_toolchain_plan(
            LoaderToolchainKind::NeoGradleUserdev,
            &dependency.coordinate,
        ));
    }

    let fallback = MavenCoordinate::parse(&format!(
        "net.minecraftforge:forge:{minecraft_version}-{loader_version}"
    ))?;
    Ok(loader_toolchain_plan(
        LoaderToolchainKind::ForgeGradleForge,
        &fallback,
    ))
}

fn loader_toolchain_plan(
    kind: LoaderToolchainKind,
    base_coordinate: &MavenCoordinate,
) -> LoaderToolchainPlan {
    let userdev_coordinate = base_coordinate.with_classifier("userdev");
    let sources_coordinate = base_coordinate.with_classifier("sources");
    let universal_coordinate = base_coordinate.with_classifier("universal");

    LoaderToolchainPlan {
        kind,
        base_coordinate: base_coordinate.to_string(),
        userdev_coordinate: userdev_coordinate.to_string(),
        sources_coordinate: Some(sources_coordinate.to_string()),
        universal_coordinate: Some(universal_coordinate.to_string()),
    }
}

fn resolve_minecraft_plan(
    minecraft_cache: &Path,
    client: &Client,
    minecraft_version: &str,
    cancellation_token: &CancellationToken,
) -> eyre::Result<MinecraftPlan> {
    cancellation_token.bail_if_cancelled()?;
    fs::create_dir_all(minecraft_cache)?;
    let metadata_cache = minecraft_cache.join("metadata");
    fs::create_dir_all(&metadata_cache)?;
    let manifest_path = metadata_cache.join("version_manifest_v2.json");
    download_to_path(
        cancellation_token,
        client,
        VERSION_MANIFEST_URL,
        &manifest_path,
    )?;
    cancellation_token.bail_if_cancelled()?;
    let manifest: MojangVersionManifest = read_json_file(&manifest_path)?;
    let version_url = manifest
        .versions
        .iter()
        .find_map(|version| (version.id == minecraft_version).then_some(version.url.as_str()))
        .ok_or_else(|| {
            eyre::eyre!("Minecraft version {minecraft_version} not found in Mojang manifest")
        })?;

    let version_cache = minecraft_cache.join("versions").join(minecraft_version);
    fs::create_dir_all(&version_cache)?;
    let version_json_path = version_cache.join("version.json");
    download_to_path(cancellation_token, client, version_url, &version_json_path)?;
    cancellation_token.bail_if_cancelled()?;
    let version_json: MinecraftVersionJson = read_json_file(&version_json_path)?;
    let libraries_count = version_json.libraries.len();

    Ok(MinecraftPlan {
        version_manifest: plain_artifact(
            ArtifactId::from("minecraft-version-manifest"),
            VERSION_MANIFEST_URL,
            manifest_path,
            ArtifactPurpose::from("Minecraft version discovery"),
        )?,
        version_json: plain_artifact(
            ArtifactId::from("minecraft-version-json"),
            version_url,
            version_json_path,
            ArtifactPurpose::from("Minecraft libraries and downloads"),
        )?,
        client_jar_url: version_json.downloads.client.url,
        server_jar_url: version_json.downloads.server.url,
        client_mappings_url: version_json
            .downloads
            .client_mappings
            .map(|download| download.url),
        server_mappings_url: version_json
            .downloads
            .server_mappings
            .map(|download| download.url),
        libraries_count,
    })
}

fn read_forge_userdev(artifact: &ArtifactPlan) -> eyre::Result<ForgeUserdevPlan> {
    let config: ForgeUserdevConfig = read_zip_json_entry(&artifact.cache_path, "config.json")?;
    let binpatcher = config
        .binpatcher
        .as_ref()
        .and_then(|binpatcher| binpatcher.version.clone());
    let modules = config.modules;
    let libraries = config.libraries;
    let module_count = modules.len();
    let library_count = libraries.len();
    let test_libraries = config.test_libraries;
    let run_configs = config.runs.keys().cloned().collect();

    Ok(ForgeUserdevPlan {
        artifact: ArtifactPlan {
            id: artifact.id.clone(),
            coordinate: artifact.coordinate.clone(),
            repository: artifact.repository.clone(),
            url: artifact.url.clone(),
            cache_path: artifact.cache_path.clone(),
            sha1: artifact.sha1,
            downloaded: artifact.downloaded,
            required_for: artifact.required_for.clone(),
            provenance: artifact.provenance.clone(),
        },
        spec: config.spec,
        mcp: config.mcp,
        neo_form: config.neo_form,
        sources: config.sources,
        universal: config.universal,
        binpatcher,
        patches: config.patches,
        patches_original_prefix: config.patches_original_prefix,
        patches_modified_prefix: config.patches_modified_prefix,
        access_transformers: config.ats.map_or_else(Vec::new, StringList::into_vec),
        side_strippers: config.sass.map_or_else(Vec::new, StringList::into_vec),
        modules,
        libraries,
        module_count,
        library_count,
        test_libraries,
        run_configs,
    })
}

fn read_mcp_config(artifact: &ArtifactPlan) -> eyre::Result<McpConfigPlan> {
    let config: McpConfigJson = read_zip_json_entry(&artifact.cache_path, "config.json")?;
    let joined_steps = config
        .steps
        .joined
        .iter()
        .filter_map(|step| step.name.as_ref().or(step.step_type.as_ref()).cloned())
        .collect();
    let mut data_keys = Vec::new();
    if config.data.inject.is_some() {
        data_keys.push("inject".to_string());
    }
    if config.data.mappings.is_some() {
        data_keys.push("mappings".to_string());
    }
    if config
        .data
        .patches
        .as_ref()
        .is_some_and(McpPatchData::has_any_patch_root)
    {
        data_keys.push("patches".to_string());
    }
    let function_count = config
        .functions
        .values()
        .filter(|function| function.has_declared_config())
        .count();
    let function_coordinates = config
        .functions
        .iter()
        .filter_map(|(name, function)| {
            function
                .version
                .as_ref()
                .map(|version| (name.clone(), version.clone()))
        })
        .collect();

    Ok(McpConfigPlan {
        artifact: ArtifactPlan {
            id: artifact.id.clone(),
            coordinate: artifact.coordinate.clone(),
            repository: artifact.repository.clone(),
            url: artifact.url.clone(),
            cache_path: artifact.cache_path.clone(),
            sha1: artifact.sha1,
            downloaded: artifact.downloaded,
            required_for: artifact.required_for.clone(),
            provenance: artifact.provenance.clone(),
        },
        joined_steps,
        function_coordinates,
        function_count,
        data_keys,
        library_count: config.libraries.values().map(Vec::len).sum(),
    })
}

#[derive(Clone, Debug)]
struct ParsedDependency {
    configuration: String,
    coordinate: MavenCoordinate,
    fg_deobf: bool,
}

fn parse_dependency_script(
    path: &Path,
    properties: &BTreeMap<String, String>,
) -> eyre::Result<Vec<ParsedDependency>> {
    let content = fs::read_to_string(path)
        .wrap_err_with(|| format!("Failed to read dependency script: {}", path.display()))?;
    let mut dependencies = Vec::new();

    for raw_line in content.lines() {
        let line = raw_line
            .split("//")
            .next()
            .unwrap_or_default()
            .trim()
            .trim_end_matches(';')
            .trim();
        if line.is_empty() || line == "dependencies {" || line == "}" {
            continue;
        }

        if line.starts_with("jarJar(") {
            let Some(notation) = extract_quoted(line) else {
                continue;
            };
            let notation = interpolate_properties(&notation, properties);
            dependencies.push(ParsedDependency {
                configuration: "jarJar".to_string(),
                coordinate: MavenCoordinate::parse(&notation)?,
                fg_deobf: false,
            });
            continue;
        }

        let Some((configuration, rest)) = line.split_once(char::is_whitespace) else {
            continue;
        };
        if !is_dependency_configuration(configuration) {
            continue;
        }
        let rest = rest.trim();
        let fg_deobf = rest.contains("fg.deobf");
        let Some(notation) = extract_quoted(rest) else {
            continue;
        };
        let notation = interpolate_properties(&notation, properties);
        dependencies.push(ParsedDependency {
            configuration: configuration.to_string(),
            coordinate: MavenCoordinate::parse(&notation)?,
            fg_deobf,
        });
    }

    Ok(dependencies)
}

fn parse_maven_pom_runtime_dependencies(
    pom: &str,
    parent: &MavenCoordinate,
) -> Vec<MavenCoordinate> {
    let properties = parse_maven_pom_properties(pom, parent);
    let search_start = pom
        .find("</dependencyManagement>")
        .map_or(0, |index| index + "</dependencyManagement>".len());
    let Some(dependencies_xml) = extract_xml_section(&pom[search_start..], "dependencies") else {
        return Vec::new();
    };

    let mut coordinates = Vec::new();
    for block in dependencies_xml.split("<dependency").skip(1) {
        let Some(start) = block.find('>') else {
            continue;
        };
        let Some(end) = block[start + 1..].find("</dependency>") else {
            continue;
        };
        let dependency_xml = &block[start + 1..start + 1 + end];
        let scope = extract_xml_tag_text(dependency_xml, "scope").unwrap_or_default();
        if !scope.is_empty() && scope != "compile" && scope != "runtime" {
            continue;
        }
        if extract_xml_tag_text(dependency_xml, "optional")
            .is_some_and(|optional| optional.eq_ignore_ascii_case("true"))
        {
            continue;
        }
        let dependency_type =
            extract_xml_tag_text(dependency_xml, "type").unwrap_or_else(|| "jar".to_string());
        if dependency_type != "jar" {
            continue;
        }

        let Some(group) = extract_resolved_pom_tag(dependency_xml, "groupId", &properties) else {
            continue;
        };
        let Some(artifact) = extract_resolved_pom_tag(dependency_xml, "artifactId", &properties)
        else {
            continue;
        };
        let Some(version) = extract_resolved_pom_tag(dependency_xml, "version", &properties) else {
            continue;
        };
        if version.contains('[') || version.contains('(') {
            continue;
        }
        let classifier = extract_resolved_pom_tag(dependency_xml, "classifier", &properties);
        coordinates.push(MavenCoordinate {
            group,
            artifact,
            version,
            classifier,
            extension: "jar".to_string(),
        });
    }

    coordinates
}

fn parse_maven_pom_properties(pom: &str, parent: &MavenCoordinate) -> BTreeMap<String, String> {
    let mut properties = BTreeMap::from([
        ("project.groupId".to_string(), parent.group.clone()),
        ("pom.groupId".to_string(), parent.group.clone()),
        ("project.artifactId".to_string(), parent.artifact.clone()),
        ("pom.artifactId".to_string(), parent.artifact.clone()),
        ("project.version".to_string(), parent.version.clone()),
        ("pom.version".to_string(), parent.version.clone()),
        ("version".to_string(), parent.version.clone()),
    ]);
    if let Some(properties_xml) = extract_xml_section(pom, "properties") {
        for block in properties_xml.split('<').skip(1) {
            let Some((tag, rest)) = block.split_once('>') else {
                continue;
            };
            if tag.starts_with('/') || tag.contains(char::is_whitespace) {
                continue;
            }
            let end_tag = format!("</{tag}>");
            let Some(end) = rest.find(&end_tag) else {
                continue;
            };
            properties.insert(tag.to_string(), strip_xml_cdata(rest[..end].trim()));
        }
    }
    properties
}

fn extract_resolved_pom_tag(
    input: &str,
    tag: &str,
    properties: &BTreeMap<String, String>,
) -> Option<String> {
    let value = extract_xml_tag_text(input, tag)?;
    let mut resolved = value;
    for _ in 0..8 {
        let Some(start) = resolved.find("${") else {
            return Some(resolved);
        };
        let property_start = start + "${".len();
        let end = resolved[property_start..].find('}')? + property_start;
        let property_name = &resolved[property_start..end];
        let replacement = properties.get(property_name)?;
        resolved.replace_range(start..=end, replacement);
    }
    None
}

fn extract_xml_section(input: &str, tag: &str) -> Option<String> {
    let start_tag = format!("<{tag}>");
    let end_tag = format!("</{tag}>");
    let start = input.find(&start_tag)? + start_tag.len();
    let end = input[start..].find(&end_tag)? + start;
    Some(input[start..end].to_string())
}

fn extract_xml_tag_text(input: &str, tag: &str) -> Option<String> {
    let start_tag = format!("<{tag}>");
    let end_tag = format!("</{tag}>");
    let start = input.find(&start_tag)? + start_tag.len();
    let end = input[start..].find(&end_tag)? + start;
    Some(strip_xml_cdata(input[start..end].trim()))
}

fn strip_xml_cdata(input: &str) -> String {
    input
        .strip_prefix("<![CDATA[")
        .and_then(|value| value.strip_suffix("]]>"))
        .unwrap_or(input)
        .to_string()
}

fn is_dependency_configuration(configuration: &str) -> bool {
    matches!(
        configuration,
        "minecraft"
            | "annotationProcessor"
            | "antlr"
            | "implementation"
            | "compileOnly"
            | "runtimeOnly"
            | "gametestImplementation"
            | "gametestCompileOnly"
            | "gametestRuntimeOnly"
            | "testImplementation"
            | "testCompileOnly"
            | "testRuntimeOnly"
            | "testAnnotationProcessor"
    )
}

fn extract_quoted(input: &str) -> Option<String> {
    let mut chars = input.char_indices();
    let (start_index, quote) =
        chars.find(|(_, character)| *character == '"' || *character == '\'')?;
    let value_start = start_index + quote.len_utf8();
    let end_offset = input[value_start..].find(quote)?;
    Some(input[value_start..value_start + end_offset].to_string())
}

fn interpolate_properties(input: &str, properties: &BTreeMap<String, String>) -> String {
    let mut output = input.to_string();
    let mut aliases = properties.clone();
    if let Some(minecraft_version) = properties.get("minecraft_version") {
        aliases.insert("mc_version".to_string(), minecraft_version.clone());
    }

    for (key, value) in aliases {
        output = output.replace(&format!("${{{key}}}"), &value);
    }

    output
}

fn build_graph(
    minecraft_version: &str,
    rust_output_jar: &Path,
    dependencies: &[DependencyPlan],
    loader_toolchain: &LoaderToolchainPlan,
) -> Vec<GraphNode> {
    let mut graph = vec![
        graph_ready(
            "resolve-project-config",
            vec!["gradle.properties", "versioned Gradle fragments"],
            vec!["build/sfm-toolchain/state/last-plan.json"],
        ),
        graph_ready(
            "resolve-maven-and-minecraft-inputs",
            vec![
                "Maven repositories",
                VERSION_MANIFEST_URL,
                "dependencies.gradle",
            ],
            vec!["$sfm-cache/maven", "$sfm-cache/minecraft"],
        ),
    ];

    if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        graph.push(graph_planned(
            "execute-neoform-userdev",
            "NeoForm userdev inputs are detected; execution support still needs implementation",
            vec![&loader_toolchain.userdev_coordinate],
            vec!["build/sfm-toolchain/neoform"],
        ));
    } else {
        graph.push(graph_planned(
            "execute-mcp-config-joined",
            "MCPConfig joined runtime inputs will be fingerprinted before execution",
            vec![
                &format!("Minecraft {minecraft_version} client/server jars"),
                "MCPConfig config.json",
            ],
            vec!["build/sfm-toolchain/mcp/joined"],
        ));
        graph.push(graph_planned(
            "execute-forge-userdev",
            "Forge userdev inputs will be fingerprinted before execution",
            vec![
                &loader_toolchain.userdev_coordinate,
                "MCPConfig joined outputs",
            ],
            vec!["build/sfm-toolchain/forge"],
        ));
    }

    graph.extend([
        graph_planned(
            "deobfuscate-mod-dependencies",
            "fg.deobf dependency jars will be remapped into SFM-owned cache",
            vec![&format!(
                "{} active fg.deobf dependencies",
                dependencies.len()
            )],
            vec!["build/sfm-toolchain/dependencies"],
        ),
        graph_planned(
            "compile-project",
            "Project sources, resources, classpath, and processors will be fingerprinted before javac",
            vec![
                "src/main/java",
                "src/main/antlr",
                "src/main/resources",
                "mapped Forge/Minecraft jar",
            ],
            vec!["build/sfm-toolchain/project/classes"],
        ),
        graph_planned(
            "package-and-reobfuscate-jar",
            "Development jar and reobfuscation mappings will be fingerprinted before packaging",
            vec!["compiled classes", "expanded resources", "MCP mappings"],
            vec![&rust_output_jar.display().to_string()],
        ),
    ]);

    graph
}

fn graph_ready(id: &str, inputs: Vec<&str>, outputs: Vec<&str>) -> GraphNode {
    GraphNode {
        id: id.to_string(),
        kind: "planning".to_string(),
        status: NodeStatus::Ready,
        inputs: inputs.into_iter().map(str::to_string).collect(),
        outputs: outputs.into_iter().map(str::to_string).collect(),
        rebuild_reason: "Planner inputs were read during this invocation".to_string(),
    }
}

fn graph_planned(id: &str, reason: &str, inputs: Vec<&str>, outputs: Vec<&str>) -> GraphNode {
    GraphNode {
        id: id.to_string(),
        kind: "execution".to_string(),
        status: NodeStatus::Planned,
        inputs: inputs.into_iter().map(str::to_string).collect(),
        outputs: outputs.into_iter().map(str::to_string).collect(),
        rebuild_reason: reason.to_string(),
    }
}

fn ensure_forge_gradle_execution_supported(plan: &BuildPlan) -> eyre::Result<()> {
    if plan.forge_userdev.is_none() || plan.mcp_config.is_none() {
        eyre::bail!(
            "Minecraft {} did not resolve the ForgeGradle userdev plus MCPConfig inputs required by the current executor.",
            plan.minecraft_version
        );
    }

    Ok(())
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum BuildTarget {
    Jar,
    Run,
}

#[expect(
    clippy::too_many_lines,
    reason = "build orchestration keeps the node order and timing output visible."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %plan.branch_name,
        mc = %plan.minecraft_version,
        loader = ?plan.loader_toolchain.kind,
        graph_nodes = plan.graph.len(),
        explain_rebuild,
        target = ?target,
    )
)]
fn execute_build(
    plan: &BuildPlan,
    explain_rebuild: bool,
    target: BuildTarget,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let context = ExecutionContext::new(plan, cancellation_token.clone())?;
    let total_started = Instant::now();

    if explain_rebuild {
        for node in &plan.graph {
            tracing::info!("{}: {}", node.id, node.rebuild_reason);
        }
    }

    tracing::info!("Build node resolve-project-config: recording plan state");
    context.bail_if_cancelled()?;
    context.write_node_state(
        "resolve-project-config",
        &["gradle.properties", "versioned Gradle fragments"],
        &[plan.state_dir.join("last-plan.json")],
        "complete",
    )?;
    tracing::info!("Build node resolve-maven-and-minecraft-inputs: recording resolved artifacts");
    context.bail_if_cancelled()?;
    context.write_node_state(
        "resolve-maven-and-minecraft-inputs",
        &[
            "Maven repositories",
            VERSION_MANIFEST_URL,
            "dependencies.gradle",
        ],
        &[
            plan.maven_cache_dir.clone(),
            plan.minecraft_version_cache_dir.clone(),
            plan.minecraft_libraries_dir.clone(),
            plan.minecraft_assets_dir.clone(),
        ],
        "complete",
    )?;

    if plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let started = Instant::now();
        tracing::info!("Build node execute-neoform-userdev: start");
        context.bail_if_cancelled()?;
        execute_neoform_userdev(&context)?;
        context.bail_if_cancelled()?;
        tracing::info!(
            "Build node execute-neoform-userdev: done in {} ms",
            started.elapsed().as_millis()
        );
    } else {
        ensure_forge_gradle_execution_supported(plan)?;
        let started = Instant::now();
        tracing::info!("Build node execute-mcp-config-joined: start");
        context.bail_if_cancelled()?;
        execute_mcp_config_joined(&context)?;
        context.bail_if_cancelled()?;
        tracing::info!(
            "Build node execute-mcp-config-joined: done in {} ms",
            started.elapsed().as_millis()
        );

        let started = Instant::now();
        tracing::info!("Build node execute-forge-userdev: start");
        context.bail_if_cancelled()?;
        execute_forge_userdev(&context)?;
        context.bail_if_cancelled()?;
        tracing::info!(
            "Build node execute-forge-userdev: done in {} ms",
            started.elapsed().as_millis()
        );
    }
    let started = Instant::now();
    tracing::info!("Build node deobfuscate-mod-dependencies: start");
    context.bail_if_cancelled()?;
    execute_dependency_deobf(&context)?;
    context.bail_if_cancelled()?;
    tracing::info!(
        "Build node deobfuscate-mod-dependencies: done in {} ms",
        started.elapsed().as_millis()
    );
    let started = Instant::now();
    tracing::info!("Build node compile-project: start");
    context.bail_if_cancelled()?;
    execute_project_compile(&context)?;
    context.bail_if_cancelled()?;
    tracing::info!(
        "Build node compile-project: done in {} ms",
        started.elapsed().as_millis()
    );
    if target == BuildTarget::Jar {
        let started = Instant::now();
        tracing::info!("Build node package-and-reobfuscate-jar: start");
        context.bail_if_cancelled()?;
        execute_package_and_reobfuscate(&context)?;
        context.bail_if_cancelled()?;
        tracing::info!(
            "Build node package-and-reobfuscate-jar: done in {} ms",
            started.elapsed().as_millis()
        );

        if !plan.rust_output_jar.is_file() {
            eyre::bail!(
                "Build finished without producing Rust output jar: {}",
                plan.rust_output_jar.display()
            );
        }

        tracing::info!(
            "Rust jar build completed in {} ms",
            total_started.elapsed().as_millis()
        );
    } else {
        tracing::info!(
            "Rust run build outputs prepared in {} ms",
            total_started.elapsed().as_millis()
        );
    }

    Ok(())
}

impl RunKind {
    const fn userdev_name(self) -> &'static str {
        match self {
            Self::Client | Self::ClientSmoke | Self::ClientPuppet => "client",
            Self::Server => "server",
            Self::Data => "data",
            Self::GameTestServer => "gameTestServer",
            Self::Test => "test",
        }
    }

    const fn userdev_names(self) -> &'static [&'static str] {
        match self {
            Self::Data => &["data", "clientData"],
            Self::Client | Self::ClientSmoke | Self::ClientPuppet => &["client"],
            Self::Server => &["server"],
            Self::GameTestServer => &["gameTestServer"],
            Self::Test => &["test"],
        }
    }

    const fn command_name(self) -> &'static str {
        match self {
            Self::Client => "runClient",
            Self::ClientSmoke => "runClientSmoke",
            Self::ClientPuppet => "runClientPuppet",
            Self::Server => "runServer",
            Self::Data => "runData",
            Self::GameTestServer => "runGameTestServer",
            Self::Test => "runTest",
        }
    }

    const fn working_dir_name(self) -> &'static str {
        match self {
            Self::Client => "run",
            Self::ClientSmoke => "runClientSmoke",
            Self::ClientPuppet => "runClientPuppet",
            Self::Server => "runServer",
            Self::Data => "runData",
            Self::GameTestServer => "runGameTest",
            Self::Test => "runTest",
        }
    }

    const fn optional_source_set(self) -> &'static str {
        match self {
            Self::Client
            | Self::ClientSmoke
            | Self::ClientPuppet
            | Self::Server
            | Self::GameTestServer => "gametest",
            Self::Data => "datagen",
            Self::Test => "test",
        }
    }

    const fn enables_game_tests(self) -> bool {
        matches!(
            self,
            Self::Client
                | Self::ClientSmoke
                | Self::ClientPuppet
                | Self::Server
                | Self::GameTestServer
        )
    }

    const fn game_test_max_program_run_millis(self) -> &'static str {
        match self {
            Self::Client | Self::ClientSmoke | Self::ClientPuppet => "1000",
            Self::Server | Self::GameTestServer | Self::Data | Self::Test => "150",
        }
    }

    const fn automation_mode(self) -> Option<&'static str> {
        match self {
            Self::ClientSmoke => Some("smoke"),
            Self::ClientPuppet => Some("puppet"),
            _ => None,
        }
    }

    const fn launch_timeout(self) -> Option<Duration> {
        match self {
            Self::ClientSmoke => Some(Duration::from_mins(2)),
            Self::ClientPuppet => Some(Duration::from_mins(15)),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Default, Facet)]
#[facet(rename_all = "camelCase")]
struct ForgeRunConfig {
    #[facet(default)]
    main: String,
    #[facet(default)]
    args: Vec<String>,
    #[facet(default)]
    jvm_args: Vec<String>,
    #[facet(default)]
    env: BTreeMap<String, String>,
    #[facet(default)]
    props: BTreeMap<String, String>,
}

#[derive(Debug)]
struct MinecraftAssets {
    root: PathBuf,
    index_id: String,
}

#[derive(Debug)]
struct RunClasspath {
    legacy: Vec<PathBuf>,
    userdev_mods: Vec<PathBuf>,
}

#[derive(Debug)]
struct LaunchOutput {
    status: ExitStatus,
    combined: String,
    timed_out: bool,
    cancelled: bool,
}

#[derive(Debug)]
struct CancellableOutput {
    status: ExitStatus,
    stdout: Vec<u8>,
    stderr: Vec<u8>,
    cancelled: bool,
}

#[expect(
    clippy::too_many_lines,
    reason = "Run launch orchestration intentionally mirrors Forge userdev config shape."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %plan.branch_name,
        mc = %plan.minecraft_version,
        kind = kind.command_name(),
        loader = ?plan.loader_toolchain.kind,
        dry_run,
    )
)]
fn execute_run(
    plan: &BuildPlan,
    kind: RunKind,
    dry_run: bool,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    if matches!(kind, RunKind::Test) {
        return execute_junit_tests(
            plan,
            dry_run,
            &RunTestOptions::default(),
            cancellation_token,
        );
    }

    let context = ExecutionContext::new(plan, cancellation_token.clone())?;
    let run_config = read_forge_run_config(&context, kind)?;
    context.bail_if_cancelled()?;
    if run_config.main.is_empty() {
        eyre::bail!(
            "Forge userdev run config {} did not declare a main class",
            kind.userdev_name()
        );
    }
    let launch_main = run_config.main.clone();

    let run_state_dir = plan.cache_dir.join("run").join(kind.command_name());
    fs::create_dir_all(&run_state_dir)?;
    let working_dir = plan.minecraft_dir.join(kind.working_dir_name());
    fs::create_dir_all(&working_dir)?;
    if matches!(kind, RunKind::GameTestServer) {
        clean_gametest_server_world(&plan.minecraft_dir, &working_dir)?;
    }
    if matches!(kind, RunKind::ClientPuppet) {
        clean_client_puppet_world(&plan.minecraft_dir, &working_dir)?;
    }
    let automation_options_path =
        prepare_client_automation_options(&plan.minecraft_dir, &working_dir, kind)?;

    let run_lockfile = if plan.refresh {
        None
    } else {
        plan.lockfile.clone()
    };
    let resolver = Resolver::new(
        plan.maven_cache_dir.clone(),
        plan.repositories.clone(),
        false,
        plan.allow_local_artifact_cache,
        plan.artifact_sources.clone(),
        run_lockfile.clone(),
        plan.lockfile.clone(),
        context.cancellation_token.clone(),
    )?;
    let modules = resolve_forge_userdev_modules(&context, &resolver)?;
    context.bail_if_cancelled()?;
    let launch_classpath = resolve_run_classpath(&context, &resolver, kind)?;
    context.bail_if_cancelled()?;
    let run_cache_artifact_paths = run_lockfile_cache_artifact_paths(&launch_classpath, &modules);
    write_artifact_lockfile_with_extra_cache_paths(plan, &run_cache_artifact_paths)?;
    let minecraft_classpath_file = run_state_dir.join("minecraftClasspath.txt");
    write_classpath_file(&minecraft_classpath_file, &launch_classpath.legacy)?;

    let assets = if run_config_requires_assets(&run_config) {
        Some(prepare_minecraft_assets(&context)?)
    } else {
        None
    };
    context.bail_if_cancelled()?;

    let source_roots = run_source_roots(&context, kind)?;
    let mcp_mappings = run_mcp_mappings(plan);
    let module_path = join_classpath(&modules);
    let minecraft_classpath_file_text = minecraft_classpath_file.display().to_string();
    let assets_root = assets
        .as_ref()
        .map_or_else(String::new, |assets| assets.root.display().to_string());
    let asset_index = assets
        .as_ref()
        .map_or_else(String::new, |assets| assets.index_id.clone());
    let replacements = [
        ("{modules}", module_path.as_str()),
        ("{source_roots}", source_roots.as_str()),
        ("{mcp_mappings}", mcp_mappings.as_str()),
        (
            "{minecraft_classpath_file}",
            minecraft_classpath_file_text.as_str(),
        ),
        ("{asset_index}", asset_index.as_str()),
        ("{assets_root}", assets_root.as_str()),
    ];

    let mut properties = run_config.props.clone();
    properties.insert(
        "forge.logging.markers".to_string(),
        "REGISTRIES".to_string(),
    );
    properties.insert(
        "forge.logging.console.level".to_string(),
        "info".to_string(),
    );
    properties.insert("mixin.env.remapRefMap".to_string(), "true".to_string());
    if plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev {
        let refmap_remapping_file = ensure_run_refmap_remapping_file(&context)?;
        properties.insert(
            "mixin.env.refMapRemappingFile".to_string(),
            refmap_remapping_file.display().to_string(),
        );
        properties.insert(
            "net.minecraftforge.gradle.GradleStart.srg.srg-mcp".to_string(),
            refmap_remapping_file.display().to_string(),
        );
    }
    if kind.enables_game_tests() {
        let game_test_property = game_test_namespace_property(&context)?;
        properties.insert(
            game_test_property,
            required_property(&plan.properties, "mod_id")?.to_string(),
        );
        properties.insert(
            "sfm.gametest.maxProgramRunMillis".to_string(),
            kind.game_test_max_program_run_millis().to_string(),
        );
    }
    if let Some(automation_mode) = kind.automation_mode() {
        properties.insert(
            "sfm.clientRun.mode".to_string(),
            automation_mode.to_string(),
        );
        properties.insert(
            "sfm.clientRun.keepOpenSeconds".to_string(),
            "25".to_string(),
        );
    }

    let mut jvm_args = properties
        .into_iter()
        .map(|(key, value)| format!("-D{key}={}", replace_placeholders(&value, &replacements)))
        .collect::<Vec<_>>();
    jvm_args.extend(
        run_config
            .jvm_args
            .iter()
            .map(|arg| replace_placeholders(arg, &replacements)),
    );
    jvm_args.extend([
        "-XX:+IgnoreUnrecognizedVMOptions".to_string(),
        "-XX:+AllowRedefinitionToAddDeleteMethods".to_string(),
    ]);

    let mut program_args = run_config
        .args
        .iter()
        .map(|arg| replace_placeholders(arg, &replacements))
        .collect::<Vec<_>>();
    program_args.extend(kind_extra_program_args(plan, kind)?);
    if !matches!(kind, RunKind::Data) {
        program_args.extend(["--mixin.config".to_string(), "sfm.mixins.json".to_string()]);
    }

    let mut env = BTreeMap::new();
    for (key, value) in &run_config.env {
        env.insert(key.clone(), replace_placeholders(value, &replacements));
    }
    env.insert("MOD_CLASSES".to_string(), source_roots);
    env.insert("MCP_MAPPINGS".to_string(), mcp_mappings);

    let mut java_classpath_inputs = launch_classpath
        .legacy
        .iter()
        .cloned()
        .chain(launch_classpath.userdev_mods.iter().cloned())
        .chain(modules.iter().cloned())
        .collect::<Vec<_>>();
    if launch_main.starts_with("net.neoforged.fml.startup.") {
        java_classpath_inputs.extend(run_source_root_paths(&context, kind)?);
    }
    let java_classpath = dedup_paths_preserve_order(java_classpath_inputs);
    let mut java_args = Vec::new();
    java_args.extend(jvm_args);
    java_args.extend(["-cp".to_string(), join_classpath(&java_classpath)]);
    java_args.push(launch_main);
    java_args.extend(program_args);

    let argfile = run_state_dir.join("launch.java.args");
    fs::write(
        &argfile,
        java_args
            .into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n"),
    )
    .wrap_err_with(|| format!("Failed to write {}", argfile.display()))?;

    tracing::info!(
        "Launching {} from {}",
        kind.command_name(),
        working_dir.display()
    );
    tracing::info!("Launch args: {}", argfile.display());
    tracing::info!(
        "MOD_CLASSES={}",
        env.get("MOD_CLASSES").map_or("", String::as_str)
    );
    tracing::info!(
        "Userdev mod jars on launch classpath: {}",
        launch_classpath.userdev_mods.len()
    );
    tracing::info!(
        kind = kind.command_name(),
        argfile = %argfile.display(),
        legacy_classpath_entries = launch_classpath.legacy.len(),
        userdev_mods = launch_classpath.userdev_mods.len(),
        "run_setup_complete"
    );

    let launch_log = run_state_dir.join("console.log");
    if dry_run {
        let mut run_outputs = vec![argfile.clone(), minecraft_classpath_file.clone()];
        if let Some(path) = automation_options_path {
            run_outputs.push(path);
        }
        context.write_node_state(
            &format!("run-{}", kind.userdev_name()),
            &["Forge userdev run config", "Rust-owned build outputs"],
            &run_outputs,
            "dry-run",
        )?;
        tracing::info!(
            "Dry run prepared {} launch setup and skipped Minecraft JVM launch.",
            kind.command_name()
        );
        tracing::info!(
            kind = kind.command_name(),
            argfile = %argfile.display(),
            "run_dry_run_skip_launch"
        );
        return Ok(());
    }
    let max_launch_attempts = if matches!(kind, RunKind::GameTestServer) {
        3
    } else {
        1
    };
    let mut launch_output = None;
    for attempt in 1..=max_launch_attempts {
        context.bail_if_cancelled()?;
        if matches!(kind, RunKind::GameTestServer) {
            clean_gametest_server_world(&plan.minecraft_dir, &working_dir)?;
            tracing::info!("Game-test server attempt {attempt}/{max_launch_attempts}");
        }
        if matches!(kind, RunKind::ClientPuppet) {
            clean_client_puppet_world(&plan.minecraft_dir, &working_dir)?;
        }
        let attempt_output = run_launch_command(
            &context.cancellation_token,
            plan,
            &argfile,
            &working_dir,
            &env,
            &launch_log,
            kind.launch_timeout(),
        )
        .wrap_err_with(|| {
            format!(
                "Failed to launch {} using {}",
                kind.command_name(),
                plan.java.executable.display()
            )
        })?;
        if attempt_output.status.success() || attempt == max_launch_attempts {
            launch_output = Some(attempt_output);
            break;
        }
        tracing::info!(
            "{} attempt {attempt}/{max_launch_attempts} exited with {}; retrying. See {}",
            kind.command_name(),
            attempt_output.status,
            launch_log.display()
        );
    }
    let launch_output = launch_output.ok_or_else(|| {
        eyre::eyre!(
            "Failed to launch {} using {}",
            kind.command_name(),
            plan.java.executable.display()
        )
    })?;

    let mut run_outputs = vec![
        argfile.clone(),
        minecraft_classpath_file.clone(),
        launch_log.clone(),
    ];
    if let Some(path) = automation_options_path {
        run_outputs.push(path);
    }
    context.write_node_state(
        &format!("run-{}", kind.userdev_name()),
        &["Forge userdev run config", "Rust-owned build outputs"],
        &run_outputs,
        if launch_output.status.success() {
            "complete"
        } else {
            "failed"
        },
    )?;

    if launch_output.timed_out {
        eyre::bail!(
            "{} timed out after {} seconds. See {}",
            kind.command_name(),
            kind.launch_timeout().map_or(0, |timeout| timeout.as_secs()),
            launch_log.display()
        );
    }
    if launch_output.cancelled {
        eyre::bail!(
            "{} was cancelled by Ctrl+C. See {}",
            kind.command_name(),
            launch_log.display()
        );
    }
    if !launch_output.status.success() {
        eyre::bail!(
            "{} exited with {}. See {}",
            kind.command_name(),
            launch_output.status,
            launch_log.display()
        );
    }
    if matches!(kind, RunKind::GameTestServer) {
        let Some(pass_count) = extract_required_gametest_pass_count(&launch_output.combined) else {
            let running_count = extract_running_gametest_count(&launch_output.combined)
                .map_or_else(|| "unknown".to_string(), |count| count.to_string());
            eyre::bail!(
                "{} exited successfully but did not report a required game-test pass count (running count: {}). See {}",
                kind.command_name(),
                running_count,
                launch_log.display()
            );
        };
        if pass_count == 0 {
            eyre::bail!(
                "{} reported 0 required game tests passed. See {}",
                kind.command_name(),
                launch_log.display()
            );
        }
        tracing::info!("Validated {pass_count} required game tests passed.");
    }
    if matches!(kind, RunKind::ClientSmoke) {
        if !launch_output.combined.contains("SFM_CLIENT_SMOKE_READY") {
            eyre::bail!(
                "{} exited successfully but did not report title-screen readiness. See {}",
                kind.command_name(),
                launch_log.display()
            );
        }
        tracing::info!("Validated client reached the title screen.");
    }
    if matches!(kind, RunKind::ClientPuppet) {
        if let Some(failure) = extract_client_puppet_failure(&launch_output.combined) {
            eyre::bail!(
                "{} reported {} required and {} optional game-test failures (required count: {}, total: {}). See {}",
                kind.command_name(),
                failure.required_failed,
                failure.optional_failed,
                failure.required_count,
                failure.total_count,
                launch_log.display()
            );
        }
        let Some(pass_count) = extract_client_puppet_pass_count(&launch_output.combined) else {
            eyre::bail!(
                "{} exited successfully but did not report a client puppet pass count. See {}",
                kind.command_name(),
                launch_log.display()
            );
        };
        if pass_count == 0 {
            eyre::bail!(
                "{} reported 0 required game tests passed. See {}",
                kind.command_name(),
                launch_log.display()
            );
        }
        tracing::info!("Validated client puppet completed {pass_count} required game tests.");
    }
    Ok(())
}

const JUNIT_EVENT_PREFIX: &str = "SFM_JUNIT\t";
const JUNIT_EVENT_RUNNER_MAIN_CLASS: &str = "dev.teamdman.sfm.toolchain.SfmJUnitRunner";
const JUNIT_EVENT_RUNNER_SOURCE: &str = include_str!("junit_event_runner.java");

#[expect(
    clippy::too_many_lines,
    reason = "JUnit execution mirrors the run setup flow while avoiding Gradle."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %plan.branch_name,
        mc = %plan.minecraft_version,
        dry_run,
        action = ?test_options.action,
        filter = test_options.filter.as_deref().unwrap_or(""),
        no_capture = test_options.no_capture,
    )
)]
fn execute_junit_tests(
    plan: &BuildPlan,
    dry_run: bool,
    test_options: &RunTestOptions,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let context = ExecutionContext::new(plan, cancellation_token.clone())?;
    let project_root = plan.cache_dir.join("project");
    let classes_dir = project_root.join("classes");
    let staged_resources_dir = project_root.join("staged-resources");
    let test_classes_dir = project_root.join("test").join("classes");
    let test_resources_dir = project_root.join("test").join("resources");
    let run_state_dir = plan
        .cache_dir
        .join("run")
        .join(RunKind::Test.command_name());
    fs::create_dir_all(&run_state_dir)?;

    let locked_resolver = Resolver::new(
        plan.maven_cache_dir.clone(),
        plan.repositories.clone(),
        plan.refresh,
        plan.allow_local_artifact_cache,
        plan.artifact_sources.clone(),
        plan.lockfile.clone(),
        plan.lockfile.clone(),
        context.cancellation_token.clone(),
    )?;
    let test_resolver = Resolver::new(
        plan.maven_cache_dir.clone(),
        plan.repositories.clone(),
        plan.refresh,
        plan.allow_local_artifact_cache,
        plan.artifact_sources.clone(),
        None,
        None,
        context.cancellation_token.clone(),
    )?;
    context.bail_if_cancelled()?;

    let antlr_classpath = resolve_antlr_classpath(&context, &locked_resolver)?;
    let base_classpath =
        resolve_project_compile_classpath(&context, &locked_resolver, &antlr_classpath)?;
    let test_compile_dependencies =
        resolve_test_dependency_classpath(&context, &test_resolver, TestClasspathKind::Compile)?;
    context.bail_if_cancelled()?;

    let mut test_compile_classpath = base_classpath.clone();
    test_compile_classpath.extend(test_compile_dependencies.iter().cloned());
    test_compile_classpath = dedup_paths_preserve_order(test_compile_classpath);

    let mut upstream_fingerprint_paths = vec![classes_dir.clone(), staged_resources_dir.clone()];
    upstream_fingerprint_paths.extend(test_compile_classpath.iter().cloned());
    let upstream_fingerprint = input_fingerprint(
        &context,
        "javac-test-upstream",
        &upstream_fingerprint_paths,
        &[
            plan.java.version_output.clone(),
            plan.java_release.to_string(),
            format!("{:?}", plan.loader_toolchain.kind),
        ],
    )?;

    let started = Instant::now();
    tracing::info!("Build node compile-test: start");
    compile_optional_java_source_set(
        &context,
        "test",
        &test_compile_classpath,
        &classes_dir,
        &test_classes_dir,
        &upstream_fingerprint,
    )
    .wrap_err("Failed to compile test source set")?;
    stage_optional_resource_source_set(&context, "test", &test_resources_dir, &[])
        .wrap_err("Failed to stage test resources")?;
    tracing::info!(
        "Build node compile-test: done in {} ms",
        started.elapsed().as_millis()
    );
    context.bail_if_cancelled()?;

    if matches!(test_options.action, RunTestAction::Compile) {
        write_artifact_lockfile_with_extra_cache_paths(plan, &test_compile_dependencies)?;
        context.write_node_state(
            "run-compile",
            &["Rust-owned main/gametest/datagen/test outputs"],
            &[test_classes_dir, test_resources_dir],
            if dry_run { "dry-run" } else { "complete" },
        )?;
        tracing::info!(
            "Compiled all Java source sets (main, gametest, datagen, test) without launch."
        );
        return Ok(());
    }

    let test_runtime_dependencies =
        resolve_test_dependency_classpath(&context, &test_resolver, TestClasspathKind::Runtime)?;
    let console_launcher = resolve_junit_console_standalone(&context, &test_resolver)?.cache_path;
    context.bail_if_cancelled()?;

    let mut test_runtime_classpath = vec![
        test_classes_dir.clone(),
        test_resources_dir.clone(),
        staged_resources_dir.clone(),
        classes_dir.clone(),
    ];
    test_runtime_classpath.extend(base_classpath);
    test_runtime_classpath.extend(test_compile_dependencies.iter().cloned());
    test_runtime_classpath.extend(test_runtime_dependencies.iter().cloned());
    test_runtime_classpath = dedup_paths_preserve_order(test_runtime_classpath);

    let runtime_classpath_file = run_state_dir.join("testRuntimeClasspath.txt");
    write_classpath_file(&runtime_classpath_file, &test_runtime_classpath)?;
    let runner_classes_dir = compile_junit_event_runner(&context, &console_launcher)?;
    let argfile = run_state_dir.join("junit.java.args");
    let test_source_root = plan.minecraft_dir.join("src").join("test").join("java");
    write_junit_runner_argfile(
        &argfile,
        &runner_classes_dir,
        &console_launcher,
        &test_runtime_classpath,
        &test_classes_dir,
        &test_source_root,
        test_options,
    )?;

    let mut extra_cache_paths = Vec::new();
    extra_cache_paths.extend(test_compile_dependencies);
    extra_cache_paths.extend(test_runtime_dependencies);
    extra_cache_paths.push(console_launcher.clone());
    write_artifact_lockfile_with_extra_cache_paths(plan, &extra_cache_paths)?;

    tracing::info!(
        "Launching JUnit tests from {}",
        plan.minecraft_dir.display()
    );
    tracing::info!("JUnit args: {}", argfile.display());
    tracing::info!(
        runtime_classpath_entries = test_runtime_classpath.len(),
        argfile = %argfile.display(),
        "junit_test_setup_complete"
    );

    let console_log = run_state_dir.join("console.log");
    if dry_run {
        context.write_node_state(
            "run-test",
            &["SFM JUnit event runner", "Rust-owned test outputs"],
            &[
                argfile.clone(),
                runtime_classpath_file,
                runner_classes_dir,
                test_classes_dir,
                test_resources_dir,
            ],
            "dry-run",
        )?;
        tracing::info!("Dry run prepared runTest launch setup and skipped JUnit execution.");
        tracing::info!(argfile = %argfile.display(), "run_test_dry_run_skip_launch");
        return Ok(());
    }

    let mut command = Command::new(&plan.java.executable);
    command
        .arg(format!("@{}", argfile.display()))
        .current_dir(&plan.minecraft_dir);
    let started = Instant::now();
    let output =
        run_command_capture_output(&context.cancellation_token, &mut command, "junit-test")
            .wrap_err_with(|| {
                format!(
                    "Failed to launch JUnit tests using {}",
                    plan.java.executable.display()
                )
            })?;
    let report = process_junit_event_output(
        plan,
        test_options,
        &console_log,
        &output,
        started.elapsed().as_millis(),
    )?;

    context.write_node_state(
        "run-test",
        &["SFM JUnit event runner", "Rust-owned test outputs"],
        &[
            argfile,
            runtime_classpath_file,
            console_log.clone(),
            runner_classes_dir,
            test_classes_dir,
            test_resources_dir,
        ],
        if output.status.success() {
            "complete"
        } else {
            "failed"
        },
    )?;

    if output.cancelled {
        eyre::bail!(
            "runTest was cancelled by Ctrl+C. See {}",
            console_log.display()
        );
    }
    if !output.status.success() {
        eyre::bail!(
            "runTest exited with {} ({}). See {}",
            output.status,
            report.summary_message(),
            console_log.display()
        );
    }
    if !report.protocol_errors.is_empty() {
        eyre::bail!(
            "runTest produced {} malformed protocol event(s). See {}",
            report.protocol_errors.len(),
            console_log.display()
        );
    }
    tracing::info!("JUnit tests completed successfully.");
    Ok(())
}

fn compile_junit_event_runner(
    context: &ExecutionContext<'_>,
    console_launcher: &Path,
) -> eyre::Result<PathBuf> {
    context.bail_if_cancelled()?;
    let runner_root = context.plan.cache_dir.join("junit-runner");
    let source_dir = runner_root
        .join("src")
        .join("dev")
        .join("teamdman")
        .join("sfm")
        .join("toolchain");
    let source_file = source_dir.join("SfmJUnitRunner.java");
    let classes_dir = runner_root.join("classes");
    fs::create_dir_all(&source_dir)?;
    write_file_if_changed(&source_file, JUNIT_EVENT_RUNNER_SOURCE.as_bytes())?;

    let argfile = runner_root.join("javac-junit-runner.args");
    write_javac_no_ap_argfile(
        context,
        &argfile,
        &[console_launcher.to_path_buf()],
        std::slice::from_ref(&source_file),
        &classes_dir,
    )?;
    let fingerprint = input_fingerprint(
        context,
        "javac-junit-runner",
        &[
            source_file.clone(),
            argfile.clone(),
            console_launcher.to_path_buf(),
        ],
        &[
            context.plan.java.version_output.clone(),
            context.plan.java_release.to_string(),
        ],
    )?;
    let state_path = runner_root.join("javac-junit-runner.inputs.sha1");
    if cache_state_matches(context, &state_path, &fingerprint, &[&classes_dir])? {
        tracing::info!(
            "javac junit-runner: reused cached outputs at {}",
            classes_dir.display()
        );
        return Ok(classes_dir);
    }

    reset_cache_directory(&context.plan.cache_dir, &classes_dir)?;
    let mut command = Command::new(javac_executable(&context.plan.java));
    command.arg(format!("@{}", argfile.display()));
    let output = run_command_capture_output(
        &context.cancellation_token,
        &mut command,
        "javac-junit-runner",
    )
    .wrap_err("Failed to run javac for SFM JUnit event runner")?;
    trace_subprocess_bytes(
        context.plan,
        "java-tool",
        "javac-junit-runner",
        "stdout",
        &output.stdout,
    );
    trace_subprocess_bytes(
        context.plan,
        "java-tool",
        "javac-junit-runner",
        "stderr",
        &output.stderr,
    );
    let log_path = runner_root.join("javac-junit-runner.log");
    let mut log = Vec::new();
    log.extend_from_slice(b"--- stdout ---\n");
    log.extend_from_slice(&output.stdout);
    log.extend_from_slice(b"\n--- stderr ---\n");
    log.extend_from_slice(&output.stderr);
    fs::write(&log_path, log)
        .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
    if output.cancelled {
        eyre::bail!(
            "javac junit-runner was cancelled by Ctrl+C. See {}",
            log_path.display()
        );
    }
    if !output.status.success() {
        eyre::bail!(
            "javac junit-runner failed with {}. See {}",
            output.status,
            log_path.display()
        );
    }
    write_cache_state(&state_path, &fingerprint)?;
    Ok(classes_dir)
}

fn write_file_if_changed(path: &Path, bytes: &[u8]) -> eyre::Result<()> {
    if path.is_file() && fs::read(path).is_ok_and(|existing| existing == bytes) {
        return Ok(());
    }
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, bytes).wrap_err_with(|| format!("Failed to write {}", path.display()))
}

fn write_junit_runner_argfile(
    argfile: &Path,
    runner_classes_dir: &Path,
    console_launcher: &Path,
    runtime_classpath: &[PathBuf],
    test_classes_dir: &Path,
    test_source_root: &Path,
    test_options: &RunTestOptions,
) -> eyre::Result<()> {
    if let Some(parent) = argfile.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut command_classpath = vec![
        runner_classes_dir.to_path_buf(),
        console_launcher.to_path_buf(),
    ];
    command_classpath.extend(runtime_classpath.iter().cloned());
    let mut args = vec![
        "-cp".to_string(),
        join_classpath(&command_classpath),
        JUNIT_EVENT_RUNNER_MAIN_CLASS.to_string(),
        "--mode".to_string(),
        match test_options.action {
            RunTestAction::Run => "run".to_string(),
            RunTestAction::List => "list".to_string(),
            RunTestAction::Compile => "compile".to_string(),
        },
        "--classpath-root".to_string(),
        test_classes_dir.display().to_string(),
        "--source-root".to_string(),
        test_source_root.display().to_string(),
    ];
    if let Some(filter) = test_options
        .filter
        .as_ref()
        .filter(|filter| !filter.trim().is_empty())
    {
        args.extend(["--filter".to_string(), filter.clone()]);
    }
    fs::write(
        argfile,
        args.into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n"),
    )
    .wrap_err_with(|| format!("Failed to write {}", argfile.display()))
}

#[derive(Clone, Debug, Default)]
struct JunitTestInfo {
    unique_id: String,
    display_name: String,
    legacy_name: String,
    source_path: String,
}

impl JunitTestInfo {
    fn label(&self) -> &str {
        if !self.legacy_name.is_empty() {
            &self.legacy_name
        } else if !self.display_name.is_empty() {
            &self.display_name
        } else {
            "unknown-test"
        }
    }

    fn key(&self) -> String {
        if self.unique_id.is_empty() {
            self.label().to_string()
        } else {
            self.unique_id.clone()
        }
    }

    fn uri(&self) -> String {
        vscode_file_uri_for_path(&self.source_path)
    }
}

#[derive(Clone, Debug)]
struct JunitCapturedOutput {
    stream: String,
    line: String,
}

#[derive(Clone, Debug)]
struct JunitFinishedTest {
    info: JunitTestInfo,
    status: String,
    throwable_type: String,
    throwable_message: String,
    stack_trace: String,
}

#[derive(Clone, Debug, Default)]
struct JunitSummary {
    tests_found: u64,
    tests_started: u64,
    tests_succeeded: u64,
    tests_failed: u64,
    tests_skipped: u64,
    tests_aborted: u64,
    containers_found: u64,
    containers_failed: u64,
}

#[derive(Clone, Debug)]
enum JunitProtocolEvent {
    Test(JunitTestInfo),
    Started(JunitTestInfo),
    Skipped {
        info: JunitTestInfo,
        reason: String,
    },
    Output {
        stream: String,
        info: JunitTestInfo,
        line: String,
    },
    Finished(JunitFinishedTest),
    Summary(JunitSummary),
    ListSummary {
        count: u64,
    },
    RunnerError {
        message: String,
        stack_trace: String,
    },
}

#[derive(Debug, Default)]
struct JunitExecutionReport {
    summary: Option<JunitSummary>,
    list_count: Option<u64>,
    listed_tests: Vec<JunitTestInfo>,
    failures: Vec<JunitFinishedTest>,
    runner_errors: Vec<String>,
    protocol_errors: Vec<String>,
    non_protocol_stdout: Vec<String>,
    child_stderr: Vec<String>,
}

impl JunitExecutionReport {
    fn summary_message(&self) -> String {
        if let Some(summary) = self.summary.as_ref() {
            return format!(
                "{} found, {} started, {} passed, {} failed, {} skipped, {} aborted",
                summary.tests_found,
                summary.tests_started,
                summary.tests_succeeded,
                summary.tests_failed,
                summary.tests_skipped,
                summary.tests_aborted
            );
        }
        if let Some(count) = self.list_count {
            return format!("{count} discovered");
        }
        "no JUnit summary received".to_string()
    }
}

fn process_junit_event_output(
    plan: &BuildPlan,
    test_options: &RunTestOptions,
    console_log: &Path,
    output: &CancellableOutput,
    duration_ms: u128,
) -> eyre::Result<JunitExecutionReport> {
    let mut report = JunitExecutionReport::default();
    let mut events = Vec::new();
    let mut captured_outputs: BTreeMap<String, Vec<JunitCapturedOutput>> = BTreeMap::new();
    let stdout = String::from_utf8_lossy(&output.stdout);
    for line in stdout.lines() {
        match parse_junit_protocol_line(line) {
            Some(Ok(event)) => {
                process_junit_event(
                    plan,
                    test_options,
                    &mut report,
                    &mut captured_outputs,
                    &event,
                );
                events.push(event);
            }
            Some(Err(error)) => report.protocol_errors.push(error.to_string()),
            None => report.non_protocol_stdout.push(line.to_string()),
        }
    }

    let stderr = String::from_utf8_lossy(&output.stderr);
    report
        .child_stderr
        .extend(stderr.lines().map(std::string::ToString::to_string));

    if test_options.no_capture || !output.status.success() {
        for line in &report.non_protocol_stdout {
            trace_subprocess_line("java-tool", "junit-test", "stdout", line);
        }
        for line in &report.child_stderr {
            trace_subprocess_line("java-tool", "junit-test", "stderr", line);
        }
    }

    emit_junit_terminal_summary(plan, test_options, &report);
    write_junit_event_console_log(console_log, output, duration_ms, &events, &report)?;
    Ok(report)
}

fn process_junit_event(
    plan: &BuildPlan,
    test_options: &RunTestOptions,
    report: &mut JunitExecutionReport,
    captured_outputs: &mut BTreeMap<String, Vec<JunitCapturedOutput>>,
    event: &JunitProtocolEvent,
) {
    match event {
        JunitProtocolEvent::Test(info) => {
            report.listed_tests.push(info.clone());
            trace_junit_test_list_entry(plan, info);
        }
        JunitProtocolEvent::Started(_) => {}
        JunitProtocolEvent::Skipped { info, reason } => {
            if test_options.no_capture {
                trace_junit_test_line(plan, info, "stdout", &format!("skipped: {reason}"));
            }
        }
        JunitProtocolEvent::Output { stream, info, line } => {
            captured_outputs
                .entry(info.key())
                .or_default()
                .push(JunitCapturedOutput {
                    stream: stream.clone(),
                    line: line.clone(),
                });
            if test_options.no_capture {
                trace_junit_test_line(plan, info, stream, line);
            }
        }
        JunitProtocolEvent::Finished(finished) => {
            if finished.status != "SUCCESSFUL" {
                if !test_options.no_capture {
                    for captured in captured_outputs
                        .get(&finished.info.key())
                        .into_iter()
                        .flat_map(|outputs| outputs.iter())
                    {
                        trace_junit_test_line(
                            plan,
                            &finished.info,
                            &captured.stream,
                            &captured.line,
                        );
                    }
                }
                trace_junit_test_failure(plan, finished);
                report.failures.push(finished.clone());
            }
        }
        JunitProtocolEvent::Summary(summary) => {
            report.summary = Some(summary.clone());
        }
        JunitProtocolEvent::ListSummary { count } => {
            report.list_count = Some(*count);
        }
        JunitProtocolEvent::RunnerError {
            message,
            stack_trace,
        } => {
            report.runner_errors.push(message.clone());
            let info = JunitTestInfo::default();
            trace_junit_test_line(plan, &info, "stderr", message);
            for line in stack_trace.lines() {
                trace_junit_test_line(plan, &info, "stderr", line);
            }
        }
    }
}

fn emit_junit_terminal_summary(
    plan: &BuildPlan,
    test_options: &RunTestOptions,
    report: &JunitExecutionReport,
) {
    let _span = tracing::info_span!("junit_terminal_summary", branch = %plan.branch_name).entered();
    let source = "java-tool";
    let process = "junit-test";
    match test_options.action {
        RunTestAction::Compile => {
            tracing::info!(
                source,
                process,
                "Compiled test source set; no JUnit launch requested."
            );
        }
        RunTestAction::List => {
            let count = report
                .list_count
                .unwrap_or(report.listed_tests.len() as u64);
            tracing::info!(source, process, "Discovered {count} JUnit tests.");
        }
        RunTestAction::Run => {
            if let Some(summary) = report.summary.as_ref() {
                tracing::info!(
                    source,
                    process,
                    "JUnit tests: {} passed, {} failed, {} skipped, {} aborted ({} found).",
                    summary.tests_succeeded,
                    summary.tests_failed,
                    summary.tests_skipped,
                    summary.tests_aborted,
                    summary.tests_found
                );
            }
        }
    }
}

fn trace_junit_test_list_entry(plan: &BuildPlan, info: &JunitTestInfo) {
    let _span = tracing::info_span!("junit_test_list_entry", branch = %plan.branch_name).entered();
    let source = "java-tool";
    let process = "junit-test";
    let test = info.label();
    let test_uri = info.uri();
    tracing::info!(
        source,
        process,
        test = %test,
        test_uri = %test_uri,
        "{test}"
    );
}

fn trace_junit_test_line(plan: &BuildPlan, info: &JunitTestInfo, stream: &str, line: &str) {
    let _span = tracing::info_span!("junit_test_output", branch = %plan.branch_name).entered();
    let source = "java-tool";
    let process = "junit-test";
    let test = info.label();
    let test_uri = info.uri();
    tracing::info!(
        source,
        process,
        stream = %stream,
        test = %test,
        test_uri = %test_uri,
        "{line}"
    );
}

fn trace_junit_test_failure(plan: &BuildPlan, failure: &JunitFinishedTest) {
    let _span = tracing::info_span!("junit_test_failure", branch = %plan.branch_name).entered();
    let source = "java-tool";
    let process = "junit-test";
    let stream = "stderr";
    let test = failure.info.label();
    let test_uri = failure.info.uri();
    let message = if failure.throwable_message.is_empty() {
        failure.throwable_type.as_str()
    } else {
        failure.throwable_message.as_str()
    };
    tracing::error!(
        source,
        process,
        stream,
        test = %test,
        test_uri = %test_uri,
        "JUnit test failed ({}) {message}",
        failure.status
    );
    for line in failure.stack_trace.lines() {
        tracing::error!(
            source,
            process,
            stream,
            test = %test,
            test_uri = %test_uri,
            "{line}"
        );
    }
}

fn parse_junit_protocol_line(line: &str) -> Option<eyre::Result<JunitProtocolEvent>> {
    let rest = line.strip_prefix(JUNIT_EVENT_PREFIX)?;
    Some(parse_junit_protocol_event(rest))
}

fn parse_junit_protocol_event(rest: &str) -> eyre::Result<JunitProtocolEvent> {
    let mut parts = rest.split('\t');
    let event = parts
        .next()
        .ok_or_else(|| eyre::eyre!("JUnit protocol line did not include an event name"))?;
    let fields = parts
        .map(decode_junit_protocol_field)
        .collect::<eyre::Result<Vec<_>>>()?;
    match event {
        "test" => {
            expect_junit_field_count(event, &fields, 4)?;
            Ok(JunitProtocolEvent::Test(junit_info_from_fields(&fields, 0)))
        }
        "started" => {
            expect_junit_field_count(event, &fields, 4)?;
            Ok(JunitProtocolEvent::Started(junit_info_from_fields(
                &fields, 0,
            )))
        }
        "skipped" => {
            expect_junit_field_count(event, &fields, 5)?;
            Ok(JunitProtocolEvent::Skipped {
                info: junit_info_from_fields(&fields, 0),
                reason: fields[4].clone(),
            })
        }
        "output" => {
            expect_junit_field_count(event, &fields, 6)?;
            Ok(JunitProtocolEvent::Output {
                stream: fields[0].clone(),
                info: junit_info_from_fields(&fields, 1),
                line: fields[5].clone(),
            })
        }
        "finished" => {
            expect_junit_field_count(event, &fields, 8)?;
            Ok(JunitProtocolEvent::Finished(JunitFinishedTest {
                info: junit_info_from_fields(&fields, 0),
                status: fields[4].clone(),
                throwable_type: fields[5].clone(),
                throwable_message: fields[6].clone(),
                stack_trace: fields[7].clone(),
            }))
        }
        "summary" => {
            expect_junit_field_count(event, &fields, 8)?;
            Ok(JunitProtocolEvent::Summary(JunitSummary {
                tests_found: parse_junit_u64(event, "tests_found", &fields[0])?,
                tests_started: parse_junit_u64(event, "tests_started", &fields[1])?,
                tests_succeeded: parse_junit_u64(event, "tests_succeeded", &fields[2])?,
                tests_failed: parse_junit_u64(event, "tests_failed", &fields[3])?,
                tests_skipped: parse_junit_u64(event, "tests_skipped", &fields[4])?,
                tests_aborted: parse_junit_u64(event, "tests_aborted", &fields[5])?,
                containers_found: parse_junit_u64(event, "containers_found", &fields[6])?,
                containers_failed: parse_junit_u64(event, "containers_failed", &fields[7])?,
            }))
        }
        "list_summary" => {
            expect_junit_field_count(event, &fields, 1)?;
            Ok(JunitProtocolEvent::ListSummary {
                count: parse_junit_u64(event, "count", &fields[0])?,
            })
        }
        "runner_error" => {
            expect_junit_field_count(event, &fields, 2)?;
            Ok(JunitProtocolEvent::RunnerError {
                message: fields[0].clone(),
                stack_trace: fields[1].clone(),
            })
        }
        _ => eyre::bail!("Unknown JUnit protocol event: {event}"),
    }
}

fn decode_junit_protocol_field(field: &str) -> eyre::Result<String> {
    let bytes = BASE64_STANDARD
        .decode(field)
        .wrap_err("Failed to decode JUnit protocol field as base64")?;
    String::from_utf8(bytes).wrap_err("JUnit protocol field was not UTF-8")
}

fn expect_junit_field_count(event: &str, fields: &[String], expected: usize) -> eyre::Result<()> {
    if fields.len() != expected {
        eyre::bail!(
            "JUnit protocol event {event} expected {expected} field(s), got {}",
            fields.len()
        );
    }
    Ok(())
}

fn junit_info_from_fields(fields: &[String], offset: usize) -> JunitTestInfo {
    JunitTestInfo {
        unique_id: fields[offset].clone(),
        display_name: fields[offset + 1].clone(),
        legacy_name: fields[offset + 2].clone(),
        source_path: fields[offset + 3].clone(),
    }
}

fn parse_junit_u64(event: &str, field: &str, value: &str) -> eyre::Result<u64> {
    value
        .parse::<u64>()
        .wrap_err_with(|| format!("JUnit protocol event {event} field {field} was not a number"))
}

fn write_junit_event_console_log(
    log_path: &Path,
    output: &CancellableOutput,
    duration_ms: u128,
    events: &[JunitProtocolEvent],
    report: &JunitExecutionReport,
) -> eyre::Result<()> {
    let mut log = String::new();
    writeln!(log, "tool=junit-test")?;
    writeln!(log, "main={JUNIT_EVENT_RUNNER_MAIN_CLASS}")?;
    writeln!(log, "status={}", output.status)?;
    writeln!(log, "cancelled={}", output.cancelled)?;
    writeln!(log, "duration_ms={duration_ms}")?;
    writeln!(log, "summary={}", report.summary_message())?;
    writeln!(log)?;
    writeln!(log, "--- events ---")?;
    for event in events {
        write_junit_event_log_line(&mut log, event)?;
    }
    if !report.non_protocol_stdout.is_empty() {
        writeln!(log)?;
        writeln!(log, "--- non-protocol stdout ---")?;
        for line in &report.non_protocol_stdout {
            writeln!(log, "{line}")?;
        }
    }
    if !report.child_stderr.is_empty() {
        writeln!(log)?;
        writeln!(log, "--- child stderr ---")?;
        for line in &report.child_stderr {
            writeln!(log, "{line}")?;
        }
    }
    fs::write(log_path, log).wrap_err_with(|| format!("Failed to write {}", log_path.display()))
}

fn write_junit_event_log_line(log: &mut String, event: &JunitProtocolEvent) -> eyre::Result<()> {
    match event {
        JunitProtocolEvent::Test(info) => {
            writeln!(log, "test {}", info.label())?;
        }
        JunitProtocolEvent::Started(info) => {
            writeln!(log, "started {}", info.label())?;
        }
        JunitProtocolEvent::Skipped { info, reason } => {
            writeln!(log, "skipped {}: {reason}", info.label())?;
        }
        JunitProtocolEvent::Output { stream, info, line } => {
            writeln!(log, "{stream} {}: {line}", info.label())?;
        }
        JunitProtocolEvent::Finished(finished) => {
            writeln!(
                log,
                "finished {}: {}",
                finished.info.label(),
                finished.status
            )?;
            if !finished.throwable_type.is_empty() {
                writeln!(
                    log,
                    "  failure_type={}\n  failure_message={}",
                    finished.throwable_type, finished.throwable_message
                )?;
                writeln!(log, "{}", finished.stack_trace)?;
            }
        }
        JunitProtocolEvent::Summary(summary) => {
            writeln!(
                log,
                "summary: {} found, {} passed, {} failed, {} skipped, {} aborted",
                summary.tests_found,
                summary.tests_succeeded,
                summary.tests_failed,
                summary.tests_skipped,
                summary.tests_aborted
            )?;
            writeln!(
                log,
                "containers: {} found, {} failed",
                summary.containers_found, summary.containers_failed
            )?;
        }
        JunitProtocolEvent::ListSummary { count } => {
            writeln!(log, "list_summary: {count} discovered")?;
        }
        JunitProtocolEvent::RunnerError {
            message,
            stack_trace,
        } => {
            writeln!(log, "runner_error: {message}")?;
            writeln!(log, "{stack_trace}")?;
        }
    }
    Ok(())
}

fn vscode_file_uri_for_path(path: &str) -> String {
    if path.is_empty() {
        return String::new();
    }
    let path = percent_encode_uri_path_for_junit(&path.replace('\\', "/"));
    format!("vscode://file/{path}:1:1")
}

fn percent_encode_uri_path_for_junit(path: &str) -> String {
    let mut output = String::with_capacity(path.len());
    for byte in path.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'/' | b':' | b'-' | b'_' | b'.' | b'~' => {
                output.push(char::from(byte));
            }
            byte => {
                const HEX: &[u8; 16] = b"0123456789ABCDEF";
                output.push('%');
                output.push(char::from(HEX[usize::from(byte >> 4)]));
                output.push(char::from(HEX[usize::from(byte & 0x0F)]));
            }
        }
    }
    output
}

#[derive(Clone, Copy, Debug)]
enum TestClasspathKind {
    Compile,
    Runtime,
}

fn resolve_test_dependency_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: TestClasspathKind,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let dependency_script = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("dependencies")
        .join(context.plan.minecraft_version.as_str())
        .join("dependencies.gradle");
    let dependencies = parse_dependency_script(&dependency_script, &context.plan.properties)?;
    let configurations: &[&str] = match kind {
        TestClasspathKind::Compile => &["testImplementation", "testCompileOnly"],
        TestClasspathKind::Runtime => &["testImplementation", "testRuntimeOnly"],
    };
    let roots = dependencies
        .iter()
        .filter(|dependency| {
            !dependency.fg_deobf && configurations.contains(&dependency.configuration.as_str())
        })
        .map(|dependency| {
            (
                dependency.configuration.as_str(),
                dependency.coordinate.clone(),
            )
        })
        .collect::<Vec<_>>();
    resolve_dependency_artifact_closure(context, resolver, "test-dependency", roots)
}

fn resolve_dependency_artifact_closure(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    artifact_prefix: &str,
    roots: Vec<(&str, MavenCoordinate)>,
) -> eyre::Result<Vec<PathBuf>> {
    let mut seen = BTreeSet::new();
    let mut queue = VecDeque::new();
    let mut paths = Vec::new();

    for (configuration, coordinate) in roots {
        context.bail_if_cancelled()?;
        let dependency = resolver.resolve_dependency(configuration, &coordinate)?;
        let resolved_coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        if seen.insert(resolved_coordinate.to_string()) {
            paths.push(dependency.cache_path);
            queue.push_back(resolved_coordinate);
        }
    }

    while let Some(parent) = queue.pop_front() {
        context.bail_if_cancelled()?;
        for coordinate in resolver.resolve_pom_runtime_dependencies(&parent)? {
            context.bail_if_cancelled()?;
            if !seen.insert(coordinate.to_string()) {
                continue;
            }
            let artifact = resolver.resolve_artifact(
                ArtifactId::from(format!("{artifact_prefix}-{}", paths.len())),
                &coordinate,
                ArtifactPurpose::from("JUnit test classpath"),
            )?;
            paths.push(artifact.cache_path);
            queue.push_back(coordinate);
        }
    }

    Ok(paths)
}

fn resolve_junit_console_standalone(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<ArtifactPlan> {
    let dependency_script = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("dependencies")
        .join(context.plan.minecraft_version.as_str())
        .join("dependencies.gradle");
    let dependencies = parse_dependency_script(&dependency_script, &context.plan.properties)?;
    let platform_version = junit_platform_version(&dependencies)?;
    let coordinate = MavenCoordinate::parse(&format!(
        "org.junit.platform:junit-platform-console-standalone:{platform_version}"
    ))?;
    resolver.resolve_artifact(
        ArtifactId::from("junit-platform-console-standalone"),
        &coordinate,
        ArtifactPurpose::from("JUnit Platform ConsoleLauncher"),
    )
}

fn junit_platform_version(dependencies: &[ParsedDependency]) -> eyre::Result<String> {
    for artifact in [
        "junit-platform-console-standalone",
        "junit-platform-launcher",
        "junit-platform-engine",
        "junit-platform-commons",
    ] {
        if let Some(dependency) = dependencies.iter().find(|dependency| {
            dependency.coordinate.group == "org.junit.platform"
                && dependency.coordinate.artifact == artifact
        }) {
            return Ok(dependency.coordinate.version.clone());
        }
    }
    if let Some(dependency) = dependencies.iter().find(|dependency| {
        dependency.coordinate.group == "org.junit.jupiter"
            && dependency.coordinate.artifact.starts_with("junit-jupiter")
    }) {
        return platform_version_from_jupiter_version(&dependency.coordinate.version);
    }
    eyre::bail!("Could not infer a JUnit Platform version from test dependencies")
}

fn platform_version_from_jupiter_version(version: &str) -> eyre::Result<String> {
    let Some(rest) = version.strip_prefix("5.") else {
        eyre::bail!("Unsupported JUnit Jupiter version for platform inference: {version}");
    };
    Ok(format!("1.{rest}"))
}

fn run_mcp_mappings(plan: &BuildPlan) -> String {
    if let (Some(channel), Some(version)) = (
        plan.properties.get("mapping_channel"),
        plan.properties.get("mapping_version"),
    ) {
        return format!("{channel}_{version}");
    }
    format!("official_{}", plan.minecraft_version)
}

fn run_lockfile_cache_artifact_paths(
    launch_classpath: &RunClasspath,
    modules: &[PathBuf],
) -> Vec<PathBuf> {
    dedup_paths_preserve_order(
        launch_classpath
            .legacy
            .iter()
            .cloned()
            .chain(launch_classpath.userdev_mods.iter().cloned())
            .chain(modules.iter().cloned())
            .collect(),
    )
}

fn game_test_namespace_property(context: &ExecutionContext<'_>) -> eyre::Result<String> {
    let run_config = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("run-configurations")
        .join(context.plan.minecraft_version.as_str())
        .join("run-configurations.gradle");
    if run_config.is_file() {
        let text = fs::read_to_string(&run_config)
            .wrap_err_with(|| format!("Failed to read {}", run_config.display()))?;
        if text.contains("neoforge.enabledGameTestNamespaces") {
            return Ok("neoforge.enabledGameTestNamespaces".to_string());
        }
    }
    Ok("forge.enabledGameTestNamespaces".to_string())
}

fn clean_gametest_server_world(minecraft_dir: &Path, working_dir: &Path) -> eyre::Result<()> {
    if working_dir.file_name().and_then(|name| name.to_str()) != Some("runGameTest")
        || !working_dir.starts_with(minecraft_dir)
    {
        eyre::bail!(
            "Refusing to clean unexpected game-test working directory: {}",
            working_dir.display()
        );
    }

    let world_dir = working_dir.join("gametestserver");
    if world_dir.exists() {
        fs::remove_dir_all(&world_dir)
            .wrap_err_with(|| format!("Failed to remove {}", world_dir.display()))?;
    }
    Ok(())
}

fn clean_client_puppet_world(minecraft_dir: &Path, working_dir: &Path) -> eyre::Result<()> {
    if working_dir.file_name().and_then(|name| name.to_str()) != Some("runClientPuppet")
        || !working_dir.starts_with(minecraft_dir)
    {
        eyre::bail!(
            "Refusing to clean unexpected client puppet working directory: {}",
            working_dir.display()
        );
    }

    let world_dir = working_dir.join("saves").join("sfm_client_puppet");
    if world_dir.exists() {
        fs::remove_dir_all(&world_dir)
            .wrap_err_with(|| format!("Failed to remove {}", world_dir.display()))?;
    }
    Ok(())
}

fn prepare_client_automation_options(
    minecraft_dir: &Path,
    working_dir: &Path,
    kind: RunKind,
) -> eyre::Result<Option<PathBuf>> {
    if !matches!(kind, RunKind::ClientSmoke | RunKind::ClientPuppet) {
        return Ok(None);
    }
    if !working_dir.starts_with(minecraft_dir) {
        eyre::bail!(
            "Refusing to prepare client automation options outside minecraft dir: {}",
            working_dir.display()
        );
    }

    let options_path = working_dir.join("options.txt");
    let existing = match fs::read_to_string(&options_path) {
        Ok(content) => content,
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => String::new(),
        Err(err) => {
            return Err(err).wrap_err_with(|| format!("Failed to read {}", options_path.display()));
        }
    };
    let mut updated = set_minecraft_option(&existing, "onboardAccessibility", "false");
    updated = set_minecraft_option(&updated, "narrator", "0");
    updated = set_minecraft_option(&updated, "pauseOnLostFocus", "false");
    updated = set_minecraft_option(&updated, "tutorialStep", "none");
    fs::write(&options_path, updated)
        .wrap_err_with(|| format!("Failed to write {}", options_path.display()))?;
    Ok(Some(options_path))
}

fn set_minecraft_option(content: &str, key: &str, value: &str) -> String {
    let prefix = format!("{key}:");
    let mut found = false;
    let mut lines = content
        .lines()
        .map(|line| {
            if line.starts_with(&prefix) {
                found = true;
                format!("{prefix}{value}")
            } else {
                line.to_string()
            }
        })
        .collect::<Vec<_>>();
    if !found {
        lines.push(format!("{prefix}{value}"));
    }
    let mut output = lines.join("\n");
    output.push('\n');
    output
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %plan.branch_name,
        mc = %plan.minecraft_version,
        java = %plan.java.executable.display(),
        argfile = %argfile.display(),
        working_dir = %working_dir.display(),
        env_vars = env.len(),
        timeout_seconds = timeout.map_or(0, |timeout| timeout.as_secs()),
    )
)]
fn run_launch_command(
    cancellation_token: &CancellationToken,
    plan: &BuildPlan,
    argfile: &Path,
    working_dir: &Path,
    env: &BTreeMap<String, String>,
    log_path: &Path,
    timeout: Option<Duration>,
) -> eyre::Result<LaunchOutput> {
    cancellation_token.bail_if_cancelled()?;
    if let Some(parent) = log_path.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut child = Command::new(&plan.java.executable)
        .arg(format!("@{}", argfile.display()))
        .current_dir(working_dir)
        .envs(env)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .wrap_err_with(|| format!("Failed to spawn {}", plan.java.executable.display()))?;
    tracing::info!("minecraft_jvm_spawned");

    let stdout = child
        .stdout
        .take()
        .ok_or_else(|| eyre::eyre!("Failed to capture launch stdout"))?;
    let stderr = child
        .stderr
        .take()
        .ok_or_else(|| eyre::eyre!("Failed to capture launch stderr"))?;
    let stdout_branch = plan.branch_name.clone();
    let stderr_branch = plan.branch_name.clone();
    let stdout_thread = thread::spawn(move || {
        read_launch_stream(stdout, &stdout_branch, "minecraft", "minecraft", "stdout")
    });
    let stderr_thread = thread::spawn(move || {
        read_launch_stream(stderr, &stderr_branch, "minecraft", "minecraft", "stderr")
    });
    let started = Instant::now();
    let mut timed_out = false;
    let mut cancelled = false;
    let status = loop {
        if let Some(status) = child.try_wait().wrap_err("Failed to poll launched JVM")? {
            break status;
        }
        if cancellation_token.is_cancelled() {
            cancelled = true;
            tracing::warn!("Cancellation requested; killing launched Minecraft JVM");
            kill_child_for_cancellation(&mut child, "launched Minecraft JVM")?;
            break child
                .wait()
                .wrap_err("Failed to wait for cancelled launched JVM")?;
        }
        if let Some(timeout) = timeout
            && started.elapsed() >= timeout
        {
            timed_out = true;
            child
                .kill()
                .wrap_err("Failed to kill timed-out launched JVM")?;
            break child
                .wait()
                .wrap_err("Failed to wait for timed-out launched JVM")?;
        }
        thread::sleep(Duration::from_millis(250));
    };
    let stdout_text = join_launch_stream(stdout_thread, "stdout")?;
    let stderr_text = join_launch_stream(stderr_thread, "stderr")?;
    let combined = format!("{stdout_text}{stderr_text}");
    let mut log = String::new();
    writeln!(log, "status={status}")?;
    writeln!(log, "timed_out={timed_out}")?;
    writeln!(log, "cancelled={cancelled}")?;
    writeln!(log, "argfile={}", argfile.display())?;
    writeln!(log, "working_dir={}", working_dir.display())?;
    writeln!(log)?;
    log.push_str(&combined);
    fs::write(log_path, log).wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
    Ok(LaunchOutput {
        status,
        combined,
        timed_out,
        cancelled,
    })
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(branch = %branch)
)]
fn read_launch_stream<R>(
    stream: R,
    branch: &str,
    source: &'static str,
    process: &str,
    stream_name: &'static str,
) -> std::io::Result<String>
where
    R: Read,
{
    let mut captured = String::new();
    for line in BufReader::new(stream).lines() {
        let line = line?;
        trace_subprocess_line(source, process, stream_name, &line);
        captured.push_str(&line);
        captured.push('\n');
    }
    Ok(captured)
}

fn join_launch_stream(
    handle: thread::JoinHandle<std::io::Result<String>>,
    name: &str,
) -> eyre::Result<String> {
    handle
        .join()
        .map_err(|_panic| eyre::eyre!("Launch {name} reader thread panicked"))?
        .wrap_err_with(|| format!("Failed to read launch {name}"))
}

fn run_command_capture_output(
    cancellation_token: &CancellationToken,
    command: &mut Command,
    process_name: &str,
) -> eyre::Result<CancellableOutput> {
    cancellation_token.bail_if_cancelled()?;
    command.stdout(Stdio::piped()).stderr(Stdio::piped());
    let mut child = command
        .spawn()
        .wrap_err_with(|| format!("Failed to spawn {process_name}"))?;
    let stdout = child
        .stdout
        .take()
        .ok_or_else(|| eyre::eyre!("Failed to capture {process_name} stdout"))?;
    let stderr = child
        .stderr
        .take()
        .ok_or_else(|| eyre::eyre!("Failed to capture {process_name} stderr"))?;
    let stdout_thread = thread::spawn(move || read_stream_bytes(stdout));
    let stderr_thread = thread::spawn(move || read_stream_bytes(stderr));
    let mut cancelled = false;
    let status = loop {
        if let Some(status) = child
            .try_wait()
            .wrap_err_with(|| format!("Failed to poll {process_name}"))?
        {
            break status;
        }
        if cancellation_token.is_cancelled() {
            cancelled = true;
            tracing::warn!(
                process = process_name,
                "Cancellation requested; killing JVM child"
            );
            kill_child_for_cancellation(&mut child, process_name)?;
            break child
                .wait()
                .wrap_err_with(|| format!("Failed to wait for cancelled {process_name}"))?;
        }
        thread::sleep(Duration::from_millis(250));
    };
    Ok(CancellableOutput {
        status,
        stdout: join_output_stream(stdout_thread, process_name, "stdout")?,
        stderr: join_output_stream(stderr_thread, process_name, "stderr")?,
        cancelled,
    })
}

fn kill_child_for_cancellation(
    child: &mut std::process::Child,
    process_name: &str,
) -> eyre::Result<()> {
    match child.kill() {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::InvalidInput => Ok(()),
        Err(error) => {
            Err(error).wrap_err_with(|| format!("Failed to kill cancelled {process_name}"))
        }
    }
}

fn read_stream_bytes<R>(mut stream: R) -> std::io::Result<Vec<u8>>
where
    R: Read,
{
    let mut bytes = Vec::new();
    stream.read_to_end(&mut bytes)?;
    Ok(bytes)
}

fn join_output_stream(
    handle: thread::JoinHandle<std::io::Result<Vec<u8>>>,
    process_name: &str,
    stream_name: &str,
) -> eyre::Result<Vec<u8>> {
    handle
        .join()
        .map_err(|_panic| eyre::eyre!("{process_name} {stream_name} reader thread panicked"))?
        .wrap_err_with(|| format!("Failed to read {process_name} {stream_name}"))
}

fn trace_subprocess_bytes(
    plan: &BuildPlan,
    source: &'static str,
    process: &str,
    stream: &'static str,
    bytes: &[u8],
) {
    let _span =
        tracing::info_span!("forward_subprocess_bytes", branch = %plan.branch_name).entered();
    let content = String::from_utf8_lossy(bytes);
    for line in content.lines() {
        trace_subprocess_line(source, process, stream, line);
    }
}

fn write_java_tool_console_log(
    log_path: &Path,
    tool_id: &str,
    main_class: &str,
    duration_ms: u128,
    classpath_arg: &str,
    args: &[String],
    output: &CancellableOutput,
) -> eyre::Result<()> {
    let mut log = Vec::new();
    writeln!(
        log,
        "tool={tool_id}\nmain={main_class}\nstatus={}\ncancelled={}\nduration_ms={}\nclasspath={}\nargs={:?}\n",
        output.status, output.cancelled, duration_ms, classpath_arg, args
    )?;
    log.extend_from_slice(b"\n--- stdout ---\n");
    log.extend_from_slice(&output.stdout);
    log.extend_from_slice(b"\n--- stderr ---\n");
    log.extend_from_slice(&output.stderr);
    fs::write(log_path, log).wrap_err_with(|| format!("Failed to write {}", log_path.display()))
}

fn trace_subprocess_line(source: &'static str, process: &str, stream: &'static str, line: &str) {
    tracing::info!(
        source,
        process = %process,
        stream,
        "{line}"
    );
}

fn extract_required_gametest_pass_count(output: &str) -> Option<usize> {
    extract_number_between(output, "All ", " required tests passed :)")
}

fn extract_running_gametest_count(output: &str) -> Option<usize> {
    extract_number_between(output, "Running all ", " tests")
}

fn extract_client_puppet_pass_count(output: &str) -> Option<usize> {
    extract_number_between(
        output,
        "SFM_CLIENT_PUPPET_TESTS_PASSED required=",
        " total=",
    )
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct ClientPuppetFailure {
    required_failed: usize,
    optional_failed: usize,
    required_count: usize,
    total_count: usize,
}

fn extract_client_puppet_failure(output: &str) -> Option<ClientPuppetFailure> {
    let prefix = "SFM_CLIENT_PUPPET_TESTS_FAILED required_failed=";
    for (start, _) in output.match_indices(prefix) {
        let after_prefix = &output[start + prefix.len()..];
        let Some((required_failed, after_required_failed)) =
            take_usize_before(after_prefix, " optional_failed=")
        else {
            continue;
        };
        let Some((optional_failed, after_optional_failed)) =
            take_usize_before(after_required_failed, " required=")
        else {
            continue;
        };
        let Some((required_count, after_required_count)) =
            take_usize_before(after_optional_failed, " total=")
        else {
            continue;
        };
        let total_count = take_leading_usize(after_required_count)?;
        return Some(ClientPuppetFailure {
            required_failed,
            optional_failed,
            required_count,
            total_count,
        });
    }
    None
}

fn extract_number_between(output: &str, prefix: &str, suffix: &str) -> Option<usize> {
    for (start, _) in output.match_indices(prefix) {
        let after_prefix = &output[start + prefix.len()..];
        let Some(end) = after_prefix.find(suffix) else {
            continue;
        };
        let number = after_prefix[..end].trim();
        if !number.is_empty() && number.chars().all(|character| character.is_ascii_digit()) {
            return number.parse().ok();
        }
    }
    None
}

fn take_usize_before<'a>(text: &'a str, delimiter: &str) -> Option<(usize, &'a str)> {
    let end = text.find(delimiter)?;
    let number = text[..end].trim();
    if number.is_empty() || !number.chars().all(|character| character.is_ascii_digit()) {
        return None;
    }
    Some((number.parse().ok()?, &text[end + delimiter.len()..]))
}

fn take_leading_usize(text: &str) -> Option<usize> {
    let end = text
        .char_indices()
        .find_map(|(index, character)| (!character.is_ascii_digit()).then_some(index))
        .unwrap_or(text.len());
    if end == 0 {
        return None;
    }
    text[..end].parse().ok()
}

fn read_forge_run_config(
    context: &ExecutionContext<'_>,
    kind: RunKind,
) -> eyre::Result<ForgeRunConfig> {
    let config: ForgeUserdevConfig = read_zip_json_entry(
        &context
            .artifact(ArtifactId::from("forge-userdev"))?
            .cache_path,
        "config.json",
    )?;
    kind.userdev_names()
        .iter()
        .find_map(|name| config.runs.get(*name))
        .cloned()
        .ok_or_else(|| {
            eyre::eyre!(
                "Forge userdev config does not define any run config for {} (tried: {})",
                kind.command_name(),
                kind.userdev_names().join(", ")
            )
        })
}

fn run_config_requires_assets(config: &ForgeRunConfig) -> bool {
    config
        .args
        .iter()
        .chain(config.jvm_args.iter())
        .chain(config.env.values())
        .chain(config.props.values())
        .any(|value| value.contains("{asset_index}") || value.contains("{assets_root}"))
}

fn replace_placeholders(input: &str, replacements: &[(&str, &str)]) -> String {
    replacements
        .iter()
        .fold(input.to_string(), |output, (needle, replacement)| {
            output.replace(needle, replacement)
        })
}

fn resolve_forge_userdev_modules(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    let _span = tracing::debug_span!("resolve_forge_userdev_modules").entered();
    let config: ForgeUserdevConfig = {
        let _span = tracing::debug_span!("resolve_forge_userdev_modules_read_config").entered();
        read_zip_json_entry(
            &context
                .artifact(ArtifactId::from("forge-userdev"))?
                .cache_path,
            "config.json",
        )?
    };
    let mut artifacts = Vec::new();
    for (index, coordinate) in config.modules.into_iter().enumerate() {
        context.bail_if_cancelled()?;
        artifacts.push((
            ArtifactId::from(format!("forge-userdev-module-{index}")),
            MavenCoordinate::parse(&coordinate)?,
            ArtifactPurpose::from("Forge userdev module path"),
        ));
    }
    let _span = tracing::debug_span!(
        "resolve_forge_userdev_modules_resolve_artifacts",
        modules = artifacts.len()
    )
    .entered();
    Ok(resolver
        .resolve_artifacts(artifacts)?
        .into_iter()
        .map(|artifact| artifact.cache_path)
        .collect())
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        mc = %context.plan.minecraft_version,
        kind = kind.command_name(),
    )
)]
fn resolve_run_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: RunKind,
) -> eyre::Result<RunClasspath> {
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let _span = tracing::debug_span!("resolve_run_classpath_neogradle").entered();
        return resolve_neogradle_run_classpath(context, resolver, kind);
    }

    let mut legacy = Vec::new();
    {
        let _span = tracing::debug_span!("resolve_run_classpath_ensure_forge_dev_jar").entered();
        legacy.push(ensure_run_forge_dev_jar(context)?);
    };
    {
        let _span = tracing::debug_span!("resolve_run_classpath_ensure_client_extra_jar").entered();
        legacy.push(ensure_client_extra_jar(context)?);
    };
    {
        let _span = tracing::debug_span!("resolve_run_classpath_ensure_mcp_csv_mappings").entered();
        legacy.push(ensure_runtime_mcp_csv_mappings(context)?);
    };
    {
        let _span = tracing::debug_span!("resolve_run_classpath_minecraft_libraries").entered();
        legacy.extend(resolve_current_minecraft_libraries(
            context,
            &resolver.client,
        )?);
    };
    {
        let _span = tracing::debug_span!("resolve_run_classpath_forge_userdev_libraries").entered();
        legacy.extend(resolve_forge_userdev_libraries(context, resolver)?);
    };
    {
        let _span = tracing::debug_span!("resolve_run_classpath_plain_dependencies").entered();
        legacy.extend(resolve_run_plain_dependencies(context, resolver, kind)?);
    };

    let userdev_mods = {
        let _span = tracing::debug_span!("resolve_run_classpath_deobf_dependencies").entered();
        resolve_run_deobf_dependencies(context, resolver, kind)?
    };
    let legacy = {
        let _span =
            tracing::debug_span!("resolve_run_classpath_dedup_legacy", entries = legacy.len())
                .entered();
        dedup_paths_preserve_order(legacy)
    };
    let userdev_mods = {
        let _span = tracing::debug_span!(
            "resolve_run_classpath_dedup_userdev_mods",
            entries = userdev_mods.len()
        )
        .entered();
        dedup_paths_preserve_order(userdev_mods)
    };
    tracing::info!(
        legacy_entries = legacy.len(),
        userdev_mods = userdev_mods.len(),
        "run_classpath resolved"
    );
    Ok(RunClasspath {
        legacy,
        userdev_mods,
    })
}

fn resolve_neogradle_run_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: RunKind,
) -> eyre::Result<RunClasspath> {
    let mut legacy = Vec::new();
    {
        let _span =
            tracing::debug_span!("resolve_neogradle_run_classpath_minecraft_libraries").entered();
        legacy.extend(resolve_current_minecraft_libraries(
            context,
            &resolver.client,
        )?);
    };
    {
        let _span =
            tracing::debug_span!("resolve_neogradle_run_classpath_userdev_libraries").entered();
        legacy.extend(resolve_forge_userdev_libraries(context, resolver)?);
    };
    {
        let _span =
            tracing::debug_span!("resolve_neogradle_run_classpath_client_extra_jar").entered();
        legacy.push(ensure_client_extra_jar(context)?);
    };
    {
        let _span =
            tracing::debug_span!("resolve_neogradle_run_classpath_dev_jars", kind = %kind.command_name())
                .entered();
        legacy.extend(ensure_run_neoforge_dev_jars(context, kind)?);
    };

    let mut userdev_mods = Vec::new();
    if matches!(kind, RunKind::GameTestServer) {
        let _span =
            tracing::debug_span!("resolve_neogradle_run_classpath_test_libraries").entered();
        userdev_mods.extend(resolve_forge_userdev_test_libraries(context, resolver)?);
    }
    {
        let _span =
            tracing::debug_span!("resolve_neogradle_run_classpath_run_dependencies").entered();
        userdev_mods.extend(resolve_neogradle_run_dependencies(context, kind)?);
    };

    let legacy = {
        let _span = tracing::debug_span!(
            "resolve_neogradle_run_classpath_dedup_legacy",
            entries = legacy.len()
        )
        .entered();
        dedup_paths_preserve_order(legacy)
    };
    let userdev_mods = {
        let _span = tracing::debug_span!(
            "resolve_neogradle_run_classpath_dedup_userdev_mods",
            entries = userdev_mods.len()
        )
        .entered();
        dedup_paths_preserve_order(userdev_mods)
    };
    tracing::info!(
        legacy_entries = legacy.len(),
        userdev_mods = userdev_mods.len(),
        "neogradle_run_classpath resolved"
    );
    Ok(RunClasspath {
        legacy,
        userdev_mods,
    })
}

#[expect(
    clippy::too_many_lines,
    reason = "NeoGradle dev jar setup mirrors the userdev config steps in order."
)]
fn ensure_run_neoforge_dev_jars(
    context: &ExecutionContext<'_>,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let _span =
        tracing::debug_span!("ensure_run_neoforge_dev_jars", kind = %kind.command_name()).entered();
    let input = loader_dev_compile_jar(context);
    if !input.is_file() {
        eyre::bail!(
            "{} requires the Rust-owned NeoForm dev jar first: {}",
            kind.command_name(),
            input.display()
        );
    }
    context.assert_allowed_input(&input)?;
    let neoforge_universal = context.artifact(ArtifactId::from("neoforge-universal"))?;
    context.assert_allowed_input(&neoforge_universal.cache_path)?;
    let neoforge_version = required_property(&context.plan.properties, "neo_version")?;
    let requires_split_runtime = {
        let _span = tracing::debug_span!(
            "ensure_run_neoforge_check_split_runtime",
            universal = %neoforge_universal.cache_path.display()
        )
        .entered();
        neoforge_requires_split_runtime(&neoforge_universal.cache_path)?
    };
    if requires_split_runtime {
        let minecraft_output = context
            .plan
            .cache_dir
            .join("run")
            .join(format!("minecraft-{neoforge_version}.jar"));
        let minecraft_input_state = {
            let _span = tracing::debug_span!(
                "ensure_run_neoforge_hash_split_runtime_inputs",
                input = %input.display(),
                universal = %neoforge_universal.cache_path.display()
            )
            .entered();
            format!(
                "{}\n{}\nsplit-minecraft-v3\n",
                ContentHash::from_path(&input, ContentHashAlgorithm::Blake3)?,
                ContentHash::from_path(
                    &neoforge_universal.cache_path,
                    ContentHashAlgorithm::Blake3
                )?
            )
        };
        let minecraft_input_state_path = minecraft_output.with_extension("inputs.sha1");
        let current_minecraft_input_state =
            fs::read_to_string(&minecraft_input_state_path).unwrap_or_default();
        if !minecraft_output.is_file()
            || context.plan.refresh
            || current_minecraft_input_state != minecraft_input_state
        {
            let _span = tracing::debug_span!(
                "ensure_run_neoforge_write_split_minecraft_jar",
                output = %minecraft_output.display()
            )
            .entered();
            write_run_neoforge_minecraft_dev_jar(
                &input,
                &neoforge_universal.cache_path,
                &minecraft_output,
            )?;
            fs::write(&minecraft_input_state_path, minecraft_input_state).wrap_err_with(|| {
                format!(
                    "Failed to write NeoForge Minecraft run jar input state {}",
                    minecraft_input_state_path.display()
                )
            })?;
        }
        return Ok(vec![
            minecraft_output,
            neoforge_universal.cache_path.clone(),
        ]);
    }

    let output = context
        .plan
        .cache_dir
        .join("run")
        .join(format!("neoforge-{neoforge_version}.jar"));
    let input_state = {
        let _span = tracing::debug_span!(
            "ensure_run_neoforge_hash_runtime_inputs",
            input = %input.display(),
            universal = %neoforge_universal.cache_path.display()
        )
        .entered();
        format!(
            "{}\n{}\n",
            ContentHash::from_path(&input, ContentHashAlgorithm::Blake3)?,
            ContentHash::from_path(&neoforge_universal.cache_path, ContentHashAlgorithm::Blake3)?
        )
    };
    let input_state_path = output.with_extension("inputs.sha1");
    let current_input_state = fs::read_to_string(&input_state_path).unwrap_or_default();
    if !output.is_file() || context.plan.refresh || current_input_state != input_state {
        let _span = tracing::debug_span!(
            "ensure_run_neoforge_write_runtime_jar",
            output = %output.display()
        )
        .entered();
        write_run_neoforge_dev_jar(&input, &neoforge_universal.cache_path, &output)?;
        fs::write(&input_state_path, input_state).wrap_err_with(|| {
            format!(
                "Failed to write NeoForge run jar input state {}",
                input_state_path.display()
            )
        })?;
    }
    Ok(vec![output])
}

fn ensure_run_forge_dev_jar(context: &ExecutionContext<'_>) -> eyre::Result<PathBuf> {
    let _span = tracing::debug_span!("ensure_run_forge_dev_jar").entered();
    let forge_version = required_property(&context.plan.properties, "neo_version")?;
    let input = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("classes")
        .join("dev-compile.jar");
    if !input.is_file() {
        eyre::bail!(
            "Forge userdev launch requires the Rust-owned mapped dev compile jar first: {}",
            input.display()
        );
    }
    context.assert_allowed_input(&input)?;
    let forge_universal = context.artifact(ArtifactId::from("forge-universal"))?;
    context.assert_allowed_input(&forge_universal.cache_path)?;

    let output = context.plan.cache_dir.join("run").join(format!(
        "forge-{}-{}-dev-compile.jar",
        context.plan.minecraft_version, forge_version
    ));
    let _span = tracing::debug_span!(
        "ensure_run_forge_write_dev_jar",
        input = %input.display(),
        output = %output.display()
    )
    .entered();
    write_run_forge_dev_jar(&input, &forge_universal.cache_path, &output)?;
    Ok(output)
}

fn ensure_client_extra_jar(context: &ExecutionContext<'_>) -> eyre::Result<PathBuf> {
    let _span = tracing::debug_span!("ensure_client_extra_jar").entered();
    let client_jar = context.plan.minecraft_version_cache_dir.join("client.jar");
    if !client_jar.is_file() {
        let _span = tracing::debug_span!(
            "ensure_client_extra_download_client_jar",
            output = %client_jar.display()
        )
        .entered();
        let client = Client::builder()
            .user_agent("sfm-propagate-changes/no-gradle-toolchain")
            .build()
            .wrap_err("Failed to create HTTP client")?;
        download_to_path(
            &context.cancellation_token,
            &client,
            &context.plan.minecraft.client_jar_url,
            &client_jar,
        )?;
    }
    context.assert_allowed_input(&client_jar)?;

    let output = context.plan.cache_dir.join("run").join("client-extra.jar");
    if output.is_file() && !context.plan.refresh {
        return Ok(output);
    }

    let _span = tracing::debug_span!(
        "ensure_client_extra_write_jar",
        input = %client_jar.display(),
        output = %output.display()
    )
    .entered();
    write_client_extra_jar(&client_jar, &output)?;
    Ok(output)
}

fn ensure_runtime_mcp_csv_mappings(context: &ExecutionContext<'_>) -> eyre::Result<PathBuf> {
    let _span = tracing::debug_span!("ensure_runtime_mcp_csv_mappings").entered();
    let input = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("mappings")
        .join("srg_to_official.tsrg");
    if !input.is_file() {
        eyre::bail!(
            "Forge userdev launch requires SRG-to-named mappings first: {}",
            input.display()
        );
    }
    context.assert_allowed_input(&input)?;

    let output = context.plan.cache_dir.join("run").join("mcp-mappings");
    let _span = tracing::debug_span!(
        "ensure_runtime_mcp_write_csv_mappings",
        input = %input.display(),
        output = %output.display()
    )
    .entered();
    write_runtime_mcp_csv_mappings(&input, &output)?;
    Ok(output)
}

fn ensure_run_refmap_remapping_file(context: &ExecutionContext<'_>) -> eyre::Result<PathBuf> {
    let input = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("mappings")
        .join("srg_to_official.tsrg");
    if !input.is_file() {
        eyre::bail!(
            "Forge userdev launch requires SRG-to-named mappings first: {}",
            input.display()
        );
    }
    context.assert_allowed_input(&input)?;

    let output = context
        .plan
        .cache_dir
        .join("project")
        .join("run-refmap-remap.srg");
    write_srg_to_named_mapping_file(&input, &output)?;
    Ok(output)
}

fn resolve_run_plain_dependencies(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let _span = tracing::debug_span!("resolve_run_plain_dependencies", kind = %kind.command_name())
        .entered();
    let dependency_script = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("dependencies")
        .join(context.plan.minecraft_version.as_str())
        .join("dependencies.gradle");
    let dependencies = {
        let _span = tracing::debug_span!(
            "resolve_run_plain_dependencies_parse_script",
            script = %dependency_script.display()
        )
        .entered();
        parse_dependency_script(&dependency_script, &context.plan.properties)?
    };
    let configurations = run_dependency_configurations(kind);
    let mut artifacts = Vec::new();
    for (index, dependency) in dependencies
        .iter()
        .filter(|dependency| {
            !dependency.fg_deobf
                && configurations.contains(&dependency.configuration.as_str())
                && !is_api_classifier(&dependency.coordinate)
        })
        .enumerate()
    {
        artifacts.push((
            ArtifactId::from(format!("run-plain-dependency-{index}")),
            dependency.coordinate.clone(),
            ArtifactPurpose::from("Forge userdev run classpath"),
        ));
    }
    let _span = tracing::debug_span!(
        "resolve_run_plain_dependencies_resolve_artifacts",
        dependencies = artifacts.len()
    )
    .entered();
    Ok(resolver
        .resolve_artifacts(artifacts)?
        .into_iter()
        .map(|artifact| artifact.cache_path)
        .collect())
}

fn is_api_classifier(coordinate: &MavenCoordinate) -> bool {
    coordinate.classifier.as_deref() == Some("api")
}

fn resolve_run_deobf_dependencies(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let _span = tracing::debug_span!("resolve_run_deobf_dependencies", kind = %kind.command_name())
        .entered();
    let dependency_output = context.plan.cache_dir.join("dependencies");
    let mapping_path = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("mappings")
        .join("srg_to_official.tsrg");
    if !mapping_path.is_file() {
        eyre::bail!(
            "{} requires generated dependency mappings first: {}",
            kind.command_name(),
            mapping_path.display()
        );
    }
    let mapping_hash = {
        let _span = tracing::debug_span!(
            "resolve_run_deobf_dependencies_hash_mapping",
            mapping = %mapping_path.display()
        )
        .entered();
        ContentHash::from_path(&mapping_path, ContentHashAlgorithm::Blake3)?
    };
    let configurations = run_dependency_configurations(kind);
    let mut selected = Vec::new();
    for dependency in context
        .plan
        .dependencies
        .iter()
        .filter(|dependency| configurations.contains(&dependency.configuration.as_str()))
    {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        if is_api_classifier(&coordinate) {
            continue;
        }
        let index = selected.len();
        selected.push((dependency, coordinate, index));
    }
    let artifacts = selected
        .iter()
        .map(|(dependency, coordinate, index)| {
            (
                ArtifactId::from(format!("run-deobf-dependency-{index}")),
                coordinate.clone(),
                ArtifactPurpose::from(format!("{} runtime dependency", dependency.configuration)),
            )
        })
        .collect::<Vec<_>>();
    let resolved_artifacts = {
        let _span = tracing::debug_span!(
            "resolve_run_deobf_dependencies_resolve_artifacts",
            dependencies = artifacts.len()
        )
        .entered();
        resolver.resolve_artifacts(artifacts)?
    };
    let mut output = Vec::new();
    for ((_, coordinate, _), artifact) in selected.into_iter().zip(resolved_artifacts) {
        let _span = tracing::debug_span!(
            "resolve_run_deobf_dependency_output",
            coordinate = %coordinate,
            artifact = %artifact.cache_path.display()
        )
        .entered();
        context.assert_allowed_input(&artifact.cache_path)?;
        let artifact_hash = resolved_artifact_hash(&artifact)?;
        let remapped = remapped_dependency_output_path(
            &dependency_output,
            &artifact_hash,
            &mapping_hash,
            &coordinate,
        );
        if !remapped.is_file() {
            eyre::bail!(
                "{} requires remapped dependency jar {}. Run jar build first.",
                kind.command_name(),
                remapped.display()
            );
        }
        context.assert_allowed_input(&remapped)?;
        output.push(remapped);
    }

    Ok(output)
}

fn resolve_neogradle_run_dependencies(
    context: &ExecutionContext<'_>,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let _span =
        tracing::debug_span!("resolve_neogradle_run_dependencies", kind = %kind.command_name())
            .entered();
    let dependency_output = context.plan.cache_dir.join("dependencies");
    let configurations = run_dependency_configurations(kind);
    let mut output = Vec::new();

    for dependency in context.plan.dependencies.iter().filter(|dependency| {
        configurations.contains(&dependency.configuration.as_str())
            && MavenCoordinate::parse(&dependency.resolved_notation)
                .is_ok_and(|coordinate| !is_api_classifier(&coordinate))
    }) {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        let _span = tracing::debug_span!(
            "resolve_neogradle_run_dependency_output",
            coordinate = %coordinate,
            configuration = %dependency.configuration
        )
        .entered();
        let copied = copied_neogradle_dependency_output_path(
            &dependency_output,
            &dependency.configuration,
            &coordinate,
        );
        if !copied.is_file() {
            eyre::bail!(
                "{} requires copied NeoGradle dependency jar {}. Run jar build first.",
                kind.command_name(),
                copied.display()
            );
        }
        context.assert_allowed_input(&copied)?;
        output.push(copied);
    }

    Ok(output)
}

fn run_dependency_configurations(kind: RunKind) -> &'static [&'static str] {
    match kind {
        RunKind::Data => &["jarJar"],
        RunKind::Test => &[
            "implementation",
            "compileOnly",
            "runtimeOnly",
            "testImplementation",
            "testCompileOnly",
            "testRuntimeOnly",
            "transitiveRuntime",
        ],
        RunKind::Client
        | RunKind::ClientSmoke
        | RunKind::ClientPuppet
        | RunKind::Server
        | RunKind::GameTestServer => &[
            "implementation",
            "jarJar",
            "runtimeOnly",
            "gametestImplementation",
            "gametestRuntimeOnly",
            "transitiveRuntime",
        ],
    }
}

fn write_classpath_file(path: &Path, classpath: &[PathBuf]) -> eyre::Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let lines = classpath
        .iter()
        .map(|path| path.display().to_string())
        .collect::<Vec<_>>();
    fs::write(path, format!("{}\n", lines.join("\n")))
        .wrap_err_with(|| format!("Failed to write {}", path.display()))
}

fn run_source_roots(context: &ExecutionContext<'_>, kind: RunKind) -> eyre::Result<String> {
    let mod_id = required_property(&context.plan.properties, "mod_id")?;
    let existing_roots = run_source_root_paths(context, kind)?;
    let separator = if cfg!(windows) { ";" } else { ":" };
    Ok(existing_roots
        .into_iter()
        .map(|path| format!("{mod_id}%%{}", path.display()))
        .collect::<Vec<_>>()
        .join(separator))
}

fn run_source_root_paths(
    context: &ExecutionContext<'_>,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let project_root = context.plan.cache_dir.join("project");
    let mut roots = vec![
        project_root.join("staged-resources"),
        project_root.join("classes"),
    ];
    let source_set = kind.optional_source_set();
    roots.push(project_root.join(source_set).join("resources"));
    roots.push(project_root.join(source_set).join("classes"));

    let existing_roots = roots
        .into_iter()
        .filter(|path| path.exists())
        .collect::<Vec<_>>();
    if existing_roots.is_empty() {
        eyre::bail!("No Rust-owned project class/resource roots are available for launch");
    }

    Ok(existing_roots)
}

fn kind_extra_program_args(plan: &BuildPlan, kind: RunKind) -> eyre::Result<Vec<String>> {
    if !matches!(kind, RunKind::Data) {
        return Ok(Vec::new());
    }
    Ok(vec![
        "--mod".to_string(),
        required_property(&plan.properties, "mod_id")?.to_string(),
        "--all".to_string(),
        "--output".to_string(),
        plan.minecraft_dir
            .join("src")
            .join("generated")
            .join("resources")
            .display()
            .to_string(),
        "--existing".to_string(),
        plan.minecraft_dir
            .join("src")
            .join("main")
            .join("resources")
            .display()
            .to_string(),
    ])
}

#[expect(
    clippy::too_many_lines,
    reason = "Asset preparation follows the Minecraft version manifest shape linearly."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn prepare_minecraft_assets(context: &ExecutionContext<'_>) -> eyre::Result<MinecraftAssets> {
    context.bail_if_cancelled()?;
    let client = Client::builder()
        .user_agent("sfm-propagate-changes/no-gradle-toolchain")
        .build()
        .wrap_err("Failed to create HTTP client")?;
    let version_json: MinecraftVersionJson = {
        let _span = tracing::debug_span!(
            "prepare_minecraft_assets_read_version_json",
            path = %context.plan.minecraft.version_json.cache_path.display()
        )
        .entered();
        read_json_file(&context.plan.minecraft.version_json.cache_path)?
    };
    let asset_index = version_json
        .asset_index
        .ok_or_else(|| eyre::eyre!("Minecraft version JSON missing assetIndex"))?;
    let MinecraftAssetIndex {
        id: index_id,
        url: index_url,
    } = asset_index;
    let assets_root = context.plan.minecraft_assets_dir.clone();
    let index_path = assets_root.join("indexes").join(format!("{index_id}.json"));
    {
        let _span = tracing::debug_span!(
            "prepare_minecraft_assets_download_index",
            index_id = index_id.as_str(),
            path = %index_path.display()
        )
        .entered();
        download_to_path(
            &context.cancellation_token,
            &client,
            &index_url,
            &index_path,
        )?;
    };
    context.bail_if_cancelled()?;

    let index_json: MinecraftAssetIndexJson = {
        let _span = tracing::debug_span!(
            "prepare_minecraft_assets_read_index",
            path = %index_path.display()
        )
        .entered();
        read_json_file(&index_path)?
    };
    let objects = index_json.objects;
    let total_assets = objects.len();
    let assets = {
        let _span = tracing::debug_span!(
            "prepare_minecraft_assets_collect_unique",
            total = total_assets
        )
        .entered();
        minecraft_asset_downloads(&assets_root, &objects)?
    };
    let unique_assets = assets.len();
    let stats = {
        let _span = tracing::debug_span!(
            "prepare_minecraft_assets_download_objects",
            unique_assets,
            workers = rayon::current_num_threads()
        )
        .entered();
        let checked = AtomicUsize::new(0);
        let downloaded = AtomicUsize::new(0);
        let stats = assets
            .par_iter()
            .map(|asset| {
                let asset_downloaded = prepare_minecraft_asset(context, &client, asset)?;
                let checked = checked.fetch_add(1, AtomicOrdering::Relaxed) + 1;
                if asset_downloaded {
                    let downloaded = downloaded.fetch_add(1, AtomicOrdering::Relaxed) + 1;
                    if downloaded.is_multiple_of(100) {
                        tracing::info!(
                            "Downloaded {downloaded} missing Minecraft assets ({checked}/{unique_assets})"
                        );
                    }
                }
                Ok(MinecraftAssetPrepareStats {
                    checked: 1,
                    downloaded: usize::from(asset_downloaded),
                })
            })
            .collect::<Vec<eyre::Result<_>>>()
            .into_iter()
            .collect::<eyre::Result<Vec<_>>>()?;
        stats
            .into_iter()
            .fold(MinecraftAssetPrepareStats::default(), |mut total, stats| {
                total.checked += stats.checked;
                total.downloaded += stats.downloaded;
                total
            })
    };
    let downloaded = stats.downloaded;
    if downloaded > 0 {
        tracing::info!(
            "Downloaded {downloaded} Minecraft assets into {}",
            assets_root.display()
        );
    }
    tracing::info!(
        asset_index = index_id.as_str(),
        checked = stats.checked,
        downloaded,
        total = total_assets,
        unique_assets,
        workers = rayon::current_num_threads(),
        assets_root = %assets_root.display(),
        "minecraft_assets_prepared"
    );

    Ok(MinecraftAssets {
        root: assets_root,
        index_id,
    })
}

#[derive(Debug)]
struct MinecraftAssetDownload {
    hash: ContentHash,
    path: PathBuf,
    url: String,
}

#[derive(Debug, Default)]
struct MinecraftAssetPrepareStats {
    checked: usize,
    downloaded: usize,
}

fn minecraft_asset_downloads(
    assets_root: &Path,
    objects: &BTreeMap<String, MinecraftAssetObject>,
) -> eyre::Result<Vec<MinecraftAssetDownload>> {
    let mut assets = BTreeMap::new();
    for object in objects.values() {
        let hash = object.hash;
        let hash_hex = hash.hex();
        let prefix = hash_hex
            .get(..2)
            .ok_or_else(|| eyre::eyre!("Minecraft asset hash is too short: {hash}"))?;
        let key = hash.to_string();
        if assets.contains_key(&key) {
            continue;
        }
        assets.insert(
            key,
            MinecraftAssetDownload {
                hash,
                path: assets_root.join("objects").join(prefix).join(&hash_hex),
                url: format!("https://resources.download.minecraft.net/{prefix}/{hash_hex}"),
            },
        );
    }
    Ok(assets.into_values().collect())
}

fn prepare_minecraft_asset(
    context: &ExecutionContext<'_>,
    client: &Client,
    asset: &MinecraftAssetDownload,
) -> eyre::Result<bool> {
    let _span = tracing::debug_span!(
        "prepare_minecraft_asset",
        hash = %asset.hash,
        path = %asset.path.display()
    )
    .entered();
    context.bail_if_cancelled()?;
    if existing_file_matches_hash(&asset.path, &asset.hash)? {
        context.assert_allowed_input(&asset.path)?;
        return Ok(false);
    }
    download_to_path_overwrite_with_expected_hash(
        &context.cancellation_token,
        client,
        &asset.url,
        &asset.path,
        true,
        &asset.hash,
    )?;
    context.assert_allowed_input(&asset.path)?;
    Ok(true)
}

#[derive(Debug)]
struct ExecutionContext<'a> {
    plan: &'a BuildPlan,
    forbidden_input_roots: Vec<PathBuf>,
    cancellation_token: CancellationToken,
    minecraft_libraries_cache: Mutex<Option<Vec<PathBuf>>>,
}

#[derive(Debug, Facet)]
struct NodeState {
    schema_version: u32,
    id: String,
    status: String,
    started_at_unix_ms: u128,
    duration_ms: u128,
    #[facet(proxy = JsonPath)]
    java_executable: PathBuf,
    java_version: String,
    inputs: Vec<String>,
    outputs: Vec<NodeOutputState>,
}

#[derive(Debug, Facet)]
struct NodeOutputState {
    #[facet(proxy = JsonPath)]
    path: PathBuf,
    exists: bool,
    sha1: Option<ContentHash>,
}

impl<'a> ExecutionContext<'a> {
    fn new(plan: &'a BuildPlan, cancellation_token: CancellationToken) -> eyre::Result<Self> {
        cancellation_token.bail_if_cancelled()?;
        let forbidden_input_roots = [
            plan.minecraft_dir.join("build").join("fg_cache"),
            plan.minecraft_dir.join("build").join("classpath"),
            plan.minecraft_dir.join("build").join("classes"),
            plan.minecraft_dir.join("build").join("resources"),
            plan.minecraft_dir.join("build").join("tmp").join("jar"),
        ]
        .into_iter()
        .map(|path| canonicalize_lenient(&path))
        .collect::<eyre::Result<Vec<_>>>()?;

        Ok(Self {
            plan,
            forbidden_input_roots,
            cancellation_token,
            minecraft_libraries_cache: Mutex::new(None),
        })
    }

    fn bail_if_cancelled(&self) -> eyre::Result<()> {
        self.cancellation_token.bail_if_cancelled()
    }

    fn write_node_state(
        &self,
        id: &str,
        inputs: &[&str],
        outputs: &[PathBuf],
        status: &str,
    ) -> eyre::Result<()> {
        self.bail_if_cancelled()?;
        let started = Instant::now();
        let _span = tracing::debug_span!(
            "write_node_state",
            id,
            status,
            inputs = inputs.len(),
            outputs = outputs.len()
        )
        .entered();
        let started_at_unix_ms = {
            let _span = tracing::debug_span!("write_node_state_timestamp").entered();
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map_or(0, |duration| duration.as_millis())
        };
        let output_states = {
            let _span =
                tracing::debug_span!("write_node_state_output_states", outputs = outputs.len())
                    .entered();
            let cancellation_token = self.cancellation_token.clone();
            outputs
                .par_iter()
                .map(|path| {
                    cancellation_token.bail_if_cancelled()?;
                    let _span = tracing::debug_span!(
                        "write_node_state_output_state",
                        path = %path.display()
                    )
                    .entered();
                    let exists = path.exists();
                    let sha1 = path
                        .is_file()
                        .then(|| ContentHash::from_path(path, ContentHashAlgorithm::Blake3))
                        .transpose()?;
                    Ok(NodeOutputState {
                        path: path.clone(),
                        exists,
                        sha1,
                    })
                })
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()?
        };
        self.bail_if_cancelled()?;
        let state = {
            let _span = tracing::debug_span!("write_node_state_build").entered();
            NodeState {
                schema_version: 1,
                id: id.to_string(),
                status: status.to_string(),
                started_at_unix_ms,
                duration_ms: started.elapsed().as_millis(),
                java_executable: self.plan.java.executable.clone(),
                java_version: self.plan.java.version_output.clone(),
                inputs: inputs.iter().map(|input| (*input).to_string()).collect(),
                outputs: output_states,
            }
        };

        {
            let _span = tracing::debug_span!(
                "write_node_state_create_dir",
                dir = %self.plan.state_dir.display()
            )
            .entered();
            fs::create_dir_all(&self.plan.state_dir)?;
        };
        let state_path = self.plan.state_dir.join(format!("{id}.json"));
        let state_json = {
            let _span =
                tracing::debug_span!("write_node_state_encode", path = %state_path.display())
                    .entered();
            facet_json::to_string_pretty(&state)?
        };
        {
            let _span =
                tracing::debug_span!("write_node_state_write", path = %state_path.display())
                    .entered();
            fs::write(&state_path, state_json)
                .wrap_err_with(|| format!("Failed to write {}", state_path.display()))?;
        };
        Ok(())
    }

    fn assert_allowed_input(&self, path: &Path) -> eyre::Result<()> {
        let canonical = canonicalize_lenient(path)?;
        for forbidden in &self.forbidden_input_roots {
            if canonical.starts_with(forbidden) {
                eyre::bail!(
                    "Clean-slate jar build attempted to read forbidden Gradle output path: {}",
                    path.display()
                );
            }
        }
        Ok(())
    }

    #[expect(
        clippy::needless_pass_by_value,
        reason = "ArtifactId call sites construct ids inline and this lookup API owns that boundary."
    )]
    fn artifact(&self, id: ArtifactId) -> eyre::Result<&ArtifactPlan> {
        self.plan
            .artifacts
            .iter()
            .find(|artifact| artifact.id == id)
            .ok_or_else(|| eyre::eyre!("Resolved plan did not include artifact id {id}"))
    }

    #[expect(
        clippy::needless_pass_by_value,
        reason = "ArtifactId call sites construct ids inline and this lookup API owns that boundary."
    )]
    fn maybe_artifact(&self, id: ArtifactId) -> Option<&ArtifactPlan> {
        self.plan
            .artifacts
            .iter()
            .find(|artifact| artifact.id == id)
    }

    fn run_java_tool(
        &self,
        tool_id: &str,
        jvm_args: &[&str],
        args: &[String],
        work_dir: &Path,
    ) -> eyre::Result<()> {
        self.run_java_tool_with_classpath(tool_id, jvm_args, &[], args, work_dir)
    }

    #[tracing::instrument(level = "info", skip_all, fields(tool_id))]
    fn run_java_tool_with_classpath(
        &self,
        tool_id: &str,
        jvm_args: &[&str],
        extra_classpath: &[PathBuf],
        args: &[String],
        work_dir: &Path,
    ) -> eyre::Result<()> {
        let tool = self.artifact(ArtifactId::from(tool_id))?;
        self.assert_allowed_input(&tool.cache_path)?;
        for path in extra_classpath {
            self.assert_allowed_input(path)?;
        }
        fs::create_dir_all(work_dir)?;
        let main_class = read_main_class(&tool.cache_path)?;
        let classpath = dedup_paths_preserve_order(
            std::iter::once(tool.cache_path.clone())
                .chain(extra_classpath.iter().cloned())
                .collect(),
        );
        let classpath_arg = join_classpath(&classpath);
        let java_argfile = work_dir.join(format!("{tool_id}.java.args"));
        let mut java_args = jvm_args
            .iter()
            .map(|arg| (*arg).to_string())
            .collect::<Vec<_>>();
        java_args.extend(["-cp".to_string(), classpath_arg.clone(), main_class.clone()]);
        java_args.extend(args.iter().cloned());
        fs::write(
            &java_argfile,
            java_args
                .into_iter()
                .map(escape_argfile_arg)
                .collect::<Vec<_>>()
                .join("\n"),
        )
        .wrap_err_with(|| format!("Failed to write {}", java_argfile.display()))?;
        let started = Instant::now();
        let log_path = work_dir.join("console.log");
        tracing::info!(
            "Java tool {tool_id}: start main={main_class} argfile={} log={}",
            java_argfile.display(),
            log_path.display()
        );
        let mut command = Command::new(&self.plan.java.executable);
        command
            .arg(format!("@{}", java_argfile.display()))
            .current_dir(work_dir);
        let output = run_command_capture_output(&self.cancellation_token, &mut command, tool_id)
            .wrap_err_with(|| {
                format!(
                    "Failed to run Java tool {tool_id} using {}",
                    self.plan.java.executable.display()
                )
            })?;
        trace_subprocess_bytes(self.plan, "java-tool", tool_id, "stdout", &output.stdout);
        trace_subprocess_bytes(self.plan, "java-tool", tool_id, "stderr", &output.stderr);

        let duration_ms = started.elapsed().as_millis();
        write_java_tool_console_log(
            &log_path,
            tool_id,
            &main_class,
            duration_ms,
            &classpath_arg,
            args,
            &output,
        )?;

        if output.cancelled {
            eyre::bail!(
                "Java tool {tool_id} was cancelled by Ctrl+C. See {}",
                log_path.display()
            );
        }
        if !output.status.success() {
            tracing::warn!(
                tool_id,
                status = %output.status,
                duration_ms,
                log_path = %log_path.display(),
                "java_tool_failed"
            );
            eyre::bail!(
                "Java tool {tool_id} failed with {}. See {}",
                output.status,
                log_path.display()
            );
        }
        tracing::info!(
            tool_id,
            duration_ms,
            log_path = %log_path.display(),
            "java_tool_completed"
        );
        tracing::info!("Java tool {tool_id}: done in {duration_ms} ms");
        Ok(())
    }
}

#[expect(
    clippy::too_many_lines,
    reason = "clean-slate MCP executor is being split incrementally"
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn execute_mcp_config_joined(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let client = Client::builder()
        .user_agent("sfm-propagate-changes/no-gradle-toolchain")
        .build()
        .wrap_err("Failed to create HTTP client")?;
    let mcp_root = context
        .plan
        .cache_dir
        .join("mcp")
        .join(context.plan.minecraft_version.as_str())
        .join("joined");
    fs::create_dir_all(&mcp_root)?;
    let output = mcp_root.join("patch").join("joined-patched-sources.jar");
    if output.is_file() && !context.plan.refresh {
        tracing::info!(
            output = %output.display(),
            "mcp_config_joined cache hit"
        );
        tracing::info!(
            "Build node execute-mcp-config-joined: reusing {}",
            output.display()
        );
        context.write_node_state(
            "execute-mcp-config-joined",
            &["Minecraft client/server jars", "MCPConfig config.json"],
            &[output],
            "cached",
        )?;
        return Ok(());
    }
    tracing::info!(
        output = %output.display(),
        refresh = context.plan.refresh,
        "mcp_config_joined cache miss"
    );

    let client_jar = context.plan.minecraft_version_cache_dir.join("client.jar");
    let server_bundle = context
        .plan
        .minecraft_version_cache_dir
        .join("server-bundle.jar");
    let client_mappings = context.plan.minecraft_version_cache_dir.join("client.txt");

    download_to_path(
        &context.cancellation_token,
        &client,
        &context.plan.minecraft.client_jar_url,
        &client_jar,
    )?;
    context.bail_if_cancelled()?;
    download_to_path(
        &context.cancellation_token,
        &client,
        &context.plan.minecraft.server_jar_url,
        &server_bundle,
    )?;
    context.bail_if_cancelled()?;
    download_to_path(
        &context.cancellation_token,
        &client,
        required_minecraft_mapping_url(
            context,
            context.plan.minecraft.client_mappings_url.as_deref(),
            "client",
        )?,
        &client_mappings,
    )?;
    context.bail_if_cancelled()?;

    context.assert_allowed_input(&client_jar)?;
    context.assert_allowed_input(&server_bundle)?;
    context.assert_allowed_input(&client_mappings)?;

    let mcp_config = context.artifact(ArtifactId::from("mcp-config"))?;
    context.assert_allowed_input(&mcp_config.cache_path)?;
    let data_dir = mcp_root.join("data");
    fs::create_dir_all(&data_dir)?;
    let joined_tsrg = data_dir.join("joined.tsrg");
    extract_zip_entry_to_path(&mcp_config.cache_path, "config/joined.tsrg", &joined_tsrg)?;
    context.bail_if_cancelled()?;

    let extract_server = mcp_root.join("extractServer").join("output.jar");
    context.run_java_tool(
        "tool-installer-tools-1-3",
        &[],
        &[
            "--task".to_string(),
            "bundler_extract".to_string(),
            "--input".to_string(),
            server_bundle.display().to_string(),
            "--output".to_string(),
            extract_server.display().to_string(),
            "--jar-only".to_string(),
        ],
        &mcp_root.join("extractServer"),
    )?;

    let merged_mappings = mcp_root.join("mergeMappings").join("output.tsrg");
    context.run_java_tool(
        "tool-installer-tools-1-2",
        &[],
        &[
            "--task".to_string(),
            "MERGE_MAPPING".to_string(),
            "--left".to_string(),
            joined_tsrg.display().to_string(),
            "--right".to_string(),
            client_mappings.display().to_string(),
            "--right-names".to_string(),
            "right,left".to_string(),
            "--classes".to_string(),
            "--output".to_string(),
            merged_mappings.display().to_string(),
        ],
        &mcp_root.join("mergeMappings"),
    )?;

    let mapped_classes = read_tsrg_original_classes(&joined_tsrg)?;
    let stripped_client = mcp_root.join("stripClient").join("output.jar");
    copy_filtered_jar(&client_jar, &stripped_client, &mapped_classes)?;
    let stripped_server = mcp_root.join("stripServer").join("output.jar");
    copy_filtered_jar(&extract_server, &stripped_server, &mapped_classes)?;

    let merged_jar = mcp_root.join("merge").join("output.jar");
    context.run_java_tool(
        "tool-mergetool-1-1-5",
        &[],
        &[
            "--client".to_string(),
            stripped_client.display().to_string(),
            "--server".to_string(),
            stripped_server.display().to_string(),
            "--ann".to_string(),
            context.plan.minecraft_version.to_string(),
            "--output".to_string(),
            merged_jar.display().to_string(),
            "--inject".to_string(),
            "false".to_string(),
        ],
        &mcp_root.join("merge"),
    )?;

    let libraries_file = mcp_root.join("listLibraries").join("libraries.txt");
    write_minecraft_libraries_cfg(context, &client, &libraries_file)?;

    let renamed_jar = mcp_root.join("rename").join("output.jar");
    context.run_java_tool(
        "tool-fart",
        &[],
        &[
            "--input".to_string(),
            merged_jar.display().to_string(),
            "--output".to_string(),
            renamed_jar.display().to_string(),
            "--map".to_string(),
            merged_mappings.display().to_string(),
            "--cfg".to_string(),
            libraries_file.display().to_string(),
            "--ann-fix".to_string(),
            "--ids-fix".to_string(),
            "--src-fix".to_string(),
            "--record-fix".to_string(),
        ],
        &mcp_root.join("rename"),
    )?;

    let decompiled_jar = mcp_root.join("decompile").join("output.jar");
    context.run_java_tool(
        "tool-forgeflower",
        &["-Xmx4G"],
        &[
            "-din=1".to_string(),
            "-rbr=1".to_string(),
            "-dgs=1".to_string(),
            "-asc=1".to_string(),
            "-rsy=1".to_string(),
            "-iec=1".to_string(),
            "-jvn=1".to_string(),
            "-isl=0".to_string(),
            "-iib=1".to_string(),
            "-bsm=1".to_string(),
            "-dcl=1".to_string(),
            "-log=TRACE".to_string(),
            "-cfg".to_string(),
            libraries_file.display().to_string(),
            renamed_jar.display().to_string(),
            decompiled_jar.display().to_string(),
        ],
        &mcp_root.join("decompile"),
    )?;

    let injected_jar = mcp_root.join("inject").join("output.jar");
    inject_mcp_sources(&mcp_config.cache_path, &decompiled_jar, &injected_jar)?;

    apply_mcp_joined_patches(
        context,
        &mcp_config.cache_path,
        &injected_jar,
        &output,
        &mcp_root,
    )?;

    context.write_node_state(
        "execute-mcp-config-joined",
        &["Minecraft client/server jars", "MCPConfig config.json"],
        &[output],
        "complete",
    )?;
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "clean-slate Forge userdev executor is being split incrementally"
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn execute_forge_userdev(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let client = Client::builder()
        .user_agent("sfm-propagate-changes/no-gradle-toolchain")
        .build()
        .wrap_err("Failed to create HTTP client")?;
    let forge_root = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str());
    fs::create_dir_all(&forge_root)?;
    let mappings_root = forge_root.join("mappings");
    let srg_to_official = mappings_root.join("srg_to_official.tsrg");
    let official_to_srg = mappings_root.join("official_to_srg.tsrg");
    let output = forge_root.join("classes").join("dev-compile.jar");
    if output.is_file()
        && srg_to_official.is_file()
        && official_to_srg.is_file()
        && !context.plan.refresh
    {
        tracing::info!(
            output = %output.display(),
            "forge_userdev cache hit"
        );
        tracing::info!(
            "Build node execute-forge-userdev: reusing {}",
            output.display()
        );
        context.write_node_state(
            "execute-forge-userdev",
            &["Forge userdev", "MCPConfig joined outputs"],
            &[output],
            "cached",
        )?;
        return Ok(());
    }
    tracing::info!(
        output = %output.display(),
        refresh = context.plan.refresh,
        "forge_userdev cache miss"
    );

    let mcp_root = context
        .plan
        .cache_dir
        .join("mcp")
        .join(context.plan.minecraft_version.as_str())
        .join("joined");
    let mcp_sources = mcp_root.join("patch").join("joined-patched-sources.jar");
    if !mcp_sources.is_file() {
        eyre::bail!(
            "Forge userdev requires MCP joined sources first: {}",
            mcp_sources.display()
        );
    }

    let userdev = context.artifact(ArtifactId::from("forge-userdev"))?;
    let forge_sources = context.artifact(ArtifactId::from("forge-sources"))?;
    let forge_universal = context.artifact(ArtifactId::from("forge-universal"))?;
    context.assert_allowed_input(&userdev.cache_path)?;
    context.assert_allowed_input(&forge_sources.cache_path)?;
    context.assert_allowed_input(&forge_universal.cache_path)?;

    let patched_minecraft_sources = forge_root.join("sourcePatches").join("minecraft-srg.jar");
    context.run_java_tool(
        "tool-diffpatch",
        &[],
        &[
            "--patch".to_string(),
            "--mode".to_string(),
            "OFFSET".to_string(),
            "--archive".to_string(),
            "ZIP".to_string(),
            "--archive-rejects".to_string(),
            "ZIP".to_string(),
            "--prefix".to_string(),
            "patches/".to_string(),
            "--output".to_string(),
            patched_minecraft_sources.display().to_string(),
            "--reject".to_string(),
            forge_root
                .join("sourcePatches")
                .join("rejects.zip")
                .display()
                .to_string(),
            mcp_sources.display().to_string(),
            userdev.cache_path.display().to_string(),
        ],
        &forge_root.join("sourcePatches"),
    )?;
    context.bail_if_cancelled()?;

    let combined_srg_sources = forge_root.join("sources").join("combined-srg.jar");
    merge_zip_archives(
        &[
            patched_minecraft_sources.clone(),
            forge_sources.cache_path.clone(),
        ],
        &combined_srg_sources,
    )?;

    let client_mappings = context.plan.minecraft_version_cache_dir.join("client.txt");
    let server_mappings = context.plan.minecraft_version_cache_dir.join("server.txt");
    download_to_path(
        &context.cancellation_token,
        &client,
        required_minecraft_mapping_url(
            context,
            context.plan.minecraft.client_mappings_url.as_deref(),
            "client",
        )?,
        &client_mappings,
    )?;
    context.bail_if_cancelled()?;
    download_to_path(
        &context.cancellation_token,
        &client,
        required_minecraft_mapping_url(
            context,
            context.plan.minecraft.server_mappings_url.as_deref(),
            "server",
        )?,
        &server_mappings,
    )?;
    context.bail_if_cancelled()?;
    fs::create_dir_all(&mappings_root)?;
    let obf_to_official = mappings_root.join("obf_to_official.tsrg");
    let parchment_parameters = context
        .artifact(ArtifactId::from("parchment-data"))
        .ok()
        .map(|artifact| read_parchment_parameters(&artifact.cache_path))
        .transpose()?;
    generate_mojang_tsrg_mappings(
        &mcp_root.join("mergeMappings").join("output.tsrg"),
        &[client_mappings, server_mappings],
        parchment_parameters.as_ref(),
        &obf_to_official,
        &srg_to_official,
        &official_to_srg,
    )?;
    context.bail_if_cancelled()?;

    let official_sources = forge_root.join("sources").join("combined-official.jar");
    context.run_java_tool(
        "tool-fart",
        &[],
        &[
            "--input".to_string(),
            combined_srg_sources.display().to_string(),
            "--output".to_string(),
            official_sources.display().to_string(),
            "--map".to_string(),
            srg_to_official.display().to_string(),
            "--src-fix".to_string(),
            "--record-fix".to_string(),
        ],
        &forge_root.join("remapSources"),
    )?;

    let binpatches = forge_root.join("binpatch").join("joined.lzma");
    extract_zip_entry_to_path(&userdev.cache_path, "joined.lzma", &binpatches)?;
    let binpatched_minecraft = forge_root.join("classes").join("minecraft-binpatched.jar");
    context.run_java_tool(
        "forge-binarypatcher",
        &[],
        &[
            "--clean".to_string(),
            mcp_root
                .join("rename")
                .join("output.jar")
                .display()
                .to_string(),
            "--output".to_string(),
            binpatched_minecraft.display().to_string(),
            "--apply".to_string(),
            binpatches.display().to_string(),
        ],
        &forge_root.join("binpatch"),
    )?;
    if !binpatched_minecraft.is_file() {
        eyre::bail!(
            "BinaryPatcher completed without producing {}",
            binpatched_minecraft.display()
        );
    }

    let merged_output = forge_root
        .join("classes")
        .join("dev-compile-srg-untransformed.jar");
    let clean_named_minecraft = mcp_root.join("rename").join("output.jar");
    merge_zip_archives(
        &[
            binpatched_minecraft,
            clean_named_minecraft,
            forge_universal.cache_path.clone(),
        ],
        &merged_output,
    )?;

    let access_transformed = forge_root.join("classes").join("dev-compile-srg-at.jar");
    let access_transformed_patched = forge_root.join("classes").join("dev-compile-srg.jar");
    let forge_at = forge_root
        .join("accessTransform")
        .join("forge-accesstransformer.cfg");
    extract_zip_entry_to_path(&userdev.cache_path, "ats/accesstransformer.cfg", &forge_at)?;
    let project_at = context
        .plan
        .minecraft_dir
        .join("src")
        .join("main")
        .join("resources")
        .join("META-INF")
        .join("accesstransformer.cfg");
    context.run_java_tool(
        "tool-access-transformers",
        &[],
        &[
            "--inJar".to_string(),
            merged_output.display().to_string(),
            "--outJar".to_string(),
            access_transformed.display().to_string(),
            "--atFile".to_string(),
            forge_at.display().to_string(),
            "--atFile".to_string(),
            project_at.display().to_string(),
        ],
        &forge_root.join("accessTransform"),
    )?;
    if !access_transformed.is_file() {
        eyre::bail!(
            "AccessTransformers completed without producing {}",
            access_transformed.display()
        );
    }
    patch_inner_class_access_in_jar(
        &access_transformed,
        &access_transformed_patched,
        "net/minecraft/client/gui/components/MultilineTextField",
        "net/minecraft/client/gui/components/MultilineTextField$StringView",
    )?;
    context.run_java_tool(
        "tool-fart",
        &[],
        &[
            "--input".to_string(),
            access_transformed_patched.display().to_string(),
            "--output".to_string(),
            output.display().to_string(),
            "--map".to_string(),
            srg_to_official.display().to_string(),
            "--ann-fix".to_string(),
            "--ids-fix".to_string(),
            "--record-fix".to_string(),
        ],
        &forge_root.join("remapDevCompile"),
    )?;
    if !output.is_file() {
        eyre::bail!(
            "FART completed without producing mapped Forge dev compile jar {}",
            output.display()
        );
    }
    context.write_node_state(
        "execute-forge-userdev",
        &["Forge userdev", "MCPConfig joined outputs"],
        &[output],
        "complete",
    )
}

#[expect(
    clippy::too_many_lines,
    reason = "NeoForm userdev orchestration keeps cache checks, arguments, and state writes visible."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn execute_neoform_userdev(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let neoform_root = context
        .plan
        .cache_dir
        .join("neoform")
        .join(context.plan.minecraft_version.as_str());
    let output_root = neoform_root.join("classes");
    let nfrt_home = neoform_root.join("nfrt-home");
    let nfrt_work = neoform_root.join("nfrt-work");
    let artifact_manifest = neoform_root.join("artifact-manifest.properties");
    let problem_report = neoform_root.join("problems.json");
    let game_jar = neoform_dev_compile_jar(context);
    let game_sources = output_root.join("gameSourcesWithNeoForge.jar");

    if context.plan.refresh {
        tracing::info!("neoform_userdev refresh requested");
        reset_cache_directory(&context.plan.cache_dir, &neoform_root)?;
    }
    fs::create_dir_all(&output_root)?;
    fs::create_dir_all(&nfrt_home)?;
    fs::create_dir_all(&nfrt_work)?;
    write_neoform_artifact_manifest(context, &artifact_manifest)?;

    if game_jar.is_file() && !context.plan.refresh {
        tracing::info!(
            output = %game_jar.display(),
            "neoform_userdev cache hit"
        );
        context.write_node_state(
            "execute-neoform-userdev",
            &["NeoForge userdev", "NeoForm Runtime"],
            &[game_jar],
            "complete",
        )?;
        return Ok(());
    }
    tracing::info!(
        output = %game_jar.display(),
        refresh = context.plan.refresh,
        "neoform_userdev cache miss"
    );

    let mut args = vec![
        "--home-dir".to_string(),
        nfrt_home.display().to_string(),
        "--work-dir".to_string(),
        nfrt_work.display().to_string(),
        "--artifact-manifest".to_string(),
        artifact_manifest.display().to_string(),
        "--warn-on-artifact-manifest-miss".to_string(),
        "--no-color".to_string(),
        "--no-emojis".to_string(),
    ];
    for repository in &context.plan.repositories {
        args.push(format!("--add-repository={}", repository.url));
    }
    args.extend([
        "run".to_string(),
        "--dist".to_string(),
        "joined".to_string(),
        "--neoforge".to_string(),
        context.plan.loader_toolchain.userdev_coordinate.clone(),
        "--write-result".to_string(),
        format!("gameJarWithNeoForge:{}", game_jar.display()),
        "--write-result".to_string(),
        format!("gameSourcesWithNeoForge:{}", game_sources.display()),
        "--problems-report".to_string(),
        problem_report.display().to_string(),
    ]);

    if let Some(java_home) = &context.plan.java.home {
        args.extend(["--java-home".to_string(), java_home.display().to_string()]);
    }

    if let Some(parchment) = context.maybe_artifact(ArtifactId::from("parchment-data"))
        && let Some(coordinate) = &parchment.coordinate
    {
        args.extend([
            "--parchment-data".to_string(),
            coordinate.clone(),
            "--parchment-conflict-prefix".to_string(),
            "p_".to_string(),
        ]);
    }

    let project_at = context
        .plan
        .minecraft_dir
        .join("src")
        .join("main")
        .join("resources")
        .join("META-INF")
        .join("accesstransformer.cfg");
    if project_at.is_file() {
        context.assert_allowed_input(&project_at)?;
        args.extend([
            "--access-transformer".to_string(),
            project_at.display().to_string(),
        ]);
    }

    context.run_java_tool("tool-neoform-runtime", &[], &args, &neoform_root)?;
    if !game_jar.is_file() {
        eyre::bail!(
            "NeoForm Runtime completed without producing {}",
            game_jar.display()
        );
    }

    context.write_node_state(
        "execute-neoform-userdev",
        &["NeoForge userdev", "NeoForm Runtime"],
        &[game_jar, game_sources],
        "complete",
    )
}

fn neoform_dev_compile_jar(context: &ExecutionContext<'_>) -> PathBuf {
    context
        .plan
        .cache_dir
        .join("neoform")
        .join(context.plan.minecraft_version.as_str())
        .join("classes")
        .join("gameJarWithNeoForge.jar")
}

fn required_minecraft_mapping_url<'a>(
    context: &ExecutionContext<'_>,
    url: Option<&'a str>,
    side: &str,
) -> eyre::Result<&'a str> {
    url.ok_or_else(|| {
        eyre::eyre!(
            "Minecraft {} version metadata does not include {side} mappings; this is only supported by the NeoGradle/NeoForm executor.",
            context.plan.minecraft_version
        )
    })
}

fn loader_dev_compile_jar(context: &ExecutionContext<'_>) -> PathBuf {
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        neoform_dev_compile_jar(context)
    } else {
        context
            .plan
            .cache_dir
            .join("forge")
            .join(context.plan.minecraft_version.as_str())
            .join("classes")
            .join("dev-compile.jar")
    }
}

fn write_neoform_artifact_manifest(
    context: &ExecutionContext<'_>,
    output: &Path,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }

    let mut lines = Vec::new();
    for artifact in &context.plan.artifacts {
        let Some(coordinate) = &artifact.coordinate else {
            continue;
        };
        context.assert_allowed_input(&artifact.cache_path)?;
        lines.push(format!(
            "{}={}",
            java_properties_escape(coordinate),
            java_properties_escape(&artifact.cache_path.display().to_string())
        ));
    }
    lines.sort();
    lines.dedup();
    fs::write(output, lines.join("\n"))
        .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    Ok(())
}

fn java_properties_escape(input: &str) -> String {
    let mut output = String::new();
    for character in input.chars() {
        match character {
            '\\' => output.push_str("\\\\"),
            ':' => output.push_str("\\:"),
            '=' => output.push_str("\\="),
            ' ' => output.push_str("\\ "),
            _ => output.push(character),
        }
    }
    output
}

#[expect(
    clippy::too_many_lines,
    reason = "dependency deobf orchestration keeps per-dependency cache and remap behavior visible."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        mc = %context.plan.minecraft_version,
        dependencies = context.plan.dependencies.len(),
    )
)]
fn execute_dependency_deobf(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let output = context.plan.cache_dir.join("dependencies");
    {
        let _span = tracing::debug_span!(
            "dependency_deobf_prepare_output_dir",
            refresh = context.plan.refresh,
            output = %output.display(),
        )
        .entered();
        if context.plan.refresh {
            reset_cache_directory(&context.plan.cache_dir, &output)?;
        } else {
            fs::create_dir_all(&output)?;
            remove_stale_dependency_outputs(&output)?;
        }
    }
    let resolver = {
        let _span = tracing::debug_span!("dependency_deobf_create_resolver").entered();
        Resolver::new(
            context.plan.maven_cache_dir.clone(),
            context.plan.repositories.clone(),
            context.plan.refresh,
            context.plan.allow_local_artifact_cache,
            context.plan.artifact_sources.clone(),
            context.plan.lockfile.clone(),
            context.plan.lockfile.clone(),
            context.cancellation_token.clone(),
        )?
    };
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let _span = tracing::debug_span!("dependency_deobf_copy_neogradle_jars").entered();
        copy_neogradle_dependency_jars(context, &resolver, &output)?;
        return Ok(());
    }

    let (mapping_path, mapping_hash, member_mappings) = {
        let _span = tracing::debug_span!("dependency_deobf_load_mappings").entered();
        let mapping_path = context
            .plan
            .cache_dir
            .join("forge")
            .join(context.plan.minecraft_version.as_str())
            .join("mappings")
            .join("srg_to_official.tsrg");
        if !mapping_path.is_file() {
            eyre::bail!(
                "Dependency deobf requires generated mapping first: {}",
                mapping_path.display()
            );
        }
        let mapping_hash = {
            let _span = tracing::debug_span!(
                "dependency_deobf_hash_mapping",
                mapping = %mapping_path.display()
            )
            .entered();
            ContentHash::from_path(&mapping_path, ContentHashAlgorithm::Blake3)?
        };
        let member_mappings = {
            let _span = tracing::debug_span!(
                "dependency_deobf_read_member_mappings",
                mapping = %mapping_path.display()
            )
            .entered();
            read_unique_srg_member_mappings(&mapping_path)?
        };
        (mapping_path, mapping_hash, member_mappings)
    };
    let outputs: Vec<PathBuf> = {
        let _span = tracing::debug_span!(
            "dependency_deobf_process_dependencies",
            dependency_count = context.plan.dependencies.len(),
            workers = rayon::current_num_threads()
        )
        .entered();
        let mut outputs = context
            .plan
            .dependencies
            .par_iter()
            .enumerate()
            .map(|(dependency_index, dependency)| {
                context.bail_if_cancelled()?;
                let resolver = resolver.clone();
                execute_dependency_deobf_dependency(
                    context,
                    &resolver,
                    dependency_index,
                    dependency,
                    &output,
                    &mapping_path,
                    mapping_hash,
                    &member_mappings,
                )
            })
            .collect::<Vec<eyre::Result<_>>>()
            .into_iter()
            .collect::<eyre::Result<Vec<_>>>()
            .wrap_err("Failed to deobfuscate dependency")?;
        {
            let _span = tracing::debug_span!("dependency_deobf_sort_outputs").entered();
            outputs.sort_by_key(|(dependency_index, _)| *dependency_index);
        };
        outputs.into_iter().map(|(_, output)| output).collect()
    };

    {
        let _span =
            tracing::debug_span!("dependency_deobf_write_node_state", outputs = outputs.len())
                .entered();
        context.write_node_state(
            "deobfuscate-mod-dependencies",
            &["active fg.deobf dependency jars"],
            &outputs,
            "complete",
        )?;
    };
    Ok(())
}

#[expect(
    clippy::too_many_arguments,
    clippy::too_many_lines,
    reason = "Dependency deobfuscation is orchestration-heavy and kept linear for cache/debug tracing."
)]
fn execute_dependency_deobf_dependency(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    dependency_index: usize,
    dependency: &DependencyPlan,
    output: &Path,
    mapping_path: &Path,
    mapping_hash: ContentHash,
    member_mappings: &BTreeMap<String, String>,
) -> eyre::Result<(usize, PathBuf)> {
    let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
    let _dependency_span = tracing::debug_span!(
        "dependency_deobf_dependency",
        index = dependency_index,
        configuration = %dependency.configuration,
        coordinate = %coordinate,
        source = ?dependency.source,
    )
    .entered();
    let artifact = {
        let _span = tracing::debug_span!("dependency_deobf_resolve_artifact").entered();
        resolver.resolve_artifact(
            ArtifactId::from(format!("dependency-{dependency_index}")),
            &coordinate,
            ArtifactPurpose::from(format!("{} dependency", dependency.configuration)),
        )?
    };
    {
        let _span = tracing::debug_span!(
            "dependency_deobf_validate_input",
            artifact = %artifact.cache_path.display()
        )
        .entered();
        context.assert_allowed_input(&artifact.cache_path)?;
    };
    let (remapped, specialsource_output) = {
        let _span = tracing::debug_span!(
            "dependency_deobf_compute_output_paths",
            artifact = %artifact.cache_path.display()
        )
        .entered();
        let artifact_hash = resolved_artifact_hash(&artifact)?;
        (
            remapped_dependency_output_path(output, &artifact_hash, &mapping_hash, &coordinate),
            specialsource_dependency_output_path(
                output,
                &artifact_hash,
                &mapping_hash,
                &coordinate,
            ),
        )
    };
    let cache_hit = {
        let _span = tracing::debug_span!(
            "dependency_deobf_check_remapped_cache",
            output = %remapped.display()
        )
        .entered();
        remapped.is_file()
    };
    if cache_hit {
        tracing::debug!(
            coordinate = %coordinate,
            output = %remapped.display(),
            "dependency_deobf cache hit"
        );
    } else {
        let _output_lock = {
            let _span = tracing::debug_span!(
                "dependency_deobf_acquire_output_lock",
                output = %remapped.display()
            )
            .entered();
            acquire_artifact_path_lock(&remapped)?
        };
        if remapped.is_file() {
            tracing::debug!(
                coordinate = %coordinate,
                output = %remapped.display(),
                "dependency_deobf cache hit"
            );
        } else {
            let _span = info_span!(
                "dependency_deobf cache miss",
                %coordinate,
                output = %remapped.display(),
            )
            .entered();
            let specialsource_cache_hit = {
                let _span = tracing::debug_span!(
                    "dependency_deobf_check_specialsource_cache",
                    output = %specialsource_output.display()
                )
                .entered();
                specialsource_output.is_file()
            };
            if specialsource_cache_hit {
                tracing::debug!(
                    coordinate = %coordinate,
                    output = %specialsource_output.display(),
                    "dependency_deobf specialsource cache hit"
                );
            } else {
                if let Some(parent) = specialsource_output.parent() {
                    let _span = tracing::debug_span!(
                        "dependency_deobf_create_specialsource_output_dir",
                        output = %specialsource_output.display()
                    )
                    .entered();
                    fs::create_dir_all(parent)?;
                }
                {
                    let _span = tracing::debug_span!(
                        "dependency_deobf_run_specialsource",
                        input = %artifact.cache_path.display(),
                        output = %specialsource_output.display(),
                    )
                    .entered();
                    context.run_java_tool_with_classpath(
                        "tool-specialsource",
                        &[],
                        &[],
                        &[
                            "--in-jar".to_string(),
                            artifact.cache_path.display().to_string(),
                            "--out-jar".to_string(),
                            specialsource_output.display().to_string(),
                            "--srg-in".to_string(),
                            mapping_path.display().to_string(),
                            "--live".to_string(),
                        ],
                        &output
                            .join("remap-work")
                            .join(safe_path_segment(&coordinate.file_name())),
                    )?;
                }
            }
            {
                let _span = tracing::debug_span!(
                    "dependency_deobf_rewrite_member_constants",
                    input = %specialsource_output.display(),
                    output = %remapped.display(),
                )
                .entered();
                rewrite_srg_member_constants_in_jar(
                    &specialsource_output,
                    &remapped,
                    member_mappings,
                )?;
            }
        }
    }
    {
        let _span = tracing::debug_span!(
            "dependency_deobf_verify_remapped_output",
            output = %remapped.display()
        )
        .entered();
        if !remapped.is_file() {
            eyre::bail!(
                "SpecialSource completed without producing remapped dependency jar {}",
                remapped.display()
            );
        }
    }
    Ok((dependency_index, remapped))
}

fn copy_neogradle_dependency_jars(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    output: &Path,
) -> eyre::Result<()> {
    let mut outputs = Vec::new();
    for dependency in &context.plan.dependencies {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        let artifact = resolver.resolve_artifact(
            ArtifactId::from(format!("dependency-{}", outputs.len())),
            &coordinate,
            ArtifactPurpose::from(format!("{} dependency", dependency.configuration)),
        )?;
        context.assert_allowed_input(&artifact.cache_path)?;
        let copied =
            copied_neogradle_dependency_output_path(output, &dependency.configuration, &coordinate);
        fs::copy(&artifact.cache_path, &copied).wrap_err_with(|| {
            format!(
                "Failed to copy {} to {}",
                artifact.cache_path.display(),
                copied.display()
            )
        })?;
        outputs.push(copied);
    }
    context.write_node_state(
        "deobfuscate-mod-dependencies",
        &["active NeoGradle dependency jars"],
        &outputs,
        "complete",
    )?;
    Ok(())
}

fn copied_neogradle_dependency_output_path(
    output_dir: &Path,
    configuration: &str,
    coordinate: &MavenCoordinate,
) -> PathBuf {
    output_dir.join(format!(
        "{}-{}",
        safe_path_segment(configuration),
        coordinate.file_name()
    ))
}

fn remapped_dependency_output_path(
    output_dir: &Path,
    input_hash: &ContentHash,
    mapping_hash: &ContentHash,
    coordinate: &MavenCoordinate,
) -> PathBuf {
    output_dir.join(format!(
        "{}-{}-named-mixin-{}",
        input_hash.short_hex(12),
        mapping_hash.short_hex(12),
        coordinate.file_name()
    ))
}

fn specialsource_dependency_output_path(
    output_dir: &Path,
    input_hash: &ContentHash,
    mapping_hash: &ContentHash,
    coordinate: &MavenCoordinate,
) -> PathBuf {
    output_dir.join("specialsource").join(format!(
        "{}-{}-specialsource-{}",
        input_hash.short_hex(12),
        mapping_hash.short_hex(12),
        coordinate.file_name()
    ))
}

fn resolved_artifact_hash(artifact: &ArtifactPlan) -> eyre::Result<ContentHash> {
    artifact.sha1.ok_or_else(|| {
        eyre::eyre!(
            "Resolved artifact {} did not record a content hash",
            artifact.cache_path.display()
        )
    })
}

#[instrument(
    level = "debug",
    skip_all,
    fields(mapping = %mapping_path.display())
)]
fn read_unique_srg_member_mappings(mapping_path: &Path) -> eyre::Result<BTreeMap<String, String>> {
    let content = fs::read_to_string(mapping_path)
        .wrap_err_with(|| format!("Failed to read {}", mapping_path.display()))?;
    let candidates = {
        let _span = tracing::debug_span!(
            "read_unique_srg_member_mappings_parse",
            bytes = content.len()
        )
        .entered();
        content
            .par_lines()
            .filter_map(parse_srg_member_mapping_line)
            .fold(BTreeMap::new, |mut candidates, (srg, named)| {
                insert_unique_member_mapping(&mut candidates, srg, named);
                candidates
            })
            .reduce(BTreeMap::new, merge_unique_member_mapping_candidates)
    };

    Ok(candidates
        .into_iter()
        .filter_map(|(srg, named)| named.map(|named| (srg, named)))
        .collect())
}

fn parse_srg_member_mapping_line(line: &str) -> Option<(&str, &str)> {
    if !line.starts_with('\t') && !line.starts_with(' ') {
        return None;
    }
    if line.starts_with("\t\t") || line.starts_with("  ") {
        return None;
    }
    let parts = line.split_whitespace().collect::<Vec<_>>();
    match parts.as_slice() {
        [srg, named] if is_srg_member_name(srg) => Some((*srg, *named)),
        [srg, _descriptor, named] if is_srg_member_name(srg) => Some((*srg, *named)),
        _ => None,
    }
}

fn insert_unique_member_mapping(
    candidates: &mut BTreeMap<String, Option<String>>,
    srg: &str,
    named: &str,
) {
    match candidates.get_mut(srg) {
        Some(existing) if existing.as_deref() == Some(named) => {}
        Some(existing) => *existing = None,
        None => {
            candidates.insert(srg.to_string(), Some(named.to_string()));
        }
    }
}

fn merge_unique_member_mapping_candidates(
    mut left: BTreeMap<String, Option<String>>,
    right: BTreeMap<String, Option<String>>,
) -> BTreeMap<String, Option<String>> {
    for (srg, right_named) in right {
        match (left.get_mut(&srg), right_named) {
            (None, named) => {
                left.insert(srg, named);
            }
            (Some(left_named), Some(right_named))
                if left_named.as_deref() == Some(right_named.as_str()) => {}
            (Some(left_named), _) => *left_named = None,
        }
    }
    left
}

fn is_srg_member_name(name: &str) -> bool {
    let Some(rest) = name.strip_prefix("f_").or_else(|| name.strip_prefix("m_")) else {
        return false;
    };
    let Some(number) = rest.strip_suffix('_') else {
        return false;
    };
    !number.is_empty() && number.chars().all(|character| character.is_ascii_digit())
}

fn rewrite_srg_member_constants_in_jar(
    input: &Path,
    output: &Path,
    member_mappings: &BTreeMap<String, String>,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let input_bytes =
        fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(input_bytes))
        .wrap_err_with(|| format!("Failed to open {}", input.display()))?;
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    let mut replacements = 0usize;

    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read {} entry #{index}", input.display()))?;
        let name = entry.name().replace('\\', "/");
        if name.ends_with('/') || is_signature_file(&name) {
            continue;
        }
        if !zip_entry_has_extension(&name, "class") {
            writer
                .raw_copy_file_rename(entry, name)
                .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
            continue;
        }
        let mut bytes = Vec::new();
        entry
            .read_to_end(&mut bytes)
            .wrap_err_with(|| format!("Failed to read entry {name} from {}", input.display()))?;
        let (patched, patched_count) = rewrite_class_srg_member_constants(&bytes, member_mappings)
            .wrap_err_with(|| format!("Failed to patch class entry {name}"))?;
        bytes = patched;
        replacements += patched_count;
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    if replacements > 0 {
        tracing::info!(
            "Patched {replacements} SRG member constants in {}",
            output.display()
        );
    }
    Ok(())
}

fn rewrite_class_srg_member_constants(
    bytes: &[u8],
    member_mappings: &BTreeMap<String, String>,
) -> eyre::Result<(Vec<u8>, usize)> {
    if bytes.len() < 10 || bytes.get(..4) != Some(&[0xCA, 0xFE, 0xBA, 0xBE]) {
        return Ok((bytes.to_vec(), 0));
    }

    let constant_pool_count = read_u16(bytes, 8)? as usize;
    let mut output = Vec::with_capacity(bytes.len());
    output.extend_from_slice(&bytes[..10]);
    let mut cursor = 10usize;
    let mut index = 1usize;
    let mut replacements = 0usize;

    while index < constant_pool_count {
        let tag = *bytes
            .get(cursor)
            .ok_or_else(|| eyre::eyre!("Class constant pool is truncated"))?;
        output.push(tag);
        cursor += 1;
        match tag {
            1 => {
                let length = read_u16(bytes, cursor)? as usize;
                let content_start = cursor + 2;
                let content_end = content_start
                    .checked_add(length)
                    .ok_or_else(|| eyre::eyre!("Utf8 constant length overflow"))?;
                if content_end > bytes.len() {
                    eyre::bail!("Utf8 constant extends past end of class file");
                }
                let content = &bytes[content_start..content_end];
                if let Ok(text) = std::str::from_utf8(content)
                    && let Some(replacement) = member_mappings.get(text)
                {
                    let replacement_bytes = replacement.as_bytes();
                    let replacement_len = u16::try_from(replacement_bytes.len())
                        .wrap_err("Replacement member name is too long for classfile UTF8")?;
                    output.extend_from_slice(&replacement_len.to_be_bytes());
                    output.extend_from_slice(replacement_bytes);
                    replacements += 1;
                } else {
                    output.extend_from_slice(&bytes[cursor..content_end]);
                }
                cursor = content_end;
            }
            3 | 4 | 9 | 10 | 11 | 12 | 17 | 18 => {
                copy_constant_pool_payload(bytes, &mut output, &mut cursor, 4)?;
            }
            5 | 6 => {
                copy_constant_pool_payload(bytes, &mut output, &mut cursor, 8)?;
                index += 1;
            }
            7 | 8 | 16 | 19 | 20 => {
                copy_constant_pool_payload(bytes, &mut output, &mut cursor, 2)?;
            }
            15 => {
                copy_constant_pool_payload(bytes, &mut output, &mut cursor, 3)?;
            }
            _ => eyre::bail!("Unsupported class constant pool tag {tag}"),
        }
        index += 1;
    }

    output.extend_from_slice(
        bytes
            .get(cursor..)
            .ok_or_else(|| eyre::eyre!("Class constant pool extends past end of file"))?,
    );
    Ok((output, replacements))
}

fn copy_constant_pool_payload(
    input: &[u8],
    output: &mut Vec<u8>,
    cursor: &mut usize,
    length: usize,
) -> eyre::Result<()> {
    let end = cursor
        .checked_add(length)
        .ok_or_else(|| eyre::eyre!("Constant pool payload length overflow"))?;
    let payload = input
        .get(*cursor..end)
        .ok_or_else(|| eyre::eyre!("Class constant pool extends past end of file"))?;
    output.extend_from_slice(payload);
    *cursor = end;
    Ok(())
}

fn remove_stale_dependency_outputs(output: &Path) -> eyre::Result<()> {
    for entry in
        fs::read_dir(output).wrap_err_with(|| format!("Failed to read {}", output.display()))?
    {
        let entry = entry.wrap_err_with(|| format!("Failed to read {}", output.display()))?;
        let path = entry.path();
        if !path.is_file() || !zip_entry_has_extension(&path.to_string_lossy(), "jar") {
            continue;
        }
        let file_name = path
            .file_name()
            .and_then(std::ffi::OsStr::to_str)
            .map_or("", |name| name);
        if !file_name.contains("-named-mixin-") {
            fs::remove_file(&path).wrap_err_with(|| {
                format!(
                    "Failed to remove stale dependency output {}",
                    path.display()
                )
            })?;
        }
    }
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "project compile orchestration keeps generated sources, resources, and javac inputs visible."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn execute_project_compile(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    let (
        project_root,
        generated_sources,
        classes_dir,
        resources_dir,
        staged_resources_dir,
        gametest_classes_dir,
        gametest_resources_dir,
        datagen_classes_dir,
        datagen_resources_dir,
    ) = {
        let _span = tracing::debug_span!("project_compile_resolve_paths").entered();
        let project_root = context.plan.cache_dir.join("project");
        let generated_sources = project_root
            .join("generated-src")
            .join("antlr")
            .join("main")
            .join("ca")
            .join("teamdman")
            .join("langs");
        let classes_dir = project_root.join("classes");
        let resources_dir = project_root.join("resources");
        let staged_resources_dir = project_root.join("staged-resources");
        let gametest_classes_dir = project_root.join("gametest").join("classes");
        let gametest_resources_dir = project_root.join("gametest").join("resources");
        let datagen_classes_dir = project_root.join("datagen").join("classes");
        let datagen_resources_dir = project_root.join("datagen").join("resources");
        (
            project_root,
            generated_sources,
            classes_dir,
            resources_dir,
            staged_resources_dir,
            gametest_classes_dir,
            gametest_resources_dir,
            datagen_classes_dir,
            datagen_resources_dir,
        )
    };
    tracing::info!(
        classes_dir = %classes_dir.display(),
        resources_dir = %resources_dir.display(),
        gametest_classes_dir = %gametest_classes_dir.display(),
        datagen_classes_dir = %datagen_classes_dir.display(),
        "project_compile_outputs_will_be_recreated"
    );
    {
        let _span = tracing::debug_span!(
            "project_compile_prepare_generated_sources",
            output = %generated_sources.display()
        )
        .entered();
        fs::create_dir_all(&generated_sources)?;
    };
    context.bail_if_cancelled()?;

    let resolver = {
        let _span = tracing::debug_span!("project_compile_create_resolver").entered();
        Resolver::new(
            context.plan.maven_cache_dir.clone(),
            context.plan.repositories.clone(),
            context.plan.refresh,
            context.plan.allow_local_artifact_cache,
            context.plan.artifact_sources.clone(),
            context.plan.lockfile.clone(),
            context.plan.lockfile.clone(),
            context.cancellation_token.clone(),
        )?
    };
    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!("project_compile_write_minecraft_libraries_cfg").entered();
        write_minecraft_libraries_cfg(
            context,
            &resolver.client,
            &project_root.join("minecraft-libraries.cfg"),
        )?;
    };
    context.bail_if_cancelled()?;
    let antlr_classpath = {
        let _span = tracing::debug_span!("project_compile_resolve_antlr_classpath").entered();
        resolve_antlr_classpath(context, &resolver)?
    };
    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!(
            "project_compile_run_antlr",
            generated_sources = %generated_sources.display()
        )
        .entered();
        run_antlr(context, &antlr_classpath, &generated_sources)?;
    };
    context.bail_if_cancelled()?;

    let (classpath, sources) = {
        let resolver = resolver.clone();
        let generated_sources = generated_sources.as_path();
        let (classpath, sources) = rayon::join(
            || {
                let _span =
                    tracing::debug_span!("project_compile_resolve_main_classpath").entered();
                context.bail_if_cancelled()?;
                resolve_project_compile_classpath(context, &resolver, &antlr_classpath)
                    .wrap_err("Failed to resolve project compile classpath")
            },
            || {
                let _span = tracing::debug_span!(
                    "project_compile_collect_main_sources",
                    generated_sources = %generated_sources.display()
                )
                .entered();
                context.bail_if_cancelled()?;
                collect_project_java_sources(context, generated_sources)
                    .wrap_err("Failed to collect project Java sources")
            },
        );
        (classpath?, sources?)
    };
    context.bail_if_cancelled()?;
    let argfile = project_root.join("javac-main.args");
    {
        let _span = tracing::debug_span!(
            "project_compile_write_main_argfile",
            argfile = %argfile.display(),
            sources = sources.len(),
            classpath = classpath.len(),
        )
        .entered();
        write_javac_argfile(context, &argfile, &classpath, &sources, &classes_dir)?;
    };
    context.bail_if_cancelled()?;

    let started = Instant::now();
    tracing::info!(
        "javac main: start sources={} argfile={}",
        sources.len(),
        argfile.display()
    );
    let mut main_fingerprint_paths = classpath.clone();
    main_fingerprint_paths.extend(sources.iter().cloned());
    main_fingerprint_paths.push(argfile.clone());
    context.bail_if_cancelled()?;
    let main_fingerprint = {
        let _span = tracing::debug_span!(
            "project_compile_fingerprint_main",
            inputs = main_fingerprint_paths.len()
        )
        .entered();
        input_fingerprint(
            context,
            "javac-main",
            &main_fingerprint_paths,
            &[
                context.plan.java.version_output.clone(),
                context.plan.java_release.to_string(),
                format!("{:?}", context.plan.loader_toolchain.kind),
            ],
        )?
    };
    context.bail_if_cancelled()?;
    let main_state_path = project_root.join("javac-main.inputs.sha1");
    let main_refmap = resources_dir.join("sfm.refmap.json");
    let main_cache_hit = {
        let _span = tracing::debug_span!(
            "project_compile_check_main_cache",
            state = %main_state_path.display(),
            classes = %classes_dir.display(),
            refmap = %main_refmap.display(),
        )
        .entered();
        cache_state_matches(
            context,
            &main_state_path,
            &main_fingerprint,
            &[&classes_dir],
        )? && (context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev
            || main_refmap.is_file())
    };
    context.bail_if_cancelled()?;
    if main_cache_hit {
        tracing::info!(
            "javac main: reused cached outputs in {} ms",
            started.elapsed().as_millis()
        );
    } else {
        context.bail_if_cancelled()?;
        {
            let _span = tracing::debug_span!(
                "project_compile_reset_main_classes",
                output = %classes_dir.display()
            )
            .entered();
            reset_cache_directory(&context.plan.cache_dir, &classes_dir)?;
        };
        context.bail_if_cancelled()?;
        {
            let _span = tracing::debug_span!(
                "project_compile_reset_javac_resources",
                output = %resources_dir.display()
            )
            .entered();
            reset_cache_directory(&context.plan.cache_dir, &resources_dir)?;
        };
        context.bail_if_cancelled()?;
        let mut command = Command::new(javac_executable(&context.plan.java));
        command.arg(format!("@{}", argfile.display()));
        context.bail_if_cancelled()?;
        let output = {
            let _span = tracing::debug_span!(
                "project_compile_run_javac_main",
                sources = sources.len(),
                argfile = %argfile.display()
            )
            .entered();
            run_command_capture_output(&context.cancellation_token, &mut command, "javac-main")
                .wrap_err("Failed to run javac")?
        };
        {
            let _span = tracing::debug_span!("project_compile_trace_javac_main_output").entered();
            trace_subprocess_bytes(
                context.plan,
                "java-tool",
                "javac-main",
                "stdout",
                &output.stdout,
            );
        };
        context.bail_if_cancelled()?;
        {
            let _span = tracing::debug_span!("project_compile_trace_javac_main_error").entered();
            trace_subprocess_bytes(
                context.plan,
                "java-tool",
                "javac-main",
                "stderr",
                &output.stderr,
            );
        };
        let log_path = project_root.join("javac-main.log");
        {
            let _span = tracing::debug_span!(
                "project_compile_write_javac_main_log",
                log = %log_path.display()
            )
            .entered();
            let mut log = Vec::new();
            log.extend_from_slice(b"--- stdout ---\n");
            log.extend_from_slice(&output.stdout);
            log.extend_from_slice(b"\n--- stderr ---\n");
            log.extend_from_slice(&output.stderr);
            fs::write(&log_path, log)
                .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
        };
        context.bail_if_cancelled()?;
        if output.cancelled {
            eyre::bail!("javac was cancelled by Ctrl+C. See {}", log_path.display());
        }
        if !output.status.success() {
            eyre::bail!(
                "javac failed with {}. See {}",
                output.status,
                log_path.display()
            );
        }
        {
            let _span = tracing::debug_span!(
                "project_compile_write_main_cache_state",
                state = %main_state_path.display()
            )
            .entered();
            write_cache_state(&main_state_path, &main_fingerprint)?;
        };
        context.bail_if_cancelled()?;
        tracing::info!("javac main: done in {} ms", started.elapsed().as_millis());
    }

    context.bail_if_cancelled()?;
    {
        let classes_dir = classes_dir.as_path();
        let gametest_classes_dir = gametest_classes_dir.as_path();
        let gametest_resources_dir = gametest_resources_dir.as_path();
        let main_fingerprint = main_fingerprint.as_str();
        let (gametest_compile, gametest_resources) = rayon::join(
            || {
                let _span = tracing::debug_span!("project_compile_gametest_javac").entered();
                compile_optional_java_source_set(
                    context,
                    "gametest",
                    &classpath,
                    classes_dir,
                    gametest_classes_dir,
                    main_fingerprint,
                )
                .wrap_err("Failed to compile gametest source set")
            },
            || {
                let _span = tracing::debug_span!("project_compile_gametest_resources").entered();
                stage_optional_resource_source_set(
                    context,
                    "gametest",
                    gametest_resources_dir,
                    &["README.md"],
                )
                .wrap_err("Failed to stage gametest resources")
            },
        );
        gametest_compile?;
        gametest_resources?;
    };
    context.bail_if_cancelled()?;
    {
        let classes_dir = classes_dir.as_path();
        let datagen_classes_dir = datagen_classes_dir.as_path();
        let datagen_resources_dir = datagen_resources_dir.as_path();
        let main_fingerprint = main_fingerprint.as_str();
        let (datagen_compile, datagen_resources) = rayon::join(
            || {
                let _span = tracing::debug_span!("project_compile_datagen_javac").entered();
                compile_optional_java_source_set(
                    context,
                    "datagen",
                    &classpath,
                    classes_dir,
                    datagen_classes_dir,
                    main_fingerprint,
                )
                .wrap_err("Failed to compile datagen source set")
            },
            || {
                let _span = tracing::debug_span!("project_compile_datagen_resources").entered();
                stage_optional_resource_source_set(context, "datagen", datagen_resources_dir, &[])
                    .wrap_err("Failed to stage datagen resources")
            },
        );
        datagen_compile?;
        datagen_resources?;
    };
    context.bail_if_cancelled()?;
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let _span = tracing::debug_span!("project_compile_patch_neogradle_debug_names").entered();
        patch_neogradle_anonymous_constructor_debug_names(context, &classes_dir)?;
    } else {
        let _span = tracing::debug_span!("project_compile_ensure_run_refmap_remap").entered();
        ensure_run_refmap_remapping_file(context)?;
    }
    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!(
            "project_compile_stage_main_resources",
            output = %staged_resources_dir.display()
        )
        .entered();
        stage_project_resources(context, &staged_resources_dir, &resources_dir)?;
    };
    context.bail_if_cancelled()?;

    {
        let _span = tracing::debug_span!("project_compile_write_node_state").entered();
        context.write_node_state(
            "compile-project",
            &[
                "src/main/java",
                "src/main/antlr",
                "src/gametest/java",
                "src/datagen/java",
                "src/datagen/resources",
                "mapped Forge/Minecraft jar",
            ],
            &[
                classes_dir,
                resources_dir,
                staged_resources_dir,
                gametest_classes_dir,
                gametest_resources_dir,
                datagen_classes_dir,
                datagen_resources_dir,
                project_root.join("run-refmap-remap.srg"),
            ],
            "complete",
        )?;
    };
    Ok(())
}

fn patch_neogradle_anonymous_constructor_debug_names(
    context: &ExecutionContext<'_>,
    classes_dir: &Path,
) -> eyre::Result<()> {
    let debug_name_patches: &[(&str, &[(&str, &str)])] = &[
        (
            "ca/teamdman/sfm/client/text_styling/ProgramSyntaxHighlightingHelper$1.class",
            &[("arg0", "tokenSource")],
        ),
        (
            "ca/teamdman/sfm/common/containermenu/ManagerContainerMenu$1.class",
            &[
                ("arg0", "container"),
                ("arg1", "slot"),
                ("arg2", "x"),
                ("arg3", "y"),
            ],
        ),
        (
            "ca/teamdman/sfm/common/resourcetype/FluidResourceType$1.class",
            &[("arg0", "size"), ("arg1", "capacity")],
        ),
        (
            "ca/teamdman/sfm/common/resourcetype/ForgeEnergyResourceType$1.class",
            &[("arg0", "capacity")],
        ),
        (
            "ca/teamdman/sfm/common/resourcetype/ItemResourceType$1.class",
            &[("arg0", "size")],
        ),
    ];

    let mut total_replacements = 0usize;
    for (class_name, replacements) in debug_name_patches {
        context.bail_if_cancelled()?;
        let class_path = zip_name_to_path(classes_dir, class_name);
        if !class_path.is_file() {
            continue;
        }
        let mapping = replacements
            .iter()
            .map(|(from, to)| ((*from).to_string(), (*to).to_string()))
            .collect::<BTreeMap<_, _>>();
        let bytes = fs::read(&class_path)
            .wrap_err_with(|| format!("Failed to read {}", class_path.display()))?;
        context.bail_if_cancelled()?;
        let (patched_bytes, replacement_count) =
            rewrite_class_srg_member_constants(&bytes, &mapping).wrap_err_with(|| {
                format!("Failed to patch debug names in {}", class_path.display())
            })?;
        context.bail_if_cancelled()?;
        if replacement_count == 0 {
            continue;
        }
        fs::write(&class_path, patched_bytes)
            .wrap_err_with(|| format!("Failed to write {}", class_path.display()))?;
        total_replacements += replacement_count;
    }

    if total_replacements > 0 {
        tracing::info!("Patched {total_replacements} NeoGradle anonymous constructor debug names");
    }
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "Optional source-set compilation keeps all javac cache, trace, and log handling together."
)]
fn compile_optional_java_source_set(
    context: &ExecutionContext<'_>,
    source_set: &str,
    base_classpath: &[PathBuf],
    main_classes_dir: &Path,
    classes_dir: &Path,
    upstream_fingerprint: &str,
) -> eyre::Result<()> {
    let _source_set_span =
        tracing::debug_span!("compile_optional_java_source_set", source_set).entered();
    context.bail_if_cancelled()?;
    let project_root = context.plan.cache_dir.join("project");
    let source_root = context
        .plan
        .minecraft_dir
        .join("src")
        .join(source_set)
        .join("java");
    if !source_root.exists() {
        reset_cache_directory(&context.plan.cache_dir, classes_dir)?;
        return Ok(());
    }

    let sources = {
        let _span = tracing::debug_span!(
            "compile_optional_collect_sources",
            source_set,
            root = %source_root.display()
        )
        .entered();
        collect_source_set_java_sources(context, source_set)?
    };
    context.bail_if_cancelled()?;
    if sources.is_empty() {
        reset_cache_directory(&context.plan.cache_dir, classes_dir)?;
        return Ok(());
    }

    let classpath = {
        let _span = tracing::debug_span!(
            "compile_optional_build_classpath",
            source_set,
            base_entries = base_classpath.len()
        )
        .entered();
        dedup_paths_preserve_order(
            std::iter::once(main_classes_dir.to_path_buf())
                .chain(base_classpath.iter().cloned())
                .collect(),
        )
    };
    let argfile = project_root.join(format!("javac-{source_set}.args"));
    {
        let _span = tracing::debug_span!(
            "compile_optional_write_argfile",
            source_set,
            argfile = %argfile.display(),
            sources = sources.len(),
            classpath = classpath.len()
        )
        .entered();
        write_javac_no_ap_argfile(context, &argfile, &classpath, &sources, classes_dir)?;
    };
    context.bail_if_cancelled()?;

    let started = Instant::now();
    tracing::info!(
        "javac {source_set}: start sources={} argfile={}",
        sources.len(),
        argfile.display()
    );
    let mut fingerprint_paths = sources.clone();
    fingerprint_paths.push(argfile.clone());
    context.bail_if_cancelled()?;
    let fingerprint = {
        let _span = tracing::debug_span!(
            "compile_optional_fingerprint",
            source_set,
            inputs = fingerprint_paths.len()
        )
        .entered();
        input_fingerprint(
            context,
            &format!("javac-{source_set}"),
            &fingerprint_paths,
            &[
                context.plan.java.version_output.clone(),
                context.plan.java_release.to_string(),
                source_set.to_string(),
                upstream_fingerprint.to_string(),
            ],
        )?
    };
    context.bail_if_cancelled()?;
    let state_path = project_root.join(format!("javac-{source_set}.inputs.sha1"));
    let cache_hit = {
        let _span = tracing::debug_span!(
            "compile_optional_check_cache",
            source_set,
            state = %state_path.display(),
            output = %classes_dir.display()
        )
        .entered();
        cache_state_matches(context, &state_path, &fingerprint, &[classes_dir])?
    };
    if cache_hit {
        tracing::info!(
            "javac {source_set}: reused cached outputs in {} ms",
            started.elapsed().as_millis()
        );
        return Ok(());
    }

    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!(
            "compile_optional_reset_classes",
            source_set,
            output = %classes_dir.display()
        )
        .entered();
        reset_cache_directory(&context.plan.cache_dir, classes_dir)?;
    };
    context.bail_if_cancelled()?;
    let mut command = Command::new(javac_executable(&context.plan.java));
    command.arg(format!("@{}", argfile.display()));
    let source = format!("javac-{source_set}");
    context.bail_if_cancelled()?;
    let output = {
        let _span = tracing::debug_span!(
            "compile_optional_run_javac",
            source_set,
            sources = sources.len(),
            argfile = %argfile.display()
        )
        .entered();
        run_command_capture_output(&context.cancellation_token, &mut command, &source)
            .wrap_err_with(|| format!("Failed to run javac for {source_set}"))?
    };
    context.bail_if_cancelled()?;
    {
        let _span =
            tracing::debug_span!("compile_optional_trace_javac_output", source_set).entered();
        trace_subprocess_bytes(context.plan, "java-tool", &source, "stdout", &output.stdout);
        trace_subprocess_bytes(context.plan, "java-tool", &source, "stderr", &output.stderr);
    };
    context.bail_if_cancelled()?;
    let log_path = project_root.join(format!("javac-{source_set}.log"));
    {
        let _span = tracing::debug_span!(
            "compile_optional_write_javac_log",
            source_set,
            log = %log_path.display()
        )
        .entered();
        let mut log = Vec::new();
        log.extend_from_slice(b"--- stdout ---\n");
        log.extend_from_slice(&output.stdout);
        log.extend_from_slice(b"\n--- stderr ---\n");
        log.extend_from_slice(&output.stderr);
        fs::write(&log_path, log)
            .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
    };
    context.bail_if_cancelled()?;
    if output.cancelled {
        eyre::bail!(
            "javac {source_set} was cancelled by Ctrl+C. See {}",
            log_path.display()
        );
    }
    if !output.status.success() {
        eyre::bail!(
            "javac {source_set} failed with {}. See {}",
            output.status,
            log_path.display()
        );
    }
    {
        let _span = tracing::debug_span!(
            "compile_optional_write_cache_state",
            source_set,
            state = %state_path.display()
        )
        .entered();
        write_cache_state(&state_path, &fingerprint)?;
    };
    context.bail_if_cancelled()?;
    tracing::info!(
        "javac {source_set}: done in {} ms",
        started.elapsed().as_millis()
    );
    Ok(())
}

fn stage_optional_resource_source_set(
    context: &ExecutionContext<'_>,
    source_set: &str,
    output: &Path,
    excludes: &[&str],
) -> eyre::Result<()> {
    let _source_set_span = tracing::debug_span!(
        "stage_optional_resource_source_set",
        source_set,
        output = %output.display()
    )
    .entered();
    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!("stage_optional_resources_reset_output").entered();
        reset_cache_directory(&context.plan.cache_dir, output)?;
    };
    context.bail_if_cancelled()?;
    let root = context
        .plan
        .minecraft_dir
        .join("src")
        .join(source_set)
        .join("resources");
    if !root.exists() {
        return Ok(());
    }
    context.assert_allowed_input(&root)?;

    let files = {
        let _span = tracing::debug_span!(
            "stage_optional_resources_collect_files",
            root = %root.display()
        )
        .entered();
        collect_files_under_cancellable(context, &root)?
    };
    for path in files {
        context.bail_if_cancelled()?;
        context.assert_allowed_input(&path)?;
        let name = relative_zip_name(&root, &path)?;
        if excludes.iter().any(|exclude| *exclude == name) {
            continue;
        }
        let output_path = zip_name_to_path(output, &name);
        if let Some(parent) = output_path.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::copy(&path, &output_path).wrap_err_with(|| {
            format!(
                "Failed to copy {} to {}",
                path.display(),
                output_path.display()
            )
        })?;
        context.bail_if_cancelled()?;
    }
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "packaging executor keeps orchestration visible while toolchain is incomplete"
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        mc = %context.plan.minecraft_version,
        output = %context.plan.rust_output_jar.display(),
    )
)]
fn execute_package_and_reobfuscate(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    if context.plan.rust_output_jar == context.plan.gradle_output_jar {
        eyre::bail!(
            "Refusing to write Rust jar over Gradle jar: {}",
            context.plan.rust_output_jar.display()
        );
    }

    let project_root = context.plan.cache_dir.join("project");
    let classes_dir = project_root.join("classes");
    let javac_resources_dir = project_root.join("resources");
    let staged_resources_dir = project_root.join("staged-resources");
    let development_jar = project_root.join("dev.jar");
    let mixin_reobf_mapping = project_root.join("compileJava-mappings.tsrg");
    let reobf_mapping = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("mappings")
        .join("official_to_srg.tsrg");
    tracing::info!(
        development_jar = %development_jar.display(),
        staged_resources_dir = %staged_resources_dir.display(),
        reobf_mapping = %reobf_mapping.display(),
        "package_inputs_resolved"
    );

    if !classes_dir.is_dir() {
        eyre::bail!(
            "Project classes directory is missing: {}",
            classes_dir.display()
        );
    }
    if context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev
        && !javac_resources_dir.join("sfm.refmap.json").is_file()
    {
        eyre::bail!(
            "Mixin annotation processor did not produce {}",
            javac_resources_dir.join("sfm.refmap.json").display()
        );
    }
    stage_project_resources(context, &staged_resources_dir, &javac_resources_dir)?;
    let mut package_fingerprint_paths = vec![classes_dir.clone(), staged_resources_dir.clone()];
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        package_fingerprint_paths.extend(
            context
                .plan
                .dependencies
                .iter()
                .filter(|dependency| dependency.configuration == "jarJar")
                .map(|dependency| dependency.cache_path.clone()),
        );
    } else {
        package_fingerprint_paths.push(mixin_reobf_mapping.clone());
        package_fingerprint_paths.push(reobf_mapping.clone());
    }
    let package_fingerprint = input_fingerprint(
        context,
        "package-and-reobfuscate-jar",
        &package_fingerprint_paths,
        &package_fingerprint_extras(context),
    )?;
    let package_state_path = project_root.join("package-and-reobfuscate.inputs.sha1");
    let started = Instant::now();
    tracing::info!(
        "package-and-reobfuscate-jar: start output={}",
        context.plan.rust_output_jar.display()
    );
    if cache_state_matches_outputs(
        context,
        &package_state_path,
        &package_fingerprint,
        &[],
        &[&context.plan.rust_output_jar],
    )? {
        tracing::info!(
            "package-and-reobfuscate-jar: reused cached Rust jar in {} ms",
            started.elapsed().as_millis()
        );
        context.write_node_state(
            "package-and-reobfuscate-jar",
            &[
                "compiled classes",
                "expanded resources",
                "reobfuscation mappings",
            ],
            std::slice::from_ref(&context.plan.rust_output_jar),
            "cached",
        )?;
        return Ok(());
    }

    write_project_development_jar(
        context,
        &classes_dir,
        &staged_resources_dir,
        &development_jar,
    )?;

    if let Some(parent) = context.plan.rust_output_jar.parent() {
        fs::create_dir_all(parent)?;
    }
    if context.plan.rust_output_jar.exists() {
        fs::remove_file(&context.plan.rust_output_jar).wrap_err_with(|| {
            format!(
                "Failed to remove previous Rust jar {}",
                context.plan.rust_output_jar.display()
            )
        })?;
    }
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        fs::copy(&development_jar, &context.plan.rust_output_jar).wrap_err_with(|| {
            format!(
                "Failed to copy {} to {}",
                development_jar.display(),
                context.plan.rust_output_jar.display()
            )
        })?;
        context.write_node_state(
            "package-and-reobfuscate-jar",
            &["compiled classes", "expanded resources"],
            &[
                staged_resources_dir,
                development_jar,
                context.plan.rust_output_jar.clone(),
            ],
            "complete",
        )?;
        write_cache_state(&package_state_path, &package_fingerprint)?;
        tracing::info!(
            "package-and-reobfuscate-jar: done in {} ms",
            started.elapsed().as_millis()
        );
        return Ok(());
    }

    if !reobf_mapping.is_file() {
        eyre::bail!(
            "Reobfuscation mapping is missing: {}",
            reobf_mapping.display()
        );
    }
    if !mixin_reobf_mapping.is_file() {
        eyre::bail!(
            "Mixin reobfuscation mapping is missing: {}",
            mixin_reobf_mapping.display()
        );
    }

    let resolver = Resolver::new(
        context.plan.maven_cache_dir.clone(),
        context.plan.repositories.clone(),
        context.plan.refresh,
        context.plan.allow_local_artifact_cache,
        context.plan.artifact_sources.clone(),
        context.plan.lockfile.clone(),
        context.plan.lockfile.clone(),
        context.cancellation_token.clone(),
    )?;
    let antlr_classpath = resolve_antlr_classpath(context, &resolver)?;
    let reobf_classpath = resolve_project_compile_classpath(context, &resolver, &antlr_classpath)?;
    context.run_java_tool_with_classpath(
        "tool-specialsource",
        &[],
        &reobf_classpath,
        &[
            "--in-jar".to_string(),
            development_jar.display().to_string(),
            "--out-jar".to_string(),
            context.plan.rust_output_jar.display().to_string(),
            "--srg-in".to_string(),
            reobf_mapping.display().to_string(),
            "--srg-in".to_string(),
            mixin_reobf_mapping.display().to_string(),
            "--live".to_string(),
        ],
        &project_root.join("reobf"),
    )?;
    if !context.plan.rust_output_jar.is_file() {
        eyre::bail!(
            "SpecialSource completed without producing Rust jar {}",
            context.plan.rust_output_jar.display()
        );
    }

    write_cache_state(&package_state_path, &package_fingerprint)?;
    tracing::info!(
        "package-and-reobfuscate-jar: done in {} ms",
        started.elapsed().as_millis()
    );
    context.write_node_state(
        "package-and-reobfuscate-jar",
        &[
            "compiled classes",
            "expanded resources",
            "reobfuscation mappings",
        ],
        &[
            staged_resources_dir,
            development_jar,
            context.plan.rust_output_jar.clone(),
        ],
        "complete",
    )
}

fn package_fingerprint_extras(context: &ExecutionContext<'_>) -> Vec<String> {
    let mut extras = vec![
        "package-and-reobfuscate-v1".to_string(),
        format!("{:?}", context.plan.loader_toolchain.kind),
        context
            .plan
            .worktree_path
            .file_name()
            .and_then(|name| name.to_str())
            .unwrap_or("sfm")
            .to_string(),
    ];
    extras.extend(
        context
            .plan
            .properties
            .iter()
            .map(|(key, value)| format!("property:{key}={value}")),
    );
    extras.extend(context.plan.artifacts.iter().map(|artifact| {
        format!(
            "artifact:{}:{:?}:{:?}:{:?}",
            artifact.id, artifact.coordinate, artifact.url, artifact.sha1
        )
    }));
    extras.extend(context.plan.dependencies.iter().map(|dependency| {
        format!(
            "dependency:{}:{}:{}",
            dependency.configuration, dependency.notation, dependency.resolved_notation
        )
    }));
    extras
}

fn stage_project_resources(
    context: &ExecutionContext<'_>,
    staging_dir: &Path,
    javac_resources_dir: &Path,
) -> eyre::Result<()> {
    let _stage_span = tracing::debug_span!(
        "stage_project_resources",
        output = %staging_dir.display(),
        javac_resources = %javac_resources_dir.display()
    )
    .entered();
    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!("stage_project_resources_reset_output").entered();
        reset_cache_directory(&context.plan.cache_dir, staging_dir)?;
    };
    let mut written = BTreeSet::new();
    for root in [
        context
            .plan
            .minecraft_dir
            .join("src")
            .join("main")
            .join("resources"),
        context
            .plan
            .minecraft_dir
            .join("src")
            .join("generated")
            .join("resources"),
        javac_resources_dir.to_path_buf(),
    ] {
        context.bail_if_cancelled()?;
        let _span = tracing::debug_span!(
            "stage_project_resource_root",
            root = %root.display(),
            output = %staging_dir.display()
        )
        .entered();
        stage_resource_root(context, &root, staging_dir, &mut written)?;
    }
    Ok(())
}

fn stage_resource_root(
    context: &ExecutionContext<'_>,
    root: &Path,
    staging_dir: &Path,
    written: &mut BTreeSet<String>,
) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    if !root.exists() {
        return Ok(());
    }
    context.assert_allowed_input(root)?;

    let files = {
        let _span = tracing::debug_span!(
            "stage_resource_root_collect_files",
            root = %root.display()
        )
        .entered();
        collect_files_under_cancellable(context, root)?
    };
    for path in files {
        context.bail_if_cancelled()?;
        if path
            .components()
            .any(|component| component.as_os_str() == ".cache")
        {
            continue;
        }
        context.assert_allowed_input(&path)?;
        let name = relative_zip_name(root, &path)?;
        if !written.insert(name.clone()) {
            continue;
        }

        let bytes = if matches!(
            name.as_str(),
            "META-INF/mods.toml" | "META-INF/neoforge.mods.toml" | "pack.mcmeta"
        ) {
            let template_text = fs::read_to_string(&path)
                .wrap_err_with(|| format!("Failed to read resource {}", path.display()))?;
            expand_gradle_resource_template(&template_text, &context.plan.properties)?.into_bytes()
        } else {
            fs::read(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?
        };
        context.bail_if_cancelled()?;
        let output = zip_name_to_path(staging_dir, &name);
        if let Some(parent) = output.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::write(&output, bytes)
            .wrap_err_with(|| format!("Failed to write staged resource {}", output.display()))?;
        context.bail_if_cancelled()?;
    }

    Ok(())
}

fn write_project_development_jar(
    context: &ExecutionContext<'_>,
    classes_dir: &Path,
    resources_dir: &Path,
    output: &Path,
) -> eyre::Result<()> {
    context.assert_allowed_input(classes_dir)?;
    context.assert_allowed_input(resources_dir)?;
    let mut entries = BTreeMap::new();
    add_directory_to_jar_entries(context, &mut entries, classes_dir)?;
    add_directory_to_jar_entries(context, &mut entries, resources_dir)?;
    add_neogradle_jarjar_entries(context, &mut entries)?;

    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);

    writer
        .start_file("META-INF/MANIFEST.MF", options)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;
    writer
        .write_all(build_project_manifest(context).as_bytes())
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;

    for (name, bytes) in entries {
        if name.eq_ignore_ascii_case("META-INF/MANIFEST.MF") {
            continue;
        }
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn add_neogradle_jarjar_entries(
    context: &ExecutionContext<'_>,
    entries: &mut BTreeMap<String, Vec<u8>>,
) -> eyre::Result<()> {
    if context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev {
        return Ok(());
    }

    let mut metadata_entries = Vec::new();
    for dependency in context
        .plan
        .dependencies
        .iter()
        .filter(|dependency| dependency.configuration == "jarJar")
    {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        context.assert_allowed_input(&dependency.cache_path)?;
        let path = format!("META-INF/jarjar/{}", coordinate.file_name());
        let bytes = fs::read(&dependency.cache_path)
            .wrap_err_with(|| format!("Failed to read {}", dependency.cache_path.display()))?;
        entries.insert(path.clone(), bytes);
        metadata_entries.push(JarJarMetadataEntry {
            identifier: JarJarIdentifier {
                group: coordinate.group,
                artifact: coordinate.artifact,
            },
            version: JarJarVersion {
                range: format!("[{}]", coordinate.version),
                artifact_version: coordinate.version,
            },
            path,
            is_obfuscated: false,
        });
    }

    if metadata_entries.is_empty() {
        return Ok(());
    }

    let metadata = JarJarMetadata {
        jars: metadata_entries,
    };
    let mut metadata_json = facet_json::to_string_pretty(&metadata)?.replace('\n', "\r\n");
    metadata_json.push_str("\r\n");
    entries.insert(
        "META-INF/jarjar/metadata.json".to_string(),
        metadata_json.into_bytes(),
    );
    Ok(())
}

fn add_directory_to_jar_entries(
    context: &ExecutionContext<'_>,
    entries: &mut BTreeMap<String, Vec<u8>>,
    root: &Path,
) -> eyre::Result<()> {
    if !root.exists() {
        return Ok(());
    }
    context.assert_allowed_input(root)?;
    for path in collect_files_under(root)? {
        context.assert_allowed_input(&path)?;
        let name = relative_zip_name(root, &path)?;
        if !should_package_project_entry(&name) {
            continue;
        }
        if entries.contains_key(&name) {
            continue;
        }
        let bytes =
            fs::read(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
        entries.insert(name, bytes);
    }
    Ok(())
}

fn should_package_project_entry(name: &str) -> bool {
    !name.eq_ignore_ascii_case(
        "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat",
    )
}

fn build_project_manifest(context: &ExecutionContext<'_>) -> String {
    let properties = &context.plan.properties;
    let mod_id = properties.get("mod_id").map_or("sfm", String::as_str);
    let mod_authors = properties.get("mod_authors").map_or("", String::as_str);
    let project_name = context
        .plan
        .worktree_path
        .file_name()
        .and_then(|name| name.to_str())
        .map_or_else(|| "sfm".to_string(), |version| format!("sfm-{version}"));
    let mod_version = properties.get("mod_version").map_or("", String::as_str);
    let timestamp = Local::now().format("%Y-%m-%dT%H:%M:%S%z").to_string();
    let mut manifest = String::new();
    let attributes = [
        ("Manifest-Version", "1.0"),
        ("Specification-Title", mod_id),
        ("Specification-Vendor", mod_authors),
        ("Specification-Version", "1"),
        ("Implementation-Title", project_name.as_str()),
        ("Implementation-Version", mod_version),
        ("Implementation-Vendor", mod_authors),
        ("Implementation-Timestamp", timestamp.as_str()),
    ];
    for (key, value) in attributes {
        append_manifest_attribute(&mut manifest, key, value);
    }
    if context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev {
        append_manifest_attribute(&mut manifest, "MixinConfigs", "sfm.mixins.json");
    }
    manifest.push_str("\r\n");
    manifest
}

fn append_manifest_attribute(manifest: &mut String, key: &str, value: &str) {
    manifest.push_str(key);
    manifest.push_str(": ");
    manifest.push_str(value);
    manifest.push_str("\r\n");
}

fn expand_gradle_resource_template(
    content: &str,
    properties: &BTreeMap<String, String>,
) -> eyre::Result<String> {
    let mut output = String::new();
    let mut remaining = content;
    while let Some(start) = remaining.find("${") {
        output.push_str(&remaining[..start]);
        let after_start = &remaining[start + 2..];
        let Some(end) = after_start.find('}') else {
            eyre::bail!("Unclosed resource expansion placeholder in {content:?}");
        };
        let key = after_start[..end].trim();
        let value = properties
            .get(key)
            .ok_or_else(|| eyre::eyre!("Missing resource expansion property: {key}"))?;
        output.push_str(&decode_gradle_property_value(value));
        remaining = &after_start[end + 1..];
    }
    output.push_str(remaining);
    Ok(output)
}

fn decode_gradle_property_value(value: &str) -> String {
    let mut output = String::new();
    let mut chars = value.chars();
    while let Some(character) = chars.next() {
        if character != '\\' {
            output.push(character);
            continue;
        }
        match chars.next() {
            Some('n') => output.push('\n'),
            Some('r') => output.push('\r'),
            Some('t') => output.push('\t'),
            Some('\\') | None => output.push('\\'),
            Some(other) => {
                output.push('\\');
                output.push(other);
            }
        }
    }
    output
}

fn reset_cache_directory(cache_dir: &Path, path: &Path) -> eyre::Result<()> {
    let canonical_cache = canonicalize_lenient(cache_dir)?;
    let canonical_path = canonicalize_lenient(path)?;
    if !canonical_path.starts_with(&canonical_cache) {
        eyre::bail!("Refusing to reset non-cache directory {}", path.display());
    }
    if path.exists() {
        fs::remove_dir_all(path)
            .wrap_err_with(|| format!("Failed to remove {}", path.display()))?;
    }
    fs::create_dir_all(path).wrap_err_with(|| format!("Failed to create {}", path.display()))?;
    Ok(())
}

fn cache_state_matches(
    context: &ExecutionContext<'_>,
    state_path: &Path,
    expected_state: &str,
    required_output_dirs: &[&Path],
) -> eyre::Result<bool> {
    cache_state_matches_outputs(
        context,
        state_path,
        expected_state,
        required_output_dirs,
        &[],
    )
}

fn cache_state_matches_outputs(
    context: &ExecutionContext<'_>,
    state_path: &Path,
    expected_state: &str,
    required_output_dirs: &[&Path],
    required_output_files: &[&Path],
) -> eyre::Result<bool> {
    context.bail_if_cancelled()?;
    if context.plan.refresh {
        return Ok(false);
    }
    if fs::read_to_string(state_path).unwrap_or_default() != expected_state {
        return Ok(false);
    }
    for output_dir in required_output_dirs {
        context.bail_if_cancelled()?;
        if !directory_has_files(context, output_dir)? {
            return Ok(false);
        }
    }
    for output_file in required_output_files {
        context.bail_if_cancelled()?;
        if !output_file.is_file() {
            return Ok(false);
        }
    }
    Ok(true)
}

fn directory_has_files(context: &ExecutionContext<'_>, path: &Path) -> eyre::Result<bool> {
    context.bail_if_cancelled()?;
    Ok(path.is_dir() && !collect_files_under_cancellable(context, path)?.is_empty())
}

fn input_fingerprint(
    context: &ExecutionContext<'_>,
    label: &str,
    paths: &[PathBuf],
    extras: &[String],
) -> eyre::Result<String> {
    context.bail_if_cancelled()?;
    let mut input = Vec::new();
    input.extend_from_slice(b"sfm-input-fingerprint-v2\n");
    input.extend_from_slice(label.as_bytes());
    input.extend_from_slice(b"\n");
    for extra in extras {
        context.bail_if_cancelled()?;
        input.extend_from_slice(b"extra:");
        input.extend_from_slice(extra.as_bytes());
        input.extend_from_slice(b"\n");
    }
    let path_inputs = {
        let _span =
            tracing::debug_span!("input_fingerprint_paths", label, paths = paths.len()).entered();
        paths
            .par_iter()
            .map(|path| hash_path_input(context, path))
            .collect::<Vec<eyre::Result<_>>>()
            .into_iter()
            .collect::<eyre::Result<Vec<_>>>()?
    };
    for path_input in path_inputs {
        input.extend_from_slice(&path_input);
    }
    Ok(ContentHash::from_bytes(&input, ContentHashAlgorithm::Blake3).to_string())
}

fn hash_path_input(context: &ExecutionContext<'_>, path: &Path) -> eyre::Result<Vec<u8>> {
    context.bail_if_cancelled()?;
    let _span = tracing::debug_span!("hash_path_input", path = %path.display()).entered();
    context.assert_allowed_input(path)?;
    let normalized = path.to_string_lossy().replace('\\', "/");
    let mut input = Vec::new();
    input.extend_from_slice(b"path:");
    input.extend_from_slice(normalized.as_bytes());
    input.extend_from_slice(b"\n");

    if path.is_file() {
        context.bail_if_cancelled()?;
        let _span = tracing::debug_span!("hash_path_input_file").entered();
        input.extend_from_slice(b"file:");
        input.extend_from_slice(
            ContentHash::from_path(path, ContentHashAlgorithm::Blake3)?
                .to_string()
                .as_bytes(),
        );
        input.extend_from_slice(b"\n");
        return Ok(input);
    }

    if path.is_dir() {
        let files = {
            let _span = tracing::debug_span!("hash_path_input_dir_collect").entered();
            collect_files_under_cancellable(context, path)?
        };
        input.extend_from_slice(b"dir\n");
        let entry_inputs = {
            let _span =
                tracing::debug_span!("hash_path_input_dir_entries", files = files.len()).entered();
            files
                .par_iter()
                .map(|file| hash_directory_entry_input(context, path, file))
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()?
        };
        for entry_input in entry_inputs {
            input.extend_from_slice(&entry_input);
        }
        return Ok(input);
    }

    input.extend_from_slice(b"missing\n");
    Ok(input)
}

fn hash_directory_entry_input(
    context: &ExecutionContext<'_>,
    root: &Path,
    file: &Path,
) -> eyre::Result<Vec<u8>> {
    context.bail_if_cancelled()?;
    let _span =
        tracing::debug_span!("hash_directory_entry_input", file = %file.display()).entered();
    context.assert_allowed_input(file)?;
    let relative = relative_zip_name(root, file)?;
    let hash = ContentHash::from_path(file, ContentHashAlgorithm::Blake3)?;
    let mut input = Vec::new();
    input.extend_from_slice(b"entry:");
    input.extend_from_slice(relative.as_bytes());
    input.extend_from_slice(b":");
    input.extend_from_slice(hash.to_string().as_bytes());
    input.extend_from_slice(b"\n");
    Ok(input)
}

fn write_cache_state(path: &Path, state: &str) -> eyre::Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, state).wrap_err_with(|| format!("Failed to write {}", path.display()))
}

fn collect_files_under(root: &Path) -> eyre::Result<Vec<PathBuf>> {
    let mut files = Vec::new();
    if !root.exists() {
        return Ok(files);
    }
    let mut entries = fs::read_dir(root)
        .wrap_err_with(|| format!("Failed to read {}", root.display()))?
        .collect::<Result<Vec<_>, _>>()
        .wrap_err_with(|| format!("Failed to read {}", root.display()))?;
    entries.sort_by_key(std::fs::DirEntry::path);
    for entry in entries {
        let path = entry.path();
        if path.is_dir() {
            files.extend(collect_files_under(&path)?);
        } else if path.is_file() {
            files.push(path);
        }
    }
    Ok(files)
}

fn collect_files_under_cancellable(
    context: &ExecutionContext<'_>,
    root: &Path,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let mut files = Vec::new();
    if !root.exists() {
        return Ok(files);
    }
    let mut entries = fs::read_dir(root)
        .wrap_err_with(|| format!("Failed to read {}", root.display()))?
        .collect::<Result<Vec<_>, _>>()
        .wrap_err_with(|| format!("Failed to read {}", root.display()))?;
    entries.sort_by_key(std::fs::DirEntry::path);
    for entry in entries {
        context.bail_if_cancelled()?;
        let path = entry.path();
        if path.is_dir() {
            files.extend(collect_files_under_cancellable(context, &path)?);
        } else if path.is_file() {
            files.push(path);
        }
    }
    Ok(files)
}

fn zip_name_to_path(root: &Path, name: &str) -> PathBuf {
    name.split('/')
        .filter(|part| !part.is_empty())
        .fold(root.to_path_buf(), |path, part| path.join(part))
}

fn zip_entry_has_extension(name: &str, extension: &str) -> bool {
    Path::new(name)
        .extension()
        .is_some_and(|actual| actual.eq_ignore_ascii_case(extension))
}

#[expect(
    clippy::needless_pass_by_value,
    reason = "The purpose is cloned once per coordinate while call sites pass freshly built labels."
)]
fn resolve_coordinates_for_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    coordinates: &[&str],
    required_for: ArtifactPurpose,
) -> eyre::Result<Vec<PathBuf>> {
    let mut artifacts = Vec::new();
    for (index, coordinate) in coordinates.iter().copied().enumerate() {
        context.bail_if_cancelled()?;
        let coordinate = MavenCoordinate::parse(coordinate)?;
        artifacts.push((
            ArtifactId::from(format!("classpath-{index}")),
            coordinate,
            required_for.clone(),
        ));
    }
    let _span = tracing::debug_span!(
        "resolve_coordinates_for_classpath",
        coordinates = artifacts.len(),
        required_for = %required_for,
    )
    .entered();
    Ok(resolver
        .resolve_artifacts(artifacts)?
        .into_iter()
        .map(|artifact| artifact.cache_path)
        .collect())
}

fn resolve_antlr_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let dependency_script = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("dependencies")
        .join(context.plan.minecraft_version.as_str())
        .join("dependencies.gradle");
    let dependencies = parse_dependency_script(&dependency_script, &context.plan.properties)?;
    context.bail_if_cancelled()?;
    let antlr_version = dependencies
        .iter()
        .find(|dependency| dependency.configuration == "antlr")
        .map_or("4.9.1", |dependency| dependency.coordinate.version.as_str());
    let coordinates = antlr_classpath_coordinates(antlr_version)?;
    let coordinate_refs = coordinates.iter().map(String::as_str).collect::<Vec<_>>();
    resolve_coordinates_for_classpath(
        context,
        resolver,
        &coordinate_refs,
        ArtifactPurpose::from("ANTLR grammar generation"),
    )
}

fn antlr_classpath_coordinates(version: &str) -> eyre::Result<Vec<String>> {
    match version {
        "4.9.1" => Ok(vec![
            "org.antlr:antlr4:4.9.1".to_string(),
            "org.antlr:antlr-runtime:3.5.2".to_string(),
            "org.antlr:antlr4-runtime:4.9.1".to_string(),
            "org.antlr:ST4:4.3".to_string(),
            "org.abego.treelayout:org.abego.treelayout.core:1.0.3".to_string(),
            "org.glassfish:javax.json:1.0.4".to_string(),
        ]),
        "4.13.1" => Ok(vec![
            "org.antlr:antlr4:4.13.1".to_string(),
            "org.antlr:antlr4-runtime:4.13.1".to_string(),
            "org.antlr:antlr-runtime:3.5.3".to_string(),
            "org.antlr:ST4:4.3.4".to_string(),
            "org.abego.treelayout:org.abego.treelayout.core:1.0.3".to_string(),
            "com.ibm.icu:icu4j:72.1".to_string(),
        ]),
        _ => eyre::bail!("Unsupported ANTLR tool version: {version}"),
    }
}

fn resolve_project_compile_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    antlr_classpath: &[PathBuf],
) -> eyre::Result<Vec<PathBuf>> {
    let _classpath_span = tracing::debug_span!("resolve_project_compile_classpath").entered();
    context.bail_if_cancelled()?;
    let mut classpath = Vec::new();
    {
        let _span = tracing::debug_span!("resolve_project_compile_loader_jar").entered();
        classpath.push(loader_dev_compile_jar(context));
    };
    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!("resolve_project_compile_minecraft_libraries").entered();
        classpath.extend(resolve_current_minecraft_libraries(
            context,
            &resolver.client,
        )?);
    };
    context.bail_if_cancelled()?;
    {
        let _span =
            tracing::debug_span!("resolve_project_compile_forge_userdev_libraries").entered();
        classpath.extend(resolve_forge_userdev_libraries(context, resolver)?);
    };
    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!("resolve_project_compile_declared_dependencies").entered();
        classpath.extend(resolve_compile_dependencies(context, resolver)?);
    };
    context.bail_if_cancelled()?;
    {
        let dependency_deobf_dir = context.plan.cache_dir.join("dependencies");
        let _span = tracing::debug_span!(
            "resolve_project_compile_deobf_dependency_jars",
            root = %dependency_deobf_dir.display()
        )
        .entered();
        classpath.extend(collect_jars(context, &dependency_deobf_dir)?);
    };
    context.bail_if_cancelled()?;
    let annotation_coordinates = PROJECT_COMPILE_ANNOTATION_COORDINATES
        .iter()
        .map(|(_, coordinate)| *coordinate)
        .collect::<Vec<_>>();
    {
        let _span = tracing::debug_span!("resolve_project_compile_annotations").entered();
        classpath.extend(resolve_coordinates_for_classpath(
            context,
            resolver,
            &annotation_coordinates,
            ArtifactPurpose::from("Project compile annotations"),
        )?);
    };
    context.bail_if_cancelled()?;
    {
        let _span =
            tracing::debug_span!("resolve_project_compile_append_antlr_classpath").entered();
        classpath.extend(antlr_classpath.iter().cloned());
    };
    let classpath = {
        let _span = tracing::debug_span!(
            "resolve_project_compile_dedup_classpath",
            entries = classpath.len()
        )
        .entered();
        dedup_paths_preserve_order(classpath)
    };
    Ok(classpath)
}

fn dedup_paths_preserve_order(paths: Vec<PathBuf>) -> Vec<PathBuf> {
    let mut seen = BTreeSet::new();
    let mut deduped = Vec::new();
    for path in paths {
        let key = path
            .to_string_lossy()
            .replace('\\', "/")
            .to_ascii_lowercase();
        if seen.insert(key) {
            deduped.push(path);
        }
    }
    deduped
}

fn safe_path_segment(value: &str) -> String {
    value
        .chars()
        .map(|ch| {
            if ch.is_ascii_alphanumeric() || matches!(ch, '.' | '-' | '_') {
                ch
            } else {
                '_'
            }
        })
        .collect()
}

fn run_antlr(
    context: &ExecutionContext<'_>,
    classpath: &[PathBuf],
    output_dir: &Path,
) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    let grammar_root = context
        .plan
        .minecraft_dir
        .join("src")
        .join("main")
        .join("antlr");
    let grammars = [
        grammar_root.join("sfml").join("SFML.g4"),
        grammar_root.join("toml").join("TomlLexer.g4"),
        grammar_root.join("toml").join("TomlParser.g4"),
    ];
    for grammar in &grammars {
        context.bail_if_cancelled()?;
        context.assert_allowed_input(grammar)?;
    }

    let mut fingerprint_paths = classpath.to_vec();
    fingerprint_paths.extend(grammars.iter().cloned());
    context.bail_if_cancelled()?;
    let fingerprint = input_fingerprint(
        context,
        "antlr-main",
        &fingerprint_paths,
        &[
            context.plan.java.version_output.clone(),
            "-visitor -Xexact-output-dir".to_string(),
        ],
    )?;
    context.bail_if_cancelled()?;
    let state_path = output_dir.with_extension("inputs.sha1");
    let started = Instant::now();
    tracing::info!(
        "ANTLR main: start grammars={} output={}",
        grammars.len(),
        output_dir.display()
    );
    if cache_state_matches(context, &state_path, &fingerprint, &[output_dir])? {
        tracing::info!(
            "ANTLR main: reused cached outputs in {} ms",
            started.elapsed().as_millis()
        );
        return Ok(());
    }

    context.bail_if_cancelled()?;
    reset_cache_directory(&context.plan.cache_dir, output_dir)?;
    context.bail_if_cancelled()?;
    let mut command = Command::new(&context.plan.java.executable);
    command
        .arg("-cp")
        .arg(join_classpath(classpath))
        .arg("org.antlr.v4.Tool")
        .arg("-visitor")
        .arg("-Xexact-output-dir")
        .arg("-o")
        .arg(output_dir)
        .args(grammars);
    context.bail_if_cancelled()?;
    let output = run_command_capture_output(&context.cancellation_token, &mut command, "antlr")
        .wrap_err("Failed to run ANTLR")?;
    context.bail_if_cancelled()?;
    trace_subprocess_bytes(context.plan, "java-tool", "antlr", "stdout", &output.stdout);
    trace_subprocess_bytes(context.plan, "java-tool", "antlr", "stderr", &output.stderr);
    context.bail_if_cancelled()?;
    let log_path = output_dir.parent().unwrap_or(output_dir).join("antlr.log");
    let mut log = Vec::new();
    log.extend_from_slice(b"--- stdout ---\n");
    log.extend_from_slice(&output.stdout);
    log.extend_from_slice(b"\n--- stderr ---\n");
    log.extend_from_slice(&output.stderr);
    fs::write(&log_path, log)
        .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
    context.bail_if_cancelled()?;
    if output.cancelled {
        eyre::bail!("ANTLR was cancelled by Ctrl+C. See {}", log_path.display());
    }
    if !output.status.success() {
        eyre::bail!(
            "ANTLR failed with {}. See {}",
            output.status,
            log_path.display()
        );
    }
    write_cache_state(&state_path, &fingerprint)?;
    context.bail_if_cancelled()?;
    tracing::info!("ANTLR main: done in {} ms", started.elapsed().as_millis());
    Ok(())
}

fn resolve_forge_userdev_libraries(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let config: ForgeUserdevConfig = {
        let _span = tracing::debug_span!("resolve_forge_userdev_libraries_read_config").entered();
        read_zip_json_entry(
            &context
                .artifact(ArtifactId::from("forge-userdev"))?
                .cache_path,
            "config.json",
        )?
    };
    context.bail_if_cancelled()?;
    let mut coordinates = Vec::new();
    coordinates.extend(config.libraries);
    coordinates.extend(config.modules);
    coordinates.sort();
    coordinates.dedup();

    let mut artifacts = Vec::new();
    for (index, coordinate) in coordinates.into_iter().enumerate() {
        context.bail_if_cancelled()?;
        artifacts.push((
            ArtifactId::from(format!("forge-userdev-library-{index}")),
            MavenCoordinate::parse(&coordinate)?,
            ArtifactPurpose::from("Forge userdev compile classpath"),
        ));
    }
    let _span = tracing::debug_span!(
        "resolve_forge_userdev_libraries",
        libraries = artifacts.len()
    )
    .entered();
    Ok(resolver
        .resolve_artifacts(artifacts)?
        .into_iter()
        .map(|artifact| artifact.cache_path)
        .collect())
}

fn resolve_forge_userdev_test_libraries(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let config: ForgeUserdevConfig = {
        let _span =
            tracing::debug_span!("resolve_forge_userdev_test_libraries_read_config").entered();
        read_zip_json_entry(
            &context
                .artifact(ArtifactId::from("forge-userdev"))?
                .cache_path,
            "config.json",
        )?
    };
    context.bail_if_cancelled()?;

    let mut artifacts = Vec::new();
    for (index, coordinate) in config.test_libraries.into_iter().enumerate() {
        context.bail_if_cancelled()?;
        artifacts.push((
            ArtifactId::from(format!("forge-userdev-test-library-{index}")),
            MavenCoordinate::parse(&coordinate)?,
            ArtifactPurpose::from("Forge userdev game-test runtime classpath"),
        ));
    }
    let _span = tracing::debug_span!(
        "resolve_forge_userdev_test_libraries_resolve_artifacts",
        libraries = artifacts.len()
    )
    .entered();
    Ok(resolver
        .resolve_artifacts(artifacts)?
        .into_iter()
        .map(|artifact| artifact.cache_path)
        .collect())
}

fn resolve_compile_dependencies(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let dependency_script = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("dependencies")
        .join(context.plan.minecraft_version.as_str())
        .join("dependencies.gradle");
    let dependencies = parse_dependency_script(&dependency_script, &context.plan.properties)?;
    context.bail_if_cancelled()?;
    let mut artifacts = Vec::new();
    for dependency in dependencies
        .iter()
        .filter(|dependency| {
            !dependency.fg_deobf
                && matches!(
                    dependency.configuration.as_str(),
                    "implementation" | "compileOnly" | "annotationProcessor"
                )
                && (context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev
                    || dependency.configuration == "annotationProcessor")
        })
        .enumerate()
    {
        context.bail_if_cancelled()?;
        let (index, dependency) = dependency;
        artifacts.push((
            ArtifactId::from(format!("compile-dependency-{index}")),
            dependency.coordinate.clone(),
            ArtifactPurpose::from("Project compile classpath"),
        ));
    }
    let _span = tracing::debug_span!(
        "resolve_compile_dependencies",
        dependencies = artifacts.len()
    )
    .entered();
    Ok(resolver
        .resolve_artifacts(artifacts)?
        .into_iter()
        .map(|artifact| artifact.cache_path)
        .collect())
}

fn collect_project_java_sources(
    context: &ExecutionContext<'_>,
    generated_sources: &Path,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let mut sources = collect_source_set_java_sources(context, "main")?;
    context.bail_if_cancelled()?;
    sources.extend(collect_java_sources_under(context, generated_sources)?);
    sources.sort();
    Ok(sources)
}

fn collect_source_set_java_sources(
    context: &ExecutionContext<'_>,
    source_set: &str,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let source_root = context
        .plan
        .minecraft_dir
        .join("src")
        .join(source_set)
        .join("java");
    let excludes = read_source_excludes(context, source_set)?;
    let mut sources = Vec::new();
    for path in collect_java_sources_under(context, &source_root)? {
        context.bail_if_cancelled()?;
        let relative = relative_zip_name(&source_root, &path).unwrap_or_default();
        if !is_excluded_source(&relative, &excludes) {
            sources.push(path);
        }
    }
    sources.sort();
    Ok(sources)
}

fn read_source_excludes(
    context: &ExecutionContext<'_>,
    source_set: &str,
) -> eyre::Result<Vec<String>> {
    context.bail_if_cancelled()?;
    let path = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("source-excludes")
        .join(context.plan.minecraft_version.as_str())
        .join(format!("{source_set}-java.txt"));
    if !path.exists() {
        return Ok(Vec::new());
    }
    let excludes_text =
        fs::read_to_string(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    context.bail_if_cancelled()?;
    Ok(excludes_text
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(|line| line.replace('\\', "/"))
        .collect())
}

fn is_excluded_source(relative: &str, excludes: &[String]) -> bool {
    excludes.iter().any(|exclude| {
        if let Some(prefix) = exclude.strip_suffix("/**") {
            relative.starts_with(prefix)
        } else if Path::new(exclude)
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("java"))
        {
            relative == exclude
        } else {
            relative == exclude || relative.starts_with(&format!("{exclude}/"))
        }
    })
}

fn collect_java_sources_under(
    context: &ExecutionContext<'_>,
    root: &Path,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let mut sources = Vec::new();
    if !root.exists() {
        return Ok(sources);
    }
    for entry in
        fs::read_dir(root).wrap_err_with(|| format!("Failed to read {}", root.display()))?
    {
        context.bail_if_cancelled()?;
        let entry = entry.wrap_err_with(|| format!("Failed to read {}", root.display()))?;
        let path = entry.path();
        if path.is_dir() {
            sources.extend(collect_java_sources_under(context, &path)?);
        } else if path.extension().and_then(|extension| extension.to_str()) == Some("java") {
            sources.push(path);
        }
    }
    Ok(sources)
}

fn collect_jars(context: &ExecutionContext<'_>, root: &Path) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let mut jars = Vec::new();
    if !root.exists() {
        return Ok(jars);
    }
    for entry in
        fs::read_dir(root).wrap_err_with(|| format!("Failed to read {}", root.display()))?
    {
        context.bail_if_cancelled()?;
        let entry = entry.wrap_err_with(|| format!("Failed to read {}", root.display()))?;
        let path = entry.path();
        if path.is_dir() {
            jars.extend(collect_jars(context, &path)?);
        } else if path.extension().and_then(|extension| extension.to_str()) == Some("jar") {
            jars.push(path);
        }
    }
    Ok(jars)
}

fn write_javac_argfile(
    context: &ExecutionContext<'_>,
    argfile: &Path,
    classpath: &[PathBuf],
    sources: &[PathBuf],
    classes_dir: &Path,
) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    let refmap = context
        .plan
        .cache_dir
        .join("project")
        .join("resources")
        .join("sfm.refmap.json");
    let out_tsrg = context
        .plan
        .cache_dir
        .join("project")
        .join("compileJava-mappings.tsrg");
    let reobf_tsrg = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("mappings")
        .join("official_to_srg.tsrg");
    if let Some(parent) = refmap.parent() {
        fs::create_dir_all(parent)?;
    }
    context.bail_if_cancelled()?;

    let mut args = Vec::new();
    args.extend([
        "-encoding".to_string(),
        "UTF-8".to_string(),
        "-g".to_string(),
        "-Xmaxerrs".to_string(),
        "0".to_string(),
        "-d".to_string(),
        classes_dir.display().to_string(),
        "-classpath".to_string(),
        join_classpath(classpath),
        "-sourcepath".to_string(),
        String::new(),
        "-AoutRefMapFile=".to_string() + &refmap.display().to_string(),
    ]);
    append_javac_release_args(
        &mut args,
        context.plan.java_release,
        context.plan.java.major_version,
    );
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        args.extend([
            "-AdefaultObfuscationEnv=named".to_string(),
            "-AdisableTargetValidator=true".to_string(),
        ]);
    } else {
        args.extend([
            "-AoutTsrgFile=".to_string() + &out_tsrg.display().to_string(),
            "-AreobfTsrgFile=".to_string() + &reobf_tsrg.display().to_string(),
            "-AmappingTypes=tsrg".to_string(),
            "-AdefaultObfuscationEnv=searge".to_string(),
        ]);
    }
    for source in sources {
        context.bail_if_cancelled()?;
        args.push(source.display().to_string());
    }

    if let Some(parent) = argfile.parent() {
        fs::create_dir_all(parent)?;
    }
    context.bail_if_cancelled()?;
    fs::write(
        argfile,
        args.into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n"),
    )
    .wrap_err_with(|| format!("Failed to write {}", argfile.display()))?;
    Ok(())
}

fn write_javac_no_ap_argfile(
    context: &ExecutionContext<'_>,
    argfile: &Path,
    classpath: &[PathBuf],
    sources: &[PathBuf],
    classes_dir: &Path,
) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    let mut args = Vec::new();
    args.extend([
        "-encoding".to_string(),
        "UTF-8".to_string(),
        "-g".to_string(),
        "-Xmaxerrs".to_string(),
        "0".to_string(),
        "-proc:none".to_string(),
        "-d".to_string(),
        classes_dir.display().to_string(),
        "-classpath".to_string(),
        join_classpath(classpath),
        "-sourcepath".to_string(),
        String::new(),
    ]);
    append_javac_release_args(
        &mut args,
        context.plan.java_release,
        context.plan.java.major_version,
    );
    for source in sources {
        context.bail_if_cancelled()?;
        args.push(source.display().to_string());
    }

    if let Some(parent) = argfile.parent() {
        fs::create_dir_all(parent)?;
    }
    context.bail_if_cancelled()?;
    fs::write(
        argfile,
        args.into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n"),
    )
    .wrap_err_with(|| format!("Failed to write {}", argfile.display()))?;
    Ok(())
}

fn append_javac_release_args(args: &mut Vec<String>, java_release: u32, java_major_version: u32) {
    if java_major_version != java_release {
        args.extend(["--release".to_string(), java_release.to_string()]);
    }
}

fn join_classpath(classpath: &[PathBuf]) -> String {
    let separator = if cfg!(windows) { ";" } else { ":" };
    classpath
        .iter()
        .map(|path| path.display().to_string())
        .collect::<Vec<_>>()
        .join(separator)
}

fn escape_argfile_arg(arg: String) -> String {
    if arg.is_empty() {
        "\"\"".to_string()
    } else if arg.contains(' ') || arg.contains('(') || arg.contains(')') {
        format!("\"{}\"", arg.replace('\\', "\\\\").replace('"', "\\\""))
    } else {
        arg
    }
}

fn read_main_class(path: &Path) -> eyre::Result<String> {
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", path.display()))?;
    let mut manifest = archive
        .by_name("META-INF/MANIFEST.MF")
        .wrap_err_with(|| format!("Tool jar {} has no manifest", path.display()))?;
    let mut content = String::new();
    manifest
        .read_to_string(&mut content)
        .wrap_err_with(|| format!("Failed to read manifest from {}", path.display()))?;
    manifest_attribute(&content, "Main-Class")
        .ok_or_else(|| eyre::eyre!("Tool jar {} has no Main-Class", path.display()))
}

fn manifest_attribute(content: &str, key: &str) -> Option<String> {
    let mut unfolded: Vec<String> = Vec::new();
    for line in content.lines() {
        if let Some(continuation) = line.strip_prefix(' ') {
            if let Some(last) = unfolded.last_mut() {
                last.push_str(continuation);
            }
        } else {
            unfolded.push(line.to_string());
        }
    }

    let prefix = format!("{key}:");
    unfolded
        .iter()
        .find_map(|line| line.strip_prefix(&prefix).map(str::trim))
        .map(str::to_string)
}

fn extract_zip_entry_to_path(zip_path: &Path, entry_name: &str, output: &Path) -> eyre::Result<()> {
    let bytes =
        fs::read(zip_path).wrap_err_with(|| format!("Failed to read {}", zip_path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", zip_path.display()))?;
    let mut entry = archive
        .by_name(entry_name)
        .wrap_err_with(|| format!("Archive {} missing {entry_name}", zip_path.display()))?;
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    std::io::copy(&mut entry, &mut file)
        .wrap_err_with(|| format!("Failed to extract {entry_name} to {}", output.display()))?;
    Ok(())
}

fn read_zip_entry(zip_path: &Path, entry_name: &str) -> eyre::Result<Vec<u8>> {
    let bytes =
        fs::read(zip_path).wrap_err_with(|| format!("Failed to read {}", zip_path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", zip_path.display()))?;
    let mut entry = archive
        .by_name(entry_name)
        .wrap_err_with(|| format!("Archive {} missing {entry_name}", zip_path.display()))?;
    let mut output = Vec::new();
    entry
        .read_to_end(&mut output)
        .wrap_err_with(|| format!("Failed to read {entry_name} from {}", zip_path.display()))?;
    Ok(output)
}

fn read_tsrg_original_classes(path: &Path) -> eyre::Result<BTreeSet<String>> {
    let content =
        fs::read_to_string(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut classes = BTreeSet::new();
    for line in content.lines() {
        if line.trim().is_empty()
            || line.starts_with('\t')
            || line.starts_with(' ')
            || line.starts_with('#')
            || line.starts_with("tsrg")
        {
            continue;
        }
        if let Some(class_name) = line.split_whitespace().next() {
            classes.insert(format!("{}.class", class_name.replace('.', "/")));
        }
    }
    Ok(classes)
}

fn copy_filtered_jar(
    input: &Path,
    output: &Path,
    allowed_entries: &BTreeSet<String>,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to open jar {}", input.display()))?;
    let file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(file);

    for index in 0..archive.len() {
        let entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read jar entry #{index}"))?;
        let name = entry.name().replace('\\', "/");
        if !allowed_entries.contains(&name) {
            continue;
        }
        writer
            .raw_copy_file_rename(entry, name)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn raw_copy_zip_entry_rename<R, W>(
    archive: &mut ZipArchive<R>,
    writer: &mut ZipWriter<W>,
    source_name: &str,
    output_name: &str,
    output: &Path,
) -> eyre::Result<()>
where
    R: Read + Seek,
    W: Write + Seek,
{
    let entry = archive
        .by_name(source_name)
        .wrap_err_with(|| format!("Failed to read zip entry {source_name}"))?;
    writer
        .raw_copy_file_rename(entry, output_name)
        .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    Ok(())
}

fn write_minecraft_libraries_cfg(
    context: &ExecutionContext<'_>,
    client: &Client,
    output: &Path,
) -> eyre::Result<()> {
    let library_paths = resolve_current_minecraft_libraries(context, client)?;
    let mut lines = library_paths
        .iter()
        .map(|library_path| {
            dunce::canonicalize(library_path)
                .map(|path| format!("-e={}", path.display()))
                .wrap_err_with(|| format!("Failed to canonicalize {}", library_path.display()))
        })
        .collect::<eyre::Result<Vec<_>>>()?;

    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    lines.sort();
    fs::write(output, format!("{}\n", lines.join("\n")))
        .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn resolve_current_minecraft_libraries(
    context: &ExecutionContext<'_>,
    client: &Client,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let mut cached_libraries = {
        let _span =
            tracing::debug_span!("resolve_current_minecraft_libraries_cache_lock").entered();
        context
            .minecraft_libraries_cache
            .lock()
            .map_err(|_poisoned| eyre::eyre!("Minecraft library cache lock poisoned"))?
    };
    if let Some(libraries) = cached_libraries.as_ref() {
        tracing::debug!(
            libraries = libraries.len(),
            "resolve_current_minecraft_libraries cache hit"
        );
        return Ok(libraries.clone());
    }

    let version_json: MinecraftVersionJson = {
        let _span = tracing::debug_span!(
            "resolve_current_minecraft_libraries_read_version_json",
            path = %context.plan.minecraft.version_json.cache_path.display()
        )
        .entered();
        read_json_file(&context.plan.minecraft.version_json.cache_path)?
    };
    let libraries = {
        let _span = tracing::debug_span!("resolve_current_minecraft_libraries_select").entered();
        minecraft_library_jars_from_version_json(
            &context.plan.minecraft_libraries_dir,
            &version_json,
        )
    };

    let library_paths = {
        let _span = tracing::debug_span!(
            "resolve_current_minecraft_libraries_download",
            libraries = libraries.len()
        )
        .entered();
        libraries
            .par_iter()
            .enumerate()
            .map(|(index, library)| {
                context.bail_if_cancelled()?;
                let _span = tracing::debug_span!(
                    "resolve_current_minecraft_library",
                    index,
                    path = %library.path.display(),
                    url = %library.url,
                    has_expected_hash = library.sha1.is_some()
                )
                .entered();
                if let Some(expected_hash) = library.sha1.as_ref() {
                    let _span = tracing::debug_span!(
                        "resolve_current_minecraft_library_download_checked",
                        expected_hash = %expected_hash
                    )
                    .entered();
                    download_to_path_overwrite_with_expected_hash(
                        &context.cancellation_token,
                        client,
                        &library.url,
                        &library.path,
                        false,
                        expected_hash,
                    )?;
                } else {
                    let _span = tracing::debug_span!("resolve_current_minecraft_library_download")
                        .entered();
                    download_to_path(
                        &context.cancellation_token,
                        client,
                        &library.url,
                        &library.path,
                    )?;
                }
                {
                    let _span =
                        tracing::debug_span!("resolve_current_minecraft_library_assert_input")
                            .entered();
                    context.assert_allowed_input(&library.path)?;
                };
                Ok(library.path.clone())
            })
            .collect::<Vec<eyre::Result<_>>>()
            .into_iter()
            .collect::<eyre::Result<Vec<_>>>()?
    };

    let _span = tracing::debug_span!(
        "resolve_current_minecraft_libraries_dedup",
        libraries = library_paths.len()
    )
    .entered();
    let library_paths = dedup_paths_preserve_order(library_paths);
    *cached_libraries = Some(library_paths.clone());
    Ok(library_paths)
}

#[derive(Debug)]
struct MinecraftLibraryJar {
    path: PathBuf,
    url: String,
    sha1: Option<ContentHash>,
}

fn minecraft_library_jars_from_version_json(
    libraries_root: &Path,
    version_json: &MinecraftVersionJson,
) -> Vec<MinecraftLibraryJar> {
    version_json
        .libraries
        .iter()
        .filter_map(|library| {
            let artifact = library.downloads.as_ref()?.artifact.as_ref()?;
            Some(MinecraftLibraryJar {
                path: minecraft_library_path(libraries_root, &artifact.path),
                url: artifact.url.clone(),
                sha1: artifact.sha1,
            })
        })
        .collect()
}

fn minecraft_library_path(libraries_root: &Path, artifact_path: &str) -> PathBuf {
    let mut path = libraries_root.to_path_buf();
    for segment in artifact_path
        .split('/')
        .filter(|segment| !segment.is_empty())
    {
        path.push(segment);
    }
    path
}

fn inject_mcp_sources(mcp_zip: &Path, source_jar: &Path, output: &Path) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let source_bytes = fs::read(source_jar)
        .wrap_err_with(|| format!("Failed to read {}", source_jar.display()))?;
    let mut source_archive = ZipArchive::new(Cursor::new(source_bytes))
        .wrap_err_with(|| format!("Failed to open {}", source_jar.display()))?;
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let mut written = BTreeSet::new();

    for index in 0..source_archive.len() {
        let entry = source_archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read source entry #{index}"))?;
        let name = entry.name().replace('\\', "/");
        if name.ends_with('/') {
            continue;
        }
        writer
            .raw_copy_file_rename(entry, &name)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        written.insert(name);
    }

    let mcp_bytes =
        fs::read(mcp_zip).wrap_err_with(|| format!("Failed to read {}", mcp_zip.display()))?;
    let mut mcp_archive = ZipArchive::new(Cursor::new(mcp_bytes))
        .wrap_err_with(|| format!("Failed to open {}", mcp_zip.display()))?;
    for index in 0..mcp_archive.len() {
        let entry = mcp_archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read MCP entry #{index}"))?;
        let name = entry.name().replace('\\', "/");
        let Some(output_name) = name.strip_prefix("config/inject/") else {
            continue;
        };
        if output_name.is_empty() || output_name.ends_with('/') || written.contains(output_name) {
            continue;
        }
        writer
            .raw_copy_file_rename(entry, output_name)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        written.insert(output_name.to_string());
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn apply_mcp_joined_patches(
    context: &ExecutionContext<'_>,
    mcp_zip: &Path,
    source_jar: &Path,
    output: &Path,
    mcp_root: &Path,
) -> eyre::Result<()> {
    let patch_root = mcp_root.join("patch");
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let rejects = patch_root.join("rejects.zip");
    context.run_java_tool(
        "tool-diffpatch",
        &[],
        &[
            "--patch".to_string(),
            "--mode".to_string(),
            "OFFSET".to_string(),
            "--archive".to_string(),
            "ZIP".to_string(),
            "--archive-rejects".to_string(),
            "ZIP".to_string(),
            "--prefix".to_string(),
            "patches/joined/".to_string(),
            "--output".to_string(),
            output.display().to_string(),
            "--reject".to_string(),
            rejects.display().to_string(),
            source_jar.display().to_string(),
            mcp_zip.display().to_string(),
        ],
        &patch_root,
    )?;
    Ok(())
}

fn merge_zip_archives(inputs: &[PathBuf], output: &Path) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let mut written = BTreeSet::new();

    for input in inputs {
        let bytes =
            fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
        let mut archive = ZipArchive::new(Cursor::new(bytes))
            .wrap_err_with(|| format!("Failed to open {}", input.display()))?;
        for index in 0..archive.len() {
            let entry = archive
                .by_index(index)
                .wrap_err_with(|| format!("Failed to read {} entry #{index}", input.display()))?;
            let name = entry.name().replace('\\', "/");
            if name.ends_with('/')
                || name.eq_ignore_ascii_case("META-INF/MANIFEST.MF")
                || zip_entry_has_extension(&name, "SF")
                || zip_entry_has_extension(&name, "RSA")
                || !written.insert(name.clone())
            {
                continue;
            }
            writer
                .raw_copy_file_rename(entry, name)
                .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        }
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(
        input = %input.display(),
        universal = %forge_universal_jar.display(),
        output = %output.display(),
    )
)]
fn write_run_forge_dev_jar(
    input: &Path,
    forge_universal_jar: &Path,
    output: &Path,
) -> eyre::Result<()> {
    let manifest = forge_runtime_manifest(forge_universal_jar)?;
    write_run_loader_dev_jar(input, &manifest, output)?;
    tracing::info!("Generated Forge userdev runtime jar: {}", output.display());
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(
        input = %input.display(),
        universal = %neoforge_universal_jar.display(),
        output = %output.display(),
    )
)]
fn write_run_neoforge_dev_jar(
    input: &Path,
    neoforge_universal_jar: &Path,
    output: &Path,
) -> eyre::Result<()> {
    let manifest = neoforge_runtime_manifest(neoforge_universal_jar)?;
    write_run_loader_dev_jar(input, &manifest, output)?;
    tracing::info!(
        "Generated NeoForge userdev runtime jar: {}",
        output.display()
    );
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(
        input = %input.display(),
        universal = %neoforge_universal_jar.display(),
        output = %output.display(),
    )
)]
fn write_run_neoforge_minecraft_dev_jar(
    input: &Path,
    neoforge_universal_jar: &Path,
    output: &Path,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }

    let manifest = minecraft_runtime_manifest(input)?;
    let neoforge_entries = zip_entry_names(neoforge_universal_jar)?;
    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes)).wrap_err_with(|| {
        format!(
            "Failed to open NeoForge Minecraft runtime jar {}",
            input.display()
        )
    })?;
    let mut names = BTreeSet::new();
    {
        let _span = tracing::debug_span!(
            "write_run_neoforge_minecraft_dev_jar_scan_entries",
            archive_entries = archive.len()
        )
        .entered();
        for index in 0..archive.len() {
            let entry = archive.by_index(index).wrap_err_with(|| {
                format!("Failed to read NeoForge Minecraft runtime jar entry #{index}")
            })?;
            let name = entry.name().replace('\\', "/");
            if should_keep_split_minecraft_runtime_entry(&name, &neoforge_entries) {
                names.insert(name);
            }
        }
    }

    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    writer
        .start_file("META-INF/MANIFEST.MF", options)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;
    writer
        .write_all(&manifest)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;

    {
        let _span = tracing::debug_span!(
            "write_run_neoforge_minecraft_dev_jar_write_entries",
            entries = names.len(),
            method = "raw_copy"
        )
        .entered();
        for name in names {
            raw_copy_zip_entry_rename(&mut archive, &mut writer, &name, &name, output)?;
        }
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    tracing::info!(
        "Generated NeoForge Minecraft runtime jar: {}",
        output.display()
    );
    Ok(())
}

fn should_keep_split_minecraft_runtime_entry(
    name: &str,
    neoforge_entries: &BTreeSet<String>,
) -> bool {
    if name.ends_with('/')
        || name.eq_ignore_ascii_case("META-INF/MANIFEST.MF")
        || is_signature_file(name)
        || is_neoforge_specific_runtime_entry(name)
    {
        return false;
    }

    if is_neoforge_mod_marker(name) {
        return true;
    }

    if neoforge_entries.contains(name) && zip_entry_has_extension(name, "class") {
        return false;
    }

    true
}

#[instrument(
    level = "debug",
    skip_all,
    fields(input = %input.display(), output = %output.display())
)]
fn write_run_loader_dev_jar(input: &Path, manifest: &[u8], output: &Path) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }

    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes.as_slice()))
        .wrap_err_with(|| format!("Failed to open loader runtime jar {}", input.display()))?;
    let mut names = BTreeSet::new();
    {
        let _span = tracing::debug_span!(
            "write_run_loader_dev_jar_scan_entries",
            archive_entries = archive.len()
        )
        .entered();
        for index in 0..archive.len() {
            let entry = archive
                .by_index(index)
                .wrap_err_with(|| format!("Failed to read loader runtime jar entry #{index}"))?;
            let name = entry.name().replace('\\', "/");
            if !name.ends_with('/')
                && !name.eq_ignore_ascii_case("META-INF/MANIFEST.MF")
                && !is_signature_file(&name)
            {
                names.insert(name);
            }
        }
    }

    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    writer
        .start_file("META-INF/MANIFEST.MF", options)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;
    writer
        .write_all(manifest)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;

    let names = names.into_iter().collect::<Vec<_>>();
    {
        let _span = tracing::debug_span!(
            "write_run_loader_dev_jar_write_entries",
            entries = names.len(),
            method = "raw_copy"
        )
        .entered();
        for name in names {
            let entry = archive
                .by_name(&name)
                .wrap_err_with(|| format!("Failed to read loader runtime jar entry {name}"))?;
            writer
                .raw_copy_file_rename(entry, name)
                .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        }
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

#[instrument(level = "debug", skip_all, fields(path = %path.display()))]
fn zip_entry_names(path: &Path) -> eyre::Result<BTreeSet<String>> {
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open jar {}", path.display()))?;
    let mut names = BTreeSet::new();
    {
        let _span =
            tracing::debug_span!("zip_entry_names_scan", archive_entries = archive.len()).entered();
        for index in 0..archive.len() {
            let entry = archive
                .by_index(index)
                .wrap_err_with(|| format!("Failed to read {} entry #{index}", path.display()))?;
            let name = entry.name().replace('\\', "/");
            if !name.ends_with('/') {
                names.insert(name);
            }
        }
    }
    Ok(names)
}

#[instrument(
    level = "debug",
    skip_all,
    fields(universal = %forge_universal_jar.display())
)]
fn forge_runtime_manifest(forge_universal_jar: &Path) -> eyre::Result<Vec<u8>> {
    let manifest = read_zip_entry(forge_universal_jar, "META-INF/MANIFEST.MF")?;
    let manifest = String::from_utf8(manifest).wrap_err_with(|| {
        format!(
            "Forge universal manifest was not UTF-8: {}",
            forge_universal_jar.display()
        )
    })?;
    let normalized = manifest.replace("\r\n", "\n");
    let sections = normalized.split("\n\n").collect::<Vec<_>>();
    let main_section = sections
        .first()
        .copied()
        .ok_or_else(|| eyre::eyre!("Forge universal manifest was empty"))?;
    let required_sections = [
        "net/minecraftforge/fml/loading/",
        "net/minecraftforge/versions/forge/",
        "net/minecraftforge/versions/mcp/",
    ];
    let mut output_sections = vec![strip_manifest_digests(main_section)];

    for required in required_sections {
        let Some(section) = sections
            .iter()
            .copied()
            .find(|section| manifest_section_name(section) == Some(required))
        else {
            if manifest_attribute(&manifest, "FML-System-Mods").as_deref() == Some("forge") {
                let output_sections = sections
                    .iter()
                    .copied()
                    .map(strip_manifest_digests)
                    .filter(|section| !section.trim().is_empty())
                    .collect::<Vec<_>>();
                return Ok(format!("{}\r\n\r\n", output_sections.join("\r\n\r\n")).into_bytes());
            }
            eyre::bail!(
                "Forge universal manifest {} did not contain package section {required}",
                forge_universal_jar.display()
            );
        };
        output_sections.push(strip_manifest_digests(section));
    }

    Ok(format!("{}\r\n\r\n", output_sections.join("\r\n\r\n")).into_bytes())
}

#[instrument(level = "debug", skip_all, fields(input = %input.display()))]
fn minecraft_runtime_manifest(input: &Path) -> eyre::Result<Vec<u8>> {
    let manifest = match read_zip_entry(input, "META-INF/MANIFEST.MF") {
        Ok(manifest) => String::from_utf8(manifest).wrap_err_with(|| {
            format!(
                "Minecraft runtime manifest was not UTF-8: {}",
                input.display()
            )
        })?,
        Err(_) => "Manifest-Version: 1.0\n".to_string(),
    };
    let normalized = manifest.replace("\r\n", "\n");
    let sections = normalized
        .split("\n\n")
        .map(strip_manifest_digests)
        .map(|section| strip_manifest_attribute(&section, "FML-System-Mods"))
        .filter(|section| !section.trim().is_empty())
        .collect::<Vec<_>>();
    Ok(format!("{}\r\n\r\n", sections.join("\r\n\r\n")).into_bytes())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(universal = %neoforge_universal_jar.display())
)]
fn neoforge_runtime_manifest(neoforge_universal_jar: &Path) -> eyre::Result<Vec<u8>> {
    let manifest = read_zip_entry(neoforge_universal_jar, "META-INF/MANIFEST.MF")?;
    let manifest = String::from_utf8(manifest).wrap_err_with(|| {
        format!(
            "NeoForge universal manifest was not UTF-8: {}",
            neoforge_universal_jar.display()
        )
    })?;
    if manifest_attribute(&manifest, "FML-System-Mods").as_deref() != Some("neoforge") {
        eyre::bail!(
            "NeoForge universal manifest {} did not declare FML-System-Mods: neoforge",
            neoforge_universal_jar.display()
        );
    }
    let normalized = manifest.replace("\r\n", "\n");
    let output_sections = normalized
        .split("\n\n")
        .map(strip_manifest_digests)
        .filter(|section| !section.trim().is_empty())
        .collect::<Vec<_>>();

    Ok(format!("{}\r\n\r\n", output_sections.join("\r\n\r\n")).into_bytes())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(universal = %neoforge_universal_jar.display())
)]
fn neoforge_requires_split_runtime(neoforge_universal_jar: &Path) -> eyre::Result<bool> {
    let manifest = read_zip_entry(neoforge_universal_jar, "META-INF/MANIFEST.MF")?;
    let manifest = String::from_utf8(manifest).wrap_err_with(|| {
        format!(
            "NeoForge universal manifest was not UTF-8: {}",
            neoforge_universal_jar.display()
        )
    })?;
    Ok(
        manifest_attribute(&manifest, "FML-System-Mods").as_deref() == Some("neoforge")
            && !manifest.contains("\nName: net/neoforged/neoforge/versions/neoform/"),
    )
}

fn manifest_section_name(section: &str) -> Option<&str> {
    section.lines().next()?.strip_prefix("Name: ")
}

fn strip_manifest_digests(section: &str) -> String {
    let mut output = Vec::new();
    let mut dropping_attribute = false;
    for line in section.lines() {
        if line.starts_with(' ') {
            if !dropping_attribute {
                output.push(line);
            }
            continue;
        }
        dropping_attribute = line.contains("-Digest:");
        if !dropping_attribute {
            output.push(line);
        }
    }
    output.join("\r\n")
}

fn strip_manifest_attribute(section: &str, attribute: &str) -> String {
    let mut output = Vec::new();
    let mut dropping_attribute = false;
    let prefix = format!("{attribute}:");
    for line in section.lines() {
        if line.starts_with(' ') {
            if !dropping_attribute {
                output.push(line);
            }
            continue;
        }
        dropping_attribute = line.starts_with(&prefix);
        if !dropping_attribute {
            output.push(line);
        }
    }
    output.join("\r\n")
}

fn is_neoforge_specific_runtime_entry(name: &str) -> bool {
    name.starts_with("net/neoforged/neoforge/")
        || name.starts_with("META-INF/services/")
        || name.eq_ignore_ascii_case("META-INF/neoforged.mods.toml")
        || name.eq_ignore_ascii_case("META-INF/mods.toml")
        || name.starts_with("data/neoforge/")
        || name.starts_with("assets/neoforge/")
}

fn is_neoforge_mod_marker(name: &str) -> bool {
    name.eq_ignore_ascii_case("META-INF/neoforge.mods.toml")
}

#[instrument(
    level = "debug",
    skip_all,
    fields(client_jar = %client_jar.display(), output = %output.display())
)]
fn write_client_extra_jar(client_jar: &Path, output: &Path) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }

    let bytes = fs::read(client_jar)
        .wrap_err_with(|| format!("Failed to read {}", client_jar.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open client jar {}", client_jar.display()))?;
    let mut names = BTreeSet::new();
    {
        let _span = tracing::debug_span!(
            "write_client_extra_jar_scan_entries",
            archive_entries = archive.len()
        )
        .entered();
        for index in 0..archive.len() {
            let entry = archive
                .by_index(index)
                .wrap_err_with(|| format!("Failed to read client jar entry #{index}"))?;
            let name = entry.name().replace('\\', "/");
            if is_client_extra_entry(&name) {
                names.insert(name);
            }
        }
    }

    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    writer
        .start_file("META-INF/MANIFEST.MF", options)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;
    writer
        .write_all(b"Manifest-Version: 1.0\r\nMinecraft-Dists: server client\r\n\r\n")
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;

    {
        let _span = tracing::debug_span!(
            "write_client_extra_jar_write_entries",
            entries = names.len(),
            method = "raw_copy"
        )
        .entered();
        for name in names {
            raw_copy_zip_entry_rename(&mut archive, &mut writer, &name, &name, output)?;
        }
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    tracing::info!("Generated client-extra jar: {}", output.display());
    Ok(())
}

fn is_client_extra_entry(name: &str) -> bool {
    !name.ends_with('/')
        && !zip_entry_has_extension(name, "class")
        && !name.eq_ignore_ascii_case("META-INF/MANIFEST.MF")
        && !is_signature_file(name)
}

fn is_signature_file(name: &str) -> bool {
    name.starts_with("META-INF/")
        && (zip_entry_has_extension(name, "SF")
            || zip_entry_has_extension(name, "RSA")
            || zip_entry_has_extension(name, "EC")
            || zip_entry_has_extension(name, "DSA"))
}

fn patch_inner_class_access_in_jar(
    input: &Path,
    output: &Path,
    outer_class: &str,
    inner_class: &str,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open jar {}", input.display()))?;
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);

    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read {} entry #{index}", input.display()))?;
        let name = entry.name().replace('\\', "/");
        if name.ends_with('/') {
            continue;
        }
        if !zip_entry_has_extension(&name, "class") {
            writer
                .raw_copy_file_rename(entry, name)
                .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
            continue;
        }
        let mut entry_bytes = Vec::new();
        entry
            .read_to_end(&mut entry_bytes)
            .wrap_err_with(|| format!("Failed to read entry {name} from {}", input.display()))?;
        patch_inner_class_access_in_class_file(&mut entry_bytes, outer_class, inner_class)
            .wrap_err_with(|| format!("Failed to patch class access in {name}"))?;
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&entry_bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn patch_inner_class_access_in_class_file(
    bytes: &mut [u8],
    outer_class: &str,
    inner_class: &str,
) -> eyre::Result<bool> {
    const ACC_PUBLIC: u16 = 0x0001;
    const ACC_PRIVATE: u16 = 0x0002;
    const ACC_PROTECTED: u16 = 0x0004;

    if bytes.len() < 10 || read_u32(bytes, 0)? != 0xCAFE_BABE {
        return Ok(false);
    }

    let parsed = parse_class_constant_pool(bytes)?;
    let mut changed = false;
    let access_flags_offset = parsed.after_constant_pool;
    let this_class = read_u16(bytes, access_flags_offset + 2)?;
    if parsed.class_name(this_class) == Some(inner_class) {
        let flags = read_u16(bytes, access_flags_offset)?;
        let patched = (flags | ACC_PUBLIC) & !ACC_PRIVATE & !ACC_PROTECTED;
        if patched != flags {
            write_u16(bytes, access_flags_offset, patched)?;
            changed = true;
        }
    }

    let mut cursor = parsed.after_constant_pool + 6;
    let interfaces_count = read_u16(bytes, cursor)? as usize;
    cursor += 2 + interfaces_count * 2;
    cursor = skip_class_members(bytes, cursor)?;
    cursor = skip_class_members(bytes, cursor)?;

    let attributes_count = read_u16(bytes, cursor)? as usize;
    cursor += 2;
    for _ in 0..attributes_count {
        let attribute_name_index = read_u16(bytes, cursor)?;
        let attribute_length = read_u32(bytes, cursor + 2)? as usize;
        let attribute_info = cursor + 6;
        let attribute_end = attribute_info
            .checked_add(attribute_length)
            .ok_or_else(|| eyre::eyre!("Class attribute length overflow"))?;
        if attribute_end > bytes.len() {
            eyre::bail!("Class attribute extends past end of file");
        }

        if parsed.utf8(attribute_name_index) == Some("InnerClasses") {
            let inner_classes_count = read_u16(bytes, attribute_info)? as usize;
            let mut inner_cursor = attribute_info + 2;
            for _ in 0..inner_classes_count {
                let inner_class_index = read_u16(bytes, inner_cursor)?;
                let outer_class_index = read_u16(bytes, inner_cursor + 2)?;
                let access_offset = inner_cursor + 6;
                let inner_name_matches = parsed.class_name(inner_class_index) == Some(inner_class);
                let outer_name_matches = outer_class_index == 0
                    || parsed.class_name(outer_class_index) == Some(outer_class);
                if inner_name_matches && outer_name_matches {
                    let flags = read_u16(bytes, access_offset)?;
                    let patched = (flags | ACC_PUBLIC) & !ACC_PRIVATE & !ACC_PROTECTED;
                    if patched != flags {
                        write_u16(bytes, access_offset, patched)?;
                        changed = true;
                    }
                }
                inner_cursor += 8;
            }
        }
        cursor = attribute_end;
    }

    Ok(changed)
}

#[derive(Debug)]
struct ParsedClassConstantPool {
    entries: Vec<ClassConstant>,
    after_constant_pool: usize,
}

impl ParsedClassConstantPool {
    fn utf8(&self, index: u16) -> Option<&str> {
        match self.entries.get(index as usize)? {
            ClassConstant::Utf8(value) => Some(value.as_str()),
            ClassConstant::Other | ClassConstant::Class { .. } => None,
        }
    }

    fn class_name(&self, index: u16) -> Option<&str> {
        let ClassConstant::Class { name_index } = self.entries.get(index as usize)? else {
            return None;
        };
        self.utf8(*name_index)
    }
}

#[derive(Debug)]
enum ClassConstant {
    Utf8(String),
    Class { name_index: u16 },
    Other,
}

fn parse_class_constant_pool(bytes: &[u8]) -> eyre::Result<ParsedClassConstantPool> {
    let constant_pool_count = read_u16(bytes, 8)? as usize;
    let mut entries = Vec::with_capacity(constant_pool_count);
    entries.push(ClassConstant::Other);
    let mut cursor = 10;
    let mut index = 1;

    while index < constant_pool_count {
        let tag = *bytes
            .get(cursor)
            .ok_or_else(|| eyre::eyre!("Class constant pool is truncated"))?;
        cursor += 1;
        match tag {
            1 => {
                let length = read_u16(bytes, cursor)? as usize;
                cursor += 2;
                let end = cursor
                    .checked_add(length)
                    .ok_or_else(|| eyre::eyre!("Utf8 constant length overflow"))?;
                if end > bytes.len() {
                    eyre::bail!("Utf8 constant extends past end of class file");
                }
                let value = String::from_utf8_lossy(&bytes[cursor..end]).into_owned();
                entries.push(ClassConstant::Utf8(value));
                cursor = end;
            }
            3 | 4 | 9 | 10 | 11 | 12 | 17 | 18 => {
                cursor += 4;
                entries.push(ClassConstant::Other);
            }
            5 | 6 => {
                cursor += 8;
                entries.push(ClassConstant::Other);
                entries.push(ClassConstant::Other);
                index += 1;
            }
            7 => {
                let name_index = read_u16(bytes, cursor)?;
                cursor += 2;
                entries.push(ClassConstant::Class { name_index });
            }
            8 | 16 | 19 | 20 => {
                cursor += 2;
                entries.push(ClassConstant::Other);
            }
            15 => {
                cursor += 3;
                entries.push(ClassConstant::Other);
            }
            _ => eyre::bail!("Unsupported class constant pool tag {tag}"),
        }
        if cursor > bytes.len() {
            eyre::bail!("Class constant pool extends past end of file");
        }
        index += 1;
    }

    Ok(ParsedClassConstantPool {
        entries,
        after_constant_pool: cursor,
    })
}

fn skip_class_members(bytes: &[u8], mut cursor: usize) -> eyre::Result<usize> {
    let member_count = read_u16(bytes, cursor)? as usize;
    cursor += 2;
    for _ in 0..member_count {
        cursor += 6;
        let attributes_count = read_u16(bytes, cursor)? as usize;
        cursor += 2;
        for _ in 0..attributes_count {
            let attribute_length = read_u32(bytes, cursor + 2)? as usize;
            cursor = cursor
                .checked_add(6)
                .and_then(|value| value.checked_add(attribute_length))
                .ok_or_else(|| eyre::eyre!("Member attribute length overflow"))?;
            if cursor > bytes.len() {
                eyre::bail!("Member attribute extends past end of class file");
            }
        }
    }
    Ok(cursor)
}

fn read_u16(bytes: &[u8], offset: usize) -> eyre::Result<u16> {
    let value = bytes
        .get(offset..offset + 2)
        .ok_or_else(|| eyre::eyre!("Class file ended before u16 at offset {offset}"))?;
    Ok(u16::from_be_bytes([value[0], value[1]]))
}

fn write_u16(bytes: &mut [u8], offset: usize, value: u16) -> eyre::Result<()> {
    let destination = bytes
        .get_mut(offset..offset + 2)
        .ok_or_else(|| eyre::eyre!("Class file ended before u16 at offset {offset}"))?;
    destination.copy_from_slice(&value.to_be_bytes());
    Ok(())
}

fn read_u32(bytes: &[u8], offset: usize) -> eyre::Result<u32> {
    let value = bytes
        .get(offset..offset + 4)
        .ok_or_else(|| eyre::eyre!("Class file ended before u32 at offset {offset}"))?;
    Ok(u32::from_be_bytes([value[0], value[1], value[2], value[3]]))
}

#[derive(Debug)]
struct MojangClassMapping {
    official_slash: String,
    obf: String,
    fields: BTreeMap<String, String>,
    methods: BTreeMap<(String, String), MojangMethodMapping>,
}

#[derive(Clone, Debug)]
struct MojangMethodMapping {
    official_name: String,
    official_descriptor: String,
}

#[derive(Debug)]
struct SrgClassMapping {
    fields: BTreeMap<String, String>,
    methods: BTreeMap<(String, String), SrgMethodMapping>,
}

#[derive(Debug)]
struct SrgMethodMapping {
    srg_name: String,
    parameters: BTreeMap<usize, String>,
    is_static: bool,
}

type ParchmentParameters = BTreeMap<String, BTreeMap<(String, String), BTreeMap<usize, String>>>;

#[derive(Debug, Facet)]
struct ParchmentData {
    #[facet(default)]
    classes: Vec<ParchmentClass>,
}

#[derive(Debug, Facet)]
struct ParchmentClass {
    name: String,
    #[facet(default)]
    methods: Vec<ParchmentMethod>,
}

#[derive(Debug, Facet)]
struct ParchmentMethod {
    name: String,
    descriptor: String,
    #[facet(default)]
    parameters: Vec<ParchmentParameter>,
}

#[derive(Debug, Facet)]
struct ParchmentParameter {
    index: usize,
    name: String,
}

fn generate_mojang_tsrg_mappings(
    merged_mcp_mappings: &Path,
    mojang_mapping_paths: &[PathBuf],
    parchment_parameters: Option<&ParchmentParameters>,
    obf_to_official_output: &Path,
    srg_to_official_output: &Path,
    official_to_srg_output: &Path,
) -> eyre::Result<()> {
    let mojang = read_mojang_mappings(mojang_mapping_paths)?;
    let srg = read_srg_member_mappings(merged_mcp_mappings)?;

    let mut obf_to_official = String::from("tsrg2 left right\n");
    let mut srg_to_official = String::from("tsrg2 left right\n");
    let mut official_to_srg = String::from("tsrg2 left right\n");

    for class in mojang.values() {
        writeln!(obf_to_official, "{} {}", class.obf, class.official_slash)?;
        writeln!(
            srg_to_official,
            "{} {}",
            class.official_slash, class.official_slash
        )?;
        writeln!(
            official_to_srg,
            "{} {}",
            class.official_slash, class.official_slash
        )?;

        let srg_class = srg.get(&class.official_slash);
        for (obf_name, official_name) in &class.fields {
            writeln!(obf_to_official, "\t{obf_name} {official_name}")?;
            if let Some(srg_name) = srg_class.and_then(|mapping| mapping.fields.get(obf_name)) {
                writeln!(srg_to_official, "\t{srg_name} {official_name}")?;
                writeln!(official_to_srg, "\t{official_name} {srg_name}")?;
            }
        }

        for ((obf_name, obf_descriptor), method) in &class.methods {
            writeln!(
                obf_to_official,
                "\t{} {} {}",
                obf_name, obf_descriptor, method.official_name
            )?;
            let candidate_descriptors =
                [obf_descriptor.clone(), method.official_descriptor.clone()];
            if let Some(srg_method) = srg_class.and_then(|mapping| {
                find_srg_method_mapping(
                    mapping,
                    obf_name,
                    &method.official_name,
                    &candidate_descriptors,
                )
            }) {
                writeln!(
                    srg_to_official,
                    "\t{} {} {}",
                    srg_method.srg_name, method.official_descriptor, method.official_name
                )?;
                write_parameter_mappings(
                    &mut srg_to_official,
                    &class.official_slash,
                    method,
                    srg_method,
                    parchment_parameters,
                    ParameterDirection::SrgToOfficial,
                )?;
                writeln!(
                    official_to_srg,
                    "\t{} {} {}",
                    method.official_name, method.official_descriptor, srg_method.srg_name
                )?;
                write_parameter_mappings(
                    &mut official_to_srg,
                    &class.official_slash,
                    method,
                    srg_method,
                    parchment_parameters,
                    ParameterDirection::OfficialToSrg,
                )?;
            }
        }
    }

    if let Some(parent) = obf_to_official_output.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(obf_to_official_output, obf_to_official)
        .wrap_err_with(|| format!("Failed to write {}", obf_to_official_output.display()))?;
    fs::write(srg_to_official_output, srg_to_official)
        .wrap_err_with(|| format!("Failed to write {}", srg_to_official_output.display()))?;
    fs::write(official_to_srg_output, official_to_srg)
        .wrap_err_with(|| format!("Failed to write {}", official_to_srg_output.display()))?;
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(input = %srg_to_named.display(), output = %output.display())
)]
fn write_runtime_mcp_csv_mappings(srg_to_named: &Path, output: &Path) -> eyre::Result<()> {
    let mapping_text = fs::read_to_string(srg_to_named)
        .wrap_err_with(|| format!("Failed to read {}", srg_to_named.display()))?;
    let mut fields = String::from("searge,name,desc\n");
    let mut methods = String::from("searge,name,desc\n");

    {
        let _span = tracing::debug_span!(
            "write_runtime_mcp_csv_mappings_parse",
            bytes = mapping_text.len()
        )
        .entered();
        for line in mapping_text.lines() {
            if line.trim().is_empty() || line.starts_with("tsrg") {
                continue;
            }
            if !line.starts_with('\t') && !line.starts_with(' ') {
                continue;
            }
            if line.starts_with("\t\t") || line.starts_with("  ") {
                continue;
            }

            let parts = line.split_whitespace().collect::<Vec<_>>();
            match parts.as_slice() {
                [srg, named] => {
                    if srg.starts_with("f_") && *srg != *named {
                        writeln!(fields, "{srg},{named},")?;
                    }
                }
                [srg, _descriptor, named] if srg.starts_with("m_") && *srg != *named => {
                    writeln!(methods, "{srg},{named},")?;
                }
                _ => {}
            }
        }
    }

    {
        let _span = tracing::debug_span!("write_runtime_mcp_csv_mappings_write").entered();
        fs::create_dir_all(output)?;
        let fields_path = output.join("fields.csv");
        let methods_path = output.join("methods.csv");
        fs::write(&fields_path, fields)
            .wrap_err_with(|| format!("Failed to write {}", fields_path.display()))?;
        fs::write(&methods_path, methods)
            .wrap_err_with(|| format!("Failed to write {}", methods_path.display()))?;
    };
    tracing::info!(
        "Generated Forge runtime MCP CSV mappings: {}",
        output.display()
    );
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(input = %srg_to_named.display(), output = %output.display())
)]
fn write_srg_to_named_mapping_file(srg_to_named: &Path, output: &Path) -> eyre::Result<()> {
    let content = fs::read_to_string(srg_to_named)
        .wrap_err_with(|| format!("Failed to read {}", srg_to_named.display()))?;
    let sections = {
        let _span = tracing::debug_span!(
            "write_srg_to_named_mapping_file_collect_sections",
            bytes = content.len()
        )
        .entered();
        collect_srg_mapping_class_sections(&content)
    };
    let class_mappings = {
        let _span = tracing::debug_span!(
            "write_srg_to_named_mapping_file_build_class_map",
            classes = sections.len()
        )
        .entered();
        sections
            .iter()
            .map(|section| {
                (
                    section.srg_class.to_string(),
                    section.named_class.to_string(),
                )
            })
            .collect::<BTreeMap<_, _>>()
    };
    let rendered_sections = {
        let _span = tracing::debug_span!(
            "write_srg_to_named_mapping_file_render",
            classes = sections.len()
        )
        .entered();
        sections
            .par_iter()
            .map(|section| render_srg_mapping_class_section(section, &class_mappings))
            .collect::<Vec<eyre::Result<_>>>()
            .into_iter()
            .collect::<eyre::Result<Vec<_>>>()?
    };
    let output_text = {
        let _span = tracing::debug_span!(
            "write_srg_to_named_mapping_file_merge_rendered",
            classes = rendered_sections.len()
        )
        .entered();
        rendered_sections.concat()
    };

    {
        let _span = tracing::debug_span!("write_srg_to_named_mapping_file_write").entered();
        if let Some(parent) = output.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::write(output, output_text)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    };
    tracing::info!("Generated Mixin refmap remap file: {}", output.display());
    Ok(())
}

#[derive(Debug)]
struct SrgMappingClassSection<'a> {
    srg_class: &'a str,
    named_class: &'a str,
    member_lines: Vec<&'a str>,
}

fn collect_srg_mapping_class_sections(content: &str) -> Vec<SrgMappingClassSection<'_>> {
    let mut sections = Vec::new();
    let mut current: Option<SrgMappingClassSection<'_>> = None;
    for line in content.lines() {
        if line.trim().is_empty() || line.starts_with("tsrg") {
            continue;
        }
        if !line.starts_with('\t') && !line.starts_with(' ') {
            let parts = line.split_whitespace().collect::<Vec<_>>();
            if let [srg_class, named_class] = parts.as_slice() {
                if let Some(section) = current.take() {
                    sections.push(section);
                }
                current = Some(SrgMappingClassSection {
                    srg_class,
                    named_class,
                    member_lines: Vec::new(),
                });
            }
            continue;
        }
        if let Some(section) = current.as_mut() {
            section.member_lines.push(line);
        }
    }
    if let Some(section) = current {
        sections.push(section);
    }
    sections
}

fn render_srg_mapping_class_section(
    section: &SrgMappingClassSection<'_>,
    class_mappings: &BTreeMap<String, String>,
) -> eyre::Result<String> {
    let mut output = String::new();
    writeln!(output, "CL: {} {}", section.srg_class, section.named_class)?;
    for line in &section.member_lines {
        if line.starts_with("\t\t") || line.starts_with("  ") {
            continue;
        }

        let parts = line.split_whitespace().collect::<Vec<_>>();
        match parts.as_slice() {
            [srg, named] => {
                writeln!(
                    output,
                    "FD: {}/{} {}/{}",
                    section.srg_class, srg, section.named_class, named
                )?;
            }
            [srg, descriptor, named] => {
                let named_descriptor = remap_descriptor_classes(descriptor, class_mappings);
                writeln!(
                    output,
                    "MD: {}/{} {} {}/{} {}",
                    section.srg_class,
                    srg,
                    descriptor,
                    section.named_class,
                    named,
                    named_descriptor
                )?;
            }
            _ => {}
        }
    }
    Ok(output)
}

fn remap_descriptor_classes(descriptor: &str, class_mappings: &BTreeMap<String, String>) -> String {
    let mut output = String::with_capacity(descriptor.len());
    let mut cursor = 0;
    while let Some(relative_start) = descriptor[cursor..].find('L') {
        let start = cursor + relative_start;
        output.push_str(&descriptor[cursor..=start]);
        let name_start = start + 1;
        let Some(relative_end) = descriptor[name_start..].find(';') else {
            cursor = name_start;
            break;
        };
        let end = name_start + relative_end;
        let class_name = &descriptor[name_start..end];
        output.push_str(
            class_mappings
                .get(class_name)
                .map_or(class_name, String::as_str),
        );
        output.push(';');
        cursor = end + 1;
    }
    output.push_str(&descriptor[cursor..]);
    output
}

#[derive(Clone, Copy)]
enum ParameterDirection {
    SrgToOfficial,
    OfficialToSrg,
}

fn write_parameter_mappings(
    output: &mut String,
    class_name: &str,
    method: &MojangMethodMapping,
    srg_method: &SrgMethodMapping,
    parchment_parameters: Option<&ParchmentParameters>,
    direction: ParameterDirection,
) -> eyre::Result<()> {
    if srg_method.parameters.is_empty() {
        return Ok(());
    }

    let parchment_method = parchment_parameters
        .and_then(|classes| classes.get(class_name))
        .and_then(|methods| {
            methods.get(&(
                method.official_name.clone(),
                method.official_descriptor.clone(),
            ))
        });

    for (index, srg_name) in &srg_method.parameters {
        let parchment_index = if srg_method.is_static {
            *index
        } else {
            index + 1
        };
        let official_name = parchment_method
            .and_then(|parameters| parameters.get(&parchment_index))
            .map_or_else(|| srg_name.clone(), |name| forgegradle_parameter_name(name));
        match direction {
            ParameterDirection::SrgToOfficial => {
                writeln!(output, "\t\t{index} {srg_name} {official_name}")?;
            }
            ParameterDirection::OfficialToSrg => {
                writeln!(output, "\t\t{index} {official_name} {srg_name}")?;
            }
        }
    }

    Ok(())
}

fn forgegradle_parameter_name(name: &str) -> String {
    let mut chars = name.chars();
    let Some(first) = chars.next() else {
        return String::new();
    };
    format!("p{}{}", first.to_uppercase(), chars.collect::<String>())
}

fn read_parchment_parameters(path: &Path) -> eyre::Result<ParchmentParameters> {
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open Parchment zip {}", path.display()))?;
    let mut entry = archive
        .by_name("parchment.json")
        .wrap_err_with(|| format!("Parchment zip {} has no parchment.json", path.display()))?;
    let mut content = String::new();
    entry
        .read_to_string(&mut content)
        .wrap_err_with(|| format!("Failed to read parchment.json from {}", path.display()))?;
    let data: ParchmentData = facet_json::from_str(&content)
        .wrap_err_with(|| format!("Failed to parse parchment.json from {}", path.display()))?;

    let mut classes = BTreeMap::new();
    for class in data.classes {
        let mut methods = BTreeMap::new();
        for method in class.methods {
            let parameters = method
                .parameters
                .into_iter()
                .map(|parameter| (parameter.index, parameter.name))
                .collect::<BTreeMap<_, _>>();
            if !parameters.is_empty() {
                methods.insert((method.name, method.descriptor), parameters);
            }
        }
        if !methods.is_empty() {
            classes.insert(class.name, methods);
        }
    }

    Ok(classes)
}

fn find_srg_method_mapping<'a>(
    mapping: &'a SrgClassMapping,
    obf_name: &str,
    official_name: &str,
    descriptors: &[String],
) -> Option<&'a SrgMethodMapping> {
    for source_name in [obf_name, official_name] {
        for descriptor in descriptors {
            if let Some(srg_method) = mapping
                .methods
                .get(&(source_name.to_owned(), descriptor.clone()))
            {
                return Some(srg_method);
            }
        }
    }

    for source_name in [obf_name, official_name] {
        let mut candidates = mapping
            .methods
            .iter()
            .filter(|((name, _), _)| name == source_name)
            .map(|(_, srg_method)| srg_method);
        let first = candidates.next();
        if first.is_some() && candidates.next().is_none() {
            return first;
        }
    }

    None
}

fn read_mojang_mappings(paths: &[PathBuf]) -> eyre::Result<BTreeMap<String, MojangClassMapping>> {
    let mut class_names = BTreeMap::new();
    for path in paths {
        let content = fs::read_to_string(path)
            .wrap_err_with(|| format!("Failed to read {}", path.display()))?;
        for line in content.lines() {
            if line.starts_with('#') || line.starts_with(' ') {
                continue;
            }
            if let Some((official, obf_with_colon)) = line.split_once(" -> ") {
                let obf = obf_with_colon.trim_end_matches(':');
                class_names.insert(official.replace('.', "/"), obf.to_string());
            }
        }
    }

    let mut classes = BTreeMap::new();
    for path in paths {
        let content = fs::read_to_string(path)
            .wrap_err_with(|| format!("Failed to read {}", path.display()))?;
        let mut current_official = None::<String>;
        let mut current_obf = None::<String>;
        for line in content.lines() {
            if line.starts_with('#') {
                continue;
            }
            if !line.starts_with(' ') {
                if let Some((official, obf_with_colon)) = line.split_once(" -> ") {
                    let official_slash = official.replace('.', "/");
                    let obf = obf_with_colon.trim_end_matches(':').to_string();
                    classes
                        .entry(official_slash.clone())
                        .or_insert_with(|| MojangClassMapping {
                            official_slash: official_slash.clone(),
                            obf: obf.clone(),
                            fields: BTreeMap::new(),
                            methods: BTreeMap::new(),
                        });
                    current_official = Some(official_slash);
                    current_obf = Some(obf);
                }
                continue;
            }

            let Some(class_name) = &current_official else {
                continue;
            };
            let Some(class_obf) = &current_obf else {
                continue;
            };
            let member = line.trim();
            let Some((left, obf_name)) = member.split_once(" -> ") else {
                continue;
            };
            let class = classes
                .get_mut(class_name)
                .ok_or_else(|| eyre::eyre!("Missing Mojang class mapping for {class_name}"))?;
            if left.contains('(') {
                if let Some((official_name, official_descriptor, obf_descriptor)) =
                    parse_mojang_method_signature(left, &class_names)?
                {
                    class.methods.insert(
                        (obf_name.to_string(), obf_descriptor),
                        MojangMethodMapping {
                            official_name,
                            official_descriptor,
                        },
                    );
                }
            } else if let Some(official_name) = left.split_whitespace().last() {
                class
                    .fields
                    .insert(obf_name.to_string(), official_name.to_string());
            }

            if class.obf != *class_obf {
                eyre::bail!("Conflicting Mojang class mapping for {class_name}");
            }
        }
    }

    Ok(classes)
}

fn read_srg_member_mappings(path: &Path) -> eyre::Result<BTreeMap<String, SrgClassMapping>> {
    let content =
        fs::read_to_string(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut classes = BTreeMap::new();
    let mut current_official = None::<String>;
    let mut current_method_key = None::<(String, String)>;

    for line in content.lines() {
        if line.trim().is_empty() || line.starts_with("tsrg") {
            continue;
        }
        if !line.starts_with('\t') && !line.starts_with(' ') {
            let parts = line.split_whitespace().collect::<Vec<_>>();
            if parts.len() >= 2 {
                let official = parts[1].to_string();
                current_official = Some(official.clone());
                current_method_key = None;
                classes
                    .entry(official.clone())
                    .or_insert_with(|| SrgClassMapping {
                        fields: BTreeMap::new(),
                        methods: BTreeMap::new(),
                    });
            }
            continue;
        }

        if line.starts_with("\t\t") || line.starts_with("  ") {
            let Some(class_name) = &current_official else {
                continue;
            };
            let Some(method_key) = &current_method_key else {
                continue;
            };
            let parts = line.split_whitespace().collect::<Vec<_>>();
            if parts.len() == 1 && parts[0] == "static" {
                if let Some(method) = classes
                    .get_mut(class_name)
                    .and_then(|class| class.methods.get_mut(method_key))
                {
                    method.is_static = true;
                }
                continue;
            }
            if parts.len() >= 3 {
                let index = parts[0].parse::<usize>().wrap_err_with(|| {
                    format!(
                        "Invalid TSRG parameter index {} in {}",
                        parts[0],
                        path.display()
                    )
                })?;
                let parameter_name = parts[parts.len() - 1].to_string();
                if let Some(method) = classes
                    .get_mut(class_name)
                    .and_then(|class| class.methods.get_mut(method_key))
                {
                    method.parameters.insert(index, parameter_name);
                }
            }
            continue;
        }
        let Some(class_name) = &current_official else {
            continue;
        };
        let parts = line.split_whitespace().collect::<Vec<_>>();
        let class = classes
            .get_mut(class_name)
            .ok_or_else(|| eyre::eyre!("Missing SRG class mapping for {class_name}"))?;
        if parts.len() >= 3 && parts[1].starts_with('(') {
            let method_key = (parts[0].to_string(), parts[1].to_string());
            class.methods.insert(
                method_key.clone(),
                SrgMethodMapping {
                    srg_name: parts[2].to_string(),
                    parameters: BTreeMap::new(),
                    is_static: false,
                },
            );
            current_method_key = Some(method_key);
        } else if parts.len() >= 2 {
            class
                .fields
                .insert(parts[0].to_string(), parts[1].to_string());
            current_method_key = None;
        }
    }

    Ok(classes)
}

fn parse_mojang_method_signature(
    raw: &str,
    class_names: &BTreeMap<String, String>,
) -> eyre::Result<Option<(String, String, String)>> {
    let signature = strip_mojang_line_numbers(raw);
    let Some(open_paren) = signature.find('(') else {
        return Ok(None);
    };
    let Some(close_paren) = signature.rfind(')') else {
        return Ok(None);
    };
    let before_args = signature[..open_paren].trim();
    let Some((return_type, method_name)) = before_args.rsplit_once(' ') else {
        return Ok(None);
    };
    let args = &signature[open_paren + 1..close_paren];
    let mut official_descriptor = String::from("(");
    let mut obf_descriptor = String::from("(");
    if !args.trim().is_empty() {
        for arg in args.split(',') {
            official_descriptor.push_str(&type_descriptor(arg.trim(), class_names, false)?);
            obf_descriptor.push_str(&type_descriptor(arg.trim(), class_names, true)?);
        }
    }
    official_descriptor.push(')');
    official_descriptor.push_str(&type_descriptor(return_type.trim(), class_names, false)?);
    obf_descriptor.push(')');
    obf_descriptor.push_str(&type_descriptor(return_type.trim(), class_names, true)?);
    Ok(Some((
        method_name.to_string(),
        official_descriptor,
        obf_descriptor,
    )))
}

fn strip_mojang_line_numbers(raw: &str) -> &str {
    let mut remaining = raw;
    for _ in 0..2 {
        let Some((left, right)) = remaining.split_once(':') else {
            return raw;
        };
        if left.chars().all(|character| character.is_ascii_digit()) {
            remaining = right;
        } else {
            return raw;
        }
    }
    remaining
}

fn type_descriptor(
    raw_type: &str,
    class_names: &BTreeMap<String, String>,
    obfuscate_classes: bool,
) -> eyre::Result<String> {
    let mut ty = raw_type.trim();
    let mut array_depth = 0usize;
    while let Some(stripped) = ty.strip_suffix("[]") {
        array_depth += 1;
        ty = stripped;
    }

    let base = match ty {
        "void" => "V".to_string(),
        "boolean" => "Z".to_string(),
        "byte" => "B".to_string(),
        "char" => "C".to_string(),
        "short" => "S".to_string(),
        "int" => "I".to_string(),
        "long" => "J".to_string(),
        "float" => "F".to_string(),
        "double" => "D".to_string(),
        _ => {
            let official_slash = ty.replace('.', "/");
            let mapped = if obfuscate_classes {
                class_names
                    .get(&official_slash)
                    .map_or(official_slash, Clone::clone)
            } else {
                official_slash
            };
            format!("L{mapped};")
        }
    };

    if array_depth == 0 {
        return Ok(base);
    }
    if base == "V" {
        eyre::bail!("Invalid array type: {raw_type}");
    }
    Ok(format!("{}{base}", "[".repeat(array_depth)))
}

fn resolve_compare_paths(
    options: &CompareOptions,
    target: &WorktreeTarget,
) -> eyre::Result<ComparePaths> {
    let minecraft_dir = target
        .worktree_path
        .as_path()
        .join("platform")
        .join("minecraft");
    let properties = read_properties(&minecraft_dir.join("gradle.properties"))?;
    let minecraft_version = required_property(&properties, "minecraft_version")?;
    let mod_name = required_property(&properties, "mod_name")?;
    let mod_version = required_property(&properties, "mod_version")?;

    Ok(ComparePaths {
        gradle_jar: options.gradle_jar.clone().unwrap_or_else(|| {
            gradle_output_jar_path(&minecraft_dir, mod_name, minecraft_version, mod_version)
        }),
        rust_jar: options.rust_jar.clone().unwrap_or_else(|| {
            rust_output_jar_path(&minecraft_dir, mod_name, minecraft_version, mod_version)
        }),
    })
}

fn compare_jars(
    gradle_jar: &Path,
    rust_jar: &Path,
    strict_manifest: bool,
) -> eyre::Result<JarCompareReport> {
    let gradle = read_normalized_jar(gradle_jar, strict_manifest)?;
    let rust = read_normalized_jar(rust_jar, strict_manifest)?;

    let gradle_names: BTreeSet<String> = gradle.entries.keys().cloned().collect();
    let rust_names: BTreeSet<String> = rust.entries.keys().cloned().collect();

    let missing_entries: Vec<String> = gradle_names.difference(&rust_names).cloned().collect();
    let extra_entries: Vec<String> = rust_names.difference(&gradle_names).cloned().collect();
    let common_entries: Vec<String> = gradle_names.intersection(&rust_names).cloned().collect();

    let changed_entries = common_entries
        .iter()
        .filter_map(|path| {
            let gradle_sha1 = gradle.entries.get(path)?;
            let rust_sha1 = rust.entries.get(path)?;
            (gradle_sha1 != rust_sha1).then(|| ChangedEntry {
                path: path.clone(),
                gradle_hash: *gradle_sha1,
                rust_hash: *rust_sha1,
            })
        })
        .collect::<Vec<_>>();

    let manifest_compared = gradle.manifest_sha1.is_some() || rust.manifest_sha1.is_some();
    let manifest_changed = gradle.manifest_sha1 != rust.manifest_sha1;
    let manifest = ManifestCompare {
        compared: manifest_compared,
        changed: manifest_changed,
        ignored_implementation_timestamp: !strict_manifest,
        gradle_sha1: gradle.manifest_sha1,
        rust_sha1: rust.manifest_sha1,
    };

    let matches = missing_entries.is_empty()
        && extra_entries.is_empty()
        && changed_entries.is_empty()
        && !manifest.changed;

    Ok(JarCompareReport {
        gradle_jar: gradle_jar.to_path_buf(),
        rust_jar: rust_jar.to_path_buf(),
        strict_manifest,
        matches,
        total_gradle_entries: gradle.total_entries,
        total_rust_entries: rust.total_entries,
        compared_entries: common_entries.len() + usize::from(manifest_compared),
        missing_entries,
        extra_entries,
        changed_entries,
        manifest,
    })
}

fn read_normalized_jar(path: &Path, strict_manifest: bool) -> eyre::Result<NormalizedJar> {
    if !path.is_file() {
        eyre::bail!("Jar does not exist: {}", path.display());
    }

    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to open jar {}", path.display()))?;
    let mut entries = BTreeMap::new();
    let mut manifest_sha1 = None;
    let mut total_entries = 0usize;

    for index in 0..archive.len() {
        let mut file = archive.by_index(index).wrap_err_with(|| {
            format!("Failed to read jar entry #{index} from {}", path.display())
        })?;
        let name = file.name().replace('\\', "/");
        if name.ends_with('/') {
            continue;
        }

        total_entries += 1;
        let mut entry_bytes = Vec::new();
        file.read_to_end(&mut entry_bytes)
            .wrap_err_with(|| format!("Failed to read jar entry {name} from {}", path.display()))?;

        if name.eq_ignore_ascii_case("META-INF/MANIFEST.MF") {
            let normalized = normalize_manifest_bytes(&entry_bytes, strict_manifest);
            manifest_sha1 = Some(ContentHash::from_bytes(
                normalized.as_bytes(),
                ContentHashAlgorithm::Blake3,
            ));
        } else {
            entries.insert(
                name,
                ContentHash::from_bytes(&entry_bytes, ContentHashAlgorithm::Blake3),
            );
        }
    }

    Ok(NormalizedJar {
        entries,
        manifest_sha1,
        total_entries,
    })
}

fn normalize_manifest_bytes(bytes: &[u8], strict_manifest: bool) -> String {
    let text = String::from_utf8_lossy(bytes)
        .replace("\r\n", "\n")
        .replace('\r', "\n");
    if strict_manifest {
        return text;
    }

    let mut lines = Vec::new();
    let mut skipping_timestamp = false;
    for line in text.split('\n') {
        if skipping_timestamp && line.starts_with(' ') {
            continue;
        }
        skipping_timestamp = false;
        if line.starts_with("Implementation-Timestamp:") {
            skipping_timestamp = true;
            continue;
        }
        lines.push(line);
    }

    lines.join("\n")
}

fn emit_compare_report(report: &JarCompareReport) {
    tracing::info!("Gradle jar: {}", report.gradle_jar.display());
    tracing::info!("Rust jar:   {}", report.rust_jar.display());
    tracing::info!("Compared entries: {}", report.compared_entries);
    tracing::info!("Gradle entries:   {}", report.total_gradle_entries);
    tracing::info!("Rust entries:     {}", report.total_rust_entries);
    tracing::info!("Missing entries:  {}", report.missing_entries.len());
    tracing::info!("Extra entries:    {}", report.extra_entries.len());
    tracing::info!("Changed entries:  {}", report.changed_entries.len());
    tracing::info!(
        "Manifest changed: {}",
        if report.manifest.changed { "yes" } else { "no" }
    );

    emit_string_list("Missing", &report.missing_entries);
    emit_string_list("Extra", &report.extra_entries);
    emit_changed_entries(&report.changed_entries);

    if report.matches {
        tracing::info!("Jar comparison passed: normalized jars match.");
    } else {
        tracing::info!("Jar comparison failed: normalized jars differ.");
    }
}

fn emit_string_list(label: &str, entries: &[String]) {
    if entries.is_empty() {
        return;
    }

    tracing::info!("{label} entry sample:");
    for entry in entries.iter().take(20) {
        tracing::info!(" - {entry}");
    }
    if entries.len() > 20 {
        tracing::info!(" - ... {} more", entries.len() - 20);
    }
}

fn emit_changed_entries(entries: &[ChangedEntry]) {
    if entries.is_empty() {
        return;
    }

    tracing::info!("Changed entry sample:");
    for entry in entries.iter().take(20) {
        tracing::info!(
            " - {} (gradle {}, rust {})",
            entry.path,
            entry.gradle_hash,
            entry.rust_hash
        );
    }
    if entries.len() > 20 {
        tracing::info!(" - ... {} more", entries.len() - 20);
    }
}

fn write_compare_reports(
    reports: &[TargetJarCompareReport],
    requested_path: Option<&Path>,
) -> eyre::Result<()> {
    let Some(path) = requested_path else {
        return Ok(());
    };

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let json = if reports.len() == 1 {
        facet_json::to_string_pretty(&reports[0].report)?
    } else {
        facet_json::to_string_pretty(reports)?
    };
    fs::write(path, json).wrap_err_with(|| format!("Failed to write {}", path.display()))?;
    Ok(())
}

fn write_last_plan_output(plan: &BuildPlan) -> eyre::Result<()> {
    fs::create_dir_all(&plan.state_dir)?;
    let plan_json = facet_json::to_string_pretty(plan)?;
    let last_plan_path = plan.state_dir.join("last-plan.json");
    fs::write(&last_plan_path, &plan_json)
        .wrap_err_with(|| format!("Failed to write {}", last_plan_path.display()))?;

    Ok(())
}

fn write_requested_plan_outputs(
    plans: &[BuildPlan],
    requested_path: Option<&Path>,
) -> eyre::Result<()> {
    let Some(path) = requested_path else {
        return Ok(());
    };

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }

    let plan_json = if let [plan] = plans {
        facet_json::to_string_pretty(plan)?
    } else {
        facet_json::to_string_pretty(&plans)?
    };
    fs::write(path, &plan_json).wrap_err_with(|| format!("Failed to write {}", path.display()))?;

    Ok(())
}

fn write_artifact_lockfile(plan: &BuildPlan) -> eyre::Result<()> {
    write_artifact_lockfile_with_extra_cache_paths(plan, &[])
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %plan.branch_name,
        mc = %plan.minecraft_version,
        artifacts = plan.artifacts.len(),
        dependencies = plan.dependencies.len(),
        extra_cache_paths = extra_cache_paths.len(),
    )
)]
fn write_artifact_lockfile_with_extra_cache_paths(
    plan: &BuildPlan,
    extra_cache_paths: &[PathBuf],
) -> eyre::Result<()> {
    let lockfile = build_artifact_lockfile(plan, extra_cache_paths)?;
    if let Some(parent) = plan.lockfile_path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(
        &plan.lockfile_path,
        facet_json::to_string_pretty(&lockfile)?,
    )
    .wrap_err_with(|| format!("Failed to write {}", plan.lockfile_path.display()))?;
    tracing::info!(
        "Artifact lockfile: {} ({} artifacts)",
        plan.lockfile_path.display(),
        lockfile.artifacts.len()
    );
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(
        branch = %plan.branch_name,
        mc = %plan.minecraft_version
    )
)]
#[expect(
    clippy::too_many_lines,
    reason = "Lockfile construction deliberately keeps artifact/dependency/extra sections in one pass."
)]
fn build_artifact_lockfile(
    plan: &BuildPlan,
    extra_cache_paths: &[PathBuf],
) -> eyre::Result<ArtifactLockfile> {
    let mut artifacts = Vec::new();
    let mut entries = Vec::new();
    if let Some(existing_lockfile) = &plan.lockfile {
        let _span = tracing::debug_span!(
            "artifact_lock_migrate_entries",
            entries = existing_lockfile.artifacts.len()
        )
        .entered();
        entries.extend(
            existing_lockfile
                .artifacts
                .par_iter()
                .map(|locked| migrate_locked_artifact(plan, locked).map(Some))
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()
                .wrap_err("Failed to migrate artifact lock entry")?,
        );
    }
    {
        let _span =
            tracing::debug_span!("artifact_lock_plan_entries", entries = plan.artifacts.len())
                .entered();
        entries.extend(
            plan.artifacts
                .par_iter()
                .map(|artifact| artifact_lock_entry_from_plan_artifact(plan, artifact).map(Some))
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()
                .wrap_err("Failed to build planned artifact lock entry")?,
        );
    };
    {
        let _span = tracing::debug_span!(
            "artifact_lock_dependency_entries",
            entries = plan.dependencies.len()
        )
        .entered();
        entries.extend(
            plan.dependencies
                .par_iter()
                .map(|dependency| {
                    artifact_lock_entry_from_cache_path(
                        plan,
                        &dependency.cache_path,
                        Some(&dependency.resolved_notation),
                    )
                    .map(Some)
                })
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()
                .wrap_err("Failed to build dependency artifact lock entry")?,
        );
    };
    {
        let _span = tracing::debug_span!(
            "artifact_lock_extra_entries",
            entries = extra_cache_paths.len()
        )
        .entered();
        entries.extend(
            extra_cache_paths
                .par_iter()
                .map(|path| {
                    if should_record_extra_cache_artifact(plan, path)? {
                        artifact_lock_entry_from_cache_path(plan, path, None).map(Some)
                    } else {
                        Ok(None)
                    }
                })
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()
                .wrap_err("Failed to build extra artifact lock entry")?,
        );
    };

    for entry in entries.into_iter().flatten() {
        push_artifact_lock_entry(&mut artifacts, entry);
    }

    artifacts.sort_by(|left, right| {
        (
            left.coordinate.as_deref(),
            left.repository.as_deref(),
            left.cache_path.as_path(),
        )
            .cmp(&(
                right.coordinate.as_deref(),
                right.repository.as_deref(),
                right.cache_path.as_path(),
            ))
    });

    Ok(ArtifactLockfile {
        schema_version: 1,
        minecraft_version: plan.minecraft_version.to_string(),
        maven_cache_dir: portable_cache_path(plan, &plan.maven_cache_dir),
        allow_local_artifact_cache: plan.allow_local_artifact_cache,
        repositories: plan.repositories.clone(),
        dependencies: plan
            .dependencies
            .iter()
            .map(|dependency| DependencyLockEntry {
                configuration: dependency.configuration.clone(),
                notation: dependency.notation.clone(),
                resolved_notation: dependency.resolved_notation.clone(),
                source: dependency.source.clone(),
                dynamic_version: dependency.dynamic_version,
                cache_path: portable_cache_path(plan, &dependency.cache_path),
            })
            .collect(),
        artifacts,
    })
}

fn should_record_extra_cache_artifact(plan: &BuildPlan, path: &Path) -> eyre::Result<bool> {
    Ok(path.is_file()
        && path.starts_with(&plan.maven_cache_dir)
        && read_artifact_provenance(path)?.is_some())
}

fn migrate_locked_artifact(
    plan: &BuildPlan,
    locked: &ArtifactLockEntry,
) -> eyre::Result<ArtifactLockEntry> {
    let Some(coordinate_text) = locked.coordinate.as_deref() else {
        return Ok(locked.clone());
    };
    let Ok(coordinate) = MavenCoordinate::parse(coordinate_text) else {
        return Ok(locked.clone());
    };
    let cache_path = maven_cache_path_for(&plan.maven_cache_dir, &coordinate);
    if !cache_path.is_file() {
        return Ok(locked.clone());
    }
    let legacy_actual_hash = ContentHash::from_path(&cache_path, locked.hash.algorithm)?;
    if legacy_actual_hash != locked.hash {
        return Ok(locked.clone());
    }
    let actual_hash = if locked.hash.algorithm == ContentHashAlgorithm::Blake3 {
        legacy_actual_hash
    } else {
        ContentHash::from_path(&cache_path, ContentHashAlgorithm::Blake3)?
    };
    let provenance = read_artifact_provenance(&cache_path)?.unwrap_or_else(|| {
        artifact_provenance(
            locked.source.clone(),
            locked.coordinate.clone(),
            locked.repository.clone(),
            locked.url.clone(),
            locked.original_path.clone(),
            locked.source_git.clone(),
            actual_hash,
        )
    });
    Ok(ArtifactLockEntry {
        coordinate: provenance.coordinate.or_else(|| locked.coordinate.clone()),
        source: provenance.source,
        repository: provenance.repository.or_else(|| locked.repository.clone()),
        url: provenance.url.or_else(|| locked.url.clone()),
        cache_path: portable_cache_path(plan, &cache_path),
        original_path: provenance
            .original_path
            .or_else(|| locked.original_path.clone()),
        source_relative_path: provenance
            .source_relative_path
            .or_else(|| locked.source_relative_path.clone()),
        source_git: provenance.source_git.or_else(|| locked.source_git.clone()),
        source_build: provenance
            .source_build
            .or_else(|| locked.source_build.clone()),
        hash: actual_hash,
    })
}

fn artifact_lock_entry_from_plan_artifact(
    plan: &BuildPlan,
    artifact: &ArtifactPlan,
) -> eyre::Result<ArtifactLockEntry> {
    let actual_hash = artifact_actual_hash(&artifact.cache_path, artifact.sha1.as_ref())?;
    let provenance_actual_hash = if artifact.cache_path.is_file()
        && artifact.provenance.hash.algorithm == actual_hash.algorithm
    {
        actual_hash
    } else {
        ContentHash::from_path(&artifact.cache_path, artifact.provenance.hash.algorithm)?
    };
    if provenance_actual_hash != artifact.provenance.hash {
        eyre::bail!(
            "Artifact provenance hash mismatch for {}: sidecar {}, actual {}",
            artifact.cache_path.display(),
            artifact.provenance.hash,
            provenance_actual_hash
        );
    }
    Ok(ArtifactLockEntry {
        coordinate: artifact
            .provenance
            .coordinate
            .clone()
            .or_else(|| artifact.coordinate.clone()),
        source: artifact.provenance.source.clone(),
        repository: artifact.provenance.repository.clone(),
        url: artifact.provenance.url.clone(),
        cache_path: portable_cache_path(plan, &artifact.cache_path),
        original_path: artifact.provenance.original_path.clone(),
        source_relative_path: artifact.provenance.source_relative_path.clone(),
        source_git: artifact.provenance.source_git.clone(),
        source_build: artifact.provenance.source_build.clone(),
        hash: actual_hash,
    })
}

fn artifact_lock_entry_from_cache_path(
    plan: &BuildPlan,
    path: &Path,
    fallback_coordinate: Option<&str>,
) -> eyre::Result<ArtifactLockEntry> {
    let actual_hash = ContentHash::from_path(path, ContentHashAlgorithm::Blake3)?;
    let provenance = read_artifact_provenance(path)?.unwrap_or_else(|| {
        artifact_provenance(
            ArtifactSource::ExistingSfmCacheUnknown,
            fallback_coordinate.map(str::to_string),
            None,
            None,
            None,
            None,
            actual_hash,
        )
    });
    let provenance_actual_hash = if provenance.hash.algorithm == ContentHashAlgorithm::Blake3 {
        actual_hash
    } else {
        ContentHash::from_path(path, provenance.hash.algorithm)?
    };
    if provenance_actual_hash != provenance.hash {
        eyre::bail!(
            "Artifact provenance hash mismatch for {}: sidecar {}, actual {}",
            path.display(),
            provenance.hash,
            provenance_actual_hash
        );
    }
    Ok(ArtifactLockEntry {
        coordinate: provenance.coordinate,
        source: provenance.source,
        repository: provenance.repository,
        url: provenance.url,
        cache_path: portable_cache_path(plan, path),
        original_path: provenance.original_path,
        source_relative_path: provenance.source_relative_path,
        source_git: provenance.source_git,
        source_build: provenance.source_build,
        hash: actual_hash,
    })
}

fn artifact_actual_hash(
    path: &Path,
    planned_hash: Option<&ContentHash>,
) -> eyre::Result<ContentHash> {
    if path.is_file() {
        let actual_hash = ContentHash::from_path(path, ContentHashAlgorithm::Blake3)?;
        if let Some(planned_hash) = planned_hash {
            let planned_actual_hash = if planned_hash.algorithm == ContentHashAlgorithm::Blake3 {
                actual_hash
            } else {
                ContentHash::from_path(path, planned_hash.algorithm)?
            };
            if planned_actual_hash != *planned_hash {
                eyre::bail!(
                    "Artifact {} resolved with content hash {}, but the plan recorded {}",
                    path.display(),
                    planned_actual_hash,
                    planned_hash
                );
            }
        }
        return Ok(actual_hash);
    }
    planned_hash.copied().ok_or_else(|| {
        eyre::eyre!(
            "Artifact is missing and has no content hash: {}",
            path.display()
        )
    })
}

fn push_artifact_lock_entry(artifacts: &mut Vec<ArtifactLockEntry>, entry: ArtifactLockEntry) {
    if let Some(existing) = artifacts
        .iter()
        .position(|artifact| artifact.same_locked_artifact(&entry))
    {
        artifacts[existing] = entry;
        return;
    }
    artifacts.push(entry);
}

impl ArtifactLockEntry {
    fn same_locked_artifact(&self, other: &Self) -> bool {
        if self.coordinate.is_some() || other.coordinate.is_some() {
            return self.coordinate == other.coordinate;
        }
        self.cache_path == other.cache_path
    }
}

fn portable_cache_path(plan: &BuildPlan, path: &Path) -> PathBuf {
    if let Ok(relative) = path.strip_prefix(&plan.common_cache_dir) {
        return PathBuf::from("$sfm-cache").join(relative);
    }
    relative_path(&plan.minecraft_dir, path)
}

fn read_optional_artifact_lockfile(
    path: &Path,
    minecraft_version: &str,
) -> eyre::Result<Option<ArtifactLockfile>> {
    if !path.is_file() {
        return Ok(None);
    }
    let content =
        fs::read_to_string(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let lockfile: ArtifactLockfile = facet_json::from_str(&content)
        .wrap_err_with(|| format!("Failed to parse {}", path.display()))?;
    if lockfile.minecraft_version != minecraft_version {
        eyre::bail!(
            "Lockfile {} is for Minecraft {}, but this plan is for {}",
            path.display(),
            lockfile.minecraft_version,
            minecraft_version
        );
    }
    Ok(Some(lockfile))
}

fn relative_path(base: &Path, path: &Path) -> PathBuf {
    path.strip_prefix(base)
        .map_or_else(|_| path.to_path_buf(), Path::to_path_buf)
}

fn print_plan_summary(plan: &BuildPlan) {
    let dependency_label = if plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        "project deps"
    } else {
        "fg.deobf deps"
    };
    let lines = [
        "Clean-slate jar build plan resolved.".to_string(),
        format!("Minecraft:    {}", plan.minecraft_version),
        format!("Worktree:     {}", plan.worktree_path.display()),
        format!("Gradle jar:   {}", plan.gradle_output_jar.display()),
        format!("Rust jar:     {}", plan.rust_output_jar.display()),
        format!("Java:         {}", plan.java.executable.display()),
        format!("Java release: {}", plan.java_release),
        format!("Common cache: {}", plan.common_cache_dir.display()),
        format!(
            "Toolchain:    {:?} ({})",
            plan.loader_toolchain.kind, plan.loader_toolchain.userdev_coordinate
        ),
        format!(
            "State:        {}",
            plan.state_dir.join("last-plan.json").display()
        ),
        format!("Lockfile:     {}", plan.lockfile_path.display()),
        format!("Artifacts:    {}", plan.artifacts.len()),
        format!(
            "Portable:     {}/{} artifacts",
            plan.artifact_portability.portable_artifacts, plan.artifact_portability.total_artifacts
        ),
        format!("{dependency_label}: {}", plan.dependencies.len()),
        format!("Graph nodes:  {}", plan.graph.len()),
    ];
    for line in lines {
        tracing::info!("{line}");
    }

    for warning in &plan.warnings {
        tracing::warn!("Warning: {warning}");
    }
}

#[instrument(level = "info", skip_all, fields(minecraft_version = %minecraft_version))]
fn read_java_toolchain_release(minecraft_dir: &Path, minecraft_version: &str) -> eyre::Result<u32> {
    let path = minecraft_dir
        .join("gradle")
        .join("java-toolchain")
        .join(minecraft_version)
        .join("java-toolchain.gradle");
    let content =
        fs::read_to_string(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let start = content.find("JavaLanguageVersion.of(").ok_or_else(|| {
        eyre::eyre!(
            "Could not find JavaLanguageVersion.of(...) in {}",
            path.display()
        )
    })? + "JavaLanguageVersion.of(".len();
    let end = content[start..]
        .find(')')
        .ok_or_else(|| eyre::eyre!("Could not parse Java toolchain in {}", path.display()))?
        + start;
    content[start..end]
        .trim()
        .parse()
        .wrap_err_with(|| format!("Could not parse Java release from {}", path.display()))
}

#[instrument(level = "info", skip_all, fields(loader_toolchain = ?loader_toolchain.kind, java_release = %java_release))]
fn required_java_runtime_major(loader_toolchain: &LoaderToolchainPlan, java_release: u32) -> u32 {
    if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        java_release.max(21)
    } else {
        java_release
    }
}

fn javac_executable(java: &JavaPlan) -> PathBuf {
    java.home.as_ref().map_or_else(
        || {
            if cfg!(windows) {
                PathBuf::from("javac.exe")
            } else {
                PathBuf::from("javac")
            }
        },
        |home| {
            home.join("bin")
                .join(if cfg!(windows) { "javac.exe" } else { "javac" })
        },
    )
}

fn canonicalize_lenient(path: &Path) -> eyre::Result<PathBuf> {
    if path.exists() {
        return dunce::canonicalize(path)
            .wrap_err_with(|| format!("Failed to canonicalize {}", path.display()));
    }

    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Path has no parent: {}", path.display()))?;
    let canonical_parent = if parent.exists() {
        dunce::canonicalize(parent)
            .wrap_err_with(|| format!("Failed to canonicalize {}", parent.display()))?
    } else {
        canonicalize_lenient(parent)?
    };
    let file_name = path
        .file_name()
        .ok_or_else(|| eyre::eyre!("Path has no file name: {}", path.display()))?;
    Ok(canonical_parent.join(file_name))
}

fn relative_zip_name(root: &Path, path: &Path) -> eyre::Result<String> {
    let relative = path
        .strip_prefix(root)
        .wrap_err_with(|| format!("{} is not under {}", path.display(), root.display()))?;
    Ok(relative.to_string_lossy().replace('\\', "/"))
}

fn gradle_output_jar_path(
    minecraft_dir: &Path,
    mod_name: &str,
    minecraft_version: &str,
    mod_version: &str,
) -> PathBuf {
    minecraft_dir.join("build").join("libs").join(format!(
        "{mod_name}-MC{minecraft_version}-{mod_version}.jar"
    ))
}

fn rust_output_jar_path(
    minecraft_dir: &Path,
    mod_name: &str,
    minecraft_version: &str,
    mod_version: &str,
) -> PathBuf {
    minecraft_dir.join("build").join("libs").join(format!(
        "{mod_name}-MC{minecraft_version}-{mod_version}-rust.jar"
    ))
}

fn read_properties(path: &Path) -> eyre::Result<BTreeMap<String, String>> {
    let content = fs::read_to_string(path)
        .wrap_err_with(|| format!("Failed to read properties file: {}", path.display()))?;
    let mut properties = BTreeMap::new();

    for line in content.lines().map(str::trim) {
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        if let Some((key, value)) = line.split_once('=') {
            properties.insert(key.trim().to_string(), value.trim().to_string());
        }
    }

    Ok(properties)
}

fn required_property<'a>(
    properties: &'a BTreeMap<String, String>,
    key: &str,
) -> eyre::Result<&'a str> {
    properties
        .get(key)
        .map(String::as_str)
        .filter(|value| !value.is_empty())
        .ok_or_else(|| eyre::eyre!("Missing required gradle.properties key: {key}"))
}

fn repositories() -> Vec<Repository> {
    [
        ("Forge", "https://maven.minecraftforge.net"),
        ("NeoForged", "https://maven.neoforged.net/releases"),
        ("Maven Central", "https://repo1.maven.org/maven2"),
        ("Parchment", "https://maven.parchmentmc.org"),
        (
            "Sponge",
            "https://repo.spongepowered.org/repository/maven-public",
        ),
        ("BlameJared", "https://maven.blamejared.com"),
        ("JEI", "https://dvs1.progwml6.com/files/maven"),
        ("CurseMaven", "https://www.cursemaven.com"),
        ("ModMaven", "https://modmaven.dev"),
        ("Thermal", "https://maven.covers1624.net"),
    ]
    .into_iter()
    .map(|(name, url)| Repository {
        name: name.to_string(),
        url: url.to_string(),
    })
    .collect()
}

fn plain_artifact(
    id: ArtifactId,
    url: &str,
    cache_path: PathBuf,
    required_for: ArtifactPurpose,
) -> eyre::Result<ArtifactPlan> {
    let hash = ContentHash::from_path(&cache_path, ContentHashAlgorithm::Blake3)?;
    Ok(ArtifactPlan {
        id,
        coordinate: None,
        repository: None,
        url: Some(url.to_string()),
        sha1: Some(hash),
        cache_path,
        downloaded: true,
        required_for,
        provenance: artifact_provenance(
            ArtifactSource::RemoteHttp,
            None,
            None,
            Some(url.to_string()),
            None,
            None,
            hash,
        ),
    })
}

fn source_git_provenance(path: &Path) -> Option<SourceGitProvenance> {
    let working_dir = if path.is_dir() { path } else { path.parent()? };
    let root = PathBuf::from(git_stdout(working_dir, ["rev-parse", "--show-toplevel"])?);
    let commit = git_stdout(&root, ["rev-parse", "HEAD"])?;
    let branch = git_stdout(&root, ["rev-parse", "--abbrev-ref", "HEAD"])?;
    let status = git_stdout(&root, ["status", "--porcelain"])?;
    let remote_url = git_stdout(&root, ["remote", "get-url", "origin"]);
    Some(SourceGitProvenance {
        root,
        commit,
        branch,
        dirty: !status.trim().is_empty(),
        remote_url,
    })
}

fn git_stdout<const N: usize>(working_dir: &Path, args: [&str; N]) -> Option<String> {
    let output = Command::new("git")
        .arg("-C")
        .arg(working_dir)
        .args(args)
        .output()
        .ok()?;
    if !output.status.success() {
        return None;
    }
    let stdout = String::from_utf8(output.stdout).ok()?;
    Some(stdout.trim().to_string())
}

#[instrument(level = "debug", skip_all, fields(source = ?source, coordinate, repository, url, original_path = ?original_path, source_git = ?source_git))]
fn artifact_provenance(
    source: ArtifactSource,
    coordinate: Option<String>,
    repository: Option<String>,
    url: Option<String>,
    original_path: Option<PathBuf>,
    source_git: Option<SourceGitProvenance>,
    hash: ContentHash,
) -> ArtifactProvenance {
    let source_relative_path = source_relative_path(original_path.as_deref(), source_git.as_ref());
    let source_build = source_build_provenance(
        &source,
        coordinate.as_deref(),
        source_relative_path.as_deref(),
        source_git.as_ref(),
    );
    ArtifactProvenance {
        schema_version: 1,
        source,
        coordinate,
        repository,
        url,
        original_path,
        source_relative_path,
        source_git,
        source_build,
        hash,
    }
}

fn source_build_provenance(
    source: &ArtifactSource,
    coordinate: Option<&str>,
    source_relative_path: Option<&Path>,
    source_git: Option<&SourceGitProvenance>,
) -> Option<SourceBuildProvenance> {
    if source != &ArtifactSource::ExplicitSource {
        return None;
    }
    let source_git = source_git?;
    let source_relative_path = source_relative_path?;
    if !source_git.root.join("gradlew").is_file() && !source_git.root.join("gradlew.bat").is_file()
    {
        return None;
    }
    let coordinate = coordinate.and_then(|coordinate| MavenCoordinate::parse(coordinate).ok())?;
    let mut environment = BTreeMap::new();
    if let Some(build_number) = explicit_source_build_number(&coordinate) {
        environment.insert("BUILD_NUMBER".to_string(), build_number);
    }

    Some(SourceBuildProvenance {
        build_system: SourceBuildSystem::GradleWrapper,
        tasks: vec![explicit_source_build_task(&coordinate)],
        environment,
        output_path: source_relative_path.to_path_buf(),
    })
}

fn explicit_source_build_task(coordinate: &MavenCoordinate) -> String {
    coordinate.classifier.as_ref().map_or_else(
        || "jar".to_string(),
        |classifier| format!("{classifier}Jar"),
    )
}

fn explicit_source_build_number(coordinate: &MavenCoordinate) -> Option<String> {
    if coordinate.group != "mekanism" || coordinate.artifact != "Mekanism" {
        return None;
    }
    let (_, basic_version) = coordinate.version.rsplit_once('-')?;
    let (_, build_number) = basic_version.rsplit_once('.')?;
    build_number
        .chars()
        .all(|character| character.is_ascii_digit())
        .then(|| build_number.to_string())
}

fn source_build_checkout_key(remote_url: &str, commit: &str) -> String {
    ContentHash::from_bytes(
        format!("{remote_url}\n{commit}").as_bytes(),
        ContentHashAlgorithm::Blake3,
    )
    .hex()
}

fn materialize_source_build(
    cancellation_token: &CancellationToken,
    remote_url: &str,
    commit: &str,
    source_build: &SourceBuildProvenance,
    checkout_dir: &Path,
) -> eyre::Result<()> {
    match source_build.build_system {
        SourceBuildSystem::GradleWrapper => materialize_gradle_wrapper_source_build(
            cancellation_token,
            remote_url,
            commit,
            source_build,
            checkout_dir,
        ),
    }
}

fn materialize_gradle_wrapper_source_build(
    cancellation_token: &CancellationToken,
    remote_url: &str,
    commit: &str,
    source_build: &SourceBuildProvenance,
    checkout_dir: &Path,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    if source_build.tasks.is_empty() {
        eyre::bail!("Source build for {remote_url}@{commit} has no Gradle tasks");
    }
    prepare_source_build_checkout(cancellation_token, remote_url, commit, checkout_dir)?;
    cancellation_token.bail_if_cancelled()?;
    let wrapper = gradle_wrapper_path(checkout_dir)?;
    tracing::info!(
        remote = remote_url,
        commit,
        checkout = %checkout_dir.display(),
        tasks = ?source_build.tasks,
        output = %source_build.output_path.display(),
        "running source build"
    );
    let mut command = Command::new(wrapper);
    command
        .current_dir(checkout_dir)
        .arg("--no-daemon")
        .args(&source_build.tasks);
    for (key, value) in &source_build.environment {
        command.env(key, value);
    }
    run_source_build_process(
        cancellation_token,
        &mut command,
        "source-build-gradle-wrapper",
    )?;
    Ok(())
}

fn prepare_source_build_checkout(
    cancellation_token: &CancellationToken,
    remote_url: &str,
    commit: &str,
    checkout_dir: &Path,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    if !checkout_dir.join(".git").is_dir() {
        if checkout_dir.exists() {
            eyre::bail!(
                "Source build checkout path exists but is not a Git checkout: {}",
                checkout_dir.display()
            );
        }
        if let Some(parent) = checkout_dir.parent() {
            fs::create_dir_all(parent)?;
        }
        let mut clone = Command::new("git");
        clone
            .arg("-c")
            .arg("core.longpaths=true")
            .arg("clone")
            .arg("--no-checkout")
            .arg(remote_url)
            .arg(checkout_dir);
        run_source_build_process(cancellation_token, &mut clone, "source-build-git-clone")?;
    }

    cancellation_token.bail_if_cancelled()?;
    let mut longpaths = Command::new("git");
    longpaths
        .arg("-C")
        .arg(checkout_dir)
        .arg("config")
        .arg("core.longpaths")
        .arg("true");
    run_source_build_process(
        cancellation_token,
        &mut longpaths,
        "source-build-git-config-longpaths",
    )?;

    cancellation_token.bail_if_cancelled()?;
    let mut fetch = Command::new("git");
    fetch
        .arg("-C")
        .arg(checkout_dir)
        .arg("fetch")
        .arg("origin")
        .arg(commit);
    run_source_build_process(cancellation_token, &mut fetch, "source-build-git-fetch")?;

    cancellation_token.bail_if_cancelled()?;
    let mut checkout = Command::new("git");
    checkout
        .arg("-C")
        .arg(checkout_dir)
        .arg("checkout")
        .arg("--detach")
        .arg(commit);
    run_source_build_process(
        cancellation_token,
        &mut checkout,
        "source-build-git-checkout",
    )?;
    Ok(())
}

fn gradle_wrapper_path(checkout_dir: &Path) -> eyre::Result<PathBuf> {
    #[cfg(windows)]
    let wrapper = checkout_dir.join("gradlew.bat");
    #[cfg(not(windows))]
    let wrapper = checkout_dir.join("gradlew");

    if !wrapper.is_file() {
        eyre::bail!(
            "Source build checkout {} does not contain {}",
            checkout_dir.display(),
            wrapper
                .file_name()
                .and_then(std::ffi::OsStr::to_str)
                .unwrap_or("gradlew")
        );
    }
    Ok(wrapper)
}

fn run_source_build_process(
    cancellation_token: &CancellationToken,
    command: &mut Command,
    process_name: &str,
) -> eyre::Result<()> {
    let output = run_command_capture_output(cancellation_token, command, process_name)?;
    if !output.stdout.is_empty() {
        tracing::debug!(
            process = process_name,
            stream = "stdout",
            "{}",
            String::from_utf8_lossy(&output.stdout)
        );
    }
    if !output.stderr.is_empty() {
        tracing::debug!(
            process = process_name,
            stream = "stderr",
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
    }
    if output.cancelled {
        eyre::bail!("{process_name} was cancelled");
    }
    if !output.status.success() {
        eyre::bail!(
            "{process_name} failed with status {}\nstdout:\n{}\nstderr:\n{}",
            output.status,
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
    }
    Ok(())
}

fn source_relative_path(
    original_path: Option<&Path>,
    source_git: Option<&SourceGitProvenance>,
) -> Option<PathBuf> {
    let original_path = original_path?;
    let source_root = &source_git?.root;
    let canonical_original = dunce::canonicalize(original_path).ok();
    let canonical_root = dunce::canonicalize(source_root).ok();
    if let (Some(canonical_original), Some(canonical_root)) = (&canonical_original, &canonical_root)
        && let Ok(relative) = canonical_original.strip_prefix(canonical_root)
    {
        return Some(relative.to_path_buf());
    }
    original_path
        .strip_prefix(source_root)
        .ok()
        .map(Path::to_path_buf)
}

fn artifact_provenance_path(path: &Path) -> eyre::Result<PathBuf> {
    let file_name = path
        .file_name()
        .and_then(std::ffi::OsStr::to_str)
        .ok_or_else(|| eyre::eyre!("Path has no filename: {}", path.display()))?;
    Ok(path.with_file_name(format!("{file_name}.sfm-provenance.json")))
}

#[instrument(level = "debug", skip_all)]
fn read_artifact_provenance(path: &Path) -> eyre::Result<Option<ArtifactProvenance>> {
    let provenance_path = artifact_provenance_path(path)?;
    if !provenance_path.is_file() {
        return Ok(None);
    }
    let content = fs::read_to_string(&provenance_path)
        .wrap_err_with(|| format!("Failed to read {}", provenance_path.display()))?;
    let provenance = facet_json::from_str(&content)
        .wrap_err_with(|| format!("Failed to parse {}", provenance_path.display()))?;
    Ok(Some(provenance))
}

fn write_artifact_provenance(path: &Path, provenance: &ArtifactProvenance) -> eyre::Result<()> {
    let provenance_path = artifact_provenance_path(path)?;
    if let Some(parent) = provenance_path.parent() {
        fs::create_dir_all(parent)?;
    }
    let content = facet_json::to_string_pretty(provenance)
        .wrap_err("Failed to encode artifact provenance")?;
    fs::write(&provenance_path, content)
        .wrap_err_with(|| format!("Failed to write {}", provenance_path.display()))
}

fn read_json_file<T>(path: &Path) -> eyre::Result<T>
where
    T: Facet<'static>,
{
    let content = fs::read_to_string(path)
        .wrap_err_with(|| format!("Failed to read JSON file: {}", path.display()))?;
    facet_json::from_str(&content).wrap_err_with(|| format!("Failed to parse {}", path.display()))
}

fn read_zip_json_entry<T>(path: &Path, entry_name: &str) -> eyre::Result<T>
where
    T: Facet<'static>,
{
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", path.display()))?;
    let mut entry = archive
        .by_name(entry_name)
        .wrap_err_with(|| format!("Archive {} missing {entry_name}", path.display()))?;
    let mut content = String::new();
    entry
        .read_to_string(&mut content)
        .wrap_err_with(|| format!("Failed to read {entry_name} from {}", path.display()))?;
    facet_json::from_str(&content)
        .wrap_err_with(|| format!("Failed to parse {entry_name} from {}", path.display()))
}

fn download_to_path(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
    path: &Path,
) -> eyre::Result<()> {
    download_to_path_overwrite(cancellation_token, client, url, path, false)
}

fn download_to_path_overwrite_with_expected_hash(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
    path: &Path,
    overwrite: bool,
    expected_hash: &ContentHash,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let _lock = acquire_artifact_path_lock(path)?;
    download_to_path_overwrite_locked(
        cancellation_token,
        client,
        url,
        path,
        overwrite,
        Some(expected_hash),
    )
}

fn download_to_path_overwrite(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
    path: &Path,
    overwrite: bool,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let _lock = acquire_artifact_path_lock(path)?;
    download_to_path_overwrite_locked(cancellation_token, client, url, path, overwrite, None)
}

fn download_to_path_overwrite_locked(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
    path: &Path,
    overwrite: bool,
    expected_hash: Option<&ContentHash>,
) -> eyre::Result<()> {
    #[cfg(feature = "tracing_detailed")]
    let _span = tracing::debug_span!(
        "download_to_path",
        url,
        path = %path.display(),
        overwrite,
        expected_hash = expected_hash.map(ToString::to_string),
    )
    .entered();
    cancellation_token.bail_if_cancelled()?;
    prepare_existing_artifact_for_reuse(path, expected_hash)?;

    if path.is_file() && !overwrite {
        tracing::debug!(
            path = %path.display(),
            url,
            "download cache hit"
        );
        return Ok(());
    }
    if path.is_file()
        && expected_hash
            .is_some_and(|expected| existing_file_matches_hash(path, expected).unwrap_or(false))
    {
        tracing::debug!(
            path = %path.display(),
            url,
            "download cache hit after lock wait"
        );
        return Ok(());
    }

    tracing::info!(
        path = %path.display(),
        url,
        overwrite,
        "download cache miss"
    );
    let mut last_error = None;
    for attempt in 1..=DOWNLOAD_RETRY_ATTEMPTS {
        cancellation_token.bail_if_cancelled()?;
        match download_to_path_once(cancellation_token, client, url, path, expected_hash) {
            Ok(()) => {
                remove_bad_artifacts_for(path)?;
                return Ok(());
            }
            Err(error) => {
                if cancellation_token.is_cancelled() {
                    return Err(error);
                }
                tracing::warn!(
                    path = %path.display(),
                    url,
                    attempt,
                    attempts = DOWNLOAD_RETRY_ATTEMPTS,
                    error = %error,
                    "download attempt failed"
                );
                last_error = Some(error);
            }
        }
    }
    Err(last_error.unwrap_or_else(|| eyre::eyre!("Download failed for {url}")))
}

fn download_to_path_once(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
    path: &Path,
    expected_hash: Option<&ContentHash>,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Path has no parent: {}", path.display()))?;
    fs::create_dir_all(parent)?;
    let response = client
        .get(url)
        .send()
        .wrap_err_with(|| format!("Failed to request {url}"))?;
    cancellation_token.bail_if_cancelled()?;
    if !response.status().is_success() {
        eyre::bail!("Failed to download {url}: HTTP {}", response.status());
    }
    let bytes = response
        .bytes()
        .wrap_err_with(|| format!("Failed to read response body for {url}"))?;
    cancellation_token.bail_if_cancelled()?;
    let temporary_path = write_unique_temp_file(path, bytes.as_ref())?;
    if let Some(expected_hash) = expected_hash {
        let actual_hash = ContentHash::from_path(&temporary_path, expected_hash.algorithm)?;
        if actual_hash != *expected_hash {
            let _ = fs::remove_file(&temporary_path);
            eyre::bail!(
                "Downloaded {} with content hash {}, expected {}",
                path.display(),
                actual_hash,
                expected_hash
            );
        }
    }
    replace_artifact_file(&temporary_path, path)
}

#[cfg(test)]
fn copy_file_to_path_checked(
    source: &Path,
    path: &Path,
    expected_hash: Option<&ContentHash>,
) -> eyre::Result<()> {
    let _lock = acquire_artifact_path_lock(path)?;
    copy_file_to_path_checked_locked(source, path, expected_hash)
}

fn copy_file_to_path_checked_locked(
    source: &Path,
    path: &Path,
    expected_hash: Option<&ContentHash>,
) -> eyre::Result<()> {
    prepare_existing_artifact_for_reuse(path, expected_hash)?;
    if path.is_file()
        && expected_hash
            .is_some_and(|expected| existing_file_matches_hash(path, expected).unwrap_or(false))
    {
        return Ok(());
    }

    let bytes =
        fs::read(source).wrap_err_with(|| format!("Failed to read {}", source.display()))?;
    let temporary_path = write_unique_temp_file(path, &bytes)?;
    if let Some(expected_hash) = expected_hash {
        let actual_hash = ContentHash::from_path(&temporary_path, expected_hash.algorithm)?;
        if actual_hash != *expected_hash {
            let _ = fs::remove_file(&temporary_path);
            eyre::bail!(
                "Copied local artifact {} with content hash {}, expected {}",
                source.display(),
                actual_hash,
                expected_hash
            );
        }
    }
    replace_artifact_file(&temporary_path, path)?;
    remove_bad_artifacts_for(path)?;
    Ok(())
}

fn acquire_artifact_path_lock(path: &Path) -> eyre::Result<ArtifactLock> {
    ArtifactLock::acquire(artifact_lock_path(path)?, path.display().to_string())
}

fn acquire_artifact_path_read_lock(path: &Path) -> eyre::Result<ArtifactReadLock> {
    ArtifactReadLock::acquire(artifact_lock_path(path)?, path.display().to_string())
}

fn artifact_lock_path(path: &Path) -> eyre::Result<PathBuf> {
    let file_name = artifact_file_name(path)?;
    Ok(path.with_file_name(format!("{file_name}.lock")))
}

fn prepare_existing_artifact_for_reuse(
    path: &Path,
    expected_hash: Option<&ContentHash>,
) -> eyre::Result<()> {
    let Some(expected_hash) = expected_hash else {
        return Ok(());
    };
    if !path.is_file() {
        return Ok(());
    }
    let actual_hash = ContentHash::from_path(path, expected_hash.algorithm)?;
    if actual_hash == *expected_hash {
        return Ok(());
    }
    quarantine_bad_artifact(path, &actual_hash, expected_hash)
}

#[instrument(level = "debug", skip_all)]
fn existing_file_matches_hash(path: &Path, expected_hash: &ContentHash) -> eyre::Result<bool> {
    Ok(path.is_file() && ContentHash::from_path(path, expected_hash.algorithm)? == *expected_hash)
}

fn quarantine_bad_artifact(
    path: &Path,
    actual_hash: &ContentHash,
    expected_hash: &ContentHash,
) -> eyre::Result<()> {
    let bad_path = unique_sibling_path(path, &format!("bad.{}", actual_hash.hex()))?;
    tracing::warn!(
        path = %path.display(),
        bad_path = %bad_path.display(),
        actual_hash = %actual_hash,
        expected_hash = %expected_hash,
        "quarantining corrupt artifact"
    );
    fs::rename(path, &bad_path).wrap_err_with(|| {
        format!(
            "Failed to quarantine corrupt artifact {} as {}",
            path.display(),
            bad_path.display()
        )
    })
}

fn remove_bad_artifacts_for(path: &Path) -> eyre::Result<()> {
    let Some(parent) = path.parent() else {
        return Ok(());
    };
    if !parent.is_dir() {
        return Ok(());
    }
    let file_name = artifact_file_name(path)?;
    let bad_prefix = format!("{file_name}.bad.");
    for entry in
        fs::read_dir(parent).wrap_err_with(|| format!("Failed to read {}", parent.display()))?
    {
        let entry = entry?;
        let entry_path = entry.path();
        let is_bad_artifact = entry_path
            .file_name()
            .and_then(std::ffi::OsStr::to_str)
            .is_some_and(|name| name.starts_with(&bad_prefix));
        if is_bad_artifact {
            fs::remove_file(&entry_path)
                .wrap_err_with(|| format!("Failed to remove {}", entry_path.display()))?;
        }
    }
    Ok(())
}

fn write_unique_temp_file(path: &Path, bytes: &[u8]) -> eyre::Result<PathBuf> {
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Path has no parent: {}", path.display()))?;
    fs::create_dir_all(parent)?;
    for _ in 0..100 {
        let temporary_path = unique_sibling_path(path, "tmp")?;
        match OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&temporary_path)
        {
            Ok(mut file) => {
                file.write_all(bytes)
                    .wrap_err_with(|| format!("Failed to write {}", temporary_path.display()))?;
                file.sync_all()
                    .wrap_err_with(|| format!("Failed to sync {}", temporary_path.display()))?;
                return Ok(temporary_path);
            }
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
            Err(error) => {
                return Err(error)
                    .wrap_err_with(|| format!("Failed to create {}", temporary_path.display()));
            }
        }
    }
    eyre::bail!(
        "Could not allocate a unique temporary file for {}",
        path.display()
    )
}

fn replace_artifact_file(temporary_path: &Path, path: &Path) -> eyre::Result<()> {
    if path.exists() {
        fs::remove_file(path).wrap_err_with(|| format!("Failed to replace {}", path.display()))?;
    }
    fs::rename(temporary_path, path).wrap_err_with(|| {
        format!(
            "Failed to move downloaded file {} to {}",
            temporary_path.display(),
            path.display()
        )
    })?;
    Ok(())
}

fn unique_sibling_path(path: &Path, kind: &str) -> eyre::Result<PathBuf> {
    let file_name = artifact_file_name(path)?;
    Ok(path.with_file_name(format!(
        "{file_name}.{kind}.{}.{}",
        std::process::id(),
        unique_file_nonce()
    )))
}

fn unique_file_nonce() -> u128 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos()
}

fn artifact_file_name(path: &Path) -> eyre::Result<&str> {
    path.file_name()
        .and_then(std::ffi::OsStr::to_str)
        .ok_or_else(|| eyre::eyre!("Path has no filename: {}", path.display()))
}

fn download_text_optional(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
) -> eyre::Result<String> {
    cancellation_token.bail_if_cancelled()?;
    let response = client
        .get(url)
        .send()
        .wrap_err_with(|| format!("Failed to request {url}"))?;
    cancellation_token.bail_if_cancelled()?;
    if response.status() == StatusCode::NOT_FOUND {
        eyre::bail!("not found");
    }
    if !response.status().is_success() {
        eyre::bail!("HTTP {}", response.status());
    }
    let text = response
        .text()
        .wrap_err_with(|| format!("Failed to read response body for {url}"))?;
    cancellation_token.bail_if_cancelled()?;
    Ok(text)
}

fn remote_exists(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
) -> eyre::Result<bool> {
    #[cfg(feature = "tracing_detailed")]
    let _span = tracing::debug_span!("remote_exists", url).entered();
    cancellation_token.bail_if_cancelled()?;
    let response = client
        .head(url)
        .send()
        .wrap_err_with(|| format!("Failed to request {url}"))?;
    cancellation_token.bail_if_cancelled()?;
    if response.status() == StatusCode::METHOD_NOT_ALLOWED {
        return match download_text_optional(cancellation_token, client, url) {
            Ok(_) => Ok(true),
            Err(error) if cancellation_token.is_cancelled() => Err(error),
            Err(_) => Ok(false),
        };
    }
    Ok(response.status().is_success())
}

fn parse_maven_versions(metadata: &str) -> Vec<String> {
    let mut versions = Vec::new();
    let mut remaining = metadata;

    while let Some(start) = remaining.find("<version>") {
        let after_start = &remaining[start + "<version>".len()..];
        let Some(end) = after_start.find("</version>") else {
            break;
        };
        versions.push(after_start[..end].trim().to_string());
        remaining = &after_start[end + "</version>".len()..];
    }

    versions
}

fn compare_version_text(left: &str, right: &str) -> Ordering {
    let left_parts = split_version_parts(left);
    let right_parts = split_version_parts(right);
    left_parts.cmp(&right_parts)
}

fn split_version_parts(version: &str) -> Vec<VersionPart> {
    let mut parts = Vec::new();
    let mut current = String::new();
    let mut digit_mode = None;

    for character in version.chars() {
        let is_digit = character.is_ascii_digit();
        if digit_mode.is_some_and(|mode| mode != is_digit) && !current.is_empty() {
            parts.push(VersionPart::from_text(&current));
            current.clear();
        }
        digit_mode = Some(is_digit);
        if character == '.' || character == '-' || character == '_' || character == '+' {
            if !current.is_empty() {
                parts.push(VersionPart::from_text(&current));
                current.clear();
            }
            digit_mode = None;
        } else {
            current.push(character);
        }
    }

    if !current.is_empty() {
        parts.push(VersionPart::from_text(&current));
    }

    parts
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
enum VersionPart {
    Number(u64),
    Text(String),
}

impl VersionPart {
    fn from_text(text: &str) -> Self {
        text.parse::<u64>()
            .map_or_else(|_| Self::Text(text.to_string()), Self::Number)
    }
}

#[cfg(test)]
#[path = "engine_tests.rs"]
mod tests;
