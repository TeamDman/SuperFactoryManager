use super::SessionTarget;
use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionStartRequest {
    pub target: SessionTarget,
    pub image: String,
    pub projection: String,
    pub game_test: String,
    pub java_max_heap_mib: u64,
    pub session_seconds: u64,
    pub startup_seconds: u64,
}
impl SessionStartRequest {
    pub(crate) fn command(&self) -> eyre::Result<Vec<String>> {
        self.target.validate()?;
        eyre::ensure!(
            self.projection == "sfm-dev/mc-1.19.2",
            "only the reviewed sfm-dev/mc-1.19.2 session projection is supported"
        );
        eyre::ensure!(
            (512..=8192).contains(&self.java_max_heap_mib)
                && (1..=28800).contains(&self.session_seconds)
                && (30..=900).contains(&self.startup_seconds),
            "heap/lifetime/startup exceeds reviewed bounds"
        );
        super::session_request::bare_test(&self.game_test)?;
        eyre::ensure!(
            self.image.len() <= 256
                && !self.image.is_empty()
                && !self.image.contains(['\n', '\r', '\0']),
            "invalid local image reference"
        );
        Ok(vec![
            self.projection.clone(),
            self.game_test.clone(),
            "--java-max-heap-mib".into(),
            self.java_max_heap_mib.to_string(),
            "--session-seconds".into(),
            self.session_seconds.to_string(),
            "--startup-seconds".into(),
            self.startup_seconds.to_string(),
        ])
    }
}
