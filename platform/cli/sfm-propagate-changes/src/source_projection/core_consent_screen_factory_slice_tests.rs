//! Genuine typed screen factories and their three pure context carriers.
//! Promotion-first source evidence: no registry, UI, action or signing executes.
//! Historical ledgers and Git blobs stay immutable; the current carrier union
//! is an explicit refinement, not a rewrite of earlier action witnesses.
#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_client_registration_current_contract::reviewed_pre_workspace_registrar;
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

const LEDGER: &str = "docs/tasks/sfm-core-consent-screen-factory-slice.json";
const TYPE: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMClientScreenType.java";
const REGISTRY: &str = "src/main/java/ca/teamdman/sfm/client/registry/SFMClientScreenTypes.java";
const FACTORY: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMClientProgramConsentsScreenType.java";
const SOURCE: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionSource.java";
const CONTEXT: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionContext.java";
const AVAILABILITY: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionAvailability.java";
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const TEN: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const UI: [&str; 7] = [
    "client_actions",
    "client_program_consent",
    "sfml_execution_side",
    "confirmation_review_callbacks",
    "workspace_panel_lookup",
    "workspace_panels",
    "workspace_widget_hosts",
];
const EFFECTS: [&str; 7] = [
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientAction.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionRequirement.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionExecutor.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionDispatcherCompiler.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionCommandTree.java",
    "src/main/java/ca/teamdman/sfm/client/presentation/SFMItemIcon.java",
    "src/main/java/ca/teamdman/sfm/client/registry/SFMClientActions.java",
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
const RAW: [(&str, usize, &str, usize); 6] = [
    (
        "dc59dc67b0b07a93d24a007c612136243a20aece",
        853,
        "sha256:d2ef680ecbc804c49d64948b2cf21150c59a19e75e4a6141c371077222853f5d",
        16,
    ),
    (
        "34183e2d37fe20bb9656b8f34afb2977079c23ba",
        822,
        "sha256:f97f38226b9d69c86991f5edabbbe1de92c7445af46e87d2c4dd6c8bef3b5c3c",
        23,
    ),
    (
        "973d08b367991e749ee24e01fbf6ae3f2806eea6",
        815,
        "sha256:aac2b3208d30ed0d1f1a9ebd1e0dedbb668d9b37045880d91bff39678f14b22c",
        23,
    ),
    (
        "771540939be921246ffbfacf5b04eccd43fd02d7",
        803,
        "sha256:78e41048f6062c2ee037335ea51672ae4186268488c53b6254a7f9001dd1febe",
        23,
    ),
    (
        "12b06af9e5d6dfc29e5bb62fd9ff7b2a2aff31d6",
        1729,
        "sha256:04794882816eab36ad70037ffc6c8ab205cc0f832e65754a172a605c26482b85",
        44,
    ),
    (
        "978394efaf4dcac785fd74e74b4fe1d9a5e9ed3b",
        1780,
        "sha256:73c765db1a34839a571b6cd81355b8e3a9af0046d12c01a9f51c667840465b0d",
        45,
    ),
];
const SOURCES: [(&str, usize, &str); 3] = [
    (
        TYPE,
        1275,
        "sha256:0aaff1567c87db98f03ac5bb49970fdb2e42182b670ad7d751a20f448f4c33a9",
    ),
    (
        REGISTRY,
        2034,
        "sha256:6c964756347d0de30a4f9343e3d22268f7884610dc6ffa2cf8d4ed834a4d4ca2",
    ),
    (
        FACTORY,
        1070,
        "sha256:65f993e62376660c9866d9f3ebe0106a1d8659f1595ff390e389ef8e9bb47e00",
    ),
];
const CARRIERS: [(&str, usize, &str); 3] = [
    (
        SOURCE,
        1857,
        "sha256:bb3a637c1ed303d5f16d2f4ddf177950076a3cfdfd89eb037e0ec6bb08e0e037",
    ),
    (
        CONTEXT,
        2894,
        "sha256:2713d367601d6441a697e2c25650a7e187a4157f7785e0c35e407a0650e97a68",
    ),
    (
        AVAILABILITY,
        1363,
        "sha256:aea89e3a2ac072f69579a9447d44d4ce2cba5f0551bb2a3f6e704a3e21618afb",
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
    carriers: Vec<Carrier>,
    actual_provider_closure: Vec<Provider>,
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
    carrier_rule_only_changes: usize,
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
}
#[derive(Facet)]
struct Carrier {
    path: String,
    bytes: usize,
    sha256: String,
    historical_rule: InputVariant,
    current_rule: InputVariant,
}
#[derive(Facet)]
struct Provider {
    path: String,
    bytes: usize,
    sha256: String,
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
            ledger.schema == "sfm:core-consent-screen-factory-slice@1"
                && ledger.normalization == "none"
                && s.new_sources == 3
                && s.historical_cells == 60
                && s.present_cells == 22
                && s.absent_cells == 38
                && s.raw_objects == 6
                && s.raw_bytes == 6802
                && s.authored_bytes == 4379
                && s.carrier_rule_only_changes == 3
                && s.prepared_tests == 8
                && ledger.files.len() == 3
                && ledger.raw_objects.len() == 6
                && ledger.carriers.len() == 3
                && ledger.actual_provider_closure.len() == 8
                && ledger.definitions.len() == 8
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(key, oid)| (key.to_owned(), oid.to_owned()))
                        .collect(),
            "typed factory witness scope changed"
        );
        for (name, support, requires) in [
            ("client_actions", &TEN[..], &[][..]),
            ("workspace_panels", &TEN[..], &[][..]),
            (
                "workspace_panel_reopening",
                &D2[..],
                &["workspace_panels"][..],
            ),
            (
                "client_program_consent",
                &D2[..],
                &["sfml_execution_side"][..],
            ),
            ("sfml_execution_side", &D2[..], &[][..]),
            ("confirmation_review_callbacks", &D2[..], &[][..]),
            ("workspace_panel_lookup", &D2[..], &[][..]),
            ("workspace_widget_hosts", &D2[..], &[][..]),
        ] {
            let old = &ledger.definitions[name];
            let current = &core.features.0[name];
            ensure!(
                old.supported_targets == support
                    && old.requires == requires
                    && current.supported_targets == support
                    && current.requires == requires,
                "factory owner support/prerequisites changed: {name}"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &RAW.iter().map(|(oid, _, _, _)| (*oid).to_owned()).collect(),
        )?;
        for ((oid, length, digest, lf), row) in RAW.into_iter().zip(&ledger.raw_objects) {
            ensure!(
                row.oid == oid && row.bytes == length && row.sha256 == digest && row.lf_count == lf,
                "raw factory evidence changed"
            );
            verify_raw(&raw[oid], length, digest, lf)?;
        }
        let mut sources = BTreeMap::new();
        for ((path, length, digest), row) in SOURCES.into_iter().zip(&ledger.files) {
            let bytes = core.read_source(path)?;
            ensure!(
                row.path == path
                    && row.bytes == length
                    && row.sha256 == digest
                    && bytes.len() == length
                    && sha256(&bytes) == digest
                    && core.metadata.source_rules.get(path) == Some(&vec![row.source_rule.clone()]),
                "typed factory source/rule changed: {path}"
            );
            ensure!(
                !bytes.contains(&b'\r')
                    && bytes.last() == Some(&b'\n')
                    && std::str::from_utf8(&bytes)?
                        .matches(&row.common_edit_anchor)
                        .count()
                        == 1,
                "authored factory bytes/anchor changed"
            );
            sources.insert(path.to_owned(), bytes);
        }
        for ((path, length, digest), row) in CARRIERS.into_iter().zip(&ledger.carriers) {
            let bytes = core.read_source(path)?;
            ensure!(
                row.path == path
                    && row.bytes == length
                    && row.sha256 == digest
                    && bytes.len() == length
                    && sha256(&bytes) == digest
                    && row.historical_rule.input == path
                    && row.historical_rule.template
                    && row.historical_rule.when.all_features == ["client_actions"]
                    && row.historical_rule.when.any_features.is_empty()
                    && row.historical_rule.when.targets.is_empty()
                    && row.historical_rule.when.none_features.is_empty()
                    && row.current_rule.input == path
                    && row.current_rule.template
                    && row.current_rule.when.any_features == ["client_actions", "workspace_panels"]
                    && row.current_rule.when.all_features.is_empty()
                    && row.current_rule.when.targets.is_empty()
                    && row.current_rule.when.none_features.is_empty()
                    && core.metadata.source_rules.get(path)
                        == Some(&vec![row.current_rule.clone()]),
                "only exact pure-carrier union is authorized: {path}"
            );
            sources.insert(path.to_owned(), bytes);
        }
        for row in &ledger.actual_provider_closure {
            let bytes = core.read_source(&row.path)?;
            // Keep this ledger's historical identity. The reviewed successor
            // validates current bytes before inverting exactly two approved guards.
            let predecessor;
            let bytes =
                if row.path == "src/main/java/ca/teamdman/sfm/client/SFMClientRegistrations.java" {
                    predecessor = reviewed_pre_workspace_registrar(&bytes)?;
                    predecessor.as_bytes()
                } else {
                    bytes.as_slice()
                };
            ensure!(
                bytes.len() == row.bytes && sha256(bytes) == row.sha256,
                "real provider changed: {}",
                row.path
            );
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
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selection = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if !selection.inputs.contains_key(path) {
            ensure!(
                selection.omitted_paths.contains(path),
                "unaccounted factory omission"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.sources[path])?,
            context,
        )?))
    }
    fn metadata(&self) -> CoreProjectInputs {
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
                    .ok_or_else(|| eyre::eyre!("isolated source parent"))?,
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
                    "isolated project input changed between target contexts"
                );
            } else {
                fs::create_dir_all(
                    file.parent()
                        .ok_or_else(|| eyre::eyre!("isolated build parent"))?,
                )?;
                fs::write(file, bytes)?;
            }
        }
        Ok(())
    }
}
fn verify_raw(bytes: &[u8], length: usize, digest: &str, lf: usize) -> Result<()> {
    ensure!(
        bytes.len() == length
            && sha256(bytes) == digest
            && bytes.iter().filter(|byte| **byte == b'\n').count() == lf
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[239, 187, 191])
            && bytes.last() == Some(&b'\n'),
        "raw source normalization forbidden"
    );
    Ok(())
}
fn historical_oid(path: &str, environment: &str, target: &str) -> Option<&'static str> {
    if environment == "release" {
        return None;
    }
    match path {
        TYPE if D2.contains(&target) => Some("34183e2d37fe20bb9656b8f34afb2977079c23ba"),
        TYPE if target == "26.1.2" => Some("771540939be921246ffbfacf5b04eccd43fd02d7"),
        TYPE => Some("973d08b367991e749ee24e01fbf6ae3f2806eea6"),
        REGISTRY if ["1.19.2", "1.19.4", "1.20", "1.20.1"].contains(&target) => {
            Some("12b06af9e5d6dfc29e5bb62fd9ff7b2a2aff31d6")
        }
        REGISTRY => Some("978394efaf4dcac785fd74e74b4fe1d9a5e9ed3b"),
        FACTORY if D2.contains(&target) => Some("dc59dc67b0b07a93d24a007c612136243a20aece"),
        FACTORY => None,
        _ => panic!("unreviewed factory witness"),
    }
}
fn historical_features(environment: &str, target: &str) -> Vec<&'static str> {
    if environment == "release" {
        return Vec::new();
    }
    if D2.contains(&target) {
        let mut features = UI.to_vec();
        features.push("workspace_panel_reopening");
        features
    } else {
        vec!["workspace_panels"]
    }
}

