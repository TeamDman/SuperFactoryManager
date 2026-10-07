//! Source-only proofs for four real preview models and immutable explorer data.
//!
//! Exact Git objects are bounded test witnesses, never production inputs. The
//! whole Entry API is data, not authority. Wider resolver/theme/runtime closure
//! remains explicit in the portable ledger. No Java execution is claimed.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::InputPredicate;
use super::core_inputs::InputVariant;
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

const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const ENTRY_OWNERS: [&str; 8] = [
    "theme_preview_rules",
    "file_explorer",
    "registry_explorer",
    "release_review",
    "java_symbols",
    "explorer_search",
    "explorer_compaction",
    "explorer_navigation",
];
const PRIOR_PROVIDER_OWNERS: [&str; 3] = ["editor_documents", "file_explorer", "registry_explorer"];
const PROVIDER_OWNERS: [&str; 9] = [
    "editor_documents",
    "file_explorer",
    "registry_explorer",
    "theme_preview_rules",
    "release_review",
    "java_symbols",
    "explorer_search",
    "explorer_compaction",
    "explorer_navigation",
];
// Keep the nine-owner v1 ledger contract immutable; only the current live rule
// gains the independently reviewed notification metadata consumer.
const CURRENT_PROVIDER_OWNERS: [&str; 10] = [
    "editor_documents",
    "file_explorer",
    "registry_explorer",
    "theme_preview_rules",
    "release_review",
    "java_symbols",
    "explorer_search",
    "explorer_compaction",
    "explorer_navigation",
    "workspace_notifications",
];
// Live availability adds captured CLI and counterfactual consumers; the v1 ledger stays strict.
const CURRENT_ENTRY_OWNERS: [&str; 10] = [
    "theme_preview_rules",
    "file_explorer",
    "registry_explorer",
    "release_review",
    "java_symbols",
    "explorer_search",
    "explorer_compaction",
    "explorer_navigation",
    "client_control_cli",
    "workspace_counterfactuals",
];
const THEME_SET: [&str; 2] = ["client_theme", "theme_preview_rules"];
const LEDGER: &str = "docs/tasks/sfm-core-theme-preview-models-slice.json";
const ENTRY: &str = "src/main/java/ca/teamdman/sfm/client/explorer/lazy/SFMExplorerEntry.java";
const THEME: &str = "src/main/java/ca/teamdman/sfm/client/theme/SFMClientTheme.java";

#[derive(Clone, Copy)]
struct Pin {
    path: &'static str,
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}
const PINS: [Pin; 5] = [
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewRules.java",
        oid: "cbc287346f798b42b69a093165ce6e286ea2cf5e",
        digest: "sha256:75fd2d25401600f62264b98f6b225c29066f1a429cae4ef02fcb8ac798c0bade",
        bytes: 6430,
    },
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewExpression.java",
        oid: "56b4de6591a93711f1bd946e5067dac4e1902eb1",
        digest: "sha256:f662a509b7ce4ee23d0f7c1a79898307a58eacd1b2aefbb40a3d099b17728879",
        bytes: 8267,
    },
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewOperators.java",
        oid: "0811682b8ea153516d18b32c8f7847a351bdd578",
        digest: "sha256:dbae593474c89b5effce854a912a04235ebc524bbd85fb2500468ee03008c1d3",
        bytes: 5977,
    },
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewSubject.java",
        oid: "908d73e7a2508d6c2beeec69c950a3cb977ee931",
        digest: "sha256:48c4724b9161e17cbf6ca392472eb2dc4222331a179106c701eca97066744d15",
        bytes: 3017,
    },
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/explorer/lazy/SFMExplorerEntry.java",
        oid: "96a15c0e38590e3640b252fb0fc46c882a85fd0d",
        digest: "sha256:42fd2bbed2630cf2a5eee4467197506ab44aea53377cd2929dada33646ff9d5f",
        bytes: 6197,
    },
];
#[derive(Clone, Copy)]
struct ProviderPin {
    path: &'static str,
    digest: &'static str,
    bytes: usize,
}
const PROVIDERS: [ProviderPin; 3] = [
    ProviderPin {
        path: "src/main/java/ca/teamdman/sfm/client/explorer/SFMPath.java",
        digest: "sha256:bc943185664f9c8acd7501660601896aa4e8944334d2e61ae12ca53e8521654b",
        bytes: 22104,
    },
    ProviderPin {
        path: "src/main/java/ca/teamdman/sfm/client/explorer/SFMCanonicalText.java",
        digest: "sha256:fa77e2ad754a5bdb837028686d870c264edfa477d310d27ec91f13d369c82705",
        bytes: 7446,
    },
    ProviderPin {
        path: "src/main/java/ca/teamdman/sfm/client/explorer/SFMParseException.java",
        digest: "sha256:e1fb1f11a650308a3d8066716bdf88dd7ad5c95c6f6b59cdda0bf4da5c4127a4",
        bytes: 530,
    },
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

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    context_commits: BTreeMap<String, String>,
    files: Vec<InputEvidence>,
    raw_blobs: BTreeMap<String, RawEvidence>,
    feature_contracts: Vec<FeatureContract>,
    proposed_provider_unions: Vec<ProviderExpansionEvidence>,
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
    normalized_sha256: String,
    normalized_bytes: usize,
}

