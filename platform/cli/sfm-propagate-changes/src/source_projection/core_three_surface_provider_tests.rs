//! Prepared three-surface provider regressions. Not registered or executed by this capsule.
//! Real fixture, selector, renderer and collector calls. No Git/process/network reads.
//! Rich-settings circuit case is an explicit acceptance gate until authentic CaptureWidget is integrated.
//! These source tests do not establish Java compilation or visible UI behavior.
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

const LEDGER: &str = "docs/tasks/sfm-three-surface-provider-core-provenance-20261002.json";
const DETAILS: &str = "src/main/java/ca/teamdman/sfm/client/screen/SFMKeyBindingDetailsScreen.java";
const CONSOLE: &str = "src/main/java/ca/teamdman/sfm/client/screen/widget/SFMConsoleWidget.java";
const KEYCAP: &str = "src/main/java/ca/teamdman/sfm/client/screen/widget/SFMKeycapRenderer.java";
const CAPTURE_WIDGET: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/widget/SFMKeySequenceCaptureWidget.java";
const CAPTURE: &str = "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeySequenceCapture.java";
const SITUATIONS: &str =
    "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyboardUsageSituations.java";
const BINDING: &str = "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBinding.java";
const DISPLAY: &str = "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingDisplay.java";
const SCISSOR: &str = "src/main/java/ca/teamdman/sfm/client/screen/SFMScissorStack.java";
const PATHS: [&str; 3] = [DETAILS, CONSOLE, KEYCAP];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const KEYBOARD: [&str; 2] = ["client_actions", "keyboard_profiles"];
const PALETTE: [&str; 4] = [
    "client_actions",
    "keyboard_profiles",
    "client_theme",
    "command_palette",
];
const SETTINGS: [&str; 3] = ["client_actions", "keyboard_profiles", "keybinding_settings"];
const TYPED: [&str; 5] = [
    "client_actions",
    "keyboard_profiles",
    "client_theme",
    "command_palette",
    "typed_command_palette",
];
const RICH: [&str; 6] = [
    "client_actions",
    "keyboard_profiles",
    "client_theme",
    "command_palette",
    "typed_command_palette",
    "keybinding_settings",
];
const TEMPLATE_PINS: [(&str, usize, &str); 3] = [
    (
        "src/main/java/ca/teamdman/sfm/client/screen/SFMKeyBindingDetailsScreen.java",
        24591,
        "sha256:d2e62437b114f76a32d9e34e79502d99cba51ea11195c7183776edf084be3641",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/widget/SFMConsoleWidget.java",
        17936,
        "sha256:08b5021c67dcbc1e9e3a9d4611ed31b4c093da1e27a874fe59dd926f1328df5d",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/widget/SFMKeycapRenderer.java",
        2627,
        "sha256:f5a7253b3943300f87987181d8dd09f72925823dec732b7bd1adcddeafbb8706",
    ),
];
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
    bytes: 10286,
    digest: "sha256:71ab3c65ddcae2735c86ae6db136df0b05526a7a04cc8b5ce1645399781f3ae7",
    oid: "3fa138d7df42bc034e272f0bf85a1663e4a961b2",
};
const G1: Golden = Golden {
    bytes: 14216,
    digest: "sha256:db9fee0dfcbb9ba16ae7c80954cf77454a259f91758a6ef81ba2b339599292ad",
    oid: "4235687be8516685c6a50f79507de9ec3ca4776a",
};
const G2: Golden = Golden {
    bytes: 10111,
    digest: "sha256:0fdab04e13adb34f4c95f24dbde0bc933a2125088f72e2080bd6c554000123b9",
    oid: "aeaffb49c838d8a05e4037b49f2107d48fb09967",
};
const G3: Golden = Golden {
    bytes: 10140,
    digest: "sha256:469b554c19a8b7f2ddea3de57bdd5c76e778837a3375fec653e44de8dc84d94f",
    oid: "f99131b41e5d3d28162fb16d4ded73dd6c4d0461",
};
const G4: Golden = Golden {
    bytes: 10294,
    digest: "sha256:70ff41c4449b277fb2909d25b2fc51a2c09a77d5e121f188e9e58b2b06fbc7a0",
    oid: "83529c1b5083ccecde584d3ef96662825c41d2c3",
};
const G5: Golden = Golden {
    bytes: 12148,
    digest: "sha256:5aee5edea4d8db8e1156475dfb522d022a7379d5e1db3b4be8a5bd93778b778c",
    oid: "dedf62f630782f4e7afa0fb0cec07395798518d9",
};
const G6: Golden = Golden {
    bytes: 12141,
    digest: "sha256:b688bf23508783b9f81ca176890374c26547aa6ddc5880cd10ac9d1195737012",
    oid: "d35ca1405496fa6856e7371a1b15275c7efc830e",
};
const G7: Golden = Golden {
    bytes: 12619,
    digest: "sha256:ea8e7566edf8db56ad7a478d872b46fb0caad9884a48edb7c33ecd630f87bf27",
    oid: "54bf57047d848cf106e373b36f4021dd4b790426",
};
const G8: Golden = Golden {
    bytes: 12244,
    digest: "sha256:2616b7800d2aa0ed80ff306d595ae0f6a4284228eb58d29995bf3d6bc2dec2cc",
    oid: "72856689aadeed4ea0fcf92d224cd51f88b43087",
};
const G9: Golden = Golden {
    bytes: 11594,
    digest: "sha256:4d88a15c83c5c0fe20c73d8721078c694a0d9c284ec8dd29890dc10f6d2cce71",
    oid: "ab74be9ca3c26454f66017ad22e5ff71aacc4bb2",
};
const G10: Golden = Golden {
    bytes: 2627,
    digest: "sha256:f5a7253b3943300f87987181d8dd09f72925823dec732b7bd1adcddeafbb8706",
    oid: "8050b0829696ee074f31ca52d501e1fb59da1cc7",
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
fn historical_mask(target: &str) -> &'static [&'static str] {
    if d2(target) { &RICH } else { &PALETTE }
}
fn historical(index: usize, target: &str) -> Option<Golden> {
    match index {
        0 => Some(if d2(target) {
            G1
        } else if matches!(target, "1.20" | "1.20.1") {
            G2
        } else if target == "26.1.2" {
            G4
        } else {
            G3
        }),
        1 => Some(if target == "1.19.2" {
            G5
        } else if target == "1.19.4" {
            G6
        } else if matches!(target, "1.21.0" | "1.21.1") {
            G8
        } else if target == "26.1.2" {
            G9
        } else {
            G7
        }),
        2 => d2(target).then_some(G10),
        _ => None,
    }
}
fn identity(bytes: &[u8], g: Golden) -> Result<()> {
    ensure!(
        bytes.len() == g.bytes && sha256(bytes) == g.digest,
        "surface output digest changed"
    );
    let mut digest = Sha1::new();
    digest.update(format!("blob {}\0", bytes.len()).as_bytes());
    digest.update(bytes);
    ensure!(
        format!("{:x}", digest.finalize()) == g.oid,
        "surface framed identity changed"
    );
    ensure!(
        !bytes.contains(&b'\r') && bytes.ends_with(b"\n"),
        "surface LF contract changed"
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
            ledger.schema == "sfm:three_surface_core_provenance@1"
                && ledger.normalization == "none"
                && ledger.scope.contains("Source-only")
                && ledger.files.len() == 3,
            "surface ledger schema changed"
        );
        let commits: BTreeMap<String, String> = COMMITS
            .into_iter()
            .map(|(c, h)| (c.to_owned(), h.to_owned()))
            .collect();
        ensure!(
            ledger.context_commits == commits,
            "surface frozen twenty commits changed"
        );
        for (owner, targets, requires) in [
            ("keyboard_profiles", &TARGETS[..], &["client_actions"][..]),
            (
                "command_palette",
                &TARGETS[..],
                &["client_actions", "client_theme", "keyboard_profiles"][..],
            ),
            (
                "keybinding_settings",
                &TARGETS[..2],
                &["keyboard_profiles"][..],
            ),
            (
                "typed_command_palette",
                &TARGETS[..2],
                &["command_palette"][..],
            ),
        ] {
            let definition = &core.features.0[owner];
            ensure!(
                same(&definition.supported_targets, targets)
                    && same(&definition.requires, requires),
                "existing surface owner changed"
            );
        }
        for (i, (path, bytes, digest)) in TEMPLATE_PINS.into_iter().enumerate() {
            let leaf = &ledger.files[i];
            let raw = core.read_source(path)?;
            ensure!(
                leaf.path == path
                    && leaf.core_path == format!("{CORE_ROOT}/{path}")
                    && leaf.template_bytes == bytes
                    && leaf.template_sha256 == digest
                    && raw.len() == bytes
                    && sha256(&raw) == digest
                    && leaf.witnesses.len() == 20,
                "shared surface source identity changed"
            );
            let (targets, all, any) = if i == 0 {
                (&[][..], &["keyboard_profiles"][..], &[][..])
            } else if i == 1 {
                (&[][..], &["command_palette"][..], &[][..])
            } else {
                (
                    &TARGETS[..2],
                    &[][..],
                    &["keybinding_settings", "typed_command_palette"][..],
                )
            };
            ensure!(
                same(&leaf.membership.targets, targets)
                    && same(&leaf.membership.all_features, all)
                    && same(&leaf.membership.any_features, any)
                    && leaf.membership.none_features.is_empty(),
                "surface portable membership changed"
            );
            let rules = core
                .metadata
                .source_rules
                .get(path)
                .ok_or_else(|| eyre::eyre!("surface rule absent: {path}"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == path
                    && rules[0].template
                    && same(&rules[0].when.targets, targets)
                    && same(&rules[0].when.all_features, all)
                    && same(&rules[0].when.any_features, any)
                    && rules[0].when.none_features.is_empty(),
                "surface gained alternate input or owner"
            );
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "surface canonical source absent"
        );
        Ok(Self {
            core,
            ledger,
            inventory,
        })
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selected.inputs.get(path) else {
            ensure!(
                selected.omitted_paths.contains(path),
                "surface omitted without rule evidence"
            );
            return Ok(None);
        };
        ensure!(input.input == path, "surface selected alternate source");
        Ok(Some(
            render_java_source(std::str::from_utf8(&self.core.read_source(path)?)?, context)?
                .into_bytes(),
        ))
    }
    fn provider(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let input = selected
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("actual direct surface provider absent: {path}"))?;
        ensure!(input.input == path, "direct provider uses alternate input");
        render_java_source(std::str::from_utf8(&self.core.read_source(path)?)?, context)
    }
    fn isolated_metadata(&self) -> CoreProjectInputs {
        let mut m = self.core.metadata.clone();
        m.source_rules
            .retain(|path, _| PATHS.contains(&path.as_str()));
        m
    }
    fn isolated_inventory(&self) -> BTreeSet<String> {
        PATHS.into_iter().map(str::to_owned).collect()
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
fn surface_twenty_cells_each_preserve_twenty_two_present_thirty_eight_absent_and_authentic_bytes()
-> Result<()> {
    let f = Fixture::load()?;
    let (mut present, mut absent) = (0, 0);
    for (i, leaf) in f.ledger.files.iter().enumerate() {
        let mut seen = BTreeSet::new();
        for w in &leaf.witnesses {
            let (environment, target) = w
                .context
                .split_once('/')
                .ok_or_else(|| eyre::eyre!("invalid surface context"))?;
            let expected = if environment == "dev" {
                historical(i, target)
            } else {
                None
            };
            let mask = if environment == "dev" {
                historical_mask(target)
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
                "surface witness changed"
            );
            let context = f.core.context(target, mask)?;
            if let Some(g) = expected {
                identity(
                    &f.render(&leaf.path, &context)?
                        .expect("historical surface selected"),
                    g,
                )?;
                present += 1;
            } else {
                ensure!(
                    f.render(&leaf.path, &context)?.is_none(),
                    "historical absent surface invented"
                );
                absent += 1;
            }
        }
        assert_eq!(seen.len(), 20);
    }
    assert_eq!((present, absent), (22, 38));
    Ok(())
}

#[test]
fn surface_twenty_feature_off_controls_omit_sixty_paths_before_raw_reads() -> Result<()> {
    let mut f = Fixture::load()?;
    let catalog = CoreCatalog::load(&f.core.repository, &f.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    f.core.core = f.core.core.join("deliberately_missing_surface_sources");
    let mut omitted = 0;
    for key in catalog.catalog.0.keys() {
        let c = f.core.historical_feature_off_catalog_context(key)?;
        for path in PATHS {
            assert!(f.render(path, &c)?.is_none());
            omitted += 1;
        }
    }
    assert_eq!(omitted, 60);
    Ok(())
}

#[test]
fn surface_keyboard_only_details_use_real_six_argument_global_binding_without_capture_or_typed_palette()
-> Result<()> {
    let f = Fixture::load()?;
    for target in TARGETS {
        let c = f.core.context(target, &KEYBOARD)?;
        let body = f.render(DETAILS, &c)?.expect("keyboard Details selected");
        identity(
            &body,
            if d2(target) {
                G0
            } else {
                historical(0, target).expect("normal Details")
            },
        )?;
        let text = std::str::from_utf8(&body)?;
        ensure!(
            !text.contains("SFMKeySequenceCaptureWidget")
                && !text.contains("SFMKeycapRenderer")
                && text.contains("captured.add(new SFMKeyStroke(keyCode, modifiers(modifiers)))"),
            "keyboard-only Details gained richer capture dependency"
        );
        assert!(f.render(CONSOLE, &c)?.is_none() && f.render(KEYCAP, &c)?.is_none());
        if d2(target) {
            ensure!(
                text.contains("SFMKeyboardUsageSituations.GLOBAL")
                    && text.contains("public void render(PoseStack graphics")
                    && text.contains("GuiComponent.fill(graphics"),
                "D2 normal API adaptation absent"
            );
            ensure!(
                f.provider(SITUATIONS, &c)?
                    .contains("public static final ResourceLocation GLOBAL =")
                    && f.provider(BINDING, &c)?
                        .contains("ResourceLocation situationId,"),
                "D2 selected real declaring providers absent"
            );
        }
    }
    Ok(())
}

#[test]
fn surface_keycap_is_independent_settings_or_typed_d2_only_with_existing_display_tokens()
-> Result<()> {
    let f = Fixture::load()?;
    for target in &TARGETS[..2] {
        for mask in [&SETTINGS[..], &TYPED[..]] {
            let c = f.core.context(target, mask)?;
            identity(
                &f.render(KEYCAP, &c)?.expect("independent Keycap selected"),
                G10,
            )?;
            ensure!(
                f.provider(DISPLAY, &c)?
                    .contains("public static List<List<String>> tokens(SFMKeySequence sequence)"),
                "Keycap selected no real token provider"
            );
            if mask == &SETTINGS[..] {
                assert!(!c.features["typed_command_palette"] && !c.features["command_palette"]);
                identity(
                    &f.render(DETAILS, &c)?.expect("settings Details selected"),
                    G1,
                )?;
            } else {
                assert!(!c.features["keybinding_settings"]);
                identity(
                    &f.render(DETAILS, &c)?
                        .expect("normal typed Details selected"),
                    G0,
                )?;
            }
        }
    }
    for target in &TARGETS[2..] {
        assert!(
            f.core.context(target, &SETTINGS).is_err() && f.core.context(target, &TYPED).is_err()
        );
        assert!(
            f.render(KEYCAP, &f.core.context(target, &PALETTE)?)?
                .is_none()
        );
    }
    Ok(())
}

#[test]
fn surface_rich_settings_circuit_requires_authentic_capture_widget_provider_before_full_admission()
-> Result<()> {
    let f = Fixture::load()?;
    for target in &TARGETS[..2] {
        let c = f.core.context(target, &SETTINGS)?;
        let details =
            std::str::from_utf8(&f.render(DETAILS, &c)?.expect("settings Details"))?.to_owned();
        ensure!(
            details.contains("new SFMKeySequenceCaptureWidget(")
                && details.contains("captureWidget.hasFocusedToken()")
                && details.contains("this::cancelRecording"),
            "real richer capture call sites changed"
        );
        let widget = f.provider(CAPTURE_WIDGET, &c)?;
        ensure!(
            widget.contains("class SFMKeySequenceCaptureWidget")
                && widget.contains("hasFocusedToken(")
                && widget.contains("SFMKeySequenceCapture"),
            "actual capture widget seam absent"
        );
        ensure!(
            f.provider(CAPTURE, &c)?
                .contains("public boolean commitPendingEscapes()"),
            "selected capture model absent"
        );
    }
    Ok(())
}

#[test]
fn surface_console_all_ten_exact_bodies_preserve_actual_render_clip_and_batch_api_boundaries()
-> Result<()> {
    let f = Fixture::load()?;
    for target in TARGETS {
        let c = f.core.context(target, &PALETTE)?;
        let body = f.render(CONSOLE, &c)?.expect("console selected");
        identity(&body, historical(1, target).expect("historical console"))?;
        let text = std::str::from_utf8(&body)?;
        ensure!(
            text.contains("MAX_LINES = 2_000")
                && text.contains("scrollbarDragActive")
                && text.contains("boolean followTail = true"),
            "console common behavior changed"
        );
        if d2(target) {
            ensure!(
                text.contains("SFMScissorStack.pushGui(")
                    && text.contains("finally")
                    && text.contains("buffer.endBatch()"),
                "D2 clip/batch boundary changed"
            );
            ensure!(
                f.provider(SCISSOR, &c)?
                    .contains("public static void pop()"),
                "actual command_palette scissor provider absent"
            );
        } else if target == "26.1.2" {
            ensure!(
                text.contains("GuiGraphicsExtractor graphics")
                    && text.contains("graphics.enableScissor(")
                    && !text.contains("RenderSystem")
                    && !text.contains("Tesselator"),
                "26 extractor API changed"
            );
        } else if matches!(target, "1.21.0" | "1.21.1") {
            ensure!(
                text.contains("(int) firstLineY")
                    && !text.contains("drawInBatch")
                    && !text.contains("Tesselator"),
                "1.21 direct text API changed"
            );
        } else {
            ensure!(
                text.contains("graphics.pose().last().pose()")
                    && text.contains("buffer.endBatch()"),
                "1.20 batch API changed"
            );
        }
        if target == "1.21.0" {
            assert_eq!(c.minecraft_version, "1.21");
        }
    }
    Ok(())
}

#[test]
fn surface_real_collector_omits_malformed_off_sources_and_refuses_selected_or_unknown_feature_sources()
-> Result<()> {
    let f = Fixture::load()?;
    let m = f.isolated_metadata();
    let inventory = f.isolated_inventory();
    let off = f.core.context("1.19.2", &[])?;
    let on = f.core.context("1.19.2", &RICH)?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    std::fs::create_dir_all(&root)?;
    f.copy_project_inputs(&root, &m, &on)?;
    for path in PATHS {
        let destination = root.join(path);
        std::fs::create_dir_all(destination.parent().expect("source parent"))?;
        std::fs::write(destination, [0xff])?;
    }
    let omitted = select_core_inputs(&m, &off, &inventory)?;
    let output = collect_core_artifacts(&root, &omitted, &off)?;
    assert!(PATHS.iter().all(|path| !output.contains_key(*path)));
    let selected = select_core_inputs(&m, &on, &inventory)?;
    assert!(collect_core_artifacts(&root, &selected, &on).is_err());
    for path in PATHS {
        std::fs::write(root.join(path), f.core.read_source(path)?)?;
    }
    let raw = f.core.read_source(DETAILS)?;
    let mut poison = b"{% if features.not_registered %}\n{% endif %}\n".to_vec();
    poison.extend_from_slice(&raw);
    std::fs::write(root.join(DETAILS), poison)?;
    assert!(collect_core_artifacts(&root, &selected, &on).is_err());
    std::fs::write(root.join(DETAILS), &raw)?;
    let actual = collect_core_artifacts(&root, &selected, &on)?;
    for path in PATHS {
        let artifact = &actual[path];
        ensure!(
            artifact.source_bytes == f.core.read_source(path)?
                && artifact.source_path == format!("{CORE_ROOT}/{path}")
                && artifact.overlay.is_none()
                && artifact
                    .output_bytes
                    .starts_with(b"// GENERATED by sfm-propagate-changes;")
                && artifact
                    .output_bytes
                    .ends_with(&f.render(path, &on)?.expect("surface selected")),
            "actual collector body/provenance changed"
        );
    }
    Ok(())
}

#[test]
fn surface_real_common_comments_reach_twenty_two_collected_outputs_only_in_temp() -> Result<()> {
    let f = Fixture::load()?;
    let m = f.isolated_metadata();
    let inventory = f.isolated_inventory();
    let prefix = b"// Shared surface source propagation proof.\n";
    let before: BTreeMap<&str, Vec<u8>> = PATHS
        .into_iter()
        .map(|path| Ok((path, f.core.read_source(path)?)))
        .collect::<Result<_>>()?;
    let mut count = 0;
    for target in TARGETS {
        let c = f.core.context(target, historical_mask(target))?;
        let temp = tempfile::tempdir()?;
        let root = temp.path().join(CORE_ROOT);
        std::fs::create_dir_all(&root)?;
        f.copy_project_inputs(&root, &m, &c)?;
        for path in PATHS {
            let destination = root.join(path);
            std::fs::create_dir_all(destination.parent().expect("source parent"))?;
            let mut changed = prefix.to_vec();
            changed.extend_from_slice(&before[path]);
            std::fs::write(destination, changed)?;
        }
        let selected = select_core_inputs(&m, &c, &inventory)?;
        let actual = collect_core_artifacts(&root, &selected, &c)?;
        for path in PATHS {
            if let Some(output) = f.render(path, &c)? {
                let artifact = &actual[path];
                let mut expected_source = prefix.to_vec();
                expected_source.extend_from_slice(&before[path]);
                let mut expected_output = prefix.to_vec();
                expected_output.extend_from_slice(&output);
                ensure!(
                    artifact.source_bytes == expected_source
                        && artifact.source_bytes != before[path]
                        && artifact.source_path == format!("{CORE_ROOT}/{path}")
                        && artifact.overlay.is_none()
                        && artifact
                            .output_bytes
                            .starts_with(b"// GENERATED by sfm-propagate-changes;")
                        && artifact.output_bytes.ends_with(&expected_output),
                    "common source edit did not reach collected output"
                );
                count += 1;
            } else {
                assert!(!actual.contains_key(path));
            }
        }
    }
    assert_eq!(count, 22);
    for path in PATHS {
        ensure!(
            f.core.read_source(path)? == before[path],
            "canonical surface source changed"
        );
    }
    Ok(())
}
