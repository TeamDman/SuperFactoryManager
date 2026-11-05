use super::sha256;
use crate::source_projection::core_catalog::read_bounded_input;
use facet::Facet;
use std::path::Path;

/// SHA-256 identities of serialized JVM inputs, captured before effects.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct LaunchInputs {
    #[facet(rename = "runtime_classpath_sha256")]
    pub runtime_classpath: String,
    #[facet(rename = "argfile_sha256")]
    pub argfile: String,
    #[facet(rename = "working_directory_sha256")]
    pub working_directory: String,
}

impl LaunchInputs {
    fn capture(run_state_dir: &Path) -> eyre::Result<Self> {
        let classpath =
            read_bounded_input(run_state_dir, "testRuntimeClasspath.txt", 8 * 1024 * 1024)?;
        let argfile = read_bounded_input(run_state_dir, "junit.java.args", 8 * 1024 * 1024)?;
        let working_directory = read_bounded_input(
            run_state_dir,
            "junit-working-directory.json",
            8 * 1024 * 1024,
        )?;
        Ok(Self {
            runtime_classpath: sha256(&classpath),
            argfile: sha256(&argfile),
            working_directory: sha256(&working_directory),
        })
    }

    pub(in crate::jar_build::engine) fn capture_for_directory(
        run_state_dir: &Path,
        expected: &str,
    ) -> eyre::Result<Self> {
        let captured = Self::capture(run_state_dir)?;
        eyre::ensure!(
            captured.working_directory == expected.trim_start_matches("sha256:"),
            "JUnit working-directory record differs from the prepared launch"
        );
        Ok(captured)
    }

    pub(in crate::jar_build::engine) fn recheck(&self, run_state_dir: &Path) -> eyre::Result<()> {
        eyre::ensure!(
            *self == Self::capture(run_state_dir)?,
            "JUnit serialized launch inputs changed after admission; refusing Java effect or success receipt"
        );
        Ok(())
    }
}