#[test]
fn sixty_actual_git_cells_reconstruct_raw_factories_and_original_absence() -> Result<()> {
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
            "bounded offline factory tree query failed"
        );
        let mut tree = BTreeMap::new();
        for row in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = row
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("tree framing"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && tree.insert(path.to_owned(), fields[2].to_owned()).is_none(),
                "tree member type changed"
            );
        }
        let (environment, target) = key
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("context framing"))?;
        let context = f
            .core
            .context(target, &historical_features(environment, target))?;
        for (path, _, _) in SOURCES {
            let expected = historical_oid(path, environment, target);
            assert_eq!(
                tree.get(&format!("platform/minecraft/{path}"))
                    .map(String::as_str),
                expected
            );
            match (expected, f.render(path, &context)?) {
                (Some(oid), Some(body)) => {
                    assert_eq!(body.as_bytes(), f.raw[oid]);
                    present += 1;
                }
                (None, None) => absent += 1,
                _ => panic!("historical membership changed: {key} {path}"),
            }
        }
    }
    assert_eq!((present, absent), (22, 38));
    Ok(())
}

#[test]
fn action_off_workspace_on_selects_only_three_pure_carriers_not_action_effects() -> Result<()> {
    let f = Fixture::load()?;
    for target in TEN {
        for mask in 0_u8..4 {
            let mut enabled = Vec::new();
            if mask & 1 != 0 {
                enabled.push("client_actions");
            }
            if mask & 2 != 0 {
                enabled.push("workspace_panels");
            }
            let context = f.core.context(target, &enabled)?;
            for (path, _, _) in CARRIERS {
                assert_eq!(f.render(path, &context)?.is_some(), mask != 0);
            }
            for path in [TYPE, REGISTRY] {
                assert_eq!(f.render(path, &context)?.is_some(), mask & 2 != 0);
            }
            assert!(f.render(FACTORY, &context)?.is_none());
            let effects = EFFECTS.into_iter().map(str::to_owned).collect();
            let selected = select_core_inputs(&f.core.metadata, &context, &effects)?;
            for path in EFFECTS {
                assert_eq!(
                    selected.inputs.contains_key(path),
                    mask & 1 != 0,
                    "{target} {mask} {path}"
                );
                if mask & 1 == 0 {
                    assert!(selected.omitted_paths.contains(path));
                }
            }
            if mask == 2 {
                let source = f
                    .render(SOURCE, &context)?
                    .ok_or_else(|| eyre::eyre!("pure Source omitted"))?;
                assert!(
                    source.contains("Consumer<Component> feedback")
                        && source.contains("feedback.accept(")
                );
                assert!(!source.contains("StructuredResult") && !source.contains("execute("));
                let host = f
                    .render(CONTEXT, &context)?
                    .ok_or_else(|| eyre::eyre!("pure Context omitted"))?;
                assert!(
                    host.contains("originatingHostIsCurrent.getAsBoolean()")
                        && host.contains("requiredType.isInstance(originatingHost)")
                );
                assert_eq!(host.contains("originatingPanelId"), D2.contains(&target));
                for forbidden in [
                    "SFMClientActions",
                    "SFMClientActionExecutor",
                    "programmaticHandler",
                    "SFMClientActionRequirement",
                ] {
                    assert!(!source.contains(forbidden) && !host.contains(forbidden));
                }
            }
        }
    }
    Ok(())
}

