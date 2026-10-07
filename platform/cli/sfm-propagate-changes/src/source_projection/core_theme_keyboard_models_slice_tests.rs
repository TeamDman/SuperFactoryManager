//! Eight theme/keyboard model source proofs after parent-reviewed promotion.
//!
//! Frozen Git blobs are offline bounded test witnesses only. Membership comes
//! from the real core selector and every included Java file uses the production
//! controlled renderer. No stub provider, complete Java build, host dispatch,
//! theme file loading, keyboard execution or game/runtime acceptance is implied.

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

const SNAPSHOT: &str =
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyboardUsageContextSnapshot.java";
const CATALOG: &str =
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyboardUsageSituationCatalog.java";
const LOAD_RESULT: &str = "src/main/java/ca/teamdman/sfm/client/theme/SFMThemeLoadResult.java";
const COLOUR: &str = "src/main/java/ca/teamdman/sfm/client/theme/SFMColourRole.java";
const SITUATION: &str =
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyboardUsageSituation.java";
const REGISTRY: &str =
    "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyboardUsageSituations.java";
const PROVIDER: &str =
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyboardUsageContextProvider.java";
const STYLE: &str = "src/main/java/ca/teamdman/sfm/client/theme/SFMSyntaxStyle.java";
const PATHS: [&str; 8] = [
    PROVIDER,
    SNAPSHOT,
    SITUATION,
    CATALOG,
    REGISTRY,
    COLOUR,
    STYLE,
    LOAD_RESULT,
];
const KEYBOARD_PATHS: [&str; 5] = [PROVIDER, SNAPSHOT, SITUATION, CATALOG, REGISTRY];
const THEME_PATHS: [&str; 3] = [COLOUR, STYLE, LOAD_RESULT];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const FULL_D2: [&str; 5] = [
    "client_actions",
    "keyboard_profiles",
    "workspace_panels",
    "client_theme",
    "explorer_search",
];
const LEDGER: &str = "docs/tasks/sfm-core-theme-keyboard-models-slice.json";
const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";
const SNAPSHOT_OFF_SHA256: &str =
    "sha256:d42a3fa512548ca3132c6453e341c86379c5a3d5b6f9d7043ee2bdecf944e2dd";
const SNAPSHOT_OFF_BYTES: usize = 5239;
const COLOUR_ON_OID: &str = "0594e3ba3bb983c026117a1880716659bcc58798";
const COLOUR_OFF_OID: &str = "ec24f0ddf438200b526323bcd90472a898c84e2e";
const SNAPSHOT_ON_OID: &str = "5d71d7095400ca5f4f2f64ab9cdf948bab60102d";
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
struct RawPin {
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}

const RAW_PINS: [RawPin; 9] = [
    RawPin {
        oid: "86d9e3e4b4e6124ade6e59e3d2daec0f2d5b0420",
        digest: "sha256:9db75a19c8e4380c6b4841a16b18168605412451d4c3b1acb7ba65c3b9e76301",
        bytes: 412,
    },
    RawPin {
        oid: "2247420aebdd83bbefd8431150877d88f5d33fa2",
        digest: "sha256:c6671489b5105b9420c5b85bbd75880923327c94e5d9f6b67dd03f75194966cb",
        bytes: 3920,
    },
    RawPin {
        oid: "793e45bb7e1332b65c155e3c18784269a1d5ad3c",
        digest: "sha256:99714c38fbeb85f7d6e8b5b1a8a73f085bbb854f581b3c26a3a2db7a1496fa2b",
        bytes: 525,
    },
    RawPin {
        oid: "ec24f0ddf438200b526323bcd90472a898c84e2e",
        digest: "sha256:1d173067f57825356dbf43550ce3ed7711a05a8a618489e2dbb98c36a52ee008",
        bytes: 1256,
    },
    RawPin {
        oid: "007d9a046f9eabca1d5f08b75d3e44da86949317",
        digest: "sha256:b27df19e7b3cf9cbac8b4a9e5f5390db77e13e4b9459a81d3b00b4b1c789ca1c",
        bytes: 393,
    },
    RawPin {
        oid: "5d71d7095400ca5f4f2f64ab9cdf948bab60102d",
        digest: "sha256:757974b534bf7797c6d51815851193658be0ba7c338c692b7fc11d75a3a8a714",
        bytes: 5737,
    },
    RawPin {
        oid: "0594e3ba3bb983c026117a1880716659bcc58798",
        digest: "sha256:cc597f0f1989f4f27c0e26e41bc1e4e264fc6adefb0399f972352a6d42b9a3c1",
        bytes: 1408,
    },
    RawPin {
        oid: "7f5cbde527e09b9197ac0d1867dfea08be06bc8a",
        digest: "sha256:c3c0e7e74192992f771a70c8557af541e328deb27ec197d9410fb4cf2c3d5bdf",
        bytes: 6859,
    },
    RawPin {
        oid: "137b052e7cc460c51773e76fe724fffaf91c720e",
        digest: "sha256:ca1a3030617d2b3a7f55e2d9401dcb2ae706176063cc5a36587d03cf689ec051",
        bytes: 573,
    },
];

