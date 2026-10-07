//! Eleven genuine signer/trust providers through authored-core selection.
//!
//! Historical Git objects are bounded offline test witnesses, never production
//! source routing. Ten leaves retain signing ownership, while ConsentReview is
//! independently consent-owned. No test constructs a Java key/store/controller,
//! executes a signing/storage operation or supplies fake packets/acknowledgements.
//! Parent promotion and sparse rules precede registration. These tests prove
//! source/membership/collector contracts, not Java/runtime/storage acceptance.
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

const LEDGER: &str = "docs/tasks/sfm-core-signer-trust-providers-slice.json";
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const TEN: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const CONSENT: [&str; 2] = ["client_program_consent", "sfml_execution_side"];
const MANAGER: [&str; 4] = [
    "client_manager",
    "client_program_consent",
    "disk_readonly_access",
    "sfml_execution_side",
];
const FRAME: [&str; 5] = [
    "client_frame_language",
    "client_manager",
    "client_program_consent",
    "disk_readonly_access",
    "sfml_execution_side",
];
const ACTION: [&str; 9] = [
    "client_actions",
    "client_manager",
    "client_program_actions",
    "client_program_consent",
    "disk_readonly_access",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
    "sfml_execution_side",
];
const SIGNING: [&str; 11] = [
    "client_actions",
    "client_frame_language",
    "client_manager",
    "client_program_actions",
    "client_program_consent",
    "client_program_signing",
    "disk_readonly_access",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
    "sfml_execution_side",
];
const DEFINITIONS: [(&str, &[&str], &[&str]); 11] = [
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
        "client_program_signing",
        &["1.19.2", "1.19.4"],
        &["client_frame_language", "client_program_actions"],
    ),
    ("sfml_execution_side", &["1.19.2", "1.19.4"], &[]),
    (
        "disk_readonly_access",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    ("packet_values", &["1.19.2", "1.19.4"], &[]),
    (
        "client_actions",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "packet_computation",
        &["1.19.2", "1.19.4"],
        &["packet_values", "runtime_resource_cleanup"],
    ),
    ("runtime_resource_cleanup", &["1.19.2", "1.19.4"], &[]),
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
struct Golden {
    path: &'static str,
    oid: &'static str,
    bytes: usize,
    digest: &'static str,
    lf: usize,
    owner: &'static str,
    edit_anchor: &'static str,
}
const GOLDENS: [Golden; 11] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramSignerTrustRuntime.java",
        oid: "ead5d012a11bd37287cda7f6ca3d19604689b3e6",
        bytes: 3500,
        digest: "sha256:d4d26435fb556c919b10947bdd823f728248b192e7c62a6acaafa08c7f577461",
        lf: 62,
        owner: "client_program_signing",
        edit_anchor: "public final class ClientProgramSignerTrustRuntime",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramSignerTrustService.java",
        oid: "73efbe43d5e5214bfd37b6a5ffa62e86c90e0409",
        bytes: 7094,
        digest: "sha256:bfbd6611b9f95696df0feeb64c4e9d832865dbab2d05ff34e2d2aeb771961807",
        lf: 135,
        owner: "client_program_signing",
        edit_anchor: "public final class ClientProgramSignerTrustService",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramSignerTrustGrant.java",
        oid: "a96fbd30a1be2f0215cabf0adace7649a64e70bb",
        bytes: 3822,
        digest: "sha256:3e80b2ce6fe472a4a9fc0764963f828651903762c3a22e77885cf56e12505d12",
        lf: 65,
        owner: "client_program_signing",
        edit_anchor: "public record ClientProgramSignerTrustGrant",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramSignerTrustStore.java",
        oid: "cf94f1319e2dd76cfee44ed39dd107f255ce1b45",
        bytes: 12121,
        digest: "sha256:ebf830c494b98ccd1f21478eae29324e83a1f5c2430a7c2698737326b2883f32",
        lf: 208,
        owner: "client_program_signing",
        edit_anchor: "public final class ClientProgramSignerTrustStore",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/signing/ClientProgramSigningController.java",
        oid: "c08b5dc5cb28908bc9966d6546128a70dee5fb11",
        bytes: 18631,
        digest: "sha256:0cae5bda077697ac23402a29ee36c78eead52675c74948544ac1aab5be9eae8a",
        lf: 333,
        owner: "client_program_signing",
        edit_anchor: "public final class ClientProgramSigningController",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/signing/ClientProgramSigningCeremony.java",
        oid: "8727fab5e5fb05078108b068c4c4fc3e765609a3",
        bytes: 1449,
        digest: "sha256:6c3e3db2f701b1ba398f17e84ed282b1735a244e62c51fbbe21b59073e784969",
        lf: 32,
        owner: "client_program_signing",
        edit_anchor: "public final class ClientProgramSigningCeremony",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/signing/ClientProgramSigningReview.java",
        oid: "9e6a910735f6f28f341e5689a3e53b72ed2a0242",
        bytes: 4333,
        digest: "sha256:b75d4ede4d19cd17a32ef9a95f5ab040208c6e4252933afde10dc543f2dfad2a",
        lf: 74,
        owner: "client_program_signing",
        edit_anchor: "public final class ClientProgramSigningReview",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/signing/ClientSigningKeyStore.java",
        oid: "17257b80da52d25000aade5b75de0e639eb4b181",
        bytes: 19974,
        digest: "sha256:a8d11fbcd21c0bd5a0859465e61af148aabcd13835f5f0daf0f1b8b1c241ac9c",
        lf: 396,
        owner: "client_program_signing",
        edit_anchor: "public final class ClientSigningKeyStore",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/signing/ClientSigningSecretBuffer.java",
        oid: "4fbc7422783c32725d4fed8352fb31dcfda2b858",
        bytes: 1316,
        digest: "sha256:2399409dfaf0d961999d9a29e843efa50c1d1033c2725651f478eacd82d99b23",
        lf: 31,
        owner: "client_program_signing",
        edit_anchor: "public final class ClientSigningSecretBuffer",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/signing/ClientSigningUnlockOperation.java",
        oid: "ed21875c48cea521d72b6a08720e7da2b838a892",
        bytes: 1581,
        digest: "sha256:a44c8641be1b64b5124975921bfc06f7820f2d38b4dbc85c707baf0f0f56b020",
        lf: 34,
        owner: "client_program_signing",
        edit_anchor: "public final class ClientSigningUnlockOperation",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentReview.java",
        oid: "bbd322be57e9278a851f06d91159b183899b8783",
        bytes: 5359,
        digest: "sha256:20ea9e4b0852e2b514cadb150142cae2172a61f327a5fa6f77f0a19e3be72728",
        lf: 91,
        owner: "client_program_consent",
        edit_anchor: "public final class ClientProgramConsentReview",
    },
];
const PROVIDERS: [(&str, usize, &str); 15] = [
    (
        "src/main/java/ca/teamdman/sfm/client/program/ClientProgramIdentity.java",
        4288,
        "sha256:5a98ab3ed814af1664864a95f4bc65a2e20789afe2d9a9d28b78559e773ec462",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/program/ClientProgramWorldIdentity.java",
        1010,
        "sha256:bf911ed28ddb5ab17e0913188c40029ecb17b41afbbfc787331982d29e6f1219",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentGate.java",
        5627,
        "sha256:8d0d619601b0e7fbdba204927af635afbe347631a3671a156588cf565fb411af",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentStore.java",
        21472,
        "sha256:7cc7e3c6c71ac516b984d06c31c276be0a10effcfea2f7cbbd5b470ead09d6fe",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/program/ClientFrameSourceBudget.java",
        2107,
        "sha256:e2671efcb2dbbbf554d6e5d105f78aefdbdb86865cfe9ba677c57a047ba5b1a2",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/program/ClientManagerTargetBindings.java",
        2139,
        "sha256:7c7336e3e0034daadc9d18bd157ade145bcbf7e88c8faa0e2a08f2feb1a409f7",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/action/SFMClientProgramActionDispatcher.java",
        3525,
        "sha256:a0245427dc691a9c8051dda10075bb39e11021c252e863011450094d43d20452",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramSignatureDescriptor.java",
        4472,
        "sha256:fe5538c9146ef5b7e0685d3ae18b4f63833b12d186331f550fe35fa551a2cd82",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramAttestation.java",
        3965,
        "sha256:31e5bdd9ae6488189f8fe9e568856f3c98dc233acb2ad02742fa762893972dab",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramAttestationHistory.java",
        1386,
        "sha256:6e23902c16c8b1cd22564fa9db910ee02d32b1b1a386c20fc54c660736963caa",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningSnapshot.java",
        1337,
        "sha256:273ffaff7ff66bae05e815ae737cbc07212a4c08025e6895b4f639f9f7a5a5b8",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningAcknowledgement.java",
        912,
        "sha256:8de69b4c67cdb77eb703c1f0c68d5d2fa7897c85fc6e4b27430866fd76192918",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningBody.java",
        3813,
        "sha256:cfeb326229ab621963883792fa959502e76852201b6e9f53f554c13304d30ecb",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningCodec.java",
        7207,
        "sha256:038be7d0522ddcc2452d6d69dec8459e569547e56a43322b0abbdf6564a49c58",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningState.java",
        8942,
        "sha256:9c2096bdcc9b18c95c3a32373b51a7daa8be727a0c54aecc8da780d6a0ce114f",
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    contract_changes: bool,
    definitions: BTreeMap<String, Definition>,
    context_commits: BTreeMap<String, String>,
    files: Vec<SourceEvidence>,
    current_provider_source_receipts: Vec<ProviderEvidence>,
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
    authored_bytes: usize,
    authored_sha256: String,
    cr_count: usize,
    lf_count: usize,
    final_lf: bool,
    bom: bool,
    source_rule: InputVariant,
    witnesses: BTreeMap<String, Option<String>>,
    common_edit_anchor: String,
}
#[derive(Facet)]
struct ProviderEvidence {
    path: String,
    bytes: usize,
    sha256: String,
    rules: Vec<InputVariant>,
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
            192 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-signer-trust-providers-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && ledger.files.len() == 11
                && ledger.definitions.len() == 11
                && ledger.current_provider_source_receipts.len() == 15
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
                        .collect(),
            "signer/trust bounded source evidence changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let original = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing original signer contract: {name}"))?;
            let current = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing actual signer contract: {name}"))?;
            ensure!(
                original.supported_targets == support
                    && original.requires == requires
                    && current.supported_targets == support
                    && current.requires == requires,
                "original/current signer contract changed: {name}"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|g| g.oid.to_owned()).collect(),
        )?;
        let mut sources = BTreeMap::new();
        for (g, evidence) in GOLDENS.iter().zip(&ledger.files) {
            ensure!(
                evidence.path == g.path
                    && evidence.owner == g.owner
                    && evidence.raw_oid == g.oid
                    && evidence.raw_bytes == g.bytes
                    && evidence.raw_sha256 == g.digest
                    && evidence.authored_bytes == g.bytes
                    && evidence.authored_sha256 == g.digest
                    && evidence.cr_count == 0
                    && evidence.lf_count == g.lf
                    && evidence.final_lf
                    && !evidence.bom
                    && evidence.common_edit_anchor == g.edit_anchor
                    && evidence.witnesses == expected_witnesses(g),
                "immutable signer/trust raw identity/membership changed"
            );
            verify_raw(&raw[g.oid], g)?;
            let source = core.read_source(g.path)?;
            verify_raw(&source, g)?;
            ensure!(
                source == raw[g.oid],
                "signer/trust raw source was rewritten: {}",
                g.path
            );
            let rules = core.metadata.source_rules.get(g.path).ok_or_else(|| {
                eyre::eyre!("root must first promote sparse signer rule: {}", g.path)
            })?;
            ensure!(
                rules.len() == 1
                    && rules[0] == evidence.source_rule
                    && rules[0].input == g.path
                    && rules[0].template
                    && rules[0].when.targets == D2
                    && rules[0].when.all_features == vec![g.owner]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "signer membership must retain exact existing owner/D2 support"
            );
            sources.insert(g.path.to_owned(), source);
        }
        for ((path, bytes, digest), evidence) in PROVIDERS
            .iter()
            .zip(&ledger.current_provider_source_receipts)
        {
            ensure!(
                evidence.path == *path
                    && evidence.bytes == *bytes
                    && evidence.sha256 == *digest
                    && core.metadata.source_rules.get(*path) == Some(&evidence.rules),
                "real existing signer provider identity/membership changed: {path}"
            );
            let source = core.read_source(path)?;
            ensure!(
                source.len() == *bytes && sha256(&source) == *digest,
                "real existing signer provider bytes changed: {path}"
            );
        }
        ensure!(
            sources.values().map(Vec::len).sum::<usize>() == 79_180,
            "eleven raw signer source byte total changed"
        );
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
                "unaccounted signer source omission"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.sources[path])?,
            context,
        )?))
    }
    fn provider(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        let selected = select_core_inputs(
            &self.core.metadata,
            context,
            &BTreeSet::from([path.to_owned()]),
        )?;
        let input = selected
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("real signer provider omitted: {path}"))?;
        render_java_source(
            std::str::from_utf8(&self.core.read_source(&input.input)?)?,
            context,
        )
    }
    fn bounded_metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|path, _| self.sources.contains_key(path));
        metadata
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
            // Real selected standalone inputs, not fake Java runtime providers.
            let bytes = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let destination = root.join(&input.input);
            if destination.exists() {
                ensure!(
                    read_bounded(&destination, 16 * 1024 * 1024)? == bytes,
                    "standalone fixture input differs between exact contexts"
                );
            } else {
                fs::create_dir_all(
                    destination
                        .parent()
                        .ok_or_else(|| eyre::eyre!("project input lacks a parent"))?,
                )?;
                fs::write(destination, bytes)?;
            }
        }
        Ok(())
    }
    fn write_sources(&self, root: &Path) -> Result<()> {
        for (path, source) in &self.sources {
            let destination = root.join(path);
            fs::create_dir_all(
                destination
                    .parent()
                    .ok_or_else(|| eyre::eyre!("fixed signer source lacks a parent"))?,
            )?;
            fs::write(destination, source)?;
        }
        Ok(())
    }
    fn body(&self, suffix: &str) -> Result<&str> {
        let (path, bytes) = self
            .sources
            .iter()
            .find(|(path, _)| path.ends_with(suffix))
            .ok_or_else(|| eyre::eyre!("bounded signer source not found: {suffix}"))?;
        ensure!(path.ends_with(".java"), "signer body must be Java");
        Ok(std::str::from_utf8(bytes)?)
    }
}
fn present(name: &str) -> bool {
    matches!(name, "dev/1.19.2" | "dev/1.19.4")
}
fn expected_witnesses(g: &Golden) -> BTreeMap<String, Option<String>> {
    CONTEXTS
        .into_iter()
        .map(|(name, _)| (name.to_owned(), present(name).then(|| g.oid.to_owned())))
        .collect()
}
fn verify_raw(bytes: &[u8], g: &Golden) -> Result<()> {
    ensure!(
        bytes.len() == g.bytes
            && sha256(bytes) == g.digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && bytes.iter().filter(|byte| **byte == b'\n').count() == g.lf
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw signer bytes changed; no normalization is approved"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn profiles() -> Vec<Vec<&'static str>> {
    vec![
        vec![],
        vec!["sfml_execution_side"],
        CONSENT.to_vec(),
        MANAGER.to_vec(),
        FRAME.to_vec(),
        ACTION.to_vec(),
        SIGNING.to_vec(),
    ]
}

#[test]
fn all_two_hundred_twenty_signer_cells_match_real_git_membership_and_raw_rendered_goldens()
-> Result<()> {
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
            output.status.success() && output.stdout.len() <= 16_384 && output.stderr.len() <= 4096,
            "bounded local frozen signer tree read failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed signer tree record"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|expected| expected == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected signer tree member"
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
        assert_eq!(
            actual, expected,
            "real immutable eleven-path membership: {name}"
        );
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("fixed target missing"))?;
        // Closed source-proof features do not impersonate historical global flags.
        let context = f
            .core
            .context(target, if present(name) { &SIGNING } else { &[] })?;
        for g in &GOLDENS {
            let body = f.render(g.path, &context)?;
            if present(name) {
                assert_eq!(
                    body.as_deref().map(str::as_bytes),
                    Some(f.raw[g.oid].as_slice())
                );
                counts.0 += 1;
            } else {
                assert!(body.is_none());
                counts.1 += 1;
            }
        }
    }
    assert_eq!(counts, (22, 198));
    Ok(())
}

