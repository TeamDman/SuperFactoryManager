//! Test-only frozen mixed terminal carrier source proofs.
//!
//! Requires root promotion and explicit feature/source-rule registration.
//! Historical Git objects remain bounded test goldens, never production inputs.
//! These tests deliberately do not establish missing workspace-model or backend
//! API closure. Future core edits require explicit review of frozen evidence.
#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
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
use std::io::Read;
use std::process::Stdio;

const LEDGER: &str = "docs/tasks/sfm-core-terminal-carriers-slice.json";
const LEDGER_SHA: &str = "sha256:dc907554e27c8b56f5710e8f26681ed47323136c6f54f666271646cbf4798d7a";
const LIMIT: u64 = 256 * 1024;
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
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
// Frozen raw identity, SHA-256 and exact byte count, all LF/final LF.
const RAW: [(&str, &str, u64); 9] = [
    (
        "90643a57bc19ca8f51c51dbf3739771215a3a62b",
        "sha256:d2079eee8ba81c3a1f952d07d5bccb2a202d59ece38c3f522d848c23a06e2be9",
        1619,
    ),
    (
        "7ca1810b7695fdd979a6fdb8d69d16977bff164e",
        "sha256:43e5a8cf69e5f1136b7e5e4e47f3008b9e1b7c3d716ffad6f8370e05abe0144c",
        477,
    ),
    (
        "b06b7d5ce12bbe727592828f45dfcacac8065b74",
        "sha256:a51df2701f889d9443d30bfef5994a2843573d0bdff22586babf3ad329709bf1",
        1497,
    ),
    (
        "c2ab4b68ab201b35293ce56d42699e8c0858c62f",
        "sha256:07c96fa60830f787ef91575a2b02c193d077c907a1f1ecd44549429039672a45",
        1800,
    ),
    (
        "e3477bfab28b7e72edd1989659577089a1d4aa84",
        "sha256:d21516d58e86e66c0f2cfbb943007a7f34abd33a615eb05918f60a4f1f940dea",
        1786,
    ),
    (
        "2301ef0a93cc25b4cb6bf0b074e7fb5b2a427b63",
        "sha256:44a010b47095af6a3e73beec2c87d12a9779141e4d4d084561371480a142c520",
        4228,
    ),
    (
        "2d4b9c09ab326c452ecf06903c22374c7fccd235",
        "sha256:4002ae9566c30d568b664d53a5553bdc6ae8222c6e1d658462e6bb4d2cb91e31",
        1029,
    ),
    (
        "4730fd8311553067d57b79cc6985d0d8dec9a1ce",
        "sha256:207bf89fd772530f32aafbec2229b9be68bece9668618d47732537d5af4bdf7f",
        11566,
    ),
    (
        "51f407dae52d72f4bca0c39371a2eded66d19b9f",
        "sha256:b9fd3fdd2471349e9727e4daa41be81f3be6bf0d4bc654b92a43ac8fe4b7a39c",
        3076,
    ),
];
// Class, staged shared-template hash/count, D2 raw, optional later8 raw, all10 membership.
const FILES: [(&str, &str, u64, &str, &str, bool); 7] = [
    (
        "SFMTerminalFrame",
        "sha256:008c1989377411be60c3f5e65f7c0fb46a3d73591169d5c93fc52f6fe7cf01d1",
        1937,
        "90643a57bc19ca8f51c51dbf3739771215a3a62b",
        "7ca1810b7695fdd979a6fdb8d69d16977bff164e",
        true,
    ),
    (
        "SFMTerminalFrameMetadata",
        "sha256:a51df2701f889d9443d30bfef5994a2843573d0bdff22586babf3ad329709bf1",
        1497,
        "b06b7d5ce12bbe727592828f45dfcacac8065b74",
        "",
        false,
    ),
    (
        "SFMTerminalFocusSequence",
        "sha256:a6ec3b881d595dd494ceb53f6abdd0efbd166a0986968b6193c708c52c6a4194",
        1972,
        "c2ab4b68ab201b35293ce56d42699e8c0858c62f",
        "e3477bfab28b7e72edd1989659577089a1d4aa84",
        true,
    ),
    (
        "SFMTerminalRasterFrame",
        "sha256:44a010b47095af6a3e73beec2c87d12a9779141e4d4d084561371480a142c520",
        4228,
        "2301ef0a93cc25b4cb6bf0b074e7fb5b2a427b63",
        "",
        false,
    ),
    (
        "SFMTerminalTransportId",
        "sha256:4002ae9566c30d568b664d53a5553bdc6ae8222c6e1d658462e6bb4d2cb91e31",
        1029,
        "2d4b9c09ab326c452ecf06903c22374c7fccd235",
        "",
        false,
    ),
    (
        "SFMTerminalSelectionLayout",
        "sha256:207bf89fd772530f32aafbec2229b9be68bece9668618d47732537d5af4bdf7f",
        11566,
        "4730fd8311553067d57b79cc6985d0d8dec9a1ce",
        "",
        false,
    ),
    (
        "SFMTerminalImageLayout",
        "sha256:b9fd3fdd2471349e9727e4daa41be81f3be6bf0d4bc654b92a43ac8fe4b7a39c",
        3076,
        "51f407dae52d72f4bca0c39371a2eded66d19b9f",
        "",
        false,
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    scope: Scope,
    context_commits: BTreeMap<String, String>,
    normalization: Normalization,
    prerequisite_definitions: BTreeMap<String, Definition>,
    files: Vec<File>,
    raw_variants: Vec<RawVariant>,
    full_source_profiles: Vec<Profile>,
}
#[derive(Facet)]
struct Scope {
    production_files: usize,
    context_cells: usize,
    present_cells: usize,
    absent_cells: usize,
    raw_variants: usize,
    stage_bytes: u64,
    new_flags: usize,
    new_dependencies: usize,
    java_compilation: bool,
    live_runtime: bool,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    raw_byte_exact: bool,
    authorized_transformations: Vec<String>,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct File {
    name: String,
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
    class_support: Vec<String>,
    source_rule: SourceRule,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct SourceRule {
    input: String,
    template: bool,
    when: SourceWhen,
}
#[derive(Facet)]
struct SourceWhen {
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    commit: String,
    present: bool,
    git_blob: Option<String>,
    raw_bytes: u64,
    raw_sha256: Option<String>,
}
#[derive(Facet)]
struct RawVariant {
    name: String,
    git_blob: String,
    sha256: String,
    bytes: u64,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
}
#[derive(Facet)]
struct Profile {
    target: String,
    enabled: Vec<String>,
    selected_paths: Vec<String>,
}

struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    sources: Vec<Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), LIMIT)?;
        ensure!(
            sha256(&bytes) == LEDGER_SHA,
            "reviewed terminal-carrier ledger changed"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)
            .wrap_err("cannot parse terminal carrier evidence")?;
        let sources = FILES
            .iter()
            .map(|f| shared.read_source(&path(f.0)))
            .collect::<Result<Vec<_>>>()?;
        let raw = read_git_blobs(
            &shared.repository,
            &RAW.iter().map(|r| r.0.to_owned()).collect(),
        )?;
        let result = Self {
            shared,
            ledger,
            sources,
            raw,
        };
        result.validate()?;
        Ok(result)
    }
    fn validate(&self) -> Result<()> {
        let s = &self.ledger.scope;
        ensure!(
            self.ledger.schema == "sfm:core-terminal-carriers-slice@1"
                && s.production_files == 7
                && s.context_cells == 140
                && s.present_cells == 30
                && s.absent_cells == 110
                && s.raw_variants == 9
                && s.stage_bytes == 25305
                && s.new_flags == 2
                && s.new_dependencies == 0
                && !s.java_compilation
                && !s.live_runtime,
            "terminal carrier source-only scope changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.raw_byte_exact
                && self
                    .ledger
                    .normalization
                    .authorized_transformations
                    .is_empty(),
            "no normalization authorized"
        );
        let contexts = CONTEXTS
            .into_iter()
            .map(|(n, id)| (n.to_owned(), id.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == contexts
                && self.ledger.files.len() == 7
                && self.ledger.raw_variants.len() == 9
                && self.ledger.full_source_profiles.len() == 10,
            "carrier frozen witness coverage changed"
        );
        for (name, definition) in &self.ledger.prerequisite_definitions {
            let actual = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("carrier feature not registered: {name}"))?;
            ensure!(
                actual.supported_targets == definition.supported_targets
                    && actual.requires == definition.requires,
                "carrier dependency/support changed: {name}"
            );
        }
        for (oid, digest, count) in RAW {
            validate_bytes(&self.raw[oid], digest, count)?;
            let row = self
                .ledger
                .raw_variants
                .iter()
                .find(|r| r.git_blob == oid)
                .ok_or_else(|| eyre::eyre!("raw carrier fact absent"))?;
            ensure!(
                row.sha256 == digest
                    && row.bytes == count
                    && row.crlf_count == 0
                    && row.lone_cr_count == 0
                    && row.final_lf
                    && !row.bom,
                "raw carrier facts changed"
            );
            ensure!(
                FILES
                    .iter()
                    .any(|f| f.0 == row.name && (f.3 == oid || f.4 == oid)),
                "raw owner changed"
            );
        }
        for (index, fact) in FILES.iter().enumerate() {
            let row = &self.ledger.files[index];
            let p = path(fact.0);
            let targets = if fact.5 {
                TARGETS.to_vec()
            } else {
                TARGETS[..2].to_vec()
            };
            let (all, any) = predicates(index);
            ensure!(
                row.name == fact.0
                    && row.intended_core_path == p
                    && row.stage_sha256 == fact.1
                    && row.stage_bytes == fact.2
                    && row.class_support == targets
                    && row.witnesses.len() == 20
                    && row.source_rule.input == p
                    && row.source_rule.template
                    && row.source_rule.when.targets == targets
                    && row.source_rule.when.all_features == all
                    && row.source_rule.when.any_features == any,
                "canonical carrier identity/predicate changed: {}",
                fact.0
            );
            validate_bytes(&self.sources[index], fact.1, fact.2)?;
            let rules = self
                .shared
                .metadata
                .source_rules
                .get(&p)
                .ok_or_else(|| eyre::eyre!("explicit carrier source rule absent"))?;
            ensure!(rules.len() == 1, "carrier requires one canonical input");
            let rule = &rules[0];
            ensure!(
                rule.input == p
                    && rule.when.none_features.is_empty()
                    && set(&rule.when.targets) == targets.iter().copied().collect()
                    && set(&rule.when.all_features) == all.into_iter().collect()
                    && set(&rule.when.any_features) == any.into_iter().collect(),
                "actual carrier predicate/support changed"
            );
            let mut seen = BTreeSet::new();
            for witness in &row.witnesses {
                let (kind, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("bad witness"))?;
                let present = kind == "dev" && (fact.5 || is_d2(target));
                let oid = if present {
                    Some(witness_oid(index, target))
                } else {
                    None
                };
                let raw_fact = oid.and_then(|id| RAW.iter().find(|r| r.0 == id));
                ensure!(
                    seen.insert(witness.context.as_str())
                        && contexts.get(&witness.context) == Some(&witness.commit)
                        && witness.present == present
                        && witness.git_blob.as_deref() == oid
                        && witness.raw_bytes == raw_fact.map_or(0, |r| r.2)
                        && witness.raw_sha256.as_deref() == raw_fact.map(|r| r.1),
                    "frozen carrier membership changed"
                );
            }
            ensure!(
                seen == contexts.keys().map(String::as_str).collect(),
                "witness contexts incomplete"
            );
            let source = std::str::from_utf8(&self.sources[index])?;
            ensure!(
                !source.contains("case minecraft_version")
                    && !source.contains("environment")
                    && !source.contains("projection_key"),
                "functional terminal differences must not be target/snapshot cases"
            );
            for line in source.lines().filter(|l| l.starts_with("import ")) {
                ensure!(
                    line.starts_with("import java.")
                        || (matches!(index, 5 | 6)
                            && line.starts_with("import ca.teamdman.sfm.client.screen.workspace.")),
                    "unreviewed carrier host import"
                );
            }
        }
        Ok(())
    }
    fn context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        // Test-only explicit dependency closure from the immutable reviewed
        // contract. Production context helper still refuses incomplete sets.
        let mut flags = requested
            .iter()
            .map(|f| (*f).to_owned())
            .collect::<BTreeSet<_>>();
        loop {
            let mut added = false;
            for name in flags.clone() {
                let definition = self
                    .ledger
                    .prerequisite_definitions
                    .get(&name)
                    .ok_or_else(|| eyre::eyre!("unreviewed carrier owner: {name}"))?;
                ensure!(
                    definition.supported_targets.iter().any(|t| t == target),
                    "owner unsupported on target"
                );
                for dependency in &definition.requires {
                    added |= flags.insert(dependency.clone());
                }
            }
            if !added {
                break;
            }
        }
        self.shared.context(
            target,
            &flags.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }
    fn selected(&self, context: &ProjectionContext) -> Result<BTreeSet<usize>> {
        let selection = self.shared.selection_for_assertion(context, &inventory())?;
        let mut result = BTreeSet::new();
        for (index, file) in self.ledger.files.iter().enumerate() {
            let (all, any) = predicates(index);
            let expected = file.class_support.contains(&selection.target_id)
                && all.iter().all(|f| enabled(context, f))
                && (any.is_empty() || any.iter().any(|f| enabled(context, f)));
            if let Some(input) = selection.inputs.get(&file.intended_core_path) {
                ensure!(
                    expected
                        && input.input == file.intended_core_path
                        && !selection.omitted_paths.contains(&file.intended_core_path),
                    "carrier provenance escaped predicate"
                );
                result.insert(index);
            } else {
                ensure!(
                    !expected && selection.omitted_paths.contains(&file.intended_core_path),
                    "inactive carrier not explicitly omitted"
                );
            }
        }
        Ok(result)
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<String> {
        ensure!(
            self.selected(context)?.contains(&index),
            "refuse omitted carrier rendering"
        );
        render_java_source(std::str::from_utf8(&self.sources[index])?, context)
    }
    fn full_context(&self, target: &str) -> Result<ProjectionContext> {
        let profile = self
            .ledger
            .full_source_profiles
            .iter()
            .find(|p| p.target == target)
            .ok_or_else(|| eyre::eyre!("full carrier profile absent"))?;
        let context = self.shared.context(
            target,
            &profile
                .enabled
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>(),
        )?;
        let paths = self
            .selected(&context)?
            .iter()
            .map(|i| path(FILES[*i].0))
            .collect::<Vec<_>>();
        ensure!(
            paths == profile.selected_paths,
            "full carrier profile selection changed"
        );
        Ok(context)
    }
}

