//! Frozen source-only Buffer/Manager migration goldens.
//!
//! Actual core membership and production Liquid rendering reconstruct 80 exact
//! raw or narrowly CRLF-normalized witnesses. Historical blobs are bounded,
//! offline test oracles, never production generation sources. Independent owner
//! masks exercise current real prerequisites without relaxing CoreTestFixture.
//! No Java compilation, persistent-world or capability-lifecycle claim is made.
//! Deliberate later core edits require reviewing these migration goldens.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::projection_catalog::SUPPORTED_TARGETS;
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

const LEDGER: &str = "docs/tasks/sfm-core-buffer-manager-slice.json";
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;
const MAX_SOURCE_BYTES: u64 = 128 * 1024;
const MAX_TREE_BYTES: u64 = 16 * 1024;
const NORMALIZATION: &str = "sfm:java_lf_final_newline@1";
const BLOCK: &str = "src/main/java/ca/teamdman/sfm/common/block/BufferBlock.java";
const ENTITY: &str = "src/main/java/ca/teamdman/sfm/common/blockentity/BufferBlockEntity.java";
const CONTENTS: &str =
    "src/main/java/ca/teamdman/sfm/common/blockentity/BufferBlockEntityContents.java";
const MANAGER: &str = "src/main/java/ca/teamdman/sfm/common/blockentity/ManagerBlockEntity.java";
// Reconstruct only the three reviewed Liquid whitespace edits. The original
// hash below still rejects every other template change, including comments.
pub(super) fn historical_manager_template(current: &[u8]) -> Result<Vec<u8>> {
    let mut source = std::str::from_utf8(current)?.to_owned();
    let gap = "{% case minecraft_version %}\n{% when \"26.1.2\" %}\n\n{% else %}\n{% case minecraft_version %}\n{% when \"1.21\", \"1.21.0\", \"1.21.1\" %}\n\n{% else %}\n{% endcase %}\n{% endcase %}\n";
    for marker in [
        "    @Override\n    public ItemStack removeItem(",
        "    @Override\n    protected AbstractContainerMenu createMenu(",
    ] {
        ensure!(
            source.matches(marker).count() == 1,
            "manager whitespace anchor changed"
        );
        source = source.replacen(marker, &format!("{gap}{marker}"), 1);
    }
    let marker = "    protected void saveAdditional(CompoundTag tag) {\n\n";
    ensure!(
        source.matches(marker).count() == 1,
        "manager save anchor changed"
    );
    source = source.replacen(marker, "    protected void saveAdditional(CompoundTag tag) {\n{% endcase %}\n\n{% case minecraft_version %}\n{% when \"1.21\", \"1.21.0\", \"1.21.1\" %}\n{% else %}\n", 1);
    ensure!(
        sha256(source.as_bytes())
            == "sha256:6496f23d64dcdb3825ecbb7ed3cbec1041958ca53947ed058b5954caffa03b93",
        "manager historical template changed outside reviewed whitespace edits"
    );
    Ok(source.into_bytes())
}
const PATHS: [&str; 4] = [BLOCK, ENTITY, CONTENTS, MANAGER];
const OWNERS: [&str; 4] = [
    "redstone_buffer_storage",
    "image_resources",
    "buffer_image_persistence",
    "computercraft",
];
const PINNED_CORE: [(&str, &str, u64); 4] = [
    (
        BLOCK,
        "sha256:89b55d4c76f79d86c81453c080f2752c19f5e23605db8a6cf5db574118880254",
        6702,
    ),
    (
        ENTITY,
        "sha256:85aba6d6b4ee3a4e89748b01fd6c62950a19ffeaa23d05170f067c3349dfcf55",
        9368,
    ),
    (
        CONTENTS,
        "sha256:9aa704b0f6d3437f1a720d981f9bce0efc300ed6032920dfe714982e01d489b9",
        6570,
    ),
    (
        MANAGER,
        "sha256:6496f23d64dcdb3825ecbb7ed3cbec1041958ca53947ed058b5954caffa03b93",
        26108,
    ),
];
const PINNED_CONTEXTS: [(&str, &str); 20] = [
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

#[derive(Clone, Copy)]
struct RawSpec {
    path: &'static str,
    blob: &'static str,
    digest: &'static str,
    bytes: u64,
    crlf_count: usize,
    expected_digest: &'static str,
    expected_bytes: u64,
    contexts: &'static [&'static str],
}
const RAW_SPECS: [RawSpec; 18] = [
    RawSpec {
        path: BLOCK,
        blob: "6488127cbd4675aecc593e61328e26cd8ae3c672",
        digest: "sha256:c3671860e532b0c0fed52fb4c53211f3d58f501b59229638b20ae018c8d855bc",
        bytes: 5929,
        crlf_count: 159,
        expected_digest: "sha256:2807925f1216bf8927f36486d58d1755764031cdf8d202dae63474f6782457e4",
        expected_bytes: 5770,
        contexts: &["dev/1.19.2", "dev/1.19.4"],
    },
    RawSpec {
        path: BLOCK,
        blob: "caf51c2acea22cfb8a0850e92c8af5804236aff6",
        digest: "sha256:f956b1bdf1b0661c4d900080ae3fedd61521060f5600915220149eb35ea7a73b",
        bytes: 4797,
        crlf_count: 138,
        expected_digest: "sha256:32370ea1cbb81d4397caef8c8e92ee9d4b584e58b4ed73f731fd203e18147d64",
        expected_bytes: 4659,
        contexts: &[
            "dev/1.20",
            "dev/1.20.1",
            "dev/1.20.2",
            "release/1.19.2",
            "release/1.19.4",
            "release/1.20",
            "release/1.20.1",
            "release/1.20.2",
        ],
    },
    RawSpec {
        path: BLOCK,
        blob: "7624ecbb0dbb14c474f158fea60f68e3e37369e8",
        digest: "sha256:b990aa7adafec05d32259d57a3e7a09421946015c2363f6d45a6249271a837a3",
        bytes: 5070,
        crlf_count: 145,
        expected_digest: "sha256:ff261340736b398dda9614e64477c54103a0e697e268ea7c1ce1da0805ce0ec4",
        expected_bytes: 4925,
        contexts: &[
            "dev/1.20.3",
            "dev/1.20.4",
            "dev/1.21.0",
            "dev/1.21.1",
            "dev/26.1.2",
            "release/1.20.3",
            "release/1.20.4",
            "release/1.21.0",
            "release/1.21.1",
            "release/26.1.2",
        ],
    },
    RawSpec {
        path: ENTITY,
        blob: "7ecb7437455d11f06c712f5002d6715e0df53b0b",
        digest: "sha256:dc87e4d8dd7e2a7d35290afe88a00e98d035a05855c095fbce9f411e43809ecd",
        bytes: 7008,
        crlf_count: 83,
        expected_digest: "sha256:cd5dcce50211d16a3ee9f9bc6ba75eeb705c21b5c553a8151516e87fb7240b75",
        expected_bytes: 6925,
        contexts: &["dev/1.19.2", "dev/1.19.4"],
    },
    RawSpec {
        path: ENTITY,
        blob: "2c77aac01c654b8d31bf79ffe01149289e179915",
        digest: "sha256:c716fa1baba38ce5d3e6de33684b16107ca8f22a559f7119cb563bf52afd111a",
        bytes: 3772,
        crlf_count: 96,
        expected_digest: "sha256:e7cd27e9314a6bfea14de9893018b74b1c1e9130c507a60abfb46a7236d293d9",
        expected_bytes: 3676,
        contexts: &[
            "dev/1.20",
            "dev/1.20.1",
            "release/1.19.2",
            "release/1.19.4",
            "release/1.20",
            "release/1.20.1",
        ],
    },
    RawSpec {
        path: ENTITY,
        blob: "392187b6a062550b700ea32bb2a2581090593a57",
        digest: "sha256:da7a504352ff5a06105c269569d65f8c06c0d4a1b0564f474262a90725bc310b",
        bytes: 3780,
        crlf_count: 96,
        expected_digest: "sha256:4ca12507ec945faaec93050f61deedd9deb791081021eb1fde65196d1489d6bb",
        expected_bytes: 3684,
        contexts: &["dev/1.20.2", "release/1.20.2"],
    },
    RawSpec {
        path: ENTITY,
        blob: "7e4aad5800feed911b39bef7a82f24c69d69364d",
        digest: "sha256:0ee6891a2bf9c3207bc4e39ee8c31967d5968b5604adc892684a60edeae92c19",
        bytes: 1831,
        crlf_count: 47,
        expected_digest: "sha256:46ed2c6ca6d8037055e1dc8a4ccf11a1d4ab3d165d0e27d0e8cf938ff5f97cb9",
        expected_bytes: 1784,
        contexts: &[
            "dev/1.20.3",
            "dev/1.20.4",
            "dev/1.21.0",
            "dev/1.21.1",
            "dev/26.1.2",
            "release/1.20.3",
            "release/1.20.4",
            "release/1.21.0",
            "release/1.21.1",
            "release/26.1.2",
        ],
    },
    RawSpec {
        path: CONTENTS,
        blob: "0395aa843cc7aa6783fe4e5b47002299a8e65a84",
        digest: "sha256:cc30c4d7ecc174fe71c860f05696242a5575f3473162b27b8660a4854ceeb208",
        bytes: 5823,
        crlf_count: 141,
        expected_digest: "sha256:557119f8f8d3635902c16f622ef87e153c72a47ec4642bc3de32a0b0a51337cd",
        expected_bytes: 5682,
        contexts: &["dev/1.19.2", "dev/1.19.4"],
    },
    RawSpec {
        path: CONTENTS,
        blob: "c4873c394c11ee2a9a6ad334acd5f2db6d28f558",
        digest: "sha256:6d48e6be9252051a434b2a5c17ce246993abb315478edd763802d1d23c1a33b0",
        bytes: 4277,
        crlf_count: 109,
        expected_digest: "sha256:bd2284f148ee8b987a48b6b5bfb70602f7a6be3908b03b23e11fc48973b80bba",
        expected_bytes: 4168,
        contexts: &[
            "dev/1.20",
            "dev/1.20.1",
            "dev/1.20.2",
            "release/1.19.2",
            "release/1.19.4",
            "release/1.20",
            "release/1.20.1",
            "release/1.20.2",
        ],
    },
    RawSpec {
        path: CONTENTS,
        blob: "6b9235f372ed128747318f1cec33543a9ca8f8d9",
        digest: "sha256:bd7d24a39927d8dbe5b5a6d13549ec4d849af545a4f966ee28dd312e14ac0bc6",
        bytes: 4279,
        crlf_count: 108,
        expected_digest: "sha256:12d689795f9f7f6dce87e9febf25a660a3c73fa023b1a3158d53640eb25419e6",
        expected_bytes: 4171,
        contexts: &[
            "dev/1.20.3",
            "dev/1.20.4",
            "dev/1.21.0",
            "dev/1.21.1",
            "dev/26.1.2",
            "release/1.20.3",
            "release/1.20.4",
            "release/1.21.0",
            "release/1.21.1",
            "release/26.1.2",
        ],
    },
    RawSpec {
        path: MANAGER,
        blob: "91749620bef74c961544e5587438d1bda2ad715b",
        digest: "sha256:bc8ab6663073140e18f9002388acaf75841e67460b6b6139dcfa63c5a7c6c7d2",
        bytes: 21006,
        crlf_count: 0,
        expected_digest: "sha256:bc8ab6663073140e18f9002388acaf75841e67460b6b6139dcfa63c5a7c6c7d2",
        expected_bytes: 21006,
        contexts: &[
            "dev/1.19.2",
            "dev/1.19.4",
            "dev/1.20",
            "dev/1.20.1",
            "dev/1.20.2",
        ],
    },
    RawSpec {
        path: MANAGER,
        blob: "2c0b1a9066cc2d60ad4b11b3a22a97dcf4977de8",
        digest: "sha256:480fdd8a2aa12ec07fc4f8dfc6fdb2e07f54599f5493baae481f6e1a229d2d26",
        bytes: 21172,
        crlf_count: 0,
        expected_digest: "sha256:480fdd8a2aa12ec07fc4f8dfc6fdb2e07f54599f5493baae481f6e1a229d2d26",
        expected_bytes: 21172,
        contexts: &["dev/1.20.3", "dev/1.20.4"],
    },
    RawSpec {
        path: MANAGER,
        blob: "477569b21e535d8e870ab04f71e5366fa1a97434",
        digest: "sha256:93d1dffabea1fa25d65f6f6ccfe6004f150c5d71e40a94486a6da2f4f104303f",
        bytes: 21663,
        crlf_count: 0,
        expected_digest: "sha256:93d1dffabea1fa25d65f6f6ccfe6004f150c5d71e40a94486a6da2f4f104303f",
        expected_bytes: 21663,
        contexts: &["dev/1.21.0", "dev/1.21.1"],
    },
    RawSpec {
        path: MANAGER,
        blob: "5686f6cf23c4bb0d1eb377a13645baf0c40004a6",
        digest: "sha256:d3087ba09ca969ed92f33b7513d0c7b4cf3d9b7d4251eae6b95f89d5b4e873fa",
        bytes: 21954,
        crlf_count: 0,
        expected_digest: "sha256:d3087ba09ca969ed92f33b7513d0c7b4cf3d9b7d4251eae6b95f89d5b4e873fa",
        expected_bytes: 21954,
        contexts: &["dev/26.1.2"],
    },
    RawSpec {
        path: MANAGER,
        blob: "e0f695ad3477212f93ebc0ea6fd54a3cef758026",
        digest: "sha256:2a447a92b5e8da63581c83b1223b3169e2dd43b290a54edc0ca3acdc5d5c314c",
        bytes: 20621,
        crlf_count: 0,
        expected_digest: "sha256:2a447a92b5e8da63581c83b1223b3169e2dd43b290a54edc0ca3acdc5d5c314c",
        expected_bytes: 20621,
        contexts: &[
            "release/1.19.2",
            "release/1.19.4",
            "release/1.20",
            "release/1.20.1",
            "release/1.20.2",
        ],
    },
    RawSpec {
        path: MANAGER,
        blob: "071e5aab4fdeba690c3f4731658b06fe85e2cf3b",
        digest: "sha256:9e0600fbd374eb6136b45246e41c0c0bc2223055bd8a66d6ab0ff5a7c3305ccf",
        bytes: 20787,
        crlf_count: 0,
        expected_digest: "sha256:9e0600fbd374eb6136b45246e41c0c0bc2223055bd8a66d6ab0ff5a7c3305ccf",
        expected_bytes: 20787,
        contexts: &["release/1.20.3", "release/1.20.4"],
    },
    RawSpec {
        path: MANAGER,
        blob: "6e51690bb8ccb5fe94034eda746d2a8a95b35f8f",
        digest: "sha256:24f3245bca41c45f6095b620dea8fee50a24c0329c2952f20284d995ae85b844",
        bytes: 21278,
        crlf_count: 0,
        expected_digest: "sha256:24f3245bca41c45f6095b620dea8fee50a24c0329c2952f20284d995ae85b844",
        expected_bytes: 21278,
        contexts: &["release/1.21.0", "release/1.21.1"],
    },
    RawSpec {
        path: MANAGER,
        blob: "a3f617cab1fe83dcc919855e6f3afa40c0ba240e",
        digest: "sha256:7cd1bedbac952581b072c68b3373b27dd3001fd9c114661ac3b5b029740171af",
        bytes: 21569,
        crlf_count: 0,
        expected_digest: "sha256:7cd1bedbac952581b072c68b3373b27dd3001fd9c114661ac3b5b029740171af",
        expected_bytes: 21569,
        contexts: &["release/26.1.2"],
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    owners: BTreeMap<String, Owner>,
    normalization: Normalization,
    files: Vec<AuthoredFile>,
    raw_witnesses: Vec<RawWitness>,
}
#[derive(Facet)]
struct Owner {
    support: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    approved_git_blobs: Vec<String>,
    crlf_to_lf_only: bool,
    append_final_lf: bool,
    other_whitespace_changes: bool,
}
#[derive(Facet)]
struct AuthoredFile {
    intended_core_path: String,
    stage_sha256: String,
    stage_bytes: u64,
    membership: String,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    git_blob: String,
    raw_sha256: String,
    raw_bytes: u64,
    expected_sha256: String,
    expected_bytes: u64,
    crlf_count: usize,
    lf_added: bool,
    normalization: String,
    present: bool,
}
#[derive(Facet)]
struct RawWitness {
    path: String,
    git_blob: String,
    raw_sha256: String,
    raw_bytes: u64,
    expected_sha256: String,
    expected_bytes: u64,
    crlf_count: usize,
    lone_cr_count: usize,
    lf_added: bool,
    final_lf: bool,
    bom: bool,
    contexts: Vec<String>,
}
struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    sources: BTreeMap<String, Vec<u8>>,
    blobs: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), MAX_LEDGER_BYTES)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)
            .wrap_err("cannot parse bounded Buffer/Manager evidence")?;
        let sources = PATHS
            .into_iter()
            .map(|path| {
                let bytes = shared.read_source(path)?;
                Ok((
                    path.to_owned(),
                    if path == MANAGER {
                        historical_manager_template(&bytes)?
                    } else {
                        bytes
                    },
                ))
            })
            .collect::<Result<BTreeMap<_, _>>>()?;
        let blobs = read_git_blobs(
            &shared.repository,
            &RAW_SPECS
                .into_iter()
                .map(|spec| spec.blob.to_owned())
                .collect(),
        )?;
        let fixture = Self {
            shared,
            ledger,
            sources,
            blobs,
        };
        fixture.validate_scope()?;
        Ok(fixture)
    }
    fn validate_scope(&self) -> Result<()> {
        ensure!(
            self.ledger.schema == "sfm:core-buffer-manager-slice@1",
            "schema changed"
        );
        let commits = PINNED_CONTEXTS
            .into_iter()
            .map(|(c, id)| (c.to_owned(), id.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == commits,
            "twenty frozen contexts changed"
        );
        ensure!(
            self.ledger.files.len() == 4
                && self
                    .ledger
                    .files
                    .iter()
                    .map(|file| file.intended_core_path.as_str())
                    .collect::<BTreeSet<_>>()
                    == BTreeSet::from(PATHS),
            "Buffer/Manager logical cohort changed"
        );
        ensure!(
            self.ledger
                .owners
                .keys()
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == BTreeSet::from(OWNERS),
            "owner scope changed"
        );
        let all_targets = SUPPORTED_TARGETS
            .into_iter()
            .map(|(id, _)| id)
            .collect::<Vec<_>>();
        for (name, support, requires) in [
            (
                "redstone_buffer_storage",
                &["1.19.2", "1.19.4"][..],
                &[][..],
            ),
            (
                "image_resources",
                &["1.19.2", "1.19.4"][..],
                &["packet_values"][..],
            ),
            (
                "buffer_image_persistence",
                &["1.19.2", "1.19.4"][..],
                &["image_resources"][..],
            ),
            (
                "computercraft",
                all_targets.as_slice(),
                &[
                    "mod_event_filtering",
                    "disk_readonly_access",
                    "label_readonly_access",
                ][..],
            ),
        ] {
            let owner = self
                .ledger
                .owners
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing owner"))?;
            let definition = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("unregistered owner"))?;
            ensure!(
                owner.support == support.iter().map(|s| (*s).to_owned()).collect::<Vec<_>>()
                    && owner.requires
                        == requires.iter().map(|s| (*s).to_owned()).collect::<Vec<_>>()
                    && owner.support == definition.supported_targets
                    && owner.requires == definition.requires,
                "real owner support/prerequisite contract changed"
            );
        }
        let approved = RAW_SPECS
            .into_iter()
            .filter(|spec| spec.crlf_count > 0)
            .map(|spec| spec.blob)
            .collect::<BTreeSet<_>>();
        let normalization = &self.ledger.normalization;
        ensure!(
            approved.len() == 10
                && normalization.policy == NORMALIZATION
                && normalization.approved_git_blobs.len() == 10
                && normalization
                    .approved_git_blobs
                    .iter()
                    .map(String::as_str)
                    .collect::<BTreeSet<_>>()
                    == approved
                && normalization.crlf_to_lf_only
                && !normalization.append_final_lf
                && !normalization.other_whitespace_changes,
            "normalization scope widened"
        );
        ensure!(
            self.ledger.raw_witnesses.len() == 18,
            "raw witness count changed"
        );
        let mut seen_raw = BTreeSet::new();
        for row in &self.ledger.raw_witnesses {
            let spec = raw_spec(&row.path, &row.git_blob)?;
            let normalized = normalize_witness(spec, self.blob(spec.blob)?)?;
            ensure!(
                seen_raw.insert(spec.blob)
                    && row.raw_sha256 == spec.digest
                    && row.raw_bytes == spec.bytes
                    && row.expected_sha256 == spec.expected_digest
                    && row.expected_bytes == spec.expected_bytes
                    && row.crlf_count == spec.crlf_count
                    && row.lone_cr_count == 0
                    && !row.lf_added
                    && row.final_lf
                    && !row.bom
                    && sha256(&normalized) == spec.expected_digest
                    && row.contexts
                        == spec
                            .contexts
                            .iter()
                            .map(|s| (*s).to_owned())
                            .collect::<Vec<_>>(),
                "raw/normalized Buffer/Manager identity changed"
            );
        }
        for file in &self.ledger.files {
            let path = file.intended_core_path.as_str();
            let (_, digest, count) = PINNED_CORE
                .into_iter()
                .find(|(p, _, _)| *p == path)
                .ok_or_else(|| eyre::eyre!("unexpected promoted core path"))?;
            let source = self
                .sources
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing source"))?;
            ensure!(
                file.stage_sha256 == digest
                    && file.stage_bytes == count
                    && source.len() as u64 == count
                    && sha256(source) == digest
                    && file.membership == "unconditional_shared_class",
                "core template golden changed; intentional edits need review"
            );
            ensure!(file.witnesses.len() == 20, "incomplete witnesses");
            let mut seen = BTreeSet::new();
            for row in &file.witnesses {
                let spec = expected_spec(path, &row.context)?;
                ensure!(
                    seen.insert(row.context.as_str())
                        && row.git_blob == spec.blob
                        && row.raw_sha256 == spec.digest
                        && row.raw_bytes == spec.bytes
                        && row.expected_sha256 == spec.expected_digest
                        && row.expected_bytes == spec.expected_bytes
                        && row.crlf_count == spec.crlf_count
                        && !row.lf_added
                        && row.present
                        && row.normalization
                            == if spec.crlf_count == 0 {
                                "none"
                            } else {
                                NORMALIZATION
                            },
                    "frozen witness changed"
                );
            }
            ensure!(
                seen == commits.keys().map(String::as_str).collect(),
                "witness context scope changed"
            );
        }
        Ok(())
    }
    fn blob(&self, oid: &str) -> Result<&[u8]> {
        self.blobs
            .get(oid)
            .map(Vec::as_slice)
            .ok_or_else(|| eyre::eyre!("missing raw oracle"))
    }
    fn oracle(&self, path: &str, context_name: &str) -> Result<String> {
        let spec = expected_spec(path, context_name)?;
        Ok(String::from_utf8(normalize_witness(
            spec,
            self.blob(spec.blob)?,
        )?)?)
    }
    fn context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        let closed = feature_closure(&self.shared, requested)?;
        self.shared.context(
            target,
            &closed.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }
    fn render_sources(&self, context: &ProjectionContext) -> Result<BTreeMap<String, String>> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let mut rendered = BTreeMap::new();
        for path in PATHS {
            ensure!(
                !selection.omitted_paths.contains(path)
                    && selection
                        .inputs
                        .get(path)
                        .is_some_and(|input| input.input == path),
                "shared class omitted or routed to historical input"
            );
            let historical =
                render_java_source(std::str::from_utf8(&self.sources[path])?, context)?;
            if path == MANAGER {
                let current = render_java_source(
                    std::str::from_utf8(&self.shared.read_source(path)?)?,
                    context,
                )?;
                let comparison = super::simplify::compare(
                    &super::simplify::parse(historical.clone())?,
                    &super::simplify::parse(current)?,
                );
                ensure!(
                    matches!(
                        comparison.classification.as_str(),
                        "exact" | "whitespace_only"
                    ),
                    "current manager differs semantically from its historical rendering"
                );
            }
            rendered.insert(path.to_owned(), historical);
        }
        // The bounded cohort is these four paths, not all other selected owner rules.
        Ok(rendered)
    }
}
fn inventory() -> BTreeSet<String> {
    PATHS.into_iter().map(str::to_owned).collect()
}
fn feature_closure(shared: &CoreTestFixture, requested: &[&str]) -> Result<BTreeSet<String>> {
    ensure!(
        shared.features.0.len() <= 256,
        "feature graph exceeds test bound"
    );
    let mut enabled = requested
        .iter()
        .map(|name| (*name).to_owned())
        .collect::<BTreeSet<_>>();
    let mut pending = enabled.iter().cloned().collect::<Vec<_>>();
    while let Some(name) = pending.pop() {
        let definition = shared
            .features
            .0
            .get(&name)
            .ok_or_else(|| eyre::eyre!("unknown source feature"))?;
        for required in &definition.requires {
            if enabled.insert(required.clone()) {
                pending.push(required.clone());
            }
        }
    }
    Ok(enabled)
}
fn raw_spec(path: &str, blob: &str) -> Result<RawSpec> {
    RAW_SPECS
        .into_iter()
        .find(|spec| spec.path == path && spec.blob == blob)
        .ok_or_else(|| eyre::eyre!("unreviewed raw identity"))
}
fn expected_spec(path: &str, context: &str) -> Result<RawSpec> {
    RAW_SPECS
        .into_iter()
        .find(|spec| spec.path == path && spec.contexts.contains(&context))
        .ok_or_else(|| eyre::eyre!("unknown frozen context"))
}
fn normalize_witness(spec: RawSpec, bytes: &[u8]) -> Result<Vec<u8>> {
    ensure!(
        bytes.len() as u64 == spec.bytes
            && sha256(bytes) == spec.digest
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf])
            && bytes.ends_with(b"\n")
            && bytes.windows(2).filter(|pair| *pair == b"\r\n").count() == spec.crlf_count,
        "raw witness shape/hash changed"
    );
    let normalized = std::str::from_utf8(bytes)?
        .replace("\r\n", "\n")
        .into_bytes();
    ensure!(
        !normalized.contains(&b'\r')
            && normalized.len() as u64 == spec.expected_bytes
            && bytes.len() == normalized.len() + spec.crlf_count
            && sha256(&normalized) == spec.expected_digest,
        "unapproved CR/BOM/newline/token normalization"
    );
    Ok(normalized)
}
fn target_id(context: &ProjectionContext) -> Result<&'static str> {
    SUPPORTED_TARGETS
        .into_iter()
        .find(|(_, mc)| *mc == context.minecraft_version)
        .map(|(id, _)| id)
        .ok_or_else(|| eyre::eyre!("unknown actual Minecraft version"))
}
fn enabled(context: &ProjectionContext, flag: &str) -> Result<bool> {
    context
        .features
        .get(flag)
        .copied()
        .ok_or_else(|| eyre::eyre!("missing owner boolean"))
}
fn once(source: &str, anchor: &str, replacement: &str) -> Result<String> {
    ensure!(
        source.matches(anchor).count() == 1,
        "reviewed anchor not unique"
    );
    Ok(source.replacen(anchor, replacement, 1))
}
fn section<'a>(source: &'a str, start: &str, end: &str) -> Result<&'a str> {
    ensure!(source.matches(start).count() == 1, "ambiguous member start");
    let from = source
        .find(start)
        .ok_or_else(|| eyre::eyre!("missing member start"))?;
    let to = source[from..]
        .find(end)
        .map(|index| from + index)
        .ok_or_else(|| eyre::eyre!("missing member end"))?;
    ensure!(to > from, "invalid member boundary");
    Ok(&source[from..to])
}

