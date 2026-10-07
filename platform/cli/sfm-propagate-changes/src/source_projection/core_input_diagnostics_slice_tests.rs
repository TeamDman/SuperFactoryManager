//! Promotion-first source proof for the genuine input-diagnostics factory and panel.
//! Historical Git is test evidence only. No GLFW, clipboard, UI or Java runs here.
#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputPredicate;
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

const LEDGER: &str = "docs/tasks/sfm-core-input-diagnostics-slice.json";
const FACTORY: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/diagnostic/SFMInputDiagnosticsScreenType.java";
const PANEL: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/diagnostic/SFMInputDiagnosticsPanel.java";
const TAP: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/diagnostic/SFMRawInputDiagnosticTap.java";
const TYPE: &str = "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMClientScreenType.java";
const SOURCE: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionSource.java";
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
const SOURCES: [(&str, usize, &str); 3] = [
    (
        FACTORY,
        1519,
        "sha256:5569c04295b926cb64ef1d2a88be668a42b0326884a26f3d1659860f01cddd5c",
    ),
    (
        PANEL,
        8161,
        "sha256:d9973d9bdfadcc63ff6b537fc7234f306b6de7831aca0d59b94e4835736acbce",
    ),
    (
        TAP,
        5871,
        "sha256:12156f711994c7170298ecc7dc73fa0df42367608cbbb09c797d441c602139a9",
    ),
];
const RAW: [(&str, usize, &str, usize); 3] = [
    (
        "76c8d7286e04896571fcad2f7a1ae3b7c0c361f1",
        1192,
        "sha256:73a450d1deb012d3028850c02b73c077fbb275848d52742a7cee80b4842f99a0",
        32,
    ),
    (
        "6a928cbeac7ecb15dc887e713669cce2a28a4d67",
        8161,
        "sha256:d9973d9bdfadcc63ff6b537fc7234f306b6de7831aca0d59b94e4835736acbce",
        208,
    ),
    (
        "169faa1ccbeaeff351f8cd70e2e7aae77f320de3",
        5871,
        "sha256:12156f711994c7170298ecc7dc73fa0df42367608cbbb09c797d441c602139a9",
        154,
    ),
];
const PROVIDERS: [(&str, usize, &str); 7] = [
    (
        SOURCE,
        1857,
        "sha256:bb3a637c1ed303d5f16d2f4ddf177950076a3cfdfd89eb037e0ec6bb08e0e037",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenPanel.java",
        4493,
        "sha256:7534488e1e0667681a9e4567d068827536587d55fe31cef942d0a141d75f7046",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenPanelBounds.java",
        749,
        "sha256:cf9503cda9d837a9743656999d42ef98ca2c102df563dfc07b820421c5347254",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspacePanelContext.java",
        1289,
        "sha256:359084ad72455d38243caf5154cd3635354abe1a1376d09162752c38400d0c6f",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPanelReopenRecipe.java",
        487,
        "sha256:c7a03242824ac56d58af090ca28d553e86e5dfac29eae8fde76f8f6b652acd65",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/screen/SFMFontUtils.java",
        15462,
        "sha256:8308845678605cf0bf052c1cfa6f93b2d98c97b4b9e2b5b879b3528c5fd4a78b",
    ),
    (
        TYPE,
        1275,
        "sha256:0aaff1567c87db98f03ac5bb49970fdb2e42182b670ad7d751a20f448f4c33a9",
    ),
];
// V2 integration evidence is separate from the immutable original seven-provider ledger.
const CURRENT_WORKSPACE_CARRIERS: [(&str, usize, &str); 3] = [
    (
        SOURCE,
        1857,
        "sha256:bb3a637c1ed303d5f16d2f4ddf177950076a3cfdfd89eb037e0ec6bb08e0e037",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionContext.java",
        2894,
        "sha256:2713d367601d6441a697e2c25650a7e187a4157f7785e0c35e407a0650e97a68",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionAvailability.java",
        1363,
        "sha256:aea89e3a2ac072f69579a9447d44d4ce2cba5f0551bb2a3f6e704a3e21618afb",
    ),
];

