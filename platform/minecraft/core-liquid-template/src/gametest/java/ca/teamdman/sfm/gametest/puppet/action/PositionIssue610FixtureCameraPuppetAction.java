package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;

/** Locate an unobstructed polished-andesite floor cell before yielding the FE fixture. */
public final class PositionIssue610FixtureCameraPuppetAction implements SFMPuppetAction {
    private int ticks;
    private int waitingTicks;

    @Override public String description() { return "position Issue 610 fixture camera"; }

    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (ticks == 0 && !runtime.positionIssue610FixtureCamera()) {
            if (++waitingTicks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
                throw new IllegalStateException("Timed out waiting for visible Issue 610 fixture floor");
            }
            return false;
        }
        ticks++;
        return ticks > SFMGamePuppetHelper.RENDER_SETTLE_TICKS;
    }
}