/// Independent member-removal oracle from normalized raw development source.
/// No Liquid or source-template interpretation is used; baseline API bytes are
/// read from the target's exact released witness.
fn expected_owner_body(
    fixture: &Fixture,
    path: &str,
    context: &ProjectionContext,
) -> Result<String> {
    let target = target_id(context)?;
    let baseline = fixture.oracle(path, &format!("release/{target}"))?;
    if path == MANAGER {
        if !enabled(context, "computercraft")? {
            return Ok(baseline);
        }
        let dev = fixture.oracle(path, "dev/1.19.2")?;
        let member = section(
            &dev,
            "    /**\n     * Computes manager state without creating NBT on a blank disk.\n",
            "    public @Nullable String getProgramString() {",
        )?;
        return once(
            &baseline,
            "    public @Nullable String getProgramString() {",
            &format!("{member}    public @Nullable String getProgramString() {{"),
        );
    }
    if !matches!(target, "1.19.2" | "1.19.4") {
        return Ok(baseline);
    }
    let redstone = enabled(context, "redstone_buffer_storage")?;
    let image = enabled(context, "image_resources")?;
    let persistence = enabled(context, "buffer_image_persistence")?;
    let mut source = fixture.oracle(path, "dev/1.19.2")?;
    match path {
        BLOCK => {
            if !redstone {
                for row in [
                    "import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;\n",
                    "import net.minecraft.util.Mth;\n",
                ] {
                    source = once(&source, row, "")?;
                }
                let block = section(
                    &source,
                    "    @SuppressWarnings(\"deprecation\")\n    @MCVersionDependentBehaviour\n    @Override\n    public boolean hasAnalogOutputSignal",
                    "    public BufferBlock(\n",
                )?;
                source = once(&source, block, "")?;
            }
            if !image {
                for row in [
                    "        Image,\n",
                    "                case Image -> \"image\";\n",
                    "            } else if (name.equals(\"image\")) {\n                return Image;\n",
                ] {
                    source = once(&source, row, "")?;
                }
            }
        }
        ENTITY => {
            if !persistence {
                for row in [
                    "import ca.teamdman.sfm.common.capability.IImageHandler;\n",
                    "import ca.teamdman.sfm.common.image.SFMImageSnapshotCodec;\n",
                    "import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;\n",
                    "import ca.teamdman.sfm.common.resourcetype.SFMImageStack;\n",
                    "import ca.teamdman.sfm.common.value.SFMValue;\n",
                    "import ca.teamdman.sfm.common.value.SFMValueJsonCodec;\n",
                    "import net.minecraft.nbt.Tag;\n",
                    "import java.util.Optional;\n",
                    "    private static final String IMAGE_TAG = \"image_snapshot\";\n    private static final String IMAGE_STATE_TAG = \"image_interaction_state\";\n    private static final String IMAGE_STATE_CODEC_TAG = \"image_state_codec\";\n",
                ] {
                    source = once(&source, row, "")?;
                }
            }
            if !redstone && !image {
                source = once(
                    &source,
                    "        this.contents = new BufferBlockEntityContents(tier, this::setChanged);\n",
                    "        this.contents = new BufferBlockEntityContents(tier);\n",
                )?;
            }
            if !redstone && !persistence {
                source = once(&source, "import net.minecraft.nbt.CompoundTag;\n", "")?;
                let block = section(
                    &source,
                    "    /** Redstone and complete image/state resources are durable; other resources remain transient. */\n",
                    "    @Override\n    public void invalidateCaps() {",
                )?;
                source = once(&source, block, "")?;
            } else {
                if !redstone {
                    source = once(
                        &source,
                        "        contents.loadRedstone(tag.getLong(\"redstone\"));\n",
                        "",
                    )?;
                    source = once(
                        &source,
                        "        tag.putInt(\"redstone\", contents.getStoredRedstone());\n",
                        "",
                    )?;
                }
                if !persistence {
                    source = once(
                        &source,
                        "        // Clear the old image before restoring redstone so persisted resources\n        // can replace each other without replacing cached capability handlers.\n        // Occupied nonpersisted handlers remain protected by resource exclusion.\n        imageHandler().ifPresent(handler -> handler.extractImage(false));\n",
                        "",
                    )?;
                    source = once(
                        &source,
                        "        // Redstone wins if malformed NBT supplies both persisted resource types.\n        loadImage(tag);\n",
                        "",
                    )?;
                    let member = section(
                        &source,
                        "    private void loadImage(CompoundTag tag) {",
                        "    @MCVersionDependentBehaviour\n    @Override\n    protected void saveAdditional",
                    )?;
                    source = once(&source, member, "")?;
                    source = once(
                        &source,
                        "        imageHandler().ifPresent(handler -> {\n            SFMImageStack image = handler.getImage();\n            image.snapshot().ifPresent(snapshot -> {\n                tag.put(IMAGE_TAG, SFMImageSnapshotCodec.toTag(snapshot));\n                tag.putString(IMAGE_STATE_TAG, SFMValueJsonCodec.encode(image.interactionState()));\n                tag.putInt(IMAGE_STATE_CODEC_TAG, SFMValueJsonCodec.VERSION);\n            });\n        });\n",
                        "",
                    )?;
                    let helper = section(
                        &source,
                        "    private Optional<IImageHandler> imageHandler() {",
                        "    @Override\n    public void invalidateCaps() {",
                    )?;
                    source = once(&source, helper, "")?;
                }
            }
        }
        CONTENTS => {
            if !redstone {
                for row in [
                    "import ca.teamdman.sfm.common.capability.IRedstoneSignalStorage;\n",
                    "import ca.teamdman.sfm.common.capability.RedstoneSignalStorage;\n",
                    "import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;\n",
                    "import net.minecraft.nbt.IntTag;\n",
                ] {
                    source = once(&source, row, "")?;
                }
                let anchor = "    public BufferBlock.ContainedResource lastUsedResource = BufferBlock.ContainedResource.Unknown;\n";
                let from = source
                    .find(anchor)
                    .ok_or_else(|| eyre::eyre!("missing resource anchor"))?
                    + anchor.len();
                let to = source[from..]
                    .find("    /// Should return None")
                    .map(|i| from + i)
                    .ok_or_else(|| eyre::eyre!("missing capability anchor"))?;
                source.replace_range(from..to, "\n\n");
            }
            if !redstone && !image {
                source = once(&source, "    private final Runnable onChanged;\n", "")?;
                let ctor = section(
                    &source,
                    "    public BufferBlockEntityContents(BufferBlockTier tier) {",
                    "    public BufferBlock.ContainedResource lastUsedResource",
                )?;
                let old_ctor = section(
                    &baseline,
                    "    public BufferBlockEntityContents(BufferBlockTier tier) {",
                    "    public BufferBlock.ContainedResource lastUsedResource",
                )?;
                source = once(&source, ctor, old_ctor)?;
            } else if !image {
                let member = section(
                    &source,
                    "    public void markChanged() {",
                    "    public BufferBlock.ContainedResource lastUsedResource",
                )?;
                source = once(&source, member, "")?;
            }
        }
        _ => eyre::bail!("unknown Buffer/Manager source oracle"),
    }
    Ok(source)
}

