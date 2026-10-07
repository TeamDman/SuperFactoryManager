//! Frozen source-only V1/V2 editor migration goldens.
//!
//! Raw offline Git blobs and the eighteen approved CRLF-only transformations are
//! independent test witnesses, never production historical fallbacks. This
//! module checks actual core selection and Liquid rendering, not Java compile,
//! document/GUI behavior or complete-project admission. Future intentional
//! common edits require reviewed migration golden updates.

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

const LEDGER: &str = "docs/tasks/sfm-core-editor-screens-slice.json";
const LIMIT: u64 = 256 * 1024;
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const FLAGS: [&str; 7] = [
    "editor_documents",
    "editor_async_save",
    "editor_v1_event_modifiers",
    "editor_v1_adaptive_layout",
    "editor_v1_widget_focus",
    "editor_v1_panel_clipping",
    "editor_overlay_push",
];
struct ContextGolden {
    name: &'static str,
    commit: &'static str,
    blobs: [&'static str; 2],
}
const CONTEXTS: [ContextGolden; 20] = [
    ContextGolden {
        name: "dev/1.19.2",
        commit: "f3ff2f6425434f36c7c680fa909c977b158e1860",
        blobs: [
            "638e8b1cfa94cd1595b241a2d2e3d2bd08094f09",
            "b10f384ae4c239ea8dcf4b20d1d341835a7086c6",
        ],
    },
    ContextGolden {
        name: "dev/1.19.4",
        commit: "2e3b561c15d663fb89fd353ccc2af67eeb0c2053",
        blobs: [
            "aff37ea1019c9066764afa76c3735699ef7e0648",
            "ada03dc91483269a6c9fd0b792f3e4bf82933dcc",
        ],
    },
    ContextGolden {
        name: "dev/1.20",
        commit: "6bf4845761d06560fc5e0e36018b5d583589004d",
        blobs: [
            "afe5c5b42c8ef47c330bf22df27b72845aa58fdf",
            "0fccca83a5313af42bf2bd16029b6d634aa7ec80",
        ],
    },
    ContextGolden {
        name: "dev/1.20.1",
        commit: "faa040ce14dd825f2dd9716ea59508bf46278c06",
        blobs: [
            "afe5c5b42c8ef47c330bf22df27b72845aa58fdf",
            "0fccca83a5313af42bf2bd16029b6d634aa7ec80",
        ],
    },
    ContextGolden {
        name: "dev/1.20.2",
        commit: "a829fb4db2ae06eddd2defb22e33f0b45a4b3ff9",
        blobs: [
            "7c037155c0b8d19473ceaf7c49bf61c9e4328d4a",
            "0fccca83a5313af42bf2bd16029b6d634aa7ec80",
        ],
    },
    ContextGolden {
        name: "dev/1.20.3",
        commit: "704aa69edad5376d8d6cfb0b0ef7845af077e647",
        blobs: [
            "7c037155c0b8d19473ceaf7c49bf61c9e4328d4a",
            "0fccca83a5313af42bf2bd16029b6d634aa7ec80",
        ],
    },
    ContextGolden {
        name: "dev/1.20.4",
        commit: "11d3ed07d654ff801329f17cf1eb81c2b347eecd",
        blobs: [
            "7c037155c0b8d19473ceaf7c49bf61c9e4328d4a",
            "0fccca83a5313af42bf2bd16029b6d634aa7ec80",
        ],
    },
    ContextGolden {
        name: "dev/1.21.0",
        commit: "43068d610b1c053c6569be486439769eec2ae9ef",
        blobs: [
            "45ecd70761d3ad21f5a34c4b1130c606af2f06a4",
            "20b1798d2e04bbb2eb66631f5e0d80cdb727d4eb",
        ],
    },
    ContextGolden {
        name: "dev/1.21.1",
        commit: "7524ab5512878b773e212600c9578b2bc4db4717",
        blobs: [
            "45ecd70761d3ad21f5a34c4b1130c606af2f06a4",
            "20b1798d2e04bbb2eb66631f5e0d80cdb727d4eb",
        ],
    },
    ContextGolden {
        name: "dev/26.1.2",
        commit: "6bd27f03863ddd041344cbc3da51b01a7b3c3e1f",
        blobs: [
            "bfa158391c28c924b1d06539e1a775b6dca3edb1",
            "114da2b0c397d24b6f9ad46a9ca5615a8d3bca90",
        ],
    },
    ContextGolden {
        name: "release/1.19.2",
        commit: "31135b8e86801b862d5cb2283c7c5878b7cc5bb4",
        blobs: [
            "9f95c4b3a3e99d1a47e426a25acf9c111b38ea70",
            "6d258910fe90cb3b42bbfc72b5617d6621b8779d",
        ],
    },
    ContextGolden {
        name: "release/1.19.4",
        commit: "23785b63e3e1fe35e5be6a6d89b6638ee0ff0daa",
        blobs: [
            "af5f5a4e8f2fac648f4b420823c3a3bab2af47fd",
            "5e7d00e4806b19f43f1ab4d61a64360713bf64ff",
        ],
    },
    ContextGolden {
        name: "release/1.20",
        commit: "3df18123a19535fd0e5d1dc81aa302105c3fd2f6",
        blobs: [
            "afe5c5b42c8ef47c330bf22df27b72845aa58fdf",
            "a4261a1d199534b0098ea75543369c4cb90341b0",
        ],
    },
    ContextGolden {
        name: "release/1.20.1",
        commit: "bb5babf12f467235b3a44ad5098666ee3ed171ec",
        blobs: [
            "afe5c5b42c8ef47c330bf22df27b72845aa58fdf",
            "a4261a1d199534b0098ea75543369c4cb90341b0",
        ],
    },
    ContextGolden {
        name: "release/1.20.2",
        commit: "cfbbafaeda4a006ae32743a92de330711983056b",
        blobs: [
            "7c037155c0b8d19473ceaf7c49bf61c9e4328d4a",
            "a4261a1d199534b0098ea75543369c4cb90341b0",
        ],
    },
    ContextGolden {
        name: "release/1.20.3",
        commit: "1b7f9605da0ef13c7601daf3786545868dfc3c78",
        blobs: [
            "7c037155c0b8d19473ceaf7c49bf61c9e4328d4a",
            "a4261a1d199534b0098ea75543369c4cb90341b0",
        ],
    },
    ContextGolden {
        name: "release/1.20.4",
        commit: "a637581b5e1078d7cc0ca68333add568e3e387ff",
        blobs: [
            "7c037155c0b8d19473ceaf7c49bf61c9e4328d4a",
            "a4261a1d199534b0098ea75543369c4cb90341b0",
        ],
    },
    ContextGolden {
        name: "release/1.21.0",
        commit: "6bfab8a21e7a5bbeb0bb136bf4e5b26f6ce03e25",
        blobs: [
            "45ecd70761d3ad21f5a34c4b1130c606af2f06a4",
            "51f554011e32352b08d6c6fe724bfcf07c79e7d4",
        ],
    },
    ContextGolden {
        name: "release/1.21.1",
        commit: "f5366c79c823ff52712130e69dd9c8166c70bd14",
        blobs: [
            "45ecd70761d3ad21f5a34c4b1130c606af2f06a4",
            "51f554011e32352b08d6c6fe724bfcf07c79e7d4",
        ],
    },
    ContextGolden {
        name: "release/26.1.2",
        commit: "fe32b29453b13b4f3050ad441677c7eb79e80814",
        blobs: [
            "bfa158391c28c924b1d06539e1a775b6dca3edb1",
            "55ff57d6d98c81f0e6757fc90fd3867dda8d9a0a",
        ],
    },
];
struct FileGolden {
    path: &'static str,
    bytes: u64,
    digest: &'static str,
}
const FILES: [FileGolden; 2] = [
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/screen/text_editor/SFMTextEditScreenV1.java",
        bytes: 72393,
        digest: "sha256:92cb82e2a2f74bacffc2b5a3b1a13a2830a96180e682c67df6b6b07bd25b6772",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/screen/text_editor/SFMTextEditScreenV2.java",
        bytes: 18131,
        digest: "sha256:a5b5c2cd9b911b48ed4348cd852f92c96201ca8a60191e319b0646db85432559",
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
const RAW: [RawGolden; 18] = [
    RawGolden {
        blob: "0fccca83a5313af42bf2bd16029b6d634aa7ec80",
        bytes: 11351,
        digest: "sha256:76fb1937cbaca0dc33b597623bf1bea5a8df23c88d878c3e6ab0142765f15ff1",
        crlf: 271,
        normalized_bytes: 11080,
        normalized_digest: "sha256:4c074151aab1aa0ca454aeddc9dc080a025d9b8fb2e667712cca9a519191f975",
    },
    RawGolden {
        blob: "114da2b0c397d24b6f9ad46a9ca5615a8d3bca90",
        bytes: 11381,
        digest: "sha256:d261e434e9b8bad3674b40b304726841cf2e6b8eedf689b7693ab718fb76d505",
        crlf: 270,
        normalized_bytes: 11111,
        normalized_digest: "sha256:6f4a9bf079e998e17c9550365992ed2cf6a6136b921256fe5f07e08456a463d9",
    },
    RawGolden {
        blob: "20b1798d2e04bbb2eb66631f5e0d80cdb727d4eb",
        bytes: 11303,
        digest: "sha256:4d92f01d28953262fbf0f5fe6905fb318b74403081a225ad5f5495817310bac2",
        crlf: 269,
        normalized_bytes: 11034,
        normalized_digest: "sha256:dfa6b7b20d9bb0ae1d2912c5721fe960866e1830be0f65c29e06ef369c72bf17",
    },
    RawGolden {
        blob: "45ecd70761d3ad21f5a34c4b1130c606af2f06a4",
        bytes: 39683,
        digest: "sha256:238d366e7fde257d042c76ca8520c9c8872d626d9d00634b5f411fd06050fa24",
        crlf: 1014,
        normalized_bytes: 38669,
        normalized_digest: "sha256:de16c8295aa5843655df8c62df3b32265e4190a2b54814dc25e733d75cf18813",
    },
    RawGolden {
        blob: "51f554011e32352b08d6c6fe724bfcf07c79e7d4",
        bytes: 10910,
        digest: "sha256:c023597a7f6b425067681980390ff98ddaa4b1c30498aed8e4c33059ae8f69b9",
        crlf: 287,
        normalized_bytes: 10623,
        normalized_digest: "sha256:aeb4e1b6839a1b9fd48cc08a65bb860865f9e1d324a2bea9824f72fa8c16367d",
    },
    RawGolden {
        blob: "55ff57d6d98c81f0e6757fc90fd3867dda8d9a0a",
        bytes: 10988,
        digest: "sha256:c09f156023f98a40062206d28a08d6202f25fbc2919ea10c8f6a3778b8dd6db8",
        crlf: 288,
        normalized_bytes: 10700,
        normalized_digest: "sha256:6262aa353b2365cea937839fb6da6c15158a81601cbf50c977223f4999d8a4af",
    },
    RawGolden {
        blob: "5e7d00e4806b19f43f1ab4d61a64360713bf64ff",
        bytes: 10997,
        digest: "sha256:e96d56a7e268f6f1fff0c73172c3893bfc545f10828c0cdfce563f8979ed08c9",
        crlf: 291,
        normalized_bytes: 10706,
        normalized_digest: "sha256:56752e8651ea41e8a3a5720b44cc80bb56d67221d4e897dd6d894379b8299a1d",
    },
    RawGolden {
        blob: "638e8b1cfa94cd1595b241a2d2e3d2bd08094f09",
        bytes: 46128,
        digest: "sha256:6d3a950a116903b0743ea1600f7ddc8e7fafa89bf2126a1ae3de549015bd8921",
        crlf: 926,
        normalized_bytes: 45202,
        normalized_digest: "sha256:0d2d2d8cce4d736ca6fcfeb449ab1b13a2a40884e62b3f3da2ebda287cd17426",
    },
    RawGolden {
        blob: "6d258910fe90cb3b42bbfc72b5617d6621b8779d",
        bytes: 11069,
        digest: "sha256:b1597bcee0274d48a139176aa843ea4b7c75f4451f31e5d91fe56fac24a8cc3a",
        crlf: 292,
        normalized_bytes: 10777,
        normalized_digest: "sha256:3b37595c5903c2bb7880cde36874c637b228657dff06a277ff5cd322afb6be53",
    },
    RawGolden {
        blob: "7c037155c0b8d19473ceaf7c49bf61c9e4328d4a",
        bytes: 39772,
        digest: "sha256:a9328df4c3e4fee6753bdb867303feeac978bc608203977b0f152427b6feabb6",
        crlf: 1015,
        normalized_bytes: 38757,
        normalized_digest: "sha256:5f7bb5b6333315061a5253f4d8ad9be6d50826ca8573ea9e9071c29e34575a62",
    },
    RawGolden {
        blob: "9f95c4b3a3e99d1a47e426a25acf9c111b38ea70",
        bytes: 38248,
        digest: "sha256:826ec8045b2b1881bd9e73c8670127e0387b6cd61921ff97a0fa3a58c60850b9",
        crlf: 989,
        normalized_bytes: 37259,
        normalized_digest: "sha256:d254d6f4319f432a4ee05eae211c218dcc7b5eebde1e6d2511dde5d7e963a514",
    },
    RawGolden {
        blob: "a4261a1d199534b0098ea75543369c4cb90341b0",
        bytes: 10958,
        digest: "sha256:2c5492bcb6ed9d2605bab2da50cdd21c660f8e802039b2fd9c382744db904c6c",
        crlf: 289,
        normalized_bytes: 10669,
        normalized_digest: "sha256:e68feee73157405d9ef84f4b29b4d393cc904ba5acb690802453fe063286e843",
    },
    RawGolden {
        blob: "ada03dc91483269a6c9fd0b792f3e4bf82933dcc",
        bytes: 11390,
        digest: "sha256:aa64b37a3a5d4acaab55b177e26406893830a66bd8151173d5a284e058149246",
        crlf: 273,
        normalized_bytes: 11117,
        normalized_digest: "sha256:41755d881a845c49c48f3736e79e139a9a9eff31e5e87c7e31b53f995b27a1e0",
    },
    RawGolden {
        blob: "af5f5a4e8f2fac648f4b420823c3a3bab2af47fd",
        bytes: 40025,
        digest: "sha256:e22f3d770afe7827a74ca34b2ad85393b5467dd2c3b0f97d38b778867b07ba95",
        crlf: 1024,
        normalized_bytes: 39001,
        normalized_digest: "sha256:ee37e6bbe2ad3f6274076d70a1c5d9579eb615d1fccea1e0aa3d85eecb31162e",
    },
    RawGolden {
        blob: "afe5c5b42c8ef47c330bf22df27b72845aa58fdf",
        bytes: 39669,
        digest: "sha256:3146e1dcbaabd9f46ba9782a12e788c8ae98cc46bdd523498ddbae4f0c273641",
        crlf: 1007,
        normalized_bytes: 38662,
        normalized_digest: "sha256:cee9069030da0c89c01ee949138ff3411b7e9eb14d3894b8e8242ccd0c8ec150",
    },
    RawGolden {
        blob: "aff37ea1019c9066764afa76c3735699ef7e0648",
        bytes: 48018,
        digest: "sha256:76e514d84783a041a746f10c8c7ab553005e8edaa7dd6133d0bda603c2791949",
        crlf: 961,
        normalized_bytes: 47057,
        normalized_digest: "sha256:827b167c90f5a7ed0c2ffa904935f1ac0a81dfb17dd3dd1b93c72e308ffc1e31",
    },
    RawGolden {
        blob: "b10f384ae4c239ea8dcf4b20d1d341835a7086c6",
        bytes: 11462,
        digest: "sha256:caf47f79a05ab82ba10d86f2411e06e1b21230415b9422ef209cdd0e1e18ae21",
        crlf: 274,
        normalized_bytes: 11188,
        normalized_digest: "sha256:9b0a836c8c802b5e8117e8117f818a07dd7745f6bf1b30e7bc83c3d249d7be94",
    },
    RawGolden {
        blob: "bfa158391c28c924b1d06539e1a775b6dca3edb1",
        bytes: 38813,
        digest: "sha256:623ad5bbdc3234cb378ba8020712add777c708e65340bd55b0b35d067edab3ab",
        crlf: 990,
        normalized_bytes: 37823,
        normalized_digest: "sha256:67c0d1b54b34746a7f754f7db0d661ae69d3fbe65c0718cf1ed1d20633e4e3b5",
    },
];
struct OwnerGolden {
    name: &'static str,
    support: &'static [&'static str],
    requires: &'static [&'static str],
}
const OWNERS: [OwnerGolden; 7] = [
    OwnerGolden {
        name: "editor_documents",
        support: &["1.19.2", "1.19.4"],
        requires: &[],
    },
    OwnerGolden {
        name: "editor_async_save",
        support: &["1.19.2", "1.19.4"],
        requires: &["editor_documents"],
    },
    OwnerGolden {
        name: "editor_v1_event_modifiers",
        support: &["1.19.2", "1.19.4"],
        requires: &[],
    },
    OwnerGolden {
        name: "editor_v1_adaptive_layout",
        support: &["1.19.2", "1.19.4"],
        requires: &[],
    },
    OwnerGolden {
        name: "editor_v1_widget_focus",
        support: &["1.19.2", "1.19.4"],
        requires: &[],
    },
    OwnerGolden {
        name: "editor_v1_panel_clipping",
        support: &["1.19.2", "1.19.4"],
        requires: &["workspace_panels"],
    },
    OwnerGolden {
        name: "editor_overlay_push",
        support: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        requires: &[],
    },
];
// Exact reviewed line-region witnesses, derived from raw source deltas. The
// oracle never reads a template or synthesizes another MC API implementation.
struct OwnedUnitGolden {
    target: &'static str,
    index: usize,
    old_start: usize,
    owner: &'static str,
    on: &'static str,
    off: &'static str,
}
const OWNED_UNITS: [OwnedUnitGolden; 100] = [
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 11,
        owner: "editor_documents",
        on: r###"import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 46,
        owner: "editor_documents",
        on: r###"import java.util.Optional;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 87,
        owner: "editor_documents",
        on: r###"    private Optional<Component> saveDiagnostic = Optional.empty();
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 87,
        owner: "editor_async_save",
        on: r###"    private final ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession asyncSave =
            new ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession();
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 111,
        owner: "editor_async_save",
        on: r###"        if (openContext.asynchronousSave()) {
            saveDiagnostic = Optional.of(ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession.SAVING.getComponent());
            boolean submitted = asyncSave.submit(textarea.getValue(), true, openContext::saveDocumentAsync,
                    work -> Minecraft.getInstance().execute(work), completion -> {
                        if (!openContext.saveHostIsCurrent()) return;
                        saveDiagnostic = completion.result().diagnostic();
                        if (completion.result().saved()) {
                            openContext.documentSaved(completion.submittedText());
                            if (completion.mayClose(textarea.getValue())
                                    && !openContext.detachSaveAndCloseAfterSubmission()) {
                                openContext.finishAsyncSaveClose();
                            }
                            else if (!completion.submittedText().equals(textarea.getValue())) {
                                saveDiagnostic = Optional.of(ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession
                                        .SAVED_NEWER_EDITS.getComponent());
                            }
                        }
                    });
            if (submitted && openContext.detachSaveAndCloseAfterSubmission()) {
                openContext.finishAsyncSaveClose();
            }
            return;
        }
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 111,
        owner: "editor_documents",
        on: r###"        SFMTextDocumentSaveResult result = openContext.trySaveAndClose(textarea.getValue());
        saveDiagnostic = result.diagnostic();
        SFM.LOGGER.info("SFM_TEXT_EDITOR_SAVE_COMPLETED editor=sfm:v1 saved={} diagnostic={}",
                result.saved(), result.diagnostic().map(Component::getString).orElse("none"));
