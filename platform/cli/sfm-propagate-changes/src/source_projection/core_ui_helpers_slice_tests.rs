//! Frozen source-only UI-helper migration goldens.
//!
//! Raw offline Git blobs and explicitly approved CRLF-only transformations are
//! independent witnesses, never production source fallbacks. This module
//! exercises actual core membership and Liquid rendering, not Java compilation,
//! canvas rendering or complete-project network-layout admission. Future common
//! edits require a deliberate review of these frozen migration goldens.

#![cfg(test)]

use super::context::ProjectionContext;
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

const LEDGER: &str = "docs/tasks/sfm-core-ui-helpers-slice.json";
const LIMIT: u64 = 256 * 1024;
const GRAMMAR: &str = "src/main/antlr/sfml/SFML.g4";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const FLAGS: [&str; 5] = [
    "font_formatted_text",
    "font_caller_owned_batches",
    "screen_fractional_highlights",
    "canvas_text_editor",
    "client_theme",
];
const LEXICAL: [&[&str]; 6] = [
    &[],
    &["packet_computation"],
    &["packet_computation", "packet_transport_private"],
    &["client_frame_language"],
    &["packet_computation", "client_program_actions"],
    &[
        "packet_computation",
        "packet_transport_private",
        "client_frame_language",
        "client_program_actions",
    ],
];
struct ContextGolden {
    name: &'static str,
    commit: &'static str,
    blobs: [&'static str; 4],
}
const CONTEXTS: [ContextGolden; 20] = [
    ContextGolden {
        name: "dev/1.19.2",
        commit: "f3ff2f6425434f36c7c680fa909c977b158e1860",
        blobs: [
            "1a96ac210229743d775e68646d8ae249bbdd9abe",
            "d44c10c98b7d088aecd2f890219ececc4f6d7475",
            "2de7e070c6888a33a04962e0705841dbe80ca366",
            "8067614a218219194af6d6a5d1a27b485b37c0c1",
        ],
    },
    ContextGolden {
        name: "dev/1.19.4",
        commit: "2e3b561c15d663fb89fd353ccc2af67eeb0c2053",
        blobs: [
            "af15b44daf70bd82c608604ab3cab61cc70629e8",
            "0d43741be2346fe527fee17b0f330fc90f6e585d",
            "2de7e070c6888a33a04962e0705841dbe80ca366",
            "8067614a218219194af6d6a5d1a27b485b37c0c1",
        ],
    },
    ContextGolden {
        name: "dev/1.20",
        commit: "6bf4845761d06560fc5e0e36018b5d583589004d",
        blobs: [
            "2a4b7648a8ef54e618a189bab9bad6a40d3ef0ed",
            "ad47bf67803f0c6c9204b88cf253eb0a55b6bf86",
            "919dd44aa346a40e43ca81b469c8a2cb8e9f3c92",
            "0a54989f09a812356005aff11888ee362f9ad02e",
        ],
    },
    ContextGolden {
        name: "dev/1.20.1",
        commit: "faa040ce14dd825f2dd9716ea59508bf46278c06",
        blobs: [
            "2a4b7648a8ef54e618a189bab9bad6a40d3ef0ed",
            "ad47bf67803f0c6c9204b88cf253eb0a55b6bf86",
            "919dd44aa346a40e43ca81b469c8a2cb8e9f3c92",
            "0a54989f09a812356005aff11888ee362f9ad02e",
        ],
    },
    ContextGolden {
        name: "dev/1.20.2",
        commit: "a829fb4db2ae06eddd2defb22e33f0b45a4b3ff9",
        blobs: [
            "2a4b7648a8ef54e618a189bab9bad6a40d3ef0ed",
            "ad47bf67803f0c6c9204b88cf253eb0a55b6bf86",
            "0e354595ac56ab88ad232a4b08889fbcf1a8e2fd",
            "0a54989f09a812356005aff11888ee362f9ad02e",
        ],
    },
    ContextGolden {
        name: "dev/1.20.3",
        commit: "704aa69edad5376d8d6cfb0b0ef7845af077e647",
        blobs: [
            "2a4b7648a8ef54e618a189bab9bad6a40d3ef0ed",
            "ad47bf67803f0c6c9204b88cf253eb0a55b6bf86",
            "0e354595ac56ab88ad232a4b08889fbcf1a8e2fd",
            "0a54989f09a812356005aff11888ee362f9ad02e",
        ],
    },
    ContextGolden {
        name: "dev/1.20.4",
        commit: "11d3ed07d654ff801329f17cf1eb81c2b347eecd",
        blobs: [
            "2a4b7648a8ef54e618a189bab9bad6a40d3ef0ed",
            "ad47bf67803f0c6c9204b88cf253eb0a55b6bf86",
            "0e354595ac56ab88ad232a4b08889fbcf1a8e2fd",
            "0a54989f09a812356005aff11888ee362f9ad02e",
        ],
    },
    ContextGolden {
        name: "dev/1.21.0",
        commit: "43068d610b1c053c6569be486439769eec2ae9ef",
        blobs: [
            "2a4b7648a8ef54e618a189bab9bad6a40d3ef0ed",
            "ad47bf67803f0c6c9204b88cf253eb0a55b6bf86",
            "0e354595ac56ab88ad232a4b08889fbcf1a8e2fd",
            "0a54989f09a812356005aff11888ee362f9ad02e",
        ],
    },
    ContextGolden {
        name: "dev/1.21.1",
        commit: "7524ab5512878b773e212600c9578b2bc4db4717",
        blobs: [
            "2a4b7648a8ef54e618a189bab9bad6a40d3ef0ed",
            "ad47bf67803f0c6c9204b88cf253eb0a55b6bf86",
            "0e354595ac56ab88ad232a4b08889fbcf1a8e2fd",
            "0a54989f09a812356005aff11888ee362f9ad02e",
        ],
    },
    ContextGolden {
        name: "dev/26.1.2",
        commit: "6bd27f03863ddd041344cbc3da51b01a7b3c3e1f",
        blobs: [
            "90e87b30871c56e34427ecaa820ca17ab6ee2182",
            "9c2e9aa326e6f0720e3d052043933eee78ada3c1",
            "66a0e92daf9265ff8ca4cb8cb0c10d231d970b13",
            "0a54989f09a812356005aff11888ee362f9ad02e",
        ],
    },
    ContextGolden {
        name: "release/1.19.2",
        commit: "31135b8e86801b862d5cb2283c7c5878b7cc5bb4",
        blobs: [
            "757b12b044b02dbb7c86fda310b407d642441e94",
            "5330b8161278a7df58018de70abca16477da5442",
            "ca2adde34af722f19ec90fa80d7c63726a852f4b",
            "599794f144508fb0f60a1aa60f98a86cbf34a1a3",
        ],
    },
    ContextGolden {
        name: "release/1.19.4",
        commit: "23785b63e3e1fe35e5be6a6d89b6638ee0ff0daa",
        blobs: [
            "7899a651f0ad7067e44dcff484af1774d889e56c",
            "7c8368e275e8dc843166f856930ae3b40e458009",
            "ca2adde34af722f19ec90fa80d7c63726a852f4b",
            "599794f144508fb0f60a1aa60f98a86cbf34a1a3",
        ],
    },
    ContextGolden {
        name: "release/1.20",
        commit: "3df18123a19535fd0e5d1dc81aa302105c3fd2f6",
        blobs: [
            "34c0fc3dd74d2ee5e4e243a387c40ed25709c37f",
            "3330077f84ed9f0bbbc966869569316f5df3181c",
            "c6ec0f57b6dfdf9d572ba6bf890378519fff4433",
            "599794f144508fb0f60a1aa60f98a86cbf34a1a3",
        ],
    },
    ContextGolden {
        name: "release/1.20.1",
        commit: "bb5babf12f467235b3a44ad5098666ee3ed171ec",
        blobs: [
            "34c0fc3dd74d2ee5e4e243a387c40ed25709c37f",
            "3330077f84ed9f0bbbc966869569316f5df3181c",
            "c6ec0f57b6dfdf9d572ba6bf890378519fff4433",
            "599794f144508fb0f60a1aa60f98a86cbf34a1a3",
        ],
    },
    ContextGolden {
        name: "release/1.20.2",
        commit: "cfbbafaeda4a006ae32743a92de330711983056b",
        blobs: [
            "34c0fc3dd74d2ee5e4e243a387c40ed25709c37f",
            "3330077f84ed9f0bbbc966869569316f5df3181c",
            "f9ef4b17fb2e96a0d1478a5e7a6941e2898822f4",
            "599794f144508fb0f60a1aa60f98a86cbf34a1a3",
        ],
    },
    ContextGolden {
        name: "release/1.20.3",
        commit: "1b7f9605da0ef13c7601daf3786545868dfc3c78",
        blobs: [
            "34c0fc3dd74d2ee5e4e243a387c40ed25709c37f",
            "3330077f84ed9f0bbbc966869569316f5df3181c",
            "f9ef4b17fb2e96a0d1478a5e7a6941e2898822f4",
            "599794f144508fb0f60a1aa60f98a86cbf34a1a3",
        ],
    },
    ContextGolden {
        name: "release/1.20.4",
        commit: "a637581b5e1078d7cc0ca68333add568e3e387ff",
        blobs: [
            "34c0fc3dd74d2ee5e4e243a387c40ed25709c37f",
            "3330077f84ed9f0bbbc966869569316f5df3181c",
            "f9ef4b17fb2e96a0d1478a5e7a6941e2898822f4",
            "599794f144508fb0f60a1aa60f98a86cbf34a1a3",
        ],
    },
    ContextGolden {
        name: "release/1.21.0",
        commit: "6bfab8a21e7a5bbeb0bb136bf4e5b26f6ce03e25",
        blobs: [
            "34c0fc3dd74d2ee5e4e243a387c40ed25709c37f",
            "3330077f84ed9f0bbbc966869569316f5df3181c",
            "f9ef4b17fb2e96a0d1478a5e7a6941e2898822f4",
            "599794f144508fb0f60a1aa60f98a86cbf34a1a3",
        ],
    },
    ContextGolden {
        name: "release/1.21.1",
        commit: "f5366c79c823ff52712130e69dd9c8166c70bd14",
        blobs: [
            "34c0fc3dd74d2ee5e4e243a387c40ed25709c37f",
            "3330077f84ed9f0bbbc966869569316f5df3181c",
            "f9ef4b17fb2e96a0d1478a5e7a6941e2898822f4",
            "599794f144508fb0f60a1aa60f98a86cbf34a1a3",
        ],
    },
    ContextGolden {
        name: "release/26.1.2",
        commit: "fe32b29453b13b4f3050ad441677c7eb79e80814",
        blobs: [
            "afaf1a19fab4f0713745e9891f25c27a30ece459",
            "3c2c0cffeee5adbd62bf64b85bd7c88cb185b74b",
            "b95b408654391ec331f652008e928172300dae1e",
            "599794f144508fb0f60a1aa60f98a86cbf34a1a3",
        ],
    },
];
struct FileGolden {
    path: &'static str,
    bytes: u64,
    digest: &'static str,
}
const FILES: [FileGolden; 4] = [
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/screen/SFMFontUtils.java",
        bytes: 15462,
        digest: "sha256:8308845678605cf0bf052c1cfa6f93b2d98c97b4b9e2b5b879b3528c5fd4a78b",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/screen/SFMScreenRenderUtils.java",
        bytes: 4234,
        digest: "sha256:eb2104fe9ac8ce28a229d0db570e810d469e4fdaa2b346f02b1f701937130a02",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/screen/SFMTextEditorConfigScreen.java",
        bytes: 21894,
        digest: "sha256:a5fed1b08f5de01d21054f128660b3506877901f8d92bcf045be398ffbfe9181",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/text_styling/ProgramSyntaxHighlightingHelper.java",
        bytes: 10655,
        digest: "sha256:0d4daf5bde910597e20c17fa1b8734ec31d70eb827a3ac85f1adc9c5bbe4278a",
    },
];
struct RawGolden {
    blob: &'static str,
    bytes: u64,
    digest: &'static str,
    crlf: usize,
    normalized_bytes: u64,
    normalized_digest: &'static str,
}
const RAW: [RawGolden; 27] = [
    RawGolden {
        blob: "0a54989f09a812356005aff11888ee362f9ad02e",
        bytes: 6854,
        digest: "sha256:312b87d95e0eb39a28fad45adde1f1b980b170dff71a0312c1e2459f99883766",
        crlf: 0,
        normalized_bytes: 6854,
        normalized_digest: "sha256:312b87d95e0eb39a28fad45adde1f1b980b170dff71a0312c1e2459f99883766",
    },
    RawGolden {
        blob: "0d43741be2346fe527fee17b0f330fc90f6e585d",
        bytes: 1227,
        digest: "sha256:78fc140a98bfe9e2e16d30d4a1694869a6518e0222a32c8e2f510efd2ba5d2bd",
        crlf: 0,
        normalized_bytes: 1227,
        normalized_digest: "sha256:78fc140a98bfe9e2e16d30d4a1694869a6518e0222a32c8e2f510efd2ba5d2bd",
    },
    RawGolden {
        blob: "0e354595ac56ab88ad232a4b08889fbcf1a8e2fd",
        bytes: 13257,
        digest: "sha256:cfb0ddedcadae58799ac9f6e767533f7d536b5396cc1e542d555b4e1d9f3b63b",
        crlf: 246,
        normalized_bytes: 13011,
        normalized_digest: "sha256:66e4d9db4147452d87e7cdd2d7208d1e2571cf6cc8d9c21a34146cf687259b8e",
    },
    RawGolden {
        blob: "1a96ac210229743d775e68646d8ae249bbdd9abe",
        bytes: 5162,
        digest: "sha256:c0fbbe55f7664113c58e70cdf39e344bbb72e66dea99bc0fb44e151c321dbee3",
        crlf: 90,
        normalized_bytes: 5072,
        normalized_digest: "sha256:0921c5eb5cbac23f7f3bb7565d9ad5f0cb9597ed8c29fa30129b0184a2985022",
    },
    RawGolden {
        blob: "2a4b7648a8ef54e618a189bab9bad6a40d3ef0ed",
        bytes: 3300,
        digest: "sha256:d99de767b40adb9baeb10974ada044446434fbac0db89b8bbdb6399038d7248c",
        crlf: 0,
        normalized_bytes: 3300,
        normalized_digest: "sha256:d99de767b40adb9baeb10974ada044446434fbac0db89b8bbdb6399038d7248c",
    },
    RawGolden {
        blob: "2de7e070c6888a33a04962e0705841dbe80ca366",
        bytes: 13249,
        digest: "sha256:f7cccf2c06e3ef1103df83a0aae3f616ff3921cca90e1f5be6ee2ac7f88b37d2",
        crlf: 246,
        normalized_bytes: 13003,
        normalized_digest: "sha256:10ea5e791c1294cfa10c8e5f18a180cfb4f7ed95eef8b0429db4c354dc31ce60",
    },
    RawGolden {
        blob: "3330077f84ed9f0bbbc966869569316f5df3181c",
        bytes: 1074,
        digest: "sha256:0aeb08ebbcfa8bb0391b60ecf776e07ad33ffb7c97af325b01f42c6a4e4fcd2f",
        crlf: 33,
        normalized_bytes: 1041,
        normalized_digest: "sha256:e0bc6aee2964cbf959171c058f5858e51b1145d599ddf1daf2ad1f275e9d9482",
    },
    RawGolden {
        blob: "34c0fc3dd74d2ee5e4e243a387c40ed25709c37f",
        bytes: 2827,
        digest: "sha256:85be0018cc4e93647f6fab6b7f1d8b0c51c87aead0bbb7c6098bc2ed4696a655",
        crlf: 0,
        normalized_bytes: 2827,
        normalized_digest: "sha256:85be0018cc4e93647f6fab6b7f1d8b0c51c87aead0bbb7c6098bc2ed4696a655",
    },
    RawGolden {
        blob: "3c2c0cffeee5adbd62bf64b85bd7c88cb185b74b",
        bytes: 1104,
        digest: "sha256:98331ba26ea952e82158c6598902bc3428a069c0c8e309bdbd8882b7e0d3062c",
        crlf: 34,
        normalized_bytes: 1070,
        normalized_digest: "sha256:794f8bbc76216e97e1ebda997302b4fe4cde5a8ff23af34b0b49c2d89779d3de",
    },
    RawGolden {
        blob: "5330b8161278a7df58018de70abca16477da5442",
        bytes: 2150,
        digest: "sha256:549b21b98f9fd6170bab0451a0551bcdd689eac394c33576fdd438b0126a83fa",
        crlf: 51,
        normalized_bytes: 2099,
        normalized_digest: "sha256:e08c83601b75a962cad425cd509f0526a556cb9fc66dfdad86d985a4c8a00b10",
    },
    RawGolden {
        blob: "599794f144508fb0f60a1aa60f98a86cbf34a1a3",
        bytes: 6149,
        digest: "sha256:6912abee7b8b89094f68db7a6363af74bd4aa2037c4502848e2350a3a1e8c098",
        crlf: 0,
        normalized_bytes: 6149,
        normalized_digest: "sha256:6912abee7b8b89094f68db7a6363af74bd4aa2037c4502848e2350a3a1e8c098",
    },
    RawGolden {
        blob: "66a0e92daf9265ff8ca4cb8cb0c10d231d970b13",
        bytes: 11927,
        digest: "sha256:55f61993b0c08014de312653ee5534d7b12d779dc5b52e24b9c884efdb33bb20",
        crlf: 0,
        normalized_bytes: 11927,
        normalized_digest: "sha256:55f61993b0c08014de312653ee5534d7b12d779dc5b52e24b9c884efdb33bb20",
    },
    RawGolden {
        blob: "757b12b044b02dbb7c86fda310b407d642441e94",
        bytes: 3002,
        digest: "sha256:1b2e193791db5f0946d93ea09425de6f817d57b7e88c1ef8f1a353d0ee4083a4",
        crlf: 111,
        normalized_bytes: 2891,
        normalized_digest: "sha256:f3869a3758341371426a86b571540141de5e15e04a6902f83baf38e0beac45b3",
    },
    RawGolden {
        blob: "7899a651f0ad7067e44dcff484af1774d889e56c",
        bytes: 2998,
        digest: "sha256:5979b4e3ce1d071b7ed66d8b3946423861e68fb6a59af0031549de4a78a6d2ed",
        crlf: 0,
        normalized_bytes: 2998,
        normalized_digest: "sha256:5979b4e3ce1d071b7ed66d8b3946423861e68fb6a59af0031549de4a78a6d2ed",
    },
    RawGolden {
        blob: "7c8368e275e8dc843166f856930ae3b40e458009",
        bytes: 1264,
        digest: "sha256:bc6db624bac36c641fc42f3c39c050a7458506a7e095591dd4be7c862842160c",
        crlf: 37,
        normalized_bytes: 1227,
        normalized_digest: "sha256:78fc140a98bfe9e2e16d30d4a1694869a6518e0222a32c8e2f510efd2ba5d2bd",
    },
    RawGolden {
        blob: "8067614a218219194af6d6a5d1a27b485b37c0c1",
        bytes: 7401,
        digest: "sha256:6614ee90655af2b35660dcd9de1a4ef484d65d2652cd45dabe1e7251131ab79a",
        crlf: 0,
        normalized_bytes: 7401,
        normalized_digest: "sha256:6614ee90655af2b35660dcd9de1a4ef484d65d2652cd45dabe1e7251131ab79a",
    },
    RawGolden {
        blob: "90e87b30871c56e34427ecaa820ca17ab6ee2182",
        bytes: 4879,
        digest: "sha256:a2dfd1411ba0467ede083604c21ae02ec1ecdcb4d752a8cdf7e3aa358917e001",
        crlf: 0,
        normalized_bytes: 4879,
        normalized_digest: "sha256:a2dfd1411ba0467ede083604c21ae02ec1ecdcb4d752a8cdf7e3aa358917e001",
    },
    RawGolden {
        blob: "919dd44aa346a40e43ca81b469c8a2cb8e9f3c92",
        bytes: 13246,
        digest: "sha256:cc9e1b36a8afa4660137061b669edfcf8e32d0357c36d3720a00d43fb9d97660",
        crlf: 246,
        normalized_bytes: 13000,
        normalized_digest: "sha256:b07a143d996d5d9a77095a3741f1b464901b3f1bf83daccd258f81102b9c01e2",
    },
    RawGolden {
        blob: "9c2e9aa326e6f0720e3d052043933eee78ada3c1",
        bytes: 1070,
        digest: "sha256:794f8bbc76216e97e1ebda997302b4fe4cde5a8ff23af34b0b49c2d89779d3de",
        crlf: 0,
        normalized_bytes: 1070,
        normalized_digest: "sha256:794f8bbc76216e97e1ebda997302b4fe4cde5a8ff23af34b0b49c2d89779d3de",
    },
    RawGolden {
        blob: "ad47bf67803f0c6c9204b88cf253eb0a55b6bf86",
        bytes: 1041,
        digest: "sha256:e0bc6aee2964cbf959171c058f5858e51b1145d599ddf1daf2ad1f275e9d9482",
        crlf: 0,
        normalized_bytes: 1041,
        normalized_digest: "sha256:e0bc6aee2964cbf959171c058f5858e51b1145d599ddf1daf2ad1f275e9d9482",
    },
    RawGolden {
        blob: "af15b44daf70bd82c608604ab3cab61cc70629e8",
        bytes: 5293,
        digest: "sha256:e83e0a4f5ff353697ecc45889e2735cb6933388af72129e7e0cc77421db36951",
        crlf: 0,
        normalized_bytes: 5293,
        normalized_digest: "sha256:e83e0a4f5ff353697ecc45889e2735cb6933388af72129e7e0cc77421db36951",
    },
    RawGolden {
        blob: "afaf1a19fab4f0713745e9891f25c27a30ece459",
        bytes: 4510,
        digest: "sha256:bf7b6dd5d0adff90b7bb4c4d72bfa80b8ab9e87deb63ba346941dfe300dd4327",
        crlf: 0,
        normalized_bytes: 4510,
        normalized_digest: "sha256:bf7b6dd5d0adff90b7bb4c4d72bfa80b8ab9e87deb63ba346941dfe300dd4327",
    },
    RawGolden {
        blob: "b95b408654391ec331f652008e928172300dae1e",
        bytes: 11966,
        digest: "sha256:e11b0cd026b5f459ff7687a76a01d4a5bb219e029eac1e92af3da3ca4c94feba",
        crlf: 301,
        normalized_bytes: 11665,
        normalized_digest: "sha256:8116ba122f3f9379b00a1bd960d8209b92786fddecd6c7f2acf8e80e8fcfe37e",
    },
    RawGolden {
        blob: "c6ec0f57b6dfdf9d572ba6bf890378519fff4433",
        bytes: 11920,
        digest: "sha256:e31efa70eb400d00e5b33774d3a90bae63b0548181ecedcc35b0a1a76eea41ab",
        crlf: 301,
        normalized_bytes: 11619,
        normalized_digest: "sha256:dadca477e639613d3e357643e3aa6271cce16c8ed64e781eca3eb2b6f816733a",
    },
    RawGolden {
        blob: "ca2adde34af722f19ec90fa80d7c63726a852f4b",
        bytes: 12007,
        digest: "sha256:809f96613e2568f4e0bdfeb8704c735d2e0cb7d8fd0ef680f10c674fae19ed2b",
        crlf: 305,
        normalized_bytes: 11702,
        normalized_digest: "sha256:25ef71c4acd9c264946096543ed3fe860361c54f9d94da90b6988913131d18db",
    },
    RawGolden {
        blob: "d44c10c98b7d088aecd2f890219ececc4f6d7475",
        bytes: 2146,
        digest: "sha256:001551ff8cb42cfedf5a995bf5055adad79e063610c97fb756a63a53135ee2e9",
        crlf: 35,
        normalized_bytes: 2111,
        normalized_digest: "sha256:598a8f344fa8bb3fea4efdc892c7b5981b874bb658af7ecdb4d0a25b2b9b668d",
    },
    RawGolden {
        blob: "f9ef4b17fb2e96a0d1478a5e7a6941e2898822f4",
        bytes: 11931,
        digest: "sha256:07e15f70cfafddf3e601946963d78f70afc142f51501613cb0c445d934ed87f8",
        crlf: 301,
        normalized_bytes: 11630,
        normalized_digest: "sha256:ca084ef26597fc09dab13e7a8965d637c36852365ac5b2686f2e67af396487d7",
    },
];
struct FontOwnedGolden {
    target: &'static str,
    formatted: &'static str,
    batches: &'static [&'static str],
}
const FONT_OWNED: [FontOwnedGolden; 10] = [
    FontOwnedGolden {
        target: "1.19.2",
        formatted: r###"
    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            PoseStack context,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        if (shadow) {
            font.drawShadow(context, text, x, y, colour);
        } else {
            font.draw(context, text, x, y, colour);
        }
    }
"###,
        batches: &[
            r###"
    /**
     * Draws coloured component text into a caller-owned batch. The caller is
     * responsible for flushing the buffer after all related text is queued.
     */
    @MCVersionDependentBehaviour
    public static int drawInBatch(
            Component text,
            Font font,
            float x,
            float y,
            int colour,
            boolean dropShadow,
            boolean transparent,
            Matrix4f matrix4f,
            MultiBufferSource bufferSource
    ) {
        return font.drawInBatch(
                text,
                x,
                y,
                colour,
                dropShadow,
                matrix4f,
                bufferSource,
                transparent,
                0,
                LightTexture.FULL_BRIGHT
        );
    }
"###,
            r###"
    /**
     * Draws coloured plain text into a caller-owned batch. The caller is
     * responsible for flushing the buffer after all related text is queued.
     */
    @MCVersionDependentBehaviour
    public static int drawInBatch(
            String text,
            Font font,
            float x,
            float y,
            int colour,
            boolean dropShadow,
            boolean transparent,
            Matrix4f matrix4f,
            MultiBufferSource bufferSource
    ) {
        return font.drawInBatch(
                text,
                x,
                y,
                colour,
                dropShadow,
                matrix4f,
                bufferSource,
                transparent,
                0,
                LightTexture.FULL_BRIGHT
        );
    }
"###,
        ],
    },
    FontOwnedGolden {
        target: "1.19.4",
        formatted: r###"
    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            PoseStack context,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        if (shadow) {
            font.drawShadow(context, text, x, y, colour);
        } else {
            font.draw(context, text, x, y, colour);
        }
    }
