package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Sends a key transition directly to Rust, bypassing SFM's focus gestures. */
public record PressTerminalKeyDirectPuppetAction(int keyCode, int modifiers) implements SFMPuppetAction {
    @Override
    public String description() {
        return "send terminal key directly to Rust";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.pressTerminalKeyDirect(keyCode, modifiers);
        return true;
    }
}
