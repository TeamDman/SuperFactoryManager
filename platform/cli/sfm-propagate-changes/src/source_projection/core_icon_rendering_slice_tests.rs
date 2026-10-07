//! Post-promotion regression for three staged icon presentation templates.
//!
//! This module deliberately requires the real core inputs and registered
//! predicates/feature definitions. It does not read the ignored staging area,
//! synthesize absent feature definitions or fall back to historical sources.
//! Frozen Git blobs and the independently reconstructed plain D2 hashes are
//! test witnesses only. Root owns registration and execution after promotion.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::discover_core_source_files;
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

const RESOLVED: &str = "src/main/java/ca/teamdman/sfm/client/presentation/SFMResolvedItemIcon.java";
const RESOLVER: &str = "src/main/java/ca/teamdman/sfm/client/presentation/SFMItemIconResolver.java";
const RENDERER: &str = "src/main/java/ca/teamdman/sfm/client/presentation/SFMItemIconRenderer.java";
const CARRIER: &str = "src/main/java/ca/teamdman/sfm/client/presentation/SFMItemIcon.java";
const PATHS: [&str; 3] = [RESOLVED, RESOLVER, RENDERER];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const SAFETY: &str = "item_icon_context_safety";
const INDICATOR: &str = "item_icon_fallback_indicator";
const LEDGER: &str = "docs/tasks/sfm-core-icon-rendering-slice.json";
const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";
const STAGE_PREFIX: &str = "platform/cli/sfm-propagate-changes/target/core-icon-template-stage-v1/";
const PALETTE: [&str; 4] = [
    "client_actions",
    "client_theme",
    "keyboard_profiles",
    "command_palette",
];
const CONTEXT_COMMITS: [(&str, &str); 20] = [
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
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}

const GOLDENS: [Golden; 8] = [
    Golden {
        oid: "32c273d0c9cbf598a123c6eb97d2db0559d686dd",
        digest: "sha256:2a8caddd59bc7fcf5eded5849d35c78ea99ddd5b45bc205dfd19bf803a728fe0",
        bytes: 4662,
    },
    Golden {
        oid: "80c585d5c6158aa69ed314592efddf8788fd2028",
        digest: "sha256:be2021b2935cef8bd53e6f3ff6d5d7186e2edf314722256d4ba4c51a47f040c5",
        bytes: 480,
    },
    Golden {
        oid: "827328e59bb362193790a086c7e15bf2d7cb9492",
        digest: "sha256:9bf078773c4c4afcfdc45e4bcfca91d47bf2a025f03fca9d5efd6f4d26c38d0e",
        bytes: 2564,
    },
    Golden {
        oid: "c2d98bed26a8fc0b2d2574afb06158189c4db710",
        digest: "sha256:9080930fb43251e08001a58b9f3fccdf1c5b3adfc6ba8b55ca262b2988fb4c13",
        bytes: 1613,
    },
    Golden {
        oid: "c71b5ff298a4502b9eaaa4d616445bdaff1dc90b",
        digest: "sha256:6f45bf86f1c5f240797c6b1c7f2c9ac5af8106345151747251ce7c7d0be12e5d",
        bytes: 659,
    },
    Golden {
        oid: "cc364458d04b05f03d5b6e6cd898de788ab7d363",
        digest: "sha256:7aece96779011eaf5f647eca6701652839306f109460836703eae8f175f7138f",
        bytes: 5477,
    },
    Golden {
        oid: "ea9487531caea86c8fb22e0368992099a70f5fb2",
        digest: "sha256:a749525cd1897e99cea80c8213b49e20060d1c180ae76086d28d7aad43540d1b",
        bytes: 1675,
    },
    Golden {
        oid: "fb57e864f8e282ed5ae2377b41a48c6886203a75",
        digest: "sha256:4328163e378be378734c356b75de368ae764064fdb3b1a01f5002680df9ac3d6",
        bytes: 671,
    },
];

#[derive(Clone, Copy)]
struct TemplateGolden {
    path: &'static str,
    digest: &'static str,
    bytes: usize,
}