"###,
        batches: &[
            r###"
    /**
     * Draws coloured component text into a caller-owned batch. The caller is
     * responsible for flushing the buffer after all related text is queued.
     */
    @MCVersionDependentBehaviour
    public static int drawInBatch(
            Component text,
            Font font,
            float x,
            float y,
            int colour,
            boolean dropShadow,
            boolean transparent,
            Matrix4f matrix4f,
            MultiBufferSource bufferSource
    ) {
        return font.drawInBatch(
                text,
                x,
                y,
                colour,
                dropShadow,
                matrix4f,
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT
        );
    }
"###,
            r###"
    /**
     * Draws coloured plain text into a caller-owned batch. The caller is
     * responsible for flushing the buffer after all related text is queued.
     */
    @MCVersionDependentBehaviour
    public static int drawInBatch(
            String text,
            Font font,
            float x,
            float y,
            int colour,
            boolean dropShadow,
            boolean transparent,
            Matrix4f matrix4f,
            MultiBufferSource bufferSource
    ) {
        return font.drawInBatch(
                text,
                x,
                y,
                colour,
                dropShadow,
                matrix4f,
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT
        );
    }
"###,
        ],
    },
    FontOwnedGolden {
        target: "1.20",
        formatted: r###"
    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.drawString(font, text, x, y, colour, shadow);
    }
