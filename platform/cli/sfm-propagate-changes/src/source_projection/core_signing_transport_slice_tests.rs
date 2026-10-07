//! Four genuine signing wire objects/worker through real authored-core selection.
//!
//! Frozen Git objects are offline test witnesses, never source generation inputs.
//! This bounded slice does not install receivers, validate an accepted complete
//! wire layout, invoke Java, create a key/worker or perform signing/network effects.
//! Register only after parent promotion. Future edits require deliberate golden
//! review rather than silently adopting changed sources.
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

const LEDGER: &str = "docs/tasks/sfm-core-signing-transport-slice.json";
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
const ACTIONS: [&str; 9] = [
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
        "client_actions",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
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
        "client_manager",
        &["1.19.2", "1.19.4"],
        &[
            "sfml_execution_side",
            "client_program_consent",
            "disk_readonly_access",
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
        "client_program_consent",
        &["1.19.2", "1.19.4"],
        &["sfml_execution_side"],
    ),
    (
        "client_program_signing",
        &["1.19.2", "1.19.4"],
        &["client_frame_language", "client_program_actions"],
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
        "packet_computation",
        &["1.19.2", "1.19.4"],
        &["packet_values", "runtime_resource_cleanup"],
    ),
    ("packet_values", &["1.19.2", "1.19.4"], &[]),
    ("runtime_resource_cleanup", &["1.19.2", "1.19.4"], &[]),
    ("sfml_execution_side", &["1.19.2", "1.19.4"], &[]),
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
    anchor: &'static str,
}
const GOLDENS: [Golden; 4] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/net/ClientboundClientManagerSigningResponsePacket.java",
        oid: "c2389853349f4d1251ffae97de163e155d5241b5",
        bytes: 3653,
        digest: "sha256:9e3a56674cf9122152840975cb43705d593c9272ea39a8a5d9caf48eb08059c0",
        lf: 57,
        anchor: "public record ClientboundClientManagerSigningResponsePacket",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/net/ServerboundClientManagerSigningRequestPacket.java",
        oid: "7713432b7fa10fb5a42f92b7d9835cc4691783c8",
        bytes: 4448,
        digest: "sha256:eb0cfbd3eb23d5857b94579ed3f3338acb1d74b0579129e0fea12cb4dc1d1af7",
        lf: 73,
        anchor: "public record ServerboundClientManagerSigningRequestPacket",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/net/ServerboundClientManagerSignaturePacket.java",
        oid: "d55b155e8a021ac441b25933c9065665995f4ab0",
        bytes: 2749,
        digest: "sha256:9d2ebebdbd15f29aad78bb77912808165e764cb9ddea6b6a7dc863fe32d5c42b",
        lf: 45,
        anchor: "public record ServerboundClientManagerSignaturePacket",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/signing/ClientSigningUiWorker.java",
        oid: "5eed5867d93e62d8f4da76fe7b69d0135cdb47ca",
        bytes: 807,
        digest: "sha256:ac3bde67f6e94037b8db79171445558205dfcdf8054513a73b339ba8bd225685",
        lf: 18,
        anchor: "public final class ClientSigningUiWorker",
    },
];
const PROVIDERS: [(&str, usize, &str); 10] = [
    (
        "src/main/java/ca/teamdman/sfm/common/net/SFMPacket.java",
        68,
        "sha256:e4a31a3581999e9da9f37ad8a9187911c1c9f44f8d9ccb2a0d996401631adf81",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/net/SFMPacketDaddy.java",
        3787,
        "sha256:d6e14f758a30638d4dc152ee442998773610414523341f41524d527b451662be",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/net/SFMPacketHandlingContext.java",
        9531,
        "sha256:090379da05475414ddc93e848a795e4f3852d142fe51774e7c44d4a2257d3c11",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMPackets.java",
        23921,
        "sha256:00e65686080d1644e3bbf30c71d58f96a8bfc42c7f97dfa4090cacafa1066791",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramSignatureDescriptor.java",
        4472,
        "sha256:fe5538c9146ef5b7e0685d3ae18b4f63833b12d186331f550fe35fa551a2cd82",
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
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ProgramAttestationCodec.java",
        6771,
        "sha256:1fcf0c493f1abba27d5f19eccfcdb516089546d13ca53005cb771f0e3b6719b9",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/program/signing/ClientProgramSigningController.java",
        18631,
        "sha256:0cae5bda077697ac23402a29ee36c78eead52675c74948544ac1aab5be9eae8a",
    ),
    (
        "src/main/java/ca/teamdman/sfml/ast/Program.java",
        21161,
        "sha256:086f8cc029be421f5eed29c4cd54edb566c444ccf9836f2e48ef0429d675382d",
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
            128 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-signing-transport-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && ledger.files.len() == 4
                && ledger.definitions.len() == 11
                && ledger.current_provider_source_receipts.len() == 10
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
                        .collect(),
            "bounded transport original evidence changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let original = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("original transport contract absent: {name}"))?;
            let current = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("actual transport contract absent: {name}"))?;
            ensure!(
                original.supported_targets == support
                    && original.requires == requires
                    && current.supported_targets == support
                    && current.requires == requires,
                "original/current transport feature contract changed: {name}"
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
                    && evidence.owner == "client_program_signing"
                    && evidence.raw_oid == g.oid
                    && evidence.raw_bytes == g.bytes
                    && evidence.raw_sha256 == g.digest
                    && evidence.authored_bytes == g.bytes
                    && evidence.authored_sha256 == g.digest
                    && evidence.cr_count == 0
                    && evidence.lf_count == g.lf
                    && evidence.final_lf
                    && !evidence.bom
                    && evidence.common_edit_anchor == g.anchor
                    && evidence.witnesses == expected_witnesses(g),
                "immutable transport byte/membership evidence changed"
            );
            verify_raw(&raw[g.oid], g)?;
            let source = core.read_source(g.path)?;
            verify_raw(&source, g)?;
            ensure!(
                source == raw[g.oid],
                "transport source rewritten: {}",
                g.path
            );
            let rules =
                core.metadata.source_rules.get(g.path).ok_or_else(|| {
                    eyre::eyre!("root must first promote transport rule: {}", g.path)
                })?;
            ensure!(
                rules.len() == 1
                    && rules[0] == evidence.source_rule
                    && rules[0].input == g.path
                    && rules[0].template
                    && rules[0].when.targets == D2
                    && rules[0].when.all_features == vec!["client_program_signing"]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "transport ownership/support must remain exact signing/D2"
            );
            sources.insert(g.path.to_owned(), source);
        }
        for ((path, length, digest), evidence) in PROVIDERS
            .iter()
            .zip(&ledger.current_provider_source_receipts)
        {
            let current_rules = core
                .metadata
                .source_rules
                .get(*path)
                .cloned()
                .unwrap_or_default();
            ensure!(
                evidence.path == *path
                    && evidence.bytes == *length
                    && evidence.sha256 == *digest
                    && current_rules == evidence.rules,
                "real transport provider membership/evidence changed: {path}"
            );
            let source = core.read_source(path)?;
            ensure!(
                source.len() == *length && sha256(&source) == *digest,
                "real transport provider body changed: {path}"
            );
        }
        ensure!(
            sources.values().map(Vec::len).sum::<usize>() == 11_657,
            "transport four-source total changed"
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
                "unaccounted transport omission"
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
            .ok_or_else(|| eyre::eyre!("real transport provider omitted: {path}"))?;
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
    fn write_sources(&self, root: &Path) -> Result<()> {
        for (path, source) in &self.sources {
            let destination = root.join(path);
            fs::create_dir_all(
                destination
                    .parent()
                    .ok_or_else(|| eyre::eyre!("transport source parent missing"))?,
            )?;
            fs::write(destination, source)?;
        }
        Ok(())
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
            let bytes = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let destination = root.join(&input.input);
            if destination.exists() {
                ensure!(
                    read_bounded(&destination, 16 * 1024 * 1024)? == bytes,
                    "real standalone fixture input changed between exact contexts"
                );
            } else {
                fs::create_dir_all(
                    destination
                        .parent()
                        .ok_or_else(|| eyre::eyre!("standalone fixture parent missing"))?,
                )?;
                fs::write(destination, bytes)?;
            }
        }
        Ok(())
    }
}
fn expected_witnesses(g: &Golden) -> BTreeMap<String, Option<String>> {
    CONTEXTS
        .into_iter()
        .map(|(name, _)| {
            (
                name.to_owned(),
                matches!(name, "dev/1.19.2" | "dev/1.19.4").then(|| g.oid.to_owned()),
            )
        })
        .collect()
}
fn verify_raw(bytes: &[u8], g: &Golden) -> Result<()> {
    ensure!(
        bytes.len() == g.bytes
            && sha256(bytes) == g.digest
            && bytes.iter().filter(|byte| **byte == b'\n').count() == g.lf
            && !bytes.contains(&b'\r')
            && bytes.last() == Some(&b'\n')
            && !bytes.starts_with(&[239, 187, 191]),
        "transport raw identity changed: {}",
        g.path
    );
    Ok(())
}
fn assert_before(body: &str, first: &str, second: &str) {
    let left = body
        .find(first)
        .unwrap_or_else(|| panic!("missing source anchor: {first}"));
    let right = body
        .find(second)
        .unwrap_or_else(|| panic!("missing source anchor: {second}"));
    assert!(
        left < right,
        "source order changed: {first} before {second}"
    );
}

