//! Genuine evaluator and action-manifest source contracts through actual core.
//! Frozen objects/region hashes are offline regression witnesses, not sources.
//! Counterfactual no-action traversal and no-frame compile are explicitly new.
//! These tests do not compile Java or assert runtime/renderer/effect liveness.
#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputVariant;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
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

const LEDGER: &str = "docs/tasks/sfm-core-frame-evaluator-manifest-slice.json";
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const TEN: [&str; 10] = [
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
const DEFINITIONS: [(&str, &[&str], &[&str]); 13] = [
    (
        "client_actions",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    ("packet_values", &["1.19.2", "1.19.4"], &[]),
    ("sfml_execution_side", &["1.19.2", "1.19.4"], &[]),
    (
        "client_manager",
        &["1.19.2", "1.19.4"],
        &[
            "sfml_execution_side",
            "client_program_consent",
            "disk_readonly_access",
        ],
    ),
    (
        "client_program_consent",
        &["1.19.2", "1.19.4"],
        &["sfml_execution_side"],
    ),
    (
        "packet_computation",
        &["1.19.2", "1.19.4"],
        &["packet_values", "runtime_resource_cleanup"],
    ),
    ("runtime_resource_cleanup", &["1.19.2", "1.19.4"], &[]),
    (
        "disk_readonly_access",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "client_program_actions",
        &["1.19.2", "1.19.4"],
        &[
            "client_actions",
            "packet_values",
            "sfml_execution_side",
            "client_manager",
            "client_program_consent",
            "packet_computation",
        ],
    ),
    (
        "client_frame_language",
        &["1.19.2", "1.19.4"],
        &[
            "client_manager",
            "sfml_execution_side",
            "client_program_consent",
        ],
    ),
    (
        "client_frame_render",
        &["1.19.2", "1.19.4"],
        &["client_frame_language", "touch_display", "image_resources"],
    ),
    ("image_resources", &["1.19.2", "1.19.4"], &["packet_values"]),
    (
        "touch_display",
        &["1.19.2", "1.19.4"],
        &["packet_values", "image_resources"],
    ),
];
const PROFILES: [(&str, &[&str]); 6] = [
    ("owner_off", &[]),
    (
        "frame_without_actions_or_render",
        &[
            "client_frame_language",
            "client_manager",
            "client_program_consent",
            "disk_readonly_access",
            "sfml_execution_side",
        ],
    ),
    (
        "actions_without_frame",
        &[
            "client_actions",
            "client_manager",
            "client_program_actions",
            "client_program_consent",
            "disk_readonly_access",
            "packet_computation",
            "packet_values",
            "runtime_resource_cleanup",
            "sfml_execution_side",
        ],
    ),
    (
        "frame_with_actions_without_render",
        &[
            "client_actions",
            "client_frame_language",
            "client_manager",
            "client_program_actions",
            "client_program_consent",
            "disk_readonly_access",
            "packet_computation",
            "packet_values",
            "runtime_resource_cleanup",
            "sfml_execution_side",
        ],
    ),
    (
        "frame_render_without_actions",
        &[
            "client_frame_language",
            "client_frame_render",
            "client_manager",
            "client_program_consent",
            "disk_readonly_access",
            "image_resources",
            "packet_values",
            "sfml_execution_side",
            "touch_display",
        ],
    ),
    (
        "frame_render_actions",
        &[
            "client_actions",
            "client_frame_language",
            "client_frame_render",
            "client_manager",
            "client_program_actions",
            "client_program_consent",
            "disk_readonly_access",
            "image_resources",
            "packet_computation",
            "packet_values",
            "runtime_resource_cleanup",
            "sfml_execution_side",
            "touch_display",
        ],
    ),
];
struct Golden {
    path: &'static str,
    owner: &'static str,
    oid: &'static str,
    raw_bytes: usize,
    raw_sha256: &'static str,
    raw_lf: usize,
    authored_bytes: usize,
    authored_sha256: &'static str,
    declaration: &'static str,
}
const GOLDENS: [Golden; 2] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientFrameEvaluator.java",
        owner: "client_frame_language",
        oid: "badd733c7343f6be8c4a24aeefbd941776b37394",
        raw_bytes: 5144,
        raw_sha256: "sha256:7ebc676240953132c3ea169c07d167287ee25f90bd9f595b234e78c4155e4f58",
        raw_lf: 95,
        authored_bytes: 7315,
        authored_sha256: "sha256:0b649fa35a918b3749b6ac5fccc6f5e9e74e4d177b975e023dd70cb43e33bc91",
        declaration: "public final class ClientFrameEvaluator",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramActionManifest.java",
        owner: "client_program_actions",
        oid: "efba229f4e6285bc2973279e09a3706122465686",
        raw_bytes: 8756,
        raw_sha256: "sha256:cdbdd3e18171d2a2e02d89bdf2c0f16538e96b8401fb12fb10779bc00898f5d4",
        raw_lf: 134,
        authored_bytes: 9073,
        authored_sha256: "sha256:7aabec5131c06094ff4399f9fbdef2078c3722d809227fda1697fb56b83201e6",
        declaration: "public record ClientProgramActionManifest(",
    },
];
struct RegionPin {
    file: usize,
    name: &'static str,
    original_bytes: usize,
    original_sha256: &'static str,
    template_bytes: usize,
    template_sha256: &'static str,
}
const REGION_PINS: [RegionPin; 21] = [
    RegionPin {
        file: 0,
        name: "value_imports",
        original_bytes: 98,
        original_sha256: "sha256:c4f846a057bf2f659443788d9ec0d3b7eed3843615eb1b646c090bbfacd390f6",
        template_bytes: 151,
        template_sha256: "sha256:e703291a08dcabdd25b6049a26bbc0e4ecafcd410a10f556edf6ea259e873a4b",
    },
    RegionPin {
        file: 0,
        name: "optional_image_entry",
        original_bytes: 174,
        original_sha256: "sha256:deb233723c9c6f90b8f55369ebfeab1d580e937548f49df93b7fbce39a8d3f0a",
        template_bytes: 403,
        template_sha256: "sha256:bdce9b9de3be4789c1fac6ff2d7ab7ce284b75fec68a1f23ba9adff7bfd3f2e7",
    },
    RegionPin {
        file: 0,
        name: "typed_action_api",
        original_bytes: 675,
        original_sha256: "sha256:38f8151114903439037eb4cc9dad5e58ad6cfa7981d97af36700813230cb1ea2",
        template_bytes: 728,
        template_sha256: "sha256:1e867728ea596dc01c3c50490d640f07c41a7ae07f86a38e84ac12a132524c90",
    },
    RegionPin {
        file: 0,
        name: "block_parameters",
        original_bytes: 174,
        original_sha256: "sha256:07d5a6d0d7023b8f1e8d9d35a909405e7c2c1753491476a9783d11f910d316ad",
        template_bytes: 353,
        template_sha256: "sha256:fbb835a3e3e46841c14f6e68ef0f68430262d165036137d35fc4165d9e1650ba",
    },
    RegionPin {
        file: 0,
        name: "render_statement",
        original_bytes: 139,
        original_sha256: "sha256:116015de06ae527d2d6934f648e97db88f29aad845b3ae8a5d0b5bf5794c0cc7",
        template_bytes: 189,
        template_sha256: "sha256:784692fd86ad9340499f2f5f350f5c89b73ff9510207ccc14aea6f5ae295d599",
    },
    RegionPin {
        file: 0,
        name: "value_statements",
        original_bytes: 1110,
        original_sha256: "sha256:aff5bfaa2c6bb28242eb67c1c2fa746bd308d93388bd1ed794e8eb59347d55c5",
        template_bytes: 1281,
        template_sha256: "sha256:49f7ed0201bb670eb8abb19c15732d6124daa89993b2b78694c0fc107b89e7af",
    },
    RegionPin {
        file: 0,
        name: "if_statement_header",
        original_bytes: 66,
        original_sha256: "sha256:3ce0126541b9e4ee855a2dc7854084e1f14e4e1a9a1ef18572674f5ab332c5b0",
        template_bytes: 221,
        template_sha256: "sha256:b944c60b54e1f34bb9750f4c3a304faf2ed27bd0972d7d3e842db69a4bc726fa",
    },
    RegionPin {
        file: 0,
        name: "branch_condition_call",
        original_bytes: 82,
        original_sha256: "sha256:a90aa7436da269b51d294b396c2c69a89f4ae623af498b18180a7ef02096221f",
        template_bytes: 220,
        template_sha256: "sha256:0749d411a2d6123f76a05e80931e9a3560b96805f63132353219b528110bd4a8",
    },
    RegionPin {
        file: 0,
        name: "branch_value_copy",
        original_bytes: 70,
        original_sha256: "sha256:c7b343e3881eeac2c6ddbc86d99b6b5255e03a9778bffe64c2440eda12cfc7e8",
        template_bytes: 172,
        template_sha256: "sha256:8ab3bc4db04d101cf0307b2d6077f8b0842e6444674af82a61bb29b0387ef0db",
    },
    RegionPin {
        file: 0,
        name: "bound_value_lookup",
        original_bytes: 279,
        original_sha256: "sha256:7aecf89d83fe8778c6c36c81cf67d943930c0cf2f486507f8489d4e2c26fab44",
        template_bytes: 332,
        template_sha256: "sha256:440003977778893b18db18af88e41c9cff05a971db979ec989881a705e19d03e",
    },
    RegionPin {
        file: 0,
        name: "condition_parameters",
        original_bytes: 114,
        original_sha256: "sha256:213a5ab8080875238416eeb4fb9067de6d26bd50b37d604b99a62e111df3b8a8",
        template_bytes: 262,
        template_sha256: "sha256:f12879d9227ed253f605212bd63731dc23925f52ceed3eff773fdbdc2f71dba4",
    },
    RegionPin {
        file: 0,
        name: "value_condition",
        original_bytes: 168,
        original_sha256: "sha256:366ed3b8c43e55add183cb16eb63918e53b62f794bde8224f73162f0b284f1f0",
        template_bytes: 221,
        template_sha256: "sha256:2cc484c90c77618bfe676e82572edd67a260d0f22988595f9580a35e3e9fc074",
    },
    RegionPin {
        file: 0,
        name: "parenthesized_condition",
        original_bytes: 81,
        original_sha256: "sha256:c1cad4ab81a8973d9da4e7fbf49771e5baaab70e9066ae017d5953bac5d0f6f7",
        template_bytes: 218,
        template_sha256: "sha256:0c34cc4f135a9b2de3c3d5a2ca08a7b95828f31b4cad41de7a88f32be26a5ac1",
    },
    RegionPin {
        file: 0,
        name: "negated_condition",
        original_bytes: 76,
        original_sha256: "sha256:16e0a523c8f96218a68bcaeec8fd2661718b52997bfc1d2c010d1f9679ce0f17",
        template_bytes: 208,
        template_sha256: "sha256:c4faae5eeaf4fe51cb61b91ebda5b6ba84a2cbd33d6768c6c23269c545d4da3b",
    },
    RegionPin {
        file: 0,
        name: "and_left_condition",
        original_bytes: 70,
        original_sha256: "sha256:1ae95ba7112d08e896c1d1e02634ca56390fdaa3edaaf505d2ef8d0bb54ceaef",
        template_bytes: 196,
        template_sha256: "sha256:2b69bad8213ca18bd259d1a576051aca41ca58b79353fdb71380fe7920aff819",
    },
    RegionPin {
        file: 0,
        name: "and_right_condition",
        original_bytes: 75,
        original_sha256: "sha256:18231ce615ffd51488937667ab1a40db64ec3fdea35f529be205032e8276eec1",
        template_bytes: 206,
        template_sha256: "sha256:3a8fbbeffd51d2923b95f495e62cbf503d1b24b23e9296efc2132609220dd445",
    },
    RegionPin {
        file: 0,
        name: "or_left_condition",
        original_bytes: 72,
        original_sha256: "sha256:64ed373293630eafdddd1309731b1e16eac1b653481d1d89d3d68b1c08b0cf3d",
        template_bytes: 200,
        template_sha256: "sha256:cc26ec8ca1a4ac154ca956e5daca098183349817fad3ae71181eaa22a742c255",
    },
    RegionPin {
        file: 0,
        name: "or_right_condition",
        original_bytes: 77,
        original_sha256: "sha256:22dcf42edcf14b942ecbebd5fa3b63663dcb38f2c41e60a61ee4cf4de1df0eeb",
        template_bytes: 210,
        template_sha256: "sha256:1e66274b08e2c785d5488d97a27be78b424b66a40bdf2ab193c32f1abf622658",
    },
    RegionPin {
        file: 1,
        name: "frame_link_entry",
        original_bytes: 547,
        original_sha256: "sha256:500af56776608494c930368965f809a8f71eac57396ce1ab67c7b3dee50debd9",
        template_bytes: 696,
        template_sha256: "sha256:74b93fd566a4d36c558ec80c1bc469f1e2a185dff684a3cd5efe69896270dc18",
    },
    RegionPin {
        file: 1,
        name: "render_capability",
        original_bytes: 127,
        original_sha256: "sha256:376fe29bed2b2bd41714cb2140f83857c578a0322c9ca70cf93c8a2c6a296f91",
        template_bytes: 177,
        template_sha256: "sha256:74d426f7f92c4ebc9206fe65537fa2fecabea63a17370370241a1df0f8077753",
    },
    RegionPin {
        file: 1,
        name: "value_statement_header",
        original_bytes: 64,
        original_sha256: "sha256:f081e877cb20b4838af6f5bd9562ee548babb349e2c4cd5223b80fa44d3be212",
        template_bytes: 182,
        template_sha256: "sha256:c90f1ba4948d046a65f890f6d2674bfeafebf30f19891dc7ef86805b6ad1e519",
    },
];
struct VariantPin {
    file: usize,
    actions: bool,
    frame: bool,
    render: bool,
    bytes: usize,
    lf: usize,
    sha256: &'static str,
    historical: bool,
}
const VARIANTS: [VariantPin; 7] = [
    VariantPin {
        file: 0,
        actions: false,
        frame: true,
        render: false,
        bytes: 2482,
        lf: 54,
        sha256: "sha256:f579637cdbe73227e7aa6957904a0fccd739c2b60d214aca667f838171a00ae2",
        historical: false,
    },
    VariantPin {
        file: 0,
        actions: true,
        frame: true,
        render: false,
        bytes: 4998,
        lf: 93,
        sha256: "sha256:02a3819953104e1e90330afe9afdcfa3dbd7dddacba1b4b85fda48816526039c",
        historical: false,
    },
    VariantPin {
        file: 0,
        actions: false,
        frame: true,
        render: true,
        bytes: 2628,
        lf: 56,
        sha256: "sha256:a6a68d2d8e8147d7f16588e4238a11b62930ab2eb7a093347cc099efcfd69640",
        historical: false,
    },
    VariantPin {
        file: 0,
        actions: true,
        frame: true,
        render: true,
        bytes: 5144,
        lf: 95,
        sha256: "sha256:7ebc676240953132c3ea169c07d167287ee25f90bd9f595b234e78c4155e4f58",
        historical: true,
    },
    VariantPin {
        file: 1,
        actions: true,
        frame: false,
        render: false,
        bytes: 8161,
        lf: 126,
        sha256: "sha256:374c5d6bba2ee53f4d09cd66d0c251d0a655e56f73b1ff0b5a0683d1ba22486d",
        historical: false,
    },
    VariantPin {
        file: 1,
        actions: true,
        frame: true,
        render: false,
        bytes: 8622,
        lf: 132,
        sha256: "sha256:19b819b89fbd314a040936bf9fe3ca24fafc456c64d7f9050303d6b524dd0364",
        historical: false,
    },
    VariantPin {
        file: 1,
        actions: true,
        frame: true,
        render: true,
        bytes: 8756,
        lf: 134,
        sha256: "sha256:cdbdd3e18171d2a2e02d89bdf2c0f16538e96b8401fb12fb10779bc00898f5d4",
        historical: true,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    contract_changes: bool,
    scope: Scope,
    definitions: BTreeMap<String, Definition>,
    context_commits: BTreeMap<String, String>,
    files: Vec<SourceEvidence>,
    body_variants: Vec<BodyEvidence>,
    current_source_profiles: BTreeMap<String, Vec<String>>,
}
#[derive(Facet)]
struct Scope {
    authored_java_files: usize,
    raw_unique_blobs: usize,
    raw_java_bytes: usize,
    authored_java_bytes: usize,
    historical_membership_cells: usize,
    present_cells: usize,
    absent_cells: usize,
    guarded_regions: usize,
    definitions: usize,
    distinct_body_variants: usize,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct SourceEvidence {
    path: String,
    owner: String,
    raw_oid: String,
    raw_bytes: usize,
    raw_sha256: String,
    raw_cr_count: usize,
    raw_lf_count: usize,
    raw_final_lf: bool,
    authored_bytes: usize,
    authored_sha256: String,
    witnesses: BTreeMap<String, Option<String>>,
    source_rule: InputVariant,
    regions: Vec<RegionEvidence>,
}
#[derive(Facet)]
struct RegionEvidence {
    name: String,
    original: String,
    template: String,
    original_bytes: usize,
    original_sha256: String,
    template_bytes: usize,
    template_sha256: String,
}
#[derive(Facet)]
struct BodyEvidence {
    path: String,
    features: BTreeMap<String, bool>,
    bytes: usize,
    sha256: String,
    lf_count: usize,
    historical: bool,
}
struct Fixture {
    core: CoreTestFixture,
    sources: BTreeMap<String, Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            128 * 1024,
        )?)?)?;
        let s = &ledger.scope;
        ensure!(
            ledger.schema == "sfm:core-frame-evaluator-manifest-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && s.authored_java_files == 2
                && s.raw_unique_blobs == 2
                && s.raw_java_bytes == 13_900
                && s.authored_java_bytes == 16_388
                && s.historical_membership_cells == 40
                && s.present_cells == 4
                && s.absent_cells == 36
                && s.guarded_regions == 21
                && s.definitions == 13
                && s.distinct_body_variants == 7
                && ledger.files.len() == 2
                && ledger.definitions.len() == 13
                && ledger.body_variants.len() == 7
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(key, value)| (key.to_owned(), value.to_owned()))
                        .collect()
                && ledger.current_source_profiles
                    == PROFILES
                        .into_iter()
                        .map(|(key, flags)| (
                            key.to_owned(),
                            flags.iter().map(|flag| (*flag).to_owned()).collect()
                        ))
                        .collect(),
            "bounded evaluator/manifest preservation scope changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let original = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing frozen model owner: {name}"))?;
            let current = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing current model owner: {name}"))?;
            ensure!(
                original.supported_targets == support
                    && original.requires == requires
                    && current.supported_targets == support
                    && current.requires == requires,
                "frozen/current evaluator contract changed: {name}"
            );
        }
        for (pin, evidence) in VARIANTS.iter().zip(&ledger.body_variants) {
            ensure!(
                evidence.path == GOLDENS[pin.file].path
                    && evidence.features
                        == BTreeMap::from([
                            ("client_program_actions".to_owned(), pin.actions),
                            ("client_frame_language".to_owned(), pin.frame),
                            ("client_frame_render".to_owned(), pin.render)
                        ])
                    && evidence.bytes == pin.bytes
                    && evidence.sha256 == pin.sha256
                    && evidence.lf_count == pin.lf
                    && evidence.historical == pin.historical,
                "historical/counterfactual output pin changed"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|g| g.oid.to_owned()).collect(),
        )?;
        let mut sources = BTreeMap::new();
        for (index, (g, evidence)) in GOLDENS.iter().zip(&ledger.files).enumerate() {
            ensure!(
                evidence.path == g.path
                    && evidence.owner == g.owner
                    && evidence.raw_oid == g.oid
                    && evidence.raw_bytes == g.raw_bytes
                    && evidence.raw_sha256 == g.raw_sha256
                    && evidence.raw_cr_count == 0
                    && evidence.raw_lf_count == g.raw_lf
                    && evidence.raw_final_lf
                    && evidence.authored_bytes == g.authored_bytes
                    && evidence.authored_sha256 == g.authored_sha256
                    && evidence.witnesses
                        == CONTEXTS
                            .into_iter()
                            .map(|(name, _)| (
                                name.to_owned(),
                                present(name).then(|| g.oid.to_owned())
                            ))
                            .collect(),
                "exact evaluator witness identity changed"
            );
            verify_raw(&raw[g.oid], g)?;
            let source = core.read_source(g.path)?;
            ensure!(
                source.len() == g.authored_bytes
                    && sha256(&source) == g.authored_sha256
                    && !source.contains(&b'\r')
                    && source.ends_with(b"\n"),
                "actual core must be promoted from this exact reviewed template"
            );
            let pins = REGION_PINS
                .iter()
                .filter(|pin| pin.file == index)
                .collect::<Vec<_>>();
            ensure!(
                pins.len() == evidence.regions.len(),
                "region ownership count changed"
            );
            let mut reconstructed = std::str::from_utf8(&raw[g.oid])?.to_owned();
            for (pin, region) in pins.iter().zip(&evidence.regions) {
                ensure!(
                    region.name == pin.name
                        && region.original_bytes == pin.original_bytes
                        && region.original_sha256 == pin.original_sha256
                        && region.template_bytes == pin.template_bytes
                        && region.template_sha256 == pin.template_sha256
                        && region.original.len() == pin.original_bytes
                        && sha256(region.original.as_bytes()) == pin.original_sha256
                        && region.template.len() == pin.template_bytes
                        && sha256(region.template.as_bytes()) == pin.template_sha256
                        && reconstructed.matches(&region.original).count() == 1,
                    "byte-bound original/guarded region changed"
                );
                reconstructed = reconstructed.replacen(&region.original, &region.template, 1);
            }
            ensure!(
                reconstructed.as_bytes() == source,
                "only reviewed raw regions may change"
            );
            let rules = core.metadata.source_rules.get(g.path).ok_or_else(|| {
                eyre::eyre!("root must promote exact class membership: {}", g.path)
            })?;
            ensure!(
                rules.len() == 1
                    && rules[0] == evidence.source_rule
                    && rules[0].input == g.path
                    && rules[0].when.targets == D2
                    && rules[0].when.all_features == [g.owner]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "frame/actions class membership cannot force optional behavior"
            );
            sources.insert(g.path.to_owned(), source);
        }
        Ok(Self { core, sources, raw })
    }
    fn inventory(&self) -> BTreeSet<String> {
        self.sources.keys().cloned().collect()
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if !selected.inputs.contains_key(path) {
            ensure!(
                selected.omitted_paths.contains(path),
                "unaccounted model omission"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.sources[path])?,
            context,
        )?))
    }
    fn provider(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selected = select_core_inputs(
            &self.core.metadata,
            context,
            &BTreeSet::from([path.to_owned()]),
        )?;
        let Some(input) = selected.inputs.get(path) else {
            ensure!(
                selected.omitted_paths.contains(path),
                "unaccounted provider omission"
            );
            return Ok(None);
        };
        let bytes = self.core.read_source(&input.input)?;
        Ok(Some(render_java_source(
            std::str::from_utf8(&bytes)?,
            context,
        )?))
    }
    fn bounded_metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|path, _| self.sources.contains_key(path));
        metadata
    }
    fn write_sources(&self, root: &Path) -> Result<()> {
        for (path, bytes) in &self.sources {
            let destination = root.join(path);
            fs::create_dir_all(
                destination
                    .parent()
                    .ok_or_else(|| eyre::eyre!("fixed source has no parent"))?,
            )?;
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
                    "actual project bytes differ across isolated masks"
                );
            } else {
                fs::create_dir_all(
                    destination
                        .parent()
                        .ok_or_else(|| eyre::eyre!("fixed project source has no parent"))?,
                )?;
                fs::write(destination, bytes)?;
            }
        }
        Ok(())
    }
}
fn present(name: &str) -> bool {
    matches!(name, "dev/1.19.2" | "dev/1.19.4")
}
fn verify_raw(bytes: &[u8], g: &Golden) -> Result<()> {
    ensure!(
        bytes.len() == g.raw_bytes
            && sha256(bytes) == g.raw_sha256
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && bytes.iter().filter(|byte| **byte == b'\n').count() == g.raw_lf
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw model mutation; no normalization is approved"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn body_pin(index: usize, context: &ProjectionContext) -> Result<&'static VariantPin> {
    VARIANTS
        .iter()
        .find(|pin| {
            pin.file == index
                && pin.actions == context.features["client_program_actions"]
                && pin.frame == context.features["client_frame_language"]
                && pin.render == context.features["client_frame_render"]
        })
        .ok_or_else(|| eyre::eyre!("selected body has no explicit reviewed golden"))
}
fn assert_body(body: &str, pin: &VariantPin) {
    assert_eq!(
        (
            body.len(),
            sha256(body.as_bytes()),
            body.bytes().filter(|byte| *byte == b'\n').count()
        ),
        (pin.bytes, pin.sha256.to_owned(), pin.lf)
    );
    assert!(!body.contains("{%"));
}

#[test]
fn two_providers_reconstruct_forty_real_historical_cells_and_raw_full_bodies() -> Result<()> {
    let f = Fixture::load()?;
    let paths = GOLDENS
        .iter()
        .map(|g| format!("platform/minecraft/{}", g.path))
        .collect::<Vec<_>>();
    let mut counts = (0, 0);
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
            output.status.success() && output.stdout.len() <= 4096 && output.stderr.len() <= 4096,
            "bounded model tree query failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed model tree record"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|candidate| candidate == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected model source mode/path/identity"
            );
        }
        let expected = if present(name) {
            GOLDENS
                .iter()
                .map(|g| (format!("platform/minecraft/{}", g.path), g.oid.to_owned()))
                .collect()
        } else {
            BTreeMap::new()
        };
        assert_eq!(actual, expected, "historical membership: {name}");
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("fixed context malformed"))?;
        let context = f
            .core
            .context(target, if present(name) { PROFILES[5].1 } else { &[] })?;
        for g in &GOLDENS {
            if present(name) {
                let body = f
                    .render(g.path, &context)?
                    .ok_or_else(|| eyre::eyre!("full model source omitted"))?;
                assert_eq!(body.as_bytes(), f.raw[g.oid]);
                counts.0 += 1;
            } else {
                assert!(f.render(g.path, &context)?.is_none());
                counts.1 += 1;
            }
        }
    }
    assert_eq!(counts, (4, 36));
    Ok(())
}

