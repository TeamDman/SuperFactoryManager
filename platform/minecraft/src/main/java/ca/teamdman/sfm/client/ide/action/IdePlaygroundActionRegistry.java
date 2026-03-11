package ca.teamdman.sfm.client.ide.action;

import ca.teamdman.sfm.client.screen.IdePlaygroundScreen;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class IdePlaygroundActionRegistry {
    private static final Map<String, Consumer<IdePlaygroundScreen>> ACTIONS = new HashMap<>();
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        ACTIONS.put(IdePlaygroundActionIds.TOGGLE_SHELL_PANEL, IdePlaygroundScreen::toggleShellPanel);
        ACTIONS.put(IdePlaygroundActionIds.TOGGLE_LAYOUT_PANEL, IdePlaygroundScreen::toggleLayoutPanel);
        ACTIONS.put(IdePlaygroundActionIds.TOGGLE_TERMINAL_PANEL, IdePlaygroundScreen::toggleTerminalPanel);
        ACTIONS.put(IdePlaygroundActionIds.FOCUS_SHELL_PANEL, IdePlaygroundScreen::focusShellPanel);
        ACTIONS.put(IdePlaygroundActionIds.FOCUS_WORKSPACE_PANEL, IdePlaygroundScreen::focusWorkspacePanel);
        ACTIONS.put(IdePlaygroundActionIds.FOCUS_LAYOUT_PANEL, IdePlaygroundScreen::focusLayoutPanel);
        ACTIONS.put(IdePlaygroundActionIds.FOCUS_TERMINAL_PANEL, IdePlaygroundScreen::focusTerminalPanel);
        ACTIONS.put(IdePlaygroundActionIds.SELECT_FOCUSED_TARGET, IdePlaygroundScreen::selectFocusedTarget);
        ACTIONS.put(IdePlaygroundActionIds.CLEAR_SELECTED_TARGETS, IdePlaygroundScreen::clearSelectedTargets);

        registerAlias("panel.toggle_shell", IdePlaygroundActionIds.TOGGLE_SHELL_PANEL);
        registerAlias("panel.toggle_layout", IdePlaygroundActionIds.TOGGLE_LAYOUT_PANEL);
        registerAlias("panel.toggle_terminal", IdePlaygroundActionIds.TOGGLE_TERMINAL_PANEL);
        registerAlias("panel.focus_shell", IdePlaygroundActionIds.FOCUS_SHELL_PANEL);
        registerAlias("panel.focus_workspace", IdePlaygroundActionIds.FOCUS_WORKSPACE_PANEL);
        registerAlias("panel.focus_layout", IdePlaygroundActionIds.FOCUS_LAYOUT_PANEL);
        registerAlias("panel.focus_terminal", IdePlaygroundActionIds.FOCUS_TERMINAL_PANEL);
        registerAlias("selection.select_focused", IdePlaygroundActionIds.SELECT_FOCUSED_TARGET);
        registerAlias("selection.clear", IdePlaygroundActionIds.CLEAR_SELECTED_TARGETS);

        registerAlias("/sfm ide toggle_shell", IdePlaygroundActionIds.TOGGLE_SHELL_PANEL);
        registerAlias("/sfm ide toggle_layout", IdePlaygroundActionIds.TOGGLE_LAYOUT_PANEL);
        registerAlias("/sfm ide toggle_terminal", IdePlaygroundActionIds.TOGGLE_TERMINAL_PANEL);
        registerAlias("/sfm ide focus_shell", IdePlaygroundActionIds.FOCUS_SHELL_PANEL);
        registerAlias("/sfm ide focus_workspace", IdePlaygroundActionIds.FOCUS_WORKSPACE_PANEL);
        registerAlias("/sfm ide focus_layout", IdePlaygroundActionIds.FOCUS_LAYOUT_PANEL);
        registerAlias("/sfm ide focus_terminal", IdePlaygroundActionIds.FOCUS_TERMINAL_PANEL);
        registerAlias("/sfm ide select_focused", IdePlaygroundActionIds.SELECT_FOCUSED_TARGET);
        registerAlias("/sfm ide clear_selection", IdePlaygroundActionIds.CLEAR_SELECTED_TARGETS);
    }

    private IdePlaygroundActionRegistry() {
    }

    public static boolean run(String actionId, IdePlaygroundScreen screen) {
        var action = ACTIONS.get(actionId);
        if (action == null) {
            return false;
        }
        action.accept(screen);
        return true;
    }

    public static Optional<String> resolveActionId(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (ACTIONS.containsKey(normalized)) {
            return Optional.of(normalized);
        }
        if (normalized.startsWith("panel.") || normalized.startsWith("selection.")) {
            normalized = "sfm:" + normalized;
            if (ACTIONS.containsKey(normalized)) {
                return Optional.of(normalized);
            }
        }
        return Optional.ofNullable(ALIASES.get(normalized));
    }

    private static void registerAlias(String alias, String actionId) {
        ALIASES.put(alias, actionId);
    }
}