#[derive(Facet)]
struct InputEvidence {
    path: String,
    core_path: String,
    template_sha256: String,
    template_bytes: usize,
    raw_oids: Vec<String>,
    member_owners: Vec<String>,
    membership: InputPredicate,
    template: bool,
    version_seams: Vec<String>,
    functional_member_seams: Vec<String>,
    witnesses: Vec<Witness>,
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

#[derive(Facet)]
struct FeatureContract {
    name: String,
    supported_targets: Vec<String>,
    requires: Vec<String>,
}

#[derive(Facet)]
struct ProviderExpansionEvidence {
    path: String,
    source_bytes: usize,
    source_sha256: String,
    prior_rules: Vec<InputVariant>,
    proposed_rules: Vec<InputVariant>,
    prior_ledger_unchanged: bool,
    live_rule_changed: bool,
}

struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}

impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core_theme_preview_models_slice@1"
                && ledger.normalization == "none_raw_exact"
                && ledger.context_commits == pinned_commits()
                && ledger.files.len() == PINS.len()
                && ledger.raw_blobs.len() == PINS.len()
                && ledger.feature_contracts.len() == PROVIDER_OWNERS.len() + 1
                && ledger.proposed_provider_unions.len() == PROVIDERS.len(),
            "preview ledger identity or scope drift"
        );
        let mut contracts = BTreeSet::new();
        for contract in &ledger.feature_contracts {
            ensure!(
                contracts.insert(contract.name.as_str()),
                "duplicate owner evidence"
            );
            ensure!(
                PROVIDER_OWNERS.contains(&contract.name.as_str())
                    || contract.name == "client_theme",
                "unreviewed preview owner"
            );
            let targets = if contract.name == "client_theme" {
                &TARGETS[..]
            } else {
                &TARGETS[..2]
            };
            let requires = if contract.name == "theme_preview_rules" {
                &["client_theme"][..]
            } else {
                &[][..]
            };
            let registered = core
                .features
                .0
                .get(&contract.name)
                .ok_or_else(|| eyre::eyre!("missing real preview owner"))?;
            ensure!(
                same_names(&contract.supported_targets, targets)
                    && same_names(&contract.requires, requires)
                    && same_names(&registered.supported_targets, targets)
                    && same_names(&registered.requires, requires),
                "preview supported-target or prerequisite drift"
            );
        }
        let mut paths = BTreeSet::new();
        for input in &ledger.files {
            let pin = pin_for(&input.path)?;
            ensure!(paths.insert(input.path.as_str()), "duplicate preview input");
            let owners = if input.path == ENTRY {
                &ENTRY_OWNERS[..]
            } else {
                &["theme_preview_rules"][..]
            };
            ensure!(
                input.core_path == format!("platform/minecraft/core-liquid-template/{}", pin.path)
                    && input.template_sha256 == pin.digest
                    && input.template_bytes == pin.bytes
                    && input.raw_oids == [pin.oid.to_owned()]
                    && same_names(&input.member_owners, owners)
                    && input.template
                    && input.version_seams.is_empty()
                    && input.functional_member_seams.is_empty()
                    && input.witnesses.len() == CONTEXT_COMMITS.len(),
                "preview authored input evidence changed"
            );
            validate_predicate(&input.membership, pin.path == ENTRY)?;
            validate_rule(
                &core.metadata.source_rules,
                pin.path,
                if pin.path == ENTRY {
                    &CURRENT_ENTRY_OWNERS
                } else {
                    &[]
                },
                if pin.path == ENTRY {
                    &[]
                } else {
                    &["theme_preview_rules"]
                },
            )?;
            let raw = &ledger.raw_blobs[pin.oid];
            ensure!(
                raw.oid == pin.oid
                    && raw.raw_sha256 == pin.digest
                    && raw.raw_bytes == pin.bytes
                    && !raw.bom
                    && raw.crlf_count == 0
                    && raw.lone_cr_count == 0
                    && raw.final_lf
                    && raw.normalization == "none_raw_exact"
                    && raw.normalized_sha256 == pin.digest
                    && raw.normalized_bytes == pin.bytes,
                "preview raw or no-normalization evidence changed"
            );
            let mut contexts = BTreeSet::new();
            for witness in &input.witnesses {
                ensure!(
                    contexts.insert(witness.context.as_str()),
                    "duplicate preview context"
                );
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid pinned preview context"))?;
                let present = environment == "dev" && is_d2(target);
                ensure!(
                    ledger.context_commits.get(&witness.context) == Some(&witness.source_commit)
                        && witness.present == present
                        && witness.raw_oid.as_deref() == present.then_some(pin.oid)
                        && witness.raw_sha256.as_deref() == present.then_some(pin.digest)
                        && witness.raw_bytes == present.then_some(pin.bytes)
                        && witness.mode.as_deref() == present.then_some("100644")
                        && same_names(
                            &witness.explicit_features,
                            historical_features(environment, target)
                        )
                        && witness.features_origin
                            == "reviewed_current_explicit_reconstruction_not_historical_manifest",
                    "preview frozen membership/context/raw witness changed"
                );
                core.context(target, historical_features(environment, target))?;
            }
            ensure!(
                contexts.len() == CONTEXT_COMMITS.len(),
                "incomplete preview context matrix"
            );
        }
        let mut provider_paths = BTreeSet::new();
        for proposal in &ledger.proposed_provider_unions {
            let pin = PROVIDERS
                .iter()
                .find(|pin| pin.path == proposal.path)
                .ok_or_else(|| eyre::eyre!("unreviewed provider expansion"))?;
            ensure!(
                provider_paths.insert(proposal.path.as_str())
                    && proposal.source_bytes == pin.bytes
                    && proposal.source_sha256 == pin.digest
                    && proposal.prior_ledger_unchanged
                    && !proposal.live_rule_changed,
                "prior provider evidence changed"
            );
            validate_variants(&proposal.prior_rules, pin.path, &PRIOR_PROVIDER_OWNERS, &[])?;
            validate_variants(&proposal.proposed_rules, pin.path, &PROVIDER_OWNERS, &[])?;
            validate_rule(
                &core.metadata.source_rules,
                pin.path,
                &current_provider_owners(pin.path),
                &[],
            )?;
        }
        let raw = read_git_blobs(
            &core.repository,
            &PINS.iter().map(|pin| pin.oid.to_owned()).collect(),
        )?;
        for pin in PINS {
            validate_bytes(pin, &raw[pin.oid])?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PINS.iter().all(|pin| inventory.contains(pin.path))
                && PROVIDERS.iter().all(|pin| inventory.contains(pin.path)),
            "missing real preview or pure provider"
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
                "unexplained preview omission"
            );
            return Ok(None);
        };
        ensure!(
            input.input == path && input.template,
            "unreviewed alternate preview provider"
        );
        let bytes = self.core.read_source(path)?;
        if let Ok(pin) = pin_for(path) {
            validate_bytes(pin, &bytes)?;
        } else {
            let pin = PROVIDERS
                .iter()
                .find(|pin| pin.path == path)
                .ok_or_else(|| eyre::eyre!("unreviewed selected helper"))?;
            ensure!(
                bytes.len() == pin.bytes && sha256(&bytes) == pin.digest,
                "pure provider source changed"
            );
        }
        Ok(Some(
            render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes(),
        ))
    }
}

