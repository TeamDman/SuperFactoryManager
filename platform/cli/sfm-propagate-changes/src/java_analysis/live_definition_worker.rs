use super::JavaDefinitionLinker;
use super::JavaDependencyResolutionDefinition;
use super::JavaFileFactDetail;
use super::JavaFileFacts;
use super::JavaFileFactsInput;
use super::JavaSourceFile;
use super::JavaSourceWorkspace;
use super::JavaSymbolIndex;
use super::LiveWorkerScheduler;
use super::ScheduledShard;
use super::StableShardKey;
use super::extract_java_file_facts;
use super::extract_java_file_facts_from_text_with_detail;
use crate::cancellation::CancellationToken;
use eyre::WrapErr;
use facet::Facet;
use std::collections::BTreeMap;
use std::io::BufRead;
use std::io::BufReader;
use std::io::BufWriter;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::process::Child;
use std::process::Command;
use std::process::ExitStatus;
use std::time::Duration;
use std::time::Instant;

const LIVE_WORKER_ENV: &str = "SFM_LIVE_DEFINITION_WORKER_REQUEST";
const LIVE_WORKER_GATE_ENV: &str = "SFM_LIVE_DEFINITION_WORKER_GATE";
const LIVE_WORKER_REQUEST_SCHEMA: &str = "sfm.live-definition-worker-request/1";
const LIVE_WORKER_OUTPUT_SCHEMA: &str = "sfm.live-definition-worker-output/1";
const LIVE_WORKER_OUTPUT_LIMIT_BYTES: u64 = 128 * 1024 * 1024;
const LIVE_WORKER_PROCESS_MEMORY_LIMIT_BYTES: usize = 1536 * 1024 * 1024;
const LIVE_WORKER_JOB_MEMORY_LIMIT_BYTES: usize = 6 * 1024 * 1024 * 1024;
const LIVE_WORKER_GATE_TIMEOUT: Duration = Duration::from_secs(30);

#[cfg(windows)]
const LIVE_WORKER_LIMIT: usize = super::LIVE_WORKER_CONCURRENCY;

// Until another platform has an equivalent aggregate child-memory guard, keep
// its fallback serial. This preserves the lifecycle/memory contract instead of
// treating the Windows Job Object as an optional optimization.
#[cfg(not(windows))]
const LIVE_WORKER_LIMIT: usize = 1;

#[derive(Facet, Clone, Debug)]
struct LiveWorkerRequest {
    schema: String,
    output_path: String,
    include_members: bool,
    files: Vec<LiveWorkerSource>,
}

#[derive(Facet, Clone, Debug)]
struct LiveWorkerSource {
    sequence: u64,
    path: String,
    report_path: String,
    source_set: String,
    visible_source_sets: Vec<String>,
}

struct RunningLiveWorker {
    child: Child,
    shard: ScheduledShard,
    output_path: PathBuf,
}

/// Handle the private one-parse definition worker before public CLI parsing.
///
/// # Errors
///
/// Returns an error when the start gate, request, source, parser, or output is
/// invalid.
pub(crate) fn run_live_definition_worker_from_env() -> eyre::Result<bool> {
    let Some(request_path) = std::env::var_os(LIVE_WORKER_ENV) else {
        return Ok(false);
    };
    let gate_path = std::env::var_os(LIVE_WORKER_GATE_ENV)
        .ok_or_else(|| eyre::eyre!("live definition worker start gate is missing"))?;
    wait_for_start_gate(Path::new(&gate_path))?;
    let request: LiveWorkerRequest = facet_json::from_str(&std::fs::read_to_string(request_path)?)?;
    if request.schema != LIVE_WORKER_REQUEST_SCHEMA {
        eyre::bail!("unsupported live definition worker request schema");
    }
    let mut writer = BufWriter::new(std::fs::File::create(&request.output_path)?);
    writeln!(writer, "{LIVE_WORKER_OUTPUT_SCHEMA}")?;
    for file in request.files {
        let source = std::fs::read_to_string(&file.path)
            .map_err(|error| eyre::eyre!("Failed to read Java source {}: {error}", file.path))?;
        let facts = extract_java_file_facts_from_text_with_detail(
            JavaFileFactsInput {
                sequence: file.sequence,
                report_path: &file.report_path,
                source_set: &file.source_set,
                visible_source_sets: &file.visible_source_sets,
            },
            source,
            Some(8),
            if request.include_members {
                JavaFileFactDetail::Declarations
            } else {
                JavaFileFactDetail::TypesOnly
            },
        )?;
        facet_json::to_writer_std(&mut writer, &facts)?;
        writer.write_all(b"\n")?;
    }
    writer.flush()?;
    Ok(true)
}

