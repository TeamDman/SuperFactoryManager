package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Requests a deterministic logical resize through the visible Rust terminal panel. */
public record ResizeTerminalPuppetAction(int columns, int rows) implements SFMPuppetAction {
    @Override
    public String description() {
        return "resize terminal to " + columns + "x" + rows;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.resizeTerminal(columns, rows);
        return true;
    }
}
