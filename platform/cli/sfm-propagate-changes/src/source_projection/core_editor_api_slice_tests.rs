//! Frozen source-only editor API migration goldens.
//!
//! Raw offline Git blobs and approved EOL-normalized bodies are immutable
//! independent witnesses, never production source fallbacks. These tests use
//! actual core membership and Liquid rendering, not Java compilation or editor
//! behavior. Deliberate future common edits require reviewed golden evidence.

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

const LEDGER: &str = "docs/tasks/sfm-core-editor-api-slice.json";
const LIMIT: u64 = 256 * 1024;
const FLAGS: [&str; 5] = [
    "editor_overlay_push",
    "editor_v2_cursor_advance",
    "editor_documents",
    "editor_document_panels",
    "editor_async_save",
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
struct ContextGolden {
    name: &'static str,
    commit: &'static str,
    blobs: [&'static str; 6],
}
const CONTEXTS: [ContextGolden; 20] = [
    ContextGolden {
        name: "dev/1.19.2",
        commit: "f3ff2f6425434f36c7c680fa909c977b158e1860",
        blobs: [
            "08e551502890ac739e7846b86a10395ad28567f3",
            "71cabb763cc46ffc2fa8784467104a1483fae179",
            "e9c10bca7d9e08515dcb8f8d480fd4c482407201",
            "61cdc565434aeb91b33703971327a7f1e0d2b9d1",
            "44b86c32e8c78875fd07dcaad4a77de99b56bb9f",
            "3ea151909097c385113a62e6506d8c78d6b4ac26",
        ],
    },
    ContextGolden {
        name: "dev/1.19.4",
        commit: "2e3b561c15d663fb89fd353ccc2af67eeb0c2053",
        blobs: [
            "08e551502890ac739e7846b86a10395ad28567f3",
            "71cabb763cc46ffc2fa8784467104a1483fae179",
            "e9c10bca7d9e08515dcb8f8d480fd4c482407201",
            "61cdc565434aeb91b33703971327a7f1e0d2b9d1",
            "44b86c32e8c78875fd07dcaad4a77de99b56bb9f",
            "3ea151909097c385113a62e6506d8c78d6b4ac26",
        ],
    },
    ContextGolden {
        name: "dev/1.20",
        commit: "6bf4845761d06560fc5e0e36018b5d583589004d",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "c2596cd7ebd9b3c7bc2d1ac1b2c76fb1078dbfef",
            "61cdc565434aeb91b33703971327a7f1e0d2b9d1",
            "44b86c32e8c78875fd07dcaad4a77de99b56bb9f",
            "3ea151909097c385113a62e6506d8c78d6b4ac26",
        ],
    },
    ContextGolden {
        name: "dev/1.20.1",
        commit: "faa040ce14dd825f2dd9716ea59508bf46278c06",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "c2596cd7ebd9b3c7bc2d1ac1b2c76fb1078dbfef",
            "61cdc565434aeb91b33703971327a7f1e0d2b9d1",
            "44b86c32e8c78875fd07dcaad4a77de99b56bb9f",
            "3ea151909097c385113a62e6506d8c78d6b4ac26",
        ],
    },
    ContextGolden {
        name: "dev/1.20.2",
        commit: "a829fb4db2ae06eddd2defb22e33f0b45a4b3ff9",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "c2596cd7ebd9b3c7bc2d1ac1b2c76fb1078dbfef",
            "61cdc565434aeb91b33703971327a7f1e0d2b9d1",
            "44b86c32e8c78875fd07dcaad4a77de99b56bb9f",
            "3ea151909097c385113a62e6506d8c78d6b4ac26",
        ],
    },
    ContextGolden {
        name: "dev/1.20.3",
        commit: "704aa69edad5376d8d6cfb0b0ef7845af077e647",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "c2596cd7ebd9b3c7bc2d1ac1b2c76fb1078dbfef",
            "61cdc565434aeb91b33703971327a7f1e0d2b9d1",
            "44b86c32e8c78875fd07dcaad4a77de99b56bb9f",
            "3ea151909097c385113a62e6506d8c78d6b4ac26",
        ],
    },
    ContextGolden {
        name: "dev/1.20.4",
        commit: "11d3ed07d654ff801329f17cf1eb81c2b347eecd",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "c2596cd7ebd9b3c7bc2d1ac1b2c76fb1078dbfef",
            "61cdc565434aeb91b33703971327a7f1e0d2b9d1",
            "44b86c32e8c78875fd07dcaad4a77de99b56bb9f",
            "3ea151909097c385113a62e6506d8c78d6b4ac26",
        ],
    },
    ContextGolden {
        name: "dev/1.21.0",
        commit: "43068d610b1c053c6569be486439769eec2ae9ef",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "c2596cd7ebd9b3c7bc2d1ac1b2c76fb1078dbfef",
            "61cdc565434aeb91b33703971327a7f1e0d2b9d1",
            "44b86c32e8c78875fd07dcaad4a77de99b56bb9f",
            "3ea151909097c385113a62e6506d8c78d6b4ac26",
        ],
    },
    ContextGolden {
        name: "dev/1.21.1",
        commit: "7524ab5512878b773e212600c9578b2bc4db4717",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "c2596cd7ebd9b3c7bc2d1ac1b2c76fb1078dbfef",
            "61cdc565434aeb91b33703971327a7f1e0d2b9d1",
            "44b86c32e8c78875fd07dcaad4a77de99b56bb9f",
            "3ea151909097c385113a62e6506d8c78d6b4ac26",
        ],
    },
    ContextGolden {
        name: "dev/26.1.2",
        commit: "6bd27f03863ddd041344cbc3da51b01a7b3c3e1f",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "c2596cd7ebd9b3c7bc2d1ac1b2c76fb1078dbfef",
            "61cdc565434aeb91b33703971327a7f1e0d2b9d1",
            "44b86c32e8c78875fd07dcaad4a77de99b56bb9f",
            "3ea151909097c385113a62e6506d8c78d6b4ac26",
        ],
    },
    ContextGolden {
        name: "release/1.19.2",
        commit: "31135b8e86801b862d5cb2283c7c5878b7cc5bb4",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "b29b1f708d4843203f3c7519d49145cf31f6e33d",
            "62d2cdb5459d4450818616e2314e39d53fdfa4fb",
            "bd657219a1a53c53a0ec09d8dcf2b0e628c6f830",
            "0e9103f8de51276ca37c8be48a3fcb414c0847b2",
        ],
    },
    ContextGolden {
        name: "release/1.19.4",
        commit: "23785b63e3e1fe35e5be6a6d89b6638ee0ff0daa",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "b29b1f708d4843203f3c7519d49145cf31f6e33d",
            "62d2cdb5459d4450818616e2314e39d53fdfa4fb",
            "bd657219a1a53c53a0ec09d8dcf2b0e628c6f830",
            "0e9103f8de51276ca37c8be48a3fcb414c0847b2",
        ],
    },
    ContextGolden {
        name: "release/1.20",
        commit: "3df18123a19535fd0e5d1dc81aa302105c3fd2f6",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "b29b1f708d4843203f3c7519d49145cf31f6e33d",
            "62d2cdb5459d4450818616e2314e39d53fdfa4fb",
            "bd657219a1a53c53a0ec09d8dcf2b0e628c6f830",
            "0e9103f8de51276ca37c8be48a3fcb414c0847b2",
        ],
    },
    ContextGolden {
        name: "release/1.20.1",
        commit: "bb5babf12f467235b3a44ad5098666ee3ed171ec",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "b29b1f708d4843203f3c7519d49145cf31f6e33d",
            "62d2cdb5459d4450818616e2314e39d53fdfa4fb",
            "bd657219a1a53c53a0ec09d8dcf2b0e628c6f830",
            "0e9103f8de51276ca37c8be48a3fcb414c0847b2",
        ],
    },
    ContextGolden {
        name: "release/1.20.2",
        commit: "cfbbafaeda4a006ae32743a92de330711983056b",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "b29b1f708d4843203f3c7519d49145cf31f6e33d",
            "62d2cdb5459d4450818616e2314e39d53fdfa4fb",
            "bd657219a1a53c53a0ec09d8dcf2b0e628c6f830",
            "0e9103f8de51276ca37c8be48a3fcb414c0847b2",
        ],
    },
    ContextGolden {
        name: "release/1.20.3",
        commit: "1b7f9605da0ef13c7601daf3786545868dfc3c78",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "b29b1f708d4843203f3c7519d49145cf31f6e33d",
            "62d2cdb5459d4450818616e2314e39d53fdfa4fb",
            "bd657219a1a53c53a0ec09d8dcf2b0e628c6f830",
            "0e9103f8de51276ca37c8be48a3fcb414c0847b2",
        ],
    },
    ContextGolden {
        name: "release/1.20.4",
        commit: "a637581b5e1078d7cc0ca68333add568e3e387ff",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "b29b1f708d4843203f3c7519d49145cf31f6e33d",
            "62d2cdb5459d4450818616e2314e39d53fdfa4fb",
            "bd657219a1a53c53a0ec09d8dcf2b0e628c6f830",
            "0e9103f8de51276ca37c8be48a3fcb414c0847b2",
        ],
    },
    ContextGolden {
        name: "release/1.21.0",
        commit: "6bfab8a21e7a5bbeb0bb136bf4e5b26f6ce03e25",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "b29b1f708d4843203f3c7519d49145cf31f6e33d",
            "62d2cdb5459d4450818616e2314e39d53fdfa4fb",
            "bd657219a1a53c53a0ec09d8dcf2b0e628c6f830",
            "0e9103f8de51276ca37c8be48a3fcb414c0847b2",
        ],
    },
    ContextGolden {
        name: "release/1.21.1",
        commit: "f5366c79c823ff52712130e69dd9c8166c70bd14",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "b29b1f708d4843203f3c7519d49145cf31f6e33d",
            "62d2cdb5459d4450818616e2314e39d53fdfa4fb",
            "bd657219a1a53c53a0ec09d8dcf2b0e628c6f830",
            "0e9103f8de51276ca37c8be48a3fcb414c0847b2",
        ],
    },
    ContextGolden {
        name: "release/26.1.2",
        commit: "fe32b29453b13b4f3050ad441677c7eb79e80814",
        blobs: [
            "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
            "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
            "b29b1f708d4843203f3c7519d49145cf31f6e33d",
            "62d2cdb5459d4450818616e2314e39d53fdfa4fb",
            "bd657219a1a53c53a0ec09d8dcf2b0e628c6f830",
            "0e9103f8de51276ca37c8be48a3fcb414c0847b2",
        ],
    },
];
struct FileGolden {
    path: &'static str,
    bytes: u64,
    digest: &'static str,
}
const FILES: [FileGolden; 6] = [
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/screen/text_editor/ISFMTextEditScreen.java",
        bytes: 724,
        digest: "sha256:52e7c6fce6e668a6332579acd33634588abc57ccff589e4d9259329d41369e56",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/text_editor/ISFMTextEditorRegistration.java",
        bytes: 1012,
        digest: "sha256:6e6324b153af6dfcfa7445e80fcf7bc2743cba4cad70400462fe6d995d9ef3b8",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/text_editor/ISFMTextEditScreenOpenContext.java",
        bytes: 5790,
        digest: "sha256:9ef78cb0a045189dc4dd0689879613eaafd5244196d98d775965a4ca9a91e026",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextEditScreenDiskOpenContext.java",
        bytes: 349,
        digest: "sha256:a3c9518cc77f47557ddff628f637de5a00cb6e2dd0c7384a1f980c9d4a822899",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/text_editor/TextEditContext.java",
        bytes: 11380,
        digest: "sha256:b2ed8a0d637696c67b7554db901dcec22fe906fd76144c4e6290945f5d3e2140",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextEditScreenV2Registration.java",
        bytes: 728,
        digest: "sha256:579abe0e7888fdc26f5a1691635f2b075c12b8acdd0872093f1e3f36461d5814",
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
const RAW: [RawGolden; 13] = [
    RawGolden {
        blob: "08e551502890ac739e7846b86a10395ad28567f3",
        bytes: 660,
        digest: "sha256:829be823665dda220c990bea619e3c01acb1063d4f544f290facf2a2d09e5ede",
        crlf: 19,
        normalized_bytes: 641,
        normalized_digest: "sha256:d6ebdaef57f28b6d101e4b8fe200bc211e3313326efcd5a3f733f5b89f324855",
    },
    RawGolden {
        blob: "e9c10bca7d9e08515dcb8f8d480fd4c482407201",
        bytes: 5455,
        digest: "sha256:8c69442305ecd81ce9e2924e35c639afb0d795f4b7d4782ecd2e018cef0b6d23",
        crlf: 67,
        normalized_bytes: 5388,
        normalized_digest: "sha256:a1e0e19017cb1cb5f9762b245dc5e6088ef54ecc473651b96afcca60a1f77275",
    },
    RawGolden {
        blob: "71cabb763cc46ffc2fa8784467104a1483fae179",
        bytes: 858,
        digest: "sha256:bc279605c3773453a4317cff2d6e925645e2aaa3759f25b4c3f0547ed5b87bd1",
        crlf: 5,
        normalized_bytes: 853,
        normalized_digest: "sha256:6d595bbb28a0160c36dff66c9eb113b786f771debdceb93325017a6f05f1c964",
    },
    RawGolden {
        blob: "61cdc565434aeb91b33703971327a7f1e0d2b9d1",
        bytes: 355,
        digest: "sha256:c69f707a335b16824241ac2f379c9932b6fa22f59ac843f16122a2aa2d8a752f",
        crlf: 6,
        normalized_bytes: 349,
        normalized_digest: "sha256:a3c9518cc77f47557ddff628f637de5a00cb6e2dd0c7384a1f980c9d4a822899",
    },
    RawGolden {
        blob: "3ea151909097c385113a62e6506d8c78d6b4ac26",
        bytes: 620,
        digest: "sha256:8eb751017c1f23f7f493e8a0cd208872f97ec2fc568146040c483d45a4ccb085",
        crlf: 11,
        normalized_bytes: 609,
        normalized_digest: "sha256:d4a4c9e391ac263c9433fd51ae3368075c8c7ff3d85f9b9dc4a6aee75c3bbe54",
    },
    RawGolden {
        blob: "44b86c32e8c78875fd07dcaad4a77de99b56bb9f",
        bytes: 11557,
        digest: "sha256:d6f4e430291038a0be8809cba741a467018548159e3d3f9c555a8eb03d079f23",
        crlf: 232,
        normalized_bytes: 11325,
        normalized_digest: "sha256:c7b913322be51a40170c9657e8e3d166869fcbc0fc5c39901c25306fb2e47acb",
    },
    RawGolden {
        blob: "3a8e3f2c78a1625e02609a970b09650edb20f7cb",
        bytes: 540,
        digest: "sha256:494ad0c54897a4f00f0202a44833e7b1481edb3860b88619fbe92dadd3f09615",
        crlf: 20,
        normalized_bytes: 520,
        normalized_digest: "sha256:2264901fba2946a1930b7d2c754caad8804f9e1f4e094391491dafdd034472a6",
    },
    RawGolden {
        blob: "c2596cd7ebd9b3c7bc2d1ac1b2c76fb1078dbfef",
        bytes: 3339,
        digest: "sha256:6a7d6f9912f4ecbb9131ad7b97989059d707f7469aeafed27478717c4ba71884",
        crlf: 75,
        normalized_bytes: 3264,
        normalized_digest: "sha256:ac3f9ae3b6e4d071bbd468b465eb4c952d39f0d2926ce7311c0ee67111769ec3",
    },
    RawGolden {
        blob: "67ba69418eed95a614aa4a06477a0c0bf136c3e2",
        bytes: 342,
        digest: "sha256:c3ceddc12dae69f247f247fec221a216bca1d2c3b55c952a4445bcaffe92f931",
        crlf: 11,
        normalized_bytes: 331,
        normalized_digest: "sha256:88b3e48e2d2d1ff385819f958065df774f424f00c13d704dcc514dc2b90088f7",
    },
    RawGolden {
        blob: "b29b1f708d4843203f3c7519d49145cf31f6e33d",
        bytes: 3034,
        digest: "sha256:3fc749028075b4fa9bcbe3a86802191f7a63490025f35deff553bc2b3514788d",
        crlf: 78,
        normalized_bytes: 2956,
        normalized_digest: "sha256:87fc747a29d8df8b6dc5783142814eecac895eef149c85a864d0d27f15da57ac",
    },
    RawGolden {
        blob: "62d2cdb5459d4450818616e2314e39d53fdfa4fb",
        bytes: 361,
        digest: "sha256:4cf06806ca28573a34505d20aeb11a57496336c6c0e013e7dea66e74b5056a09",
        crlf: 12,
        normalized_bytes: 349,
        normalized_digest: "sha256:a3c9518cc77f47557ddff628f637de5a00cb6e2dd0c7384a1f980c9d4a822899",
    },
    RawGolden {
        blob: "0e9103f8de51276ca37c8be48a3fcb414c0847b2",
        bytes: 586,
        digest: "sha256:b14cbb4a607f8b64801b62af35c8f38edf6e62c28ae2865aa9d033eecc06876c",
        crlf: 15,
        normalized_bytes: 571,
        normalized_digest: "sha256:e37ddea297a580557d2d576b66ba9ade7f0b90aebff4e9407f28dea1c3776bed",
    },
    RawGolden {
        blob: "bd657219a1a53c53a0ec09d8dcf2b0e628c6f830",
        bytes: 10761,
        digest: "sha256:127a1a2709c3e17af2f53f1a7b6df33707297d9707b88797a7ff4887d6a925d2",
        crlf: 241,
        normalized_bytes: 10520,
        normalized_digest: "sha256:b9b9b84c1a04c8db69690687c8a1dbc6cc0dbcaae14a48d972d797b551bf681c",
    },
];
#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    owners: BTreeMap<String, Owner>,
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
            .wrap_err("cannot parse bounded editor API ledger")?;
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
            self.ledger.schema == "sfm:core-editor-api-slice@1"
                && self.ledger.context_commits == commits,
            "frozen context identity changed"
        );
        ensure!(self.ledger.owners.len() == 5, "editor owner scope changed");
        for (index, name) in FLAGS.iter().enumerate() {
            let proposed = self
                .ledger
                .owners
                .get(*name)
                .ok_or_else(|| eyre::eyre!("missing proposed editor owner"))?;
            let actual = self
                .shared
                .features
                .0
                .get(*name)
                .ok_or_else(|| eyre::eyre!("editor owner not registered"))?;
            let support: &[&str] = if index < 2 {
                &TARGETS
            } else {
                &["1.19.2", "1.19.4"]
            };
            let requires: &[&str] = match index {
                3 => &["workspace_panels", "editor_documents"],
                4 => &["editor_documents"],
                _ => &[],
            };
            ensure!(
                proposed
                    .support
                    .iter()
                    .map(String::as_str)
                    .collect::<Vec<_>>()
                    == support
                    && proposed
                        .requires
                        .iter()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>()
                        == requires.iter().copied().collect()
                    && proposed.support == actual.supported_targets
                    && proposed.requires.iter().collect::<BTreeSet<_>>()
                        == actual.requires.iter().collect(),
                "editor owner support/prerequisite changed"
            );
        }
        ensure!(
            self.ledger.normalization.policy == "sfm:java_crlf_to_lf@1"
                && !self.ledger.normalization.raw_byte_exact
                && self.ledger.normalization.approved_changes.len() == RAW.len()
                && self
                    .ledger
                    .normalization
                    .approved_changes
                    .iter()
                    .map(String::as_str)
                    .collect::<BTreeSet<_>>()
                    == RAW.iter().map(|r| r.blob).collect(),
            "normalization scope changed"
        );
        for row in &RAW {
            self.normalized(row.blob)?;
        }
        ensure!(self.ledger.files.len() == 6, "editor file scope changed");
        for (index, (file, golden)) in self.ledger.files.iter().zip(&FILES).enumerate() {
            ensure!(
                file.intended_core_path == golden.path
                    && file.stage_bytes == golden.bytes
                    && file.stage_sha256 == golden.digest
                    && self.sources[index].len() as u64 == golden.bytes
                    && sha256(&self.sources[index]) == golden.digest
                    && !self.sources[index].contains(&b'\r')
                    && self.sources[index].ends_with(b"\n")
                    && !self.sources[index].starts_with(&[0xef, 0xbb, 0xbf]),
                "promoted template identity changed"
            );
            let owners: &[&str] = match index {
                0 => &[FLAGS[3], FLAGS[4]],
                1 => &[FLAGS[3]],
                2 => &[FLAGS[0], FLAGS[2], FLAGS[4]],
                3 => &[],
                4 => &[FLAGS[1]],
                5 => &[FLAGS[0]],
                _ => eyre::bail!("invalid editor index"),
            };
            ensure!(
                file.functional_owners
                    .iter()
                    .map(String::as_str)
                    .collect::<Vec<_>>()
                    == owners,
                "member owner contract changed"
            );
            ensure!(
                file.witnesses.len() == 20,
                "editor witness coverage changed"
            );
            let mut seen = BTreeSet::new();
            for witness in &file.witnesses {
                ensure!(
                    seen.insert(witness.context.as_str()),
                    "duplicate context witness"
                );
                let context = context_golden(&witness.context)?;
                let raw = raw_golden(context.blobs[index])?;
                validate_witness(witness, raw)?;
            }
            ensure!(
                seen == commits.keys().map(String::as_str).collect(),
                "editor context coverage changed"
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
                "duplicate raw witness"
            );
            let raw = raw_golden(&witness.git_blob)?;
            let index = CONTEXTS
                .iter()
                .find_map(|c| c.blobs.iter().position(|oid| *oid == raw.blob))
                .ok_or_else(|| eyre::eyre!("raw witness is not a member"))?;
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
                    && witness.normalization == "sfm:java_crlf_to_lf@1"
                    && witness.contexts.len() == contexts.len()
                    && witness
                        .contexts
                        .iter()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>()
                        == contexts,
                "independent raw/normalized evidence changed"
            );
        }
        Ok(())
    }
    fn normalized(&self, oid: &str) -> Result<String> {
        normalize_raw(
            raw_golden(oid)?,
            self.raw
                .get(oid)
                .ok_or_else(|| eyre::eyre!("missing frozen raw oracle"))?,
        )
    }
    fn selection(&self, context: &ProjectionContext) -> Result<()> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        for file in &FILES {
            let input = selection
                .inputs
                .get(file.path)
                .ok_or_else(|| eyre::eyre!("baseline editor API omitted"))?;
            ensure!(
                input.input == file.path
                    && (input.template || file.path.ends_with(".java"))
                    && !selection.omitted_paths.contains(file.path),
                "editor API routed away from shared core"
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
    fn oracle(&self, index: usize, flags: &[&str]) -> Result<String> {
        let mut text = self.normalized(
            *CONTEXTS[0]
                .blobs
                .get(index)
                .ok_or_else(|| eyre::eyre!("invalid oracle index"))?,
        )?;
        match index {
            0 if !flags.contains(&FLAGS[3]) && !flags.contains(&FLAGS[4]) => {
                text = replace_once(&text, HOST_CLOSE, "")?;
            }
            1 if !flags.contains(&FLAGS[3]) => {
                text = replace_once(&text, PANEL_IMPORTS, "")?;
                text = replace_once(&text, READ_ONLY_PANEL, "")?;
                text = replace_once(&text, CREATE_PANEL, "")?;
            }
            2 => {
                if !flags.contains(&FLAGS[0]) {
                    text = replace_once(&text, PUSH_DEFAULT, "")?;
                }
                if !flags.contains(&FLAGS[2]) {
                    text = replace_once(&text, "import java.util.Optional;\n", "")?;
                    text = replace_once(&text, DOCUMENT_DEFAULTS, "")?;
                    let original = self.normalized("b29b1f708d4843203f3c7519d49145cf31f6e33d")?;
                    text = replace_once(&text, save_region(&text)?, save_region(&original)?)?;
                } else if !flags.contains(&FLAGS[4]) {
                    text = replace_once(&text, ASYNC_DEFAULTS, "")?;
                }
            }
            4 if !flags.contains(&FLAGS[1]) => {
                text = replace_once(&text, CURSOR_ADVANCE, "")?;
            }
            5 if !flags.contains(&FLAGS[0]) => {
                text = replace_once(
                    &text,
                    "                SFMScreenChangeHelpers.getCurrentScreen(),\n                context.preferPush()\n",
                    "                SFMScreenChangeHelpers.getCurrentScreen()\n",
                )?;
            }
            _ => {}
        }
        Ok(text)
    }
}
fn inventory() -> BTreeSet<String> {
    FILES.iter().map(|f| f.path.to_owned()).collect()
}
fn context_golden(name: &str) -> Result<&'static ContextGolden> {
    CONTEXTS
        .iter()
        .find(|c| c.name == name)
        .ok_or_else(|| eyre::eyre!("unknown frozen context"))
}
fn raw_golden(oid: &str) -> Result<&'static RawGolden> {
    RAW.iter()
        .find(|r| r.blob == oid)
        .ok_or_else(|| eyre::eyre!("unapproved raw identity"))
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
            && w.normalization == "sfm:java_crlf_to_lf@1",
        "per-context raw/normalized witness changed"
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
fn save_region(text: &str) -> Result<&str> {
    let start = text
        .find("    default void onSaveAndClose")
        .ok_or_else(|| eyre::eyre!("missing save region start"))?;
    let end = text
        .find("    Consumer<String> saveWriter();")
        .ok_or_else(|| eyre::eyre!("missing save region end"))?;
    ensure!(start < end, "invalid save region bounds");
    Ok(&text[start..end])
}
fn required_context_features(flags: &[&'static str]) -> Vec<&'static str> {
    let mut context = flags.to_vec();
    if flags.contains(&FLAGS[3]) {
        context.push("workspace_panels");
    }
    context
}
fn historical_flags(name: &str) -> Result<Vec<&'static str>> {
    let (kind, target) = name
        .split_once('/')
        .ok_or_else(|| eyre::eyre!("invalid context"))?;
    if kind == "release" {
        return Ok(Vec::new());
    }
    ensure!(kind == "dev", "unknown source witness kind");
    let d2 = matches!(target, "1.19.2" | "1.19.4");
    Ok(FLAGS
        .iter()
        .enumerate()
        .filter(|(i, _)| *i < 2 || d2)
        .map(|(_, f)| *f)
        .collect())
}

const HOST_CLOSE: &str = "    /** Actual panel disposal, not transient screen covering or resizing. */\n    default void onDocumentHostClosed() { }\n";
const PANEL_IMPORTS: &str = "import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;\nimport ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;\n";
const READ_ONLY_PANEL: &str = "    /** Whether this editor enforces {@code readOnly=true} throughout its UI. */\n    default boolean supportsReadOnlyPanel() {\n        return false;\n    }\n\n";
const CREATE_PANEL: &str = "\n    /** Create a panel-capable projection of this editor implementation. */\n    default SFMScreenPanel createPanel(SFMTextEditorPanelOpenContext context) {\n        return SFMTextEditorPanel.legacy(context, this::createScreen);\n    }\n";
const PUSH_DEFAULT: &str = "    /**\n     * Whether the preferred editor should be layered over the current screen\n     * instead of replacing it. This is useful for read-only/help documents\n     * opened from another transient screen, such as the command palette.\n     */\n    default boolean preferPush() {\n        return false;\n    }\n\n";
const DOCUMENT_DEFAULTS: &str = "    /** Whether the editor must present the document without allowing edits. */\n    default boolean readOnly() {\n        return false;\n    }\n\n    /** Concrete document metadata when this editor was opened from a typed panel document. */\n    default Optional<SFMTextDocumentSnapshot> documentSnapshot() {\n        return Optional.empty();\n    }\n\n";
const ASYNC_DEFAULTS: &str = "    default boolean asynchronousSave() { return false; }\n\n    default java.util.concurrent.CompletableFuture<SFMTextDocumentSaveResult> saveDocumentAsync(String content) {\n        return java.util.concurrent.CompletableFuture.completedFuture(saveDocument(content));\n    }\n\n    /** Whether Save-and-close may hand an accepted async operation to durable host feedback. */\n    default boolean detachSaveAndCloseAfterSubmission() { return false; }\n\n    /** Called on the client thread only after durable async success. */\n    default void documentSaved(String submittedText) { }\n\n    default void finishAsyncSaveClose() { SFMScreenChangeHelpers.popScreen(); }\n\n    default boolean saveHostIsCurrent() { return true; }\n\n    default boolean cancelPendingSave() { return false; }\n";
const CURSOR_ADVANCE: &str = "        ArrayDeque<Cursor> advancedCursors = new ArrayDeque<>();\n        for (Cursor cursor : multiCursor().cursors()) {\n            IntList lineCarets = caretsByLine.get(cursor.head().lineIndex());\n            int insertedTextBeforeOrAtCursor = 0;\n            for (int gapIndex : lineCarets) {\n                if (gapIndex <= cursor.head().gapIndex()) {\n                    insertedTextBeforeOrAtCursor += text.length();\n                }\n            }\n            Caret newCaret = new Caret(\n                    cursor.head().lineIndex(),\n                    cursor.head().gapIndex() + insertedTextBeforeOrAtCursor\n            );\n            advancedCursors.add(new Cursor(newCaret, newCaret));\n        }\n        multiCursor().cursors().clear();\n        multiCursor().cursors().addAll(advancedCursors);\n";

#[test]
fn all_one_hundred_twenty_raw_and_normalized_bodies_reconstruct_through_real_renderer() -> Result<()>
{
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for row in &CONTEXTS {
        let (_, target) = row
            .name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("bad context"))?;
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
    assert_eq!(cells, 120);
    Ok(())
}

#[test]
fn independent_editor_owner_masks_preserve_optional_api_imports_and_original_off_behavior()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut profiles = 0;
    let mut cells = 0;
    for target in TARGETS {
        let d2 = matches!(target, "1.19.2" | "1.19.4");
        for mask in 0_u8..32 {
            if (!d2 && mask & 28 != 0) || (mask & 4 == 0 && mask & 24 != 0) {
                continue;
            }
            let flags = FLAGS
                .iter()
                .enumerate()
                .filter(|(i, _)| mask & (1 << i) != 0)
                .map(|(_, f)| *f)
                .collect::<Vec<_>>();
            let context = fixture
                .shared
                .context(target, &required_context_features(&flags))?;
            for index in 0..FILES.len() {
                let body = fixture.render(index, &context)?;
                assert_eq!(
                    body,
                    fixture.oracle(index, &flags)?,
                    "{target} mask {mask} file {index}"
                );
                match index {
                    0 => assert_eq!(
                        body.contains("onDocumentHostClosed()"),
                        flags.contains(&FLAGS[3]) || flags.contains(&FLAGS[4])
                    ),
                    1 => {
                        assert_eq!(
                            body.contains("SFMTextEditorPanel"),
                            flags.contains(&FLAGS[3])
                        );
                        assert_eq!(
                            body.contains("supportsReadOnlyPanel()"),
                            flags.contains(&FLAGS[3])
                        );
                    }
                    2 => {
                        assert_eq!(body.contains("preferPush()"), flags.contains(&FLAGS[0]));
                        assert_eq!(
                            body.contains("import java.util.Optional;"),
                            flags.contains(&FLAGS[2])
                        );
                        assert_eq!(body.contains("readOnly()"), flags.contains(&FLAGS[2]));
                        assert_eq!(
                            body.contains("SFMTextDocumentSnapshot"),
                            flags.contains(&FLAGS[2])
                        );
                        assert_eq!(
                            body.contains("SFMTextDocumentSaveResult"),
                            flags.contains(&FLAGS[2])
                        );
                        assert_eq!(
                            body.contains("catch (RuntimeException failure)"),
                            flags.contains(&FLAGS[2])
                        );
                        assert_eq!(
                            body.contains("asynchronousSave()"),
                            flags.contains(&FLAGS[4])
                        );
                        assert_eq!(
                            body.contains("cancelPendingSave()"),
                            flags.contains(&FLAGS[4])
                        );
                        assert!(body.contains("exitWithoutSavingConfirmScreen.setDelay(20);"));
                    }
                    3 => assert!(!body.contains("preferPush") && !body.contains("readOnly")),
                    4 => {
                        assert_eq!(body.contains("advancedCursors"), flags.contains(&FLAGS[1]));
                        assert!(body.contains("assertInvariants();"));
                    }
                    5 => assert_eq!(
                        body.contains("context.preferPush()"),
                        flags.contains(&FLAGS[0])
                    ),
                    _ => unreachable!(),
                }
                cells += 1;
            }
            for unrelated in [
                "client_actions",
                "canvas_text_editor",
                "packet_values",
                "client_manager",
                "terminal_remote",
                "terminal_vox",
            ] {
                assert_ne!(context.features.get(unrelated), Some(&true), "{unrelated}");
            }
            profiles += 1;
        }
    }
    assert_eq!((profiles, cells), (72, 432));
    Ok(())
}

