//! Five genuine signing runtime/UI providers through the actual core collector.
//! Raw objects and counterfactual hashes are deliberate offline test witnesses;
//! never fallback source inputs. Register only after exact source/rule promotion.
//! No worker, screen, store, key, packet, Java or runtime operation is executed.
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

const LEDGER: &str = "docs/tasks/sfm-core-signing-runtime-ui-slice.json";
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const TEN: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
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
const OPTIONAL: [&str; 3] = [
    "single_line_input",
    "font_formatted_text",
    "editor_overlay_push",
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
const DEFINITIONS: [(&str, &[&str], &[&str]); 14] = [
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
        "editor_overlay_push",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "font_formatted_text",
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
    ("single_line_input", &["1.19.2", "1.19.4"], &[]),
];
struct SourceSpec {
    path: &'static str,
    bytes: usize,
    digest: &'static str,
    anchor: &'static str,
}
const SOURCES: [SourceSpec; 5] = [
    SourceSpec {
        path: "src/main/java/ca/teamdman/sfm/client/program/signing/ClientProgramSigningRuntime.java",
        bytes: 7610,
        digest: "sha256:d2538e0bb576bf3156c572bedc65c136ef4e1ec59940340ddc56650784111680",
        anchor: "public final class ClientProgramSigningRuntime {",
    },
    SourceSpec {
        path: "src/main/java/ca/teamdman/sfm/client/screen/ClientProgramSigningScreen.java",
        bytes: 17865,
        digest: "sha256:8925a8480b4039110c8949fb8427ffee39d0bfdc6a979bb94cb9f45cdb094f6e",
        anchor: "public final class ClientProgramSigningScreen extends Screen {",
    },
    SourceSpec {
        path: "src/main/java/ca/teamdman/sfm/client/screen/ClientSigningKeysScreen.java",
        bytes: 16573,
        digest: "sha256:c26fdacbb6b52913c61adca094a241cd2f63071d779355c0898b2b12465629cd",
        anchor: "public final class ClientSigningKeysScreen extends Screen {",
    },
    SourceSpec {
        path: "src/main/java/ca/teamdman/sfm/client/screen/widget/SFMSigningPassphraseWidget.java",
        bytes: 3818,
        digest: "sha256:0c4a8f8b07dc8ebefeda473b3f2f437b4f3c85f7570b1da6694f5afdc087f811",
        anchor: "public final class SFMSigningPassphraseWidget extends AbstractWidget implements AutoCloseable {",
    },
    SourceSpec {
        path: "src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextEditScreenOverlayOpenContext.java",
        bytes: 585,
        digest: "sha256:2e2d9fa9829fd64d1369945fcf8626775b97ee932b2c9d1a896ad73e1f5c481a",
        anchor: "public record SFMTextEditScreenOverlayOpenContext(",
    },
];
struct RawSpec {
    oid: &'static str,
    bytes: usize,
    digest: &'static str,
    lf: usize,
}
const RAW: [RawSpec; 7] = [
    RawSpec {
        oid: "0edc4adccec5cd446618b0f63a74bc36abd3b4e7",
        bytes: 7610,
        digest: "sha256:d2538e0bb576bf3156c572bedc65c136ef4e1ec59940340ddc56650784111680",
        lf: 136,
    },
    RawSpec {
        oid: "b78d588f7728d31dab7ced0cc5a63b0f5c60b42f",
        bytes: 16889,
        digest: "sha256:fd0e3662503e6a20333aae7a61882fe44955949665fb96cbf88359e42623cc3c",
        lf: 322,
    },
    RawSpec {
        oid: "0ccfed7a9286203f04fe81eb3b7e7396fc3eaadb",
        bytes: 17131,
        digest: "sha256:7a27c1b23b9651c64d456068fc5be08c3f04e5c8fe0928a2cb7f2724090b4f0a",
        lf: 332,
    },
    RawSpec {
        oid: "fd4506e439572ecbae3c83650d0a5c9b2dda4d3b",
        bytes: 15659,
        digest: "sha256:e786014db7a7cefdf5338250827d2694fad03fafea6b1cc82afe97fee25e5d5e",
        lf: 288,
    },
    RawSpec {
        oid: "fcfcf81918e0d6f0ba1be9b87ddb4485c7328532",
        bytes: 3367,
        digest: "sha256:f51c2f1ef52c535b1ba96cb56c2dc7fab260006266fcb8f6c35adf68f69f6a5f",
        lf: 61,
    },
    RawSpec {
        oid: "b10ead5b4e2bd49138daeaa953bbdc0403857c4e",
        bytes: 3075,
        digest: "sha256:a0a802a01d2f96f8ca073a427d62ef43bd56bb844cad057cf9453174eb76c5ec",
        lf: 53,
    },
    RawSpec {
        oid: "ed606eeff43f6c135c5a95e35e0bf101369ebe29",
        bytes: 585,
        digest: "sha256:2e2d9fa9829fd64d1369945fcf8626775b97ee932b2c9d1a896ad73e1f5c481a",
        lf: 20,
    },
];
const PROVIDERS: [(&str, usize, &str); 15] = [
    (
        "src/main/java/ca/teamdman/sfm/client/program/signing/ClientProgramSigningController.java",
        18631,
        "sha256:0cae5bda077697ac23402a29ee36c78eead52675c74948544ac1aab5be9eae8a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/program/signing/ClientProgramSigningCeremony.java",
        1449,
        "sha256:6c3e3db2f701b1ba398f17e84ed282b1735a244e62c51fbbe21b59073e784969",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/program/signing/ClientProgramSigningReview.java",
        4333,
        "sha256:b75d4ede4d19cd17a32ef9a95f5ab040208c6e4252933afde10dc543f2dfad2a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/program/signing/ClientSigningSecretBuffer.java",
        1316,
        "sha256:2399409dfaf0d961999d9a29e843efa50c1d1033c2725651f478eacd82d99b23",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/program/signing/ClientSigningKeyStore.java",
        19974,
        "sha256:a8d11fbcd21c0bd5a0859465e61af148aabcd13835f5f0daf0f1b8b1c241ac9c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/program/signing/ClientSigningUnlockOperation.java",
        1581,
        "sha256:a44c8641be1b64b5124975921bfc06f7820f2d38b4dbc85c707baf0f0f56b020",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/program/ClientManagerFrameRuntime.java",
        27717,
        "sha256:528094eb87f7b4f78678c26929e8e84b54d7a4c0fe1c919b73d0bc82abb4e184",
    ),
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
        "src/main/java/ca/teamdman/sfm/common/blockentity/ClientManagerBlockEntity.java",
        16044,
        "sha256:d5e8419a0d8f1ffb70a76317f92f37afcdb80a9ceb2d98423373f0277f5e974d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/registry/SFMClientActions.java",
        4852,
        "sha256:28e99bfe0fa20dcf7e646b4002ea6adab2cffcacf3b1f2e2046864048f15814c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/SFMFontUtils.java",
        15462,
        "sha256:8308845678605cf0bf052c1cfa6f93b2d98c97b4b9e2b5b879b3528c5fd4a78b",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/SFMScreenChangeHelpers.java",
        8189,
        "sha256:981c73e9953df71577e85f08fab4de2406c8d8a1f8774a125be58afd758930f2",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/SFMWidgetUtils.java",
        5692,
        "sha256:faaa1ee8a0d01f777d107b72b6e5bac9e2fd5494e1f429ba82b10175aaab9882",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/text_editor/ISFMTextEditScreenOpenContext.java",
        5790,
        "sha256:9ef78cb0a045189dc4dd0689879613eaafd5244196d98d775965a4ca9a91e026",
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    contract_changes: bool,
    definitions: BTreeMap<String, Definition>,
    context_commits: BTreeMap<String, String>,
    files: Vec<FileEvidence>,
    raw_objects: Vec<RawEvidence>,
    counterfactuals: Vec<Counterfactual>,
    current_provider_source_receipts: Vec<ProviderEvidence>,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct FileEvidence {
    path: String,
    owner: String,
    bytes: usize,
    sha256: String,
    cr_count: usize,
    final_lf: bool,
    witnesses: BTreeMap<String, Option<String>>,
    source_rule: InputVariant,
    common_edit_anchor: String,
}
#[derive(Facet)]
struct RawEvidence {
    oid: String,
    bytes: usize,
    sha256: String,
    lf_count: usize,
    cr_count: usize,
    final_lf: bool,
    bom: bool,
}
#[derive(Facet)]
struct Counterfactual {
    key: String,
    target: String,
    flags: BTreeMap<String, bool>,
    paths: BTreeMap<String, Pin>,
}
#[derive(Facet)]
struct Pin {
    bytes: usize,
    sha256: String,
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
    masks: Vec<Counterfactual>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            128 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-signing-runtime-ui-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && ledger.files.len() == 5
                && ledger.raw_objects.len() == 7
                && ledger.definitions.len() == 14
                && ledger.counterfactuals.len() == 16
                && ledger.current_provider_source_receipts.len() == 15
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
                        .collect(),
            "immutable signing UI scope changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let original = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("original signing UI contract absent: {name}"))?;
            let current = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("actual signing UI contract absent: {name}"))?;
            ensure!(
                original.supported_targets == support
                    && original.requires == requires
                    && current.supported_targets == support
                    && current.requires == requires,
                "original/current signing UI contract changed: {name}"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &RAW.iter().map(|g| g.oid.to_owned()).collect(),
        )?;
        for (golden, evidence) in RAW.iter().zip(&ledger.raw_objects) {
            ensure!(
                evidence.oid == golden.oid
                    && evidence.bytes == golden.bytes
                    && evidence.sha256 == golden.digest
                    && evidence.lf_count == golden.lf
                    && evidence.cr_count == 0
                    && evidence.final_lf
                    && !evidence.bom,
                "immutable signing raw identity changed"
            );
            verify_raw(&raw[golden.oid], golden)?;
        }
        let mut sources = BTreeMap::new();
        for (index, (spec, evidence)) in SOURCES.iter().zip(&ledger.files).enumerate() {
            let owner = if index == 4 {
                "editor_overlay_push"
            } else {
                "client_program_signing"
            };
            let support = if index == 4 {
                TEN.as_slice()
            } else {
                D2.as_slice()
            };
            ensure!(
                evidence.path == spec.path
                    && evidence.owner == owner
                    && evidence.bytes == spec.bytes
                    && evidence.sha256 == spec.digest
                    && evidence.common_edit_anchor == spec.anchor
                    && evidence.cr_count == 0
                    && evidence.final_lf
                    && evidence.witnesses == expected_witnesses(index),
                "immutable signing UI body/member contract changed"
            );
            let source = core.read_source(spec.path)?;
            ensure!(
                source.len() == spec.bytes
                    && sha256(&source) == spec.digest
                    && !source.contains(&b'\r')
                    && source.last() == Some(&b'\n'),
                "signing UI authored source changed: {}",
                spec.path
            );
            let rules =
                core.metadata.source_rules.get(spec.path).ok_or_else(|| {
                    eyre::eyre!("promote exact signing UI rule first: {}", spec.path)
                })?;
            ensure!(
                rules.len() == 1
                    && rules[0] == evidence.source_rule
                    && rules[0].input == spec.path
                    && rules[0].template
                    && rules[0].when.targets == support
                    && rules[0].when.all_features == vec![owner]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "signing UI membership/support changed: {}",
                spec.path
            );
            sources.insert(spec.path.to_owned(), source);
        }
        for ((path, length, digest), evidence) in PROVIDERS
            .iter()
            .zip(&ledger.current_provider_source_receipts)
        {
            ensure!(
                evidence.path == *path
                    && evidence.bytes == *length
                    && evidence.sha256 == *digest
                    && core
                        .metadata
                        .source_rules
                        .get(*path)
                        .cloned()
                        .unwrap_or_default()
                        == evidence.rules,
                "real signing UI provider evidence/rule changed: {path}"
            );
            let bytes = core.read_source(path)?;
            ensure!(
                bytes.len() == *length && sha256(&bytes) == *digest,
                "real signing UI provider source changed: {path}"
            );
        }
        let mut keys = BTreeSet::new();
        for mask in &ledger.counterfactuals {
            ensure!(
                D2.contains(&mask.target.as_str())
                    && mask.flags.len() == 3
                    && mask.paths.len() == 5
                    && mask
                        .flags
                        .keys()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>()
                        == OPTIONAL.into_iter().collect()
                    && keys.insert(mask.key.clone()),
                "bounded signing UI mask inventory changed"
            );
            let suffix: String = OPTIONAL
                .into_iter()
                .map(|name| if mask.flags[name] { '1' } else { '0' })
                .collect();
            ensure!(
                mask.key == format!("{}-{suffix}", mask.target),
                "counterfactual mask key changed"
            );
            for spec in &SOURCES {
                let pin = mask
                    .paths
                    .get(spec.path)
                    .ok_or_else(|| eyre::eyre!("counterfactual source pin missing"))?;
                ensure!(
                    pin.bytes > 0 && pin.sha256.starts_with("sha256:") && pin.sha256.len() == 71,
                    "counterfactual pin malformed"
                );
            }
        }
        ensure!(
            sources.values().map(Vec::len).sum::<usize>() == 46_451
                && RAW.iter().map(|g| g.bytes).sum::<usize>() == 64_316,
            "bounded signing byte totals changed"
        );
        Ok(Self {
            core,
            sources,
            raw,
            masks: ledger.counterfactuals,
        })
    }
    fn inventory(&self) -> BTreeSet<String> {
        self.sources.keys().cloned().collect()
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if !selected.inputs.contains_key(path) {
            ensure!(
                selected.omitted_paths.contains(path),
                "unaccounted UI omission"
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
            .ok_or_else(|| eyre::eyre!("real signing UI provider omitted: {path}"))?;
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
                    .ok_or_else(|| eyre::eyre!("UI source parent missing"))?,
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
                    "actual standalone input changed across contexts"
                );
            } else {
                fs::create_dir_all(
                    destination
                        .parent()
                        .ok_or_else(|| eyre::eyre!("standalone UI parent missing"))?,
                )?;
                fs::write(destination, bytes)?;
            }
        }
        Ok(())
    }
}
fn expected_oid(index: usize, name: &str) -> Option<&'static str> {
    let (environment, target) = name.split_once('/')?;
    if environment != "dev" {
        return None;
    }
    if index == 4 {
        return Some("ed606eeff43f6c135c5a95e35e0bf101369ebe29");
    }
    if !D2.contains(&target) {
        return None;
    }
    match (index, target) {
        (0, _) => Some("0edc4adccec5cd446618b0f63a74bc36abd3b4e7"),
        (1, "1.19.2") => Some("b78d588f7728d31dab7ced0cc5a63b0f5c60b42f"),
        (1, "1.19.4") => Some("0ccfed7a9286203f04fe81eb3b7e7396fc3eaadb"),
        (2, _) => Some("fd4506e439572ecbae3c83650d0a5c9b2dda4d3b"),
        (3, "1.19.2") => Some("b10ead5b4e2bd49138daeaa953bbdc0403857c4e"),
        (3, "1.19.4") => Some("fcfcf81918e0d6f0ba1be9b87ddb4485c7328532"),
        _ => None,
    }
}
fn expected_witnesses(index: usize) -> BTreeMap<String, Option<String>> {
    CONTEXTS
        .into_iter()
        .map(|(name, _)| {
            (
                name.to_owned(),
                expected_oid(index, name).map(str::to_owned),
            )
        })
        .collect()
}
fn flags_for(target: &str, dev: bool, mask: usize) -> Vec<&'static str> {
    let mut flags = if dev && D2.contains(&target) {
        SIGNING.to_vec()
    } else {
        Vec::new()
    };
    if dev {
        for (index, owner) in OPTIONAL.into_iter().enumerate() {
            if mask & (1 << index) != 0 {
                flags.push(owner);
            }
        }
    }
    flags
}
fn verify_raw(bytes: &[u8], golden: &RawSpec) -> Result<()> {
    ensure!(
        bytes.len() == golden.bytes
            && sha256(bytes) == golden.digest
            && bytes.iter().filter(|byte| **byte == b'\n').count() == golden.lf
            && !bytes.contains(&b'\r')
            && bytes.last() == Some(&b'\n')
            && !bytes.starts_with(&[239, 187, 191]),
        "immutable raw signing identity changed"
    );
    Ok(())
}
fn assert_before(body: &str, first: &str, second: &str) {
    let left = body
        .find(first)
        .unwrap_or_else(|| panic!("source anchor missing: {first}"));
    let right = body
        .find(second)
        .unwrap_or_else(|| panic!("source anchor missing: {second}"));
    assert!(
        left < right,
        "source ordering changed: {first} before {second}"
    );
}

