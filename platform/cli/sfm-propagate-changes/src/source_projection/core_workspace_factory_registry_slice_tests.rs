//! Original workspace factory registry and typed human opener source proof.
//! Promotion-first; no registry event, UI, command, Java or runtime executes.
//! Counterfactual capability refusal is explicit and preserves all-on raw bytes.
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

const LEDGER: &str = "docs/tasks/sfm-core-workspace-factory-registry-slice.json";
const REGISTRY: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspaceScreenTypes.java";
const OPEN: &str = "src/main/java/ca/teamdman/sfm/client/action/OpenPanelAction.java";
const TYPE: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMTestScreenType.java";
const PANEL: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMTestScreenPanel.java";
const TEN: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
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
const SOURCES: [(&str, usize, &str); 4] = [
    (
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspaceScreenTypes.java",
        9525,
        "sha256:9e58ba1b548b45c59fc773e4d692ab750e473300d896ce682732a40597ca70c8",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/action/OpenPanelAction.java",
        11154,
        "sha256:9835e073925361bcb23aff0f1abd10dc7df8d747206c5b6cbca4d8c97523f7db",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMTestScreenType.java",
        2695,
        "sha256:8fabc4de5a233b1b2f967059f03b4295cea4191d0b099fefbed7233c0ace98b3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMTestScreenPanel.java",
        2156,
        "sha256:6807cb20c5bf4355ceabdab047313a062e41531f9c03e00e5d67ef86c95d4cd0",
    ),
];
const RAW: [(&str, usize, &str, usize); 11] = [
    (
        "406b2f18f8554f01778fd98770e59292efa8d13d",
        1131,
        "sha256:5b702cf0aaa86faf35977ee1c17e26b46edb10614eb627abbec6791ea29e707e",
        25,
    ),
    (
        "59da16fa85b4d0c23263b7a15ade1cc37d367d47",
        7288,
        "sha256:f8377208fc9094e4f43e5b8c6efb06d58ac31630e71546d89021becab7af2270",
        154,
    ),
    (
        "82dfb94ac046e20aa7cf82487b94d1922ada86f0",
        1582,
        "sha256:de39746ed5c7a02901863053f805e55cc523877010046f51314315a7bd1fc895",
        39,
    ),
    (
        "8c7ebb25e6b7509fd2ecf9cc2404b420b2f8dce7",
        1486,
        "sha256:16b5f52fc92dd3424b8d5c5d15c1acf879c8bbb059914400ef40e7a5f7f4ffcd",
        50,
    ),
    (
        "8c96ee65dc8bb0a3a53e105a9e8bcf9be6c7fa7a",
        8239,
        "sha256:f4b1de0a4b65eff92d08e033e7cc22f4f97c2006afa7a5f49d6012c4039c1ad5",
        182,
    ),
    (
        "b6877191c0ff801d05fea7d521a42c0f0ea06811",
        1143,
        "sha256:85f0a445c2fed85c70d7fef489e455d3284831bb5035f34b63a0126dbd27dc18",
        25,
    ),
    (
        "bade785b21ebc805556d27bf8ed28869cb53c9fb",
        1468,
        "sha256:0c4080040331d83e61954c5eca5dc44976af00a4c2c7edf27e8555d0acb940a2",
        50,
    ),
    (
        "bb521a68ae976edec8aba37d9e662aeb2b8afcf8",
        822,
        "sha256:8f2126cecf199ea07dc2ada2739ee211a28c0f55a0c2ecf11ecaff04be532d48",
        24,
    ),
    (
        "db32c98079367fee80627b5615f3bae07d33eeda",
        1407,
        "sha256:fa2ad46fb2c8e03df1f11f7098b9f9b04a11c261fc165b2db4d0a1c9bd115f46",
        49,
    ),
    (
        "f4f4b5f0f0042fefa170d32de574d54a39ed508c",
        7229,
        "sha256:5ea422f2c907e82a12688ddebc75fea6a8800fe2f018b516d71c4352b4642957",
        152,
    ),
    (
        "fe248698637b426abeffef0916aed5cabd804f00",
        873,
        "sha256:d47afef05ff1ba4b9a7a3d5dcbd0c5c261c12fd679c70b27c88efc7c73098541",
        25,
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    scope: Scope,
    context_commits: BTreeMap<String, String>,
    definitions: BTreeMap<String, Definition>,
    files: Vec<FileEvidence>,
    raw_objects: Vec<RawEvidence>,
    historical_membership: BTreeMap<String, BTreeMap<String, Option<String>>>,
    actual_provider_closure: Vec<Provider>,
    registrar_members: Vec<Member>,
    source_only_opener_profiles: Vec<OpenerProfile>,
}
#[derive(Facet)]
struct Scope {
    new_sources: usize,
    historical_cells: usize,
    present_cells: usize,
    absent_cells: usize,
    raw_objects: usize,
    raw_bytes: usize,
    authored_bytes: usize,
    registrar_fields: usize,
    definitions: usize,
    unchanged_providers: usize,
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
    common_edit_anchor: String,
    source_rule: InputVariant,
}
#[derive(Facet)]
struct RawEvidence {
    oid: String,
    bytes: usize,
    sha256: String,
    lf_count: usize,
    cr_count: usize,
    final_lf: bool,
}
#[derive(Facet)]
struct Provider {
    path: String,
    bytes: usize,
    sha256: String,
}
#[derive(Facet)]
struct Member {
    field: String,
    required_features: Vec<String>,
}
#[derive(Facet)]
struct OpenerProfile {
    target: String,
    mask: u8,
    bytes: usize,
    sha256: String,
}

struct Fixture {
    core: CoreTestFixture,
    ledger: Ledger,
    source: BTreeMap<String, Vec<u8>>,
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
            ledger.schema == "sfm:core-workspace-factory-registry-slice@1"
                && ledger.normalization == "none"
                && s.new_sources == 4
                && s.historical_cells == 80
                && s.present_cells == 32
                && s.absent_cells == 48
                && s.raw_objects == 11
                && s.raw_bytes == 32668
                && s.authored_bytes == 25530
                && s.registrar_fields == 24
                && s.definitions == 29
                && s.unchanged_providers == 15
                && s.prepared_tests == 8
                && ledger.files.len() == 4
                && ledger.raw_objects.len() == 11
                && ledger.registrar_members.len() == 24
                && ledger.definitions.len() == 29
                && ledger.actual_provider_closure.len() == 15
                && ledger.source_only_opener_profiles.len() == 32
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(k, v)| (k.to_owned(), v.to_owned()))
                        .collect(),
            "workspace factory reviewed scope changed"
        );
        let raw = read_git_blobs(
            &core.repository,
            &RAW.iter().map(|(oid, _, _, _)| (*oid).to_owned()).collect(),
        )?;
        for ((oid, len, digest, lf), row) in RAW.into_iter().zip(&ledger.raw_objects) {
            ensure!(
                row.oid == oid
                    && row.bytes == len
                    && row.sha256 == digest
                    && row.lf_count == lf
                    && row.cr_count == 0
                    && row.final_lf,
                "raw evidence row changed"
            );
            verify_raw(&raw[oid], len, digest, lf)?;
        }
        let mut source = BTreeMap::new();
        for ((path, len, digest), row) in SOURCES.into_iter().zip(&ledger.files) {
            let bytes = core.read_source(path)?;
            let historical = if path == REGISTRY {
                super::core_workspace_registrar_current_contract::reviewed_original_registrar(
                    std::str::from_utf8(&bytes)?,
                )?
                .into_bytes()
            } else {
                bytes.clone()
            };
            ensure!(
                row.path == path
                    && row.bytes == len
                    && row.sha256 == digest
                    && historical.len() == len
                    && sha256(&historical) == digest
                    && !bytes.contains(&b'\r')
                    && bytes.last() == Some(&b'\n')
                    && std::str::from_utf8(&bytes)?
                        .matches(&row.common_edit_anchor)
                        .count()
                        == 1,
                "authored workspace source changed: {path}"
            );
            ensure!(
                row.source_rule.input == path
                    && row.source_rule.template
                    && row.source_rule.when.any_features.is_empty()
                    && row.source_rule.when.none_features.is_empty()
                    && row.source_rule.when.all_features
                        == [if path == OPEN {
                            "workspace_panel_actions"
                        } else {
                            "workspace_panels"
                        }]
                    && row.source_rule.when.targets
                        == if path == OPEN {
                            D2.to_vec()
                        } else {
                            Vec::new()
                        }
                    && core.metadata.source_rules.get(path) == Some(&vec![row.source_rule.clone()]),
                "exact functional membership changed: {path}"
            );
            ensure!(
                ledger.historical_membership[path].len() == 20,
                "missing witness cells"
            );
            source.insert(path.to_owned(), bytes);
        }
        for (name, row) in &ledger.definitions {
            let actual = &core.features.0[name];
            if name == "screen_diagnostics" {
                ensure!(
                    row.supported_targets == D2
                        && row.requires.is_empty()
                        && actual.supported_targets == TEN
                        && actual.requires.is_empty(),
                    "historical or current registrar diagnostics contract changed"
                );
                continue;
            }
            ensure!(
                actual.supported_targets == row.supported_targets
                    && actual.requires == row.requires,
                "current owner support/prerequisites changed: {name}"
            );
        }
        for row in &ledger.actual_provider_closure {
            let bytes = core.read_source(&row.path)?;
            let historical = if row.path.ends_with("/SFMScreenMultiplexer.java") {
                super::core_workspace_host_current_contract::reviewed_pre_typed_palette_host(
                    std::str::from_utf8(&bytes)?,
                )?
                .into_bytes()
            } else {
                bytes
            };
            ensure!(
                historical.len() == row.bytes && sha256(&historical) == row.sha256,
                "real provider changed: {}",
                row.path
            );
        }
        Ok(Self {
            core,
            ledger,
            source,
            raw,
        })
    }
    fn inventory(&self) -> BTreeSet<String> {
        self.source.keys().cloned().collect()
    }
    fn context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        let mut names = requested
            .iter()
            .map(|name| (*name).to_owned())
            .collect::<BTreeSet<_>>();
        loop {
            let before = names.len();
            for name in names.clone() {
                let def = self
                    .core
                    .features
                    .0
                    .get(&name)
                    .ok_or_else(|| eyre::eyre!("unknown owner {name}"))?;
                names.extend(def.requires.iter().cloned());
            }
            if before == names.len() {
                break;
            }
        }
        // Preserve the ledger's historical workspace-diagnostics request
        // explicitly; the neutral provider no longer enables host behaviour.
        if D2.contains(&target)
            && names.contains("workspace_panels")
            && names.contains("screen_diagnostics")
        {
            names.insert("workspace_screen_diagnostics".to_owned());
        }
        self.core.context(
            target,
            &names.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }
    fn historical_context(&self, key: &str) -> Result<ProjectionContext> {
        let (environment, target) = key
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("context framing"))?;
        if environment == "release" {
            return self.context(target, &[]);
        }
        if !D2.contains(&target) {
            return self.context(target, &["workspace_panels"]);
        }
        let mut enabled = self
            .ledger
            .definitions
            .keys()
            .map(String::as_str)
            .collect::<Vec<_>>();
        enabled.extend(REGISTRAR_ADDED_OWNERS);
        self.context(target, &enabled)
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if !selected.inputs.contains_key(path) {
            ensure!(
                selected.omitted_paths.contains(path),
                "unaccounted source omission"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.source[path])?,
            context,
        )?))
    }
    fn golden(&self, path: &str, key: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let Some(oid) = self.ledger.historical_membership[path][key].as_ref() else {
            return Ok(None);
        };
        let raw = std::str::from_utf8(&self.raw[oid])?;
        if raw.contains("{%") {
            Ok(Some(render_java_source(raw, context)?))
        } else {
            Ok(Some(raw.to_owned()))
        }
    }
    fn metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|path, _| self.source.contains_key(path));
        metadata
    }
    fn write_sources(&self, root: &Path) -> Result<()> {
        for (path, bytes) in &self.source {
            let file = root.join(path);
            fs::create_dir_all(file.parent().ok_or_else(|| eyre::eyre!("source parent"))?)?;
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
            if self.source.contains_key(output) {
                continue;
            }
            let bytes = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let path = root.join(&input.input);
            if path.exists() {
                ensure!(
                    read_bounded(&path, 16 * 1024 * 1024)? == bytes,
                    "shared fixture input changed"
                );
            } else {
                fs::create_dir_all(path.parent().ok_or_else(|| eyre::eyre!("project parent"))?)?;
                fs::write(path, bytes)?;
            }
        }
        Ok(())
    }
}
fn verify_raw(bytes: &[u8], len: usize, digest: &str, lf: usize) -> Result<()> {
    ensure!(
        bytes.len() == len
            && sha256(bytes) == digest
            && bytes.last() == Some(&b'\n')
            && bytes.iter().filter(|byte| **byte == b'\n').count() == lf
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[239, 187, 191]),
        "raw normalization refused"
    );
    Ok(())
}
fn has_field(body: &str, field: &str) -> bool {
    body.contains(&format!(" {field} ="))
}

