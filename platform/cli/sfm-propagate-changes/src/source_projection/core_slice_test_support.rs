//! Test-only readers shared by the bounded authored-core slice regressions.
//!
//! Historical objects are witnesses here, never production rendering inputs.
//! No helper writes a catalog, source, generated project or Git configuration.

#![cfg(test)]

use super::candidate_lock::checked_directory;
use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_features::CoreFeatureDefinition;
use super::core_features::CoreFeatureDefinitions;
use super::core_inputs::CORE_METADATA_PATH;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::CoreSelection;
use super::core_inputs::MAX_CORE_METADATA_BYTES;
use super::core_inputs::select_core_inputs;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use sha1::Digest;
use sha1::Sha1;
use std::cell::Ref;
use std::cell::RefCell;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::BufRead;
use std::io::BufReader;
use std::io::Read;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::process::Stdio;

const MAX_SOURCE_BYTES: u64 = 1024 * 1024;
const MAX_BLOB_TOTAL_BYTES: u64 = 16 * 1024 * 1024;
const MAX_BLOB_COUNT: usize = 128;

// Fixed-wave reader admission; existing shared parser and other callers are unchanged.
#[path = "core_wave_process_capture.rs"]
mod wave_process_capture;

#[cfg(windows)]
pub(super) use wave_process_capture::read_wave_git_blobs;
#[cfg(windows)]
pub(super) use wave_process_capture::read_wave_git_tree;

pub(super) struct CoreTestFixture {
    pub repository: PathBuf,
    pub core: PathBuf,
    pub metadata: CoreProjectInputs,
    pub features: CoreFeatureDefinitions,
    catalog: CoreCatalog,
    selection_cache: RefCell<Option<TestSelectionSnapshot>>,
}

struct TestSelectionSnapshot {
    context_json: String,
    metadata: CoreProjectInputs,
    inventory: BTreeSet<String>,
    selection: CoreSelection,
}

impl CoreTestFixture {
    pub(super) fn load() -> Result<Self> {
        let repository = Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .ok_or_else(|| eyre::eyre!("CLI crate must be three levels below the repository"))?;
        let catalog = CoreCatalog::load(repository, repository)?;
        let repository = catalog.repo_root.clone();
        let core = checked_directory(&repository.join(CORE_ROOT))?;
        let metadata_path = checked_file(&repository, CORE_METADATA_PATH)?;
        let metadata_bytes = read_bounded(&metadata_path, MAX_CORE_METADATA_BYTES)?;
        let metadata = CoreProjectInputs::from_json(
            std::str::from_utf8(&metadata_bytes)?,
            &catalog.registered_features,
        )?;
        let features = CoreFeatureDefinitions(catalog.definitions.0.clone());
        Ok(Self {
            repository,
            core,
            metadata,
            features,
            catalog,
            selection_cache: RefCell::new(None),
        })
    }

