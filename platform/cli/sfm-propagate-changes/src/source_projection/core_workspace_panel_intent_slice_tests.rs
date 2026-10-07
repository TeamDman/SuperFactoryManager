//! Source-only proofs for real workspace intent/dispatcher/recipe inputs.
//!
//! Frozen source witnesses are evidence, never generation inputs. This module
//! supplies no surrogate Multiplexer, host or panel implementation and makes no
//! Java compilation, runtime registration or action-discovery claim.
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

const INTENT: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspacePanelIntent.java";
const DISPATCHER: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspacePanelIntentDispatcher.java";
const RECIPE: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPanelReopenRecipe.java";
const REOPEN_CONTEXT: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPanelReopenContext.java";
const PATHS: [&str; 4] = [INTENT, DISPATCHER, RECIPE, REOPEN_CONTEXT];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const PANELS: &str = "workspace_panels";
const METADATA: &str = "workspace_panel_metadata";
const STACK: &str = "workspace_stack_controls";
const REOPEN: &str = "workspace_panel_reopening";
const OWNERS: [&str; 4] = [PANELS, METADATA, STACK, REOPEN];
const LEDGER: &str = "docs/tasks/sfm-core-workspace-panel-intent-slice-v2.json";
const LEDGER_SHA: &str = "sha256:784b1a7df131545e9e59e4cf8eda746c47bd9ecbb02c644756155e0506955d7d";
const RAW: [(&str, &str, u64); 6] = [
    (
        "a6a3740eb8157237167edcbe352f4184b96fabe1",
        "sha256:bcd06e918228ce3cc84fb6e2182910d28d48d975231009c309a1073fb1e0ca46",
        2158,
    ),
    (
        "de0eafaead774c39fe3b348342fed4f7810b2866",
        "sha256:8f5c899aef730cb29313e06e4773434028b29c241b44339c94a9b4eaddc60174",
        681,
    ),
    (
        "1d34519efe4b896525ba39769d9632f3d4c70048",
        "sha256:b72101de996081e4a623fb23105117f8470db34db51b4d4f3f4508d77048348b",
        2647,
    ),
    (
        "a24dedb76d941476f3691d6c0bdf678695513c6e",
        "sha256:7c7503713b0243d7e90e70ab667dd3ad019081af3ad368b10afd2d1420041c25",
        1781,
    ),
    (
        "43954428af236ff77c7be9fb88fb0f4851455edf",
        "sha256:c7a03242824ac56d58af090ca28d553e86e5dfac29eae8fde76f8f6b652acd65",
        487,
    ),
    (
        "5218c2fb0864acef1522cc2047e0c59dea70814e",
        "sha256:e208de3ca5954f440753139fd284808e946970d982a6a32388c3f2e822f2fe7d",
        417,
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
struct Independent {
    mask: u8,
    name: String,
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
            "immutable intent ledger changed"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        let source = PATHS
            .into_iter()
            .map(|path| Ok((path.to_owned(), shared.read_source(path)?)))
            .collect::<Result<BTreeMap<_, _>>>()?;
        let raw = read_git_blobs(
            &shared.repository,
            &RAW.into_iter().map(|row| row.0.to_owned()).collect(),
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
        let scope = &self.ledger.scope;
        ensure!(
            self.ledger.schema == "sfm:core-workspace-panel-intent-slice@2"
                && scope.production_files == 4
                && scope.context_cells == 80
                && scope.present_cells == 24
                && scope.absent_cells == 56
                && scope.raw_variants == 6
                && scope.stage_bytes == 8774
                && scope.new_source_flags == 1
                && scope.independent_masks == 6
                && scope.independent_goldens == 18
                && scope.dependencies_changed == 0
                && !scope.java_compilation
                && !scope.live_runtime
                && !scope.complete_java_closure,
            "intent source-only scope changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.raw_byte_exact
                && self
                    .ledger
                    .normalization
                    .authorized_transformations
                    .is_empty(),
            "this cohort has no normalization authority"
        );
        let contexts = CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == contexts
                && self.ledger.files.len() == 4
                && self.ledger.raw_variants.len() == 6
                && self.ledger.prerequisite_definitions.len() == 4
                && self.ledger.independent_source_profiles.len() == 18,
            "frozen scope or source evidence changed"
        );
        for owner in OWNERS {
            let reviewed = self
                .ledger
                .prerequisite_definitions
                .get(owner)
                .ok_or_else(|| eyre::eyre!("reviewed owner absent: {owner}"))?;
            let registered = self
                .shared
                .features
                .0
                .get(owner)
                .ok_or_else(|| eyre::eyre!("unregistered intent owner: {owner}"))?;
            let targets = if owner == PANELS {
                &TARGETS[..]
            } else {
                &TARGETS[..2]
            };
            let requires: &[&str] = match owner {
                STACK => &[PANELS, METADATA],
                REOPEN => &[PANELS],
                _ => &[],
            };
            ensure!(
                reviewed
                    .supported_targets
                    .iter()
                    .map(String::as_str)
                    .collect::<Vec<_>>()
                    == targets
                    && reviewed
                        .requires
                        .iter()
                        .map(String::as_str)
                        .collect::<Vec<_>>()
                        == requires
                    && registered.supported_targets == reviewed.supported_targets
                    && registered.requires == reviewed.requires,
                "current reviewed owner graph changed: {owner}"
            );
        }
        for (index, (oid, digest, count)) in RAW.into_iter().enumerate() {
            exact_raw(&self.raw[oid], digest, count)?;
            let fact = &self.ledger.raw_variants[index];
            ensure!(
                fact.git_blob == oid
                    && fact.sha256 == digest
                    && fact.bytes == count
                    && fact.crlf_count == 0
                    && fact.lone_cr_count == 0
                    && fact.final_lf
                    && !fact.bom,
                "raw intent witness identity changed"
            );
        }
        let mut seen_paths = BTreeSet::new();
        let mut present = 0;
        for file in &self.ledger.files {
            let path = path_for_name(&file.name)?;
            let (targets, owner) = if is_recipe(path) {
                (&TARGETS[..2], REOPEN)
            } else {
                (&TARGETS[..], PANELS)
            };
            let bytes = &self.source[path];
            let rule = &file.source_rule;
            ensure!(
                seen_paths.insert(path)
                    && file.intended_core_path == path
                    && bytes.len() as u64 == file.stage_bytes
                    && sha256(bytes) == file.stage_sha256
                    && bytes.is_ascii()
                    && !bytes.contains(&b'\r')
                    && bytes.ends_with(b"\n")
                    && rule.input == path
                    && rule.template
                    && rule
                        .when
                        .targets
                        .iter()
                        .map(String::as_str)
                        .collect::<Vec<_>>()
                        == targets
                    && rule.when.all_features == [owner]
                    && rule.when.any_features.is_empty()
                    && rule.when.none_features.is_empty(),
                "reviewed core input or membership changed: {path}"
            );
            let rules = self
                .shared
                .metadata
                .source_rules
                .get(path)
                .ok_or_else(|| eyre::eyre!("explicit intent source rule absent: {path}"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == path
                    && rules[0].template
                    && rules[0].when.targets == rule.when.targets
                    && rules[0].when.all_features == rule.when.all_features
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "actual intent membership rule differs: {path}"
            );
            ensure!(file.witnesses.len() == 20, "twenty witness cells missing");
            let mut seen = BTreeSet::new();
            for witness in &file.witnesses {
                let (kind, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid intent witness context"))?;
                ensure!(
                    matches!(kind, "dev" | "release") && TARGETS.contains(&target),
                    "unsupported frozen intent witness"
                );
                let oid = expected_oid(path, target, kind == "dev");
                let raw = oid.map(|id| &self.raw[id]);
                ensure!(
                    seen.insert(witness.context.as_str())
                        && contexts.get(&witness.context) == Some(&witness.commit)
                        && witness.present == oid.is_some()
                        && witness.git_blob.as_deref() == oid
                        && witness.raw_bytes == raw.map_or(0, |bytes| bytes.len() as u64)
                        && witness.raw_sha256 == raw.map(|bytes| sha256(bytes)),
                    "immutable source membership or bytes changed: {path}"
                );
                present += usize::from(witness.present);
            }
        }
        ensure!(
            seen_paths.len() == 4 && present == 24,
            "scoped membership totals changed"
        );
        Ok(())
    }

    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let expected = feature(context, if is_recipe(path) { REOPEN } else { PANELS });
        let Some(input) = selection.inputs.get(path) else {
            ensure!(
                !expected && selection.omitted_paths.contains(path),
                "source unexpectedly absent or lacks explicit omission: {path}"
            );
            return Ok(None);
        };
        ensure!(
            expected && input.input == path && input.template,
            "historical routing or wrong membership selected: {path}"
        );
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.source[path])?,
            context,
        )?))
    }

    fn full(&self, target: &str) -> Result<ProjectionContext> {
        let enabled: &[&str] = if is_d2(target) { &OWNERS } else { &[PANELS] };
        self.shared.context(target, enabled)
    }
}
fn inventory() -> BTreeSet<String> {
    PATHS.into_iter().map(str::to_owned).collect()
}
fn is_recipe(path: &str) -> bool {
    matches!(path, RECIPE | REOPEN_CONTEXT)
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn feature(context: &ProjectionContext, owner: &str) -> bool {
    context.features.get(owner) == Some(&true)
}
fn path_for_name(name: &str) -> Result<&'static str> {
    PATHS
        .into_iter()
        .find(|path| path.ends_with(&format!("/{name}.java")))
        .ok_or_else(|| eyre::eyre!("source path outside bounded intent cohort"))
}
fn expected_oid(path: &str, target: &str, dev: bool) -> Option<&'static str> {
    if !dev {
        return None;
    }
    match (path, is_d2(target)) {
        (INTENT, true) => Some(RAW[0].0),
        (INTENT, false) => Some(RAW[1].0),
        (DISPATCHER, true) => Some(RAW[2].0),
        (DISPATCHER, false) => Some(RAW[3].0),
        (RECIPE, true) => Some(RAW[4].0),
        (REOPEN_CONTEXT, true) => Some(RAW[5].0),
        _ => None,
    }
}
fn exact_raw(bytes: &[u8], digest: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count
            && sha256(bytes) == digest
            && bytes.is_ascii()
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw witness changed or unapproved normalization"
    );
    Ok(())
}
fn enabled_mask(mask: u8) -> Vec<&'static str> {
    let mut enabled = vec![PANELS];
    for (bit, owner) in [METADATA, STACK, REOPEN].into_iter().enumerate() {
        if mask & (1 << bit) != 0 {
            enabled.push(owner);
        }
    }
    enabled
}

