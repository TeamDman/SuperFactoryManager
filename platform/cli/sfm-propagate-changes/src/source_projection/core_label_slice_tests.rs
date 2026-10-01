//! Post-promotion regression for the nine staged common-label templates.
//!
//! Requires real core-owned files, registered feature definitions and production
//! selection before source reads. Staged paths and frozen historical trees are
//! never production fallbacks. These tests prove source contracts, not gameplay.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
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

const PREFIX: &str = "src/main/java/ca/teamdman/sfm/common/label/";
const HOLDER: &str = "LabelPositionHolder.java";
const ACTIONS: &str = "LabelGunActions.java";
const CLIENT_ACTION: &str = "LabelGunClientManagerPushOrPullAction.java";
const MANAGER: &str = "LabelGunManagerPushOrPullAction.java";
const PICK: &str = "LabelGunPickLabelAction.java";
const TARGET_DISCOVERY: &str = "LabelGunPlanTargets.java";
const PLANNER: &str = "LabelGunPlanner.java";
const TOGGLE: &str = "LabelGunToggleLabelAction.java";
const UNSET: &str = "LabelGunUnsetBlockLabelsAction.java";
const NAMES: [&str; 9] = [
    HOLDER,
    ACTIONS,
    CLIENT_ACTION,
    MANAGER,
    PICK,
    TARGET_DISCOVERY,
    PLANNER,
    TOGGLE,
    UNSET,
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const CC: &str = "computercraft";
const CM: &str = "client_manager";
const READ: &str = "label_readonly_access";
const LEDGER: &str = "docs/tasks/sfm-core-label-slice.json";
const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";
const STAGE_PREFIX: &str =
    "platform/cli/sfm-propagate-changes/target/core-label-template-stage-v1/";
const NORMALIZATION: &str = "sfm:java_lf_final_newline@1";
const CLIENT_ONLY_DIGEST: &str =
    "sha256:a48a9213f13ad66aed9d273fe20bafb3043a76e105f4b8e5b118a836fe147510";
const CLIENT_ONLY_BYTES: usize = 1761;
const CONTEXT_COMMITS: [(&str, &str); 20] = [
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
struct Golden {
    oid: &'static str,
    raw_digest: &'static str,
    raw_bytes: usize,
    normalized_digest: &'static str,
    normalized_bytes: usize,
    crlf_count: usize,
}
const GOLDENS: [Golden; 21] = [
    Golden {
        oid: "03032bf8fe527c83f7fb16339d12d2e3f565f22c",
        raw_digest: "sha256:b60e7a97c5ff6097630e1cedb1779c07da7c7bdd0b700d10b542360553ff70c3",
        raw_bytes: 12051,
        normalized_digest: "sha256:b60e7a97c5ff6097630e1cedb1779c07da7c7bdd0b700d10b542360553ff70c3",
        normalized_bytes: 12051,
        crlf_count: 0,
    },
    Golden {
        oid: "2a5e00c698f65866283bc8fbc3320793070da720",
        raw_digest: "sha256:12b0c9c76da9d06aa9ae344c464c560a0222756fb096c31f5644b9ef16e6c7c7",
        raw_bytes: 10628,
        normalized_digest: "sha256:12b0c9c76da9d06aa9ae344c464c560a0222756fb096c31f5644b9ef16e6c7c7",
        normalized_bytes: 10628,
        crlf_count: 0,
    },
    Golden {
        oid: "2b4d648875108272a675d3a6dce570f1307d507b",
        raw_digest: "sha256:e7c2e7e90f11f4390f3473809b22818998cc938b9f460ccf42dd6ffd184d7958",
        raw_bytes: 6316,
        normalized_digest: "sha256:e7c2e7e90f11f4390f3473809b22818998cc938b9f460ccf42dd6ffd184d7958",
        normalized_bytes: 6316,
        crlf_count: 0,
    },
    Golden {
        oid: "2f7984dfa601e37b478110d8a066a912d8cbeab3",
        raw_digest: "sha256:9d843e9b9826d41a455ad7d0b88f605a5c1b32bbd2d7433d125e79bc46861089",
        raw_bytes: 11583,
        normalized_digest: "sha256:9d843e9b9826d41a455ad7d0b88f605a5c1b32bbd2d7433d125e79bc46861089",
        normalized_bytes: 11583,
        crlf_count: 0,
    },
    Golden {
        oid: "48156b535b2b2845856bbd53128be10a14c4aebb",
        raw_digest: "sha256:11837e5b678c0e62775a41f16f1203802bbee8665e7dc431d89e76c851e0d359",
        raw_bytes: 10208,
        normalized_digest: "sha256:11837e5b678c0e62775a41f16f1203802bbee8665e7dc431d89e76c851e0d359",
        normalized_bytes: 10208,
        crlf_count: 0,
    },
    Golden {
        oid: "637f12f19b5ae73aa4cb06d0cf3f910f93455846",
        raw_digest: "sha256:125013f96a35e94a3bd840388a747a379c34113f0194db4bec0d076159d9155b",
        raw_bytes: 12045,
        normalized_digest: "sha256:125013f96a35e94a3bd840388a747a379c34113f0194db4bec0d076159d9155b",
        normalized_bytes: 12045,
        crlf_count: 0,
    },
    Golden {
        oid: "8bd5028d74458f89d2afb3a45abc568d3d41ed68",
        raw_digest: "sha256:a732a05f997fc3fd881bd84b0c67b009cd9a7903e76e95c7af828b91bbf0b976",
        raw_bytes: 11478,
        normalized_digest: "sha256:a732a05f997fc3fd881bd84b0c67b009cd9a7903e76e95c7af828b91bbf0b976",
        normalized_bytes: 11478,
        crlf_count: 0,
    },
    Golden {
        oid: "a779e2bb1bc28a322d674c12b489b4f6175bc2b9",
        raw_digest: "sha256:a1dbbd32f75283384f45a22d4bbe0052367f8d1d6d88bf51d7a0e47418f6d953",
        raw_bytes: 1210,
        normalized_digest: "sha256:a1dbbd32f75283384f45a22d4bbe0052367f8d1d6d88bf51d7a0e47418f6d953",
        normalized_bytes: 1210,
        crlf_count: 0,
    },
    Golden {
        oid: "efeef91a385e7cefcaa2087f297888441d62204f",
        raw_digest: "sha256:720e6c9a5c50e83a774d2d543570fa1c2f9d776cc866e44fa336d8b31a958f61",
        raw_bytes: 5362,
        normalized_digest: "sha256:720e6c9a5c50e83a774d2d543570fa1c2f9d776cc866e44fa336d8b31a958f61",
        normalized_bytes: 5362,
        crlf_count: 0,
    },
    Golden {
        oid: "545e070f1cea014f03fd12dcbc98eb8620e81c66",
        raw_digest: "sha256:7388fe9d520567217b9ad53124991a0fabc1fd2bf996875d0a9ae53113894efb",
        raw_bytes: 835,
        normalized_digest: "sha256:31fe9652940d70e878ae8a213ebe4e612c3c68e86dcb9a0db9974ff1fff65253",
        normalized_bytes: 813,
        crlf_count: 22,
    },
    Golden {
        oid: "8a564f6f4cf315348310c7a706d8bf13a065afc7",
        raw_digest: "sha256:fbb052223da8f76f1ea316cc8d09b7d7aee9febb608f10dcebadcbf774270423",
        raw_bytes: 1251,
        normalized_digest: "sha256:acb197c07a4390d5dab7ff2bebe5cc67ab52f39203a1d4226860efbb67c142e7",
        normalized_bytes: 1232,
        crlf_count: 19,
    },
    Golden {
        oid: "90fceb0de975b0f2e1a5bbf89a1434b270d67aba",
        raw_digest: "sha256:db7c64b3dd7df5173f15c9e57d60cd3868979e0a85979d13503266a71b81d358",
        raw_bytes: 3447,
        normalized_digest: "sha256:c93e5a26ed4f17b60fc9f317dfceae030a8733b2978163e17ae20f2e7c58981a",
        normalized_bytes: 3373,
        crlf_count: 74,
    },
    Golden {
        oid: "9e2ad81cf1fb56f62292ce90bd841a3641fd205d",
        raw_digest: "sha256:4ba4140d2497f54d31264c3e411c6e5cc39dd78d2d02c6d3de0263a21dca0fee",
        raw_bytes: 3899,
        normalized_digest: "sha256:5f022f36288a764f6d5578f2b8b99d4b40807b8626a968e748171287dde0a82c",
        normalized_bytes: 3837,
        crlf_count: 62,
    },
    Golden {
        oid: "bd51ca1cd31160f748535a8e8e50f3bf39165077",
        raw_digest: "sha256:d35ee37c6c731c5057939a76e6eaf66494baca653f0a332ef6c70953ea370adf",
        raw_bytes: 1383,
        normalized_digest: "sha256:7d8f84dccb2f3b10e855dfb67980f9e16f9dcdb3f5cd5b85f422a2c42e1345ab",
        normalized_bytes: 1342,
        crlf_count: 41,
    },
    Golden {
        oid: "c798208e1d465aef42ecbfcfdb5595084933ad98",
        raw_digest: "sha256:1d4077eca6b680208d66c8418604650fc8e8114d7cff0ef8a151e7520459dea5",
        raw_bytes: 1813,
        normalized_digest: "sha256:985fc06ad18bd1531ee8fe1fae8f2678212be881d63de8d0a24ea976be2bc2c2",
        normalized_bytes: 1767,
        crlf_count: 46,
    },
    Golden {
        oid: "05159ff0114bea0e6a5243848c1bb7bed462a696",
        raw_digest: "sha256:cd0ad40ddf9913c620e95ff9cf8add63c7b3fcb44cd713ea4a4e1d0576e0d8cb",
        raw_bytes: 3434,
        normalized_digest: "sha256:315e66b3da2942f0452a4e487f5fb5aaa764bd2a08f4b47610b31f6e944807e8",
        normalized_bytes: 3340,
        crlf_count: 94,
    },
    Golden {
        oid: "22d4da5090703b18fce93c33e6d2915e6b264c6e",
        raw_digest: "sha256:017d365cef02834c20baf0de29b6a9f8a0918df4b64bab611e33497a068e1f99",
        raw_bytes: 728,
        normalized_digest: "sha256:584fd29764f8ca1dd10f62259cc135185111877417c3a646a483a1043703178e",
        normalized_bytes: 711,
        crlf_count: 17,
    },
    Golden {
        oid: "273af7b38a31da2a8c80ced1fd769b308a4d47f8",
        raw_digest: "sha256:1e1958802715420438100880ba79ac64280cc3dd92601cc021151a03ee4e69ac",
        raw_bytes: 3416,
        normalized_digest: "sha256:22f19bd8f3e2ed56106de1f923ecebc57bb8ac744738af928727cfa1865d37b7",
        normalized_bytes: 3332,
        crlf_count: 84,
    },
    Golden {
        oid: "77fe988edbb5c0ec40e6263d9a7ff38a824dbcb1",
        raw_digest: "sha256:3835d2c193f60e3bfafc14e5f43323930cb8631267a117e7d5255d617ca4d18d",
        raw_bytes: 3399,
        normalized_digest: "sha256:31bdce47e31dfe70e0d2879d7acb8b9971ab583c0eb3f6539489e3d101061d80",
        normalized_bytes: 3305,
        crlf_count: 94,
    },
    Golden {
        oid: "c190fcec57c28e85601c114000fe70b308cb75aa",
        raw_digest: "sha256:1712ce533aef343078d621afd3731c51e9c0dd95786030ba1f19c658c88e4a32",
        raw_bytes: 1335,
        normalized_digest: "sha256:79c2b87e4375bc1e0bca02c900b547c3d32dfb7e7e66e79fa5b1601b52182084",
        normalized_bytes: 1298,
        crlf_count: 37,
    },
    Golden {
        oid: "f44fb14d64101f86a8e417d6e175b1c596f86c46",
        raw_digest: "sha256:18224937b0bf25d5ac8ef6bd0f73653a835e18028ec5275ecb36933f7f379cc1",
        raw_bytes: 3721,
        normalized_digest: "sha256:32247627312f4adccb3aed8c73e5de436bd6da4b4d06cf33c1f00b6f3ee42689",
        normalized_bytes: 3633,
        crlf_count: 88,
    },
];

#[derive(Clone, Copy)]
struct TemplateGolden {
    name: &'static str,
    digest: &'static str,
    bytes: usize,
}
const TEMPLATES: [TemplateGolden; 9] = [
    TemplateGolden {
        name: "LabelGunActions.java",
        digest: "sha256:54c01c5b71bf8563fcc03363afaa0514981041dd9e7ab2f1ab5b8257f830b6a8",
        bytes: 6582,
    },
    TemplateGolden {
        name: "LabelGunClientManagerPushOrPullAction.java",
        digest: "sha256:a1dbbd32f75283384f45a22d4bbe0052367f8d1d6d88bf51d7a0e47418f6d953",
        bytes: 1210,
    },
    TemplateGolden {
        name: "LabelGunManagerPushOrPullAction.java",
        digest: "sha256:3070e346660f5160181665a6f2ccf1d5f429c1b970d8cb5de703e79554b2c332",
        bytes: 2416,
    },
    TemplateGolden {
        name: "LabelGunPickLabelAction.java",
        digest: "sha256:cd93b38e07e5133ee877a0bd02715bd894e756fbde77646a4a31c83d00689efa",
        bytes: 1489,
    },
    TemplateGolden {
        name: "LabelGunPlanner.java",
        digest: "sha256:17c121ed3cb2e80d26942547e5532116f387a196a53158c28b5215b861daada3",
        bytes: 3745,
    },
    TemplateGolden {
        name: "LabelGunPlanTargets.java",
        digest: "sha256:1f1c23ce8f4cf83822cf7c617937116e486245b618ff2cd6494bf8548c06dbe3",
        bytes: 4492,
    },
    TemplateGolden {
        name: "LabelGunToggleLabelAction.java",
        digest: "sha256:accaa7c21bf66a053fd293699a5d553072819a103f1013d5db759065e304be86",
        bytes: 1447,
    },
    TemplateGolden {
        name: "LabelGunUnsetBlockLabelsAction.java",
        digest: "sha256:9f9a028deeb68f7e3695fc541d5d6ce9bbba7529217b9e19cbd82b40e6931014",
        bytes: 3763,
    },
    TemplateGolden {
        name: "LabelPositionHolder.java",
        digest: "sha256:455b848abc02562dddec08d58776f93e58480fd48f9efaa093441d8c304f70a1",
        bytes: 18388,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: String,
    normalization_constraint: String,
    files: Vec<InputEvidence>,
}
#[derive(Facet)]
struct InputEvidence {
    path: String,
    staged_path: String,
    intended_core_path: String,
    template_sha256: String,
    template_bytes: usize,
    membership: Membership,
    raw_variants: Vec<RawVariant>,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Membership {
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
    other_token_edits: bool,
}
#[derive(Facet)]
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    raw_blob: Option<String>,
    raw_sha256: Option<String>,
    raw_bytes: Option<usize>,
    normalized_sha256: Option<String>,
    normalized_bytes: Option<usize>,
    mode: Option<String>,
    explicit_features: Vec<String>,
    feature_context_origin: String,
    stage_preview_normalized_exact: Option<bool>,
}

struct LabelFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    normalized: BTreeMap<String, Vec<u8>>,
}
impl LabelFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let source = String::from_utf8(read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            1024 * 1024,
        )?)?;
        let ledger: Ledger = facet_json::from_str(&source)?;
        ensure!(
            ledger.schema == "sfm:core_label_slice@1"
                && ledger.context_commits == pinned_commits()
                && ledger.normalization == NORMALIZATION
                && ledger.normalization_constraint
                    == "CRLF_to_LF_only_all_existing_final_LFs_retained_no_trim_or_token_edits"
                && ledger.files.len() == NAMES.len(),
            "label ledger scope, pins or normalization changed"
        );
        let read_definition = core
            .features
            .0
            .get(READ)
            .ok_or_else(|| eyre::eyre!("read-only label owner not registered"))?;
        ensure!(
            same_names(&read_definition.supported_targets, &TARGETS)
                && read_definition.requires.is_empty(),
            "read-only label API must remain an independent all-target owner"
        );
        let cc_definition = core
            .features
            .0
            .get(CC)
            .ok_or_else(|| eyre::eyre!("ComputerCraft owner missing"))?;
        ensure!(
            same_names(&cc_definition.supported_targets, &TARGETS)
                && same_names(&cc_definition.requires, &["mod_event_filtering"]),
            "ComputerCraft owner closure changed"
        );
        let cm_definition = core
            .features
            .0
            .get(CM)
            .ok_or_else(|| eyre::eyre!("Client Manager owner missing"))?;
        ensure!(
            same_names(&cm_definition.supported_targets, &TARGETS[..2])
                && !cm_definition.requires.iter().any(|flag| flag == CC),
            "Client Manager must not depend on ComputerCraft"
        );

        let mut seen = BTreeSet::new();
        let mut all_variants = BTreeSet::new();
        for input in ledger.files {
            let name = input
                .path
                .strip_prefix(PREFIX)
                .ok_or_else(|| eyre::eyre!("label path escaped scope"))?;
            let expected = template(name)?;
            ensure!(
                seen.insert(name.to_owned())
                    && input.staged_path == format!("{STAGE_PREFIX}{name}")
                    && input.intended_core_path == format!("{CORE_PREFIX}{}", input.path)
                    && input.template_sha256 == expected.digest
                    && input.template_bytes == expected.bytes,
                "label template path/hash changed"
            );
            let (targets, all, any) = membership(name)?;
            ensure!(
                same_names(&input.membership.targets, targets)
                    && same_names(&input.membership.all_features, all)
                    && same_names(&input.membership.any_features, any)
                    && input.membership.none_features.is_empty(),
                "label ledger membership changed"
            );
            if name == ACTIONS || name == CLIENT_ACTION {
                let rules = core
                    .metadata
                    .source_rules
                    .get(&input.path)
                    .ok_or_else(|| eyre::eyre!("new label source needs explicit membership"))?;
                ensure!(
                    rules.len() == 1
                        && rules[0].input == input.path
                        && same_names(&rules[0].when.targets, targets)
                        && same_names(&rules[0].when.all_features, all)
                        && same_names(&rules[0].when.any_features, any)
                        && rules[0].when.none_features.is_empty(),
                    "actual label owner predicate differs from contract"
                );
            } else if let Some(rules) = core.metadata.source_rules.get(&input.path) {
                ensure!(
                    rules.len() == 1
                        && rules[0].input == input.path
                        && rules[0].when.targets.is_empty()
                        && rules[0].when.all_features.is_empty()
                        && rules[0].when.any_features.is_empty()
                        && rules[0].when.none_features.is_empty(),
                    "released label membership was made conditional"
                );
            }
            let mut local_variants = BTreeSet::new();
            for variant in input.raw_variants {
                let fixed = golden(&variant.oid)?;
                ensure!(
                    local_variants.insert(variant.oid.clone())
                        && variant.raw_sha256 == fixed.raw_digest
                        && variant.raw_bytes == fixed.raw_bytes
                        && variant.normalized_sha256 == fixed.normalized_digest
                        && variant.normalized_bytes == fixed.normalized_bytes
                        && variant.crlf_to_lf_count == fixed.crlf_count
                        && !variant.appended_final_lf
                        && !variant.other_token_edits,
                    "label raw or normalization witness changed"
                );
                all_variants.insert(variant.oid);
            }
            ensure!(
                input.witnesses.len() == 20,
                "incomplete label context evidence"
            );
            let mut contexts = BTreeSet::new();
            let mut witnessed_variants = BTreeSet::new();
            for witness in input.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid label context"))?;
                let dev = environment == "dev";
                let cm = dev && is_d2(target);
                let expected_oid = historical_oid(name, target, dev)?;
                let flags = explicit_flags(dev, cm, dev);
                core.context(target, &flags)?;
                ensure!(
                    contexts.insert(witness.context.clone())
                        && pinned_commits().get(&witness.context) == Some(&witness.source_commit)
                        && witness.present == expected_oid.is_some()
                        && witness.raw_blob.as_deref() == expected_oid
                        && same_names(&witness.explicit_features, &flags)
                        && witness.feature_context_origin
                            == "reconstructed_explicit_owner_selection_not_historical_feature_claim",
                    "label historical context/member contract changed"
                );
                if let Some(oid) = expected_oid {
                    let fixed = golden(oid)?;
                    ensure!(
                        witness.raw_sha256.as_deref() == Some(fixed.raw_digest)
                            && witness.raw_bytes == Some(fixed.raw_bytes)
                            && witness.normalized_sha256.as_deref()
                                == Some(fixed.normalized_digest)
                            && witness.normalized_bytes == Some(fixed.normalized_bytes)
                            && witness.mode.as_deref() == Some("100644")
                            && witness.stage_preview_normalized_exact == Some(true),
                        "label full byte witness changed"
                    );
                    witnessed_variants.insert(oid.to_owned());
                } else {
                    ensure!(
                        witness.raw_sha256.is_none()
                            && witness.raw_bytes.is_none()
                            && witness.normalized_sha256.is_none()
                            && witness.normalized_bytes.is_none()
                            && witness.mode.is_none()
                            && witness.stage_preview_normalized_exact.is_none(),
                        "absent label source retained a invented witness"
                    );
                }
            }
            ensure!(
                local_variants == witnessed_variants,
                "label raw variant coverage changed"
            );
        }
        ensure!(
            seen == NAMES.into_iter().map(str::to_owned).collect()
                && all_variants == GOLDENS.iter().map(|row| row.oid.to_owned()).collect(),
            "label file/blob scope changed"
        );
        let raw = read_git_blobs(&core.repository, &all_variants)?;
        let mut normalized = BTreeMap::new();
        for row in GOLDENS {
            let bytes = &raw[row.oid];
            ensure!(
                bytes.len() == row.raw_bytes
                    && sha256(bytes) == row.raw_digest
                    && bytes.windows(2).filter(|pair| *pair == b"\r\n").count() == row.crlf_count,
                "label fixed raw blob changed"
            );
            let output = normalize(bytes)?;
            ensure!(
                output.len() == row.normalized_bytes
                    && sha256(&output) == row.normalized_digest
                    && output.len() == bytes.len() - row.crlf_count,
                "label normalization exceeded authorized byte edits"
            );
            normalized.insert(row.oid.to_owned(), output);
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            NAMES
                .iter()
                .all(|name| inventory.contains(&format!("{PREFIX}{name}"))),
            "staged label inputs not promoted to real core"
        );
        Ok(Self {
            core,
            inventory,
            normalized,
        })
    }

    fn render(&self, name: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let path = format!("{PREFIX}{name}");
        let selection = select_core_inputs(&self.core.metadata, context, &self.inventory)?;
        let Some(input) = selection.inputs.get(&path) else {
            return Ok(None);
        };
        ensure!(
            input.input == path,
            "label source uses an unreviewed alternate input"
        );
        let source = self.core.read_source(&path)?;
        let fixed = template(name)?;
        ensure!(
            source.len() == fixed.bytes
                && sha256(&source) == fixed.digest
                && !source.contains(&b'\r')
                && source.ends_with(b"\n"),
            "promoted label template bytes changed"
        );
        Ok(Some(
            render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes(),
        ))
    }

    fn assert_profile(
        &self,
        target: &str,
        cc: bool,
        cm: bool,
        read: bool,
    ) -> Result<(usize, usize)> {
        let flags = explicit_flags(cc, cm, read);
        let context = self.core.context(target, &flags)?;
        let mut present = 0;
        let mut absent = 0;
        for name in NAMES {
            let output = self.render(name, &context)?;
            if name == ACTIONS && !cc && cm {
                let output = output
                    .ok_or_else(|| eyre::eyre!("Client Manager-only shared label class omitted"))?;
                ensure!(
                    output.len() == CLIENT_ONLY_BYTES && sha256(&output) == CLIENT_ONLY_DIGEST,
                    "reconstructed Client Manager-only class changed"
                );
                present += 1;
            } else if let Some(oid) = profile_oid(name, target, cc, cm, read)? {
                ensure!(
                    output.as_deref() == Some(self.normalized[oid].as_slice()),
                    "label full rendered witness mismatch: {name} / {target}"
                );
                present += 1;
            } else {
                ensure!(output.is_none(), "disabled label class was read/rendered");
                absent += 1;
            }
        }
        Ok((present, absent))
    }

    fn body(&self, name: &str, context: &ProjectionContext) -> Result<String> {
        let output = self
            .render(name, context)?
            .ok_or_else(|| eyre::eyre!("label body expected to be present"))?;
        Ok(String::from_utf8(output)?)
    }
}

