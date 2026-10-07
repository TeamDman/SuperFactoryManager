//! Frozen raw workspace model leaves: production selector/renderer evidence.
//!
//! Run only after root review, source promotion and exact sparse membership.
//! Historical Git blobs are bounded test goldens, never generation inputs.
//! The Layout/SFMScreenPanel and native host gaps remain explicit; no javac or
//! fully enabled runtime is claimed. Future body edits need reviewed goldens.
#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::collect_core_artifacts;
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
use std::io::Read;
use std::process::Stdio;

const LEDGER: &str = "docs/tasks/sfm-core-workspace-layout-leaves-slice.json";
const LEDGER_SHA: &str = "sha256:5d3b37ca4d7ca16d8cdb97c96a4d9753f940fb5acbb14e1ba30196bb58aa2b0c";
const LIMIT: u64 = 256 * 1024;
const TARGETS: [&str; 10] = [
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
// Class, frozen exact OID/SHA256/bytes, all10 class support.
const FILES: [(&str, &str, &str, u64, bool); 5] = [
    (
        "SFMWorkspacePanelMetadata",
        "35b41ce927e408c7dc667a72256ec1bc4c46951e",
        "sha256:243015d12a01a05fa241fcfd90e2dd686b5663682b4a4db8ea55a3e1dcec4114",
        5246,
        false,
    ),
    (
        "SFMWorkspaceDividerInteraction",
        "61d64c44e4e33418072326506e4354c825d8eaab",
        "sha256:f9bec772e00d1679520c1b3944cfcea85e89a004f878f45bfe6c6c5bea30df53",
        9788,
        false,
    ),
    (
        "SFMWorkspaceResizeDividersIntent",
        "e3586e4e963bb0081cae932f2db0d1575050b8b1",
        "sha256:dc1045f39f3354b043966099df054458fb3128415dc95e01d5d3ac034f83ddb1",
        1330,
        false,
    ),
    (
        "SFMWorkspacePanelGroup",
        "6677ea42083a100648b3c096504736ea92428b7e",
        "sha256:a1b503fa7c7d6bad0042caa5a19d7ae07541286a961574528a9ad5fe8676baf6",
        1515,
        true,
    ),
    (
        "SFMWorkspacePanelIntentResult",
        "163c2467f56cf3e91d2236f73ea256bfb667416d",
        "sha256:5a9ece47752964199d395fd2fe945243caa95bf475bca1f9c287b03b163e4e82",
        142,
        true,
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    scope: Scope,
    context_commits: BTreeMap<String, String>,
    normalization: Normalization,
    prerequisite_definitions: BTreeMap<String, Definition>,
    files: Vec<File>,
    raw_variants: Vec<Raw>,
    full_source_profiles: Vec<Profile>,
}
#[derive(Facet)]
struct Scope {
    planned_files: usize,
    production_files: usize,
    context_cells: usize,
    present_cells: usize,
    absent_cells: usize,
    raw_variants: usize,
    stage_bytes: u64,
    new_source_flags: usize,
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
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct File {
    name: String,
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
    class_support: Vec<String>,
    source_rule: SourceRule,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct SourceRule {
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
    name: String,
    git_blob: String,
    sha256: String,
    bytes: u64,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
}
#[derive(Facet)]
struct Profile {
    target: String,
    enabled: Vec<String>,
    selected_paths: Vec<String>,
}

struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    sources: Vec<Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), LIMIT)?;
        ensure!(
            sha256(&bytes) == LEDGER_SHA,
            "workspace leaf reviewed ledger changed"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        let sources = FILES
            .iter()
            .map(|f| shared.read_source(&path(f.0)))
            .collect::<Result<Vec<_>>>()?;
        let raw = read_git_blobs(
            &shared.repository,
            &FILES.iter().map(|f| f.1.to_owned()).collect(),
        )?;
        let fixture = Self {
            shared,
            ledger,
            sources,
            raw,
        };
        fixture.validate()?;
        Ok(fixture)
    }
    fn validate(&self) -> Result<()> {
        let s = &self.ledger.scope;
        ensure!(
            self.ledger.schema == "sfm:core-workspace-layout-leaves-slice@1"
                && s.planned_files == 6
                && s.production_files == 5
                && s.context_cells == 100
                && s.present_cells == 26
                && s.absent_cells == 74
                && s.raw_variants == 5
                && s.stage_bytes == 18021
                && s.new_source_flags == 1
                && s.dependencies_changed == 0
                && !s.java_compilation
                && !s.live_runtime
                && !s.complete_java_closure,
            "bounded five-leaf tranche changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.raw_byte_exact
                && self
                    .ledger
                    .normalization
                    .authorized_transformations
                    .is_empty(),
            "raw leaves have no normalization"
        );
        ensure!(
            self.ledger.files.len() == 5
                && self.ledger.raw_variants.len() == 5
                && self.ledger.full_source_profiles.len() == 10
                && self.ledger.prerequisite_definitions.len() == 3,
            "leaf inventory changed"
        );
        let contexts = CONTEXTS
            .into_iter()
            .map(|(k, v)| (k.to_owned(), v.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            contexts == self.ledger.context_commits,
            "frozen twenty contexts changed"
        );
        for (key, d) in &self.ledger.prerequisite_definitions {
            let actual = self
                .shared
                .features
                .0
                .get(key)
                .ok_or_else(|| eyre::eyre!("leaf feature not registered: {key}"))?;
            let supported = if key == "workspace_panels" {
                TARGETS.to_vec()
            } else {
                TARGETS[..2].to_vec()
            };
            ensure!(
                d.supported_targets == supported
                    && d.requires.is_empty()
                    && actual.supported_targets == d.supported_targets
                    && actual.requires == d.requires,
                "leaf owner support/prerequisite changed: {key}"
            );
        }
        for (index, f) in FILES.iter().enumerate() {
            let p = path(f.0);
            let row = &self.ledger.files[index];
            let support = if f.4 {
                TARGETS.to_vec()
            } else {
                TARGETS[..2].to_vec()
            };
            let owners = owners(index);
            ensure!(
                row.name == f.0
                    && row.intended_core_path == p
                    && row.stage_bytes == f.3
                    && row.stage_sha256 == f.2
                    && row.class_support == support
                    && row.source_rule.input == p
                    && row.source_rule.template
                    && row.source_rule.when.targets == support
                    && set(&row.source_rule.when.all_features) == owners
                    && row.source_rule.when.any_features.is_empty()
                    && row.witnesses.len() == 20,
                "leaf manifest identity/predicate changed"
            );
            valid_raw(&self.raw[f.1], f.2, f.3)?;
            valid_raw(&self.sources[index], f.2, f.3)?;
            ensure!(
                self.sources[index] == self.raw[f.1],
                "leaf differs from frozen raw"
            );
            let raw = &self.ledger.raw_variants[index];
            ensure!(
                raw.name == f.0
                    && raw.git_blob == f.1
                    && raw.sha256 == f.2
                    && raw.bytes == f.3
                    && raw.crlf_count == 0
                    && raw.lone_cr_count == 0
                    && raw.final_lf
                    && !raw.bom,
                "raw leaf facts changed"
            );
            let rules = self
                .shared
                .metadata
                .source_rules
                .get(&p)
                .ok_or_else(|| eyre::eyre!("leaf sparse rule absent"))?;
            ensure!(rules.len() == 1, "one canonical source per leaf required");
            let rule = &rules[0];
            ensure!(
                rule.input == p
                    && set(&rule.when.targets) == support.iter().copied().collect()
                    && set(&rule.when.all_features) == owners
                    && rule.when.any_features.is_empty()
                    && rule.when.none_features.is_empty(),
                "actual sparse rule changed"
            );
            let mut seen = BTreeSet::new();
            for w in &row.witnesses {
                let (kind, target) = w
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid witness"))?;
                let present = kind == "dev" && (f.4 || is_d2(target));
                ensure!(
                    seen.insert(w.context.as_str())
                        && contexts.get(&w.context) == Some(&w.commit)
                        && w.present == present
                        && w.git_blob.as_deref() == present.then_some(f.1)
                        && w.raw_bytes == if present { f.3 } else { 0 }
                        && w.raw_sha256.as_deref() == present.then_some(f.2),
                    "leaf membership changed"
                );
            }
            ensure!(
                seen == contexts.keys().map(String::as_str).collect(),
                "leaf witnesses incomplete"
            );
            let text = std::str::from_utf8(&self.sources[index])?;
            ensure!(!text.contains("{%"), "raw leaf gained a hidden body branch");
            for line in text.lines().filter(|line| line.starts_with("import ")) {
                ensure!(
                    line.starts_with("import java.")
                        || line == "import org.jetbrains.annotations.Nullable;",
                    "pure leaf gained host/native import"
                );
            }
        }
        Ok(())
    }
    fn selected(&self, context: &ProjectionContext) -> Result<BTreeSet<usize>> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let mut selected = BTreeSet::new();
        for (index, f) in FILES.iter().enumerate() {
            let p = path(f.0);
            let expected = (f.4 || is_d2(&selection.target_id))
                && owners(index).iter().all(|key| enabled(context, key));
            if let Some(input) = selection.inputs.get(&p) {
                ensure!(
                    expected && input.input == p && !selection.omitted_paths.contains(&p),
                    "leaf provenance escaped sparse owner"
                );
                selected.insert(index);
            } else {
                ensure!(
                    !expected && selection.omitted_paths.contains(&p),
                    "omitted leaf not explicit"
                );
            }
        }
        Ok(selected)
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<String> {
        ensure!(
            self.selected(context)?.contains(&index),
            "refuse omitted leaf render"
        );
        render_java_source(std::str::from_utf8(&self.sources[index])?, context)
    }
    fn full(&self, target: &str) -> Result<ProjectionContext> {
        let profile = self
            .ledger
            .full_source_profiles
            .iter()
            .find(|p| p.target == target)
            .ok_or_else(|| eyre::eyre!("source profile absent"))?;
        let context = self.shared.context(
            target,
            &profile
                .enabled
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>(),
        )?;
        let actual = self
            .selected(&context)?
            .iter()
            .map(|i| path(FILES[*i].0))
            .collect::<Vec<_>>();
        ensure!(
            actual == profile.selected_paths,
            "full source profile changed"
        );
        Ok(context)
    }
}
fn path(name: &str) -> String {
    format!("src/main/java/ca/teamdman/sfm/client/screen/workspace/{name}.java")
}
fn inventory() -> BTreeSet<String> {
    FILES.iter().map(|f| path(f.0)).collect()
}
fn set(values: &[String]) -> BTreeSet<&str> {
    values.iter().map(String::as_str).collect()
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn enabled(context: &ProjectionContext, key: &str) -> bool {
    context.features.get(key).copied().unwrap_or(false)
}
fn owners(index: usize) -> BTreeSet<&'static str> {
    match index {
        0 => BTreeSet::from(["workspace_panel_metadata"]),
        1 => BTreeSet::from(["workspace_dividers", "workspace_panels"]),
        2 => BTreeSet::from(["workspace_dividers"]),
        3 | 4 => BTreeSet::from(["workspace_panels"]),
        _ => BTreeSet::new(),
    }
}
fn valid_raw(bytes: &[u8], hash: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count
            && sha256(bytes) == hash
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "unreviewed raw source mutation"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

#[test]
fn exact_twenty_context_membership_and_twenty_six_real_rendered_bodies() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    let mut bodies = 0;
    for (name, _) in CONTEXTS {
        let (kind, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let context = if kind == "dev" {
            fixture.full(target)?
        } else {
            fixture.shared.context(target, &[])?
        };
        for (index, f) in FILES.iter().enumerate() {
            let expected = kind == "dev" && (f.4 || is_d2(target));
            assert_eq!(fixture.selected(&context)?.contains(&index), expected);
            if expected {
                assert_eq!(
                    fixture.render(index, &context)?.as_bytes(),
                    fixture.raw[f.1]
                );
                bodies += 1;
            } else {
                assert!(fixture.render(index, &context).is_err());
            }
            cells += 1;
        }
    }
    assert_eq!((cells, bodies), (100, 26));
    Ok(())
}

#[test]
fn independent_masks_keep_divider_carriers_separate_from_panel_runtime() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        for mask in 0..8 {
            let flags = [
                "workspace_panels",
                "workspace_dividers",
                "workspace_panel_metadata",
            ]
            .into_iter()
            .enumerate()
            .filter(|(i, _)| mask & (1 << i) != 0)
            .map(|(_, s)| s)
            .collect::<Vec<_>>();
            if !is_d2(target) && mask & 6 != 0 {
                assert!(fixture.shared.context(target, &flags).is_err());
                continue;
            }
            let context = fixture.shared.context(target, &flags)?;
            for (index, f) in FILES.iter().enumerate() {
                let expected =
                    (f.4 || is_d2(target)) && owners(index).iter().all(|key| flags.contains(key));
                assert_eq!(fixture.selected(&context)?.contains(&index), expected);
                if expected {
                    assert_eq!(
                        fixture.render(index, &context)?.as_bytes(),
                        fixture.raw[f.1]
                    );
                }
            }
        }
        if is_d2(target) {
            let carriers = fixture.shared.context(target, &["workspace_dividers"])?;
            assert_eq!(fixture.selected(&carriers)?, BTreeSet::from([2]));
            assert!(!enabled(&carriers, "workspace_panels"));
            let pointer = fixture
                .shared
                .context(target, &["workspace_dividers", "workspace_panels"])?;
            assert_eq!(fixture.selected(&pointer)?, BTreeSet::from([1, 2, 3, 4]));
            assert!(!enabled(&pointer, "workspace_panel_metadata"));
        }
    }
    Ok(())
}

#[test]
fn real_model_edges_are_preserved_without_fabricating_layout_or_panel_providers() -> Result<()> {
    let fixture = Fixture::load()?;
    let metadata = std::str::from_utf8(&fixture.sources[0])?;
    for anchor in [
        "@Nullable Integer guiScaleOverride,",
        "String provenance",
        "public static SFMWorkspacePanelMetadata ordinary()",
        "public static SFMWorkspacePanelMetadata explorerPreview(",
        "Base64.getUrlDecoder().decode",
        "public SFMWorkspacePanelMetadata withGuiScaleOverride(",
    ] {
        assert!(metadata.contains(anchor));
    }
    let pointer = std::str::from_utf8(&fixture.sources[1])?;
    for anchor in [
        "SFMWorkspaceLayout.DividerResizeSession",
        "layout.captureDividerResize(",
        "host.layout().updateDividerResize(",
        "host.layout().cancelDividerResize(",
        "public interface CursorSink extends AutoCloseable",
        "cursorSink.close();",
    ] {
        assert!(pointer.contains(anchor));
    }
    // The frozen documentation mentions GLFW cursor ownership; reject real
    // native/client API imports and calls, not that explanatory comment.
    assert!(
        !pointer.contains("import net.minecraft.")
            && !pointer.contains("import org.lwjgl.")
            && !pointer.contains("GLFW.")
    );
    let intent = std::str::from_utf8(&fixture.sources[2])?;
    for anchor in [
        "List<SFMWorkspaceDividerId> dividerIds,",
        "new LinkedHashSet<>(dividerIds)",
        "SFMWorkspaceDividerId.parse(value)",
    ] {
        assert!(intent.contains(anchor));
    }
    let group = std::str::from_utf8(&fixture.sources[3])?;
    assert!(group.contains(
        "BiFunction<SFMScreenPanelBounds, @Nullable SFMScreenPanel, SFMWorkspaceLayout.LayoutSpec>"
    ));
    assert!(group.contains("SFMWorkspaceLayout.panel(maximized)"));
    // Layout and SFMScreenPanel are genuine remaining source prerequisites.
    // This static contract audit deliberately never invents a fixture stub or
    // claims the host/runtime/complete Java dependency graph has compiled.
    let provider = fixture.shared.read_source(&path("SFMWorkspacePanelId"))?;
    assert_eq!(
        sha256(&provider),
        "sha256:c300406b1a1a52c4ea856d399f2f06c17564dbc52be863d6e5ec47e033880439"
    );
    let id = fixture.shared.read_source(&path("SFMWorkspaceDividerId"))?;
    assert_eq!(
        sha256(&id),
        "sha256:e1bfccdf915f338abe621a6a3dca63fb0b870fb4db1ad235083a4ffe5537dec4"
    );
    Ok(())
}

#[derive(Facet)]
struct AnnotationLock {
    schema_version: u32,
    artifacts: Vec<AnnotationArtifact>,
}
#[derive(Facet)]
struct AnnotationArtifact {
    coordinate: Option<String>,
    hash: String,
}
#[test]
fn nullable_compile_annotation_is_pinned_on_each_actual_target_without_acquisition() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let bytes = read_bounded(
            &fixture
                .shared
                .core
                .join(format!("build/lockfiles/{target}/schema-2.json")),
            2 * 1024 * 1024,
        )?;
        let lock: AnnotationLock = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        assert_eq!(lock.schema_version, 2);
        let rows = lock
            .artifacts
            .iter()
            .filter(|a| a.coordinate.as_deref() == Some("org.jetbrains:annotations:24.0.1"))
            .collect::<Vec<_>>();
        assert_eq!(rows.len(), 1);
        assert_eq!(
            rows[0].hash,
            "blake3:9a3d2fae94bf334d4dc72f71112c47755d37f533"
        );
    }
    Ok(())
}

