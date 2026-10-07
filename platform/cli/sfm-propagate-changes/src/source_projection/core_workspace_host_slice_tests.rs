//! Bounded source proofs for the real Multiplexer and its two direct providers.
//!
//! Frozen Git blobs are golden evidence, never production inputs. A matching
//! render does not prove Java compilation, runtime discovery or the 36 optional
//! provider closures. Authoring edits require deliberate review of these pins.
//! V3 separately checks immutable v1, preserved current-v2 and the one accessor delta.
#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;

const HOST: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenMultiplexer.java";
const PREVIOUS: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPreviousScreenPanel.java";
const DROP: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMFileDropTarget.java";
const PATHS: [&str; 3] = [HOST, PREVIOUS, DROP];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const PANELS: &str = "workspace_panels";
const NEW_OWNERS: [&str; 5] = [
    "workspace_directional_opening",
    "workspace_focus_tracking",
    "workspace_panel_entry_controls",
    "workspace_panel_move_gestures",
    "workspace_notifications",
];
const LEDGER: &str = "docs/tasks/sfm-core-workspace-host-slice.json";
const LEDGER_SHA: &str = "sha256:dd4def0a8b762b49cec2f5e9d3db9307afa8e1548930ac1210e22d35600bf882";

const CURRENT_HOST_BYTES: u64 = 161594;
const CURRENT_HOST_SHA: &str =
    "sha256:258b0a9e5fef738d839c3ae5cb79d864e222b49b238b6921ab441bc91ef7d800";
const V2_HOST_BYTES: u64 = 161388;
const V2_HOST_SHA: &str = "sha256:242f4706e7cbd20c3f153c0aae214eb12c0286a948b3832bfdfee534ab1097bc";
const V3_ACCESSOR_BEFORE: &str = "{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening %}\n\n    public @Nullable SFMScreenPanel panelInstance(SFMWorkspacePanelId panelId) {\n        return layout.panel(panelId);\n    }\n{% endif %}\n";
const V3_ACCESSOR_AFTER: &str = "{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_toast_path_actions %}\n{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_notifications %}\n\n    public @Nullable SFMScreenPanel panelInstance(SFMWorkspacePanelId panelId) {\n        return layout.panel(panelId);\n    }\n{% endif %}\n{% endif %}\n";
const HOST_V2_REVIEWED_EDITS: [(&str, &str); 8] = [
    (
        "{% if features.workspace_notifications %}\n    private @Nullable SFMWorkspaceToastQueue workspaceToasts = new SFMWorkspaceToastQueue();\n{% endif %}",
        "{% if features.workspace_notifications or features.workspace_toast_actions %}\n    private @Nullable SFMWorkspaceToastQueue workspaceToasts = new SFMWorkspaceToastQueue();\n{% endif %}",
    ),
    (
        "{% if features.workspace_notifications %}\n        toastQueue().tick();\n{% endif %}",
        "{% if features.workspace_notifications or features.workspace_toast_actions %}\n        toastQueue().tick();\n{% endif %}",
    ),
    (
        "{% if features.workspace_notifications %}\n        toastQueue().close();\n        workspaceToastHitRegions = List.of();\n{% endif %}",
        "{% if features.workspace_notifications or features.workspace_toast_actions %}\n        toastQueue().close();\n{% endif %}\n{% if features.workspace_notifications %}\n        workspaceToastHitRegions = List.of();\n{% endif %}",
    ),
    (
        "{% if features.workspace_notifications %}\n\n    public List<SFMWorkspaceToastQueue.ToastId> activeWorkspaceToastIds",
        "{% if features.workspace_notifications or features.workspace_toast_actions %}\n\n    public List<SFMWorkspaceToastQueue.ToastId> activeWorkspaceToastIds",
    ),
    (
        "{% if features.workspace_notifications %}\n\n    public SFMWorkspaceToastQueue.MutationResult stopWorkspaceToastTimer",
        "{% if features.workspace_notifications or features.workspace_toast_actions %}\n\n    public SFMWorkspaceToastQueue.MutationResult stopWorkspaceToastTimer",
    ),
    (
        "{% if features.workspace_notifications %}\n\n    public SFMWorkspaceToastQueue.MutationResult resumeWorkspaceToastTimer",
        "{% if features.workspace_notifications or features.workspace_toast_actions %}\n\n    public SFMWorkspaceToastQueue.MutationResult resumeWorkspaceToastTimer",
    ),
    (
        "{% if features.workspace_notifications %}\n\n    public SFMWorkspaceToastQueue.MutationResult dismissWorkspaceToast",
        "{% if features.workspace_notifications or features.workspace_toast_actions %}\n\n    public SFMWorkspaceToastQueue.MutationResult dismissWorkspaceToast",
    ),
    (
        "{% if features.workspace_notifications %}\n\n    private SFMWorkspaceToastQueue toastQueue",
        "{% if features.workspace_notifications or features.workspace_toast_actions %}\n\n    private SFMWorkspaceToastQueue toastQueue",
    ),
];
const V2_HOST_GOLDENS: [(&str, u64, &str); 3] = [
    (
        "only_workspace_toast_actions",
        17260,
        "sha256:31c933766a89c364a4e2dc87c43aa054a695c8fe5bb2fb3864c5dd49eba7e3f8",
    ),
    (
        "only_workspace_toast_path_actions",
        17260,
        "sha256:31c933766a89c364a4e2dc87c43aa054a695c8fe5bb2fb3864c5dd49eba7e3f8",
    ),
    (
        "full_without_workspace_notifications",
        82527,
        "sha256:4b104a5639adf140934f3279ec871896da498cba26171f0cabfd2df8c14a9890",
    ),
];