#[test]
fn one_hundred_fifty_four_valid_cells_keep_consent_projection_independent_of_signing() -> Result<()>
{
    let f = Fixture::load()?;
    let mut counts = (0, 0);
    for target in D2 {
        for flags in profiles() {
            let context = f.core.context(target, &flags)?;
            let mut descriptive = context.clone();
            descriptive.environment = "release".to_owned();
            descriptive.preset = "description-is-not-signer-ownership".to_owned();
            descriptive.projection_key = "independent/signer-source".to_owned();
            for g in &GOLDENS {
                let body = f.render(g.path, &context)?;
                assert_eq!(body.is_some(), context.features[g.owner]);
                assert_eq!(body, f.render(g.path, &descriptive)?);
                if let Some(body) = body {
                    assert_eq!(body.as_bytes(), f.raw[g.oid]);
                    assert!(!body.contains("{%"));
                    counts.0 += 1;
                } else {
                    counts.1 += 1;
                }
            }
        }
        let minimal = f.core.context(target, &CONSENT)?;
        for forbidden in [
            "client_manager",
            "client_frame_language",
            "client_program_actions",
            "client_frame_render",
            "client_program_signing",
            "client_actions",
            "packet_values",
            "image_resources",
            "touch_display",
            "multiplayer_packets",
            "client_manager_gui",
        ] {
            assert!(
                !minimal.features[forbidden],
                "plain consent review forces {forbidden}"
            );
        }
        for g in &GOLDENS {
            assert_eq!(
                f.render(g.path, &minimal)?.is_some(),
                g.owner == "client_program_consent"
            );
        }
    }
    assert_eq!(counts, (30, 124));
    Ok(())
}

