package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;

public final class CloseScreenNaturallyPuppetAction implements SFMPuppetAction {
    private int ticks;

    @Override
    public String description() {
        return "close current screen through its back behavior";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (ticks++ == 0) runtime.closeScreenNaturally();
        return ticks > SFMGamePuppetHelper.RENDER_SETTLE_TICKS;
    }
}