#[test]
fn all_raw_leaves_reject_eol_bom_eof_and_token_mutations() -> Result<()> {
    let fixture = Fixture::load()?;
    for f in FILES {
        let bytes = &fixture.raw[f.1];
        assert!(valid_raw(&bytes[..bytes.len() - 1], f.2, f.3).is_err());
        assert!(
            valid_raw(
                &std::str::from_utf8(bytes)?
                    .replace('\n', "\r\n")
                    .into_bytes(),
                f.2,
                f.3
            )
            .is_err()
        );
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(bytes);
        assert!(valid_raw(&bom, f.2, f.3).is_err());
        let mut token = bytes.clone();
        token[0] ^= 1;
        assert!(valid_raw(&token, f.2, f.3).is_err());
    }
    Ok(())
}

#[test]
fn twenty_six_common_edits_use_one_source_without_changing_frozen_or_real_inputs() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let anchor = "package ca.teamdman.sfm.client.screen.workspace;\n";
    let replacement = format!("{anchor}\n// Shared workspace leaf proof.\n");
    for (index, f) in FILES.iter().enumerate() {
        let source = std::str::from_utf8(&fixture.sources[index])?;
        ensure!(source.matches(anchor).count() == 1, "common anchor changed");
        let p = temp.path().join(CORE_ROOT).join(path(f.0));
        fs::create_dir_all(
            p.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(p, source.replacen(anchor, &replacement, 1))?;
    }
    let mut count = 0;
    for target in TARGETS {
        let context = fixture.full(target)?;
        let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
        for index in fixture.selected(&context)? {
            let p = path(FILES[index].0);
            let bytes = read_bounded(
                &temp
                    .path()
                    .join(CORE_ROOT)
                    .join(&selection.inputs[&p].input),
                LIMIT,
            )?;
            let rendered = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            assert_eq!(
                rendered,
                fixture
                    .render(index, &context)?
                    .replacen(anchor, &replacement, 1)
            );
            assert_eq!(fixture.shared.read_source(&p)?, fixture.sources[index]);
            count += 1;
        }
    }
    assert_eq!(count, 26);
    Ok(())
}

