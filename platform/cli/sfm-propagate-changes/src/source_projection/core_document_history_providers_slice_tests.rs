//! Prepared preservation regressions for four genuine shared history providers.
//! Register only after exact source/rule/ledger promotion. Local pinned Git objects
//! are test evidence, not production source lookup. Neither source selection nor
//! text rendering executes a widget, history engine, clipboard or signing ceremony.
#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputVariant;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;

const LEDGER: &str = "docs/tasks/sfm-core-document-history-providers-slice.json";
const PREFIX: &str = "src/main/java/ca/teamdman/sfm/client/history/";
const NAMES: [&str; 4] = [
    "SFMHistoryGraphContract",
    "SFMDocumentHistoryContract",
    "SFMDocumentHistorySession",
    "SFMDocumentSemanticTransactionProjection",
];
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const GRAPH_CONSUMERS: [&str; 4] = [
    "trajectory_panels",
    "workspace_counterfactuals",
    "review_sessions",
    "route_comparison",
];
const OLDER: [&str; 8] = [
    "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2",
];
const CONTEXTS: [(&str, &str); 20] = [
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
const RAW: [(&str, usize, &str); 4] = [
    (
        "0cb0f73193ee70e56e12781be7113a65d4a5c840",
        20549,
        "sha256:3cdc09ef6b6cce2606b66d09fba3d74662b36b3baf6bcb07744e6f701af2a2d4",
    ),
    (
        "e4f2e8a9b07b06cdf790762e5baaabacf98d8610",
        30228,
        "sha256:616753ef3347eadad334a63cebb59ec2f0731b17c322d4f54fc7289b0ec216bf",
    ),
    (
        "ab04b642af2c15fbce92cba6bd623163048417a6",
        43523,
        "sha256:a8cc520ef680bf4368b57cffa878b654882fd6f25fcd145e213fed45921e7355",
    ),
    (
        "0e6ac13a0f6f9a49f074fb7c2c23c705375a9fd9",
        12325,
        "sha256:cc913fb1cfff89e1f5f1996b5174687751ac8767a26f93723798279a017bfa43",
    ),
];
#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    contract_changes: bool,
    feature_definition_changes: bool,
    prerequisite_changes: bool,
    context_commits: BTreeMap<String, String>,
    definitions: BTreeMap<String, Definition>,
    files: Vec<SourceEvidence>,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct RawEvidence {
    oid: String,
    bytes: usize,
    sha256: String,
    cr_count: usize,
    lf_count: usize,
    final_lf: bool,
    bom: bool,
}
#[derive(Facet)]
struct SourceEvidence {
    path: String,
    owners: Vec<String>,
    source_rule: InputVariant,
    raw: RawEvidence,
    witnesses: BTreeMap<String, Option<String>>,
    authored_bytes: usize,
    authored_sha256: String,
}
struct Fixture {
    core: CoreTestFixture,
    ledger: Ledger,
    sources: BTreeMap<String, Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
fn path(name: &str) -> String {
    if name == "SFMHistoryGraphContract" {
        format!("{PREFIX}{name}.java")
    } else {
        format!("{PREFIX}document/{name}.java")
    }
}
fn verify_raw(bytes: &[u8], oid: &str) -> Result<()> {
    let (_, length, digest) = RAW
        .iter()
        .find(|(id, _, _)| *id == oid)
        .ok_or_else(|| eyre::eyre!("unreviewed shared history raw identity"))?;
    ensure!(
        bytes.len() == *length
            && sha256(bytes) == *digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "shared history raw bytes changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            96 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-document-history-providers-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && !ledger.feature_definition_changes
                && !ledger.prerequisite_changes
                && ledger.files.len() == 4
                && ledger.definitions.len() == 2
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(key, value)| (key.to_owned(), value.to_owned()))
                        .collect(),
            "history reviewed scope changed"
        );
        for owner in ["single_line_input", "document_history"] {
            let original = &ledger.definitions[owner];
            let current = &core.features.0[owner];
            ensure!(
                original.supported_targets == D2
                    && original.requires.is_empty()
                    && current.supported_targets == D2
                    && current.requires.is_empty(),
                "single-line/history original independent contract changed"
            );
        }
        for owner in GRAPH_CONSUMERS {
            let current = &core.features.0[owner];
            ensure!(
                current.supported_targets == D2 && current.requires.is_empty(),
                "shared graph consumer support or prerequisites changed"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &RAW.iter().map(|(oid, _, _)| (*oid).to_owned()).collect(),
        )?;
        let mut sources = BTreeMap::new();
        for ((name, e), (oid, bytes, digest)) in NAMES.iter().zip(&ledger.files).zip(RAW) {
            verify_raw(&raw[oid], oid)?;
            ensure!(
                e.path == path(name)
                    && e.owners == ["single_line_input", "document_history"]
                    && e.raw.oid == oid
                    && e.raw.bytes == bytes
                    && e.raw.sha256 == digest
                    && e.raw.cr_count == 0
                    && e.raw.final_lf
                    && !e.raw.bom
                    && e.raw.lf_count == raw[oid].iter().filter(|byte| **byte == b'\n').count(),
                "history frozen identity changed"
            );
            for (key, _) in CONTEXTS {
                let expected = matches!(key, "dev/1.19.2" | "dev/1.19.4").then(|| oid.to_owned());
                ensure!(
                    e.witnesses.get(key) == Some(&expected),
                    "history twenty-cell historical ledger changed"
                );
            }
            let source = core.read_source(&e.path)?;
            ensure!(
                source == raw[oid]
                    && source.len() == e.authored_bytes
                    && sha256(&source) == e.authored_sha256,
                "root must promote the exact raw history source"
            );
            let rule = &e.source_rule;
            let mut current_rule = rule.clone();
            if *name == "SFMHistoryGraphContract" {
                current_rule
                    .when
                    .any_features
                    .extend(GRAPH_CONSUMERS.map(str::to_owned));
                current_rule
                    .when
                    .any_features
                    .push("selection_history".to_owned());
            } else {
                current_rule
                    .when
                    .any_features
                    .push("trajectory_panels".to_owned());
            }
            let mut current_rules = vec![current_rule.clone()];
            if *name == "SFMHistoryGraphContract" {
                let mut review_rule = current_rule;
                review_rule.when.all_features = vec!["release_review".to_owned()];
                review_rule.when.none_features = review_rule.when.any_features.clone();
                review_rule.when.any_features.clear();
                current_rules.push(review_rule);
            }
            ensure!(
                rule.input == e.path
                    && rule.template
                    && rule.when.targets == D2
                    && rule.when.all_features.is_empty()
                    && rule.when.any_features == ["single_line_input", "document_history"]
                    && rule.when.none_features.is_empty()
                    && core.metadata.source_rules.get(&e.path) == Some(&current_rules),
                "root must promote the exact shared history union rule"
            );
            sources.insert(e.path.clone(), source);
        }
        Ok(Self {
            core,
            ledger,
            sources,
            raw,
        })
    }
    fn inventory(&self) -> BTreeSet<String> {
        self.sources.keys().cloned().collect()
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if !selected.inputs.contains_key(path) {
            ensure!(
                selected.omitted_paths.contains(path),
                "history omitted path unaccounted"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.sources[path])?,
            context,
        )?))
    }
    fn bounded_metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|path, _| self.sources.contains_key(path));
        metadata
    }
    fn write_sources(&self, root: &Path) -> Result<()> {
        for (path, bytes) in &self.sources {
            let destination = root.join(path);
            fs::create_dir_all(destination.parent().expect("fixed source parent"))?;
            fs::write(destination, bytes)?;
        }
        Ok(())
    }
    fn copy_project_inputs(
        &self,
        root: &Path,
        metadata: &CoreProjectInputs,
        context: &ProjectionContext,
    ) -> Result<()> {
        let selected = select_core_inputs(metadata, context, &self.inventory())?;
        for (output, input) in &selected.inputs {
            if self.sources.contains_key(output) {
                continue;
            }
            let bytes = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let destination = root.join(&input.input);
            if destination.exists() {
                ensure!(
                    read_bounded(&destination, 16 * 1024 * 1024)? == bytes,
                    "actual isolated project input changed across owner masks"
                );
            } else {
                fs::create_dir_all(destination.parent().expect("fixed project input parent"))?;
                fs::write(destination, bytes)?;
            }
        }
        Ok(())
    }
}

