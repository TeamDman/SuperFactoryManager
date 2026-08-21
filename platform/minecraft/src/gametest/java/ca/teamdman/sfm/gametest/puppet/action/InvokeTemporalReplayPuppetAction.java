package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingTrajectoryController;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

import java.util.Objects;

/** Resolves runtime-generated state ids, then types an ordinary registered replay action. */
public record InvokeTemporalReplayPuppetAction(
        SFMTemporalReplayArchive.ReplayMode mode,
        Target target
) implements SFMPuppetAction {
    public InvokeTemporalReplayPuppetAction {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public String description() {
        return "invoke registered " + mode + " against " + target;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMDecimalNumberingTrajectoryController controller =
                SFMTemporalReplayPuppetSupport.requireController();
        String sourceBoundary = SFMTemporalReplayPuppetSupport.realInputSourceBoundary(controller);
        String targetParent = target == Target.SOURCE_BOUNDARY
                ? sourceBoundary
                : controller.currentState().revisionId();
        runtime.executeCommandPalette(SFMTemporalReplayPuppetSupport.command(
                controller,
                mode,
                targetParent
        ));
        return true;
    }

    public enum Target {
        SOURCE_BOUNDARY,
        CURRENT_HEAD
    }
}
