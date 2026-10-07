//! Post-promotion contracts for three shared startup registration templates.
//!
//! Fixed Git objects are bounded witnesses only. No staged source fallback or
//! dependency acquisition occurs here. These are selector/rendering contracts,
//! not Java compilation, JAR parity or runtime startup acceptance.

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

const PREFIX: &str = "src/main/java/ca/teamdman/sfm/common/registry/registration/";
const LEDGER: &str = "docs/tasks/sfm-core-startup-registration-slice.json";
const ITEMS: &str = "SFMItems.java";
const BLOCKS: &str = "SFMBlocks.java";
const ENTITIES: &str = "SFMBlockEntities.java";
const NAMES: [&str; 3] = [ITEMS, BLOCKS, ENTITIES];
const CLIENT: &str = "client_manager";
const TOUCH: &str = "touch_display";
const PACKET: &str = "packet_values";
const OWNERS: [&str; 3] = [CLIENT, TOUCH, PACKET];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const TEMPLATES: [(&str, &str, usize); 3] = [
    (
        ITEMS,
        "sha256:2153daf5e75486c4aeb575eacb7b7151e849f17ea61478c52705529926422feb",
        13463,
    ),
    (
        BLOCKS,
        "sha256:c164a97e4d3117530025ad29fc06ab2e8984c60f5e3536e8b2b7d3faf8154e85",
        21054,
    ),
    (
        ENTITIES,
        "sha256:fb9ba557180627240a5ce77e2d9f35d7c2344855e33abe0abb2d688a96a3eb03",
        11749,
    ),
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
const WITNESSES: [(&str, &str, &str); 60] = [
    (
        "SFMItems.java",
        "dev/1.19.2",
        "8c7d8fafcbedeae4ea13592c55b139847813c9ec",
    ),
    (
        "SFMItems.java",
        "dev/1.19.4",
        "f6df0e88ca335f7b7083da2e9c5dddfb599955c6",
    ),
    (
        "SFMItems.java",
        "dev/1.20",
        "0d56138687c899c82f75daa52af021d8c6f8de2e",
    ),
    (
        "SFMItems.java",
        "dev/1.20.1",
        "0d56138687c899c82f75daa52af021d8c6f8de2e",
    ),
    (
        "SFMItems.java",
        "dev/1.20.2",
        "3837e75f4aae35019042d410b10e8cceaf5057ba",
    ),
    (
        "SFMItems.java",
        "dev/1.20.3",
        "3837e75f4aae35019042d410b10e8cceaf5057ba",
    ),
    (
        "SFMItems.java",
        "dev/1.20.4",
        "3837e75f4aae35019042d410b10e8cceaf5057ba",
    ),
    (
        "SFMItems.java",
        "dev/1.21.0",
        "3837e75f4aae35019042d410b10e8cceaf5057ba",
    ),
    (
        "SFMItems.java",
        "dev/1.21.1",
        "3837e75f4aae35019042d410b10e8cceaf5057ba",
    ),
    (
        "SFMItems.java",
        "dev/26.1.2",
        "b2996d35745d260c41e08876510b837d5e13aa7b",
    ),
    (
        "SFMItems.java",
        "release/1.19.2",
        "12fe00b8715634c0ed05c9abfbc4bdd7eba2b4b3",
    ),
    (
        "SFMItems.java",
        "release/1.19.4",
        "46e73b1d02f9364c03806e5a3213085e00d3d6f9",
    ),
    (
        "SFMItems.java",
        "release/1.20",
        "0d56138687c899c82f75daa52af021d8c6f8de2e",
    ),
    (
        "SFMItems.java",
        "release/1.20.1",
        "0d56138687c899c82f75daa52af021d8c6f8de2e",
    ),
    (
        "SFMItems.java",
        "release/1.20.2",
        "3837e75f4aae35019042d410b10e8cceaf5057ba",
    ),
    (
        "SFMItems.java",
        "release/1.20.3",
        "3837e75f4aae35019042d410b10e8cceaf5057ba",
    ),
    (
        "SFMItems.java",
        "release/1.20.4",
        "3837e75f4aae35019042d410b10e8cceaf5057ba",
    ),
    (
        "SFMItems.java",
        "release/1.21.0",
        "3837e75f4aae35019042d410b10e8cceaf5057ba",
    ),
    (
        "SFMItems.java",
        "release/1.21.1",
        "3837e75f4aae35019042d410b10e8cceaf5057ba",
    ),
    (
        "SFMItems.java",
        "release/26.1.2",
        "b2996d35745d260c41e08876510b837d5e13aa7b",
    ),
    (
        "SFMBlocks.java",
        "dev/1.19.2",
        "f7538595b682d3ea83118d1080222d43f007ea84",
    ),
    (
        "SFMBlocks.java",
        "dev/1.19.4",
        "f7538595b682d3ea83118d1080222d43f007ea84",
    ),
    (
        "SFMBlocks.java",
        "dev/1.20",
        "e464563211ff4900b96c86fb8e53954c0c730c6e",
    ),
    (
        "SFMBlocks.java",
        "dev/1.20.1",
        "e464563211ff4900b96c86fb8e53954c0c730c6e",
    ),
    (
        "SFMBlocks.java",
        "dev/1.20.2",
        "a9457794c1b9d2ae1fa30e8463f44dd5fd465765",
    ),
    (
        "SFMBlocks.java",
        "dev/1.20.3",
        "a9457794c1b9d2ae1fa30e8463f44dd5fd465765",
    ),
    (
        "SFMBlocks.java",
        "dev/1.20.4",
        "a9457794c1b9d2ae1fa30e8463f44dd5fd465765",
    ),
    (
        "SFMBlocks.java",
        "dev/1.21.0",
        "a9457794c1b9d2ae1fa30e8463f44dd5fd465765",
    ),
    (
        "SFMBlocks.java",
        "dev/1.21.1",
        "a9457794c1b9d2ae1fa30e8463f44dd5fd465765",
    ),
    (
        "SFMBlocks.java",
        "dev/26.1.2",
        "430fe4d1b3558fdd8d225f63357a2e74bd559723",
    ),
    (
        "SFMBlocks.java",
        "release/1.19.2",
        "ffae7e91e295a09952ad797bdf97a6e670f91f30",
    ),
    (
        "SFMBlocks.java",
        "release/1.19.4",
        "ffae7e91e295a09952ad797bdf97a6e670f91f30",
    ),
    (
        "SFMBlocks.java",
        "release/1.20",
        "e464563211ff4900b96c86fb8e53954c0c730c6e",
    ),
    (
        "SFMBlocks.java",
        "release/1.20.1",
        "e464563211ff4900b96c86fb8e53954c0c730c6e",
    ),
    (
        "SFMBlocks.java",
        "release/1.20.2",
        "a9457794c1b9d2ae1fa30e8463f44dd5fd465765",
    ),
    (
        "SFMBlocks.java",
        "release/1.20.3",
        "a9457794c1b9d2ae1fa30e8463f44dd5fd465765",
    ),
    (
        "SFMBlocks.java",
        "release/1.20.4",
        "a9457794c1b9d2ae1fa30e8463f44dd5fd465765",
    ),
    (
        "SFMBlocks.java",
        "release/1.21.0",
        "a9457794c1b9d2ae1fa30e8463f44dd5fd465765",
    ),
    (
        "SFMBlocks.java",
        "release/1.21.1",
        "a9457794c1b9d2ae1fa30e8463f44dd5fd465765",
    ),
    (
        "SFMBlocks.java",
        "release/26.1.2",
        "430fe4d1b3558fdd8d225f63357a2e74bd559723",
    ),
    (
        "SFMBlockEntities.java",
        "dev/1.19.2",
        "54435d1e7187213db7b13067b97b63d2654a35aa",
    ),
    (
        "SFMBlockEntities.java",
        "dev/1.19.4",
        "54435d1e7187213db7b13067b97b63d2654a35aa",
    ),
    (
        "SFMBlockEntities.java",
        "dev/1.20",
        "8f934f6abb6d7e70cdbac890dcbe6a650bd4f585",
    ),
    (
        "SFMBlockEntities.java",
        "dev/1.20.1",
        "8f934f6abb6d7e70cdbac890dcbe6a650bd4f585",
    ),
    (
        "SFMBlockEntities.java",
        "dev/1.20.2",
        "c4869875e4afd3993c54920c88a1539165044e12",
    ),
    (
        "SFMBlockEntities.java",
        "dev/1.20.3",
        "c4869875e4afd3993c54920c88a1539165044e12",
    ),
    (
        "SFMBlockEntities.java",
        "dev/1.20.4",
        "c4869875e4afd3993c54920c88a1539165044e12",
    ),
    (
        "SFMBlockEntities.java",
        "dev/1.21.0",
        "c4869875e4afd3993c54920c88a1539165044e12",
    ),
    (
        "SFMBlockEntities.java",
        "dev/1.21.1",
        "c4869875e4afd3993c54920c88a1539165044e12",
    ),
    (
        "SFMBlockEntities.java",
        "dev/26.1.2",
        "9b4c70a1813855565f4f3e8044d88e315243a865",
    ),
    (
        "SFMBlockEntities.java",
        "release/1.19.2",
        "8f934f6abb6d7e70cdbac890dcbe6a650bd4f585",
    ),
    (
        "SFMBlockEntities.java",
        "release/1.19.4",
        "8f934f6abb6d7e70cdbac890dcbe6a650bd4f585",
    ),
    (
        "SFMBlockEntities.java",
        "release/1.20",
        "8f934f6abb6d7e70cdbac890dcbe6a650bd4f585",
    ),
    (
        "SFMBlockEntities.java",
        "release/1.20.1",
        "8f934f6abb6d7e70cdbac890dcbe6a650bd4f585",
    ),
    (
        "SFMBlockEntities.java",
        "release/1.20.2",
        "c4869875e4afd3993c54920c88a1539165044e12",
    ),
    (
        "SFMBlockEntities.java",
        "release/1.20.3",
        "c4869875e4afd3993c54920c88a1539165044e12",
    ),
    (
        "SFMBlockEntities.java",
        "release/1.20.4",
        "c4869875e4afd3993c54920c88a1539165044e12",
    ),
    (
        "SFMBlockEntities.java",
        "release/1.21.0",
        "c4869875e4afd3993c54920c88a1539165044e12",
    ),
    (
        "SFMBlockEntities.java",
        "release/1.21.1",
        "c4869875e4afd3993c54920c88a1539165044e12",
    ),
    (
        "SFMBlockEntities.java",
        "release/26.1.2",
        "9b4c70a1813855565f4f3e8044d88e315243a865",
    ),
];
struct Golden {
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}
static GOLDENS: [Golden; 16] = [
    Golden {
        oid: "0d56138687c899c82f75daa52af021d8c6f8de2e",
        digest: "sha256:17e9f48e7c026d899df155568c02dc6d20921c863919b25e44ed288fac8dc32e",
        bytes: 4191,
    },
    Golden {
        oid: "12fe00b8715634c0ed05c9abfbc4bdd7eba2b4b3",
        digest: "sha256:cb505b177d9c9e55ab7a1b0611d26c0d9ca282185cee893d09b92a5704016de5",
        bytes: 4210,
    },
    Golden {
        oid: "3837e75f4aae35019042d410b10e8cceaf5057ba",
        digest: "sha256:7aa47e66af9b0bc8ba567110912df2cf20c05ce6ba46bfa0656b6b666c4bcd91",
        bytes: 4139,
    },
    Golden {
        oid: "46e73b1d02f9364c03806e5a3213085e00d3d6f9",
        digest: "sha256:08900de26a0a3ae072e4f175a7f36e8ca20ff96e78ef3235fdd0276a14fcb014",
        bytes: 4149,
    },
    Golden {
        oid: "8c7d8fafcbedeae4ea13592c55b139847813c9ec",
        digest: "sha256:103100ad59a84e169844ffd7140124374a8194dbbeb98beaf0580aada4305451",
        bytes: 4621,
    },
    Golden {
        oid: "b2996d35745d260c41e08876510b837d5e13aa7b",
        digest: "sha256:4b0456eea66a0935b1a41f892f806b87c086c06735a177602c7d9e083ba429bf",
        bytes: 6676,
    },
    Golden {
        oid: "f6df0e88ca335f7b7083da2e9c5dddfb599955c6",
        digest: "sha256:331bfadf5e8418be4a88ee6581b0a4d0f49dc297cf4a1ce57e83bf6b61550f89",
        bytes: 4562,
    },
    Golden {
        oid: "430fe4d1b3558fdd8d225f63357a2e74bd559723",
        digest: "sha256:5b0384efcdb92ada0e6ccf6d684f0ca248c22f9788daed283d90610cc3d7b0d9",
        bytes: 11383,
    },
    Golden {
        oid: "a9457794c1b9d2ae1fa30e8463f44dd5fd465765",
        digest: "sha256:3a8ec013a4f0afb8069c429a0872b8467a37280079877812354e243b1a21990a",
        bytes: 8997,
    },
    Golden {
        oid: "e464563211ff4900b96c86fb8e53954c0c730c6e",
        digest: "sha256:bf2d077914850910dfa592e30e387d693546d164865550d6e5b1e9fbb5f7a164",
        bytes: 9007,
    },
    Golden {
        oid: "f7538595b682d3ea83118d1080222d43f007ea84",
        digest: "sha256:2fe149c8b67e7cf4195689d0c932ca4ad89a4d891c6de46fb847516c57f6dbaa",
        bytes: 9466,
    },
    Golden {
        oid: "ffae7e91e295a09952ad797bdf97a6e670f91f30",
        digest: "sha256:df8d7e3972212659e7ac3d2f352f1c615479a4d55670c60858a6fd69900ba273",
        bytes: 9132,
    },
    Golden {
        oid: "54435d1e7187213db7b13067b97b63d2654a35aa",
        digest: "sha256:e90c21bf02fa0656ad040c35e23910cb4d98542dd66196c6aa06f78ad79f8980",
        bytes: 7123,
    },
    Golden {
        oid: "8f934f6abb6d7e70cdbac890dcbe6a650bd4f585",
        digest: "sha256:36a0fbe678a1b9a96c3413da7eeb6a3b46126daa12edb04d6a170ad0ed008a36",
        bytes: 6418,
    },
    Golden {
        oid: "9b4c70a1813855565f4f3e8044d88e315243a865",
        digest: "sha256:e53d09da4d66675cfe00d62873b50be8c0b8effe39dd56e20bd122e213d7b384",
        bytes: 5573,
    },
    Golden {
        oid: "c4869875e4afd3993c54920c88a1539165044e12",
        digest: "sha256:889169035418924635b1a9978643e8b7dce0520b867a53f4d3f0621e9186ef0e",
        bytes: 6407,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    scope: String,
    context_commits: BTreeMap<String, String>,
    owners: Vec<Owner>,
    inputs: Vec<Input>,
    raw_variants: Vec<Raw>,
}
#[derive(Facet)]
struct Owner {
    id: String,
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct Input {
    path: String,
    stage_path: String,
    core_path: String,
    source_sha256: String,
    source_bytes: usize,
    membership: String,
    member_features: Vec<String>,
    witnesses: BTreeMap<String, String>,
}
#[derive(Facet)]
struct Raw {
    blob: String,
    sha256: String,
    bytes: usize,
    cr_count: usize,
    final_lf: bool,
    contexts: Vec<String>,
}
struct Fixture {
    core: CoreTestFixture,
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
            ledger.schema == "sfm:core-startup-registration-slice@1"
                && ledger.scope == "SFMItems_SFMBlocks_SFMBlockEntities_only",
            "wrong startup registration scope"
        );
        let expected_commits = COMMITS
            .into_iter()
            .map(|(name, oid)| (name.to_owned(), oid.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            ledger.context_commits == expected_commits,
            "startup witness commits changed"
        );
        ensure!(
            ledger.inputs.len() == 3 && ledger.raw_variants.len() == 16 && ledger.owners.len() == 3,
            "startup source scope changed"
        );
        let mut seen_names = BTreeSet::new();
        for input in ledger.inputs {
            let name = input
                .path
                .strip_prefix(PREFIX)
                .ok_or_else(|| eyre::eyre!("startup input outside fixed scope"))?;
            let (_, digest, count) = TEMPLATES
                .iter()
                .find(|(candidate, _, _)| *candidate == name)
                .ok_or_else(|| eyre::eyre!("unreviewed startup source"))?;
            let expected_members: &[&str] = if name == ITEMS {
                &OWNERS
            } else {
                &[CLIENT, TOUCH]
            };
            ensure!(
                seen_names.insert(name.to_owned())
                    && input.source_sha256 == *digest
                    && input.source_bytes == *count
                    && input.core_path
                        == format!("platform/minecraft/core-liquid-template/{}{name}", PREFIX)
                    && input.stage_path
                        == format!(
                            "platform/cli/sfm-propagate-changes/target/core-startup-registration-stage-v1/{name}"
                        )
                    && input.membership == "shared_all_ten_targets_all_feature_masks"
                    && input
                        .member_features
                        .iter()
                        .map(String::as_str)
                        .eq(expected_members.iter().copied()),
                "startup authored witness or member owners changed"
            );
            let expected_map = WITNESSES
                .iter()
                .filter(|(file, _, _)| *file == name)
                .map(|(_, context, oid)| ((*context).to_owned(), (*oid).to_owned()))
                .collect::<BTreeMap<_, _>>();
            ensure!(
                input.witnesses == expected_map && expected_map.len() == 20,
                "startup all-context map changed"
            );
            validate_shared_rule(&core.metadata, &input.path)?;
        }
        let mut seen_owners = BTreeSet::new();
        for owner in ledger.owners {
            let prerequisites: &[&str] = match owner.id.as_str() {
                CLIENT => &[
                    "sfml_execution_side",
                    "client_program_consent",
                    "disk_readonly_access",
                ],
                TOUCH => &["packet_values", "image_resources"],
                PACKET => &[],
                _ => eyre::bail!("unreviewed startup member owner"),
            };
            ensure!(
                seen_owners.insert(owner.id.clone())
                    && owner.supported_targets == ["1.19.2", "1.19.4"]
                    && owner
                        .requires
                        .iter()
                        .map(String::as_str)
                        .eq(prerequisites.iter().copied()),
                "startup owner support/prerequisites changed"
            );
            let actual = core
                .features
                .0
                .get(&owner.id)
                .ok_or_else(|| eyre::eyre!("owner is not registered"))?;
            ensure!(
                actual.supported_targets == owner.supported_targets
                    && actual.requires == owner.requires,
                "registry disagrees with startup owner evidence"
            );
        }
        let mut seen_oids = BTreeSet::new();
        for row in ledger.raw_variants {
            let pin = GOLDENS
                .iter()
                .find(|pin| pin.oid == row.blob)
                .ok_or_else(|| eyre::eyre!("unreviewed raw startup blob"))?;
            let contexts = WITNESSES
                .iter()
                .filter(|(_, _, oid)| *oid == row.blob)
                .map(|(_, context, _)| (*context).to_owned())
                .collect::<BTreeSet<_>>();
            ensure!(
                seen_oids.insert(row.blob.clone())
                    && row.sha256 == pin.digest
                    && row.bytes == pin.bytes
                    && row.cr_count == 0
                    && row.final_lf
                    && row.contexts.len() == contexts.len()
                    && row.contexts.into_iter().collect::<BTreeSet<_>>() == contexts,
                "startup raw byte or occurrence evidence changed"
            );
        }
        let oids = GOLDENS.iter().map(|pin| pin.oid.to_owned()).collect();
        let raw = read_git_blobs(&core.repository, &oids)?;
        for pin in &GOLDENS {
            verify_raw(&raw[pin.oid], pin)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            NAMES
                .iter()
                .all(|name| inventory.contains(&format!("{PREFIX}{name}"))),
            "startup templates must be promoted before registration; no staged fallback"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }
    fn context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        // Only the real registered prerequisite graph may expand a request.
        // This is not a claim that the three owner bits are independently legal.
        let mut enabled = BTreeSet::new();
        let mut pending = requested
            .iter()
            .map(|flag| (*flag).to_owned())
            .collect::<Vec<_>>();
        while let Some(flag) = pending.pop() {
            if enabled.insert(flag.clone()) {
                let definition = self
                    .core
                    .features
                    .0
                    .get(&flag)
                    .ok_or_else(|| eyre::eyre!("unknown requested startup owner"))?;
                pending.extend(definition.requires.iter().cloned());
            }
        }
        let flags = enabled.iter().map(String::as_str).collect::<Vec<_>>();
        self.core.context(target, &flags)
    }
    fn render(&self, name: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let path = format!("{PREFIX}{name}");
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let input = selected
            .inputs
            .get(&path)
            .ok_or_else(|| eyre::eyre!("released startup source was omitted"))?;
        ensure!(
            input.input == path && !selected.omitted_paths.contains(&path),
            "startup source gained an alternate or optional input"
        );
        let bytes = self.core.read_source(&path)?;
        verify_template(name, &bytes)?;
        Ok(render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes())
    }
    fn partial_golden(
        &self,
        name: &str,
        target: &str,
        context: &ProjectionContext,
    ) -> Result<Vec<u8>> {
        ensure!(
            matches!(target, "1.19.2" | "1.19.4"),
            "partial member fixture is D2 only"
        );
        let full_oid = witness(name, &format!("dev/{target}"))?;
        let mut expected = String::from_utf8(self.raw[full_oid].clone())?;
        for owner in OWNERS {
            let block = member_block(name, owner, target)?;
            if block.is_empty() {
                continue;
            }
            ensure!(
                expected.matches(&block).count() == 1,
                "owned startup member has no unique full witness"
            );
            if !context.features[owner] {
                expected = expected.replacen(&block, "", 1);
            }
        }
        Ok(expected.into_bytes())
    }
}
fn witness(name: &str, context: &str) -> Result<&'static str> {
    WITNESSES
        .iter()
        .find(|(file, cell, _)| *file == name && *cell == context)
        .map(|(_, _, oid)| *oid)
        .ok_or_else(|| eyre::eyre!("unsupported startup witness"))
}
fn verify_raw(bytes: &[u8], pin: &Golden) -> Result<()> {
    ensure!(
        bytes.len() == pin.bytes
            && sha256(bytes) == pin.digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "raw startup byte mutation; normalization is not approved"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn verify_template(name: &str, bytes: &[u8]) -> Result<()> {
    let (_, digest, count) = TEMPLATES
        .iter()
        .find(|(file, _, _)| *file == name)
        .ok_or_else(|| eyre::eyre!("unreviewed startup template"))?;
    ensure!(
        bytes.len() == *count
            && sha256(bytes) == *digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "startup authored bytes changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn validate_shared_rule(metadata: &CoreProjectInputs, path: &str) -> Result<()> {
    if let Some(rules) = metadata.source_rules.get(path) {
        ensure!(
            rules.len() == 1
                && rules[0].input == path
                && rules[0].when.targets.is_empty()
                && rules[0].when.all_features.is_empty()
                && rules[0].when.any_features.is_empty()
                && rules[0].when.none_features.is_empty(),
            "startup source must remain shared and unconditional"
        );
    }
    Ok(())
}
fn member_block(name: &str, owner: &str, target: &str) -> Result<String> {
    let (field, class, id) = match owner {
        CLIENT => ("CLIENT_MANAGER", "ClientManager", "client_manager"),
        TOUCH => ("TOUCH_DISPLAY", "TouchDisplay", "touch_display"),
        PACKET if name == ITEMS => {
            let registerer = if target == "1.19.2" {
                "REGISTRY"
            } else {
                "REGISTERER"
            };
            return Ok(format!(
                "    public static final SFMRegistryObject<Item, PacketItem> PACKET\n            = {registerer}.register(\"packet\", PacketItem::new);\n\n"
            ));
        }
        PACKET => return Ok(String::new()),
        _ => eyre::bail!("unknown startup member owner"),
    };
    Ok(match name {
        ITEMS => format!(
            "    public static final SFMRegistryObject<Item, BlockItem> {field}\n            = register(\"{id}\", SFMBlocks.{field});\n\n"
        ),
        BLOCKS if owner == CLIENT => format!(
            "    public static final SFMRegistryObject<Block, {class}Block> {field}\n            = REGISTERER.register(\"{id}\", {class}Block::new);\n\n"
        ),
        BLOCKS => format!(
            "    public static final SFMRegistryObject<Block, {class}Block> {field}\n            =\n            REGISTERER.register(\"{id}\", {class}Block::new);\n\n"
        ),
        ENTITIES => format!(
            "    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<{class}BlockEntity>>\n            {field} = REGISTERER.register(\n            \"{id}\",\n            () -> BlockEntityType.Builder\n                    .of({class}BlockEntity::new, SFMBlocks.{field}.get())\n                    .build(null)\n    );\n\n"
        ),
        _ => eyre::bail!("unknown startup class"),
    })
}

#[test]
fn startup_registration_templates_match_sixty_exact_witnessed_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for (cell, _) in COMMITS {
        let (environment, target) = cell.split_once('/').expect("fixed context");
        let full = environment == "dev" && matches!(target, "1.19.2" | "1.19.4");
        let mut context = fixture.context(target, if full { &OWNERS } else { &[] })?;
        context.environment = environment.to_owned();
        context.projection_key = format!(
            "{}/mc-{target}",
            if environment == "dev" {
                "sfm-dev"
            } else {
                "sfm-4.34.0"
            }
        );
        context.preset.clone_from(&context.projection_key);
        for name in NAMES {
            let actual = fixture.render(name, &context)?;
            assert_eq!(
                actual,
                fixture.raw[witness(name, cell)?],
                "wrong startup raw output for {cell} {name}"
            );
            assert!(!std::str::from_utf8(&actual)?.contains("{%"));
            cells += 1;
        }
    }
    assert_eq!(cells, 60);
    Ok(())
}

#[test]
fn startup_registration_requested_masks_follow_effective_prerequisites() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for target in ["1.19.2", "1.19.4"] {
        for mask in 0_u8..8 {
            let requested = OWNERS
                .iter()
                .enumerate()
                .filter(|(index, _)| mask & (1_u8 << *index) != 0)
                .map(|(_, owner)| *owner)
                .collect::<Vec<_>>();
            let context = fixture.context(target, &requested)?;
            for owner in &requested {
                assert!(context.features[*owner]);
            }
            for name in NAMES {
                assert_eq!(
                    fixture.render(name, &context)?,
                    fixture.partial_golden(name, target, &context)?,
                    "wrong effective startup mask {target}/{mask}/{name}"
                );
                cells += 1;
            }
        }
    }
    assert_eq!(cells, 48);
    Ok(())
}

#[test]
fn startup_registration_aliases_and_baseline_fields_preserve_released_behavior() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let context = fixture.context(target, &[])?;
        let items = String::from_utf8(fixture.render(ITEMS, &context)?)?;
        let blocks = String::from_utf8(fixture.render(BLOCKS, &context)?)?;
        let entities = String::from_utf8(fixture.render(ENTITIES, &context)?)?;
        for text in [&items, &blocks, &entities] {
            assert!(!text.contains("CLIENT_MANAGER"));
            assert!(!text.contains("TOUCH_DISPLAY"));
            assert!(!text.contains("PacketItem"));
        }
        if target == "1.19.2" {
            assert!(items.contains("private static final SFMDeferredRegister<Item> REGISTRY"));
            assert!(items.contains(".tab(SFMCreativeTabs.MAIN)"));
        } else {
            assert!(items.contains("public static final SFMDeferredRegister<Item> REGISTERER"));
        }
        if matches!(target, "1.19.2" | "1.19.4") {
            assert!(blocks.contains(".of(Material.STONE, MaterialColor.COLOR_BLACK)"));
        } else {
            assert!(blocks.contains("NoteBlockInstrument.BASS"));
        }
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
        if target == "26.1.2" {
            assert!(items.contains(".useBlockDescriptionPrefix()"));
            assert!(blocks.contains(
                ".setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))"
            ));
            assert!(!entities.contains("BlockEntityType.Builder"));
            assert!(entities.contains("() -> new BlockEntityType<>(TestBarrelTankBlockEntity::new, SFMBlocks.TEST_BARREL.get())"));
        } else {
            assert!(
                entities
                    .contains(".of(TestBarrelTankBlockEntity::new, SFMBlocks.TEST_BARREL.get())")
            );
        }
        if !matches!(target, "1.19.2" | "1.19.4") {
            for owner in OWNERS {
                assert!(fixture.context(target, &[owner]).is_err());
            }
        }
    }
    Ok(())
}

