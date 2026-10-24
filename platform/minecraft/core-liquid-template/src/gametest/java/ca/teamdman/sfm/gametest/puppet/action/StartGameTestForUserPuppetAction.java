package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Start the fixture and wait only until it has a visible structure. */
public final class StartGameTestForUserPuppetAction implements SFMPuppetAction {
    private final String testName;
    private boolean prepared;

    public StartGameTestForUserPuppetAction(String testName) {
        this.testName = testName;
    }

    @Override public String description() { return "start GameTest for user " + testName; }

    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!prepared) {
            runtime.prepareGameTest();
            prepared = true;
        }
        return runtime.startGameTestForUser(testName);
    }
}