#[test]
fn all_one_hundred_ui_git_cells_reconstruct_exact_membership_and_raw_bytes() -> Result<()> {
    let f = Fixture::load()?;
    let (mut present, mut absent) = (0, 0);
    for (name, commit) in CONTEXTS {
        let mut command = frozen_git_command(&f.core.repository);
        command.args(["ls-tree", commit, "--"]);
        for spec in &SOURCES {
            command.arg(format!("platform/minecraft/{}", spec.path));
        }
        let output = command.output()?;
        ensure!(
            output.status.success()
                && output.stdout.len() <= 64 * 1024
                && output.stderr.len() <= 64 * 1024,
            "bounded offline UI tree query failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("UI tree row framing changed"))?;
            let mut fields = header.split_whitespace();
            ensure!(
                fields.next() == Some("100644") && fields.next() == Some("blob"),
                "UI witness type/mode changed"
            );
            let oid = fields
                .next()
                .ok_or_else(|| eyre::eyre!("UI blob ID missing"))?;
            ensure!(
                fields.next().is_none() && actual.insert(path.to_owned(), oid.to_owned()).is_none(),
                "UI tree row duplicated or extended"
            );
        }
        let target = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("target missing"))?
            .1;
        let flags = if matches!(name, "dev/1.19.2" | "dev/1.19.4") {
            flags_for(target, true, 7)
        } else if name.starts_with("dev/") {
            vec!["editor_overlay_push"]
        } else {
            vec![]
        };
        let context = f.core.context(target, &flags)?;
        for (index, spec) in SOURCES.iter().enumerate() {
            let expected = expected_oid(index, name);
            assert_eq!(
                actual
                    .get(&format!("platform/minecraft/{}", spec.path))
                    .map(String::as_str),
                expected
            );
            let rendered = f.render(spec.path, &context)?;
            if let Some(oid) = expected {
                assert_eq!(
                    rendered
                        .ok_or_else(|| eyre::eyre!("historical UI omitted"))?
                        .as_bytes(),
                    f.raw[oid]
                );
                present += 1;
            } else {
                assert!(rendered.is_none());
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (18, 82));
    Ok(())
}

#[test]
fn independent_public_input_font_and_edit_masks_preserve_secrets_and_real_off_paths() -> Result<()>
{
    let f = Fixture::load()?;
    let (mut selected, mut omitted) = (0, 0);
    for mask in &f.masks {
        let bits = OPTIONAL
            .into_iter()
            .enumerate()
            .fold(0, |value, (index, name)| {
                value | (usize::from(mask.flags[name]) << index)
            });
        let flags = flags_for(&mask.target, true, bits);
        let mut context = f.core.context(&mask.target, &flags)?;
        context.environment = "unrelated-source-proof".to_owned();
        context.preset = "unrelated-preset".to_owned();
        context.projection_key = "unrelated/key".to_owned();
        for (index, spec) in SOURCES.iter().enumerate() {
            let output = f.render(spec.path, &context)?;
            if index == 4 && !mask.flags["editor_overlay_push"] {
                assert!(output.is_none());
                omitted += 1;
                continue;
            }
            let body = output.ok_or_else(|| eyre::eyre!("signing UI mask omitted"))?;
            let pin = &mask.paths[spec.path];
            assert_eq!(body.len(), pin.bytes);
            assert_eq!(sha256(body.as_bytes()), pin.sha256);
            assert!(!body.contains("{%"));
            selected += 1;
        }
        let keys = f
            .render(SOURCES[2].path, &context)?
            .ok_or_else(|| eyre::eyre!("keys UI omitted"))?;
        assert_eq!(
            keys.contains("import ca.teamdman.sfm.client.input.SFMSingleLineEditBox;"),
            mask.flags["single_line_input"]
        );
        assert_eq!(
            keys.contains("import net.minecraft.client.gui.components.EditBox;"),
            !mask.flags["single_line_input"]
        );
        if !mask.flags["single_line_input"] {
            assert!(keys.contains("private EditBox name;"));
            assert!(keys.contains("private EditBox external;"));
            assert!(!keys.contains("new SFMSingleLineEditBox"));
        }
        for anchor in [
            "private SFMSigningPassphraseWidget passphrase;",
            "private SFMSigningPassphraseWidget confirmation;",
            "new SFMSigningPassphraseWidget",
            "char[] captured = passphrase.consume();",
            "finally { Arrays.fill(captured, '\\0'); }",
        ] {
            assert!(
                keys.contains(anchor),
                "secret path must not change: {anchor}"
            );
        }
        let widget = f
            .render(SOURCES[3].path, &context)?
            .ok_or_else(|| eyre::eyre!("secret widget omitted"))?;
        assert!(!widget.contains("import net.minecraft.client.gui.components.EditBox"));
        assert!(!widget.contains("keyboardHandler"));
        assert!(widget.contains(
            "Consume clipboard/undo combinations without ever invoking a clipboard service."
        ));
        let sign = f
            .render(SOURCES[1].path, &context)?
            .ok_or_else(|| eyre::eyre!("signing UI omitted"))?;
        assert_eq!(
            sign.contains("private void openEditor()"),
            mask.flags["editor_overlay_push"]
        );
        assert_eq!(
            sign.contains("SFMTextEditScreenOverlayOpenContext"),
            mask.flags["editor_overlay_push"]
        );
        if !mask.flags["font_formatted_text"] {
            assert!(sign.contains("font.draw(pose, lines.get(scroll + index)"));
            assert!(keys.contains("font.draw(pose, line, left, y"));
            assert!(!keys.contains("SFMFontUtils.draw(pose, font, line,"));
        }
        for forbidden in [
            "client_frame_render",
            "touch_display",
            "image_resources",
            "multiplayer_packets",
            "client_manager_gui",
            "command_palette",
            "workspace_panels",
        ] {
            assert!(!context.features[forbidden]);
        }
    }
    assert_eq!((selected, omitted), (72, 8));
    Ok(())
}

#[test]
fn strict_current_prerequisites_unsupported_owners_and_non_signing_profiles_are_retained()
-> Result<()> {
    let f = Fixture::load()?;
    let profiles: [&[&str]; 5] = [
        &[],
        &["sfml_execution_side"],
        &["client_program_consent", "sfml_execution_side"],
        &[
            "client_frame_language",
            "client_manager",
            "client_program_consent",
            "disk_readonly_access",
            "sfml_execution_side",
        ],
        &["editor_overlay_push"],
    ];
    let (mut present, mut absent, mut rejected) = (0, 0, 0);
    for target in D2 {
        for flags in profiles {
            let context = f.core.context(target, flags)?;
            for (index, spec) in SOURCES.iter().enumerate() {
                let body = f.render(spec.path, &context)?;
                if index == 4 && flags.contains(&"editor_overlay_push") {
                    assert_eq!(
                        body.ok_or_else(|| eyre::eyre!("overlay context omitted"))?
                            .as_bytes(),
                        f.raw["ed606eeff43f6c135c5a95e35e0bf101369ebe29"]
                    );
                    present += 1;
                } else {
                    assert!(body.is_none());
                    absent += 1;
                }
            }
        }
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
            rejected += 1;
        }
        let mut context = f.core.context(target, &SIGNING)?;
        context.features.remove("single_line_input");
        assert!(f.render(SOURCES[2].path, &context).is_err());
    }
    assert_eq!((present, absent, rejected), (2, 48, 20));
    for target in TEN.into_iter().filter(|target| !D2.contains(target)) {
        assert!(f.core.context(target, &SIGNING).is_err());
        assert!(f.core.context(target, &["single_line_input"]).is_err());
        let context = f.core.context(target, &["editor_overlay_push"])?;
        assert!(f.render(SOURCES[4].path, &context)?.is_some());
    }
    Ok(())
}

