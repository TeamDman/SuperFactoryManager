//! Native Explorer evidence: comparisons are roots, pairs have representation children.
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::release_review_surface_v1::*;
use crate::release_review_v1::ChangeOperationV1;
use crate::source_comparison::request::ComparisonRequest;
use crate::source_projection::provenance::sha256;
use eyre::{Result, ensure};
use facet::Facet;
use base64::Engine as _;
use figue as args;
use std::collections::{BTreeMap, BTreeSet};
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
#[derive(Debug, Facet)]
struct OriginalFile {
    encoding: String,
    bytes_base64: String,
    byte_length: usize,
    sha256: String,
    mode: Option<u32>,
    git_oid: Option<String>,
    source_path: Option<String>,
    source_sha256: Option<String>,
    related_references: Option<crate::source_projection::reference_associations::ReferenceAssociations>,
}
fn original(file: Option<&crate::source_comparison::file::ComparisonFile>) -> Result<Option<OriginalFile>> {
    file.map(|f| {
        ensure!(f.bytes.len() <= 4 * 1024 * 1024, "Inspection source exceeds 4 MiB: {:?}", f.source_path);
        Ok(OriginalFile { encoding: "base64".into(), bytes_base64: base64::engine::general_purpose::STANDARD.encode(&f.bytes),
            byte_length: f.bytes.len(), sha256: f.sha256.clone(), mode: f.mode, git_oid: f.git_oid.clone(),
            source_path: f.source_path.clone(), source_sha256: f.source_sha256.clone(), related_references: f.related_references.clone() })
    }).transpose()
}
fn account_views(views: &[Representation], retained: &mut usize) -> Result<()> {
    for v in views {
        for value in [Some(v.text.as_str()), v.surface_request_json.as_deref(), v.surface_json.as_deref()].into_iter().flatten() {
            ensure!(value.len() <= 8 * 1024 * 1024, "Inspection representation exceeds 8 MiB");
            *retained = retained.checked_add(value.len()).ok_or_else(|| eyre::eyre!("Inspection budget overflow"))?;
        }
    }
    ensure!(*retained <= 64 * 1024 * 1024, "Inspection retained text exceeds 64 MiB");
    Ok(())
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
        let mut bytes = BoundedJson(Vec::new());
        facet_json::to_writer_std(&mut bytes, &workspace)?;
        let mut output = std::fs::OpenOptions::new().write(true).create_new(true).open(&self.output)?;
        output.write_all(&bytes.0)?;
        output.sync_all()?;
        Ok(CliOutput::facet(("sfm:explorer-inspection-prepared@1", self.output.to_string_lossy().into_owned(), workspace.roots.len(), workspace.roots.iter().filter(|r| r.error.is_some()).count())))
    }
}
struct BoundedJson(Vec<u8>);
impl std::io::Write for BoundedJson {
    fn write(&mut self, bytes: &[u8]) -> std::io::Result<usize> {
        if self.0.len().saturating_add(bytes.len()) > 128 * 1024 * 1024 {
            return Err(std::io::Error::other("Explorer evidence exceeds 128 MiB"));
        }
        self.0.extend_from_slice(bytes); Ok(bytes.len())
    }
    fn flush(&mut self) -> std::io::Result<()> { Ok(()) }
}
fn read(path: &Path, limit: usize) -> Result<Vec<u8>> {
    let metadata = std::fs::symlink_metadata(path)?;
    ensure!(metadata.file_type().is_file() && metadata.len() <= limit as u64, "Input must be a regular file within {limit} bytes: {}", path.display());
    let mut bytes = Vec::new();
    std::fs::File::open(path)?.take(limit as u64 + 1).read_to_end(&mut bytes)?;
    ensure!(bytes.len() <= limit, "Input exceeds {limit} bytes: {}", path.display());
    Ok(bytes)
}
fn prepare(request: &Request, cancellation: &CancellationToken) -> Result<Workspace> {
    ensure!(request.schema == "sfm:explorer-inspection-request@1", "Unsupported inspection request");
    ensure!(!request.comparisons.is_empty() && request.comparisons.len() <= 8, "Select one to eight comparisons");
    let mut roots = Vec::new();
    let mut retained = 0;
    for input in &request.comparisons {
        cancellation.bail_if_cancelled()?;
        let label = match input { Input::Source { label, .. } | Input::Jar { label, .. } => label };
        let provenance = facet_json::to_string(input)?;
        let result = match input {
            Input::Source { request, .. } => source(label, request, cancellation),
            Input::Jar { old, new, class_evidence, .. } => jar(label, old, new, class_evidence.as_deref(), cancellation),
        }.and_then(|root| {
            let mut next = retained;
            for pair in &root.pairs { account_views(&pair.representations, &mut next)?; }
            retained = next; Ok(root)
        });
        cancellation.bail_if_cancelled()?;
        roots.push(result.unwrap_or_else(|error| Root {
            label: label.clone(), identity: sha256(provenance.as_bytes()), provenance,
            error: Some(format!("{error:#}")), pairs: vec![],
        }));
    }
    Ok(Workspace { schema: "sfm:explorer-inspection@1".into(), roots })
}
fn source(label: &str, request: &ComparisonRequest, cancellation: &CancellationToken) -> Result<Root> {
    for input in [&request.left, &request.right] {
        if let crate::source_comparison::input::ComparisonInput::Directory { root } = input {
            admit_directory(Path::new(root), cancellation)?;
        }
    }
    let report = crate::source_comparison::compare(request, cancellation)?;
    let mut pairs = Vec::new();
    let mut retained = 0;
    ensure!(report.differences.len() <= 20000, "Inspection exceeds 20000 pairs");
    for snapshot in [&report.left, &report.right] {
        ensure!(snapshot.files.values().map(|f| f.bytes.len()).sum::<usize>() <= 32 * 1024 * 1024,
            "Inspection source side exceeds 32 MiB");
    }
    for diff in &report.differences {
        cancellation.bail_if_cancelled()?;
        let old = diff.old_path.as_ref().and_then(|p| report.left.files.get(p));
        let new = diff.new_path.as_ref().and_then(|p| report.right.files.get(p));
        // Raw originals, modes, object IDs, references and renderer provenance remain independent of views.
        let mut views = vec![Representation::new("Provenance and original bytes", "evidence.json",
            facet_json::to_string(&(diff, original(old)?, original(new)?))?)];
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
        account_views(&views, &mut retained)?;
        pairs.push(Pair { path: match (&diff.old_path, &diff.new_path) {
            (Some(a), Some(b)) if a != b => format!("{a} → {b}"),
            _ => diff.path.clone(),
        }, classification, representations: views });
    }
    Ok(Root { label: label.into(), identity: report.comparison_identity,
        provenance: facet_json::to_string(&(report.request, report.left.identity, report.right.identity, report.limitations))?,
        error: None, pairs })
}
fn admit_directory(root: &Path, cancellation: &CancellationToken) -> Result<()> {
    ensure!(std::fs::symlink_metadata(root)?.file_type().is_dir(), "Inspection source must be a real directory");
    let mut pending = vec![root.to_owned()];
    let mut count = 0;
    let mut total = 0_u64;
    while let Some(path) = pending.pop() {
        for entry in std::fs::read_dir(path)? {
            cancellation.bail_if_cancelled()?;
            let entry = entry?;
            if entry.file_name().to_string_lossy().eq_ignore_ascii_case(".git") { continue; }
            let info = std::fs::symlink_metadata(entry.path())?;
            ensure!(!info.file_type().is_symlink(), "Inspection source contains a symbolic link");
            count += 1;
            ensure!(count <= 20000, "Inspection source entry budget exceeded");
            if info.is_dir() { pending.push(entry.path()); }
            else {
                ensure!(info.is_file() && info.len() <= 4 * 1024 * 1024, "Inspection source file exceeds 4 MiB or is a special file");
                total += info.len();
                ensure!(total <= 32 * 1024 * 1024, "Inspection source side exceeds 32 MiB");
            }
        }
    }
    Ok(())
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
fn instruction_bodies(view: &str) -> Option<BTreeMap<String, String>> {
    let lines: Vec<_> = view.lines().map(str::trim).filter(|line| !line.is_empty()).collect();
    if !lines.first()?.starts_with("Compiled from \"") || !lines.first()?.ends_with('"')
        || !lines.get(1)?.ends_with('{') || lines.last()? != &"}"
        || !lines[1].split_whitespace().any(|word| matches!(word, "class" | "interface")) { return None; }
    let mut output = BTreeMap::new();
    let mut in_code = false;
    let mut in_switch = false;
    let mut in_exceptions = false;
    let mut method: Option<String> = None;
    let mut descriptor: Option<String> = None;
    let mut awaiting_descriptor = false;
    let mut key = String::new();
    for line in &lines[2..lines.len() - 1] {
        if in_code {
            let offset = line.split_once(':').is_some_and(|(offset, _)|
                !offset.is_empty() && offset.chars().all(|c| c.is_ascii_digit() || in_switch && c == '-'));
            if offset || in_switch && (line.starts_with("default:") || *line == "}") {
                let body: &mut String = output.get_mut(&key)?;
                body.push_str(line); body.push('\n');
                if line.split_once(':').and_then(|(_, tail)| tail.split_whitespace().next())
                    .is_some_and(|opcode| matches!(opcode, "lookupswitch" | "tableswitch")) { in_switch = true; }
                if *line == "}" { in_switch = false; }
                continue;
            }
            if in_switch { return None; }
            in_code = false;
        }
        if *line == "Exception table:" { in_exceptions = true; continue; }
        if in_exceptions {
            let words: Vec<_> = line.split_whitespace().collect();
            if words == ["from", "to", "target", "type"] { continue; }
            if words.len() >= 4 && words[..3].iter().all(|v| v.chars().all(|c| c.is_ascii_digit())) { continue; }
            in_exceptions = false;
        }
        if *line == "static {};" {
            if awaiting_descriptor { return None; }
            method = Some("<clinit>".into()); descriptor = None;
            awaiting_descriptor = true;
        } else if line.ends_with(';') && !line.starts_with("descriptor:") {
            if awaiting_descriptor { return None; }
            method = line.split_once('(').and_then(|(prefix, _)| prefix.split_whitespace().last()).map(str::to_owned);
            descriptor = None;
            awaiting_descriptor = true;
        } else if let Some(value) = line.strip_prefix("descriptor:") {
            if !awaiting_descriptor { return None; }
            awaiting_descriptor = false;
            let value = value.trim();
            if value.len() > 4096 || value.bytes().filter(|b| *b == b'[').count() > 255 { return None; }
            let checked = if method.is_some() { value.to_owned() } else {
                if value == "V" { return None; }
                format!("(){value}")
            };
            if crate::java_analysis::validate_method_descriptor(&checked).is_err() { return None; }
            descriptor = Some(value.to_owned());
        } else if *line == "Code:" {
            let descriptor = descriptor.as_ref()?;
            if descriptor.len() > 4096 || descriptor.bytes().filter(|b| *b == b'[').count() > 255
                || crate::java_analysis::validate_method_descriptor(descriptor).is_err() { return None; }
            key = format!("{} {}", method.as_ref()?, descriptor);
            if output.insert(key.clone(), String::new()).is_some() { return None; }
            in_code = true;
        } else { return None; }
    }
    if in_switch || awaiting_descriptor || output.values().any(String::is_empty) { return None; }
    Some(output)
}
fn instruction_view(view: &str) -> Option<String> {
    Some(instruction_bodies(view)?.into_iter().map(|(method, body)| format!("{method}\n{body}\n")).collect())
}
fn instructions_changed(a: &BTreeMap<String, String>, b: &BTreeMap<String, String>) -> bool {
    if a.iter().any(|(method, body)| b.get(method).is_some_and(|other| other != body)) { return true; }
    // A renamed/re-described method with identical printed code is a declaration
    // change. Added/deleted bodies still count; matching names cannot hide swaps.
    let mut removed: Vec<_> = a.iter().filter(|(method, _)| !b.contains_key(*method)).map(|(_, body)| body).collect();
    let mut added: Vec<_> = b.iter().filter(|(method, _)| !a.contains_key(*method)).map(|(_, body)| body).collect();
    removed.sort(); added.sort(); removed != added
}
fn jar(label: &str, old: &str, new: &str, evidence_path: Option<&str>, cancellation: &CancellationToken) -> Result<Root> {
    ensure!(!old.is_empty() && !new.is_empty(), "Select both release JAR paths; no release artifacts were inferred or built");
    let old_hash = sha256(&read(Path::new(old), 128 * 1024 * 1024)?);
    let new_hash = sha256(&read(Path::new(new), 128 * 1024 * 1024)?);
    let a = crate::release_report::archive_tree::entries_for_inspection(old, &old_hash)?;
    let b = crate::release_report::archive_tree::entries_for_inspection(new, &new_hash)?;
    let evidence_bytes = evidence_path.map(|p| read(Path::new(p), 32 * 1024 * 1024)).transpose()?;
    let evidence_hash = evidence_bytes.as_ref().map(|bytes| sha256(bytes));
    let evidence = evidence_bytes.as_ref().map(|bytes| -> Result<crate::release_report::class_evidence::ClassEvidence> {
        let e: crate::release_report::class_evidence::ClassEvidence = facet_json::from_slice(bytes)?;
        ensure!(e.schema == "sfm:jar-class-evidence@1" && e.baseline_sha256 == old_hash && e.candidate_sha256 == new_hash,
            "Class evidence belongs to different JAR bytes or schema");
        let mut seen = BTreeSet::new();
        for row in &e.classes {
            ensure!(row.path.ends_with(".class") && seen.insert(&row.path), "Invalid or duplicate class evidence path");
            let before = a.get(&row.path); let after = b.get(&row.path);
            let membership_valid = match row.status.as_str() {
                "added" => before.is_none() && after.is_some(),
                "deleted" => before.is_some() && after.is_none(),
                "unchanged" | "zip-metadata" => before.is_some() && before == after,
                "representation" | "debug-view" | "unrepresented-bytes" => before.is_some() && after.is_some() && before != after,
                _ => false,
            };
            ensure!(membership_valid, "Class evidence status contradicts raw member membership/equality: {}", row.path);
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
    let class_rows: BTreeMap<_, _> = evidence.as_ref().map(|e| e.classes.iter().map(|r| (r.path.as_str(), r)).collect()).unwrap_or_default();
    let mut pairs = Vec::new();
    let mut retained = 0;
    let paths: BTreeSet<_> = a.keys().chain(b.keys()).collect();
    for path in paths {
        cancellation.bail_if_cancelled()?;
        let before = a.get(path); let after = b.get(path);
        let classification = if before.is_none() { "added" } else if after.is_none() { "removed" }
            else if before == after { "exact" } else { "changed" };
        let mut views = vec![Representation::new("Original member bytes and hashes", "evidence.json",
            facet_json::to_string(&((old, &old_hash), (new, &new_hash), path, (before.map(|v| ("base64", sha256(v), base64::engine::general_purpose::STANDARD.encode(v))), after.map(|v| ("base64", sha256(v), base64::engine::general_purpose::STANDARD.encode(v))))))?)];
        let mut classification = classification.to_owned();
        if let Some(row) = class_rows.get(path.as_str()) {
            if classification == "changed" { classification = match row.status.as_str() {
                "representation" => match (instruction_bodies(&row.before), instruction_bodies(&row.after)) {
                    (Some(a), Some(b)) if instructions_changed(&a, &b) => "instruction-view-changed",
                    (Some(_), Some(_)) => "class-view-changed",
                    _ => "changed",
                },
                "debug-view" => "printed-debug-only",
                "unrepresented-bytes" => "unrepresented-byte-change",
                _ => "changed",
            }.into(); }
            if row.status == "representation" {
                let mut representations = Vec::new();
                if let (Some(a), Some(b)) = (instruction_view(&row.before), instruction_view(&row.after)) {
                    text_views(&mut representations, "instructions.txt", Some(&a), Some(&b))?;
                } else { representations.push(Representation::new("Unavailable", "unavailable.txt", "Printed method identity or descriptor could not be parsed. Instruction classification remains unknown.")); }
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
        account_views(&views, &mut retained)?;
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
        let a = "Compiled from \"A.java\"\npublic class A {\n  public void f();\n    descriptor: ()V\n    Code:\n       0: return\n}\n";
        assert_eq!(instruction_bodies(a), instruction_bodies(&a.replace("public", "private")));
        assert_eq!(instruction_bodies(a), instruction_bodies(&a.replace("\n}", "\n  public abstract void added();\n    descriptor: ()V\n}")));
        assert_ne!(instruction_bodies(a), instruction_bodies(&a.replace("return", "nop")));
        assert!(instruction_bodies("garbage").is_none());
        assert!(instruction_bodies(&a.replace("()V", "(garbage")).is_none());
        assert!(instruction_bodies(&a.replace("       0: return\n", "")).is_none());
        let switch = "Compiled from \"A.java\"\nclass A {\nvoid f();\ndescriptor: ()V\nCode:\n0: lookupswitch { // 1\n-1: 20\ndefault: 30\n}\n20: return\nException table:\nfrom to target type\n}\n";
        assert!(instruction_view(switch).unwrap().contains("default: 30"));
        assert!(instruction_view(switch).unwrap().contains("-1: 20"));
        assert!(!instruction_view(switch).unwrap().contains("Exception"));
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
