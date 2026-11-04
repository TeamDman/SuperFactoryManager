//! Resumable catalog-scoped synchronization and validation.
use crate::artifact_lock::ArtifactLock;
use crate::artifact_lock::ArtifactLockWaitPolicy;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::core_catalog::CoreCatalog;
use eyre::Context;
use eyre::ensure;
use facet::Facet;
use figue as args;
use rayon::prelude::*;
use sha2::Digest;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::fs::File;
use std::fs::OpenOptions;
use std::fs::{self};
use std::io::Read;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use std::process::Stdio;
use std::thread;
use std::time::Duration;

const SCHEMA: &str = "sfm:check_everything_native@1";

#[derive(Debug, Facet)]
pub struct CheckEverythingArgs {
    /// Projection key glob or Minecraft-version glob; matrix defaults to the whole catalog.
    #[facet(default = "", args::named)]
    pub projection: String,
    /// Repeat to select stages. Defaults depend on sync, build, or matrix.
    #[facet(default, args::named)]
    pub stage: Vec<String>,
    /// Repository root, otherwise the containing Git checkout.
    #[facet(default, args::named)]
    pub repo_root: Option<PathBuf>,
    /// Maximum concurrent read-only source checks.
    #[facet(default = 2, args::named)]
    pub read_workers: usize,
    /// Exact `GameTest` selector. Unbounded visual/extended selection is refused.
    #[facet(default = "*", args::named)]
    pub headless_filter: String,
    /// Exact retained-input recipe for one historical plain projection.
    #[facet(default, args::named)]
    pub released_recipe: Option<String>,
    /// Minimum free bytes on the projection drive before a mutable stage.
    #[facet(default = 2_000_000_000, args::named)]
    pub min_free_bytes: u64,
    /// Print the selected manifest without executing or writing receipts.
    #[facet(default = false, args::named)]
    pub plan_only: bool,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum Stage {
    Sync,
    Check,
    Compile,
    Jar,
    Headless,
}

impl Stage {
    fn parse(text: &str) -> eyre::Result<Self> {
        match text.to_ascii_lowercase().as_str() {
            "sync" => Ok(Self::Sync),
            "check" => Ok(Self::Check),
            "compile" => Ok(Self::Compile),
            "jar" => Ok(Self::Jar),
            "headless" => Ok(Self::Headless),
            _ => eyre::bail!("unknown stage {text:?}; use sync, check, compile, jar, headless"),
        }
    }
    const fn label(self) -> &'static str {
        match self {
            Self::Sync => "sync",
            Self::Check => "check",
            Self::Compile => "compile",
            Self::Jar => "jar",
            Self::Headless => "headless",
        }
    }
}

#[derive(Clone, Copy, Eq, PartialEq)]
enum RunMode {
    Sync,
    Build,
    Matrix,
}

#[derive(Clone, Debug, Facet)]
struct Manifest {
    schema: String,
    projections: Vec<String>,
    stages: Vec<String>,
    read_workers: usize,
    mutable_workers: usize,
    headless_filter: String,
    released_recipe: Option<String>,
    state_root: String,
}

#[derive(Clone, Debug, Facet)]
struct Receipt {
    schema: String,
    projection: String,
    stage: String,
    identity: String,
    status: String,
    reason: String,
    inputs: BTreeMap<String, String>,
    started_utc: String,
    ended_utc: String,
    exit_code: Option<i32>,
    log: String,
    artifact_path: Option<String>,
    artifact_sha256: Option<String>,
}

#[derive(Clone, Debug, Facet)]
struct JarPreservation {
    schema: String,
    projection: String,
    source: String,
    retained: String,
    sha256: String,
    recorded_utc: String,
}

#[derive(Clone, Debug, Facet)]
struct Started {
    schema: String,
    identity: String,
    pid: u32,
    #[facet(rename = "started_utc")]
    at_utc: String,
}

#[derive(Clone, Debug, Facet)]
struct Row {
    projection: String,
    stage: String,
    status: String,
    reason: String,
    receipt: Option<String>,
    identity: Option<String>,
}

#[derive(Clone, Debug, Facet)]
struct Summary {
    schema: String,
    manifest: Manifest,
    rows: Vec<Row>,
    passed: usize,
    reused: usize,
    failed: usize,
    blocked: usize,
    skipped: usize,
    #[facet(rename = "summary_path")]
    path: String,
}

struct ContextState {
    root: PathBuf,
    state_root: PathBuf,
    exe: PathBuf,
    filter: String,
    recipe: Option<String>,
    min_free_bytes: u64,
}

