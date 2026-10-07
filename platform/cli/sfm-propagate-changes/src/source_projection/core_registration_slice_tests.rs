//! Frozen source-reconstruction goldens for three registration templates.
//!
//! These tests use actual core selection and Liquid rendering. Bounded historical
//! Git blobs are test-only witnesses, never generation inputs. Only four approved
//! capability blobs may replace CRLF with LF; no token/other whitespace changes
//! or terminal newline insertion are permitted.
//!
//! Functional source proofs do not collect a complete project or prove Java,
//! capability events, menu synchronization or runtime behavior. Deliberate future
//! core edits require reviewing these migration goldens.

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

const LEDGER: &str = "docs/tasks/sfm-core-registration-slice.json";
const RESOURCES: &str =
    "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMResourceTypes.java";
const CAPABILITIES: &str =
    "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMCapabilities.java";
const MENUS: &str = "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMMenus.java";
const PATHS: [&str; 3] = [RESOURCES, CAPABILITIES, MENUS];
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;
const MAX_SOURCE_BYTES: u64 = 128 * 1024;
const NORMALIZATION: &str = "sfm:java_lf_final_newline@1";

const PINNED_STAGE: [(&str, &str, u64); 3] = [
    (
        RESOURCES,
        "sha256:588d8d9e9a8d70f0854b7bcecb12ec909c35e9a94c0c4e899423b38dbae13b5c",
        5472,
    ),
    (
        CAPABILITIES,
        "sha256:9fd4e78d31a0c40cefc4d63f6aca71e3f0d94c2617a010a135fa2085e46c35b6",
        1772,
    ),
    (
        MENUS,
        "sha256:374d0bd416872931b27d52cfcc2f936bb75ee909ba77e18d967d9160f55a1e8f",
        7650,
    ),
];

