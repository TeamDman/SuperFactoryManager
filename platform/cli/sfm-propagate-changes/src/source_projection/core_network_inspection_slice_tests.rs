//! Frozen source-reconstruction tests for the two inspection request packets.
//!
//! Core selection and Liquid rendering are real. Exact bounded Git objects are
//! test-only migration witnesses, never production rendering inputs. They use
//! raw LF bytes without normalization.
//!
//! These isolated source proofs use only actual packet-computation prerequisites.
//! They do not make signing/multiplayer prerequisites for a parser/helper proof,
//! collect a complete project, prove Java closure or assert codec interoperability.
//! Deliberate future common-core edits require reviewing these migration goldens.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::select_core_inputs;
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

const LEDGER: &str = "docs/tasks/sfm-core-network-inspection-slice.json";
const INPUT: &str =
    "src/main/java/ca/teamdman/sfm/common/net/ServerboundInputInspectionRequestPacket.java";
const OUTPUT: &str =
    "src/main/java/ca/teamdman/sfm/common/net/ServerboundOutputInspectionRequestPacket.java";
const PATHS: [&str; 2] = [INPUT, OUTPUT];
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;
const MAX_SOURCE_BYTES: u64 = 128 * 1024;
const PACKET_CLOSURE: [&str; 3] = [
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
];
const PINNED_CORE: [(&str, &str, u64); 2] = [
    (
        INPUT,
        "sha256:3885afffbf2be873049613240f4e7d5a204cce6cbf6e83d336419d57800a47db",
        6704,
    ),
    (
        OUTPUT,
        "sha256:4c4ea04889d8b162759a8f577bceca8bab3c068ba3d1b88ebd11425f57a32820",
        21721,
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

const RAW_SPECS: [RawSpec; 7] = [
    RawSpec {
        path: INPUT,
        oid: "7833a800b2662356c8a6e7874954c77e4a8dc79b",
        digest: "sha256:847a3ab4cf4753a1d78d64f1f41dd02b22fde0d39a517f69f532a3f5a48c7fea",
        bytes: 4695,
    },
    RawSpec {
        path: INPUT,
        oid: "919eb3246d00a9023921deb1a1dbecaddbe1e37e",
        digest: "sha256:9fc4b7736d2e233d464f0ec3c46c447a6f96e2ac34645b8ff7b66c6fdf8e236d",
        bytes: 5103,
    },
    RawSpec {
        path: INPUT,
        oid: "c9f991b5d48e449ecb5c2c85898e8b1888d235fc",
        digest: "sha256:636e4b1c251b3102d93da65a40fd528f77e6b23256d9d8f264177a631417b67a",
        bytes: 4672,
    },
    RawSpec {
        path: OUTPUT,
        oid: "2fe735f2c763028972e0c79a953734f567dbe906",
        digest: "sha256:a5d3d9530c169a2be3b6eaa65cf5f9567c4e27dabd75baf6478d2b9ab5138d25",
        bytes: 18823,
    },
    RawSpec {
        path: OUTPUT,
        oid: "ae3e6b3b3f04d5fcab4a0556ddf42d39d9c543e9",
        digest: "sha256:ac13f001347d1c357f12b31b7a75c656d43a198959dc712b0c89fa8ccf44a57a",
        bytes: 19038,
    },
    RawSpec {
        path: OUTPUT,
        oid: "b282309d0cbe4c78ff99797c15f9f0a0b4c0f74c",
        digest: "sha256:12756cd563ae3897fd3482d5077805d3756feb2b9573e0758ceb34f3e4d13354",
        bytes: 18846,
    },
    RawSpec {
        path: OUTPUT,
        oid: "cdfd40a04c7aebfc7b2f374adfdf9aadae018263",
        digest: "sha256:3e2ab4c9e20ecae4a0323b373b45a8a94b27d28513ebcdddc319f1ad0591a43e",
        bytes: 18816,
    },
];

// Only this typed golden subset is executable; other review prose is ignored.
#[derive(Debug, Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    files: Vec<AuthoredFile>,
    owners: BTreeMap<String, OwnerContract>,
    normalization: NormalizationContract,
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
struct OwnerContract {
    support: Vec<String>,
    requires: Vec<String>,
}

#[derive(Debug, Facet)]
struct NormalizationContract {
    policy: String,
    approval_needed: bool,
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
            .wrap_err("cannot parse exact inspection-packet source evidence")?;
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
            self.ledger.schema == "sfm:core-network-inspection-slice@1",
            "inspection source ledger schema changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && !self.ledger.normalization.approval_needed,
            "raw LF scope must not normalize"
        );
        let contexts = PINNED_CONTEXTS
            .into_iter()
            .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == contexts,
            "frozen twenty contexts changed"
        );
        let paths = self
            .ledger
            .files
            .iter()
            .map(|file| file.intended_core_path.as_str())
            .collect::<BTreeSet<_>>();
        ensure!(
            self.ledger.files.len() == 2 && paths == BTreeSet::from(PATHS),
            "two-packet scope changed"
        );
        ensure!(
            self.ledger.owners.len() == 1,
            "inspection owner scope widened"
        );
        let owner = self
            .ledger
            .owners
            .get("packet_computation")
            .ok_or_else(|| eyre::eyre!("missing packet-computation owner"))?;
        let registered = self
            .shared
            .features
            .0
            .get("packet_computation")
            .ok_or_else(|| eyre::eyre!("packet-computation owner is not registered"))?;
        ensure!(
            owner.support == ["1.19.2", "1.19.4"]
                && owner.support == registered.supported_targets
                && owner.requires == ["packet_values", "runtime_resource_cleanup"]
                && owner.requires == registered.requires,
            "actual packet prerequisites/support changed"
        );
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
        let path = file.intended_core_path.as_str();
        let (_, digest, bytes) = PINNED_CORE
            .into_iter()
            .find(|(p, _, _)| *p == path)
            .ok_or_else(|| eyre::eyre!("path outside the exact inspection-packet cohort"))?;
        let source = self
            .sources
            .get(path)
            .ok_or_else(|| eyre::eyre!("missing authored core"))?;
        ensure!(
            file.stage_sha256 == digest
                && file.stage_bytes == bytes
                && sha256(source) == digest
                && source.len() as u64 == bytes,
            "authored source golden changed; common edits require deliberate review"
        );
        let mut seen = BTreeSet::new();
        for witness in &file.witnesses {
            ensure!(
                contexts.contains_key(&witness.context) && seen.insert(&witness.context),
                "unknown or repeated witness context"
            );
            ensure!(
                witness.git_blob == expected_blob(path, &witness.context)?,
                "raw inspection packet context assignment changed"
            );
            let raw = self.blob(&witness.git_blob)?;
            validate_raw(path, &witness.git_blob, raw)?;
            ensure!(
                witness.raw_sha256 == sha256(raw)
                    && witness.expected_sha256 == sha256(raw)
                    && witness.raw_bytes == raw.len() as u64
                    && witness.expected_bytes == raw.len() as u64
                    && witness.normalization == "none",
                "raw witness contract changed"
            );
        }
        ensure!(
            seen.into_iter().eq(contexts.keys()),
            "incomplete twenty-context coverage"
        );
        Ok(())
    }

    fn blob(&self, oid: &str) -> Result<&[u8]> {
        self.blobs
            .get(oid)
            .map(Vec::as_slice)
            .ok_or_else(|| eyre::eyre!("missing frozen raw body"))
    }

    /// Isolated source context: use real registry prerequisites, not a network bundle.
    fn source_context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        let enabled = feature_closure(&self.shared, requested)?;
        let names = enabled.iter().map(String::as_str).collect::<Vec<_>>();
        self.shared.context(target, &names)
    }

    fn render_sources(&self, context: &ProjectionContext) -> Result<BTreeMap<String, String>> {
        let inventory = PATHS.into_iter().map(str::to_owned).collect();
        let selected = select_core_inputs(&self.shared.metadata, context, &inventory)?;
        PATHS
            .into_iter()
            .map(|path| {
                ensure!(
                    selected
                        .inputs
                        .get(path)
                        .is_some_and(|input| input.input == path),
                    "inspection packet omitted or routed away from its shared core path"
                );
                let source = std::str::from_utf8(&self.sources[path])?;
                Ok((path.to_owned(), render_java_source(source, context)?))
            })
            .collect()
    }
}