fn assert_owner_contract(path: &str, source: &str, context: &ProjectionContext) -> Result<()> {
    let redstone = enabled(context, "redstone_buffer_storage")?;
    let image = enabled(context, "image_resources")?;
    let persistence = enabled(context, "buffer_image_persistence")?;
    match path {
        BLOCK => ensure!(
            source.contains("public int getAnalogOutputSignal") == redstone
                && source.contains("        Image,") == image
                && source.contains("case Image ->") == image,
            "buffer block owner leaked/omitted"
        ),
        ENTITY => {
            ensure!(
                source.contains("image_snapshot") == persistence
                    && source.contains("private void loadImage") == persistence
                    && source.contains("tag.putInt(\"redstone\"") == redstone
                    && source.contains("contents.loadRedstone") == redstone
                    && source.contains("new BufferBlockEntityContents(tier, this::setChanged)")
                        == (redstone || image)
                    && source.contains("public void load(CompoundTag tag)")
                        == (redstone || persistence),
                "buffer NBT or mutation callback owners were coupled"
            );
            if redstone && persistence {
                let clear = source
                    .find("handler.extractImage(false)")
                    .ok_or_else(|| eyre::eyre!("missing image clear"))?;
                let red = source
                    .find("contents.loadRedstone")
                    .ok_or_else(|| eyre::eyre!("missing redstone restore"))?;
                let img = source
                    .find("        loadImage(tag);")
                    .ok_or_else(|| eyre::eyre!("missing image restore"))?;
                ensure!(
                    clear < red && red < img,
                    "authoritative restored-resource exclusion order changed"
                );
            }
        }
        CONTENTS => ensure!(
            source.contains("private final Runnable onChanged") == (redstone || image)
                && source.contains("public void markChanged()") == image
                && source.contains("public int getStoredRedstone()") == redstone
                && source.contains("public void loadRedstone(long amount)") == redstone,
            "buffer contents callback/storage owner leaked/omitted"
        ),
        MANAGER => ensure!(
            source.contains("public State getStateReadOnly()")
                == enabled(context, "computercraft")?,
            "read-only manager owner changed"
        ),
        _ => eyre::bail!("unknown owner-contract source"),
    }
    Ok(())
}

