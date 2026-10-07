//! Prepared source-only theme runtime/preview preservation regressions.
//! No Java compilation, filesystem theme mutation, GUI, clipboard or game effects.
//! Historical source witnesses are test inputs, never production renderer fallbacks.
#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CORE_ROOT;
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

const LEDGER: &str = "docs/tasks/sfm-core-theme-runtime-six-slice.json";
const PATHS: [&str; 6] = [
    "src/main/java/ca/teamdman/sfm/client/theme/SFMClientThemeLoader.java",
    "src/main/java/ca/teamdman/sfm/client/theme/SFMClientThemeService.java",
    "src/main/java/ca/teamdman/sfm/client/theme/SFMClientThemeTomlWriter.java",
    "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewRuleCodec.java",
    "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewThemeResolver.java",
    "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewCache.java",
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const FULL: [&str; 4] = [
    "client_theme",
    "theme_file_icon_defaults",
    "theme_file_icon_matching",
    "theme_preview_rules",
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
const TEMPLATE_PINS: [(&str, usize); 6] = [
    (
        "sha256:4cbb42b9880480a388b572b444024954796931ff0a8707c9997af15c9c4b8c65",
        13887,
    ),
    (
        "sha256:f977ae44e499950730d8a064ab3c4fc74f20e099a0d3db1d124937441cde000c",
        13329,
    ),
    (
        "sha256:81bee22bd4abd588412643614fdf843b50a7c38853a1fb4690dd2ddc6cbf03c1",
        3994,
    ),
    (
        "sha256:bb78bfbc0a18934427603a4a881917ef7aca576fb2da0c17d4c9c6dd799b59e5",
        5255,
    ),
    (
        "sha256:22c9eb034584a7349cf73877568522d3689ef0c595fdf9cf7cf55aaede6b6f66",
        3606,
    ),
    (
        "sha256:f6f6cada1367faeb34f5873912d3827d84c557602cc0896b87a458f336448c65",
        1297,
    ),
];
#[derive(Clone, Copy)]
struct Golden {
    path: &'static str,
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}
const RAW: [Golden; 11] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/theme/SFMClientThemeLoader.java",
        oid: "e4b392095061b0f92b5874ca48f794f2b2ff1f20",
        digest: "sha256:533ea67ef9b421b23b4fdf0a4325c5a4ebbc68a3b361cbbb71ccbd68b1764a55",
        bytes: 10717,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/theme/SFMClientThemeService.java",
        oid: "4ee556e70a8d5fa2d1620567b277aa6a1ff3135a",
        digest: "sha256:037c9546e14b96995c147015bf118ea156e1f0f2f7b451c219c5b672db830a35",
        bytes: 10769,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/theme/SFMClientThemeTomlWriter.java",
        oid: "eaa895e1c5a55f905bd230c5c40b0dcfdf3ae539",
        digest: "sha256:92adc83618cc7cedc2a97b1ea3e915d6e7ccaf8ea13e723ec29477a238c68239",
        bytes: 2711,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewCache.java",
        oid: "f5cc245fe979506ac00bb748b383a6e0574752a7",
        digest: "sha256:f6f6cada1367faeb34f5873912d3827d84c557602cc0896b87a458f336448c65",
        bytes: 1297,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewRuleCodec.java",
        oid: "eef6e0608903b9d15f48c01a7d416a0115be8e55",
        digest: "sha256:bb78bfbc0a18934427603a4a881917ef7aca576fb2da0c17d4c9c6dd799b59e5",
        bytes: 5255,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewThemeResolver.java",
        oid: "71641dfee3f2f79edf4eb75d1ac109009d2469a2",
        digest: "sha256:22c9eb034584a7349cf73877568522d3689ef0c595fdf9cf7cf55aaede6b6f66",
        bytes: 3606,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/theme/SFMClientThemeLoader.java",
        oid: "eb0cb1790e3885977b400413f98bd156f1dd7eda",
        digest: "sha256:a3c3aec6c497325974dd012a6c4996d534658a818f19dd4705816f17a006d651",
        bytes: 7649,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/theme/SFMClientThemeService.java",
        oid: "e154423dde4c73aa4ce745e84f7c8f28f1d43936",
        digest: "sha256:b829601316f593808500f0ca5d581645d77d6caafa67adaf803258b155a057e6",
        bytes: 5911,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/theme/SFMClientThemeTomlWriter.java",
        oid: "db2bfea558c8d5a665922b6d6e058427b623ee16",
        digest: "sha256:36d1d2754aafce104e396e0f42100bb41a8035ad48fffaf6eb2c1d8904e612ad",
        bytes: 2111,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/theme/SFMClientThemeLoader.java",
        oid: "cdd8d9c48e59e5781a386c55d953f04e41f7c132",
        digest: "sha256:e1fef8a0e90748ac018c41f041dfab8523f3f66c99a9182a2dd6ecdc4240104c",
        bytes: 7716,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/theme/SFMClientThemeLoader.java",
        oid: "ba04298101a6ff3c9a892a4411005ca7c13da8c6",
        digest: "sha256:e04df3ce4537a5cb6cc9172499e5322a32613e085918afe67bd1392caf3f958e",
        bytes: 7698,
    },
];
const PROVIDERS: [(&str, &str, usize); 15] = [
    (
        "src/main/java/ca/teamdman/sfm/client/theme/SFMThemeLoadResult.java",
        "sha256:9db75a19c8e4380c6b4841a16b18168605412451d4c3b1acb7ba65c3b9e76301",
        412,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/theme/SFMColourRole.java",
        "sha256:c5c4729339582fb8c16c57be099076c07b69e595d560dec94ffd765d17313b9c",
        1454,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/theme/SFMSyntaxStyle.java",
        "sha256:b27df19e7b3cf9cbac8b4a9e5f5390db77e13e4b9459a81d3b00b4b1c789ca1c",
        393,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewRules.java",
        "sha256:75fd2d25401600f62264b98f6b225c29066f1a429cae4ef02fcb8ac798c0bade",
        6430,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewExpression.java",
        "sha256:f662a509b7ce4ee23d0f7c1a79898307a58eacd1b2aefbb40a3d099b17728879",
        8267,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewRegistry.java",
        "sha256:7fb1eae67afc031b16da59b9dd561f56951f96d09f2d50c334ed40486fda6fa9",
        1642,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewSubject.java",
        "sha256:48c4724b9161e17cbf6ca392472eb2dc4222331a179106c701eca97066744d15",
        3017,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewOperators.java",
        "sha256:dbae593474c89b5effce854a912a04235ebc524bbd85fb2500468ee03008c1d3",
        5977,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewDefaultNames.java",
        "sha256:57d53265f4fdd98ff9e426a722496629ba2d669e2a9dbff03740995625489ed5",
        5065,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewThemeTarget.java",
        "sha256:a0da8bdc0e54079027eeda262fd39661363a87b7f3ad2074ae0895a0f3ad95a7",
        1320,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/presentation/SFMItemIcon.java",
        "sha256:ee57563f45ffb0192c4fb3db2775a5dbcdff590fc896d17923e735a971aa3abb",
        2393,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/explorer/SFMPath.java",
        "sha256:bc943185664f9c8acd7501660601896aa4e8944334d2e61ae12ca53e8521654b",
        22104,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/explorer/lazy/SFMExplorerEntry.java",
        "sha256:42fd2bbed2630cf2a5eee4467197506ab44aea53377cd2929dada33646ff9d5f",
        6197,
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/theme/SFMClientTheme.java",
        "sha256:fff0ddb181cd8d05ac9457f799a6b8857494b3bdd35d790ab550d9acf90ccbfb",
        12717,
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/util/SFMResourceLocation.java",
        "sha256:0f2ca1de996283914081d4353fa12a7bede98db310c5e613b9540e038a4bb60b",
        2854,
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    files: Vec<Leaf>,
    raw: Vec<RawWitness>,
    contexts: Vec<Cell>,
}
#[derive(Facet)]
struct Leaf {
    path: String,
    core_path: String,
    template_sha256: String,
    template_bytes: usize,
    membership: Membership,
}
#[derive(Facet)]
struct Membership {
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
    none_features: Vec<String>,
}
#[derive(Facet)]
struct RawWitness {
    path: String,
    raw_blob: String,
    raw_sha256: String,
    raw_bytes: usize,
    mode: String,
    line_endings: String,
    terminal_newline: String,
    normalization: String,
    contexts: Vec<String>,
}
#[derive(Facet)]
struct Cell {
    context: String,
    source_commit: String,
    minecraft_version: String,
    explicit_registered_features: Vec<String>,
    features_origin: String,
    members: BTreeMap<String, Option<String>>,
}
fn same(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn actual_mc(target: &str) -> &str {
    if target == "1.21.0" { "1.21" } else { target }
}
fn historical_features(environment: &str, target: &str) -> &'static [&'static str] {
    if environment != "dev" {
        &[]
    } else if d2(target) {
        &FULL
    } else {
        &["client_theme"]
    }
}
fn expected_oid(index: usize, target: &str) -> &'static str {
    match index {
        0 if d2(target) => "e4b392095061b0f92b5874ca48f794f2b2ff1f20",
        0 if target == "26.1.2" => "ba04298101a6ff3c9a892a4411005ca7c13da8c6",
        0 if matches!(target, "1.21.0" | "1.21.1") => "cdd8d9c48e59e5781a386c55d953f04e41f7c132",
        0 => "eb0cb1790e3885977b400413f98bd156f1dd7eda",
        1 if d2(target) => "4ee556e70a8d5fa2d1620567b277aa6a1ff3135a",
        1 => "e154423dde4c73aa4ce745e84f7c8f28f1d43936",
        2 if d2(target) => "eaa895e1c5a55f905bd230c5c40b0dcfdf3ae539",
        2 => "db2bfea558c8d5a665922b6d6e058427b623ee16",
        3 => "eef6e0608903b9d15f48c01a7d416a0115be8e55",
        4 => "71641dfee3f2f79edf4eb75d1ac109009d2469a2",
        5 => "f5cc245fe979506ac00bb748b383a6e0574752a7",
        _ => unreachable!("fixed six-path slice"),
    }
}
fn selected(index: usize, target: &str, context: &ProjectionContext) -> bool {
    if index < 3 {
        context.features["client_theme"]
    } else {
        d2(target) && context.features["theme_preview_rules"]
    }
}
fn validate_bytes(body: &[u8], bytes: usize, digest: &str) -> Result<()> {
    ensure!(
        body.len() == bytes && sha256(body) == digest,
        "source pin changed"
    );
    std::str::from_utf8(body)?;
    Ok(())
}
fn validate_raw(golden: Golden, bytes: &[u8]) -> Result<()> {
    validate_bytes(bytes, golden.bytes, golden.digest)?;
    ensure!(
        !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.ends_with(b"\n\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw source framing changed"
    );
    Ok(())
}
struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            1024 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core_theme_runtime_six_slice@1"
                && ledger.normalization == "none_raw_exact_lf_with_exactly_one_terminal_lf"
                && ledger.files.len() == 6
                && ledger.raw.len() == 11
                && ledger.contexts.len() == 20,
            "theme ledger scope changed"
        );
        for owner in FULL {
            let contract = &core.features.0[owner];
            let expected_targets = if owner == "client_theme" {
                &TARGETS[..]
            } else {
                &TARGETS[..2]
            };
            let expected_requires: &[&str] = if owner == "client_theme" {
                &[]
            } else {
                &["client_theme"]
            };
            ensure!(
                same(&contract.supported_targets, expected_targets)
                    && same(&contract.requires, expected_requires),
                "existing theme owner changed"
            );
        }
        let mut seen = BTreeSet::new();
        for leaf in ledger.files {
            let index = PATHS
                .iter()
                .position(|p| *p == leaf.path)
                .ok_or_else(|| eyre::eyre!("unexpected theme path"))?;
            let owner = if index < 3 {
                "client_theme"
            } else {
                "theme_preview_rules"
            };
            let targets: &[&str] = if index < 3 { &[] } else { &TARGETS[..2] };
            ensure!(
                seen.insert(leaf.path.clone())
                    && leaf.core_path == format!("{CORE_ROOT}/{}", leaf.path)
                    && leaf.template_sha256 == TEMPLATE_PINS[index].0
                    && leaf.template_bytes == TEMPLATE_PINS[index].1
                    && same(&leaf.membership.targets, targets)
                    && same(&leaf.membership.all_features, &[owner])
                    && leaf.membership.any_features.is_empty()
                    && leaf.membership.none_features.is_empty(),
                "theme template/membership ledger changed"
            );
            let rules = &core.metadata.source_rules[&leaf.path];
            ensure!(
                rules.len() == 1
                    && rules[0].input == leaf.path
                    && rules[0].template
                    && same(&rules[0].when.targets, targets)
                    && same(&rules[0].when.all_features, &[owner])
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "theme source rule changed"
            );
            validate_bytes(
                &core.read_source(&leaf.path)?,
                leaf.template_bytes,
                &leaf.template_sha256,
            )?;
        }
        ensure!(
            seen == PATHS.into_iter().map(str::to_owned).collect(),
            "theme path scope changed"
        );
        let mut raw_seen = BTreeSet::new();
        for witness in ledger.raw {
            let golden = RAW
                .iter()
                .find(|r| r.oid == witness.raw_blob)
                .ok_or_else(|| eyre::eyre!("unexpected raw theme blob"))?;
            let index = PATHS
                .iter()
                .position(|p| *p == golden.path)
                .expect("fixed raw path");
            let expected_contexts = COMMITS
                .iter()
                .filter_map(|(name, _)| {
                    let (environment, target) = name.split_once('/').expect("fixed context");
                    let present = environment == "dev" && (index < 3 || d2(target));
                    (present && expected_oid(index, target) == golden.oid).then_some(*name)
                })
                .collect::<Vec<_>>();
            ensure!(
                raw_seen.insert(witness.raw_blob.clone())
                    && witness.path == golden.path
                    && witness.raw_sha256 == golden.digest
                    && witness.raw_bytes == golden.bytes
                    && witness.mode == "100644"
                    && witness.line_endings == "lf"
                    && witness.terminal_newline == "exactly_one_lf"
                    && witness.normalization == "none"
                    && same(&witness.contexts, &expected_contexts),
                "raw theme variant ledger changed"
            );
        }
        let mut cells = BTreeSet::new();
        for cell in ledger.contexts {
            let (environment, target) = cell
                .context
                .split_once('/')
                .ok_or_else(|| eyre::eyre!("invalid theme context"))?;
            let features = historical_features(environment, target);
            ensure!(
                cells.insert(cell.context.clone())
                    && COMMITS
                        .iter()
                        .find(|(c, _)| *c == cell.context)
                        .map(|(_, h)| *h)
                        == Some(cell.source_commit.as_str())
                    && cell.minecraft_version == actual_mc(target)
                    && same(&cell.explicit_registered_features, features)
                    && cell.features_origin
                        == "reviewed_current_explicit_reconstruction_not_historical_manifest"
                    && cell
                        .members
                        .keys()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>()
                        == PATHS.into_iter().collect(),
                "theme historical cell changed"
            );
            let context = core.context(target, features)?;
            ensure!(
                context.minecraft_version == actual_mc(target),
                "real catalog MC alias changed"
            );
            for (index, path) in PATHS.iter().enumerate() {
                let present = selected(index, target, &context);
                ensure!(
                    cell.members[*path].as_deref()
                        == present.then_some(expected_oid(index, target)),
                    "theme historical membership changed"
                );
            }
        }
        let raw = read_git_blobs(
            &core.repository,
            &RAW.iter().map(|g| g.oid.to_owned()).collect(),
        )?;
        for golden in RAW {
            validate_raw(golden, &raw[golden.oid])?;
        }
        for (path, digest, bytes) in PROVIDERS {
            validate_bytes(&core.read_source(path)?, bytes, digest)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|p| inventory.contains(*p))
                && PROVIDERS.iter().all(|(p, _, _)| inventory.contains(*p)),
            "theme/provider absent"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let choice = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = choice.inputs.get(PATHS[index]) else {
            ensure!(
                choice.omitted_paths.contains(PATHS[index]),
                "theme omitted without rule"
            );
            return Ok(None);
        };
        ensure!(
            input.input == PATHS[index] && input.template,
            "alternate theme input selected"
        );
        let bytes = self.core.read_source(PATHS[index])?;
        validate_bytes(&bytes, TEMPLATE_PINS[index].1, TEMPLATE_PINS[index].0)?;
        Ok(Some(
            render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes(),
        ))
    }
    fn assert_context(&self, target: &str, context: &ProjectionContext) -> Result<()> {
        let choice = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        for index in 0..6 {
            let body = self.render(index, context)?;
            assert_eq!(
                body.is_some(),
                selected(index, target, context),
                "{}/{}",
                PATHS[index],
                target
            );
            if let Some(bytes) = body {
                let text = std::str::from_utf8(&bytes)?;
                assert!(!text.contains("{%") && !text.contains("{#"));
                if !context.features["theme_preview_rules"] {
                    assert!(
                        !text.contains("SFMItemstackPreview"),
                        "preview reference survived owner-off: {}",
                        PATHS[index]
                    );
                    assert!(
                        !text.contains(".explicitFileIcons()") && !text.contains(".previewRules()")
                    );
                }
            }
        }
        if context.features["client_theme"] {
            for path in [
                PROVIDERS[0].0,
                PROVIDERS[1].0,
                PROVIDERS[2].0,
                PROVIDERS[10].0,
                THEME_PROVIDER,
            ] {
                assert!(
                    choice.inputs.contains_key(path),
                    "actual base theme provider absent: {path}"
                );
            }
        }
        if context.features["theme_preview_rules"] {
            for (path, _, _) in PROVIDERS {
                assert!(
                    choice.inputs.contains_key(path),
                    "actual preview provider absent: {path}"
                );
            }
            assert!(
                !context.features["client_actions"] && !context.features["workspace_panels"],
                "preview proof must not inherit unrelated UI/action providers"
            );
        }
        Ok(())
    }
}
const THEME_PROVIDER: &str = "src/main/java/ca/teamdman/sfm/client/theme/SFMClientTheme.java";

