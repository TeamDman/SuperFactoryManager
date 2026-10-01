//! Frozen migration evidence for the first four authored language templates.
//!
//! These tests use the real renderer and core membership selector, not the
//! independent mechanical-expansion verifier recorded in the prose ledger.
//! Historical Git objects are test-only golden evidence: production authoring
//! and generation must never consult them. The feature-on Java slice still
//! requires parser/runtime companions and is not claimed to compile here.
//!
//! Deliberate future core edits may change this migration golden. Review and
//! update the bounded first-slice evidence explicitly; do not turn a golden
//! mismatch into an automatic per-target hash-adoption or snapshot fallback.

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;

const LEDGER_PATH: &str = "docs/tasks/sfm-core-language-first-slice.json";
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;
const MAX_SOURCE_BYTES: u64 = 64 * 1024;
const EXECUTION_SIDE: &str = "sfml_execution_side";
const WORDED_INTERVALS: &str = "sfml_worded_intervals";
const INTERVAL_PATH: &str = "src/main/java/ca/teamdman/sfml/ast/Interval.java";
const EXECUTION_SIDE_PATH: &str = "src/main/java/ca/teamdman/sfml/ast/ProgramExecutionSide.java";
const PROGRAM_BUILDER_PATH: &str =
    "src/main/java/ca/teamdman/sfml/program_builder/ProgramBuilder.java";
const LEGACY_LINTER_PATH: &str =
    "src/main/java/ca/teamdman/sfm/common/program/linting/LegacyIntervalOffsetProgramLinter.java";
const SOURCE_PATHS: [&str; 4] = [
    INTERVAL_PATH,
    EXECUTION_SIDE_PATH,
    PROGRAM_BUILDER_PATH,
    LEGACY_LINTER_PATH,
];

const INTERVAL_OFF: &str = "3a5839c13468beadcebcd28b08a53014cd61f526";
const INTERVAL_ON: &str = "4895a66f2bf98ee32ae2886d7c2aafc3d12babe5";
const SIDE_ENUM_ON: &str = "f8160e83d6d6fa204629bcacee7356e06b120cb2";
const BUILDER_OFF: &str = "aa46e58898d5136a819e9fe8e016cde78113077b";
const BUILDER_OFF_26: &str = "2411991dd47f63d7e53c435e5d8b5fd3a5cda9b8";
const BUILDER_ON: &str = "f5c58bec75f66d062fb706373a1f819446712d49";
const LINTER_ON: &str = "10e5e96d97ce75e3546f1e96b4f2bf60b61b5bb0";

const PINNED_BLOBS: [(&str, &str, u64); 7] = [
    (
        INTERVAL_OFF,
        "sha256:bb41cd8d513e39b23582846e9dce7f4993b9a005f2aabe2414632dd7a45c56c4",
        696,
    ),
    (
        INTERVAL_ON,
        "sha256:91357173b4ed1f7c9f3fe2d3a0ebe5c3750ac27f95d1a5e182a1dccaa371661a",
        1072,
    ),
    (
        SIDE_ENUM_ON,
        "sha256:49f9e235d80aa8c3d87cb85d2d21d892fe5b9b5f1607a22a6a7714a268552ae0",
        188,
    ),
    (
        BUILDER_OFF,
        "sha256:cf2fe9ab9817c7652bc7652922892707c703af81dde9a18bf8aa50dffa1aa5c0",
        9038,
    ),
    (
        BUILDER_OFF_26,
        "sha256:884c3f0dc80915f53403c5e11e809dbe2b0d1cc645fc1e194e94e0452f02cb1a",
        9008,
    ),
    (
        BUILDER_ON,
        "sha256:1a9384fa70c0aeb398435d53a11f4dc98a529fcaa43f0d60afb29ea568d4e1a3",
        9908,
    ),
    (
        LINTER_ON,
        "sha256:2bf9c449b443e82060ced051a57fa99ffff7e891f8e50624e442e9edf418e89e",
        1885,
    ),
];