#[test]
fn six_dependency_closed_profiles_preserve_seven_independent_body_variants() -> Result<()> {
    let f = Fixture::load()?;
    let mut counts = (0, 0);
    for target in D2 {
        for (_, flags) in PROFILES {
            let context = f.core.context(target, flags)?;
            let mut descriptive = context.clone();
            descriptive.environment = "release".to_owned();
            descriptive.preset = "only-description".to_owned();
            descriptive.projection_key = "independent/frame-and-action".to_owned();
            for (index, g) in GOLDENS.iter().enumerate() {
                let body = f.render(g.path, &context)?;
                assert_eq!(body.is_some(), context.features[g.owner]);
                assert_eq!(body, f.render(g.path, &descriptive)?);
                if let Some(body) = body {
                    assert_body(&body, body_pin(index, &context)?);
                    counts.0 += 1;
                } else {
                    counts.1 += 1;
                }
            }
        }
        let actions = f.core.context(target, PROFILES[2].1)?;
        assert!(!actions.features["client_frame_language"]);
        assert!(!actions.features["client_frame_render"]);
        let frame = f.core.context(target, PROFILES[1].1)?;
        for absent in [
            "client_program_actions",
            "client_actions",
            "packet_computation",
            "packet_values",
            "client_frame_render",
            "touch_display",
            "image_resources",
        ] {
            assert!(!frame.features[absent]);
        }
        let render = f.core.context(target, PROFILES[4].1)?;
        assert!(!render.features["client_program_actions"]);
        for context in [actions, frame, render] {
            for absent in [
                "client_program_signing",
                "multiplayer_packets",
                "client_inbox",
            ] {
                assert!(!context.features[absent]);
            }
        }
    }
    assert_eq!(counts, (14, 10));
    Ok(())
}

