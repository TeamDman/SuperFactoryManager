//! Four real typed program-action providers through actual authored-core selection.
//!
//! Historical Git objects are bounded offline test witnesses, not generators.
//! The original frame-on default lookup is retained. Frame-off changes only
//! the two default hooks to empty lookup, preserving existing stale refusal.
//! This is source preservation, not runtime liveness, authority or Java proof.
#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputVariant;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_program_authorization_current_contract::reviewed_pre_human_authorization;
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

const LEDGER: &str = "docs/tasks/sfm-core-program-action-dispatch-slice.json";
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const TEN: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const PROGRAM: [&str; 9] = [
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
const PROGRAM_FRAME: [&str; 10] = [
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
];
const FRAME: [&str; 5] = [
    "client_frame_language",
    "client_manager",
    "client_program_consent",
    "disk_readonly_access",
    "sfml_execution_side",
];
const DEFINITIONS: [(&str, &[&str], &[&str]); 10] = [
    (
        "client_actions",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    ("packet_values", &["1.19.2", "1.19.4"], &[]),
    ("sfml_execution_side", &["1.19.2", "1.19.4"], &[]),
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
        "packet_computation",
        &["1.19.2", "1.19.4"],
        &["packet_values", "runtime_resource_cleanup"],
    ),
    ("runtime_resource_cleanup", &["1.19.2", "1.19.4"], &[]),
    (
        "disk_readonly_access",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
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
        "client_frame_language",
        &["1.19.2", "1.19.4"],
        &[
            "client_manager",
            "sfml_execution_side",
            "client_program_consent",
        ],
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
struct Golden {
    path: &'static str,
    oid: &'static str,
    raw_bytes: usize,
    raw_digest: &'static str,
    lf: usize,
    authored_bytes: usize,
    authored_digest: &'static str,
    declaration: &'static str,
}
const GOLDENS: [Golden; 4] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/action/SFMClientProgramActionDispatcher.java",
        oid: "9b4159536540e136d6116245bfb473674f79e1c3",
        raw_bytes: 3525,
        raw_digest: "sha256:a0245427dc691a9c8051dda10075bb39e11021c252e863011450094d43d20452",
        lf: 68,
        authored_bytes: 3525,
        authored_digest: "sha256:a0245427dc691a9c8051dda10075bb39e11021c252e863011450094d43d20452",
        declaration: "public final class SFMClientProgramActionDispatcher",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionAuthorizationService.java",
        oid: "bf5568b50d418dff6423076f1126f7bba7bc57b8",
        raw_bytes: 16594,
        raw_digest: "sha256:8af03f5343ecf539ace0cee50124d1248a446e6358ca0ff1315449f0c9711d00",
        lf: 312,
        authored_bytes: 16892,
        authored_digest: "sha256:c1b41edbd8696d3cbe7fff2aa55b368be711c4c435c3cdec9979b764b07ac509",
        declaration: "public final class SFMClientActionAuthorizationService",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionProgrammaticHandler.java",
        oid: "48e1c9ba2f1a3953f2efe88d7a14dc538a980ba7",
        raw_bytes: 346,
        raw_digest: "sha256:2c8f6e2c3ea53846e91b27d1d4b05647c5ec25fc0f79b92112d76d48abcc2dd3",
        lf: 9,
        authored_bytes: 346,
        authored_digest: "sha256:2c8f6e2c3ea53846e91b27d1d4b05647c5ec25fc0f79b92112d76d48abcc2dd3",
        declaration: "public interface SFMClientActionProgrammaticHandler",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionProgrammaticContext.java",
        oid: "9018bd72ea6cd548569b27d3470681ca74637dc5",
        raw_bytes: 783,
        raw_digest: "sha256:e01803f3974296f43272079993d7f77be795d654229e70af88c7ffac9182ceb5",
        lf: 20,
        authored_bytes: 783,
        authored_digest: "sha256:e01803f3974296f43272079993d7f77be795d654229e70af88c7ffac9182ceb5",
        declaration: "public record SFMClientActionProgrammaticContext(",
    },
];
const AUTH: usize = 1;
const AUTH_OFF: (usize, usize, &str) = (
    16501,
    311,
    "sha256:7ddd14213ad96da7d85dac57e3e5621e01cffc29d8a504afb4fe4e9b173901e5",
);
const REGIONS: [(&str, &str, &str, usize, &str, usize, &str); 3] = [
    (
        "runtime_import",
        "import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;\n",
        "",
        65,
        "sha256:be8bdb0f8809c8330dc850750e633689740135522e03e7e2fc1788ccbaf71515",
        0,
        "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    ),
    (
        "shared_default_lookup",
        "            ClientManagerFrameRuntime::liveIdentityFor\n",
        "            expected -> Optional.empty()\n",
        55,
        "sha256:55e247999e843a0eb0fc235eaa012ae0f99240b1e7b54bb80998b2af82a1bdd3",
        41,
        "sha256:24206578483b55c84d8c076f2fe8bcb934dec3bb5b327c0ce926013b5a469aa5",
    ),
    (
        "three_argument_default_lookup",
        "        this(descriptors, budget, secondWindow, ClientManagerFrameRuntime::liveIdentityFor);\n",
        "        this(descriptors, budget, secondWindow, expected -> Optional.empty());\n",
        93,
        "sha256:4575c7213954975c5e8ed304030c1cbe49ef0a2b2f29f5994132e40b0a58dd32",
        79,
        "sha256:a87f3b8f852afa2fcbbb18fe7cf7c1e705a6a04846777baf2e7acd14001bf285",
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
    authorization_variant: AuthorizationEvidence,
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
    raw_cr_count: usize,
    raw_lf_count: usize,
    raw_final_lf: bool,
    authored_bytes: usize,
    authored_sha256: String,
    witnesses: BTreeMap<String, Option<String>>,
    source_rule: InputVariant,
}
#[derive(Facet)]
struct AuthorizationEvidence {
    owner: String,
    on: BodyEvidence,
    off: BodyEvidence,
    regions: Vec<RegionEvidence>,
}
#[derive(Facet)]
struct BodyEvidence {
    bytes: usize,
    sha256: String,
    lf_count: usize,
    historical: bool,
}
#[derive(Facet)]
struct RegionEvidence {
    name: String,
    original: String,
    replacement: String,
    on: String,
    raw_bytes: usize,
    raw_sha256: String,
    off_bytes: usize,
    off_sha256: String,
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
            ledger.schema == "sfm:core-program-action-dispatch-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && ledger.definitions.len() == 10
                && ledger.files.len() == 4
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
                        .collect(),
            "bounded action-dispatch preservation scope changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let original = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing original dispatch contract: {name}"))?;
            let actual = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing actual dispatch contract: {name}"))?;
            ensure!(
                original.supported_targets == support
                    && original.requires == requires
                    && actual.supported_targets == support
                    && actual.requires == requires,
                "original/current dispatch contract changed: {name}"
            );
        }
        let variant = &ledger.authorization_variant;
        ensure!(
            variant.owner == "client_frame_language"
                && variant.regions.len() == 3
                && variant.on.bytes == GOLDENS[AUTH].raw_bytes
                && variant.on.sha256 == GOLDENS[AUTH].raw_digest
                && variant.on.lf_count == GOLDENS[AUTH].lf
                && variant.on.historical
                && variant.off.bytes == AUTH_OFF.0
                && variant.off.lf_count == AUTH_OFF.1
                && variant.off.sha256 == AUTH_OFF.2
                && !variant.off.historical,
            "reviewed default lookup variant changed"
        );
        for (
            (name, original, replacement, raw_bytes, raw_digest, off_bytes, off_digest),
            evidence,
        ) in REGIONS.iter().zip(&variant.regions)
        {
            ensure!(
                evidence.name == *name
                    && evidence.original == *original
                    && evidence.on == *original
                    && evidence.replacement == *replacement
                    && evidence.raw_bytes == *raw_bytes
                    && evidence.raw_sha256 == *raw_digest
                    && evidence.off_bytes == *off_bytes
                    && evidence.off_sha256 == *off_digest
                    && original.len() == *raw_bytes
                    && sha256(original.as_bytes()) == *raw_digest
                    && replacement.len() == *off_bytes
                    && sha256(replacement.as_bytes()) == *off_digest,
                "exact default lookup region fingerprint changed"
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
                    && evidence.owner == "client_program_actions"
                    && evidence.raw_oid == g.oid
                    && evidence.raw_bytes == g.raw_bytes
                    && evidence.raw_sha256 == g.raw_digest
                    && evidence.raw_cr_count == 0
                    && evidence.raw_lf_count == g.lf
                    && evidence.raw_final_lf
                    && evidence.authored_bytes == g.authored_bytes
                    && evidence.authored_sha256 == g.authored_digest
                    && evidence.witnesses
                        == CONTEXTS
                            .into_iter()
                            .map(|(name, _)| (
                                name.to_owned(),
                                present(name).then(|| g.oid.to_owned())
                            ))
                            .collect(),
                "immutable dispatch raw/source identity changed"
            );
            verify_raw(&raw[g.oid], g)?;
            let source = core.read_source(g.path)?;
            let reviewed = if g.path == GOLDENS[AUTH].path {
                reviewed_pre_human_authorization(std::str::from_utf8(&source)?)?.into_bytes()
            } else {
                source.clone()
            };
            ensure!(
                reviewed.len() == g.authored_bytes
                    && sha256(&reviewed) == g.authored_digest
                    && !source.contains(&b'\r')
                    && source.ends_with(b"\n"),
                "actual dispatch source differs from reviewed authored stage"
            );
            let rules = core.metadata.source_rules.get(g.path).ok_or_else(|| {
                eyre::eyre!("root must promote dispatch membership first: {}", g.path)
            })?;
            ensure!(
                rules.len() == (if g.path == GOLDENS[AUTH].path { 2 } else { 1 })
                    && rules[0] == evidence.source_rule
                    && rules[0].input == g.path
                    && rules[0].when.targets == D2
                    && rules[0].when.all_features == ["client_program_actions"]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "dispatch class owner cannot imply frame/render or expand targets"
            );
            if g.path == GOLDENS[AUTH].path {
                let mut human = evidence.source_rule.clone();
                human.when.all_features = [
                    "client_actions",
                    "packet_actions",
                    "packet_transport_private",
                ]
                .map(str::to_owned)
                .to_vec();
                human.when.none_features = vec!["client_program_actions".to_owned()];
                ensure!(
                    rules[1] == human,
                    "human selector must be exact and disjoint from program authority"
                );
            }
            sources.insert(g.path.to_owned(), source);
        }
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
                "unaccounted dispatch omission"
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
            .ok_or_else(|| eyre::eyre!("real dispatch provider omitted: {path}"))?;
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
            // Actual standalone project inputs; no fake FrameRuntime/provider.
            let bytes = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let destination = root.join(&input.input);
            if destination.exists() {
                ensure!(
                    read_bounded(&destination, 16 * 1024 * 1024)? == bytes,
                    "selected project input differs across isolated contexts"
                );
            } else {
                fs::create_dir_all(
                    destination
                        .parent()
                        .ok_or_else(|| eyre::eyre!("fixed project input lacks a parent"))?,
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
                    .ok_or_else(|| eyre::eyre!("fixed dispatch source lacks a parent"))?,
            )?;
            fs::write(destination, source)?;
        }
        Ok(())
    }
}
fn present(name: &str) -> bool {
    matches!(name, "dev/1.19.2" | "dev/1.19.4")
}
fn verify_raw(bytes: &[u8], g: &Golden) -> Result<()> {
    ensure!(
        bytes.len() == g.raw_bytes
            && sha256(bytes) == g.raw_digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && bytes.iter().filter(|byte| **byte == b'\n').count() == g.lf
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw dispatch witness changed; no normalization is approved"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn verify_human_only_authorization(f: &Fixture) -> Result<()> {
    for target in D2 {
        for mask in 0..8 {
            let mut flags = vec![
                "packet_computation",
                "packet_values",
                "runtime_resource_cleanup",
            ];
            for (index, owner) in [
                "client_actions",
                "packet_actions",
                "packet_transport_private",
            ]
            .iter()
            .enumerate()
            {
                if mask & (1 << index) != 0 {
                    flags.push(*owner);
                }
            }
            let context = f.core.context(target, &flags)?;
            for (index, g) in GOLDENS.iter().enumerate() {
                let body = f.render(g.path, &context)?;
                assert_eq!(body.is_some(), index == AUTH && mask == 7);
                if let Some(body) = body {
                    for required in [
                        "MAX_ACTIONS_PER_SECOND = 32",
                        "MAX_ACTION_BYTES_PER_SECOND = 64 * 1024",
                        "descriptor.checkInput(input)",
                        "!effectsAvailable.getAsBoolean()",
                        "SFMValueSchema.boundedEncodedBytes(input)",
                        "selectedBudget.reserve(",
                        "if (!authorization.allowed()) return new EffectAttempt(authorization, false, false)",
                    ] {
                        assert!(
                            body.contains(required),
                            "human authorization lost admission guard: {required}"
                        );
                    }
                    for forbidden in [
                        "ClientProgramIdentity",
                        "ClientProgramConsentGate",
                        "ClientManagerFrameRuntime",
                        "authorizeProgram(",
                        "performProgram(",
                        "checkProgramPermissions(",
                        "livePrograms",
                        "{%",
                    ] {
                        assert!(
                            !body.contains(forbidden),
                            "human-only artifact leaked program authority: {forbidden}"
                        );
                    }
                }
            }
            assert!(
                !context.features["client_program_actions"]
                    && !context.features["client_frame_language"]
            );
        }
    }
    Ok(())
}
fn profiles() -> Vec<Vec<&'static str>> {
    vec![
        vec![],
        FRAME.to_vec(),
        PROGRAM.to_vec(),
        PROGRAM_FRAME.to_vec(),
        vec![
            "packet_computation",
            "packet_values",
            "runtime_resource_cleanup",
        ],
        vec!["client_program_consent", "sfml_execution_side"],
    ]
}
fn auth_off(raw: &str) -> Result<String> {
    let mut expected = raw.to_owned();
    for (_, original, replacement, _, _, _, _) in REGIONS {
        ensure!(
            expected.matches(original).count() == 1,
            "default hook raw region not unique"
        );
        expected = expected.replacen(original, replacement, 1);
    }
    ensure!(
        !expected.contains("ClientManagerFrameRuntime"),
        "default runtime reference survived"
    );
    Ok(expected)
}

