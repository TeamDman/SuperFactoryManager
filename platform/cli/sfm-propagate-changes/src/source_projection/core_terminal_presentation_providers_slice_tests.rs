//! Prepared current-core regressions for six genuine terminal presentation providers.
//! Root must atomically promote all six sources/rules, Terminal26 sources, the
//! four exact pure properties consumer unions, genuine shared UI peers and ledger.
//! This module NEVER reverses current source selectors. Its properties-only
//! collector counts/bodies are current evidence, separate from Terminal26 v2's
//! private historical fixture. Git is offline witness only, not production input.
//! V2 uses one shared package/class scaffold and the actual 1.21 alias for
//! target 1.21.0. Common-edit tests change one unique authored hunk once.
//! No Java types, GL, clipboard/input consent, native/backend/network/runtime proof.
//! Authored, unregistered, unexecuted and unformatted.
#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputPredicate;
use super::core_inputs::InputVariant;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::historical_feature_registry;
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

const LEDGER: &str = "docs/tasks/sfm-core-terminal-presentation-providers-slice.json";
const PREFIX: &str = "src/main/java/ca/teamdman/sfm/client/terminal/";
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const TEN: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const NEW: [&str; 6] = [
    "SFMTerminalRemoteService",
    "SFMTerminalClient",
    "SFMTerminalPanel",
    "SFMTerminalPropertiesPanel",
    "SFMTerminalPngRenderer",
    "SFMTerminalRgbaRenderer",
];
const PURE: [&str; 4] = [
    "SFMTerminalService",
    "SFMTerminalScrollback",
    "SFMTerminalLine",
    "SFMTerminalResponse",
];
const OLD_PURE: [&str; 3] = ["terminal_local", "terminal_remote", "terminal_vox_runtime"];
const CURRENT_PURE: [&str; 4] = [
    "terminal_local",
    "terminal_remote",
    "terminal_vox_runtime",
    "terminal_properties",
];
const UI: [&str; 3] = [
    "workspace_panels",
    "workspace_widget_hosts",
    "client_actions",
];
const EFFECTS: [&str; 10] = [
    "terminal_keyboard_input",
    "terminal_clipboard",
    "terminal_paste_confirmation",
    "terminal_server_lifecycle",
    "terminal_vox",
    "packet_transport_private",
    "multiplayer_packets",
    "client_inbox",
    "touch_display_terminal_mount",
    "terminal_repl_actions",
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
const RAW: [(&str, usize, &str); 18] = [
    (
        "a82f08f7cc438ab6dd93c2dd13e0e9115471cd68",
        7504,
        "sha256:a050f0447c32d0a2ad0aa17307b23789a00c9f9d9de4ce0d0bac7ae281eeff2c",
    ),
    (
        "de606bf796665a9c290e0b0fcfddd8fe9c961250",
        754,
        "sha256:b8ef28983d207f967ba64f7df19b9ffdcf2be778da1f381961e3de05ee783830",
    ),
    (
        "ce9e6bf3e5465a2d6c448070e20c4dacfd6fe9ff",
        740,
        "sha256:55ca9eb976cd0450c59162b703fabbbf1d8217baeb9060197c9ef4469d47f160",
    ),
    (
        "71fe595b87be649875e9be770edb1442b83a31df",
        91705,
        "sha256:0e934bc5d4aa2e894be5068aa5cf08eba8988ed2b2aa908921b44e2469fdf85f",
    ),
    (
        "495d72a7df045ff81a8e289b962519e680a067bb",
        92330,
        "sha256:65fc5d920e34e4b9c45d5cf74f6c3079fe14cf9e8a67abc09ad372c3755000bc",
    ),
    (
        "0dfeec7d19258b930fc9de0b8b7ba7608a7b4ecd",
        19625,
        "sha256:5c1c3e5b14c92effeaf0dbf93dc3daef940242957fcdc5922b4c12b3d887611a",
    ),
    (
        "68f99477868fe871fcb584a1c14da286563318c6",
        19661,
        "sha256:f0e8120a93577609aed57a1f104210c48b64e23c843a789958aa167e475569be",
    ),
    (
        "7c06817708bc9c503d8c192f7c9c8ebc76535536",
        24899,
        "sha256:c83875a82b5b56623d7fc6030a527c2bf5b5c7e6a139ad339d346d454be5b517",
    ),
    (
        "edf56cf5fc000a67edd13b11c47b53859bf10a8c",
        12903,
        "sha256:7e8cb2052c7489c4332aa572a7a8ae331b55455fd52ad75efc0aaf22f750364b",
    ),
    (
        "c138dc11cfbd1b500f01e7c560649704d886cbc8",
        4157,
        "sha256:eda24ad160fb2f5a8119fc71d71d849305c98659a11cfaf51ca58d57f8f53896",
    ),
    (
        "e9b71e612116f882d077564bf0baf6ad8a35bdf2",
        4234,
        "sha256:1c28ad00d5812bd36d2b14fa58ae2f4dca6cd511fc7c7cc9618eb40a2a44eb7c",
    ),
    (
        "83e0b4c7a7bb0482d7de16e322d2cc277b7b7fdc",
        4083,
        "sha256:130c50eba0be165480aeada5a14c23ff30a584659cf80d7eb87b91012edcdd77",
    ),
    (
        "4b2453b7ece1706a509b9374feda76c221fed2ae",
        5577,
        "sha256:cdcba0dd7b8ed168bbaf5fb455865f293f956f71442f96b4556dfaec4f4110f7",
    ),
    (
        "47052c952a040b4d50a584fa93ca448736eb6445",
        338,
        "sha256:aad57cff7c746953fd9a537f8e94bb0050a5690bce63ed9a4529500a056667e6",
    ),
    (
        "935533693c01e03fdd9b5b273b659f500db10713",
        344,
        "sha256:b22817f909620c0b11755833b2930b9e5c73f0cd9a8f930eacf418c130a70b6c",
    ),
    (
        "e0caa13fb14e1ad1250db6197d881a7cb5f8377e",
        5599,
        "sha256:7ea64b0c1d3e6406a6fb6daab8e1b441e55e18e8828f1a0060aa4f3c6f38a84a",
    ),
    (
        "dd20303165846720d0b72f61a420716f1adefe39",
        505,
        "sha256:c64ef66f07eb705bec614978a5a10846243220be4296dc19d291dee82170aecb",
    ),
    (
        "d7c55c4d75a95b2739c04b5ec928a68ca64c8df5",
        1989,
        "sha256:61245350da59b9b7782e2697ed384639ba448307d519e771053ccd91a400c81a",
    ),
];
const SOURCE_PINS: [(&str, usize, &str); 10] = [
    (
        "SFMTerminalRemoteService",
        9001,
        "sha256:5656ba33c63c80c1590fd6116241f293a5114d7f0a6bc63f4cb6e95a4305ee0e",
    ),
    (
        "SFMTerminalClient",
        740,
        "sha256:55ca9eb976cd0450c59162b703fabbbf1d8217baeb9060197c9ef4469d47f160",
    ),
    (
        "SFMTerminalPanel",
        138071,
        "sha256:40d6f54d972dcc8bc11aa8ed8f30bb5cc9a3970f47a27f079f9947ebb9e9a0a8",
    ),
    (
        "SFMTerminalPropertiesPanel",
        25590,
        "sha256:3607d496bffd83b4b1be74108c67d8acd30e3955cec027475291d81b1d8a237e",
    ),
    (
        "SFMTerminalPngRenderer",
        20425,
        "sha256:e38345f961b31144f26bf8a66581795578eafd2806137d097d37d4eafdb9c2d6",
    ),
    (
        "SFMTerminalRgbaRenderer",
        5577,
        "sha256:cdcba0dd7b8ed168bbaf5fb455865f293f956f71442f96b4556dfaec4f4110f7",
    ),
    (
        "SFMTerminalService",
        515,
        "sha256:c2b4e11be5ccb7a46e1dd9d819d4324dd0461763fa7c1ccfd1a4c1c5606e6a9c",
    ),
    (
        "SFMTerminalScrollback",
        5599,
        "sha256:7ea64b0c1d3e6406a6fb6daab8e1b441e55e18e8828f1a0060aa4f3c6f38a84a",
    ),
    (
        "SFMTerminalLine",
        505,
        "sha256:c64ef66f07eb705bec614978a5a10846243220be4296dc19d291dee82170aecb",
    ),
    (
        "SFMTerminalResponse",
        1989,
        "sha256:61245350da59b9b7782e2697ed384639ba448307d519e771053ccd91a400c81a",
    ),
];
const DEFINITIONS: [(&str, &[&str], &[&str]); 24] = [
    (
        "workspace_keyboard_context",
        &["1.19.2", "1.19.4"],
        &["keyboard_profiles"],
    ),
    ("workspace_widget_hosts", &["1.19.2", "1.19.4"], &[]),
    ("workspace_panel_measurement", &["1.19.2", "1.19.4"], &[]),
    ("workspace_panel_lookup", &["1.19.2", "1.19.4"], &[]),
    (
        "terminal_focus_gestures",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "terminal_local",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    ("terminal_keyboard_input", &["1.19.2", "1.19.4"], &[]),
    (
        "terminal_vox_runtime",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "terminal_properties",
        &["1.19.2", "1.19.4"],
        &["workspace_panels"],
    ),
    ("terminal_clipboard", &["1.19.2", "1.19.4"], &[]),
    ("terminal_paste_confirmation", &["1.19.2", "1.19.4"], &[]),
    (
        "terminal_server_lifecycle",
        &["1.19.2", "1.19.4"],
        &["client_actions"],
    ),
    (
        "terminal_legacy_open_actions",
        &[
            "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2",
        ],
        &["client_actions", "workspace_panels", "terminal_remote"],
    ),
    (
        "terminal_presentation_actions",
        &["1.19.2", "1.19.4"],
        &["client_actions", "workspace_panels", "terminal_remote"],
    ),
    (
        "terminal_repl_actions",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &["client_actions", "workspace_panels"],
    ),
    (
        "terminal_tuning_actions",
        &["1.19.2", "1.19.4"],
        &["client_actions", "workspace_panels", "terminal_remote"],
    ),
    (
        "font_formatted_text",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "terminal_remote",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    ("terminal_vox", &["1.19.2", "1.19.4"], &[]),
    (
        "workspace_panels",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "client_actions",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    ("terminal_frame_metadata", &["1.19.2", "1.19.4"], &[]),
    (
        "terminal_host_escape",
        &["1.19.2", "1.19.4"],
        &["terminal_focus_gestures"],
    ),
    ("screen_fractional_highlights", &["1.19.2"], &[]),
];
const D2_PROFILES: [(&str, &[&str]); 22] = [
    ("off", &[]),
    ("local_only", &["terminal_local"]),
    ("remote_only", &["terminal_remote"]),
    (
        "properties_only",
        &["workspace_panels", "terminal_properties"],
    ),
    (
        "local_ui",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_local",
        ],
    ),
    (
        "local_input",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_local",
            "terminal_keyboard_input",
        ],
    ),
    (
        "local_focus",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_local",
            "terminal_keyboard_input",
            "terminal_focus_gestures",
        ],
    ),
    (
        "local_host_escape",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_local",
            "terminal_keyboard_input",
            "terminal_focus_gestures",
            "terminal_host_escape",
        ],
    ),
    (
        "local_repl",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_local",
            "terminal_repl_actions",
        ],
    ),
    (
        "remote_ui",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_remote",
        ],
    ),
    (
        "remote_input",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_remote",
            "terminal_keyboard_input",
        ],
    ),
    (
        "remote_clipboard",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_remote",
            "terminal_clipboard",
        ],
    ),
    (
        "remote_confirmation",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_remote",
            "terminal_clipboard",
            "terminal_paste_confirmation",
        ],
    ),
    (
        "remote_metadata",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_remote",
            "terminal_frame_metadata",
        ],
    ),
    (
        "remote_full_input",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_remote",
            "terminal_frame_metadata",
            "terminal_keyboard_input",
            "terminal_clipboard",
            "terminal_paste_confirmation",
            "terminal_focus_gestures",
            "terminal_host_escape",
            "workspace_panel_measurement",
        ],
    ),
    (
        "remote_presentation",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_remote",
            "terminal_presentation_actions",
        ],
    ),
    (
        "remote_tuning",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_remote",
            "terminal_tuning_actions",
        ],
    ),
    (
        "remote_lifecycle",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_remote",
            "terminal_server_lifecycle",
        ],
    ),
    (
        "properties_ui",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_properties",
        ],
    ),
    (
        "properties_lookup",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_properties",
            "workspace_panel_lookup",
        ],
    ),
    (
        "properties_controls",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_properties",
            "workspace_panel_lookup",
            "terminal_remote",
            "terminal_tuning_actions",
        ],
    ),
    (
        "full",
        &[
            "workspace_panels",
            "workspace_widget_hosts",
            "client_actions",
            "terminal_local",
            "terminal_remote",
            "terminal_vox_runtime",
            "terminal_properties",
            "terminal_tuning_actions",
            "terminal_presentation_actions",
            "terminal_frame_metadata",
            "terminal_keyboard_input",
            "terminal_clipboard",
            "terminal_paste_confirmation",
            "terminal_focus_gestures",
            "terminal_host_escape",
            "terminal_server_lifecycle",
            "terminal_repl_actions",
            "workspace_panel_measurement",
            "workspace_panel_lookup",
        ],
    ),
];
const OLDER_PROFILES: [(&str, &[&str]); 5] = [
    ("off", &[]),
    ("local_only", &["terminal_local"]),
    ("local_ui", &["workspace_panels", "terminal_local"]),
    ("remote_ui", &["workspace_panels", "terminal_remote"]),
    (
        "full",
        &[
            "workspace_panels",
            "terminal_local",
            "terminal_remote",
            "terminal_vox_runtime",
            "terminal_focus_gestures",
        ],
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    contract_changes: bool,
    feature_definition_changes: bool,
    prerequisite_changes: bool,
    current_feature_definition_count: usize,
    starting_feature_definitions_sha256: String,
    context_commits: BTreeMap<String, String>,
    target_minecraft_versions: BTreeMap<String, String>,
    definitions: BTreeMap<String, Definition>,
    files: Vec<SourceEvidence>,
    pure_provider_refinements: Vec<PureEvidence>,
    raw_objects: Vec<RawEvidence>,
    profile_goldens: Vec<ProfileGolden>,
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
}
#[derive(Facet)]
struct SourceEvidence {
    path: String,
    name: String,
    rules: Vec<InputVariant>,
    authored_bytes: usize,
    authored_sha256: String,
    cr_count: usize,
    lf_count: usize,
    final_lf: bool,
    raw_variants: Vec<RawEvidence>,
    witnesses: BTreeMap<String, Option<String>>,
}
#[derive(Facet)]
struct PureEvidence {
    path: String,
    name: String,
    original_rules: Vec<InputVariant>,
    proposed_rules: Vec<InputVariant>,
    java_body_unchanged: bool,
    authored_bytes: usize,
    authored_sha256: String,
    cr_count: usize,
    lf_count: usize,
    final_lf: bool,
    raw_variants: Vec<RawEvidence>,
    witnesses: BTreeMap<String, Option<String>>,
}
#[derive(Facet)]
struct ProfileGolden {
    target: String,
    profile: String,
    features: Vec<String>,
    cells: Vec<Cell>,
}
#[derive(Facet)]
struct Cell {
    path: String,
    bytes: Option<usize>,
    sha256: Option<String>,
}
struct Fixture {
    core: CoreTestFixture,
    ledger: Ledger,
    sources: BTreeMap<String, Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
fn path(name: &str) -> String {
    format!("{PREFIX}{name}.java")
}
fn rule(name: &str, targets: &[&str], all: &[&str], any: &[&str]) -> InputVariant {
    InputVariant {
        input: path(name),
        template: true,
        when: InputPredicate {
            targets: targets.iter().map(|x| (*x).to_owned()).collect(),
            all_features: all.iter().map(|x| (*x).to_owned()).collect(),
            any_features: any.iter().map(|x| (*x).to_owned()).collect(),
            none_features: vec![],
        },
    }
}
fn expected_rules(name: &str) -> Vec<InputVariant> {
    match name {
        "SFMTerminalRemoteService" | "SFMTerminalPngRenderer" => vec![rule(
            name,
            &TEN,
            &[],
            &["terminal_remote", "terminal_vox_runtime"],
        )],
        "SFMTerminalClient" => vec![rule(name, &TEN, &[], &CURRENT_PURE)],
        "SFMTerminalPanel" => vec![
            rule(name, &D2, &UI, &CURRENT_PURE),
            rule(name, &TEN[2..], &["workspace_panels"], &OLD_PURE),
        ],
        "SFMTerminalPropertiesPanel" => vec![rule(
            name,
            &D2,
            &[
                "workspace_panels",
                "workspace_widget_hosts",
                "client_actions",
                "terminal_properties",
            ],
            &[],
        )],
        "SFMTerminalRgbaRenderer" => vec![rule(
            name,
            &D2,
            &["terminal_frame_metadata"],
            &["terminal_remote", "terminal_vox_runtime"],
        )],
        _ => {
            assert!(PURE.contains(&name));
            vec![rule(name, &TEN, &[], &CURRENT_PURE)]
        }
    }
}
fn verify_raw(bytes: &[u8], oid: &str) -> Result<()> {
    let (_, size, digest) = RAW
        .iter()
        .find(|(known, _, _)| *known == oid)
        .ok_or_else(|| eyre::eyre!("unreviewed terminal presentation blob"))?;
    ensure!(
        bytes.len() == *size
            && sha256(bytes) == *digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "terminal raw identity changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn verify_source(
    name: &str,
    source: &[u8],
    bytes: usize,
    digest: &str,
    cr: usize,
    lf: usize,
    final_lf: bool,
) -> Result<()> {
    let (_, pin_bytes, pin_digest) = SOURCE_PINS
        .iter()
        .find(|(n, _, _)| *n == name)
        .ok_or_else(|| eyre::eyre!("unreviewed terminal source name"))?;
    ensure!(
        bytes == *pin_bytes
            && digest == *pin_digest
            && source.len() == bytes
            && sha256(source) == digest
            && cr == 0
            && final_lf
            && !source.contains(&b'\r')
            && source.ends_with(b"\n")
            && source.iter().filter(|b| **b == b'\n').count() == lf,
        "root must promote exact source pin for {name}"
    );
    std::str::from_utf8(source)?;
    Ok(())
}
fn profile_flags(target: &str, profile: &str) -> Vec<&'static str> {
    let profiles = if D2.contains(&target) {
        &D2_PROFILES[..]
    } else {
        &OLDER_PROFILES[..]
    };
    let mut flags = profiles
        .iter()
        .find(|(id, _)| *id == profile)
        .expect("exact reviewed profile")
        .1
        .to_vec();
    if target == "1.19.2" && profile == "full" {
        flags.push("screen_fractional_highlights");
    }
    flags
}
fn full_flags(target: &str) -> Vec<&'static str> {
    profile_flags(target, "full")
}
fn assert_cell(body: Option<&str>, cell: &Cell) {
    assert_eq!(
        body.map(str::len),
        cell.bytes,
        "current profile length: {}",
        cell.path
    );
    assert_eq!(
        body.map(|s| sha256(s.as_bytes())),
        cell.sha256,
        "current profile body: {}",
        cell.path
    );
}
impl Fixture {
    fn current_rules(name: &str) -> Vec<InputVariant> {
        if name == "SFMTerminalRemoteService" {
            vec![
                rule(
                    name,
                    &D2,
                    &[],
                    &[
                        "terminal_remote",
                        "terminal_vox_runtime",
                        "terminal_properties",
                    ],
                ),
                rule(
                    name,
                    &TEN[2..],
                    &[],
                    &["terminal_remote", "terminal_vox_runtime"],
                ),
            ]
        } else {
            expected_rules(name)
        }
    }
    fn assert_current_cell(&self, body: Option<&str>, cell: &Cell, context: &ProjectionContext) {
        let properties_interface = cell.path == path("SFMTerminalRemoteService")
            && matches!(context.minecraft_version.as_str(), "1.19.2" | "1.19.4")
            && context.features["terminal_properties"]
            && !context.features["terminal_remote"]
            && !context.features["terminal_vox_runtime"];
        if properties_interface {
            // Do not rewrite the old absence receipt. Prove the current body
            // equals the frozen remote-only golden without optional input APIs.
            assert_cell(None, cell);
            for effect in [
                "terminal_keyboard_input",
                "terminal_clipboard",
                "terminal_paste_confirmation",
            ] {
                assert!(
                    !context.features[effect],
                    "properties-only receipt cannot stand for optional input APIs"
                );
            }
            let golden = self
                .ledger
                .profile_goldens
                .iter()
                .find(|g| g.target == context.minecraft_version && g.profile == "remote_only")
                .expect("frozen remote-only profile");
            let interface = golden
                .cells
                .iter()
                .find(|c| c.path == cell.path)
                .expect("remote-only interface receipt");
            assert!(interface.bytes.is_some());
            assert_cell(body, interface);
        } else {
            assert_cell(body, cell);
        }
    }
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let (definition_bytes, historical_features) = historical_feature_registry()?;
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 2 * 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm-core-terminal-presentation-providers-slice-v2"
                && ledger.normalization == "none: exact UTF-8 LF raw Git bodies, no BOM, final LF"
                && !ledger.contract_changes
                && !ledger.feature_definition_changes
                && !ledger.prerequisite_changes
                && ledger.current_feature_definition_count == 180
                && historical_features.0.len() == 180,
            "terminal presentation scope/feature contract changed"
        );
        ensure!(
            ledger.starting_feature_definitions_sha256
                == "sha256:38d9a82444b8478e80018a51ebcbd99a959788f3ccf38237d4175455be810b83"
                && sha256(definition_bytes) == ledger.starting_feature_definitions_sha256,
            "this tranche must not change feature definitions"
        );
        ensure!(
            ledger.definitions.len() == DEFINITIONS.len()
                && ledger.context_commits
                    == CONTEXTS
                        .iter()
                        .map(|(n, c)| ((*n).to_owned(), (*c).to_owned()))
                        .collect(),
            "terminal context/definition ledger changed"
        );
        for (name, supported, requires) in DEFINITIONS {
            let actual = core.features.0.get(name).expect("existing reviewed owner");
            let saved = &ledger.definitions[name];
            ensure!(
                actual.supported_targets == supported
                    && actual.requires == requires
                    && saved.supported_targets == supported
                    && saved.requires == requires,
                "existing owner support/prerequisites changed: {name}"
            );
        }
        ensure!(
            ledger.files.len() == 6
                && ledger.pure_provider_refinements.len() == 4
                && ledger.raw_objects.len() == RAW.len(),
            "terminal source/raw inventory changed"
        );
        let oids = RAW
            .iter()
            .map(|(oid, _, _)| (*oid).to_owned())
            .collect::<BTreeSet<_>>();
        let raw = read_git_blobs(&core.repository, &oids)?;
        for (oid, size, digest) in RAW {
            verify_raw(&raw[oid], oid)?;
            let saved = ledger
                .raw_objects
                .iter()
                .find(|r| r.oid == oid)
                .expect("exact saved raw object");
            ensure!(
                saved.bytes == size && saved.sha256 == digest,
                "raw receipt changed"
            );
        }
        ensure!(
            ledger.target_minecraft_versions.len() == 10,
            "exact target alias inventory changed"
        );
        for target in TEN {
            let expected = if target == "1.21.0" { "1.21" } else { target };
            ensure!(
                ledger
                    .target_minecraft_versions
                    .get(target)
                    .map(String::as_str)
                    == Some(expected)
                    && core.context(target, &[])?.minecraft_version == expected,
                "actual catalog Minecraft version differs from reviewed target alias: {target}"
            );
        }
        let mut sources = BTreeMap::new();
        for (name, e) in NEW.iter().zip(&ledger.files) {
            ensure!(
                e.name == *name
                    && e.path == path(name)
                    && e.rules == expected_rules(name)
                    && core.metadata.source_rules.get(&e.path) == Some(&Self::current_rules(name)),
                "root must promote exact current new source rules: {name}"
            );
            ensure!(
                e.witnesses.len() == 20 && !e.raw_variants.is_empty(),
                "new witness inventory changed"
            );
            for r in &e.raw_variants {
                verify_raw(&raw[&r.oid], &r.oid)?;
                ensure!(
                    r.bytes == raw[&r.oid].len() && r.sha256 == sha256(&raw[&r.oid]),
                    "new raw receipt changed"
                );
            }
            let source = core.read_source(&e.path)?;
            verify_source(
                name,
                &source,
                e.authored_bytes,
                &e.authored_sha256,
                e.cr_count,
                e.lf_count,
                e.final_lf,
            )?;
            sources.insert(e.path.clone(), source);
        }
        for name in PURE {
            let e = ledger
                .pure_provider_refinements
                .iter()
                .find(|e| e.name == name)
                .expect("exact pure properties consumer");
            ensure!(
                e.path == path(name)
                    && e.java_body_unchanged
                    && e.original_rules == vec![rule(name, &TEN, &[], &OLD_PURE)]
                    && e.proposed_rules == expected_rules(name)
                    && core.metadata.source_rules.get(&e.path) == Some(&e.proposed_rules),
                "current pure provider union must append only properties: {name}"
            );
            ensure!(
                e.witnesses.len() == 20 && !e.raw_variants.is_empty(),
                "pure witness inventory changed"
            );
            for r in &e.raw_variants {
                verify_raw(&raw[&r.oid], &r.oid)?;
                ensure!(
                    r.bytes == raw[&r.oid].len() && r.sha256 == sha256(&raw[&r.oid]),
                    "pure raw receipt changed"
                );
            }
            let source = core.read_source(&e.path)?;
            verify_source(
                name,
                &source,
                e.authored_bytes,
                &e.authored_sha256,
                e.cr_count,
                e.lf_count,
                e.final_lf,
            )?;
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
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory())?;
        if !selected.inputs.contains_key(path) {
            ensure!(
                selected.omitted_paths.contains(path),
                "unaccounted current omission"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.sources[path])?,
            context,
        )?))
    }
    fn body(&self, name: &str, target: &str, profile: &str) -> Result<String> {
        let context = self.core.context(target, &profile_flags(target, profile))?;
        self.render(&path(name), &context)?
            .ok_or_else(|| eyre::eyre!("reviewed source unexpectedly omitted"))
    }
    fn bounded_metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|path, _| self.sources.contains_key(path));
        // Real standalone project files/target metadata are deliberately retained.
        metadata
    }
    fn write_sources(&self, root: &Path) -> Result<()> {
        for (path, bytes) in &self.sources {
            let destination = root.join(path);
            fs::create_dir_all(destination.parent().expect("fixed terminal parent"))?;
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
                    "actual standalone fixture input changed between profiles"
                );
            } else {
                fs::create_dir_all(destination.parent().expect("standalone parent"))?;
                fs::write(destination, bytes)?;
            }
        }
        Ok(())
    }
}