#[test]
fn value_free_traversal_and_optional_render_keep_original_shared_algorithm() -> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        for (_, flags) in PROFILES.into_iter().skip(1) {
            let context = f.core.context(target, flags)?;
            let Some(body) = f.render(GOLDENS[0].path, &context)? else {
                continue;
            };
            for shared in [
                "public static Optional<ResourceLocation> evaluate(FrameTrigger trigger, long frameIndex)",
                "if (frameIndex < 0) throw new IllegalArgumentException(\"Frame index must be non-negative\")",
                "Optional<ResourceLocation> last = Optional.empty();",
                "for (Statement statement : block.statements()) {",
                "? branch.trueBlock() : branch.falseBlock(), frameIndex,",
                "if (nested.isPresent()) last = nested;",
                "return last;",
                "if (condition instanceof BoolFrameModulo frame) return frame.testFrame(frameIndex);",
                "if (condition instanceof BoolTrue) return true;",
                "if (condition instanceof BoolFalse) return false;",
                "Unexpected client frame statement: ",
                "Unexpected client frame condition: ",
            ] {
                assert!(
                    body.contains(shared),
                    "original traversal anchor changed: {shared}"
                );
            }
            if context.features["client_program_actions"] {
                for typed in [
                    "public interface Actions",
                    "public record Evaluation(",
                    "public static Evaluation evaluate(",
                    "Map<String, SFMValue> values",
                    "ClientValueExpression.JsonLiteral",
                    "ClientValueExpression.Field",
                    "ClientValueExpression.Invoke",
                    "statement instanceof LetStatement let",
                    "condition instanceof BoolClientValueEquals comparison",
                    "new HashMap<>(values), actions, renderAllowed",
                    "MAX_VARIABLES",
                    "64 * 1024",
                    "Client value binding budget exceeded",
                ] {
                    assert!(
                        body.contains(typed),
                        "original typed evaluation changed: {typed}"
                    );
                }
            } else {
                for absent in [
                    "SFMValue",
                    "SFMValueSchema",
                    "Actions actions",
                    "public interface Actions",
                    "public record Evaluation(",
                    "ClientProgramActionManifest",
                    "LetStatement",
                    "ClientValueExpression",
                    "BoolClientValueEquals",
                    "new HashMap",
                    "Map<String",
                ] {
                    assert!(
                        !body.contains(absent),
                        "frame-only traversal leaks typed provider: {absent}"
                    );
                }
                assert!(body.contains("return evaluateBlock(trigger.block(), frameIndex, true);"));
                assert!(body.contains(
                    "evaluateBlock(Block block, long frameIndex, boolean renderAllowed)"
                ));
                assert!(body.contains("evaluateCondition(BoolExpr condition, long frameIndex)"));
                assert!(
                    body.contains("return evaluateCondition(parenthesized.inner(), frameIndex);")
                );
                assert!(body.contains("return !evaluateCondition(negated.inner(), frameIndex);"));
                assert!(body.contains("&& evaluateCondition(both.right(), frameIndex);"));
                assert!(body.contains("|| evaluateCondition(either.right(), frameIndex);"));
            }
            if context.features["client_frame_render"] {
                assert!(body.contains("statement instanceof RenderImageStatement render"));
                assert!(body.contains("if (renderAllowed) last = Optional.of(render.image());"));
            } else {
                assert!(!body.contains("RenderImageStatement"));
            }
            assert!(!body.contains("ClientManagerFrameRuntime"));
            assert!(!body.contains("SFMClientProgramActionDispatcher"));
        }
    }
    Ok(())
}

