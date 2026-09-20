package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record PressScreenKeyPuppetAction(int keyCode, int modifiers) implements SFMPuppetAction {
    @Override
    public String description() {
        return "press screen key " + keyCode + " modifiers " + modifiers;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.pressScreenKey(keyCode, modifiers);
        return true;
    }
}