fn validate_predicate(predicate: &InputPredicate, entry: bool) -> Result<()> {
    let any: &[&str] = if entry { &ENTRY_OWNERS } else { &[] };
    let all: &[&str] = if entry { &[] } else { &["theme_preview_rules"] };
    ensure!(
        same_names(&predicate.targets, &TARGETS[..2])
            && same_names(&predicate.all_features, all)
            && same_names(&predicate.any_features, any)
            && predicate.none_features.is_empty(),
        "preview selection predicate changed"
    );
    Ok(())
}
fn validate_variants(
    variants: &[InputVariant],
    path: &str,
    any: &[&str],
    all: &[&str],
) -> Result<()> {
    ensure!(variants.len() == 1, "unreviewed source alternatives");
    let variant = &variants[0];
    ensure!(
        variant.input == path
            && variant.template
            && same_names(&variant.when.targets, &TARGETS[..2])
            && same_names(&variant.when.any_features, any)
            && same_names(&variant.when.all_features, all)
            && variant.when.none_features.is_empty(),
        "source membership is not the reviewed real-consumer rule"
    );
    Ok(())
}
fn validate_rule(
    rules: &BTreeMap<String, Vec<InputVariant>>,
    path: &str,
    any: &[&str],
    all: &[&str],
) -> Result<()> {
    validate_variants(
        rules
            .get(path)
            .ok_or_else(|| eyre::eyre!("missing reviewed source rule"))?,
        path,
        any,
        all,
    )
}
// The captured ownership arrays above remain the historical ledger contract.
// Only checked live metadata gains these independently witnessed neutral consumers.
fn current_provider_owners(path: &str) -> Vec<&'static str> {
    let mut owners = CURRENT_PROVIDER_OWNERS.to_vec();
    // Selection graph projection consumes SFMPath, whose parser needs the
    // other two unchanged pure providers. Keep the v1 receipt lists unchanged.
    owners.push("selection_history");
    if path.ends_with("/SFMPath.java") {
        owners.extend([
            "context_actions",
            "client_control_cli",
            "workspace_counterfactuals",
        ]);
    } else {
        owners.extend([
            "client_control_cli",
            "client_overlay_scenes",
            "review_sessions",
            "route_comparison",
            "trajectory_panels",
            "workspace_counterfactuals",
        ]);
    }
    owners
}
fn pinned_commits() -> BTreeMap<String, String> {
    CONTEXT_COMMITS
        .into_iter()
        .map(|(k, v)| (k.to_owned(), v.to_owned()))
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
fn historical_features(environment: &str, target: &str) -> &'static [&'static str] {
    if environment == "dev" && is_d2(target) {
        &THEME_SET
    } else {
        &[]
    }
}
fn pin_for(path: &str) -> Result<Pin> {
    PINS.iter()
        .find(|pin| pin.path == path)
        .copied()
        .ok_or_else(|| eyre::eyre!("unreviewed preview path"))
}
fn validate_bytes(pin: Pin, bytes: &[u8]) -> Result<()> {
    ensure!(
        bytes.len() == pin.bytes
            && sha256(bytes) == pin.digest
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf])
            && bytes.ends_with(b"\n")
            && !bytes.ends_with(b"\n\n"),
        "unreviewed preview raw/template bytes or newline normalization"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

