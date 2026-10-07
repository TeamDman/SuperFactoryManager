//! ComputerCraft integration preservation through the actual authored-core selector.
//!
//! Prepared under an ignored target only. Root must promote the source cohort,
//! register exact sparse ownership and register this module before execution.
//! Historical Git is test evidence, never a production source fallback.
//! These tests do not establish Lua registration, Java compilation or gameplay.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::render_java_source;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;

const LEDGER: &str = "docs/tasks/sfm-core-computercraft-integration-slice.json";
const STAGE: &str =
    "platform/cli/sfm-propagate-changes/target/core-computercraft-integration-stage-v1";
const PREFIX: &str = "src/main/java/ca/teamdman/sfm/common/compat/computercraft/";
const OWNER: &str = "computercraft";
const PREREQUISITES: [&str; 3] = [
    "mod_event_filtering",
    "disk_readonly_access",
    "label_readonly_access",
];
const FLAGS: [&str; 4] = [
    OWNER,
    "mod_event_filtering",
    "disk_readonly_access",
    "label_readonly_access",
];
const MAX_SOURCE_BYTES: u64 = 1024 * 1024;
const PINNED_CONTEXTS: [(&str, &str); 20] = [
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
];
struct RawGolden {
    basename: &'static str,
    oid: &'static str,
    sha256: &'static str,
    bytes: usize,
    line_feeds: usize,
    targets: &'static [&'static str],
}
const RAW_GOLDENS: [RawGolden; 18] = [
    RawGolden {
        basename: "ComputerCraftIntegration.java",
        oid: "e7df2ab9b189b2128a765362defac533a8ef92a9",
        sha256: "sha256:ca75bdfe1d0e3c82e4b0f294ec203dbecbb5f8994bc42486f4f588c001167e95",
        bytes: 1721,
        line_feeds: 46,
        targets: &["1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3"],
    },
    RawGolden {
        basename: "ComputerCraftIntegration.java",
        oid: "d71d4d279c30d51c64f7897b28dcc3c22262b87b",
        sha256: "sha256:f6689488608bb7ba4f6165e35e41bc3f84c41e094762c9025fda09ff274a1ce3",
        bytes: 4250,
        line_feeds: 101,
        targets: &["1.20.4"],
    },
    RawGolden {
        basename: "ComputerCraftIntegration.java",
        oid: "efadeaa2ef64296356d496f2446415f03cdc69d1",
        sha256: "sha256:5a871250ed50bf94d43e26f76260dad5fbf4160dd2d4b9f0c01335515cdc53c6",
        bytes: 4358,
        line_feeds: 104,
        targets: &["1.21.0"],
    },
    RawGolden {
        basename: "ComputerCraftIntegration.java",
        oid: "09593f307b4a8ddf2338f62b4c261c8e375736af",
        sha256: "sha256:529d559627f5e50d339656c57b09b45136e36cbf7b991d1ac7e3d4405366ebb1",
        bytes: 4358,
        line_feeds: 104,
        targets: &["1.21.1", "26.1.2"],
    },
    RawGolden {
        basename: "SFMNetworkPeripheral.java",
        oid: "6516feaaba970c235023a77286eb9cf7e730cf2f",
        sha256: "sha256:0aeda37c34acd5b63f887d7ee76e0fb5eac9cca8dd12187a765c176a7acb3dcf",
        bytes: 1377,
        line_feeds: 48,
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    RawGolden {
        basename: "SFMNetworkPeripheralProvider.java",
        oid: "1f171721d5a2e121b45ca56b74c0f1afb3057bff",
        sha256: "sha256:db3978dd3589c6d805c26d2d8405fde627bb47e757c807a9958b5f3a76449b3b",
        bytes: 1206,
        line_feeds: 33,
        targets: &["1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3"],
    },
    RawGolden {
        basename: "SFMNetworkPeripheralProvider.java",
        oid: "c1ebf8a24a66b57485ca29a0c36fe334afa90251",
        sha256: "sha256:ec7a8e5930cc9be839127d837c7fa6a2b1c8830823e558731ef0fa45f9b8639d",
        bytes: 1407,
        line_feeds: 34,
        targets: &["1.20.4"],
    },
    RawGolden {
        basename: "SFMNetworkPeripheralProvider.java",
        oid: "673a4cf252fe87d0df681a3ce86c2ce430ae5f2c",
        sha256: "sha256:8297be92407a7be26d84bab248e22600f240031b2b46977251855620f658917f",
        bytes: 1407,
        line_feeds: 34,
        targets: &["1.21.0"],
    },
    RawGolden {
        basename: "SFMNetworkPeripheralProvider.java",
        oid: "fdf018e161ab11d15c75be042d29592a1e427aee",
        sha256: "sha256:8bc11266dc66482344e5c7b23dc9d1dc034a520ea743029ed74b930c656921b7",
        bytes: 1407,
        line_feeds: 34,
        targets: &["1.21.1", "26.1.2"],
    },
    RawGolden {
        basename: "SFMComputerCraftTurtleUpgrades.java",
        oid: "f6733335a32d96b1831be89994671076b9112089",
        sha256: "sha256:a52575d7350ce8d4a93ecdbd74d91ee6ae97545d41501c50d162d18f8df7d6a8",
        bytes: 1225,
        line_feeds: 32,
        targets: &["1.19.2", "1.19.4"],
    },
    RawGolden {
        basename: "SFMComputerCraftTurtleUpgrades.java",
        oid: "1374ecd51d7ec7bc2643695a9f448cdc7cd0dc6e",
        sha256: "sha256:0f853959ffef37e72bf18794b751abf9a6938dccfe6b5caa12f2ae1c3676c329",
        bytes: 1226,
        line_feeds: 32,
        targets: &["1.20", "1.20.1", "1.20.2", "1.20.3"],
    },
    RawGolden {
        basename: "SFMComputerCraftTurtleUpgrades.java",
        oid: "61d578cd7669a82bf4d241d98508e0a8b918f9ce",
        sha256: "sha256:fb02005d0058acbbed61c9d6cedea34d697e52a7ede79c130be34eddd06c81f6",
        bytes: 1469,
        line_feeds: 35,
        targets: &["1.20.4", "1.21.0"],
    },
    RawGolden {
        basename: "SFMComputerCraftTurtleUpgrades.java",
        oid: "f131ef18089f25843c0cae8746938b1a7bc4ff58",
        sha256: "sha256:c66b275e42dc028c0695dad6e4b720455d19765fa227fa5ae41af79c926c655f",
        bytes: 1426,
        line_feeds: 35,
        targets: &["1.21.1", "26.1.2"],
    },
    RawGolden {
        basename: "SFMLabelerTurtleUpgrade.java",
        oid: "373ff192493a862b01c5d4acd6d842a5bdc5b568",
        sha256: "sha256:0c589e0621a577b76f43d49de6f842b38106a518760ee0f7c2dbb41690a0266d",
        bytes: 1497,
        line_feeds: 39,
        targets: &["1.19.2"],
    },
    RawGolden {
        basename: "SFMLabelerTurtleUpgrade.java",
        oid: "94e227af4d3e337cd458b6aa8356a471381a716c",
        sha256: "sha256:3ea1cdf4ea1f738439f2be793fe9cfbcb343ea06f2581a706a05855569cf1f90",
        bytes: 1495,
        line_feeds: 39,
        targets: &["1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4"],
    },
    RawGolden {
        basename: "SFMLabelerTurtleUpgrade.java",
        oid: "46bbb34d966d1430886a8a7c1eb391eddee2735f",
        sha256: "sha256:fad9f1eeb01d1d9a0495647c03f8c907710df05fe377377604a62d4cfb452b14",
        bytes: 1572,
        line_feeds: 41,
        targets: &["1.21.0"],
    },
    RawGolden {
        basename: "SFMLabelerTurtleUpgrade.java",
        oid: "ed73deedd9050cc103bf995035ae051ca44d968d",
        sha256: "sha256:b5a1dacdc26cba9b9bdd60f570b9fa0834530d7483e562bbf965d7f7e839f8dc",
        bytes: 1562,
        line_feeds: 44,
        targets: &["1.21.1", "26.1.2"],
    },
    RawGolden {
        basename: "SFMTurtleLabelerPeripheral.java",
        oid: "dd20ef7b29498e3bdbc1ca89bf11dcea5d32e25d",
        sha256: "sha256:b0663b0ad210b6595e814c3459ed88ac5a84b8002080cd3e574298139bea8aac",
        bytes: 12975,
        line_feeds: 386,
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
];
const TEMPLATE_PINS: [(&str, usize, &str); 6] = [
    (
        "ComputerCraftIntegration.java",
        7937,
        "sha256:a2fce8be9c7bc28428a1bc458173791540f41241876214395eb1897bfe3444dc",
    ),
    (
        "SFMComputerCraftTurtleUpgrades.java",
        3744,
        "sha256:1f24b3981420a4b9d83b1c6369a77edd853272f9b9348e53ca6980ba5437f7de",
    ),
    (
        "SFMLabelerTurtleUpgrade.java",
        2993,
        "sha256:1cfbd92b7f8fb16f2bc34d7c3bfbd13aa8095ca3413dbace7de61c5393587074",
    ),
    (
        "SFMNetworkPeripheral.java",
        1377,
        "sha256:0aeda37c34acd5b63f887d7ee76e0fb5eac9cca8dd12187a765c176a7acb3dcf",
    ),
    (
        "SFMNetworkPeripheralProvider.java",
        3137,
        "sha256:5beb562ffbd3228d2097ac59cc411bcc8153a96638a24f6114e87c3400a1348a",
    ),
    (
        "SFMTurtleLabelerPeripheral.java",
        12975,
        "sha256:b0663b0ad210b6595e814c3459ed88ac5a84b8002080cd3e574298139bea8aac",
    ),
];

#[derive(Facet)]
struct SliceLedger {
    schema: String,
    status: String,
    scope: String,
    feature_owner: String,
    owner_prerequisites: Vec<String>,
    context_commits: BTreeMap<String, String>,
    files: BTreeMap<String, SliceFile>,
}
#[derive(Facet)]
struct SliceFile {
    original_path: String,
    core_input: String,
    staged_path: String,
    feature_owner: String,
    supported_targets: Vec<String>,
    required_features: Vec<String>,
    feature_prerequisites: Vec<String>,
    normalization: String,
    source_groups: Vec<SourceGroup>,
    template_bytes: usize,
    template_sha256: String,
    witnesses: BTreeMap<String, Option<String>>,
}
#[derive(Facet)]
struct SourceGroup {
    oid: String,
    sha256: String,
    bytes: usize,
    carriage_returns: usize,
    line_feeds: usize,
    final_lf: bool,
    targets: Vec<String>,
}
struct Fixture {
    core: CoreTestFixture,
    templates: BTreeMap<String, Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: SliceLedger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            MAX_SOURCE_BYTES,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-computercraft-integration-slice@1"
                && ledger.status
                    == "ignored_source_stage_pending_root_review_promotion_membership_and_tests"
                && ledger.scope
                    == "six_computercraft_registration_peripheral_and_turtle_integrations"
                && ledger.feature_owner == OWNER
                && same_names(&ledger.owner_prerequisites, &PREREQUISITES)
                && ledger.files.len() == 6,
            "ComputerCraft integration ledger scope changed"
        );
        let contexts = PINNED_CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            ledger.context_commits == contexts,
            "frozen integration contexts changed"
        );
        let targets = SUPPORTED_TARGETS
            .iter()
            .map(|(target, _)| *target)
            .collect::<Vec<_>>();
        let owner = core
            .features
            .0
            .get(OWNER)
            .ok_or_else(|| eyre::eyre!("ComputerCraft owner is missing"))?;
        ensure!(
            same_names(&owner.supported_targets, &targets)
                && same_names(&owner.requires, &PREREQUISITES),
            "ComputerCraft support/prerequisite contract changed"
        );
        for prerequisite in PREREQUISITES {
            let definition = core
                .features
                .0
                .get(prerequisite)
                .ok_or_else(|| eyre::eyre!("required integration provider owner is missing"))?;
            ensure!(
                same_names(&definition.supported_targets, &targets)
                    && definition.requires.is_empty(),
                "integration prerequisite support changed"
            );
        }
        let oids = RAW_GOLDENS.iter().map(|g| g.oid.to_owned()).collect();
        let raw = read_git_blobs(&core.repository, &oids)?;
        for golden in &RAW_GOLDENS {
            validate_raw(golden, &raw[golden.oid])?;
        }
        let mut templates = BTreeMap::new();
        for (basename, bytes, digest) in TEMPLATE_PINS {
            let path = format!("{PREFIX}{basename}");
            let evidence = ledger
                .files
                .get(&path)
                .ok_or_else(|| eyre::eyre!("missing exact integration ledger path {path}"))?;
            let groups = RAW_GOLDENS
                .iter()
                .filter(|g| g.basename == basename)
                .collect::<Vec<_>>();
            ensure!(
                evidence.original_path == format!("platform/minecraft/{path}")
                    && evidence.core_input == format!("{CORE_ROOT}/{path}")
                    && evidence.staged_path == format!("{STAGE}/{basename}")
                    && evidence.feature_owner == OWNER
                    && same_names(&evidence.supported_targets, &targets)
                    && evidence.required_features == [OWNER]
                    && same_names(&evidence.feature_prerequisites, &PREREQUISITES)
                    && evidence.normalization == "none"
                    && evidence.source_groups.len() == groups.len()
                    && evidence.template_bytes == bytes
                    && evidence.template_sha256 == digest,
                "integration source identity/ownership changed for {path}"
            );
            for golden in groups {
                let group = evidence
                    .source_groups
                    .iter()
                    .find(|row| row.oid == golden.oid)
                    .ok_or_else(|| eyre::eyre!("missing exact integration raw group"))?;
                ensure!(
                    group.sha256 == golden.sha256
                        && group.bytes == golden.bytes
                        && group.carriage_returns == 0
                        && group.line_feeds == golden.line_feeds
                        && group.final_lf
                        && same_names(&group.targets, golden.targets),
                    "historical integration raw group changed"
                );
            }
            let expected = PINNED_CONTEXTS
                .into_iter()
                .map(|(name, _)| {
                    let (environment, target) = name.split_once('/').expect("fixed context");
                    let oid =
                        (environment == "dev").then(|| golden_for(basename, target).oid.to_owned());
                    (name.to_owned(), oid)
                })
                .collect::<BTreeMap<_, _>>();
            ensure!(
                evidence.witnesses == expected,
                "integration witness membership changed"
            );
            let source = core.read_source(&path)?;
            ensure!(
                source.len() == bytes
                    && sha256(&source) == digest
                    && !source.contains(&b'\r')
                    && source.ends_with(b"\n")
                    && !source.ends_with(b"\n\n"),
                "actual authored integration bytes changed"
            );
            let rules =
                core.metadata.source_rules.get(&path).ok_or_else(|| {
                    eyre::eyre!("integration source needs exact sparse ownership")
                })?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == path
                    && same_names(&rules[0].when.targets, &targets)
                    && rules[0].when.all_features == [OWNER]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "actual integration sparse membership changed"
            );
            // Production treats every Java input as a template regardless of
            // the metadata bit used for non-Java project files.
            templates.insert(path, source);
        }
        Ok(Self {
            core,
            templates,
            raw,
        })
    }
    fn inventory(&self) -> BTreeSet<String> {
        self.templates.keys().cloned().collect()
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selection = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if let Some(input) = selection.inputs.get(path) {
            ensure!(
                input.input == path && !selection.omitted_paths.contains(path),
                "integration source alias or conflicting omission"
            );
            Ok(Some(
                render_java_source(std::str::from_utf8(&self.templates[path])?, context)?
                    .into_bytes(),
            ))
        } else {
            ensure!(
                selection.omitted_paths.contains(path),
                "missing explicit integration omission"
            );
            Ok(None)
        }
    }
    fn body(&self, basename: &str, context: &ProjectionContext) -> Result<String> {
        String::from_utf8(
            self.render(&format!("{PREFIX}{basename}"), context)?
                .ok_or_else(|| eyre::eyre!("expected selected integration source"))?,
        )
        .map_err(Into::into)
    }
}
fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn golden_for(basename: &str, target: &str) -> &'static RawGolden {
    RAW_GOLDENS
        .iter()
        .find(|golden| golden.basename == basename && golden.targets.contains(&target))
        .expect("each reviewed file has an exact raw group for every target")
}
fn validate_raw(golden: &RawGolden, source: &[u8]) -> Result<()> {
    ensure!(
        source.len() == golden.bytes
            && sha256(source) == golden.sha256
            && !source.contains(&b'\r')
            && source.iter().filter(|byte| **byte == b'\n').count() == golden.line_feeds
            && source.ends_with(b"\n")
            && !source.ends_with(b"\n\n"),
        "exact raw integration body changed"
    );
    let text = std::str::from_utf8(source)?;
    ensure!(
        !text.contains("{%") && !text.contains("{{"),
        "historical integration has unreviewed Liquid"
    );
    Ok(())
}