#[test]
fn shared_history_providers_match_all80_frozen_tree_and_body_cells() -> Result<()> {
    let f = Fixture::load()?;
    let paths = NAMES.map(|name| format!("platform/minecraft/{}", path(name)));
    let mut counts = (0, 0);
    for (key, commit) in CONTEXTS {
        let output = frozen_git_command(&f.core.repository)
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
            .output()?;
        ensure!(
            output.status.success() && output.stdout.len() <= 4096 && output.stderr.len() <= 4096,
            "bounded offline history tree failure"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, entry) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed history tree entry"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|path| path == entry)
                    && actual
                        .insert(entry.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected history tree entry"
            );
        }
        let expected = f
            .ledger
            .files
            .iter()
            .filter_map(|e| {
                e.witnesses[key]
                    .as_ref()
                    .map(|oid| (format!("platform/minecraft/{}", e.path), oid.clone()))
            })
            .collect::<BTreeMap<_, _>>();
        assert_eq!(actual, expected, "frozen single-line cells: {key}");
        let (_, target) = key.split_once('/').expect("fixed context");
        let flags = if matches!(key, "dev/1.19.2" | "dev/1.19.4") {
            &["single_line_input"][..]
        } else {
            &[][..]
        };
        let context = f.core.context(target, flags)?;
        for e in &f.ledger.files {
            let body = f.render(&e.path, &context)?;
            if let Some(oid) = &e.witnesses[key] {
                assert_eq!(
                    body.expect("historical selected field").as_bytes(),
                    f.raw[oid]
                );
                counts.0 += 1;
            } else {
                assert!(body.is_none());
                counts.1 += 1;
            }
        }
    }
    assert_eq!(counts, (8, 72));
    Ok(())
}