#[test]
fn terminal_presentation_matches_all_200_exact_frozen_tree_cells_and_18_raw_objects() -> Result<()>
{
    let f = Fixture::load()?;
    let paths = f
        .inventory()
        .iter()
        .map(|p| format!("platform/minecraft/{p}"))
        .collect::<Vec<_>>();
    let mut tree_counts = (0, 0);
    let mut new_counts = (0, 0);
    let mut pure_counts = (0, 0);
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
            output.status.success()
                && output.stdout.len() <= 16 * 1024
                && output.stderr.len() <= 4096,
            "bounded original terminal tree read failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed tree row"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|p| p == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected tree row"
            );
        }
        let mut expected = BTreeMap::new();
        for e in &f.ledger.files {
            if let Some(oid) = &e.witnesses[name] {
                expected.insert(format!("platform/minecraft/{}", e.path), oid.clone());
            }
        }
        for e in &f.ledger.pure_provider_refinements {
            if let Some(oid) = &e.witnesses[name] {
                expected.insert(format!("platform/minecraft/{}", e.path), oid.clone());
            }
        }
        assert_eq!(actual, expected, "original historical membership: {name}");
        tree_counts.0 += actual.len();
        tree_counts.1 += 10 - actual.len();
        let (_, target) = name.split_once('/').expect("fixed context");
        let flags = if name.starts_with("dev/") {
            full_flags(target)
        } else {
            vec![]
        };
        let context = f.core.context(target, &flags)?;
        for e in &f.ledger.files {
            let body = f.render(&e.path, &context)?;
            if let Some(oid) = &e.witnesses[name] {
                assert_eq!(body.expect("all-on source omitted").as_bytes(), f.raw[oid]);
                new_counts.0 += 1;
            } else {
                assert!(body.is_none());
                new_counts.1 += 1;
            }
        }
        for e in &f.ledger.pure_provider_refinements {
            let body = f.render(&e.path, &context)?;
            if let Some(oid) = &e.witnesses[name] {
                assert_eq!(body.expect("pure source omitted").as_bytes(), f.raw[oid]);
                pure_counts.0 += 1;
            } else {
                assert!(body.is_none());
                pure_counts.1 += 1;
            }
        }
    }
    assert_eq!(tree_counts, (84, 116));
    assert_eq!(new_counts, (44, 76));
    assert_eq!(pure_counts, (40, 40));
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

