use super::JdkAddArgs;
use super::JdkInstallArgs;
use super::JdkListArgs;
use super::JdkRemoveArgs;
use super::JdkShowArgs;
use crate::cancellation::CancellationToken;
use crate::cli::InvocationHomes;
use crate::cli::output::CliOutput;
use crate::paths::InvocationDirectory;
use facet::Facet;
use figue as args;

/// Arguments for managed SDK installation, discovery and machine-local preferences.
#[derive(Facet, Debug)]
pub struct JdkArgs {
    /// JDK subcommand.
    #[facet(args::subcommand)]
    pub command: JdkCommand,
}

impl JdkArgs {
    pub(crate) fn invoke_in(
        self,
        cancel: &CancellationToken,
        invocation_dir: &InvocationDirectory<'_>,
        homes: &InvocationHomes,
    ) -> eyre::Result<CliOutput> {
        self.command.invoke_in(cancel, invocation_dir, homes)
    }
}

/// Managed Linux SDK installation, discovery and machine-local preferences.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum JdkCommand {
    /// Install and fully verify one pinned SDK without project/dependency work.
    Install(JdkInstallArgs),
    /// Register a real JDK home by logical ID; this is not a lockfile pin.
    Add(JdkAddArgs),
    /// List registered preferences and discovered JDKs.
    List(JdkListArgs),
    /// Remove a preference mapping, retaining the installed JDK.
    Remove(JdkRemoveArgs),
    /// Show one stored JDK preference.
    Show(JdkShowArgs),
}

impl JdkCommand {
    /// # Errors
    /// This function returns an error if JDK inspection or storage fails.
    pub fn invoke_in(
        self,
        cancel: &CancellationToken,
        invocation_dir: &InvocationDirectory<'_>,
        homes: &InvocationHomes,
    ) -> eyre::Result<CliOutput> {
        match self {
            Self::Install(args) => {
                return Ok(CliOutput::facet(
                    args.install(cancel, invocation_dir.as_path())?,
                ));
            }
            Self::Add(args) => args.invoke_in(invocation_dir, homes.app_home()),
            Self::List(args) => args.invoke(homes.app_home()),
            Self::Remove(args) => args.invoke(homes.app_home()),
            Self::Show(args) => args.invoke(homes.app_home()),
        }?;
        Ok(CliOutput::none())
    }
}