fn validate_reviewed_current_workspace_carrier_rules(metadata: &CoreProjectInputs) -> Result<()> {
    for (path, _, _) in CURRENT_WORKSPACE_CARRIERS {
        let expected = InputVariant {
            input: path.to_owned(),
            template: true,
            when: InputPredicate {
                any_features: vec!["client_actions".to_owned(), "workspace_panels".to_owned()],
                ..InputPredicate::default()
            },
        };
        ensure!(
            metadata.source_rules.get(path) == Some(&vec![expected]),
            "exact reviewed current workspace carrier rule changed: {path}"
        );
    }
    Ok(())
}

const PROFILES: [(bool, usize, &str, usize); 2] = [
    (
        false,
        792,
        "sha256:e83ef2c032f708415a04718dc4ce400100e0430d37fdafcb27dd62baafe31b39",
        18,
    ),
    (
        true,
        1192,
        "sha256:73a450d1deb012d3028850c02b73c077fbb275848d52742a7cee80b4842f99a0",
        32,
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
    source_only_profiles: Vec<Profile>,
}
#[derive(Facet)]
struct Scope {
    sources: usize,
    raw_objects: usize,
    raw_bytes: usize,
    authored_bytes: usize,
    historical_cells: usize,
    present_cells: usize,
    absent_cells: usize,
    prepared_tests: usize,
    definitions: usize,
    unchanged_providers: usize,
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
struct Profile {
    reopening: bool,
    bytes: usize,
    sha256: String,
    lf_count: usize,
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
            128 * 1024,
        )?)?)?;
        let scope = &ledger.scope;
        ensure!(
            ledger.schema == "sfm:core-input-diagnostics-slice@1"
                && ledger.normalization == "none"
                && scope.sources == 3
                && scope.raw_objects == 3
                && scope.raw_bytes == 15224
                && scope.authored_bytes == 15551
                && scope.historical_cells == 60
                && scope.present_cells == 6
                && scope.absent_cells == 54
                && scope.prepared_tests == 8
                && scope.definitions == 3
                && scope.unchanged_providers == 7
                && ledger.files.len() == 3
                && ledger.raw_objects.len() == 3
                && ledger.definitions.len() == 3
                && ledger.actual_provider_closure.len() == 7
                && ledger.historical_membership.len() == 3
                && ledger.source_only_profiles.len() == 2
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(key, value)| (key.to_owned(), value.to_owned()))
                        .collect(),
            "input diagnostic reviewed scope changed"
        );
        let raw = read_git_blobs(
            &core.repository,
            &RAW.iter().map(|(oid, _, _, _)| (*oid).to_owned()).collect(),
        )?;
        for ((oid, length, digest, lf), row) in RAW.into_iter().zip(&ledger.raw_objects) {
            ensure!(
                row.oid == oid
                    && row.bytes == length
                    && row.sha256 == digest
                    && row.lf_count == lf
                    && row.cr_count == 0
                    && row.final_lf,
                "raw diagnostic evidence row changed"
            );
            verify_raw(&raw[oid], length, digest, lf)?;
        }
        let mut source = BTreeMap::new();
        for ((path, length, digest), row) in SOURCES.into_iter().zip(&ledger.files) {
            let bytes = core.read_source(path)?;
            ensure!(
                row.path == path
                    && row.bytes == length
                    && row.sha256 == digest
                    && bytes.len() == length
                    && sha256(&bytes) == digest
                    && !bytes.contains(&b'\r')
                    && bytes.last() == Some(&b'\n')
                    && std::str::from_utf8(&bytes)?
                        .matches(&row.common_edit_anchor)
                        .count()
                        == 1,
                "authored diagnostic input changed: {path}"
            );
            let rule = &row.source_rule;
            let mut current_rule = rule.clone();
            current_rule.template = true;
            ensure!(
                rule.input == path
                    && rule.template == (path == FACTORY)
                    && rule.when.targets == D2
                    && rule.when.all_features == ["input_diagnostics_panel"]
                    && rule.when.any_features.is_empty()
                    && rule.when.none_features.is_empty()
                    && core.metadata.source_rules.get(path) == Some(&vec![current_rule]),
                "diagnostic membership changed: {path}"
            );
            ensure!(
                ledger.historical_membership[path].len() == 20,
                "diagnostic historical cells missing"
            );
            source.insert(path.to_owned(), bytes);
        }
        for (name, row) in &ledger.definitions {
            let actual = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("reviewed diagnostic owner absent: {name}"))?;
            ensure!(
                actual.supported_targets == row.supported_targets
                    && actual.requires == row.requires,
                "diagnostic owner support/prerequisite changed: {name}"
            );
        }
        ensure!(
            ledger.definitions["input_diagnostics_panel"].supported_targets == D2
                && ledger.definitions["input_diagnostics_panel"].requires == ["workspace_panels"]
                && ledger.definitions["workspace_panels"].supported_targets == TEN
                && ledger.definitions["workspace_panels"].requires.is_empty()
                && ledger.definitions["workspace_panel_reopening"].supported_targets == D2
                && ledger.definitions["workspace_panel_reopening"].requires == ["workspace_panels"],
            "fixed original diagnostic contracts changed"
        );
        for ((path, length, digest), provider) in
            PROVIDERS.into_iter().zip(&ledger.actual_provider_closure)
        {
            ensure!(
                provider.path == path && provider.bytes == length && provider.sha256 == digest,
                "fixed original provider evidence changed"
            );
            let bytes = core.read_source(&provider.path)?;
            ensure!(
                bytes.len() == provider.bytes && sha256(&bytes) == provider.sha256,
                "real diagnostic provider changed: {}",
                provider.path
            );
        }
        validate_reviewed_current_workspace_carrier_rules(&core.metadata)?;
        for (path, length, digest) in CURRENT_WORKSPACE_CARRIERS {
            let bytes = core.read_source(path)?;
            ensure!(
                bytes.len() == length && sha256(&bytes) == digest,
                "reviewed current pure workspace carrier body changed: {path}"
            );
        }
        for ((reopening, length, digest, lf), profile) in
            PROFILES.into_iter().zip(&ledger.source_only_profiles)
        {
            ensure!(
                profile.reopening == reopening
                    && profile.bytes == length
                    && profile.sha256 == digest
                    && profile.lf_count == lf,
                "fixed original diagnostic profile changed"
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
                let definition = self
                    .core
                    .features
                    .0
                    .get(&name)
                    .ok_or_else(|| eyre::eyre!("unknown diagnostic owner: {name}"))?;
                names.extend(definition.requires.iter().cloned());
            }
            if before == names.len() {
                break;
            }
        }
        self.core.context(
            target,
            &names.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }

    fn historical_context(&self, key: &str) -> Result<ProjectionContext> {
        let (environment, target) = key
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("historical diagnostic context framing"))?;
        if environment == "dev" && D2.contains(&target) {
            self.context(
                target,
                &["input_diagnostics_panel", "workspace_panel_reopening"],
            )
        } else {
            self.context(target, &[])
        }
    }

    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if !selected.inputs.contains_key(path) {
            ensure!(
                selected.omitted_paths.contains(path),
                "diagnostic omission unaccounted"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.source[path])?,
            context,
        )?))
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
            fs::create_dir_all(
                file.parent()
                    .ok_or_else(|| eyre::eyre!("diagnostic source parent"))?,
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
            if self.source.contains_key(output) {
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
                    "shared diagnostic fixture project input changed"
                );
            } else {
                fs::create_dir_all(
                    file.parent()
                        .ok_or_else(|| eyre::eyre!("diagnostic project parent"))?,
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
            && bytes.last() == Some(&b'\n')
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[239, 187, 191])
            && bytes.iter().filter(|byte| **byte == b'\n').count() == lf,
        "diagnostic raw normalization refused"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

#[test]
fn sixty_frozen_tree_cells_reconstruct_six_raw_diagnostic_outputs() -> Result<()> {
    let fixture = Fixture::load()?;
    let (mut present, mut absent) = (0, 0);
    for (key, commit) in CONTEXTS {
        let mut command = frozen_git_command(&fixture.core.repository);
        command.args(["ls-tree", commit, "--"]);
        for (path, _, _) in SOURCES {
            command.arg(format!("platform/minecraft/{path}"));
        }
        let output = command.output()?;
        ensure!(
            output.status.success()
                && output.stdout.len() <= 64 * 1024
                && output.stderr.len() <= 64 * 1024,
            "bounded diagnostic tree query failed"
        );
        let mut tree = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("diagnostic tree framing"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && tree.insert(path.to_owned(), fields[2].to_owned()).is_none(),
                "unexpected diagnostic Git member"
            );
        }
        let context = fixture.historical_context(key)?;
        for (path, _, _) in SOURCES {
            let expected = fixture.ledger.historical_membership[path][key].as_ref();
            assert_eq!(tree.get(&format!("platform/minecraft/{path}")), expected);
            match (expected, fixture.render(path, &context)?) {
                (Some(oid), Some(actual)) => {
                    assert_eq!(actual.as_bytes(), fixture.raw[oid], "{key} {path}");
                    present += 1;
                }
                (None, None) => absent += 1,
                _ => panic!("diagnostic historical membership changed: {key} {path}"),
            }
        }
    }
    assert_eq!((present, absent), (6, 54));
    Ok(())
}

