package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record AssertWorkspacePanelInstancesDistinctPuppetAction(
        int firstPanelIndex,
        int secondPanelIndex
) implements SFMPuppetAction {
    public AssertWorkspacePanelInstancesDistinctPuppetAction {
        if (firstPanelIndex < 0 || secondPanelIndex < 0) {
            throw new IllegalArgumentException("Workspace panel indices must not be negative");
        }
    }

    @Override
    public String description() {
        return "assert workspace panel identities differ " + firstPanelIndex + " and " + secondPanelIndex;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.assertWorkspacePanelInstancesDistinct(firstPanelIndex, secondPanelIndex);
        return true;
    }
}
