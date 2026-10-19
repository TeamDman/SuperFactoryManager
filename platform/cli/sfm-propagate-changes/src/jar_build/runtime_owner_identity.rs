use eyre::WrapErr;
use facet::Facet;
use std::path::PathBuf;

/// An observed process identity; a PID alone cannot establish runtime ownership.
#[derive(Debug, Clone, PartialEq, Eq, Facet)]
#[facet(deny_unknown_fields)]
pub(crate) struct RuntimeOwnerIdentity {
    pub(crate) pid: u32,
    pub(crate) creation_identity: String,
    #[facet(proxy = super::json_path::JsonPath)]
    pub(crate) executable_path: PathBuf,
}

impl RuntimeOwnerIdentity {
    /// Returns `None` only when the process is absent or has exited.
    /// Inspection failures remain errors rather than proving a stale owner.
    pub(crate) fn capture(pid: u32) -> eyre::Result<Option<Self>> {
        if pid == 0 {
            return Ok(None);
        }
        capture_platform(pid)
    }

    pub(crate) fn current() -> eyre::Result<Self> {
        Self::capture(std::process::id())
            .wrap_err("cannot inspect current runtime owner")?
            .ok_or_else(|| eyre::eyre!("current runtime owner disappeared during identity capture"))
    }
}

#[cfg(windows)]
fn capture_platform(pid: u32) -> eyre::Result<Option<RuntimeOwnerIdentity>> {
    use windows::Win32::Foundation::ERROR_INVALID_PARAMETER;
    use windows::Win32::System::Threading::OpenProcess;
    use windows::Win32::System::Threading::PROCESS_QUERY_LIMITED_INFORMATION;
    use windows::core::Owned;

    // SAFETY: only read-only process query access is requested, without handle inheritance.
    let handle = match unsafe { OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid) } {
        Ok(handle) => handle,
        Err(error) if error.code() == ERROR_INVALID_PARAMETER.to_hresult() => return Ok(None),
        Err(error) => {
            return Err(error).wrap_err_with(|| format!("cannot inspect runtime owner PID {pid}"));
        }
    };
    // SAFETY: OpenProcess returned a newly owned handle; the guard closes it exactly once.
    let handle = unsafe { Owned::new(handle) };
    let (created, exited) = process_times(*handle, pid)?;
    if exited != 0 {
        return Ok(None);
    }
    // Read the exit state again even if querying the image fails during process exit.
    let executable_path = process_executable(*handle, pid);
    let (confirmed_created, exited) = process_times(*handle, pid)?;
    if exited != 0 {
        return Ok(None);
    }
    eyre::ensure!(
        created != 0 && created == confirmed_created,
        "runtime owner PID {pid} creation identity changed during capture"
    );
    Ok(Some(RuntimeOwnerIdentity {
        pid,
        creation_identity: format!("windows:{created}"),
        executable_path: executable_path?,
    }))
}

#[cfg(windows)]
fn process_times(handle: windows::Win32::Foundation::HANDLE, pid: u32) -> eyre::Result<(u64, u64)> {
    use windows::Win32::Foundation::FILETIME;
    use windows::Win32::System::Threading::GetProcessTimes;

    let mut created = FILETIME::default();
    let mut exited = FILETIME::default();
    let mut kernel = FILETIME::default();
    let mut user = FILETIME::default();
    // SAFETY: the caller retains the query handle; all four FILETIME outputs are writable.
    unsafe {
        GetProcessTimes(
            handle,
            &raw mut created,
            &raw mut exited,
            &raw mut kernel,
            &raw mut user,
        )
    }
    .wrap_err_with(|| format!("cannot read creation/exit time for runtime owner PID {pid}"))?;
    let ticks =
        |time: FILETIME| (u64::from(time.dwHighDateTime) << 32) | u64::from(time.dwLowDateTime);
    Ok((ticks(created), ticks(exited)))
}

#[cfg(windows)]
fn process_executable(
    handle: windows::Win32::Foundation::HANDLE,
    pid: u32,
) -> eyre::Result<PathBuf> {
    use std::ffi::OsString;
    use std::os::windows::ffi::OsStringExt;
    use windows::Win32::System::Threading::PROCESS_NAME_WIN32;
    use windows::Win32::System::Threading::QueryFullProcessImageNameW;
    use windows::core::PWSTR;

    let mut buffer = vec![0_u16; 32_768];
    let mut length = u32::try_from(buffer.len())?;
    // SAFETY: the query handle is live, and length describes the writable UTF-16 buffer.
    unsafe {
        QueryFullProcessImageNameW(
            handle,
            PROCESS_NAME_WIN32,
            PWSTR(buffer.as_mut_ptr()),
            &raw mut length,
        )
    }
    .wrap_err_with(|| format!("cannot read executable path for runtime owner PID {pid}"))?;
    let length = usize::try_from(length)?;
    eyre::ensure!(
        length > 0 && length <= buffer.len(),
        "runtime owner PID {pid} returned an invalid executable path length"
    );
    let path = PathBuf::from(OsString::from_wide(&buffer[..length]));
    eyre::ensure!(
        path.is_absolute(),
        "runtime owner PID {pid} executable path is not absolute"
    );
    Ok(path)
}

