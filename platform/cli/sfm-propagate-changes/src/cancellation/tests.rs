use super::CancellationState;
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
