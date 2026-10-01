//! Frozen post-promotion evidence for three shared network infrastructure inputs.
//!
//! Real core selection and Liquid rendering are used; historical Git blobs are
//! bounded test-only witnesses, never production rendering inputs. Common-core
//! edits require a deliberate review of these migration goldens.
//!
//! Accepted network layout identity is not a complete project build or codec
//! compatibility proof. Source-only grammar/helper contexts deliberately remain
//! distinct: no signing/multiplayer prerequisite is invented to render a parser
//! fragment. Complete project generation must apply its own layout refusal gate.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::select_core_inputs;
use super::core_network_layout::NetworkMessageFamilies;
use super::core_network_layout::ReviewedNetworkLayout;
use super::core_network_layout::validate_network_layout;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;

const LEDGER: &str = "docs/tasks/sfm-core-network-first-slice.json";
const PACKETS: &str = "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMPackets.java";
const DADDY: &str = "src/main/java/ca/teamdman/sfm/common/net/SFMPacketDaddy.java";
const CONTEXT: &str = "src/main/java/ca/teamdman/sfm/common/net/SFMPacketHandlingContext.java";
const GRAMMAR: &str = "src/main/antlr/sfml/SFML.g4";
const PATHS: [&str; 3] = [PACKETS, DADDY, CONTEXT];
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;
const MAX_SOURCE_BYTES: u64 = 128 * 1024;
const FAMILIES: [&str; 5] = [
    "packet_transport_private",
    "client_inbox",
    "client_program_signing",
    "multiplayer_packets",
    "manager_operator_queries",
];
const OPTIONAL_MEMBERS: [&str; 3] = [
    "packet_direction_validation",
    "manager_menu_request_validation",
    "sfml_execution_side",
];
const NEW_OWNERS: [&str; 5] = [
    "client_program_signing",
    "multiplayer_packets",
    "manager_operator_queries",
    "packet_direction_validation",
    "manager_menu_request_validation",
];

