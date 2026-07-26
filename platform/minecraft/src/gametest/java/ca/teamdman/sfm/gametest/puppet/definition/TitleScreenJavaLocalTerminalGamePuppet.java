package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Proof of the useful terminal path when Rust/Vox is absent. */
@SFMGamePuppet
public final class TitleScreenJavaLocalTerminalGamePuppet {
    private TitleScreenJavaLocalTerminalGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.openTerminal();
        puppet.executeTerminal("pwd");
        puppet.executeTerminal("write /workspace/hello.txt edited in game");
        puppet.executeTerminal("ls /workspace");
        puppet.executeTerminal("cat /workspace/hello.txt");
        puppet.capture("java-local-terminal", Component.literal("SFM Terminal ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Java-local service, bounded virtual filesystem, and explicit Rust/Vox-unavailable fallback.")));
    }
}
