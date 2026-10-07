//! Four genuine Client Manager providers after atomic parent promotion.
//! Real core selectors/renderers/collector prove only source contracts.
//! Historical Git objects are bounded offline witnesses, not production input.
//! No prepared case is a Java, GUI, signing, consent or game-runtime acceptance.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CORE_ROOT;
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

const LEDGER: &str = "docs/tasks/sfm-core-client-manager-provider-slice.json";
const PATHS: [&str; 4] = [
    "src/main/java/ca/teamdman/sfm/common/blockentity/ClientManagerBlockEntity.java",
    "src/main/java/ca/teamdman/sfm/common/block/ClientManagerBlock.java",
    "src/main/java/ca/teamdman/sfm/common/blockentity/ClientManagerWorldIdentitySavedData.java",
    "src/main/java/ca/teamdman/sfm/common/containermenu/ClientManagerContainerMenu.java",
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const BASE: [&str; 4] = [
    "client_manager",
    "client_program_consent",
    "disk_readonly_access",
    "sfml_execution_side",
];
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
struct SourcePin {
    path: &'static str,
    digest: &'static str,
    bytes: usize,
}
const SOURCE_PINS: [SourcePin; 4] = [
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/blockentity/ClientManagerBlockEntity.java",
        digest: "sha256:d5e8419a0d8f1ffb70a76317f92f37afcdb80a9ceb2d98423373f0277f5e974d",
        bytes: 16044,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/block/ClientManagerBlock.java",
        digest: "sha256:a003f675a2a211773b70a61105343c92ed674ffc6caf8abf0999e2fc31d0ecc5",
        bytes: 5074,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/blockentity/ClientManagerWorldIdentitySavedData.java",
        digest: "sha256:cc154519959a2939249f1ab864ee2d683710771201be5ef0429c7a629ef1b745",
        bytes: 1430,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/containermenu/ClientManagerContainerMenu.java",
        digest: "sha256:200786512da834e857966fd64e25997f60327f81777b78542b07b1f534cab74e",
        bytes: 7312,
    },
];
#[derive(Clone, Copy)]
struct RawPin {
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}
const RAW_PINS: [RawPin; 6] = [
    RawPin {
        oid: "70ff6ad90ce2c15b7ef6a8df604a332772881b83",
        digest: "sha256:849781777c03d8e06bda652eeb86093ee0c83bebafb9d31cd04020206c02ccde",
        bytes: 4354,
    },
    RawPin {
        oid: "8c4381d5e9babd21a495c3dd1461e7c8a21ae075",
        digest: "sha256:af8fc579f39ba7e04327e2f92697f5650bd2b3367556f265d9d158f34e7e70fe",
        bytes: 3517,
    },
    RawPin {
        oid: "3f9e3e020ffd8cd50d2222884bd0e759ebf35a36",
        digest: "sha256:cc154519959a2939249f1ab864ee2d683710771201be5ef0429c7a629ef1b745",
        bytes: 1430,
    },
    RawPin {
        oid: "01fb5f22b279071a8e057f2705986e50197cbae8",
        digest: "sha256:bae9b8491ac074cea03a36b73b6880283f417c7042bea8984534deadc18a455d",
        bytes: 14334,
    },
    RawPin {
        oid: "07b7e146fc812012ffeb370ad629d2dbe68cfc09",
        digest: "sha256:200786512da834e857966fd64e25997f60327f81777b78542b07b1f534cab74e",
        bytes: 7312,
    },
    RawPin {
        oid: "751e352ec4a37da923c48c0a7e094957956e3fdc",
        digest: "sha256:8b70a8cb6696f8d8c1e17dca6bba24dbe7760d402fbbdcb373d23f55efac1857",
        bytes: 15257,
    },
];
const MODEL_PINS: [SourcePin; 11] = [
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningAcknowledgement.java",
        digest: "sha256:8de69b4c67cdb77eb703c1f0c68d5d2fa7897c85fc6e4b27430866fd76192918",
        bytes: 912,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningBody.java",
        digest: "sha256:cfeb326229ab621963883792fa959502e76852201b6e9f53f554c13304d30ecb",
        bytes: 3813,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningCodec.java",
        digest: "sha256:038be7d0522ddcc2452d6d69dec8459e569547e56a43322b0abbdf6564a49c58",
        bytes: 7207,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningMetadata.java",
        digest: "sha256:049e0bdafbddb653eaf33ed4599cd3297951a180364cbad52fd51a108dfb3ce9",
        bytes: 1870,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningSession.java",
        digest: "sha256:45013039826e255c98340e2b6ccf5944386aa86a7c8e4a1495b3fe25d87a4cba",
        bytes: 4443,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningSnapshot.java",
        digest: "sha256:273ffaff7ff66bae05e815ae737cbc07212a4c08025e6895b4f639f9f7a5a5b8",
        bytes: 1337,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningState.java",
        digest: "sha256:9c2096bdcc9b18c95c3a32373b51a7daa8be727a0c54aecc8da780d6a0ce114f",
        bytes: 8942,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramAttestation.java",
        digest: "sha256:31e5bdd9ae6488189f8fe9e568856f3c98dc233acb2ad02742fa762893972dab",
        bytes: 3965,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramAttestationCodec.java",
        digest: "sha256:1fcf0c493f1abba27d5f19eccfcdb516089546d13ca53005cb771f0e3b6719b9",
        bytes: 6771,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramAttestationHistory.java",
        digest: "sha256:6e23902c16c8b1cd22564fa9db910ee02d32b1b1a386c20fc54c660736963caa",
        bytes: 1386,
    },
    SourcePin {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramSignatureDescriptor.java",
        digest: "sha256:fe5538c9146ef5b7e0685d3ae18b4f63833b12d186331f550fe35fa551a2cd82",
        bytes: 4472,
    },
];
const LOADED_REGISTRY_PIN: SourcePin = SourcePin {
    path: "src/main/java/ca/teamdman/sfm/common/blockentity/ClientManagerLoadedRegistry.java",
    digest: "sha256:4747d24c589691198da6927de3807c947108a8eb07e3bbc4760d3b91a86e8186",
    bytes: 1325,
};
const FEATURE_CONTRACTS: [(&str, &[&str], &[&str]); 13] = [
    (
        "client_manager",
        &["1.19.2", "1.19.4"],
        &[
            "sfml_execution_side",
            "client_program_consent",
            "disk_readonly_access",
        ],
    ),
    ("client_manager_gui", &["1.19.2"], &["client_manager"]),
    (
        "client_program_signing",
        &["1.19.2", "1.19.4"],
        &["client_frame_language", "client_program_actions"],
    ),
    (
        "client_program_consent",
        &["1.19.2", "1.19.4"],
        &["sfml_execution_side"],
    ),
    (
        "disk_readonly_access",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "label_readonly_access",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    ("sfml_execution_side", &["1.19.2", "1.19.4"], &[]),
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
        "client_actions",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    ("packet_values", &["1.19.2", "1.19.4"], &[]),
    (
        "packet_computation",
        &["1.19.2", "1.19.4"],
        &["packet_values", "runtime_resource_cleanup"],
    ),
    ("runtime_resource_cleanup", &["1.19.2", "1.19.4"], &[]),
];
// Only BE changes with labels/signing; Block chooses exactly the two witnessed GUI shapes.
const PROFILE_PINS: [(&str, bool, bool, bool, usize, &str); 12] = [
    (
        "1.19.2",
        false,
        false,
        false,
        14020,
        "sha256:76e6377ad65fa044f25eda8ca46ef4cd7db32298f98678394e8def0e4253fbb2",
    ),
    (
        "1.19.2",
        false,
        false,
        true,
        14393,
        "sha256:6c945d0ebca614f342ffa5ce5d5fb6b17e9d2c7c31d883bf63dc678a62578678",
    ),
    (
        "1.19.2",
        false,
        true,
        false,
        13961,
        "sha256:85700aee4d5418a2279c807c6aa7e4304c90c69fc1945304af14bbfd73af7c46",
    ),
    (
        "1.19.2",
        false,
        true,
        true,
        14334,
        "sha256:bae9b8491ac074cea03a36b73b6880283f417c7042bea8984534deadc18a455d",
    ),
    (
        "1.19.2",
        true,
        false,
        false,
        14943,
        "sha256:3953dc439e6a3a85596482c3746b09e020218e9e8f2dd333275b3bb93b9add31",
    ),
    (
        "1.19.2",
        true,
        false,
        true,
        15316,
        "sha256:867670e3e39ba8dfb0f333df4e69cfbb93e71b3b7bc73591462b570935db3c64",
    ),
    (
        "1.19.2",
        true,
        true,
        false,
        14884,
        "sha256:cda4d66d2a797d742ca9593208e2125e37061cae3e36555e29e35e9ab532dc44",
    ),
    (
        "1.19.2",
        true,
        true,
        true,
        15257,
        "sha256:8b70a8cb6696f8d8c1e17dca6bba24dbe7760d402fbbdcb373d23f55efac1857",
    ),
    (
        "1.19.4",
        false,
        false,
        false,
        14020,
        "sha256:76e6377ad65fa044f25eda8ca46ef4cd7db32298f98678394e8def0e4253fbb2",
    ),
    (
        "1.19.4",
        false,
        false,
        true,
        14393,
        "sha256:6c945d0ebca614f342ffa5ce5d5fb6b17e9d2c7c31d883bf63dc678a62578678",
    ),
    (
        "1.19.4",
        false,
        true,
        false,
        13961,
        "sha256:85700aee4d5418a2279c807c6aa7e4304c90c69fc1945304af14bbfd73af7c46",
    ),
    (
        "1.19.4",
        false,
        true,
        true,
        14334,
        "sha256:bae9b8491ac074cea03a36b73b6880283f417c7042bea8984534deadc18a455d",
    ),
];
const OFF_LABELS: &str = "return disk.isEmpty() || disk.getTag() == null ? LabelPositionHolder.empty() : LabelPositionHolder.deserialize(disk.getTag().getCompound(\"sfm:labels\"));";
const READONLY_LABELS: &str =
    "return disk.isEmpty() ? LabelPositionHolder.empty() : LabelPositionHolder.fromReadOnly(disk);";

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    context_commits: BTreeMap<String, String>,
    feature_contracts: BTreeMap<String, FeatureContract>,
    files: Vec<FileEvidence>,
    raw_blobs: BTreeMap<String, RawEvidence>,
    guard_regions: Vec<GuardRegion>,
    profiles: Vec<Profile>,
}
#[derive(Facet)]
struct FeatureContract {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct FileEvidence {
    path: String,
    core_path: String,
    template_sha256: String,
    template_bytes: usize,
    membership: Membership,
    raw_oids: Vec<String>,
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
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    raw_oid: Option<String>,
    raw_sha256: Option<String>,
    raw_bytes: Option<usize>,
    mode: Option<String>,
    explicit_features: Vec<String>,
    features_origin: String,
}
#[derive(Facet)]
struct RawEvidence {
    raw_sha256: String,
    raw_bytes: usize,
    bom: bool,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    normalization: String,
    prefix_hex: String,
}
#[derive(Facet)]
struct GuardRegion {
    path: String,
    id: String,
    owner: String,
    original: String,
    replacement: String,
}
#[derive(Facet)]
struct Profile {
    id: String,
    target: String,
    gui: bool,
    labels: bool,
    signing: bool,
    features: Vec<String>,
    files: Vec<Golden>,
}
#[derive(Facet)]
struct Golden {
    path: String,
    present: bool,
    bytes: Option<usize>,
    sha256: Option<String>,
}

struct Fixture {
    core: CoreTestFixture,
    ledger: Ledger,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            1024 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core_client_manager_provider_slice@1"
                && ledger.normalization == "none_raw_exact_lf_final_lf"
                && ledger.context_commits
                    == CONTEXT_COMMITS
                        .into_iter()
                        .map(|(key, hash)| (key.to_owned(), hash.to_owned()))
                        .collect()
                && ledger.files.len() == 4
                && ledger.raw_blobs.len() == 6
                && ledger.guard_regions.len() == 14
                && ledger.profiles.len() == 12,
            "manager ledger scope changed"
        );
        ensure!(
            ledger.feature_contracts.len() == FEATURE_CONTRACTS.len(),
            "feature scope changed"
        );
        for (name, targets, requires) in FEATURE_CONTRACTS {
            let declared = ledger
                .feature_contracts
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing owner"))?;
            let actual = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("unregistered owner"))?;
            ensure!(
                same_names(&declared.supported_targets, targets)
                    && same_names(&declared.requires, requires)
                    && same_names(&actual.supported_targets, targets)
                    && same_names(&actual.requires, requires),
                "owner support/prerequisite changed: {name}"
            );
        }
        let mut seen = BTreeSet::new();
        for file in &ledger.files {
            let pin = source_pin(&file.path)?;
            let (owner, targets) = ownership(&file.path)?;
            ensure!(
                seen.insert(file.path.clone())
                    && file.core_path == format!("{CORE_ROOT}/{}", file.path)
                    && file.template_sha256 == pin.digest
                    && file.template_bytes == pin.bytes
                    && same_names(&file.membership.targets, targets)
                    && same_names(&file.membership.all_features, &[owner])
                    && file.membership.any_features.is_empty()
                    && file.membership.none_features.is_empty(),
                "source membership changed"
            );
            let rules = core
                .metadata
                .source_rules
                .get(&file.path)
                .ok_or_else(|| eyre::eyre!("atomic source-rule promotion is required"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == file.path
                    && rules[0].template
                    && same_names(&rules[0].when.targets, targets)
                    && same_names(&rules[0].when.all_features, &[owner])
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "actual source rule differs from proposed template:true rule"
            );
            validate_source(pin, &core.read_source(&file.path)?)?;
            ensure!(file.witnesses.len() == 20, "incomplete historical cells");
            let mut contexts = BTreeSet::new();
            let mut oids = BTreeSet::new();
            for witness in &file.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid context"))?;
                let expected = historical_oid(&file.path, environment, target)?;
                let pin = expected.map(raw_pin).transpose()?;
                let expected_features = historical_features(&core, environment, target)?;
                ensure!(
                    contexts.insert(witness.context.clone())
                        && ledger.context_commits.get(&witness.context)
                            == Some(&witness.source_commit)
                        && witness.present == expected.is_some()
                        && witness.raw_oid.as_deref() == expected
                        && witness.raw_sha256.as_deref() == pin.map(|p| p.digest)
                        && witness.raw_bytes == pin.map(|p| p.bytes)
                        && witness.mode.as_deref() == expected.map(|_| "100644")
                        && witness.explicit_features == expected_features
                        && witness.features_origin
                            == "reviewed_current_explicit_reconstruction_not_historical_manifest",
                    "historical cell/explicit current reconstruction changed"
                );
                core.context(
                    target,
                    &expected_features
                        .iter()
                        .map(String::as_str)
                        .collect::<Vec<_>>(),
                )?;
                if let Some(oid) = expected {
                    oids.insert(oid.to_owned());
                }
            }
            ensure!(
                file.raw_oids.len() == oids.len()
                    && file.raw_oids.iter().cloned().collect::<BTreeSet<_>>() == oids,
                "raw variant set changed"
            );
        }
        ensure!(
            seen == PATHS.into_iter().map(str::to_owned).collect(),
            "unexpected provider path"
        );
        let raw = read_git_blobs(
            &core.repository,
            &RAW_PINS.iter().map(|p| p.oid.to_owned()).collect(),
        )?;
        for pin in RAW_PINS {
            let row = &ledger.raw_blobs[pin.oid];
            ensure!(
                row.raw_sha256 == pin.digest
                    && row.raw_bytes == pin.bytes
                    && !row.bom
                    && row.crlf_count == 0
                    && row.lone_cr_count == 0
                    && row.final_lf
                    && row.normalization == "none"
                    && row.prefix_hex == "7061636b61676520",
                "raw identity/newline contract changed"
            );
            validate_raw(pin, &raw[pin.oid])?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "missing actual authored provider"
        );
        let fixture = Self {
            core,
            ledger,
            inventory,
            raw,
        };
        let mut profile_ids = BTreeSet::new();
        for profile in &fixture.ledger.profiles {
            ensure!(
                profile_ids.insert(profile.id.clone())
                    && profile.id
                        == format!(
                            "{}/gui-{}/labels-{}/signing-{}",
                            profile.target,
                            u8::from(profile.gui),
                            u8::from(profile.labels),
                            u8::from(profile.signing)
                        )
                    && profile.features
                        == feature_closure(
                            &fixture.core,
                            &roots(profile.gui, profile.labels, profile.signing)
                        )?
                    && profile.files.len() == PATHS.len(),
                "profile authority changed"
            );
            let pin = profile_pin(profile)?;
            let mut paths = BTreeSet::new();
            for row in &profile.files {
                ensure!(paths.insert(row.path.clone()), "duplicate profile path");
                let present = row.path != PATHS[3] || profile.gui;
                let (bytes, digest) = match row.path.as_str() {
                    path if path == PATHS[0] => (pin.4, pin.5),
                    path if path == PATHS[1] => {
                        let p = raw_pin(if profile.gui {
                            "8c4381d5e9babd21a495c3dd1461e7c8a21ae075"
                        } else {
                            "70ff6ad90ce2c15b7ef6a8df604a332772881b83"
                        })?;
                        (p.bytes, p.digest)
                    }
                    path if path == PATHS[2] => (
                        1430,
                        "sha256:cc154519959a2939249f1ab864ee2d683710771201be5ef0429c7a629ef1b745",
                    ),
                    path if path == PATHS[3] => (
                        7312,
                        "sha256:200786512da834e857966fd64e25997f60327f81777b78542b07b1f534cab74e",
                    ),
                    _ => return Err(eyre::eyre!("unreviewed golden provider")),
                };
                ensure!(
                    row.present == present
                        && row.bytes == present.then_some(bytes)
                        && row.sha256.as_deref() == present.then_some(digest),
                    "independently pinned profile body changed"
                );
            }
            ensure!(
                paths == PATHS.into_iter().map(str::to_owned).collect(),
                "profile path scope changed"
            );
            fixture.profile_context(profile)?;
        }
        Ok(fixture)
    }
    fn profile_context(&self, profile: &Profile) -> Result<ProjectionContext> {
        self.core.context(
            &profile.target,
            &profile
                .features
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>(),
        )
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selected.inputs.get(path) else {
            ensure!(
                selected.omitted_paths.contains(path),
                "missing ownership omission"
            );
            return Ok(None);
        };
        ensure!(
            input.input == path && input.template,
            "unexpected provider alias/template setting"
        );
        let source = self.core.read_source(path)?;
        validate_source(source_pin(path)?, &source)?;
        Ok(Some(
            render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes(),
        ))
    }
    fn assert_profile(&self, profile: &Profile) -> Result<()> {
        let context = self.profile_context(profile)?;
        for row in &profile.files {
            let result = self.render(&row.path, &context)?;
            ensure!(
                result.is_some() == row.present,
                "profile membership mismatch"
            );
            if let Some(result) = result {
                ensure!(
                    Some(result.len()) == row.bytes && Some(sha256(&result)) == row.sha256,
                    "real renderer body differs from static pinned golden"
                );
            }
        }
        Ok(())
    }
}
fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn ownership(path: &str) -> Result<(&'static str, &'static [&'static str])> {
    ensure!(PATHS.contains(&path), "unreviewed provider");
    Ok(if path == PATHS[3] {
        ("client_manager_gui", &TARGETS[..1])
    } else {
        ("client_manager", &TARGETS[..2])
    })
}
fn source_pin(path: &str) -> Result<SourcePin> {
    SOURCE_PINS
        .iter()
        .find(|p| p.path == path)
        .copied()
        .ok_or_else(|| eyre::eyre!("unknown source"))
}
fn raw_pin(oid: &str) -> Result<RawPin> {
    RAW_PINS
        .iter()
        .find(|p| p.oid == oid)
        .copied()
        .ok_or_else(|| eyre::eyre!("unknown raw witness"))
}
fn historical_oid(path: &str, environment: &str, target: &str) -> Result<Option<&'static str>> {
    ensure!(
        matches!(environment, "dev" | "release") && TARGETS.contains(&target),
        "invalid historical context"
    );
    let index = PATHS
        .iter()
        .position(|p| *p == path)
        .ok_or_else(|| eyre::eyre!("unknown provider"))?;
    if environment == "release"
        || !TARGETS[..2].contains(&target)
        || (index == 3 && target != "1.19.2")
    {
        return Ok(None);
    }
    Ok(Some(match (index, target) {
        (0, "1.19.2") => "751e352ec4a37da923c48c0a7e094957956e3fdc",
        (0, _) => "01fb5f22b279071a8e057f2705986e50197cbae8",
        (1, "1.19.2") => "8c4381d5e9babd21a495c3dd1461e7c8a21ae075",
        (1, _) => "70ff6ad90ce2c15b7ef6a8df604a332772881b83",
        (2, _) => "3f9e3e020ffd8cd50d2222884bd0e759ebf35a36",
        (3, _) => "07b7e146fc812012ffeb370ad629d2dbe68cfc09",
        _ => unreachable!(),
    }))
}
fn roots(gui: bool, labels: bool, signing: bool) -> Vec<&'static str> {
    let mut result = vec!["client_manager"];
    if gui {
        result.push("client_manager_gui");
    }
    if labels {
        result.push("label_readonly_access");
    }
    if signing {
        result.push("client_program_signing");
    }
    result
}
fn feature_closure(core: &CoreTestFixture, roots: &[&str]) -> Result<Vec<String>> {
    let mut features = roots
        .iter()
        .map(|name| (*name).to_owned())
        .collect::<BTreeSet<_>>();
    let mut pending = features.iter().cloned().collect::<Vec<_>>();
    while let Some(name) = pending.pop() {
        let definition = core
            .features
            .0
            .get(&name)
            .ok_or_else(|| eyre::eyre!("unknown closure root"))?;
        for required in &definition.requires {
            if features.insert(required.clone()) {
                pending.push(required.clone());
            }
        }
    }
    Ok(features.into_iter().collect())
}
fn historical_features(
    core: &CoreTestFixture,
    environment: &str,
    target: &str,
) -> Result<Vec<String>> {
    if environment == "dev" && TARGETS[..2].contains(&target) {
        feature_closure(core, &roots(target == "1.19.2", true, true))
    } else {
        Ok(Vec::new())
    }
}
fn profile_pin(profile: &Profile) -> Result<(&'static str, bool, bool, bool, usize, &'static str)> {
    PROFILE_PINS
        .iter()
        .copied()
        .find(|p| {
            p.0 == profile.target
                && p.1 == profile.gui
                && p.2 == profile.labels
                && p.3 == profile.signing
        })
        .ok_or_else(|| eyre::eyre!("invalid/unsupported profile"))
}
fn validate_source(pin: SourcePin, source: &[u8]) -> Result<()> {
    ensure!(
        source.len() == pin.bytes && sha256(source) == pin.digest,
        "authored provider changed"
    );
    Ok(())
}
fn validate_raw(pin: RawPin, source: &[u8]) -> Result<()> {
    ensure!(
        source.len() == pin.bytes
            && sha256(source) == pin.digest
            && !source.contains(&b'\r')
            && source.ends_with(b"\n")
            && !source.ends_with(b"\n\n")
            && !source.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw provider changed/normalized"
    );
    std::str::from_utf8(source)?;
    Ok(())
}
fn between<'a>(source: &'a str, start: &str, end: &str) -> Result<&'a str> {
    ensure!(source.matches(start).count() == 1, "method start changed");
    let rest = source.split_once(start).expect("checked start").1;
    let (body, _) = rest
        .split_once(end)
        .ok_or_else(|| eyre::eyre!("method end changed"))?;
    Ok(body)
}
fn before(source: &str, first: &str, second: &str) -> Result<()> {
    let first = source
        .find(first)
        .ok_or_else(|| eyre::eyre!("missing refusal anchor"))?;
    let second = source
        .find(second)
        .ok_or_else(|| eyre::eyre!("missing operation anchor"))?;
    ensure!(first < second, "operation moved before refusal");
    Ok(())
}