#[test]
fn theme_six_reconstructs_exact_120_frozen_cells_and_offline_git_trees() -> Result<()> {
    let fixture = Fixture::load()?;
    // Deliberately exercise the production scanner on all six complete proposals.
    // The previous equality-if spelling is outside its accepted Boolean grammar.
    assert!(
        super::directive_scanner::scan("{% if minecraft_version == \"1.19.2\" %}\n{% endif %}\n",)
            .is_err()
    );
    let full_d2 = fixture.core.context("1.19.2", &FULL)?;
    for index in 0..6 {
        let bytes = fixture.core.read_source(PATHS[index])?;
        let source = std::str::from_utf8(&bytes)?;
        match super::directive_scanner::scan(source)? {
            super::directive_scanner::ScannedSource::Template(scanned) => {
                assert!(index < 3, "raw preview leaf gained directives");
                assert_eq!(
                    scanned
                        .referenced_selectors
                        .keys()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>(),
                    BTreeSet::from(["minecraft_version"])
                );
                let expected_conditions = if index == 1 {
                    BTreeSet::from([
                        "features.theme_preview_rules",
                        "features.theme_file_icon_defaults",
                    ])
                } else {
                    BTreeSet::from(["features.theme_preview_rules"])
                };
                assert_eq!(
                    scanned
                        .referenced_conditions
                        .keys()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>(),
                    expected_conditions
                );
                assert!(!source.contains("minecraft_version =="));
                assert_eq!(
                    source.matches("{% case minecraft_version %}").count(),
                    [17, 15, 5][index]
                );
            }
            super::directive_scanner::ScannedSource::Identity(raw) => {
                assert!(index >= 3, "versioned theme proposal lost its directives");
                assert_eq!(raw, source);
            }
        }
        // This invokes the real render path (scanner plus Liquid) when admitted.
        assert_eq!(
            fixture.render(index, &full_d2)?.as_deref(),
            Some(fixture.raw[expected_oid(index, "1.19.2")].as_slice())
        );
    }
    let (mut present, mut absent) = (0, 0);
    for (name, commit) in COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed context");
        let context = fixture
            .core
            .context(target, historical_features(environment, target))?;
        fixture.assert_context(target, &context)?;
        let mut command = frozen_git_command(&fixture.core.repository);
        command.args([
            "-c",
            "protocol.allow=never",
            "-c",
            "core.fsmonitor=false",
            "ls-tree",
            "-r",
            commit,
            "--",
        ]);
        for path in PATHS {
            command.arg(format!("platform/minecraft/{path}"));
        }
        let output = command.output()?;
        ensure!(
            output.status.success() && output.stdout.len() <= 4096,
            "bounded theme tree query failed"
        );
        let mut found = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("invalid tree line"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3 && fields[0] == "100644" && fields[1] == "blob",
                "invalid tree type/mode"
            );
            ensure!(
                found
                    .insert(path.to_owned(), fields[2].to_owned())
                    .is_none(),
                "duplicate tree path"
            );
        }
        for index in 0..6 {
            let expected = selected(index, target, &context).then_some(expected_oid(index, target));
            assert_eq!(
                found
                    .get(&format!("platform/minecraft/{}", PATHS[index]))
                    .map(String::as_str),
                expected
            );
            if let Some(oid) = expected {
                assert_eq!(
                    fixture.render(index, &context)?.as_deref(),
                    Some(fixture.raw[oid].as_slice())
                );
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (36, 84));
    Ok(())
}