"###,
        batches: &[],
    },
    FontOwnedGolden {
        target: "1.20.1",
        formatted: r###"
    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.drawString(font, text, x, y, colour, shadow);
    }
"###,
        batches: &[],
    },
    FontOwnedGolden {
        target: "1.20.2",
        formatted: r###"
    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.drawString(font, text, x, y, colour, shadow);
    }
"###,
        batches: &[],
    },
    FontOwnedGolden {
        target: "1.20.3",
        formatted: r###"
    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.drawString(font, text, x, y, colour, shadow);
    }
"###,
        batches: &[],
    },
    FontOwnedGolden {
        target: "1.20.4",
        formatted: r###"
    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.drawString(font, text, x, y, colour, shadow);
    }
"###,
        batches: &[],
    },
    FontOwnedGolden {
        target: "1.21.0",
        formatted: r###"
    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.drawString(font, text, x, y, colour, shadow);
    }
"###,
        batches: &[],
    },
    FontOwnedGolden {
        target: "1.21.1",
        formatted: r###"
    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.drawString(font, text, x, y, colour, shadow);
    }
"###,
        batches: &[],
    },
    FontOwnedGolden {
        target: "26.1.2",
        formatted: r###"
    @MCVersionDependentBehaviour
    public static void draw(
            GuiGraphicsExtractor graphics,
            Font font,
            net.minecraft.util.FormattedCharSequence text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.text(font, text, x, y, normalizeLegacyRgb(colour), shadow);
    }

"###,
        batches: &[],
    },
];
struct OwnerGolden {
    name: &'static str,
    support: &'static [&'static str],
    requires: &'static [&'static str],
}
const OWNERS: [OwnerGolden; 9] = [
    OwnerGolden {
        name: "font_formatted_text",
        support: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        requires: &[],
    },
    OwnerGolden {
        name: "font_caller_owned_batches",
        support: &["1.19.2", "1.19.4"],
        requires: &[],
    },
    OwnerGolden {
        name: "screen_fractional_highlights",
        support: &["1.19.2"],
        requires: &[],
    },
    OwnerGolden {
        name: "canvas_text_editor",
        support: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        requires: &[],
    },
    OwnerGolden {
        name: "client_theme",
        support: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        requires: &[],
    },
    OwnerGolden {
        name: "packet_computation",
        support: &["1.19.2", "1.19.4"],
        requires: &["packet_values", "runtime_resource_cleanup"],
    },
    OwnerGolden {
        name: "packet_transport_private",
        support: &["1.19.2", "1.19.4"],
        requires: &["packet_computation"],
    },
    OwnerGolden {
        name: "client_frame_language",
        support: &["1.19.2", "1.19.4"],
        requires: &[
            "client_manager",
            "sfml_execution_side",
            "client_program_consent",
        ],
    },
    OwnerGolden {
        name: "client_program_actions",
        support: &["1.19.2", "1.19.4"],
        requires: &[
            "client_actions",
            "packet_values",
            "sfml_execution_side",
            "client_manager",
            "client_program_consent",
            "packet_computation",
        ],
    },
];
const KEYWORDS: [(&str, &str); 16] = [
    ("LET", "packet_computation"),
    ("BE", "packet_computation"),
    ("PLAYER", "packet_computation"),
    ("LIKE", "packet_computation"),
    ("OBJECT", "packet_computation"),
    ("INVOKE", "packet_computation"),
    ("JSON", "client_program_actions"),
    ("CREATE", "packet_computation"),
    ("BROADCAST", "packet_transport_private"),
    ("NEW", "packet_computation"),
    ("OF", "packet_computation"),
    ("FIELD", "packet_computation"),
    ("GUID", "packet_computation"),
    ("STRING_TYPE", "packet_computation"),
    ("CAPABILITY", "packet_computation"),
    ("AS", "packet_computation_or_client_frame_language"),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    owners: BTreeMap<String, Owner>,
    lexical_case_owners: BTreeMap<String, String>,
    normalization: Normalization,
    files: Vec<FileEvidence>,
    raw_witnesses: Vec<RawEvidence>,
}
#[derive(Facet)]
struct Owner {
    support: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    raw_byte_exact: bool,
    approved_changes: Vec<String>,
    lf_added: bool,
}
#[derive(Facet)]
struct FileEvidence {
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
    functional_owners: Vec<String>,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    present: bool,
    git_blob: String,
    raw_bytes: u64,
    raw_sha256: String,
    normalized_bytes: u64,
    normalized_sha256: String,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
    lf_added: bool,
    normalization: String,
    historical_owners: Vec<String>,
}
#[derive(Facet)]
struct RawEvidence {
    path: String,
    git_blob: String,
    raw_bytes: u64,
    raw_sha256: String,
    normalized_bytes: u64,
    normalized_sha256: String,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
    lf_added: bool,
    normalization: String,
    contexts: Vec<String>,
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
        let ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)
            .wrap_err("cannot parse bounded UI-helper ledger")?;
        let raw = read_git_blobs(
            &shared.repository,
            &RAW.iter().map(|r| r.blob.to_owned()).collect(),
        )?;
        let sources = FILES
            .iter()
            .map(|f| shared.read_source(f.path))
            .collect::<Result<Vec<_>>>()?;
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
        let commits = CONTEXTS
            .iter()
            .map(|r| (r.name.to_owned(), r.commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.schema == "sfm:core-ui-helpers-slice@1"
                && self.ledger.context_commits == commits,
            "frozen context identity changed"
        );
        ensure!(
            self.ledger.owners.len() == OWNERS.len(),
            "UI-helper owner scope changed"
        );
        for golden in &OWNERS {
            let proposed = self
                .ledger
                .owners
                .get(golden.name)
                .ok_or_else(|| eyre::eyre!("missing UI-helper owner"))?;
            let actual = self
                .shared
                .features
                .0
                .get(golden.name)
                .ok_or_else(|| eyre::eyre!("UI-helper owner not registered"))?;
            ensure!(
                proposed
                    .support
                    .iter()
                    .map(String::as_str)
                    .collect::<Vec<_>>()
                    == golden.support
                    && proposed
                        .requires
                        .iter()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>()
                        == golden.requires.iter().copied().collect()
                    && proposed.support == actual.supported_targets
                    && proposed.requires.iter().collect::<BTreeSet<_>>()
                        == actual.requires.iter().collect(),
                "UI-helper support/prerequisite contract changed"
            );
        }
        let approved = RAW
            .iter()
            .filter(|r| r.crlf != 0)
            .map(|r| r.blob)
            .collect::<BTreeSet<_>>();
        ensure!(
            approved.len() == 14
                && self.ledger.normalization.policy == "sfm:java_crlf_to_lf@1"
                && !self.ledger.normalization.raw_byte_exact
                && !self.ledger.normalization.lf_added
                && self.ledger.normalization.approved_changes.len() == approved.len()
                && self
                    .ledger
                    .normalization
                    .approved_changes
                    .iter()
                    .map(String::as_str)
                    .collect::<BTreeSet<_>>()
                    == approved,
            "normalization scope changed"
        );
        ensure!(
            self.ledger.lexical_case_owners
                == KEYWORDS
                    .iter()
                    .map(|(token, owner)| ((*token).to_owned(), (*owner).to_owned()))
                    .collect(),
            "keyword ownership changed"
        );
        for raw in &RAW {
            self.normalized(raw.blob)?;
        }
        ensure!(
            self.ledger.files.len() == FILES.len(),
            "UI-helper file scope changed"
        );
        for (index, (file, golden)) in self.ledger.files.iter().zip(&FILES).enumerate() {
            let source = self
                .sources
                .get(index)
                .ok_or_else(|| eyre::eyre!("missing source"))?;
            ensure!(
                file.intended_core_path == golden.path
                    && file.stage_bytes == golden.bytes
                    && file.stage_sha256 == golden.digest
                    && source.len() as u64 == golden.bytes
                    && sha256(source) == golden.digest
                    && !source.contains(&b'\r')
                    && source.ends_with(b"\n")
                    && !source.starts_with(&[0xef, 0xbb, 0xbf]),
                "promoted template identity changed"
            );
            ensure!(
                file.functional_owners
                    .iter()
                    .map(String::as_str)
                    .collect::<Vec<_>>()
                    == file_owners(index)?,
                "member ownership changed"
            );
            ensure!(file.witnesses.len() == 20, "witness coverage changed");
            let mut seen = BTreeSet::new();
            for witness in &file.witnesses {
                ensure!(seen.insert(witness.context.as_str()), "duplicate witness");
                let row = context_golden(&witness.context)?;
                let oid = row
                    .blobs
                    .get(index)
                    .ok_or_else(|| eyre::eyre!("invalid witness index"))?;
                let raw = raw_golden(oid)?;
                validate_witness(witness, raw)?;
                ensure!(
                    witness
                        .historical_owners
                        .iter()
                        .map(String::as_str)
                        .collect::<Vec<_>>()
                        == historical_flags(row.name)?,
                    "original source-owner witness changed"
                );
            }
            ensure!(
                seen == commits.keys().map(String::as_str).collect(),
                "context coverage changed"
            );
        }
        ensure!(
            self.ledger.raw_witnesses.len() == RAW.len(),
            "raw variant scope changed"
        );
        let mut seen = BTreeSet::new();
        for witness in &self.ledger.raw_witnesses {
            ensure!(
                seen.insert(witness.git_blob.as_str()),
                "duplicate raw identity"
            );
            let raw = raw_golden(&witness.git_blob)?;
            let index = CONTEXTS
                .iter()
                .find_map(|c| c.blobs.iter().position(|oid| *oid == raw.blob))
                .ok_or_else(|| eyre::eyre!("non-member raw identity"))?;
            let contexts = CONTEXTS
                .iter()
                .filter(|c| c.blobs[index] == raw.blob)
                .map(|c| c.name)
                .collect::<BTreeSet<_>>();
            ensure!(
                witness.path == FILES[index].path
                    && witness.raw_bytes == raw.bytes
                    && witness.raw_sha256 == raw.digest
                    && witness.normalized_bytes == raw.normalized_bytes
                    && witness.normalized_sha256 == raw.normalized_digest
                    && witness.crlf_count == raw.crlf
                    && witness.lone_cr_count == 0
                    && witness.final_lf
                    && !witness.bom
                    && !witness.lf_added
                    && witness.normalization == normalization_name(raw)
                    && witness.contexts.len() == contexts.len()
                    && witness
                        .contexts
                        .iter()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>()
                        == contexts,
                "raw/normalized evidence changed"
            );
        }
        Ok(())
    }
    fn normalized(&self, oid: &str) -> Result<String> {
        normalize_raw(
            raw_golden(oid)?,
            self.raw
                .get(oid)
                .ok_or_else(|| eyre::eyre!("missing raw oracle"))?,
        )
    }
    fn historical_body(&self, kind: &str, target: &str, index: usize) -> Result<String> {
        let name = format!("{kind}/{target}");
        let row = context_golden(&name)?;
        self.normalized(
            row.blobs
                .get(index)
                .ok_or_else(|| eyre::eyre!("invalid raw index"))?,
        )
    }
    fn selection(&self, context: &ProjectionContext) -> Result<()> {
        let selection = self.shared.selection_for_assertion(context, &inventory())?;
        for file in &FILES {
            let input = selection
                .inputs
                .get(file.path)
                .ok_or_else(|| eyre::eyre!("baseline UI helper omitted"))?;
            ensure!(
                input.input == file.path
                    && (input.template || file.path.ends_with(".java"))
                    && !selection.omitted_paths.contains(file.path),
                "UI helper routed away from common core"
            );
        }
        Ok(())
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<String> {
        self.selection(context)?;
        render_java_source(
            std::str::from_utf8(
                self.sources
                    .get(index)
                    .ok_or_else(|| eyre::eyre!("invalid source index"))?,
            )?,
            context,
        )
    }
    // Raw member witnesses, not generated templates, define the independent oracle.
    fn oracle(&self, index: usize, target: &str, flags: &[&str]) -> Result<String> {
        match index {
            0 => {
                let members = FONT_OWNED
                    .iter()
                    .find(|m| m.target == target)
                    .ok_or_else(|| eyre::eyre!("missing font member golden"))?;
                let mut body = self.historical_body("dev", target, 0)?;
                if !flags.contains(&FLAGS[0]) {
                    body = replace_once(&body, members.formatted, "")?;
                    if target != "26.1.2" {
                        body = replace_once(
                            &body,
                            "import net.minecraft.util.FormattedCharSequence;\n",
                            "",
                        )?;
                    }
                }
                if !flags.contains(&FLAGS[1]) {
                    for member in members.batches {
                        body = replace_once(&body, member, "")?;
                    }
                }
                Ok(body)
            }
            1 => self.historical_body(
                if flags.contains(&FLAGS[2]) {
                    "dev"
                } else {
                    "release"
                },
                target,
                index,
            ),
            2 => self.historical_body(
                if flags.contains(&FLAGS[3]) {
                    "dev"
                } else {
                    "release"
                },
                target,
                index,
            ),
            3 => {
                if !flags.contains(&FLAGS[4]) {
                    return self.normalized("599794f144508fb0f60a1aa60f98a86cbf34a1a3");
                }
                let mut body = self.normalized("8067614a218219194af6d6a5d1a27b485b37c0c1")?;
                for (token, owner) in &KEYWORDS {
                    if !keyword_enabled(owner, flags) {
                        body = replace_once(
                            &body,
                            &format!("            case SFMLLexer.{token}:\n"),
                            "",
                        )?;
                    }
                }
                Ok(body)
            }
            _ => eyre::bail!("invalid UI-helper oracle index"),
        }
    }
}
fn inventory() -> BTreeSet<String> {
    FILES.iter().map(|f| f.path.to_owned()).collect()
}
fn file_owners(index: usize) -> Result<&'static [&'static str]> {
    match index {
        0 => Ok(&["font_formatted_text", "font_caller_owned_batches"]),
        1 => Ok(&["screen_fractional_highlights"]),
        2 => Ok(&["canvas_text_editor"]),
        3 => Ok(&[
            "client_theme",
            "packet_computation",
            "packet_transport_private",
            "client_frame_language",
            "client_program_actions",
        ]),
        _ => eyre::bail!("invalid owner index"),
    }
}
fn context_golden(name: &str) -> Result<&'static ContextGolden> {
    CONTEXTS
        .iter()
        .find(|r| r.name == name)
        .ok_or_else(|| eyre::eyre!("unknown source context"))
}
fn raw_golden(oid: &str) -> Result<&'static RawGolden> {
    RAW.iter()
        .find(|r| r.blob == oid)
        .ok_or_else(|| eyre::eyre!("unapproved raw identity"))
}
fn normalization_name(raw: &RawGolden) -> &'static str {
    if raw.crlf == 0 {
        "none"
    } else {
        "sfm:java_crlf_to_lf@1"
    }
}
fn validate_witness(w: &Witness, raw: &RawGolden) -> Result<()> {
    ensure!(
        w.present
            && w.git_blob == raw.blob
            && w.raw_bytes == raw.bytes
            && w.raw_sha256 == raw.digest
            && w.normalized_bytes == raw.normalized_bytes
            && w.normalized_sha256 == raw.normalized_digest
            && w.crlf_count == raw.crlf
            && w.lone_cr_count == 0
            && w.final_lf
            && !w.bom
            && !w.lf_added
            && w.normalization == normalization_name(raw),
        "per-context raw/normalized identity changed"
    );
    Ok(())
}
fn normalize_raw(golden: &RawGolden, bytes: &[u8]) -> Result<String> {
    ensure!(
        bytes.len() as u64 == golden.bytes
            && sha256(bytes) == golden.digest
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "unapproved raw bytes"
    );
    let raw = std::str::from_utf8(bytes)?;
    ensure!(
        raw.matches("\r\n").count() == golden.crlf && !raw.replace("\r\n", "").contains('\r'),
        "raw EOL witness changed"
    );
    let normalized = raw.replace("\r\n", "\n");
    ensure!(
        normalized.len() as u64 == golden.normalized_bytes
            && sha256(normalized.as_bytes()) == golden.normalized_digest
            && normalized.ends_with('\n'),
        "normalized identity changed"
    );
    Ok(normalized)
}
fn replace_once(text: &str, old: &str, new: &str) -> Result<String> {
    ensure!(
        !old.is_empty() && text.matches(old).count() == 1,
        "frozen member anchor changed"
    );
    Ok(text.replacen(old, new, 1))
}
fn keyword_enabled(owner: &str, flags: &[&str]) -> bool {
    if owner == "packet_computation_or_client_frame_language" {
        flags.contains(&"packet_computation") || flags.contains(&"client_frame_language")
    } else {
        flags.contains(&owner)
    }
}
fn supported_mask(target: &str, mask: u8) -> bool {
    let d2 = matches!(target, "1.19.2" | "1.19.4");
    (d2 || mask & 2 == 0) && (target == "1.19.2" || mask & 4 == 0)
}
fn owner_flags(mask: u8, lexical: &[&'static str]) -> Vec<&'static str> {
    let mut flags = FLAGS
        .iter()
        .enumerate()
        .filter(|(i, _)| mask & (1 << i) != 0)
        .map(|(_, f)| *f)
        .collect::<Vec<_>>();
    flags.extend_from_slice(lexical);
    flags
}
fn historical_flags(name: &str) -> Result<Vec<&'static str>> {
    let (kind, target) = name
        .split_once('/')
        .ok_or_else(|| eyre::eyre!("invalid historical context"))?;
    if kind == "release" {
        return Ok(Vec::new());
    }
    ensure!(kind == "dev", "unknown witness kind");
    let mut flags = vec![FLAGS[0]];
    if matches!(target, "1.19.2" | "1.19.4") {
        flags.push(FLAGS[1]);
    }
    if target == "1.19.2" {
        flags.push(FLAGS[2]);
    }
    flags.extend([FLAGS[3], FLAGS[4]]);
    if matches!(target, "1.19.2" | "1.19.4") {
        flags.extend([
            "packet_computation",
            "packet_transport_private",
            "client_frame_language",
            "client_program_actions",
        ]);
    }
    Ok(flags)
}
// Explicit, reviewed current closure. Historical owner lists above stay immutable.
// Do not auto-expand the production fixture graph or invent signing/wire families
// for this isolated source proof. The root-approved readonly edge must be installed.
fn required_context_features(flags: &[&'static str]) -> Vec<&'static str> {
    let mut context = flags.to_vec();
    if flags.contains(&"packet_computation") {
        context.extend(["packet_values", "runtime_resource_cleanup"]);
    }
    if flags.contains(&"client_frame_language") || flags.contains(&"client_program_actions") {
        context.extend([
            "client_manager",
            "sfml_execution_side",
            "client_program_consent",
            "disk_readonly_access",
        ]);
    }
    if flags.contains(&"client_program_actions") {
        context.push("client_actions");
    }
    context.sort_unstable();
    context.dedup();
    context
}
fn lexer_rule_names(grammar: &str) -> BTreeSet<&str> {
    let mut rules = BTreeSet::new();
    let mut pending = None;
    let valid = |name: &str| {
        !name.is_empty()
            && name
                .bytes()
                .all(|b| b.is_ascii_uppercase() || b == b'_' || b.is_ascii_digit())
    };
    for line in grammar.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with("//") {
            continue;
        }
        if let Some((prefix, _)) = line.split_once(':') {
            let prefix = prefix.trim();
            if valid(prefix) {
                rules.insert(prefix);
            } else if prefix.is_empty()
                && let Some(name) = pending.take()
            {
                rules.insert(name);
            }
            pending = None;
        } else {
            pending = valid(line).then_some(line);
        }
    }
    rules
}
fn switch_token_names(body: &str) -> BTreeSet<&str> {
    body.lines()
        .filter_map(|line| {
            line.trim()
                .strip_prefix("case SFMLLexer.")?
                .strip_suffix(':')
        })
        .collect()
}

#[test]
fn all_eighty_frozen_bodies_reconstruct_through_actual_core_renderer() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for row in &CONTEXTS {
        let (_, target) = row
            .name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let flags = historical_flags(row.name)?;
        let context = fixture
            .shared
            .context(target, &required_context_features(&flags))?;
        for (index, oid) in row.blobs.iter().enumerate() {
            assert_eq!(
                fixture.render(index, &context)?,
                fixture.normalized(oid)?,
                "{} {}",
                row.name,
                FILES[index].path
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 80);
    Ok(())
}

#[test]
fn independent_owner_masks_preserve_raw_member_oracles_and_do_not_enable_unrelated_runtimes()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut profiles = 0;
    let mut cells = 0;
    for target in TARGETS {
        let lexical_count = if matches!(target, "1.19.2" | "1.19.4") {
            LEXICAL.len()
        } else {
            1
        };
        for mask in 0_u8..32 {
            if !supported_mask(target, mask) {
                continue;
            }
            for lexical in LEXICAL.iter().take(lexical_count) {
                let flags = owner_flags(mask, lexical);
                let context = fixture
                    .shared
                    .context(target, &required_context_features(&flags))?;
                for index in 0..FILES.len() {
                    let body = fixture.render(index, &context)?;
                    assert_eq!(
                        body,
                        fixture.oracle(index, target, &flags)?,
                        "{target} mask {mask} {lexical:?} file {index}"
                    );
                    match index {
                        0 => {
                            let members = FONT_OWNED
                                .iter()
                                .find(|m| m.target == target)
                                .ok_or_else(|| eyre::eyre!("font golden missing"))?;
                            assert_eq!(body.contains(members.formatted), flags.contains(&FLAGS[0]));
                            for member in members.batches {
                                assert_eq!(body.contains(member), flags.contains(&FLAGS[1]));
                            }
                        }
                        1 => {
                            if target == "1.19.2" {
                                assert_eq!(
                                    body.contains("            double startX,"),
                                    flags.contains(&FLAGS[2])
                                );
                                assert_eq!(
                                    body.contains("            int startX,"),
                                    !flags.contains(&FLAGS[2])
                                );
                            }
                        }
                        2 => {
                            assert_eq!(
                                body.contains("preferredEditorV3Button"),
                                flags.contains(&FLAGS[3]) && matches!(target, "1.19.2" | "1.19.4")
                            );
                            assert_eq!(
                                body.contains("preferredEditorDrawButton"),
                                flags.contains(&FLAGS[3])
                                    && !matches!(target, "1.19.2" | "1.19.4" | "26.1.2")
                            );
                            assert!(body.contains("SFMEnvironmentUtils.isInIDE()"));
                        }
                        3 => {
                            assert_eq!(
                                body.contains("public record TokenHighlight("),
                                flags.contains(&FLAGS[4])
                            );
                            assert_eq!(
                                body.contains("import net.minecraft.ChatFormatting;"),
                                !flags.contains(&FLAGS[4])
                            );
                            for (token, owner) in &KEYWORDS {
                                assert_eq!(
                                    body.contains(&format!("case SFMLLexer.{token}:")),
                                    flags.contains(&FLAGS[4]) && keyword_enabled(owner, &flags),
                                    "{target} mask {mask} lexical {lexical:?} token {token}"
                                );
                            }
                            for retained_default in [
                                "CLIENT", "SERVER", "BTW", "OFFSET", "FRAME", "FOR", "MOD",
                                "RENDER", "IMAGE", "CHANNEL",
                            ] {
                                assert!(
                                    !body.contains(&format!("case SFMLLexer.{retained_default}:"))
                                );
                            }
                        }
                        _ => eyre::bail!("invalid source index"),
                    }
                    cells += 1;
                }
                for unrelated in [
                    "client_program_signing",
                    "multiplayer_packets",
                    "terminal_remote",
                    "terminal_vox",
                    "workspace_panels",
                    "touch_display",
                    "client_frame_render",
                ] {
                    assert_ne!(context.features.get(unrelated), Some(&true), "{unrelated}");
                }
                profiles += 1;
            }
        }
    }
    assert_eq!((profiles, cells), (352, 1408));
    Ok(())
}

#[test]
fn leaf_api_support_is_exact_and_canvas_is_not_globally_coupled_to_d2_batch_api() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let base = fixture.shared.context(target, &[])?;
        fixture.selection(&base)?;
        if target == "1.21.0" {
            assert_eq!(base.minecraft_version, "1.21");
        }
        fixture.shared.context(target, &[FLAGS[0]])?;
        fixture.shared.context(target, &[FLAGS[3]])?;
        fixture.shared.context(target, &[FLAGS[4]])?;
        assert!(
            fixture
                .shared
                .context(target, &["unreviewed_ui_helper_owner"])
                .is_err()
        );
        if matches!(target, "1.19.2" | "1.19.4") {
            let batch = fixture.shared.context(target, &[FLAGS[1]])?;
            assert_ne!(batch.features.get(FLAGS[3]), Some(&true));
            let canvas = fixture.shared.context(target, &[FLAGS[3]])?;
            assert_ne!(canvas.features.get(FLAGS[1]), Some(&true));
            // The D2 caller's closure is deliberately pending, not hidden in a
            // global all-target prerequisite or silently accepted project build.
            assert_eq!(fixture.render(0, &canvas)?, fixture.render(0, &base)?);
            assert_ne!(fixture.render(0, &batch)?, fixture.render(0, &base)?);
            assert!(
                fixture
                    .shared
                    .context(target, &["packet_computation"])
                    .is_err()
            );
            assert!(
                fixture
                    .shared
                    .context(target, &["packet_computation", "packet_values"])
                    .is_err()
            );
            fixture.shared.context(
                target,
                &[
                    "packet_computation",
                    "packet_values",
                    "runtime_resource_cleanup",
                ],
            )?;
            assert!(
                fixture
                    .shared
                    .context(target, &["client_frame_language"])
                    .is_err()
            );
            assert!(
                fixture
                    .shared
                    .context(target, &["client_program_actions"])
                    .is_err()
            );
        } else {
            assert!(fixture.shared.context(target, &[FLAGS[1]]).is_err());
            for lexical in [
                "packet_computation",
                "packet_transport_private",
                "client_frame_language",
                "client_program_actions",
            ] {
                assert!(
                    fixture.shared.context(target, &[lexical]).is_err(),
                    "{target} {lexical}"
                );
            }
        }
        if target == "1.19.2" {
            let fractional = fixture.shared.context(target, &[FLAGS[2]])?;
            for unrelated in [FLAGS[0], FLAGS[1], FLAGS[3], FLAGS[4], "terminal_remote"] {
                assert_ne!(fractional.features.get(unrelated), Some(&true));
            }
        } else {
            assert!(fixture.shared.context(target, &[FLAGS[2]]).is_err());
        }
    }
    Ok(())
}

