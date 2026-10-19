//! Private Rust test child; no SDK, Java, shell, or descendant process is started.

use super::OwnedChild;
use eyre::Result;
use eyre::ensure;
use std::fs;
use std::io::Write as _;
use std::os::windows::io::AsHandle as _;
use std::os::windows::io::AsRawHandle as _;
use std::os::windows::io::OwnedHandle;
use std::os::windows::process::CommandExt as _;
use std::process::Command;
use std::process::Stdio;
use std::time::Duration;
use std::time::Instant;
use windows::Win32::Foundation::FILETIME;
use windows::Win32::Foundation::HANDLE;
use windows::Win32::Foundation::WAIT_OBJECT_0;
use windows::Win32::Foundation::WAIT_TIMEOUT;
use windows::Win32::System::Threading::GetProcessId;
use windows::Win32::System::Threading::GetProcessTimes;
use windows::Win32::System::Threading::WaitForSingleObject;

const CHILD_ENV: &str = "SFM_PRIVATE_NFRT_ACTIVE_CHILD_FIXTURE";
pub(crate) const PIPE_MARKER: &str = "SFM_NFRT_ACTIVE_PIPE_READY\n";

pub(crate) struct ActiveChildFixture {
    pub(crate) child: OwnedChild,
    pub(crate) handle: OwnedHandle,
    signals: tempfile::TempDir,
    creation: u64,
}

impl ActiveChildFixture {
    pub(crate) fn spawn() -> Result<Self> {
        let signals = tempfile::tempdir()?;
        let name = concat!(module_path!(), "::private_active_child_fixture");
        let selector = name.split_once("::").map_or(name, |(_, name)| name);
        let mut command = Command::new(std::env::current_exe()?);
        command
            .args(["--exact", selector, "--nocapture"])
            .env(CHILD_ENV, signals.path())
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::from(fs::File::create(
                signals.path().join("stderr"),
            )?))
            .creation_flags(0x0800_0000);
        let mut child = OwnedChild(command.spawn()?);
        // Duplicate the exact Child process object; never reopen a recycled PID.
        let handle = child.0.as_handle().try_clone_to_owned()?;
        let (pid, creation, _) = process_identity(&handle)?;
        ensure!(
            pid == child.0.id() && creation > 0,
            "fixture process identity changed"
        );
        let started = Instant::now();
        let ready = signals.path().join("ready");
        while !ready.try_exists()? {
            ensure!(
                child.0.try_wait()?.is_none(),
                "fixture exited before readiness"
            );
            ensure!(
                started.elapsed() < Duration::from_secs(10),
                "fixture readiness timed out"
            );
            std::thread::sleep(Duration::from_millis(10));
        }
        ensure!(
            fs::read_to_string(ready)? == pid.to_string(),
            "fixture readiness PID mismatch"
        );
        let fixture = Self {
            child,
            handle,
            signals,
            creation,
        };
        fixture.assert_live()?;
        Ok(fixture)
    }

    pub(crate) fn assert_live(&self) -> Result<()> {
        let (pid, creation, _) = process_identity(&self.handle)?;
        ensure!(
            pid == self.child.0.id() && creation == self.creation,
            "fixture creation identity changed"
        );
        // GetProcessTimes exit FILETIME is undefined for a still-active process.
        // The zero-time wait on this retained object is the liveness authority.
        assert_live_handle(&self.handle)
    }

    pub(crate) fn release(&self) -> Result<()> {
        fs::write(
            self.signals.path().join("release"),
            self.child.0.id().to_string(),
        )?;
        Ok(())
    }

    pub(crate) fn assert_reaped(self) -> Result<()> {
        let Self {
            child,
            handle,
            signals,
            creation,
        } = self;
        let pid = child.0.id();
        // This is the actual production RAII guard, including kill + wait on error.
        drop(child);
        // SAFETY: handle owns the same exact process object throughout this wait.
        let status = unsafe { WaitForSingleObject(HANDLE(handle.as_raw_handle()), 10_000) };
        ensure!(
            status == WAIT_OBJECT_0,
            "owned child did not signal exit after cleanup: {status:?}"
        );
        let (actual_pid, actual_creation, exited) = process_identity(&handle)?;
        ensure!(
            actual_pid == pid && actual_creation == creation && exited >= creation,
            "exact child exit/creation identity was not retained"
        );
        ensure!(
            !signals.path().join("expired").try_exists()?,
            "fixture self-expiry must not satisfy cancellation or cleanup"
        );
        eprintln!("NFRT_ACTIVE_CHILD_REAPED pid={pid} creation={creation} exited={exited}");
        Ok(())
    }
}

pub(crate) fn assert_live_handle(handle: &OwnedHandle) -> Result<()> {
    // SAFETY: the borrowed owned process handle is retained through this query.
    let status = unsafe { WaitForSingleObject(HANDLE(handle.as_raw_handle()), 0) };
    ensure!(
        status == WAIT_TIMEOUT,
        "private child was not active at cancellation: {status:?}"
    );
    Ok(())
}

fn process_identity(handle: &OwnedHandle) -> Result<(u32, u64, u64)> {
    let raw = HANDLE(handle.as_raw_handle());
    let mut creation = FILETIME::default();
    let mut exited = FILETIME::default();
    let mut kernel = FILETIME::default();
    let mut user = FILETIME::default();
    // SAFETY: handle owns the process object and every FILETIME output is writable.
    unsafe {
        GetProcessTimes(
            raw,
            &raw mut creation,
            &raw mut exited,
            &raw mut kernel,
            &raw mut user,
        )?
    };
    // SAFETY: the same owned process handle is retained throughout this query.
    let pid = unsafe { GetProcessId(raw) };
    let ticks =
        |value: FILETIME| (u64::from(value.dwHighDateTime) << 32) | u64::from(value.dwLowDateTime);
    Ok((pid, ticks(creation), ticks(exited)))
}

#[test]
fn private_active_child_fixture() -> Result<()> {
    let Some(directory) = std::env::var_os(CHILD_ENV) else {
        return Ok(());
    };
    let signals = std::path::Path::new(&directory);
    let pid = std::process::id().to_string();
    print!("{PIPE_MARKER}unterminated-host-frame");
    std::io::stdout().flush()?;
    fs::write(signals.join("ready.tmp"), &pid)?;
    fs::rename(signals.join("ready.tmp"), signals.join("ready"))?;
    let started = Instant::now();
    loop {
        if signals.join("release").try_exists()? {
            ensure!(
                fs::read_to_string(signals.join("release"))? == pid,
                "fixture release PID mismatch"
            );
            return Ok(());
        }
        if started.elapsed() >= Duration::from_secs(45) {
            fs::write(signals.join("expired"), &pid)?;
            eyre::bail!("private active child exceeded its independent self-deadline");
        }
        std::thread::sleep(Duration::from_millis(10));
    }
}
