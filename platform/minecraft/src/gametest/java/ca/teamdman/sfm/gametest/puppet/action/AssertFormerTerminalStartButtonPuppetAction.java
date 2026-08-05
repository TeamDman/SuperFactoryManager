package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Proves loaded terminal pixels cannot activate the former disconnected button. */
public record AssertFormerTerminalStartButtonPuppetAction() implements SFMPuppetAction {
    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!runtime.isFormerTerminalStartButtonRoutingReady()) return false;
        runtime.assertFormerTerminalStartButtonRoutesToTerminal();
        return true;
    }

    @Override
    public String description() {
        return "assert former terminal Start/Retry coordinates route to terminal input";
    }
}