#[test]
fn preview_models_reconstruct_all_hundred_frozen_raw_and_membership_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXT_COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed context");
        let context = fixture
            .core
            .context(target, historical_features(environment, target))?;
        for pin in PINS {
            let expected =
                (environment == "dev" && is_d2(target)).then(|| fixture.raw[pin.oid].as_slice());
            assert_eq!(fixture.render(pin.path, &context)?.as_deref(), expected);
            if expected.is_some() {
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (10, 90));
    Ok(())
}

#[test]
fn preview_models_independent_entry_consumer_masks_keep_pure_provider_closure() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cases = 0;
    for target in &TARGETS[..2] {
        for mask in 0_u16..256 {
            let mut enabled = Vec::new();
            if mask & 1 != 0 {
                enabled.push("client_theme");
            }
            for (index, owner) in ENTRY_OWNERS.iter().enumerate() {
                if mask & (1 << index) != 0 {
                    enabled.push(*owner);
                }
            }
            let context = fixture.core.context(target, &enabled)?;
            for pin in PINS {
                let expected = if pin.path == ENTRY {
                    mask != 0
                } else {
                    mask & 1 != 0
                };
                assert_eq!(
                    fixture.render(pin.path, &context)?.as_deref(),
                    expected.then(|| fixture.raw[pin.oid].as_slice())
                );
            }
            for provider in PROVIDERS {
                let output = fixture.render(provider.path, &context)?;
                assert_eq!(output.is_some(), mask != 0);
                if let Some(bytes) = output {
                    assert_eq!(bytes.len(), provider.bytes);
                    assert_eq!(sha256(&bytes), provider.digest);
                }
            }
            // Pure model selection must not add editor, keyboard or workspace privileges.
            for owner in [
                "editor_documents",
                "workspace_panels",
                "keyboard_profiles",
                "client_program_actions",
                "developer_tools",
                "game_puppet_runtime",
            ] {
                assert!(
                    !context.features[owner],
                    "unrelated owner enabled implicitly: {owner}"
                );
            }
            cases += 1;
        }
    }
    assert_eq!(cases, 512);
    for target in &TARGETS[..2] {
        let context = fixture.core.context(target, &["selection_history"])?;
        for provider in PROVIDERS {
            let body = fixture
                .render(provider.path, &context)?
                .expect("selection graph pure provider");
            assert_eq!(body.len(), provider.bytes);
            assert_eq!(sha256(&body), provider.digest);
        }
        for pin in PINS {
            assert!(
                fixture.render(pin.path, &context)?.is_none(),
                "selection history does not select preview or explorer Entry"
            );
        }
        for unrelated in [
            "editor_documents",
            "workspace_panels",
            "client_actions",
            "theme_preview_rules",
            "file_explorer",
        ] {
            assert!(
                !context.features[unrelated],
                "pure dependency must not widen authority: {unrelated}"
            );
        }
    }
    // The pre-existing editor owner still selects the original pure providers,
    // but not Entry or preview members. Its immutable historical rule is retained.
    for target in &TARGETS[..2] {
        let context = fixture.core.context(target, &["editor_documents"])?;
        for provider in PROVIDERS {
            assert!(fixture.render(provider.path, &context)?.is_some());
        }
        for pin in PINS {
            assert!(fixture.render(pin.path, &context)?.is_none());
        }
    }
    Ok(())
}

