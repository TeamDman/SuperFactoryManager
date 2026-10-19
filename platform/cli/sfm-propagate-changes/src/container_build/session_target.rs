use facet::Facet;
use std::path::PathBuf;
#[derive(Debug, Facet)]
pub struct SessionTarget {
    pub engine_endpoint: PathBuf,
    pub session: String,
}
impl SessionTarget {
    pub(crate) fn validate(&self) -> eyre::Result<()> {
        eyre::ensure!(
            self.session.starts_with("sfm-session-")
                && self.session.len() <= 64
                && self
                    .session
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b"_-".contains(&b)),
            "session name must start sfm-session- and use at most64 ASCII letters/digits/_/-"
        );
        Ok(())
    }
}
