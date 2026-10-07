//! Real ClientManagerFrameRuntime source-selection and Liquid contracts.
//!
//! Parent promotion of the one genuine template/rule precedes registration.
//! Frozen Git objects are offline test evidence, not production routing.
//! Independent Boolean profiles below are explicitly prerequisite-closed;
//! no helper silently expands them or changes the public catalog. These tests
//! do not compile Java, run a world, activate multiplayer, grant consent,
//! prove actual pixels/visibility or fabricate any missing provider.
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

const PATH: &str = "src/main/java/ca/teamdman/sfm/client/program/ClientManagerFrameRuntime.java";
const LEDGER: &str = "docs/tasks/sfm-core-frame-runtime-slice.json";
const RAW_OID: &str = "3b29d2749cf33b06e8a10bc3ba3f497173841cdf";
const RAW_DIGEST: &str = "sha256:bf88b401ff3eacc0c2dad9504b55912a02da0f374ae34955578f4d002d1f7634";
const AUTHORED_DIGEST: &str =
    "sha256:528094eb87f7b4f78678c26929e8e84b54d7a4c0fe1c919b73d0bc82abb4e184";
const RAW_BYTES: usize = 23_913;
const AUTHORED_BYTES: usize = 27_717;
const COMMON_ANCHOR: &str = "    public static final int MAX_FRAME_TRIGGERS = 32;";
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const TEN: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const L: &str = "client_frame_language";
const T: &str = "touch_display";
const A: &str = "client_program_actions";
const R: &str = "client_frame_render";
const M: &str = "multiplayer_packets";

const LEDGER_BYTES: usize = 95093;
const LEDGER_DIGEST: &str =
    "sha256:f666426ca2ab9ce10c6bf1082d87351710b3dae0a3ec70d706fb90ed5ca72fa1";
const DEFINITIONS: [(&str, &[&str], &[&str]); 17] = [
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
        "client_frame_render",
        &["1.19.2", "1.19.4"],
        &["client_frame_language", "touch_display", "image_resources"],
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
        "multiplayer_packets",
        &["1.19.2", "1.19.4"],
        &[
            "client_inbox",
            "client_frame_language",
            "client_program_actions",
        ],
    ),
    (
        "client_inbox",
        &["1.19.2", "1.19.4"],
        &["packet_transport_private"],
    ),
    (
        "packet_transport_private",
        &["1.19.2", "1.19.4"],
        &["packet_computation"],
    ),
    (
        "packet_computation",
        &["1.19.2", "1.19.4"],
        &["packet_values", "runtime_resource_cleanup"],
    ),
    ("packet_values", &["1.19.2", "1.19.4"], &[]),
    ("runtime_resource_cleanup", &["1.19.2", "1.19.4"], &[]),
    (
        "touch_display",
        &["1.19.2", "1.19.4"],
        &["packet_values", "image_resources"],
    ),
    ("image_resources", &["1.19.2", "1.19.4"], &["packet_values"]),
    ("sfml_execution_side", &["1.19.2", "1.19.4"], &[]),
    (
        "client_actions",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
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
        "client_program_signing",
        &["1.19.2", "1.19.4"],
        &["client_frame_language", "client_program_actions"],
    ),
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

struct ProfileGolden {
    id: &'static str,
    flags: &'static [&'static str],
    touch: bool,
    actions: bool,
    render: bool,
    multiplayer: bool,
    bytes: usize,
    digest: &'static str,
}

