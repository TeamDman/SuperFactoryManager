package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Uses Minecraft's clipboard plus the ordinary-shell right-click branch. */
public record RightClickPasteTerminalTextPuppetAction(String text) implements SFMPuppetAction {
    @Override
    public String description() {
        return "right-click paste terminal text";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.pasteTerminalTextByRightClick(text);
        return true;
    }
}
