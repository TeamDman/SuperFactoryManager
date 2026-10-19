//! Owned, bounded child lifecycle for a checked NFRT tool request.
//! Caller supplies a command prepared by its registered graph and retained SDK.

use super::nfrt_child_tool::NfrtChildCommand;
use super::nfrt_preparation_phase::NfrtPreparationPhase;
use crate::source_projection::nfrt_tool_sdk::NfrtToolSdk;
use eyre::Result;
use eyre::ensure;
use std::fs::OpenOptions;
use std::io::Write;
use std::process::Child;
use std::process::ExitStatus;
use std::process::Stdio;
use std::time::Duration;
use std::time::Instant;

pub(crate) struct OwnedChild(pub(crate) Child);
impl Drop for OwnedChild {
    fn drop(&mut self) {
        if !matches!(self.0.try_wait(), Ok(Some(_))) {
            let _ = self.0.kill();
            let _ = self.0.wait();
        }
    }
}

pub(crate) fn execute_child(
    planned: NfrtChildCommand,
    sdk: &mut NfrtToolSdk,
    cancellation: &crate::cancellation::CancellationToken,
) -> Result<String> {
    let node_label = NfrtPreparationPhase::safe_node(&planned.node_id);
    let _span = tracing::info_span!("nfrt_child", node = %node_label).entered();
    NfrtPreparationPhase::run("nfrt.child.execute", || {
        execute_checked_child(planned, sdk, cancellation)
    })
}

fn execute_checked_child(
    mut planned: NfrtChildCommand,
    sdk: &mut NfrtToolSdk,
    cancellation: &crate::cancellation::CancellationToken,
) -> Result<String> {
    #[derive(facet::Facet)]
    struct Reply<'a> {
        schema: &'a str,
        node_id: &'a str,
        exit_code: i32,
    }
    cancellation.bail_if_cancelled()?;
    ensure!(
        planned.command.get_program() == sdk.executable(),
        "Child command changed the retained SDK"
    );
    NfrtPreparationPhase::run("nfrt.child.sdk_precheck", || sdk.recheck())?;
    let workspace = planned
        .command
        .get_current_dir()
        .ok_or_else(|| eyre::eyre!("Child has no declared workspace"))?;
    // create_new refuses prior log adoption. Parent directory remains leased by
    // the invocation store throughout this function and its child lifetime.
    let mut log = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(workspace.join("console_output.txt"))?;
    writeln!(
        log,
        "SDK identity: {}\nCommand: {:?}",
        sdk.identity(),
        planned.command
    )?;
    planned
        .command
        .stdin(Stdio::null())
        .stdout(Stdio::from(log.try_clone()?))
        .stderr(Stdio::from(log));
    let mut lifetime = NfrtPreparationPhase::start("nfrt.child.lifetime");
    let mut child = OwnedChild(NfrtPreparationPhase::run("nfrt.child.spawn", || {
        Ok(planned.command.spawn()?)
    })?);
    tracing::info!(
        phase = "nfrt.child.spawn",
        event = "child_observed",
        child_pid = child.0.id(),
        rust_pid = std::process::id(),
        measurement = "wall_duration",
        nested = true,
        "NFRT child process observed"
    );
    let status = wait_for_child(&mut child, &planned.node_id, cancellation)?;
    tracing::info!(
        phase = "nfrt.child.lifetime",
        event = "child_exit",
        child_pid = child.0.id(),
        rust_pid = std::process::id(),
        exit_code = ?status.code(),
        exit_success = status.success(),
        measurement = "wall_duration",
        nested = true,
        "NFRT child process exited"
    );
    NfrtPreparationPhase::run("nfrt.child.sdk_postcheck", || sdk.recheck())?;
    let result = NfrtPreparationPhase::run("nfrt.child.postvalidation_reply", || {
        ensure!(
            status.success(),
            "NFRT child {} failed: {status}; inspect its console_output.txt",
            planned.node_id
        );
        Ok(facet_json::to_string(&Reply {
            schema: "sfm:nfrt_child_tool_result@1",
            node_id: &planned.node_id,
            exit_code: 0,
        })?)
    });
    if result.is_ok() {
        lifetime.success();
    }
    result
}

fn wait_for_child(
    child: &mut OwnedChild,
    node_id: &str,
    cancellation: &crate::cancellation::CancellationToken,
) -> Result<ExitStatus> {
    eprintln!("NFRT child {} started pid={}", node_id, child.0.id());
    let started = Instant::now();
    let mut next_progress = Duration::from_secs(10);
    loop {
        cancellation.bail_if_cancelled()?;
        if let Some(status) = child.0.try_wait()? {
            return Ok(status);
        }
        let elapsed = started.elapsed();
        ensure!(
            elapsed < Duration::from_hours(1),
            "NFRT child exceeded its one-hour process bound"
        );
        if elapsed >= next_progress {
            eprintln!("NFRT child {} running: {}s", node_id, elapsed.as_secs());
            next_progress += Duration::from_secs(10);
        }
        std::thread::sleep(Duration::from_millis(100));
    }
}

// The outer cfg excludes every registration token from production builds.
#[cfg(all(test, windows))]
include!("nfrt_child_test_registration.rs");
