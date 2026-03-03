package ca.teamdman.sfm.client.ide.action;

import ca.teamdman.sfm.client.screen.ManagerIdeScreen;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class ManagerIdeActionRegistry {
    private static final Map<String, Consumer<ManagerIdeScreen>> ACTIONS = new HashMap<>();

    static {
        ACTIONS.put(ManagerIdeActionIds.TOGGLE_RIGHT_PANEL, ManagerIdeScreen::toggleRightPanel);
        ACTIONS.put(ManagerIdeActionIds.SHOW_RIGHT_PANEL, x -> x.setRightPanelVisible(true));
        ACTIONS.put(ManagerIdeActionIds.HIDE_RIGHT_PANEL, x -> x.setRightPanelVisible(false));
        ACTIONS.put(ManagerIdeActionIds.TOGGLE_BOTTOM_PANEL, ManagerIdeScreen::toggleBottomPanel);
        ACTIONS.put(ManagerIdeActionIds.SHOW_BOTTOM_PANEL, x -> x.setBottomPanelVisible(true));
        ACTIONS.put(ManagerIdeActionIds.HIDE_BOTTOM_PANEL, x -> x.setBottomPanelVisible(false));
        ACTIONS.put(ManagerIdeActionIds.FOCUS_EXPLORER_PANEL, ManagerIdeScreen::focusExplorerPanel);
    }

    private ManagerIdeActionRegistry() {
    }

    public static boolean run(String actionId, ManagerIdeScreen screen) {
        var action = ACTIONS.get(actionId);
        if (action == null) {
            return false;
        }
        action.accept(screen);
        return true;
    }
}
