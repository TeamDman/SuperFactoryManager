use super::SymbolIndexArgs;
#[cfg(test)]
use super::SymbolIndexCommand;
use super::SymbolListArgs;
use super::SymbolListUsagesArgs;
use super::SymbolMoveArgs;
use super::SymbolProjectListArgs;
use super::SymbolRenameArgs;
use super::SymbolServeArgs;
use super::SymbolShowDefinitionArgs;
use crate::cancellation::CancellationToken;
use crate::cli::InvocationHomes;
use crate::cli::jar::BranchSelector;
use crate::cli::output::CliOutput;
use crate::jar_build::engine::select_project_source_cache;
use crate::java_analysis::JavaAnalysisDiagnosticOutput;
use crate::java_analysis::JavaClasspathMode;
use crate::java_analysis::JavaSourceWorkspace;
use crate::java_analysis::JavaSymbolSelector;
use crate::paths::AppHome;
use facet::Facet;
use figue::{self as args};
use std::env::current_dir;
use std::path::Path;
use std::path::PathBuf;

#[derive(Facet, Debug)]
pub struct SymbolArgs {
    #[facet(args::subcommand)]
    pub command: SymbolCommand,
}

impl SymbolArgs {
    /// # Errors
    ///
    /// Returns an error when symbol arguments or analysis fail.
    pub fn invoke(
        self,
        cancellation_token: &CancellationToken,
        homes: &InvocationHomes,
    ) -> eyre::Result<CliOutput> {
        let invocation_dir = current_dir()?;
        self.invoke_in(cancellation_token, &invocation_dir, homes)
    }

    /// Invoke relative-path arguments against an explicit directory.
    ///
    /// # Errors
    ///
    /// Returns an error when validation, workspace resolution, or analysis
    /// fails.
    pub fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
        homes: &InvocationHomes,
    ) -> eyre::Result<CliOutput> {
        self.command
            .invoke_in(cancellation_token, invocation_dir, homes)
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum SymbolCommand {
    /// Find the declaration selected by an Access Transformer style target.
    ShowDefinition(SymbolShowDefinitionArgs),
    /// Find references to a selected Java symbol.
    #[facet(args::alias = "list-usage")]
    ListUsages(SymbolListUsagesArgs),
    /// Enumerate symbols, optionally filtering exact selectors with `*` and `?`.
    List(SymbolListArgs),
    /// Enumerate projected Java declarations in one generated Gradle root.
    ProjectList(SymbolProjectListArgs),
    /// Inspect or refresh the immutable dependency symbol index.
    Index(SymbolIndexArgs),
    /// Serve framed definition-at-position requests over stdin/stdout.
    Serve(SymbolServeArgs),
    /// Rename a selected Java symbol.
    Rename(SymbolRenameArgs),
    /// Move a selected Java symbol.
    Move(SymbolMoveArgs),
}

impl SymbolCommand {
    /// # Errors
    ///
    /// Returns an error when the selected command fails.
    pub fn invoke(
        self,
        cancellation_token: &CancellationToken,
        homes: &InvocationHomes,
    ) -> eyre::Result<CliOutput> {
        let invocation_dir = current_dir()?;
        self.invoke_in(cancellation_token, &invocation_dir, homes)
    }

    /// Invoke the selected symbol command against an explicit directory.
    ///
    /// # Errors
    ///
    /// Returns an error when the selected command fails.
    pub fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
        homes: &InvocationHomes,
    ) -> eyre::Result<CliOutput> {
        match self {
            Self::ShowDefinition(args) => {
                args.invoke_in(cancellation_token, invocation_dir, homes.app_home())
            }
            Self::ListUsages(args) => {
                args.invoke_in(cancellation_token, invocation_dir, homes.app_home())
            }
            Self::List(args) => {
                args.invoke_in(cancellation_token, invocation_dir, homes.app_home())
            }
            Self::ProjectList(args) => args.invoke_in(cancellation_token, invocation_dir),
            Self::Index(args) => {
                args.invoke_in(cancellation_token, invocation_dir, homes.app_home())
            }
            Self::Serve(args) => {
                args.invoke_in(cancellation_token, invocation_dir, homes.app_home())
            }
            Self::Rename(args) => args.invoke(),
            Self::Move(args) => args.invoke(),
        }
    }
}

