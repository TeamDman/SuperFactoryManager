package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.core.BlockPos;

/** Create a playable FE comparison fixture, then release this client to its user. */
@SFMGamePuppet(timeoutTicks = 20 * 60 * 5)
public final class Issue610ManualReadyGamePuppet {
    private Issue610ManualReadyGamePuppet() {}

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.createFreshFlatWorld();
        puppet.startGameTestForUser("issue_610_visual_controls_manual_ready");
        // Feet settle on the template floor at Y=1 after yield. The higher
        // structure orbit falls to surrounding ground and hides the sign faces.
        puppet.positionOrbit(new BlockPos(4, 2, 2), 3.8D, -1.5D, 1.05D);
        puppet.yieldToUser();
    }
}
