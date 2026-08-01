package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Sends the protocol cancellation request through the currently visible terminal. */
public record CancelTerminalPuppetAction() implements SFMPuppetAction {
    @Override public String description() { return "cancel terminal operation"; }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.cancelTerminal();
        return true;
    }
}
