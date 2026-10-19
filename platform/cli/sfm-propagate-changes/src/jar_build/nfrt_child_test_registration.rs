// Included only by nfrt_child_process's outer cfg(all(test, windows)).
#[path = "nfrt_active_child_fixture.rs"]
pub(super) mod active_fixture;

#[path = "nfrt_child_process_tests.rs"]
mod active_child_tests;
