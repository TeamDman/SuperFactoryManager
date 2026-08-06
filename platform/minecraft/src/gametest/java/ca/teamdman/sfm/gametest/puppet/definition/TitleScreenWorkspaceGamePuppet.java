package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Live proof of the palette -> Brigadier -> client action -> workspace path. */
@SFMGamePuppet
public final class TitleScreenWorkspaceGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;
    private static final String OPEN_COMMAND =
            "sfm action invoke sfm:panel/open/right sfm:test_screen ";

    private TitleScreenWorkspaceGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        puppet.openCommandPalette();
        puppet.executeCommandPalette(OPEN_COMMAND + "first workspace opening");
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.assertWorkspaceState(2, 2, 1, "first workspace opening", -1);
        puppet.capture("workspace-right-focused", caption("Opened through the command palette; right panel focused."));

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open sfm:test_screen stacked workspace opening");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.assertWorkspaceState(3, 2, 2, "stacked workspace opening", -1);
        puppet.pressScreenKey(GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_CONTROL);
        puppet.assertWorkspaceState(3, 2, 2, "first workspace opening", -1);
        puppet.pressScreenKey(GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
        puppet.assertWorkspaceState(3, 2, 2, "stacked workspace opening", -1);

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/scale/set 2");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.assertWorkspaceState(3, 2, 2, "stacked workspace opening", 2);
        puppet.pressScreenKey(
                GLFW.GLFW_KEY_EQUAL,
                GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.assertWorkspaceState(3, 2, 2, "stacked workspace opening", 3);
        puppet.pressScreenKey(GLFW.GLFW_KEY_0, GLFW.GLFW_MOD_CONTROL);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.assertWorkspaceState(3, 2, 2, "stacked workspace opening", -1);
        puppet.pressScreenKey(GLFW.GLFW_KEY_F3, 0);
        puppet.assertActionChoice(java.util.List.of(
                "sfm action invoke sfm:panel/open sfm:size_display",
                "sfm action invoke sfm:panel/open/left sfm:size_display",
                "sfm action invoke sfm:panel/open/right sfm:size_display",
                "sfm action invoke sfm:panel/open/above sfm:size_display",
                "sfm action invoke sfm:panel/open/below sfm:size_display"
        ));
        puppet.capture("workspace-contextual-diagnostics", caption(
                "F3 is a registered contextual action and panel scale defaults use the same input path."
        ));
        puppet.closeScreenNaturally();
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/move/left");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.assertWorkspaceState(3, 2, 2, "stacked workspace opening", -1);
        puppet.capture("workspace-stack-navigation", caption(
                "Panel stack traversal, per-entry scale, and identity-preserving move are observable in one workspace."
        ));

        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        puppet.assertWorkspaceState(3, 2, 2, "stacked workspace opening", -1);
        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
        puppet.assertWorkspaceState(3, 2, 1, "first workspace opening", -1);

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
        puppet.pressScreenKey(
                GLFW.GLFW_KEY_W,
                GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.assertWorkspaceState(1, 1, 1, "Title Screen", -1);
        puppet.capture("workspace-contextual-close", caption(
                "Ctrl+Shift+W closes exactly the focused panel through its registered default action."
        ));
        puppet.closeScreenNaturally();
    }

    private static Component caption(String text) {
        return Component.literal("SFM ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text));
    }
}
