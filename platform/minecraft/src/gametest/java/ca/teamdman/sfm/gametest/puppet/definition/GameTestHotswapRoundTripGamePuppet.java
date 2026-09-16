package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.properties.SFMProperties;
import net.minecraft.network.chat.Component;

/** Opt-in three-run experiment, with file-controlled pauses to apply a reload between runs. */
@SFMGamePuppet(timeoutTicks = 20 * 60 * 60)
public final class GameTestHotswapRoundTripGamePuppet {
    public static void run(SFMGamePuppetHelper puppet) {
        String testName = SFMProperties.requiredGamePuppetGameTest().value();
        puppet.createFreshFlatWorld();
        for (int phase = 0; phase < 3; phase++) {
            puppet.runGameTest(testName);
            puppet.captureGameTestOrbit("hotswap-phase-" + phase, 1,
                    Component.literal("Completed fixture after reload phase " + phase));
            if (phase < 2) puppet.exploreReviewInteractively();
        }
    }
}