#[test]
fn manifest_no_frame_compile_is_unavailable_but_typed_and_scope_api_is_preserved() -> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        for (_, flags) in [PROFILES[2], PROFILES[3], PROFILES[5]] {
            let context = f.core.context(target, flags)?;
            let body = f
                .render(GOLDENS[1].path, &context)?
                .ok_or_else(|| eyre::eyre!("manifest source omitted"))?;
            for original in [
                "Set.copyOf(capabilities)",
                "Set.copyOf(values)",
                "scopes = Map.copyOf(copied)",
                "MAX_VARIABLES = 64",
                "MAX_ACTIONS = 32",
                "MAX_SCOPES = 64",
                "ClientProgramConsentStore.MAX_CAPABILITIES",
                "Action has no typed program handler: ",
                "descriptor.actionId().equals(invoke.action())",
                "descriptor.executionSide() != SFMClientActionDescriptor.ExecutionSide.CLIENT",
                "Action arguments must currently have a statically resolvable value and target: ",
                "descriptor.checkInput(constant)",
                "permissions.add(descriptor.controlPermission())",
                "subjects.forEach(scope -> permissions.add(scope.permission()))",
                "SFMClientProgramActionDispatcher.resultSchema(descriptor)",
                "scopes.size() > MAX_ACTIONS",
                "mapToInt(Set::size).sum() > MAX_SCOPES",
                "variables.size() > MAX_VARIABLES",
                "static String key(String name)",
                "public static SFMValue field(SFMValue value, String name)",
                "Field is absent from client value: ",
                "Field is not declared by the client value schema: ",
            ] {
                assert!(
                    body.contains(original),
                    "manifest intrinsic API changed: {original}"
                );
            }
            if context.features["client_frame_language"] {
                assert!(body.contains("trigger instanceof FrameTrigger frame"));
                assert!(body.contains(
                    "link(frame.block(), new HashMap<>(), actions, permissions, scopes);"
                ));
                assert!(
                    body.contains("return new ClientProgramActionManifest(permissions, scopes);")
                );
            } else {
                assert!(!body.contains("FrameTrigger"));
                let unavailable = "public static ClientProgramActionManifest compile(Program program, SFMClientProgramActionDispatcher.Lookup actions) {\n        throw new IllegalArgumentException(\"Client program requires frame triggers\");\n    }";
                assert!(body.contains(unavailable));
                assert!(
                    !body.contains("return new ClientProgramActionManifest(permissions, scopes);")
                );
            }
            assert_eq!(
                body.contains("permissions.add(ClientProgramConsentGate.RENDER);"),
                context.features["client_frame_render"]
            );
            assert_eq!(
                body.contains("RenderImageStatement"),
                context.features["client_frame_render"]
            );
        }
    }
    Ok(())
}

