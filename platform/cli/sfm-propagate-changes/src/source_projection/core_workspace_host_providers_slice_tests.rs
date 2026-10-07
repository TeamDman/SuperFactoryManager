//! Source-only proofs for eight genuine workspace-host local/data providers.
//!
//! Raw objects are bounded historical test witnesses, never rendering inputs.
//! ReopenCatalog keeps the real missing Explorer presentation adapter; these
//! tests do not claim complete Java, UI, privilege or runtime closure.

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
const FULL: [&str; 16] = [
    "workspace_panels",
    "workspace_panel_metadata",
    "workspace_stack_controls",
    "workspace_lifecycle",
    "workspace_panel_reopening",
    "workspace_panel_actions",
    "client_actions",
    "command_palette",
    "client_theme",
    "keyboard_profiles",
    "workspace_panel_entry_controls",
    "workspace_panel_move_gestures",
    "pointer_modifier_ingress",
    "workspace_notifications",
    "font_formatted_text",
    "workspace_toast_actions",
];
const HOST_APPROVED: [&str; 5] = [
    "workspace_directional_opening",
    "workspace_focus_tracking",
    "workspace_panel_entry_controls",
    "workspace_panel_move_gestures",
    "workspace_notifications",
];
const EXISTING_DEFINITIONS: [&str; 13] = [
    "workspace_panels",
    "workspace_panel_metadata",
    "workspace_stack_controls",
    "workspace_lifecycle",
    "workspace_panel_reopening",
    "workspace_panel_actions",
    "client_actions",
    "command_palette",
    "pointer_modifier_ingress",
    "font_formatted_text",
    "workspace_toast_actions",
    "client_theme",
    "keyboard_profiles",
];
const ENTRY_GROUP: [&str; 9] = [
    "workspace_panel_entry_controls",
    "workspace_stack_controls",
    "workspace_panels",
    "workspace_panel_metadata",
    "workspace_panel_actions",
    "client_actions",
    "command_palette",
    "client_theme",
    "keyboard_profiles",
];
const PRIOR_PROVIDER_OWNERS: [&str; 3] = ["editor_documents", "file_explorer", "registry_explorer"];
const PREVIEW_PROVIDER_OWNERS: [&str; 9] = [
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
const FINAL_PROVIDER_OWNERS: [&str; 10] = [
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
const LEDGER: &str = "docs/tasks/sfm-core-workspace-host-providers-slice.json";
const PREVIEW_LEDGER: &str = "docs/tasks/sfm-core-theme-preview-models-slice.json";
const PREVIEW_LEDGER_DIGEST: &str =
    "sha256:66da92bfca798703b92dfc73a4221f208f3e44ddee1c17b8c8c1c7ebb3fb8936";

#[derive(Clone, Copy)]
struct Pin {
    path: &'static str,
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}
const PINS: [Pin; 8] = [
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPanelEntryAffordanceLayout.java",
        oid: "b482ad3d1494cfca53c5ddc507ae43407c2dab45",
        digest: "sha256:b600a0b98ff7d07940cc44b1a6b407783ae4849ff05ff9b46e3b193aa173606f",
        bytes: 3321,
    },
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMPanelReopenCatalog.java",
        oid: "832b286f82f506657a51b99c49271785022bbe54",
        digest: "sha256:edaae47b340c33205d8053a79e4610634b98cb27e28d6db7aac6b616af65b906",
        bytes: 1819,
    },
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspacePaneCloseCapture.java",
        oid: "cc0cbfdaa441b95fe2d7d306f59a5eecc3c704aa",
        digest: "sha256:24aa2fc66e9990d684858d56968dadffed422bc79be631f260c9b55b8616e9e6",
        bytes: 1041,
    },
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspacePaneCloseSummary.java",
        oid: "2501319940c3f09efcb7340561efa89bca8ee842",
        digest: "sha256:0f9e93a32203fc1e29ba0785a476b4c15bf4591d72919e0144c34651644de431",
        bytes: 1207,
    },
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMWorkspacePanelMoveInteraction.java",
        oid: "138ec23b49036e199946609ad133f6281cd4413e",
        digest: "sha256:f48290dd76e8b7cd098e6e1a521b9c773a0192242f4d92dd4151f31a74f41ab7",
        bytes: 4723,
    },
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/screen/workspace/toast/SFMWorkspaceToastContent.java",
        oid: "b48739acc138bc7cd59fcdb23d1df645be9d1bf1",
        digest: "sha256:bb025f2f0246b30e10fd2996d78b41d83261b05aaafdc8255870b7a0b3d1e326",
        bytes: 5494,
    },
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/screen/workspace/toast/SFMWorkspaceToastLayout.java",
        oid: "f1d91717484e299bdcf612e90cdd9339eb15dc67",
        digest: "sha256:8b72ad9d452bac61192a772ab3419ff9e744bded7b5234b180bf6da715d93e92",
        bytes: 2220,
    },
    Pin {
        path: "src/main/java/ca/teamdman/sfm/client/screen/workspace/toast/SFMWorkspaceToastQueue.java",
        oid: "1c4685c717f99598ea3c68f1a552604cd2fff7d5",
        digest: "sha256:630bc95e6d6f2c1c1a8071acda53be3725d61f23f3106f527f56e4abdcc6090b",
        bytes: 19118,
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
        bytes: 22104,
        digest: "sha256:bc943185664f9c8acd7501660601896aa4e8944334d2e61ae12ca53e8521654b",
    },
    ProviderPin {
        path: "src/main/java/ca/teamdman/sfm/client/explorer/SFMCanonicalText.java",
        bytes: 7446,
        digest: "sha256:fa77e2ad754a5bdb837028686d870c264edfa477d310d27ec91f13d369c82705",
    },
    ProviderPin {
        path: "src/main/java/ca/teamdman/sfm/client/explorer/SFMParseException.java",
        bytes: 530,
        digest: "sha256:e1fb1f11a650308a3d8066716bdf88dd7ad5c95c6f6b59cdda0bf4da5c4127a4",
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
    files: Vec<FileEvidence>,
    raw_blobs: BTreeMap<String, RawEvidence>,
    existing_owner_definitions: BTreeMap<String, Definition>,
    approved_not_live_host_definitions: BTreeMap<String, Definition>,
    reconstructed_full_features: Vec<String>,
    features_origin: String,
    provider_or_proposals: Vec<ProviderProposal>,
}

