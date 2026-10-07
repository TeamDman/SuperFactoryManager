//! Frozen migration goldens for the three shared config templates.
//!
//! These tests read promoted core inputs and use actual core selection and
//! Liquid rendering. Historical Git blobs are bounded test-only witnesses,
//! never production generation inputs. Only seven exact blobs may undergo the
//! reviewed CRLF/final-LF policy. No other whitespace or tokens are rewritten.
//!
//! Source-only owner contexts do not prove complete-project wire acceptance,
//! Java compilation, config loading, terminal backends or canvas UI behavior.
//! Deliberate future core edits require reviewing the migration goldens.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
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

const LEDGER: &str = "docs/tasks/sfm-core-config-slice.json";
const CLIENT: &str = "src/main/java/ca/teamdman/sfm/common/config/SFMClientConfig.java";
const EDITOR: &str = "src/main/java/ca/teamdman/sfm/common/config/SFMClientTextEditorConfig.java";
const TRACKER: &str = "src/main/java/ca/teamdman/sfm/common/config/SFMConfigTracker.java";
const PATHS: [&str; 3] = [CLIENT, EDITOR, TRACKER];
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;
const MAX_TREE_BYTES: u64 = 16 * 1024;
const MAX_SOURCE_BYTES: u64 = 128 * 1024;
const NORMALIZATION: &str = "sfm:java_lf_final_newline@1";

