//! Frozen consent/choice providers via real core selection, rendering and collection.
//! Promotion-first evidence only: no Java, screen, key, store, packet or query executes.
//! Git blobs are offline test goldens, never production source lookup. Frame-off
//! human refusal is a reviewed counterfactual, not a historical result-schema change.
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

const LEDGER: &str = "docs/tasks/sfm-core-consent-action-choice-providers-slice.json";
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const TEN: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const CHOICE: &str = "src/main/java/ca/teamdman/sfm/client/screen/SFMActionChoice.java";
const CONSENT: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientProgramConsentAction.java";
const FRAME: &str = "src/main/java/ca/teamdman/sfm/client/program/ClientManagerFrameRuntime.java";
const SOURCE: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionSource.java";
const REGISTRAR: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientProgramConsentActions.java";
const EXECUTOR: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPanelActionExecution.java";
const FULL: &[&str] = &[
    "client_actions",
    "client_frame_language",
    "client_manager",
    "client_program_actions",
    "client_program_consent",
    "client_theme",
    "command_palette",
    "disk_readonly_access",
    "keyboard_profiles",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
    "sfml_execution_side",
    "structured_action_results",
    "workspace_panel_actions",
    "workspace_panels",
    "workspace_toast_actions",
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
const SOURCES: [(&str, usize, &str); 2] = [
    (
        "src/main/java/ca/teamdman/sfm/client/screen/SFMActionChoice.java",
        2666,
        "sha256:01f71c772489180f86b7f60e78e7f5c8d0b0a428fb98a8077cf62b0799b53217",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/action/SFMClientProgramConsentAction.java",
        9109,
        "sha256:cbeed655fec64e5a557d3ee62a3154c4eb440664ac6a37d3efd206059f770743",
    ),
];
const RAW: [(&str, usize, &str, usize); 2] = [
    (
        "9ccb7a1a1f7b205bb1eae2d44b9c7624ffefbc35",
        8704,
        "sha256:8a3371ed58ce3ec00c9aa8574e6b7cfa72318044bac4077fb88be1476556d144",
        122,
    ),
    (
        "e89c6aad185781023e075b76083a4021a848c2c7",
        2666,
        "sha256:01f71c772489180f86b7f60e78e7f5c8d0b0a428fb98a8077cf62b0799b53217",
        63,
    ),
];
const DEFINITIONS: [(&str, &[&str], &[&str]); 17] = [
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
    (
        "structured_action_results",
        &["1.19.2", "1.19.4"],
        &["client_actions"],
    ),
    (
        "workspace_panel_actions",
        &["1.19.2", "1.19.4"],
        &["client_actions", "workspace_panels"],
    ),
    (
        "workspace_panels",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "workspace_toast_actions",
        &["1.19.2", "1.19.4"],
        &["client_actions", "workspace_panels"],
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    contract_changes: bool,
    scope: Scope,
    definitions: BTreeMap<String, Definition>,
    context_commits: BTreeMap<String, String>,
    files: Vec<FileEvidence>,
    raw_objects: Vec<RawEvidence>,
    full_features: Vec<String>,
    counterfactuals: Vec<Profile>,
    current_provider_source_receipts: Vec<Provider>,
    required_peer_promotions: Vec<Provider>,
    regions: Vec<Region>,
}
#[derive(Facet)]
struct Scope {
    production_inputs: usize,
    historical_cells: usize,
    present_cells: usize,
    absent_cells: usize,
    raw_objects: usize,
    raw_bytes: usize,
    authored_bytes: usize,
    independent_profiles: usize,
    independent_cells: usize,
    selected_profile_cells: usize,
    omitted_profile_cells: usize,
    prepared_tests: usize,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct FileEvidence {
    path: String,
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
struct Profile {
    key: String,
    target: String,
    enabled_features: Vec<String>,
    paths: BTreeMap<String, Option<Pin>>,
}
#[derive(Facet)]
struct Pin {
    bytes: usize,
    sha256: String,
}
#[derive(Facet)]
struct Provider {
    path: String,
    bytes: usize,
    sha256: String,
    rules: Option<Vec<InputVariant>>,
}
#[derive(Facet)]
struct Region {
    owner: String,
    before: String,
    after: String,
    before_bytes: usize,
    before_sha256: String,
    after_bytes: usize,
    after_sha256: String,
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
        let s = &ledger.scope;
        ensure!(
            ledger.schema == "sfm:core-consent-action-choice-providers-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && s.production_inputs == 2
                && s.historical_cells == 40
                && s.present_cells == 4
                && s.absent_cells == 36
                && s.raw_objects == 2
                && s.raw_bytes == 11370
                && s.authored_bytes == 11775
                && s.independent_profiles == 24
                && s.independent_cells == 48
                && s.selected_profile_cells == 22
                && s.omitted_profile_cells == 26
                && s.prepared_tests == 8
                && ledger.files.len() == 2
                && ledger.raw_objects.len() == 2
                && ledger.definitions.len() == 17
                && ledger.counterfactuals.len() == 24
                && ledger.current_provider_source_receipts.len() == 19
                && ledger.required_peer_promotions.len() == 2
                && ledger.regions.len() == 6
                && ledger.full_features == FULL
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(key, oid)| (key.to_owned(), oid.to_owned()))
                        .collect(),
            "immutable consent/choice evidence scope changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let original = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("historical owner absent: {name}"))?;
            let current = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("current owner absent: {name}"))?;
            ensure!(
                original.supported_targets == support
                    && original.requires == requires
                    && current.supported_targets == support
                    && current.requires == requires,
                "provider preservation must not broaden/force prerequisites: {name}"
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
                    && evidence.final_lf
                    && !evidence.bom,
                "raw provider pin changed"
            );
            verify_raw(&raw[oid], length, digest, lf)?;
        }
        let mut sources = BTreeMap::new();
        for ((path, length, digest), evidence) in SOURCES.into_iter().zip(&ledger.files) {
            let source = core.read_source(path)?;
            ensure!(
                evidence.path == path
                    && evidence.bytes == length
                    && evidence.sha256 == digest
                    && evidence.cr_count == 0
                    && evidence.final_lf
                    && evidence.witnesses.len() == 20
                    && source.len() == length
                    && sha256(&source) == digest
                    && source.last() == Some(&b'\n')
                    && !source.contains(&b'\r'),
                "authored provider identity changed: {path}"
            );
            let rules = core
                .metadata
                .source_rules
                .get(path)
                .ok_or_else(|| eyre::eyre!("promote genuine provider rules first: {path}"))?;
            let mut expected_rules = vec![evidence.source_rule.clone()];
            if path == CHOICE {
                expected_rules[0]
                    .when
                    .any_features
                    .push("explorer_navigation".to_owned());
                let mut review_rule = evidence.source_rule.clone();
                review_rule.when.all_features =
                    vec!["client_actions".to_owned(), "release_review".to_owned()];
                review_rule.when.none_features = review_rule.when.any_features.clone();
                review_rule
                    .when
                    .none_features
                    .push("explorer_navigation".to_owned());
                review_rule.when.any_features.clear();
                let mut action_rule = review_rule.clone();
                action_rule.when.all_features = vec!["client_actions".to_owned()];
                action_rule
                    .when
                    .none_features
                    .push("release_review".to_owned());
                expected_rules.push(review_rule);
                expected_rules.push(action_rule);
            }
            ensure!(
                *rules == expected_rules
                    && rules[0].input == path
                    && rules[0].template
                    && rules[0].when.targets == D2
                    && rules[0].when.none_features.is_empty(),
                "provider source/target contract changed"
            );
            if path == CHOICE {
                ensure!(
                    rules[0].when.all_features.is_empty()
                        && rules[0].when.any_features
                            == [
                                "command_palette",
                                "workspace_panel_actions",
                                "workspace_toast_actions",
                                "explorer_navigation"
                            ],
                    "pure choice must use actual consumer OR, not mandatory palette"
                );
            } else {
                ensure!(
                    rules[0].when.all_features == ["client_program_actions"]
                        && rules[0].when.any_features.is_empty(),
                    "consent action must remain independently programmatic"
                );
            }
            ensure!(
                !evidence.common_edit_anchor.is_empty()
                    && std::str::from_utf8(&source)?
                        .matches(&evidence.common_edit_anchor)
                        .count()
                        == 1,
                "nonunique common source edit anchor"
            );
            sources.insert(path.to_owned(), source);
        }
        for provider in ledger
            .current_provider_source_receipts
            .iter()
            .chain(&ledger.required_peer_promotions)
        {
            let source = core.read_source(&provider.path)?;
            let pinned = if provider.path.ends_with("/SFMScreenMultiplexer.java") {
                reviewed_pre_typed_palette_host(std::str::from_utf8(&source)?)?.into_bytes()
            } else {
                source.clone()
            };
            ensure!(
                pinned.len() == provider.bytes
                    && sha256(&pinned) == provider.sha256
                    && current_provider_rules_match(&core, provider)?,
                "real provider or promotion-first peer changed: {}",
                provider.path
            );
        }
        let mut keys = BTreeSet::new();
        for profile in &ledger.counterfactuals {
            ensure!(
                D2.contains(&profile.target.as_str())
                    && keys.insert(profile.key.clone())
                    && profile.paths.len() == 2,
                "legal profile scope changed"
            );
            core.context(
                &profile.target,
                &profile
                    .enabled_features
                    .iter()
                    .map(String::as_str)
                    .collect::<Vec<_>>(),
            )?;
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
    fn context(&self, profile: &Profile) -> Result<ProjectionContext> {
        self.core.context(
            &profile.target,
            &profile
                .enabled_features
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>(),
        )
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if !selected.inputs.contains_key(path) {
            ensure!(
                selected.omitted_paths.contains(path),
                "unaccounted provider omission"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.sources[path])?,
            context,
        )?))
    }
    fn peer(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        render_java_source(std::str::from_utf8(&self.core.read_source(path)?)?, context)
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
                    .ok_or_else(|| eyre::eyre!("isolated provider parent absent"))?,
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
                    "isolated project input changed between contexts"
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
fn current_provider_rules_match(core: &CoreTestFixture, provider: &Provider) -> Result<bool> {
    let current = core.metadata.source_rules.get(&provider.path).cloned();
    if provider.path.ends_with("/SFMClientActionDescriptor.java") {
        let old = provider
            .rules
            .as_ref()
            .ok_or_else(|| eyre::eyre!("historical descriptor rules absent"))?;
        ensure!(
            old.len() == 1
                && old[0].input == provider.path
                && old[0].template
                && old[0].when.targets == D2
                && old[0].when.all_features == ["client_program_actions"]
                && old[0].when.any_features.is_empty()
                && old[0].when.none_features.is_empty(),
            "immutable action descriptor selector changed"
        );
        let mut expected = old.clone();
        let mut human = old[0].clone();
        human.when.all_features = [
            "client_actions",
            "packet_actions",
            "packet_transport_private",
        ]
        .map(str::to_owned)
        .to_vec();
        human.when.none_features = vec!["client_program_actions".to_owned()];
        expected.push(human);
        return Ok(current == Some(expected));
    }
    if provider.path.ends_with("/SFMCanonicalTokenArgument.java") {
        let old = provider
            .rules
            .as_ref()
            .ok_or_else(|| eyre::eyre!("historical token rules absent"))?;
        ensure!(
            old.len() == 1
                && old[0].input == provider.path
                && old[0].when.targets == D2
                && old[0].when.all_features == ["client_actions"]
                && old[0].when.any_features == ["client_program_consent", "keyboard_profiles"]
                && old[0].when.none_features.is_empty()
                && !old[0].template,
            "immutable canonical token selector changed"
        );
        let mut expected = old.clone();
        expected[0].template = true;
        expected[0].when.any_features = [
            "client_program_consent",
            "spatial_coverage",
            "keyboard_profiles",
            "trajectory_panels",
            "workspace_counterfactuals",
            "review_sessions",
            "route_comparison",
            "file_explorer",
            "registry_explorer",
            "explorer_search",
            "explorer_compaction",
            "explorer_navigation",
            "theme_preview_rules",
        ]
        .map(str::to_owned)
        .to_vec();
        return Ok(current == Some(expected));
    }
    if ![
        "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionSource.java",
        "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionContext.java",
    ]
    .contains(&provider.path.as_str())
    {
        return Ok(current == provider.rules);
    }
    let old = provider
        .rules
        .as_ref()
        .ok_or_else(|| eyre::eyre!("immutable historical carrier rules absent"))?;
    ensure!(
        old.len() == 1
            && old[0].input == provider.path
            && old[0].template
            && old[0].when.all_features == ["client_actions"]
            && old[0].when.any_features.is_empty()
            && old[0].when.targets.is_empty()
            && old[0].when.none_features.is_empty(),
        "immutable Choice2 historical carrier contract changed"
    );
    let mut exact_current = old.clone();
    exact_current[0].when.all_features.clear();
    exact_current[0].when.any_features =
        vec!["client_actions".to_owned(), "workspace_panels".to_owned()];
    Ok(current == Some(exact_current))
}

fn verify_raw(bytes: &[u8], length: usize, digest: &str, lf: usize) -> Result<()> {
    ensure!(
        bytes.len() == length
            && sha256(bytes) == digest
            && bytes.iter().filter(|byte| **byte == b'\n').count() == lf
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[239, 187, 191])
            && bytes.last() == Some(&b'\n'),
        "no raw witness normalization permitted"
    );
    Ok(())
}
fn before(source: &str, left: &str, right: &str) {
    assert!(
        source
            .find(left)
            .unwrap_or_else(|| panic!("missing anchor {left}"))
            < source
                .find(right)
                .unwrap_or_else(|| panic!("missing anchor {right}")),
        "{left} must precede {right}"
    );
}
fn section<'a>(source: &'a str, start: &str, end: &str) -> &'a str {
    let start = source
        .find(start)
        .unwrap_or_else(|| panic!("missing section {start}"));
    let end = source[start..]
        .find(end)
        .unwrap_or_else(|| panic!("missing section {end}"))
        + start;
    &source[start..end]
}