const PINNED_CORE: [(&str, &str, u64); 3] = [
    (
        PACKETS,
        "sha256:00e65686080d1644e3bbf30c71d58f96a8bfc42c7f97dfa4090cacafa1066791",
        23921,
    ),
    (
        DADDY,
        "sha256:d6e14f758a30638d4dc152ee442998773610414523341f41524d527b451662be",
        3787,
    ),
    (
        CONTEXT,
        "sha256:090379da05475414ddc93e848a795e4f3852d142fe51774e7c44d4a2257d3c11",
        9531,
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
    oid: &'static str,
    digest: &'static str,
    bytes: u64,
}

const RAW_SPECS: [RawSpec; 18] = [
    RawSpec {
        path: PACKETS,
        oid: "6e7671df11884ced9535e04e3d3780b168fd45df",
        digest: "sha256:c61e8e0ad8196bdd55990145b193aea1cbe37ba6f48a3a67b1404445e0eb289b",
        bytes: 6343,
    },
    RawSpec {
        path: PACKETS,
        oid: "b47e9fd0c47ac698f12048335e8c13cd4e7f9265",
        digest: "sha256:54db1c399ee2a0b2d11968141a544a7e08e97454f251f30b4e74637fe3956194",
        bytes: 6215,
    },
    RawSpec {
        path: PACKETS,
        oid: "95fff5038be915e77fd357350c21947e3828262c",
        digest: "sha256:f43b20e7946237813722dc8b54bf31619d116b04b369c19e019770e341d7988c",
        bytes: 4599,
    },
    RawSpec {
        path: PACKETS,
        oid: "d929cbace9495b1f9c53711a96040e2bb0c44692",
        digest: "sha256:5bbe9c05c6109a56b32ab8ce7edfd86b0a47a9c950e01b93fb1afe623c0ab158",
        bytes: 4611,
    },
    RawSpec {
        path: PACKETS,
        oid: "6124cd8c085474bfbfba136742ca7bb4cbee6a0a",
        digest: "sha256:8cb42db7116e9247676feef8d6d6dbcfd1a9760c773f16eb28b05309873a31f2",
        bytes: 6789,
    },
    RawSpec {
        path: PACKETS,
        oid: "be6efbd87f166378e8a473625eda22ea5fbc4de7",
        digest: "sha256:a53898b20929e7bc08265be86f1ffb19bcf48381f18e010c8bbf220ab396ce4b",
        bytes: 7379,
    },
    RawSpec {
        path: PACKETS,
        oid: "f7b762cc080e7ae726fa13bb8db006b3904059f4",
        digest: "sha256:644ef1278fa97baa8776b83d5a907b078de04b8626c80b48d229f8622eb5aba0",
        bytes: 7431,
    },
    RawSpec {
        path: DADDY,
        oid: "48ddb0dce77d60e3033214553d4836e2fb5ff895",
        digest: "sha256:240c771ac14a2f9edb1fd996ee75df351f14be9890d5153edec0a8f7b6a0aaf0",
        bytes: 1980,
    },
    RawSpec {
        path: DADDY,
        oid: "22685247ec1d494bd11a29506f738a354c16e8ec",
        digest: "sha256:0e34a6a0187d5464706b95a74f3877aab054c1efe2eb3574e7c8ed68fec634b3",
        bytes: 1681,
    },
    RawSpec {
        path: DADDY,
        oid: "fd87ba46e48cc6b275664c04eb0afae01a523b78",
        digest: "sha256:7338bb0b388533ef7886c35839766156b208f8c2ac873eacf2da41dfa0ebc045",
        bytes: 1635,
    },
    RawSpec {
        path: DADDY,
        oid: "d61a169db857cc83371adc5748e15423d1ac36a9",
        digest: "sha256:5b4cd932fea80dde10cf90d24a281ea94cfac84d4ec58e403dfd74caa4e4f606",
        bytes: 1644,
    },
    RawSpec {
        path: DADDY,
        oid: "dfffea5463ab7191c99d1af3e09a3342bd24fb12",
        digest: "sha256:c9ff10e1ed9ca7d348a61c7d2c5546a8d1062cd4c06eff6b447f998dc5b1b90e",
        bytes: 1662,
    },
    RawSpec {
        path: CONTEXT,
        oid: "ad9dd331a0fd89372f2c889581de0f2a6874279b",
        digest: "sha256:df65e9d213e2f1f41cb2af1415c5d73b3bde64c9abd56fc623be91143e0fb9f2",
        bytes: 7207,
    },
    RawSpec {
        path: CONTEXT,
        oid: "5694b8d2d5724314c9bb3a1b1928eafd79b2280d",
        digest: "sha256:8444c4fbe4418270aad9da432f657ade00048e7764b31511b9ffcdbd0885c97b",
        bytes: 6640,
    },
    RawSpec {
        path: CONTEXT,
        oid: "85e15991d61bfa83157b72ba60a994078a5f456a",
        digest: "sha256:5d80390eadbccade57c09e0e7129a133aa705a52573bf79fe180ffa3f2ae8854",
        bytes: 5838,
    },
    RawSpec {
        path: CONTEXT,
        oid: "1c53eee93a86d5b47488fd957793c268b9fdf931",
        digest: "sha256:f5c013dfe7539ca6d5dccd41c58912139122048155f3dc561d0b4f478025951c",
        bytes: 5842,
    },
    RawSpec {
        path: CONTEXT,
        oid: "ffafc6ae032b2aed5cd4ce2d9925e305a520a1d5",
        digest: "sha256:faa82a0ea66799c53a7aa6d49b71f574ccaa5058fbfc2088f989cb2055e94a50",
        bytes: 5937,
    },
    RawSpec {
        path: CONTEXT,
        oid: "d3b88a9b72253ca06a44409a9098b18d49c9076e",
        digest: "sha256:9d0c52fd3a78569dadea954338832f9a9500c8680420218271d5d11b000946be",
        bytes: 5899,
    },
];

#[derive(Clone, Copy)]
struct NormalizedSpec {
    oid: &'static str,
    digest: &'static str,
    bytes: u64,
    crlf_count: u64,
}

const NORMALIZED_SPECS: [NormalizedSpec; 6] = [
    NormalizedSpec {
        oid: "1c53eee93a86d5b47488fd957793c268b9fdf931",
        digest: "sha256:b7c5bcb00a22a6459f3cf0bb617d23e61b2f27100fa01d842d2e9116d5ecb19b",
        bytes: 5667,
        crlf_count: 175,
    },
    NormalizedSpec {
        oid: "5694b8d2d5724314c9bb3a1b1928eafd79b2280d",
        digest: "sha256:9fa673429544a40a5641c8a32983888a2bf0670910ec21af2bf21b718c691357",
        bytes: 6476,
        crlf_count: 164,
    },
    NormalizedSpec {
        oid: "85e15991d61bfa83157b72ba60a994078a5f456a",
        digest: "sha256:99a478b69c57f3faa41ff919a04d680b6a67f5d2fcc8430d68a96419397de8ab",
        bytes: 5663,
        crlf_count: 175,
    },
    NormalizedSpec {
        oid: "ad9dd331a0fd89372f2c889581de0f2a6874279b",
        digest: "sha256:6ca03fb91a37456023ec265e4d0d4187b71a8b975c4599031f69807ac909e943",
        bytes: 7043,
        crlf_count: 164,
    },
    NormalizedSpec {
        oid: "d3b88a9b72253ca06a44409a9098b18d49c9076e",
        digest: "sha256:04ea298e819770687da60d03276b19fc38623e29498bccda6b40e71273fbcab9",
        bytes: 5723,
        crlf_count: 176,
    },
    NormalizedSpec {
        oid: "ffafc6ae032b2aed5cd4ce2d9925e305a520a1d5",
        digest: "sha256:355b30e60617bc8f264d2cdc91cc349a9a17b2d888b39e1453393d7914a115e2",
        bytes: 5760,
        crlf_count: 177,
    },
];

const BASELINE: [&str; 37] = [
    "ClientboundBoolExprStatementInspectionResultsPacket",
    "ClientboundClientConfigCommandPacket",
    "ClientboundContainerExportsInspectionResultsPacket",
    "ClientboundIfStatementInspectionResultsPacket",
    "ClientboundInputInspectionResultsPacket",
    "ClientboundLabelGunUseResponsePacket",
    "ClientboundLabelInspectionResultsPacket",
    "ClientboundManagerGuiUpdatePacket",
    "ClientboundManagerLogLevelUpdatedPacket",
    "ClientboundManagerLogsPacket",
    "ClientboundOutputInspectionResultsPacket",
    "ClientboundServerConfigCommandPacket",
    "ClientboundShowChangelogPacket",
    "ServerboundBoolExprStatementInspectionRequestPacket",
    "ServerboundContainerExportsInspectionRequestPacket",
    "ServerboundDiskItemSetProgramPacket",
    "ServerboundFacadePacket",
    "ServerboundIfStatementInspectionRequestPacket",
    "ServerboundInputInspectionRequestPacket",
    "ServerboundLabelGunClearPacket",
    "ServerboundLabelGunCycleViewModePacket",
    "ServerboundLabelGunPrunePacket",
    "ServerboundLabelGunSetActiveLabelPacket",
    "ServerboundLabelGunUsePacket",
    "ServerboundLabelInspectionRequestPacket",
    "ServerboundManagerClearLogsPacket",
    "ServerboundManagerFixPacket",
    "ServerboundManagerLogDesireUpdatePacket",
    "ServerboundManagerProgramPacket",
    "ServerboundManagerRebuildPacket",
    "ServerboundManagerResetPacket",
    "ServerboundManagerSetLogLevelPacket",
    "ServerboundNetworkToolToggleOverlayPacket",
    "ServerboundNetworkToolUsePacket",
    "ServerboundOutputInspectionRequestPacket",
    "ServerboundServerConfigRequestPacket",
    "ServerboundServerConfigUpdatePacket",
];
const APPENDED: [(&str, &str); 11] = [
    (
        "packet_transport_private",
        "ClientboundPacketObservationPacket",
    ),
    (
        "packet_transport_private",
        "ServerboundPacketInsertionPacket",
    ),
    ("client_inbox", "ClientboundClientInboxValuePacket"),
    ("client_inbox", "ServerboundClientInboxSubscriptionPacket"),
    (
        "client_program_signing",
        "ServerboundClientManagerSigningRequestPacket",
    ),
    (
        "client_program_signing",
        "ServerboundClientManagerSignaturePacket",
    ),
    (
        "client_program_signing",
        "ClientboundClientManagerSigningResponsePacket",
    ),
    (
        "multiplayer_packets",
        "ca.teamdman.sfm.common.net.multiplayer.ServerboundMultiplayerPacket",
    ),
    (
        "multiplayer_packets",
        "ca.teamdman.sfm.common.net.multiplayer.ClientboundMultiplayerPacket",
    ),
    ("manager_operator_queries", "ServerboundManagerShowPacket"),
    ("manager_operator_queries", "ClientboundManagerShowPacket"),
];

// Consume only an explicit typed golden subset; prose is not executable.
#[derive(Debug, Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    files: Vec<AuthoredFile>,
    approved_normalization: Normalization,
    functional_owner_contracts: BTreeMap<String, OwnerContract>,
}