const TEMPLATES: [TemplateGolden; 3] = [
    TemplateGolden {
        path: "src/main/java/ca/teamdman/sfm/client/presentation/SFMResolvedItemIcon.java",
        digest: "sha256:be2021b2935cef8bd53e6f3ff6d5d7186e2edf314722256d4ba4c51a47f040c5",
        bytes: 480,
    },
    TemplateGolden {
        path: "src/main/java/ca/teamdman/sfm/client/presentation/SFMItemIconResolver.java",
        digest: "sha256:6d4c00550b42329df998e2959ce416b5748c64136cd4487569ead1a292b3aa2d",
        bytes: 3983,
    },
    TemplateGolden {
        path: "src/main/java/ca/teamdman/sfm/client/presentation/SFMItemIconRenderer.java",
        digest: "sha256:cee9bf19c2e5bea612803334b06ed3765a6227470c639b8639da1cc4b76f69e8",
        bytes: 7959,
    },
];

// These explicit reconstructed body hashes are not historical off witnesses.
// The real production renderer must confirm them after promotion/registration.
const D2_RENDERER_PROFILES: [(&str, bool, bool, &str, usize); 8] = [
    (
        "1.19.2",
        false,
        false,
        "sha256:d4e8a992b40f7e53f6d654144c1f3559f0d74225fc2d4f6921abece73f111539",
        1705,
    ),
    (
        "1.19.2",
        true,
        false,
        "sha256:67f54376228bb4bceb17a7cb85ea7e83d6a2dd094a8dcc291aa9bea87fef1fee",
        5307,
    ),
    (
        "1.19.2",
        false,
        true,
        "sha256:5f0ff44e625d55211711ce386f8367c7e981b986771e2ac90964879cbfc5b923",
        1875,
    ),
    (
        "1.19.2",
        true,
        true,
        "sha256:7aece96779011eaf5f647eca6701652839306f109460836703eae8f175f7138f",
        5477,
    ),
    (
        "1.19.4",
        false,
        false,
        "sha256:e57e3a828a7946b56d6eb2236bd3c83b71aad1a197e99c404b5ba3846050cab0",
        734,
    ),
    (
        "1.19.4",
        true,
        false,
        "sha256:f0a348d06904eeea8e10ca86f263406fe81d1fc737b0258af0896ea6ed79352c",
        4337,
    ),
    (
        "1.19.4",
        false,
        true,
        "sha256:fed8c25ae4a41ab43340cdc4bdd7a23c18647311220300e0d35637d89fc03806",
        1059,
    ),
    (
        "1.19.4",
        true,
        true,
        "sha256:2a8caddd59bc7fcf5eded5849d35c78ea99ddd5b45bc205dfd19bf803a728fe0",
        4662,
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: String,
    files: Vec<InputEvidence>,
}

#[derive(Facet)]
struct InputEvidence {
    path: String,
    staged_path: String,
    intended_core_path: String,
    template_sha256: String,
    template_bytes: usize,
    normalization: String,
    line_endings: String,
    terminal_newline: String,
    membership: Membership,
    raw_variants: Vec<RawVariant>,
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
struct RawVariant {
    oid: String,
    sha256: String,
    bytes: usize,
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
    feature_context_origin: String,
    stage_preview_raw_exact: Option<bool>,
}

struct IconFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}

impl IconFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let source = String::from_utf8(read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            1024 * 1024,
        )?)?;
        let ledger: Ledger = facet_json::from_str(&source)?;
        ensure!(
            ledger.schema == "sfm:core_icon_rendering_slice@1"
                && ledger.context_commits == pinned_commits()
                && ledger.normalization == "none_raw_exact_lf_with_exactly_one_terminal_lf"
                && ledger.files.len() == 3,
            "icon ledger scope/identity/normalization changed"
        );
        validate_definitions(&core)?;
        let mut seen = BTreeSet::new();
        for input in ledger.files {
            let template = template(&input.path)?;
            let name = input.path.rsplit('/').next().expect("fixed filename");
            ensure!(
                seen.insert(input.path.clone())
                    && input.intended_core_path == format!("{CORE_PREFIX}{}", input.path)
                    && input.staged_path == format!("{STAGE_PREFIX}{name}")
                    && input.template_sha256 == template.digest
                    && input.template_bytes == template.bytes
                    && input.normalization == "none"
                    && input.line_endings == "lf"
                    && input.terminal_newline == "exactly_one_lf",
                "icon authored template contract changed"
            );
            validate_membership(&core, &input)?;
            let expected_oids = TARGETS
                .iter()
                .map(|target| raw_oid(&input.path, target))
                .collect::<Result<BTreeSet<_>>>()?;
            let mut variants = BTreeSet::new();
            for variant in input.raw_variants {
                let row = golden(&variant.oid)?;
                ensure!(
                    variants.insert(variant.oid.clone())
                        && variant.sha256 == row.digest
                        && variant.bytes == row.bytes,
                    "icon raw variant duplicated or changed"
                );
            }
            ensure!(
                variants.iter().map(String::as_str).collect::<BTreeSet<_>>() == expected_oids,
                "icon raw variant set changed"
            );
            ensure!(input.witnesses.len() == 20, "incomplete icon witness set");
            let mut contexts = BTreeSet::new();
            for witness in input.witnesses {
                let commit = pinned_commits()
                    .get(&witness.context)
                    .cloned()
                    .ok_or_else(|| eyre::eyre!("unknown icon witness context"))?;
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid pinned icon context"))?;
                let present = environment == "dev";
                let row = golden(raw_oid(&input.path, target)?)?;
                let enabled = historical_features(environment, target);
                core.context(target, &enabled)?;
                ensure!(
                    contexts.insert(witness.context.clone())
                        && witness.source_commit == commit
                        && witness.present == present
                        && witness.raw_blob.as_deref() == present.then_some(row.oid)
                        && witness.raw_sha256.as_deref() == present.then_some(row.digest)
                        && witness.raw_bytes == present.then_some(row.bytes)
                        && witness.mode.as_deref() == present.then_some("100644")
                        && same_names(&witness.explicit_registered_features, &enabled)
                        && witness.feature_context_origin
                            == "reconstructed_minimal_consumer_owner_not_historical_flag_claim"
                        && witness.stage_preview_raw_exact == present.then_some(true),
                    "icon historical raw/membership witness changed"
                );
            }
        }
        ensure!(
            seen == PATHS.into_iter().map(str::to_owned).collect(),
            "icon path scope changed"
        );
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|row| row.oid.to_owned()).collect(),
        )?;
        for row in GOLDENS {
            let bytes = &raw[row.oid];
            ensure!(
                bytes.len() == row.bytes
                    && sha256(bytes) == row.digest
                    && !bytes.contains(&b'\r')
                    && bytes.ends_with(b"\n")
                    && !bytes.ends_with(b"\n\n"),
                "icon frozen bytes/newlines changed"
            );
            std::str::from_utf8(bytes)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "staged icons have not been promoted to the real core"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }

    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selection.inputs.get(path) else {
            ensure!(
                selection.omitted_paths.contains(path),
                "icon source lost outside predicate"
            );
            return Ok(None);
        };
        ensure!(input.input == path, "unreviewed alternate icon source");
        // Source membership is decided before this bounded physical read.
        let source = self.core.read_source(path)?;
        let expected = template(path)?;
        ensure!(
            source.len() == expected.bytes
                && sha256(&source) == expected.digest
                && !source.contains(&b'\r')
                && source.ends_with(b"\n")
                && !source.ends_with(b"\n\n"),
            "icon promoted template bytes changed"
        );
        Ok(Some(
            render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes(),
        ))
    }

    fn assert_absent(&self, context: &ProjectionContext) -> Result<()> {
        for path in PATHS {
            assert_eq!(self.render(path, context)?, None);
        }
        Ok(())
    }

    fn assert_raw(&self, context: &ProjectionContext, target: &str) -> Result<()> {
        for path in PATHS {
            assert_eq!(
                self.render(path, context)?.as_deref(),
                Some(self.raw[raw_oid(path, target)?].as_slice()),
                "{path} / {target}"
            );
        }
        Ok(())
    }

    fn body(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        String::from_utf8(
            self.render(path, context)?
                .ok_or_else(|| eyre::eyre!("expected included icon"))?,
        )
        .map_err(Into::into)
    }
}

