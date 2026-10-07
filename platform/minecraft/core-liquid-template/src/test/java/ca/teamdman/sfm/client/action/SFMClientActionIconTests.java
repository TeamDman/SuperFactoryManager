package ca.teamdman.sfm.client.action;

{% if features.workspace_panel_actions %}
{% else %}
import ca.teamdman.sfm.client.screen.SFMTitleScreenDevScreen;
{% endif %}
import org.junit.jupiter.api.Test;

{% if features.workspace_panel_actions %}
{% else %}
{% if features.legacy_file_explorer %}
import static org.junit.jupiter.api.Assertions.assertEquals;
{% endif %}
{% endif %}
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMClientActionIconTests {
    private static final SFMClientActionContext CONTEXT = SFMClientActionContext.create(null, () -> true);
{% if features.workspace_panel_actions %}
{% else %}
{% if features.legacy_file_explorer %}

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
{% endif %}
{% endif %}

    @Test
    public void unrelatedDeveloperActionRetainsTextOnlyFallback() {
{% if features.workspace_panel_actions %}
        OpenDeveloperPanelAction action = new OpenDeveloperPanelAction(
                OpenDeveloperPanelAction.Scene.TEXT_EDITOR
        );
{% else %}
        OpenTitleScreenDevScreenAction action = new OpenTitleScreenDevScreenAction(
                SFMTitleScreenDevScreen.DRAW_CANVAS
        );
{% endif %}

        assertTrue(action.itemIcon(CONTEXT).isEmpty());
    }
}