#[test]
fn consent_ui_conjunction_and_reopening_off_real_panel_remain_independent() -> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        for reopening in [false, true] {
            let mut enabled = UI.to_vec();
            if reopening {
                enabled.push("workspace_panel_reopening");
            }
            let context = f.core.context(target, &enabled)?;
            let factory = f
                .render(FACTORY, &context)?
                .ok_or_else(|| eyre::eyre!("genuine consent factory omitted"))?;
            let screen_type = f
                .render(TYPE, &context)?
                .ok_or_else(|| eyre::eyre!("screen type omitted"))?;
            if reopening {
                assert_eq!(
                    factory.as_bytes(),
                    f.raw["dc59dc67b0b07a93d24a007c612136243a20aece"]
                );
                assert_eq!(
                    screen_type.as_bytes(),
                    f.raw["34183e2d37fe20bb9656b8f34afb2977079c23ba"]
                );
                assert!(
                    factory.contains("new Recipe(id)")
                        && factory.contains("implements SFMPanelReopenRecipe")
                );
            } else {
                assert_eq!(
                    screen_type.as_bytes(),
                    f.raw["973d08b367991e749ee24e01fbf6ae3f2806eea6"]
                );
                assert!(factory.contains("opener.open(context, new ClientProgramConsentsPanel())"));
                assert!(!factory.contains("Recipe") && !screen_type.contains("Recipe"));
            }
            assert!(
                !context.features["client_program_signing"]
                    && !context.features["client_frame_language"]
                    && !context.features["command_palette"]
                    && !context.features["structured_action_results"]
            );
            assert!(
                factory.contains(
                    "LiteralArgumentBuilder.<SFMClientActionSource>literal(id.toString())"
                )
            );
        }
        for missing in [
            "client_actions",
            "client_program_consent",
            "confirmation_review_callbacks",
            "workspace_panel_lookup",
            "workspace_panels",
            "workspace_widget_hosts",
        ] {
            let enabled = UI
                .into_iter()
                .filter(|name| *name != missing)
                .collect::<Vec<_>>();
            let context = f.core.context(target, &enabled)?;
            assert!(
                f.render(FACTORY, &context)?.is_none(),
                "missing {missing} must omit UI factory"
            );
        }
    }
    Ok(())
}

