//! One-time, reviewed import into the shared core authoring tree.
//!
//! This migration helper is not a renderer input or a historical fallback.
//! Two disjoint, fixed ledgers cover main Java and pure-shared auxiliary inputs.
//! Preview verifies the complete twenty-context witness and every live path
//! before any writes. Apply only creates absent files; it never overwrites or
//! removes an authored source, including the existing `ItemResourceType`.

use super::candidate_lock::checked_directory;
use super::candidate_lock::checked_file;
use super::core_inputs::InputPredicate;
use super::core_inputs::InputVariant;
use super::frozen_release::validate_git_commit_root;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::projection_catalog::validate_projection_key;
use super::provenance::sha256;
use super::release_baseline::RELEASE_4_34_0_TAGS;
use super::release_baseline::frozen_git_command;
use super::release_baseline::read_pinned_blob_with_mode_hardened;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use sha1::Digest as _;
use sha1::Sha1;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Path;
use std::path::PathBuf;

pub const DEFAULT_SEED_LEDGER: &str = "docs/tasks/sfm-core-shared-seed.json";
pub const DEFAULT_AUXILIARY_SEED_LEDGER: &str = "docs/tasks/sfm-core-auxiliary-seed.json";
const CORE_ROOT: &str = "platform/minecraft/core-liquid-template";
const SCHEMA: &str = "sfm:core-shared-seed@1";
const CHECKPOINT: &str = "f3ff2f6425434f36c7c680fa909c977b158e1860";
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;
const MAX_FILE_BYTES: u64 = 1024 * 1024;
const MAX_TOTAL_BYTES: u64 = 16 * 1024 * 1024;
const SHARED_FILES: usize = 195;
const MEMBERSHIP_FILES: usize = 11;
const SOURCE_PREFIX: &str = "src/main/java/";

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum SeedScope {
    MainJava,
    Auxiliary,
}

impl SeedScope {
    fn ledger_path(self) -> &'static str {
        match self {
            Self::MainJava => DEFAULT_SEED_LEDGER,
            Self::Auxiliary => DEFAULT_AUXILIARY_SEED_LEDGER,
        }
    }

    fn schema(self) -> &'static str {
        match self {
            Self::MainJava => SCHEMA,
            Self::Auxiliary => "sfm:core-auxiliary-seed@1",
        }
    }

    fn report_schema(self) -> &'static str {
        match self {
            Self::MainJava => "sfm:core-shared-seed-report@1",
            Self::Auxiliary => "sfm:core-auxiliary-seed-evidence@1",
        }
    }

    fn counts(self) -> (usize, usize) {
        match self {
            Self::MainJava => (SHARED_FILES, MEMBERSHIP_FILES),
            Self::Auxiliary => (222, 124),
        }
    }

    fn tree_prefix(self) -> &'static str {
        match self {
            Self::MainJava => "platform/minecraft/src/main/java/",
            Self::Auxiliary => "platform/minecraft/src/",
        }
    }

    fn owns(self, path: &str) -> bool {
        match self {
            Self::MainJava => path.starts_with(SOURCE_PREFIX),
            Self::Auxiliary => path.starts_with("src/") && !path.starts_with(SOURCE_PREFIX),
        }
    }

    fn validate_path(self, path: &str) -> Result<()> {
        validate_projection_key(path)?;
        ensure!(
            self.owns(path),
            "seed path is outside its reviewed scope: `{path}`"
        );
        Ok(())
    }
}