#[test]
fn owner_support_and_real_prerequisites_reject_unreviewed_combinations() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        fixture.shared.context(target, &[FLAGS[0], FLAGS[1]])?;
        let baseline = fixture.shared.context(target, &[])?;
        if target == "1.21.0" {
            assert_eq!(baseline.minecraft_version, "1.21");
        }
        fixture.selection(&baseline)?;
        assert!(
            fixture
                .shared
                .context(target, &["unknown_editor_owner"])
                .is_err()
        );
        if matches!(target, "1.19.2" | "1.19.4") {
            fixture.shared.context(target, &[FLAGS[2]])?;
            fixture.shared.context(target, &[FLAGS[2], FLAGS[4]])?;
            fixture
                .shared
                .context(target, &[FLAGS[2], FLAGS[3], "workspace_panels"])?;
            assert!(fixture.shared.context(target, &[FLAGS[4]]).is_err());
            assert!(
                fixture
                    .shared
                    .context(target, &[FLAGS[3], FLAGS[2]])
                    .is_err()
            );
            assert!(
                fixture
                    .shared
                    .context(target, &[FLAGS[3], "workspace_panels"])
                    .is_err()
            );
        } else {
            for owner in &FLAGS[2..] {
                assert!(
                    fixture.shared.context(target, &[*owner]).is_err(),
                    "{target} {owner}"
                );
            }
        }
    }
    Ok(())
}

