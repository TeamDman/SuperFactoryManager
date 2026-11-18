use crate::container_build::SessionTarget;
use facet::Facet;
use figue as args;
use std::path::PathBuf;
#[derive(Debug, Facet)]
pub struct SessionTargetArgs {
    /// Advertised local Podman6.0.2 pipe/socket; TCP endpoints are refused.
    #[facet(args::named)]
    pub engine_endpoint: PathBuf,
    /// Owned disposable worker name beginning sfm-session-.
    #[facet(args::named)]
    pub session: String,
}
impl SessionTargetArgs {
    pub(crate) fn into_request(self) -> eyre::Result<SessionTarget> {
        let target = SessionTarget {
            engine_endpoint: self.engine_endpoint,
            session: self.session,
        };
        target.validate()?;
        Ok(target)
    }
}