#[test]
fn six_computercraft_integration_witnesses_cover_one_hundred_twenty_actual_tree_cells() -> Result<()>
{
    let fixture = Fixture::load()?;
    let paths = TEMPLATE_PINS
        .iter()
        .map(|(basename, _, _)| format!("platform/minecraft/{PREFIX}{basename}"))
        .collect::<Vec<_>>();
    let mut present = 0;
    let mut cells = 0;
    for (name, commit) in PINNED_CONTEXTS {
        let (environment, target) = name.split_once('/').expect("fixed context");
        let output = frozen_git_command(&fixture.core.repository)
            .env("GIT_ALLOW_PROTOCOL", "")
            .env("GIT_TERMINAL_PROMPT", "0")
            .args([
                "-c",
                "protocol.allow=never",
                "-c",
                "core.fsmonitor=false",
                "ls-tree",
                commit,
                "--",
            ])
            .args(&paths)
            .output()
            .wrap_err("cannot read fixed offline integration tree")?;
        ensure!(
            output.status.success(),
            "offline integration tree query failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed integration witness row"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|expected| expected == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected integration tree member"
            );
        }
        let expected = if environment == "dev" {
            TEMPLATE_PINS
                .iter()
                .map(|(basename, _, _)| {
                    (
                        format!("platform/minecraft/{PREFIX}{basename}"),
                        golden_for(basename, target).oid.to_owned(),
                    )
                })
                .collect()
        } else {
            BTreeMap::new()
        };
        assert_eq!(
            actual, expected,
            "integration tree membership differs for {name}"
        );
        present += actual.len();
        cells += TEMPLATE_PINS.len();
    }
    assert_eq!((cells, present, cells - present), (120, 60, 60));
    Ok(())
}

