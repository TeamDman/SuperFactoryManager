// todo(2026-06-16) file very large
use super::ArtifactAuditOptions;
use super::ArtifactId;
use super::ArtifactPurpose;
use super::BuildMode;
use super::BuildOptions;
use super::CompareOptions;
use super::DependencyAddOptions;
use super::RunKind;
use super::RunOptions;
use super::RunTestAction;
use super::RunTestOptions;
use super::SourceIdentifierMappingPath;
use super::SourceJarPath;
use super::SourceOutputCacheRoot;
use super::SourceOutputLayout;
use super::SourceOutputOptions;
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
use crate::branch_targets::select_single_worktree_target;
use crate::cancellation::CancellationToken;
use crate::colour::stable_color;
use crate::paths::CACHE_DIR;
use crate::terminal_output::stdout_line;
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
    run_options: &RunOptions,
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
    } = execute_run_targets(options, kind, run_options, targets, cancellation_token)?;
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

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %options.build.branch,
        layout = %options.layout,
        refresh = options.build.refresh,
        explain_rebuild = options.build.explain_rebuild,
        allow_local_artifact_cache = options.build.allow_local_artifact_cache,
        require_portable_artifacts = options.build.require_portable_artifacts,
        error_action = %options.build.error_action,
        parallelism = %options.build.parallelism,
    )
)]
pub(crate) fn invoke_source_outputs(
    options: &SourceOutputOptions,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    if options.build.dry_run {
        eyre::bail!("jar sources does not support --dry-run because it must materialize outputs");
    }

    cancellation_token.bail_if_cancelled()?;
    let targets = resolve_build_targets(&options.build)?;
    cancellation_token.bail_if_cancelled()?;
    let target_count = targets.len();
    let TargetExecutionSummary {
        plans,
        failures,
        reports,
    } = execute_source_output_targets(&options.build, targets, cancellation_token)?;
    cancellation_token.bail_if_cancelled()?;

    write_requested_plan_outputs(&plans, options.build.plan_json.as_deref())?;
    write_source_outputs(&plans, options.layout)?;
    finish_target_summary(
        "jar sources",
        target_count,
        plans.len(),
        &failures,
        &reports,
    )
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
    run_options: &RunOptions,
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
            execute_run_target(options, kind, run_options, target, cancellation_token)
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

fn execute_source_output_targets(
    options: &BuildOptions,
    targets: Vec<WorktreeTarget>,
    cancellation_token: &CancellationToken,
) -> eyre::Result<TargetExecutionSummary> {
    execute_targets(
        options,
        targets,
        "sfm_source_output_target",
        cancellation_token,
        |options, target, cancellation_token| {
            let _target_span = tracing::info_span!(
                "sfm_source_output_target",
                branch = %target.branch,
                worktree = %target.worktree_path.display(),
            )
            .entered();
            execute_source_output_target(options, target, cancellation_token)
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
    run_options: &RunOptions,
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
    if run_options.game_test_bisect.is_some() {
        execute_game_test_bisect(
            &plan,
            kind,
            run_options,
            options.dry_run,
            cancellation_token,
        )?;
    } else {
        execute_run(
            &plan,
            kind,
            run_options,
            options.dry_run,
            cancellation_token,
        )?;
    }
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

fn execute_source_output_target(
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
    execute_build(
        &plan,
        options.explain_rebuild,
        BuildTarget::SourceOutputs,
        cancellation_token,
    )?;
    cancellation_token.bail_if_cancelled()?;
    write_artifact_lockfile(&plan)?;
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

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %options.branch,
        coordinate = %options.coordinate,
        weak_mod_metadata = options.weak_mod_metadata,
    )
)]
pub(crate) fn invoke_dependency_add(
    options: &DependencyAddOptions,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let target = select_single_worktree_target(&options.branch)?;
    let worktree_path = target.worktree_path.as_path().to_path_buf();
    let minecraft_dir = worktree_path.join("platform").join("minecraft");
    let properties_path = minecraft_dir.join("gradle.properties");
    let properties = read_properties(&properties_path)?;
    let minecraft_version = required_property(&properties, "minecraft_version")?;
    let lockfile_path = minecraft_dir.join("sfm-toolchain.lock.json");
    let common_cache_dir = common_toolchain_cache_dir();
    let mut lockfile = read_optional_artifact_lockfile(&lockfile_path, minecraft_version)?
        .ok_or_else(|| eyre::eyre!("Artifact lockfile is missing: {}", lockfile_path.display()))?;

    let coordinate = MavenCoordinate::parse(&options.coordinate)?;
    let coordinate_text = coordinate.to_string();
    let Some(artifact_index) = lockfile
        .artifacts
        .iter()
        .position(|artifact| artifact.coordinate.as_deref() == Some(coordinate_text.as_str()))
    else {
        eyre::bail!(
            "Artifact {} is not present in {}",
            coordinate_text,
            lockfile_path.display()
        );
    };

    let artifact_path = current_locked_artifact_path(
        &lockfile.artifacts[artifact_index],
        &minecraft_dir,
        &common_cache_dir,
    )?;
    let hash = ContentHash::from_path(&artifact_path, ContentHashAlgorithm::Blake3)?;
    let weak = if options.weak_mod_metadata {
        Some(weak_mod_metadata_from_artifact(&artifact_path, options)?)
    } else {
        None
    };

    let artifact = &mut lockfile.artifacts[artifact_index];
    let old_hash = artifact.hash;
    artifact.hash = hash;
    artifact.weak = weak;
    let weak = artifact.weak.is_some();

    fs::write(&lockfile_path, facet_json::to_string_pretty(&lockfile)?)
        .wrap_err_with(|| format!("Failed to write {}", lockfile_path.display()))?;
    tracing::info!(
        branch = %target.branch,
        coordinate = %coordinate_text,
        artifact = %artifact_path.display(),
        old_hash = %old_hash,
        new_hash = %hash,
        weak,
        "dependency lock entry updated"
    );
    Ok(())
}

fn current_locked_artifact_path(
    artifact: &ArtifactLockEntry,
    minecraft_dir: &Path,
    common_cache_dir: &Path,
) -> eyre::Result<PathBuf> {
    let cache_path =
        resolve_locked_artifact_path(&artifact.cache_path, minecraft_dir, common_cache_dir);
    if cache_path.is_file() {
        return Ok(cache_path);
    }
    if let (Some(source_git), Some(source_build)) = (&artifact.source_git, &artifact.source_build) {
        let source_root =
            resolve_locked_artifact_path(&source_git.root, minecraft_dir, common_cache_dir);
        let source_output = source_root.join(&source_build.output_path);
        if source_output.is_file() {
            return Ok(source_output);
        }
    }
    eyre::bail!(
        "Locked artifact {} is missing and no source-build output is available",
        artifact_label(artifact)
    )
}

fn weak_mod_metadata_from_artifact(
    artifact_path: &Path,
    options: &DependencyAddOptions,
) -> eyre::Result<WeakArtifactValidation> {
    let metadata_path = match &options.metadata_path {
        Some(path) => path.clone(),
        None => detect_mod_metadata_path(artifact_path)?,
    };
    let metadata = read_zip_text_entry(artifact_path, &metadata_path)?;
    let mod_id = options
        .mod_id
        .clone()
        .or_else(|| metadata_assignment(&metadata, "modId"))
        .ok_or_else(|| eyre::eyre!("{} has no modId assignment", metadata_path.display()))?;
    let version = options
        .version
        .clone()
        .or_else(|| metadata_assignment(&metadata, "version"))
        .ok_or_else(|| eyre::eyre!("{} has no version assignment", metadata_path.display()))?;
    let weak = WeakArtifactValidation {
        metadata_path,
        mod_id,
        version,
    };
    validate_weak_mod_metadata(artifact_path, &weak)?;
    Ok(weak)
}

fn detect_mod_metadata_path(artifact_path: &Path) -> eyre::Result<PathBuf> {
    for metadata_path in ["META-INF/mods.toml", "META-INF/neoforge.mods.toml"] {
        let path = PathBuf::from(metadata_path);
        if read_zip_text_entry(artifact_path, &path).is_ok() {
            return Ok(path);
        }
    }
    eyre::bail!(
        "Could not find META-INF/mods.toml or META-INF/neoforge.mods.toml in {}",
        artifact_path.display()
    )
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
    let artifact_path = if cache_path.is_file() {
        cache_path
    } else if let Ok(source_output) =
        current_locked_artifact_path(artifact, minecraft_dir, common_cache_dir)
    {
        source_output
    } else {
        report.push_error(
            ArtifactAuditIssueKind::ArtifactMissing,
            artifact.coordinate.clone(),
            Some(artifact.source.clone()),
            &cache_path,
            format!("Locked artifact is missing: {}", cache_path.display()),
        );
        return Ok(());
    };

    let _cache_read_lock = acquire_artifact_path_read_lock(&artifact_path)?;
    let actual_hash = ContentHash::from_path(&artifact_path, artifact.hash.algorithm)?;
    if actual_hash != artifact.hash {
        if let Some(weak) = artifact.weak.as_ref() {
            validate_weak_mod_metadata(&artifact_path, weak)?;
            report.push_warning(
                ArtifactAuditIssueKind::ArtifactHashMismatch,
                artifact.coordinate.clone(),
                Some(artifact.source.clone()),
                &artifact_path,
                format!(
                    "Weak artifact {} has content hash {}, but lockfile records {}; mod metadata matched {} {}",
                    artifact_path.display(),
                    actual_hash,
                    artifact.hash,
                    weak.mod_id,
                    weak.version
                ),
            );
        } else {
            report.push_error(
                ArtifactAuditIssueKind::ArtifactHashMismatch,
                artifact.coordinate.clone(),
                Some(artifact.source.clone()),
                &artifact_path,
                format!(
                    "Locked artifact {} has content hash {}, but lockfile requires {}",
                    artifact_path.display(),
                    actual_hash,
                    artifact.hash
                ),
            );
            return Ok(());
        }
    }
    report.verified_artifacts += 1;

    if let Some(provenance) = read_artifact_provenance(&artifact_path)? {
        audit_artifact_provenance_sidecar(report, artifact, &artifact_path, &provenance);
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

fn validate_locked_artifact_content(
    path: &Path,
    locked: &ArtifactLockEntry,
    actual_hash: ContentHash,
) -> eyre::Result<()> {
    if actual_hash == locked.hash {
        return Ok(());
    }
    if let Some(weak) = locked.weak.as_ref() {
        validate_weak_mod_metadata(path, weak)?;
        tracing::warn!(
            artifact = %path.display(),
            actual_hash = %actual_hash,
            locked_hash = %locked.hash,
            mod_id = weak.mod_id,
            version = weak.version,
            "weak artifact hash mismatch accepted after mod metadata validation"
        );
        return Ok(());
    }

    eyre::bail!(
        "Artifact {} resolved with content hash {}, but sfm-toolchain.lock.json requires {}",
        locked.coordinate.as_deref().unwrap_or("<unknown>"),
        actual_hash,
        locked.hash
    )
}

fn validate_weak_mod_metadata(path: &Path, weak: &WeakArtifactValidation) -> eyre::Result<()> {
    let metadata = read_zip_text_entry(path, &weak.metadata_path)?;
    let actual_mod_id = metadata_assignment(&metadata, "modId")
        .ok_or_else(|| eyre::eyre!("{} has no modId assignment", weak.metadata_path.display()))?;
    let actual_version = metadata_assignment(&metadata, "version")
        .ok_or_else(|| eyre::eyre!("{} has no version assignment", weak.metadata_path.display()))?;

    if actual_mod_id != weak.mod_id || actual_version != weak.version {
        eyre::bail!(
            "Weak artifact {} metadata mismatch in {}: expected modId={} version={}, found modId={} version={}",
            path.display(),
            weak.metadata_path.display(),
            weak.mod_id,
            weak.version,
            actual_mod_id,
            actual_version
        );
    }
    Ok(())
}

fn read_zip_text_entry(path: &Path, entry_name: &Path) -> eyre::Result<String> {
    let entry_name = entry_name.to_string_lossy().replace('\\', "/");
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", path.display()))?;
    let mut entry = archive
        .by_name(&entry_name)
        .wrap_err_with(|| format!("Archive {} missing {entry_name}", path.display()))?;
    let mut content = String::new();
    entry
        .read_to_string(&mut content)
        .wrap_err_with(|| format!("Failed to read {entry_name} from {}", path.display()))?;
    Ok(content)
}

fn metadata_assignment(content: &str, key: &str) -> Option<String> {
    content.lines().find_map(|line| {
        let line = line.split('#').next()?.trim();
        let (left, right) = line.split_once('=')?;
        if left.trim() != key {
            return None;
        }
        Some(
            right
                .trim()
                .trim_matches('"')
                .trim_matches('\'')
                .to_string(),
        )
    })
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
        Self::from_message(target, format!("{error:?}"))
    }

    fn from_message(target: &WorktreeTarget, error: impl Into<String>) -> Self {
        Self {
            branch: target.branch.clone(),
            worktree_path: target.worktree_path.as_path().to_path_buf(),
            error: error.into(),
        }
    }
}

include!("engine_model.rs");
include!("engine_plan.rs");
include!("engine_run.rs");
include!("engine_execute.rs");
include!("engine_sources.rs");
include!("engine_mappings_compare_artifacts.rs");
