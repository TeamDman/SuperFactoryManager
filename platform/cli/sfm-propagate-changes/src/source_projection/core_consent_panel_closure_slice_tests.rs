//! Genuine consent-panel human UI and shared widgets through production selection.
//! Promote exact sources/rules/ledger before registration. No Java/key/store/screen,
//! packet, worker or process is executed. Git blobs are offline golden evidence only.
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
use super::core_workspace_host_current_contract::reviewed_pre_typed_palette_host;
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

const LEDGER: &str = "docs/tasks/sfm-core-consent-panel-closure-slice.json";
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const TEN: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
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
const SOURCES: [(&str, usize, &str); 7] = [
    (
        "src/main/java/ca/teamdman/sfm/client/program/ClientProgramConsentsPanel.java",
        21577,
        "sha256:4dc2efef16687e8423071a4ca3a5b754d8677c50a661fbbe868aaffa03b5994c",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/action/SFMClientProgramConsentControlAction.java",
        2663,
        "sha256:3554401a88a133e9ad90db772a283324c4adf7536a43d15fbee1367b730eba05",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/action/SFMClientProgramConsentActions.java",
        1120,
        "sha256:019ebc54546340232d58ee62e870762f14930485d1ffa455ca2ae97743430b1f",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPanelWidget.java",
        1663,
        "sha256:310424615b7e81ed3779647fe045f11ec806a04e38a93a601b1eceec04a530f9",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPanelWidgetHost.java",
        10639,
        "sha256:d54d25c11339b8adc8faf3f686ddd89d6e7935dc40da00e5a803588f59d3be00",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPanelActionButton.java",
        5199,
        "sha256:0776bac2cf6f05a06d73e23f47a286cab92cc6285cbe1a62e9bba58fb091c29a",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPanelActionExecution.java",
        2279,
        "sha256:036b2aee5628367e8e2fa45393041881aee9c4eddad040a90f137c356cf6c5fc",
    ),
];
const RAW: [(&str, usize, &str, usize); 9] = [
    (
        "00a5f3f16fbcaab546f8979a776ff399406cd7be",
        4690,
        "sha256:d26a7eca1494df90dd36855e2a281b7e52e07504d75ec45860edcf1d29536222",
        138,
    ),
    (
        "12fdd7e1700970e31fd7d93db7b46ea46efd1ffa",
        2663,
        "sha256:3554401a88a133e9ad90db772a283324c4adf7536a43d15fbee1367b730eba05",
        43,
    ),
    (
        "2922f7ba7c90c9b7db632859f4ec589d879609f8",
        854,
        "sha256:ebd24f2e00e29812b618f87029e2a43d21ce05d24c566f0606120fd3ff432a4d",
        16,
    ),
    (
        "520596a61672d6096e41fee9525e22d4b43e6e9c",
        1243,
        "sha256:2d730302e1a555f2392a11b54f2a23bff82ad998fd19b21002768b10732458f9",
        42,
    ),
    (
        "56e1d31e01cac6f0295a0b5df5e7e7f7a60d8276",
        1344,
        "sha256:b481c2073872a770019bac20d597d163bfd87a9c27f05cad0c46b086873a5f50",
        44,
    ),
    (
        "57bc5ef39d8452537cb1ef78527ef84b943f9fbc",
        2141,
        "sha256:509e8f545ee3fa151825f76d07f09635f08ebdb7c8833ef0de5692965e5aa4fc",
        55,
    ),
    (
        "62af0b6c572755ad06902bdefc55df7dbca88851",
        4765,
        "sha256:d05ee96e2f7e45c07ea5f284a899329fb10bd5e9a292cedc2347548c77ae2934",
        140,
    ),
    (
        "bf1192a40905b12aea7aa59268403546222ce349",
        10639,
        "sha256:d54d25c11339b8adc8faf3f686ddd89d6e7935dc40da00e5a803588f59d3be00",
        289,
    ),
    (
        "c1a66e74d1a219f67efe38216198c3c08731f0b8",
        20663,
        "sha256:2a6bebb60cee3226460b281cc308ae741df4d651ad6957e25b58b48a7b0096e8",
        333,
    ),
];
const DEFINITIONS: [(&str, &[&str], &[&str]); 18] = [
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
        "client_theme",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "command_palette",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &["client_actions", "client_theme", "keyboard_profiles"],
    ),
    ("confirmation_review_callbacks", &["1.19.2", "1.19.4"], &[]),
    (
        "disk_readonly_access",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "keyboard_profiles",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &["client_actions"],
    ),
    (
        "packet_computation",
        &["1.19.2", "1.19.4"],
        &["packet_values", "runtime_resource_cleanup"],
    ),
    ("packet_values", &["1.19.2", "1.19.4"], &[]),
    ("runtime_resource_cleanup", &["1.19.2", "1.19.4"], &[]),
    ("sfml_execution_side", &["1.19.2", "1.19.4"], &[]),
    ("workspace_panel_lookup", &["1.19.2", "1.19.4"], &[]),
    (
        "workspace_panels",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    ("workspace_widget_hosts", &["1.19.2", "1.19.4"], &[]),
];
const UI: [&str; 7] = [
    "client_actions",
    "client_program_consent",
    "confirmation_review_callbacks",
    "sfml_execution_side",
    "workspace_panel_lookup",
    "workspace_panels",
    "workspace_widget_hosts",
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
    primary_owner: String,
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
    enabled_features: Vec<String>,
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
    rules: Option<Vec<InputVariant>>,
}
struct Fixture {
    core: CoreTestFixture,
    ledger: Ledger,
    sources: BTreeMap<String, Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            256 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-consent-panel-closure-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && ledger.files.len() == 7
                && ledger.raw_objects.len() == 9
                && ledger.definitions.len() == 18
                && ledger.counterfactuals.len() == 12
                && ledger.current_provider_source_receipts.len() == 19
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(key, oid)| (key.to_owned(), oid.to_owned()))
                        .collect(),
            "immutable consent UI scope changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let original = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("original UI contract absent: {name}"))?;
            let current = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("current UI contract absent: {name}"))?;
            ensure!(
                original.supported_targets == support
                    && original.requires == requires
                    && current.supported_targets == support
                    && current.requires == requires,
                "consent UI must not invent/change global feature prerequisites: {name}"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &RAW.iter().map(|(oid, _, _, _)| (*oid).to_owned()).collect(),
        )?;
        for ((oid, length, digest, lf), evidence) in RAW.into_iter().zip(&ledger.raw_objects) {
            ensure!(
                evidence.oid == oid
                    && evidence.bytes == length
                    && evidence.sha256 == digest
                    && evidence.lf_count == lf
                    && evidence.cr_count == 0
                    && !evidence.bom
                    && evidence.final_lf,
                "immutable consent raw evidence changed"
            );
            verify_raw(&raw[oid], length, digest, lf)?;
        }
        let mut sources = BTreeMap::new();
        for (index, ((path, length, digest), evidence)) in
            SOURCES.into_iter().zip(&ledger.files).enumerate()
        {
            let owner = if index < 3 {
                "client_program_consent"
            } else if index == 6 {
                "client_actions"
            } else {
                "workspace_widget_hosts"
            };
            ensure!(
                evidence.path == path
                    && evidence.primary_owner == owner
                    && evidence.bytes == length
                    && evidence.sha256 == digest
                    && evidence.cr_count == 0
                    && evidence.final_lf
                    && evidence.witnesses.len() == 20,
                "immutable authored UI evidence changed"
            );
            let source = core.read_source(path)?;
            ensure!(
                source.len() == length
                    && sha256(&source) == digest
                    && source.last() == Some(&b'\n')
                    && !source.contains(&b'\r'),
                "consent UI source changed: {path}"
            );
            let rules = core
                .metadata
                .source_rules
                .get(path)
                .ok_or_else(|| eyre::eyre!("promote exact consent UI rules first: {path}"))?;
            ensure!(
                rules.as_slice() == [evidence.source_rule.clone()]
                    && rules[0].input == path
                    && rules[0].template
                    && rules[0].when.targets == D2
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty()
                    && rules[0].when.all_features == expected_requirements(index),
                "UI-local conjunction/peer ownership changed: {path}"
            );
            ensure!(
                !evidence.common_edit_anchor.is_empty()
                    && std::str::from_utf8(&source)?
                        .matches(&evidence.common_edit_anchor)
                        .count()
                        == 1,
                "common source anchor changed"
            );
            sources.insert(path.to_owned(), source);
        }
        for provider in &ledger.current_provider_source_receipts {
            let source = core.read_source(&provider.path)?;
            let pinned = if provider.path.ends_with("/SFMScreenMultiplexer.java") {
                reviewed_pre_typed_palette_host(std::str::from_utf8(&source)?)?.into_bytes()
            } else {
                source.clone()
            };
            ensure!(
                pinned.len() == provider.bytes
                    && sha256(&pinned) == provider.sha256
                    && current_ui_provider_rules_match(&core.metadata, provider)?,
                "unchanged actual UI peer/rule moved: {}",
                provider.path
            );
        }
        let mut keys = BTreeSet::new();
        for profile in &ledger.counterfactuals {
            ensure!(
                D2.contains(&profile.target.as_str())
                    && keys.insert(profile.key.clone())
                    && profile.paths.len() == 7,
                "independent UI profile scope changed"
            );
            let enabled: Vec<_> = profile
                .enabled_features
                .iter()
                .map(String::as_str)
                .collect();
            let context = core.context(&profile.target, &enabled)?;
            ensure!(
                UI.into_iter().all(|name| context.features[name]),
                "UI proof needs real current host/lookup/widgets/confirmation, not stubs"
            );
        }
        Ok(Self {
            core,
            ledger,
            sources,
            raw,
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
    fn full_context(&self, target: &str) -> Result<ProjectionContext> {
        let profile = self
            .ledger
            .counterfactuals
            .iter()
            .find(|mask| mask.key == format!("{target}/signing_palette"))
            .ok_or_else(|| eyre::eyre!("full preserved profile absent"))?;
        self.core.context(
            target,
            &profile
                .enabled_features
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>(),
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
        for (path, bytes) in &self.sources {
            let file = root.join(path);
            fs::create_dir_all(
                file.parent()
                    .ok_or_else(|| eyre::eyre!("isolated UI parent absent"))?,
            )?;
            fs::write(file, bytes)?;
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
            let file = root.join(&input.input);
            if file.exists() {
                ensure!(
                    read_bounded(&file, 16 * 1024 * 1024)? == bytes,
                    "standalone project inputs changed across isolated UI contexts"
                );
            } else {
                fs::create_dir_all(
                    file.parent()
                        .ok_or_else(|| eyre::eyre!("isolated project parent absent"))?,
                )?;
                fs::write(file, bytes)?;
            }
        }
        Ok(())
    }
}
// Reviewed current membership only. The sealed ledger still describes the
// original action-only rule; no body pin, target support or original mask changes.
fn current_ui_provider_rules_match(
    metadata: &CoreProjectInputs,
    provider: &ProviderEvidence,
) -> Result<bool> {
    const CONTEXT: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionContext.java";
    let current = metadata.source_rules.get(&provider.path).cloned();
    let helper_identity = match provider.path.as_str() {
        "src/main/java/ca/teamdman/sfm/client/action/SFMActionElement.java" => Some((
            769,
            "sha256:09d0b48991f9d0f2096f86324eee5a87c8b06e0dfb51e92383214cae63a8b464",
        )),
        "src/main/java/ca/teamdman/sfm/client/action/SFMActionElementAudit.java" => Some((
            2871,
            "sha256:d8503b8b46c3143acff87ac186d21c871768e2ea754a34a23d3266ba45a1bbdd",
        )),
        _ => None,
    };
    if let Some((bytes, digest)) = helper_identity {
        ensure!(
            provider.bytes == bytes && provider.sha256 == digest,
            "immutable helper source identity changed"
        );
        let historical = provider
            .rules
            .as_ref()
            .ok_or_else(|| eyre::eyre!("immutable helper historical rule absent"))?;
        ensure!(
            historical.len() == 1
                && historical[0].input == provider.path
                && !historical[0].template
                && historical[0].when.targets == D2
                && historical[0].when.all_features == ["workspace_panels"]
                && historical[0].when.any_features.is_empty()
                && historical[0].when.none_features.is_empty(),
            "immutable helper historical workspace-only contract changed"
        );
        let mut exact_current = historical.clone();
        exact_current[0].when.all_features.clear();
        exact_current[0].when.any_features = vec![
            "workspace_panels".to_owned(),
            "manager_editor_actions".to_owned(),
        ];
        return Ok(current == Some(exact_current));
    }
    if provider.path != CONTEXT {
        // Every other provider retains its original strict comparison.
        return Ok(current == provider.rules);
    }
    ensure!(
        provider.bytes == 2894
            && provider.sha256
                == "sha256:2713d367601d6441a697e2c25650a7e187a4157f7785e0c35e407a0650e97a68",
        "immutable UI Context body evidence changed"
    );
    let historical = provider
        .rules
        .as_ref()
        .ok_or_else(|| eyre::eyre!("immutable UI Context historical rule absent"))?;
    ensure!(
        historical.len() == 1
            && historical[0].input == CONTEXT
            && historical[0].template
            && historical[0].when.targets.is_empty()
            && historical[0].when.all_features == ["client_actions"]
            && historical[0].when.any_features.is_empty()
            && historical[0].when.none_features.is_empty(),
        "immutable UI Context historical action-only contract changed"
    );
    let mut exact_current = historical.clone();
    exact_current[0].when.all_features.clear();
    exact_current[0].when.any_features =
        vec!["client_actions".to_owned(), "workspace_panels".to_owned()];
    Ok(current == Some(exact_current))
}

#[test]
fn reviewed_context_carrier_refinement_refuses_current_or_historical_rule_drift() -> Result<()> {
    let fixture = Fixture::load()?;
    const CONTEXT: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionContext.java";
    let provider = fixture
        .ledger
        .current_provider_source_receipts
        .iter()
        .find(|row| row.path == CONTEXT)
        .ok_or_else(|| eyre::eyre!("sealed UI Context provider absent"))?;
    let current = fixture
        .core
        .metadata
        .source_rules
        .get(CONTEXT)
        .ok_or_else(|| eyre::eyre!("promote exact current Context OR rule first"))?;
    assert!(current_ui_provider_rules_match(
        &fixture.core.metadata,
        provider
    )?);
    assert_eq!(
        provider
            .rules
            .as_ref()
            .map(|rules| rules[0].when.all_features.as_slice()),
        Some(["client_actions".to_owned()].as_slice())
    );

    // Every current rule field stays exact. Unknown owner names, duplicate
    // rules, reversed OR order and the old rule cannot silently be accepted.
    let mut missing = fixture.core.metadata.clone();
    missing.source_rules.remove(CONTEXT);
    assert!(!current_ui_provider_rules_match(&missing, provider)?);
    let historical = provider
        .rules
        .as_ref()
        .ok_or_else(|| eyre::eyre!("historical Context rule absent"))?;
    let mut reverted = fixture.core.metadata.clone();
    reverted
        .source_rules
        .insert(CONTEXT.to_owned(), historical.clone());
    assert!(!current_ui_provider_rules_match(&reverted, provider)?);
    let mut duplicated = fixture.core.metadata.clone();
    let mut duplicate_rules = current.clone();
    duplicate_rules.push(current[0].clone());
    duplicated
        .source_rules
        .insert(CONTEXT.to_owned(), duplicate_rules);
    assert!(!current_ui_provider_rules_match(&duplicated, provider)?);
    for field in 0..8 {
        let mut metadata = fixture.core.metadata.clone();
        let rules = metadata
            .source_rules
            .get_mut(CONTEXT)
            .ok_or_else(|| eyre::eyre!("current Context rule absent"))?;
        match field {
            0 => rules[0].input.push_str(".unreviewed"),
            1 => rules[0].template = false,
            2 => rules[0].when.targets.push("1.19.2".to_owned()),
            3 => rules[0].when.all_features.push("client_actions".to_owned()),
            4 => rules[0]
                .when
                .any_features
                .push("unapproved_context_owner".to_owned()),
            5 => rules[0].when.any_features.reverse(),
            6 => {
                rules[0].when.any_features.remove(1);
            }
            7 => rules[0]
                .when
                .none_features
                .push("client_actions".to_owned()),
            _ => unreachable!("bounded current-rule controls"),
        };
        assert!(
            !current_ui_provider_rules_match(&metadata, provider)?,
            "unreviewed current rule field {field} was accepted"
        );
    }

    // Validate the original rule before deriving the reviewed delta. Do not
    // reinterpret a widened or missing historical ledger as a new baseline.
    for field in 0..10 {
        let mut changed = ProviderEvidence {
            path: provider.path.clone(),
            bytes: provider.bytes,
            sha256: provider.sha256.clone(),
            rules: provider.rules.clone(),
        };
        match field {
            0 => changed.bytes += 1,
            1 => changed.sha256.push('0'),
            2 => changed.rules = None,
            3 => {
                let rules = changed
                    .rules
                    .as_mut()
                    .ok_or_else(|| eyre::eyre!("historical controls need rules"))?;
                rules.push(rules[0].clone());
            }
            4 => changed
                .rules
                .as_mut()
                .ok_or_else(|| eyre::eyre!("historical rules"))?[0]
                .input
                .push_str(".unreviewed"),
            5 => {
                changed
                    .rules
                    .as_mut()
                    .ok_or_else(|| eyre::eyre!("historical rules"))?[0]
                    .template = false
            }
            6 => changed
                .rules
                .as_mut()
                .ok_or_else(|| eyre::eyre!("historical rules"))?[0]
                .when
                .targets
                .push("1.19.2".to_owned()),
            7 => changed
                .rules
                .as_mut()
                .ok_or_else(|| eyre::eyre!("historical rules"))?[0]
                .when
                .all_features
                .push("workspace_panels".to_owned()),
            8 => changed
                .rules
                .as_mut()
                .ok_or_else(|| eyre::eyre!("historical rules"))?[0]
                .when
                .any_features
                .push("workspace_panels".to_owned()),
            9 => changed
                .rules
                .as_mut()
                .ok_or_else(|| eyre::eyre!("historical rules"))?[0]
                .when
                .none_features
                .push("client_actions".to_owned()),
            _ => unreachable!("bounded historical-rule controls"),
        }
        assert!(
            current_ui_provider_rules_match(&fixture.core.metadata, &changed).is_err(),
            "unreviewed historical field {field} became a baseline"
        );
    }

    // Two reviewed helpers use the current consumer union; the remaining
    // sixteen providers retain historical equality, including None.
    let mut unrelated = 0;
    let mut refined_helpers = 0;
    for other in fixture
        .ledger
        .current_provider_source_receipts
        .iter()
        .filter(|row| row.path != CONTEXT)
    {
        if matches!(
            other.path.as_str(),
            "src/main/java/ca/teamdman/sfm/client/action/SFMActionElement.java"
                | "src/main/java/ca/teamdman/sfm/client/action/SFMActionElementAudit.java"
        ) {
            assert!(current_ui_provider_rules_match(
                &fixture.core.metadata,
                other
            )?);
            assert_ne!(
                fixture.core.metadata.source_rules.get(&other.path).cloned(),
                other.rules
            );
            refined_helpers += 1;
        } else {
            assert_eq!(
                current_ui_provider_rules_match(&fixture.core.metadata, other)?,
                fixture.core.metadata.source_rules.get(&other.path).cloned() == other.rules
            );
        }
        let mut metadata = fixture.core.metadata.clone();
        let mut unreviewed = current[0].clone();
        unreviewed.input = other.path.clone();
        metadata
            .source_rules
            .insert(other.path.clone(), vec![unreviewed]);
        assert!(!current_ui_provider_rules_match(&metadata, other)?);
        unrelated += 1;
    }
    assert_eq!(unrelated, 18);
    assert_eq!(refined_helpers, 2);
    let context_source = fixture.core.read_source(CONTEXT)?;
    assert_eq!(context_source.len(), 2894);
    assert_eq!(
        sha256(&context_source),
        "sha256:2713d367601d6441a697e2c25650a7e187a4157f7785e0c35e407a0650e97a68"
    );
    Ok(())
}

fn expected_requirements(index: usize) -> Vec<&'static str> {
    match index {
        0 | 1 => vec![
            "client_actions",
            "client_program_consent",
            "confirmation_review_callbacks",
            "workspace_panel_lookup",
            "workspace_panels",
            "workspace_widget_hosts",
        ],
        2 => vec!["client_actions", "client_program_consent"],
        3 | 4 | 5 => vec!["workspace_panels", "workspace_widget_hosts"],
        6 => vec!["client_actions", "workspace_panels"],
        _ => panic!("unknown consent UI row"),
    }
}
fn verify_raw(bytes: &[u8], length: usize, digest: &str, lf: usize) -> Result<()> {
    ensure!(
        bytes.len() == length
            && sha256(bytes) == digest
            && bytes.iter().filter(|byte| **byte == b'\n').count() == lf
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[239, 187, 191])
            && bytes.last() == Some(&b'\n'),
        "raw consent witness must not normalize"
    );
    Ok(())
}
fn before(source: &str, first: &str, second: &str) {
    let left = source
        .find(first)
        .unwrap_or_else(|| panic!("missing anchor {first}"));
    let right = source
        .find(second)
        .unwrap_or_else(|| panic!("missing anchor {second}"));
    assert!(left < right, "{first} must precede {second}");
}

