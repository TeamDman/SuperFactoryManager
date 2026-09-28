use super::SymbolQueryWorkspaceArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::java_analysis::JavaSymbolGlob;
use facet::Facet;
use figue::{self as args};
use std::path::Path;

#[derive(Facet, Debug)]
pub struct SymbolListArgs {
    /// Optional case-sensitive glob over canonical symbol selectors (`*` and `?`).
    #[facet(default, args::positional)]
    pub pattern: Option<String>,
    #[facet(flatten)]
    pub workspace: SymbolQueryWorkspaceArgs,
}

impl SymbolListArgs {
    /// # Errors
    ///
    /// Returns an error when workspace resolution, parsing, or indexing fails.
    pub fn invoke(self) -> eyre::Result<CliOutput> {
        let invocation_dir = std::env::current_dir()?;
        self.invoke_in(&CancellationToken::new(), &invocation_dir)
    }

    /// Run symbol enumeration against an explicit invocation directory.
    ///
    /// # Errors
    ///
    /// Returns an error when workspace resolution, parsing, or indexing fails.
    pub fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        let pattern = JavaSymbolGlob::new(self.pattern);
        let resolved = self.workspace.resolve(invocation_dir)?;
        let project_diagnostics = super::project_jdk_diagnostics(&resolved);
        let workspace = resolved.workspace;
        let (index, dependency_index) = super::build_query_index(
            &workspace,
            &resolved.branch,
            false,
            super::DependencySymbolQuery::List(&pattern),
            cancellation_token,
        )?;
        let mut report = index.list(&pattern);
        if let Some(dependency_index) = dependency_index {
            report = report.with_dependency_index(dependency_index);
        }
        super::append_project_jdk_diagnostics(&mut report.diagnostics, project_diagnostics);
        let exit_code = report.status();
        Ok(CliOutput::facet_with_csv_and_status(
            report,
            |report| Ok(report.to_csv()),
            exit_code,
        ))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::output::OutputFormat;
    use crate::cli::symbol::SymbolListUsagesArgs;
    use crate::cli::symbol::SymbolShowDefinitionArgs;
    use crate::source_projection::provenance::ProjectionProvenance;
    use std::path::PathBuf;

    fn generated_project_fixture() -> (tempfile::TempDir, SymbolQueryWorkspaceArgs) {
        let temporary = tempfile::tempdir().expect("temporary generated project");
        let project_root = temporary.path().join("generated-project");
        let main_root = project_root.join("src/main/java/example");
        let toolchain_root = project_root.join("gradle/java-toolchain/1.19.2");
        std::fs::create_dir_all(&main_root).expect("main source root");
        std::fs::create_dir_all(&toolchain_root).expect("toolchain root");
        std::fs::write(
            project_root.join("settings.gradle"),
            "rootProject.name = 'sfm'\n",
        )
        .expect("Gradle settings");
        std::fs::write(
            project_root.join(".sfm-source-projection-manifest.json"),
            ProjectionProvenance::new(
                "1.19.2",
                "1.19.2",
                "current-development-pilot",
                "blake3:test",
            )
            .to_json()
            .expect("projection manifest"),
        )
        .expect("projection manifest file");
        std::fs::write(
            toolchain_root.join("java-toolchain.gradle"),
            "JavaLanguageVersion.of(17)\n",
        )
        .expect("Java release");
        std::fs::write(
            main_root.join("Example.java"),
            "package example; public class Example { public int value; public int get() { return value; } }",
        )
        .expect("Java source");
        let workspace = SymbolQueryWorkspaceArgs {
            branch: None,
            project_root: Some(PathBuf::from("generated-project")),
            source_root: Vec::new(),
            classpath_mode: None,
            java_home: None,
        };
        (temporary, workspace)
    }

