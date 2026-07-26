package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public final class OpenTerminalPuppetAction implements SFMPuppetAction {
    @Override public String description() { return "open Java-local terminal"; }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.openTerminal();
        return true;
    }
}
