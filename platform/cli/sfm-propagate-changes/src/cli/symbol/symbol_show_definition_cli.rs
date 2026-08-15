use super::SymbolWorkspaceArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::java_analysis::DefinitionAtPositionEngine;
use crate::java_analysis::DefinitionAtPositionEngineLimits;
use crate::java_analysis::DefinitionAtPositionRequest;
use crate::java_analysis::DefinitionDocumentInput;
use crate::java_analysis::DefinitionTextPositionInput;
use crate::java_analysis::DefinitionWorkspaceIdentityInput;
use crate::java_analysis::JavaSymbolSelector;
use crate::java_analysis::blake3_content_hash;
use crate::java_analysis::definition_workspace_fingerprint;
use facet::Facet;
use figue::{self as args};
use std::path::Path;

#[derive(Facet, Debug)]
pub struct SymbolShowDefinitionArgs {
    /// Class, field, or method selector; class owners may be qualified suffixes.
    #[facet(default, args::positional)]
    pub selector: Vec<String>,
    /// Canonical Java path relative to one selected source root.
    #[facet(default, args::named)]
    pub source_path: Option<String>,
    /// Source-root ID used to disambiguate an otherwise repeated relative path.
    #[facet(default, args::named)]
    pub source_root_id: Option<String>,
    /// One-based line for a definition-at-position query.
    #[facet(default, args::named)]
    pub line: Option<u64>,
    /// One-based Unicode-scalar column for a definition-at-position query.
    #[facet(default, args::named)]
    pub column: Option<u64>,
    #[facet(flatten)]
    pub workspace: SymbolWorkspaceArgs,
}

#[derive(Debug)]
enum SymbolShowDefinitionInput {
    Selector(JavaSymbolSelector),
    Location {
        source_path: String,
        source_root_id: Option<String>,
        line: u64,
        column: u64,
    },
}

impl SymbolShowDefinitionArgs {
    /// # Errors
    ///
    /// Returns an error when workspace resolution, parsing, or indexing fails.
    pub fn invoke(self) -> eyre::Result<CliOutput> {
        let invocation_dir = std::env::current_dir()?;
        self.invoke_in(&CancellationToken::new(), &invocation_dir)
    }