fn path(name: &str) -> String {
    format!("src/main/java/ca/teamdman/sfm/client/terminal/{name}.java")
}
fn inventory() -> BTreeSet<String> {
    FILES.iter().map(|f| path(f.0)).collect()
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn enabled(context: &ProjectionContext, feature: &str) -> bool {
    context.features.get(feature).copied().unwrap_or(false)
}
fn set(values: &[String]) -> BTreeSet<&str> {
    values.iter().map(String::as_str).collect()
}
fn witness_oid(index: usize, target: &str) -> &'static str {
    let fact = FILES[index];
    if is_d2(target) || fact.4.is_empty() {
        fact.3
    } else {
        fact.4
    }
}
fn predicates(index: usize) -> (Vec<&'static str>, Vec<&'static str>) {
    match index {
        0 => (
            vec![],
            vec![
                "terminal_remote",
                "terminal_vox_runtime",
                "terminal_properties",
                "touch_display_terminal_mount",
            ],
        ),
        1 => (
            vec!["terminal_frame_metadata"],
            vec![
                "terminal_remote",
                "terminal_vox_runtime",
                "terminal_properties",
                "touch_display_terminal_mount",
            ],
        ),
        2 => (vec!["terminal_focus_gestures"], vec![]),
        3 => (vec![], vec!["terminal_remote", "terminal_vox_runtime"]),
        4 => (
            vec![],
            vec![
                "terminal_remote",
                "terminal_vox_runtime",
                "terminal_presentation_actions",
            ],
        ),
        5 => (vec!["terminal_clipboard"], vec![]),
        6 => (
            vec![],
            vec![
                "terminal_remote",
                "terminal_vox_runtime",
                "terminal_clipboard",
                "terminal_properties",
            ],
        ),
        _ => (vec![], vec![]),
    }
}
fn validate_bytes(bytes: &[u8], expected: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count && sha256(bytes) == expected,
        "exact carrier bytes changed"
    );
    ensure!(
        !bytes.starts_with(&[0xef, 0xbb, 0xbf])
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "only raw LF/final-LF carrier text authorized"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

#[test]
fn frozen_one_hundred_forty_membership_cells_and_thirty_raw_goldens_reconstruct() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    let mut outputs = 0;
    for (kind_target, _) in CONTEXTS {
        let (kind, target) = kind_target
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("context absent"))?;
        let context = if kind == "dev" {
            fixture.full_context(target)?
        } else {
            fixture.shared.context(target, &[])?
        };
        for (index, fact) in FILES.iter().enumerate() {
            let present = kind == "dev" && (fact.5 || is_d2(target));
            assert_eq!(
                fixture.selected(&context)?.contains(&index),
                present,
                "{kind_target} {}",
                fact.0
            );
            if present {
                assert_eq!(
                    fixture.render(index, &context)?.as_bytes(),
                    fixture.raw[witness_oid(index, target)],
                    "{kind_target} {}",
                    fact.0
                );
                outputs += 1;
            } else {
                assert!(fixture.render(index, &context).is_err());
            }
            cells += 1;
        }
    }
    assert_eq!((cells, outputs), (140, 30));
    Ok(())
}