#[derive(Debug, Facet)]
struct AuthoredFile {
    intended_core_path: String,
    stage_sha256: String,
    stage_bytes: u64,
    witnesses: Vec<Witness>,
}

#[derive(Debug, Facet)]
struct Witness {
    context: String,
    git_blob: String,
    raw_sha256: String,
    raw_bytes: u64,
    expected_sha256: String,
    expected_bytes: u64,
    normalization: String,
}

#[derive(Debug, Facet)]
struct Normalization {
    policy: String,
    rows: Vec<NormalizationRow>,
}

#[derive(Debug, Facet)]
struct NormalizationRow {
    git_blob: String,
    raw_sha256: String,
    raw_bytes: u64,
    normalized_sha256: String,
    normalized_bytes: u64,
    crlf_count: u64,
    lf_added: bool,
    has_bom: bool,
    lone_cr_count: u64,
    has_final_lf: bool,
}

#[derive(Debug, Facet)]
struct OwnerContract {
    support: Vec<String>,
    #[facet(default)]
    requires: Vec<String>,
}

struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    sources: BTreeMap<String, Vec<u8>>,
    blobs: BTreeMap<String, Vec<u8>>,
}

struct RenderedSlice {
    context: ProjectionContext,
    bodies: BTreeMap<String, String>,
    layout: ReviewedNetworkLayout,
}

impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), MAX_LEDGER_BYTES)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)
            .wrap_err("cannot parse frozen network slice evidence")?;
        let sources = PATHS
            .into_iter()
            .map(|path| Ok((path.to_owned(), shared.read_source(path)?)))
            .collect::<Result<BTreeMap<_, _>>>()?;
        let ids = RAW_SPECS
            .into_iter()
            .map(|spec| spec.oid.to_owned())
            .collect();
        let blobs = read_git_blobs(&shared.repository, &ids)?;
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
            self.ledger.schema == "sfm:core-network-first-slice@1",
            "wrong golden schema"
        );
        let expected_contexts = PINNED_CONTEXTS
            .into_iter()
            .map(|(name, oid)| (name.to_owned(), oid.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == expected_contexts,
            "frozen twenty contexts changed"
        );
        let paths = self
            .ledger
            .files
            .iter()
            .map(|file| file.intended_core_path.as_str())
            .collect::<BTreeSet<_>>();
        ensure!(
            self.ledger.files.len() == 3 && paths == BTreeSet::from(PATHS),
            "three-file scope changed"
        );
        for file in &self.ledger.files {
            self.validate_file(file, &expected_contexts)?;
        }
        self.validate_normalizations()?;
        for name in FAMILIES.into_iter().chain(OPTIONAL_MEMBERS) {
            let expected = self
                .ledger
                .functional_owner_contracts
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing golden owner {name}"))?;
            let registered = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing registered owner {name}"))?;
            ensure!(
                registered.supported_targets == expected.support,
                "support changed at {name}"
            );
            if NEW_OWNERS.contains(&name) {
                ensure!(
                    registered.requires == expected.requires,
                    "review prerequisites changed at {name}"
                );
            }
        }
        Ok(())
    }

    fn validate_file(
        &self,
        file: &AuthoredFile,
        contexts: &BTreeMap<String, String>,
    ) -> Result<()> {
        let path = file.intended_core_path.as_str();
        let (_, digest, count) = PINNED_CORE
            .into_iter()
            .find(|(p, _, _)| *p == path)
            .ok_or_else(|| eyre::eyre!("unknown authored core path"))?;
        let source = self
            .sources
            .get(path)
            .ok_or_else(|| eyre::eyre!("missing authored source"))?;
        ensure!(
            source.len() as u64 <= MAX_SOURCE_BYTES
                && sha256(source) == digest
                && source.len() as u64 == count
                && file.stage_sha256 == digest
                && file.stage_bytes == count,
            "authored migration golden changed at {path}; review the common-core edit deliberately"
        );
        let mut covered = BTreeSet::new();
        for witness in &file.witnesses {
            ensure!(
                contexts.contains_key(&witness.context) && covered.insert(&witness.context),
                "unknown or repeated witness context"
            );
            ensure!(
                witness.git_blob == expected_blob(path, &witness.context)?,
                "wrong raw body assignment"
            );
            let raw = self.blob(&witness.git_blob)?;
            let expected = expected_body(path, &witness.git_blob, raw)?;
            ensure!(
                witness.raw_sha256 == sha256(raw)
                    && witness.raw_bytes == raw.len() as u64
                    && witness.expected_sha256 == sha256(&expected)
                    && witness.expected_bytes == expected.len() as u64,
                "raw/normalized golden hash changed"
            );
            let policy = if path == CONTEXT {
                "sfm:java_lf_final_newline@1"
            } else {
                "none"
            };
            ensure!(
                witness.normalization == policy,
                "normalization scope changed"
            );
        }
        ensure!(
            covered.into_iter().eq(contexts.keys()),
            "incomplete twenty-context witness coverage"
        );
        Ok(())
    }

    fn validate_normalizations(&self) -> Result<()> {
        ensure!(
            self.ledger.approved_normalization.policy == "sfm:java_lf_final_newline@1"
                && self.ledger.approved_normalization.rows.len() == 6,
            "normalization policy widened"
        );
        let mut seen = BTreeSet::new();
        for row in &self.ledger.approved_normalization.rows {
            ensure!(
                seen.insert(row.git_blob.as_str()),
                "repeated normalized raw object"
            );
            let spec = NORMALIZED_SPECS
                .into_iter()
                .find(|spec| spec.oid == row.git_blob)
                .ok_or_else(|| eyre::eyre!("unreviewed normalized raw object"))?;
            let raw = self.blob(spec.oid)?;
            let bytes = expected_body(CONTEXT, spec.oid, raw)?;
            ensure!(
                row.raw_sha256 == sha256(raw)
                    && row.raw_bytes == raw.len() as u64
                    && row.normalized_sha256 == spec.digest
                    && row.normalized_sha256 == sha256(&bytes)
                    && row.normalized_bytes == spec.bytes
                    && row.normalized_bytes == bytes.len() as u64
                    && row.crlf_count == spec.crlf_count
                    && !row.lf_added
                    && !row.has_bom
                    && row.lone_cr_count == 0
                    && row.has_final_lf,
                "approved normalization evidence changed"
            );
        }
        Ok(())
    }

    fn blob(&self, oid: &str) -> Result<&[u8]> {
        self.blobs
            .get(oid)
            .map(Vec::as_slice)
            .ok_or_else(|| eyre::eyre!("missing frozen raw body"))
    }

    /// Source proof only: resolve actual registered support/prerequisites.
    /// It deliberately does not require a complete network project layout.
    fn source_context(&self, target: &str, owners: &[&str]) -> Result<ProjectionContext> {
        let enabled = feature_closure(&self.shared, owners)?;
        let enabled = enabled.iter().map(String::as_str).collect::<Vec<_>>();
        self.shared.context(target, &enabled)
    }

    /// An accepted network registration identity, but still a three-file proof.
    /// This does not build/collect a full project or establish codec compatibility.
    fn render_accepted_slice(&self, target: &str, owners: &[&str]) -> Result<RenderedSlice> {
        let context = self.source_context(target, owners)?;
        let layout = validate_network_layout(target, family_flags(&context)?)?;
        let bodies = self.render_source_context(&context)?;
        Ok(RenderedSlice {
            context,
            bodies,
            layout,
        })
    }

    fn render_source_context(
        &self,
        context: &ProjectionContext,
    ) -> Result<BTreeMap<String, String>> {
        let inventory = PATHS.into_iter().map(str::to_owned).collect();
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory)?;
        for path in PATHS {
            ensure!(
                selection
                    .inputs
                    .get(path)
                    .is_some_and(|input| input.input == path),
                "network source was omitted or routed away from shared core: {path}"
            );
        }
        PATHS
            .into_iter()
            .map(|path| {
                let source = std::str::from_utf8(&self.sources[path])?;
                Ok((path.to_owned(), render_java_source(source, context)?))
            })
            .collect()
    }
}