#[test]
fn all_forty_historical_cells_reconstruct_exact_raw_sources_and_absence() -> Result<()> {
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
            "bounded offline provider tree query failed"
        );
        let mut tree = BTreeMap::new();
        for row in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = row
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("provider tree framing"))?;
            let mut fields = header.split_whitespace();
            ensure!(
                fields.next() == Some("100644") && fields.next() == Some("blob"),
                "provider tree type/mode changed"
            );
            let oid = fields
                .next()
                .ok_or_else(|| eyre::eyre!("provider tree OID absent"))?;
            ensure!(
                fields.next().is_none() && tree.insert(path.to_owned(), oid.to_owned()).is_none(),
                "duplicate/extended provider tree"
            );
        }
        let (environment, target) = key
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("historical context framing"))?;
        let context = f.core.context(
            target,
            if environment == "dev" && D2.contains(&target) {
                FULL
            } else {
                &[]
            },
        )?;
        for (index, (path, _, _)) in SOURCES.into_iter().enumerate() {
            let expected = &f.ledger.files[index].witnesses[key];
            assert_eq!(
                tree.get(&format!("platform/minecraft/{path}")),
                expected.as_ref()
            );
            let body = f.render(path, &context)?;
            if let Some(oid) = expected {
                assert_eq!(
                    body.ok_or_else(|| eyre::eyre!("historical provider omitted"))?
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
    assert_eq!((present, absent), (4, 36));
    Ok(())
}

#[test]
fn all_twenty_four_profiles_preserve_actual_consumer_union_and_independent_member_owners()
-> Result<()> {
    let f = Fixture::load()?;
    let (mut selected, mut omitted) = (0, 0);
    let mut current_action_additions = 0;
    for profile in &f.ledger.counterfactuals {
        let mut context = f.context(profile)?;
        context.environment = "unrelated-provider-proof".to_owned();
        context.preset = "unrelated-preset".to_owned();
        context.projection_key = "unrelated/key".to_owned();
        for (path, _, _) in SOURCES {
            let body = f.render(path, &context)?;
            match (&profile.paths[path], body) {
                (Some(pin), Some(body)) => {
                    assert_eq!(body.len(), pin.bytes);
                    assert_eq!(sha256(body.as_bytes()), pin.sha256);
                    assert!(!body.contains("{%"));
                    selected += 1;
                }
                (None, None) => omitted += 1,
                (None, Some(body)) if path == CHOICE && context.features["client_actions"] => {
                    // Preserve the frozen historical absence while proving the
                    // current action-only consumer receives the unchanged carrier.
                    assert_eq!(body.as_bytes(), f.sources[CHOICE]);
                    omitted += 1;
                    current_action_additions += 1;
                }
                _ => panic!(
                    "profile/source membership disagrees: {} {path}",
                    profile.key
                ),
            }
        }
        assert!(
            !context.features["client_program_signing"]
                && !context.features["image_resources"]
                && !context.features["touch_display"]
                && !context.features["multiplayer_packets"]
                && !context.features["terminal_remote"]
                && !context.features["workspace_widget_hosts"]
        );
    }
    assert_eq!((selected, omitted), (22, 26));
    let expected_additions = f
        .ledger
        .counterfactuals
        .iter()
        .filter(|profile| {
            profile.paths[CHOICE].is_none()
                && profile
                    .enabled_features
                    .iter()
                    .any(|name| name == "client_actions")
        })
        .count();
    assert!(expected_additions > 0);
    assert_eq!(current_action_additions, expected_additions);
    Ok(())
}

#[test]
fn programmatic_query_admission_request_policy_and_descriptors_stay_byte_exact() -> Result<()> {
    let f = Fixture::load()?;
    let raw = std::str::from_utf8(&f.raw["9ccb7a1a1f7b205bb1eae2d44b9c7624ffefbc35"])?;
    for profile in f
        .ledger
        .counterfactuals
        .iter()
        .filter(|p| p.key.contains("/consent-"))
    {
        let context = f.context(profile)?;
        let body = f
            .render(CONSENT, &context)?
            .ok_or_else(|| eyre::eyre!("programmatic action omitted"))?;
        assert_eq!(
            section(
                &body,
                "    public SFMValue query(",
                "    @Override public void configureCommandNode("
            ),
            section(
                raw,
                "    public SFMValue query(",
                "    @Override public void configureCommandNode("
            )
        );
        assert_eq!(
            section(
                &body,
                "    public SFMClientProgramConsentAction(boolean request)",
                "    public SFMValue query("
            ),
            section(
                raw,
                "    public SFMClientProgramConsentAction(boolean request)",
                "    public SFMValue query("
            )
        );
        before(
            &body,
            "if (INPUT.validate(input).isPresent())",
            "ResourceLocation capability =",
        );
        before(
            &body,
            "!identity.requestedCapabilities().contains(capability)",
            "var state = service.get();",
        );
        before(
            &body,
            "var state = service.get();",
            "request ? state.request(identity, capability)",
        );
        assert!(
            body.contains("context.caller().isPresent()")
                && body.contains("result(\"no_program_caller\", null, false, false)")
                && body.contains("CostClass.CLIENT_EFFECT")
                && body.contains("CostClass.LOCAL_READ")
                && body.contains("Acknowledgement.LOCAL_RESULT_ONLY")
                && body.contains("ClientProgramConsentGate.EXECUTE")
        );
        assert!(
            !body.contains(".approve(")
                && !body.contains(".decide(")
                && !body.contains("setOrPushScreen")
        );
        assert!(
            body.contains("private_integrated_world_required")
                && body.contains("server.isPublished()")
        );
        let execute = section(
            &body,
            "    @Override public int execute(",
            "    public static List<String> policyBlockers(",
        );
        if context.features["client_frame_language"] {
            assert!(
                execute.contains("ClientManagerFrameRuntime.identityFor(manager)")
                    && execute.contains("result(\"missing_manager\"")
                    && execute.contains("query(identity.orElseThrow()")
                    && execute.contains("return 1;")
            );
            assert!(!execute.contains("frame runtime is unavailable"));
            assert_eq!(
                execute.contains("publishStructuredResult("),
                context.features["structured_action_results"]
            );
            assert!(execute.contains(
                "sendFeedback(Component.literal(SFMValueSchema.canonicalActionJson(output)))"
            ));
        } else {
            assert!(execute.contains("sendFeedback(Component.literal(\"Client Manager frame runtime is unavailable\"))")
                && execute.contains("return 0;"));
            for absent in [
                "BlockPos",
                "Minecraft.getInstance()",
                "identityFor(",
                "query(",
                "missing_manager",
                "publishStructuredResult(",
                "return 1;",
            ] {
                assert!(!execute.contains(absent));
            }
        }
        if !context.features["structured_action_results"] {
            assert!(
                !body.contains("JsonParser") && !body.contains("SFMClientActionStructuredResult")
            );
        }
        if !context.features["client_frame_language"] {
            assert!(
                !body.contains("ClientManagerBlockEntity")
                    && !body.contains("net.minecraft.core.BlockPos")
            );
        }
        let source = f.peer(SOURCE, &context)?;
        assert_eq!(
            source.contains("public void publishStructuredResult("),
            context.features["structured_action_results"]
        );
        let registrar = f.peer(REGISTRAR, &context)?;
        assert!(
            registrar.contains("new SFMClientProgramConsentAction(false)")
                && registrar.contains("new SFMClientProgramConsentAction(true)")
        );
        assert!(!registrar.contains("SFMClientProgramConsentControlAction::new"));
    }
    Ok(())
}

#[test]
fn pure_choice_validation_and_string_executor_do_not_force_palette_or_ui() -> Result<()> {
    let f = Fixture::load()?;
    let descriptor = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionDescriptor.java";
    let descriptor_source = f.core.read_source(descriptor)?;
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
            let selected = select_core_inputs(
                &f.core.metadata,
                &context,
                &BTreeSet::from([descriptor.to_owned()]),
            )?;
            assert_eq!(selected.inputs.contains_key(descriptor), mask == 7);
            if mask == 7 {
                assert_eq!(selected.inputs[descriptor].input, descriptor);
                assert!(selected.inputs[descriptor].template);
                assert_eq!(
                    render_java_source(std::str::from_utf8(&descriptor_source)?, &context)?
                        .as_bytes(),
                    descriptor_source
                );
            }
            assert!(!context.features["client_program_actions"]);
        }
    }
    let path = "src/main/java/ca/teamdman/sfm/client/action/SFMCanonicalTokenArgument.java";
    let original = f.core.read_source(path)?;
    for target in D2 {
        for owner in [
            "client_program_consent",
            "spatial_coverage",
            "keyboard_profiles",
            "trajectory_panels",
            "workspace_counterfactuals",
            "review_sessions",
            "route_comparison",
            "file_explorer",
            "registry_explorer",
            "explorer_search",
            "explorer_compaction",
            "explorer_navigation",
            "theme_preview_rules",
        ] {
            let mut requested = BTreeSet::from(["client_actions".to_owned(), owner.to_owned()]);
            loop {
                let before = requested.len();
                for name in requested.clone() {
                    requested.extend(f.core.features.0[&name].requires.iter().cloned());
                }
                if requested.len() == before {
                    break;
                }
            }
            let flags = requested.iter().map(String::as_str).collect::<Vec<_>>();
            let context = f.core.context(target, &flags)?;
            let selection = select_core_inputs(
                &f.core.metadata,
                &context,
                &BTreeSet::from([path.to_owned()]),
            )?;
            let input = &selection.inputs[path];
            assert_eq!(input.input, path);
            assert!(input.template);
            assert_eq!(
                render_java_source(std::str::from_utf8(&original)?, &context)?.as_bytes(),
                original
            );
        }
    }
    for target in D2 {
        for mask in 0_u8..8 {
            let mut requested = Vec::new();
            if mask & 1 != 0 {
                requested.push("client_actions");
            }
            if mask & 2 != 0 {
                requested.push("release_review");
            }
            if mask & 4 != 0 {
                requested.push("explorer_navigation");
            }
            let context = f.core.context(target, &requested)?;
            assert!(!context.features["command_palette"]);
            assert!(!context.features["workspace_panel_actions"]);
            assert!(!context.features["workspace_toast_actions"]);
            assert!(!context.features["workspace_panels"]);
            let choice = f.render(CHOICE, &context)?;
            // Generic explorer action providers are independently selected by
            // client_actions. They need the same pure choice carrier without
            // enabling the palette, review UI or workspace.
            assert_eq!(choice.is_some(), mask & 4 != 0 || mask & 1 != 0);
            let registry = "src/main/java/ca/teamdman/sfm/client/screen/explorer/SFMExplorerContextActionRegistry.java";
            let selected = select_core_inputs(
                &f.core.metadata,
                &context,
                &BTreeSet::from([registry.to_owned()]),
            )?;
            assert_eq!(selected.inputs.contains_key(registry), mask & 1 != 0);
            if selected.inputs.contains_key(registry) {
                let rendered_registry = f.peer(registry, &context)?;
                assert!(
                    rendered_registry
                        .contains("import ca.teamdman.sfm.client.screen.SFMActionChoice;")
                );
                assert!(
                    choice.is_some(),
                    "selected registry lost its choice provider"
                );
            }
            if let Some(choice) = choice {
                assert_eq!(
                    choice.as_bytes(),
                    f.raw["e89c6aad185781023e075b76083a4021a848c2c7"]
                );
            }
        }
        let mut overlapping = FULL.to_vec();
        overlapping.push("release_review");
        let context = f.core.context(target, &overlapping)?;
        assert!(f.render(CHOICE, &context)?.is_some());
    }
    for target in D2 {
        for owner in ["workspace_panel_actions", "workspace_toast_actions"] {
            let context = f
                .core
                .context(target, &["client_actions", "workspace_panels", owner])?;
            assert!(!context.features["command_palette"]);
            let choice = f
                .render(CHOICE, &context)?
                .ok_or_else(|| eyre::eyre!("real non-palette choice omitted"))?;
            assert_eq!(
                choice.as_bytes(),
                f.raw["e89c6aad185781023e075b76083a4021a848c2c7"]
            );
            for anchor in [
                "Choice display text must not be empty",
                "command.startsWith(expectedPrefix + \" \")",
                "Choice command must invoke its declared action",
                "continuation(ResourceLocation actionId",
                "new SFMActionChoice(actionId, exact.command(), displayText, true)",
            ] {
                assert!(
                    choice.contains(anchor),
                    "pure actual choice contract {anchor}"
                );
            }
            assert!(
                !choice.contains("Minecraft")
                    && !choice.contains("Executor")
                    && !choice.contains("execute(")
            );
            let executor = f.peer(EXECUTOR, &context)?;
            assert!(
                executor.contains("public static boolean execute(")
                    && executor.contains("minecraft.screen == context.host()")
            );
            assert!(!executor.contains("SFMActionChoice") && !executor.contains("executeAction("));
            let host = f.peer(
                "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenMultiplexer.java",
                &context,
            )?;
            assert!(host.contains("SFMActionChoice") && !host.contains("{%"));
        }
        let plain = f
            .core
            .context(target, &["client_actions", "workspace_panels"])?;
        assert_eq!(
            f.render(CHOICE, &plain)?
                .expect("action-only choice carrier")
                .as_bytes(),
            f.sources[CHOICE]
        );
        assert!(f.render(CONSENT, &plain)?.is_none());
        assert!(
            f.peer(EXECUTOR, &plain)?
                .contains("public static boolean execute(")
        );
    }
    Ok(())
}