#[test]
fn independent_reopening_and_action_masks_use_real_panel_without_action_effects() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in D2 {
        for mask in 0_u8..4 {
            let reopening = mask & 1 != 0;
            let actions = mask & 2 != 0;
            let mut requested = vec!["input_diagnostics_panel"];
            if reopening {
                requested.push("workspace_panel_reopening");
            }
            if actions {
                requested.push("client_actions");
            }
            let context = fixture.context(target, &requested)?;
            let factory = fixture
                .render(FACTORY, &context)?
                .ok_or_else(|| eyre::eyre!("genuine diagnostic factory omitted"))?;
            let profile = fixture
                .ledger
                .source_only_profiles
                .iter()
                .find(|profile| profile.reopening == reopening)
                .ok_or_else(|| eyre::eyre!("diagnostic profile absent"))?;
            assert_eq!(factory.len(), profile.bytes);
            assert_eq!(sha256(factory.as_bytes()), profile.sha256);
            assert_eq!(
                factory.bytes().filter(|byte| *byte == b'\n').count(),
                profile.lf_count
            );
            assert_eq!(factory.contains("SFMPanelReopenRecipe"), reopening);
            assert_eq!(factory.contains("import java.util.Objects;"), reopening);
            assert_eq!(factory.contains("public record Recipe"), reopening);
            assert_eq!(factory.contains("new Recipe(screenTypeId)"), reopening);
            assert_eq!(
                factory.contains("opener.open(context, new SFMInputDiagnosticsPanel())"),
                !reopening
            );
            if reopening {
                assert_eq!(factory.as_bytes(), fixture.raw[RAW[0].0]);
            }
            assert_eq!(
                fixture.render(PANEL, &context)?.unwrap().as_bytes(),
                fixture.raw[RAW[1].0]
            );
            assert_eq!(
                fixture.render(TAP, &context)?.unwrap().as_bytes(),
                fixture.raw[RAW[2].0]
            );
            assert_eq!(context.features["client_actions"], actions);
            assert!(
                !context.features["command_palette"] && !context.features["client_program_signing"]
            );
            assert!(
                !factory.contains("SFMClientActionExecutor")
                    && !factory.contains("grant")
                    && !factory.contains("machineHandler")
                    && !factory.contains("sendPacket")
            );
            let carrier = render_java_source(
                std::str::from_utf8(&fixture.core.read_source(TYPE)?)?,
                &context,
            )?;
            assert_eq!(carrier.contains("SFMPanelReopenRecipe recipe"), reopening);
            assert_eq!(carrier.contains("SFMScreenPanel panel"), !reopening);
        }
    }
    Ok(())
}

