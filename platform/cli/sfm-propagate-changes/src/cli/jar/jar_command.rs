use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Jar directory and release artifact related commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum JarCommand {
    /// Jar directory related commands
    Dir {
        /// Directory subcommand
        #[facet(args::subcommand)]
        command: JarDirCommand,
    },
    /// Collect jars from each MC version based on that version's `mod_version`
    Collect,
    /// List jars in the configured jar directory
    List,
    /// Remove old SFM jar(s) and copy tracked-version jar to each tracked client mods folder
    #[facet(rename = "update-clients")]
    UpdateClients,
    /// Remove old SFM jar(s) and copy tracked-version jar to each tracked server mods folder
    #[facet(rename = "update-servers")]
    UpdateServers,
}

/// Jar directory commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum JarDirCommand {
    /// Set the jar directory path
    Set {
        /// The path to the jar directory
        #[facet(args::positional)]
        path: PathBuf,
    },
    /// Remove jars from the configured jar directory while keeping the directory
    Clean,
    /// Show the current jar directory path
    Show,
    /// Open the jar directory in the file explorer
    Open,
}

impl JarCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            JarCommand::Dir { command } => command.invoke(),
            JarCommand::Collect => super::jar_collect_command::invoke(),
            JarCommand::List => super::jar_list_command::invoke(),
            JarCommand::UpdateClients => super::jar_update_clients_command::invoke(),
            JarCommand::UpdateServers => super::jar_update_servers_command::invoke(),
        }
    }
}