const V3_HOST_DELTA_GOLDENS: [(&str, u64, &str); 1] = [(
    "mixed_N_PATH",
    37255,
    "sha256:d22175feac3d029a51066ae47a47474f653b57e75f3e3acc870c487c667c4bd7",
)];

/// Test-only v3-to-v2 reconstruction; selected actual source binding is checked first.
fn v2_host_source(current: &str) -> Result<String> {
    let current =
        super::core_workspace_host_current_contract::reviewed_pre_typed_palette_host(current)?;
    ensure!(
        current.len() as u64 == CURRENT_HOST_BYTES
            && sha256(current.as_bytes()) == CURRENT_HOST_SHA
            && current.matches(V3_ACCESSOR_AFTER).count() == 1,
        "unreviewed v3 host source or accessor anchor"
    );
    let previous = current.replacen(V3_ACCESSOR_AFTER, V3_ACCESSOR_BEFORE, 1);
    ensure!(
        previous.len() as u64 == V2_HOST_BYTES && sha256(previous.as_bytes()) == V2_HOST_SHA,
        "v3 must reverse exactly one accessor guard to immutable v2"
    );
    Ok(previous)
}

/// Test-only reconstruction of immutable v1 authoring evidence, never a source input.
fn original_host_source(current: &str) -> Result<String> {
    let mut original = v2_host_source(current)?;
    for (before, after) in HOST_V2_REVIEWED_EDITS.into_iter().rev() {
        ensure!(
            original.matches(after).count() == 1,
            "v2 reverse anchor drift"
        );
        original = original.replacen(after, before, 1);
    }
    ensure!(
        original.len() == 161046
            && sha256(original.as_bytes())
                == "sha256:41d5786bac8fb951d022f1232edc9422a7c0f26452f5da1aff2001ccc933958b",
        "v1 authored host identity was not preserved"
    );
    Ok(original)
}
const RAW: [(&str, &str, u64); 8] = [
    (
        "432f13e4adaa63c07967a9a1fc8b1e515028493a",
        "sha256:1df832db1ba47fd2710fa3ca2f0680d70f4b90e5593afeb651eb1b020824a6df",
        104077,
    ),
    (
        "6f26e80ba3bd0022b76206112b82931c073462ae",
        "sha256:e8b723001fc1adedee78e4b73f4f531d3f16c7aab0558a772b03ccf3759ab777",
        14661,
    ),
    (
        "76efef90b1e9daf920138efdfaeace8f10bbd87a",
        "sha256:0270852ca3d2182a36181da29b47cb75b8adfc018575044184c535aeeca10125",
        14792,
    ),
    (
        "709afe4d65cf87621e35b71ea529ea5d75f4f4b8",
        "sha256:34e32d1717ced2db5254f9f6a316f323eca19f63df3ab2e1d4d80b82a1d1de76",
        15045,
    ),
    (
        "526bd53c0d5f686e6736e6dc5219e7143cde7a99",
        "sha256:a71635dc0f3cf59a27cf11ac44a95a0cc59e9209c3df6d98afee4aedcff0d9fe",
        271,
    ),
    (
        "c8505e19efb8e7f8701a4f2c160d3ca1dc844c2e",
        "sha256:98ddc9b4cbd003cc2e5936b4091dc6860602e30fbd7e41fa59a9f15a04e82c9b",
        1783,
    ),
    (
        "714beefe70495f83f09fd3ae4b4f2ef3145adcca",
        "sha256:b3300f63209dfeb79f6256ab9839659ce6850c5e1de37a8d43cf525e9af77d7e",
        1844,
    ),
    (
        "04f783dd580558d750eec785917f049cfa87884f",
        "sha256:32f900f0227c07b93f3a8e80de247be3d0fafd41cca567863a5a1a9a1de13bec",
        1862,
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

#[derive(Facet)]
struct Ledger {
    schema: String,
    scope: Scope,
    context_commits: BTreeMap<String, String>,
    normalization: Normalization,
    files: Vec<File>,
    raw_variants: Vec<Raw>,
    prerequisite_definitions: BTreeMap<String, Definition>,
    proposed_new_definitions: BTreeMap<String, Definition>,
    full_source_profiles: Vec<Full>,
    independent_profiles: Vec<Profile>,
    independent_goldens: Vec<Golden>,
}
#[derive(Facet)]
struct Scope {
    production_files: usize,
    context_cells: usize,
    present_cells: usize,
    absent_cells: usize,
    raw_variants: usize,
    stage_bytes: u64,
    new_source_flags: usize,
    referenced_owner_flags: usize,
    independent_profiles: usize,
    independent_goldens: usize,
    dependencies_changed: usize,
    java_compilation: bool,
    live_runtime: bool,
    complete_java_closure: bool,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    raw_byte_exact: bool,
    authorized_transformations: Vec<String>,
}
#[derive(Facet)]
struct File {
    name: String,
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
    source_rule: Rule,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Rule {
    input: String,
    template: bool,
    when: When,
}
#[derive(Facet)]
struct When {
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
    none_features: Vec<String>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    commit: String,
    present: bool,
    git_blob: Option<String>,
    raw_bytes: u64,
    raw_sha256: Option<String>,
}
#[derive(Facet)]
struct Raw {
    git_blob: String,
    bytes: u64,
    sha256: String,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct Full {
    target: String,
    enabled: Vec<String>,
}
#[derive(Facet)]
struct Profile {
    id: String,
    enabled: Vec<String>,
}
#[derive(Facet)]
struct Golden {
    profile: String,
    name: String,
    bytes: u64,
    sha256: String,
}

struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    source: BTreeMap<String, Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), 512 * 1024)?;
        ensure!(
            sha256(&bytes) == LEDGER_SHA,
            "immutable host ledger changed"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        let source = PATHS
            .into_iter()
            .map(|p| Ok((p.to_owned(), shared.read_source(p)?)))
            .collect::<Result<BTreeMap<_, _>>>()?;
        let raw = read_git_blobs(
            &shared.repository,
            &RAW.into_iter().map(|r| r.0.to_owned()).collect(),
        )?;
        let fixture = Self {
            shared,
            ledger,
            source,
            raw,
        };
        fixture.validate()?;
        Ok(fixture)
    }
    fn validate(&self) -> Result<()> {
        let s = &self.ledger.scope;
        ensure!(
            self.ledger.schema == "sfm:core-workspace-host-slice@1"
                && s.production_files == 3
                && s.context_cells == 60
                && s.present_cells == 30
                && s.absent_cells == 30
                && s.raw_variants == 8
                && s.stage_bytes == 164204
                && s.new_source_flags == 5
                && s.referenced_owner_flags == 27
                && s.independent_profiles == 106
                && s.independent_goldens == 318
                && s.dependencies_changed == 0
                && !s.java_compilation
                && !s.live_runtime
                && !s.complete_java_closure,
            "host source-only scope changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.raw_byte_exact
                && self
                    .ledger
                    .normalization
                    .authorized_transformations
                    .is_empty(),
            "host sources have no normalization authority"
        );
        ensure!(
            self.ledger.context_commits
                == CONTEXTS
                    .into_iter()
                    .map(|(a, b)| (a.to_owned(), b.to_owned()))
                    .collect()
                && self.ledger.files.len() == 3
                && self.ledger.raw_variants.len() == 8
                && self.ledger.full_source_profiles.len() == 10
                && self.ledger.independent_profiles.len() == 106
                && self.ledger.independent_goldens.len() == 318
                && self.ledger.proposed_new_definitions.len() == 5,
            "host evidence cardinality changed"
        );
        for (id, reviewed) in &self.ledger.prerequisite_definitions {
            let actual = self
                .shared
                .features
                .0
                .get(id)
                .ok_or_else(|| eyre::eyre!("unregistered reviewed host owner {id}"))?;
            if id == "screen_diagnostics" {
                ensure!(
                    reviewed.supported_targets == TARGETS[..2]
                        && reviewed.requires.is_empty()
                        && actual.supported_targets == TARGETS
                        && actual.requires.is_empty(),
                    "historical or current diagnostics support contract changed"
                );
            } else {
                ensure!(
                    actual.supported_targets == reviewed.supported_targets
                        && actual.requires == reviewed.requires,
                    "current reviewed host contract changed: {id}"
                );
            }
        }
        for (i, (oid, digest, count)) in RAW.into_iter().enumerate() {
            exact_raw(&self.raw[oid], digest, count)?;
            let r = &self.ledger.raw_variants[i];
            ensure!(
                r.git_blob == oid
                    && r.sha256 == digest
                    && r.bytes == count
                    && r.crlf_count == 0
                    && r.lone_cr_count == 0
                    && r.final_lf
                    && !r.bom,
                "immutable raw host identity changed"
            );
        }
        let mut seen = BTreeSet::new();
        let mut present = 0;
        for f in &self.ledger.files {
            let path = path_for_name(&f.name)?;
            let bytes = &self.source[path];
            let historical = if path == HOST {
                original_host_source(std::str::from_utf8(bytes)?)?.into_bytes()
            } else {
                bytes.clone()
            };
            let bytes = &historical;
            ensure!(
                seen.insert(path)
                    && f.intended_core_path == path
                    && bytes.len() as u64 == f.stage_bytes
                    && sha256(bytes) == f.stage_sha256
                    && std::str::from_utf8(bytes).is_ok()
                    && !bytes.contains(&b'\r')
                    && bytes.ends_with(b"\n")
                    && !bytes.starts_with(&[0xef, 0xbb, 0xbf])
                    && f.source_rule.input == path
                    && f.source_rule.template
                    && f.source_rule
                        .when
                        .targets
                        .iter()
                        .map(String::as_str)
                        .collect::<Vec<_>>()
                        == TARGETS
                    && f.source_rule.when.all_features == [PANELS]
                    && f.source_rule.when.any_features.is_empty()
                    && f.source_rule.when.none_features.is_empty(),
                "reviewed host source or membership changed: {path}"
            );
            let rules = self
                .shared
                .metadata
                .source_rules
                .get(path)
                .ok_or_else(|| eyre::eyre!("explicit host source rule missing"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == path
                    && rules[0].template
                    && rules[0].when.targets == f.source_rule.when.targets
                    && rules[0].when.all_features == [PANELS]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "actual host rule differs: {path}"
            );
            ensure!(f.witnesses.len() == 20, "twenty host cells missing");
            let mut contexts = BTreeSet::new();
            for w in &f.witnesses {
                let (kind, target) = w
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("fixed host context"))?;
                let oid = expected_oid(path, target, kind == "dev");
                let raw = oid.map(|o| &self.raw[o]);
                ensure!(
                    contexts.insert(w.context.as_str())
                        && matches!(kind, "dev" | "release")
                        && TARGETS.contains(&target)
                        && self.ledger.context_commits.get(&w.context) == Some(&w.commit)
                        && w.present == oid.is_some()
                        && w.git_blob.as_deref() == oid
                        && w.raw_bytes == raw.map_or(0, |r| r.len() as u64)
                        && w.raw_sha256 == raw.map(|r| sha256(r)),
                    "raw historical host membership differs: {path}"
                );
                present += usize::from(w.present);
            }
        }
        ensure!(
            seen.len() == 3 && present == 30,
            "host membership total changed"
        );
        Ok(())
    }
    fn context(&self, target: &str, enabled: &[String]) -> Result<ProjectionContext> {
        // These ledger profiles predate the explicit workspace implementation
        // owner. Translate only the witnessed old-two host behaviour, leaving
        // neutral diagnostics-only requests and all frozen ledgers unchanged.
        let mut enabled = enabled.to_vec();
        if TARGETS[..2].contains(&target)
            && enabled.iter().any(|name| name == "workspace_panels")
            && enabled.iter().any(|name| name == "screen_diagnostics")
            && !enabled
                .iter()
                .any(|name| name == "workspace_screen_diagnostics")
        {
            enabled.push("workspace_screen_diagnostics".to_owned());
        }
        self.shared.context(
            target,
            &enabled.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }
    fn full(&self, target: &str) -> Result<ProjectionContext> {
        let profile = self
            .ledger
            .full_source_profiles
            .iter()
            .find(|p| p.target == target)
            .ok_or_else(|| eyre::eyre!("host full profile missing"))?;
        self.context(target, &profile.enabled)
    }
    fn profile(&self, target: &str, id: &str) -> Result<ProjectionContext> {
        let p = self
            .ledger
            .independent_profiles
            .iter()
            .find(|p| p.id == id)
            .ok_or_else(|| eyre::eyre!("host independent profile missing {id}"))?;
        self.context(target, &p.enabled)
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selection = self.shared.selection_for_assertion(context, &inventory())?;
        let Some(input) = selection.inputs.get(path) else {
            ensure!(
                !enabled(context, PANELS) && selection.omitted_paths.contains(path),
                "host unexpectedly absent without explicit omission"
            );
            return Ok(None);
        };
        ensure!(
            enabled(context, PANELS) && input.input == path && input.template,
            "wrong host membership or historical routing"
        );
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.source[path])?,
            context,
        )?))
    }
    fn historical_render(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        let selection = self.shared.selection_for_assertion(context, &inventory())?;
        let input = selection
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("historical comparison requires actual selected input"))?;
        ensure!(
            input.input == path && input.template,
            "historical comparison source binding"
        );
        let source = if path == HOST {
            original_host_source(std::str::from_utf8(&self.source[path])?)?
        } else {
            std::str::from_utf8(&self.source[path])?.to_owned()
        };
        render_java_source(&source, context)
    }
    fn v2_render(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        let selection = self.shared.selection_for_assertion(context, &inventory())?;
        let input = selection
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("v2 comparison requires actual selected input"))?;
        ensure!(
            input.input == path && input.template,
            "v2 comparison source binding"
        );
        let source = if path == HOST {
            v2_host_source(std::str::from_utf8(&self.source[path])?)?
        } else {
            std::str::from_utf8(&self.source[path])?.to_owned()
        };
        render_java_source(&source, context)
    }
    fn host(&self, target: &str, profile: &str) -> Result<String> {
        self.render(HOST, &self.profile(target, profile)?)?
            .ok_or_else(|| eyre::eyre!("host unexpectedly absent"))
    }
    fn v3_render(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        let source = if path == HOST {
            super::core_workspace_host_current_contract::reviewed_pre_typed_palette_host(
                std::str::from_utf8(&self.source[path])?,
            )?
        } else {
            std::str::from_utf8(&self.source[path])?.to_owned()
        };
        render_java_source(&source, context)
    }
}
fn inventory() -> BTreeSet<String> {
    PATHS.into_iter().map(str::to_owned).collect()
}
fn enabled(c: &ProjectionContext, flag: &str) -> bool {
    c.features.get(flag) == Some(&true)
}
fn path_for_name(name: &str) -> Result<&'static str> {
    PATHS
        .into_iter()
        .find(|p| p.ends_with(&format!("/{name}.java")))
        .ok_or_else(|| eyre::eyre!("outside bounded host cohort"))
}
fn expected_oid(path: &str, target: &str, dev: bool) -> Option<&'static str> {
    if !dev {
        return None;
    }
    let d2 = matches!(target, "1.19.2" | "1.19.4");
    match path {
        HOST => Some(if d2 {
            RAW[0].0
        } else if matches!(target, "1.20" | "1.20.1") {
            RAW[1].0
        } else if target == "26.1.2" {
            RAW[3].0
        } else {
            RAW[2].0
        }),
        PREVIOUS => Some(if d2 {
            RAW[5].0
        } else if target == "26.1.2" {
            RAW[7].0
        } else {
            RAW[6].0
        }),
        DROP => Some(RAW[4].0),
        _ => None,
    }
}
fn exact_raw(bytes: &[u8], digest: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count
            && sha256(bytes) == digest
            && std::str::from_utf8(bytes).is_ok()
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw host witness changed or unapproved normalization"
    );
    Ok(())
}