const DEVELOPMENT_WITNESSES: [(&str, &str); 10] = [
    ("1.19.2", CHECKPOINT),
    ("1.19.4", "2e3b561c15d663fb89fd353ccc2af67eeb0c2053"),
    ("1.20", "6bf4845761d06560fc5e0e36018b5d583589004d"),
    ("1.20.1", "faa040ce14dd825f2dd9716ea59508bf46278c06"),
    ("1.20.2", "a829fb4db2ae06eddd2defb22e33f0b45a4b3ff9"),
    ("1.20.3", "704aa69edad5376d8d6cfb0b0ef7845af077e647"),
    ("1.20.4", "11d3ed07d654ff801329f17cf1eb81c2b347eecd"),
    ("1.21.0", "43068d610b1c053c6569be486439769eec2ae9ef"),
    ("1.21.1", "7524ab5512878b773e212600c9578b2bc4db4717"),
    ("26.1.2", "6bd27f03863ddd041344cbc3da51b01a7b3c3e1f"),
];

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct CoreSeedLedger {
    pub schema: String,
    pub source_checkpoint: String,
    pub context_commits: BTreeMap<String, String>,
    pub files: BTreeMap<String, CoreSeedFile>,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct CoreSeedFile {
    pub targets: Vec<String>,
    pub witness_commit: String,
    pub witness_blob: String,
    pub source_sha256: String,
    /// Exact legacy canonical bytes, or null when that path was absent.
    pub canonical_raw_blob: Option<String>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum CoreSeedStatus {
    ExistingMatches,
    WouldCreate,
    Created,
}

#[derive(Clone, Debug, Facet)]
pub struct CoreSeedFileReport {
    pub targets: Vec<String>,
    pub blob_oid: String,
    pub source_sha256: String,
    pub status: CoreSeedStatus,
}

#[derive(Clone, Debug, Facet)]
pub struct CoreSeedReport {
    pub schema: String,
    pub ledger_path: String,
    pub ledger_sha256: String,
    pub apply: bool,
    pub seed_only: bool,
    pub production_historical_lookup: bool,
    pub verified_files: usize,
    pub shared_files: usize,
    pub target_membership_files: usize,
    pub existing_matching_files: usize,
    pub missing_files: usize,
    pub written_files: usize,
    pub total_bytes: u64,
    pub files: BTreeMap<String, CoreSeedFileReport>,
}

#[derive(Clone, Debug, Facet)]
pub struct AuxiliaryCoreSeedReport {
    pub schema: String,
    pub seed: CoreSeedReport,
    /// Suggestions only: this importer does not alter production selection.
    pub source_rules: BTreeMap<String, Vec<InputVariant>>,
    pub metadata_modified: bool,
}

impl AuxiliaryCoreSeedReport {
    /// Serialize auxiliary evidence and sparse target-membership suggestions.
    ///
    /// # Errors
    /// Returns an error if Facet cannot encode the report.
    pub fn to_json(&self) -> Result<String> {
        Ok(format!("{}\n", facet_json::to_string_pretty(self)?))
    }
}

impl CoreSeedReport {
    /// Serialize the typed migration evidence, without source contents.
    ///
    /// # Errors
    /// Returns an error if Facet cannot encode the report.
    pub fn to_json(&self) -> Result<String> {
        Ok(format!("{}\n", facet_json::to_string_pretty(self)?))
    }
}

impl CoreSeedLedger {
    /// Decode only the fixed, reviewed 206-file migration contract.
    ///
    /// # Errors
    /// Rejects oversized JSON, duplicate or unknown fields, unsafe paths,
    /// changed witnesses, invalid hashes and unsupported target membership.
    pub fn from_json(input: &str) -> Result<Self> {
        Self::from_json_for_scope(input, SeedScope::MainJava)
    }

    fn from_json_for_scope(input: &str, scope: SeedScope) -> Result<Self> {
        ensure!(
            input.len() as u64 <= MAX_LEDGER_BYTES,
            "seed ledger is too large"
        );
        reject_duplicate_keys(input)?;
        let ledger: Self = facet_json::from_str(input).wrap_err("invalid core seed ledger")?;
        ledger.validate_for_scope(scope)?;
        Ok(ledger)
    }

    #[cfg(test)]
    fn validate(&self) -> Result<()> {
        self.validate_for_scope(SeedScope::MainJava)
    }

    fn validate_for_scope(&self, scope: SeedScope) -> Result<()> {
        ensure!(
            self.schema == scope.schema() && self.source_checkpoint == CHECKPOINT,
            "seed ledger has a different reviewed source checkpoint"
        );
        let expected = expected_contexts();
        ensure!(
            self.context_commits == expected,
            "seed witness commits differ from review"
        );
        let (shared_count, membership_count) = scope.counts();
        ensure!(
            self.files.len() == shared_count + membership_count,
            "seed ledger path count differs from reviewed scope"
        );
        let targets = supported_targets();
        let mut case_paths = BTreeSet::new();
        let mut shared = 0;
        for (path, row) in &self.files {
            scope.validate_path(path)?;
            ensure!(
                case_paths.insert(path.to_ascii_lowercase()),
                "case-aliased seed path"
            );
            validate_sha1(&row.witness_commit)?;
            validate_sha1(&row.witness_blob)?;
            validate_sha256(&row.source_sha256)?;
            let members = row.targets.iter().cloned().collect::<BTreeSet<_>>();
            ensure!(
                !members.is_empty()
                    && members.len() == row.targets.len()
                    && members.is_subset(&targets),
                "invalid seed target membership for `{path}`"
            );
            ensure!(
                row.targets.iter().any(|target| {
                    [format!("release/{target}"), format!("dev/{target}")]
                        .iter()
                        .any(|context| expected[context] == row.witness_commit)
                }),
                "seed source commit is outside its target witnesses for `{path}`"
            );
            if let Some(blob) = &row.canonical_raw_blob {
                validate_sha1(blob)?;
                ensure!(
                    blob == &row.witness_blob,
                    "canonical seed hash differs from witness"
                );
            }
            if members == targets {
                shared += 1;
                ensure!(
                    row.canonical_raw_blob.is_some(),
                    "shared seed lacks canonical evidence"
                );
            }
        }
        ensure!(
            shared == shared_count,
            "seed ledger shared membership differs from review"
        );
        for path in &case_paths {
            let mut parent = path.as_str();
            while let Some((ancestor, _)) = parent.rsplit_once('/') {
                ensure!(
                    !case_paths.contains(ancestor),
                    "seed file/directory collision"
                );
                parent = ancestor;
            }
        }
        Ok(())
    }
}

#[derive(Clone, Debug)]
struct TreeEntry {
    mode: String,
    blob: String,
}

type WitnessTrees = BTreeMap<String, BTreeMap<String, TreeEntry>>;

#[derive(Debug)]
struct PreparedSeed {
    bytes: BTreeMap<String, Vec<u8>>,
    existing: BTreeSet<String>,
    total_bytes: u64,
}

/// Preview or explicitly apply the one-time reviewed shared-core seed.
///
/// `apply == false` is read-only. The only supported ledger path is the
/// documented default. No renderer consults this ledger or these Git objects.
///
/// # Errors
/// Rejects unsafe paths, changed witness membership/hashes, canonical drift,
/// differing existing destinations, I/O errors, or a concurrent destination
/// creation. Apply never overwrites/deletes; a failed write leaves its already
/// created files for explicit inspection rather than destructive rollback.
pub fn seed_shared_core(
    repo_root: &Path,
    ledger_relative_path: &str,
    apply: bool,
) -> Result<CoreSeedReport> {
    ensure!(
        ledger_relative_path == DEFAULT_SEED_LEDGER,
        "seed requires its fixed reviewed ledger"
    );
    seed_scope(repo_root, SeedScope::MainJava, apply)
}

/// Preview or explicitly apply the fixed, reviewed auxiliary source seed.
///
/// Assets retain their exact binary bytes. Only feature-independent files with
/// equal release/development membership qualify. Sparse rule suggestions are
/// returned for review, never applied to production metadata.
///
/// # Errors
/// Rejects invalid scope, witness drift, unsafe paths, differing destinations,
/// changed canonical bytes and I/O failures before any planned overwrite.
pub fn seed_auxiliary_core(repo_root: &Path, apply: bool) -> Result<AuxiliaryCoreSeedReport> {
    let seed = seed_scope(repo_root, SeedScope::Auxiliary, apply)?;
    let source_rules = membership_rules(&seed.files);
    Ok(AuxiliaryCoreSeedReport {
        schema: "sfm:core-auxiliary-seed-report@1".to_owned(),
        seed,
        source_rules,
        metadata_modified: false,
    })
}

fn membership_rules(
    files: &BTreeMap<String, CoreSeedFileReport>,
) -> BTreeMap<String, Vec<InputVariant>> {
    files
        .iter()
        .filter(|(_, row)| row.targets.len() < SUPPORTED_TARGETS.len())
        .map(|(path, row)| {
            (
                path.clone(),
                vec![InputVariant {
                    input: path.clone(),
                    when: InputPredicate {
                        targets: row.targets.clone(),
                        ..InputPredicate::default()
                    },
                    template: false,
                }],
            )
        })
        .collect()
}

fn seed_scope(repo_root: &Path, scope: SeedScope, apply: bool) -> Result<CoreSeedReport> {
    let candidate = if repo_root == Path::new(".") {
        std::env::current_dir()?
    } else if repo_root.is_absolute() {
        repo_root.to_path_buf()
    } else {
        std::env::current_dir()?.join(repo_root)
    };
    let root = checked_directory(&candidate)?;
    let core = checked_directory(&root.join(CORE_ROOT))?;
    let ledger_bytes = read_bounded(&checked_file(&root, scope.ledger_path())?, MAX_LEDGER_BYTES)?;
    let ledger = CoreSeedLedger::from_json_for_scope(std::str::from_utf8(&ledger_bytes)?, scope)?;
    let trees = read_witness_trees(&root, &ledger, scope)?;
    verify_memberships(&ledger, &trees, scope)?;
    let prepared = prepare_seed(&root, &core, &ledger, &trees)?;
    let mut report = seed_report(&ledger, &prepared, &ledger_bytes, apply, scope);
    if apply {
        write_prepared_for_scope(&core, &prepared, scope)?;
        for (path, file) in &mut report.files {
            if !prepared.existing.contains(path) {
                file.status = CoreSeedStatus::Created;
                report.written_files += 1;
            }
        }
    }
    Ok(report)
}

fn expected_contexts() -> BTreeMap<String, String> {
    RELEASE_4_34_0_TAGS
        .iter()
        .map(|(target, _, commit)| (format!("release/{target}"), (*commit).to_owned()))
        .chain(
            DEVELOPMENT_WITNESSES
                .iter()
                .map(|(target, commit)| (format!("dev/{target}"), (*commit).to_owned())),
        )
        .collect()
}

fn supported_targets() -> BTreeSet<String> {
    SUPPORTED_TARGETS
        .iter()
        .map(|(target, _)| (*target).to_owned())
        .collect()
}

fn read_witness_trees(
    root: &Path,
    ledger: &CoreSeedLedger,
    scope: SeedScope,
) -> Result<WitnessTrees> {
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
                scope.tree_prefix(),
            ])
            .output()
            .wrap_err("cannot read seed witness tree")?;
        ensure!(
            output.status.success(),
            "cannot read seed witness commit `{commit}`"
        );
        ensure!(
            output.stdout.len() as u64 <= MAX_TOTAL_BYTES,
            "seed witness tree is too large"
        );
        let mut tree = BTreeMap::new();
        for record in output
            .stdout
            .split(|byte| *byte == 0)
            .filter(|record| !record.is_empty())
        {
            let text = std::str::from_utf8(record).wrap_err("seed witness path is not UTF-8")?;
            let (header, repo_path) = text
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("invalid Git tree record"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[1] == "blob"
                    && matches!(fields[0], "100644" | "100755"),
                "seed witness contains a nonregular source"
            );
            validate_sha1(fields[2])?;
            let path = repo_path
                .strip_prefix("platform/minecraft/")
                .ok_or_else(|| eyre::eyre!("seed witness escaped source root"))?;
            if !scope.owns(path) {
                continue;
            }
            scope.validate_path(path)?;
            ensure!(
                tree.insert(
                    path.to_owned(),
                    TreeEntry {
                        mode: fields[0].to_owned(),
                        blob: fields[2].to_owned(),
                    }
                )
                .is_none(),
                "duplicate seed witness path"
            );
        }
        trees.insert(context.clone(), tree);
    }
    Ok(trees)
}