#[test]
fn owner_off_and_standalone_diagnostics_do_not_select_panel_on_any_target() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TEN {
        for requested in [
            vec![],
            vec!["workspace_panels"],
            vec!["client_actions"],
            vec!["input_diagnostics_screen"],
        ] {
            let context = fixture.context(target, &requested)?;
            for (path, _, _) in SOURCES {
                assert!(fixture.render(path, &context)?.is_none());
            }
        }
    }
    assert!(
        fixture
            .core
            .context("1.19.2", &["input_diagnostics_panel"])
            .is_err()
    );
    for target in TEN.into_iter().filter(|target| !D2.contains(target)) {
        assert!(
            fixture
                .context(target, &["input_diagnostics_panel"])
                .is_err()
        );
        assert!(
            fixture
                .context(target, &["workspace_panel_reopening"])
                .is_err()
        );
    }
    assert!(
        fixture
            .context("1.19.2", &["unapproved_input_diagnostics"])
            .is_err()
    );
    Ok(())
}

#[test]
fn real_collector_automatically_templates_java_false_and_keeps_six_owned_origins() -> Result<()> {
    let fixture = Fixture::load()?;
    let temporary = tempfile::tempdir()?;
    let root = temporary.path().join(CORE_ROOT);
    fixture.write_sources(&root)?;
    let mut metadata = fixture.metadata();
    for variants in metadata.source_rules.values_mut() {
        variants[0].template = false;
    }
    let inventory = discover_core_source_files(&root)?;
    let mut present = 0;
    for (key, _) in CONTEXTS {
        let context = fixture.historical_context(key)?;
        fixture.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (path, _, _) in SOURCES {
            match fixture.ledger.historical_membership[path][key].as_ref() {
                Some(oid) => {
                    let artifact = &artifacts[path];
                    let (_, body) = std::str::from_utf8(&artifact.output_bytes)?
                        .split_once('\n')
                        .ok_or_else(|| eyre::eyre!("diagnostic generated banner absent"))?;
                    assert_eq!(body.as_bytes(), fixture.raw[oid]);
                    assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{path}"));
                    assert!(artifact.overlay.is_none());
                    present += 1;
                }
                None => assert!(!artifacts.contains_key(path)),
            }
        }
    }
    assert_eq!(present, 6);
    Ok(())
}