#[test]
fn thirty_two_independent_metadata_host_escape_clipboard_and_presentation_masks() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut masks = 0;
    for target in &TARGETS[..2] {
        for mask in 0_u8..16 {
            let mut flags = vec!["terminal_remote", "terminal_focus_gestures"];
            for (bit, flag) in [
                "terminal_frame_metadata",
                "terminal_host_escape",
                "terminal_clipboard",
                "terminal_presentation_actions",
            ]
            .into_iter()
            .enumerate()
            {
                if mask & (1 << bit) != 0 {
                    flags.push(flag);
                }
            }
            let context = fixture.context(target, &flags)?;
            let frame = fixture.render(0, &context)?;
            let frame_oid = if mask & 1 != 0 {
                FILES[0].3
            } else {
                FILES[0].4
            };
            assert_eq!(frame.as_bytes(), fixture.raw[frame_oid]);
            assert_eq!(fixture.selected(&context)?.contains(&1), mask & 1 != 0);
            assert_eq!(fixture.selected(&context)?.contains(&5), mask & 4 != 0);
            if mask & 1 != 0 {
                assert!(frame.contains("metadata = metadata == null"));
                assert!(frame.contains("this(sequence, full, png, payload, metadata, null);"));
                assert!(frame.contains(
                    "if (!metadata.correlationId().isBlank()) return metadata.correlationId();"
                ));
                assert!(frame.contains("return \"legacy\";"));
            } else {
                assert!(
                    !frame.contains("SFMTerminalFrameMetadata")
                        && !frame.contains("streamIdentity")
                );
                assert!(frame.contains(
                    "SFMTerminalFrame(long sequence, boolean full, boolean png, byte[] payload)"
                ));
            }
            assert_eq!(
                frame
                    .matches("return Arrays.copyOf(payload, payload.length);")
                    .count(),
                1
            );
            let focus = fixture.render(2, &context)?;
            let focus_oid = if mask & 2 != 0 {
                FILES[2].3
            } else {
                FILES[2].4
            };
            assert_eq!(focus.as_bytes(), fixture.raw[focus_oid]);
            assert_eq!(focus.contains("HOST_ESCAPE"), mask & 2 != 0);
            assert_eq!(focus.contains("Decision.EXIT"), mask & 2 == 0);
            for index in [3, 4, 6] {
                assert_eq!(
                    fixture.render(index, &context)?.as_bytes(),
                    fixture.raw[FILES[index].3]
                );
            }
            for forbidden in [
                "terminal_keyboard_input",
                "terminal_paste_confirmation",
                "terminal_properties",
                "terminal_local",
                "touch_display_terminal_mount",
                "terminal_vox_runtime",
            ] {
                assert!(!enabled(&context, forbidden));
            }
            masks += 1;
        }
    }
    assert_eq!(masks, 32);
    Ok(())
}