#[test]
fn all_eighty_buffer_manager_frozen_witnesses_match_real_selection_and_renderer() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (environment, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let requested = if environment == "dev" {
            if matches!(target, "1.19.2" | "1.19.4") {
                OWNERS.to_vec()
            } else {
                vec!["computercraft"]
            }
        } else {
            Vec::new()
        };
        let context = fixture.context(target, &requested)?;
        let rendered = fixture.render_sources(&context)?;
        for path in PATHS {
            assert_eq!(
                rendered[path],
                fixture.oracle(path, name)?,
                "{name}: {path}"
            );
            assert_owner_contract(path, &rendered[path], &context)?;
            cells += 1;
        }
    }
    assert_eq!(cells, 80);
    Ok(())
}

#[test]
fn independent_storage_image_persistence_and_observation_masks_are_not_blanket_features()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut profiles = 0;
    for target in ["1.19.2", "1.19.4"] {
        for mask in 0..16 {
            let requested = OWNERS
                .iter()
                .enumerate()
                .filter_map(|(index, flag)| (mask & (1 << index) != 0).then_some(*flag))
                .collect::<Vec<_>>();
            let context = fixture.context(target, &requested)?;
            let rendered = fixture.render_sources(&context)?;
            for path in PATHS {
                assert_eq!(
                    rendered[path],
                    expected_owner_body(&fixture, path, &context)?,
                    "{target}: mask{mask}: {path}"
                );
                assert_owner_contract(path, &rendered[path], &context)?;
            }
            profiles += 1;
        }
    }
    assert_eq!(profiles, 32);
    // The direct image callback remains even when durable image NBT is disabled.
    let image_only = fixture.context("1.19.2", &["image_resources"])?;
    assert!(!enabled(&image_only, "buffer_image_persistence")?);
    let image_bodies = fixture.render_sources(&image_only)?;
    assert!(
        image_bodies[CONTENTS].contains("public void markChanged()")
            && image_bodies[ENTITY].contains("tier, this::setChanged")
            && !image_bodies[ENTITY].contains("image_snapshot")
    );
    for (target, _) in SUPPORTED_TARGETS {
        let context = fixture.context(target, &["computercraft"])?;
        let bodies = fixture.render_sources(&context)?;
        for path in PATHS {
            assert_eq!(bodies[path], expected_owner_body(&fixture, path, &context)?);
        }
        if !matches!(target, "1.19.2" | "1.19.4") {
            for flag in &OWNERS[..3] {
                assert!(fixture.context(target, &[*flag]).is_err());
            }
        }
    }
    for invalid in [
        &["buffer_image_persistence"][..],
        &["image_resources"][..],
        &["computercraft"][..],
    ] {
        assert!(fixture.shared.context("1.19.2", invalid).is_err());
    }
    Ok(())
}