#[test]
fn client_manager_providers_reconstruct_all_eighty_raw_cells_and_all_on_bodies() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut counts = (0, 0);
    for (name, _) in CONTEXT_COMMITS {
        let (environment, target) = name.split_once('/').expect("constant context");
        let features = historical_features(&fixture.core, environment, target)?;
        let context = fixture.core.context(
            target,
            &features.iter().map(String::as_str).collect::<Vec<_>>(),
        )?;
        for path in PATHS {
            let expected = historical_oid(path, environment, target)?;
            assert_eq!(
                fixture.render(path, &context)?.as_deref(),
                expected.map(|oid| fixture.raw[oid].as_slice()),
                "{name}/{path}"
            );
            if expected.is_some() {
                counts.0 += 1;
            } else {
                counts.1 += 1;
            }
        }
    }
    assert_eq!(counts, (7, 73));
    // Git source never enters a production render path; exact region reversal is evidence only.
    let mut region_ids = BTreeSet::new();
    for path in PATHS {
        let mut authored = String::from_utf8(fixture.core.read_source(path)?)?;
        for region in fixture
            .ledger
            .guard_regions
            .iter()
            .rev()
            .filter(|r| r.path == path)
        {
            ensure!(
                region_ids.insert(region.id.clone())
                    && [
                        "client_manager_gui",
                        "label_readonly_access",
                        "client_program_signing"
                    ]
                    .contains(&region.owner.as_str())
                    && authored.matches(&region.replacement).count() == 1,
                "nonunique reverse region"
            );
            authored = authored.replacen(&region.replacement, &region.original, 1);
        }
        let oid = historical_oid(path, "dev", "1.19.2")?.expect("D1 genuine source");
        assert_eq!(authored.as_bytes(), fixture.raw[oid].as_slice());
    }
    assert_eq!(region_ids.len(), 14);
    Ok(())
}

