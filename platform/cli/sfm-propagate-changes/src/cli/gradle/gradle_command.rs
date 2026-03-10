use crate::cli::git::status::assert_worktrees_clean_or_autocommit_generated;
use crate::mc_version_filter::McVersionFilter;
use crate::paths::CACHE_DIR;
use crate::worktree::get_sorted_worktrees;
use chrono::Local;
use color_eyre::owo_colors::OwoColorize;
use eyre::Context;
use eyre::bail;
use facet::Facet;
use figue::{self as args};
use humansize::DECIMAL;
use humansize::format_size;
use std::ffi::OsStr;
use std::fmt::Write as _;
use std::fs;
use std::io::Write as _;
use std::mem;
use std::path::Path;
use std::path::PathBuf;
use std::process::ExitStatus;
use std::process::Stdio;
use std::time::Duration;
use std::time::Instant;
use tokio::fs::File;
use tokio::io::AsyncRead;
use tokio::io::AsyncReadExt;
use tokio::io::AsyncWriteExt;
use tokio::process::Command;
use tracing::debug;
use tracing::error;
use tracing::info;
use tracing::warn;

#[derive(Debug, Clone)]
struct TaskRun {
    task: GradleTask,
    state: TaskState,
}

#[derive(Debug, Clone)]
struct BranchRun {
    branch: String,
    tasks: Vec<TaskRun>,
}

#[derive(Debug, Clone)]
enum TaskState {
    Waiting,
    Running { start_time: Instant },
    Success { duration: Duration },
    Failed { duration: Duration },
    NotFound { reason: String },
    Skipped,
}

#[derive(Debug, Clone)]
enum GradleTask {
    RunData,
    RunGameTestServer,
    Other(String),
}

#[derive(Debug)]
struct TaskOutput {
    status: ExitStatus,
    stdout: String,
    stderr: String,
    duration: Duration,
    stdout_log_path: PathBuf,
    stderr_log_path: PathBuf,
}

#[derive(Debug)]
struct TaskError {
    message: String,
    output: Option<TaskOutput>,
    interrupted: bool,
}

#[derive(Debug, Copy, Clone, Eq, PartialEq)]
enum LogStreamKind {
    Stdout,
    Stderr,
}

#[derive(Debug, Clone)]
struct LogPair {
    relative_task_dir: PathBuf,
    stdout_log: PathBuf,
    stderr_log: PathBuf,
}

#[derive(Debug, Copy, Clone, Eq, PartialEq)]
enum LogModelState {
    Startup,
    Registration,
    Runtime,
    Exception,
    Terminal,
}

#[derive(Debug, Copy, Clone, Eq, PartialEq)]
enum LogEvent {
    StartupNoise,
    RegistrationMarker,
    RuntimeMarker,
    ExceptionHead,
    StackLine,
    BuildSuccess,
    BuildFailed,
    TestFailures,
    Signal,
    Other,
}

#[derive(Debug, Default)]
struct LogReduction {
    important_lines: Vec<String>,
    failed_tests: Vec<String>,
    failed_test_reasons: Vec<(String, String)>,
    tests_passed: bool,
    build_failed: bool,
}

impl GradleTask {
    fn from_input(input: &str) -> Self {
        match input.to_ascii_lowercase().as_str() {
            "rundata" => Self::RunData,
            "rungametestserver" | "rungametest" => Self::RunGameTestServer,
            _ => Self::Other(input.to_string()),
        }
    }

    fn as_gradle_arg(&self) -> &str {
        match self {
            Self::RunData => "runData",
            Self::RunGameTestServer => "runGameTestServer",
            Self::Other(task) => task,
        }
    }

    fn header_name(&self) -> String {
        self.as_gradle_arg().to_string()
    }

    fn needs_generated_preflight(&self) -> bool {
        matches!(self, Self::RunData)
    }

    fn is_success(&self, output: &TaskOutput) -> bool {
        let combined = format!("{}\n{}", output.stdout, output.stderr);

        match self {
            Self::RunData => {
                output.status.success()
                    || combined.contains("BUILD SUCCESSFUL")
                    || combined.contains("All providers took")
            }
            Self::RunGameTestServer => has_gametest_success(&combined),
            Self::Other(_) => output.status.success() || combined.contains("BUILD SUCCESSFUL"),
        }
    }
}

impl TaskState {
    fn plain_text(&self) -> String {
        match self {
            Self::Waiting => "waiting".to_string(),
            Self::Running { start_time } => {
                format!("running ({})", format_duration(start_time.elapsed()))
            }
            Self::Success { duration } => format_duration(*duration),
            Self::Failed { duration } => format!("failed ({})", format_duration(*duration)),
            Self::NotFound { reason } => format!("not found ({reason})"),
            Self::Skipped => "skipped".to_string(),
        }
    }

    fn colorized_text(&self) -> String {
        let text = self.plain_text();
        match self {
            Self::Waiting | Self::Skipped => text.dimmed().to_string(),
            Self::Running { .. } => text.yellow().bold().to_string(),
            Self::Success { .. } => text.green().bold().to_string(),
            Self::Failed { .. } => text.red().bold().to_string(),
            Self::NotFound { .. } => text.magenta().to_string(),
        }
    }
}

fn extract_failed_gametest_names(output: &str) -> Vec<String> {
    let mut names = Vec::new();
    let mut in_failed_section = false;

    for line in output.lines() {
        // Strip the log prefix, e.g. "[HH:MM:SS] [Server thread/INFO] [minecraft/GameTestServer]: "
        let content = if let Some(idx) = line.rfind("]: ") {
            &line[idx + 3..]
        } else {
            line
        };

        if content.contains("required tests failed :(") {
            in_failed_section = true;
            continue;
        }

        if in_failed_section {
            if content.contains("====") {
                break;
            }
            let stripped = content.trim();
            if let Some(name) = stripped.strip_prefix("- ") {
                names.push(name.trim().to_string());
            }
        }
    }

    names
}

fn extract_failed_gametest_reasons(output: &str) -> Vec<(String, String)> {
    let mut reasons = Vec::new();

    for line in output.lines() {
        let content = if let Some(idx) = line.rfind("]: ") {
            &line[idx + 3..]
        } else {
            line
        };

        let Some((test_name, tail)) = content.split_once(" failed at ") else {
            continue;
        };

        let Some((_, reason)) = tail.split_once("! ") else {
            continue;
        };

        let test_name = test_name.trim();
        let reason = reason.trim();
        if !test_name.is_empty() && !reason.is_empty() {
            reasons.push((test_name.to_string(), reason.to_string()));
        }
    }

    reasons
}

fn print_gametest_failures(failures: &[(String, Vec<(String, Option<String>)>)]) {
    if failures.is_empty() {
        return;
    }
    println!();
    println!("{}", "FAILED GAME TESTS".red().bold());
    for (branch, tests) in failures {
        for (name, reason) in tests {
            let detail = reason
                .as_deref()
                .map(|x| format!(" — {x}"))
                .unwrap_or_default();
            println!("  {}", format!("{branch}: {name}{detail}").red());
        }
    }
}

