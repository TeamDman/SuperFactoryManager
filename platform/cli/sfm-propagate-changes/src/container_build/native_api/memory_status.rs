#[cfg(windows)]
#[repr(C)]
pub(super) struct MemoryStatus {
    length: u32,
    load: u32,
    total: u64,
    available: u64,
    total_page: u64,
    available_page: u64,
    total_virtual: u64,
    available_virtual: u64,
    extended: u64,
}
#[cfg(windows)]
#[link(name = "kernel32")]
unsafe extern "system" {
    fn GlobalMemoryStatusEx(status: *mut MemoryStatus) -> i32;
}
#[cfg(windows)]
pub(super) fn available() -> eyre::Result<u64> {
    let mut status = MemoryStatus {
        length: u32::try_from(std::mem::size_of::<MemoryStatus>())?,
        load: 0,
        total: 0,
        available: 0,
        total_page: 0,
        available_page: 0,
        total_virtual: 0,
        available_virtual: 0,
        extended: 0,
    };
    // SAFETY: correctly sized initialized writable Win32 MEMORYSTATUSEX layout.
    let succeeded = unsafe { GlobalMemoryStatusEx(&raw mut status) } != 0;
    eyre::ensure!(succeeded, "Windows memory-capacity query failed");
    Ok(status.available)
}