#[derive(Clone, Copy)]
struct TemplatePin {
    path: &'static str,
    digest: &'static str,
    bytes: usize,
}

const TEMPLATE_PINS: [TemplatePin; 8] = [
    TemplatePin {
        path: SNAPSHOT,
        digest: "sha256:c305864d372d9debc0f5469c364f6b5ff1aefee77d2f330c8a73e05e08ed6266",
        bytes: 6422,
    },
    TemplatePin {
        path: CATALOG,
        digest: "sha256:c6671489b5105b9420c5b85bbd75880923327c94e5d9f6b67dd03f75194966cb",
        bytes: 3920,
    },
    TemplatePin {
        path: LOAD_RESULT,
        digest: "sha256:9db75a19c8e4380c6b4841a16b18168605412451d4c3b1acb7ba65c3b9e76301",
        bytes: 412,
    },
    TemplatePin {
        path: COLOUR,
        digest: "sha256:c5c4729339582fb8c16c57be099076c07b69e595d560dec94ffd765d17313b9c",
        bytes: 1454,
    },
    TemplatePin {
        path: SITUATION,
        digest: "sha256:ca1a3030617d2b3a7f55e2d9401dcb2ae706176063cc5a36587d03cf689ec051",
        bytes: 573,
    },
    TemplatePin {
        path: REGISTRY,
        digest: "sha256:c3c0e7e74192992f771a70c8557af541e328deb27ec197d9410fb4cf2c3d5bdf",
        bytes: 6859,
    },
    TemplatePin {
        path: PROVIDER,
        digest: "sha256:99714c38fbeb85f7d6e8b5b1a8a73f085bbb854f581b3c26a3a2db7a1496fa2b",
        bytes: 525,
    },
    TemplatePin {
        path: STYLE,
        digest: "sha256:b27df19e7b3cf9cbac8b4a9e5f5390db77e13e4b9459a81d3b00b4b1c789ca1c",
        bytes: 393,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: String,
    files: Vec<InputEvidence>,
    raw_blobs: BTreeMap<String, RawEvidence>,
}

#[derive(Facet)]
struct RawEvidence {
    raw_sha256: String,
    raw_bytes: usize,
    bom: bool,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    normalization: String,
    prefix_hex: String,
}

#[derive(Facet)]
struct InputEvidence {
    path: String,
    core_path: String,
    template_sha256: String,
    template_bytes: usize,
    membership: Membership,
    raw_oids: Vec<String>,
    member_owners: Vec<String>,
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
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    raw_oid: Option<String>,
    raw_sha256: Option<String>,
    raw_bytes: Option<usize>,
    mode: Option<String>,
    explicit_features: Vec<String>,
    features_origin: String,
}

struct ModelFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}