const PINNED_CORE: [(&str, &str, u64); 3] = [
    (
        CLIENT,
        "sha256:84fe84d40051b8a4acf5225451fba47580370f47674a23d460b9a51a855c6c2f",
        2212,
    ),
    (
        EDITOR,
        "sha256:7baaea2fe478c3cea69d9acbe7ca2f178e18ae5c73379c370cec297044802a56",
        3866,
    ),
    (
        TRACKER,
        "sha256:854267f57d9ebd9c2e8d0817bb9d8d6b02b193e1272d791c14a2c2d821df39da",
        9143,
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
    blob: &'static str,
    digest: &'static str,
    bytes: u64,
    crlf_count: usize,
    raw_final_lf: bool,
    lf_added: bool,
    expected_digest: &'static str,
    expected_bytes: u64,
}

const RAW_SPECS: [RawSpec; 13] = [
    RawSpec {
        path: CLIENT,
        blob: "baa8be99ca10c81625dbb066eaa0703f7846fcac",
        digest: "sha256:7a1cfcd27794c4b9b2ee801b26a9b215f2260a3fe3502e895e537b4266d61749",
        bytes: 1072,
        crlf_count: 4,
        raw_final_lf: true,
        lf_added: false,
        expected_digest: "sha256:ce6f7d0a1c3574f2855ab39f4648aa4fdf6cd6e18ba044eeaee89ce3d85dea22",
        expected_bytes: 1068,
    },
    RawSpec {
        path: CLIENT,
        blob: "eb80dd0950c5cf18e27dc6939f0fa31e25b4818d",
        digest: "sha256:01e9b731620f845abbc18c1a736c784ae7ed2ae7588ffe70c593deb5e79c67a0",
        bytes: 903,
        crlf_count: 4,
        raw_final_lf: true,
        lf_added: false,
        expected_digest: "sha256:fcbff9c544672e989a8a0433f41bceede2f3e9e08c9caff9d8f707c7613e0958",
        expected_bytes: 899,
    },
    RawSpec {
        path: CLIENT,
        blob: "8311f71d4486f0df49e23d5eb1ce9a27febad705",
        digest: "sha256:b58764bdcbdd09dcd8af8093ac03efd53dd5f0c9d0cb83411489d78ccc4fa0f3",
        bytes: 891,
        crlf_count: 0,
        raw_final_lf: true,
        lf_added: false,
        expected_digest: "sha256:b58764bdcbdd09dcd8af8093ac03efd53dd5f0c9d0cb83411489d78ccc4fa0f3",
        expected_bytes: 891,
    },
    RawSpec {
        path: CLIENT,
        blob: "4c80e469c376c1baed20d51d24aaae9693f191b0",
        digest: "sha256:456bf3dc5b58755a312760280cbc8670e1ef9c46c535ee54e06fbd79568a8db1",
        bytes: 540,
        crlf_count: 13,
        raw_final_lf: true,
        lf_added: false,
        expected_digest: "sha256:00fb8d3f40169e8d57900a261b82ba520ce8312eeedf0557b402515b4f621c85",
        expected_bytes: 527,
    },
    RawSpec {
        path: CLIENT,
        blob: "1ffb03d8f79f9c05353fcc4a3cfba9e29a66912b",
        digest: "sha256:57476670078cc1fcb102da2ff459d3efd60efaf48333b97ffd8f0738bbf00d5f",
        bytes: 523,
        crlf_count: 0,
        raw_final_lf: true,
        lf_added: false,
        expected_digest: "sha256:57476670078cc1fcb102da2ff459d3efd60efaf48333b97ffd8f0738bbf00d5f",
        expected_bytes: 523,
    },
    RawSpec {
        path: EDITOR,
        blob: "ab3f3be0cba9dd3ead3d9a8c9d8a177ca28104d8",
        digest: "sha256:9f767711a26e330f50816275d5cb0c74528e7a115e4cd107c22415f137826a55",
        bytes: 2324,
        crlf_count: 43,
        raw_final_lf: true,
        lf_added: false,
        expected_digest: "sha256:b11adc86a2c83faffabd580a8b91c5cd8d8d823cb4395be2b2ac2de6c384fcca",
        expected_bytes: 2281,
    },
    RawSpec {
        path: EDITOR,
        blob: "a621347dd214438adfc3bfddc96fe6a4fa37d627",
        digest: "sha256:473d24858262d55c2d091a85c1dea46d039916b73c3e5695c7e5e3b3ffd1c44e",
        bytes: 1841,
        crlf_count: 45,
        raw_final_lf: false,
        lf_added: true,
        expected_digest: "sha256:ff6854b2a4558625716dbdb6ad0a2f5e3261747443bc93812bce800b11395bb3",
        expected_bytes: 1797,
    },
    RawSpec {
        path: EDITOR,
        blob: "ae26f51253b0258048aa0a923655141160865872",
        digest: "sha256:72a1dce770ea5849c6c0701ac4a6c550856b70e0f4b7ad172eb5505587f7d191",
        bytes: 1831,
        crlf_count: 43,
        raw_final_lf: false,
        lf_added: true,
        expected_digest: "sha256:0e8f3a0137f2fd803bb034cbda39dd3df897108352bb85d82c3fffe61b2b2822",
        expected_bytes: 1789,
    },
    RawSpec {
        path: EDITOR,
        blob: "a27e3d45f39e78468ddd13c0a45b8451710faa68",
        digest: "sha256:321a0fafa2ff0dfaa89c54930c8c5282f13512a07da49949d97625a347e3189c",
        bytes: 1822,
        crlf_count: 41,
        raw_final_lf: false,
        lf_added: true,
        expected_digest: "sha256:795be371bd79da97f8d6d55c3d3dbab6bb614f5cf4395329cf73606d60c0707f",
        expected_bytes: 1782,
    },
    RawSpec {
        path: TRACKER,
        blob: "31c63766c191ea12bc49216abdba8c8ddb421d71",
        digest: "sha256:9b652348b8ad1fb85214804787901747bf605836681f29aee5ae54c79d04e889",
        bytes: 5215,
        crlf_count: 0,
        raw_final_lf: true,
        lf_added: false,
        expected_digest: "sha256:9b652348b8ad1fb85214804787901747bf605836681f29aee5ae54c79d04e889",
        expected_bytes: 5215,
    },
    RawSpec {
        path: TRACKER,
        blob: "7c49648b0a37e450e641d14390726d13b7502332",
        digest: "sha256:072c967b0db57c296956c85efdfba74531c7d8523b7825e8861981b67cda119f",
        bytes: 4828,
        crlf_count: 0,
        raw_final_lf: true,
        lf_added: false,
        expected_digest: "sha256:072c967b0db57c296956c85efdfba74531c7d8523b7825e8861981b67cda119f",
        expected_bytes: 4828,
    },
    RawSpec {
        path: TRACKER,
        blob: "563e2ae37f17c0954e65360874cf0b8a5396256b",
        digest: "sha256:400fcda7273ad2ee68fa0d742dbe7867d47bd3149703510f12ffa39a8ee5bb53",
        bytes: 4757,
        crlf_count: 0,
        raw_final_lf: true,
        lf_added: false,
        expected_digest: "sha256:400fcda7273ad2ee68fa0d742dbe7867d47bd3149703510f12ffa39a8ee5bb53",
        expected_bytes: 4757,
    },
    RawSpec {
        path: TRACKER,
        blob: "9ff571eb8b41bc72a089c210a893722cc04e15d6",
        digest: "sha256:92b5077fe7d98716d9108bc9d4b102e673cac6b772982eb5a60af96185343dd8",
        bytes: 4935,
        crlf_count: 0,
        raw_final_lf: true,
        lf_added: false,
        expected_digest: "sha256:92b5077fe7d98716d9108bc9d4b102e673cac6b772982eb5a60af96185343dd8",
        expected_bytes: 4935,
    },
];

// Parse only the executable evidence subset; prose is not configuration.
#[derive(Debug, Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    owners: BTreeMap<String, OwnerContract>,
    normalization: NormalizationContract,
    files: Vec<AuthoredFile>,
}

#[derive(Debug, Facet)]
struct OwnerContract {
    support: Vec<String>,
    requires: Vec<String>,
}

#[derive(Debug, Facet)]
struct NormalizationContract {
    policy: String,
    approved_git_blobs: Vec<String>,
    crlf_to_lf_only: bool,
    append_final_lf: bool,
    other_whitespace_changes: bool,
}

#[derive(Debug, Facet)]
struct AuthoredFile {
    intended_core_path: String,
    stage_sha256: String,
    stage_bytes: u64,
    membership: String,
    witnesses: Vec<Witness>,
}

