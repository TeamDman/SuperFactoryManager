//! Actual-core post-promotion contracts for five released GameTest foundations.
//!
//! This module refuses staged/legacy source fallbacks. Frozen Git blobs are
//! bounded byte witnesses only. Mixed feature profiles assert source selection
//! and rendering contracts, not Java compilation or in-game test acceptance.

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

const PREFIX: &str = "src/gametest/java/ca/teamdman/sfm/gametest/";
const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";
const STAGE_PREFIX: &str =
    "platform/cli/sfm-propagate-changes/target/core-gametest-framework-stage-v1/";
const LEDGER: &str = "docs/tasks/sfm-core-gametest-framework-slice.json";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
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
const ANNOTATION: &str = "SFMGameTest.java";
const DEFINITION: &str = "SFMGameTestDefinition.java";
const DISCOVERY: &str = "SFMGameTestDiscovery.java";
const HELPER: &str = "SFMGameTestHelper.java";
const HARNESS: &str = "SFMClientRunHarness.java";
const NAMES: [&str; 5] = [ANNOTATION, DEFINITION, DISCOVERY, HELPER, HARNESS];
const PROPERTIES: &str = "client_properties";
const SIDES: &str = "gametest_sides";
const STATIC_NAMES: &str = "gametest_static_names";
const GAME_PUPPET: &str = "game_puppet_runtime";
const SMOKE: &str = "client_smoke_harness";
const PAUSE: &str = "client_automation_pause_restore";
const DELAY: &str = "client_automation_keep_open_default";
const REGISTRY_COPY: &str = "client_automation_registry_copy";
const ANNOTATIONS: &str = "client_automation_harness_annotations";
const MEK_ISOLATION: &str = "gametest_mekanism_dependency_isolation";
const HISTORY_CLOSURE: [&str; 6] = [
    "command_history",
    "typed_command_palette",
    "command_palette",
    "client_actions",
    "client_theme",
    "keyboard_profiles",
];
const HARNESS_COMMON: [&str; 6] = [PROPERTIES, GAME_PUPPET, SMOKE, PAUSE, DELAY, ANNOTATIONS];