fn has_gametest_success(output: &str) -> bool {
    let prefix = "All ";
    let suffix = " required tests passed :)";

    for (start, _) in output.match_indices(prefix) {
        let after_prefix = &output[start + prefix.len()..];
        if let Some(end) = after_prefix.find(suffix) {
            let number = &after_prefix[..end];
            if !number.is_empty() && number.chars().all(|c| c.is_ascii_digit()) {
                return true;
            }
        }
    }

    false
}

fn format_duration(duration: Duration) -> String {
    let secs = duration.as_secs();
    if secs >= 60 {
        let mins = secs / 60;
        let remaining_secs = secs % 60;
        format!("{mins}m {remaining_secs:02}s")
    } else {
        format!("{}.{:01}s", secs, duration.subsec_millis() / 100)
    }
}

fn format_log_summary(label: &str, output: &str, log_path: &Path) -> String {
    let lines = output.lines().count();
    let bytes = fs::metadata(log_path)
        .map(|meta| meta.len())
        .unwrap_or_else(|_| u64::try_from(output.len()).unwrap_or(u64::MAX));

    format!("  {label}: {lines} lines ({})", format_size(bytes, DECIMAL))
}

fn format_report(branches: &[BranchRun], tasks: &[GradleTask]) -> String {
    let mut widths = Vec::with_capacity(tasks.len() + 1);
    widths.push(
        std::iter::once("mc version".len())
            .chain(branches.iter().map(|b| b.branch.len()))
            .max()
            .unwrap_or("mc version".len()),
    );

    for (task_index, task) in tasks.iter().enumerate() {
        let header = task.header_name();
        let max_cell = branches
            .iter()
            .filter_map(|b| b.tasks.get(task_index))
            .map(|t| t.state.plain_text().len())
            .max()
            .unwrap_or(0);
        widths.push(header.len().max(max_cell));
    }

    let mut out = String::new();
    let _ = writeln!(out, "{}", build_header_row(tasks, &widths).cyan().bold());
    for branch in branches {
        let _ = writeln!(out, "{}", build_branch_row(branch, &widths));
    }
    out
}

fn build_header_row(tasks: &[GradleTask], widths: &[usize]) -> String {
    let mut cells = Vec::with_capacity(tasks.len() + 1);
    cells.push(format!("{:width$}", "mc version", width = widths[0]));

    for (idx, task) in tasks.iter().enumerate() {
        cells.push(format!(
            "{:width$}",
            task.header_name(),
            width = widths[idx + 1]
        ));
    }

    cells.join(" | ")
}

fn build_branch_row(branch: &BranchRun, widths: &[usize]) -> String {
    let mut row = format!("{:width$}", branch.branch, width = widths[0]);

    for (idx, task) in branch.tasks.iter().enumerate() {
        row.push_str(" | ");

        let plain = task.state.plain_text();
        let styled = task.state.colorized_text();
        let padding = widths[idx + 1].saturating_sub(plain.len());

        row.push_str(&styled);
        if padding > 0 {
            row.push_str(&" ".repeat(padding));
        }
    }

    row
}

fn print_report_to_stderr(branches: &[BranchRun], tasks: &[GradleTask]) {
    eprintln!();
    eprintln!("{}", format_report(branches, tasks));
}

fn print_report_to_stdout(branches: &[BranchRun], tasks: &[GradleTask]) {
    println!();
    println!("{}", format_report(branches, tasks));
}

fn print_stream_path(label: &str, path: &Path) {
    println!("{}", format!("  {label}: {}", path.display()).dimmed());
}

fn sanitize_for_path(input: &str) -> String {
    let sanitized: String = input
        .chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() || c == '.' || c == '-' || c == '_' {
                c
            } else {
                '_'
            }
        })
        .collect();

    let trimmed = sanitized.trim_matches('_');
    if trimmed.is_empty() {
        "unnamed".to_string()
    } else {
        trimmed.to_string()
    }
}

fn create_gradle_run_log_dir(tasks: &[String]) -> eyre::Result<PathBuf> {
    let timestamp = Local::now().format("%Y-%m-%d_%H-%M-%S");
    let task_segment = tasks
        .iter()
        .map(|t| sanitize_for_path(t))
        .collect::<Vec<_>>()
        .join("+");
    let task_segment = if task_segment.len() > 80 {
        task_segment[..80].to_string()
    } else {
        task_segment
    };

    let run_dir_name = if task_segment.is_empty() {
        format!("gradle_{timestamp}")
    } else {
        format!("gradle_{timestamp}_{task_segment}")
    };

    let run_dir = CACHE_DIR.0.join("gradle-runs").join(run_dir_name);
    fs::create_dir_all(&run_dir).wrap_err_with(|| {
        format!(
            "Failed to create gradle run log directory: {}",
            run_dir.display()
        )
    })?;
    Ok(run_dir)
}

fn gradle_runs_dir() -> PathBuf {
    CACHE_DIR.0.join("gradle-runs")
}

fn latest_gradle_run_dir() -> eyre::Result<PathBuf> {
    let runs_dir = gradle_runs_dir();

    if !runs_dir.exists() {
        bail!("No gradle run logs exist yet: {}", runs_dir.display());
    }

    let mut run_dirs = Vec::new();
    for entry in fs::read_dir(&runs_dir).wrap_err_with(|| {
        format!(
            "Failed to read gradle runs directory: {}",
            runs_dir.display()
        )
    })? {
        let entry = entry.wrap_err("Failed to read gradle runs directory entry")?;
        let path = entry.path();
        let file_type = entry
            .file_type()
            .wrap_err_with(|| format!("Failed to inspect: {}", path.display()))?;
        if !file_type.is_dir() {
            continue;
        }
        let modified = entry.metadata().and_then(|meta| meta.modified()).ok();
        run_dirs.push((modified, path));
    }

    run_dirs.sort_by(|left, right| right.0.cmp(&left.0).then_with(|| right.1.cmp(&left.1)));

    run_dirs
        .into_iter()
        .next()
        .map(|(_, path)| path)
        .ok_or_else(|| eyre::eyre!("No gradle run directories found in {}", runs_dir.display()))
}

fn collect_named_logs(root: &Path, file_name: &str) -> eyre::Result<Vec<PathBuf>> {
    let mut logs = Vec::new();
    let mut pending = vec![root.to_path_buf()];

    while let Some(dir) = pending.pop() {
        for entry in fs::read_dir(&dir)
            .wrap_err_with(|| format!("Failed to read directory: {}", dir.display()))?
        {
            let entry = entry.wrap_err("Failed to read directory entry")?;
            let path = entry.path();
            let file_type = entry
                .file_type()
                .wrap_err_with(|| format!("Failed to inspect: {}", path.display()))?;

            if file_type.is_dir() {
                pending.push(path);
            } else if file_type.is_file() && path.file_name() == Some(OsStr::new(file_name)) {
                logs.push(path);
            }
        }
    }

    logs.sort();
    Ok(logs)
}

