//! Test-only fixed-wave process capture with explicit mechanics-test admission.
//! Parent module: core_slice_test_support; its existing read_blob_batch is reused.
//! Windows borrows std Child pipe handles: no Tokio Blocking pipe adapters/tasks.
//! This is source for review, not a fully bounded Windows runtime claim.
#![cfg(test)]

use super::super::release_baseline::frozen_git_command;
use super::MAX_BLOB_TOTAL_BYTES;
use super::read_blob_batch;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fmt;
use std::io::Cursor;
use std::io::Read;
#[cfg(windows)]
use std::io::Seek;
use std::io::Write;
use std::io::{self};
use std::path::Path;
use std::process::Child;
use std::process::Command;
use std::process::ExitStatus;
use std::process::Stdio;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::time::Duration;
use tokio::time::Instant;

const CHUNK_BYTES: usize = 8192;
const TURNS_PER_STREAM: usize = 8;
const STDERR_BYTES: usize = 65536;
const TREE_BYTES: usize = 8192;
const WAVE_INPUT_BYTES: usize = 15 * 41;
const WAVE_RAW_BYTES: usize = 16 * 1024 * 1024 + 15 * (256 + 1);
const WORK: Duration = Duration::from_secs(10);
const DRAIN: Duration = Duration::from_secs(1);
const REAP: Duration = Duration::from_secs(5);
const POLL: Duration = Duration::from_millis(5);
// Only this new fixed-wave boundary is poisoned after unresolved owned-root cleanup.
// Old shared read_git_blobs callers remain unchanged.
static CLEANUP_UNRESOLVED: AtomicBool = AtomicBool::new(false);
const WAVE_OIDS: [&str; 15] = [
    "8a2665cb80e5851db9d8caf5f8dad27b02d718e5",
    "0090f839d3cfeafeb9a6f5d7b369f86c499fb3db",
    "57e957303154db2ccb0cd257363d5f5a306236f2",
    "c558061b6e46b81f699c60527cfe26695198e053",
    "cd1dba30f6974c912c60c72851ce2954aeb6cb97",
    "8a5a413ca781030608593b6c8e505891abdff5d0",
    "6b12b7ba6f95ebb726ee528d85e19ecd53b1549b",
    "d3d9392be09c13f784294e2d82bd1e8cc82afb24",
    "5c59deb5f45ca741393f2ab1904133842ac11306",
    "0fd30f9f5234f33a09b0552d337cf9185ac94b6a",
    "e084135763817e65fdf09f79fa9c39de1bf6035e",
    "9fb213b027d928597e254b27cd89e68949e0946b",
    "ebd8f404ca087835a7a3f6285f99591f83d31668",
    "27e4d66f79605a201831e3280f7ac5b718229bf8",
    "3ea5a31009d5309f676e8b2a427f723614f4e790",
];
const WAVE_COMMITS: [&str; 20] = [
    "f3ff2f6425434f36c7c680fa909c977b158e1860",
    "2e3b561c15d663fb89fd353ccc2af67eeb0c2053",
    "6bf4845761d06560fc5e0e36018b5d583589004d",
    "faa040ce14dd825f2dd9716ea59508bf46278c06",
    "a829fb4db2ae06eddd2defb22e33f0b45a4b3ff9",
    "704aa69edad5376d8d6cfb0b0ef7845af077e647",
    "11d3ed07d654ff801329f17cf1eb81c2b347eecd",
    "43068d610b1c053c6569be486439769eec2ae9ef",
    "7524ab5512878b773e212600c9578b2bc4db4717",
    "6bd27f03863ddd041344cbc3da51b01a7b3c3e1f",
    "31135b8e86801b862d5cb2283c7c5878b7cc5bb4",
    "23785b63e3e1fe35e5be6a6d89b6638ee0ff0daa",
    "3df18123a19535fd0e5d1dc81aa302105c3fd2f6",
    "bb5babf12f467235b3a44ad5098666ee3ed171ec",
    "cfbbafaeda4a006ae32743a92de330711983056b",
    "1b7f9605da0ef13c7601daf3786545868dfc3c78",
    "a637581b5e1078d7cc0ca68333add568e3e387ff",
    "6bfab8a21e7a5bbeb0bb136bf4e5b26f6ce03e25",
    "f5366c79c823ff52712130e69dd9c8166c70bd14",
    "fe32b29453b13b4f3050ad441677c7eb79e80814",
];
const WAVE_PATHS: [&str; 13] = [
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientCommandInsertion.java",
    "src/main/java/ca/teamdman/sfm/client/command/SFMCommandHistoryService.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionArgumentHistory.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionInvocationTrace.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMPaletteCandidateInspection.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMPaletteCandidateCopyAction.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMPaletteCandidateSetCopyAction.java",
    "src/main/java/ca/teamdman/sfm/client/action/PaletteHistoryClearAction.java",
    "src/main/java/ca/teamdman/sfm/client/action/PaletteHistoryPersistenceAction.java",
    "src/main/java/ca/teamdman/sfm/client/command/SFMCommandHistory.java",
    "src/main/java/ca/teamdman/sfm/client/command/SFMCommandHistoryCodec.java",
    "src/main/java/ca/teamdman/sfm/client/screen/SFMTransientActionScreen.java",
    "src/main/java/ca/teamdman/sfm/client/action/ClosePaletteAction.java",
];

