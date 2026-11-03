//! Opt-in trace handshake immediately before an owned client JVM is spawned.
//!
//! Use a new, existing absolute directory for each `run client --smoke
//! --diagnostic-launch-gate <directory>` invocation. After normal preparation,
//! the CLI writes `sfm-ready.json` with a random token and five-minute expiry.
//! A trace controller arms capture, writes that token to `sfm-armed.txt`, waits
//! for `sfm-arm-ack.json`, then writes it to `sfm-release.txt`. The CLI records
//! `sfm-released.json` and `sfm-spawned.json` with UTC timestamps. Missing,
//! premature or mismatched control files, cancellation, or expiry prevent spawn;
//! `sfm-aborted.json` records the reason. The runtime session and its reader
//! holds remain in scope while the handshake waits.
use super::runtime_session_receipt::RuntimeSessionReceipt;
use crate::cancellation::CancellationToken;
use crate::source_projection::candidate_lock::{checked_directory, checked_file};
use chrono::{DateTime, Utc};
use eyre::{Context, ensure};
use facet::Facet;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

const WAIT_LIMIT: Duration = Duration::from_secs(300);
const POLL_INTERVAL: Duration = Duration::from_millis(25);
const RESERVED: [&str; 6] = [
    "sfm-ready.json",
    "sfm-armed.txt",
    "sfm-arm-ack.json",
    "sfm-release.txt",
    "sfm-released.json",
    "sfm-spawned.json",
];

#[derive(Facet)]
struct ReadyRecord {
    schema: &'static str,
    token: String,
    ready_utc: String,
    expires_utc: String,
    owner_pid: u32,
    owner_id: String,
    owner_lease: String,
    session_id: String,
    java_executable: String,
    argfile: String,
}

#[derive(Facet)]
struct EventRecord {
    schema: &'static str,
    token: String,
    event_utc: String,
    child_pid: Option<u32>,
    spawn_attempt_utc: Option<String>,
    reason: Option<String>,
}

pub(super) struct DiagnosticLaunchGate {
    root: PathBuf,
    token: String,
}

impl DiagnosticLaunchGate {
    pub(super) fn ready(
        root: &Path,
        session: &RuntimeSessionReceipt,
        java: &Path,
        argfile: &Path,
    ) -> eyre::Result<Self> {
        Self::ready_with_identity(
            root,
            &session.owner_id,
            &session.owner_lease,
            &session.session_id,
            java,
            argfile,
        )
    }

    fn ready_with_identity(
        root: &Path,
        owner_id: &str,
        owner_lease: &Path,
        session_id: &str,
        java: &Path,
        argfile: &Path,
    ) -> eyre::Result<Self> {
        let root = checked_directory(root)?;
        for name in RESERVED {
            ensure!(
                matches!(fs::symlink_metadata(root.join(name)), Err(error) if error.kind() == std::io::ErrorKind::NotFound),
                "diagnostic gate directory must be fresh; {name} already exists or cannot be inspected"
            );
        }
        let token = format!("{:032x}", rand::random::<u128>());
        let now = Utc::now();
        write_record(
            &root,
            "sfm-ready.json",
            &ReadyRecord {
                schema: "sfm-diagnostic-launch-gate-v1",
                token: token.clone(),
                ready_utc: timestamp(now),
                expires_utc: timestamp(now + chrono::Duration::seconds(300)),
                owner_pid: std::process::id(),
                owner_id: owner_id.to_owned(),
                owner_lease: owner_lease.display().to_string(),
                session_id: session_id.to_owned(),
                java_executable: java.display().to_string(),
                argfile: argfile.display().to_string(),
            },
        )?;
        Ok(Self { root, token })
    }

    pub(super) fn wait_for_release(&self, cancellation: &CancellationToken) -> eyre::Result<()> {
        self.wait_for_release_with_timeout(cancellation, WAIT_LIMIT)
    }