#[test]
fn actual_rendered_grammar_contains_every_selected_highlighter_case_token() -> Result<()> {
    let fixture = Fixture::load()?;
    let grammar_source = read_bounded(
        &super::candidate_lock::checked_file(&fixture.shared.core, GRAMMAR)?,
        LIMIT,
    )?;
    let mut inventory = inventory();
    inventory.insert(GRAMMAR.to_owned());
    let mut profiles = 0;
    for target in TARGETS {
        let lexical_count = if matches!(target, "1.19.2" | "1.19.4") {
            LEXICAL.len()
        } else {
            1
        };
        for lexical in LEXICAL.iter().take(lexical_count) {
            for themed in [false, true] {
                let mut flags = lexical.to_vec();
                if themed {
                    flags.push(FLAGS[4]);
                }
                let context = fixture
                    .shared
                    .context(target, &required_context_features(&flags))?;
                let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory)?;
                let selected = selection
                    .inputs
                    .get(GRAMMAR)
                    .ok_or_else(|| eyre::eyre!("grammar omitted"))?;
                assert_eq!(selected.input, GRAMMAR);
                assert!(selected.template);
                let grammar = render_java_source(std::str::from_utf8(&grammar_source)?, &context)?;
                let rules = lexer_rule_names(&grammar);
                let body = fixture.render(3, &context)?;
                let references = switch_token_names(&body);
                assert!(!grammar.contains("{%") && !body.contains("{%"));
                for token in &references {
                    assert!(
                        rules.contains(token),
                        "dangling token {token} for {target} {flags:?}"
                    );
                }
                for (token, owner) in &KEYWORDS {
                    assert_eq!(
                        rules.contains(token),
                        keyword_enabled(owner, &flags),
                        "{target} {token}"
                    );
                    assert_eq!(
                        references.contains(token),
                        themed && keyword_enabled(owner, &flags),
                        "{target} {token}"
                    );
                }
                profiles += 1;
            }
        }
    }
    assert_eq!(profiles, 40);
    // This is source/reference closure, not an ANTLR generation or Java compile.
    Ok(())
}

