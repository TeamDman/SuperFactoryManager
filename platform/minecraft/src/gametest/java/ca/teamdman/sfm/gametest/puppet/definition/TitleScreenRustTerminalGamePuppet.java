package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Proof that the in-game action can launch and use the Rust-authoritative terminal. */
@SFMGamePuppet
public final class TitleScreenRustTerminalGamePuppet {
    private TitleScreenRustTerminalGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:terminal/start-rust-server");
        puppet.executeTerminal("1..100");
        puppet.waitTicks(30);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_HOME);
        puppet.capture("rust-terminal-powershell-range", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Rust-authoritative PNG terminal range output 1..100.")));
        puppet.pressTerminalKey(GLFW.GLFW_KEY_END);
        puppet.executeTerminal("write-host -foregroundcolor cyan \"hello, world!\"");
        puppet.waitTicks(30);
        puppet.capture("rust-terminal-powershell-cyan", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Rust-authoritative PNG terminal with cyan Write-Host output.")));
    }
}