#[test]
fn terminal_presentation_84_current_profiles_preserve_504_real_renderer_cells() -> Result<()> {
    let f = Fixture::load()?;
    ensure!(
        f.ledger.profile_goldens.len() == 84,
        "golden profile inventory changed"
    );
    let mut seen = BTreeSet::new();
    let mut counts = (0, 0);
    for golden in &f.ledger.profile_goldens {
        ensure!(
            seen.insert((golden.target.clone(), golden.profile.clone())),
            "duplicate golden profile"
        );
        let flags = profile_flags(&golden.target, &golden.profile);
        assert_eq!(golden.features, flags);
        let context = f.core.context(&golden.target, &flags)?;
        let mut description = context.clone();
        description.environment = "release".to_owned();
        description.preset = "terminal-description-not-owner".to_owned();
        description.projection_key = "independent/terminal-presentation".to_owned();
        ensure!(golden.cells.len() == 6, "golden cell inventory changed");
        for (name, cell) in NEW.iter().zip(&golden.cells) {
            assert_eq!(cell.path, path(name));
            let body = f.render(&cell.path, &context)?;
            f.assert_current_cell(body.as_deref(), cell, &context);
            assert_eq!(body, f.render(&cell.path, &description)?);
            if body.is_some() {
                counts.0 += 1;
            } else {
                counts.1 += 1;
            }
        }
    }
    // Six properties-only/UI/lookup cells across the two legacy targets now
    // select the existing interface; all frozen cell receipts remain unchanged.
    assert_eq!(counts, (234, 270));
    Ok(())
}