#[derive(Debug, Facet)]
struct Witness {
    context: String,
    git_blob: String,
    raw_sha256: String,
    raw_bytes: u64,
    crlf_count: usize,
    raw_final_lf: bool,
    lf_added: bool,
    expected_sha256: String,
    expected_bytes: u64,
    normalization: String,
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
            .wrap_err("cannot parse bounded config source evidence")?;
        let sources = PATHS
            .into_iter()
            .map(|path| Ok((path.to_owned(), shared.read_source(path)?)))
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
            self.ledger.schema == "sfm:core-config-slice@1",
            "config source schema changed"
        );
        let contexts = PINNED_CONTEXTS
            .into_iter()
            .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == contexts,
            "exact twenty contexts changed"
        );
        ensure!(
            self.ledger.files.len() == 3
                && self
                    .ledger
                    .files
                    .iter()
                    .map(|file| file.intended_core_path.as_str())
                    .collect::<BTreeSet<_>>()
                    == BTreeSet::from(PATHS),
            "exact three config paths changed"
        );
        ensure!(
            self.ledger
                .owners
                .keys()
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == BTreeSet::from([
                    "command_history",
                    "terminal_remote",
                    "terminal_vox",
                    "canvas_text_editor",
                    "canvas_pointer_defaults",
                ]),
            "config owners widened"
        );
        let all_targets = SUPPORTED_TARGETS
            .into_iter()
            .map(|(id, _)| id)
            .collect::<Vec<_>>();
        for (name, support, requires) in [
            (
                "command_history",
                &["1.19.2", "1.19.4"][..],
                &["typed_command_palette"][..],
            ),
            ("terminal_remote", all_targets.as_slice(), &[][..]),
            ("terminal_vox", &["1.19.2", "1.19.4"][..], &[][..]),
            ("canvas_text_editor", all_targets.as_slice(), &[][..]),
            (
                "canvas_pointer_defaults",
                &["1.19.2", "1.19.4"][..],
                &["canvas_text_editor"][..],
            ),
        ] {
            let owner = self
                .ledger
                .owners
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing owner"))?;
            let registered = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("unregistered config owner"))?;
            ensure!(
                owner.support
                    == support
                        .iter()
                        .map(|value| (*value).to_owned())
                        .collect::<Vec<_>>()
                    && owner.requires
                        == requires
                            .iter()
                            .map(|value| (*value).to_owned())
                            .collect::<Vec<_>>()
                    && owner.support == registered.supported_targets
                    && owner.requires == registered.requires,
                "actual config owner support/prerequisites changed"
            );
        }
        let approved = RAW_SPECS
            .into_iter()
            .filter(|spec| spec.crlf_count > 0 || spec.lf_added)
            .map(|spec| spec.blob)
            .collect::<BTreeSet<_>>();
        let declared = self
            .ledger
            .normalization
            .approved_git_blobs
            .iter()
            .map(String::as_str)
            .collect::<BTreeSet<_>>();
        ensure!(
            approved.len() == 7
                && declared == approved
                && self.ledger.normalization.approved_git_blobs.len() == 7
                && self.ledger.normalization.policy == NORMALIZATION
                && self.ledger.normalization.crlf_to_lf_only
                && self.ledger.normalization.append_final_lf
                && !self.ledger.normalization.other_whitespace_changes,
            "config normalization scope broadened"
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
            .find(|(candidate, _, _)| *candidate == path)
            .ok_or_else(|| eyre::eyre!("unreviewed core config file"))?;
        let source = self
            .sources
            .get(path)
            .ok_or_else(|| eyre::eyre!("missing promoted config source"))?;
        let reviewed = reviewed_original_config(path, source)?;
        ensure!(
            file.stage_sha256 == digest
                && file.stage_bytes == bytes
                && reviewed.len() as u64 == bytes
                && sha256(&reviewed) == digest
                && file.membership == "unconditional_shared_class",
            "promoted core config golden changed; deliberate edits need review"
        );
        let mut seen = BTreeSet::new();
        for witness in &file.witnesses {
            ensure!(
                contexts.contains_key(&witness.context) && seen.insert(&witness.context),
                "unknown/repeated config witness"
            );
            let blob = expected_blob(path, &witness.context)?;
            let spec = raw_spec(path, blob)?;
            let expected = expected_body(path, blob, self.blob(blob)?)?;
            ensure!(
                witness.git_blob == blob
                    && witness.raw_sha256 == spec.digest
                    && witness.raw_bytes == spec.bytes
                    && witness.crlf_count == spec.crlf_count
                    && witness.raw_final_lf == spec.raw_final_lf
                    && witness.lf_added == spec.lf_added
                    && witness.expected_sha256 == spec.expected_digest
                    && witness.expected_bytes == spec.expected_bytes
                    && witness.expected_sha256 == sha256(&expected)
                    && witness.normalization
                        == if spec.crlf_count > 0 || spec.lf_added {
                            NORMALIZATION
                        } else {
                            "none"
                        },
                "raw config/normalization witness changed"
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
            .ok_or_else(|| eyre::eyre!("missing raw config witness"))
    }

    /// Uses the real source feature graph, not a fabricated whole-wire bundle.
    fn context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        let enabled = feature_closure(&self.shared, requested)?;
        let names = enabled.iter().map(String::as_str).collect::<Vec<_>>();
        self.shared.context(target, &names)
    }

    fn render_sources(&self, context: &ProjectionContext) -> Result<BTreeMap<String, String>> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let mut bodies = BTreeMap::new();
        for path in PATHS {
            ensure!(
                selection
                    .inputs
                    .get(path)
                    .is_some_and(|input| input.input == path),
                "shared config omitted or historically routed"
            );
            let source = self
                .sources
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing core config source"))?;
            bodies.insert(
                path.to_owned(),
                render_java_source(std::str::from_utf8(source)?, context)?,
            );
        }
        ensure!(
            bodies.len() == PATHS.len()
                && PATHS
                    .into_iter()
                    .all(|path| !selection.omitted_paths.contains(path)),
            "config cohort membership changed"
        );
        Ok(bodies)
    }
}

