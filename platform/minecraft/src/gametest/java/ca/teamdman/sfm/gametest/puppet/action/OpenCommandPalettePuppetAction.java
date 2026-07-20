package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;

/** Opens the client command palette through its registered action surface. */
public final class OpenCommandPalettePuppetAction implements SFMPuppetAction {
    private int ticks;
    private boolean requested;

    @Override
    public String description() {
        return "open command palette";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!requested) {
            requested = true;
            runtime.openCommandPalette();
        }
        if (runtime.isScreen(SFMCommandPaletteScreen.class)) {
            return true;
        }
        if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for command palette");
        }
        return false;
    }
}