/// Build live definition declarations with one parse per source. Completed
/// shards are linked immediately while the remaining child workers continue.
#[expect(
    clippy::too_many_lines,
    reason = "the supervisor loop keeps child ownership, streaming admission, bisection, and cleanup in one auditable lifecycle"
)]
pub(crate) fn build_live_definition_index_streaming(
    workspace: &JavaSourceWorkspace,
    external: &[JavaDependencyResolutionDefinition],
    include_members: bool,
    cancellation_token: &CancellationToken,
) -> eyre::Result<JavaSymbolIndex> {
    cancellation_token.bail_if_cancelled()?;
    let temporary = tempfile::tempdir()?;
    let mut files = workspace.files.iter().collect::<Vec<_>>();
    files.sort_by(|left, right| {
        (&left.report_path, &left.source_set, &left.absolute_path).cmp(&(
            &right.report_path,
            &right.source_set,
            &right.absolute_path,
        ))
    });
    let visible_source_sets = workspace
        .context
        .source_sets
        .iter()
        .map(|source_set| {
            let mut visible = source_set.visible_source_sets.clone();
            visible.push(source_set.id.clone());
            visible.sort();
            visible.dedup();
            (source_set.id.clone(), visible)
        })
        .collect::<BTreeMap<_, _>>();
    // Start with one balanced shard per worker. The hard process/job limits
    // and deterministic failure bisection are the resource boundary; forcing
    // 32-source shards here would reintroduce dozens of process startups even
    // though each worker streams one file record at a time.
    let maximum_shard_size = files.len().div_ceil(LIVE_WORKER_LIMIT).max(1);
    let mut scheduler =
        LiveWorkerScheduler::new(files.len(), LIVE_WORKER_LIMIT, maximum_shard_size)?;
    let worker_job = SharedWorkerJob::new()?;
    let mut running = BTreeMap::<StableShardKey, RunningLiveWorker>::new();
    let mut linker = JavaDefinitionLinker::new(workspace.context.clone(), external);
    let started = Instant::now();

    let result = (|| {
        while scheduler.pending_count() > 0 || scheduler.running_count() > 0 {
            cancellation_token.bail_if_cancelled()?;
            for shard in scheduler.dispatch_ready()? {
                let worker = spawn_live_worker(
                    &shard,
                    &files,
                    &visible_source_sets,
                    include_members,
                    temporary.path(),
                    &worker_job,
                )?;
                tracing::info!(
                    target: "sfm::java_analysis",
                    event = "SFM_JAVA_LIVE_WORKER_STARTED",
                    shard = %shard.key(),
                    source_count = shard.range().len(),
                    active_workers = running.len() + 1,
                    "live Java definition worker started"
                );
                running.insert(shard.key().clone(), worker);
            }

            let completed = poll_completed_workers(&mut running)?;
            if completed.is_empty() {
                std::thread::sleep(Duration::from_millis(10));
                continue;
            }
            for (key, mut worker, status) in completed {
                if status.success() {
                    let facts = read_live_worker_output(&worker.output_path)?;
                    let expected = worker.shard.range().len();
                    if facts.len() != expected {
                        eyre::bail!(
                            "live definition worker {key} returned {} file records, expected {expected}",
                            facts.len()
                        );
                    }
                    for facts in facts {
                        linker.ingest(facts)?;
                    }
                    scheduler.complete_success(&key)?;
                    tracing::info!(
                        target: "sfm::java_analysis",
                        event = "SFM_JAVA_LIVE_WORKER_COMPLETED",
                        shard = %key,
                        source_count = expected,
                        active_workers = running.len(),
                        elapsed_ms = u64::try_from(started.elapsed().as_millis()).unwrap_or(u64::MAX),
                        "live Java definition facts admitted while worker production remains active"
                    );
                } else {
                    let disposition = scheduler.complete_failure(&key)?;
                    tracing::warn!(
                        target: "sfm::java_analysis",
                        event = "SFM_JAVA_LIVE_WORKER_FAILED",
                        shard = %key,
                        %status,
                        disposition = ?disposition,
                        "live Java definition worker failed"
                    );
                    if worker.shard.range().is_single_source() {
                        eyre::bail!(
                            "live definition worker for source ordinal {} exited with {status}",
                            worker.shard.range().start()
                        );
                    }
                }
                // `try_wait` reaped the process. Keep this explicit so future
                // process backends cannot accidentally rely on Drop semantics.
                let _ = worker.child.wait();
            }
        }
        let leaves = scheduler.seal()?;
        let index = linker.seal(files.len())?;
        tracing::info!(
            target: "sfm::java_analysis",
            event = "SFM_JAVA_LIVE_QUERY_FANOUT_COMPLETED",
            source_files = files.len(),
            successful_shards = leaves.len(),
            peak_active_workers = scheduler.peak_active_workers(),
            elapsed_ms = u64::try_from(started.elapsed().as_millis()).unwrap_or(u64::MAX),
            "live Java definition fan-out and streaming link completed"
        );
        Ok(index)
    })();

    match result {
        Ok(index) => Ok(index),
        Err(error) => {
            worker_job.terminate();
            let cleanup_failures = kill_and_wait_all(&mut running);
            if cleanup_failures.is_empty() {
                Err(error)
            } else {
                Err(error.wrap_err(format!(
                    "live definition worker cleanup also failed: {}",
                    cleanup_failures.join("; ")
                )))
            }
        }
    }
}