    fn wait_for_release_with_timeout(
        &self,
        cancellation: &CancellationToken,
        timeout: Duration,
    ) -> eyre::Result<()> {
        let result = self.wait_inner(cancellation, timeout);
        if let Err(error) = &result {
            let _ = self.record_event("sfm-aborted.json", None, None, Some(error.to_string()));
        }
        result
    }

    fn wait_inner(&self, cancellation: &CancellationToken, timeout: Duration) -> eyre::Result<()> {
        let started = Instant::now();
        loop {
            self.poll_preconditions(cancellation, started, timeout)?;
            ensure!(
                read_token(&self.root, "sfm-release.txt")?.is_none(),
                "diagnostic release arrived before arm acknowledgement"
            );
            if let Some(token) = read_token(&self.root, "sfm-armed.txt")? {
                ensure!(
                    token == self.token,
                    "diagnostic arm token does not match this run"
                );
                break;
            }
            std::thread::sleep(POLL_INTERVAL);
        }
        self.record_event("sfm-arm-ack.json", None, None, None)?;
        loop {
            self.poll_preconditions(cancellation, started, timeout)?;
            if let Some(token) = read_token(&self.root, "sfm-release.txt")? {
                ensure!(
                    token == self.token,
                    "diagnostic release token does not match this run"
                );
                self.record_event("sfm-released.json", None, None, None)?;
                return Ok(());
            }
            std::thread::sleep(POLL_INTERVAL);
        }
    }

    fn poll_preconditions(
        &self,
        cancellation: &CancellationToken,
        started: Instant,
        timeout: Duration,
    ) -> eyre::Result<()> {
        cancellation.bail_if_cancelled()?;
        ensure!(
            started.elapsed() < timeout,
            "diagnostic launch gate timed out without JVM spawn"
        );
        ensure!(
            checked_directory(&self.root)? == self.root,
            "diagnostic launch gate directory changed while waiting"
        );
        Ok(())
    }

    pub(super) fn record_spawn(
        &self,
        spawn_attempt_utc: DateTime<Utc>,
        child_pid: u32,
    ) -> eyre::Result<()> {
        self.record_event(
            "sfm-spawned.json",
            Some(child_pid),
            Some(spawn_attempt_utc),
            None,
        )
    }

    fn record_event(
        &self,
        name: &str,
        child_pid: Option<u32>,
        spawn_attempt_utc: Option<DateTime<Utc>>,
        reason: Option<String>,
    ) -> eyre::Result<()> {
        write_record(
            &self.root,
            name,
            &EventRecord {
                schema: "sfm-diagnostic-launch-gate-v1",
                token: self.token.clone(),
                event_utc: timestamp(Utc::now()),
                child_pid,
                spawn_attempt_utc: spawn_attempt_utc.map(timestamp),
                reason,
            },
        )
    }
}

fn timestamp(value: DateTime<Utc>) -> String {
    value.to_rfc3339_opts(chrono::SecondsFormat::Millis, true)
}

fn write_record<T: for<'a> Facet<'a>>(root: &Path, name: &str, record: &T) -> eyre::Result<()> {
    let mut file = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(root.join(name))
        .wrap_err_with(|| format!("cannot create diagnostic gate {name}"))?;
    file.write_all(facet_json::to_string_pretty(record)?.as_bytes())?;
    file.sync_all()?;
    Ok(())
}