// The staged proposal used the target/key spelling in Liquid comparisons.
// Promotion fixes only those selectors to the actual upstream version "1.21".
// Keep the original stage identities separate from the reviewed core identities;
// all historical rendered-byte witnesses remain unchanged.
const PINNED_CORE: [(&str, &str, u64); 3] = [
    (
        RESOURCES,
        "sha256:c3f2e7d2f1efc3b73151c2b47a5fe80066a39016bec64401316b0f97ce807117",
        5464,
    ),
    (
        CAPABILITIES,
        "sha256:c6f6af799f0f224e6a416feb809abc1623f66d00d59a8a17cfe35c5b065a9043",
        1766,
    ),
    (
        MENUS,
        "sha256:49730038255cdc4dae709f552e884c803c5fe1e903ab43b54aba0ed9aa013e9a",
        7644,
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
    expected_digest: &'static str,
    expected_bytes: u64,
}

const RAW_SPECS: [RawSpec; 15] = [
    RawSpec {
        path: RESOURCES,
        blob: "ce18ed15905a95c27c69872289202c2fed719d39",
        digest: "sha256:6daf6b5e8c8c8c47eaa7ba5640dc29ae9cb1f03fb2dc4033357c979c7da543d2",
        bytes: 3261,
        crlf_count: 0,
        expected_digest: "sha256:6daf6b5e8c8c8c47eaa7ba5640dc29ae9cb1f03fb2dc4033357c979c7da543d2",
        expected_bytes: 3261,
    },
    RawSpec {
        path: RESOURCES,
        blob: "9bf6de1ff11a973d4a8266765fc45d3443ffda88",
        digest: "sha256:6330c24dc78f70be2712138e2e21887dd61eb7ddf5709bbe9b9ad085921ac09f",
        bytes: 3207,
        crlf_count: 0,
        expected_digest: "sha256:6330c24dc78f70be2712138e2e21887dd61eb7ddf5709bbe9b9ad085921ac09f",
        expected_bytes: 3207,
    },
    RawSpec {
        path: RESOURCES,
        blob: "9b0278a4f993c073fabe4af0377154a7ef916233",
        digest: "sha256:3a4fc00010468377832e4cd5d4261423c1515dbbdafce50f8bcb211ca10e0f9a",
        bytes: 3048,
        crlf_count: 0,
        expected_digest: "sha256:3a4fc00010468377832e4cd5d4261423c1515dbbdafce50f8bcb211ca10e0f9a",
        expected_bytes: 3048,
    },
    RawSpec {
        path: RESOURCES,
        blob: "b771f74a7932b53c2359fa45a9faae8df8784a86",
        digest: "sha256:3e05d52c6517696e53452a84190400a562376742cc9dbd035dc8ced5265962df",
        bytes: 3102,
        crlf_count: 0,
        expected_digest: "sha256:3e05d52c6517696e53452a84190400a562376742cc9dbd035dc8ced5265962df",
        expected_bytes: 3102,
    },
    RawSpec {
        path: RESOURCES,
        blob: "edab87382f0cab6b6e52e3ca0f39cce4a5a09424",
        digest: "sha256:33466820fdd456dbae916071cebf618bae7d21b97caf45ef8f185ce44d747ec6",
        bytes: 3303,
        crlf_count: 0,
        expected_digest: "sha256:33466820fdd456dbae916071cebf618bae7d21b97caf45ef8f185ce44d747ec6",
        expected_bytes: 3303,
    },
    RawSpec {
        path: RESOURCES,
        blob: "1fa91aec99888e0f36feedceb0005e97ee6b5790",
        digest: "sha256:f42272fe9b6a3244bd1dac3772edda283023fdbf08447c132493639139403cf7",
        bytes: 3400,
        crlf_count: 0,
        expected_digest: "sha256:f42272fe9b6a3244bd1dac3772edda283023fdbf08447c132493639139403cf7",
        expected_bytes: 3400,
    },
    RawSpec {
        path: RESOURCES,
        blob: "dc6a3e8b28899ed038932fd837b77855b002dcc0",
        digest: "sha256:c1d3d267ed153c3ec98d56a438146da4c5d106d03dbe39f74095954093e622a0",
        bytes: 3458,
        crlf_count: 0,
        expected_digest: "sha256:c1d3d267ed153c3ec98d56a438146da4c5d106d03dbe39f74095954093e622a0",
        expected_bytes: 3458,
    },
    RawSpec {
        path: CAPABILITIES,
        blob: "7d892a16b3e54e3362cc5ec944ea53653c1703f2",
        digest: "sha256:822bc8ac8471473c0b2ff1ba9753a32a24a6059609e2ea743c995b133493956a",
        bytes: 775,
        crlf_count: 14,
        expected_digest: "sha256:ad785b0b5cd726c220a0af38b1a181717f3f5fd19664ddc7b0da58065e790b00",
        expected_bytes: 761,
    },
    RawSpec {
        path: CAPABILITIES,
        blob: "4214f8713cfbc06e5a0eeab0e75ea257af954fe1",
        digest: "sha256:f5b831fea74fbde3a27ed7314e9eff58193690a4710576f8eee053dbe5fa2af6",
        bytes: 676,
        crlf_count: 16,
        expected_digest: "sha256:808ab927a7be364eb36cb28c20cd9b9525219bc45f5839dcc1d084637c78baf2",
        expected_bytes: 660,
    },
    RawSpec {
        path: CAPABILITIES,
        blob: "54db3a4d48c25addc3067fb79783026a3898534b",
        digest: "sha256:ae84249ce179c30c7ccca8a1e477645db2db9306da45d0976e7742f8f0d67461",
        bytes: 680,
        crlf_count: 16,
        expected_digest: "sha256:204bcdc7069a1f43ab86db59bc1311a2614b29607890e79a3d8c03c397d542d5",
        expected_bytes: 664,
    },
    RawSpec {
        path: CAPABILITIES,
        blob: "5a99d9b438e362d205cfdf196067596148f9854f",
        digest: "sha256:42b34c66a6c6a6399cb2f7d42ad418f756d6bcf9071e582083dc22b95ec5dee1",
        bytes: 862,
        crlf_count: 18,
        expected_digest: "sha256:7d7ea1da3429d48d82b0615b239f4c8ec76ebaa7d156f6ddd03ef3475277e67e",
        expected_bytes: 844,
    },
    RawSpec {
        path: MENUS,
        blob: "24bdc67e35db16509f224070f6b58ff176f45e0e",
        digest: "sha256:638dd8caecfec0803ae19d656bc641927cf3a3271727a6686f02af952e5163f2",
        bytes: 6484,
        crlf_count: 0,
        expected_digest: "sha256:638dd8caecfec0803ae19d656bc641927cf3a3271727a6686f02af952e5163f2",
        expected_bytes: 6484,
    },
    RawSpec {
        path: MENUS,
        blob: "7c886331646b68687e71545433dd927d19d1dfa4",
        digest: "sha256:bdf3b52706ee97318b244db5b93f175b9182bf72e2ebcf448c490a1f2f39563d",
        bytes: 5016,
        crlf_count: 0,
        expected_digest: "sha256:bdf3b52706ee97318b244db5b93f175b9182bf72e2ebcf448c490a1f2f39563d",
        expected_bytes: 5016,
    },
    RawSpec {
        path: MENUS,
        blob: "3635622228caf4188398639120fd723529006797",
        digest: "sha256:aef9a8f6bd6037107bb49e70167048563733c3f891b318433adb03d3fdba9075",
        bytes: 5026,
        crlf_count: 0,
        expected_digest: "sha256:aef9a8f6bd6037107bb49e70167048563733c3f891b318433adb03d3fdba9075",
        expected_bytes: 5026,
    },
    RawSpec {
        path: MENUS,
        blob: "9df05d5421b30c802acd5bf719b8ad417baf0c8a",
        digest: "sha256:52f4d84947709132fa89216bae1ead86ccdf10f10b5bd961ded1fff781fc36ce",
        bytes: 5050,
        crlf_count: 0,
        expected_digest: "sha256:52f4d84947709132fa89216bae1ead86ccdf10f10b5bd961ded1fff781fc36ce",
        expected_bytes: 5050,
    },
];

// Only this typed golden subset is executable; prose is not configuration.
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
    expected_sha256: String,
    expected_bytes: u64,
    normalization: String,
    lf_added: bool,
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
            .wrap_err("cannot parse bounded registration source evidence")?;
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
            self.ledger.schema == "sfm:core-registration-slice@1",
            "registration source schema changed"
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
            "exact three-file scope changed"
        );
        ensure!(
            self.ledger
                .owners
                .keys()
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == BTreeSet::from(["image_resources", "client_manager_gui"]),
            "functional owners widened"
        );
        for (name, support, requires) in [
            (
                "image_resources",
                &["1.19.2", "1.19.4"][..],
                &["packet_values"][..],
            ),
            (
                "client_manager_gui",
                &["1.19.2"][..],
                &["client_manager"][..],
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
                .ok_or_else(|| eyre::eyre!("unregistered owner"))?;
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
                "actual owner support/prerequisites changed"
            );
        }
        let approved = RAW_SPECS
            .into_iter()
            .filter(|spec| spec.crlf_count > 0)
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
            approved.len() == 4
                && declared == approved
                && self.ledger.normalization.approved_git_blobs.len() == 4
                && self.ledger.normalization.policy == NORMALIZATION
                && self.ledger.normalization.crlf_to_lf_only
                && !self.ledger.normalization.append_final_lf
                && !self.ledger.normalization.other_whitespace_changes,
            "normalization scope broadened"
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
            .ok_or_else(|| eyre::eyre!("unreviewed core file"))?;
        let (_, stage_digest, stage_bytes) = PINNED_STAGE
            .into_iter()
            .find(|(candidate, _, _)| *candidate == path)
            .ok_or_else(|| eyre::eyre!("unreviewed staged file"))?;
        let source = self
            .sources
            .get(path)
            .ok_or_else(|| eyre::eyre!("missing source"))?;
        ensure!(
            file.stage_sha256 == stage_digest
                && file.stage_bytes == stage_bytes
                && source.len() as u64 == bytes
                && sha256(source) == digest
                && file.membership == "unconditional_shared_class",
            "core registration golden changed; deliberate common edits need review"
        );
        let mut seen = BTreeSet::new();
        for witness in &file.witnesses {
            ensure!(
                contexts.contains_key(&witness.context) && seen.insert(&witness.context),
                "unknown/repeated witness"
            );
            let blob = expected_blob(path, &witness.context)?;
            let spec = raw_spec(path, blob)?;
            let expected = expected_body(path, blob, self.blob(blob)?)?;
            ensure!(
                witness.git_blob == blob
                    && witness.raw_sha256 == spec.digest
                    && witness.raw_bytes == spec.bytes
                    && witness.crlf_count == spec.crlf_count
                    && witness.expected_sha256 == spec.expected_digest
                    && witness.expected_bytes == spec.expected_bytes
                    && witness.expected_sha256 == sha256(&expected)
                    && witness.normalization
                        == if spec.crlf_count == 0 {
                            "none"
                        } else {
                            NORMALIZATION
                        }
                    && !witness.lf_added,
                "raw/normalization witness contract changed"
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
            .ok_or_else(|| eyre::eyre!("missing raw witness"))
    }

    /// Source-only contexts use the real prerequisite graph, never a wire-family bundle.
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
                "shared registration was omitted or historically routed"
            );
            let source = self
                .sources
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing core source"))?;
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
            "registration cohort membership changed"
        );
        Ok(bodies)
    }
}