#[test]
fn all_one_hundred_forty_git_cells_reconstruct_exact_raw_membership() -> Result<()> {
    let f = Fixture::load()?;
    let (mut present, mut absent) = (0, 0);
    for (key, commit) in CONTEXTS {
        let mut command = frozen_git_command(&f.core.repository);
        command.args(["ls-tree", commit, "--"]);
        for (path, _, _) in SOURCES {
            command.arg(format!("platform/minecraft/{path}"));
        }
        let output = command.output()?;
        ensure!(
            output.status.success()
                && output.stdout.len() <= 64 * 1024
                && output.stderr.len() <= 64 * 1024,
            "bounded offline UI tree query failed"
        );
        let mut tree = BTreeMap::new();
        for row in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = row
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("UI tree framing"))?;
            let mut fields = header.split_whitespace();
            ensure!(
                fields.next() == Some("100644") && fields.next() == Some("blob"),
                "UI tree type/mode"
            );
            let oid = fields.next().ok_or_else(|| eyre::eyre!("UI OID absent"))?;
            ensure!(
                fields.next().is_none() && tree.insert(path.to_owned(), oid.to_owned()).is_none(),
                "UI tree duplicate/extended row"
            );
        }
        let (environment, target) = key
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("UI context framing"))?;
        let context = if environment == "dev" && D2.contains(&target) {
            f.full_context(target)?
        } else {
            f.core.context(target, &[])?
        };
        for (index, (path, _, _)) in SOURCES.into_iter().enumerate() {
            let expected = f.ledger.files[index]
                .witnesses
                .get(key)
                .ok_or_else(|| eyre::eyre!("immutable UI tree cell absent"))?;
            assert_eq!(
                tree.get(&format!("platform/minecraft/{path}")),
                expected.as_ref()
            );
            let body = f.render(path, &context)?;
            if let Some(oid) = expected {
                assert_eq!(
                    body.ok_or_else(|| eyre::eyre!("historical UI omitted"))?
                        .as_bytes(),
                    f.raw[oid]
                );
                present += 1;
            } else {
                assert!(body.is_none());
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (14, 126));
    Ok(())
}