#[test]
fn existing_terminal_feature_contracts_refuse_missing_prerequisites_and_unsupported_flags()
-> Result<()> {
    let f = Fixture::load()?;
    let mut refusals = 0;
    for target in D2 {
        for (owner, profile) in [
            ("terminal_properties", "properties_ui"),
            ("terminal_presentation_actions", "remote_presentation"),
            ("terminal_tuning_actions", "remote_tuning"),
            ("terminal_server_lifecycle", "remote_lifecycle"),
            ("terminal_repl_actions", "local_repl"),
            ("terminal_host_escape", "local_host_escape"),
        ] {
            let flags = profile_flags(target, profile);
            for prerequisite in &f.core.features.0[owner].requires {
                let missing = flags
                    .iter()
                    .copied()
                    .filter(|flag| *flag != prerequisite.as_str())
                    .collect::<Vec<_>>();
                assert!(
                    f.core.context(target, &missing).is_err(),
                    "missing actual prerequisite {owner}:{prerequisite}"
                );
                refusals += 1;
            }
        }
        // A properties flag with no genuine workspace prerequisite is refused;
        // it is never repaired by an inferred blanket prerequisite closure.
        assert!(f.core.context(target, &["terminal_properties"]).is_err());
        for owner in CURRENT_PURE
            .into_iter()
            .chain(UI)
            .chain(["terminal_frame_metadata"])
        {
            let mut unknown = f.core.context(target, &full_flags(target))?;
            assert!(unknown.features.remove(owner).is_some());
            assert!(select_core_inputs(&f.core.metadata, &unknown, &f.inventory()).is_err());
        }
        // UI selector omissions are valid profiles, not prerequisite additions.
        for missing in ["workspace_widget_hosts", "client_actions"] {
            let flags = profile_flags(target, "properties_ui")
                .into_iter()
                .filter(|f| *f != missing)
                .collect::<Vec<_>>();
            let context = f.core.context(target, &flags)?;
            assert!(f.render(&path("SFMTerminalPanel"), &context)?.is_none());
            assert!(
                f.render(&path("SFMTerminalPropertiesPanel"), &context)?
                    .is_none()
            );
            for name in PURE {
                assert!(f.render(&path(name), &context)?.is_some());
            }
        }
    }
    assert_eq!(refusals, 22);
    for target in &TEN[2..] {
        for flags in [
            &["terminal_frame_metadata"][..],
            &["terminal_clipboard"][..],
            &["terminal_keyboard_input"][..],
            &["workspace_panels", "terminal_properties"][..],
            &[
                "client_actions",
                "workspace_panels",
                "terminal_remote",
                "terminal_tuning_actions",
            ][..],
        ] {
            assert!(f.core.context(target, flags).is_err());
        }
    }
    assert!(
        f.core
            .context("1.19.4", &["screen_fractional_highlights"])
            .is_err()
    );
    Ok(())
}

