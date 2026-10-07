//! ComputerCraft handle preservation through the actual authored-core selector.
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

const LEDGER: &str = "docs/tasks/sfm-core-computercraft-handles-slice.json";
const STAGE: &str = "platform/cli/sfm-propagate-changes/target/core-computercraft-handles-stage-v1";
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
const RAW_GOLDENS: [RawGolden; 13] = [
    RawGolden {
        basename: "SFMBlockPosSetHandle.java",
        oid: "26f2acc9a9335334fb7f5ffb7129630f98f4c13c",
        sha256: "sha256:36f4ea9b5afa311ad813d98eb3ced9f26211af6c3d5966cdcc411c6fd6e077c3",
        bytes: 2428,
        line_feeds: 88,
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    RawGolden {
        basename: "SFMComputerCraftResults.java",
        oid: "7d98ba50e9fd0736c2ca7e36f147982053972328",
        sha256: "sha256:bb48fa152cf8d79daa0db9ce7c19cd952c5e3a48003fd347fbe1171914f654ce",
        bytes: 529,
        line_feeds: 27,
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    RawGolden {
        basename: "SFMDiskHandle.java",
        oid: "483bda2bf10204bf3f43824c926a54410743bac7",
        sha256: "sha256:96c0a12f4f16244a5db81437bf060c95c669b0292db31b2bbb1b82e85908c553",
        bytes: 2019,
        line_feeds: 59,
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    RawGolden {
        basename: "SFMInventoryMethods.java",
        oid: "5e5b15c934b3df937afaea517b46d278566e1f99",
        sha256: "sha256:d069c8e3262c28a514a909788ceb9ee01679d23053db117beb813ada441a7649",
        bytes: 1640,
        line_feeds: 54,
        targets: &["1.19.2"],
    },
    RawGolden {
        basename: "SFMItemHandleTarget.java",
        oid: "a75f97f6ade01980889756c6f73c7fbbab5a96ee",
        sha256: "sha256:776fe93aad9ec63730ca2b3c9ee75efd5d37f18372db4c1f69e65e3f6dc15edb",
        bytes: 5065,
        line_feeds: 152,
        targets: &["1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3"],
    },
    RawGolden {
        basename: "SFMLabelDiscoveryHandle.java",
        oid: "c1fb943b33e2687fa5b6673abcc6f555dafa3642",
        sha256: "sha256:00e1ff112df5ef678685978a7bcd6abe111e234a82f0a8e715773ada96469d9a",
        bytes: 1399,
        line_feeds: 49,
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    RawGolden {
        basename: "SFMLabelGunHandle.java",
        oid: "92e27c072521c3b21aed436dccd0362bc20d8304",
        sha256: "sha256:b9cf6dedc324bcd8f0fdcdd830156e0cdc446d5a3c59ed49916e28cb2bb17488",
        bytes: 3631,
        line_feeds: 99,
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    RawGolden {
        basename: "SFMLabelPositionHolderHandle.java",
        oid: "f38bd62286c4ed58a144ba6580d456a30d08dd25",
        sha256: "sha256:819e1731d1db636287122700580224d4e1c4522c24a4102bd18a156aff05f582",
        bytes: 7905,
        line_feeds: 267,
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    RawGolden {
        basename: "SFMManagerCollectionHandle.java",
        oid: "dd27abbb09d2e7866d1c9ab20bf8fcae3e3c3bb2",
        sha256: "sha256:a057faf90fc26007784a123468940b6256f74c54e1cb0f0d97163e48e7b219dd",
        bytes: 1298,
        line_feeds: 46,
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    RawGolden {
        basename: "SFMManagerHandle.java",
        oid: "e74015eed60e6e66c482edf9fc18749d108c2f1b",
        sha256: "sha256:a4bc4544b166251634cecaed20add0c44e5db8a6efaee1718665fe3f6c374f9f",
        bytes: 4138,
        line_feeds: 119,
        targets: &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
    },
    RawGolden {
        basename: "SFMInventoryMethods.java",
        oid: "fe3f97e3b5dfac96bc71731c215c94106226a6e6",
        sha256: "sha256:8047b61cb2153c96e411005c4977785e1dfb325f61f825c754c3f2e7b177ddd2",
        bytes: 1551,
        line_feeds: 53,
        targets: &["1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3"],
    },
    RawGolden {
        basename: "SFMInventoryMethods.java",
        oid: "6a2265442a85498e78107e92bf83423457b359ff",
        sha256: "sha256:76af817eafe72692478dd47d1ffe363a989fa29040881e62d42fbeef4ead05bf",
        bytes: 1555,
        line_feeds: 53,
        targets: &["1.20.4", "1.21.0", "1.21.1", "26.1.2"],
    },
    RawGolden {
        basename: "SFMItemHandleTarget.java",
        oid: "4eb89e35dcafb9f21ba073b2a40371e1a198da4d",
        sha256: "sha256:c5b6c779dd390c36f5478afd95943b6727a5466644bfa3b5b02860f0d8e488be",
        bytes: 5073,
        line_feeds: 152,
        targets: &["1.20.4", "1.21.0", "1.21.1", "26.1.2"],
    },
];
const TEMPLATE_PINS: [(&str, usize, &str); 10] = [
    (
        "SFMBlockPosSetHandle.java",
        2428,
        "sha256:36f4ea9b5afa311ad813d98eb3ced9f26211af6c3d5966cdcc411c6fd6e077c3",
    ),
    (
        "SFMComputerCraftResults.java",
        529,
        "sha256:bb48fa152cf8d79daa0db9ce7c19cd952c5e3a48003fd347fbe1171914f654ce",
    ),
    (
        "SFMDiskHandle.java",
        2019,
        "sha256:96c0a12f4f16244a5db81437bf060c95c669b0292db31b2bbb1b82e85908c553",
    ),
    (
        "SFMInventoryMethods.java",
        2210,
        "sha256:9caa991e7c3e49e929db8d54cd104aa86064a5ad20ea27f7bf6e2c9819f584d9",
    ),
    (
        "SFMItemHandleTarget.java",
        5334,
        "sha256:5b13987adbe3027b6f07ca786280b3430eb178635b24cd1c95655db30fbe31ee",
    ),
    (
        "SFMLabelDiscoveryHandle.java",
        1399,
        "sha256:00e1ff112df5ef678685978a7bcd6abe111e234a82f0a8e715773ada96469d9a",
    ),
    (
        "SFMLabelGunHandle.java",
        3631,
        "sha256:b9cf6dedc324bcd8f0fdcdd830156e0cdc446d5a3c59ed49916e28cb2bb17488",
    ),
    (
        "SFMLabelPositionHolderHandle.java",
        7905,
        "sha256:819e1731d1db636287122700580224d4e1c4522c24a4102bd18a156aff05f582",
    ),
    (
        "SFMManagerCollectionHandle.java",
        1298,
        "sha256:a057faf90fc26007784a123468940b6256f74c54e1cb0f0d97163e48e7b219dd",
    ),
    (
        "SFMManagerHandle.java",
        4138,
        "sha256:a4bc4544b166251634cecaed20add0c44e5db8a6efaee1718665fe3f6c374f9f",
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
            ledger.schema == "sfm:core-computercraft-handles-slice@1"
                && ledger.status
                    == "ignored_source_stage_pending_root_review_promotion_membership_and_tests"
                && ledger.scope == "ten_computercraft_item_label_manager_handle_providers"
                && ledger.feature_owner == OWNER
                && same_names(&ledger.owner_prerequisites, &PREREQUISITES)
                && ledger.files.len() == 10,
            "ComputerCraft handle ledger scope changed"
        );
        let contexts = PINNED_CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            ledger.context_commits == contexts,
            "frozen handle contexts changed"
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
                .ok_or_else(|| eyre::eyre!("required handle provider owner is missing"))?;
            ensure!(
                same_names(&definition.supported_targets, &targets)
                    && definition.requires.is_empty(),
                "handle prerequisite support changed"
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
                .ok_or_else(|| eyre::eyre!("missing exact handle ledger path {path}"))?;
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
                "handle source identity/ownership changed for {path}"
            );
            for golden in groups {
                let group = evidence
                    .source_groups
                    .iter()
                    .find(|row| row.oid == golden.oid)
                    .ok_or_else(|| eyre::eyre!("missing exact handle raw group"))?;
                ensure!(
                    group.sha256 == golden.sha256
                        && group.bytes == golden.bytes
                        && group.carriage_returns == 0
                        && group.line_feeds == golden.line_feeds
                        && group.final_lf
                        && same_names(&group.targets, golden.targets),
                    "historical handle raw group changed"
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
                "handle witness membership changed"
            );
            let source = core.read_source(&path)?;
            ensure!(
                source.len() == bytes
                    && sha256(&source) == digest
                    && !source.contains(&b'\r')
                    && source.ends_with(b"\n")
                    && !source.ends_with(b"\n\n"),
                "actual authored handle bytes changed"
            );
            let rules = core
                .metadata
                .source_rules
                .get(&path)
                .ok_or_else(|| eyre::eyre!("handle source needs exact sparse ownership"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == path
                    && same_names(&rules[0].when.targets, &targets)
                    && rules[0].when.all_features == [OWNER]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "actual handle sparse membership changed"
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
                "handle source alias or conflicting omission"
            );
            Ok(Some(
                render_java_source(std::str::from_utf8(&self.templates[path])?, context)?
                    .into_bytes(),
            ))
        } else {
            ensure!(
                selection.omitted_paths.contains(path),
                "missing explicit handle omission"
            );
            Ok(None)
        }
    }
    fn body(&self, basename: &str, context: &ProjectionContext) -> Result<String> {
        String::from_utf8(
            self.render(&format!("{PREFIX}{basename}"), context)?
                .ok_or_else(|| eyre::eyre!("expected selected handle source"))?,
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
        "exact raw handle body changed"
    );
    let text = std::str::from_utf8(source)?;
    ensure!(
        !text.contains("{%") && !text.contains("{{"),
        "historical handle has unreviewed Liquid"
    );
    Ok(())
}

#[test]
fn ten_computercraft_handle_witnesses_cover_two_hundred_actual_tree_cells() -> Result<()> {
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
            .wrap_err("cannot read fixed offline handle tree")?;
        ensure!(output.status.success(), "offline handle tree query failed");
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed handle witness row"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|expected| expected == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected handle tree member"
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
            "handle tree membership differs for {name}"
        );
        present += actual.len();
        cells += TEMPLATE_PINS.len();
    }
    assert_eq!((cells, present, cells - present), (200, 100, 100));
    Ok(())
}

#[test]
fn real_selector_and_liquid_renderer_reconstruct_all_twenty_handle_contexts() -> Result<()> {
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
    assert_eq!((selected, omitted), (100, 100));
    Ok(())
}

#[test]
fn owner_off_masks_do_not_enable_handles_and_catalog_labels_do_not_select_bodies() -> Result<()> {
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
        renamed.projection_key = "independent/nested/handle-source-proof".to_owned();
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
    assert_eq!((cells, selected, cells - selected), (900, 100, 800));
    Ok(())
}

#[test]
fn handle_context_refuses_each_missing_provider_prerequisite_without_implicit_enabling()
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
                .expect_err("handle owner cannot silently enable required providers");
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
fn handle_api_adapters_and_existing_direct_providers_keep_original_source_contracts() -> Result<()>
{
    let fixture = Fixture::load()?;
    for source in fixture.templates.values() {
        let text = std::str::from_utf8(source)?;
        for forbidden in ["features.", "environment", "projection_key", "{% if", "{{"] {
            assert!(
                !text.contains(forbidden),
                "unreviewed handle body selector {forbidden}"
            );
        }
    }
    for (target, _) in SUPPORTED_TARGETS {
        let context = fixture.core.context(target, &FLAGS)?;
        let neo = matches!(target, "1.20.4" | "1.21.0" | "1.21.1" | "26.1.2");
        let target_body = fixture.body("SFMItemHandleTarget.java", &context)?;
        assert_eq!(
            target_body.contains("net.neoforged.neoforge.items.IItemHandler;"),
            neo
        );
        assert_eq!(
            target_body.contains("net.minecraftforge.items.IItemHandler;"),
            !neo
        );
        assert!(target_body.contains("current == expected[0] && !current.isEmpty()"));
        assert!(
            target_body.contains("DiskItem.compileAndUpdateErrorsAndWarnings(stack, null, true)")
        );
        assert!(target_body.contains("catch (RuntimeException ignored)"));
        let inventory = fixture.body("SFMInventoryMethods.java", &context)?;
        assert_eq!(
            inventory.contains("public @Nonnull ResourceLocation id()"),
            target == "1.19.2"
        );
        assert_eq!(
            inventory.contains("public @Nonnull String id()"),
            target != "1.19.2"
        );
        assert!(inventory.contains("slot - 1"));
        let disk = fixture.body("SFMDiskHandle.java", &context)?;
        assert!(disk.contains("@LuaFunction(mainThread = true)"));
        assert!(disk.contains("DiskItem.getProgramStringReadOnly(resolution.stack())"));
        assert!(disk.contains("DiskItem.setProgram(stack, source);"));
        let labels = fixture.body("SFMLabelPositionHolderHandle.java", &context)?;
        assert!(labels.contains("saver.save(labels.toOwned())"));
        assert!(labels.contains("LabelPositionHolder.from(resolution.stack()).toOwned()"));
        let manager = fixture.body("SFMManagerHandle.java", &context)?;
        assert!(manager.contains("currentDisk == expectedDisk[0]"));
        assert!(manager.contains("managers.contains(expectedManager)"));
        assert!(manager.contains("manager.rebuildProgramAndUpdateDisk();"));
        for (path, anchor) in [
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
                "src/main/java/ca/teamdman/sfm/common/label/LabelGunPlanTargets.java",
                "warnBecauseNoCableNeighbour",
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
                "getNetworkFromCablePosition",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/block_network/CableNetwork.java",
                "getManagers",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/blockentity/ManagerBlockEntity.java",
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
                .ok_or_else(|| eyre::eyre!("owned direct handle provider omitted: {path}"))?;
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
fn one_shared_handle_edit_reaches_all_ten_targets_only_in_an_isolated_authored_root() -> Result<()>
{
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
    let path = format!("{PREFIX}SFMComputerCraftResults.java");
    let source = std::str::from_utf8(&fixture.templates[&path])?;
    const ANCHOR: &str = "final class SFMComputerCraftResults {";
    const EDIT: &str =
        "// Shared result-provider authoring proof.\nfinal class SFMComputerCraftResults {";
    ensure!(
        source.matches(ANCHOR).count() == 1,
        "shared handle edit anchor changed"
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
fn original_handle_raw_hashes_refuse_eol_eof_and_token_rewrites() -> Result<()> {
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
