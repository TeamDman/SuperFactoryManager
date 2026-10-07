//! Four frame/side AST carriers through actual authored-core membership and rendering.
//!
//! Frozen Git blobs are bounded offline test witnesses, never production sources.
//! These are source-preservation and counterfactual-owner proofs, not Java,
//! frame evaluation, rendering, consent, transport, or runtime acceptance.
#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
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

const LEDGER: &str = "docs/tasks/sfm-core-frame-ast-slice.json";
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
const RENDER: [&str; 9] = [
    "client_frame_language",
    "client_frame_render",
    "client_manager",
    "client_program_consent",
    "disk_readonly_access",
    "image_resources",
    "packet_values",
    "sfml_execution_side",
    "touch_display",
];
const FULL: [&str; 13] = [
    "client_actions",
    "client_frame_language",
    "client_frame_render",
    "client_manager",
    "client_program_actions",
    "client_program_consent",
    "disk_readonly_access",
    "image_resources",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
    "sfml_execution_side",
    "touch_display",
];
const DEFINITIONS: [(&str, &[&str], &[&str]); 13] = [
    ("sfml_execution_side", &D2, &[]),
    (
        "client_frame_language",
        &D2,
        &[
            "client_manager",
            "sfml_execution_side",
            "client_program_consent",
        ],
    ),
    (
        "client_frame_render",
        &D2,
        &["client_frame_language", "touch_display", "image_resources"],
    ),
    (
        "client_program_actions",
        &D2,
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
        "client_manager",
        &D2,
        &[
            "sfml_execution_side",
            "client_program_consent",
            "disk_readonly_access",
        ],
    ),
    ("client_program_consent", &D2, &["sfml_execution_side"]),
    ("disk_readonly_access", &TEN, &[]),
    ("client_actions", &TEN, &[]),
    (
        "packet_computation",
        &D2,
        &["packet_values", "runtime_resource_cleanup"],
    ),
    ("packet_values", &D2, &[]),
    ("runtime_resource_cleanup", &D2, &[]),
    ("touch_display", &D2, &["packet_values", "image_resources"]),
    ("image_resources", &D2, &["packet_values"]),
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
    owner: &'static str,
    oid: &'static str,
    raw_bytes: usize,
    raw_sha256: &'static str,
    authored_bytes: usize,
    authored_sha256: &'static str,
}
const GOLDENS: [Golden; 4] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfml/ast/ProgramExecutionSideDeclaration.java",
        owner: "sfml_execution_side",
        oid: "b733b30a79a386d4343a2b324499d0751489c31e",
        raw_bytes: 422,
        raw_sha256: "sha256:1019e3c80a5d097b78cbfd4b3a683e16a5364df60716ad7b6aaa4263640b659e",
        authored_bytes: 422,
        authored_sha256: "sha256:1019e3c80a5d097b78cbfd4b3a683e16a5364df60716ad7b6aaa4263640b659e",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfml/ast/FrameTrigger.java",
        owner: "client_frame_language",
        oid: "90e72a89368adfbb422a54e5f8b8b72535edb4da",
        raw_bytes: 4858,
        raw_sha256: "sha256:6000995d53b3bcba2b1fa3ddc4a841f47ad8b2b31566dc3880ccc6d875af77e7",
        authored_bytes: 5504,
        authored_sha256: "sha256:48febdc9aef80af99647f34290065961472758efe989c1a2c058e19eaeeedab2",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfml/ast/BoolFrameModulo.java",
        owner: "client_frame_language",
        oid: "c407c73930f8587c9664f70195255748db32a079",
        raw_bytes: 1001,
        raw_sha256: "sha256:1951827684cc487ca668fd2f62a651a823d4780f36b344033a6885155a7e2841",
        authored_bytes: 1001,
        authored_sha256: "sha256:1951827684cc487ca668fd2f62a651a823d4780f36b344033a6885155a7e2841",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfml/ast/RenderImageStatement.java",
        owner: "client_frame_render",
        oid: "ea9065df88dab5f8bfbf1641b75eb14b4c25be14",
        raw_bytes: 765,
        raw_sha256: "sha256:06d4a20ddd9e9281086a4aecdbc6b82c67b8540d3dcf0ab7cb1d3c3de940a15d",
        authored_bytes: 765,
        authored_sha256: "sha256:06d4a20ddd9e9281086a4aecdbc6b82c67b8540d3dcf0ab7cb1d3c3de940a15d",
    },
];
const FRAME_BODIES: [(bool, bool, usize, &str); 4] = [
    (
        false,
        false,
        4211,
        "sha256:409d00282c49fba94ce43ec486410aaf1e06d60cee938497fa9ba564209d2381",
    ),
    (
        false,
        true,
        4588,
        "sha256:09be0c44753f54f0f5eef1481dfad4046c6fe9b486d46d9e01d5f9c380f2d8c1",
    ),
    (
        true,
        false,
        4481,
        "sha256:9f257bf77fdf4aa28cc3bce86d99fde6e3f44f73afb150571a4be5f5b2430dab",
    ),
    (
        true,
        true,
        4858,
        "sha256:6000995d53b3bcba2b1fa3ddc4a841f47ad8b2b31566dc3880ccc6d875af77e7",
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    definitions: BTreeMap<String, Definition>,
    context_commits: BTreeMap<String, String>,
    files: Vec<SourceEvidence>,
    contract_changes: bool,
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
    raw_oid: String,
    raw_bytes: usize,
    raw_sha256: String,
    authored_bytes: usize,
    authored_sha256: String,
    witnesses: BTreeMap<String, Option<String>>,
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
            ledger.schema == "sfm:core-frame-ast-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
                        .collect()
                && ledger.files.len() == 4
                && ledger.definitions.len() == 13,
            "bounded frame AST evidence scope changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let witnessed = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("frozen definition missing: {name}"))?;
            let current = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("actual definition missing: {name}"))?;
            ensure!(
                witnessed.supported_targets == support
                    && witnessed.requires == requires
                    && current.supported_targets == support
                    && current.requires == requires,
                "frame preservation cannot widen current or original contracts: {name}"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|g| g.oid.to_owned()).collect(),
        )?;
        let mut sources = BTreeMap::new();
        for (g, evidence) in GOLDENS.iter().zip(&ledger.files) {
            ensure!(
                evidence.path == g.path
                    && evidence.owner == g.owner
                    && evidence.raw_oid == g.oid
                    && evidence.raw_bytes == g.raw_bytes
                    && evidence.raw_sha256 == g.raw_sha256
                    && evidence.authored_bytes == g.authored_bytes
                    && evidence.authored_sha256 == g.authored_sha256
                    && evidence.witnesses
                        == CONTEXTS
                            .into_iter()
                            .map(|(name, _)| (
                                name.to_owned(),
                                present(name).then(|| g.oid.to_owned())
                            ))
                            .collect(),
                "frame source/witness identity changed"
            );
            verify_raw(&raw[g.oid], g)?;
            let source = core.read_source(g.path)?;
            ensure!(
                source.len() == g.authored_bytes
                    && sha256(&source) == g.authored_sha256
                    && !source.contains(&b'\r')
                    && source.ends_with(b"\n"),
                "promoted authored frame source differs from reviewed stage"
            );
            let rules = core
                .metadata
                .source_rules
                .get(g.path)
                .ok_or_else(|| eyre::eyre!("frame sparse rule must be promoted first"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == g.path
                    && rules[0].when.targets == D2
                    && rules[0].when.all_features == [g.owner]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "frame class membership must follow its actual independent owner"
            );
            // Every .java is automatically rendered; template is not an authority bit.
            sources.insert(g.path.to_owned(), source);
        }
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
                "unaccounted omitted frame member"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.sources[path])?,
            context,
        )?))
    }
    fn render_other(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        let selected = select_core_inputs(
            &self.core.metadata,
            context,
            &BTreeSet::from([path.to_owned()]),
        )?;
        let input = selected
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("real frame provider omitted: {path}"))?;
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
            // Copy only the actual selected standalone project inputs, not build stubs.
            let bytes = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let destination = root.join(&input.input);
            if destination.exists() {
                ensure!(
                    read_bounded(&destination, 16 * 1024 * 1024)? == bytes,
                    "selected project input differs across isolated target fixtures"
                );
            } else {
                fs::create_dir_all(
                    destination
                        .parent()
                        .ok_or_else(|| eyre::eyre!("fixed project input lacks a parent"))?,
                )?;
                fs::write(destination, bytes)?;
            }
        }
        Ok(())
    }
}
fn present(name: &str) -> bool {
    matches!(name, "dev/1.19.2" | "dev/1.19.4")
}
fn verify_raw(bytes: &[u8], g: &Golden) -> Result<()> {
    ensure!(
        bytes.len() == g.raw_bytes
            && sha256(bytes) == g.raw_sha256
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw frame witness changed; no normalization is approved"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn profiles() -> Vec<Vec<&'static str>> {
    let mut frame_packet = FRAME.to_vec();
    frame_packet.extend([
        "packet_computation",
        "packet_values",
        "runtime_resource_cleanup",
    ]);
    let mut frame_action = ACTION.to_vec();
    frame_action.push("client_frame_language");
    vec![
        vec![],
        vec!["sfml_execution_side"],
        FRAME.to_vec(),
        frame_packet,
        ACTION.to_vec(),
        frame_action,
        RENDER.to_vec(),
        FULL.to_vec(),
    ]
}

#[test]
fn four_frame_ast_members_reconstruct_eighty_actual_frozen_tree_cells() -> Result<()> {
    let f = Fixture::load()?;
    let paths = GOLDENS
        .iter()
        .map(|g| format!("platform/minecraft/{}", g.path))
        .collect::<Vec<_>>();
    let mut counts = (0, 0);
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
            output.status.success() && output.stdout.len() <= 4096 && output.stderr.len() <= 4096,
            "bounded frozen frame tree query failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("bad frame tree record"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|expected| expected == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected frame tree member"
            );
        }
        let expected = if present(name) {
            GOLDENS
                .iter()
                .map(|g| (format!("platform/minecraft/{}", g.path), g.oid.to_owned()))
                .collect()
        } else {
            BTreeMap::new()
        };
        assert_eq!(actual, expected, "frozen frame membership changed: {name}");
        let (_, target) = name.split_once('/').expect("fixed context delimiter");
        let context = f
            .core
            .context(target, if present(name) { &FULL } else { &[] })?;
        for g in &GOLDENS {
            if present(name) {
                let body = f
                    .render(g.path, &context)?
                    .ok_or_else(|| eyre::eyre!("historical frame member omitted"))?;
                assert_eq!(
                    body.as_bytes(),
                    f.raw[g.oid],
                    "full raw frame output: {}",
                    g.path
                );
                counts.0 += 1;
            } else {
                assert!(f.render(g.path, &context)?.is_none());
                counts.1 += 1;
            }
        }
    }
    assert_eq!(counts, (8, 72));
    Ok(())
}

