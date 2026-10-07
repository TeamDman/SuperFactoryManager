//! Four genuine frame/program helpers through authored-core source selection.
//!
//! Frozen blobs are bounded offline test witnesses, never production routing.
//! This tranche proves byte/membership preservation, not Java compilation,
//! lexer generation, network layouts, consent, scheduler or rendering runtime.
//! Core FrameTrigger and these four inputs must first be promoted by the root.
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

const LEDGER: &str = "docs/tasks/sfm-core-frame-helpers-slice.json";

const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const TEN: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const FRAME: [&str; 5] = [
    "client_frame_language",
    "client_manager",
    "client_program_consent",
    "disk_readonly_access",
    "sfml_execution_side",
];
const MANAGER: [&str; 4] = [
    "client_manager",
    "client_program_consent",
    "disk_readonly_access",
    "sfml_execution_side",
];
const ACTION: [&str; 9] = [
    "client_actions",
    "client_manager",
    "client_program_actions",
    "client_program_consent",
    "disk_readonly_access",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
    "sfml_execution_side",
];
const SIGNING: [&str; 11] = [
    "client_actions",
    "client_frame_language",
    "client_manager",
    "client_program_actions",
    "client_program_consent",
    "client_program_signing",
    "disk_readonly_access",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
    "sfml_execution_side",
];
const MULTIPLAYER: [&str; 13] = [
    "client_actions",
    "client_frame_language",
    "client_inbox",
    "client_manager",
    "client_program_actions",
    "client_program_consent",
    "disk_readonly_access",
    "multiplayer_packets",
    "packet_computation",
    "packet_transport_private",
    "packet_values",
    "runtime_resource_cleanup",
    "sfml_execution_side",
];
const INBOX: [&str; 5] = [
    "client_inbox",
    "packet_transport_private",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
];
const CONSUMERS: [&str; 4] = [
    "client_manager",
    "manager_operator_queries",
    "client_program_signing",
    "multiplayer_packets",
];
const DEFINITIONS: [(&str, &[&str], &[&str]); 15] = [
    (
        "client_frame_language",
        &["1.19.2", "1.19.4"],
        &[
            "client_manager",
            "sfml_execution_side",
            "client_program_consent",
        ],
    ),
    (
        "client_manager",
        &["1.19.2", "1.19.4"],
        &[
            "sfml_execution_side",
            "client_program_consent",
            "disk_readonly_access",
        ],
    ),
    (
        "client_program_consent",
        &["1.19.2", "1.19.4"],
        &["sfml_execution_side"],
    ),
    (
        "disk_readonly_access",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    ("sfml_execution_side", &["1.19.2", "1.19.4"], &[]),
    (
        "client_actions",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    ("packet_values", &["1.19.2", "1.19.4"], &[]),
    ("runtime_resource_cleanup", &["1.19.2", "1.19.4"], &[]),
    (
        "packet_computation",
        &["1.19.2", "1.19.4"],
        &["packet_values", "runtime_resource_cleanup"],
    ),
    (
        "client_program_actions",
        &["1.19.2", "1.19.4"],
        &[
            "client_actions",
            "packet_values",
            "sfml_execution_side",
            "client_manager",
            "client_program_consent",
            "packet_computation",
        ],
    ),
    (
        "client_program_signing",
        &["1.19.2", "1.19.4"],
        &["client_frame_language", "client_program_actions"],
    ),
    (
        "packet_transport_private",
        &["1.19.2", "1.19.4"],
        &["packet_computation"],
    ),
    (
        "client_inbox",
        &["1.19.2", "1.19.4"],
        &["packet_transport_private"],
    ),
    (
        "multiplayer_packets",
        &["1.19.2", "1.19.4"],
        &[
            "client_inbox",
            "client_frame_language",
            "client_program_actions",
        ],
    ),
    ("manager_operator_queries", &["1.19.2"], &[]),
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
struct Golden {
    path: &'static str,
    oid: &'static str,
    bytes: usize,
    digest: &'static str,
    lf: usize,
}
const GOLDENS: [Golden; 4] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientFrameWorkBudget.java",
        oid: "4d185af6597f35251588bdec4bc42c56eed95bc7",
        bytes: 792,
        digest: "sha256:6544991f90770fe9b4bfa192eecebb24635cf15f00a7e4d7580708d428ecfc31",
        lf: 28,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientManagerTargetBindings.java",
        oid: "4af390412d42df57b39b0e9061290aba01b22032",
        bytes: 2139,
        digest: "sha256:7c7336e3e0034daadc9d18bd157ade145bcbf7e88c8faa0e2a08f2feb1a409f7",
        lf: 48,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientFrameSourceBudget.java",
        oid: "204cfde7568c3868fcfd270fdc152bacff90237d",
        bytes: 2107,
        digest: "sha256:e2671efcb2dbbbf554d6e5d105f78aefdbdb86865cfe9ba677c57a047ba5b1a2",
        lf: 45,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/blockentity/ClientManagerProgramProjection.java",
        oid: "018e235bb8c1dc88d5f6066d4bc917b691dd4db5",
        bytes: 5247,
        digest: "sha256:407fcb32d212bbb5f1eaf3f24c02a38a794b5f573a9b3e6228de4ad71dd05b81",
        lf: 108,
    },
];
const DEFERRED: [Golden; 3] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientFrameEvaluator.java",
        oid: "badd733c7343f6be8c4a24aeefbd941776b37394",
        bytes: 5144,
        digest: "sha256:7ebc676240953132c3ea169c07d167287ee25f90bd9f595b234e78c4155e4f58",
        lf: 95,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientManagerFrameRuntime.java",
        oid: "3b29d2749cf33b06e8a10bc3ba3f497173841cdf",
        bytes: 23913,
        digest: "sha256:bf88b401ff3eacc0c2dad9504b55912a02da0f374ae34955578f4d002d1f7634",
        lf: 465,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramActionManifest.java",
        oid: "efba229f4e6285bc2973279e09a3706122465686",
        bytes: 8756,
        digest: "sha256:cdbdd3e18171d2a2e02d89bdf2c0f16538e96b8401fb12fb10779bc00898f5d4",
        lf: 134,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    contract_changes: bool,
    definitions: BTreeMap<String, Definition>,
    context_commits: BTreeMap<String, String>,
    files: Vec<SourceEvidence>,
    deferred: Vec<DeferredEvidence>,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct SourceEvidence {
    path: String,
    owner: String,
    any_consumers: Vec<String>,
    raw_oid: String,
    raw_bytes: usize,
    raw_sha256: String,
    authored_bytes: usize,
    authored_sha256: String,
    cr_count: usize,
    lf_count: usize,
    final_lf: bool,
    source_rule: InputVariant,
    witnesses: BTreeMap<String, Option<String>>,
}
#[derive(Facet)]
struct DeferredEvidence {
    path: String,
    raw_oid: String,
    raw_bytes: usize,
    raw_sha256: String,
    witnesses: BTreeMap<String, Option<String>>,
    authored: bool,
}
struct Fixture {
    core: CoreTestFixture,
    sources: BTreeMap<String, Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            128 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-frame-helpers-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && ledger.files.len() == 4
                && ledger.deferred.len() == 3
                && ledger.definitions.len() == 15
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
                        .collect(),
            "frame helper bounded evidence scope changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let original = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing witnessed helper contract: {name}"))?;
            let current = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing actual helper contract: {name}"))?;
            ensure!(
                original.supported_targets == support
                    && original.requires == requires
                    && current.supported_targets == support
                    && current.requires == requires,
                "original/current helper owner contract changed: {name}"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS
                .iter()
                .chain(&DEFERRED)
                .map(|g| g.oid.to_owned())
                .collect(),
        )?;
        let mut sources = BTreeMap::new();
        for (index, (g, evidence)) in GOLDENS.iter().zip(&ledger.files).enumerate() {
            let union = index == 3;
            ensure!(
                evidence.path == g.path
                    && evidence.raw_oid == g.oid
                    && evidence.raw_bytes == g.bytes
                    && evidence.raw_sha256 == g.digest
                    && evidence.authored_bytes == g.bytes
                    && evidence.authored_sha256 == g.digest
                    && evidence.cr_count == 0
                    && evidence.lf_count == g.lf
                    && evidence.final_lf
                    && evidence.owner
                        == if union {
                            "actual_consumer_union"
                        } else {
                            "client_frame_language"
                        }
                    && evidence.any_consumers == if union { CONSUMERS.to_vec() } else { vec![] }
                    && evidence.witnesses == expected_witnesses(g),
                "immutable helper source identity/membership changed"
            );
            verify_raw(&raw[g.oid], g)?;
            let source = core.read_source(g.path)?;
            verify_raw(&source, g)?;
            let rules = core.metadata.source_rules.get(g.path).ok_or_else(|| {
                eyre::eyre!("root must promote helper sparse rule first: {}", g.path)
            })?;
            ensure!(
                rules.len() == 1
                    && rules[0] == evidence.source_rule
                    && rules[0].input == g.path
                    && rules[0].when.targets == D2
                    && rules[0].when.all_features
                        == if union {
                            vec![]
                        } else {
                            vec!["client_frame_language"]
                        }
                    && rules[0].when.any_features
                        == if union { CONSUMERS.to_vec() } else { vec![] }
                    && rules[0].when.none_features.is_empty(),
                "helper membership must retain actual owners and exact D2 support"
            );
            // Every Java input is rendered automatically; template is not authority.
            sources.insert(g.path.to_owned(), source);
        }
        for (g, evidence) in DEFERRED.iter().zip(&ledger.deferred) {
            ensure!(
                evidence.path == g.path
                    && evidence.raw_oid == g.oid
                    && evidence.raw_bytes == g.bytes
                    && evidence.raw_sha256 == g.digest
                    && !evidence.authored
                    && evidence.witnesses == expected_witnesses(g),
                "deferred runtime evidence is not an authored helper"
            );
            verify_raw(&raw[g.oid], g)?;
        }
        let descriptor = core
            .metadata
            .source_rules
            .get(DESCRIPTOR)
            .ok_or_else(|| eyre::eyre!("actual descriptor provider rule missing"))?;
        ensure!(
            descriptor.len() == 1
                && descriptor[0].input == DESCRIPTOR
                && descriptor[0].when.targets == D2
                && descriptor[0].when.all_features.is_empty()
                && descriptor[0].when.any_features == CONSUMERS
                && descriptor[0].when.none_features.is_empty(),
            "projection and real descriptor provider must share evidenced consumer union"
        );
        Ok(Self { core, sources, raw })
    }
    fn inventory(&self) -> BTreeSet<String> {
        self.sources.keys().cloned().collect()
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if !selected.inputs.contains_key(path) {
            ensure!(
                selected.omitted_paths.contains(path),
                "unaccounted helper omission"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.sources[path])?,
            context,
        )?))
    }
    fn provider(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        let selected = select_core_inputs(
            &self.core.metadata,
            context,
            &BTreeSet::from([path.to_owned()]),
        )?;
        let input = selected
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("actual helper provider omitted: {path}"))?;
        render_java_source(
            std::str::from_utf8(&self.core.read_source(&input.input)?)?,
            context,
        )
    }
    fn bounded_metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|path, _| self.sources.contains_key(path));
        metadata
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
            // Real selected standalone project files, never fake service providers.
            let bytes = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let destination = root.join(&input.input);
            if destination.exists() {
                ensure!(
                    read_bounded(&destination, 16 * 1024 * 1024)? == bytes,
                    "standalone fixture input differs between exact contexts"
                );
            } else {
                fs::create_dir_all(
                    destination
                        .parent()
                        .ok_or_else(|| eyre::eyre!("project input lacks a parent"))?,
                )?;
                fs::write(destination, bytes)?;
            }
        }
        Ok(())
    }
    fn write_sources(&self, root: &Path) -> Result<()> {
        for (path, source) in &self.sources {
            let destination = root.join(path);
            fs::create_dir_all(
                destination
                    .parent()
                    .ok_or_else(|| eyre::eyre!("fixed helper source lacks a parent"))?,
            )?;
            fs::write(destination, source)?;
        }
        Ok(())
    }
}
const DESCRIPTOR: &str =
    "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramSignatureDescriptor.java";
