package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Visible end-to-end witness for the external live-game control CLI. */
@SFMGamePuppet
public final class TitleScreenExternalCliSizeDisplayGamePuppet {
    private TitleScreenExternalCliSizeDisplayGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.waitForExternalSizeDisplay();
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.assertWorkspaceState(1, 1, 1, "size-display: logical size", -1);
        puppet.capture(
                "external-cli-size-display",
                Component.literal("SFM ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(
                                "The external sfm.exe selected this game and opened sfm:size_display through the registered action executor."
                        ))
        );
        puppet.closeScreenNaturally();
    }
}
