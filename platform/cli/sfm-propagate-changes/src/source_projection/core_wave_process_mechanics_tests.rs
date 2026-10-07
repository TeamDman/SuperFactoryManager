//! Mechanical process checks only; execution requires the explicitly pinned fixture.
//! The real compiled Rust fixture must be separately admitted and checksum-pinned.
//! No Java bodies, game/provider simulations, OS fault injection or descendants.
use super::super::read_bounded;
use super::*;
use crate::source_projection::provenance::sha256;
use std::path::PathBuf;

fn fixture_path() -> Result<PathBuf> {
    let path = PathBuf::from(
        std::env::var_os("SFM_WAVE_MECHANICS_FIXTURE")
            .ok_or_else(|| eyre::eyre!("separately admitted mechanics fixture path is required"))?,
    );
    let expected = std::env::var("SFM_WAVE_MECHANICS_FIXTURE_SHA256")?;
    ensure!(
        path.is_absolute()
            && expected.len() == 71
            && expected.starts_with("sha256:")
            && expected[7..]
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "mechanics fixture requires absolute path and exact sha256 pin"
    );
    let metadata = std::fs::symlink_metadata(&path)?;
    ensure!(
        metadata.is_file() && !metadata.file_type().is_symlink(),
        "mechanics fixture must be a regular non-symlink file"
    );
    // read_bounded's16MiB maximum applies; bytes are dropped before any child is spawned.
    let actual = {
        let bytes = read_bounded(&path, 16 * 1024 * 1024)?;
        sha256(&bytes)
    };
    ensure!(
        actual == expected,
        "mechanics fixture bytes differ from separately admitted pin"
    );
    Ok(path)
}
fn request(path: &Path, mode: &str) -> Command {
    let mut command = Command::new(path);
    command.arg(mode);
    command
}
fn test_limits() -> Limits {
    Limits {
        stdout: 8192,
        stderr: 65536,
        input: 615,
        work: Duration::from_secs(2),
        drain: Duration::from_millis(250),
        reap: Duration::from_secs(2),
    }
}
#[test]
#[ignore = "Requires root-admitted compiled Rust fixture exact pin; no implicit process execution"]
fn fixed_wave_owned_root_process_mechanics() -> Result<()> {
    let fixture = fixture_path()?;
    let limits = test_limits();
    let exact = capture_owned(request(&fixture, "stdout_exact"), None, limits)?;
    assert!(
        exact.started
            && exact.reaped
            && exact.stdout_eof
            && exact.stderr_eof
            && exact.local_pipes_closed
    );
    assert_eq!(exact.stdout, vec![b'o'; 8192]);
    assert!(exact.stderr.is_empty());

    let overflow = capture_owned(request(&fixture, "stdout_overflow"), None, limits).unwrap_err();
    assert!(matches!(overflow.reason, Failure::Overflow(Stream::Stdout)));
    assert!(overflow.capture.stdout.len() <= 8192 && overflow.capture.stdout_observed > 8192);
    assert!(overflow.capture.reaped && overflow.capture.local_pipes_closed);

    let stderr = capture_owned(request(&fixture, "stderr_overflow"), None, limits).unwrap_err();
    assert!(matches!(stderr.reason, Failure::Overflow(Stream::Stderr)));
    assert!(stderr.capture.stderr.len() <= 65536 && stderr.capture.stderr_observed > 65536);
    assert!(stderr.capture.reaped && stderr.capture.local_pipes_closed);

    let nonzero = capture_owned(request(&fixture, "nonzero"), None, limits).unwrap_err();
    assert!(matches!(nonzero.reason, Failure::NonzeroExit));
    assert_eq!(
        nonzero.capture.status.and_then(|status| status.code()),
        Some(2)
    );
    assert!(nonzero.capture.reaped && nonzero.capture.stdout_eof && nonzero.capture.stderr_eof);

    let stall = capture_owned(request(&fixture, "stall"), None, limits).unwrap_err();
    assert!(matches!(stall.reason, Failure::WorkDeadline));
    assert!(
        stall.capture.kill_requested && stall.capture.reaped && stall.capture.local_pipes_closed
    );
    assert!(!stall.capture.cleanup_deadline);

    let dual = capture_owned(request(&fixture, "dual"), None, limits)?;
    assert_eq!(dual.stdout, vec![b'o'; 8192]);
    assert_eq!(dual.stderr, vec![b'e'; 65536]);
    assert!(dual.reaped && dual.stdout_eof && dual.stderr_eof);

    let input = capture_owned(
        request(&fixture, "stdin_eof"),
        Some(vec![b'i'; 615]),
        limits,
    )?;
    assert!(input.input_complete && input.input_written == 615 && input.input_from_file);
    assert_eq!(input.stdout, b"stdin=615\n");
    assert!(input.reaped && input.stdout_eof && input.stderr_eof);

    let absent =
        fixture.with_file_name("sfm-wave-mechanics-no-such-executable-70370ec4654234c2.exe");
    ensure!(
        !absent.exists(),
        "controlled spawn-refusal path unexpectedly exists"
    );
    let spawn = capture_owned(Command::new(absent), None, limits).unwrap_err();
    assert!(matches!(spawn.reason, Failure::Spawn));
    assert!(!spawn.capture.started);
    assert!(!spawn.capture.kill_requested && !spawn.capture.reaped);
    Ok(())
}
