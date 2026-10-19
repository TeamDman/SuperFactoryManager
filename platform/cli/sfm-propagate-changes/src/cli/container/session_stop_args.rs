use super::session_target_args::SessionTargetArgs;
use crate::container_build::SessionRequest;
use facet::Facet;
use figue as args;
#[derive(Debug, Facet)]
pub struct SessionStopArgs {
    #[facet(flatten)]
    pub target: SessionTargetArgs,
    #[facet(default, args::named)]
    pub output: Option<std::path::PathBuf>,
    #[facet(default, args::named)]
    pub remove: bool,
}
impl SessionStopArgs {
    pub(crate) fn into_request(self, invocation: &std::path::Path) -> eyre::Result<SessionRequest> {
        Ok(SessionRequest::Stop(
            crate::container_build::SessionStopRequest {
                target: self.target.into_request()?,
                output: self.output.map(|p| {
                    if p.is_absolute() {
                        p
                    } else {
                        invocation.join(p)
                    }
                }),
                remove: self.remove,
            },
        ))
    }
}