impl CheckEverythingArgs {
    /// Run the selected catalog stages and publish durable operation receipts.
    ///
    /// # Errors
    /// Rejects invalid selection, inaccessible checked inputs, lock contention, or receipt I/O.
    #[expect(
        clippy::too_many_lines,
        reason = "selection, guarded scheduling and summary publication form one command boundary"
    )]
    pub fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        self.invoke_mode(cancellation, invocation_dir, RunMode::Matrix)
    }

    /// Run the ordinary incremental sync path with the same durable stage engine.
    ///
    /// # Errors
    /// Rejects invalid selection, inaccessible checked inputs, lock contention, or receipt I/O.
    pub fn invoke_sync_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        self.invoke_mode(cancellation, invocation_dir, RunMode::Sync)
    }

    /// Run strict source checking, incremental compilation, and create-only packaging.
    ///
    /// # Errors
    /// Rejects invalid selection, inaccessible checked inputs, lock contention, or receipt I/O.
    pub fn invoke_build_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        self.invoke_mode(cancellation, invocation_dir, RunMode::Build)
    }

    #[expect(
        clippy::too_many_lines,
        reason = "selection, guarded scheduling and summary publication form one command boundary"
    )]
    fn invoke_mode(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
        mode: RunMode,
    ) -> eyre::Result<CliOutput> {
        ensure!(
            (1..=8).contains(&self.read_workers),
            "--read-workers must be between 1 and 8"
        );
        let root = match self.repo_root.as_deref() {
            Some(root) => root.to_path_buf(),
            None => gix::discover(invocation_dir)?
                .workdir()
                .ok_or_else(|| {
                    eyre::eyre!("check-everything requires a Git checkout or --repo-root")
                })?
                .to_path_buf(),
        };
        let catalog = CoreCatalog::load(&root, invocation_dir)?;
        let root = catalog.repo_root;
        let selected = select_keys(
            &catalog.catalog.0,
            selection_pattern(&self.projection, mode)?,
        )?;
        if self.released_recipe.is_some() {
            ensure!(
                selected.len() == 1 && is_plain(&selected[0]),
                "--released-recipe requires one exact historical plain projection"
            );
        }
        let stages = selected_stages(&self.stage, mode)?;
        let state_root = root.join("sfm-toolchain-data/check-everything-native");
        let manifest = Manifest {
            schema: SCHEMA.to_owned(),
            projections: selected.clone(),
            stages: stages
                .iter()
                .map(|stage| stage.label().to_owned())
                .collect(),
            read_workers: self.read_workers,
            mutable_workers: 1,
            headless_filter: self.headless_filter.clone(),
            released_recipe: self.released_recipe.clone(),
            state_root: state_root.display().to_string(),
        };
        if self.plan_only {
            return Ok(CliOutput::facet(manifest));
        }
        fs::create_dir_all(&state_root)?;
        let policy = ArtifactLockWaitPolicy::default()
            .with_max_wait(Duration::from_secs(30))
            .with_cancellation(cancellation.clone());
        let _lock = ArtifactLock::acquire_with_policy(
            root.join("sfm-toolchain-data/check-everything.global.lock"),
            "check-everything",
            &policy,
        )?;
        let ctx = ContextState {
            root,
            state_root: state_root.clone(),
            exe: std::env::current_exe()?,
            filter: self.headless_filter,
            recipe: self.released_recipe,
            min_free_bytes: self.min_free_bytes,
        };
        let mut rows = Vec::new();
        if mode != RunMode::Matrix {
            let mut stopped = false;
            for key in &selected {
                for stage in &stages {
                    if stopped {
                        rows.push(skipped(key, *stage, "stopped after first failed stage"));
                        continue;
                    }
                    let result = run_one(&ctx, key, *stage, cancellation);
                    stopped = !is_success(&result);
                    rows.push(result);
                }
            }
        } else {
            if stages.contains(&Stage::Check) {
                let pool = rayon::ThreadPoolBuilder::new()
                    .num_threads(self.read_workers)
                    .build()?;
                rows.extend(pool.install(|| {
                    selected
                        .par_iter()
                        .map(|key| run_one(&ctx, key, Stage::Check, cancellation))
                        .collect::<Vec<_>>()
                }));
            }
            for key in &selected {
                let checked = rows
                    .iter()
                    .find(|row| row.projection == *key && row.stage == "check");
                if stages.contains(&Stage::Check) && !checked.is_some_and(is_success) {
                    for stage in stages.iter().filter(|stage| **stage != Stage::Check) {
                        rows.push(skipped(key, *stage, "source check did not pass"));
                    }
                    continue;
                }
                let mut compile_ok = true;
                for stage in stages.iter().filter(|stage| **stage != Stage::Check) {
                    if !compile_ok {
                        rows.push(skipped(key, *stage, "compile did not pass"));
                        continue;
                    }
                    let row = run_one(&ctx, key, *stage, cancellation);
                    if *stage == Stage::Compile && !is_success(&row) {
                        compile_ok = false;
                    }
                    rows.push(row);
                }
            }
        }
        let mut summary = Summary {
            schema: SCHEMA.to_owned(),
            manifest,
            passed: count(&rows, "passed"),
            reused: count(&rows, "reused"),
            failed: count(&rows, "failed"),
            blocked: count(&rows, "blocked"),
            skipped: count(&rows, "skipped"),
            rows,
            path: String::new(),
        };
        let runs = state_root.join("runs");
        fs::create_dir_all(&runs)?;
        let path = runs.join(format!("summary-{}-{}.json", stamp(), std::process::id()));
        summary.path = path.display().to_string();
        write_new_json(&path, &summary)?;
        let exit = u8::from(summary.failed + summary.blocked + summary.skipped > 0);
        Ok(CliOutput::facet_with_status(summary, exit))
    }
}

fn select_keys(
    catalog: &BTreeMap<String, crate::source_projection::projection_catalog::ProjectionEntry>,
    selector: &str,
) -> eyre::Result<Vec<String>> {
    let pattern = glob::Pattern::new(selector)?;
    let keys = catalog
        .iter()
        .filter(|(key, entry)| {
            pattern.matches(key)
                || (!selector.contains('/')
                    && (pattern.matches(&entry.minecraft_version)
                        || key
                            .rsplit_once("/mc-")
                            .is_some_and(|(_, version)| pattern.matches(version))))
        })
        .map(|(key, _)| key.clone())
        .collect::<Vec<_>>();
    ensure!(
        !keys.is_empty(),
        "no catalog projection or Minecraft version matches {selector:?}"
    );
    Ok(keys)
}

fn selection_pattern<'a>(projection: &'a str, mode: RunMode) -> eyre::Result<&'a str> {
    if projection.is_empty() {
        ensure!(
            mode == RunMode::Matrix,
            "sync and build require an explicit --projection selector"
        );
        Ok("*")
    } else {
        Ok(projection)
    }
}

