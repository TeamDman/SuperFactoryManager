//! Post-promotion contracts for four released test gap classes.
//!
//! Git objects are bounded offline witnesses, never production rendering inputs.
//! Actual promoted core membership and reviewed owners are required. Rendering
//! does not prove JUnit, GameTest, Mekanism linkage or native compilation.

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

const LEDGER: &str = "docs/tasks/sfm-core-released-test-gaps-slice.json";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const OWNERS: [&str; 7] = [
    "mekanism_sidedness_linter",
    "editor_v2_cursor_advance",
    "packet_computation",
    "packet_transport_private",
    "client_inbox",
    "client_theme",
    "client_properties",
];
const PATHS: [&str; 4] = [
    "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
    "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
    "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
    "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
];
const TEMPLATES: [(&str, &str, usize); 4] = [
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "sha256:6711c3fb616f247290b94f9c7e841748016053311f7870424ef3b0ba5324955b",
        1887,
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "sha256:1b0c4afc4bd1e6dfa18763532705242ebf7be6d7ea35829da94afb3ac966174a",
        6101,
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "sha256:2452989046d075ed60a62e837a26f2420978cc97bee36f674366a7c9949435c9",
        25326,
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "sha256:6af2a183edecf123d26bcafec4d4d6b5cb516ead8421cd6b0bc7a1bc625fba10",
        34114,
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
const WITNESSES: [(&str, &str, &str); 80] = [
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "dev/1.19.2",
        "b38e959873f309c6748f65cebf041e34da8061b5",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "dev/1.19.4",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "dev/1.20",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "dev/1.20.1",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "dev/1.20.2",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "dev/1.20.3",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "dev/1.20.4",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "dev/1.21.0",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "dev/1.21.1",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "dev/26.1.2",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "release/1.19.2",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "release/1.19.4",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "release/1.20",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "release/1.20.1",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "release/1.20.2",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "release/1.20.3",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "release/1.20.4",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "release/1.21.0",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "release/1.21.1",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MekanismNullIoDirectionGameTest.java",
        "release/26.1.2",
        "61e6408ea1c182378d772d9e510969cd44e6d225",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "dev/1.19.2",
        "559950607461b338eb73dae84cab31011177d864",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "dev/1.19.4",
        "559950607461b338eb73dae84cab31011177d864",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "dev/1.20",
        "559950607461b338eb73dae84cab31011177d864",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "dev/1.20.1",
        "559950607461b338eb73dae84cab31011177d864",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "dev/1.20.2",
        "559950607461b338eb73dae84cab31011177d864",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "dev/1.20.3",
        "559950607461b338eb73dae84cab31011177d864",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "dev/1.20.4",
        "559950607461b338eb73dae84cab31011177d864",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "dev/1.21.0",
        "559950607461b338eb73dae84cab31011177d864",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "dev/1.21.1",
        "559950607461b338eb73dae84cab31011177d864",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "dev/26.1.2",
        "559950607461b338eb73dae84cab31011177d864",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "release/1.19.2",
        "36db8c768d940f04aa0cf3b3eebad3642a53f10a",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "release/1.19.4",
        "36db8c768d940f04aa0cf3b3eebad3642a53f10a",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "release/1.20",
        "36db8c768d940f04aa0cf3b3eebad3642a53f10a",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "release/1.20.1",
        "36db8c768d940f04aa0cf3b3eebad3642a53f10a",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "release/1.20.2",
        "36db8c768d940f04aa0cf3b3eebad3642a53f10a",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "release/1.20.3",
        "36db8c768d940f04aa0cf3b3eebad3642a53f10a",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "release/1.20.4",
        "36db8c768d940f04aa0cf3b3eebad3642a53f10a",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "release/1.21.0",
        "36db8c768d940f04aa0cf3b3eebad3642a53f10a",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "release/1.21.1",
        "36db8c768d940f04aa0cf3b3eebad3642a53f10a",
    ),
    (
        "src/test/java/ca/teamdman/sfm/test/text_editor/TextEditorTests.java",
        "release/26.1.2",
        "36db8c768d940f04aa0cf3b3eebad3642a53f10a",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "dev/1.19.2",
        "69776d7146670517f14c71545c1722cac41089df",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "dev/1.19.4",
        "69776d7146670517f14c71545c1722cac41089df",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "dev/1.20",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "dev/1.20.1",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "dev/1.20.2",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "dev/1.20.3",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "dev/1.20.4",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "dev/1.21.0",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "dev/1.21.1",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "dev/26.1.2",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "release/1.19.2",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "release/1.19.4",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "release/1.20",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "release/1.20.1",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "release/1.20.2",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "release/1.20.3",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "release/1.20.4",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "release/1.21.0",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "release/1.21.1",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLIntellisenseTests.java",
        "release/26.1.2",
        "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "dev/1.19.2",
        "ea428df66690ebb72ddae0c1460331396706ec66",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "dev/1.19.4",
        "ea428df66690ebb72ddae0c1460331396706ec66",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "dev/1.20",
        "a090acc94faaf59fc456e080204ff66b37db48ec",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "dev/1.20.1",
        "a090acc94faaf59fc456e080204ff66b37db48ec",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "dev/1.20.2",
        "aaff9069a061bd1a83ba8c75a000b4ad5d832edd",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "dev/1.20.3",
        "aaff9069a061bd1a83ba8c75a000b4ad5d832edd",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "dev/1.20.4",
        "aaff9069a061bd1a83ba8c75a000b4ad5d832edd",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "dev/1.21.0",
        "aaff9069a061bd1a83ba8c75a000b4ad5d832edd",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "dev/1.21.1",
        "aaff9069a061bd1a83ba8c75a000b4ad5d832edd",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "dev/26.1.2",
        "aaff9069a061bd1a83ba8c75a000b4ad5d832edd",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "release/1.19.2",
        "246cb5415bea1470adb1c47e3d6380b30ab18231",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "release/1.19.4",
        "246cb5415bea1470adb1c47e3d6380b30ab18231",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "release/1.20",
        "246cb5415bea1470adb1c47e3d6380b30ab18231",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "release/1.20.1",
        "246cb5415bea1470adb1c47e3d6380b30ab18231",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "release/1.20.2",
        "e2b467dbc0899215be4b2fb7d2b5dc22a49e44a6",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "release/1.20.3",
        "e2b467dbc0899215be4b2fb7d2b5dc22a49e44a6",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "release/1.20.4",
        "e2b467dbc0899215be4b2fb7d2b5dc22a49e44a6",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "release/1.21.0",
        "e2b467dbc0899215be4b2fb7d2b5dc22a49e44a6",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "release/1.21.1",
        "e2b467dbc0899215be4b2fb7d2b5dc22a49e44a6",
    ),
    (
        "src/test/java/ca/teamdman/sfml/test/SFMLTests.java",
        "release/26.1.2",
        "e2b467dbc0899215be4b2fb7d2b5dc22a49e44a6",
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
const GOLDENS: [Golden; 11] = [
    Golden {
        oid: "b38e959873f309c6748f65cebf041e34da8061b5",
        raw_sha256: "sha256:14f1a52a7e4a22570570a345bfad065e2b641dc487e617dc3fb7c9c47f57396b",
        raw_bytes: 934,
        cr: 0,
        lf: 32,
        normalized_sha256: "sha256:14f1a52a7e4a22570570a345bfad065e2b641dc487e617dc3fb7c9c47f57396b",
        normalized_bytes: 934,
    },
    Golden {
        oid: "61e6408ea1c182378d772d9e510969cd44e6d225",
        raw_sha256: "sha256:6045a6a2ec17c5db35e9055651dbf395c52cdd970fa34496151902c34abd1fd4",
        raw_bytes: 962,
        cr: 0,
        lf: 37,
        normalized_sha256: "sha256:6045a6a2ec17c5db35e9055651dbf395c52cdd970fa34496151902c34abd1fd4",
        normalized_bytes: 962,
    },
    Golden {
        oid: "559950607461b338eb73dae84cab31011177d864",
        raw_sha256: "sha256:933a84267aa97b0d3a26c83a70047b05a91db2b5f997ded672ba8cf6d5c2a641",
        raw_bytes: 1282,
        cr: 0,
        lf: 37,
        normalized_sha256: "sha256:933a84267aa97b0d3a26c83a70047b05a91db2b5f997ded672ba8cf6d5c2a641",
        normalized_bytes: 1282,
    },
    Golden {
        oid: "36db8c768d940f04aa0cf3b3eebad3642a53f10a",
        raw_sha256: "sha256:9bbb7ae08d7e5d68558cb17794eeefa5a85050a924bae8d42c98c31f1bfda385",
        raw_bytes: 4797,
        cr: 89,
        lf: 89,
        normalized_sha256: "sha256:cec9a2d71dbcbf6de93e85030c97a748e283b605aa3e86381e704d33f4efa194",
        normalized_bytes: 4708,
    },
    Golden {
        oid: "69776d7146670517f14c71545c1722cac41089df",
        raw_sha256: "sha256:c72c2fc8ddd54c0592dda4b770ce5fe8a2a16f2245d037bea0fc9d03f4a40c2a",
        raw_bytes: 25248,
        cr: 565,
        lf: 601,
        normalized_sha256: "sha256:09d237aea8a8f5193085059962cfd871658354bdbcc56d11e2b58b3745b4573d",
        normalized_bytes: 24683,
    },
    Golden {
        oid: "9953a24df82b7cd8da8103e9cd5b19c4470ccd8b",
        raw_sha256: "sha256:96ed33c409218f32d1f243301d904a55500d8f5ea68fae5157845ad35191fe36",
        raw_bytes: 24011,
        cr: 571,
        lf: 571,
        normalized_sha256: "sha256:a7b45f3ad21a204498d8d7ba6cbd5a8c7e122c51a515012ba886e571aefc94e0",
        normalized_bytes: 23440,
    },
    Golden {
        oid: "ea428df66690ebb72ddae0c1460331396706ec66",
        raw_sha256: "sha256:3288ee95ebbf3eed110e737ba5a88ad65a2cfdf681615749f4fd7b4673622657",
        raw_bytes: 32451,
        cr: 0,
        lf: 936,
        normalized_sha256: "sha256:3288ee95ebbf3eed110e737ba5a88ad65a2cfdf681615749f4fd7b4673622657",
        normalized_bytes: 32451,
    },
    Golden {
        oid: "a090acc94faaf59fc456e080204ff66b37db48ec",
        raw_sha256: "sha256:6455af9c03d7e9cd2cbdf60fd36576d02f7e142e91fc7b621ca3bd7e7a10a87f",
        raw_bytes: 28380,
        cr: 0,
        lf: 845,
        normalized_sha256: "sha256:6455af9c03d7e9cd2cbdf60fd36576d02f7e142e91fc7b621ca3bd7e7a10a87f",
        normalized_bytes: 28380,
    },
    Golden {
        oid: "aaff9069a061bd1a83ba8c75a000b4ad5d832edd",
        raw_sha256: "sha256:1565454ff45534c46d75b7fc481d49067409b06dd88ed80d326413f4176d7fbe",
        raw_bytes: 28392,
        cr: 0,
        lf: 845,
        normalized_sha256: "sha256:1565454ff45534c46d75b7fc481d49067409b06dd88ed80d326413f4176d7fbe",
        normalized_bytes: 28392,
    },
    Golden {
        oid: "246cb5415bea1470adb1c47e3d6380b30ab18231",
        raw_sha256: "sha256:91b1014deb6c5578ad83e8750b663aaeb94e61b2ae9f6564b03c9eae29d78c8d",
        raw_bytes: 27597,
        cr: 0,
        lf: 823,
        normalized_sha256: "sha256:91b1014deb6c5578ad83e8750b663aaeb94e61b2ae9f6564b03c9eae29d78c8d",
        normalized_bytes: 27597,
    },
    Golden {
        oid: "e2b467dbc0899215be4b2fb7d2b5dc22a49e44a6",
        raw_sha256: "sha256:1890952a577769029f793d5b74b2501f3cd07a5bac1b79003bd1379f8313087b",
        raw_bytes: 27609,
        cr: 0,
        lf: 823,
        normalized_sha256: "sha256:1890952a577769029f793d5b74b2501f3cd07a5bac1b79003bd1379f8313087b",
        normalized_bytes: 27609,
    },
];
const SFML_PATCHES_JSON: &str = r###"[
  {
    "targets": [
      "1.19.2",
      "1.19.4",
      "1.20",
      "1.20.1",
      "1.20.2",
      "1.20.3",
      "1.20.4",
      "1.21.0",
      "1.21.1",
      "26.1.2"
    ],
    "all_features": [
      "client_properties"
    ],
    "any_features": [],
    "old": "",
    "new": "import ca.teamdman.sfm.properties.SFMProperties;\n",
    "insert_old_after": ""
  },
  {
    "targets": [
      "1.19.2",
      "1.19.4"
    ],
    "all_features": [
      "packet_transport_private"
    ],
    "any_features": [],
    "old": "",
    "new": "import ca.teamdman.sfml.ast.*;\nimport ca.teamdman.sfm.common.value.SFMValuePattern;\n",
    "insert_old_after": ""
  },
  {
    "targets": [
      "1.19.2",
      "1.19.4",
      "1.20",
      "1.20.1",
      "1.20.2",
      "1.20.3",
      "1.20.4",
      "1.21.0",
      "1.21.1",
      "26.1.2"
    ],
    "all_features": [
      "client_theme"
    ],
    "any_features": [],
    "old": "",
    "new": "import net.minecraft.ChatFormatting;\n",
    "insert_old_after": ""
  },
  {
    "targets": [
      "1.19.2",
      "1.19.4",
      "1.20",
      "1.20.1",
      "1.20.2",
      "1.20.3",
      "1.20.4",
      "1.21.0",
      "1.21.1",
      "26.1.2"
    ],
    "all_features": [
      "client_properties"
    ],
    "any_features": [],
    "old": "import java.nio.file.Paths;\n",
    "new": "",
    "insert_old_after": "import java.nio.file.Path;\n"
  },
  {
    "targets": [
      "1.19.2",
      "1.19.4"
    ],
    "all_features": [
      "packet_transport_private"
    ],
    "any_features": [],
    "old": "",
    "new": "    public void packetComputationLanguageSurfaceBuildsDefinitionsAndStatements() {\n        String source = \"\"\"\n                let me be player of TeamDman\n                let JobId be like guid\n                let Request be like object with field type of \"Request\"\n                    and field prompt like string\n                    and field JobId\n                let Response be like object with field type of \"Response\"\n                    and field JobId like guid\n                    and field text like string\n\n                every 20 ticks do\n                    input WITH CAPABILITY sfm:text from chest as userinput\n                    let userinputstring be string of invoke sfm:text/read with userinput\n                    let request be Request with field prompt of userinputstring\n                        and field JobId of new guid\n                    create input sfm:packet with request\n                    broadcast to me\n                    output to chest1\n                end\n\n                every 20 ticks do\n                    input like Response from inbox as r\n                    output to responsechest\n                end\n                \"\"\";\n\n        assertNoCompileErrors(source);\n        Program program = compile(source);\n        assertEquals(\"TeamDman\", program.definitions().player(\"ME\").orElseThrow());\n        assertSame(SFMValuePattern.GUID, program.definitions().pattern(\"jobid\").orElseThrow());\n        assertTrue(program.definitions().pattern(\"request\").orElseThrow() instanceof SFMValuePattern.ObjectPattern);\n        assertEquals(\n                java.util.List.of(\n                        InputStatement.class,\n                        LetStatement.class,\n                        LetStatement.class,\n                        CreateInputStatement.class,\n                        BroadcastStatement.class,\n                        OutputStatement.class\n                ),\n                program.triggers().get(0).getStatements().get(0).getStatements().stream()\n                        .map(Object::getClass)\n                        .toList()\n        );\n        assertNoCompileErrors(program.toString());\n        assertTrue(program.astBuilder().getNodesUnderCursor(source.indexOf(\"broadcast\"))\n                                  .stream()\n                                  .map(com.mojang.datafixers.util.Pair::getFirst)\n                                  .anyMatch(BroadcastStatement.class::isInstance));\n        assertTrue(program.astBuilder().getNodesUnderCursor(source.indexOf(\"let Request\"))\n                                  .stream()\n                                  .map(com.mojang.datafixers.util.Pair::getFirst)\n                                  .anyMatch(ProgramPatternDeclaration.class::isInstance));\n    }\n\n    @Test\n",
    "insert_old_after": ""
  },
  {
    "targets": [
      "1.19.2",
      "1.19.4"
    ],
    "all_features": [
      "client_inbox"
    ],
    "any_features": [],
    "old": "",
    "new": "    public void addressedBroadcastPreservesLegacyFormAndSourceRoundTrip() {\n        String source = \"\"\"\n                let viewer be player of TeamDman\n                every 20 ticks do\n                    broadcast to viewer\n                    broadcast to viewer channel sfm:dashboard_state\n                end\n                \"\"\";\n        assertNoCompileErrors(source);\n        Program program = compile(source);\n        var statements = program.triggers().get(0).getStatements().get(0).getStatements();\n        BroadcastStatement legacy = (BroadcastStatement) statements.get(0);\n        BroadcastStatement addressed = (BroadcastStatement) statements.get(1);\n        assertNull(legacy.channel());\n        assertEquals(\"sfm:dashboard_state\", addressed.channel().toString());\n        assertNoCompileErrors(program.toString());\n    }\n\n    @Test\n",
    "insert_old_after": ""
  },
  {
    "targets": [
      "1.19.2",
      "1.19.4"
    ],
    "all_features": [],
    "any_features": [
      "packet_computation",
      "packet_transport_private",
      "client_inbox"
    ],
    "old": "",
    "new": "    public void newKeywordsRemainLegalLegacyLabels() {\n        assertNoCompileErrors(\"\"\"\n                every 20 ticks do\n                    input from let\n                    output to like\n                    input from object\n                    output to broadcast\n                    input from channel\n                end\n                \"\"\");\n    }\n\n    @Test\n",
    "insert_old_after": ""
  },
  {
    "targets": [
      "1.19.2",
      "1.19.4",
      "1.20",
      "1.20.1",
      "1.20.2",
      "1.20.3",
      "1.20.4",
      "1.21.0",
      "1.21.1",
      "26.1.2"
    ],
    "all_features": [
      "client_theme"
    ],
    "any_features": [],
    "old": "",
    "new": "    @Test\n    public void syntaxHighlightingTokenRanges() {\n        var rawInput = \"EVERY 20 TICKS DO\\nEND\";\n",
    "insert_old_after": ""
  },
  {
    "targets": [
      "1.19.2",
      "1.19.4",
      "1.20",
      "1.20.1",
      "1.20.2",
      "1.20.3",
      "1.20.4",
      "1.21.0",
      "1.21.1",
      "26.1.2"
    ],
    "all_features": [
      "client_theme"
    ],
    "any_features": [],
    "old": "",
    "new": "        var highlights = ProgramSyntaxHighlightingHelper.getTokenHighlights(rawInput);\n\n        var every = highlights.stream()\n                .filter(highlight -> highlight.text().equals(\"EVERY\"))\n                .findFirst()\n                .orElseThrow();\n        assertEquals(0, every.startIndex());\n        assertEquals(4, every.stopIndex());\n        assertEquals(0xFF5555FF, every.colour());\n\n        var ticks = highlights.stream()\n                .filter(highlight -> highlight.text().equals(\"TICKS\"))\n                .findFirst()\n                .orElseThrow();\n        assertEquals(0xFFFFAA00, ticks.colour());\n    }\n\n\n",
    "insert_old_after": ""
  },
  {
    "targets": [
      "1.19.2",
      "1.19.4",
      "1.20",
      "1.20.1",
      "1.20.2",
      "1.20.3",
      "1.20.4",
      "1.21.0",
      "1.21.1",
      "26.1.2"
    ],
    "all_features": [
      "client_properties"
    ],
    "any_features": [],
    "old": "        assertNotNull(examplesPath, \"Could not locate examples directory starting from \" + System.getProperty(\"user.dir\"));\n",
    "new": "        assertNotNull(examplesPath, \"Could not locate examples directory starting from \" + SFMProperties.userDirectory());\n",
    "insert_old_after": ""
  },
  {
    "targets": [
      "1.19.2",
      "1.19.4",
      "1.20",
      "1.20.1",
      "1.20.2",
      "1.20.3",
      "1.20.4",
      "1.21.0",
      "1.21.1",
      "26.1.2"
    ],
    "all_features": [
      "client_properties"
    ],
    "any_features": [],
    "old": "        assertNotNull(examplesPath, \"Could not locate template programs directory starting from \" + System.getProperty(\"user.dir\"));\n",
    "new": "        assertNotNull(examplesPath, \"Could not locate template programs directory starting from \" + SFMProperties.userDirectory());\n",
    "insert_old_after": ""
  },
  {
    "targets": [
      "1.19.2",
      "1.19.4",
      "1.20",
      "1.20.1",
      "1.20.2",
      "1.20.3",
      "1.20.4",
      "1.21.0",
      "1.21.1",
      "26.1.2"
    ],
    "all_features": [
      "client_properties"
    ],
    "any_features": [],
    "old": "        Path cwd = Paths.get(System.getProperty(\"user.dir\"));\n",
    "new": "        Path cwd = SFMProperties.userDirectory();\n",
    "insert_old_after": ""
  }
]"###;
const INTELLI_SEGMENTS_JSON: &str = r###"{
  "first_test": "    @Test\n    public void packetLanguageStatementStartersAreCompletionCandidates() {\n        Set<Integer> candidates = candidateTokensAtEnd(\"\"\"\n                EVERY 20 TICKS DO\n                \"\"\");\n        assertTrue(candidates.contains(SFMLLexer.INPUT));\n        assertTrue(candidates.contains(SFMLLexer.LET));\n        assertTrue(candidates.contains(SFMLLexer.CREATE));\n        assertTrue(candidates.contains(SFMLLexer.BROADCAST));\n    }\n\n",
  "second_test": "    @Test\n    public void packetLanguageDeclarationsAreCompletionCandidates() {\n        Set<Integer> candidates = candidateTokensAtEnd(\"\");\n        assertTrue(candidates.contains(SFMLLexer.LET));\n        assertTrue(candidates.contains(SFMLLexer.EVERY));\n    }\n\n",
  "let_assertions": "        assertTrue(candidates.contains(SFMLLexer.LET));\n        assertTrue(candidates.contains(SFMLLexer.CREATE));\n",
  "broadcast_assertion": "        assertTrue(candidates.contains(SFMLLexer.BROADCAST));\n",
  "helper": "    private static Set<Integer> candidateTokensAtEnd(String source) {\n        ProgramBuildResult result = new ProgramBuilder(source).build();\n        CommonTokenStream tokens = result.metadata().tokens();\n        tokens.fill();\n        CodeCompletionCore core = new CodeCompletionCore(\n                result.metadata().parser(),\n                Set.of(SFMLParser.RULE_resourceId, SFMLParser.RULE_label),\n                Set.of(SFMLLexer.WS)\n        );\n        return core.collectCandidates(tokens.size() - 1, null).tokens.keySet();\n    }\n\n"
}"###;

