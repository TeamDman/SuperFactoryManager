//! Four genuine manager/consent providers through authored-core selection.
//!
//! Frozen Git objects are bounded offline test witnesses, never production
//! source routing. The three raw leaves retain exact bytes. ConsentRuntime's
//! signer-off profile calls its genuine two-argument constructor, not a fake
//! authority/provider. Parent promotion and sparse rules precede registration.
//! Tests prove source/membership/collector contracts, not Java compilation,
//! filesystem consent effects, UI, chunk lifecycle, signing or runtime.
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

const LEDGER: &str = "docs/tasks/sfm-core-manager-consent-providers-slice.json";
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
    authored_bytes: usize,
    authored_digest: &'static str,
    owner: &'static str,
    edit_anchor: &'static str,
}
const GOLDENS: [Golden; 4] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/blockentity/ClientManagerLoadedRegistry.java",
        oid: "2b3aea64eada4ec895bbb42c85bd505a1d190fdf",
        bytes: 1325,
        digest: "sha256:4747d24c589691198da6927de3807c947108a8eb07e3bbc4760d3b91a86e8186",
        lf: 35,
        authored_bytes: 1325,
        authored_digest: "sha256:4747d24c589691198da6927de3807c947108a8eb07e3bbc4760d3b91a86e8186",
        owner: "client_manager",
        edit_anchor: "public final class ClientManagerLoadedRegistry {",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentService.java",
        oid: "391ecbfe3c703be2508c4a9ecc206c51daeca32d",
        bytes: 9295,
        digest: "sha256:97e618975be5f86b89e704f5d7af7a3f5aa9254c3f151787082499612213a590",
        lf: 176,
        authored_bytes: 9295,
        authored_digest: "sha256:97e618975be5f86b89e704f5d7af7a3f5aa9254c3f151787082499612213a590",
        owner: "client_program_consent",
        edit_anchor: "public final class ClientProgramConsentService {",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramSignerAuthority.java",
        oid: "7ee528bb706e6972cbe259a6b40716d4dc7cb71a",
        bytes: 762,
        digest: "sha256:31efff48059f4e84a12c439257b3bc88300802f656108aab92ace79a9056f435",
        lf: 18,
        authored_bytes: 762,
        authored_digest: "sha256:31efff48059f4e84a12c439257b3bc88300802f656108aab92ace79a9056f435",
        owner: "client_program_consent",
        edit_anchor: "public interface ClientProgramSignerAuthority {",
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentRuntime.java",
        oid: "b6f700da5dd6953092e134491606c9f952f35f04",
        bytes: 1898,
        digest: "sha256:a36842374da9fcb46d5b59e95e67e8f1ad5918114ff5223a4691dd5562f2cbc2",
        lf: 39,
        authored_bytes: 2006,
        authored_digest: "sha256:740abca80575004521a6d90715dc3681994d58756a13cd682a9581b22f573405",
        owner: "client_program_consent",
        edit_anchor: "public final class ClientProgramConsentRuntime {",
    },
];
const ORIGINAL_REGION: &str =
    "                System::currentTimeMillis, ClientProgramSignerTrustRuntime.service());\n";
const TEMPLATE_REGION: &str = concat!(
    "{% if features.client_program_signing %}\n",
    "                System::currentTimeMillis, ClientProgramSignerTrustRuntime.service());\n",
    "{% else %}\n",
    "                System::currentTimeMillis);\n",
    "{% endif %}\n",
);
const OFF_REGION: &str = "                System::currentTimeMillis);\n";
const OFF_DIGEST: &str = "sha256:33af335808af1c1f509121a7f245a3eb1c32dc82f28e762a1775a75d797acf32";
const ORIGINAL_DIGEST: &str =
    "sha256:e1c5bd4607cd946ddf5bc3fc197d7006a7b75e3e13a874ab458b661a83a24eb0";
