use super::session_target_args::SessionTargetArgs;
use crate::container_build::SessionRequest;
use facet::Facet;
use figue as args;
#[derive(Debug, Facet)]
pub struct SessionRunTestArgs {
    #[facet(flatten)]
    pub target: SessionTargetArgs,
    #[facet(args::named)]
    pub game_test: String,
}
impl SessionRunTestArgs {
    pub(crate) fn into_request(
        self,
        _invocation: &std::path::Path,
    ) -> eyre::Result<SessionRequest> {
        Ok(SessionRequest::RunTest(
            crate::container_build::SessionRunTestRequest {
                target: self.target.into_request()?,
                game_test: self.game_test,
            },
        ))
    }
}