#[test]
fn per_file_consumer_or_masks_preserve_target_bounds_without_backend_or_ui_catchall() -> Result<()>
{
    let fixture = Fixture::load()?;
    let mut masks = 0;
    for target in TARGETS {
        for (index, fact) in FILES.iter().enumerate() {
            let (all, any) = predicates(index);
            for all_on in [false, true] {
                for mask in 0..(1_usize << any.len()) {
                    let mut flags = if all_on { all.clone() } else { vec![] };
                    flags.extend(
                        any.iter()
                            .enumerate()
                            .filter_map(|(bit, flag)| (mask & (1 << bit) != 0).then_some(*flag)),
                    );
                    let supported = flags.iter().all(|flag| {
                        fixture.ledger.prerequisite_definitions[*flag]
                            .supported_targets
                            .iter()
                            .any(|t| t == target)
                    });
                    if !supported {
                        assert!(fixture.context(target, &flags).is_err());
                        continue;
                    }
                    let context = fixture.context(target, &flags)?;
                    let expected = (fact.5 || is_d2(target))
                        && all.iter().all(|f| enabled(&context, f))
                        && (any.is_empty() || any.iter().any(|f| enabled(&context, f)));
                    assert_eq!(fixture.selected(&context)?.contains(&index), expected);
                    if expected {
                        assert!(!fixture.render(index, &context)?.contains("{%"));
                    } else {
                        assert!(fixture.render(index, &context).is_err());
                    }
                    masks += 1;
                }
            }
        }
        for backend in ["terminal_remote", "terminal_vox_runtime"] {
            let context = fixture.context(target, &[backend])?;
            assert!(fixture.selected(&context)?.contains(&0));
            if !is_d2(target) {
                assert_eq!(fixture.selected(&context)?, BTreeSet::from([0]));
            }
            assert!(
                !enabled(&context, "workspace_panels")
                    && !enabled(&context, "client_actions")
                    && !enabled(&context, "terminal_frame_metadata")
                    && !enabled(&context, "terminal_host_escape")
            );
        }
    }
    assert!(masks > 200);
    Ok(())
}