#[cfg(target_os = "linux")]
fn capture_platform(pid: u32) -> eyre::Result<Option<RuntimeOwnerIdentity>> {
    let root = PathBuf::from(format!("/proc/{pid}"));
    let stat_path = root.join("stat");
    let Some(before) = read_bounded_proc(&stat_path, 16_384)? else {
        return Ok(None);
    };
    let Some(created) = linux_start_time(pid, &before)? else {
        return Ok(None);
    };
    let boot = linux_boot_identity()?;
    let executable_path = match std::fs::read_link(root.join("exe")) {
        Ok(path) => Some(path),
        Err(error) if missing_proc(&error) => None,
        Err(error) => {
            return Err(error).wrap_err_with(|| {
                format!("cannot read executable path for runtime owner PID {pid}")
            });
        }
    };
    let Some(after) = read_bounded_proc(&stat_path, 16_384)? else {
        return Ok(None);
    };
    let Some(confirmed_created) = linux_start_time(pid, &after)? else {
        return Ok(None);
    };
    eyre::ensure!(
        created == confirmed_created,
        "runtime owner PID {pid} was reused during identity capture"
    );
    let executable_path = executable_path.ok_or_else(|| {
        eyre::eyre!("runtime owner PID {pid} is alive but its executable cannot be verified")
    })?;
    eyre::ensure!(
        executable_path.is_absolute(),
        "runtime owner PID {pid} executable path is not absolute"
    );
    Ok(Some(RuntimeOwnerIdentity {
        pid,
        creation_identity: format!("linux:{boot}:{created}"),
        executable_path,
    }))
}

#[cfg(target_os = "linux")]
fn read_bounded_proc(path: &std::path::Path, limit: u64) -> eyre::Result<Option<Vec<u8>>> {
    use std::io::Read;

    let file = match std::fs::File::open(path) {
        Ok(file) => file,
        Err(error) if missing_proc(&error) => return Ok(None),
        Err(error) => {
            return Err(error).wrap_err_with(|| format!("cannot inspect {}", path.display()));
        }
    };
    let mut bytes = Vec::new();
    match file.take(limit + 1).read_to_end(&mut bytes) {
        Ok(_) => (),
        Err(error) if missing_proc(&error) => return Ok(None),
        Err(error) => {
            return Err(error).wrap_err_with(|| format!("cannot read {}", path.display()));
        }
    }
    eyre::ensure!(
        u64::try_from(bytes.len())? <= limit,
        "{} exceeds process identity inspection bound",
        path.display()
    );
    Ok(Some(bytes))
}

#[cfg(any(target_os = "linux", test))]
fn missing_proc(error: &std::io::Error) -> bool {
    // Linux ESRCH is errno 3; procfs can return it if a process exits after open.
    error.kind() == std::io::ErrorKind::NotFound || error.raw_os_error() == Some(3)
}

#[cfg(target_os = "linux")]
fn linux_boot_identity() -> eyre::Result<String> {
    let path = std::path::Path::new("/proc/sys/kernel/random/boot_id");
    let bytes = read_bounded_proc(path, 64)?.ok_or_else(|| {
        eyre::eyre!(
            "cannot verify Linux process boot identity: {} is missing",
            path.display()
        )
    })?;
    let boot = std::str::from_utf8(&bytes)
        .wrap_err("Linux process boot identity is not UTF-8")?
        .trim();
    eyre::ensure!(
        boot.len() == 36
            && boot.bytes().enumerate().all(|(index, byte)| {
                if matches!(index, 8 | 13 | 18 | 23) {
                    byte == b'-'
                } else {
                    byte.is_ascii_hexdigit()
                }
            }),
        "Linux process boot identity has an unexpected format"
    );
    Ok(boot.to_owned())
}

