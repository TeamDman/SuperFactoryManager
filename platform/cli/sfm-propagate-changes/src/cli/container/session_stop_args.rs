use super::session_target_args::SessionTargetArgs;
use crate::container_build::SessionRequest;
use crate::container_build::SessionStopRequest;
use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;
#[derive(Debug, Facet)]
pub struct SessionStopArgs {
    #[facet(flatten)]
    pub target: SessionTargetArgs,
    #[facet(default, args::named)]
    pub output: Option<PathBuf>,
    #[facet(default, args::named)]
    pub remove: bool,
}
impl SessionStopArgs {
    pub(crate) fn into_request(self, invocation: &Path) -> eyre::Result<SessionRequest> {
        Ok(SessionRequest::Stop(SessionStopRequest {
            target: self.target.into_request()?,
            output: self.output.map(|p| {
                if p.is_absolute() {
                    p
                } else {
                    invocation.join(p)
                }
            }),
            remove: self.remove,
        }))
    }
}