#[test]
fn omitted_poison_is_not_read_and_malformed_selected_ownership_refuses() -> Result<()> {
    let fixture = Fixture::load()?;
    let temporary = tempfile::tempdir()?;
    let root = temporary.path().join(CORE_ROOT);
    for path in fixture.source.keys() {
        let file = root.join(path);
        fs::create_dir_all(
            file.parent()
                .ok_or_else(|| eyre::eyre!("diagnostic poison parent"))?,
        )?;
        fs::write(
            file,
            b"{% if features.unapproved_input_diagnostics %}\n\xff",
        )?;
    }
    let metadata = fixture.metadata();
    let inventory = discover_core_source_files(&root)?;
    for target in TEN {
        let context = fixture.context(target, &[])?;
        fixture.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for path in fixture.source.keys() {
            assert!(selected.omitted_paths.contains(path) && !artifacts.contains_key(path));
        }
    }
    let context = fixture.context("1.19.2", &["input_diagnostics_panel"])?;
    let bad = std::str::from_utf8(&fixture.source[FACTORY])?.replacen(
        "features.workspace_panel_reopening",
        "features.unapproved_input_diagnostics",
        1,
    );
    assert!(render_java_source(&bad, &context).is_err());
    let selected = select_core_inputs(&metadata, &context, &inventory)?;
    assert!(collect_core_artifacts(&root, &selected, &context).is_err());
    let mut malformed = metadata;
    malformed.source_rules.get_mut(FACTORY).unwrap()[0]
        .when
        .all_features
        .push("unapproved_input_diagnostics".to_owned());
    assert!(select_core_inputs(&malformed, &context, &inventory).is_err());
    Ok(())
}