fn collect_log_pairs(run_dir: &Path) -> eyre::Result<Vec<LogPair>> {
    let stdout_logs = collect_named_logs(run_dir, "stdout.log")?;
    let mut pairs = Vec::new();

    for stdout_log in stdout_logs {
        let Some(task_dir) = stdout_log.parent() else {
            continue;
        };

        let stderr_log = task_dir.join("stderr.log");
        if !stderr_log.exists() {
            continue;
        }

        let relative_task_dir = task_dir
            .strip_prefix(run_dir)
            .map_or_else(|_| task_dir.to_path_buf(), Path::to_path_buf);

        pairs.push(LogPair {
            relative_task_dir,
            stdout_log,
            stderr_log,
        });
    }

    pairs.sort_by(|left, right| left.relative_task_dir.cmp(&right.relative_task_dir));
    Ok(pairs)
}

fn classify_log_event(line: &str) -> LogEvent {
    let trimmed = line.trim();

    if trimmed.is_empty()
        || trimmed.starts_with("To honour the JVM settings for this build")
        || trimmed.starts_with("Daemon will be stopped at the end of the build")
        || trimmed.contains("did not locate the diffplug APT plugin")
        || trimmed.contains("[main/DEBUG]")
        || trimmed.contains("/DEBUG]")
        || (trimmed.starts_with("> Task :")
            && (trimmed.contains("UP-TO-DATE")
                || trimmed.contains("NO-SOURCE")
                || trimmed.ends_with("FROM-CACHE")))
    {
        return LogEvent::StartupNoise;
    }

    if trimmed.contains("register") || trimmed.contains("registry") {
        return LogEvent::RegistrationMarker;
    }

    if trimmed.contains("runGameTestServer")
        || trimmed.contains("Launch target")
        || trimmed.contains("GameTestServer")
    {
        return LogEvent::RuntimeMarker;
    }

    if trimmed.contains("BUILD SUCCESSFUL") {
        return LogEvent::BuildSuccess;
    }

    if trimmed.contains("BUILD FAILED") {
        return LogEvent::BuildFailed;
    }

    if trimmed.contains("required tests failed :(") {
        return LogEvent::TestFailures;
    }

    if trimmed.starts_with("at ")
        || trimmed.starts_with("\tat ")
        || trimmed.starts_with("... ")
        || trimmed.starts_with("Suppressed:")
    {
        return LogEvent::StackLine;
    }

    if trimmed.contains("Exception") || trimmed.contains("Caused by:") {
        return LogEvent::ExceptionHead;
    }

    if trimmed.contains(" WARN") || trimmed.contains(" ERROR") || trimmed.contains(" FAILURE:") {
        return LogEvent::Signal;
    }

    LogEvent::Other
}

fn advance_log_state(
    state: LogModelState,
    event: LogEvent,
    resume_state: &mut LogModelState,
) -> LogModelState {
    if state == LogModelState::Terminal {
        return LogModelState::Terminal;
    }

    if matches!(event, LogEvent::BuildSuccess | LogEvent::BuildFailed) {
        return LogModelState::Terminal;
    }

    match state {
        LogModelState::Exception => {
            if event == LogEvent::StackLine {
                LogModelState::Exception
            } else {
                *resume_state
            }
        }
        LogModelState::Startup => match event {
            LogEvent::RegistrationMarker => LogModelState::Registration,
            LogEvent::RuntimeMarker => LogModelState::Runtime,
            LogEvent::ExceptionHead => {
                *resume_state = LogModelState::Startup;
                LogModelState::Exception
            }
            _ => LogModelState::Startup,
        },
        LogModelState::Registration => match event {
            LogEvent::RuntimeMarker => LogModelState::Runtime,
            LogEvent::ExceptionHead => {
                *resume_state = LogModelState::Registration;
                LogModelState::Exception
            }
            _ => LogModelState::Registration,
        },
        LogModelState::Runtime => match event {
            LogEvent::ExceptionHead => {
                *resume_state = LogModelState::Runtime;
                LogModelState::Exception
            }
            _ => LogModelState::Runtime,
        },
        LogModelState::Terminal => LogModelState::Terminal,
    }
}

fn is_ignorable_surprise_line(line: &str) -> bool {
    let trimmed = line.trim();
    if trimmed.is_empty() {
        return false;
    }

    trimmed.contains("Advanced terminal features are not available in this environment")
        || trimmed.contains("[main/WARN] [mixin/]: Error loading class:")
        || trimmed.contains("noobanidus/mods/lootr/config/ConfigManager")
        || trimmed.contains("Reflective setAccessible(true) disabled")
        || trimmed.contains("io.netty.util.internal.ReflectionUtil.trySetAccessible")
        || trimmed.contains("io.netty.util.internal.PlatformDependent0")
        || trimmed.contains("io.netty.util.internal.PlatformDependent")
        || trimmed.contains("io.netty.util.ConstantPool")
        || trimmed.contains("io.netty.util.AttributeKey")
        || trimmed.contains("net.minecraftforge.network.NetworkConstants")
        || trimmed.contains("net.minecraftforge.common.ForgeMod")
        || trimmed.contains("java.lang.IllegalAccessException")
        || trimmed.contains("jdk.internal.misc.Unsafe")
        || trimmed.starts_with("... omitted ")
        || trimmed.contains("finished with non-zero exit value 1")
        || trimmed == "* Try:"
        || trimmed.contains("Run with --stacktrace option to get the stack trace")
        || trimmed.contains("Run with --info or --debug option to get more log output")
        || trimmed.contains("Run with --scan to get full insights")
        || trimmed.contains("Get more help at https://help.gradle.org")
        || trimmed.starts_with("BUILD FAILED in ")
}

fn is_daemon_shutdown_noise_line(line: &str) -> bool {
    let trimmed = line.trim();

    trimmed.contains("Daemon vm is shutting down")
        || trimmed.contains("The daemon has exited normally")
        || trimmed == "----- End of the daemon log -----"
        || trimmed == "* What went wrong:"
        || trimmed == "Could not dispatch a message to the daemon."
        || trimmed == "FAILURE: Build failed with an exception."
}

fn is_ignorable_exception_head_line(line: &str) -> bool {
    let trimmed = line.trim();
    trimmed.contains(
        "java.lang.UnsupportedOperationException: Reflective setAccessible(true) disabled",
    ) || (trimmed.contains("java.lang.IllegalAccessException")
        && trimmed.contains("jdk.internal.misc.Unsafe"))
}

fn is_relevant_stack_line(line: &str) -> bool {
    let trimmed = line.trim();
    trimmed.contains("ca.teamdman.")
        || trimmed.contains("net.minecraft.gametest.")
        || trimmed.contains("GameTest")
}