#[test]
fn human_acknowledgement_cas_connection_worker_and_secret_boundaries_remain_source_exact()
-> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        let flags = flags_for(target, true, 7);
        let context = f.core.context(target, &flags)?;
        let runtime = f
            .render(SOURCES[0].path, &context)?
            .ok_or_else(|| eyre::eyre!("runtime omitted"))?;
        let (_, open) = runtime
            .split_once("public static String open(")
            .ok_or_else(|| eyre::eyre!("explicit signing entry absent"))?;
        assert_before(open, "!minecraft.isSameThread()", "observeConnection();");
        assert_before(open, "!privateWorld()", "closeActive();");
        assert_before(
            open,
            "ClientManagerFrameRuntime.liveIdentityFor(identity).isEmpty()",
            "closeActive();",
        );
        assert_before(
            open,
            "active = new ClientProgramSigningController",
            "ClientManagerSigningResponses.setReceiver",
        );
        assert_before(
            open,
            "ClientManagerSigningResponses.setReceiver",
            "SFMScreenChangeHelpers.setOrPushScreen",
        );
        for anchor in [
            "body -> ClientProgramSigningController.compileLocally(body, SFMClientActions::programmaticBinding)",
            "response -> minecraft.execute(() -> {",
            "if (active != null) active.receive(response);",
            "target.connectionId().equals(connectionId)",
            "manager.isRemoved() || manager.worldId() == null",
            "snapshot.incarnation(), snapshot.revision(), snapshot.body()",
            "server.isSingleplayer() && !server.isPublished()",
            "ClientManagerSigningResponses.setReceiver(ignored -> {});",
            "SFMProperties.clientRunMode() != SFMProperties.ClientRunMode.GAME_PUPPET",
            "selected.equals(root) || !selected.startsWith(root)",
        ] {
            assert!(
                runtime.contains(anchor),
                "runtime authority anchor: {anchor}"
            );
        }
        let sign = f
            .render(SOURCES[1].path, &context)?
            .ok_or_else(|| eyre::eyre!("sign UI omitted"))?;
        let (_, sign_method) = sign
            .split_once("private void sign()")
            .ok_or_else(|| eyre::eyre!("explicit Sign method absent"))?;
        assert_before(
            sign_method,
            "if (!sign.active || key == null) return;",
            "char[] captured = passphrase.consume();",
        );
        assert_before(
            sign_method,
            "char[] captured = passphrase.consume();",
            "controller.sign(",
        );
        for anchor in [
            "controller.save(base, source)",
            "Never switch this compare-and-swap base to a later live revision",
            "ceremony.bind(challenge, key == null ? null : key.identity().fingerprint())",
            "ready && key != null && ceremony.ready() && passphrase.usable()",
            "strokes.size() < 2048",
            "setInitialFocus(cancel);",
            "controller.close();",
            "lines.stream().limit(128)",
            "text.length() >= 512",
            "passphrase widgets and cosmetic strokes are excluded",
        ] {
            assert!(
                sign.contains(anchor),
                "review/CAS/ceremony anchor: {anchor}"
            );
        }
        let keys = f
            .render(SOURCES[2].path, &context)?
            .ok_or_else(|| eyre::eyre!("keys UI omitted"))?;
        let (_, apply) = keys
            .split_once("private void apply()")
            .ok_or_else(|| eyre::eyre!("explicit key operation absent"))?;
        assert_before(
            apply,
            "if (!apply.active) return;",
            "char[] captured = passphrase.consume();",
        );
        assert_before(
            apply,
            "char[] captured = passphrase.consume();",
            "runJob(captured, secret ->",
        );
        for anchor in [
            "private int delay = 40;",
            "delay == 0 && passphrase.usable()",
            "passphrase.matches(confirmation)",
            "ClientSigningUiWorker.executor().execute",
            "finally { Arrays.fill(captured, '\\0'); }",
            "if (closed) return;",
            "New identity created. Old key and existing signatures are unchanged.",
            "Cancel clears input; an accepted file operation may still finish.",
            "if (!directory.equals(resolved.getParent()))",
            "if (files.size() > 64)",
            "Public inspection failure does not expose private paths",
        ] {
            assert!(
                keys.contains(anchor),
                "key operation/worker boundary: {anchor}"
            );
        }
        for (path, _, _) in PROVIDERS {
            assert!(!f.provider(path, &context)?.contains("{%"));
        }
        assert!(
            f.provider(PROVIDERS[0].0, &context)?
                .contains("compileLocally(")
        );
        assert!(
            f.provider(PROVIDERS[3].0, &context)?
                .contains("public char[] consume()")
        );
        assert!(
            f.provider(PROVIDERS[10].0, &context)?
                .contains("programmaticBinding(")
        );
        assert!(
            f.provider(PROVIDERS[12].0, &context)?
                .contains("public static void setOrPushScreen(Screen screen)")
        );
        // Missing Properties/input services/human action and full Java/runtime
        // closure remain ledger obligations, not invented or exercised here.
    }
    Ok(())
}