/// Use the same one-parse fact/link contract for isolated tiny roots without
/// paying child-process startup costs or taking the machine-wide branch lock.
pub(crate) fn build_live_definition_index_in_process(
    workspace: &JavaSourceWorkspace,
    external: &[JavaDependencyResolutionDefinition],
) -> eyre::Result<JavaSymbolIndex> {
    let mut files = workspace.files.iter().collect::<Vec<_>>();
    files.sort_by(|left, right| {
        (&left.report_path, &left.source_set, &left.absolute_path).cmp(&(
            &right.report_path,
            &right.source_set,
            &right.absolute_path,
        ))
    });
    let visible = workspace
        .context
        .source_sets
        .iter()
        .map(|source_set| {
            (
                source_set.id.clone(),
                source_set.visible_source_sets.clone(),
            )
        })
        .collect::<BTreeMap<_, _>>();
    let mut linker = JavaDefinitionLinker::new(workspace.context.clone(), external);
    for (sequence, file) in files.iter().enumerate() {
        linker.ingest(extract_java_file_facts(
            file,
            u64::try_from(sequence).wrap_err("live source sequence does not fit u64")?,
            visible
                .get(&file.source_set)
                .map(Vec::as_slice)
                .unwrap_or_default(),
            None,
        )?)?;
    }
    linker.seal(files.len())
}

fn wait_for_start_gate(path: &Path) -> eyre::Result<()> {
    let started = Instant::now();
    while !path.exists() {
        if started.elapsed() >= LIVE_WORKER_GATE_TIMEOUT {
            eyre::bail!("live definition worker timed out waiting for its start gate");
        }
        std::thread::sleep(Duration::from_millis(1));
    }
    Ok(())
}