#[test]
fn startup_registration_common_edit_reaches_ten_targets_in_isolated_tree() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let core = temp.path().join(super::core_inputs::CORE_ROOT);
    for name in NAMES {
        let path = format!("{PREFIX}{name}");
        let destination = core.join(&path);
        fs::create_dir_all(destination.parent().expect("fixed input parent"))?;
        let original = fixture.core.read_source(&path)?;
        let mut edited = original.clone();
        edited.extend_from_slice(b"// isolated shared startup registration edit\n");
        fs::write(&destination, &edited)?;
        let inventory = discover_core_source_files(&core)?;
        let mut metadata = fixture.core.metadata.clone();
        metadata
            .source_rules
            .retain(|output, _| inventory.contains(output));
        metadata.project_files.clear();
        for target in TARGETS {
            let context = fixture.context(target, &[])?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            assert_eq!(selected.inputs[&path].input, path);
            let bytes = read_bounded(
                &checked_file(&core, &selected.inputs[&path].input)?,
                1024 * 1024,
            )?;
            let actual = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            let mut expected = fixture.render(name, &context)?;
            expected.extend_from_slice(b"// isolated shared startup registration edit\n");
            assert_eq!(actual.as_bytes(), expected);
        }
        assert_eq!(fixture.core.read_source(&path)?, original);
    }
    Ok(())
}

