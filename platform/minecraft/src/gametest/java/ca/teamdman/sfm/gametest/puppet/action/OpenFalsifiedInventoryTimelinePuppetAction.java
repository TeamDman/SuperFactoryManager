package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;

public final class OpenFalsifiedInventoryTimelinePuppetAction implements SFMPuppetAction {
    private boolean requested;
    private int ticks;

    @Override
    public String description() {
        return "open falsified inventory timeline";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!requested) {
            requested = true;
            runtime.openFalsifiedInventoryTimeline();
        }
        if (runtime.isScreen(SFMScreenMultiplexer.class)) return true;
        if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out opening falsified inventory timeline");
        }
        return false;
    }
}
