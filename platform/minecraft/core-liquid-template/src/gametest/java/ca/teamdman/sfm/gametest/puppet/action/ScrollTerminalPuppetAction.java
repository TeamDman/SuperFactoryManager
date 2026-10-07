package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record ScrollTerminalPuppetAction(double delta) implements SFMPuppetAction {
    @Override
    public String description() {
        return "scroll terminal by " + delta;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.scrollTerminal(delta);
        return true;
    }
}