#[derive(Clone, Copy, Debug)]
struct Limits {
    stdout: usize,
    stderr: usize,
    input: usize,
    work: Duration,
    drain: Duration,
    reap: Duration,
}
impl Limits {
    fn wave(stdout: usize, input: usize) -> Self {
        Self {
            stdout,
            stderr: STDERR_BYTES,
            input,
            work: WORK,
            drain: DRAIN,
            reap: REAP,
        }
    }
    fn valid(self) -> bool {
        self.stdout > 0
            && self.stdout <= WAVE_RAW_BYTES
            && self.stderr > 0
            && self.stderr <= STDERR_BYTES
            && self.input <= WAVE_INPUT_BYTES
            && !self.work.is_zero()
            && self.work <= WORK
            && !self.drain.is_zero()
            && self.drain <= DRAIN
            && !self.reap.is_zero()
            && self.reap <= REAP
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum Phase {
    Preflight,
    Work,
    Drain,
    Cleanup,
    Parse,
}
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum Stream {
    Stdout,
    Stderr,
}
#[derive(Debug)]
enum Failure {
    InvalidLimits,
    InputTooLarge,
    PriorCleanupUnresolved,
    NestedRuntime,
    RuntimeBuild,
    #[cfg(not(windows))]
    UnsupportedPlatform,
    Spawn,
    MissingPipe,
    InvalidPipe,
    InputPrepare,
    InputIncomplete,
    StreamRead(Stream),
    Overflow(Stream),
    Allocation(Stream),
    CounterOverflow(Stream),
    Wait,
    WorkDeadline,
    DrainDeadline,
    NonzeroExit,
    Parse,
}
impl Failure {
    fn stream(&self) -> Option<Stream> {
        match self {
            Self::StreamRead(stream)
            | Self::Overflow(stream)
            | Self::Allocation(stream)
            | Self::CounterOverflow(stream) => Some(*stream),
            _ => None,
        }
    }
}
struct IoCause {
    operation: &'static str,
    kind: io::ErrorKind,
    raw_os_error: Option<i32>,
    message: String,
}
impl fmt::Debug for IoCause {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("IoCause")
            .field("operation", &self.operation)
            .field("kind", &self.kind)
            .field("raw_os_error", &self.raw_os_error)
            .field("message", &self.message)
            .finish()
    }
}
fn diagnostic_prefix(value: &impl fmt::Display) -> String {
    // Bound retained diagnostic text during formatting, not after an unbounded to_string.
    struct Prefix(String);
    impl fmt::Write for Prefix {
        fn write_str(&mut self, text: &str) -> fmt::Result {
            let mut end = text.len().min(2048 - self.0.len());
            while !text.is_char_boundary(end) {
                end -= 1;
            }
            self.0.push_str(&text[..end]);
            Ok(())
        }
    }
    let mut text = Prefix(String::new());
    let _ = fmt::write(&mut text, format_args!("{value}"));
    text.0
}
fn io_cause(operation: &'static str, error: &io::Error) -> IoCause {
    IoCause {
        operation,
        kind: error.kind(),
        raw_os_error: error.raw_os_error(),
        message: diagnostic_prefix(error),
    }
}

#[derive(Default)]
struct Capture {
    started: bool,
    phase: Option<Phase>,
    failure_phase: Option<Phase>,
    status: Option<ExitStatus>,
    reaped: bool,
    stdout: Vec<u8>,
    stderr: Vec<u8>,
    stdout_observed: usize,
    stderr_observed: usize,
    stdout_eof: bool,
    stderr_eof: bool,
    input_written: usize,
    input_complete: bool,
    input_from_file: bool,
    local_pipes_closed: bool,
    kill_requested: bool,
    cleanup_deadline: bool,
    primary_io: Option<IoCause>,
    kill_io: Option<IoCause>,
    reap_io: Option<IoCause>,
}
impl fmt::Debug for Capture {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        // Never format raw streams, argv, paths or process IDs into diagnostics.
        f.debug_struct("Capture")
            .field("started", &self.started)
            .field("phase", &self.phase)
            .field("failure_phase", &self.failure_phase)
            .field("status", &self.status)
            .field("reaped", &self.reaped)
            .field("stdout_retained", &self.stdout.len())
            .field("stderr_retained", &self.stderr.len())
            .field("stdout_observed", &self.stdout_observed)
            .field("stderr_observed", &self.stderr_observed)
            .field("stdout_eof", &self.stdout_eof)
            .field("stderr_eof", &self.stderr_eof)
            .field("input_written", &self.input_written)
            .field("input_complete", &self.input_complete)
            .field("input_from_file", &self.input_from_file)
            .field("local_pipes_closed", &self.local_pipes_closed)
            .field("kill_requested", &self.kill_requested)
            .field("cleanup_deadline", &self.cleanup_deadline)
            .field("primary_io", &self.primary_io)
            .field("kill_io", &self.kill_io)
            .field("reap_io", &self.reap_io)
            .finish()
    }
}
#[derive(Debug)]
struct CaptureError {
    reason: Failure,
    capture: Capture,
}
impl fmt::Display for CaptureError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "fixed-wave capture refused: {:?}; stream={:?}; {:?}",
            self.reason,
            self.reason.stream(),
            self.capture
        )
    }
}
impl std::error::Error for CaptureError {}
fn refusal(reason: Failure, capture: Capture) -> CaptureError {
    CaptureError { reason, capture }
}