const TEMPLATE_DIGEST: &str =
    "sha256:0e6a53dfba84a68f5d36c2660392b782db68aa8980917a3f9e59ecbb45d852d4";

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    contract_changes: bool,
    definitions: BTreeMap<String, Definition>,
    context_commits: BTreeMap<String, String>,
    files: Vec<SourceEvidence>,
    runtime_guard: RuntimeGuard,
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
    source_rule: InputVariant,
    witnesses: BTreeMap<String, Option<String>>,
    common_edit_anchor: String,
}
#[derive(Facet)]
struct RuntimeGuard {
    path: String,
    owner: String,
    original_region: String,
    template_region: String,
    original_bytes: usize,
    original_sha256: String,
    template_bytes: usize,
    template_sha256: String,
    signer_off_bytes: usize,
    signer_off_sha256: String,
    changes_outside_region: bool,
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
            ledger.schema == "sfm:core-manager-consent-providers-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && ledger.files.len() == 4
                && ledger.definitions.len() == 11
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
                        .collect(),
            "manager/consent bounded source evidence changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let original = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing original consent contract: {name}"))?;
            let current = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing actual consent contract: {name}"))?;
            ensure!(
                original.supported_targets == support
                    && original.requires == requires
                    && current.supported_targets == support
                    && current.requires == requires,
                "original/current manager/consent contract changed: {name}"
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
                    && evidence.authored_bytes == g.authored_bytes
                    && evidence.authored_sha256 == g.authored_digest
                    && evidence.cr_count == 0
                    && evidence.lf_count == g.lf
                    && evidence.final_lf
                    && evidence.common_edit_anchor == g.edit_anchor
                    && evidence.witnesses == expected_witnesses(g),
                "immutable manager/consent raw identity/membership changed"
            );
            verify_raw(&raw[g.oid], g)?;
            let source = core.read_source(g.path)?;
            ensure!(
                source.len() == g.authored_bytes
                    && sha256(&source) == g.authored_digest
                    && !source.contains(&b'\r')
                    && source.ends_with(b"\n"),
                "authored manager/consent template identity changed"
            );
            std::str::from_utf8(&source)?;
            let rules = core.metadata.source_rules.get(g.path).ok_or_else(|| {
                eyre::eyre!("root must first promote sparse consent rule: {}", g.path)
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
                "manager/consent membership must retain exact existing owner and D2 support"
            );
            sources.insert(g.path.to_owned(), source);
        }
        let guard = &ledger.runtime_guard;
        ensure!(
            guard.path == GOLDENS[3].path
                && guard.owner == "client_program_signing"
                && guard.original_region == ORIGINAL_REGION
                && guard.template_region == TEMPLATE_REGION
                && guard.original_bytes == 87
                && guard.original_sha256 == ORIGINAL_DIGEST
                && guard.template_bytes == 195
                && guard.template_sha256 == TEMPLATE_DIGEST
                && guard.signer_off_bytes == 1855
                && guard.signer_off_sha256 == OFF_DIGEST
                && !guard.changes_outside_region,
            "consent-runtime sole guarded region changed"
        );
        ensure!(
            sha256(ORIGINAL_REGION.as_bytes()) == ORIGINAL_DIGEST
                && sha256(TEMPLATE_REGION.as_bytes()) == TEMPLATE_DIGEST,
            "constructor region byte identities changed"
        );
        let original = std::str::from_utf8(&raw[GOLDENS[3].oid])?;
        let authored = std::str::from_utf8(&sources[GOLDENS[3].path])?;
        ensure!(
            original.matches(ORIGINAL_REGION).count() == 1
                && authored.matches(TEMPLATE_REGION).count() == 1
                && authored == original.replacen(ORIGINAL_REGION, TEMPLATE_REGION, 1),
            "changes outside the one approved signer constructor region"
        );
        ensure!(
            authored
                .lines()
                .filter(|line| line.starts_with("{%"))
                .count()
                == 3
                && authored.bytes().filter(|byte| *byte == b'\n').count() == 43,
            "runtime must contain only the approved if/else/endif guard"
        );
        for g in &GOLDENS[..3] {
            ensure!(
                sources[g.path] == raw[g.oid],
                "raw leaf changed: {}",
                g.path
            );
        }
        let off = original.replacen(ORIGINAL_REGION, OFF_REGION, 1);
        ensure!(
            off.len() == 1855
                && sha256(off.as_bytes()) == OFF_DIGEST
                && off.bytes().filter(|byte| *byte == b'\n').count() == 39,
            "counterfactual genuine two-argument constructor output changed"
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
                "unaccounted consent source omission"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.sources[path])?,
            context,
        )?))
    }
    fn expected_body(&self, index: usize, context: &ProjectionContext) -> Result<String> {
        let original = std::str::from_utf8(&self.raw[GOLDENS[index].oid])?;
        if index == 3 && !context.features["client_program_signing"] {
            Ok(original.replacen(ORIGINAL_REGION, OFF_REGION, 1))
        } else {
            Ok(original.to_owned())
        }
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
            .ok_or_else(|| eyre::eyre!("real consent provider omitted: {path}"))?;
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
                    .ok_or_else(|| eyre::eyre!("fixed consent source lacks a parent"))?,
            )?;
            fs::write(destination, source)?;
        }
        Ok(())
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
        "raw manager/consent bytes changed; no normalization is approved"
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
fn all_eighty_manager_consent_cells_match_real_git_membership_and_rendered_goldens() -> Result<()> {
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
            output.status.success() && output.stdout.len() <= 8192 && output.stderr.len() <= 4096,
            "bounded local frozen manager/consent tree read failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed manager/consent tree record"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|expected| expected == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected manager/consent tree member"
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
            "real immutable four-path membership: {name}"
        );
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("fixed target missing"))?;
        // A closed source-proof context is not a historical global flag inventory.
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
    assert_eq!(counts, (8, 72));
    Ok(())
}

