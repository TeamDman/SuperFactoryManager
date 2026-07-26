package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record PressTerminalKeyPuppetAction(int keyCode) implements SFMPuppetAction {
    @Override
    public String description() {
        return "press terminal key " + keyCode;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.pressTerminalKey(keyCode);
        return true;
    }
}
