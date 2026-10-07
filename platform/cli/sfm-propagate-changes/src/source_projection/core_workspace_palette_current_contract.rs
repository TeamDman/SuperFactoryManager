//! Strict test-only current Workspace palette to pre-Workspace source inverse.
//!
//! The current source is separately identity-bound. The 25 Workspace guard
//! refinements and one legacy-review refinement are reversed; immutable witnesses and existing inverses
//! remain untouched. Rendering callers must retain the actual current bytes.
#![cfg(test)]

use super::context::ProjectionContext;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeMap;

const CURRENT_BYTES: usize = 34041;
const CURRENT_SHA: &str = "sha256:ce737218cef89ffc7450ffb898c0f939f3ad0cec50ecfecbdc87560efc9cc305";
const PRE_REVIEW_BYTES: usize = 33943;
const PRE_REVIEW_SHA: &str =
    "sha256:3e1c471cc02367cae3b70e8f45cf2ea6ca09693a9b268e3f72a1db0c762e74dc";
const REVIEW_GUARD_CURRENT: &str = "{% if features.repository_review_bundle_actions and features.legacy_repository_review and features.client_theme and features.legacy_file_explorer %}\n";
const REVIEW_GUARD_PREVIOUS: &str = "{% if features.repository_review_bundle_actions %}\n";
const PRE_WORKSPACE_BYTES: usize = 32247;
const PRE_WORKSPACE_SHA: &str =
    "sha256:16cf4557b043f1221c261024113098eb9f17b82c95a644025c221851730eac6a";

// Exact current nested block, exact immutable pre-Workspace block. The order is
// the original admission's 32..56 order, not source registration order.
const GUARD_INVERSES: [(&str, &str); 25] = [
    // DUPLICATE_PANEL_LEFT; original admission operation 32.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_panel_reopening %}
    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_LEFT = REGISTERER.register(
            "panel/duplicate/left",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.LEFT)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_LEFT = REGISTERER.register(
            "panel/duplicate/left",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.LEFT)
    );

{% endif %}
"#,
    ),
    // DUPLICATE_PANEL_RIGHT; original admission operation 33.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_panel_reopening %}
    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_RIGHT = REGISTERER.register(
            "panel/duplicate/right",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.RIGHT)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_RIGHT = REGISTERER.register(
            "panel/duplicate/right",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.RIGHT)
    );

{% endif %}
"#,
    ),
    // DUPLICATE_PANEL_ABOVE; original admission operation 34.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_panel_reopening %}
    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_ABOVE = REGISTERER.register(
            "panel/duplicate/above",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.ABOVE)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_ABOVE = REGISTERER.register(
            "panel/duplicate/above",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.ABOVE)
    );

{% endif %}
"#,
    ),
    // DUPLICATE_PANEL_BELOW; original admission operation 35.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_panel_reopening %}
    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_BELOW = REGISTERER.register(
            "panel/duplicate/below",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.BELOW)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_BELOW = REGISTERER.register(
            "panel/duplicate/below",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.BELOW)
    );

{% endif %}
"#,
    ),
    // MOVE_PANEL_LEFT; original admission operation 36.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_LEFT = REGISTERER.register(
            "panel/move/left",
            () -> new MovePanelAction(SFMWorkspaceSide.LEFT)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_LEFT = REGISTERER.register(
            "panel/move/left",
            () -> new MovePanelAction(SFMWorkspaceSide.LEFT)
    );

{% endif %}
"#,
    ),
    // MOVE_PANEL_RIGHT; original admission operation 37.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_RIGHT = REGISTERER.register(
            "panel/move/right",
            () -> new MovePanelAction(SFMWorkspaceSide.RIGHT)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_RIGHT = REGISTERER.register(
            "panel/move/right",
            () -> new MovePanelAction(SFMWorkspaceSide.RIGHT)
    );