fn reduce_log_content(content: &str, stream: LogStreamKind) -> LogReduction {
    let mut state = LogModelState::Startup;
    let mut resume_state = LogModelState::Startup;
    let mut stack_lines_kept = 0_usize;
    let mut stack_lines_omitted = 0_usize;
    let mut suppress_exception_stack = false;
    const STACKTRACE_LINE_BUDGET: usize = 12;

    let mut reduction = LogReduction {
        failed_tests: extract_failed_gametest_names(content),
        failed_test_reasons: extract_failed_gametest_reasons(content),
        tests_passed: has_gametest_success(content),
        ..LogReduction::default()
    };
    let has_daemon_shutdown_noise = content.contains("Daemon vm is shutting down")
        || content.contains("The daemon has exited normally");

    for line in content.lines() {
        let mut reprocess = true;
        while reprocess {
            reprocess = false;

            let event = classify_log_event(line);
            let previous_state = state;
            state = advance_log_state(state, event, &mut resume_state);

            if previous_state == LogModelState::Exception
                && state != LogModelState::Exception
                && event != LogEvent::StackLine
            {
                if stack_lines_omitted > 0 {
                    reduction.important_lines.push(
                        format!("... omitted {} stacktrace lines", stack_lines_omitted)
                            .dimmed()
                            .to_string(),
                    );
                    stack_lines_omitted = 0;
                }
                stack_lines_kept = 0;
                suppress_exception_stack = false;
                reprocess = true;
                continue;
            }

            if event == LogEvent::ExceptionHead && is_ignorable_exception_head_line(line) {
                suppress_exception_stack = true;
                stack_lines_kept = 0;
                stack_lines_omitted = 0;
                continue;
            }

            if previous_state == LogModelState::Exception
                && event == LogEvent::StackLine
                && suppress_exception_stack
            {
                continue;
            }

            if has_daemon_shutdown_noise && is_daemon_shutdown_noise_line(line) {
                continue;
            }

            if is_ignorable_surprise_line(line) {
                continue;
            }

            if event == LogEvent::BuildFailed {
                reduction.build_failed = true;
                reduction.important_lines.push(line.to_string());
                continue;
            }

            if event == LogEvent::ExceptionHead
                || event == LogEvent::Signal
                || event == LogEvent::TestFailures
            {
                reduction.important_lines.push(line.to_string());
                if event == LogEvent::ExceptionHead {
                    suppress_exception_stack = false;
                    stack_lines_kept = 0;
                    stack_lines_omitted = 0;
                }
                continue;
            }

            if previous_state == LogModelState::Exception && event == LogEvent::StackLine {
                if suppress_exception_stack {
                    continue;
                }

                if !is_relevant_stack_line(line) {
                    stack_lines_omitted += 1;
                    continue;
                }

                if stack_lines_kept < STACKTRACE_LINE_BUDGET {
                    reduction.important_lines.push(line.to_string());
                    stack_lines_kept += 1;
                } else {
                    stack_lines_omitted += 1;
                }
                continue;
            }

            if stream == LogStreamKind::Stderr
                && event == LogEvent::Other
                && !line.trim().is_empty()
                && state == LogModelState::Runtime
            {
                reduction.important_lines.push(line.to_string());
            }
        }
    }

    if stack_lines_omitted > 0 {
        reduction.important_lines.push(
            format!("... omitted {} stacktrace lines", stack_lines_omitted)
                .dimmed()
                .to_string(),
        );
    }

    reduction
}

fn merge_reduction(into: &mut LogReduction, from: LogReduction) {
    into.tests_passed |= from.tests_passed;
    into.build_failed |= from.build_failed;
    into.failed_tests.extend(from.failed_tests);
    into.failed_test_reasons.extend(from.failed_test_reasons);
    into.important_lines.extend(from.important_lines);
}

fn find_failed_test_reason<'a>(reduction: &'a LogReduction, test_name: &str) -> Option<&'a str> {
    reduction
        .failed_test_reasons
        .iter()
        .find_map(|(name, reason)| (name == test_name).then_some(reason.as_str()))
}

fn normalize_spaces(input: &str) -> String {
    input
        .chars()
        .map(|c| {
            if c.is_alphanumeric() {
                c.to_ascii_lowercase()
            } else {
                ' '
            }
        })
        .collect::<String>()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
}

fn normalize_compact(input: &str) -> String {
    input
        .chars()
        .filter(|c| c.is_alphanumeric())
        .map(|c| c.to_ascii_lowercase())
        .collect()
}

fn relativize_to_repo_root(path: PathBuf, repo_root: &Path) -> PathBuf {
    path.strip_prefix(repo_root)
        .ok()
        .map(Path::to_path_buf)
        .unwrap_or(path)
}

fn find_gametest_source(test_name: &str) -> Option<PathBuf> {
    let cwd = std::env::current_dir().ok()?;

    let mut repo_root = None;
    for ancestor in cwd.ancestors() {
        let gametest_dir = ancestor
            .join("platform")
            .join("minecraft")
            .join("src")
            .join("gametest")
            .join("java");
        if gametest_dir.exists() {
            repo_root = Some(ancestor.to_path_buf());
            break;
        }
    }

    let repo_root = repo_root?;
    let gametest_dir = repo_root
        .join("platform")
        .join("minecraft")
        .join("src")
        .join("gametest")
        .join("java");

    let normalized_test = normalize_spaces(test_name);
    let compact_test = normalize_compact(test_name);
    let compact_test_with_suffix = format!("{compact_test}gametest");
    let legacy_marker = normalize_spaces(&format!(
        "Migrated from SFMCorrectnessGameTests.{test_name}"
    ));
    let mut pending = vec![gametest_dir];
    let mut legacy_marker_match = None;
    let mut exact_phrase_match = None;

    while let Some(dir) = pending.pop() {
        let entries = match fs::read_dir(&dir) {
            Ok(entries) => entries,
            Err(_) => continue,
        };
        for entry in entries {
            let entry = match entry {
                Ok(entry) => entry,
                Err(_) => continue,
            };
            let path = entry.path();
            let file_type = match entry.file_type() {
                Ok(file_type) => file_type,
                Err(_) => continue,
            };

            if file_type.is_dir() {
                pending.push(path);
                continue;
            }

            if !file_type.is_file() || path.extension() != Some(OsStr::new("java")) {
                continue;
            }

            let file_stem = path.file_stem().and_then(OsStr::to_str).unwrap_or_default();
            let compact_file_stem = normalize_compact(file_stem);
            if compact_file_stem == compact_test || compact_file_stem == compact_test_with_suffix {
                return Some(relativize_to_repo_root(path, &repo_root));
            }

            let content = match fs::read_to_string(&path) {
                Ok(content) => content,
                Err(_) => continue,
            };
            let normalized_content = normalize_spaces(&content);

            if legacy_marker_match.is_none() && normalized_content.contains(&legacy_marker) {
                legacy_marker_match = Some(relativize_to_repo_root(path.clone(), &repo_root));
            }

            if exact_phrase_match.is_none() && normalized_content.contains(&normalized_test) {
                exact_phrase_match = Some(relativize_to_repo_root(path, &repo_root));
            }
        }
    }

    legacy_marker_match.or(exact_phrase_match)
}