const PROFILES: [ProfileGolden; 9] = [
    ProfileGolden {
        id: "frame_only",
        flags: &[
            "client_frame_language",
            "client_manager",
            "client_program_consent",
            "disk_readonly_access",
            "sfml_execution_side",
        ],
        touch: false,
        actions: false,
        render: false,
        multiplayer: false,
        bytes: 9493,
        digest: "sha256:57862f1a2cc7f3acd3c1a0b0896f80359b7df8776de093a950b4e85e6777b9d0",
    },
    ProfileGolden {
        id: "touch",
        flags: &[
            "client_frame_language",
            "client_manager",
            "client_program_consent",
            "disk_readonly_access",
            "image_resources",
            "packet_values",
            "sfml_execution_side",
            "touch_display",
        ],
        touch: true,
        actions: false,
        render: false,
        multiplayer: false,
        bytes: 21748,
        digest: "sha256:298d7db4e3673947bde85d29883a39460fb0d09ba4d43d55c474cf09f0e7d716",
    },
    ProfileGolden {
        id: "actions",
        flags: &[
            "client_actions",
            "client_frame_language",
            "client_manager",
            "client_program_actions",
            "client_program_consent",
            "disk_readonly_access",
            "packet_computation",
            "packet_values",
            "runtime_resource_cleanup",
            "sfml_execution_side",
        ],
        touch: false,
        actions: true,
        render: false,
        multiplayer: false,
        bytes: 9938,
        digest: "sha256:3851dcf064bcce811f83391166bdf978ff85e8f2a26de2d6b2453700d539c3ba",
    },
    ProfileGolden {
        id: "touch_actions",
        flags: &[
            "client_actions",
            "client_frame_language",
            "client_manager",
            "client_program_actions",
            "client_program_consent",
            "disk_readonly_access",
            "image_resources",
            "packet_computation",
            "packet_values",
            "runtime_resource_cleanup",
            "sfml_execution_side",
            "touch_display",
        ],
        touch: true,
        actions: true,
        render: false,
        multiplayer: false,
        bytes: 23422,
        digest: "sha256:df9c8dd7bca5fa062775a34ddd575ffc2714b61a06f179a32c2a4f01eababb37",
    },
    ProfileGolden {
        id: "touch_render",
        flags: &[
            "client_frame_language",
            "client_frame_render",
            "client_manager",
            "client_program_consent",
            "disk_readonly_access",
            "image_resources",
            "packet_values",
            "sfml_execution_side",
            "touch_display",
        ],
        touch: true,
        actions: false,
        render: true,
        multiplayer: false,
        bytes: 22083,
        digest: "sha256:7beccbcec3069ead47336510be90e846b9e8af7355ec3b2764650bde2dc34ba4",
    },
    ProfileGolden {
        id: "touch_render_actions",
        flags: &[
            "client_actions",
            "client_frame_language",
            "client_frame_render",
            "client_manager",
            "client_program_actions",
            "client_program_consent",
            "disk_readonly_access",
            "image_resources",
            "packet_computation",
            "packet_values",
            "runtime_resource_cleanup",
            "sfml_execution_side",
            "touch_display",
        ],
        touch: true,
        actions: true,
        render: true,
        multiplayer: false,
        bytes: 23757,
        digest: "sha256:5e351b0fe5937cdbdebf7bd73835cc28238e7e36295d7c90548a932b27ddba09",
    },
    ProfileGolden {
        id: "actions_remote",
        flags: &[
            "client_actions",
            "client_frame_language",
            "client_inbox",
            "client_manager",
            "client_program_actions",
            "client_program_consent",
            "disk_readonly_access",
            "multiplayer_packets",
            "packet_computation",
            "packet_transport_private",
            "packet_values",
            "runtime_resource_cleanup",
            "sfml_execution_side",
        ],
        touch: false,
        actions: true,
        render: false,
        multiplayer: true,
        bytes: 10094,
        digest: "sha256:4c9997d5819cb3c9ffc7fd85f39bf802255d2c9bf1cdb5800edbde8d0a944da9",
    },
    ProfileGolden {
        id: "touch_actions_remote",
        flags: &[
            "client_actions",
            "client_frame_language",
            "client_inbox",
            "client_manager",
            "client_program_actions",
            "client_program_consent",
            "disk_readonly_access",
            "image_resources",
            "multiplayer_packets",
            "packet_computation",
            "packet_transport_private",
            "packet_values",
            "runtime_resource_cleanup",
            "sfml_execution_side",
            "touch_display",
        ],
        touch: true,
        actions: true,
        render: false,
        multiplayer: true,
        bytes: 23578,
        digest: "sha256:1e85c1a67980869d31ff16601e58b266d46ecf275eae0e1c5cbb7dec7b104550",
    },
    ProfileGolden {
        id: "touch_render_actions_remote",
        flags: &[
            "client_actions",
            "client_frame_language",
            "client_frame_render",
            "client_inbox",
            "client_manager",
            "client_program_actions",
            "client_program_consent",
            "disk_readonly_access",
            "image_resources",
            "multiplayer_packets",
            "packet_computation",
            "packet_transport_private",
            "packet_values",
            "runtime_resource_cleanup",
            "sfml_execution_side",
            "touch_display",
        ],
        touch: true,
        actions: true,
        render: true,
        multiplayer: true,
        bytes: 23913,
        digest: "sha256:bf88b401ff3eacc0c2dad9504b55912a02da0f374ae34955578f4d002d1f7634",
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    contract_changes: bool,
    definitions: BTreeMap<String, Definition>,
    context_commits: BTreeMap<String, String>,
    file: SourceEvidence,
    regions: Vec<Region>,
    profiles: Vec<ProfileEvidence>,
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
    lf_count: usize,
    cr_count: usize,
    final_lf: bool,
    source_rule: InputVariant,
    witnesses: BTreeMap<String, Option<String>>,
    common_edit_anchor: String,
}
#[derive(Facet)]
struct Region {
    id: String,
    owners: Vec<String>,
    original_region: String,
    template_region: String,
    occurrences: usize,
    original_bytes: usize,
    template_bytes: usize,
    original_sha256: String,
    template_sha256: String,
}
#[derive(Facet)]
struct ProfileEvidence {
    id: String,
    touch: bool,
    actions: bool,
    render: bool,
    multiplayer: bool,
    explicit_features: Vec<String>,
    output_bytes: usize,
    output_sha256: String,
}
struct Fixture {
    core: CoreTestFixture,
    source: Vec<u8>,
    raw: Vec<u8>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger_bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 128 * 1024)?;
        ensure!(
            ledger_bytes.len() == LEDGER_BYTES && sha256(&ledger_bytes) == LEDGER_DIGEST,
            "immutable frame-runtime review ledger changed; update evidence deliberately"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&ledger_bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core-frame-runtime-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && ledger.definitions.len() == DEFINITIONS.len()
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
                        .collect(),
            "frame-runtime scope/raw owner contracts changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let original = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing original runtime contract: {name}"))?;
            let current = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing current runtime contract: {name}"))?;
            ensure!(
                original.supported_targets == support
                    && original.requires == requires
                    && current.supported_targets == support
                    && current.requires == requires,
                "original/current runtime owner contract drift: {name}"
            );
        }
        let evidence = &ledger.file;
        ensure!(
            evidence.path == PATH
                && evidence.owner == L
                && evidence.raw_oid == RAW_OID
                && evidence.raw_bytes == RAW_BYTES
                && evidence.raw_sha256 == RAW_DIGEST
                && evidence.authored_bytes == AUTHORED_BYTES
                && evidence.authored_sha256 == AUTHORED_DIGEST
                && evidence.lf_count == 465
                && evidence.cr_count == 0
                && evidence.final_lf
                && evidence.common_edit_anchor == COMMON_ANCHOR
                && evidence.witnesses == expected_witnesses(),
            "runtime exact twenty-tree/raw identity evidence changed"
        );
        let mut blobs = read_git_blobs(&core.repository, &BTreeSet::from([RAW_OID.to_owned()]))?;
        let raw = blobs
            .remove(RAW_OID)
            .ok_or_else(|| eyre::eyre!("missing frozen runtime raw blob"))?;
        verify_raw(&raw)?;
        let source = core.read_source(PATH)?;
        ensure!(
            source.len() == AUTHORED_BYTES
                && sha256(&source) == AUTHORED_DIGEST
                && !source.contains(&b'\r')
                && source.ends_with(b"\n"),
            "parent must first promote exact authored runtime source"
        );
        let rules = core
            .metadata
            .source_rules
            .get(PATH)
            .ok_or_else(|| eyre::eyre!("parent must first promote sparse runtime rule"))?;
        ensure!(
            rules.len() == 1
                && rules[0].input == PATH
                && rules[0].template
                && rules[0].when.targets == D2
                && rules[0].when.all_features == [L]
                && rules[0].when.any_features.is_empty()
                && rules[0].when.none_features.is_empty()
                && evidence.source_rule.input == PATH
                && evidence.source_rule.template
                && evidence.source_rule.when.targets == D2
                && evidence.source_rule.when.all_features == [L]
                && evidence.source_rule.when.any_features.is_empty()
                && evidence.source_rule.when.none_features.is_empty(),
            "runtime rule must use only existing frame owner plus actual D2 support"
        );
        ensure!(
            ledger.regions.len() == 36 && ledger.profiles.len() == PROFILES.len(),
            "bounded runtime region/profile inventory changed"
        );
        let mut ids = BTreeSet::new();
        for region in &ledger.regions {
            ensure!(
                ids.insert(region.id.clone())
                    && !region.owners.is_empty()
                    && region
                        .owners
                        .iter()
                        .all(|owner| [T, A, R, M].contains(&owner.as_str()))
                    && region.occurrences
                        == (if region.id == "compile_failure_manifest_arguments" {
                            4
                        } else {
                            1
                        })
                    && region.original_region.len() == region.original_bytes
                    && region.template_region.len() == region.template_bytes
                    && sha256(region.original_region.as_bytes()) == region.original_sha256
                    && sha256(region.template_region.as_bytes()) == region.template_sha256
                    && region.original_region.ends_with('\n')
                    && region.template_region.ends_with('\n'),
                "runtime ordered region identity/ownership changed"
            );
        }
        // Outer-region originals include earlier inner guards by design. This
        // is an ordered migration proof, not a context-free patching heuristic.
        let mut forward = std::str::from_utf8(&raw)?.to_owned();
        for region in &ledger.regions {
            ensure!(
                forward.matches(region.original_region.as_str()).count() == region.occurrences,
                "ordered forward runtime region became stale: {}",
                region.id
            );
            forward = forward.replace(region.original_region.as_str(), &region.template_region);
        }
        ensure!(
            forward.as_bytes() == source,
            "runtime changes outside reviewed regions"
        );
        let mut reverse = std::str::from_utf8(&source)?.to_owned();
        for region in ledger.regions.iter().rev() {
            ensure!(
                reverse.matches(region.template_region.as_str()).count() == region.occurrences,
                "ordered reverse runtime region became stale: {}",
                region.id
            );
            reverse = reverse.replace(region.template_region.as_str(), &region.original_region);
        }
        ensure!(
            reverse.as_bytes() == raw,
            "runtime reverse transformation is not exact raw witness"
        );
        for (g, evidence) in PROFILES.iter().zip(&ledger.profiles) {
            ensure!(
                evidence.id == g.id
                    && evidence.touch == g.touch
                    && evidence.actions == g.actions
                    && evidence.render == g.render
                    && evidence.multiplayer == g.multiplayer
                    && evidence.explicit_features == g.flags
                    && evidence.output_bytes == g.bytes
                    && evidence.output_sha256 == g.digest,
                "runtime independently reviewed mask/golden changed"
            );
        }
        Ok(Self { core, source, raw })
    }
    fn inventory(&self) -> BTreeSet<String> {
        BTreeSet::from([PATH.to_owned()])
    }
    fn render(&self, context: &ProjectionContext) -> Result<Option<String>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if !selected.inputs.contains_key(PATH) {
            ensure!(
                selected.omitted_paths.contains(PATH),
                "runtime omission not accounted"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.source)?,
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
            .ok_or_else(|| eyre::eyre!("real runtime provider omitted: {path}"))?;
        render_java_source(
            std::str::from_utf8(&self.core.read_source(&input.input)?)?,
            context,
        )
    }
    fn bounded_metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata.source_rules.retain(|path, _| path == PATH);
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
            if output == PATH {
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
                    "real standalone runtime fixture input differs by context"
                );
            } else {
                fs::create_dir_all(
                    destination
                        .parent()
                        .ok_or_else(|| eyre::eyre!("standalone input parent missing"))?,
                )?;
                fs::write(destination, bytes)?;
            }
        }
        Ok(())
    }
    fn write_source(&self, root: &Path, source: &[u8]) -> Result<()> {
        let path = root.join(PATH);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("runtime fixture parent missing"))?,
        )?;
        fs::write(path, source)?;
        Ok(())
    }
}
fn present(name: &str) -> bool {
    matches!(name, "dev/1.19.2" | "dev/1.19.4")
}
fn expected_witnesses() -> BTreeMap<String, Option<String>> {
    CONTEXTS
        .into_iter()
        .map(|(name, _)| (name.to_owned(), present(name).then(|| RAW_OID.to_owned())))
        .collect()
}
fn verify_raw(raw: &[u8]) -> Result<()> {
    ensure!(
        raw.len() == RAW_BYTES
            && sha256(raw) == RAW_DIGEST
            && raw.iter().filter(|byte| **byte == b'\n').count() == 465
            && !raw.contains(&b'\r')
            && raw.ends_with(b"\n")
            && !raw.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw runtime identity changed; no normalization approved"
    );
    std::str::from_utf8(raw)?;
    Ok(())
}
fn required_body<'a>(body: &'a str, start: &str, end: &str) -> Result<&'a str> {
    body.split_once(start)
        .and_then(|(_, tail)| tail.split_once(end))
        .map(|(member, _)| member)
        .ok_or_else(|| eyre::eyre!("runtime reviewed member boundaries changed: {start}"))
}
fn owner_shape(body: &str, g: &ProfileGolden) {
    for anchor in [
        "public static ClientProgramConsentGate consent()",
        "identityFor(ClientManagerBlockEntity manager)",
        "liveIdentityFor(ClientProgramIdentity expected)",
        "ClientFrameSourceBudget.permits(source)",
        "new ProgramBuilder(source).forExecutionSide(ProgramExecutionSide.CLIENT)",
        "ClientManagerTargetBindings.canonical(program, labels)",
        "ClientProgramConsentRuntime.observe(identity, source, bindings)",
        "current_private_or_negotiated_world_required",
    ] {
        assert!(
            body.contains(anchor),
            "{}: missing actual base API/authority {anchor}",
            g.id
        );
    }
    assert_eq!(body.contains("textureForSelectedFrame("), g.touch);
    assert_eq!(body.contains("ClientProgramActionManifest"), g.actions);
    assert_eq!(body.contains("RenderImageStatement"), g.render);
    assert_eq!(body.contains("SFMMultiplayerClientRuntime"), g.multiplayer);
    if !g.touch {
        for forbidden in [
            "TouchDisplayBlock",
            "FrameState",
            "ExecutionKey",
            "List<Writer>",
            "observeWorld(",
            "textureFor(",
            "presentationIdentity(",
            "valueFor(",
            "renderEligible(",
        ] {
            assert!(
                !body.contains(forbidden),
                "{}: unavailable touch provider {forbidden}",
                g.id
            );
        }
    }
    if !g.actions {
        for forbidden in [
            "SFMValue",
            "SFMClientActions",
            "SFMClientActionAuthorizationService",
            "SFMClientProgramActionDispatcher",
            "ClientProgramActionManifest",
            "ClientFrameEvaluator.Evaluation",
            "state.values",
            "writer.manifest()",
            "Map<String, SFMValue>",
        ] {
            assert!(
                !body.contains(forbidden),
                "{}: unavailable action provider {forbidden}",
                g.id
            );
        }
        assert!(body.contains(
            "? Set.of(ClientProgramConsentGate.EXECUTE, ClientProgramConsentGate.RENDER)"
        ));
        assert!(body.contains(": Set.of(ClientProgramConsentGate.EXECUTE)"));
        if g.touch {
            assert!(body.contains("Optional<ResourceLocation> evaluated = ClientFrameEvaluator.evaluate(writer.trigger(), state.frameIndex);"));
            assert!(body.contains("if (writer.renderAllowed() && writers.size() == 1\n"));
            assert!(body.contains(
                "&& evaluated.isPresent() && !Objects.equals(evaluated.get(), state.texture)"
            ));
        }
    }
    if !g.render {
        assert!(body.contains(
            "private static boolean requestsRender(Block block) {\n        return false;\n    }"
        ));
        assert!(!body.contains("IfStatement"));
    }
    if !g.multiplayer {
        assert!(body.contains("return privateWorldAvailable();"));
        assert!(body.contains(
            "Optional.of(ClientProgramWorldIdentity.integrated(worldId)) : Optional.empty();"
        ));
    }
    if g.touch {
        for anchor in [
            "programs.stream().filter(Writer::renderAllowed).toList()",
            "if (writers.size() != 1)",
            "consent().execution(compiled.identity()",
            "consent().evaluate(compiled.identity(), ClientProgramConsentGate.RENDER",
            "if (!execution.allowed()) continue;",
            "WORK_BUDGET.tryAcquire(renderEpoch)",
            "PROGRAM_WORK_BUDGET.tryAcquire(renderEpoch)",
            "if (state.lastEpoch == epoch) continue;",
            "state.frameIndex++;",
            "state.evaluations++;",
            "states.keySet().retainAll(active)",
            "world.getBlockEntity(manager.getBlockPos()) != manager",
            "manager.getBlockPos().distSqr(display.getBlockPos())",
            "presentationIdentity(display).filter(state.identity::equals).isEmpty()",
            "if (front <= 0.05) return false;",
            "return projectedPixels >= 4;",
        ] {
            assert!(
                body.contains(anchor),
                "{}: lost actual scheduling authority {anchor}",
                g.id
            );
        }
        assert_eq!(
            body.contains("public static Optional<SFMValue> valueFor("),
            g.actions
        );
    }
    assert!(!body.contains("{%"));
    assert!(!body.contains("ProcessBuilder"));
}

