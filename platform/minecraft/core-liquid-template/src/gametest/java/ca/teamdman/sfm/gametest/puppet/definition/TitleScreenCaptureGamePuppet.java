package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/**
 * Captures the raw title screen without entering a world or title-screen developer tool.
 */
@SFMGamePuppet
public final class TitleScreenCaptureGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenCaptureGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToBePresent(LoadingOverlay.class);
        puppet.capture(
                "loading-overlay",
                Component.literal("Minecraft ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal("loading overlay."))
        );
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.capture(
                "title-screen-fading-in",
                Component.literal("SFM ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("title screen fading in."))
        );
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        puppet.capture(
                "title-screen-settled",
                Component.literal("SFM ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("title screen settled."))
        );
    }
}