#[test]
fn valid_independent_owner_profiles_select_members_not_catalog_labels() -> Result<()> {
    let f = Fixture::load()?;
    let mut counts = (0, 0);
    for target in D2 {
        for flags in profiles() {
            let context = f.core.context(target, &flags)?;
            let mut renamed = context.clone();
            renamed.environment = "release".to_owned();
            renamed.preset = "descriptive-only-frame-profile".to_owned();
            renamed.projection_key = "independent/frame/owner-profile".to_owned();
            for g in &GOLDENS {
                let body = f.render(g.path, &context)?;
                assert_eq!(body.is_some(), context.features[g.owner]);
                assert_eq!(body, f.render(g.path, &renamed)?);
                if body.is_some() {
                    counts.0 += 1;
                } else {
                    counts.1 += 1;
                }
            }
        }
    }
    assert_eq!(counts, (38, 26));
    Ok(())
}

#[test]
fn frame_validator_has_four_real_optional_owner_bodies_and_no_packet_only_let_reference()
-> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        for (render, actions, bytes, digest) in FRAME_BODIES {
            let flags = match (render, actions) {
                (false, false) => FRAME.to_vec(),
                (false, true) => {
                    let mut flags = ACTION.to_vec();
                    flags.push("client_frame_language");
                    flags
                }
                (true, false) => RENDER.to_vec(),
                (true, true) => FULL.to_vec(),
            };
            let context = f.core.context(target, &flags)?;
            let body = f
                .render(GOLDENS[1].path, &context)?
                .ok_or_else(|| eyre::eyre!("frame validator omitted"))?;
            assert_eq!(
                (body.len(), sha256(body.as_bytes())),
                (bytes, digest.to_owned())
            );
            assert_eq!(body.contains("RenderImageStatement"), render);
            assert_eq!(body.contains("LetStatement"), actions);
            assert_eq!(body.contains("ClientValueExpression"), actions);
            assert_eq!(body.contains("BoolClientValueEquals"), actions);
            for anchor in [
                "private static void validateBlock(",
                "private static void validateBoolean(",
                "private static void checkBudget(",
                "MAX_BODY_NODES = 512",
                "MAX_NESTING = 32",
                "Client frame body cannot execute server statement:",
                "Client frame condition cannot read server state:",
            ] {
                assert!(body.contains(anchor), "frame counterfactual lost {anchor}");
            }
            if render {
                assert!(body.contains("RENDER target must be the frame binding "));
            }
            if actions {
                assert!(context.features["packet_computation"]);
                assert!(body.contains("A value binding cannot replace the frame display binding"));
            } else {
                assert!(!context.features["client_program_actions"]);
            }
            assert!(!body.contains("{%"));
        }
        let mut packet_only = FRAME.to_vec();
        packet_only.extend([
            "packet_computation",
            "packet_values",
            "runtime_resource_cleanup",
        ]);
        let body = f
            .render(GOLDENS[1].path, &f.core.context(target, &packet_only)?)?
            .ok_or_else(|| eyre::eyre!("packet-only frame omitted"))?;
        assert_eq!(sha256(body.as_bytes()), FRAME_BODIES[0].3);
        for absent in [
            "LetStatement",
            "ClientValueExpression",
            "BoolClientValueEquals",
            "RenderImageStatement",
        ] {
            assert!(!body.contains(absent));
        }
    }
    Ok(())
}