#[test]
fn exact_prerequisites_missing_owner_predicates_and_other_eight_targets_refuse() -> Result<()> {
    let f = Fixture::load()?;
    let mut refused = 0;
    for target in D2 {
        for owner in [
            "client_manager",
            "client_program_consent",
            "client_program_signing",
            "client_frame_language",
            "client_program_actions",
        ] {
            for prerequisite in &f.core.features.0[owner].requires {
                let flags = SIGNING
                    .iter()
                    .copied()
                    .filter(|name| *name != prerequisite.as_str())
                    .collect::<Vec<_>>();
                assert!(
                    f.core.context(target, &flags).is_err(),
                    "accepted missing {owner} prerequisite {prerequisite}"
                );
                refused += 1;
            }
        }
        for owner in ["client_program_consent", "client_program_signing"] {
            let mut broken = f.core.context(target, &CONSENT)?;
            assert!(broken.features.remove(owner).is_some());
            assert!(select_core_inputs(&f.core.metadata, &broken, &f.inventory()).is_err());
        }
    }
    assert_eq!(refused, 30);
    let mut unsupported = 0;
    for target in &TEN[2..] {
        for flags in [CONSENT.as_slice(), MANAGER.as_slice(), SIGNING.as_slice()] {
            assert!(f.core.context(target, flags).is_err());
            unsupported += 1;
        }
        let off = f.core.context(target, &[])?;
        for g in &GOLDENS {
            assert!(f.render(g.path, &off)?.is_none());
        }
    }
    assert_eq!(unsupported, 24);
    Ok(())
}

