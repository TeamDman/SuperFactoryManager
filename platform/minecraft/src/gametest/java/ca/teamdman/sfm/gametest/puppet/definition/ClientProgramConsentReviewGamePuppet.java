package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;

/** File-driven review UI acceptance, deliberately separate from the screen-free runtime tests. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE, timeoutTicks = 20 * 60 * 15)
public final class ClientProgramConsentReviewGamePuppet {
    private ClientProgramConsentReviewGamePuppet() {}
    public static void run(SFMGamePuppetHelper puppet) {
        InWorldTouchDisplayExploratoryGamePuppet.declare(puppet, true);
    }
}