#[test]
fn all_eighty_transport_git_cells_reconstruct_exact_source_membership_and_bytes() -> Result<()> {
    let f = Fixture::load()?;
    let mut present = 0;
    let mut omitted = 0;
    for (name, commit) in CONTEXTS {
        let mut command = frozen_git_command(&f.core.repository);
        command.args(["ls-tree", commit, "--"]);
        for g in &GOLDENS {
            command.arg(format!("platform/minecraft/{}", g.path));
        }
        let output = command.output()?;
        ensure!(
            output.status.success()
                && output.stdout.len() <= 64 * 1024
                && output.stderr.len() <= 64 * 1024,
            "bounded offline transport tree query failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("transport tree framing changed"))?;
            let mut fields = header.split_whitespace();
            ensure!(
                fields.next() == Some("100644") && fields.next() == Some("blob"),
                "transport witness type/mode changed"
            );
            let oid = fields
                .next()
                .ok_or_else(|| eyre::eyre!("transport blob ID missing"))?;
            ensure!(
                fields.next().is_none() && actual.insert(path.to_owned(), oid.to_owned()).is_none(),
                "transport tree row duplicated/extended"
            );
        }
        let target = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("context target missing"))?
            .1;
        let flags = if matches!(name, "dev/1.19.2" | "dev/1.19.4") {
            SIGNING.as_slice()
        } else {
            &[]
        };
        let context = f.core.context(target, flags)?;
        for g in &GOLDENS {
            let expected = expected_witnesses(g)[name].clone();
            assert_eq!(
                actual
                    .get(&format!("platform/minecraft/{}", g.path))
                    .cloned(),
                expected
            );
            let rendered = f.render(g.path, &context)?;
            if expected.is_some() {
                assert_eq!(
                    rendered
                        .ok_or_else(|| eyre::eyre!("witness transport omitted"))?
                        .as_bytes(),
                    f.raw[g.oid]
                );
                present += 1;
            } else {
                assert!(rendered.is_none());
                omitted += 1;
            }
        }
    }
    assert_eq!((present, omitted), (8, 72));
    Ok(())
}