fn present(name: &str) -> bool {
    matches!(name, "dev/1.19.2" | "dev/1.19.4")
}
fn expected_witnesses(g: &Golden) -> BTreeMap<String, Option<String>> {
    CONTEXTS
        .into_iter()
        .map(|(name, _)| (name.to_owned(), present(name).then(|| g.oid.to_owned())))
        .collect()
}
fn verify_raw(bytes: &[u8], g: &Golden) -> Result<()> {
    ensure!(
        bytes.len() == g.bytes
            && sha256(bytes) == g.digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && bytes.iter().filter(|byte| **byte == b'\n').count() == g.lf
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw helper bytes changed; no normalization is approved"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn profiles() -> Vec<Vec<&'static str>> {
    vec![
        vec![],
        vec!["client_program_consent", "sfml_execution_side"],
        MANAGER.to_vec(),
        FRAME.to_vec(),
        ACTION.to_vec(),
        SIGNING.to_vec(),
        MULTIPLAYER.to_vec(),
        INBOX.to_vec(),
    ]
}
fn expected_selected(index: usize, context: &ProjectionContext) -> bool {
    if index == 3 {
        CONSUMERS.iter().any(|name| context.features[*name])
    } else {
        context.features["client_frame_language"]
    }
}

#[test]
fn helper_eighty_cells_and_deferred_sixty_cells_match_real_frozen_git_trees() -> Result<()> {
    let f = Fixture::load()?;
    let paths = GOLDENS
        .iter()
        .chain(&DEFERRED)
        .map(|g| format!("platform/minecraft/{}", g.path))
        .collect::<Vec<_>>();
    let mut tree_counts = (0, 0);
    let mut helper_counts = (0, 0);
    for (name, commit) in CONTEXTS {
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
            output.status.success() && output.stdout.len() <= 8192 && output.stderr.len() <= 4096,
            "bounded local frozen helper tree read failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed helper tree record"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|expected| expected == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected helper/deferred tree member"
            );
        }
        let expected = if present(name) {
            GOLDENS
                .iter()
                .chain(&DEFERRED)
                .map(|g| (format!("platform/minecraft/{}", g.path), g.oid.to_owned()))
                .collect()
        } else {
            BTreeMap::new()
        };
        assert_eq!(
            actual, expected,
            "real immutable seven-path membership: {name}"
        );
        tree_counts.0 += actual.len();
        tree_counts.1 += 7 - actual.len();
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("fixed context delimiter missing"))?;
        let context = f
            .core
            .context(target, if present(name) { &FRAME } else { &[] })?;
        for g in &GOLDENS {
            if present(name) {
                let body = f
                    .render(g.path, &context)?
                    .ok_or_else(|| eyre::eyre!("historical helper omitted"))?;
                assert_eq!(body.as_bytes(), f.raw[g.oid], "raw helper body: {}", g.path);
                helper_counts.0 += 1;
            } else {
                assert!(f.render(g.path, &context)?.is_none());
                helper_counts.1 += 1;
            }
        }
    }
    assert_eq!(tree_counts, (14, 126));
    assert_eq!(helper_counts, (8, 72));
    Ok(())
}

