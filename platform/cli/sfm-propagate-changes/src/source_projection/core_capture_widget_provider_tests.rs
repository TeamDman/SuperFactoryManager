//! Prepared CaptureWidget source regressions. Unregistered and not executed by this capsule.
//! Genuine portable fixture, selector, renderer and temp collector calls, without Git/process reads.
//! Historical rendering/input code stays unchanged; source acceptance is not Java/runtime proof.
#![cfg(test)]
use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use sha1::Digest;
use sha1::Sha1;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;

const LEDGER: &str = "docs/tasks/sfm-capture-widget-core-provenance-20261002.json";
const WIDGET: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/widget/SFMKeySequenceCaptureWidget.java";
const DISPLAY: &str = "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingDisplay.java";
const MODIFIER: &str = "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyModifier.java";
const SEQUENCE: &str = "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeySequence.java";
const STROKE: &str = "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyStroke.java";
const CAPTURE: &str = "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeySequenceCapture.java";
const FONT: &str = "src/main/java/ca/teamdman/sfm/client/screen/SFMFontUtils.java";
const WIDGET_UTILS: &str = "src/main/java/ca/teamdman/sfm/client/screen/SFMWidgetUtils.java";
const ANNOTATION: &str =
    "src/main/java/ca/teamdman/sfm/common/util/MCVersionDependentBehaviour.java";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const SETTINGS: [&str; 3] = ["client_actions", "keyboard_profiles", "keybinding_settings"];
const KEYBOARD: [&str; 2] = ["client_actions", "keyboard_profiles"];
const TEMPLATE_BYTES: usize = 9534;
const TEMPLATE_SHA: &str =
    "sha256:282b7ad165fb610b452e43eb1b995c1783046c0651b8cf8d5dcbc87bc8b96878";
