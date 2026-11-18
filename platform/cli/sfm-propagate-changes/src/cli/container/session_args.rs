use super::session_command::SessionCommand;
use crate::container_build::SessionRequest;
use facet::Facet;
use figue as args;
use std::path::Path;
#[derive(Debug, Facet)]
pub struct SessionArgs {
    #[facet(args::subcommand)]
    pub command: super::session_command::SessionCommand,
}
impl SessionArgs {
    pub(crate) fn into_request(self, invocation: &Path) -> eyre::Result<SessionRequest> {
        match self.command {
            SessionCommand::Start(r) => r.into_request(invocation),
            SessionCommand::Status(r) => Ok(SessionRequest::Status(r.into_request()?)),
            SessionCommand::Reset(r) => Ok(SessionRequest::Reset(r.into_request()?)),
            SessionCommand::RunTest(r) => r.into_request(invocation),
            SessionCommand::Capture(r) => r.into_request(invocation),
            SessionCommand::Stop(r) => r.into_request(invocation),
        }
    }
}