#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct FileEvidence {
    path: String,
    core_path: String,
    template_bytes: usize,
    template_sha256: String,
    raw_oids: Vec<String>,
    template: bool,
    membership: InputPredicate,
    member_owners: Vec<String>,
    version_seams: Vec<String>,
    functional_member_seams: Vec<String>,
    witnesses: Vec<Witness>,
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
    normalized_bytes: usize,
    normalized_sha256: String,
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
struct ProviderProposal {
    path: String,
    source_bytes: usize,
    source_sha256: String,
    live_prior_rules: Vec<InputVariant>,
    preview_proposed_rules: Vec<InputVariant>,
    final_proposed_rules: Vec<InputVariant>,
    source_body_change: bool,
    original_and_preview_evidence_unchanged: bool,
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
            ledger.schema == "sfm:core_workspace_host_providers_slice@1"
                && ledger.normalization == "none_raw_exact"
                && ledger.context_commits == pinned_commits()
                && ledger.files.len() == PINS.len()
                && ledger.raw_blobs.len() == PINS.len()
                && ledger.existing_owner_definitions.len() == EXISTING_DEFINITIONS.len()
                && ledger.approved_not_live_host_definitions.len() == HOST_APPROVED.len()
                && ledger.provider_or_proposals.len() == PROVIDERS.len()
                && same_names(&ledger.reconstructed_full_features, &FULL)
                && ledger.features_origin
                    == "reviewed_current_explicit_reconstruction_not_historical_manifest",
            "host-provider ledger identity/scope drift"
        );
        for (names, definitions) in [
            (
                &EXISTING_DEFINITIONS[..],
                &ledger.existing_owner_definitions,
            ),
            (
                &HOST_APPROVED[..],
                &ledger.approved_not_live_host_definitions,
            ),
        ] {
            ensure!(
                same_names(&definitions.keys().cloned().collect::<Vec<_>>(), names),
                "unreviewed owner-definition set"
            );
            for (name, definition) in definitions {
                let (targets, required) = owner_contract(name)?;
                let live = core
                    .features
                    .0
                    .get(name)
                    .ok_or_else(|| eyre::eyre!("approved host owner not registered yet"))?;
                ensure!(
                    same_names(&definition.supported_targets, targets)
                        && same_names(&definition.requires, required)
                        && same_names(&live.supported_targets, targets)
                        && same_names(&live.requires, required),
                    "host-provider owner support/prerequisite drift"
                );
            }
        }
        let mut paths = BTreeSet::new();
        for file in &ledger.files {
            let pin = pin_for(&file.path)?;
            let (all, any) = members(pin.path)?;
            ensure!(
                paths.insert(file.path.as_str())
                    && file.core_path
                        == format!("platform/minecraft/core-liquid-template/{}", pin.path)
                    && file.template_bytes == pin.bytes
                    && file.template_sha256 == pin.digest
                    && file.raw_oids == [pin.oid.to_owned()]
                    && file.template
                    && same_names(&file.member_owners, &[all, any].concat())
                    && file.version_seams.is_empty()
                    && file.functional_member_seams.is_empty()
                    && file.witnesses.len() == CONTEXT_COMMITS.len(),
                "host-provider authored input evidence drift"
            );
            validate_predicate(&file.membership, all, any)?;
            let (current_all, current_any) = current_members(pin.path)?;
            validate_rule(
                &core.metadata.source_rules,
                pin.path,
                current_all,
                current_any,
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
                    && raw.normalized_bytes == pin.bytes
                    && raw.normalized_sha256 == pin.digest,
                "host-provider raw/normalization evidence drift"
            );
            let mut contexts = BTreeSet::new();
            for witness in &file.witnesses {
                ensure!(
                    contexts.insert(witness.context.as_str()),
                    "duplicate frozen context"
                );
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("bad frozen context"))?;
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
                    "frozen160-cell membership/raw/context evidence drift"
                );
                core.context(target, historical_features(environment, target))?;
            }
        }
        let mut providers = BTreeSet::new();
        for proposal in &ledger.provider_or_proposals {
            let pin = PROVIDERS
                .iter()
                .find(|pin| pin.path == proposal.path)
                .ok_or_else(|| eyre::eyre!("unknown pure-provider proposal"))?;
            ensure!(
                providers.insert(proposal.path.as_str())
                    && proposal.source_bytes == pin.bytes
                    && proposal.source_sha256 == pin.digest
                    && !proposal.source_body_change
                    && proposal.original_and_preview_evidence_unchanged,
                "pure-provider source or prior evidence drift"
            );
            validate_variants(
                &proposal.live_prior_rules,
                pin.path,
                &[],
                &PRIOR_PROVIDER_OWNERS,
            )?;
            validate_variants(
                &proposal.preview_proposed_rules,
                pin.path,
                &[],
                &PREVIEW_PROVIDER_OWNERS,
            )?;
            validate_variants(
                &proposal.final_proposed_rules,
                pin.path,
                &[],
                &FINAL_PROVIDER_OWNERS,
            )?;
            validate_rule(
                &core.metadata.source_rules,
                pin.path,
                &[],
                &current_provider_owners(pin.path),
            )?;
        }
        let preview_bytes = read_bounded(
            &checked_file(&core.repository, PREVIEW_LEDGER)?,
            1024 * 1024,
        )?;
        ensure!(
            sha256(&preview_bytes) == PREVIEW_LEDGER_DIGEST,
            "frozen nine-owner preview ledger was rewritten"
        );
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
        ensure!(
            input.input == path && input.template,
            "unexpected historical/alternate provider input"
        );
        let bytes = self.core.read_source(path)?;
        if let Ok(pin) = pin_for(path) {
            validate_current_bytes(pin, &bytes)?;
        } else {
            let pin = PROVIDERS
                .iter()
                .find(|pin| pin.path == path)
                .ok_or_else(|| eyre::eyre!("unreviewed provider path"))?;
            ensure!(
                bytes.len() == pin.bytes && sha256(&bytes) == pin.digest,
                "pure path provider body changed"
            );
        }
        Ok(Some(
            render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes(),
        ))
    }
}