    /// Reuse only the last successful selection within this test fixture.
    /// Compare complete values, not timestamps or addresses. Source bytes are
    /// deliberately not cached: callers still read and verify every selected
    /// source, and direct mutation tests retain the fresh selector.
    pub(super) fn selection_for_assertion(
        &self,
        context: &ProjectionContext,
        inventory: &BTreeSet<String>,
    ) -> Result<Ref<'_, CoreSelection>> {
        let context_json = facet_json::to_string(context)?;
        let hit = self
            .selection_cache
            .try_borrow()?
            .as_ref()
            .is_some_and(|snapshot| {
                snapshot.context_json == context_json
                    && snapshot.metadata == self.metadata
                    && snapshot.inventory == *inventory
            });
        if !hit {
            let selection = select_core_inputs(&self.metadata, context, inventory)?;
            *self.selection_cache.try_borrow_mut()? = Some(TestSelectionSnapshot {
                context_json,
                metadata: self.metadata.clone(),
                inventory: inventory.clone(),
                selection,
            });
        }
        Ok(Ref::map(self.selection_cache.try_borrow()?, |snapshot| {
            &snapshot
                .as_ref()
                .expect("successful fixture selection")
                .selection
        }))
    }

    /// Start with the real catalog's target mapping, then validate an explicit
    /// test feature set. Current release/dev feature defaults are not inherited.
    pub(super) fn context(&self, target: &str, enabled: &[&str]) -> Result<ProjectionContext> {
        let (key, entry) = self
            .catalog
            .catalog
            .0
            .iter()
            .find(|(_, entry)| entry.target_id().is_ok_and(|id| id == target))
            .ok_or_else(|| eyre::eyre!("catalog has no context for target `{target}`"))?;
        let mut explicit = entry.clone();
        explicit.features = enabled
            .iter()
            .map(|feature| (*feature).to_owned())
            .collect();
        self.features.validate_entry(&explicit)?;
        let mut context = self.catalog.context(key)?;
        context.features = explicit.feature_flags(&self.features.registered_names())?;
        Ok(context)
    }

    /// Explicit named feature-off controls for immutable historical source and
    /// omission checks. Current catalog feature selections remain untouched.
    pub(super) fn historical_feature_off_catalog_context(
        &self,
        key: &str,
    ) -> Result<ProjectionContext> {
        let mut historical = self.catalog.catalog.entry(key)?.clone();
        historical.features.clear();
        self.features.validate_entry(&historical)?;
        let mut context = self.catalog.context(key)?;
        context.features = historical.feature_flags(&self.catalog.registered_features)?;
        Ok(context)
    }

    pub(super) fn read_source(&self, relative: &str) -> Result<Vec<u8>> {
        ensure!(
            relative.starts_with("src/") && relative.ends_with(".java"),
            "test source must be a core-relative Java input"
        );
        read_bounded(&checked_file(&self.core, relative)?, MAX_SOURCE_BYTES)
    }
}

/// The immutable original 180-owner registry is historical evidence only.
/// Current contexts and per-owner contracts continue using the actual catalog.
pub(super) fn historical_feature_registry() -> Result<(&'static [u8], CoreFeatureDefinitions)> {
    let bytes = include_bytes!(
        "../../tests/fixtures/source_projection/feature-definitions-original180.json"
    );
    Ok((bytes, validate_historical_feature_registry(bytes)?))
}

fn validate_historical_feature_registry(bytes: &[u8]) -> Result<CoreFeatureDefinitions> {
    ensure!(
        bytes.len() == 30254
            && sha256(bytes)
                == "sha256:38d9a82444b8478e80018a51ebcbd99a959788f3ccf38237d4175455be810b83",
        "immutable original180 registry witness changed"
    );
    let definitions = CoreFeatureDefinitions::from_json(std::str::from_utf8(bytes)?)?;
    ensure!(
        definitions.0.len() == 180,
        "historical registry owner count changed"
    );
    Ok(definitions)
}

/// Callers resolve the file against their checked repository/core boundary.
pub(super) fn read_bounded(path: &Path, limit: u64) -> Result<Vec<u8>> {
    ensure!(
        limit > 0 && limit <= MAX_BLOB_TOTAL_BYTES,
        "invalid bounded fixture read limit"
    );
    let file = fs::File::open(path).wrap_err_with(|| format!("cannot open {}", path.display()))?;
    let metadata = file.metadata()?;
    ensure!(
        metadata.is_file() && metadata.len() <= limit,
        "fixture file is not regular or exceeds its {limit}-byte limit"
    );
    let mut bytes = Vec::new();
    file.take(limit + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= limit,
        "fixture grew beyond its read limit"
    );
    Ok(bytes)
}