#[test]
fn missing_frame_action_render_prerequisites_and_unsupported_targets_fail_closed() -> Result<()> {
    let f = Fixture::load()?;
    let mut missing = 0;
    for target in D2 {
        for owner in [
            "client_frame_language",
            "client_frame_render",
            "client_program_actions",
        ] {
            for required in &f.core.features.0[owner].requires {
                let flags = FULL
                    .into_iter()
                    .filter(|name| *name != required.as_str())
                    .collect::<Vec<_>>();
                assert!(
                    f.core.context(target, &flags).is_err(),
                    "silently accepted missing {owner} prerequisite {required}"
                );
                missing += 1;
            }
        }
        for owner in [
            "sfml_execution_side",
            "client_frame_language",
            "client_frame_render",
        ] {
            let mut context = f.core.context(target, &FULL)?;
            assert_eq!(context.features.remove(owner), Some(true));
            assert!(select_core_inputs(&f.core.metadata, &context, &f.inventory()).is_err());
        }
    }
    assert_eq!(missing, 24);
    for target in &TEN[2..] {
        for flags in [
            &["sfml_execution_side"][..],
            &FRAME[..],
            &RENDER[..],
            &FULL[..],
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
fn real_ast_provider_and_parser_contracts_follow_narrow_side_frame_action_render_owners()
-> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        for flags in profiles() {
            let context = f.core.context(target, &flags)?;
            let frame = context.features["client_frame_language"];
            let render = context.features["client_frame_render"];
            let actions = context.features["client_program_actions"];
            let side = context.features["sfml_execution_side"];
            for path in [
                "src/main/java/ca/teamdman/sfml/ast/ASTBuilder.java",
                "src/main/java/ca/teamdman/sfml/ast/Program.java",
            ] {
                let body = f.render_other(path, &context)?;
                assert_eq!(body.contains("ProgramExecutionSideDeclaration"), side);
                assert_eq!(body.contains("FrameTrigger"), frame);
                assert_eq!(body.contains("BoolFrameModulo"), frame);
                assert_eq!(body.contains("RenderImageStatement"), render);
                assert_eq!(body.contains("BoolClientValueEquals"), actions);
            }
            if frame {
                for (path, anchor) in [
                    (
                        "src/main/java/ca/teamdman/sfm/common/program/ProgramContext.java",
                        "public class ProgramContext",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/Trigger.java",
                        "public interface Trigger extends Statement",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/Statement.java",
                        "void tick(ProgramContext context)",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/Label.java",
                        "public record Label(String name)",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/Block.java",
                        "public record Block(List<Statement> statements)",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/BoolExpr.java",
                        "public interface BoolExpr",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/IfStatement.java",
                        "public record IfStatement(",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/BoolParen.java",
                        "record BoolParen(BoolExpr inner)",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/BoolNegation.java",
                        "record BoolNegation(BoolExpr inner)",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/BoolConjunction.java",
                        "record BoolConjunction(BoolExpr left, BoolExpr right)",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/BoolDisjunction.java",
                        "record BoolDisjunction(BoolExpr left, BoolExpr right)",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/ComparisonOperator.java",
                        "implements ASTNode, BiPredicate<Long, Long>",
                    ),
                ] {
                    assert!(
                        f.render_other(path, &context)?.contains(anchor),
                        "real provider {path}"
                    );
                }
            }
            if actions {
                for (path, anchor) in [
                    (
                        "src/main/java/ca/teamdman/sfml/ast/LetStatement.java",
                        "public record LetStatement(",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/ClientValueExpression.java",
                        "interface ClientValueExpression",
                    ),
                    (
                        "src/main/java/ca/teamdman/sfml/ast/BoolClientValueEquals.java",
                        "record BoolClientValueEquals(",
                    ),
                ] {
                    assert!(f.render_other(path, &context)?.contains(anchor));
                }
            }
            if side {
                assert!(
                    f.render_other(
                        "src/main/java/ca/teamdman/sfml/ast/ProgramExecutionSide.java",
                        &context
                    )?
                    .contains("public enum ProgramExecutionSide")
                );
            }
        }
    }
    Ok(())
}

#[test]
fn raw_frame_source_refuses_mutations_and_keeps_server_refusal_and_budget_contracts() -> Result<()>
{
    let f = Fixture::load()?;
    for g in &GOLDENS {
        let raw = &f.raw[g.oid];
        for mutated in [
            std::str::from_utf8(raw)?.replace('\n', "\r\n").into_bytes(),
            raw[..raw.len() - 1].to_vec(),
            [raw.as_slice(), b"\n"].concat(),
            [b" ".as_slice(), raw.as_slice()].concat(),
        ] {
            assert!(verify_raw(&mutated, g).is_err());
        }
    }
    let side = std::str::from_utf8(&f.sources[GOLDENS[0].path])?;
    assert!(side.contains("it never chooses or changes the actual execution host"));
    assert!(side.contains("Objects.requireNonNull(side, \"side\")"));
    let frame = std::str::from_utf8(&f.raw[GOLDENS[1].oid])?;
    assert_eq!(
        frame
            .matches("A frame trigger cannot run in the server ProgramContext")
            .count(),
        2
    );
    let modulo = std::str::from_utf8(&f.sources[GOLDENS[2].path])?;
    assert!(modulo.contains("divisor <= 0"));
    assert!(modulo.contains("frameIndex < 0"));
    assert!(modulo.contains("comparison.test(frameIndex % divisor, value)"));
    assert!(modulo.contains("FRAME MOD cannot read server ProgramContext"));
    let render = std::str::from_utf8(&f.sources[GOLDENS[3].path])?;
    assert!(render.contains("RENDER IMAGE cannot run in the server ProgramContext"));
    for bytes in f.sources.values() {
        let body = std::str::from_utf8(bytes)?;
        for absent in [
            "Minecraft.getInstance()",
            "ClientFrameEvaluator",
            "ClientManagerFrameRuntime",
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
fn collector_omits_owner_off_bytes_before_read_and_java_never_needs_template_permission()
-> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    for g in &GOLDENS {
        let destination = root.join(g.path);
        fs::create_dir_all(destination.parent().expect("fixed frame source parent"))?;
        // Invalid UTF-8 + invalid directive would fail if an omitted source were read/rendered.
        fs::write(destination, b"{% if features.unreviewed_owner %}\n\xff")?;
    }
    let mut metadata = f.bounded_metadata();
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    for target in D2 {
        let off = f.core.context(target, &[])?;
        f.copy_project_inputs(&root, &metadata, &off)?;
        let selected = select_core_inputs(&metadata, &off, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &off)?;
        for g in &GOLDENS {
            assert!(selected.omitted_paths.contains(g.path));
            assert!(!artifacts.contains_key(g.path));
        }
    }
    for (path, source) in &f.sources {
        fs::write(root.join(path), source)?;
    }
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    for target in D2 {
        let context = f.core.context(target, &FRAME)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        let artifact = &artifacts[GOLDENS[1].path];
        let output = std::str::from_utf8(&artifact.output_bytes)?;
        let (_, body) = output
            .split_once('\n')
            .ok_or_else(|| eyre::eyre!("generated Java banner missing"))?;
        assert_eq!(sha256(body.as_bytes()), FRAME_BODIES[0].3);
        assert!(!body.contains("{%"));
        assert_eq!(
            artifact.source_path,
            format!("{CORE_ROOT}/{}", GOLDENS[1].path)
        );
        assert!(artifact.overlay.is_none());
    }
    Ok(())
}

#[test]
fn one_shared_frame_edit_collects_for_two_targets_and_four_masks_without_live_writes() -> Result<()>
{
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let marker = "// One isolated shared frame validator edit.\n";
    for (path, source) in &f.sources {
        let destination = root.join(path);
        fs::create_dir_all(destination.parent().expect("fixed source parent"))?;
        fs::write(destination, source)?;
    }
    let original = std::str::from_utf8(&f.sources[GOLDENS[1].path])?;
    let edited = original.replacen(
        "    private static void checkBudget(",
        &format!("{marker}    private static void checkBudget("),
        1,
    );
    assert_ne!(edited, original);
    fs::write(root.join(GOLDENS[1].path), &edited)?;
    let inventory = discover_core_source_files(&root)?;
    let metadata = f.bounded_metadata();
    let mut outputs = 0;
    for target in D2 {
        for flags in [
            FRAME.to_vec(),
            {
                let mut flags = ACTION.to_vec();
                flags.push("client_frame_language");
                flags
            },
            RENDER.to_vec(),
            FULL.to_vec(),
        ] {
            let context = f.core.context(target, &flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            let output = std::str::from_utf8(&artifacts[GOLDENS[1].path].output_bytes)?;
            let (_, body) = output
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("generated edited Java banner missing"))?;
            assert_eq!(body, render_java_source(&edited, &context)?);
            assert!(body.contains(marker));
            assert_eq!(
                body.contains("RenderImageStatement"),
                context.features["client_frame_render"]
            );
            assert_eq!(
                body.contains("LetStatement"),
                context.features["client_program_actions"]
            );
            outputs += 1;
        }
    }
    assert_eq!(outputs, 8);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}
