//! Authentic two-file tooltip-mode action circuit, source preparation only.
//!
//! Future admission tests use the real Fixture, selector and Java renderer.
//! Retained-tree witnesses are immutable source evidence, not old feature
//! defaults, Java compilation, loader registration or runtime acceptance.
//! No Git reader, child process or generated-project collector is used.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_client_registration_current_contract::CLIENT_REGISTRAR_CURRENT;
use super::core_client_registration_current_contract::CLIENT_REGISTRAR_PRE_WORKSPACE;
use super::core_client_registration_current_contract::reviewed_pre_workspace_registrar;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputPredicate;
use super::core_inputs::InputVariant;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use sha1::Digest;
use sha1::Sha1;
use std::collections::BTreeSet;

const ACTION: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMTooltipModeAction.java";
const ACTIONS: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMTooltipModeActions.java";
const CLIENT_REGISTRAR: &str = "src/main/java/ca/teamdman/sfm/client/SFMClientRegistrations.java";
const CLIENT_SOURCE: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionSource.java";
const SERVICE: &str = "src/main/java/ca/teamdman/sfm/client/tooltip/SFMTooltipModeService.java";
const RESULT: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionStructuredResult.java";
const LEDGER: &str = "docs/tasks/sfm-core-tooltip-mode-action-slice.json";
const LEDGER_SHA: &str = "sha256:ac19bae114b602163037c3ade2ed2f2d3615fd3690f587e7a526fbef0ebc9795";
const FEATURES: [&str; 3] = [
    "client_actions",
    "tooltip_mode_override",
    "structured_action_results",
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const RAW: [(&str, u64, &str, &str, &str); 2] = [
    (
        ACTION,
        3084,
        "sha256:2436c2e345bbfef6f71f4997e94cfdbddd839685fca8759d06489ce1a638f3e2",
        "6541548d48e06a8aa668c5193bcbdce055ef4a10",
        "143.stdout.bin",
    ),
    (
        ACTIONS,
        943,
        "sha256:8ea204d840dbe4f62e0a1e844cd04be3eeffdbe64407f5657434ce8a7cd19e31",
        "89e7bf1baeb4f739c09ff24e86e4c2af143ffe9b",
        "177.stdout.bin",
    ),
];
const PREPARED: [(&str, u64, &str); 2] = [
    (
        ACTION,
        3196,
        "sha256:88e52cdb5f5ac12d3386a1ece0f135af048a799431864e8807af35447def0719",
    ),
    (
        ACTIONS,
        943,
        "sha256:8ea204d840dbe4f62e0a1e844cd04be3eeffdbe64407f5657434ce8a7cd19e31",
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
#[facet(deny_unknown_fields)]
struct Ledger {
    schema: String,
    status: String,
    source_cohort: Cohort,
    source_paths: Vec<RawSource>,
    witness_feature_context_origin: String,
    contexts: usize,
    path_context_cells: usize,
    present_cells: usize,
    absent_cells: usize,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct Cohort {
    role: String,
    report: String,
    bytes: u64,
    sha256: String,
}
#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct RawSource {
    path: String,
    raw_bytes: u64,
    raw_sha256: String,
    raw_blob: String,
    raw_evidence: String,
}
#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct Witness {
    context: String,
    commit: String,
    target: String,
    present: bool,
    action_blob: Option<String>,
    registrar_blob: Option<String>,
    explicit_owner_roots: Vec<String>,
    proof: String,
}
struct Fixture {
    shared: CoreTestFixture,
    inventory: BTreeSet<String>,
    ledger: Ledger,
    action: String,
    actions: String,
    registrar: String,
    client_source: String,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), 64 * 1024)?;
        ensure!(
            bytes.len() == 7710 && sha256(&bytes) == LEDGER_SHA,
            "immutable tooltip authenticity ledger changed"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        let inventory = discover_core_source_files(&shared.core)?;
        for (path, count, digest) in PREPARED {
            ensure!(
                inventory.contains(path),
                "tooltip input not physically promoted"
            );
            validate_bytes(&shared.read_source(path)?, count, digest)?;
            ensure!(
                shared.metadata.source_rules.get(path) == Some(&vec![expected_rule(path)]),
                "tooltip membership must preserve the independent owner conjunction"
            );
        }
        for (name, support, requires) in [
            ("client_actions", &TARGETS[..], &[][..]),
            ("tooltip_mode_override", &TARGETS[..2], &[][..]),
            (
                "structured_action_results",
                &TARGETS[..2],
                &["client_actions"][..],
            ),
        ] {
            let definition = shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("tooltip feature absent: {name}"))?;
            ensure!(
                definition
                    .supported_targets
                    .iter()
                    .map(String::as_str)
                    .collect::<Vec<_>>()
                    == support
                    && definition
                        .requires
                        .iter()
                        .map(String::as_str)
                        .collect::<Vec<_>>()
                        == requires,
                "existing support or requires[] changed: {name}"
            );
        }
        reviewed_pre_workspace_registrar(&shared.read_source(CLIENT_REGISTRAR)?)?;
        for (path, count, digest) in [
            (
                CLIENT_SOURCE,
                1857,
                "sha256:bb3a637c1ed303d5f16d2f4ddf177950076a3cfdfd89eb037e0ec6bb08e0e037",
            ),
            (
                SERVICE,
                1126,
                "sha256:bf4b5c4c1ebdcac7ff5b803db3bf55d7ecbf137157640141ab760531b3734f17",
            ),
            (
                RESULT,
                2492,
                "sha256:d6deefc66052c4138aa6889c0fd0b4366d36711808928cf19debb3011e4d64b7",
            ),
        ] {
            validate_bytes(&shared.read_source(path)?, count, digest)?;
        }
        let action = String::from_utf8(shared.read_source(ACTION)?)?;
        let actions = String::from_utf8(shared.read_source(ACTIONS)?)?;
        let registrar = String::from_utf8(shared.read_source(CLIENT_REGISTRAR)?)?;
        let client_source = String::from_utf8(shared.read_source(CLIENT_SOURCE)?)?;
        let result = Self {
            shared,
            inventory,
            ledger,
            action,
            actions,
            registrar,
            client_source,
        };
        result.validate_ledger()?;
        Ok(result)
    }

    fn validate_ledger(&self) -> Result<()> {
        let ledger = &self.ledger;
        ensure!(
            ledger.schema == "sfm:tooltip_mode_action_authenticity@1"
                && ledger.status
                    == "authentic_retained_twenty_context_source_witnesses_not_historical_feature_defaults_or_runtime_acceptance"
                && ledger.witness_feature_context_origin
                    == "reconstructed_explicit_owner_selection_not_historical_feature_flag_claim"
                && ledger.contexts == 20
                && ledger.path_context_cells == 40
                && ledger.present_cells == 4
                && ledger.absent_cells == 36,
            "tooltip finite witness scope changed"
        );
        ensure!(
            ledger.source_cohort.role == "sfm-action-family-frozen-body-stage-20261002-v2"
                && ledger.source_cohort.report == "frozen-action-family-source-cohort.json"
                && ledger.source_cohort.bytes == 590397
                && ledger.source_cohort.sha256
                    == "1fa03b55b404fb635b3bc9267d56bf081a267007efcc225dc8aa5d88d9e286b1",
            "tooltip retained-body cohort changed"
        );
        ensure!(
            ledger.source_paths.len() == 2 && ledger.witnesses.len() == 20,
            "tooltip source or context count changed"
        );
        for (row, (path, count, digest, blob, raw)) in ledger.source_paths.iter().zip(RAW) {
            ensure!(
                row.path == path
                    && row.raw_bytes == count
                    && format!("sha256:{}", row.raw_sha256) == digest
                    && row.raw_blob == blob
                    && row.raw_evidence == raw,
                "tooltip immutable acquired raw identity changed"
            );
        }
        let mut seen = BTreeSet::new();
        let mut present = 0;
        for (row, (context, commit)) in ledger.witnesses.iter().zip(CONTEXTS) {
            let (_, target) = context
                .split_once('/')
                .ok_or_else(|| eyre::eyre!("tooltip witness context malformed"))?;
            let expected = context.starts_with("dev/") && d2(target);
            ensure!(
                seen.insert(context)
                    && row.context == context
                    && row.commit == commit
                    && row.target == target
                    && row.present == expected
                    && row.action_blob.as_deref() == expected.then_some(RAW[0].3)
                    && row.registrar_blob.as_deref() == expected.then_some(RAW[1].3)
                    && row
                        .explicit_owner_roots
                        .iter()
                        .map(String::as_str)
                        .collect::<Vec<_>>()
                        == if expected {
                            FEATURES.to_vec()
                        } else {
                            Vec::new()
                        }
                    && row.proof
                        == if expected {
                            "exact_acquired_raw_blob_in_retained_tree"
                        } else {
                            "exact_retained_tree_membership_absence"
                        },
                "tooltip retained-tree witness or reconstructed explicit roots changed"
            );
            present += usize::from(expected) * 2;
        }
        ensure!(
            present == 4 && 40 - present == 36,
            "tooltip witness cell accounting changed"
        );
        Ok(())
    }

    fn check(&self, context: &ProjectionContext) -> Result<()> {
        check_wiring(
            self,
            &self.shared.metadata,
            context,
            &self.action,
            &self.registrar,
        )
    }
}
fn d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn enabled(context: &ProjectionContext, feature: &str) -> bool {
    context.features.get(feature).copied().unwrap_or(false)
}
fn expected_rule(path: &str) -> InputVariant {
    InputVariant {
        input: path.to_owned(),
        template: true,
        when: InputPredicate {
            targets: TARGETS[..2].iter().map(|name| (*name).to_owned()).collect(),
            all_features: FEATURES[..2]
                .iter()
                .map(|name| (*name).to_owned())
                .collect(),
            any_features: Vec::new(),
            none_features: Vec::new(),
        },
    }
}
fn validate_bytes(bytes: &[u8], count: u64, digest: &str) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count
            && sha256(bytes) == digest
            && bytes.ends_with(b"\n")
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "exact tooltip LF source identity changed"
    );
    Ok(())
}
fn blob(bytes: &[u8]) -> String {
    let mut digest = Sha1::new();
    digest.update(format!("blob {}\0", bytes.len()).as_bytes());
    digest.update(bytes);
    format!("{:x}", digest.finalize())
}
fn roots(mask: usize) -> Vec<&'static str> {
    FEATURES
        .into_iter()
        .enumerate()
        .filter_map(|(bit, name)| (mask & (1 << bit) != 0).then_some(name))
        .collect()
}
fn check_wiring(
    fixture: &Fixture,
    metadata: &CoreProjectInputs,
    context: &ProjectionContext,
    action_source: &str,
    registrar_source: &str,
) -> Result<()> {
    let selection = select_core_inputs(metadata, context, &fixture.inventory)?;
    let target = selection.target_id.as_str();
    let expected = d2(target) && enabled(context, FEATURES[0]) && enabled(context, FEATURES[1]);
    for path in [ACTION, ACTIONS] {
        if let Some(input) = selection.inputs.get(path) {
            ensure!(
                expected
                    && input.input == path
                    && input.template
                    && !selection.omitted_paths.contains(path),
                "tooltip selection widened or routed to an alternate"
            );
        } else {
            ensure!(
                !expected && selection.omitted_paths.contains(path),
                "tooltip conjunction must explicitly omit both action inputs"
            );
        }
    }
    let selected_registrar = selection.inputs.get(CLIENT_REGISTRAR);
    ensure!(
        !expected || selected_registrar.is_some(),
        "selected tooltip action needs its real selected registrar"
    );
    if let Some(input) = selected_registrar {
        ensure!(
            input.input == CLIENT_REGISTRAR && !selection.omitted_paths.contains(CLIENT_REGISTRAR),
            "selected client registrar must use its real input"
        );
        let registrar = render_java_source(registrar_source, context)?;
        for reference in [
            "import ca.teamdman.sfm.client.action.SFMTooltipModeActions;",
            "        SFMTooltipModeActions.register(bus);",
        ] {
            ensure!(
                registrar.matches(reference).count() == usize::from(expected),
                "real registrar and action selection disagree"
            );
        }
    } else {
        ensure!(
            selection.omitted_paths.contains(CLIENT_REGISTRAR),
            "inactive client registrar must be explicitly omitted"
        );
    }
    if expected {
        let action = render_java_source(action_source, context)?;
        let actions = render_java_source(&fixture.actions, context)?;
        let structured = enabled(context, FEATURES[2]);
        for reference in [
            "import com.google.gson.JsonObject;",
            "JsonObject result = new JsonObject();",
            "publishStructuredResult(",
            "SFMClientActionStructuredResult.of(",
        ] {
            ensure!(
                action.matches(reference).count() == usize::from(structured),
                "optional structured result reference escaped its existing member owner"
            );
        }
        for reference in [
            "service.setMode(requested);",
            "String mode = requested.name().toLowerCase(Locale.ROOT);",
            "sendFeedback(Component.literal(\"Tooltip more info: \" + mode));",
            "return 1;",
        ] {
            ensure!(
                action.matches(reference).count() == 1,
                "independent mode/feedback circuit changed"
            );
        }
        ensure!(
            !action.contains("isExpanded(")
                && !action.contains("isKeyDown(")
                && !action.contains("{%")
                && !actions.contains("{%"),
            "mode reporting gained input polling or directives"
        );
        ensure!(
            selection.inputs.contains_key(SERVICE) && selection.inputs.contains_key(CLIENT_SOURCE),
            "actual tooltip service or action source input absent"
        );
        let client_source = render_java_source(&fixture.client_source, context)?;
        ensure!(
            client_source
                .matches("public void publishStructuredResult(")
                .count()
                == usize::from(structured)
                && selection.inputs.contains_key(RESULT) == structured
                && selection.omitted_paths.contains(RESULT) != structured,
            "actual result provider/member ownership disagrees with optional call"
        );
        for reference in [
            "tooltip/more_info/expand",
            "tooltip/more_info/compact",
            "tooltip/more_info/reset",
            "SFMClientActions.createContributor(\"sfm\")",
            "REGISTERER.register(bus);",
        ] {
            ensure!(
                actions.matches(reference).count() == 1,
                "authentic registrar circuit changed"
            );
        }
    }
    Ok(())
}

