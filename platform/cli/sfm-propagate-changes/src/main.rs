// Only this executable opts into allocation profiling. Libraries and
// integration-test binaries must not inherit a process-global diagnostic.
#[cfg(feature = "tracy_memory")]
#[global_allocator]
static TRACY_ALLOCATOR: tracy_client::ProfiledAllocator<std::alloc::System> =
    tracy_client::ProfiledAllocator::new(std::alloc::System, 100);

#[cfg(not(feature = "tracy_memory"))]
#[global_allocator]
static GLOBAL_ALLOCATOR: mimalloc::MiMalloc = mimalloc::MiMalloc;

fn main() -> eyre::Result<std::process::ExitCode> {
    sfm_propagate_changes::main()
}