fn owner_contract(name: &str) -> Result<(&'static [&'static str], &'static [&'static str])> {
    let targets: &[&str] = if [
        "workspace_panels",
        "client_actions",
        "command_palette",
        "font_formatted_text",
        "client_theme",
        "keyboard_profiles",
    ]
    .contains(&name)
    {
        &TARGETS
    } else {
        &TARGETS[..2]
    };
    let requires: &[&str] = match name {
        "workspace_panels"
        | "client_actions"
        | "client_theme"
        | "font_formatted_text"
        | "workspace_lifecycle"
        | "workspace_panel_metadata"
        | "pointer_modifier_ingress" => &[],
        "workspace_panel_reopening"
        | "workspace_directional_opening"
        | "workspace_focus_tracking" => &["workspace_panels"],
        "workspace_stack_controls" => &["workspace_panels", "workspace_panel_metadata"],
        "workspace_panel_actions" | "workspace_toast_actions" => {
            &["client_actions", "workspace_panels"]
        }
        "command_palette" => &["client_actions", "client_theme", "keyboard_profiles"],
        "keyboard_profiles" => &["client_actions"],
        "workspace_panel_entry_controls" => &[
            "workspace_stack_controls",
            "workspace_panel_actions",
            "command_palette",
        ],
        "workspace_panel_move_gestures" => {
            &["workspace_panel_entry_controls", "pointer_modifier_ingress"]
        }
        "workspace_notifications" => &["workspace_panels", "font_formatted_text"],
        _ => return Err(eyre::eyre!("unreviewed host owner")),
    };
    Ok((targets, requires))
}
fn members(path: &str) -> Result<(&'static [&'static str], &'static [&'static str])> {
    let file = path
        .rsplit('/')
        .next()
        .ok_or_else(|| eyre::eyre!("missing filename"))?;
    Ok(match file {
        "SFMPanelEntryAffordanceLayout.java" => (&["workspace_panel_entry_controls"], &[]),
        "SFMPanelReopenCatalog.java" => (&["workspace_panel_reopening"], &[]),
        "SFMWorkspacePaneCloseCapture.java" | "SFMWorkspacePaneCloseSummary.java" => {
            (&["workspace_lifecycle", "workspace_stack_controls"], &[])
        }
        "SFMWorkspacePanelMoveInteraction.java" => (&["workspace_panel_move_gestures"], &[]),
        "SFMWorkspaceToastContent.java" | "SFMWorkspaceToastLayout.java" => {
            (&["workspace_notifications"], &[])
        }
        "SFMWorkspaceToastQueue.java" => {
            (&[], &["workspace_notifications", "workspace_toast_actions"])
        }
        _ => return Err(eyre::eyre!("unreviewed provider member ownership")),
    })
}
fn validate_predicate(predicate: &InputPredicate, all: &[&str], any: &[&str]) -> Result<()> {
    ensure!(
        same_names(&predicate.targets, &TARGETS[..2])
            && same_names(&predicate.all_features, all)
            && same_names(&predicate.any_features, any)
            && predicate.none_features.is_empty(),
        "host-provider membership predicate drift"
    );
    Ok(())
}
fn validate_variants(
    variants: &[InputVariant],
    path: &str,
    all: &[&str],
    any: &[&str],
) -> Result<()> {
    ensure!(variants.len() == 1, "unreviewed source alternatives");
    let variant = &variants[0];
    ensure!(
        variant.input == path && variant.template,
        "unreviewed source binding"
    );
    validate_predicate(&variant.when, all, any)
}
fn validate_rule(
    rules: &BTreeMap<String, Vec<InputVariant>>,
    path: &str,
    all: &[&str],
    any: &[&str],
) -> Result<()> {
    validate_variants(
        rules
            .get(path)
            .ok_or_else(|| eyre::eyre!("missing reviewed source rule"))?,
        path,
        all,
        any,
    )
}
// The captured ownership arrays above remain the historical ledger contract.
// Only checked live metadata gains these independently witnessed neutral consumers.
fn current_provider_owners(path: &str) -> Vec<&'static str> {
    let mut owners = FINAL_PROVIDER_OWNERS.to_vec();
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
fn validate_bytes(pin: Pin, bytes: &[u8]) -> Result<()> {
    ensure!(
        bytes.len() == pin.bytes
            && sha256(bytes) == pin.digest
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf])
            && bytes.ends_with(b"\n")
            && !bytes.ends_with(b"\n\n"),
        "unreviewed host-provider raw/template byte or line-ending drift"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

