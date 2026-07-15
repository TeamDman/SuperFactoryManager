package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public final class CloseScreenPuppetAction implements SFMPuppetAction {
    private int ticks;

    @Override
    public String description() {

        return "close screen";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {

        if (ticks == 0) {
            runtime.closeScreen();
        }
        ticks++;
        return ticks > SFMGamePuppetHelper.RENDER_SETTLE_TICKS;
    }

}
