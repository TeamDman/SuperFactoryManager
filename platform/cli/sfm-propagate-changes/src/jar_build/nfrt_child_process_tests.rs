use super::active_fixture::ActiveChildFixture;
use super::active_fixture::assert_live_handle;
use super::wait_for_child;
use crate::cancellation::CancellationToken;
use eyre::Result;
use eyre::ensure;
use std::time::Duration;
use std::time::Instant;

#[test]
fn active_tool_child_cancellation_reaps_exact_process_before_return() -> Result<()> {
    let mut fixture = ActiveChildFixture::spawn()?;
    let handle = fixture.handle.try_clone()?;
    let parent = CancellationToken::new();
    let cancellation = parent.child_token();
    let started = Instant::now();
    let error = std::thread::scope(|scope| -> Result<eyre::Report> {
        let canceller = scope.spawn(move || -> Result<()> {
            std::thread::sleep(Duration::from_millis(150));
            let observed = assert_live_handle(&handle);
            parent.request_cancel("active NFRT tool child cancellation fixture");
            observed
        });
        let result = wait_for_child(&mut fixture.child, "private-active-tool", &cancellation);
        canceller
            .join()
            .map_err(|panic| eyre::eyre!("canceller panicked: {panic:?}"))??;
        Ok(result.unwrap_err())
    })?;
    ensure!(
        error.to_string() == "active NFRT tool child cancellation fixture",
        "unexpected cancellation result: {error:#}"
    );
    // The wait returns the token error while the owned process is still alive.
    // Production execute_checked_child then unwinds this same RAII guard.
    fixture.assert_live()?;
    fixture.assert_reaped()?;
    ensure!(
        started.elapsed() < Duration::from_secs(15),
        "cleanup waited for the child self-deadline"
    );
    Ok(())
}

#[test]
fn active_tool_child_success_is_reaped_without_cancellation() -> Result<()> {
    let mut fixture = ActiveChildFixture::spawn()?;
    fixture.release()?;
    let status = wait_for_child(
        &mut fixture.child,
        "private-successful-tool",
        &CancellationToken::new(),
    )?;
    ensure!(status.success(), "private child failed: {status}");
    fixture.assert_reaped()
}
