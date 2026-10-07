//! Frozen migration goldens for 25 exact-byte development-only support leaves.
//!
//! The post-promotion tests select actual core-owned inputs and use the real
//! Liquid renderer. Exact Git objects and trees are bounded test-only witnesses,
//! never production source routing. Every membership cell is checked explicitly.
//!
//! Source contexts expand only real registered prerequisites. They do not claim
//! complete-project network acceptance, Java closure, persistence execution or a
//! running client. Deliberate future core edits require reviewing these goldens.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::InputPredicate;
use super::core_inputs::InputVariant;
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

const LEDGER: &str = "docs/tasks/sfm-core-development-leaf-slice.json";
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;
const MAX_SOURCE_BYTES: u64 = 128 * 1024;
const MAX_TREE_BYTES: u64 = 64 * 1024;
const TARGETS: [&str; 2] = ["1.19.2", "1.19.4"];

#[derive(Clone, Copy)]
struct RawSpec {
    path: &'static str,
    blob: &'static str,
    digest: &'static str,
    bytes: u64,
    owners: &'static [&'static str],
}

const RAW_SPECS: [RawSpec; 25] = [
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningAdmission.java",
        blob: "c87725e796d4ebe96a2d1d49b02428ef6c1bb85c",
        digest: "sha256:a42d06ed0fe17c9d4c867a7464ac33d8c28365b641f1b99838ee931029dfac28",
        bytes: 2031,
        owners: &["client_program_signing"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningAcknowledgement.java",
        blob: "c01322a0581ab43e4f7210ee6ad8a89af05794a2",
        digest: "sha256:8de69b4c67cdb77eb703c1f0c68d5d2fa7897c85fc6e4b27430866fd76192918",
        bytes: 912,
        owners: &["client_manager", "client_program_signing"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningBody.java",
        blob: "e32959cde86642ca69feca8994f3fd6dc12ba5c6",
        digest: "sha256:cfeb326229ab621963883792fa959502e76852201b6e9f53f554c13304d30ecb",
        bytes: 3813,
        owners: &["client_manager", "client_program_signing"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningCodec.java",
        blob: "d27640d69d3cf489a74783843ba57a96a2ca536e",
        digest: "sha256:038be7d0522ddcc2452d6d69dec8459e569547e56a43322b0abbdf6564a49c58",
        bytes: 7207,
        owners: &["client_manager", "client_program_signing"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningMetadata.java",
        blob: "d22a1d27af37684e7160441be5f5bf5e9039aa14",
        digest: "sha256:049e0bdafbddb653eaf33ed4599cd3297951a180364cbad52fd51a108dfb3ce9",
        bytes: 1870,
        owners: &["client_manager", "client_program_signing"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningSession.java",
        blob: "47fb6a7998fbcbef47f4d0532e837509c1c4f6f4",
        digest: "sha256:45013039826e255c98340e2b6ccf5944386aa86a7c8e4a1495b3fe25d87a4cba",
        bytes: 4443,
        owners: &["client_manager", "client_program_signing"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningSnapshot.java",
        blob: "7b7c6d3417afb6b3947533460c814dea085710a4",
        digest: "sha256:273ffaff7ff66bae05e815ae737cbc07212a4c08025e6895b4f639f9f7a5a5b8",
        bytes: 1337,
        owners: &[
            "client_manager",
            "client_program_signing",
            "multiplayer_packets",
        ],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningState.java",
        blob: "c090b02697c8053ac573c9d51ad6f3d9d8cb66dd",
        digest: "sha256:9c2096bdcc9b18c95c3a32373b51a7daa8be727a0c54aecc8da780d6a0ce114f",
        bytes: 8942,
        owners: &["client_manager", "client_program_signing"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramAttestation.java",
        blob: "117f0b5cdfa7b9779e31e6833110e07dbe54eefa",
        digest: "sha256:31e5bdd9ae6488189f8fe9e568856f3c98dc233acb2ad02742fa762893972dab",
        bytes: 3965,
        owners: &["client_manager", "client_program_signing"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramAttestationCodec.java",
        blob: "a8ba875b007d517520ec22c191f52e2b99a29ec1",
        digest: "sha256:1fcf0c493f1abba27d5f19eccfcdb516089546d13ca53005cb771f0e3b6719b9",
        bytes: 6771,
        owners: &["client_manager", "client_program_signing"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramAttestationHistory.java",
        blob: "829eb7582668d45f02b16d431f70f045513c4883",
        digest: "sha256:6e23902c16c8b1cd22564fa9db910ee02d32b1b1a386c20fc54c660736963caa",
        bytes: 1386,
        owners: &["client_manager", "client_program_signing"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramSignatureDescriptor.java",
        blob: "47ecb791fec6a516863c7d55f6b8d2aff745e425",
        digest: "sha256:fe5538c9146ef5b7e0685d3ae18b4f63833b12d186331f550fe35fa551a2cd82",
        bytes: 4472,
        owners: &[
            "client_manager",
            "manager_operator_queries",
            "client_program_signing",
            "multiplayer_packets",
        ],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramWorldIdentity.java",
        blob: "216ecd00a0aeebaac78240965283d9bfdf3f6d67",
        digest: "sha256:bf911ed28ddb5ab17e0913188c40029ecb17b41afbbfc787331982d29e6f1219",
        bytes: 1010,
        owners: &["client_program_consent"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramIdentity.java",
        blob: "f8b2ed8545a32b18a25c06402bfc5d493fc73202",
        digest: "sha256:5a98ab3ed814af1664864a95f4bc65a2e20789afe2d9a9d28b78559e773ec462",
        bytes: 4288,
        owners: &["client_program_consent"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentGate.java",
        blob: "a712a728d6de9537431e3871d7d64e86ac47ffa0",
        digest: "sha256:8d0d619601b0e7fbdba204927af635afbe347631a3671a156588cf565fb411af",
        bytes: 5627,
        owners: &["client_program_consent"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentStore.java",
        blob: "b56d63be3b493eea0472d16a07d987f228d0901d",
        digest: "sha256:7cc7e3c6c71ac516b984d06c31c276be0a10effcfea2f7cbbd5b470ead09d6fe",
        bytes: 21472,
        owners: &["client_program_consent"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/image/SFMImageSnapshot.java",
        blob: "77134f16da242a4d6952be809e29bfc60ba99eea",
        digest: "sha256:de07b5ab0240b841e4d002ea03e0fc4dcbfff7b4b72e6d9ab78761c923476b80",
        bytes: 10657,
        owners: &["image_resources"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/image/SFMImageSnapshotCodec.java",
        blob: "2988e248554ade5f586c81891b111ae6da855ece",
        digest: "sha256:3067418df198e024af7ac119c4038bee3186afdb06850311fc0de17db437c290",
        bytes: 2224,
        owners: &["image_resources"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/resourcetype/SFMImageStack.java",
        blob: "c6bc362cde49052862ca6a59d5b4fc1c26b12b01",
        digest: "sha256:5182f9ed9dd5b948f1b38266f8f77da0d442e45ae6dba6d0831ba14908d3f1ea",
        bytes: 2047,
        owners: &["image_resources"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/capability/IImageHandler.java",
        blob: "88ee15a76741f708c22fc5b710e2d77a214d8669",
        digest: "sha256:c88c14255bc40a77bbf6547bcdd27a1c2ead778696604b595983b90663f8c4b7",
        bytes: 619,
        owners: &["image_resources"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/value/SFMTouchValue.java",
        blob: "a6340c25600b31387aeba9d7bcffa9abd2f69488",
        digest: "sha256:66bf92c881c35c1b1a7ff1685aaebdd4a04371f6de295bd090a1f9ebe1c87293",
        bytes: 3319,
        owners: &["touch_display"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/net/SFMBoundedEffectBudget.java",
        blob: "a45dc26d066caca83d822651be77bc0b6dc25217",
        digest: "sha256:07c7e066d5f54128275c389597220612e9ae960f313ba724127a0c84efe1c572",
        bytes: 2315,
        owners: &["packet_transport_private", "client_program_actions"],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/net/SFMPacketEffectGate.java",
        blob: "f86f1e9c568c691713357cd25582f8824b982cc3",
        digest: "sha256:5c879613b51fb3e27f357afa8e75727daeb2d3bc5c789ae634f97a9ebeebbba1",
        bytes: 1228,
        owners: &[
            "packet_transport_private",
            "client_inbox",
            "client_program_signing",
            "multiplayer_packets",
        ],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/net/SFMPacketInventoryAddress.java",
        blob: "9eed969752a5ceaeb528125b673181238f8e23e9",
        digest: "sha256:6dfdaa97f975a7164e508b42730896bdf5874e131b5749dd1738a78ab1e6716b",
        bytes: 1836,
        owners: &[
            "packet_transport_private",
            "touch_display",
            "multiplayer_packets",
        ],
    },
    RawSpec {
        path: "src/main/java/ca/teamdman/sfm/common/net/SFMPacketValueEnvelope.java",
        blob: "993112616deabfe0905ce07fb3891f0b6771e12a",
        digest: "sha256:67e79434de1e9ac58b4b19785307bd688da05faf3477fbf25278c4eb86bf93aa",
        bytes: 4464,
        owners: &[
            "packet_transport_private",
            "client_inbox",
            "multiplayer_packets",
        ],
    },
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

// Only this typed golden subset is executable; review prose is not configuration.
#[derive(Debug, Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: NormalizationContract,
    owners: BTreeMap<String, OwnerContract>,
    files: Vec<AuthoredFile>,
}

#[derive(Debug, Facet)]
struct NormalizationContract {
    policy: String,
    approval_needed: bool,
}

#[derive(Debug, Facet)]
struct OwnerContract {
    support: Vec<String>,
    requires: Vec<String>,
}

#[derive(Debug, Facet)]
struct AuthoredFile {
    intended_core_path: String,
    raw_git_blob: String,
    raw_sha256: String,
    raw_bytes: u64,
    stage_sha256: String,
    stage_bytes: u64,
    predicate: InputPredicate,
    membership: BTreeMap<String, Option<String>>,
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
            .wrap_err("cannot parse bounded development-leaf source evidence")?;
        let sources = RAW_SPECS
            .into_iter()
            .map(|spec| Ok((spec.path.to_owned(), shared.read_source(spec.path)?)))
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
            self.ledger.schema == "sfm:core-development-leaf-slice@1"
                && self.ledger.normalization.policy == "none"
                && !self.ledger.normalization.approval_needed,
            "leaf import scope must remain raw and explicitly bounded"
        );
        let contexts = PINNED_CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == contexts,
            "exact twenty source contexts changed"
        );
        let paths = RAW_SPECS
            .into_iter()
            .map(|spec| spec.path)
            .collect::<BTreeSet<_>>();
        ensure!(
            self.ledger.files.len() == 25
                && self
                    .ledger
                    .files
                    .iter()
                    .map(|file| file.intended_core_path.as_str())
                    .collect::<BTreeSet<_>>()
                    == paths,
            "exact twenty-five-file import scope changed"
        );
        let owners = RAW_SPECS
            .into_iter()
            .flat_map(|spec| spec.owners.iter().copied())
            .collect::<BTreeSet<_>>();
        ensure!(
            self.ledger
                .owners
                .keys()
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == owners,
            "leaf consumer owner set changed"
        );
        for (name, owner) in &self.ledger.owners {
            let registered = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("unregistered leaf owner {name}"))?;
            // The ledger records the original migration review. It is not a
            // mutable copy of today's prerequisite registry.
            let historical_requires = if name == "client_manager" {
                &["sfml_execution_side", "client_program_consent"][..]
            } else {
                current_source_proof_requirements(name)?
            };
            ensure!(
                owner
                    .requires
                    .iter()
                    .map(String::as_str)
                    .collect::<Vec<_>>()
                    == historical_requires,
                "frozen leaf owner prerequisite evidence changed: {name}"
            );
            ensure!(
                owner.support == registered.supported_targets
                    && registered
                        .requires
                        .iter()
                        .map(String::as_str)
                        .collect::<Vec<_>>()
                        == current_source_proof_requirements(name)?,
                "current leaf owner support/prerequisites changed: {name}"
            );
        }
        for file in &self.ledger.files {
            self.validate_file(file, &contexts)?;
        }
        Ok(())
    }

    fn validate_file(
        &self,
        file: &AuthoredFile,
        contexts: &BTreeMap<String, String>,
    ) -> Result<()> {
        let spec = raw_spec(&file.intended_core_path)?;
        let source = self
            .sources
            .get(spec.path)
            .ok_or_else(|| eyre::eyre!("missing authored leaf source"))?;
        validate_raw(spec.path, spec.blob, source)?;
        let raw = self.blob(spec.blob)?;
        validate_raw(spec.path, spec.blob, raw)?;
        ensure!(
            file.raw_git_blob == spec.blob
                && file.raw_sha256 == spec.digest
                && file.stage_sha256 == spec.digest
                && file.raw_bytes == spec.bytes
                && file.stage_bytes == spec.bytes,
            "authored raw source golden changed; common edits require deliberate review"
        );
        let expected = expected_predicate(spec);
        ensure!(
            file.predicate == expected,
            "reviewed per-file functional consumer predicate changed"
        );
        let rule = vec![InputVariant {
            input: spec.path.to_owned(),
            when: expected,
            template: true,
        }];
        ensure!(
            self.shared.metadata.source_rules.get(spec.path) == Some(&rule),
            "actual core rule omits/expands/reroutes the reviewed raw leaf"
        );
        ensure!(
            file.membership.keys().eq(contexts.keys()),
            "incomplete explicit twenty-context membership"
        );
        for (context, blob) in &file.membership {
            let expected = witnessed_present(context).then_some(spec.blob);
            ensure!(
                blob.as_deref() == expected,
                "frozen leaf membership assignment changed: {context}"
            );
        }
        Ok(())
    }

    fn blob(&self, oid: &str) -> Result<&[u8]> {
        self.blobs
            .get(oid)
            .map(Vec::as_slice)
            .ok_or_else(|| eyre::eyre!("missing frozen raw leaf blob"))
    }

    fn source_context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        let enabled = feature_closure(&self.shared, requested)?;
        let names = enabled.iter().map(String::as_str).collect::<Vec<_>>();
        self.shared.context(target, &names)
    }

    fn render_selected(&self, context: &ProjectionContext) -> Result<BTreeMap<String, String>> {
        let selected = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let mut bodies = BTreeMap::new();
        for spec in RAW_SPECS {
            let should_select = predicate_matches(spec, context);
            match selected.inputs.get(spec.path) {
                Some(input) => {
                    ensure!(
                        should_select && input.input == spec.path && input.template,
                        "unexpected leaf selection or historical routing"
                    );
                    let bytes = self
                        .sources
                        .get(spec.path)
                        .ok_or_else(|| eyre::eyre!("missing raw core input"))?;
                    let rendered = render_java_source(std::str::from_utf8(bytes)?, context)?;
                    ensure!(
                        rendered.as_bytes() == self.blob(spec.blob)?,
                        "real Liquid renderer changed raw leaf bytes"
                    );
                    bodies.insert(spec.path.to_owned(), rendered);
                }
                None => ensure!(
                    !should_select && selected.omitted_paths.contains(spec.path),
                    "omitted leaf must have explicit inactive functional membership"
                ),
            }
        }
        // Membership rules outside this bounded cohort are also selected even
        // when the fixture inventory contains only these leaves. They are not
        // read here: this is an exact 25-file source proof, not a complete build.
        ensure!(
            bodies.len()
                == RAW_SPECS
                    .iter()
                    .filter(|spec| selected.inputs.contains_key(spec.path))
                    .count(),
            "unexpected leaf-cohort selection"
        );
        Ok(bodies)
    }
}

fn inventory() -> BTreeSet<String> {
    RAW_SPECS
        .into_iter()
        .map(|spec| spec.path.to_owned())
        .collect()
}

fn raw_spec(path: &str) -> Result<RawSpec> {
    RAW_SPECS
        .into_iter()
        .find(|spec| spec.path == path)
        .ok_or_else(|| eyre::eyre!("path outside exact leaf source cohort"))
}

fn expected_predicate(spec: RawSpec) -> InputPredicate {
    InputPredicate {
        targets: TARGETS.into_iter().map(str::to_owned).collect(),
        any_features: spec.owners.iter().map(|name| (*name).to_owned()).collect(),
        ..InputPredicate::default()
    }
}

fn predicate_matches(spec: RawSpec, context: &ProjectionContext) -> bool {
    TARGETS.contains(&context.minecraft_version.as_str())
        && spec
            .owners
            .iter()
            .any(|owner| context.features.get(*owner) == Some(&true))
}

fn witnessed_present(context: &str) -> bool {
    matches!(context, "dev/1.19.2" | "dev/1.19.4")
}

fn validate_raw(path: &str, oid: &str, bytes: &[u8]) -> Result<()> {
    let spec = raw_spec(path)?;
    ensure!(
        spec.blob == oid && bytes.len() as u64 == spec.bytes && sha256(bytes) == spec.digest,
        "unreviewed raw leaf object, bytes or path"
    );
    ensure!(
        bytes.is_ascii()
            && !bytes.contains(&b'\r')
            && bytes.last() == Some(&b'\n')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "leaf import must preserve raw ASCII LF/final-LF bytes"
    );
    let source = std::str::from_utf8(bytes)?;
    ensure!(
        !source.contains("{%") && !source.contains("{{"),
        "raw leaf has unexpected templating surface"
    );
    Ok(())
}

// These test-local contexts explicitly enumerate the reviewed current graph.
// New registry prerequisites are errors, not automatically adopted fixture inputs.
fn current_source_proof_requirements(name: &str) -> Result<&'static [&'static str]> {
    Ok(match name {
        "client_actions"
        | "packet_values"
        | "sfml_execution_side"
        | "disk_readonly_access"
        | "runtime_resource_cleanup"
        | "manager_operator_queries" => &[],
        "client_program_consent" => &["sfml_execution_side"],
        "client_manager" => &[
            "sfml_execution_side",
            "client_program_consent",
            "disk_readonly_access",
        ],
        "packet_computation" => &["packet_values", "runtime_resource_cleanup"],
        "packet_transport_private" => &["packet_computation"],
        "client_inbox" => &["packet_transport_private"],
        "image_resources" => &["packet_values"],
        "touch_display" => &["packet_values", "image_resources"],
        "client_frame_language" => &[
            "client_manager",
            "sfml_execution_side",
            "client_program_consent",
        ],
        "client_program_actions" => &[
            "client_actions",
            "packet_values",
            "sfml_execution_side",
            "client_manager",
            "client_program_consent",
            "packet_computation",
        ],
        "client_program_signing" => &["client_frame_language", "client_program_actions"],
        "multiplayer_packets" => &[
            "client_inbox",
            "client_frame_language",
            "client_program_actions",
        ],
        _ => eyre::bail!("unreviewed dependency in bounded leaf source proof: {name}"),
    })
}

fn feature_closure(shared: &CoreTestFixture, requested: &[&str]) -> Result<BTreeSet<String>> {
    ensure!(
        shared.features.0.len() <= 256,
        "bounded source-proof registry"
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
            .ok_or_else(|| eyre::eyre!("unknown source-proof feature {name}"))?;
        let required = current_source_proof_requirements(&name)?;
        ensure!(
            definition
                .requires
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>()
                == required,
            "current source-proof prerequisite contract changed: {name}"
        );
        for required in required {
            if enabled.insert((*required).to_owned()) {
                pending.push((*required).to_owned());
            }
        }
    }
    Ok(enabled)
}

fn has_class(bodies: &BTreeMap<String, String>, class: &str) -> bool {
    bodies
        .keys()
        .any(|path| path.ends_with(&format!("/{class}.java")))
}

fn class_name(path: &str) -> Result<&str> {
    path.rsplit('/')
        .next()
        .and_then(|name| name.strip_suffix(".java"))
        .ok_or_else(|| eyre::eyre!("invalid pinned Java class path"))
}

/// Confirm only this cohort's internal type closure, not whole-project Java closure.
fn assert_cohort_type_closure(bodies: &BTreeMap<String, String>) -> Result<()> {
    for (path, body) in bodies {
        let own = class_name(path)?;
        let tokens = body
            .split(|character: char| !character.is_ascii_alphanumeric() && character != '_')
            .collect::<BTreeSet<_>>();
        for spec in RAW_SPECS {
            let dependency = class_name(spec.path)?;
            if dependency != own && tokens.contains(dependency) {
                ensure!(
                    has_class(bodies, dependency),
                    "selected leaf omits its cohort type dependency: {own} -> {dependency}"
                );
            }
        }
    }
    Ok(())
}

/// Offline, exact, bounded tree lookup used only to verify migration witnesses.
fn read_frozen_tree(fixture: &Fixture, commit: &str) -> Result<BTreeMap<String, String>> {
    ensure!(
        commit.len() == 40
            && commit
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "tree witness must be an exact lowercase commit"
    );
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
            "-r",
            "-z",
            "--full-tree",
            commit,
            "--",
        ]);
    for spec in RAW_SPECS {
        command.arg(format!("platform/minecraft/{}", spec.path));
    }
    let mut child = command
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .wrap_err("cannot start offline exact-tree witness reader")?;
    let Some(output) = child.stdout.take() else {
        let _ = child.kill();
        let _ = child.wait();
        eyre::bail!("missing configured tree-witness output");
    };
    let mut bytes = Vec::new();
    if let Err(error) = output.take(MAX_TREE_BYTES + 1).read_to_end(&mut bytes) {
        let _ = child.kill();
        let _ = child.wait();
        return Err(error.into());
    }
    if bytes.len() as u64 > MAX_TREE_BYTES {
        let _ = child.kill();
        let _ = child.wait();
        eyre::bail!("tree-witness output exceeded its exact cohort bound");
    }
    ensure!(child.wait()?.success(), "offline exact-tree lookup failed");
    let mut rows = BTreeMap::new();
    for raw in bytes.split(|byte| *byte == 0).filter(|row| !row.is_empty()) {
        let row = std::str::from_utf8(raw)?;
        let (header, path) = row
            .split_once('\t')
            .ok_or_else(|| eyre::eyre!("invalid frozen tree row"))?;
        let fields = header.split(' ').collect::<Vec<_>>();
        ensure!(
            fields.len() == 3 && fields[0] == "100644" && fields[1] == "blob",
            "frozen leaf tree entry is not an ordinary source blob"
        );
        let path = path
            .strip_prefix("platform/minecraft/")
            .ok_or_else(|| eyre::eyre!("tree witness escaped Minecraft source root"))?;
        let spec = raw_spec(path)?;
        ensure!(
            fields[2] == spec.blob,
            "tree contains a different leaf witness"
        );
        ensure!(
            rows.insert(path.to_owned(), fields[2].to_owned()).is_none(),
            "duplicate tree row"
        );
    }
    Ok(rows)
}

#[test]
fn all_five_hundred_leaf_membership_cells_reconstruct_through_real_core() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    let mut present = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid pinned context"))?;
        let requested = if witnessed_present(name) {
            fixture
                .ledger
                .owners
                .iter()
                .filter_map(|(name, owner)| {
                    owner
                        .support
                        .iter()
                        .any(|id| id == target)
                        .then_some(name.as_str())
                })
                .collect::<Vec<_>>()
        } else {
            Vec::new()
        };
        let context = fixture.source_context(target, &requested)?;
        let bodies = fixture.render_selected(&context)?;
        for spec in RAW_SPECS {
            let expected = witnessed_present(name);
            ensure!(
                bodies.contains_key(spec.path) == expected,
                "frozen membership did not reconstruct"
            );
            if let Some(body) = bodies.get(spec.path) {
                assert_eq!(
                    body.as_bytes(),
                    fixture.blob(spec.blob)?,
                    "{name}: {}",
                    spec.path
                );
                present += 1;
            }
            cells += 1;
        }
    }
    assert_eq!((cells, present), (500, 50));
    Ok(())
}

#[test]
fn exact_twenty_git_trees_independently_verify_present_and_absent_leaf_witnesses() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    let mut present = 0;
    for (name, commit) in PINNED_CONTEXTS {
        let rows = read_frozen_tree(&fixture, commit)?;
        for spec in RAW_SPECS {
            let expected = witnessed_present(name).then_some(spec.blob);
            ensure!(
                rows.get(spec.path).map(String::as_str) == expected,
                "exact tree membership differs"
            );
            present += usize::from(expected.is_some());
            cells += 1;
        }
    }
    assert_eq!((cells, present), (500, 50));
    Ok(())
}

#[test]
fn independent_functional_owners_use_actual_prerequisites_and_internal_type_closure() -> Result<()>
{
    let fixture = Fixture::load()?;
    let mut contexts = 0;
    for (target, _) in SUPPORTED_TARGETS {
        let baseline = fixture.source_context(target, &[])?;
        assert!(fixture.render_selected(&baseline)?.is_empty());
        contexts += 1;
        for (owner, contract) in &fixture.ledger.owners {
            if contract.support.iter().any(|id| id == target) {
                let context = fixture.source_context(target, &[owner])?;
                let bodies = fixture.render_selected(&context)?;
                assert_cohort_type_closure(&bodies)?;
                ensure!(!bodies.is_empty(), "functional leaf owner selects nothing");
                contexts += 1;
            } else {
                assert!(
                    fixture.source_context(target, &[owner]).is_err(),
                    "unwitnessed owner target was accepted"
                );
            }
        }
    }
    assert_eq!(contexts, 29);
    Ok(())
}

fn assert_consent_image_boundaries(fixture: &Fixture, target: &str) -> Result<()> {
    let consent = fixture.source_context(target, &["client_program_consent"])?;
    assert_eq!(
        consent
            .features
            .iter()
            .filter_map(|(name, enabled)| enabled.then_some(name.as_str()))
            .collect::<BTreeSet<_>>(),
        BTreeSet::from(["client_program_consent", "sfml_execution_side"])
    );
    let consent_bodies = fixture.render_selected(&consent)?;
    assert_eq!(consent_bodies.len(), 4);
    assert!(!has_class(&consent_bodies, "ProgramSignatureDescriptor"));

    let image = fixture.source_context(target, &["image_resources"])?;
    assert_eq!(
        image
            .features
            .iter()
            .filter_map(|(name, enabled)| enabled.then_some(name.as_str()))
            .collect::<BTreeSet<_>>(),
        BTreeSet::from(["image_resources", "packet_values"])
    );
    assert_eq!(fixture.render_selected(&image)?.len(), 4);

    let touch = fixture.source_context(target, &["touch_display"])?;
    assert_eq!(
        touch
            .features
            .iter()
            .filter_map(|(name, enabled)| enabled.then_some(name.as_str()))
            .collect::<BTreeSet<_>>(),
        BTreeSet::from(["touch_display", "image_resources", "packet_values"])
    );
    let touch_bodies = fixture.render_selected(&touch)?;
    assert_eq!(touch_bodies.len(), 6);
    assert!(has_class(&touch_bodies, "SFMPacketInventoryAddress"));
    assert!(!has_class(&touch_bodies, "SFMPacketEffectGate"));
    assert!(!has_class(&touch_bodies, "SFMPacketValueEnvelope"));

    Ok(())
}

fn assert_basic_manager_boundaries(fixture: &Fixture, target: &str) -> Result<()> {
    let manager = fixture.source_context(target, &["client_manager"])?;
    let manager_bodies = fixture.render_selected(&manager)?;
    assert_eq!(manager_bodies.len(), 15);
    for absent in [
        "client_program_signing",
        "packet_transport_private",
        "multiplayer_packets",
        "client_inbox",
    ] {
        assert_eq!(manager.features.get(absent), Some(&false));
    }
    for model in [
        "ClientManagerSigningState",
        "ClientManagerSigningSession",
        "ClientManagerSigningAcknowledgement",
        "ClientManagerSigningMetadata",
        "ClientManagerSigningCodec",
        "ProgramAttestation",
        "ProgramAttestationCodec",
        "ProgramAttestationHistory",
    ] {
        assert!(
            has_class(&manager_bodies, model),
            "stored metadata support was removed"
        );
    }
    assert!(!has_class(&manager_bodies, "ClientManagerSigningAdmission"));
    assert!(!has_class(&manager_bodies, "SFMPacketEffectGate"));

    Ok(())
}

fn assert_packet_helper_boundaries(fixture: &Fixture, target: &str) -> Result<()> {
    let actions = fixture.source_context(target, &["client_program_actions"])?;
    let action_bodies = fixture.render_selected(&actions)?;
    assert!(has_class(&action_bodies, "SFMBoundedEffectBudget"));
    assert!(!has_class(&action_bodies, "SFMPacketValueEnvelope"));
    assert_eq!(
        actions.features.get("packet_transport_private"),
        Some(&false)
    );

    let private = fixture.source_context(target, &["packet_transport_private"])?;
    let private_bodies = fixture.render_selected(&private)?;
    assert_eq!(private_bodies.len(), 4);
    for helper in [
        "SFMBoundedEffectBudget",
        "SFMPacketEffectGate",
        "SFMPacketInventoryAddress",
        "SFMPacketValueEnvelope",
    ] {
        assert!(has_class(&private_bodies, helper));
    }
    assert_eq!(private.features.get("client_program_signing"), Some(&false));
    assert_eq!(private.features.get("client_manager"), Some(&false));
    Ok(())
}

#[test]
fn consent_images_basic_manager_and_helpers_do_not_enable_unrelated_signing_or_transport()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        assert_consent_image_boundaries(&fixture, target)?;
        assert_basic_manager_boundaries(&fixture, target)?;
        assert_packet_helper_boundaries(&fixture, target)?;
    }
    let query = fixture.source_context("1.19.2", &["manager_operator_queries"])?;
    let query_bodies = fixture.render_selected(&query)?;
    assert_eq!(query_bodies.len(), 1);
    assert!(has_class(&query_bodies, "ProgramSignatureDescriptor"));
    for unrelated in [
        "client_manager",
        "client_program_consent",
        "client_program_signing",
        "packet_transport_private",
    ] {
        assert_eq!(query.features.get(unrelated), Some(&false));
    }
    Ok(())
}

#[test]
fn environment_and_projection_names_do_not_create_leaf_membership() -> Result<()> {
    let fixture = Fixture::load()?;
    for requested in [
        &[][..],
        &[
            "client_manager",
            "image_resources",
            "packet_transport_private",
        ][..],
    ] {
        let original = fixture.source_context("1.19.2", requested)?;
        let mut renamed = original.clone();
        renamed.environment = "dev".to_owned();
        renamed.preset = "unreviewed-label-does-not-enable-anything".to_owned();
        renamed.projection_key = "different/name".to_owned();
        let original_selection =
            select_core_inputs(&fixture.shared.metadata, &original, &inventory())?;
        let renamed_selection =
            select_core_inputs(&fixture.shared.metadata, &renamed, &inventory())?;
        assert_eq!(original_selection.inputs, renamed_selection.inputs);
        assert_eq!(
            original_selection.omitted_paths,
            renamed_selection.omitted_paths
        );
        assert_eq!(
            fixture.render_selected(&original)?,
            fixture.render_selected(&renamed)?
        );
    }
    Ok(())
}

#[test]
fn a_common_leaf_edit_reaches_both_witnessed_targets_without_historical_source_adoption()
-> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::Builder::new()
        .prefix("sfm-core-leaf-common-edit-")
        .tempdir()?;
    let core = temp.path().join(CORE_ROOT);
    let comment = "// Isolated shared-body proof; not a persisted core edit.\n";
    let mut proofs = 0;
    for spec in RAW_SPECS {
        let source = std::str::from_utf8(
            fixture
                .sources
                .get(spec.path)
                .ok_or_else(|| eyre::eyre!("missing core source"))?,
        )?;
        let (package, rest) = source
            .split_once('\n')
            .ok_or_else(|| eyre::eyre!("missing package line"))?;
        ensure!(
            package.starts_with("package ") && package.ends_with(';'),
            "shared package anchor changed"
        );
        let edited = format!("{package}\n{comment}{rest}");
        let path = core.join(spec.path);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(&path, edited.as_bytes())?;
        let bytes = read_bounded(&path, MAX_SOURCE_BYTES)?;
        for target in TARGETS {
            let context = fixture.source_context(target, &[spec.owners[0]])?;
            let selected = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
            ensure!(
                selected
                    .inputs
                    .get(spec.path)
                    .is_some_and(|input| input.input == spec.path),
                "common fixture edit was omitted or routed elsewhere"
            );
            let before = render_java_source(source, &context)?;
            let after = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            assert_eq!(after, edited);
            assert_eq!(after.replacen(comment, "", 1), before);
            proofs += 1;
        }
    }
    assert_eq!(proofs, 50);
    Ok(())
}

#[test]
fn raw_leaf_goldens_reject_whitespace_normalization_token_edits_and_wrong_paths() -> Result<()> {
    let fixture = Fixture::load()?;
    for (index, spec) in RAW_SPECS.into_iter().enumerate() {
        let raw = fixture.blob(spec.blob)?;
        validate_raw(spec.path, spec.blob, raw)?;
        let crlf = std::str::from_utf8(raw)?.replace('\n', "\r\n").into_bytes();
        let mut token_edit = raw.to_vec();
        token_edit[0] = b'G';
        for changed in [
            crlf,
            token_edit,
            [&[0xef, 0xbb, 0xbf][..], raw].concat(),
            raw[..raw.len() - 1].to_vec(),
            [raw, b" "].concat(),
        ] {
            assert!(validate_raw(spec.path, spec.blob, &changed).is_err());
        }
        let next = RAW_SPECS[(index + 1) % RAW_SPECS.len()];
        assert!(validate_raw(next.path, spec.blob, raw).is_err());
    }
    Ok(())
}
