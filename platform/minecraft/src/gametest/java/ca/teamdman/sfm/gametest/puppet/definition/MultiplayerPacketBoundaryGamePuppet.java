package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;

/** Explicitly selected normal remote client; never part of ambient GameTest discovery. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE,
        timeoutTicks = 20 * 60 * 15)
public final class MultiplayerPacketBoundaryGamePuppet {
    private MultiplayerPacketBoundaryGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.remoteMultiplayerPacketBoundary();
    }
}
