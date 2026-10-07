//! Frozen source-only migration goldens for the five shared datagen providers.
//!
//! Actual core selection and the production Liquid renderer must reconstruct
//! every released/development witness without any byte normalization. Git
//! blobs are bounded, offline, test-only oracles, never generation inputs.
//! Feature masks are dependency-closed source proofs, not complete-project
//! wire/Java/datagen/build acceptance. Future deliberate core edits require
//! reviewing these migration goldens.

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

const LEDGER: &str = "docs/tasks/sfm-core-datagen-slice.json";
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;
const MAX_SOURCE_BYTES: u64 = 128 * 1024;
const MAX_TREE_BYTES: u64 = 16 * 1024;
const STATES: &str = "src/datagen/java/ca/teamdman/sfm/datagen/SFMBlockStatesAndModelsDatagen.java";
const TAGS: &str = "src/datagen/java/ca/teamdman/sfm/datagen/SFMBlockTagsDatagen.java";
const ITEMS: &str = "src/datagen/java/ca/teamdman/sfm/datagen/SFMItemModelsDatagen.java";
const LOOT: &str = "src/datagen/java/ca/teamdman/sfm/datagen/SFMLootTablesDatagen.java";
const RECIPES: &str = "src/datagen/java/ca/teamdman/sfm/datagen/SFMRecipesDatagen.java";
const PATHS: [&str; 5] = [STATES, TAGS, ITEMS, LOOT, RECIPES];
const OWNERS: [&str; 6] = [
    "client_manager",
    "touch_display",
    "packet_values",
    "image_resources",
    "client_manager_custom_textures",
    "touch_display_custom_textures",
];
const PINNED_CORE: [(&str, &str, u64); 5] = [
    (
        STATES,
        "sha256:ce3902eceeaefe66bb826191c26fd134fa3e39e9481bfe5f74d7b44555b4b994",
        33703,
    ),
    (
        TAGS,
        "sha256:dab03c82e283bef4e7492065c161445ef4ca1b2784f6343687698a0f6e3382a1",
        3561,
    ),
    (
        ITEMS,
        "sha256:228208e473d28128b3feea19f3ea8738b9bcae31113315885fb8c24edf1facb5",
        7119,
    ),
    (
        LOOT,
        "sha256:cc6ae937409d2fbf8f2da2443006a760e2f40ad8f4c0cb644ae12aa3bea4ed0c",
        3817,
    ),
    (
        RECIPES,
        "sha256:80eb87c20228dbcfe47a26c2eafa4657c643aeec806cd25b0b330a120505f50b",
        24352,
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
    lf_count: usize,
    contexts: &'static [&'static str],
}
const RAW_SPECS: [RawSpec; 22] = [
    RawSpec {
        path: STATES,
        blob: "17d554f3c44c0a3b55077e27e3dcc48468940737",
        digest: "sha256:e61707c156dfc98d2bd7e2ddb9a1bd55c617eb336e06ae7cc8fd0f10871f859b",
        bytes: 13659,
        lf_count: 355,
        contexts: &["dev/1.19.2"],
    },
    RawSpec {
        path: STATES,
        blob: "fe89ebb1e3b858c9f6a92c5fc620db653eda641f",
        digest: "sha256:c18110f7d0142fc78085b19f2424ee597b61e4e5c4d79457802ff4d106abf959",
        bytes: 13616,
        lf_count: 355,
        contexts: &["dev/1.19.4"],
    },
    RawSpec {
        path: STATES,
        blob: "e01a91d8e3232d015c6feb643fd9891a9f666c50",
        digest: "sha256:2f55bc50a9f967e026c6a30c217cee4d4871484a1a93f4fe2166d25c4ce6a47a",
        bytes: 10942,
        lf_count: 286,
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
        path: STATES,
        blob: "d986b69df722e34f1b0e37d959a599b2b55ef74c",
        digest: "sha256:93644df95d6638c4045a075dc84a7579dc46845dd39449bf67ecbba548778c62",
        bytes: 10958,
        lf_count: 286,
        contexts: &[
            "dev/1.20.2",
            "dev/1.20.3",
            "dev/1.20.4",
            "dev/1.21.0",
            "dev/1.21.1",
            "release/1.20.2",
            "release/1.20.3",
            "release/1.20.4",
            "release/1.21.0",
            "release/1.21.1",
        ],
    },
    RawSpec {
        path: STATES,
        blob: "d30df564f8e9bbb29d68ade7379e6df130789d3b",
        digest: "sha256:1daf1bafb9ef491221e6565a63c2df8447746c946b729aebfef57d000c4ef5db",
        bytes: 17569,
        lf_count: 401,
        contexts: &["dev/26.1.2", "release/26.1.2"],
    },
    RawSpec {
        path: TAGS,
        blob: "efb214311f7691705aee9516b9f36325479b2ae3",
        digest: "sha256:0080808c78fc5d59136dd49c4cfe75ea9d77a1a3b6a900a609e3c210ea9c00a1",
        bytes: 2076,
        lf_count: 46,
        contexts: &["dev/1.19.2", "dev/1.19.4"],
    },
    RawSpec {
        path: TAGS,
        blob: "45544a71640b7995a57f4cbdc450e49bc4050989",
        digest: "sha256:4a440e2d6c64e4e82c3d808ac6cafa54a89460e34618e202ebcb18fc1ea4ed37",
        bytes: 1971,
        lf_count: 44,
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
        path: TAGS,
        blob: "6b10b12e1a23051a195c72eb77dc0e1603edf8ce",
        digest: "sha256:96f9c7e9bdee8aa82e695524516494c70fe3d5c285d1acc4cf35619c0f312c1a",
        bytes: 1975,
        lf_count: 44,
        contexts: &[
            "dev/1.20.2",
            "dev/1.20.3",
            "dev/1.20.4",
            "dev/1.21.0",
            "dev/1.21.1",
            "release/1.20.2",
            "release/1.20.3",
            "release/1.20.4",
            "release/1.21.0",
            "release/1.21.1",
        ],
    },
    RawSpec {
        path: TAGS,
        blob: "8c0af0d041fd572905174e5edb5487ef9b9edc6a",
        digest: "sha256:c6c588bfbf4f619de4da02a275e53e992857c123ebd2bd880d794ef88893c292",
        bytes: 2136,
        lf_count: 50,
        contexts: &["dev/26.1.2", "release/26.1.2"],
    },
    RawSpec {
        path: ITEMS,
        blob: "116797d0828e9ca0a330bcbf562221cda7d7aa39",
        digest: "sha256:61e70b51d678a0dc22f1cf714346f668cfdcf7d389ecf43231cbc39bb96475c8",
        bytes: 3695,
        lf_count: 95,
        contexts: &["dev/1.19.2", "dev/1.19.4"],
    },
    RawSpec {
        path: ITEMS,
        blob: "755d594614690448cb3f1631f0e8d9f89d3e3e2c",
        digest: "sha256:13c7862fb990ad35e46778314f98efeb35fbb5da786c5f48957f8daadce3401a",
        bytes: 3519,
        lf_count: 92,
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
        path: ITEMS,
        blob: "f697fd40a39e5225218791890ce50ecdfbade300",
        digest: "sha256:b2a77778a2a3056d39cd68795a472b8f93cfb44ea010387a243b6e288132ceb6",
        bytes: 3531,
        lf_count: 92,
        contexts: &[
            "dev/1.20.2",
            "dev/1.20.3",
            "dev/1.20.4",
            "dev/1.21.0",
            "dev/1.21.1",
            "release/1.20.2",
            "release/1.20.3",
            "release/1.20.4",
            "release/1.21.0",
            "release/1.21.1",
        ],
    },
    RawSpec {
        path: ITEMS,
        blob: "6ce979bf65224ef9816367a5666212f62b260009",
        digest: "sha256:a9797741f121d60518301aeb2617ec497c3cf08b52a520c7decd51008a0d9b17",
        bytes: 3376,
        lf_count: 84,
        contexts: &["dev/26.1.2", "release/26.1.2"],
    },
    RawSpec {
        path: LOOT,
        blob: "7655bb83ef9aef5f682942cc8cab5d5598b662e1",
        digest: "sha256:b456e90efcf775f903780ee0fdc60b21a7e32652e65b397b3f1556347a739e66",
        bytes: 2353,
        lf_count: 57,
        contexts: &["dev/1.19.2", "dev/1.19.4"],
    },
    RawSpec {
        path: LOOT,
        blob: "eab71d01e11921bf95eaf2c31fc5c7a1bf398df5",
        digest: "sha256:2d5ee1f0f62a51c978258546d57458ed5c4ba94be7cb99c25dcf58333c0412a7",
        bytes: 2252,
        lf_count: 55,
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
        path: LOOT,
        blob: "98569b323c558ef716859dd5f7b20f210b19b23d",
        digest: "sha256:d14111bfdff19f38a6a444a2cce7071e39332b5a5719320e0e129ae4a58506a8",
        bytes: 2256,
        lf_count: 55,
        contexts: &[
            "dev/1.20.2",
            "dev/1.20.3",
            "dev/1.20.4",
            "dev/1.21.0",
            "dev/1.21.1",
            "release/1.20.2",
            "release/1.20.3",
            "release/1.20.4",
            "release/1.21.0",
            "release/1.21.1",
        ],
    },
    RawSpec {
        path: LOOT,
        blob: "9bc75cd61775c66093c1c957110014b902902873",
        digest: "sha256:bfde74e83a71f7c2066335f652692362ecc5411feeccc92446343b84c1d0ce2f",
        bytes: 2701,
        lf_count: 64,
        contexts: &["dev/26.1.2", "release/26.1.2"],
    },
    RawSpec {
        path: RECIPES,
        blob: "cc196e01c94329611e6a3e2dc2d3864d795dba4e",
        digest: "sha256:d472010f40d687f0474a5eb8428542c7d08946ae684d8afcab4f522c9f1ced4e",
        bytes: 14039,
        lf_count: 323,
        contexts: &["dev/1.19.2", "dev/1.19.4"],
    },
    RawSpec {
        path: RECIPES,
        blob: "d8e5e55f19febb829bc7f8807787e486367afa63",
        digest: "sha256:572ce87758b0571d6c92510bf1be10f1ce6e593fab67d1eec68f3858e3356037",
        bytes: 13087,
        lf_count: 301,
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
        path: RECIPES,
        blob: "ee087f8d149ded49dc652c269f74cdf9e3743afe",
        digest: "sha256:44e6556e5e8b7a23b9e1d47872bd269b43f43602d93ee90232869abb2b34dea2",
        bytes: 13032,
        lf_count: 299,
        contexts: &["dev/1.20.2", "release/1.20.2"],
    },
    RawSpec {
        path: RECIPES,
        blob: "1cf0f3b5a14890943bd4dffe9349f85dd2cf7632",
        digest: "sha256:fb03df7ebd176d1e51bc56e001953ec5a0c036d72721a58068ae22714677e3e9",
        bytes: 13386,
        lf_count: 307,
        contexts: &[
            "dev/1.20.3",
            "dev/1.20.4",
            "dev/1.21.0",
            "dev/1.21.1",
            "release/1.20.3",
            "release/1.20.4",
            "release/1.21.0",
            "release/1.21.1",
        ],
    },
    RawSpec {
        path: RECIPES,
        blob: "62c15efd0a2d946bd5374b733c1fe6bd935143ac",
        digest: "sha256:75ef6a38fe19f27e5ef050e0dd07ff33737ed63cd09a38ef0bc0720f9ac6cb45",
        bytes: 13643,
        lf_count: 325,
        contexts: &["dev/26.1.2", "release/26.1.2"],
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
    crlf_to_lf: bool,
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
    normalization: String,
    present: bool,
}
#[derive(Facet)]
struct RawWitness {
    path: String,
    git_blob: String,
    raw_sha256: String,
    raw_bytes: u64,
    crlf_count: usize,
    lone_cr_count: usize,
    lf_count: usize,
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
        let ledger_bytes = read_bounded(&shared.repository.join(LEDGER), MAX_LEDGER_BYTES)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&ledger_bytes)?)
            .wrap_err("cannot parse bounded datagen source evidence")?;
        let sources = PATHS
            .into_iter()
            .map(|path| Ok((path.to_owned(), shared.read_source(path)?)))
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
            self.ledger.schema == "sfm:core-datagen-slice@1",
            "datagen schema changed"
        );
        let commits = PINNED_CONTEXTS
            .into_iter()
            .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == commits,
            "twenty frozen contexts changed"
        );
        ensure!(
            self.ledger.files.len() == 5
                && self
                    .ledger
                    .files
                    .iter()
                    .map(|file| file.intended_core_path.as_str())
                    .collect::<BTreeSet<_>>()
                    == BTreeSet::from(PATHS),
            "datagen cohort paths changed"
        );
        ensure!(
            self.ledger
                .owners
                .keys()
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == BTreeSet::from(OWNERS),
            "functional owner set changed"
        );
        for (name, support, requires) in [
            (
                "client_manager",
                &["1.19.2", "1.19.4"][..],
                &["sfml_execution_side", "client_program_consent"][..],
            ),
            (
                "touch_display",
                &["1.19.2", "1.19.4"][..],
                &["packet_values", "image_resources"][..],
            ),
            ("packet_values", &["1.19.2", "1.19.4"][..], &[][..]),
            (
                "image_resources",
                &["1.19.2", "1.19.4"][..],
                &["packet_values"][..],
            ),
            (
                "client_manager_custom_textures",
                &["1.19.2"][..],
                &["client_manager"][..],
            ),
            (
                "touch_display_custom_textures",
                &["1.19.2"][..],
                &["touch_display"][..],
            ),
        ] {
            let owner = self
                .ledger
                .owners
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing datagen owner"))?;
            let definition = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("unregistered datagen owner"))?;
            ensure!(
                owner.support
                    == support
                        .iter()
                        .map(|value| (*value).to_owned())
                        .collect::<Vec<_>>()
                    && owner.requires
                        == requires
                            .iter()
                            .map(|value| (*value).to_owned())
                            .collect::<Vec<_>>()
                    && owner.support == definition.supported_targets,
                "frozen source owner support or prerequisites changed"
            );
            // The historical ledger remains immutable. Only the explicitly
            // reviewed Client Manager readonly-disk prerequisite is added now.
            let mut current_requires = owner.requires.clone();
            if name == "client_manager" {
                current_requires.push("disk_readonly_access".to_owned());
            }
            ensure!(
                current_requires == definition.requires,
                "current source owner differs beyond the reviewed disk readonly refinement at {name}"
            );
        }
        let normalization = &self.ledger.normalization;
        ensure!(
            normalization.policy == "none"
                && normalization.approved_git_blobs.is_empty()
                && !normalization.crlf_to_lf
                && !normalization.append_final_lf
                && !normalization.other_whitespace_changes,
            "datagen raw witnesses must not be normalized"
        );
        ensure!(
            self.ledger.raw_witnesses.len() == 22,
            "raw witness cohort changed"
        );
        let mut raw_seen = BTreeSet::new();
        for row in &self.ledger.raw_witnesses {
            let spec = raw_spec(&row.path, &row.git_blob)?;
            let bytes = self.blob(spec.blob)?;
            verify_raw(spec, bytes)?;
            ensure!(
                raw_seen.insert(spec.blob)
                    && row.raw_sha256 == spec.digest
                    && row.raw_bytes == spec.bytes
                    && row.lf_count == spec.lf_count
                    && row.crlf_count == 0
                    && row.lone_cr_count == 0
                    && row.final_lf
                    && !row.bom
                    && row.contexts
                        == spec
                            .contexts
                            .iter()
                            .map(|value| (*value).to_owned())
                            .collect::<Vec<_>>(),
                "raw LF datagen evidence changed"
            );
        }
        for file in &self.ledger.files {
            let path = file.intended_core_path.as_str();
            let (_, digest, size) = PINNED_CORE
                .into_iter()
                .find(|(p, _, _)| *p == path)
                .ok_or_else(|| eyre::eyre!("unexpected core datagen input"))?;
            let source = self
                .sources
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing promoted core datagen source"))?;
            ensure!(
                file.stage_sha256 == digest
                    && file.stage_bytes == size
                    && source.len() as u64 == size
                    && sha256(source) == digest
                    && file.membership == "unconditional_shared_class",
                "promoted datagen golden changed; intentional edits require review"
            );
            ensure!(
                file.witnesses.len() == 20,
                "datagen witness membership count changed"
            );
            let mut seen = BTreeSet::new();
            for witness in &file.witnesses {
                let spec = expected_spec(path, &witness.context)?;
                ensure!(
                    seen.insert(witness.context.as_str())
                        && commits.contains_key(&witness.context)
                        && witness.git_blob == spec.blob
                        && witness.raw_sha256 == spec.digest
                        && witness.raw_bytes == spec.bytes
                        && witness.normalization == "none"
                        && witness.present,
                    "datagen witness changed"
                );
            }
            ensure!(
                seen == commits.keys().map(String::as_str).collect(),
                "incomplete witness coverage"
            );
        }
        Ok(())
    }

    fn blob(&self, oid: &str) -> Result<&[u8]> {
        self.blobs
            .get(oid)
            .map(Vec::as_slice)
            .ok_or_else(|| eyre::eyre!("missing bounded datagen oracle"))
    }

    fn context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        let names = feature_closure(&self.shared, requested)?;
        self.shared.context(
            target,
            &names.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }

    fn render_sources(&self, context: &ProjectionContext) -> Result<BTreeMap<String, String>> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let mut bodies = BTreeMap::new();
        for path in PATHS {
            ensure!(
                !selection.omitted_paths.contains(path)
                    && selection
                        .inputs
                        .get(path)
                        .is_some_and(|input| input.input == path),
                "unconditional shared datagen class omitted or historically routed"
            );
            let source = self
                .sources
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing core datagen source"))?;
            bodies.insert(
                path.to_owned(),
                render_java_source(std::str::from_utf8(source)?, context)?,
            );
        }
        // Other metadata source rules may select additional owned inputs;
        // only these five logical paths constitute this source-test cohort.
        Ok(bodies)
    }
}

