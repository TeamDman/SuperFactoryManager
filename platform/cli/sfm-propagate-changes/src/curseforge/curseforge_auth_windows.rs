//! Native Windows credential storage and self-cleaning Task Scheduler registration.
//! No shell, encoded script, registry persistence or privileged service is involved.
use chrono::DateTime;
use chrono::Utc;
use eyre::Context;
use std::io::Write;
use std::os::windows::ffi::OsStrExt;
use std::os::windows::fs::MetadataExt;
use std::path::Path;
use windows::Win32::Foundation::HANDLE;
use windows::Win32::Foundation::HLOCAL;
use windows::Win32::Foundation::RPC_E_CHANGED_MODE;
use windows::Win32::Security::Authorization::ConvertSidToStringSidW;
use windows::Win32::Security::Authorization::ConvertStringSecurityDescriptorToSecurityDescriptorW;
use windows::Win32::Security::Cryptography::CRYPT_INTEGER_BLOB;
use windows::Win32::Security::Cryptography::CRYPTPROTECT_UI_FORBIDDEN;
use windows::Win32::Security::Cryptography::CryptProtectData;
use windows::Win32::Security::Cryptography::CryptUnprotectData;
use windows::Win32::Security::DACL_SECURITY_INFORMATION;
use windows::Win32::Security::GetTokenInformation;
use windows::Win32::Security::PROTECTED_DACL_SECURITY_INFORMATION;
use windows::Win32::Security::PSECURITY_DESCRIPTOR;
use windows::Win32::Security::SetFileSecurityW;
use windows::Win32::Security::TOKEN_QUERY;
use windows::Win32::Security::TOKEN_USER;
use windows::Win32::Security::TokenUser;
use windows::Win32::System::Com::CLSCTX_INPROC_SERVER;
use windows::Win32::System::Com::COINIT_MULTITHREADED;
use windows::Win32::System::Com::CoCreateInstance;
use windows::Win32::System::Com::CoInitializeEx;
use windows::Win32::System::Com::CoUninitialize;
use windows::Win32::System::TaskScheduler::ITaskFolder;
use windows::Win32::System::TaskScheduler::ITaskService;
use windows::Win32::System::TaskScheduler::TASK_CREATE;
use windows::Win32::System::TaskScheduler::TASK_LOGON_INTERACTIVE_TOKEN;
use windows::Win32::System::TaskScheduler::TaskScheduler;
use windows::Win32::System::Threading::GetCurrentProcess;
use windows::Win32::System::Threading::OpenProcessToken;
use windows::Win32::System::Variant::VARIANT;
use windows::core::BSTR;
use windows::core::Owned;
use windows::core::PCWSTR;
use windows::core::PWSTR;

fn reject_reparse(path: &Path) -> eyre::Result<()> {
    for ancestor in path.ancestors() {
        match std::fs::symlink_metadata(ancestor) {
            Ok(meta) => eyre::ensure!(
                meta.file_attributes() & 0x400 == 0,
                "Credential paths cannot contain reparse points"
            ),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => (),
            Err(error) => return Err(error.into()),
        }
    }
    Ok(())
}

