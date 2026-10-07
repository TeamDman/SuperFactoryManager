//! Test-only regression for four pure typed-palette helper inputs.
//!
//! The real source selector excludes disabled owners before any source read;
//! the real controlled renderer supplies included bodies. Pinned Git blobs are
//! bounded test witnesses only, never production inputs. This establishes no
//! complete Java build, persistence, palette UI or action-execution authority.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
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

const COMPLETION: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionCompletion.java";
const APPLICATION: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMCompletionApplication.java";
const FRONTIER: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMCommandFrontierAnalysis.java";
const CANDIDATE: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMPaletteCandidate.java";
const PATHS: [&str; 4] = [COMPLETION, APPLICATION, FRONTIER, CANDIDATE];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const LEDGER: &str = "docs/tasks/sfm-core-typed-palette-slice.json";
const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";
const LEGACY_FEATURES: [&str; 4] = [
    "client_actions",
    "client_theme",
    "keyboard_profiles",
    "command_palette",
];
const TYPED_FEATURES: [&str; 5] = [
    "client_actions",
    "client_theme",
    "keyboard_profiles",
    "command_palette",
    "typed_command_palette",
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

const GOLDENS: [Golden; 4] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionCompletion.java",
        oid: "76157bd87cbec785fd1ca2773650a86ae5b6ba29",
        digest: "sha256:6a46213b85eb879d9dc2854f5cb54bc6f1300f3c21351530237c5afbb97c8732",
        bytes: 930,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/action/SFMCommandFrontierAnalysis.java",
        oid: "e145b062872bd15e331923c61f83322c6caeeec3",
        digest: "sha256:be628285c7fd7cf7b150a4f67852eaf4bf86af843cb87e77469ede48a3ffe50e",
        bytes: 5180,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/action/SFMPaletteCandidate.java",
        oid: "f25fa650d5bc186d701d335f14cf6ddb6cbeb679",
        digest: "sha256:4a27565d055f7239c4b1a53f4c3d471d177ccc03c2f893c3f120cfbf8ce0499e",
        bytes: 7032,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/action/SFMCompletionApplication.java",
        oid: "503c23f7c872b2420602229f70608bc6064eb8a6",
        digest: "sha256:039b340011c908047f939ad2445ecfa6c9b8b9ac80cd238b01913fd977cfe6ce",
        bytes: 1233,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: String,
    files: Vec<InputEvidence>,
}

#[derive(Facet)]
struct InputEvidence {
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
    owner: String,
    membership: Membership,
    registered_prerequisite_closure: Vec<String>,
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

struct TypedPaletteFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}

