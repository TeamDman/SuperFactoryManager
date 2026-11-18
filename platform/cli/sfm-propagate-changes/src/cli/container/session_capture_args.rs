use super::session_target_args::SessionTargetArgs;
use crate::container_build::SessionCaptureRequest;
use crate::container_build::SessionRequest;
use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;
#[derive(Debug, Facet)]
pub struct SessionCaptureArgs {
    #[facet(flatten)]
    pub target: SessionTargetArgs,
    #[facet(default = 0.0_f64, args::named)]
    pub angle: f64,
    #[facet(args::named)]
    pub output: PathBuf,
}
impl SessionCaptureArgs {
    pub(crate) fn into_request(self, invocation: &Path) -> eyre::Result<SessionRequest> {
        Ok(SessionRequest::Capture(SessionCaptureRequest {
            target: self.target.into_request()?,
            angle: self.angle,
            output: if self.output.is_absolute() {
                self.output
            } else {
                invocation.join(self.output)
            },
        }))
    }
}