#[test]
fn selected_real_providers_and_the_pending_dispatcher_have_matching_source_signatures() -> Result<()>
{
    let f = Fixture::load()?;
    for target in D2 {
        for (_, flags) in PROFILES.into_iter().skip(1) {
            let context = f.core.context(target, flags)?;
            let frame = context.features["client_frame_language"];
            let actions = context.features["client_program_actions"];
            let render = context.features["client_frame_render"];
            for (path, selected, anchors) in [
                (
                    "src/main/java/ca/teamdman/sfml/ast/FrameTrigger.java",
                    frame,
                    &[
                        "public record FrameTrigger(",
                        "MAX_BODY_NODES = 512",
                        "MAX_NESTING = 32",
                        "checkBudget(",
                        "validateBlock(",
                    ][..],
                ),
                (
                    "src/main/java/ca/teamdman/sfml/ast/RenderImageStatement.java",
                    render,
                    &[
                        "public record RenderImageStatement(",
                        "ResourceLocation image",
                        "String binding",
                    ][..],
                ),
                (
                    "src/main/java/ca/teamdman/sfml/ast/ClientValueExpression.java",
                    actions,
                    &[
                        "public sealed interface ClientValueExpression",
                        "record JsonLiteral(",
                        "record Invoke(",
                        "record Field(",
                    ][..],
                ),
                (
                    "src/main/java/ca/teamdman/sfml/ast/BoolClientValueEquals.java",
                    actions,
                    &["public record BoolClientValueEquals(", "SFMValue expected"][..],
                ),
                (
                    "src/main/java/ca/teamdman/sfml/ast/LetStatement.java",
                    context.features["packet_computation"],
                    &[
                        "public record LetStatement(",
                        "ProgramValueExpression expression",
                    ][..],
                ),
                (
                    "src/main/java/ca/teamdman/sfm/client/action/SFMClientProgramActionDispatcher.java",
                    actions,
                    &[
                        "public record Binding(",
                        "public interface Lookup",
                        "public static SFMValueSchema resultSchema(",
                    ][..],
                ),
            ] {
                let body = f.provider(path, &context)?;
                assert_eq!(body.is_some(), selected, "real provider owner: {path}");
                if let Some(body) = body {
                    for anchor in anchors {
                        assert!(body.contains(anchor), "real signature {path}: {anchor}");
                    }
                }
            }
            if actions {
                for (path, anchors) in [
                    (
                        "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionDescriptor.java",
                        &[
                            "public record SFMClientActionDescriptor(",
                            "public record DataScope(",
                            "checkInput(",
                            "checkResult(",
                        ][..],
                    ),
                    (
                        "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentStore.java",
                        &["MAX_CAPABILITIES = 64"][..],
                    ),
                    (
                        "src/main/java/ca/teamdman/sfm/common/value/SFMValueSchema.java",
                        &[
                            "interface SFMValueSchema",
                            "boundedEncodedBytes(",
                            "canonicalActionJson(",
                        ][..],
                    ),
                ] {
                    let body = f
                        .provider(path, &context)?
                        .ok_or_else(|| eyre::eyre!("actual typed prerequisite missing: {path}"))?;
                    for anchor in anchors {
                        assert!(body.contains(anchor));
                    }
                }
            }
            let trigger = f.provider(
                "src/main/java/ca/teamdman/sfml/ast/FrameTrigger.java",
                &context,
            )?;
            if let Some(trigger) = trigger {
                assert_eq!(trigger.contains("LetStatement"), actions);
                assert_eq!(trigger.contains("BoolClientValueEquals"), actions);
                assert_eq!(trigger.contains("RenderImageStatement"), render);
                assert!(trigger.contains("Client frame body cannot execute server statement: "));
                assert!(trigger.contains("Client frame condition cannot read server state: "));
            }
        }
    }
    Ok(())
}

