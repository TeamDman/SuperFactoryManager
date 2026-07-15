package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record RunGameTestPuppetAction(String testName) implements SFMPuppetAction {
    @Override
    public String description() {

        return "run GameTest " + testName;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {

        return runtime.runGameTest(testName);
    }

}
