//! Bounded, checksum-verified storage for immutable comparison cells.
//!
//! This is not a cached completion report. Callers must discover current inputs,
//! resolve references and validate each cell's content/context key before reuse.

use super::candidate_lock::checked_directory;
use super::candidate_lock::checked_file;
use super::oracle::OracleCounts;
use super::oracle::OracleIssue;
use super::oracle_git::GitInventoryDescriptor;
use super::oracle_git::ValidatedGitInventory;
use super::provenance::sha256;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use sha2::Digest;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::collections::HashMap;
use std::collections::HashSet;
use std::fmt::Write as _;
use std::fs;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Path;
use std::sync::Arc;

const FILE_NAME: &str = "oracle-comparison-cells-v4.cache";
const MAGIC: &str = "sfm:oracle_comparison_cells@4";
const MAX_BYTES: u64 = 64 * 1024 * 1024;
const MAX_CELLS: usize = 100_000;
const MAX_EXPANDED_BYTES: u64 = MAX_BYTES * 8;

/// Selection facts, not timestamps or queue position. Full context and policy
/// identities are hashes of their actual typed values, not just preset names.
#[cfg(test)]
#[derive(Clone, Debug)]
pub struct CellIdentity {
    pub context_identity: String,
    pub policy_identity: String,
    pub oracle_binding_identity: String,
    pub output_path: String,
    pub input_path: Option<String>,
    pub template: bool,
    pub source_sha256: Option<String>,
    pub oracle_blob: Option<String>,
    pub oracle_mode: Option<u32>,
}

#[cfg(test)]
impl CellIdentity {
    pub fn key(&self) -> Result<String> {
        CellIdentityRef {
            context_identity: &self.context_identity,
            policy_identity: &self.policy_identity,
            oracle_binding_identity: &self.oracle_binding_identity,
            output_path: &self.output_path,
            input_path: self.input_path.as_deref(),
            template: self.template,
            source_sha256: self.source_sha256.as_deref(),
            oracle_blob: self.oracle_blob.as_deref(),
            oracle_mode: self.oracle_mode,
        }
        .key()
    }
}

/// Borrowed identity avoids allocating and serializing a JSON object per path.
pub struct CellIdentityRef<'a> {
    pub context_identity: &'a str,
    pub policy_identity: &'a str,
    pub oracle_binding_identity: &'a str,
    pub output_path: &'a str,
    pub input_path: Option<&'a str>,
    pub template: bool,
    pub source_sha256: Option<&'a str>,
    pub oracle_blob: Option<&'a str>,
    pub oracle_mode: Option<u32>,
}

impl CellIdentityRef<'_> {
    /// Compute an unambiguous, content-sensitive comparison lookup key.
    ///
    /// # Errors
    /// Rejects invalid digests and inconsistent absent/present inputs.
    #[cfg_attr(
        feature = "tracy",
        tracing::instrument(level = "info", skip_all, name = "oracle_comparison_cell_key")
    )]
    pub fn key(&self) -> Result<String> {
        validate_digest(self.context_identity)?;
        validate_digest(self.policy_identity)?;
        validate_digest(self.oracle_binding_identity)?;
        if let Some(digest) = &self.source_sha256 {
            validate_digest(digest)?;
        }
        ensure!(
            self.input_path.is_some() == self.source_sha256.is_some(),
            "cell source identity is incomplete"
        );
        ensure!(
            self.oracle_blob.is_some() == self.oracle_mode.is_some(),
            "cell oracle identity is incomplete"
        );
        if let Some(oid) = &self.oracle_blob {
            ensure!(
                oid.len() == 40
                    && oid
                        .bytes()
                        .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
                "invalid cell oracle blob"
            );
        }
        // Fixed field order, presence markers and explicit byte lengths prevent
        // concatenation/absence ambiguity. Renderer identity rejects old keys.
        let mut hash = Sha256::new();
        hash.update(b"sfm:oracle_cell_key@2\0");
        for value in [
            Some(self.context_identity),
            Some(self.policy_identity),
            Some(self.oracle_binding_identity),
            Some(self.output_path),
            self.input_path,
            self.source_sha256,
            self.oracle_blob,
        ] {
            if let Some(value) = value {
                hash.update([1]);
                hash.update((value.len() as u64).to_le_bytes());
                hash.update(value.as_bytes());
            } else {
                hash.update([0]);
            }
        }
        hash.update([u8::from(self.template)]);
        if let Some(mode) = self.oracle_mode {
            hash.update([1]);
            hash.update(mode.to_le_bytes());
        } else {
            hash.update([0]);
        }
        Ok(format!("sha256:{:x}", hash.finalize()))
    }
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ComparisonCell {
    pub counts: OracleCounts,
    pub issues: Vec<OracleIssue>,
    pub normalizations: BTreeMap<String, Vec<String>>,
    pub rendered_sha256: Option<String>,
    pub rendered_bytes: u64,
}