fn eligible_paths(trees: &WitnessTrees, scope: SeedScope) -> BTreeSet<String> {
    let mut blobs: BTreeMap<String, BTreeSet<String>> = BTreeMap::new();
    let mut released = BTreeSet::new();
    for (context, tree) in trees {
        for (path, entry) in tree {
            blobs
                .entry(path.clone())
                .or_default()
                .insert(entry.blob.clone());
            if context.starts_with("release/") {
                released.insert(path.clone());
            }
        }
    }
    blobs
        .iter()
        .filter(|(path, variants)| {
            scope.owns(path) && variants.len() == 1 && released.contains(*path)
        })
        .filter(|(path, _)| {
            scope != SeedScope::Auxiliary
                || SUPPORTED_TARGETS.iter().all(|(target, _)| {
                    trees[&format!("release/{target}")].contains_key(*path)
                        == trees[&format!("dev/{target}")].contains_key(*path)
                })
        })
        .map(|(path, _)| path.clone())
        .collect()
}

fn verify_memberships(
    ledger: &CoreSeedLedger,
    trees: &WitnessTrees,
    scope: SeedScope,
) -> Result<()> {
    let candidates = eligible_paths(trees, scope);
    ensure!(
        candidates == ledger.files.keys().cloned().collect(),
        "seed ledger is not the exact reviewed shared/version-membership path set"
    );
    for (path, row) in &ledger.files {
        for (context, tree) in trees {
            let (_, target) = context
                .split_once('/')
                .ok_or_else(|| eyre::eyre!("invalid seed context"))?;
            let expected = row.targets.iter().any(|member| member == target);
            let entry = tree.get(path);
            ensure!(
                entry.is_some() == expected,
                "seed membership mismatch at `{context}` / `{path}`"
            );
            if let Some(entry) = entry {
                ensure!(
                    entry.blob == row.witness_blob,
                    "seed blob differs at `{context}` / `{path}`"
                );
            }
        }
    }
    Ok(())
}