#[test]
fn twelve_independent_signing_programmatic_and_palette_masks_preserve_genuine_off_paths()
-> Result<()> {
    let f = Fixture::load()?;
    let mut cells = 0;
    for profile in &f.ledger.counterfactuals {
        let flags: Vec<_> = profile
            .enabled_features
            .iter()
            .map(String::as_str)
            .collect();
        let mut context = f.core.context(&profile.target, &flags)?;
        context.environment = "unrelated-ui-source-proof".to_owned();
        context.preset = "unrelated-preset".to_owned();
        context.projection_key = "unrelated/key".to_owned();
        for (path, _, _) in SOURCES {
            let body = f
                .render(path, &context)?
                .ok_or_else(|| eyre::eyre!("legal UI profile omitted"))?;
            let pin = &profile.paths[path];
            assert_eq!(body.len(), pin.bytes);
            assert_eq!(sha256(body.as_bytes()), pin.sha256);
            assert!(!body.contains("{%"));
            cells += 1;
        }
        let panel = f
            .render(SOURCES[0].0, &context)?
            .ok_or_else(|| eyre::eyre!("panel omitted"))?;
        let signing = context.features["client_program_signing"];
        assert_eq!(panel.contains("case SIGN_REVIEW ->"), signing);
        assert_eq!(panel.contains("ClientProgramSignerTrustService"), signing);
        assert_eq!(panel.contains("ClientProgramSigningRuntime.open("), signing);
        if !signing {
            assert!(panel.contains("public ClientProgramConsentsPanel() { this(ClientProgramConsentRuntime.service()); }"));
            assert!(!panel.contains("selectedSigner("));
            assert!(!panel.contains("trustSigner("));
            assert!(!panel.contains("SIGN_REVIEW"));
        }
        assert!(
            panel.contains("case DENY ->")
                && panel.contains("case REVOKE ->")
                && panel.contains("service.stopAll()")
                && panel.contains("private void review(")
        );
        let registrar = f
            .render(SOURCES[2].0, &context)?
            .ok_or_else(|| eyre::eyre!("UI registrar omitted"))?;
        assert_eq!(
            registrar.contains("SFMClientProgramConsentAction(false)"),
            context.features["client_program_actions"]
        );
        assert_eq!(
            registrar.contains("SFMClientProgramConsentAction(true)"),
            context.features["client_program_actions"]
        );
        assert!(registrar.contains("SFMClientProgramConsentControlAction::new"));
        let execution = f
            .render(SOURCES[6].0, &context)?
            .ok_or_else(|| eyre::eyre!("real executor helper omitted"))?;
        assert!(execution.contains("public static boolean execute("));
        assert_eq!(
            execution.contains("SFMActionChoice"),
            context.features["command_palette"]
        );
        assert_eq!(
            execution.contains("public static boolean executeAction("),
            context.features["command_palette"]
        );
        assert!(
            !context.features["multiplayer_packets"]
                && !context.features["client_properties"]
                && !context.features["image_resources"]
                && !context.features["terminal_remote"]
        );
    }
    assert_eq!(cells, 84);
    Ok(())
}