"###,
        off: r###"
        openContext.onSaveAndClose(textarea.getValue());
"###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 185,
        owner: "editor_v1_event_modifiers",
        on: r###"        if (isSaveAndCloseShortcut(pKeyCode, pModifiers)) {
"###,
        off: r###"        if ((pKeyCode == GLFW.GLFW_KEY_ENTER || pKeyCode == GLFW.GLFW_KEY_KP_ENTER) && Screen.hasShiftDown()) {
"###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 261,
        owner: "editor_v1_event_modifiers",
        on: r###"    static boolean isSaveAndCloseShortcut(int keyCode, int modifiers) {
        // Use the event's state, including virtual inputs, not an OS key poll.
        return (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 262,
        owner: "editor_async_save",
        on: r###"    public void onDocumentHostClosed() {
        asyncSave.detach();
    }

    @Override
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 288,
        owner: "editor_documents",
        on: r###"        if (saveDiagnostic.isPresent()) {
            String message = saveDiagnostic.orElseThrow().getString();
            String rendered = font.plainSubstrByWidth(message, Math.max(0, width - 8));
            SFMFontUtils.draw(
                    poseStack,
                    font,
                    rendered,
                    4,
                    Math.max(1, height - 34),
                    0xFFFF7777,
                    true
            );
        }

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 289,
        owner: "editor_v1_widget_focus",
        on: r###"        // A panel-hosted editor is not Minecraft.screen: the multiplexer is.
        // Do not clear widget keyboard focus while drawing tooltips. Doing so
        // accepts one character after a click, then drops all later typing on
        // the next rendered frame. Tooltips already require a hovered widget.
