package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Terminal action that gives a prepared, still-running world back to its user. */
public final class YieldToUserPuppetAction implements SFMPuppetAction {
    @Override public String description() { return "yield to user"; }

    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.yieldToUser();
        return true;
    }
}