#[cfg(any(target_os = "linux", test))]
fn linux_start_time(pid: u32, stat: &[u8]) -> eyre::Result<Option<String>> {
    eyre::ensure!(
        stat.starts_with(format!("{pid} (").as_bytes()),
        "process stat does not identify expected PID {pid}"
    );
    // The comm field can contain spaces, parentheses, or non-UTF-8 bytes.
    let closing = stat
        .iter()
        .rposition(|&byte| byte == b')')
        .ok_or_else(|| eyre::eyre!("runtime owner PID {pid} stat has no closing comm delimiter"))?;
    let tail = std::str::from_utf8(&stat[closing + 1..])
        .wrap_err_with(|| format!("runtime owner PID {pid} stat fields are not UTF-8"))?;
    let mut fields = tail.split_ascii_whitespace();
    let state = fields
        .next()
        .ok_or_else(|| eyre::eyre!("runtime owner PID {pid} stat has no process state"))?;
    eyre::ensure!(
        state.len() == 1 && state.as_bytes()[0].is_ascii_alphabetic(),
        "runtime owner PID {pid} stat has invalid process state"
    );
    if matches!(state, "Z" | "X" | "x") {
        return Ok(None);
    }
    // After consuming field 3 (state), skip fields 4 through 21 to reach field 22.
    let started = fields
        .nth(18)
        .ok_or_else(|| eyre::eyre!("runtime owner PID {pid} stat has no start time"))?;
    eyre::ensure!(
        started.bytes().all(|byte| byte.is_ascii_digit()),
        "runtime owner PID {pid} stat start time is not decimal"
    );
    let ticks = started
        .parse::<u64>()
        .wrap_err_with(|| format!("runtime owner PID {pid} stat start time is invalid"))?;
    Ok(Some(ticks.to_string()))
}

#[cfg(not(any(windows, target_os = "linux")))]
fn capture_platform(pid: u32) -> eyre::Result<Option<RuntimeOwnerIdentity>> {
    eyre::bail!(
        "cannot verify runtime owner PID {pid}: process creation identity is unsupported on this platform"
    )
}

#[cfg(test)]
mod tests {
    use super::RuntimeOwnerIdentity;
    use super::linux_start_time;
    use super::missing_proc;
    use std::path::PathBuf;

    #[test]
    fn persisted_identity_distinguishes_pid_reuse_and_executable_changes() {
        let owner = RuntimeOwnerIdentity {
            pid: 42,
            creation_identity: "linux:boot-a:1234".to_owned(),
            executable_path: PathBuf::from("/runtime/java"),
        };
        let encoded = facet_json::to_string(&owner).unwrap();
        let decoded: RuntimeOwnerIdentity = facet_json::from_str(&encoded).unwrap();
        assert_eq!(owner, decoded);
        for replacement in [
            RuntimeOwnerIdentity {
                creation_identity: "linux:boot-a:1235".to_owned(),
                ..owner.clone()
            },
            RuntimeOwnerIdentity {
                creation_identity: "linux:boot-b:1234".to_owned(),
                ..owner.clone()
            },
            RuntimeOwnerIdentity {
                executable_path: PathBuf::from("/other/java"),
                ..owner.clone()
            },
            RuntimeOwnerIdentity {
                pid: 43,
                ..owner.clone()
            },
        ] {
            assert_ne!(owner, replacement);
        }
    }

    fn stat_fixture(comm: &[u8], state: &str, started: &str) -> Vec<u8> {
        let mut stat = b"42 (".to_vec();
        stat.extend_from_slice(comm);
        stat.extend_from_slice(
            format!(") {state} {} {started} 0 0\n", ["0"; 18].join(" ")).as_bytes(),
        );
        stat
    }

    #[test]
    fn linux_stat_handles_opaque_comm_and_reads_field_22() {
        let stat = stat_fixture(b"worker ) ( \xff", "S", "987654321");
        assert_eq!(
            linux_start_time(42, &stat).unwrap().as_deref(),
            Some("987654321")
        );
        assert_eq!(
            linux_start_time(42, &stat_fixture(b"worker", "Z", "987654321")).unwrap(),
            None
        );
        assert!(linux_start_time(41, &stat).is_err());
        assert!(linux_start_time(42, b"42 (worker) S 0").is_err());
        assert!(linux_start_time(42, &stat_fixture(b"worker", "S", "invalid")).is_err());
    }

    #[test]
    fn vanished_process_errors_do_not_treat_permission_failure_as_absence() {
        assert!(missing_proc(&std::io::Error::from_raw_os_error(3)));
        assert!(missing_proc(&std::io::Error::from(
            std::io::ErrorKind::NotFound
        )));
        assert!(!missing_proc(&std::io::Error::from(
            std::io::ErrorKind::PermissionDenied
        )));
    }
}
