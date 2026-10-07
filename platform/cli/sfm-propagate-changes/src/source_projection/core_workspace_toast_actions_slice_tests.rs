//! Source-boundary regressions for the reviewed toast timer/clipboard split.
//! V3 adds only the exact captured-path accessor consumer and a five-owner truth table.
//! These use the real selected core inputs and renderer, not Java execution.
#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::InputVariant;
use super::core_inputs::discover_core_source_files;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::core_workspace_host_current_contract::reviewed_pre_typed_palette_host;
use super::core_workspace_palette_current_contract::pre_workspace_palette_source;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

const PREFIX: &str = "src/main/java/ca/teamdman/sfm/";
const HOST: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenMultiplexer.java";
const REGISTRAR: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMCommandPaletteActions.java";
const ACTION: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMToastAction.java";
const PATH_ACTION: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMToastPathAction.java";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const PINS: [(&str, usize, &str); 4] = [
    (
        HOST,
        161594,
        "sha256:258b0a9e5fef738d839c3ae5cb79d864e222b49b238b6921ab441bc91ef7d800",
    ),
    (
        REGISTRAR,
        32247,
        "sha256:16cf4557b043f1221c261024113098eb9f17b82c95a644025c221851730eac6a",
    ),
    (
        ACTION,
        5360,
        "sha256:743bbe69a02501f662e06ce2e77eea66e986bae1197db4b09a66e209bc622914",
    ),
    (
        PATH_ACTION,
        7438,
        "sha256:fed2244ac389d9121b3c2d0a7514f3719acc59a8c867493cf12909bc66b7d07a",
    ),
];
const ACTION_OID: &str = "e6701a5c3d10ee843fdc79922148f67452dc76ff";
const PATH_OID: &str = "e6b7855e2c2f980889fc4e8e320df4617b47145c";
const V1_LEDGER: &str = "docs/tasks/sfm-core-workspace-host-slice.json";
const V1_SHA: &str = "sha256:dd4def0a8b762b49cec2f5e9d3db9307afa8e1548930ac1210e22d35600bf882";
const V2_LEDGER: &str = "docs/tasks/sfm-core-workspace-toast-actions-refinement-v2.json";
const V3_LEDGER: &str = "docs/tasks/sfm-core-workspace-toast-actions-refinement-v3.json";

#[derive(Facet)]
struct HistoricalLedger {
    full_source_profiles: Vec<Full>,
    independent_profiles: Vec<Profile>,
}
#[derive(Facet)]
struct Full {
    target: String,
    enabled: Vec<String>,
}
#[derive(Facet)]
struct Profile {
    id: String,
    enabled: Vec<String>,
}
#[derive(Facet)]
struct Refinement {
    schema: String,
    new_definitions: Vec<String>,
    support_changes: Vec<String>,
    prerequisite_changes: Vec<String>,
    registrar_edits: Vec<Edit>,
}
#[derive(Facet)]
struct AccessorRefinement {
    schema: String,
    registrar_edits: Vec<Edit>,
}
#[derive(Facet)]
struct Edit {
    id: String,
    before: String,
    after: String,
}

struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    source: BTreeMap<String, Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
    historical: HistoricalLedger,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let historical_bytes = read_bounded(&core.repository.join(V1_LEDGER), 512 * 1024)?;
        ensure!(
            sha256(&historical_bytes) == V1_SHA,
            "immutable host-v1 ledger changed"
        );
        let historical: HistoricalLedger =
            facet_json::from_str(std::str::from_utf8(&historical_bytes)?)?;
        ensure!(
            historical.full_source_profiles.len() == 10
                && historical.independent_profiles.len() == 106,
            "historical profile scope drift"
        );
        let receipt_bytes = read_bounded(&core.repository.join(V2_LEDGER), 128 * 1024)?;
        let receipt: Refinement = facet_json::from_str(std::str::from_utf8(&receipt_bytes)?)?;
        ensure!(
            receipt.schema == "sfm:core_workspace_toast_actions_refinement@2"
                && receipt.new_definitions.is_empty()
                && receipt.support_changes.is_empty()
                && receipt.prerequisite_changes.is_empty()
                && receipt.registrar_edits.len() == 5,
            "unapproved feature or registrar scope"
        );
        let mut source = BTreeMap::new();
        for (path, count, digest) in PINS {
            ensure!(path.starts_with(PREFIX), "bounded cohort path");
            let bytes = core.read_source(path)?;
            // Historical pins bind only the exact inverse; actual current bytes
            // remain in source so every rendering test exercises current guards.
            let historical = if path == REGISTRAR {
                pre_workspace_palette_source(&bytes)?
            } else if path == HOST {
                reviewed_pre_typed_palette_host(std::str::from_utf8(&bytes)?)?.into_bytes()
            } else {
                bytes.clone()
            };
            ensure!(
                historical.len() == count
                    && sha256(&historical) == digest
                    && !historical.contains(&b'\r')
                    && historical.ends_with(b"\n"),
                "reviewed toast source identity drift: {path}"
            );
            source.insert(path.to_owned(), bytes);
        }
        let pre_workspace = pre_workspace_palette_source(&source[REGISTRAR])?;
        let mut original = std::str::from_utf8(&pre_workspace)?.to_owned();
        let amendment_bytes = read_bounded(&core.repository.join(V3_LEDGER), 128 * 1024)?;
        let amendment: AccessorRefinement =
            facet_json::from_str(std::str::from_utf8(&amendment_bytes)?)?;
        ensure!(
            amendment.schema == "sfm:core_workspace_toast_actions_refinement@3"
                && amendment.registrar_edits.len() == 5,
            "unreviewed registrar grammar amendment"
        );
        let mut seen = BTreeSet::new();
        for edit in amendment.registrar_edits.iter().rev() {
            ensure!(
                seen.insert(edit.id.as_str()) && original.matches(&edit.after).count() == 1,
                "nested registrar reverse anchor drift"
            );
            original = original.replacen(&edit.after, &edit.before, 1);
        }
        ensure!(
            original.len() == 32162
                && sha256(original.as_bytes())
                    == "sha256:57c2d33c1b40378761e8aeaafbc586caa6986368da55fcccff24223d9862734d",
            "nested registrar must reverse exactly to immutable v2"
        );
        seen.clear();
        for edit in receipt.registrar_edits.iter().rev() {
            ensure!(
                seen.insert(edit.id.as_str()) && original.matches(&edit.after).count() == 1,
                "registrar reverse anchor drift"
            );
            original = original.replacen(&edit.after, &edit.before, 1);
        }
        ensure!(
            original.len() == 31977
                && sha256(original.as_bytes())
                    == "sha256:1862eb535ea1ee1b0a9f44906a6e257b5fe700211261fc42323536e0b47cd0b1",
            "original current registrar template was not preserved"
        );
        validate_rule(&core, ACTION, &TARGETS[..2], &["workspace_toast_actions"])?;
        validate_rule(
            &core,
            PATH_ACTION,
            &TARGETS[..2],
            &["workspace_toast_path_actions", "workspace_notifications"],
        )?;
        let raw = read_git_blobs(
            &core.repository,
            &BTreeSet::from([ACTION_OID.to_owned(), PATH_OID.to_owned()]),
        )?;
        ensure!(
            raw[ACTION_OID].len() == 5036 && raw[PATH_OID].len() == 7438,
            "frozen mixed-action raw witnesses changed"
        );
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PINS.iter().all(|(p, _, _)| inventory.contains(*p)),
            "real source provider absent"
        );
        Ok(Self {
            core,
            inventory,
            source,
            raw,
            historical,
        })
    }
    fn context(&self, target: &str, names: &[&str]) -> Result<ProjectionContext> {
        self.core.context(target, names)
    }
    fn profile(&self, target: &str, name: &str) -> Result<ProjectionContext> {
        let p = self
            .historical
            .independent_profiles
            .iter()
            .find(|p| p.id == name)
            .ok_or_else(|| eyre::eyre!("reviewed profile missing"))?;
        self.context(
            target,
            &p.enabled.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selected.inputs.get(path) else {
            ensure!(
                selected.omitted_paths.contains(path),
                "unexplained source omission"
            );
            return Ok(None);
        };
        ensure!(input.input == path, "unreviewed alternate source");
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.source[path])?,
            context,
        )?))
    }
}
fn validate_rule(core: &CoreTestFixture, path: &str, targets: &[&str], all: &[&str]) -> Result<()> {
    let rows: &[InputVariant] = core
        .metadata
        .source_rules
        .get(path)
        .ok_or_else(|| eyre::eyre!("new action rule not registered"))?;
    ensure!(
        rows.len() == 1
            && rows[0].input == path
            && rows[0].template
            && rows[0].when.targets == targets.iter().map(|s| (*s).to_owned()).collect::<Vec<_>>()
            && rows[0].when.all_features == all.iter().map(|s| (*s).to_owned()).collect::<Vec<_>>()
            && rows[0].when.any_features.is_empty()
            && rows[0].when.none_features.is_empty(),
        "action membership drift"
    );
    Ok(())
}
fn contains_all(text: &str, names: &[&str]) {
    for name in names {
        assert!(text.contains(name), "missing source API: {name}");
    }
}
fn contains_none(text: &str, names: &[&str]) {
    for name in names {
        assert!(!text.contains(name), "unapproved source API: {name}");
    }
}

