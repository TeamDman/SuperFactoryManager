package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record TypeTerminalTextPuppetAction(String text) implements SFMPuppetAction {
    @Override
    public String description() {
        return "type terminal text";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.typeTerminalText(text);
        return true;
    }
}