"###,
        off: r###"        SFMWidgetUtils.hideTooltipsWhenNotFocused(this, this.renderables);
"###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 339,
        owner: "editor_v1_adaptive_layout",
        on: r###"        EditorLayout layout = editorLayout(this.width, this.height, this.font.lineHeight);

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 341,
        owner: "editor_v1_adaptive_layout",
        on: r###"                layout.textareaX(),
                layout.textareaY(),
                layout.textareaWidth(),
                layout.textareaHeight(),
"###,
        off: r###"                SFMTextEditScreenV1.this.width / 2 - 200,
                SFMTextEditScreenV1.this.height / 2 - 110,
                400,
                200,
"###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 353,
        owner: "editor_v1_adaptive_layout",
        on: r###"                layout.suggestionWidth(),
                layout.suggestionHeight(),
"###,
        off: r###"                180,
                this.font.lineHeight * 6,
"###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 361,
        owner: "editor_v1_adaptive_layout",
        on: r###"                        .setPosition(layout.configX(), layout.footerY())
"###,
        off: r###"                        .setPosition(this.width / 2 - 200, this.height / 2 - 100 + 195)
"###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 384,
        owner: "editor_v1_adaptive_layout",
        on: r###"                        .setPosition(layout.doneX(), layout.footerY())
                        .setSize(layout.doneWidth(), 20)
