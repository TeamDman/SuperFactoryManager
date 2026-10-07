//! Frozen source-only FormItem, renderer and LabelGun migration goldens.
//!
//! These bounded offline blobs are migration test oracles, never production
//! sources. Actual core selection and Liquid rendering are tested separately
//! from Java compilation, model registration, input or gameplay.
//! Deliberate later core edits require an explicit golden-evidence refresh.

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

const LEDGER: &str = "docs/tasks/sfm-core-form-label-slice.json";
const MAX_BYTES: u64 = 128 * 1024;
const RENDERER: usize = 0;
const FORM: usize = 1;
const LABEL: usize = 2;
const FLAGS: [&str; 3] = [
    "form_readonly_access",
    "label_readonly_access",
    "tooltip_mode_override",
];
const ALL_TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
struct ContextGolden {
    name: &'static str,
    commit: &'static str,
    blobs: [&'static str; 3],
}
const CONTEXTS: [ContextGolden; 20] = [
    ContextGolden {
        name: "dev/1.19.2",
        commit: "f3ff2f6425434f36c7c680fa909c977b158e1860",
        blobs: [
            "1c93441058fdd2ba806ca3e1c9faede49f4399bc",
            "726522ec6d97f35857896b24e6537d06e15e5532",
            "f3dfec153bf729aa6e0c0c5a689aa87d9c7a1709",
        ],
    },
    ContextGolden {
        name: "dev/1.19.4",
        commit: "2e3b561c15d663fb89fd353ccc2af67eeb0c2053",
        blobs: [
            "2cb1cbe85dcca5c5a54d4734deeea3ac90a8d779",
            "ceb034789cbc65ed8a3326044d4587a3c059507d",
            "f3dfec153bf729aa6e0c0c5a689aa87d9c7a1709",
        ],
    },
    ContextGolden {
        name: "dev/1.20",
        commit: "6bf4845761d06560fc5e0e36018b5d583589004d",
        blobs: [
            "d730bb89fb6da8259f9086fccd993007a265b24c",
            "ceb034789cbc65ed8a3326044d4587a3c059507d",
            "ceb794c1ef7fc43234079f1aeb9fb267732de424",
        ],
    },
    ContextGolden {
        name: "dev/1.20.1",
        commit: "faa040ce14dd825f2dd9716ea59508bf46278c06",
        blobs: [
            "d730bb89fb6da8259f9086fccd993007a265b24c",
            "ceb034789cbc65ed8a3326044d4587a3c059507d",
            "ceb794c1ef7fc43234079f1aeb9fb267732de424",
        ],
    },
    ContextGolden {
        name: "dev/1.20.2",
        commit: "a829fb4db2ae06eddd2defb22e33f0b45a4b3ff9",
        blobs: [
            "6fd1b206d4e9979b8b83bb4edd45d52364e7af1c",
            "6ffd1ad3364df071cba2b1353f5efc73863afc9f",
            "ceb794c1ef7fc43234079f1aeb9fb267732de424",
        ],
    },
    ContextGolden {
        name: "dev/1.20.3",
        commit: "704aa69edad5376d8d6cfb0b0ef7845af077e647",
        blobs: [
            "6fd1b206d4e9979b8b83bb4edd45d52364e7af1c",
            "d0d52039423502ee73363c846c2a1644ad60a009",
            "ceb794c1ef7fc43234079f1aeb9fb267732de424",
        ],
    },
    ContextGolden {
        name: "dev/1.20.4",
        commit: "11d3ed07d654ff801329f17cf1eb81c2b347eecd",
        blobs: [
            "6fd1b206d4e9979b8b83bb4edd45d52364e7af1c",
            "d0d52039423502ee73363c846c2a1644ad60a009",
            "ceb794c1ef7fc43234079f1aeb9fb267732de424",
        ],
    },
    ContextGolden {
        name: "dev/1.21.0",
        commit: "43068d610b1c053c6569be486439769eec2ae9ef",
        blobs: [
            "33064b6af287e1b0619f4bc823bac1aa1a8db16a",
            "bf103ae107602a0cc4c0dc83d8083248150671db",
            "2e8a697febd1cf0b29f691f47a34072205bddd06",
        ],
    },
    ContextGolden {
        name: "dev/1.21.1",
        commit: "7524ab5512878b773e212600c9578b2bc4db4717",
        blobs: [
            "33064b6af287e1b0619f4bc823bac1aa1a8db16a",
            "bf103ae107602a0cc4c0dc83d8083248150671db",
            "2e8a697febd1cf0b29f691f47a34072205bddd06",
        ],
    },
    ContextGolden {
        name: "dev/26.1.2",
        commit: "6bd27f03863ddd041344cbc3da51b01a7b3c3e1f",
        blobs: [
            "048ee3fc6046f3d0a768bf7566973087eb15409b",
            "87b9acf2d928702105c72fcff06991e61c342421",
            "cf8775cadd538b5a611478b34e3e50e44978a8f2",
        ],
    },
    ContextGolden {
        name: "release/1.19.2",
        commit: "31135b8e86801b862d5cb2283c7c5878b7cc5bb4",
        blobs: [
            "e869859a3a0767bb3b4d43a096147d56aa7a0915",
            "b8b17c17e99d1f9578d3a4048e8b5bc127dad7fb",
            "d400274ac168e8218dccc7c0dd166ba2fe55f507",
        ],
    },
    ContextGolden {
        name: "release/1.19.4",
        commit: "23785b63e3e1fe35e5be6a6d89b6638ee0ff0daa",
        blobs: [
            "d730bb89fb6da8259f9086fccd993007a265b24c",
            "03a1561340271b05fce70c57720949f35abb4337",
            "d400274ac168e8218dccc7c0dd166ba2fe55f507",
        ],
    },
    ContextGolden {
        name: "release/1.20",
        commit: "3df18123a19535fd0e5d1dc81aa302105c3fd2f6",
        blobs: [
            "d730bb89fb6da8259f9086fccd993007a265b24c",
            "03a1561340271b05fce70c57720949f35abb4337",
            "d400274ac168e8218dccc7c0dd166ba2fe55f507",
        ],
    },
    ContextGolden {
        name: "release/1.20.1",
        commit: "bb5babf12f467235b3a44ad5098666ee3ed171ec",
        blobs: [
            "d730bb89fb6da8259f9086fccd993007a265b24c",
            "03a1561340271b05fce70c57720949f35abb4337",
            "d400274ac168e8218dccc7c0dd166ba2fe55f507",
        ],
    },
    ContextGolden {
        name: "release/1.20.2",
        commit: "cfbbafaeda4a006ae32743a92de330711983056b",
        blobs: [
            "6fd1b206d4e9979b8b83bb4edd45d52364e7af1c",
            "58004256fb65e25fd3700194fd1104250b0c5791",
            "d400274ac168e8218dccc7c0dd166ba2fe55f507",
        ],
    },
    ContextGolden {
        name: "release/1.20.3",
        commit: "1b7f9605da0ef13c7601daf3786545868dfc3c78",
        blobs: [
            "6fd1b206d4e9979b8b83bb4edd45d52364e7af1c",
            "51e54d5ae857124a5401ca331dc525c1fa63af87",
            "d400274ac168e8218dccc7c0dd166ba2fe55f507",
        ],
    },
    ContextGolden {
        name: "release/1.20.4",
        commit: "a637581b5e1078d7cc0ca68333add568e3e387ff",
        blobs: [
            "6fd1b206d4e9979b8b83bb4edd45d52364e7af1c",
            "51e54d5ae857124a5401ca331dc525c1fa63af87",
            "d400274ac168e8218dccc7c0dd166ba2fe55f507",
        ],
    },
    ContextGolden {
        name: "release/1.21.0",
        commit: "6bfab8a21e7a5bbeb0bb136bf4e5b26f6ce03e25",
        blobs: [
            "33064b6af287e1b0619f4bc823bac1aa1a8db16a",
            "e38b2b54882f0abf97b76de5c3ccc4bf73fd062d",
            "5d2e2bdc6fb7b6ad7d18047e26fa3ba7c467c790",
        ],
    },
    ContextGolden {
        name: "release/1.21.1",
        commit: "f5366c79c823ff52712130e69dd9c8166c70bd14",
        blobs: [
            "33064b6af287e1b0619f4bc823bac1aa1a8db16a",
            "e38b2b54882f0abf97b76de5c3ccc4bf73fd062d",
            "5d2e2bdc6fb7b6ad7d18047e26fa3ba7c467c790",
        ],
    },
    ContextGolden {
        name: "release/26.1.2",
        commit: "fe32b29453b13b4f3050ad441677c7eb79e80814",
        blobs: [
            "048ee3fc6046f3d0a768bf7566973087eb15409b",
            "881a0290f72c2f477b81e055544d7d95a1a864b6",
            "c61616b110d78e5de9e86d1c3a745d1b63268da8",
        ],
    },
];
struct FileGolden {
    path: &'static str,
    bytes: u64,
    digest: &'static str,
}
const FILES: [FileGolden; 3] = [
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/render/FormItemRenderer.java",
        bytes: 11759,
        digest: "sha256:419d9630b9cba91378130c493c78bb6d5cb1ffd157d8a438a41a03a055727af3",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/common/item/FormItem.java",
        bytes: 8975,
        digest: "sha256:6f672d64619cbf6194b77984d59f59836fd0419ae6a0d5f6e7a95752f5a5d2c8",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/common/item/LabelGunItem.java",
        bytes: 27130,
        digest: "sha256:99d8bda7a5c826eb557ce6465ce772ef0715db056b05e919a45725e35b70866b",
    },
];
struct RawGolden {
    blob: &'static str,
    bytes: u64,
    digest: &'static str,
}
const RAW: [RawGolden; 26] = [
    RawGolden {
        blob: "1c93441058fdd2ba806ca3e1c9faede49f4399bc",
        bytes: 3617,
        digest: "sha256:b728c7a1d1091a6d749d35180129ef9615ffba51ae153837ce5d764cd615da53",
    },
    RawGolden {
        blob: "726522ec6d97f35857896b24e6537d06e15e5532",
        bytes: 3001,
        digest: "sha256:87594911bfed0c5df74885f45b71e8d47a5b59855d7ecdb2e0cefca4a971f128",
    },
    RawGolden {
        blob: "f3dfec153bf729aa6e0c0c5a689aa87d9c7a1709",
        bytes: 14949,
        digest: "sha256:8aff1546a0e9a4c0d3fd18c02cfc46f55c7dcf5c9e54a2a4904fa46a887ada47",
    },
    RawGolden {
        blob: "2cb1cbe85dcca5c5a54d4734deeea3ac90a8d779",
        bytes: 3574,
        digest: "sha256:c6b14ae1bbbade2003bb605b508d766039531387a9e3ceb3546d5ae9e6080bab",
    },
    RawGolden {
        blob: "ceb034789cbc65ed8a3326044d4587a3c059507d",
        bytes: 2906,
        digest: "sha256:a464d2545e178f52ea11c84e9db35e812a2c7adf7afad95b2ae23a47bfa6b7e9",
    },
    RawGolden {
        blob: "d730bb89fb6da8259f9086fccd993007a265b24c",
        bytes: 3587,
        digest: "sha256:b70504f135c258ce6175e8da548fa9409da32689d61ba10d6bef0316cf02ac84",
    },
    RawGolden {
        blob: "ceb794c1ef7fc43234079f1aeb9fb267732de424",
        bytes: 14950,
        digest: "sha256:dd71af74df936912b691cf9bbafb8a327c7d27d080ecc70b9683ac6c18646e64",
    },
    RawGolden {
        blob: "6fd1b206d4e9979b8b83bb4edd45d52364e7af1c",
        bytes: 3591,
        digest: "sha256:28b5d369e2d9218fad25834417745baa19361965bd1b118cefc63093a7c32852",
    },
    RawGolden {
        blob: "6ffd1ad3364df071cba2b1353f5efc73863afc9f",
        bytes: 2910,
        digest: "sha256:4bc2e143016763d96462bcf99256c3bccc358f1581309a2ef51e7b074e366f0d",
    },
    RawGolden {
        blob: "d0d52039423502ee73363c846c2a1644ad60a009",
        bytes: 2956,
        digest: "sha256:b6fcf713c3ab58db8f27f7b48e55f338c0cbc87c3962d7b5d4cc5d1110f657c7",
    },
    RawGolden {
        blob: "33064b6af287e1b0619f4bc823bac1aa1a8db16a",
        bytes: 3648,
        digest: "sha256:f3c8ae1afbd50cc358a6632cafae26b41ba4ca71676bdea8ad309897f8a9ffa1",
    },
    RawGolden {
        blob: "bf103ae107602a0cc4c0dc83d8083248150671db",
        bytes: 2525,
        digest: "sha256:500e7dd863a609cf39edfdc05752fec1fcbc83db0868361cc489bc1e82e9e4ac",
    },
    RawGolden {
        blob: "2e8a697febd1cf0b29f691f47a34072205bddd06",
        bytes: 15477,
        digest: "sha256:f280739ca8bf3801bd9ddbd019928d6c25b0018dc897acec9236659d90640eb5",
    },
    RawGolden {
        blob: "048ee3fc6046f3d0a768bf7566973087eb15409b",
        bytes: 3834,
        digest: "sha256:0aca3c9155cfb1fa193bfeb06f20a5380c54621b93f9b7a090c23102d070ad7d",
    },
    RawGolden {
        blob: "87b9acf2d928702105c72fcff06991e61c342421",
        bytes: 2954,
        digest: "sha256:4fa7943b82d33413e852824760dfaeaacd83aeab9d7bbdf1eac3750f93b0fe98",
    },
    RawGolden {
        blob: "cf8775cadd538b5a611478b34e3e50e44978a8f2",
        bytes: 15774,
        digest: "sha256:039ec1bd4239ed3b18b1b0ae2403e8a7179a8bcea6a11fabbc9479a959a3eb63",
    },
    RawGolden {
        blob: "e869859a3a0767bb3b4d43a096147d56aa7a0915",
        bytes: 3630,
        digest: "sha256:6c5f24a68be7c669518b80a9c3bb7aaa46e5ff20c29977c907b06b33455283fb",
    },
    RawGolden {
        blob: "b8b17c17e99d1f9578d3a4048e8b5bc127dad7fb",
        bytes: 2662,
        digest: "sha256:4eff29e2c9192e6641bf4d61160e6e6aac3f8b4c16a31606c5d3644670441741",
    },
    RawGolden {
        blob: "d400274ac168e8218dccc7c0dd166ba2fe55f507",
        bytes: 14480,
        digest: "sha256:9f76946a7947e28de0d539d9aa72f9c976a75bec38611dd281cf7f2050a9e008",
    },
    RawGolden {
        blob: "03a1561340271b05fce70c57720949f35abb4337",
        bytes: 2567,
        digest: "sha256:ad2f16c9ec3151ef4b410b4e0a3eafc52733cdad0bce389287650879c152925f",
    },
    RawGolden {
        blob: "58004256fb65e25fd3700194fd1104250b0c5791",
        bytes: 2571,
        digest: "sha256:2d79784793c652d0d61491bace6b47a2f6a1f5a83fd462cffc031a623e50a478",
    },
    RawGolden {
        blob: "51e54d5ae857124a5401ca331dc525c1fa63af87",
        bytes: 2617,
        digest: "sha256:25775b2d3b08dbe3223f30ed542e1f9e0d5266771169e9fbbdd47babe9ad7b01",
    },
    RawGolden {
        blob: "e38b2b54882f0abf97b76de5c3ccc4bf73fd062d",
        bytes: 2258,
        digest: "sha256:4980d352fe6f27e1a241d3b68e8465390ddaa351eda894705ac78b967e8bbe56",
    },
    RawGolden {
        blob: "5d2e2bdc6fb7b6ad7d18047e26fa3ba7c467c790",
        bytes: 15263,
        digest: "sha256:ed047094004f366adf4c2e1adddb8b00c5b3f35585ff3e0be192ee3bdce3ada7",
    },
    RawGolden {
        blob: "881a0290f72c2f477b81e055544d7d95a1a864b6",
        bytes: 2687,
        digest: "sha256:efb34d488f09c9099485497b62983138bd733db841c8e988db7939e5183b072f",
    },
    RawGolden {
        blob: "c61616b110d78e5de9e86d1c3a745d1b63268da8",
        bytes: 15560,
        digest: "sha256:342567cd1cfa4e880c3deb2ef09a50c81bec17ceb7d8436634358e98f71be2d7",
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
    approved_changes: Vec<String>,
    raw_byte_exact: bool,
}
#[derive(Facet)]
struct AuthoredFile {
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    present: bool,
    git_blob: String,
    raw_bytes: u64,
    raw_sha256: String,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
    lf_added: bool,
    normalization: String,
}
#[derive(Facet)]
struct RawWitness {
    path: String,
    git_blob: String,
    raw_bytes: u64,
    raw_sha256: String,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
    lf_added: bool,
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
        let ledger_bytes = read_bounded(&shared.repository.join(LEDGER), MAX_BYTES)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&ledger_bytes)?)
            .wrap_err("cannot parse bounded form/label ledger")?;
        let raw = read_git_blobs(
            &shared.repository,
            &RAW.iter().map(|row| row.blob.to_owned()).collect(),
        )?;
        let sources = FILES
            .iter()
            .map(|row| shared.read_source(row.path))
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
            .map(|row| (row.name.to_owned(), row.commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.schema == "sfm:core-form-label-slice@1"
                && self.ledger.context_commits == commits,
            "frozen context identity changed"
        );
        ensure!(self.ledger.owners.len() == 3, "owner scope changed");
        for name in FLAGS {
            let proposed = self
                .ledger
                .owners
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing proposed owner"))?;
            let actual = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("owner not registered"))?;
            let support: &[&str] = if name == "tooltip_mode_override" {
                &["1.19.2", "1.19.4"]
            } else {
                &ALL_TARGETS
            };
            ensure!(
                proposed
                    .support
                    .iter()
                    .map(String::as_str)
                    .collect::<Vec<_>>()
                    == support
                    && proposed.requires.is_empty()
                    && proposed.support == actual.supported_targets
                    && proposed.requires == actual.requires,
                "leaf-owner support/dependency contract changed"
            );
        }
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.approved_changes.is_empty()
                && self.ledger.normalization.raw_byte_exact,
            "raw form/label normalization is not approved"
        );
        for golden in &RAW {
            validate_raw(
                golden,
                self.raw
                    .get(golden.blob)
                    .ok_or_else(|| eyre::eyre!("missing pinned raw body"))?,
            )?;
        }
        ensure!(self.ledger.files.len() == 3, "file scope changed");
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
                "promoted form/label template identity changed"
            );
            ensure!(file.witnesses.len() == 20, "witness scope changed");
            let mut seen = BTreeSet::new();
            for witness in &file.witnesses {
                ensure!(seen.insert(witness.context.as_str()), "duplicate witness");
                let context = golden_context(&witness.context)?;
                let raw = raw_golden(context.blobs[index])?;
                ensure!(
                    witness.present
                        && witness.git_blob == raw.blob
                        && witness.raw_bytes == raw.bytes
                        && witness.raw_sha256 == raw.digest
                        && witness.crlf_count == 0
                        && witness.lone_cr_count == 0
                        && witness.final_lf
                        && !witness.bom
                        && !witness.lf_added
                        && witness.normalization == "none",
                    "raw per-context witness changed"
                );
            }
            ensure!(
                seen == commits.keys().map(String::as_str).collect(),
                "context coverage changed"
            );
        }
        ensure!(
            self.ledger.raw_witnesses.len() == 26,
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
                .find_map(|row| row.blobs.iter().position(|oid| *oid == raw.blob))
                .ok_or_else(|| eyre::eyre!("raw blob has no witnessed owner"))?;
            let expected_contexts = CONTEXTS
                .iter()
                .filter(|row| row.blobs[index] == raw.blob)
                .map(|row| row.name)
                .collect::<BTreeSet<_>>();
            ensure!(
                witness.path == FILES[index].path
                    && witness.raw_bytes == raw.bytes
                    && witness.raw_sha256 == raw.digest
                    && witness.crlf_count == 0
                    && witness.lone_cr_count == 0
                    && witness.final_lf
                    && !witness.bom
                    && !witness.lf_added
                    && witness.contexts.len() == expected_contexts.len()
                    && witness
                        .contexts
                        .iter()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>()
                        == expected_contexts,
                "raw identity/membership contract changed"
            );
        }
        Ok(())
    }
    fn selection(&self, context: &ProjectionContext) -> Result<()> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        for golden in &FILES {
            let input = selection
                .inputs
                .get(golden.path)
                .ok_or_else(|| eyre::eyre!("baseline form/label class was omitted"))?;
            ensure!(
                input.input == golden.path
                    && (input.template || golden.path.ends_with(".java"))
                    && !selection.omitted_paths.contains(golden.path),
                "form/label source routed away from the exact core template"
            );
        }
        Ok(())
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<String> {
        self.selection(context)?;
        render_java_source(std::str::from_utf8(&self.sources[index])?, context)
    }
    fn oracle(&self, index: usize, target: &str, flags: &[&str]) -> Result<String> {
        let is_mode = flags.contains(&"tooltip_mode_override");
        let development = match index {
            FORM => flags.contains(&"form_readonly_access"),
            LABEL => flags.contains(&"label_readonly_access"),
            RENDERER => is_mode,
            _ => eyre::bail!("invalid file index"),
        };
        let name = format!("{}/{target}", if development { "dev" } else { "release" });
        let oid = golden_context(&name)?.blobs[index];
        let mut body = std::str::from_utf8(
            self.raw
                .get(oid)
                .ok_or_else(|| eyre::eyre!("missing raw oracle"))?,
        )?
        .to_owned();
        if index == LABEL && matches!(target, "1.19.2" | "1.19.4") {
            let old = if is_mode {
                "SFMItemUtils.isClientAndMoreInfoKeyPressed()"
            } else {
                "SFMItemUtils.isClientAndMoreInfoRequested()"
            };
            let replacement = if is_mode {
                "SFMItemUtils.isClientAndMoreInfoRequested()"
            } else {
                "SFMItemUtils.isClientAndMoreInfoKeyPressed()"
            };
            // Only one optional predicate changes. A raw member oracle contains
            // no Liquid expansion or source-template-dependent expectation.
            if body.contains(old) {
                ensure!(body.matches(old).count() == 1, "tooltip raw anchor changed");
                body = body.replacen(old, replacement, 1);
            }
        }
        Ok(body)
    }
}
fn inventory() -> BTreeSet<String> {
    FILES.iter().map(|row| row.path.to_owned()).collect()
}
fn golden_context(name: &str) -> Result<&'static ContextGolden> {
    CONTEXTS
        .iter()
        .find(|row| row.name == name)
        .ok_or_else(|| eyre::eyre!("unknown source-proof context"))
}
fn raw_golden(oid: &str) -> Result<&'static RawGolden> {
    RAW.iter()
        .find(|row| row.blob == oid)
        .ok_or_else(|| eyre::eyre!("unknown raw body"))
}
fn target_of(name: &str) -> Result<&str> {
    name.split_once('/')
        .map(|(_, target)| target)
        .ok_or_else(|| eyre::eyre!("invalid context"))
}
fn validate_raw(golden: &RawGolden, bytes: &[u8]) -> Result<()> {
    ensure!(
        bytes.len() as u64 == golden.bytes
            && sha256(bytes) == golden.digest
            && bytes.ends_with(b"\n")
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw LF witness identity changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

#[test]
fn all_sixty_frozen_raw_bodies_reconstruct_through_real_selection_and_renderer() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for row in &CONTEXTS {
        let target = target_of(row.name)?;
        let flags: &[&str] = if row.name.starts_with("release/") {
            &[]
        } else if matches!(target, "1.19.2" | "1.19.4") {
            &FLAGS
        } else {
            &["form_readonly_access", "label_readonly_access"]
        };
        let context = fixture.shared.context(target, flags)?;
        fixture.selection(&context)?;
        for index in 0..FILES.len() {
            let expected = fixture
                .raw
                .get(row.blobs[index])
                .ok_or_else(|| eyre::eyre!("missing frozen oracle"))?;
            assert_eq!(
                fixture.render(index, &context)?.as_bytes(),
                expected,
                "{}: {}",
                row.name,
                FILES[index].path
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 60);
    Ok(())
}

#[test]
fn readonly_form_label_and_tooltip_owners_are_independent_across_all_targets() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    let mut profiles = 0;
    for target in ALL_TARGETS {
        let d2 = matches!(target, "1.19.2" | "1.19.4");
        for mask in 0..if d2 { 8 } else { 4 } {
            let mut flags = Vec::new();
            if mask & 1 != 0 {
                flags.push("form_readonly_access");
            }
            if mask & 2 != 0 {
                flags.push("label_readonly_access");
            }
            if d2 && mask & 4 != 0 {
                flags.push("tooltip_mode_override");
            }
            let context = fixture.shared.context(target, &flags)?;
            assert_eq!(
                context
                    .features
                    .iter()
                    .filter_map(|(name, enabled)| enabled.then_some(name.as_str()))
                    .collect::<BTreeSet<_>>(),
                flags.iter().copied().collect()
            );
            fixture.selection(&context)?;
            for index in 0..FILES.len() {
                let body = fixture.render(index, &context)?;
                assert_eq!(
                    body,
                    fixture.oracle(index, target, &flags)?,
                    "{target}, {flags:?}, {}",
                    FILES[index].path
                );
                if index == FORM {
                    assert_eq!(
                        body.contains("public static ItemStack getReferenceFromFormReadOnly("),
                        flags.contains(&"form_readonly_access")
                    );
                } else if index == LABEL {
                    assert_eq!(
                        body.contains("public static LabelGunViewMode getViewModeReadOnly("),
                        flags.contains(&"label_readonly_access")
                    );
                    assert_eq!(
                        body.contains("isClientAndMoreInfoRequested()"),
                        flags.contains(&"tooltip_mode_override")
                    );
                } else {
                    assert_eq!(
                        body.contains("SFMTooltipModeService"),
                        flags.contains(&"tooltip_mode_override")
                    );
                    assert_eq!(
                        body.contains("SFMKeyMappings"),
                        !flags.contains(&"tooltip_mode_override")
                    );
                }
                cells += 1;
            }
            for unrelated in [
                "computercraft",
                "client_manager",
                "client_manager_gui",
                "packet_values",
                "packet_computation",
            ] {
                assert_ne!(context.features.get(unrelated), Some(&true), "{unrelated}");
            }
            profiles += 1;
        }
        if !d2 {
            assert!(
                fixture
                    .shared
                    .context(target, &["tooltip_mode_override"])
                    .is_err()
            );
        }
    }
    assert_eq!((profiles, cells), (48, 144));
    Ok(())
}

#[test]
fn released_renderer_form_components_and_label_codecs_keep_genuine_version_apis() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in ALL_TARGETS {
        let context = fixture.shared.context(target, &[])?;
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
        let modern = matches!(target, "1.21.0" | "1.21.1" | "26.1.2");
        let form = fixture.render(FORM, &context)?;
        assert_eq!(
            form.contains("new Item.Properties().tab(SFMCreativeTabs.MAIN)"),
            target == "1.19.2"
        );
        assert_eq!(
            form.contains("stack.serializeNBT()"),
            matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1" | "1.20.2")
        );
        assert_eq!(
            form.contains("stack.save(new CompoundTag())"),
            matches!(target, "1.20.3" | "1.20.4")
        );
        assert_eq!(form.contains("new ItemStackBox(stack)"), modern);
        assert_eq!(
            form.contains("getBorrowedReferenceFromForm(stack).copy()"),
            modern
        );
        assert_eq!(form.contains("initializeClient("), !modern);
        assert_eq!(
            form.contains("implements TooltipProvider"),
            target == "26.1.2"
        );
        let renderer = fixture.render(RENDERER, &context)?;
        assert_eq!(
            renderer.contains("ItemTransforms.TransformType"),
            target == "1.19.2"
        );
        assert_eq!(
            renderer.contains("ModelResourceLocation.standalone("),
            matches!(target, "1.21.0" | "1.21.1")
        );
        assert_eq!(
            renderer.contains("SpecialModelRenderer<FormItemRenderer.Data>"),
            target == "26.1.2"
        );
        assert_eq!(
            renderer.contains("BlockEntityWithoutLevelRenderer"),
            target != "26.1.2"
        );
        assert_eq!(renderer.contains("SubmitNodeCollector"), target == "26.1.2");
        assert_eq!(
            renderer.contains("import net.minecraftforge.client.event.ModelEvent;"),
            matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1")
        );
        let label = fixture.render(LABEL, &context)?;
        assert_eq!(
            label.contains("LabelGunViewMode implements StringRepresentable"),
            modern
        );
        assert_eq!(
            label.contains("StreamCodec<ByteBuf, LabelGunViewMode>"),
            modern
        );
        assert_eq!(
            label.contains("InteractionResultHolder<ItemStack> use("),
            target != "26.1.2"
        );
        assert_eq!(
            label.contains("return InteractionResult.SUCCESS_SERVER;"),
            target == "26.1.2"
        );
        assert_eq!(
            label.contains("implements TooltipProvider"),
            target == "26.1.2"
        );
        assert!(label.contains("sendLabelGunUsePacketFromClientWithConfirmationIfNecessary"));
    }
    Ok(())
}

#[test]
fn readonly_helpers_never_create_legacy_tags_and_old_getters_remain_when_owner_is_off() -> Result<()>
{
    let fixture = Fixture::load()?;
    for target in ALL_TARGETS {
        let context = fixture
            .shared
            .context(target, &["form_readonly_access", "label_readonly_access"])?;
        let modern = matches!(target, "1.21.0" | "1.21.1" | "26.1.2");
        let form = fixture.render(FORM, &context)?;
        let form_helper = form
            .split_once("public static ItemStack getReferenceFromFormReadOnly(ItemStack stack)")
            .and_then(|(_, tail)| {
                tail.split_once("    @MCVersionDependentBehaviour")
                    .map(|(body, _)| body)
            })
            .ok_or_else(|| eyre::eyre!("form readonly boundaries changed"))?;
        assert!(!form_helper.contains("getOrCreateTag"));
        if modern {
            assert!(form_helper.contains("return getBorrowedReferenceFromForm(stack);"));
        } else {
            assert!(form_helper.contains("var tag = stack.getTag();"));
            assert!(form_helper.contains("tag == null ? ItemStack.EMPTY"));
        }
        let label = fixture.render(LABEL, &context)?;
        let label_helper = label
            .split_once("public static LabelGunViewMode getViewModeReadOnly(ItemStack stack)")
            .and_then(|(_, tail)| tail.split_once("    /**").map(|(body, _)| body))
            .ok_or_else(|| eyre::eyre!("label readonly boundaries changed"))?;
        assert!(!label_helper.contains("getOrCreateTag"));
        if modern {
            assert!(label_helper.contains("return getViewMode(stack);"));
        } else {
            assert!(label_helper.contains("var tag = stack.getTag();"));
            assert!(label_helper.contains("tag == null ? 0"));
            assert!(
                label_helper
                    .contains("if (ordinal < 0 || ordinal >= LabelGunViewMode.values().length)")
            );
        }
        let off = fixture.shared.context(target, &[])?;
        assert!(
            !fixture
                .render(FORM, &off)?
                .contains("getReferenceFromFormReadOnly")
        );
        assert!(!fixture.render(LABEL, &off)?.contains("getViewModeReadOnly"));
        assert_eq!(
            fixture.render(FORM, &off)?,
            fixture.oracle(FORM, target, &[])?
        );
        assert_eq!(
            fixture.render(LABEL, &off)?,
            fixture.oracle(LABEL, target, &[])?
        );
    }
    Ok(())
}

#[test]
fn raw_witnesses_reject_bom_line_ending_token_and_whitespace_normalization() -> Result<()> {
    let fixture = Fixture::load()?;
    for golden in &RAW {
        let bytes = fixture
            .raw
            .get(golden.blob)
            .ok_or_else(|| eyre::eyre!("missing raw witness"))?;
        validate_raw(golden, bytes)?;
        let source = std::str::from_utf8(bytes)?;
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(bytes);
        let mutations = [
            bom,
            source.replace('\n', "\r\n").into_bytes(),
            bytes[..bytes.len() - 1].to_vec(),
            source.replacen("public ", "private ", 1).into_bytes(),
            source.replacen("    ", "\t", 1).into_bytes(),
        ];
        for mutation in mutations {
            assert!(
                validate_raw(golden, &mutation).is_err(),
                "unreviewed normalization accepted: {}",
                golden.blob
            );
        }
    }
    Ok(())
}

#[test]
fn common_edits_propagate_to_three_api_generations_without_touching_version_or_feature_guards()
-> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(super::core_inputs::CORE_ROOT);
    let mut cells = 0;
    for (index, golden) in FILES.iter().enumerate() {
        let original = std::str::from_utf8(&fixture.sources[index])?;
        let anchor = if index == RENDERER {
            "package ca.teamdman.sfm.client.render;\n"
        } else {
            "package ca.teamdman.sfm.common.item;\n"
        };
        ensure!(
            original.matches(anchor).count() == 1,
            "common package anchor changed"
        );
        let replacement = format!("{anchor}// Form/label common-edit proof.\n");
        let edited = original.replacen(anchor, &replacement, 1);
        let guards = |text: &str| -> Vec<String> {
            text.lines()
                .filter(|line| line.trim_start().starts_with("{%"))
                .map(str::to_owned)
                .collect()
        };
        assert_eq!(guards(original), guards(&edited));
        let path = root.join(golden.path);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(&path, edited.as_bytes())?;
        for target in ["1.19.2", "1.21.0", "26.1.2"] {
            let context = fixture
                .shared
                .context(target, &["form_readonly_access", "label_readonly_access"])?;
            let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
            let input = selection
                .inputs
                .get(golden.path)
                .ok_or_else(|| eyre::eyre!("common edit source not selected"))?;
            ensure!(
                input.input == golden.path && (input.template || golden.path.ends_with(".java")),
                "edit fixture is not core-owned"
            );
            let bytes = read_bounded(&root.join(&input.input), MAX_BYTES)?;
            assert_eq!(std::str::from_utf8(&bytes)?, edited);
            let rendered = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            assert_eq!(
                rendered,
                fixture
                    .render(index, &context)?
                    .replacen(anchor, &replacement, 1)
            );
            cells += 1;
        }
        assert_eq!(
            fixture.shared.read_source(golden.path)?,
            fixture.sources[index]
        );
    }
    assert_eq!(cells, 9);
    Ok(())
}