impl ModelFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core_theme_keyboard_models_slice@1"
                && ledger.context_commits == pinned_commits()
                && ledger.normalization == "none_raw_exact_lf_final_lf"
                && ledger.files.len() == PATHS.len()
                && ledger.raw_blobs.len() == RAW_PINS.len(),
            "theme/keyboard ledger scope or frozen identity changed"
        );
        for pin in RAW_PINS {
            let row = ledger
                .raw_blobs
                .get(pin.oid)
                .ok_or_else(|| eyre::eyre!("missing exact theme/keyboard raw pin"))?;
            ensure!(
                row.raw_sha256 == pin.digest
                    && row.raw_bytes == pin.bytes
                    && !row.bom
                    && row.crlf_count == 0
                    && row.lone_cr_count == 0
                    && row.final_lf
                    && row.normalization == "none"
                    && row.prefix_hex == "7061636b61676520",
                "theme/keyboard raw byte or newline policy changed"
            );
        }
        let mut seen_paths = BTreeSet::new();
        for input in &ledger.files {
            let pin = template_pin(&input.path)?;
            let (owner, targets) = member_contract(&input.path)?;
            ensure!(
                seen_paths.insert(input.path.clone())
                    && input.core_path == format!("{CORE_PREFIX}{}", input.path)
                    && input.template_sha256 == pin.digest
                    && input.template_bytes == pin.bytes,
                "changed model input scope/template identity"
            );
            ensure!(
                same_names(&input.membership.targets, targets)
                    && same_names(&input.membership.all_features, &[owner])
                    && input.membership.any_features.is_empty()
                    && input.membership.none_features.is_empty(),
                "model ledger membership changed"
            );
            let expected_owners = match input.path.as_str() {
                SNAPSHOT => &["workspace_panels"][..],
                COLOUR => &["explorer_search"][..],
                _ => &[][..],
            };
            ensure!(
                same_names(&input.member_owners, expected_owners),
                "member seam owner changed"
            );
            let rules = core
                .metadata
                .source_rules
                .get(&input.path)
                .ok_or_else(|| eyre::eyre!("model has no explicit source predicate"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == input.path
                    && rules[0].template
                    && same_names(&rules[0].when.targets, targets)
                    && same_names(&rules[0].when.all_features, &[owner])
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "model live source membership differs from reviewed contract"
            );
            let source = core.read_source(&input.path)?;
            validate_template_body(pin, &source)?;
            ensure!(input.witnesses.len() == 20, "incomplete model witnesses");
            let mut contexts = BTreeSet::new();
            let mut raw_oids = BTreeSet::new();
            for witness in &input.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid frozen model context"))?;
                let expected_oid = historical_oid(&input.path, environment, target)?;
                let expected_pin = expected_oid.map(raw_pin).transpose()?;
                let features = historical_features(environment, target);
                ensure!(
                    contexts.insert(witness.context.clone())
                        && pinned_commits().get(&witness.context) == Some(&witness.source_commit)
                        && witness.present == expected_oid.is_some()
                        && witness.raw_oid.as_deref() == expected_oid
                        && witness.raw_sha256.as_deref() == expected_pin.map(|row| row.digest)
                        && witness.raw_bytes == expected_pin.map(|row| row.bytes)
                        && witness.mode.as_deref() == expected_oid.map(|_| "100644")
                        && same_names(&witness.explicit_features, features)
                        && witness.features_origin
                            == "reviewed_current_explicit_reconstruction_not_historical_manifest",
                    "changed frozen model tree/blob/feature witness"
                );
                core.context(target, features)?;
                if let Some(oid) = expected_oid {
                    raw_oids.insert(oid.to_owned());
                }
            }
            ensure!(
                input.raw_oids.len() == raw_oids.len()
                    && input.raw_oids.iter().cloned().collect::<BTreeSet<_>>() == raw_oids,
                "model raw variant set changed"
            );
        }
        ensure!(
            seen_paths == PATHS.into_iter().map(str::to_owned).collect(),
            "unexpected model path"
        );
        let raw = read_git_blobs(
            &core.repository,
            &RAW_PINS.iter().map(|pin| pin.oid.to_owned()).collect(),
        )?;
        for pin in RAW_PINS {
            let body = &raw[pin.oid];
            validate_raw_body(pin, body)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "missing authored model input"
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
                "omitted model lacks a deliberate source predicate"
            );
            return Ok(None);
        };
        ensure!(input.input == path, "unexpected alternative model source");
        // This source read is deliberately after real production selection.
        let source = self.core.read_source(path)?;
        let pin = template_pin(path)?;
        validate_template_body(pin, &source)?;
        Ok(Some(
            render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes(),
        ))
    }

    fn assert_models(&self, context: &ProjectionContext) -> Result<()> {
        let target = if context.minecraft_version == "1.21" {
            "1.21.0"
        } else {
            &context.minecraft_version
        };
        for path in PATHS {
            let present = if KEYBOARD_PATHS.contains(&path) {
                is_d2(target) && context.features["keyboard_profiles"]
            } else {
                context.features["client_theme"]
            };
            let body = self.render(path, context)?;
            if !present {
                ensure!(
                    body.is_none(),
                    "disabled/unsupported model included: {path}"
                );
                continue;
            }
            let body = body.ok_or_else(|| eyre::eyre!("required model omitted: {path}"))?;
            if path == SNAPSHOT && !context.features["workspace_panels"] {
                ensure!(
                    body.len() == SNAPSHOT_OFF_BYTES && sha256(&body) == SNAPSHOT_OFF_SHA256,
                    "reviewed reconstructed standalone snapshot changed"
                );
                assert_standalone_snapshot(std::str::from_utf8(&body)?)?;
            } else {
                let oid = if path == COLOUR {
                    if context.features["explorer_search"] {
                        COLOUR_ON_OID
                    } else {
                        COLOUR_OFF_OID
                    }
                } else {
                    basic_oid(path)?
                };
                assert_eq!(body, self.raw[oid], "{path} / {target}");
            }
        }
        Ok(())
    }
}

