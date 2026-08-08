package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Live witness for MRU ordering, read-only history opening, and true clear. */
@SFMGamePuppet
public final class TitleScreenCommandPaletteHistoryGamePuppet {
    private TitleScreenCommandPaletteHistoryGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:palette/history/clear");

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:echo history-first");
        puppet.executeCommandPalette("sfm action invoke sfm:echo history-second");
        puppet.setCommandPaletteInput("sfm action invoke ");
        puppet.capture("command-palette-history-mru", caption(
                "The newest successful command is first, followed by the older history entry."
        ));

        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:palette/history/open",
                SFMScreenMultiplexer.class
        );
        puppet.assertWorkspaceState(1, 1, 1, "Command History (read-only)", -1);
        puppet.capture("command-palette-history-read-only", caption(
                "Command history opens as an immutable Text Editor v3 panel."
        ));
        puppet.closeScreenNaturally();

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:palette/history/clear");
        puppet.openCommandPalette();
        puppet.capture("command-palette-history-cleared", caption(
                "Clear removes history; the blank palette falls back to ordinary actions."
        ));
    }

    private static Component caption(String text) {
        return Component.literal("SFM Palette History: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