const PINNED_CONTEXTS: [(&str, &str); 20] = [
    ("dev/1.19.2", "f3ff2f6425434f36c7c680fa909c977b158e1860"),
    ("dev/1.19.4", "2e3b561c15d663fb89fd353ccc2af67eeb0c2053"),
    ("dev/1.20", "6bf4845761d06560fc5e0e36018b5d583589004d"),
    ("dev/1.20.1", "faa040ce14dd825f2dd9716ea59508bf46278c06"),
    ("dev/1.20.2", "a829fb4db2ae06eddd2defb22e33f0b45a4b3ff9"),
    ("dev/1.20.3", "704aa69edad5376d8d6cfb0b0ef7845af077e647"),
    ("dev/1.20.4", "11d3ed07d654ff801329f17cf1eb81c2b347eecd"),
    ("dev/1.21.0", "43068d610b1c053c6569be486439769eec2ae9ef"),
    ("dev/1.21.1", "7524ab5512878b773e212600c9578b2bc4db4717"),
    ("dev/26.1.2", "6bd27f03863ddd041344cbc3da51b01a7b3c3e1f"),
    ("release/1.19.2", "31135b8e86801b862d5cb2283c7c5878b7cc5bb4"),
    ("release/1.19.4", "23785b63e3e1fe35e5be6a6d89b6638ee0ff0daa"),
    ("release/1.20", "3df18123a19535fd0e5d1dc81aa302105c3fd2f6"),
    ("release/1.20.1", "bb5babf12f467235b3a44ad5098666ee3ed171ec"),
    ("release/1.20.2", "cfbbafaeda4a006ae32743a92de330711983056b"),
    ("release/1.20.3", "1b7f9605da0ef13c7601daf3786545868dfc3c78"),
    ("release/1.20.4", "a637581b5e1078d7cc0ca68333add568e3e387ff"),
    ("release/1.21.0", "6bfab8a21e7a5bbeb0bb136bf4e5b26f6ce03e25"),
    ("release/1.21.1", "f5366c79c823ff52712130e69dd9c8166c70bd14"),
    ("release/26.1.2", "fe32b29453b13b4f3050ad441677c7eb79e80814"),
];

// No deny_unknown_fields: the test consumes only the typed golden subset.
// Evidence prose and other proof sections are deliberately not executable.
#[derive(Debug, Facet)]
struct SliceLedger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    witness_feature_contexts: BTreeMap<String, WitnessContext>,
    files: BTreeMap<String, AuthoredFile>,
}

#[derive(Debug, Facet)]
struct WitnessContext {
    minecraft_version: String,
    features: BTreeMap<String, bool>,
}

#[derive(Debug, Facet)]
struct AuthoredFile {
    authored_sha256: String,
    authored_bytes: u64,
    witness_groups: Vec<WitnessGroup>,
}

#[derive(Debug, Facet)]
struct WitnessGroup {
    git_blob: Option<String>,
    source_sha256: Option<String>,
    raw_bytes: u64,
    contexts: Vec<String>,
}

struct LanguageFixture {
    shared: CoreTestFixture,
    ledger: SliceLedger,
    sources: BTreeMap<String, Vec<u8>>,
    blobs: BTreeMap<String, Vec<u8>>,
}