#[test]
fn one_shared_edit_reaches_all_twelve_legal_reopening_outputs() -> Result<()> {
    let fixture = Fixture::load()?;
    let temporary = tempfile::tempdir()?;
    let root = temporary.path().join(CORE_ROOT);
    fixture.write_sources(&root)?;
    for row in &fixture.ledger.files {
        let original = std::str::from_utf8(&fixture.source[&row.path])?;
        fs::write(
            root.join(&row.path),
            original.replacen(
                &row.common_edit_anchor,
                &format!(
                    "// Isolated shared diagnostics edit.\n{}",
                    row.common_edit_anchor
                ),
                1,
            ),
        )?;
    }
    let metadata = fixture.metadata();
    let inventory = discover_core_source_files(&root)?;
    let mut count = 0;
    for target in D2 {
        for reopening in [false, true] {
            let mut requested = vec!["input_diagnostics_panel"];
            if reopening {
                requested.push("workspace_panel_reopening");
            }
            let context = fixture.context(target, &requested)?;
            fixture.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for row in &fixture.ledger.files {
                let original = fixture.render(&row.path, &context)?.unwrap();
                let (_, body) = std::str::from_utf8(&artifacts[&row.path].output_bytes)?
                    .split_once('\n')
                    .ok_or_else(|| eyre::eyre!("shared diagnostic banner absent"))?;
                assert_eq!(
                    body,
                    original.replacen(
                        &row.common_edit_anchor,
                        &format!(
                            "// Isolated shared diagnostics edit.\n{}",
                            row.common_edit_anchor
                        ),
                        1
                    )
                );
                count += 1;
            }
        }
    }
    assert_eq!(count, 12);
    for row in &fixture.ledger.files {
        assert_eq!(
            fixture.core.read_source(&row.path)?,
            fixture.source[&row.path]
        );
    }
    Ok(())
}

#[test]
fn genuine_subscription_lifecycle_and_clipboard_contracts_remain_raw_exact() -> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture.context("1.19.2", &["input_diagnostics_panel"])?;
    let panel = fixture.render(PANEL, &context)?.unwrap();
    let tap = fixture.render(TAP, &context)?.unwrap();
    assert_eq!(panel.as_bytes(), fixture.raw[RAW[1].0]);
    assert_eq!(tap.as_bytes(), fixture.raw[RAW[2].0]);
    assert!(panel.contains("private static final int EVENT_LIMIT = 500;")
        && panel.contains("while (events.size() > EVENT_LIMIT) events.remove(0);")
        && panel.contains("rawInputSubscription.close();\n        rawInputSubscription = null;")
        && panel.contains("Minecraft.getInstance().keyboardHandler.setClipboard(String.join(\"\\n\", events));"));
    let opened = panel
        .split("public void opened(")
        .nth(1)
        .unwrap()
        .split("    @Override")
        .next()
        .unwrap();
    assert!(
        opened.find("closeSubscription();").unwrap()
            < opened
                .find("SFMRawInputDiagnosticTap.subscribe(this::log)")
                .unwrap()
    );
    let closed = panel
        .split("public void closed()")
        .nth(1)
        .unwrap()
        .split("    @Override")
        .next()
        .unwrap();
    assert!(closed.contains("closeSubscription();"));
    for anchor in [
        "private synchronized Subscription add(Consumer<String> subscriber)",
        "private synchronized void remove(Consumer<String> subscriber)",
        "if (subscribers.isEmpty()) restore();",
        "for (Consumer<String> subscriber : List.copyOf(subscribers))",
        "previousKeyCallback.invoke(callbackWindow, key, scanCode, action, modifiers);",
        "previousScrollCallback.invoke(callbackWindow, xOffset, yOffset);",
        "GLFW.glfwSetKeyCallback(window, previousKeyCallback)",
        "GLFW.glfwSetScrollCallback(window, previousScrollCallback)",
        "if (owner == null) return;\n            owner.remove(subscriber);\n            owner = null;\n            subscriber = null;",
    ] {
        assert!(
            tap.contains(anchor),
            "original callback contract absent: {anchor}"
        );
    }
    assert!(
        !tap.contains("new Thread") && !tap.contains("ProcessBuilder") && !tap.contains("Socket")
    );
    Ok(())
}