fn user_sid() -> eyre::Result<String> {
    let mut raw = HANDLE::default();
    // SAFETY: this returns a borrowed pseudo-handle for the current process.
    let process = unsafe { GetCurrentProcess() };
    // SAFETY: pseudo process handle and writable token out-parameter are valid.
    unsafe { OpenProcessToken(process, TOKEN_QUERY, &raw mut raw) }?;
    // SAFETY: OpenProcessToken returned a newly owned kernel handle.
    let token = unsafe { Owned::new(raw) };
    let mut size = 0;
    // SAFETY: null buffer deliberately queries the required size.
    let _ = unsafe { GetTokenInformation(*token, TokenUser, None, 0, &raw mut size) };
    eyre::ensure!(size > 0, "Cannot determine token-user buffer size");
    let mut buffer = vec![0_usize; (size as usize).div_ceil(std::mem::size_of::<usize>())];
    // SAFETY: allocated aligned buffer has at least the returned required length.
    unsafe {
        GetTokenInformation(
            *token,
            TokenUser,
            Some(buffer.as_mut_ptr().cast()),
            size,
            &raw mut size,
        )
    }?;
    // SAFETY: successful TokenUser query initialized this aligned TOKEN_USER.
    let user = unsafe { &*buffer.as_ptr().cast::<TOKEN_USER>() };
    let mut text = PWSTR::null();
    // SAFETY: token buffer owns a valid SID; the output is initialized by Windows.
    unsafe { ConvertSidToStringSidW(user.User.Sid, &raw mut text) }?;
    // SAFETY: ConvertSidToStringSidW requires LocalFree; HLOCAL implements that ownership.
    let _allocation = unsafe { Owned::new(HLOCAL(text.0.cast())) };
    // SAFETY: successful conversion returned a null-terminated string owned above.
    Ok(unsafe { text.to_string()? })
}

pub(super) fn prepare(root: &Path) -> eyre::Result<()> {
    reject_reparse(root)?;
    std::fs::create_dir_all(root)?;
    let sddl = format!("D:P(A;OICI;FA;;;{})", user_sid()?);
    let sddl: Vec<u16> = sddl.encode_utf16().chain(Some(0)).collect();
    let path: Vec<u16> = root.as_os_str().encode_wide().chain(Some(0)).collect();
    let mut descriptor = PSECURITY_DESCRIPTOR::default();
    // SAFETY: SDDL is terminated and descriptor is a valid output pointer.
    unsafe {
        ConvertStringSecurityDescriptorToSecurityDescriptorW(
            PCWSTR(sddl.as_ptr()),
            1,
            &raw mut descriptor,
            None,
        )
    }?;
    // SAFETY: Windows allocated this descriptor with LocalAlloc.
    let _allocation = unsafe { Owned::new(HLOCAL(descriptor.0)) };
    // SAFETY: path is terminated and the owned descriptor remains alive for this call.
    unsafe {
        SetFileSecurityW(
            PCWSTR(path.as_ptr()),
            DACL_SECURITY_INFORMATION | PROTECTED_DACL_SECURITY_INFORMATION,
            descriptor,
        )
        .ok()
    }?;
    Ok(())
}

fn crypt(bytes: &[u8], encrypt: bool) -> eyre::Result<Vec<u8>> {
    eyre::ensure!(
        !bytes.is_empty() && bytes.len() <= 65536,
        "Invalid credential payload size"
    );
    let input = CRYPT_INTEGER_BLOB {
        cbData: u32::try_from(bytes.len())?,
        pbData: bytes.as_ptr().cast_mut(),
    };
    let mut output = CRYPT_INTEGER_BLOB::default();
    if encrypt {
        // SAFETY: DPAPI reads the bounded input and allocates the output. Machine scope is NOT enabled.
        unsafe {
            CryptProtectData(
                &raw const input,
                PCWSTR::null(),
                None,
                None,
                None,
                CRYPTPROTECT_UI_FORBIDDEN,
                &raw mut output,
            )?;
        }
    } else {
        // SAFETY: as above; no prompt or unmanaged description output is requested.
        unsafe {
            CryptUnprotectData(
                &raw const input,
                None,
                None,
                None,
                None,
                CRYPTPROTECT_UI_FORBIDDEN,
                &raw mut output,
            )?;
        }
    }
    // SAFETY: successful DPAPI calls return a LocalAlloc allocation.
    let _allocation = unsafe { Owned::new(HLOCAL(output.pbData.cast())) };
    // SAFETY: DPAPI returned this initialized span, kept alive by the allocation above.
    let result =
        unsafe { std::slice::from_raw_parts(output.pbData, output.cbData as usize) }.to_vec();
    if !encrypt {
        for index in 0..output.cbData as usize {
            // SAFETY: each index is within the initialized, exclusively owned DPAPI output.
            let byte = unsafe { output.pbData.add(index) };
            // SAFETY: the in-bounds pointer remains exclusively owned and writable.
            unsafe {
                byte.write_volatile(0);
            }
        }
    }
    Ok(result)
}

