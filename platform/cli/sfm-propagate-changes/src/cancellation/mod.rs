mod cancellation_state;

pub use cancellation_state::*;
use color_eyre::owo_colors::OwoColorize;
use std::sync::LazyLock;
use std::sync::Mutex;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::time::Instant;

static INSTALLED: AtomicBool = AtomicBool::new(false);
static CANCELLED: AtomicBool = AtomicBool::new(false);
static STATE: LazyLock<Mutex<CancellationState>> =
    LazyLock::new(|| Mutex::new(CancellationState::new()));

/// Install the process-wide Ctrl+C handler.
///
/// # Errors
///
/// Returns an error if the platform handler cannot be registered.
pub fn install_ctrlc_handler() -> eyre::Result<()> {
    if INSTALLED.swap(true, Ordering::AcqRel) {
        return Ok(());
    }

    ctrlc::set_handler(handle_ctrl_c).map_err(|error| eyre::eyre!(error))
}

#[must_use]
pub fn is_cancelled() -> bool {
    CANCELLED.load(Ordering::Acquire)
}

/// Return an error if cancellation has been requested.
///
/// # Errors
///
/// Returns an error after the first Ctrl+C.
pub fn bail_if_cancelled() -> eyre::Result<()> {
    if is_cancelled() {
        eyre::bail!("Operation cancelled by Ctrl+C");
    }
    Ok(())
}

fn handle_ctrl_c() {
    eprintln!("{}", "^C".red());
    let action = {
        let mut state = STATE
            .lock()
            .expect("cancellation state should not be poisoned");
        state.record_ctrl_c(Instant::now())
    };
    CANCELLED.store(true, Ordering::Release);
    match action {
        CtrlCAction::RequestGracefulShutdown => {
            tracing::warn!("{} received; graceful shutdown requested", "Ctrl+C".red());
        }
        CtrlCAction::ForceExit => {
            tracing::warn!("{}", "Second Ctrl+C received; forcing exit".red());
            std::process::exit(130);
        }
    }
}

#[cfg(test)]
mod tests;