fn append_chunk(
    capture: &mut Capture,
    stream: Stream,
    bytes: &[u8],
    limit: usize,
) -> std::result::Result<(), Failure> {
    let (observed, retained) = match stream {
        Stream::Stdout => (&mut capture.stdout_observed, &mut capture.stdout),
        Stream::Stderr => (&mut capture.stderr_observed, &mut capture.stderr),
    };
    *observed = observed
        .checked_add(bytes.len())
        .ok_or(Failure::CounterOverflow(stream))?;
    let next = retained
        .len()
        .checked_add(bytes.len())
        .ok_or(Failure::CounterOverflow(stream))?;
    if next > limit {
        return Err(Failure::Overflow(stream));
    }
    retained
        .try_reserve_exact(bytes.len())
        .map_err(|_| Failure::Allocation(stream))?;
    retained.extend_from_slice(bytes);
    Ok(())
}

fn capture_owned(
    command: Command,
    input: Option<Vec<u8>>,
    limits: Limits,
) -> std::result::Result<Capture, CaptureError> {
    let capture = Capture {
        phase: Some(Phase::Preflight),
        ..Capture::default()
    };
    if !limits.valid() {
        return Err(refusal(Failure::InvalidLimits, capture));
    }
    if input
        .as_ref()
        .is_some_and(|bytes| bytes.len() > limits.input)
    {
        return Err(refusal(Failure::InputTooLarge, capture));
    }
    if CLEANUP_UNRESOLVED.load(Ordering::Acquire) {
        return Err(refusal(Failure::PriorCleanupUnresolved, capture));
    }
    // Current scope is Windows. Other platforms refuse before runtime or spawn.
    #[cfg(not(windows))]
    {
        let _ = (command, input);
        return Err(refusal(Failure::UnsupportedPlatform, capture));
    }
    #[cfg(windows)]
    {
        if tokio::runtime::Handle::try_current().is_ok() {
            return Err(refusal(Failure::NestedRuntime, capture));
        }
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_time()
            .build()
            .map_err(|error| {
                let mut capture = capture;
                capture.primary_io = Some(io_cause("runtime_build", &error));
                refusal(Failure::RuntimeBuild, capture)
            })?;
        // Timer-only runtime: no Tokio process/pipe, spawn_blocking, fs or spawned task.
        runtime.block_on(capture_windows(command, input, limits))
    }
}

