package ca.teamdman.sfm.client.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMClientActionIconTests {
    private static final SFMClientActionContext CONTEXT = SFMClientActionContext.create(null, () -> true);

    @Test
    public void unrelatedDeveloperActionRetainsTextOnlyFallback() {
        OpenDeveloperPanelAction action = new OpenDeveloperPanelAction(
                OpenDeveloperPanelAction.Scene.TEXT_EDITOR
        );

        assertTrue(action.itemIcon(CONTEXT).isEmpty());
    }
}