#[test]
fn actual_collector_uses_fixed_core_and_java_templates_not_snapshot_dispatch() -> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture.shared.context("1.19.2", &["workspace_dividers"])?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let paths = inventory();
    let mut metadata = fixture.shared.metadata.clone();
    metadata.source_rules.retain(|p, _| paths.contains(p));
    for rules in metadata.source_rules.values_mut() {
        for rule in rules {
            rule.template = false;
        }
    }
    let selection = select_core_inputs(&metadata, &context, &paths)?;
    for (output, input) in &selection.inputs {
        let bytes = if let Some(index) = FILES.iter().position(|f| path(f.0) == *output) {
            fixture.sources[index].clone()
        } else {
            read_bounded(&fixture.shared.core.join(&input.input), 16 * 1024 * 1024)?
        };
        let p = root.join(&input.input);
        fs::create_dir_all(
            p.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(p, bytes)?;
    }
    let artifacts = collect_core_artifacts(&root, &selection, &context)?;
    assert_eq!(fixture.selected(&context)?, BTreeSet::from([2]));
    let p = path(FILES[2].0);
    let artifact = &artifacts[&p];
    assert_eq!(artifact.source_bytes, fixture.sources[2]);
    assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{p}"));
    assert!(artifact.overlay.is_none());
    let (_, body) = std::str::from_utf8(&artifact.output_bytes)?
        .split_once('\n')
        .ok_or_else(|| eyre::eyre!("banner absent"))?;
    assert_eq!(body, fixture.render(2, &context)?);
    let source = std::str::from_utf8(&fixture.sources[2])?;
    fs::write(
        root.join(&p),
        format!(
            "{source}{{% if features.workspace_dividers %}}\n// automatic Java template\n{{% endif %}}\n"
        ),
    )?;
    let artifacts = collect_core_artifacts(&root, &selection, &context)?;
    assert!(
        std::str::from_utf8(&artifacts[&p].output_bytes)?.ends_with("// automatic Java template\n")
    );
    fs::write(
        root.join(&p),
        format!("{{% case environment %}}\n{{% when \"release\" %}}\n{source}{{% endcase %}}\n"),
    )?;
    assert!(collect_core_artifacts(&root, &selection, &context).is_err());
    assert!(collect_core_artifacts(&temp.path().join("wrong-core"), &selection, &context).is_err());
    Ok(())
}