#[derive(Facet, Clone, Debug)]
pub struct SymbolWorkspaceArgs {
    /// Branch selector providing the SFM/Minecraft/Java context.
    #[facet(args::named)]
    pub branch: BranchSelector,
    /// Override a Java source root. Repeat for multiple roots.
    #[facet(default, args::named)]
    pub source_root: Vec<PathBuf>,
    /// Use the branch classpath or an isolated source-only classpath.
    #[facet(default, args::named)]
    pub classpath_mode: Option<JavaClasspathMode>,
}

impl SymbolWorkspaceArgs {
    /// # Errors
    ///
    /// Returns an error when source-root and classpath-mode arguments conflict.
    pub fn validate(&self) -> eyre::Result<()> {
        if !self.source_root.is_empty() && self.classpath_mode.is_none() {
            eyre::bail!("--source-root requires explicit --classpath-mode branch|isolated");
        }
        if self.source_root.is_empty()
            && matches!(self.classpath_mode, Some(JavaClasspathMode::Isolated))
        {
            eyre::bail!("--classpath-mode isolated requires at least one --source-root");
        }
        Ok(())
    }

    #[must_use]
    pub fn effective_classpath_mode(&self) -> JavaClasspathMode {
        self.classpath_mode.unwrap_or(JavaClasspathMode::Branch)
    }

    /// Resolve this argument set into a read-only Java source workspace.
    ///
    /// # Errors
    ///
    /// Returns an error when arguments conflict, branch selection fails, or a
    /// configured source root cannot be read.
    pub fn resolve(
        self,
        invocation_dir: &Path,
        app_home: &AppHome,
    ) -> eyre::Result<JavaSourceWorkspace> {
        self.validate()?;
        let mode = self.effective_classpath_mode();
        JavaSourceWorkspace::resolve(
            self.branch,
            &self.source_root,
            mode,
            invocation_dir,
            app_home,
        )
    }
}

/// Read-only symbol queries and the symbol server inspect an explicitly
/// selected historical branch or an exact catalogued generated projection.
#[derive(Facet, Clone, Debug)]
pub struct SymbolQueryWorkspaceArgs {
    /// Branch selector providing the SFM/Minecraft/Java context.
    #[facet(default, args::named)]
    pub branch: Option<BranchSelector>,
    /// Generated standalone Gradle project root, without branch resolution.
    #[facet(default, args::named)]
    pub project_root: Option<PathBuf>,
    /// Override a branch Java source root. Repeat for multiple roots.
    #[facet(default, args::named)]
    pub source_root: Vec<PathBuf>,
    /// Use the branch classpath or an isolated source-only classpath.
    #[facet(default, args::named)]
    pub classpath_mode: Option<JavaClasspathMode>,
    /// Explicit exact-major JDK home for a generated project.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,
    /// Guarded native source-cache override; no application/cache home is resolved here.
    #[facet(default, args::named)]
    pub cache_root: Option<PathBuf>,
}

pub struct ResolvedSymbolQueryWorkspace {
    pub workspace: JavaSourceWorkspace,
    pub branch: BranchSelector,
    pub generated_project: bool,
    pub project_root: Option<PathBuf>,
    pub cache_root: Option<PathBuf>,
}

pub(super) fn project_jdk_diagnostics(
    resolved: &ResolvedSymbolQueryWorkspace,
) -> Vec<JavaAnalysisDiagnosticOutput> {
    if !resolved.generated_project {
        return Vec::new();
    }
    resolved
        .workspace
        .diagnostics
        .iter()
        .filter(|diagnostic| diagnostic.code == "java.jdk-source-unavailable")
        .cloned()
        .collect()
}

pub(super) fn append_project_jdk_diagnostics(
    diagnostics: &mut Vec<JavaAnalysisDiagnosticOutput>,
    project_diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
) {
    if project_diagnostics.is_empty() {
        return;
    }
    diagnostics.extend(project_diagnostics);
    diagnostics.sort();
    diagnostics.dedup();
}

impl SymbolQueryWorkspaceArgs {
    /// # Errors
    ///
    /// Returns an error for ambiguous or incompatible workspace selectors.
    pub fn validate(&self) -> eyre::Result<()> {
        match (&self.branch, &self.project_root) {
            (Some(_), None) => {
                if self.java_home.is_some() {
                    eyre::bail!("--java-home requires --project-root");
                }
                if !self.source_root.is_empty() && self.classpath_mode.is_none() {
                    eyre::bail!("--source-root requires explicit --classpath-mode branch|isolated");
                }
                if self.source_root.is_empty()
                    && matches!(self.classpath_mode, Some(JavaClasspathMode::Isolated))
                {
                    eyre::bail!("--classpath-mode isolated requires at least one --source-root");
                }
            }
            (None, Some(project_root)) => {
                if project_root.as_os_str().is_empty() {
                    eyre::bail!("--project-root cannot be empty");
                }
                if !self.source_root.is_empty() || self.classpath_mode.is_some() {
                    eyre::bail!(
                        "--project-root cannot be combined with --source-root or --classpath-mode"
                    );
                }
            }
            (Some(_), Some(_)) => {
                eyre::bail!("choose exactly one of --branch or --project-root");
            }
            (None, None) => {
                eyre::bail!("provide exactly one of --branch or --project-root");
            }
        }
        Ok(())
    }

