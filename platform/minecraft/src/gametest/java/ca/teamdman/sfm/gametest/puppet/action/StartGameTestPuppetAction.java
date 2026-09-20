package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Starts a GameTest while allowing later client actions to run before it completes. */
public record StartGameTestPuppetAction(String testName) implements SFMPuppetAction {
    @Override
    public String description() {
        return "start GameTest " + testName;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        return runtime.startGameTest(testName);
    }
}
