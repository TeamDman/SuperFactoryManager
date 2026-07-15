package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.core.BlockPos;

public final class PositionOrbitCameraPuppetAction implements SFMPuppetAction {
    private final BlockPos localTarget;

    private final double radius;

    private final double height;

    private final double angleRadians;

    private int ticks;

    public PositionOrbitCameraPuppetAction(
            BlockPos localTarget,
            double radius,
            double height,
            double angleRadians
    ) {

        this.localTarget = localTarget;
        this.radius = radius;
        this.height = height;
        this.angleRadians = angleRadians;
    }

    @Override
    public String description() {

        return "position orbit camera";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {

        if (ticks == 0) {
            runtime.positionOrbitCamera(localTarget, radius, height, angleRadians);
        }
        ticks++;
        return ticks > SFMGamePuppetHelper.RENDER_SETTLE_TICKS;
    }

}
