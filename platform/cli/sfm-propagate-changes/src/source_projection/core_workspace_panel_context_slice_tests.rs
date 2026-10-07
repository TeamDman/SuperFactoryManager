//! Source-only proofs for actual workspace PanelContext/PanelHost prerequisites.
//!
//! Frozen identities are test evidence, not a generation fallback. No host,
//! dispatcher, Minecraft Screen or missing-intent stub is supplied by this module.
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

const CONTEXT: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspacePanelContext.java";
const HOST: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspacePanelHost.java";
const PATHS: [&str; 2] = [CONTEXT, HOST];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const QUERIES: [&str; 2] = ["workspace_panel_measurement", "workspace_panel_lookup"];
const LEDGER: &str = "docs/tasks/sfm-core-workspace-panel-context-slice.json";
const LEDGER_SHA: &str = "sha256:348aee68ab3d2e4eb0c4061a37a1ec1c1fb5f13208429ef0385b397c76d9678a";
const RAW: [(&str, &str, u64); 4] = [
    (
        "2bddc19dc9279bee030082c969f9180f14b71450",
        "sha256:f3fabb61c62a27395834c7ff6f6e17fbbbab97b99d3a1016aadd0784f5b03473",
        1085,
    ),
    (
        "73fbd4727b9278a74e66f2735373290a3c51f290",
        "sha256:ff15580ca1baad6ab5614eb25a2f9c797d89c2e0d06f1e102ee99e7fe723e15d",
        834,
    ),
    (
        "175f66d0ac424507bf8b73259a5b18d915b110c0",
        "sha256:4b358882683237db399e3a01e5ff1134b0179e82c277a3e9ce5354a366e1e5ee",
        724,
    ),
    (
        "ee3ac8425d0a6d0402cefa8c0a8d6d904dff9b89",
        "sha256:c0d8d15d9acdb233bdababdab1871cccc7b0746aa00ea9c3ac2a3fa6872a8db6",
        320,
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
    enabled: Vec<String>,
    goldens: Vec<Golden>,
}
#[derive(Facet)]
struct Golden {
    name: String,
    git_blob: String,
    bytes: u64,
    sha256: String,
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
            "immutable context/host ledger changed"
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
            self.ledger.schema == "sfm:core-workspace-panel-context-slice@1"
                && s.production_files == 2
                && s.context_cells == 40
                && s.present_cells == 20
                && s.absent_cells == 20
                && s.raw_variants == 4
                && s.stage_bytes == 2420
                && s.new_source_flags == 2
                && s.independent_masks == 4
                && s.dependencies_changed == 0
                && !s.java_compilation
                && !s.live_runtime
                && !s.complete_java_closure,
            "context/host source-only scope changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.raw_byte_exact
                && self
                    .ledger
                    .normalization
                    .authorized_transformations
                    .is_empty(),
            "normalization forbidden"
        );
        let contexts = CONTEXTS
            .into_iter()
            .map(|(k, v)| (k.to_owned(), v.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            contexts == self.ledger.context_commits,
            "frozen context set changed"
        );
        ensure!(
            self.ledger.files.len() == 2
                && self.ledger.raw_variants.len() == 4
                && self.ledger.prerequisite_definitions.len() == 3
                && self.ledger.full_source_profiles.len() == 10
                && self.ledger.independent_source_profiles.len() == 8,
            "evidence counts changed"
        );
        for (key, d) in &self.ledger.prerequisite_definitions {
            let actual = self
                .shared
                .features
                .0
                .get(key)
                .ok_or_else(|| eyre::eyre!("unregistered context owner {key}"))?;
            let targets = if key == "workspace_panels" {
                TARGETS.to_vec()
            } else {
                TARGETS[..2].to_vec()
            };
            ensure!(
                d.supported_targets == targets
                    && d.requires.is_empty()
                    && actual.supported_targets == d.supported_targets
                    && actual.requires == d.requires,
                "context/host owner graph changed"
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
                "raw identity changed"
            );
        }
        let mut present = 0;
        let mut absent = 0;
        for file in &self.ledger.files {
            let path = path_for_name(&file.name)?;
            ensure!(
                file.intended_core_path == path
                    && file.source_rule.input == path
                    && file.source_rule.template
                    && file.source_rule.when.targets == TARGETS
                    && file.source_rule.when.all_features == ["workspace_panels"]
                    && file.source_rule.when.any_features.is_empty(),
                "sparse portable source contract changed"
            );
            let bytes = &self.source[path];
            ensure!(
                bytes.len() as u64 == file.stage_bytes
                    && sha256(bytes) == file.stage_sha256
                    && bytes.ends_with(b"\n")
                    && !bytes.contains(&b'\r'),
                "promoted core body changed"
            );
            let rules = self
                .shared
                .metadata
                .source_rules
                .get(path)
                .ok_or_else(|| eyre::eyre!("context source rule absent"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == path
                    && rules[0].when.targets == TARGETS
                    && rules[0].when.all_features == ["workspace_panels"]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "actual context membership changed"
            );
            ensure!(file.witnesses.len() == 20, "witness cells missing");
            let mut seen = BTreeSet::new();
            for witness in &file.witnesses {
                let (kind, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid witness"))?;
                let expected = kind == "dev";
                let oid = expected.then(|| golden_oid(path, is_d2(target)));
                let raw = oid.map(|id| &self.raw[id]);
                ensure!(
                    seen.insert(witness.context.as_str())
                        && contexts.get(&witness.context) == Some(&witness.commit)
                        && witness.present == expected
                        && witness.git_blob.as_deref() == oid
                        && witness.raw_bytes == raw.map_or(0, |r| r.len() as u64)
                        && witness.raw_sha256.as_deref() == raw.map(|r| sha256(r)).as_deref(),
                    "frozen membership changed"
                );
                if expected {
                    present += 1;
                } else {
                    absent += 1;
                }
            }
            ensure!(
                seen == contexts.keys().map(String::as_str).collect(),
                "incomplete source contexts"
            );
        }
        ensure!((present, absent) == (20, 20), "membership totals changed");
        Ok(())
    }
    fn full(&self, target: &str) -> Result<ProjectionContext> {
        let profile = self
            .ledger
            .full_source_profiles
            .iter()
            .find(|p| p.target == target)
            .ok_or_else(|| eyre::eyre!("profile absent"))?;
        let expected = if is_d2(target) {
            vec!["workspace_panels", QUERIES[0], QUERIES[1]]
        } else {
            vec!["workspace_panels"]
        };
        ensure!(
            profile.enabled == expected && profile.goldens.len() == 2,
            "source profile ownership changed"
        );
        for golden in &profile.goldens {
            let path = path_for_name(&golden.name)?;
            let oid = golden_oid(path, is_d2(target));
            ensure!(
                golden.git_blob == oid
                    && golden.bytes == self.raw[oid].len() as u64
                    && golden.sha256 == sha256(&self.raw[oid]),
                "full profile identity changed"
            );
        }
        self.shared.context(target, &expected)
    }
    fn selected(&self, context: &ProjectionContext) -> Result<bool> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let expected = feature(context, "workspace_panels");
        let scoped = selection
            .inputs
            .keys()
            .filter(|p| inventory().contains(*p))
            .count();
        ensure!(
            scoped == if expected { 2 } else { 0 },
            "scope includes unowned context sources"
        );
        for path in PATHS {
            if expected {
                ensure!(
                    selection.inputs[path].input == path && !selection.omitted_paths.contains(path),
                    "core provenance changed"
                );
            } else {
                ensure!(
                    selection.omitted_paths.contains(path),
                    "source omission not explicit"
                );
            }
        }
        Ok(expected)
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        ensure!(self.selected(context)?, "refuse omitted context source");
        render_java_source(std::str::from_utf8(&self.source[path])?, context)
    }
}
fn inventory() -> BTreeSet<String> {
    PATHS.into_iter().map(str::to_owned).collect()
}
fn feature(context: &ProjectionContext, key: &str) -> bool {
    context.features.get(key).copied().unwrap_or(false)
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn path_for_name(name: &str) -> Result<&'static str> {
    match name {
        "SFMWorkspacePanelContext" | "SFMWorkspacePanelContext.java" => Ok(CONTEXT),
        "SFMWorkspacePanelHost" | "SFMWorkspacePanelHost.java" => Ok(HOST),
        _ => eyre::bail!("unexpected context/host class"),
    }
}
fn golden_oid(path: &str, new: bool) -> &'static str {
    match (path, new) {
        (CONTEXT, true) => RAW[0].0,
        (HOST, true) => RAW[1].0,
        (CONTEXT, false) => RAW[2].0,
        _ => RAW[3].0,
    }
}
fn exact_raw(bytes: &[u8], digest: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count
            && sha256(bytes) == digest
            && bytes.ends_with(b"\n")
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "unreviewed raw context mutation"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

#[test]
fn forty_membership_cells_and_twenty_exact_bodies_use_actual_selector_and_renderer() -> Result<()> {
    let f = Fixture::load()?;
    let mut count = 0;
    for (key, _) in CONTEXTS {
        let (kind, target) = key
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let context = if kind == "dev" {
            f.full(target)?
        } else {
            f.shared.context(target, &[])?
        };
        assert_eq!(f.selected(&context)?, kind == "dev");
        for path in PATHS {
            if kind == "dev" {
                assert_eq!(
                    f.render(path, &context)?.as_bytes(),
                    f.raw[golden_oid(path, is_d2(target))]
                );
                count += 1;
            } else {
                assert!(f.render(path, &context).is_err());
            }
        }
    }
    assert_eq!(count, 20);
    Ok(())
}

#[test]
fn sixteen_real_query_mask_outputs_match_independent_goldens_and_common_optional_guard()
-> Result<()> {
    let f = Fixture::load()?;
    let mut seen = BTreeSet::new();
    for profile in &f.ledger.independent_source_profiles {
        let path = path_for_name(&profile.name)?;
        assert!(profile.mask < 4 && seen.insert((profile.mask, path)));
        let mut enabled = vec!["workspace_panels"];
        enabled.extend(
            QUERIES
                .into_iter()
                .enumerate()
                .filter(|(i, _)| profile.mask & (1 << i) != 0)
                .map(|(_, key)| key),
        );
        assert_eq!(profile.enabled, enabled);
        for target in &TARGETS[..2] {
            let context = f.shared.context(target, &enabled)?;
            let source = f.render(path, &context)?;
            assert_eq!(source.len() as u64, profile.bytes);
            assert_eq!(sha256(source.as_bytes()), profile.sha256);
            assert_query_contracts(path, &source, &context);
        }
    }
    assert_eq!(seen.len(), 8);
    Ok(())
}
fn assert_query_contracts(path: &str, source: &str, context: &ProjectionContext) {
    let measure = feature(context, QUERIES[0]);
    let lookup = feature(context, QUERIES[1]);
    assert_eq!(
        source.contains("import java.util.Optional;"),
        measure || lookup
    );
    assert_eq!(
        source.contains("Optional<SFMWorkspacePanelMetrics> measure("),
        measure
    );
    assert_eq!(source.contains("Optional<SFMScreenPanel> panel("), lookup);
    assert_eq!(source.contains("SFMScreenPanelBounds"), measure);
    assert_eq!(source.contains("SFMWorkspacePanelMetrics"), measure);
    if path == CONTEXT {
        assert!(
            source.contains("Objects.requireNonNull(panelId);")
                && source.contains("Objects.requireNonNull(host);")
        );
        assert!(source.contains("host.submit(panelId, Objects.requireNonNull(intent))"));
        assert!(
            source.contains(
                "new SFMWorkspacePanelContext(panelId, SFMWorkspacePanelHost.UNAVAILABLE)"
            )
        );
        assert_eq!(
            source.contains("host.measure(panelId, Objects.requireNonNull(logicalBounds))"),
            measure
        );
        assert_eq!(
            source.contains("host.panel(Objects.requireNonNull(requestedPanelId))"),
            lookup
        );
    } else {
        assert_eq!(source.matches("@FunctionalInterface").count(), 1);
        assert_eq!(
            source
                .matches("SFMWorkspacePanelIntentResult submit(")
                .count(),
            1
        );
        assert!(source.contains(
            "UNAVAILABLE = (source, intent) -> SFMWorkspacePanelIntentResult.UNAVAILABLE;"
        ));
        assert_eq!(
            source.matches("default Optional<").count(),
            usize::from(measure) + usize::from(lookup)
        );
        assert_eq!(
            source.matches("return Optional.empty();").count(),
            usize::from(measure) + usize::from(lookup)
        );
    }
}

#[test]
fn query_flags_are_independent_protocols_and_never_force_base_class_or_other_subsystems()
-> Result<()> {
    let f = Fixture::load()?;
    for target in &TARGETS[..2] {
        for query in QUERIES {
            let alone = f.shared.context(target, &[query])?;
            assert!(!f.selected(&alone)?);
            let context = f.shared.context(target, &["workspace_panels", query])?;
            for unrelated in [
                "workspace_panel_metadata",
                "workspace_widget_hosts",
                "terminal_remote",
                "terminal_vox_runtime",
                "canvas_text_editor",
                "workspace_stack_controls",
            ] {
                assert!(!feature(&context, unrelated));
            }
            for path in PATHS {
                assert_query_contracts(path, &f.render(path, &context)?, &context);
            }
        }
    }
    for target in &TARGETS[2..] {
        for query in QUERIES {
            assert!(
                f.shared
                    .context(target, &["workspace_panels", query])
                    .is_err()
            );
        }
    }
    Ok(())
}

#[test]
fn query_off_surface_reconstructs_one_identical_original_body_on_all_ten_targets() -> Result<()> {
    let f = Fixture::load()?;
    for target in TARGETS {
        let context = f.shared.context(target, &["workspace_panels"])?;
        for path in PATHS {
            let source = f.render(path, &context)?;
            assert_eq!(source.as_bytes(), f.raw[golden_oid(path, false)]);
            assert_query_contracts(path, &source, &context);
            assert!(!source.contains("{%") && !source.contains("minecraft_version"));
        }
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
    }
    Ok(())
}

#[test]
fn common_edit_reaches_both_shared_sources_across_all_ten_contexts_without_live_mutation()
-> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let anchor = "package ca.teamdman.sfm.client.screen.workspace;\n";
    let replacement = format!("{anchor}\n// Shared context/host source proof.\n");
    for path in PATHS {
        let source = std::str::from_utf8(&f.source[path])?;
        ensure!(source.matches(anchor).count() == 1, "common anchor changed");
        let p = temp.path().join(CORE_ROOT).join(path);
        fs::create_dir_all(
            p.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(&p, source.replacen(anchor, &replacement, 1))?;
        let changed = read_bounded(&p, 64 * 1024)?;
        for target in TARGETS {
            let context = f.full(target)?;
            assert_eq!(
                render_java_source(std::str::from_utf8(&changed)?, &context)?,
                f.render(path, &context)?.replacen(anchor, &replacement, 1)
            );
        }
        assert_eq!(f.shared.read_source(path)?, f.source[path]);
    }
    Ok(())
}

#[test]
fn actual_collector_uses_fixed_core_automatic_java_and_refuses_snapshot_selection() -> Result<()> {
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
            .ok_or_else(|| eyre::eyre!("banner absent"))?;
        assert_eq!(body, f.render(path, &context)?);
    }
    let source = std::str::from_utf8(&f.source[HOST])?;
    fs::write(
        root.join(HOST),
        format!("{{% case environment %}}\n{{% when \"dev\" %}}\n{source}{{% endcase %}}\n"),
    )?;
    assert!(collect_core_artifacts(&root, &selection, &context).is_err());
    assert!(collect_core_artifacts(&temp.path().join("other-core"), &selection, &context).is_err());
    Ok(())
}