fn summarize_reduction(_pair: &LogPair, reduction: &LogReduction) -> String {
    let mut unique_failed = reduction.failed_tests.clone();
    unique_failed.sort();
    unique_failed.dedup();

    if unique_failed.is_empty() && reduction.important_lines.is_empty() && reduction.tests_passed {
        return "No surprises, all tests passed".green().bold().to_string();
    }

    if unique_failed.len() == 1 {
        let test_name = &unique_failed[0];
        let reason_suffix = find_failed_test_reason(reduction, test_name)
            .map(|reason| format!(" — {reason}"))
            .unwrap_or_default();

        if let Some(path) = find_gametest_source(test_name) {
            return format!(
                "One test failed: {test_name}{reason_suffix} at {}",
                path.display()
            )
            .red()
            .bold()
            .to_string();
        }

        return format!("One test failed: {test_name}{reason_suffix}")
            .red()
            .bold()
            .to_string();
    }

    if !unique_failed.is_empty() {
        return format!(
            "{} tests failed: {}",
            unique_failed.len(),
            unique_failed.join(", ")
        )
        .red()
        .bold()
        .to_string();
    }

    if reduction.build_failed {
        return "Build failed with surprises".red().bold().to_string();
    }

    "Potential surprises".yellow().bold().to_string()
}

fn reduction_has_visible_content(reduction: &LogReduction) -> bool {
    reduction.tests_passed
        || reduction.build_failed
        || !reduction.failed_tests.is_empty()
        || !reduction.important_lines.is_empty()
}

fn read_log_stats(path: &Path) -> eyre::Result<(usize, u64)> {
    let bytes = fs::metadata(path)
        .wrap_err_with(|| format!("Failed to stat log file: {}", path.display()))?
        .len();
    let content =
        fs::read(path).wrap_err_with(|| format!("Failed to read log file: {}", path.display()))?;
    let text = String::from_utf8_lossy(&content);
    Ok((text.lines().count(), bytes))
}

fn print_log_list(run_dir: &Path) -> eyre::Result<()> {
    let pairs = collect_log_pairs(run_dir)?;
    if pairs.is_empty() {
        println!("No stdout/stderr log pairs found in {}", run_dir.display());
        return Ok(());
    }

    println!(
        "{}",
        format!("Gradle logs in {}", run_dir.display())
            .cyan()
            .bold()
    );

    for pair in pairs {
        println!("{}", pair.relative_task_dir.display().to_string().bold());

        let (stdout_lines, stdout_bytes) = read_log_stats(&pair.stdout_log)?;
        let (stderr_lines, stderr_bytes) = read_log_stats(&pair.stderr_log)?;

        let stdout_rel = pair
            .stdout_log
            .strip_prefix(run_dir)
            .map_or_else(|_| pair.stdout_log.clone(), Path::to_path_buf);
        let stderr_rel = pair
            .stderr_log
            .strip_prefix(run_dir)
            .map_or_else(|_| pair.stderr_log.clone(), Path::to_path_buf);

        println!(
            "  stdout: {} ({stdout_lines} lines, {})",
            stdout_rel.display(),
            format_size(stdout_bytes, DECIMAL)
        );
        println!(
            "  stderr: {} ({stderr_lines} lines, {})",
            stderr_rel.display(),
            format_size(stderr_bytes, DECIMAL)
        );
    }

    Ok(())
}

const TLDR_MAX_LINES: usize = 100;
const TLDR_MAX_CHARS: usize = 50_000;

fn truncate_tldr_summary(summary: &str) -> String {
    let lines: Vec<&str> = summary.lines().collect();
    if lines.is_empty() {
        return summary.to_string();
    }

    let mut kept_lines = Vec::new();
    let mut used_chars = 0_usize;
    let mut used_bytes = 0_u64;

    for line in &lines {
        if kept_lines.len() >= TLDR_MAX_LINES {
            break;
        }

        let separator_chars = usize::from(!kept_lines.is_empty());
        let separator_bytes = u64::from(!kept_lines.is_empty());
        let line_chars = line.chars().count();

        if used_chars
            .saturating_add(separator_chars)
            .saturating_add(line_chars)
            > TLDR_MAX_CHARS
        {
            break;
        }

        kept_lines.push(*line);
        used_chars = used_chars
            .saturating_add(separator_chars)
            .saturating_add(line_chars);
        used_bytes = used_bytes
            .saturating_add(separator_bytes)
            .saturating_add(u64::try_from(line.len()).unwrap_or(u64::MAX));
    }

    if kept_lines.len() == lines.len() {
        return summary.to_string();
    }

    let omitted_lines = lines.len().saturating_sub(kept_lines.len());
    let total_bytes = u64::try_from(summary.len()).unwrap_or(u64::MAX);
    let omitted_bytes = total_bytes.saturating_sub(used_bytes);
    let truncated_line = format!(
        "... truncated, {} lines ({}) omitted",
        omitted_lines,
        format_size(omitted_bytes, DECIMAL)
    )
    .dimmed()
    .to_string();

    if kept_lines.is_empty() {
        truncated_line
    } else {
        format!("{}\n{}", kept_lines.join("\n"), truncated_line)
    }
}

fn print_tldr_for_run(run_dir: &Path) -> eyre::Result<()> {
    let pairs = collect_log_pairs(run_dir)?;
    if pairs.is_empty() {
        println!("No stdout/stderr log pairs found in {}", run_dir.display());
        return Ok(());
    }

    println!();
    println!(
        "{}",
        format!("TLDR (model-driven reduction) {}", run_dir.display())
            .cyan()
            .bold()
    );

    let mut printed_any = false;

    for pair in pairs {
        let stdout_content = fs::read_to_string(&pair.stdout_log)
            .wrap_err_with(|| format!("Failed to read log file: {}", pair.stdout_log.display()))?;
        let stderr_content = fs::read_to_string(&pair.stderr_log)
            .wrap_err_with(|| format!("Failed to read log file: {}", pair.stderr_log.display()))?;

        let stdout_reduction = reduce_log_content(&stdout_content, LogStreamKind::Stdout);
        let stderr_reduction = reduce_log_content(&stderr_content, LogStreamKind::Stderr);
        let mut combined = LogReduction::default();
        merge_reduction(&mut combined, stdout_reduction);
        merge_reduction(&mut combined, stderr_reduction);

        if !reduction_has_visible_content(&combined) {
            continue;
        }

        let headline = summarize_reduction(&pair, &combined);
        printed_any = true;
        println!();
        println!("{}", pair.relative_task_dir.display().to_string().bold());
        println!("  {}", headline);

        let important_lines = mem::take(&mut combined.important_lines);
        if !important_lines.is_empty() {
            let preview = truncate_tldr_summary(&important_lines.join("\n"));
            println!("{}", preview);
        }
    }

    if !printed_any {
        println!("  {}", "No notable log output found".dimmed());
    }

    Ok(())
}

