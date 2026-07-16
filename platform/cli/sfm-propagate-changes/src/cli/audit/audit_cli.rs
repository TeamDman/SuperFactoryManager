use crate::cli::jar::BranchSelector;
use crate::source_audit::SourceAuditCommand;
use crate::source_audit::SourceAuditOptions;
use crate::source_audit::SourceLanguage;
use crate::source_audit::SourceLineLimit;
use facet::Facet;
use figue as args;

/// Options for auditing tracked source files and cross-version change surfaces.
#[derive(Facet, Debug, Clone)]
pub struct AuditArgs {
    /// Branch selector to audit.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// Source language to audit. Repeat for multiple languages. Defaults to rust and java.
    #[facet(default, args::named)]
    pub language: Vec<SourceLanguage>,

    /// Short alias for `--language`.
    #[facet(default, args::named)]
    pub lang: Vec<SourceLanguage>,

    /// Warn when a tracked source file has more than this many lines.
    #[facet(default, args::named)]
    pub max_lines: SourceLineLimit,

    /// Compare selected version branches with 1.19.2 and warn about CLI or unbounded Java changes.
    #[facet(default = false, args::named)]
    pub version_surfaces: bool,

    /// Audit Java text-rendering calls against the declarative `sfm.audit_rules` policy. Enabled by default; use `--no-font-render-surface` only for focused diagnostic runs.
    #[facet(default = true, args::named)]
    pub font_render_surface: bool,
}

impl AuditArgs {
    pub(crate) fn into_options(self) -> eyre::Result<SourceAuditOptions> {
        let mut languages = self.language;
        languages.extend(self.lang);
        Ok(SourceAuditOptions {
            branch: self.branch.into_query()?,
            languages,
            max_lines: self.max_lines,
            version_surfaces: self.version_surfaces,
            font_render_surface: self.font_render_surface,
        })
    }

    /// # Errors
    ///
    /// Returns an error if source auditing fails.
    pub fn invoke(self) -> eyre::Result<()> {
        SourceAuditCommand::new(self.into_options()?).invoke()
    }
}