#[test]
fn exact_ten_crlf_only_transformations_preserve_final_lf_and_reject_broader_normalization()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut changed = 0;
    for spec in RAW_SPECS {
        let raw = fixture.blob(spec.blob)?;
        let normalized = normalize_witness(spec, raw)?;
        if spec.crlf_count > 0 {
            changed += 1;
            assert_ne!(raw, normalized.as_slice());
        } else {
            assert_eq!(raw, normalized.as_slice());
        }
        let mut extra = raw.to_vec();
        extra.push(b'\n');
        assert!(normalize_witness(spec, &extra).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend_from_slice(raw);
        assert!(normalize_witness(spec, &bom).is_err());
        assert!(normalize_witness(spec, &raw[..raw.len() - 1]).is_err());
        let mut whitespace = raw.to_vec();
        let space = whitespace
            .iter_mut()
            .find(|byte| **byte == b' ')
            .ok_or_else(|| eyre::eyre!("no raw mutation anchor"))?;
        *space = b'\t';
        assert!(normalize_witness(spec, &whitespace).is_err());
    }
    assert_eq!(changed, 10);
    Ok(())
}

#[test]
fn common_buffer_manager_body_edits_reach_three_api_eras_without_guard_or_provenance_changes()
-> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let mut edits = 0;
    for path in PATHS {
        let original_current = fixture.shared.read_source(path)?;
        let source = std::str::from_utf8(&fixture.sources[path])?;
        let name = path
            .rsplit('/')
            .next()
            .and_then(|name| name.strip_suffix(".java"))
            .ok_or_else(|| eyre::eyre!("invalid class name"))?;
        let line = source
            .lines()
            .find(|line| line.starts_with(&format!("public class {name} ")))
            .ok_or_else(|| eyre::eyre!("missing common class body"))?;
        let anchor = format!("{line}\n");
        let replacement = format!("{anchor}    // Common Buffer/Manager body edit proof.\n");
        let edited = once(source, &anchor, &replacement)?;
        assert_eq!(
            source
                .lines()
                .filter(|line| line.trim().starts_with("{%"))
                .collect::<Vec<_>>(),
            edited
                .lines()
                .filter(|line| line.trim().starts_with("{%"))
                .collect::<Vec<_>>()
        );
        let file = root.join(path);
        fs::create_dir_all(
            file.parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(&file, edited.as_bytes())?;
        for target in ["1.19.2", "1.21.0", "26.1.2"] {
            let context = fixture.context(target, &[])?;
            let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
            let input = selection
                .inputs
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing shared input"))?;
            assert_eq!(input.input, path);
            let bytes = read_bounded(&root.join(&input.input), MAX_SOURCE_BYTES)?;
            let rendered = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            let expected = once(
                &fixture.oracle(path, &format!("release/{target}"))?,
                &anchor,
                &replacement,
            )?;
            assert_eq!(rendered, expected, "{target}: {path}");
            edits += 1;
        }
        assert_eq!(fixture.shared.read_source(path)?, original_current);
    }
    assert_eq!(edits, 12);
    Ok(())
}

