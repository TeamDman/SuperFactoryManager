package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.core.BlockPos;

public final class UseBlockPuppetAction implements SFMPuppetAction {
    private final BlockPos localTarget;

    private int ticks;

    private boolean used;

    public UseBlockPuppetAction(BlockPos localTarget) {

        this.localTarget = localTarget;
    }

    @Override
    public String description() {

        return "use block at " + localTarget;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {

        if (ticks == 0) {
            runtime.positionForBlockUse(localTarget);
        }
        ticks++;
        if (!used && ticks > SFMGamePuppetHelper.RENDER_SETTLE_TICKS) {
            runtime.useBlock(localTarget);
            used = true;
        }
        return used;
    }

}
