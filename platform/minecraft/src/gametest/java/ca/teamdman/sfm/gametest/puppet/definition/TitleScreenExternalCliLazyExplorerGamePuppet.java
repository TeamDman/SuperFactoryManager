package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.client.gui.screens.LoadingOverlay;

/**
 * Self-orchestrating live proof for typed selections, child relations, the
 * heterogeneous lazy explorer, and the direct external {@code sfm explorer}
 * control surface.
 */
@SFMGamePuppet
public final class TitleScreenExternalCliLazyExplorerGamePuppet {
    private TitleScreenExternalCliLazyExplorerGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.invokeExternalCliLazyExplorer();
        puppet.openCommandPalette();
        puppet.executeCommandPalette(
                "sfm action invoke sfm:explorer/location/edit id(explorer-1) right"
        );
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture(
                "07-explorer-location-editor",
                net.minecraft.network.chat.Component.literal(
                        "The explorer header shows its exact canonical location; the same value opens in the preferred editor through the shared right-panel placement."
                )
        );
        puppet.closeScreenNaturally();
    }
}