#[derive(Clone, Copy)]
struct Template {
    name: &'static str,
    digest: &'static str,
    bytes: usize,
}
const TEMPLATES: [Template; 5] = [
    Template {
        name: "SFMClientRunHarness.java",
        digest: "sha256:e72c37355f083d711678839474beaf3df798f0e41330a469a45936db52b438c6",
        bytes: 36612,
    },
    Template {
        name: "SFMGameTest.java",
        digest: "sha256:8a37afc66e349cdbc6f8849d0870299c37f8ab03e07e20c67a53e610bf26bba9",
        bytes: 834,
    },
    Template {
        name: "SFMGameTestDefinition.java",
        digest: "sha256:4d38cad91e26db4b822d0b521306e3c380cb274e8a6c4e9c40cbf818f4acab23",
        bytes: 4435,
    },
    Template {
        name: "SFMGameTestDiscovery.java",
        digest: "sha256:f7d98744d3fe977a8d88d026188ecbba1dba78c780393633635548c9729bd9d6",
        bytes: 12495,
    },
    Template {
        name: "SFMGameTestHelper.java",
        digest: "sha256:c6114f87b04dbfc3c2a97763bea31a2e1a1f6fda946858475988fca29944e940",
        bytes: 30163,
    },
];
#[derive(Clone, Copy)]
struct Golden {
    oid: &'static str,
    raw_digest: &'static str,
    raw_bytes: usize,
    normalized_digest: &'static str,
    normalized_bytes: usize,
    removed_cr: usize,
}
const GOLDENS: [Golden; 41] = [
    Golden {
        oid: "d1ea53b49c605a3259bc970068297f4a920ad00e",
        raw_digest: "sha256:a0c878eca26134875385f9f135e61ee0c5f1cfbd2b4cb168f2ef8df89473b2e8",
        raw_bytes: 15748,
        normalized_digest: "sha256:a0c878eca26134875385f9f135e61ee0c5f1cfbd2b4cb168f2ef8df89473b2e8",
        normalized_bytes: 15748,
        removed_cr: 0,
    },
    Golden {
        oid: "47ae8c998a02b646c88814457588ecfdb0c10ca3",
        raw_digest: "sha256:434de69e04b77a78a3f3308ea577c50e441264561aa260f2e37d728f28bb8b9e",
        raw_bytes: 15689,
        normalized_digest: "sha256:434de69e04b77a78a3f3308ea577c50e441264561aa260f2e37d728f28bb8b9e",
        normalized_bytes: 15689,
        removed_cr: 0,
    },
    Golden {
        oid: "c67174eddd49fd83ef8c5fc9c2a5a68b5bf8fcd4",
        raw_digest: "sha256:7186617dde7841ef5aa079cf5018f2ca4b4a8aa84ca1c62b6341cd42c664ee68",
        raw_bytes: 15032,
        normalized_digest: "sha256:7186617dde7841ef5aa079cf5018f2ca4b4a8aa84ca1c62b6341cd42c664ee68",
        normalized_bytes: 15032,
        removed_cr: 0,
    },
    Golden {
        oid: "f11ad5b7b1a10f360905b3cf1b3ed661bf5130f0",
        raw_digest: "sha256:c80d8b0517a054e659922764632b1bacbc6807c0130ae688cc109e187ce69621",
        raw_bytes: 15044,
        normalized_digest: "sha256:c80d8b0517a054e659922764632b1bacbc6807c0130ae688cc109e187ce69621",
        normalized_bytes: 15044,
        removed_cr: 0,
    },
    Golden {
        oid: "703bb696eca41f4b67bf500e277c844b02749d44",
        raw_digest: "sha256:0f1db41fdfc49964a5bc002356be7e5354e85c9edb26a038f67edde2589d0432",
        raw_bytes: 15078,
        normalized_digest: "sha256:0f1db41fdfc49964a5bc002356be7e5354e85c9edb26a038f67edde2589d0432",
        normalized_bytes: 15078,
        removed_cr: 0,
    },
    Golden {
        oid: "d19f681497b3cc6dc40cec159829c5b8339ea7eb",
        raw_digest: "sha256:176c8988c99d295f6e78077ec3263e923bbb3193321fe3fb9717c451e1ab7545",
        raw_bytes: 15193,
        normalized_digest: "sha256:176c8988c99d295f6e78077ec3263e923bbb3193321fe3fb9717c451e1ab7545",
        normalized_bytes: 15193,
        removed_cr: 0,
    },
    Golden {
        oid: "a275cad0f878cf79a349d6609008312c0768c078",
        raw_digest: "sha256:399b777370434ce489bc9c4b798040879a909e99491ac61e91de5332c036fd8e",
        raw_bytes: 15985,
        normalized_digest: "sha256:399b777370434ce489bc9c4b798040879a909e99491ac61e91de5332c036fd8e",
        normalized_bytes: 15985,
        removed_cr: 0,
    },
    Golden {
        oid: "838f2bbfd63e56d29624e7d630492daa239cb954",
        raw_digest: "sha256:6e94ef113f3c15b1d8330fbf1dada81801ad63ac79707689c8d5a1142b561e63",
        raw_bytes: 13841,
        normalized_digest: "sha256:6e94ef113f3c15b1d8330fbf1dada81801ad63ac79707689c8d5a1142b561e63",
        normalized_bytes: 13841,
        removed_cr: 0,
    },
    Golden {
        oid: "bb073a8d3503a164d4665ecc7a521bd64a23d335",
        raw_digest: "sha256:44dfe83ac0384b114c4402d375e182353b9302405f27f6be7136f21b58d64f7f",
        raw_bytes: 13678,
        normalized_digest: "sha256:44dfe83ac0384b114c4402d375e182353b9302405f27f6be7136f21b58d64f7f",
        normalized_bytes: 13678,
        removed_cr: 0,
    },
    Golden {
        oid: "cf7a1e65e7d623078eb7b5b37efebf8faa07436f",
        raw_digest: "sha256:21986d04d674db35b514b7813cbe4c1539ccb0a535b726d7ccc5b9ae8202fd53",
        raw_bytes: 13684,
        normalized_digest: "sha256:21986d04d674db35b514b7813cbe4c1539ccb0a535b726d7ccc5b9ae8202fd53",
        normalized_bytes: 13684,
        removed_cr: 0,
    },
    Golden {
        oid: "4dbbef1ecbd8710fc754df12fe261fee8926741a",
        raw_digest: "sha256:268fdd1d3c317f5f7c8000498126f46cc73bdab73cac79b7b867f131214673d2",
        raw_bytes: 13696,
        normalized_digest: "sha256:268fdd1d3c317f5f7c8000498126f46cc73bdab73cac79b7b867f131214673d2",
        normalized_bytes: 13696,
        removed_cr: 0,
    },
    Golden {
        oid: "63799daeaa60a5dd5bcf123f2ec9249d8fd15815",
        raw_digest: "sha256:bf935670d2eb17c875912b18f25a771cd9d628baa6b8187cbf4e98cc1f942a84",
        raw_bytes: 13730,
        normalized_digest: "sha256:bf935670d2eb17c875912b18f25a771cd9d628baa6b8187cbf4e98cc1f942a84",
        normalized_bytes: 13730,
        removed_cr: 0,
    },
    Golden {
        oid: "0f8f86601c425330bf9855d5f805bd50125affb5",
        raw_digest: "sha256:96ae334f7adad15b04e9ff37ac66fb388e390f13599e1f4b05563a70251a51db",
        raw_bytes: 13888,
        normalized_digest: "sha256:96ae334f7adad15b04e9ff37ac66fb388e390f13599e1f4b05563a70251a51db",
        normalized_bytes: 13888,
        removed_cr: 0,
    },
    Golden {
        oid: "b35e6e79f05f892825b4f084ed6dbb8a002377f4",
        raw_digest: "sha256:35eea92a56e1d2c1015eee3731b21e11935c4e2a995391aa27bee9bbdc2f7a26",
        raw_bytes: 14566,
        normalized_digest: "sha256:35eea92a56e1d2c1015eee3731b21e11935c4e2a995391aa27bee9bbdc2f7a26",
        normalized_bytes: 14566,
        removed_cr: 0,
    },
    Golden {
        oid: "b018684f868b9f843520c523418a33e92d38f515",
        raw_digest: "sha256:8b38cb74113947deccf046843ffe28f7751b457f9a601b516b2e3f83364688fd",
        raw_bytes: 633,
        normalized_digest: "sha256:5d972966399bef314ceac40dd79bcc052b506f03e0622227181537fe790f0762",
        normalized_bytes: 625,
        removed_cr: 8,
    },
    Golden {
        oid: "327a76db6932f802e5f6ec914add7a5a9cc35f0e",
        raw_digest: "sha256:583de39585e91cfab5b56aa870ae77ab706ed9a296cb9933d89d2dbe64f0b609",
        raw_bytes: 400,
        normalized_digest: "sha256:b6d4f4f473dccc71db770bae41cc6548471821c10e097ac497dc8b2257b5f42a",
        normalized_bytes: 386,
        removed_cr: 14,
    },
    Golden {
        oid: "201c5f5c70925468770ef05daff0febd265b8381",
        raw_digest: "sha256:0c831f55c319ddf2186c1a52c8920f6c1df09fe3b895af75036cf8ef33bdeb64",
        raw_bytes: 2294,
        normalized_digest: "sha256:47faea792a28445f670e3ddbb8be1296834881d472102f28e51b95581a6d381f",
        normalized_bytes: 2214,
        removed_cr: 80,
    },
    Golden {
        oid: "b4ee1f1d7f5d54163d4075c63073737e16ac54a2",
        raw_digest: "sha256:82a18cbce1a631df72605823c8ea46c61fe3ddcb510b1b12672e116919ab3201",
        raw_bytes: 2564,
        normalized_digest: "sha256:6dedb6a6e8026f62f85561a0fe8063a79f229c8c6906ca945d8f7532cd201313",
        normalized_bytes: 2481,
        removed_cr: 83,
    },
    Golden {
        oid: "110b37542f837b68cab8164b5e2c46207cdaf031",
        raw_digest: "sha256:f36ee4cc88b839c9fb146e6ccafec794c45e55f7f5597338a4bb64be57a63eb5",
        raw_bytes: 2184,
        normalized_digest: "sha256:e98900653576f712cad015bac32db4fcca0fbbd32309d423ffcf13473463625e",
        normalized_bytes: 2109,
        removed_cr: 75,
    },
    Golden {
        oid: "1fb5ccfc528c945af2d59d1d15959ca9e5644473",
        raw_digest: "sha256:8370e1e96d154a766e0796da254868ad260259fd6f3fa56d8f783c6193f5c053",
        raw_bytes: 2454,
        normalized_digest: "sha256:70229d2724fffcbddd6d182fa4c48937bd90f724e6865325017c3215b79b9009",
        normalized_bytes: 2376,
        removed_cr: 78,
    },
    Golden {
        oid: "9e3949ba6aaf4720c8db4639a83868e04a3ae190",
        raw_digest: "sha256:c82e7e230081adc28a46939af864a948262e59b059fbe53a8f5911abaf600a97",
        raw_bytes: 6348,
        normalized_digest: "sha256:c82e7e230081adc28a46939af864a948262e59b059fbe53a8f5911abaf600a97",
        normalized_bytes: 6348,
        removed_cr: 0,
    },
    Golden {
        oid: "7571daf43a8061892303d0f6302c64dc99ff4d03",
        raw_digest: "sha256:49a22cf9c53f1da6e20d031f691df9e7f2122fd41d3b29908b39aa9249d5be76",
        raw_bytes: 5415,
        normalized_digest: "sha256:49a22cf9c53f1da6e20d031f691df9e7f2122fd41d3b29908b39aa9249d5be76",
        normalized_bytes: 5415,
        removed_cr: 0,
    },
    Golden {
        oid: "5ba7f810190534aa8a8458b5e3af3a53532ce11a",
        raw_digest: "sha256:168e024636e9deaf50518355089ef2f08cc5c7aa4bd9e5a816a37be71d59b4f1",
        raw_bytes: 5419,
        normalized_digest: "sha256:168e024636e9deaf50518355089ef2f08cc5c7aa4bd9e5a816a37be71d59b4f1",
        normalized_bytes: 5419,
        removed_cr: 0,
    },
    Golden {
        oid: "61fdf40007ed68759a1c4501943462846f3c6f18",
        raw_digest: "sha256:b93f9197147bc73602428a48af7a69964c2e093753a328909b664706561591d4",
        raw_bytes: 7743,
        normalized_digest: "sha256:b93f9197147bc73602428a48af7a69964c2e093753a328909b664706561591d4",
        normalized_bytes: 7743,
        removed_cr: 0,
    },
    Golden {
        oid: "cb74ea25d474f34f87863fbdaa54d3ffe73b6a6b",
        raw_digest: "sha256:8b4e14dc47a919a76e39f6db3e0b787c754443abfd11e13e034474594c80cc10",
        raw_bytes: 5523,
        normalized_digest: "sha256:098c8a776fe9a6eac0d50326dfba28ea73a02365bcb439859b9d98e76dcdd897",
        normalized_bytes: 5481,
        removed_cr: 42,
    },
    Golden {
        oid: "d5badda423f8ea6b924d3e68771cecfb6e512489",
        raw_digest: "sha256:be0dc37e19316aae5a27b41137ceef5c1adc27eeaf7c3c56ebb9cc912b147cb3",
        raw_bytes: 5527,
        normalized_digest: "sha256:0ab685503f60c63806a43232915b35452b501fbe4c03982df8d4b1921a03bca1",
        normalized_bytes: 5485,
        removed_cr: 42,
    },
    Golden {
        oid: "4d9573ac3a3cd0a169fde578aadb9984714b3c5b",
        raw_digest: "sha256:52c3991a489c448746125bdad32dd202f5055145129f4fc4d16e971fe936a3c5",
        raw_bytes: 7809,
        normalized_digest: "sha256:52c3991a489c448746125bdad32dd202f5055145129f4fc4d16e971fe936a3c5",
        normalized_bytes: 7809,
        removed_cr: 0,
    },
    Golden {
        oid: "8ffa7f9420b98a0b484abe204b9ae8bb777d2991",
        raw_digest: "sha256:deed562eca243b7f82af5cab171c695538d2d3f54c576aed45a51c27b09c07e8",
        raw_bytes: 17211,
        normalized_digest: "sha256:7a46c7db1cf7299ff999e4887c9cf532676445d5146b6cceee3c0578d185c7e5",
        normalized_bytes: 16700,
        removed_cr: 511,
    },
    Golden {
        oid: "838d59523cdb28bb9705867da3d0397281c06f7d",
        raw_digest: "sha256:6d3146b35421dbfcd20afb6ea97034a75faabee265dadc7837b299c04b407c85",
        raw_bytes: 17225,
        normalized_digest: "sha256:d2f5f0c564bbc55deb812dc7457f39195256590e305aad9c5499bb0f5adc4091",
        normalized_bytes: 16714,
        removed_cr: 511,
    },
    Golden {
        oid: "9970803f0d8785d1f7739d60bfbad74243847769",
        raw_digest: "sha256:bc95b8668641cb611da0eaee40926be1062de49aa0e5636891b930c1e4c760b2",
        raw_bytes: 17416,
        normalized_digest: "sha256:a1baa3a777790bb0e506575f1d851169d1588ac26aea4524fcc25091a69fde87",
        normalized_bytes: 16901,
        removed_cr: 515,
    },
    Golden {
        oid: "a103ff76f246c47c8f89b043655eef0b670440b8",
        raw_digest: "sha256:30af32d000743146c362ee5325784046c5259c8c21227c233d0865631d6c1949",
        raw_bytes: 17428,
        normalized_digest: "sha256:4cfd416fc1592e661cfb27c480aec3a4a97a7894e4a8e96ad425d30c33d4f576",
        normalized_bytes: 16913,
        removed_cr: 515,
    },
    Golden {
        oid: "13e64351b268e6557d122dbfbe2072c22f860c24",
        raw_digest: "sha256:fe6f9adff96afa2b0aea79c6d83e170885a1f7db157613c043aa4c325129120f",
        raw_bytes: 17769,
        normalized_digest: "sha256:c6068417e24002a4fc65e1db43827d9279f39970aef5cc1817942ae03be37d54",
        normalized_bytes: 17246,
        removed_cr: 523,
    },
    Golden {
        oid: "422cdd7adbbbe11cef8d91a5fb1ce08b6399cb67",
        raw_digest: "sha256:6c843e0401105202f9af874b61605cbe886a00678e639ee1fdf11b5efcc4b0d0",
        raw_bytes: 17795,
        normalized_digest: "sha256:c1bc083d42a183fbbca0d83e83fb65f5ddbdddfc3045514ea5397d91587dd45f",
        normalized_bytes: 17272,
        removed_cr: 523,
    },
    Golden {
        oid: "4b728e67776130aa1f48683ac550311c12e69b7f",
        raw_digest: "sha256:05988122a513c566033596f43e430387748bc1d769019f780904e620167e070c",
        raw_bytes: 17384,
        normalized_digest: "sha256:64f91dae6d60fbef6eef861c7becfc145e10fd85d49fd30e46b0be3250d35a27",
        normalized_bytes: 16898,
        removed_cr: 486,
    },
    Golden {
        oid: "21e7bbaaaed5e6f0a24a73f2aa3693683fbe664e",
        raw_digest: "sha256:47caddbe2a184bd4bcd91a5e7944ce4199e5fc75124504d56349658edc03369f",
        raw_bytes: 17202,
        normalized_digest: "sha256:69219132a95689ac4788eb574d8c79983e796c1f0310843494b7924753afa70f",
        normalized_bytes: 16688,
        removed_cr: 514,
    },
    Golden {
        oid: "2aa7cf39f778d8a11557ada6e7931d55902dafd4",
        raw_digest: "sha256:950d94af0b3c864e6a341a4503176b961a29e0a37f7d955ba64d5e685c4ae759",
        raw_bytes: 17216,
        normalized_digest: "sha256:df2a9b04cee42582f3ea0e73777c14c754f2fd99f8b9a9691116baad97c284b0",
        normalized_bytes: 16702,
        removed_cr: 514,
    },
    Golden {
        oid: "8d1e0a9365a50ddb98ed77c1d4a73009f6c297fe",
        raw_digest: "sha256:39a1a58e70908db5822ed5b33b14d18b256daaca92784bf8ef85eed8be12d7d2",
        raw_bytes: 17407,
        normalized_digest: "sha256:4f354f665a304cd580c2553719019d45adceb151d50db30a8b611660e92ad768",
        normalized_bytes: 16889,
        removed_cr: 518,
    },
    Golden {
        oid: "fbcbffb7025431099a9f0d2bf4b1797010995ee6",
        raw_digest: "sha256:cc9a95ca5e8929d676ddddbb1b0ef861fb63b62e29aba86c504466e484783a9e",
        raw_bytes: 17419,
        normalized_digest: "sha256:144009eb4174feed0237ff614a9b7c32e0354d7d2d0631c25a56c960a9241428",
        normalized_bytes: 16901,
        removed_cr: 518,
    },
    Golden {
        oid: "3958acd387762cf1bc4f61b549e117a369b44058",
        raw_digest: "sha256:dec7f7ac46f793db85a6ec42a719bc01a8c95d12a1355d7e82f5f84d866fa903",
        raw_bytes: 19025,
        normalized_digest: "sha256:018ebf38b139ec7ce5ff8d318f594ff7154213f33e52913e7259096151884a8a",
        normalized_bytes: 18468,
        removed_cr: 557,
    },
    Golden {
        oid: "333ca8e9574b5a7dbcdd8b3df3e093b838a452fc",
        raw_digest: "sha256:b47537bd45ad64d8f3fec7b7f778a0151f396288e0be7b461dffd8a5cc374be0",
        raw_bytes: 19051,
        normalized_digest: "sha256:9d32891a0e9f4f552c9416dd0cdd733a6c757c05416ec7808b063921ba63d45d",
        normalized_bytes: 18494,
        removed_cr: 557,
    },
    Golden {
        oid: "048c296a9bc5eaa3c58d208a4abe07db28776949",
        raw_digest: "sha256:3b883910569e5a548e299414e17e68e19077d0f603c08a1f0119fb705d06b6ff",
        raw_bytes: 18677,
        normalized_digest: "sha256:4727b0d879e88c36fa947b35467fe92b2c19843ec64b69fe2488352c54f5f062",
        normalized_bytes: 18157,
        removed_cr: 520,
    },
];
#[derive(Facet)]
struct Ledger {
    schema: String,
    scope: String,
    source_context_commits: BTreeMap<String, String>,
    inputs: Vec<InputEvidence>,
    consumer_contexts: Vec<ConsumerContext>,
}
#[derive(Facet)]
struct InputEvidence {
    path: String,
    core_input: String,
    staged_input: String,
    template_sha256: String,
    template_bytes: usize,
    membership: Predicate,
    raw_variants: Vec<RawVariant>,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Predicate {
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
    none_features: Vec<String>,
}
#[derive(Facet)]
struct RawVariant {
    oid: String,
    raw_sha256: String,
    raw_bytes: usize,
    normalized_sha256: String,
    normalized_bytes: usize,
    crlf_to_lf_count: usize,
    appended_final_lf: bool,
    other_whitespace_or_token_edits: bool,
}
#[derive(Facet)]
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    mode: String,
    raw_blob: String,
    raw_sha256: String,
    raw_bytes: usize,
    normalized_sha256: String,
    normalized_bytes: usize,
    explicit_features: Vec<String>,
    feature_context_origin: String,
    physical_stage_normalized_exact: bool,
}
#[derive(Facet)]
struct ConsumerContext {
    context: String,
    commit: String,
    static_name_callers: Vec<String>,
    mek_method_mentions: Vec<String>,
    puppet_harness_mentions: Vec<String>,
}
struct FrameworkFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    normalized: BTreeMap<String, Vec<u8>>,
}
impl FrameworkFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core_gametest_framework_slice@1"
                && ledger.scope == "five_released_gametest_framework_classes",
            "framework ledger scope changed"
        );
        let commits: BTreeMap<String, String> = COMMITS
            .into_iter()
            .map(|(name, oid)| (name.to_owned(), oid.to_owned()))
            .collect();
        ensure!(
            ledger.source_context_commits == commits && ledger.inputs.len() == 5,
            "framework fixed contexts/path count changed"
        );
        let mut names = BTreeSet::new();
        let mut oids = BTreeSet::new();
        let mut cells = 0;
        for input in ledger.inputs {
            let name = input
                .path
                .strip_prefix(PREFIX)
                .ok_or_else(|| eyre::eyre!("unexpected framework path"))?;
            let fixed = template(name)?;
            ensure!(
                names.insert(name.to_owned())
                    && input.core_input == format!("{CORE_PREFIX}{}", input.path)
                    && input.staged_input == format!("{STAGE_PREFIX}{name}")
                    && input.template_sha256 == fixed.digest
                    && input.template_bytes == fixed.bytes,
                "framework physical template provenance changed"
            );
            ensure!(
                input.membership.targets.is_empty()
                    && input.membership.all_features.is_empty()
                    && input.membership.any_features.is_empty()
                    && input.membership.none_features.is_empty(),
                "released framework membership became optional"
            );
            validate_rule(&core.metadata, &input.path)?;
            let mut local_oids = BTreeSet::new();
            for variant in input.raw_variants {
                let pin = golden(&variant.oid)?;
                ensure!(
                    local_oids.insert(variant.oid.clone())
                        && variant.raw_sha256 == pin.raw_digest
                        && variant.raw_bytes == pin.raw_bytes
                        && variant.normalized_sha256 == pin.normalized_digest
                        && variant.normalized_bytes == pin.normalized_bytes
                        && variant.crlf_to_lf_count == pin.removed_cr
                        && !variant.appended_final_lf
                        && !variant.other_whitespace_or_token_edits,
                    "framework normalization scope changed"
                );
                oids.insert(variant.oid);
            }
            let mut contexts = BTreeSet::new();
            let mut witnessed = BTreeSet::new();
            ensure!(input.witnesses.len() == 20, "incomplete framework matrix");
            for witness in input.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid framework context"))?;
                let oid = expected_oid(name, &witness.context)?;
                let pin = golden(oid)?;
                let flags = flags_for(name, environment == "dev", target)?;
                core.context(target, &flags)?;
                ensure!(
                    contexts.insert(witness.context.clone())
                        && commits.get(&witness.context) == Some(&witness.source_commit)
                        && witness.present
                        && witness.mode == "100644"
                        && witness.raw_blob == oid
                        && witness.raw_sha256 == pin.raw_digest
                        && witness.raw_bytes == pin.raw_bytes
                        && witness.normalized_sha256 == pin.normalized_digest
                        && witness.normalized_bytes == pin.normalized_bytes
                        && same_names(&witness.explicit_features, &flags)
                        && witness.physical_stage_normalized_exact
                        && witness.feature_context_origin
                            == "reconstructed_explicit_owner_selection_not_historical_feature_claim",
                    "framework witness bytes/context/membership changed"
                );
                witnessed.insert(oid.to_owned());
                cells += 1;
            }
            ensure!(
                contexts == commits.keys().cloned().collect() && local_oids == witnessed,
                "framework context/variant coverage drift"
            );
        }
        ensure!(
            names == NAMES.into_iter().map(str::to_owned).collect()
                && oids == GOLDENS.iter().map(|row| row.oid.to_owned()).collect()
                && cells == 100,
            "framework golden scope changed"
        );
        let mut consumers = BTreeSet::new();
        ensure!(
            ledger.consumer_contexts.len() == 20,
            "incomplete framework consumer trees"
        );
        for record in ledger.consumer_contexts {
            ensure!(
                consumers.insert(record.context.clone())
                    && commits.get(&record.context) == Some(&record.commit),
                "framework consumer witness identity changed"
            );
            let dev = record.context.starts_with("dev/");
            ensure!(
                (dev || (record.static_name_callers.is_empty()
                    && record.puppet_harness_mentions.is_empty()))
                    && record
                        .static_name_callers
                        .iter()
                        .chain(&record.mek_method_mentions)
                        .chain(&record.puppet_harness_mentions)
                        .all(|path| path.starts_with("platform/minecraft/src/")
                            && path.ends_with(".java")),
                "unreviewed framework consumer boundary"
            );
        }
        ensure!(
            consumers == commits.keys().cloned().collect(),
            "consumer tree scope drift"
        );
        let raw = read_git_blobs(&core.repository, &oids)?;
        let mut normalized = BTreeMap::new();
        for pin in GOLDENS {
            normalized.insert(pin.oid.to_owned(), verify_raw(&raw[pin.oid], pin)?);
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            NAMES
                .iter()
                .all(|name| inventory.contains(&format!("{PREFIX}{name}"))),
            "framework sources must be promoted before actual-core tests run"
        );
        Ok(Self {
            core,
            inventory,
            normalized,
        })
    }

    fn render(&self, name: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let path = format!("{PREFIX}{name}");
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let input = selection
            .inputs
            .get(&path)
            .ok_or_else(|| eyre::eyre!("released framework source omitted"))?;
        ensure!(
            input.input == path,
            "framework gained a historical fallback/alternate input"
        );
        let bytes = self.core.read_source(&path)?;
        verify_template(&bytes, template(name)?)?;
        Ok(render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes())
    }
    fn body(&self, name: &str, target: &str, flags: &[&str]) -> Result<String> {
        let context = self.core.context(target, flags)?;
        Ok(String::from_utf8(self.render(name, &context)?)?)
    }
}
fn template(name: &str) -> Result<Template> {
    TEMPLATES
        .iter()
        .find(|row| row.name == name)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected framework template"))
}
fn golden(oid: &str) -> Result<Golden> {
    GOLDENS
        .iter()
        .find(|row| row.oid == oid)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected framework raw blob"))
}
fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn modern(target: &str) -> bool {
    matches!(target, "1.21.0" | "1.21.1" | "26.1.2")
}
fn flags_for(name: &str, dev: bool, target: &str) -> Result<Vec<&'static str>> {
    ensure!(TARGETS.contains(&target), "unknown framework target");
    if !dev {
        return Ok(vec![]);
    }
    let flags = match name {
        ANNOTATION if is_d2(target) => vec![SIDES],
        ANNOTATION => vec![],
        DEFINITION => vec![STATIC_NAMES],
        DISCOVERY if is_d2(target) => vec![PROPERTIES, SIDES],
        DISCOVERY => vec![PROPERTIES],
        HELPER if modern(target) => vec![PROPERTIES, MEK_ISOLATION],
        HELPER => vec![PROPERTIES],
        HARNESS => {
            let mut flags = HARNESS_COMMON.to_vec();
            if is_d2(target) {
                flags.extend(HISTORY_CLOSURE);
            }
            if target == "1.19.2" {
                flags.push(REGISTRY_COPY);
            }
            flags
        }
        _ => return Err(eyre::eyre!("unknown framework path")),
    };
    Ok(flags)
}
fn validate_rule(metadata: &CoreProjectInputs, path: &str) -> Result<()> {
    if let Some(rules) = metadata.source_rules.get(path) {
        ensure!(
            rules.len() == 1
                && rules[0].input == path
                && rules[0].when.targets.is_empty()
                && rules[0].when.all_features.is_empty()
                && rules[0].when.any_features.is_empty()
                && rules[0].when.none_features.is_empty(),
            "released framework must remain shared/unconditional"
        );
    }
    Ok(())
}
fn normalized_lf(bytes: &[u8]) -> Result<Vec<u8>> {
    ensure!(
        bytes.ends_with(b"\n"),
        "framework final-LF changes are not authorized"
    );
    let text = std::str::from_utf8(bytes)?;
    ensure!(
        !text.replace("\r\n", "").contains('\r'),
        "framework lone-CR changes are not authorized"
    );
    Ok(text.replace("\r\n", "\n").into_bytes())
}
fn verify_raw(bytes: &[u8], pin: Golden) -> Result<Vec<u8>> {
    ensure!(
        bytes.len() == pin.raw_bytes && sha256(bytes) == pin.raw_digest,
        "framework exact raw witness changed"
    );
    let normalized = normalized_lf(bytes)?;
    ensure!(
        normalized.len() == pin.normalized_bytes
            && sha256(&normalized) == pin.normalized_digest
            && bytes.len() - normalized.len() == pin.removed_cr,
        "framework normalization exceeded exact CRLF scope"
    );
    Ok(normalized)
}
fn verify_template(bytes: &[u8], pin: Template) -> Result<()> {
    ensure!(
        bytes.len() == pin.bytes
            && sha256(bytes) == pin.digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "promoted framework authoring bytes changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn expected_oid(name: &str, context: &str) -> Result<&'static str> {
    match name {
        "SFMClientRunHarness.java" => match context {
            "dev/1.19.2" => Ok("d1ea53b49c605a3259bc970068297f4a920ad00e"),
            "dev/1.19.4" => Ok("47ae8c998a02b646c88814457588ecfdb0c10ca3"),
            "dev/1.20" | "dev/1.20.1" => Ok("c67174eddd49fd83ef8c5fc9c2a5a68b5bf8fcd4"),
            "dev/1.20.2" => Ok("f11ad5b7b1a10f360905b3cf1b3ed661bf5130f0"),
            "dev/1.20.3" | "dev/1.20.4" => Ok("703bb696eca41f4b67bf500e277c844b02749d44"),
            "dev/1.21.0" | "dev/1.21.1" => Ok("d19f681497b3cc6dc40cec159829c5b8339ea7eb"),
            "dev/26.1.2" => Ok("a275cad0f878cf79a349d6609008312c0768c078"),
            "release/1.19.2" => Ok("838f2bbfd63e56d29624e7d630492daa239cb954"),
            "release/1.19.4" => Ok("bb073a8d3503a164d4665ecc7a521bd64a23d335"),
            "release/1.20" | "release/1.20.1" => Ok("cf7a1e65e7d623078eb7b5b37efebf8faa07436f"),
            "release/1.20.2" => Ok("4dbbef1ecbd8710fc754df12fe261fee8926741a"),
            "release/1.20.3" | "release/1.20.4" => Ok("63799daeaa60a5dd5bcf123f2ec9249d8fd15815"),
            "release/1.21.0" | "release/1.21.1" => Ok("0f8f86601c425330bf9855d5f805bd50125affb5"),
            "release/26.1.2" => Ok("b35e6e79f05f892825b4f084ed6dbb8a002377f4"),
            _ => Err(eyre::eyre!("unknown fixed framework context")),
        },
        "SFMGameTest.java" => match context {
            "dev/1.19.2" | "dev/1.19.4" => Ok("b018684f868b9f843520c523418a33e92d38f515"),
            "dev/1.20" | "dev/1.20.1" | "dev/1.20.2" | "dev/1.20.3" | "dev/1.20.4"
            | "dev/1.21.0" | "dev/1.21.1" | "dev/26.1.2" | "release/1.19.2" | "release/1.19.4"
            | "release/1.20" | "release/1.20.1" | "release/1.20.2" | "release/1.20.3"
            | "release/1.20.4" | "release/1.21.0" | "release/1.21.1" | "release/26.1.2" => {
                Ok("327a76db6932f802e5f6ec914add7a5a9cc35f0e")
            }
            _ => Err(eyre::eyre!("unknown fixed framework context")),
        },
        "SFMGameTestDefinition.java" => match context {
            "dev/1.19.2" | "dev/1.19.4" | "dev/1.20" | "dev/1.20.1" | "dev/1.20.2"
            | "dev/1.20.3" | "dev/1.20.4" | "dev/1.21.0" | "dev/1.21.1" => {
                Ok("201c5f5c70925468770ef05daff0febd265b8381")
            }
            "dev/26.1.2" => Ok("b4ee1f1d7f5d54163d4075c63073737e16ac54a2"),
            "release/1.19.2" | "release/1.19.4" | "release/1.20" | "release/1.20.1"
            | "release/1.20.2" | "release/1.20.3" | "release/1.20.4" | "release/1.21.0"
            | "release/1.21.1" => Ok("110b37542f837b68cab8164b5e2c46207cdaf031"),
            "release/26.1.2" => Ok("1fb5ccfc528c945af2d59d1d15959ca9e5644473"),
            _ => Err(eyre::eyre!("unknown fixed framework context")),
        },
        "SFMGameTestDiscovery.java" => match context {
            "dev/1.19.2" | "dev/1.19.4" => Ok("9e3949ba6aaf4720c8db4639a83868e04a3ae190"),
            "dev/1.20" | "dev/1.20.1" => Ok("7571daf43a8061892303d0f6302c64dc99ff4d03"),
            "dev/1.20.2" | "dev/1.20.3" | "dev/1.20.4" | "dev/1.21.0" | "dev/1.21.1" => {
                Ok("5ba7f810190534aa8a8458b5e3af3a53532ce11a")
            }
            "dev/26.1.2" => Ok("61fdf40007ed68759a1c4501943462846f3c6f18"),
            "release/1.19.2" | "release/1.19.4" | "release/1.20" | "release/1.20.1" => {
                Ok("cb74ea25d474f34f87863fbdaa54d3ffe73b6a6b")
            }
            "release/1.20.2" | "release/1.20.3" | "release/1.20.4" | "release/1.21.0"
            | "release/1.21.1" => Ok("d5badda423f8ea6b924d3e68771cecfb6e512489"),
            "release/26.1.2" => Ok("4d9573ac3a3cd0a169fde578aadb9984714b3c5b"),
            _ => Err(eyre::eyre!("unknown fixed framework context")),
        },
        "SFMGameTestHelper.java" => match context {
            "dev/1.19.2" => Ok("8ffa7f9420b98a0b484abe204b9ae8bb777d2991"),
            "dev/1.19.4" => Ok("838d59523cdb28bb9705867da3d0397281c06f7d"),
            "dev/1.20" | "dev/1.20.1" => Ok("9970803f0d8785d1f7739d60bfbad74243847769"),
            "dev/1.20.2" | "dev/1.20.3" | "dev/1.20.4" => {
                Ok("a103ff76f246c47c8f89b043655eef0b670440b8")
            }
            "dev/1.21.0" => Ok("13e64351b268e6557d122dbfbe2072c22f860c24"),
            "dev/1.21.1" => Ok("422cdd7adbbbe11cef8d91a5fb1ce08b6399cb67"),
            "dev/26.1.2" => Ok("4b728e67776130aa1f48683ac550311c12e69b7f"),
            "release/1.19.2" => Ok("21e7bbaaaed5e6f0a24a73f2aa3693683fbe664e"),
            "release/1.19.4" => Ok("2aa7cf39f778d8a11557ada6e7931d55902dafd4"),
            "release/1.20" | "release/1.20.1" => Ok("8d1e0a9365a50ddb98ed77c1d4a73009f6c297fe"),
            "release/1.20.2" | "release/1.20.3" | "release/1.20.4" => {
                Ok("fbcbffb7025431099a9f0d2bf4b1797010995ee6")
            }
            "release/1.21.0" => Ok("3958acd387762cf1bc4f61b549e117a369b44058"),
            "release/1.21.1" => Ok("333ca8e9574b5a7dbcdd8b3df3e093b838a452fc"),
            "release/26.1.2" => Ok("048c296a9bc5eaa3c58d208a4abe07db28776949"),
            _ => Err(eyre::eyre!("unknown fixed framework context")),
        },
        _ => Err(eyre::eyre!("unexpected framework path")),
    }
}

#[test]
fn gametest_framework_reconstructs_all100_present_frozen_source_cells() -> Result<()> {
    let fixture = FrameworkFixture::load()?;
    let mut cells = 0;
    for (context, _) in COMMITS {
        let (environment, target) = context.split_once('/').expect("fixed context");
        for name in NAMES {
            let flags = flags_for(name, environment == "dev", target)?;
            let context = fixture.core.context(target, &flags)?;
            assert_eq!(
                fixture.render(name, &context)?,
                fixture.normalized[expected_oid(name, &format!("{environment}/{target}"))?],
                "framework source mismatch: {name} / {environment}/{target}"
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 100);
    Ok(())
}

#[test]
fn gametest_physical_side_filter_and_static_names_do_not_enable_packet_or_client_runtime()
-> Result<()> {
    let fixture = FrameworkFixture::load()?;
    for target in &TARGETS[..2] {
        for mask in 0_u8..8 {
            let flags: Vec<_> = [SIDES, STATIC_NAMES, PROPERTIES]
                .into_iter()
                .enumerate()
                .filter_map(|(bit, flag)| (mask & (1 << bit) != 0).then_some(flag))
                .collect();
            let context = fixture.core.context(target, &flags)?;
            for unrelated in [
                "packet_values",
                "packet_computation",
                "client_manager",
                "client_program_actions",
                "mod_event_filtering",
                GAME_PUPPET,
            ] {
                assert!(
                    !context.features[unrelated],
                    "side/name metadata acquired {unrelated}"
                );
            }
            let annotation = String::from_utf8(fixture.render(ANNOTATION, &context)?)?;
            let discovery = String::from_utf8(fixture.render(DISCOVERY, &context)?)?;
            let definition = String::from_utf8(fixture.render(DEFINITION, &context)?)?;
            assert_eq!(annotation.contains("SFMDist[] value()"), mask & 1 != 0);
            assert_eq!(
                discovery.contains(".filter(SFMGameTestDiscovery::isCompatibleWithCurrentDist)"),
                mask & 1 != 0
            );
            assert_eq!(
                definition.contains("public static String testNameFor(Class<?> clazz)"),
                mask & 2 != 0
            );
            assert_eq!(
                discovery.contains("SFMProperties.gameTestSelection()"),
                mask & 4 != 0
            );
            if mask & 1 != 0 {
                assert!(
                    discovery.find(".filter(SFMGameTestDiscovery::isCompatibleWithCurrentDist)")
                        < discovery
                            .find(".map(SFMAnnotationUtils.SFMAnnotationData::tryLoadClass)")
                );
                let generated = discovery
                    .split("public static Stream<SFMGameTestDefinition> gatherGeneratedTests()")
                    .nth(1)
                    .expect("preserved generated discovery");
                assert!(!generated.contains("isCompatibleWithCurrentDist"));
            }
        }
    }
    for target in &TARGETS[2..] {
        assert!(fixture.core.context(target, &[SIDES]).is_err());
    }
    Ok(())
}

#[test]
fn gametest_harness_masks_keep_smoke_pause_property_delay_and_puppet_routes_independent()
-> Result<()> {
    let fixture = FrameworkFixture::load()?;
    let mut valid = 0;
    let mut rejected = 0;
    for target in &TARGETS[..2] {
        for mask in 0_u8..64 {
            let flags: Vec<_> = HARNESS_COMMON
                .into_iter()
                .enumerate()
                .filter_map(|(bit, flag)| (mask & (1 << bit) != 0).then_some(flag))
                .collect();
            let properties = mask & 1 != 0;
            let game = mask & 2 != 0;
            let smoke = mask & 4 != 0;
            let context = fixture.core.context(target, &flags);
            if !properties && (game || smoke) {
                assert!(
                    context.is_err(),
                    "missing typed-mode prerequisite was auto-added"
                );
                rejected += 1;
                continue;
            }
            let context = context?;
            let body = String::from_utf8(fixture.render(HARNESS, &context)?)?;
            assert_eq!(body.contains("SFMProperties.clientRunMode()"), properties);
            assert_eq!(body.contains("private enum Mode"), !properties);
            assert_eq!(body.contains("SFMGamePuppetHarness.onClientTick()"), game);
            assert_eq!(body.contains("SFM_CLIENT_SMOKE_READY title_screen"), !smoke);
            assert_eq!(
                body.contains("private static void restorePuppetRuntimeOptions()"),
                mask & 8 != 0
            );
            assert_eq!(body.contains("minecraft.options.save();"), mask & 8 == 0);
            assert_eq!(
                body.contains("private static @Nullable MultipleTestTracker"),
                mask & 32 != 0
            );
            let default_line = if properties {
                if mask & 16 != 0 {
                    "SFMProperties.clientRunKeepOpenSeconds(25)"
                } else {
                    "SFMProperties.clientRunKeepOpenSeconds(10)"
                }
            } else if mask & 16 != 0 {
                "Integer.getInteger(KEEP_OPEN_SECONDS_PROPERTY, 25)"
            } else {
                "Integer.getInteger(KEEP_OPEN_SECONDS_PROPERTY, 10)"
            };
            assert!(body.contains(default_line));
            assert!(!body.contains("SFMCommandHistoryService.installForTests"));
            for name in NAMES {
                let _ = fixture.render(name, &context)?;
            }
            valid += 1;
        }
    }
    assert_eq!((valid, rejected), (80, 48));
    Ok(())
}

#[test]
fn gametest_version_and_optional_mekanism_api_boundaries_remain_explicit() -> Result<()> {
    let fixture = FrameworkFixture::load()?;
    for target in TARGETS {
        let definition = fixture.body(DEFINITION, target, &[])?;
        let discovery = fixture.body(DISCOVERY, target, &[])?;
        let helper = fixture.body(HELPER, target, &[])?;
        let harness = fixture.body(HARNESS, target, &[])?;
        let latest = target == "26.1.2";
        assert_eq!(
            definition.contains("FunctionGameTestInstance intoTestInstance("),
            latest
        );
        assert_eq!(
            definition.contains("public TestFunction intoTestFunction()"),
            !latest
        );
        assert_eq!(discovery.contains("Registries.TEST_ENVIRONMENT"), latest);
        assert_eq!(
            discovery.contains("GameTestRegistry.getAllTestFunctions()"),
            !latest
        );
        assert_eq!(helper.contains("getAndPrepMekTile"), modern(target));
        assert_eq!(
            helper.contains("ResourceHandler<ItemResource> getItemResourceHandler"),
            latest
        );
        assert_eq!(
            harness.contains("ClientTickEvent.Post event"),
            modern(target)
        );
        assert_eq!(
            harness.contains("WorldPresets::createFlatWorldDimensions"),
            latest
        );
        if modern(target) {
            let isolated = fixture.body(HELPER, target, &[MEK_ISOLATION])?;
            assert!(
                !isolated.contains("getAndPrepMekTile") && !isolated.contains("import mekanism.")
            );
            assert!(isolated.contains("Long.getLong("));
            let typed = fixture.body(HELPER, target, &[PROPERTIES])?;
            assert!(typed.contains("getAndPrepMekTile"));
            assert!(typed.contains("SFMProperties.gameTestMaxProgramRunMillis(80L)"));
        } else {
            assert!(fixture.core.context(target, &[MEK_ISOLATION]).is_err());
        }
    }
    let copied = fixture.body(HARNESS, "1.19.2", &[REGISTRY_COPY])?;
    assert!(copied.contains("RegistryAccess.builtinCopy()"));
    assert!(!copied.contains("SFMGamePuppetHarness") && !copied.contains("SFMProperties"));
    for target in &TARGETS[1..] {
        assert!(fixture.core.context(target, &[REGISTRY_COPY]).is_err());
    }
    Ok(())
}

#[test]
fn gametest_common_edit_propagates_across_all10_targets_in_an_isolated_core_tree() -> Result<()> {
    let fixture = FrameworkFixture::load()?;
    let temp = tempfile::tempdir()?;
    let core = temp.path().join("platform/minecraft/core-liquid-template");
    let path = format!("{PREFIX}{ANNOTATION}");
    let destination = core.join(&path);
    fs::create_dir_all(destination.parent().expect("fixed source parent"))?;
    let original = fixture.core.read_source(&path)?;
    let mut edited = original.clone();
    edited.extend_from_slice(b"// isolated shared GameTest contract\n");
    fs::write(&destination, &edited)?;
    let inventory = discover_core_source_files(&core)?;
    let mut metadata = fixture.core.metadata.clone();
    metadata.source_rules.retain(|output, _| output == &path);
    metadata.project_files.clear();
    for target in TARGETS {
        let context = fixture.core.context(target, &[])?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        assert_eq!(selected.inputs[&path].input, path);
        let bytes = read_bounded(
            &checked_file(&core, &selected.inputs[&path].input)?,
            1024 * 1024,
        )?;
        let actual = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
        let mut expected = fixture.render(ANNOTATION, &context)?;
        expected.extend_from_slice(b"// isolated shared GameTest contract\n");
        assert_eq!(actual.as_bytes(), expected);
    }
    assert_eq!(fixture.core.read_source(&path)?, original);
    Ok(())
}

#[test]
fn gametest_raw_template_and_unconditional_membership_mutations_fail_closed() -> Result<()> {
    let fixture = FrameworkFixture::load()?;
    assert!(normalized_lf(b"no final LF").is_err());
    assert!(normalized_lf(b"lone\rCR\n").is_err());
    assert_eq!(
        normalized_lf(b" keep spaces \r\n\r\n")?,
        b" keep spaces \n\n"
    );
    let pin = template(ANNOTATION)?;
    let mut changed = fixture.core.read_source(&format!("{PREFIX}{ANNOTATION}"))?;
    changed.push(b'\n');
    assert!(verify_template(&changed, pin).is_err());
    changed.pop();
    changed[0] ^= 1;
    assert!(verify_template(&changed, pin).is_err());
    let mut metadata = fixture.core.metadata.clone();
    let path = format!("{PREFIX}{ANNOTATION}");
    let variant = super::core_inputs::InputVariant {
        input: path.clone(),
        when: super::core_inputs::InputPredicate {
            all_features: vec![SIDES.to_owned()],
            ..Default::default()
        },
        template: false,
    };
    metadata
        .source_rules
        .insert(path.clone(), vec![variant.clone()]);
    assert!(validate_rule(&metadata, &path).is_err());
    metadata
        .source_rules
        .insert(path, vec![variant.clone(), variant]);
    let context = fixture.core.context("1.19.2", &[SIDES])?;
    assert!(select_core_inputs(&metadata, &context, &fixture.inventory).is_err());
    for target in TARGETS {
        for name in NAMES {
            let flags = flags_for(name, true, target)?;
            let context = fixture.core.context(target, &flags)?;
            let mut described = context.clone();
            described.environment = "release".to_owned();
            described.projection_key = "arbitrary/nested/gametest-review".to_owned();
            described.preset = "different-visible-label".to_owned();
            assert_eq!(
                fixture.render(name, &context)?,
                fixture.render(name, &described)?
            );
        }
    }
    Ok(())
}