const REGISTRAR_ADDED_OWNERS: [&str; 10] = [
    "client_program_actions",
    "client_frame_render",
    "client_inbox",
    "terminal_keyboard_input",
    "terminal_frame_metadata",
    "workspace_panel_reopening",
    "client_program_reads",
    "client_theme",
    "client_properties",
    "canvas_text_editor",
];

fn registrar_added_owners(field: &str) -> &'static [&'static str] {
    match field {
        "TERMINAL_MOUNTS" => &[
            "client_program_actions",
            "client_frame_render",
            "client_inbox",
            "terminal_keyboard_input",
            "terminal_frame_metadata",
            "workspace_widget_hosts",
            "workspace_panel_reopening",
            "client_program_reads",
        ],
        "TERMINAL" | "TERMINAL_PROPERTIES" => &["workspace_panel_reopening"],
        "CANDIDATE_HISTORY" => &[
            "workspace_panel_reopening",
            "client_actions",
            "client_theme",
        ],
        "TEMPORAL_NUMBERING_CHAMBER" => &[
            "workspace_panel_reopening",
            "client_actions",
            "client_properties",
            "editor_document_panels",
            "canvas_text_editor",
        ],
        "WORKSPACE_COUNTERFACTUAL_CHAMBER" => &[
            "workspace_panel_reopening",
            "client_actions",
            "client_properties",
            "editor_document_panels",
        ],
        "HISTORY_GRAPH"
        | "TEXT_EDITOR"
        | "GRAMMAR"
        | "EXPLORER"
        | "REVIEW_CHANGES"
        | "REVIEW_COMMENTS"
        | "REVIEW_HASHTAGS"
        | "RELEASE_REVIEW_CHANGES"
        | "RELEASE_REVIEW_COMMENTS"
        | "RELEASE_REVIEW_HASHTAGS"
        | "RELEASE_REVIEW_QUERY"
        | "RELEASE_REVIEW_STATUS"
        | "RELEASE_REVIEW_MIGRATIONS" => &["workspace_panel_reopening", "client_actions"],
        _ => &[],
    }
}