#[test]
fn local_only_and_independent_input_clipboard_metadata_focus_lifecycle_members_remain_real()
-> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        let local = f.body("SFMTerminalPanel", target, "local_ui")?;
        for anchor in [
            "new SFMTerminalClient(service)",
            "new SFMTerminalScrollback()",
            "widgetHost",
            "scrollback.visibleLineEntries()",
            "TerminalViewportWidget",
        ] {
            assert!(
                local.contains(anchor),
                "genuine local path anchor: {anchor}"
            );
        }
        for forbidden in [
            "SFMTerminalRemoteService",
            "SFMTerminalPngRenderer",
            "SFMTerminalRgbaRenderer",
            "SFMTerminalServiceFactory",
            "SFMTerminalFrameMetadata",
            "pasteWithGuard(",
            "sendMouse(",
            "PendingPaste",
            "focusSequence",
            "executeForAutomation(",
        ] {
            assert!(
                !local.contains(forbidden),
                "local-only leaked optional provider {forbidden}"
            );
        }
        let local_context = f.core.context(target, &profile_flags(target, "local_ui"))?;
        for (name, all, anchor) in [
            (
                "SFMPanelWidget",
                &["workspace_panels", "workspace_widget_hosts"][..],
                "ResourceLocation keyboardUsageSituationId();",
            ),
            (
                "SFMPanelWidgetHost",
                &["workspace_panels", "workspace_widget_hosts"][..],
                "public void setFocusStateListener(",
            ),
            (
                "SFMPanelActionButton",
                &["workspace_panels", "workspace_widget_hosts"][..],
                "class SFMPanelActionButton",
            ),
            (
                "SFMPanelActionExecution",
                &["client_actions", "workspace_panels"][..],
                "public static boolean execute(",
            ),
        ] {
            let p = format!("src/main/java/ca/teamdman/sfm/client/screen/workspace/{name}.java");
            let mut expected = rule(name, &D2, all, &[]);
            expected.input = p.clone();
            assert_eq!(f.core.metadata.source_rules.get(&p), Some(&vec![expected]));
            let selected = select_core_inputs(
                &f.core.metadata,
                &local_context,
                &BTreeSet::from([p.clone()]),
            )?;
            assert!(
                selected.inputs.contains_key(&p),
                "real selected UI peer {name}"
            );
            let source = f.core.read_source(&p)?;
            let body = render_java_source(std::str::from_utf8(&source)?, &local_context)?;
            assert!(body.contains(anchor), "genuine UI API anchor {name}");
            if name == "SFMPanelActionExecution" {
                assert!(!body.contains("SFMActionChoice") && !body.contains("executeAction("));
            }
        }
        let no_input = f.body("SFMTerminalPanel", target, "remote_ui")?;
        let input = f.body("SFMTerminalPanel", target, "remote_input")?;
        for token in [
            "remoteService.sendKey(",
            "remoteService.sendText(",
            "remoteService.sendMouse(",
        ] {
            assert!(!no_input.contains(token));
            assert!(input.contains(token));
        }
        let clipboard = f.body("SFMTerminalPanel", target, "remote_clipboard")?;
        assert!(
            clipboard.contains("remoteService.copySelection(")
                && clipboard.contains("remoteService.pasteWithGuard(")
        );
        for token in [
            "sendMouse(",
            "sendKey(",
            "sendText(",
            "PendingPaste",
            "pasteWithoutGuard(",
            "SFMTerminalPasteConfirmationScreen",
        ] {
            assert!(!clipboard.contains(token));
        }
        assert!(clipboard.contains("SFMTerminalPasteResult.Disposition.PASTED"));
        let confirmed = f.body("SFMTerminalPanel", target, "remote_confirmation")?;
        for token in [
            "PendingPaste",
            "SFMTerminalPasteConfirmationScreen",
            "remoteService.pasteWithoutGuard(",
        ] {
            assert!(confirmed.contains(token));
        }
        assert!(!confirmed.contains("sendMouse("));
        let remote_api = f.body("SFMTerminalRemoteService", target, "remote_clipboard")?;
        assert!(remote_api.contains("copySelection(") && remote_api.contains("pasteWithGuard("));
        for token in ["sendMouse(", "sendKey(", "sendText(", "pasteWithoutGuard("] {
            assert!(!remote_api.contains(token));
        }
        assert!(
            f.body("SFMTerminalRemoteService", target, "remote_confirmation")?
                .contains("pasteWithoutGuard(")
        );
        let metadata = f.body("SFMTerminalPanel", target, "remote_metadata")?;
        for token in [
            "SFMTerminalRgbaRenderer",
            "frame.metadata()",
            "frame.streamIdentity()",
        ] {
            assert!(metadata.contains(token));
        }
        for token in [
            "SFMTerminalRgbaRenderer",
            "frame.metadata()",
            "frame.streamIdentity()",
        ] {
            assert!(!no_input.contains(token));
        }
        let png_off = f.body("SFMTerminalPngRenderer", target, "remote_ui")?;
        let png_on = f.body("SFMTerminalPngRenderer", target, "remote_metadata")?;
        assert!(
            !png_off.contains("frame.streamIdentity()")
                && !png_off.contains("streamIdentity = frame")
        );
        assert!(
            png_off.contains("frame.sequence() == currentSequence")
                && !png_off.contains("frame.sequence() < currentSequence")
        );
        assert!(
            png_on.contains("frame.sequence() < currentSequence")
                && png_on.contains("frame.streamIdentity()")
        );
        let focus = f.body("SFMTerminalPanel", target, "local_focus")?;
        assert!(
            focus.contains("focusSequence")
                && focus.contains("SFMTerminalFocusSequence.Decision.EXIT")
        );
        assert!(!focus.contains("Decision.HOST_ESCAPE"));
        let host = f.body("SFMTerminalPanel", target, "local_host_escape")?;
        assert!(host.contains("Decision.HOST_ESCAPE") && !host.contains("Decision.EXIT"));
        assert!(
            !no_input.contains("SFMTerminalServiceFactory") && !no_input.contains("new Thread(")
        );
        assert!(no_input.contains("return RustLifecycleRequest.UNAVAILABLE;"));
        let lifecycle = f.body("SFMTerminalPanel", target, "remote_lifecycle")?;
        assert!(
            lifecycle.contains("SFMTerminalServiceFactory") && lifecycle.contains("new Thread(")
        );
        assert!(!no_input.contains("new SFMPanelActionButton(PRESENTATION_ELEMENT"));
        assert!(
            f.body("SFMTerminalPanel", target, "remote_presentation")?
                .contains("presentationButton")
        );
        assert!(!local.contains("executeForAutomation("));
        assert!(
            f.body("SFMTerminalPanel", target, "local_repl")?
                .contains("executeForAutomation(")
        );
        let properties = f.body("SFMTerminalPropertiesPanel", target, "properties_ui")?;
        assert!(
            properties.contains("return Optional.empty();")
                && !properties.contains("context.panel(")
        );
        assert!(!properties.contains("public SFMTerminalTuningChangeResult requestTuning("));
        assert!(
            f.body("SFMTerminalPropertiesPanel", target, "properties_lookup")?
                .contains("context.panel(")
        );
        assert!(
            f.body("SFMTerminalPropertiesPanel", target, "properties_controls")?
                .contains("public SFMTerminalTuningChangeResult requestTuning(")
        );
    }
    let fractional_off = f.body("SFMTerminalPanel", "1.19.2", "remote_full_input")?;
    assert!(
        fractional_off.contains("SFMScreenRenderUtils.renderHighlight(poseStack, 0, 0, 1, 1);")
    );
    assert!(fractional_off.contains("poseStack.scale("));
    let all_on = f.body("SFMTerminalPanel", "1.19.2", "full")?;
    assert!(!all_on.contains("SFMScreenRenderUtils.renderHighlight(poseStack, 0, 0, 1, 1);"));
    for target in &TEN[2..] {
        let local = f.body("SFMTerminalPanel", target, "local_ui")?;
        for token in [
            "SFMTerminalRemoteService",
            "SFMTerminalPngRenderer",
            "SFMTerminalFocusSequence",
        ] {
            assert!(!local.contains(token));
        }
    }
    Ok(())
}

