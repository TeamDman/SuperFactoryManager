package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;

/** Create a playable FE comparison fixture, then release this client to its user. */
@SFMGamePuppet(timeoutTicks = 20 * 60 * 5)
public final class Issue610ManualReadyGamePuppet {
    private Issue610ManualReadyGamePuppet() {}

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.createFreshFlatWorld();
        puppet.startGameTestForUser("issue_610_visual_controls_manual_ready");
        puppet.positionGameTestOrbit(0);
        puppet.yieldToUser();
    }
}