#[test]
fn independent_owner_profiles_keep_transport_and_worker_signing_only() -> Result<()> {
    let f = Fixture::load()?;
    let mut selected = 0;
    let mut omitted = 0;
    for target in D2 {
        for flags in [
            &[] as &[&str],
            &["sfml_execution_side"],
            CONSENT.as_slice(),
            MANAGER.as_slice(),
            FRAME.as_slice(),
            ACTIONS.as_slice(),
            SIGNING.as_slice(),
        ] {
            let mut context = f.core.context(target, flags)?;
            context.environment = "unrelated-counterfactual".to_owned();
            context.preset = "unrelated-preset".to_owned();
            context.projection_key = "unrelated/key".to_owned();
            for g in &GOLDENS {
                let output = f.render(g.path, &context)?;
                if flags.contains(&"client_program_signing") {
                    assert_eq!(
                        output
                            .ok_or_else(|| eyre::eyre!("signing transport omitted"))?
                            .as_bytes(),
                        f.raw[g.oid]
                    );
                    selected += 1;
                } else {
                    assert!(output.is_none());
                    omitted += 1;
                }
            }
            for forbidden in [
                "client_frame_render",
                "touch_display",
                "image_resources",
                "multiplayer_packets",
                "client_manager_gui",
                "command_palette",
                "terminal_local",
                "workspace_panels",
            ] {
                assert!(!context.features[forbidden]);
            }
        }
    }
    assert_eq!((selected, omitted), (8, 48));
    Ok(())
}