fn prepare_seed(
    root: &Path,
    core: &Path,
    ledger: &CoreSeedLedger,
    trees: &WitnessTrees,
) -> Result<PreparedSeed> {
    let mut bytes = BTreeMap::new();
    let mut total_bytes = 0;
    for (path, row) in &ledger.files {
        let context = ledger
            .context_commits
            .iter()
            .find(|(_, commit)| *commit == &row.witness_commit)
            .map(|(context, _)| context)
            .ok_or_else(|| eyre::eyre!("unknown seed witness"))?;
        let source = read_pinned_blob_with_mode_hardened(
            root,
            &row.witness_commit,
            path,
            &row.witness_blob,
            &row.source_sha256,
            Some(&trees[context][path].mode),
        )?;
        ensure!(
            source.len() as u64 <= MAX_FILE_BYTES,
            "seed source exceeds the file limit"
        );
        ensure!(
            git_blob(&source) == row.witness_blob,
            "seed raw Git blob verification failed"
        );
        total_bytes += source.len() as u64;
        ensure!(
            total_bytes <= MAX_TOTAL_BYTES,
            "seed exceeds the total input limit"
        );
        bytes.insert(path.clone(), source);
    }
    let existing = preflight_live_paths(root, core, &ledger.files, &bytes)?;
    Ok(PreparedSeed {
        bytes,
        existing,
        total_bytes,
    })
}

fn preflight_live_paths(
    root: &Path,
    core: &Path,
    files: &BTreeMap<String, CoreSeedFile>,
    bytes: &BTreeMap<String, Vec<u8>>,
) -> Result<BTreeSet<String>> {
    let mut existing = BTreeSet::new();
    for (path, row) in files {
        let canonical = optional_file(root, &format!("platform/minecraft/{path}"))?;
        match (&row.canonical_raw_blob, canonical) {
            (Some(expected), Some(file)) => ensure!(
                git_blob(&read_bounded(&file, MAX_FILE_BYTES)?) == *expected,
                "legacy canonical raw bytes changed for `{path}`"
            ),
            (None, None) => {}
            _ => eyre::bail!("legacy canonical presence changed for `{path}`"),
        }
        if let Some(file) = optional_file(core, path)? {
            ensure!(
                read_bounded(&file, MAX_FILE_BYTES)? == bytes[path],
                "core destination differs for `{path}`; no overwrite is permitted"
            );
            existing.insert(path.clone());
        }
    }
    Ok(existing)
}

