package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record ClickTerminalPropertiesControlPuppetAction(
        String operation
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "click terminal properties control " + operation;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        return runtime.clickTerminalPropertiesControl(operation);
    }
}