#[test]
fn actual_timer_only_host_and_action_do_not_import_render_content_or_clipboard() -> Result<()> {
    let f = Fixture::load()?;
    for target in &TARGETS[..2] {
        let c = f.profile(target, "only_workspace_toast_actions")?;
        assert!(!c.features["workspace_notifications"] && !c.features["command_palette"]);
        let host = f.render(HOST, &c)?.expect("actual host");
        contains_all(
            &host,
            &[
                "SFMWorkspaceToastQueue workspaceToasts",
                "toastQueue().tick();",
                "toastQueue().close();",
                "activeWorkspaceToastIds(",
                "stopWorkspaceToastTimer(",
                "resumeWorkspaceToastTimer(",
                "dismissWorkspaceToast(",
            ],
        );
        contains_none(
            &host,
            &[
                "SFMWorkspaceToastContent",
                "SFMWorkspaceToastLayout",
                "renderWorkspaceToasts(",
                "showWorkspaceToast(",
                "toastContents(",
                "copyWorkspaceToast(",
                "copyWorkspaceToastDetails(",
                "writeVerifiedClipboard(",
                "keyboardHandler::setClipboard",
                "workspaceToastAt(",
                "SFMCommandPaletteScreen",
            ],
        );
        let action = f.render(ACTION, &c)?.expect("actual pure timer action");
        contains_all(
            &action,
            &[
                "STOP_TIMER",
                "RESUME_TIMER",
                "DISMISS",
                "workspace.activeWorkspaceToastIds()",
                "workspace.stopWorkspaceToastTimer(id)",
                "workspace.resumeWorkspaceToastTimer(id)",
                "workspace.dismissWorkspaceToast(id)",
            ],
        );
        contains_none(
            &action,
            &["COPY", "Clipboard", "clipboard", "copyWorkspaceToast"],
        );
        let registry = f.render(REGISTRAR, &c)?.expect("actual registrar");
        contains_all(
            &registry,
            &[
                "\"toast/timer/stop\"",
                "\"toast/timer/resume\"",
                "\"toast/dismiss\"",
            ],
        );
        contains_none(
            &registry,
            &[
                "\"toast/copy\"",
                "\"toast/details/copy\"",
                "\"toast/path/copy\"",
                "\"toast/path/text/open\"",
                "\"toast/path/explorer/open\"",
            ],
        );
        assert!(f.render(PATH_ACTION, &c)?.is_none());
    }
    Ok(())
}