#[derive(Debug, Eq, PartialEq)]
pub struct ComparisonIndex {
    pub repository_identity: String,
    pub renderer_identity: String,
    pub cells: BTreeMap<String, Arc<ComparisonCell>>,
    pub inventories: BTreeMap<String, ValidatedGitInventory>,
}

// Private storage encoding: nine fixed-order counters instead of repeating
// their JSON field names tens of thousands of times. Public reports retain
// named counters. The explicit schema change requires a deliberate rebuild.
#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct StoredCell {
    counts: [usize; 9],
    issues: Vec<OracleIssue>,
    normalizations: BTreeMap<String, Vec<String>>,
    rendered_sha256: Option<String>,
    rendered_bytes: u64,
}

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct StoredIndex {
    repository_identity: String,
    renderer_identity: String,
    cells: BTreeMap<String, usize>,
    values: Vec<StoredCell>,
    inventories: BTreeMap<String, GitInventoryDescriptor>,
}

impl StoredCell {
    fn expansion_bound(&self) -> Result<u64> {
        // Conservative content/container allowance, computed without cloning or
        // serializing. Bound alias expansion before constructing owned cells.
        let mut bytes = 256_u64;
        let mut add = |text: &str| -> Result<()> {
            bytes = bytes
                .checked_add(128)
                .and_then(|bytes| bytes.checked_add((text.len() as u64).checked_mul(6)?))
                .ok_or_else(|| eyre::eyre!("oracle index expansion overflow"))?;
            Ok(())
        };
        if let Some(text) = &self.rendered_sha256 {
            add(text)?;
        }
        for (path, rules) in &self.normalizations {
            add(path)?;
            for rule in rules {
                add(rule)?;
            }
        }
        for issue in &self.issues {
            add(&issue.path)?;
            add(&issue.kind)?;
            add(&issue.recommendation)?;
            for text in [
                &issue.core_input,
                &issue.oracle_blob,
                &issue.oracle_sha256,
                &issue.rendered_sha256,
            ]
            .into_iter()
            .flatten()
            {
                add(text)?;
            }
            for text in &issue.diagnostics {
                add(text)?;
            }
        }
        Ok(bytes)
    }
}

impl From<StoredCell> for ComparisonCell {
    fn from(cell: StoredCell) -> Self {
        let [
            expected,
            produced,
            matched,
            exact,
            normalized,
            missing,
            changed,
            unexpected,
            errors,
        ] = cell.counts;
        Self {
            counts: OracleCounts {
                expected,
                produced,
                matched,
                exact,
                normalized,
                missing,
                changed,
                unexpected,
                errors,
            },
            issues: cell.issues,
            normalizations: cell.normalizations,
            rendered_sha256: cell.rendered_sha256,
            rendered_bytes: cell.rendered_bytes,
        }
    }
}

impl From<&ComparisonCell> for StoredCell {
    fn from(cell: &ComparisonCell) -> Self {
        let counts = &cell.counts;
        Self {
            counts: [
                counts.expected,
                counts.produced,
                counts.matched,
                counts.exact,
                counts.normalized,
                counts.missing,
                counts.changed,
                counts.unexpected,
                counts.errors,
            ],
            issues: cell.issues.clone(),
            normalizations: cell.normalizations.clone(),
            rendered_sha256: cell.rendered_sha256.clone(),
            rendered_bytes: cell.rendered_bytes,
        }
    }
}

impl ComparisonIndex {
    /// Create an empty index for an explicitly identified repository/renderer.
    ///
    /// # Errors
    /// Rejects malformed identities.
    pub fn new(repository_identity: String, renderer_identity: String) -> Result<Self> {
        let index = Self {
            repository_identity,
            renderer_identity,
            cells: BTreeMap::new(),
            inventories: BTreeMap::new(),
        };
        index.validate()?;
        Ok(index)
    }