#[test]
fn client_manager_providers_bind_all_original_tree_memberships() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for (name, commit) in CONTEXT_COMMITS {
        let (environment, target) = name.split_once('/').expect("constant context");
        let mut command = frozen_git_command(&fixture.core.repository);
        command
            .env("GIT_ALLOW_PROTOCOL", "")
            .env("GIT_TERMINAL_PROMPT", "0")
            .args([
                "-c",
                "protocol.allow=never",
                "-c",
                "core.fsmonitor=false",
                "ls-tree",
                "-r",
                commit,
                "--",
            ]);
        for path in PATHS {
            command.arg(format!("platform/minecraft/{path}"));
        }
        let output = command.output()?;
        ensure!(
            output.status.success() && output.stdout.len() <= 8192,
            "bounded offline tree reader failed"
        );
        let mut found = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("bad tree framing"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && found
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "bad/duplicate tree entry"
            );
        }
        for path in PATHS {
            assert_eq!(
                found
                    .get(&format!("platform/minecraft/{path}"))
                    .map(String::as_str),
                historical_oid(path, environment, target)?
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 80);
    Ok(())
}

#[test]
fn client_manager_providers_render_all_twelve_independent_gui_labels_signing_rows() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut counts = (0, 0);
    for profile in &fixture.ledger.profiles {
        fixture.assert_profile(profile)?;
        let context = fixture.profile_context(profile)?;
        assert_eq!(context.features["client_manager_gui"], profile.gui);
        assert_eq!(context.features["label_readonly_access"], profile.labels);
        assert_eq!(context.features["client_program_signing"], profile.signing);
        let be = String::from_utf8(
            fixture
                .render(PATHS[0], &context)?
                .expect("manager selected"),
        )?;
        let block = fixture.render(PATHS[1], &context)?.expect("block selected");
        let expected_block = if profile.gui {
            "8c4381d5e9babd21a495c3dd1461e7c8a21ae075"
        } else {
            "70ff6ad90ce2c15b7ef6a8df604a332772881b83"
        };
        assert_eq!(block, fixture.raw[expected_block]);
        assert_eq!(be.contains("implements MenuProvider"), profile.gui);
        assert_eq!(be.contains("createMenu("), profile.gui);
        assert_eq!(be.contains("public boolean acceptsDisk("), profile.gui);
        assert_eq!(be.contains("fromReadOnly(disk)"), profile.labels);
        assert_eq!(be.contains(OFF_LABELS), !profile.labels);
        assert_eq!(
            be.contains("SFMServerClientManagerSigningTransport"),
            profile.signing
        );
        for row in &profile.files {
            if row.present {
                counts.0 += 1;
            } else {
                counts.1 += 1;
            }
        }
    }
    assert_eq!(counts, (40, 8));
    Ok(())
}

