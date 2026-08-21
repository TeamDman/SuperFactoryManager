package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;

/** Self-orchestrating natural proof for the reusable non-pausing overlay slice. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO,
        timeoutTicks = 20 * 20 * 60
)
public final class InWorldHistoryGraphOverlayGamePuppet {
    private InWorldHistoryGraphOverlayGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.createFreshFlatWorld();
        puppet.waitTicks(40);
        puppet.exerciseHistoryGraphOverlay();
    }
}