fn validate_current_bytes(pin: Pin, bytes: &[u8]) -> Result<()> {
    if pin.path != PINS[5].path {
        return validate_bytes(pin, bytes);
    }
    ensure!(
        bytes.len() == 5584
            && sha256(bytes)
                == "sha256:b0a0f0efa83cbe31f7b9c76aa905ca66ac653699cf9b76b58595313014c246b3",
        "current toast content changed outside reviewed capture boundary"
    );
    let text = std::str::from_utf8(bytes)?;
    let open = "{% if features.workspace_notifications or features.workspace_toast_actions %}\n";
    let close = "{% endif %}\n";
    ensure!(
        text.matches(open).count() == 1 && text.matches(close).count() == 1,
        "toast capture boundary is not unique"
    );
    let previous = text.replacen(open, "", 1).replacen(close, "", 1);
    validate_bytes(pin, previous.as_bytes())
}

// Independent raw-body oracle for neutral consumers. They retain formatted
// path construction but do not acquire the notification capture operation.
fn neutral_toast_body(raw: &[u8]) -> Result<Vec<u8>> {
    let text = std::str::from_utf8(raw)?;
    let start = "    public static SFMWorkspaceToastContent capture(Component message) {";
    let end = "    public static Optional<SFMPath> pathInStyle(Style style) {";
    ensure!(
        text.matches(start).count() == 1 && text.matches(end).count() == 1,
        "frozen toast capture method anchors changed"
    );
    let begin = text
        .find(start)
        .ok_or_else(|| eyre::eyre!("missing capture start"))?;
    let finish = text
        .find(end)
        .ok_or_else(|| eyre::eyre!("missing capture end"))?;
    ensure!(begin < finish, "toast capture method order changed");
    // Keep the blank separator preceding pathInStyle, as in the live template.
    Ok(format!("{}\n{}", &text[..begin], &text[finish..]).into_bytes())
}
fn pin_for(path: &str) -> Result<Pin> {
    PINS.iter()
        .find(|pin| pin.path == path)
        .copied()
        .ok_or_else(|| eyre::eyre!("unreviewed host-provider path"))
}
fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn pinned_commits() -> BTreeMap<String, String> {
    CONTEXT_COMMITS
        .into_iter()
        .map(|(k, v)| (k.to_owned(), v.to_owned()))
        .collect()
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn historical_features(environment: &str, target: &str) -> &'static [&'static str] {
    if environment == "dev" && is_d2(target) {
        &FULL
    } else {
        &[]
    }
}