#[test]
fn eighty_frozen_git_cells_reconstruct_all_raw_workspace_factory_outputs() -> Result<()> {
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
            "bounded offline workspace tree query failed"
        );
        let mut tree = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("tree framing"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && tree.insert(path.to_owned(), fields[2].to_owned()).is_none(),
                "unexpected Git member"
            );
        }
        let context = f.historical_context(key)?;
        for (path, _, _) in SOURCES {
            assert_eq!(
                tree.get(&format!("platform/minecraft/{path}")),
                f.ledger.historical_membership[path][key].as_ref()
            );
            match (f.golden(path, key, &context)?, f.render(path, &context)?) {
                (Some(expected), Some(actual)) => {
                    assert_eq!(actual.as_bytes(), expected.as_bytes(), "{key} {path}");
                    present += 1;
                }
                (None, None) => absent += 1,
                _ => panic!("historical source membership changed: {key} {path}"),
            }
        }
    }
    assert_eq!((present, absent), (32, 48));
    Ok(())
}

#[test]
fn workspace_only_keeps_real_test_factory_without_action_effects_on_all_ten() -> Result<()> {
    let f = Fixture::load()?;
    for target in TEN {
        let context = f.context(target, &["workspace_panels"])?;
        let registry = f
            .render(REGISTRY, &context)?
            .ok_or_else(|| eyre::eyre!("base registry omitted"))?;
        let expected = if ["1.19.2", "1.19.4", "1.20", "1.20.1"].contains(&target) {
            "bb521a68ae976edec8aba37d9e662aeb2b8afcf8"
        } else {
            "fe248698637b426abeffef0916aed5cabd804f00"
        };
        assert_eq!(registry.as_bytes(), f.raw[expected]);
        let kind = f
            .render(TYPE, &context)?
            .ok_or_else(|| eyre::eyre!("test factory omitted"))?;
        assert!(kind.contains(
            "new SFMTestScreenPanel(StringArgumentType.getString(context, \"display_text\"))"
        ));
        assert!(!kind.contains("Recipe") && !kind.contains("java.util.Objects"));
        assert!(f.render(PANEL, &context)?.is_some());
        assert!(f.render(OPEN, &context)?.is_none());
        assert!(!context.features["client_actions"] && !context.features["command_palette"]);
        let effects = [
            "src/main/java/ca/teamdman/sfm/client/action/SFMClientAction.java",
            "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionExecutor.java",
            "src/main/java/ca/teamdman/sfm/client/registry/SFMClientActions.java",
        ]
        .into_iter()
        .map(str::to_owned)
        .collect();
        let selected = select_core_inputs(&f.core.metadata, &context, &effects)?;
        for path in &effects {
            assert!(selected.omitted_paths.contains(path));
        }
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
        if target == "26.1.2" {
            assert!(kind.contains("Identifier screenTypeId"));
        }
    }
    Ok(())
}