#[test]
fn both_real_consumers_independently_select_exact_history_without_effect_owners() -> Result<()> {
    let f = Fixture::load()?;
    let mut counts = (0, 0);
    for target in D2 {
        for flags in [
            &[][..],
            &["single_line_input"][..],
            &["document_history"][..],
            &["single_line_input", "document_history"][..],
        ] {
            let context = f.core.context(target, flags)?;
            let enabled = !flags.is_empty();
            let mut description = context.clone();
            description.environment = "release".to_owned();
            description.preset = "description-is-not-history-authority".to_owned();
            description.projection_key = "source-proof/single-line".to_owned();
            for e in &f.ledger.files {
                let body = f.render(&e.path, &context)?;
                assert_eq!(body.is_some(), enabled);
                assert_eq!(body, f.render(&e.path, &description)?);
                if let Some(body) = body {
                    assert_eq!(body.as_bytes(), f.raw[&e.raw.oid]);
                    counts.0 += 1;
                } else {
                    counts.1 += 1;
                }
            }
            for effect in [
                "client_program_signing",
                "command_palette",
                "typed_command_palette",
                "client_program_actions",
                "packet_transport_private",
                "multiplayer_packets",
            ] {
                assert!(
                    !context.features[effect],
                    "pure field context enabled {effect}"
                );
            }
        }
    }
    assert_eq!(counts, (24, 8));
    Ok(())
}

#[test]
fn witnessed_consumers_select_only_their_exact_neutral_history_providers() -> Result<()> {
    let f = Fixture::load()?;
    let graph = path("SFMHistoryGraphContract");
    for target in D2 {
        for owner in GRAPH_CONSUMERS {
            let context = f.core.context(target, &[owner])?;
            assert!(!context.features["single_line_input"]);
            assert!(!context.features["document_history"]);
            let body = f.render(&graph, &context)?.expect("shared graph selected");
            assert_eq!(body.as_bytes(), f.raw[RAW[0].0]);
            for (name, (oid, _, _)) in NAMES[1..].iter().zip(&RAW[1..]) {
                let body = f.render(&path(name), &context)?;
                if owner == "trajectory_panels" {
                    assert_eq!(
                        body.expect("neutral chamber document helper").as_bytes(),
                        f.raw[*oid]
                    );
                } else {
                    assert!(body.is_none());
                }
            }
        }
    }
    Ok(())
}

#[test]
fn original_history_support_unknown_consumers_and_mutated_raw_bodies_are_refused() -> Result<()> {
    verify_release_review_history_selection()?;
    let f = Fixture::load()?;
    for target in OLDER {
        assert!(f.core.context(target, &["single_line_input"]).is_err());
        assert!(f.core.context(target, &["document_history"]).is_err());
    }
    for target in D2 {
        let mut unknown = f.core.context(target, &[])?;
        unknown.features.remove("single_line_input");
        assert!(select_core_inputs(&f.core.metadata, &unknown, &f.inventory()).is_err());
        assert!(
            f.core
                .context(target, &["single_line_input_unreviewed"])
                .is_err()
        );
    }
    for (oid, _, _) in RAW {
        let mut damaged = f.raw[oid].clone();
        damaged[0] ^= 1;
        assert!(verify_raw(&damaged, oid).is_err());
        let mut crlf = f.raw[oid].clone();
        crlf.insert(0, b'\r');
        assert!(verify_raw(&crlf, oid).is_err());
    }
    Ok(())
}

