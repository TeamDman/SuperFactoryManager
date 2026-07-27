package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
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
        if (SFMTerminalServiceFactory.voxConfigured()) {
            puppet.executeTerminal("1..100");
            puppet.pressTerminalKey(GLFW.GLFW_KEY_HOME);
            puppet.capture("vox-terminal-powershell-range", Component.literal("SFM Terminal ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Rust-authoritative PNG terminal range output 1..100.")));
            puppet.pressTerminalKey(GLFW.GLFW_KEY_END);
            puppet.executeTerminal("write-host -foregroundcolor cyan \"hello, world!\"");
            puppet.capture("vox-terminal-powershell-cyan", Component.literal("SFM Terminal ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Rust-authoritative PNG terminal with cyan Write-Host output.")));
            return;
        }
        puppet.executeTerminal("pwd");
        puppet.executeTerminal("write /workspace/hello.txt edited in game");
        puppet.executeTerminal("ls /workspace");
        puppet.executeTerminal("cat /workspace/hello.txt");
        puppet.executeTerminal("1..100");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_HOME);
        puppet.capture("java-local-terminal-powershell-range", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Java-local PowerShell-compatible bounded range output 1..100.")));
        puppet.pressTerminalKey(GLFW.GLFW_KEY_END);
        puppet.executeTerminal("write-host -foregroundcolor cyan \"hello, world!\"");
        for (int i = 0; i < 20; i++) puppet.executeTerminal("echo scroll-row-" + i);
        puppet.scrollTerminal(6);
        puppet.executeTerminal("echo output-arrived-while-scrolled");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_END);
        puppet.capture("java-local-terminal-powershell-cyan", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Java-local service, 1..100 output, cyan Write-Host, bounded scrollback, and explicit Rust/Vox-unavailable fallback.")));
    }
}
