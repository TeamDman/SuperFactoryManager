package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import net.minecraft.client.gui.screens.LoadingOverlay;

/** Opt-in adaptive exploration, not an unattended passing acceptance script. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.REVIEW_READINESS, timeoutTicks = 20 * 60 * 60)
public final class TitleScreenExploratoryReviewGamePuppet {
    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.exploreReviewInteractively();
    }
}