pub(super) fn read_git_blobs(
    repository: &Path,
    oids: &BTreeSet<String>,
) -> Result<BTreeMap<String, Vec<u8>>> {
    ensure!(
        oids.len() <= MAX_BLOB_COUNT,
        "too many bounded fixture blobs"
    );
    ensure!(
        oids.iter().all(|oid| {
            oid.len() == 40
                && oid
                    .bytes()
                    .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
        }),
        "fixture object IDs must be exact lowercase SHA-1 blobs"
    );
    if oids.is_empty() {
        return Ok(BTreeMap::new());
    }
    let mut child = frozen_git_command(repository)
        .env("GIT_ALLOW_PROTOCOL", "")
        .env("GIT_TERMINAL_PROMPT", "0")
        .args([
            "-c",
            "protocol.allow=never",
            "-c",
            "core.fsmonitor=false",
            "cat-file",
            "--batch",
        ])
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .wrap_err("cannot start offline fixture Git reader")?;
    let mut input = child.stdin.take().expect("piped Git input");
    let mut reader = BufReader::new(child.stdout.take().expect("piped Git output"));
    let result = read_blob_batch(&mut input, &mut reader, oids);
    drop(input);
    drop(reader);
    if result.is_err() {
        // Stop only the Git child spawned by this exact fixture call.
        let _ = child.kill();
        let _ = child.wait();
        return result;
    }
    ensure!(child.wait()?.success(), "offline fixture Git reader failed");
    result
}

fn read_blob_batch(
    input: &mut impl Write,
    reader: &mut impl BufRead,
    oids: &BTreeSet<String>,
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
            "invalid offline fixture blob header"
        );
        ensure!(
            fields[2].bytes().all(|byte| byte.is_ascii_digit()),
            "offline fixture size must be unsigned decimal digits"
        );
        let size: u64 = fields[2].parse()?;
        ensure!(
            header == format!("{oid} blob {size}\n"),
            "offline fixture header must use exact Git framing"
        );
        total = total
            .checked_add(size)
            .ok_or_else(|| eyre::eyre!("fixture size overflow"))?;
        ensure!(
            size <= MAX_SOURCE_BYTES && total <= MAX_BLOB_TOTAL_BYTES,
            "fixture blobs exceed their individual/aggregate limits"
        );
        let mut bytes = vec![0; usize::try_from(size)?];
        reader.read_exact(&mut bytes)?;
        let mut separator = [0_u8];
        reader.read_exact(&mut separator)?;
        let mut digest = Sha1::new();
        digest.update(format!("blob {}\0", bytes.len()).as_bytes());
        digest.update(&bytes);
        ensure!(
            separator == [b'\n'] && format!("{:x}", digest.finalize()) == *oid,
            "offline fixture raw blob verification failed"
        );
        blobs.insert(oid.clone(), bytes);
    }
    Ok(blobs)
}

#[test]
fn fixture_selection_reuses_only_complete_equal_inputs() -> Result<()> {
    let fixture = CoreTestFixture::load()?;
    let inventory = super::core_inputs::discover_core_source_files(&fixture.core)?;
    let off = fixture.context("1.19.2", &[])?;
    let fresh = select_core_inputs(&fixture.metadata, &off, &inventory)?;
    {
        let first = fixture.selection_for_assertion(&off, &inventory)?;
        let repeated = fixture.selection_for_assertion(&off, &inventory)?;
        assert!(std::ptr::eq(&*first, &*repeated));
        assert_eq!(&*repeated, &fresh);
    }
    let on = fixture.context("1.19.2", &["client_theme"])?;
    assert_eq!(
        &*fixture.selection_for_assertion(&on, &inventory)?,
        &select_core_inputs(&fixture.metadata, &on, &inventory)?
    );
    let mut added = inventory.clone();
    added.insert("src/main/resources/selection-cache-probe.txt".into());
    assert_eq!(
        &*fixture.selection_for_assertion(&on, &added)?,
        &select_core_inputs(&fixture.metadata, &on, &added)?
    );
    assert_eq!(&*fixture.selection_for_assertion(&off, &inventory)?, &fresh);
    Ok(())
}