fn inventory() -> BTreeSet<String> {
    PATHS.into_iter().map(str::to_owned).collect()
}

fn feature_closure(shared: &CoreTestFixture, requested: &[&str]) -> Result<BTreeSet<String>> {
    ensure!(
        shared.features.0.len() <= 256,
        "source feature registry exceeds test bound"
    );
    let mut enabled = requested
        .iter()
        .map(|value| (*value).to_owned())
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
        .ok_or_else(|| eyre::eyre!("unreviewed raw datagen witness"))
}
fn expected_spec(path: &str, context: &str) -> Result<RawSpec> {
    RAW_SPECS
        .into_iter()
        .find(|spec| spec.path == path && spec.contexts.contains(&context))
        .ok_or_else(|| eyre::eyre!("unreviewed frozen datagen context"))
}
fn verify_raw(spec: RawSpec, bytes: &[u8]) -> Result<()> {
    ensure!(
        bytes.len() as u64 == spec.bytes
            && sha256(bytes) == spec.digest
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf])
            && !bytes.contains(&b'\r')
            && bytes.last() == Some(&b'\n')
            && bytes.iter().filter(|byte| **byte == b'\n').count() == spec.lf_count,
        "raw datagen bytes changed; no normalization is allowed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn target_id(context: &ProjectionContext) -> Result<&'static str> {
    SUPPORTED_TARGETS
        .into_iter()
        .find(|(_, mc)| *mc == context.minecraft_version)
        .map(|(id, _)| id)
        .ok_or_else(|| eyre::eyre!("unknown actual catalog Minecraft version"))
}
fn enabled(context: &ProjectionContext, name: &str) -> Result<bool> {
    context
        .features
        .get(name)
        .copied()
        .ok_or_else(|| eyre::eyre!("missing owner boolean"))
}
fn once(source: &str, anchor: &str, replacement: &str) -> Result<String> {
    ensure!(
        source.matches(anchor).count() == 1,
        "reviewed source anchor is not unique"
    );
    Ok(source.replacen(anchor, replacement, 1))
}
fn section<'a>(source: &'a str, start: &str, end: &str) -> Result<&'a str> {
    ensure!(
        source.matches(start).count() == 1,
        "ambiguous reviewed member start"
    );
    let from = source
        .find(start)
        .ok_or_else(|| eyre::eyre!("missing reviewed member start"))?;
    let to = source[from..]
        .find(end)
        .map(|index| from + index)
        .ok_or_else(|| eyre::eyre!("missing reviewed member end"))?;
    ensure!(to > from, "invalid reviewed member boundary");
    Ok(&source[from..to])
}
fn witnessed_owners(context_name: &str) -> Result<Vec<&'static str>> {
    let (kind, target) = context_name
        .split_once('/')
        .ok_or_else(|| eyre::eyre!("invalid frozen context"))?;
    if kind == "release" || !matches!(target, "1.19.2" | "1.19.4") {
        return Ok(Vec::new());
    }
    ensure!(kind == "dev", "unknown frozen environment");
    let mut owners = vec![
        "client_manager",
        "touch_display",
        "packet_values",
        "image_resources",
    ];
    if target == "1.19.2" {
        owners.extend([
            "client_manager_custom_textures",
            "touch_display_custom_textures",
        ]);
    }
    Ok(owners)
}

