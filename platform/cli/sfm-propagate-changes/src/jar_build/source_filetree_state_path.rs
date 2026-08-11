use super::SourceJarPath;
use std::path::Path;
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct SourceFiletreeStatePath(PathBuf);

impl SourceFiletreeStatePath {
    pub(super) fn for_source_jar(source_jar: &SourceJarPath) -> eyre::Result<Self> {
        let path = source_jar.as_path();
        let stem = path
            .file_stem()
            .and_then(std::ffi::OsStr::to_str)
            .ok_or_else(|| eyre::eyre!("Jar path has no file stem: {}", path.display()))?;
        Ok(Self(
            path.with_file_name(format!("{stem}.filetree.input.blake3")),
        ))
    }

    #[must_use]
    pub(super) fn as_path(&self) -> &Path {
        &self.0
    }
}