fn seed_report(
    ledger: &CoreSeedLedger,
    prepared: &PreparedSeed,
    ledger_bytes: &[u8],
    apply: bool,
    scope: SeedScope,
) -> CoreSeedReport {
    let (shared_files, target_membership_files) = scope.counts();
    CoreSeedReport {
        schema: scope.report_schema().to_owned(),
        ledger_path: scope.ledger_path().to_owned(),
        ledger_sha256: sha256(ledger_bytes),
        apply,
        seed_only: true,
        production_historical_lookup: false,
        verified_files: ledger.files.len(),
        shared_files,
        target_membership_files,
        existing_matching_files: prepared.existing.len(),
        missing_files: ledger.files.len() - prepared.existing.len(),
        written_files: 0,
        total_bytes: prepared.total_bytes,
        files: ledger
            .files
            .iter()
            .map(|(path, row)| {
                (
                    path.clone(),
                    CoreSeedFileReport {
                        targets: row.targets.clone(),
                        blob_oid: row.witness_blob.clone(),
                        source_sha256: row.source_sha256.clone(),
                        status: if prepared.existing.contains(path) {
                            CoreSeedStatus::ExistingMatches
                        } else {
                            CoreSeedStatus::WouldCreate
                        },
                    },
                )
            })
            .collect(),
    }
}

#[cfg(test)]
fn write_prepared(core: &Path, prepared: &PreparedSeed) -> Result<()> {
    write_prepared_for_scope(core, prepared, SeedScope::MainJava)
}

fn write_prepared_for_scope(core: &Path, prepared: &PreparedSeed, scope: SeedScope) -> Result<()> {
    // Recheck every existing destination before creating the first source.
    for (path, bytes) in &prepared.bytes {
        scope.validate_path(path)?;
        let existing = optional_file(core, path)?;
        ensure!(
            existing.is_some() == prepared.existing.contains(path),
            "core destination presence changed after preview"
        );
        if let Some(file) = existing {
            ensure!(
                read_bounded(&file, MAX_FILE_BYTES)? == *bytes,
                "core destination changed after preview"
            );
        }
    }
    for (path, bytes) in &prepared.bytes {
        if prepared.existing.contains(path) {
            continue;
        }
        create_checked_parents(core, path, scope)?;
        ensure!(
            optional_file(core, path)?.is_none(),
            "core destination appeared during seed apply"
        );
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(core.join(path))
            .wrap_err_with(|| format!("cannot create core seed `{path}` without overwriting"))?;
        file.write_all(bytes).wrap_err_with(|| {
            format!("cannot write core seed `{path}`; created files were retained")
        })?;
        file.sync_all()
            .wrap_err_with(|| format!("cannot flush core seed `{path}`"))?;
    }
    Ok(())
}

fn create_checked_parents(root: &Path, relative: &str, scope: SeedScope) -> Result<()> {
    scope.validate_path(relative)?;
    let mut current = root.to_path_buf();
    let parts = relative.split('/').collect::<Vec<_>>();
    for part in &parts[..parts.len() - 1] {
        current.push(part);
        match fs::create_dir(&current) {
            Ok(()) => {}
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
            Err(error) => return Err(error).wrap_err("cannot create core seed parent"),
        }
        checked_directory(&current)?;
    }
    Ok(())
}