fn normalize(raw: &[u8]) -> Result<Vec<u8>> {
    ensure!(
        raw.ends_with(b"\n"),
        "this cohort cannot add a missing terminal LF"
    );
    for (index, byte) in raw.iter().enumerate() {
        if *byte == b'\r' {
            ensure!(
                raw.get(index + 1) == Some(&b'\n'),
                "lone CR is not authorized"
            );
        }
    }
    Ok(std::str::from_utf8(raw)?.replace("\r\n", "\n").into_bytes())
}
fn pinned_commits() -> BTreeMap<String, String> {
    CONTEXT_COMMITS
        .into_iter()
        .map(|(name, oid)| (name.to_owned(), oid.to_owned()))
        .collect()
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn explicit_flags(cc: bool, cm: bool, read: bool) -> Vec<&'static str> {
    let mut flags = BTreeSet::new();
    if cc {
        flags.extend([CC, "mod_event_filtering"]);
    }
    if cm {
        flags.extend([CM, "client_program_consent", "sfml_execution_side", READ]);
    }
    if read {
        flags.insert(READ);
    }
    flags.into_iter().collect()
}
fn membership(
    name: &str,
) -> Result<(
    &'static [&'static str],
    &'static [&'static str],
    &'static [&'static str],
)> {
    match name {
        ACTIONS => Ok((&TARGETS, &[], &[CC, CM])),
        CLIENT_ACTION => Ok((&TARGETS[..2], &[CM], &[])),
        name if NAMES.contains(&name) => Ok((&[], &[], &[])),
        _ => Err(eyre::eyre!("unexpected label membership input")),
    }
}
fn template(name: &str) -> Result<TemplateGolden> {
    TEMPLATES
        .iter()
        .find(|row| row.name == name)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected label template"))
}
fn golden(oid: &str) -> Result<Golden> {
    GOLDENS
        .iter()
        .find(|row| row.oid == oid)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected label raw blob"))
}
fn historical_oid(name: &str, target: &str, dev: bool) -> Result<Option<&'static str>> {
    profile_oid(name, target, dev, dev && is_d2(target), dev)
}
fn profile_oid(
    name: &str,
    target: &str,
    cc: bool,
    cm: bool,
    read: bool,
) -> Result<Option<&'static str>> {
    ensure!(
        TARGETS.contains(&target) && (!cm || is_d2(target)),
        "unsupported label profile"
    );
    let oid = match name {
        HOLDER => match (target, read) {
            ("1.21.0" | "1.21.1", false) => "8bd5028d74458f89d2afb3a45abc568d3d41ed68",
            ("1.21.0" | "1.21.1", true) => "637f12f19b5ae73aa4cb06d0cf3f910f93455846",
            ("26.1.2", false) => "2f7984dfa601e37b478110d8a066a912d8cbeab3",
            ("26.1.2", true) => "03032bf8fe527c83f7fb16339d12d2e3f565f22c",
            (_, false) => "48156b535b2b2845856bbd53128be10a14c4aebb",
            (_, true) => "2a5e00c698f65866283bc8fbc3320793070da720",
        },
        ACTIONS if cc && cm => "2b4d648875108272a675d3a6dce570f1307d507b",
        ACTIONS if cc => "efeef91a385e7cefcaa2087f297888441d62204f",
        ACTIONS if cm => {
            return Err(eyre::eyre!(
                "Client Manager-only class has an explicit reconstructed digest, not a historical raw blob"
            ));
        }
        ACTIONS => return Ok(None),
        CLIENT_ACTION if cm => "a779e2bb1bc28a322d674c12b489b4f6175bc2b9",
        CLIENT_ACTION => return Ok(None),
        MANAGER if cc => "8a564f6f4cf315348310c7a706d8bf13a065afc7",
        MANAGER => "c798208e1d465aef42ecbfcfdb5595084933ad98",
        PICK if cc => "545e070f1cea014f03fd12dcbc98eb8620e81c66",
        PICK => "bd51ca1cd31160f748535a8e8e50f3bf39165077",
        TARGET_DISCOVERY if cc => "9e2ad81cf1fb56f62292ce90bd841a3641fd205d",
        TARGET_DISCOVERY => "90fceb0de975b0f2e1a5bbf89a1434b270d67aba",
        PLANNER if cm => "f44fb14d64101f86a8e417d6e175b1c596f86c46",
        PLANNER => "77fe988edbb5c0ec40e6263d9a7ff38a824dbcb1",
        TOGGLE if cc => "22d4da5090703b18fce93c33e6d2915e6b264c6e",
        TOGGLE => "c190fcec57c28e85601c114000fe70b308cb75aa",
        UNSET if cc => "273af7b38a31da2a8c80ced1fd769b308a4d47f8",
        UNSET => "05159ff0114bea0e6a5243848c1bb7bed462a696",
        _ => return Err(eyre::eyre!("unexpected label profile input")),
    };
    Ok(Some(oid))
}