    /// Read a complete verified index. Absence is explicit, not a passing queue.
    ///
    /// # Errors
    /// Rejects unsafe paths, oversized/truncated/corrupt files, invalid cells,
    /// unknown fields and mismatched repository or renderer identities.
    #[tracing::instrument(level = "info", skip_all, name = "oracle_index_load")]
    pub fn load(directory: &Path, repository: &str, renderer: &str) -> Result<Option<Self>> {
        let directory = checked_directory(directory)?;
        if !directory.join(FILE_NAME).try_exists()? {
            return Ok(None);
        }
        let path = checked_file(&directory, FILE_NAME)?;
        let file = fs::File::open(path)?;
        ensure!(
            file.metadata()?.len() <= MAX_BYTES,
            "oracle index exceeds byte limit"
        );
        let mut bytes = Vec::new();
        file.take(MAX_BYTES + 1).read_to_end(&mut bytes)?;
        ensure!(
            bytes.len() as u64 <= MAX_BYTES,
            "oracle index exceeds byte limit"
        );
        let text = std::str::from_utf8(&bytes).wrap_err("oracle index is not UTF-8")?;
        let (magic, rest) = text
            .split_once('\n')
            .ok_or_else(|| eyre::eyre!("truncated oracle index header"))?;
        ensure!(magic == MAGIC, "unsupported oracle index schema");
        let (digest, payload) = rest
            .split_once('\n')
            .ok_or_else(|| eyre::eyre!("truncated oracle index checksum"))?;
        let () = {
            let _span = tracing::info_span!("oracle_index_checksum").entered();
            ensure!(
                digest == sha256(payload.as_bytes()),
                "oracle index checksum mismatch"
            );
        };
        let stored: StoredIndex = {
            let _span = tracing::info_span!("oracle_index_decode").entered();
            facet_json::from_str(payload).wrap_err("cannot decode oracle index")?
        };
        ensure!(
            stored.cells.len() <= MAX_CELLS && stored.values.len() <= stored.cells.len(),
            "invalid oracle index record table size"
        );
        let inventories = seal_inventories(stored.inventories)?;
        let sizes = stored
            .values
            .iter()
            .map(StoredCell::expansion_bound)
            .collect::<Result<Vec<_>>>()?;
        let mut expanded_bytes = 0_u64;
        let mut used = vec![false; stored.values.len()];
        for id in stored.cells.values() {
            let size = sizes
                .get(*id)
                .ok_or_else(|| eyre::eyre!("oracle index record reference is out of range"))?;
            expanded_bytes = expanded_bytes
                .checked_add(*size)
                .ok_or_else(|| eyre::eyre!("oracle index expansion overflow"))?;
            ensure!(
                expanded_bytes <= MAX_EXPANDED_BYTES,
                "oracle index record expansion exceeds byte limit"
            );
            used[*id] = true;
        }
        ensure!(
            used.into_iter().all(|value| value),
            "oracle index has unreferenced records"
        );
        let values = stored
            .values
            .into_iter()
            .map(|cell| Arc::new(ComparisonCell::from(cell)))
            .collect::<Vec<_>>();
        let cells = stored
            .cells
            .into_iter()
            .map(|(key, id)| {
                let cell = values
                    .get(id)
                    .ok_or_else(|| eyre::eyre!("oracle index record reference is out of range"))?;
                Ok((key, Arc::clone(cell)))
            })
            .collect::<Result<BTreeMap<_, _>>>()?;
        let index = Self {
            repository_identity: stored.repository_identity,
            renderer_identity: stored.renderer_identity,
            cells,
            inventories,
        };
        let () = {
            let _span = tracing::info_span!("oracle_index_validate_loaded").entered();
            index.validate()?;
        };
        ensure!(
            index.repository_identity == repository,
            "oracle index belongs to another repository"
        );
        ensure!(
            index.renderer_identity == renderer,
            "oracle index belongs to another renderer; explicitly rebuild it"
        );
        Ok(Some(index))
    }