#[test]
fn raw_witness_and_guard_mutations_are_refused_without_implicit_normalization() -> Result<()> {
    let f = Fixture::load()?;
    for golden in &RAW {
        let bytes = &f.raw[golden.oid];
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
            assert!(verify_raw(&mutant, golden).is_err());
        }
    }
    let context = f.core.context("1.19.2", &flags_for("1.19.2", true, 7))?;
    let original = std::str::from_utf8(&f.sources[SOURCES[1].path])?;
    let broken = original.replacen(
        "{% if features.editor_overlay_push %}",
        "{% if features.unapproved_signing_editor %}",
        1,
    );
    assert!(render_java_source(&broken, &context).is_err());
    let unclosed = format!("{original}{{% if features.client_program_signing %}}\n");
    assert!(render_java_source(&unclosed, &context).is_err());
    let mut widened = f.bounded_metadata();
    let rule = widened
        .source_rules
        .get_mut(SOURCES[0].path)
        .ok_or_else(|| eyre::eyre!("runtime rule absent"))?;
    rule[0].when.all_features = vec!["unapproved_signing_runtime".to_owned()];
    assert!(select_core_inputs(&widened, &context, &f.inventory()).is_err());
    Ok(())
}

#[test]
fn owner_off_inputs_are_omitted_before_invalid_utf8_and_unknown_directive_reads() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    for spec in &SOURCES {
        let path = root.join(spec.path);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("poison UI parent absent"))?,
        )?;
        fs::write(path, b"{% if features.unapproved_signing_owner %}\n\xff")?;
    }
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    let metadata = f.bounded_metadata();
    for target in D2 {
        for flags in [
            &[] as &[&str],
            &["sfml_execution_side"],
            &[
                "client_frame_language",
                "client_manager",
                "client_program_consent",
                "disk_readonly_access",
                "sfml_execution_side",
            ],
        ] {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for spec in &SOURCES {
                assert!(selected.omitted_paths.contains(spec.path));
                assert!(!artifacts.contains_key(spec.path));
            }
        }
    }
    Ok(())
}