{% endif %}
"#,
    ),
    // MOVE_PANEL_ABOVE; original admission operation 38.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_ABOVE = REGISTERER.register(
            "panel/move/above",
            () -> new MovePanelAction(SFMWorkspaceSide.ABOVE)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_ABOVE = REGISTERER.register(
            "panel/move/above",
            () -> new MovePanelAction(SFMWorkspaceSide.ABOVE)
    );

{% endif %}
"#,
    ),
    // MOVE_PANEL_BELOW; original admission operation 39.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_BELOW = REGISTERER.register(
            "panel/move/below",
            () -> new MovePanelAction(SFMWorkspaceSide.BELOW)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_BELOW = REGISTERER.register(
            "panel/move/below",
            () -> new MovePanelAction(SFMWorkspaceSide.BELOW)
    );

{% endif %}
"#,
    ),
    // RESIZE_PANEL_LEFT; original admission operation 40.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_directional_resize %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_LEFT = REGISTERER.register(
            "panel/resize/left",
            () -> new ResizePanelAction(SFMWorkspaceSide.LEFT)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_LEFT = REGISTERER.register(
            "panel/resize/left",
            () -> new ResizePanelAction(SFMWorkspaceSide.LEFT)
    );

{% endif %}
"#,
    ),
    // RESIZE_PANEL_RIGHT; original admission operation 41.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_directional_resize %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_RIGHT = REGISTERER.register(
            "panel/resize/right",
            () -> new ResizePanelAction(SFMWorkspaceSide.RIGHT)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_RIGHT = REGISTERER.register(
            "panel/resize/right",
            () -> new ResizePanelAction(SFMWorkspaceSide.RIGHT)
    );

{% endif %}
"#,
    ),
    // RESIZE_PANEL_ABOVE; original admission operation 42.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_directional_resize %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_ABOVE = REGISTERER.register(
            "panel/resize/above",
            () -> new ResizePanelAction(SFMWorkspaceSide.ABOVE)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_ABOVE = REGISTERER.register(
            "panel/resize/above",
            () -> new ResizePanelAction(SFMWorkspaceSide.ABOVE)
    );

{% endif %}
"#,
    ),
    // RESIZE_PANEL_BELOW; original admission operation 43.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_directional_resize %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_BELOW = REGISTERER.register(
            "panel/resize/below",
            () -> new ResizePanelAction(SFMWorkspaceSide.BELOW)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_BELOW = REGISTERER.register(
            "panel/resize/below",
            () -> new ResizePanelAction(SFMWorkspaceSide.BELOW)
    );

{% endif %}
"#,
    ),
    // RESIZE_DIVIDERS; original admission operation 44.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_dividers %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizeDividersAction> RESIZE_DIVIDERS = REGISTERER.register(
            "panel/resize/dividers",
            ResizeDividersAction::new
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizeDividersAction> RESIZE_DIVIDERS = REGISTERER.register(
            "panel/resize/dividers",
            ResizeDividersAction::new
    );

{% endif %}
"#,
    ),
    // SET_PANEL_SCALE; original admission operation 45.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_panel_metadata %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> SET_PANEL_SCALE = REGISTERER.register(
            "panel/scale/set",
            () -> new PanelScaleAction(PanelScaleAction.Operation.SET)
    );

{% endif %}
{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> SET_PANEL_SCALE = REGISTERER.register(
            "panel/scale/set",
            () -> new PanelScaleAction(PanelScaleAction.Operation.SET)
    );

{% endif %}
"#,
    ),
    // INCREASE_PANEL_SCALE; original admission operation 46.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_panel_metadata %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> INCREASE_PANEL_SCALE = REGISTERER.register(
            "panel/scale/increase",
            () -> new PanelScaleAction(PanelScaleAction.Operation.INCREASE)
    );

{% endif %}
{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> INCREASE_PANEL_SCALE = REGISTERER.register(
            "panel/scale/increase",
            () -> new PanelScaleAction(PanelScaleAction.Operation.INCREASE)
    );

{% endif %}
"#,
    ),
    // DECREASE_PANEL_SCALE; original admission operation 47.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_panel_metadata %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> DECREASE_PANEL_SCALE = REGISTERER.register(
            "panel/scale/decrease",
            () -> new PanelScaleAction(PanelScaleAction.Operation.DECREASE)
    );