/// Independent member-level oracle: begin with the target's released bytes and
/// insert only requested reviewed members. Artwork is chosen per member from
/// exact bounded development witnesses, never from the Liquid template.
fn expected_owner_body(
    fixture: &Fixture,
    path: &str,
    context: &ProjectionContext,
) -> Result<String> {
    let target = target_id(context)?;
    let spec = expected_spec(path, &format!("release/{target}"))?;
    let mut source = std::str::from_utf8(fixture.blob(spec.blob)?)?.to_owned();
    if !matches!(target, "1.19.2" | "1.19.4") {
        return Ok(source);
    }
    let manager = enabled(context, "client_manager")?;
    let touch = enabled(context, "touch_display")?;
    match path {
        STATES => {
            if manager {
                source = once(
                    &source,
                    "        registerManager();\n",
                    "        registerManager();\n        registerClientManager();\n",
                )?;
                let art_context = if enabled(context, "client_manager_custom_textures")? {
                    "dev/1.19.2"
                } else {
                    "dev/1.19.4"
                };
                let dev =
                    std::str::from_utf8(fixture.blob(expected_spec(STATES, art_context)?.blob)?)?;
                let member = section(
                    dev,
                    "    private void registerClientManager() {",
                    "    private void registerWaterTank() {",
                )?;
                source = once(
                    &source,
                    "    private void registerWaterTank() {",
                    &format!("{member}    private void registerWaterTank() {{"),
                )?;
            }
            if touch {
                source = once(
                    &source,
                    "        registerPrintingPress();\n",
                    "        registerPrintingPress();\n        registerTouchDisplay();\n",
                )?;
                let art_context = if enabled(context, "touch_display_custom_textures")? {
                    "dev/1.19.2"
                } else {
                    "dev/1.19.4"
                };
                let dev =
                    std::str::from_utf8(fixture.blob(expected_spec(STATES, art_context)?.blob)?)?;
                let member = section(
                    dev,
                    "    private void registerTouchDisplay() {",
                    "    private void registerTestBarrelTank() {",
                )?;
                source = once(
                    &source,
                    "    private void registerTestBarrelTank() {",
                    &format!("{member}    private void registerTestBarrelTank() {{"),
                )?;
            }
            if enabled(context, "image_resources")? {
                let anchor = "                    BufferBlock.ContainedResource containedResource = state.getValue(BufferBlock.CONTAINED_RESOURCE);\n";
                source = once(
                    &source,
                    anchor,
                    &format!(
                        "{anchor}                    // The image buffer uses the existing neutral texture until it has dedicated art.\n                    String texture = containedResource == BufferBlock.ContainedResource.Image\n                                     ? \"unknown\"\n                                     : containedResource.getSerializedName();\n"
                    ),
                )?;
                source = once(
                    &source,
                    "                            modLoc(\"block/buffer_\" + containedResource.getSerializedName())\n",
                    "                            modLoc(\"block/buffer_\" + texture)\n",
                )?;
            }
        }
        TAGS => {
            if manager {
                source = once(
                    &source,
                    "                .add(SFMBlocks.MANAGER.get())\n",
                    "                .add(SFMBlocks.MANAGER.get())\n                .add(SFMBlocks.CLIENT_MANAGER.get())\n",
                )?;
            }
            if touch {
                source = once(
                    &source,
                    "                .add(SFMBlocks.TUNNELLED_MANAGER.get())\n",
                    "                .add(SFMBlocks.TUNNELLED_MANAGER.get())\n                .add(SFMBlocks.TOUCH_DISPLAY.get())\n",
                )?;
            }
        }
        ITEMS => {
            if manager {
                source = once(
                    &source,
                    "        justParent(SFMItems.MANAGER, SFMBlocks.MANAGER);\n",
                    "        justParent(SFMItems.MANAGER, SFMBlocks.MANAGER);\n        justParent(SFMItems.CLIENT_MANAGER, SFMBlocks.CLIENT_MANAGER);\n",
                )?;
            }
            if touch {
                source = once(
                    &source,
                    "        justParent(SFMItems.PRINTING_PRESS, SFMBlocks.PRINTING_PRESS);\n",
                    "        justParent(SFMItems.PRINTING_PRESS, SFMBlocks.PRINTING_PRESS);\n        justParent(SFMItems.TOUCH_DISPLAY, SFMBlocks.TOUCH_DISPLAY);\n",
                )?;
            }
            if enabled(context, "packet_values")? {
                source = once(
                    &source,
                    "        basicItem(SFMItems.NETWORK_TOOL);\n",
                    "        basicItem(SFMItems.NETWORK_TOOL);\n        basicItem(SFMItems.PACKET);\n",
                )?;
            }
        }
        LOOT => {
            if manager {
                source = once(
                    &source,
                    "        writer.dropSelf(SFMBlocks.MANAGER);\n",
                    "        writer.dropSelf(SFMBlocks.MANAGER);\n        writer.dropSelf(SFMBlocks.CLIENT_MANAGER);\n",
                )?;
            }
            if touch {
                source = once(
                    &source,
                    "        writer.dropSelf(SFMBlocks.PRINTING_PRESS);\n",
                    "        writer.dropSelf(SFMBlocks.PRINTING_PRESS);\n        writer.dropSelf(SFMBlocks.TOUCH_DISPLAY);\n",
                )?;
            }
        }
        RECIPES => {
            let dev =
                std::str::from_utf8(fixture.blob(expected_spec(RECIPES, "dev/1.19.2")?.blob)?)?;
            let mut additions = String::new();
            if manager {
                additions.push_str(section(
                    dev,
                    "        beginShaped(SFMBlocks.CLIENT_MANAGER.get(), 1)\n",
                    "        beginShaped(SFMBlocks.TOUCH_DISPLAY.get(), 1)\n",
                )?);
            }
            if touch {
                additions.push_str(section(
                    dev,
                    "        beginShaped(SFMBlocks.TOUCH_DISPLAY.get(), 1)\n",
                    "        beginShaped(SFMBlocks.TUNNELLED_MANAGER.get(), 1)\n",
                )?);
            }
            if !additions.is_empty() {
                let anchor = "        beginShaped(SFMBlocks.TUNNELLED_MANAGER.get(), 1)\n                .define('A', Tags.Items.FENCES)\n                .define('B', SFMBlocks.MANAGER.get())\n                .unlockedBy(\"has_manager\", RecipeProvider.has(SFMItems.MANAGER.get()))\n                .pattern(\"A A\")\n";
                source = once(&source, anchor, &format!("{additions}{anchor}"))?;
            }
        }
        _ => eyre::bail!("unexpected datagen oracle path"),
    }
    Ok(source)
}