#[test]
fn client_manager_providers_feature_off_controls_and_unrelated_owners_omit_before_reads()
-> Result<()> {
    let mut fixture = Fixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture
        .core
        .core
        .join("deliberately_missing_manager_provider_sources");
    for key in catalog.catalog.0.keys() {
        let context = fixture.core.historical_feature_off_catalog_context(key)?;
        assert!(!context.features["client_manager"] && !context.features["client_manager_gui"]);
        for path in PATHS {
            assert!(fixture.render(path, &context)?.is_none());
        }
    }
    for target in TARGETS {
        for features in [
            &[][..],
            &["client_actions"][..],
            &["label_readonly_access"][..],
            &["workspace_panels"][..],
        ] {
            let context = fixture.core.context(target, features)?;
            for path in PATHS {
                assert!(fixture.render(path, &context)?.is_none());
            }
        }
    }
    Ok(())
}

#[test]
fn client_manager_providers_exact_owner_contracts_prerequisites_and_models_are_real() -> Result<()>
{
    let fixture = Fixture::load()?;
    let mut refusals = 0;
    for (name, targets, required) in FEATURE_CONTRACTS {
        for target in TARGETS[..2]
            .iter()
            .filter(|target| targets.contains(*target))
        {
            let enabled = feature_closure(&fixture.core, &[name])?;
            fixture.core.context(
                target,
                &enabled.iter().map(String::as_str).collect::<Vec<_>>(),
            )?;
            for missing in required {
                let incomplete = enabled
                    .iter()
                    .filter(|feature| feature.as_str() != *missing)
                    .map(String::as_str)
                    .collect::<Vec<_>>();
                assert!(fixture.core.context(target, &incomplete).is_err());
                refusals += 1;
            }
        }
    }
    assert_eq!(refusals, 35);
    for target in &TARGETS[2..] {
        assert!(fixture.core.context(target, &BASE).is_err());
    }
    let gui = feature_closure(&fixture.core, &["client_manager_gui"])?;
    assert!(
        fixture
            .core
            .context(
                "1.19.4",
                &gui.iter().map(String::as_str).collect::<Vec<_>>()
            )
            .is_err()
    );
    assert!(
        fixture
            .core
            .context("1.19.2", &["unregistered_manager_owner"])
            .is_err()
    );
    let context = fixture.core.context("1.19.2", &BASE)?;
    assert!(
        !context.features["label_readonly_access"]
            && !context.features["client_manager_gui"]
            && !context.features["client_program_signing"]
            && !context.features["client_frame_language"]
    );
    let selected = select_core_inputs(&fixture.core.metadata, &context, &fixture.inventory)?;
    for pin in MODEL_PINS {
        let path = pin.path;
        let rules = fixture
            .core
            .metadata
            .source_rules
            .get(path)
            .ok_or_else(|| eyre::eyre!("missing genuine model rule"))?;
        let owners: &[&str] = if path.ends_with("/ClientManagerSigningSnapshot.java") {
            &[
                "client_manager",
                "client_program_signing",
                "multiplayer_packets",
            ]
        } else if path.ends_with("/ProgramSignatureDescriptor.java") {
            &[
                "client_manager",
                "manager_operator_queries",
                "client_program_signing",
                "multiplayer_packets",
            ]
        } else {
            &["client_manager", "client_program_signing"]
        };
        ensure!(
            rules.len() == 1
                && rules[0].input == path
                && rules[0].template
                && same_names(&rules[0].when.targets, &TARGETS[..2])
                && same_names(&rules[0].when.any_features, owners)
                && rules[0].when.all_features.is_empty()
                && rules[0].when.none_features.is_empty(),
            "pure history model lost its exact genuine existing ownership"
        );
        ensure!(
            selected.inputs.contains_key(path),
            "manager-only model omitted"
        );
        validate_source(pin, &fixture.core.read_source(path)?)?;
    }
    // Atomic parent promotion must include the reviewed exact LoadedRegistry peer.
    let pin = LOADED_REGISTRY_PIN;
    let rules = fixture
        .core
        .metadata
        .source_rules
        .get(pin.path)
        .ok_or_else(|| eyre::eyre!("reviewed LoadedRegistry peer promotion is required"))?;
    ensure!(
        rules.len() == 1
            && rules[0].input == pin.path
            && rules[0].template
            && same_names(&rules[0].when.targets, &TARGETS[..2])
            && same_names(&rules[0].when.all_features, &["client_manager"])
            && rules[0].when.any_features.is_empty()
            && rules[0].when.none_features.is_empty()
            && selected.inputs.contains_key(pin.path),
        "LoadedRegistry peer ownership changed"
    );
    validate_source(pin, &fixture.core.read_source(pin.path)?)?;
    Ok(())
}

