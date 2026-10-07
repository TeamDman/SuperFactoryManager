//! Exact promoted relation/observation sources and sparse ownership evidence.
//!
//! This module is staged and unregistered until root promotion. It then reads
//! only the actual core and registered metadata, never the ignored source stage.
//! Git objects are offline test witnesses, not production source fallbacks.
//! Passing proves source selection/rendering, not packet Java or gameplay closure.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::render_java_source;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;

const LEDGER: &str = "docs/tasks/sfm-core-program-relations-slice.json";
const STAGE: &str = "platform/cli/sfm-propagate-changes/target/core-program-relations-stage-v1";
const PREFIX: &str = "src/main/java/ca/teamdman/sfm/common/program/";
const OWNER: &str = "packet_computation";
const VALUES: &str = "packet_values";
const CLEANUP: &str = "runtime_resource_cleanup";
const OWNER_FLAGS: [&str; 3] = [OWNER, VALUES, CLEANUP];
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const MAX_SOURCE_BYTES: u64 = 1024 * 1024;
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

struct RawGolden {
    basename: &'static str,
    oid: &'static str,
    sha256: &'static str,
    bytes: usize,
    line_feeds: usize,
}
const RAW_GOLDENS: [RawGolden; 7] = [
    RawGolden {
        basename: "ProgramOccurrenceId.java",
        oid: "af268df0ab76b59a9e79c4e6f4d49652bfa62190",
        sha256: "sha256:858e32e9b0466db092f3592b3f80533024079555e74833c7aa73d6cba379363f",
        bytes: 461,
        line_feeds: 17,
    },
    RawGolden {
        basename: "ProgramRelation.java",
        oid: "c2995835a0957047865631ce873847beec2eeccb",
        sha256: "sha256:64f4b3414dc8ddb205d4ccdfdcb0f27dccc8dbaca3c39596a66c19acae978a8a",
        bytes: 899,
        line_feeds: 27,
    },
    RawGolden {
        basename: "ProgramRelationRow.java",
        oid: "c479e592fb9f2fb21f9ef120eb13c744ad8b4e0b",
        sha256: "sha256:1f57487ac6ea6454ab7dee5d74c4903fb804ce31ed01c0bbbb26985df614572f",
        bytes: 475,
        line_feeds: 18,
    },
    RawGolden {
        basename: "ProgramResourceObservation.java",
        oid: "b94b3ec20cdd5a42850f2fc36bf9fcb7cceccde8",
        sha256: "sha256:02c68f935f172de410a1cebf4a974ff6c61b2583d9c22acb19846699b5f2413e",
        bytes: 478,
        line_feeds: 16,
    },
    RawGolden {
        basename: "ProgramResourceObserver.java",
        oid: "c285961dc46dca97c7cfbab8d6e46e6f94c086f6",
        sha256: "sha256:7d8cee665b8ce3ec665e5fd1f9ec4270c7af773e026f7dc942f148fa25451b51",
        bytes: 5228,
        line_feeds: 139,
    },
    RawGolden {
        basename: "ProgramResourceValue.java",
        oid: "d70cc8659c387f79b27a5a15f81bf6196832a7e1",
        sha256: "sha256:e988a8f9ece8058be095fc70311031534a5afc482e896646e93da88c15eab33e",
        bytes: 728,
        line_feeds: 22,
    },
    RawGolden {
        basename: "ProgramValueReference.java",
        oid: "cee01176fede2246c98f509a43a21cf34873573a",
        sha256: "sha256:8fbabaa05a9a83688f9c8f27f0d14dbd06d43f48e6d2326d8c5f8101d9b522e8",
        bytes: 1649,
        line_feeds: 51,
    },
];