#[test]
fn current_properties_only_four_carrier_union_is_not_historical_fixture_or_effect_authority()
-> Result<()> {
    let f = Fixture::load()?;
    let metadata = f.bounded_metadata();
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    let mut current_pure_bodies = 0;
    for target in D2 {
        let flags = ["workspace_panels", "terminal_properties"];
        let context = f.core.context(target, &flags)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selection = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selection, &context)?;
        assert_eq!(
            NEW.iter()
                .filter(|name| artifacts.contains_key(&path(name)))
                .count(),
            2
        );
        assert_eq!(
            PURE.iter()
                .filter(|name| artifacts.contains_key(&path(name)))
                .count(),
            4
        );
        for name in PURE {
            assert_eq!(
                f.core.metadata.source_rules[&path(name)],
                expected_rules(name)
            );
            let e = f
                .ledger
                .pure_provider_refinements
                .iter()
                .find(|e| e.name == name)
                .expect("current carrier");
            let oid = e.witnesses[&format!("dev/{target}")]
                .as_ref()
                .expect("original raw carrier");
            let output = std::str::from_utf8(&artifacts[&path(name)].output_bytes)?;
            let (_, body) = output.split_once('\n').expect("current generated banner");
            assert_eq!(
                body.as_bytes(),
                f.raw[oid],
                "current actual properties carrier {name}"
            );
            assert_eq!(Some(body.to_owned()), f.render(&path(name), &context)?);
            current_pure_bodies += 1;
        }
        for effect in EFFECTS {
            assert!(
                !context.features[effect],
                "pure consumer enabled effect {effect}"
            );
        }
        let interface_context = f.core.context(target, &["terminal_remote"])?;
        assert_eq!(
            f.render(&path("SFMTerminalRemoteService"), &context)?,
            f.render(&path("SFMTerminalRemoteService"), &interface_context)?,
            "properties fallback reuses the real interface without enabling a remote effect"
        );
        assert!(!context.features["terminal_remote"] && !context.features["terminal_vox_runtime"]);
        for name in [
            "SFMTerminalPngRenderer",
            "SFMTerminalRgbaRenderer",
            "SFMTerminalPanel",
            "SFMTerminalPropertiesPanel",
        ] {
            assert!(!artifacts.contains_key(&path(name)));
        }
        let omitted = f.core.context(target, &["workspace_panels"])?;
        f.copy_project_inputs(&root, &metadata, &omitted)?;
        let no_owner = select_core_inputs(&metadata, &omitted, &inventory)?;
        let no_artifacts = collect_core_artifacts(&root, &no_owner, &omitted)?;
        for name in PURE {
            assert!(!no_artifacts.contains_key(&path(name)));
        }
        assert!(f.core.context(target, &["terminal_properties"]).is_err());
        let mut absent_name = context.clone();
        assert!(absent_name.features.remove("terminal_properties").is_some());
        assert!(select_core_inputs(&metadata, &absent_name, &inventory).is_err());
        // Actual existing typed availability for properties diagnostics, with
        // no invented metadata methods or implicit backend/remote enablement.
        for name in ["SFMTerminalFrame", "SFMTerminalImageLayout"] {
            let p = path(name);
            let selected =
                select_core_inputs(&f.core.metadata, &context, &BTreeSet::from([p.clone()]))?;
            assert!(
                selected.inputs.contains_key(&p),
                "genuine existing properties typed provider"
            );
            let source = f.core.read_source(&p)?;
            let body = render_java_source(std::str::from_utf8(&source)?, &context)?;
            assert!(!body.contains("{%"));
        }
        let ui = f
            .core
            .context(target, &profile_flags(target, "properties_ui"))?;
        let panel = f
            .render(&path("SFMTerminalPanel"), &ui)?
            .expect("real properties-local Panel");
        assert!(panel.contains("Optional<SFMTerminalPropertiesSnapshot.AcceptedFrame> acceptedFrame = Optional.empty();"));
        assert!(!panel.contains("SFMTerminalPropertiesSnapshot.fromFrame("));
        assert!(
            !panel.contains("SFMTerminalRemoteService")
                && !panel.contains("SFMTerminalRgbaRenderer")
        );
    }
    assert_eq!(current_pure_bodies, 8);
    let mut union_counts = (0, 0);
    for target in D2 {
        for bits in 0_u32..16 {
            let mut flags = CURRENT_PURE
                .iter()
                .enumerate()
                .filter(|(index, _)| bits & (1_u32 << *index) != 0)
                .map(|(_, owner)| *owner)
                .collect::<Vec<_>>();
            if flags.contains(&"terminal_properties") {
                flags.push("workspace_panels");
            }
            // Unlike a counterfactual predicate-only table, every row is a
            // valid explicit profile with genuine existing prerequisites.
            let context = f.core.context(target, &flags)?;
            for name in PURE {
                let body = f.render(&path(name), &context)?;
                assert_eq!(body.is_some(), bits != 0);
                if body.is_some() {
                    union_counts.0 += 1;
                } else {
                    union_counts.1 += 1;
                }
            }
            for effect in EFFECTS {
                assert!(!context.features[effect]);
            }
            for name in ["SFMTerminalPanel", "SFMTerminalPropertiesPanel"] {
                assert!(f.render(&path(name), &context)?.is_none());
            }
        }
    }
    assert_eq!(union_counts, (120, 8));
    Ok(())
}