#[test]
fn eighty_membership_cells_reconstruct_twenty_actual_source_contexts() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXTS {
        let (kind, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("fixed context"))?;
        let context = if kind == "dev" {
            fixture.full(target)?
        } else {
            fixture.shared.context(target, &[])?
        };
        for path in PATHS {
            let rendered = fixture.render(path, &context)?;
            if let Some(oid) = expected_oid(path, target, kind == "dev") {
                assert_eq!(
                    rendered.as_deref().map(str::as_bytes),
                    Some(fixture.raw[oid].as_slice())
                );
                present += 1;
            } else {
                assert!(rendered.is_none());
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (24, 56));
    Ok(())
}

#[test]
fn thirty_six_supported_mixed_owner_outputs_match_independent_source_goldens() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut seen = BTreeSet::new();
    let mut outputs = 0;
    for profile in &fixture.ledger.independent_source_profiles {
        let path = path_for_name(&profile.name)?;
        ensure!(
            [0, 1, 3, 4, 5, 7].contains(&profile.mask) && seen.insert((profile.mask, path)),
            "unexpected independent profile"
        );
        let enabled = enabled_mask(profile.mask);
        assert_eq!(
            profile
                .enabled
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>(),
            enabled
        );
        for target in &TARGETS[..2] {
            let context = fixture.shared.context(target, &enabled)?;
            let rendered = fixture
                .render(path, &context)?
                .ok_or_else(|| eyre::eyre!("independent selected source absent"))?;
            assert_eq!(rendered.len() as u64, profile.bytes);
            assert_eq!(sha256(rendered.as_bytes()), profile.sha256);
            assert!(!rendered.contains("{%"));
            if !feature(&context, METADATA) {
                assert!(!rendered.contains("SFMWorkspacePanelMetadata"));
            }
            if path == INTENT {
                assert_eq!(rendered.contains("record Move("), feature(&context, STACK));
                assert_eq!(
                    rendered.contains("SFMPanelReopenRecipe"),
                    feature(&context, REOPEN)
                );
            }
            if path == DISPATCHER {
                assert_eq!(rendered.contains("boolean moved"), feature(&context, STACK));
                assert_eq!(
                    rendered.contains("layout.pushToStack("),
                    feature(&context, STACK)
                );
                assert_eq!(
                    rendered.contains("Outcome.unsupported();"),
                    !feature(&context, STACK)
                );
            }
            outputs += 1;
        }
    }
    assert_eq!((seen.len(), outputs), (18, 36));
    Ok(())
}

