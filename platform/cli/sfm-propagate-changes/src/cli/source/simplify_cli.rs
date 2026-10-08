//! Read-only analysis of actual generated Java, not Liquid fragments.

use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::source_projection::promotion::validate_relative_path;
use crate::source_projection::provenance::sha256;
use crate::source_projection::simplify::Comparison;
use crate::source_projection::simplify::ParsedJava;
use crate::source_projection::simplify::{self};
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;
use std::sync::Arc;

#[derive(Debug, Facet)]
pub struct SimplifyArgs {
    #[facet(args::subcommand)]
    pub command: SimplifyCommand,
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum SimplifyCommand {
    /// Scan one complete manifested Java file across selected catalog contexts.
    Scan(SimplifyScanArgs),
    /// Render one template in memory and certify it against pinned Git oracles.
    Verify(super::simplify_verify_cli::SimplifyVerifyArgs),
}

#[derive(Debug, Facet)]
pub struct SimplifyScanArgs {
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Project-relative Java path, such as src/main/java/ca/teamdman/sfm/SFM.java.
    #[facet(args::named)]
    pub file: String,
    /// Exact projection key; repeat to select contexts. Default: all catalog entries.
    #[facet(args::named, default)]
    pub projection: Vec<String>,
    /// Maximum displayed regions of each kind per pair (1 to 128); counts stay complete.
    #[facet(args::named, default = 16)]
    pub max_regions: usize,
}

#[derive(Debug, Facet)]
struct ProjectionInput {
    projection: String,
    minecraft_version: String,
    environment: String,
    enabled_features: Vec<String>,
    context_identity: String,
    path: String,
    source_sha256: Option<String>,
    status: String,
    diagnostic: Option<String>,
}

#[derive(Debug, Facet)]
struct PairReport {
    before_projection: String,
    after_projection: String,
    comparison: Option<Comparison>,
    diagnostic: Option<String>,
}

#[derive(Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Independent evidence boundaries in a machine-readable report, not runtime state"
)]
struct ScanReport {
    schema: String,
    algorithm: String,
    parser: String,
    scope: String,
    file: String,
    catalog_sha256: String,
    feature_definitions_sha256: String,
    all_inputs_parsed: bool,
    distinct_sources_parsed: usize,
    parsed_pair_count: usize,
    pairs_with_whitespace_candidates: usize,
    inputs: Vec<ProjectionInput>,
    pairs: Vec<PairReport>,
    writes_performed: bool,
    core_freshness_checked: bool,
    all_feature_combinations_checked: bool,
}

impl SimplifyArgs {
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        match self.command {
            SimplifyCommand::Verify(args) => {
                super::simplify_verify_cli::invoke(&args, cancellation, invocation_dir)
            }
            SimplifyCommand::Scan(args) => {
                Ok(CliOutput::facet(scan(&args, cancellation, invocation_dir)?))
            }
        }
    }
}

pub(super) fn read(root: &Path, relative: &str, max: usize) -> Result<Vec<u8>> {
    let path = checked_file(root, relative)?;
    let file = fs::File::open(path)?;
    ensure!(
        file.metadata()?.len() <= max as u64,
        "input exceeds {max} bytes"
    );
    let mut bytes = Vec::new();
    file.take(max as u64 + 1).read_to_end(&mut bytes)?;
    ensure!(bytes.len() <= max, "input grew beyond {max} bytes");
    Ok(bytes)
}