#[test]
fn only_three_rules_refine_historical_carrier_membership_without_changing_bytes() -> Result<()> {
    let f = Fixture::load()?;
    let mut historical = f.core.metadata.clone();
    for row in &f.ledger.carriers {
        historical
            .source_rules
            .insert(row.path.clone(), vec![row.historical_rule.clone()]);
    }
    let deltas = historical
        .source_rules
        .iter()
        .filter(|(path, rules)| f.core.metadata.source_rules.get(*path) != Some(*rules))
        .map(|(path, _)| path.as_str())
        .collect::<BTreeSet<_>>();
    assert_eq!(deltas, BTreeSet::from([SOURCE, CONTEXT, AVAILABILITY]));
    let nine = CARRIERS
        .iter()
        .map(|(path, _, _)| (*path).to_owned())
        .chain(EFFECTS[..6].iter().map(|path| (*path).to_owned()))
        .collect();
    for target in TEN {
        let workspace = f.core.context(target, &["workspace_panels"])?;
        let old = select_core_inputs(&historical, &workspace, &nine)?;
        let current = select_core_inputs(&f.core.metadata, &workspace, &nine)?;
        for (path, _, _) in CARRIERS {
            assert!(old.omitted_paths.contains(path) && current.inputs.contains_key(path));
        }
        for path in &EFFECTS[..6] {
            assert!(old.omitted_paths.contains(*path) && current.omitted_paths.contains(*path));
        }
        let actions = f.core.context(target, &["client_actions"])?;
        assert_eq!(
            select_core_inputs(&historical, &actions, &nine)?,
            select_core_inputs(&f.core.metadata, &actions, &nine)?
        );
        let off = f.core.context(target, &[])?;
        for metadata in [&historical, &f.core.metadata] {
            let selection = select_core_inputs(metadata, &off, &nine)?;
            for path in &nine {
                assert!(selection.omitted_paths.contains(path));
            }
        }
    }
    for (path, _, _) in CARRIERS {
        assert_eq!(f.core.read_source(path)?, f.sources[path]);
    }
    Ok(())
}

