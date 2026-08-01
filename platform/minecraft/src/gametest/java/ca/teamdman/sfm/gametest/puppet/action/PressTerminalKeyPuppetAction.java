package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record PressTerminalKeyPuppetAction(int keyCode, int modifiers) implements SFMPuppetAction {
    public PressTerminalKeyPuppetAction(int keyCode) {
        this(keyCode, 0);
    }

    @Override
    public String description() {
        return "press terminal key " + keyCode + " with modifiers " + modifiers;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.pressTerminalKey(keyCode, modifiers);
        return true;
    }
}