"###,
        off: r###"                        .setPosition(
                                this.width / 2 - 2 - 150,
                                this.height / 2 - 100 + 195
                        )
                        .setSize(200, 20)
"###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 396,
        owner: "editor_v1_adaptive_layout",
        on: r###"                        .setPosition(layout.cancelX(), layout.footerY())
                        .setSize(layout.cancelWidth(), 20)
"###,
        off: r###"                        .setPosition(
                                this.width / 2 - 2 + 100,
                                this.height / 2 - 100 + 195
                        )
                        .setSize(100, 20)
"###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 417,
        owner: "editor_v1_adaptive_layout",
        on: r###"    static EditorLayout editorLayout(int width, int height, int lineHeight) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int margin = Math.min(4, Math.max(0, (safeWidth - 1) / 2));
        int footerY = Math.max(0, Math.min(safeHeight - 20, safeHeight / 2 + 95));
        int textareaY = Math.max(0, Math.min(safeHeight / 2 - 110, Math.max(0, footerY - 1)));
        int textareaWidth = Math.max(1, Math.min(400, safeWidth - margin * 2));
        int textareaX = Math.max(0, (safeWidth - textareaWidth) / 2);
        int textareaHeight = Math.max(1, Math.min(200, footerY - textareaY - 5));
        int suggestionWidth = Math.max(1, Math.min(180, safeWidth));
        int suggestionHeight = Math.max(1, Math.min(Math.max(1, lineHeight) * 6, safeHeight));

        if (safeWidth >= 408) {
            return new EditorLayout(
                    textareaX,
                    textareaY,
                    textareaWidth,
                    textareaHeight,
                    suggestionWidth,
                    suggestionHeight,
                    safeWidth / 2 - 200,
                    safeWidth / 2 - 152,
                    200,
                    safeWidth / 2 + 98,
                    100,
                    footerY
            );
        }

        int configX = margin;
        int doneX = Math.min(safeWidth, configX + 18);
        int available = Math.max(2, safeWidth - margin - doneX - 2);
        int doneWidth = Math.max(1, available / 2);
        int cancelX = Math.min(safeWidth, doneX + doneWidth + 2);
        int cancelWidth = Math.max(1, safeWidth - margin - cancelX);
        return new EditorLayout(
                textareaX,
                textareaY,
                textareaWidth,
                textareaHeight,
                suggestionWidth,
                suggestionHeight,
                configX,
                doneX,
                doneWidth,
                cancelX,
                cancelWidth,
                footerY
        );
    }

    record EditorLayout(
            int textareaX,
            int textareaY,
            int textareaWidth,
            int textareaHeight,
            int suggestionWidth,
            int suggestionHeight,
            int configX,
            int doneX,
            int doneWidth,
            int cancelX,
            int cancelWidth,
            int footerY
    ) {
    }

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 458,
        owner: "editor_v1_panel_clipping",
        on: r###"        /**
         * Vanilla's implementation uses screen-global scissor coordinates and
         * disables any parent scissor.  This editor can live in a transformed
         * workspace panel, so clip through the shared nested stack instead.
         */
        @Override
        @MCVersionDependentBehaviour
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) return;
            int border = this.isFocused() ? -1 : -6250336;
            fill(poseStack, this.x, this.y, this.x + this.width, this.y + this.height, border);
            fill(
                    poseStack,
                    this.x + 1,
                    this.y + 1,
                    this.x + this.width - 1,
                    this.y + this.height - 1,
                    -16777216
            );
            SFMScissorStack.pushGui(
                    poseStack,
                    this.x + 1,
                    this.y + 1,
                    this.x + this.width - 1,
                    this.y + this.height - 1
            );
            poseStack.pushPose();
            try {
                poseStack.translate(0.0D, -this.scrollAmount(), 0.0D);
                this.renderContents(poseStack, mouseX, mouseY, partialTick);
            } finally {
                poseStack.popPose();
                SFMScissorStack.pop();
            }
            renderPanelAwareScrollbar(poseStack);
        }

        private void renderPanelAwareScrollbar(PoseStack poseStack) {
            if (!this.scrollbarVisible()) return;
            int thumbHeight = this.getScrollBarHeight();
            int left = this.x + this.width;
            int right = left + 8;
            int top = Math.max(
                    this.y,
                    (int) this.scrollAmount() * (this.height - thumbHeight)
                    / Math.max(1, this.getMaxScrollAmount()) + this.y
            );
            int bottom = top + thumbHeight;
            fill(poseStack, left, top, right, bottom, 0xFF808080);
            fill(poseStack, left, top, right - 1, bottom - 1, 0xFFC0C0C0);
        }

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 0,
        old_start: 507,
        owner: "editor_v1_widget_focus",
        on: r###"                        // Empty literal documents (including a new review note)
                        // still need a focusable insertion target. Returning false
                        // here prevents Screen from routing subsequent characters.
                        this.setFocused(true);
                        this.textField.setSelecting(true);
                        return true;
"###,
        off: r###"                        return false;
"###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 1,
        old_start: 45,
        owner: "editor_overlay_push",
        on: r###"    private final boolean pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 1,
        old_start: 54,
        owner: "editor_overlay_push",
        on: r###"        this(openContext, previousScreen, false);
    }
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 1,
        old_start: 55,
        owner: "editor_overlay_push",
        on: r###"    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen,
            boolean pushed
    ) {

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 1,
        old_start: 58,
        owner: "editor_overlay_push",
        on: r###"        this.pushed = pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 1,
        old_start: 203,
        owner: "editor_overlay_push",
        on: r###"                pushed
                        ? SFMScreenChangeHelpers::popScreen
                        : () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
        off: r###"                () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
    },
    OwnedUnitGolden {
        target: "1.19.2",
        index: 1,
        old_start: 216,
        owner: "editor_overlay_push",
        on: r###"        return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;
"###,
        off: r###"        return OpenBehaviour.Replace;
"###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 11,
        owner: "editor_documents",
        on: r###"import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 46,
        owner: "editor_documents",
        on: r###"import java.util.Optional;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 87,
        owner: "editor_documents",
        on: r###"    private Optional<Component> saveDiagnostic = Optional.empty();
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 87,
        owner: "editor_async_save",
        on: r###"    private final ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession asyncSave =
            new ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession();
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 111,
        owner: "editor_async_save",
        on: r###"        if (openContext.asynchronousSave()) {
            saveDiagnostic = Optional.of(ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession.SAVING.getComponent());
            boolean submitted = asyncSave.submit(textarea.getValue(), true, openContext::saveDocumentAsync,
                    work -> Minecraft.getInstance().execute(work), completion -> {
                        if (!openContext.saveHostIsCurrent()) return;
                        saveDiagnostic = completion.result().diagnostic();
                        if (completion.result().saved()) {
                            openContext.documentSaved(completion.submittedText());
                            if (completion.mayClose(textarea.getValue())
                                    && !openContext.detachSaveAndCloseAfterSubmission()) {
                                openContext.finishAsyncSaveClose();
                            }
                            else if (!completion.submittedText().equals(textarea.getValue())) {
                                saveDiagnostic = Optional.of(ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession
                                        .SAVED_NEWER_EDITS.getComponent());
                            }
                        }
                    });
            if (submitted && openContext.detachSaveAndCloseAfterSubmission()) {
                openContext.finishAsyncSaveClose();
            }
            return;
        }
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 111,
        owner: "editor_documents",
        on: r###"        SFMTextDocumentSaveResult result = openContext.trySaveAndClose(textarea.getValue());
        saveDiagnostic = result.diagnostic();
        SFM.LOGGER.info("SFM_TEXT_EDITOR_SAVE_COMPLETED editor=sfm:v1 saved={} diagnostic={}",
                result.saved(), result.diagnostic().map(Component::getString).orElse("none"));