// Explicit reviewed closures, not automatic traversal of arbitrary prerequisites.
fn mask_features(mask: u8) -> Vec<&'static str> {
    let mut enabled = BTreeSet::new();
    if mask & 1 != 0 {
        enabled.insert("workspace_lifecycle");
    }
    if mask & 2 != 0 {
        enabled.extend([
            "workspace_stack_controls",
            "workspace_panels",
            "workspace_panel_metadata",
        ]);
    }
    if mask & 4 != 0 {
        enabled.extend(["workspace_panel_reopening", "workspace_panels"]);
    }
    if mask & 8 != 0 {
        enabled.extend(ENTRY_GROUP);
    }
    if mask & 16 != 0 {
        enabled.extend(ENTRY_GROUP);
        enabled.extend(["workspace_panel_move_gestures", "pointer_modifier_ingress"]);
    }
    if mask & 32 != 0 {
        enabled.extend([
            "workspace_notifications",
            "workspace_panels",
            "font_formatted_text",
        ]);
    }
    if mask & 64 != 0 {
        enabled.extend([
            "workspace_toast_actions",
            "workspace_panels",
            "client_actions",
        ]);
    }
    enabled.into_iter().collect()
}
fn current_members(path: &str) -> Result<(&'static [&'static str], &'static [&'static str])> {
    if path.ends_with("/SFMWorkspaceToastContent.java") {
        Ok((
            &[],
            &[
                "workspace_notifications",
                "file_explorer",
                "java_symbols",
                "release_review",
                "workspace_counterfactuals",
                "explorer_navigation",
            ],
        ))
    } else {
        members(path)
    }
}
fn expected(path: &str, context: &ProjectionContext) -> Result<bool> {
    let (all, any) = current_members(path)?;
    Ok(all.iter().all(|name| context.features[*name])
        && (any.is_empty() || any.iter().any(|name| context.features[*name])))
}

