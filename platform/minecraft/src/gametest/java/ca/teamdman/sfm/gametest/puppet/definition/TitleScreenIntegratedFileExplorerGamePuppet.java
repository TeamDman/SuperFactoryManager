package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** End-to-end proof of drop-root, split-once preview, and stable viewer replacement. */
@SFMGamePuppet
public final class TitleScreenIntegratedFileExplorerGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenIntegratedFileExplorerGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:developer/open_file_explorer",
                SFMScreenMultiplexer.class
        );
        puppet.assertFileExplorerWorkspace(1, "", "", "", false);
        puppet.capture("explorer-full", caption("Explorer initially owns the entire workspace."));

        puppet.deliverFileExplorerDropFixture();
        puppet.assertFileExplorerWorkspace(1, "sfm-file-explorer-drop-fixture", "", "", false);
        puppet.capture("dropped-root", caption("Screen.onFilesDrop replaced the bounded read-only root."));

        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_RIGHT);
        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_DOWN);
        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_SPACE);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.assertFileExplorerWorkspace(
                2,
                "sfm-file-explorer-drop-fixture",
                "alpha.txt",
                "alpha content from dropped root",
                true
        );
        puppet.assertFileExplorerPreviewFocus(false);
        puppet.capture("first-preview", caption("Space opened the explorer-owned preview while preserving explorer focus."));

        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_DOWN);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.assertFileExplorerWorkspace(
                3,
                "sfm-file-explorer-drop-fixture",
                "beta.txt",
                "beta replacement content",
                true
        );
        puppet.assertFileExplorerPreviewFocus(true);
        puppet.capture("stacked-preview", caption("Ctrl+Enter opened a new preview entry in the explorer-owned slot and focused it."));
        puppet.closeScreenNaturally();
    }

    private static Component caption(String text) {
        return Component.literal("SFM Explorer Workspace — ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