"###,
        off: r###"
        openContext.onSaveAndClose(textarea.getValue());
"###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 185,
        owner: "editor_v1_event_modifiers",
        on: r###"        if (isSaveAndCloseShortcut(pKeyCode, pModifiers)) {
"###,
        off: r###"        if ((pKeyCode == GLFW.GLFW_KEY_ENTER || pKeyCode == GLFW.GLFW_KEY_KP_ENTER) && Screen.hasShiftDown()) {
"###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 261,
        owner: "editor_v1_event_modifiers",
        on: r###"    static boolean isSaveAndCloseShortcut(int keyCode, int modifiers) {
        // Use the event's state, including virtual inputs, not an OS key poll.
        return (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 262,
        owner: "editor_async_save",
        on: r###"    public void onDocumentHostClosed() {
        asyncSave.detach();
    }

    @Override
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 288,
        owner: "editor_documents",
        on: r###"        if (saveDiagnostic.isPresent()) {
            String message = saveDiagnostic.orElseThrow().getString();
            String rendered = font.plainSubstrByWidth(message, Math.max(0, width - 8));
            SFMFontUtils.draw(
                    poseStack,
                    font,
                    rendered,
                    4,
                    Math.max(1, height - 34),
                    0xFFFF7777,
                    true
            );
        }

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 289,
        owner: "editor_v1_widget_focus",
        on: r###"        // A panel-hosted editor is not Minecraft.screen: the multiplexer is.
        // Do not clear widget keyboard focus while drawing tooltips. Doing so
        // accepts one character after a click, then drops all later typing on
        // the next rendered frame. Tooltips already require a hovered widget.
"###,
        off: r###"        SFMWidgetUtils.hideTooltipsWhenNotFocused(this, this.renderables);
"###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 339,
        owner: "editor_v1_adaptive_layout",
        on: r###"        EditorLayout layout = editorLayout(this.width, this.height, this.font.lineHeight);

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 341,
        owner: "editor_v1_adaptive_layout",
        on: r###"                layout.textareaX(),
                layout.textareaY(),
                layout.textareaWidth(),
                layout.textareaHeight(),
"###,
        off: r###"                SFMTextEditScreenV1.this.width / 2 - 200,
                SFMTextEditScreenV1.this.height / 2 - 110,
                400,
                200,
"###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 353,
        owner: "editor_v1_adaptive_layout",
        on: r###"                layout.suggestionWidth(),
                layout.suggestionHeight(),
"###,
        off: r###"                180,
                this.font.lineHeight * 6,
"###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 361,
        owner: "editor_v1_adaptive_layout",
        on: r###"                        .setPosition(layout.configX(), layout.footerY())
"###,
        off: r###"                        .setPosition(this.width / 2 - 200, this.height / 2 - 100 + 195)
"###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 384,
        owner: "editor_v1_adaptive_layout",
        on: r###"                        .setPosition(layout.doneX(), layout.footerY())
                        .setSize(layout.doneWidth(), 20)
"###,
        off: r###"                        .setPosition(
                                this.width / 2 - 2 - 150,
                                this.height / 2 - 100 + 195
                        )
                        .setSize(200, 20)
"###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 396,
        owner: "editor_v1_adaptive_layout",
        on: r###"                        .setPosition(layout.cancelX(), layout.footerY())
                        .setSize(layout.cancelWidth(), 20)
"###,
        off: r###"                        .setPosition(
                                this.width / 2 - 2 + 100,
                                this.height / 2 - 100 + 195
                        )
                        .setSize(100, 20)
"###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 417,
        owner: "editor_v1_adaptive_layout",
        on: r###"    static EditorLayout editorLayout(int width, int height, int lineHeight) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int margin = Math.min(4, Math.max(0, (safeWidth - 1) / 2));
        int footerY = Math.max(0, Math.min(safeHeight - 20, safeHeight / 2 + 95));
        int textareaY = Math.max(0, Math.min(safeHeight / 2 - 110, Math.max(0, footerY - 1)));
        int textareaWidth = Math.max(1, Math.min(400, safeWidth - margin * 2));
        int textareaX = Math.max(0, (safeWidth - textareaWidth) / 2);
        int textareaHeight = Math.max(1, Math.min(200, footerY - textareaY - 5));
        int suggestionWidth = Math.max(1, Math.min(180, safeWidth));
        int suggestionHeight = Math.max(1, Math.min(Math.max(1, lineHeight) * 6, safeHeight));

        if (safeWidth >= 408) {
            return new EditorLayout(
                    textareaX,
                    textareaY,
                    textareaWidth,
                    textareaHeight,
                    suggestionWidth,
                    suggestionHeight,
                    safeWidth / 2 - 200,
                    safeWidth / 2 - 152,
                    200,
                    safeWidth / 2 + 98,
                    100,
                    footerY
            );
        }

        int configX = margin;
        int doneX = Math.min(safeWidth, configX + 18);
        int available = Math.max(2, safeWidth - margin - doneX - 2);
        int doneWidth = Math.max(1, available / 2);
        int cancelX = Math.min(safeWidth, doneX + doneWidth + 2);
        int cancelWidth = Math.max(1, safeWidth - margin - cancelX);
        return new EditorLayout(
                textareaX,
                textareaY,
                textareaWidth,
                textareaHeight,
                suggestionWidth,
                suggestionHeight,
                configX,
                doneX,
                doneWidth,
                cancelX,
                cancelWidth,
                footerY
        );
    }

    record EditorLayout(
            int textareaX,
            int textareaY,
            int textareaWidth,
            int textareaHeight,
            int suggestionWidth,
            int suggestionHeight,
            int configX,
            int doneX,
            int doneWidth,
            int cancelX,
            int cancelWidth,
            int footerY
    ) {
    }

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 458,
        owner: "editor_v1_panel_clipping",
        on: r###"        /**
         * Vanilla's implementation uses screen-global scissor coordinates and
         * disables any parent scissor.  This editor can live in a transformed
         * workspace panel, so clip through the shared nested stack instead.
         */
        @Override
        @MCVersionDependentBehaviour
        public void renderWidget(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) return;
            int x = SFMWidgetUtils.getX(this);
            int y = SFMWidgetUtils.getY(this);
            int border = this.isFocused() ? -1 : -6250336;
            fill(poseStack, x, y, x + this.width, y + this.height, border);
            fill(
                    poseStack,
                    x + 1,
                    y + 1,
                    x + this.width - 1,
                    y + this.height - 1,
                    -16777216
            );
            SFMScissorStack.pushGui(
                    poseStack,
                    x + 1,
                    y + 1,
                    x + this.width - 1,
                    y + this.height - 1
            );
            poseStack.pushPose();
            try {
                poseStack.translate(0.0D, -this.scrollAmount(), 0.0D);
                this.renderContents(poseStack, mouseX, mouseY, partialTick);
            } finally {
                poseStack.popPose();
                SFMScissorStack.pop();
            }
            renderPanelAwareScrollbar(poseStack);
        }

        private void renderPanelAwareScrollbar(PoseStack poseStack) {
            if (!this.scrollbarVisible()) return;
            int x = SFMWidgetUtils.getX(this);
            int y = SFMWidgetUtils.getY(this);
            int thumbHeight = this.getScrollBarHeight();
            int left = x + this.width;
            int right = left + 8;
            int top = Math.max(
                    y,
                    (int) this.scrollAmount() * (this.height - thumbHeight)
                    / Math.max(1, this.getMaxScrollAmount()) + y
            );
            int bottom = top + thumbHeight;
            fill(poseStack, left, top, right, bottom, 0xFF808080);
            fill(poseStack, left, top, right - 1, bottom - 1, 0xFFC0C0C0);
        }

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 0,
        old_start: 507,
        owner: "editor_v1_widget_focus",
        on: r###"                        // Empty literal documents (including a new review note)
                        // still need a focusable insertion target. Returning false
                        // here prevents Screen from routing subsequent characters.
                        this.setFocused(true);
                        this.textField.setSelecting(true);
                        return true;