// The frozen four-provider ledger remains unchanged. This current consumer
// needs only the neutral graph; its fallback selector excludes every original
// consumer so combined feature requests still select exactly one provider.
fn verify_release_review_history_selection() -> Result<()> {
    let f = Fixture::load()?;
    let graph = path("SFMHistoryGraphContract");
    let metadata = f.bounded_metadata();
    for target in D2 {
        for consumer in [
            None,
            Some("single_line_input"),
            Some("document_history"),
            Some("trajectory_panels"),
            Some("workspace_counterfactuals"),
            Some("review_sessions"),
            Some("route_comparison"),
            Some("selection_history"),
        ] {
            let mut flags = vec!["release_review"];
            flags.extend(consumer);
            let context = f.core.context(target, &flags)?;
            let selected = select_core_inputs(&metadata, &context, &f.inventory())?;
            assert!(selected.inputs.contains_key(&graph));
            assert_eq!(
                f.render(&graph, &context)?
                    .expect("review graph selected")
                    .as_bytes(),
                f.raw[RAW[0].0]
            );
            let document_selected = matches!(
                consumer,
                Some("single_line_input" | "document_history" | "trajectory_panels")
            );
            for name in &NAMES[1..] {
                assert_eq!(selected.inputs.contains_key(&path(name)), document_selected);
            }
            assert!(!context.features["client_actions"]);
            assert!(!context.features["workspace_panels"]);
        }
    }
    Ok(())
}

#[test]
fn real_single_line_consumer_and_four_genuine_provider_signatures_remain_closed() -> Result<()> {
    let f = Fixture::load()?;
    let consumer = "src/main/java/ca/teamdman/sfm/client/input/SFMSingleLineInput.java";
    for target in D2 {
        let context = f.core.context(target, &["single_line_input"])?;
        let selected = select_core_inputs(
            &f.core.metadata,
            &context,
            &BTreeSet::from([consumer.to_owned()]),
        )?;
        let input = selected
            .inputs
            .get(consumer)
            .expect("prior genuine field provider required");
        let source = f.core.read_source(&input.input)?;
        let body = render_java_source(std::str::from_utf8(&source)?, &context)?;
        for anchor in [
            "SFMDocumentHistorySession.create(",
            "new MutationRequest(",
            "private DocumentState snapshot()",
            "history.currentState()",
            "history.undo(",
            "history.redo(",
            "public Optional<SFMDocumentHistorySession> history()",
        ] {
            assert!(
                body.contains(anchor),
                "prior genuine consumer anchor: {anchor}"
            );
        }
    }
    for (name, anchors) in [
        (
            "SFMDocumentHistoryContract",
            &[
                "public record SessionIdentity(",
                "public record DocumentState(",
                "public record MutationRequest(",
                "public record LogicalPoint(",
            ][..],
        ),
        (
            "SFMDocumentHistorySession",
            &[
                "create(SessionIdentity identity, DocumentState initialState)",
                "AppendResult append(MutationRequest request)",
                "HeadMoveResult undo(",
                "HeadMoveResult redo(",
                "synchronized DocumentState currentState()",
            ][..],
        ),
        (
            "SFMDocumentSemanticTransactionProjection",
            &[
                "static List<SemanticTransaction> project(",
                "List<DocumentHeadMovement> headMovements",
                "return List.copyOf(answer);",
            ][..],
        ),
        (
            "SFMHistoryGraphContract",
            &[
                "public record Graph(",
                "public record HeadMovement(",
                "public record RetentionPin(",
                "public record ActionAttempt(",
            ][..],
        ),
    ] {
        let source = std::str::from_utf8(&f.sources[&path(name)])?;
        for anchor in anchors {
            assert!(
                source.contains(anchor),
                "real provider anchor: {name}/{anchor}"
            );
        }
    }
    let session = std::str::from_utf8(&f.sources[&path("SFMDocumentHistorySession")])?;
    assert_eq!(
        session
            .matches("SFMDocumentSemanticTransactionProjection.project(")
            .count(),
        2
    );
    assert!(session.contains("new SFMHistoryGraphContract.Graph("));
    Ok(())
}

#[test]
fn complete_history_and_graph_public_api_preserves_authority_and_restoration_boundaries()
-> Result<()> {
    let f = Fixture::load()?;
    let contract = std::str::from_utf8(&f.sources[&path("SFMDocumentHistoryContract")])?;
    for anchor in [
        "ARCHIVE_SCHEMA = \"sfm.document-history-archive/1\"",
        "PROJECTION_SCHEMA = \"sfm.document-history-projection/1\"",
        "public record Archive(",
        "public record Projection(",
        "public record GraphIdentity(",
        "MessageDigest.getInstance(\"SHA-256\")",
        "requireWellFormedUtf16",
        "Primary selection is not present in selections",
    ] {
        assert!(
            contract.contains(anchor),
            "original contract boundary: {anchor}"
        );
    }
    let session = std::str::from_utf8(&f.sources[&path("SFMDocumentHistorySession")])?;
    for anchor in [
        "Archive belongs to another session",
        "Archive head identity differs",
        "Archive root identity differs",
        "Mutation id collision:",
        "NO_OP mutations must preserve",
        "HeadMoveStatus.AMBIGUOUS",
    ] {
        assert!(
            session.contains(anchor),
            "original session boundary: {anchor}"
        );
    }
    let graph = std::str::from_utf8(&f.sources[&path("SFMHistoryGraphContract")])?;
    for anchor in [
        "Projected irreversible effects must be explicit barriers",
        "A childless action attempt cannot publish a result state",
        "External barriers require an irreversible effect class",
        "Committed states must be materialized",
    ] {
        assert!(graph.contains(anchor), "original graph boundary: {anchor}");
    }
    // These are preserved value contracts, not permission to restore/replay or perform effects.
    Ok(())
}