#[test]
fn client_manager_providers_source_refusals_history_and_menu_independent_lifecycle_remain()
-> Result<()> {
    let fixture = Fixture::load()?;
    for profile in &fixture.ledger.profiles {
        let context = fixture.profile_context(profile)?;
        let be = String::from_utf8(fixture.render(PATHS[0], &context)?.expect("selected BE"))?;
        let labels = between(
            &be,
            "public LabelPositionHolder labels()",
            "public Set<String> referencedLabels()",
        )?;
        assert!(labels.contains(if profile.labels {
            READONLY_LABELS
        } else {
            OFF_LABELS
        }));
        for forbidden in [
            "getOrCreateTag",
            "LabelPositionHolder.from(disk",
            "disk.copy()",
            "CACHE",
            ".save(",
        ] {
            assert!(
                !labels.contains(forbidden),
                "label read gained mutation/copy/cache seam"
            );
        }
        let available = between(
            &be,
            "private boolean serverSigningAvailable()",
            "private static ClientManagerSigningState.Review unavailableReview()",
        )?;
        assert_eq!(available.contains("return false;"), !profile.signing);
        assert_eq!(available.contains("return level != null"), profile.signing);
        before(
            &be,
            "if (!serverSigningAvailable()) return unavailableReview();",
            "return signingState.review(",
        )?;
        let save = between(
            &be,
            "public ClientManagerSigningState.Review saveForSigning(",
            "public ClientManagerSigningState.Status submitSignature(",
        )?;
        before(
            save,
            "if (!serverSigningAvailable()) return unavailableReview();",
            "signingState.save(",
        )?;
        let submit = between(
            &be,
            "public ClientManagerSigningState.Status submitSignature(",
            "private boolean serverSigningAvailable()",
        )?;
        before(
            submit,
            "if (!serverSigningAvailable()) return ClientManagerSigningState.Status.UNAVAILABLE;",
            "signingState.submitEncoded(",
        )?;
        assert!(be.contains("new ClientManagerSigningState.Review(ClientManagerSigningState.Status.UNAVAILABLE, Optional.empty())"));
        for marker in [
            "return disk.copy();",
            "ItemStack replacement = value.copy();",
            "ClientManagerSigningBody body = signingBody(replacement);",
            "ClientManagerSigningCodec.encodeMetadata(ClientManagerSigningMetadata.from(signingState.snapshot()))",
            "ClientManagerSigningCodec.decodeMetadata(tag.getByteArray(SIGNING_TAG))",
            "ClientManagerLoadedRegistry.add(level, this);",
            "ClientManagerLoadedRegistry.remove(level, this);",
            "ClientManagerWorldIdentitySavedData.forLevel(serverLevel)",
            "ClientManagerProgramProjection.project(",
            "Preserve the full disk on the server, but publish no executable/signable projection.",
            "clientSnapshotRevision++;",
        ] {
            ensure!(
                be.contains(marker),
                "common manager/history lifecycle lost: {marker}"
            );
        }
        assert!(!be.contains("new ProgramContext("));
        let block =
            String::from_utf8(fixture.render(PATHS[1], &context)?.expect("selected block"))?;
        assert_eq!(block.contains("NetworkHooks.openScreen"), profile.gui);
        assert_eq!(block.contains("PROGRAM_TOO_LARGE"), !profile.gui);
        assert!(!block.contains("getTicker("));
    }
    // Existing decoder and bounded projection are real independent providers, not new off-branch implementations.
    let holder = String::from_utf8(
        fixture
            .core
            .read_source("src/main/java/ca/teamdman/sfm/common/label/LabelPositionHolder.java")?,
    )?;
    assert!(holder.contains("public static LabelPositionHolder deserialize(CompoundTag tag)"));
    let projection = String::from_utf8(fixture.core.read_source(
        "src/main/java/ca/teamdman/sfm/common/blockentity/ClientManagerProgramProjection.java",
    )?)?;
    for marker in [
        "MAX_LABELS = 32",
        "MAX_POSITIONS = 64",
        "MAX_COMPRESSED_BYTES = 4096",
        "input.release();",
    ] {
        ensure!(
            projection.contains(marker),
            "projection boundary changed: {marker}"
        );
    }
    Ok(())
}