#[test]
fn released_api_shapes_and_known_historical_observation_semantics_are_preserved() -> Result<()> {
    let fixture = Fixture::load()?;
    for (target, _) in SUPPORTED_TARGETS {
        let context = fixture.context(target, &[])?;
        let source = fixture.render_sources(&context)?;
        let old = matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1" | "1.20.2");
        assert_eq!(source[BLOCK].contains("protected MapCodec"), !old);
        assert_eq!(source[ENTITY].contains("LazyOptional"), old);
        assert_eq!(
            source[MANAGER].contains("IItemHandler invWrapper"),
            !old && target != "26.1.2"
        );
        assert_eq!(
            source[MANAGER].contains("HolderLookup.Provider"),
            matches!(target, "1.21.0" | "1.21.1")
        );
        assert_eq!(
            source[MANAGER].contains("ValueInput input"),
            target == "26.1.2"
        );
        if target == "26.1.2" {
            assert!(
                source[MANAGER].contains("VanillaContainerWrapper.of(this)")
                    && source[MANAGER].contains("public void onTransfer(")
                    && source[MANAGER].contains("return ItemStack.EMPTY;")
            );
            let with_observation = fixture.context(target, &["computercraft"])?;
            let observed = fixture.render_sources(&with_observation)?;
            let helper = section(
                &observed[MANAGER],
                "    public State getStateReadOnly() {",
                "    public @Nullable String getProgramString() {",
            )?;
            assert!(
                helper.contains("if (disk == null) return State.NO_DISK;"),
                "do not silently fix the witnessed 26.1.2 helper while consolidating"
            );
        }
    }
    Ok(())
}

