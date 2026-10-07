//! Test-only regression for three pure action/shared helper leaves.
//!
//! Production membership runs before reads and every emitted Java body passes
//! through the controlled renderer. Historical blobs are bounded test witnesses
//! only. This is not a complete Java-build, UI or programmatic-permission proof.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::discover_core_source_files;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

const PANEL_ID: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspacePanelId.java";
const STRUCTURED: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionStructuredResult.java";
const SCORER: &str = "src/main/java/ca/teamdman/sfm/client/search/SFMFuzzyScorer.java";
const PATHS: [&str; 3] = [PANEL_ID, STRUCTURED, SCORER];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const LEDGER: &str = "docs/tasks/sfm-core-action-leaf-slice.json";
const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";
const D2_HISTORICAL_FEATURES: [&str; 8] = [
    "client_actions",
    "structured_action_results",
    "workspace_panels",
    "client_theme",
    "keyboard_profiles",
    "command_palette",
    "typed_command_palette",
    "command_history",
];
const PALETTE_PREREQUISITES: [&str; 4] = [
    "client_actions",
    "client_theme",
    "keyboard_profiles",
    "command_palette",
];
const CONTEXT_COMMITS: [(&str, &str); 20] = [
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

#[derive(Clone, Copy)]
struct Golden {
    path: &'static str,
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}

const GOLDENS: [Golden; 3] = [
    Golden {
        path: PANEL_ID,
        oid: "7f96dbc242b9dc4ebc1625a8f4d26bd228e523e7",
        digest: "sha256:c300406b1a1a52c4ea856d399f2f06c17564dbc52be863d6e5ec47e033880439",
        bytes: 320,
    },
    Golden {
        path: STRUCTURED,
        oid: "a320b7443da632303d40e7836bb5f9d09c8eded1",
        digest: "sha256:d6deefc66052c4138aa6889c0fd0b4366d36711808928cf19debb3011e4d64b7",
        bytes: 2492,
    },
    Golden {
        path: SCORER,
        oid: "968ce56a9a760db6bef98cb28eea30d15107662e",
        digest: "sha256:92a1bd9c0f630099a62ff721ecaeb5f3796e87913adf17ae1341ae55df2ae2b1",
        bytes: 3091,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: String,
    files: Vec<Leaf>,
}

#[derive(Facet)]
struct Leaf {
    path: String,
    core_path: String,
    template_sha256: String,
    template_bytes: usize,
    raw_blob: String,
    raw_sha256: String,
    raw_bytes: usize,
    line_endings: String,
    terminal_newline: String,
    normalization: String,
    membership: Membership,
    witnesses: Vec<Witness>,
}

#[derive(Facet)]
struct Membership {
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
    none_features: Vec<String>,
}

#[derive(Facet)]
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    raw_blob: Option<String>,
    raw_sha256: Option<String>,
    raw_bytes: Option<usize>,
    mode: Option<String>,
    explicit_registered_features: Vec<String>,
}

struct LeafFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}

