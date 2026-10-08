//! Read-only current-template lookup for one generated project file.

use crate::cli::output::CliOutput;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::source_projection::manifestation::render_output;
use crate::source_projection::promotion::validate_relative_path;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use facet::Facet;
use figue::{self as args};
use std::path::PathBuf;

#[derive(Debug, Facet)]
pub struct SourceTraceArgs {
    /// Generated Minecraft project root registered in its checkout's catalog.
    #[facet(args::named)]
    pub project_root: PathBuf,
    /// Project-relative generated file, such as src/main/java/Example.java.
    #[facet(args::named)]
    pub file: String,
}

#[derive(Debug, Facet)]
struct SourceTraceReport {
    schema: String,
    projection_key: String,
    minecraft_version: String,
    generated_file: String,
    source_path: String,
    source_sha256: String,
    rendered_sha256: String,
    current_sha256: Option<String>,
    matches_current_template: bool,
}

impl SourceTraceArgs {
    /// Render and compare one output without writing it or recording history.
    ///
    /// # Errors
    /// Rejects unsafe paths, invalid configuration and unselected outputs.
    pub(super) fn invoke_in(self) -> Result<CliOutput> {
        Ok(CliOutput::facet(trace(&self)?))
    }
}

fn trace(args: &SourceTraceArgs) -> Result<SourceTraceReport> {
    validate_relative_path(&args.file)?;
    let root = checked_directory(&args.project_root)?;
    let (loaded, key) = CoreCatalog::for_project(&root)?;
    let artifact = render_output(&loaded, &key, &args.file)?;
    let current = crate::source_projection::manifestation::read_output(&root, &args.file)?;
    Ok(SourceTraceReport {
        schema: "sfm:source_projection_trace@3".to_owned(),
        minecraft_version: loaded.catalog.entry(&key)?.minecraft_version.clone(),
        projection_key: key,
        generated_file: args.file.clone(),
        source_path: artifact.source_path,
        source_sha256: sha256(&artifact.source_bytes),
        rendered_sha256: sha256(&artifact.output_bytes),
        matches_current_template: current.as_deref() == Some(artifact.output_bytes.as_slice()),
        current_sha256: current.as_deref().map(sha256),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::core_catalog::write_project_catalog_fixture;
    use crate::source_projection::core_inputs::CORE_METADATA_PATH;
    use crate::source_projection::core_inputs::CORE_ROOT;
    use std::fs;

    fn fixture() -> (tempfile::TempDir, SourceTraceArgs) {
        let temp = tempfile::tempdir().unwrap();
        let root = write_project_catalog_fixture(temp.path(), "custom/nested", "1.19.2").unwrap();
        fs::write(
            temp.path().join(CORE_METADATA_PATH),
            r#"{
            "schema_version":1,
            "targets":{"1.19.2":{"java_major":17,"loader":"forge"}},
            "source_rules":{},"project_files":{}
        }"#,
        )
        .unwrap();
        let mut metadata = fs::read(temp.path().join(CORE_METADATA_PATH)).unwrap();
        metadata.resize(1024 * 1024 + 1, b' ');
        fs::write(temp.path().join(CORE_METADATA_PATH), metadata).unwrap();
        let source = temp
            .path()
            .join(CORE_ROOT)
            .join("src/main/java/Example.java");
        fs::create_dir_all(source.parent().unwrap()).unwrap();
        fs::write(source, "class Example {}\n").unwrap();
        let args = SourceTraceArgs {
            project_root: root,
            file: "src/main/java/Example.java".to_owned(),
        };
        (temp, args)
    }

    #[test]
    fn compares_fresh_render_and_reports_missing_or_edited_output_without_writing() {
        let (temp, args) = fixture();
        let missing = trace(&args).unwrap();
        assert_eq!(missing.projection_key, "custom/nested");
        assert!(!missing.matches_current_template);
        assert!(missing.current_sha256.is_none());
        let (catalog, key) = CoreCatalog::for_project(&args.project_root).unwrap();
        let artifact = render_output(&catalog, &key, &args.file).unwrap();
        let output = args.project_root.join(&args.file);
        fs::create_dir_all(output.parent().unwrap()).unwrap();
        fs::write(&output, &artifact.output_bytes).unwrap();
        assert!(trace(&args).unwrap().matches_current_template);
        fs::write(&output, "contributor edit\n").unwrap();
        assert!(!trace(&args).unwrap().matches_current_template);
        assert_eq!(fs::read_to_string(&output).unwrap(), "contributor edit\n");
        fs::write(
            temp.path().join(CORE_ROOT).join(&args.file),
            "class Changed {}\n",
        )
        .unwrap();
        let changed = trace(&args).unwrap();
        assert_ne!(changed.rendered_sha256, missing.rendered_sha256);
        assert!(
            !args
                .project_root
                .join(".sfm-source-projection-manifest.json")
                .exists()
        );
    }

    #[test]
    fn rejects_unselected_and_unsafe_outputs() {
        let (_temp, mut args) = fixture();
        args.file = "src/main/java/Other.java".to_owned();
        assert!(
            trace(&args)
                .unwrap_err()
                .to_string()
                .contains("not selected")
        );
        args.file = "../outside.java".to_owned();
        assert!(trace(&args).is_err());
    }
}