#[test]
fn all_twenty_four_registrar_members_follow_real_local_owner_conjunctions() -> Result<()> {
    let f = Fixture::load()?;
    let names = f
        .ledger
        .registrar_members
        .iter()
        .map(|row| row.field.as_str())
        .collect::<BTreeSet<_>>();
    assert_eq!(names.len(), 24);
    for target in D2 {
        for requested in &f.ledger.registrar_members {
            for include_added in [false, true] {
                let mut enabled = requested
                    .required_features
                    .iter()
                    .map(String::as_str)
                    .collect::<Vec<_>>();
                if include_added {
                    enabled.extend(registrar_added_owners(&requested.field));
                    if requested.field == "TERMINAL_MOUNTS" {
                        enabled.push("terminal_remote");
                    }
                }
                let context = f.context(target, &enabled)?;
                let registry = f
                    .render(REGISTRY, &context)?
                    .ok_or_else(|| eyre::eyre!("registry omitted"))?;
                for member in &f.ledger.registrar_members {
                    let expected = member
                        .required_features
                        .iter()
                        .filter(|name| {
                            !(member.field == "TERMINAL" && *name == "terminal_remote")
                                && !(member.field == "TERMINAL_PROPERTIES"
                                    && *name == "workspace_panel_lookup")
                        })
                        .all(|name| context.features[name])
                        && registrar_added_owners(&member.field)
                            .iter()
                            .all(|name| context.features[*name])
                        && (!matches!(member.field.as_str(), "TERMINAL" | "TERMINAL_MOUNTS")
                            || [
                                "terminal_remote",
                                "terminal_vox_runtime",
                                "terminal_properties",
                            ]
                            .iter()
                            .any(|name| context.features[*name]));
                    assert_eq!(
                        has_field(&registry, &member.field),
                        expected,
                        "{target} {} {}",
                        requested.field,
                        member.field
                    );
                }
            }
        }
        let base = f.context(target, &["client_actions", "workspace_panels"])?;
        let registry = f
            .render(REGISTRY, &base)?
            .ok_or_else(|| eyre::eyre!("registry omitted"))?;
        for member in &f.ledger.registrar_members {
            assert_eq!(
                has_field(&registry, &member.field),
                member.field == "TEST_SCREEN"
            );
        }
    }
    Ok(())
}