#[test]
fn real_collector_all_current_profiles_match_body_counts_and_exact_renderer_outputs() -> Result<()>
{
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let metadata = f.bounded_metadata();
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    let mut counts = (0, 0);
    for golden in &f.ledger.profile_goldens {
        let context = f.core.context(
            &golden.target,
            &profile_flags(&golden.target, &golden.profile),
        )?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selection = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selection, &context)?;
        for cell in &golden.cells {
            let body = artifacts.get(&cell.path).map(|artifact| {
                assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{}", cell.path));
                assert!(artifact.overlay.is_none());
                std::str::from_utf8(&artifact.output_bytes)
                    .expect("valid rendered Java")
                    .split_once('\n')
                    .expect("actual generated banner")
                    .1
            });
            f.assert_current_cell(body, cell, &context);
            assert_eq!(body.map(str::to_owned), f.render(&cell.path, &context)?);
            if body.is_some() {
                counts.0 += 1;
            } else {
                counts.1 += 1;
            }
        }
        for name in PURE {
            let actual = artifacts.get(&path(name)).map(|a| {
                std::str::from_utf8(&a.output_bytes)
                    .expect("pure UTF-8")
                    .split_once('\n')
                    .expect("actual pure banner")
                    .1
                    .to_owned()
            });
            assert_eq!(actual, f.render(&path(name), &context)?);
        }
    }
    assert_eq!(counts, (234, 270));
    Ok(())
}