#[test]
fn workspace_host_providers_reconstruct_all160_frozen_membership_and_raw_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXT_COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed context");
        let context = fixture
            .core
            .context(target, historical_features(environment, target))?;
        for pin in PINS {
            let golden =
                (environment == "dev" && is_d2(target)).then(|| fixture.raw[pin.oid].as_slice());
            assert_eq!(fixture.render(pin.path, &context)?.as_deref(), golden);
            if golden.is_some() {
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (16, 144));
    Ok(())
}

#[test]
fn workspace_host_providers_independent_masks_preserve_exact_local_state_and_data_api() -> Result<()>
{
    let fixture = Fixture::load()?;
    let mut cases = 0;
    for target in &TARGETS[..2] {
        for mask in 0_u8..128 {
            let enabled = mask_features(mask);
            let context = fixture.core.context(target, &enabled)?;
            for pin in PINS {
                assert_eq!(
                    fixture.render(pin.path, &context)?.as_deref(),
                    expected(pin.path, &context)?.then(|| fixture.raw[pin.oid].as_slice())
                );
            }
            for provider in PROVIDERS {
                let bytes = fixture.render(provider.path, &context)?;
                assert_eq!(bytes.is_some(), context.features["workspace_notifications"]);
                if let Some(bytes) = bytes {
                    assert_eq!(bytes.len(), provider.bytes);
                    assert_eq!(sha256(&bytes), provider.digest);
                }
            }
            for prohibited in [
                "game_puppet_runtime",
                "client_program_consent",
                "client_program_signing",
                "multiplayer_packets",
                "packet_transport_private",
                "terminal_remote",
                "file_explorer",
                "registry_explorer",
                "editor_documents",
                "theme_preview_rules",
                "release_review",
                "java_symbols",
                "explorer_compaction",
            ] {
                assert!(
                    !context.features[prohibited],
                    "unrelated authority implicitly enabled: {prohibited}"
                );
            }
            cases += 1;
        }
    }
    assert_eq!(cases, 256);
    for target in &TARGETS[..2] {
        for owner in [
            "file_explorer",
            "java_symbols",
            "release_review",
            "workspace_counterfactuals",
            "explorer_navigation",
            "selection_history",
        ] {
            let context = fixture.core.context(target, &[owner])?;
            for pin in PINS {
                let body = fixture.render(pin.path, &context)?;
                let content = pin.path.ends_with("/SFMWorkspaceToastContent.java")
                    && owner != "selection_history";
                let neutral = content
                    .then(|| neutral_toast_body(&fixture.raw[pin.oid]))
                    .transpose()?;
                assert_eq!(body.as_deref(), neutral.as_deref());
                if let Some(body) = body {
                    let text = std::str::from_utf8(&body)?;
                    assert!(text.contains("static Component pathMessage("));
                    assert!(!text.contains("SFMWorkspaceToastContent capture("));
                }
            }
            for provider in PROVIDERS {
                let body = fixture
                    .render(provider.path, &context)?
                    .expect("current pure consumer provider");
                assert_eq!(body.len(), provider.bytes);
                assert_eq!(sha256(&body), provider.digest);
            }
            for authority in [
                "workspace_notifications",
                "workspace_toast_actions",
                "workspace_panels",
                "client_actions",
            ] {
                assert!(
                    !context.features[authority],
                    "data-only consumer widened authority: {authority}"
                );
            }
        }
    }
    assert_eq!(
        mask_features(127).into_iter().collect::<BTreeSet<_>>(),
        FULL.into_iter().collect()
    );
    Ok(())
}