#[test]
fn minimal_profiles_preserve_independent_frame_and_four_consumer_or_membership() -> Result<()> {
    let f = Fixture::load()?;
    let mut counts = (0, 0);
    for target in D2 {
        for flags in profiles() {
            let context = f.core.context(target, &flags)?;
            let mut descriptive = context.clone();
            descriptive.environment = "release".to_owned();
            descriptive.preset = "helper-description-not-an-owner".to_owned();
            descriptive.projection_key = "independent/helpers".to_owned();
            for (index, g) in GOLDENS.iter().enumerate() {
                let body = f.render(g.path, &context)?;
                assert_eq!(body.is_some(), expected_selected(index, &context));
                assert_eq!(body, f.render(g.path, &descriptive)?);
                if let Some(body) = body {
                    assert_eq!(body.as_bytes(), f.raw[g.oid]);
                    counts.0 += 1;
                } else {
                    counts.1 += 1;
                }
            }
        }
    }
    let query = f.core.context("1.19.2", &["manager_operator_queries"])?;
    for (index, g) in GOLDENS.iter().enumerate() {
        let body = f.render(g.path, &query)?;
        assert_eq!(body.is_some(), index == 3);
        if body.is_some() {
            counts.0 += 1;
        } else {
            counts.1 += 1;
        }
    }
    assert_eq!(counts, (29, 39));
    for target in D2 {
        let frame = f.core.context(target, &FRAME)?;
        for forbidden in [
            "packet_values",
            "client_program_actions",
            "client_frame_render",
            "client_program_signing",
            "multiplayer_packets",
            "client_inbox",
            "client_manager_gui",
        ] {
            assert!(
                !frame.features[forbidden],
                "minimal helper source forces {forbidden}"
            );
        }
        for g in &GOLDENS {
            assert!(f.render(g.path, &frame)?.is_some());
        }
    }
    Ok(())
}