fn optional_file(root: &Path, relative: &str) -> Result<Option<PathBuf>> {
    validate_projection_key(relative)?;
    let parts = relative.split('/').collect::<Vec<_>>();
    let mut current = root.to_path_buf();
    for (index, part) in parts.iter().enumerate() {
        current.push(part);
        match fs::symlink_metadata(&current) {
            Ok(metadata) => {
                ensure!(
                    !is_reparse(&metadata)
                        && if index + 1 == parts.len() {
                            metadata.is_file()
                        } else {
                            metadata.is_dir()
                        },
                    "seed path has a reparse point or wrong type at `{relative}`"
                );
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
            Err(error) => {
                return Err(error)
                    .wrap_err_with(|| format!("cannot inspect seed path `{relative}`"));
            }
        }
    }
    Ok(Some(current))
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
    let file = fs::File::open(path).wrap_err("cannot open seed input")?;
    ensure!(
        file.metadata()?.len() <= maximum,
        "seed input exceeds its byte limit"
    );
    let mut bytes = Vec::new();
    file.take(maximum + 1)
        .read_to_end(&mut bytes)
        .wrap_err("cannot read seed input")?;
    ensure!(
        bytes.len() as u64 <= maximum,
        "seed input grew beyond its byte limit"
    );
    Ok(bytes)
}

fn git_blob(bytes: &[u8]) -> String {
    let mut hasher = Sha1::new();
    hasher.update(format!("blob {}\0", bytes.len()).as_bytes());
    hasher.update(bytes);
    format!("{:x}", hasher.finalize())
}

#[cfg(test)]
fn validate_source_path(path: &str) -> Result<()> {
    SeedScope::MainJava.validate_path(path)
}

fn validate_sha1(value: &str) -> Result<()> {
    ensure!(
        value.len() == 40
            && value
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "seed requires a lowercase exact Git SHA-1"
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
        "seed requires a lowercase SHA-256"
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
                    ensure!(keys.insert(key), "duplicate seed ledger JSON key");
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

    const LEDGER: &str = include_str!("../../../../../docs/tasks/sfm-core-shared-seed.json");
    const AUXILIARY_LEDGER: &str =
        include_str!("../../../../../docs/tasks/sfm-core-auxiliary-seed.json");

    #[test]
    fn reviewed_ledger_has_exact_scope_and_no_item_resource_type() {
        let ledger = CoreSeedLedger::from_json(LEDGER).unwrap();
        assert_eq!(ledger.files.len(), 206);
        assert!(
            !ledger
                .files
                .keys()
                .any(|path| path.ends_with("/ItemResourceType.java"))
        );
        assert_eq!(
            ledger
                .files
                .values()
                .filter(|row| row.canonical_raw_blob.is_none())
                .count(),
            9
        );
    }

    #[test]
    fn ledger_rejects_duplicate_unknown_fields_and_changed_witnesses() {
        assert!(
            CoreSeedLedger::from_json(&LEDGER.replacen(
                "\"schema\":",
                "\"schema\":null,\"schema\":",
                1
            ))
            .is_err()
        );
        assert!(CoreSeedLedger::from_json(&LEDGER.replacen("{", "{\"unknown\":true,", 1)).is_err());
        assert!(
            CoreSeedLedger::from_json(&LEDGER.replacen(CHECKPOINT, &"0".repeat(40), 1)).is_err()
        );
        assert!(reject_duplicate_keys(r#"{"x":1,"\u0078":2}"#).is_err());
        assert!(CoreSeedLedger::from_json("not JSON").is_err());
    }

    #[test]
    fn ledger_rejects_unsafe_paths_and_membership() {
        let mut ledger = CoreSeedLedger::from_json(LEDGER).unwrap();
        let path = ledger.files.keys().next().unwrap().clone();
        let row = ledger.files.remove(&path).unwrap();
        ledger
            .files
            .insert("src/main/java/../../escape.java".to_owned(), row);
        assert!(ledger.validate().is_err());
        let mut ledger = CoreSeedLedger::from_json(LEDGER).unwrap();
        ledger
            .files
            .values_mut()
            .next()
            .unwrap()
            .targets
            .push("unreviewed".to_owned());
        assert!(ledger.validate().is_err());
        assert!(validate_source_path("build.gradle").is_err());
        assert!(validate_source_path("src/main/java/NUL.java").is_err());
    }

    #[test]
    fn auxiliary_ledger_is_disjoint_and_retains_reviewed_membership() {
        let ledger =
            CoreSeedLedger::from_json_for_scope(AUXILIARY_LEDGER, SeedScope::Auxiliary).unwrap();
        assert_eq!(ledger.files.len(), 346);
        assert_eq!(
            ledger
                .files
                .values()
                .filter(|row| row.targets.len() == SUPPORTED_TARGETS.len())
                .count(),
            222
        );
        assert!(
            ledger
                .files
                .keys()
                .all(|path| SeedScope::Auxiliary.owns(path))
        );
        assert!(CoreSeedLedger::from_json(AUXILIARY_LEDGER).is_err());
        assert!(CoreSeedLedger::from_json_for_scope(LEDGER, SeedScope::Auxiliary).is_err());
        assert!(
            SeedScope::Auxiliary
                .validate_path("src/main/java/Example.java")
                .is_err()
        );
        assert!(
            SeedScope::MainJava
                .validate_path("src/main/resources/texture.png")
                .is_err()
        );
        assert!(
            SeedScope::Auxiliary
                .validate_path("src/main/resources/NUL.png")
                .is_err()
        );
        assert!(
            SeedScope::Auxiliary
                .validate_path("src/main/resources/../../../../escape")
                .is_err()
        );
    }

    #[test]
    fn auxiliary_ledger_rejects_unknown_duplicates_and_changed_pins() {
        assert!(
            CoreSeedLedger::from_json_for_scope(
                &AUXILIARY_LEDGER.replacen("{", "{\"unknown\":true,", 1),
                SeedScope::Auxiliary
            )
            .is_err()
        );
        assert!(
            CoreSeedLedger::from_json_for_scope(
                &AUXILIARY_LEDGER.replacen("\"schema\":", "\"schema\":null,\"schema\":", 1),
                SeedScope::Auxiliary
            )
            .is_err()
        );
        assert!(
            CoreSeedLedger::from_json_for_scope(
                &AUXILIARY_LEDGER.replacen(CHECKPOINT, &"0".repeat(40), 1),
                SeedScope::Auxiliary
            )
            .is_err()
        );
        let mut ledger =
            CoreSeedLedger::from_json_for_scope(AUXILIARY_LEDGER, SeedScope::Auxiliary).unwrap();
        let path = ledger.files.keys().next().unwrap().clone();
        let row = ledger.files.remove(&path).unwrap();
        ledger
            .files
            .insert("src/main/java/Forbidden.java".to_owned(), row);
        assert!(ledger.validate_for_scope(SeedScope::Auxiliary).is_err());
    }

    fn empty_witness_trees() -> WitnessTrees {
        expected_contexts()
            .into_keys()
            .map(|context| (context, BTreeMap::new()))
            .collect()
    }

    fn insert_witness(trees: &mut WitnessTrees, context: &str, path: &str, blob: &str) {
        trees.get_mut(context).unwrap().insert(
            path.to_owned(),
            TreeEntry {
                mode: "100644".to_owned(),
                blob: blob.to_owned(),
            },
        );
    }

    #[test]
    fn auxiliary_eligibility_excludes_feature_deltas_and_development_only_inputs() {
        let mut trees = empty_witness_trees();
        let shared = "src/main/resources/shared.png";
        let sparse = "src/generated/resources/sparse.json";
        let feature = "src/gametest/java/Feature.java";
        let changed = "src/test/java/Changed.java";
        let dev_only = "src/main/resources/development.png";
        let main_java = "src/main/java/Excluded.java";
        let blob = "1".repeat(40);
        let changed_blob = "2".repeat(40);
        for context in expected_contexts().keys() {
            insert_witness(&mut trees, context, shared, &blob);
            insert_witness(&mut trees, context, main_java, &blob);
        }
        for context in ["release/1.19.2", "dev/1.19.2"] {
            insert_witness(&mut trees, context, sparse, &blob);
            insert_witness(
                &mut trees,
                context,
                changed,
                if context.starts_with("release/") {
                    &blob
                } else {
                    &changed_blob
                },
            );
        }
        insert_witness(&mut trees, "release/1.19.2", feature, &blob);
        insert_witness(&mut trees, "dev/1.19.2", dev_only, &blob);
        assert_eq!(
            eligible_paths(&trees, SeedScope::Auxiliary),
            BTreeSet::from([shared.to_owned(), sparse.to_owned()])
        );
    }

    #[test]
    fn auxiliary_rules_are_sparse_target_only_suggestions() {
        let ledger =
            CoreSeedLedger::from_json_for_scope(AUXILIARY_LEDGER, SeedScope::Auxiliary).unwrap();
        let evidence = ledger
            .files
            .iter()
            .map(|(path, row)| {
                (
                    path.clone(),
                    CoreSeedFileReport {
                        targets: row.targets.clone(),
                        blob_oid: row.witness_blob.clone(),
                        source_sha256: row.source_sha256.clone(),
                        status: CoreSeedStatus::WouldCreate,
                    },
                )
            })
            .collect();
        let rules = membership_rules(&evidence);
        assert_eq!(rules.len(), 124);
        for (path, variants) in rules {
            assert_eq!(variants.len(), 1);
            assert_eq!(variants[0].input, path);
            assert_eq!(variants[0].when.targets, ledger.files[&path].targets);
            assert!(variants[0].when.all_features.is_empty());
            assert!(variants[0].when.any_features.is_empty());
            assert!(variants[0].when.none_features.is_empty());
            assert!(!variants[0].template);
        }
    }

    #[test]
    fn auxiliary_membership_proof_rejects_incomplete_or_changed_evidence() {
        let path = "src/generated/resources/fixture.json";
        let blob = "1".repeat(40);
        let mut trees = empty_witness_trees();
        for context in ["release/1.19.2", "dev/1.19.2"] {
            insert_witness(&mut trees, context, path, &blob);
        }
        let mut ledger = CoreSeedLedger {
            schema: SeedScope::Auxiliary.schema().to_owned(),
            source_checkpoint: CHECKPOINT.to_owned(),
            context_commits: expected_contexts(),
            files: BTreeMap::from([(
                path.to_owned(),
                CoreSeedFile {
                    targets: vec!["1.19.2".to_owned()],
                    witness_commit: CHECKPOINT.to_owned(),
                    witness_blob: blob.clone(),
                    source_sha256: sha256(b"fixture"),
                    canonical_raw_blob: Some(blob),
                },
            )]),
        };
        verify_memberships(&ledger, &trees, SeedScope::Auxiliary).unwrap();
        ledger
            .files
            .get_mut(path)
            .unwrap()
            .targets
            .push("1.19.4".to_owned());
        assert!(verify_memberships(&ledger, &trees, SeedScope::Auxiliary).is_err());
        ledger.files.get_mut(path).unwrap().targets.pop();
        insert_witness(&mut trees, "dev/1.19.2", path, &"2".repeat(40));
        assert!(verify_memberships(&ledger, &trees, SeedScope::Auxiliary).is_err());
        ledger.files.clear();
        insert_witness(&mut trees, "dev/1.19.2", path, &"1".repeat(40));
        assert!(verify_memberships(&ledger, &trees, SeedScope::Auxiliary).is_err());
    }

    fn fixture(
        bytes: &[u8],
    ) -> (
        tempfile::TempDir,
        PathBuf,
        PathBuf,
        BTreeMap<String, CoreSeedFile>,
        BTreeMap<String, Vec<u8>>,
    ) {
        fixture_at(bytes, "src/main/java/Example.java")
    }

    fn fixture_at(
        bytes: &[u8],
        relative: &str,
    ) -> (
        tempfile::TempDir,
        PathBuf,
        PathBuf,
        BTreeMap<String, CoreSeedFile>,
        BTreeMap<String, Vec<u8>>,
    ) {
        let temp = tempfile::tempdir().unwrap();
        let root = checked_directory(temp.path()).unwrap();
        let core = root.join(CORE_ROOT);
        fs::create_dir_all(&core).unwrap();
        let path = relative.to_owned();
        let canonical = root.join("platform/minecraft").join(&path);
        fs::create_dir_all(canonical.parent().unwrap()).unwrap();
        fs::write(canonical, bytes).unwrap();
        let files = BTreeMap::from([(
            path.clone(),
            CoreSeedFile {
                targets: vec!["1.19.2".to_owned()],
                witness_commit: CHECKPOINT.to_owned(),
                witness_blob: git_blob(bytes),
                source_sha256: sha256(bytes),
                canonical_raw_blob: Some(git_blob(bytes)),
            },
        )]);
        (
            temp,
            root,
            core,
            files,
            BTreeMap::from([(path, bytes.to_vec())]),
        )
    }

    #[test]
    fn preview_does_not_write_and_apply_preserves_crlf_then_reuses_exact_bytes() {
        let bytes = b"class Example {}\r\n";
        let (_temp, root, core, files, content) = fixture(bytes);
        let existing = preflight_live_paths(&root, &core, &files, &content).unwrap();
        assert!(existing.is_empty());
        assert!(!core.join("src").exists());
        let prepared = PreparedSeed {
            bytes: content,
            existing,
            total_bytes: bytes.len() as u64,
        };
        write_prepared(&core, &prepared).unwrap();
        assert_eq!(
            fs::read(core.join("src/main/java/Example.java")).unwrap(),
            bytes
        );
        assert_eq!(
            preflight_live_paths(&root, &core, &files, &prepared.bytes)
                .unwrap()
                .len(),
            1
        );
        assert_ne!(git_blob(bytes), git_blob(b"class Example {}\n"));
    }

    #[test]
    fn auxiliary_binary_assets_remain_exact_and_scope_cannot_cross_write() {
        let bytes = b"\x89PNG\r\n\x1a\n\x00\xff\xfe\x01";
        let path = "src/main/resources/assets/sfm/textures/block/fixture.png";
        let (_temp, root, core, files, content) = fixture_at(bytes, path);
        let existing = preflight_live_paths(&root, &core, &files, &content).unwrap();
        assert!(existing.is_empty());
        let prepared = PreparedSeed {
            bytes: content,
            existing,
            total_bytes: bytes.len() as u64,
        };
        assert!(write_prepared(&core, &prepared).is_err());
        assert!(!core.join("src").exists());
        write_prepared_for_scope(&core, &prepared, SeedScope::Auxiliary).unwrap();
        assert_eq!(fs::read(core.join(path)).unwrap(), bytes);
        assert_eq!(
            preflight_live_paths(&root, &core, &files, &prepared.bytes)
                .unwrap()
                .len(),
            1
        );
        fs::write(core.join(path), b"existing authored asset").unwrap();
        assert!(preflight_live_paths(&root, &core, &files, &prepared.bytes).is_err());
        assert!(write_prepared_for_scope(&core, &prepared, SeedScope::Auxiliary).is_err());
        assert_eq!(
            fs::read(core.join(path)).unwrap(),
            b"existing authored asset"
        );
        let (_temp, _root, main_core, _files, content) = fixture(b"class Example {}\n");
        let main = PreparedSeed {
            bytes: content,
            existing: BTreeSet::new(),
            total_bytes: 17,
        };
        assert!(write_prepared_for_scope(&main_core, &main, SeedScope::Auxiliary).is_err());
        assert!(!main_core.join("src").exists());
    }

    #[test]
    fn canonical_drift_and_differing_destinations_fail_before_writes() {
        let (_temp, root, core, files, content) = fixture(b"original\r\n");
        fs::write(
            root.join("platform/minecraft/src/main/java/Example.java"),
            b"changed\n",
        )
        .unwrap();
        assert!(preflight_live_paths(&root, &core, &files, &content).is_err());
        assert!(!core.join("src").exists());
        fs::write(
            root.join("platform/minecraft/src/main/java/Example.java"),
            b"original\r\n",
        )
        .unwrap();
        fs::create_dir_all(core.join("src/main/java")).unwrap();
        fs::write(core.join("src/main/java/Example.java"), b"authored\n").unwrap();
        assert!(preflight_live_paths(&root, &core, &files, &content).is_err());
        assert_eq!(
            fs::read(core.join("src/main/java/Example.java")).unwrap(),
            b"authored\n"
        );
    }

    #[test]
    fn newly_created_destination_and_wrong_parent_type_are_not_overwritten() {
        let (_temp, root, core, files, content) = fixture(b"original\n");
        let existing = preflight_live_paths(&root, &core, &files, &content).unwrap();
        let prepared = PreparedSeed {
            bytes: content,
            existing,
            total_bytes: 9,
        };
        fs::create_dir_all(core.join("src/main/java")).unwrap();
        fs::write(core.join("src/main/java/Example.java"), b"new owner\n").unwrap();
        assert!(write_prepared(&core, &prepared).is_err());
        assert_eq!(
            fs::read(core.join("src/main/java/Example.java")).unwrap(),
            b"new owner\n"
        );
        fs::write(core.join("src/blocked"), b"not a directory").unwrap();
        assert!(optional_file(&core, "src/blocked/Example.java").is_err());
    }

    #[test]
    fn byte_limits_and_fixed_ledger_scope_fail_closed() {
        let (_temp, root, core, _files, _content) = fixture(b"original\n");
        assert!(
            read_bounded(
                &root.join("platform/minecraft/src/main/java/Example.java"),
                2
            )
            .is_err()
        );
        assert!(seed_shared_core(&root, "other-ledger.json", false).is_err());
        assert!(optional_file(&core, "../escape").is_err());
    }

    #[cfg(unix)]
    #[test]
    fn reparse_parent_is_rejected() {
        let (_temp, _root, core, _files, _content) = fixture(b"original\n");
        let outside = tempfile::tempdir().unwrap();
        std::os::unix::fs::symlink(outside.path(), core.join("src")).unwrap();
        assert!(optional_file(&core, "src/main/java/Example.java").is_err());
    }
}
