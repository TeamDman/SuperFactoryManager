//! Isolated worker loop: current template -> memory -> immutable Git oracle.

use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::source_projection::core_catalog::read_bounded_catalog_input;
use crate::source_projection::core_features::CoreFeatureDefinitions;
use crate::source_projection::core_features::FEATURE_DEFINITIONS_PATH;
use crate::source_projection::core_inputs::CORE_METADATA_PATH;
use crate::source_projection::core_inputs::CORE_ROOT;
use crate::source_projection::core_inputs::CoreProjectInputs;
use crate::source_projection::core_inputs::select_core_inputs;
use crate::source_projection::oracle::ORACLES_PATH;
use crate::source_projection::oracle::OracleBindings;
use crate::source_projection::oracle_git::OracleGitRepository;
use crate::source_projection::projection_catalog::CATALOG_PATH;
use crate::source_projection::projection_catalog::ProjectionCatalog;
use crate::source_projection::promotion::validate_relative_path;
use crate::source_projection::provenance::sha256;
use crate::source_projection::render_java_source;
use crate::source_projection::simplify::Comparison;
use crate::source_projection::simplify::ComparisonClassification;
use crate::source_projection::simplify::ParsedJava;
use crate::source_projection::simplify::{self};
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
use std::path::PathBuf;
use std::sync::Arc;

#[derive(Debug, Facet)]
pub struct SimplifyVerifyArgs {
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Core-relative Java output path. No generated files are written.
    #[facet(args::named)]
    pub file: String,
    #[facet(args::named, default)]
    pub projection: Vec<String>,
    /// Full commit SHA containing committed projection outputs, instead of historical bindings.
    #[facet(args::named, default)]
    pub baseline_commit: Option<String>,
    #[facet(args::named, default = 16)]
    pub max_regions: usize,
    /// Compact worker output: full checks, failures and bounded whitespace examples.
    #[facet(args::named, default)]
    pub summary: bool,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
enum VerificationStatus {
    Unverified,
    VerifiedJava,
    VerifiedAbsent,
    NonWhitespaceChange,
}

impl VerificationStatus {
    fn is_verified(self) -> bool {
        match self {
            Self::VerifiedJava | Self::VerifiedAbsent => true,
            Self::Unverified | Self::NonWhitespaceChange => false,
        }
    }
}

#[derive(Debug, Facet)]
struct Verification {
    projection: String,
    context_identity: String,
    oracle_commit: String,
    oracle_path: String,
    oracle_blob: Option<String>,
    template_path: Option<String>,
    template_sha256: Option<String>,
    rendered_sha256: Option<String>,
    status: VerificationStatus,
    comparison: Option<Comparison>,
    diagnostic: Option<String>,
}

#[derive(Debug, Facet)]
struct Pair {
    before_projection: String,
    after_projection: String,
    comparison: Comparison,
}

#[derive(Debug, Facet)]
struct Report {
    schema: String,
    scope: String,
    file: String,
    catalog_sha256: String,
    feature_definitions_sha256: String,
    project_inputs_sha256: String,
    oracle_bindings_sha256: Option<String>,
    all_oracles_verified: bool,
    simplification_complete: bool,
    pairs_with_whitespace_candidates: usize,
    inputs: Vec<Verification>,
    candidate_pairs: Vec<Pair>,
    writes_performed: bool,
}

#[derive(Debug, Facet)]
struct WorkerExample {
    before_projection: String,
    after_projection: String,
    region: simplify::Region,
}

#[derive(Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Independent report facts preserve the full verifier's outcomes and explicitly describe sampled, read-only output"
)]
struct WorkerReport {
    schema: String,
    file: String,
    all_oracles_verified: bool,
    simplification_complete: bool,
    contexts_checked: usize,
    pairs_with_whitespace_candidates: usize,
    failures: Vec<Verification>,
    examples: Vec<WorkerExample>,
    examples_are_sample: bool,
    writes_performed: bool,
}

