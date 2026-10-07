//! Real selector/Liquid proofs for the reviewed shared workspace Layout.
//!
//! Frozen blobs and migration regions are test evidence only. Every source
//! profile is dependency-closed explicitly; public presets are not modified.
//! These tests do not compile Java or supply a fabricated SFMScreenPanel.
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

const PATH: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspaceLayout.java";
const LEDGER: &str = "docs/tasks/sfm-core-workspace-layout-slice.json";
const LEDGER_SHA: &str = "sha256:b38db87efbcc10b8790790287ff1512719ec6bcb18956010ea886698ddcaccd3";
const TEMPLATE_SHA: &str =
    "sha256:e19b55e319b08104a3a5b3c68ff5dd80fa45f13fa30f9cd96733502cdf6a86d0";
const TEMPLATE_BYTES: u64 = 100485;
const OLD: (&str, &str, u64) = (
    "66f0416021e11b95d5635518d5cf58e025aec90a",
    "sha256:daf2fc3c32d50828ea19e320f55be411d24ce81837cd97feb10b56d5aad2dcef",
    23509,
);
const FULL: (&str, &str, u64) = (
    "69c1c61ff9a78fe9bb956e3c13630b2ce6d0e8dc",
    "sha256:29289bf06e5deb3863ea6274db80f344ac35684692c59ca7d12fa4f3ea7e3284",
    89056,
);
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const OPTIONAL: [&str; 6] = [
    "workspace_panel_metadata",
    "workspace_stack_controls",
    "workspace_directional_resize",
    "workspace_dividers",
    "workspace_layout_focus_fix",
    "workspace_layout_rounding_fix",
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
    file: File,
    raw_variants: Vec<Raw>,
    prerequisite_definitions: BTreeMap<String, Definition>,
    full_source_profiles: Vec<Profile>,
    independent_source_profiles: Vec<Independent>,
    frozen_region_review: RegionReview,
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
    supported_independent_masks: usize,
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
    git_blob: String,
    sha256: String,
    bytes: u64,
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
    enabled: Vec<String>,
    raw_git_blob: String,
    rendered_bytes: u64,
    rendered_sha256: String,
}
#[derive(Facet)]
struct Independent {
    mask: u8,
    enabled: Vec<String>,
    bytes: u64,
    sha256: String,
    unresolved_known_method_names: Vec<String>,
}
#[derive(Facet)]
struct RegionReview {
    migration_only: bool,
    legacy_git_blob: String,
    d2_git_blob: String,
    legacy_lines: usize,
    d2_lines: usize,
    shared_lines: usize,
    changed_regions: usize,
    regions: Vec<Region>,
}
#[derive(Facet)]
struct Region {
    region: usize,
    legacy_lines: [usize; 2],
    d2_lines: [usize; 2],
    legacy_line_count: usize,
    d2_line_count: usize,
    legacy_sha256: String,
    d2_sha256: String,
    fingerprint_sha256: String,
}

struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    source: Vec<u8>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), 256 * 1024)?;
        ensure!(
            sha256(&bytes) == LEDGER_SHA,
            "reviewed Layout ledger changed"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        let source = shared.read_source(PATH)?;
        let raw = read_git_blobs(
            &shared.repository,
            &BTreeSet::from([OLD.0.to_owned(), FULL.0.to_owned()]),
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
        let s = &self.ledger.scope;
        ensure!(
            self.ledger.schema == "sfm:core-workspace-layout-slice@1"
                && s.production_files == 1
                && s.context_cells == 20
                && s.present_cells == 10
                && s.absent_cells == 10
                && s.raw_variants == 2
                && s.stage_bytes == TEMPLATE_BYTES
                && s.new_source_flags == 5
                && s.supported_independent_masks == 48
                && s.dependencies_changed == 0
                && !s.java_compilation
                && !s.live_runtime
                && !s.complete_java_closure,
            "Layout source-only scope changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.raw_byte_exact
                && self
                    .ledger
                    .normalization
                    .authorized_transformations
                    .is_empty(),
            "Layout normalization forbidden"
        );
        ensure!(
            self.source.len() as u64 == TEMPLATE_BYTES
                && sha256(&self.source) == TEMPLATE_SHA
                && !self.source.contains(&b'\r')
                && self.source.ends_with(b"\n"),
            "authored Layout template changed"
        );
        let contexts = CONTEXTS
            .into_iter()
            .map(|(k, v)| (k.to_owned(), v.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            contexts == self.ledger.context_commits,
            "frozen context identities changed"
        );
        let file = &self.ledger.file;
        ensure!(
            file.name == "SFMWorkspaceLayout"
                && file.intended_core_path == PATH
                && file.stage_bytes == TEMPLATE_BYTES
                && file.stage_sha256 == TEMPLATE_SHA
                && file.class_support == TARGETS
                && file.source_rule.input == PATH
                && file.source_rule.template
                && file.source_rule.when.targets == TARGETS
                && file.source_rule.when.all_features == ["workspace_panels"]
                && file.source_rule.when.any_features.is_empty()
                && file.witnesses.len() == 20,
            "Layout canonical source contract changed"
        );
        let rules = self
            .shared
            .metadata
            .source_rules
            .get(PATH)
            .ok_or_else(|| eyre::eyre!("Layout sparse rule absent"))?;
        ensure!(
            rules.len() == 1
                && rules[0].input == PATH
                && rules[0].when.targets == TARGETS
                && rules[0].when.all_features == ["workspace_panels"]
                && rules[0].when.any_features.is_empty()
                && rules[0].when.none_features.is_empty(),
            "actual Layout sparse membership changed"
        );
        ensure!(
            self.ledger.prerequisite_definitions.len() == 7
                && self.ledger.raw_variants.len() == 2
                && self.ledger.full_source_profiles.len() == 10
                && self.ledger.independent_source_profiles.len() == 48,
            "Layout evidence coverage changed"
        );
        for (key, d) in &self.ledger.prerequisite_definitions {
            let actual = self
                .shared
                .features
                .0
                .get(key)
                .ok_or_else(|| eyre::eyre!("Layout feature not registered: {key}"))?;
            let targets = if key == "workspace_panels" {
                TARGETS.to_vec()
            } else {
                TARGETS[..2].to_vec()
            };
            let dependencies = match key.as_str() {
                "workspace_stack_controls" => vec!["workspace_panels", "workspace_panel_metadata"],
                "workspace_directional_resize"
                | "workspace_layout_focus_fix"
                | "workspace_layout_rounding_fix" => vec!["workspace_panels"],
                _ => vec![],
            };
            ensure!(
                d.supported_targets == targets
                    && d.requires == dependencies
                    && actual.supported_targets == d.supported_targets
                    && actual.requires == d.requires,
                "Layout owner graph changed"
            );
        }
        for (index, fact) in [OLD, FULL].into_iter().enumerate() {
            exact_raw(&self.raw[fact.0], fact.1, fact.2)?;
            let row = &self.ledger.raw_variants[index];
            ensure!(
                row.git_blob == fact.0
                    && row.sha256 == fact.1
                    && row.bytes == fact.2
                    && row.crlf_count == 0
                    && row.lone_cr_count == 0
                    && row.final_lf
                    && !row.bom,
                "raw Layout witness changed"
            );
        }
        let mut seen = BTreeSet::new();
        for witness in &file.witnesses {
            let (kind, target) = witness
                .context
                .split_once('/')
                .ok_or_else(|| eyre::eyre!("invalid witness"))?;
            let fact = if is_d2(target) { FULL } else { OLD };
            let present = kind == "dev";
            ensure!(
                seen.insert(witness.context.as_str())
                    && contexts.get(&witness.context) == Some(&witness.commit)
                    && witness.present == present
                    && witness.git_blob.as_deref() == present.then_some(fact.0)
                    && witness.raw_bytes == if present { fact.2 } else { 0 }
                    && witness.raw_sha256.as_deref() == present.then_some(fact.1),
                "Layout frozen membership changed"
            );
        }
        ensure!(
            seen == contexts.keys().map(String::as_str).collect(),
            "Layout witness contexts incomplete"
        );
        Ok(())
    }
    fn selected(&self, context: &ProjectionContext) -> Result<bool> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let expected = enabled(context, "workspace_panels");
        if let Some(input) = selection.inputs.get(PATH) {
            ensure!(
                expected && input.input == PATH && !selection.omitted_paths.contains(PATH),
                "Layout provenance escaped canonical owner"
            );
            Ok(true)
        } else {
            ensure!(
                !expected && selection.omitted_paths.contains(PATH),
                "Layout absence not explicit"
            );
            Ok(false)
        }
    }
    fn render(&self, context: &ProjectionContext) -> Result<String> {
        ensure!(self.selected(context)?, "refuse omitted Layout rendering");
        render_java_source(std::str::from_utf8(&self.source)?, context)
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
        let fact = if is_d2(target) { FULL } else { OLD };
        ensure!(
            profile.raw_git_blob == fact.0
                && profile.rendered_sha256 == fact.1
                && profile.rendered_bytes == fact.2,
            "full raw profile changed"
        );
        Ok(context)
    }
}
fn inventory() -> BTreeSet<String> {
    BTreeSet::from([PATH.to_owned()])
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn enabled(context: &ProjectionContext, key: &str) -> bool {
    context.features.get(key).copied().unwrap_or(false)
}
fn exact_raw(bytes: &[u8], digest: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count
            && sha256(bytes) == digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "unreviewed raw Layout mutation"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

#[test]
fn frozen_twenty_cells_and_ten_exact_bodies_use_real_selection_and_renderer() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    for (name, _) in CONTEXTS {
        let (kind, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let context = if kind == "dev" {
            fixture.full(target)?
        } else {
            fixture.shared.context(target, &[])?
        };
        assert_eq!(fixture.selected(&context)?, kind == "dev");
        if kind == "dev" {
            let fact = if is_d2(target) { FULL } else { OLD };
            assert_eq!(fixture.render(&context)?.as_bytes(), fixture.raw[fact.0]);
            present += 1;
        } else {
            assert!(fixture.render(&context).is_err());
        }
    }
    assert_eq!(present, 10);
    for target in TARGETS {
        let off = fixture.shared.context(target, &["workspace_panels"])?;
        assert_eq!(fixture.render(&off)?.as_bytes(), fixture.raw[OLD.0]);
    }
    Ok(())
}

#[test]
fn ninety_six_dependency_closed_masks_match_independent_reviewed_source_hashes() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut seen = BTreeSet::new();
    for profile in &fixture.ledger.independent_source_profiles {
        assert!(profile.unresolved_known_method_names.is_empty());
        assert!(seen.insert(profile.mask));
        let mask_flags = OPTIONAL
            .into_iter()
            .enumerate()
            .filter(|(i, _)| profile.mask & (1 << i) != 0)
            .map(|(_, f)| f)
            .collect::<Vec<_>>();
        assert!(
            !mask_flags.contains(&"workspace_stack_controls")
                || mask_flags.contains(&"workspace_panel_metadata")
        );
        let mut expected = vec!["workspace_panels"];
        expected.extend(mask_flags);
        assert_eq!(profile.enabled, expected);
        for target in &TARGETS[..2] {
            let context = fixture.shared.context(target, &expected)?;
            let output = fixture.render(&context)?;
            assert_eq!(output.len() as u64, profile.bytes);
            assert_eq!(sha256(output.as_bytes()), profile.sha256);
            assert_member_contracts(&output, &context);
        }
    }
    assert_eq!(seen.len(), 48);
    for target in &TARGETS[..2] {
        assert!(
            fixture
                .shared
                .context(target, &["workspace_panels", "workspace_stack_controls"])
                .is_err()
        );
        assert!(
            fixture
                .shared
                .context(
                    target,
                    &["workspace_stack_controls", "workspace_panel_metadata"]
                )
                .is_err()
        );
    }
    Ok(())
}

fn assert_member_contracts(source: &str, context: &ProjectionContext) {
    let meta = enabled(context, "workspace_panel_metadata");
    let stack = enabled(context, "workspace_stack_controls");
    let dir = enabled(context, "workspace_directional_resize");
    let div = enabled(context, "workspace_dividers");
    let focus = enabled(context, "workspace_layout_focus_fix");
    let round = enabled(context, "workspace_layout_rounding_fix");
    assert_eq!(source.contains("SFMWorkspacePanelMetadata"), meta);
    assert_eq!(source.contains("public boolean move("), stack);
    assert_eq!(source.contains("public boolean canResize("), dir);
    assert_eq!(
        source.contains("public List<SFMWorkspaceDivider> dividers("),
        div
    );
    assert_eq!(source.contains("Math.nextUp("), round);
    assert_eq!(source.contains("int removedVisibleIndex = indexOf("), focus);
    assert_eq!(
        source.contains("private long mutationRevision;"),
        meta || stack || dir || div
    );
    assert_eq!(
        source.contains("public List<PanelEntry> visiblePanels()"),
        meta || stack || dir || div || focus
    );
    assert_eq!(
        source.contains("private static List<SFMWorkspacePanelId> panelIds("),
        stack || div
    );
    assert_eq!(
        source.contains("private static void collectPanelIds("),
        stack || div
    );
    assert_eq!(
        source.contains("private static boolean contains("),
        stack || dir || div
    );
    assert_eq!(
        source.contains("private static DividerApplication applyLinearDividerDelta("),
        dir || div
    );
    assert_eq!(
        source.contains("Track share must be finite and non-negative"),
        dir || div
    );
    // configurePanel always requires a positive requested share; only the
    // internal Track constructor accepts zero after directional resizing.
    let track_constructor = source
        .split_once("private record Track(")
        .expect("real internal Track constructor")
        .1;
    assert_eq!(
        track_constructor.contains("Track share must be finite and positive"),
        !dir && !div
    );
    assert_eq!(
        source.contains("import org.jetbrains.annotations.Nullable;"),
        meta || stack || div
    );
    assert!(
        source.contains(
            "public sealed interface LayoutSpec permits PanelSpec, LinearSpec, StackSpec"
        )
    );
    assert!(source.contains("public static LayoutSpec stack(int active, LayoutSpec... children)"));
    if !div {
        assert!(!source.contains("pruneDividerLinks"));
        assert!(!source.contains("SFMWorkspaceDivider"));
    }
    if !stack && !div {
        assert!(!source.contains("SFMWorkspaceStackId"));
    }
    if !meta {
        assert!(
            source
                .contains("public record PanelEntry(SFMWorkspacePanelId id, SFMScreenPanel panel)")
        );
    }
}

#[test]
fn model_source_membership_does_not_force_panels_for_pure_metadata_or_ids() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        for pure in ["workspace_panel_metadata", "workspace_dividers"] {
            let context = fixture.shared.context(target, &[pure])?;
            assert!(!fixture.selected(&context)?);
            assert!(fixture.render(&context).is_err());
        }
    }
    for target in &TARGETS[2..] {
        for owner in OPTIONAL {
            assert!(
                fixture
                    .shared
                    .context(target, &["workspace_panels", owner])
                    .is_err()
            );
        }
    }
    Ok(())
}