{% endif %}
{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> DECREASE_PANEL_SCALE = REGISTERER.register(
            "panel/scale/decrease",
            () -> new PanelScaleAction(PanelScaleAction.Operation.DECREASE)
    );

{% endif %}
"#,
    ),
    // CLEAR_PANEL_SCALE; original admission operation 48.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_panel_metadata %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> CLEAR_PANEL_SCALE = REGISTERER.register(
            "panel/scale/clear",
            () -> new PanelScaleAction(PanelScaleAction.Operation.CLEAR)
    );

{% endif %}
{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> CLEAR_PANEL_SCALE = REGISTERER.register(
            "panel/scale/clear",
            () -> new PanelScaleAction(PanelScaleAction.Operation.CLEAR)
    );

{% endif %}
"#,
    ),
    // ROTATE_CONTENT_LEFT; original admission operation 49.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_CONTENT_LEFT = REGISTERER.register(
            "panel/rotate/content/left",
            () -> new RotatePanelAction(RotatePanelAction.Kind.CONTENT, RotatePanelAction.Direction.LEFT)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_CONTENT_LEFT = REGISTERER.register(
            "panel/rotate/content/left",
            () -> new RotatePanelAction(RotatePanelAction.Kind.CONTENT, RotatePanelAction.Direction.LEFT)
    );

{% endif %}
"#,
    ),
    // ROTATE_CONTENT_RIGHT; original admission operation 50.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_CONTENT_RIGHT = REGISTERER.register(
            "panel/rotate/content/right",
            () -> new RotatePanelAction(RotatePanelAction.Kind.CONTENT, RotatePanelAction.Direction.RIGHT)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_CONTENT_RIGHT = REGISTERER.register(
            "panel/rotate/content/right",
            () -> new RotatePanelAction(RotatePanelAction.Kind.CONTENT, RotatePanelAction.Direction.RIGHT)
    );

{% endif %}
"#,
    ),
    // ROTATE_SCALE_LEFT; original admission operation 51.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_SCALE_LEFT = REGISTERER.register(
            "panel/rotate/scale/left",
            () -> new RotatePanelAction(RotatePanelAction.Kind.SCALE, RotatePanelAction.Direction.LEFT)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_SCALE_LEFT = REGISTERER.register(
            "panel/rotate/scale/left",
            () -> new RotatePanelAction(RotatePanelAction.Kind.SCALE, RotatePanelAction.Direction.LEFT)
    );

{% endif %}
"#,
    ),
    // ROTATE_SCALE_RIGHT; original admission operation 52.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_SCALE_RIGHT = REGISTERER.register(
            "panel/rotate/scale/right",
            () -> new RotatePanelAction(RotatePanelAction.Kind.SCALE, RotatePanelAction.Direction.RIGHT)
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_SCALE_RIGHT = REGISTERER.register(
            "panel/rotate/scale/right",
            () -> new RotatePanelAction(RotatePanelAction.Kind.SCALE, RotatePanelAction.Direction.RIGHT)
    );