#[test]
fn genuine_frame_identity_provider_and_authored_regions_remain_real_without_surrogates()
-> Result<()> {
    let f = Fixture::load()?;
    let mut reconstructed = std::str::from_utf8(&f.sources[CONSENT])?.to_owned();
    for region in f.ledger.regions.iter().rev() {
        assert!(
            ["client_frame_language", "structured_action_results"].contains(&region.owner.as_str())
        );
        assert_eq!(region.before.len(), region.before_bytes);
        assert_eq!(sha256(region.before.as_bytes()), region.before_sha256);
        assert_eq!(region.after.len(), region.after_bytes);
        assert_eq!(sha256(region.after.as_bytes()), region.after_sha256);
        assert_eq!(reconstructed.matches(&region.after).count(), 1);
        reconstructed = reconstructed.replacen(&region.after, &region.before, 1);
    }
    assert_eq!(
        reconstructed.as_bytes(),
        f.raw["9ccb7a1a1f7b205bb1eae2d44b9c7624ffefbc35"]
    );
    for target in D2 {
        let context = f.core.context(target, FULL)?;
        let frame = f.peer(FRAME, &context)?;
        for anchor in [
            "public static Optional<ClientProgramIdentity> identityFor(ClientManagerBlockEntity manager)",
            "if (!runtimeAvailable()) return Optional.empty();",
            "Optional.ofNullable(compiled(manager).identity())",
            "new ProgramBuilder(source).forExecutionSide(ProgramExecutionSide.CLIENT).build()",
        ] {
            assert!(frame.contains(anchor), "actual provider anchor {anchor}");
        }
        assert!(
            frame.contains("ClientProgramIdentity.fromStoredSourceAndBindings(")
                && frame
                    .contains("ClientProgramConsentRuntime.observe(identity, source, bindings)")
        );
        let selected = select_core_inputs(
            &f.core.metadata,
            &context,
            &BTreeSet::from([FRAME.to_owned()]),
        )?;
        assert!(selected.inputs.contains_key(FRAME));
        let off = f
            .ledger
            .counterfactuals
            .iter()
            .find(|p| p.key == format!("{target}/consent-0"))
            .ok_or_else(|| eyre::eyre!("frame-off explicit profile absent"))?;
        let context = f.context(off)?;
        let selected = select_core_inputs(
            &f.core.metadata,
            &context,
            &BTreeSet::from([FRAME.to_owned()]),
        )?;
        assert!(selected.omitted_paths.contains(FRAME));
    }
    Ok(())
}

