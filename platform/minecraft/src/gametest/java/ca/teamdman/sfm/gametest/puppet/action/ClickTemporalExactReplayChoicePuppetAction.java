package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingTrajectoryController;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Chooses the exact source/source replay from the visible constrained command palette. */
public final class ClickTemporalExactReplayChoicePuppetAction implements SFMPuppetAction {
    @Override
    public String description() {
        return "click exact temporal replay choice";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMDecimalNumberingTrajectoryController controller =
                SFMTemporalReplayPuppetSupport.requireController();
        String sourceBoundary = SFMTemporalReplayPuppetSupport.realInputSourceBoundary(controller);
        runtime.clickActionChoice(SFMTemporalReplayPuppetSupport.command(
                controller,
                SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY,
                sourceBoundary
        ));
        return true;
    }
}
