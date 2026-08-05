package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record ClickActionChoicePuppetAction(String command) implements SFMPuppetAction {
    @Override
    public String description() {
        return "click bounded action choice " + command;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.clickActionChoice(command);
        return true;
    }
}