#[test]
fn exact_prerequisites_unknown_predicates_and_unsupported_targets_are_not_relaxed() -> Result<()> {
    let f = Fixture::load()?;
    let all = SIGNING
        .into_iter()
        .chain(MULTIPLAYER)
        .collect::<BTreeSet<_>>();
    let mut missing = 0;
    for target in D2 {
        for owner in [
            "client_frame_language",
            "client_manager",
            "client_program_signing",
            "multiplayer_packets",
        ] {
            for requirement in &f.core.features.0[owner].requires {
                let flags = all
                    .iter()
                    .copied()
                    .filter(|name| *name != requirement.as_str())
                    .collect::<Vec<_>>();
                assert!(
                    f.core.context(target, &flags).is_err(),
                    "accepted missing {owner} prerequisite {requirement}"
                );
                missing += 1;
            }
        }
        for owner in CONSUMERS.into_iter().chain(["client_frame_language"]) {
            let mut context = f.core.context(target, &FRAME)?;
            assert!(context.features.remove(owner).is_some());
            assert!(select_core_inputs(&f.core.metadata, &context, &f.inventory()).is_err());
        }
    }
    assert_eq!(missing, 22);
    assert!(
        f.core
            .context("1.19.4", &["manager_operator_queries"])
            .is_err()
    );
    for target in &TEN[2..] {
        for flags in [
            FRAME.as_slice(),
            MANAGER.as_slice(),
            SIGNING.as_slice(),
            MULTIPLAYER.as_slice(),
            &["manager_operator_queries"],
        ] {
            assert!(f.core.context(target, flags).is_err());
        }
        let off = f.core.context(target, &[])?;
        for g in &GOLDENS {
            assert!(f.render(g.path, &off)?.is_none());
        }
    }
    Ok(())
}