#[test]
fn runtime_all_twenty_real_git_cells_reconstruct_two_full_raw_bodies_and_eighteen_omissions()
-> Result<()> {
    let f = Fixture::load()?;
    let logical = format!("platform/minecraft/{PATH}");
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
                &logical,
            ])
            .output()?;
        ensure!(
            output.status.success() && output.stdout.len() <= 1024 && output.stderr.len() <= 4096,
            "bounded local runtime tree read failed"
        );
        let expected = if present(name) {
            format!("100644 blob {RAW_OID}\t{logical}\n")
        } else {
            String::new()
        };
        assert_eq!(
            std::str::from_utf8(&output.stdout)?,
            expected,
            "raw runtime membership: {name}"
        );
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("fixed target missing"))?;
        // This is an isolated closed source-proof context, not the historical
        // global feature inventory and not complete multiplayer wire acceptance.
        let context = f.core.context(
            target,
            if present(name) {
                PROFILES[8].flags
            } else {
                &[]
            },
        )?;
        let body = f.render(&context)?;
        if present(name) {
            assert_eq!(body.as_deref().map(str::as_bytes), Some(f.raw.as_slice()));
            counts.0 += 1;
        } else {
            assert!(body.is_none());
            counts.1 += 1;
        }
    }
    assert_eq!(counts, (2, 18));
    Ok(())
}