#[test]
fn actual_collector_automatically_templates_java_and_preserves_twenty_origins() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut metadata = f.metadata();
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let inventory = discover_core_source_files(&root)?;
    let mut present = 0;
    for (key, _) in CONTEXTS {
        let (environment, target) = key
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("context framing"))?;
        let context = f
            .core
            .context(target, &historical_features(environment, target))?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (path, _, _) in SOURCES {
            if let Some(oid) = historical_oid(path, environment, target) {
                let artifact = &artifacts[path];
                let (_, body) = std::str::from_utf8(&artifact.output_bytes)?
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
    assert_eq!(present, 22);
    Ok(())
}

#[test]
fn omitted_poison_is_not_read_and_unknown_or_unsupported_features_refuse() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    for path in f.sources.keys() {
        let file = root.join(path);
        fs::create_dir_all(
            file.parent()
                .ok_or_else(|| eyre::eyre!("poison parent absent"))?,
        )?;
        fs::write(file, b"{% if features.unapproved_screen_factory %}\n\xff")?;
    }
    let inventory = discover_core_source_files(&root)?;
    let metadata = f.metadata();
    for target in TEN {
        let context = f.core.context(target, &[])?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for path in f.sources.keys() {
            assert!(selected.omitted_paths.contains(path) && !artifacts.contains_key(path));
        }
    }
    assert!(
        f.core
            .context("1.19.2", &["workspace_panel_reopening"])
            .is_err()
    );
    assert!(
        f.core
            .context("1.19.2", &["client_program_consent"])
            .is_err()
    );
    for target in TEN.into_iter().filter(|target| !D2.contains(target)) {
        assert!(
            f.core
                .context(target, &["workspace_panels", "workspace_panel_reopening"])
                .is_err()
        );
        assert!(f.core.context(target, &UI).is_err());
    }
    let context = f.core.context("1.19.2", &UI)?;
    let bad = std::str::from_utf8(&f.sources[FACTORY])?.replacen(
        "features.workspace_panel_reopening",
        "features.unapproved_screen_factory",
        1,
    );
    assert!(render_java_source(&bad, &context).is_err());
    let mut metadata = f.metadata();
    metadata
        .source_rules
        .get_mut(TYPE)
        .ok_or_else(|| eyre::eyre!("type rule absent"))?[0]
        .when
        .any_features
        .push("unapproved_screen_factory".to_owned());
    assert!(select_core_inputs(&metadata, &context, &inventory).is_err());
    Ok(())
}