#[test]
fn reopening_without_metadata_preserves_real_nullable_recipe_and_compatibility_constructors()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        let context = fixture.shared.context(target, &[PANELS, REOPEN])?;
        let intent = fixture
            .render(INTENT, &context)?
            .ok_or_else(|| eyre::eyre!("intent absent"))?;
        assert!(!feature(&context, METADATA) && !feature(&context, STACK));
        assert!(intent.contains("@Nullable SFMPanelReopenRecipe reopenRecipe"));
        assert!(
            intent.contains("this(side, panel, null);") && intent.contains("this(panel, null);")
        );
        assert!(!intent.contains("SFMWorkspacePanelMetadata") && !intent.contains("record Move("));
        let dispatcher = fixture
            .render(DISPATCHER, &context)?
            .ok_or_else(|| eyre::eyre!("dispatcher absent"))?;
        assert!(dispatcher.contains("OpenAsTab) return Outcome.unsupported();"));
        assert!(dispatcher.contains("layout.insert(source, open.side(), open.panel());"));
        for path in [RECIPE, REOPEN_CONTEXT] {
            assert!(fixture.render(path, &context)?.is_some());
        }
        for unrelated in [
            "workspace_lifecycle",
            "workspace_widget_hosts",
            "keyboard_profiles",
            "terminal_remote",
            "terminal_vox_runtime",
            "editor_documents",
        ] {
            assert!(!feature(&context, unrelated));
        }
    }
    Ok(())
}