#[test]
fn real_selector_and_liquid_renderer_reconstruct_all_twenty_integration_contexts() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut selected = 0;
    let mut omitted = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (environment, target) = name.split_once('/').expect("fixed context");
        let flags = if environment == "dev" {
            &FLAGS[..]
        } else {
            &[]
        };
        let mut context = fixture.core.context(target, flags)?;
        context.environment = environment.to_owned();
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
        for (basename, _, _) in TEMPLATE_PINS {
            let path = format!("{PREFIX}{basename}");
            if environment == "dev" {
                assert_eq!(
                    fixture.render(&path, &context)?.as_deref(),
                    Some(fixture.raw[golden_for(basename, target).oid].as_slice())
                );
                selected += 1;
            } else {
                assert!(fixture.render(&path, &context)?.is_none());
                omitted += 1;
            }
        }
    }
    assert_eq!((selected, omitted), (60, 60));
    Ok(())
}

#[test]
fn owner_off_masks_do_not_enable_integrations_and_catalog_labels_do_not_select_bodies() -> Result<()>
{
    let fixture = Fixture::load()?;
    let mut cells = 0;
    let mut selected = 0;
    for (target, _) in SUPPORTED_TARGETS {
        for mask in 0_u8..8 {
            let flags = PREREQUISITES
                .iter()
                .enumerate()
                .filter_map(|(bit, feature)| ((mask & (1 << bit)) != 0).then_some(*feature))
                .collect::<Vec<_>>();
            let context = fixture.core.context(target, &flags)?;
            for path in fixture.templates.keys() {
                assert!(fixture.render(path, &context)?.is_none());
                cells += 1;
            }
        }
        let context = fixture.core.context(target, &FLAGS)?;
        let mut renamed = context.clone();
        renamed.environment = "release".to_owned();
        renamed.projection_key = "independent/nested/integration-source-proof".to_owned();
        renamed.preset = "different-human-label".to_owned();
        for (basename, _, _) in TEMPLATE_PINS {
            let path = format!("{PREFIX}{basename}");
            let expected = &fixture.raw[golden_for(basename, target).oid];
            assert_eq!(
                fixture.render(&path, &context)?.as_deref(),
                Some(expected.as_slice())
            );
            assert_eq!(
                fixture.render(&path, &context)?,
                fixture.render(&path, &renamed)?
            );
            cells += 1;
            selected += 1;
        }
    }
    assert_eq!((cells, selected, cells - selected), (540, 60, 480));
    Ok(())
}