fn inventory() -> BTreeSet<String> {
    PATHS.into_iter().map(str::to_owned).collect()
}

// Preserve the original full-template pin. Only the three exact terminal
// config guards may differ; actual current bytes remain the rendering input.
fn reviewed_original_config(path: &str, current: &[u8]) -> Result<Vec<u8>> {
    if path != CLIENT {
        return Ok(current.to_vec());
    }
    ensure!(
        current.len() == 2407
            && sha256(current)
                == "sha256:94f3c7b7ac418e1acd359882274ea0e95b8b1bd5e0da6d41edf93caa215d3e58",
        "current client config changed outside reviewed terminal consumer guards"
    );
    let text = std::str::from_utf8(current)?;
    let after = "{% if features.terminal_remote or features.terminal_vox or features.terminal_vox_runtime or features.terminal_properties %}";
    ensure!(
        text.matches(after).count() == 3,
        "reviewed terminal config guard count changed"
    );
    Ok(text
        .replace(
            after,
            "{% if features.terminal_remote or features.terminal_vox %}",
        )
        .into_bytes())
}

fn terminal_config_enabled(context: &ProjectionContext) -> Result<bool> {
    Ok(enabled(context, "terminal_remote")?
        || enabled(context, "terminal_vox")?
        || enabled(context, "terminal_vox_runtime")?
        || enabled(context, "terminal_properties")?)
}

fn feature_closure(shared: &CoreTestFixture, requested: &[&str]) -> Result<BTreeSet<String>> {
    ensure!(
        shared.features.0.len() <= 256,
        "source registry exceeds test bound"
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
            .ok_or_else(|| eyre::eyre!("unknown source feature"))?;
        for required in &definition.requires {
            if enabled.insert(required.clone()) {
                pending.push(required.clone());
            }
        }
    }
    Ok(enabled)
}

fn raw_spec(path: &str, blob: &str) -> Result<RawSpec> {
    RAW_SPECS
        .into_iter()
        .find(|spec| spec.path == path && spec.blob == blob)
        .ok_or_else(|| eyre::eyre!("unreviewed raw config witness"))
}

fn d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}

fn forge(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1")
}

fn old_tracker(target: &str) -> bool {
    matches!(
        target,
        "1.19.2" | "1.19.4" | "1.20" | "1.20.1" | "1.20.2" | "1.20.3" | "1.20.4"
    )
}

fn expected_blob(path: &str, context: &str) -> Result<&'static str> {
    let (kind, target) = context
        .split_once('/')
        .ok_or_else(|| eyre::eyre!("invalid witness context"))?;
    ensure!(
        ["dev", "release"].contains(&kind)
            && SUPPORTED_TARGETS.into_iter().any(|(id, _)| id == target),
        "unreviewed witness context"
    );
    let development = kind == "dev";
    Ok(match (path, target) {
        (CLIENT, _) if development && d2(target) => "baa8be99ca10c81625dbb066eaa0703f7846fcac",
        (CLIENT, "1.20" | "1.20.1") if development => "eb80dd0950c5cf18e27dc6939f0fa31e25b4818d",
        (CLIENT, _) if development => "8311f71d4486f0df49e23d5eb1ce9a27febad705",
        (CLIENT, _) if forge(target) => "4c80e469c376c1baed20d51d24aaae9693f191b0",
        (CLIENT, _) => "1ffb03d8f79f9c05353fcc4a3cfba9e29a66912b",
        (EDITOR, _) if development && d2(target) => "ab3f3be0cba9dd3ead3d9a8c9d8a177ca28104d8",
        (EDITOR, _) if forge(target) => "a621347dd214438adfc3bfddc96fe6a4fa37d627",
        (EDITOR, "26.1.2") => "a27e3d45f39e78468ddd13c0a45b8451710faa68",
        (EDITOR, _) => "ae26f51253b0258048aa0a923655141160865872",
        (TRACKER, _) if development && d2(target) => "31c63766c191ea12bc49216abdba8c8ddb421d71",
        (TRACKER, _) if forge(target) => "7c49648b0a37e450e641d14390726d13b7502332",
        (TRACKER, _) if old_tracker(target) => "563e2ae37f17c0954e65360874cf0b8a5396256b",
        (TRACKER, _) => "9ff571eb8b41bc72a089c210a893722cc04e15d6",
        _ => eyre::bail!("unreviewed config path"),
    })
}