fn selected_stages(values: &[String], mode: RunMode) -> eyre::Result<Vec<Stage>> {
    let requested = if values.is_empty() {
        match mode {
            RunMode::Sync => vec!["sync"],
            RunMode::Build => vec!["check", "compile", "jar"],
            RunMode::Matrix => vec!["check", "compile", "jar", "headless"],
        }
    } else {
        values.iter().map(String::as_str).collect()
    };
    let mut stages = Vec::new();
    for stage in [
        Stage::Sync,
        Stage::Check,
        Stage::Compile,
        Stage::Jar,
        Stage::Headless,
    ] {
        if requested
            .iter()
            .any(|value| Stage::parse(value).ok() == Some(stage))
        {
            stages.push(stage);
        }
    }
    for value in requested {
        Stage::parse(value)?;
    }
    ensure!(
        mode == RunMode::Sync || !stages.contains(&Stage::Sync),
        "use the top-level sync command for source generation"
    );
    ensure!(
        mode != RunMode::Sync || stages == [Stage::Sync],
        "sync performs only guarded source generation"
    );
    Ok(stages)
}

fn is_plain(key: &str) -> bool {
    key.starts_with("sfm-4.34.0/mc-") || key.starts_with("sfm-4.35.0/mc-")
}
fn is_success(row: &Row) -> bool {
    row.status == "passed" || row.status == "reused"
}
fn count(rows: &[Row], status: &str) -> usize {
    rows.iter().filter(|row| row.status == status).count()
}
fn stamp() -> String {
    chrono::Utc::now().format("%Y%m%dT%H%M%S%.3fZ").to_string()
}
fn skipped(key: &str, stage: Stage, reason: &str) -> Row {
    row(key, stage, "skipped", reason, None, None)
}
fn row(
    key: &str,
    stage: Stage,
    status: &str,
    reason: &str,
    receipt: Option<String>,
    identity: Option<String>,
) -> Row {
    Row {
        projection: key.to_owned(),
        stage: stage.label().to_owned(),
        status: status.to_owned(),
        reason: reason.to_owned(),
        receipt,
        identity,
    }
}

fn run_one(ctx: &ContextState, key: &str, stage: Stage, cancellation: &CancellationToken) -> Row {
    let result = match execute_one(ctx, key, stage, cancellation) {
        Ok(row) => row,
        Err(error) => row(key, stage, "failed", &format!("{error:#}"), None, None),
    };
    eprintln!(
        "check-everything {} {}: {} {}",
        key,
        stage.label(),
        result.status,
        result.reason
    );
    result
}

#[expect(
    clippy::too_many_lines,
    reason = "one receipt transaction owns preflight, execution, and atomic terminal publication"
)]
fn execute_one(
    ctx: &ContextState,
    key: &str,
    stage: Stage,
    cancellation: &CancellationToken,
) -> eyre::Result<Row> {
    cancellation.bail_if_cancelled()?;
    let project = ctx.root.join("platform/minecraft/projections").join(key);
    let mut inputs = input_state(ctx, &project, stage)?;
    inputs.insert("stage".to_owned(), stage.label().to_owned());
    inputs.insert(
        "selector".to_owned(),
        if stage == Stage::Headless {
            ctx.filter.clone()
        } else {
            String::new()
        },
    );
    inputs.insert(
        "recipe".to_owned(),
        if matches!(stage, Stage::Compile | Stage::Jar) {
            ctx.recipe.clone().unwrap_or_default()
        } else {
            String::new()
        },
    );
    let mut identity = hash_bytes(facet_json::to_string(&inputs)?.as_bytes());
    let receipt_dir = ctx
        .state_root
        .join("receipts")
        .join(key.replace('/', "_"))
        .join(stage.label());
    fs::create_dir_all(&receipt_dir)?;
    let mut pass_path = receipt_dir.join(format!("{identity}.pass.json"));
    if pass_path.exists() {
        let prior: Receipt = facet_json::from_slice(&fs::read(&pass_path)?)?;
        ensure!(
            prior.schema == SCHEMA && prior.identity == identity && prior.status == "passed",
            "invalid passing receipt {}",
            pass_path.display()
        );
        if output_available(&prior, stage, &project)? {
            return Ok(row(
                key,
                stage,
                "reused",
                "exact passing inputs and output present",
                Some(pass_path.display().to_string()),
                Some(identity),
            ));
        }
        return Ok(row(
            key,
            stage,
            "blocked",
            "passing receipt output is missing or changed; inspect before replacement",
            Some(pass_path.display().to_string()),
            Some(identity),
        ));
    }
    let reason = invalidation(&receipt_dir, &inputs)?;
    let attempt = next_attempt(&receipt_dir, &identity)?;
    let base = receipt_dir.join(format!("{identity}.attempt-{attempt:03}"));
    let started_path = PathBuf::from(format!("{}.started.json", base.display()));
    let terminal_path = PathBuf::from(format!("{}.receipt.json", base.display()));
    let log_path = PathBuf::from(format!("{}.console.log", base.display()));
    let started = stamp();
    write_new_json(
        &started_path,
        &Started {
            schema: SCHEMA.to_owned(),
            identity: identity.clone(),
            pid: std::process::id(),
            at_utc: started.clone(),
        },
    )?;
    eprintln!(
        "check-everything {key} {}: running ({reason})",
        stage.label()
    );
    let (status, detail, code, artifact_path, artifact_sha256) =
        operation(ctx, key, &project, stage, &log_path, cancellation);
    let after = input_state(ctx, &project, stage);
    let (status, detail) = match after {
        Ok(mut after) => {
            after.insert("stage".to_owned(), stage.label().to_owned());
            after.insert("selector".to_owned(), inputs["selector"].clone());
            after.insert("recipe".to_owned(), inputs["recipe"].clone());
            if stage == Stage::Sync
                && sync_authoring_stable(ctx, &inputs, &after)?
                && status == "passed"
            {
                inputs = after;
                identity = hash_bytes(facet_json::to_string(&inputs)?.as_bytes());
                pass_path = receipt_dir.join(format!("{identity}.pass.json"));
                (status, detail)
            } else if after == inputs {
                (status, detail)
            } else {
                (
                    "failed".to_owned(),
                    "relevant source, lock, CLI or artifact changed during the operation"
                        .to_owned(),
                )
            }
        }
        Err(error) => (
            "failed".to_owned(),
            format!("cannot recheck frozen inputs: {error:#}"),
        ),
    };
    let message = if status == "passed" {
        format!("fresh validation; {reason}")
    } else {
        detail
    };
    let receipt = Receipt {
        schema: SCHEMA.to_owned(),
        projection: key.to_owned(),
        stage: stage.label().to_owned(),
        identity: identity.clone(),
        status: status.clone(),
        reason: message.clone(),
        inputs,
        started_utc: started,
        ended_utc: stamp(),
        exit_code: code,
        log: log_path.display().to_string(),
        artifact_path,
        artifact_sha256,
    };
    write_new_json(&terminal_path, &receipt)?;
    if status == "passed" {
        if pass_path.exists() {
            let prior: Receipt = facet_json::from_slice(&fs::read(&pass_path)?)?;
            ensure!(
                prior.schema == SCHEMA
                    && prior.identity == identity
                    && prior.status == "passed"
                    && output_available(&prior, stage, &project)?,
                "existing passing receipt is not valid: {}",
                pass_path.display()
            );
        } else {
            write_new_json(&pass_path, &receipt)?;
        }
    }
    Ok(row(
        key,
        stage,
        &status,
        &message,
        Some(terminal_path.display().to_string()),
        Some(identity),
    ))
}

