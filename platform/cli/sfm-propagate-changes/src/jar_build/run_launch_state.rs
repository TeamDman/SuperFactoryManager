//! Mutable invocation-owned state retained through one runtime launch.

use super::ArtifactLock;
use super::ForgeBuildHolds;
use std::path::PathBuf;

pub(super) struct RunLaunchState<'a> {
    pub(super) build_cache_lock: &'a mut Option<ArtifactLock>,
    pub(super) owned_console_log: &'a mut Option<PathBuf>,
    pub(super) build_holds: Option<&'a mut ForgeBuildHolds>,
}