fn hunk(lines: &[&str], bounds: [usize; 2], count: usize) -> Result<String> {
    ensure!(bounds[0] > 0, "hunk line numbers are one-based");
    let start = bounds[0] - 1;
    let end = bounds[1];
    ensure!(
        start <= end && end <= lines.len() && end - start == count,
        "raw hunk bounds/count changed"
    );
    Ok(lines[start..end]
        .iter()
        .map(|line| format!("{line}\n"))
        .collect())
}
#[test]
fn forty_eight_migration_only_region_fingerprints_bind_exact_raw_hunks() -> Result<()> {
    let fixture = Fixture::load()?;
    let review = &fixture.ledger.frozen_region_review;
    assert!(review.migration_only);
    assert_eq!(review.legacy_git_blob, OLD.0);
    assert_eq!(review.d2_git_blob, FULL.0);
    assert_eq!(
        (
            review.legacy_lines,
            review.d2_lines,
            review.shared_lines,
            review.changed_regions
        ),
        (531, 2014, 496, 48)
    );
    let old = std::str::from_utf8(&fixture.raw[OLD.0])?
        .lines()
        .collect::<Vec<_>>();
    let full = std::str::from_utf8(&fixture.raw[FULL.0])?
        .lines()
        .collect::<Vec<_>>();
    assert_eq!((old.len(), full.len()), (531, 2014));
    assert_eq!(review.regions.len(), 48);
    for (i, row) in review.regions.iter().enumerate() {
        assert_eq!(row.region, i + 1);
        assert_eq!(
            sha256(hunk(&old, row.legacy_lines, row.legacy_line_count)?.as_bytes()),
            row.legacy_sha256
        );
        assert_eq!(
            sha256(hunk(&full, row.d2_lines, row.d2_line_count)?.as_bytes()),
            row.d2_sha256
        );
        let encoded = format!(
            "{{\"legacy\":[{}, {}, \"{}\"],\"d2\":[{}, {}, \"{}\"]}}",
            row.legacy_lines[0],
            row.legacy_lines[1],
            row.legacy_sha256,
            row.d2_lines[0],
            row.d2_lines[1],
            row.d2_sha256
        )
        .replace(", ", ",");
        assert_eq!(sha256(encoded.as_bytes()), row.fingerprint_sha256);
    }
    Ok(())
}

