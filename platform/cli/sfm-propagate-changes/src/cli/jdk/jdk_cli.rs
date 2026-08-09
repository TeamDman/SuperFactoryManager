use super::JdkListArgs;
use facet::Facet;
use figue as args;

/// Arguments for JDK discovery commands.
#[derive(Facet, Debug)]
pub struct JdkArgs {
    /// JDK subcommand.
    #[facet(args::subcommand)]
    pub command: JdkCommand,
}

impl JdkArgs {
    /// # Errors
    ///
    /// Returns an error if the selected JDK command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// JDK discovery and selection commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum JdkCommand {
    /// List JDKs discovered by the clean-slate toolchain
    List(JdkListArgs),
}

impl JdkCommand {
    /// # Errors
    ///
    /// This function will return an error if JDK discovery fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            JdkCommand::List(args) => args.invoke(),
        }
    }
}
