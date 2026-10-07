package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import net.minecraft.client.gui.screens.LoadingOverlay;

/** Requires the stage puppet in an earlier JVM; local recovery is removed before reopen. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE, timeoutTicks = 20 * 20 * 60)
public final class TitleScreenExactReleaseReviewResumeGamePuppet {
    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.exactReleaseReviewJourney(true);
    }
}