#[test]
fn four_action_providers_reconstruct_eighty_real_frozen_membership_cells() -> Result<()> {
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
            output.status.success() && output.stdout.len() <= 4096 && output.stderr.len() <= 4096,
            "bounded frozen action tree query failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed dispatch tree record"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|expected| expected == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected frozen dispatch path/mode/identity"
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
        assert_eq!(actual, expected, "raw membership: {name}");
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("fixed historical context lacks delimiter"))?;
        let context = f
            .core
            .context(target, if present(name) { &PROGRAM_FRAME } else { &[] })?;
        for g in &GOLDENS {
            if present(name) {
                let body = f
                    .render(g.path, &context)?
                    .ok_or_else(|| eyre::eyre!("historical typed action source omitted"))?;
                assert_eq!(body.as_bytes(), f.raw[g.oid], "full raw source: {}", g.path);
                counts.0 += 1;
            } else {
                assert!(f.render(g.path, &context)?.is_none());
                counts.1 += 1;
            }
        }
    }
    assert_eq!(counts, (8, 72));
    Ok(())
}

#[test]
fn independent_owner_masks_do_not_force_frame_render_or_catalog_names() -> Result<()> {
    let f = Fixture::load()?;
    let mut counts = (0, 0);
    for target in D2 {
        for flags in profiles() {
            let context = f.core.context(target, &flags)?;
            let mut names = context.clone();
            names.environment = "release".to_owned();
            names.preset = "dispatch-description-only".to_owned();
            names.projection_key = "independent/program-action".to_owned();
            for g in &GOLDENS {
                let body = f.render(g.path, &context)?;
                assert_eq!(body.is_some(), context.features["client_program_actions"]);
                assert_eq!(body, f.render(g.path, &names)?);
                if body.is_some() {
                    counts.0 += 1;
                } else {
                    counts.1 += 1;
                }
            }
        }
        let base = f.core.context(target, &PROGRAM)?;
        for absent in [
            "client_frame_language",
            "client_frame_render",
            "touch_display",
            "image_resources",
            "multiplayer_packets",
            "client_program_signing",
            "client_inbox",
        ] {
            assert!(
                !base.features[absent],
                "typed dispatcher requires unrelated {absent}"
            );
        }
    }
    assert_eq!(counts, (16, 32));
    Ok(())
}