#[test]
fn theme_six_twenty_feature_off_controls_omit_before_source_reads() -> Result<()> {
    let mut fixture = Fixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture
        .core
        .core
        .join("deliberately_absent_theme_six_sources");
    let mut cells = 0;
    for key in catalog.catalog.0.keys() {
        let context = fixture.core.historical_feature_off_catalog_context(key)?;
        for index in 0..6 {
            assert!(fixture.render(index, &context)?.is_none());
            cells += 1;
        }
    }
    assert_eq!(cells, 120);
    Ok(())
}

#[test]
fn theme_six_34_independent_legal_owner_masks_preserve_real_provider_closure() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut masks = 0;
    for target in TARGETS {
        fixture.assert_context(target, &fixture.core.context(target, &[])?)?;
        masks += 1;
        if d2(target) {
            for bits in 0..8 {
                let mut flags = vec!["client_theme"];
                for (bit, owner) in FULL[1..].iter().enumerate() {
                    if bits & (1 << bit) != 0 {
                        flags.push(*owner);
                    }
                }
                let context = fixture.core.context(target, &flags)?;
                fixture.assert_context(target, &context)?;
                for unrelated in [
                    "client_actions",
                    "workspace_panels",
                    "file_explorer",
                    "registry_explorer",
                ] {
                    assert!(
                        !context.features[unrelated],
                        "theme mask invented {unrelated}"
                    );
                }
                masks += 1;
            }
        } else {
            fixture.assert_context(target, &fixture.core.context(target, &["client_theme"])?)?;
            masks += 1;
        }
    }
    assert_eq!(masks, 34);
    Ok(())
}