fn create_task_log_paths(
    run_log_dir: &Path,
    branch: &str,
    task_idx: usize,
    task: &GradleTask,
) -> eyre::Result<(PathBuf, PathBuf)> {
    let branch_dir = sanitize_for_path(branch);
    let task_dir = format!(
        "{:02}_{}",
        task_idx + 1,
        sanitize_for_path(task.as_gradle_arg())
    );

    let dir = run_log_dir.join(branch_dir).join(task_dir);
    fs::create_dir_all(&dir)
        .wrap_err_with(|| format!("Failed to create task log directory: {}", dir.display()))?;

    Ok((dir.join("stdout.log"), dir.join("stderr.log")))
}

async fn collect_output<R>(
    reader: R,
    stream_logs_to_console: bool,
    log_path: PathBuf,
) -> std::io::Result<String>
where
    R: AsyncRead + Unpin,
{
    let mut reader = reader;
    let mut log_file = File::create(log_path).await?;
    let mut buf = [0_u8; 8 * 1024];
    let mut out = Vec::new();

    loop {
        let bytes_read = reader.read(&mut buf).await?;
        if bytes_read == 0 {
            break;
        }

        let chunk = &buf[..bytes_read];
        if stream_logs_to_console {
            eprint!("{}", String::from_utf8_lossy(chunk));
            std::io::stderr().flush()?;
        }

        log_file.write_all(chunk).await?;
        log_file.flush().await?;
        out.extend_from_slice(chunk);
    }

    log_file.flush().await?;

    Ok(String::from_utf8_lossy(&out).into_owned())
}

#[expect(
    clippy::too_many_lines,
    reason = "Task execution, streaming, and Ctrl+C handling are clearer in one place."
)]
async fn run_gradle_task(
    gradlew: &Path,
    minecraft_dir: &Path,
    task: &GradleTask,
    show_logs: bool,
    stdout_log_path: &Path,
    stderr_log_path: &Path,
) -> Result<TaskOutput, TaskError> {
    debug!(
        path = %minecraft_dir.display(),
        task = %task.as_gradle_arg(),
        "Running task"
    );

    if show_logs {
        eprintln!(
            "{}",
            format!("━━━ {} ({})", task.as_gradle_arg(), minecraft_dir.display())
                .cyan()
                .bold()
        );
    }

    let start = Instant::now();
    let mut child = Command::new(gradlew)
        .arg("--console=plain")
        .arg(task.as_gradle_arg())
        .current_dir(minecraft_dir)
        .kill_on_drop(true)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|err| TaskError {
            message: format!("Failed to start {}: {err}", task.as_gradle_arg()),
            output: None,
            interrupted: false,
        })?;

    let stdout = child.stdout.take().ok_or_else(|| TaskError {
        message: format!(
            "Failed to capture stdout for {} in {}",
            task.as_gradle_arg(),
            minecraft_dir.display()
        ),
        output: None,
        interrupted: false,
    })?;
    let stderr = child.stderr.take().ok_or_else(|| TaskError {
        message: format!(
            "Failed to capture stderr for {} in {}",
            task.as_gradle_arg(),
            minecraft_dir.display()
        ),
        output: None,
        interrupted: false,
    })?;

    let stdout_log_path_buf = stdout_log_path.to_path_buf();
    let stderr_log_path_buf = stderr_log_path.to_path_buf();
    let stdout_task =
        tokio::spawn(async move { collect_output(stdout, show_logs, stdout_log_path_buf).await });
    let stderr_task =
        tokio::spawn(async move { collect_output(stderr, show_logs, stderr_log_path_buf).await });

    let (status, interrupted) = tokio::select! {
        status_res = child.wait() => {
            let status = status_res.map_err(|err| TaskError {
                message: format!(
                    "Failed while waiting for {} in {}: {err}",
                    task.as_gradle_arg(),
                    minecraft_dir.display()
                ),
                output: None,
                interrupted: false,
            })?;
            (status, false)
        }
        signal_res = tokio::signal::ctrl_c() => {
            if let Err(err) = signal_res {
                warn!(
                    path = %minecraft_dir.display(),
                    task = %task.as_gradle_arg(),
                    "Failed to listen for Ctrl+C: {err}"
                );
            }
            warn!(
                path = %minecraft_dir.display(),
                task = %task.as_gradle_arg(),
                "Received Ctrl+C, terminating running gradle task"
            );
            let _ = child.kill().await;
            let status = child.wait().await.map_err(|err| TaskError {
                message: format!(
                    "Failed while terminating {} in {}: {err}",
                    task.as_gradle_arg(),
                    minecraft_dir.display()
                ),
                output: None,
                interrupted: false,
            })?;
            (status, true)
        }
    };

    let stdout = stdout_task
        .await
        .map_err(|err| TaskError {
            message: format!(
                "Failed while joining stdout reader for {} in {}: {err}",
                task.as_gradle_arg(),
                minecraft_dir.display()
            ),
            output: None,
            interrupted: false,
        })?
        .map_err(|err| TaskError {
            message: format!(
                "Failed while reading stdout for {} in {}: {err}",
                task.as_gradle_arg(),
                minecraft_dir.display()
            ),
            output: None,
            interrupted: false,
        })?;

    let stderr = stderr_task
        .await
        .map_err(|err| TaskError {
            message: format!(
                "Failed while joining stderr reader for {} in {}: {err}",
                task.as_gradle_arg(),
                minecraft_dir.display()
            ),
            output: None,
            interrupted: false,
        })?
        .map_err(|err| TaskError {
            message: format!(
                "Failed while reading stderr for {} in {}: {err}",
                task.as_gradle_arg(),
                minecraft_dir.display()
            ),
            output: None,
            interrupted: false,
        })?;

    let task_output = TaskOutput {
        status,
        stdout,
        stderr,
        duration: start.elapsed(),
        stdout_log_path: stdout_log_path.to_path_buf(),
        stderr_log_path: stderr_log_path.to_path_buf(),
    };

    if interrupted {
        return Err(TaskError {
            message: format!(
                "Interrupted by Ctrl+C while running {} in {}",
                task.as_gradle_arg(),
                minecraft_dir.display()
            ),
            output: Some(task_output),
            interrupted: true,
        });
    }

    if task.is_success(&task_output) {
        Ok(task_output)
    } else {
        Err(TaskError {
            message: format!(
                "{} failed for {} (exit: {:?})",
                task.as_gradle_arg(),
                minecraft_dir.display(),
                task_output.status.code()
            ),
            output: Some(task_output),
            interrupted: false,
        })
    }
}