#[test]
fn genuine_existing_identity_public_signature_and_compiler_provider_contracts_are_selected()
-> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        let signing = f.core.context(target, &SIGNING)?;
        for (path, anchors) in [
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientProgramIdentity.java",
                &[
                    "public record ClientProgramIdentity(",
                    "fromStoredSourceAndBindings(",
                    "Set<ResourceLocation> requestedCapabilities",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentGate.java",
                &[
                    "public final class ClientProgramConsentGate",
                    "store.hasRecordedDecision(identity, capability)",
                    "signerAuthority.test(identity, capability)",
                    "reopenDenied",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentStore.java",
                &[
                    "public static final int MAX_PROGRAMS = 128;",
                    "public static final int MAX_CAPABILITIES = 64;",
                    "public synchronized List<Snapshot> snapshots()",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramSignatureDescriptor.java",
                &[
                    "normalizedSourceBytes",
                    "public static ProgramSignatureDescriptor fromSource(",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramAttestation.java",
                &[
                    "public record ProgramAttestation(",
                    "verifies(ProgramSignatureDescriptor",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningAcknowledgement.java",
                &[
                    "public record ClientManagerSigningAcknowledgement(",
                    "ProgramSignatureDescriptor descriptor",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningSnapshot.java",
                &["public record ClientManagerSigningSnapshot(", "descriptor("][..],
            ),
        ] {
            let body = f.provider(path, &signing)?;
            for anchor in anchors {
                assert!(body.contains(anchor), "real provider {path}: {anchor}");
            }
        }
        for (path, _, _) in PROVIDERS {
            assert!(!f.provider(path, &signing)?.contains("{%"));
        }
        let consent = f.core.context(target, &CONSENT)?;
        let source = f
            .render(GOLDENS[10].path, &consent)?
            .ok_or_else(|| eyre::eyre!("independent review omitted"))?;
        assert!(source.contains("ClientProgramConsentStore.Snapshot"));
        assert!(!source.contains("ClientProgramSignerTrust"));
        assert!(!source.contains("ClientSigningKeyStore"));
        // These real provider peers do not supply a missing packet, BE, frame
        // runtime, UI worker or signing-effects bridge. No Java proof is claimed.
    }
    Ok(())
}

#[test]
fn raw_mutations_refuse_and_original_failclosed_lifetimes_secret_and_public_review_anchors_remain()
-> Result<()> {
    let f = Fixture::load()?;
    for g in &GOLDENS {
        let raw = &f.raw[g.oid];
        for mutated in [
            std::str::from_utf8(raw)?.replace('\n', "\r\n").into_bytes(),
            raw[..raw.len() - 1].to_vec(),
            [raw.as_slice(), b"\n"].concat(),
            [b" ".as_slice(), raw.as_slice()].concat(),
            [b"\xef\xbb\xbf".as_slice(), raw.as_slice()].concat(),
        ] {
            assert!(verify_raw(&mutated, g).is_err());
        }
    }
    for (suffix, anchors) in [
        (
            "ClientProgramSignerTrustRuntime.java",
            &[
                "ClientManagerFrameRuntime.liveIdentityFor(expected).isEmpty()",
                "manager.signingSnapshot()",
                "WeakHashMap<K, Cached>",
                "existing.identity().equals(identity) && existing.snapshot() == snapshot",
                "OBSERVED.clear(); service().clearTransient();",
            ][..],
        ),
        (
            "ClientProgramSignerTrustService.java",
            &[
                "implements ClientProgramSignerAuthority",
                "available = loaded.diagnostics().isEmpty();",
                "if (!currentSigners(identity).contains(fingerprint))",
                "if (!available) throw new IllegalStateException(\"Signer trust storage unavailable\");",
                "var observed = currentProgram.apply(identity);",
                "if (observed.isEmpty()) { cache.remove(identity); return false; }",
                "return known.allowed() && now < known.expiresAt();",
                "Signer rules were not saved; signer authority is disabled locally. Restart may restore older rules",
            ][..],
        ),
        (
            "ClientProgramSignerTrustGrant.java",
            &[
                "managerPosition = managerPosition.immutable();",
                "allowedCapabilities = Set.copyOf(allowedCapabilities);",
                "identity.hostSide() != ProgramExecutionSide.CLIENT",
                "!allowedCapabilities.containsAll(identity.requestedCapabilities())",
                "attestation.verifies(expected)",
            ][..],
        ),
        (
            "ClientProgramSignerTrustStore.java",
            &[
                "public static final int MAX_GRANTS = 128;",
                "public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;",
                "StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING",
                "Files.isSymbolicLink(target)",
                "Duplicate signer trust scope",
                "Trailing trust store data",
                "Malformed signer trust UTF-8",
            ][..],
        ),
        (
            "ClientProgramSigningController.java",
            &[
                "public static final long REQUEST_TIMEOUT_MILLIS = 30_000;",
                "public static final long REVIEW_TIMEOUT_MILLIS = 120_000;",
                "ClientFrameSourceBudget.permits(body.source())",
                "public boolean receive(ClientboundClientManagerSigningResponsePacket response)",
                "!snapshot.incarnation().equals(expected.incarnation())",
                "if (Thread.currentThread() == owner) failed = Problem.WORKER_UNAVAILABLE;",
                "try (operation)",
                "try (Signer signer = Objects.requireNonNull(operation.unlock()))",
                "if (done.token() != generation.get() || state != State.SIGNING)",
                "transport.submit(new ServerboundClientManagerSignaturePacket",
                "Signing controller requires its owner thread",
            ][..],
        ),
        (
            "ClientProgramSigningCeremony.java",
            &[
                "public static final int REVIEW_DELAY_TICKS = 40;",
                "public boolean ready() { return challenge != null && acknowledged && remaining == 0; }",
            ][..],
        ),
        (
            "ClientProgramSigningReview.java",
            &[
                "Signing does not grant permission to execute.",
                "Locked fingerprint is unverified until unlock.",
                "ClientProgramConsentReview.diff(",
                "Character.getType(c) == Character.FORMAT",
            ][..],
        ),
        (
            "ClientSigningKeyStore.java",
            &[
                "No constructor/read operation generates a key.",
                "static final int ITERATIONS = 600_000;",
                "AES-256-GCM",
                "this(path, new SecureRandom(), Files::createLink, ClientSigningKeyStore::deriveKey);",
                "Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)",
                "requireAbsent(target);",
                "throw new FileAlreadyExistsException(target.toString())",
                "public static final class UnlockedSigner implements AutoCloseable",
                "privateBytes == null",
                "clear(privateBytes);",
                "Windows files inherit the containing directory ACL.",
            ][..],
        ),
        (
            "ClientSigningSecretBuffer.java",
            &[
                "private final char[] value = new char[1024];",
                "char[] result = Arrays.copyOf(value, length);",
                "close();",
                "Arrays.fill(value, '\\0')",
                "return \"[hidden passphrase]\";",
            ][..],
        ),
        (
            "ClientSigningUnlockOperation.java",
            &[
                "implements ClientProgramSigningController.UnlockOperation",
                "passphrase = null;",
                "new ClientSigningKeyStore(file).unlock(captured)",
                "finally { Arrays.fill(captured, '\\0'); }",
                "public void close() { signer.close(); }",
            ][..],
        ),
        (
            "ClientProgramConsentReview.java",
            &[
                "never an execution input.",
                "public static List<String> diff(String before, String after)",
                "Linear changed-segment diff avoids quadratic work",
                "Sources are identical.",
            ][..],
        ),
    ] {
        let source = f.body(suffix)?;
        for anchor in anchors {
            assert!(source.contains(anchor), "preserved signer anchor: {anchor}");
        }
    }
    let service = f.body("ClientProgramSignerTrustService.java")?;
    let permits = service
        .split("boolean permits(")
        .nth(1)
        .ok_or_else(|| eyre::eyre!("genuine permits member missing"))?;
    let live = permits
        .find("currentProgram.apply(identity)")
        .ok_or_else(|| eyre::eyre!("live identity recheck missing"))?;
    let cache = permits
        .find("Cached known = cache.get(identity)")
        .ok_or_else(|| eyre::eyre!("cache member missing"))?;
    assert!(live < cache);
    let controller = f.body("ClientProgramSigningController.java")?;
    let receive = controller
        .split("public boolean receive(")
        .nth(1)
        .and_then(|tail| tail.split("public boolean sign(").next())
        .ok_or_else(|| eyre::eyre!("typed response envelope member missing"))?;
    let envelope = receive
        .find("!pendingRequest.equals(response.requestId())")
        .ok_or_else(|| eyre::eyre!("typed request identity refusal missing"))?;
    let decode = receive
        .find("decoder.decode(")
        .ok_or_else(|| eyre::eyre!("original typed acknowledgement decode missing"))?;
    assert!(envelope < decode);
    let key = f.body("ClientSigningKeyStore.java")?;
    let constructor = key
        .split("public ClientSigningKeyStore(Path path) {")
        .nth(1)
        .and_then(|tail| tail.split("/** Public metadata").next())
        .ok_or_else(|| eyre::eyre!("genuine key constructor boundary missing"))?;
    for forbidden in [
        "create(",
        "Files.write",
        "generateKeyPair",
        "unlock(",
        "publish(",
    ] {
        assert!(
            !constructor.contains(forbidden),
            "constructor acquires private-key effect: {forbidden}"
        );
    }
    // Text assertions and exact bytes only: no key, grant/store, worker,
    // environment, unlock operation or controller is constructed/executed.
    Ok(())
}

#[test]
fn omitted_signing_inputs_are_not_read_when_only_independent_consent_review_is_selected()
-> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let metadata = f.bounded_metadata();
    for g in &GOLDENS {
        let destination = root.join(g.path);
        fs::create_dir_all(
            destination
                .parent()
                .ok_or_else(|| eyre::eyre!("fixed signer input parent missing"))?,
        )?;
        fs::write(destination, b"{% if features.unreviewed_owner %}\n\xff")?;
    }
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    for target in D2 {
        let off = f.core.context(target, &[])?;
        f.copy_project_inputs(&root, &metadata, &off)?;
        let selected = select_core_inputs(&metadata, &off, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &off)?;
        for g in &GOLDENS {
            assert!(selected.omitted_paths.contains(g.path));
            assert!(!artifacts.contains_key(g.path));
        }
    }
    let review = &GOLDENS[10];
    fs::write(root.join(review.path), &f.sources[review.path])?;
    for target in D2 {
        let consent = f.core.context(target, &CONSENT)?;
        f.copy_project_inputs(&root, &metadata, &consent)?;
        let selected = select_core_inputs(&metadata, &consent, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &consent)?;
        for g in &GOLDENS {
            assert_eq!(
                artifacts.contains_key(g.path),
                g.owner == "client_program_consent"
            );
            if g.owner == "client_program_signing" {
                assert!(selected.omitted_paths.contains(g.path));
            }
        }
    }
    // SFMPackets is deliberately absent in this bounded fixture; complete
    // project negotiated transport, Java and runtime remain separate gates.
    Ok(())
}

#[test]
fn all_java_signer_sources_render_with_template_false_through_fixed_core_collector() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let review = &GOLDENS[10];
    let original = std::str::from_utf8(&f.sources[review.path])?;
    let fixture_source = format!(
        "{original}{{% if features.client_program_consent %}}\n// Isolated Java rendering probe.\n{{% endif %}}\n"
    );
    fs::write(root.join(review.path), &fixture_source)?;
    let mut metadata = f.bounded_metadata();
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let inventory = discover_core_source_files(&root)?;
    let mut count = 0;
    for target in D2 {
        for flags in [CONSENT.as_slice(), SIGNING.as_slice()] {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for g in &GOLDENS {
                if !context.features[g.owner] {
                    assert!(!artifacts.contains_key(g.path));
                    continue;
                }
                let artifact = &artifacts[g.path];
                let output = std::str::from_utf8(&artifact.output_bytes)?;
                let (_, body) = output
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("generated Java banner missing"))?;
                if g.path == review.path {
                    assert_eq!(body, render_java_source(&fixture_source, &context)?);
                    assert!(body.contains("// Isolated Java rendering probe."));
                } else {
                    assert_eq!(body.as_bytes(), f.raw[g.oid]);
                }
                assert!(!body.contains("{%"));
                assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{}", g.path));
                assert!(artifact.overlay.is_none());
                count += 1;
            }
        }
    }
    assert_eq!(count, 24);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}