#[test]
fn integration_context_refuses_each_missing_provider_prerequisite_without_implicit_enabling()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut refused = 0;
    for (target, _) in SUPPORTED_TARGETS {
        for missing in PREREQUISITES {
            let flags = FLAGS
                .iter()
                .copied()
                .filter(|feature| *feature != missing)
                .collect::<Vec<_>>();
            let error = fixture
                .core
                .context(target, &flags)
                .expect_err("integration owner cannot silently enable required providers");
            assert!(error.to_string().contains(missing));
            refused += 1;
        }
        let mut context = fixture.core.context(target, &FLAGS)?;
        assert_eq!(context.features.remove(OWNER), Some(true));
        assert!(
            select_core_inputs(&fixture.core.metadata, &context, &fixture.inventory()).is_err()
        );
    }
    assert_eq!(refused, 30);
    Ok(())
}

#[test]
fn integration_version_decisions_and_existing_providers_keep_exact_source_contracts() -> Result<()>
{
    let fixture = Fixture::load()?;
    for source in fixture.templates.values() {
        let text = std::str::from_utf8(source)?;
        for forbidden in ["features.", "environment", "projection_key", "{% if", "{{"] {
            assert!(
                !text.contains(forbidden),
                "unreviewed integration body selector {forbidden}"
            );
        }
    }
    for (target, _) in SUPPORTED_TARGETS {
        let context = fixture.core.context(target, &FLAGS)?;
        let modern = matches!(target, "1.20.4" | "1.21.0" | "1.21.1" | "26.1.2");
        let type_api = matches!(target, "1.21.1" | "26.1.2");
        let integration = fixture.body("ComputerCraftIntegration.java", &context)?;
        assert_eq!(
            integration.contains("ForgeComputerCraftAPI.registerPeripheralProvider("),
            !modern
        );
        assert_eq!(
            integration.contains("event.enqueueWork(ComputerCraftIntegration::register);"),
            !modern
        );
        assert_eq!(
            integration
                .contains("event.enqueueWork(ComputerCraftIntegration::registerIntegration);"),
            modern
        );
        assert_eq!(integration.contains("RegisterCapabilitiesEvent"), modern);
        assert_eq!(integration.contains("RegisterTurtleModellersEvent"), modern);
        assert_eq!(
            integration.contains("ComputerCraftAPIClient.registerTurtleUpgradeModeller("),
            !modern
        );
        assert!(integration.contains("if (registered) return;"));
        assert!(
            integration
                .contains("ComputerCraftAPI.registerGenericSource(new SFMInventoryMethods());")
        );
        assert!(integration.contains("@SFMSubscribeEvent(requiredModId = \"computercraft\")"));
        if modern {
            for block in [
                "MANAGER",
                "TUNNELLED_MANAGER",
                "CABLE",
                "CABLE_FACADE",
                "FANCY_CABLE",
                "FANCY_CABLE_FACADE",
                "TOUGH_CABLE",
                "TOUGH_CABLE_FACADE",
                "TOUGH_FANCY_CABLE",
                "TOUGH_FANCY_CABLE_FACADE",
                "TUNNELLED_CABLE",
                "TUNNELLED_CABLE_FACADE",
                "TUNNELLED_FANCY_CABLE",
                "TUNNELLED_FANCY_CABLE_FACADE",
            ] {
                assert!(integration.contains(&format!("SFMBlocks.{block}.get()")));
            }
            assert!(
                integration.contains("normalTurtle == Blocks.AIR || advancedTurtle == Blocks.AIR")
            );
            assert!(integration.contains("? new InvWrapper(inventory)"));
            assert_eq!(
                integration
                    .contains("new ResourceLocation(ComputerCraftAPI.MOD_ID, \"turtle_normal\")"),
                target == "1.20.4"
            );
            assert_eq!(integration.contains("SFMResourceLocation.fromNamespaceAndPath(ComputerCraftAPI.MOD_ID, \"turtle_normal\")"), target != "1.20.4");
        }
        let peripheral = fixture.body("SFMNetworkPeripheralProvider.java", &context)?;
        assert_eq!(
            peripheral.contains("implements IPeripheralProvider"),
            !modern
        );
        assert_eq!(
            peripheral.contains("implements IBlockCapabilityProvider<IPeripheral, Direction>"),
            modern
        );
        assert_eq!(peripheral.contains("getPeripheral("), !modern);
        assert_eq!(peripheral.contains("getCapability("), modern);
        assert_eq!(peripheral.contains("return LazyOptional.empty();"), !modern);
        assert_eq!(peripheral.contains("return null;"), modern);
        assert!(peripheral.contains("level.isClientSide() || !CableNetwork.isCable(level, pos)"));
        assert!(peripheral.contains(
            "CableNetworkManager.getOrRegisterNetworkFromCablePosition(level, pos).isEmpty()"
        ));
        assert!(peripheral.contains("new SFMNetworkPeripheral(level, pos.immutable())"));
        let upgrades = fixture.body("SFMComputerCraftTurtleUpgrades.java", &context)?;
        assert_eq!(
            upgrades.contains("import net.minecraftforge.eventbus.api.IEventBus;"),
            !modern
        );
        assert_eq!(
            upgrades.contains("import net.neoforged.bus.api.IEventBus;"),
            modern
        );
        assert_eq!(
            upgrades.contains("TurtleUpgradeSerialiser.REGISTRY_ID"),
            matches!(target, "1.19.2" | "1.19.4")
        );
        assert_eq!(
            upgrades.contains("TurtleUpgradeSerialiser.registryId()"),
            matches!(target, "1.20" | "1.20.1" | "1.20.2" | "1.20.3")
        );
        assert_eq!(
            upgrades.contains("ITurtleUpgrade.serialiserRegistryKey()"),
            matches!(target, "1.20.4" | "1.21.0")
        );
        assert_eq!(upgrades.contains("ITurtleUpgrade.typeRegistry()"), type_api);
        let labeler = fixture.body("SFMLabelerTurtleUpgrade.java", &context)?;
        assert_eq!(
            labeler.contains("IUpgradeBase.getDefaultAdjective"),
            target == "1.19.2"
        );
        assert_eq!(
            labeler.contains(
                "UpgradeBase.getDefaultAdjective(SFMResourceLocation.fromNamespaceAndPath"
            ),
            target == "1.21.0"
        );
        assert_eq!(
            labeler.contains("public SFMLabelerTurtleUpgrade()"),
            type_api
        );
        assert_eq!(
            labeler.contains("public UpgradeType<SFMLabelerTurtleUpgrade> getType()"),
            type_api
        );
        assert!(labeler.contains("new ItemStack(SFMItems.LABEL_GUN.get())"));
        assert!(labeler.contains("return new SFMTurtleLabelerPeripheral(turtle);"));
        for (path, anchor) in [
            (
                "src/main/java/ca/teamdman/sfm/common/registry/SFMDeferredRegisterBuilder.java",
                "SFMDeferredRegister<T> build()",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/registry/SFMDeferredRegister.java",
                "register(",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/registry/SFMRegistryObject.java",
                "public T get()",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMBlocks.java",
                "TUNNELLED_FANCY_CABLE_FACADE",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMItems.java",
                "LABEL_GUN",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/compat/SFMModCompat.java",
                "isComputerCraftLoaded",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/event_bus/SFMSubscribeEvent.java",
                "requiredModId",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/util/SFMResourceLocation.java",
                "fromNamespaceAndPath",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/label/LabelGunActions.java",
                "public static LabelGunActionResult toggle(",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/label/LabelGunPlanTargets.java",
                "warnBecauseNoCableNeighbour",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/item/DiskItem.java",
                "getProgramStringReadOnly",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/item/LabelGunItem.java",
                "getViewModeReadOnly",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/label/LabelPositionHolder.java",
                "LabelPositionHolder toOwned()",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/util/BlockPosSet.java",
                "blockPosIterator",
            ),
            (
                "src/main/java/ca/teamdman/sfml/ast/Program.java",
                "public record Program",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/block_network/CableNetworkManager.java",
                "getOrRegisterNetworkFromCablePosition",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/block_network/CableNetwork.java",
                "getManagers",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/blockentity/ManagerBlockEntity.java",
                "getStateReadOnly",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/compat/computercraft/SFMItemHandleTarget.java",
                "current == expected[0]",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/compat/computercraft/SFMDiskHandle.java",
                "getProgram()",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/compat/computercraft/SFMInventoryMethods.java",
                "implements GenericSource",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/compat/computercraft/SFMLabelPositionHolderHandle.java",
                "saver.save(labels.toOwned())",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/compat/computercraft/SFMLabelGunHandle.java",
                "getActiveLabel()",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/compat/computercraft/SFMLabelDiscoveryHandle.java",
                "SFMBlockPosSetHandle",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/compat/computercraft/SFMManagerCollectionHandle.java",
                "SFMManagerHandle",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/compat/computercraft/SFMManagerHandle.java",
                "getStateReadOnly",
            ),
        ] {
            let selected = select_core_inputs(
                &fixture.core.metadata,
                &context,
                &BTreeSet::from([path.to_owned()]),
            )?;
            let input = selected
                .inputs
                .get(path)
                .ok_or_else(|| eyre::eyre!("owned direct integration provider omitted: {path}"))?;
            let raw = fixture.core.read_source(&input.input)?;
            assert!(
                render_java_source(std::str::from_utf8(&raw)?, &context)?.contains(anchor),
                "actual owned provider anchor changed: {path}"
            );
        }
    }
    Ok(())
}