impl LeafFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let source = String::from_utf8(read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            1024 * 1024,
        )?)?;
        let ledger: Ledger = facet_json::from_str(&source)?;
        ensure!(
            ledger.schema == "sfm:core_action_leaf_slice@1"
                && ledger.context_commits == pinned_commits()
                && ledger.normalization == "none_raw_exact_lf_with_exactly_one_terminal_lf"
                && ledger.files.len() == 3,
            "leaf ledger identity or normalization changed"
        );
        let mut seen = BTreeSet::new();
        for leaf in ledger.files {
            let golden = golden(&leaf.path)?;
            ensure!(
                seen.insert(leaf.path.clone())
                    && leaf.core_path == format!("{CORE_PREFIX}{}", leaf.path)
                    && leaf.template_sha256 == golden.digest
                    && leaf.template_bytes == golden.bytes
                    && leaf.raw_blob == golden.oid
                    && leaf.raw_sha256 == golden.digest
                    && leaf.raw_bytes == golden.bytes
                    && leaf.line_endings == "lf"
                    && leaf.terminal_newline == "exactly_one_lf"
                    && leaf.normalization == "none",
                "leaf raw/template contract changed"
            );
            validate_membership(&core, &leaf)?;
            ensure!(leaf.witnesses.len() == 20, "incomplete leaf witness set");
            let mut contexts = BTreeSet::new();
            for witness in leaf.witnesses {
                let commit = pinned_commits()
                    .get(&witness.context)
                    .cloned()
                    .ok_or_else(|| eyre::eyre!("unknown leaf witness context"))?;
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid pinned leaf context"))?;
                ensure!(
                    contexts.insert(witness.context.clone()) && witness.source_commit == commit,
                    "duplicate or changed leaf witness commit"
                );
                let expected_features = historical_features(environment, target);
                ensure!(
                    witness.explicit_registered_features.len() == expected_features.len()
                        && witness
                            .explicit_registered_features
                            .iter()
                            .map(String::as_str)
                            .collect::<BTreeSet<_>>()
                            == expected_features.iter().copied().collect(),
                    "leaf historical explicit feature context changed"
                );
                core.context(target, &expected_features)?;
                let present = environment == "dev" && (leaf.path == PANEL_ID || is_d2(target));
                ensure!(
                    witness.present == present
                        && witness.raw_blob.as_deref() == present.then_some(golden.oid)
                        && witness.raw_sha256.as_deref() == present.then_some(golden.digest)
                        && witness.raw_bytes == present.then_some(golden.bytes)
                        && witness.mode.as_deref() == present.then_some("100644"),
                    "leaf historical membership/raw witness changed"
                );
            }
        }
        ensure!(seen.len() == PATHS.len(), "leaf scope changed");
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|row| row.oid.to_owned()).collect(),
        )?;
        for row in GOLDENS {
            let bytes = &raw[row.oid];
            ensure!(
                bytes.len() == row.bytes
                    && sha256(bytes) == row.digest
                    && !bytes.contains(&b'\r')
                    && bytes.ends_with(b"\n")
                    && !bytes.ends_with(b"\n\n"),
                "leaf raw bytes or newline witness changed"
            );
            std::str::from_utf8(bytes)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "missing authored leaf input"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }

    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selection.inputs.get(path) else {
            ensure!(
                selection.omitted_paths.contains(path),
                "leaf vanished outside explicit membership"
            );
            return Ok(None);
        };
        ensure!(
            input.input == path,
            "leaf uses an unreviewed alternate source"
        );
        // No leaf source is read until the production selector includes it.
        let source = self.core.read_source(path)?;
        let golden = golden(path)?;
        ensure!(
            source.len() == golden.bytes && sha256(&source) == golden.digest,
            "authored leaf source differs from its bounded raw contract"
        );
        Ok(Some(
            render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes(),
        ))
    }

    fn assert_output(&self, path: &str, context: &ProjectionContext, present: bool) -> Result<()> {
        let rendered = self.render(path, context)?;
        let expected =
            present.then(|| self.raw[golden(path).expect("fixed leaf path").oid].as_slice());
        assert_eq!(
            rendered.as_deref(),
            expected,
            "{path} / {}",
            context.minecraft_version
        );
        Ok(())
    }
}

fn pinned_commits() -> BTreeMap<String, String> {
    CONTEXT_COMMITS
        .into_iter()
        .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
        .collect()
}

fn golden(path: &str) -> Result<Golden> {
    GOLDENS
        .iter()
        .find(|row| row.path == path)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected leaf path"))
}

fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}

fn historical_features(environment: &str, target: &str) -> Vec<&'static str> {
    if environment == "release" {
        Vec::new()
    } else if is_d2(target) {
        D2_HISTORICAL_FEATURES.to_vec()
    } else {
        vec!["workspace_panels"]
    }
}