#[test]
fn automatic_java_template_false_preserves_real_collector_origins_on_all_ten_targets() -> Result<()>
{
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let original = std::str::from_utf8(&f.sources[SOURCES[4].path])?;
    let edited = format!(
        "{original}{{% if features.editor_overlay_push %}}\n// Isolated overlay Java probe.\n{{% endif %}}\n"
    );
    fs::write(root.join(SOURCES[4].path), &edited)?;
    let mut metadata = f.bounded_metadata();
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let inventory = discover_core_source_files(&root)?;
    let mut outputs = 0;
    for target in TEN {
        let flags = if D2.contains(&target) {
            flags_for(target, true, 7)
        } else {
            vec!["editor_overlay_push"]
        };
        let context = f.core.context(target, &flags)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (index, spec) in SOURCES.iter().enumerate() {
            if index != 4 && !D2.contains(&target) {
                assert!(!artifacts.contains_key(spec.path));
                continue;
            }
            let artifact = &artifacts[spec.path];
            let output = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = output
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("generated Java banner absent"))?;
            if index == 4 {
                assert_eq!(body, render_java_source(&edited, &context)?);
                assert!(body.contains("// Isolated overlay Java probe."));
            } else {
                let name = format!("dev/{target}");
                let oid = expected_oid(index, &name)
                    .ok_or_else(|| eyre::eyre!("historical UI object absent"))?;
                assert_eq!(body.as_bytes(), f.raw[oid]);
            }
            assert!(!body.contains("{%"));
            assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{}", spec.path));
            assert!(artifact.overlay.is_none());
            outputs += 1;
        }
    }
    assert_eq!(outputs, 18);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}