#[test]
fn turtle_queue_validation_and_main_thread_delegate_bodies_remain_raw_exact() -> Result<()> {
    let fixture = Fixture::load()?;
    for (target, _) in SUPPORTED_TARGETS {
        let context = fixture.core.context(target, &FLAGS)?;
        let turtle = fixture.body("SFMTurtleLabelerPeripheral.java", &context)?;
        assert_eq!(turtle.matches("@LuaFunction(mainThread = true)").count(), 7);
        assert_eq!(
            turtle
                .matches("return turtle.executeCommand(access -> {")
                .count(),
            2
        );
        assert_eq!(
            turtle
                .matches("access.getInventory().setChanged();")
                .count(),
            2
        );
        for anchor in [
            "if (access.isRemoved())",
            "TurtleCommandResult.failure(\"target_changed\")",
            "TurtleCommandResult.failure(\"not_label_gun\")",
            "TurtleCommandResult.failure(\"not_manager\")",
            "TurtleCommandResult.failure(result.errorCode())",
            "if (contiguousOption != null && !(contiguousOption instanceof Boolean))",
            "throw new LuaException(\"invalid_direction\")",
            "throw new LuaException(\"invalid_options\")",
            "if (selectedSlot[0] < 0 || selectedSlot[0] >= inventory.getContainerSize())",
            "return current == expected[0] && !current.isEmpty()",
            "labels.save(stack);",
            "target.diskUpdated(stack);",
            "target.itemChanged(stack);",
            "DiskItem.compileAndUpdateErrorsAndWarnings(stack, null, true)",
            "case \"front\" -> turtle.getDirection();",
            "case \"up\" -> Direction.UP;",
            "case \"down\" -> Direction.DOWN;",
        ] {
            assert!(
                turtle.contains(anchor),
                "original turtle validation/delegation changed: {anchor}"
            );
        }
        assert_eq!(
            turtle.as_bytes(),
            fixture.raw[golden_for("SFMTurtleLabelerPeripheral.java", target).oid].as_slice()
        );
        let network = fixture.body("SFMNetworkPeripheral.java", &context)?;
        assert!(network.contains("public static final String TYPE = \"sfm_network\";"));
        assert!(network.contains("new SFMManagerCollectionHandle(level, cablePos)"));
        assert!(network.contains("level == otherNetwork.level"));
        assert!(network.contains("cablePos.equals(otherNetwork.cablePos)"));
    }
    Ok(())
}