impl TypedPaletteFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let source = String::from_utf8(read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            1024 * 1024,
        )?)?;
        let ledger: Ledger = facet_json::from_str(&source)?;
        ensure!(
            ledger.schema == "sfm:core_typed_palette_slice@1"
                && ledger.context_commits == pinned_commits()
                && ledger.normalization == "none_raw_exact_lf_with_exactly_one_terminal_lf"
                && ledger.files.len() == 4,
            "typed-palette ledger identity or normalization changed"
        );
        let mut paths = BTreeSet::new();
        for input in ledger.files {
            let golden = golden(&input.path)?;
            ensure!(
                paths.insert(input.path.clone())
                    && input.core_path == format!("{CORE_PREFIX}{}", input.path)
                    && input.template_sha256 == golden.digest
                    && input.template_bytes == golden.bytes
                    && input.raw_blob == golden.oid
                    && input.raw_sha256 == golden.digest
                    && input.raw_bytes == golden.bytes
                    && input.line_endings == "lf"
                    && input.terminal_newline == "exactly_one_lf"
                    && input.normalization == "none"
                    && input.owner == "typed_command_palette"
                    && same_names(&input.registered_prerequisite_closure, &LEGACY_FEATURES),
                "typed-palette raw/template owner contract changed"
            );
            validate_membership(&core, &input)?;
            ensure!(
                input.witnesses.len() == 20,
                "incomplete typed-palette witnesses"
            );
            let mut contexts = BTreeSet::new();
            for witness in input.witnesses {
                let commit = pinned_commits()
                    .get(&witness.context)
                    .cloned()
                    .ok_or_else(|| eyre::eyre!("unknown typed-palette witness context"))?;
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid pinned typed-palette context"))?;
                ensure!(
                    contexts.insert(witness.context.clone()) && witness.source_commit == commit,
                    "duplicate or changed typed-palette witness commit"
                );
                let present = environment == "dev" && is_d2(target);
                let expected_features = if present {
                    &TYPED_FEATURES[..]
                } else {
                    &[][..]
                };
                ensure!(
                    same_names(&witness.explicit_registered_features, expected_features),
                    "typed-palette historical explicit feature context changed"
                );
                core.context(target, expected_features)?;
                ensure!(
                    witness.present == present
                        && witness.raw_blob.as_deref() == present.then_some(golden.oid)
                        && witness.raw_sha256.as_deref() == present.then_some(golden.digest)
                        && witness.raw_bytes == present.then_some(golden.bytes)
                        && witness.mode.as_deref() == present.then_some("100644"),
                    "typed-palette historical membership/raw witness changed"
                );
            }
        }
        ensure!(
            paths == PATHS.into_iter().map(str::to_owned).collect(),
            "typed-palette scope changed"
        );
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
                "typed-palette raw bytes/newline witness changed"
            );
            std::str::from_utf8(bytes)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "missing authored typed-palette input"
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
                "typed-palette source vanished outside explicit membership"
            );
            return Ok(None);
        };
        ensure!(
            input.input == path,
            "unreviewed alternate typed-palette source"
        );
        // No Java input read precedes this production membership decision.
        let source = self.core.read_source(path)?;
        let golden = golden(path)?;
        ensure!(
            source.len() == golden.bytes && sha256(&source) == golden.digest,
            "authored typed-palette input differs from its bounded raw contract"
        );
        Ok(Some(
            render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes(),
        ))
    }

    fn assert_cohort(&self, context: &ProjectionContext, present: bool) -> Result<()> {
        for path in PATHS {
            let output = self.render(path, context)?;
            let expected =
                present.then(|| self.raw[golden(path).expect("fixed path").oid].as_slice());
            assert_eq!(
                output.as_deref(),
                expected,
                "{path} / {}",
                context.minecraft_version
            );
        }
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
        .ok_or_else(|| eyre::eyre!("unexpected typed-palette input"))
}

fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}

fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}

fn validate_membership(core: &CoreTestFixture, input: &InputEvidence) -> Result<()> {
    ensure!(
        same_names(&input.membership.targets, &TARGETS[..2])
            && same_names(&input.membership.all_features, &["typed_command_palette"])
            && input.membership.any_features.is_empty()
            && input.membership.none_features.is_empty(),
        "typed-palette ledger source predicate changed"
    );
    let rules = core
        .metadata
        .source_rules
        .get(&input.path)
        .ok_or_else(|| eyre::eyre!("typed-palette source lacks an explicit predicate"))?;
    ensure!(
        rules.len() == 1
            && rules[0].input == input.path
            && same_names(&rules[0].when.targets, &TARGETS[..2])
            && same_names(&rules[0].when.all_features, &["typed_command_palette"])
            && rules[0].when.any_features.is_empty()
            && rules[0].when.none_features.is_empty(),
        "typed-palette production predicate changed"
    );
    Ok(())
}

#[test]
fn typed_palette_cohort_reconstructs_twenty_exact_historical_contexts() -> Result<()> {
    let fixture = TypedPaletteFixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXT_COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed contexts");
        let expected = environment == "dev" && is_d2(target);
        let features = if expected {
            &TYPED_FEATURES[..]
        } else {
            &[][..]
        };
        let context = fixture.core.context(target, features)?;
        fixture.assert_cohort(&context, expected)?;
        if expected {
            present += 4;
        } else {
            absent += 4;
        }
    }
    assert_eq!((present, absent), (8, 72));
    Ok(())
}

#[test]
fn typed_helpers_feature_off_controls_omit_before_input_reads() -> Result<()> {
    let mut fixture = TypedPaletteFixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture
        .core
        .core
        .join("deliberately_missing_typed_palette_boundary");
    let mut omitted = 0;
    for key in catalog.catalog.0.keys() {
        fixture.assert_cohort(
            &fixture.core.historical_feature_off_catalog_context(key)?,
            false,
        )?;
        omitted += 4;
    }
    assert_eq!(omitted, 80);
    Ok(())
}