/// Delimiter preflight only, not Java semantic analysis. These witnesses contain
/// no text blocks; ordinary strings, chars and comments are skipped literally.
fn balanced_java(source: &str) -> Result<()> {
    let b = source.as_bytes();
    let mut stack = Vec::new();
    let mut state = 0_u8;
    let mut quote = 0_u8;
    let mut i = 0;
    while i < b.len() {
        let c = b[i];
        let n = b.get(i + 1).copied();
        match state {
            1 => {
                if c == b'\n' {
                    state = 0;
                }
            }
            2 => {
                if c == b'*' && n == Some(b'/') {
                    state = 0;
                    i += 1;
                }
            }
            3 => {
                if c == b'\\' {
                    i += 1;
                } else if c == quote {
                    state = 0;
                }
            }
            _ => {
                if c == b'/' && n == Some(b'/') {
                    state = 1;
                    i += 1;
                } else if c == b'/' && n == Some(b'*') {
                    state = 2;
                    i += 1;
                } else if matches!(c, b'"' | b'\'') {
                    state = 3;
                    quote = c;
                } else if matches!(c, b'{' | b'(' | b'[') {
                    stack.push(c);
                } else if matches!(c, b'}' | b')' | b']') {
                    let wanted = match c {
                        b'}' => b'{',
                        b')' => b'(',
                        _ => b'[',
                    };
                    ensure!(
                        stack.pop() == Some(wanted),
                        "projected Java delimiter mismatch"
                    );
                }
            }
        }
        i += 1;
    }
    ensure!(
        stack.is_empty() && matches!(state, 0 | 1),
        "unfinished projected Java"
    );
    Ok(())
}