#[test]
fn tooltip_action_twenty_retained_contexts_reconstruct_four_authentic_source_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for row in &fixture.ledger.witnesses {
        // These roots reconstruct source witnesses; they are not historical or current defaults.
        let names = row
            .explicit_owner_roots
            .iter()
            .map(String::as_str)
            .collect::<Vec<_>>();
        let context = fixture.shared.context(&row.target, &names)?;
        fixture.check(&context)?;
        if row.present {
            for (source, (_, count, digest, oid, _)) in
                [&fixture.action, &fixture.actions].into_iter().zip(RAW)
            {
                let rendered = render_java_source(source, &context)?;
                validate_bytes(rendered.as_bytes(), count, digest)?;
                assert_eq!(blob(rendered.as_bytes()), oid);
                cells += 1;
            }
        }
    }
    assert_eq!(cells, 4);
    Ok(())
}

#[test]
fn tooltip_action_all_sixteen_exact_d2_masks_preserve_independence_and_real_refusals() -> Result<()>
{
    let fixture = Fixture::load()?;
    let mut legal = 0;
    let mut refused = 0;
    for target in &TARGETS[..2] {
        for mask in 0..8 {
            let names = roots(mask);
            let result = fixture.shared.context(target, &names);
            if mask & 4 != 0 && mask & 1 == 0 {
                assert!(
                    result.is_err(),
                    "{target}/{mask}: structured result requires client_actions"
                );
                refused += 1;
            } else {
                let context = result?;
                assert_eq!(
                    context.features.values().filter(|flag| **flag).count(),
                    names.len()
                );
                fixture.check(&context)?;
                legal += 1;
            }
        }
    }
    assert_eq!((legal, refused), (12, 4));
    Ok(())
}