fn assert_owner_contract(path: &str, source: &str, context: &ProjectionContext) -> Result<()> {
    let manager = enabled(context, "client_manager")?;
    let touch = enabled(context, "touch_display")?;
    ensure!(
        source.contains("SFMBlocks.CLIENT_MANAGER") == manager
            && source.contains("SFMBlocks.TOUCH_DISPLAY") == touch,
        "basic block owner leaked or omitted"
    );
    ensure!(
        source.contains("SFMItems.PACKET") == (path == ITEMS && enabled(context, "packet_values")?),
        "packet item model was coupled to other datagen"
    );
    ensure!(
        source.contains("BufferBlock.ContainedResource.Image")
            == (path == STATES && enabled(context, "image_resources")?),
        "image buffer art branch was coupled or omitted"
    );
    ensure!(
        !source.contains("ClientManagerContainerMenu") && !source.contains("SFMMenus"),
        "GUI ownership leaked into basic block datagen"
    );
    if path == STATES {
        ensure!(
            source.contains("block/client_manager_side")
                == enabled(context, "client_manager_custom_textures")?
                && source.contains("block/touch_display_face")
                    == enabled(context, "touch_display_custom_textures")?,
            "custom artwork ownership changed"
        );
        if manager && !enabled(context, "client_manager_custom_textures")? {
            ensure!(
                source.contains("mcLoc(\"block/cyan_concrete\")"),
                "witnessed manager artwork fallback lost"
            );
        }
        if touch && !enabled(context, "touch_display_custom_textures")? {
            ensure!(
                source.contains("modLoc(\"block/buffer_unknown\")"),
                "witnessed display artwork fallback lost"
            );
        }
    }
    if path == RECIPES {
        ensure!(
            source
                .matches("beginShaped(SFMBlocks.CLIENT_MANAGER.get(), 1)")
                .count()
                == usize::from(manager)
                && source
                    .matches("beginShaped(SFMBlocks.TOUCH_DISPLAY.get(), 1)")
                    .count()
                    == usize::from(touch),
            "adjacent recipes were not independently owned"
        );
    }
    Ok(())
}