fn pinned_commits() -> BTreeMap<String, String> {
    CONTEXT_COMMITS
        .into_iter()
        .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
        .collect()
}

fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}

fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}

fn golden(oid: &str) -> Result<Golden> {
    GOLDENS
        .iter()
        .find(|row| row.oid == oid)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected frozen icon object"))
}

fn template(path: &str) -> Result<TemplateGolden> {
    TEMPLATES
        .iter()
        .find(|row| row.path == path)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected promoted icon template"))
}

fn raw_oid(path: &str, target: &str) -> Result<&'static str> {
    ensure!(TARGETS.contains(&target), "unexpected icon target");
    match path {
        RESOLVED => Ok("80c585d5c6158aa69ed314592efddf8788fd2028"),
        RESOLVER if is_d2(target) => Ok("827328e59bb362193790a086c7e15bf2d7cb9492"),
        RESOLVER if target == "26.1.2" => Ok("ea9487531caea86c8fb22e0368992099a70f5fb2"),
        RESOLVER => Ok("c2d98bed26a8fc0b2d2574afb06158189c4db710"),
        RENDERER if target == "1.19.2" => Ok("cc364458d04b05f03d5b6e6cd898de788ab7d363"),
        RENDERER if target == "1.19.4" => Ok("32c273d0c9cbf598a123c6eb97d2db0559d686dd"),
        RENDERER if target == "26.1.2" => Ok("fb57e864f8e282ed5ae2377b41a48c6886203a75"),
        RENDERER => Ok("c71b5ff298a4502b9eaaa4d616445bdaff1dc90b"),
        _ => Err(eyre::eyre!("unexpected icon path")),
    }
}

