package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Captures the contextual command palette over the settled title screen. */
@SFMGamePuppet
public final class TitleScreenCommandPaletteGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenCommandPaletteGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        puppet.openCommandPalette();
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture(
                "command-palette",
                Component.literal("SFM ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("command palette on the title screen."))
        );
    }
}