#[test]
fn common_edit_propagates_across_ten_targets_without_mutating_core_or_goldens() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let source = std::str::from_utf8(&fixture.source)?;
    let anchor = "package ca.teamdman.sfm.client.screen.workspace;\n";
    let replacement = format!("{anchor}\n// Common Layout edit proof.\n");
    ensure!(
        source.matches(anchor).count() == 1,
        "common package anchor changed"
    );
    let root = temp.path().join(CORE_ROOT);
    let p = root.join(PATH);
    fs::create_dir_all(
        p.parent()
            .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
    )?;
    fs::write(&p, source.replacen(anchor, &replacement, 1))?;
    let changed = read_bounded(&p, 256 * 1024)?;
    for target in TARGETS {
        let context = fixture.full(target)?;
        let selected = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
        assert_eq!(selected.inputs[PATH].input, PATH);
        assert_eq!(
            render_java_source(std::str::from_utf8(&changed)?, &context)?,
            fixture.render(&context)?.replacen(anchor, &replacement, 1)
        );
    }
    assert_eq!(fixture.shared.read_source(PATH)?, fixture.source);
    Ok(())
}

#[test]
fn collector_keeps_fixed_core_automatic_java_and_refuses_snapshot_routing() -> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture.full("1.19.2")?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let mut metadata = fixture.shared.metadata.clone();
    metadata.source_rules.retain(|key, _| key == PATH);
    for rules in metadata.source_rules.values_mut() {
        for rule in rules {
            rule.template = false;
        }
    }
    let selection = select_core_inputs(&metadata, &context, &inventory())?;
    for (output, input) in &selection.inputs {
        let bytes = if output == PATH {
            fixture.source.clone()
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
    let artifact = &artifacts[PATH];
    assert_eq!(artifact.source_bytes, fixture.source);
    assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{PATH}"));
    assert!(artifact.overlay.is_none());
    let (_, body) = std::str::from_utf8(&artifact.output_bytes)?
        .split_once('\n')
        .ok_or_else(|| eyre::eyre!("generated banner absent"))?;
    assert_eq!(body, fixture.render(&context)?);
    let source = std::str::from_utf8(&fixture.source)?;
    fs::write(
        root.join(PATH),
        format!("{{% case environment %}}\n{{% when \"dev\" %}}\n{source}{{% endcase %}}\n"),
    )?;
    assert!(collect_core_artifacts(&root, &selection, &context).is_err());
    assert!(collect_core_artifacts(&temp.path().join("other-core"), &selection, &context).is_err());
    assert_eq!(fixture.shared.read_source(PATH)?, fixture.source);
    Ok(())
}

#[test]
fn raw_identity_and_invalid_feature_directives_fail_closed() -> Result<()> {
    let fixture = Fixture::load()?;
    for fact in [OLD, FULL] {
        let bytes = &fixture.raw[fact.0];
        assert!(exact_raw(&bytes[..bytes.len() - 1], fact.1, fact.2).is_err());
        assert!(
            exact_raw(
                &std::str::from_utf8(bytes)?
                    .replace('\n', "\r\n")
                    .into_bytes(),
                fact.1,
                fact.2
            )
            .is_err()
        );
        let mut token = bytes.clone();
        token[0] ^= 1;
        assert!(exact_raw(&token, fact.1, fact.2).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(bytes);
        assert!(exact_raw(&bom, fact.1, fact.2).is_err());
    }
    let context = fixture.shared.context("1.19.2", &["workspace_panels"])?;
    assert!(
        fixture
            .shared
            .context("1.19.2", &["unknown_layout_owner"])
            .is_err()
    );
    let source = std::str::from_utf8(&fixture.source)?;
    for bad in [
        format!("{{% else %}}\n{source}"),
        format!("{{% if features.unknown_layout_owner %}}\n{source}{{% endif %}}\n"),
        format!("{{% if features.workspace_panels %}}\n{source}{{% endcase %}}\n"),
    ] {
        assert!(render_java_source(&bad, &context).is_err());
    }
    Ok(())
}

#[test]
fn model_type_seams_preserve_real_panel_requirement_and_no_native_host_effects() -> Result<()> {
    let fixture = Fixture::load()?;
    let full = fixture.render(&fixture.full("1.19.2")?)?;
    assert!(full.contains("IdentityHashMap<SFMScreenPanel, SFMWorkspacePanelId>"));
    assert!(full.contains("public static SFMWorkspaceLayout single(SFMScreenPanel panel)"));
    assert!(full.contains("public static final class DividerResizeSession"));
    assert!(!full.contains("GLFW") && !full.contains("Minecraft") && !full.contains("glfwCreate"));
    // The real panel interface/hosts remain separately authored prerequisites.
    // No Java source is injected to pretend those are compiled or runnable.
    Ok(())
}