#[test]
fn real_core_provider_signatures_and_grammar_tokens_supply_only_source_contracts() -> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        let context = f.core.context(target, &FRAME)?;
        for (path, anchors) in [
            (
                "src/main/java/ca/teamdman/sfml/ast/FrameTrigger.java",
                &[
                    "public record FrameTrigger(List<Label> labels",
                    "MAX_NESTING = 32",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfml/ast/Program.java",
                &[
                    "public record Program(",
                    "MAX_PROGRAM_LENGTH",
                    "MAX_LABEL_LENGTH",
                    "List<Trigger> triggers",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/label/LabelPositionHolder.java",
                &["public BlockPosSet getPositions(String label)"][..],
            ),
            (
                DESCRIPTOR,
                &[
                    "public record ProgramSignatureDescriptor(",
                    "public static byte[] normalizedSourceBytes(String source)",
                ][..],
            ),
        ] {
            let body = f.provider(path, &context)?;
            for anchor in anchors {
                assert!(body.contains(anchor), "real provider {path}: {anchor}");
            }
        }
        // The lexer is generated from this actual selected grammar, never stubbed.
        let grammar_path = "src/main/antlr/sfml/SFML.g4";
        let selected = select_core_inputs(
            &f.core.metadata,
            &context,
            &BTreeSet::from([grammar_path.to_owned()]),
        )?;
        let input = selected
            .inputs
            .get(grammar_path)
            .ok_or_else(|| eyre::eyre!("actual lexer grammar omitted"))?;
        assert!(
            input.template,
            "non-Java grammar must be explicitly rendered"
        );
        let raw = read_bounded(&checked_file(&f.core.core, &input.input)?, 1024 * 1024)?;
        let grammar = render_java_source(std::str::from_utf8(&raw)?, &context)?;
        for token in [
            "IF", "EVERY", "END", "LPAREN", "RPAREN", "NOT", "AND", "OR", "THEN", "DO",
        ] {
            assert!(
                grammar.lines().any(|line| line
                    .split_once(':')
                    .is_some_and(|(left, _)| left.trim() == token)),
                "real lexer token absent: {token}"
            );
        }
    }
    Ok(())
}