#[test]
fn common_edits_reach_both_targets_without_widening_independent_consent_or_live_sources()
-> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut edited_sources = BTreeMap::new();
    for g in &GOLDENS {
        let original = std::str::from_utf8(&f.sources[g.path])?;
        assert_eq!(original.matches(g.edit_anchor).count(), 1);
        let edited = original.replacen(
            g.edit_anchor,
            &format!(
                "// Isolated common signer/provider edit.\n{}",
                g.edit_anchor
            ),
            1,
        );
        assert_ne!(edited, original);
        fs::write(root.join(g.path), &edited)?;
        edited_sources.insert(g.path.to_owned(), edited);
    }
    let inventory = discover_core_source_files(&root)?;
    let metadata = f.bounded_metadata();
    let mut outputs = 0;
    for target in D2 {
        for flags in [CONSENT.as_slice(), SIGNING.as_slice()] {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for g in &GOLDENS {
                if !context.features[g.owner] {
                    assert!(!artifacts.contains_key(g.path));
                    continue;
                }
                let output = std::str::from_utf8(&artifacts[g.path].output_bytes)?;
                let (_, body) = output
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("generated edited Java banner missing"))?;
                assert_eq!(body, render_java_source(&edited_sources[g.path], &context)?);
                assert!(body.contains("// Isolated common signer/provider edit."));
                outputs += 1;
            }
        }
    }
    assert_eq!(outputs, 24);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}
