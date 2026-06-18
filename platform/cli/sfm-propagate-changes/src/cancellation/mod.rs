mod cancellation_state;
mod cancellation_token;

use cancellation_state::CancellationState;
use cancellation_state::CtrlCAction;
pub use cancellation_token::CancellationToken;
use color_eyre::owo_colors::OwoColorize;
use std::sync::Arc;
use std::sync::Mutex;
use std::time::Instant;

/// Create the process cancellation token and install the process-wide Ctrl+C handler.
///
/// # Errors
///
/// Returns an error if the platform handler cannot be registered.
pub fn install_ctrlc_handler() -> eyre::Result<CancellationToken> {
    let cancellation_token = CancellationToken::new();
    let handler_token = cancellation_token.clone();
    let handler_state = Arc::new(Mutex::new(CancellationState::new()));
    let ctrlc_state = Arc::clone(&handler_state);
    ctrlc::set_handler(move || handle_ctrl_c(&handler_token, &ctrlc_state))
        .map_err(|error| eyre::eyre!(error))?;
    Ok(cancellation_token)
}

fn handle_ctrl_c(cancellation_token: &CancellationToken, state: &Mutex<CancellationState>) {
    eprintln!("{}", "^C".red());
    let action = {
        let mut state = state
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        state.record_ctrl_c(Instant::now())
    };
    cancellation_token.request_cancel("Operation cancelled by Ctrl+C");
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
