use super::finish_host;
use super::read_frame;
use super::wait_success;
use crate::cancellation::CancellationToken;
use crate::jar_build::nfrt_child_process::active_fixture::ActiveChildFixture;
use crate::jar_build::nfrt_child_process::active_fixture::PIPE_MARKER;
use crate::jar_build::nfrt_child_process::active_fixture::assert_live_handle;
use eyre::Result;
use eyre::ensure;
use std::io::BufRead as _;
use std::io::BufReader;
use std::io::Read as _;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::time::Duration;
use std::time::Instant;

#[test]
fn active_host_cancellation_reaps_before_partial_pipe_reader_joins() -> Result<()> {
    let mut fixture = ActiveChildFixture::spawn()?;
    let stdout = fixture
        .child
        .0
        .stdout
        .take()
        .ok_or_else(|| eyre::eyre!("missing child stdout"))?;
    let mut reader = BufReader::new(stdout);
    read_ready_marker(&mut reader)?;
    let writer = fixture
        .child
        .0
        .stdin
        .take()
        .ok_or_else(|| eyre::eyre!("missing child stdin"))?;
    let handle = fixture.handle.try_clone()?;
    let cancellation = CancellationToken::new();
    let cancel = cancellation.clone();
    let reader_finished = Arc::new(AtomicBool::new(false));
    let finished = Arc::clone(&reader_finished);
    let started = Instant::now();
    let result = std::thread::scope(|scope| -> Result<()> {
        let (sender, receiver) = std::sync::mpsc::sync_channel(1);
        let reader_thread = scope.spawn(move || {
            let frame = read_frame(&mut reader);
            finished.store(frame.is_err(), Ordering::SeqCst);
            let _ = sender.send(frame);
        });
        let canceller = scope.spawn(move || -> Result<()> {
            std::thread::sleep(Duration::from_millis(150));
            let observed = assert_live_handle(&handle);
            cancel.request_cancel("active NFRT host cancellation fixture");
            observed
        });
        let result = wait_success(
            &mut fixture.child,
            Duration::from_secs(10),
            "private active host",
            &cancellation,
        );
        // Keep all assertions inside this result so every ordinary error still
        // reaches the production kill/wait/join sequence below.
        let observed = (|| -> Result<()> {
            canceller
                .join()
                .map_err(|panic| eyre::eyre!("canceller panicked: {panic:?}"))??;
            ensure!(
                result.as_ref().is_err_and(
                    |error| error.to_string() == "active NFRT host cancellation fixture"
                ),
                "unexpected host cancellation result"
            );
            fixture.assert_live()?;
            ensure!(
                !reader_finished.load(Ordering::SeqCst),
                "partial frame reader finished while child retained stdout"
            );
            Ok(())
        })();
        // This ordering is exactly the production caller's precondition.
        drop(writer);
        drop(receiver);
        finish_host(
            &mut fixture.child,
            observed.and(result),
            &cancellation,
            reader_thread,
        )
    });
    ensure!(
        result.unwrap_err().to_string() == "active NFRT host cancellation fixture",
        "cleanup masked cancellation"
    );
    ensure!(
        reader_finished.load(Ordering::SeqCst),
        "pipe reader was not joined"
    );
    fixture.assert_reaped()?;
    ensure!(
        started.elapsed() < Duration::from_secs(15),
        "host cleanup waited for fixture expiry"
    );
    Ok(())
}

#[test]
fn successful_host_exit_joins_its_pipe_reader() -> Result<()> {
    let mut fixture = ActiveChildFixture::spawn()?;
    let stdout = fixture
        .child
        .0
        .stdout
        .take()
        .ok_or_else(|| eyre::eyre!("missing child stdout"))?;
    let mut reader = BufReader::new(stdout);
    read_ready_marker(&mut reader)?;
    let writer = fixture
        .child
        .0
        .stdin
        .take()
        .ok_or_else(|| eyre::eyre!("missing child stdin"))?;
    let finished = Arc::new(AtomicBool::new(false));
    let reader_finished = Arc::clone(&finished);
    std::thread::scope(|scope| -> Result<()> {
        let (sender, receiver) = std::sync::mpsc::sync_channel(1);
        let reader_thread = scope.spawn(move || {
            let frame = read_frame(&mut reader);
            // A successful child may let the Rust test harness terminate the
            // partial line normally; completion is independent of frame status.
            reader_finished.store(true, Ordering::SeqCst);
            let _ = sender.send(frame);
        });
        let result = fixture.release();
        drop(writer);
        drop(receiver);
        finish_host(
            &mut fixture.child,
            result,
            &CancellationToken::new(),
            reader_thread,
        )
    })?;
    ensure!(
        finished.load(Ordering::SeqCst),
        "successful host reader was not joined"
    );
    fixture.assert_reaped()
}

fn read_ready_marker(reader: &mut impl std::io::BufRead) -> Result<()> {
    // Test-harness startup lines precede the fixture's explicit marker. The
    // retained partial frame immediately follows it and cannot complete early.
    let mut prefix = reader.take(64 * 1024);
    loop {
        let mut line = String::new();
        ensure!(
            prefix.read_line(&mut line)? > 0,
            "fixture pipe closed before marker"
        );
        if line.ends_with(PIPE_MARKER) {
            return Ok(());
        }
    }
}
