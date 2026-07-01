use super::SourceAuditArgs;
use facet::Facet;
use figue as args;

#[derive(Facet, Debug)]
pub struct SourceArgs {
    #[facet(args::subcommand)]
    pub command: SourceCommand,
}

impl SourceArgs {
    /// # Errors
    ///
    /// Returns an error if the selected source command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum SourceCommand {
    /// Audit tracked Rust and Java source file sizes.
    Audit(SourceAuditArgs),
}

impl SourceCommand {
    /// # Errors
    ///
    /// Returns an error if the selected source command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Audit(args) => args.invoke(),
        }
    }
}
