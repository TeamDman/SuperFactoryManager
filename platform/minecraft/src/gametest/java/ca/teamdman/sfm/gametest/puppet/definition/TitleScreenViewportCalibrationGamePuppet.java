package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMViewportCalibrationWorkspace;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Preferred-viewport proof that the same diagnostic leaf composes in four workspace shapes. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE)
public final class TitleScreenViewportCalibrationGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenViewportCalibrationGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        capture(puppet, SFMViewportCalibrationWorkspace.Allocation.FULL, "viewport-calibration-full",
                "One calibration leaf receives the complete workspace content bounds.");
        capture(puppet, SFMViewportCalibrationWorkspace.Allocation.HALF, "viewport-calibration-half",
                "Two calibration leaves expose equal half allocations and their local coordinates.");
        capture(puppet, SFMViewportCalibrationWorkspace.Allocation.THIRD, "viewport-calibration-third",
                "The n-ary horizontal layout gives three leaves equal one-third shares.");
        capture(puppet, SFMViewportCalibrationWorkspace.Allocation.NESTED, "viewport-calibration-nested",
                "A horizontal split contains a vertical split without application-specific coordinates.");
    }

    private static void capture(
            SFMGamePuppetHelper puppet,
            SFMViewportCalibrationWorkspace.Allocation allocation,
            String id,
            String text
    ) {
        puppet.openViewportCalibration(allocation);
        puppet.capture(id, Component.literal("SFM ").withStyle(ChatFormatting.GOLD).append(text));
    }
}
