use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::paths::InvocationDirectory;
use crate::syntax_highlight::audit_repository;
use facet::Facet;
use figue::{self as args};
use std::path::Path;
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
    pub fn invoke(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        let root = InvocationDirectory::new(invocation_dir)?.resolve(&self.root)?;
        Ok(CliOutput::facet(audit_repository(&root, cancellation)?))
    }
}

#[cfg(test)]
mod tests {
    use super::SyntaxAuditArgs;
    use crate::cancellation::CancellationToken;
    use crate::cli::output::OutputFormat;
    use crate::syntax_highlight::SyntaxCoverageReport;
    use eyre::eyre;
    use std::fs;

    #[test]
    fn audit_resolves_a_relative_repository_from_the_supplied_directory() -> eyre::Result<()> {
        let invocation = tempfile::tempdir()?;
        let checkout = invocation.path().join("checkout");
        fs::create_dir(&checkout)?;
        gix::init(&checkout)?;
        let output = SyntaxAuditArgs {
            root: "checkout".into(),
        }
        .invoke(&CancellationToken::new(), invocation.path())?
        .render(Some(OutputFormat::Json), false)?
        .ok_or_else(|| eyre!("syntax audit returned no report"))?;
        let report: SyntaxCoverageReport = facet_json::from_str(&output)?;
        assert_eq!(
            report.repository_root,
            checkout.canonicalize()?.display().to_string()
        );
        Ok(())
    }
}