#[test]
fn theme_six_owner_off_omits_preview_members_but_retains_d2_input_bounds() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        let base = fixture.core.context(target, &["client_theme"])?;
        let service = String::from_utf8(fixture.render(1, &base)?.expect("base service"))?;
        for marker in [
            "MAX_THEME_BYTES=1024*1024",
            "readNBytes(MAX_THEME_BYTES+1)",
            "newDecoder().decode(java.nio.ByteBuffer.wrap(bytes))",
            "Theme exceeds 1 MiB limit",
            "@FunctionalInterface interface ThemeWriter",
            "writeAtomically",
        ] {
            assert!(
                service.contains(marker),
                "D2 base lost input/write contract: {marker}"
            );
        }
        for absent in [
            "record Authority",
            "activeAuthority()",
            "mutatePreviewRules(",
            "authority=",
        ] {
            assert!(
                !service.contains(absent),
                "preview-off service retained {absent}"
            );
        }
        let defaults = fixture
            .core
            .context(target, &["client_theme", "theme_file_icon_defaults"])?;
        let preview = fixture
            .core
            .context(target, &["client_theme", "theme_preview_rules"])?;
        let defaults_service =
            String::from_utf8(fixture.render(1, &defaults)?.expect("defaults service"))?;
        let preview_service =
            String::from_utf8(fixture.render(1, &preview)?.expect("preview service"))?;
        assert!(defaults_service.contains("fallback = \"minecraft:barrel\""));
        assert!(!preview_service.contains("fallback = \"minecraft:barrel\""));
        assert!(!defaults_service.contains("mutatePreviewRules("));
        assert!(preview_service.contains("mutatePreviewRules("));
        let loader = String::from_utf8(fixture.render(0, &base)?.expect("loader"))?;
        assert!(loader.contains("new SFMClientTheme(colours, syntax, fileIcons, actionIcons)"));
        assert!(loader.contains("inline icon table"));
    }
    Ok(())
}