    /// Atomically publish a complete index in a caller-verified cache directory.
    /// Integration must locate that directory outside authored/generated source
    /// and Git state before calling this storage primitive.
    ///
    /// # Errors
    /// Rejects invalid/oversized state, unsafe paths and failed durable writes.
    #[tracing::instrument(level = "info", skip_all, name = "oracle_index_save")]
    pub fn save(&self, directory: &Path) -> Result<()> {
        let () = {
            let _span = tracing::info_span!("oracle_index_validate_before_save").entered();
            self.validate()?;
        };
        let directory = checked_directory(directory)?;
        let destination = directory.join(FILE_NAME);
        if destination.try_exists()? {
            checked_file(&directory, FILE_NAME)?;
        }
        // Retain each record's canonical JSON from interning rather than
        // cloning its details and serializing the same record a second time.
        let mut values = Vec::new();
        let mut identities = BTreeMap::new();
        let mut cells = BTreeMap::new();
        // These addresses identify simultaneously live immutable Arc bodies,
        // not persistent content. They are never serialized or dereferenced.
        // Distinct allocations still undergo exact canonical-byte interning.
        let mut shared_values = HashMap::new();
        for (key, cell) in &self.cells {
            let pointer = Arc::as_ptr(cell);
            if let Some(id) = shared_values.get(&pointer) {
                cells.insert(key.clone(), *id);
                continue;
            }
            let value = StoredCell::from(cell.as_ref());
            // Exact canonical bytes, not a lossy semantic hash. Each context's
            // lookup key remains independent even when its result is identical.
            let identity = facet_json::to_string(&value)?;
            let id = if let Some(id) = identities.get(&identity) {
                *id
            } else {
                let id = values.len();
                identities.insert(identity.clone(), id);
                values.push(identity);
                id
            };
            shared_values.insert(pointer, id);
            cells.insert(key.clone(), id);
        }
        let payload = {
            let _span = tracing::info_span!("oracle_index_encode_payload").entered();
            encode_payload(self, &cells, &values)?
        };
        let bytes = format!("{MAGIC}\n{}\n{payload}", sha256(payload.as_bytes()));
        ensure!(
            bytes.len() as u64 <= MAX_BYTES,
            "oracle index exceeds byte limit"
        );
        let mut staged = tempfile::NamedTempFile::new_in(&directory)?;
        staged.write_all(bytes.as_bytes())?;
        staged.as_file().sync_all()?;
        staged.persist(destination).map_err(|error| error.error)?;
        Ok(())
    }

    fn validate(&self) -> Result<()> {
        validate_digest(&self.repository_identity)?;
        validate_renderer_digest(&self.renderer_identity)?;
        ensure!(
            self.cells.len() <= MAX_CELLS,
            "oracle index exceeds cell limit"
        );
        ensure!(
            self.inventories.len() <= 40,
            "oracle index exceeds inventory limit"
        );
        let mut files = 0_usize;
        for (key, inventory) in &self.inventories {
            validate_digest(key)?;
            files = files
                .checked_add(inventory.descriptor().files.len())
                .ok_or_else(|| eyre::eyre!("oracle inventory count overflow"))?;
            ensure!(
                files <= MAX_CELLS,
                "oracle index exceeds inventory file limit"
            );
        }
        // All keys remain checked. A shared immutable body needs its complete
        // checks only once in this call; mutations through Arc::make_mut create
        // a distinct allocation and are validated on the next call.
        let mut checked_bodies = HashSet::new();
        for (key, cell) in &self.cells {
            validate_digest(key)?;
            if !checked_bodies.insert(Arc::as_ptr(cell)) {
                continue;
            }
            ensure!(
                cell.counts.expected <= 1 && cell.counts.produced <= 1,
                "oracle index cell is not a single-file comparison"
            );
            ensure!(
                cell.issues.len() <= 1 && cell.normalizations.len() <= 1,
                "oracle index cell has excessive comparison details"
            );
            validate_counts(cell)?;
            if let Some(digest) = &cell.rendered_sha256 {
                validate_digest(digest)?;
            }
            ensure!(
                cell.rendered_sha256.is_some() || cell.rendered_bytes == 0,
                "absent rendering has nonzero size"
            );
            ensure!(
                cell.rendered_bytes <= super::core_inputs::MAX_CORE_PROJECTION_BYTES,
                "oracle index rendering exceeds byte limit"
            );
        }
        Ok(())
    }
}

#[tracing::instrument(level = "info", skip_all, name = "oracle_index_seal_inventories")]
fn seal_inventories(
    inventories: BTreeMap<String, GitInventoryDescriptor>,
) -> Result<BTreeMap<String, ValidatedGitInventory>> {
    ensure!(
        inventories.len() <= 40,
        "oracle index exceeds inventory limit"
    );
    let total_files = inventories.values().try_fold(0_usize, |total, inventory| {
        total
            .checked_add(inventory.files.len())
            .ok_or_else(|| eyre::eyre!("oracle inventory count overflow"))
    })?;
    ensure!(
        total_files <= MAX_CELLS,
        "oracle index exceeds inventory file limit"
    );
    inventories
        .into_iter()
        .map(|(key, value)| Ok((key, ValidatedGitInventory::new(value)?)))
        .collect()
}