fn validate_membership(core: &CoreTestFixture, leaf: &Leaf) -> Result<()> {
    let (targets, all, any) = match leaf.path.as_str() {
        PANEL_ID => (&[][..], &["workspace_panels"][..], &[][..]),
        STRUCTURED => (&TARGETS[..2], &["structured_action_results"][..], &[][..]),
        SCORER => (
            &TARGETS[..2],
            &[][..],
            &["typed_command_palette", "command_history"][..],
        ),
        _ => eyre::bail!("unknown leaf path"),
    };
    let same_set = |actual: &[String], expected: &[&str]| {
        actual.len() == expected.len()
            && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
                == expected.iter().copied().collect()
    };
    ensure!(
        same_set(&leaf.membership.targets, targets)
            && same_set(&leaf.membership.all_features, all)
            && same_set(&leaf.membership.any_features, any)
            && leaf.membership.none_features.is_empty(),
        "leaf ledger source predicate changed"
    );
    let rules = core
        .metadata
        .source_rules
        .get(&leaf.path)
        .ok_or_else(|| eyre::eyre!("leaf has no explicit production membership rule"))?;
    // Preserve the original ledger ownership witness above. Current neutral
    // consumers share exact helper bytes without enabling unrelated behavior.
    let (current_all, current_any) = match leaf.path.as_str() {
        PANEL_ID => (&[][..], &["workspace_panels", "workspace_dividers"][..]),
        SCORER => (
            all,
            &[
                "typed_command_palette",
                "command_history",
                "editor_search",
                "explorer_search",
                "release_review",
                "client_control_cli",
                "explorer_compaction",
                "explorer_navigation",
                "file_explorer",
                "java_symbols",
                "registry_explorer",
                "workspace_counterfactuals",
            ][..],
        ),
        _ => (all, any),
    };
    ensure!(
        rules.len() == 1
            && rules[0].input == leaf.path
            && same_set(&rules[0].when.targets, targets)
            && same_set(&rules[0].when.all_features, current_all)
            && same_set(&rules[0].when.any_features, current_any)
            && rules[0].when.none_features.is_empty(),
        "leaf production predicate differs from its reviewed functional owner"
    );
    Ok(())
}