#[test]
fn workspace_host_providers_toast_actions_and_rendering_remain_independent() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        let action = fixture.core.context(target, &mask_features(64))?;
        let queue = fixture
            .render(PINS[7].path, &action)?
            .expect("exact queue data provider");
        assert_eq!(queue, fixture.raw[PINS[7].oid]);
        assert!(!action.features["workspace_notifications"]);
        assert!(!action.features["font_formatted_text"]);
        assert!(!action.features["client_theme"]);
        assert!(!action.features["keyboard_profiles"]);
        assert!(fixture.render(PINS[5].path, &action)?.is_none());
        assert!(fixture.render(PINS[6].path, &action)?.is_none());
        let notification = fixture.core.context(target, &mask_features(32))?;
        assert!(!notification.features["workspace_toast_actions"]);
        assert!(!notification.features["client_actions"]);
        assert!(!notification.features["command_palette"]);
        for pin in &PINS[5..] {
            assert_eq!(
                fixture
                    .render(pin.path, &notification)?
                    .expect("real notification provider"),
                fixture.raw[pin.oid]
            );
        }
    }
    Ok(())
}

#[test]
fn workspace_host_providers_feature_off_controls_and_prerequisites_fail_closed() -> Result<()> {
    let mut fixture = Fixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture.core.core.join("missing_host_provider_boundary");
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
                .context(target, &["unknown_host_provider_owner"])
                .is_err()
        );
        for owner in [
            "workspace_panel_entry_controls",
            "workspace_panel_move_gestures",
            "workspace_notifications",
            "workspace_toast_actions",
            "workspace_panel_reopening",
        ] {
            assert!(
                fixture.core.context(target, &[owner]).is_err(),
                "incomplete owner prerequisites accepted: {owner}"
            );
        }
    }
    for target in &TARGETS[2..] {
        for mask in [1, 2, 4, 8, 16, 32, 64] {
            assert!(fixture.core.context(target, &mask_features(mask)).is_err());
        }
    }
    Ok(())
}

#[test]
fn workspace_host_providers_descriptive_identity_and_prior_path_evidence_do_not_select_snapshots()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        for environment in ["release", "dev"] {
            let mut off = fixture.core.context(target, &[])?;
            off.environment = environment.to_owned();
            off.projection_key = format!("review/host-data/{environment}/{target}");
            off.preset = off.projection_key.clone();
            for pin in PINS {
                assert!(fixture.render(pin.path, &off)?.is_none());
            }
            if is_d2(target) {
                let mut on = fixture.core.context(target, &FULL)?;
                on.environment = environment.to_owned();
                on.projection_key = off.projection_key.clone();
                on.preset = on.projection_key.clone();
                for pin in PINS {
                    assert_eq!(
                        fixture.render(pin.path, &on)?.expect("real host data"),
                        fixture.raw[pin.oid]
                    );
                }
                for owner in FINAL_PROVIDER_OWNERS {
                    let enabled = if owner == "theme_preview_rules" {
                        vec!["client_theme", owner]
                    } else if owner == "workspace_notifications" {
                        mask_features(32)
                    } else {
                        vec![owner]
                    };
                    let context = fixture.core.context(target, &enabled)?;
                    for provider in PROVIDERS {
                        assert!(fixture.render(provider.path, &context)?.is_some());
                    }
                }
            }
        }
    }
    Ok(())
}

