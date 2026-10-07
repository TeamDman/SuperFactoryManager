package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
{% if features.workspace_panel_actions %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
{% endif %}
{% if features.terminal_tuning_actions %}
import ca.teamdman.sfm.client.terminal.SFMTerminalTuningOperation;
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.eventbus.api.IEventBus;
{% else %}
import net.neoforged.bus.api.IEventBus;
{% endcase %}

public final class SFMCommandPaletteActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

{% if features.command_palette %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenCommandPaletteAction> OPEN = REGISTERER.register(
            "palette/open",
            OpenCommandPaletteAction::new
    );

{% endif %}
{% if features.developer_tools %}
    public static final SFMRegistryObject<SFMClientAction<?>, DumpRegistriesAction> DUMP_REGISTRIES = REGISTERER.register(
            "dump_registries",
            DumpRegistriesAction::new
    );

{% endif %}
{% if features.client_action_help %}
    public static final SFMRegistryObject<SFMClientAction<?>, CommandPaletteHelpAction> HELP = REGISTERER.register(
            "help",
            CommandPaletteHelpAction::new
    );

{% endif %}
{% if features.echo_action %}
    public static final SFMRegistryObject<SFMClientAction<?>, EchoAction> ECHO = REGISTERER.register(
            "echo",
            EchoAction::new
    );
{% endif %}

{% if features.client_control_file_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMClientControlEnableAction> ENABLE_FILE_CONTROL = REGISTERER.register(
            "control/files/enable",
            SFMClientControlEnableAction::new
    );

{% endif %}
{% if features.client_action_listing %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMClientControlActionListAction> LIST_ACTIONS = REGISTERER.register(
            "action/list",
            SFMClientControlActionListAction::new
    );

{% endif %}
{% if features.client_log_read_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMClientControlLogsAction> READ_LOGS = REGISTERER.register(
            "logs",
            SFMClientControlLogsAction::new
    );

{% endif %}
{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelAction> OPEN_PANEL = REGISTERER.register(
            "panel/open",
            () -> new OpenPanelAction(OpenPanelAction.Direction.FOCUSED)
    );

{% endif %}
{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelAction> OPEN_PANEL_LEFT = REGISTERER.register(
            "panel/open/left",
            () -> new OpenPanelAction(OpenPanelAction.Direction.LEFT)
    );

{% endif %}
{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelAction> OPEN_PANEL_RIGHT = REGISTERER.register(
            "panel/open/right",
            () -> new OpenPanelAction(OpenPanelAction.Direction.RIGHT)
    );

{% endif %}
{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelAction> OPEN_PANEL_ABOVE = REGISTERER.register(
            "panel/open/above",
            () -> new OpenPanelAction(OpenPanelAction.Direction.ABOVE)
    );

{% endif %}
{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelAction> OPEN_PANEL_BELOW = REGISTERER.register(
            "panel/open/below",
            () -> new OpenPanelAction(OpenPanelAction.Direction.BELOW)
    );

{% endif %}
{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, ClosePanelAction> CLOSE_PANEL = REGISTERER.register(
            "panel/close",
            ClosePanelAction::new
    );

{% endif %}
{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, FocusPanelAction> FOCUS_PANEL_NEXT = REGISTERER.register(
            "panel/focus/next",
            () -> new FocusPanelAction(FocusPanelAction.Operation.NEXT)
    );

{% endif %}
{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, FocusPanelAction> FOCUS_PANEL_PREVIOUS = REGISTERER.register(
            "panel/focus/previous",
            () -> new FocusPanelAction(FocusPanelAction.Operation.PREVIOUS)
    );

{% endif %}
{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, FocusPanelAction> FOCUS_PANEL_INDEX = REGISTERER.register(
            "panel/focus/index",
            () -> new FocusPanelAction(FocusPanelAction.Operation.INDEX)
    );

{% endif %}
{% if features.workspace_panel_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, ToggleMaximizePanelAction> TOGGLE_MAXIMIZE_PANEL = REGISTERER.register(
            "panel/maximize/toggle",
            ToggleMaximizePanelAction::new
    );

{% endif %}
{% if features.workspace_panel_actions %}
{% if features.typed_command_palette %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenPanelDiagnosticsAction> OPEN_PANEL_DIAGNOSTICS = REGISTERER.register(
            "panel/diagnostics/open",
            OpenPanelDiagnosticsAction::new
    );

{% endif %}
{% endif %}
{% if features.client_screen_actions %}
{% if features.workspace_panels %}
    public static final SFMRegistryObject<SFMClientAction<?>, CloseScreenAction> CLOSE_SCREEN = REGISTERER.register(
            "screen/close",
            CloseScreenAction::new
    );

{% endif %}
{% endif %}
{% if features.typed_command_palette %}
    public static final SFMRegistryObject<SFMClientAction<?>, ClosePaletteAction> CLOSE_PALETTE = REGISTERER.register(
            "palette/close",
            ClosePaletteAction::new
    );

{% endif %}
{% if features.focus_target_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMFocusAction> FOCUS = REGISTERER.register(
            "focus",
            SFMFocusAction::new
    );

{% endif %}
{% if features.clipboard_action_commands %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMClipboardCopyAction> COPY_ACTION_COMMAND = REGISTERER.register(
            "clipboard/copy/action",
            SFMClipboardCopyAction::new
    );

{% endif %}
{% if features.typed_command_palette %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMPaletteCandidateCopyAction> COPY_PALETTE_CANDIDATE = REGISTERER.register(
            "palette/candidate/copy",
            SFMPaletteCandidateCopyAction::new
    );

{% endif %}
{% if features.typed_command_palette %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMPaletteCandidateSetCopyAction> COPY_PALETTE_CANDIDATES = REGISTERER.register(
            "palette/candidates/copy",
            SFMPaletteCandidateSetCopyAction::new
    );

{% endif %}
{% if features.context_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMDocumentSelectionCopyAction> COPY_DOCUMENT_SELECTION = REGISTERER.register(
            "document/selection/copy",
            SFMDocumentSelectionCopyAction::new
    );

{% endif %}
{% if features.screen_diagnostics %}
{% if features.workspace_panel_actions %}
{% if features.editor_overlay_push %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMScreenDiagnosticsAction> SCREEN_DIAGNOSTICS = REGISTERER.register(
            "screen/diagnostics",
            SFMScreenDiagnosticsAction::new
    );

{% endif %}
{% endif %}
{% endif %}
{% if features.screen_diagnostics %}
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
{% if features.typed_command_palette %}
    public static final SFMRegistryObject<SFMClientAction<?>, CommandPaletteSuggestionSelectionAction>
            SELECT_FIRST_PALETTE_SUGGESTION = REGISTERER.register(
            "palette/suggestion/select/first",
            () -> new CommandPaletteSuggestionSelectionAction(
                    CommandPaletteSuggestionSelectionAction.Boundary.FIRST)
    );

{% endif %}
{% if features.typed_command_palette %}
    public static final SFMRegistryObject<SFMClientAction<?>, CommandPaletteSuggestionSelectionAction>
            SELECT_LAST_PALETTE_SUGGESTION = REGISTERER.register(
            "palette/suggestion/select/last",
            () -> new CommandPaletteSuggestionSelectionAction(
                    CommandPaletteSuggestionSelectionAction.Boundary.LAST)
    );

{% endif %}
{% if features.workspace_toast_path_actions %}
{% if features.workspace_notifications %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> COPY_TOAST_PATH =
            REGISTERER.register("toast/path/copy", () -> new SFMToastPathAction(SFMToastPathAction.Operation.COPY));
{% endif %}
{% endif %}
{% if features.workspace_toast_path_actions %}
{% if features.workspace_notifications %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> OPEN_TOAST_PATH_TEXT =
            REGISTERER.register("toast/path/text/open", () -> new SFMToastPathAction(SFMToastPathAction.Operation.OPEN_TEXT));
{% endif %}
{% endif %}
{% if features.workspace_toast_path_actions %}
{% if features.workspace_notifications %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> OPEN_TOAST_PATH_EXPLORER =
            REGISTERER.register("toast/path/explorer/open", () -> new SFMToastPathAction(SFMToastPathAction.Operation.OPEN_EXPLORER));

{% endif %}
{% endif %}
{% if features.workspace_toast_actions %}
{% if features.workspace_notifications %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> COPY_TOAST = REGISTERER.register(
            "toast/copy",
            () -> new SFMToastAction(SFMToastAction.Operation.COPY)
    );

{% endif %}
{% endif %}
{% if features.workspace_toast_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> STOP_TOAST_TIMER = REGISTERER.register(
            "toast/timer/stop",
            () -> new SFMToastAction(SFMToastAction.Operation.STOP_TIMER)
    );

{% endif %}
{% if features.workspace_toast_actions %}
{% if features.workspace_notifications %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> COPY_TOAST_DETAILS = REGISTERER.register(
            "toast/details/copy", () -> new SFMToastAction(SFMToastAction.Operation.COPY_DETAILS));

{% endif %}
{% endif %}
{% if features.workspace_toast_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> RESUME_TOAST_TIMER = REGISTERER.register(
            "toast/timer/resume",
            () -> new SFMToastAction(SFMToastAction.Operation.RESUME_TIMER)
    );

{% endif %}
{% if features.workspace_toast_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> DISMISS_TOAST = REGISTERER.register(
            "toast/dismiss",
            () -> new SFMToastAction(SFMToastAction.Operation.DISMISS)
    );

{% endif %}
{% if features.command_history %}
    public static final SFMRegistryObject<SFMClientAction<?>, PaletteHistoryOpenAction> OPEN_PALETTE_HISTORY = REGISTERER.register(
            "palette/history/open",
            () -> new PaletteHistoryOpenAction(PaletteHistoryOpenAction.Direction.CENTER)
    );

{% endif %}
{% if features.command_history %}
    public static final SFMRegistryObject<SFMClientAction<?>, PaletteHistoryOpenAction> OPEN_PALETTE_HISTORY_LEFT = REGISTERER.register(
            "palette/history/open/left",
            () -> new PaletteHistoryOpenAction(PaletteHistoryOpenAction.Direction.LEFT)
    );

{% endif %}
{% if features.command_history %}
    public static final SFMRegistryObject<SFMClientAction<?>, PaletteHistoryOpenAction> OPEN_PALETTE_HISTORY_RIGHT = REGISTERER.register(
            "palette/history/open/right",
            () -> new PaletteHistoryOpenAction(PaletteHistoryOpenAction.Direction.RIGHT)
    );

{% endif %}
{% if features.command_history %}
    public static final SFMRegistryObject<SFMClientAction<?>, PaletteHistoryOpenAction> OPEN_PALETTE_HISTORY_ABOVE = REGISTERER.register(
            "palette/history/open/above",
            () -> new PaletteHistoryOpenAction(PaletteHistoryOpenAction.Direction.ABOVE)
    );

{% endif %}
{% if features.command_history %}
    public static final SFMRegistryObject<SFMClientAction<?>, PaletteHistoryOpenAction> OPEN_PALETTE_HISTORY_BELOW = REGISTERER.register(
            "palette/history/open/below",
            () -> new PaletteHistoryOpenAction(PaletteHistoryOpenAction.Direction.BELOW)
    );

{% endif %}
{% if features.command_history %}
    public static final SFMRegistryObject<SFMClientAction<?>, PaletteHistoryClearAction> CLEAR_PALETTE_HISTORY = REGISTERER.register(
            "palette/history/clear",
            PaletteHistoryClearAction::new
    );

{% endif %}
{% if features.command_history %}
    public static final SFMRegistryObject<SFMClientAction<?>, PaletteHistoryPersistenceAction> ENABLE_PALETTE_HISTORY = REGISTERER.register(
            "palette/history/persistence/enable",
            () -> new PaletteHistoryPersistenceAction(PaletteHistoryPersistenceAction.Operation.ENABLE)
    );

{% endif %}
{% if features.command_history %}
    public static final SFMRegistryObject<SFMClientAction<?>, PaletteHistoryPersistenceAction> DISABLE_PALETTE_HISTORY = REGISTERER.register(
            "palette/history/persistence/disable",
            () -> new PaletteHistoryPersistenceAction(PaletteHistoryPersistenceAction.Operation.DISABLE)
    );

{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_LEFT = REGISTERER.register(
            "panel/move/left",
            () -> new MovePanelAction(SFMWorkspaceSide.LEFT)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_RIGHT = REGISTERER.register(
            "panel/move/right",
            () -> new MovePanelAction(SFMWorkspaceSide.RIGHT)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_ABOVE = REGISTERER.register(
            "panel/move/above",
            () -> new MovePanelAction(SFMWorkspaceSide.ABOVE)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, MovePanelAction> MOVE_PANEL_BELOW = REGISTERER.register(
            "panel/move/below",
            () -> new MovePanelAction(SFMWorkspaceSide.BELOW)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_panel_reopening %}
    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_LEFT = REGISTERER.register(
            "panel/duplicate/left",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.LEFT)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_panel_reopening %}
    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_RIGHT = REGISTERER.register(
            "panel/duplicate/right",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.RIGHT)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_panel_reopening %}
    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_ABOVE = REGISTERER.register(
            "panel/duplicate/above",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.ABOVE)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_panel_reopening %}
    public static final SFMRegistryObject<SFMClientAction<?>, DuplicatePanelAction> DUPLICATE_PANEL_BELOW = REGISTERER.register(
            "panel/duplicate/below",
            () -> new DuplicatePanelAction(SFMWorkspaceSide.BELOW)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_directional_resize %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_LEFT = REGISTERER.register(
            "panel/resize/left",
            () -> new ResizePanelAction(SFMWorkspaceSide.LEFT)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_directional_resize %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_RIGHT = REGISTERER.register(
            "panel/resize/right",
            () -> new ResizePanelAction(SFMWorkspaceSide.RIGHT)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_directional_resize %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_ABOVE = REGISTERER.register(
            "panel/resize/above",
            () -> new ResizePanelAction(SFMWorkspaceSide.ABOVE)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_directional_resize %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizePanelAction> RESIZE_PANEL_BELOW = REGISTERER.register(
            "panel/resize/below",
            () -> new ResizePanelAction(SFMWorkspaceSide.BELOW)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_dividers %}
    public static final SFMRegistryObject<SFMClientAction<?>, ResizeDividersAction> RESIZE_DIVIDERS = REGISTERER.register(
            "panel/resize/dividers",
            ResizeDividersAction::new
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_panel_metadata %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> SET_PANEL_SCALE = REGISTERER.register(
            "panel/scale/set",
            () -> new PanelScaleAction(PanelScaleAction.Operation.SET)
    );

{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_panel_metadata %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> INCREASE_PANEL_SCALE = REGISTERER.register(
            "panel/scale/increase",
            () -> new PanelScaleAction(PanelScaleAction.Operation.INCREASE)
    );

{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_panel_metadata %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> DECREASE_PANEL_SCALE = REGISTERER.register(
            "panel/scale/decrease",
            () -> new PanelScaleAction(PanelScaleAction.Operation.DECREASE)
    );

{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_panel_metadata %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, PanelScaleAction> CLEAR_PANEL_SCALE = REGISTERER.register(
            "panel/scale/clear",
            () -> new PanelScaleAction(PanelScaleAction.Operation.CLEAR)
    );

{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_CONTENT_LEFT = REGISTERER.register(
            "panel/rotate/content/left",
            () -> new RotatePanelAction(RotatePanelAction.Kind.CONTENT, RotatePanelAction.Direction.LEFT)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_CONTENT_RIGHT = REGISTERER.register(
            "panel/rotate/content/right",
            () -> new RotatePanelAction(RotatePanelAction.Kind.CONTENT, RotatePanelAction.Direction.RIGHT)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_SCALE_LEFT = REGISTERER.register(
            "panel/rotate/scale/left",
            () -> new RotatePanelAction(RotatePanelAction.Kind.SCALE, RotatePanelAction.Direction.LEFT)
    );

{% endif %}
{% endif %}
{% if features.workspace_panel_actions %}
{% if features.workspace_stack_controls %}
    public static final SFMRegistryObject<SFMClientAction<?>, RotatePanelAction> ROTATE_SCALE_RIGHT = REGISTERER.register(
            "panel/rotate/scale/right",
            () -> new RotatePanelAction(RotatePanelAction.Kind.SCALE, RotatePanelAction.Direction.RIGHT)
    );

{% endif %}
{% endif %}
{% if features.workspace_legacy_open_action %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenScreenToSideAction> OPEN_SCREEN_TO_SIDE = REGISTERER.register(
            "workspace/open_to_side",
{% case minecraft_version %}
{% when "26.1.2" %}
            () -> new OpenScreenToSideAction()
{% else %}
            OpenScreenToSideAction::new
{% endcase %}
    );

{% endif %}
{% if features.keyboard_profiles %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenKeyBindingScreenAction> MANAGE_KEY_BINDINGS = REGISTERER.register(
            "keybindings/manage",
            OpenKeyBindingScreenAction::new
    );

{% endif %}
{% if features.keybinding_settings %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMKeyBindingManagerAction> KEY_BINDINGS_SORT_SET = REGISTERER.register(
            "keybindings/sort/set",
            () -> new SFMKeyBindingManagerAction(SFMKeyBindingManagerAction.Kind.SORT_SET)
    );

{% endif %}
{% if features.keybinding_settings %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMKeyBindingManagerAction> KEY_BINDINGS_SCOPE_SET = REGISTERER.register(
            "keybindings/scope/set",
            () -> new SFMKeyBindingManagerAction(SFMKeyBindingManagerAction.Kind.SCOPE_SET)
    );

{% endif %}
{% if features.keybinding_settings %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMKeyBindingManagerAction> KEY_BINDINGS_DISPLAY_SET = REGISTERER.register(
            "keybindings/display/set",
            () -> new SFMKeyBindingManagerAction(SFMKeyBindingManagerAction.Kind.DISPLAY_SET)
    );

{% endif %}
{% if features.repository_review_bundle_actions and features.legacy_repository_review and features.client_theme and features.legacy_file_explorer %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenReviewBundleAction> OPEN_REVIEW_BUNDLE = REGISTERER.register(
            "review/open_bundle",
            OpenReviewBundleAction::new
    );

{% endif %}
{% if features.terminal_legacy_open_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenTerminalAction> OPEN_TERMINAL = REGISTERER.register(
            "terminal/open",
            OpenTerminalAction::new
    );

{% endif %}
{% if features.client_screen_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenMinecraftScreenAction> OPEN_MINECRAFT_SCREEN = REGISTERER.register(
            "minecraft/screen/open",
            OpenMinecraftScreenAction::new
    );

{% endif %}
{% if features.manager_direct_edit_action %}
    public static final SFMRegistryObject<SFMClientAction<?>, ManagerEditAction> MANAGER_EDIT = REGISTERER.register(
            "manager/edit",
            ManagerEditAction::new
    );

{% endif %}
{% if features.client_screen_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMGuiScaleAction> SET_GUI_SCALE = REGISTERER.register(
            "ui/gui_scale/set",
            () -> new SFMGuiScaleAction(SFMGuiScaleAction.Operation.SET)
    );

{% endif %}
{% if features.client_screen_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMGuiScaleAction> INCREMENT_GUI_SCALE = REGISTERER.register(
            "ui/gui_scale/increment",
            () -> new SFMGuiScaleAction(SFMGuiScaleAction.Operation.INCREMENT)
    );

{% endif %}
{% if features.client_screen_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMGuiScaleAction> DECREMENT_GUI_SCALE = REGISTERER.register(
            "ui/gui_scale/decrement",
            () -> new SFMGuiScaleAction(SFMGuiScaleAction.Operation.DECREMENT)
    );

{% endif %}
{% if features.terminal_repl_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenReplAction> OPEN_REPL = REGISTERER.register(
            "repl/open",
            OpenReplAction::new
    );

{% endif %}
{% if features.terminal_remote %}
    public static final SFMRegistryObject<SFMClientAction<?>, ConnectRustServerAction> CONNECT_RUST_SERVER = REGISTERER.register(
{% if features.terminal_presentation_actions %}
            "terminal/server/connect",
{% else %}
            "terminal/connect-rust-server",
{% endif %}
            ConnectRustServerAction::new
    );

{% endif %}
{% if features.terminal_remote %}
    public static final SFMRegistryObject<SFMClientAction<?>, StartRustServerAction> START_RUST_SERVER = REGISTERER.register(
{% if features.terminal_presentation_actions %}
            "terminal/server/start",
{% else %}
            "terminal/start-rust-server",
{% endif %}
            StartRustServerAction::new
    );

{% endif %}
{% if features.terminal_presentation_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SetTerminalTransportAction> SET_TERMINAL_TRANSPORT = REGISTERER.register(
            "terminal/transport/set",
            SetTerminalTransportAction::new
    );

{% endif %}
{% if features.terminal_presentation_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, SetTerminalRendererAction> SET_TERMINAL_RENDERER = REGISTERER.register(
            "terminal/renderer/set",
            SetTerminalRendererAction::new
    );

{% endif %}
{% if features.terminal_presentation_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, ToggleTerminalPresentationAction> TOGGLE_TERMINAL_PRESENTATION = REGISTERER.register(
            "terminal/presentation/toggle",
            ToggleTerminalPresentationAction::new
    );

{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_SURFACE_AUTO = REGISTERER.register(
            "terminal/properties/surface/auto",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.SURFACE_AUTO)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_SURFACE_SET = REGISTERER.register(
            "terminal/properties/surface/set",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.SURFACE_SET)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_SURFACE_WIDTH_INCREASE = REGISTERER.register(
            "terminal/properties/surface/width/increase",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.SURFACE_WIDTH_INCREASE)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_SURFACE_WIDTH_DECREASE = REGISTERER.register(
            "terminal/properties/surface/width/decrease",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.SURFACE_WIDTH_DECREASE)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_SURFACE_HEIGHT_INCREASE = REGISTERER.register(
            "terminal/properties/surface/height/increase",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.SURFACE_HEIGHT_INCREASE)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_SURFACE_HEIGHT_DECREASE = REGISTERER.register(
            "terminal/properties/surface/height/decrease",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.SURFACE_HEIGHT_DECREASE)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_FONT_AUTO = REGISTERER.register(
            "terminal/properties/font/auto",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.FONT_AUTO)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_FONT_SET = REGISTERER.register(
            "terminal/properties/font/set",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.FONT_SET)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_FONT_INCREASE = REGISTERER.register(
            "terminal/properties/font/increase",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.FONT_INCREASE)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_FONT_DECREASE = REGISTERER.register(
            "terminal/properties/font/decrease",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.FONT_DECREASE)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_CELLS_AUTO = REGISTERER.register(
            "terminal/properties/cells/auto",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.CELLS_AUTO)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_CELLS_SET = REGISTERER.register(
            "terminal/properties/cells/set",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.CELLS_SET)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_COLUMNS_INCREASE = REGISTERER.register(
            "terminal/properties/cells/columns/increase",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.COLUMNS_INCREASE)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_COLUMNS_DECREASE = REGISTERER.register(
            "terminal/properties/cells/columns/decrease",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.COLUMNS_DECREASE)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_ROWS_INCREASE = REGISTERER.register(
            "terminal/properties/cells/rows/increase",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.ROWS_INCREASE)
    );
{% endif %}
{% if features.terminal_tuning_actions %}
    public static final SFMRegistryObject<SFMClientAction<?>, TerminalPropertiesAction> TERMINAL_ROWS_DECREASE = REGISTERER.register(
            "terminal/properties/cells/rows/decrease",
            () -> new TerminalPropertiesAction(SFMTerminalTuningOperation.ROWS_DECREASE)
    );

{% endif %}
{% if features.client_theme %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMThemeAction> THEME_RELOAD = REGISTERER.register(
            "theme/reload",
            () -> new SFMThemeAction(SFMThemeAction.Operation.RELOAD)
    );

{% endif %}
{% if features.client_theme %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMThemeAction> THEME_RESTORE_DEFAULTS = REGISTERER.register(
            "theme/restore_defaults",
            () -> new SFMThemeAction(SFMThemeAction.Operation.RESTORE_DEFAULTS)
    );

{% endif %}
{% if features.client_theme %}
    public static final SFMRegistryObject<SFMClientAction<?>, SFMThemeAction> THEME_OPEN_FILE = REGISTERER.register(
            "theme/open_file",
            () -> new SFMThemeAction(SFMThemeAction.Operation.OPEN_FILE)
    );
{% endif %}
{% if features.client_theme %}
    public static final SFMRegistryObject<SFMClientAction<?>, OpenThemeSettingsAction> THEME_SETTINGS = REGISTERER.register(
            "theme/settings", OpenThemeSettingsAction::new
    );

{% endif %}
    private SFMCommandPaletteActions() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