#[test]
fn missing_prerequisites_unknown_owner_keys_and_unsupported_targets_are_refused() -> Result<()> {
    let f = Fixture::load()?;
    let mut missing = 0;
    for target in D2 {
        for removed in SIGNING
            .into_iter()
            .filter(|name| *name != "client_program_signing")
        {
            let flags: Vec<_> = SIGNING
                .into_iter()
                .filter(|name| *name != removed)
                .collect();
            assert!(
                f.core.context(target, &flags).is_err(),
                "required {removed}"
            );
            missing += 1;
        }
        let mut context = f.core.context(target, &SIGNING)?;
        context.features.remove("client_program_signing");
        assert!(select_core_inputs(&f.core.metadata, &context, &f.inventory()).is_err());
    }
    assert_eq!(missing, 20);
    let mut unsupported = 0;
    for target in TEN.into_iter().filter(|target| !D2.contains(target)) {
        for flags in [FRAME.as_slice(), ACTIONS.as_slice(), SIGNING.as_slice()] {
            assert!(f.core.context(target, flags).is_err());
            unsupported += 1;
        }
    }
    assert_eq!(unsupported, 24);
    Ok(())
}

#[test]
fn real_provider_apis_registration_order_and_delayed_crypto_decoding_are_preserved() -> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        let context = f.core.context(target, &SIGNING)?;
        for (path, _, _) in PROVIDERS {
            assert!(!f.provider(path, &context)?.contains("{%"));
        }
        let state = f.provider(PROVIDERS[6].0, &context)?;
        let encoded = state
            .split("Status submitEncoded")
            .nth(1)
            .ok_or_else(|| eyre::eyre!("real encoded submission API missing"))?;
        assert_before(
            encoded,
            "session.take(",
            "ProgramAttestationCodec.decode(encoded)",
        );
        assert_before(
            encoded,
            "matchesRevision(",
            "ProgramAttestationCodec.decode(encoded)",
        );
        let controller = f.provider(PROVIDERS[8].0, &context)?;
        let receive = controller
            .split("boolean receive(")
            .nth(1)
            .ok_or_else(|| eyre::eyre!("real receiver API missing"))?;
        assert_before(
            receive,
            "!pendingRequest.equals(response.requestId())",
            "decoder.decode(",
        );
        assert_before(
            receive,
            "!base.target().dimension().equals(response.dimension())",
            "decoder.decode(",
        );
        let registrar = f.provider(PROVIDERS[3].0, &context)?;
        assert_before(
            &registrar,
            "new ServerboundClientManagerSigningRequestPacket.Daddy()",
            "new ServerboundClientManagerSignaturePacket.Daddy()",
        );
        assert_before(
            &registrar,
            "new ServerboundClientManagerSignaturePacket.Daddy()",
            "new ClientboundClientManagerSigningResponsePacket.Daddy()",
        );
        for g in &GOLDENS[..3] {
            let body = std::str::from_utf8(&f.sources[g.path])?;
            assert!(!body.contains("ProgramAttestationCodec.decode("));
            assert!(!body.contains("ClientSigningKeyStore"));
        }
        let response = std::str::from_utf8(&f.sources[GOLDENS[0].path])?;
        let decode = response
            .split("decode(FriendlyByteBuf source)")
            .nth(1)
            .ok_or_else(|| eyre::eyre!("real envelope decoder missing"))?;
        assert!(!decode.contains("decodeAcknowledgement("));
        // This is registrar/handler SOURCE proof. The bounded collector excludes
        // SFMPackets and cannot approve a partial complete-project wire layout.
    }
    Ok(())
}