fn pinned_commits() -> BTreeMap<String, String> {
    CONTEXT_COMMITS
        .into_iter()
        .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
        .collect()
}

fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}

fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}

fn member_contract(path: &str) -> Result<(&'static str, &'static [&'static str])> {
    if KEYBOARD_PATHS.contains(&path) {
        Ok(("keyboard_profiles", &TARGETS[..2]))
    } else if THEME_PATHS.contains(&path) {
        Ok(("client_theme", &[]))
    } else {
        Err(eyre::eyre!("unreviewed model membership path"))
    }
}

fn template_pin(path: &str) -> Result<TemplatePin> {
    TEMPLATE_PINS
        .iter()
        .find(|pin| pin.path == path)
        .copied()
        .ok_or_else(|| eyre::eyre!("unreviewed model template"))
}

fn raw_pin(oid: &str) -> Result<RawPin> {
    RAW_PINS
        .iter()
        .find(|pin| pin.oid == oid)
        .copied()
        .ok_or_else(|| eyre::eyre!("unreviewed model raw object"))
}

fn validate_template_body(pin: TemplatePin, source: &[u8]) -> Result<()> {
    ensure!(
        source.len() == pin.bytes && sha256(source) == pin.digest,
        "authored model template changed without reviewed source evidence"
    );
    Ok(())
}

fn validate_raw_body(pin: RawPin, body: &[u8]) -> Result<()> {
    ensure!(
        body.len() == pin.bytes
            && sha256(body) == pin.digest
            && !body.contains(&b'\r')
            && body.ends_with(b"\n")
            && !body.ends_with(b"\n\n")
            && !body.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw model witness does not satisfy exact byte policy"
    );
    std::str::from_utf8(body)?;
    Ok(())
}

fn basic_oid(path: &str) -> Result<&'static str> {
    match path {
        PROVIDER => Ok("793e45bb7e1332b65c155e3c18784269a1d5ad3c"),
        SNAPSHOT => Ok(SNAPSHOT_ON_OID),
        SITUATION => Ok("137b052e7cc460c51773e76fe724fffaf91c720e"),
        CATALOG => Ok("2247420aebdd83bbefd8431150877d88f5d33fa2"),
        REGISTRY => Ok("7f5cbde527e09b9197ac0d1867dfea08be06bc8a"),
        STYLE => Ok("007d9a046f9eabca1d5f08b75d3e44da86949317"),
        LOAD_RESULT => Ok("86d9e3e4b4e6124ade6e59e3d2daec0f2d5b0420"),
        _ => Err(eyre::eyre!("unreviewed basic model")),
    }
}