#[test]
fn isolated_common_edits_reach_two_ui_versions_and_ten_overlay_versions() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut edits = BTreeMap::new();
    for spec in &SOURCES {
        let original = std::str::from_utf8(&f.sources[spec.path])?;
        assert_eq!(original.matches(spec.anchor).count(), 1);
        let edited = original.replacen(
            spec.anchor,
            &format!("// Isolated common signing UI edit.\n{}", spec.anchor),
            1,
        );
        fs::write(root.join(spec.path), &edited)?;
        edits.insert(spec.path.to_owned(), edited);
    }
    let inventory = discover_core_source_files(&root)?;
    let metadata = f.bounded_metadata();
    let mut outputs = 0;
    for target in TEN {
        let flags = if D2.contains(&target) {
            flags_for(target, true, 7)
        } else {
            vec!["editor_overlay_push"]
        };
        let context = f.core.context(target, &flags)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (index, spec) in SOURCES.iter().enumerate() {
            if index != 4 && !D2.contains(&target) {
                continue;
            }
            let artifact = &artifacts[spec.path];
            let output = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = output
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("generated Java banner absent"))?;
            assert_eq!(body, render_java_source(&edits[spec.path], &context)?);
            assert!(body.contains("// Isolated common signing UI edit."));
            outputs += 1;
        }
    }
    assert_eq!(outputs, 18);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}