#[test]
fn missing_prerequisites_and_keys_unsupported_targets_and_raw_mutations_are_refused() -> Result<()>
{
    let f = Fixture::load()?;
    let mut refusals = 0;
    for target in D2 {
        for owner in [
            "client_frame_language",
            "client_frame_render",
            "client_program_actions",
        ] {
            for required in &f.core.features.0[owner].requires {
                let flags = PROFILES[5]
                    .1
                    .iter()
                    .copied()
                    .filter(|flag| *flag != required.as_str())
                    .collect::<Vec<_>>();
                assert!(
                    f.core.context(target, &flags).is_err(),
                    "missing {owner}: {required}"
                );
                refusals += 1;
            }
        }
        for key in [
            "client_frame_language",
            "client_program_actions",
            "client_frame_render",
        ] {
            let mut context = f.core.context(target, PROFILES[5].1)?;
            assert_eq!(context.features.remove(key), Some(true));
            assert!(select_core_inputs(&f.core.metadata, &context, &f.inventory()).is_err());
        }
        for (index, key) in [
            (0, "client_program_actions"),
            (0, "client_frame_render"),
            (1, "client_frame_language"),
            (1, "client_frame_render"),
        ] {
            let mut context = f.core.context(target, PROFILES[5].1)?;
            context.features.remove(key);
            assert!(
                render_java_source(
                    std::str::from_utf8(&f.sources[GOLDENS[index].path])?,
                    &context
                )
                .is_err()
            );
        }
    }
    assert_eq!(refusals, 24);
    for target in &TEN[2..] {
        for profile in &PROFILES[1..] {
            assert!(f.core.context(target, profile.1).is_err());
        }
        for g in &GOLDENS {
            assert!(f.render(g.path, &f.core.context(target, &[])?)?.is_none());
        }
    }
    for g in &GOLDENS {
        let raw = &f.raw[g.oid];
        for mutation in [
            std::str::from_utf8(raw)?.replace('\n', "\r\n").into_bytes(),
            raw[..raw.len() - 1].to_vec(),
            [raw.as_slice(), b"\n"].concat(),
            [b" ".as_slice(), raw.as_slice()].concat(),
        ] {
            assert!(verify_raw(&mutation, g).is_err());
        }
    }
    Ok(())
}

