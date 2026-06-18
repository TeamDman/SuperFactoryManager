use super::CancellationState;
use super::CancellationToken;
use super::CtrlCAction;
use std::time::Duration;
use std::time::Instant;

#[test]
fn first_ctrl_c_requests_graceful_shutdown() {
    let mut state = CancellationState::new();

    assert_eq!(
        state.record_ctrl_c(Instant::now()),
        CtrlCAction::RequestGracefulShutdown
    );
    assert!(state.is_cancelled());
}

#[test]
fn second_ctrl_c_within_one_second_forces_exit() {
    let mut state = CancellationState::new();
    let now = Instant::now();

    assert_eq!(
        state.record_ctrl_c(now),
        CtrlCAction::RequestGracefulShutdown
    );
    assert_eq!(
        state.record_ctrl_c(now + Duration::from_millis(500)),
        CtrlCAction::ForceExit
    );
}

#[test]
fn later_ctrl_c_starts_a_new_graceful_window() {
    let mut state = CancellationState::new();
    let now = Instant::now();

    assert_eq!(
        state.record_ctrl_c(now),
        CtrlCAction::RequestGracefulShutdown
    );
    assert_eq!(
        state.record_ctrl_c(now + Duration::from_secs(2)),
        CtrlCAction::RequestGracefulShutdown
    );
}

#[test]
fn cancellation_token_clones_share_cancellation() {
    let token = CancellationToken::new();
    let clone = token.clone();

    clone.request_cancel("profile stop");

    assert!(token.is_cancelled());
    assert_eq!(token.cancellation_reason().as_deref(), Some("profile stop"));
}

#[test]
fn cancellation_token_keeps_first_reason() {
    let token = CancellationToken::new();

    token.request_cancel("first");
    token.request_cancel("second");

    assert_eq!(token.cancellation_reason().as_deref(), Some("first"));
    assert!(
        token
            .bail_if_cancelled()
            .is_err_and(|error| error.to_string() == "first")
    );
}