"###,
        off: r###"                        return false;
"###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 1,
        old_start: 44,
        owner: "editor_overlay_push",
        on: r###"    private final boolean pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 1,
        old_start: 53,
        owner: "editor_overlay_push",
        on: r###"        this(openContext, previousScreen, false);
    }
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 1,
        old_start: 54,
        owner: "editor_overlay_push",
        on: r###"    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen,
            boolean pushed
    ) {

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 1,
        old_start: 57,
        owner: "editor_overlay_push",
        on: r###"        this.pushed = pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 1,
        old_start: 202,
        owner: "editor_overlay_push",
        on: r###"                pushed
                        ? SFMScreenChangeHelpers::popScreen
                        : () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
        off: r###"                () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
    },
    OwnedUnitGolden {
        target: "1.19.4",
        index: 1,
        old_start: 215,
        owner: "editor_overlay_push",
        on: r###"        return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;
"###,
        off: r###"        return OpenBehaviour.Replace;
"###,
    },
    OwnedUnitGolden {
        target: "1.20",
        index: 1,
        old_start: 44,
        owner: "editor_overlay_push",
        on: r###"    private final boolean pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20",
        index: 1,
        old_start: 53,
        owner: "editor_overlay_push",
        on: r###"        this(openContext, previousScreen, false);
    }
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20",
        index: 1,
        old_start: 54,
        owner: "editor_overlay_push",
        on: r###"    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen,
            boolean pushed
    ) {

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20",
        index: 1,
        old_start: 57,
        owner: "editor_overlay_push",
        on: r###"        this.pushed = pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20",
        index: 1,
        old_start: 201,
        owner: "editor_overlay_push",
        on: r###"                pushed
                        ? SFMScreenChangeHelpers::popScreen
                        : () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
        off: r###"                () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
    },
    OwnedUnitGolden {
        target: "1.20",
        index: 1,
        old_start: 214,
        owner: "editor_overlay_push",
        on: r###"        return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;
"###,
        off: r###"        return OpenBehaviour.Replace;
"###,
    },
    OwnedUnitGolden {
        target: "1.20.1",
        index: 1,
        old_start: 44,
        owner: "editor_overlay_push",
        on: r###"    private final boolean pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.1",
        index: 1,
        old_start: 53,
        owner: "editor_overlay_push",
        on: r###"        this(openContext, previousScreen, false);
    }
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.1",
        index: 1,
        old_start: 54,
        owner: "editor_overlay_push",
        on: r###"    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen,
            boolean pushed
    ) {

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.1",
        index: 1,
        old_start: 57,
        owner: "editor_overlay_push",
        on: r###"        this.pushed = pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.1",
        index: 1,
        old_start: 201,
        owner: "editor_overlay_push",
        on: r###"                pushed
                        ? SFMScreenChangeHelpers::popScreen
                        : () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
        off: r###"                () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
    },
    OwnedUnitGolden {
        target: "1.20.1",
        index: 1,
        old_start: 214,
        owner: "editor_overlay_push",
        on: r###"        return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;
"###,
        off: r###"        return OpenBehaviour.Replace;
"###,
    },
    OwnedUnitGolden {
        target: "1.20.2",
        index: 1,
        old_start: 44,
        owner: "editor_overlay_push",
        on: r###"    private final boolean pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.2",
        index: 1,
        old_start: 53,
        owner: "editor_overlay_push",
        on: r###"        this(openContext, previousScreen, false);
    }
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.2",
        index: 1,
        old_start: 54,
        owner: "editor_overlay_push",
        on: r###"    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen,
            boolean pushed
    ) {

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.2",
        index: 1,
        old_start: 57,
        owner: "editor_overlay_push",
        on: r###"        this.pushed = pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.2",
        index: 1,
        old_start: 201,
        owner: "editor_overlay_push",
        on: r###"                pushed
                        ? SFMScreenChangeHelpers::popScreen
                        : () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
        off: r###"                () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
    },
    OwnedUnitGolden {
        target: "1.20.2",
        index: 1,
        old_start: 214,
        owner: "editor_overlay_push",
        on: r###"        return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;
"###,
        off: r###"        return OpenBehaviour.Replace;
"###,
    },
    OwnedUnitGolden {
        target: "1.20.3",
        index: 1,
        old_start: 44,
        owner: "editor_overlay_push",
        on: r###"    private final boolean pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.3",
        index: 1,
        old_start: 53,
        owner: "editor_overlay_push",
        on: r###"        this(openContext, previousScreen, false);
    }
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.3",
        index: 1,
        old_start: 54,
        owner: "editor_overlay_push",
        on: r###"    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen,
            boolean pushed
    ) {

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.3",
        index: 1,
        old_start: 57,
        owner: "editor_overlay_push",
        on: r###"        this.pushed = pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.3",
        index: 1,
        old_start: 201,
        owner: "editor_overlay_push",
        on: r###"                pushed
                        ? SFMScreenChangeHelpers::popScreen
                        : () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
        off: r###"                () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
    },
    OwnedUnitGolden {
        target: "1.20.3",
        index: 1,
        old_start: 214,
        owner: "editor_overlay_push",
        on: r###"        return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;
"###,
        off: r###"        return OpenBehaviour.Replace;
"###,
    },
    OwnedUnitGolden {
        target: "1.20.4",
        index: 1,
        old_start: 44,
        owner: "editor_overlay_push",
        on: r###"    private final boolean pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.4",
        index: 1,
        old_start: 53,
        owner: "editor_overlay_push",
        on: r###"        this(openContext, previousScreen, false);
    }
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.4",
        index: 1,
        old_start: 54,
        owner: "editor_overlay_push",
        on: r###"    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen,
            boolean pushed
    ) {

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.4",
        index: 1,
        old_start: 57,
        owner: "editor_overlay_push",
        on: r###"        this.pushed = pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.20.4",
        index: 1,
        old_start: 201,
        owner: "editor_overlay_push",
        on: r###"                pushed
                        ? SFMScreenChangeHelpers::popScreen
                        : () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
        off: r###"                () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
    },
    OwnedUnitGolden {
        target: "1.20.4",
        index: 1,
        old_start: 214,
        owner: "editor_overlay_push",
        on: r###"        return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;
"###,
        off: r###"        return OpenBehaviour.Replace;
"###,
    },
    OwnedUnitGolden {
        target: "1.21.0",
        index: 1,
        old_start: 46,
        owner: "editor_overlay_push",
        on: r###"    private final boolean pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.21.0",
        index: 1,
        old_start: 55,
        owner: "editor_overlay_push",
        on: r###"        this(openContext, previousScreen, false);
    }
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.21.0",
        index: 1,
        old_start: 56,
        owner: "editor_overlay_push",
        on: r###"    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen,
            boolean pushed
    ) {

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.21.0",
        index: 1,
        old_start: 59,
        owner: "editor_overlay_push",
        on: r###"        this.pushed = pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.21.0",
        index: 1,
        old_start: 199,
        owner: "editor_overlay_push",
        on: r###"                pushed
                        ? SFMScreenChangeHelpers::popScreen
                        : () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
        off: r###"                () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
    },
    OwnedUnitGolden {
        target: "1.21.0",
        index: 1,
        old_start: 212,
        owner: "editor_overlay_push",
        on: r###"        return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;
"###,
        off: r###"        return OpenBehaviour.Replace;
"###,
    },
    OwnedUnitGolden {
        target: "1.21.1",
        index: 1,
        old_start: 46,
        owner: "editor_overlay_push",
        on: r###"    private final boolean pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.21.1",
        index: 1,
        old_start: 55,
        owner: "editor_overlay_push",
        on: r###"        this(openContext, previousScreen, false);
    }
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.21.1",
        index: 1,
        old_start: 56,
        owner: "editor_overlay_push",
        on: r###"    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen,
            boolean pushed
    ) {

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.21.1",
        index: 1,
        old_start: 59,
        owner: "editor_overlay_push",
        on: r###"        this.pushed = pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "1.21.1",
        index: 1,
        old_start: 199,
        owner: "editor_overlay_push",
        on: r###"                pushed
                        ? SFMScreenChangeHelpers::popScreen
                        : () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
        off: r###"                () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
    },
    OwnedUnitGolden {
        target: "1.21.1",
        index: 1,
        old_start: 212,
        owner: "editor_overlay_push",
        on: r###"        return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;
"###,
        off: r###"        return OpenBehaviour.Replace;
"###,
    },
    OwnedUnitGolden {
        target: "26.1.2",
        index: 1,
        old_start: 47,
        owner: "editor_overlay_push",
        on: r###"    private final boolean pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "26.1.2",
        index: 1,
        old_start: 56,
        owner: "editor_overlay_push",
        on: r###"        this(openContext, previousScreen, false);
    }
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "26.1.2",
        index: 1,
        old_start: 57,
        owner: "editor_overlay_push",
        on: r###"    public SFMTextEditScreenV2(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen,
            boolean pushed
    ) {

"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "26.1.2",
        index: 1,
        old_start: 60,
        owner: "editor_overlay_push",
        on: r###"        this.pushed = pushed;
"###,
        off: r###""###,
    },
    OwnedUnitGolden {
        target: "26.1.2",
        index: 1,
        old_start: 197,
        owner: "editor_overlay_push",
        on: r###"                pushed
                        ? SFMScreenChangeHelpers::popScreen
                        : () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
        off: r###"                () -> SFMScreenChangeHelpers.setScreen(previousScreen)
"###,
    },
    OwnedUnitGolden {
        target: "26.1.2",
        index: 1,
        old_start: 210,
        owner: "editor_overlay_push",
        on: r###"        return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;
"###,
        off: r###"        return OpenBehaviour.Replace;
"###,
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
            .wrap_err("cannot parse bounded editor screens ledger")?;
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
            self.ledger.schema == "sfm:core-editor-screens-slice@1"
                && self.ledger.context_commits == commits,
            "frozen context identity changed"
        );
        ensure!(
            self.ledger.owners.len() == OWNERS.len(),
            "editor screens owner scope changed"
        );
        for golden in &OWNERS {
            let proposed = self
                .ledger
                .owners
                .get(golden.name)
                .ok_or_else(|| eyre::eyre!("missing editor screens owner"))?;
            let actual = self
                .shared
                .features
                .0
                .get(golden.name)
                .ok_or_else(|| eyre::eyre!("editor screens owner not registered"))?;
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
                "editor screens support/prerequisite contract changed"
            );
        }
        let approved = RAW
            .iter()
            .filter(|r| r.crlf != 0)
            .map(|r| r.blob)
            .collect::<BTreeSet<_>>();
        ensure!(
            approved.len() == 18
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
        for raw in &RAW {
            self.normalized(raw.blob)?;
        }
        ensure!(
            self.ledger.files.len() == FILES.len(),
            "editor screens file scope changed"
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
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        for file in &FILES {
            let input = selection
                .inputs
                .get(file.path)
                .ok_or_else(|| eyre::eyre!("baseline editor screen omitted"))?;
            ensure!(
                input.input == file.path
                    && (input.template || file.path.ends_with(".java"))
                    && !selection.omitted_paths.contains(file.path),
                "editor screen routed away from common core"
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
    // Literal raw-owned regions, not generated template bodies, form this oracle.
    fn oracle(&self, index: usize, target: &str, flags: &[&str]) -> Result<String> {
        let source = self.historical_body("release", target, index)?;
        let lines = source.split_inclusive('\n').collect::<Vec<_>>();
        let mut output = String::new();
        let mut cursor = 0;
        for unit in OWNED_UNITS
            .iter()
            .filter(|unit| unit.target == target && unit.index == index)
        {
            let start = unit
                .old_start
                .checked_sub(1)
                .ok_or_else(|| eyre::eyre!("invalid raw unit line"))?;
            let off_lines = unit.off.bytes().filter(|b| *b == b'\n').count();
            let end = start
                .checked_add(off_lines)
                .ok_or_else(|| eyre::eyre!("unit bounds overflow"))?;
            ensure!(
                cursor <= start && end <= lines.len(),
                "overlapping raw units"
            );
            let common = lines
                .get(cursor..start)
                .ok_or_else(|| eyre::eyre!("invalid common bounds"))?;
            let old = lines
                .get(start..end)
                .ok_or_else(|| eyre::eyre!("invalid off bounds"))?;
            ensure!(old.concat() == unit.off, "raw off region changed");
            output.push_str(&common.concat());
            output.push_str(if flags.contains(&unit.owner) {
                unit.on
            } else {
                unit.off
            });
            cursor = end;
        }
        output.push_str(
            &lines
                .get(cursor..)
                .ok_or_else(|| eyre::eyre!("invalid tail bounds"))?
                .concat(),
        );
        Ok(output)
    }
}
fn inventory() -> BTreeSet<String> {
    FILES.iter().map(|f| f.path.to_owned()).collect()
}
fn file_owners(index: usize) -> Result<&'static [&'static str]> {
    match index {
        0 => Ok(&[
            "editor_documents",
            "editor_async_save",
            "editor_v1_event_modifiers",
            "editor_v1_adaptive_layout",
            "editor_v1_widget_focus",
            "editor_v1_panel_clipping",
        ]),
        1 => Ok(&["editor_overlay_push"]),
        _ => eyre::bail!("invalid editor index"),
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

fn supported_mask(target: &str, mask: u8) -> bool {
    let d2 = matches!(target, "1.19.2" | "1.19.4");
    (d2 || mask & 63 == 0) && (mask & 2 == 0 || mask & 1 != 0)
}
fn owner_flags(mask: u8) -> Vec<&'static str> {
    FLAGS
        .iter()
        .enumerate()
        .filter(|(i, _)| mask & (1 << i) != 0)
        .map(|(_, f)| *f)
        .collect()
}
fn historical_flags(name: &str) -> Result<Vec<&'static str>> {
    let (kind, target) = name
        .split_once('/')
        .ok_or_else(|| eyre::eyre!("invalid historical context"))?;
    if kind == "release" {
        return Ok(Vec::new());
    }
    ensure!(kind == "dev", "unknown witness kind");
    if matches!(target, "1.19.2" | "1.19.4") {
        Ok(FLAGS.to_vec())
    } else {
        Ok(vec![FLAGS[6]])
    }
}
// Explicit source-context prerequisite, not automatic fixture graph expansion.
fn required_context_features(flags: &[&'static str]) -> Vec<&'static str> {
    let mut context = flags.to_vec();
    if flags.contains(&FLAGS[5]) {
        context.push("workspace_panels");
    }
    context
}

#[test]
fn all_forty_raw_and_normalized_witnesses_reconstruct_through_actual_core_renderer() -> Result<()> {
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
    assert_eq!(cells, 40);
    Ok(())
}

#[test]
fn all_supported_editor_masks_match_independent_raw_owned_regions() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut profiles = 0;
    let mut cells = 0;
    for target in TARGETS {
        for mask in 0_u8..128 {
            if !supported_mask(target, mask) {
                continue;
            }
            let flags = owner_flags(mask);
            let context = fixture
                .shared
                .context(target, &required_context_features(&flags))?;
            for index in 0..FILES.len() {
                let body = fixture.render(index, &context)?;
                assert_eq!(
                    body,
                    fixture.oracle(index, target, &flags)?,
                    "{target} mask {mask} file {index}"
                );
                if index == 0 {
                    assert_eq!(
                        body.contains(
                            "import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult;"
                        ),
                        flags.contains(&FLAGS[0])
                    );
                    assert_eq!(
                        body.contains("private Optional<Component> saveDiagnostic"),
                        flags.contains(&FLAGS[0])
                    );
                    assert_eq!(
                        body.contains("SFM_TEXT_EDITOR_SAVE_COMPLETED editor=sfm:v1"),
                        flags.contains(&FLAGS[0])
                    );
                    assert_eq!(body.contains("private final ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveSession asyncSave"), flags.contains(&FLAGS[1]));
                    assert_eq!(
                        body.contains("public void onDocumentHostClosed()"),
                        flags.contains(&FLAGS[1])
                    );
                    assert_eq!(
                        body.contains("static boolean isSaveAndCloseShortcut("),
                        flags.contains(&FLAGS[2])
                    );
                    assert_eq!(
                        body.contains("record EditorLayout("),
                        flags.contains(&FLAGS[3])
                    );
                    assert_eq!(
                        body.contains("Empty literal documents"),
                        flags.contains(&FLAGS[4])
                    );
                    assert_eq!(
                        body.contains("SFMScissorStack.pushGui("),
                        flags.contains(&FLAGS[5])
                    );
                    assert_eq!(
                        body.contains("renderPanelAwareScrollbar("),
                        flags.contains(&FLAGS[5])
                    );
                } else {
                    assert_eq!(
                        body.contains("private final boolean pushed;"),
                        flags.contains(&FLAGS[6])
                    );
                    assert_eq!(
                        body.contains(
                            "return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;"
                        ),
                        flags.contains(&FLAGS[6])
                    );
                    // V2 does not acquire unimplemented read-only/document/async
                    // semantics merely because the shared open API supports them.
                    assert!(!body.contains("openContext.readOnly()"));
                    assert!(!body.contains("openContext.asynchronousSave()"));
                }
                cells += 1;
            }
            for unrelated in [
                "editor_document_panels",
                "canvas_text_editor",
                "client_theme",
                "client_actions",
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
    assert_eq!((profiles, cells), (208, 416));
    Ok(())
}

#[test]
fn editor_support_and_precise_prerequisites_reject_unreviewed_combinations() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let base = fixture.shared.context(target, &[])?;
        fixture.selection(&base)?;
        if target == "1.21.0" {
            assert_eq!(base.minecraft_version, "1.21");
        }
        let overlay = fixture.shared.context(target, &[FLAGS[6]])?;
        assert_eq!(fixture.render(0, &base)?, fixture.render(0, &overlay)?);
        assert_ne!(fixture.render(1, &base)?, fixture.render(1, &overlay)?);
        assert!(
            fixture
                .shared
                .context(target, &["unreviewed_editor_screen_owner"])
                .is_err()
        );
        if matches!(target, "1.19.2" | "1.19.4") {
            for owner in &FLAGS[..6] {
                if *owner == FLAGS[1] || *owner == FLAGS[5] {
                    continue;
                }
                fixture.shared.context(target, &[*owner])?;
            }
            assert!(fixture.shared.context(target, &[FLAGS[1]]).is_err());
            assert!(fixture.shared.context(target, &[FLAGS[5]]).is_err());
            fixture.shared.context(target, &[FLAGS[0], FLAGS[1]])?;
            let clip = fixture
                .shared
                .context(target, &[FLAGS[5], "workspace_panels"])?;
            assert_ne!(clip.features.get(FLAGS[0]), Some(&true));
            assert_ne!(clip.features.get(FLAGS[1]), Some(&true));
            assert_ne!(clip.features.get("editor_document_panels"), Some(&true));
        } else {
            for owner in &FLAGS[..6] {
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
fn async_lifecycle_is_optional_and_independent_v1_repairs_do_not_enable_documents() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in ["1.19.2", "1.19.4"] {
        let base = fixture.shared.context(target, &[])?;
        let documents = fixture.shared.context(target, &[FLAGS[0]])?;
        let synchronous = fixture.render(0, &documents)?;
        assert!(synchronous.contains("openContext.trySaveAndClose(textarea.getValue())"));
        assert!(!synchronous.contains("openContext.asynchronousSave()"));
        assert!(!synchronous.contains("onDocumentHostClosed()"));
        let asynchronous = fixture.shared.context(target, &[FLAGS[0], FLAGS[1]])?;
        let asynchronous_body = fixture.render(0, &asynchronous)?;
        assert!(asynchronous_body.contains(
            "asyncSave.submit(textarea.getValue(), true, openContext::saveDocumentAsync"
        ));
        assert!(asynchronous_body.contains("if (!openContext.saveHostIsCurrent()) return;"));
        assert!(asynchronous_body.contains("asyncSave.detach();"));
        assert_ne!(
            asynchronous.features.get("editor_document_panels"),
            Some(&true)
        );
        for owner in &FLAGS[2..6] {
            let flags = [*owner];
            let context = fixture
                .shared
                .context(target, &required_context_features(&flags))?;
            let body = fixture.render(0, &context)?;
            assert_eq!(body, fixture.oracle(0, target, &flags)?);
            assert!(body.contains("openContext.onSaveAndClose(textarea.getValue())"));
            assert!(
                !body.contains("SFMTextDocumentSaveResult")
                    && !body.contains("SFMTextDocumentSaveSession")
            );
            assert_eq!(fixture.render(1, &context)?, fixture.render(1, &base)?);
        }
    }
    Ok(())
}

#[test]
fn all_eighteen_crlf_normalizations_are_exact_and_reject_mutated_sources() -> Result<()> {
    let fixture = Fixture::load()?;
    assert!(raw_golden("0000000000000000000000000000000000000000").is_err());
    for golden in &RAW {
        let raw = fixture
            .raw
            .get(golden.blob)
            .ok_or_else(|| eyre::eyre!("raw missing"))?;
        let normalized = normalize_raw(golden, raw)?;
        assert!(golden.crlf > 0);
        assert_ne!(normalized.as_bytes(), raw);
        assert_eq!(raw.len() - normalized.len(), golden.crlf);
        let text = std::str::from_utf8(raw)?;
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(raw);
        let mut appended = raw.clone();
        appended.push(b'\n');
        for mutation in [
            bom,
            appended,
            raw[..raw.len() - 1].to_vec(),
            text.replacen("public ", "private ", 1).into_bytes(),
            text.replacen("    ", "\t", 1).into_bytes(),
            text.replace("\r\n", "\n").into_bytes(),
            text.replacen("\r\n", "\r", 1).into_bytes(),
        ] {
            assert_ne!(&mutation, raw, "raw mutation fixture unchanged");
            assert!(
                normalize_raw(golden, &mutation).is_err(),
                "mutation accepted {}",
                golden.blob
            );
        }
    }
    Ok(())
}

#[test]
fn common_edits_reach_all_targets_without_changing_editor_guards_or_original_inputs() -> Result<()>
{
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
        let new = format!("{anchor}\n// Editor-screen common-edit proof.\n");
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
    assert_eq!(cells, 40);
    Ok(())
}

#[test]
fn all_forty_offline_historical_tree_memberships_match_fixed_blob_ids() -> Result<()> {
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
    assert_eq!(cells, 40);
    Ok(())
}