#[test]
fn preview_models_feature_off_controls_omit_before_read_and_refuse_unsupported_owners() -> Result<()>
{
    let mut fixture = Fixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture.core.core.join("missing_preview_input_boundary");
    for key in catalog.catalog.0.keys() {
        let context = fixture.core.historical_feature_off_catalog_context(key)?;
        for pin in PINS {
            assert!(fixture.render(pin.path, &context)?.is_none());
        }
    }
    for target in TARGETS {
        assert!(
            fixture
                .core
                .context(target, &["theme_preview_rules"])
                .is_err()
        );
        assert!(
            fixture
                .core
                .context(target, &["unregistered_preview_owner"])
                .is_err()
        );
        let context = fixture.core.context(target, &["client_theme"])?;
        for pin in PINS {
            assert!(fixture.render(pin.path, &context)?.is_none());
        }
    }
    for target in &TARGETS[2..] {
        for owner in ENTRY_OWNERS {
            let enabled = if owner == "theme_preview_rules" {
                vec!["client_theme", owner]
            } else {
                vec![owner]
            };
            assert!(fixture.core.context(target, &enabled).is_err());
        }
    }
    Ok(())
}

#[test]
fn preview_models_descriptive_key_and_environment_do_not_select_historical_bodies() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        for environment in ["release", "dev"] {
            let mut off = fixture.core.context(target, &[])?;
            off.environment = environment.to_owned();
            off.projection_key = format!("review/immutable-preview/{environment}/{target}");
            off.preset = off.projection_key.clone();
            for pin in PINS {
                assert!(fixture.render(pin.path, &off)?.is_none());
            }
            if is_d2(target) {
                let mut enabled = fixture.core.context(target, &THEME_SET)?;
                enabled.environment = environment.to_owned();
                enabled.projection_key = off.projection_key.clone();
                enabled.preset = enabled.projection_key.clone();
                for pin in PINS {
                    assert_eq!(
                        fixture
                            .render(pin.path, &enabled)?
                            .expect("real data model"),
                        fixture.raw[pin.oid]
                    );
                }
            }
        }
    }
    Ok(())
}