#[test]
fn fifty_six_valid_cells_keep_consent_manager_and_optional_signer_independent() -> Result<()> {
    let f = Fixture::load()?;
    let mut counts = (0, 0);
    for target in D2 {
        for flags in profiles() {
            let context = f.core.context(target, &flags)?;
            let mut descriptive = context.clone();
            descriptive.environment = "release".to_owned();
            descriptive.preset = "description-is-not-consent-ownership".to_owned();
            descriptive.projection_key = "independent/consent-source".to_owned();
            for (index, g) in GOLDENS.iter().enumerate() {
                let body = f.render(g.path, &context)?;
                assert_eq!(body.is_some(), context.features[g.owner]);
                assert_eq!(body, f.render(g.path, &descriptive)?);
                if let Some(body) = body {
                    assert_eq!(body, f.expected_body(index, &context)?);
                    if index == 3 {
                        assert_eq!(
                            body.contains("ClientProgramSignerTrustRuntime.service()"),
                            context.features["client_program_signing"]
                        );
                        if !context.features["client_program_signing"] {
                            assert_eq!(sha256(body.as_bytes()), OFF_DIGEST);
                            assert!(body.contains("                System::currentTimeMillis);"));
                        }
                    }
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
                "basic consent forces {forbidden}"
            );
        }
        assert!(f.render(GOLDENS[0].path, &minimal)?.is_none());
        for g in &GOLDENS[1..] {
            assert!(f.render(g.path, &minimal)?.is_some());
        }
        let manager = f.core.context(target, &MANAGER)?;
        for forbidden in [
            "client_frame_language",
            "client_program_actions",
            "client_frame_render",
            "client_program_signing",
            "multiplayer_packets",
            "client_manager_gui",
        ] {
            assert!(
                !manager.features[forbidden],
                "loaded registry forces {forbidden}"
            );
        }
        for g in &GOLDENS {
            assert!(f.render(g.path, &manager)?.is_some());
        }
    }
    assert_eq!(counts, (38, 18));
    Ok(())
}

#[test]
fn exact_prerequisites_missing_predicates_and_other_eight_targets_refuse_without_expansion()
-> Result<()> {
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
        for owner in ["client_manager", "client_program_consent"] {
            let mut broken = f.core.context(target, &CONSENT)?;
            assert!(broken.features.remove(owner).is_some());
            assert!(select_core_inputs(&f.core.metadata, &broken, &f.inventory()).is_err());
        }
        let mut missing_signer = f.core.context(target, &CONSENT)?;
        assert!(
            missing_signer
                .features
                .remove("client_program_signing")
                .is_some()
        );
        assert!(f.render(GOLDENS[3].path, &missing_signer).is_err());
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
fn genuine_existing_identity_gate_store_and_local_none_provider_signatures_are_retained()
-> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        let context = f.core.context(target, &CONSENT)?;
        for (path, anchors) in [
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientProgramIdentity.java",
                &[
                    "public record ClientProgramIdentity(",
                    "fromStoredSourceAndBindings(",
                    "Set<ResourceLocation> requestedCapabilities",
                    "ClientProgramConsentGate.EXECUTE",
                ][..],
            ),
            (
                "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentGate.java",
                &[
                    "public final class ClientProgramConsentGate",
                    "ClientProgramConsentGate(ClientProgramConsentStore store,",
                    "BiPredicate<ClientProgramIdentity, ResourceLocation> signerAuthority",
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
                    "public synchronized void setStoppedAll(boolean stopped)",
                    "public synchronized void observe(",
                    "public synchronized List<Snapshot> snapshots()",
                    "public synchronized void save(Path destination, Set<ClientProgramIdentity> durableIdentities)",
                    "public static LoadResult load(Path path, LongSupplier clock)",
                ][..],
            ),
        ] {
            let body = f.provider(path, &context)?;
            for anchor in anchors {
                assert!(body.contains(anchor), "real provider {path}: {anchor}");
            }
        }
        let service = f
            .render(GOLDENS[1].path, &context)?
            .ok_or_else(|| eyre::eyre!("real service omitted"))?;
        assert!(
            service.contains(
                "public ClientProgramConsentService(Path destination, LongSupplier clock)"
            )
        );
        assert!(service.contains("this(destination, clock, ClientProgramSignerAuthority.NONE);"));
        let authority = f
            .render(GOLDENS[2].path, &context)?
            .ok_or_else(|| eyre::eyre!("genuine NONE authority omitted"))?;
        assert!(authority.contains("public boolean permits(ClientProgramIdentity identity, ResourceLocation capability) { return false; }"));
        assert!(authority.contains("public void revokeAll() { }"));
        assert!(
            authority.contains("public void revokeAtLocation(ClientProgramIdentity identity) { }")
        );
        let runtime = f
            .render(GOLDENS[3].path, &context)?
            .ok_or_else(|| eyre::eyre!("genuine runtime wrapper omitted"))?;
        assert!(!runtime.contains("ClientProgramSignerTrustRuntime"));
        assert!(runtime.contains(
            "FMLPaths.CONFIGDIR.get().resolve(\"sfm\").resolve(\"client-program-consents.bin\")"
        ));
        assert!(
            runtime.contains(
                "return service().observe(identity, source, canonicalBindings, versions);"
            )
        );
    }
    // No replacement ClientManagerBlockEntity or signer-trust implementation is
    // synthesized by these source tests. Real Java/provider closure is separate.
    Ok(())
}