#[test]
fn pure_workspace_source_is_selected_but_effect_registry_executor_remain_off() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in D2 {
        let context = fixture.context(target, &["input_diagnostics_panel"])?;
        let pure_paths = CURRENT_WORKSPACE_CARRIERS
            .into_iter()
            .map(|(path, _, _)| path)
            .chain([TYPE])
            .map(str::to_owned)
            .collect::<BTreeSet<_>>();
        let mut paths = pure_paths.clone();
        paths.extend(
            [
                "src/main/java/ca/teamdman/sfm/client/action/SFMClientAction.java",
                "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionExecutor.java",
                "src/main/java/ca/teamdman/sfm/client/registry/SFMClientActions.java",
            ]
            .into_iter()
            .map(str::to_owned),
        );
        let selected = select_core_inputs(&fixture.core.metadata, &context, &paths)?;
        for path in &pure_paths {
            assert!(
                selected.inputs.contains_key(path),
                "real workspace carrier omitted: {path}"
            );
        }
        for path in paths.difference(&pure_paths) {
            assert!(
                selected.omitted_paths.contains(path),
                "action effects leaked: {path}"
            );
        }
        assert!(
            !context.features["client_actions"] && !context.features["workspace_panel_reopening"]
        );
    }
    // Refuse every other rule shape. These are cloned test metadata only.
    for (path, _, _) in CURRENT_WORKSPACE_CARRIERS {
        let mut missing = fixture.core.metadata.clone();
        missing.source_rules.remove(path);
        assert!(validate_reviewed_current_workspace_carrier_rules(&missing).is_err());
        for mutation in 0_u8..8 {
            let mut changed = fixture.core.metadata.clone();
            let variants = changed.source_rules.get_mut(path).unwrap();
            match mutation {
                0 => variants[0].when.targets.push("1.19.2".to_owned()),
                1 => variants[0]
                    .when
                    .all_features
                    .push("client_actions".to_owned()),
                2 => variants[0]
                    .when
                    .any_features
                    .push("input_diagnostics_panel".to_owned()),
                3 => variants[0]
                    .when
                    .none_features
                    .push("client_program_signing".to_owned()),
                4 => variants[0].template = false,
                5 => variants[0].input.push_str(".unreviewed"),
                6 => variants[0].when.any_features.reverse(),
                _ => {
                    let duplicate = variants[0].clone();
                    variants.push(duplicate);
                }
            }
            assert!(
                validate_reviewed_current_workspace_carrier_rules(&changed).is_err(),
                "unreviewed carrier rule accepted: {path}, mutation {mutation}"
            );
        }
    }
    for (oid, length, digest, lf) in RAW {
        let original = &fixture.raw[oid];
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
            assert!(verify_raw(&changed, length, digest, lf).is_err());
        }
    }
    Ok(())
}