#[test]
fn collector_omits_malformed_off_inputs_and_refuses_selected_corruption_without_template_authority()
-> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    for source in f.inventory() {
        let destination = root.join(source);
        fs::create_dir_all(destination.parent().expect("fixed terminal parent"))?;
        fs::write(
            destination,
            b"{% if features.unreviewed_effect_owner %}\n\xff",
        )?;
    }
    let mut metadata = f.bounded_metadata();
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    for target in D2 {
        let off = f.core.context(target, &[])?;
        f.copy_project_inputs(&root, &metadata, &off)?;
        let selection = select_core_inputs(&metadata, &off, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selection, &off)?;
        for source in f.inventory() {
            assert!(!artifacts.contains_key(&source));
        }
        let properties = f
            .core
            .context(target, &["workspace_panels", "terminal_properties"])?;
        f.copy_project_inputs(&root, &metadata, &properties)?;
        let selection = select_core_inputs(&metadata, &properties, &inventory)?;
        assert!(collect_core_artifacts(&root, &selection, &properties).is_err());
    }
    f.write_sources(&root)?;
    for rules in metadata.source_rules.values_mut() {
        for rule in rules {
            rule.template = false;
        }
    }
    let probe_path = path("SFMTerminalClient");
    let original = std::str::from_utf8(&f.sources[&probe_path])?;
    let probe = format!(
        "{original}{{% if features.terminal_properties %}}\n// Isolated current properties Java renderer probe.\n{{% endif %}}\n"
    );
    fs::write(root.join(&probe_path), &probe)?;
    for target in D2 {
        let context = f
            .core
            .context(target, &["workspace_panels", "terminal_properties"])?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selection = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selection, &context)?;
        let body = std::str::from_utf8(&artifacts[&probe_path].output_bytes)?
            .split_once('\n')
            .expect("Java generated banner")
            .1;
        assert_eq!(body, render_java_source(&probe, &context)?);
        assert!(
            body.contains("// Isolated current properties Java renderer probe.")
                && !body.contains("{%")
        );
    }
    let context = f
        .core
        .context("1.19.2", &["workspace_panels", "terminal_properties"])?;
    let selection = select_core_inputs(&metadata, &context, &inventory)?;
    // Missing bytes after selection refuse; this never deletes a user/live file.
    let owned_probe = root.join(&probe_path);
    let retained =
        root.join("src/main/java/ca/teamdman/sfm/client/terminal/retained-owned-probe.txt");
    fs::rename(&owned_probe, &retained)?;
    assert!(collect_core_artifacts(&root, &selection, &context).is_err());
    fs::rename(&retained, &owned_probe)?;
    for corrupt in [
        "{% if features.unreviewed_effect_owner %}\nclass Broken {}\n{% endif %}\n",
        "{% if environment %}\nclass Broken {}\n{% endif %}\n",
    ] {
        fs::write(&owned_probe, corrupt)?;
        assert!(collect_core_artifacts(&root, &selection, &context).is_err());
    }
    Ok(())
}

#[test]
fn common_authored_presentation_edits_reach_all_44_outputs_without_live_mutation() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut edited = BTreeMap::new();
    for name in NEW {
        let p = path(name);
        let source = std::str::from_utf8(&f.sources[&p])?;
        // One shared authored hunk, once. No per-version package/class copy.
        assert_eq!(
            source
                .matches("package ca.teamdman.sfm.client.terminal;")
                .count(),
            1
        );
        let changed = source.replacen("package ca.teamdman.sfm.client.terminal;",
            "package ca.teamdman.sfm.client.terminal;\n// Isolated common terminal presentation edit.", 1);
        assert_ne!(changed, source);
        fs::write(root.join(&p), &changed)?;
        edited.insert(p, changed);
    }
    let metadata = f.bounded_metadata();
    let inventory = discover_core_source_files(&root)?;
    let mut changed_outputs = 0;
    for target in TEN {
        let context = f.core.context(target, &full_flags(target))?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selection = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selection, &context)?;
        for name in NEW {
            let p = path(name);
            let expected = D2.contains(&target)
                || !matches!(
                    name,
                    "SFMTerminalPropertiesPanel" | "SFMTerminalRgbaRenderer"
                );
            assert_eq!(artifacts.contains_key(&p), expected);
            if expected {
                let body = std::str::from_utf8(&artifacts[&p].output_bytes)?
                    .split_once('\n')
                    .expect("actual common edit banner")
                    .1;
                assert_eq!(body, render_java_source(&edited[&p], &context)?);
                assert!(body.contains("// Isolated common terminal presentation edit."));
                changed_outputs += 1;
            }
        }
    }
    assert_eq!(changed_outputs, 44);
    for (p, source) in &f.sources {
        assert_eq!(f.core.read_source(p)?, *source);
    }
    Ok(())
}
