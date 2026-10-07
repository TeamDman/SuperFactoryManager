package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Captures the command palette displaying output from the echo action. */
@SFMGamePuppet
public final class TitleScreenCommandPaletteEchoGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenCommandPaletteEchoGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        puppet.openCommandPalette();
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.executeCommandPalette(
                "sfm action invoke sfm:echo Echo output rendered by the SFM console widget"
        );
        puppet.capture(
                "command-palette-echo",
                Component.literal("SFM ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("command palette echo output."))
        );
    }
}