#[test]
fn real_collector_omits_corrupt_unselected_history_before_read_and_templates_java() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    for path in f.inventory() {
        let destination = root.join(path);
        fs::create_dir_all(destination.parent().expect("fixed field source parent"))?;
        fs::write(destination, b"{% if features.unreviewed_history %}\n\xff")?;
    }
    let mut metadata = f.bounded_metadata();
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    for target in D2 {
        let context = f.core.context(target, &[])?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for path in f.inventory() {
            assert!(!artifacts.contains_key(&path));
        }
    }
    f.write_sources(&root)?;
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let probe_path = path("SFMHistoryGraphContract");
    let original = std::str::from_utf8(&f.sources[&probe_path])?;
    let probe = format!(
        "{original}{{% if features.single_line_input %}}\n// Isolated automatic Java proof.\n{{% endif %}}\n"
    );
    fs::write(root.join(&probe_path), &probe)?;
    for target in D2 {
        let context = f.core.context(target, &["single_line_input"])?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (path, source) in &f.sources {
            let artifact = &artifacts[path];
            let output = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = output.split_once('\n').expect("actual generated banner");
            let input = if *path == probe_path {
                probe.as_str()
            } else {
                std::str::from_utf8(source)?
            };
            assert_eq!(body, render_java_source(input, &context)?);
            assert!(!body.contains("{%"));
            assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{path}"));
            assert!(artifact.overlay.is_none());
        }
    }
    Ok(())
}

#[test]
fn common_history_edits_reach_both_targets_without_mutating_frozen_sources() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut edited = BTreeMap::new();
    for (path, source) in &f.sources {
        let original = std::str::from_utf8(source)?;
        let package = original.lines().next().expect("fixed package line");
        assert!(
            package.starts_with("package ca.teamdman.sfm.client.history") && package.ends_with(';')
        );
        let edit = original.replacen(
            package,
            &format!("{package}\n// Isolated shared history edit."),
            1,
        );
        assert_ne!(original, edit);
        fs::write(root.join(path), &edit)?;
        edited.insert(path.clone(), edit);
    }
    let metadata = f.bounded_metadata();
    let inventory = discover_core_source_files(&root)?;
    let mut count = 0;
    for target in D2 {
        let context = f.core.context(target, &["single_line_input"])?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for path in f.inventory() {
            let output = std::str::from_utf8(&artifacts[&path].output_bytes)?;
            let (_, body) = output.split_once('\n').expect("actual generated banner");
            assert_eq!(body, render_java_source(&edited[&path], &context)?);
            assert!(body.contains("// Isolated shared history edit."));
            count += 1;
        }
    }
    assert_eq!(count, 8);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}

#[test]
fn actual_shared_history_import_graph_is_bounded_and_grouping_algorithms_are_retained() -> Result<()>
{
    let f = Fixture::load()?;
    for source in f.sources.values() {
        let body = std::str::from_utf8(source)?;
        assert!(!body.contains("{%"));
        for line in body.lines().filter(|line| line.starts_with("import ")) {
            assert!(
                line.starts_with("import java.")
                    || line.starts_with(
                        "import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;"
                    )
                    || line.starts_with(
                        "import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract."
                    ),
                "genuine history tranche acquired missing external provider: {line}"
            );
        }
        for forbidden in [
            "Minecraft.getInstance",
            "org.lwjgl",
            "Files.write",
            "ProcessBuilder",
            "java.net.",
            "Class.forName",
            "ClientSigningKeyStore",
        ] {
            assert!(
                !body.contains(forbidden),
                "history source acquired effect provider: {forbidden}"
            );
        }
    }
    let projection =
        std::str::from_utf8(&f.sources[&path("SFMDocumentSemanticTransactionProjection")])?;
    for anchor in [
        "tickDelta < 0 || tickDelta > policy.idleBoundTicks()",
        "hasHeadMovementBetween",
        "hasBoundaryEventBetween",
        "selectionTopologyHash",
        "case KEY_RESET, PASTE, COMPLETION, FOCUS, ACTION -> true;",
        "Semantic transaction mutation chain is discontinuous",
        "return List.copyOf(answer);",
    ] {
        assert!(
            projection.contains(anchor),
            "original semantic grouping boundary: {anchor}"
        );
    }
    let session = std::str::from_utf8(&f.sources[&path("SFMDocumentHistorySession")])?;
    assert!(
        session.contains("for (Listener listener : listeners) listener.changed(notification);")
    );
    assert!(session.contains("return () -> listeners.remove(listener);"));
    assert!(
        session.contains("if (notify) notifyListeners(ChangeKind.RESTORED, archive.digest());")
    );
    Ok(())
}