#[test]
fn preview_models_keep_actual_data_parser_and_conservative_rule_contracts_source_only() -> Result<()>
{
    let fixture = Fixture::load()?;
    let context = fixture.core.context("1.19.2", &THEME_SET)?;
    let rules = std::str::from_utf8(&fixture.raw[PINS[0].oid])?;
    for marker in [
        "Rule set exceeds 1024 entries",
        "UNAVAILABLE",
        "AMBIGUOUS",
        "Unknown implication is never a numeric priority",
        "List.copyOf(candidates)",
        "SFMItemIcon icon",
        "NoSuchAlgorithmException",
    ] {
        assert!(rules.contains(marker), "lost real rule contract: {marker}");
    }
    let expression = std::str::from_utf8(&fixture.raw[PINS[1].oid])?;
    for marker in [
        "MAX_CHARS = 8192",
        "MAX_NODES = 128",
        "MAX_DEPTH = 16",
        "MAX_LITERAL_CODEPOINTS = 1024",
        "List.copyOf(operands)",
        "Wrong arity",
        "Wrong type",
        "Invalid Unicode in rule literal",
        "Unescaped control character",
        "Unexpected trailing rule text",
        "operator identities are not executable commands",
    ] {
        assert!(
            expression.contains(marker),
            "lost real expression contract: {marker}"
        );
    }
    let operators = std::str::from_utf8(&fixture.raw[PINS[2].oid])?;
    for marker in [
        "Operator arity exceeds 8",
        "Operator registry exceeds 256 signatures",
        "Duplicate operator",
        "Unknown rule operator",
        "Locale.ROOT",
        "SCHEMA_ONLY",
        "Collections.unmodifiableMap",
    ] {
        assert!(
            operators.contains(marker),
            "lost real typed operator contract: {marker}"
        );
    }
    let subject = std::str::from_utf8(&fixture.raw[PINS[3].oid])?;
    for marker in [
        "SFMExplorerEntry.SUBJECT_NAME",
        "SFMExplorerEntry.SUBJECT_KIND",
        "List.copyOf(suffixes)",
        "Map.copyOf(metadata)",
        "Character.charCount",
    ] {
        assert!(
            subject.contains(marker),
            "lost real subject contract: {marker}"
        );
    }
    let entry = std::str::from_utf8(&fixture.raw[PINS[4].oid])?;
    for marker in [
        "A sort key must contain either a value or an unavailable reason",
        "Every explorer entry must contribute an available name key",
        "Explorer search terms must contain non-empty values",
        "Collections.unmodifiableMap(immutableKeys)",
        "List.copyOf(searchTerms)",
        "List.copyOf(diagnostics)",
        "PRESENTATION_ICON_FALLBACK",
        "PRESENTATION_ICON_LABEL",
        "SUBJECT_NAME",
        "SUBJECT_KIND",
        "PRIMARY_ACTION_OPEN",
        "MOUNT_PROVIDER",
        "SUBJECT_COMPLETE_CHILD_COUNT",
        "boolean opensOnActivate()",
        "resolver did not contribute sort key",
    ] {
        assert!(
            entry.contains(marker),
            "lost real immutable data API: {marker}"
        );
    }
    for pin in PINS {
        let source = std::str::from_utf8(&fixture.raw[pin.oid])?;
        for forbidden in [
            "Files.read",
            "Files.write",
            "ProcessBuilder",
            "SFMClientActions.",
            "Minecraft.getInstance()",
            "class SFMClientThemeService",
        ] {
            assert!(
                !source.contains(forbidden),
                "surrogate or authority entered a pure source"
            );
        }
        assert_eq!(
            fixture
                .render(pin.path, &context)?
                .expect("real selected model"),
            fixture.raw[pin.oid]
        );
    }
    let path = fixture
        .render(PROVIDERS[0].path, &context)?
        .expect("real path provider");
    let canonical = fixture
        .render(PROVIDERS[1].path, &context)?
        .expect("real Unicode provider");
    assert!(std::str::from_utf8(&path)?.contains("SFMCanonicalText."));
    assert!(std::str::from_utf8(&canonical)?.contains("new SFMParseException("));
    let theme = render_java_source(
        std::str::from_utf8(&fixture.core.read_source(THEME)?)?,
        &context,
    )?;
    assert!(theme.contains("List<SFMItemstackPreviewRules.Rule> previewRules"));
    assert!(!theme.contains("class SFMItemstackPreviewRules"));
    Ok(())
}

