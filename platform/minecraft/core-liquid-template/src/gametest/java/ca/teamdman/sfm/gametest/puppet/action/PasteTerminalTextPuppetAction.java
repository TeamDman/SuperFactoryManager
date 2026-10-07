package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Exercises the visible terminal's Ctrl+V clipboard path with deterministic text. */
public record PasteTerminalTextPuppetAction(String text) implements SFMPuppetAction {
    @Override
    public String description() {
        return "paste terminal text";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.pasteTerminalText(text);
        return true;
    }
}