#[test]
fn runtime_eighteen_dependency_closed_source_masks_match_exact_reviewed_outputs() -> Result<()> {
    let f = Fixture::load()?;
    let mut outputs = 0;
    for target in D2 {
        for g in &PROFILES {
            let context = f.core.context(target, g.flags)?;
            let body = f
                .render(&context)?
                .ok_or_else(|| eyre::eyre!("enabled runtime omitted"))?;
            assert_eq!(body.len(), g.bytes, "{} {target}", g.id);
            assert_eq!(sha256(body.as_bytes()), g.digest, "{} {target}", g.id);
            owner_shape(&body, g);
            let mut description = context.clone();
            description.environment = "release".to_owned();
            description.preset = "metadata-is-not-authority".to_owned();
            description.projection_key = "independent/runtime-source".to_owned();
            assert_eq!(f.render(&description)?, Some(body));
            outputs += 1;
        }
        let base = f.core.context(target, PROFILES[0].flags)?;
        for unavailable in [
            T,
            A,
            R,
            M,
            "client_program_signing",
            "client_manager_gui",
            "touch_display_terminal_mount",
            "mod_event_filtering",
        ] {
            assert!(
                !base.features[unavailable],
                "frame base forces {unavailable}"
            );
        }
    }
    assert_eq!(outputs, 18);
    Ok(())
}

