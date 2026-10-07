package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record ExecuteTerminalPuppetAction(String command) implements SFMPuppetAction {
    @Override public String description() { return "execute terminal command " + command; }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.executeTerminal(command);
        return true;
    }
}