#[test]
fn all_sixty_offline_tree_memberships_match_exact_frozen_ids() -> Result<()> {
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
        for golden in &FILES {
            command.arg(format!("platform/minecraft/{}", golden.path));
        }
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("missing offline membership output"))?;
        let mut bytes = Vec::new();
        let result = reader.by_ref().take(8193).read_to_end(&mut bytes);
        drop(reader);
        if result.is_err() || bytes.len() > 8192 {
            let _ = child.kill();
            let _ = child.wait();
            result?;
            eyre::bail!("offline tree output exceeds bound");
        }
        ensure!(child.wait()?.success(), "offline frozen tree lookup failed");
        let mut expected = FILES
            .iter()
            .enumerate()
            .map(|(index, golden)| {
                (
                    golden.path,
                    format!(
                        "100644 blob {}\tplatform/minecraft/{}\0",
                        row.blobs[index], golden.path
                    ),
                )
            })
            .collect::<Vec<_>>();
        expected.sort_by_key(|(path, _)| *path);
        let expected = expected
            .into_iter()
            .map(|(_, record)| record)
            .collect::<String>();
        assert_eq!(std::str::from_utf8(&bytes)?, expected, "{}", row.name);
        cells += FILES.len();
    }
    assert_eq!(cells, 60);
    Ok(())
}
