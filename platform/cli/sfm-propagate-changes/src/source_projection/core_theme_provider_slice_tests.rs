//! Actual-selector/renderer proofs for two real theme/keyboard providers.
//!
//! Historical Git blobs are bounded test-only witnesses. Preview metadata keeps
//! its real Rule type; its wider graph remains outside this source slice. No
//! synthetic provider or full Java/runtime acceptance is supplied by these tests.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::discover_core_source_files;
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

const THEME: &str = "src/main/java/ca/teamdman/sfm/client/theme/SFMClientTheme.java";
const REGISTRAR: &str =
    "src/main/java/ca/teamdman/sfm/client/registry/SFMKeyboardUsageSituationRegistrations.java";
const PATHS: [&str; 2] = [THEME, REGISTRAR];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const OPTIONAL: [&str; 3] = [
    "theme_file_icon_matching",
    "theme_file_icon_defaults",
    "theme_preview_rules",
];
const FULL_D2: [&str; 6] = [
    "client_theme",
    "theme_file_icon_matching",
    "theme_file_icon_defaults",
    "theme_preview_rules",
    "client_actions",
    "keyboard_profiles",
];
const LEDGER: &str = "docs/tasks/sfm-core-theme-provider-slice.json";
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
const RAW_PINS: [RawPin; 5] = [
    RawPin {
        oid: "f88a6ec9caade1045e8791f05ac78377c616e634",
        digest: "sha256:bbf13532c7fc58bab3557f53fd5e4a8cab6a6f45c9a1b30a914b491fbc6c63c5",
        bytes: 2762,
    },
    RawPin {
        oid: "3504396e653e535a8264c89f7be57a6bfebcfcac",
        digest: "sha256:345f9776f53453dc17d0c62ded735e3b04ed6213b3a2c8dff7698fec433173b3",
        bytes: 10418,
    },
    RawPin {
        oid: "7c43f787c37c2d2592e2e6ad1c433ed766046773",
        digest: "sha256:6b6df66f81bbc471573aa3ac7334d74f556088de3810765220c1f78852c08f20",
        bytes: 3442,
    },
    RawPin {
        oid: "8ab60eddffd42082d891edc33280730bf4c554c3",
        digest: "sha256:0ac9fa41b59aea06d1a59c2cc66aae17eaac6c228f882df23705054730176d6c",
        bytes: 3504,
    },
    RawPin {
        oid: "fd35951fbc87ba03a2e05a441fe0b39f4fb78872",
        digest: "sha256:482904fa3b9d00641461171e2b51101314d6143c4b64f72df54876a415054726",
        bytes: 3486,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    context_commits: BTreeMap<String, String>,
    files: Vec<InputEvidence>,
    raw_blobs: BTreeMap<String, RawEvidence>,
}

#[derive(Facet)]
struct RawEvidence {
    oid: String,
    raw_sha256: String,
    raw_bytes: usize,
    bom: bool,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    normalization: String,
}

#[derive(Facet)]
struct InputEvidence {
    path: String,
    core_path: String,
    template_sha256: String,
    template_bytes: usize,
    raw_oids: Vec<String>,
    member_owners: Vec<String>,
    membership: Membership,
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

struct ProviderFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}

impl ProviderFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        for feature in OPTIONAL {
            let definition = core
                .features
                .0
                .get(feature)
                .ok_or_else(|| eyre::eyre!("missing reviewed optional theme owner"))?;
            ensure!(
                same_names(&definition.supported_targets, &TARGETS[..2])
                    && same_names(&definition.requires, &["client_theme"]),
                "optional theme owner support/prerequisite changed"
            );
        }
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core_theme_provider_slice@1"
                && ledger.normalization == "none_raw_exact_lf_final_lf"
                && ledger.context_commits == pinned_commits()
                && ledger.files.len() == PATHS.len()
                && ledger.raw_blobs.len() == RAW_PINS.len(),
            "provider migration contract changed"
        );
        for pin in RAW_PINS {
            let raw = ledger
                .raw_blobs
                .get(pin.oid)
                .ok_or_else(|| eyre::eyre!("missing provider raw witness"))?;
            ensure!(
                raw.oid == pin.oid
                    && raw.raw_sha256 == pin.digest
                    && raw.raw_bytes == pin.bytes
                    && !raw.bom
                    && raw.crlf_count == 0
                    && raw.lone_cr_count == 0
                    && raw.final_lf
                    && raw.normalization == "none",
                "provider raw byte policy changed"
            );
        }
        let mut paths = BTreeSet::new();
        for input in &ledger.files {
            let (digest, bytes) = template_identity(&input.path)?;
            let (owner, targets) = if input.path == THEME {
                ("client_theme", &[][..])
            } else {
                ("keyboard_profiles", &TARGETS[..2])
            };
            ensure!(
                paths.insert(input.path.clone())
                    && PATHS.contains(&input.path.as_str())
                    && input.core_path
                        == format!("platform/minecraft/core-liquid-template/{}", input.path)
                    && input.template_sha256 == digest
                    && input.template_bytes == bytes
                    && same_names(&input.membership.targets, targets)
                    && same_names(&input.membership.all_features, &[owner])
                    && input.membership.any_features.is_empty()
                    && input.membership.none_features.is_empty()
                    && same_names(
                        &input.member_owners,
                        if input.path == THEME {
                            &OPTIONAL[..]
                        } else {
                            &[]
                        }
                    ),
                "provider source/membership/member-owner changed"
            );
            let rules = core
                .metadata
                .source_rules
                .get(&input.path)
                .ok_or_else(|| eyre::eyre!("provider has no source predicate"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == input.path
                    && rules[0].template
                    && same_names(&rules[0].when.targets, targets)
                    && same_names(&rules[0].when.all_features, &[owner])
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "provider metadata does not match reviewed source membership"
            );
            validate_template(&input.path, &core.read_source(&input.path)?)?;
            let mut seen = BTreeSet::new();
            let mut witnessed_oids = BTreeSet::new();
            ensure!(input.witnesses.len() == 20, "incomplete provider witnesses");
            for witness in &input.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("bad witness context"))?;
                let oid = historical_oid(&input.path, environment, target)?;
                let pin = oid.map(raw_pin).transpose()?;
                ensure!(
                    seen.insert(witness.context.clone())
                        && pinned_commits().get(&witness.context) == Some(&witness.source_commit)
                        && witness.present == oid.is_some()
                        && witness.raw_oid.as_deref() == oid
                        && witness.raw_sha256.as_deref() == pin.map(|p| p.digest)
                        && witness.raw_bytes == pin.map(|p| p.bytes)
                        && witness.mode.as_deref() == oid.map(|_| "100644")
                        && same_names(
                            &witness.explicit_features,
                            historical_features(environment, target)
                        )
                        && witness.features_origin
                            == "reviewed_current_explicit_reconstruction_not_historical_manifest",
                    "provider frozen tree/raw/context witness changed"
                );
                core.context(target, historical_features(environment, target))?;
                if let Some(oid) = oid {
                    witnessed_oids.insert(oid.to_owned());
                }
            }
            ensure!(
                input.raw_oids.len() == witnessed_oids.len()
                    && input.raw_oids.iter().cloned().collect::<BTreeSet<_>>() == witnessed_oids,
                "provider raw variant set changed"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &RAW_PINS.iter().map(|p| p.oid.to_owned()).collect(),
        )?;
        for pin in RAW_PINS {
            validate_raw(pin, &raw[pin.oid])?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "missing genuine provider"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }

    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selected.inputs.get(path) else {
            ensure!(
                selected.omitted_paths.contains(path),
                "unexplained provider omission"
            );
            return Ok(None);
        };
        ensure!(input.input == path, "unreviewed alternative provider input");
        let source = self.core.read_source(path)?;
        validate_template(path, &source)?;
        Ok(Some(
            render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes(),
        ))
    }
}

