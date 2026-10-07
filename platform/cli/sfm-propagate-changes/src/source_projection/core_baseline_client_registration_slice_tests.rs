//! Post-promotion source contracts for seven released client registrars.
//!
//! Historical Git blobs are bounded witnesses, never production source inputs.
//! This module requires promoted core files and registered narrow owners. It
//! proves membership/rendering, not Java linkage, native builds or gameplay.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
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

const LEDGER: &str = "docs/tasks/sfm-core-baseline-client-registration-slice.json";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const OWNERS: [&str; 7] = [
    "touch_display",
    "command_palette",
    "client_manager_gui",
    "client_overlay_scenes",
    "canvas_text_editor",
    "item_details_jei",
    "client_launch_screen",
];
const PATHS: [&str; 7] = [
    "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
    "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
    "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
    "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
    "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
    "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
    "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
];
const TEMPLATES: [(&str, &str, usize); 7] = [
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "sha256:a30fe955fd87954be491b65f74e3c4f9adb3fb93484b6512d8bdf8cb8b87140a",
        1385,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "sha256:e54808974f06dd90b3acb96300b2cabbd1ef528b6ab9503b713b3b58d908a3ef",
        19359,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "sha256:cc4e39bf1617dcad42d6cb506a066866349ef260de67b53307c606438ff24e50",
        2469,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "sha256:80462170f846887fd634bcb53f8663abcaf371ace2f1156f65c7f72fe3e8e0d0",
        3975,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "sha256:7b381761342c5f0723f34881b84844d275e1d4088c063d9a30f42974a0577737",
        3593,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "sha256:b0908117d8dc646df4bdacf4b5bbb47b950a864352e5a1d48bc6461752ad4725",
        14410,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "sha256:dc064e987efe003d0dac54c7c27db36644b06284ad7ad31439397a51d568bb89",
        2260,
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
const WITNESSES: [(&str, &str, &str); 140] = [
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "dev/1.19.2",
        "a55ad3e4edfba8eb162fc8aae9616e7f6a79f3dd",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "dev/1.19.4",
        "a55ad3e4edfba8eb162fc8aae9616e7f6a79f3dd",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "dev/1.20",
        "a6ce912ea926844e3ab8a4fc88351e0612b32739",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "dev/1.20.1",
        "a6ce912ea926844e3ab8a4fc88351e0612b32739",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "dev/1.20.2",
        "f01670cf122329d37d5f6df69da5fc94552a2a5b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "dev/1.20.3",
        "f01670cf122329d37d5f6df69da5fc94552a2a5b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "dev/1.20.4",
        "f01670cf122329d37d5f6df69da5fc94552a2a5b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "dev/1.21.0",
        "f01670cf122329d37d5f6df69da5fc94552a2a5b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "dev/1.21.1",
        "f01670cf122329d37d5f6df69da5fc94552a2a5b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "dev/26.1.2",
        "f01670cf122329d37d5f6df69da5fc94552a2a5b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "release/1.19.2",
        "a6ce912ea926844e3ab8a4fc88351e0612b32739",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "release/1.19.4",
        "a6ce912ea926844e3ab8a4fc88351e0612b32739",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "release/1.20",
        "a6ce912ea926844e3ab8a4fc88351e0612b32739",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "release/1.20.1",
        "a6ce912ea926844e3ab8a4fc88351e0612b32739",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "release/1.20.2",
        "f01670cf122329d37d5f6df69da5fc94552a2a5b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "release/1.20.3",
        "f01670cf122329d37d5f6df69da5fc94552a2a5b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "release/1.20.4",
        "f01670cf122329d37d5f6df69da5fc94552a2a5b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "release/1.21.0",
        "f01670cf122329d37d5f6df69da5fc94552a2a5b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "release/1.21.1",
        "f01670cf122329d37d5f6df69da5fc94552a2a5b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMBlockEntityRenderers.java",
        "release/26.1.2",
        "f01670cf122329d37d5f6df69da5fc94552a2a5b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "dev/1.19.2",
        "47b87ac8ad9467cf31e47543a01994c2da6d72c3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "dev/1.19.4",
        "47b87ac8ad9467cf31e47543a01994c2da6d72c3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "dev/1.20",
        "47b87ac8ad9467cf31e47543a01994c2da6d72c3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "dev/1.20.1",
        "47b87ac8ad9467cf31e47543a01994c2da6d72c3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "dev/1.20.2",
        "4b6d83fa0f93c7a91f4c97fe82ab8177b65e4ea4",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "dev/1.20.3",
        "4b6d83fa0f93c7a91f4c97fe82ab8177b65e4ea4",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "dev/1.20.4",
        "4b6d83fa0f93c7a91f4c97fe82ab8177b65e4ea4",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "dev/1.21.0",
        "4b6d83fa0f93c7a91f4c97fe82ab8177b65e4ea4",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "dev/1.21.1",
        "4b6d83fa0f93c7a91f4c97fe82ab8177b65e4ea4",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "dev/26.1.2",
        "9ce52e450cb5ed0de3cbc3492ee05efbda127754",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "release/1.19.2",
        "281d527e9053fc559fce2341b2990fadbd780d4d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "release/1.19.4",
        "281d527e9053fc559fce2341b2990fadbd780d4d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "release/1.20",
        "281d527e9053fc559fce2341b2990fadbd780d4d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "release/1.20.1",
        "281d527e9053fc559fce2341b2990fadbd780d4d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "release/1.20.2",
        "682afc2118a0f61177bb915ce80e34a4fc999903",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "release/1.20.3",
        "682afc2118a0f61177bb915ce80e34a4fc999903",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "release/1.20.4",
        "682afc2118a0f61177bb915ce80e34a4fc999903",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "release/1.21.0",
        "682afc2118a0f61177bb915ce80e34a4fc999903",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "release/1.21.1",
        "682afc2118a0f61177bb915ce80e34a4fc999903",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyMappings.java",
        "release/26.1.2",
        "758f175bf865066e6a51745f9239552705938ef9",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "dev/1.19.2",
        "d89cd329f09bc830588d6f7b1af3b6af5e0a7288",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "dev/1.19.4",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "dev/1.20",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "dev/1.20.1",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "dev/1.20.2",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "dev/1.20.3",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "dev/1.20.4",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "dev/1.21.0",
        "dc7ce44b98d96d2b18d3a36dd50e599694dcbde3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "dev/1.21.1",
        "dc7ce44b98d96d2b18d3a36dd50e599694dcbde3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "dev/26.1.2",
        "2d748aadc704a714dc6796eedda282215963e8c7",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "release/1.19.2",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "release/1.19.4",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "release/1.20",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "release/1.20.1",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "release/1.20.2",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "release/1.20.3",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "release/1.20.4",
        "85d108feb10610ae698e3388d42c9dea7827091a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "release/1.21.0",
        "dc7ce44b98d96d2b18d3a36dd50e599694dcbde3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "release/1.21.1",
        "dc7ce44b98d96d2b18d3a36dd50e599694dcbde3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMMenuScreens.java",
        "release/26.1.2",
        "2d748aadc704a714dc6796eedda282215963e8c7",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "dev/1.19.2",
        "66800c378c767d4d0ba087b433e14a8b21c5b86b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "dev/1.19.4",
        "66800c378c767d4d0ba087b433e14a8b21c5b86b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "dev/1.20",
        "0ea6959a4d65c4a3ecd1dae9ed9a8487eae04415",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "dev/1.20.1",
        "0ea6959a4d65c4a3ecd1dae9ed9a8487eae04415",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "dev/1.20.2",
        "b3bad8f2a8fa6fc3bfcc8c5b7ea7fa214c991dc4",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "dev/1.20.3",
        "b3bad8f2a8fa6fc3bfcc8c5b7ea7fa214c991dc4",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "dev/1.20.4",
        "d73d2914fa7db57d7136bba0d6446b11e7d4f6c1",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "dev/1.21.0",
        "eb2d0e39f889eae297ef7b73aa94c61f90f6f4e0",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "dev/1.21.1",
        "eb2d0e39f889eae297ef7b73aa94c61f90f6f4e0",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "dev/26.1.2",
        "eb2d0e39f889eae297ef7b73aa94c61f90f6f4e0",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "release/1.19.2",
        "0ea6959a4d65c4a3ecd1dae9ed9a8487eae04415",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "release/1.19.4",
        "0ea6959a4d65c4a3ecd1dae9ed9a8487eae04415",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "release/1.20",
        "0ea6959a4d65c4a3ecd1dae9ed9a8487eae04415",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "release/1.20.1",
        "0ea6959a4d65c4a3ecd1dae9ed9a8487eae04415",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "release/1.20.2",
        "b3bad8f2a8fa6fc3bfcc8c5b7ea7fa214c991dc4",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "release/1.20.3",
        "b3bad8f2a8fa6fc3bfcc8c5b7ea7fa214c991dc4",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "release/1.20.4",
        "d73d2914fa7db57d7136bba0d6446b11e7d4f6c1",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "release/1.21.0",
        "eb2d0e39f889eae297ef7b73aa94c61f90f6f4e0",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "release/1.21.1",
        "eb2d0e39f889eae297ef7b73aa94c61f90f6f4e0",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMOverlays.java",
        "release/26.1.2",
        "eb2d0e39f889eae297ef7b73aa94c61f90f6f4e0",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "dev/1.19.2",
        "2fe8f071d151a4e5fc26861a6cb7138653c0469e",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "dev/1.19.4",
        "2fe8f071d151a4e5fc26861a6cb7138653c0469e",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "dev/1.20",
        "8791e765fb5c6bfee327c8ecaf8b077f393aeb66",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "dev/1.20.1",
        "8791e765fb5c6bfee327c8ecaf8b077f393aeb66",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "dev/1.20.2",
        "ce635435bf6697bc0534919c2ccab1ad7724993c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "dev/1.20.3",
        "ce635435bf6697bc0534919c2ccab1ad7724993c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "dev/1.20.4",
        "ce635435bf6697bc0534919c2ccab1ad7724993c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "dev/1.21.0",
        "ce635435bf6697bc0534919c2ccab1ad7724993c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "dev/1.21.1",
        "ce635435bf6697bc0534919c2ccab1ad7724993c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "dev/26.1.2",
        "ce635435bf6697bc0534919c2ccab1ad7724993c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "release/1.19.2",
        "6d0833663f518a532efd49eb9e77fb5e90b3fea3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "release/1.19.4",
        "6d0833663f518a532efd49eb9e77fb5e90b3fea3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "release/1.20",
        "6d0833663f518a532efd49eb9e77fb5e90b3fea3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "release/1.20.1",
        "6d0833663f518a532efd49eb9e77fb5e90b3fea3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "release/1.20.2",
        "9bb363d795b1fa362e7418c9dcee5614785a2be3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "release/1.20.3",
        "9bb363d795b1fa362e7418c9dcee5614785a2be3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "release/1.20.4",
        "9bb363d795b1fa362e7418c9dcee5614785a2be3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "release/1.21.0",
        "9bb363d795b1fa362e7418c9dcee5614785a2be3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "release/1.21.1",
        "9bb363d795b1fa362e7418c9dcee5614785a2be3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMTextEditors.java",
        "release/26.1.2",
        "9bb363d795b1fa362e7418c9dcee5614785a2be3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "dev/1.19.2",
        "00073f1d2456858a89a34479a763bca483f9f320",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "dev/1.19.4",
        "a2b3ac83d8d50e35da1611a338ac3fc1ce2e6dd8",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "dev/1.20",
        "a2b3ac83d8d50e35da1611a338ac3fc1ce2e6dd8",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "dev/1.20.1",
        "d60fe085a9cbe74f8b80b048712abe6cbb286a8b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "dev/1.20.2",
        "72b2960a140ddcf0741b5f9f47a17309438a806c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "dev/1.20.3",
        "72b2960a140ddcf0741b5f9f47a17309438a806c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "dev/1.20.4",
        "72b2960a140ddcf0741b5f9f47a17309438a806c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "dev/1.21.0",
        "72b2960a140ddcf0741b5f9f47a17309438a806c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "dev/1.21.1",
        "d80eec0cbdceac858496356a40acbe19c44ceb88",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "dev/26.1.2",
        "c2375485ac9bb0ba86f14897ca14a1d808fd8b8d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "release/1.19.2",
        "a2b3ac83d8d50e35da1611a338ac3fc1ce2e6dd8",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "release/1.19.4",
        "a2b3ac83d8d50e35da1611a338ac3fc1ce2e6dd8",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "release/1.20",
        "a2b3ac83d8d50e35da1611a338ac3fc1ce2e6dd8",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "release/1.20.1",
        "d60fe085a9cbe74f8b80b048712abe6cbb286a8b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "release/1.20.2",
        "72b2960a140ddcf0741b5f9f47a17309438a806c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "release/1.20.3",
        "72b2960a140ddcf0741b5f9f47a17309438a806c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "release/1.20.4",
        "72b2960a140ddcf0741b5f9f47a17309438a806c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "release/1.21.0",
        "72b2960a140ddcf0741b5f9f47a17309438a806c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "release/1.21.1",
        "d80eec0cbdceac858496356a40acbe19c44ceb88",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/jei/SFMJEIPlugin.java",
        "release/26.1.2",
        "c2375485ac9bb0ba86f14897ca14a1d808fd8b8d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "dev/1.19.2",
        "f6f2cd292bf2db13e353efd51ed1b661a8675efa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "dev/1.19.4",
        "f6f2cd292bf2db13e353efd51ed1b661a8675efa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "dev/1.20",
        "f6f2cd292bf2db13e353efd51ed1b661a8675efa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "dev/1.20.1",
        "f6f2cd292bf2db13e353efd51ed1b661a8675efa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "dev/1.20.2",
        "6ca534f7de0f1867a75b362a279a268c37840bae",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "dev/1.20.3",
        "6ca534f7de0f1867a75b362a279a268c37840bae",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "dev/1.20.4",
        "6ca534f7de0f1867a75b362a279a268c37840bae",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "dev/1.21.0",
        "6ca534f7de0f1867a75b362a279a268c37840bae",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "dev/1.21.1",
        "6ca534f7de0f1867a75b362a279a268c37840bae",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "dev/26.1.2",
        "6ca534f7de0f1867a75b362a279a268c37840bae",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "release/1.19.2",
        "3e5ecf9ee3bdcb18b7ed0f34904c04fc0dd42468",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "release/1.19.4",
        "3e5ecf9ee3bdcb18b7ed0f34904c04fc0dd42468",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "release/1.20",
        "3e5ecf9ee3bdcb18b7ed0f34904c04fc0dd42468",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "release/1.20.1",
        "3e5ecf9ee3bdcb18b7ed0f34904c04fc0dd42468",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "release/1.20.2",
        "24980a79950af0223fe4bc360aa60f434a55630f",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "release/1.20.3",
        "24980a79950af0223fe4bc360aa60f434a55630f",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "release/1.20.4",
        "24980a79950af0223fe4bc360aa60f434a55630f",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "release/1.21.0",
        "24980a79950af0223fe4bc360aa60f434a55630f",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "release/1.21.1",
        "24980a79950af0223fe4bc360aa60f434a55630f",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/handler/TitleScreenOpenTextEditorOnLaunchHandler.java",
        "release/26.1.2",
        "24980a79950af0223fe4bc360aa60f434a55630f",
    ),
];
struct Golden {
    oid: &'static str,
    raw_sha256: &'static str,
    raw_bytes: usize,
    cr: usize,
    lf: usize,
    normalized_sha256: &'static str,
    normalized_bytes: usize,
}
const GOLDENS: [Golden; 33] = [
    Golden {
        oid: "a55ad3e4edfba8eb162fc8aae9616e7f6a79f3dd",
        raw_sha256: "sha256:2974bf6fa68215899374be475963873a18ee89f6acfc63409858764f1365175b",
        raw_bytes: 939,
        cr: 12,
        lf: 22,
        normalized_sha256: "sha256:57da7b5bf346f92d7b43701a056011ef7c124bb0aaa284b917baa0f0e337cc6e",
        normalized_bytes: 927,
    },
    Golden {
        oid: "a6ce912ea926844e3ab8a4fc88351e0612b32739",
        raw_sha256: "sha256:778270682e67c4ffc4e7aee654bd6553f7ddfff21167a13e2cc6c5a67d4b6b2d",
        raw_bytes: 713,
        cr: 17,
        lf: 17,
        normalized_sha256: "sha256:a6aaf4651dc57effd4eadffffb16ce509efe889ee812ca6fdf14bda930ec1436",
        normalized_bytes: 696,
    },
    Golden {
        oid: "f01670cf122329d37d5f6df69da5fc94552a2a5b",
        raw_sha256: "sha256:e26d919d0e4730de999dc0857ca7e620b076d5cfb73cb6c54a8b2d967972e8eb",
        raw_bytes: 700,
        cr: 0,
        lf: 17,
        normalized_sha256: "sha256:e26d919d0e4730de999dc0857ca7e620b076d5cfb73cb6c54a8b2d967972e8eb",
        normalized_bytes: 700,
    },
    Golden {
        oid: "47b87ac8ad9467cf31e47543a01994c2da6d72c3",
        raw_sha256: "sha256:ddc6d2f8b11f95ac6b083180e71155bcacbac3f73a808c9a131f51a7296e7653",
        raw_bytes: 14366,
        cr: 0,
        lf: 368,
        normalized_sha256: "sha256:ddc6d2f8b11f95ac6b083180e71155bcacbac3f73a808c9a131f51a7296e7653",
        normalized_bytes: 14366,
    },
    Golden {
        oid: "4b6d83fa0f93c7a91f4c97fe82ab8177b65e4ea4",
        raw_sha256: "sha256:f6585009d2cdb974fa12c992a581be3f8de549cf7cc3c17210a29a7076324c02",
        raw_bytes: 14382,
        cr: 0,
        lf: 368,
        normalized_sha256: "sha256:f6585009d2cdb974fa12c992a581be3f8de549cf7cc3c17210a29a7076324c02",
        normalized_bytes: 14382,
    },
    Golden {
        oid: "9ce52e450cb5ed0de3cbc3492ee05efbda127754",
        raw_sha256: "sha256:98ba634b486d333589b54016c205348884e47422517a0929abf7334e83bb1f93",
        raw_bytes: 14391,
        cr: 0,
        lf: 372,
        normalized_sha256: "sha256:98ba634b486d333589b54016c205348884e47422517a0929abf7334e83bb1f93",
        normalized_bytes: 14391,
    },
    Golden {
        oid: "281d527e9053fc559fce2341b2990fadbd780d4d",
        raw_sha256: "sha256:35d6090507e3d81fb8737029b37d8a46a3e1d5ad01c974110a5939bfab3f2432",
        raw_bytes: 13694,
        cr: 0,
        lf: 351,
        normalized_sha256: "sha256:35d6090507e3d81fb8737029b37d8a46a3e1d5ad01c974110a5939bfab3f2432",
        normalized_bytes: 13694,
    },
    Golden {
        oid: "682afc2118a0f61177bb915ce80e34a4fc999903",
        raw_sha256: "sha256:60bf9dbb4df068351f43b2f303aaf1e6d96b7baee4fc64e9a14d8f93db45d94d",
        raw_bytes: 13710,
        cr: 0,
        lf: 351,
        normalized_sha256: "sha256:60bf9dbb4df068351f43b2f303aaf1e6d96b7baee4fc64e9a14d8f93db45d94d",
        normalized_bytes: 13710,
    },
    Golden {
        oid: "758f175bf865066e6a51745f9239552705938ef9",
        raw_sha256: "sha256:dbf688fadae20053d2a53a85b12b5a0b7facfa1268ce08e7f15fd7419e09bf76",
        raw_bytes: 13731,
        cr: 0,
        lf: 355,
        normalized_sha256: "sha256:dbf688fadae20053d2a53a85b12b5a0b7facfa1268ce08e7f15fd7419e09bf76",
        normalized_bytes: 13731,
    },
    Golden {
        oid: "d89cd329f09bc830588d6f7b1af3b6af5e0a7288",
        raw_sha256: "sha256:f2daac9c9cda2eae115aaa211db508d7c51bf1f4795baf2044319b50d0897aab",
        raw_bytes: 652,
        cr: 0,
        lf: 15,
        normalized_sha256: "sha256:f2daac9c9cda2eae115aaa211db508d7c51bf1f4795baf2044319b50d0897aab",
        normalized_bytes: 652,
    },
    Golden {
        oid: "85d108feb10610ae698e3388d42c9dea7827091a",
        raw_sha256: "sha256:ced18367de52e8e7172a000bd906eb76b2879b0fdd9d275dcc55f9d4169834dd",
        raw_bytes: 507,
        cr: 0,
        lf: 13,
        normalized_sha256: "sha256:ced18367de52e8e7172a000bd906eb76b2879b0fdd9d275dcc55f9d4169834dd",
        normalized_bytes: 507,
    },
    Golden {
        oid: "dc7ce44b98d96d2b18d3a36dd50e599694dcbde3",
        raw_sha256: "sha256:b70b64eb172394696eead5abfc16b530733db42b036f29c41b92e48822669286",
        raw_bytes: 623,
        cr: 0,
        lf: 15,
        normalized_sha256: "sha256:b70b64eb172394696eead5abfc16b530733db42b036f29c41b92e48822669286",
        normalized_bytes: 623,
    },
    Golden {
        oid: "2d748aadc704a714dc6796eedda282215963e8c7",
        raw_sha256: "sha256:fc78a16f48fa912cce3bafb7c2decc82490dadea146f51febb897afa28d30081",
        raw_bytes: 1090,
        cr: 0,
        lf: 26,
        normalized_sha256: "sha256:fc78a16f48fa912cce3bafb7c2decc82490dadea146f51febb897afa28d30081",
        normalized_bytes: 1090,
    },
    Golden {
        oid: "66800c378c767d4d0ba087b433e14a8b21c5b86b",
        raw_sha256: "sha256:bdaad32bedf8212af72900d81effe92661e9aad7f8baf66db4fb0d6376a36721",
        raw_bytes: 1566,
        cr: 20,
        lf: 35,
        normalized_sha256: "sha256:4eaac695a539b611c7462df4f0f813ad237c69deb4790e5c2c9668c9be8c3793",
        normalized_bytes: 1546,
    },
    Golden {
        oid: "0ea6959a4d65c4a3ecd1dae9ed9a8487eae04415",
        raw_sha256: "sha256:0e74aede7b94604defbf4c875e25c4bdd8be40ce53f427fcc7906c719c07b042",
        raw_bytes: 1242,
        cr: 28,
        lf: 28,
        normalized_sha256: "sha256:cee7a6529cf18c95012d48d13bedcc30a1b581a19b58e8c86df8483ce7601bae",
        normalized_bytes: 1214,
    },
    Golden {
        oid: "b3bad8f2a8fa6fc3bfcc8c5b7ea7fa214c991dc4",
        raw_sha256: "sha256:23d2776a0607e912938135e3b4986518f09e5f8f266a0e88f1f9ece6dfcfa89c",
        raw_bytes: 1349,
        cr: 0,
        lf: 29,
        normalized_sha256: "sha256:23d2776a0607e912938135e3b4986518f09e5f8f266a0e88f1f9ece6dfcfa89c",
        normalized_bytes: 1349,
    },
    Golden {
        oid: "d73d2914fa7db57d7136bba0d6446b11e7d4f6c1",
        raw_sha256: "sha256:66da12dd8a2cca7fd1df794355b6e39cd9ab5f85cbfd59fe6099fd2a0a1c8118",
        raw_bytes: 1473,
        cr: 0,
        lf: 32,
        normalized_sha256: "sha256:66da12dd8a2cca7fd1df794355b6e39cd9ab5f85cbfd59fe6099fd2a0a1c8118",
        normalized_bytes: 1473,
    },
    Golden {
        oid: "eb2d0e39f889eae297ef7b73aa94c61f90f6f4e0",
        raw_sha256: "sha256:9b0a8ac3dd9c9a7f0a3475a2f229619a3fd4fcb69ebee52c9f71be49aa5473d4",
        raw_bytes: 1324,
        cr: 0,
        lf: 29,
        normalized_sha256: "sha256:9b0a8ac3dd9c9a7f0a3475a2f229619a3fd4fcb69ebee52c9f71be49aa5473d4",
        normalized_bytes: 1324,
    },
    Golden {
        oid: "2fe8f071d151a4e5fc26861a6cb7138653c0469e",
        raw_sha256: "sha256:4b7e41aee92bb423e203ed578e5b609e76662dfe613713fe2c823ea51deb80ce",
        raw_bytes: 2339,
        cr: 0,
        lf: 54,
        normalized_sha256: "sha256:4b7e41aee92bb423e203ed578e5b609e76662dfe613713fe2c823ea51deb80ce",
        normalized_bytes: 2339,
    },
    Golden {
        oid: "8791e765fb5c6bfee327c8ecaf8b077f393aeb66",
        raw_sha256: "sha256:d418bcb7704739f998a7de8f02ad1465f280977f2bfb85d6907d9a4fef4a2e94",
        raw_bytes: 2355,
        cr: 0,
        lf: 54,
        normalized_sha256: "sha256:d418bcb7704739f998a7de8f02ad1465f280977f2bfb85d6907d9a4fef4a2e94",
        normalized_bytes: 2355,
    },
    Golden {
        oid: "ce635435bf6697bc0534919c2ccab1ad7724993c",
        raw_sha256: "sha256:8baa14e8212135a015a77d409153fb9982752a1b820f3de1c2fafd11f3fa0bed",
        raw_bytes: 2345,
        cr: 0,
        lf: 54,
        normalized_sha256: "sha256:8baa14e8212135a015a77d409153fb9982752a1b820f3de1c2fafd11f3fa0bed",
        normalized_bytes: 2345,
    },
    Golden {
        oid: "6d0833663f518a532efd49eb9e77fb5e90b3fea3",
        raw_sha256: "sha256:725aed374aa1a1d654d0e6579283db96a99936aa4f9ff882decbc57a8daf3c5c",
        raw_bytes: 2060,
        cr: 0,
        lf: 48,
        normalized_sha256: "sha256:725aed374aa1a1d654d0e6579283db96a99936aa4f9ff882decbc57a8daf3c5c",
        normalized_bytes: 2060,
    },
    Golden {
        oid: "9bb363d795b1fa362e7418c9dcee5614785a2be3",
        raw_sha256: "sha256:91c9957d9a170db94e193eb3b31b29735b3fa528928d3d4873cd192a8a8b71b6",
        raw_bytes: 2050,
        cr: 0,
        lf: 48,
        normalized_sha256: "sha256:91c9957d9a170db94e193eb3b31b29735b3fa528928d3d4873cd192a8a8b71b6",
        normalized_bytes: 2050,
    },
    Golden {
        oid: "00073f1d2456858a89a34479a763bca483f9f320",
        raw_sha256: "sha256:037e3fd16c8bdd13c4e56dd91a220d7fff3c1e897373936a70ef3f935f42d10a",
        raw_bytes: 4881,
        cr: 85,
        lf: 116,
        normalized_sha256: "sha256:5c402d6d0a1fec78cf99796af8881f60e14bdafa8a56ea549ba9563a84fabc85",
        normalized_bytes: 4796,
    },
    Golden {
        oid: "a2b3ac83d8d50e35da1611a338ac3fc1ce2e6dd8",
        raw_sha256: "sha256:dce53096d3ba22e5b77e14805e71587ac94729ffaafd4e98e3e93d1606b48b03",
        raw_bytes: 3835,
        cr: 90,
        lf: 90,
        normalized_sha256: "sha256:54795a57ab08e1568c77cddb0437d09ac9f856d5f771d332b13dc911771383b4",
        normalized_bytes: 3745,
    },
    Golden {
        oid: "d60fe085a9cbe74f8b80b048712abe6cbb286a8b",
        raw_sha256: "sha256:bda40e19df89b3345b34e6af0f7c606033a8366fba99aafdb24c4b73b196b6e8",
        raw_bytes: 4593,
        cr: 116,
        lf: 116,
        normalized_sha256: "sha256:05b4a62471877c17e534b86759ef5c836ea90653db31c06cc9d4d58a3e80f096",
        normalized_bytes: 4477,
    },
    Golden {
        oid: "72b2960a140ddcf0741b5f9f47a17309438a806c",
        raw_sha256: "sha256:80eb6427a447a41d18b4b25acc83ce676ae8bf606935ca9c212ed5ad5cc0c577",
        raw_bytes: 4609,
        cr: 116,
        lf: 116,
        normalized_sha256: "sha256:623f9b1d6f3c4eda3d37489fb4f7f52dbcfb8f369303649b386cf89b1cb6fb76",
        normalized_bytes: 4493,
    },
    Golden {
        oid: "d80eec0cbdceac858496356a40acbe19c44ceb88",
        raw_sha256: "sha256:8c58a154a70686aa12dacc7f526a07712b851a327090f6e7efe0b62236298ce3",
        raw_bytes: 5668,
        cr: 152,
        lf: 152,
        normalized_sha256: "sha256:a51c40f499bbc83d1ec827e0307f124afb9a55a04b996b59e9e085ec7a439fd1",
        normalized_bytes: 5516,
    },
    Golden {
        oid: "c2375485ac9bb0ba86f14897ca14a1d808fd8b8d",
        raw_sha256: "sha256:eee1f9c63996f81b6129329f63cb58929107dc18d8ab342c8043d4176c40df08",
        raw_bytes: 5676,
        cr: 152,
        lf: 152,
        normalized_sha256: "sha256:b203034c3e0c97bfc88784ec2fd5044c3986f5a9a2799852cdb54da06b95cde9",
        normalized_bytes: 5524,
    },
    Golden {
        oid: "f6f2cd292bf2db13e353efd51ed1b661a8675efa",
        raw_sha256: "sha256:d674b2f5e6030f28b6bc1516f654c05459656f479d4531a9bee00550e338fa10",
        raw_bytes: 850,
        cr: 0,
        lf: 22,
        normalized_sha256: "sha256:d674b2f5e6030f28b6bc1516f654c05459656f479d4531a9bee00550e338fa10",
        normalized_bytes: 850,
    },
    Golden {
        oid: "6ca534f7de0f1867a75b362a279a268c37840bae",
        raw_sha256: "sha256:2f248fd117a828eb00d547ec5a12a6c8de1107ac036546ca63b5a5c2139d3360",
        raw_bytes: 854,
        cr: 0,
        lf: 22,
        normalized_sha256: "sha256:2f248fd117a828eb00d547ec5a12a6c8de1107ac036546ca63b5a5c2139d3360",
        normalized_bytes: 854,
    },
    Golden {
        oid: "3e5ecf9ee3bdcb18b7ed0f34904c04fc0dd42468",
        raw_sha256: "sha256:f709241d5f030ce64bcfb1561ad6b041233b4367ae01fcb68df27214182086d1",
        raw_bytes: 1452,
        cr: 32,
        lf: 32,
        normalized_sha256: "sha256:da0124675b96da07084eb10d5ca9819cf2ddfc99610da679af1d4ca8be04f5aa",
        normalized_bytes: 1420,
    },
    Golden {
        oid: "24980a79950af0223fe4bc360aa60f434a55630f",
        raw_sha256: "sha256:887dd61a10eb75a96e67fcf00c67aefe9e6c60dcaf0fedc88d68d4946ff4c31a",
        raw_bytes: 1456,
        cr: 32,
        lf: 32,
        normalized_sha256: "sha256:cb28f0ed35b6a086ea68851f17dddc11a06bcbcfdda2486b86cfc4c18a6efe0a",
        normalized_bytes: 1424,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    scope: String,
    source_context_commits: BTreeMap<String, String>,
    owners: Vec<Owner>,
    proposed_companion_owner: Owner,
    inputs: Vec<Input>,
    raw_variants: Vec<Raw>,
}
#[derive(Facet)]
struct Owner {
    id: String,
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct Input {
    path: String,
    stage_path: String,
    core_path: String,
    source_sha256: String,
    source_bytes: usize,
    membership: String,
    member_features: Vec<String>,
    witnesses: BTreeMap<String, String>,
}
#[derive(Facet)]
struct Raw {
    blob: String,
    raw_sha256: String,
    raw_bytes: usize,
    raw_cr_count: usize,
    raw_lf_count: usize,
    raw_final_lf: bool,
    normalized_sha256: String,
    normalized_bytes: usize,
    removed_cr: usize,
}

struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    normalized: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core-baseline-client-registration-slice@1"
                && ledger.scope == "seven_baseline_client_registration_files_only",
            "wrong baseline client registration scope"
        );
        ensure!(
            ledger.source_context_commits
                == COMMITS
                    .into_iter()
                    .map(|(k, v)| (k.to_owned(), v.to_owned()))
                    .collect(),
            "frozen source commits changed"
        );
        ensure!(
            ledger.inputs.len() == 7 && ledger.raw_variants.len() == 33 && ledger.owners.len() == 7,
            "baseline client registration cohort widened"
        );
        let mut seen = BTreeSet::new();
        for (index, path) in PATHS.iter().enumerate() {
            let input = ledger
                .inputs
                .iter()
                .find(|input| input.path == *path)
                .ok_or_else(|| eyre::eyre!("missing input evidence: {path}"))?;
            let (_, digest, count) = TEMPLATES[index];
            ensure!(
                seen.insert(input.path.clone())
                    && input.stage_path
                        == format!(
                            "platform/cli/sfm-propagate-changes/target/core-baseline-client-registration-stage-v1/{}",
                            path.rsplit('/').next().expect("fixed basename")
                        )
                    && input.core_path == format!("platform/minecraft/core-liquid-template/{path}")
                    && input.source_sha256 == digest
                    && input.source_bytes == count
                    && input.membership == "shared_all_ten_targets_all_feature_masks"
                    && input.member_features == [OWNERS[index]],
                "authored identity or shared membership changed"
            );
            let witnesses = COMMITS
                .into_iter()
                .map(|(context, _)| {
                    Ok((context.to_owned(), expected_oid(path, context)?.to_owned()))
                })
                .collect::<Result<BTreeMap<_, _>>>()?;
            ensure!(
                input.witnesses == witnesses,
                "all20 source witness map changed"
            );
            validate_shared_rule(&core.metadata, path)?;
        }
        let mut seen = BTreeSet::new();
        for owner in &ledger.owners {
            let (targets, requires) = owner_contract(&owner.id)?;
            ensure!(
                seen.insert(owner.id.clone())
                    && owner
                        .supported_targets
                        .iter()
                        .map(String::as_str)
                        .eq(targets.iter().copied())
                    && owner
                        .requires
                        .iter()
                        .map(String::as_str)
                        .eq(requires.iter().copied()),
                "reviewed owner support or prerequisite changed: {}",
                owner.id
            );
            let registered =
                core.features.0.get(&owner.id).ok_or_else(|| {
                    eyre::eyre!("source member owner not registered: {}", owner.id)
                })?;
            ensure!(
                registered.supported_targets == owner.supported_targets
                    && registered.requires == owner.requires,
                "source member registry/evidence mismatch: {}",
                owner.id
            );
        }
        let companion = ledger.proposed_companion_owner;
        ensure!(
            companion.id == "item_inspection"
                && companion.supported_targets == ["1.19.2", "1.19.4"]
                && companion.requires.is_empty(),
            "generic item-inspection contract changed"
        );
        let definition = core
            .features
            .0
            .get("item_inspection")
            .ok_or_else(|| eyre::eyre!("generic item-inspection owner must be registered"))?;
        ensure!(
            definition.supported_targets == companion.supported_targets
                && definition.requires == companion.requires,
            "generic inspection acquired unreviewed prerequisites"
        );
        let mut seen = BTreeSet::new();
        for row in &ledger.raw_variants {
            let pin = golden(&row.blob)?;
            ensure!(
                seen.insert(row.blob.clone())
                    && row.raw_sha256 == pin.raw_sha256
                    && row.raw_bytes == pin.raw_bytes
                    && row.raw_cr_count == pin.cr
                    && row.raw_lf_count == pin.lf
                    && row.raw_final_lf
                    && row.normalized_sha256 == pin.normalized_sha256
                    && row.normalized_bytes == pin.normalized_bytes
                    && row.removed_cr == pin.cr,
                "raw/normalized golden identity changed"
            );
        }
        ensure!(
            GOLDENS.iter().filter(|pin| pin.cr > 0).count() == 12
                && GOLDENS.iter().map(|pin| pin.cr).sum::<usize>() == 852,
            "EOL normalization scope widened"
        );
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|pin| pin.oid.to_owned()).collect(),
        )?;
        let mut normalized = BTreeMap::new();
        for pin in &GOLDENS {
            normalized.insert(pin.oid.to_owned(), verify_raw(&raw[pin.oid], pin)?);
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "all seven actual core templates must be promoted; no staged fallback"
        );
        Ok(Self {
            core,
            inventory,
            normalized,
        })
    }
    fn context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        let mut pending = requested
            .iter()
            .map(|id| (*id).to_owned())
            .collect::<Vec<_>>();
        let mut enabled = BTreeSet::new();
        while let Some(id) = pending.pop() {
            if !enabled.insert(id.clone()) {
                continue;
            }
            let definition = self
                .core
                .features
                .0
                .get(&id)
                .ok_or_else(|| eyre::eyre!("unknown owner {id}"))?;
            ensure!(
                definition
                    .supported_targets
                    .iter()
                    .any(|supported| supported == target),
                "unsupported owner {id} on {target}"
            );
            pending.extend(definition.requires.iter().cloned());
        }
        let enabled = enabled.iter().map(String::as_str).collect::<Vec<_>>();
        self.core.context(target, &enabled)
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let input = selected
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("released registration omitted"))?;
        ensure!(
            input.input == path && !selected.omitted_paths.contains(path),
            "registration gained alternate or optional input"
        );
        let source = self.core.read_source(path)?;
        verify_template(path, &source)?;
        Ok(render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes())
    }
    fn expected(&self, path: &str, target: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let index = PATHS
            .iter()
            .position(|candidate| *candidate == path)
            .ok_or_else(|| eyre::eyre!("unknown registrar"))?;
        let member_enabled =
            context.features[OWNERS[index]] && (index != 3 || context.features["workspace_panels"]);
        let environment = if member_enabled { "dev" } else { "release" };
        Ok(self.normalized[expected_oid(path, &format!("{environment}/{target}"))?].clone())
    }
    fn supported_requests(&self, target: &str) -> Vec<&'static str> {
        OWNERS
            .into_iter()
            .filter(|owner| {
                self.core.features.0[*owner]
                    .supported_targets
                    .iter()
                    .any(|value| value == target)
            })
            .collect()
    }
}
fn owner_contract(id: &str) -> Result<(&'static [&'static str], &'static [&'static str])> {
    Ok(match id {
        "touch_display" => (&["1.19.2", "1.19.4"], &["packet_values", "image_resources"]),
        "command_palette" => (
            &TARGETS,
            &["client_actions", "client_theme", "keyboard_profiles"],
        ),
        "client_manager_gui" => (&["1.19.2"], &["client_manager"]),
        "client_overlay_scenes" => (&["1.19.2", "1.19.4"], &[]),
        "canvas_text_editor" => (&TARGETS, &[]),
        "item_details_jei" => (&["1.19.2"], &["item_inspection"]),
        "client_launch_screen" => (&TARGETS, &["client_properties"]),
        _ => eyre::bail!("unreviewed registration member owner"),
    })
}
fn expected_oid(path: &str, context: &str) -> Result<&'static str> {
    WITNESSES
        .iter()
        .find(|(p, c, _)| *p == path && *c == context)
        .map(|(_, _, oid)| *oid)
        .ok_or_else(|| eyre::eyre!("missing frozen source witness"))
}
fn golden(oid: &str) -> Result<&'static Golden> {
    GOLDENS
        .iter()
        .find(|pin| pin.oid == oid)
        .ok_or_else(|| eyre::eyre!("unreviewed raw witness"))
}
fn verify_raw(bytes: &[u8], pin: &Golden) -> Result<Vec<u8>> {
    ensure!(
        bytes.len() == pin.raw_bytes
            && sha256(bytes) == pin.raw_sha256
            && bytes.iter().filter(|&&b| b == b'\r').count() == pin.cr
            && bytes.iter().filter(|&&b| b == b'\n').count() == pin.lf
            && bytes.ends_with(b"\n"),
        "raw source identity changed"
    );
    ensure!(!bytes.starts_with(&[0xef, 0xbb, 0xbf]), "unexpected BOM");
    let source = std::str::from_utf8(bytes)?;
    ensure!(
        !source.replace("\r\n", "").contains('\r'),
        "lone CR is not approved"
    );
    let normalized = source.replace("\r\n", "\n").into_bytes();
    ensure!(
        normalized.len() == pin.normalized_bytes
            && sha256(&normalized) == pin.normalized_sha256
            && bytes.len() - normalized.len() == pin.cr
            && normalized.ends_with(b"\n"),
        "normalization exceeded the exact EOL-only scope"
    );
    Ok(normalized)
}
fn verify_template(path: &str, bytes: &[u8]) -> Result<()> {
    let (_, digest, count) = TEMPLATES
        .iter()
        .find(|(name, _, _)| *name == path)
        .ok_or_else(|| eyre::eyre!("unreviewed core source"))?;
    // Preserve the original registrar ledger while rendering today's source.
    // The overlay host now requires its actual panel API at all three seams.
    let historical = if path == PATHS[3] {
        reviewed_pre_panel_overlay_template(bytes)?
    } else {
        bytes.to_vec()
    };
    ensure!(
        historical.len() == *count
            && sha256(&historical) == *digest
            && !historical.contains(&b'\r')
            && historical.ends_with(b"\n"),
        "authored core bytes changed: {path}"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn reviewed_pre_panel_overlay_template(bytes: &[u8]) -> Result<Vec<u8>> {
    ensure!(
        bytes.len() == 4_065
            && sha256(bytes)
                == "sha256:4d88dbb3c656c3ec84ef0322a216b3a0151e273d12ec37944ab5b3e3c5f4ab86",
        "current overlay panel-API refinement changed"
    );
    let source = std::str::from_utf8(bytes)?;
    let current = "{% if features.client_overlay_scenes and features.workspace_panels %}";
    ensure!(
        source.matches(current).count() == 3,
        "overlay import/member/registration guard count changed"
    );
    Ok(source
        .replace(current, "{% if features.client_overlay_scenes %}")
        .into_bytes())
}
fn validate_shared_rule(metadata: &CoreProjectInputs, path: &str) -> Result<()> {
    if let Some(rules) = metadata.source_rules.get(path) {
        ensure!(
            rules.len() == 1
                && rules[0].input == path
                && rules[0].when.targets.is_empty()
                && rules[0].when.all_features.is_empty()
                && rules[0].when.any_features.is_empty()
                && rules[0].when.none_features.is_empty(),
            "released registration must stay one unconditional shared input"
        );
    }
    Ok(())
}
#[test]
fn baseline_client_registrars_match_all_140_frozen_source_contexts() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        for enabled in [false, true] {
            let mut requested = if enabled {
                fixture.supported_requests(target)
            } else {
                Vec::new()
            };
            // Full historical output needs the real host API as well as its
            // scene owner. Single-owner requests are tested independently below.
            if enabled && matches!(target, "1.19.2" | "1.19.4") {
                requested.push("workspace_panels");
            }
            let context = fixture.context(target, &requested)?;
            for path in PATHS {
                let actual = fixture.render(path, &context)?;
                let environment = if enabled { "dev" } else { "release" };
                let expected =
                    &fixture.normalized[expected_oid(path, &format!("{environment}/{target}"))?];
                assert_eq!(&actual, expected, "{environment}/{target}/{path}");
            }
        }
    }
    Ok(())
}
#[test]
fn overlay_panel_refinement_preserves_history_and_independent_owner_masks() -> Result<()> {
    let fixture = Fixture::load()?;
    let source = fixture.core.read_source(PATHS[3])?;
    verify_template(PATHS[3], &source)?;
    let historical = reviewed_pre_panel_overlay_template(&source)?;
    assert_eq!(historical.len(), TEMPLATES[3].2);
    assert_eq!(sha256(&historical), TEMPLATES[3].1);
    for mutation in [
        String::from_utf8(source.clone())?.replacen(
            "{% if features.client_overlay_scenes and features.workspace_panels %}",
            "{% if features.client_overlay_scenes %}",
            1,
        ),
        format!("{}\n// unrelated mutation\n", std::str::from_utf8(&source)?),
    ] {
        assert!(verify_template(PATHS[3], mutation.as_bytes()).is_err());
    }
    for target in ["1.19.2", "1.19.4"] {
        for requested in [
            &[][..],
            &["client_overlay_scenes"][..],
            &["workspace_panels"][..],
            &["client_overlay_scenes", "workspace_panels"][..],
        ] {
            let context = fixture.context(target, requested)?;
            let actual = fixture.render(PATHS[3], &context)?;
            assert_eq!(actual, fixture.expected(PATHS[3], target, &context)?);
            let text = std::str::from_utf8(&actual)?;
            let host_enabled = requested.len() == 2;
            assert_eq!(
                text.contains("import ca.teamdman.sfm.client.overlay.SFMClientOverlayHost;"),
                host_enabled
            );
            assert_eq!(text.contains("CLIENT_SCENE_OVERLAY"), host_enabled);
            assert_eq!(text.contains("\"client_scene\""), host_enabled);
            assert!(text.contains("LABEL_GUN_REMINDER_OVERLAY"));
            assert!(text.contains("NETWORK_TOOL_REMINDER_OVERLAY"));
        }
    }
    Ok(())
}
#[test]
fn registrar_member_requests_keep_released_neighbors_and_target_support() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        for owner in OWNERS {
            if !fixture.supported_requests(target).contains(&owner) {
                assert!(
                    fixture.context(target, &[owner]).is_err(),
                    "unsupported {owner}/{target}"
                );
                continue;
            }
            let context = fixture.context(target, &[owner])?;
            for path in PATHS {
                assert_eq!(
                    fixture.render(path, &context)?,
                    fixture.expected(path, target, &context)?,
                    "{owner}/{target}/{path}"
                );
            }
        }
    }
    let jei = fixture.context("1.19.2", &["item_details_jei"])?;
    assert!(jei.features["item_inspection"]);
    assert!(!jei.features["packet_values"]);
    assert!(!jei.features["workspace_panels"]);
    assert!(!jei.features["client_actions"]);
    let launch = fixture.context("1.19.2", &["client_launch_screen"])?;
    assert!(launch.features["client_properties"]);
    assert!(!launch.features["command_palette"]);
    assert!(!launch.features["canvas_text_editor"]);
    let basic = fixture.context("1.19.2", &["client_actions"])?;
    for path in PATHS {
        assert_eq!(
            fixture.render(path, &basic)?,
            fixture.expected(path, "1.19.2", &basic)?
        );
    }
    Ok(())
}
#[test]
fn baseline_client_api_adapters_and_noop_members_are_preserved() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let context = fixture.context(target, &[])?;
        let render = |index| -> Result<String> {
            Ok(String::from_utf8(fixture.render(PATHS[index], &context)?)?)
        };
        let mappings = render(1)?;
        if target == "26.1.2" {
            assert!(mappings.contains("KeyMapping.Category"));
            assert!(
                mappings.contains("Window windowHandle = Minecraft.getInstance().getWindow();")
            );
            assert!(!mappings.contains("SFM_KEY_CATEGORY.key().get()"));
        } else {
            assert!(mappings.contains("SFM_KEY_CATEGORY.key().get()"));
        }
        assert!(!mappings.contains("COMMAND_PALETTE_KEY"));
        let menu = render(2)?;
        assert!(!menu.contains("ClientManagerScreen"));
        if matches!(target, "1.21.0" | "1.21.1" | "26.1.2") {
            assert!(menu.contains("RegisterMenuScreensEvent"));
        } else {
            assert!(menu.contains("public static void register()"));
        }
        assert_eq!(menu.contains("registerPip("), target == "26.1.2");
        let overlay = render(3)?;
        assert!(overlay.contains("LABEL_GUN_REMINDER_OVERLAY"));
        assert!(overlay.contains("NETWORK_TOOL_REMINDER_OVERLAY"));
        assert!(!overlay.contains("CLIENT_SCENE_OVERLAY"));
        assert_eq!(
            overlay.contains("RegisterGuiLayersEvent"),
            matches!(target, "1.21.0" | "1.21.1" | "26.1.2")
        );
        let editors = render(4)?;
        assert!(editors.contains("SFMTextEditScreenV1Registration"));
        assert!(editors.contains("SFMTextEditScreenV2Registration"));
        assert!(!editors.contains("SFMTextEditorV3Registration"));
        assert!(!editors.contains("SFMDrawCanvasTextEditorRegistration"));
        let jei = render(5)?;
        assert!(!jei.contains("SFMItemInspectionDocument"));
        assert!(!jei.contains("hoveredItemStack()"));
        assert_eq!(
            jei.contains("getJeiRuntime()"),
            !matches!(target, "1.19.2" | "1.19.4" | "1.20")
        );
        if target == "26.1.2" {
            assert!(jei.contains("Identifier getPluginUid()"));
            assert!(jei.contains("registration.addCraftingStation("));
            assert!(jei.contains("ServerLifecycleHooks.getCurrentServer().getRecipeManager()"));
        }
        let title = render(6)?;
        assert!(title.contains("firstTime = false;"));
        assert!(!title.contains("SFMProperties"));
        assert!(title.contains("SFMScreenChangeHelpers.createProgramEditScreen(ctx)"));
        let enabled = fixture.context(target, &["canvas_text_editor"])?;
        let canvas = String::from_utf8(fixture.render(PATHS[4], &enabled)?)?;
        assert_eq!(
            canvas.contains("SFMTextEditorV3Registration"),
            matches!(target, "1.19.2" | "1.19.4")
        );
        assert_eq!(
            canvas.contains("SFMDrawCanvasTextEditorRegistration"),
            !matches!(target, "1.19.2" | "1.19.4")
        );
    }
    assert_eq!(fixture.context("1.21.0", &[])?.minecraft_version, "1.21");
    Ok(())
}
#[test]
fn shared_registration_edit_reaches_all_ten_targets_in_isolated_tree() -> Result<()> {
    let fixture = Fixture::load()?;
    let path = PATHS[2];
    let original = fixture.core.read_source(path)?;
    let mut edited = original.clone();
    edited.extend_from_slice(b"// isolated shared registration edit\n");
    let temp = tempfile::tempdir()?;
    let core = temp.path().join(super::core_inputs::CORE_ROOT);
    let destination = core.join(path);
    fs::create_dir_all(destination.parent().expect("fixed parent"))?;
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
        assert_eq!(selected.inputs[path].input, path);
        let bytes = read_bounded(
            &checked_file(&core, &selected.inputs[path].input)?,
            1024 * 1024,
        )?;
        let actual = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
        let mut expected = fixture.render(path, &context)?;
        expected.extend_from_slice(b"// isolated shared registration edit\n");
        assert_eq!(actual.as_bytes(), expected);
    }
    assert_eq!(fixture.core.read_source(path)?, original);
    Ok(())
}
#[test]
fn changed_raw_normalization_owner_or_whole_class_membership_refuses() -> Result<()> {
    let fixture = Fixture::load()?;
    let raw = read_git_blobs(
        &fixture.core.repository,
        &BTreeSet::from([GOLDENS[0].oid.to_owned()]),
    )?;
    let mut bytes = raw[GOLDENS[0].oid].clone();
    bytes.push(b' ');
    assert!(verify_raw(&bytes, &GOLDENS[0]).is_err());
    for (index, path) in PATHS.iter().enumerate() {
        let source = fixture.core.read_source(path)?;
        let mut changed = source.clone();
        changed.push(b' ');
        assert!(verify_template(path, &changed).is_err());
        let mut context = fixture.context("1.19.2", &[])?;
        context.features.remove(OWNERS[index]);
        assert!(
            render_java_source(std::str::from_utf8(&source)?, &context).is_err(),
            "inactive unregistered owner not rejected: {path}"
        );
        let mut metadata = fixture.core.metadata.clone();
        metadata.source_rules.insert(
            (*path).to_owned(),
            vec![super::core_inputs::InputVariant {
                input: (*path).to_owned(),
                when: super::core_inputs::InputPredicate {
                    targets: vec![],
                    all_features: vec![OWNERS[index].to_owned()],
                    any_features: vec![],
                    none_features: vec![],
                },
                template: false,
            }],
        );
        assert!(validate_shared_rule(&metadata, path).is_err());
    }
    Ok(())
}
