package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Proof of the useful terminal path when Rust/Vox is absent. */
@SFMGamePuppet
public final class TitleScreenJavaLocalTerminalGamePuppet {
    private TitleScreenJavaLocalTerminalGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:terminal/open");
        puppet.executeTerminal("pwd");
        puppet.executeTerminal("write /workspace/hello.txt edited in game");
        puppet.executeTerminal("ls /workspace");
        puppet.executeTerminal("cat /workspace/hello.txt");
        for (int i = 0; i < 20; i++) puppet.executeTerminal("echo scroll-row-" + i);
        puppet.scrollTerminal(6);
        puppet.executeTerminal("echo output-arrived-while-scrolled");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_END);
        puppet.capture("java-local-terminal", Component.literal("SFM Terminal ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Java-local service, bounded virtual filesystem, scrollback, and explicit Rust/Vox-unavailable fallback.")));
    }
}