fn historical_features(environment: &str, target: &str) -> Vec<&'static str> {
    if environment == "release" {
        return vec![];
    }
    if is_d2(target) {
        vec!["client_theme", SAFETY, INDICATOR]
    } else {
        vec!["client_theme"]
    }
}

fn validate_definitions(core: &CoreTestFixture) -> Result<()> {
    for id in [SAFETY, INDICATOR] {
        let definition = core
            .features
            .0
            .get(id)
            .ok_or_else(|| eyre::eyre!("staged icon flag has not been registered: {id}"))?;
        ensure!(
            same_names(&definition.supported_targets, &TARGETS[..2])
                && definition.requires.is_empty(),
            "icon flags must be independent D2 features"
        );
    }
    let carrier = core
        .metadata
        .source_rules
        .get(CARRIER)
        .ok_or_else(|| eyre::eyre!("icon carrier lacks reviewed owner predicate"))?;
    ensure!(
        carrier.len() == 1
            && carrier[0].input == CARRIER
            && carrier[0].when.targets.is_empty()
            && carrier[0].when.all_features.is_empty()
            && same_names(
                &carrier[0].when.any_features,
                &[
                    "client_actions",
                    "client_theme",
                    "file_explorer",
                    "legacy_file_explorer"
                ]
            )
            && carrier[0].when.none_features.is_empty(),
        "theme-only icon carrier ownership has not been registered"
    );
    Ok(())
}

fn validate_membership(core: &CoreTestFixture, input: &InputEvidence) -> Result<()> {
    ensure!(
        input.membership.targets.is_empty()
            && input.membership.all_features.is_empty()
            && same_names(
                &input.membership.any_features,
                &["command_palette", "client_theme"]
            )
            && input.membership.none_features.is_empty(),
        "staged icon ledger predicate changed"
    );
    let rules = core
        .metadata
        .source_rules
        .get(&input.path)
        .ok_or_else(|| eyre::eyre!("icon helpers have not been registered"))?;
    ensure!(
        rules.len() == 1
            && rules[0].input == input.path
            && rules[0].when.targets.is_empty()
            && rules[0].when.all_features.is_empty()
            && same_names(
                &rules[0].when.any_features,
                &[
                    "command_palette",
                    "client_theme",
                    "file_explorer",
                    "legacy_file_explorer"
                ]
            )
            && rules[0].when.none_features.is_empty(),
        "icon production predicate differs from reviewed initial consumers"
    );
    Ok(())
}

#[test]
fn icon_templates_reconstruct_twenty_full_historical_raw_memberships() -> Result<()> {
    let fixture = IconFixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXT_COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed contexts");
        let enabled = historical_features(environment, target);
        let context = fixture.core.context(target, &enabled)?;
        if environment == "dev" {
            fixture.assert_raw(&context, target)?;
            present += 3;
        } else {
            fixture.assert_absent(&context)?;
            absent += 3;
        }
    }
    assert_eq!((present, absent), (30, 30));
    Ok(())
}

#[test]
fn icons_twenty_feature_off_controls_omit_before_input_reads() -> Result<()> {
    let mut fixture = IconFixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture
        .core
        .core
        .join("deliberately_missing_icon_source_boundary");
    for key in catalog.catalog.0.keys() {
        fixture.assert_absent(&fixture.core.historical_feature_off_catalog_context(key)?)?;
    }
    Ok(())
}