fn sync_authoring_stable(
    ctx: &ContextState,
    before: &BTreeMap<String, String>,
    after: &BTreeMap<String, String>,
) -> eyre::Result<bool> {
    let core = "platform/minecraft/core-liquid-template";
    Ok(["core_tree", "catalog", "project_inputs", "cli"]
        .iter()
        .all(|name| before.get(*name) == after.get(*name))
        && hash_bytes(&git_bytes(
            &ctx.root,
            &["diff", "--binary", "HEAD", "--", core],
        )?) == before["sync_core_diff"]
        && hash_untracked(&ctx.root, &[core])? == before["sync_core_untracked"])
}

fn invalidation(dir: &Path, inputs: &BTreeMap<String, String>) -> eyre::Result<String> {
    let mut passes = fs::read_dir(dir)?
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| path.to_string_lossy().ends_with(".pass.json"))
        .collect::<Vec<_>>();
    passes.sort_by_key(|path| {
        fs::metadata(path)
            .and_then(|metadata| metadata.modified())
            .ok()
    });
    let Some(path) = passes.pop() else {
        return Ok("no prior passing receipt".to_owned());
    };
    let prior: Receipt = facet_json::from_slice(&fs::read(path)?)?;
    let changed = inputs
        .iter()
        .filter(|(name, value)| prior.inputs.get(*name) != Some(*value))
        .map(|(name, _)| name.as_str())
        .collect::<Vec<_>>();
    Ok(if changed.is_empty() {
        "prior receipt schema or identity changed".to_owned()
    } else {
        format!("input changed: {}", changed.join(", "))
    })
}

fn next_attempt(dir: &Path, identity: &str) -> eyre::Result<usize> {
    Ok(1 + fs::read_dir(dir)?
        .filter_map(Result::ok)
        .filter(|entry| {
            entry
                .file_name()
                .to_string_lossy()
                .starts_with(&format!("{identity}.attempt-"))
                && entry
                    .file_name()
                    .to_string_lossy()
                    .ends_with(".started.json")
        })
        .count())
}

fn output_available(prior: &Receipt, stage: Stage, project: &Path) -> eyre::Result<bool> {
    match stage {
        Stage::Sync => Ok(true),
        Stage::Jar => {
            let (Some(path), Some(hash)) = (&prior.artifact_path, &prior.artifact_sha256) else {
                return Ok(false);
            };
            Ok(Path::new(path).is_file() && hash_file(Path::new(path))? == *hash)
        }
        Stage::Compile => {
            let output = project.join("build/sfm-toolchain/native-project");
            Ok(output.is_dir() && fs::read_dir(output)?.next().is_some())
        }
        Stage::Check | Stage::Headless => Ok(true),
    }
}