#[test]
fn eol_normalization_is_exact_id_scoped_and_rejects_mutated_raw_oracles() -> Result<()> {
    let fixture = Fixture::load()?;
    assert!(raw_golden("0000000000000000000000000000000000000000").is_err());
    for golden in &RAW {
        let raw = fixture
            .raw
            .get(golden.blob)
            .ok_or_else(|| eyre::eyre!("missing raw"))?;
        let text = std::str::from_utf8(raw)?;
        normalize_raw(golden, raw)?;
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(raw);
        let mutations = [
            bom,
            text.replace("\r\n", "\n").into_bytes(),
            text.replacen("\r\n", "\r", 1).into_bytes(),
            raw[..raw.len() - 1].to_vec(),
            text.replacen("public ", "private ", 1).into_bytes(),
            text.replacen("    ", "\t", 1).into_bytes(),
        ];
        for mutation in mutations {
            assert!(
                normalize_raw(golden, &mutation).is_err(),
                "raw mutation accepted: {}",
                golden.blob
            );
        }
    }
    Ok(())
}

#[test]
fn cursor_advance_is_independent_of_overlay_document_panel_and_async_flags() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let off = fixture.shared.context(target, &[])?;
        let on = fixture.shared.context(target, &[FLAGS[1]])?;
        assert_eq!(
            fixture.render(4, &off)?,
            replace_once(&fixture.render(4, &on)?, CURSOR_ADVANCE, "")?
        );
        let body = fixture.render(4, &on)?;
        let inserted = body
            .find("        ArrayDeque<Cursor> advancedCursors")
            .ok_or_else(|| eyre::eyre!("cursor unit omitted"))?;
        assert!(body[..inserted].contains("caretsByLine"));
        assert!(body[inserted..].contains("if (gapIndex <= cursor.head().gapIndex())"));
        assert!(body[inserted..].contains("advancedCursors.add(new Cursor(newCaret, newCaret));"));
        for index in [0, 1, 2, 3, 5] {
            assert_eq!(fixture.render(index, &off)?, fixture.render(index, &on)?);
        }
    }
    Ok(())
}

