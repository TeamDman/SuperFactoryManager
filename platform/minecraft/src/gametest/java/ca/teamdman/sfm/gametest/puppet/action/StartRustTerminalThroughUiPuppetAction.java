package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Proves the disconnected Start/Retry child through the real keyboard path. */
public final class StartRustTerminalThroughUiPuppetAction implements SFMPuppetAction {
    @Override
    public String description() {
        return "start Rust terminal through focused Start/Retry widget";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.startRustTerminalThroughUi();
        return true;
    }
}
