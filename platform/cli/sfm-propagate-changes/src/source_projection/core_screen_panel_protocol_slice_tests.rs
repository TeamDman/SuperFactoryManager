//! Source-only proofs for the reviewed shared panel protocol and pure values.
//!
//! Frozen Git identities are migration evidence, never production source routing.
//! These tests exercise real selection/Liquid/collection after root promotion.
//! They do not supply fake PanelContext, PanelHost, widget-host or native classes.
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

const DIR: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/";
const PANEL: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenPanel.java";
const CLOSE: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPanelCloseState.java";
const TOOLTIP: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPanelTooltip.java";
const PATHS: [&str; 3] = [PANEL, CLOSE, TOOLTIP];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const OPTIONAL: [&str; 4] = [
    "workspace_keyboard_context",
    "workspace_widget_hosts",
    "workspace_panel_tooltips",
    "workspace_lifecycle",
];
const LEDGER: &str = "docs/tasks/sfm-core-screen-panel-protocol-slice.json";
const LEDGER_SHA: &str = "sha256:6a5a0fd54b2bb6c1b2188cebedf8eeaea67884a8c6f049f5f14316797ef5018f";
const RAW: [(&str, &str, u64); 5] = [
    (
        "7ae346a175a5df202ad2bd064de8330b51f299fa",
        "sha256:4bf28e7fc7c84e08f667ed47c7458d85266795766626ec87123ffe1589d09835",
        3534,
    ),
    (
        "1b8839528bab23ea9bdcdf78055a960364d24200",
        "sha256:23c294dc1e96522f079c028e26db61c5a06b2739194a78b68cdfd086a49b6737",
        2055,
    ),
    (
        "304709869d9c274edff48202fc744c4a4f2919c8",
        "sha256:423bc66f56131cde733d4baf40e30caf8988fa8c203f6820adfbbd6acfcf49e7",
        2073,
    ),
    (
        "38e62ddfbdec59ed5c45a3568777ef346d66f033",
        "sha256:26153eea1244db8c1652c6cf97c5c859fe640b2836eb600d560cb0f81b41dc5c",
        421,
    ),
    (
        "eba372d5b7948194fc8b1aab23e60ef5246f2fe4",
        "sha256:0b59f7d83313fc6d094b3cbf9c2ba0df54704d37965e35658234a436ae942c76",
        835,
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
    full_source_profiles: Vec<Profile>,
    independent_source_profiles: Vec<Independent>,
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
    independent_masks: usize,
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
struct Profile {
    target: String,
    minecraft_version: String,
    enabled: Vec<String>,
    bytes: u64,
    sha256: String,
    git_blob: String,
}
#[derive(Facet)]
struct Independent {
    mask: u8,
    enabled: Vec<String>,
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
        let bytes = read_bounded(&shared.repository.join(LEDGER), 128 * 1024)?;
        ensure!(
            sha256(&bytes) == LEDGER_SHA,
            "immutable panel protocol ledger changed"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        let source = PATHS
            .into_iter()
            .map(|path| Ok((path.to_owned(), shared.read_source(path)?)))
            .collect::<Result<BTreeMap<_, _>>>()?;
        let raw = read_git_blobs(
            &shared.repository,
            &RAW.into_iter().map(|r| r.0.to_owned()).collect(),
        )?;
        let result = Self {
            shared,
            ledger,
            source,
            raw,
        };
        result.validate()?;
        Ok(result)
    }
    fn validate(&self) -> Result<()> {
        let scope = &self.ledger.scope;
        ensure!(
            self.ledger.schema == "sfm:core-screen-panel-protocol-slice@1"
                && scope.production_files == 3
                && scope.context_cells == 60
                && scope.present_cells == 14
                && scope.absent_cells == 46
                && scope.raw_variants == 5
                && scope.stage_bytes == 5749
                && scope.new_source_flags == 3
                && scope.independent_masks == 16
                && scope.dependencies_changed == 0
                && !scope.java_compilation
                && !scope.live_runtime
                && !scope.complete_java_closure,
            "source-only panel scope changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.raw_byte_exact
                && self
                    .ledger
                    .normalization
                    .authorized_transformations
                    .is_empty(),
            "raw normalization forbidden"
        );
        let contexts = CONTEXTS
            .into_iter()
            .map(|(k, v)| (k.to_owned(), v.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            contexts == self.ledger.context_commits,
            "frozen context identities changed"
        );
        ensure!(
            self.ledger.files.len() == 3
                && self.ledger.raw_variants.len() == 5
                && self.ledger.prerequisite_definitions.len() == 7
                && self.ledger.full_source_profiles.len() == 10
                && self.ledger.independent_source_profiles.len() == 16,
            "panel coverage changed"
        );
        for (key, definition) in &self.ledger.prerequisite_definitions {
            let actual = self
                .shared
                .features
                .0
                .get(key)
                .ok_or_else(|| eyre::eyre!("unregistered panel owner {key}"))?;
            let targets = if matches!(
                key.as_str(),
                "workspace_panels" | "client_actions" | "keyboard_profiles"
            ) {
                TARGETS.to_vec()
            } else {
                TARGETS[..2].to_vec()
            };
            let dependencies = match key.as_str() {
                "keyboard_profiles" => vec!["client_actions"],
                "workspace_keyboard_context" => vec!["keyboard_profiles"],
                _ => vec![],
            };
            ensure!(
                definition.supported_targets == targets
                    && definition.requires == dependencies
                    && actual.supported_targets == definition.supported_targets
                    && actual.requires == definition.requires,
                "panel owner/support contract changed: {key}"
            );
        }
        for (i, (oid, digest, count)) in RAW.into_iter().enumerate() {
            exact_raw(&self.raw[oid], digest, count)?;
            let fact = &self.ledger.raw_variants[i];
            ensure!(
                fact.git_blob == oid
                    && fact.bytes == count
                    && fact.sha256 == digest
                    && fact.crlf_count == 0
                    && fact.lone_cr_count == 0
                    && fact.final_lf
                    && !fact.bom,
                "raw panel evidence changed"
            );
        }
        let mut present = 0;
        let mut absent = 0;
        for file in &self.ledger.files {
            let path = file.intended_core_path.as_str();
            ensure!(
                PATHS.contains(&path) && file.name == class_name(path),
                "unexpected source scope"
            );
            let bytes = &self.source[path];
            ensure!(
                bytes.len() as u64 == file.stage_bytes
                    && sha256(bytes) == file.stage_sha256
                    && !bytes.contains(&b'\r')
                    && bytes.ends_with(b"\n"),
                "promoted panel source changed"
            );
            let targets = if path == PANEL {
                TARGETS.to_vec()
            } else {
                TARGETS[..2].to_vec()
            };
            ensure!(
                file.source_rule.input == path
                    && file.source_rule.template
                    && file.source_rule.when.targets == targets
                    && file.source_rule.when.all_features == [owner(path)]
                    && file.source_rule.when.any_features.is_empty(),
                "portable sparse predicate changed"
            );
            let rules = self
                .shared
                .metadata
                .source_rules
                .get(path)
                .ok_or_else(|| eyre::eyre!("sparse panel rule absent"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == path
                    && rules[0].when.targets == targets
                    && rules[0].when.all_features == [owner(path)]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "actual sparse predicate changed"
            );
            ensure!(file.witnesses.len() == 20, "incomplete panel witnesses");
            let mut seen = BTreeSet::new();
            for witness in &file.witnesses {
                let (kind, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid witness key"))?;
                let expected = kind == "dev" && (path == PANEL || is_d2(target));
                let oid = expected.then(|| golden_oid(path, target));
                let raw = oid.map(|id| &self.raw[id]);
                ensure!(
                    seen.insert(witness.context.as_str())
                        && contexts.get(&witness.context) == Some(&witness.commit)
                        && witness.present == expected
                        && witness.git_blob.as_deref() == oid
                        && witness.raw_bytes == raw.map_or(0, |r| r.len() as u64)
                        && witness.raw_sha256.as_deref() == raw.map(|r| sha256(r)).as_deref(),
                    "frozen panel membership changed"
                );
                if expected {
                    present += 1;
                } else {
                    absent += 1;
                }
            }
            ensure!(
                seen == contexts.keys().map(String::as_str).collect(),
                "panel context cells incomplete"
            );
        }
        ensure!(
            (present, absent) == (14, 46),
            "panel membership totals changed"
        );
        Ok(())
    }
    fn full(&self, target: &str) -> Result<ProjectionContext> {
        let profile = self
            .ledger
            .full_source_profiles
            .iter()
            .find(|p| p.target == target)
            .ok_or_else(|| eyre::eyre!("full profile absent"))?;
        let context = self.shared.context(
            target,
            &profile
                .enabled
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>(),
        )?;
        let raw = &self.raw[golden_oid(PANEL, target)];
        ensure!(
            context.minecraft_version == profile.minecraft_version
                && profile.git_blob == golden_oid(PANEL, target)
                && profile.bytes == raw.len() as u64
                && profile.sha256 == sha256(raw),
            "renderer target/profile changed"
        );
        Ok(context)
    }
    fn selection(&self, context: &ProjectionContext) -> Result<BTreeSet<String>> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let expected = PATHS
            .into_iter()
            .filter(|path| {
                feature(context, owner(path))
                    && (*path == PANEL || is_d2(&context.minecraft_version))
            })
            .map(str::to_owned)
            .collect::<BTreeSet<_>>();
        let scoped = selection
            .inputs
            .keys()
            .filter(|path| inventory().contains(*path))
            .cloned()
            .collect::<BTreeSet<_>>();
        ensure!(scoped == expected, "real sparse source membership differs");
        for path in PATHS {
            if expected.contains(path) {
                ensure!(
                    selection.inputs[path].input == path && !selection.omitted_paths.contains(path),
                    "canonical panel provenance changed"
                );
            } else {
                ensure!(
                    selection.omitted_paths.contains(path),
                    "absence not explicit"
                );
            }
        }
        Ok(scoped)
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        ensure!(
            self.selection(context)?.contains(path),
            "refuse omitted panel source"
        );
        render_java_source(std::str::from_utf8(&self.source[path])?, context)
    }
}
fn inventory() -> BTreeSet<String> {
    PATHS.into_iter().map(str::to_owned).collect()
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn class_name(path: &str) -> &str {
    path.strip_prefix(DIR)
        .unwrap_or(path)
        .strip_suffix(".java")
        .unwrap_or(path)
}
fn owner(path: &str) -> &str {
    match path {
        PANEL => "workspace_panels",
        CLOSE => "workspace_lifecycle",
        TOOLTIP => "workspace_panel_tooltips",
        _ => "unknown_panel_path",
    }
}
fn golden_oid(path: &str, target: &str) -> &'static str {
    match path {
        CLOSE => RAW[3].0,
        TOOLTIP => RAW[4].0,
        _ if is_d2(target) => RAW[0].0,
        _ if target == "26.1.2" => RAW[2].0,
        _ => RAW[1].0,
    }
}
fn feature(context: &ProjectionContext, key: &str) -> bool {
    context.features.get(key).copied().unwrap_or(false)
}
fn exact_raw(bytes: &[u8], digest: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count
            && sha256(bytes) == digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "unreviewed raw panel mutation"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

#[test]
fn all_sixty_frozen_cells_and_fourteen_exact_bodies_use_real_selector_and_renderer() -> Result<()> {
    let f = Fixture::load()?;
    let mut count = 0;
    for (key, _) in CONTEXTS {
        let (kind, target) = key
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid key"))?;
        let context = if kind == "dev" {
            f.full(target)?
        } else {
            f.shared.context(target, &[])?
        };
        let selected = f.selection(&context)?;
        for path in PATHS {
            let present = kind == "dev" && (path == PANEL || is_d2(target));
            assert_eq!(selected.contains(path), present);
            if present {
                assert_eq!(
                    f.render(path, &context)?.as_bytes(),
                    f.raw[golden_oid(path, target)]
                );
                count += 1;
            } else {
                assert!(f.render(path, &context).is_err());
            }
        }
    }
    assert_eq!(count, 14);
    Ok(())
}

#[test]
fn thirty_two_independent_protocol_outputs_match_reviewed_masks_and_exact_imports() -> Result<()> {
    let f = Fixture::load()?;
    let mut seen = BTreeSet::new();
    for profile in &f.ledger.independent_source_profiles {
        assert!(seen.insert(profile.mask));
        let mut expected = vec!["workspace_panels"];
        if profile.mask & 1 != 0 {
            expected.extend(["client_actions", "keyboard_profiles"]);
        }
        expected.extend(
            OPTIONAL
                .into_iter()
                .enumerate()
                .filter(|(i, _)| profile.mask & (1 << i) != 0)
                .map(|(_, name)| name),
        );
        assert_eq!(profile.enabled, expected);
        for target in &TARGETS[..2] {
            let context = f.shared.context(target, &expected)?;
            let panel = f.render(PANEL, &context)?;
            assert_eq!(panel.len() as u64, profile.bytes);
            assert_eq!(sha256(panel.as_bytes()), profile.sha256);
            assert_protocol_members(&panel, &context);
            assert_eq!(
                f.selection(&context)?.contains(CLOSE),
                feature(&context, "workspace_lifecycle")
            );
            assert_eq!(
                f.selection(&context)?.contains(TOOLTIP),
                feature(&context, "workspace_panel_tooltips")
            );
        }
    }
    assert_eq!(seen, (0..16).collect());
    Ok(())
}
fn assert_protocol_members(source: &str, context: &ProjectionContext) {
    let keyboard = feature(context, OPTIONAL[0]);
    let widget = feature(context, OPTIONAL[1]);
    let tooltip = feature(context, OPTIONAL[2]);
    let close = feature(context, OPTIONAL[3]);
    for anchor in [
        "keyboardUsageSituationId",
        "SFMKeyboardUsageSituations",
        "import net.minecraft.resources.ResourceLocation;",
    ] {
        assert_eq!(source.contains(anchor), keyboard);
    }
    assert_eq!(source.contains("widgetHost()"), widget);
    assert_eq!(source.contains("widgetHostOwnsInput()"), widget);
    assert_eq!(source.contains("SFMPanelWidgetHost"), widget);
    assert_eq!(source.contains("tooltipAt("), tooltip);
    assert_eq!(source.contains("SFMPanelTooltip"), tooltip);
    assert_eq!(source.contains("closeState()"), close);
    assert_eq!(source.contains("SFMPanelCloseState"), close);
    assert_eq!(
        source.contains("import java.util.Optional;"),
        widget || tooltip
    );
    for anchor in [
        "Component title();",
        "default Component narration()",
        "default void tick()",
        "default boolean keyPressed(",
        "default boolean mouseScrolled(",
    ] {
        assert!(source.contains(anchor));
    }
}

#[test]
fn pure_close_and_tooltip_values_do_not_force_panels_widget_hosts_or_keyboard_graph() -> Result<()>
{
    let f = Fixture::load()?;
    for target in &TARGETS[..2] {
        let close = f.shared.context(target, &["workspace_lifecycle"])?;
        assert_eq!(f.selection(&close)?, BTreeSet::from([CLOSE.to_owned()]));
        assert_eq!(f.render(CLOSE, &close)?.as_bytes(), f.raw[RAW[3].0]);
        let tooltip = f.shared.context(target, &["workspace_panel_tooltips"])?;
        assert_eq!(f.selection(&tooltip)?, BTreeSet::from([TOOLTIP.to_owned()]));
        assert_eq!(f.render(TOOLTIP, &tooltip)?.as_bytes(), f.raw[RAW[4].0]);
        let widget = f.shared.context(target, &["workspace_widget_hosts"])?;
        assert!(f.selection(&widget)?.is_empty());
        assert!(
            f.shared
                .context(target, &["workspace_keyboard_context"])
                .is_err()
        );
        assert!(
            f.shared
                .context(target, &["workspace_keyboard_context", "keyboard_profiles"])
                .is_err()
        );
    }
    for target in &TARGETS[2..] {
        for name in OPTIONAL {
            assert!(
                f.shared
                    .context(target, &["workspace_panels", name])
                    .is_err()
            );
        }
    }
    Ok(())
}

#[test]
fn minimum_panel_surface_preserves_real_renderer_apis_and_actual_target_alias() -> Result<()> {
    let f = Fixture::load()?;
    for target in TARGETS {
        let empty = f.shared.context(target, &[])?;
        assert!(f.selection(&empty)?.is_empty());
        let context = f.shared.context(target, &["workspace_panels"])?;
        let source = f.render(PANEL, &context)?;
        assert_protocol_members(&source, &context);
        let (import, parameter, annotation) = if is_d2(target) {
            (
                "import com.mojang.blaze3d.vertex.PoseStack;",
                "PoseStack poseStack,",
                false,
            )
        } else if target == "26.1.2" {
            (
                "import net.minecraft.client.gui.GuiGraphicsExtractor;",
                "GuiGraphicsExtractor graphics,",
                true,
            )
        } else {
            (
                "import net.minecraft.client.gui.GuiGraphics;",
                "GuiGraphics graphics,",
                true,
            )
        };
        assert!(source.contains(import) && source.contains(parameter));
        assert_eq!(
            source.contains("@ca.teamdman.sfm.common.util.MCVersionDependentBehaviour"),
            annotation
        );
        assert_eq!(source.matches("void render(").count(), 1);
        if !is_d2(target) {
            assert_eq!(source.as_bytes(), f.raw[golden_oid(PANEL, target)]);
        }
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
    }
    Ok(())
}

#[test]
fn common_package_edit_propagates_without_changing_witnesses_or_live_core() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let anchor = "package ca.teamdman.sfm.client.screen.workspace;\n";
    let replacement = format!("{anchor}\n// Shared panel protocol edit proof.\n");
    for path in PATHS {
        let source = std::str::from_utf8(&f.source[path])?;
        ensure!(
            source.matches(anchor).count() == 1,
            "common package anchor changed"
        );
        let p = temp.path().join(CORE_ROOT).join(path);
        fs::create_dir_all(
            p.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(&p, source.replacen(anchor, &replacement, 1))?;
        let changed = read_bounded(&p, 64 * 1024)?;
        for target in TARGETS {
            let context = f.full(target)?;
            if f.selection(&context)?.contains(path) {
                assert_eq!(
                    render_java_source(std::str::from_utf8(&changed)?, &context)?,
                    f.render(path, &context)?.replacen(anchor, &replacement, 1)
                );
            }
        }
        assert_eq!(f.shared.read_source(path)?, f.source[path]);
    }
    Ok(())
}

#[test]
fn collector_keeps_fixed_core_and_automatic_java_without_historical_routing() -> Result<()> {
    let f = Fixture::load()?;
    let context = f.full("1.19.2")?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let mut metadata = f.shared.metadata.clone();
    metadata
        .source_rules
        .retain(|key, _| inventory().contains(key));
    for rules in metadata.source_rules.values_mut() {
        for rule in rules {
            rule.template = false;
        }
    }
    let selection = select_core_inputs(&metadata, &context, &inventory())?;
    for (output, input) in &selection.inputs {
        let bytes = if inventory().contains(output) {
            f.source[output].clone()
        } else {
            read_bounded(&f.shared.core.join(&input.input), 16 * 1024 * 1024)?
        };
        let p = root.join(&input.input);
        fs::create_dir_all(
            p.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(p, bytes)?;
    }
    let artifacts = collect_core_artifacts(&root, &selection, &context)?;
    for path in PATHS {
        let artifact = &artifacts[path];
        assert_eq!(artifact.source_bytes, f.source[path]);
        assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{path}"));
        assert!(artifact.overlay.is_none());
        let (_, body) = std::str::from_utf8(&artifact.output_bytes)?
            .split_once('\n')
            .ok_or_else(|| eyre::eyre!("generated banner absent"))?;
        assert_eq!(body, f.render(path, &context)?);
    }
    let panel = std::str::from_utf8(&f.source[PANEL])?;
    fs::write(
        root.join(PANEL),
        format!("{{% case environment %}}\n{{% when \"dev\" %}}\n{panel}{{% endcase %}}\n"),
    )?;
    assert!(collect_core_artifacts(&root, &selection, &context).is_err());
    assert!(collect_core_artifacts(&temp.path().join("other-core"), &selection, &context).is_err());
    Ok(())
}

#[test]
fn raw_mutations_and_unknown_or_mismatched_directives_are_rejected() -> Result<()> {
    let f = Fixture::load()?;
    for (oid, hash, count) in RAW {
        let bytes = &f.raw[oid];
        assert!(exact_raw(&bytes[..bytes.len() - 1], hash, count).is_err());
        assert!(
            exact_raw(
                &std::str::from_utf8(bytes)?
                    .replace('\n', "\r\n")
                    .into_bytes(),
                hash,
                count
            )
            .is_err()
        );
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(bytes);
        assert!(exact_raw(&bom, hash, count).is_err());
        let mut token = bytes.clone();
        token[0] ^= 1;
        assert!(exact_raw(&token, hash, count).is_err());
    }
    let context = f.shared.context("1.19.2", &["workspace_panels"])?;
    let source = std::str::from_utf8(&f.source[PANEL])?;
    for bad in [
        format!("{{% else %}}\n{source}"),
        format!("{{% if features.unknown_panel_owner %}}\n{source}{{% endif %}}\n"),
        format!("{{% case minecraft_version %}}\n{source}{{% endif %}}\n"),
    ] {
        assert!(render_java_source(&bad, &context).is_err());
    }
    assert!(
        f.shared
            .context("1.19.2", &["unknown_panel_owner"])
            .is_err()
    );
    Ok(())
}

#[test]
fn reference_boundary_keeps_real_hosts_pending_instead_of_fake_compile_closure() -> Result<()> {
    let f = Fixture::load()?;
    let panel = f.render(PANEL, &f.full("1.19.2")?)?;
    assert!(
        panel.contains("SFMWorkspacePanelContext context")
            && panel.contains("Optional<SFMPanelWidgetHost>")
    );
    assert!(
        !panel.contains("class SFMPanelWidgetHost")
            && !panel.contains("class SFMWorkspacePanelHost")
    );
    assert!(
        !panel.contains("GLFW")
            && !panel.contains("glfwCreate")
            && !panel.contains("Runtime.getRuntime")
    );
    let close = f.render(CLOSE, &f.full("1.19.2")?)?;
    assert!(!close.contains("import net.minecraft"));
    let tooltip = f.render(TOOLTIP, &f.full("1.19.2")?)?;
    assert!(tooltip.contains("List.copyOf(Objects.requireNonNull(lines"));
    assert!(tooltip.contains("if (lines.isEmpty()) throw new IllegalArgumentException"));
    assert!(tooltip.contains("import net.minecraft.network.chat.Component;"));
    Ok(())
}
