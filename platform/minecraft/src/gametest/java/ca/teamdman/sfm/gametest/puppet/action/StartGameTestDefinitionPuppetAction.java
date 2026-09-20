package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Starts a fixture owned by one game puppet rather than ordinary GameTest discovery. */
public record StartGameTestDefinitionPuppetAction(
        SFMGameTestDefinition testDefinition
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "start puppet-owned GameTest " + testDefinition.testName();
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        return runtime.startGameTest(testDefinition);
    }
}