#[test]
fn only_fourteen_reviewed_crlf_identities_normalize_and_mutations_fail_closed() -> Result<()> {
    let fixture = Fixture::load()?;
    assert!(raw_golden("0000000000000000000000000000000000000000").is_err());
    let mut approved = 0;
    let mut exact = 0;
    for golden in &RAW {
        let raw = fixture
            .raw
            .get(golden.blob)
            .ok_or_else(|| eyre::eyre!("raw missing"))?;
        let normalized = normalize_raw(golden, raw)?;
        if golden.crlf == 0 {
            assert_eq!(normalized.as_bytes(), raw);
            exact += 1;
        } else {
            assert_ne!(normalized.as_bytes(), raw);
            assert_eq!(raw.len() - normalized.len(), golden.crlf);
            approved += 1;
        }
        let text = std::str::from_utf8(raw)?;
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(raw);
        let mut appended = raw.clone();
        appended.push(b'\n');
        let mut mutations = vec![
            bom,
            appended,
            raw[..raw.len() - 1].to_vec(),
            text.replacen("public ", "private ", 1).into_bytes(),
            text.replacen("    ", "\t", 1).into_bytes(),
        ];
        if golden.crlf != 0 {
            mutations.push(text.replace("\r\n", "\n").into_bytes());
            mutations.push(text.replacen("\r\n", "\r", 1).into_bytes());
        } else {
            mutations.push(text.replacen('\n', "\r\n", 1).into_bytes());
        }
        for mutation in mutations {
            assert_ne!(
                &mutation, raw,
                "mutation fixture did not change {} bytes",
                golden.blob
            );
            assert!(
                normalize_raw(golden, &mutation).is_err(),
                "raw mutation accepted: {}",
                golden.blob
            );
        }
    }
    assert_eq!((approved, exact), (14, 13));
    Ok(())
}