impl LanguageFixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let ledger_bytes = read_bounded(&shared.repository.join(LEDGER_PATH), MAX_LEDGER_BYTES)?;
        let ledger: SliceLedger = facet_json::from_str(std::str::from_utf8(&ledger_bytes)?)
            .wrap_err("cannot parse the test-only first-slice golden subset")?;
        let registered = shared.features.registered_names();
        ensure!(
            registered.contains(EXECUTION_SIDE) && registered.contains(WORDED_INTERVALS),
            "the two language owners must be registered before this module runs"
        );
        let sources = SOURCE_PATHS
            .into_iter()
            .map(|path| {
                let bytes = shared.read_source(path)?;
                ensure!(
                    bytes.len() as u64 <= MAX_SOURCE_BYTES,
                    "language core source exceeds its slice bound"
                );
                Ok((path.to_owned(), bytes))
            })
            .collect::<Result<BTreeMap<_, _>>>()?;
        let golden_ids = PINNED_BLOBS
            .into_iter()
            .map(|(oid, _, _)| oid.to_owned())
            .collect();
        let blobs = read_git_blobs(&shared.repository, &golden_ids)?;
        ensure!(
            blobs.len() == PINNED_BLOBS.len(),
            "bounded language blob scope changed"
        );
        for (oid, digest, raw_bytes) in PINNED_BLOBS {
            let bytes = &blobs[oid];
            ensure!(
                sha256(bytes) == digest && bytes.len() as u64 == raw_bytes,
                "test-only pinned blob evidence changed at {oid}"
            );
            ensure!(
                bytes.ends_with(b"\n") && !bytes.contains(&b'\r'),
                "the first-slice raw witnesses must remain LF/final-LF without normalization"
            );
        }
        let fixture = Self {
            shared,
            ledger,
            sources,
            blobs,
        };
        fixture.validate_golden_scope()?;
        Ok(fixture)
    }

    fn validate_golden_scope(&self) -> Result<()> {
        ensure!(
            self.ledger.schema == "sfm:core-language-first-slice@1",
            "unsupported test-only first-slice evidence schema"
        );
        let expected_contexts = PINNED_CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == expected_contexts
                && self
                    .ledger
                    .witness_feature_contexts
                    .keys()
                    .eq(expected_contexts.keys()),
            "the first-slice twenty-context golden scope changed; review explicitly"
        );
        ensure!(
            self.ledger
                .files
                .keys()
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == SOURCE_PATHS.into_iter().collect(),
            "the first-slice four-file golden scope changed"
        );
        for (name, witness) in &self.ledger.witness_feature_contexts {
            let (_, target) = split_context(name)?;
            let version = minecraft_version(target)?;
            let witnessed_on = matches!(name.as_str(), "dev/1.19.2" | "dev/1.19.4");
            ensure!(
                witness.minecraft_version == version
                    && witness.features
                        == BTreeMap::from([
                            (EXECUTION_SIDE.to_owned(), witnessed_on),
                            (WORDED_INTERVALS.to_owned(), witnessed_on),
                        ]),
                "the bounded witness feature assignment changed at {name}"
            );
        }
        for (path, file) in &self.ledger.files {
            let bytes = &self.sources[path];
            ensure!(
                sha256(bytes) == file.authored_sha256 && bytes.len() as u64 == file.authored_bytes,
                "authored core golden changed at {path}; deliberately review updated evidence"
            );
            let mut covered = BTreeSet::new();
            for group in &file.witness_groups {
                validate_witness_group(group, &self.blobs)?;
                for name in &group.contexts {
                    ensure!(
                        self.ledger.context_commits.contains_key(name) && covered.insert(name),
                        "unknown or repeated witness context at {path}/{name}"
                    );
                    let (_, target) = split_context(name)?;
                    let witness = &self.ledger.witness_feature_contexts[name];
                    ensure!(
                        group.git_blob.as_deref()
                            == expected_blob(
                                path,
                                target,
                                witness.features[EXECUTION_SIDE],
                                witness.features[WORDED_INTERVALS],
                            )?,
                        "pinned raw witness ownership changed at {path}/{name}"
                    );
                }
            }
            ensure!(
                covered.into_iter().eq(self.ledger.context_commits.keys()),
                "incomplete witness coverage for {path}"
            );
        }
        Ok(())
    }

    fn context(
        &self,
        target: &str,
        execution_side: bool,
        worded_intervals: bool,
    ) -> Result<ProjectionContext> {
        let enabled = [
            (execution_side, EXECUTION_SIDE),
            (worded_intervals, WORDED_INTERVALS),
        ]
        .into_iter()
        .filter_map(|(enabled, name)| enabled.then_some(name))
        .collect::<Vec<_>>();
        self.shared.context(target, &enabled)
    }

    fn assert_case(
        &self,
        target: &str,
        execution_side: bool,
        worded_intervals: bool,
    ) -> Result<(usize, usize)> {
        let context = self.context(target, execution_side, worded_intervals)?;
        let inventory = SOURCE_PATHS.into_iter().map(str::to_owned).collect();
        let selected = select_core_inputs(&self.shared.metadata, &context, &inventory)?;
        let mut present = 0;
        let mut absent = 0;
        for path in SOURCE_PATHS {
            let expected = expected_blob(path, target, execution_side, worded_intervals)?;
            if let Some(oid) = expected {
                let input = selected
                    .inputs
                    .get(path)
                    .ok_or_else(|| eyre::eyre!("expected member omitted: {target}/{path}"))?;
                ensure!(
                    input.input == path,
                    "language source routed outside its common core path"
                );
                let source = std::str::from_utf8(&self.sources[path])?;
                let rendered = render_java_source(source, &context)?;
                let expected_bytes = &self.blobs[oid];
                ensure!(
                    rendered.as_bytes() == expected_bytes,
                    "raw rendered bytes differ for {target}/{path}: expected {}, got {}",
                    sha256(expected_bytes),
                    sha256(rendered.as_bytes())
                );
                present += 1;
            } else {
                ensure!(
                    !selected.inputs.contains_key(path) && selected.omitted_paths.contains(path),
                    "feature-off source was selected instead of absent: {target}/{path}"
                );
                absent += 1;
            }
        }
        Ok((present, absent))
    }
}