#[test]
fn preview_models_shared_edit_and_exact_byte_mutation_refusals_are_bounded() -> Result<()> {
    let fixture = Fixture::load()?;
    let pin = pin_for(ENTRY)?;
    let bytes = &fixture.raw[pin.oid];
    let text = std::str::from_utf8(bytes)?;
    let anchor = "Resolver-contributed presentation and sort metadata for one logical path.";
    ensure!(
        text.matches(anchor).count() == 1,
        "shared immutable-entry anchor drift"
    );
    let edited = text.replace(
        anchor,
        "Reviewed immutable resolver data for one logical path.",
    );
    for target in &TARGETS[..2] {
        let context = fixture.core.context(target, &THEME_SET)?;
        let before = render_java_source(text, &context)?;
        let after = render_java_source(&edited, &context)?;
        assert_eq!(
            after,
            before.replace(
                anchor,
                "Reviewed immutable resolver data for one logical path."
            )
        );
        assert!(
            render_java_source(
                &format!("{{% if features.unregistered_preview_owner %}}\n{text}{{% endif %}}\n"),
                &context
            )
            .is_err()
        );
    }
    let mut changed = bytes.clone();
    changed[0] = b'P';
    assert!(validate_bytes(pin, &changed).is_err());
    let mut extra_lf = bytes.clone();
    extra_lf.push(b'\n');
    assert!(validate_bytes(pin, &extra_lf).is_err());
    assert!(validate_bytes(pin, &bytes[..bytes.len() - 1]).is_err());
    let mut bom = vec![0xef, 0xbb, 0xbf];
    bom.extend_from_slice(bytes);
    assert!(validate_bytes(pin, &bom).is_err());
    let crlf = text.replace('\n', "\r\n");
    assert!(validate_bytes(pin, crlf.as_bytes()).is_err());
    Ok(())
}