#[test]
fn theme_six_actual_provider_members_cover_preview_only_without_action_or_screen_features()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        let context = fixture
            .core
            .context(target, &["client_theme", "theme_preview_rules"])?;
        fixture.assert_context(target, &context)?;
        let theme = render_java_source(
            std::str::from_utf8(&fixture.core.read_source(THEME_PROVIDER)?)?,
            &context,
        )?;
        for marker in [
            "List<SFMItemstackPreviewRules.Rule> previewRules",
            "Set<String> explicitFileIcons",
            "public SFMClientTheme(Map<SFMColourRole, Integer> colours",
            "withPreviewRules(",
        ] {
            assert!(
                theme.contains(marker),
                "actual theme provider lacks {marker}"
            );
        }
        for (path, _, _) in PROVIDERS {
            let body = render_java_source(
                std::str::from_utf8(&fixture.core.read_source(path)?)?,
                &context,
            )?;
            assert!(
                !body.contains("{%"),
                "actual provider retained directives: {path}"
            );
        }
    }
    for target in ["1.21.0", "1.21.1", "26.1.2"] {
        let context = fixture.core.context(target, &["client_theme"])?;
        let body = String::from_utf8(fixture.render(0, &context)?.expect("loader"))?;
        assert!(body.contains("import ca.teamdman.sfm.common.util.SFMResourceLocation;"));
        assert!(body.contains("SFMResourceLocation.parse(entry.getKey())"));
        assert_eq!(
            body.contains("import net.minecraft.resources.Identifier;"),
            target == "26.1.2"
        );
    }
    Ok(())
}

