use super::session_target_args::SessionTargetArgs;
use crate::container_build::SessionRequest;
use crate::container_build::SessionRunTestRequest;
use facet::Facet;
use figue as args;
use std::path::Path;
#[derive(Debug, Facet)]
pub struct SessionRunTestArgs {
    #[facet(flatten)]
    pub target: SessionTargetArgs,
    #[facet(args::named)]
    pub game_test: String,
}
impl SessionRunTestArgs {
    pub(crate) fn into_request(self, _invocation: &Path) -> eyre::Result<SessionRequest> {
        Ok(SessionRequest::RunTest(SessionRunTestRequest {
            target: self.target.into_request()?,
            game_test: self.game_test,
        }))
    }
}
