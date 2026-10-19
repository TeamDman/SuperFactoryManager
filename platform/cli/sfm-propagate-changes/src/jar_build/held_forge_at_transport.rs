//! Exact captured project AT bytes held at the path actually opened by Java.
use super::project_compilation_cache;
use crate::source_projection::core_input_leases::DirectoryLeases;
use eyre::Result;
use eyre::ensure;
use std::fs::File;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Path;

pub(super) struct HeldForgeAtTransport {
    _file: File,
    _ancestry: DirectoryLeases,
    path: tempfile::TempPath,
}

impl HeldForgeAtTransport {
    pub(super) fn capture(bytes: &[u8]) -> Result<Option<Self>> {
        ensure!(
            bytes.len() <= 16 * 1024 * 1024,
            "Oversized captured project AT"
        );
        let mut writer = tempfile::Builder::new()
            .prefix("sfm-forge-at-")
            .suffix(".cfg")
            .tempfile()?;
        writer.write_all(bytes)?;
        writer.flush()?;
        // Closing the writer is necessary before imposing deny-write/delete sharing.
        let path = writer.into_temp_path();
        let mut ancestry = DirectoryLeases::default();
        let parent = path
            .parent()
            .ok_or_else(|| eyre::eyre!("AT transport has no parent"))?;
        let name = path
            .file_name()
            .and_then(|name| name.to_str())
            .ok_or_else(|| eyre::eyre!("AT transport is not Unicode"))?;
        if ancestry.prepare_if_available(parent, name)?.is_none() {
            return Ok(None);
        }
        let mut file = project_compilation_cache::open_read(&path)?;
        ensure!(
            file.metadata()?.len() == bytes.len() as u64,
            "AT transport length changed"
        );
        let mut actual = Vec::new();
        std::io::Read::by_ref(&mut file)
            .take(bytes.len() as u64 + 1)
            .read_to_end(&mut actual)?;
        ensure!(actual == bytes, "AT transport bytes changed");
        Ok(Some(Self {
            _file: file,
            _ancestry: ancestry,
            path,
        }))
    }

    pub(super) fn path(&self) -> &Path {
        &self.path
    }
}