#[derive(Facet)]
struct SliceLedger {
    schema: String,
    status: String,
    scope: String,
    context_commits: BTreeMap<String, String>,
    files: BTreeMap<String, SliceFile>,
}
#[derive(Facet)]
struct SliceFile {
    original_path: String,
    core_input: String,
    staged_path: String,
    feature_owner: String,
    supported_targets: Vec<String>,
    required_features: Vec<String>,
    feature_prerequisites: Vec<String>,
    source_blob: String,
    source_sha256: String,
    source_bytes: usize,
    line_endings: String,
    carriage_return_count: usize,
    line_feed_count: usize,
    final_newline: String,
    normalized: bool,
    token_or_whitespace_edits: bool,
    current_canonical_sha256: String,
    staged_sha256: String,
    witnesses: BTreeMap<String, Option<String>>,
}
struct Fixture {
    core: CoreTestFixture,
    sources: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: SliceLedger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            MAX_SOURCE_BYTES,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-program-relations-slice@1"
                && ledger.status
                    == "exact_source_bodies_staged_pending_root_promotion_and_sparse_membership_registration"
                && ledger.scope == "seven_common_program_relations_and_resource_observation_inputs",
            "program relation ledger scope changed"
        );
        let pinned = PINNED_CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            ledger.context_commits == pinned && ledger.files.len() == 7,
            "program relation frozen source set changed"
        );
        let owner = core
            .features
            .0
            .get(OWNER)
            .ok_or_else(|| eyre::eyre!("packet computation owner is missing"))?;
        ensure!(
            same_names(&owner.supported_targets, &D2)
                && same_names(&owner.requires, &[VALUES, CLEANUP]),
            "packet computation support or prerequisites changed"
        );
        for prerequisite in [VALUES, CLEANUP] {
            let definition = core
                .features
                .0
                .get(prerequisite)
                .ok_or_else(|| eyre::eyre!("missing prerequisite {prerequisite}"))?;
            ensure!(
                same_names(&definition.supported_targets, &D2) && definition.requires.is_empty(),
                "relation prerequisite support changed"
            );
        }
        let oids = RAW_GOLDENS.iter().map(|g| g.oid.to_owned()).collect();
        let raw = read_git_blobs(&core.repository, &oids)?;
        let mut sources = BTreeMap::new();
        for golden in &RAW_GOLDENS {
            let path = format!("{PREFIX}{}", golden.basename);
            let evidence = ledger
                .files
                .get(&path)
                .ok_or_else(|| eyre::eyre!("missing exact relation ledger path {path}"))?;
            ensure!(
                evidence.original_path == format!("platform/minecraft/{path}")
                    && evidence.core_input == format!("{CORE_ROOT}/{path}")
                    && evidence.staged_path == format!("{STAGE}/{}", golden.basename)
                    && evidence.feature_owner == OWNER
                    && same_names(&evidence.supported_targets, &D2)
                    && evidence.required_features == [OWNER]
                    && same_names(&evidence.feature_prerequisites, &[VALUES, CLEANUP])
                    && evidence.source_blob == golden.oid
                    && evidence.source_sha256 == golden.sha256
                    && evidence.current_canonical_sha256 == golden.sha256
                    && evidence.staged_sha256 == golden.sha256
                    && evidence.source_bytes == golden.bytes
                    && evidence.line_endings == "lf"
                    && evidence.carriage_return_count == 0
                    && evidence.line_feed_count == golden.line_feeds
                    && evidence.final_newline == "exactly_one_lf"
                    && !evidence.normalized
                    && !evidence.token_or_whitespace_edits,
                "relation source identity/ownership changed for {path}"
            );
            let witnesses = PINNED_CONTEXTS
                .into_iter()
                .map(|(name, _)| {
                    (
                        name.to_owned(),
                        present_context(name).then(|| golden.oid.to_owned()),
                    )
                })
                .collect::<BTreeMap<_, _>>();
            ensure!(
                evidence.witnesses == witnesses,
                "relation witness membership changed for {path}"
            );
            let source = core.read_source(&path)?;
            validate_raw(golden, &source)?;
            ensure!(source == raw[golden.oid], "relation raw Git body mismatch");
            let rules =
                core.metadata.source_rules.get(&path).ok_or_else(|| {
                    eyre::eyre!("relation source needs registered sparse membership")
                })?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == path
                    && same_names(&rules[0].when.targets, &D2)
                    && rules[0].when.all_features == [OWNER]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "actual relation sparse source rule changed for {path}"
            );
            // Java is always processed as a core template by the production
            // collector, independent of the optional non-Java template bit.
            sources.insert(path, source);
        }
        Ok(Self { core, sources })
    }
    fn inventory(&self) -> BTreeSet<String> {
        self.sources.keys().cloned().collect()
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if let Some(input) = selected.inputs.get(path) {
            ensure!(
                input.input == path && !selected.omitted_paths.contains(path),
                "relation source alias or conflicting omission"
            );
            Ok(Some(
                render_java_source(std::str::from_utf8(&self.sources[path])?, context)?
                    .into_bytes(),
            ))
        } else {
            ensure!(
                selected.omitted_paths.contains(path),
                "missing explicit relation omission"
            );
            Ok(None)
        }
    }
    fn body(&self, basename: &str, context: &ProjectionContext) -> Result<String> {
        String::from_utf8(
            self.render(&format!("{PREFIX}{basename}"), context)?
                .ok_or_else(|| eyre::eyre!("expected selected relation source"))?,
        )
        .map_err(Into::into)
    }
}
fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn present_context(name: &str) -> bool {
    matches!(name, "dev/1.19.2" | "dev/1.19.4")
}
fn validate_raw(golden: &RawGolden, source: &[u8]) -> Result<()> {
    ensure!(
        source.len() == golden.bytes
            && sha256(source) == golden.sha256
            && !source.contains(&b'\r')
            && source.iter().filter(|byte| **byte == b'\n').count() == golden.line_feeds
            && source.ends_with(b"\n")
            && !source.ends_with(b"\n\n"),
        "exact raw relation body changed"
    );
    let text = std::str::from_utf8(source)?;
    ensure!(
        !text.contains("{%") && !text.contains("{{"),
        "unreviewed relation directive"
    );
    Ok(())
}