const COMMITS: [(&str, &str); 20] = [
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
struct Golden {
    bytes: usize,
    digest: &'static str,
    oid: &'static str,
}
const G0: Golden = Golden {
    bytes: 8394,
    digest: "sha256:064944e143dba532a856811d0fdf2b0e40db3f83064b77bec8d20d76007ad16f",
    oid: "fbeb28c02f0fb93a003f952bdf7d9f890fe13eba",
};
const G1: Golden = Golden {
    bytes: 8841,
    digest: "sha256:a03e3a375b9e1c37da9fcd36dd738c728163975c32943abff69d40d3c3b7c070",
    oid: "9c87181b56cda40b552700a020a2bc63dd1793ce",
};
#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    scope: String,
    context_commits: BTreeMap<String, String>,
    files: Vec<Leaf>,
}
#[derive(Facet)]
struct Leaf {
    path: String,
    core_path: String,
    template_bytes: usize,
    template_sha256: String,
    membership: Membership,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Membership {
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
    none_features: Vec<String>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    raw_blob: Option<String>,
    raw_sha256: Option<String>,
    raw_bytes: Option<usize>,
    mode: Option<String>,
    explicit_registered_features: Vec<String>,
    features_origin: String,
}
struct Fixture {
    core: CoreTestFixture,
    ledger: Ledger,
    inventory: BTreeSet<String>,
}
fn same(a: &[String], b: &[&str]) -> bool {
    a.len() == b.len()
        && a.iter().map(String::as_str).collect::<BTreeSet<_>>() == b.iter().copied().collect()
}
fn d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn golden(target: &str) -> Golden {
    if target == "1.19.2" { G0 } else { G1 }
}
fn identity(bytes: &[u8], g: Golden) -> Result<()> {
    ensure!(
        bytes.len() == g.bytes && sha256(bytes) == g.digest,
        "authentic widget output changed"
    );
    let mut digest = Sha1::new();
    digest.update(format!("blob {}\0", bytes.len()).as_bytes());
    digest.update(bytes);
    ensure!(
        format!("{:x}", digest.finalize()) == g.oid,
        "widget Git-framed identity changed"
    );
    ensure!(
        !bytes.contains(&b'\r') && bytes.ends_with(b"\n"),
        "widget LF contract changed"
    );
    Ok(())
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            128 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:capture_widget_core_provenance@1"
                && ledger.normalization == "none"
                && ledger.scope.contains("Source-only")
                && ledger.files.len() == 1,
            "widget ledger contract changed"
        );
        let commits: BTreeMap<String, String> = COMMITS
            .into_iter()
            .map(|(c, h)| (c.to_owned(), h.to_owned()))
            .collect();
        ensure!(
            ledger.context_commits == commits,
            "widget fixed twenty commits changed"
        );
        let settings = &core.features.0["keybinding_settings"];
        ensure!(
            same(&settings.supported_targets, &TARGETS[..2])
                && same(&settings.requires, &["keyboard_profiles"]),
            "settings definition/prerequisite changed"
        );
        let keyboard = &core.features.0["keyboard_profiles"];
        ensure!(
            same(&keyboard.supported_targets, &TARGETS)
                && same(&keyboard.requires, &["client_actions"]),
            "keyboard definition changed"
        );
        let leaf = &ledger.files[0];
        let raw = core.read_source(WIDGET)?;
        ensure!(
            leaf.path == WIDGET
                && leaf.core_path == format!("{CORE_ROOT}/{WIDGET}")
                && leaf.template_bytes == TEMPLATE_BYTES
                && leaf.template_sha256 == TEMPLATE_SHA
                && raw.len() == TEMPLATE_BYTES
                && sha256(&raw) == TEMPLATE_SHA
                && leaf.witnesses.len() == 20,
            "shared widget template identity changed"
        );
        ensure!(
            same(&leaf.membership.targets, &TARGETS[..2])
                && same(&leaf.membership.all_features, &["keybinding_settings"])
                && leaf.membership.any_features.is_empty()
                && leaf.membership.none_features.is_empty(),
            "widget portable owner changed"
        );
        let rules = core
            .metadata
            .source_rules
            .get(WIDGET)
            .ok_or_else(|| eyre::eyre!("real widget rule absent"))?;
        ensure!(
            rules.len() == 1
                && rules[0].input == WIDGET
                && rules[0].template
                && same(&rules[0].when.targets, &TARGETS[..2])
                && same(&rules[0].when.all_features, &["keybinding_settings"])
                && rules[0].when.any_features.is_empty()
                && rules[0].when.none_features.is_empty(),
            "widget alternate input or prerequisite added"
        );
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(inventory.contains(WIDGET), "canonical widget source absent");
        Ok(Self {
            core,
            ledger,
            inventory,
        })
    }
    fn render(&self, c: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selected = select_core_inputs(&self.core.metadata, c, &self.inventory)?;
        let Some(input) = selected.inputs.get(WIDGET) else {
            ensure!(
                selected.omitted_paths.contains(WIDGET),
                "widget omission lacks evidence"
            );
            return Ok(None);
        };
        ensure!(input.input == WIDGET, "widget uses an alternate source");
        Ok(Some(
            render_java_source(std::str::from_utf8(&self.core.read_source(WIDGET)?)?, c)?
                .into_bytes(),
        ))
    }
    fn provider(&self, path: &str, c: &ProjectionContext) -> Result<String> {
        let selected = select_core_inputs(&self.core.metadata, c, &self.inventory)?;
        let input = selected
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("actual widget provider absent: {path}"))?;
        ensure!(input.input == path, "widget provider uses alternate source");
        Ok(
            render_java_source(std::str::from_utf8(&self.core.read_source(path)?)?, c)?
                .replace("\r\n", "\n"),
        )
    }
    fn isolated_metadata(&self) -> CoreProjectInputs {
        let mut m = self.core.metadata.clone();
        m.source_rules.retain(|path, _| path == WIDGET);
        m
    }
    fn isolated_inventory(&self) -> BTreeSet<String> {
        [WIDGET.to_owned()].into_iter().collect()
    }
    fn copy_project_inputs(
        &self,
        root: &Path,
        m: &CoreProjectInputs,
        c: &ProjectionContext,
    ) -> Result<()> {
        let selected = select_core_inputs(m, c, &self.isolated_inventory())?;
        for (output, input) in &selected.inputs {
            if output.starts_with("src/") {
                continue;
            }
            let bytes = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let destination = root.join(&input.input);
            std::fs::create_dir_all(destination.parent().expect("project parent"))?;
            std::fs::write(destination, bytes)?;
        }
        Ok(())
    }
}

#[test]
fn capture_widget_twenty_cells_preserve_two_present_eighteen_absent_and_exact_framed_sources()
-> Result<()> {
    let f = Fixture::load()?;
    let leaf = &f.ledger.files[0];
    let mut seen = BTreeSet::new();
    let (mut present, mut absent) = (0, 0);
    for w in &leaf.witnesses {
        let (environment, target) = w
            .context
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid widget context"))?;
        let expected = (environment == "dev" && d2(target)).then(|| golden(target));
        let mask = if expected.is_some() {
            &SETTINGS[..]
        } else {
            &[][..]
        };
        ensure!(
            seen.insert(w.context.clone())
                && f.ledger.context_commits.get(&w.context) == Some(&w.source_commit)
                && w.present == expected.is_some()
                && w.raw_blob.as_deref() == expected.map(|g| g.oid)
                && w.raw_sha256.as_deref() == expected.map(|g| g.digest)
                && w.raw_bytes == expected.map(|g| g.bytes)
                && w.mode.as_deref() == expected.map(|_| "100644")
                && same(&w.explicit_registered_features, mask)
                && w.features_origin
                    == "reviewed_current_explicit_reconstruction_not_historical_manifest",
            "widget witness changed"
        );
        let c = f.core.context(target, mask)?;
        if let Some(g) = expected {
            identity(&f.render(&c)?.expect("authentic widget selected"), g)?;
            present += 1;
        } else {
            assert!(f.render(&c)?.is_none());
            absent += 1;
        }
    }
    assert_eq!(seen.len(), 20);
    assert_eq!((present, absent), (2, 18));
    Ok(())
}

