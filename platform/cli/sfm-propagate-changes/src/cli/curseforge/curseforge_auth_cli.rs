use crate::curseforge::discovery_cleanup;
use crate::curseforge::discovery_login;
use crate::curseforge::discovery_logout;
use crate::curseforge::discovery_status;
use crate::terminal_output::stdout_line;
use facet::Facet;
use figue as args;

/// Explicit authorization for repeated read-only discovery.
#[derive(Facet, Debug)]
pub struct CurseforgeAuthArgs {
    #[facet(args::subcommand)]
    pub command: CurseforgeAuthCommand,
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeAuthCommand {
    /// Ask 1Password once and create an encrypted, expiring discovery lease.
    Login(CurseforgeAuthLoginArgs),
    /// Show local lease counts and expiry without accessing 1Password.
    Status,
    /// Delete all local discovery leases and their cleanup tasks.
    Logout,
    /// Delete one generated discovery lease and its task (used by scheduled cleanup).
    Cleanup(CurseforgeAuthCleanupArgs),
}

#[derive(Facet, Debug)]
pub struct CurseforgeAuthCleanupArgs {
    /// Generated lease identity, not a filesystem path.
    #[facet(args::named)]
    pub lease: String,
}

#[derive(Facet, Debug)]
#[repr(u8)]
#[facet(rename_all = "kebab-case")]
pub enum CurseforgeAuthPurpose {
    Discovery,
}

#[derive(Facet, Debug)]
pub struct CurseforgeAuthLoginArgs {
    /// Only discovery may be cached. Publishing always needs separate authorization.
    #[facet(args::named)]
    pub purpose: CurseforgeAuthPurpose,
    /// Absolute lifetime, from 1s to 1h (for example 20m).
    #[facet(args::named)]
    pub ttl: String,
    /// Optional Core API key reference; defaults to the configured Core 1Password item.
    #[facet(default, args::named)]
    pub op_secret: Option<String>,
}

impl CurseforgeAuthArgs {
    /// # Errors
    /// Returns an error when authorization or secure credential lifecycle operations fail.
    pub fn invoke(self) -> eyre::Result<()> {
        match self.command {
            CurseforgeAuthCommand::Login(args) => {
                stdout_line(discovery_login(&args.ttl, args.op_secret.as_deref())?)
            }
            CurseforgeAuthCommand::Status => stdout_line(discovery_status()?),
            CurseforgeAuthCommand::Logout => stdout_line(format!(
                "Removed {} discovery lease(s). The underlying API key was not revoked.",
                discovery_logout()?
            )),
            CurseforgeAuthCommand::Cleanup(args) => discovery_cleanup(&args.lease),
        }
    }
}