/// Gradle-related commands.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum GradleCommand {
    /// Run arbitrary gradle task(s) for each worktree in strict sequence
    Run {
        /// Gradle run options
        #[facet(flatten)]
        command: GradleRunCommand,
    },
    /// Inspect previously captured gradle logs (alias)
    Log {
        /// Log inspection subcommand
        #[facet(args::subcommand)]
        command: GradleLogsCommand,
    },
}

impl GradleCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Run { command } => super::gradle_run_command::invoke(command),
            Self::Log { command } => super::gradle_log_command::invoke(command),
        }
    }
}

/// Gradle command - runs arbitrary gradle tasks in each worktree in strict sequence.
#[derive(Facet, Debug, Default)]
pub struct GradleRunCommand {
    /// Gradle tasks to run (for example: `runData`, `runGameTestServer`, `test`).
    #[facet(args::positional)]
    pub tasks: Vec<String>,

    /// Minecraft version filter expression for branch names (examples: `>=1.21.0`, `<1.20`, `=1.20.4`, `=1.19.2 OR =1.21.1`).
    #[facet(default, args::named)]
    pub mc: Option<String>,

    /// If set, stream gradle stdout/stderr to the console while tasks run.
    ///
    /// By default logs are written to cache files only and not streamed.
    #[facet(rename = "show-logs", args::named, default = false)]
    pub show_logs: bool,

    /// If set, continue with later branches after a task failure.
    ///
    /// Remaining tasks for the failed branch are marked as skipped.
    #[facet(rename = "continue-on-error", args::named, default = false)]
    pub continue_on_error: bool,
}

impl GradleRunCommand {
    /// # Errors
    ///
    /// Returns an error if any task fails.
    pub fn invoke(self) -> eyre::Result<()> {
        let rt = tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .build()
            .wrap_err("Failed to create tokio runtime")?;

        rt.block_on(self.invoke_async())
    }

    #[expect(
        clippy::too_many_lines,
        reason = "Control flow and report updates are clearest when kept together."
    )]
    async fn invoke_async(self) -> eyre::Result<()> {
        if self.tasks.is_empty() {
            bail!("No tasks provided. Usage: sfm-propagate-changes gradle <task1> <task2> ...");
        }

        let mut worktrees = get_sorted_worktrees()?;
        let all_worktree_branches: Vec<String> =
            worktrees.iter().map(|wt| wt.branch.clone()).collect();
        let total_worktrees = worktrees.len();
        let mc_filter = self.mc.as_deref().map(McVersionFilter::parse).transpose()?;

        let mut excluded_worktree_branches = Vec::new();

        if let Some(filter) = mc_filter {
            worktrees.retain(|wt| match filter.matches_version_text(&wt.branch) {
                Some(true) => true,
                Some(false) => {
                    excluded_worktree_branches.push(wt.branch.clone());
                    debug!(branch = %wt.branch, filter = ?self.mc, "Skipping branch due to --mc filter");
                    false
                }
                None => {
                    excluded_worktree_branches.push(wt.branch.clone());
                    warn!(branch = %wt.branch, filter = ?self.mc, "Skipping non-version branch for --mc filter");
                    false
                }
            });
        }

        if worktrees.is_empty() {
            if self.mc.is_some() {
                println!("No worktrees match the requested --mc filter.");
            } else {
                println!("No worktrees found.");
            }
            return Ok(());
        }

        let tasks: Vec<GradleTask> = self
            .tasks
            .iter()
            .map(|task| GradleTask::from_input(task))
            .collect();

        let run_log_dir = create_gradle_run_log_dir(&self.tasks)?;
        println!("Gradle run logs: {}", run_log_dir.display());

        if tasks.iter().any(GradleTask::needs_generated_preflight) {
            assert_worktrees_clean_or_autocommit_generated(&worktrees)?;
        }

        let mut branches: Vec<BranchRun> = worktrees
            .iter()
            .map(|wt| BranchRun {
                branch: wt.branch.clone(),
                tasks: tasks
                    .iter()
                    .cloned()
                    .map(|task| TaskRun {
                        task,
                        state: TaskState::Waiting,
                    })
                    .collect(),
            })
            .collect();

        let included_worktree_branches: Vec<String> =
            worktrees.iter().map(|wt| wt.branch.clone()).collect();
        let worktrees_included = included_worktree_branches.len();
        let worktrees_excluded = total_worktrees.saturating_sub(worktrees.len());

        info!(
            tasks = ?self.tasks,
            mc_filter = ?self.mc,
            continue_on_error = self.continue_on_error,
            worktrees = ?all_worktree_branches,
            worktrees_included = ?included_worktree_branches,
            worktrees_included_count = worktrees_included,
            worktrees_total = total_worktrees,
            worktrees_excluded = ?excluded_worktree_branches,
            worktrees_excluded_count = worktrees_excluded,
            "Running gradle tasks in strict sequence"
        );

        let mut failures: Vec<String> = Vec::new();
        let mut gametest_failures: Vec<(String, Vec<(String, Option<String>)>)> = Vec::new();

        for (branch_idx, wt) in worktrees.iter().enumerate() {
            let minecraft_dir = wt.path.join("platform").join("minecraft");
            let gradlew = if cfg!(windows) {
                minecraft_dir.join("gradlew.bat")
            } else {
                minecraft_dir.join("gradlew")
            };

            if !minecraft_dir.exists() {
                let reason = "platform/minecraft not found".to_string();
                warn!(branch = %wt.branch, path = %wt.path.display(), "{reason}");
                for task in &mut branches[branch_idx].tasks {
                    task.state = TaskState::NotFound {
                        reason: reason.clone(),
                    };
                }
                print_report_to_stderr(&branches, &tasks);
                continue;
            }

            if !gradlew.exists() {
                let reason = format!("gradlew not found in {}", minecraft_dir.display());
                warn!(branch = %wt.branch, path = %minecraft_dir.display(), "{reason}");
                for task in &mut branches[branch_idx].tasks {
                    task.state = TaskState::NotFound {
                        reason: reason.clone(),
                    };
                }
                print_report_to_stderr(&branches, &tasks);
                continue;
            }

            for task_idx in 0..tasks.len() {
                branches[branch_idx].tasks[task_idx].state = TaskState::Running {
                    start_time: Instant::now(),
                };
                print_report_to_stderr(&branches, &tasks);

                let current_task = branches[branch_idx].tasks[task_idx].task.clone();
                debug!(
                    branch = %wt.branch,
                    path = %minecraft_dir.display(),
                    task = %current_task.as_gradle_arg(),
                    "Starting gradle task"
                );

                let result = {
                    let (stdout_log_path, stderr_log_path) =
                        create_task_log_paths(&run_log_dir, &wt.branch, task_idx, &current_task)?;

                    fs::write(&stdout_log_path, "").wrap_err_with(|| {
                        format!(
                            "Failed to initialize stdout log file: {}",
                            stdout_log_path.display()
                        )
                    })?;
                    fs::write(&stderr_log_path, "").wrap_err_with(|| {
                        format!(
                            "Failed to initialize stderr log file: {}",
                            stderr_log_path.display()
                        )
                    })?;

                    println!(
                        "Running {} for {}",
                        current_task.as_gradle_arg().cyan().bold(),
                        wt.branch.cyan().bold()
                    );
                    print_stream_path("stdout", &stdout_log_path);
                    print_stream_path("stderr", &stderr_log_path);

                    run_gradle_task(
                        &gradlew,
                        &minecraft_dir,
                        &current_task,
                        self.show_logs,
                        &stdout_log_path,
                        &stderr_log_path,
                    )
                    .await
                };

                match result {
                    Ok(output) => {
                        branches[branch_idx].tasks[task_idx].state = TaskState::Success {
                            duration: output.duration,
                        };
                        println!(
                            "{}",
                            format_log_summary("stdout", &output.stdout, &output.stdout_log_path)
                        );
                        println!(
                            "{}",
                            format_log_summary("stderr", &output.stderr, &output.stderr_log_path)
                        );
                        debug!(
                            branch = %wt.branch,
                            task = %current_task.as_gradle_arg(),
                            duration = %format_duration(output.duration),
                            "Task succeeded"
                        );
                    }
                    Err(err) => {
                        let duration = err
                            .output
                            .as_ref()
                            .map_or_else(|| Duration::from_secs(0), |out| out.duration);
                        branches[branch_idx].tasks[task_idx].state = TaskState::Failed { duration };

                        for remaining in branches[branch_idx].tasks.iter_mut().skip(task_idx + 1) {
                            remaining.state = TaskState::Skipped;
                        }

                        if matches!(current_task, GradleTask::RunGameTestServer) {
                            if let Some(ref output) = err.output {
                                let combined = format!(
                                    "{}
{}",
                                    output.stdout, output.stderr
                                );
                                let names = extract_failed_gametest_names(&combined);
                                let reasons = extract_failed_gametest_reasons(&combined);
                                if !names.is_empty() {
                                    let tests = names
                                        .into_iter()
                                        .map(|name| {
                                            let reason =
                                                reasons.iter().find_map(|(test_name, reason)| {
                                                    (test_name == &name).then_some(reason.clone())
                                                });
                                            (name, reason)
                                        })
                                        .collect();
                                    gametest_failures.push((wt.branch.clone(), tests));
                                }
                            }
                        }

                        if let Some(ref output) = err.output {
                            println!(
                                "{}",
                                format_log_summary(
                                    "stdout",
                                    &output.stdout,
                                    &output.stdout_log_path
                                )
                            );
                            println!(
                                "{}",
                                format_log_summary(
                                    "stderr",
                                    &output.stderr,
                                    &output.stderr_log_path
                                )
                            );
                        }

                        error!(
                            branch = %wt.branch,
                            task = %current_task.as_gradle_arg(),
                            error = %err.message,
                            "Task failed"
                        );

                        failures.push(format!(
                            "branch: {}, task: {}, error: {}",
                            wt.branch,
                            current_task.as_gradle_arg(),
                            err.message
                        ));

                        if err.interrupted {
                            println!();
                            println!("{}", "ABORTED BY CTRL+C".yellow().bold());
                            println!(
                                "{}",
                                format!(
                                    "branch: {}, task: {}",
                                    wt.branch,
                                    current_task.as_gradle_arg()
                                )
                                .yellow()
                            );
                        }

                        if let Some(output) = err.output {
                            println!();
                            println!("{}", "FAILED COMMAND LOGS".red().bold());
                            println!(
                                "{}",
                                format!(
                                    "branch: {}, task: {}, exit: {:?}",
                                    wt.branch,
                                    current_task.as_gradle_arg(),
                                    output.status.code()
                                )
                                .red()
                            );
                        }

                        if self.continue_on_error && !err.interrupted {
                            print_report_to_stderr(&branches, &tasks);
                            continue;
                        }

                        for later_branch in branches.iter_mut().skip(branch_idx + 1) {
                            for task in &mut later_branch.tasks {
                                task.state = TaskState::Skipped;
                            }
                        }
                        print_gametest_failures(&gametest_failures);
                        print_report_to_stdout(&branches, &tasks);
                        if let Err(tldr_err) = print_tldr_for_run(&run_log_dir) {
                            warn!(
                                run_dir = %run_log_dir.display(),
                                error = %tldr_err,
                                "Failed to generate automatic TLDR summary for gradle run"
                            );
                        }
                        bail!(err.message);
                    }
                }
            }
        }

        print_gametest_failures(&gametest_failures);
        print_report_to_stdout(&branches, &tasks);
        if let Err(tldr_err) = print_tldr_for_run(&run_log_dir) {
            warn!(
                run_dir = %run_log_dir.display(),
                error = %tldr_err,
                "Failed to generate automatic TLDR summary for gradle run"
            );
        }

        if !failures.is_empty() {
            let mut summary = String::from("One or more gradle tasks failed:\n");
            for failure in &failures {
                let _ = writeln!(summary, "  - {failure}");
            }
            bail!(summary);
        }

        Ok(())
    }
}