#[cfg(windows)]
mod win_pipe {
    use std::ffi::c_void;
    use std::io::Read;
    use std::io::{self};
    use std::os::windows::io::AsRawHandle;

    // Signatures match locally reviewed windows-rs0.62.2 System/Pipes bindings.
    // System/Pipes is not an enabled crate feature: no dependency/feature edit.
    // Handles are borrowed from std-owned Child pipes. No CloseHandle or owner clone.
    #[link(name = "kernel32")]
    unsafe extern "system" {
        fn PeekNamedPipe(
            pipe: *mut c_void,
            buffer: *mut c_void,
            buffer_size: u32,
            bytes_read: *mut u32,
            available: *mut u32,
            remaining: *mut u32,
        ) -> i32;
        fn GetNamedPipeInfo(
            pipe: *mut c_void,
            flags: *mut u32,
            out_capacity: *mut u32,
            in_capacity: *mut u32,
            instances: *mut u32,
        ) -> i32;
    }
    const PIPE_TYPE_MESSAGE: u32 = 4;
    const ERROR_BROKEN_PIPE: i32 = 109;

    pub(super) enum Ready {
        Bytes(usize),
        Pending,
        Eof,
    }
    fn info(pipe: &impl AsRawHandle) -> io::Result<(u32, u32, u32)> {
        let (mut flags, mut out_capacity, mut in_capacity) = (0, 0, 0);
        // SAFETY: borrowed live handle and three writable u32 outputs outlive this call.
        let ok = unsafe {
            GetNamedPipeInfo(
                pipe.as_raw_handle(),
                &mut flags,
                &mut out_capacity,
                &mut in_capacity,
                std::ptr::null_mut(),
            )
        };
        if ok == 0 {
            return Err(io::Error::last_os_error());
        }
        if flags & PIPE_TYPE_MESSAGE != 0 {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "fixed-wave pipe must be byte mode",
            ));
        }
        Ok((flags, out_capacity, in_capacity))
    }
    pub(super) fn validate_read(pipe: &impl AsRawHandle) -> io::Result<()> {
        info(pipe).map(|_| ())
    }
    pub(super) fn ready(pipe: &impl AsRawHandle) -> io::Result<Ready> {
        let mut available = 0;
        // SAFETY: no buffer/read/remaining outputs requested; live borrowed pipe and u32 output.
        let ok = unsafe {
            PeekNamedPipe(
                pipe.as_raw_handle(),
                std::ptr::null_mut(),
                0,
                std::ptr::null_mut(),
                &mut available,
                std::ptr::null_mut(),
            )
        };
        if ok == 0 {
            let error = io::Error::last_os_error();
            if error.raw_os_error() == Some(ERROR_BROKEN_PIPE) {
                return Ok(Ready::Eof);
            }
            return Err(error);
        }
        Ok(if available == 0 {
            Ready::Pending
        } else {
            Ready::Bytes(available as usize)
        })
    }
    pub(super) fn read_ready(
        pipe: &mut (impl Read + AsRawHandle),
        output: &mut [u8],
    ) -> io::Result<Option<usize>> {
        match ready(pipe)? {
            Ready::Pending => Ok(None),
            Ready::Eof => Ok(Some(0)),
            Ready::Bytes(available) => {
                // Sole reader, no duplicate handle/read task. Peer only adds bytes or closes.
                // Never ask synchronous std Read for more than the observed available bytes.
                let count = available.min(output.len());
                pipe.read(&mut output[..count]).map(Some)
            }
        }
    }
}