fn split_context(name: &str) -> Result<(&str, &str)> {
    name.split_once('/')
        .ok_or_else(|| eyre::eyre!("invalid bounded witness context {name}"))
}

fn minecraft_version(target: &str) -> Result<&'static str> {
    SUPPORTED_TARGETS
        .into_iter()
        .find(|(id, _)| *id == target)
        .map(|(_, version)| version)
        .ok_or_else(|| eyre::eyre!("unsupported bounded witness target {target}"))
}

fn expected_blob(
    path: &str,
    target: &str,
    execution_side: bool,
    worded_intervals: bool,
) -> Result<Option<&'static str>> {
    minecraft_version(target)?;
    let witnessed_target = matches!(target, "1.19.2" | "1.19.4");
    ensure!(
        witnessed_target || (!execution_side && !worded_intervals),
        "feature-on first-slice evidence is only supported on 1.19.2/1.19.4"
    );
    Ok(match path {
        INTERVAL_PATH => Some(if worded_intervals {
            INTERVAL_ON
        } else {
            INTERVAL_OFF
        }),
        EXECUTION_SIDE_PATH => execution_side.then_some(SIDE_ENUM_ON),
        PROGRAM_BUILDER_PATH => Some(if execution_side {
            BUILDER_ON
        } else if target == "26.1.2" {
            BUILDER_OFF_26
        } else {
            BUILDER_OFF
        }),
        LEGACY_LINTER_PATH => worded_intervals.then_some(LINTER_ON),
        _ => eyre::bail!("path outside the bounded first-slice golden"),
    })
}

fn validate_witness_group(group: &WitnessGroup, blobs: &BTreeMap<String, Vec<u8>>) -> Result<()> {
    if let Some(oid) = &group.git_blob {
        let bytes = blobs
            .get(oid)
            .ok_or_else(|| eyre::eyre!("unreviewed test-only golden blob {oid}"))?;
        ensure!(
            group.source_sha256.as_deref() == Some(sha256(bytes).as_str())
                && bytes.len() as u64 == group.raw_bytes,
            "raw witness evidence changed for {oid}"
        );
    } else {
        ensure!(
            group.source_sha256.is_none() && group.raw_bytes == 0,
            "absent witnesses cannot contain a raw source hash or bytes"
        );
    }
    Ok(())
}

#[test]
fn twenty_frozen_language_contexts_reconstruct_raw_bytes_and_source_membership() -> Result<()> {
    let fixture = LanguageFixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, witness) in &fixture.ledger.witness_feature_contexts {
        let (_, target) = split_context(name)?;
        let (case_present, case_absent) = fixture.assert_case(
            target,
            witness.features[EXECUTION_SIDE],
            witness.features[WORDED_INTERVALS],
        )?;
        present += case_present;
        absent += case_absent;
    }
    assert_eq!((present, absent), (44, 36));
    Ok(())
}