#[test]
fn omission_before_read_and_auto_java_collection_use_the_real_scoped_selector() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    for g in &GOLDENS {
        let destination = root.join(g.path);
        fs::create_dir_all(destination.parent().expect("fixed model source parent"))?;
        fs::write(destination, b"{% if features.unreviewed_owner %}\n\xff")?;
    }
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    let mut metadata = f.bounded_metadata();
    for target in D2 {
        for flags in [
            &[][..],
            &[
                "packet_computation",
                "packet_values",
                "runtime_resource_cleanup",
            ][..],
        ] {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for g in &GOLDENS {
                assert!(selected.omitted_paths.contains(g.path));
                assert!(!artifacts.contains_key(g.path));
            }
        }
    }
    f.write_sources(&root)?;
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let mut outputs = 0;
    for target in D2 {
        for (_, flags) in PROFILES {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for (index, g) in GOLDENS.iter().enumerate() {
                let Some(artifact) = artifacts.get(g.path) else {
                    assert!(selected.omitted_paths.contains(g.path));
                    continue;
                };
                let (_, body) = std::str::from_utf8(&artifact.output_bytes)?
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("generated Java banner missing"))?;
                assert_body(body, body_pin(index, &context)?);
                assert!(artifact.overlay.is_none());
                assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{}", g.path));
                outputs += 1;
            }
        }
    }
    assert_eq!(outputs, 14);
    // Only owned sources + exact existing Gradle inputs are collected here.
    // No FrameRuntime, renderer host, authority mock or synthetic registry exists.
    Ok(())
}