#[test]
fn theme_only_consumer_does_not_require_action_runtime_or_palette() -> Result<()> {
    let fixture = IconFixture::load()?;
    for target in TARGETS {
        let theme = fixture.core.context(target, &["client_theme"])?;
        assert!(!theme.features["client_actions"] && !theme.features["command_palette"]);
        let selection = select_core_inputs(&fixture.core.metadata, &theme, &fixture.inventory)?;
        assert!(selection.inputs.contains_key(CARRIER));
        for path in PATHS {
            assert!(fixture.render(path, &theme)?.is_some());
        }
        let palette = fixture.core.context(target, &PALETTE)?;
        for path in PATHS {
            assert_eq!(
                fixture.render(path, &theme)?,
                fixture.render(path, &palette)?
            );
        }
        fixture.assert_absent(&fixture.core.context(target, &["client_actions"])?)?;
        fixture.assert_absent(&fixture.core.context(target, &[])?)?;
        assert!(fixture.core.context(target, &["command_palette"]).is_err());
        if !is_d2(target) {
            fixture.assert_raw(&theme, target)?;
            for id in [SAFETY, INDICATOR] {
                assert!(fixture.core.context(target, &["client_theme", id]).is_err());
            }
        }
    }
    Ok(())
}

#[test]
fn independent_file_explorers_select_real_icon_imports_without_palette_or_theme() -> Result<()> {
    let fixture = IconFixture::load()?;
    for target in TARGETS {
        let owner = if is_d2(target) {
            "file_explorer"
        } else {
            "legacy_file_explorer"
        };
        let context = fixture.core.context(target, &[owner, "workspace_panels"])?;
        assert!(!context.features["client_actions"]);
        assert!(!context.features["client_theme"]);
        assert!(!context.features["command_palette"]);
        let selected = select_core_inputs(&fixture.core.metadata, &context, &fixture.inventory)?;
        assert!(selected.inputs.contains_key(CARRIER));
        for path in PATHS {
            assert!(fixture.render(path, &context)?.is_some());
        }
        let consumer = if is_d2(target) {
            "src/main/java/ca/teamdman/sfm/client/screen/explorer/SFMExplorerPanel.java"
        } else {
            "src/main/java/ca/teamdman/sfm/client/screen/file_explorer/SFMFileExplorerPanel.java"
        };
        let selected_consumer = select_core_inputs(
            &fixture.core.metadata,
            &context,
            &BTreeSet::from([consumer.to_owned()]),
        )?;
        assert!(selected_consumer.inputs.contains_key(consumer));
        let source = fixture.core.read_source(consumer)?;
        let rendered = render_java_source(std::str::from_utf8(&source)?, &context)?;
        let icon_import = if is_d2(target) {
            "import ca.teamdman.sfm.client.presentation.SFMItemIcon;"
        } else {
            "import ca.teamdman.sfm.client.presentation.SFMResolvedItemIcon;"
        };
        assert!(rendered.contains(icon_import));
        assert!(
            rendered.contains("import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;")
        );
    }
    Ok(())
}

#[test]
fn icon_flags_are_independent_and_real_renderer_confirms_d2_reconstructions() -> Result<()> {
    let fixture = IconFixture::load()?;
    let mut bodies = 0;
    for target in &TARGETS[..2] {
        for mask in 0_u8..4 {
            let safety = mask & 1 != 0;
            let indicator = mask & 2 != 0;
            let mut enabled = vec!["client_theme"];
            if safety {
                enabled.push(SAFETY);
            }
            if indicator {
                enabled.push(INDICATOR);
            }
            let context = fixture.core.context(target, &enabled)?;
            for path in PATHS {
                let body = fixture.render(path, &context)?.expect("selected owner");
                let (digest, bytes) = match path {
                    RESOLVED => {
                        let row = golden(raw_oid(RESOLVED, target)?)?;
                        (row.digest, row.bytes)
                    }
                    RESOLVER => {
                        let row = golden(if safety {
                            "827328e59bb362193790a086c7e15bf2d7cb9492"
                        } else {
                            "c2d98bed26a8fc0b2d2574afb06158189c4db710"
                        })?;
                        (row.digest, row.bytes)
                    }
                    RENDERER => {
                        let row = D2_RENDERER_PROFILES
                            .iter()
                            .find(|row| row.0 == *target && row.1 == safety && row.2 == indicator)
                            .expect("fixed reconstructed renderer profile");
                        (row.3, row.4)
                    }
                    _ => unreachable!("fixed paths"),
                };
                assert_eq!((sha256(&body), body.len()), (digest.to_owned(), bytes));
                bodies += 1;
            }
            let resolver = fixture.body(RESOLVER, &context)?;
            let renderer = fixture.body(RENDERER, &context)?;
            for marker in ["resolveFallback(", "resolvePaper(", "resolveSelected("] {
                assert_eq!(resolver.contains(marker), safety);
            }
            for marker in [
                "public record Inspection(",
                "requiresTitleScreenFallback(",
                "IClientItemExtensions",
            ] {
                assert_eq!(renderer.contains(marker), safety);
            }
            assert_eq!(renderer.contains("SFMFontUtils.draw"), indicator);
            let without_owner = enabled
                .into_iter()
                .filter(|id| *id != "client_theme")
                .collect::<Vec<_>>();
            fixture.assert_absent(&fixture.core.context(target, &without_owner)?)?;
        }
    }
    assert_eq!(bodies, 24);
    Ok(())
}