#[test]
fn theme_six_unsupported_preview_targets_and_missing_prerequisites_fail_closed() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[2..] {
        for owner in &FULL[1..] {
            assert!(
                fixture
                    .core
                    .context(target, &["client_theme", owner])
                    .is_err()
            );
        }
    }
    for target in &TARGETS[..2] {
        for flags in [
            &["theme_preview_rules"][..],
            &["theme_file_icon_defaults"][..],
            &["theme_file_icon_matching"][..],
            &["client_theme", "theme_six_unregistered"][..],
        ] {
            assert!(fixture.core.context(target, flags).is_err());
        }
    }
    Ok(())
}

#[test]
fn theme_six_environment_and_projection_names_do_not_enable_sources() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        for environment in ["release", "dev"] {
            let flags = if d2(target) {
                &FULL[..]
            } else {
                &["client_theme"][..]
            };
            let mut context = fixture.core.context(target, flags)?;
            context.environment = environment.to_owned();
            context.projection_key = format!("review/theme/{environment}/{target}");
            context.preset = context.projection_key.clone();
            for index in 0..6 {
                if selected(index, target, &context) {
                    assert_eq!(
                        fixture.render(index, &context)?.as_deref(),
                        Some(fixture.raw[expected_oid(index, target)].as_slice())
                    );
                } else {
                    assert!(fixture.render(index, &context)?.is_none());
                }
            }
        }
    }
    Ok(())
}

