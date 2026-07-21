package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Captures the responsive file explorer through its registered palette action. */
@SFMGamePuppet
public final class TitleScreenFileExplorerGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenFileExplorerGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        puppet.openCommandPalette();
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:developer/open_file_explorer",
                SFMScreenMultiplexer.class
        );
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("ready", caption("Read-only fixture with keyboard focus on the programs directory."));

        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_RIGHT);
        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_DOWN);
        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_DOWN);
        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_DOWN);
        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_RIGHT);
        puppet.pressFileExplorerKey(GLFW.GLFW_KEY_DOWN);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("expanded-file-types", caption(
                "Expanded directories, extension-aware styling, and selected Java source."
        ));

        puppet.setFileExplorerSnapshot(SFMFileExplorerSnapshot.loading("Indexing a bounded SFM source..."));
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("loading", caption("Explicit loading state from the source-provider contract."));

        puppet.setFileExplorerSnapshot(SFMFileExplorerSnapshot.ready(List.of()));
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("empty", caption("Explicit empty state for a successfully loaded source."));

        puppet.setFileExplorerSnapshot(SFMFileExplorerSnapshot.error("Fixture source unavailable"));
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("error", caption("Explicit source error state with textual diagnostics."));
    }

    private static Component caption(String text) {
        return Component.literal("SFM File Explorer — ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