#[cfg(windows)]
fn pump(
    pipe: &mut impl ReadPipe,
    capture: &mut Capture,
    stream: Stream,
    limit: usize,
) -> std::result::Result<(), Failure> {
    let mut bytes = [0; CHUNK_BYTES];
    for _ in 0..TURNS_PER_STREAM {
        let count = win_pipe::read_ready(pipe, &mut bytes).map_err(|error| {
            capture.primary_io = Some(io_cause("pipe_read", &error));
            Failure::StreamRead(stream)
        })?;
        match count {
            None => return Ok(()),
            Some(0) => {
                match stream {
                    Stream::Stdout => capture.stdout_eof = true,
                    Stream::Stderr => capture.stderr_eof = true,
                }
                return Ok(());
            }
            Some(count) => append_chunk(capture, stream, &bytes[..count], limit)?,
        }
    }
    Ok(())
}
#[cfg(windows)]
trait ReadPipe: Read + std::os::windows::io::AsRawHandle {}
#[cfg(windows)]
impl<T: Read + std::os::windows::io::AsRawHandle> ReadPipe for T {}

#[cfg(windows)]
async fn cleanup(child: &mut Child, capture: &mut Capture, timeout: Duration) {
    capture.phase = Some(Phase::Cleanup);
    if capture.reaped {
        return;
    }
    match child.try_wait() {
        Ok(Some(status)) => {
            capture.status = Some(status);
            capture.reaped = true;
            return;
        }
        Ok(None) => {}
        Err(error) => capture.reap_io = Some(io_cause("pre_kill_poll", &error)),
    }
    capture.kill_requested = true;
    if let Err(error) = child.kill() {
        capture.kill_io = Some(io_cause("owned_root_kill", &error));
    }
    let deadline = Instant::now() + timeout;
    loop {
        match child.try_wait() {
            Ok(Some(status)) => {
                capture.status = Some(status);
                capture.reaped = true;
                return;
            }
            Ok(None) => {}
            Err(error) => {
                capture.reap_io = Some(io_cause("owned_root_reap", &error));
                return;
            }
        }
        if Instant::now() >= deadline {
            capture.cleanup_deadline = true;
            return;
        }
        tokio::time::sleep_until((Instant::now() + POLL).min(deadline)).await;
    }
}

#[cfg(windows)]
fn prepare_input_file(input: &[u8], capture: &mut Capture) -> io::Result<std::fs::File> {
    if input.len() > WAVE_INPUT_BYTES || capture.input_written != 0 {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "invalid fixed request staging",
        ));
    }
    // Existing locked tempfile returns a std-owned delete-on-close file. This is
    // pre-spawn synchronous IO, not covered by the child work timer. OS causes
    // propagate unchanged; a disk-space error requires the orchestrator to stop.
    let mut file = tempfile::tempfile()?;
    while capture.input_written < input.len() {
        let count = file.write(&input[capture.input_written..])?;
        if count == 0 {
            return Err(io::Error::new(
                io::ErrorKind::WriteZero,
                "fixed request staging made no progress",
            ));
        }
        capture.input_written = capture
            .input_written
            .checked_add(count)
            .ok_or_else(|| io::Error::other("fixed input counter overflow"))?;
    }
    file.flush()?;
    file.rewind()?;
    capture.input_from_file = true;
    capture.input_complete = true;
    // Exact staged bytes and EOF; no remaining parent writer after Command drops.
    // The inherited file is not an adversarially immutable/read-only capability.
    Ok(file)
}