#[test]
fn theme_six_pure_preview_leaves_keep_bounds_without_ui_or_process_effects() -> Result<()> {
    let fixture = Fixture::load()?;
    for index in 3..6 {
        let text = std::str::from_utf8(&fixture.raw[expected_oid(index, "1.19.2")])?;
        for forbidden in [
            "Files.",
            "ProcessBuilder",
            "Runtime.getRuntime",
            "Minecraft.getInstance",
            "SFMClientActionExecutor",
            "setClipboard",
            "SFMClientThemeService",
            "new Thread",
            "java.net.",
        ] {
            assert!(
                !text.contains(forbidden),
                "pure leaf gained effect: {}/{forbidden}",
                PATHS[index]
            );
        }
    }
    let cache = std::str::from_utf8(&fixture.raw[expected_oid(5, "1.19.2")])?;
    assert!(
        cache.contains("LIMIT=2048")
            && cache.contains("entries.size()>=LIMIT")
            && cache.contains("entries.keySet().iterator().next()")
    );
    let codec = std::str::from_utf8(&fixture.raw[expected_oid(3, "1.19.2")])?;
    for marker in [
        "rules.size()>1024",
        "SFMItemstackPreviewExpression.MAX_DEPTH",
        "SFMItemstackPreviewExpression.MAX_NODES",
        "Layer.USER",
        "n.doubleValue()!=1.0",
    ] {
        assert!(
            codec.contains(marker),
            "codec source contract lost: {marker}"
        );
    }
    Ok(())
}