#[test]
fn captured_path_and_copy_members_exist_only_with_notifications_not_by_action_proximity()
-> Result<()> {
    let f = Fixture::load()?;
    for target in &TARGETS[..2] {
        let without = f.profile(target, "only_workspace_toast_path_actions")?;
        assert!(!without.features["workspace_notifications"]);
        assert!(f.render(PATH_ACTION, &without)?.is_none());
        let registry = f.render(REGISTRAR, &without)?.expect("actual registrar");
        contains_none(
            &registry,
            &[
                "SFMToastPathAction",
                "\"toast/copy\"",
                "\"toast/details/copy\"",
            ],
        );
        let with = f.profile(target, "mixed_N_PATH")?;
        assert_eq!(
            f.render(ACTION, &with)?.expect("mixed action").as_bytes(),
            f.raw[ACTION_OID]
        );
        assert_eq!(
            f.render(PATH_ACTION, &with)?
                .expect("real path action")
                .as_bytes(),
            f.raw[PATH_OID]
        );
        let registry = f.render(REGISTRAR, &with)?.expect("actual registrar");
        contains_all(
            &registry,
            &[
                "\"toast/copy\"",
                "\"toast/details/copy\"",
                "\"toast/path/copy\"",
                "\"toast/path/text/open\"",
                "\"toast/path/explorer/open\"",
            ],
        );
        let copy_only = f.profile(target, "mixed_N_TA")?;
        assert!(f.render(PATH_ACTION, &copy_only)?.is_none());
        assert_eq!(
            f.render(ACTION, &copy_only)?
                .expect("notification copy action")
                .as_bytes(),
            f.raw[ACTION_OID]
        );
        let copy_registry = f
            .render(REGISTRAR, &copy_only)?
            .expect("notification copy registrar");
        contains_all(
            &copy_registry,
            &["\"toast/copy\"", "\"toast/details/copy\""],
        );
        contains_none(&copy_registry, &["SFMToastPathAction", "\"toast/path/"]);
        let notification = f.profile(target, "only_workspace_notifications")?;
        assert!(f.render(ACTION, &notification)?.is_none());
        assert!(f.render(PATH_ACTION, &notification)?.is_none());
        let host = f
            .render(HOST, &notification)?
            .expect("real notification host");
        contains_all(
            &host,
            &["renderWorkspaceToasts(", "copyWorkspaceToast(id);"],
        );
    }
    Ok(())
}

#[test]
fn twenty_historical_mixed_action_cells_reconstruct_exact_raw_or_omit() -> Result<()> {
    let f = Fixture::load()?;
    let mut cells = 0;
    for environment in ["release", "dev"] {
        for target in TARGETS {
            let features: Vec<&str> = if environment == "dev" {
                f.historical
                    .full_source_profiles
                    .iter()
                    .find(|p| p.target == target)
                    .ok_or_else(|| eyre::eyre!("ten full profiles missing"))?
                    .enabled
                    .iter()
                    .map(String::as_str)
                    .collect()
            } else {
                Vec::new()
            };
            let mut c = f.context(target, &features)?;
            c.environment = environment.to_owned();
            c.projection_key = format!("review/toast-boundary/{environment}/{target}");
            c.preset = c.projection_key.clone();
            for (path, oid) in [(ACTION, ACTION_OID), (PATH_ACTION, PATH_OID)] {
                let expected = environment == "dev" && matches!(target, "1.19.2" | "1.19.4");
                let output = f.render(path, &c)?;
                assert_eq!(
                    output.as_deref().map(str::as_bytes),
                    expected.then(|| f.raw[oid].as_slice())
                );
                cells += 1;
            }
        }
    }
    assert_eq!(cells, 40);
    for target in &TARGETS[2..] {
        assert!(
            f.context(
                target,
                &[
                    "client_actions",
                    "workspace_panels",
                    "workspace_toast_actions"
                ]
            )
            .is_err()
        );
    }
    assert!(f.context("1.19.2", &["workspace_toast_actions"]).is_err());
    assert!(
        f.context("1.19.2", &["workspace_panels", "unreviewed_toast_owner"])
            .is_err()
    );
    Ok(())
}

#[test]
fn real_queue_and_captured_path_providers_keep_pure_lifetime_and_authority_boundaries() -> Result<()>
{
    let f = Fixture::load()?;
    let queue = f.core.read_source(
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/toast/SFMWorkspaceToastQueue.java",
    )?;
    let queue = std::str::from_utf8(&queue)?;
    contains_all(
        queue,
        &[
            "LongSupplier",
            "retired.forEach(InteractionLease::toastRemoved)",
            "MutationResult",
            "publishPreserving",
            "MAX_TOASTS = 8",
        ],
    );
    contains_none(
        queue,
        &[
            "Minecraft.getInstance()",
            "ProcessBuilder",
            "Files.write",
            "keyboardHandler",
        ],
    );
    let original = std::str::from_utf8(&f.raw[PATH_OID])?;
    contains_all(
        original,
        &[
            "workspace.workspaceToastPaths(id)",
            "runtime.authorizedFilesystemRootFor(path)",
            "SFMPathHierarchy.deepestContainingRoot",
            "runtime.discardExplorer(panel.explorerId())",
        ],
    );
    // Marker/raw identity is not execution of timers, callbacks, clipboard or resolver authorization.
    Ok(())
}