#[test]
fn carrier_owner_only_unknown_and_unsupported_feature_sets_fail_closed() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        assert!(
            fixture
                .shared
                .context(target, &["unknown_terminal_carrier_owner"])
                .is_err()
        );
        if is_d2(target) {
            let metadata_only = fixture
                .shared
                .context(target, &["terminal_frame_metadata"])?;
            assert!(fixture.selected(&metadata_only)?.is_empty());
            assert!(
                fixture
                    .shared
                    .context(target, &["terminal_host_escape"])
                    .is_err()
            );
            let focus = fixture.context(target, &["terminal_host_escape"])?;
            assert_eq!(fixture.selected(&focus)?, BTreeSet::from([2]));
            let clipboard = fixture.context(target, &["terminal_clipboard"])?;
            assert_eq!(fixture.selected(&clipboard)?, BTreeSet::from([5, 6]));
            assert!(
                !enabled(&clipboard, "workspace_panels")
                    && !enabled(&clipboard, "terminal_frame_metadata")
            );
        } else {
            for flag in [
                "terminal_frame_metadata",
                "terminal_host_escape",
                "terminal_clipboard",
                "terminal_properties",
                "terminal_presentation_actions",
            ] {
                assert!(fixture.context(target, &[flag]).is_err());
            }
        }
        let configuration = if is_d2(target) {
            fixture.shared.context(target, &["terminal_vox"])?
        } else {
            fixture.shared.context(target, &[])?
        };
        assert!(fixture.selected(&configuration)?.is_empty());
    }
    Ok(())
}