fn expected_body(path: &str, blob: &str, bytes: &[u8]) -> Result<Vec<u8>> {
    let spec = raw_spec(path, blob)?;
    ensure!(
        bytes.len() as u64 == spec.bytes
            && sha256(bytes) == spec.digest
            && bytes.is_ascii()
            && (bytes.last() == Some(&b'\n')) == spec.raw_final_lf
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw config witness changed"
    );
    let raw = std::str::from_utf8(bytes)?;
    ensure!(
        raw.matches("\r\n").count() == spec.crlf_count
            && !raw.replace("\r\n", "").contains('\r')
            && spec.lf_added != spec.raw_final_lf,
        "raw config EOL evidence changed"
    );
    let normalized = if spec.crlf_count > 0 || spec.lf_added {
        ensure!(path != TRACKER, "unapproved tracker normalization");
        let mut text = raw.replace("\r\n", "\n");
        if spec.lf_added {
            ensure!(!text.ends_with('\n'), "final LF addition is not needed");
            text.push('\n');
        }
        text.into_bytes()
    } else {
        ensure!(!bytes.contains(&b'\r'), "unapproved raw normalization");
        bytes.to_vec()
    };
    ensure!(
        normalized.len() as u64 == spec.expected_bytes
            && sha256(&normalized) == spec.expected_digest
            && normalized.last() == Some(&b'\n'),
        "exact approved config normalized hash changed"
    );
    Ok(normalized)
}

fn body<'a>(bodies: &'a BTreeMap<String, String>, path: &str) -> Result<&'a str> {
    bodies
        .get(path)
        .map(String::as_str)
        .ok_or_else(|| eyre::eyre!("missing config body"))
}

fn enabled(context: &ProjectionContext, name: &str) -> Result<bool> {
    context
        .features
        .get(name)
        .copied()
        .ok_or_else(|| eyre::eyre!("missing explicit source flag"))
}

fn once(source: &str, anchor: &str, replacement: &str) -> Result<String> {
    ensure!(
        source.matches(anchor).count() == 1,
        "expected unique reviewed config anchor"
    );
    Ok(source.replacen(anchor, replacement, 1))
}

/// An independent reviewed textual oracle starts from the target's released
/// body and inserts only the requested fields/defines/methods. It does not
/// interpret Liquid or select an entire development blob.
fn expected_owner_body(
    fixture: &Fixture,
    path: &str,
    context: &ProjectionContext,
) -> Result<String> {
    // The real catalog's 1.21.0 target uses Mojang's Minecraft version "1.21".
    // Only the frozen witness key is normalized; retain the actual render context.
    let target = if context.minecraft_version == "1.21" {
        "1.21.0"
    } else {
        context.minecraft_version.as_str()
    };
    let blob = expected_blob(path, &format!("release/{target}"))?;
    let bytes = expected_body(path, blob, fixture.blob(blob)?)?;
    let mut source = String::from_utf8(bytes)?;
    match path {
        CLIENT => {
            let spec = if forge(&context.minecraft_version) {
                "ForgeConfigSpec"
            } else {
                "ModConfigSpec"
            };
            let history = enabled(context, "command_history")?;
            let terminal = terminal_config_enabled(context)?;
            let field_anchor =
                format!("    public final {spec}.BooleanValue showNetworkToolReminderOverlay;\n");
            let mut fields = field_anchor.clone();
            if history {
                fields.push_str(&format!(
                    "    public final {spec}.BooleanValue commandPaletteHistoryEnabled;\n"
                ));
            }
            if terminal {
                fields.push_str(&format!("    public final {spec}.ConfigValue<String> terminalRustServerAddress;\n    public final {spec}.ConfigValue<String> terminalRustServerExecutable;\n"));
            }
            source = once(&source, &field_anchor, &fields)?;
            let define_anchor = "        showNetworkToolReminderOverlay = builder.define(\"showNetworkToolReminderOverlay\", true);\n";
            let mut defines = define_anchor.to_owned();
            if history {
                defines.push_str("        commandPaletteHistoryEnabled = builder.define(\"commandPaletteHistoryEnabled\", true);\n");
            }
            if terminal {
                defines.push_str("        terminalRustServerAddress = builder.define(\"terminalRustServerAddress\", \"127.0.0.1:63946\");\n        terminalRustServerExecutable = builder.define(\"terminalRustServerExecutable\", \"teamy-terminal.exe\");\n");
            }
            source = once(&source, define_anchor, &defines)?;
        }
        EDITOR if enabled(context, "canvas_pointer_defaults")? => {
            ensure!(
                d2(&context.minecraft_version),
                "pointer preferences widened"
            );
            let anchor = "    public final ForgeConfigSpec.BooleanValue showLineNumbers;\n";
            source = once(
                &source,
                anchor,
                &format!(
                    "{anchor}    public final ForgeConfigSpec.BooleanValue canvasWheelZooms;\n    public final ForgeConfigSpec.BooleanValue canvasMiddlePans;\n"
                ),
            )?;
            let anchor = "        showLineNumbers = builder.define(\"showLineNumbers\", false);\n";
            source = once(
                &source,
                anchor,
                &format!(
                    "{anchor}        canvasWheelZooms = builder.comment(\"Default for newly opened v3 editors; existing editors keep their own setting.\")\n                .define(\"canvasWheelZooms\", true);\n        canvasMiddlePans = builder.comment(\"Middle pans and right opens actions; false swaps the two buttons in new v3 editors.\")\n                .define(\"canvasMiddlePans\", true);\n"
                ),
            )?;
        }
        TRACKER if enabled(context, "command_history")? => {
            source = once(
                &source,
                "        public static class ModConfigEventListeners",
                "    /** Persist a client-config mutation made by an in-game action. */\n    public static boolean saveClientConfig() {\n        ModConfig modConfig = getClientModConfig();\n        if (modConfig == null) {\n            SFM.LOGGER.warn(\"Unable to save SFM client config because it is not registered\");\n            return false;\n        }\n        modConfig.save();\n        return true;\n    }\n\n        public static class ModConfigEventListeners",
            )?;
        }
        EDITOR | TRACKER => {}
        _ => eyre::bail!("unknown config owner oracle"),
    }
    Ok(source)
}