#[test]
fn common_edits_reach_all_targets_without_touching_owned_feature_guards_or_originals() -> Result<()>
{
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(super::core_inputs::CORE_ROOT);
    let mut cells = 0;
    for (index, file) in FILES.iter().enumerate() {
        let source = std::str::from_utf8(&fixture.sources[index])?;
        let anchor = source
            .lines()
            .next()
            .ok_or_else(|| eyre::eyre!("empty template"))?;
        let old = format!("{anchor}\n");
        let new = format!("{anchor}\n// Editor API common-edit proof.\n");
        let edited = replace_once(source, &old, &new)?;
        let guards = |s: &str| -> Vec<String> {
            s.lines()
                .filter(|line| line.trim_start().starts_with("{%"))
                .map(str::to_owned)
                .collect()
        };
        assert_eq!(guards(source), guards(&edited));
        let path = root.join(file.path);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(&path, edited.as_bytes())?;
        for target in TARGETS {
            let flags: &[&str] = if matches!(target, "1.19.2" | "1.19.4") {
                &[
                    "editor_overlay_push",
                    "editor_v2_cursor_advance",
                    "editor_documents",
                    "editor_document_panels",
                    "editor_async_save",
                    "workspace_panels",
                ]
            } else {
                &[FLAGS[0], FLAGS[1]]
            };
            let context = fixture.shared.context(target, flags)?;
            fixture.selection(&context)?;
            let bytes = read_bounded(&path, LIMIT)?;
            assert_eq!(std::str::from_utf8(&bytes)?, edited);
            assert_eq!(
                render_java_source(std::str::from_utf8(&bytes)?, &context)?,
                replace_once(&fixture.render(index, &context)?, &old, &new)?
            );
            cells += 1;
        }
        assert_eq!(
            fixture.shared.read_source(file.path)?,
            fixture.sources[index]
        );
    }
    assert_eq!(cells, 60);
    Ok(())
}

#[test]
fn all_one_hundred_twenty_offline_tree_memberships_match_fixed_blob_ids() -> Result<()> {
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
            eyre::bail!("tree membership output exceeds bound");
        }
        ensure!(child.wait()?.success(), "offline tree membership failed");
        let mut actual = BTreeMap::new();
        for entry in output.split(|byte| *byte == 0).filter(|e| !e.is_empty()) {
            let entry = std::str::from_utf8(entry)?;
            let (header, path) = entry
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
                "duplicate historical path"
            );
        }
        ensure!(
            actual.len() == FILES.len(),
            "historical source membership changed"
        );
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
    assert_eq!(cells, 120);
    Ok(())
}