fn feature_closure(shared: &CoreTestFixture, requested: &[&str]) -> Result<BTreeSet<String>> {
    ensure!(
        shared.features.0.len() <= 256,
        "source-proof feature registry exceeds bound"
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
        for required in &definition.requires {
            if enabled.insert(required.clone()) {
                pending.push(required.clone());
            }
        }
    }
    Ok(enabled)
}

fn expected_blob(path: &str, context: &str) -> Result<&'static str> {
    let (kind, target) = context
        .split_once('/')
        .ok_or_else(|| eyre::eyre!("invalid source context"))?;
    ensure!(
        ["dev", "release"].contains(&kind)
            && SUPPORTED_TARGETS.into_iter().any(|(id, _)| id == target),
        "unreviewed source context"
    );
    let packet = kind == "dev" && matches!(target, "1.19.2" | "1.19.4");
    Ok(match (path, packet, target) {
        (INPUT, true, _) => "919eb3246d00a9023921deb1a1dbecaddbe1e37e",
        (OUTPUT, true, _) => "ae3e6b3b3f04d5fcab4a0556ddf42d39d9c543e9",
        (INPUT, false, "1.21.0" | "1.21.1" | "26.1.2") => {
            "7833a800b2662356c8a6e7874954c77e4a8dc79b"
        }
        (OUTPUT, false, "1.21.0" | "1.21.1") => "b282309d0cbe4c78ff99797c15f9f0a0b4c0f74c",
        (OUTPUT, false, "26.1.2") => "cdfd40a04c7aebfc7b2f374adfdf9aadae018263",
        (INPUT, false, _) => "c9f991b5d48e449ecb5c2c85898e8b1888d235fc",
        (OUTPUT, false, _) => "2fe735f2c763028972e0c79a953734f567dbe906",
        _ => eyre::bail!("path outside inspection packet source cohort"),
    })
}

