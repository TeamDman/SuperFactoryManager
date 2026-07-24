package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record PressFileExplorerKeyPuppetAction(int keyCode) implements SFMPuppetAction {
    @Override
    public String description() {
        return "press file explorer key " + keyCode;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.pressFileExplorerKey(keyCode);
        return true;
    }
}