#[test]
fn label_slice_matches_twenty_historical_member_and_full_byte_contexts() -> Result<()> {
    let fixture = LabelFixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXT_COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed context");
        let dev = environment == "dev";
        let (p, a) = fixture.assert_profile(target, dev, dev && is_d2(target), dev)?;
        present += p;
        absent += a;
    }
    assert_eq!((present, absent), (152, 28));
    Ok(())
}

#[test]
fn label_slice_preserves_independent_cc_client_and_readonly_owner_profiles() -> Result<()> {
    let fixture = LabelFixture::load()?;
    let mut profiles = 0;
    let mut present = 0;
    let mut absent = 0;
    for target in TARGETS {
        for mask in 0_u8..if is_d2(target) { 8 } else { 4 } {
            let cc = mask & 1 != 0;
            let read = mask & 2 != 0;
            let cm = mask & 4 != 0;
            if cm && !read {
                continue;
            }
            let (p, a) = fixture.assert_profile(target, cc, cm, read)?;
            profiles += 1;
            present += p;
            absent += a;
        }
    }
    assert_eq!((profiles, present, absent), (44, 336, 60));
    Ok(())
}

#[test]
fn client_manager_label_actions_do_not_enable_computercraft_or_old_player_delegation() -> Result<()>
{
    let fixture = LabelFixture::load()?;
    for target in &TARGETS[..2] {
        let context = fixture
            .core
            .context(target, &explicit_flags(false, true, true))?;
        assert!(!context.features[CC]);
        let actions = fixture.body(ACTIONS, &context)?;
        assert!(actions.contains("ClientManagerBlockEntity manager"));
        assert!(!actions.contains("import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;"));
        assert!(!actions.contains("public static LabelGunActionResult toggle("));
        assert!(!actions.contains("Level level"));
        assert!(
            fixture
                .body(PLANNER, &context)?
                .contains("instanceof ClientManagerBlockEntity clientManager")
        );
        assert!(
            fixture
                .body(TOGGLE, &context)?
                .contains("// if any missing label, make all blocks have label")
        );
        assert!(
            !fixture
                .body(TARGET_DISCOVERY, &context)?
                .contains("boolean contiguous")
        );
        let cc = fixture
            .core
            .context(target, &explicit_flags(true, false, false))?;
        assert!(fixture.render(CLIENT_ACTION, &cc)?.is_none());
        assert!(
            !fixture
                .body(PLANNER, &cc)?
                .contains("ClientManagerBlockEntity")
        );
        let actions = fixture.body(ACTIONS, &cc)?;
        assert!(actions.contains("public static LabelGunActionResult toggle("));
        assert!(!actions.contains("ClientManagerBlockEntity"));
    }
    for target in &TARGETS[2..] {
        assert!(
            fixture
                .core
                .context(target, &explicit_flags(false, true, true))
                .is_err()
        );
    }
    Ok(())
}