#[test]
fn selection_history_and_timeline_operations_render_independently() -> Result<()> {
    let core = CoreTestFixture::load()?;
    let timeline =
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/timeline/SFMTimelinePanel.java";
    let actions = "src/main/java/ca/teamdman/sfm/client/history/SFMSelectionHistoryActions.java";
    let projection =
        "src/main/java/ca/teamdman/sfm/client/history/SFMSelectionHistoryGraphProjection.java";
    let episode = "src/main/java/ca/teamdman/sfm/client/history/SFMEpisodeContext.java";
    let repository = "src/main/java/ca/teamdman/sfm/client/explorer/SFMSelectionRepository.java";
    let graph = path("SFMHistoryGraphContract");
    let inventory = [
        timeline,
        actions,
        projection,
        episode,
        repository,
        graph.as_str(),
    ]
    .map(str::to_owned)
    .into_iter()
    .collect::<BTreeSet<_>>();
    for owner in [
        "timeline_dynamic_bounds",
        "timeline_wrapper_transparency",
        "timeline_positive_transition_ticks",
    ] {
        let definition = &core.features.0[owner];
        ensure!(
            definition.supported_targets == D2
                && definition.requires == ["timeline_panels", "client_theme"],
            "timeline operation acquired unrelated support/prerequisites: {owner}"
        );
    }
    let definition = &core.features.0["selection_history"];
    ensure!(
        definition.supported_targets == D2 && definition.requires.is_empty(),
        "selection operation acquired unrelated support/prerequisites"
    );
    let witnesses = [
        (
            timeline,
            "69725a833f466c7b7faeef8929339c90102ed089",
            14939,
            "sha256:7f62bd7f2e97c988bd5ba5728b00db7355db2c49807d0d6ea648e8c3567f18d0",
        ),
        (
            actions,
            "ed31ae18528453dc2a671541f91574676eaedf13",
            5081,
            "sha256:fc45efb7f3cfe6edf7b521738b920548966249c13bea600708f7cc480e533390",
        ),
        (
            projection,
            "2dccbd34d10e8fc43100fedbca0733952af99b41",
            11261,
            "sha256:db4bfb24b4c4d3140237e1667ffb9d6b2472a11dd1696839b9e7363147288886",
        ),
    ];
    let raw = read_git_blobs(
        &core.repository,
        &witnesses
            .iter()
            .map(|(_, oid, _, _)| (*oid).to_owned())
            .collect(),
    )?;
    for (_, oid, length, digest) in witnesses {
        ensure!(
            raw[oid].len() == length && sha256(&raw[oid]) == digest,
            "independent operation raw witness changed: {oid}"
        );
    }
    let cases: [(&str, &[&str]); 8] = [
        (
            "base",
            &["workspace_panels", "timeline_panels", "client_theme"],
        ),
        (
            "bounds only",
            &[
                "workspace_panels",
                "timeline_panels",
                "client_theme",
                "timeline_dynamic_bounds",
            ],
        ),
        (
            "wrapper only",
            &[
                "workspace_panels",
                "timeline_panels",
                "client_theme",
                "timeline_wrapper_transparency",
            ],
        ),
        (
            "wrapper with episode owner",
            &[
                "workspace_panels",
                "timeline_panels",
                "client_theme",
                "timeline_wrapper_transparency",
                "trajectory_panels",
            ],
        ),
        (
            "positive ticks only",
            &[
                "workspace_panels",
                "timeline_panels",
                "client_theme",
                "timeline_positive_transition_ticks",
            ],
        ),
        (
            "bounds and positive ticks",
            &[
                "workspace_panels",
                "timeline_panels",
                "client_theme",
                "timeline_dynamic_bounds",
                "timeline_positive_transition_ticks",
            ],
        ),
        ("selection only", &["selection_history"]),
        (
            "all witnessed operations",
            &[
                "workspace_panels",
                "timeline_panels",
                "client_theme",
                "timeline_dynamic_bounds",
                "timeline_wrapper_transparency",
                "timeline_positive_transition_ticks",
                "selection_history",
                "trajectory_panels",
            ],
        ),
    ];
    // This is a source-selection/rendering proof, not Java execution or compilation.
    for target in D2 {
        for (case, flags) in cases {
            let context = core.context(target, flags)?;
            let selected = select_core_inputs(&core.metadata, &context, &inventory)?;
            let selection = flags.contains(&"selection_history");
            let episode_owner = flags.contains(&"trajectory_panels");
            let mut bodies = BTreeMap::new();
            for (source, expected) in [
                (timeline, flags.contains(&"timeline_panels")),
                (actions, selection),
                (projection, selection),
                (repository, selection),
                (graph.as_str(), selection || episode_owner),
                (episode, episode_owner),
            ] {
                ensure!(
                    selected.inputs.contains_key(source) == expected
                        && selected.omitted_paths.contains(source) != expected,
                    "{target}/{case}: independent operation membership changed: {source}"
                );
                if expected {
                    let source_bytes = core.read_source(source)?;
                    let body = render_java_source(std::str::from_utf8(&source_bytes)?, &context)?;
                    ensure!(
                        !body.contains("{%"),
                        "{target}/{case}: opaque directive leaked: {source}"
                    );
                    bodies.insert(source.to_owned(), body);
                }
            }
            if let Some(body) = bodies.get(timeline) {
                let dynamic = flags.contains(&"timeline_dynamic_bounds");
                let wrapper = flags.contains(&"timeline_wrapper_transparency");
                let positive = flags.contains(&"timeline_positive_transition_ticks");
                for marker in [
                    "private final int defaultTicksPerTransition;",
                    "private SFMTimelineModel model;",
                    "public void seekWhenAvailable(int keyframe)",
                    "refreshTimelineBounds();",
                    "double retainedPosition = nextBounds.clamp(model.keyframePosition());",
                ] {
                    ensure!(
                        body.contains(marker) == dynamic,
                        "{target}/{case}: bounds operation: {marker}"
                    );
                }
                ensure!(
                    body.contains("private final SFMTimelineModel model;") != dynamic
                        && body.matches("pendingKeyframeSeek = null;").count()
                            == if dynamic { 4 } else { 0 },
                    "{target}/{case}: mutable model or user-seek cancellation changed"
                );
                ensure!(
                    body.contains("public SFMSeekableTimelinePanel child() { return child; }")
                        == wrapper,
                    "{target}/{case}: wrapper access coupled to another operation"
                );
                for marker in [
                    "import ca.teamdman.sfm.client.history.SFMEpisodeContext;",
                    "implements SFMScreenPanel, SFMEpisodeContext",
                    "public java.util.Optional<String> episodeId()",
                    "context.episodeId()",
                ] {
                    ensure!(
                        body.contains(marker) == (wrapper && episode_owner),
                        "{target}/{case}: episode forwarding: {marker}"
                    );
                }
                ensure!(
                    body.contains("if (defaultTicksPerTransition <= 0)") == positive
                        && body.contains("defaultTicksPerTransition must be positive") == positive,
                    "{target}/{case}: constructor validation coupled to another operation"
                );
                if dynamic && wrapper && positive && episode_owner {
                    ensure!(
                        body.as_bytes() == raw[witnesses[0].1].as_slice(),
                        "{target}/{case}: exact old-two timeline witness mismatch"
                    );
                }
            }
            if selection {
                for (source, oid, _, _) in &witnesses[1..] {
                    ensure!(
                        bodies[*source].as_bytes() == raw[*oid].as_slice(),
                        "{target}/{case}: exact selection operation witness mismatch: {source}"
                    );
                }
                let body = &bodies[actions];
                for operation in [
                    "ENUMERATE",
                    "CHECKOUT",
                    "UNDO",
                    "REDO",
                    "REDO_CHILD",
                    "NAME_HEAD",
                ] {
                    ensure!(
                        body.contains(&format!("case {operation} ->")),
                        "{target}/{case}: missing own typed selection operation: {operation}"
                    );
                }
                let body = &bodies[projection];
                ensure!(
                    body.contains("archive.headEvents()")
                        && body.contains("selection.namedHeadRevisionIds()")
                        && body.contains("SFMHistoryGraphContract.UndoDomainKind.SELECTION"),
                    "{target}/{case}: selection projection lost its own topology"
                );
            }
        }
    }
    Ok(())
}