#[test]
fn automatic_java_even_template_false_collector_preserves_twenty_origins() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut metadata = f.bounded_metadata();
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let inventory = discover_core_source_files(&root)?;
    let mut present = 0;
    for (key, _) in CONTEXTS {
        let (environment, target) = key
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("context framing"))?;
        let context = f.core.context(
            target,
            if environment == "dev" && D2.contains(&target) {
                FULL
            } else {
                &[]
            },
        )?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (index, (path, _, _)) in SOURCES.into_iter().enumerate() {
            if let Some(oid) = &f.ledger.files[index].witnesses[key] {
                let artifact = artifacts
                    .get(path)
                    .ok_or_else(|| eyre::eyre!("selected real provider artifact absent"))?;
                let text = std::str::from_utf8(&artifact.output_bytes)?;
                let (_, body) = text
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("generated banner absent"))?;
                assert_eq!(body.as_bytes(), f.raw[oid]);
                assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{path}"));
                assert!(artifact.overlay.is_none());
                present += 1;
            } else {
                assert!(!artifacts.contains_key(path));
            }
        }
    }
    assert_eq!(present, 4);
    Ok(())
}

#[test]
fn omitted_poison_unsupported_owners_and_unknown_guards_refuse_without_effects() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    for (path, _, _) in SOURCES {
        let file = root.join(path);
        fs::create_dir_all(
            file.parent()
                .ok_or_else(|| eyre::eyre!("poison source parent absent"))?,
        )?;
        fs::write(file, b"{% if features.unapproved_consent_choice %}\n\xff")?;
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
            assert!(selected.omitted_paths.contains(path) && !artifacts.contains_key(path));
        }
    }
    assert!(
        f.core
            .context("1.19.2", &["client_program_actions"])
            .is_err()
    );
    assert!(
        f.core
            .context("1.19.2", &["structured_action_results"])
            .is_err()
    );
    assert!(
        f.core
            .context("1.19.2", &["workspace_panel_actions"])
            .is_err()
    );
    assert!(
        f.core
            .context("1.19.2", &["workspace_toast_actions"])
            .is_err()
    );
    for target in TEN.into_iter().filter(|target| !D2.contains(target)) {
        assert!(f.core.context(target, FULL).is_err());
    }
    let context = f.core.context("1.19.2", FULL)?;
    let source = std::str::from_utf8(&f.sources[CONSENT])?;
    let bad = source.replacen(
        "{% if features.client_frame_language %}",
        "{% if features.unapproved_frame_identity %}",
        1,
    );
    assert!(render_java_source(&bad, &context).is_err());
    let mut metadata = f.bounded_metadata();
    metadata
        .source_rules
        .get_mut(CHOICE)
        .ok_or_else(|| eyre::eyre!("choice rule absent"))?[0]
        .when
        .any_features
        .push("unapproved_choice_consumer".to_owned());
    assert!(select_core_inputs(&metadata, &context, &inventory).is_err());
    Ok(())
}

#[test]
fn isolated_common_edits_and_raw_mutation_refusals_preserve_frozen_source_authority() -> Result<()>
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
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut edits = BTreeMap::new();
    for evidence in &f.ledger.files {
        let source = std::str::from_utf8(&f.sources[&evidence.path])?;
        let edited = source.replacen(
            &evidence.common_edit_anchor,
            &format!(
                "// Isolated real consent-choice common edit.\n{}",
                evidence.common_edit_anchor
            ),
            1,
        );
        fs::write(root.join(&evidence.path), &edited)?;
        edits.insert(evidence.path.clone(), edited);
    }
    let metadata = f.bounded_metadata();
    let inventory = discover_core_source_files(&root)?;
    let mut outputs = 0;
    for target in D2 {
        let context = f.core.context(target, FULL)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (path, _, _) in SOURCES {
            let artifact = &artifacts[path];
            let text = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = text
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("edited banner absent"))?;
            assert_eq!(body, render_java_source(&edits[path], &context)?);
            assert!(body.contains("// Isolated real consent-choice common edit."));
            outputs += 1;
        }
    }
    assert_eq!(outputs, 4);
    for (path, original) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *original);
    }
    Ok(())
}