#[test]
fn opener_sixteen_independent_masks_keep_genuine_capability_refusals_before_mutation() -> Result<()>
{
    let f = Fixture::load()?;
    for target in D2 {
        for mask in 0_u8..16 {
            let mut enabled = vec!["workspace_panel_actions"];
            let flags = [
                "workspace_stack_controls",
                "workspace_directional_opening",
                "workspace_panel_reopening",
                "command_palette",
            ];
            for (bit, flag) in flags.into_iter().enumerate() {
                if mask & (1 << bit) != 0 {
                    enabled.push(flag);
                }
            }
            let context = f.context(target, &enabled)?;
            let body = f
                .render(OPEN, &context)?
                .ok_or_else(|| eyre::eyre!("real typed opener omitted"))?;
            let profile = f
                .ledger
                .source_only_opener_profiles
                .iter()
                .find(|row| row.target == target && row.mask == mask)
                .ok_or_else(|| eyre::eyre!("reviewed opener mask missing"))?;
            assert_eq!(body.len(), profile.bytes);
            assert_eq!(sha256(body.as_bytes()), profile.sha256);
            let stack = mask & 1 != 0;
            let side = mask & 2 != 0;
            let reopening = mask & 4 != 0;
            let palette = mask & 8 != 0;
            assert_eq!(body.contains("SFMWorkspacePanelMetadata"), stack);
            assert_eq!(body.contains("workspace.openFocused"), stack);
            assert_eq!(body.contains("workspace.openIntoSlot"), stack);
            assert_eq!(body.contains("SFMWorkspaceSide"), side);
            assert_eq!(body.contains("SFMScreenMultiplexer.openToSide"), side);
            assert_eq!(body.contains("SFMPanelReopenRecipe"), reopening);
            assert_eq!(body.contains("SFMCommandPaletteScreen"), palette);
            assert_eq!(
                body.contains("paletteWasOpen"),
                palette && (stack || side),
                "{target} mask={mask}: palette effects require an opening capability"
            );
            assert_eq!(body.contains("commandContext.getSource().sendFeedback(Component.literal(\n                    \"Focused"),!stack);
            assert_eq!(
                body.contains(
                    "\"Directional panel opening requires workspace directional opening\""
                ),
                !side
            );
            if !stack {
                assert!(body.contains("if (direction == Direction.FOCUSED) return 0;"));
            }
            if !side {
                assert!(body.contains("if (direction != Direction.FOCUSED) return 0;"));
            }
            let private_start = body
                .find("    private int open(")
                .ok_or_else(|| eyre::eyre!("typed opener missing"))?;
            let private_body = &body[private_start
                ..body
                    .find("/** Shared registered-action")
                    .ok_or_else(|| eyre::eyre!("public seam absent"))?];
            if !stack || !side {
                assert!(
                    private_body.find("sendFeedback").unwrap()
                        < private_body
                            .find("SFMClientActionContext actionContext")
                            .unwrap()
                );
                if reopening {
                    assert!(
                        private_body.find("sendFeedback").unwrap()
                            < private_body.find("recipe.reopen()").unwrap()
                    );
                }
            }
            let public_start = body
                .find("    public static int openPanel")
                .ok_or_else(|| eyre::eyre!("public seam missing"))?;
            let public_body = &body[public_start
                ..body
                    .find("    private static List")
                    .ok_or_else(|| eyre::eyre!("registry list absent"))?];
            if stack || side {
                if !stack {
                    assert!(
                        public_body
                            .find("if (direction == Direction.FOCUSED) return 0;")
                            .unwrap()
                            < public_body.find("@Nullable Screen origin").unwrap()
                    );
                }
                if !side {
                    assert!(
                        public_body
                            .find("if (direction != Direction.FOCUSED) return 0;")
                            .unwrap()
                            < public_body.find("@Nullable Screen origin").unwrap()
                    );
                }
            } else {
                assert!(
                    !public_body.contains("@Nullable Screen origin")
                        && !public_body.contains("return 1;")
                );
            }
            assert!(
                body.contains("Objects.requireNonNull(direction)")
                    && body.contains("Objects.requireNonNull(screenTypes)")
                    && body.contains("registrations.sort(Comparator.comparing")
            );
            assert!(
                !body.contains("structuredResult")
                    && !body.contains("programmaticHandler")
                    && !body.contains("grant")
            );
        }
    }
    Ok(())
}