fn read_token(root: &Path, name: &str) -> eyre::Result<Option<String>> {
    let path = root.join(name);
    match fs::symlink_metadata(&path) {
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Err(error) => Err(error).wrap_err_with(|| format!("cannot inspect diagnostic gate {name}")),
        Ok(metadata) => {
            ensure!(
                metadata.is_file() && metadata.len() <= 128,
                "invalid diagnostic gate {name}"
            );
            checked_file(root, name)?;
            let token = fs::read_to_string(path)?;
            Ok(Some(token.trim_end_matches(['\r', '\n']).to_owned()))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_gate(root: &Path) -> DiagnosticLaunchGate {
        DiagnosticLaunchGate::ready_with_identity(
            root,
            "fixture-owner",
            &root.join("owner-lease"),
            "fixture-session",
            &root.join("fake-java"),
            &root.join("args"),
        )
        .unwrap()
    }

    #[test]
    fn ready_arm_ack_release_then_fake_child() {
        let temp = tempfile::tempdir().unwrap();
        let gate = make_gate(temp.path());
        let ready = fs::read_to_string(temp.path().join("sfm-ready.json")).unwrap();
        assert!(ready.contains(&gate.token));
        assert!(ready.contains("fixture-session"));
        let root = temp.path().to_path_buf();
        let token = gate.token.clone();
        let controller = std::thread::spawn(move || {
            fs::write(root.join("sfm-armed.txt"), &token).unwrap();
            while !root.join("sfm-arm-ack.json").exists() {
                std::thread::yield_now();
            }
            fs::write(root.join("sfm-release.txt"), &token).unwrap();
        });
        gate.wait_for_release_with_timeout(&CancellationToken::new(), Duration::from_secs(2))
            .unwrap();
        controller.join().unwrap();
        #[cfg(windows)]
        {
            let mut child = std::process::Command::new("cmd")
                .args(["/C", "exit", "0"])
                .spawn()
                .unwrap();
            gate.record_spawn(Utc::now(), child.id()).unwrap();
            assert!(child.wait().unwrap().success());
            let spawned = fs::read_to_string(temp.path().join("sfm-spawned.json")).unwrap();
            assert!(spawned.contains(&child.id().to_string()));
        }
    }

    #[test]
    fn cancellation_and_timeout_never_release() {
        let temp = tempfile::tempdir().unwrap();
        let gate = make_gate(temp.path());
        let cancellation = CancellationToken::new();
        cancellation.request_cancel("test cancellation");
        assert!(
            gate.wait_for_release_with_timeout(&cancellation, Duration::from_secs(1))
                .is_err()
        );
        assert!(!temp.path().join("sfm-released.json").exists());
        assert!(temp.path().join("sfm-aborted.json").exists());

        let other = tempfile::tempdir().unwrap();
        let gate = make_gate(other.path());
        assert!(
            gate.wait_for_release_with_timeout(
                &CancellationToken::new(),
                Duration::from_millis(40)
            )
            .is_err()
        );
        assert!(!other.path().join("sfm-released.json").exists());
    }

    #[test]
    fn stale_or_early_release_and_reused_directory_are_rejected() {
        let temp = tempfile::tempdir().unwrap();
        let gate = make_gate(temp.path());
        assert!(
            DiagnosticLaunchGate::ready_with_identity(
                temp.path(),
                "other",
                temp.path(),
                "other",
                temp.path(),
                temp.path()
            )
            .is_err()
        );
        fs::write(temp.path().join("sfm-release.txt"), &gate.token).unwrap();
        assert!(
            gate.wait_for_release_with_timeout(&CancellationToken::new(), Duration::from_secs(1))
                .is_err()
        );

        let other = tempfile::tempdir().unwrap();
        let gate = make_gate(other.path());
        fs::write(other.path().join("sfm-armed.txt"), "wrong-token").unwrap();
        assert!(
            gate.wait_for_release_with_timeout(&CancellationToken::new(), Duration::from_secs(1))
                .is_err()
        );
        assert!(!other.path().join("sfm-arm-ack.json").exists());
    }

    #[test]
    fn concurrent_owners_cannot_claim_one_gate_directory() {
        let temp = tempfile::tempdir().unwrap();
        let barrier = std::sync::Arc::new(std::sync::Barrier::new(2));
        let root = temp.path().to_path_buf();
        let first = std::thread::spawn({
            let barrier = barrier.clone();
            let root = root.clone();
            move || {
                barrier.wait();
                DiagnosticLaunchGate::ready_with_identity(
                    &root, "first", &root, "first", &root, &root,
                )
                .is_ok()
            }
        });
        let second = std::thread::spawn(move || {
            barrier.wait();
            DiagnosticLaunchGate::ready_with_identity(
                &root, "second", &root, "second", &root, &root,
            )
            .is_ok()
        });
        assert_ne!(first.join().unwrap(), second.join().unwrap());
    }
}