#[test]
fn runtime_actions_off_still_requires_original_render_consent_unique_writer_and_retained_authority()
-> Result<()> {
    let f = Fixture::load()?;
    let mut masks = 0;
    for target in D2 {
        for index in [1, 4] {
            let g = &PROFILES[index];
            let context = f.core.context(target, g.flags)?;
            let body = f
                .render(&context)?
                .ok_or_else(|| eyre::eyre!("touch runtime omitted"))?;
            let evaluation = required_body(
                &body,
                "            try {\n",
                "            } catch (RuntimeException failure)",
            )?;
            assert!(evaluation.contains("Optional<ResourceLocation> evaluated = ClientFrameEvaluator.evaluate(writer.trigger(), state.frameIndex);"));
            let commit = evaluation
                .find("if (writer.renderAllowed() && writers.size() == 1\n")
                .ok_or_else(|| {
                    eyre::eyre!("actions-off commit has no real consent/sole-writer gate")
                })?;
            let texture = evaluation
                .find("state.texture = evaluated.get();")
                .ok_or_else(|| eyre::eyre!("actions-off commit is missing"))?;
            assert!(commit < texture);
            assert_eq!(evaluation.matches("state.texture =").count(), 1);
            assert!(
                evaluation.contains("state.frameIndex++;")
                    && evaluation.contains("state.evaluations++;")
            );
            assert!(!evaluation.contains("ACTIONS.invoke") && !evaluation.contains("state.values"));
            let candidates = required_body(
                &body,
                "    private static List<Writer> candidates(",
                "    private static boolean requestsRender(",
            )?;
            for anchor in [
                "consent().execution(compiled.identity()",
                "boolean renderRequested = requestsRender(frame.block());",
                "? consent().evaluate(compiled.identity(), ClientProgramConsentGate.RENDER",
                "rendering != null && rendering.allowed()",
                "if (!execution.allowed()) continue;",
            ] {
                assert!(candidates.contains(anchor));
            }
            assert!(body.contains("if (liveIdentityFor(state.identity).isEmpty()\n"));
            assert!(body.contains(
                "|| presentationIdentity(display).filter(state.identity::equals).isEmpty())"
            ));
            assert!(body.contains("FRAMES.remove(display);"));
            // Render-off keeps the actual scheduler/counters but can never
            // manufacture a render request or approved writer.
            if !g.render {
                assert!(body.contains(
                    "private static boolean requestsRender(Block block) {\n        return false;"
                ));
            }
            masks += 1;
        }
    }
    assert_eq!(masks, 4);
    Ok(())
}

