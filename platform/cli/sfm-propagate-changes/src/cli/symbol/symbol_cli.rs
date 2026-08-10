use super::SymbolIndexArgs;
#[cfg(test)]
use super::SymbolIndexCommand;
use super::SymbolListArgs;
use super::SymbolListUsagesArgs;
use super::SymbolMoveArgs;
use super::SymbolRenameArgs;
use super::SymbolShowDefinitionArgs;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::cli::output::CliOutput;
use crate::java_analysis::JavaClasspathMode;
use crate::java_analysis::JavaSourceWorkspace;
use crate::java_analysis::JavaSymbolSelector;
use facet::Facet;
use figue::{self as args};
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
    pub fn invoke(self, cancellation_token: &CancellationToken) -> eyre::Result<CliOutput> {
        let invocation_dir = std::env::current_dir()?;
        self.invoke_in(cancellation_token, &invocation_dir)
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
    ) -> eyre::Result<CliOutput> {
        self.command.invoke_in(cancellation_token, invocation_dir)
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
    /// Inspect or refresh the immutable dependency symbol index.
    Index(SymbolIndexArgs),
    /// Rename a selected Java symbol.
    Rename(SymbolRenameArgs),
    /// Move a selected Java symbol.
    Move(SymbolMoveArgs),
}

impl SymbolCommand {
    /// # Errors
    ///
    /// Returns an error when the selected command fails.
    pub fn invoke(self, cancellation_token: &CancellationToken) -> eyre::Result<CliOutput> {
        let invocation_dir = std::env::current_dir()?;
        self.invoke_in(cancellation_token, &invocation_dir)
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
    ) -> eyre::Result<CliOutput> {
        match self {
            Self::ShowDefinition(args) => args.invoke_in(cancellation_token, invocation_dir),
            Self::ListUsages(args) => args.invoke_in(cancellation_token, invocation_dir),
            Self::List(args) => args.invoke_in(cancellation_token, invocation_dir),
            Self::Index(args) => args.invoke_in(cancellation_token, invocation_dir),
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
    pub fn resolve(self, invocation_dir: &Path) -> eyre::Result<JavaSourceWorkspace> {
        self.validate()?;
        let mode = self.effective_classpath_mode();
        JavaSourceWorkspace::resolve(self.branch, &self.source_root, mode, invocation_dir)
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
    use figue::ToArgs;

    fn parse(arguments: &[&str]) -> Cli {
        figue::from_slice::<Cli>(arguments)
            .into_result()
            .expect("symbol CLI should parse")
            .get_silent()
    }

    #[test]
    fn parses_symbol_cli_show_definition_and_usage_aliases() {
        let definition = parse(&[
            "symbol",
            "show-definition",
            "example.A",
            "--branch",
            "1.19.2",
        ]);
        let CliCommand::Symbol(SymbolArgs {
            command: SymbolCommand::ShowDefinition(definition),
        }) = definition.command
        else {
            panic!("expected symbol show-definition command");
        };
        assert_eq!(definition.selector, ["example.A"]);

        let canonical = parse(&[
            "--output-format",
            "json",
            "symbol",
            "list-usages",
            "example.A",
            "run(Ljava/lang/String;)V",
            "--branch",
            "1.19.2",
        ]);
        assert_eq!(
            canonical.global_args.output_format,
            Some(OutputFormat::Json)
        );
        let CliCommand::Symbol(SymbolArgs {
            command: SymbolCommand::ListUsages(canonical_args),
        }) = canonical.command
        else {
            panic!("expected symbol list-usages command");
        };
        assert!(matches!(
            JavaSymbolSelector::parse_terms(&canonical_args.selector).expect("method selector"),
            JavaSymbolSelector::Method { .. }
        ));

        let alias = parse(&[
            "symbol",
            "list-usage",
            "example.A",
            "run(Ljava/lang/String;)V",
            "--branch",
            "1.19.2",
        ]);
        let rendered = alias.to_args().expect("alias should serialize canonically");
        let rendered = rendered
            .iter()
            .map(|argument| argument.to_string_lossy())
            .collect::<Vec<_>>();
        assert!(rendered.iter().any(|argument| argument == "list-usages"));
        assert!(!rendered.iter().any(|argument| argument == "list-usage"));
    }

    #[test]
    fn parses_symbol_list_with_optional_canonical_selector_glob() {
        let all = parse(&["symbol", "list", "--branch", "1.19.2"]);
        let CliCommand::Symbol(SymbolArgs {
            command: SymbolCommand::List(all),
        }) = all.command
        else {
            panic!("expected symbol list command");
        };
        assert_eq!(all.pattern, None);

        let filtered = parse(&["symbol", "list", "example.* run*", "--branch", "1.19.2"]);
        let CliCommand::Symbol(SymbolArgs {
            command: SymbolCommand::List(filtered),
        }) = filtered.command
        else {
            panic!("expected filtered symbol list command");
        };
        assert_eq!(filtered.pattern.as_deref(), Some("example.* run*"));
    }

    #[test]
    fn removed_pre_phase_command_shapes_do_not_parse() {
        for arguments in [
            vec!["symbol", "definition", "example.A", "--branch", "1.19.2"],
            vec!["symbol", "usage", "list", "example.A", "--branch", "1.19.2"],
        ] {
            figue::from_slice::<Cli>(&arguments)
                .into_result()
                .unwrap_err();
        }
    }

    #[test]
    fn parses_symbol_index_refresh_and_show() {
        let refresh = parse(&["symbol", "index", "refresh", "--branch", "1.19.2"]);
        let CliCommand::Symbol(SymbolArgs {
            command:
                SymbolCommand::Index(SymbolIndexArgs {
                    command: SymbolIndexCommand::Refresh(refresh),
                }),
        }) = refresh.command
        else {
            panic!("expected symbol index refresh command");
        };
        assert_eq!(refresh.branch.0, "1.19.2");

        let show = parse(&["symbol", "index", "show", "--branch", "1.19.2"]);
        let CliCommand::Symbol(SymbolArgs {
            command:
                SymbolCommand::Index(SymbolIndexArgs {
                    command: SymbolIndexCommand::Show(show),
                }),
        }) = show.command
        else {
            panic!("expected symbol index show command");
        };
        assert_eq!(show.branch.0, "1.19.2");
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
    fn symbol_cli_requires_branch() {
        figue::from_slice::<Cli>(&["symbol", "show-definition", "example.A"])
            .into_result()
            .unwrap_err();
    }
}