#[test]
fn startup_registration_unreviewed_sources_and_whole_class_gates_refuse() -> Result<()> {
    let fixture = Fixture::load()?;
    for name in NAMES {
        let path = format!("{PREFIX}{name}");
        let mut bytes = fixture.core.read_source(&path)?;
        bytes.push(b' ');
        assert!(verify_template(name, &bytes).is_err());
        let mut metadata = fixture.core.metadata.clone();
        metadata.source_rules.insert(
            path.clone(),
            vec![super::core_inputs::InputVariant {
                input: path.clone(),
                when: super::core_inputs::InputPredicate {
                    targets: vec![],
                    all_features: vec![PACKET.to_owned()],
                    any_features: vec![],
                    none_features: vec![],
                },
                template: false,
            }],
        );
        assert!(validate_shared_rule(&metadata, &path).is_err());
    }
    let pin = &GOLDENS[0];
    let mut raw = fixture.raw[pin.oid].clone();
    raw[0] = b'X';
    assert!(verify_raw(&raw, pin).is_err());
    let mut context = fixture.context("1.19.2", &[])?;
    context.features.remove(CLIENT);
    let bytes = fixture.core.read_source(&format!("{PREFIX}{BLOCKS}"))?;
    assert!(render_java_source(std::str::from_utf8(&bytes)?, &context).is_err());
    Ok(())
}