{% endif %}
"#,
    ),
    // OPEN_PANEL_DIAGNOSTICS; original admission operation 53.
    (
        r#"{% if features.workspace_panel_actions %}
{% if features.typed_command_palette %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelDiagnosticsAction> OPEN_PANEL_DIAGNOSTICS = REGISTERER.register(
            "panel/diagnostics/open",
            OpenPanelDiagnosticsAction::new
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelDiagnosticsAction> OPEN_PANEL_DIAGNOSTICS = REGISTERER.register(
            "panel/diagnostics/open",
            OpenPanelDiagnosticsAction::new
    );

{% endif %}
"#,
    ),
    // CLOSE_SCREEN; original admission operation 54.
    (
        r#"{% if features.client_screen_actions %}
{% if features.workspace_panels %}
    public static final SFMRegistryObject<SFMClientAction<?>, CloseScreenAction> CLOSE_SCREEN = REGISTERER.register(
            "screen/close",
            CloseScreenAction::new
    );

{% endif %}
{% endif %}
"#,
        r#"{% if features.client_screen_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, CloseScreenAction> CLOSE_SCREEN = REGISTERER.register(
            "screen/close",
            CloseScreenAction::new
    );

{% endif %}
"#,
    ),
    // SCREEN_DIAGNOSTICS; original admission operation 55.
    (
        r#"{% if features.screen_diagnostics %}
{% if features.workspace_panel_actions %}
{% if features.editor_overlay_push %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMScreenDiagnosticsAction> SCREEN_DIAGNOSTICS = REGISTERER.register(
            "screen/diagnostics",
            SFMScreenDiagnosticsAction::new
    );

{% endif %}
{% endif %}
{% endif %}
"#,
        r#"{% if features.screen_diagnostics %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMScreenDiagnosticsAction> SCREEN_DIAGNOSTICS = REGISTERER.register(
            "screen/diagnostics",
            SFMScreenDiagnosticsAction::new
    );

{% endif %}
"#,
    ),
    // OVERLAY_DIAGNOSTICS; original admission operation 56.
    (
        r#"{% if features.screen_diagnostics %}
{% if features.workspace_panel_actions %}
{% if features.editor_overlay_push %}
    /** Compatibility alias: overlays are included in the same screen snapshot. */
    public static final SFMRegistryObject<SFMClientAction<?>, SFMScreenDiagnosticsAction> OVERLAY_DIAGNOSTICS = REGISTERER.register(
            "overlay/diagnostics",
            SFMScreenDiagnosticsAction::new
    );

{% endif %}
{% endif %}
{% endif %}
"#,
        r#"{% if features.screen_diagnostics %}
    /** Compatibility alias: overlays are included in the same screen snapshot. */
    public static final SFMRegistryObject<SFMClientAction<?>, SFMScreenDiagnosticsAction> OVERLAY_DIAGNOSTICS = REGISTERER.register(
            "overlay/diagnostics",
            SFMScreenDiagnosticsAction::new
    );

{% endif %}
"#,
    ),
];

fn exact_source(bytes: &[u8], count: usize, digest: &str) -> Result<()> {
    ensure!(
        bytes.len() == count
            && sha256(bytes) == digest
            && bytes.ends_with(b"\n")
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "current Workspace palette source identity changed"
    );
    Ok(())
}

/// Restore only the reviewed current guards, without accepting arbitrary bytes.
/// This result is for historical validation; it is not a current render source.
pub(super) fn pre_workspace_palette_source(bytes: &[u8]) -> Result<Vec<u8>> {
    exact_source(bytes, CURRENT_BYTES, CURRENT_SHA)?;
    let mut source = std::str::from_utf8(bytes)?.to_owned();
    ensure!(
        source.matches(REVIEW_GUARD_CURRENT).count() == 1,
        "current legacy-review guard inverse anchor is not unique"
    );
    source = source.replacen(REVIEW_GUARD_CURRENT, REVIEW_GUARD_PREVIOUS, 1);
    // This independently hash-bound snapshot was recovered from the retained
    // pre-migration test executable. No historical ledger or source pin changes.
    exact_source(source.as_bytes(), PRE_REVIEW_BYTES, PRE_REVIEW_SHA)?;
    for (current, previous) in GUARD_INVERSES.iter().rev() {
        ensure!(
            source.matches(current).count() == 1,
            "current Workspace palette inverse anchor is not unique"
        );
        source = source.replacen(current, previous, 1);
    }
    exact_source(source.as_bytes(), PRE_WORKSPACE_BYTES, PRE_WORKSPACE_SHA)?;
    Ok(source.into_bytes())
}