impl WorkerReport {
    fn from_report(report: Report, limit: usize) -> Self {
        let mut seen = BTreeSet::new();
        let mut examples = Vec::new();
        for pair in report.candidate_pairs {
            for region in pair.comparison.whitespace_candidates {
                if examples.len() < limit
                    && seen.insert((region.before.text.clone(), region.after.text.clone()))
                {
                    examples.push(WorkerExample {
                        before_projection: pair.before_projection.clone(),
                        after_projection: pair.after_projection.clone(),
                        region,
                    });
                }
            }
        }
        Self {
            schema: "sfm:source_simplify_worker@1".into(),
            file: report.file,
            all_oracles_verified: report.all_oracles_verified,
            simplification_complete: report.simplification_complete,
            contexts_checked: report.inputs.len(),
            pairs_with_whitespace_candidates: report.pairs_with_whitespace_candidates,
            failures: report
                .inputs
                .into_iter()
                .filter(|row| !row.status.is_verified())
                .collect(),
            examples,
            examples_are_sample: true,
            writes_performed: false,
        }
    }
}

pub(super) fn invoke(
    args: &SimplifyVerifyArgs,
    cancellation: &CancellationToken,
    invocation_dir: &Path,
) -> Result<CliOutput> {
    let report = verify(args, cancellation, invocation_dir)?;
    let status = u8::from(!report.all_oracles_verified);
    if args.summary {
        return Ok(CliOutput::facet_with_status(
            WorkerReport::from_report(report, args.max_regions),
            status,
        ));
    }
    Ok(CliOutput::facet_with_status(report, status))
}

fn trim_regions(report: &mut Comparison, max: usize) {
    report.whitespace_candidates.truncate(max);
    report.changed_token_regions.truncate(max);
    report.regions_truncated = report.whitespace_gap_count > report.whitespace_candidates.len()
        || report.changed_token_region_count > report.changed_token_regions.len();
}

fn cached_parse(
    cache: &mut BTreeMap<String, Arc<ParsedJava>>,
    source: String,
) -> Result<Arc<ParsedJava>> {
    let key = sha256(source.as_bytes());
    if let Some(parsed) = cache.get(&key) {
        return Ok(Arc::clone(parsed));
    }
    let parsed = Arc::new(simplify::parse(source)?);
    cache.insert(key, Arc::clone(&parsed));
    Ok(parsed)
}

// Only the exact generated prologue is outside the Java comparison contract.
// Never normalize source before parsing. The atom comparator alone recognizes
// JLS-equivalent physical line endings in block comments and text blocks.
fn without_banner(source: &str) -> &str {
    const BANNER: &str =
        "// GENERATED by sfm-propagate-changes; edit the primary source or reconcile this file.";
    source
        .strip_prefix(BANNER)
        .and_then(|tail| {
            tail.strip_prefix("\r\n")
                .or_else(|| tail.strip_prefix('\n'))
        })
        .unwrap_or(source)
}