#[test]
fn independent_language_owners_reconstruct_thirty_two_supported_toggle_cells() -> Result<()> {
    let fixture = LanguageFixture::load()?;
    let mut cells = 0;
    for target in ["1.19.2", "1.19.4"] {
        for execution_side in [false, true] {
            for worded_intervals in [false, true] {
                let (present, absent) =
                    fixture.assert_case(target, execution_side, worded_intervals)?;
                cells += present + absent;
            }
        }
    }
    assert_eq!(cells, 32);
    Ok(())
}

#[test]
fn isolated_common_body_edit_reaches_three_targets_without_changing_owned_guards() -> Result<()> {
    let fixture = LanguageFixture::load()?;
    let temporary = tempfile::tempdir()?;
    let temporary_core = temporary.path().join(CORE_ROOT);
    for (path, bytes) in &fixture.sources {
        let output = temporary_core.join(path);
        fs::create_dir_all(
            output
                .parent()
                .ok_or_else(|| eyre::eyre!("fixture source has no parent"))?,
        )?;
        fs::write(output, bytes)?;
    }
    let original = std::str::from_utf8(&fixture.sources[INTERVAL_PATH])?;
    const BEFORE: &str = "import java.util.Objects;\n";
    const AFTER: &str = "import java.util.Objects;\n// shared authoring probe\n";
    ensure!(
        original.matches(BEFORE).count() == 1,
        "common edit anchor changed"
    );
    let edited = original.replacen(BEFORE, AFTER, 1);
    assert_eq!(directive_lines(original), directive_lines(&edited));
    fs::write(temporary_core.join(INTERVAL_PATH), edited.as_bytes())?;
    let inventory = discover_core_source_files(&temporary_core)?;
    assert_eq!(
        inventory,
        SOURCE_PATHS.into_iter().map(str::to_owned).collect()
    );

    let mut visited_targets = BTreeSet::new();
    for (target, worded_intervals) in [
        ("1.19.2", false),
        ("1.19.2", true),
        ("1.19.4", true),
        ("26.1.2", false),
    ] {
        let context = fixture.context(target, false, worded_intervals)?;
        let selected = select_core_inputs(&fixture.shared.metadata, &context, &inventory)?;
        assert_eq!(selected.inputs[INTERVAL_PATH].input, INTERVAL_PATH);
        let actual_source = read_bounded(&temporary_core.join(INTERVAL_PATH), MAX_SOURCE_BYTES)?;
        let rendered = render_java_source(std::str::from_utf8(&actual_source)?, &context)?;
        let oid = expected_blob(INTERVAL_PATH, target, false, worded_intervals)?
            .ok_or_else(|| eyre::eyre!("Interval must be present"))?;
        let expected = std::str::from_utf8(&fixture.blobs[oid])?.replacen(BEFORE, AFTER, 1);
        assert_eq!(rendered.as_bytes(), expected.as_bytes());
        visited_targets.insert(target);
    }
    assert_eq!(visited_targets.len(), 3);
    for (path, bytes) in &fixture.sources {
        assert_eq!(
            read_bounded(&fixture.shared.core.join(path), MAX_SOURCE_BYTES)?,
            *bytes
        );
    }
    assert_eq!(
        fixture.shared.repository.join(CORE_ROOT),
        fixture.shared.core
    );
    Ok(())
}

fn directive_lines(source: &str) -> Vec<&str> {
    source
        .lines()
        .filter(|line| line.trim_start().starts_with("{%"))
        .collect()
}

#[test]
fn bounded_test_file_reader_refuses_oversized_evidence() -> Result<()> {
    let temporary = tempfile::NamedTempFile::new()?;
    temporary.as_file().set_len(MAX_SOURCE_BYTES + 1)?;
    assert!(read_bounded(temporary.path(), MAX_SOURCE_BYTES).is_err());
    Ok(())
}