#[test]
fn promoted_buffer_manager_inputs_are_core_owned_not_historical_or_environment_routed() -> Result<()>
{
    let fixture = Fixture::load()?;
    for path in PATHS {
        let source = std::str::from_utf8(&fixture.sources[path])?;
        for line in source.lines().filter(|line| line.trim().starts_with("{%")) {
            for forbidden in ["environment", "preset", "projection_key"] {
                assert!(!line.contains(forbidden));
            }
        }
    }
    for (target, _) in SUPPORTED_TARGETS {
        fixture.render_sources(&fixture.context(target, &[])?)?;
    }
    Ok(())
}

#[test]
fn eighty_exact_frozen_block_and_entity_membership_cells_match_offline_git_ids() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for (name, commit) in PINNED_CONTEXTS {
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
                commit,
                "--",
            ]);
        for path in PATHS {
            command.arg(format!("platform/minecraft/{path}"));
        }
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("missing tree output"))?;
        let mut bytes = Vec::new();
        let read_result = reader
            .by_ref()
            .take(MAX_TREE_BYTES + 1)
            .read_to_end(&mut bytes);
        drop(reader);
        if read_result.is_err() || bytes.len() as u64 > MAX_TREE_BYTES {
            let _ = child.kill();
            let _ = child.wait();
            read_result?;
            eyre::bail!("frozen tree output exceeds bound");
        }
        ensure!(child.wait()?.success(), "offline membership read failed");
        ensure!(bytes.last() == Some(&0), "missing tree terminator");
        let mut actual = BTreeMap::new();
        for record in bytes.split(|byte| *byte == 0).filter(|row| !row.is_empty()) {
            let text = std::str::from_utf8(record)?;
            let (header, path) = text
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("invalid tree record"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3 && fields[0] == "100644" && fields[1] == "blob",
                "invalid frozen source mode/type"
            );
            ensure!(
                actual
                    .insert(path.to_owned(), fields[2].to_owned())
                    .is_none(),
                "duplicate membership"
            );
        }
        ensure!(actual.len() == 4, "frozen logical cohort changed");
        for path in PATHS {
            assert_eq!(
                actual
                    .get(&format!("platform/minecraft/{path}"))
                    .map(String::as_str),
                Some(expected_spec(path, name)?.blob)
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 80);
    Ok(())
}