#[tracing::instrument(level = "info", skip_all, name = "source_simplify_verify")]
#[expect(
    clippy::too_many_lines,
    reason = "Single bounded worker transaction keeps snapshot, per-context evidence and pair results together"
)]
fn verify(
    args: &SimplifyVerifyArgs,
    cancellation: &CancellationToken,
    invocation_dir: &Path,
) -> Result<Report> {
    validate_relative_path(&args.file)?;
    ensure!(
        args.file.starts_with("src/")
            && Path::new(&args.file)
                .extension()
                .is_some_and(|ext| ext == "java"),
        "file must be src/... .java"
    );
    ensure!(
        (1..=128).contains(&args.max_regions),
        "max-regions must be between 1 and 128"
    );
    let catalog = CoreCatalog::load(&args.repo_root, invocation_dir)?;
    let keys = if args.projection.is_empty() {
        catalog.catalog.0.keys().cloned().collect::<Vec<_>>()
    } else {
        args.projection.clone()
    };
    ensure!(
        !keys.is_empty()
            && keys.len() <= 32
            && keys.iter().collect::<BTreeSet<_>>().len() == keys.len(),
        "select 1 to 32 unique projections"
    );
    let metadata_bytes = super::simplify_cli::read(
        &catalog.repo_root,
        CORE_METADATA_PATH,
        usize::try_from(crate::source_projection::core_inputs::MAX_CORE_METADATA_BYTES)?,
    )?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&metadata_bytes)?,
        &catalog.registered_features,
    )?;
    let git = OracleGitRepository::open(&catalog.repo_root)?;
    let (bindings, oracle_bindings_sha256) = if let Some(commit) = &args.baseline_commit {
        git.resolve_commit(commit)?;
        (None, None)
    } else {
        let bytes = read_bounded_catalog_input(&catalog.repo_root, ORACLES_PATH)?;
        let bindings: OracleBindings = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            bindings.schema == "sfm:projection_oracles@1"
                && bindings.scope == "sfm:minecraft_project_inputs@1",
            "unsupported oracle bindings"
        );
        (Some(bindings), Some(sha256(&bytes)))
    };
    let mut cache = BTreeMap::new();
    let mut sources = BTreeMap::new();
    let mut parsed = Vec::new();
    let mut inputs = Vec::new();
    for key in keys {
        cancellation.bail_if_cancelled()?;
        let (commit, prefix) = oracle_location(args, &catalog, &git, bindings.as_ref(), &key)?;
        let path = format!("{prefix}/{}", args.file);
        let mut row = Verification {
            projection: key.clone(),
            context_identity: catalog.catalog.context_identity(&key)?,
            oracle_commit: commit.clone(),
            oracle_path: path.clone(),
            oracle_blob: None,
            template_path: None,
            template_sha256: None,
            rendered_sha256: None,
            status: VerificationStatus::Unverified,
            comparison: None,
            diagnostic: None,
        };
        let result = (|| -> Result<Option<Arc<ParsedJava>>> {
            let context = catalog.context(&key)?;
            let selection =
                select_core_inputs(&metadata, &context, &BTreeSet::from([args.file.clone()]))?;
            let oracle = git.file_at_commit(&commit, &path)?;
            let Some(selected) = selection.inputs.get(&args.file) else {
                ensure!(
                    oracle.is_none(),
                    "template omits a file present in the oracle"
                );
                row.status = VerificationStatus::VerifiedAbsent;
                return Ok(None);
            };
            let oracle =
                oracle.ok_or_else(|| eyre::eyre!("selected file absent from pinned oracle"))?;
            row.oracle_blob = Some(oracle.oid);
            let template_path = format!("{CORE_ROOT}/{}", selected.input);
            if !sources.contains_key(&template_path) {
                sources.insert(
                    template_path.clone(),
                    read_bounded_catalog_input(&catalog.repo_root, &template_path)?,
                );
            }
            let bytes = &sources[&template_path];
            row.template_path = Some(template_path);
            row.template_sha256 = Some(sha256(bytes));
            let rendered = render_java_source(std::str::from_utf8(bytes)?, &context)?;
            row.rendered_sha256 = Some(sha256(rendered.as_bytes()));
            let oracle_text = std::str::from_utf8(&oracle.bytes)?;
            let oracle_text = if args.baseline_commit.is_some() {
                without_banner(oracle_text)
            } else {
                oracle_text
            };
            let before = cached_parse(&mut cache, oracle_text.to_owned())?;
            let after = cached_parse(&mut cache, rendered)?;
            let mut comparison = simplify::compare(&before, &after);
            row.status = match comparison.classification {
                ComparisonClassification::Exact | ComparisonClassification::WhitespaceOnly => {
                    VerificationStatus::VerifiedJava
                }
                ComparisonClassification::CodeOrCommentChange => {
                    VerificationStatus::NonWhitespaceChange
                }
            };
            trim_regions(&mut comparison, args.max_regions);
            row.comparison = Some(comparison);
            Ok(Some(after))
        })();
        match result {
            Ok(value) => parsed.push(value),
            Err(error) => {
                row.diagnostic = Some(format!("{error:#}"));
                parsed.push(None);
            }
        }
        inputs.push(row);
    }
    let mut candidate_pairs = Vec::new();
    for left in 0..inputs.len() {
        for right in left + 1..inputs.len() {
            cancellation.bail_if_cancelled()?;
            if let (Some(a), Some(b)) = (&parsed[left], &parsed[right]) {
                let mut comparison = simplify::compare(a, b);
                if comparison.whitespace_gap_count > 0 {
                    trim_regions(&mut comparison, args.max_regions);
                    candidate_pairs.push(Pair {
                        before_projection: inputs[left].projection.clone(),
                        after_projection: inputs[right].projection.clone(),
                        comparison,
                    });
                }
            }
        }
    }
    let all_oracles_verified = inputs.iter().all(|row| row.status.is_verified());
    Ok(Report {
        schema: "sfm:source_simplify_verify@1".into(),
        scope: "read_only_in_memory_java_whitespace_certificate_not_semantic_equivalence".into(),
        file: args.file.clone(),
        catalog_sha256: catalog.catalog_sha256,
        feature_definitions_sha256: catalog.feature_definitions_sha256,
        project_inputs_sha256: sha256(&metadata_bytes),
        oracle_bindings_sha256,
        all_oracles_verified,
        simplification_complete: all_oracles_verified && candidate_pairs.is_empty(),
        pairs_with_whitespace_candidates: candidate_pairs.len(),
        inputs,
        candidate_pairs,
        writes_performed: false,
    })
}