fn spawn_live_worker(
    shard: &ScheduledShard,
    files: &[&JavaSourceFile],
    visible_source_sets: &BTreeMap<String, Vec<String>>,
    include_members: bool,
    temporary: &Path,
    worker_job: &SharedWorkerJob,
) -> eyre::Result<RunningLiveWorker> {
    let shard_name = shard.key().to_string().replace(['.', ':'], "-");
    let request_path = temporary.join(format!("{shard_name}-request.json"));
    let output_path = temporary.join(format!("{shard_name}-output.ndjson"));
    let gate_path = temporary.join(format!("{shard_name}-start.gate"));
    let mut request_files = Vec::with_capacity(shard.range().len());
    for sequence in shard.range().start()..shard.range().end() {
        let file = files
            .get(sequence)
            .ok_or_else(|| eyre::eyre!("live worker source ordinal {sequence} is out of range"))?;
        request_files.push(LiveWorkerSource {
            sequence: u64::try_from(sequence)
                .wrap_err("live worker source sequence does not fit u64")?,
            path: file.absolute_path.display().to_string(),
            report_path: file.report_path.clone(),
            source_set: file.source_set.clone(),
            visible_source_sets: visible_source_sets
                .get(&file.source_set)
                .cloned()
                .unwrap_or_else(|| vec![file.source_set.clone()]),
        });
    }
    let request = LiveWorkerRequest {
        schema: LIVE_WORKER_REQUEST_SCHEMA.to_owned(),
        output_path: output_path.display().to_string(),
        include_members,
        files: request_files,
    };
    let mut writer = BufWriter::new(std::fs::File::create(&request_path)?);
    facet_json::to_writer_std(&mut writer, &request)?;
    writer.flush()?;
    drop(writer);

    let mut command = Command::new(std::env::current_exe()?);
    command
        .env(LIVE_WORKER_ENV, &request_path)
        .env(LIVE_WORKER_GATE_ENV, &gate_path);
    let mut child = command.spawn()?;
    if let Err(error) = worker_job.assign(&child) {
        let _ = child.kill();
        let _ = child.wait();
        return Err(error.wrap_err("failed to assign live definition worker to memory job"));
    }
    if let Err(error) = std::fs::File::create(&gate_path) {
        let _ = child.kill();
        let _ = child.wait();
        return Err(error.into());
    }
    Ok(RunningLiveWorker {
        child,
        shard: shard.clone(),
        output_path,
    })
}

fn poll_completed_workers(
    running: &mut BTreeMap<StableShardKey, RunningLiveWorker>,
) -> eyre::Result<Vec<(StableShardKey, RunningLiveWorker, ExitStatus)>> {
    let mut completed_keys = Vec::new();
    for (key, worker) in running.iter_mut() {
        if let Some(status) = worker.child.try_wait()? {
            completed_keys.push((key.clone(), status));
        }
    }
    let mut completed = Vec::with_capacity(completed_keys.len());
    for (key, status) in completed_keys {
        let worker = running
            .remove(&key)
            .expect("completed worker remains in the running map");
        completed.push((key, worker, status));
    }
    Ok(completed)
}

fn read_live_worker_output(path: &Path) -> eyre::Result<Vec<JavaFileFacts>> {
    let bytes = std::fs::metadata(path)?.len();
    if bytes > LIVE_WORKER_OUTPUT_LIMIT_BYTES {
        eyre::bail!(
            "live definition worker output exceeded {LIVE_WORKER_OUTPUT_LIMIT_BYTES} bytes"
        );
    }
    let file = std::fs::File::open(path)?;
    let mut lines = BufReader::new(file).lines();
    let schema = lines
        .next()
        .transpose()?
        .ok_or_else(|| eyre::eyre!("live definition worker output is empty"))?;
    if schema != LIVE_WORKER_OUTPUT_SCHEMA {
        eyre::bail!("unsupported live definition worker output schema");
    }
    let mut facts = Vec::new();
    for line in lines {
        let line = line?;
        if line.is_empty() {
            continue;
        }
        let record: JavaFileFacts = facet_json::from_str(&line)?;
        record.validate()?;
        facts.push(record);
    }
    facts.sort_by_key(|record| record.file.sequence);
    for pair in facts.windows(2) {
        if pair[0].file.sequence == pair[1].file.sequence {
            eyre::bail!(
                "live definition worker output duplicates source sequence {}",
                pair[0].file.sequence
            );
        }
    }
    Ok(facts)
}

fn kill_and_wait_all(running: &mut BTreeMap<StableShardKey, RunningLiveWorker>) -> Vec<String> {
    let mut failures = Vec::new();
    for (key, worker) in running.iter_mut() {
        if let Err(error) = worker.child.kill()
            && worker.child.try_wait().ok().flatten().is_none()
        {
            failures.push(format!("could not kill worker {key}: {error}"));
        }
    }
    for (key, worker) in running.iter_mut() {
        if let Err(error) = worker.child.wait() {
            failures.push(format!("could not wait for worker {key}: {error}"));
        }
    }
    running.clear();
    failures
}

#[cfg(windows)]
struct SharedWorkerJob(windows::Win32::Foundation::HANDLE);