#[test]
fn actual_authorization_render_has_only_three_exact_optional_default_hook_regions() -> Result<()> {
    let f = Fixture::load()?;
    let original_source =
        reviewed_pre_human_authorization(std::str::from_utf8(&f.sources[GOLDENS[AUTH].path])?)?;
    let source = original_source.as_str();
    assert_eq!(
        source
            .matches("{% if features.client_frame_language %}")
            .count(),
        3
    );
    assert_eq!(source.matches("{% else %}").count(), 2);
    assert_eq!(source.matches("{% endif %}").count(), 3);
    for (_, original, replacement, _, _, _, _) in REGIONS {
        let guard = if replacement.is_empty() {
            format!("{{% if features.client_frame_language %}}\n{original}{{% endif %}}\n")
        } else {
            format!(
                "{{% if features.client_frame_language %}}\n{original}{{% else %}}\n{replacement}{{% endif %}}\n"
            )
        };
        assert_eq!(source.matches(&guard).count(), 1);
    }
    let expected_off = auth_off(std::str::from_utf8(&f.raw[GOLDENS[AUTH].oid])?)?;
    assert_eq!(
        (expected_off.len(), sha256(expected_off.as_bytes())),
        (AUTH_OFF.0, AUTH_OFF.2.to_owned())
    );
    for target in D2 {
        for (flags, frame) in [(&PROGRAM[..], false), (&PROGRAM_FRAME[..], true)] {
            let context = f.core.context(target, flags)?;
            for (index, g) in GOLDENS.iter().enumerate() {
                let body = f
                    .render(g.path, &context)?
                    .ok_or_else(|| eyre::eyre!("owned typed source omitted"))?;
                if index == AUTH {
                    if frame {
                        assert_eq!(body.as_bytes(), f.raw[g.oid]);
                    } else {
                        assert_eq!(body, expected_off);
                        assert_eq!(body.matches("expected -> Optional.empty()").count(), 2);
                        assert!(!body.contains("ClientManagerFrameRuntime"));
                    }
                    assert!(!body.contains("{%"));
                } else {
                    assert_eq!(body.as_bytes(), f.raw[g.oid]);
                }
            }
        }
    }
    Ok(())
}

