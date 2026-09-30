//! Read-only ownership and edit-state lookup for one generated project file.

use crate::cli::output::CliOutput;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::promotion::validate_relative_path;
use crate::source_projection::provenance::CatalogProjectionOwner;
use crate::source_projection::provenance::ProjectionProvenance;
use crate::source_projection::provenance::sha256;
use crate::source_projection::sync::MANIFEST_FILE;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::fs;
use std::path::PathBuf;

const TRACE_SCHEMA: &str = "sfm:source_projection_trace@1";
const MAX_MANIFEST_BYTES: u64 = 16 * 1024 * 1024;

#[derive(Debug, Facet)]
pub struct SourceTraceArgs {
    /// Absolute generated Minecraft project root containing provenance.
    #[facet(args::named)]
    pub project_root: PathBuf,
    /// Project-relative generated file, such as src/main/java/Example.java.
    #[facet(args::named)]
    pub file: String,
}

#[derive(Debug, Facet)]
struct SourceTraceReport {
    schema: String,
    target_id: String,
    #[facet(default, skip_serializing_if = Option::is_none)]
    catalog: Option<CatalogProjectionOwner>,
    preset_id: String,
    preset_definition_identity: String,
    generated_file: String,
    source_path: String,
    overlay: Option<String>,
    source_sha256: String,
    generated_sha256: String,
    current_sha256: String,
    generated_file_matches_manifest: bool,
    provenance_manifest_sha256: String,
}

impl SourceTraceArgs {
    /// Inspect one owned generated file without changing the project or its manifest.
    ///
    /// # Errors
    ///
    /// Rejects an unsafe root or file, absent or malformed provenance, an
    /// unowned file, a missing file, or a reparse-point path.
    pub(super) fn invoke_in(self) -> Result<CliOutput> {
        Ok(CliOutput::facet(trace(&self)?))
    }
}

