//! Read-only discovery and one-file rendering through the core Liquid catalog.
//!
//! These commands never consult the legacy selection manifest or historical
//! source overlays. Rendering one authored text file is a template proof, not a complete
//! project generation, release-compatibility proof, or compilation operation.

use crate::cli::output::CliOutput;
use crate::source_projection::context::ProjectionContext;
use crate::source_projection::core_catalog::CoreCatalog as LoadedCatalog;
use crate::source_projection::core_catalog::read_bounded_catalog_input;
use crate::source_projection::core_features::FEATURE_DEFINITIONS_PATH;
use crate::source_projection::core_inputs::CORE_METADATA_PATH;
use crate::source_projection::core_inputs::CoreProjectInputs;
use crate::source_projection::core_inputs::select_core_inputs;
use crate::source_projection::projection_catalog::CATALOG_PATH;
use crate::source_projection::promotion::validate_relative_path;
use crate::source_projection::provenance::sha256;
use crate::source_projection::render_java_source;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
use std::path::PathBuf;

const CORE_ROOT: &str = "platform/minecraft/core-liquid-template";
const CATALOG_SCOPE: &str = "read_only_projection_catalog_inspection";
const RENDER_SCOPE: &str = "read_only_single_java_template_proof_not_project_generation_or_build";
const TEXT_RENDER_SCOPE: &str =
    "read_only_selected_text_template_proof_not_project_generation_or_build";

#[derive(Debug, Facet)]
pub struct SourceListArgs {
    /// Repository root containing projections.json and the core Liquid tree.
    #[facet(args::named)]
    pub repo_root: PathBuf,
}

#[derive(Debug, Facet)]
pub struct SourceShowArgs {
    /// Repository root containing projections.json and the core Liquid tree.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Exact nested projection key, such as sfm-dev/mc-1.19.2.
    #[facet(args::named)]
    pub projection: String,
}

#[derive(Debug, Facet)]
pub struct SourceRenderArgs {
    /// Repository root containing projections.json and the core Liquid tree.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Exact nested projection key, such as sfm-dev/mc-1.19.2.
    #[facet(args::named)]
    pub projection: String,
    /// Core-relative Java source or explicitly selected non-Java text template.
    #[facet(args::named)]
    pub file: String,
}

#[derive(Debug, Facet)]
struct CatalogInputsReport {
    catalog_path: String,
    catalog_sha256: String,
    feature_definitions_path: String,
    feature_definitions_sha256: String,
}

#[derive(Debug, Facet)]
struct ProjectionSummary {
    projection_key: String,
    target_id: String,
    minecraft_version: String,
    environment: String,
    enabled_features: Vec<String>,
    feature_flags: BTreeMap<String, bool>,
    context_identity: String,
    project_dir: String,
}

#[derive(Debug, Facet)]
struct SourceListReport {
    schema: String,
    scope: String,
    inputs: CatalogInputsReport,
    projections: Vec<ProjectionSummary>,
}

#[derive(Debug, Facet)]
struct SourceShowReport {
    schema: String,
    scope: String,
    inputs: CatalogInputsReport,
    projection: ProjectionSummary,
    template_context: ProjectionContext,
}

#[derive(Debug, Facet)]
struct SourceRenderReport {
    schema: String,
    scope: String,
    inputs: CatalogInputsReport,
    projection: ProjectionSummary,
    template_context: ProjectionContext,
    core_relative_file: String,
    source_path: String,
    source_sha256: String,
    rendered_sha256: String,
    rendered_content: String,
    project_inputs_sha256: Option<String>,
    writes_performed: bool,
    full_project_generated: bool,
    compiled: bool,
}

impl SourceListArgs {
    /// List validated contexts without creating or updating any projection.
    ///
    /// # Errors
    ///
    /// Rejects unsafe roots, oversized/malformed inputs, invalid feature
    /// definitions, or any invalid entry anywhere in the catalog.
    pub(super) fn invoke_in(self, invocation_dir: &Path) -> Result<CliOutput> {
        Ok(CliOutput::facet(list_report(&self, invocation_dir)?))
    }
}

