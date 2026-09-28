package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Captures the bounded read-only view of the isolated puppet instance. */
@SFMGamePuppet
public final class TitleScreenInstanceFileExplorerGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenInstanceFileExplorerGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        puppet.openCommandPalette();
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:developer/open_instance_file_explorer",
                SFMScreenMultiplexer.class
        );
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("instance-root", caption("Bounded root of the isolated runGameTestPreview instance."));

        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_RIGHT);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("instance-expanded", caption(
                "Read-only instance entries; screenshots, logs, saves, and sensitive history are excluded."
        ));
    }

    private static Component caption(String text) {
        return Component.literal("SFM Instance Explorer — ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