fn validate_raw(path: &str, oid: &str, bytes: &[u8]) -> Result<()> {
    let spec = RAW_SPECS
        .into_iter()
        .find(|spec| spec.path == path && spec.oid == oid)
        .ok_or_else(|| eyre::eyre!("unreviewed raw inspection packet object"))?;
    ensure!(
        bytes.len() as u64 == spec.bytes && sha256(bytes) == spec.digest,
        "raw source changed; no implicit normalized adoption"
    );
    ensure!(
        bytes.ends_with(b"\n")
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw LF/no-BOM/final-LF contract changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

fn assert_source_contract(
    target: &str,
    packet: bool,
    bodies: &BTreeMap<String, String>,
) -> Result<()> {
    let input = &bodies[INPUT];
    let output = &bodies[OUTPUT];
    for marker in [
        "import ca.teamdman.sfm.common.program.WorldProgramInputSource;",
        "WorldProgramInputSource inputSource = new WorldProgramInputSource(inputStatement);",
        "inputSource.gatherSlots(",
        "} finally {",
        "inputSource.free();",
    ] {
        ensure!(
            input.contains(marker) == packet,
            "input source-owner marker leaked/omitted"
        );
    }
    ensure!(
        input.matches("inputSource.free();").count() == usize::from(packet),
        "source-owned cache must be freed exactly once when packet inputs are enabled"
    );
    ensure!(
        input.contains("inputStatement.gatherSlots(") != packet,
        "direct released input gathering branch changed"
    );
    ensure!(
        output.contains("inputSource.inputStatement().ifPresent(inputStatement ->") == packet
            && output.contains(".forEach(inputStatement -> inputStatement.gatherSlots(") != packet,
        "optional originating input statement branch changed"
    );
    let modern = matches!(target, "1.21.0" | "1.21.1" | "26.1.2");
    for (path, body) in [(INPUT, input), (OUTPUT, output)] {
        ensure!(
            body.contains("import net.minecraft.network.RegistryFriendlyByteBuf;") == modern
                && body.contains("import net.minecraft.network.FriendlyByteBuf;") != modern,
            "buffer API import changed"
        );
        let class = if path == INPUT {
            "ServerboundInputInspectionRequestPacket"
        } else {
            "ServerboundOutputInspectionRequestPacket"
        };
        let (buffer, suffix) = if modern {
            ("RegistryFriendlyByteBuf", "\n")
        } else {
            ("FriendlyByteBuf", "\n\n")
        };
        ensure!(
            body.contains(&format!(
                "public {class} decode({buffer} friendlyByteBuf) {{{suffix}            return"
            )),
            "decode API/historical blank-line distinction changed"
        );
        ensure!(!body.contains("{%"), "unrendered source directive");
    }
    let identifier = target == "26.1.2";
    ensure!(
        output.contains("import net.minecraft.resources.Identifier;") == identifier
            && output.contains("import net.minecraft.resources.ResourceLocation;") != identifier
            && output.contains(".anyMatchIdentifier(") == identifier
            && output.contains("exclusion.matchesIdentifier(") == identifier
            && output.contains("Identifier stackId =") == identifier,
        "26.1.2 identifier API changed"
    );
    Ok(())
}

#[test]
fn forty_frozen_inspection_packet_witnesses_reconstruct_through_real_core() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut compared = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid pinned context"))?;
        let packet = matches!(name, "dev/1.19.2" | "dev/1.19.4");
        let requested: &[&str] = if packet { &["packet_computation"] } else { &[] };
        let context = fixture.source_context(target, requested)?;
        let bodies = fixture.render_sources(&context)?;
        assert_source_contract(target, packet, &bodies)?;
        for path in PATHS {
            let oid = expected_blob(path, name)?;
            let raw = fixture.blob(oid)?;
            validate_raw(path, oid, raw)?;
            assert_eq!(bodies[path].as_bytes(), raw, "{name}: {path}");
            compared += 1;
        }
    }
    assert_eq!(compared, 40);
    Ok(())
}

