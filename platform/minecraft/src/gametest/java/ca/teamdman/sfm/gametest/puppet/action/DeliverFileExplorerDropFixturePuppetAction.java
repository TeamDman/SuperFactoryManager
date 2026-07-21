package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public final class DeliverFileExplorerDropFixturePuppetAction implements SFMPuppetAction {
    @Override public String description() { return "deliver deterministic directory through Screen.onFilesDrop"; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.deliverFileExplorerDropFixture();
        return true;
    }
}