#[expect(
    clippy::too_many_lines,
    reason = "all guarded child stages share one bounded launch and result path"
)]
fn operation(
    ctx: &ContextState,
    key: &str,
    project: &Path,
    stage: Stage,
    log: &Path,
    cancellation: &CancellationToken,
) -> (String, String, Option<i32>, Option<String>, Option<String>) {
    let blocked = |message: &str| ("blocked".to_owned(), message.to_owned(), None, None, None);
    if !matches!(stage, Stage::Sync | Stage::Check) {
        match free_bytes(project) {
            Ok(free) if free < ctx.min_free_bytes => {
                return blocked(&format!(
                    "free space {free} below {} bytes",
                    ctx.min_free_bytes
                ));
            }
            Err(error) => return blocked(&format!("cannot measure free space: {error:#}")),
            _ => {}
        }
    }
    if matches!(stage, Stage::Compile | Stage::Jar) && is_plain(key) && ctx.recipe.is_none() {
        return blocked("historical plain projection requires an exact --released-recipe");
    }
    if stage == Stage::Headless
        && ctx.filter == "*"
        && (key.contains("extended") || key.contains("visual"))
    {
        return blocked(
            "manual visual GameTest may be selected; supply a bounded --headless-filter",
        );
    }
    let libs = project.join("build/libs");
    let mut command = Command::new(&ctx.exe);
    command.current_dir(project);
    match stage {
        Stage::Sync => {
            command.args([
                "source",
                "project",
                "sync",
                "--projection",
                key,
                "--output-format",
                "json",
            ]);
        }
        Stage::Check => {
            command.args([
                "source",
                "project",
                "check",
                "--projection",
                key,
                "--output-format",
                "json",
            ]);
        }
        Stage::Compile | Stage::Jar => {
            command.args(["source", "project", stage.label(), "--projection", key]);
            if let Some(recipe) = &ctx.recipe {
                command.args(["--released-recipe", recipe]);
            } else {
                command.args(["--dependency-profile", "rust-toolchain"]);
            }
        }
        Stage::Headless => {
            command.args([
                "game-test",
                "run-server",
                "--projection",
                key,
                "--filter",
                &ctx.filter,
            ]);
        }
    }
    let file = match OpenOptions::new().write(true).create_new(true).open(log) {
        Ok(file) => file,
        Err(error) => {
            return (
                "failed".to_owned(),
                format!("cannot create log: {error}"),
                None,
                None,
                None,
            );
        }
    };
    let stderr = match file.try_clone() {
        Ok(stderr) => stderr,
        Err(error) => {
            return (
                "failed".to_owned(),
                format!("cannot clone log handle: {error}"),
                None,
                None,
                None,
            );
        }
    };
    command
        .stdout(Stdio::from(file))
        .stderr(Stdio::from(stderr));
    if stage == Stage::Jar {
        if let Err(error) = preserve_generated_jar(ctx, key, &libs) {
            return (
                "blocked".to_owned(),
                format!("cannot safely preserve prior generated JAR: {error:#}"),
                None,
                None,
                None,
            );
        }
    }
    let mut child = match command.spawn() {
        Ok(child) => child,
        Err(error) => {
            return (
                "failed".to_owned(),
                format!("cannot start CLI: {error}"),
                None,
                None,
                None,
            );
        }
    };
    let code = loop {
        if cancellation.is_cancelled() {
            let _ = child.kill();
            let _ = child.wait();
            return (
                "failed".to_owned(),
                "cancelled; child stopped, retry on next invocation".to_owned(),
                None,
                None,
                None,
            );
        }
        match child.try_wait() {
            Ok(Some(exit)) => break exit.code(),
            Ok(None) => thread::sleep(Duration::from_millis(100)),
            Err(error) => {
                return (
                    "failed".to_owned(),
                    format!("cannot wait for child: {error}"),
                    None,
                    None,
                    None,
                );
            }
        }
    };
    let content = fs::read_to_string(log).unwrap_or_default();
    let marker = match stage {
        Stage::Sync => content.contains("\"operation\": \"sync\""),
        Stage::Check => {
            content.contains("\"writes_performed\": false")
                && content.contains("\"operation\": \"check\"")
        }
        Stage::Compile => content.contains("\"compilation\": \"completed\""),
        Stage::Jar => content.contains("\"jar_packaging\": \"completed\""),
        Stage::Headless => passed_game_tests(&content),
    };
    if code != Some(0) || !marker {
        let tail = content
            .lines()
            .rfind(|line| !line.trim().is_empty())
            .unwrap_or("");
        return (
            "failed".to_owned(),
            format!(
                "exit={code:?}, completion marker={marker}; {}",
                truncate(tail, 240)
            ),
            code,
            None,
            None,
        );
    }
    if stage == Stage::Jar {
        match generated_jars(&libs) {
            Ok(jars) if jars.len() == 1 => match hash_file(&jars[0]) {
                Ok(hash) => {
                    return (
                        "passed".to_owned(),
                        String::new(),
                        code,
                        Some(jars[0].display().to_string()),
                        Some(hash),
                    );
                }
                Err(error) => {
                    return (
                        "failed".to_owned(),
                        format!("cannot hash generated JAR: {error:#}"),
                        code,
                        None,
                        None,
                    );
                }
            },
            _ => {
                return (
                    "failed".to_owned(),
                    "expected one generated JAR".to_owned(),
                    code,
                    None,
                    None,
                );
            }
        }
    }
    ("passed".to_owned(), String::new(), code, None, None)
}

fn preserve_generated_jar(
    ctx: &ContextState,
    key: &str,
    libs: &Path,
) -> eyre::Result<Option<PathBuf>> {
    let jars = generated_jars(libs)?;
    ensure!(
        jars.len() <= 1,
        "multiple canonical generated JARs in {}",
        libs.display()
    );
    let Some(source) = jars.into_iter().next() else {
        return Ok(None);
    };
    let sha256 = hash_file(&source)?;
    let name = source
        .file_name()
        .ok_or_else(|| eyre::eyre!("generated JAR has no filename"))?
        .to_string_lossy();
    let recorded_utc = stamp();
    let retained = libs.join(format!(
        "{name}.pre-native-{}-{recorded_utc}",
        &sha256[..12]
    ));
    ensure!(
        !retained.exists(),
        "retained JAR collision: {}",
        retained.display()
    );
    let manifest_dir = ctx
        .state_root
        .join("preserved-jars")
        .join(key.replace('/', "_"));
    fs::create_dir_all(&manifest_dir)?;
    let manifest = manifest_dir.join(format!("{recorded_utc}-{}.json", &sha256[..12]));
    write_new_json(
        &manifest,
        &JarPreservation {
            schema: SCHEMA.to_owned(),
            projection: key.to_owned(),
            source: source.display().to_string(),
            retained: retained.display().to_string(),
            sha256: sha256.clone(),
            recorded_utc,
        },
    )?;
    ensure!(
        hash_file(&source)? == sha256,
        "generated JAR changed during preservation preflight"
    );
    fs::rename(&source, &retained).wrap_err_with(|| {
        format!(
            "cannot retain generated JAR {} as {}",
            source.display(),
            retained.display()
        )
    })?;
    ensure!(
        hash_file(&retained)? == sha256,
        "retained JAR hash changed after same-volume rename: {}",
        retained.display()
    );
    Ok(Some(retained))
}

fn generated_jars(dir: &Path) -> eyre::Result<Vec<PathBuf>> {
    if !dir.exists() {
        return Ok(Vec::new());
    }
    Ok(fs::read_dir(dir)?
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| {
            path.file_name()
                .is_some_and(|name| name.to_string_lossy().ends_with("rust.jar"))
        })
        .collect())
}