impl SourceShowArgs {
    /// Show the resolved context of an exact projection, without writing.
    ///
    /// # Errors
    ///
    /// Rejects invalid catalog/registry inputs, unsafe roots, and unknown keys.
    pub(super) fn invoke_in(self, invocation_dir: &Path) -> Result<CliOutput> {
        Ok(CliOutput::facet(show_report(&self, invocation_dir)?))
    }
}

impl SourceRenderArgs {
    /// Render only the requested authored text file into a typed stdout report.
    /// No output directory, provenance manifest, Gradle task, or JAR is written.
    ///
    /// # Errors
    ///
    /// Non-Java inputs must be selected and explicitly marked as templates in
    /// core project metadata; disabled or opaque assets are not previewed.
    /// Rejects invalid inputs/keys, unsafe or missing files, oversized
    /// source bytes, malformed directives, and unknown context references.
    pub(super) fn invoke_in(self, invocation_dir: &Path) -> Result<CliOutput> {
        Ok(CliOutput::facet(render_report(&self, invocation_dir)?))
    }
}

fn list_report(args: &SourceListArgs, invocation_dir: &Path) -> Result<SourceListReport> {
    let loaded = load_catalog(&args.repo_root, invocation_dir)?;
    let projections = loaded
        .catalog
        .0
        .keys()
        .map(|key| loaded.summary(key))
        .collect::<Result<_>>()?;
    Ok(SourceListReport {
        schema: "sfm:source_projection_list@1".to_owned(),
        scope: CATALOG_SCOPE.to_owned(),
        inputs: loaded.inputs_report(),
        projections,
    })
}

fn show_report(args: &SourceShowArgs, invocation_dir: &Path) -> Result<SourceShowReport> {
    let loaded = load_catalog(&args.repo_root, invocation_dir)?;
    Ok(SourceShowReport {
        schema: "sfm:source_projection_show@1".to_owned(),
        scope: CATALOG_SCOPE.to_owned(),
        inputs: loaded.inputs_report(),
        projection: loaded.summary(&args.projection)?,
        template_context: loaded.context(&args.projection)?,
    })
}

fn render_report(args: &SourceRenderArgs, invocation_dir: &Path) -> Result<SourceRenderReport> {
    validate_relative_path(&args.file)?;
    ensure!(
        args.file.starts_with("src/"),
        "source render accepts only core-relative src/... files"
    );
    let loaded = load_catalog(&args.repo_root, invocation_dir)?;
    let context = loaded.context(&args.projection)?;
    let java = Path::new(&args.file)
        .extension()
        .and_then(|value| value.to_str())
        == Some("java");
    let project_inputs_sha256 = if java {
        None
    } else {
        let bytes = read_bounded_catalog_input(&loaded.repo_root, CORE_METADATA_PATH)?;
        let metadata = CoreProjectInputs::from_json(
            std::str::from_utf8(&bytes).wrap_err("core project metadata is not UTF-8")?,
            &loaded.registered_features,
        )?;
        // Explicit non-Java rules suffice: no source inventory or asset body
        // is traversed/read to preview this single selected text template.
        let selected = select_core_inputs(&metadata, &context, &BTreeSet::default())?;
        ensure!(
            selected
                .inputs
                .values()
                .any(|input| input.input == args.file && input.template),
            "non-Java source preview requires a selected explicit text-template rule"
        );
        Some(sha256(&bytes))
    };
    let source_path = format!("{CORE_ROOT}/{}", args.file);
    let source = read_bounded_catalog_input(&loaded.repo_root, &source_path)?;
    let source_text = std::str::from_utf8(&source).wrap_err("core source template is not UTF-8")?;
    let rendered = render_java_source(source_text, &context)
        .wrap_err_with(|| format!("could not render core source template `{}`", args.file))?;
    Ok(SourceRenderReport {
        schema: "sfm:source_projection_render@1".to_owned(),
        scope: if java {
            RENDER_SCOPE
        } else {
            TEXT_RENDER_SCOPE
        }
        .to_owned(),
        inputs: loaded.inputs_report(),
        projection: loaded.summary(&args.projection)?,
        template_context: context,
        core_relative_file: args.file.clone(),
        source_path,
        source_sha256: sha256(&source),
        rendered_sha256: sha256(rendered.as_bytes()),
        rendered_content: rendered,
        project_inputs_sha256,
        writes_performed: false,
        full_project_generated: false,
        compiled: false,
    })
}