#[test]
fn ui_local_selection_and_control_registration_refuse_each_missing_actual_api_without_global_prerequisites()
-> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        for removed in UI.into_iter().filter(|name| *name != "sfml_execution_side") {
            let flags: Vec<_> = UI.into_iter().filter(|name| *name != removed).collect();
            let context = f.core.context(target, &flags)?;
            assert!(f.render(SOURCES[0].0, &context)?.is_none());
            assert!(f.render(SOURCES[1].0, &context)?.is_none());
            if let Some(registrar) = f.render(SOURCES[2].0, &context)? {
                assert!(!registrar.contains("SFMClientProgramConsentControlAction::new"));
            }
        }
        let consent_only = f
            .core
            .context(target, &["client_program_consent", "sfml_execution_side"])?;
        for (path, _, _) in SOURCES {
            assert!(f.render(path, &consent_only)?.is_none());
        }
        let widget_only = f
            .core
            .context(target, &["workspace_panels", "workspace_widget_hosts"])?;
        assert!(f.render(SOURCES[3].0, &widget_only)?.is_some());
        assert!(f.render(SOURCES[4].0, &widget_only)?.is_some());
        assert!(f.render(SOURCES[5].0, &widget_only)?.is_some());
        assert!(f.render(SOURCES[6].0, &widget_only)?.is_none());
        let action_only = f
            .core
            .context(target, &["workspace_panels", "client_actions"])?;
        assert!(f.render(SOURCES[6].0, &action_only)?.is_some());
        assert!(f.render(SOURCES[0].0, &action_only)?.is_none());
    }
    Ok(())
}