#[test]
fn tooltip_action_without_structured_results_keeps_mode_feedback_and_service_only_owner()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        let context = fixture.shared.context(target, &FEATURES[..2])?;
        fixture.check(&context)?;
        assert!(!enabled(&context, "structured_action_results"));
        let body = render_java_source(&fixture.action, &context)?;
        assert!(!body.contains("JsonObject") && !body.contains("SFMClientActionStructuredResult"));
        let tooltip_only = fixture.shared.context(target, &["tooltip_mode_override"])?;
        fixture.check(&tooltip_only)?;
        let selected =
            select_core_inputs(&fixture.shared.metadata, &tooltip_only, &fixture.inventory)?;
        assert!(selected.inputs.contains_key(SERVICE));
        assert!(
            selected.omitted_paths.contains(ACTION) && selected.omitted_paths.contains(ACTIONS)
        );
        assert!(!enabled(&tooltip_only, "client_actions"));
    }
    Ok(())
}

#[test]
fn tooltip_action_twenty_feature_off_controls_keep_existing_omissions() -> Result<()> {
    let fixture = Fixture::load()?;
    let catalog = CoreCatalog::load(&fixture.shared.repository, &fixture.shared.repository)?;
    let mut contexts = 0;
    for key in catalog.catalog.0.keys() {
        let context = fixture.shared.historical_feature_off_catalog_context(key)?;
        assert!(!enabled(&context, "tooltip_mode_override"), "{key}");
        assert!(!enabled(&context, "structured_action_results"), "{key}");
        fixture.check(&context)?;
        contexts += 1;
    }
    assert_eq!(contexts, 20);
    Ok(())
}