#[test]
fn real_collector_templates_false_java_and_preserves_thirty_two_owned_origins() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut metadata = f.metadata();
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let inventory = discover_core_source_files(&root)?;
    let mut count = 0;
    for (key, _) in CONTEXTS {
        let context = f.historical_context(key)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (path, _, _) in SOURCES {
            match f.golden(path, key, &context)? {
                Some(expected) => {
                    let artifact = &artifacts[path];
                    let (_, body) = std::str::from_utf8(&artifact.output_bytes)?
                        .split_once('\n')
                        .ok_or_else(|| eyre::eyre!("generated banner absent"))?;
                    assert_eq!(body.as_bytes(), expected.as_bytes());
                    assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{path}"));
                    assert!(artifact.overlay.is_none());
                    count += 1;
                }
                None => assert!(!artifacts.contains_key(path)),
            }
        }
    }
    assert_eq!(count, 32);
    Ok(())
}

#[test]
fn omitted_poison_and_unknown_or_unsupported_or_incomplete_owners_refuse() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    for path in f.source.keys() {
        let file = root.join(path);
        fs::create_dir_all(file.parent().ok_or_else(|| eyre::eyre!("poison parent"))?)?;
        fs::write(
            file,
            b"{% if features.unapproved_workspace_factory %}\n\xff",
        )?;
    }
    let metadata = f.metadata();
    let inventory = discover_core_source_files(&root)?;
    for target in TEN {
        let context = f.context(target, &[])?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for path in f.source.keys() {
            assert!(selected.omitted_paths.contains(path) && !artifacts.contains_key(path));
        }
    }
    assert!(
        f.core
            .context("1.19.2", &["workspace_panel_actions", "workspace_panels"])
            .is_err()
    );
    for target in TEN.into_iter().filter(|target| !D2.contains(target)) {
        assert!(f.context(target, &["workspace_panel_actions"]).is_err());
        assert!(f.context(target, &["workspace_panel_reopening"]).is_err());
    }
    let context = f.context("1.19.2", &["workspace_panels"])?;
    let bad = std::str::from_utf8(&f.source[REGISTRY])?.replacen(
        "features.client_actions",
        "features.unapproved_workspace_factory",
        1,
    );
    assert!(render_java_source(&bad, &context).is_err());
    let mut metadata = f.metadata();
    metadata
        .source_rules
        .get_mut(REGISTRY)
        .ok_or_else(|| eyre::eyre!("registry rule absent"))?[0]
        .when
        .any_features
        .push("unapproved_workspace_factory".to_owned());
    assert!(select_core_inputs(&metadata, &context, &inventory).is_err());
    Ok(())
}

