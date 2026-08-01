package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Restarts the Rust server process owned by SFM and reconnects the open panel. */
public final class RestartRustTerminalServerPuppetAction implements SFMPuppetAction {
    @Override
    public String description() {
        return "restart owned Rust terminal server";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.restartRustTerminalServer();
        return true;
    }
}