fn trace(args: &SourceTraceArgs) -> Result<SourceTraceReport> {
    validate_relative_path(&args.file)?;
    let root = checked_directory(&args.project_root)?;
    let manifest_path = checked_file(&root, MANIFEST_FILE)?;
    let metadata = fs::metadata(&manifest_path)?;
    ensure!(
        metadata.len() <= MAX_MANIFEST_BYTES,
        "source projection manifest exceeds the {MAX_MANIFEST_BYTES}-byte limit"
    );
    let manifest_bytes = fs::read(&manifest_path).wrap_err("cannot read source provenance")?;
    ensure!(
        manifest_bytes.len() as u64 <= MAX_MANIFEST_BYTES,
        "source projection manifest exceeds the {MAX_MANIFEST_BYTES}-byte limit"
    );
    let manifest = ProjectionProvenance::from_json(std::str::from_utf8(&manifest_bytes)?)?;
    let file = manifest
        .files
        .get(&args.file)
        .ok_or_else(|| eyre::eyre!("generated file is not owned by this source projection"))?;
    let output_path = checked_file(&root, &args.file)?;
    let current_sha256 = sha256(&fs::read(output_path).wrap_err("cannot read generated file")?);
    Ok(SourceTraceReport {
        schema: if manifest.catalog.is_some() {
            "sfm:source_projection_trace@2".to_owned()
        } else {
            TRACE_SCHEMA.to_owned()
        },
        target_id: manifest.target_id,
        catalog: manifest.catalog,
        preset_id: manifest.preset_id,
        preset_definition_identity: manifest.preset_definition_identity,
        generated_file: args.file.clone(),
        source_path: file.source_path.clone(),
        overlay: file.overlay.clone(),
        source_sha256: file.source_sha256.clone(),
        generated_sha256: file.output_sha256.clone(),
        generated_file_matches_manifest: current_sha256 == file.output_sha256,
        current_sha256,
        provenance_manifest_sha256: sha256(&manifest_bytes),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::provenance::ProjectedFileProvenance;

    fn fixture() -> (tempfile::TempDir, SourceTraceArgs) {
        let temp = tempfile::tempdir().unwrap();
        let project = temp.path().join("project");
        let output = project.join("src/main/java/Example.java");
        fs::create_dir_all(output.parent().unwrap()).unwrap();
        fs::write(&output, b"class Example {}\n").unwrap();
        let mut provenance =
            ProjectionProvenance::new("1.19.2", "1.19.2", "released-4.34.0", "blake3:example");
        provenance.files.insert(
            "src/main/java/Example.java".to_owned(),
            ProjectedFileProvenance {
                source_path: "src/main/java/Example.java".to_owned(),
                source_sha256: sha256(b"class Example {}\n"),
                overlay: Some("release-tag".to_owned()),
                output_sha256: sha256(b"class Example {}\n"),
            },
        );
        fs::write(project.join(MANIFEST_FILE), provenance.to_json().unwrap()).unwrap();
        (
            temp,
            SourceTraceArgs {
                project_root: project,
                file: "src/main/java/Example.java".to_owned(),
            },
        )
    }

    #[test]
    fn reports_owner_and_current_edit_state_without_writing() {
        let (_temp, args) = fixture();
        let manifest_before = fs::read(args.project_root.join(MANIFEST_FILE)).unwrap();
        let initial = trace(&args).unwrap();
        assert_eq!(initial.schema, TRACE_SCHEMA);
        assert_eq!(initial.source_path, args.file);
        assert_eq!(initial.overlay.as_deref(), Some("release-tag"));
        assert!(initial.generated_file_matches_manifest);
        fs::write(args.project_root.join(&args.file), b"contributor edit\n").unwrap();
        let edited = trace(&args).unwrap();
        assert!(!edited.generated_file_matches_manifest);
        assert_eq!(edited.generated_sha256, initial.generated_sha256);
        assert_ne!(edited.current_sha256, initial.current_sha256);
        assert_eq!(
            fs::read(args.project_root.join(MANIFEST_FILE)).unwrap(),
            manifest_before
        );
    }

    #[test]
    fn traces_nested_catalog_owner_without_inventing_a_flat_preset() {
        let (_temp, args) = fixture();
        let mut manifest = ProjectionProvenance::new_catalog(
            "1.19.2",
            "1.19.2",
            CatalogProjectionOwner {
                projection_key: "custom/nested/project".to_owned(),
                environment:
                    crate::source_projection::projection_catalog::ProjectionEnvironment::Dev,
                context_identity: format!("blake3:{}", "a".repeat(64)),
            },
        );
        manifest.files.insert(
            args.file.clone(),
            ProjectedFileProvenance {
                source_path: format!("platform/minecraft/core-liquid-template/{}", args.file),
                source_sha256: sha256(b"class Example {}\n"),
                overlay: None,
                output_sha256: sha256(b"class Example {}\n"),
            },
        );
        fs::write(
            args.project_root.join(MANIFEST_FILE),
            manifest.to_json().unwrap(),
        )
        .unwrap();
        let before = fs::read(args.project_root.join(MANIFEST_FILE)).unwrap();
        let report = trace(&args).unwrap();
        assert_eq!(report.schema, "sfm:source_projection_trace@2");
        assert_eq!(
            report.catalog.unwrap().projection_key,
            "custom/nested/project"
        );
        assert!(report.preset_id.is_empty() && report.preset_definition_identity.is_empty());
        assert!(report.generated_file_matches_manifest);
        assert_eq!(
            fs::read(args.project_root.join(MANIFEST_FILE)).unwrap(),
            before
        );
    }

    #[test]
    fn rejects_unowned_unsafe_and_missing_files() {
        let (_temp, mut args) = fixture();
        args.file = "src/main/java/Other.java".to_owned();
        assert!(trace(&args).unwrap_err().to_string().contains("not owned"));
        args.file = "../outside.java".to_owned();
        assert!(trace(&args).is_err());
        args.file = "src/main/java/Example.java".to_owned();
        fs::remove_file(args.project_root.join(&args.file)).unwrap();
        assert!(trace(&args).is_err());
    }
}
