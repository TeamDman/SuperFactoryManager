use super::DependencyAddArgs;
use crate::cancellation::CancellationToken;
use facet::Facet;
use figue as args;

#[derive(Facet, Debug)]
pub struct DependencyArgs {
    #[facet(args::subcommand)]
    pub command: DependencyCommand,
}

impl DependencyArgs {
    /// # Errors
    ///
    /// Returns an error if the selected dependency command fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        self.command.invoke(cancellation_token)
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum DependencyCommand {
    /// Accept the current artifact for a locked dependency.
    Add(DependencyAddArgs),
}

impl DependencyCommand {
    /// # Errors
    ///
    /// Returns an error if the selected dependency command fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        match self {
            Self::Add(args) => args.invoke(cancellation_token),
        }
    }
}