#[test]
fn sixty_cells_reconstruct_all_twenty_raw_host_contexts() -> Result<()> {
    let f = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXTS {
        let (kind, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("fixed context"))?;
        let c = if kind == "dev" {
            f.full(target)?
        } else {
            f.shared.context(target, &[])?
        };
        for path in PATHS {
            let out = f.render(path, &c)?;
            if let Some(oid) = expected_oid(path, target, kind == "dev") {
                assert_eq!(
                    out.as_deref().map(str::as_bytes),
                    Some(f.raw[oid].as_slice())
                );
                balanced_java(
                    out.as_deref()
                        .ok_or_else(|| eyre::eyre!("present output missing"))?,
                )?;
                present += 1;
            } else {
                assert!(out.is_none());
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (30, 30));
    Ok(())
}

#[test]
fn six_hundred_thirty_six_independent_owner_outputs_match_real_renderer_goldens() -> Result<()> {
    let f = Fixture::load()?;
    let mut seen = BTreeSet::new();
    let mut outputs = 0;
    for g in &f.ledger.independent_goldens {
        let path = path_for_name(&g.name)?;
        ensure!(
            seen.insert((g.profile.as_str(), path)),
            "duplicate independent host golden"
        );
        for target in &TARGETS[..2] {
            let c = f.profile(target, &g.profile)?;
            let out = f
                .render(path, &c)?
                .ok_or_else(|| eyre::eyre!("independent host absent"))?;
            let historical = f.historical_render(path, &c)?;
            assert_eq!(historical.len() as u64, g.bytes);
            assert_eq!(sha256(historical.as_bytes()), g.sha256);
            let v2 = f.v2_render(path, &c)?;
            let previous = (path == HOST)
                .then(|| {
                    V2_HOST_GOLDENS
                        .iter()
                        .find(|(profile, _, _)| *profile == g.profile)
                })
                .flatten();
            if let Some((_, bytes, digest)) = previous {
                assert_eq!(v2.len() as u64, *bytes);
                assert_eq!(sha256(v2.as_bytes()), *digest);
                assert_ne!(v2, historical);
            } else {
                assert_eq!(v2, historical);
            }
            let current = (path == HOST)
                .then(|| {
                    V3_HOST_DELTA_GOLDENS
                        .iter()
                        .find(|(profile, _, _)| *profile == g.profile)
                })
                .flatten();
            let v3 = f.v3_render(path, &c)?;
            if let Some((_, bytes, digest)) = current {
                assert_eq!(v3.len() as u64, *bytes);
                assert_eq!(sha256(v3.as_bytes()), *digest);
                assert_ne!(v3, v2);
            } else {
                assert_eq!(v3, v2);
            }
            if enabled(&c, "typed_command_palette") || path != HOST {
                assert_eq!(out, v3);
            } else {
                for typed_only in [
                    "SFMCommandPaletteScreen.openChoices(Component.literal(\"Close SFM workspace\"), escapeChoices());",
                    "else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) openWorkspaceToastActions(id);",
                    "private boolean openWorkspaceToastActions(",
                ] {
                    assert!(
                        !out.contains(typed_only),
                        "plain palette leaked typed operation: {typed_only}"
                    );
                }
                if out.contains("private boolean openPanelEntryActions(") {
                    assert!(out.contains("private boolean openPanelEntryActions(SFMPanelEntryAffordanceLayout.HitRegion hit) {\n        return false;\n    }"));
                }
            }
            balanced_java(&out)?;
            assert!(!out.contains("{%"));
            outputs += 1;
        }
    }
    assert_eq!((seen.len(), outputs), (318, 636));
    Ok(())
}

#[test]
fn minimum_host_keeps_real_providers_and_complete_old_input_signatures() -> Result<()> {
    let f = Fixture::load()?;
    for target in TARGETS {
        let c = f.shared.context(target, &[PANELS])?;
        let out = f
            .render(HOST, &c)?
            .ok_or_else(|| eyre::eyre!("minimum host absent"))?;
        balanced_java(&out)?;
        assert!(
            out.contains("implements SFMWorkspacePanelHost")
                && out.contains("SFMWorkspacePanelIntentDispatcher.apply(layout, source, intent)")
                && out.contains("new SFMPreviousScreenPanel(origin)")
                && out.contains("instanceof SFMFileDropTarget dropTarget")
                && out.contains("return super.keyPressed(")
                && out.contains("return super.mouseClicked(")
                && out.contains("super.mouseReleased(")
                && out.contains("super.mouseDragged(")
        );
        for token in [
            "SFMPanelWidgetHost",
            "SFMWorkspaceToastQueue",
            "SFMContextCaptureService",
            "SFMKeyboardUsageContextProvider",
            "SFMPanelReopenCatalog",
            "SFMWorkspacePanelMoveInteraction",
            "SFMCommandPaletteScreen",
            "observeWorkspaceFocus(",
        ] {
            assert!(!out.contains(token), "{token}");
        }
        if matches!(target, "1.19.2" | "1.19.4") {
            for sig in [
                "public boolean keyPressed(int keyCode, int scanCode, int modifiers)",
                "public boolean mouseClicked(double mouseX, double mouseY, int button)",
                "public boolean mouseScrolled(double mouseX, double mouseY, double delta)",
                "private static void enableScissor(SFMScreenPanelBounds bounds)",
            ] {
                assert!(out.contains(sig), "{sig}");
            }
        }
        let previous = f
            .render(PREVIOUS, &c)?
            .ok_or_else(|| eyre::eyre!("real previous provider absent"))?;
        assert!(
            previous.contains("record SFMPreviousScreenPanel")
                && previous.contains("previousScreen.getTitle()")
                && previous.contains("Previous screen parked; Escape restores it")
        );
        let drop = f
            .render(DROP, &c)?
            .ok_or_else(|| eyre::eyre!("real drop provider absent"))?;
        assert!(drop.contains("void onFilesDrop(List<Path> paths);"));
    }
    Ok(())
}

#[test]
fn notification_only_status_copy_and_render_do_not_require_palette_or_action_hosts() -> Result<()> {
    let f = Fixture::load()?;
    for target in &TARGETS[..2] {
        let c = f.profile(target, "only_workspace_notifications")?;
        assert!(!enabled(&c, "command_palette") && !enabled(&c, "workspace_toast_actions"));
        assert!(enabled(&c, "font_formatted_text"));
        let out = f.host(target, "only_workspace_notifications")?;
        for token in [
            "renderWorkspaceToasts(",
            "showWorkspaceToast(",
            "copyWorkspaceToast(id);",
            "stopWorkspaceToastTimer(",
            "resumeWorkspaceToastTimer(",
            "FormattedCharSequence",
            "import ca.teamdman.sfm.SFM;",
            "import net.minecraft.resources.ResourceLocation;",
        ] {
            assert!(out.contains(token), "{token}");
        }
        for token in [
            "SFMCommandPaletteScreen",
            "SFMToastPathAction",
            "SFMClientActionExecutor",
            "openWorkspaceToastActions(",
            "observeWorkspaceFocus(",
        ] {
            assert!(!out.contains(token), "{token}");
        }
        let action_no_palette = f.host(target, "only_workspace_toast_actions")?;
        assert!(
            action_no_palette.contains("workspaceToastActionDraft(")
                && action_no_palette.contains(
                    "import ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastQueue;"
                )
        );
        assert!(!action_no_palette.contains("SFMCommandPaletteScreen"));
        for token in [
            "private @Nullable SFMWorkspaceToastQueue workspaceToasts",
            "private SFMWorkspaceToastQueue toastQueue(",
            "toastQueue().tick();",
            "toastQueue().close();",
            "activeWorkspaceToastIds(",
            "stopWorkspaceToastTimer(",
            "resumeWorkspaceToastTimer(",
            "dismissWorkspaceToast(",
        ] {
            assert!(
                action_no_palette.contains(token),
                "missing headless queue API: {token}"
            );
        }
        for token in [
            "SFMWorkspaceToastContent",
            "SFMWorkspaceToastLayout",
            "renderWorkspaceToasts(",
            "toastContents(",
            "showWorkspaceToast(",
            "copyWorkspaceToast(",
            "copyWorkspaceToastDetails(",
            "writeVerifiedClipboard(",
            "keyboardHandler::setClipboard",
            "workspaceToastAt(",
        ] {
            assert!(
                !action_no_palette.contains(token),
                "unapproved action-only operation: {token}"
            );
        }
        let with_palette = f.host(target, "mixed_N_TA_P")?;
        assert!(
            !with_palette.contains("openWorkspaceToastActions(")
                && with_palette.contains("workspaceToastActionDraft(")
        );
        assert!(!with_palette.contains("SFMToastPathAction"));
        let mut typed = f.profile(target, "mixed_N_TA_P")?;
        for required in &f.shared.features.0["typed_command_palette"].requires {
            assert!(
                typed.features[required],
                "typed profile lacks prerequisite: {required}"
            );
        }
        typed
            .features
            .insert("typed_command_palette".to_owned(), true);
        let host = f.render(HOST, &typed)?.expect("typed host");
        assert!(
            host.contains("openWorkspaceToastActions(") && host.contains("SFMCommandPaletteScreen")
        );
        assert_eq!(host, f.v3_render(HOST, &typed)?);
    }
    Ok(())
}

#[test]
fn measurement_reopening_focus_and_widget_protocols_are_independent() -> Result<()> {
    let f = Fixture::load()?;
    for target in &TARGETS[..2] {
        let measure = f.host(target, "only_workspace_panel_measurement")?;
        assert!(
            measure.contains("Optional<SFMWorkspacePanelMetrics> measure(")
                && measure.contains("double scale = 1.0D;")
                && measure.contains("entry.panel().resized(this.minecraft, contentBounds(entry));")
        );
        for t in [
            "SFMPanelWidgetHost",
            "SFMPanelReopenCatalog",
            "SFMWorkspacePanelMetadata",
            "observeWorkspaceFocus(",
        ] {
            assert!(!measure.contains(t), "{t}");
        }
        let reopening = f.host(target, "only_workspace_panel_reopening")?;
        assert!(
            reopening.contains("SFMPanelReopenRecipe")
                && reopening.contains("registerReopenRecipe(")
        );
        assert!(!reopening.contains("SFMWorkspacePanelMetadata"));
        let widget = f.host(target, "only_workspace_widget_hosts")?;
        assert!(widget.contains("widgetHost()") && widget.contains("localMouse("));
        assert!(!widget.contains("observeWorkspaceFocus("));
        let focus = f.host(target, "only_workspace_focus_tracking")?;
        assert!(focus.contains("focusHistory(") && focus.contains("observeWorkspaceFocus("));
        assert!(!focus.contains("SFMKeyboardUsageContextSnapshot"));
        let keyboard = f.host(target, "only_workspace_keyboard_context")?;
        assert!(
            keyboard.contains("SFMKeyboardUsageContextProvider")
                && keyboard.contains("SFMKeyboardUsageContextSnapshot")
                && !keyboard.contains("record FocusedPanelWitness")
        );
    }
    Ok(())
}

#[test]
fn strict_contracts_reject_missing_prerequisites_and_unsupported_new_owner_targets() -> Result<()> {
    let f = Fixture::load()?;
    for target in TARGETS {
        let diagnostics = f.shared.context(target, &["screen_diagnostics"])?;
        assert!(diagnostics.features["screen_diagnostics"]);
        assert!(!diagnostics.features[PANELS] && !diagnostics.features["client_actions"]);
    }
    for owner in NEW_OWNERS {
        let d = f
            .ledger
            .proposed_new_definitions
            .get(owner)
            .ok_or_else(|| eyre::eyre!("reviewed proposed owner missing"))?;
        assert_eq!(
            d.supported_targets
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>(),
            &TARGETS[..2]
        );
        for target in &TARGETS[2..] {
            assert!(f.shared.context(target, &[PANELS, owner]).is_err());
        }
        let p = f
            .ledger
            .independent_profiles
            .iter()
            .find(|p| p.id == format!("only_{owner}"))
            .ok_or_else(|| eyre::eyre!("owner-only closed profile missing"))?;
        for required in &d.requires {
            let missing = p
                .enabled
                .iter()
                .filter(|x| *x != required)
                .map(String::as_str)
                .collect::<Vec<_>>();
            assert!(f.shared.context("1.19.2", &missing).is_err());
        }
    }
    assert!(
        f.shared
            .context("1.19.2", &[PANELS, "unreviewed_host_owner"])
            .is_err()
    );
    Ok(())
}

#[test]
fn common_core_edit_reaches_thirty_outputs_without_guard_or_witness_changes() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let anchor = "package ca.teamdman.sfm.client.screen.workspace;\n";
    let added = format!("{anchor}\n// Shared workspace host source proof.\n");
    let mut outputs = 0;
    for path in PATHS {
        let s = std::str::from_utf8(&f.source[path])?;
        ensure!(
            s.matches(anchor).count() == 1,
            "shared package anchor changed"
        );
        let p = temp.path().join(CORE_ROOT).join(path);
        fs::create_dir_all(p.parent().ok_or_else(|| eyre::eyre!("fixture parent"))?)?;
        fs::write(&p, s.replacen(anchor, &added, 1))?;
        let changed = read_bounded(&p, 256 * 1024)?;
        for target in TARGETS {
            let c = f.full(target)?;
            let original = f
                .render(path, &c)?
                .ok_or_else(|| eyre::eyre!("original host absent"))?;
            assert_eq!(
                render_java_source(std::str::from_utf8(&changed)?, &c)?,
                original.replacen(anchor, &added, 1)
            );
            outputs += 1;
        }
        assert_eq!(f.shared.read_source(path)?, f.source[path]);
    }
    assert_eq!(outputs, 30);
    Ok(())
}

#[test]
fn fixed_core_automatic_java_collection_rejects_historic_dispatch_and_unknown_controls()
-> Result<()> {
    let f = Fixture::load()?;
    let c = f.profile("1.19.2", "minimum")?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let mut metadata = f.shared.metadata.clone();
    metadata.source_rules.retain(|k, _| inventory().contains(k));
    for rules in metadata.source_rules.values_mut() {
        for r in rules {
            r.template = false;
        }
    }
    let selection = select_core_inputs(&metadata, &c, &inventory())?;
    for (out, input) in &selection.inputs {
        let bytes = if inventory().contains(out) {
            f.source[out].clone()
        } else {
            read_bounded(&f.shared.core.join(&input.input), 16 * 1024 * 1024)?
        };
        let p = root.join(&input.input);
        fs::create_dir_all(p.parent().ok_or_else(|| eyre::eyre!("fixture parent"))?)?;
        fs::write(&p, bytes)?;
    }
    let artifacts = collect_core_artifacts(&root, &selection, &c)?;
    for path in PATHS {
        let a = &artifacts[path];
        assert_eq!(a.source_bytes, f.source[path]);
        assert_eq!(a.source_path, format!("{CORE_ROOT}/{path}"));
        assert!(a.overlay.is_none());
        let (_, body) = std::str::from_utf8(&a.output_bytes)?
            .split_once('\n')
            .ok_or_else(|| eyre::eyre!("generated banner missing"))?;
        assert_eq!(Some(body), f.render(path, &c)?.as_deref());
    }
    let original = std::str::from_utf8(&f.source[HOST])?;
    for bad in [
        format!("{{% if features.unknown_host_owner %}}\n{original}{{% endif %}}\n"),
        format!("{{% else %}}\n{original}"),
        format!("{{% if features.workspace_panels %}}\n{original}{{% endcase %}}\n"),
    ] {
        assert!(render_java_source(&bad, &c).is_err());
    }
    let conjunction = "{% if features.workspace_panels and features.workspace_notifications %}\nclass FeatureConjunctionProof {}\n{% endif %}\n";
    assert!(render_java_source(conjunction, &c)?.trim().is_empty());
    let notifications = f.profile("1.19.2", "only_workspace_notifications")?;
    assert_eq!(
        render_java_source(conjunction, &notifications)?.trim(),
        "class FeatureConjunctionProof {}"
    );
    let no_panels = f.shared.context("1.19.2", &[])?;
    assert!(
        render_java_source(conjunction, &no_panels)?
            .trim()
            .is_empty()
    );
    fs::write(
        root.join(HOST),
        format!("{{% case environment %}}\n{{% when \"dev\" %}}\n{original}{{% endcase %}}\n"),
    )?;
    assert!(collect_core_artifacts(&root, &selection, &c).is_err());
    assert!(collect_core_artifacts(&temp.path().join("other-core"), &selection, &c).is_err());
    Ok(())
}

#[test]
fn frozen_raw_mutations_and_unsafe_delimiter_preflights_fail_closed() -> Result<()> {
    let f = Fixture::load()?;
    for (oid, digest, count) in RAW {
        let b = &f.raw[oid];
        assert!(exact_raw(&b[..b.len() - 1], digest, count).is_err());
        let crlf = std::str::from_utf8(b)?.replace('\n', "\r\n").into_bytes();
        assert!(exact_raw(&crlf, digest, count).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(b);
        assert!(exact_raw(&bom, digest, count).is_err());
        let mut token = b.clone();
        token[0] ^= 1;
        assert!(exact_raw(&token, digest, count).is_err());
    }
    for bad in [
        "class A { } }",
        "class A { void f( { }",
        "class A { String s=\"unterminated; }",
    ] {
        assert!(balanced_java(bad).is_err());
    }
    balanced_java("class A { String s=\"} [\"; /* } */ int[][] x={{1}}; }\n")?;
    Ok(())
}
