package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;

/** Executes a command through the visible command palette input. */
public final class ExecuteCommandPalettePuppetAction implements SFMPuppetAction {
    private final String command;
    private int ticks;
    private boolean requested;

    public ExecuteCommandPalettePuppetAction(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("Command palette command must not be blank");
        }
        this.command = command;
    }

    @Override
    public String description() {
        return "execute command palette command: " + this.command;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!runtime.isScreen(SFMCommandPaletteScreen.class)) {
            if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
                throw new IllegalStateException("Timed out waiting for command palette before executing command");
            }
            return false;
        }
        if (!requested) {
            requested = true;
            runtime.executeCommandPalette(this.command);
            return false;
        }
        return ++ticks > SFMGamePuppetHelper.RENDER_SETTLE_TICKS;
    }
}