fn passed_game_tests(content: &str) -> bool {
    content.lines().any(|line| {
        line.find(" required game tests passed").is_some_and(|at| {
            line[..at]
                .split_whitespace()
                .last()
                .and_then(|number| number.parse::<usize>().ok())
                .is_some_and(|count| count > 0)
        })
    })
}
fn truncate(text: &str, max: usize) -> String {
    text.chars().take(max).collect()
}

fn input_state(
    ctx: &ContextState,
    project: &Path,
    stage: Stage,
) -> eyre::Result<BTreeMap<String, String>> {
    let mut fingerprints = BTreeMap::new();
    let core = "platform/minecraft/core-liquid-template";
    let project_relative = project
        .strip_prefix(&ctx.root)?
        .to_string_lossy()
        .replace('\\', "/");
    fingerprints.insert(
        "core_tree".to_owned(),
        git(&ctx.root, &["rev-parse", &format!("HEAD:{core}")])?
            .trim()
            .to_owned(),
    );
    fingerprints.insert(
        "source_diff".to_owned(),
        hash_bytes(&git_bytes(
            &ctx.root,
            &["diff", "--binary", "HEAD", "--", core, &project_relative],
        )?),
    );
    fingerprints.insert(
        "untracked".to_owned(),
        hash_untracked(&ctx.root, &[core, &project_relative])?,
    );
    fingerprints.insert("project_source".to_owned(), hash_project_sources(project)?);
    if stage == Stage::Sync {
        fingerprints.insert(
            "sync_core_diff".to_owned(),
            hash_bytes(&git_bytes(
                &ctx.root,
                &["diff", "--binary", "HEAD", "--", core],
            )?),
        );
        fingerprints.insert(
            "sync_core_untracked".to_owned(),
            hash_untracked(&ctx.root, &[core])?,
        );
    }
    for (name, path) in [
        (
            "catalog",
            ctx.root.join("platform/minecraft/projections.json"),
        ),
        (
            "project_inputs",
            ctx.root.join(core).join("project-inputs.json"),
        ),
        ("lock", project.join("sfm-toolchain.lock.json")),
        ("cli", ctx.exe.clone()),
    ] {
        fingerprints.insert(name.to_owned(), hash_file(&path)?);
    }
    if stage == Stage::Headless {
        let jars = generated_jars(&project.join("build/libs"))?;
        let mut hashes = Vec::new();
        for jar in jars {
            hashes.push((
                jar.file_name().unwrap().to_string_lossy().to_string(),
                hash_file(&jar)?,
            ));
        }
        hashes.sort();
        fingerprints.insert(
            "generated_jar".to_owned(),
            hash_bytes(facet_json::to_string(&hashes)?.as_bytes()),
        );
    }
    Ok(fingerprints)
}

fn hash_project_sources(project: &Path) -> eyre::Result<String> {
    let mut paths = Vec::new();
    for entry in fs::read_dir(project)? {
        let entry = entry?;
        if entry.file_type()?.is_file() {
            paths.push(entry.path());
        }
    }
    for subdir in ["src", "gradle"] {
        let dir = project.join(subdir);
        if !dir.exists() {
            continue;
        }
        for entry in walkdir::WalkDir::new(dir).follow_links(false) {
            let entry = entry?;
            ensure!(
                !entry.file_type().is_symlink(),
                "projection source link: {}",
                entry.path().display()
            );
            if entry.file_type().is_file() {
                paths.push(entry.path().to_path_buf());
            }
        }
    }
    paths.sort();
    let mut digest = Sha256::new();
    for path in paths {
        let relative = path
            .strip_prefix(project)?
            .to_string_lossy()
            .replace('\\', "/");
        digest.update(relative.len().to_le_bytes());
        digest.update(relative.as_bytes());
        digest.update(fs::metadata(&path)?.len().to_le_bytes());
        digest.update(hash_file(&path)?.as_bytes());
    }
    Ok(format!("{:X}", digest.finalize()))
}

fn hash_untracked(root: &Path, paths: &[&str]) -> eyre::Result<String> {
    let mut args = vec!["ls-files", "--others", "--exclude-standard", "--"];
    args.extend_from_slice(paths);
    let text = git(root, &args)?;
    let mut digest = Sha256::new();
    for relative in text.lines() {
        let path = root.join(relative);
        ensure!(path.is_file(), "untracked source disappeared: {relative}");
        digest.update(relative.as_bytes());
        digest.update(hash_file(&path)?.as_bytes());
    }
    Ok(format!("{:X}", digest.finalize()))
}
fn git(root: &Path, args: &[&str]) -> eyre::Result<String> {
    Ok(String::from_utf8(git_bytes(root, args)?)?)
}
fn git_bytes(root: &Path, args: &[&str]) -> eyre::Result<Vec<u8>> {
    let output = Command::new("git")
        .arg("-C")
        .arg(root)
        .args(args)
        .output()?;
    ensure!(
        output.status.success(),
        "git {} failed: {}",
        args.join(" "),
        String::from_utf8_lossy(&output.stderr)
    );
    Ok(output.stdout)
}
fn hash_file(path: &Path) -> eyre::Result<String> {
    let mut file = File::open(path).wrap_err_with(|| format!("cannot read {}", path.display()))?;
    let mut digest = Sha256::new();
    let mut buffer = [0_u8; 16 * 1024];
    loop {
        let count = file.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        digest.update(&buffer[..count]);
    }
    Ok(format!("{:X}", digest.finalize()))
}
fn hash_bytes(bytes: &[u8]) -> String {
    format!("{:X}", Sha256::digest(bytes))
}

