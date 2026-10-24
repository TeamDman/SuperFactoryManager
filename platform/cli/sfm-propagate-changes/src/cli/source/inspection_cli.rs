//! Native Explorer evidence: comparisons are roots, pairs have representation children.
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::release_review_surface_v1::*;
use crate::release_review_v1::ChangeOperationV1;
use crate::source_comparison::request::ComparisonRequest;
use crate::source_projection::provenance::sha256;
use eyre::{Result, ensure};
use facet::Facet;
use figue as args;
use std::collections::BTreeSet;
use std::io::{Read as _, Write as _};
use std::path::{Path, PathBuf};

#[derive(Debug, Facet)]
pub struct InspectionArgs {
    /// Explicit old/new inputs. No checkout or implicit build is performed.
    #[facet(args::named)]
    pub request: PathBuf,
    /// New workspace JSON file. Existing evidence is never overwritten.
    #[facet(args::named)]
    pub output: PathBuf,
}
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct Request {
    schema: String,
    comparisons: Vec<Input>,
}
#[derive(Debug, Facet)]
#[repr(u8)]
#[facet(deny_unknown_fields)]
enum Input {
    Source { label: String, request: ComparisonRequest },
    Jar { label: String, old: String, new: String, class_evidence: Option<String> },
}
#[derive(Debug, Facet)]
struct Workspace {
    schema: String,
    roots: Vec<Root>,
}
#[derive(Debug, Facet)]
struct Root {
    label: String,
    identity: String,
    provenance: String,
    error: Option<String>,
    pairs: Vec<Pair>,
}
#[derive(Debug, Facet)]
struct Pair {
    path: String,
    classification: String,
    representations: Vec<Representation>,
}
#[derive(Debug, Facet)]
struct Representation {
    label: String,
    filename: String,
    text: String,
    surface_request_json: Option<String>,
    surface_json: Option<String>,
}
impl Representation {
    fn new(label: &str, filename: &str, text: impl Into<String>) -> Self {
        Self { label: label.into(), filename: filename.into(), text: text.into(), surface_request_json: None, surface_json: None }
    }
}
impl InspectionArgs {
    pub(super) fn invoke(self, cancellation: &CancellationToken) -> Result<CliOutput> {
        let request: Request = facet_json::from_slice(&read(&self.request, 1024 * 1024)?)?;
        let workspace = prepare(&request, cancellation)?;
        let bytes = facet_json::to_string(&workspace)?;
        ensure!(bytes.len() <= 128 * 1024 * 1024, "Explorer evidence exceeds 128 MiB");
        let mut output = std::fs::OpenOptions::new().write(true).create_new(true).open(&self.output)?;
        output.write_all(bytes.as_bytes())?;
        output.sync_all()?;
        Ok(CliOutput::facet(("sfm:explorer-inspection-prepared@1", self.output.to_string_lossy().into_owned(), workspace.roots.len(), workspace.roots.iter().filter(|r| r.error.is_some()).count())))
    }
}
fn read(path: &Path, limit: usize) -> Result<Vec<u8>> {
    let mut bytes = Vec::new();
    std::fs::File::open(path)?.take(limit as u64 + 1).read_to_end(&mut bytes)?;
    ensure!(bytes.len() <= limit, "Input exceeds {limit} bytes: {}", path.display());
    Ok(bytes)
}
fn prepare(request: &Request, cancellation: &CancellationToken) -> Result<Workspace> {
    ensure!(request.schema == "sfm:explorer-inspection-request@1", "Unsupported inspection request");
    ensure!(!request.comparisons.is_empty() && request.comparisons.len() <= 8, "Select one to eight comparisons");
    let mut roots = Vec::new();
    for input in &request.comparisons {
        cancellation.bail_if_cancelled()?;
        let label = match input { Input::Source { label, .. } | Input::Jar { label, .. } => label };
        let provenance = facet_json::to_string(input)?;
        let result = match input {
            Input::Source { request, .. } => source(label, request, cancellation),
            Input::Jar { old, new, class_evidence, .. } => jar(label, old, new, class_evidence.as_deref(), cancellation),
        };
        cancellation.bail_if_cancelled()?;
        roots.push(result.unwrap_or_else(|error| Root {
            label: label.clone(), identity: sha256(provenance.as_bytes()), provenance,
            error: Some(format!("{error:#}")), pairs: vec![],
        }));
    }
    Ok(Workspace { schema: "sfm:explorer-inspection@1".into(), roots })
}
fn source(label: &str, request: &ComparisonRequest, cancellation: &CancellationToken) -> Result<Root> {
    let report = crate::source_comparison::compare(request, cancellation)?;
    let mut pairs = Vec::new();
    for diff in &report.differences {
        cancellation.bail_if_cancelled()?;
        let old = diff.old_path.as_ref().and_then(|p| report.left.files.get(p));
        let new = diff.new_path.as_ref().and_then(|p| report.right.files.get(p));
        // Raw originals, modes, object IDs, references and renderer provenance remain independent of views.
        let mut views = vec![Representation::new("Provenance and original bytes", "evidence.json",
            facet_json::to_string(&(diff, old, new))?)];
        let mut classification = diff.status.clone();
        if let (Some(before), Some(after)) = (&diff.before, &diff.after) {
            if let Some(basis) = crate::release_report::whitespace_review::basis(&diff.path, before, after) {
                if diff.status == "changed" { classification = "source-whitespace".into(); }
                views.push(Representation::new("Whitespace classification basis", "classification.txt", basis));
            }
        }
        if diff.status == "exact" {
            if let Some(source) = &diff.before {
                let name = Path::new(&diff.path).file_name().and_then(|s| s.to_str()).unwrap_or("source.txt");
                views.push(Representation::new("Both sources · exact bytes", name, source));
            }
        } else if old.is_some() && diff.before.is_none() || new.is_some() && diff.after.is_none() {
            views.push(Representation::new("Text diff unavailable", "unavailable.txt",
                "At least one present source is binary or unsupported text. It is not an absent side. Original bytes remain above."));
        } else {
            text_views(&mut views, &diff.path, diff.before.as_deref(), diff.after.as_deref())?;
        }
        pairs.push(Pair { path: match (&diff.old_path, &diff.new_path) {
            (Some(a), Some(b)) if a != b => format!("{a} → {b}"),
            _ => diff.path.clone(),
        }, classification, representations: views });
    }
    Ok(Root { label: label.into(), identity: report.comparison_identity,
        provenance: facet_json::to_string(&(report.request, report.left.identity, report.right.identity, report.limitations))?,
        error: None, pairs })
}
fn text(bytes: &[u8]) -> Option<&str> {
    std::str::from_utf8(bytes).ok().filter(|s| !s.chars().any(|c| c.is_control() && !matches!(c, '\r'|'\n'|'\t')))
}
fn text_views(views: &mut Vec<Representation>, path: &str, before: Option<&str>, after: Option<&str>) -> Result<()> {
    let filename = Path::new(path).file_name().and_then(|n| n.to_str()).unwrap_or("source.txt");
    if let Some(s) = before { views.push(Representation::new("Old source", &format!("old-{filename}"), s)); }
    if let Some(s) = after { views.push(Representation::new("New source", &format!("new-{filename}"), s)); }
    if before.is_none() && after.is_none() {
        views.push(Representation::new("Text diff unavailable", "unavailable.txt",
            "Binary or unsupported encoding. Inspect original bytes and hashes; no textual or semantic equivalence is inferred."));
        return Ok(());
    }
    let language = if path.ends_with(".java") { "java" } else { "text" };
    let operation = if before.is_none() { ChangeOperationV1::Added } else if after.is_none() { ChangeOperationV1::Deleted } else { ChangeOperationV1::Modified };
    let pair = ReviewFilePairV1::new("inspection-pair", "inspection", operation, vec!["inspection-unit".into()],
        before.map(|s| ReviewSurfaceSourceV1::new("old", path, language, s)),
        after.map(|s| ReviewSurfaceSourceV1::new("new", path, language, s)));
    let limits = ReviewSurfaceLimitsV1::default();
    for kind in [ReviewSurfaceKindV1::TextPatch, ReviewSurfaceKindV1::JavaStructuredDiff] {
        if kind == ReviewSurfaceKindV1::JavaStructuredDiff && language != "java" { continue; }
        let request = ReviewSurfaceRequestV1::new(1, 1, pair.clone(), kind, limits);
        let result = if kind == ReviewSurfaceKindV1::JavaStructuredDiff {
            crate::release_review_java_diff::produce_java_structured_diff(&request, limits)
        } else { crate::release_review_text_diff::produce_text_diff(&request, limits) };
        match result {
            Ok(surface) => {
                let name = if kind == ReviewSurfaceKindV1::TextPatch { "Text diff · Histogram" } else { "Java structured diff" };
                let mut view = Representation::new(name, "view.diff", surface.text.clone());
                view.surface_request_json = Some(facet_json::to_string(&request)?);
                view.surface_json = Some(facet_json::to_string(&surface)?);
                views.push(view);
                views.push(Representation::new(&format!("{name} · mappings and diagnostics"), "surface.json", facet_json::to_string(&surface)?));
            }
            Err(error) => views.push(Representation::new("Diff unavailable", "unavailable.txt", error.to_string())),
        }
    }
    Ok(())
}
/// Narrow the report's instruction/signature view to printed Code bodies. Keep
/// offsets, operands, symbolic comments and switch targets. Declarations, source
/// filenames, descriptors and exception tables remain in the full class view.
/// This is evidence of printed instruction changes, never behavioral equivalence.
fn instruction_bodies(view: &str) -> String {
    let mut output = String::new();
    let mut in_code = false;
    let mut in_switch = false;
    for line in view.lines() {
        let line = line.trim();
        if line == "Code:" {
            output.push_str("Code:\n");
            in_code = true;
            in_switch = false;
        } else if in_code {
            let offset = line.split_once(':').is_some_and(|(offset, _)|
                !offset.is_empty() && offset.chars().all(|c| c.is_ascii_digit() || c == '-'));
            if offset || in_switch && (line.starts_with("default:") || line == "}") {
                output.push_str(line); output.push('\n');
                if line.contains("lookupswitch {") || line.contains("tableswitch {") { in_switch = true; }
                if line == "}" { in_switch = false; }
            } else { in_code = false; }
        }
    }
    output
}
fn jar(label: &str, old: &str, new: &str, evidence_path: Option<&str>, cancellation: &CancellationToken) -> Result<Root> {
    ensure!(!old.is_empty() && !new.is_empty(), "Select both release JAR paths; no release artifacts were inferred or built");
    let old_hash = sha256(&read(Path::new(old), 128 * 1024 * 1024)?);
    let new_hash = sha256(&read(Path::new(new), 128 * 1024 * 1024)?);
    let a = crate::release_report::archive_tree::entries(old, &old_hash)?;
    let b = crate::release_report::archive_tree::entries(new, &new_hash)?;
    let evidence_bytes = evidence_path.map(|p| read(Path::new(p), 32 * 1024 * 1024)).transpose()?;
    let evidence_hash = evidence_bytes.as_ref().map(|bytes| sha256(bytes));
    let evidence = evidence_bytes.as_ref().map(|bytes| -> Result<crate::release_report::class_evidence::ClassEvidence> {
        let e: crate::release_report::class_evidence::ClassEvidence = facet_json::from_slice(bytes)?;
        ensure!(e.schema == "sfm:jar-class-evidence@1" && e.baseline_sha256 == old_hash && e.candidate_sha256 == new_hash,
            "Class evidence belongs to different JAR bytes or schema");
        let mut seen = BTreeSet::new();
        for row in &e.classes {
            ensure!(row.path.ends_with(".class") && seen.insert(&row.path), "Invalid or duplicate class evidence path");
            match row.status.as_str() {
                "representation" => ensure!(row.before != row.after, "Instruction classification lacks differing printed views"),
                "debug-view" => ensure!(row.before == row.after && row.debug_before != row.debug_after, "Debug-only classification contradicts printed views"),
                "unrepresented-bytes" => ensure!(row.before == row.after && row.debug_before == row.debug_after, "Unrepresented classification contradicts printed views"),
                "added" | "deleted" | "unchanged" | "zip-metadata" => {},
                _ => { eyre::bail!("Unsupported class evidence classification"); }
            }
            for (files, digest) in [(&a, &row.before_sha256), (&b, &row.after_sha256)] {
                let actual = files.get(&row.path).map(|bytes| sha256(bytes).trim_start_matches("sha256:").to_owned()).unwrap_or_default();
                ensure!(actual == *digest, "Class member evidence mismatch: {}", row.path);
            }
        }
        Ok(e)
    }).transpose()?;
    let mut pairs = Vec::new();
    let paths: BTreeSet<_> = a.keys().chain(b.keys()).collect();
    for path in paths {
        cancellation.bail_if_cancelled()?;
        let before = a.get(path); let after = b.get(path);
        let classification = if before.is_none() { "added" } else if after.is_none() { "removed" }
            else if before == after { "exact" } else { "changed" };
        let mut views = vec![Representation::new("Original member bytes and hashes", "evidence.json",
            facet_json::to_string(&((old, &old_hash), (new, &new_hash), path, (before.map(|v| (sha256(v), v)), after.map(|v| (sha256(v), v)))))?)];
        let mut classification = classification.to_owned();
        if let Some(row) = evidence.as_ref().and_then(|e| e.classes.iter().find(|r| r.path == *path)) {
            if classification == "changed" { classification = match row.status.as_str() {
                "representation" if instruction_bodies(&row.before) != instruction_bodies(&row.after) => "instruction-view-changed",
                "representation" => "class-view-changed",
                "debug-view" => "printed-debug-only",
                "unrepresented-bytes" => "unrepresented-byte-change",
                _ => "changed",
            }.into(); }
            if row.status == "representation" {
                let mut representations = Vec::new();
                text_views(&mut representations, "instructions.txt", Some(&instruction_bodies(&row.before)), Some(&instruction_bodies(&row.after)))?;
                for mut view in representations { view.label = format!("Instruction bodies · {}", view.label); views.push(view); }
            }
            for (name, x, y) in [("Instruction/signature", &row.before, &row.after), ("Debug tables", &row.debug_before, &row.debug_after)] {
                let mut representations = Vec::new();
                if x.is_empty() && y.is_empty() {
                    views.push(Representation::new(&format!("{name} · equal printed view"), "equal.txt",
                        "The retained producer records equal printed views and omits their repeated text. This does not establish raw class or semantic equality."));
                } else {
                    text_views(&mut representations, "class.txt", before.map(|_| x.as_str()), after.map(|_| y.as_str()))?;
                    for mut view in representations { view.label = format!("{name} · {}", view.label); views.push(view); }
                }
            }
        } else if path.ends_with(".class") && classification != "exact" {
            views.push(Representation::new("Instruction view unavailable", "unavailable.txt",
                "No hash-bound class evidence supplied. Raw class differences are unclassified. Prepare evidence with release_report/scripts/prepare_class_evidence.py."));
        }
        if before.is_some_and(|v| text(v).is_none()) || after.is_some_and(|v| text(v).is_none()) {
            views.push(Representation::new("Text diff unavailable", "unavailable.txt", "At least one present member is binary or unsupported text. It is not an absent side. Original bytes remain above."));
        } else {
            text_views(&mut views, path, before.and_then(|v| text(v)), after.and_then(|v| text(v)))?;
        }
        pairs.push(Pair { path: path.clone(), classification, representations: views });
    }
    let provenance = facet_json::to_string(&((old, &old_hash), (new, &new_hash), (evidence_path, evidence_hash,
        evidence.as_ref().map(|e| &e.tool)), "Printed debug-only means instruction/signature view equal while printed debug differs. Other attributes are not exhausted. No whitespace cause or behavioral equivalence is inferred."))?;
    Ok(Root { label: label.into(), identity: sha256(provenance.as_bytes()), provenance, error: None, pairs })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn instruction_strategy_excludes_declarations_but_keeps_switch_targets() {
        let a = "Compiled from \"A.java\"\npublic class A {\n  public void f();\n    Code:\n       0: return\n}\n";
        assert_eq!(instruction_bodies(a), instruction_bodies(&a.replace("public", "private")));
        assert_eq!(instruction_bodies(a), instruction_bodies(&a.replace("\n}", "\n  public abstract void added();\n}")));
        assert_ne!(instruction_bodies(a), instruction_bodies(&a.replace("return", "nop")));
        let switch = "Code:\n0: lookupswitch { // 1\n-1: 20\ndefault: 30\n}\n20: return\nException table:\nfrom to target type\n";
        assert!(instruction_bodies(switch).contains("default: 30"));
        assert!(instruction_bodies(switch).contains("-1: 20"));
        assert!(!instruction_bodies(switch).contains("Exception"));
    }
    #[test]
    fn missing_inputs_are_error_roots_never_equal_empty_trees() {
        let request = Request { schema: "sfm:explorer-inspection-request@1".into(), comparisons: vec![Input::Jar {
            label: "release".into(), old: "/nonexistent/old.jar".into(), new: "/nonexistent/new.jar".into(), class_evidence: None,
        }] };
        let w = prepare(&request, &CancellationToken::new()).unwrap();
        assert!(w.roots[0].error.is_some()); assert!(w.roots[0].pairs.is_empty());
    }
    #[test]
    fn text_and_structured_representations_retain_added_removed_and_unicode() {
        let mut views = vec![];
        text_views(&mut views, "A.java", Some("class A { String s = \"old\"; }\n"), Some("class A { String s = \"😀\"; }\n")).unwrap();
        assert!(views.iter().any(|v| v.label == "Java structured diff" && v.text.contains("😀")));
        assert!(views.iter().any(|v| v.label == "Text diff · Histogram" && v.text.contains("old")));
        for (a,b) in [(None, Some("added\n")), (Some("removed\n"), None)] {
            let mut views = vec![]; text_views(&mut views, "x.txt", a,b).unwrap();
            assert!(views.iter().any(|v| v.filename == "view.diff"));
        }
    }
}