fn inventory() -> BTreeSet<String> {
    PATHS.into_iter().map(str::to_owned).collect()
}

fn feature_closure(shared: &CoreTestFixture, requested: &[&str]) -> Result<BTreeSet<String>> {
    ensure!(
        shared.features.0.len() <= 256,
        "source-proof registry is bounded"
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
            .ok_or_else(|| eyre::eyre!("unknown feature"))?;
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
        .ok_or_else(|| eyre::eyre!("unreviewed raw registration witness"))
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
    let image = matches!(context, "dev/1.19.2" | "dev/1.19.4");
    let gui = context == "dev/1.19.2";
    Ok(match (path, target) {
        (RESOURCES, "1.19.2") if image => "ce18ed15905a95c27c69872289202c2fed719d39",
        (RESOURCES, "1.19.4") if image => "9bf6de1ff11a973d4a8266765fc45d3443ffda88",
        (RESOURCES, "1.19.2" | "1.20.1") => "b771f74a7932b53c2359fa45a9faae8df8784a86",
        (RESOURCES, "1.19.4" | "1.20") => "9b0278a4f993c073fabe4af0377154a7ef916233",
        (RESOURCES, "1.20.2" | "1.20.3") => "edab87382f0cab6b6e52e3ca0f39cce4a5a09424",
        (RESOURCES, "1.20.4" | "1.21.0" | "1.21.1") => "1fa91aec99888e0f36feedceb0005e97ee6b5790",
        (RESOURCES, "26.1.2") => "dc6a3e8b28899ed038932fd837b77855b002dcc0",
        (CAPABILITIES, _) if image => "7d892a16b3e54e3362cc5ec944ea53653c1703f2",
        (CAPABILITIES, "1.19.2" | "1.19.4" | "1.20" | "1.20.1") => {
            "4214f8713cfbc06e5a0eeab0e75ea257af954fe1"
        }
        (CAPABILITIES, "1.20.2") => "54db3a4d48c25addc3067fb79783026a3898534b",
        (CAPABILITIES, _) => "5a99d9b438e362d205cfdf196067596148f9854f",
        (MENUS, _) if gui => "24bdc67e35db16509f224070f6b58ff176f45e0e",
        (MENUS, "1.19.2" | "1.19.4" | "1.20" | "1.20.1") => {
            "7c886331646b68687e71545433dd927d19d1dfa4"
        }
        (MENUS, "1.20.2" | "1.20.3" | "1.20.4") => "3635622228caf4188398639120fd723529006797",
        (MENUS, _) => "9df05d5421b30c802acd5bf719b8ad417baf0c8a",
        _ => eyre::bail!("unreviewed registration source"),
    })
}

fn expected_body(path: &str, blob: &str, bytes: &[u8]) -> Result<Vec<u8>> {
    let spec = raw_spec(path, blob)?;
    ensure!(
        bytes.len() as u64 == spec.bytes
            && sha256(bytes) == spec.digest
            && bytes.is_ascii()
            && bytes.last() == Some(&b'\n')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw witness changed"
    );
    let raw = std::str::from_utf8(bytes)?;
    let count = raw.matches("\r\n").count();
    ensure!(
        count == spec.crlf_count && !raw.replace("\r\n", "").contains('\r'),
        "raw EOL evidence changed"
    );
    let expected = if spec.crlf_count > 0 {
        ensure!(path == CAPABILITIES, "unapproved source normalization");
        raw.replace("\r\n", "\n").into_bytes()
    } else {
        ensure!(!bytes.contains(&b'\r'), "unexpected normalization need");
        bytes.to_vec()
    };
    ensure!(
        expected.len() as u64 == spec.expected_bytes
            && sha256(&expected) == spec.expected_digest
            && expected.last() == Some(&b'\n'),
        "exact approved normalized hash changed"
    );
    Ok(expected)
}

fn body<'a>(bodies: &'a BTreeMap<String, String>, path: &str) -> Result<&'a str> {
    bodies
        .get(path)
        .map(String::as_str)
        .ok_or_else(|| eyre::eyre!("missing rendered class"))
}