fn write_new_json<T: for<'a> Facet<'a>>(path: &Path, value: &T) -> eyre::Result<()> {
    ensure!(!path.exists(), "receipt collision: {}", path.display());
    let temporary = path.with_extension(format!(
        "writing-{}-{}",
        std::process::id(),
        chrono::Utc::now().timestamp_nanos_opt().unwrap_or_default()
    ));
    let mut file = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&temporary)?;
    file.write_all(facet_json::to_string_pretty(value)?.as_bytes())?;
    file.write_all(b"\n")?;
    file.sync_all()?;
    fs::rename(&temporary, path)?;
    Ok(())
}

#[cfg(windows)]
fn free_bytes(path: &Path) -> eyre::Result<u64> {
    #[link(name = "Kernel32")]
    unsafe extern "system" {
        fn GetDiskFreeSpaceExW(
            path: *const u16,
            available: *mut u64,
            total: *mut u64,
            free: *mut u64,
        ) -> i32;
    }
    use std::os::windows::ffi::OsStrExt;
    let drive = path
        .ancestors()
        .last()
        .ok_or_else(|| eyre::eyre!("path has no drive"))?;
    let wide = drive
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect::<Vec<_>>();
    let mut available = 0_u64;
    // SAFETY: the null-terminated path and output pointer remain live for this call.
    let ok = unsafe {
        GetDiskFreeSpaceExW(
            wide.as_ptr(),
            &raw mut available,
            std::ptr::null_mut(),
            std::ptr::null_mut(),
        )
    };
    ensure!(ok != 0, "cannot measure free space for {}", path.display());
    Ok(available)
}
#[cfg(not(windows))]
fn free_bytes(_path: &Path) -> eyre::Result<u64> {
    eyre::bail!("check-everything capacity preflight is not implemented on this OS")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn selects_project_or_version_patterns() {
        use crate::source_projection::projection_catalog::ProjectionEntry;
        let _type_check: Option<ProjectionEntry> = None;
        let pattern = glob::Pattern::new("sfm-dev/*").unwrap();
        assert!(pattern.matches("sfm-dev/mc-1.21.1"));
        assert!(!pattern.matches("sfm-4.35.0/mc-1.21.1"));
    }

    #[test]
    fn everyday_commands_require_an_explicit_projection() {
        assert!(selection_pattern("", RunMode::Sync).is_err());
        assert!(selection_pattern("", RunMode::Build).is_err());
        assert_eq!(selection_pattern("", RunMode::Matrix).unwrap(), "*");
        assert_eq!(selection_pattern("*", RunMode::Build).unwrap(), "*");
    }

    #[test]
    fn stage_selection_is_ordered_and_rejects_unknown() {
        assert_eq!(
            selected_stages(
                &["headless".to_owned(), "check".to_owned()],
                RunMode::Matrix,
            )
            .unwrap(),
            vec![Stage::Check, Stage::Headless]
        );
        assert!(selected_stages(&["graphics".to_owned()], RunMode::Matrix).is_err());
        assert_eq!(
            selected_stages(&[], RunMode::Sync).unwrap(),
            vec![Stage::Sync]
        );
        assert!(selected_stages(&["check".to_owned()], RunMode::Sync).is_err());
        assert!(selected_stages(&["sync".to_owned()], RunMode::Matrix).is_err());
        assert_eq!(
            selected_stages(&[], RunMode::Build).unwrap(),
            vec![Stage::Check, Stage::Compile, Stage::Jar]
        );
    }

    #[test]
    fn interrupted_attempt_retries_and_pass_receipt_reuses() {
        let temporary = tempfile::tempdir().unwrap();
        let dir = temporary.path();
        fs::write(dir.join("abc.attempt-001.started.json"), "{}").unwrap();
        assert_eq!(next_attempt(dir, "abc").unwrap(), 2);
        assert_eq!(
            invalidation(dir, &BTreeMap::new()).unwrap(),
            "no prior passing receipt"
        );
        fs::write(dir.join("abc.attempt-002.started.json"), "{}").unwrap();
        assert_eq!(next_attempt(dir, "abc").unwrap(), 3);
    }

    #[test]
    fn headless_requires_a_positive_selected_count() {
        assert!(!passed_game_tests("Validated 0 required game tests passed"));
        assert!(passed_game_tests("Validated 1 required game tests passed"));
    }
}

#[cfg(test)]
mod resume_tests {
    use super::*;
    use crate::source_projection::projection_catalog::ProjectionEntry;
    use crate::source_projection::projection_catalog::ProjectionEnvironment;

    #[test]
    fn version_selection_uses_checked_catalog_entries() {
        let entries = BTreeMap::from([
            (
                "sfm-dev/mc-1.21.1".to_owned(),
                ProjectionEntry {
                    minecraft_version: "1.21.1".to_owned(),
                    environment: ProjectionEnvironment::Dev,
                    features: Vec::new(),
                },
            ),
            (
                "sfm-dev/mc-1.20.4".to_owned(),
                ProjectionEntry {
                    minecraft_version: "1.20.4".to_owned(),
                    environment: ProjectionEnvironment::Dev,
                    features: Vec::new(),
                },
            ),
            (
                "sfm-dev/mc-1.21.0".to_owned(),
                ProjectionEntry {
                    minecraft_version: "1.21".to_owned(),
                    environment: ProjectionEnvironment::Dev,
                    features: Vec::new(),
                },
            ),
        ]);
        assert_eq!(
            select_keys(&entries, "1.21.*").unwrap(),
            ["sfm-dev/mc-1.21.0", "sfm-dev/mc-1.21.1"]
        );
        assert_eq!(
            select_keys(&entries, "sfm-dev/mc-1.20.*").unwrap(),
            ["sfm-dev/mc-1.20.4"]
        );
        assert_eq!(
            select_keys(&entries, "1.21.0").unwrap(),
            ["sfm-dev/mc-1.21.0"]
        );
        assert!(select_keys(&entries, "../*").is_err());
    }

