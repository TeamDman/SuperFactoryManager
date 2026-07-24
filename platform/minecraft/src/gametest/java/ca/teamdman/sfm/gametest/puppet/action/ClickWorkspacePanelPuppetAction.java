package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record ClickWorkspacePanelPuppetAction(int panelIndex) implements SFMPuppetAction {
    public ClickWorkspacePanelPuppetAction {
        if (panelIndex < 0) throw new IllegalArgumentException("Panel index must not be negative");
    }

    @Override
    public String description() {
        return "click workspace panel " + panelIndex;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!runtime.clickWorkspacePanel(panelIndex)) {
            throw new IllegalStateException("Workspace panel did not receive focus: " + panelIndex);
        }
        return true;
    }
}