#[test]
fn all_one_hundred_frozen_datagen_witnesses_are_raw_exact_through_real_renderer() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let requested = witnessed_owners(name)?;
        let context = fixture.context(target, &requested)?;
        let bodies = fixture.render_sources(&context)?;
        for path in PATHS {
            let spec = expected_spec(path, name)?;
            assert_eq!(
                bodies[path].as_bytes(),
                fixture.blob(spec.blob)?,
                "{name}: {path}"
            );
            assert_owner_contract(path, &bodies[path], &context)?;
            cells += 1;
        }
    }
    assert_eq!(cells, 100);
    Ok(())
}

#[test]
fn independent_datagen_owner_masks_and_art_fallbacks_use_real_dependency_closure() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut profiles = 0;
    for target in ["1.19.2", "1.19.4"] {
        let owner_count = if target == "1.19.2" { 6 } else { 4 };
        for mask in 0..(1 << owner_count) {
            let requested = OWNERS[..owner_count]
                .iter()
                .enumerate()
                .filter_map(|(index, owner)| (mask & (1 << index) != 0).then_some(*owner))
                .collect::<Vec<_>>();
            let context = fixture.context(target, &requested)?;
            let bodies = fixture.render_sources(&context)?;
            for path in PATHS {
                assert_eq!(
                    bodies[path],
                    expected_owner_body(&fixture, path, &context)?,
                    "{target}: mask{mask}: {path}"
                );
                assert_owner_contract(path, &bodies[path], &context)?;
            }
            profiles += 1;
        }
    }
    assert_eq!(profiles, 80);
    for (target, _) in SUPPORTED_TARGETS {
        if !matches!(target, "1.19.2" | "1.19.4") {
            for owner in OWNERS {
                assert!(fixture.context(target, &[owner]).is_err());
            }
        }
    }
    for owner in &OWNERS[4..] {
        assert!(fixture.context("1.19.4", &[*owner]).is_err());
    }
    // Missing required underlying block/image owners cannot be granted directly.
    for owner in [
        "client_manager_custom_textures",
        "touch_display_custom_textures",
        "image_resources",
        "touch_display",
    ] {
        assert!(fixture.shared.context("1.19.2", &[owner]).is_err());
    }
    Ok(())
}

