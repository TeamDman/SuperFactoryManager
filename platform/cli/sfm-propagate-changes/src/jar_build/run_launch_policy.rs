//! Time and diagnostic coordination selected for one native launch.

use std::path::Path;
use std::time::Duration;

#[derive(Clone, Copy, Debug)]
pub(super) struct RunLaunchPolicy<'a> {
    pub(super) timeout: Option<Duration>,
    pub(super) diagnostic_launch_gate: Option<&'a Path>,
}