#[test]
fn strict_current_contexts_reject_missing_stack_or_reopening_prerequisites_and_unwitnessed_targets()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        for (enabled, missing) in [
            (&[PANELS, STACK][..], METADATA),
            (&[METADATA, STACK][..], PANELS),
            (&[REOPEN][..], PANELS),
        ] {
            let error = fixture
                .shared
                .context(target, enabled)
                .expect_err("prerequisite must not be auto-enabled");
            assert!(error.to_string().contains(missing));
        }
    }
    for target in &TARGETS[2..] {
        for owner in [METADATA, STACK, REOPEN] {
            let error = fixture
                .shared
                .context(target, &[PANELS, owner])
                .expect_err("this exact owner has no source support on the target");
            let message = error.to_string();
            assert!(message.contains(owner) && message.contains("does not support target"));
        }
    }
    for target in TARGETS {
        let context = fixture.shared.context(target, &[])?;
        for path in PATHS {
            assert!(fixture.render(path, &context)?.is_none());
        }
    }
    assert!(
        fixture
            .shared
            .context("1.19.2", &[PANELS, "unknown_intent_owner"])
            .is_err()
    );
    Ok(())
}

#[test]
fn baseline_dispatcher_and_intent_are_identical_across_all_ten_real_mc_contexts() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let context = fixture.shared.context(target, &[PANELS])?;
        for path in [INTENT, DISPATCHER] {
            let body = fixture
                .render(path, &context)?
                .ok_or_else(|| eyre::eyre!("baseline absent"))?;
            assert_eq!(
                body.as_bytes(),
                fixture.raw[expected_oid(path, "1.20", true)
                    .ok_or_else(|| eyre::eyre!("baseline raw witness absent"))?]
            );
        }
        for path in [RECIPE, REOPEN_CONTEXT] {
            assert!(fixture.render(path, &context)?.is_none());
        }
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
    }
    Ok(())
}

#[test]
fn isolated_common_edit_propagates_through_all_twenty_present_owners_without_live_mutation()
-> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let anchor = "package ca.teamdman.sfm.client.screen.workspace;\n";
    let replacement = format!("{anchor}\n// Shared intent source proof.\n");
    let mut outputs = 0;
    for path in [INTENT, DISPATCHER] {
        let source = std::str::from_utf8(&fixture.source[path])?;
        ensure!(
            source.matches(anchor).count() == 1,
            "shared package anchor changed"
        );
        let p = temp.path().join(CORE_ROOT).join(path);
        fs::create_dir_all(
            p.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent missing"))?,
        )?;
        fs::write(&p, source.replacen(anchor, &replacement, 1))?;
        let changed = read_bounded(&p, 64 * 1024)?;
        for target in TARGETS {
            let context = fixture.full(target)?;
            let original = fixture
                .render(path, &context)?
                .ok_or_else(|| eyre::eyre!("original absent"))?;
            assert_eq!(
                render_java_source(std::str::from_utf8(&changed)?, &context)?,
                original.replacen(anchor, &replacement, 1)
            );
            outputs += 1;
        }
        assert_eq!(fixture.shared.read_source(path)?, fixture.source[path]);
    }
    assert_eq!(outputs, 20);
    Ok(())
}