#[test]
fn reviewed_unavailable_version_exclusions_remain_exact_and_are_not_compile_acceptance()
-> Result<()> {
    let fixture = Fixture::load()?;
    for (path, bytes, digest) in [
        (
            "build/features/review-only/gradle/source-excludes/1.20.2/main-java.txt",
            582,
            "sha256:e2a36f173bb0f279bf21e8ed73c7bf9a320c79791241b814d96fa81cd8bfda13",
        ),
        (
            "build/features/review-only/gradle/source-excludes/1.20.3/main-java.txt",
            582,
            "sha256:e78dc4164e36b0cc19b06514b9ec26c1442a8c9c402689149ec21a7973c8c7d6",
        ),
        (
            "build/features/review-only/1.21.0/gradle/source-excludes/1.21/main-java.txt",
            459,
            "sha256:b4aef9922d6835fc1084561b8b035e54f997ce6b6f5b35ee3651054ff5b43138",
        ),
        (
            "build/features/review-only/gradle/source-excludes/26.1.2/main-java.txt",
            604,
            "sha256:fea2a238cb0cd020336cc22fe5ae4d4768906a81c2366a2f644a212ee027a3e0",
        ),
    ] {
        let input = read_bounded(&checked_file(&fixture.core.core, path)?, MAX_SOURCE_BYTES)?;
        assert_eq!(input.len(), bytes);
        assert_eq!(sha256(&input), digest);
        assert!(
            std::str::from_utf8(&input)?.contains("ca/teamdman/sfm/common/compat/computercraft/**")
        );
    }
    Ok(())
}