#[test]
fn common_edits_reach_all_ten_targets_in_isolated_fixtures_without_changing_guards_or_inputs()
-> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(super::core_inputs::CORE_ROOT);
    let mut cells = 0;
    for (index, file) in FILES.iter().enumerate() {
        let original = fixture
            .sources
            .get(index)
            .ok_or_else(|| eyre::eyre!("source missing"))?;
        let source = std::str::from_utf8(original)?;
        let anchor = source
            .lines()
            .next()
            .ok_or_else(|| eyre::eyre!("empty template"))?;
        let old = format!("{anchor}\n");
        let new = format!("{anchor}\n// UI-helper common-edit proof.\n");
        let edited = replace_once(source, &old, &new)?;
        let guards = |s: &str| -> Vec<String> {
            s.lines()
                .filter(|line| line.trim_start().starts_with("{%"))
                .map(str::to_owned)
                .collect()
        };
        assert_eq!(guards(source), guards(&edited));
        let path = root.join(file.path);
        fs::create_dir_all(path.parent().ok_or_else(|| eyre::eyre!("missing parent"))?)?;
        fs::write(&path, edited.as_bytes())?;
        for target in TARGETS {
            for kind in ["release", "dev"] {
                let name = format!("{kind}/{target}");
                let flags = historical_flags(&name)?;
                let context = fixture
                    .shared
                    .context(target, &required_context_features(&flags))?;
                fixture.selection(&context)?;
                let bytes = read_bounded(&path, LIMIT)?;
                assert_eq!(std::str::from_utf8(&bytes)?, edited);
                assert_eq!(
                    render_java_source(std::str::from_utf8(&bytes)?, &context)?,
                    replace_once(&fixture.render(index, &context)?, &old, &new)?,
                    "{target} {kind} file {index}"
                );
                cells += 1;
            }
        }
        assert_eq!(fixture.shared.read_source(file.path)?, *original);
    }
    assert_eq!(cells, 80);
    Ok(())
}