#[test]
fn actual_collector_uses_fixed_core_automatic_java_and_refuses_snapshot_dispatch() -> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture.full("1.19.2")?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let mut metadata = fixture.shared.metadata.clone();
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
            fixture.source[output].clone()
        } else {
            read_bounded(&fixture.shared.core.join(&input.input), 16 * 1024 * 1024)?
        };
        let p = root.join(&input.input);
        fs::create_dir_all(
            p.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent missing"))?,
        )?;
        fs::write(&p, bytes)?;
    }
    let artifacts = collect_core_artifacts(&root, &selection, &context)?;
    for path in PATHS {
        let artifact = &artifacts[path];
        assert_eq!(artifact.source_bytes, fixture.source[path]);
        assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{path}"));
        assert!(artifact.overlay.is_none());
        let (_, body) = std::str::from_utf8(&artifact.output_bytes)?
            .split_once('\n')
            .ok_or_else(|| eyre::eyre!("generated banner absent"))?;
        assert_eq!(Some(body), fixture.render(path, &context)?.as_deref());
    }
    let original = std::str::from_utf8(&fixture.source[INTENT])?;
    fs::write(
        root.join(INTENT),
        format!("{{% case environment %}}\n{{% when \"dev\" %}}\n{original}{{% endcase %}}\n"),
    )?;
    assert!(collect_core_artifacts(&root, &selection, &context).is_err());
    assert!(collect_core_artifacts(&temp.path().join("other-core"), &selection, &context).is_err());
    Ok(())
}

#[test]
fn raw_mutations_unknown_directives_and_real_multiplexer_cycle_remain_explicit() -> Result<()> {
    let fixture = Fixture::load()?;
    for (oid, digest, count) in RAW {
        let raw = &fixture.raw[oid];
        assert!(exact_raw(&raw[..raw.len() - 1], digest, count).is_err());
        assert!(
            exact_raw(
                &std::str::from_utf8(raw)?.replace('\n', "\r\n").into_bytes(),
                digest,
                count
            )
            .is_err()
        );
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(raw);
        assert!(exact_raw(&bom, digest, count).is_err());
        let mut token = raw.clone();
        token[0] ^= 1;
        assert!(exact_raw(&token, digest, count).is_err());
    }
    let context = fixture.full("1.19.2")?;
    for path in [INTENT, DISPATCHER] {
        let source = std::str::from_utf8(&fixture.source[path])?;
        for bad in [
            format!("{{% else %}}\n{source}"),
            format!("{{% if features.unknown_intent_owner %}}\n{source}{{% endif %}}\n"),
            format!("{{% if features.workspace_panel_reopening %}}\n{source}{{% endcase %}}\n"),
        ] {
            assert!(render_java_source(&bad, &context).is_err());
        }
    }
    let context_body = fixture
        .render(REOPEN_CONTEXT, &context)?
        .ok_or_else(|| eyre::eyre!("real reopen context absent"))?;
    let recipe = fixture
        .render(RECIPE, &context)?
        .ok_or_else(|| eyre::eyre!("recipe absent"))?;
    assert!(context_body.contains("SFMScreenMultiplexer workspace,"));
    assert!(recipe.contains("SFMScreenPanel reopen();"));
    assert!(!context_body.contains("interface") && !context_body.contains("Object workspace"));
    for path in PATHS {
        let body = fixture
            .render(path, &context)?
            .ok_or_else(|| eyre::eyre!("full source absent"))?;
        assert!(
            !body.contains("class SFMScreenMultiplexer")
                && !body.contains("SFMExplorerPresentationRecipe")
                && !body.contains("Runtime.getRuntime")
                && !body.contains("GLFW")
        );
    }
    Ok(())
}