#[test]
fn client_manager_providers_common_edits_propagate_and_malformed_sources_refuse() -> Result<()> {
    let fixture = Fixture::load()?;
    let anchors = [
        "return clientSnapshotRevision;",
        "return RenderShape.MODEL;",
        "return tag;",
        "return manager;",
    ];
    let edits = [
        "return (clientSnapshotRevision);",
        "return (RenderShape.MODEL);",
        "return (tag);",
        "return (manager);",
    ];
    let mut edits_seen = 0;
    for profile in &fixture.ledger.profiles {
        let context = fixture.profile_context(profile)?;
        for (index, path) in PATHS.into_iter().enumerate() {
            if fixture.render(path, &context)?.is_none() {
                continue;
            }
            let authored = String::from_utf8(fixture.core.read_source(path)?)?;
            ensure!(
                authored.matches(anchors[index]).count() == 1,
                "common edit anchor changed"
            );
            let before = render_java_source(&authored, &context)?;
            let after =
                render_java_source(&authored.replace(anchors[index], edits[index]), &context)?;
            assert_eq!(after, before.replace(anchors[index], edits[index]));
            edits_seen += 1;
        }
    }
    assert_eq!(edits_seen, 40);
    let context = fixture.core.context("1.19.2", &BASE)?;
    let source = String::from_utf8(fixture.core.read_source(PATHS[0])?)?;
    for malformed in [
        format!("{{% if features.manager_provider_unregistered %}}\n{source}{{% endif %}}\n"),
        format!("{{% if environment %}}\n{source}{{% endif %}}\n"),
    ] {
        assert!(render_java_source(&malformed, &context).is_err());
    }
    let raw_pin = raw_pin("3f9e3e020ffd8cd50d2222884bd0e759ebf35a36")?;
    let mut changed = fixture.raw[raw_pin.oid].clone();
    changed[0] = b'P';
    assert!(validate_raw(raw_pin, &changed).is_err());
    let mut extra_lf = fixture.raw[raw_pin.oid].clone();
    extra_lf.push(b'\n');
    assert!(validate_raw(raw_pin, &extra_lf).is_err());
    let mut bom = vec![0xef, 0xbb, 0xbf];
    bom.extend_from_slice(&fixture.raw[raw_pin.oid]);
    assert!(validate_raw(raw_pin, &bom).is_err());
    let mut authored = fixture.core.read_source(PATHS[0])?;
    authored[0] = b'P';
    assert!(validate_source(source_pin(PATHS[0])?, &authored).is_err());
    Ok(())
}