#[test]
fn capture_widget_twenty_feature_off_controls_omit_before_raw_reads() -> Result<()> {
    let mut f = Fixture::load()?;
    let catalog = CoreCatalog::load(&f.core.repository, &f.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    f.core.core = f
        .core
        .core
        .join("deliberately_missing_capture_widget_sources");
    for key in catalog.catalog.0.keys() {
        assert!(
            f.render(&f.core.historical_feature_off_catalog_context(key)?)?
                .is_none()
        );
    }
    Ok(())
}

#[test]
fn capture_widget_d2_independent_settings_select_actual_display_capture_and_draw_providers()
-> Result<()> {
    let f = Fixture::load()?;
    for &target in &TARGETS[..2] {
        let c = f.core.context(target, &SETTINGS)?;
        assert!(!c.features["typed_command_palette"] && !c.features["command_palette"]);
        identity(
            &f.render(&c)?.expect("settings widget selected"),
            golden(target),
        )?;
        ensure!(
            f.provider(DISPLAY, &c)?
                .contains("public static List<List<String>> tokens(SFMKeySequence sequence)"),
            "real tokens provider absent"
        );
        ensure!(
            f.provider(MODIFIER, &c)?.contains("CONTROL,")
                && f.provider(SEQUENCE, &c)?.contains("record SFMKeySequence")
                && f.provider(STROKE, &c)?.contains("record SFMKeyStroke"),
            "real physical key models absent"
        );
        let capture = f.provider(CAPTURE, &c)?;
        for member in [
            "public boolean hasFocusedToken()",
            "public boolean moveFocusedToken(int delta)",
            "public boolean removeFocusedToken()",
            "public boolean commitPendingEscapes()",
            "public boolean flushExpired(long nowMillis)",
        ] {
            ensure!(
                capture.contains(member),
                "actual capture member absent: {member}"
            );
        }
        ensure!(
            f.provider(FONT, &c)?.contains(
                "String text,\n            int x,\n            int y,\n            int colour,"
            ),
            "unconditional real string draw overload absent"
        );
        if target == "1.19.4" {
            let utils = f.provider(WIDGET_UTILS, &c)?;
            ensure!(
                utils.contains("public static int getX(AbstractWidget widget)")
                    && utils.contains("return widget.getX();")
                    && utils.contains("public static int getY(AbstractWidget widget)")
                    && utils.contains("return widget.getY();"),
                "real coordinate helper absent"
            );
            ensure!(
                f.provider(ANNOTATION, &c)?
                    .contains("public @interface MCVersionDependentBehaviour"),
                "real annotation provider absent"
            );
        }
    }
    Ok(())
}

#[test]
fn capture_widget_settings_off_or_unsupported_targets_do_not_invent_modern_stub() -> Result<()> {
    let f = Fixture::load()?;
    for target in TARGETS {
        assert!(f.render(&f.core.context(target, &KEYBOARD)?)?.is_none());
    }
    for &target in &TARGETS[2..] {
        assert!(f.core.context(target, &SETTINGS).is_err());
    }
    Ok(())
}

#[test]
fn capture_widget_exact_api_hunks_preserve_narration_render_and_input_dispatch() -> Result<()> {
    let f = Fixture::load()?;
    for &target in &TARGETS[..2] {
        let c = f.core.context(target, &SETTINGS)?;
        let body = f.render(&c)?.expect("widget");
        identity(&body, golden(target))?;
        let text = std::str::from_utf8(&body)?;
        for common in [
            "capture.flushExpired(System.currentTimeMillis())",
            "capture.escape(now)",
            "capture.moveFocusedToken(-1)",
            "capture.removeFocusedToken()",
            "GLFW.GLFW_KEY_KP_ENTER",
            "if (isModifierKey(keyCode)) return true;",
        ] {
            ensure!(
                text.contains(common),
                "historical input/capture member changed: {common}"
            );
        }
        if target == "1.19.2" {
            ensure!(
                text.contains("public void updateNarration(")
                    && text.contains("public void render(PoseStack")
                    && !text.contains("renderCapture(")
                    && !text.contains("SFMWidgetUtils"),
                "1.19.2 actual override changed"
            );
        } else {
            ensure!(
                text.contains("protected void updateWidgetNarration(")
                    && text.contains("public void renderWidget(PoseStack")
                    && text
                        .contains("private void renderCapture(PoseStack poseStack, int x, int y)")
                    && text.contains("int x = SFMWidgetUtils.getX(this);"),
                "1.19.4 actual API fragment changed"
            );
        }
    }
    Ok(())
}

#[test]
fn capture_widget_real_collector_omits_invalid_utf8_off_and_refuses_it_selected() -> Result<()> {
    let f = Fixture::load()?;
    let m = f.isolated_metadata();
    let inv = f.isolated_inventory();
    let off = f.core.context("1.19.2", &[])?;
    let on = f.core.context("1.19.2", &SETTINGS)?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    std::fs::create_dir_all(&root)?;
    f.copy_project_inputs(&root, &m, &on)?;
    let path = root.join(WIDGET);
    std::fs::create_dir_all(path.parent().expect("source parent"))?;
    std::fs::write(&path, [0xff])?;
    let omitted = select_core_inputs(&m, &off, &inv)?;
    assert!(!collect_core_artifacts(&root, &omitted, &off)?.contains_key(WIDGET));
    let selected = select_core_inputs(&m, &on, &inv)?;
    assert!(collect_core_artifacts(&root, &selected, &on).is_err());
    Ok(())
}

#[test]
fn capture_widget_real_collector_refuses_unknown_feature_and_records_actual_source_provenance()
-> Result<()> {
    let f = Fixture::load()?;
    let m = f.isolated_metadata();
    let inv = f.isolated_inventory();
    let c = f.core.context("1.19.4", &SETTINGS)?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    std::fs::create_dir_all(&root)?;
    f.copy_project_inputs(&root, &m, &c)?;
    let path = root.join(WIDGET);
    std::fs::create_dir_all(path.parent().expect("source parent"))?;
    let raw = f.core.read_source(WIDGET)?;
    let mut poison = b"{% if features.not_registered %}\n{% endif %}\n".to_vec();
    poison.extend_from_slice(&raw);
    std::fs::write(&path, poison)?;
    let selected = select_core_inputs(&m, &c, &inv)?;
    assert!(collect_core_artifacts(&root, &selected, &c).is_err());
    std::fs::write(&path, &raw)?;
    let actual = collect_core_artifacts(&root, &selected, &c)?;
    let artifact = &actual[WIDGET];
    ensure!(
        artifact.source_bytes == raw
            && artifact.source_path == format!("{CORE_ROOT}/{WIDGET}")
            && artifact.overlay.is_none()
            && artifact
                .output_bytes
                .starts_with(b"// GENERATED by sfm-propagate-changes;")
            && artifact
                .output_bytes
                .ends_with(&f.render(&c)?.expect("selected widget")),
        "real widget collector provenance changed"
    );
    Ok(())
}

#[test]
fn capture_widget_real_common_comment_reaches_both_collected_outputs_without_canonical_write()
-> Result<()> {
    let f = Fixture::load()?;
    let m = f.isolated_metadata();
    let inv = f.isolated_inventory();
    let raw = f.core.read_source(WIDGET)?;
    let prefix = b"// Shared capture-widget source propagation proof.\n";
    let mut changed = prefix.to_vec();
    changed.extend_from_slice(&raw);
    let mut count = 0;
    for &target in &TARGETS[..2] {
        let c = f.core.context(target, &SETTINGS)?;
        let temp = tempfile::tempdir()?;
        let root = temp.path().join(CORE_ROOT);
        std::fs::create_dir_all(&root)?;
        f.copy_project_inputs(&root, &m, &c)?;
        let path = root.join(WIDGET);
        std::fs::create_dir_all(path.parent().expect("source parent"))?;
        std::fs::write(&path, &changed)?;
        let selected = select_core_inputs(&m, &c, &inv)?;
        let actual = collect_core_artifacts(&root, &selected, &c)?;
        let artifact = &actual[WIDGET];
        let mut expected = prefix.to_vec();
        expected.extend_from_slice(&f.render(&c)?.expect("widget selected"));
        ensure!(
            artifact.source_bytes == changed
                && artifact.source_bytes != raw
                && artifact.source_path == format!("{CORE_ROOT}/{WIDGET}")
                && artifact.overlay.is_none()
                && artifact
                    .output_bytes
                    .starts_with(b"// GENERATED by sfm-propagate-changes;")
                && artifact.output_bytes.ends_with(&expected),
            "actual common widget source edit not propagated"
        );
        count += 1;
    }
    assert_eq!(count, 2);
    ensure!(
        f.core.read_source(WIDGET)? == raw,
        "canonical widget changed"
    );
    Ok(())
}