#[test]
fn all_eighty_offline_historical_tree_memberships_match_fixed_blob_ids() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for row in &CONTEXTS {
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
                row.commit,
                "--",
            ]);
        for file in &FILES {
            command.arg(format!("platform/minecraft/{}", file.path));
        }
        let mut child = command
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()
            .wrap_err("cannot read offline tree membership")?;
        let mut output = Vec::new();
        child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("missing tree pipe"))?
            .take(64 * 1024 + 1)
            .read_to_end(&mut output)?;
        if output.len() > 64 * 1024 {
            let _ = child.kill();
            let _ = child.wait();
            eyre::bail!("tree output exceeds bound");
        }
        ensure!(child.wait()?.success(), "offline tree command failed");
        let mut actual = BTreeMap::new();
        for entry in output.split(|b| *b == 0).filter(|entry| !entry.is_empty()) {
            let (header, path) = std::str::from_utf8(entry)?
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("invalid tree entry"))?;
            let words = header.split(' ').collect::<Vec<_>>();
            ensure!(
                words.len() == 3 && words[0] == "100644" && words[1] == "blob",
                "non-regular historical Java input"
            );
            ensure!(
                actual
                    .insert(path.to_owned(), words[2].to_owned())
                    .is_none(),
                "duplicate tree path"
            );
        }
        ensure!(actual.len() == FILES.len(), "historical membership changed");
        for (index, file) in FILES.iter().enumerate() {
            assert_eq!(
                actual
                    .get(&format!("platform/minecraft/{}", file.path))
                    .map(String::as_str),
                Some(row.blobs[index]),
                "{} {}",
                row.name,
                file.path
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 80);
    Ok(())
}
