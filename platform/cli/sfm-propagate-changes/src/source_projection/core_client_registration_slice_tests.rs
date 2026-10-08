//! Post-promotion entrypoint/member contracts for optional client registrations.
//!
//! This validates source selection and exact raw/expanded witnesses, not the
//! Java dependency closure of the independently guarded registrar classes.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_client_registration_current_contract::reviewed_pre_workspace_registrar;
use super::core_inputs::CoreProjectInputs;
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
use std::fs;

const MAIN: &str = "src/main/java/ca/teamdman/sfm/SFM.java";
const CLIENT: &str = "src/main/java/ca/teamdman/sfm/client/SFMClientRegistrations.java";
const LEDGER: &str = "docs/tasks/sfm-core-client-registration-slice.json";
const BASIC: &str = "client_actions";
const CC: &str = "computercraft";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const UNION: [&str; 30] = [
    "canvas_text_editor",
    "client_actions",
    "client_manager_gui",
    "client_overlay_scenes",
    "client_program_consent",
    "client_program_reads",
    "command_history",
    "context_actions",
    "developer_tools",
    "document_history",
    "explorer_compaction",
    "explorer_search",
    "file_explorer",
    "icon_rules",
    "java_symbols",
    "keyboard_profiles",
    "manager_editor_actions",
    "multiplayer_packets",
    "packet_actions",
    "registry_explorer",
    "release_review",
    "review_sessions",
    "route_comparison",
    "spatial_coverage",
    "tooltip_mode_override",
    "touch_display_terminal_mount",
    "trajectory_panels",
    "workspace_counterfactuals",
    "workspace_lifecycle",
    "workspace_panels",
];
const TEMPLATES: [(&str, &str, usize); 2] = [
    (
        MAIN,
        "sha256:ca54cff14668e28ac0ad872f1b94e25068bf2b5dd23e4556e328eee99c773d45",
        16396,
    ),
    (
        CLIENT,
        "sha256:54cf007cb986311b9a831aaee9ad91612202b36cf23a1b8ac9a7a5824976bdfb",
        11404,
    ),
];
const COMMITS: [(&str, &str); 20] = [
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
const WITNESSES: [(&str, &str, Option<&str>); 40] = [
    (
        MAIN,
        "dev/1.19.2",
        Some("6406a80b03e152fb7623a85b3440fc1c89b8ad64"),
    ),
    (
        MAIN,
        "dev/1.19.4",
        Some("2cf8c7dd945f92669b28b2f17becf2d05bbc2d3b"),
    ),
    (
        MAIN,
        "dev/1.20",
        Some("e8e0292965dbbd3828f13cc17aadf2fa697cca4d"),
    ),
    (
        MAIN,
        "dev/1.20.1",
        Some("e8e0292965dbbd3828f13cc17aadf2fa697cca4d"),
    ),
    (
        MAIN,
        "dev/1.20.2",
        Some("2962338b95949b93e460b1e182f333cd4489007b"),
    ),
    (
        MAIN,
        "dev/1.20.3",
        Some("2962338b95949b93e460b1e182f333cd4489007b"),
    ),
    (
        MAIN,
        "dev/1.20.4",
        Some("56b91be1759a28f4ad093a370b4f6f9f9c051c27"),
    ),
    (
        MAIN,
        "dev/1.21.0",
        Some("b4756abd5eebe7862a745458c22c579cb8bf3080"),
    ),
    (
        MAIN,
        "dev/1.21.1",
        Some("fc23bd2b9637ae35f65e94e7e988107abe59effb"),
    ),
    (
        MAIN,
        "dev/26.1.2",
        Some("596cd22da4cd2e737394ae2246107c6c04fdc35a"),
    ),
    (
        MAIN,
        "release/1.19.2",
        Some("6520ceec4ec97a4ffcc0b185f49eb62d960acdf9"),
    ),
    (
        MAIN,
        "release/1.19.4",
        Some("6520ceec4ec97a4ffcc0b185f49eb62d960acdf9"),
    ),
    (
        MAIN,
        "release/1.20",
        Some("82258c4b7d37ffe5a41bb142ad39b5a024cd2a52"),
    ),
    (
        MAIN,
        "release/1.20.1",
        Some("82258c4b7d37ffe5a41bb142ad39b5a024cd2a52"),
    ),
    (
        MAIN,
        "release/1.20.2",
        Some("bd82ab073fc6d6842db251473514f4bc259ca326"),
    ),
    (
        MAIN,
        "release/1.20.3",
        Some("bd82ab073fc6d6842db251473514f4bc259ca326"),
    ),
    (
        MAIN,
        "release/1.20.4",
        Some("9d028832f73e68cc2e29bfec2bf9da2ef94fa0de"),
    ),
    (
        MAIN,
        "release/1.21.0",
        Some("0140f70be0ae1a680bc3bb3aa99434ba4fd81cef"),
    ),
    (
        MAIN,
        "release/1.21.1",
        Some("0140f70be0ae1a680bc3bb3aa99434ba4fd81cef"),
    ),
    (
        MAIN,
        "release/26.1.2",
        Some("7d5ea7ab90ff17cd5e4d0af8d34b42c661299174"),
    ),
    (
        CLIENT,
        "dev/1.19.2",
        Some("d7f6e317a66ad89d2b871c2dd4ebbc05f350b07a"),
    ),
    (
        CLIENT,
        "dev/1.19.4",
        Some("53ab744eb88feca2ae09e78acab5af151af00537"),
    ),
    (
        CLIENT,
        "dev/1.20",
        Some("d450476334bcd5135c05347ac2af12321204e84e"),
    ),
    (
        CLIENT,
        "dev/1.20.1",
        Some("d450476334bcd5135c05347ac2af12321204e84e"),
    ),
    (
        CLIENT,
        "dev/1.20.2",
        Some("218f27cc371e6b0dbc8ff29c0a2f00f45e40952d"),
    ),
    (
        CLIENT,
        "dev/1.20.3",
        Some("218f27cc371e6b0dbc8ff29c0a2f00f45e40952d"),
    ),
    (
        CLIENT,
        "dev/1.20.4",
        Some("218f27cc371e6b0dbc8ff29c0a2f00f45e40952d"),
    ),
    (
        CLIENT,
        "dev/1.21.0",
        Some("0c3fdb54b795885e0b32e95a74eca3a9185cee24"),
    ),
    (
        CLIENT,
        "dev/1.21.1",
        Some("0c3fdb54b795885e0b32e95a74eca3a9185cee24"),
    ),
    (
        CLIENT,
        "dev/26.1.2",
        Some("0c3fdb54b795885e0b32e95a74eca3a9185cee24"),
    ),
    (CLIENT, "release/1.19.2", None),
    (CLIENT, "release/1.19.4", None),
    (CLIENT, "release/1.20", None),
    (CLIENT, "release/1.20.1", None),
    (CLIENT, "release/1.20.2", None),
    (CLIENT, "release/1.20.3", None),
    (CLIENT, "release/1.20.4", None),
    (CLIENT, "release/1.21.0", None),
    (CLIENT, "release/1.21.1", None),
    (CLIENT, "release/26.1.2", None),
];
const FULL_MASKS: [(&str, &[&str]); 10] = [
    (
        "1.19.2",
        &[
            "canvas_text_editor",
            "client_actions",
            "client_manager_gui",
            "client_overlay_scenes",
            "client_program_consent",
            "client_program_reads",
            "command_history",
            "context_actions",
            "developer_tools",
            "document_history",
            "explorer_compaction",
            "explorer_search",
            "file_explorer",
            "icon_rules",
            "java_symbols",
            "keyboard_profiles",
            "manager_editor_actions",
            "multiplayer_packets",
            "packet_actions",
            "registry_explorer",
            "release_review",
            "review_sessions",
            "route_comparison",
            "spatial_coverage",
            "tooltip_mode_override",
            "touch_display_terminal_mount",
            "trajectory_panels",
            "workspace_counterfactuals",
            "workspace_lifecycle",
            "workspace_panels",
            "computercraft",
        ],
    ),
    (
        "1.19.4",
        &[
            "canvas_text_editor",
            "client_actions",
            "client_overlay_scenes",
            "client_program_consent",
            "client_program_reads",
            "command_history",
            "context_actions",
            "developer_tools",
            "document_history",
            "explorer_compaction",
            "explorer_search",
            "file_explorer",
            "icon_rules",
            "java_symbols",
            "keyboard_profiles",
            "manager_editor_actions",
            "multiplayer_packets",
            "packet_actions",
            "registry_explorer",
            "release_review",
            "review_sessions",
            "route_comparison",
            "spatial_coverage",
            "tooltip_mode_override",
            "touch_display_terminal_mount",
            "trajectory_panels",
            "workspace_counterfactuals",
            "workspace_lifecycle",
            "workspace_panels",
            "computercraft",
        ],
    ),
    (
        "1.20",
        &[
            "canvas_text_editor",
            "client_actions",
            "developer_tools",
            "manager_editor_actions",
            "workspace_panels",
            "computercraft",
        ],
    ),
    (
        "1.20.1",
        &[
            "canvas_text_editor",
            "client_actions",
            "developer_tools",
            "manager_editor_actions",
            "workspace_panels",
            "computercraft",
        ],
    ),
    (
        "1.20.2",
        &[
            "canvas_text_editor",
            "client_actions",
            "developer_tools",
            "manager_editor_actions",
            "workspace_panels",
            "computercraft",
        ],
    ),
    (
        "1.20.3",
        &[
            "canvas_text_editor",
            "client_actions",
            "developer_tools",
            "manager_editor_actions",
            "workspace_panels",
            "computercraft",
        ],
    ),
    (
        "1.20.4",
        &[
            "canvas_text_editor",
            "client_actions",
            "developer_tools",
            "manager_editor_actions",
            "workspace_panels",
            "computercraft",
        ],
    ),
    (
        "1.21.0",
        &[
            "canvas_text_editor",
            "client_actions",
            "developer_tools",
            "manager_editor_actions",
            "workspace_panels",
            "computercraft",
        ],
    ),
    (
        "1.21.1",
        &[
            "canvas_text_editor",
            "client_actions",
            "developer_tools",
            "manager_editor_actions",
            "workspace_panels",
            "computercraft",
        ],
    ),
    (
        "26.1.2",
        &[
            "canvas_text_editor",
            "client_actions",
            "developer_tools",
            "manager_editor_actions",
            "workspace_panels",
            "computercraft",
        ],
    ),
];
struct Golden {
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
    lf: usize,
}
static GOLDENS: [Golden; 19] = [
    Golden {
        oid: "6406a80b03e152fb7623a85b3440fc1c89b8ad64",
        digest: "sha256:1bb298097135a17083707685ca214ca58cd04e78baed35281de03fea75aa5009",
        bytes: 2564,
        lf: 81,
    },
    Golden {
        oid: "2cf8c7dd945f92669b28b2f17becf2d05bbc2d3b",
        digest: "sha256:8589da28a9b50a22435e57c02502f48ed10a17b95ff49d2f8c26114722957603",
        bytes: 2476,
        lf: 78,
    },
    Golden {
        oid: "e8e0292965dbbd3828f13cc17aadf2fa697cca4d",
        digest: "sha256:c84f2b0e4c073f15e4d72b9d995663eb192ac1913af98fc5b68765d48de34023",
        bytes: 2516,
        lf: 80,
    },
    Golden {
        oid: "2962338b95949b93e460b1e182f333cd4489007b",
        digest: "sha256:6c1d2f084099e90a88b4d86628574ecb1bf21c9671ac7c686176f29e0f572c21",
        bytes: 2390,
        lf: 79,
    },
    Golden {
        oid: "56b91be1759a28f4ad093a370b4f6f9f9c051c27",
        digest: "sha256:8da3956ce52c38a8c319cc6f29f8117f678e0c5b682849aec8c6156b8cf278f1",
        bytes: 2248,
        lf: 74,
    },
    Golden {
        oid: "b4756abd5eebe7862a745458c22c579cb8bf3080",
        digest: "sha256:98d96832ee530dac9633d85242cbd4cb52f06297d6045188b5747003ef9002c6",
        bytes: 2183,
        lf: 74,
    },
    Golden {
        oid: "fc23bd2b9637ae35f65e94e7e988107abe59effb",
        digest: "sha256:df4bc76a5a20528bb51e1ecf822b214f73272afb36ea4753083b851a2c9a06b0",
        bytes: 2290,
        lf: 76,
    },
    Golden {
        oid: "596cd22da4cd2e737394ae2246107c6c04fdc35a",
        digest: "sha256:253c6a2ba27c58326b6ae25415f8003b7b7f86d2c9df0f1e677b9616e877cb34",
        bytes: 2182,
        lf: 73,
    },
    Golden {
        oid: "6520ceec4ec97a4ffcc0b185f49eb62d960acdf9",
        digest: "sha256:a7271e5891d5c019c1142ed9f22fa0fc0061d0c41aa19a46706f0556624ae02e",
        bytes: 2329,
        lf: 74,
    },
    Golden {
        oid: "82258c4b7d37ffe5a41bb142ad39b5a024cd2a52",
        digest: "sha256:e176aa88ffb70de8c3b854ffe48934151d8c24db6ab50942568e735de891ed70",
        bytes: 2369,
        lf: 76,
    },
    Golden {
        oid: "bd82ab073fc6d6842db251473514f4bc259ca326",
        digest: "sha256:3b859324b4ea2a06b30ac5cce8749cf3f3001e04819c7ea0c17681491b275128",
        bytes: 2344,
        lf: 76,
    },
    Golden {
        oid: "9d028832f73e68cc2e29bfec2bf9da2ef94fa0de",
        digest: "sha256:66b1e04f38aa6baee61b0fa6c7cb19e4be3c2ed7003da67b68949fbadcdbbcc6",
        bytes: 2096,
        lf: 70,
    },
    Golden {
        oid: "0140f70be0ae1a680bc3bb3aa99434ba4fd81cef",
        digest: "sha256:46c69119ff3173e713f02fff7b2532266e440337c8be64ca72f1c17cf0a2efca",
        bytes: 1941,
        lf: 68,
    },
    Golden {
        oid: "7d5ea7ab90ff17cd5e4d0af8d34b42c661299174",
        digest: "sha256:8427e927d81ae8a4cab8ccb51e75f645cf343867a3d6b8be8fd846bc5f3e0e65",
        bytes: 1940,
        lf: 67,
    },
    Golden {
        oid: "d7f6e317a66ad89d2b871c2dd4ebbc05f350b07a",
        digest: "sha256:6535a80ceb9c621247588e35f64b9830af7186e05179b050fc50b0cfe40458c1",
        bytes: 4063,
        lf: 88,
    },
    Golden {
        oid: "53ab744eb88feca2ae09e78acab5af151af00537",
        digest: "sha256:99d4236b8f94859f3ae8861741ec1a674d4690d5f27efce9acfb5ba7bc441fcd",
        bytes: 3812,
        lf: 80,
    },
    Golden {
        oid: "d450476334bcd5135c05347ac2af12321204e84e",
        digest: "sha256:1f106638217ea3b8a31debaed1a71dffb6ca85506ee177d703ef3fb07fe097a0",
        bytes: 1569,
        lf: 36,
    },
    Golden {
        oid: "218f27cc371e6b0dbc8ff29c0a2f00f45e40952d",
        digest: "sha256:2d3851dea15a06567427e6c6f0391a69be5ddba596bd182a82556baa637d9417",
        bytes: 1554,
        lf: 36,
    },
    Golden {
        oid: "0c3fdb54b795885e0b32e95a74eca3a9185cee24",
        digest: "sha256:28216d8d94722e6b9388c0406c1fb5aca75b4ebb229129af336a143a17111ffa",
        bytes: 1285,
        lf: 30,
    },
];
struct Guard {
    class: &'static str,
    flags: &'static [&'static str],
    action: bool,
    all: bool,
    call: &'static str,
    targets: &'static [&'static str],
}
static GUARDS: [Guard; 26] = [
    Guard {
        class: "SFMClientActions",
        flags: &["client_actions"],
        action: false,
        all: false,
        call: "SFMClientActions.register",
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    Guard {
        class: "SFMClientScreenTypes",
        flags: &["workspace_panels"],
        action: false,
        all: false,
        call: "SFMClientScreenTypes.register",
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    Guard {
        class: "SFMWorkspaceScreenTypes",
        flags: &["workspace_panels"],
        action: false,
        all: false,
        call: "SFMWorkspaceScreenTypes.register",
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    Guard {
        class: "SFMRouteComparisonScreenType",
        flags: &["workspace_panels", "route_comparison"],
        action: false,
        all: true,
        call: "SFMRouteComparisonScreenType.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMDocumentActionTarget",
        flags: &["manager_editor_actions"],
        action: true,
        all: false,
        call: "SFMDocumentActionTarget.Actions.register",
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    Guard {
        class: "SFMDocumentHistoryActions",
        flags: &["document_history"],
        action: true,
        all: false,
        call: "SFMDocumentHistoryActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMCommandPaletteActions",
        flags: &["client_actions"],
        action: false,
        all: false,
        call: "SFMCommandPaletteActions.register",
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    Guard {
        class: "SFMExplorerActions",
        flags: &[
            "file_explorer",
            "registry_explorer",
            "explorer_search",
            "explorer_compaction",
            "icon_rules",
        ],
        action: true,
        all: false,
        call: "SFMExplorerActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMOverlayActions",
        flags: &["client_overlay_scenes"],
        action: true,
        all: false,
        call: "SFMOverlayActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMTooltipModeActions",
        flags: &["tooltip_mode_override"],
        action: true,
        all: false,
        call: "SFMTooltipModeActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMPacketActions",
        flags: &["packet_actions"],
        action: true,
        all: false,
        call: "SFMPacketActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMClientProgramConsentActions",
        flags: &["client_program_consent"],
        action: true,
        all: false,
        call: "SFMClientProgramConsentActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMClientProgramReadActions",
        flags: &["client_program_reads"],
        action: true,
        all: false,
        call: "SFMClientProgramReadActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMTerminalDisplayActions",
        flags: &["touch_display_terminal_mount"],
        action: true,
        all: false,
        call: "SFMTerminalDisplayActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMSymbolActions",
        flags: &["java_symbols", "context_actions"],
        action: true,
        all: false,
        call: "SFMSymbolActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMSpatialActions",
        flags: &["spatial_coverage"],
        action: true,
        all: false,
        call: "SFMSpatialActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMTrajectoryActions",
        flags: &["trajectory_panels"],
        action: true,
        all: false,
        call: "SFMTrajectoryActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMReviewActions",
        flags: &["review_sessions", "release_review"],
        action: true,
        all: false,
        call: "SFMReviewActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMRouteComparisonActions",
        flags: &["route_comparison"],
        action: true,
        all: false,
        call: "SFMRouteComparisonActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMWorkspaceCounterfactualActions",
        flags: &["workspace_counterfactuals"],
        action: true,
        all: false,
        call: "SFMWorkspaceCounterfactualActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMWorkspaceLifecycleActions",
        flags: &["workspace_lifecycle"],
        action: true,
        all: false,
        call: "SFMWorkspaceLifecycleActions.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMKeyboardUsageSituations",
        flags: &["keyboard_profiles"],
        action: false,
        all: false,
        call: "SFMKeyboardUsageSituations.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMKeyboardUsageSituationRegistrations",
        flags: &["keyboard_profiles"],
        action: false,
        all: false,
        call: "SFMKeyboardUsageSituationRegistrations.register",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMDeveloperActions",
        flags: &["developer_tools"],
        action: true,
        all: false,
        call: "SFMDeveloperActions.register",
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    Guard {
        class: "SFMCommandHistoryService",
        flags: &["command_history"],
        action: true,
        all: false,
        call: "SFMCommandHistoryService.initializeDefault",
        targets: &["1.19.2", "1.19.4"],
    },
    Guard {
        class: "SFMMultiplayerClientRuntime",
        flags: &["multiplayer_packets"],
        action: false,
        all: false,
        call: "SFMMultiplayerClientRuntime.initialize",
        targets: &["1.19.2", "1.19.4"],
    },
];
const MOUNT_RAW: &str = "d7f6e317a66ad89d2b871c2dd4ebbc05f350b07a";
const MOUNT_GOLDEN_SHA: &str =
    "sha256:b85b5a87a305afd766f8cc6ef439ad042bdd64c31d18acc1f7db621bc9d0d17e";
const MOUNT_GOLDEN_BYTES: usize = 3945;
const CC_LIVE: &str = "    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour\n    private static void registerComputerCraftTurtleUpgrades() {\n\n        if (ca.teamdman.sfm.common.compat.SFMModCompat.isComputerCraftLoaded()) {\n            ca.teamdman.sfm.common.compat.computercraft.SFMComputerCraftTurtleUpgrades.register(SFMEventBus.MOD_BUS);\n        }\n    }\n\n";
const CC_NOOP: &str = "    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour\n    private static void registerComputerCraftTurtleUpgrades() {\n\n        // CC:Tweaked sources are retained but excluded because this branch has no compatible runtime.\n    }\n\n";
const OLD_CLIENT: &str =
    "        SFMTextEditors.register(bus);\n\n        SFMTextEditorActions.register(bus);\n";
const NEW_CLIENT_FIRST: &str = "        DistExecutor.safeRunWhenOn(\n                Dist.CLIENT,\n                () -> ca.teamdman.sfm.client.SFMClientRegistrations::register\n        );\n";
const NEW_CLIENT_OTHER: &str = "        if (ca.teamdman.sfm.common.util.SFMEnvironmentUtils.isClient()) {\n            ca.teamdman.sfm.client.SFMClientRegistrations.register(bus);\n        }\n";
const CC_CALL: &str = "        registerComputerCraftTurtleUpgrades();\n";

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    inputs: Vec<Input>,
    client_registration_owner_union: Vec<String>,
    guard_members: Vec<GuardMember>,
    exact_full_witness_masks: BTreeMap<String, Vec<String>>,
    raw_variants: Vec<RawVariant>,
}
#[derive(Facet)]
struct Input {
    path: String,
    stage_path: String,
    core_path: String,
    source_sha256: String,
    source_bytes: usize,
    membership: String,
    witnesses: BTreeMap<String, Option<String>>,
}
#[derive(Facet)]
struct GuardMember {
    class: String,
    flags: Vec<String>,
    requires_basic_client_actions: bool,
    all_owner_flags: bool,
    registration_call: String,
    registration_targets: Vec<String>,
}
#[derive(Facet)]
struct RawVariant {
    blob: String,
    sha256: String,
    bytes: usize,
    cr_count: usize,
    lf_count: usize,
    final_lf: bool,
    occurrences: Vec<Occurrence>,
}
#[derive(Facet)]
struct Occurrence {
    path: String,
    context: String,
}
struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let bytes = read_bounded(&core.repository.join(LEDGER), 2 * 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core-client-registration-slice@1",
            "wrong client registration ledger"
        );
        let commits = COMMITS
            .into_iter()
            .map(|(cell, oid)| (cell.to_owned(), oid.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            ledger.context_commits == commits,
            "client registration commits changed"
        );
        ensure!(
            ledger.inputs.len() == 2
                && ledger.guard_members.len() == 26
                && ledger.raw_variants.len() == 19,
            "client registration scope changed"
        );
        ensure!(
            ledger
                .client_registration_owner_union
                .iter()
                .map(String::as_str)
                .eq(UNION),
            "actual client registrar OR union changed"
        );
        let full_masks = FULL_MASKS
            .into_iter()
            .map(|(target, flags)| {
                (
                    target.to_owned(),
                    flags
                        .iter()
                        .map(|flag| (*flag).to_owned())
                        .collect::<Vec<_>>(),
                )
            })
            .collect::<BTreeMap<_, _>>();
        ensure!(
            ledger.exact_full_witness_masks == full_masks,
            "full witnessed member masks changed"
        );
        for id in UNION.into_iter().chain([CC]) {
            ensure!(
                core.features.0.contains_key(id),
                "member owner is not registered: {id}"
            );
        }
        let basic = &core.features.0[BASIC];
        ensure!(
            basic
                .supported_targets
                .iter()
                .map(String::as_str)
                .eq(TARGETS)
                && basic.requires.is_empty(),
            "basic actions must not imply unrelated UI registrars"
        );
        let mut seen_paths = BTreeSet::new();
        for input in ledger.inputs {
            let (_, digest, count) = TEMPLATES
                .iter()
                .find(|(path, _, _)| *path == input.path)
                .ok_or_else(|| eyre::eyre!("unreviewed client source"))?;
            let basename = if input.path == MAIN {
                "SFM.java"
            } else {
                "SFMClientRegistrations.java"
            };
            let expected_membership = if input.path == MAIN {
                "shared_all_ten_targets_all_feature_masks"
            } else {
                "actual_client_registration_owner_union"
            };
            ensure!(
                seen_paths.insert(input.path.clone())
                    && input.source_sha256 == *digest
                    && input.source_bytes == *count
                    && input.core_path
                        == format!("platform/minecraft/core-liquid-template/{}", input.path)
                    && input.stage_path
                        == format!(
                            "platform/cli/sfm-propagate-changes/target/core-client-registration-stage-v1/{basename}"
                        )
                    && input.membership == expected_membership,
                "client authored input or membership changed"
            );
            let expected = WITNESSES
                .iter()
                .filter(|(path, _, _)| *path == input.path)
                .map(|(_, cell, oid)| ((*cell).to_owned(), oid.map(str::to_owned)))
                .collect::<BTreeMap<_, _>>();
            ensure!(
                input.witnesses == expected && expected.len() == 20,
                "client raw all-context map changed"
            );
        }
        validate_membership(&core.metadata)?;
        let mut seen_classes = BTreeSet::new();
        for member in ledger.guard_members {
            let pin = GUARDS
                .iter()
                .find(|guard| guard.class == member.class)
                .ok_or_else(|| eyre::eyre!("unreviewed client registrar"))?;
            ensure!(
                seen_classes.insert(member.class.clone())
                    && member
                        .flags
                        .iter()
                        .map(String::as_str)
                        .eq(pin.flags.iter().copied())
                    && member.requires_basic_client_actions == pin.action
                    && member.all_owner_flags == pin.all
                    && member.registration_call == pin.call
                    && member
                        .registration_targets
                        .iter()
                        .map(String::as_str)
                        .eq(pin.targets.iter().copied()),
                "client import/call owner or version scope changed"
            );
        }
        let mut seen_oids = BTreeSet::new();
        for row in ledger.raw_variants {
            let pin = GOLDENS
                .iter()
                .find(|pin| pin.oid == row.blob)
                .ok_or_else(|| eyre::eyre!("unreviewed client raw blob"))?;
            let expected = WITNESSES
                .iter()
                .filter(|(_, _, oid)| *oid == Some(row.blob.as_str()))
                .map(|(path, cell, _)| ((*path).to_owned(), (*cell).to_owned()))
                .collect::<BTreeSet<_>>();
            let actual = row
                .occurrences
                .into_iter()
                .map(|item| (item.path, item.context))
                .collect::<BTreeSet<_>>();
            ensure!(
                seen_oids.insert(row.blob.clone())
                    && row.sha256 == pin.digest
                    && row.bytes == pin.bytes
                    && row.cr_count == 0
                    && row.lf_count == pin.lf
                    && row.final_lf
                    && actual == expected,
                "client raw byte or occurrence identity changed"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|pin| pin.oid.to_owned()).collect(),
        )?;
        for pin in &GOLDENS {
            verify_raw(&raw[pin.oid], pin)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            inventory.contains(MAIN) && inventory.contains(CLIENT),
            "client source templates must be promoted; no staged fallback"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }
    fn context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        let mut enabled = BTreeSet::new();
        let mut pending = requested
            .iter()
            .map(|flag| (*flag).to_owned())
            .collect::<Vec<_>>();
        while let Some(flag) = pending.pop() {
            if enabled.insert(flag.clone()) {
                let definition = self
                    .core
                    .features
                    .0
                    .get(&flag)
                    .ok_or_else(|| eyre::eyre!("unregistered requested member owner"))?;
                pending.extend(definition.requires.iter().cloned());
            }
        }
        let flags = enabled.iter().map(String::as_str).collect::<Vec<_>>();
        self.core.context(target, &flags)
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selected.inputs.get(path) else {
            ensure!(
                path == CLIENT && selected.omitted_paths.contains(path),
                "unexpected released main class omission"
            );
            return Ok(None);
        };
        ensure!(
            input.input == path && !selected.omitted_paths.contains(path),
            "client source gained alternate input"
        );
        let bytes = self.historical_source(path)?;
        verify_template(path, &bytes)?;
        Ok(Some(
            render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes(),
        ))
    }
    fn historical_source(&self, path: &str) -> Result<Vec<u8>> {
        let bytes = self.core.read_source(path)?;
        if path == MAIN {
            restore_constructor_baseline(&bytes)
        } else {
            Ok(bytes)
        }
    }
    fn full(&self, path: &str, target: &str) -> Result<String> {
        let oid = witness(path, &format!("dev/{target}"))?
            .ok_or_else(|| eyre::eyre!("missing full client witness"))?;
        let raw = std::str::from_utf8(&self.raw[oid])?;
        if oid == MOUNT_RAW {
            expand_mount(raw)
        } else {
            Ok(raw.to_owned())
        }
    }
    fn partial_main(&self, target: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let mut expected = self.full(MAIN, target)?;
        if !context.features[CC] {
            let live = matches!(
                target,
                "1.19.2" | "1.19.4" | "1.20" | "1.20.1" | "1.20.4" | "1.21.1"
            );
            replace_unique(&mut expected, if live { CC_LIVE } else { CC_NOOP }, "")?;
            let call = if matches!(target, "1.20" | "1.20.1" | "1.20.4") {
                CC_CALL.to_owned()
            } else {
                format!("{CC_CALL}\n")
            };
            replace_unique(&mut expected, &call, "")?;
        }
        if !has_client_owner(context) {
            replace_unique(
                &mut expected,
                if target == "1.19.2" {
                    NEW_CLIENT_FIRST
                } else {
                    NEW_CLIENT_OTHER
                },
                OLD_CLIENT,
            )?;
            if target == "1.19.2" {
                replace_unique(
                    &mut expected,
                    "import net.minecraftforge.api.distmarker.Dist;\nimport net.minecraftforge.fml.DistExecutor;\n",
                    "",
                )?;
            }
            let menus = !matches!(target, "1.21.0" | "1.21.1" | "26.1.2");
            let prefix = if menus {
                "import ca.teamdman.sfm.client.registry.SFMMenuScreens;\n"
            } else {
                ""
            };
            let imports = format!(
                "package ca.teamdman.sfm;\n\n{prefix}import ca.teamdman.sfm.client.registry.SFMTextEditorActions;\nimport ca.teamdman.sfm.client.registry.SFMTextEditors;\n"
            );
            replace_unique(&mut expected, "package ca.teamdman.sfm;\n\n", &imports)?;
            if matches!(target, "1.20" | "1.20.1" | "1.20.2" | "1.20.3" | "1.20.4") {
                replace_unique(
                    &mut expected,
                    "        SFMConfig.register(ModLoadingContext.get());\n\n\n",
                    "        SFMConfig.register(ModLoadingContext.get());\n\n",
                )?;
            }
            if menus {
                let namespace = if matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1") {
                    "net.minecraftforge"
                } else {
                    "net.neoforged"
                };
                replace_unique(
                    &mut expected,
                    &format!("import {namespace}.fml.common.Mod;\n"),
                    &format!(
                        "import {namespace}.fml.common.Mod;\nimport {namespace}.fml.event.lifecycle.FMLClientSetupEvent;\n"
                    ),
                )?;
                let config = "        SFMConfig.register(ModLoadingContext.get());\n\n";
                replace_unique(
                    &mut expected,
                    config,
                    &format!(
                        "{config}        bus.addListener((FMLClientSetupEvent e) -> SFMMenuScreens.register());\n\n"
                    ),
                )?;
            }
        }
        Ok(expected.into_bytes())
    }
    fn partial_client(&self, target: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        if !has_client_owner(context) {
            return Ok(None);
        }
        let raw = self.full(CLIENT, target)?;
        let mut expected = String::new();
        for line in raw.split_inclusive('\n') {
            let omit = GUARDS
                .iter()
                .any(|guard| !guard_enabled(guard, context) && owns_line(guard, line))
                || (!context.features[BASIC] && line.trim() == "SFMClientActions.commandTree();");
            if !omit {
                expected.push_str(line);
            }
        }
        Ok(Some(expected.into_bytes()))
    }
}
fn has_client_owner(context: &ProjectionContext) -> bool {
    UNION.iter().any(|flag| context.features[*flag])
}
fn guard_enabled(guard: &Guard, context: &ProjectionContext) -> bool {
    let selected = if guard.all {
        guard.flags.iter().all(|flag| context.features[*flag])
    } else {
        guard.flags.iter().any(|flag| context.features[*flag])
    };
    // GUARDS remain immutable historical evidence. Model the reviewed current
    // successor independently; never replace the ledger's member predicates.
    let selected = if matches!(context.minecraft_version.as_str(), "1.19.2" | "1.19.4") {
        match guard.class {
            "SFMWorkspaceLifecycleActions" => {
                selected || context.features["workspace_panel_entry_controls"]
            }
            "SFMDocumentHistoryActions" => {
                context.features["document_history"]
                    || context.features["editor_pointer_actions"]
                    || context.features["editor_search"]
            }
            "SFMExplorerActions" => [
                "file_explorer",
                "registry_explorer",
                "explorer_search",
                "explorer_compaction",
                "explorer_navigation",
                "java_symbols",
                "release_review",
                "workspace_counterfactuals",
                "theme_preview_rules",
                "clipboard_action_commands",
            ]
            .iter()
            .any(|name| context.features[*name]),
            "SFMPacketActions" => {
                selected
                    && (context.features["packet_transport_private"]
                        || context.features["multiplayer_packets"])
            }
            "SFMDeveloperActions" => {
                selected
                    && (context.features["developer_world_actions"]
                        || (context.features["workspace_panel_actions"]
                            && (context.features["input_diagnostics_panel"]
                                || (context.features["editor_document_panels"]
                                    && context.features["workspace_panel_reopening"]))))
            }
            _ => selected,
        }
    } else {
        selected
    };
    selected && (!guard.action || context.features[BASIC])
}
fn owns_line(guard: &Guard, line: &str) -> bool {
    (line.starts_with("import ") && line.trim_end().ends_with(&format!(".{};", guard.class)))
        || (line.trim_start().starts_with(&format!("{}(", guard.call))
            && line.trim_end().ends_with(");"))
}
fn expand_mount(source: &str) -> Result<String> {
    const IF: &str = "{% if features.touch_display_terminal_mount %}\n";
    const END: &str = "{% endif %}\n";
    ensure!(
        source.matches(IF).count() == 2 && source.matches(END).count() == 2,
        "original mount carrier changed"
    );
    let expanded = source.replace(IF, "").replace(END, "");
    ensure!(
        expanded.len() == MOUNT_GOLDEN_BYTES && sha256(expanded.as_bytes()) == MOUNT_GOLDEN_SHA,
        "expanded mount golden changed"
    );
    Ok(expanded)
}
fn witness(path: &str, cell: &str) -> Result<Option<&'static str>> {
    WITNESSES
        .iter()
        .find(|(candidate, context, _)| *candidate == path && *context == cell)
        .map(|(_, _, oid)| *oid)
        .ok_or_else(|| eyre::eyre!("unreviewed client source cell"))
}
fn replace_unique(text: &mut String, old: &str, new: &str) -> Result<()> {
    ensure!(
        text.matches(old).count() == 1,
        "client owned raw anchor is not unique"
    );
    *text = text.replacen(old, new, 1);
    Ok(())
}
fn verify_raw(bytes: &[u8], pin: &Golden) -> Result<()> {
    ensure!(
        bytes.len() == pin.bytes
            && sha256(bytes) == pin.digest
            && !bytes.contains(&b'\r')
            && bytes.iter().filter(|byte| **byte == b'\n').count() == pin.lf
            && bytes.ends_with(b"\n"),
        "client raw bytes changed; no normalization approved"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn verify_template(path: &str, bytes: &[u8]) -> Result<()> {
    // TEMPLATES and its ledger bindings remain the original reviewed identities.
    // Validate current bytes strictly, then use the exact predecessor only as evidence.
    let predecessor;
    let bytes = if path == CLIENT {
        predecessor = reviewed_pre_workspace_registrar(bytes)?;
        predecessor.as_bytes()
    } else {
        bytes
    };
    let (_, digest, count) = TEMPLATES
        .iter()
        .find(|(candidate, _, _)| *candidate == path)
        .ok_or_else(|| eyre::eyre!("unreviewed client authored path"))?;
    ensure!(
        bytes.len() == *count
            && sha256(bytes) == *digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "client authored bytes changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn validate_membership(metadata: &CoreProjectInputs) -> Result<()> {
    if let Some(rules) = metadata.source_rules.get(MAIN) {
        ensure!(
            rules.len() == 1
                && rules[0].input == MAIN
                && rules[0].when.targets.is_empty()
                && rules[0].when.all_features.is_empty()
                && rules[0].when.any_features.is_empty()
                && rules[0].when.none_features.is_empty(),
            "released SFM entrypoint must remain shared"
        );
    }
    let rules = metadata
        .source_rules
        .get(CLIENT)
        .ok_or_else(|| eyre::eyre!("client registration OR membership not registered"))?;
    ensure!(
        rules.len() == 1
            && rules[0].input == CLIENT
            && rules[0].when.targets.is_empty()
            && rules[0].when.all_features.is_empty()
            && rules[0].when.none_features.is_empty()
            && rules[0]
                .when
                .any_features
                .iter()
                .map(String::as_str)
                .eq(UNION),
        "client registry shell must use the actual owner OR union"
    );
    Ok(())
}

#[test]
fn client_entrypoints_match_forty_present_and_absent_witnessed_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for (cell, _) in COMMITS {
        let (environment, target) = cell.split_once('/').expect("fixed context");
        let requested = if environment == "dev" {
            FULL_MASKS
                .iter()
                .find(|(candidate, _)| *candidate == target)
                .expect("fixed target")
                .1
        } else {
            &[]
        };
        let mut current_requested = requested.to_vec();
        // The frozen historical FULL_MASKS and ledger stay unchanged. This
        // current full-source proof explicitly enables the refined real owner.
        if environment == "dev" && matches!(target, "1.19.2" | "1.19.4") {
            current_requested.push("developer_world_actions");
        }
        let mut context = fixture.context(target, &current_requested)?;
        context.environment = environment.to_owned();
        context.projection_key = format!(
            "{}/mc-{target}",
            if environment == "dev" {
                "sfm-dev"
            } else {
                "sfm-4.34.0"
            }
        );
        context.preset.clone_from(&context.projection_key);
        for path in [MAIN, CLIENT] {
            let actual = fixture.render(path, &context)?;
            let expected = match witness(path, cell)? {
                Some(oid) if oid == MOUNT_RAW => {
                    Some(expand_mount(std::str::from_utf8(&fixture.raw[oid])?)?.into_bytes())
                }
                Some(oid) => Some(fixture.raw[oid].clone()),
                None => None,
            };
            assert_eq!(
                actual, expected,
                "wrong source membership or bytes {cell}/{path}"
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 40);
    Ok(())
}

#[test]
fn client_imports_and_calls_follow_effective_member_masks_not_a_bootstrap_flag() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cases = 0;
    for target in TARGETS {
        let mut requests = vec![
            vec![],
            vec![CC],
            vec![BASIC],
            vec!["workspace_panels"],
            vec!["canvas_text_editor"],
        ];
        if matches!(target, "1.19.2" | "1.19.4") {
            requests.extend([
                vec![BASIC, "packet_actions", "packet_transport_private"],
                vec![BASIC, "developer_tools", "developer_world_actions"],
                vec![
                    BASIC,
                    "developer_tools",
                    "workspace_panel_actions",
                    "input_diagnostics_panel",
                ],
                vec![
                    BASIC,
                    "developer_tools",
                    "workspace_panel_actions",
                    "editor_document_panels",
                    "workspace_panel_reopening",
                ],
                vec![BASIC, "editor_pointer_actions"],
                vec![BASIC, "editor_search"],
                vec![BASIC, "explorer_navigation"],
                vec![BASIC, "theme_preview_rules"],
                vec![BASIC, "clipboard_action_commands"],
            ]);
        }
        for owner in UNION {
            if fixture.core.features.0[owner]
                .supported_targets
                .iter()
                .any(|id| id == target)
            {
                requests.push(vec![BASIC, owner]);
            }
        }
        for requested in requests {
            let context = fixture.context(target, &requested)?;
            assert_eq!(
                fixture.render(MAIN, &context)?,
                Some(fixture.partial_main(target, &context)?),
                "wrong independently selected common entrypoint {target}/{requested:?}"
            );
            let actual_client = fixture.render(CLIENT, &context)?;
            assert_eq!(
                actual_client,
                fixture.partial_client(target, &context)?,
                "wrong effective registrar mask {target}/{requested:?}"
            );
            if let Some(bytes) = actual_client {
                let actual = std::str::from_utf8(&bytes)?;
                for guard in &GUARDS {
                    let present = guard.targets.contains(&target) && guard_enabled(guard, &context);
                    let full = fixture.full(CLIENT, target)?;
                    let owned_lines = full
                        .split_inclusive('\n')
                        .filter(|line| owns_line(guard, line))
                        .collect::<Vec<_>>();
                    assert_eq!(
                        !owned_lines.is_empty(),
                        guard.targets.contains(&target),
                        "raw member target scope changed"
                    );
                    for line in owned_lines {
                        assert_eq!(
                            actual.contains(line),
                            present,
                            "wrong typed import/call {target}/{}",
                            guard.class
                        );
                    }
                }
            }
            cases += 1;
        }
    }
    assert!(cases >= 50);
    Ok(())
}

#[test]
fn basic_actions_do_not_register_other_client_features_and_old_apis_remain() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let off = fixture.context(target, &[])?;
        let released = fixture.render(MAIN, &off)?.expect("released entrypoint");
        assert_eq!(
            released,
            fixture.raw[witness(MAIN, &format!("release/{target}"))?.expect("release source")]
        );
        assert!(fixture.render(CLIENT, &off)?.is_none());
        let basic = fixture.context(target, &[BASIC])?;
        let client_bytes = fixture
            .render(CLIENT, &basic)?
            .expect("basic shared action registrar");
        let client = std::str::from_utf8(&client_bytes)?;
        assert!(client.contains("SFMClientActions.register(bus);"));
        assert!(client.contains("SFMCommandPaletteActions.register(bus);"));
        for guard in &GUARDS {
            if !guard.flags.contains(&BASIC) {
                assert!(
                    !client.lines().any(|line| owns_line(guard, line)),
                    "basic actions pulled unrelated registrar {}",
                    guard.class
                );
            }
        }
        let main_bytes = fixture.render(MAIN, &basic)?.expect("entrypoint");
        let main = std::str::from_utf8(&main_bytes)?;
        if target == "1.19.2" {
            assert!(main.contains("DistExecutor.safeRunWhenOn"));
            assert!(client.contains("public static void register()"));
        } else {
            assert!(main.contains("SFMEnvironmentUtils.isClient()"));
            assert!(!main.contains("DistExecutor"));
            assert!(client.contains("public static void register(IEventBus bus)"));
        }
        if target == "1.21.0" {
            assert_eq!(off.minecraft_version, "1.21");
        }
        let cc_context = fixture.context(target, &[CC])?;
        let cc_bytes = fixture.render(MAIN, &cc_context)?.expect("entrypoint");
        let cc = std::str::from_utf8(&cc_bytes)?;
        assert!(cc.contains("registerComputerCraftTurtleUpgrades"));
        assert!(!cc.contains("SFMClientRegistrations"));
        if matches!(target, "1.20.2" | "1.20.3" | "1.21.0" | "26.1.2") {
            assert!(cc.contains("no compatible runtime"));
            assert!(!cc.contains("SFMComputerCraftTurtleUpgrades.register("));
        }
        if matches!(target, "1.21.0" | "1.21.1" | "26.1.2") {
            assert!(!client.contains("FMLClientSetupEvent"));
        } else {
            assert!(client.contains("SFMMenuScreens.register();"));
        }
        if !matches!(target, "1.19.2" | "1.19.4") {
            let keyboard = fixture.context(target, &["keyboard_profiles"])?;
            let bytes = fixture
                .render(CLIENT, &keyboard)?
                .expect("existing wide keyboard/action context");
            let text = std::str::from_utf8(&bytes)?;
            assert!(!text.contains("SFMKeyboardUsageSituations.register("));
            assert!(!text.contains("SFMKeyboardUsageSituationRegistrations"));
        }
    }
    Ok(())
}

#[test]
fn client_entrypoint_common_edit_reaches_ten_targets_in_isolated_core() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let core = temp.path().join(super::core_inputs::CORE_ROOT);
    let destination = core.join(MAIN);
    fs::create_dir_all(destination.parent().expect("fixed parent"))?;
    let original = fixture.historical_source(MAIN)?;
    let mut edited = original.clone();
    edited.extend_from_slice(b"// isolated common mod entrypoint edit\n");
    fs::write(&destination, &edited)?;
    let inventory = discover_core_source_files(&core)?;
    let mut metadata = fixture.core.metadata.clone();
    metadata
        .source_rules
        .retain(|output, _| inventory.contains(output));
    metadata.project_files.clear();
    for target in TARGETS {
        let context = fixture.context(target, &[])?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        assert_eq!(selected.inputs[MAIN].input, MAIN);
        let bytes = read_bounded(
            &checked_file(&core, &selected.inputs[MAIN].input)?,
            1024 * 1024,
        )?;
        let actual = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
        let mut expected = fixture.render(MAIN, &context)?.expect("shared entrypoint");
        expected.extend_from_slice(b"// isolated common mod entrypoint edit\n");
        assert_eq!(actual.as_bytes(), expected);
    }
    assert_eq!(fixture.historical_source(MAIN)?, original);
    Ok(())
}

#[test]
fn client_registry_unknown_owner_whole_class_gate_or_raw_mutation_refuses() -> Result<()> {
    let fixture = Fixture::load()?;
    for path in [MAIN, CLIENT] {
        let mut bytes = fixture.historical_source(path)?;
        bytes.push(b' ');
        assert!(verify_template(path, &bytes).is_err());
    }
    let pin = &GOLDENS[0];
    let mut raw = fixture.raw[pin.oid].clone();
    raw[0] = b'X';
    assert!(verify_raw(&raw, pin).is_err());
    let mut metadata = fixture.core.metadata.clone();
    metadata
        .source_rules
        .get_mut(CLIENT)
        .expect("validated rule")[0]
        .when
        .any_features = vec![BASIC.to_owned()];
    assert!(validate_membership(&metadata).is_err());
    let mut metadata = fixture.core.metadata.clone();
    metadata.source_rules.insert(
        MAIN.to_owned(),
        vec![super::core_inputs::InputVariant {
            input: MAIN.to_owned(),
            when: super::core_inputs::InputPredicate {
                targets: vec![],
                all_features: vec![BASIC.to_owned()],
                any_features: vec![],
                none_features: vec![],
            },
            template: false,
        }],
    );
    assert!(validate_membership(&metadata).is_err());
    for path in [MAIN, CLIENT] {
        let bytes = fixture.historical_source(path)?;
        let mut context = fixture.context("1.19.2", &[])?;
        context.features.remove("client_program_reads");
        assert!(render_java_source(std::str::from_utf8(&bytes)?, &context).is_err());
    }
    let altered = String::from_utf8(fixture.raw[MOUNT_RAW].clone())?
        .replace("touch_display_terminal_mount", "unreviewed");
    assert!(expand_mount(&altered).is_err());
    Ok(())
}

#[test]
fn client_current_contract_preserves_entry_only_render_and_explicit_omission_without_git()
-> Result<()> {
    // New current-source coverage does not invoke the older Git-backed Fixture.
    let core = CoreTestFixture::load()?;
    let inventory = discover_core_source_files(&core.core)?;
    for target in &TARGETS[..2] {
        let empty = core.context(target, &[])?;
        let omitted = select_core_inputs(&core.metadata, &empty, &inventory)?;
        ensure!(
            !omitted.inputs.contains_key(CLIENT) && omitted.omitted_paths.contains(CLIENT),
            "empty current registrar must be explicitly omitted; do not read/render it"
        );
        for typed in [false, true] {
            let mut names = BTreeSet::from(["workspace_panel_entry_controls".to_owned()]);
            if typed {
                names.insert("typed_command_palette".to_owned());
            }
            loop {
                let mut additions = Vec::new();
                for name in &names {
                    let definition = core
                        .features
                        .0
                        .get(name)
                        .ok_or_else(|| eyre::eyre!("unknown owner {name}"))?;
                    additions.extend(
                        definition
                            .requires
                            .iter()
                            .filter(|name| !names.contains(*name))
                            .cloned(),
                    );
                }
                if additions.is_empty() {
                    break;
                }
                names.extend(additions);
            }
            let roots = names.iter().map(String::as_str).collect::<Vec<_>>();
            let context = core.context(target, &roots)?;
            ensure!(
                !context.features["workspace_lifecycle"],
                "test widened lifecycle requires"
            );
            let lifecycle_guard = GUARDS
                .iter()
                .find(|guard| guard.class == "SFMWorkspaceLifecycleActions")
                .expect("fixed historical guard");
            ensure!(
                guard_enabled(lifecycle_guard, &context),
                "current entry ownership must not require lifecycle"
            );
            let selection = select_core_inputs(&core.metadata, &context, &inventory)?;
            let input = selection
                .inputs
                .get(CLIENT)
                .ok_or_else(|| eyre::eyre!("entry owner needs its real registrar"))?;
            ensure!(
                input.input == CLIENT && !selection.omitted_paths.contains(CLIENT),
                "current registrar gained an alternate or implicit omission"
            );
            let bytes = core.read_source(&input.input)?;
            verify_template(CLIENT, &bytes)?;
            let mut changed = bytes.clone();
            changed[0] ^= 1;
            assert!(verify_template(CLIENT, &changed).is_err());
            let rendered = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            for reference in [
                "import ca.teamdman.sfm.client.action.SFMWorkspaceLifecycleActions;",
                "        SFMWorkspaceLifecycleActions.register(bus);",
            ] {
                ensure!(
                    rendered.matches(reference).count() == 1,
                    "real entry registration absent"
                );
            }
        }
    }
    Ok(())
}

// Keep immutable historical checks exact without freezing current authored
// whitespace. This is test-only and accepts the reviewed whitespace mutations;
// verify_template still checks the complete original hash after reversing it.
const BASELINE_CONSTRUCTOR: &str = concat!(
    "{% when \"1.20.4\", \"1.21\", \"1.21.1\" %}\n",
    "    public SFM(IEventBus bus) {\n\n",
    "{% when \"26.1.2\" %}\n",
    "    public SFM(IEventBus bus) {\n",
    "{% endcase %}"
);
const NORMALIZED_CONSTRUCTOR: &str = concat!(
    "{% when \"1.20.4\", \"1.21\", \"1.21.1\", \"26.1.2\" %}\n",
    "    public SFM(IEventBus bus) {\n\n",
    "{% endcase %}"
);

fn restore_constructor_baseline(bytes: &[u8]) -> Result<Vec<u8>> {
    let source = std::str::from_utf8(bytes)?;
    ensure!(
        source.matches(NORMALIZED_CONSTRUCTOR).count() == 1,
        "expected exactly one approved normalized constructor branch"
    );
    let historical = source.replacen(NORMALIZED_CONSTRUCTOR, BASELINE_CONSTRUCTOR, 1);
    let cc = "{% when \"1.20\", \"1.20.1\", \"1.20.4\" %}\n";
    let normalized_cc = format!("{cc}\n        registerComputerCraftTurtleUpgrades();");
    ensure!(
        historical.matches(&normalized_cc).count() == 2,
        "expected two normalized CC branches"
    );
    let historical = historical.replace(
        &normalized_cc,
        &format!("{cc}        registerComputerCraftTurtleUpgrades();"),
    );
    let condition = source
        .lines()
        .find(|line| line.starts_with("{% if features.canvas_text_editor or "))
        .ok_or_else(|| eyre::eyre!("missing client feature guard"))?;
    let anchor = "        SFMConfig.register(ModLoadingContext.get());\n\n";
    ensure!(
        historical.matches(anchor).count() == 1,
        "expected one config anchor"
    );
    let historical = historical.replacen(anchor, &format!("{anchor}{condition}\n{{% case minecraft_version %}}\n{{% when \"1.20\", \"1.20.1\", \"1.20.2\", \"1.20.3\", \"1.20.4\" %}}\n\n{{% endcase %}}\n{{% endif %}}\n"), 1);
    verify_template(MAIN, historical.as_bytes())?;
    Ok(historical.into_bytes())
}

#[test]
fn normalized_constructor_preserves_twenty_complete_java_renders() -> Result<()> {
    let core = CoreTestFixture::load()?;
    let catalog = super::core_catalog::CoreCatalog::load(&core.repository, &core.repository)?;
    let current = core.read_source(MAIN)?;
    let historical = restore_constructor_baseline(&current)?;
    let mut changed = Vec::new();
    for key in catalog.catalog.0.keys() {
        let context = catalog.context(key)?;
        let before = render_java_source(std::str::from_utf8(&historical)?, &context)?;
        let after = render_java_source(std::str::from_utf8(&current)?, &context)?;
        let comparison = super::simplify::compare(
            &super::simplify::parse(before.clone())?,
            &super::simplify::parse(after.clone())?,
        );
        let mut expected = before.clone();
        if context.minecraft_version == "26.1.2" {
            let needle = "    public SFM(IEventBus bus) {\n        SFMEventBus";
            assert_eq!(before.matches(needle).count(), 1);
            expected = expected.replacen(
                needle,
                "    public SFM(IEventBus bus) {\n\n        SFMEventBus",
                1,
            );
        }
        if key.starts_with("sfm-dev/") {
            expected = expected.replace(
                "SFMConfig.register(ModLoadingContext.get());\n\n\n",
                "SFMConfig.register(ModLoadingContext.get());\n\n",
            ).replace(
                "SFMCreativeTabs.register(bus);\n        registerComputerCraftTurtleUpgrades();",
                "SFMCreativeTabs.register(bus);\n\n        registerComputerCraftTurtleUpgrades();",
            );
        }
        assert_eq!(after, expected, "unexpected output change for {key}");
        if before != after {
            assert_eq!(comparison.classification, "whitespace_only");
            assert!(comparison.whitespace_gap_count > 0);
            assert_eq!(comparison.changed_token_region_count, 0);
            changed.push(key.as_str());
        } else {
            assert_eq!(before, after, "unexpected output change for {key}");
            assert_eq!(comparison.classification, "exact");
        }
    }
    assert_eq!(catalog.catalog.0.len(), 20);
    assert_eq!(
        changed,
        [
            "sfm-4.34.0/mc-26.1.2",
            "sfm-dev/mc-1.20",
            "sfm-dev/mc-1.20.1",
            "sfm-dev/mc-1.20.2",
            "sfm-dev/mc-1.20.3",
            "sfm-dev/mc-1.20.4",
            "sfm-dev/mc-26.1.2",
        ]
    );
    Ok(())
}

#[test]
fn constructor_baseline_restore_rejects_unreviewed_mutations() -> Result<()> {
    let core = CoreTestFixture::load()?;
    let source = core.read_source(MAIN)?;
    let mut mutated = source.clone();
    mutated.extend_from_slice(b"// unrelated change\n");
    assert!(restore_constructor_baseline(&mutated).is_err());
    let duplicate = format!(
        "{}\n{NORMALIZED_CONSTRUCTOR}",
        std::str::from_utf8(&source)?
    );
    assert!(restore_constructor_baseline(duplicate.as_bytes()).is_err());
    let changed = std::str::from_utf8(&source)?
        .replace("SFMEventBus.MOD_BUS = bus;", "SFMEventBus.MOD_BUS = null;");
    assert!(restore_constructor_baseline(changed.as_bytes()).is_err());
    Ok(())
}