#[test]
fn one_common_edit_per_source_propagates_to_every_selected_target() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    for row in &f.ledger.files {
        let text = std::str::from_utf8(&f.source[&row.path])?;
        let changed = text.replacen(
            &row.common_edit_anchor,
            &format!(
                "// Isolated shared factory edit.\n{}",
                row.common_edit_anchor
            ),
            1,
        );
        fs::write(root.join(&row.path), changed)?;
    }
    let metadata = f.metadata();
    let inventory = discover_core_source_files(&root)?;
    let mut count = 0;
    for target in TEN {
        let context = f.historical_context(&format!("dev/{target}"))?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for row in &f.ledger.files {
            let Some(original) = f.render(&row.path, &context)? else {
                continue;
            };
            let (_, body) = std::str::from_utf8(&artifacts[&row.path].output_bytes)?
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("common-edit banner absent"))?;
            assert_eq!(
                body,
                original.replacen(
                    &row.common_edit_anchor,
                    &format!(
                        "// Isolated shared factory edit.\n{}",
                        row.common_edit_anchor
                    ),
                    1
                )
            );
            count += 1;
        }
    }
    assert_eq!(count, 32);
    for row in &f.ledger.files {
        assert_eq!(f.core.read_source(&row.path)?, f.source[&row.path]);
    }
    Ok(())
}