fn feature_closure(shared: &CoreTestFixture, requested: &[&str]) -> Result<BTreeSet<String>> {
    ensure!(
        shared.features.0.len() <= 256,
        "test prerequisite registry exceeds its bound"
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
            .ok_or_else(|| eyre::eyre!("unknown requested source-proof owner {name}"))?;
        for prerequisite in &definition.requires {
            if enabled.insert(prerequisite.clone()) {
                pending.push(prerequisite.clone());
            }
        }
    }
    Ok(enabled)
}

fn flag(context: &ProjectionContext, owner: &str) -> Result<bool> {
    context
        .features
        .get(owner)
        .copied()
        .ok_or_else(|| eyre::eyre!("unknown resolved owner {owner}"))
}

fn family_flags(context: &ProjectionContext) -> Result<NetworkMessageFamilies> {
    Ok(NetworkMessageFamilies {
        packet_transport_private: flag(context, "packet_transport_private")?,
        client_inbox: flag(context, "client_inbox")?,
        client_program_signing: flag(context, "client_program_signing")?,
        multiplayer_packets: flag(context, "multiplayer_packets")?,
        manager_operator_queries: flag(context, "manager_operator_queries")?,
    })
}

fn expected_blob(path: &str, context: &str) -> Result<&'static str> {
    let (kind, target) = context
        .split_once('/')
        .ok_or_else(|| eyre::eyre!("bad witness context"))?;
    ensure!(
        ["dev", "release"].contains(&kind)
            && SUPPORTED_TARGETS.into_iter().any(|(id, _)| id == target),
        "unreviewed witness context"
    );
    if kind == "dev" && matches!(target, "1.19.2" | "1.19.4") {
        return Ok(match (path, target) {
            (PACKETS, "1.19.2") => "6e7671df11884ced9535e04e3d3780b168fd45df",
            (PACKETS, _) => "b47e9fd0c47ac698f12048335e8c13cd4e7f9265",
            (DADDY, _) => "48ddb0dce77d60e3033214553d4836e2fb5ff895",
            (CONTEXT, "1.19.2") => "ad9dd331a0fd89372f2c889581de0f2a6874279b",
            (CONTEXT, _) => "5694b8d2d5724314c9bb3a1b1928eafd79b2280d",
            _ => eyre::bail!("path outside the network slice"),
        });
    }
    Ok(match (path, target) {
        (PACKETS, "1.19.2" | "1.19.4" | "1.20" | "1.20.1") => {
            "95fff5038be915e77fd357350c21947e3828262c"
        }
        (PACKETS, "1.20.2" | "1.20.3") => "d929cbace9495b1f9c53711a96040e2bb0c44692",
        (PACKETS, "1.20.4") => "6124cd8c085474bfbfba136742ca7bb4cbee6a0a",
        (PACKETS, "1.21.0" | "1.21.1") => "be6efbd87f166378e8a473625eda22ea5fbc4de7",
        (PACKETS, "26.1.2") => "f7b762cc080e7ae726fa13bb8db006b3904059f4",
        (DADDY, "1.19.2" | "1.19.4" | "1.20" | "1.20.1") => {
            "22685247ec1d494bd11a29506f738a354c16e8ec"
        }
        (DADDY, "1.20.2" | "1.20.3") => "fd87ba46e48cc6b275664c04eb0afae01a523b78",
        (DADDY, "1.20.4") => "d61a169db857cc83371adc5748e15423d1ac36a9",
        (DADDY, "1.21.0" | "1.21.1" | "26.1.2") => "dfffea5463ab7191c99d1af3e09a3342bd24fb12",
        (CONTEXT, "1.19.2" | "1.19.4" | "1.20" | "1.20.1") => {
            "85e15991d61bfa83157b72ba60a994078a5f456a"
        }
        (CONTEXT, "1.20.2" | "1.20.3") => "1c53eee93a86d5b47488fd957793c268b9fdf931",
        (CONTEXT, "1.20.4") => "ffafc6ae032b2aed5cd4ce2d9925e305a520a1d5",
        (CONTEXT, "1.21.0" | "1.21.1" | "26.1.2") => "d3b88a9b72253ca06a44409a9098b18d49c9076e",
        _ => eyre::bail!("unknown path/target witness"),
    })
}