#[test]
fn raw_mutations_refuse_and_original_recovery_durability_loaded_registry_contracts_remain()
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
    let anchors: [&[&str]; 4] = [
        &[
            "WeakHashMap<Level, Set<ClientManagerBlockEntity>>",
            "Collections.newSetFromMap(new IdentityHashMap<>())",
            "public static synchronized void add",
            "public static synchronized void remove",
            "public static synchronized Set<ClientManagerBlockEntity> snapshot",
            "Set.copyOf(entries)",
            "public static synchronized void clear",
        ],
        &[
            "if (!loaded.diagnostics().isEmpty()) loaded.store().setStoppedAll(true);",
            "gate = new ClientProgramConsentGate(loaded.store(), signerAuthority::permits);",
            "store().observe(identity, source, bindings, versions);",
            "if (!requested.created()) return new Result(Status.OK, false, durable.contains(identity), requested.state().name());",
            "var requested = gate.reopenDenied(identity, capability);",
            "try { signerAuthority.revokeAtLocation(identity); }",
            "Result stopped = save(true, \"All client programs stopped; approvals revoked\");",
            "if (!stopped.successful()) return stopped;",
            "store().setStoppedAll(false);",
            "store().save(destination, Set.copyOf(durable));",
            "Restart may restore previous decisions; retry saving before restarting",
            "if (!identity.requestedCapabilities().contains(capability))",
            "s.evidence().isPresent()",
        ],
        &[
            "boolean permits(ClientProgramIdentity identity, ResourceLocation capability);",
            "void revokeAll() throws IOException;",
            "void revokeAtLocation(ClientProgramIdentity identity) throws IOException;",
            "ClientProgramSignerAuthority NONE = new ClientProgramSignerAuthority()",
        ],
        &[
            "private static final class Holder",
            "public static ClientProgramConsentService service()",
            "public static ClientProgramConsentGate gate()",
            "versions.put(\"minecraft\", SharedConstants.getCurrentVersion().getName());",
            "versions.put(\"sfm_build\", build == null ? \"development-unversioned\" : build);",
        ],
    ];
    for (g, anchors) in GOLDENS.iter().zip(anchors) {
        let source = std::str::from_utf8(&f.sources[g.path])?;
        for anchor in anchors {
            assert!(
                source.contains(anchor),
                "preserved consent anchor: {anchor}"
            );
        }
        for forbidden in [
            "Minecraft.getInstance()",
            "ClientFrameEvaluator",
            "ProcessBuilder",
            "SFMPackets",
            "ClientManagerFrameRuntime",
        ] {
            assert!(!source.contains(forbidden));
        }
    }
    let service = std::str::from_utf8(&f.sources[GOLDENS[1].path])?;
    let resume = service
        .split("public synchronized Result resume()")
        .nth(1)
        .and_then(|tail| tail.split("public synchronized Result retrySave()").next())
        .ok_or_else(|| eyre::eyre!("original resume member boundary missing"))?;
    let revoke = resume
        .find("signerAuthority.revokeAll()")
        .ok_or_else(|| eyre::eyre!("original signer revocation before resume missing"))?;
    let unstop = resume
        .find("store().setStoppedAll(false)")
        .ok_or_else(|| eyre::eyre!("original policy unstop missing"))?;
    assert!(revoke < unstop);
    assert!(resume.contains("no approvals restored"));
    Ok(())
}