#[test]
fn theme_six_actual_collector_omits_malformed_off_sources_and_refuses_on_reads() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    std::fs::create_dir_all(&root)?;
    let mut metadata = fixture.core.metadata.clone();
    metadata
        .source_rules
        .retain(|p, _| PATHS.contains(&p.as_str()));
    let inventory = PATHS
        .into_iter()
        .map(str::to_owned)
        .collect::<BTreeSet<_>>();
    let off = fixture.core.context("1.19.2", &[])?;
    let off_selected = select_core_inputs(&metadata, &off, &inventory)?;
    for (output, input) in &off_selected.inputs {
        ensure!(!output.starts_with("src/"), "off selected a theme leaf");
        let bytes = read_bounded(
            &checked_file(&fixture.core.core, &input.input)?,
            16 * 1024 * 1024,
        )?;
        let destination = root.join(&input.input);
        std::fs::create_dir_all(destination.parent().expect("project-file parent"))?;
        std::fs::write(destination, bytes)?;
    }
    for path in PATHS {
        let destination = root.join(path);
        std::fs::create_dir_all(destination.parent().expect("theme parent"))?;
        std::fs::write(destination, [0xff, 0xfe])?;
    }
    assert!(
        collect_core_artifacts(&root, &off_selected, &off)?
            .keys()
            .all(|p| !PATHS.contains(&p.as_str()))
    );
    let on = fixture.core.context("1.19.2", &FULL)?;
    let on_selected = select_core_inputs(&metadata, &on, &inventory)?;
    assert!(collect_core_artifacts(&root, &on_selected, &on).is_err());
    for path in PATHS {
        std::fs::write(root.join(path), fixture.core.read_source(path)?)?;
    }
    let artifacts = collect_core_artifacts(&root, &on_selected, &on)?;
    for index in 0..6 {
        assert!(on_selected.inputs[PATHS[index]].template);
        assert_eq!(
            artifacts[PATHS[index]].source_bytes,
            fixture.core.read_source(PATHS[index])?
        );
        assert!(
            artifacts[PATHS[index]]
                .output_bytes
                .ends_with(&fixture.raw[expected_oid(index, "1.19.2")])
        );
    }
    Ok(())
}

#[test]
fn theme_six_closes_existing_syntax_consumer_only_under_the_real_theme_owner() -> Result<()> {
    let fixture = Fixture::load()?;
    const CALLER: &str =
        "src/main/java/ca/teamdman/sfm/client/text_styling/ProgramSyntaxHighlightingHelper.java";
    let bytes = fixture.core.read_source(CALLER)?;
    validate_bytes(
        &bytes,
        10655,
        "sha256:0d4daf5bde910597e20c17fa1b8734ec31d70eb827a3ac85f1adc9c5bbe4278a",
    )?;
    assert!(
        !fixture.core.metadata.source_rules.contains_key(CALLER),
        "historical unconditional syntax helper gained a source-rule refinement"
    );
    for target in TARGETS {
        let off = fixture.core.context(target, &[])?;
        let on = fixture.core.context(target, &["client_theme"])?;
        let disabled = render_java_source(std::str::from_utf8(&bytes)?, &off)?;
        let enabled = render_java_source(std::str::from_utf8(&bytes)?, &on)?;
        assert!(
            !disabled.contains("SFMClientThemeService") && !disabled.contains("SFMSyntaxStyle")
        );
        for marker in [
            "import ca.teamdman.sfm.client.theme.SFMClientThemeService;",
            "SFMClientThemeService.active().syntax(syntaxTokenId(token))",
            ".apply(Style.EMPTY)",
        ] {
            assert!(
                enabled.contains(marker),
                "actual caller no longer exercises {marker}"
            );
        }
        let service = String::from_utf8(fixture.render(1, &on)?.expect("real theme service"))?;
        assert!(service.contains("public static SFMClientTheme active()"));
        let theme = render_java_source(
            std::str::from_utf8(&fixture.core.read_source(THEME_PROVIDER)?)?,
            &on,
        )?;
        assert!(theme.contains("public SFMSyntaxStyle syntax(String tokenId)"));
        let style = render_java_source(
            std::str::from_utf8(&fixture.core.read_source(PROVIDERS[2].0)?)?,
            &on,
        )?;
        assert!(style.contains("public Style apply(Style base)"));
    }
    Ok(())
}
