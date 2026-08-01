package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Sends a real terminal-panel mouse click through the screen multiplexer. */
public record ClickTerminalPuppetAction() implements SFMPuppetAction {
    @Override
    public String description() {
        return "click terminal";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.clickTerminal();
        return true;
    }
}