    /// Resolve a query workspace without acquiring project dependencies.
    ///
    /// # Errors
    ///
    /// Returns an error when the selected workspace cannot be read.
    pub fn resolve(
        self,
        invocation_dir: &Path,
        app_home: &AppHome,
    ) -> eyre::Result<ResolvedSymbolQueryWorkspace> {
        self.validate()?;
        let cache_root = self
            .cache_root
            .map(|path| resolve_invocation_path(path, invocation_dir));
        if let Some(branch) = self.branch {
            let mode = self.classpath_mode.unwrap_or(JavaClasspathMode::Branch);
            let workspace = JavaSourceWorkspace::resolve(
                branch.clone(),
                &self.source_root,
                mode,
                invocation_dir,
                app_home,
            )?;
            return Ok(ResolvedSymbolQueryWorkspace {
                workspace,
                branch,
                generated_project: false,
                project_root: None,
                cache_root,
            });
        }
        let project_root = self
            .project_root
            .ok_or_else(|| eyre::eyre!("--project-root is required"))?;
        let project_root = resolve_invocation_path(project_root, invocation_dir);
        let java_home = self
            .java_home
            .map(|home| resolve_invocation_path(home, invocation_dir));
        let selected = select_project_source_cache(
            &project_root,
            cache_root.as_deref(),
            &CancellationToken::new(),
        )?;
        let workspace = JavaSourceWorkspace::resolve_generated_project_with_cache(
            &project_root,
            &selected.home().join("minecraft-toolchain").join("jbrsdk"),
            &selected.home().0,
            java_home.as_deref(),
        )?;
        eyre::ensure!(
            workspace.context.classpath_mode == JavaClasspathMode::Isolated
                && workspace.classpath_entries.is_empty(),
            "generated-project symbol query must remain isolated from branch dependencies"
        );
        let branch = BranchSelector(workspace.context.branch.clone());
        Ok(ResolvedSymbolQueryWorkspace {
            workspace,
            branch,
            generated_project: true,
            project_root: Some(project_root),
            cache_root,
        })
    }
}

fn resolve_invocation_path(path: PathBuf, invocation_dir: &Path) -> PathBuf {
    if path.is_absolute() {
        path
    } else {
        invocation_dir.join(path)
    }
}

pub(super) struct ParsedMutationRequest {
    pub selector: JavaSymbolSelector,
    pub destination: String,
}