fn enabled(context: &ProjectionContext, name: &str) -> Result<bool> {
    context
        .features
        .get(name)
        .copied()
        .ok_or_else(|| eyre::eyre!("missing explicit feature flag"))
}

fn old_namespace(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1")
}

fn modern_capability(target: &str) -> bool {
    matches!(
        target,
        "1.20.3" | "1.20.4" | "1.21" | "1.21.0" | "1.21.1" | "26.1.2"
    )
}

fn assert_resource_contract(target: &str, image: bool, source: &str) -> Result<()> {
    ensure!(
        source.contains("ImageResourceType> IMAGE") == image
            && source.contains("REGISTERER.register(\"image\", ImageResourceType::new)") == image,
        "image resource registration leaked/omitted"
    );
    ensure!(
        source.matches("= REGISTERER.register(").count() == 4 + usize::from(image),
        "resource registration count/order changed"
    );
    let capability = !old_namespace(target);
    ensure!(
        source.contains("getCapabilities()") == capability
            && source.contains("import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;")
                == capability,
        "released capability stream boundary changed"
    );
    let active_mek = matches!(
        target,
        "1.19.2" | "1.20.1" | "1.20.4" | "1.21" | "1.21.0" | "1.21.1" | "26.1.2"
    );
    ensure!(
        source.contains("\n            SFMMekanismCompat.registerResourceTypes(REGISTERER);\n")
            == active_mek
            && source.contains("import ca.teamdman.sfm.common.compat.SFMMekanismCompat;")
                == active_mek,
        "historic Mekanism registration behavior changed"
    );
    let whole_comment = matches!(target, "1.20.2" | "1.20.3");
    ensure!(
        source.contains("//    static {") == whole_comment
            && source.contains("import ca.teamdman.sfm.common.compat.SFMModCompat;")
                != whole_comment,
        "historic commented Mekanism static block changed"
    );
    if matches!(target, "1.19.4" | "1.20") {
        ensure!(source.contains("    static {\n        if (SFMModCompat.isMekanismLoaded()) {\n//            SFMMekanismCompat.registerResourceTypes"),
            "historic disabled Mekanism call changed");
    }
    let identifier = target == "26.1.2";
    ensure!(
        source.contains("import net.minecraft.resources.Identifier;") == identifier
            && source.contains("Object2ObjectOpenHashMap<Identifier,") == identifier
            && source.contains("import net.minecraft.core.Holder;") == identifier
            && source.contains(".map(Holder.Reference::value).orElse(null)") == identifier,
        "26.1.2 identifier/holder nullable lookup changed"
    );
    ensure!(
        source.contains("import net.minecraftforge.eventbus.api.IEventBus;")
            == old_namespace(target)
            && source.contains("import net.neoforged.bus.api.IEventBus;") != old_namespace(target),
        "event-bus package boundary changed"
    );
    let mut previous = 0;
    for name in ["item", "fluid", "forge_energy", "redstone"]
        .into_iter()
        .chain(image.then_some("image"))
    {
        let position = source
            .find(&format!("REGISTERER.register(\"{name}\","))
            .ok_or_else(|| eyre::eyre!("missing resource declaration"))?;
        ensure!(position > previous, "resource registration order changed");
        previous = position;
    }
    Ok(())
}