#[cfg(windows)]
async fn capture_windows(
    mut command: Command,
    input: Option<Vec<u8>>,
    limits: Limits,
) -> std::result::Result<Capture, CaptureError> {
    let mut capture = Capture {
        phase: Some(Phase::Preflight),
        input_complete: input.as_ref().is_none_or(Vec::is_empty),
        ..Capture::default()
    };
    let stdin = match input.as_deref() {
        Some(bytes) => match prepare_input_file(bytes, &mut capture) {
            Ok(file) => Stdio::from(file),
            Err(error) => {
                capture.primary_io = Some(io_cause("fixed_request_prepare", &error));
                capture.failure_phase = capture.phase;
                return Err(refusal(Failure::InputPrepare, capture));
            }
        },
        None => Stdio::null(),
    };
    drop(input);
    command
        .stdin(stdin)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    let mut child = match command.spawn() {
        Ok(child) => child,
        Err(error) => {
            capture.primary_io = Some(io_cause("spawn", &error));
            return Err(refusal(Failure::Spawn, capture));
        }
    };
    drop(command); // Release the retained parent-side request-file owner after spawn.
    capture.started = true;
    capture.phase = Some(Phase::Work);
    let mut deadline = Instant::now() + limits.work;
    let (mut stdout, mut stderr) = (child.stdout.take(), child.stderr.take());
    let primary = 'work: loop {
        let (Some(out), Some(err)) = (stdout.as_mut(), stderr.as_mut()) else {
            break Failure::MissingPipe;
        };
        if let Err(error) = win_pipe::validate_read(out).and_then(|()| win_pipe::validate_read(err))
        {
            capture.primary_io = Some(io_cause("validate_pipe", &error));
            break Failure::InvalidPipe;
        }
        loop {
            if Instant::now() >= deadline {
                break 'work if capture.phase == Some(Phase::Drain) {
                    Failure::DrainDeadline
                } else {
                    Failure::WorkDeadline
                };
            }
            if !capture.stdout_eof {
                if let Err(reason) = pump(out, &mut capture, Stream::Stdout, limits.stdout) {
                    break 'work reason;
                }
            }
            if !capture.stderr_eof {
                if let Err(reason) = pump(err, &mut capture, Stream::Stderr, limits.stderr) {
                    break 'work reason;
                }
            }
            if !capture.reaped {
                match child.try_wait() {
                    Ok(Some(status)) => {
                        capture.status = Some(status);
                        capture.reaped = true;
                        if !capture.input_complete {
                            break 'work Failure::InputIncomplete;
                        }
                        capture.phase = Some(Phase::Drain);
                        deadline = Instant::now() + limits.drain;
                    }
                    Ok(None) => {}
                    Err(error) => {
                        capture.primary_io = Some(io_cause("work_poll", &error));
                        break 'work Failure::Wait;
                    }
                }
            }
            if capture.reaped && capture.stdout_eof && capture.stderr_eof {
                drop(stdout.take());
                drop(stderr.take());
                capture.local_pipes_closed = true;
                if capture.status.is_some_and(|status| status.success()) {
                    return Ok(capture);
                }
                break 'work Failure::NonzeroExit;
            }
            tokio::time::sleep_until((Instant::now() + POLL).min(deadline)).await;
        }
    };
    // No outstanding async pipe future/thread: pipes are owned local std values.
    drop(stdout.take());
    drop(stderr.take());
    capture.local_pipes_closed = true;
    capture.failure_phase = capture.phase;
    cleanup(&mut child, &mut capture, limits.reap).await;
    if !capture.reaped {
        CLEANUP_UNRESOLVED.store(true, Ordering::Release);
    }
    Err(refusal(primary, capture))
}