const GUARDED_SOURCE_CASES: [(&str, &str, &str); 3] = [
    (
        "src/main/java/ca/teamdman/sfm/client/history/SFMHistoryGraphRuntime.java",
        "ac320fce60e8bb318b2f7bbad5afce129ab4d94e",
        "sha256:3ed91fadd02ee930d15d5c45282c05aa7a5d896ec5fa8e49d009b602963989fc",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/history/chamber/SFMTemporalReplayJournal.java",
        "14a67987394b5264768d3c9432cc101df2fac3b6",
        "sha256:26867a4e883b7b441501ec0342b57d7ff5510f29903ab525b58c869630b9bb49",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/history/SFMDocumentHistoryInputRouting.java",
        "6a3ac1d53cef11873340c5019ba8ed0fc72079ea",
        "sha256:d0b6ba3c5308afa09267f939cc9535a150e800617ff213e89e4bc703b180b8fa",
    ),
];

#[test]
fn history_runtime_routing_and_keyboard_guards_preserve_exact_owned_seams() -> Result<()> {
    let f = Fixture::load()?;
    let raw = read_git_blobs(
        &f.core.repository,
        &GUARDED_SOURCE_CASES
            .iter()
            .map(|(_, oid, _)| (*oid).to_owned())
            .collect(),
    )?;
    for (_, oid, digest) in GUARDED_SOURCE_CASES {
        assert_eq!(sha256(&raw[oid]), digest);
    }
    let runtime = GUARDED_SOURCE_CASES[0].0;
    let journal = GUARDED_SOURCE_CASES[1].0;
    let routing = GUARDED_SOURCE_CASES[2].0;
    for target in D2 {
        for flags in [
            &["single_line_input"][..],
            &["document_history"][..],
            &["trajectory_panels"][..],
            &["workspace_counterfactuals"][..],
            &["review_sessions"][..],
            &["route_comparison"][..],
        ] {
            let context = f.core.context(target, flags)?;
            let inventory = BTreeSet::from([runtime.to_owned()]);
            let selected = select_core_inputs(&f.core.metadata, &context, &inventory)?;
            assert!(selected.inputs.contains_key(runtime));
            let source = f.core.read_source(runtime)?;
            let body = render_java_source(std::str::from_utf8(&source)?, &context)?;
            let machine = !matches!(flags[0], "single_line_input" | "document_history");
            let candidate = matches!(
                flags[0],
                "trajectory_panels" | "review_sessions" | "route_comparison"
            );
            assert!(body.contains("public record OperationResult("));
            for marker in [
                "SFMEntitySelector",
                "SFMHistoryGraphPresentationModel",
                "SFMTemporalReplayArchive",
                "MachineSnapshot",
                "controllers",
            ] {
                assert_eq!(
                    body.contains(marker),
                    machine,
                    "{target}/{flags:?}/{marker}"
                );
            }
            assert_eq!(body.contains("SFMCandidateHistoryContract"), candidate);
            if flags[0] == "trajectory_panels" {
                assert!(
                    body.as_bytes() == raw[GUARDED_SOURCE_CASES[0].1].as_slice(),
                    "{target}/{flags:?}: exact runtime witness mismatch"
                );
            }
        }
        for flags in [
            &["trajectory_panels"][..],
            &["trajectory_panels", "keyboard_profiles", "client_actions"][..],
        ] {
            let context = f.core.context(target, flags)?;
            let inventory = BTreeSet::from([journal.to_owned()]);
            let selected = select_core_inputs(&f.core.metadata, &context, &inventory)?;
            assert!(selected.inputs.contains_key(journal));
            let source = f.core.read_source(journal)?;
            let body = render_java_source(std::str::from_utf8(&source)?, &context)?;
            if context.features["keyboard_profiles"] {
                assert!(
                    body.as_bytes() == raw[GUARDED_SOURCE_CASES[1].1].as_slice(),
                    "{target}/{flags:?}: exact keyboard journal witness mismatch"
                );
            } else {
                for provider in [
                    "SFMClientActionInvocationTrace",
                    "SFMKeyBinding",
                    "SFMKeyInputEvent",
                ] {
                    assert!(
                        !body.contains(provider),
                        "unowned keyboard provider: {provider}"
                    );
                }
                assert!(
                    body.contains("String command = \"controller \" + semanticAction.actionId();")
                );
                assert!(body.contains("SFMTemporalReplayArchive.EventOrigin.CONTROLLER_API;"));
                assert!(body.contains("SFMTemporalReplayArchive.EventKind.CONTROLLER_OPERATION;"));
            }
        }
        for flags in [
            &["document_history"][..],
            &["document_history", "workspace_panels"][..],
        ] {
            let context = f.core.context(target, flags)?;
            let inventory = BTreeSet::from([routing.to_owned()]);
            let selected = select_core_inputs(&f.core.metadata, &context, &inventory)?;
            assert!(selected.inputs.contains_key(routing));
            let source = f.core.read_source(routing)?;
            let body = render_java_source(std::str::from_utf8(&source)?, &context)?;
            if context.features["workspace_panels"] {
                assert!(
                    body.as_bytes() == raw[GUARDED_SOURCE_CASES[2].1].as_slice(),
                    "{target}/{flags:?}: exact input routing witness mismatch"
                );
            } else {
                assert!(!body.contains("SFMScreenMultiplexer"));
                assert!(body.contains("instanceof SFMDocumentHistoryInputTarget direct"));
            }
        }
    }
    Ok(())
}