/// The key and identity strings have already passed strict digest validation.
/// Record strings come only from the canonical serializer above; inventory
/// paths still pass through that serializer for escaping. Preserve the exact
/// schema-v4 JSON encoding, independently checked against `StoredIndex` in tests.
fn encode_payload(
    index: &ComparisonIndex,
    cells: &BTreeMap<String, usize>,
    values: &[String],
) -> Result<String> {
    let mut payload = format!(
        "{{\"repository_identity\":\"{}\",\"renderer_identity\":\"{}\",\"cells\":{{",
        index.repository_identity, index.renderer_identity,
    );
    for (position, (key, id)) in cells.iter().enumerate() {
        if position != 0 {
            payload.push(',');
        }
        write!(payload, "\"{key}\":{id}")?;
    }
    payload.push_str("},\"values\":[");
    for (position, value) in values.iter().enumerate() {
        if position != 0 {
            payload.push(',');
        }
        payload.push_str(value);
    }
    payload.push_str("],\"inventories\":");
    let inventories = index
        .inventories
        .iter()
        .map(|(key, value)| (key.clone(), value.shared_descriptor()))
        .collect::<BTreeMap<_, _>>();
    payload.push_str(&facet_json::to_string(&inventories)?);
    payload.push('}');
    Ok(payload)
}

fn validate_counts(cell: &ComparisonCell) -> Result<()> {
    let counts = &cell.counts;
    let add = |a: usize, b: usize| {
        a.checked_add(b)
            .ok_or_else(|| eyre::eyre!("oracle cell count overflow"))
    };
    ensure!(
        counts.matched == add(counts.exact, counts.normalized)?,
        "oracle cell match counts disagree"
    );
    let differences = add(
        add(counts.changed, counts.errors)?,
        add(counts.missing, counts.unexpected)?,
    )?;
    ensure!(
        add(counts.matched, differences)? <= 1,
        "oracle cell has multiple outcomes"
    );
    ensure!(
        counts.expected
            == add(
                add(counts.matched, counts.missing)?,
                add(counts.changed, counts.errors)?
            )?,
        "oracle cell expected count disagrees"
    );
    ensure!(
        counts.produced
            == add(
                add(counts.matched, counts.unexpected)?,
                add(counts.changed, counts.errors)?
            )?,
        "oracle cell produced count disagrees"
    );
    ensure!(
        cell.issues.len() == differences,
        "oracle cell issues disagree with counts"
    );
    ensure!(
        cell.normalizations.len() <= counts.matched,
        "unmatched cell has normalizations"
    );
    ensure!(
        cell.rendered_sha256.is_some() == (counts.produced == 1),
        "oracle cell rendered identity disagrees with produced count"
    );
    if let Some(issue) = cell.issues.first() {
        let outcome = match issue.kind.as_str() {
            "missing" => counts.missing,
            "unexpected" => counts.unexpected,
            "changed" => counts.changed,
            "error" => counts.errors,
            _ => 0,
        };
        ensure!(
            outcome == 1 && issue.rendered_sha256 == cell.rendered_sha256,
            "oracle cell issue outcome disagrees"
        );
    }
    Ok(())
}

fn validate_digest(value: &str) -> Result<()> {
    validate_digest_digits(value.strip_prefix("sha256:").unwrap_or(""))
}

fn validate_renderer_digest(value: &str) -> Result<()> {
    // Only executable identities admit BLAKE3; source/oracle/key identities
    // retain their strict SHA-256 contract. Both algorithms are already locked.
    validate_digest_digits(
        value
            .strip_prefix("blake3:")
            .or_else(|| value.strip_prefix("sha256:"))
            .unwrap_or(""),
    )
}

