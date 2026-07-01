use crate::cli::jar::BranchSelector;
use crate::source_audit::SourceAuditCommand;
use crate::source_audit::SourceAuditOptions;
use crate::source_audit::SourceLanguage;
use crate::source_audit::SourceLineLimit;
use facet::Facet;
use figue as args;

/// Options for auditing tracked Rust and Java source file sizes.
#[derive(Facet, Debug, Clone)]
pub struct SourceAuditArgs {
    /// Branch selector to audit. Defaults to all worktrees.
    #[facet(default = default_source_audit_branch(), args::named)]
    pub branch: BranchSelector,

    /// Source language to audit. Repeat for multiple languages. Defaults to rust and java.
    #[facet(default, args::named)]
    pub language: Vec<SourceLanguage>,

    /// Short alias for `--language`.
    #[facet(default, args::named)]
    pub lang: Vec<SourceLanguage>,

    /// Warn when a tracked source file has more than this many lines.
    #[facet(rename = "max-lines", default, args::named)]
    pub max_lines: SourceLineLimit,
}

impl SourceAuditArgs {
    pub(crate) fn into_options(self) -> eyre::Result<SourceAuditOptions> {
        let mut languages = self.language;
        languages.extend(self.lang);
        Ok(SourceAuditOptions {
            branch: self.branch.into_query()?,
            languages,
            max_lines: self.max_lines,
        })
    }

    /// # Errors
    ///
    /// Returns an error if source auditing fails.
    pub fn invoke(self) -> eyre::Result<()> {
        SourceAuditCommand::new(self.into_options()?).invoke()
    }
}

fn default_source_audit_branch() -> BranchSelector {
    BranchSelector("*".to_string())
}
