package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMTestScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceAxis;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceDivider;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceDividerLinkId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Opens deterministic two-, three-, and explicitly-linked four-pane resize fixtures. */
public record OpenWorkspaceDividerFixturePuppetAction(int paneCount) implements SFMPuppetAction {
    public OpenWorkspaceDividerFixturePuppetAction {
        if (paneCount < 2 || paneCount > 4) {
            throw new IllegalArgumentException("Workspace divider fixture requires 2, 3, or 4 panes");
        }
    }

    @Override
    public String description() {
        return "open " + paneCount + "-pane workspace divider fixture";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        Minecraft minecraft = Minecraft.getInstance();
        SFMWorkspaceLayout layout = switch (paneCount) {
            case 2 -> SFMWorkspaceLayout.sideBySide(panel("left"), panel("right"));
            case 3 -> SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                    SFMWorkspaceLayout.panel(panel("left")),
                    SFMWorkspaceLayout.vertical(
                            SFMWorkspaceLayout.panel(panel("top right")),
                            SFMWorkspaceLayout.panel(panel("bottom right")))));
            case 4 -> fourPaneLayout();
            default -> throw new IllegalStateException("Unsupported pane count: " + paneCount);
        };
        minecraft.setScreen(SFMScreenMultiplexer.create(minecraft.screen, layout));
        return true;
    }

    private static SFMWorkspaceLayout fourPaneLayout() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.vertical(
                SFMWorkspaceLayout.horizontal(
                        SFMWorkspaceLayout.panel(panel("top left")),
                        SFMWorkspaceLayout.panel(panel("top right"))),
                SFMWorkspaceLayout.horizontal(
                        SFMWorkspaceLayout.panel(panel("bottom left")),
                        SFMWorkspaceLayout.panel(panel("bottom right")))));
        List<ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceDividerId> columns = layout.dividers(
                        new SFMScreenPanelBounds(0, 0, 1000, 800), 2, 3, 48).stream()
                .filter(divider -> divider.axis() == SFMWorkspaceAxis.HORIZONTAL)
                .map(SFMWorkspaceDivider::id)
                .toList();
        layout.linkDividers(new SFMWorkspaceDividerLinkId("puppet-four-pane-columns"), columns);
        return layout;
    }

    private static SFMTestScreenPanel panel(String label) {
        return new SFMTestScreenPanel("divider fixture: " + label);
    }
}