fn assert_capability_contract(target: &str, image: bool, source: &str) -> Result<()> {
    ensure!(
        source.contains("import ca.teamdman.sfm.common.capability.IImageHandler;") == image
            && source.contains("event.register(IImageHandler.class);") == image,
        "image capability import/event registration leaked/omitted"
    );
    let modern = modern_capability(target);
    ensure!(
        source.contains("REDSTONE_HANDLER = new SFMBlockCapabilityKind<>(") == modern
            && source.contains("BlockCapability.createSided(") == modern
            && source.contains("onRegisterCapabilities(RegisterCapabilitiesEvent event)") != modern
            && source.contains("event.register(IRedstoneSignalStorage.class);") != modern,
        "released capability event/field API changed"
    );
    if !modern {
        let namespace = if target == "1.20.2" {
            "net.neoforged.neoforge"
        } else {
            "net.minecraftforge"
        };
        ensure!(
            source.contains(&format!(
                "import {namespace}.common.capabilities.RegisterCapabilitiesEvent;"
            )),
            "capability registration event namespace changed"
        );
    }
    ensure!(
        !source.contains('\r') && source.ends_with('\n'),
        "approved capability LF shape changed"
    );
    Ok(())
}

fn assert_menu_contract(target: &str, gui: bool, source: &str) -> Result<()> {
    ensure!(
        source.contains("import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;")
            == gui
            && source.contains(
                "import ca.teamdman.sfm.common.containermenu.ClientManagerContainerMenu;"
            ) == gui
            && source.contains(" CLIENT_MANAGER = MENU_TYPES.register(") == gui,
        "client-manager GUI source leaked/omitted"
    );
    let count = 2 + usize::from(gui);
    ensure!(
        source.matches("= MENU_TYPES.register(").count() == count,
        "menu registration count changed"
    );
    let modern = matches!(target, "1.21" | "1.21.0" | "1.21.1" | "26.1.2");
    ensure!(
        source.contains("import net.minecraft.network.RegistryFriendlyByteBuf;") == modern
            && source.contains("import net.minecraft.network.FriendlyByteBuf;") != modern,
        "menu opening buffer API changed"
    );
    let factory = if old_namespace(target) {
        "IForgeMenuType"
    } else {
        "IMenuTypeExtension"
    };
    ensure!(
        source.matches(&format!("() -> {factory}.create(")).count() == count,
        "menu loader extension factory changed"
    );
    let manager = source
        .find(" MANAGER = MENU_TYPES.register(")
        .ok_or_else(|| eyre::eyre!("missing manager menu"))?;
    let tank = source
        .find(" TEST_BARREL_TANK = MENU_TYPES.register(")
        .ok_or_else(|| eyre::eyre!("missing tank menu"))?;
    ensure!(manager < tank, "released menu order changed");
    if gui {
        let client = source
            .find(" CLIENT_MANAGER = MENU_TYPES.register(")
            .ok_or_else(|| eyre::eyre!("missing client menu"))?;
        ensure!(
            manager < client
                && client < tank
                && source.contains("if (be instanceof ClientManagerBlockEntity manager)")
                && source.contains("return IContainerFactory.super.create(windowId, inv);"),
            "GUI order or inventory-only fallback changed"
        );
    }
    Ok(())
}