#[test]
fn runtime_frame_only_preserves_exact_live_identity_private_world_and_weak_cache_lifetime_boundary()
-> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        for g in &PROFILES {
            let context = f.core.context(target, g.flags)?;
            let body = f
                .render(&context)?
                .ok_or_else(|| eyre::eyre!("runtime omitted"))?;
            for anchor in [
                "if (!minecraft.isSameThread()) return Optional.empty();",
                "world.hasChunkAt(expected.managerPosition())",
                "world.getBlockEntity(expected.managerPosition()) instanceof ClientManagerBlockEntity manager",
                "manager.isRemoved()) return Optional.empty();",
                "return identityFor(manager).filter(expected::equals);",
                "previous.snapshotRevision() == manager.clientSnapshotRevision()",
                "world.equals(COMPILED_WORLDS.get(manager))",
                "COMPILE_COUNTS.merge(manager, 1, Integer::sum);",
                "ClientManagerLoadedRegistry.clear(world);",
                "if (world == activeWorld) clearWorld();",
                "COMPILED.clear();",
                "COMPILED_WORLDS.clear();",
                "COMPILE_COUNTS.clear();",
                "server != null && server.isSingleplayer() && !server.isPublished()",
            ] {
                assert!(
                    body.contains(anchor),
                    "preserved real runtime boundary: {anchor}"
                );
            }
            let identity = required_body(
                &body,
                "    public static Optional<ClientProgramIdentity> identityFor(",
                "    /** Resolves authority",
            )?;
            let compiled = required_body(
                &body,
                "    private static Compiled compiled(",
                "    private static Compiled compile(",
            )?;
            assert!(!identity.contains("observeWorld(") && !compiled.contains("observeWorld("));
            assert_eq!(body.contains("        observeWorld(world);"), g.touch);
            assert_eq!(body.contains("        activeWorld = world;"), g.touch);
            // Existing identity-only weak caches do NOT gain eager clearing.
            // This test freezes the honest boundary rather than inventing a
            // clearWorld call in identityFor/compile for a closure claim.
            if !g.touch {
                assert!(!body.contains("activeWorld = world;"));
                assert!(!body.contains("FRAMES") && !body.contains("WORK_BUDGET"));
            }
        }
    }
    Ok(())
}

#[test]
fn runtime_missing_prerequisites_unknown_flags_and_other_eight_targets_refuse_without_auto_expansion()
-> Result<()> {
    let f = Fixture::load()?;
    let mut prerequisites = 0;
    for target in D2 {
        for owner in [L, A, R, M] {
            for prerequisite in &f.core.features.0[owner].requires {
                let flags = PROFILES[8]
                    .flags
                    .iter()
                    .copied()
                    .filter(|flag| *flag != prerequisite.as_str())
                    .collect::<Vec<_>>();
                assert!(
                    f.core.context(target, &flags).is_err(),
                    "accepted missing real {owner} prerequisite {prerequisite}"
                );
                prerequisites += 1;
            }
        }
        for owner in [L, T, A, R, M] {
            assert!(f.core.context(target, &[owner]).is_err());
            let mut broken = f.core.context(target, PROFILES[0].flags)?;
            assert!(broken.features.remove(owner).is_some());
            if owner == L {
                assert!(select_core_inputs(&f.core.metadata, &broken, &f.inventory()).is_err());
            } else {
                assert!(f.render(&broken).is_err());
            }
        }
    }
    assert_eq!(prerequisites, 30);
    let mut unsupported = 0;
    for target in &TEN[2..] {
        for g in &PROFILES {
            assert!(f.core.context(target, g.flags).is_err());
            unsupported += 1;
        }
        assert!(f.render(&f.core.context(target, &[])?)?.is_none());
    }
    assert_eq!(unsupported, 72);
    Ok(())
}

