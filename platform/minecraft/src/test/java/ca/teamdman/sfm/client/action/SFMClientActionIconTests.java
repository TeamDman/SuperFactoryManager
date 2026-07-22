package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMTitleScreenDevScreen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMClientActionIconTests {
    private static final SFMClientActionContext CONTEXT = SFMClientActionContext.create(null, () -> true);

    @Test
    public void fileExplorerActionReusesDirectoryPresentationIcon() {
        OpenTitleScreenDevScreenAction action = new OpenTitleScreenDevScreenAction(
                SFMTitleScreenDevScreen.FILE_EXPLORER
        );

        var icon = action.itemIcon(CONTEXT);

        assertTrue(icon.isPresent());
        assertEquals("minecraft:chest", icon.orElseThrow().requestedItem().toString());
        assertEquals("directory", icon.orElseThrow().accessibleLabel());
    }

    @Test
    public void unrelatedDeveloperActionRetainsTextOnlyFallback() {
        OpenTitleScreenDevScreenAction action = new OpenTitleScreenDevScreenAction(
                SFMTitleScreenDevScreen.DRAW_CANVAS
        );

        assertTrue(action.itemIcon(CONTEXT).isEmpty());
    }
}
