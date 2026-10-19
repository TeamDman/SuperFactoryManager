use std::path::Path;
/// Host-side admission; leaves space for archives, diagnostics and unrelated applications.
pub(super) fn check(path: &Path, minimum: u64) -> eyre::Result<()> {
    let existing = path
        .ancestors()
        .find(|p| p.exists())
        .ok_or_else(|| eyre::eyre!("disk path has no existing ancestor"))?;
    let available = free(existing)?;
    eyre::ensure!(
        available >= minimum,
        "disk capacity exhausted or below safety reserve: {} has {available} bytes, requires {minimum}; no cleanup was attempted",
        existing.display()
    );
    Ok(())
}
#[cfg(windows)]
fn free(path: &Path) -> eyre::Result<u64> {
    use std::os::windows::ffi::OsStrExt;
    #[link(name = "kernel32")]
    unsafe extern "system" {
        fn GetDiskFreeSpaceExW(
            path: *const u16,
            available: *mut u64,
            total: *mut u64,
            free: *mut u64,
        ) -> i32;
    }
    let path = path
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect::<Vec<_>>();
    let mut available = 0;
    // SAFETY: null-terminated UTF-16 path and valid writable u64; unused outputs null.
    let ok = unsafe {
        GetDiskFreeSpaceExW(
            path.as_ptr(),
            &raw mut available,
            std::ptr::null_mut(),
            std::ptr::null_mut(),
        )
    };
    eyre::ensure!(
        ok != 0,
        "disk capacity query failed: {}",
        std::io::Error::last_os_error()
    );
    Ok(available)
}
#[cfg(not(windows))]
fn free(path: &Path) -> eyre::Result<u64> {
    let output = std::process::Command::new("df")
        .arg("-Pk")
        .arg(path)
        .output()?;
    eyre::ensure!(output.status.success(), "disk capacity query failed");
    let text = String::from_utf8(output.stdout)?;
    let free = text
        .lines()
        .last()
        .and_then(|l| l.split_whitespace().nth(3))
        .ok_or_else(|| eyre::eyre!("disk capacity result missing"))?
        .parse::<u64>()?;
    free.checked_mul(1024)
        .ok_or_else(|| eyre::eyre!("disk capacity overflow"))
}