#[test]
fn seven_program_relation_witnesses_cover_all_one_hundred_forty_tree_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let paths = fixture
        .sources
        .keys()
        .map(|path| format!("platform/minecraft/{path}"))
        .collect::<Vec<_>>();
    let mut cells = 0;
    let mut present = 0;
    for (name, commit) in PINNED_CONTEXTS {
        let output = frozen_git_command(&fixture.core.repository)
            .env("GIT_ALLOW_PROTOCOL", "")
            .env("GIT_TERMINAL_PROMPT", "0")
            .args([
                "-c",
                "protocol.allow=never",
                "-c",
                "core.fsmonitor=false",
                "ls-tree",
                commit,
                "--",
            ])
            .args(&paths)
            .output()
            .wrap_err("cannot read fixed offline relation tree")?;
        ensure!(
            output.status.success(),
            "offline relation tree query failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed relation witness row"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|expected| expected == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected relation tree member"
            );
        }
        let expected = if present_context(name) {
            RAW_GOLDENS
                .iter()
                .map(|g| {
                    (
                        format!("platform/minecraft/{PREFIX}{}", g.basename),
                        g.oid.to_owned(),
                    )
                })
                .collect()
        } else {
            BTreeMap::new()
        };
        assert_eq!(
            actual, expected,
            "relation tree membership differs for {name}"
        );
        present += actual.len();
        cells += RAW_GOLDENS.len();
    }
    assert_eq!((cells, present, cells - present), (140, 14, 126));
    Ok(())
}

#[test]
fn program_relation_real_selector_renderer_reconstructs_all_twenty_source_contexts() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut omitted = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (environment, target) = name.split_once('/').expect("fixed context name");
        let flags = if present_context(name) {
            &OWNER_FLAGS[..]
        } else {
            &[]
        };
        let mut context = fixture.core.context(target, flags)?;
        context.environment = environment.to_owned();
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
        for (path, raw) in &fixture.sources {
            if present_context(name) {
                assert_eq!(
                    fixture.render(path, &context)?.as_deref(),
                    Some(raw.as_slice())
                );
                present += 1;
            } else {
                assert!(fixture.render(path, &context)?.is_none());
                omitted += 1;
            }
        }
    }
    assert_eq!((present, omitted), (14, 126));
    Ok(())
}

