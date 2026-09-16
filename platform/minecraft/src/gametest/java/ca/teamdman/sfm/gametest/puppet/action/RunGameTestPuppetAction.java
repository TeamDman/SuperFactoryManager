package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public final class RunGameTestPuppetAction implements SFMPuppetAction {
    private final String testName;
    private boolean prepared;

    public RunGameTestPuppetAction(String testName) {
        this.testName = testName;
    }
    @Override
    public String description() {

        return "run GameTest " + testName;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!prepared) {
            runtime.prepareGameTest();
            prepared = true;
        }
        return runtime.runGameTest(testName);
    }

}
