package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Sends a real terminal-panel mouse drag through the screen multiplexer. */
public record DragTerminalPuppetAction() implements SFMPuppetAction {
    @Override
    public String description() {
        return "drag terminal";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.dragTerminal();
        return true;
    }
}
