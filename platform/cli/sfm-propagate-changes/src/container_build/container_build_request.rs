use super::ContainerEngine;
use eyre::ensure;
use std::path::PathBuf;
use std::time::Duration;

#[derive(Clone, Debug)]
pub struct ContainerBuildRequest {
    pub projection: String,
    pub profile: super::ContainerBuildProfile,
    pub repo_root: PathBuf,
    pub context: PathBuf,
    pub toolchain_lock: Option<PathBuf>,
    pub cache_root: PathBuf,
    pub platform: String,
    pub engine: ContainerEngine,
    pub transport: super::ContainerTransport,
    pub engine_endpoint: Option<PathBuf>,
    pub tag: String,
    pub prepare_only: bool,
    pub reuse_context: bool,
    pub offline: bool,
    pub max_bytes: u64,
    pub timeout: Duration,
}
impl ContainerBuildRequest {
    /// Validate the request without acquiring or creating anything.
    /// # Errors
    /// Rejects unsupported platforms, unsafe tags or budgets.
    pub fn validate(&self) -> eyre::Result<()> {
        crate::source_projection::projection_catalog::validate_projection_key(&self.projection)?;
        ensure!(
            !self.projection.contains(['*', '?', '[']),
            "container build requires one exact projection"
        );
        ensure!(
            super::policy::safe_projection(&self.projection),
            "container projection key has unsafe shell/image syntax"
        );
        ensure!(
            self.platform == "linux/amd64",
            "container build currently supports linux/amd64 only"
        );
        ensure!(
            !self.tag.is_empty()
                && self.tag.len() <= 255
                && !self.tag.starts_with('-')
                && self
                    .tag
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b"._-/:".contains(&b)),
            "invalid local image tag"
        );
        ensure!(
            (1..=32 * 1024 * 1024 * 1024).contains(&self.max_bytes),
            "context byte budget must be 1..32 GiB"
        );
        ensure!(
            !self.timeout.is_zero() && self.timeout <= Duration::from_hours(2),
            "timeout must be greater than zero and at most 2h"
        );
        Ok(())
    }
}