#[test]
fn program_relation_owner_is_independent_of_prerequisites_key_and_environment() -> Result<()> {
    let fixture = Fixture::load()?;
    let masks: [&[&str]; 5] = [&[], &[VALUES], &[CLEANUP], &[VALUES, CLEANUP], &OWNER_FLAGS];
    let mut cells = 0;
    let mut present = 0;
    for target in D2 {
        for flags in masks {
            let context = fixture.core.context(target, flags)?;
            let mut renamed = context.clone();
            renamed.environment = "release".to_owned();
            renamed.projection_key = "synthetic/nested/relation-proof".to_owned();
            renamed.preset = "unrelated-visible-label".to_owned();
            for (path, raw) in &fixture.sources {
                let actual = fixture.render(path, &context)?;
                assert_eq!(actual, fixture.render(path, &renamed)?);
                if flags.contains(&OWNER) {
                    assert_eq!(actual.as_deref(), Some(raw.as_slice()));
                    present += 1;
                } else {
                    assert!(actual.is_none());
                }
                cells += 1;
            }
        }
    }
    assert_eq!((cells, present, cells - present), (70, 14, 56));
    let mut off = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (_, target) = name.split_once('/').expect("fixed context");
        let context = fixture.core.context(target, &[])?;
        for path in fixture.sources.keys() {
            assert!(fixture.render(path, &context)?.is_none());
            off += 1;
        }
    }
    assert_eq!(off, 140);
    Ok(())
}

#[test]
fn program_relation_refuses_missing_prerequisites_unsupported_targets_and_unknown_owner_flag()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in D2 {
        for (flags, missing) in [
            (&[OWNER][..], VALUES),
            (&[OWNER, VALUES][..], CLEANUP),
            (&[OWNER, CLEANUP][..], VALUES),
        ] {
            let error = fixture
                .core
                .context(target, flags)
                .expect_err("packet computation cannot silently enable a prerequisite");
            assert!(error.to_string().contains(missing));
        }
    }
    let mut refused = 0;
    for (target, _) in SUPPORTED_TARGETS {
        if !D2.contains(&target) {
            assert!(fixture.core.context(target, &OWNER_FLAGS).is_err());
            refused += 1;
        }
    }
    assert_eq!(refused, 8);
    let mut context = fixture.core.context(D2[0], &OWNER_FLAGS)?;
    assert_eq!(context.features.remove(OWNER), Some(true));
    assert!(select_core_inputs(&fixture.core.metadata, &context, &fixture.inventory()).is_err());
    Ok(())
}

#[test]
fn program_relation_source_contracts_preserve_identity_memoization_and_readonly_observation()
-> Result<()> {
    let fixture = Fixture::load()?;
    for raw in fixture.sources.values() {
        for line in std::str::from_utf8(raw)?.lines() {
            if let Some(import) = line.strip_prefix("import ") {
                assert!(
                    import.starts_with("java.")
                        || import == "ca.teamdman.sfm.common.resourcetype.ResourceType;"
                        || import == "ca.teamdman.sfm.common.value.SFMValue;"
                        || import == "org.jetbrains.annotations.Nullable;",
                    "relation foundation imported a foreign effect service: {import}"
                );
            }
        }
    }
    for target in D2 {
        let context = fixture.core.context(target, &OWNER_FLAGS)?;
        let relation = fixture.body("ProgramRelation.java", &context)?;
        assert!(relation.contains("rows = List.copyOf(Objects.requireNonNull(rows));"));
        assert!(relation.contains("row.mapValue(mapper.apply(row))"));
        let row = fixture.body("ProgramRelationRow.java", &context)?;
        assert!(row.contains("return new ProgramRelationRow(occurrenceId, replacement);"));
        let id = fixture.body("ProgramOccurrenceId.java", &context)?;
        assert!(!id.contains("boolean equals("));
        assert!(id.contains("It is not serialized"));
        let value = fixture.body("ProgramValueReference.java", &context)?;
        assert!(value.contains("resolved = true;"));
        assert!(value.contains("evaluator = null;"));
        assert!(value.contains("catch (RuntimeException | Error evaluationFailure)"));
        assert!(
            value
                .contains("return new ProgramValueReference(() -> Objects.requireNonNull(value));")
        );
        let observer = fixture.body("ProgramResourceObserver.java", &context)?;
        assert!(observer.contains("IInputResourceTracker::forkForObservation"));
        assert!(observer.contains("selector.test(type, type.copy(stack))"));
        assert!(observer.contains("handler == that.handler"));
        assert!(observer.contains("System.identityHashCode(handler)"));
        assert!(observer.contains("previous == null || amount > previous.amount()"));
        assert!(observer.contains("return List.copyOf(observations.values());"));
        assert!(observer.contains("tracker.trackRetentionObligation("));
        assert!(observer.contains("tracker.trackTransfer(type, stack, amount);"));
        let resource = fixture.body("ProgramResourceValue.java", &context)?;
        assert!(resource.contains("type.withCount(observation.stack(), 1)"));
        for (path, anchor) in [
            (
                format!("{PREFIX}IInputResourceTracker.java"),
                "forkForObservation",
            ),
            (format!("{PREFIX}LimitedInputSlot.java"), "peekStackInSlot"),
            (format!("{PREFIX}ProgramContext.java"), "getInputs()"),
            (format!("{PREFIX}ProgramInputSource.java"), "gatherSlots("),
            (
                format!("{PREFIX}ObservationInputResourceTracker.java"),
                "implements IInputResourceTracker",
            ),
            (
                format!("{PREFIX}ProgramVariableEnvironment.java"),
                "ProgramRelation.singleton",
            ),
            (
                "src/main/java/ca/teamdman/sfml/ast/InputStatement.java".to_owned(),
                "ProgramResourceObserver.observe(",
            ),
            (
                "src/main/java/ca/teamdman/sfml/ast/ProgramInputSelection.java".to_owned(),
                "ProgramValueReference::resolved",
            ),
        ] {
            let selected = select_core_inputs(
                &fixture.core.metadata,
                &context,
                &BTreeSet::from([path.clone()]),
            )?;
            let input = selected
                .inputs
                .get(&path)
                .ok_or_else(|| eyre::eyre!("required owned observer consumer omitted: {path}"))?;
            let body = fixture.core.read_source(&input.input)?;
            assert!(
                render_java_source(std::str::from_utf8(&body)?, &context)?.contains(anchor),
                "owned observer API/consumer anchor changed: {path}"
            );
        }
    }
    Ok(())
}

