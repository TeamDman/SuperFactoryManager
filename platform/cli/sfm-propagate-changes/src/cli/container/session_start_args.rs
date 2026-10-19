use super::session_target_args::SessionTargetArgs;
use crate::container_build::SessionRequest;
use facet::Facet;
use figue as args;
#[derive(Debug, Facet)]
pub struct SessionStartArgs {
    #[facet(flatten)]
    pub target: SessionTargetArgs,
    #[facet(args::named)]
    pub image: String,
    #[facet(default = "sfm-dev/mc-1.19.2", args::named)]
    pub projection: String,
    #[facet(default = "sfm:move_1_stack_direct", args::named)]
    pub game_test: String,
    #[facet(default = 4096_u64, args::named)]
    pub java_max_heap_mib: u64,
    #[facet(default = 900_u64, args::named)]
    pub session_seconds: u64,
    #[facet(default = 720_u64, args::named)]
    pub startup_seconds: u64,
}
impl SessionStartArgs {
    pub(crate) fn into_request(
        self,
        _invocation: &std::path::Path,
    ) -> eyre::Result<SessionRequest> {
        Ok(SessionRequest::Start(
            crate::container_build::SessionStartRequest {
                target: self.target.into_request()?,
                image: self.image,
                projection: self.projection,
                game_test: self.game_test,
                java_max_heap_mib: self.java_max_heap_mib,
                session_seconds: self.session_seconds,
                startup_seconds: self.startup_seconds,
            },
        ))
    }
}