#[test]
fn fixture_selection_never_hides_changed_metadata_or_invalid_inputs() -> Result<()> {
    let mut fixture = CoreTestFixture::load()?;
    let inventory = super::core_inputs::discover_core_source_files(&fixture.core)?;
    let context = fixture.context("1.19.2", &[])?;
    drop(fixture.selection_for_assertion(&context, &inventory)?);
    let original_schema = fixture.metadata.schema_version;
    fixture.metadata.schema_version = u32::MAX;
    assert!(
        fixture
            .selection_for_assertion(&context, &inventory)
            .is_err()
    );
    assert!(
        fixture
            .selection_for_assertion(&context, &inventory)
            .is_err()
    );
    fixture.metadata.schema_version = original_schema;
    let mut bad_inventory = inventory.clone();
    bad_inventory.insert("src/CON.java".into());
    assert!(
        fixture
            .selection_for_assertion(&context, &bad_inventory)
            .is_err()
    );
    let mut missing_feature = context.clone();
    missing_feature.features.remove("client_theme");
    assert!(
        fixture
            .selection_for_assertion(&missing_feature, &inventory)
            .is_err()
    );
    assert_eq!(
        &*fixture.selection_for_assertion(&context, &inventory)?,
        &select_core_inputs(&fixture.metadata, &context, &inventory)?
    );
    Ok(())
}

#[test]
fn current_catalog_selections_stay_distinct_from_historical_feature_off_controls() -> Result<()> {
    let fixture = CoreTestFixture::load()?;
    assert_eq!(fixture.catalog.catalog.0.len(), 20);
    let inventory = super::core_inputs::discover_core_source_files(&fixture.core)?;
    for (key, entry) in &fixture.catalog.catalog.0 {
        let current = fixture.catalog.context(key)?;
        let historical = fixture.historical_feature_off_catalog_context(key)?;
        let enabled = current
            .features
            .iter()
            .filter(|(_, enabled)| **enabled)
            .map(|(name, _)| name.clone())
            .collect::<BTreeSet<_>>();
        assert_eq!(
            enabled,
            entry.features.iter().cloned().collect::<BTreeSet<_>>(),
            "{key}"
        );
        assert!(
            historical.features.values().all(|enabled| !enabled),
            "{key}"
        );
        assert_eq!(
            historical.minecraft_version, current.minecraft_version,
            "{key}"
        );
        assert_eq!(historical.environment, current.environment, "{key}");
        assert_eq!(historical.preset, current.preset, "{key}");
        assert_eq!(historical.projection_key, current.projection_key, "{key}");
        assert_eq!(historical.targets, current.targets, "{key}");
        // Both views use the production selector. Neither view changes the catalog
        // or substitutes an empty inventory to conceal selected inputs.
        super::core_inputs::select_core_inputs(&fixture.metadata, &current, &inventory)?;
        super::core_inputs::select_core_inputs(&fixture.metadata, &historical, &inventory)?;
    }
    let current = fixture.catalog.context("sfm-dev/mc-1.19.2")?;
    let historical = fixture.historical_feature_off_catalog_context("sfm-dev/mc-1.19.2")?;
    assert!(current.features["document_history"]);
    assert!(!historical.features["document_history"]);
    assert!(fixture.catalog.context("sfm-dev/mc-1.19.2")?.features["document_history"]);
    Ok(())
}

#[test]
fn unrelated_current_owners_do_not_change_historical_registry_receipt() -> Result<()> {
    let fixture = CoreTestFixture::load()?;
    let (before, historical) = historical_feature_registry()?;
    for name in ["font_render_surface_audit", "locked_dependency_projection"] {
        assert!(!historical.0.contains_key(name));
        let context = fixture.context("1.19.2", &[name])?;
        assert!(context.features[name]);
    }
    // A valid unrelated future owner changes only this private current view.
    // The retained historical receipt is neither derived from nor stripped out
    // of a live registry, and current catalog defaults remain unchanged.
    let mut current = CoreFeatureDefinitions(fixture.features.0.clone());
    current.0.insert(
        "historical_receipt_unrelated_owner".into(),
        CoreFeatureDefinition {
            supported_targets: vec!["1.19.2".into()],
            requires: vec![],
        },
    );
    current.validate()?;
    let mut entry = fixture.catalog.catalog.entry("sfm-dev/mc-1.19.2")?.clone();
    entry.features = vec!["historical_receipt_unrelated_owner".into()];
    current.validate_entry(&entry)?;
    let (after, unchanged) = historical_feature_registry()?;
    assert_eq!(before, after);
    assert_eq!(historical.registered_names(), unchanged.registered_names());
    assert!(
        !unchanged
            .0
            .contains_key("historical_receipt_unrelated_owner")
    );
    Ok(())
}