#[test]
fn isolated_common_edits_propagate_without_raw_witness_normalization() -> Result<()> {
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
    for row in &f.ledger.files {
        let source = std::str::from_utf8(&f.sources[&row.path])?;
        let edited = source.replacen(
            &row.common_edit_anchor,
            &format!(
                "// Isolated common screen factory edit.\n{}",
                row.common_edit_anchor
            ),
            1,
        );
        fs::write(root.join(&row.path), edited)?;
    }
    let metadata = f.metadata();
    let inventory = discover_core_source_files(&root)?;
    let mut outputs = 0;
    for target in TEN {
        let context = f
            .core
            .context(target, &historical_features("dev", target))?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (path, _, _) in SOURCES {
            if historical_oid(path, "dev", target).is_some() {
                let (_, body) = std::str::from_utf8(&artifacts[path].output_bytes)?
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("common-edit banner absent"))?;
                assert!(body.contains("// Isolated common screen factory edit."));
                let original = f
                    .render(path, &context)?
                    .ok_or_else(|| eyre::eyre!("original omitted"))?;
                let row = f
                    .ledger
                    .files
                    .iter()
                    .find(|row| row.path == path)
                    .ok_or_else(|| eyre::eyre!("edit owner absent"))?;
                assert_eq!(
                    body,
                    original.replacen(
                        &row.common_edit_anchor,
                        &format!(
                            "// Isolated common screen factory edit.\n{}",
                            row.common_edit_anchor
                        ),
                        1
                    )
                );
                outputs += 1;
            }
        }
    }
    assert_eq!(outputs, 22);
    for (path, original) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *original);
    }
    Ok(())
}

#[test]
fn genuine_registry_and_typed_api_keep_existing_workspace_only_bootstrap() -> Result<()> {
    let f = Fixture::load()?;
    for target in TEN {
        let context = f.core.context(target, &["workspace_panels"])?;
        let registry = f
            .render(REGISTRY, &context)?
            .ok_or_else(|| eyre::eyre!("real registry omitted"))?;
        for anchor in [
            "namespace(SFM.MOD_ID)",
            "onlyIf(SFMEnvironmentUtils::isClient)",
            "createNewRegistry()",
            "REGISTRY_CREATOR.register(bus)",
            "REGISTRY_CREATOR.registry()",
            "createContributor(String namespace)",
        ] {
            assert!(
                registry.contains(anchor),
                "original registry contract {anchor}"
            );
        }
        assert_eq!(
            registry.contains("import net.minecraftforge.eventbus.api.IEventBus;"),
            ["1.19.2", "1.19.4", "1.20", "1.20.1"].contains(&target)
        );
        let screen_type = f
            .render(TYPE, &context)?
            .ok_or_else(|| eyre::eyre!("real type omitted"))?;
        assert!(screen_type.contains("SFMScreenPanel panel") && !screen_type.contains("Recipe"));
        assert_eq!(
            screen_type.contains("net.minecraft.resources.Identifier"),
            target == "26.1.2"
        );
        let aggregator = render_java_source(
            std::str::from_utf8(&f.core.read_source(
                "src/main/java/ca/teamdman/sfm/client/SFMClientRegistrations.java",
            )?)?,
            &context,
        )?;
        assert!(
            aggregator.contains("SFMClientScreenTypes.register(bus)")
                && aggregator.contains("SFMWorkspaceScreenTypes.register(bus)")
        );
        assert!(!aggregator.contains("SFMClientActions.register(bus)"));
        let availability = f
            .render(AVAILABILITY, &context)?
            .ok_or_else(|| eyre::eyre!("real availability omitted"))?;
        assert!(
            availability.contains("Objects.requireNonNull(target)")
                && availability.contains("Objects.requireNonNull(reason)")
                && availability.contains("Unavailable client action has no target")
                && availability.contains("Available client action has no unavailable reason")
        );
    }
    // This is source/API evidence, not a claim the remaining real factory
    // contributor, OpenPanelAction, UI/theme host or Java/runtime closure passed.
    Ok(())
}