#[test]
fn raw_mutations_fail_and_bounded_projection_lexical_and_work_contracts_are_retained() -> Result<()>
{
    let f = Fixture::load()?;
    for g in &GOLDENS {
        let raw = &f.raw[g.oid];
        for mutated in [
            std::str::from_utf8(raw)?.replace('\n', "\r\n").into_bytes(),
            raw[..raw.len() - 1].to_vec(),
            [raw.as_slice(), b"\n"].concat(),
            [b" ".as_slice(), raw.as_slice()].concat(),
            [b"\xef\xbb\xbf".as_slice(), raw.as_slice()].concat(),
        ] {
            assert!(verify_raw(&mutated, g).is_err());
        }
    }
    let anchors: [&[&str]; 4] = [
        &[
            "maximum < 1",
            "epoch != actualRenderEpoch",
            "used >= maximum",
            "used++",
            "epoch = Long.MIN_VALUE;",
            "used = 0;",
        ],
        &[
            "MAX_LABELS = 32",
            "MAX_POSITIONS = 64",
            "Set<String> names = new TreeSet<>()",
            ".filter(FrameTrigger.class::isInstance)",
            ".longStream().sorted().toArray()",
            "sorted.length == 0",
            "Too many Client Manager label positions",
            "Long.toHexString(value)",
            "labels.getPositions(name).contains(display)",
        ],
        &[
            "MAX_TOKENS = 4096",
            "Program.MAX_PROGRAM_LENGTH",
            "ClientManagerProgramProjection.MAX_SOURCE_BYTES",
            "lexer.removeErrorListeners()",
            "token.getChannel() != Token.DEFAULT_CHANNEL",
            "++count > MAX_TOKENS",
            "case SFMLLexer.IF, SFMLLexer.EVERY",
            "case SFMLLexer.END",
            "case SFMLLexer.LPAREN",
            "case SFMLLexer.RPAREN",
            "case SFMLLexer.NOT, SFMLLexer.AND, SFMLLexer.OR",
            "case SFMLLexer.THEN, SFMLLexer.DO",
            "FrameTrigger.MAX_NESTING",
        ],
        &[
            "MAX_SOURCE_BYTES = 64 * 1024",
            "MAX_LABELS = 32",
            "MAX_POSITIONS = 64",
            "MAX_COMPRESSED_BYTES = 4096",
            "ProgramSignatureDescriptor.normalizedSourceBytes",
            "raw.contains(\"sfm:labels\", Tag.TAG_COMPOUND)",
            "labels.size() > MAX_LABELS",
            "remaining -= positions.size()",
            "private static ListTag boundedPositions(",
            "require(list.size() <= remaining)",
            "element instanceof LongTag",
            "element instanceof CompoundTag",
            "raw instanceof ByteArrayTag",
            "require(encoded.length <= MAX_COMPRESSED_BYTES)",
            "require(volumes >= 0 && volumes <= remaining)",
            "directionOrdinal >= 0 && directionOrdinal < Direction.values().length",
            "require(extension >= 0 && extension < remaining - result.size())",
            "while (input.isReadable()) require(input.readByte() == 0)",
            "} finally {",
            "input.release();",
        ],
    ];
    for (g, anchors) in GOLDENS.iter().zip(anchors) {
        let body = std::str::from_utf8(&f.sources[g.path])?;
        for anchor in anchors {
            assert!(body.contains(anchor), "preserved helper anchor: {anchor}");
        }
        for absent in [
            "Minecraft.getInstance()",
            "ClientFrameEvaluator",
            "ClientManagerFrameRuntime",
            "SFMClientProgramActionDispatcher",
            "SFMPackets",
            "Files.",
            "ProcessBuilder",
        ] {
            assert!(!body.contains(absent));
        }
    }
    Ok(())
}