#[test]
fn exact_minecraft_icon_apis_and_mutable_carrier_contract_remain_explicit() -> Result<()> {
    let fixture = IconFixture::load()?;
    for target in TARGETS {
        let context = fixture.core.context(target, &["client_theme"])?;
        let renderer = fixture.body(RENDERER, &context)?;
        let resolver = fixture.body(RESOLVER, &context)?;
        match target {
            "1.19.2" => {
                assert!(renderer.contains("RenderSystem.getModelViewStack()"));
                assert!(renderer.contains("modelView.mulPoseMatrix(poseStack.last().pose())"));
                assert!(renderer.contains("renderAndDecorateItem(resolved.stack(), x, y)"));
                assert!(!renderer.contains("GuiGraphics"));
            }
            "1.19.4" => {
                assert!(renderer.contains("render(PoseStack poseStack,"));
                assert!(
                    renderer.contains("renderAndDecorateItem(poseStack, resolved.stack(), x, y)")
                );
                assert!(!renderer.contains("RenderSystem") && !renderer.contains("GuiGraphics"));
            }
            "26.1.2" => {
                assert!(renderer.contains("GuiGraphicsExtractor"));
                assert!(renderer.contains("graphics.item(resolved.stack(), x, y)"));
                assert!(resolver.contains("import net.minecraft.resources.Identifier;"));
                assert!(resolver.contains(".map(reference -> reference.value()).orElse(null)"));
                assert!(!resolver.contains("ResourceLocation"));
            }
            _ => {
                assert!(renderer.contains("render(GuiGraphics graphics,"));
                assert!(renderer.contains("graphics.renderItem(resolved.stack(), x, y)"));
                assert!(!renderer.contains("GuiGraphicsExtractor"));
            }
        }
        let resolved = fixture.body(RESOLVED, &context)?;
        assert!(resolved.contains("Objects.requireNonNull(stack"));
        assert!(!resolved.contains(".copy()") && !resolved.contains("stack ="));
        for path in PATHS {
            let body = fixture.body(path, &context)?;
            for forbidden in [
                "java.nio.file.",
                "ProcessBuilder",
                "SFMClientProgramIdentity",
                "SFMClientActionProgrammaticHandler",
                "SFMPacket",
            ] {
                assert!(
                    !body.contains(forbidden),
                    "presentation helper gained authority/I/O: {path}"
                );
            }
        }
    }
    Ok(())
}

#[test]
fn descriptive_environment_and_nested_projection_key_do_not_select_icon_versions() -> Result<()> {
    let fixture = IconFixture::load()?;
    for target in TARGETS {
        let enabled = historical_features("dev", target);
        for environment in ["release", "dev"] {
            let mut context = fixture.core.context(target, &enabled)?;
            context.environment = environment.to_owned();
            context.projection_key = format!("review/icon-apis/{environment}/{target}");
            context.preset = context.projection_key.clone();
            fixture.assert_raw(&context, target)?;
            let mut absent = fixture.core.context(target, &[])?;
            absent.environment = environment.to_owned();
            absent.projection_key = format!("review/icon-apis-off/{environment}/{target}");
            absent.preset = absent.projection_key.clone();
            fixture.assert_absent(&absent)?;
        }
    }
    Ok(())
}