#[test]
fn raw_and_stage_identities_reject_unapproved_eol_bom_token_and_eof_changes() -> Result<()> {
    let fixture = Fixture::load()?;
    for (oid, digest, count) in RAW {
        let bytes = &fixture.raw[oid];
        assert!(validate_bytes(&bytes[..bytes.len() - 1], digest, count).is_err());
        let crlf = std::str::from_utf8(bytes)?
            .replace('\n', "\r\n")
            .into_bytes();
        assert!(validate_bytes(&crlf, digest, count).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(bytes);
        assert!(validate_bytes(&bom, digest, count).is_err());
        let mut token = bytes.clone();
        token[0] ^= 1;
        assert!(validate_bytes(&token, digest, count).is_err());
    }
    for (index, fact) in FILES.iter().enumerate() {
        let mut token = fixture.sources[index].clone();
        token[0] ^= 1;
        assert!(validate_bytes(&token, fact.1, fact.2).is_err());
    }
    Ok(())
}

#[test]
fn thirty_common_body_edits_reach_multiple_targets_without_changing_member_guards() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let anchor = "package ca.teamdman.sfm.client.terminal;\n";
    let replacement = format!("{anchor}\n// Shared terminal carrier edit proof.\n");
    for (index, fact) in FILES.iter().enumerate() {
        let source = std::str::from_utf8(&fixture.sources[index])?;
        ensure!(source.matches(anchor).count() == 1, "common anchor changed");
        let p = temp.path().join(CORE_ROOT).join(path(fact.0));
        fs::create_dir_all(
            p.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(p, source.replacen(anchor, &replacement, 1))?;
    }
    let mut count = 0;
    for target in TARGETS {
        let context = fixture.full_context(target)?;
        let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
        for index in fixture.selected(&context)? {
            let p = path(FILES[index].0);
            let input = &selection.inputs[&p];
            let bytes = read_bounded(&temp.path().join(CORE_ROOT).join(&input.input), LIMIT)?;
            let output = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            assert_eq!(
                output,
                fixture
                    .render(index, &context)?
                    .replacen(anchor, &replacement, 1)
            );
            assert_eq!(fixture.shared.read_source(&p)?, fixture.sources[index]);
            count += 1;
        }
    }
    assert_eq!(count, 30);
    Ok(())
}

#[test]
fn production_collector_uses_fixed_core_and_automatic_java_rendering_not_historical_routing()
-> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture.context(
        "1.19.2",
        &[
            "terminal_remote",
            "terminal_frame_metadata",
            "terminal_focus_gestures",
            "terminal_host_escape",
        ],
    )?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let paths = inventory();
    let mut metadata = fixture.shared.metadata.clone();
    metadata.source_rules.retain(|p, _| paths.contains(p));
    for rules in metadata.source_rules.values_mut() {
        for rule in rules {
            rule.template = false;
        }
    }
    let selection = select_core_inputs(&metadata, &context, &paths)?;
    for (output, input) in &selection.inputs {
        let bytes = if let Some(index) = FILES.iter().position(|f| path(f.0) == *output) {
            fixture.sources[index].clone()
        } else {
            read_bounded(&fixture.shared.core.join(&input.input), 16 * 1024 * 1024)?
        };
        let p = root.join(&input.input);
        fs::create_dir_all(
            p.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(p, bytes)?;
    }
    let artifacts = collect_core_artifacts(&root, &selection, &context)?;
    for index in fixture.selected(&context)? {
        let p = path(FILES[index].0);
        assert!(!selection.inputs[&p].template);
        let artifact = &artifacts[&p];
        assert_eq!(artifact.source_bytes, fixture.sources[index]);
        assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{p}"));
        assert!(artifact.overlay.is_none());
        let (_, body) = std::str::from_utf8(&artifact.output_bytes)?
            .split_once('\n')
            .ok_or_else(|| eyre::eyre!("generated banner absent"))?;
        assert_eq!(body, fixture.render(index, &context)?);
        assert!(!body.contains("{%"));
    }
    assert!(
        collect_core_artifacts(&temp.path().join("different-core"), &selection, &context).is_err()
    );
    let p = path(FILES[0].0);
    let source = std::str::from_utf8(&fixture.sources[0])?;
    for changed in [
        format!("{{% else %}}\n{source}"),
        format!("{{% if features.unknown_terminal_carrier_owner %}}\n{source}{{% endif %}}\n"),
        format!("{{% if features.terminal_remote %}}\n{source}{{% endcase %}}\n"),
    ] {
        assert!(render_java_source(&changed, &context).is_err());
    }
    fs::write(
        root.join(&p),
        format!("{{% case environment %}}\n{{% when \"release\" %}}\n{source}{{% endcase %}}\n"),
    )?;
    assert!(collect_core_artifacts(&root, &selection, &context).is_err());
    for (index, fact) in FILES.iter().enumerate() {
        assert_eq!(
            fixture.shared.read_source(&path(fact.0))?,
            fixture.sources[index]
        );
    }
    Ok(())
}

#[test]
fn twenty_offline_tree_reads_confirm_all_one_hundred_forty_exact_membership_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    for (name, commit) in CONTEXTS {
        let (kind, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let mut command = frozen_git_command(&fixture.shared.repository);
        command
            .env("GIT_ALLOW_PROTOCOL", "")
            .env("GIT_TERMINAL_PROMPT", "0")
            .args([
                "-c",
                "protocol.allow=never",
                "-c",
                "core.fsmonitor=false",
                "ls-tree",
                "-z",
                commit,
                "--",
            ]);
        command.args(
            FILES
                .iter()
                .map(|f| format!("platform/minecraft/{}", path(f.0))),
        );
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("tree output absent"))?;
        let mut bytes = Vec::new();
        let result = reader.by_ref().take(8193).read_to_end(&mut bytes);
        drop(reader);
        if result.is_err() || bytes.len() > 8192 {
            let _ = child.kill();
            let _ = child.wait();
            result?;
            eyre::bail!("tree output too large");
        }
        ensure!(child.wait()?.success(), "offline tree read failed");
        let mut entries = BTreeMap::new();
        for (index, fact) in FILES.iter().enumerate() {
            if kind == "dev" && (fact.5 || is_d2(target)) {
                let p = path(fact.0);
                entries.insert(
                    p.clone(),
                    format!(
                        "100644 blob {}\tplatform/minecraft/{p}\0",
                        witness_oid(index, target)
                    ),
                );
                present += 1;
            }
        }
        assert_eq!(
            bytes,
            entries.into_values().collect::<String>().as_bytes(),
            "{name}"
        );
    }
    assert_eq!(present, 30);
    Ok(())
}
