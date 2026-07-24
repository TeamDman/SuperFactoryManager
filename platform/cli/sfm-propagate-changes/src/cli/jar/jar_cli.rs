use super::JarArtifactAuditArgs;
use super::JarBuildArgs;
use super::JarCleanLoaderProbeArgs;
use super::JarCollectArgs;
use super::JarCompareArgs;
use super::JarDirArgs;
use super::JarListArgs;
use super::JarPlanArgs;
use super::JarUpdateClientsArgs;
use super::JarUpdateServersArgs;
use crate::cancellation::CancellationToken;
use facet::Facet;
use figue as args;

/// Arguments for jar directory and release artifact commands.
#[derive(Facet, Debug)]
pub struct JarArgs {
    /// Jar subcommand.
    #[facet(args::subcommand)]
    pub command: JarCommand,
}

impl JarArgs {
    /// # Errors
    ///
    /// Returns an error if the selected jar command fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        self.command.invoke(cancellation_token)
    }
}

/// Jar directory and release artifact related commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum JarCommand {
    /// Jar directory related commands
    Dir(JarDirArgs),
    /// Resolve the clean-slate jar build graph without running expensive tools
    Plan(JarPlanArgs),
    /// Build an SFM mod jar without invoking Gradle
    Build(JarBuildArgs),
    /// Compare the Gradle jar against the Rust-built jar
    Compare(JarCompareArgs),
    /// Launch a release JAR in a freshly installed production loader instance
    CleanLoaderProbe(JarCleanLoaderProbeArgs),
    /// Verify locked artifact cache and source provenance
    AuditArtifacts(JarArtifactAuditArgs),
    /// Collect jars from each MC version based on that version's `mod_version`
    Collect(JarCollectArgs),
    /// List jars in the configured jar directory
    List(JarListArgs),
    /// Remove old SFM jar(s) and copy tracked-version jar to each tracked client mods folder
    UpdateClients(JarUpdateClientsArgs),
    /// Remove old SFM jar(s) and copy tracked-version jar to each tracked server mods folder
    UpdateServers(JarUpdateServersArgs),
}

impl JarCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        match self {
            JarCommand::Dir(args) => args.invoke(),
            JarCommand::Plan(args) => args.invoke(cancellation_token),
            JarCommand::Build(args) => args.invoke(cancellation_token),
            JarCommand::Compare(args) => args.invoke(cancellation_token),
            JarCommand::CleanLoaderProbe(args) => args.invoke(cancellation_token),
            JarCommand::AuditArtifacts(args) => args.invoke(cancellation_token),
            JarCommand::Collect(args) => args.invoke(),
            JarCommand::List(args) => args.invoke(),
            JarCommand::UpdateClients(args) => args.invoke(),
            JarCommand::UpdateServers(args) => args.invoke(),
        }
    }
}