#[cfg(windows)]
impl SharedWorkerJob {
    fn new() -> eyre::Result<Self> {
        use windows::Win32::System::JobObjects::CreateJobObjectW;
        use windows::Win32::System::JobObjects::JOB_OBJECT_LIMIT_ACTIVE_PROCESS;
        use windows::Win32::System::JobObjects::JOB_OBJECT_LIMIT_JOB_MEMORY;
        use windows::Win32::System::JobObjects::JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        use windows::Win32::System::JobObjects::JOB_OBJECT_LIMIT_PROCESS_MEMORY;
        use windows::Win32::System::JobObjects::JOBOBJECT_EXTENDED_LIMIT_INFORMATION;
        use windows::Win32::System::JobObjects::JobObjectExtendedLimitInformation;
        use windows::Win32::System::JobObjects::SetInformationJobObject;

        // SAFETY: null security attributes and no name create a private job.
        let handle = unsafe { CreateJobObjectW(None, None) }?;
        let job = Self(handle);
        let mut information = JOBOBJECT_EXTENDED_LIMIT_INFORMATION::default();
        information.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
            | JOB_OBJECT_LIMIT_PROCESS_MEMORY
            | JOB_OBJECT_LIMIT_JOB_MEMORY
            | JOB_OBJECT_LIMIT_ACTIVE_PROCESS;
        information.BasicLimitInformation.ActiveProcessLimit = 4;
        information.ProcessMemoryLimit = LIVE_WORKER_PROCESS_MEMORY_LIMIT_BYTES;
        information.JobMemoryLimit = LIVE_WORKER_JOB_MEMORY_LIMIT_BYTES;
        let size = u32::try_from(std::mem::size_of_val(&information))?;
        // SAFETY: the job handle and information pointer remain valid for the
        // exact declared byte size during this call.
        unsafe {
            SetInformationJobObject(
                job.0,
                JobObjectExtendedLimitInformation,
                std::ptr::from_ref(&information).cast(),
                size,
            )
        }?;
        Ok(job)
    }

    fn assign(&self, child: &Child) -> eyre::Result<()> {
        use std::os::windows::io::AsRawHandle;
        use windows::Win32::Foundation::HANDLE;
        use windows::Win32::System::JobObjects::AssignProcessToJobObject;
        // SAFETY: the job is live and the child owns a live process handle.
        unsafe { AssignProcessToJobObject(self.0, HANDLE(child.as_raw_handle())) }?;
        Ok(())
    }

    fn terminate(&self) {
        use windows::Win32::System::JobObjects::TerminateJobObject;
        // SAFETY: the job handle remains live for this guard's lifetime.
        let _ = unsafe { TerminateJobObject(self.0, 1) };
    }

    #[cfg(test)]
    fn query_limits(
        &self,
    ) -> eyre::Result<windows::Win32::System::JobObjects::JOBOBJECT_EXTENDED_LIMIT_INFORMATION>
    {
        use windows::Win32::System::JobObjects::JOBOBJECT_EXTENDED_LIMIT_INFORMATION;
        use windows::Win32::System::JobObjects::JobObjectExtendedLimitInformation;
        use windows::Win32::System::JobObjects::QueryInformationJobObject;

        let mut information = JOBOBJECT_EXTENDED_LIMIT_INFORMATION::default();
        let size = u32::try_from(std::mem::size_of_val(&information))?;
        let mut returned = 0_u32;
        // SAFETY: the output structure is live and writable for the exact size
        // supplied, and the job handle remains valid for this guard's lifetime.
        unsafe {
            QueryInformationJobObject(
                Some(self.0),
                JobObjectExtendedLimitInformation,
                std::ptr::from_mut(&mut information).cast(),
                size,
                Some(std::ptr::from_mut(&mut returned)),
            )
        }?;
        if returned != size {
            eyre::bail!(
                "Windows returned {returned} bytes for live worker job limits; expected {size}"
            );
        }
        Ok(information)
    }
}

#[cfg(windows)]
impl Drop for SharedWorkerJob {
    fn drop(&mut self) {
        // SAFETY: this guard owns the handle and closes it exactly once.
        let _ = unsafe { windows::Win32::Foundation::CloseHandle(self.0) };
    }
}

#[cfg(not(windows))]
struct SharedWorkerJob;

#[cfg(not(windows))]
impl SharedWorkerJob {
    fn new() -> eyre::Result<Self> {
        Ok(Self)
    }

    fn assign(&self, _child: &Child) -> eyre::Result<()> {
        Ok(())
    }