struct RequestSink {
    bytes: Vec<u8>,
}
impl Write for RequestSink {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        let next = self
            .bytes
            .len()
            .checked_add(bytes.len())
            .ok_or_else(|| io::Error::other("request counter overflow"))?;
        if next > WAVE_INPUT_BYTES {
            return Err(io::Error::other("request sink exceeds fixed-wave bound"));
        }
        self.bytes
            .try_reserve_exact(bytes.len())
            .map_err(|_| io::Error::other("request sink allocation refused"))?;
        self.bytes.extend_from_slice(bytes);
        Ok(bytes.len())
    }
    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

pub(in crate::source_projection) fn read_wave_git_blobs(
    repository: &Path,
    oids: &BTreeSet<String>,
) -> Result<BTreeMap<String, Vec<u8>>> {
    let fixed: BTreeSet<_> = WAVE_OIDS.iter().map(|oid| (*oid).to_owned()).collect();
    ensure!(
        *oids == fixed,
        "wave reader accepts only the exact15 frozen OIDs"
    );
    let mut request = RequestSink { bytes: Vec::new() };
    for oid in oids {
        writeln!(&mut request, "{oid}")?;
    }
    let expected_request = request.bytes.clone(); // Two individually bounded615-byte copies; no tempfile.
    let mut command = frozen_git_command(repository);
    command
        .env("GIT_ALLOW_PROTOCOL", "")
        .env("GIT_TERMINAL_PROMPT", "0")
        .args([
            "-c",
            "protocol.allow=never",
            "-c",
            "core.fsmonitor=false",
            "cat-file",
            "--batch",
        ]);
    let mut capture = capture_owned(
        command,
        Some(request.bytes),
        Limits::wave(WAVE_RAW_BYTES, WAVE_INPUT_BYTES),
    )
    .map_err(eyre::Report::new)?;
    capture.phase = Some(Phase::Parse);
    // Reuse the actual existing private parser without any source/body alteration.
    // Raw<=16MiB+3855 and parsed<=existing16MiB may coexist; not a whole-process16MiB claim.
    let mut cursor = Cursor::new(capture.stdout.as_slice());
    let mut parser_request = RequestSink { bytes: Vec::new() };
    match read_blob_batch(&mut parser_request, &mut cursor, oids) {
        Ok(blobs) => {
            if cursor.position() != capture.stdout.len() as u64
                || parser_request.bytes != expected_request
            {
                capture.failure_phase = Some(Phase::Parse);
                return Err(eyre::Report::new(refusal(Failure::Parse, capture)));
            }
            Ok(blobs)
        }
        Err(error) => {
            capture.failure_phase = Some(Phase::Parse);
            capture.primary_io = Some(IoCause {
                operation: "framed_blob_parse",
                kind: io::ErrorKind::InvalidData,
                raw_os_error: None,
                message: diagnostic_prefix(&error),
            });
            Err(eyre::Report::new(refusal(Failure::Parse, capture)))
        }
    }
}
pub(in crate::source_projection) fn read_wave_git_tree(
    repository: &Path,
    commit: &str,
    paths: &[&str],
) -> Result<Vec<u8>> {
    ensure!(
        WAVE_COMMITS.contains(&commit) && paths == WAVE_PATHS,
        "wave tree reader accepts only fixed20 commits and ordered13 paths"
    );
    let mut command = frozen_git_command(repository);
    command.args([
        "-c",
        "protocol.allow=never",
        "-c",
        "core.fsmonitor=false",
        "ls-tree",
        "-r",
        commit,
        "--",
    ]);
    for path in paths {
        command.arg(format!("platform/minecraft/{path}"));
    }
    capture_owned(command, None, Limits::wave(TREE_BYTES, 0))
        .map(|capture| capture.stdout)
        .map_err(eyre::Report::new)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[cfg(windows)]
    #[test]
    fn exact_request_file_has_bytes_and_eof_before_spawn() {
        let mut capture = Capture::default();
        let file = prepare_input_file(&[b'i'; 615], &mut capture).unwrap();
        let mut bytes = Vec::new();
        file.take(616).read_to_end(&mut bytes).unwrap();
        assert_eq!(bytes, vec![b'i'; 615]);
        assert!(capture.input_from_file && capture.input_complete);
        assert_eq!(capture.input_written, 615);
        assert!(!capture.started && !capture.kill_requested);
    }
    #[test]
    fn retained_cap_is_checked_before_extension() {
        let mut capture = Capture::default();
        append_chunk(&mut capture, Stream::Stdout, b"abcd", 4).unwrap();
        assert!(matches!(
            append_chunk(&mut capture, Stream::Stdout, b"x", 4),
            Err(Failure::Overflow(Stream::Stdout))
        ));
        assert_eq!(capture.stdout, b"abcd");
        assert_eq!(capture.stdout_observed, 5);
    }
    #[test]
    fn stderr_has_its_own_pre_retention_cap() {
        let mut capture = Capture::default();
        assert!(matches!(
            append_chunk(&mut capture, Stream::Stderr, b"five!", 4),
            Err(Failure::Overflow(Stream::Stderr))
        ));
        assert!(capture.stderr.is_empty());
        assert_eq!(capture.stderr_observed, 5);
    }
    #[test]
    fn counter_overflow_does_not_extend_output() {
        let mut capture = Capture {
            stdout_observed: usize::MAX,
            ..Capture::default()
        };
        assert!(matches!(
            append_chunk(&mut capture, Stream::Stdout, b"x", 4),
            Err(Failure::CounterOverflow(Stream::Stdout))
        ));
        assert!(capture.stdout.is_empty());
    }
    #[test]
    fn invalid_policy_refuses_before_any_spawn() {
        let mut limits = Limits::wave(TREE_BYTES, 0);
        limits.stdout = 0;
        let failure = capture_owned(Command::new("must-not-be-spawned"), None, limits).unwrap_err();
        assert!(matches!(failure.reason, Failure::InvalidLimits));
        assert!(!failure.capture.started);
        assert!(!failure.capture.kill_requested);
    }
    #[test]
    fn oversized_input_refuses_before_any_spawn() {
        let failure = capture_owned(
            Command::new("must-not-be-spawned"),
            Some(vec![0; 2]),
            Limits::wave(TREE_BYTES, 1),
        )
        .unwrap_err();
        assert!(matches!(failure.reason, Failure::InputTooLarge));
        assert!(!failure.capture.started);
    }
    #[test]
    fn unknown_oid_or_tree_contract_refuses_before_git() {
        assert!(read_wave_git_blobs(Path::new("."), &BTreeSet::from(["0".repeat(40)])).is_err());
        assert!(
            read_wave_git_tree(Path::new("."), WAVE_COMMITS[0], &["src/Unknown.java"]).is_err()
        );
        assert!(read_wave_git_tree(Path::new("."), "HEAD", &WAVE_PATHS).is_err());
    }
    #[test]
    fn fixed_wave_request_is_exact_and_budgeted() {
        let oids: BTreeSet<_> = WAVE_OIDS.iter().copied().collect();
        let mut sink = RequestSink { bytes: Vec::new() };
        for oid in oids {
            writeln!(&mut sink, "{oid}").unwrap();
        }
        assert_eq!(sink.bytes.len(), 615);
        assert!(sink.write(b"x").is_err());
        assert_eq!(sink.bytes.len(), 615);
        assert_eq!(WAVE_RAW_BYTES as u64, MAX_BLOB_TOTAL_BYTES + 15 * 257);
    }
    #[test]
    fn diagnostic_text_is_bounded_on_a_utf8_boundary() {
        let input = format!("{}é", "a".repeat(2047));
        let prefix = diagnostic_prefix(&input);
        assert_eq!(prefix.len(), 2047);
        assert!(prefix.bytes().all(|byte| byte == b'a'));
    }
}

#[cfg(all(test, windows))]
#[path = "core_wave_process_mechanics_tests.rs"]
mod process_mechanics_tests;