/// Gradle log inspection commands.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum GradleLogsCommand {
    /// List discovered stdout/stderr log paths and sizes
    List {
        /// Inspect the latest run directory under `cache/gradle-runs`
        #[facet(args::named, default = false)]
        latest: bool,
    },
    /// Summarize log files with noise filtering
    Tldr {
        /// Summarize the latest run directory under `cache/gradle-runs`
        #[facet(args::named, default = false)]
        latest: bool,

        /// Run directory to summarize (if set, `--latest` is not required)
        #[facet(default, args::positional)]
        path: Option<PathBuf>,
    },
}

impl GradleLogsCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::List { latest } => super::gradle_log_list_command::invoke(latest),
            Self::Tldr { latest, path } => super::gradle_log_tldr_command::invoke(latest, path),
        }
    }
}

pub(super) fn invoke_gradle_logs_list(latest: bool) -> eyre::Result<()> {
    if !latest {
        bail!("Only `--latest` is currently supported for `gradle log list`.");
    }

    let run_dir = latest_gradle_run_dir()?;
    println!("Listing latest gradle logs from {}", run_dir.display());
    print_log_list(&run_dir)
}

pub(super) fn invoke_gradle_logs_tldr(latest: bool, path: Option<PathBuf>) -> eyre::Result<()> {
    let run_dir = match (latest, path) {
        (true, Some(_)) => {
            bail!("Provide either `--latest` or `<path>`, not both.");
        }
        (true, None) => latest_gradle_run_dir()?,
        (false, Some(path)) => path,
        (false, None) => {
            bail!("Provide `--latest` or a run directory path.");
        }
    };

    if !run_dir.exists() {
        bail!("Run directory not found: {}", run_dir.display());
    }
    if !run_dir.is_dir() {
        bail!("Run path is not a directory: {}", run_dir.display());
    }

    println!("Summarizing gradle logs from {}", run_dir.display());
    print_tldr_for_run(&run_dir)
}