fn assert_contract(context: &ProjectionContext, bodies: &BTreeMap<String, String>) -> Result<()> {
    assert_resource_contract(
        &context.minecraft_version,
        enabled(context, "image_resources")?,
        body(bodies, RESOURCES)?,
    )?;
    assert_capability_contract(
        &context.minecraft_version,
        enabled(context, "image_resources")?,
        body(bodies, CAPABILITIES)?,
    )?;
    assert_menu_contract(
        &context.minecraft_version,
        enabled(context, "client_manager_gui")?,
        body(bodies, MENUS)?,
    )?;
    for source in bodies.values() {
        ensure!(!source.contains("{%"), "unrendered registration directive");
    }
    Ok(())
}

#[test]
fn sixty_frozen_registration_witnesses_reconstruct_through_real_core() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut witnesses = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid pinned context"))?;
        let requested: &[&str] = match name {
            "dev/1.19.2" => &["image_resources", "client_manager_gui"],
            "dev/1.19.4" => &["image_resources"],
            _ => &[],
        };
        let context = fixture.context(target, requested)?;
        let bodies = fixture.render_sources(&context)?;
        assert_contract(&context, &bodies)?;
        for path in PATHS {
            let blob = expected_blob(path, name)?;
            let expected = expected_body(path, blob, fixture.blob(blob)?)?;
            assert_eq!(body(&bodies, path)?.as_bytes(), expected, "{name}: {path}");
            witnesses += 1;
        }
    }
    assert_eq!(witnesses, 60);
    Ok(())
}

