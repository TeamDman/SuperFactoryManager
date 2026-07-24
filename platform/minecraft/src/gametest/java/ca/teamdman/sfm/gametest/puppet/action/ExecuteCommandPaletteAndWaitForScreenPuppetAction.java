package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;

/** Executes a palette action that replaces the palette with an expected screen. */
public final class ExecuteCommandPaletteAndWaitForScreenPuppetAction implements SFMPuppetAction {
    private final String command;
    private final Class<?> expectedScreen;
    private boolean requested;
    private int ticks;

    public ExecuteCommandPaletteAndWaitForScreenPuppetAction(
            String command,
            Class<?> expectedScreen
    ) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("Command palette command must not be blank");
        }
        this.command = command;
        this.expectedScreen = expectedScreen;
    }

    @Override
    public String description() {
        return "execute command palette command and wait for " + expectedScreen.getSimpleName() + ": " + command;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (runtime.isScreen(expectedScreen)) return true;
        if (!requested) {
            if (!runtime.isScreen(SFMCommandPaletteScreen.class)) {
                if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
                    throw new IllegalStateException("Timed out waiting for command palette before executing command");
                }
                return false;
            }
            requested = true;
            runtime.executeCommandPalette(command);
            if (runtime.isScreen(expectedScreen)) return true;
        }
        if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for " + expectedScreen.getName());
        }
        return false;
    }
}
