//! Two genuine signing admission/receiver bridges through real core selection.
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

const LEDGER: &str = "docs/tasks/sfm-core-signing-delegates-slice.json";
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
const GOLDENS: [Golden; 2] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/net/ClientManagerSigningResponses.java",
        oid: "555733a163413581cda62d7e0c217c71cb903224",
        bytes: 702,
        digest: "sha256:767895f816d73fe48a4828caeb949a560bb9967f64c2c53292a63f82c492ec28",
        lf: 14,
        anchor: "public final class ClientManagerSigningResponses",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/net/SFMServerClientManagerSigningTransport.java",
        oid: "308e1e3039c933cd974d12b5d079aacbecefd40c",
        bytes: 6805,
        digest: "sha256:ce0518f3b7c1e92a2e701e3fa4124299dd5c67999a049cfa70409420ef5cd68e",
        lf: 125,
        anchor: "public final class SFMServerClientManagerSigningTransport",
    },
];
const PROVIDERS: [(&str, usize, &str); 11] = [
    (
        "src/main/java/ca/teamdman/sfm/common/net/SFMPacketEffectGate.java",
        1228,
        "sha256:5c879613b51fb3e27f357afa8e75727daeb2d3bc5c789ae634f97a9ebeebbba1",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningAdmission.java",
        2031,
        "sha256:a42d06ed0fe17c9d4c867a7464ac33d8c28365b641f1b99838ee931029dfac28",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningSession.java",
        4443,
        "sha256:45013039826e255c98340e2b6ccf5944386aa86a7c8e4a1495b3fe25d87a4cba",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningState.java",
        8942,
        "sha256:9c2096bdcc9b18c95c3a32373b51a7daa8be727a0c54aecc8da780d6a0ce114f",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/program/signature/ClientManagerSigningCodec.java",
        7207,
        "sha256:038be7d0522ddcc2452d6d69dec8459e569547e56a43322b0abbdf6564a49c58",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/blockentity/ClientManagerBlockEntity.java",
        16044,
        "sha256:d5e8419a0d8f1ffb70a76317f92f37afcdb80a9ceb2d98423373f0277f5e974d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/event_bus/SFMSubscribeEvent.java",
        1916,
        "sha256:033c8970768a54ac472f333ab03a2058e9bf24de89e35e92b60bd260d03cac75",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMPackets.java",
        23921,
        "sha256:00e65686080d1644e3bbf30c71d58f96a8bfc42c7f97dfa4090cacafa1066791",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/net/ClientboundClientManagerSigningResponsePacket.java",
        3653,
        "sha256:9e3a56674cf9122152840975cb43705d593c9272ea39a8a5d9caf48eb08059c0",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/net/ServerboundClientManagerSigningRequestPacket.java",
        4448,
        "sha256:eb0cfbd3eb23d5857b94579ed3f3338acb1d74b0579129e0fea12cb4dc1d1af7",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/net/ServerboundClientManagerSignaturePacket.java",
        2749,
        "sha256:9d2ebebdbd15f29aad78bb77912808165e764cb9ddea6b6a7dc863fe32d5c42b",
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
            ledger.schema == "sfm:core-signing-delegates-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && ledger.files.len() == 2
                && ledger.definitions.len() == 11
                && ledger.current_provider_source_receipts.len() == 11
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
            sources.values().map(Vec::len).sum::<usize>() == 7_507,
            "delegate two-source total changed"
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
fn all_forty_delegate_git_cells_reconstruct_exact_source_membership_and_bytes() -> Result<()> {
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
    assert_eq!((present, omitted), (4, 36));
    Ok(())
}

#[test]
fn independent_owner_profiles_keep_both_real_delegates_signing_only() -> Result<()> {
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
    assert_eq!((selected, omitted), (4, 24));
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
fn real_admission_revision_rate_and_receiver_contracts_remain_existing_providers() -> Result<()> {
    let f = Fixture::load()?;
    let server = std::str::from_utf8(&f.sources[GOLDENS[1].path])?;
    let request = server
        .split("receive(ServerboundClientManagerSigningRequestPacket")
        .nth(1)
        .ok_or_else(|| eyre::eyre!("real review request overload absent"))?;
    assert_before(
        request,
        "!sender.getServer().isSameThread()",
        "state(sender.getServer())",
    );
    assert_before(request, "!state.budget.reserve", "authorizedTarget(");
    assert_before(request, "authorizedTarget(", "manager.saveForSigning(");
    assert_before(request, "authorizedTarget(", "manager.reviewForSigning(");
    let submit = server
        .split("receive(ServerboundClientManagerSignaturePacket")
        .nth(1)
        .ok_or_else(|| eyre::eyre!("real signature overload absent"))?;
    assert_before(
        submit,
        "!sender.getServer().isSameThread()",
        "state(sender.getServer())",
    );
    assert_before(submit, "!state.budget.reserve", "authorizedTarget(");
    assert_before(submit, "authorizedTarget(", "manager.submitSignature(");
    assert_before(
        submit,
        "state.reviews.take(",
        "result = ClientManagerSigningState.Status.UNAUTHORIZED;",
    );
    assert!(!server.contains("ProgramAttestationCodec.decode("));
    let receiver = std::str::from_utf8(&f.sources[GOLDENS[0].path])?;
    assert!(receiver.contains("receiver = ignored -> { };"));
    assert!(receiver.contains("receiver.accept(response);"));
    for forbidden in [
        "ClientProgramSigningRuntime",
        "ClientSigningKeyStore",
        "Minecraft.getInstance",
        "decodeAcknowledgement",
        "ProgramAttestationCodec",
    ] {
        assert!(
            !receiver.contains(forbidden),
            "common bridge must not fabricate UI/decode: {forbidden}"
        );
    }
    for target in D2 {
        let context = f.core.context(target, &SIGNING)?;
        for (path, _, _) in PROVIDERS {
            assert!(!f.provider(path, &context)?.contains("{%"));
        }
        let gate = f.provider(PROVIDERS[0].0, &context)?;
        assert!(gate.contains("return singleplayer && !published && localPlayerOwnsWorld;"));
        assert!(gate.contains("server.isSingleplayerOwner(player.getGameProfile())"));
        let admission = f.provider(PROVIDERS[1].0, &context)?;
        for anchor in [
            "MAX_OPERATIONS_PER_PLAYER = 4",
            "MAX_OPERATIONS_GLOBAL = 16",
            "MAX_BYTES_PER_PLAYER = 512 * 1024",
            "MAX_BYTES_GLOBAL = 2 * 1024 * 1024",
            "chargedBytes > MAX_BYTES_GLOBAL - bytes",
            "chargedBytes > MAX_BYTES_PER_PLAYER - current.bytes",
        ] {
            assert!(
                admission.contains(anchor),
                "aggregate existing budget anchor: {anchor}"
            );
        }
        let session = f.provider(PROVIDERS[2].0, &context)?;
        let (_, take) = session
            .rsplit_once("public synchronized Optional<ClientManagerSigningAcknowledgement> take(")
            .ok_or_else(|| eyre::eyre!("real address-bound take API absent"))?;
        assert_before(
            take,
            "pending.remove(new Key(player, incarnation))",
            "!found.address().equals(address)",
        );
        let state = f.provider(PROVIDERS[3].0, &context)?;
        let encoded = state
            .split("Status submitEncoded")
            .nth(1)
            .ok_or_else(|| eyre::eyre!("real encoded signing state API absent"))?;
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
        let manager = f.provider(PROVIDERS[5].0, &context)?;
        for anchor in [
            "reviewForSigning(",
            "saveForSigning(",
            "submitSignature(",
            "signingState.submitEncoded(",
            "SFMServerClientManagerSigningTransport.invalidate(",
        ] {
            assert!(
                manager.contains(anchor),
                "real manager API absent: {anchor}"
            );
        }
        let registrar = f.provider(PROVIDERS[7].0, &context)?;
        assert!(registrar.contains("ServerPlayer player,"));
        assert!(registrar.contains("PacketDistributor.PLAYER.with(() -> player)"));
        for g in &GOLDENS {
            assert!(
                !f.render(g.path, &context)?
                    .ok_or_else(|| eyre::eyre!("signing delegate omitted"))?
                    .contains("{%")
            );
        }
        // This is source preservation, not runtime listener discovery or approval
        // of a partial wire layout. The collector fixture excludes SFMPackets.
    }
    Ok(())
}

#[test]
fn original_authority_ack_invalidation_and_receiver_anchors_reject_mutations() -> Result<()> {
    let f = Fixture::load()?;
    const ANCHORS: [(&str, &[&str]); 2] = [
        (
            "src/main/java/ca/teamdman/sfm/common/net/ClientManagerSigningResponses.java",
            &[
                "private static volatile Consumer<ClientboundClientManagerSigningResponsePacket> receiver = ignored -> { };",
                "receiver = Objects.requireNonNull(value);",
                "receiver.accept(response);",
            ],
        ),
        (
            "src/main/java/ca/teamdman/sfm/common/net/SFMServerClientManagerSigningTransport.java",
            &[
                "private static final double MAX_DISTANCE_SQUARED = 8 * 8;",
                "new WeakHashMap<>()",
                "new ClientManagerSigningSession();",
                "new ClientManagerSigningAdmission();",
                "sender == null || sender.getServer() == null || !sender.getServer().isSameThread()",
                "request.chargedBytes() + ClientManagerSigningCodec.MAX_ACKNOWLEDGEMENT_BYTES",
                "request.chargedBytes() + 512",
                "authorizedTarget(sender, request.dimension(), request.position())",
                "manager.saveForSigning(state.reviews, sender.getUUID(), save.incarnation(), save.revision(),",
                "manager.reviewForSigning(state.reviews, sender.getUUID(), request.declaredCapabilities(), tick)",
                "state.reviews.take(sender.getUUID(), request.incarnation(), request.challenge(), tick,",
                "request.dimension() + \"/\" + request.position().asLong()",
                "manager.submitSignature(state.reviews, sender.getUUID(), request.incarnation(), request.revision(),",
                "!SFMPacketEffectGate.allowsServerEffects(sender)",
                "sender.isSpectator() || !sender.isAlive()",
                "!level.dimension().location().equals(dimension) || !level.hasChunkAt(position)",
                "sender.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) > MAX_DISTANCE_SQUARED",
                "!level.mayInteract(sender, position)",
                "instanceof ClientManagerBlockEntity manager && !manager.isRemoved()",
                "SFMPackets.sendToPlayer(sender, new ClientboundClientManagerSigningResponsePacket",
                "acknowledgement.map(ClientManagerSigningCodec::encodeAcknowledgement)",
                "state.reviews.invalidate(incarnation)",
                "PlayerEvent.PlayerLoggedOutEvent",
                "PlayerEvent.PlayerChangedDimensionEvent",
                "STATES.remove(event.getServer())",
                "removed.reviews.clear();",
                "removed.budget.clear();",
                "state.reviews.forgetPlayer(player.getUUID())",
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
    let worker = &GOLDENS[0];
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
    assert_eq!(outputs, 4);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}

#[test]
fn shared_delegate_edits_reach_both_signing_targets_without_touching_live_core() -> Result<()> {
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
    assert_eq!(outputs, 4);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}