// Refines only the 25 current fields after each caller's historical owner filter.
// Unknown fields retain that caller's existing historical/notification predicate.
const CURRENT_MEMBER_GUARDS: [(&str, &[&str]); 26] = [
    (
        "OPEN_REVIEW_BUNDLE",
        &[
            "repository_review_bundle_actions",
            "legacy_repository_review",
            "client_theme",
            "legacy_file_explorer",
        ],
    ),
    (
        "DUPLICATE_PANEL_LEFT",
        &["workspace_panel_actions", "workspace_panel_reopening"],
    ),
    (
        "DUPLICATE_PANEL_RIGHT",
        &["workspace_panel_actions", "workspace_panel_reopening"],
    ),
    (
        "DUPLICATE_PANEL_ABOVE",
        &["workspace_panel_actions", "workspace_panel_reopening"],
    ),
    (
        "DUPLICATE_PANEL_BELOW",
        &["workspace_panel_actions", "workspace_panel_reopening"],
    ),
    (
        "MOVE_PANEL_LEFT",
        &["workspace_panel_actions", "workspace_stack_controls"],
    ),
    (
        "MOVE_PANEL_RIGHT",
        &["workspace_panel_actions", "workspace_stack_controls"],
    ),
    (
        "MOVE_PANEL_ABOVE",
        &["workspace_panel_actions", "workspace_stack_controls"],
    ),
    (
        "MOVE_PANEL_BELOW",
        &["workspace_panel_actions", "workspace_stack_controls"],
    ),
    (
        "RESIZE_PANEL_LEFT",
        &["workspace_panel_actions", "workspace_directional_resize"],
    ),
    (
        "RESIZE_PANEL_RIGHT",
        &["workspace_panel_actions", "workspace_directional_resize"],
    ),
    (
        "RESIZE_PANEL_ABOVE",
        &["workspace_panel_actions", "workspace_directional_resize"],
    ),
    (
        "RESIZE_PANEL_BELOW",
        &["workspace_panel_actions", "workspace_directional_resize"],
    ),
    (
        "RESIZE_DIVIDERS",
        &["workspace_panel_actions", "workspace_dividers"],
    ),
    (
        "SET_PANEL_SCALE",
        &[
            "workspace_panel_actions",
            "workspace_panel_metadata",
            "workspace_stack_controls",
        ],
    ),
    (
        "INCREASE_PANEL_SCALE",
        &[
            "workspace_panel_actions",
            "workspace_panel_metadata",
            "workspace_stack_controls",
        ],
    ),
    (
        "DECREASE_PANEL_SCALE",
        &[
            "workspace_panel_actions",
            "workspace_panel_metadata",
            "workspace_stack_controls",
        ],
    ),
    (
        "CLEAR_PANEL_SCALE",
        &[
            "workspace_panel_actions",
            "workspace_panel_metadata",
            "workspace_stack_controls",
        ],
    ),
    (
        "ROTATE_CONTENT_LEFT",
        &["workspace_panel_actions", "workspace_stack_controls"],
    ),
    (
        "ROTATE_CONTENT_RIGHT",
        &["workspace_panel_actions", "workspace_stack_controls"],
    ),
    (
        "ROTATE_SCALE_LEFT",
        &["workspace_panel_actions", "workspace_stack_controls"],
    ),
    (
        "ROTATE_SCALE_RIGHT",
        &["workspace_panel_actions", "workspace_stack_controls"],
    ),
    (
        "OPEN_PANEL_DIAGNOSTICS",
        &["workspace_panel_actions", "typed_command_palette"],
    ),
    (
        "CLOSE_SCREEN",
        &["client_screen_actions", "workspace_panels"],
    ),
    (
        "SCREEN_DIAGNOSTICS",
        &[
            "screen_diagnostics",
            "workspace_panel_actions",
            "editor_overlay_push",
        ],
    ),
    (
        "OVERLAY_DIAGNOSTICS",
        &[
            "screen_diagnostics",
            "workspace_panel_actions",
            "editor_overlay_push",
        ],
    ),
];

pub(super) fn current_workspace_palette_member_enabled(
    field: &str,
    context: &ProjectionContext,
) -> bool {
    CURRENT_MEMBER_GUARDS
        .iter()
        .find(|(name, _)| *name == field)
        .is_none_or(|(_, guards)| {
            guards
                .iter()
                .all(|name| context.features.get(*name).copied().unwrap_or(false))
        })
}