#[test]
fn every_raw_datagen_witness_rejects_unreviewed_byte_or_line_normalization() -> Result<()> {
    let fixture = Fixture::load()?;
    for spec in RAW_SPECS {
        let raw = fixture.blob(spec.blob)?;
        verify_raw(spec, raw)?;
        let mut extra = raw.to_vec();
        extra.push(b'\n');
        assert!(verify_raw(spec, &extra).is_err());
        let crlf = std::str::from_utf8(raw)?.replace('\n', "\r\n");
        assert!(verify_raw(spec, crlf.as_bytes()).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend_from_slice(raw);
        assert!(verify_raw(spec, &bom).is_err());
        assert!(verify_raw(spec, &raw[..raw.len() - 1]).is_err());
    }
    Ok(())
}

#[test]
fn common_datagen_body_edits_reach_three_api_eras_without_changing_owned_guards() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let temp_core = temp.path().join(CORE_ROOT);
    let mut proofs = 0;
    for path in PATHS {
        let source = std::str::from_utf8(&fixture.sources[path])?;
        let name = path
            .rsplit('/')
            .next()
            .and_then(|name| name.strip_suffix(".java"))
            .ok_or_else(|| eyre::eyre!("invalid datagen class name"))?;
        let line = source
            .lines()
            .find(|line| line.starts_with(&format!("public class {name} extends ")))
            .ok_or_else(|| eyre::eyre!("missing common class anchor"))?;
        let anchor = format!("{line}\n");
        let replacement = format!("{anchor}    // Common datagen body edit proof.\n");
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
        let file = temp_core.join(path);
        fs::create_dir_all(
            file.parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(&file, edited.as_bytes())?;
        for target in ["1.19.2", "1.20.3", "26.1.2"] {
            let context = fixture.context(target, &[])?;
            let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
            let selected = selection
                .inputs
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing shared input"))?;
            assert_eq!(selected.input, path);
            let bytes = read_bounded(&temp_core.join(&selected.input), MAX_SOURCE_BYTES)?;
            let actual = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            let spec = expected_spec(path, &format!("release/{target}"))?;
            let expected = once(
                std::str::from_utf8(fixture.blob(spec.blob)?)?,
                &anchor,
                &replacement,
            )?;
            assert_eq!(actual, expected, "{target}: {path}");
            proofs += 1;
        }
        assert_eq!(fixture.shared.read_source(path)?, fixture.sources[path]);
    }
    assert_eq!(proofs, 15);
    Ok(())
}

#[test]
fn promoted_datagen_core_provenance_and_historical_api_seams_remain_explicit() -> Result<()> {
    let fixture = Fixture::load()?;
    for path in PATHS {
        let source = std::str::from_utf8(&fixture.sources[path])?;
        for forbidden in ["environment", "preset", "projection_key"] {
            for directive in source.lines().filter(|line| line.trim().starts_with("{%")) {
                assert!(
                    !directive.contains(forbidden),
                    "snapshot selector in datagen source"
                );
            }
        }
    }
    for (target, _) in SUPPORTED_TARGETS {
        let context = fixture.context(target, &[])?;
        let bodies = fixture.render_sources(&context)?;
        let recipes = &bodies[RECIPES];
        let old_recipe = matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1");
        let old_serializer = old_recipe || target == "1.20.2";
        assert_eq!(recipes.contains("Consumer<FinishedRecipe>"), old_recipe);
        assert_eq!(
            recipes.contains("PrintingPressFinishedRecipe"),
            old_serializer
        );
        assert_eq!(
            recipes.contains(".special(DiskResetRecipe::new)"),
            !old_serializer
        );
        assert_eq!(
            recipes.contains("ResourceKey.create(Registries.RECIPE"),
            target == "26.1.2"
        );
        assert!(recipes.contains("tunnelled_manager_vertical"));
        if target == "26.1.2" {
            assert!(
                bodies[STATES].contains("BlockModelGenerators blockModels")
                    && bodies[STATES].contains("SpecialModelWrapper.Unbaked")
            );
            assert!(bodies[ITEMS].contains("resourceKey.identifier()()"));
            assert!(bodies[LOOT].contains("Set<ResourceKey<LootTable>> requiredTables"));
        }
    }
    Ok(())
}

#[test]
fn twenty_frozen_datagen_tree_memberships_match_all_one_hundred_exact_blob_ids() -> Result<()> {
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
            .ok_or_else(|| eyre::eyre!("missing bounded tree output"))?;
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
            eyre::bail!("datagen tree witness exceeds bound");
        }
        ensure!(
            child.wait()?.success(),
            "offline datagen membership read failed"
        );
        ensure!(
            bytes.last() == Some(&0),
            "missing exact Git tree terminator"
        );
        let mut actual = BTreeMap::new();
        for record in bytes
            .split(|byte| *byte == 0)
            .filter(|record| !record.is_empty())
        {
            let row = std::str::from_utf8(record)?;
            let (header, path) = row
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("invalid tree record"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3 && fields[0] == "100644" && fields[1] == "blob",
                "invalid frozen source type/mode"
            );
            ensure!(
                actual
                    .insert(path.to_owned(), fields[2].to_owned())
                    .is_none(),
                "duplicate datagen tree source"
            );
        }
        ensure!(actual.len() == 5, "frozen datagen membership changed");
        for path in PATHS {
            assert_eq!(
                actual
                    .get(&format!("platform/minecraft/{path}"))
                    .map(String::as_str),
                Some(expected_spec(path, name)?.blob),
                "{name}: {path}"
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 100);
    Ok(())
}