pub(super) fn parse_mutation_request(
    terms: &[String],
    dry_run: bool,
    apply: bool,
    output_root: Option<&PathBuf>,
) -> eyre::Result<ParsedMutationRequest> {
    if dry_run == apply {
        eyre::bail!("exactly one of --dry-run or --apply is required");
    }
    if dry_run && output_root.is_some() {
        eyre::bail!("--output-root is only valid with --apply");
    }
    let Some((destination, selector_terms)) = terms.split_last() else {
        eyre::bail!("symbol mutation requires a selector and destination");
    };
    if selector_terms.is_empty() {
        eyre::bail!("symbol mutation requires a selector before the destination");
    }
    Ok(ParsedMutationRequest {
        selector: JavaSymbolSelector::parse_terms(selector_terms)?,
        destination: destination.clone(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command as CliCommand;
    use crate::cli::output::OutputFormat;

    fn parse(arguments: &[&str]) -> Cli {
        figue::from_slice::<Cli>(arguments)
            .into_result()
            .expect("symbol CLI should parse")
            .get_silent()
    }

    #[test]
    fn parses_symbol_cli_custom_roots_and_requires_explicit_mode() {
        let cli = parse(&[
            "symbol",
            "show-definition",
            "example.A",
            "--branch",
            "1.19.2",
            "--source-root",
            "source",
        ]);
        let CliCommand::Symbol(SymbolArgs {
            command: SymbolCommand::ShowDefinition(args),
        }) = cli.command
        else {
            panic!("expected symbol show-definition command");
        };
        assert!(args.workspace.validate().is_err());

        let cli = parse(&[
            "symbol",
            "show-definition",
            "example.A",
            "--branch",
            "1.19.2",
            "--source-root",
            "source",
            "--classpath-mode",
            "isolated",
        ]);
        let CliCommand::Symbol(SymbolArgs {
            command: SymbolCommand::ShowDefinition(args),
        }) = cli.command
        else {
            panic!("expected symbol show-definition command");
        };
        args.workspace.validate().expect("explicit isolated mode");
    }

    #[test]
    fn parses_symbol_cli_mutation_modes_without_mutating() {
        let cli = parse(&[
            "symbol",
            "rename",
            "example.A",
            "renamed",
            "--branch",
            "1.19.2",
            "--dry-run",
        ]);
        let CliCommand::Symbol(SymbolArgs {
            command: SymbolCommand::Rename(args),
        }) = cli.command
        else {
            panic!("expected symbol rename command");
        };
        let rendered = args
            .invoke()
            .expect("Phase 0 unsupported report")
            .render(Some(OutputFormat::Json), false)
            .expect("JSON rendering")
            .expect("typed output");
        assert!(rendered.contains("sfm.symbol-mutation/1"));
        assert!(rendered.contains("unsupported"));

        let cli = parse(&[
            "symbol",
            "move",
            "example.A",
            "example.B",
            "--branch",
            "1.19.2",
        ]);
        let CliCommand::Symbol(SymbolArgs {
            command: SymbolCommand::Move(args),
        }) = cli.command
        else {
            panic!("expected symbol move command");
        };
        let _ = args.invoke().unwrap_err();

        let both = parse(&[
            "symbol",
            "rename",
            "example.A",
            "Renamed",
            "--branch",
            "1.19.2",
            "--dry-run",
            "--apply",
        ]);
        let CliCommand::Symbol(SymbolArgs {
            command: SymbolCommand::Rename(args),
        }) = both.command
        else {
            panic!("expected symbol rename command");
        };
        let _ = args.invoke().unwrap_err();

        let preview_copy = parse(&[
            "symbol",
            "rename",
            "example.A",
            "Renamed",
            "--branch",
            "1.19.2",
            "--dry-run",
            "--output-root",
            "after-actual",
        ]);
        let CliCommand::Symbol(SymbolArgs {
            command: SymbolCommand::Rename(args),
        }) = preview_copy.command
        else {
            panic!("expected symbol rename command");
        };
        let _ = args.invoke().unwrap_err();

        let apply_copy = parse(&[
            "symbol",
            "move",
            "example.A",
            "example.B",
            "--branch",
            "1.19.2",
            "--apply",
            "--output-root",
            "after-actual",
        ]);
        let CliCommand::Symbol(SymbolArgs {
            command: SymbolCommand::Move(args),
        }) = apply_copy.command
        else {
            panic!("expected symbol move command");
        };
        args.invoke().unwrap();
    }

    #[test]
    fn read_only_queries_require_one_workspace_selector() {
        let mut workspace = SymbolQueryWorkspaceArgs {
            branch: None,
            project_root: None,
            source_root: Vec::new(),
            classpath_mode: None,
            java_home: None,
            cache_root: None,
        };
        assert!(workspace.validate().is_err());
        workspace.project_root = Some(PathBuf::from("generated-project"));
        workspace.validate().unwrap();
        workspace.branch = Some(BranchSelector("1.19.2".to_owned()));
        assert!(workspace.validate().is_err());
        workspace.project_root = None;
        workspace.validate().unwrap();
    }

    #[test]
    fn generated_project_query_options_are_isolated_from_branch_options() {
        let base = SymbolQueryWorkspaceArgs {
            branch: None,
            project_root: Some(PathBuf::from("generated-project")),
            source_root: Vec::new(),
            classpath_mode: None,
            java_home: Some(PathBuf::from("selected-jdk")),
            cache_root: None,
        };
        base.validate().unwrap();
        let mut conflicting = base.clone();
        conflicting.source_root.push(PathBuf::from("custom-source"));
        assert!(conflicting.validate().is_err());
        let mut conflicting = base.clone();
        conflicting.classpath_mode = Some(JavaClasspathMode::Branch);
        assert!(conflicting.validate().is_err());
        let mut conflicting = base;
        conflicting.project_root = None;
        conflicting.branch = Some(BranchSelector("1.19.2".to_owned()));
        assert!(
            conflicting.validate().is_err(),
            "--java-home is project-only"
        );
    }
}