#[test]
fn workspace_host_providers_keep_real_callbacks_geometry_counts_and_missing_adapter_explicit()
-> Result<()> {
    let fixture = Fixture::load()?;
    let marker_sets: [&[&str]; 8] = [
        &[
            "Pure GUI-coordinate geometry",
            "List.copyOf(entries)",
            "HitRegion",
            "Invalid panel-entry affordance ordinal",
            "candidate.bounds().contains",
        ],
        &[
            "IdentityHashMap<SFMScreenPanel, SFMPanelReopenRecipe>",
            "SFMExplorerPresentationRecipe.capture(recipe, panel)",
            "recipe.reopen()",
            "Panel recipe must return a fresh unattached instance",
            "reopened == source || recipes.containsKey(reopened)",
        ],
        &[
            "List.copyOf(entries)",
            "Pane capture and summary totals disagree",
            "EntryWitness",
        ],
        &[
            "Recoverability counts must partition the pane",
            "total > 1 || dirty > 0 || nonRecoverable > 0",
            "read-only=",
            "recoverable=",
            "non-recoverable=",
        ],
        &[
            "GLFW.GLFW_MOUSE_BUTTON_MIDDLE",
            "boolean moveModifierDown",
            "if (!moveModifierDown) return false",
            "host.openActions(capturedSource)",
            "host.moveToStack(capturedSource, releasedOver)",
            "clear();",
        ],
        &[
            "MAX_PATHS = 8",
            "MAX_PATH_LENGTH = 16_384",
            "32_768",
            "COPY_TO_CLIPBOARD",
            "style.withClickEvent(null).withHoverEvent(null).withInsertion(null)",
            "SFMPath.parse(value)",
            "List.copyOf(runs)",
            "List.copyOf(paths)",
        ],
        &[
            "VIEWPORT_INSET = 2",
            "BOTTOM_INSET = 18",
            "Collections.reverse(reverse)",
            "Toast dimensions must be positive",
            "mouseX < x + width",
            "List.copyOf(reverse)",
        ],
        &[
            "MAX_TOASTS = 8",
            "MAX_TEXT_CODE_POINTS = 512",
            "this(System::nanoTime)",
            "LongSupplier",
            "MutationResult",
            "STALE",
            "DISPOSED",
            "InteractionLease",
            "retired.forEach(InteractionLease::toastRemoved)",
            "nextId == Long.MAX_VALUE",
            "Cannot publish a bounded toast while preserving the only queue entry",
        ],
    ];
    for (pin, markers) in PINS.into_iter().zip(marker_sets) {
        let text = std::str::from_utf8(&fixture.raw[pin.oid])?;
        for marker in markers {
            assert!(
                text.contains(marker),
                "lost genuine local-provider contract: {marker}"
            );
        }
        for forbidden in [
            "Files.read",
            "Files.write",
            "ProcessBuilder",
            "Minecraft.getInstance()",
            "GLFW.glfwGet",
            "SFMClientActions.",
            "class SFMExplorerPresentationRecipe",
        ] {
            assert!(
                !text.contains(forbidden),
                "a substitute or ambient authority was introduced"
            );
        }
    }
    // Recipe creation and toast-removal callbacks remain actual supplied hooks.
    // Their host/UI behavior is not executed, stubbed or proven by this test.
    assert!(
        std::str::from_utf8(&fixture.raw[PINS[1].oid])?
            .contains("Optional.ofNullable(recipes.get(panel))")
    );
    assert!(std::str::from_utf8(&fixture.raw[PINS[7].oid])?.contains("callback.run();"));
    Ok(())
}

#[test]
fn workspace_host_providers_shared_edit_and_byte_mutations_remain_source_only() -> Result<()> {
    let fixture = Fixture::load()?;
    let pin = PINS[3];
    let bytes = &fixture.raw[pin.oid];
    let source = std::str::from_utf8(bytes)?;
    let anchor = "Exact preflight counts for one captured pane close.";
    ensure!(
        source.matches(anchor).count() == 1,
        "shared summary anchor drift"
    );
    let edited = source.replace(anchor, "Reviewed exact counts for one captured pane close.");
    for target in &TARGETS[..2] {
        let context = fixture.core.context(target, &FULL)?;
        let before = render_java_source(source, &context)?;
        let after = render_java_source(&edited, &context)?;
        assert_eq!(
            after,
            before.replace(anchor, "Reviewed exact counts for one captured pane close.")
        );
        assert!(
            render_java_source(
                &format!(
                    "{{% if features.unknown_host_provider_owner %}}\n{source}{{% endif %}}\n"
                ),
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
    assert!(validate_bytes(pin, source.replace('\n', "\r\n").as_bytes()).is_err());
    Ok(())
}