#[test]
fn immutable_raw_objects_and_prior_twenty_two_cell_ledger_remain_exact() -> Result<()> {
    let f = Fixture::load()?;
    for (oid, len, digest, lf) in RAW {
        let original = &f.raw[oid];
        let mut append = original.clone();
        append.push(b'\n');
        let mut trim = original.clone();
        trim.pop();
        let mut space = original.clone();
        space.insert(0, b' ');
        let mut bom = vec![239, 187, 191];
        bom.extend(original);
        let crlf = std::str::from_utf8(original)?
            .replace('\n', "\r\n")
            .into_bytes();
        for changed in [append, trim, space, bom, crlf] {
            assert!(verify_raw(&changed, len, digest, lf).is_err());
        }
    }
    let old = read_bounded(
        &checked_file(
            &f.core.repository,
            "docs/tasks/sfm-core-consent-screen-factory-slice.json",
        )?,
        256 * 1024,
    )?;
    ensure!(
        old.len() == 14996
            && sha256(&old)
                == "sha256:c1f38badb689623e8010e5b653a6225f895ad9e2e79e4c6f8d7956711e6cbe44",
        "immutable prior factory evidence was rewritten"
    );
    let old_text = std::str::from_utf8(&old)?;
    assert!(old_text.contains("\"present_cells\":22") && old_text.contains("\"absent_cells\":38"));
    let context = f.historical_context("dev/1.19.2")?;
    let original = render_java_source(
        std::str::from_utf8(&f.raw["59da16fa85b4d0c23263b7a15ade1cc37d367d47"])?,
        &context,
    )?;
    assert_eq!(
        original.as_bytes(),
        f.raw["f4f4b5f0f0042fefa170d32de574d54a39ed508c"]
    );
    assert_eq!(
        original
            .matches("public static final SFMRegistryObject<")
            .count(),
        24
    );
    // The raw class also has one generic helper's REGISTERER.register call.
    assert_eq!(original.matches("REGISTERER.register(").count(), 25);
    Ok(())
}