fn expected_body(path: &str, oid: &str, raw: &[u8]) -> Result<Vec<u8>> {
    let spec = RAW_SPECS
        .into_iter()
        .find(|spec| spec.path == path && spec.oid == oid)
        .ok_or_else(|| eyre::eyre!("unreviewed path/raw object"))?;
    ensure!(
        sha256(raw) == spec.digest
            && raw.len() as u64 == spec.bytes
            && raw.ends_with(b"\n")
            && !raw.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw object changed; no implicit normalization adoption"
    );
    let source = std::str::from_utf8(raw)?;
    if path != CONTEXT {
        ensure!(
            !raw.contains(&b'\r'),
            "raw-byte source cannot normalize line endings"
        );
        return Ok(raw.to_vec());
    }
    let normalized = NORMALIZED_SPECS
        .into_iter()
        .find(|spec| spec.oid == oid)
        .ok_or_else(|| eyre::eyre!("normalization outside the exact six-object approval"))?;
    ensure!(
        raw.iter()
            .enumerate()
            .all(|(index, byte)| *byte != b'\r' || raw.get(index + 1) == Some(&b'\n')),
        "lone CR is not approved"
    );
    let count = u64::try_from(raw.windows(2).filter(|pair| *pair == b"\r\n").count())?;
    let bytes = source.replace("\r\n", "\n").into_bytes();
    ensure!(
        count == normalized.crlf_count
            && sha256(&bytes) == normalized.digest
            && bytes.len() as u64 == normalized.bytes,
        "normalized golden changed"
    );
    Ok(bytes)
}

fn registrations(source: &str) -> Result<Vec<&str>> {
    let mut names = Vec::new();
    for line in source.lines().map(str::trim) {
        let Some(tail) = line
            .strip_prefix("registerPacket(new ")
            .or_else(|| line.strip_prefix("registerPacket(registrar, new "))
        else {
            continue;
        };
        let name = tail
            .strip_suffix(".Daddy());")
            .ok_or_else(|| eyre::eyre!("registration shape changed"))?;
        ensure!(
            name.len() <= 256
                && name
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || byte == b'.'),
            "unbounded/non-class registration name"
        );
        names.push(name);
    }
    ensure!(names.len() <= 48, "unreviewed registration count");
    Ok(names)
}

fn protocol_version(source: &str) -> Result<&str> {
    let markers = ["SFM_CHANNEL_VERSION=\"", ".versioned(\""];
    let mut values = Vec::new();
    for marker in markers {
        for (index, _) in source.match_indices(marker) {
            let rest = &source[index + marker.len()..];
            values.push(
                rest.split_once('"')
                    .ok_or_else(|| eyre::eyre!("unterminated version"))?
                    .0,
            );
        }
    }
    ensure!(
        values.len() == 1 && values[0].len() <= 16,
        "must select one bounded literal peer version"
    );
    Ok(values[0])
}

fn assert_slice_contract(target: &str, rendered: &RenderedSlice) -> Result<()> {
    let packets = &rendered.bodies[PACKETS];
    let names = registrations(packets)?;
    let mut expected = BASELINE.to_vec();
    for (owner, name) in APPENDED {
        if flag(&rendered.context, owner)? {
            expected.push(name);
        }
    }
    ensure!(
        names == expected && names.len() == usize::from(rendered.layout.message_count()),
        "ordered packet registration layout changed"
    );
    ensure!(
        protocol_version(packets)? == rendered.layout.protocol_version(),
        "wrong literal peer version"
    );
    let daddy = &rendered.bodies[DADDY];
    let context = &rendered.bodies[CONTEXT];
    for (body, marker, owner) in [
        (
            packets,
            "NetworkDirection direction =",
            "packet_direction_validation",
        ),
        (
            packets,
            "Optional.of(direction)",
            "packet_direction_validation",
        ),
        (
            daddy,
            "hasExpectedDirection(getPacketDirection())",
            "packet_direction_validation",
        ),
        (
            context,
            "public boolean hasExpectedDirection(",
            "packet_direction_validation",
        ),
        (
            context,
            "if (!menu.stillValid(sender))",
            "manager_menu_request_validation",
        ),
        (
            context,
            "!managerMenu.MANAGER_POSITION.equals(pos)",
            "manager_menu_request_validation",
        ),
        (
            context,
            "public Object networkConnectionIdentity()",
            "multiplayer_packets",
        ),
        (
            context,
            ".forExecutionSide(ProgramExecutionSide.SERVER)",
            "sfml_execution_side",
        ),
        (
            packets,
            "public static boolean sendPacketObservation(",
            "packet_transport_private",
        ),
    ] {
        ensure!(
            body.contains(marker) == flag(&rendered.context, owner)?,
            "member guard leaked/omitted {marker}"
        );
    }
    ensure!(
        rendered
            .bodies
            .values()
            .all(|source| !source.contains("{%")),
        "unrendered source directive"
    );
    assert_api_contract(target, &rendered.bodies)
}

