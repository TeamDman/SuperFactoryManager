//! One-time version-only authoring migration, never a production source route.
//!
//! The fixed ledger binds twenty release/development Git witnesses. Eligibility
//! is independently re-derived: each target must have identical release/dev
//! bytes and membership, and at least two raw blobs must exist across targets.
//! Java becomes genuinely shared line templates; bounded resource alternatives
//! become exact core-owned assets selected by target. Preview is read-only.
//! Exactly 43 reviewed Java paths opt into an explicit LF/final-newline policy;
//! their raw witnesses remain pinned and separately verified.

use super::candidate_lock::checked_directory;
use super::candidate_lock::checked_file;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::InputPredicate;
use super::core_inputs::InputVariant;
use super::core_seed::CoreSeedLedger;
use super::frozen_release::validate_git_commit_root;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::projection_catalog::validate_projection_key;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::variant_consolidation::ConsolidationOptions;
use super::variant_consolidation::SourceVariant;
use super::variant_consolidation::consolidate;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use sha1::Digest as _;
use sha1::Sha1;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::BufRead as _;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Path;
use std::path::PathBuf;
use std::process::Stdio;

pub const DEFAULT_VERSION_SEED_LEDGER: &str = "docs/tasks/sfm-core-version-seed.json";
const SCHEMA: &str = "sfm:core-version-seed@1";
const CHECKPOINT: &str = "f3ff2f6425434f36c7c680fa909c977b158e1860";
const ITEM_PATH: &str = "src/main/java/ca/teamdman/sfm/common/resourcetype/ItemResourceType.java";
const MAX_LEDGER_BYTES: u64 = 2 * 1024 * 1024;
const MAX_FILE_BYTES: u64 = 2 * 1024 * 1024;
const MAX_TOTAL_BYTES: u64 = 64 * 1024 * 1024;
const EXPECTED_FILES: usize = 472;
const NORMALIZATION_POLICY: &str = "sfm:java_lf_final_newline@1";
const EXPECTED_NORMALIZED_FILES: usize = 43;

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct CoreVersionSeedLedger {
    pub schema: String,
    pub source_checkpoint: String,
    pub context_commits: BTreeMap<String, String>,
    pub files: BTreeMap<String, CoreVersionSeedFile>,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct CoreVersionSeedFile {
    pub disposition: VersionSeedDisposition,
    /// Every supported target appears, including explicit absent witnesses.
    pub target_blobs: BTreeMap<String, Option<String>>,
    pub canonical_raw_blob: Option<String>,
    /// Only the separately authored `ItemResourceType` is retained, not replaced.
    pub retained_core_sha256: Option<String>,
    #[facet(default)]
    pub normalization: Option<ReviewedJavaNormalization>,
    pub reason: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReviewedJavaNormalization {
    pub policy: String,
    /// The raw-byte obstruction reviewed before normalization was approved.
    pub raw_blocker: VersionSeedDisposition,
    /// Exact evidence for each distinct raw Git blob, including no-op variants.
    pub raw_blobs: BTreeMap<String, JavaNormalizationWitness>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct JavaNormalizationWitness {
    pub raw_sha256: String,
    pub normalized_sha256: String,
    pub crlf_count: usize,
    pub final_lf_added: bool,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum VersionSeedDisposition {
    JavaTemplate,
    JavaNormalizedTemplate,
    ExactAssets,
    NoSharedRawLineAnchor,
    UnterminatedChangedEof,
    RetainExistingItem,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum VersionSeedInputStatus {
    WouldCreate,
    ExistingMatches,
    Created,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct VersionSeedInputReport {
    pub input: String,
    pub sha256: String,
    pub status: VersionSeedInputStatus,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct VersionSeedFileReport {
    pub disposition: VersionSeedDisposition,
    pub reason: String,
    pub normalization: Option<ReviewedJavaNormalization>,
    pub targets: Vec<String>,
    pub shared_lines: usize,
    pub conditional_regions: usize,
    pub inputs: Vec<VersionSeedInputReport>,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct CoreVersionSeedReport {
    pub schema: String,
    pub ledger_path: String,
    pub ledger_sha256: String,
    pub apply: bool,
    pub seed_only: bool,
    pub production_historical_lookup: bool,
    pub verified_files: usize,
    pub java_templates: usize,
    pub raw_java_templates: usize,
    pub normalized_java_templates: usize,
    pub exact_asset_outputs: usize,
    pub blocked_files: usize,
    pub retained_files: usize,
    pub written_inputs: usize,
    pub total_input_bytes: u64,
    /// Suggestions only: the caller must explicitly review/install metadata.
    pub source_rules: BTreeMap<String, Vec<InputVariant>>,
    pub files: BTreeMap<String, VersionSeedFileReport>,
}

impl CoreVersionSeedReport {
    /// Encode portable migration evidence without historic source contents.
    ///
    /// # Errors
    /// Returns an error if Facet cannot encode the typed report.
    pub fn to_json(&self) -> Result<String> {
        Ok(format!("{}\n", facet_json::to_string_pretty(self)?))
    }
}

impl CoreVersionSeedLedger {
    /// Parse the fixed, bounded review ledger; this does not access Git or write.
    ///
    /// # Errors
    /// Rejects changed witnesses/scope, duplicate/unknown fields, unsafe paths,
    /// invalid hashes, incomplete membership, or inconsistent dispositions.
    pub fn from_json(input: &str) -> Result<Self> {
        ensure!(
            input.len() as u64 <= MAX_LEDGER_BYTES,
            "version seed ledger exceeds its limit"
        );
        reject_duplicate_keys(input)?;
        let ledger: Self = facet_json::from_str(input).wrap_err("invalid version seed ledger")?;
        ledger.validate()?;
        Ok(ledger)
    }

    fn validate(&self) -> Result<()> {
        let prior = CoreSeedLedger::from_json(include_str!(
            "../../../../../docs/tasks/sfm-core-shared-seed.json"
        ))?;
        ensure!(
            self.schema == SCHEMA
                && self.source_checkpoint == CHECKPOINT
                && self.context_commits == prior.context_commits,
            "version seed differs from the exact reviewed twenty-context matrix"
        );
        ensure!(
            self.files.len() == EXPECTED_FILES,
            "version seed requires all 472 reviewed paths"
        );
        let targets = supported_targets();
        let mut aliases = BTreeSet::new();
        for (path, row) in &self.files {
            validate_source_path(path)?;
            ensure!(
                aliases.insert(path.to_ascii_lowercase()),
                "case-aliased version seed path"
            );
            ensure!(
                row.target_blobs.keys().cloned().collect::<BTreeSet<_>>() == targets,
                "version seed requires explicit membership for all targets at `{path}`"
            );
            let blobs = row.target_blobs.values().flatten().collect::<BTreeSet<_>>();
            ensure!(
                blobs.len() > 1,
                "version seed path has no raw version variation: `{path}`"
            );
            for blob in blobs {
                validate_sha1(blob)?;
            }
            ensure!(
                row.canonical_raw_blob == row.target_blobs["1.19.2"],
                "canonical raw witness differs at `{path}`"
            );
            ensure!(
                !row.reason.trim().is_empty(),
                "version seed requires an explicit review reason"
            );
            match row.disposition {
                VersionSeedDisposition::ExactAssets => {
                    ensure!(is_resource(path), "asset row must be a resource");
                }
                VersionSeedDisposition::RetainExistingItem => {
                    ensure!(
                        path == ITEM_PATH,
                        "only the reviewed ItemResourceType can be retained"
                    );
                    validate_sha256(row.retained_core_sha256.as_deref().ok_or_else(|| {
                        eyre::eyre!("retained template lacks its reviewed hash")
                    })?)?;
                }
                _ => ensure!(is_java(path), "Java seed disposition must be a Java path"),
            }
            ensure!(
                row.retained_core_sha256.is_none()
                    || row.disposition == VersionSeedDisposition::RetainExistingItem,
                "unexpected retained-template hash at `{path}`"
            );
            validate_normalization_review(path, row)?;
        }
        ensure!(
            self.files
                .values()
                .filter(|row| row.normalization.is_some())
                .count()
                == EXPECTED_NORMALIZED_FILES,
            "Java normalization is restricted to the 43 reviewed formerly blocked paths"
        );
        ensure!(
            self.files
                .get(ITEM_PATH)
                .is_some_and(|row| row.disposition == VersionSeedDisposition::RetainExistingItem),
            "the authored ItemResourceType must be explicitly retained"
        );
        Ok(())
    }
}

fn validate_normalization_review(path: &str, row: &CoreVersionSeedFile) -> Result<()> {
    ensure!(
        (row.disposition == VersionSeedDisposition::JavaNormalizedTemplate)
            == row.normalization.is_some(),
        "normalization must be explicit and exclusive to normalized Java at `{path}`"
    );
    if let Some(review) = &row.normalization {
        ensure!(
            is_java(path) && review.policy == NORMALIZATION_POLICY,
            "unsupported Java normalization policy at `{path}`"
        );
        ensure!(
            matches!(
                review.raw_blocker,
                VersionSeedDisposition::NoSharedRawLineAnchor
                    | VersionSeedDisposition::UnterminatedChangedEof
            ),
            "Java normalization lacks its reviewed raw-byte blocker at `{path}`"
        );
        ensure!(
            review.raw_blobs.keys().cloned().collect::<BTreeSet<_>>()
                == row.target_blobs.values().flatten().cloned().collect(),
            "normalization evidence must cover exactly every raw witness at `{path}`"
        );
        for evidence in review.raw_blobs.values() {
            validate_sha256(&evidence.raw_sha256)?;
            validate_sha256(&evidence.normalized_sha256)?;
            ensure!(
                evidence.crlf_count as u64 <= MAX_FILE_BYTES / 2,
                "normalization evidence exceeds its byte limit"
            );
        }
    }
    Ok(())
}

#[derive(Clone, Debug)]
struct TreeEntry {
    blob: String,
    mode: String,
}

type WitnessTrees = BTreeMap<String, BTreeMap<String, TreeEntry>>;

#[derive(Default)]
struct PreparedSeed {
    bytes: BTreeMap<String, Vec<u8>>,
    existing: BTreeSet<String>,
    source_rules: BTreeMap<String, Vec<InputVariant>>,
    files: BTreeMap<String, VersionSeedFileReport>,
}

/// Preview, or explicitly apply, the fixed one-time version-only migration.
///
/// Preview independently re-derives eligibility from all pinned trees, reads
/// every raw blob, checks every legacy live path and destination, reconstructs
/// Java through the actual renderer, and reports explicit exclusions. Apply
/// creates only missing core inputs after the whole preflight. It never edits
/// metadata, projections, legacy sources, the retained `ItemResourceType` or an
/// existing differing destination. No runtime renderer reads this ledger.
///
/// # Errors
/// Rejects changed/unsafe witnesses, raw live drift, unexplained exclusions,
/// failed reconstruction, destination conflicts or concurrent creation. I/O
/// failure may leave already-created inputs; they are retained for inspection.
pub fn seed_version_core(
    repo_root: &Path,
    ledger_relative_path: &str,
    apply: bool,
) -> Result<CoreVersionSeedReport> {
    ensure!(
        ledger_relative_path == DEFAULT_VERSION_SEED_LEDGER,
        "version seed requires its fixed review ledger"
    );
    let candidate = if repo_root.is_absolute() {
        repo_root.to_path_buf()
    } else {
        std::env::current_dir()?.join(repo_root)
    };
    let root = checked_directory(&candidate)?;
    let core = checked_directory(&root.join(CORE_ROOT))?;
    let ledger_bytes = read_bounded(
        &checked_file(&root, ledger_relative_path)?,
        MAX_LEDGER_BYTES,
    )?;
    let ledger = CoreVersionSeedLedger::from_json(std::str::from_utf8(&ledger_bytes)?)?;
    let trees = read_witness_trees(&root, &ledger)?;
    verify_eligibility(&ledger, &trees)?;
    let blobs = read_raw_blobs(&root, &ledger)?;
    preflight_legacy_and_retained(&root, &core, &ledger)?;
    let mut prepared = prepare_inputs(&ledger, &blobs)?;
    prepared.existing = preflight_destinations(&core, &prepared.bytes)?;
    let mut report = make_report(&ledger_bytes, &prepared, apply);
    if apply {
        write_prepared(&core, &prepared)?;
        for file in report.files.values_mut() {
            for input in &mut file.inputs {
                if input.status == VersionSeedInputStatus::WouldCreate {
                    input.status = VersionSeedInputStatus::Created;
                    report.written_inputs += 1;
                }
            }
        }
    }
    Ok(report)
}

fn read_witness_trees(root: &Path, ledger: &CoreVersionSeedLedger) -> Result<WitnessTrees> {
    let mut trees = BTreeMap::new();
    for (context, commit) in &ledger.context_commits {
        validate_git_commit_root(root, commit)?;
        let output = frozen_git_command(root)
            .args([
                "ls-tree",
                "-r",
                "-z",
                "--full-tree",
                commit,
                "--",
                "platform/minecraft/src/",
            ])
            .output()
            .wrap_err("cannot read version seed witness tree")?;
        ensure!(
            output.status.success(),
            "cannot read version seed witness `{commit}`"
        );
        ensure!(
            output.stdout.len() as u64 <= MAX_TOTAL_BYTES,
            "version seed tree exceeds its limit"
        );
        let mut tree = BTreeMap::new();
        for record in output
            .stdout
            .split(|byte| *byte == 0)
            .filter(|record| !record.is_empty())
        {
            let text = std::str::from_utf8(record)?;
            let (header, raw_path) = text
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("invalid Git tree record"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[1] == "blob"
                    && matches!(fields[0], "100644" | "100755"),
                "version seed witness contains a nonregular source"
            );
            validate_sha1(fields[2])?;
            let path = raw_path
                .strip_prefix("platform/minecraft/")
                .ok_or_else(|| eyre::eyre!("Git witness escaped Minecraft sources"))?;
            validate_projection_key(path)?;
            ensure!(
                tree.insert(
                    path.to_owned(),
                    TreeEntry {
                        blob: fields[2].to_owned(),
                        mode: fields[0].to_owned()
                    }
                )
                .is_none(),
                "duplicate version witness path"
            );
        }
        trees.insert(context.clone(), tree);
    }
    Ok(trees)
}

fn verify_eligibility(ledger: &CoreVersionSeedLedger, trees: &WitnessTrees) -> Result<()> {
    let all_paths: BTreeSet<_> = trees
        .values()
        .flat_map(|tree| tree.keys().cloned())
        .collect();
    let mut candidates = BTreeSet::new();
    for path in all_paths {
        let mut seen = BTreeSet::new();
        let mut same = true;
        for (target, _) in SUPPORTED_TARGETS {
            let release = trees
                .get(&format!("release/{target}"))
                .and_then(|tree| tree.get(&path));
            let dev = trees
                .get(&format!("dev/{target}"))
                .and_then(|tree| tree.get(&path));
            if release.map(|entry| &entry.blob) != dev.map(|entry| &entry.blob) {
                same = false;
            }
            if let Some(entry) = release {
                seen.insert(entry.blob.clone());
            }
        }
        if same && seen.len() > 1 {
            candidates.insert(path);
        }
    }
    ensure!(
        candidates == ledger.files.keys().cloned().collect(),
        "version seed ledger is not the complete independently re-derived pure-version path set"
    );
    for (path, row) in &ledger.files {
        for (context, tree) in trees {
            let (_, target) = context
                .split_once('/')
                .ok_or_else(|| eyre::eyre!("invalid witness context"))?;
            let expected = row
                .target_blobs
                .get(target)
                .ok_or_else(|| eyre::eyre!("missing target witness"))?;
            let entry = tree.get(path);
            ensure!(
                entry.map(|entry| &entry.blob) == expected.as_ref(),
                "version witness differs at `{context}` / `{path}`"
            );
            ensure!(
                entry.is_none_or(|entry| entry.mode == "100644"),
                "version seed source has unsupported executable mode"
            );
        }
    }
    Ok(())
}

fn read_raw_blobs(
    root: &Path,
    ledger: &CoreVersionSeedLedger,
) -> Result<BTreeMap<String, Vec<u8>>> {
    let oids: BTreeSet<_> = ledger
        .files
        .values()
        .flat_map(|row| row.target_blobs.values().flatten().cloned())
        .collect();
    let mut child = frozen_git_command(root)
        .args(["cat-file", "--batch"])
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .wrap_err("cannot start frozen Git blob reader")?;
    let mut input = child
        .stdin
        .take()
        .ok_or_else(|| eyre::eyre!("missing Git input pipe"))?;
    let output = child
        .stdout
        .take()
        .ok_or_else(|| eyre::eyre!("missing Git output pipe"))?;
    let mut reader = std::io::BufReader::new(output);
    let result = read_blob_batch(&mut input, &mut reader, oids);
    drop(input);
    drop(reader);
    if result.is_err() {
        // Only our newly spawned, identified Git child is stopped on failure.
        let _ = child.kill();
        let _ = child.wait();
        return result;
    }
    ensure!(child.wait()?.success(), "frozen Git blob reader failed");
    result
}

fn read_blob_batch(
    input: &mut impl std::io::Write,
    reader: &mut impl std::io::BufRead,
    oids: BTreeSet<String>,
) -> Result<BTreeMap<String, Vec<u8>>> {
    let mut blobs = BTreeMap::new();
    let mut total = 0_u64;
    for oid in oids {
        writeln!(input, "{oid}")?;
        input.flush()?;
        let mut header = String::new();
        (&mut *reader).take(256).read_line(&mut header)?;
        let fields = header.split_whitespace().collect::<Vec<_>>();
        ensure!(
            header.ends_with('\n') && fields.len() == 3 && fields[0] == oid && fields[1] == "blob",
            "invalid frozen Git blob response"
        );
        let size: u64 = fields[2].parse()?;
        total = total
            .checked_add(size)
            .ok_or_else(|| eyre::eyre!("version seed size overflow"))?;
        ensure!(
            size <= MAX_FILE_BYTES && total <= MAX_TOTAL_BYTES,
            "version seed raw blobs exceed their limits"
        );
        let mut bytes = vec![0; usize::try_from(size)?];
        reader.read_exact(&mut bytes)?;
        let mut separator = [0_u8];
        reader.read_exact(&mut separator)?;
        ensure!(
            separator[0] == b'\n' && git_blob(&bytes) == oid,
            "raw Git blob verification failed"
        );
        blobs.insert(oid, bytes);
    }
    Ok(blobs)
}

fn preflight_legacy_and_retained(
    root: &Path,
    core: &Path,
    ledger: &CoreVersionSeedLedger,
) -> Result<()> {
    for (path, row) in &ledger.files {
        let live = optional_file(root, &format!("platform/minecraft/{path}"))?;
        match (&row.canonical_raw_blob, live) {
            (Some(expected), Some(file)) => ensure!(
                git_blob(&read_bounded(&file, MAX_FILE_BYTES)?) == *expected,
                "legacy canonical raw bytes changed at `{path}`"
            ),
            (None, None) => {}
            _ => eyre::bail!("legacy canonical presence changed at `{path}`"),
        }
        if let Some(expected) = &row.retained_core_sha256 {
            let file = optional_file(core, path)?
                .ok_or_else(|| eyre::eyre!("retained ItemResourceType is missing"))?;
            ensure!(
                sha256(&read_bounded(&file, MAX_FILE_BYTES)?) == *expected,
                "retained authored ItemResourceType changed from its seed review"
            );
        }
    }
    Ok(())
}

fn prepare_inputs(
    ledger: &CoreVersionSeedLedger,
    blobs: &BTreeMap<String, Vec<u8>>,
) -> Result<PreparedSeed> {
    let mut prepared = PreparedSeed::default();
    for (path, row) in &ledger.files {
        let targets: Vec<_> = row
            .target_blobs
            .iter()
            .filter(|(_, blob)| blob.is_some())
            .map(|(target, _)| target.clone())
            .collect();
        let mut report = VersionSeedFileReport {
            disposition: row.disposition,
            reason: row.reason.clone(),
            normalization: row.normalization.clone(),
            targets,
            shared_lines: 0,
            conditional_regions: 0,
            inputs: Vec::new(),
        };
        match row.disposition {
            VersionSeedDisposition::JavaTemplate
            | VersionSeedDisposition::JavaNormalizedTemplate => {
                prepare_java(path, row, blobs, &mut prepared, &mut report)?;
            }
            VersionSeedDisposition::ExactAssets => {
                prepare_assets(path, row, blobs, &mut prepared, &mut report)?;
            }
            VersionSeedDisposition::NoSharedRawLineAnchor => ensure!(
                no_common_line(row, blobs)?,
                "reviewed no-anchor blocker is no longer present at `{path}`"
            ),
            VersionSeedDisposition::UnterminatedChangedEof => ensure!(
                changed_unterminated_eof(row, blobs)?,
                "reviewed EOF blocker is no longer present at `{path}`"
            ),
            VersionSeedDisposition::RetainExistingItem => {}
        }
        prepared.files.insert(path.clone(), report);
    }
    ensure!(
        prepared
            .bytes
            .values()
            .try_fold(0_u64, |total, bytes| total.checked_add(bytes.len() as u64))
            .is_some_and(|total| total <= MAX_TOTAL_BYTES),
        "version seed core inputs exceed their total limit"
    );
    Ok(prepared)
}

fn prepare_java(
    path: &str,
    row: &CoreVersionSeedFile,
    blobs: &BTreeMap<String, Vec<u8>>,
    prepared: &mut PreparedSeed,
    report: &mut VersionSeedFileReport,
) -> Result<()> {
    let normalized = row
        .normalization
        .as_ref()
        .map(|review| verified_normalized_blobs(path, row, review, blobs))
        .transpose()?;
    let effective_blobs = normalized.as_ref().unwrap_or(blobs);
    let mut variants = Vec::new();
    for (target, version) in SUPPORTED_TARGETS {
        let source = row.target_blobs[target]
            .as_ref()
            .map(|blob| effective_blobs[blob].clone());
        for lane in ["release", "dev"] {
            variants.push(SourceVariant {
                id: format!("{lane}/{target}"),
                minecraft_version: version.to_owned(),
                features: BTreeMap::new(),
                source: source.clone(),
            });
        }
    }
    let consolidated =
        consolidate(&variants, &ConsolidationOptions::default()).wrap_err_with(|| {
            format!("version-only Java consolidation requires explicit review at `{path}`")
        })?;
    let template = consolidated
        .template
        .ok_or_else(|| eyre::eyre!("present Java witnesses produced no template"))?;
    for variant in &variants {
        ensure!(
            consolidated
                .membership
                .includes(&variant.minecraft_version, &variant.features)?
                == variant.source.is_some(),
            "version-only Java membership reconstruction failed"
        );
    }
    report.shared_lines = consolidated.statistics.shared_lines;
    report.conditional_regions = consolidated.statistics.conditional_regions;
    record_input(prepared, report, path.to_owned(), template.into_bytes())?;
    if report.targets.len() != SUPPORTED_TARGETS.len() {
        prepared.source_rules.insert(
            path.to_owned(),
            vec![InputVariant {
                input: path.to_owned(),
                when: InputPredicate {
                    targets: report.targets.clone(),
                    ..InputPredicate::default()
                },
                template: false,
            }],
        );
    }
    Ok(())
}

fn normalize_java_bytes(raw: &[u8]) -> Result<(Vec<u8>, JavaNormalizationWitness)> {
    let source = std::str::from_utf8(raw).wrap_err("Java normalization requires exact UTF-8")?;
    ensure!(
        !source.starts_with('\u{feff}'),
        "Java normalization does not accept or remove a BOM"
    );
    let crlf_count = source
        .as_bytes()
        .windows(2)
        .filter(|pair| *pair == b"\r\n")
        .count();
    ensure!(
        !source.replace("\r\n", "").contains('\r'),
        "Java normalization refuses lone CR terminators"
    );
    let mut normalized = source.replace("\r\n", "\n");
    let final_lf_added = !normalized.ends_with('\n');
    if final_lf_added {
        normalized.push('\n');
    }
    let evidence = JavaNormalizationWitness {
        raw_sha256: sha256(raw),
        normalized_sha256: sha256(normalized.as_bytes()),
        crlf_count,
        final_lf_added,
    };
    Ok((normalized.into_bytes(), evidence))
}

fn verified_normalized_blobs(
    path: &str,
    row: &CoreVersionSeedFile,
    review: &ReviewedJavaNormalization,
    blobs: &BTreeMap<String, Vec<u8>>,
) -> Result<BTreeMap<String, Vec<u8>>> {
    validate_normalization_review(path, row)?;
    let blocker_present = match review.raw_blocker {
        VersionSeedDisposition::NoSharedRawLineAnchor => no_common_line(row, blobs)?,
        VersionSeedDisposition::UnterminatedChangedEof => changed_unterminated_eof(row, blobs)?,
        _ => false,
    };
    ensure!(
        blocker_present,
        "normalization would extend beyond its reviewed raw blocker at `{path}`"
    );
    review
        .raw_blobs
        .iter()
        .map(|(oid, expected)| {
            let raw = blobs
                .get(oid)
                .ok_or_else(|| eyre::eyre!("missing raw normalization witness"))?;
            ensure!(
                git_blob(raw) == *oid,
                "normalization raw Git identity differs"
            );
            let (normalized, actual) = normalize_java_bytes(raw)?;
            ensure!(
                actual == *expected,
                "raw/normalized hash or newline evidence changed at `{path}` / `{oid}`"
            );
            Ok((oid.clone(), normalized))
        })
        .collect()
}

fn prepare_assets(
    path: &str,
    row: &CoreVersionSeedFile,
    blobs: &BTreeMap<String, Vec<u8>>,
    prepared: &mut PreparedSeed,
    report: &mut VersionSeedFileReport,
) -> Result<()> {
    let mut groups = BTreeMap::<&str, Vec<String>>::new();
    for (target, oid) in &row.target_blobs {
        if let Some(oid) = oid {
            groups.entry(oid).or_default().push(target.clone());
        }
    }
    let mut rules = Vec::new();
    for (oid, targets) in groups {
        let source = &blobs[oid];
        let digest = sha256(source);
        let input = format!("assets/versioned/{}/{path}", &digest[7..]);
        record_input(prepared, report, input.clone(), source.clone())?;
        rules.push(InputVariant {
            input,
            when: InputPredicate {
                targets,
                ..InputPredicate::default()
            },
            template: false,
        });
    }
    for (target, expected) in &row.target_blobs {
        let matches: Vec<_> = rules
            .iter()
            .filter(|rule| rule.when.targets.contains(target))
            .collect();
        ensure!(
            matches.len() == usize::from(expected.is_some()),
            "asset membership reconstruction failed"
        );
        if let Some(oid) = expected {
            let selected = matches
                .first()
                .ok_or_else(|| eyre::eyre!("asset selection is absent"))?;
            ensure!(
                prepared.bytes[&selected.input] == blobs[oid],
                "asset byte reconstruction failed"
            );
        }
    }
    prepared.source_rules.insert(path.to_owned(), rules);
    Ok(())
}

fn record_input(
    prepared: &mut PreparedSeed,
    report: &mut VersionSeedFileReport,
    input: String,
    bytes: Vec<u8>,
) -> Result<()> {
    validate_projection_key(&input)?;
    ensure!(
        bytes.len() as u64 <= MAX_FILE_BYTES,
        "version seed emitted input exceeds its limit"
    );
    report.inputs.push(VersionSeedInputReport {
        input: input.clone(),
        sha256: sha256(&bytes),
        status: VersionSeedInputStatus::WouldCreate,
    });
    ensure!(
        prepared.bytes.insert(input, bytes).is_none(),
        "duplicate authored version seed input"
    );
    Ok(())
}

fn source_lines<'a>(
    row: &CoreVersionSeedFile,
    blobs: &'a BTreeMap<String, Vec<u8>>,
) -> Result<Vec<Vec<&'a str>>> {
    row.target_blobs
        .values()
        .flatten()
        .collect::<BTreeSet<_>>()
        .into_iter()
        .map(|oid| {
            Ok(std::str::from_utf8(&blobs[oid])?
                .split_inclusive('\n')
                .collect())
        })
        .collect()
}

fn no_common_line(row: &CoreVersionSeedFile, blobs: &BTreeMap<String, Vec<u8>>) -> Result<bool> {
    let lines = source_lines(row, blobs)?;
    let mut common: BTreeSet<_> = lines
        .first()
        .ok_or_else(|| eyre::eyre!("no present source"))?
        .iter()
        .copied()
        .collect();
    for source in lines.iter().skip(1) {
        let set = source.iter().copied().collect();
        common = common.intersection(&set).copied().collect();
    }
    Ok(common.is_empty())
}

fn changed_unterminated_eof(
    row: &CoreVersionSeedFile,
    blobs: &BTreeMap<String, Vec<u8>>,
) -> Result<bool> {
    let lines = source_lines(row, blobs)?;
    let tails: BTreeSet<_> = lines
        .iter()
        .filter_map(|source| source.last().copied())
        .collect();
    Ok(tails.len() > 1 && tails.iter().any(|tail| !tail.ends_with('\n')))
}

fn make_report(ledger_bytes: &[u8], prepared: &PreparedSeed, apply: bool) -> CoreVersionSeedReport {
    let mut files = prepared.files.clone();
    for file in files.values_mut() {
        for input in &mut file.inputs {
            if prepared.existing.contains(&input.input) {
                input.status = VersionSeedInputStatus::ExistingMatches;
            }
        }
    }
    let count = |disposition| {
        files
            .values()
            .filter(|file| file.disposition == disposition)
            .count()
    };
    CoreVersionSeedReport {
        schema: "sfm:core-version-seed-report@1".to_owned(),
        ledger_path: DEFAULT_VERSION_SEED_LEDGER.to_owned(),
        ledger_sha256: sha256(ledger_bytes),
        apply,
        seed_only: true,
        production_historical_lookup: false,
        verified_files: files.len(),
        java_templates: count(VersionSeedDisposition::JavaTemplate)
            + count(VersionSeedDisposition::JavaNormalizedTemplate),
        raw_java_templates: count(VersionSeedDisposition::JavaTemplate),
        normalized_java_templates: count(VersionSeedDisposition::JavaNormalizedTemplate),
        exact_asset_outputs: count(VersionSeedDisposition::ExactAssets),
        blocked_files: count(VersionSeedDisposition::NoSharedRawLineAnchor)
            + count(VersionSeedDisposition::UnterminatedChangedEof),
        retained_files: count(VersionSeedDisposition::RetainExistingItem),
        written_inputs: 0,
        total_input_bytes: prepared
            .bytes
            .values()
            .map(|bytes| bytes.len() as u64)
            .sum(),
        source_rules: prepared.source_rules.clone(),
        files,
    }
}

fn preflight_destinations(
    core: &Path,
    bytes: &BTreeMap<String, Vec<u8>>,
) -> Result<BTreeSet<String>> {
    let mut existing = BTreeSet::new();
    for (path, wanted) in bytes {
        if let Some(file) = optional_file(core, path)? {
            ensure!(
                read_bounded(&file, MAX_FILE_BYTES)? == *wanted,
                "core destination differs at `{path}`; version seed never overwrites"
            );
            existing.insert(path.clone());
        }
    }
    Ok(existing)
}

fn write_prepared(core: &Path, prepared: &PreparedSeed) -> Result<()> {
    ensure!(
        preflight_destinations(core, &prepared.bytes)? == prepared.existing,
        "core destination presence changed after version seed preview"
    );
    for (path, bytes) in &prepared.bytes {
        if prepared.existing.contains(path) {
            continue;
        }
        create_checked_parents(core, path)?;
        ensure!(
            optional_file(core, path)?.is_none(),
            "core destination appeared during apply"
        );
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(core.join(path))
            .wrap_err_with(|| format!("cannot create version core input `{path}`"))?;
        file.write_all(bytes).wrap_err_with(|| {
            format!("cannot write `{path}`; created inputs retained for inspection")
        })?;
        file.sync_all()?;
    }
    Ok(())
}

fn optional_file(root: &Path, relative: &str) -> Result<Option<PathBuf>> {
    validate_projection_key(relative)?;
    let parts: Vec<_> = relative.split('/').collect();
    let mut current = root.to_path_buf();
    for (index, part) in parts.iter().enumerate() {
        current.push(part);
        match fs::symlink_metadata(&current) {
            Ok(metadata) => ensure!(
                !is_reparse(&metadata)
                    && if index + 1 == parts.len() {
                        metadata.is_file()
                    } else {
                        metadata.is_dir()
                    },
                "version seed path has a reparse point or wrong type at `{relative}`"
            ),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
            Err(error) => return Err(error).wrap_err("cannot inspect version seed path"),
        }
    }
    Ok(Some(current))
}

fn create_checked_parents(root: &Path, relative: &str) -> Result<()> {
    validate_projection_key(relative)?;
    let parts: Vec<_> = relative.split('/').collect();
    let mut current = root.to_path_buf();
    for part in parts.iter().take(parts.len().saturating_sub(1)) {
        current.push(part);
        match fs::create_dir(&current) {
            Ok(()) => {}
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
            Err(error) => return Err(error).wrap_err("cannot create version seed parent"),
        }
        checked_directory(&current)?;
    }
    Ok(())
}

fn is_reparse(metadata: &fs::Metadata) -> bool {
    if metadata.file_type().is_symlink() {
        return true;
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt as _;
        metadata.file_attributes() & 0x0000_0400 != 0
    }
    #[cfg(not(windows))]
    {
        false
    }
}

fn read_bounded(path: &Path, maximum: u64) -> Result<Vec<u8>> {
    let file = fs::File::open(path)?;
    ensure!(
        file.metadata()?.len() <= maximum,
        "version seed input exceeds its limit"
    );
    let mut bytes = Vec::new();
    file.take(maximum + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= maximum,
        "version seed input grew beyond its limit"
    );
    Ok(bytes)
}

fn git_blob(bytes: &[u8]) -> String {
    let mut hash = Sha1::new();
    hash.update(format!("blob {}\0", bytes.len()).as_bytes());
    hash.update(bytes);
    format!("{:x}", hash.finalize())
}

fn supported_targets() -> BTreeSet<String> {
    SUPPORTED_TARGETS
        .iter()
        .map(|(target, _)| (*target).to_owned())
        .collect()
}

fn is_java(path: &str) -> bool {
    [
        "src/main/java/",
        "src/gametest/java/",
        "src/test/java/",
        "src/datagen/java/",
    ]
    .iter()
    .any(|prefix| path.starts_with(prefix))
        && Path::new(path)
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("java"))
}

fn is_resource(path: &str) -> bool {
    path.starts_with("src/main/resources/") || path.starts_with("src/generated/resources/")
}

fn validate_source_path(path: &str) -> Result<()> {
    validate_projection_key(path)?;
    ensure!(
        is_java(path) || is_resource(path),
        "version seed owns only the reviewed Java/resource sources"
    );
    Ok(())
}

fn validate_sha1(value: &str) -> Result<()> {
    ensure!(
        value.len() == 40
            && value
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "version seed requires a lowercase exact Git SHA-1"
    );
    Ok(())
}

fn validate_sha256(value: &str) -> Result<()> {
    ensure!(
        value.len() == 71
            && value.starts_with("sha256:")
            && value[7..]
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "version seed requires a lowercase SHA-256"
    );
    Ok(())
}

fn reject_duplicate_keys(input: &str) -> Result<()> {
    let bytes = input.as_bytes();
    let mut position = 0;
    let mut containers: Vec<Option<BTreeSet<String>>> = Vec::new();
    while position < bytes.len() {
        match bytes[position] {
            b'{' => {
                containers.push(Some(BTreeSet::new()));
                position += 1;
            }
            b'[' => {
                containers.push(None);
                position += 1;
            }
            b'}' | b']' => {
                containers.pop();
                position += 1;
            }
            b'"' => {
                let start = position;
                position += 1;
                let mut closed = false;
                while position < bytes.len() {
                    match bytes[position] {
                        b'\\' => position = (position + 2).min(bytes.len()),
                        b'"' => {
                            position += 1;
                            closed = true;
                            break;
                        }
                        _ => position += 1,
                    }
                }
                let mut next = position;
                while next < bytes.len() && bytes[next].is_ascii_whitespace() {
                    next += 1;
                }
                if closed
                    && bytes.get(next) == Some(&b':')
                    && let Some(Some(keys)) = containers.last_mut()
                {
                    let key: String = facet_json::from_str(&input[start..position])?;
                    ensure!(keys.insert(key), "duplicate version seed ledger JSON key");
                }
            }
            _ => position += 1,
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    const LEDGER: &str = include_str!("../../../../../docs/tasks/sfm-core-version-seed.json");

    #[test]
    fn reviewed_ledger_captures_all_paths_and_explicit_normalization_scope() {
        let ledger = CoreVersionSeedLedger::from_json(LEDGER).unwrap();
        assert_eq!(ledger.files.len(), 472);
        assert_eq!(
            ledger
                .files
                .values()
                .filter(|row| is_java_path(row))
                .count(),
            363
        );
        assert_eq!(
            ledger
                .files
                .values()
                .filter(|row| row.disposition == VersionSeedDisposition::JavaTemplate)
                .count(),
            319
        );
        assert_eq!(
            ledger
                .files
                .values()
                .filter(|row| row.disposition == VersionSeedDisposition::ExactAssets)
                .count(),
            109
        );
        assert_eq!(
            ledger
                .files
                .values()
                .filter(|row| row.disposition == VersionSeedDisposition::JavaNormalizedTemplate)
                .count(),
            43
        );
        assert!(ledger.files.values().all(|row| !matches!(
            row.disposition,
            VersionSeedDisposition::NoSharedRawLineAnchor
                | VersionSeedDisposition::UnterminatedChangedEof
        )));
    }

    fn is_java_path(row: &CoreVersionSeedFile) -> bool {
        row.disposition != VersionSeedDisposition::ExactAssets
    }

    #[test]
    fn typed_ledger_refuses_unknown_duplicate_scope_and_membership_changes() {
        assert!(
            CoreVersionSeedLedger::from_json(&LEDGER.replacen('{', "{\"unknown\":true,", 1))
                .is_err()
        );
        assert!(
            CoreVersionSeedLedger::from_json(&LEDGER.replacen(
                "\"schema\":",
                "\"schema\":null,\"schema\":",
                1
            ))
            .is_err()
        );
        assert!(
            CoreVersionSeedLedger::from_json(&LEDGER.replacen(CHECKPOINT, &"0".repeat(40), 1))
                .is_err()
        );
        let mut ledger = CoreVersionSeedLedger::from_json(LEDGER).unwrap();
        ledger
            .files
            .values_mut()
            .next()
            .unwrap()
            .target_blobs
            .remove("1.19.2");
        assert!(ledger.validate().is_err());
        assert!(reject_duplicate_keys(r#"{"x":1,"\u0078":2}"#).is_err());
    }

    fn fixture_row(old: &[u8], new: &[u8]) -> (CoreVersionSeedFile, BTreeMap<String, Vec<u8>>) {
        let old_oid = git_blob(old);
        let new_oid = git_blob(new);
        (
            CoreVersionSeedFile {
                disposition: VersionSeedDisposition::JavaTemplate,
                target_blobs: SUPPORTED_TARGETS
                    .iter()
                    .map(|(target, _)| {
                        (
                            (*target).to_owned(),
                            Some(if *target == "26.1.2" {
                                new_oid.clone()
                            } else {
                                old_oid.clone()
                            }),
                        )
                    })
                    .collect(),
                canonical_raw_blob: Some(old_oid.clone()),
                retained_core_sha256: None,
                normalization: None,
                reason: "reviewed version imports".to_owned(),
            },
            BTreeMap::from([(old_oid, old.to_vec()), (new_oid, new.to_vec())]),
        )
    }

    #[test]
    fn version_java_uses_real_consolidation_and_propagates_common_edit() {
        let (row, blobs) = fixture_row(
            b"package test;\nimport forge.Handler;\nclass Example {}\n",
            b"package test;\nimport neo.Handler;\nclass Example {}\n",
        );
        let mut prepared = PreparedSeed::default();
        let mut report = VersionSeedFileReport {
            disposition: row.disposition,
            reason: row.reason.clone(),
            normalization: row.normalization.clone(),
            targets: supported_targets().into_iter().collect(),
            shared_lines: 0,
            conditional_regions: 0,
            inputs: Vec::new(),
        };
        let path = "src/main/java/test/Example.java";
        prepare_java(path, &row, &blobs, &mut prepared, &mut report).unwrap();
        let template = std::str::from_utf8(&prepared.bytes[path]).unwrap();
        assert!(template.contains("{% case minecraft_version %}"));
        assert_eq!(template.matches("class Example {}").count(), 1);
        assert!(report.shared_lines > 0);
        assert!(prepared.source_rules.is_empty());
        assert!(!template.contains("environment"));
        assert!(!template.contains("preset"));
        let edited = template.replace("class Example {}", "class Example { int commonEdit; }");
        for (target, version) in SUPPORTED_TARGETS {
            let context = super::super::context::ProjectionContext {
                minecraft_version: version.to_owned(),
                preset: "unused".to_owned(),
                environment: "unused".to_owned(),
                projection_key: "unused".to_owned(),
                features: BTreeMap::new(),
                targets: BTreeMap::new(),
            };
            let rendered = super::super::render_java_source(&edited, &context).unwrap();
            let expected = std::str::from_utf8(&blobs[row.target_blobs[target].as_ref().unwrap()])
                .unwrap()
                .replace("class Example {}", "class Example { int commonEdit; }");
            assert_eq!(rendered, expected);
        }
    }

    #[test]
    fn exact_assets_group_by_bytes_and_preserve_absence_and_binary_payloads() {
        let (mut row, blobs) = fixture_row(b"\0old\xff", b"\0new\xfe");
        row.disposition = VersionSeedDisposition::ExactAssets;
        row.target_blobs.insert("1.19.4".to_owned(), None);
        let mut prepared = PreparedSeed::default();
        let mut report = VersionSeedFileReport {
            disposition: row.disposition,
            reason: row.reason.clone(),
            normalization: row.normalization.clone(),
            targets: Vec::new(),
            shared_lines: 0,
            conditional_regions: 0,
            inputs: Vec::new(),
        };
        let path = "src/main/resources/test.png";
        prepare_assets(path, &row, &blobs, &mut prepared, &mut report).unwrap();
        assert_eq!(prepared.bytes.len(), 2);
        assert!(
            prepared
                .bytes
                .keys()
                .all(|input| input.starts_with("assets/versioned/"))
        );
        assert!(
            prepared.source_rules[path]
                .iter()
                .all(|rule| !rule.template && !rule.when.targets.contains(&"1.19.4".to_owned()))
        );
        assert!(prepared.bytes.values().any(|source| source == b"\0old\xff"));
    }

    #[test]
    fn raw_line_and_eof_blockers_are_not_silently_normalized() {
        let (row, blobs) = fixture_row(b"class Example {\r\n}\r\n", b"class Example {\n}\n");
        assert!(no_common_line(&row, &blobs).unwrap());
        let (row, blobs) = fixture_row(b"class Example {\n}", b"class Example {\n}\n");
        assert!(changed_unterminated_eof(&row, &blobs).unwrap());
    }

    fn reviewed_normalized_fixture() -> (CoreVersionSeedFile, BTreeMap<String, Vec<u8>>) {
        let (mut row, blobs) = fixture_row(
            b"package test;\r\nimport forge.Handler;\r\nclass Example { int common; }\r\n",
            b"package test;\nimport neo.Handler;\nclass Example { int common; }\n",
        );
        row.disposition = VersionSeedDisposition::JavaNormalizedTemplate;
        row.normalization = Some(ReviewedJavaNormalization {
            policy: NORMALIZATION_POLICY.to_owned(),
            raw_blocker: VersionSeedDisposition::NoSharedRawLineAnchor,
            raw_blobs: blobs
                .iter()
                .map(|(oid, bytes)| (oid.clone(), normalize_java_bytes(bytes).unwrap().1))
                .collect(),
        });
        (row, blobs)
    }

    #[test]
    fn explicit_java_normalization_only_changes_crlf_and_missing_final_lf() {
        let raw = b"package test;\r\n\tclass Example { }  ";
        let (normalized, evidence) = normalize_java_bytes(raw).unwrap();
        assert_eq!(normalized, b"package test;\n\tclass Example { }  \n");
        assert_eq!(evidence.crlf_count, 1);
        assert!(evidence.final_lf_added);
        assert_eq!(evidence.raw_sha256, sha256(raw));
        assert_eq!(evidence.normalized_sha256, sha256(&normalized));
        let (unchanged, no_op) = normalize_java_bytes(&normalized).unwrap();
        assert_eq!(unchanged, normalized);
        assert_eq!(no_op.crlf_count, 0);
        assert!(!no_op.final_lf_added);
        assert_eq!(no_op.raw_sha256, no_op.normalized_sha256);
        assert!(normalize_java_bytes(b"\xef\xbb\xbfclass Example {}\n").is_err());
        assert!(normalize_java_bytes(b"class Example {}\r").is_err());
        assert!(normalize_java_bytes(b"\xff").is_err());
    }

    #[test]
    fn normalization_rejects_tampered_hash_counts_policy_and_witness_coverage() {
        let path = "src/main/java/Example.java";
        for failure in 0..7 {
            let (mut row, blobs) = reviewed_normalized_fixture();
            let review = row.normalization.as_mut().unwrap();
            match failure {
                0 => review.policy = "sfm:trim_and_reindent@1".to_owned(),
                1 => {
                    review.raw_blobs.values_mut().next().unwrap().raw_sha256 =
                        format!("sha256:{}", "0".repeat(64))
                }
                2 => {
                    review
                        .raw_blobs
                        .values_mut()
                        .next()
                        .unwrap()
                        .normalized_sha256 = format!("sha256:{}", "0".repeat(64))
                }
                3 => review.raw_blobs.values_mut().next().unwrap().crlf_count += 1,
                4 => {
                    let evidence = review.raw_blobs.values_mut().next().unwrap();
                    evidence.final_lf_added = !evidence.final_lf_added;
                }
                5 => {
                    let oid = review.raw_blobs.keys().next().unwrap().clone();
                    review.raw_blobs.remove(&oid);
                }
                6 => review.raw_blocker = VersionSeedDisposition::ExactAssets,
                _ => unreachable!(),
            }
            assert!(
                verified_normalized_blobs(path, &row, row.normalization.as_ref().unwrap(), &blobs)
                    .is_err(),
                "accepted tampered normalization evidence {failure}"
            );
        }
        let (mut row, blobs) = reviewed_normalized_fixture();
        row.disposition = VersionSeedDisposition::ExactAssets;
        assert!(
            verified_normalized_blobs(
                "src/main/resources/test.json",
                &row,
                row.normalization.as_ref().unwrap(),
                &blobs
            )
            .is_err()
        );
    }

    #[test]
    fn normalized_java_reconstructs_reviewed_witnesses_and_keeps_common_edits_shared() {
        let (row, blobs) = reviewed_normalized_fixture();
        let originals = blobs.clone();
        let path = "src/main/java/Example.java";
        let ledger = CoreVersionSeedLedger {
            schema: SCHEMA.to_owned(),
            source_checkpoint: CHECKPOINT.to_owned(),
            context_commits: BTreeMap::new(),
            files: BTreeMap::from([(path.to_owned(), row.clone())]),
        };
        let prepared = prepare_inputs(&ledger, &blobs).unwrap();
        assert_eq!(blobs, originals);
        let template = std::str::from_utf8(&prepared.bytes[path]).unwrap();
        assert_eq!(template.matches("class Example { int common; }").count(), 1);
        let edited = template.replace("int common;", "int commonEdit;");
        for (target, version) in SUPPORTED_TARGETS {
            let context = super::super::context::ProjectionContext {
                minecraft_version: version.to_owned(),
                preset: "unused".to_owned(),
                environment: "unused".to_owned(),
                projection_key: "unused".to_owned(),
                features: BTreeMap::new(),
                targets: BTreeMap::new(),
            };
            let source = &blobs[row.target_blobs[target].as_ref().unwrap()];
            let expected = normalize_java_bytes(source).unwrap().0;
            assert_eq!(
                super::super::render_java_source(template, &context)
                    .unwrap()
                    .as_bytes(),
                expected
            );
            assert_eq!(
                super::super::render_java_source(&edited, &context).unwrap(),
                std::str::from_utf8(&expected)
                    .unwrap()
                    .replace("int common;", "int commonEdit;")
            );
        }
        let report = make_report(b"reviewed", &prepared, false);
        assert_eq!(report.java_templates, 1);
        assert_eq!(report.raw_java_templates, 0);
        assert_eq!(report.normalized_java_templates, 1);
        assert_eq!(report.files[path].normalization, row.normalization);
        assert_eq!(report.blocked_files, 0);
    }

    #[test]
    fn normalization_cannot_be_broadened_to_unblocked_raw_java() {
        let (mut row, blobs) = fixture_row(
            b"package test;\nclass Old {}\n",
            b"package test;\nclass New {}\n",
        );
        row.disposition = VersionSeedDisposition::JavaNormalizedTemplate;
        row.normalization = Some(ReviewedJavaNormalization {
            policy: NORMALIZATION_POLICY.to_owned(),
            raw_blocker: VersionSeedDisposition::NoSharedRawLineAnchor,
            raw_blobs: blobs
                .iter()
                .map(|(oid, bytes)| (oid.clone(), normalize_java_bytes(bytes).unwrap().1))
                .collect(),
        });
        assert!(
            verified_normalized_blobs(
                "src/main/java/Example.java",
                &row,
                row.normalization.as_ref().unwrap(),
                &blobs
            )
            .is_err()
        );
        let mut ledger = CoreVersionSeedLedger::from_json(LEDGER).unwrap();
        let extra = ledger
            .files
            .values_mut()
            .find(|row| row.disposition == VersionSeedDisposition::JavaTemplate)
            .unwrap();
        extra.disposition = VersionSeedDisposition::JavaNormalizedTemplate;
        extra.normalization = Some(ReviewedJavaNormalization {
            policy: NORMALIZATION_POLICY.to_owned(),
            raw_blocker: VersionSeedDisposition::NoSharedRawLineAnchor,
            raw_blobs: BTreeMap::new(),
        });
        assert!(ledger.validate().is_err());
    }

    #[test]
    fn existing_raw_java_output_and_assets_stay_byte_identical_with_normalized_rows() {
        let path = "src/main/java/Existing.java";
        let (raw_row, mut blobs) = fixture_row(
            b"package test;\nclass Old {}\n",
            b"package test;\nclass New {}\n",
        );
        let raw_ledger = CoreVersionSeedLedger {
            schema: SCHEMA.to_owned(),
            source_checkpoint: CHECKPOINT.to_owned(),
            context_commits: BTreeMap::new(),
            files: BTreeMap::from([(path.to_owned(), raw_row.clone())]),
        };
        let original = prepare_inputs(&raw_ledger, &blobs).unwrap();
        let (normalized_row, normalized_blobs) = reviewed_normalized_fixture();
        blobs.extend(normalized_blobs);
        let (mut asset_row, asset_blobs) = fixture_row(b"\0old\xff", b"\0new\xfe");
        asset_row.disposition = VersionSeedDisposition::ExactAssets;
        blobs.extend(asset_blobs);
        let asset_path = "src/main/resources/test.png";
        let asset_ledger = CoreVersionSeedLedger {
            schema: SCHEMA.to_owned(),
            source_checkpoint: CHECKPOINT.to_owned(),
            context_commits: BTreeMap::new(),
            files: BTreeMap::from([(asset_path.to_owned(), asset_row.clone())]),
        };
        let original_assets = prepare_inputs(&asset_ledger, &blobs).unwrap();
        let combined_ledger = CoreVersionSeedLedger {
            schema: SCHEMA.to_owned(),
            source_checkpoint: CHECKPOINT.to_owned(),
            context_commits: BTreeMap::new(),
            files: BTreeMap::from([
                (path.to_owned(), raw_row),
                ("src/main/java/Normalized.java".to_owned(), normalized_row),
                (asset_path.to_owned(), asset_row),
            ]),
        };
        let combined = prepare_inputs(&combined_ledger, &blobs).unwrap();
        assert_eq!(combined.bytes[path], original.bytes[path]);
        for (input, source) in original_assets.bytes {
            assert_eq!(combined.bytes[&input], source);
        }
        assert_eq!(
            combined.source_rules[asset_path],
            original_assets.source_rules[asset_path]
        );
    }

    #[test]
    fn independent_tree_comparison_rejects_functional_changes_and_omitted_paths() {
        let (row, _) = fixture_row(b"old\n", b"new\n");
        let path = "src/main/java/Example.java".to_owned();
        let mut trees = WitnessTrees::new();
        for (target, _) in SUPPORTED_TARGETS {
            for lane in ["release", "dev"] {
                trees.insert(
                    format!("{lane}/{target}"),
                    BTreeMap::from([(
                        path.clone(),
                        TreeEntry {
                            blob: row.target_blobs[target].clone().unwrap(),
                            mode: "100644".to_owned(),
                        },
                    )]),
                );
            }
        }
        let mut ledger = CoreVersionSeedLedger {
            schema: SCHEMA.to_owned(),
            source_checkpoint: CHECKPOINT.to_owned(),
            context_commits: BTreeMap::new(),
            files: BTreeMap::from([(path.clone(), row)]),
        };
        verify_eligibility(&ledger, &trees).unwrap();
        trees
            .get_mut("dev/1.19.4")
            .unwrap()
            .get_mut(&path)
            .unwrap()
            .blob = "f".repeat(40);
        assert!(verify_eligibility(&ledger, &trees).is_err());
        trees
            .get_mut("dev/1.19.4")
            .unwrap()
            .get_mut(&path)
            .unwrap()
            .blob = trees["release/1.19.4"][&path].blob.clone();
        ledger.files.clear();
        assert!(verify_eligibility(&ledger, &trees).is_err());
    }

    #[test]
    fn java_membership_is_suggested_before_rendering_absent_targets() {
        let (mut row, blobs) = fixture_row(
            b"package test;\nclass Old {}\n",
            b"package test;\nclass New {}\n",
        );
        row.target_blobs.insert("1.19.4".to_owned(), None);
        let mut prepared = PreparedSeed::default();
        let mut report = VersionSeedFileReport {
            disposition: row.disposition,
            reason: row.reason.clone(),
            normalization: row.normalization.clone(),
            targets: supported_targets()
                .into_iter()
                .filter(|target| target != "1.19.4")
                .collect(),
            shared_lines: 0,
            conditional_regions: 0,
            inputs: Vec::new(),
        };
        let path = "src/main/java/Example.java";
        prepare_java(path, &row, &blobs, &mut prepared, &mut report).unwrap();
        assert_eq!(prepared.source_rules[path].len(), 1);
        assert!(
            !prepared.source_rules[path][0]
                .when
                .targets
                .contains(&"1.19.4".to_owned())
        );
        assert_eq!(prepared.source_rules[path][0].input, path);
    }

    #[test]
    fn raw_live_preflight_rejects_even_whitespace_changes() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path();
        let core = root.join(CORE_ROOT);
        fs::create_dir_all(&core).unwrap();
        let path = "src/main/java/Example.java".to_owned();
        let source = b"package test;\nclass Example {}\n";
        let (row, _) = fixture_row(source, b"new\n");
        let live = root.join("platform/minecraft").join(&path);
        fs::create_dir_all(live.parent().unwrap()).unwrap();
        fs::write(&live, source).unwrap();
        let ledger = CoreVersionSeedLedger {
            schema: SCHEMA.to_owned(),
            source_checkpoint: CHECKPOINT.to_owned(),
            context_commits: BTreeMap::new(),
            files: BTreeMap::from([(path, row)]),
        };
        preflight_legacy_and_retained(root, &core, &ledger).unwrap();
        fs::write(&live, b"package test;\r\nclass Example {}\r\n").unwrap();
        assert!(preflight_legacy_and_retained(root, &core, &ledger).is_err());
    }

    #[test]
    fn create_new_preflight_does_not_overwrite_or_mutate_in_preview() {
        let directory = tempfile::tempdir().unwrap();
        let core = directory.path();
        let input = "src/main/java/Example.java".to_owned();
        let bytes = BTreeMap::from([(input.clone(), b"class Example {}\n".to_vec())]);
        assert!(preflight_destinations(core, &bytes).unwrap().is_empty());
        assert!(!core.join(&input).exists());
        let prepared = PreparedSeed {
            bytes: bytes.clone(),
            ..PreparedSeed::default()
        };
        write_prepared(core, &prepared).unwrap();
        assert!(
            preflight_destinations(core, &bytes)
                .unwrap()
                .contains(&input)
        );
        fs::write(core.join(&input), b"user edit").unwrap();
        assert!(preflight_destinations(core, &bytes).is_err());
        assert!(write_prepared(core, &prepared).is_err());
        assert_eq!(fs::read(core.join(&input)).unwrap(), b"user edit");
        assert!(optional_file(core, "../escape").is_err());
    }

    #[test]
    fn bounded_git_batch_protocol_checks_raw_hash_and_separator() {
        let source = b"source\r\n";
        let oid = git_blob(source);
        let response = [
            format!("{oid} blob {}\n", source.len()).into_bytes(),
            source.to_vec(),
            vec![b'\n'],
        ]
        .concat();
        let mut input = Vec::new();
        let mut reader = std::io::Cursor::new(response.clone());
        let decoded =
            read_blob_batch(&mut input, &mut reader, BTreeSet::from([oid.clone()])).unwrap();
        assert_eq!(decoded[&oid], source);
        assert_eq!(input, format!("{oid}\n").as_bytes());
        let mut bad = response;
        *bad.last_mut().unwrap() = b'!';
        assert!(
            read_blob_batch(
                &mut Vec::new(),
                &mut std::io::Cursor::new(bad),
                BTreeSet::from([oid])
            )
            .is_err()
        );
    }
}
