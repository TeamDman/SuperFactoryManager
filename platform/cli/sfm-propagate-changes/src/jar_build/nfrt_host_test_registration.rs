// Included only by nfrt_owned_host's outer cfg(test).
#[path = "nfrt_owned_host_runtime_tests.rs"]
mod runtime_source_tests;

#[cfg(windows)]
#[path = "nfrt_owned_host_tests.rs"]
mod active_child_tests;