#[test]
fn tooltip_action_newer_targets_preserve_all_exact_owner_support_refusals() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut legal = 0;
    let mut refused = 0;
    for target in &TARGETS[2..] {
        for mask in 0..8 {
            let result = fixture.shared.context(target, &roots(mask));
            if mask & 6 != 0 {
                assert!(
                    result.is_err(),
                    "{target}/{mask}: D2 owners must stay unsupported"
                );
                refused += 1;
            } else {
                fixture.check(&result?)?;
                legal += 1;
            }
        }
    }
    assert_eq!((legal, refused), (16, 48));
    Ok(())
}

#[test]
fn tooltip_action_real_renderer_rejects_unknown_malformed_and_unguarded_result_seams() -> Result<()>
{
    let fixture = Fixture::load()?;
    let context = fixture.shared.context("1.19.2", &FEATURES[..2])?;
    let malformed = fixture.action.replacen("{% endif %}", "{% endcase %}", 1);
    assert!(render_java_source(&malformed, &context).is_err());
    let unknown = fixture.action.replacen(
        "features.structured_action_results",
        "features.unknown_tooltip_result",
        1,
    );
    assert!(render_java_source(&unknown, &context).is_err());
    let unguarded = fixture
        .action
        .lines()
        .filter(|line| {
            *line != "{% if features.structured_action_results %}" && *line != "{% endif %}"
        })
        .map(|line| format!("{line}\n"))
        .collect::<String>();
    assert!(
        check_wiring(
            &fixture,
            &fixture.shared.metadata,
            &context,
            &unguarded,
            &fixture.registrar
        )
        .is_err()
    );
    for (path, count, digest) in PREPARED {
        let original = fixture.shared.read_source(path)?;
        let mut changed = original.clone();
        changed[0] ^= 1;
        assert!(validate_bytes(&changed, count, digest).is_err());
        assert!(validate_bytes(&original[..original.len() - 1], count, digest).is_err());
    }
    Ok(())
}