pub(super) fn read(root: &Path, name: &str) -> eyre::Result<Vec<u8>> {
    let path = checked_path(root, name)?;
    eyre::ensure!(
        std::fs::metadata(&path)?.len() <= 65536,
        "Oversized discovery lease"
    );
    crypt(&std::fs::read(path)?, false)
        .wrap_err("Cannot decrypt discovery lease for the current Windows user")
}

fn checked_path(root: &Path, name: &str) -> eyre::Result<std::path::PathBuf> {
    eyre::ensure!(
        super::curseforge_auth::valid_lease_name(name),
        "Invalid discovery lease identity"
    );
    let path = root.join(name);
    reject_reparse(&path)?;
    Ok(path)
}

fn task_name(name: &str) -> eyre::Result<String> {
    Ok(format!("SFM-CurseForge-Discovery-{}-{}", user_sid()?, name))
}

struct ComApartment(bool);
impl Drop for ComApartment {
    fn drop(&mut self) {
        if self.0 {
            // SAFETY: the guard is local to the thread whose successful CoInitializeEx it balances.
            unsafe {
                CoUninitialize();
            }
        }
    }
}

fn with_scheduler<T>(action: impl FnOnce(&ITaskFolder) -> eyre::Result<T>) -> eyre::Result<T> {
    // SAFETY: this scope balances successful initialization and never moves COM interfaces across threads.
    let initialized = unsafe { CoInitializeEx(None, COINIT_MULTITHREADED) };
    let _apartment = if initialized == RPC_E_CHANGED_MODE {
        ComApartment(false)
    } else {
        initialized.ok()?;
        ComApartment(true)
    };
    // SAFETY: the requested CLSID implements ITaskService; COM is initialized on this thread.
    let service: ITaskService =
        unsafe { CoCreateInstance(&TaskScheduler, None, CLSCTX_INPROC_SERVER)? };
    let empty = VARIANT::default();
    // SAFETY: empty connection parameters select the local scheduler/current identity.
    unsafe { service.Connect(&empty, &empty, &empty, &empty) }?;
    // SAFETY: service is connected; root is a valid task folder.
    let folder = unsafe { service.GetFolder(&BSTR::from("\\"))? };
    action(&folder)
}

fn xml_escape(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&apos;")
}

fn cleanup_xml(name: &str, expiry: DateTime<Utc>, executable: &Path) -> eyre::Result<String> {
    let sid = xml_escape(&user_sid()?);
    let executable = xml_escape(&executable.to_string_lossy());
    let end = expiry + chrono::Duration::days(30);
    let expiry = expiry.to_rfc3339_opts(chrono::SecondsFormat::Secs, true);
    let end = end.to_rfc3339_opts(chrono::SecondsFormat::Secs, true);
    Ok(format!(
        r#"<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo><Description>Remove one expired SFM CurseForge discovery lease, then unregister this task. No API key is stored in this task.</Description></RegistrationInfo>
  <Triggers><TimeTrigger><StartBoundary>{expiry}</StartBoundary><EndBoundary>{end}</EndBoundary><Enabled>true</Enabled></TimeTrigger></Triggers>
  <Principals><Principal id="Author"><UserId>{sid}</UserId><LogonType>InteractiveToken</LogonType><RunLevel>LeastPrivilege</RunLevel></Principal></Principals>
  <Settings><MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy><DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries><StopIfGoingOnBatteries>false</StopIfGoingOnBatteries><StartWhenAvailable>true</StartWhenAvailable><Enabled>true</Enabled><Hidden>true</Hidden><ExecutionTimeLimit>PT2M</ExecutionTimeLimit><DeleteExpiredTaskAfter>P30D</DeleteExpiredTaskAfter><RestartOnFailure><Interval>PT1M</Interval><Count>3</Count></RestartOnFailure></Settings>
  <Actions Context="Author"><Exec><Command>{executable}</Command><Arguments>curseforge auth cleanup --lease {name}</Arguments></Exec></Actions>
</Task>"#
    ))
}