    #[test]
    fn generated_project_queries_use_only_project_sources_and_no_dependency_index() {
        let (temporary, workspace) = generated_project_fixture();
        let project_root = temporary.path().join("generated-project");
        let resolved = workspace.clone().resolve(temporary.path()).unwrap();
        assert_eq!(resolved.workspace.context.branch, "project:1.19.2");
        assert!(resolved.generated_project);
        assert!(resolved.workspace.classpath_entries.is_empty());

        let list = SymbolListArgs {
            pattern: Some("example.Example".to_owned()),
            workspace: workspace.clone(),
        }
        .invoke_in(&CancellationToken::new(), temporary.path())
        .expect("generated-project symbol list");
        assert_eq!(list.exit_code(), 0);
        let list_json = list
            .render(Some(OutputFormat::Json), false)
            .expect("JSON list report")
            .expect("typed list report");
        assert!(list_json.contains("example.Example"));
        assert!(list_json.contains("project:1.19.2"));
        assert!(list_json.contains("java.jdk-source-unavailable"));
        assert!(!list_json.contains("dependency_index"));

        let definition = SymbolShowDefinitionArgs {
            selector: vec!["example.Example".to_owned()],
            source_path: None,
            source_root_id: None,
            line: None,
            column: None,
            workspace: workspace.clone(),
        }
        .invoke_in(&CancellationToken::new(), temporary.path())
        .expect("generated-project definition");
        assert_eq!(definition.exit_code(), 0);
        let definition_json = definition
            .render(Some(OutputFormat::Json), false)
            .expect("JSON definition report")
            .expect("typed definition report");
        assert!(definition_json.contains("example.Example"));
        assert!(definition_json.contains("java.jdk-source-unavailable"));
        assert!(!definition_json.contains("dependency_index"));

        let usages = SymbolListUsagesArgs {
            selector: vec!["example.Example".to_owned()],
            source_path: None,
            source_root_id: None,
            line: None,
            column: None,
            workspace,
        }
        .invoke_in(&CancellationToken::new(), temporary.path())
        .expect("generated-project usages");
        let usage_json = usages
            .render(Some(OutputFormat::Json), false)
            .expect("JSON usage report")
            .expect("typed usage report");
        assert!(usage_json.contains("project:1.19.2"));
        assert!(usage_json.contains("java.jdk-source-unavailable"));
        assert!(!usage_json.contains("dependency_index"));
        assert!(!project_root.join(".gradle").exists());
        assert!(!project_root.join("build").exists());
    }

    #[test]
    fn generated_project_location_queries_do_not_acquire_branch_dependencies() {
        let (temporary, workspace) = generated_project_fixture();
        let project_root = temporary.path().join("generated-project");
        let resolved = workspace.clone().resolve(temporary.path()).unwrap();
        let source_file = resolved
            .workspace
            .files
            .iter()
            .find(|file| file.root_relative_path == "example/Example.java")
            .expect("generated Java source");
        let source_path = source_file.root_relative_path.clone();
        let source_root_id = Some(source_file.root_id.clone());
        let text = std::fs::read_to_string(&source_file.absolute_path).unwrap();
        let class_column =
            u64::try_from(text.find("class Example").unwrap() + "class ".len() + 1).unwrap();
        let usage_column = u64::try_from(text.rfind("value").unwrap() + 1).unwrap();

        let definition = SymbolShowDefinitionArgs {
            selector: Vec::new(),
            source_path: Some(source_path.clone()),
            source_root_id: source_root_id.clone(),
            line: Some(1),
            column: Some(class_column),
            workspace: workspace.clone(),
        }
        .invoke_in(&CancellationToken::new(), temporary.path())
        .expect("generated-project definition at location");
        let definition_json = definition
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(definition_json.contains("project:1.19.2"));
        assert!(definition_json.contains("example.Example"));
        assert!(definition_json.contains("java.jdk-source-unavailable"));
        assert!(!definition_json.contains("\"dependency_index\":"));

        let usages = SymbolListUsagesArgs {
            selector: Vec::new(),
            source_path: Some(source_path),
            source_root_id,
            line: Some(1),
            column: Some(usage_column),
            workspace,
        }
        .invoke_in(&CancellationToken::new(), temporary.path())
        .expect("generated-project usages at location");
        let usage_json = usages
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(usage_json.contains("project:1.19.2"));
        assert!(usage_json.contains("example.Example"));
        assert!(usage_json.contains("java.jdk-source-unavailable"));
        assert!(!usage_json.contains("\"dependency_index\":"));
        assert!(!project_root.join(".gradle").exists());
        assert!(!project_root.join("build").exists());
    }

    #[test]
    fn generated_project_queries_reject_missing_and_invalid_roots() {
        let (temporary, mut workspace) = generated_project_fixture();
        workspace.project_root = Some(PathBuf::from("missing-project"));
        let missing = workspace
            .clone()
            .resolve(temporary.path())
            .err()
            .expect("missing project root must fail");
        assert!(format!("{missing:#}").contains("Failed to resolve generated project root"));

        workspace.project_root = Some(PathBuf::from("generated-project"));
        std::fs::write(
            temporary
                .path()
                .join("generated-project/.sfm-source-projection-manifest.json"),
            "not a projection manifest",
        )
        .unwrap();
        let invalid = workspace
            .resolve(temporary.path())
            .err()
            .expect("malformed project manifest must fail");
        assert!(format!("{invalid:#}").contains("Invalid generated project manifest"));
    }
}