#[test]
fn fourteen_independent_owner_contexts_preserve_historical_loader_mekanism_and_menu_apis()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut contexts = 0;
    for (target, _) in SUPPORTED_TARGETS {
        let mut requested: Vec<&[&str]> = vec![&[]];
        if matches!(target, "1.19.2" | "1.19.4") {
            requested.push(&["image_resources"]);
        }
        if target == "1.19.2" {
            requested.extend([
                &["client_manager_gui"][..],
                &["client_manager_gui", "image_resources"][..],
            ]);
        }
        for owner_set in requested {
            let context = fixture.context(target, owner_set)?;
            assert_contract(&context, &fixture.render_sources(&context)?)?;
            contexts += 1;
        }
        if target != "1.19.2" {
            assert!(
                fixture.context(target, &["client_manager_gui"]).is_err(),
                "GUI support widened"
            );
        }
        if !matches!(target, "1.19.2" | "1.19.4") {
            assert!(
                fixture.context(target, &["image_resources"]).is_err(),
                "image support widened"
            );
        }
    }
    assert_eq!(contexts, 14);
    Ok(())
}

#[test]
fn gui_and_image_source_proofs_have_only_evidenced_prerequisites() -> Result<()> {
    let fixture = Fixture::load()?;
    let gui = fixture.context("1.19.2", &["client_manager_gui"])?;
    let gui_enabled = gui
        .features
        .iter()
        .filter_map(|(name, value)| value.then_some(name.as_str()))
        .collect::<BTreeSet<_>>();
    assert_eq!(
        gui_enabled,
        BTreeSet::from([
            "client_manager_gui",
            "client_manager",
            "client_program_consent",
            "sfml_execution_side",
            "disk_readonly_access"
        ])
    );
    assert!(!enabled(&gui, "image_resources")?);
    assert!(!enabled(&gui, "client_frame_language")?);
    assert!(!enabled(&gui, "client_program_signing")?);
    assert!(!enabled(&gui, "packet_transport_private")?);
    let gui_bodies = fixture.render_sources(&gui)?;
    for path in [RESOURCES, CAPABILITIES] {
        let raw = expected_blob(path, "release/1.19.2")?;
        assert_eq!(
            body(&gui_bodies, path)?.as_bytes(),
            expected_body(path, raw, fixture.blob(raw)?)?
        );
    }
    let manager = fixture.context("1.19.2", &["client_manager"])?;
    assert!(!enabled(&manager, "client_manager_gui")?);
    let manager_bodies = fixture.render_sources(&manager)?;
    let raw = expected_blob(MENUS, "release/1.19.2")?;
    assert_eq!(body(&manager_bodies, MENUS)?.as_bytes(), fixture.blob(raw)?);
    for target in ["1.19.2", "1.19.4"] {
        let image = fixture.context(target, &["image_resources"])?;
        assert_eq!(
            image
                .features
                .iter()
                .filter_map(|(name, value)| value.then_some(name.as_str()))
                .collect::<BTreeSet<_>>(),
            BTreeSet::from(["image_resources", "packet_values"])
        );
        assert!(!enabled(&image, "client_manager_gui")?);
        assert_contract(&image, &fixture.render_sources(&image)?)?;
    }
    Ok(())
}