fn assert_contract(context: &ProjectionContext, bodies: &BTreeMap<String, String>) -> Result<()> {
    let target = &context.minecraft_version;
    let client = body(bodies, CLIENT)?;
    let editor = body(bodies, EDITOR)?;
    let tracker = body(bodies, TRACKER)?;
    let history = enabled(context, "command_history")?;
    let terminal = terminal_config_enabled(context)?;
    let pointer = enabled(context, "canvas_pointer_defaults")?;
    ensure!(
        client.matches("commandPaletteHistoryEnabled").count() == 3 * usize::from(history)
            && tracker.contains("public static boolean saveClientConfig()") == history,
        "history setting/method ownership changed"
    );
    for name in ["terminalRustServerAddress", "terminalRustServerExecutable"] {
        ensure!(
            client.matches(name).count() == 3 * usize::from(terminal),
            "terminal union field/define leaked"
        );
    }
    for name in ["canvasWheelZooms", "canvasMiddlePans"] {
        ensure!(
            editor.matches(name).count() == 3 * usize::from(pointer),
            "pointer field/define leaked"
        );
    }
    ensure!(
        client.contains("showLabelGunReminderOverlay = builder.define(\"showLabelGunReminderOverlay\", true)")
            && client.contains("showNetworkToolReminderOverlay = builder.define(\"showNetworkToolReminderOverlay\", true)")
            && editor.contains("showLineNumbers = builder.define(\"showLineNumbers\", false)")
            && editor.contains("SFMTextEditorIntellisenseLevel.OFF")
            && editor.contains("preferredEditor = builder.define(\"preferredEditor\", \"sfm:v1\")"),
        "released config keys/defaults changed"
    );
    for source in [client, editor] {
        ensure!(
            source.contains("import net.minecraftforge.common.ForgeConfigSpec;") == forge(target)
                && source.contains("import net.neoforged.neoforge.common.ModConfigSpec;")
                    != forge(target),
            "config-spec loader boundary changed"
        );
    }
    let newest = target == "26.1.2";
    ensure!(
        editor.contains("import net.minecraft.resources.Identifier;") == newest
            && editor.contains("defaultId.identifier()") == newest
            && editor.contains(".get(id).map(Holder.Reference::value)") == newest
            && editor.contains("Objects.requireNonNullElse(") != newest
            && editor.contains("defaultId.location()") != newest,
        "identifier/holder editor fallback API changed"
    );
    ensure!(
        tracker.contains("ConfigTracker.INSTANCE.configSets().get(modConfigType)")
            == old_tracker(target)
            && tracker.contains("HashMap<IConfigSpec<?>, Path>") == old_tracker(target)
            && tracker.contains("IConfigSpec.ILoadedConfig") != old_tracker(target)
            && tracker.contains("getDeclaredField(\"loadedConfig\")") != old_tracker(target)
            && tracker.contains("getDeclaredField(\"childConfig\")") == old_tracker(target),
        "config tracker API/reflection boundary changed"
    );
    ensure!(
        tracker.contains("import net.minecraftforge.server.ServerLifecycleHooks;") == forge(target)
            && tracker.contains("configPaths.entrySet().removeIf(entry -> entry.getKey() == SFMConfig.SERVER_CONFIG_SPEC);"),
        "historical lifecycle import/unload behavior changed"
    );
    for source in bodies.values() {
        ensure!(
            !source.contains("{%") && !source.contains('\r') && source.ends_with('\n'),
            "unrendered config directive or changed reviewed LF output"
        );
    }
    Ok(())
}

#[test]
fn sixty_frozen_config_witnesses_reconstruct_through_actual_core() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut count = 0;
    for (context_name, _) in PINNED_CONTEXTS {
        let (kind, target) = context_name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let requested: &[&str] = if kind == "release" {
            &[]
        } else if d2(target) {
            &[
                "command_history",
                "terminal_remote",
                "terminal_vox",
                "canvas_pointer_defaults",
            ]
        } else {
            &["terminal_remote"]
        };
        let context = fixture.context(target, requested)?;
        let bodies = fixture.render_sources(&context)?;
        assert_contract(&context, &bodies)?;
        for path in PATHS {
            let blob = expected_blob(path, context_name)?;
            let expected = expected_body(path, blob, fixture.blob(blob)?)?;
            assert_eq!(
                body(&bodies, path)?.as_bytes(),
                expected,
                "{context_name}: {path}"
            );
            count += 1;
        }
    }
    assert_eq!(count, 60);
    Ok(())
}

