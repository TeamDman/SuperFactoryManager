use super::sha256;
use crate::source_projection::core_catalog::read_bounded_input;
use facet::Facet;
use std::path::Path;

/// Exact serialized JVM launch inputs captured before effects, not after execution.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct LaunchInputs {
    pub runtime_classpath_sha256: String,
    pub argfile_sha256: String,
}

impl LaunchInputs {
    pub(in crate::jar_build::engine) fn capture(run_state_dir: &Path) -> eyre::Result<Self> {
        let classpath =
            read_bounded_input(run_state_dir, "testRuntimeClasspath.txt", 8 * 1024 * 1024)?;
        let argfile = read_bounded_input(run_state_dir, "junit.java.args", 8 * 1024 * 1024)?;
        Ok(Self {
            runtime_classpath_sha256: sha256(&classpath),
            argfile_sha256: sha256(&argfile),
        })
    }

    pub(in crate::jar_build::engine) fn recheck(&self, run_state_dir: &Path) -> eyre::Result<()> {
        eyre::ensure!(
            *self == Self::capture(run_state_dir)?,
            "JUnit serialized launch inputs changed after admission; refusing Java effect or success receipt"
        );
        Ok(())
    }
}