#[test]
fn tooltip_action_real_selector_and_registrar_detect_either_conjunction_drift() -> Result<()> {
    let fixture = Fixture::load()?;
    let tooltip_only = fixture
        .shared
        .context("1.19.2", &["tooltip_mode_override"])?;
    let mut widened = fixture.shared.metadata.clone();
    for path in [ACTION, ACTIONS] {
        widened
            .source_rules
            .get_mut(path)
            .ok_or_else(|| eyre::eyre!("tooltip rule missing"))?[0]
            .when
            .all_features = vec!["tooltip_mode_override".to_owned()];
    }
    assert!(
        check_wiring(
            &fixture,
            &widened,
            &tooltip_only,
            &fixture.action,
            &fixture.registrar
        )
        .is_err()
    );
    let structured_only = fixture
        .shared
        .context("1.19.2", &["client_actions", "structured_action_results"])?;
    let wrong_registrar = fixture.registrar.replace(
        "features.tooltip_mode_override",
        "features.structured_action_results",
    );
    assert!(
        check_wiring(
            &fixture,
            &fixture.shared.metadata,
            &structured_only,
            &fixture.action,
            &wrong_registrar
        )
        .is_err()
    );
    Ok(())
}

#[test]
fn tooltip_action_two_common_source_edits_reach_both_d2_targets_and_result_modes() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut outputs = 0;
    for (source, anchor) in [
        (
            &fixture.action,
            "public final class SFMTooltipModeAction implements",
        ),
        (
            &fixture.actions,
            "public final class SFMTooltipModeActions {",
        ),
    ] {
        assert_eq!(source.matches(anchor).count(), 1);
        let replacement = format!("// Reviewed common tooltip edit.\n{anchor}");
        let changed = source.replacen(anchor, &replacement, 1);
        assert_eq!(
            source
                .lines()
                .filter(|line| line.starts_with("{%"))
                .collect::<Vec<_>>(),
            changed
                .lines()
                .filter(|line| line.starts_with("{%"))
                .collect::<Vec<_>>()
        );
        for target in &TARGETS[..2] {
            for structured in [false, true] {
                let names = if structured {
                    &FEATURES[..]
                } else {
                    &FEATURES[..2]
                };
                let context = fixture.shared.context(target, names)?;
                fixture.check(&context)?;
                let original = render_java_source(source, &context)?;
                let edited = render_java_source(&changed, &context)?;
                assert_eq!(edited, original.replacen(anchor, &replacement, 1));
                outputs += 1;
            }
        }
    }
    assert_eq!(outputs, 8);
    Ok(())
}

