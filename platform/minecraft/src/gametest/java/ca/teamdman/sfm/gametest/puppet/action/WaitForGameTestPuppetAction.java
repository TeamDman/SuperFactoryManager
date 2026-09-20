package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Waits for a GameTest that an earlier puppet action started. */
public record WaitForGameTestPuppetAction(String testName) implements SFMPuppetAction {
    @Override
    public String description() {
        return "wait for GameTest " + testName;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        return runtime.waitForGameTest(testName);
    }
}