fn historical_oid(path: &str, environment: &str, target: &str) -> Result<Option<&'static str>> {
    ensure!(
        matches!(environment, "release" | "dev") && TARGETS.contains(&target),
        "invalid historical model context"
    );
    if environment == "release" || (KEYBOARD_PATHS.contains(&path) && !is_d2(target)) {
        return Ok(None);
    }
    if path == COLOUR {
        Ok(Some(if is_d2(target) {
            COLOUR_ON_OID
        } else {
            COLOUR_OFF_OID
        }))
    } else {
        Ok(Some(basic_oid(path)?))
    }
}

fn historical_features(environment: &str, target: &str) -> &'static [&'static str] {
    if environment == "release" {
        &[]
    } else if is_d2(target) {
        &FULL_D2
    } else {
        &["client_theme"]
    }
}

fn assert_standalone_snapshot(source: &str) -> Result<()> {
    for forbidden in ["SFMWorkspacePanelId", "originatingPanelId", "panelId"] {
        ensure!(
            !source.contains(forbidden),
            "standalone keyboard snapshot retained panel API"
        );
    }
    for required in [
        "originatingHostIsCurrent);",
        "return new SFMKeyboardUsageContextSnapshot(null, () -> true, null, 0, 0, List.of(situations));",
        "host == that.host",
        "Objects.equals(elementId, that.elementId)",
        "workspaceFocusRevision",
        "elementFocusRevision",
        "situations = List.copyOf(situations);",
        "situationDepths = Map.copyOf(situationDepths);",
    ] {
        ensure!(
            source.contains(required),
            "standalone snapshot lost non-panel contract"
        );
    }
    Ok(())
}