#[test]
fn unknown_unsupported_and_malformed_source_requests_fail_closed() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        assert!(
            fixture
                .shared
                .context(target, &["unknown_workspace_leaf_owner"])
                .is_err()
        );
        if !is_d2(target) {
            for flag in ["workspace_dividers", "workspace_panel_metadata"] {
                assert!(fixture.shared.context(target, &[flag]).is_err());
            }
        }
    }
    let context = fixture.shared.context("1.19.2", &["workspace_dividers"])?;
    let source = std::str::from_utf8(&fixture.sources[2])?;
    for changed in [
        format!("{{% else %}}\n{source}"),
        format!("{{% if features.unknown_workspace_leaf_owner %}}\n{source}{{% endif %}}\n"),
        format!("{{% if features.workspace_dividers %}}\n{source}{{% endcase %}}\n"),
    ] {
        assert!(render_java_source(&changed, &context).is_err());
    }
    Ok(())
}

#[test]
fn twenty_bounded_offline_trees_reconfirm_all_one_hundred_membership_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    for (context, commit) in CONTEXTS {
        let (kind, target) = context
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
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
        command.args(
            FILES
                .iter()
                .map(|f| format!("platform/minecraft/{}", path(f.0))),
        );
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("tree output absent"))?;
        let mut bytes = Vec::new();
        let read = reader.by_ref().take(8193).read_to_end(&mut bytes);
        drop(reader);
        if read.is_err() || bytes.len() > 8192 {
            let _ = child.kill();
            let _ = child.wait();
            read?;
            eyre::bail!("tree output too large");
        }
        ensure!(child.wait()?.success(), "offline tree read failed");
        let mut expected = BTreeMap::new();
        for f in FILES {
            if kind == "dev" && (f.4 || is_d2(target)) {
                let p = path(f.0);
                expected.insert(
                    p.clone(),
                    format!("100644 blob {}\tplatform/minecraft/{p}\0", f.1),
                );
                present += 1;
            }
        }
        assert_eq!(bytes, expected.into_values().collect::<String>().as_bytes());
    }
    assert_eq!(present, 26);
    Ok(())
}
