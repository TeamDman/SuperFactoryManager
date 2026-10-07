//! One-time, deduplicated import of reviewed Gradle inputs into the core.
//!
//! This helper owns no production selector. Its historical witnesses are only
//! migration evidence. It creates absent authored build inputs and returns
//! explicit metadata suggestions; it never edits the catalog or feature flags.

use super::candidate_lock::checked_directory;
use super::candidate_lock::checked_file;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::InputPredicate;
use super::core_inputs::InputVariant;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::projection_catalog::validate_projection_key;
use super::provenance::sha256;
use super::release_baseline::RELEASE_4_34_0_TAGS;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fmt::Write as _;
use std::fs;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Path;
use std::path::PathBuf;

pub const DEFAULT_BUILD_SEED_LEDGER: &str = "docs/tasks/sfm-core-build-seed.json";
pub const LOCKED_BUILD_FEATURE: &str = "locked_dependency_projection";
const SCHEMA: &str = "sfm:core-build-seed@1";
const CHECKPOINT: &str = "f3ff2f6425434f36c7c680fa909c977b158e1860";
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;
const MAX_FILE_BYTES: u64 = 4 * 1024 * 1024;
const MAX_TOTAL_BYTES: u64 = 32 * 1024 * 1024;
const SHARED_FILES: usize = 123;
const DISTINCT_FILES: usize = 229;
const RELEASE_INPUTS: usize = 151;
const CURRENT_INPUTS: usize = 153;
const ROOT_FILES: &[&str] = &[
    "build.gradle",
    "gradle.properties",
    "settings.gradle",
    "gradlew",
    "gradlew.bat",
    "sfm-toolchain.lock.json",
];
const LOCKED_BUILD_FILES: &[&str] = &[
    "build.gradle",
    "sfm-toolchain.lock.json",
    "gradle/dependencies-from-lock.gradle",
    "gradle/lockfile-features.gradle",
    "gradle/jar-jar.gradle",
    "gradle/repositories.gradle",
    "gradle/source-excludes.gradle",
    "gradle/versioned-dependencies.gradle",
    "gradle/dependencies/1.19.2/dependencies.gradle",
];
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
pub struct CoreBuildSeedLedger {
    pub schema: String,
    pub source_checkpoint: String,
    pub context_commits: BTreeMap<String, String>,
    /// Unique authored payloads, not complete per-version project snapshots.
    pub files: BTreeMap<String, CoreBuildSeedFile>,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct CoreBuildSeedFile {
    pub project_path: String,
    pub source_sha256: String,
    pub git_mode: String,
    /// Names of reviewed witnesses only, never production environments.
    pub witnesses: Vec<String>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum CoreBuildSeedStatus {
    ExistingMatches,
    WouldCreate,
    Created,
}

#[derive(Clone, Debug, Facet)]
pub struct CoreBuildSeedFileReport {
    pub project_path: String,
    pub raw_sha256: String,
    pub authored_sha256: String,
    pub explicit_settings_specialization: bool,
    pub witness_count: usize,
    pub status: CoreBuildSeedStatus,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum CoreBuildMetadataEffect {
    Unchanged,
}

#[derive(Clone, Debug, Facet)]
pub struct CoreBuildSeedReport {
    pub schema: String,
    pub ledger_path: String,
    pub ledger_sha256: String,
    pub apply: bool,
    pub seed_only: bool,
    pub production_historical_lookup: bool,
    pub metadata_effect: CoreBuildMetadataEffect,
    pub verified_payloads: usize,
    pub shared_payloads: usize,
    pub existing_matching_files: usize,
    pub written_files: usize,
    pub total_authored_bytes: u64,
    pub files: BTreeMap<String, CoreBuildSeedFileReport>,
    /// Release-compatible defaults, with no environment or named-key selector.
    pub default_project_files: BTreeMap<String, Vec<InputVariant>>,
    /// Proposed functional alternatives only. Merge explicitly after registering
    /// the named flag and add its `none_features` predicate to replaced defaults.
    pub locked_project_variants: BTreeMap<String, Vec<InputVariant>>,
    /// Preserve newer inputs without guessing their feature ownership or version
    /// label policy. These are not silently included in a release projection.
    pub review_only_inputs: Vec<String>,
}

impl CoreBuildSeedReport {
    /// Encode bounded migration evidence and metadata suggestions.
    ///
    /// # Errors
    /// Returns a Facet serialization error.
    pub fn to_json(&self) -> Result<String> {
        Ok(format!("{}\n", facet_json::to_string_pretty(self)?))
    }
}

impl CoreBuildSeedLedger {
    /// Parse the exact reviewed build-import contract.
    ///
    /// # Errors
    /// Rejects oversized/duplicate/unknown JSON fields, changed scope, unsafe
    /// paths, aliases, invalid hashes, and incomplete or duplicate witnesses.
    pub fn from_json(input: &str) -> Result<Self> {
        ensure!(
            input.len() as u64 <= MAX_LEDGER_BYTES,
            "build seed ledger is too large"
        );
        reject_duplicate_keys(input)?;
        let ledger: Self =
            facet_json::from_str(input).wrap_err("invalid core build seed ledger")?;
        ledger.validate()?;
        Ok(ledger)
    }

    fn validate(&self) -> Result<()> {
        ensure!(
            self.schema == SCHEMA && self.source_checkpoint == CHECKPOINT,
            "build seed ledger uses a different reviewed checkpoint"
        );
        ensure!(
            self.context_commits == expected_contexts(),
            "build seed witnesses differ from the reviewed twenty-context matrix"
        );
        ensure!(
            self.files.len() == DISTINCT_FILES,
            "build seed must contain exactly {DISTINCT_FILES} distinct reviewed payloads"
        );
        validate_path_set(self.files.keys().map(String::as_str))?;
        let mut membership: BTreeMap<&str, BTreeSet<&str>> = BTreeMap::new();
        let mut shared = 0;
        for (input, row) in &self.files {
            validate_projection_key(input)?;
            ensure!(
                input.starts_with("build/"),
                "build seed cannot own source files"
            );
            validate_project_path(&row.project_path)?;
            validate_sha256(&row.source_sha256)?;
            ensure!(
                row.git_mode == "100644"
                    || (row.git_mode == "100755" && row.project_path == "gradlew"),
                "build seed has an unsupported file mode"
            );
            let witnesses = row.witnesses.iter().collect::<BTreeSet<_>>();
            ensure!(
                !witnesses.is_empty() && witnesses.len() == row.witnesses.len(),
                "build seed contains empty or duplicate witnesses"
            );
            for context in witnesses {
                ensure!(
                    self.context_commits.contains_key(context),
                    "unknown build witness `{context}`"
                );
                ensure!(
                    membership
                        .entry(context.as_str())
                        .or_default()
                        .insert(&row.project_path),
                    "build witness `{context}` duplicates project path `{}`",
                    row.project_path
                );
                witness_path(context, &row.project_path)?;
            }
            if input.starts_with("build/shared/") {
                shared += 1;
                ensure!(
                    row.witnesses.len() == 20
                        && input == &format!("build/shared/{}", row.project_path),
                    "shared build input lacks exact twenty-context agreement"
                );
            }
        }
        ensure!(
            shared == SHARED_FILES,
            "build seed shared membership differs from review"
        );
        for context in self.context_commits.keys() {
            let expected = if context.starts_with("release/") {
                RELEASE_INPUTS
            } else {
                CURRENT_INPUTS
            };
            ensure!(
                membership
                    .get(context.as_str())
                    .is_some_and(|paths| paths.len() == expected),
                "build witness `{context}` does not cover exactly {expected} project files"
            );
        }
        for (target, _) in SUPPORTED_TARGETS {
            for schema in [2, 4] {
                ensure!(
                    self.files
                        .contains_key(&format!("build/lockfiles/{target}/schema-{schema}.json")),
                    "build seed lacks an exact version lockfile"
                );
            }
        }
        Ok(())
    }
}

#[derive(Debug)]
struct PreparedBuildSeed {
    bytes: BTreeMap<String, Vec<u8>>,
    existing: BTreeSet<String>,
    total_bytes: u64,
}

/// Preview or explicitly apply the one-time reviewed Gradle import.
///
/// No actual core input is created in preview. Apply creates absent paths only,
/// and never rewrites a lockfile, contributor edit, selector, or source tree.
///
/// # Errors
/// Rejects unsafe filesystem paths, changed witness bytes, incompatible existing
/// destinations, resource limits, concurrent changes and I/O failures. Created
/// paths are retained after a failed write for explicit inspection, not deleted.
pub fn seed_core_build(repo_root: &Path, apply: bool) -> Result<CoreBuildSeedReport> {
    let root = checked_directory(repo_root)?;
    let core = checked_directory(&root.join(CORE_ROOT))?;
    let ledger_bytes = read_bounded(
        &checked_file(&root, DEFAULT_BUILD_SEED_LEDGER)?,
        MAX_LEDGER_BYTES,
    )?;
    let ledger = CoreBuildSeedLedger::from_json(std::str::from_utf8(&ledger_bytes)?)?;
    let prepared = prepare_seed(&root, &core, &ledger)?;
    let mut report = seed_report(&ledger, &prepared, &ledger_bytes, apply)?;
    if apply {
        write_prepared(&core, &prepared)?;
        for (input, row) in &mut report.files {
            if !prepared.existing.contains(input) {
                row.status = CoreBuildSeedStatus::Created;
                report.written_files += 1;
            }
        }
    }
    Ok(report)
}

fn prepare_seed(
    root: &Path,
    core: &Path,
    ledger: &CoreBuildSeedLedger,
) -> Result<PreparedBuildSeed> {
    let mut bytes = BTreeMap::new();
    let mut total_bytes = 0_u64;
    for (input, row) in &ledger.files {
        let mut raw: Option<Vec<u8>> = None;
        for context in &row.witnesses {
            let witness = checked_file(root, &witness_path(context, &row.project_path)?)?;
            let observed = read_bounded(&witness, MAX_FILE_BYTES)?;
            ensure!(
                sha256(&observed) == row.source_sha256,
                "build witness bytes changed for `{context}` / `{}`",
                row.project_path
            );
            if let Some(previous) = &raw {
                ensure!(
                    previous == &observed,
                    "build witnesses are not byte-identical"
                );
            } else {
                raw = Some(observed);
            }
        }
        let raw = raw.ok_or_else(|| eyre::eyre!("build input has no witness"))?;
        let authored = if row.project_path == "settings.gradle" {
            specialize_settings(&raw, &witness_targets(&row.witnesses))?
        } else {
            raw
        };
        total_bytes += authored.len() as u64;
        ensure!(
            authored.len() as u64 <= MAX_FILE_BYTES && total_bytes <= MAX_TOTAL_BYTES,
            "build seed exceeds its authored byte limit"
        );
        bytes.insert(input.clone(), authored);
    }
    let existing = preflight_destinations(core, &bytes)?;
    Ok(PreparedBuildSeed {
        bytes,
        existing,
        total_bytes,
    })
}

fn seed_report(
    ledger: &CoreBuildSeedLedger,
    prepared: &PreparedBuildSeed,
    ledger_bytes: &[u8],
    apply: bool,
) -> Result<CoreBuildSeedReport> {
    let mut defaults: BTreeMap<String, Vec<InputVariant>> = BTreeMap::new();
    let mut locked: BTreeMap<String, Vec<InputVariant>> = BTreeMap::new();
    let mut review_only = Vec::new();
    let mut files = BTreeMap::new();
    for (input, row) in &ledger.files {
        let released = selected_witness_targets(&row.witnesses, "release/");
        let current = selected_witness_targets(&row.witnesses, "dev/");
        if !released.is_empty() {
            defaults
                .entry(row.project_path.clone())
                .or_default()
                .push(variant(input, &released, None));
        } else if LOCKED_BUILD_FILES.contains(&row.project_path.as_str()) {
            locked
                .entry(row.project_path.clone())
                .or_default()
                .push(variant(input, &current, Some(LOCKED_BUILD_FEATURE)));
        } else {
            review_only.push(input.clone());
        }
        files.insert(
            input.clone(),
            CoreBuildSeedFileReport {
                project_path: row.project_path.clone(),
                raw_sha256: row.source_sha256.clone(),
                authored_sha256: sha256(&prepared.bytes[input]),
                explicit_settings_specialization: row.project_path == "settings.gradle",
                witness_count: row.witnesses.len(),
                status: if prepared.existing.contains(input) {
                    CoreBuildSeedStatus::ExistingMatches
                } else {
                    CoreBuildSeedStatus::WouldCreate
                },
            },
        );
    }
    ensure!(
        defaults.len() == RELEASE_INPUTS,
        "build suggestions have incomplete released membership"
    );
    Ok(CoreBuildSeedReport {
        schema: "sfm:core-build-seed-report@1".to_owned(),
        ledger_path: DEFAULT_BUILD_SEED_LEDGER.to_owned(),
        ledger_sha256: sha256(ledger_bytes),
        apply,
        seed_only: true,
        production_historical_lookup: false,
        metadata_effect: CoreBuildMetadataEffect::Unchanged,
        verified_payloads: ledger.files.len(),
        shared_payloads: SHARED_FILES,
        existing_matching_files: prepared.existing.len(),
        written_files: 0,
        total_authored_bytes: prepared.total_bytes,
        files,
        default_project_files: defaults,
        locked_project_variants: locked,
        review_only_inputs: review_only,
    })
}

fn variant(input: &str, targets: &[String], feature: Option<&str>) -> InputVariant {
    InputVariant {
        input: input.to_owned(),
        when: InputPredicate {
            targets: targets.to_vec(),
            all_features: feature.into_iter().map(str::to_owned).collect(),
            ..InputPredicate::default()
        },
        template: input.ends_with("/settings.gradle"),
    }
}

fn expected_contexts() -> BTreeMap<String, String> {
    RELEASE_4_34_0_TAGS
        .iter()
        .map(|(id, _, commit)| (format!("release/{id}"), (*commit).to_owned()))
        .chain(
            DEVELOPMENT_WITNESSES
                .iter()
                .map(|(id, commit)| (format!("dev/{id}"), (*commit).to_owned())),
        )
        .collect()
}

fn witness_path(context: &str, project_path: &str) -> Result<String> {
    validate_project_path(project_path)?;
    let (kind, target) = context
        .split_once('/')
        .ok_or_else(|| eyre::eyre!("invalid build witness name"))?;
    ensure!(
        SUPPORTED_TARGETS.iter().any(|(id, _)| *id == target),
        "unsupported build witness target"
    );
    let root = match (kind, target, project_path) {
        ("release", _, _) => {
            format!("platform/minecraft/release-baselines/4.34.0-{target}/gradle-project")
        }
        ("dev", "1.19.2", _) => "platform/minecraft".to_owned(),
        ("dev", "26.1.2", "gradle/lockfile-features.gradle") => {
            "platform/minecraft/development-overlays/26.1.2".to_owned()
        }
        ("dev", _, _) => {
            format!("platform/minecraft/development-baselines/{target}/gradle-project")
        }
        _ => eyre::bail!("unsupported build witness kind"),
    };
    Ok(format!("{root}/{project_path}"))
}

fn selected_witness_targets(witnesses: &[String], prefix: &str) -> Vec<String> {
    witnesses
        .iter()
        .filter_map(|name| name.strip_prefix(prefix).map(str::to_owned))
        .collect::<BTreeSet<_>>()
        .into_iter()
        .collect()
}

fn witness_targets(witnesses: &[String]) -> Vec<String> {
    witnesses
        .iter()
        .filter_map(|name| name.split_once('/').map(|(_, target)| target.to_owned()))
        .collect::<BTreeSet<_>>()
        .into_iter()
        .collect()
}

fn specialize_settings(raw: &[u8], targets: &[String]) -> Result<Vec<u8>> {
    let source = std::str::from_utf8(raw).wrap_err("settings witness is not UTF-8")?;
    ensure!(
        !source.contains("{%") && !source.contains("{{"),
        "raw settings witness unexpectedly contains Liquid"
    );
    let newline = if source.contains("\r\n") {
        "\r\n"
    } else {
        "\n"
    };
    let mut authored = raw.to_vec();
    if !raw.ends_with(b"\n") {
        authored.extend_from_slice(newline.as_bytes());
    }
    let mut appendix = format!(
        "{newline}// Core-owned project identity; projection directory names are not inputs.{newline}{{% case minecraft_version %}}{newline}"
    );
    for target in targets {
        let (_, minecraft) = SUPPORTED_TARGETS
            .iter()
            .find(|(id, _)| *id == target)
            .ok_or_else(|| eyre::eyre!("unsupported settings specialization target"))?;
        write!(
            appendix,
            "{{% when \"{minecraft}\" %}}{newline}rootProject.name = 'sfm-{target}'{newline}"
        )?;
    }
    write!(
        appendix,
        "{{% else %}}{newline}throw new GradleException('Unsupported core settings target'){newline}{{% endcase %}}{newline}"
    )?;
    authored.extend_from_slice(appendix.as_bytes());
    Ok(authored)
}

fn preflight_destinations(
    core: &Path,
    bytes: &BTreeMap<String, Vec<u8>>,
) -> Result<BTreeSet<String>> {
    let mut existing = BTreeSet::new();
    for (input, expected) in bytes {
        if let Some(path) = optional_file(core, input)? {
            ensure!(
                read_bounded(&path, MAX_FILE_BYTES)? == *expected,
                "authored build input differs at `{input}`; seed never overwrites"
            );
            existing.insert(input.clone());
        }
    }
    Ok(existing)
}

fn write_prepared(core: &Path, prepared: &PreparedBuildSeed) -> Result<()> {
    ensure!(
        preflight_destinations(core, &prepared.bytes)? == prepared.existing,
        "build destinations changed after seed preflight"
    );
    for (input, bytes) in &prepared.bytes {
        if prepared.existing.contains(input) {
            continue;
        }
        create_checked_parents(core, input)?;
        ensure!(
            optional_file(core, input)?.is_none(),
            "build input appeared during apply"
        );
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(core.join(input))
            .wrap_err_with(|| format!("cannot create core build input `{input}`"))?;
        file.write_all(bytes).wrap_err_with(|| {
            format!("cannot write core build input `{input}`; created paths retained")
        })?;
        file.sync_all().wrap_err("cannot flush core build input")?;
    }
    Ok(())
}

fn create_checked_parents(root: &Path, relative: &str) -> Result<()> {
    let parts = relative.split('/').collect::<Vec<_>>();
    let mut current = root.to_path_buf();
    for part in &parts[..parts.len() - 1] {
        reject_case_sibling(&current, part)?;
        current.push(part);
        match fs::create_dir(&current) {
            Ok(()) => {}
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
            Err(error) => return Err(error).wrap_err("cannot create core build parent"),
        }
        checked_directory(&current)?;
    }
    Ok(())
}

fn optional_file(root: &Path, relative: &str) -> Result<Option<PathBuf>> {
    validate_projection_key(relative)?;
    let mut current = checked_directory(root)?;
    let parts = relative.split('/').collect::<Vec<_>>();
    for (index, part) in parts.iter().enumerate() {
        reject_case_sibling(&current, part)?;
        current.push(part);
        match fs::symlink_metadata(&current) {
            Ok(metadata) => ensure!(
                !is_reparse(&metadata)
                    && if index + 1 == parts.len() {
                        metadata.is_file()
                    } else {
                        metadata.is_dir()
                    },
                "core build destination has a reparse point or wrong type at `{relative}`"
            ),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
            Err(error) => return Err(error).wrap_err("cannot inspect core build destination"),
        }
    }
    Ok(Some(current))
}

fn reject_case_sibling(parent: &Path, component: &str) -> Result<()> {
    for entry in fs::read_dir(parent).wrap_err("cannot inspect core build siblings")? {
        let name = entry?.file_name();
        let name = name.to_string_lossy();
        ensure!(
            !name.eq_ignore_ascii_case(component) || name == component,
            "case-aliased core build path component `{component}`"
        );
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
    let file = fs::File::open(path).wrap_err("cannot open build seed input")?;
    ensure!(
        file.metadata()?.len() <= maximum,
        "build seed input exceeds its byte limit"
    );
    let mut bytes = Vec::new();
    file.take(maximum + 1)
        .read_to_end(&mut bytes)
        .wrap_err("cannot read build seed input")?;
    ensure!(
        bytes.len() as u64 <= maximum,
        "build seed input grew beyond its byte limit"
    );
    Ok(bytes)
}

fn validate_project_path(path: &str) -> Result<()> {
    validate_projection_key(path)?;
    ensure!(
        ROOT_FILES.contains(&path) || path.starts_with("gradle/"),
        "build seed cannot read non-Gradle project inputs"
    );
    Ok(())
}

fn validate_sha256(hash: &str) -> Result<()> {
    ensure!(
        hash.len() == 71
            && hash.starts_with("sha256:")
            && hash[7..]
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "build seed requires lowercase exact SHA-256"
    );
    Ok(())
}

fn validate_path_set<'a>(paths: impl Iterator<Item = &'a str>) -> Result<()> {
    let mut prefixes = BTreeMap::<String, String>::new();
    let mut files = BTreeSet::new();
    for path in paths {
        validate_projection_key(path)?;
        ensure!(path.is_ascii(), "build seed paths must be portable ASCII");
        let mut prefix = String::new();
        for component in path.split('/') {
            if !prefix.is_empty() {
                prefix.push('/');
            }
            prefix.push_str(component);
            if let Some(previous) = prefixes.insert(prefix.to_ascii_lowercase(), prefix.clone()) {
                ensure!(previous == prefix, "case-aliased build seed path");
            }
        }
        files.insert(path.to_ascii_lowercase());
    }
    for path in &files {
        let mut prefix = path.as_str();
        while let Some((parent, _)) = prefix.rsplit_once('/') {
            ensure!(
                !files.contains(parent),
                "file/directory collision in build seed"
            );
            prefix = parent;
        }
    }
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
                    ensure!(keys.insert(key), "duplicate build seed JSON key");
                }
            }
            _ => position += 1,
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::super::context::ProjectionContext;
    use super::super::oracle_compare::ComparisonPolicy;
    use super::super::oracle_compare::compare_project_bytes;
    use super::super::render_java_source;
    use super::*;

    const LEDGER: &str = include_str!("../../../../../docs/tasks/sfm-core-build-seed.json");

    #[test]
    fn ledger_covers_deduplicated_build_inputs_and_twenty_exact_locks() {
        let ledger = CoreBuildSeedLedger::from_json(LEDGER).unwrap();
        assert_eq!(ledger.files.len(), 229);
        assert_eq!(
            ledger
                .files
                .keys()
                .filter(|path| path.starts_with("build/shared/"))
                .count(),
            123
        );
        assert_eq!(
            ledger
                .files
                .keys()
                .filter(|path| path.starts_with("build/lockfiles/"))
                .count(),
            20
        );
        assert!(
            ledger
                .files
                .values()
                .all(|row| !row.project_path.starts_with("src/"))
        );
    }

    #[test]
    fn ledger_refuses_ambiguous_metadata_unsafe_paths_and_changed_membership() {
        assert!(
            CoreBuildSeedLedger::from_json(&LEDGER.replacen("{", "{\"unknown\":1,", 1)).is_err()
        );
        assert!(
            CoreBuildSeedLedger::from_json(&LEDGER.replacen(
                "\"schema\":",
                "\"schema\":null,\"schema\":",
                1
            ))
            .is_err()
        );
        assert!(reject_duplicate_keys(r#"{"files":{"x":1,"\u0078":2}}"#).is_err());
        let mut ledger = CoreBuildSeedLedger::from_json(LEDGER).unwrap();
        ledger
            .files
            .values_mut()
            .next()
            .unwrap()
            .witnesses
            .push("dev/1.19.2".to_owned());
        assert!(ledger.validate().is_err());
        assert!(validate_project_path("src/main/java/Secret.java").is_err());
        assert!(witness_path("dev/1.21.0", "../secret").is_err());
        assert!(validate_path_set(["build/A.txt", "build/a.txt"].into_iter()).is_err());
        assert!(validate_path_set(["build/a", "build/a/b"].into_iter()).is_err());
    }

    #[test]
    fn explicit_settings_are_core_liquid_and_ignore_nested_projection_names() {
        let raw = b"pluginManagement {}\r\nrootProject.name = settingsDir.name\r\n";
        let targets = vec!["1.19.2".to_owned(), "1.21.0".to_owned()];
        let authored = specialize_settings(raw, &targets).unwrap();
        assert!(authored.starts_with(raw));
        assert_ne!(sha256(raw), sha256(&authored));
        for (target, minecraft) in [("1.19.2", "1.19.2"), ("1.21.0", "1.21")] {
            let context = ProjectionContext {
                minecraft_version: minecraft.to_owned(),
                preset: "not-a-version/arbitrary/deep-name".to_owned(),
                projection_key: "not-a-version/arbitrary/deep-name".to_owned(),
                environment: "dev".to_owned(),
                features: BTreeMap::new(),
                targets: BTreeMap::new(),
            };
            let output =
                render_java_source(std::str::from_utf8(&authored).unwrap(), &context).unwrap();
            assert!(output.contains(&format!("rootProject.name = 'sfm-{target}'")));
            assert!(!output.contains("{%"));
            assert!(!output.contains("Unsupported core settings target"));
            let comparison = compare_project_bytes(
                "settings.gradle",
                target,
                raw,
                output.as_bytes(),
                &ComparisonPolicy {
                    allow_settings_project_identity_override: true,
                    ..Default::default()
                },
            );
            assert!(!comparison.exact_equal && comparison.normalized_equal);
            assert_eq!(comparison.transformations.len(), 1);
        }
    }

    #[test]
    fn build_witness_paths_preserve_explicit_postbaseline_source_build_fix() {
        assert_eq!(
            witness_path("dev/26.1.2", "gradle/lockfile-features.gradle").unwrap(),
            "platform/minecraft/development-overlays/26.1.2/gradle/lockfile-features.gradle"
        );
        assert_eq!(
            witness_path("release/26.1.2", "sfm-toolchain.lock.json").unwrap(),
            "platform/minecraft/release-baselines/4.34.0-26.1.2/gradle-project/sfm-toolchain.lock.json"
        );
        assert!(witness_path("release/6.1.2", "build.gradle").is_err());
    }

    #[test]
    fn preview_is_read_only_apply_is_idempotent_and_refuses_contributor_edits() {
        let temp = tempfile::tempdir().unwrap();
        let core = checked_directory(temp.path()).unwrap();
        let input = "build/shared/gradle/example.gradle".to_owned();
        let bytes = BTreeMap::from([(input.clone(), b"// exact CRLF\r\n".to_vec())]);
        let existing = preflight_destinations(&core, &bytes).unwrap();
        assert!(existing.is_empty());
        assert!(!core.join("build").exists());
        let prepared = PreparedBuildSeed {
            bytes,
            existing,
            total_bytes: 15,
        };
        write_prepared(&core, &prepared).unwrap();
        let existing = preflight_destinations(&core, &prepared.bytes).unwrap();
        assert_eq!(existing.len(), 1);
        let reused = PreparedBuildSeed {
            existing,
            ..prepared
        };
        write_prepared(&core, &reused).unwrap();
        fs::write(core.join(&input), b"// contributor edit\r\n").unwrap();
        assert!(preflight_destinations(&core, &reused.bytes).is_err());
        assert_eq!(
            fs::read(core.join(&input)).unwrap(),
            b"// contributor edit\r\n"
        );
    }

    #[test]
    fn destination_preflight_refuses_case_aliases_and_directory_files() {
        let temp = tempfile::tempdir().unwrap();
        fs::create_dir(temp.path().join("Build")).unwrap();
        assert!(optional_file(temp.path(), "build/shared/a.gradle").is_err());
        assert!(optional_file(temp.path(), "Build").is_err());
    }

    #[cfg(unix)]
    #[test]
    fn destination_preflight_refuses_symlink_parents() {
        let temp = tempfile::tempdir().unwrap();
        let outside = tempfile::tempdir().unwrap();
        std::os::unix::fs::symlink(outside.path(), temp.path().join("build")).unwrap();
        assert!(optional_file(temp.path(), "build/shared/a.gradle").is_err());
    }
}