fn validate_digest_digits(digits: &str) -> Result<()> {
    ensure!(
        digits.len() == 64
            && digits
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "invalid oracle index digest"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn owned_test_inventories(index: &ComparisonIndex) -> BTreeMap<String, GitInventoryDescriptor> {
        index
            .inventories
            .iter()
            .map(|(key, value)| (key.clone(), value.descriptor().clone()))
            .collect()
    }

    fn fixture() -> ComparisonIndex {
        let mut index = ComparisonIndex::new(sha256(b"repository"), sha256(b"renderer")).unwrap();
        index.cells.insert(
            sha256(b"source/context/policy/oracle identity"),
            Arc::new(ComparisonCell {
                counts: OracleCounts {
                    expected: 1,
                    produced: 1,
                    matched: 1,
                    exact: 1,
                    ..OracleCounts::default()
                },
                issues: Vec::new(),
                normalizations: BTreeMap::new(),
                rendered_sha256: Some(sha256(b"rendered")),
                rendered_bytes: 8,
            }),
        );
        index.inventories.insert(
            sha256(b"binding"),
            ValidatedGitInventory::new(GitInventoryDescriptor {
                selected_root: "b".repeat(40),
                trees: std::collections::BTreeSet::from(["b".repeat(40)]),
                files: BTreeMap::from([(
                    "src/Example.java".into(),
                    super::super::oracle_git::GitFileDescriptor {
                        oid: "a".repeat(40),
                        mode: 0o100_644,
                        size: 8,
                    },
                )]),
            })
            .unwrap(),
        );
        index
    }

    #[test]
    fn reused_record_json_matches_the_independent_complete_serializer() {
        let mut index = fixture();
        index.renderer_identity = format!("blake3:{}", "a".repeat(64));
        let mut value = StoredCell::from(index.cells.values().next().unwrap().as_ref());
        value.counts = [1, 1, 1, 0, 1, 0, 0, 0, 0];
        value.normalizations.insert(
            "src/Unicode_é.java".into(),
            vec!["quote\" backslash\\ newline\n tab\t é".into()],
        );
        let cells = BTreeMap::from([
            (sha256(b"first key"), 0),
            (sha256(b"another key"), 0),
            (sha256(b"different record"), 1),
        ]);
        let values = vec![
            value,
            StoredCell::from(index.cells.values().next().unwrap().as_ref()),
        ];
        let encoded = values
            .iter()
            .map(|value| Ok(facet_json::to_string(value)?))
            .collect::<Result<Vec<_>>>()
            .unwrap();
        let actual = encode_payload(&index, &cells, &encoded).unwrap();
        let expected = facet_json::to_string(&StoredIndex {
            repository_identity: index.repository_identity.clone(),
            renderer_identity: index.renderer_identity.clone(),
            cells,
            values,
            inventories: owned_test_inventories(&index),
        })
        .unwrap();
        assert_eq!(actual, expected);
        let decoded: StoredIndex = facet_json::from_str(&actual).unwrap();
        assert_eq!(decoded.values.len(), 2);
        assert_eq!(decoded.cells.len(), 3);
        assert_eq!(decoded.inventories, owned_test_inventories(&index));
    }

    #[test]
    fn renderer_digest_algorithms_are_explicit_and_source_keys_remain_sha256_only() {
        for algorithm in ["sha256", "blake3"] {
            let value = format!("{algorithm}:{}", "a".repeat(64));
            assert!(validate_renderer_digest(&value).is_ok());
            assert_eq!(validate_digest(&value).is_ok(), algorithm == "sha256");
            assert!(ComparisonIndex::new(sha256(b"repository"), value).is_ok());
        }
        for invalid in [
            format!("blake3:{}", "a".repeat(63)),
            format!("blake3:{}", "A".repeat(64)),
            format!("blake3:{}", "g".repeat(64)),
            format!("sha1:{}", "a".repeat(64)),
            "blake3:".into(),
        ] {
            assert!(validate_renderer_digest(&invalid).is_err());
            assert!(ComparisonIndex::new(sha256(b"repository"), invalid).is_err());
        }
    }

    #[test]
    fn shared_immutable_bodies_preserve_exact_wire_bytes_and_validate_new_mutations() {
        let mut shared = fixture();
        let cell = shared.cells.values().next().unwrap().clone();
        for number in 0..32 {
            shared
                .cells
                .insert(sha256(number.to_string().as_bytes()), cell.clone());
        }
        let mut independent = fixture();
        independent.cells = shared
            .cells
            .iter()
            .map(|(key, cell)| (key.clone(), Arc::new(cell.as_ref().clone())))
            .collect();
        let first = tempfile::tempdir().unwrap();
        let second = tempfile::tempdir().unwrap();
        shared.save(first.path()).unwrap();
        independent.save(second.path()).unwrap();
        assert_eq!(
            fs::read(first.path().join(FILE_NAME)).unwrap(),
            fs::read(second.path().join(FILE_NAME)).unwrap(),
        );
        let key = shared.cells.keys().next().unwrap().clone();
        Arc::make_mut(shared.cells.get_mut(&key).unwrap())
            .counts
            .expected = 2;
        assert!(shared.validate().is_err());
        assert!(shared.save(first.path()).is_err());
        let mut malformed_key = independent;
        malformed_key.cells.insert("bad-key".into(), cell);
        assert!(
            malformed_key.validate().is_err(),
            "every aliased key is checked"
        );
    }

    #[test]
    fn lookup_keys_change_for_contents_context_policy_membership_and_oracle() {
        let initial = CellIdentity {
            context_identity: sha256(b"all context fields"),
            policy_identity: sha256(b"policy"),
            oracle_binding_identity: sha256(b"pin/commit/tree/inventory"),
            output_path: "src/Example.java".into(),
            input_path: Some("fragments/Example.java".into()),
            template: true,
            source_sha256: Some(sha256(b"contents")),
            oracle_blob: Some("a".repeat(40)),
            oracle_mode: Some(0o100644),
        };
        let original = initial.key().unwrap();
        for mutation in 0..9 {
            let mut changed = initial.clone();
            match mutation {
                0 => changed.source_sha256 = Some(sha256(b"edited with preserved timestamp")),
                1 => changed.context_identity = sha256(b"different feature/version context"),
                2 => changed.policy_identity = sha256(b"changed normalization policy"),
                3 => changed.output_path = "src/Other.java".into(),
                4 => changed.input_path = Some("fragments/Other.java".into()),
                5 => changed.template = false,
                6 => changed.oracle_blob = Some("b".repeat(40)),
                7 => changed.oracle_mode = Some(0o100755),
                8 => changed.oracle_binding_identity = sha256(b"different pin"),
                _ => unreachable!(),
            }
            assert_ne!(changed.key().unwrap(), original);
        }
        let mut missing = initial.clone();
        missing.input_path = None;
        assert!(missing.key().is_err());
        missing.source_sha256 = None;
        assert_ne!(missing.key().unwrap(), original);
        let mut left = initial.clone();
        left.output_path = "ab".into();
        left.input_path = Some("c".into());
        let mut right = initial.clone();
        right.output_path = "a".into();
        right.input_path = Some("bc".into());
        assert_ne!(left.key().unwrap(), right.key().unwrap());
        left.output_path = "a\0b".into();
        left.input_path = Some("c".into());
        right.output_path = "a".into();
        right.input_path = Some("b\0c".into());
        assert_ne!(left.key().unwrap(), right.key().unwrap());
        let mut absent = initial.clone();
        absent.oracle_blob = None;
        assert!(absent.key().is_err());
        absent.oracle_mode = None;
        assert_ne!(absent.key().unwrap(), original);
        for invalid_digest in ["sha256:x", "SHA256:invalid"] {
            absent.context_identity = invalid_digest.into();
            assert!(absent.key().is_err());
        }
        let mut invalid = fixture();
        Arc::make_mut(invalid.cells.values_mut().next().unwrap())
            .counts
            .matched = 0;
        assert!(invalid.validate().is_err());
    }

    #[test]
    fn persisted_cells_survive_restart_and_reject_other_identities() {
        let temp = tempfile::tempdir().unwrap();
        let index = fixture();
        assert!(
            ComparisonIndex::load(
                temp.path(),
                &index.repository_identity,
                &index.renderer_identity
            )
            .unwrap()
            .is_none()
        );
        index.save(temp.path()).unwrap();
        let restored = ComparisonIndex::load(
            temp.path(),
            &index.repository_identity,
            &index.renderer_identity,
        )
        .unwrap()
        .unwrap();
        assert_eq!(restored, index);
        assert!(
            ComparisonIndex::load(
                temp.path(),
                &sha256(b"another repository"),
                &index.renderer_identity
            )
            .is_err()
        );
        assert!(
            ComparisonIndex::load(
                temp.path(),
                &index.repository_identity,
                &sha256(b"another renderer")
            )
            .is_err()
        );
    }

    #[test]
    fn shared_storage_preserves_keys_and_rejects_invalid_record_references() {
        let temp = tempfile::tempdir().unwrap();
        let mut index = fixture();
        let shared = Arc::clone(index.cells.values().next().unwrap());
        index
            .cells
            .insert(sha256(b"another full context key"), shared);
        index.save(temp.path()).unwrap();
        let original = fs::read_to_string(temp.path().join(FILE_NAME)).unwrap();
        let payload = original.splitn(3, '\n').nth(2).unwrap();
        let stored: StoredIndex = facet_json::from_str(payload).unwrap();
        assert_eq!(stored.cells.len(), 2);
        assert_eq!(stored.values.len(), 1);
        let restored = ComparisonIndex::load(
            temp.path(),
            &index.repository_identity,
            &index.renderer_identity,
        )
        .unwrap()
        .unwrap();
        assert_eq!(restored, index);
        for mutation in 0..3 {
            let mut stored: StoredIndex = facet_json::from_str(payload).unwrap();
            match mutation {
                0 => *stored.cells.values_mut().next().unwrap() = usize::MAX,
                1 => stored.values.clear(),
                2 => stored.values.push(StoredCell::from(
                    index.cells.values().next().unwrap().as_ref(),
                )),
                _ => unreachable!(),
            }
            let changed = facet_json::to_string(&stored).unwrap();
            fs::write(
                temp.path().join(FILE_NAME),
                format!("{MAGIC}\n{}\n{changed}", sha256(changed.as_bytes())),
            )
            .unwrap();
            assert!(
                ComparisonIndex::load(
                    temp.path(),
                    &index.repository_identity,
                    &index.renderer_identity
                )
                .is_err()
            );
        }
        Arc::make_mut(index.cells.values_mut().next().unwrap()).rendered_bytes += 1;
        index.save(temp.path()).unwrap();
        let changed = fs::read_to_string(temp.path().join(FILE_NAME)).unwrap();
        let stored: StoredIndex =
            facet_json::from_str(changed.splitn(3, '\n').nth(2).unwrap()).unwrap();
        assert_eq!(stored.values.len(), 2);
    }

    #[test]
    fn shared_record_expansion_is_bounded_before_cloning() {
        let temp = tempfile::tempdir().unwrap();
        let index = fixture();
        index.save(temp.path()).unwrap();
        let original = fs::read_to_string(temp.path().join(FILE_NAME)).unwrap();
        let mut stored: StoredIndex =
            facet_json::from_str(original.splitn(3, '\n').nth(2).unwrap()).unwrap();
        stored.cells = (0..100)
            .map(|id| (sha256(format!("lookup {id}").as_bytes()), 0))
            .collect();
        let value = stored.values.first_mut().unwrap();
        value.counts = [1, 1, 1, 0, 1, 0, 0, 0, 0];
        value
            .normalizations
            .insert("src/Example.java".into(), vec!["a".repeat(1024 * 1024)]);
        let changed = facet_json::to_string(&stored).unwrap();
        fs::write(
            temp.path().join(FILE_NAME),
            format!("{MAGIC}\n{}\n{changed}", sha256(changed.as_bytes())),
        )
        .unwrap();
        let error = ComparisonIndex::load(
            temp.path(),
            &index.repository_identity,
            &index.renderer_identity,
        )
        .unwrap_err();
        assert!(
            error.to_string().contains("expansion exceeds byte limit"),
            "{error:?}"
        );
    }

    #[test]
    fn compact_storage_rejects_malformed_and_inconsistent_counters() {
        let temp = tempfile::tempdir().unwrap();
        let index = fixture();
        index.save(temp.path()).unwrap();
        let original = fs::read_to_string(temp.path().join(FILE_NAME)).unwrap();
        let payload = original.splitn(3, '\n').nth(2).unwrap();
        for counts in [
            [2, 1, 1, 1, 0, 0, 0, 0, 0],
            [1, 1, 0, 1, 0, 0, 0, 0, 0],
            [1, 1, 1, usize::MAX, 1, 0, 0, 0, 0],
        ] {
            let mut stored: StoredIndex = facet_json::from_str(payload).unwrap();
            stored.values.first_mut().unwrap().counts = counts;
            let changed = facet_json::to_string(&stored).unwrap();
            fs::write(
                temp.path().join(FILE_NAME),
                format!("{MAGIC}\n{}\n{changed}", sha256(changed.as_bytes())),
            )
            .unwrap();
            assert!(
                ComparisonIndex::load(
                    temp.path(),
                    &index.repository_identity,
                    &index.renderer_identity
                )
                .is_err()
            );
        }
        let malformed = payload.replace("\"counts\":[1,1,1,1,0,0,0,0,0]", "\"counts\":[1,1]");
        assert_ne!(malformed, payload);
        fs::write(
            temp.path().join(FILE_NAME),
            format!("{MAGIC}\n{}\n{malformed}", sha256(malformed.as_bytes())),
        )
        .unwrap();
        assert!(
            ComparisonIndex::load(
                temp.path(),
                &index.repository_identity,
                &index.renderer_identity
            )
            .is_err()
        );
    }

    #[test]
    fn corruption_truncation_and_abandoned_staging_never_become_completion() {
        let temp = tempfile::tempdir().unwrap();
        let index = fixture();
        index.save(temp.path()).unwrap();
        let original = fs::read(temp.path().join(FILE_NAME)).unwrap();
        let mut abandoned = tempfile::NamedTempFile::new_in(temp.path()).unwrap();
        abandoned.write_all(b"unfinished replacement").unwrap();
        assert!(
            ComparisonIndex::load(
                temp.path(),
                &index.repository_identity,
                &index.renderer_identity
            )
            .unwrap()
            .is_some()
        );
        for bytes in [original[..20].to_vec(), {
            let mut corrupted = original.clone();
            *corrupted.last_mut().unwrap() = b'!';
            corrupted
        }] {
            fs::write(temp.path().join(FILE_NAME), bytes).unwrap();
            assert!(
                ComparisonIndex::load(
                    temp.path(),
                    &index.repository_identity,
                    &index.renderer_identity
                )
                .is_err()
            );
        }
        index.save(temp.path()).unwrap();
        assert_eq!(fs::read(temp.path().join(FILE_NAME)).unwrap(), original);
    }
}