fn oracle_location(
    args: &SimplifyVerifyArgs,
    catalog: &CoreCatalog,
    git: &OracleGitRepository,
    bindings: Option<&OracleBindings>,
    key: &str,
) -> Result<(String, String)> {
    let entry = catalog.catalog.entry(key)?;
    if let Some(commit) = &args.baseline_commit {
        let prefix = catalog
            .catalog
            .project_dir(key)?
            .to_string_lossy()
            .replace('\\', "/");
        let registry = git
            .file_at_commit(commit, FEATURE_DEFINITIONS_PATH)?
            .ok_or_else(|| eyre::eyre!("pinned commit has no feature definitions"))?;
        let registry = CoreFeatureDefinitions::from_json(std::str::from_utf8(&registry.bytes)?)?;
        let pinned = git
            .file_at_commit(commit, CATALOG_PATH)?
            .ok_or_else(|| eyre::eyre!("pinned commit has no projections catalog"))?;
        let pinned = ProjectionCatalog::from_json(
            std::str::from_utf8(&pinned.bytes)?,
            &registry.registered_names(),
        )?;
        registry.validate_entry(pinned.entry(key)?)?;
        ensure!(
            pinned.context_identity(key)? == catalog.catalog.context_identity(key)?,
            "pinned projection context mismatch"
        );
        Ok((commit.clone(), prefix))
    } else {
        let matches = bindings
            .ok_or_else(|| eyre::eyre!("missing historical bindings"))?
            .bindings
            .iter()
            .filter(|binding| binding.projection == key)
            .collect::<Vec<_>>();
        ensure!(
            matches.len() == 1,
            "projection {key} requires exactly one pinned oracle"
        );
        let binding = matches[0];
        ensure!(
            binding.target_id == entry.target_id()?
                && binding.environment == entry.environment.as_str(),
            "oracle context mismatch for {key}"
        );
        ensure!(
            git.resolve_commit(&binding.commit)?.tree == binding.tree,
            "oracle tree mismatch for {key}"
        );
        Ok((binding.commit.clone(), binding.source_prefix.clone()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::process::Command;

    const FILE: &str = "src/main/java/A.java";

    #[test]
    fn verification_status_preserves_wire_names_and_outcomes() {
        for (status, wire, verified) in [
            (VerificationStatus::Unverified, "\"unverified\"", false),
            (VerificationStatus::VerifiedJava, "\"verified_java\"", true),
            (
                VerificationStatus::VerifiedAbsent,
                "\"verified_absent\"",
                true,
            ),
            (
                VerificationStatus::NonWhitespaceChange,
                "\"non_whitespace_change\"",
                false,
            ),
        ] {
            assert_eq!(facet_json::to_string(&status).unwrap(), wire);
            assert_eq!(
                facet_json::from_str::<VerificationStatus>(wire).unwrap(),
                status
            );
            assert_eq!(status.is_verified(), verified);
        }
        assert!(facet_json::from_str::<VerificationStatus>("\"unknown\"").is_err());
    }

    #[test]
    fn worker_examples_are_bounded_without_changing_full_check_outcomes() {
        let before = simplify::parse("class A { }".into()).unwrap();
        let after = simplify::parse("class A {\n}".into()).unwrap();
        let report = Report {
            schema: "fixture".into(),
            scope: "fixture".into(),
            file: FILE.into(),
            catalog_sha256: String::new(),
            feature_definitions_sha256: String::new(),
            project_inputs_sha256: String::new(),
            oracle_bindings_sha256: None,
            all_oracles_verified: false,
            simplification_complete: false,
            pairs_with_whitespace_candidates: 2,
            inputs: vec![],
            candidate_pairs: (0..2)
                .map(|index| Pair {
                    before_projection: format!("before/{index}"),
                    after_projection: format!("after/{index}"),
                    comparison: simplify::compare(&before, &after),
                })
                .collect(),
            writes_performed: false,
        };
        let compact = WorkerReport::from_report(report, 1);
        assert!(!compact.all_oracles_verified);
        assert!(!compact.simplification_complete);
        assert_eq!(compact.pairs_with_whitespace_candidates, 2);
        assert_eq!(compact.examples.len(), 1);
        assert!(compact.examples_are_sample);
        assert!(!compact.writes_performed);
    }

    fn write(root: &Path, path: &str, bytes: &[u8]) {
        let path = root.join(path);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, bytes).unwrap();
    }

    fn git(root: &Path, args: &[&str]) -> String {
        let output = Command::new("git")
            .arg("-C")
            .arg(root)
            .args([
                "-c",
                "user.name=Fixture",
                "-c",
                "user.email=fixture@example.invalid",
                "-c",
                "commit.gpgSign=false",
                "-c",
                "core.autocrlf=false",
            ])
            .args(args)
            .env_remove("GIT_DIR")
            .env_remove("GIT_WORK_TREE")
            .env_remove("GIT_INDEX_FILE")
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    }

    #[test]
    fn pinned_projection_verifies_memory_ignores_disk_and_rejects_changes() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path();
        write(root, FEATURE_DEFINITIONS_PATH, b"{}");
        write(
            root,
            CATALOG_PATH,
            br#"{"a":{"minecraft_version":"1.19.2","environment":"release","features":[]}}"#,
        );
        write(root, CORE_METADATA_PATH, br#"{"schema_version":1,"targets":{"1.19.2":{"java_major":17,"loader":"forge"}},"source_rules":{},"project_files":{}}"#);
        // Real core membership metadata exceeds the catalog's 1 MiB budget.
        let mut metadata = fs::read(root.join(CORE_METADATA_PATH)).unwrap();
        metadata.resize(1024 * 1024 + 1, b' ');
        write(root, CORE_METADATA_PATH, &metadata);
        write(root, &format!("{CORE_ROOT}/{FILE}"), b"class A {\n}\n");
        let catalog = CoreCatalog::load(root, root).unwrap();
        let prefix = catalog
            .catalog
            .project_dir("a")
            .unwrap()
            .to_string_lossy()
            .replace('\\', "/");
        write(root, &format!("{prefix}/{FILE}"), b"class A {}\n");
        git(root, &["init", "--quiet"]);
        git(root, &["add", "."]);
        git(root, &["commit", "--quiet", "-m", "baseline"]);
        let commit = git(root, &["rev-parse", "HEAD"]);
        let mut args = SimplifyVerifyArgs {
            repo_root: root.to_owned(),
            file: FILE.into(),
            projection: vec!["a".into()],
            baseline_commit: Some(commit.clone()),
            max_regions: 16,
            summary: false,
        };
        let report = verify(&args, &CancellationToken::new(), root).unwrap();
        assert!(report.all_oracles_verified);
        assert_eq!(report.inputs[0].status, VerificationStatus::VerifiedJava);
        let compact =
            WorkerReport::from_report(verify(&args, &CancellationToken::new(), root).unwrap(), 1);
        assert!(compact.all_oracles_verified);
        assert_eq!(compact.contexts_checked, 1);
        assert!(compact.failures.is_empty());
        assert!(facet_json::to_string(&compact).unwrap().len() < 2_000);
        let catalog_before = fs::read(root.join(CATALOG_PATH)).unwrap();
        write(
            root,
            CATALOG_PATH,
            br#"{"a":{"minecraft_version":"1.19.2","environment":"dev","features":[]}}"#,
        );
        assert!(
            format!(
                "{:#}",
                verify(&args, &CancellationToken::new(), root).unwrap_err()
            )
            .contains("pinned projection context mismatch")
        );
        write(root, CATALOG_PATH, &catalog_before);
        assert_eq!(
            report.inputs[0].comparison.as_ref().unwrap().classification,
            ComparisonClassification::WhitespaceOnly
        );
        write(root, &format!("{prefix}/{FILE}"), b"class ChangedDisk {}\n");
        git(root, &["add", "."]);
        git(root, &["commit", "--quiet", "-m", "move HEAD"]);
        assert!(
            verify(&args, &CancellationToken::new(), root)
                .unwrap()
                .all_oracles_verified
        );
        assert_eq!(
            fs::read(root.join(format!("{prefix}/{FILE}"))).unwrap(),
            b"class ChangedDisk {}\n"
        );
        write(
            root,
            &format!("{CORE_ROOT}/{FILE}"),
            b"class ChangedTemplate {}\n",
        );
        let report = verify(&args, &CancellationToken::new(), root).unwrap();
        assert_eq!(
            report.inputs[0].status,
            VerificationStatus::NonWhitespaceChange
        );
        args.summary = true;
        assert_eq!(
            invoke(&args, &CancellationToken::new(), root)
                .unwrap()
                .exit_code(),
            1
        );
        write(root, &format!("{CORE_ROOT}/{FILE}"), b"class A {\n}\n");
        // The same pinned source is usable through historical binding mode.
        let reader = OracleGitRepository::open(root).unwrap();
        let tree = reader.resolve_commit(&commit).unwrap().tree;
        let bindings = format!(
            r#"{{"schema":"sfm:projection_oracles@1","scope":"sfm:minecraft_project_inputs@1","comparison_policy":{{"schema_version":1,"allow_generated_banner":true,"normalize_crlf":true,"allow_final_newline_difference":true,"allow_settings_project_identity_override":true}},"bindings":[{{"projection":"a","target_id":"1.19.2","environment":"release","reference":"refs/heads/main","commit":"{commit}","tree":"{tree}","source_prefix":"{prefix}","expected_files":1,"inventory_identity":"unused"}}]}}"#
        );
        write(root, ORACLES_PATH, bindings.as_bytes());
        let mut historical = args;
        historical.baseline_commit = None;
        assert!(
            verify(&historical, &CancellationToken::new(), root)
                .unwrap()
                .all_oracles_verified
        );
        historical.file = "src/main/java/Missing.java".into();
        write(
            root,
            &format!("{CORE_ROOT}/{}", historical.file),
            b"class Missing {}",
        );
        let report = verify(&historical, &CancellationToken::new(), root).unwrap();
        assert!(!report.all_oracles_verified);
        assert_eq!(report.inputs[0].status, VerificationStatus::Unverified);
        assert!(
            report.inputs[0]
                .diagnostic
                .as_ref()
                .unwrap()
                .contains("absent from pinned oracle")
        );
        let mut metadata: CoreProjectInputs = facet_json::from_str(
            std::str::from_utf8(&fs::read(root.join(CORE_METADATA_PATH)).unwrap()).unwrap(),
        )
        .unwrap();
        metadata
            .source_rules
            .insert(historical.file.clone(), vec![]);
        write(
            root,
            CORE_METADATA_PATH,
            facet_json::to_string(&metadata).unwrap().as_bytes(),
        );
        let report = verify(&historical, &CancellationToken::new(), root).unwrap();
        assert!(report.all_oracles_verified);
        assert_eq!(report.inputs[0].status, VerificationStatus::VerifiedAbsent);
    }

    #[test]
    fn banner_removal_is_exact_and_preserves_comment_and_literal_bytes() {
        let source = "class A { String x = \"x\"; }";
        assert_eq!(without_banner(source), source);
        assert_eq!(
            without_banner(&format!(
                "// GENERATED by sfm-propagate-changes; edit the primary source or reconcile this file.\r\n{source}"
            )),
            source
        );
        let forged = format!("// GENERATED differently\n{source}");
        assert_eq!(without_banner(&forged), forged);
    }

    #[test]
    fn certificate_rejects_code_comment_and_literal_changes() {
        let before = simplify::parse("class A { String x = \"one\"; /*keep*/ }".into()).unwrap();
        for source in [
            "class A { String x = \"two\"; /*keep*/ }",
            "class A { String x = \"one\"; /*changed*/ }",
            "class B { String x = \"one\"; /*keep*/ }",
        ] {
            assert_eq!(
                simplify::compare(&before, &simplify::parse(source.into()).unwrap()).classification,
                ComparisonClassification::CodeOrCommentChange
            );
        }
        assert_eq!(
            simplify::compare(
                &before,
                &simplify::parse("class A {\n String x = \"one\"; /*keep*/\n }".into()).unwrap()
            )
            .classification,
            ComparisonClassification::WhitespaceOnly
        );
    }
}