#[test]
fn exact_current_palette_inverse_and_unauthorized_mutations_fail_closed() -> Result<()> {
    let current = include_bytes!(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../minecraft/core-liquid-template/src/main/java/ca/teamdman/sfm/client/action/SFMCommandPaletteActions.java"
    ));
    let previous = pre_workspace_palette_source(current)?;
    exact_source(&previous, PRE_WORKSPACE_BYTES, PRE_WORKSPACE_SHA)?;
    assert_eq!(GUARD_INVERSES.len(), 25);
    assert_eq!(CURRENT_MEMBER_GUARDS.len(), 26);
    // The previous source is not interchangeable with current bytes.
    let pre_review =
        std::str::from_utf8(current)?.replacen(REVIEW_GUARD_CURRENT, REVIEW_GUARD_PREVIOUS, 1);
    exact_source(pre_review.as_bytes(), PRE_REVIEW_BYTES, PRE_REVIEW_SHA)?;
    assert!(pre_workspace_palette_source(pre_review.as_bytes()).is_err());
    assert!(pre_workspace_palette_source(&previous).is_err());
    assert!(pre_workspace_palette_source(&current[..current.len() - 1]).is_err());
    let mut outside_guard = current.to_vec();
    outside_guard[0] ^= 1;
    assert!(pre_workspace_palette_source(&outside_guard).is_err());
    let mut duplicate_guard = current.to_vec();
    duplicate_guard.extend_from_slice(GUARD_INVERSES[0].0.as_bytes());
    assert!(pre_workspace_palette_source(&duplicate_guard).is_err());
    let crlf = std::str::from_utf8(current)?.replace('\n', "\r\n");
    assert!(pre_workspace_palette_source(crlf.as_bytes()).is_err());
    let mut bom = vec![0xef, 0xbb, 0xbf];
    bom.extend_from_slice(current);
    assert!(pre_workspace_palette_source(&bom).is_err());
    Ok(())
}

#[test]
fn legacy_review_registration_requires_each_real_provider_without_widening_prerequisites()
-> Result<()> {
    let core = super::core_slice_test_support::CoreTestFixture::load()?;
    let registrar = core
        .read_source("src/main/java/ca/teamdman/sfm/client/action/SFMCommandPaletteActions.java")?;
    let action = "src/main/java/ca/teamdman/sfm/client/action/OpenReviewBundleAction.java";
    let flags = [
        "repository_review_bundle_actions",
        "legacy_repository_review",
        "client_theme",
        "legacy_file_explorer",
    ];
    for target in ["1.19.2", "1.19.4"] {
        assert!(
            core.context(target, &["client_actions", "workspace_panels", flags[0]])
                .is_err()
        );
    }
    for target in [
        "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2",
    ] {
        for mask in 0_u8..16 {
            let mut enabled = vec!["client_actions", "workspace_panels"];
            for (bit, flag) in flags.iter().enumerate() {
                if mask & (1 << bit) != 0 {
                    enabled.push(*flag);
                }
            }
            let context = core.context(target, &enabled)?;
            assert!(!context.features["command_palette"]);
            let selection = super::core_inputs::select_core_inputs(
                &core.metadata,
                &context,
                &std::collections::BTreeSet::from([action.to_owned()]),
            )?;
            assert_eq!(selection.inputs.contains_key(action), mask == 15);
            let rendered = render_java_source(std::str::from_utf8(&registrar)?, &context)?;
            assert_eq!(
                rendered.contains("OpenReviewBundleAction> OPEN_REVIEW_BUNDLE"),
                mask == 15
            );
            assert_eq!(
                current_workspace_palette_member_enabled("OPEN_REVIEW_BUNDLE", &context),
                mask == 15
            );
        }
    }
    Ok(())
}