fn selected_catalog(
    args: &SimplifyScanArgs,
    invocation_dir: &Path,
) -> Result<(CoreCatalog, BTreeSet<String>)> {
    validate_relative_path(&args.file)?;
    ensure!(
        args.file.starts_with("src/")
            && Path::new(&args.file)
                .extension()
                .is_some_and(|ext| ext == "java"),
        "file must be a project-relative src/... .java file"
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
    let unique = keys.iter().cloned().collect::<BTreeSet<_>>();
    ensure!(unique.len() == keys.len(), "duplicate projection selection");
    ensure!(
        (2..=32).contains(&keys.len()),
        "select between 2 and 32 projections"
    );
    Ok((catalog, unique))
}

#[tracing::instrument(level = "info", skip_all, name = "source_simplify_scan")]
fn scan(
    args: &SimplifyScanArgs,
    cancellation: &CancellationToken,
    invocation_dir: &Path,
) -> Result<ScanReport> {
    let (catalog, unique) = selected_catalog(args, invocation_dir)?;
    let mut cache: BTreeMap<String, std::result::Result<Arc<ParsedJava>, String>> = BTreeMap::new();
    let mut inputs = Vec::new();
    let mut parsed = Vec::new();
    for key in unique {
        cancellation.bail_if_cancelled()?;
        let entry = catalog.catalog.entry(&key)?;
        let context_identity = catalog.catalog.context_identity(&key)?;
        let project = catalog
            .catalog
            .project_dir(&key)?
            .to_string_lossy()
            .replace('\\', "/");
        let mut input = ProjectionInput {
            projection: key.clone(),
            minecraft_version: entry.minecraft_version.clone(),
            environment: entry.environment.as_str().into(),
            enabled_features: entry.features.clone(),
            context_identity,
            path: format!("{project}/{}", args.file),
            source_sha256: None,
            status: "unparsed".into(),
            diagnostic: None,
        };
        let loaded = load_java(&catalog, &mut input, &mut cache);
        match loaded {
            Ok(value) => {
                input.status = "parsed_manifested_java".into();
                parsed.push(Some(value));
            }
            Err(error) => {
                input.diagnostic = Some(format!("{error:#}"));
                parsed.push(None);
            }
        }
        inputs.push(input);
    }
    let mut pairs = Vec::new();
    let mut parsed_pair_count = 0;
    let mut pairs_with_whitespace_candidates = 0;
    for left in 0..inputs.len() {
        for right in left + 1..inputs.len() {
            cancellation.bail_if_cancelled()?;
            let comparison = match (&parsed[left], &parsed[right]) {
                (Some(a), Some(b)) => {
                    let mut report = simplify::compare(a, b);
                    parsed_pair_count += 1;
                    if report.whitespace_gap_count > 0 {
                        pairs_with_whitespace_candidates += 1;
                    }
                    report.whitespace_candidates.truncate(args.max_regions);
                    report.changed_token_regions.truncate(args.max_regions);
                    report.regions_truncated = report.whitespace_gap_count
                        > report.whitespace_candidates.len()
                        || report.changed_token_region_count > report.changed_token_regions.len();
                    Some(report)
                }
                _ => None,
            };
            let diagnostic = comparison
                .is_none()
                .then(|| "unparsed input; inspect projection diagnostics".into());
            pairs.push(PairReport {
                before_projection: inputs[left].projection.clone(),
                after_projection: inputs[right].projection.clone(),
                comparison,
                diagnostic,
            });
        }
    }
    Ok(ScanReport {
        schema: "sfm:source_simplify_scan@2".into(),
        algorithm: simplify::ALGORITHM.into(),
        parser: crate::java_analysis::syntax::JAVA_PARSER_FINGERPRINT.into(),
        scope: "manifested_java_candidates_not_rewrite_or_semantic_or_oracle_proof".into(),
        file: args.file.clone(),
        catalog_sha256: catalog.catalog_sha256,
        feature_definitions_sha256: catalog.feature_definitions_sha256,
        all_inputs_parsed: parsed.iter().all(Option::is_some),
        distinct_sources_parsed: cache.len(),
        parsed_pair_count,
        pairs_with_whitespace_candidates,
        inputs,
        pairs,
        writes_performed: false,
        core_freshness_checked: false,
        all_feature_combinations_checked: false,
    })
}

fn load_java(
    catalog: &CoreCatalog,
    input: &mut ProjectionInput,
    cache: &mut BTreeMap<String, std::result::Result<Arc<ParsedJava>, String>>,
) -> Result<Arc<ParsedJava>> {
    let bytes = read(&catalog.repo_root, &input.path, simplify::MAX_SOURCE_BYTES)?;
    let hash = sha256(&bytes);
    input.source_sha256 = Some(hash.clone());
    let value = cache.entry(hash).or_insert_with(|| {
        String::from_utf8(bytes)
            .map_err(|error| error.to_string())
            .and_then(|source| {
                simplify::parse(source)
                    .map(Arc::new)
                    .map_err(|error| error.to_string())
            })
    });
    value.clone().map_err(|error| eyre::eyre!(error))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::core_features::FEATURE_DEFINITIONS_PATH;
    use crate::source_projection::projection_catalog::CATALOG_PATH;
    const FILE: &str = "src/main/java/A.java";

    fn fixture() -> (tempfile::TempDir, SimplifyScanArgs) {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path();
        fs::create_dir_all(root.join(FEATURE_DEFINITIONS_PATH).parent().unwrap()).unwrap();
        fs::write(root.join(FEATURE_DEFINITIONS_PATH), "{}").unwrap();
        fs::write(root.join(CATALOG_PATH), r#"{"a":{"minecraft_version":"1.19.2","environment":"release","features":[]},"b":{"minecraft_version":"1.19.2","environment":"release","features":[]}}"#).unwrap();
        let catalog = CoreCatalog::load(root, root).unwrap();
        for (key, text) in [
            ("a", "class A { A() {\n\n run(); } }"),
            ("b", "class A { A() {\n run(); } }"),
        ] {
            let project = root.join(catalog.catalog.project_dir(key).unwrap());
            fs::create_dir_all(project.join(FILE).parent().unwrap()).unwrap();
            fs::write(project.join(FILE), text).unwrap();
        }
        let args = SimplifyScanArgs {
            repo_root: root.to_path_buf(),
            file: FILE.into(),
            projection: Vec::new(),
            max_regions: 16,
        };
        (temp, args)
    }

    #[test]
    fn scans_manifested_sources_without_core_or_writes() {
        let (temp, args) = fixture();
        let before = fs::read(
            temp.path()
                .join("platform/minecraft/projections/a")
                .join(FILE),
        )
        .unwrap();
        let report = scan(&args, &CancellationToken::new(), temp.path()).unwrap();
        assert!(report.all_inputs_parsed);
        assert_eq!(report.parsed_pair_count, 1);
        assert_eq!(
            report.pairs[0].comparison.as_ref().unwrap().classification,
            "whitespace_only"
        );
        assert!(!report.writes_performed && !report.core_freshness_checked);
        assert_eq!(
            before,
            fs::read(
                temp.path()
                    .join("platform/minecraft/projections/a")
                    .join(FILE)
            )
            .unwrap()
        );
    }

    #[test]
    fn disk_edits_are_compared_but_missing_inputs_are_not_parsed() {
        let (temp, args) = fixture();
        let root = temp.path().join("platform/minecraft/projections/a");
        fs::write(root.join(FILE), "class Edited {}").unwrap();
        let report = scan(&args, &CancellationToken::new(), temp.path()).unwrap();
        assert!(report.all_inputs_parsed);
        assert_eq!(report.parsed_pair_count, 1);
        assert_eq!(
            report.pairs[0].comparison.as_ref().unwrap().classification,
            "code_or_comment_change"
        );
        assert!(!report.core_freshness_checked);
        fs::remove_file(root.join(FILE)).unwrap();
        let report = scan(&args, &CancellationToken::new(), temp.path()).unwrap();
        assert!(!report.all_inputs_parsed);
        assert_eq!(report.parsed_pair_count, 0);
        assert!(report.inputs[0].diagnostic.is_some());
    }

    #[test]
    fn unsafe_paths_and_duplicate_or_unknown_contexts_are_rejected() {
        let (temp, mut args) = fixture();
        args.file = "src/../../secret.java".into();
        assert!(scan(&args, &CancellationToken::new(), temp.path()).is_err());
        args.file = FILE.into();
        args.projection = vec!["a".into(), "a".into()];
        assert!(scan(&args, &CancellationToken::new(), temp.path()).is_err());
        args.projection = vec!["a".into(), "unknown".into()];
        assert!(scan(&args, &CancellationToken::new(), temp.path()).is_err());
    }

    #[test]
    fn command_parses_repeatable_projection_selection() {
        let cli = figue::from_slice::<crate::cli::Cli>(&[
            "source",
            "simplify",
            "scan",
            "--repo-root",
            ".",
            "--file",
            FILE,
            "--projection",
            "a",
            "--projection",
            "b",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let crate::cli::Command::Source(super::super::SourceArgs {
            command:
                super::super::SourceCommand::Simplify(SimplifyArgs {
                    command: SimplifyCommand::Scan(args),
                }),
        }) = cli.command
        else {
            panic!("wrong command");
        };
        assert_eq!(args.projection, ["a", "b"]);
        assert_eq!(args.max_regions, 16);
    }
}