#[test]
fn label_component_nbt_and_datacomponentgetter_apis_follow_mc_not_feature_environment() -> Result<()>
{
    let fixture = LabelFixture::load()?;
    for target in TARGETS {
        for read in [false, true] {
            let context = fixture
                .core
                .context(target, &explicit_flags(false, false, read))?;
            let body = fixture.body(HOLDER, &context)?;
            assert_eq!(
                body.contains("public static LabelPositionHolder fromReadOnly"),
                read
            );
            let components = matches!(target, "1.21.0" | "1.21.1" | "26.1.2");
            assert_eq!(body.contains("STREAM_CODEC"), components);
            assert_eq!(body.contains("deserialize(CompoundTag tag)"), !components);
            assert_eq!(
                body.contains("from(DataComponentGetter components)"),
                target == "26.1.2"
            );
            assert!(body.contains("This mutably borrows the cache entry."));
            let mut renamed = context.clone();
            renamed.environment = "release".to_owned();
            renamed.projection_key = "arbitrary/nested/label-review".to_owned();
            renamed.preset = "different-catalog-label".to_owned();
            assert_eq!(
                fixture.render(HOLDER, &context)?,
                fixture.render(HOLDER, &renamed)?
            );
        }
    }
    Ok(())
}

#[test]
fn label_normalization_preserves_whitespace_and_rejects_unapproved_eof_changes() -> Result<()> {
    let _fixture = LabelFixture::load()?;
    assert_eq!(normalize(b" x \r\n\r\n")?, b" x \n\n");
    assert_eq!(normalize(b"x\n\n")?, b"x\n\n");
    assert!(normalize(b"").is_err());
    assert!(normalize(b"x").is_err());
    assert!(normalize(b"x\ry\n").is_err());
    assert!(normalize(&[0xff, b'\n']).is_err());
    assert_eq!(GOLDENS.iter().filter(|row| row.crlf_count > 0).count(), 12);
    Ok(())
}