#[test]
fn genuine_common_edits_reach_both_versions_and_every_selected_owner_mask() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    for g in &GOLDENS {
        let source = std::str::from_utf8(&f.sources[g.path])?;
        assert_eq!(source.matches(g.declaration).count(), 1);
        let edited = source.replacen(
            g.declaration,
            &format!("// Isolated common frame-linker edit.\n{}", g.declaration),
            1,
        );
        fs::write(root.join(g.path), edited)?;
    }
    let metadata = f.bounded_metadata();
    let inventory = discover_core_source_files(&root)?;
    let mut outputs = 0;
    for target in D2 {
        for (_, flags) in PROFILES {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for g in &GOLDENS {
                let Some(artifact) = artifacts.get(g.path) else {
                    assert!(selected.omitted_paths.contains(g.path));
                    continue;
                };
                let (_, body) = std::str::from_utf8(&artifact.output_bytes)?
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("generated shared edit banner missing"))?;
                let original = std::str::from_utf8(&f.sources[g.path])?;
                let edited = original.replacen(
                    g.declaration,
                    &format!("// Isolated common frame-linker edit.\n{}", g.declaration),
                    1,
                );
                assert_eq!(body, render_java_source(&edited, &context)?);
                assert!(body.contains("// Isolated common frame-linker edit."));
                outputs += 1;
            }
        }
    }
    assert_eq!(outputs, 14);
    for (path, original) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *original);
    }
    Ok(())
}