fn assert_api_contract(target: &str, bodies: &BTreeMap<String, String>) -> Result<()> {
    let packets = &bodies[PACKETS];
    let daddy = &bodies[DADDY];
    let context = &bodies[CONTEXT];
    match target {
        "1.19.2" | "1.19.4" | "1.20" | "1.20.1" => ensure!(
            packets.contains("net.minecraftforge.network.NetworkRegistry;")
                && daddy.contains("Supplier<NetworkEvent.Context> contextSupplier")
                && context.contains("private final NetworkEvent.Context inner;"),
            "legacy namespace/context API changed"
        ),
        "1.20.2" | "1.20.3" => ensure!(
            packets.contains("net.neoforged.neoforge.network.NetworkRegistry;")
                && daddy.contains("NetworkEvent.Context outerContext")
                && context.contains("private final NetworkEvent.Context inner;"),
            "NeoForge SimpleChannel context API changed"
        ),
        "1.20.4" => ensure!(
            packets.contains("IPayloadRegistrar registrar")
                && packets.contains("public void write(FriendlyByteBuf")
                && context.contains("inner.workHandler().submitAsync(runnable)")
                && daddy.contains("PlayPayloadContext outerContext"),
            "1.20.4 named-payload API changed"
        ),
        "1.21.0" | "1.21.1" | "26.1.2" => {
            ensure!(
                packets.contains("TYPE_MAP")
                    && packets.contains("StreamCodec<RegistryFriendlyByteBuf")
                    && daddy.contains("IPayloadContext outerContext")
                    && context.contains("inner.enqueueWork(runnable)"),
                "modern StreamCodec/context API changed"
            );
            if target == "26.1.2" {
                ensure!(
                    packets.contains("import net.minecraft.resources.Identifier;")
                        && packets.contains("ClientPacketDistributor.sendToServer("),
                    "26.1.2 identifier/client API changed"
                );
            } else {
                ensure!(
                    packets.contains("import net.minecraft.resources.ResourceLocation;")
                        && packets.contains("ResourceLocation.fromNamespaceAndPath("),
                    "ResourceLocation API changed"
                );
            }
        }
        _ => eyre::bail!("unsupported API proof target"),
    }
    Ok(())
}

#[test]
fn sixty_frozen_network_witnesses_use_real_core_selector_and_renderer() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut compared = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid pinned context"))?;
        let mut owners = Vec::new();
        if matches!(name, "dev/1.19.2" | "dev/1.19.4") {
            owners.extend_from_slice(&FAMILIES[..4]);
            owners.extend(["packet_direction_validation", "sfml_execution_side"]);
            if target == "1.19.2" {
                owners.extend([
                    "manager_operator_queries",
                    "manager_menu_request_validation",
                ]);
            }
        }
        let rendered = fixture.render_accepted_slice(target, &owners)?;
        assert_slice_contract(target, &rendered)?;
        for path in PATHS {
            let oid = expected_blob(path, name)?;
            let expected = expected_body(path, oid, fixture.blob(oid)?)?;
            assert_eq!(rendered.bodies[path].as_bytes(), expected, "{name}: {path}");
            compared += 1;
        }
    }
    assert_eq!(compared, 60);
    Ok(())
}

#[test]
fn thirty_dependency_closed_layout_member_contexts_keep_order_and_loader_apis() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut contexts = BTreeSet::new();
    let mut checked = 0;
    for (target, _) in SUPPORTED_TARGETS {
        let mut layouts = vec![Vec::new()];
        if matches!(target, "1.19.2" | "1.19.4") {
            layouts.push(FAMILIES[..4].to_vec());
        }
        if target == "1.19.2" {
            layouts.push(FAMILIES.to_vec());
        }
        let optional = OPTIONAL_MEMBERS
            .into_iter()
            .filter(|name| {
                fixture.shared.features.0[*name]
                    .supported_targets
                    .iter()
                    .any(|id| id == target)
            })
            .collect::<Vec<_>>();
        for layout in layouts {
            for bits in 0..(1_usize << optional.len()) {
                let mut requested = layout.clone();
                requested.extend(
                    optional
                        .iter()
                        .enumerate()
                        .filter_map(|(index, name)| (bits & (1 << index) != 0).then_some(*name)),
                );
                let enabled = feature_closure(&fixture.shared, &requested)?;
                if !contexts.insert((target.to_owned(), enabled)) {
                    continue;
                }
                let rendered = fixture.render_accepted_slice(target, &requested)?;
                assert_slice_contract(target, &rendered)?;
                checked += 1;
            }
        }
    }
    assert_eq!(checked, 30, "review changed feature closure deliberately");
    Ok(())
}