#[test]
fn historical_registry_receipt_rejects_any_byte_drift() -> Result<()> {
    let (bytes, _) = historical_feature_registry()?;
    let mut damaged = bytes.to_vec();
    damaged[0] ^= 1;
    assert!(validate_historical_feature_registry(&damaged).is_err());
    let mut extra_blank_line = bytes.to_vec();
    extra_blank_line.push(b'\n');
    assert!(validate_historical_feature_registry(&extra_blank_line).is_err());
    let crlf = std::str::from_utf8(bytes)?.replace('\n', "\r\n");
    assert!(validate_historical_feature_registry(crlf.as_bytes()).is_err());
    Ok(())
}

#[test]
fn bounded_blob_fixture_reader_preserves_binary_bytes() -> Result<()> {
    let bytes = [0xff, 0, b'\n', 42];
    let mut digest = Sha1::new();
    digest.update(format!("blob {}\0", bytes.len()).as_bytes());
    digest.update(bytes);
    let oid = format!("{:x}", digest.finalize());
    let mut response = format!("{oid} blob {}\n", bytes.len()).into_bytes();
    response.extend_from_slice(&bytes);
    response.push(b'\n');
    let mut input = Vec::new();
    let blobs = read_blob_batch(
        &mut input,
        &mut std::io::Cursor::new(response),
        &BTreeSet::from([oid.clone()]),
    )?;
    assert_eq!(blobs[&oid], bytes);
    assert_eq!(input, format!("{oid}\n").as_bytes());
    Ok(())
}

#[test]
fn bounded_blob_fixture_reader_rejects_noncanonical_or_oversized_headers() {
    let oid = "0".repeat(40);
    for header in [
        format!("{oid} missing\n"),
        format!("{oid} tree 1\n"),
        format!("{oid} blob +1\n"),
        format!("{oid} blob 01\n"),
        format!("{oid} blob 1\r\n"),
        format!("{oid} blob 1048577\n"),
        format!("{oid} blob 18446744073709551616\n"),
        format!("{oid} blob 1"),
    ] {
        assert!(
            read_blob_batch(
                &mut Vec::new(),
                &mut std::io::Cursor::new(header.as_bytes()),
                &BTreeSet::from([oid.clone()]),
            )
            .is_err(),
            "accepted invalid blob header: {header:?}"
        );
    }
}

#[test]
fn bounded_blob_fixture_reader_rejects_wrong_digest_truncation_and_delimiter() {
    let oid = "0".repeat(40);
    for response in [
        format!("{oid} blob 1\na\n"),
        format!("{oid} blob 1\na!"),
        format!("{oid} blob 1\n"),
    ] {
        assert!(
            read_blob_batch(
                &mut Vec::new(),
                &mut std::io::Cursor::new(response),
                &BTreeSet::from([oid.clone()]),
            )
            .is_err()
        );
    }
}

#[test]
fn bounded_fixture_file_reader_refuses_invalid_limits_and_oversized_files() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let path = temp.path().join("bounded.txt");
    fs::write(&path, b"fixture")?;
    assert_eq!(read_bounded(&path, 7)?, b"fixture");
    assert!(read_bounded(&path, 6).is_err());
    assert!(read_bounded(&path, 0).is_err());
    assert!(read_bounded(&path, MAX_BLOB_TOTAL_BYTES + 1).is_err());
    assert!(read_bounded(temp.path(), 7).is_err());
    Ok(())
}

#[test]
fn offline_fixture_reader_refuses_nonblob_identifiers_before_starting_git() {
    for identifier in [
        "HEAD:src/Example.java".to_owned(),
        "A123".to_owned(),
        "A".repeat(40),
    ] {
        assert!(read_git_blobs(Path::new("."), &BTreeSet::from([identifier])).is_err());
    }
}