#[test]
fn one_shared_peripheral_edit_reaches_all_ten_targets_only_in_an_isolated_authored_root()
-> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let core = temp.path().join(CORE_ROOT);
    for (path, source) in &fixture.templates {
        let output = core.join(path);
        fs::create_dir_all(
            output
                .parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(output, source)?;
    }
    let path = format!("{PREFIX}SFMNetworkPeripheral.java");
    let source = std::str::from_utf8(&fixture.templates[&path])?;
    const ANCHOR: &str = "public final class SFMNetworkPeripheral implements IPeripheral {";
    const EDIT: &str = "// Shared peripheral-provider authoring proof.\npublic final class SFMNetworkPeripheral implements IPeripheral {";
    ensure!(
        source.matches(ANCHOR).count() == 1,
        "shared integration edit anchor changed"
    );
    let edited = source.replacen(ANCHOR, EDIT, 1);
    fs::write(core.join(&path), edited.as_bytes())?;
    let inventory = discover_core_source_files(&core)?;
    assert_eq!(inventory, fixture.inventory());
    for (target, _) in SUPPORTED_TARGETS {
        let context = fixture.core.context(target, &FLAGS)?;
        let selected = select_core_inputs(&fixture.core.metadata, &context, &inventory)?;
        assert_eq!(selected.inputs[&path].input, path);
        let input = read_bounded(&checked_file(&core, &path)?, MAX_SOURCE_BYTES)?;
        assert_eq!(
            render_java_source(std::str::from_utf8(&input)?, &context)?,
            edited
        );
        let off = fixture.core.context(target, &[])?;
        assert!(
            select_core_inputs(&fixture.core.metadata, &off, &inventory)?
                .omitted_paths
                .contains(&path)
        );
    }
    for (path, original) in &fixture.templates {
        assert_eq!(fixture.core.read_source(path)?, *original);
    }
    Ok(())
}

#[test]
fn original_integration_raw_hashes_refuse_eol_eof_and_token_rewrites() -> Result<()> {
    let fixture = Fixture::load()?;
    for golden in &RAW_GOLDENS {
        let raw = &fixture.raw[golden.oid];
        let crlf = std::str::from_utf8(raw)?.replace('\n', "\r\n");
        assert!(validate_raw(golden, crlf.as_bytes()).is_err());
        assert!(validate_raw(golden, &raw[..raw.len() - 1]).is_err());
        let mut extra = raw.clone();
        extra.push(b'\n');
        assert!(validate_raw(golden, &extra).is_err());
        let mut token = raw.clone();
        token[0] = b'P';
        assert!(validate_raw(golden, &token).is_err());
    }
    Ok(())
}