#[test]
fn valid_source_only_partial_owner_contexts_are_not_accepted_network_projects() -> Result<()> {
    let fixture = Fixture::load()?;
    for owner in FAMILIES {
        let context = fixture.source_context("1.19.2", &[owner])?;
        assert!(
            validate_network_layout("1.19.2", family_flags(&context)?).is_err(),
            "accepted partial owner {owner}"
        );
        assert!(fixture.render_accepted_slice("1.19.2", &[owner]).is_err());
        // Source selection/rendering itself stays useful for an isolated proof.
        // Its output is deliberately not a jar/complete project acceptance.
        fixture.render_source_context(&context)?;
    }
    Ok(())
}

#[test]
fn parser_only_broadcast_proof_does_not_fabricate_signing_or_multiplayer_prerequisites()
-> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture.source_context("1.19.2", &["packet_transport_private"])?;
    ensure!(
        !flag(&context, "client_program_signing")?
            && !flag(&context, "multiplayer_packets")?
            && !flag(&context, "client_inbox")?,
        "parser-only context widened to a complete network bundle"
    );
    assert!(validate_network_layout("1.19.2", family_flags(&context)?).is_err());
    let selected = select_core_inputs(
        &fixture.shared.metadata,
        &context,
        &BTreeSet::from([GRAMMAR.to_owned()]),
    )?;
    ensure!(
        selected
            .inputs
            .get(GRAMMAR)
            .is_some_and(|input| input.input == GRAMMAR && input.template),
        "isolated grammar must use actual template:true selection"
    );
    let source = read_bounded(&fixture.shared.core.join(GRAMMAR), MAX_SOURCE_BYTES)?;
    let grammar = render_java_source(std::str::from_utf8(&source)?, &context)?;
    ensure!(
        grammar.contains("broadcastStatement :") && !grammar.contains("{%"),
        "parser-only broadcast grammar failed"
    );
    let helpers = fixture.render_source_context(&context)?;
    ensure!(
        !helpers[DADDY].contains("hasExpectedDirection(getPacketDirection())")
            && !helpers[CONTEXT].contains("networkConnectionIdentity"),
        "helper-only proof acquired unrelated runtime authority"
    );
    Ok(())
}

#[test]
fn common_source_edit_propagates_across_three_mc_apis_in_an_isolated_fixture() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::Builder::new()
        .prefix("sfm-core-network-source-edit-")
        .tempdir()?;
    let core = temp.path().join(CORE_ROOT);
    let comment = "    // Isolated common-source proof, not a persisted core edit.\n";
    for path in PATHS {
        let source = std::str::from_utf8(&fixture.sources[path])?;
        let anchor = match path {
            PACKETS => "public class SFMPackets {\n",
            DADDY => "public interface SFMPacketDaddy<T extends SFMPacket> {\n",
            CONTEXT => "public class SFMPacketHandlingContext {\n",
            _ => eyre::bail!("unknown source-edit fixture path"),
        };
        ensure!(
            source.matches(anchor).count() == 1,
            "common-body anchor changed"
        );
        let modified = source.replacen(anchor, &format!("{anchor}{comment}"), 1);
        let output = core.join(path);
        fs::create_dir_all(
            output
                .parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(&output, modified.as_bytes())?;
        let bytes = read_bounded(&output, MAX_SOURCE_BYTES)?;
        for target in ["1.19.2", "1.20.4", "26.1.2"] {
            let context = fixture.source_context(target, &[])?;
            let selected = select_core_inputs(
                &fixture.shared.metadata,
                &context,
                &BTreeSet::from([path.to_owned()]),
            )?;
            ensure!(
                selected
                    .inputs
                    .get(path)
                    .is_some_and(|input| input.input == path),
                "fixture source not selected"
            );
            let before = render_java_source(source, &context)?;
            let after = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            assert_eq!(
                after,
                before.replacen(anchor, &format!("{anchor}{comment}"), 1)
            );
            assert_eq!(after.replace(comment, ""), before);
        }
    }
    Ok(())
}

#[test]
fn exact_six_object_newline_policy_refuses_altered_or_unrelated_raw_bodies() -> Result<()> {
    let fixture = Fixture::load()?;
    for spec in NORMALIZED_SPECS {
        let raw = fixture.blob(spec.oid)?;
        let normalized = expected_body(CONTEXT, spec.oid, raw)?;
        assert_eq!(
            normalized,
            std::str::from_utf8(raw)?.replace("\r\n", "\n").as_bytes()
        );
        for altered in [
            [raw, b" "].concat(),
            [b"\xef\xbb\xbf".as_slice(), raw].concat(),
            [raw, b"\r"].concat(),
        ] {
            assert!(expected_body(CONTEXT, spec.oid, &altered).is_err());
        }
        let mut token_edit = raw.to_vec();
        token_edit[0] = b'G';
        assert!(expected_body(CONTEXT, spec.oid, &token_edit).is_err());
        assert!(expected_body(PACKETS, spec.oid, raw).is_err());
        assert!(expected_body(CONTEXT, spec.oid, &normalized).is_err());
    }
    Ok(())
}
