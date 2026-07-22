//! Frozen repository-review bundle v1 model, validation, and producer.

use base64::Engine as _;
use base64::engine::general_purpose::STANDARD as BASE64_STANDARD;
use eyre::Context as _;
use eyre::eyre;
use facet::Facet;
use sha2::Digest as _;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
use unicode_normalization::UnicodeNormalization as _;

mod comparison;
mod providers;

use comparison::compare;
use comparison::validate_comparison;
use providers::check_snapshot_bounds;
use providers::load_snapshot;

pub const BUNDLE_SCHEMA: &str = "sfm.repository-review-bundle/1";
pub const SNAPSHOT_SCHEMA: &str = "sfm.repository-snapshot/1";
pub const COMPARISON_SCHEMA: &str = "sfm.repository-comparison/1";
pub const MAX_FILES: usize = 100_000;
pub const MAX_FILE_BYTES: usize = 16 * 1024 * 1024;
pub const MAX_SNAPSHOT_BYTES: usize = 256 * 1024 * 1024;
pub const MAX_JSON_BYTES: usize = 512 * 1024 * 1024;

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct RepositoryReviewBundleV1 {
    pub schema: String,
    pub id: String,
    pub name: String,
    pub repository: RepositoryDescriptionV1,
    pub producer: ProducerV1,
    pub before: RepositorySnapshotV1,
    pub after: RepositorySnapshotV1,
    pub comparison: RepositoryComparisonV1,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct RepositoryDescriptionV1 {
    pub name: String,
    pub display_path: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct ProducerV1 {
    pub id: String,
    pub contract_version: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct RepositorySnapshotV1 {
    pub schema: String,
    pub id: String,
    pub source: SnapshotSourceV1,
    pub files: Vec<SnapshotFileV1>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct SnapshotSourceV1 {
    pub kind: String,
    pub revision: String,
    pub label: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct SnapshotFileV1 {
    pub path: String,
    pub encoding: String,
    #[facet(skip_serializing_if = Option::is_none)]
    pub text: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub data: Option<String>,
    pub sha256: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct RepositoryComparisonV1 {
    pub schema: String,
    pub before_snapshot_id: String,
    pub after_snapshot_id: String,
    pub file_changes: Vec<FileChangeV1>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct FileChangeV1 {
    pub kind: String,
    pub before_path: Option<String>,
    pub after_path: Option<String>,
    pub binary: bool,
    pub operations: Vec<TextOperationV1>,
    pub diagnostics: Vec<String>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct TextOperationV1 {
    pub id: String,
    pub kind: String,
    pub before: Option<TextSelectionV1>,
    pub after: Option<TextSelectionV1>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct TextSelectionV1 {
    pub path: String,
    pub start_byte: usize,
    pub end_byte: usize,
    pub sha256: String,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum SnapshotProviderV1 {
    Git,
    Directory,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct BundleStatsV1 {
    pub before_files: usize,
    pub after_files: usize,
    pub before_bytes: usize,
    pub after_bytes: usize,
    pub file_changes: usize,
    pub operations: usize,
}

/// Reject duplicate object keys before Facet decoding, then parse and validate.
///
/// # Errors
///
/// Returns an error for oversized JSON, duplicate keys, malformed JSON, or any
/// violation of the frozen v1 contract.
pub fn parse(input: &str) -> eyre::Result<RepositoryReviewBundleV1> {
    if input.len() > MAX_JSON_BYTES {
        eyre::bail!("bundle JSON exceeds the {MAX_JSON_BYTES}-byte v1 limit");
    }
    DuplicateKeyScanner::new(input).scan()?;
    let bundle: RepositoryReviewBundleV1 = facet_json::from_str(input)
        .map_err(|error| eyre!("invalid repository-review bundle v1 JSON: {error:?}"))?;
    validate(&bundle)?;
    Ok(bundle)
}

/// Serialize a validated bundle deterministically with a trailing newline.
///
/// # Errors
///
/// Returns an error if validation or serialization fails.
pub fn to_canonical_json(bundle: &RepositoryReviewBundleV1) -> eyre::Result<String> {
    validate(bundle)?;
    let mut output = facet_json::to_string_pretty(bundle)
        .wrap_err("could not serialize repository-review bundle v1")?;
    output.push('\n');
    if output.len() > MAX_JSON_BYTES {
        eyre::bail!("encoded bundle exceeds the {MAX_JSON_BYTES}-byte v1 limit");
    }
    Ok(output)
}

/// Return the semantic digest represented by the validated bundle id.
///
/// # Errors
///
/// Returns an error when the bundle is invalid.
pub fn semantic_hash(bundle: &RepositoryReviewBundleV1) -> eyre::Result<String> {
    validate(bundle)?;
    Ok(bundle
        .id
        .strip_prefix("sha256:")
        .unwrap_or_default()
        .to_owned())
}

/// Validate all v1 bounds, hashes, references, paths, and operation ranges.
///
/// # Errors
///
/// Returns a fail-closed diagnostic for the first violated invariant.
pub fn validate(bundle: &RepositoryReviewBundleV1) -> eyre::Result<()> {
    require_equal(&bundle.schema, BUNDLE_SCHEMA, "bundle schema")?;
    validate_repository_name(&bundle.repository.name)?;
    if bundle.name.is_empty()
        || bundle.producer.id.is_empty()
        || bundle.producer.contract_version.is_empty()
    {
        eyre::bail!("bundle name and producer metadata must be non-empty");
    }
    let before = validate_snapshot(&bundle.before)?;
    let after = validate_snapshot(&bundle.after)?;
    let expected_id = bundle_id(&bundle.repository.name, &bundle.before.id, &bundle.after.id)?;
    if bundle.id != expected_id {
        eyre::bail!(
            "bundle id mismatch: expected {expected_id}, got {}",
            bundle.id
        );
    }
    validate_comparison(
        &bundle.comparison,
        &bundle.before,
        &before,
        &bundle.after,
        &after,
    )?;
    Ok(())
}

/// Compute the frozen content-addressed bundle id.
///
/// # Errors
///
/// Returns an error for an invalid repository identity or snapshot id.
pub fn bundle_id(repository_name: &str, before_id: &str, after_id: &str) -> eyre::Result<String> {
    validate_repository_name(repository_name)?;
    let before = parse_prefixed_digest(before_id, "before snapshot id")?;
    let after = parse_prefixed_digest(after_id, "after snapshot id")?;
    let name = repository_name.as_bytes();
    let length = u32::try_from(name.len()).wrap_err("repository name exceeds u32 framing")?;
    let mut hasher = Sha256::new();
    hasher.update(BUNDLE_SCHEMA.as_bytes());
    hasher.update([0]);
    hasher.update(length.to_be_bytes());
    hasher.update(name);
    hasher.update(before);
    hasher.update(after);
    Ok(format!("sha256:{:x}", hasher.finalize()))
}

/// Load two immutable snapshots through an explicit provider and compare them.
///
/// # Errors
///
/// Returns an error if source discovery, byte loading, validation, or comparison
/// fails. Git inspection never checks out or mutates the repository.
pub fn prepare(
    provider: SnapshotProviderV1,
    repository: &Path,
    repository_name: &str,
    display_name: &str,
    before_value: &str,
    after_value: &str,
) -> eyre::Result<RepositoryReviewBundleV1> {
    validate_repository_name(repository_name)?;
    let before = load_snapshot(provider, repository, before_value, "Before")?;
    let after = load_snapshot(provider, repository, after_value, "After")?;
    let comparison = compare(&before, &after)?;
    let id = bundle_id(repository_name, &before.id, &after.id)?;
    let bundle = RepositoryReviewBundleV1 {
        schema: BUNDLE_SCHEMA.to_owned(),
        id,
        name: display_name.to_owned(),
        repository: RepositoryDescriptionV1 {
            name: repository_name.to_owned(),
            display_path: repository.display().to_string(),
        },
        producer: ProducerV1 {
            id: "sfm-propagate-changes".to_owned(),
            contract_version: "1".to_owned(),
        },
        before,
        after,
        comparison,
    };
    validate(&bundle)?;
    Ok(bundle)
}

/// Compute bundle file, byte, change, and operation counts.
#[must_use]
pub fn stats(bundle: &RepositoryReviewBundleV1) -> BundleStatsV1 {
    BundleStatsV1 {
        before_files: bundle.before.files.len(),
        after_files: bundle.after.files.len(),
        before_bytes: bundle
            .before
            .files
            .iter()
            .map(|file| file_bytes(file).map_or(0, |bytes| bytes.len()))
            .sum(),
        after_bytes: bundle
            .after
            .files
            .iter()
            .map(|file| file_bytes(file).map_or(0, |bytes| bytes.len()))
            .sum(),
        file_changes: bundle.comparison.file_changes.len(),
        operations: bundle
            .comparison
            .file_changes
            .iter()
            .map(|change| change.operations.len())
            .sum(),
    }
}

fn validate_snapshot(snapshot: &RepositorySnapshotV1) -> eyre::Result<BTreeMap<String, Vec<u8>>> {
    require_equal(&snapshot.schema, SNAPSHOT_SCHEMA, "snapshot schema")?;
    if snapshot.source.kind.is_empty()
        || snapshot.source.revision.is_empty()
        || snapshot.source.label.is_empty()
    {
        eyre::bail!("snapshot source metadata must be non-empty");
    }
    check_snapshot_bounds(snapshot.files.len(), 0)?;
    let mut result = BTreeMap::new();
    let mut previous: Option<&str> = None;
    let mut total = 0usize;
    for file in &snapshot.files {
        validate_path(&file.path)?;
        if previous.is_some_and(|value| value.as_bytes() >= file.path.as_bytes()) {
            eyre::bail!(
                "snapshot paths are duplicate or not UTF-8-byte sorted at {}",
                file.path
            );
        }
        previous = Some(&file.path);
        let bytes = file_bytes(file)?;
        if bytes.len() > MAX_FILE_BYTES {
            eyre::bail!("file {} exceeds the per-file limit", file.path);
        }
        total = total
            .checked_add(bytes.len())
            .ok_or_else(|| eyre!("snapshot byte count overflow"))?;
        if total > MAX_SNAPSHOT_BYTES {
            eyre::bail!("snapshot exceeds the decoded-byte limit");
        }
        validate_digest(
            &file.sha256,
            &sha256(&bytes),
            &format!("file {}", file.path),
        )?;
        result.insert(file.path.clone(), bytes);
    }
    let expected = snapshot_id(&snapshot.files)?;
    if snapshot.id != expected {
        eyre::bail!(
            "snapshot id mismatch: expected {expected}, got {}",
            snapshot.id
        );
    }
    Ok(result)
}

fn snapshot_id(files: &[SnapshotFileV1]) -> eyre::Result<String> {
    let mut hasher = Sha256::new();
    hasher.update(SNAPSHOT_SCHEMA.as_bytes());
    hasher.update([0]);
    for file in files {
        validate_path(&file.path)?;
        let path = file.path.as_bytes();
        hasher.update(
            u32::try_from(path.len())
                .wrap_err("path exceeds u32 framing")?
                .to_be_bytes(),
        );
        hasher.update(path);
        hasher.update([match file.encoding.as_str() {
            "utf8" => 0,
            "base64" => 1,
            other => eyre::bail!("unknown file encoding {other:?}"),
        }]);
        let bytes = file_bytes(file)?;
        hasher.update(
            u64::try_from(bytes.len())
                .wrap_err("file exceeds u64 framing")?
                .to_be_bytes(),
        );
        hasher.update(bytes);
    }
    Ok(format!("sha256:{:x}", hasher.finalize()))
}

fn file_bytes(file: &SnapshotFileV1) -> eyre::Result<Vec<u8>> {
    match (file.encoding.as_str(), &file.text, &file.data) {
        ("utf8", Some(text), None) => Ok(text.as_bytes().to_vec()),
        ("base64", None, Some(data)) => BASE64_STANDARD
            .decode(data)
            .wrap_err_with(|| format!("invalid padded base64 for {}", file.path)),
        (encoding, _, _) => eyre::bail!(
            "file {} has invalid {encoding:?} text/data fields",
            file.path
        ),
    }
}

fn validate_repository_name(value: &str) -> eyre::Result<()> {
    if value.is_empty() || value.nfc().collect::<String>() != value {
        eyre::bail!("repository name must be non-empty NFC");
    }
    Ok(())
}

fn validate_path(value: &str) -> eyre::Result<()> {
    if value.is_empty()
        || value.contains(['\\', '\0'])
        || value.starts_with('/')
        || value.nfc().collect::<String>() != value
    {
        eyre::bail!("invalid repository path {value:?}");
    }
    if value.len() >= 2 && value.as_bytes()[1] == b':' || value.starts_with("//") {
        eyre::bail!("rooted repository path is forbidden: {value:?}");
    }
    if value
        .split('/')
        .any(|segment| segment.is_empty() || matches!(segment, "." | ".."))
    {
        eyre::bail!("repository path has an empty/dot segment: {value:?}");
    }
    Ok(())
}

fn validate_digest(actual: &str, expected: &str, context: &str) -> eyre::Result<()> {
    if !is_digest(actual) || actual != expected {
        eyre::bail!("{context} SHA-256 mismatch: expected {expected}, got {actual}");
    }
    Ok(())
}

fn parse_prefixed_digest(value: &str, context: &str) -> eyre::Result<[u8; 32]> {
    let digest = value
        .strip_prefix("sha256:")
        .ok_or_else(|| eyre!("{context} lacks sha256: prefix"))?;
    if !is_digest(digest) {
        eyre::bail!("{context} is not lowercase SHA-256");
    }
    let mut output = [0; 32];
    for (index, pair) in digest.as_bytes().chunks_exact(2).enumerate() {
        output[index] = (hex(pair[0])? << 4) | hex(pair[1])?;
    }
    Ok(output)
}

fn hex(value: u8) -> eyre::Result<u8> {
    match value {
        b'0'..=b'9' => Ok(value - b'0'),
        b'a'..=b'f' => Ok(value - b'a' + 10),
        _ => eyre::bail!("invalid lowercase hex"),
    }
}
fn is_digest(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || matches!(byte, b'a'..=b'f'))
}
fn sha256(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}
fn require_equal(actual: &str, expected: &str, context: &str) -> eyre::Result<()> {
    if actual == expected {
        Ok(())
    } else {
        eyre::bail!("unsupported {context} {actual:?}; expected {expected:?}")
    }
}

struct DuplicateKeyScanner<'a> {
    input: &'a [u8],
    cursor: usize,
}
impl<'a> DuplicateKeyScanner<'a> {
    fn new(input: &'a str) -> Self {
        Self {
            input: input.as_bytes(),
            cursor: 0,
        }
    }
    fn scan(mut self) -> eyre::Result<()> {
        self.ws();
        self.value()?;
        self.ws();
        if self.cursor != self.input.len() {
            eyre::bail!("trailing JSON content")
        }
        Ok(())
    }
    fn value(&mut self) -> eyre::Result<()> {
        self.ws();
        match self.peek() {
            Some(b'{') => self.object(),
            Some(b'[') => self.array(),
            Some(b'"') => {
                self.string()?;
                Ok(())
            }
            Some(b't') => self.literal(b"true"),
            Some(b'f') => self.literal(b"false"),
            Some(b'n') => self.literal(b"null"),
            Some(b'-' | b'0'..=b'9') => self.number(),
            _ => eyre::bail!("malformed JSON at byte {}", self.cursor),
        }
    }
    fn object(&mut self) -> eyre::Result<()> {
        self.take(b'{')?;
        self.ws();
        let mut keys = BTreeSet::new();
        if self.consume(b'}') {
            return Ok(());
        }
        loop {
            self.ws();
            let key = self.string()?;
            if !keys.insert(key.clone()) {
                eyre::bail!("duplicate JSON object key {key:?}");
            }
            self.ws();
            self.take(b':')?;
            self.value()?;
            self.ws();
            if self.consume(b'}') {
                return Ok(());
            }
            self.take(b',')?;
        }
    }
    fn array(&mut self) -> eyre::Result<()> {
        self.take(b'[')?;
        self.ws();
        if self.consume(b']') {
            return Ok(());
        }
        loop {
            self.value()?;
            self.ws();
            if self.consume(b']') {
                return Ok(());
            }
            self.take(b',')?;
        }
    }
    fn string(&mut self) -> eyre::Result<String> {
        let start = self.cursor;
        self.take(b'"')?;
        let mut escaped = false;
        while let Some(byte) = self.peek() {
            self.cursor += 1;
            if escaped {
                escaped = false;
                continue;
            }
            if byte == b'\\' {
                escaped = true;
            } else if byte == b'"' {
                let raw = std::str::from_utf8(&self.input[start..self.cursor])?;
                return facet_json::from_str(raw)
                    .map_err(|error| eyre!("invalid JSON string: {error:?}"));
            } else if byte < 0x20 {
                eyre::bail!("control byte in JSON string");
            }
        }
        eyre::bail!("unterminated JSON string")
    }
    fn literal(&mut self, value: &[u8]) -> eyre::Result<()> {
        if self.input.get(self.cursor..self.cursor + value.len()) == Some(value) {
            self.cursor += value.len();
            Ok(())
        } else {
            eyre::bail!("invalid JSON literal")
        }
    }
    fn number(&mut self) -> eyre::Result<()> {
        let start = self.cursor;
        while self
            .peek()
            .is_some_and(|byte| matches!(byte, b'-' | b'+' | b'.' | b'e' | b'E' | b'0'..=b'9'))
        {
            self.cursor += 1;
        }
        if self.cursor == start {
            eyre::bail!("invalid JSON number")
        }
        Ok(())
    }
    fn ws(&mut self) {
        while self.peek().is_some_and(|byte| byte.is_ascii_whitespace()) {
            self.cursor += 1;
        }
    }
    fn peek(&self) -> Option<u8> {
        self.input.get(self.cursor).copied()
    }
    fn consume(&mut self, byte: u8) -> bool {
        if self.peek() == Some(byte) {
            self.cursor += 1;
            true
        } else {
            false
        }
    }
    fn take(&mut self, byte: u8) -> eyre::Result<()> {
        if self.consume(byte) {
            Ok(())
        } else {
            eyre::bail!(
                "expected JSON byte {:?} at {}",
                char::from(byte),
                self.cursor
            )
        }
    }
}

#[cfg(test)]
mod tests;