#[test]
fn authority_confirmation_focus_and_target_capture_remain_original_without_effect_execution()
-> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        let context = f.full_context(target)?;
        let panel = f
            .render(SOURCES[0].0, &context)?
            .ok_or_else(|| eyre::eyre!("panel absent"))?;
        for anchor in [
            "discovery and program requests never open this panel",
            "40));",
            "Review cancelled; no approval granted.",
            "service.revoke(identity, capability)",
            "service.stopAll();",
            "sameScope(grant, identity)",
            "signers.trustCurrent(identity, fingerprint, future(duration))",
            "Existing exact denials, revocations and policy blocks still apply.",
            "No previous approvals will be restored.",
            "future(lifetime), null",
            "ClientProgramConsentReview.previous(service.store(), record.orElseThrow())",
        ] {
            assert!(
                panel.contains(anchor),
                "original consent/trust authority anchor {anchor}"
            );
        }
        let control = f
            .render(SOURCES[1].0, &context)?
            .ok_or_else(|| eyre::eyre!("control absent"))?;
        before(
            &control,
            "!origin.originatingHostIsCurrent().getAsBoolean()",
            "workspace.panel(",
        );
        before(
            &control,
            "origin.originatingPanelId() == null",
            "workspace.panel(",
        );
        before(
            &control,
            "panel.isEmpty()",
            "panel.orElseThrow().activate(control)",
        );
        assert!(control.contains("Human-only contextual panel actions"));
        assert!(
            !control.contains("programmaticDescriptor(")
                && !control.contains("programmaticHandler(")
        );
        let execution = f
            .render(SOURCES[6].0, &context)?
            .ok_or_else(|| eyre::eyre!("executor absent"))?;
        before(
            &execution,
            "SFMClientActionContext actionContext",
            "SFMClientActionExecutor.execute(command, actionContext, feedback)",
        );
        assert!(execution.contains("minecraft.screen == context.host()"));
        assert!(execution.contains("context.panelId()"));
        let host = f
            .render(SOURCES[4].0, &context)?
            .ok_or_else(|| eyre::eyre!("widget host absent"))?;
        for anchor in [
            "Duplicate panel element id ",
            "A visible top child owns its rectangle",
            "return true;",
            "private void reconcileFocus()",
            "Math.floor((workspaceX - workspaceContentBounds.x()) / scale)",
            "if (focused != null && focused.keyPressed(",
        ] {
            assert!(host.contains(anchor));
        }
        let interface = render_java_source(
            std::str::from_utf8(&f.core.read_source(
                "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenPanel.java",
            )?)?,
            &context,
        )?;
        assert!(interface.contains("default Optional<SFMPanelWidgetHost> widgetHost()"));
        let multiplexer = render_java_source(
            std::str::from_utf8(&f.core.read_source(
                "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenMultiplexer.java",
            )?)?,
            &context,
        )?;
        assert!(
            multiplexer
                .contains("public Optional<SFMScreenPanel> panel(SFMWorkspacePanelId panelId)")
        );
        let confirmation = render_java_source(
            std::str::from_utf8(&f.core.read_source(
                "src/main/java/ca/teamdman/sfm/client/screen/SFMConfirmationScreen.java",
            )?)?,
            &context,
        )?;
        assert!(confirmation.contains("Runnable callback, Runnable cancelled"));
        assert!(confirmation.contains("public void onClose() { choice.accept(false); }"));
    }
    Ok(())
}

