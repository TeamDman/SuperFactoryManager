package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Natural proof for global opening shortcuts and explicit palette focus actions. */
@SFMGamePuppet
public final class TitleScreenPaletteFocusAndLifecycleGamePuppet {
    private TitleScreenPaletteFocusAndLifecycleGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);

        puppet.pressScreenKey(GLFW.GLFW_KEY_D, GLFW.GLFW_MOD_ALT);
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.assertWorkspaceState(1, 1, 1, "Text Editor v3", -1);
        puppet.capture("global-alt-d", caption(
                "Alt+D opens an untitled editor directly from the title screen."));
        puppet.closeScreenNaturally();
        puppet.waitForScreen(TitleScreen.class);

        puppet.openCommandPalette();
        puppet.pressScreenKey(GLFW.GLFW_KEY_D, GLFW.GLFW_MOD_ALT);
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.assertWorkspaceState(1, 1, 1, "Text Editor v3", -1);
        puppet.closeScreenNaturally();
        puppet.waitForScreen(TitleScreen.class);

        puppet.openCommandPalette();
        puppet.setCommandPaletteInput(
                "sfm action invoke sfm:panel/open sfm:test_screen palette explicit execute focus");
        puppet.waitTicks(SFMGamePuppetHelper.COMMAND_PALETTE_OBSERVATION_TICKS);
        puppet.pressScreenKey(GLFW.GLFW_KEY_E, GLFW.GLFW_MOD_ALT);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.assertWorkspaceState(1, 1, 1, "palette explicit execute focus", -1);
        puppet.capture("palette-alt-e-execute", caption(
                "Alt+E focuses Execute without borrowing Tab from command completion."));
        puppet.closeScreenNaturally();
        puppet.waitForScreen(TitleScreen.class);

        puppet.openCommandPalette();
        puppet.pressScreenKey(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_ALT);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.waitForScreen(TitleScreen.class);

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:palette/close");
        puppet.waitForScreen(TitleScreen.class);

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:screen/diagnostics");
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.assertWorkspaceState(1, 1, 1, "Screen Diagnostics", -1);
        puppet.capture("screen-diagnostics", caption(
                "Screen diagnostics opens as a read-only document through the ordinary panel path."));
        puppet.closeScreenNaturally();
        puppet.waitForScreen(TitleScreen.class);
    }

    private static Component caption(String text) {
        return Component.literal("SFM palette lifecycle: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text));
    }
}