#[test]
fn omitted_helpers_are_not_read_even_when_bytes_or_directives_are_invalid() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let metadata = f.bounded_metadata();
    for g in &GOLDENS {
        let destination = root.join(g.path);
        fs::create_dir_all(destination.parent().expect("fixed helper parent"))?;
        fs::write(destination, b"{% if features.unreviewed_owner %}\n\xff")?;
    }
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    for target in D2 {
        for flags in [
            vec![],
            vec!["client_program_consent", "sfml_execution_side"],
        ] {
            let context = f.core.context(target, &flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for g in &GOLDENS {
                assert!(selected.omitted_paths.contains(g.path));
                assert!(!artifacts.contains_key(g.path));
            }
        }
    }
    fs::write(root.join(GOLDENS[3].path), &f.sources[GOLDENS[3].path])?;
    for target in D2 {
        let context = f.core.context(target, &MANAGER)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (index, g) in GOLDENS.iter().enumerate() {
            assert_eq!(artifacts.contains_key(g.path), index == 3);
        }
    }
    // No SFMPackets was included in this bounded source fixture; this is not
    // complete-project network-family or enabled-runtime acceptance.
    Ok(())
}

#[test]
fn java_automatically_renders_without_template_bit_in_a_fixed_core_fixture() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let original = std::str::from_utf8(&f.sources[GOLDENS[0].path])?;
    let fixture_source = format!(
        "{original}{{% if features.client_frame_language %}}\n// Isolated Java rendering probe.\n{{% endif %}}\n"
    );
    fs::write(root.join(GOLDENS[0].path), &fixture_source)?;
    let mut metadata = f.bounded_metadata();
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let inventory = discover_core_source_files(&root)?;
    for target in D2 {
        let context = f.core.context(target, &FRAME)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for g in &GOLDENS {
            let artifact = &artifacts[g.path];
            let output = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = output
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("generated Java banner missing"))?;
            if g.path == GOLDENS[0].path {
                assert_eq!(body, render_java_source(&fixture_source, &context)?);
                assert!(body.contains("// Isolated Java rendering probe."));
                assert!(!body.contains("{%"));
            } else {
                assert_eq!(body.as_bytes(), f.raw[g.oid]);
            }
            assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{}", g.path));
            assert!(artifact.overlay.is_none());
        }
    }
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}

#[test]
fn common_authored_helper_edits_reach_both_targets_without_live_mutation() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    for g in &GOLDENS {
        let original = std::str::from_utf8(&f.sources[g.path])?;
        let edited = original.replacen(
            "public final class ",
            "// Isolated common helper edit.\npublic final class ",
            1,
        );
        assert_ne!(edited, original);
        fs::write(root.join(g.path), edited)?;
    }
    let inventory = discover_core_source_files(&root)?;
    let metadata = f.bounded_metadata();
    let mut outputs = 0;
    for target in D2 {
        let context = f.core.context(target, &FRAME)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for g in &GOLDENS {
            let output = std::str::from_utf8(&artifacts[g.path].output_bytes)?;
            let (_, body) = output
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("generated edited Java banner missing"))?;
            let expected = std::str::from_utf8(&f.sources[g.path])?.replacen(
                "public final class ",
                "// Isolated common helper edit.\npublic final class ",
                1,
            );
            assert_eq!(body, render_java_source(&expected, &context)?);
            assert!(body.contains("// Isolated common helper edit."));
            outputs += 1;
        }
    }
    assert_eq!(outputs, 8);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}