#[test]
fn one_isolated_relation_edit_reaches_both_supported_targets_without_live_writes() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let core = temp.path().join(CORE_ROOT);
    for (path, raw) in &fixture.sources {
        let output = core.join(path);
        fs::create_dir_all(
            output
                .parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(output, raw)?;
    }
    let path = format!("{PREFIX}ProgramRelation.java");
    let raw = std::str::from_utf8(&fixture.sources[&path])?;
    const ANCHOR: &str = "/** An immutable, ordered, occurrence-preserving relation. */";
    const EDIT: &str =
        "/** Shared relation authoring proof: immutable and occurrence-preserving. */";
    ensure!(
        raw.matches(ANCHOR).count() == 1,
        "isolated relation edit anchor changed"
    );
    let edited = raw.replacen(ANCHOR, EDIT, 1);
    fs::write(core.join(&path), edited.as_bytes())?;
    let inventory = discover_core_source_files(&core)?;
    assert_eq!(inventory, fixture.inventory());
    for target in D2 {
        let context = fixture.core.context(target, &OWNER_FLAGS)?;
        let selected = select_core_inputs(&fixture.core.metadata, &context, &inventory)?;
        assert_eq!(selected.inputs[&path].input, path);
        let input = read_bounded(&checked_file(&core, &path)?, MAX_SOURCE_BYTES)?;
        assert_eq!(
            render_java_source(std::str::from_utf8(&input)?, &context)?,
            edited
        );
        let off = fixture.core.context(target, &[])?;
        assert!(
            select_core_inputs(&fixture.core.metadata, &off, &inventory)?
                .omitted_paths
                .contains(&path)
        );
    }
    for (path, raw) in &fixture.sources {
        assert_eq!(fixture.core.read_source(path)?, *raw);
    }
    Ok(())
}

#[test]
fn program_relation_raw_validation_refuses_any_eol_eof_or_token_rewrite() -> Result<()> {
    let fixture = Fixture::load()?;
    for golden in &RAW_GOLDENS {
        let raw = &fixture.sources[&format!("{PREFIX}{}", golden.basename)];
        validate_raw(golden, raw)?;
        let crlf = std::str::from_utf8(raw)?.replace('\n', "\r\n");
        assert!(validate_raw(golden, crlf.as_bytes()).is_err());
        assert!(validate_raw(golden, &raw[..raw.len() - 1]).is_err());
        let mut extra = raw.clone();
        extra.push(b'\n');
        assert!(validate_raw(golden, &extra).is_err());
        let mut token = raw.clone();
        token[0] = b'P';
        assert!(validate_raw(golden, &token).is_err());
    }
    Ok(())
}
