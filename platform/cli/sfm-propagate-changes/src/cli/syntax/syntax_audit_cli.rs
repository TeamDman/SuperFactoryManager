use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use facet::Facet;
use figue::{self as args};
use std::path::PathBuf;

#[derive(Debug, Facet)]
pub struct SyntaxAuditArgs {
    /// Repository root, not the Minecraft run directory.
    #[facet(args::named)]
    pub root: PathBuf,
}

impl SyntaxAuditArgs {
    /// # Errors
    /// Fails for invalid roots, enumeration errors or cancelled engine probes.
    pub fn invoke(self, cancellation: &CancellationToken) -> eyre::Result<CliOutput> {
        Ok(CliOutput::facet(crate::syntax_highlight::audit_repository(
            &self.root,
            cancellation,
        )?))
    }
}
