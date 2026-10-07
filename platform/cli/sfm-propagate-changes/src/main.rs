// Keep the existing library-owned Tracy memory diagnostic allocator distinct.
// Normal application builds choose their allocator here, not in the library.
#[cfg(not(feature = "tracy_memory"))]
#[global_allocator]
static GLOBAL_ALLOCATOR: mimalloc::MiMalloc = mimalloc::MiMalloc;

fn main() -> eyre::Result<std::process::ExitCode> {
    sfm_propagate_changes::main()
}
