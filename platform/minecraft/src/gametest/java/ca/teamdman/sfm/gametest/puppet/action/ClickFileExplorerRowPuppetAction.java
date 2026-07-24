package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record ClickFileExplorerRowPuppetAction(int visibleRowIndex) implements SFMPuppetAction {
    public ClickFileExplorerRowPuppetAction {
        if (visibleRowIndex < 0) throw new IllegalArgumentException("Visible row index must be non-negative");
    }
    @Override public String description() { return "click file explorer visible row " + visibleRowIndex; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.clickFileExplorerRow(visibleRowIndex);
        return true;
    }
}