#[test]
fn runtime_raw_mutations_and_region_reconstruction_keep_no_normalization_contract() -> Result<()> {
    let f = Fixture::load()?;
    for mutated in [
        std::str::from_utf8(&f.raw)?
            .replace('\n', "\r\n")
            .into_bytes(),
        f.raw[..f.raw.len() - 1].to_vec(),
        [f.raw.as_slice(), b"\n"].concat(),
        [b" ".as_slice(), f.raw.as_slice()].concat(),
        [b"\xef\xbb\xbf".as_slice(), f.raw.as_slice()].concat(),
    ] {
        assert!(verify_raw(&mutated).is_err());
    }
    // Load already performs the 36 forward/reverse byte-and-occurrence checks.
    let source = std::str::from_utf8(&f.source)?;
    assert!(
        !source.contains("environment")
            && !source.contains("projection_key")
            && !source.contains("preset")
            && !source.contains("{% case minecraft_version")
    );
    let mut changed = f.source.clone();
    changed[0] = b'X';
    assert_ne!(sha256(&changed), AUTHORED_DIGEST);
    assert_eq!(source.matches(COMMON_ANCHOR).count(), 1);
    Ok(())
}

#[test]
fn runtime_actual_provider_apis_selected_independently_without_surrogate_or_remote_session()
-> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        let base = f.core.context(target, PROFILES[0].flags)?;
        for (path, anchors) in [
            (
                "src/main/java/ca/teamdman/sfml/program_builder/ProgramBuilder.java",
                &[
                    "forExecutionSide(ProgramExecutionSide side)",
                    "public ProgramBuildResult build(",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfml/ast/Program.java",
                &["public record Program(", "List<Trigger> triggers,"][..],
            ),
            (
                "src/main/java/ca/teamdman/sfml/ast/FrameTrigger.java",
                &[
                    "public record FrameTrigger(",
                    "Client frame body cannot execute server statement:",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/blockentity/ClientManagerBlockEntity.java",
                &[
                    "public String storedSource()",
                    "public LabelPositionHolder labels()",
                    "public @Nullable UUID worldId()",
                    "public long clientSnapshotRevision()",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/blockentity/ClientManagerLoadedRegistry.java",
                &[
                    "snapshot(Level level)",
                    "clear(Level level)",
                    "Set.copyOf(entries)",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentRuntime.java",
                &[
                    "public static ClientProgramConsentGate gate()",
                    "public static ClientProgramConsentService.Result observe(",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientProgramIdentity.java",
                &[
                    "fromStoredSourceAndBindings(",
                    "Collection<ResourceLocation> requestedCapabilities",
                    "Set.copyOf(requestedCapabilities)",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientProgramWorldIdentity.java",
                &["integrated(UUID persistedWorldId)"][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientFrameWorkBudget.java",
                &["tryAcquire(long actualRenderEpoch)", "public void clear()"][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientFrameSourceBudget.java",
                &["permits(String source)"][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientManagerTargetBindings.java",
                &[
                    "canonical(Program program, LabelPositionHolder labels)",
                    "contains(FrameTrigger trigger, LabelPositionHolder labels, BlockPos display)",
                ][..],
            ),
        ] {
            let body = f.provider(path, &base)?;
            for anchor in anchors {
                assert!(body.contains(anchor), "real core API {path}: {anchor}");
            }
        }
        let evaluator = f.provider(
            "src/main/java/ca/teamdman/sfm/client/program/ClientFrameEvaluator.java",
            &base,
        )?;
        assert!(evaluator.contains(
            "Optional<ResourceLocation> evaluate(FrameTrigger trigger, long frameIndex)"
        ));
        assert!(
            !evaluator.contains("public record Evaluation(") && !evaluator.contains("SFMValue")
        );
        let render_only = f.core.context(target, PROFILES[4].flags)?;
        let evaluator = f.provider(
            "src/main/java/ca/teamdman/sfm/client/program/ClientFrameEvaluator.java",
            &render_only,
        )?;
        assert!(evaluator.contains("if (statement instanceof RenderImageStatement render)"));
        assert!(evaluator.contains("if (renderAllowed) last = Optional.of(render.image());"));
        assert!(!evaluator.contains("public record Evaluation("));
        let actions = f.core.context(target, PROFILES[2].flags)?;
        for (path, anchors) in [
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientFrameEvaluator.java",
                &[
                    "public record Evaluation(",
                    "Actions actions, boolean renderAllowed)",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientProgramActionManifest.java",
                &[
                    "ClientProgramActionManifest compile(",
                    "Set<ResourceLocation> capabilities,",
                    "permits(",
                    "static String key(",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionAuthorizationService.java",
                &["public static SFMClientActionAuthorizationService shared()"][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/action/SFMClientProgramActionDispatcher.java",
                &["public SFMValue invoke("][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/registry/SFMClientActions.java",
                &["programmaticBinding("][..],
            ),
        ] {
            let body = f.provider(path, &actions)?;
            for anchor in anchors {
                assert!(
                    body.contains(anchor),
                    "real selected action API {path}: {anchor}"
                );
            }
        }
    }
    // Real manager BE promotion is a deliberate prerequisite of this test.
    // No stage fallback supplies it; missing providers fail loudly. M-on
    // renders are NOT a claim that the still-missing negotiated runtime,
    // signing transport, descriptor leaves or human packet service compile.
    Ok(())
}

#[test]
fn runtime_omitted_invalid_inputs_are_unread_and_true_false_template_bits_share_real_collector()
-> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let metadata = f.bounded_metadata();
    f.write_source(&root, b"{% if features.unreviewed_owner %}\n\xff")?;
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    let mut omitted = 0;
    for target in TEN {
        let context = f.core.context(target, &[])?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selection = select_core_inputs(&metadata, &context, &inventory)?;
        assert!(selection.omitted_paths.contains(PATH));
        assert!(!collect_core_artifacts(&root, &selection, &context)?.contains_key(PATH));
        omitted += 1;
    }
    for target in D2 {
        for flags in [
            &["client_program_consent", "sfml_execution_side"][..],
            &[
                "client_manager",
                "client_program_consent",
                "disk_readonly_access",
                "sfml_execution_side",
            ][..],
        ] {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selection = select_core_inputs(&metadata, &context, &inventory)?;
            assert!(selection.omitted_paths.contains(PATH));
            assert!(!collect_core_artifacts(&root, &selection, &context)?.contains_key(PATH));
            omitted += 1;
        }
    }
    assert_eq!(omitted, 14);
    f.write_source(&root, &f.source)?;
    for template in [true, false] {
        let mut metadata = f.bounded_metadata();
        let rule = metadata
            .source_rules
            .get_mut(PATH)
            .and_then(|rules| rules.first_mut())
            .ok_or_else(|| eyre::eyre!("runtime isolated rule missing"))?;
        rule.template = template;
        for target in D2 {
            for g in &PROFILES {
                let context = f.core.context(target, g.flags)?;
                f.copy_project_inputs(&root, &metadata, &context)?;
                let selection = select_core_inputs(&metadata, &context, &inventory)?;
                let artifacts = collect_core_artifacts(&root, &selection, &context)?;
                let artifact = artifacts
                    .get(PATH)
                    .ok_or_else(|| eyre::eyre!("selected runtime artifact missing"))?;
                assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{PATH}"));
                assert!(artifact.overlay.is_none());
                let output = std::str::from_utf8(&artifact.output_bytes)?;
                let (banner, body) = output
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("runtime generated banner missing"))?;
                assert!(banner.contains("GENERATED"));
                assert_eq!(body.len(), g.bytes);
                assert_eq!(sha256(body.as_bytes()), g.digest);
            }
        }
    }
    // Isolated actual standalone inputs plus one Java source. No SFMPackets,
    // surrogate provider or full-project network-layout claim is included.
    Ok(())
}

#[test]
fn runtime_common_body_edit_reaches_both_targets_and_all_eighteen_legal_masks_only_in_fixture()
-> Result<()> {
    let f = Fixture::load()?;
    let edited = std::str::from_utf8(&f.source)?.replacen(
        COMMON_ANCHOR,
        "    public static final int MAX_FRAME_TRIGGERS = 31;",
        1,
    );
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_source(&root, edited.as_bytes())?;
    let inventory = discover_core_source_files(&root)?;
    let metadata = f.bounded_metadata();
    let mut edited_outputs = 0;
    for target in D2 {
        for g in &PROFILES {
            let context = f.core.context(target, g.flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            let artifact = artifacts
                .get(PATH)
                .ok_or_else(|| eyre::eyre!("edited runtime artifact omitted"))?;
            let output = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = output
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("edited runtime banner missing"))?;
            let original = f
                .render(&context)?
                .ok_or_else(|| eyre::eyre!("real runtime omitted"))?;
            assert_eq!(
                body,
                original.replacen(
                    COMMON_ANCHOR,
                    "    public static final int MAX_FRAME_TRIGGERS = 31;",
                    1
                )
            );
            assert_ne!(sha256(body.as_bytes()), g.digest);
            owner_shape(body, g);
            edited_outputs += 1;
        }
    }
    assert_eq!(edited_outputs, 18);
    assert_eq!(sha256(&f.core.read_source(PATH)?), AUTHORED_DIGEST);
    Ok(())
}
