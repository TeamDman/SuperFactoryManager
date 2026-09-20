package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;

/** Opt-in file-driven item art and hovered Alt+D proof; not an ambient GameTest. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE, timeoutTicks = 20 * 60 * 10)
public final class InWorldPacketInspectionGamePuppet {
    private InWorldPacketInspectionGamePuppet() {}

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.createFreshFlatWorld();
        puppet.waitTicks(20);
        puppet.explorePacketInspectionInteractively();
    }
}
