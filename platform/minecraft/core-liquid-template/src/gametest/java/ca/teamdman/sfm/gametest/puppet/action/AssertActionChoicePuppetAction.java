package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

import java.util.List;

public record AssertActionChoicePuppetAction(List<String> expectedCommands) implements SFMPuppetAction {
    public AssertActionChoicePuppetAction {
        expectedCommands = List.copyOf(expectedCommands);
    }

    @Override
    public String description() {
        return "assert constrained command palette " + expectedCommands;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.assertActionChoice(expectedCommands);
        return true;
    }
}
