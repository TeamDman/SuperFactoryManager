package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMFileExplorerLargeFixtureSource;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Captures virtualized navigation across a deterministic 1,002-file tree. */
@SFMGamePuppet
public final class TitleScreenLargeFileExplorerGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenLargeFileExplorerGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        puppet.openFileExplorer(new SFMFileExplorerLargeFixtureSource());
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("thousand-groups", caption("Collapsed 0000-0999 and 1000-1999 directory groups."));

        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_RIGHT);
        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_DOWN);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("first-entry", caption("Expanded first group with 0000.txt selected."));

        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_END);
        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_RIGHT);
        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_DOWN);
        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_DOWN);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("thousand-boundary", caption(
                "Virtualized boundary view containing 0999.txt, 1000.txt, and 1001.txt."
        ));
    }

    private static Component caption(String text) {
        return Component.literal("SFM Large Hierarchy — ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