#[test]
fn all_off_inputs_are_omitted_before_invalid_utf8_or_unknown_directive_read() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    for (path, _, _) in SOURCES {
        let file = root.join(path);
        fs::create_dir_all(
            file.parent()
                .ok_or_else(|| eyre::eyre!("poison parent absent"))?,
        )?;
        fs::write(file, b"{% if features.unapproved_consent_ui %}\n\xff")?;
    }
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    let metadata = f.bounded_metadata();
    for target in TEN {
        let context = f.core.context(target, &[])?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (path, _, _) in SOURCES {
            assert!(selected.omitted_paths.contains(path));
            assert!(!artifacts.contains_key(path));
        }
    }
    Ok(())
}

#[test]
fn automatic_java_template_false_collector_preserves_all_twenty_historical_origins() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut metadata = f.bounded_metadata();
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let inventory = discover_core_source_files(&root)?;
    let mut outputs = 0;
    for (key, _) in CONTEXTS {
        let (environment, target) = key
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("historical context framing"))?;
        let context = if environment == "dev" && D2.contains(&target) {
            f.full_context(target)?
        } else {
            f.core.context(target, &[])?
        };
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (index, (path, _, _)) in SOURCES.into_iter().enumerate() {
            if let Some(oid) = &f.ledger.files[index].witnesses[key] {
                let artifact = artifacts
                    .get(path)
                    .ok_or_else(|| eyre::eyre!("selected UI artifact absent"))?;
                let text = std::str::from_utf8(&artifact.output_bytes)?;
                let (_, body) = text
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("generated banner absent"))?;
                assert_eq!(body.as_bytes(), f.raw[oid]);
                assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{path}"));
                assert!(artifact.overlay.is_none());
                assert!(!body.contains("{%"));
                outputs += 1;
            } else {
                assert!(!artifacts.contains_key(path));
            }
        }
    }
    assert_eq!(outputs, 14);
    Ok(())
}