    /// Run definition analysis against an explicit invocation directory.
    ///
    /// # Errors
    ///
    /// Returns an error when selector parsing, workspace resolution, parsing,
    /// or indexing fails.
    pub fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        let input = self.input()?;
        let branch = self.workspace.branch.clone();
        let workspace = self.workspace.resolve(invocation_dir)?;
        match input {
            SymbolShowDefinitionInput::Selector(selector) => {
                let (index, dependency_index) = super::build_query_index(
                    &workspace,
                    &branch,
                    false,
                    super::DependencySymbolQuery::Definition(&selector),
                    cancellation_token,
                )?;
                let mut report = index.definition(&selector);
                if let Some(dependency_index) = dependency_index {
                    report = report.with_dependency_index(dependency_index);
                }
                let exit_code = report.status();
                Ok(CliOutput::facet_with_csv_and_status(
                    report,
                    |report| Ok(report.to_csv()),
                    exit_code,
                ))
            }
            SymbolShowDefinitionInput::Location {
                source_path,
                source_root_id,
                line,
                column,
            } => {
                let source_file = workspace
                    .source_file_at(source_root_id.as_deref(), &source_path)?
                    .clone();
                let text = std::fs::read_to_string(&source_file.absolute_path)?;
                let content_hash = blake3_content_hash(&text);
                let position = DefinitionTextPositionInput::from_line_column(&text, line, column)
                    .unwrap_or(DefinitionTextPositionInput {
                        line,
                        column,
                        byte_offset: 0,
                    });
                let (dependencies, dependency_index) =
                    super::load_definition_at_position_dependencies(
                        &workspace,
                        &branch,
                        cancellation_token,
                    )?;
                let dependency_index_identity = dependency_index
                    .as_ref()
                    .map(|index| index.expected_identity.clone());
                let workspace_fingerprint = definition_workspace_fingerprint(
                    &workspace.context,
                    dependency_index_identity.as_deref(),
                )?;
                let address = format!(
                    "workspace://{}/{}",
                    source_file.root_id, source_file.root_relative_path
                );
                let request = DefinitionAtPositionRequest::new(
                    0,
                    0,
                    DefinitionWorkspaceIdentityInput {
                        branch: workspace.context.branch.clone(),
                        classpath_mode: workspace.context.classpath_mode,
                        source_roots: workspace.context.source_roots.clone(),
                        classpath_fingerprint: workspace.context.classpath_fingerprint.clone(),
                        dependency_index_identity,
                        workspace_fingerprint,
                        workspace_generation: 0,
                    },
                    DefinitionDocumentInput {
                        address,
                        root_id: source_file.root_id,
                        root_relative_path: source_file.root_relative_path,
                        report_path: source_file.report_path,
                        source_set: source_file.source_set,
                        text,
                        content_hash: content_hash.clone(),
                        disk_content_hash: Some(content_hash),
                    },
                    position,
                );
                let engine = DefinitionAtPositionEngine::new(
                    workspace,
                    dependencies,
                    dependency_index,
                    DefinitionAtPositionEngineLimits::default(),
                )?;
                let report = engine.analyze(&request, cancellation_token)?;
                let exit_code = report.status();
                Ok(CliOutput::facet_with_csv_and_status(
                    report,
                    |report| Ok(report.to_csv()),
                    exit_code,
                ))
            }
        }
    }

    fn input(&self) -> eyre::Result<SymbolShowDefinitionInput> {
        let has_selector = !self.selector.is_empty();
        let has_location = self.source_path.is_some()
            || self.source_root_id.is_some()
            || self.line.is_some()
            || self.column.is_some();
        if has_selector && has_location {
            eyre::bail!(
                "symbol show-definition accepts either selector terms or --source-path/--line/--column, not both"
            );
        }
        if has_selector {
            return Ok(SymbolShowDefinitionInput::Selector(
                JavaSymbolSelector::parse_terms(&self.selector)?,
            ));
        }
        let (Some(source_path), Some(line), Some(column)) =
            (&self.source_path, self.line, self.column)
        else {
            eyre::bail!("location mode requires all of --source-path, --line, and --column");
        };
        if line == 0 || column == 0 {
            eyre::bail!("--line and --column are one-based and must be positive");
        }
        Ok(SymbolShowDefinitionInput::Location {
            source_path: source_path.clone(),
            source_root_id: self.source_root_id.clone(),
            line,
            column,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::symbol::SymbolArgs;
    use crate::cli::symbol::SymbolCommand;

    fn parse(arguments: &[&str]) -> SymbolShowDefinitionArgs {
        let cli = figue::from_slice::<Cli>(arguments)
            .into_result()
            .expect("show-definition arguments should parse")
            .get_silent();
        let Command::Symbol(SymbolArgs {
            command: SymbolCommand::ShowDefinition(args),
        }) = cli.command
        else {
            panic!("expected symbol show-definition command");
        };
        args
    }

    #[test]
    fn location_grammar_parses_without_a_selector() {
        let args = parse(&[
            "symbol",
            "show-definition",
            "--source-path",
            "q/Use.java",
            "--source-root-id",
            "custom-0",
            "--line",
            "4",
            "--column",
            "7",
            "--branch",
            "1.19.2",
            "--source-root",
            "source",
            "--classpath-mode",
            "isolated",
        ]);
        assert!(args.selector.is_empty());
        let SymbolShowDefinitionInput::Location {
            source_path,
            source_root_id,
            line,
            column,
        } = args.input().expect("complete location mode")
        else {
            panic!("expected location input");
        };
        assert_eq!(source_path, "q/Use.java");
        assert_eq!(source_root_id.as_deref(), Some("custom-0"));
        assert_eq!((line, column), (4, 7));
    }

    #[test]
    fn selector_and_location_modes_are_mutually_exclusive_and_complete() {
        let mixed = parse(&[
            "symbol",
            "show-definition",
            "p.A",
            "--source-path",
            "p/A.java",
            "--line",
            "1",
            "--column",
            "1",
            "--branch",
            "1.19.2",
        ]);
        let _ = mixed.input().expect_err("mixed grammar must fail");

        let partial = parse(&[
            "symbol",
            "show-definition",
            "--source-path",
            "p/A.java",
            "--line",
            "1",
            "--branch",
            "1.19.2",
        ]);
        let _ = partial.input().expect_err("partial location must fail");

        let absent = parse(&["symbol", "show-definition", "--branch", "1.19.2"]);
        let _ = absent
            .input()
            .expect_err("missing selector/location must fail");

        let zero = parse(&[
            "symbol",
            "show-definition",
            "--source-path",
            "p/A.java",
            "--line",
            "0",
            "--column",
            "1",
            "--branch",
            "1.19.2",
        ]);
        let _ = zero.input().expect_err("zero line must fail");
    }
}