#[test]
fn original_bounds_cloning_charge_and_worker_anchors_reject_raw_mutations() -> Result<()> {
    let f = Fixture::load()?;
    const ANCHORS: [(&str, &[&str]); 4] = [
        (
            "src/main/java/ca/teamdman/sfm/common/net/ClientboundClientManagerSigningResponsePacket.java",
            &[
                "PacketDirection.CLIENTBOUND",
                "position = Objects.requireNonNull(position).immutable();",
                "ClientManagerSigningCodec.MAX_ACKNOWLEDGEMENT_BYTES",
                "return bytes.clone();",
                "acknowledgement.map(byte[]::clone)",
                "acknowledgement.map(ClientManagerSigningCodec::decodeAcknowledgement)",
                "source.readUtf(256)",
                "ClientManagerSigningState.Status.values().length",
                "source.readByteArray(ClientManagerSigningCodec.MAX_ACKNOWLEDGEMENT_BYTES)",
                "ClientManagerSigningResponses.receive(packet);",
            ],
        ),
        (
            "src/main/java/ca/teamdman/sfm/common/net/ServerboundClientManagerSigningRequestPacket.java",
            &[
                "PacketDirection.SERVERBOUND",
                "record Save(UUID incarnation, long revision, String source)",
                "revision < 0 || source.length() > Program.MAX_PROGRAM_LENGTH",
                "ProgramSignatureDescriptor.normalizedSourceBytes(source);",
                "declaredCapabilities.isEmpty()",
                "ProgramSignatureDescriptor.MAX_CAPABILITIES",
                "declaredCapabilities = List.copyOf(declaredCapabilities);",
                "capability.toString().length() > 256",
                "getBytes(java.nio.charset.StandardCharsets.UTF_8)",
                "count <= 0 || count > ProgramSignatureDescriptor.MAX_CAPABILITIES",
                "target.writeUUID(save.incarnation);",
                "target.writeLong(save.revision);",
                "source.readUtf(Program.MAX_PROGRAM_LENGTH)",
                "SFMServerClientManagerSigningTransport.receive(packet, context.sender());",
            ],
        ),
        (
            "src/main/java/ca/teamdman/sfm/common/net/ServerboundClientManagerSignaturePacket.java",
            &[
                "PacketDirection.SERVERBOUND",
                "revision < 0 || dimension.toString().length() > 256 || attestation.length == 0",
                "ProgramAttestationCodec.MAX_ATTESTATION_BYTES",
                "attestation = attestation.clone();",
                "return attestation.clone();",
                "return attestation.length + 128;",
                "target.writeLong(value.revision);",
                "target.writeUUID(value.challenge).writeByteArray(value.attestation);",
                "source.readByteArray(ProgramAttestationCodec.MAX_ATTESTATION_BYTES)",
                "SFMServerClientManagerSigningTransport.receive(packet, context.sender());",
            ],
        ),
        (
            "src/main/java/ca/teamdman/sfm/client/program/signing/ClientSigningUiWorker.java",
            &[
                "new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS",
                "new ArrayBlockingQueue<>(1)",
                "new Thread(task, \"sfm-signing-key-ui\")",
                "thread.setDaemon(true);",
                "new ThreadPoolExecutor.AbortPolicy()",
                "public static Executor executor() { return WORKER; }",
            ],
        ),
    ];
    for (path, anchors) in ANCHORS {
        let source = std::str::from_utf8(&f.sources[path])?;
        for anchor in anchors {
            assert!(source.contains(anchor), "{path}: {anchor}");
        }
    }
    for g in &GOLDENS {
        let bytes = &f.sources[g.path];
        let mut extra_lf = bytes.clone();
        extra_lf.push(b'\n');
        let mut missing_lf = bytes.clone();
        missing_lf.pop();
        let mut space = bytes.clone();
        space.insert(0, b' ');
        let mut bom = vec![239, 187, 191];
        bom.extend(bytes);
        let crlf = std::str::from_utf8(bytes)?
            .replace('\n', "\r\n")
            .into_bytes();
        for mutant in [extra_lf, missing_lf, space, bom, crlf] {
            assert!(verify_raw(&mutant, g).is_err());
        }
    }
    Ok(())
}