#[test]
fn provider_and_registry_signatures_are_real_and_machine_fields_remain_optional() -> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        let context = f.core.context(target, &PROGRAM)?;
        for (path, anchors) in [
            (
                "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionDescriptor.java",
                &[
                    "public record SFMClientActionDescriptor(",
                    "public record DataScope(",
                    "public InputCheck checkInput(SFMValue input)",
                    "public Optional<SFMValueSchema.Failure> checkResult(SFMValue result)",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientProgramIdentity.java",
                &[
                    "public record ClientProgramIdentity(",
                    "ProgramExecutionSide hostSide",
                    "Set<ResourceLocation> requestedCapabilities",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentGate.java",
                &[
                    "public interface Policy",
                    "public synchronized Evaluation evaluate(",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/net/SFMBoundedEffectBudget.java",
                &[
                    "public enum Result",
                    "reserve(",
                    "MAX_TRACKED_PRINCIPALS = 1_024",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/value/SFMValue.java",
                &["interface SFMValue"][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/value/SFMValueSchema.java",
                &[
                    "interface SFMValueSchema",
                    "boundedEncodedBytes(",
                    "canonicalActionJson(",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfml/ast/ProgramExecutionSide.java",
                &["public enum ProgramExecutionSide"][..],
            ),
        ] {
            let body = f.provider(path, &context)?;
            for anchor in anchors {
                assert!(body.contains(anchor), "real provider {path}: {anchor}");
            }
        }
        let human = f.core.context(target, &["client_actions"])?;
        for (path, on, off) in [
            (
                "src/main/java/ca/teamdman/sfm/client/action/SFMClientAction.java",
                &["programmaticDescriptor(", "programmaticHandler("][..],
                &[
                    "Component title()",
                    "configureCommandNode(",
                    "default int invoke(",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/registry/SFMClientActions.java",
                &["programmaticDescriptor(", "programmaticBinding("][..],
                &["commandTree()", "createContributor("][..],
            ),
        ] {
            let machine = f.provider(path, &context)?;
            let ordinary = f.provider(path, &human)?;
            for anchor in on {
                assert!(machine.contains(anchor));
                assert!(
                    !ordinary.contains(anchor),
                    "basic human registry requires machine API"
                );
            }
            for anchor in off {
                assert!(machine.contains(anchor) && ordinary.contains(anchor));
            }
        }
    }
    Ok(())
}

#[test]
fn direct_prerequisites_missing_keys_and_unsupported_targets_remain_strict() -> Result<()> {
    let f = Fixture::load()?;
    let mut missing = 0;
    for target in D2 {
        for owner in ["client_program_actions", "client_frame_language"] {
            for required in &f.core.features.0[owner].requires {
                let flags = PROGRAM_FRAME
                    .into_iter()
                    .filter(|name| *name != required.as_str())
                    .collect::<Vec<_>>();
                assert!(
                    f.core.context(target, &flags).is_err(),
                    "accepted missing {owner} prerequisite {required}"
                );
                missing += 1;
            }
        }
        let mut no_owner_key = f.core.context(target, &PROGRAM)?;
        assert_eq!(
            no_owner_key.features.remove("client_program_actions"),
            Some(true)
        );
        assert!(select_core_inputs(&f.core.metadata, &no_owner_key, &f.inventory()).is_err());
        let mut no_frame_key = f.core.context(target, &PROGRAM)?;
        assert_eq!(
            no_frame_key.features.remove("client_frame_language"),
            Some(false)
        );
        assert!(
            render_java_source(
                std::str::from_utf8(&f.sources[GOLDENS[AUTH].path])?,
                &no_frame_key
            )
            .is_err()
        );
    }
    assert_eq!(missing, 18);
    for target in &TEN[2..] {
        for flags in [&PROGRAM[..], &PROGRAM_FRAME[..]] {
            assert!(f.core.context(target, flags).is_err());
        }
        for g in &GOLDENS {
            assert!(f.render(g.path, &f.core.context(target, &[])?)?.is_none());
        }
    }
    Ok(())
}

#[test]
fn omitted_sources_are_not_read_and_java_renders_without_template_permission() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let mut metadata = f.bounded_metadata();
    for g in &GOLDENS {
        let destination = root.join(g.path);
        fs::create_dir_all(destination.parent().expect("fixed dispatch source parent"))?;
        fs::write(destination, b"{% if features.unreviewed_owner %}\n\xff")?;
    }
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    for target in D2 {
        for flags in [&[][..], &FRAME[..]] {
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
    f.write_sources(&root)?;
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    for target in D2 {
        for flags in [&PROGRAM[..], &PROGRAM_FRAME[..]] {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for g in &GOLDENS {
                let artifact = &artifacts[g.path];
                let output = std::str::from_utf8(&artifact.output_bytes)?;
                let (_, body) = output
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("generated Java banner absent"))?;
                assert_eq!(
                    body,
                    f.render(g.path, &context)?
                        .ok_or_else(|| eyre::eyre!("selected dispatch body absent"))?
                );
                assert!(!body.contains("{%"));
                assert!(artifact.overlay.is_none());
                assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{}", g.path));
            }
        }
    }
    // This bounded fixture deliberately contains no runtime or network registry;
    // collection cannot establish actual liveness or complete-project authority.
    Ok(())
}

#[test]
fn genuine_shared_edits_collect_across_two_versions_and_both_frame_masks() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    for g in &GOLDENS {
        let raw = std::str::from_utf8(&f.sources[g.path])?;
        let edited = raw.replacen(
            g.declaration,
            &format!("// Isolated shared program-action edit.\n{}", g.declaration),
            1,
        );
        assert_ne!(edited, raw);
        fs::write(root.join(g.path), edited)?;
    }
    let inventory = discover_core_source_files(&root)?;
    let metadata = f.bounded_metadata();
    let mut outputs = 0;
    for target in D2 {
        for flags in [&PROGRAM[..], &PROGRAM_FRAME[..]] {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for g in &GOLDENS {
                let output = std::str::from_utf8(&artifacts[g.path].output_bytes)?;
                let (_, body) = output
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("generated shared-edit banner absent"))?;
                let expected = std::str::from_utf8(&f.sources[g.path])?.replacen(
                    g.declaration,
                    &format!("// Isolated shared program-action edit.\n{}", g.declaration),
                    1,
                );
                assert_eq!(body, render_java_source(&expected, &context)?);
                assert!(body.contains("// Isolated shared program-action edit."));
                outputs += 1;
            }
        }
    }
    assert_eq!(outputs, 16);
    for (path, original) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *original);
    }
    Ok(())
}

#[test]
fn raw_mutations_fail_and_actual_authority_input_result_gate_and_budget_checks_are_retained()
-> Result<()> {
    let f = Fixture::load()?;
    verify_human_only_authorization(&f)?;
    for g in &GOLDENS {
        let raw = &f.raw[g.oid];
        for changed in [
            std::str::from_utf8(raw)?.replace('\n', "\r\n").into_bytes(),
            raw[..raw.len() - 1].to_vec(),
            [raw.as_slice(), b"\n"].concat(),
            [b" ".as_slice(), raw.as_slice()].concat(),
        ] {
            assert!(verify_raw(&changed, g).is_err());
        }
    }
    for target in D2 {
        for flags in [&PROGRAM[..], &PROGRAM_FRAME[..]] {
            let context = f.core.context(target, flags)?;
            let auth = f
                .render(GOLDENS[AUTH].path, &context)?
                .ok_or_else(|| eyre::eyre!("authorization source omitted"))?;
            for anchor in [
                "MAX_ACTIONS_PER_SECOND = 32",
                "MAX_ACTION_BYTES_PER_SECOND = 64 * 1024",
                "MAX_TRACES = 128",
                "PrincipalKind { HUMAN, CLIENT_PROGRAM }",
                "Optional.of(identity)",
                "Status.STALE_PROGRAM_CONTEXT",
                "livePrograms.currentIdentity(expected).filter(expected::equals).isPresent()",
                "Objects.requireNonNull(livePrograms)",
                "this.livePrograms = Objects.requireNonNull(livePrograms)",
                "descriptor.executionSide() != SFMClientActionDescriptor.ExecutionSide.CLIENT",
                "hostSide() != ProgramExecutionSide.CLIENT",
                "descriptor.checkInput(input)",
                "checkProgramPermissions(",
                "scopePolicy.permits(identity, scope)",
                "!effectsAvailable.getAsBoolean()",
                "SFMValueSchema.boundedEncodedBytes(input)",
                "CostClass.LOCAL_READ",
                "readBudget : budget",
                "selectedBudget.reserve(",
                "if (!authorization.allowed()) return new EffectAttempt(authorization, false, false)",
                "new EffectAttempt(authorization, true, effect.attempt(input))",
            ] {
                assert!(auth.contains(anchor), "authority anchor missing: {anchor}");
            }
            assert_eq!(auth.matches("!isCurrent(program.orElseThrow())").count(), 2);
            let dispatcher = f
                .render(GOLDENS[0].path, &context)?
                .ok_or_else(|| eyre::eyre!("dispatcher source omitted"))?;
            for anchor in [
                "binding.descriptor().actionId().equals(id)",
                "authorization.matchesDescriptor(",
                "authorization.authorizeProgram(",
                "if (!admitted.allowed())",
                "PrincipalKind.CLIENT_PROGRAM, Optional.of(caller)",
                "checkResult(value).isPresent()",
                "SFMValueSchema.canonicalActionJson(envelope)",
                "result(\"action_failed\"",
            ] {
                assert!(dispatcher.contains(anchor));
            }
            let invocation = f
                .render(GOLDENS[3].path, &context)?
                .ok_or_else(|| eyre::eyre!("real invocation metadata omitted"))?;
            assert!(invocation.contains("PrincipalKind.CLIENT_PROGRAM) != caller.isPresent()"));
            assert!(invocation.contains("Program principal requires its exact identity"));
            for source in [&auth, &dispatcher, &invocation] {
                assert!(!source.contains("ProcessBuilder"));
                assert!(!source.contains("Minecraft.getInstance()"));
                assert!(!source.contains("ClientManagerFrameRuntime {"));
            }
        }
    }
    Ok(())
}