#[test]
fn isolated_common_edits_reach_two_real_widget_api_targets_without_mutating_authored_core()
-> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut edits = BTreeMap::new();
    for evidence in &f.ledger.files {
        let source = std::str::from_utf8(&f.sources[&evidence.path])?;
        let edited = source.replacen(
            &evidence.common_edit_anchor,
            &format!(
                "// Isolated genuine consent UI common edit.\n{}",
                evidence.common_edit_anchor
            ),
            1,
        );
        fs::write(root.join(&evidence.path), &edited)?;
        edits.insert(evidence.path.clone(), edited);
    }
    let inventory = discover_core_source_files(&root)?;
    let metadata = f.bounded_metadata();
    let mut outputs = 0;
    for target in D2 {
        let context = f.full_context(target)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (path, _, _) in SOURCES {
            let artifact = &artifacts[path];
            let text = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = text
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("generated banner absent"))?;
            assert_eq!(body, render_java_source(&edits[path], &context)?);
            assert!(body.contains("// Isolated genuine consent UI common edit."));
            outputs += 1;
        }
    }
    assert_eq!(outputs, 14);
    for (path, original) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *original);
    }
    Ok(())
}

#[test]
fn raw_mutations_unknown_guards_and_unsupported_owners_refuse_without_normalization() -> Result<()>
{
    let f = Fixture::load()?;
    for (oid, length, digest, lf) in RAW {
        let original = &f.raw[oid];
        let mut appended = original.clone();
        appended.push(b'\n');
        let mut trimmed = original.clone();
        trimmed.pop();
        let mut space = original.clone();
        space.insert(0, b' ');
        let mut bom = vec![239, 187, 191];
        bom.extend(original);
        let crlf = std::str::from_utf8(original)?
            .replace('\n', "\r\n")
            .into_bytes();
        for mutant in [appended, trimmed, space, bom, crlf] {
            assert!(verify_raw(&mutant, length, digest, lf).is_err());
        }
    }
    let context = f.full_context("1.19.2")?;
    let source = std::str::from_utf8(&f.sources[SOURCES[0].0])?;
    let bad = source.replacen(
        "{% if features.client_program_signing %}",
        "{% if features.unapproved_consent_signing %}",
        1,
    );
    assert!(render_java_source(&bad, &context).is_err());
    let unclosed = format!("{source}{{% if features.client_program_signing %}}\n");
    assert!(render_java_source(&unclosed, &context).is_err());
    let mut metadata = f.bounded_metadata();
    metadata
        .source_rules
        .get_mut(SOURCES[0].0)
        .ok_or_else(|| eyre::eyre!("UI rule absent"))?[0]
        .when
        .all_features
        .push("unapproved_consent_ui".to_owned());
    assert!(select_core_inputs(&metadata, &context, &f.inventory()).is_err());
    assert!(
        f.core
            .context("1.19.2", &["client_program_consent"])
            .is_err()
    );
    for target in TEN.into_iter().filter(|target| !D2.contains(target)) {
        assert!(f.core.context(target, &UI).is_err());
        assert!(f.core.context(target, &["workspace_widget_hosts"]).is_err());
        assert!(f.core.context(target, &["workspace_panel_lookup"]).is_err());
    }
    Ok(())
}