#[test]
fn forty_eight_independent_config_owner_masks_match_reviewed_off_insertions() -> Result<()> {
    let fixture = Fixture::load()?;
    let owners = [
        "command_history",
        "terminal_remote",
        "terminal_vox",
        "canvas_pointer_defaults",
    ];
    let mut count = 0;
    for (target, _) in SUPPORTED_TARGETS {
        let masks: Vec<Vec<&str>> = if d2(target) {
            (0_u32..16)
                .map(|mask| {
                    owners
                        .into_iter()
                        .enumerate()
                        .filter_map(|(bit, name)| (mask & (1 << bit) != 0).then_some(name))
                        .collect()
                })
                .collect()
        } else {
            vec![vec![], vec!["terminal_remote"]]
        };
        for mask in masks {
            let context = fixture.context(target, &mask)?;
            let bodies = fixture.render_sources(&context)?;
            assert_contract(&context, &bodies)?;
            for path in PATHS {
                assert_eq!(
                    body(&bodies, path)?,
                    expected_owner_body(&fixture, path, &context)?,
                    "{target}: {mask:?}: {path}"
                );
            }
            count += 1;
        }
        if !d2(target) {
            for owner in ["command_history", "terminal_vox", "canvas_pointer_defaults"] {
                assert!(
                    fixture.context(target, &[owner]).is_err(),
                    "owner widened to {target}: {owner}"
                );
            }
        }
    }
    assert_eq!(count, 48);
    Ok(())
}

#[test]
fn config_owner_source_contexts_have_only_reviewed_prerequisites() -> Result<()> {
    let fixture = Fixture::load()?;
    for requested in ["terminal_remote", "terminal_vox"] {
        let context = fixture.context("1.19.2", &[requested])?;
        let actual = context
            .features
            .iter()
            .filter_map(|(name, value)| value.then_some(name.as_str()))
            .collect::<BTreeSet<_>>();
        assert_eq!(actual, BTreeSet::from([requested]));
        let bodies = fixture.render_sources(&context)?;
        assert!(body(&bodies, CLIENT)?.contains("terminalRustServerAddress"));
        assert!(!body(&bodies, TRACKER)?.contains("saveClientConfig()"));
        assert!(!body(&bodies, EDITOR)?.contains("canvasWheelZooms"));
    }
    let pointer = fixture.context("1.19.2", &["canvas_pointer_defaults"])?;
    let actual = pointer
        .features
        .iter()
        .filter_map(|(name, value)| value.then_some(name.as_str()))
        .collect::<BTreeSet<_>>();
    assert_eq!(
        actual,
        BTreeSet::from(["canvas_pointer_defaults", "canvas_text_editor"])
    );
    let canvas = fixture.context("1.19.2", &["canvas_text_editor"])?;
    let bodies = fixture.render_sources(&canvas)?;
    assert!(!body(&bodies, EDITOR)?.contains("canvasWheelZooms"));
    let history = fixture.context("1.19.2", &["command_history"])?;
    for absent in [
        "terminal_remote",
        "terminal_vox",
        "canvas_pointer_defaults",
        "canvas_text_editor",
        "packet_transport_private",
        "client_program_signing",
        "multiplayer_packets",
        "image_resources",
    ] {
        assert!(!enabled(&history, absent)?);
    }
    let mut presentation = fixture.context("1.19.2", &[])?;
    presentation.environment = "dev".to_owned();
    presentation.preset = "not-a-feature-owner".to_owned();
    presentation.projection_key = "sfm-dev/mc-1.19.2".to_owned();
    let original = fixture.render_sources(&fixture.context("1.19.2", &[])?)?;
    assert_eq!(original, fixture.render_sources(&presentation)?);
    Ok(())
}

#[test]
fn exact_seven_config_normalizations_reject_raw_or_whitespace_mutations() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut transformed = 0;
    let mut lf_added = 0;
    for spec in RAW_SPECS {
        let raw = fixture.blob(spec.blob)?;
        let normalized = expected_body(spec.path, spec.blob, raw)?;
        if spec.crlf_count > 0 || spec.lf_added {
            assert_ne!(normalized, raw);
            transformed += 1;
        } else {
            assert_eq!(normalized, raw);
        }
        lf_added += usize::from(spec.lf_added);
        let mut extra_lf = raw.to_vec();
        extra_lf.push(b'\n');
        assert!(expected_body(spec.path, spec.blob, &extra_lf).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend_from_slice(raw);
        assert!(expected_body(spec.path, spec.blob, &bom).is_err());
        let mut whitespace = raw.to_vec();
        if let Some(byte) = whitespace.iter_mut().find(|byte| **byte == b' ') {
            *byte = b'\t';
        } else {
            eyre::bail!("raw config fixture lacks mutation anchor");
        }
        assert!(expected_body(spec.path, spec.blob, &whitespace).is_err());
        assert!(expected_body(spec.path, spec.blob, &raw[..raw.len() - 1]).is_err());
        assert!(expected_body(spec.path, "0000000000000000000000000000000000000000", raw).is_err());
    }
    assert_eq!(transformed, 7);
    assert_eq!(lf_added, 3);
    Ok(())
}

