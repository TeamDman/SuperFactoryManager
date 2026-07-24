package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** Live proof of the palette -> Brigadier -> client action -> workspace path. */
@SFMGamePuppet
public final class TitleScreenWorkspaceGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;
    private static final String OPEN_COMMAND =
            "sfm action invoke sfm:workspace/open_to_side sfm:test_screen ";

    private TitleScreenWorkspaceGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        puppet.openCommandPalette();
        puppet.executeCommandPalette(OPEN_COMMAND + "first workspace opening");
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.capture("workspace-right-focused", caption("Opened through the command palette; right panel focused."));

        puppet.clickWorkspacePanel(0);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("workspace-left-focused", caption("Mouse click moved focus to the parked-screen panel."));

        puppet.closeScreenNaturally();
        puppet.waitForScreen(TitleScreen.class);
        puppet.capture("workspace-closed-back", caption("Workspace close restored the originating title screen."));

        puppet.openCommandPalette();
        puppet.executeCommandPalette(OPEN_COMMAND + "second workspace opening");
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.capture("workspace-reopened", caption("The same registered action reopened a fresh workspace."));
        puppet.closeScreenNaturally();
    }

    private static Component caption(String text) {
        return Component.literal("SFM ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text));
    }
}
