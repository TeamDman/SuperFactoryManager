use super::SyntaxAuditArgs;
use super::SyntaxHighlightArgs;
use super::SyntaxServeArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use facet::Facet;
use figue::{self as args};
use std::path::Path;

#[derive(Facet, Debug)]
pub struct SyntaxArgs {
    #[facet(args::subcommand)]
    pub command: SyntaxCommand,
}

impl SyntaxArgs {
    /// # Errors
    ///
    /// Returns an error when highlighting or worker startup fails.
    pub fn invoke(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        self.command.invoke(cancellation_token, invocation_dir)
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum SyntaxCommand {
    /// Inventory repository file types and probe representative source highlighting.
    Audit(SyntaxAuditArgs),
    /// Highlight exact UTF-8 source supplied on stdin.
    Highlight(SyntaxHighlightArgs),
    /// Serve reusable framed syntax-highlight requests on stdin/stdout.
    Serve(SyntaxServeArgs),
}

impl SyntaxCommand {
    /// # Errors
    ///
    /// Returns an error when the selected syntax operation fails.
    pub fn invoke(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        match self {
            Self::Audit(args) => args.invoke(cancellation_token, invocation_dir),
            Self::Highlight(args) => args.invoke(cancellation_token),
            Self::Serve(args) => args.invoke(cancellation_token),
        }
    }
}
