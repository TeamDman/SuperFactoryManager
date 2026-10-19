use crate::cancellation::CancellationToken;
use eyre::Context;
use reqwest::StatusCode;
use reqwest::blocking::Client;
use std::path::Path;

pub(crate) trait PayloadFetcher {
    fn fetch(
        &self,
        url: &str,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<Option<Vec<u8>>>;
}

pub(crate) struct ReqwestFetcher(Client);

pub(crate) fn http_fetcher() -> eyre::Result<ReqwestFetcher> {
    let client = Client::builder()
        .user_agent(concat!("sfm-propagate-changes/", env!("CARGO_PKG_VERSION")))
        .build()
        .wrap_err("Failed to create HTTP client")?;
    Ok(ReqwestFetcher(client))
}

impl PayloadFetcher for ReqwestFetcher {
    fn fetch(
        &self,
        url: &str,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<Option<Vec<u8>>> {
        cancellation_token.bail_if_cancelled()?;
        let response = self
            .0
            .get(url)
            .send()
            .wrap_err_with(|| format!("Failed to fetch {url}"))?;
        if response.status() == StatusCode::NOT_FOUND {
            return Ok(None);
        }
        let response = response
            .error_for_status()
            .wrap_err_with(|| format!("Remote repository rejected {url}"))?;
        cancellation_token.bail_if_cancelled()?;
        Ok(Some(response.bytes()?.to_vec()))
    }
}

pub(crate) fn write_payload_atomically(path: &Path, bytes: &[u8]) -> eyre::Result<()> {
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Payload cache path has no parent: {}", path.display()))?;
    std::fs::create_dir_all(parent)
        .wrap_err_with(|| format!("Failed to create {}", parent.display()))?;
    let mut builder = tempfile::Builder::new();
    builder.prefix(".sfm-payload.").suffix(".tmp");
    #[cfg(windows)]
    let mut temporary = {
        use std::os::windows::fs::OpenOptionsExt as _;
        use windows::Win32::Storage::FileSystem::DELETE;
        use windows::Win32::Storage::FileSystem::FILE_GENERIC_READ;
        use windows::Win32::Storage::FileSystem::FILE_GENERIC_WRITE;

        // Normal staging avoids publishing FILE_ATTRIBUTE_TEMPORARY. Request
        // DELETE on our new file so the same synced handle can rename it.
        builder.make_in(parent, |staging_path| {
            std::fs::OpenOptions::new()
                .read(true)
                .write(true)
                .create_new(true)
                .access_mode((DELETE | FILE_GENERIC_READ | FILE_GENERIC_WRITE).0)
                .open(staging_path)
        })?
    };
    #[cfg(not(windows))]
    let mut temporary = builder.tempfile_in(parent)?;
    std::io::Write::write_all(&mut temporary, bytes)?;
    temporary.as_file_mut().sync_all()?;
    #[cfg(windows)]
    let publication = publish_payload_windows(path, &temporary);
    #[cfg(not(windows))]
    let publication = temporary
        .persist(path)
        .map(|_| ())
        .map_err(|error| error.error);
    publication.wrap_err_with(|| format!("Failed to publish {}", path.display()))?;
    Ok(())
}

#[cfg(windows)]
fn publish_payload_windows(
    path: &Path,
    temporary: &tempfile::NamedTempFile,
) -> std::io::Result<()> {
    use std::os::windows::ffi::OsStrExt as _;
    use std::os::windows::fs::MetadataExt as _;
    use std::os::windows::io::AsRawHandle as _;
    use windows::Win32::Foundation::HANDLE;
    use windows::Win32::Storage::FileSystem::FILE_ATTRIBUTE_REPARSE_POINT;
    use windows::Win32::Storage::FileSystem::FILE_RENAME_INFO;
    use windows::Win32::Storage::FileSystem::FileRenameInfoEx;
    use windows::Win32::Storage::FileSystem::SetFileInformationByHandle;

    match std::fs::symlink_metadata(path) {
        Ok(metadata)
            if metadata.is_file()
                && metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 == 0 => {}
        Ok(_) => {
            return Err(std::io::Error::new(
                std::io::ErrorKind::InvalidInput,
                "Payload destination must be a regular non-reparse file",
            ));
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error),
    }
    let filename: Vec<u16> = std::path::absolute(path)?
        .as_os_str()
        .encode_wide()
        .collect();
    let filename_bytes = std::mem::size_of_val(filename.as_slice());
    let buffer_bytes = std::mem::size_of::<FILE_RENAME_INFO>() + filename_bytes;
    // Use the API's own type for allocation alignment; the variable-length
    // filename continues beyond its one-element trailing FileName field.
    let mut buffer = vec![
        FILE_RENAME_INFO::default();
        buffer_bytes.div_ceil(std::mem::size_of::<FILE_RENAME_INFO>())
    ];
    // FILE_RENAME_REPLACE_IF_EXISTS | FILE_RENAME_POSIX_SEMANTICS retains old
    // handles while fresh opens see the complete replacement in one rename.
    buffer[0].Anonymous.Flags = 0x1 | 0x2;
    buffer[0].FileNameLength = u32::try_from(filename_bytes)
        .map_err(|error| std::io::Error::new(std::io::ErrorKind::InvalidInput, error))?;
    let filename_destination = buffer
        .as_mut_ptr()
        .wrapping_byte_add(std::mem::offset_of!(FILE_RENAME_INFO, FileName))
        .cast::<u16>();
    // SAFETY: the aligned allocation includes the complete UTF-16 name plus
    // header, and the source Vec does not overlap it. Both remain live below.
    unsafe {
        std::ptr::copy_nonoverlapping(filename.as_ptr(), filename_destination, filename.len());
    };
    let buffer_bytes = u32::try_from(buffer_bytes)
        .map_err(|error| std::io::Error::new(std::io::ErrorKind::InvalidInput, error))?;
    // SAFETY: the live synced staging handle has DELETE access. The buffer uses
    // the API layout and sufficient alignment/length for the entire filename.
    // Unsupported flags and denied deletion propagate without another fallback.
    unsafe {
        SetFileInformationByHandle(
            HANDLE(temporary.as_file().as_raw_handle()),
            FileRenameInfoEx,
            buffer.as_ptr().cast(),
            buffer_bytes,
        )
    }
    // This BOOL-based Win32 API reports GetLastError as HRESULT_FROM_WIN32.
    .map_err(|error| std::io::Error::from_raw_os_error(error.code().0 & 0xffff))
}
