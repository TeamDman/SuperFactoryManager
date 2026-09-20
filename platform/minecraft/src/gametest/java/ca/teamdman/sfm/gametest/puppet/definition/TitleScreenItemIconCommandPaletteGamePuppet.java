package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

@SFMGamePuppet
public final class TitleScreenItemIconCommandPaletteGamePuppet {
    private TitleScreenItemIconCommandPaletteGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.openCommandPalette();
        puppet.setCommandPaletteInput("sfm action invoke sfm:panel/open sfm:explorer");
        puppet.capture("generic-explorer-action", caption(
                "The generic explorer scene is the sole palette entry point while suggestion rows remain stable and legible."
        ));
        puppet.prepareIncompleteCommandPaletteInput(
                "sfm action invoke sfm:echo",
                "sfm action invoke sfm:echo "
        );
        puppet.capture("echo-required-separator", caption(
                "Activating incomplete Echo inserts exactly one ASCII space; the caret follows it and Execute stays disabled."
        ));
    }

    private static Component caption(String text) {
        return Component.literal("SFM Command Palette — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