#[test]
fn actual_accessor_guard_obeys_all_thirty_two_five_owner_sets_on_both_d2_targets() -> Result<()> {
    let f = Fixture::load()?;
    let owners = [
        ("workspace_focus_tracking", &["workspace_panels"][..]),
        (
            "workspace_panel_actions",
            &["client_actions", "workspace_panels"][..],
        ),
        ("workspace_panel_reopening", &["workspace_panels"][..]),
        (
            "workspace_toast_path_actions",
            &["workspace_toast_actions", "file_explorer"][..],
        ),
        (
            "workspace_notifications",
            &["workspace_panels", "font_formatted_text"][..],
        ),
    ];
    for (owner, requires) in owners {
        let actual = f
            .core
            .features
            .0
            .get(owner)
            .ok_or_else(|| eyre::eyre!("reviewed accessor owner is not registered: {owner}"))?;
        ensure!(
            actual.supported_targets == ["1.19.2", "1.19.4"]
                && actual.requires
                    == requires
                        .iter()
                        .map(|value| (*value).to_owned())
                        .collect::<Vec<_>>(),
            "accessor refinement cannot broaden owner support/prerequisites: {owner}"
        );
    }
    let mut cells = 0;
    let mut included = 0;
    for target in &TARGETS[..2] {
        for mask in 0_u8..32 {
            let mut names = BTreeSet::from(["workspace_panels".to_owned()]);
            for (index, (owner, _)) in owners.iter().enumerate() {
                if mask & (1 << index) != 0 {
                    names.insert((*owner).to_owned());
                }
            }
            // Explicit test-only prerequisite closure, validated through the real catalog.
            // No production profile, metadata or prerequisite is changed.
            loop {
                let mut changed = false;
                for name in names.clone() {
                    let definition = f.core.features.0.get(&name).ok_or_else(|| {
                        eyre::eyre!("accessor prerequisite is not registered: {name}")
                    })?;
                    for dependency in &definition.requires {
                        changed |= names.insert(dependency.clone());
                    }
                }
                ensure!(
                    names.len() <= 16,
                    "bounded accessor test prerequisite closure exceeded"
                );
                if !changed {
                    break;
                }
            }
            let context = f.context(
                target,
                &names.iter().map(String::as_str).collect::<Vec<_>>(),
            )?;
            for (index, (owner, _)) in owners.iter().enumerate() {
                assert_eq!(
                    context.features[*owner],
                    mask & (1 << index) != 0,
                    "an accessor owner was implicitly enabled by the test closure"
                );
            }
            let expected = mask & 0b00111 != 0 || (mask & 0b01000 != 0 && mask & 0b10000 != 0);
            let host = f.render(HOST, &context)?.expect("actual selected host");
            assert_eq!(
                host.contains("public @Nullable SFMScreenPanel panelInstance("),
                expected,
                "real Liquid guard must mean F OR A OR R OR (PATH AND N): mask={mask}, target={target}"
            );
            if expected {
                contains_all(&host, &["return layout.panel(panelId);"]);
                included += 1;
            }
            let path = f.render(PATH_ACTION, &context)?;
            let path_enabled = mask & 0b01000 != 0 && mask & 0b10000 != 0;
            assert_eq!(path.is_some(), path_enabled);
            if let Some(path) = path {
                contains_all(&path, &["workspace.panelInstance(panelId) == panel"]);
                assert!(host.contains("public @Nullable SFMScreenPanel panelInstance("));
            }
            let registrar = f.render(REGISTRAR, &context)?;
            if context.features["client_actions"] {
                let registrar = registrar.expect("actual action registrar");
                for field in [
                    "COPY_TOAST_PATH",
                    "OPEN_TOAST_PATH_TEXT",
                    "OPEN_TOAST_PATH_EXPLORER",
                ] {
                    assert_eq!(
                        registrar.contains(field),
                        path_enabled,
                        "path registration must match path-class membership"
                    );
                }
                let copies_enabled = context.features["workspace_toast_actions"]
                    && context.features["workspace_notifications"];
                for field in ["COPY_TOAST =", "COPY_TOAST_DETAILS ="] {
                    assert_eq!(
                        registrar.contains(field),
                        copies_enabled,
                        "copy registration must remain notification plus action owned"
                    );
                }
            } else {
                assert!(registrar.is_none());
            }
            assert!(!host.contains("{%"));
            cells += 1;
        }
    }
    assert_eq!((cells, included), (64, 58));
    Ok(())
}
