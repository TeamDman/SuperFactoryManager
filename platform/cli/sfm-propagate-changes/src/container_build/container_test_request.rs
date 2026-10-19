use std::path::PathBuf;
#[derive(Debug)]
pub struct ContainerTestRequest {
    pub engine_endpoint: PathBuf,
    pub image: String,
    pub projection: String,
    pub context: PathBuf,
    pub output: PathBuf,
    pub timeout_seconds: u64,
}
impl ContainerTestRequest {
    pub(crate) fn validate(&self) -> eyre::Result<()> {
        eyre::ensure!(
            self.projection == "sfm-dev/mc-1.19.2",
            "Linux JUnit currently admits sfm-dev/mc-1.19.2 only"
        );
        eyre::ensure!(
            self.image.len() == 64 && self.image.bytes().all(|b| b.is_ascii_hexdigit()),
            "test requires an immutable existing image configuration ID"
        );
        eyre::ensure!(
            (1..=900).contains(&self.timeout_seconds),
            "JUnit timeout must be 1..900 seconds"
        );
        eyre::ensure!(
            !self.output.exists(),
            "test output already exists; choose a fresh directory"
        );
        Ok(())
    }
}