    fn terminate(&self) {}
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn worker_output_accepts_schema_only_empty_snapshot() {
        let temporary = tempfile::tempdir().unwrap();
        let output = temporary.path().join("output.ndjson");
        let mut writer = BufWriter::new(std::fs::File::create(&output).unwrap());
        writeln!(writer, "{LIVE_WORKER_OUTPUT_SCHEMA}").unwrap();
        // Schema-only output is valid and keeps this parser test independent
        // from Java extraction fixtures.
        writer.flush().unwrap();
        assert!(read_live_worker_output(&output).unwrap().is_empty());
    }

    #[test]
    fn worker_output_rejects_duplicate_sequences() {
        let temporary = tempfile::tempdir().unwrap();
        let output = temporary.path().join("output.ndjson");
        let visible = vec!["main".to_owned()];
        let facts = extract_java_file_facts_from_text_with_detail(
            JavaFileFactsInput {
                sequence: 7,
                report_path: "src/main/java/example/A.java",
                source_set: "main",
                visible_source_sets: &visible,
            },
            "package example; public class A {}".to_owned(),
            None,
            JavaFileFactDetail::TypesOnly,
        )
        .unwrap();
        let mut writer = BufWriter::new(std::fs::File::create(&output).unwrap());
        writeln!(writer, "{LIVE_WORKER_OUTPUT_SCHEMA}").unwrap();
        facet_json::to_writer_std(&mut writer, &facts).unwrap();
        writer.write_all(b"\n").unwrap();
        facet_json::to_writer_std(&mut writer, &facts).unwrap();
        writer.write_all(b"\n").unwrap();
        writer.flush().unwrap();

        let error = read_live_worker_output(&output).unwrap_err().to_string();
        assert!(error.contains("duplicates source sequence 7"), "{error}");
    }

    #[test]
    fn cleanup_kills_waits_and_removes_every_owned_child() {
        let temporary = tempfile::tempdir().unwrap();
        let output = temporary.path().join("unused.ndjson");
        let mut command = long_running_test_command();
        let child = command.spawn().unwrap();
        let mut scheduler = LiveWorkerScheduler::new(1, 1, 1).unwrap();
        let shard = scheduler.dispatch_ready().unwrap().remove(0);
        let mut running = BTreeMap::from([(
            shard.key().clone(),
            RunningLiveWorker {
                child,
                shard,
                output_path: output,
            },
        )]);

        let failures = kill_and_wait_all(&mut running);

        assert!(failures.is_empty(), "{failures:?}");
        assert!(running.is_empty());
    }

    #[cfg(windows)]
    fn long_running_test_command() -> Command {
        let mut command = Command::new("cmd.exe");
        command.args(["/D", "/C", "ping -n 60 127.0.0.1 >NUL"]);
        command
    }

    #[cfg(not(windows))]
    fn long_running_test_command() -> Command {
        let mut command = Command::new("sleep");
        command.arg("60");
        command
    }

    #[cfg(windows)]
    #[test]
    fn shared_worker_job_reports_the_configured_hard_limits() {
        use windows::Win32::System::JobObjects::JOB_OBJECT_LIMIT_ACTIVE_PROCESS;
        use windows::Win32::System::JobObjects::JOB_OBJECT_LIMIT_JOB_MEMORY;
        use windows::Win32::System::JobObjects::JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        use windows::Win32::System::JobObjects::JOB_OBJECT_LIMIT_PROCESS_MEMORY;

        let job = SharedWorkerJob::new().unwrap();
        let limits = job.query_limits().unwrap();
        let flags = limits.BasicLimitInformation.LimitFlags;
        assert!(flags.contains(JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE));
        assert!(flags.contains(JOB_OBJECT_LIMIT_PROCESS_MEMORY));
        assert!(flags.contains(JOB_OBJECT_LIMIT_JOB_MEMORY));
        assert!(flags.contains(JOB_OBJECT_LIMIT_ACTIVE_PROCESS));
        assert_eq!(limits.BasicLimitInformation.ActiveProcessLimit, 4);
        assert_eq!(
            limits.ProcessMemoryLimit,
            LIVE_WORKER_PROCESS_MEMORY_LIMIT_BYTES
        );
        assert_eq!(limits.JobMemoryLimit, LIVE_WORKER_JOB_MEMORY_LIMIT_BYTES);
    }
}