impl LoadedCatalog {
    fn inputs_report(&self) -> CatalogInputsReport {
        CatalogInputsReport {
            catalog_path: CATALOG_PATH.to_owned(),
            catalog_sha256: self.catalog_sha256.clone(),
            feature_definitions_path: FEATURE_DEFINITIONS_PATH.to_owned(),
            feature_definitions_sha256: self.feature_definitions_sha256.clone(),
        }
    }

    fn summary(&self, key: &str) -> Result<ProjectionSummary> {
        let entry = self.catalog.entry(key)?;
        let mut enabled_features = entry.features.clone();
        enabled_features.sort();
        Ok(ProjectionSummary {
            projection_key: key.to_owned(),
            target_id: entry.target_id()?.to_owned(),
            minecraft_version: entry.minecraft_version.clone(),
            environment: entry.environment.as_str().to_owned(),
            enabled_features,
            feature_flags: entry.feature_flags(&self.registered_features)?,
            context_identity: self.catalog.context_identity(key)?,
            project_dir: self
                .catalog
                .project_dir(key)?
                .to_string_lossy()
                .replace('\\', "/"),
        })
    }
}

fn load_catalog(repo_root: &Path, invocation_dir: &Path) -> Result<LoadedCatalog> {
    LoadedCatalog::load(repo_root, invocation_dir)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::output::OutputFormat;
    use crate::source_projection::core_catalog::MAX_CATALOG_INPUT_BYTES as MAX_INPUT_BYTES;
    use std::fs;

    const JAVA_PATH: &str = "src/main/java/example/Example.java";
    const ITEM_PATH: &str =
        "src/main/java/ca/teamdman/sfm/common/resourcetype/ItemResourceType.java";
    const REGISTRY: &str = r#"{"alpha":{"supported_targets":["1.19.2","1.20.1"],"requires":[]},"beta":{"supported_targets":["1.19.2"],"requires":["alpha"]}}"#;
    const CATALOG: &str = r#"{"release/example":{"minecraft_version":"1.19.2","environment":"release","features":[]},"dev/example":{"minecraft_version":"1.19.2","environment":"dev","features":["alpha","beta"]},"dev/old-package-new-loader":{"minecraft_version":"1.20.1","environment":"dev","features":["alpha"]}}"#;
    const TEMPLATE: &str = "{% case minecraft_version %}\n{% when \"1.19.2\" %}\nimport old.Handler;\n{% else %}\nimport new.Handler;\n{% endcase %}\n{% if features.beta %}\nclass Example { String text = \"{{ untouched }}\"; }\n{% else %}\nclass Example {}\n{% endif %}\n";

    struct Fixture {
        temp: tempfile::TempDir,
        repo: PathBuf,
    }

    impl Fixture {
        fn new() -> Self {
            let temp = tempfile::tempdir().unwrap();
            let repo = temp.path().join("repository");
            let fixture = Self { temp, repo };
            fixture.write(CATALOG_PATH, CATALOG.as_bytes());
            fixture.write(FEATURE_DEFINITIONS_PATH, REGISTRY.as_bytes());
            fixture.write(&format!("{CORE_ROOT}/{JAVA_PATH}"), TEMPLATE.as_bytes());
            // This deliberately invalid legacy file must never be opened by
            // list/show/render; a snapshot is not a template input.
            fixture.write("platform/minecraft/source-projection.json", b"not JSON");
            fixture
        }

        fn write(&self, relative: &str, bytes: &[u8]) {
            let path = self.repo.join(relative);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(path, bytes).unwrap();
        }

        fn render_args(&self, projection: &str) -> SourceRenderArgs {
            SourceRenderArgs {
                repo_root: self.repo.clone(),
                projection: projection.to_owned(),
                file: JAVA_PATH.to_owned(),
            }
        }
    }

    #[test]
    fn list_and_show_resolve_explicit_flags_without_reading_legacy_inputs() {
        let fixture = Fixture::new();
        let before = fs::read(fixture.repo.join(CATALOG_PATH)).unwrap();
        let list = list_report(
            &SourceListArgs {
                repo_root: fixture.repo.clone(),
            },
            fixture.temp.path(),
        )
        .unwrap();
        assert_eq!(list.scope, CATALOG_SCOPE);
        assert_eq!(list.projections.len(), 3);
        assert_eq!(list.inputs.catalog_sha256, sha256(&before));
        let show = show_report(
            &SourceShowArgs {
                repo_root: fixture.repo.clone(),
                projection: "release/example".to_owned(),
            },
            fixture.temp.path(),
        )
        .unwrap();
        assert_eq!(
            show.projection.project_dir,
            "platform/minecraft/projections/release/example"
        );
        assert_eq!(show.template_context.features["alpha"], false);
        assert_eq!(show.template_context.features["beta"], false);
        assert_eq!(show.template_context.projection_key, "release/example");
        assert_eq!(show.template_context.preset, "release/example");
        assert_eq!(show.template_context.environment, "release");
        assert_eq!(fs::read(fixture.repo.join(CATALOG_PATH)).unwrap(), before);
        assert!(!fixture.repo.join("platform/minecraft/projections").exists());
    }

    #[test]
    fn read_only_render_proves_only_one_file_and_preserves_opaque_java() {
        let fixture = Fixture::new();
        let before = fs::read(fixture.repo.join(format!("{CORE_ROOT}/{JAVA_PATH}"))).unwrap();
        let release =
            render_report(&fixture.render_args("release/example"), fixture.temp.path()).unwrap();
        assert_eq!(
            release.rendered_content,
            "import old.Handler;\nclass Example {}\n"
        );
        assert_eq!(release.scope, RENDER_SCOPE);
        assert!(!release.writes_performed && !release.full_project_generated && !release.compiled);
        let dev = render_report(&fixture.render_args("dev/example"), fixture.temp.path()).unwrap();
        assert_eq!(
            dev.rendered_content,
            "import old.Handler;\nclass Example { String text = \"{{ untouched }}\"; }\n"
        );
        assert_ne!(release.rendered_sha256, dev.rendered_sha256);
        assert_eq!(release.source_sha256, sha256(&before));
        assert_eq!(
            fs::read(fixture.repo.join(format!("{CORE_ROOT}/{JAVA_PATH}"))).unwrap(),
            before
        );
        assert!(!fixture.repo.join("platform/minecraft/projections").exists());
        let output = fixture
            .render_args("dev/example")
            .invoke_in(fixture.temp.path())
            .unwrap()
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        let output_report: SourceRenderReport = facet_json::from_str(&output).unwrap();
        assert!(!output_report.compiled);
        assert_eq!(output_report.schema, "sfm:source_projection_render@1");
    }

    #[test]
    fn minecraft_1_21_and_neoforge_1_20_1_have_exact_contexts() {
        let fixture = Fixture::new();
        let loaded = load_catalog(&fixture.repo, fixture.temp.path()).unwrap();
        let context = loaded.context("dev/old-package-new-loader").unwrap();
        assert_eq!(context.minecraft_version, "1.20.1");
        assert!(context.targets["mc_1_20_1"]);
        assert!(context.targets["neoforge"]);
        assert!(!context.targets["forge"]);
        assert_eq!(
            context.targets.values().filter(|enabled| **enabled).count(),
            2
        );
        let rewritten = CATALOG
            .replace("1.20.1", "1.21")
            .replace("\"features\":[\"alpha\"]", "\"features\":[]");
        fixture.write(CATALOG_PATH, rewritten.as_bytes());
        let loaded = load_catalog(&fixture.repo, fixture.temp.path()).unwrap();
        let context = loaded.context("dev/old-package-new-loader").unwrap();
        assert_eq!(context.minecraft_version, "1.21");
        assert!(context.targets["mc_1_21_0"]);
    }

    fn write_text_rule(fixture: &Fixture, template: bool, features: &[&str]) -> String {
        use crate::source_projection::core_inputs::BuildTargetMetadata;
        use crate::source_projection::core_inputs::InputPredicate;
        use crate::source_projection::core_inputs::InputVariant;
        let metadata = CoreProjectInputs {
            schema_version: 1,
            targets: BTreeMap::from([
                (
                    "1.19.2".to_owned(),
                    BuildTargetMetadata {
                        java_major: 17,
                        loader: "forge".to_owned(),
                    },
                ),
                (
                    "1.20.1".to_owned(),
                    BuildTargetMetadata {
                        java_major: 17,
                        loader: "neoforge".to_owned(),
                    },
                ),
            ]),
            source_rules: BTreeMap::from([(
                "src/main/antlr/proof/Proof.g4".to_owned(),
                vec![InputVariant {
                    input: "src/main/antlr/proof/Proof.g4".to_owned(),
                    when: InputPredicate {
                        all_features: features.iter().map(|name| (*name).to_owned()).collect(),
                        ..Default::default()
                    },
                    template,
                }],
            )]),
            project_files: BTreeMap::new(),
        };
        let json = facet_json::to_string(&metadata).unwrap();
        fixture.write(CORE_METADATA_PATH, json.as_bytes());
        json
    }

    #[test]
    fn non_java_preview_uses_explicit_selected_template_without_writes() {
        let fixture = Fixture::new();
        let json = write_text_rule(&fixture, true, &["beta"]);
        let path = "src/main/antlr/proof/Proof.g4";
        fixture.write(&format!("{CORE_ROOT}/{path}"), TEMPLATE.as_bytes());
        let mut args = fixture.render_args("dev/example");
        args.file = path.to_owned();
        let report = render_report(&args, fixture.temp.path()).unwrap();
        assert_eq!(report.scope, TEXT_RENDER_SCOPE);
        assert_eq!(report.project_inputs_sha256, Some(sha256(json.as_bytes())));
        assert_eq!(
            report.rendered_content,
            "import old.Handler;\nclass Example { String text = \"{{ untouched }}\"; }\n"
        );
        assert!(!report.writes_performed && !report.compiled && !report.full_project_generated);
        assert!(!fixture.repo.join("platform/minecraft/projections").exists());
        assert_eq!(
            fs::read(fixture.repo.join(CORE_METADATA_PATH)).unwrap(),
            json.as_bytes()
        );
    }

    #[test]
    fn non_java_preview_refuses_inactive_and_opaque_inputs_before_reading() {
        let fixture = Fixture::new();
        write_text_rule(&fixture, true, &["beta"]);
        let mut args = fixture.render_args("release/example");
        args.file = "src/main/antlr/proof/Proof.g4".to_owned();
        // No body exists: refusal must occur at selection, not file access.
        let error = render_report(&args, fixture.temp.path())
            .unwrap_err()
            .to_string();
        assert!(
            error.contains("selected explicit text-template rule"),
            "{error}"
        );
        write_text_rule(&fixture, false, &[]);
        let error = render_report(&args, fixture.temp.path())
            .unwrap_err()
            .to_string();
        assert!(
            error.contains("selected explicit text-template rule"),
            "{error}"
        );
        args.file = "src/main/resources/opaque.png".to_owned();
        fixture.write(&format!("{CORE_ROOT}/{}", args.file), b"\xff");
        assert!(
            render_report(&args, fixture.temp.path())
                .unwrap_err()
                .to_string()
                .contains("selected explicit text-template rule")
        );
    }

    #[test]
    fn text_preview_retains_metadata_and_utf8_bounds() {
        let fixture = Fixture::new();
        write_text_rule(&fixture, true, &[]);
        let mut args = fixture.render_args("dev/example");
        args.file = "src/main/antlr/proof/Proof.g4".to_owned();
        fixture.write(&format!("{CORE_ROOT}/{}", args.file), b"\xff");
        assert!(
            render_report(&args, fixture.temp.path())
                .unwrap_err()
                .to_string()
                .contains("not UTF-8")
        );
        fixture.write(CORE_METADATA_PATH, b"not JSON");
        assert!(render_report(&args, fixture.temp.path()).is_err());
        fixture.write(
            CORE_METADATA_PATH,
            &vec![b' '; usize::try_from(MAX_INPUT_BYTES + 1).unwrap()],
        );
        assert!(
            render_report(&args, fixture.temp.path())
                .unwrap_err()
                .to_string()
                .contains("exceeds")
        );
    }

    #[test]
    fn repository_dot_resolves_against_invocation_directory() {
        let fixture = Fixture::new();
        let report = list_report(
            &SourceListArgs {
                repo_root: PathBuf::from("."),
            },
            &fixture.repo,
        )
        .unwrap();
        assert_eq!(report.projections.len(), 3);
        let report = list_report(
            &SourceListArgs {
                repo_root: PathBuf::from("repository"),
            },
            fixture.temp.path(),
        )
        .unwrap();
        assert_eq!(report.projections.len(), 3);
    }

    #[test]
    fn malformed_unknown_and_duplicate_metadata_fail_closed() {
        let fixture = Fixture::new();
        for bytes in [b"not JSON".as_slice(), b"\xff".as_slice()] {
            fixture.write(CATALOG_PATH, bytes);
            assert!(load_catalog(&fixture.repo, fixture.temp.path()).is_err());
        }
        fixture.write(CATALOG_PATH, CATALOG.as_bytes());
        for invalid in [
            "not JSON".to_owned(),
            REGISTRY.replace("\"requires\":[]", "\"requires\":[],\"unexpected\":true"),
            REGISTRY.replace(
                "\"alpha\":",
                "\"alpha\":{\"supported_targets\":[\"1.19.2\"],\"requires\":[]},\"alpha\":",
            ),
            REGISTRY.replace("\"alpha\":", "\"Alpha\":"),
        ] {
            fixture.write(FEATURE_DEFINITIONS_PATH, invalid.as_bytes());
            assert!(
                load_catalog(&fixture.repo, fixture.temp.path()).is_err(),
                "accepted {invalid}"
            );
        }
    }

    #[test]
    fn unknown_unsupported_and_missing_prerequisite_flags_fail_every_command() {
        let fixture = Fixture::new();
        for invalid in [
            CATALOG.replace("\"alpha\",\"beta\"", "\"typo\""),
            CATALOG.replace("\"alpha\",\"beta\"", "\"beta\""),
            CATALOG.replace(
                "\"features\":[\"alpha\"]",
                "\"features\":[\"alpha\",\"beta\"]",
            ),
        ] {
            fixture.write(CATALOG_PATH, invalid.as_bytes());
            assert!(
                load_catalog(&fixture.repo, fixture.temp.path()).is_err(),
                "accepted {invalid}"
            );
            assert!(
                render_report(&fixture.render_args("release/example"), fixture.temp.path())
                    .is_err()
            );
        }
    }

    #[test]
    fn feature_registry_rejects_unknown_targets_dependencies_repetitions_and_cycles() {
        let fixture = Fixture::new();
        for invalid in [
            REGISTRY.replace("\"1.20.1\"", "\"unsupported\""),
            REGISTRY.replace("\"requires\":[\"alpha\"]", "\"requires\":[\"missing\"]"),
            REGISTRY.replace(
                "\"requires\":[\"alpha\"]",
                "\"requires\":[\"alpha\",\"alpha\"]",
            ),
            REGISTRY.replace("\"1.19.2\",\"1.20.1\"", "\"1.19.2\",\"1.19.2\""),
            REGISTRY.replace("\"requires\":[\"alpha\"]", "\"requires\":[\"beta\"]"),
            REGISTRY.replace(
                "\"supported_targets\":[\"1.19.2\",\"1.20.1\"]",
                "\"supported_targets\":[]",
            ),
            REGISTRY.replace(
                "\"supported_targets\":[\"1.19.2\"],\"requires\":[\"alpha\"]",
                "\"supported_targets\":[\"26.1.2\"],\"requires\":[\"alpha\"]",
            ),
        ] {
            fixture.write(FEATURE_DEFINITIONS_PATH, invalid.as_bytes());
            assert!(
                load_catalog(&fixture.repo, fixture.temp.path()).is_err(),
                "accepted {invalid}"
            );
        }
    }

    #[test]
    fn unsafe_missing_non_java_and_unknown_projection_requests_fail() {
        let fixture = Fixture::new();
        for file in [
            "../escape.java",
            "src/../../escape.java",
            "/absolute.java",
            "src\\Example.java",
            "src/main/java/CON.java",
            "src/missing.java",
            "build.gradle",
            "src/main/java/Example.JAVA",
        ] {
            let mut args = fixture.render_args("dev/example");
            args.file = file.to_owned();
            assert!(
                render_report(&args, fixture.temp.path()).is_err(),
                "accepted {file}"
            );
        }
        assert!(render_report(&fixture.render_args("missing/key"), fixture.temp.path()).is_err());
        assert!(load_catalog(&fixture.repo.join(".."), fixture.temp.path()).is_err());
        fixture.write(
            &format!("{CORE_ROOT}/{JAVA_PATH}"),
            b"{% if features.not_registered %}\nclass Bad {}\n{% endif %}\n",
        );
        assert!(render_report(&fixture.render_args("dev/example"), fixture.temp.path()).is_err());
    }

    #[test]
    fn catalog_registry_and_source_reads_are_bounded() {
        let fixture = Fixture::new();
        let oversized = vec![b' '; usize::try_from(MAX_INPUT_BYTES + 1).unwrap()];
        for path in [CATALOG_PATH, FEATURE_DEFINITIONS_PATH] {
            let old = fs::read(fixture.repo.join(path)).unwrap();
            fixture.write(path, &oversized);
            assert!(load_catalog(&fixture.repo, fixture.temp.path()).is_err());
            fixture.write(path, &old);
        }
        fixture.write(&format!("{CORE_ROOT}/{JAVA_PATH}"), &oversized);
        assert!(render_report(&fixture.render_args("dev/example"), fixture.temp.path()).is_err());
    }

    #[cfg(unix)]
    #[test]
    fn rejects_symlinked_catalog_registry_and_core_source() {
        use std::os::unix::fs::symlink;
        let fixture = Fixture::new();
        let external = fixture.temp.path().join("external");
        fs::write(&external, TEMPLATE).unwrap();
        for path in [
            CATALOG_PATH,
            FEATURE_DEFINITIONS_PATH,
            &format!("{CORE_ROOT}/{JAVA_PATH}"),
        ] {
            let local = fixture.repo.join(path);
            let old = fs::read(&local).unwrap();
            fs::remove_file(&local).unwrap();
            symlink(&external, &local).unwrap();
            assert!(
                render_report(&fixture.render_args("dev/example"), fixture.temp.path()).is_err()
            );
            fs::remove_file(&local).unwrap();
            fs::write(local, old).unwrap();
        }
    }

    #[test]
    fn real_core_item_resource_type_renders_twenty_contexts_against_release_witnesses() {
        let repo = Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .unwrap();
        let loaded = load_catalog(repo, repo).unwrap();
        assert_eq!(
            loaded.catalog.0.len(),
            20,
            "initial catalog must cover both contexts for all ten versions"
        );
        let mut targets = BTreeMap::<String, BTreeSet<String>>::new();
        for (key, entry) in &loaded.catalog.0 {
            let report = render_report(
                &SourceRenderArgs {
                    repo_root: repo.to_path_buf(),
                    projection: key.clone(),
                    file: ITEM_PATH.to_owned(),
                },
                repo,
            )
            .unwrap();
            let witness_path = format!(
                "platform/minecraft/mc-version/{}/{ITEM_PATH}",
                entry.target_id().unwrap()
            );
            let witness = String::from_utf8(
                crate::source_projection::legacy_test_fixture::read(repo, &witness_path).unwrap(),
            )
            .unwrap();
            // Keep historical witnesses immutable; allow only the reviewed
            // 26.1.2 signature layout change, not arbitrary normalization.
            let mut expected = normalize_source(&witness);
            if entry.minecraft_version == "26.1.2" {
                let signature = "public boolean isValid(int index, ItemResource resource) {";
                assert_eq!(expected.matches(signature).count(), 1);
                expected = expected.replacen(
                    signature,
                    "public boolean isValid(\n                    int index,\n                    ItemResource resource\n            ) {",
                    1,
                );
            }
            assert_eq!(
                normalize_source(&report.rendered_content),
                expected,
                "core template differs from test-only release witness for `{key}`"
            );
            assert!(report.source_path.starts_with(CORE_ROOT));
            targets
                .entry(entry.target_id().unwrap().to_owned())
                .or_default()
                .insert(entry.environment.as_str().to_owned());
        }
        assert_eq!(targets.len(), 10);
        for (target, environments) in targets {
            assert_eq!(
                environments,
                BTreeSet::from(["dev".to_owned(), "release".to_owned()]),
                "missing environment for {target}"
            );
        }
    }

    fn normalize_source(source: &str) -> String {
        let normalized = source.replace("\r\n", "\n");
        normalized.strip_prefix("// GENERATED by sfm-propagate-changes; edit the primary source or reconcile this file.\n").unwrap_or(&normalized).to_owned()
    }
}