#[test]
fn signing_off_transport_inputs_are_omitted_before_invalid_utf8_or_directive_reads() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    for g in &GOLDENS {
        let path = root.join(g.path);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("input parent missing"))?,
        )?;
        fs::write(path, b"{% if features.unknown_owner %}\n\xff")?;
    }
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    let metadata = f.bounded_metadata();
    for target in D2 {
        for flags in [
            &[] as &[&str],
            CONSENT.as_slice(),
            FRAME.as_slice(),
            ACTIONS.as_slice(),
        ] {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for g in &GOLDENS {
                assert!(selected.omitted_paths.contains(g.path));
                assert!(!artifacts.contains_key(g.path));
            }
        }
    }
    Ok(())
}

#[test]
fn automatic_java_rendering_with_template_false_uses_actual_fixed_core_collector() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let worker = &GOLDENS[3];
    let original = std::str::from_utf8(&f.sources[worker.path])?;
    let edited = format!(
        "{original}{{% if features.client_program_signing %}}\n// Isolated signing Java probe.\n{{% endif %}}\n"
    );
    fs::write(root.join(worker.path), &edited)?;
    let mut metadata = f.bounded_metadata();
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let inventory = discover_core_source_files(&root)?;
    let mut outputs = 0;
    for target in D2 {
        let context = f.core.context(target, &SIGNING)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for g in &GOLDENS {
            let artifact = &artifacts[g.path];
            let output = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = output
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("generated Java banner absent"))?;
            if g.path == worker.path {
                assert_eq!(body, render_java_source(&edited, &context)?);
                assert!(body.contains("// Isolated signing Java probe."));
            } else {
                assert_eq!(body.as_bytes(), f.raw[g.oid]);
            }
            assert!(!body.contains("{%"));
            assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{}", g.path));
            assert!(artifact.overlay.is_none());
            outputs += 1;
        }
    }
    assert_eq!(outputs, 8);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}

#[test]
fn shared_body_edits_reach_both_signing_targets_without_touching_live_core() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut edits = BTreeMap::new();
    for g in &GOLDENS {
        let original = std::str::from_utf8(&f.sources[g.path])?;
        assert_eq!(original.matches(g.anchor).count(), 1);
        let edited = original.replacen(
            g.anchor,
            &format!("// Isolated common transport edit.\n{}", g.anchor),
            1,
        );
        fs::write(root.join(g.path), &edited)?;
        edits.insert(g.path.to_owned(), edited);
    }
    let inventory = discover_core_source_files(&root)?;
    let metadata = f.bounded_metadata();
    let mut outputs = 0;
    for target in D2 {
        let context = f.core.context(target, &SIGNING)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for g in &GOLDENS {
            let artifact = &artifacts[g.path];
            let output = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = output
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("generated Java banner absent"))?;
            assert_eq!(body, render_java_source(&edits[g.path], &context)?);
            assert!(body.contains("// Isolated common transport edit."));
            outputs += 1;
        }
    }
    assert_eq!(outputs, 8);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}
