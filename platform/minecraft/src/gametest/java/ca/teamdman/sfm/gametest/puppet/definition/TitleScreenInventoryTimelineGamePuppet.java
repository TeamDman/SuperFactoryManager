package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Visual proof of random-access item movement in the reusable composable timeline panel. */
@SFMGamePuppet
public final class TitleScreenInventoryTimelineGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenInventoryTimelineGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        puppet.openFalsifiedInventoryTimeline();
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        captureAt(puppet, 0, "first", "One cobblestone begins in the falsified chest.");
        captureAt(puppet, 1, "pickup", "The chest is empty and the virtual cursor holds the copied stack.");
        captureAt(puppet, 2, "transit", "A deterministic transit frame moves the held stack toward the player inventory.");
        captureAt(puppet, 4, "pre-place", "The virtual cursor reaches the destination slot before placement.");
        captureAt(puppet, 5, "final", "The copied stack is now in the player inventory and the cursor is empty.");

        puppet.dragFalsifiedInventoryTimeline(5, 2);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("backward-drag", caption("Dragging the MPV-like track backwards reconstructs the transit frame."));

        // Deliberately non-monotonic: the same t=1 view must not depend on playback history.
        puppet.seekFalsifiedInventoryTimeline(4);
        puppet.seekFalsifiedInventoryTimeline(0);
        captureAt(puppet, 1, "random-seek-pickup", "Random seek order reconstructs the same pickup state independently.");
    }

    private static void captureAt(SFMGamePuppetHelper puppet, int timestep, String name, String text) {
        puppet.seekFalsifiedInventoryTimeline(timestep);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture(name, caption(text));
    }

    private static Component caption(String text) {
        return Component.literal("SFM Timeline — ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