#[derive(Facet)]
struct Ledger {
    schema: String,
    scope: String,
    source_context_commits: BTreeMap<String, String>,
    owners: Vec<Owner>,
    inputs: Vec<Input>,
    raw_variants: Vec<Raw>,
    sfml_member_patches: Vec<SfmlPatch>,
    intellisense_segments: IntelliSegments,
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
#[derive(Facet, Debug, PartialEq)]
struct SfmlPatch {
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
    old: String,
    new: String,
    insert_old_after: String,
}
#[derive(Facet, Debug, PartialEq)]
struct IntelliSegments {
    first_test: String,
    second_test: String,
    let_assertions: String,
    broadcast_assertion: String,
    helper: String,
}
struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    normalized: BTreeMap<String, Vec<u8>>,
    patches: Vec<SfmlPatch>,
    segments: IntelliSegments,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core-released-test-gaps-slice@1"
                && ledger.scope == "four_released_test_gap_classes_only",
            "wrong released test gap scope"
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
            ledger.inputs.len() == 4 && ledger.raw_variants.len() == 11 && ledger.owners.len() == 7,
            "bounded cohort widened"
        );
        let mut seen = BTreeSet::new();
        for (index, path) in PATHS.iter().enumerate() {
            let input = ledger
                .inputs
                .iter()
                .find(|input| input.path == *path)
                .ok_or_else(|| eyre::eyre!("missing input evidence: {path}"))?;
            let (_, digest, count) = TEMPLATES[index];
            let members: &[&str] = match index {
                0 => &["mekanism_sidedness_linter"],
                1 => &["editor_v2_cursor_advance"],
                2 => &["packet_computation", "packet_transport_private"],
                3 => &[
                    "packet_transport_private",
                    "client_inbox",
                    "packet_computation",
                    "client_theme",
                    "client_properties",
                ],
                _ => unreachable!("fixed paths"),
            };
            ensure!(
                seen.insert(input.path.clone())
                    && input.stage_path
                        == format!(
                            "platform/cli/sfm-propagate-changes/target/core-released-test-gaps-stage-v1/{}",
                            path.rsplit('/').next().expect("fixed basename")
                        )
                    && input.core_path == format!("platform/minecraft/core-liquid-template/{path}")
                    && input.source_sha256 == digest
                    && input.source_bytes == count
                    && input.membership == "shared_all_ten_targets_all_feature_masks"
                    && input
                        .member_features
                        .iter()
                        .map(String::as_str)
                        .eq(members.iter().copied()),
                "authored identity or shared membership changed"
            );
            let witnesses = COMMITS
                .into_iter()
                .map(|(context, _)| {
                    Ok((context.to_owned(), expected_oid(path, context)?.to_owned()))
                })
                .collect::<Result<BTreeMap<_, _>>>()?;
            ensure!(input.witnesses == witnesses, "all20 witness map changed");
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
                "reviewed support/prerequisite changed: {}",
                owner.id
            );
            let registered = core
                .features
                .0
                .get(&owner.id)
                .ok_or_else(|| eyre::eyre!("source owner not registered: {}", owner.id))?;
            let expected_support = if owner.id == "mekanism_sidedness_linter" {
                ensure!(
                    owner.supported_targets == ["1.19.2"] && owner.requires.is_empty(),
                    "immutable primary linter owner evidence changed"
                );
                vec!["1.19.2".to_owned(), "1.19.4".to_owned()]
            } else {
                owner.supported_targets.clone()
            };
            ensure!(
                registered.supported_targets == expected_support
                    && registered.requires == owner.requires,
                "source registry/evidence mismatch: {}",
                owner.id
            );
        }
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
                "raw/normalized source identity changed"
            );
        }
        ensure!(
            GOLDENS.iter().filter(|pin| pin.cr > 0).count() == 3
                && GOLDENS.iter().map(|pin| pin.cr).sum::<usize>() == 1225,
            "exact EOL normalization scope widened"
        );
        let patches: Vec<SfmlPatch> = facet_json::from_str(SFML_PATCHES_JSON)?;
        let segments: IntelliSegments = facet_json::from_str(INTELLI_SEGMENTS_JSON)?;
        ensure!(
            ledger.sfml_member_patches == patches,
            "SFML member ownership or bytes changed"
        );
        ensure!(
            ledger.intellisense_segments == segments,
            "intellisense member bytes changed"
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
            "actual four core templates must be promoted; no staged fallback"
        );
        Ok(Self {
            core,
            inventory,
            normalized,
            patches,
            segments,
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
                    .any(|value| value == target),
                "unsupported owner {id} on {target}"
            );
            pending.extend(definition.requires.iter().cloned());
        }
        self.core.context(
            target,
            &enabled.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let input = selected
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("released source omitted"))?;
        ensure!(
            input.input == path && !selected.omitted_paths.contains(path),
            "alternate/optional input"
        );
        let source = self.core.read_source(path)?;
        verify_template(path, &source)?;
        Ok(render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes())
    }
    fn expected(&self, path: &str, target: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        if path == PATHS[0] || path == PATHS[1] {
            let owner = if path == PATHS[0] {
                OWNERS[0]
            } else {
                OWNERS[1]
            };
            let environment = if context.features[owner] {
                "dev"
            } else {
                "release"
            };
            return Ok(
                self.normalized[expected_oid(path, &format!("{environment}/{target}"))?].clone(),
            );
        }
        let mut expected = String::from_utf8(
            self.normalized[expected_oid(path, &format!("dev/{target}"))?].clone(),
        )?;
        if path == PATHS[2] {
            if !matches!(target, "1.19.2" | "1.19.4") {
                return Ok(expected.into_bytes());
            }
            let computation = context.features["packet_computation"];
            let private = context.features["packet_transport_private"];
            if !computation && !private {
                replace_exact(&mut expected, &self.segments.first_test, "")?;
                replace_exact(&mut expected, &self.segments.helper, "")?;
            } else {
                if !computation {
                    replace_exact(&mut expected, &self.segments.let_assertions, "")?;
                }
                if !private {
                    replace_exact(&mut expected, &self.segments.broadcast_assertion, "")?;
                }
            }
            if !computation {
                replace_exact(&mut expected, &self.segments.second_test, "")?;
            }
            return Ok(expected.into_bytes());
        }
        for patch in &self.patches {
            if !patch.targets.iter().any(|value| value == target) {
                continue;
            }
            let enabled = if patch.all_features.is_empty() {
                patch
                    .any_features
                    .iter()
                    .any(|owner| context.features[owner])
            } else {
                patch
                    .all_features
                    .iter()
                    .all(|owner| context.features[owner])
            };
            if enabled {
                continue;
            }
            if patch.new.is_empty() {
                replace_exact(
                    &mut expected,
                    &patch.insert_old_after,
                    &format!("{}{}", patch.insert_old_after, patch.old),
                )?;
            } else {
                replace_exact(&mut expected, &patch.new, &patch.old)?;
            }
        }
        Ok(expected.into_bytes())
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
        "mekanism_sidedness_linter" => (&["1.19.2"], &[]),
        "editor_v2_cursor_advance" | "client_theme" | "client_properties" => (&TARGETS, &[]),
        "packet_computation" => (
            &["1.19.2", "1.19.4"],
            &["packet_values", "runtime_resource_cleanup"],
        ),
        "packet_transport_private" => (&["1.19.2", "1.19.4"], &["packet_computation"]),
        "client_inbox" => (&["1.19.2", "1.19.4"], &["packet_transport_private"]),
        _ => eyre::bail!("unreviewed test member owner"),
    })
}
fn replace_exact(source: &mut String, old: &str, new: &str) -> Result<()> {
    ensure!(
        !old.is_empty() && source.matches(old).count() == 1,
        "nonunique fixed source segment"
    );
    *source = source.replacen(old, new, 1);
    Ok(())
}
fn expected_oid(path: &str, context: &str) -> Result<&'static str> {
    WITNESSES
        .iter()
        .find(|(p, c, _)| *p == path && *c == context)
        .map(|(_, _, oid)| *oid)
        .ok_or_else(|| eyre::eyre!("missing frozen witness"))
}
fn golden(oid: &str) -> Result<&'static Golden> {
    GOLDENS
        .iter()
        .find(|pin| pin.oid == oid)
        .ok_or_else(|| eyre::eyre!("unreviewed witness"))
}
fn verify_raw(bytes: &[u8], pin: &Golden) -> Result<Vec<u8>> {
    ensure!(
        bytes.len() == pin.raw_bytes
            && sha256(bytes) == pin.raw_sha256
            && bytes.iter().filter(|&&byte| byte == b'\r').count() == pin.cr
            && bytes.iter().filter(|&&byte| byte == b'\n').count() == pin.lf
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
        "normalization exceeded the EOL-only scope"
    );
    Ok(normalized)
}
fn verify_template(path: &str, bytes: &[u8]) -> Result<()> {
    let (_, digest, count) = TEMPLATES
        .iter()
        .find(|(name, _, _)| *name == path)
        .ok_or_else(|| eyre::eyre!("unreviewed core source"))?;
    ensure!(
        bytes.len() == *count
            && sha256(bytes) == *digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "authored core bytes changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
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
            "released source must stay one unconditional shared input"
        );
    }
    Ok(())
}

