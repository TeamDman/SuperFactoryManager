use super::SymbolWorkspaceArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::java_analysis::DefinitionAtPositionEngine;
use crate::java_analysis::DefinitionAtPositionEngineLimits;
use crate::java_analysis::DefinitionDocumentInput;
use crate::java_analysis::DefinitionTextPositionInput;
use crate::java_analysis::DefinitionWorkspaceIdentityInput;
use crate::java_analysis::JavaSymbolSelector;
use crate::java_analysis::UsageAtPositionRequest;
use crate::java_analysis::blake3_content_hash;
use crate::java_analysis::definition_workspace_fingerprint;
use facet::Facet;
use figue::{self as args};
use std::path::Path;

#[derive(Facet, Debug)]
pub struct SymbolListUsagesArgs {
    /// Class, field, or method selector; class owners may be qualified suffixes.
    #[facet(default, args::positional)]
    pub selector: Vec<String>,
    /// Canonical Java path relative to one selected source root.
    #[facet(default, args::named)]
    pub source_path: Option<String>,
    /// Source-root ID used to disambiguate an otherwise repeated relative path.
    #[facet(default, args::named)]
    pub source_root_id: Option<String>,
    /// One-based line for a usage-at-position query.
    #[facet(default, args::named)]
    pub line: Option<u64>,
    /// One-based Unicode-scalar column for a usage-at-position query.
    #[facet(default, args::named)]
    pub column: Option<u64>,
    #[facet(flatten)]
    pub workspace: SymbolWorkspaceArgs,
}

impl SymbolListUsagesArgs {
    /// # Errors
    ///
    /// Returns an error when workspace resolution, parsing, or indexing fails.
    pub fn invoke(self) -> eyre::Result<CliOutput> {
        let invocation_dir = std::env::current_dir()?;
        self.invoke_in(&CancellationToken::new(), &invocation_dir)
    }

    /// Run usage analysis against an explicit invocation directory.
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
            SymbolListUsagesInput::Selector(selector) => {
                let (index, dependency_index) = super::build_query_index(
                    &workspace,
                    &branch,
                    true,
                    super::DependencySymbolQuery::Usages(&selector),
                    cancellation_token,
                )?;
                let mut report = index.usages(&selector);
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
            SymbolListUsagesInput::Location {
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
                let (dependencies, dependency_index, dependency_source_roots) =
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
                let request = UsageAtPositionRequest::new(
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
                        address: format!(
                            "workspace://{}/{}",
                            source_file.root_id, source_file.root_relative_path
                        ),
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
                let engine = DefinitionAtPositionEngine::new_with_dependency_source_roots(
                    workspace,
                    dependencies,
                    dependency_index,
                    dependency_source_roots,
                    DefinitionAtPositionEngineLimits::default(),
                )?;
                let report = engine.analyze_usages(&request, cancellation_token)?;
                let exit_code = report.status();
                Ok(CliOutput::facet_with_csv_and_status(
                    report,
                    |report| Ok(report.to_csv()),
                    exit_code,
                ))
            }
        }
    }

    fn input(&self) -> eyre::Result<SymbolListUsagesInput> {
        let has_selector = !self.selector.is_empty();
        let has_location = self.source_path.is_some()
            || self.source_root_id.is_some()
            || self.line.is_some()
            || self.column.is_some();
        if has_selector && has_location {
            eyre::bail!(
                "symbol list-usages accepts either selector terms or --source-path/--line/--column, not both"
            );
        }
        if has_selector {
            return Ok(SymbolListUsagesInput::Selector(
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
        Ok(SymbolListUsagesInput::Location {
            source_path: source_path.clone(),
            source_root_id: self.source_root_id.clone(),
            line,
            column,
        })
    }
}

#[derive(Debug)]
enum SymbolListUsagesInput {
    Selector(JavaSymbolSelector),
    Location {
        source_path: String,
        source_root_id: Option<String>,
        line: u64,
        column: u64,
    },
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::symbol::SymbolArgs;
    use crate::cli::symbol::SymbolCommand;

    fn parse(arguments: &[&str]) -> SymbolListUsagesArgs {
        let cli = figue::from_slice::<Cli>(arguments)
            .into_result()
            .expect("list-usages arguments should parse")
            .get_silent();
        let Command::Symbol(SymbolArgs {
            command: SymbolCommand::ListUsages(args),
        }) = cli.command
        else {
            panic!("expected symbol list-usages command");
        };
        args
    }

    #[test]
    fn location_form_and_alias_parse_without_selector_terms() {
        for command in ["list-usages", "list-usage"] {
            let args = parse(&[
                "symbol",
                command,
                "--source-path",
                "q/Use.java",
                "--line",
                "4",
                "--column",
                "7",
                "--branch",
                "1.19.2",
            ]);
            assert!(matches!(
                args.input().expect("location input"),
                SymbolListUsagesInput::Location { .. }
            ));
        }
    }

    #[test]
    fn mixed_and_partial_location_forms_are_rejected() {
        let mixed = parse(&[
            "symbol",
            "list-usages",
            "p.A",
            "--source-path",
            "q/Use.java",
            "--line",
            "4",
            "--column",
            "7",
            "--branch",
            "1.19.2",
        ]);
        assert!(mixed.input().unwrap_err().to_string().contains("either"));

        let partial = parse(&[
            "symbol",
            "list-usages",
            "--source-path",
            "q/Use.java",
            "--line",
            "4",
            "--branch",
            "1.19.2",
        ]);
        assert!(
            partial
                .input()
                .unwrap_err()
                .to_string()
                .contains("requires all")
        );
    }
}