#[test]
fn client_manager_providers_real_collector_omits_malformed_off_and_renders_each_legal_profile()
-> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    std::fs::create_dir_all(&root)?;
    let mut metadata = fixture.core.metadata.clone();
    metadata
        .source_rules
        .retain(|path, _| PATHS.contains(&path.as_str()));
    let inventory = PATHS
        .into_iter()
        .map(str::to_owned)
        .collect::<BTreeSet<_>>();
    let off = fixture.core.context("1.19.2", &[])?;
    let selection = select_core_inputs(&metadata, &off, &inventory)?;
    for (output, input) in &selection.inputs {
        ensure!(!output.starts_with("src/"), "off case selected source");
        let bytes = read_bounded(
            &checked_file(&fixture.core.core, &input.input)?,
            16 * 1024 * 1024,
        )?;
        let path = root.join(&input.input);
        std::fs::create_dir_all(path.parent().expect("project input parent"))?;
        std::fs::write(path, bytes)?;
    }
    for path in PATHS {
        let destination = root.join(path);
        std::fs::create_dir_all(destination.parent().expect("source parent"))?;
        std::fs::write(destination, [0xff, 0xfe])?;
    }
    let artifacts = collect_core_artifacts(&root, &selection, &off)?;
    assert!(PATHS.iter().all(|path| !artifacts.contains_key(*path)));
    let on = fixture.core.context("1.19.2", &BASE)?;
    let selected = select_core_inputs(&metadata, &on, &inventory)?;
    assert!(collect_core_artifacts(&root, &selected, &on).is_err());
    for path in PATHS {
        std::fs::write(root.join(path), fixture.core.read_source(path)?)?;
    }
    let mut selected_cells = 0;
    let mut omitted_cells = 0;
    for profile in &fixture.ledger.profiles {
        let context = fixture.profile_context(profile)?;
        let selection = select_core_inputs(&metadata, &context, &inventory)?;
        // Copy each exact target's selected project-role inputs; no generated/stub project scaffold.
        for (output, input) in &selection.inputs {
            if PATHS.contains(&output.as_str()) {
                continue;
            }
            ensure!(
                !output.starts_with("src/"),
                "unrelated Java entered bounded collector"
            );
            let bytes = read_bounded(
                &checked_file(&fixture.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let path = root.join(&input.input);
            std::fs::create_dir_all(path.parent().expect("project input parent"))?;
            std::fs::write(path, bytes)?;
        }
        let artifacts = collect_core_artifacts(&root, &selection, &context)?;
        for row in &profile.files {
            if !row.present {
                assert!(!artifacts.contains_key(&row.path));
                omitted_cells += 1;
                continue;
            }
            let artifact = &artifacts[&row.path];
            let source = fixture.core.read_source(&row.path)?;
            assert!(selection.inputs[&row.path].template);
            assert_eq!(artifact.source_bytes, source);
            let body = render_java_source(std::str::from_utf8(&source)?, &context)?.into_bytes();
            assert_eq!(Some(body.len()), row.bytes);
            assert_eq!(Some(sha256(&body)), row.sha256);
            assert!(
                artifact.output_bytes.ends_with(&body),
                "collector bypassed real Java renderer"
            );
            selected_cells += 1;
        }
    }
    assert_eq!((selected_cells, omitted_cells), (40, 8));
    Ok(())
}
