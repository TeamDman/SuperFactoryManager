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
        let project_root = resolved.project_root.clone();
        let workspace = resolved.workspace;
        let result = super::build_query_index(
            &workspace,
            &resolved.branch,
            project_root.as_deref(),
            false,
            super::DependencySymbolQuery::List(&pattern),
            cancellation_token,
        )?;
        let mut report = result.index.list(&pattern);
        if let Some(dependency_index) = result.dependency_index {
            report = report.with_dependency_index(dependency_index);
        }
        super::append_project_jdk_diagnostics(&mut report.diagnostics, project_diagnostics);
        super::append_project_jdk_diagnostics(&mut report.diagnostics, result.project_diagnostics);
        let exit_code = if result.project_incomplete {
            5
        } else {
            report.status()
        };
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
    use crate::jar_build::hash::ContentHash;
    use crate::jar_build::hash::ContentHashAlgorithm;
    use crate::java_analysis::JavaAnalysisScenarioFixture;
    use crate::java_analysis::with_java_analysis_scenario_fixture;
    use crate::paths::CacheHome;
    use crate::source_projection::provenance::ProjectionProvenance;
    use std::io::Cursor;
    use std::io::Write as _;
    use std::path::PathBuf;
    use zip::ZipWriter;
    use zip::write::SimpleFileOptions;

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
    fn generated_project_selector_queries_use_pinned_cached_classfiles() {
        let (temporary, workspace) = generated_project_fixture();
        let project_root = temporary.path().join("generated-project");
        std::fs::write(
            project_root.join("src/main/java/example/Use.java"),
            "package example; import dep.External; public class Use { External value; }",
        )
        .unwrap();
        let cache_root = temporary.path().join("cache");
        let jar_path = cache_root.join("minecraft-toolchain/maven/dep/library.jar");
        std::fs::create_dir_all(jar_path.parent().unwrap()).unwrap();
        let mut archive = ZipWriter::new(Cursor::new(Vec::new()));
        archive
            .start_file("dep/External.class", SimpleFileOptions::default())
            .unwrap();
        archive
            .write_all(&[0xca, 0xfe, 0xba, 0xbe, 0, 0, 0, 61, 0, 1, 0, 1])
            .unwrap();
        let bytes = archive.finish().unwrap().into_inner();
        std::fs::write(&jar_path, &bytes).unwrap();
        let hash = ContentHash::from_bytes(&bytes, ContentHashAlgorithm::Blake3);
        std::fs::write(
            project_root.join("sfm-toolchain.lock.json"),
            format!(
                r#"{{"schema_version":2,"minecraft_version":"1.19.2","maven_cache_dir":"$sfm-cache/maven","allow_local_artifact_cache":false,"repositories":[],"dependencies":[],"artifacts":[{{"coordinate":"dep:library:1","source":"remote-maven","repository":null,"url":null,"cache_path":"$sfm-cache/maven/dep/library.jar","original_path":null,"source_relative_path":null,"source_git":null,"source_build":null,"hash":"{hash}","weak":null}}]}}"#
            ),
        )
        .unwrap();
        with_java_analysis_scenario_fixture(
            JavaAnalysisScenarioFixture {
                jdk_source_tree: temporary.path().join("unavailable-jdk"),
                cache_home: CacheHome(cache_root),
                decompiler_runtime_identity: "sfm:scenario_decompiler_runtime@1".to_owned(),
            },
            || {
                let definition = SymbolShowDefinitionArgs {
                    selector: vec!["dep.External".to_owned()],
                    source_path: None,
                    source_root_id: None,
                    line: None,
                    column: None,
                    workspace: workspace.clone(),
                }
                .invoke_in(&CancellationToken::new(), temporary.path())
                .unwrap();
                assert_eq!(definition.exit_code(), 0);
                let json = definition
                    .render(Some(OutputFormat::Json), false)
                    .unwrap()
                    .unwrap();
                assert!(json.contains("dep.External"));
                assert!(json.contains("External.class"));

                let usages = SymbolListUsagesArgs {
                    selector: vec!["dep.External".to_owned()],
                    source_path: None,
                    source_root_id: None,
                    line: None,
                    column: None,
                    workspace,
                }
                .invoke_in(&CancellationToken::new(), temporary.path())
                .unwrap();
                assert_eq!(usages.exit_code(), 0);
                let json = usages
                    .render(Some(OutputFormat::Json), false)
                    .unwrap()
                    .unwrap();
                assert!(json.contains("src/main/java/example/Use.java"));
                assert!(json.contains("dep.External"));
                assert!(!project_root.join(".gradle").exists());
            },
        );
    }

    #[test]
    fn generated_project_exact_member_selectors_use_pinned_cached_classfiles() {
        let (temporary, workspace) = generated_project_fixture();
        let project_root = temporary.path().join("generated-project");
        let cache_root = temporary.path().join("cache");
        let jar_path = cache_root.join("minecraft-toolchain/maven/dep/library.jar");
        std::fs::create_dir_all(jar_path.parent().unwrap()).unwrap();
        let mut archive = ZipWriter::new(Cursor::new(Vec::new()));
        archive
            .start_file("dep/External.class", SimpleFileOptions::default())
            .unwrap();
        archive.write_all(&member_classfile()).unwrap();
        let jar = archive.finish().unwrap().into_inner();
        std::fs::write(&jar_path, &jar).unwrap();
        let pin = |jar: &[u8]| {
            let hash = ContentHash::from_bytes(jar, ContentHashAlgorithm::Blake3);
            std::fs::write(
                project_root.join("sfm-toolchain.lock.json"),
                format!(
                    r#"{{"schema_version":2,"minecraft_version":"1.19.2","maven_cache_dir":"$sfm-cache/maven","allow_local_artifact_cache":false,"repositories":[],"dependencies":[],"artifacts":[{{"coordinate":"dep:library:1","source":"remote-maven","repository":null,"url":null,"cache_path":"$sfm-cache/maven/dep/library.jar","original_path":null,"source_relative_path":null,"source_git":null,"source_build":null,"hash":"{hash}","weak":null}}]}}"#
                ),
            )
            .unwrap();
        };
        pin(&jar);

        with_java_analysis_scenario_fixture(
            JavaAnalysisScenarioFixture {
                jdk_source_tree: temporary.path().join("unavailable-jdk"),
                cache_home: CacheHome(cache_root),
                decompiler_runtime_identity: "sfm:scenario_decompiler_runtime@1".to_owned(),
            },
            || {
                let definition = |member: &str| {
                    SymbolShowDefinitionArgs {
                        selector: vec!["dep.External".to_owned(), member.to_owned()],
                        source_path: None,
                        source_root_id: None,
                        line: None,
                        column: None,
                        workspace: workspace.clone(),
                    }
                    .invoke_in(&CancellationToken::new(), temporary.path())
                    .unwrap()
                };
                for member in ["VALUE", "get()I", "get(I)I"] {
                    let found = definition(member);
                    assert_eq!(found.exit_code(), 0, "{member}");
                    let json = found
                        .render(Some(OutputFormat::Json), false)
                        .unwrap()
                        .unwrap();
                    assert!(json.contains(&format!("dep.External.{member}")), "{json}");
                    assert!(json.contains("External.class"), "{json}");
                }
                let missing = definition("get(J)I");
                assert_eq!(missing.exit_code(), 2);
                let json = missing
                    .render(Some(OutputFormat::Json), false)
                    .unwrap()
                    .unwrap();
                assert!(!json.contains("dep.External.get()I"), "{json}");
                assert!(!json.contains("dep.External.get(I)I"), "{json}");

                std::fs::write(&jar_path, b"stale cached JAR").unwrap();
                let stale = definition("get()I");
                assert_eq!(stale.exit_code(), 5);
                let json = stale
                    .render(Some(OutputFormat::Json), false)
                    .unwrap()
                    .unwrap();
                assert!(json.contains("java.dependency-artifact-unavailable"));
                assert!(json.contains("checksum does not match"));

                let mut truncated_class = member_classfile();
                truncated_class.pop();
                let mut archive = ZipWriter::new(Cursor::new(Vec::new()));
                archive
                    .start_file("dep/External.class", SimpleFileOptions::default())
                    .unwrap();
                archive.write_all(&truncated_class).unwrap();
                let truncated_jar = archive.finish().unwrap().into_inner();
                std::fs::write(&jar_path, &truncated_jar).unwrap();
                pin(&truncated_jar);
                let malformed = definition("get()I");
                assert_eq!(malformed.exit_code(), 5);
                let json = malformed
                    .render(Some(OutputFormat::Json), false)
                    .unwrap()
                    .unwrap();
                assert!(json.contains("selected classfile is invalid"), "{json}");
            },
        );
    }

    fn member_classfile() -> Vec<u8> {
        let mut bytes = vec![0xca, 0xfe, 0xba, 0xbe, 0, 0, 0, 61];
        bytes.extend(10_u16.to_be_bytes());
        for entry in [
            "dep/External",
            "java/lang/Object",
            "VALUE",
            "I",
            "get",
            "()I",
            "(I)I",
        ] {
            bytes.push(1);
            bytes.extend(u16::try_from(entry.len()).unwrap().to_be_bytes());
            bytes.extend(entry.as_bytes());
            if entry == "dep/External" || entry == "java/lang/Object" {
                bytes.push(7);
                bytes.extend(
                    if entry == "dep/External" {
                        1_u16
                    } else {
                        3_u16
                    }
                    .to_be_bytes(),
                );
            }
        }
        // Public abstract class, this/super, zero interfaces.
        for word in [0x0421_u16, 2, 4, 0] {
            bytes.extend(word.to_be_bytes());
        }
        // One public static field and two abstract method overloads.
        for word in [
            1_u16, 0x0009, 5, 6, 0, 2, 0x0401, 7, 8, 0, 0x0401, 7, 9, 0, 0,
        ] {
            bytes.extend(word.to_be_bytes());
        }
        bytes
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
