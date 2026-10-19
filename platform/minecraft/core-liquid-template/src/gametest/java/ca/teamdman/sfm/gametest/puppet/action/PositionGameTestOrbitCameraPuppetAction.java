package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;

public final class PositionGameTestOrbitCameraPuppetAction implements SFMPuppetAction {
    private final double angleRadians;

    private int ticks;
    private int waitingTicks;

    public PositionGameTestOrbitCameraPuppetAction(double angleRadians) {
        this.angleRadians = angleRadians;
    }

    @Override
    public String description() {
        return "position GameTest orbit camera";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!"world".equals(runtime.currentScreenName())) {
            ticks = 0;
            if (++waitingTicks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
                throw new IllegalStateException("Timed out waiting for the world before GameTest orbit capture: "
                        + runtime.currentScreenName());
            }
            return false;
        }
        if (ticks == 0) {
            runtime.positionGameTestOrbitCamera(angleRadians);
        }
        ticks++;
        return ticks > SFMGamePuppetHelper.RENDER_SETTLE_TICKS;
    }
}
