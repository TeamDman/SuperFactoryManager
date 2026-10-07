//! Post-promotion contracts for three released manager, label and linter inputs.
//!
//! Historical Git objects are bounded source witnesses, never production inputs.
//! This module requires the actual promoted core templates and reviewed owners.
//! Rendering evidence does not prove helper linkage, Java builds or gameplay.

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

const LEDGER: &str = "docs/tasks/sfm-core-manager-label-linter-slice.json";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const OWNERS: [&str; 4] = [
    "sfml_worded_intervals",
    "single_line_input",
    "manager_editor_actions",
    "tick_graph_null_safety",
];
const PATHS: [&str; 3] = [
    "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
    "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
    "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
];
const TEMPLATES: [(&str, &str, usize); 3] = [
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "sha256:2cf5564bd491d9215add45fafeb592844c3deeba562762469c774754e32039bc",
        4246,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "sha256:9351cbdab0967834121224e3fe22f5e198b89a76846e721eba7532eda1e96453",
        11711,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "sha256:3c38085f78d89948344ef64ccb40e001b2ae8fb147fedaf4e454317ab42f23f6",
        58940,
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
const WITNESSES: [(&str, &str, &str); 60] = [
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "dev/1.19.2",
        "3935e549c494c90714bcfa8499c3818f571da6a2",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "dev/1.19.4",
        "07bf30a1b97fbd740753c088a7509e7e53305871",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "dev/1.20",
        "ac9a6f0a4e536a631d0b93961a737632594f03f3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "dev/1.20.1",
        "ac9a6f0a4e536a631d0b93961a737632594f03f3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "dev/1.20.2",
        "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "dev/1.20.3",
        "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "dev/1.20.4",
        "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "dev/1.21.0",
        "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "dev/1.21.1",
        "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "dev/26.1.2",
        "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "release/1.19.2",
        "5b416b3cdd56ab789c6e7ad61d88de39918b50c4",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "release/1.19.4",
        "ac9a6f0a4e536a631d0b93961a737632594f03f3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "release/1.20",
        "ac9a6f0a4e536a631d0b93961a737632594f03f3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "release/1.20.1",
        "ac9a6f0a4e536a631d0b93961a737632594f03f3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "release/1.20.2",
        "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "release/1.20.3",
        "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "release/1.20.4",
        "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "release/1.21.0",
        "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "release/1.21.1",
        "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMProgramLinters.java",
        "release/26.1.2",
        "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "dev/1.19.2",
        "fb3b04874b718fa61a599148fef35d5d6aa596c9",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "dev/1.19.4",
        "fb3b04874b718fa61a599148fef35d5d6aa596c9",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "dev/1.20",
        "0e2e801b6c658bc9d4d441f18f6890ab75b2f7dc",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "dev/1.20.1",
        "0e2e801b6c658bc9d4d441f18f6890ab75b2f7dc",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "dev/1.20.2",
        "b5423a4e8adcef7fe647fe5feb242043833359fa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "dev/1.20.3",
        "b5423a4e8adcef7fe647fe5feb242043833359fa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "dev/1.20.4",
        "b5423a4e8adcef7fe647fe5feb242043833359fa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "dev/1.21.0",
        "b5423a4e8adcef7fe647fe5feb242043833359fa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "dev/1.21.1",
        "b5423a4e8adcef7fe647fe5feb242043833359fa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "dev/26.1.2",
        "09988b9d84fdf8f1a90ba58acb6f2548097a3dcd",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "release/1.19.2",
        "02ae34b6e22836b5f8cc9fc2643a9ce653d2328d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "release/1.19.4",
        "02ae34b6e22836b5f8cc9fc2643a9ce653d2328d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "release/1.20",
        "0e2e801b6c658bc9d4d441f18f6890ab75b2f7dc",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "release/1.20.1",
        "0e2e801b6c658bc9d4d441f18f6890ab75b2f7dc",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "release/1.20.2",
        "b5423a4e8adcef7fe647fe5feb242043833359fa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "release/1.20.3",
        "b5423a4e8adcef7fe647fe5feb242043833359fa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "release/1.20.4",
        "b5423a4e8adcef7fe647fe5feb242043833359fa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "release/1.21.0",
        "b5423a4e8adcef7fe647fe5feb242043833359fa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "release/1.21.1",
        "b5423a4e8adcef7fe647fe5feb242043833359fa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/LabelGunScreen.java",
        "release/26.1.2",
        "09988b9d84fdf8f1a90ba58acb6f2548097a3dcd",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "dev/1.19.2",
        "2d06d4a0ae2ca6797d705d63f24965e53f5b3659",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "dev/1.19.4",
        "94300fc885aadc6be334a8334591062fbd39577a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "dev/1.20",
        "be3a30de4f0e5a913e727af364e1f5f79f4342c8",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "dev/1.20.1",
        "be3a30de4f0e5a913e727af364e1f5f79f4342c8",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "dev/1.20.2",
        "4fb75dd30ab45e33f6a60af897206c82cb8f335d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "dev/1.20.3",
        "4fb75dd30ab45e33f6a60af897206c82cb8f335d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "dev/1.20.4",
        "4fb75dd30ab45e33f6a60af897206c82cb8f335d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "dev/1.21.0",
        "8d33ce6aa5bb1a420c9419205892eeafb323b3c7",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "dev/1.21.1",
        "8d33ce6aa5bb1a420c9419205892eeafb323b3c7",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "dev/26.1.2",
        "bd35ea47bc711c2dfc42613fb598deae7ae73cc8",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "release/1.19.2",
        "ec8e9f3056c1e1183a5d93cf0bf4dc2cc5103a94",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "release/1.19.4",
        "6fe163a6f7138b12656cd8c15caebc2f4c135215",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "release/1.20",
        "a1c3fabf59f263f86a180decc8bfe091cabbd653",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "release/1.20.1",
        "a1c3fabf59f263f86a180decc8bfe091cabbd653",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "release/1.20.2",
        "6b9cae4ea1f6b812afaad7fa3e4b6babbfe27206",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "release/1.20.3",
        "6b9cae4ea1f6b812afaad7fa3e4b6babbfe27206",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "release/1.20.4",
        "6b9cae4ea1f6b812afaad7fa3e4b6babbfe27206",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "release/1.21.0",
        "0c882b5035f510d0d0159c9c2cea91bfe59e6ef3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "release/1.21.1",
        "0c882b5035f510d0d0159c9c2cea91bfe59e6ef3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/ManagerScreen.java",
        "release/26.1.2",
        "596fe0cf33b5e9855823eb2cc6ce469263c4f75c",
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
const GOLDENS: [Golden; 22] = [
    Golden {
        oid: "3935e549c494c90714bcfa8499c3818f571da6a2",
        raw_sha256: "sha256:8765208e9b3620030260aeb7bfe31200a1282632521bbb899df7d748859ad8ff",
        raw_bytes: 3632,
        cr: 0,
        lf: 98,
        normalized_sha256: "sha256:8765208e9b3620030260aeb7bfe31200a1282632521bbb899df7d748859ad8ff",
        normalized_bytes: 3632,
    },
    Golden {
        oid: "07bf30a1b97fbd740753c088a7509e7e53305871",
        raw_sha256: "sha256:15017968e121cc4d6262d92c346ea4f3c53469522acef32fb1d7f17b15a83eb3",
        raw_bytes: 3578,
        cr: 0,
        lf: 97,
        normalized_sha256: "sha256:15017968e121cc4d6262d92c346ea4f3c53469522acef32fb1d7f17b15a83eb3",
        normalized_bytes: 3578,
    },
    Golden {
        oid: "ac9a6f0a4e536a631d0b93961a737632594f03f3",
        raw_sha256: "sha256:87654d37be08e65dab9c01832931cd2b0f2226f74e939831445b3c11c9a769a4",
        raw_bytes: 3330,
        cr: 0,
        lf: 91,
        normalized_sha256: "sha256:87654d37be08e65dab9c01832931cd2b0f2226f74e939831445b3c11c9a769a4",
        normalized_bytes: 3330,
    },
    Golden {
        oid: "257b4a82878cc7cded7a565cd03e23e63cb0e9ff",
        raw_sha256: "sha256:691721ef735dc69a04de2735644f508f9f6493f406a024de953b2b0d17ec2f5e",
        raw_bytes: 3320,
        cr: 0,
        lf: 91,
        normalized_sha256: "sha256:691721ef735dc69a04de2735644f508f9f6493f406a024de953b2b0d17ec2f5e",
        normalized_bytes: 3320,
    },
    Golden {
        oid: "5b416b3cdd56ab789c6e7ad61d88de39918b50c4",
        raw_sha256: "sha256:4697d42301ca70a57de27dd719e510f5cb513e11b9e1fd88d4f32e7e51d326fe",
        raw_bytes: 3384,
        cr: 0,
        lf: 92,
        normalized_sha256: "sha256:4697d42301ca70a57de27dd719e510f5cb513e11b9e1fd88d4f32e7e51d326fe",
        normalized_bytes: 3384,
    },
    Golden {
        oid: "fb3b04874b718fa61a599148fef35d5d6aa596c9",
        raw_sha256: "sha256:229b26ee5eba74dd12524dcc3a445ab7a941c06ddeecdcc7504174466911d3c0",
        raw_bytes: 9375,
        cr: 0,
        lf: 264,
        normalized_sha256: "sha256:229b26ee5eba74dd12524dcc3a445ab7a941c06ddeecdcc7504174466911d3c0",
        normalized_bytes: 9375,
    },
    Golden {
        oid: "0e2e801b6c658bc9d4d441f18f6890ab75b2f7dc",
        raw_sha256: "sha256:c6527414fd989f83389fbe02560bf9e705e782d841a216a47b85a088b20722e4",
        raw_bytes: 9333,
        cr: 0,
        lf: 264,
        normalized_sha256: "sha256:c6527414fd989f83389fbe02560bf9e705e782d841a216a47b85a088b20722e4",
        normalized_bytes: 9333,
    },
    Golden {
        oid: "b5423a4e8adcef7fe647fe5feb242043833359fa",
        raw_sha256: "sha256:7d3b644d7e83e0033e858d7396be4ffee3390480d8dda73f92f9c67f4be6a70e",
        raw_bytes: 9344,
        cr: 0,
        lf: 264,
        normalized_sha256: "sha256:7d3b644d7e83e0033e858d7396be4ffee3390480d8dda73f92f9c67f4be6a70e",
        normalized_bytes: 9344,
    },
    Golden {
        oid: "09988b9d84fdf8f1a90ba58acb6f2548097a3dcd",
        raw_sha256: "sha256:24579d9f4a077b1aa6fa5567acb09abe8060b0c9f5c75d733ec9ce0ddf64b76d",
        raw_bytes: 9350,
        cr: 0,
        lf: 261,
        normalized_sha256: "sha256:24579d9f4a077b1aa6fa5567acb09abe8060b0c9f5c75d733ec9ce0ddf64b76d",
        normalized_bytes: 9350,
    },
    Golden {
        oid: "02ae34b6e22836b5f8cc9fc2643a9ce653d2328d",
        raw_sha256: "sha256:e194c69d514f912186219bb3f0f5ef1d50702a3984724f0261ae1a67cb786d5a",
        raw_bytes: 9333,
        cr: 0,
        lf: 264,
        normalized_sha256: "sha256:e194c69d514f912186219bb3f0f5ef1d50702a3984724f0261ae1a67cb786d5a",
        normalized_bytes: 9333,
    },
    Golden {
        oid: "2d06d4a0ae2ca6797d705d63f24965e53f5b3659",
        raw_sha256: "sha256:37d89b405ae3dca9afebbc9b3eed0ed2c7188aab1634b37cddba849e4c023964",
        raw_bytes: 36528,
        cr: 910,
        lf: 947,
        normalized_sha256: "sha256:800c0e0c965df736b16eaecb95131f087b12cb82a5111277653c71f1f96b27b6",
        normalized_bytes: 35618,
    },
    Golden {
        oid: "94300fc885aadc6be334a8334591062fbd39577a",
        raw_sha256: "sha256:8bcc5dceee3f431a39bb94d8268c4cb2e1bac69d25dd7f7f86d55e74697737d4",
        raw_bytes: 36540,
        cr: 910,
        lf: 947,
        normalized_sha256: "sha256:63c6883afb72f810e601c46e5cab78e20431f48ab926fe7b40295ec4b41f5f2b",
        normalized_bytes: 35630,
    },
    Golden {
        oid: "be3a30de4f0e5a913e727af364e1f5f79f4342c8",
        raw_sha256: "sha256:eec1e7c796f16af1e3c64821b941503a22524cfbc487f205690df9b48d46470b",
        raw_bytes: 36003,
        cr: 933,
        lf: 933,
        normalized_sha256: "sha256:5011212fc002f131c76cfd347c8f79054835e126ee50b6458168cab921624d12",
        normalized_bytes: 35070,
    },
    Golden {
        oid: "4fb75dd30ab45e33f6a60af897206c82cb8f335d",
        raw_sha256: "sha256:3e754f2ac6bcf90c821ba71a7586b917844e313ae04467383042f67c2d151a2f",
        raw_bytes: 36014,
        cr: 933,
        lf: 933,
        normalized_sha256: "sha256:cedfc8b2228cc98c1974ed97cf98425528263cea5607d7545b5250dd31106e50",
        normalized_bytes: 35081,
    },
    Golden {
        oid: "8d33ce6aa5bb1a420c9419205892eeafb323b3c7",
        raw_sha256: "sha256:9a930936eec949456c28d63cb077c14af5b3ac7c1ecb26603945bc8dd76705b0",
        raw_bytes: 35908,
        cr: 920,
        lf: 928,
        normalized_sha256: "sha256:7bfe9cf355a39e9015d6832ab10b2768074d0bb9e91d9dc5b216e20ddbc045e6",
        normalized_bytes: 34988,
    },
    Golden {
        oid: "bd35ea47bc711c2dfc42613fb598deae7ae73cc8",
        raw_sha256: "sha256:99d8f989216fa1d3167d45ece5832ec3cff920aa627751d3acac3c4767a3f0a4",
        raw_bytes: 32428,
        cr: 0,
        lf: 865,
        normalized_sha256: "sha256:99d8f989216fa1d3167d45ece5832ec3cff920aa627751d3acac3c4767a3f0a4",
        normalized_bytes: 32428,
    },
    Golden {
        oid: "ec8e9f3056c1e1183a5d93cf0bf4dc2cc5103a94",
        raw_sha256: "sha256:a909b755f4b76436f97192172c8f5011170fd65ff42f81dc8c4facbb767778e6",
        raw_bytes: 35838,
        cr: 932,
        lf: 932,
        normalized_sha256: "sha256:c955c0038ad844ef395740ee88297e1cf1302dd1bd0d5d0d1df90b896a59c44b",
        normalized_bytes: 34906,
    },
    Golden {
        oid: "6fe163a6f7138b12656cd8c15caebc2f4c135215",
        raw_sha256: "sha256:91cda1dc5fd1539bf992fbef7350d67ea28d1edb88a2f7f0028d7ea36e8314a9",
        raw_bytes: 35850,
        cr: 932,
        lf: 932,
        normalized_sha256: "sha256:5abe2641888521b7158f2f4669bf428e663a87ad58652b5c2464010499b3723c",
        normalized_bytes: 34918,
    },
    Golden {
        oid: "a1c3fabf59f263f86a180decc8bfe091cabbd653",
        raw_sha256: "sha256:5412b91b158af57fa8cda08372ee8e83c42744b16ebbc96762f55f8e049db7e2",
        raw_bytes: 35813,
        cr: 929,
        lf: 929,
        normalized_sha256: "sha256:a3e6f8ed9beb3deae15630116e6f2939ed38b678b0829e812f55937985761279",
        normalized_bytes: 34884,
    },
    Golden {
        oid: "6b9cae4ea1f6b812afaad7fa3e4b6babbfe27206",
        raw_sha256: "sha256:2bf53ea8357793a635922c2a2555548d5bf2e0fe9662cb580db14bdfa27f9478",
        raw_bytes: 35824,
        cr: 929,
        lf: 929,
        normalized_sha256: "sha256:8cde1b20f18bb8d79eaae797d21d949b4550e2151d1d3c3c146cdccfa8c83611",
        normalized_bytes: 34895,
    },
    Golden {
        oid: "0c882b5035f510d0d0159c9c2cea91bfe59e6ef3",
        raw_sha256: "sha256:d4bf5bdee706e6216b14856611e12781aaf57dfc26811df1659482b4d002c103",
        raw_bytes: 35726,
        cr: 924,
        lf: 924,
        normalized_sha256: "sha256:3f3f69413544140298be7fb829057bfa23e2640ba34e6c7c897839bd0e117127",
        normalized_bytes: 34802,
    },
    Golden {
        oid: "596fe0cf33b5e9855823eb2cc6ce469263c4f75c",
        raw_sha256: "sha256:23f2939fe53330d6907bd478d79b29b493ff7c2a9440e0949019de6694b8a9b1",
        raw_bytes: 32382,
        cr: 0,
        lf: 864,
        normalized_sha256: "sha256:23f2939fe53330d6907bd478d79b29b493ff7c2a9440e0949019de6694b8a9b1",
        normalized_bytes: 32382,
    },
];
const MEMBER_PATCHES_JSON: &str = r###"[
  {
    "owner": "manager_editor_actions",
    "targets": [
      "1.19.2",
      "1.19.4"
    ],
    "old": "",
    "new": "import ca.teamdman.sfm.client.action.SFMClientActionContext;\nimport ca.teamdman.sfm.client.action.SFMClientActionExecutor;\nimport ca.teamdman.sfm.client.action.SFMClientActionSource;\n"
  },
  {
    "owner": "manager_editor_actions",
    "targets": [
      "1.19.2",
      "1.19.4"
    ],
    "old": "",
    "new": "import ca.teamdman.sfm.client.screen.widget.SFMActionButton;\n"
  },
  {
    "owner": "manager_editor_actions",
    "targets": [
      "1.19.2",
      "1.19.4"
    ],
    "old": "            onEditButtonClicked();\n",
    "new": "            invokeEditAction();\n"
  },
  {
    "owner": "manager_editor_actions",
    "targets": [
      "1.19.2",
      "1.19.4"
    ],
    "old": "        editButton = this.addRenderableWidget(\n                new SFMButtonBuilder()\n                        .setPosition(\n                                (this.width - this.imageWidth) / 2 - buttonWidth,\n                                (this.height - this.imageHeight) / 2 + 16 + 50\n                        )\n                        .setSize(buttonWidth, buttonHeight)\n                        .setText(MANAGER_GUI_EDIT_BUTTON)\n                        .setOnPress(button -> onEditButtonClicked())\n                        .setTooltip(\n                                this,\n                                font,\n                                MANAGER_GUI_EDIT_BUTTON_TOOLTIP.getComponent(SFMKeyMappings.getKeyDisplay(SFMKeyMappings.MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY))\n                        )\n                        .build()\n        );\n",
    "new": "        editButton = this.addRenderableWidget(new SFMActionButton(\n                (this.width - this.imageWidth) / 2 - buttonWidth,\n                (this.height - this.imageHeight) / 2 + 16 + 50,\n                buttonWidth,\n                buttonHeight,\n                MANAGER_GUI_EDIT_BUTTON.getComponent(),\n                new ResourceLocation(SFM.MOD_ID, \"manager/edit\"),\n                new ResourceLocation(SFM.MOD_ID, \"default\"),\n                () -> MANAGER_GUI_EDIT_BUTTON_TOOLTIP.getComponent(),\n                () -> \"sfm action invoke sfm:manager/edit\",\n                button -> invokeEditAction()\n        ));\n"
  },
  {
    "owner": "manager_editor_actions",
    "targets": [
      "1.19.2",
      "1.19.4"
    ],
    "old": "    private void onEditButtonClicked() {\n",
    "new": "    public void openProgramEditorFromAction() {\n"
  },
  {
    "owner": "manager_editor_actions",
    "targets": [
      "1.19.2",
      "1.19.4"
    ],
    "old": "",
    "new": "    private void invokeEditAction() {\n        try {\n            SFMClientActionExecutor.execute(\n                    \"sfm action invoke sfm:manager/edit\",\n                    SFMClientActionContext.create(this, () -> Minecraft.getInstance().screen == this),\n                    message -> SFM.LOGGER.warn(\"Manager edit action feedback: {}\", message.getString()));\n        } catch (Exception exception) {\n            SFM.LOGGER.error(\"Manager edit action failed\", exception);\n        }\n    }\n\n"
  },
  {
    "owner": "tick_graph_null_safety",
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
    "old": "            if (candidate.compareTo(peakTickTime) > 0) {\n",
    "new": "            if (candidate != null && candidate.compareTo(peakTickTime) > 0) {\n"
  },
  {
    "owner": "tick_graph_null_safety",
    "targets": [
      "1.19.2",
      "1.19.4",
      "1.20",
      "1.20.1",
      "1.20.2",
      "1.20.3",
      "1.20.4",
      "1.21.0",
      "1.21.1"
    ],
    "old": "            long y = menu.tickTimes[i].toNanos();\n",
    "new": "            Duration tickTime = menu.tickTimes[i];\n            if (tickTime == null) {\n                continue;\n            }\n            long y = tickTime.toNanos();\n"
  },
  {
    "owner": "tick_graph_null_safety",
    "targets": [
      "1.19.2",
      "1.19.4",
      "1.20",
      "1.20.1",
      "1.20.2",
      "1.20.3",
      "1.20.4",
      "1.21.0",
      "1.21.1"
    ],
    "old": "        var format = new DecimalFormat(\"0.000\");\n",
    "new": "        var format = new DecimalFormat(\"0.000\"); // TODO: this should respect the user's locale\n"
  },
  {
    "owner": "tick_graph_null_safety",
    "targets": [
      "26.1.2"
    ],
    "old": "            if (mx - leftPos >= plotPosX - spaceBetweenPoints / 2\n                    && mx - leftPos <= plotPosX + spaceBetweenPoints / 2\n                    && my - topPos >= plotY - 2\n                    && my - topPos <= plotY + plotHeight + 2) {\n",
    "new": "            if (candidate != null\n                && mx - leftPos >= plotPosX - spaceBetweenPoints / 2\n                && mx - leftPos <= plotPosX + spaceBetweenPoints / 2\n                && my - topPos >= plotY - 2\n                && my - topPos <= plotY + plotHeight + 2) {\n"
  }
]"###;

#[derive(Facet)]
struct Ledger {
    schema: String,
    scope: String,
    source_context_commits: BTreeMap<String, String>,
    owners: Vec<Owner>,
    inputs: Vec<Input>,
    raw_variants: Vec<Raw>,
    manager_member_patches: Vec<MemberPatch>,
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
struct MemberPatch {
    owner: String,
    targets: Vec<String>,
    old: String,
    new: String,
}
struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    normalized: BTreeMap<String, Vec<u8>>,
    patches: Vec<MemberPatch>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core-manager-label-linter-slice@1"
                && ledger.scope == "three_released_manager_label_linter_files_only",
            "wrong manager/label/linter scope"
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
            ledger.inputs.len() == 3 && ledger.raw_variants.len() == 22 && ledger.owners.len() == 4,
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
                0 => &["sfml_worded_intervals"],
                1 => &["single_line_input"],
                2 => &["manager_editor_actions", "tick_graph_null_safety"],
                _ => unreachable!("fixed paths"),
            };
            ensure!(
                seen.insert(input.path.clone())
                    && input.stage_path
                        == format!(
                            "platform/cli/sfm-propagate-changes/target/core-manager-label-linter-stage-v1/{}",
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
            ensure!(
                registered.supported_targets == owner.supported_targets
                    && registered.requires == owner.requires,
                "source registry/evidence mismatch: {}",
                owner.id
            );
        }
        let renderer = core
            .features
            .0
            .get("tick_graph_null_samples")
            .ok_or_else(|| eyre::eyre!("existing separate renderer owner missing"))?;
        ensure!(
            renderer.supported_targets == ["26.1.2"],
            "renderer owner support widened"
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
                "raw/normalized source identity changed"
            );
        }
        ensure!(
            GOLDENS.iter().filter(|pin| pin.cr > 0).count() == 10
                && GOLDENS.iter().map(|pin| pin.cr).sum::<usize>() == 9252,
            "exact EOL normalization scope widened"
        );
        let patches: Vec<MemberPatch> = facet_json::from_str(MEMBER_PATCHES_JSON)?;
        ensure!(
            ledger.manager_member_patches == patches,
            "member patch ownership or bytes changed"
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
            "actual three core templates must be promoted; no staged fallback"
        );
        Ok(Self {
            core,
            inventory,
            normalized,
            patches,
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
        if path != PATHS[2] {
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
        for patch in &self.patches {
            if patch.targets.iter().any(|value| value == target) && !context.features[&patch.owner]
            {
                ensure!(
                    !patch.new.is_empty() && expected.matches(patch.new.as_str()).count() == 1,
                    "ambiguous independent patch"
                );
                expected = expected.replacen(patch.new.as_str(), &patch.old, 1);
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
        "sfml_worded_intervals" | "single_line_input" => (&["1.19.2", "1.19.4"], &[]),
        "manager_editor_actions" => (&TARGETS, &["client_actions"]),
        "tick_graph_null_safety" => (&TARGETS, &[]),
        _ => eyre::bail!("unreviewed owner"),
    })
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
fn manager_label_linter_match_all_60_frozen_source_contexts() -> Result<()> {
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
fn member_masks_preserve_independent_editor_input_and_null_safety() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        for mask in 0..16 {
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
    let fixed = fixture.context("1.19.2", &["tick_graph_null_safety"])?;
    assert!(!fixed.features["client_actions"] && !fixed.features["single_line_input"]);
    assert!(
        !fixed.features["manager_editor_actions"] && !fixed.features["tick_graph_null_samples"]
    );
    let actions = fixture.context("1.19.2", &["manager_editor_actions"])?;
    assert!(actions.features["client_actions"]);
    assert!(!actions.features["workspace_panels"] && !actions.features["command_palette"]);
    assert!(!actions.features["tick_graph_null_safety"]);
    Ok(())
}
#[test]
fn target_adapters_retain_live_mek_and_do_not_broaden_manager_members() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let off = fixture.context(target, &[])?;
        let linters = String::from_utf8(fixture.render(PATHS[0], &off)?)?;
        assert_eq!(
            linters.contains("import ca.teamdman.sfm.common.compat.SFMMekanismCompat;"),
            target == "1.19.2"
        );
        assert_eq!(
            linters.lines().any(|line| {
                line.trim() == "SFMMekanismCompat.registerProgramLinters(REGISTERER);"
            }),
            target == "1.19.2"
        );
        assert_eq!(
            linters.contains("//            SFMMekanismCompat.registerProgramLinters(REGISTERER);"),
            target != "1.19.2"
        );
        assert!(!linters.contains("LEGACY_INTERVAL_OFFSET"));
        let label = String::from_utf8(fixture.render(PATHS[1], &off)?)?;
        assert!(!label.contains("new ca.teamdman.sfm.client.input.SFMSingleLineEditBox("));
        let manager = String::from_utf8(fixture.render(PATHS[2], &off)?)?;
        assert!(manager.contains("private void onEditButtonClicked()"));
        assert!(!manager.contains("SFMClientActionExecutor"));
        assert!(!manager.contains("candidate != null && candidate.compareTo"));
        let actions = fixture.context(target, &["manager_editor_actions"])?;
        let edited = String::from_utf8(fixture.render(PATHS[2], &actions)?)?;
        assert_eq!(
            edited.contains("public void openProgramEditorFromAction()"),
            matches!(target, "1.19.2" | "1.19.4")
        );
        let fixed = fixture.context(target, &["tick_graph_null_safety"])?;
        let graph = String::from_utf8(fixture.render(PATHS[2], &fixed)?)?;
        assert!(graph.contains("candidate != null && candidate.compareTo"));
        assert_eq!(
            graph.contains("Duration tickTime = menu.tickTimes[i];"),
            target != "26.1.2"
        );
        assert!(!fixed.features["tick_graph_null_samples"]);
        // An artificial global bit probes the member's MC guard, not context acceptance.
        if !matches!(target, "1.19.2" | "1.19.4") {
            let mut artificial = off.clone();
            artificial
                .features
                .insert("single_line_input".to_owned(), true);
            artificial
                .features
                .insert("sfml_worded_intervals".to_owned(), true);
            artificial
                .features
                .insert("manager_editor_actions".to_owned(), true);
            for path in PATHS {
                let source = fixture.core.read_source(path)?;
                assert_eq!(
                    render_java_source(std::str::from_utf8(&source)?, &artificial)?.as_bytes(),
                    fixture.render(path, &off)?
                );
            }
        }
    }
    assert_eq!(fixture.context("1.21.0", &[])?.minecraft_version, "1.21");
    Ok(())
}
#[test]
fn common_linter_edit_reaches_all_ten_targets_in_isolated_tree() -> Result<()> {
    let fixture = Fixture::load()?;
    let path = PATHS[0];
    let original = fixture.core.read_source(path)?;
    let mut edited = original.clone();
    edited.extend_from_slice(b"// isolated shared linter edit\n");
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
        expected.extend_from_slice(b"// isolated shared linter edit\n");
        assert_eq!(actual.as_bytes(), expected);
    }
    assert_eq!(fixture.core.read_source(path)?, original);
    Ok(())
}
#[test]
fn changed_raw_normalization_owner_or_shared_membership_refuses() -> Result<()> {
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
            _ => OWNERS[2],
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
    let mut patches: Vec<MemberPatch> = facet_json::from_str(MEMBER_PATCHES_JSON)?;
    patches[0].owner = "tick_graph_null_safety".to_owned();
    assert_ne!(patches, fixture.patches);
    Ok(())
}
