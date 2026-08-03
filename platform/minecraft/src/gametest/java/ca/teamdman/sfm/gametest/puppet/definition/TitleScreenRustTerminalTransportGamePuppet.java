package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Focused V-4.2a proof for transport selection, session retention, and independent panels. */
@SFMGamePuppet
public final class TitleScreenRustTerminalTransportGamePuppet {
    private TitleScreenRustTerminalTransportGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:terminal/server/start");
        puppet.waitTicks(20);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open sfm:terminal");
        puppet.waitTicks(80);
        puppet.capture("transport-full-png", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("V3 full-png is the initial panel-local transport.")));

        // Paste exercises the same Rust text-input contract in one bounded
        // request. Sending one automation RPC per character would
        // intentionally provoke one full frame per character in full modes
        // and measure puppet queue amplification instead of transport switching.
        puppet.pasteTerminalText("$global:SfmTransportWitness='session-preserved'; Write-Output $global:SfmTransportWitness");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ENTER);
        puppet.waitTicks(30);
        puppet.writeTerminalContent("transport-full-png", "session-preserved", null);
        puppet.assertTerminalPushEvidence("transport-full-png", false);

        // The third Tab focuses the visible selector; Enter/Down/Enter chooses
        // full-raw-rgba, and the final Tab returns focus to terminal input.
        puppet.pressTerminalKey(GLFW.GLFW_KEY_TAB);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_TAB);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_TAB);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ENTER);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_DOWN);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ENTER);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_TAB);
        puppet.waitTicks(60);
        puppet.pasteTerminalText("Write-Output $global:SfmTransportWitness");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ENTER);
        puppet.waitTicks(30);
        puppet.writeTerminalContent("transport-full-raw-rgba", "session-preserved", null);
        puppet.assertTerminalPushEvidence("transport-full-raw-rgba", false);
        puppet.capture("transport-full-raw-rgba", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Keyboard dropdown switched to full RGBA without replacing the PTY.")));

        puppet.openCommandPalette();
        puppet.executeCommandPalette(
                "sfm action invoke sfm:terminal/transport/set dirty-raw-rgba");
        puppet.waitTicks(60);
        puppet.executeTerminal("1..100");
        puppet.waitTicks(40);
        puppet.writeTerminalContent("transport-dirty-raw-rgba", "100", null);
        puppet.assertTerminalPushEvidence("transport-dirty-raw-rgba", false);
        puppet.capture("transport-dirty-raw-rgba", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Focused action selected sequence-preserving dirty RGBA.")));

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:terminal/transport/set full-png");
        puppet.waitTicks(60);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open/right sfm:terminal");
        puppet.waitTicks(80);
        puppet.openCommandPalette();
        puppet.executeCommandPalette(
                "sfm action invoke sfm:terminal/transport/set full-raw-rgba");
        puppet.waitTicks(60);
        puppet.executeTerminal("Write-Output independent-right-panel");
        puppet.waitTicks(30);
        puppet.writeTerminalContent("transport-independent-right", "independent-right-panel", null);
        puppet.assertTerminalPushEvidence("transport-independent-right", false);
        puppet.capture("transport-independent-panels", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Left full-png and right full-raw-rgba panels retain independent sessions.")));
    }
}