fn two_flag_context(first: &str, second: &str, mask: u8) -> ProjectionContext {
    ProjectionContext {
        minecraft_version: "1.19.2".to_owned(),
        preset: "test_workspace_fallback".to_owned(),
        environment: "dev".to_owned(),
        projection_key: "test/mc-1.19.2".to_owned(),
        features: BTreeMap::from([
            (first.to_owned(), mask & 1 != 0),
            (second.to_owned(), mask & 2 != 0),
        ]),
        targets: BTreeMap::new(),
    }
}

#[test]
fn palette_session_fallback_preserves_all_four_exact_byte_outputs() -> Result<()> {
    let source = r#"{% if features.command_palette %}
{% if features.document_history %}
        java.util.Optional<String> activePaletteSession = activeScreen instanceof SFMCommandPaletteScreen palette
                && palette.documentHistoryAvailable()
                ? java.util.Optional.of(palette.documentHistorySessionId())
                : java.util.Optional.empty();
{% else %}
        java.util.Optional<String> activePaletteSession = java.util.Optional.empty();
{% endif %}
{% else %}
        java.util.Optional<String> activePaletteSession = java.util.Optional.empty();
{% endif %}
"#;
    let canonical = include_str!(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../minecraft/core-liquid-template/src/main/java/ca/teamdman/sfm/client/action/PanelActionSupport.java"
    ));
    assert_eq!(
        canonical.matches(source).count(),
        1,
        "canonical fallback fragment drift"
    );
    let yes = r#"        java.util.Optional<String> activePaletteSession = activeScreen instanceof SFMCommandPaletteScreen palette
                && palette.documentHistoryAvailable()
                ? java.util.Optional.of(palette.documentHistorySessionId())
                : java.util.Optional.empty();
"#;
    let no = r#"        java.util.Optional<String> activePaletteSession = java.util.Optional.empty();
"#;
    for mask in 0..4 {
        let context = two_flag_context("command_palette", "document_history", mask);
        let actual = render_java_source(source, &context)?;
        assert_eq!(
            actual.as_bytes(),
            if mask == 3 {
                yes.as_bytes()
            } else {
                no.as_bytes()
            },
            "mask {mask}"
        );
        assert!(!actual.contains("{%"));
    }
    Ok(())
}

#[test]
fn pane_close_choice_fallback_preserves_all_four_exact_byte_outputs() -> Result<()> {
    let source = r#"{% if features.workspace_lifecycle %}
{% if features.typed_command_palette %}
                SFMActionChoice.invoke(PANEL_ENTRY_MOVE_BELOW, argument, "Move " + target + " below"),
                SFMActionChoice.invoke(PANE_CLOSE, "", "Close pane containing " + target)
{% else %}
                SFMActionChoice.invoke(PANEL_ENTRY_MOVE_BELOW, argument, "Move " + target + " below")
{% endif %}
{% else %}
                SFMActionChoice.invoke(PANEL_ENTRY_MOVE_BELOW, argument, "Move " + target + " below")
{% endif %}
"#;
    let canonical = include_str!(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../minecraft/core-liquid-template/src/main/java/ca/teamdman/sfm/client/action/SFMWorkspaceLifecycleActionIds.java"
    ));
    assert_eq!(
        canonical.matches(source).count(),
        1,
        "canonical fallback fragment drift"
    );
    let yes = r#"                SFMActionChoice.invoke(PANEL_ENTRY_MOVE_BELOW, argument, "Move " + target + " below"),
                SFMActionChoice.invoke(PANE_CLOSE, "", "Close pane containing " + target)
"#;
    let no = r#"                SFMActionChoice.invoke(PANEL_ENTRY_MOVE_BELOW, argument, "Move " + target + " below")
"#;
    for mask in 0..4 {
        let context = two_flag_context("workspace_lifecycle", "typed_command_palette", mask);
        let actual = render_java_source(source, &context)?;
        assert_eq!(
            actual.as_bytes(),
            if mask == 3 {
                yes.as_bytes()
            } else {
                no.as_bytes()
            },
            "mask {mask}"
        );
        assert!(!actual.contains("{%"));
    }
    Ok(())
}