#[test]
fn typed_helper_membership_is_independent_of_history_workspace_and_diagnostics() -> Result<()> {
    let fixture = TypedPaletteFixture::load()?;
    let mut cases = 0;
    for target in &TARGETS[..2] {
        for palette in 0_u8..3 {
            for mask in 0_u8..8 {
                let mut enabled = LEGACY_FEATURES.to_vec();
                if palette > 0 {
                    enabled.push("typed_command_palette");
                }
                if palette > 1 {
                    enabled.push("command_history");
                }
                if mask & 1 != 0 {
                    enabled.push("workspace_panels");
                }
                if mask & 2 != 0 {
                    enabled.push("structured_action_results");
                }
                if mask & 4 != 0 {
                    enabled.push("client_action_completion_diagnostics");
                }
                let context = fixture.core.context(target, &enabled)?;
                fixture.assert_cohort(&context, palette > 0)?;
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
    assert_eq!(cases, 48);
    Ok(())
}

#[test]
fn legacy_targets_and_invalid_prerequisites_do_not_expand_typed_support() -> Result<()> {
    let fixture = TypedPaletteFixture::load()?;
    for target in TARGETS {
        let basic = fixture.core.context(target, &["client_actions"])?;
        fixture.assert_cohort(&basic, false)?;
        let workspace = fixture.core.context(target, &["workspace_panels"])?;
        fixture.assert_cohort(&workspace, false)?;
        let mut legacy = LEGACY_FEATURES.to_vec();
        legacy.push("workspace_panels");
        fixture.assert_cohort(&fixture.core.context(target, &legacy)?, false)?;
        if !is_d2(target) {
            legacy.push("typed_command_palette");
            assert!(fixture.core.context(target, &legacy).is_err());
        }
    }
    for target in &TARGETS[..2] {
        for invalid in [
            vec!["typed_command_palette"],
            vec!["command_history"],
            vec!["client_actions", "typed_command_palette"],
            vec!["client_actions", "command_palette", "typed_command_palette"],
            vec![
                "client_actions",
                "client_theme",
                "command_palette",
                "typed_command_palette",
            ],
        ] {
            assert!(fixture.core.context(target, &invalid).is_err());
        }
    }
    Ok(())
}

#[test]
fn selected_typed_model_edges_and_raw_pure_contracts_are_coherent() -> Result<()> {
    let fixture = TypedPaletteFixture::load()?;
    for target in &TARGETS[..2] {
        let context = fixture.core.context(target, &TYPED_FEATURES)?;
        let selection = select_core_inputs(&fixture.core.metadata, &context, &fixture.inventory)?;
        for dependency in [
            "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionContext.java",
            "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionSource.java",
            "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionExecutor.java",
            "src/main/java/ca/teamdman/sfm/client/search/SFMFuzzyScorer.java",
        ] {
            assert!(
                selection.inputs.contains_key(dependency),
                "missing reviewed type edge: {dependency}"
            );
        }
        fixture.assert_cohort(&context, true)?;
    }
    let text = |path| -> Result<&str> { Ok(std::str::from_utf8(&fixture.raw[golden(path)?.oid])?) };
    for (path, markers) in [
        (
            COMPLETION,
            &[
                "Optional<List<SFMPaletteCandidate>>",
                "return List.of();",
                "return true;",
            ][..],
        ),
        (
            APPLICATION,
            &[
                "SFMPaletteCandidate.Kind",
                "SFMPaletteCandidate.Origin",
                "Objects.requireNonNull(afterValue",
            ][..],
        ),
        (
            FRONTIER,
            &[
                "SFMClientActionExecutor.isExecutable(parsed)",
                "List.copyOf(suggestions)",
                "findSuggestionContext(command.length())",
            ][..],
        ),
        (
            CANDIDATE,
            &[
                "new SFMCompletionApplication(",
                "Usage hints cannot be applied as completions",
                "public static OptionalInt progressingIndex(",
                "historyRecency < NO_HISTORY",
            ][..],
        ),
    ] {
        for marker in markers {
            assert!(
                text(path)?.contains(marker),
                "lost typed model boundary: {path} / {marker}"
            );
        }
    }
    for path in PATHS {
        for forbidden in [
            "java.nio.file.",
            "ProcessBuilder",
            "SFMScreenMultiplexer",
            "SFMClientProgramIdentity",
            "SFMClientActionProgrammaticHandler",
        ] {
            assert!(
                !text(path)?.contains(forbidden),
                "pure helper gained runtime coupling: {path}"
            );
        }
    }
    Ok(())
}