#[test]
fn theme_keyboard_models_reconstruct_all_twenty_frozen_raw_memberships() -> Result<()> {
    let fixture = ModelFixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (context, _) in CONTEXT_COMMITS {
        let (environment, target) = context.split_once('/').expect("fixed context");
        let features = historical_features(environment, target);
        let explicit = fixture.core.context(target, features)?;
        fixture.assert_models(&explicit)?;
        for path in PATHS {
            let expected = historical_oid(path, environment, target)?;
            let output = fixture.render(path, &explicit)?;
            assert_eq!(
                output.as_deref(),
                expected.map(|oid| fixture.raw[oid].as_slice())
            );
            if expected.is_some() {
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (40, 120));
    Ok(())
}

#[test]
fn theme_keyboard_models_feature_off_controls_omit_before_source_read() -> Result<()> {
    let mut fixture = ModelFixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture
        .core
        .core
        .join("deliberately_missing_theme_keyboard_model_inputs");
    for key in catalog.catalog.0.keys() {
        fixture.assert_models(&fixture.core.historical_feature_off_catalog_context(key)?)?;
    }
    Ok(())
}

#[test]
fn theme_keyboard_models_independent_owners_and_standalone_snapshot_match_real_api() -> Result<()> {
    let fixture = ModelFixture::load()?;
    let mut cases = 0;
    for target in &TARGETS[..2] {
        for mask in 0_u8..32 {
            let mut features = vec![];
            if mask & 1 != 0 {
                features.push("client_theme");
            }
            if mask & 2 != 0 {
                features.extend(["client_actions", "keyboard_profiles"]);
            }
            if mask & 4 != 0 {
                features.push("workspace_panels");
            }
            if mask & 8 != 0 {
                features.push("explorer_search");
            }
            if mask & 16 != 0 {
                features.push("editor_search");
            }
            let context = fixture.core.context(target, &features)?;
            fixture.assert_models(&context)?;
            let action = fixture.core.read_source(
                "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionContext.java",
            )?;
            let action = render_java_source(std::str::from_utf8(&action)?, &context)?;
            if context.features["workspace_panels"] {
                assert!(action.contains("@Nullable SFMWorkspacePanelId originatingPanelId"));
            } else {
                assert!(!action.contains("SFMWorkspacePanelId"));
                assert!(action.contains("BooleanSupplier originatingHostIsCurrent\n)"));
            }
            for forbidden in [
                "developer_tools",
                "mod_event_filtering",
                "workspace_keyboard_context",
            ] {
                assert!(!context.features[forbidden]);
            }
            if let Some(colour) = fixture.render(COLOUR, &context)? {
                let text = std::str::from_utf8(&colour)?;
                for role in ["SEARCH_FIND", "SEARCH_FILTER", "SEARCH_INTERSECTION"] {
                    assert_eq!(text.contains(role), mask & 8 != 0);
                }
            }
            cases += 1;
        }
    }
    assert_eq!(cases, 64);
    Ok(())
}

#[test]
fn theme_keyboard_models_later_support_and_invalid_prerequisites_fail_closed() -> Result<()> {
    let fixture = ModelFixture::load()?;
    for target in &TARGETS[2..] {
        for mask in 0_u8..8 {
            let mut features = vec![];
            if mask & 1 != 0 {
                features.push("client_theme");
            }
            if mask & 2 != 0 {
                features.extend(["client_actions", "keyboard_profiles"]);
            }
            if mask & 4 != 0 {
                features.push("workspace_panels");
            }
            fixture.assert_models(&fixture.core.context(target, &features)?)?;
        }
        for unsupported in ["explorer_search", "editor_search"] {
            assert!(fixture.core.context(target, &[unsupported]).is_err());
        }
    }
    for target in TARGETS {
        assert!(
            fixture
                .core
                .context(target, &["keyboard_profiles"])
                .is_err()
        );
        assert!(
            fixture
                .core
                .context(target, &["theme_model_unknown_owner"])
                .is_err()
        );
        // A keyboard profile grant is not a theme, workspace or explorer grant.
        let standalone = fixture
            .core
            .context(target, &["client_actions", "keyboard_profiles"])?;
        assert!(!standalone.features["client_theme"]);
        assert!(!standalone.features["workspace_panels"]);
        assert!(!standalone.features["explorer_search"]);
        assert!(!standalone.features["developer_tools"]);
        fixture.assert_models(&standalone)?;
    }
    Ok(())
}

#[test]
fn theme_keyboard_models_source_selection_is_not_environment_or_key_dispatch() -> Result<()> {
    let fixture = ModelFixture::load()?;
    for target in TARGETS {
        for environment in ["release", "dev"] {
            let features = if is_d2(target) {
                &FULL_D2[..]
            } else {
                &["client_theme"][..]
            };
            let mut context = fixture.core.context(target, features)?;
            context.environment = environment.to_owned();
            context.projection_key = format!("review/theme-keyboard/{environment}/{target}");
            context.preset = context.projection_key.clone();
            fixture.assert_models(&context)?;
            let mut off = fixture.core.context(target, &[])?;
            off.environment = environment.to_owned();
            off.projection_key = format!("review/models-off/{environment}/{target}");
            off.preset = off.projection_key.clone();
            fixture.assert_models(&off)?;
        }
    }
    Ok(())
}

#[test]
fn theme_keyboard_models_common_edit_and_source_mutation_proofs_are_bounded() -> Result<()> {
    let fixture = ModelFixture::load()?;
    let source = fixture.core.read_source(COLOUR)?;
    let source = std::str::from_utf8(&source)?;
    let original = "TEXT_PRIMARY(\"text.primary\", 0xFFFFFFFF)";
    ensure!(
        source.matches(original).count() == 1,
        "reviewed shared edit anchor changed"
    );
    let edited = source.replace(original, "TEXT_PRIMARY(\"text.primary\", 0xFF123456)");
    let mut propagated = 0;
    for target in ["1.19.2", "1.20.4", "26.1.2"] {
        let context = fixture.core.context(target, &["client_theme"])?;
        let before = render_java_source(source, &context)?;
        let after = render_java_source(&edited, &context)?;
        assert_eq!(
            after,
            before.replace(original, "TEXT_PRIMARY(\"text.primary\", 0xFF123456)")
        );
        assert!(!after.contains("SEARCH_FIND"));
        propagated += 1;
    }
    assert_eq!(propagated, 3);
    let context = fixture.core.context("1.19.2", &FULL_D2)?;
    assert!(
        render_java_source(
            &format!("{{% if features.unregistered_owner %}}\n{source}{{% endif %}}\n"),
            &context
        )
        .is_err()
    );
    assert!(
        render_java_source(
            &format!("{{% if environment %}}\n{source}{{% endif %}}\n"),
            &context
        )
        .is_err()
    );
    let mut changed_raw = fixture.raw[COLOUR_ON_OID].clone();
    changed_raw[0] = b'P';
    assert!(validate_raw_body(raw_pin(COLOUR_ON_OID)?, &changed_raw).is_err());
    let mut changed_source = source.as_bytes().to_vec();
    changed_source[0] = b'P';
    assert!(validate_template_body(template_pin(COLOUR)?, &changed_source).is_err());
    let mut extra_newline = fixture.raw[COLOUR_ON_OID].clone();
    extra_newline.push(b'\n');
    assert!(validate_raw_body(raw_pin(COLOUR_ON_OID)?, &extra_newline).is_err());
    let mut bom_prefixed = vec![0xef, 0xbb, 0xbf];
    bom_prefixed.extend_from_slice(&fixture.raw[COLOUR_ON_OID]);
    assert!(validate_raw_body(raw_pin(COLOUR_ON_OID)?, &bom_prefixed).is_err());
    let mut invalid = fixture.core.metadata.clone();
    invalid
        .source_rules
        .get_mut(SNAPSHOT)
        .expect("validated rule")[0]
        .when
        .all_features
        .clear();
    let off = fixture.core.context("1.19.2", &[])?;
    let selected = select_core_inputs(&invalid, &off, &fixture.inventory)?;
    assert!(
        selected.inputs.contains_key(SNAPSHOT),
        "membership mutation negative no longer intersects the guard"
    );
    assert!(fixture.render(SNAPSHOT, &off)?.is_none());
    Ok(())
}

#[test]
fn theme_keyboard_models_preserve_pure_model_contracts_and_real_provider_gap() -> Result<()> {
    let fixture = ModelFixture::load()?;
    for (path, required) in [
        (
            PROVIDER,
            &["SFMKeyboardUsageContextSnapshot keyboardUsageContextSnapshot();"][..],
        ),
        (SITUATION, &["parents = List.copyOf(parents);"][..]),
        (
            CATALOG,
            &[
                "Unknown keyboard usage situation parent",
                "Keyboard usage situation ancestry cycle",
                "depths.putIfAbsent(id, depth)",
                "return new ActiveAncestry(List.copyOf(depths.keySet()), Map.copyOf(depths));",
                "if (active.contains(first) && active.contains(second)) return true;",
            ][..],
        ),
        (
            REGISTRY,
            &[
                ".onlyIf(SFMEnvironmentUtils::isClient)",
                ".createNewRegistry()",
                "exact built-in catalog before Forge has frozen the registry.",
                "public static Map<ResourceLocation, SFMKeyboardUsageSituation> builtIns()",
            ][..],
        ),
        (
            STYLE,
            &[
                "base.withColor(colour).withBold(bold).withItalic(italic).withUnderlined(underlined)",
            ][..],
        ),
        (
            LOAD_RESULT,
            &[
                "Optional<SFMClientTheme> theme",
                "List.copyOf(diagnostics)",
                "theme.isPresent() && diagnostics.isEmpty()",
            ][..],
        ),
    ] {
        let source = std::str::from_utf8(&fixture.raw[basic_oid(path)?])?;
        for marker in required {
            ensure!(
                source.contains(marker),
                "lost model boundary: {path} / {marker}"
            );
        }
        for forbidden in [
            "java.nio.file.",
            "ProcessBuilder",
            "SFMPacket",
            "Runtime.getRuntime",
        ] {
            assert!(
                !source.contains(forbidden),
                "leaf gained effect/policy coupling: {path}"
            );
        }
    }
    // The actual theme provider remains outside this slice; retaining its real
    // type is required, but its presence/complete build closure is not assumed.
    let result = fixture
        .render(
            LOAD_RESULT,
            &fixture.core.context("1.19.2", &["client_theme"])?,
        )?
        .expect("selected theme model");
    assert!(std::str::from_utf8(&result)?.contains("Optional<SFMClientTheme> theme"));
    assert!(!std::str::from_utf8(&result)?.contains("class SFMClientTheme"));
    Ok(())
}
