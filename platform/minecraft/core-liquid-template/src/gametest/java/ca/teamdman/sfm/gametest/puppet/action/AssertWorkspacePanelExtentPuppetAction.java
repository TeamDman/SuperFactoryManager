package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceAxis;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record AssertWorkspacePanelExtentPuppetAction(
        int firstPanelIndex,
        int secondPanelIndex,
        SFMWorkspaceAxis axis,
        int expectedComparison
) implements SFMPuppetAction {
    public AssertWorkspacePanelExtentPuppetAction {
        if (firstPanelIndex < 0 || secondPanelIndex < 0) {
            throw new IllegalArgumentException("Workspace panel indices must not be negative");
        }
        if (expectedComparison < -1 || expectedComparison > 1) {
            throw new IllegalArgumentException("Expected comparison must be -1, 0, or 1");
        }
    }

    @Override
    public String description() {
        return "assert workspace panel extent comparison " + firstPanelIndex + " " + expectedComparison
                + " " + secondPanelIndex + " on " + axis;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.assertWorkspacePanelExtentComparison(
                firstPanelIndex,
                secondPanelIndex,
                axis,
                expectedComparison);
        return true;
    }
}
