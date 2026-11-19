use crate::cli::jar::BranchSelector;
use crate::paths::AppHome;
use crate::source_audit::SourceAuditCommand;
use crate::source_audit::SourceLanguage;
use crate::source_audit::SourceLineLimit;
use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;

/// Options for auditing tracked source files and cross-version change surfaces.
#[derive(Facet, Debug, Clone)]
pub struct AuditArgs {
    /// Historical branch selector; choose exactly one of --branch and --project-root.
    #[facet(default, args::named)]
    pub branch: Option<BranchSelector>,

    /// Exact catalogued generated projection to audit without branch/global-root fallback.
    #[facet(default, args::named)]
    pub project_root: Option<PathBuf>,

    /// Source language to audit. Repeat for multiple languages. Defaults to rust and java.
    #[facet(default, args::named)]
    pub language: Vec<SourceLanguage>,

    /// Short alias for `--language`.
    #[facet(default, args::named)]
    pub lang: Vec<SourceLanguage>,

    /// Opt in to source-size warnings by setting the maximum permitted line count.
    #[facet(args::named)]
    pub max_lines: Option<SourceLineLimit>,

    /// Compare selected version branches with 1.19.2 and warn about CLI or unbounded Java changes.
    #[facet(default = false, args::named)]
    pub version_surfaces: bool,

    /// Audit Java text-rendering calls against the declarative `sfm.audit_rules` policy. Enabled by default; use `--no-font-render-surface` only for focused diagnostic runs.
    #[facet(default = true, args::named)]
    pub font_render_surface: bool,
}

impl AuditArgs {
    pub(crate) fn validate_selection(&self) -> eyre::Result<()> {
        eyre::ensure!(
            self.branch.is_some() != self.project_root.is_some(),
            "provide exactly one of --branch or --project-root"
        );
        eyre::ensure!(
            !self.version_surfaces || self.project_root.is_none(),
            "--version-surfaces compares historical Git branches; use --branch for that audit"
        );
        Ok(())
    }

    /// # Errors
    ///
    /// Returns an error if source auditing fails.
    pub fn invoke_in(mut self, invocation_dir: &Path, app_home: &AppHome) -> eyre::Result<()> {
        self.validate_selection()?;
        self.language.append(&mut self.lang);
        let command =
            SourceAuditCommand::new(self.language, self.max_lines, self.font_render_surface);
        if let Some(branch) = self.branch {
            return command.invoke_branch(
                &branch.into_query(app_home)?,
                self.version_surfaces,
                app_home,
            );
        }
        let project_root = self
            .project_root
            .ok_or_else(|| eyre::eyre!("provide exactly one of --branch or --project-root"))?;
        command.invoke_catalog_project(&invocation_dir.join(project_root))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn audit_args() -> AuditArgs {
        AuditArgs {
            branch: None,
            project_root: Some(PathBuf::from("selected-projection")),
            language: Vec::new(),
            lang: Vec::new(),
            max_lines: None,
            version_surfaces: false,
            font_render_surface: true,
        }
    }

    #[test]
    fn catalog_audit_requires_one_explicit_selection_and_keeps_version_audit_historical()
    -> eyre::Result<()> {
        let mut args = audit_args();
        args.validate_selection()?;

        args.branch = Some(BranchSelector("1.19.2".to_owned()));
        assert!(args.validate_selection().is_err());
        let project_root = args.project_root.take();
        args.version_surfaces = true;
        args.validate_selection()?;

        args.branch = None;
        assert!(args.validate_selection().is_err());
        args.project_root = project_root;
        assert!(args.validate_selection().is_err());
        Ok(())
    }

    #[test]
    fn catalog_audit_resolves_project_paths_against_supplied_invocation_directory()
    -> eyre::Result<()> {
        let invocation = tempfile::tempdir()?;
        let app_home = AppHome(invocation.path().join("app-home"));
        let expected = invocation.path().join("selected-projection");
        let error = audit_args()
            .invoke_in(invocation.path(), &app_home)
            .unwrap_err();
        assert_eq!(
            error.to_string(),
            format!("cannot inspect directory '{}'", expected.display())
        );
        Ok(())
    }
}