#[test]
fn four_released_tests_match_all_80_frozen_source_contexts() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        for enabled in [false, true] {
            let requested = if enabled {
                fixture.supported_requests(target)
            } else {
                Vec::new()
            };
            let context = fixture.context(target, &requested)?;
            let environment = if enabled { "dev" } else { "release" };
            for path in PATHS {
                let expected =
                    &fixture.normalized[expected_oid(path, &format!("{environment}/{target}"))?];
                assert_eq!(
                    &fixture.render(path, &context)?,
                    expected,
                    "{environment}/{target}/{path}"
                );
            }
        }
    }
    Ok(())
}
#[test]
fn test_member_masks_use_actual_owner_prerequisites_and_refuse_unsupported_targets() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        for mask in 0..128 {
            let requested = OWNERS
                .into_iter()
                .enumerate()
                .filter(|(index, _)| mask & (1 << index) != 0)
                .map(|(_, owner)| owner)
                .collect::<Vec<_>>();
            if requested
                .iter()
                .any(|owner| !fixture.supported_requests(target).contains(owner))
            {
                assert!(
                    fixture.context(target, &requested).is_err(),
                    "unsupported mask {mask}/{target}"
                );
                continue;
            }
            let context = fixture.context(target, &requested)?;
            for path in PATHS {
                assert_eq!(
                    fixture.render(path, &context)?,
                    fixture.expected(path, target, &context)?,
                    "{mask}/{target}/{path}"
                );
            }
        }
    }
    let editor = fixture.context("1.19.2", &["editor_v2_cursor_advance"])?;
    assert!(!editor.features["canvas_text_editor"] && !editor.features["client_actions"]);
    let theme = fixture.context("1.19.2", &["client_theme"])?;
    assert!(!theme.features["client_properties"] && !theme.features["packet_computation"]);
    let computation = fixture.context("1.19.2", &["packet_computation"])?;
    assert!(!computation.features["packet_transport_private"]);
    let completion = String::from_utf8(fixture.render(PATHS[2], &computation)?)?;
    assert!(completion.contains("candidates.contains(SFMLLexer.CREATE)"));
    assert!(!completion.contains("candidates.contains(SFMLLexer.BROADCAST)"));
    let packet_tests = String::from_utf8(fixture.render(PATHS[3], &computation)?)?;
    assert!(packet_tests.contains("newKeywordsRemainLegalLegacyLabels"));
    assert!(
        !packet_tests.contains("packetComputationLanguageSurfaceBuildsDefinitionsAndStatements")
    );
    let inbox = fixture.context("1.19.2", &["client_inbox"])?;
    assert!(inbox.features["packet_transport_private"] && inbox.features["packet_computation"]);
    Ok(())
}
#[test]
fn released_noops_type_adapters_and_member_boundaries_remain_exact() -> Result<()> {
    let fixture = Fixture::load()?;
    let cursor_model = String::from_utf8(
        fixture
            .core
            .read_source("src/main/java/ca/teamdman/sfm/client/text_editor/MultiCursor.java")?,
    )?;
    assert!(cursor_model.contains("record MultiCursor(ArrayDeque<Cursor> cursors)"));
    for target in TARGETS {
        let off = fixture.context(target, &[])?;
        let mek = String::from_utf8(fixture.render(PATHS[0], &off)?)?;
        assert!(mek.contains("helper.succeed();"));
        assert!(!mek.contains("MekanismSidednessLinterGameTestGenerator"));
        let editor_off = String::from_utf8(fixture.render(PATHS[1], &off)?)?;
        assert!(
            !editor_off.contains("public void repeatedSingleCharacterInsertionAppendsAtCursor")
        );
        let editor_context = fixture.context(target, &["editor_v2_cursor_advance"])?;
        let editor_on = String::from_utf8(fixture.render(PATHS[1], &editor_context)?)?;
        assert!(editor_on.contains("Cursor cursor = context.multiCursor().cursors().getFirst();"));
        let tests_off = String::from_utf8(fixture.render(PATHS[3], &off)?)?;
        assert!(!tests_off.contains("syntaxHighlightingTokenRanges"));
        assert!(!tests_off.contains("SFMProperties.userDirectory()"));
        assert!(tests_off.contains("Paths.get(System.getProperty"));
        assert_eq!(
            tests_off.contains("net.minecraftforge.items.IItemHandler"),
            matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1")
        );
        assert_eq!(
            tests_off.contains("net.neoforged.neoforge.items.IItemHandler"),
            !matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1")
        );
        let theme = fixture.context(target, &["client_theme"])?;
        let themed = String::from_utf8(fixture.render(PATHS[3], &theme)?)?;
        assert!(themed.contains("syntaxHighlightingTokenRanges"));
        assert!(!themed.contains("SFMProperties.userDirectory()"));
        if !matches!(target, "1.19.2" | "1.19.4") {
            assert!(
                fixture
                    .context(target, &["mekanism_sidedness_linter"])
                    .is_err()
            );
        }
        if !matches!(target, "1.19.2" | "1.19.4") {
            // Artificial global bits probe MC member guards, not accepted feature support.
            let mut artificial = off.clone();
            for owner in [
                "mekanism_sidedness_linter",
                "packet_computation",
                "packet_transport_private",
                "client_inbox",
            ] {
                artificial.features.insert(owner.to_owned(), true);
            }
            for path in [PATHS[0], PATHS[2], PATHS[3]] {
                let source = fixture.core.read_source(path)?;
                assert_eq!(
                    render_java_source(std::str::from_utf8(&source)?, &artificial)?.as_bytes(),
                    fixture.render(path, &off)?
                );
            }
        }
    }
    let active = fixture.context("1.19.2", &["mekanism_sidedness_linter"])?;
    let secondary = fixture.context("1.19.4", &["mekanism_sidedness_linter"])?;
    let secondary_off = fixture.context("1.19.4", &[])?;
    assert_eq!(
        fixture.render(PATHS[0], &secondary)?,
        fixture.render(PATHS[0], &secondary_off)?
    );
    assert!(
        !String::from_utf8(fixture.render(PATHS[0], &secondary)?)?
            .contains("MekanismSidednessLinterGameTestGenerator")
    );
    assert!(
        String::from_utf8(fixture.render(PATHS[0], &active)?)?
            .contains("MekanismSidednessLinterGameTestGenerator.check")
    );
    assert_eq!(fixture.context("1.21.0", &[])?.minecraft_version, "1.21");
    Ok(())
}
#[test]
fn common_released_test_edit_reaches_all_ten_targets_in_isolated_core_tree() -> Result<()> {
    let fixture = Fixture::load()?;
    let path = PATHS[0];
    let original = fixture.core.read_source(path)?;
    let mut edited = original.clone();
    edited.extend_from_slice(b"// isolated shared released test edit\n");
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
        expected.extend_from_slice(b"// isolated shared released test edit\n");
        assert_eq!(actual.as_bytes(), expected);
    }
    assert_eq!(fixture.core.read_source(path)?, original);
    Ok(())
}
#[test]
fn changed_raw_owner_member_or_released_membership_evidence_refuses() -> Result<()> {
    let fixture = Fixture::load()?;
    let raw = read_git_blobs(
        &fixture.core.repository,
        &BTreeSet::from([GOLDENS[0].oid.to_owned()]),
    )?;
    let mut changed = raw[GOLDENS[0].oid].clone();
    changed.push(b' ');
    assert!(verify_raw(&changed, &GOLDENS[0]).is_err());
    for (index, path) in PATHS.iter().enumerate() {
        let source = fixture.core.read_source(path)?;
        let mut changed = source.clone();
        changed.push(b' ');
        assert!(verify_template(path, &changed).is_err());
        let owner = match index {
            0 => OWNERS[0],
            1 => OWNERS[1],
            2 => OWNERS[2],
            _ => OWNERS[5],
        };
        let mut context = fixture.context("1.19.2", &[])?;
        context.features.remove(owner);
        assert!(
            render_java_source(std::str::from_utf8(&source)?, &context).is_err(),
            "inactive owner {owner}"
        );
        let mut metadata = fixture.core.metadata.clone();
        metadata.source_rules.insert(
            (*path).to_owned(),
            vec![super::core_inputs::InputVariant {
                input: (*path).to_owned(),
                when: super::core_inputs::InputPredicate {
                    targets: vec![],
                    all_features: vec![owner.to_owned()],
                    any_features: vec![],
                    none_features: vec![],
                },
                template: false,
            }],
        );
        assert!(validate_shared_rule(&metadata, path).is_err());
    }
    let mut patches: Vec<SfmlPatch> = facet_json::from_str(SFML_PATCHES_JSON)?;
    patches[0].all_features = vec!["mekanism_sidedness_linter".to_owned()];
    assert_ne!(patches, fixture.patches);
    Ok(())
}