#[test]
fn three_leaf_inputs_reconstruct_twenty_historical_membership_contexts() -> Result<()> {
    let fixture = LeafFixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXT_COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed contexts");
        let context = fixture
            .core
            .context(target, &historical_features(environment, target))?;
        for path in PATHS {
            let expected = environment == "dev" && (path == PANEL_ID || is_d2(target));
            fixture.assert_output(path, &context, expected)?;
            if expected {
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (14, 46));
    Ok(())
}

#[test]
fn leaf_twenty_historical_feature_off_controls_omit_before_source_reads() -> Result<()> {
    let mut fixture = LeafFixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    // A missing source boundary demonstrates that these disabled paths do not
    // merely render an empty body after reading their Java inputs.
    fixture.core.core = fixture
        .core
        .core
        .join("deliberately_missing_leaf_read_boundary");
    let mut omitted = 0;
    for key in catalog.catalog.0.keys() {
        let context = fixture.core.historical_feature_off_catalog_context(key)?;
        for path in PATHS {
            assert!(
                fixture.render(path, &context)?.is_none(),
                "historical feature-off {key} / {path} unexpectedly includes an owner"
            );
            omitted += 1;
        }
    }
    assert_eq!(omitted, 60);
    Ok(())
}

#[test]
fn leaf_owners_toggle_independently_with_actual_registered_prerequisites() -> Result<()> {
    let fixture = LeafFixture::load()?;
    let mut cases = 0;
    for target in &TARGETS[..2] {
        for structured in [false, true] {
            for workspace in [false, true] {
                // 0=legacy palette only; 1=typed without history; 2=typed+history.
                // History without typed is prohibited by the real registry.
                for palette in 0_u8..3 {
                    let mut enabled = PALETTE_PREREQUISITES.to_vec();
                    if structured {
                        enabled.push("structured_action_results");
                    }
                    if workspace {
                        enabled.push("workspace_panels");
                    }
                    if palette > 0 {
                        enabled.push("typed_command_palette");
                    }
                    if palette > 1 {
                        enabled.push("command_history");
                    }
                    let context = fixture.core.context(target, &enabled)?;
                    fixture.assert_output(STRUCTURED, &context, structured)?;
                    fixture.assert_output(PANEL_ID, &context, workspace)?;
                    fixture.assert_output(SCORER, &context, palette > 0)?;
                    for forbidden in [
                        "client_program_actions",
                        "client_program_consent",
                        "packet_values",
                        "context_actions",
                    ] {
                        assert!(!context.features[forbidden]);
                    }
                    cases += 1;
                }
            }
        }
    }
    assert_eq!(cases, 24);
    for target in TARGETS {
        let workspace = fixture.core.context(target, &["workspace_panels"])?;
        fixture.assert_output(PANEL_ID, &workspace, true)?;
        fixture.assert_output(STRUCTURED, &workspace, false)?;
        fixture.assert_output(SCORER, &workspace, false)?;
        let basic = fixture.core.context(target, &["client_actions"])?;
        for path in PATHS {
            fixture.assert_output(path, &basic, false)?;
        }
    }
    Ok(())
}

#[test]
fn shared_matcher_owners_select_raw_scorer_without_palette_or_history() -> Result<()> {
    let fixture = LeafFixture::load()?;
    for target in &TARGETS[..2] {
        for owner in ["editor_search", "explorer_search", "release_review"] {
            let context = fixture.core.context(target, &[owner])?;
            fixture.assert_output(SCORER, &context, true)?;
            fixture.assert_output(STRUCTURED, &context, false)?;
            fixture.assert_output(PANEL_ID, &context, false)?;
            for forbidden in [
                "typed_command_palette",
                "command_history",
                "context_actions",
                "client_actions",
            ] {
                assert!(!context.features[forbidden]);
            }
        }
    }
    Ok(())
}

#[test]
fn old_target_legacy_palettes_do_not_import_d2_ranking_or_machine_results() -> Result<()> {
    let fixture = LeafFixture::load()?;
    for target in &TARGETS[2..] {
        let mut enabled = PALETTE_PREREQUISITES.to_vec();
        let legacy = fixture.core.context(target, &enabled)?;
        for path in PATHS {
            fixture.assert_output(path, &legacy, false)?;
        }
        enabled.push("workspace_panels");
        let workspace = fixture.core.context(target, &enabled)?;
        fixture.assert_output(PANEL_ID, &workspace, true)?;
        fixture.assert_output(STRUCTURED, &workspace, false)?;
        fixture.assert_output(SCORER, &workspace, false)?;
        for unsupported in [
            "structured_action_results",
            "typed_command_palette",
            "command_history",
        ] {
            let mut invalid = enabled.clone();
            invalid.push(unsupported);
            assert!(fixture.core.context(target, &invalid).is_err());
        }
    }
    Ok(())
}

#[test]
fn leaf_prerequisites_fail_closed_and_existing_bounds_remain_raw_exact() -> Result<()> {
    let fixture = LeafFixture::load()?;
    for target in &TARGETS[..2] {
        for invalid in [
            vec!["structured_action_results"],
            vec!["typed_command_palette"],
            vec!["command_history"],
            vec!["client_actions", "command_history"],
        ] {
            assert!(fixture.core.context(target, &invalid).is_err());
        }
    }
    let structured = std::str::from_utf8(&fixture.raw[golden(STRUCTURED)?.oid])?;
    for marker in [
        "MAX_SCHEMA_ID_BYTES = 128",
        "MAX_JSON_BYTES = 512 * 1024",
        "[a-z0-9][a-z0-9._-]*/[1-9][0-9]*",
        "parsed.isJsonObject()",
        "CodingErrorAction.REPORT",
    ] {
        assert!(
            structured.contains(marker),
            "lost structured envelope boundary: {marker}"
        );
    }
    let panel = std::str::from_utf8(&fixture.raw[golden(PANEL_ID)?.oid])?;
    assert!(panel.contains("value < 0"));
    let scorer = std::str::from_utf8(&fixture.raw[golden(SCORER)?.oid])?;
    assert!(scorer.contains("StringDistances.damerauLevenshtein()"));
    assert!(scorer.contains("DEFAULT_THRESHOLD = 0.65F"));
    assert!(scorer.contains("Locale.ROOT"));
    Ok(())
}