#[test]
fn omitted_manager_or_consent_inputs_are_not_read_before_membership_selection() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let metadata = f.bounded_metadata();
    for g in &GOLDENS {
        let destination = root.join(g.path);
        fs::create_dir_all(
            destination
                .parent()
                .ok_or_else(|| eyre::eyre!("fixed consent input parent missing"))?,
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
    for g in &GOLDENS[1..] {
        fs::write(root.join(g.path), &f.sources[g.path])?;
    }
    for target in D2 {
        let consent = f.core.context(target, &CONSENT)?;
        f.copy_project_inputs(&root, &metadata, &consent)?;
        let selected = select_core_inputs(&metadata, &consent, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &consent)?;
        for (index, g) in GOLDENS.iter().enumerate() {
            assert_eq!(artifacts.contains_key(g.path), index != 0);
        }
        // LoadedRegistry remains invalid bytes yet is omitted before reading.
        assert!(selected.omitted_paths.contains(GOLDENS[0].path));
    }
    // SFMPackets is deliberately absent in this bounded source fixture. These
    // source profiles are not full-project wire-family/runtime acceptance.
    Ok(())
}

#[test]
fn all_java_sources_render_without_template_bit_through_actual_fixed_core_collector() -> Result<()>
{
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let original = std::str::from_utf8(&f.sources[GOLDENS[0].path])?;
    let fixture_source = format!(
        "{original}{{% if features.client_manager %}}\n// Isolated Java rendering probe.\n{{% endif %}}\n"
    );
    fs::write(root.join(GOLDENS[0].path), &fixture_source)?;
    let mut metadata = f.bounded_metadata();
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let inventory = discover_core_source_files(&root)?;
    let mut count = 0;
    for target in D2 {
        for flags in [MANAGER.as_slice(), SIGNING.as_slice()] {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for (index, g) in GOLDENS.iter().enumerate() {
                let artifact = &artifacts[g.path];
                let output = std::str::from_utf8(&artifact.output_bytes)?;
                let (_, body) = output
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("generated Java banner missing"))?;
                if index == 0 {
                    assert_eq!(body, render_java_source(&fixture_source, &context)?);
                    assert!(body.contains("// Isolated Java rendering probe."));
                } else {
                    assert_eq!(body, f.expected_body(index, &context)?);
                }
                assert!(!body.contains("{%"));
                assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{}", g.path));
                assert!(artifact.overlay.is_none());
                count += 1;
            }
        }
    }
    assert_eq!(count, 16);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}

#[test]
fn common_edits_reach_both_targets_and_both_signer_states_without_guard_or_live_changes()
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
                "// Isolated common manager/consent edit.\n{}",
                g.edit_anchor
            ),
            1,
        );
        assert_ne!(edited, original);
        if g.path == GOLDENS[3].path {
            assert_eq!(edited.matches(TEMPLATE_REGION).count(), 1);
        }
        fs::write(root.join(g.path), &edited)?;
        edited_sources.insert(g.path.to_owned(), edited);
    }
    let inventory = discover_core_source_files(&root)?;
    let metadata = f.bounded_metadata();
    let mut outputs = 0;
    for target in D2 {
        for flags in [MANAGER.as_slice(), SIGNING.as_slice()] {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for g in &GOLDENS {
                let output = std::str::from_utf8(&artifacts[g.path].output_bytes)?;
                let (_, body) = output
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("generated edited Java banner missing"))?;
                assert_eq!(body, render_java_source(&edited_sources[g.path], &context)?);
                assert!(body.contains("// Isolated common manager/consent edit."));
                outputs += 1;
            }
        }
    }
    assert_eq!(outputs, 16);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}