#[test]
fn raw_eol_token_bom_eof_mutations_and_invalid_owner_directives_fail_closed() -> Result<()> {
    let f = Fixture::load()?;
    for (oid, digest, count) in RAW {
        let bytes = &f.raw[oid];
        assert!(exact_raw(&bytes[..bytes.len() - 1], digest, count).is_err());
        assert!(
            exact_raw(
                &std::str::from_utf8(bytes)?
                    .replace('\n', "\r\n")
                    .into_bytes(),
                digest,
                count
            )
            .is_err()
        );
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(bytes);
        assert!(exact_raw(&bom, digest, count).is_err());
        let mut token = bytes.clone();
        token[0] ^= 1;
        assert!(exact_raw(&token, digest, count).is_err());
    }
    let context = f.shared.context("1.19.2", &["workspace_panels"])?;
    assert!(f.shared.context("1.19.2", &["unknown_host_query"]).is_err());
    for path in PATHS {
        let source = std::str::from_utf8(&f.source[path])?;
        for bad in [
            format!("{{% else %}}\n{source}"),
            format!("{{% if features.unknown_host_query %}}\n{source}{{% endif %}}\n"),
            format!("{{% if features.workspace_panel_lookup %}}\n{source}{{% endcase %}}\n"),
        ] {
            assert!(render_java_source(&bad, &context).is_err());
        }
    }
    Ok(())
}

#[test]
fn genuine_protocol_keeps_real_intent_and_host_implementation_gaps_visible() -> Result<()> {
    let f = Fixture::load()?;
    let context = f.full("1.19.2")?;
    let host = f.render(HOST, &context)?;
    let request = f.render(CONTEXT, &context)?;
    assert!(
        host.contains("SFMWorkspacePanelIntent intent")
            && request.contains("SFMWorkspacePanelIntent intent")
    );
    for body in [host, request] {
        for forbidden in [
            "class SFMScreenMultiplexer",
            "class SFMPanelWidgetHost",
            "sealed interface SFMWorkspacePanelIntent",
            "import net.minecraft.client.Minecraft",
            "GLFW",
            "Runtime.getRuntime",
        ] {
            assert!(!body.contains(forbidden));
        }
        assert!(!body.contains("focusedPanel") && !body.contains("focusedTerminal"));
    }
    Ok(())
}