#[test]
fn fourteen_source_contexts_preserve_cleanup_decode_and_version_api_boundaries() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut checked = 0;
    for (target, _) in SUPPORTED_TARGETS {
        let mut cases: Vec<&[&str]> = vec![&[]];
        if matches!(target, "1.19.2" | "1.19.4") {
            cases.extend([
                &["runtime_resource_cleanup"][..],
                &["packet_computation"][..],
            ]);
        }
        for requested in cases {
            let context = fixture.source_context(target, requested)?;
            let packet = context
                .features
                .get("packet_computation")
                .copied()
                .ok_or_else(|| eyre::eyre!("packet feature must be explicitly resolved"))?;
            let bodies = fixture.render_sources(&context)?;
            assert_source_contract(target, packet, &bodies)?;
            checked += 1;
        }
    }
    assert_eq!(checked, 14);
    Ok(())
}

#[test]
fn packet_only_source_proofs_use_real_prerequisites_without_a_transport_bundle() -> Result<()> {
    let fixture = Fixture::load()?;
    let expected = PACKET_CLOSURE
        .into_iter()
        .map(str::to_owned)
        .collect::<BTreeSet<_>>();
    for target in ["1.19.2", "1.19.4"] {
        let context = fixture.source_context(target, &["packet_computation"])?;
        let enabled = context
            .features
            .iter()
            .filter_map(|(name, active)| active.then_some(name.clone()))
            .collect::<BTreeSet<_>>();
        assert_eq!(
            enabled, expected,
            "no signing/multiplayer/parser-prerequisite fabrication"
        );
        fixture.render_sources(&context)?;
        let cleanup_only = fixture.source_context(target, &["runtime_resource_cleanup"])?;
        let cleanup_enabled = cleanup_only
            .features
            .iter()
            .filter_map(|(name, active)| active.then_some(name.clone()))
            .collect::<BTreeSet<_>>();
        assert_eq!(
            cleanup_enabled,
            BTreeSet::from(["runtime_resource_cleanup".to_owned()])
        );
        assert_source_contract(target, false, &fixture.render_sources(&cleanup_only)?)?;
    }
    Ok(())
}

#[test]
fn shared_inspection_body_edits_reach_three_version_apis_without_source_adoption() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::Builder::new()
        .prefix("sfm-core-inspection-source-edit-")
        .tempdir()?;
    let core = temp.path().join(CORE_ROOT);
    let anchor = ") implements SFMPacket {\n";
    let comment = "    // Isolated shared-body source proof, not a persisted core edit.\n";
    for path in PATHS {
        let source = std::str::from_utf8(&fixture.sources[path])?;
        ensure!(
            source.matches(anchor).count() == 1,
            "common packet record anchor changed"
        );
        let edited = source.replacen(anchor, &format!("{anchor}{comment}"), 1);
        let output = core.join(path);
        fs::create_dir_all(
            output
                .parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(&output, edited.as_bytes())?;
        let bytes = read_bounded(&output, MAX_SOURCE_BYTES)?;
        for (target, requested) in [
            ("1.19.2", &["packet_computation"][..]),
            ("1.20.4", &[][..]),
            ("26.1.2", &[][..]),
        ] {
            let context = fixture.source_context(target, requested)?;
            let selection = select_core_inputs(
                &fixture.shared.metadata,
                &context,
                &BTreeSet::from([path.to_owned()]),
            )?;
            ensure!(
                selection
                    .inputs
                    .get(path)
                    .is_some_and(|selected| selected.input == path),
                "shared fixture source was omitted/routed away"
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
fn raw_inspection_witnesses_reject_normalization_token_edits_and_wrong_paths() -> Result<()> {
    let fixture = Fixture::load()?;
    for spec in RAW_SPECS {
        let raw = fixture.blob(spec.oid)?;
        validate_raw(spec.path, spec.oid, raw)?;
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
            assert!(validate_raw(spec.path, spec.oid, &changed).is_err());
        }
        let other = if spec.path == INPUT { OUTPUT } else { INPUT };
        assert!(validate_raw(other, spec.oid, raw).is_err());
    }
    Ok(())
}