#[test]
fn tooltip_action_current_registrar_has_exact_two_guard_inverse_without_relabeling_witnesses()
-> Result<()> {
    let fixture = Fixture::load()?;
    let current = fixture.registrar.as_bytes();
    validate_bytes(
        current,
        CLIENT_REGISTRAR_CURRENT.0,
        CLIENT_REGISTRAR_CURRENT.1,
    )?;
    let predecessor = reviewed_pre_workspace_registrar(current)?;
    validate_bytes(
        predecessor.as_bytes(),
        CLIENT_REGISTRAR_PRE_WORKSPACE.0,
        CLIENT_REGISTRAR_PRE_WORKSPACE.1,
    )?;
    let mut changed = current.to_vec();
    changed[0] ^= 1;
    assert!(reviewed_pre_workspace_registrar(&changed).is_err());
    assert!(reviewed_pre_workspace_registrar(&current[..current.len() - 1]).is_err());
    assert!(reviewed_pre_workspace_registrar(predecessor.as_bytes()).is_err());
    let wrong_owner = fixture.registrar.replace(
        "features.workspace_lifecycle or features.workspace_panel_entry_controls",
        "features.workspace_lifecycle or features.typed_command_palette",
    );
    assert!(reviewed_pre_workspace_registrar(wrong_owner.as_bytes()).is_err());
    // The immutable action witness ledger and RAW/PREPARED checks remain in Fixture::load.
    Ok(())
}

#[test]
fn tooltip_action_typed_entry_without_lifecycle_preserves_selected_omitted_wiring() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut contexts = 0;
    for target in &TARGETS[..2] {
        for typed in [false, true] {
            for tooltip in [false, true] {
                for structured in [false, true] {
                    let mut names = BTreeSet::from(["workspace_panel_entry_controls".to_owned()]);
                    if typed {
                        names.insert("typed_command_palette".to_owned());
                    }
                    if tooltip {
                        names.insert("tooltip_mode_override".to_owned());
                    }
                    if structured {
                        names.insert("structured_action_results".to_owned());
                    }
                    // Explicit test-only prerequisite closure; production defaults/requires unchanged.
                    loop {
                        let mut additions = Vec::new();
                        for name in &names {
                            let definition = fixture
                                .shared
                                .features
                                .0
                                .get(name)
                                .ok_or_else(|| eyre::eyre!("unknown owner {name}"))?;
                            additions.extend(
                                definition
                                    .requires
                                    .iter()
                                    .filter(|name| !names.contains(*name))
                                    .cloned(),
                            );
                        }
                        if additions.is_empty() {
                            break;
                        }
                        names.extend(additions);
                    }
                    let roots = names.iter().map(String::as_str).collect::<Vec<_>>();
                    let context = fixture.shared.context(target, &roots)?;
                    ensure!(
                        !enabled(&context, "workspace_lifecycle"),
                        "entry_controls must not gain lifecycle as a prerequisite"
                    );
                    fixture.check(&context)?;
                    let selection =
                        select_core_inputs(&fixture.shared.metadata, &context, &fixture.inventory)?;
                    ensure!(
                        selection.inputs.contains_key(CLIENT_REGISTRAR),
                        "selected entry owner needs its real client registrar"
                    );
                    if let Some(input) = selection.inputs.get(CLIENT_REGISTRAR) {
                        ensure!(
                            input.input == CLIENT_REGISTRAR
                                && !selection.omitted_paths.contains(CLIENT_REGISTRAR),
                            "selected registrar must use the actual source"
                        );
                        let registrar = render_java_source(&fixture.registrar, &context)?;
                        for reference in [
                            "import ca.teamdman.sfm.client.action.SFMWorkspaceLifecycleActions;",
                            "        SFMWorkspaceLifecycleActions.register(bus);",
                        ] {
                            ensure!(
                                registrar.matches(reference).count() == 1,
                                "entry-control registration disappeared without lifecycle"
                            );
                        }
                    } else {
                        ensure!(
                            selection.omitted_paths.contains(CLIENT_REGISTRAR),
                            "omitted registrar must remain explicit; never render an omitted input"
                        );
                    }
                    contexts += 1;
                }
            }
        }
    }
    assert_eq!(contexts, 16);
    Ok(())
}