#[test]
fn common_config_body_edits_reach_three_target_api_eras_without_guard_changes() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let temp_core = temp.path().join(CORE_ROOT);
    let edits = [
        (
            CLIENT,
            "        showLabelGunReminderOverlay = builder.define(\"showLabelGunReminderOverlay\", true);\n",
        ),
        (
            EDITOR,
            "        showLineNumbers = builder.define(\"showLineNumbers\", false);\n",
        ),
        (TRACKER, "        return configPaths.get(spec);\n"),
    ];
    for (path, anchor) in edits {
        let source = std::str::from_utf8(
            fixture
                .sources
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing original source"))?,
        )?;
        let edited = once(
            source,
            anchor,
            &format!("{anchor}        // Common config body edit proof.\n"),
        )?;
        let original_guards = source
            .lines()
            .filter(|line| line.trim().starts_with("{%"))
            .collect::<Vec<_>>();
        let edited_guards = edited
            .lines()
            .filter(|line| line.trim().starts_with("{%"))
            .collect::<Vec<_>>();
        assert_eq!(original_guards, edited_guards);
        let file = temp_core.join(path);
        fs::create_dir_all(
            file.parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(&file, edited.as_bytes())?;
        for target in ["1.19.2", "1.20.2", "26.1.2"] {
            let context = fixture.context(target, &[])?;
            let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
            let input = selection
                .inputs
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing shared fixture selection"))?;
            assert_eq!(input.input, path);
            let staged = read_bounded(&temp_core.join(&input.input), MAX_SOURCE_BYTES)?;
            let actual = render_java_source(std::str::from_utf8(&staged)?, &context)?;
            let original = expected_owner_body(&fixture, path, &context)?;
            let expected = once(
                &original,
                anchor,
                &format!("{anchor}        // Common config body edit proof.\n"),
            )?;
            assert_eq!(actual, expected, "{target}: {path}");
        }
    }
    for path in PATHS {
        assert_eq!(fixture.shared.read_source(path)?, fixture.sources[path]);
    }
    Ok(())
}

#[test]
fn promoted_config_inputs_are_pinned_core_owned_not_staged_or_historical_overrides() -> Result<()> {
    let fixture = Fixture::load()?;
    for (target, _) in SUPPORTED_TARGETS {
        let requests = if d2(target) {
            vec![
                vec!["terminal_vox_runtime"],
                vec!["terminal_properties"],
                vec!["terminal_vox_runtime", "terminal_properties"],
            ]
        } else {
            vec![vec!["terminal_vox_runtime"]]
        };
        for requested in requests {
            let context = fixture.context(target, &requested)?;
            assert!(!context.features["terminal_remote"] && !context.features["terminal_vox"]);
            let bodies = fixture.render_sources(&context)?;
            assert_contract(&context, &bodies)?;
            for path in PATHS {
                assert_eq!(
                    body(&bodies, path)?,
                    expected_owner_body(&fixture, path, &context)?
                );
            }
        }
    }
    let original = &fixture.sources[CLIENT];
    let mut unrelated = original.clone();
    unrelated.push(b' ');
    assert!(reviewed_original_config(CLIENT, &unrelated).is_err());
    let altered_guard = std::str::from_utf8(original)?.replacen(
        "features.terminal_properties",
        "features.terminal_remote",
        1,
    );
    assert!(reviewed_original_config(CLIENT, altered_guard.as_bytes()).is_err());
    for path in PATHS {
        let source = std::str::from_utf8(&fixture.sources[path])?;
        assert!(
            !source.contains("case environment")
                && !source.contains("case preset")
                && !source.contains("case projection_key")
        );
    }
    for (target, _) in SUPPORTED_TARGETS {
        let context = fixture.context(target, &[])?;
        let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
        for path in PATHS {
            assert!(!selection.omitted_paths.contains(path));
            let selected = selection
                .inputs
                .get(path)
                .ok_or_else(|| eyre::eyre!("core config selection absent"))?;
            assert_eq!(selected.input, path);
        }
    }
    Ok(())
}

#[test]
fn twenty_frozen_config_tree_memberships_match_exact_raw_blob_ids() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for (context_name, commit) in PINNED_CONTEXTS {
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
                "-z",
                commit,
                "--",
            ]);
        for path in PATHS {
            command.arg(format!("platform/minecraft/{path}"));
        }
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("missing Git tree output"))?;
        let mut bytes = Vec::new();
        let read_result = reader
            .by_ref()
            .take(MAX_TREE_BYTES + 1)
            .read_to_end(&mut bytes);
        drop(reader);
        if read_result.is_err() || bytes.len() as u64 > MAX_TREE_BYTES {
            let _ = child.kill();
            let _ = child.wait();
            read_result?;
            eyre::bail!("frozen config tree exceeds bound");
        }
        ensure!(
            child.wait()?.success(),
            "offline tree membership read failed"
        );
        let mut actual = BTreeMap::new();
        ensure!(
            bytes.last() == Some(&0),
            "missing exact Git tree terminator"
        );
        for record in bytes
            .split(|byte| *byte == 0)
            .filter(|record| !record.is_empty())
        {
            let row = std::str::from_utf8(record)?;
            let (header, path) = row
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("invalid tree record"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3 && fields[0] == "100644" && fields[1] == "blob",
                "invalid frozen source type/mode"
            );
            ensure!(
                actual
                    .insert(path.to_owned(), fields[2].to_owned())
                    .is_none(),
                "duplicate tree source"
            );
        }
        ensure!(actual.len() == 3, "frozen config membership changed");
        for path in PATHS {
            assert_eq!(
                actual
                    .get(&format!("platform/minecraft/{path}"))
                    .map(String::as_str),
                Some(expected_blob(path, context_name)?),
                "{context_name}: {path}"
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 60);
    Ok(())
}