fn pinned_commits() -> BTreeMap<String, String> {
    CONTEXT_COMMITS
        .into_iter()
        .map(|(key, value)| (key.to_owned(), value.to_owned()))
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
fn template_identity(path: &str) -> Result<(&'static str, usize)> {
    match path {
        THEME => Ok((
            "sha256:fff0ddb181cd8d05ac9457f799a6b8857494b3bdd35d790ab550d9acf90ccbfb",
            12717,
        )),
        REGISTRAR => Ok((
            "sha256:bbf13532c7fc58bab3557f53fd5e4a8cab6a6f45c9a1b30a914b491fbc6c63c5",
            2762,
        )),
        _ => Err(eyre::eyre!("unreviewed provider path")),
    }
}
fn raw_pin(oid: &str) -> Result<RawPin> {
    RAW_PINS
        .iter()
        .find(|pin| pin.oid == oid)
        .copied()
        .ok_or_else(|| eyre::eyre!("unreviewed provider raw object"))
}
fn validate_template(path: &str, bytes: &[u8]) -> Result<()> {
    let (digest, count) = template_identity(path)?;
    ensure!(
        bytes.len() == count && sha256(bytes) == digest,
        "unreviewed provider template edit"
    );
    Ok(())
}
fn validate_raw(pin: RawPin, bytes: &[u8]) -> Result<()> {
    ensure!(
        bytes.len() == pin.bytes
            && sha256(bytes) == pin.digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.ends_with(b"\n\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "provider raw bytes/BOM/newline drift"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
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
fn base_theme_oid(target: &str) -> &'static str {
    match target {
        "1.21.0" | "1.21.1" => "8ab60eddffd42082d891edc33280730bf4c554c3",
        "26.1.2" => "fd35951fbc87ba03a2e05a441fe0b39f4fb78872",
        _ => "7c43f787c37c2d2592e2e6ad1c433ed766046773",
    }
}
fn historical_oid(path: &str, environment: &str, target: &str) -> Result<Option<&'static str>> {
    ensure!(
        matches!(environment, "release" | "dev")
            && TARGETS.contains(&target)
            && PATHS.contains(&path),
        "unreviewed provider context"
    );
    if environment == "release" {
        return Ok(None);
    }
    Ok(if path == REGISTRAR {
        is_d2(target).then_some("f88a6ec9caade1045e8791f05ac78377c616e634")
    } else if is_d2(target) {
        Some("3504396e653e535a8264c89f7be57a6bfebcfcac")
    } else {
        Some(base_theme_oid(target))
    })
}

#[test]
fn theme_providers_reconstruct_all_forty_frozen_raw_membership_cells() -> Result<()> {
    let fixture = ProviderFixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXT_COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed context");
        let context = fixture
            .core
            .context(target, historical_features(environment, target))?;
        for path in PATHS {
            let expected = historical_oid(path, environment, target)?;
            assert_eq!(
                fixture.render(path, &context)?.as_deref(),
                expected.map(|oid| fixture.raw[oid].as_slice())
            );
            if expected.is_some() {
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (12, 28));
    Ok(())
}

#[test]
fn theme_providers_independent_d2_masks_keep_real_base_provider_and_keyboard_api() -> Result<()> {
    let fixture = ProviderFixture::load()?;
    let mut cases = 0;
    for target in &TARGETS[..2] {
        for mask in 0_u8..32 {
            let mut enabled = vec!["client_theme"];
            if mask & 1 != 0 {
                enabled.push("theme_file_icon_matching");
            }
            if mask & 2 != 0 {
                enabled.push("theme_file_icon_defaults");
            }
            if mask & 4 != 0 {
                enabled.push("theme_preview_rules");
            }
            if mask & 8 != 0 {
                enabled.extend(["client_actions", "keyboard_profiles"]);
            }
            if mask & 16 != 0 {
                enabled.push("workspace_panels");
            }
            let context = fixture.core.context(target, &enabled)?;
            let theme = fixture
                .render(THEME, &context)?
                .expect("real selected theme");
            let source = std::str::from_utf8(&theme)?;
            assert_eq!(
                source.contains("matchingFileIcon(String fileName)"),
                mask & 1 != 0
            );
            assert_eq!(
                source.contains("fileIconForName(String fileName)"),
                mask & 1 != 0
            );
            assert_eq!(
                source.contains("private static boolean isExtensionless"),
                mask & 1 != 0
            );
            assert_eq!(source.contains("files.put(\".gradle\""), mask & 2 != 0);
            assert_eq!(
                source.contains("String id, String fallbackId, String label"),
                mask & 2 != 0
            );
            assert_eq!(
                source.contains("List<SFMItemstackPreviewRules.Rule> previewRules"),
                mask & 4 != 0
            );
            assert_eq!(
                source.contains("Set<String> explicitFileIcons"),
                mask & 4 != 0
            );
            assert_eq!(source.contains("withPreviewRules"), mask & 4 != 0);
            if mask & 7 == 0 {
                assert_eq!(theme, fixture.raw[base_theme_oid(target)]);
            }
            if mask & 7 == 7 {
                assert_eq!(
                    theme,
                    fixture.raw["3504396e653e535a8264c89f7be57a6bfebcfcac"]
                );
            }
            let keyboard = fixture.render(REGISTRAR, &context)?;
            assert_eq!(
                keyboard.as_deref(),
                (mask & 8 != 0)
                    .then(|| fixture.raw["f88a6ec9caade1045e8791f05ac78377c616e634"].as_slice())
            );
            for forbidden in [
                "developer_tools",
                "mod_event_filtering",
                "explorer_search",
                "workspace_keyboard_context",
            ] {
                assert!(!context.features[forbidden]);
            }
            assert!(
                !source.contains("class SFMItemstackPreviewRules"),
                "a surrogate preview provider was introduced"
            );
            cases += 1;
        }
    }
    assert_eq!(cases, 64);
    Ok(())
}

#[test]
fn theme_providers_feature_off_controls_and_unsupported_prerequisites_fail_closed() -> Result<()> {
    let mut fixture = ProviderFixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture.core.core.join("missing_provider_input_boundary");
    for key in catalog.catalog.0.keys() {
        let context = fixture.core.historical_feature_off_catalog_context(key)?;
        for path in PATHS {
            assert!(fixture.render(path, &context)?.is_none());
        }
    }
    for target in TARGETS {
        assert!(
            fixture
                .core
                .context(target, &["keyboard_profiles"])
                .is_err()
        );
        for owner in OPTIONAL {
            assert!(fixture.core.context(target, &[owner]).is_err());
        }
        assert!(
            fixture
                .core
                .context(target, &["theme_provider_unknown_owner"])
                .is_err()
        );
    }
    for target in &TARGETS[2..] {
        for owner in OPTIONAL {
            assert!(
                fixture
                    .core
                    .context(target, &["client_theme", owner])
                    .is_err()
            );
        }
    }
    Ok(())
}

#[test]
fn theme_providers_later_version_api_and_descriptive_identity_do_not_select_snapshots() -> Result<()>
{
    let fixture = ProviderFixture::load()?;
    for target in TARGETS {
        for environment in ["release", "dev"] {
            let mut context = fixture.core.context(target, &["client_theme"])?;
            context.environment = environment.to_owned();
            context.projection_key = format!("review/provider/{environment}/{target}");
            context.preset = context.projection_key.clone();
            assert_eq!(
                fixture
                    .render(THEME, &context)?
                    .expect("real base provider"),
                fixture.raw[base_theme_oid(target)]
            );
            assert!(fixture.render(REGISTRAR, &context)?.is_none());
            let mut keyboard = fixture
                .core
                .context(target, &["client_actions", "keyboard_profiles"])?;
            keyboard.environment = environment.to_owned();
            keyboard.projection_key = format!("review/keyboard-provider/{environment}/{target}");
            keyboard.preset = keyboard.projection_key.clone();
            assert!(fixture.render(THEME, &keyboard)?.is_none());
            let expected = is_d2(target)
                .then(|| fixture.raw["f88a6ec9caade1045e8791f05ac78377c616e634"].as_slice());
            assert_eq!(fixture.render(REGISTRAR, &keyboard)?.as_deref(), expected);
        }
    }
    Ok(())
}

#[test]
fn theme_providers_shared_edit_mutation_refusal_and_real_gap_are_source_only() -> Result<()> {
    let fixture = ProviderFixture::load()?;
    let template = fixture.core.read_source(THEME)?;
    let text = std::str::from_utf8(&template)?;
    let anchor =
        "Fully resolved immutable theme snapshot; rendering never reads configuration files.";
    ensure!(
        text.matches(anchor).count() == 1,
        "shared provider anchor changed"
    );
    let edited = text.replace(anchor, "Reviewed shared immutable theme source.");
    for target in ["1.19.2", "1.21.1", "26.1.2"] {
        let context = fixture.core.context(target, &["client_theme"])?;
        let before = render_java_source(text, &context)?;
        let after = render_java_source(&edited, &context)?;
        assert_eq!(
            after,
            before.replace(anchor, "Reviewed shared immutable theme source.")
        );
    }
    let mut changed_template = template.clone();
    changed_template[0] = b'P';
    assert!(validate_template(THEME, &changed_template).is_err());
    let pin = raw_pin("3504396e653e535a8264c89f7be57a6bfebcfcac")?;
    let mut changed_raw = fixture.raw[pin.oid].clone();
    changed_raw.push(b'\n');
    assert!(validate_raw(pin, &changed_raw).is_err());
    let mut prefixed = vec![0xef, 0xbb, 0xbf];
    prefixed.extend_from_slice(&fixture.raw[pin.oid]);
    assert!(validate_raw(pin, &prefixed).is_err());
    let context = fixture.core.context("1.19.2", &FULL_D2)?;
    assert!(
        render_java_source(
            &format!("{{% if features.unregistered_theme_owner %}}\n{text}{{% endif %}}\n"),
            &context
        )
        .is_err()
    );
    let provider = fixture
        .render(THEME, &context)?
        .expect("full source-only provider");
    let provider = std::str::from_utf8(&provider)?;
    for marker in [
        "Map.copyOf(colours)",
        "Missing colour role",
        "Missing default syntax style",
        "List.copyOf(previewRules)",
        "Set.copyOf(explicitFileIcons)",
        "Unknown explicit file icon key",
        "Map<ResourceLocation, SFMItemIcon> actionIcons",
        "List<SFMItemstackPreviewRules.Rule> previewRules",
    ] {
        assert!(
            provider.contains(marker),
            "lost genuine provider contract: {marker}"
        );
    }
    for forbidden in [
        "Files.read",
        "Files.write",
        "ProcessBuilder",
        "class SFMItemstackPreviewRules",
    ] {
        assert!(!provider.contains(forbidden));
    }
    let registrar = std::str::from_utf8(&fixture.raw["f88a6ec9caade1045e8791f05ac78377c616e634"])?;
    assert!(registrar.contains("SFMKeyboardUsageSituations.createContributor(SFM.MOD_ID)"));
    assert!(registrar.contains("REGISTERER.register(bus);"));
    assert!(!registrar.contains("import ca.teamdman.sfm.client.screen."));
    Ok(())
}
