package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public final class WaitForScreenPuppetAction implements SFMPuppetAction {
    private final Class<?> expectedScreen;

    private int ticks;

    public WaitForScreenPuppetAction(Class<?> expectedScreen) {

        this.expectedScreen = expectedScreen;
    }

    @Override
    public String description() {

        return "wait for " + expectedScreen.getSimpleName();
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {

        if (runtime.isScreen(expectedScreen)) {
            return true;
        }
        ticks++;
        if (ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for " + expectedScreen.getName());
        }
        return false;
    }

}