pub(super) fn write(
    root: &Path,
    name: &str,
    expiry: DateTime<Utc>,
    payload: &[u8],
    executable: &Path,
) -> eyre::Result<()> {
    prepare(root)?;
    let path = checked_path(root, name)?;
    eyre::ensure!(
        !path.exists(),
        "Refusing to replace an existing credential lease"
    );
    eyre::ensure!(expiry > Utc::now(), "Lease expired before storage");
    let sealed = crypt(payload, true)?;
    let xml = cleanup_xml(name, expiry, executable)?;
    let task = task_name(name)?;
    // Register before writing a usable credential, so a crash never strands an unscheduled key.
    with_scheduler(|folder| {
        let empty = VARIANT::default();
        // SAFETY: local scheduler, current-user XML, no password or elevated identity supplied.
        unsafe {
            folder.RegisterTask(
                &BSTR::from(&task),
                &BSTR::from(xml),
                TASK_CREATE.0,
                &empty,
                &empty,
                TASK_LOGON_INTERACTIVE_TOKEN,
                &empty,
            )
        }?;
        Ok(())
    })?;
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(path)?;
    file.write_all(&sealed)?;
    file.sync_all()?;
    Ok(())
}

pub(super) fn remove(root: &Path, name: &str) -> eyre::Result<()> {
    let path = checked_path(root, name)?;
    match std::fs::remove_file(path) {
        Ok(()) => (),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => (),
        Err(error) => return Err(error.into()),
    }
    let name = task_name(name)?;
    with_scheduler(|folder| {
        // SAFETY: this exact generated task identity belongs to the current user and lease.
        match unsafe { folder.DeleteTask(&BSTR::from(name), 0) } {
            Ok(()) => Ok(()),
            Err(error)
                if error.code()
                    == windows::Win32::Foundation::ERROR_FILE_NOT_FOUND.to_hresult() =>
            {
                Ok(())
            }
            Err(error) => Err(error.into()),
        }
    })
}

#[cfg(test)]
pub(super) fn inspect(name: &str) -> eyre::Result<String> {
    let name = task_name(name)?;
    with_scheduler(|folder| {
        // SAFETY: connected folder and valid owned BSTR task name.
        let task = unsafe { folder.GetTask(&BSTR::from(name))? };
        // SAFETY: IRegisteredTask is live and connected.
        Ok(unsafe { task.Xml()? }.to_string())
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn dpapi_roundtrip_is_encrypted_and_rejects_corruption() {
        let clear = b"dummy-credential-not-real";
        let mut sealed = crypt(clear, true).unwrap();
        assert!(!sealed.windows(clear.len()).any(|bytes| bytes == clear));
        assert_eq!(crypt(&sealed, false).unwrap(), clear);
        let end = sealed.len() - 1;
        sealed[end] ^= 1;
        assert!(crypt(&sealed, false).is_err());
    }
    #[test]
    fn task_xml_has_no_shell_and_retains_cleanup_guards() {
        let xml = cleanup_xml(
            "lease-00000000000000000000000000000000.bin",
            Utc::now(),
            Path::new("C:\\example & test\\sfm.exe"),
        )
        .unwrap();
        assert!(xml.contains("example &amp; test"));
        for guard in [
            "<StartWhenAvailable>true",
            "<DeleteExpiredTaskAfter>P30D",
            "<EndBoundary>",
            "auth cleanup --lease",
            "<RunLevel>LeastPrivilege",
        ] {
            assert!(xml.contains(guard));
        }
        assert!(!xml.contains("powershell"));
    }
}
