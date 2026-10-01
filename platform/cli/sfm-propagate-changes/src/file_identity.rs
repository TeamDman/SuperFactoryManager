//! Identity of an already-open file, separate from its pathname or contents.
//!
//! `std::fs::File` retains handle ownership. The Windows call only borrows that
//! handle; this module neither closes it nor introduces another owner.

use eyre::Result;
#[cfg(windows)]
use eyre::WrapErr as _;
use std::fs;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct FileIdentity {
    volume: u64,
    index: u128,
}

#[cfg(unix)]
pub(crate) fn file_identity(file: &fs::File) -> Result<FileIdentity> {
    use std::os::unix::fs::MetadataExt as _;

    let metadata = file.metadata()?;
    Ok(FileIdentity {
        volume: metadata.dev(),
        index: u128::from(metadata.ino()),
    })
}

// FILE_ID_INFO and FileIdInfo match the locked windows-rs 0.62.2 declarations
// in Windows/Win32/Storage/FileSystem: u64 volume, FILE_ID_128([u8; 16]), class18.
// https://learn.microsoft.com/en-us/windows/win32/api/winbase/ns-winbase-file_id_info
// https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-getfileinformationbyhandleex
#[cfg(windows)]
#[repr(C)]
struct WinFileIdInformation {
    volume_serial_number: u64,
    file_id: [u8; 16],
}

#[cfg(windows)]
const FILE_ID_INFORMATION_CLASS: i32 = 18;

#[cfg(windows)]
#[link(name = "kernel32")]
unsafe extern "system" {
    #[link_name = "GetFileInformationByHandleEx"]
    fn get_file_information_by_handle_ex(
        file: *mut std::ffi::c_void,
        information_class: i32,
        information: *mut std::ffi::c_void,
        buffer_bytes: u32,
    ) -> i32;
}

#[cfg(windows)]
pub(crate) fn file_identity(file: &fs::File) -> Result<FileIdentity> {
    use std::os::windows::io::AsRawHandle as _;

    let mut information = WinFileIdInformation {
        volume_serial_number: 0,
        file_id: [0; 16],
    };
    let buffer_bytes = u32::try_from(std::mem::size_of::<WinFileIdInformation>())?;
    // SAFETY: `file` owns a valid handle. The initialized writable output has
    // the FILE_ID_INFO layout and the supplied size covers the whole buffer.
    // Ownership stays with `file`; this query does not retain or close it.
    let success = unsafe {
        get_file_information_by_handle_ex(
            file.as_raw_handle(),
            FILE_ID_INFORMATION_CLASS,
            std::ptr::from_mut(&mut information).cast(),
            buffer_bytes,
        )
    };
    if success == 0 {
        return Err(std::io::Error::last_os_error())
            .wrap_err("could not identify opened file with 128-bit FileIdInfo");
    }
    Ok(FileIdentity {
        volume: information.volume_serial_number,
        index: u128::from_le_bytes(information.file_id),
    })
}

#[cfg(not(any(unix, windows)))]
pub(crate) fn file_identity(_file: &fs::File) -> Result<FileIdentity> {
    eyre::bail!("file identity is unsupported on this platform")
}

#[cfg(all(test, any(unix, windows)))]
mod tests {
    use super::*;

    #[test]
    fn repeated_opens_have_the_same_identity() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("same.bin");
        fs::write(&path, b"abc").unwrap();
        let first = fs::File::open(&path).unwrap();
        let second = fs::File::open(&path).unwrap();
        assert_eq!(
            file_identity(&first).unwrap(),
            file_identity(&second).unwrap()
        );
    }

    #[test]
    fn equal_contents_do_not_make_distinct_files_identical() {
        let temp = tempfile::tempdir().unwrap();
        let first = temp.path().join("first.bin");
        let second = temp.path().join("second.bin");
        fs::write(&first, b"abc").unwrap();
        fs::write(&second, b"abc").unwrap();
        assert_ne!(
            file_identity(&fs::File::open(first).unwrap()).unwrap(),
            file_identity(&fs::File::open(second).unwrap()).unwrap()
        );
    }

    #[test]
    fn renamed_handle_keeps_its_identity_but_replacement_does_not() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("original.bin");
        let moved = temp.path().join("moved.bin");
        fs::write(&path, b"abc").unwrap();
        let held = fs::File::open(&path).unwrap();
        let identity = file_identity(&held).unwrap();
        fs::rename(&path, &moved).unwrap();
        fs::write(&path, b"xyz").unwrap();
        assert_eq!(file_identity(&held).unwrap(), identity);
        assert_eq!(
            file_identity(&fs::File::open(moved).unwrap()).unwrap(),
            identity
        );
        assert_ne!(
            file_identity(&fs::File::open(path).unwrap()).unwrap(),
            identity
        );
    }

    #[test]
    fn identity_equality_retains_bits_above_sixty_four() {
        let low = FileIdentity {
            volume: 7,
            index: 19,
        };
        let high = FileIdentity {
            volume: 7,
            index: 19 | (1_u128 << 127),
        };
        assert_ne!(low, high);
    }

    #[cfg(windows)]
    #[test]
    fn file_id_information_has_the_reviewed_win32_abi_layout() {
        assert_eq!(std::mem::size_of::<WinFileIdInformation>(), 24);
        assert_eq!(
            std::mem::offset_of!(WinFileIdInformation, volume_serial_number),
            0
        );
        assert_eq!(std::mem::offset_of!(WinFileIdInformation, file_id), 8);
        assert_eq!(FILE_ID_INFORMATION_CLASS, 18);
    }
}