#[test]
fn common_registration_edits_reach_three_target_apis_without_historical_adoption() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::Builder::new()
        .prefix("sfm-core-registration-common-edit-")
        .tempdir()?;
    let core = temp.path().join(CORE_ROOT);
    let comment = "    // Isolated common-body proof, not a persisted core edit.\n";
    let mut proofs = 0;
    for path in PATHS {
        let class = path
            .rsplit('/')
            .next()
            .and_then(|name| name.strip_suffix(".java"))
            .ok_or_else(|| eyre::eyre!("invalid pinned source class"))?;
        let anchor = format!("public class {class} {{\n");
        let source = std::str::from_utf8(
            fixture
                .sources
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing source"))?,
        )?;
        ensure!(
            source.matches(&anchor).count() == 1,
            "common class anchor changed"
        );
        let edited = source.replacen(&anchor, &format!("{anchor}{comment}"), 1);
        let output = core.join(path);
        fs::create_dir_all(
            output
                .parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(&output, edited.as_bytes())?;
        let bytes = read_bounded(&output, MAX_SOURCE_BYTES)?;
        for (target, requested) in [
            ("1.19.2", &["client_manager_gui", "image_resources"][..]),
            ("1.19.4", &["image_resources"][..]),
            ("26.1.2", &[][..]),
        ] {
            let context = fixture.context(target, requested)?;
            let selected = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
            ensure!(
                selected
                    .inputs
                    .get(path)
                    .is_some_and(|input| input.input == path),
                "common edit routed away"
            );
            let before = render_java_source(source, &context)?;
            let after = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            assert_eq!(
                after,
                before.replacen(&anchor, &format!("{anchor}{comment}"), 1)
            );
            assert_eq!(after.replacen(comment, "", 1), before);
            proofs += 1;
        }
    }
    assert_eq!(proofs, 9);
    Ok(())
}

#[test]
fn exact_four_capability_normalizations_and_all_raw_goldens_fail_closed_on_other_changes()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut normalized = 0;
    for spec in RAW_SPECS {
        let raw = fixture.blob(spec.blob)?;
        let expected = expected_body(spec.path, spec.blob, raw)?;
        if spec.crlf_count > 0 {
            assert_eq!(spec.path, CAPABILITIES);
            assert_eq!(raw.len() - expected.len(), spec.crlf_count);
            assert_eq!(
                raw.iter().filter(|byte| **byte == b'\n').count(),
                expected.iter().filter(|byte| **byte == b'\n').count()
            );
            assert_eq!(expected.last(), Some(&b'\n'));
            assert!(!expected.contains(&b'\r'));
            assert!(
                expected_body(spec.path, spec.blob, &expected).is_err(),
                "normalized input cannot replace the raw witness"
            );
            normalized += 1;
        } else {
            assert_eq!(raw, expected);
        }
        let mut token_edit = raw.to_vec();
        token_edit[0] = b'G';
        for changed in [
            token_edit,
            [&[0xef, 0xbb, 0xbf][..], raw].concat(),
            raw[..raw.len() - 1].to_vec(),
            [raw, b" "].concat(),
            std::str::from_utf8(raw)?.replace('\n', "\r\n").into_bytes(),
        ] {
            assert!(expected_body(spec.path, spec.blob, &changed).is_err());
        }
        let wrong = if spec.path == CAPABILITIES {
            MENUS
        } else {
            CAPABILITIES
        };
        assert!(expected_body(wrong, spec.blob, raw).is_err());
    }
    assert_eq!(normalized, 4);
    Ok(())
}