    #[test]
    fn interrupted_attempt_does_not_publish_a_pass_and_new_attempt_retains_history() {
        let temp = tempfile::tempdir().unwrap();
        let dir = temp.path();
        let identity = "identity";
        write_new_json(
            &dir.join("identity.attempt-001.started.json"),
            &Started {
                schema: SCHEMA.to_owned(),
                identity: identity.to_owned(),
                pid: 3,
                at_utc: "time".to_owned(),
            },
        )
        .unwrap();
        assert!(!dir.join("identity.pass.json").exists());
        assert_eq!(next_attempt(dir, identity).unwrap(), 2);
        write_new_json(
            &dir.join("identity.attempt-002.started.json"),
            &Started {
                schema: SCHEMA.to_owned(),
                identity: identity.to_owned(),
                pid: 4,
                at_utc: "later".to_owned(),
            },
        )
        .unwrap();
        assert_eq!(next_attempt(dir, identity).unwrap(), 3);
        assert!(dir.join("identity.attempt-001.started.json").exists());
    }

    #[test]
    fn changed_input_explains_invalidation_and_old_pass_remains() {
        let temp = tempfile::tempdir().unwrap();
        let dir = temp.path();
        let old = BTreeMap::from([
            ("lock".to_owned(), "first".to_owned()),
            ("cli".to_owned(), "same".to_owned()),
        ]);
        let pass = Receipt {
            schema: SCHEMA.to_owned(),
            projection: "sfm-dev/mc-1.21.1".to_owned(),
            stage: "compile".to_owned(),
            identity: "old".to_owned(),
            status: "passed".to_owned(),
            reason: String::new(),
            inputs: old,
            started_utc: String::new(),
            ended_utc: String::new(),
            exit_code: Some(0),
            log: String::new(),
            artifact_path: None,
            artifact_sha256: None,
        };
        write_new_json(&dir.join("old.pass.json"), &pass).unwrap();
        let changed = BTreeMap::from([
            ("lock".to_owned(), "second".to_owned()),
            ("cli".to_owned(), "same".to_owned()),
        ]);
        assert_eq!(invalidation(dir, &changed).unwrap(), "input changed: lock");
        assert!(dir.join("old.pass.json").is_file());
        assert!(write_new_json(&dir.join("old.pass.json"), &pass).is_err());
    }

    #[test]
    fn missing_compiler_output_invalidates_reuse() {
        let temp = tempfile::tempdir().unwrap();
        let project = temp.path();
        let pass = Receipt {
            schema: SCHEMA.to_owned(),
            projection: String::new(),
            stage: "compile".to_owned(),
            identity: String::new(),
            status: "passed".to_owned(),
            reason: String::new(),
            inputs: BTreeMap::new(),
            started_utc: String::new(),
            ended_utc: String::new(),
            exit_code: Some(0),
            log: String::new(),
            artifact_path: None,
            artifact_sha256: None,
        };
        assert!(!output_available(&pass, Stage::Compile, project).unwrap());
        fs::create_dir_all(project.join("build/sfm-toolchain/native-project")).unwrap();
        assert!(!output_available(&pass, Stage::Compile, project).unwrap());
        fs::write(
            project.join("build/sfm-toolchain/native-project/entry"),
            b"ok",
        )
        .unwrap();
        assert!(output_available(&pass, Stage::Compile, project).unwrap());
    }

    #[test]
    fn cli_parses_native_command() {
        let cli = figue::from_slice::<crate::cli::Cli>(&[
            "check-everything",
            "--projection",
            "sfm-dev/mc-1.21.*",
            "--stage",
            "check",
            "--plan-only",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        match cli.command {
            crate::cli::Command::CheckEverything(args) => {
                assert_eq!(args.projection, "sfm-dev/mc-1.21.*");
                assert_eq!(args.stage, ["check"]);
                assert!(args.plan_only);
            }
            other => panic!("expected native check-everything, got {other:?}"),
        }
    }

    #[test]
    fn cli_parses_everyday_sync_command() {
        let cli = figue::from_slice::<crate::cli::Cli>(&[
            "sync",
            "--projection",
            "sfm-dev/mc-1.20.1",
            "--plan-only",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        match cli.command {
            crate::cli::Command::Sync(args) => {
                assert_eq!(args.projection, "sfm-dev/mc-1.20.1");
                assert!(args.plan_only);
            }
            other => panic!("expected native sync, got {other:?}"),
        }
    }

    #[test]
    fn build_preserves_prior_generated_jar_with_hash_manifest() {
        let temp = tempfile::tempdir().unwrap();
        let libs = temp.path().join("build/libs");
        fs::create_dir_all(&libs).unwrap();
        let source = libs.join("sfm-rust.jar");
        fs::write(&source, b"previous generated package").unwrap();
        let hash = hash_file(&source).unwrap();
        let ctx = ContextState {
            root: temp.path().to_path_buf(),
            state_root: temp.path().join("state"),
            exe: temp.path().join("unused.exe"),
            filter: "*".to_owned(),
            recipe: None,
            min_free_bytes: 0,
        };
        let retained = preserve_generated_jar(&ctx, "sfm-dev/mc-1.20.1", &libs)
            .unwrap()
            .unwrap();
        assert!(!source.exists());
        assert_eq!(hash_file(&retained).unwrap(), hash);
        assert!(generated_jars(&libs).unwrap().is_empty());
        let manifests = ctx.state_root.join("preserved-jars/sfm-dev_mc-1.20.1");
        let path = fs::read_dir(manifests)
            .unwrap()
            .next()
            .unwrap()
            .unwrap()
            .path();
        let recorded: JarPreservation = facet_json::from_slice(&fs::read(path).unwrap()).unwrap();
        assert_eq!(recorded.sha256, hash);
        assert_eq!(Path::new(&recorded.source), source);
        assert_eq!(Path::new(&recorded.retained), retained);
    }
}
