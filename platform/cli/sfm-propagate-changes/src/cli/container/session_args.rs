use facet::Facet;
use figue as args;
#[derive(Debug, Facet)]
pub struct SessionArgs {
    #[facet(args::subcommand)]
    pub command: super::session_command::SessionCommand,
}
impl SessionArgs {
    pub(crate) fn into_request(
        self,
        invocation: &std::path::Path,
    ) -> eyre::Result<crate::container_build::SessionRequest> {
        use super::session_command::SessionCommand as C;
        use crate::container_build::SessionRequest as R;
        match self.command {
            C::Start(r) => r.into_request(invocation),
            C::Status(r) => Ok(R::Status(r.into_request()?)),
            C::Reset(r) => Ok(R::Reset(r.into_request()?)),
            C::RunTest(r) => r.into_request(invocation),
            C::Capture(r) => r.into_request(invocation),
            C::Stop(r) => r.into_request(invocation),
        }
    }
}
