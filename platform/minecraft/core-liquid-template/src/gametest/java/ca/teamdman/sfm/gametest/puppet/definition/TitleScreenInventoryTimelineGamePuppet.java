package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Visual proof of immutable keyframe/time replay in the reusable composable timeline panel. */
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

        captureAtKeyframe(puppet, 0D, "vanilla-geometry", "The 1.19.2 chest, three-row main inventory, and separately spaced hotbar geometry are visible; cyan K and orange T tracks share one immutable state.");
        captureAtTime(puppet, 3D, "unequal-pickup-transition", "Three ticks is halfway through the short six-tick pickup transition; ownership remains in the chest until keyframe 1.");
        captureAtKeyframe(puppet, 1D, "pickup-keyframe", "At semantic keyframe 1 the shared Draw crosshair is anchored to the source slot and the virtual cursor owns the stack.");
        captureAtTime(puppet, 18D, "interpolated-midpoint", "Elapsed tick 18 resolves to keyframe 1.50, halfway through the deliberately longer 24-tick transit.");

        puppet.jumpFalsifiedInventoryKeyframe(1);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("next-keyframe-jump", caption("Next jumps from the sampled 1.50 position to semantic keyframe 2 at tick 30, with the cursor exactly over the destination."));

        captureAtKeyframe(puppet, 3D, "placement-keyframe", "At keyframe 3 and tick 40, the player inventory owns the stack and the virtual cursor is empty.");

        puppet.seekFalsifiedInventoryElapsedTicks(35D);
        puppet.seekFalsifiedInventoryElapsedTicks(18D);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("elapsed-time-reverse-seek", caption("Reverse elapsed-time seek from tick 35 to tick 18 deterministically reconstructs the same keyframe 1.50 midpoint."));

        puppet.seekFalsifiedInventoryElapsedTicks(40D);
        puppet.seekFalsifiedInventoryElapsedTicks(0D);
        puppet.seekFalsifiedInventoryElapsedTicks(6D);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("elapsed-time-random-seek", caption("Random 40→0→6 tick order reconstructs the exact pickup keyframe without playback history."));
    }

    private static void captureAtKeyframe(SFMGamePuppetHelper puppet, double keyframe, String name, String text) {
        puppet.seekFalsifiedInventoryKeyframePosition(keyframe);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture(name, caption(text));
    }

    private static void captureAtTime(SFMGamePuppetHelper puppet, double ticks, String name, String text) {
        puppet.seekFalsifiedInventoryElapsedTicks(ticks);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture(name, caption(text));
    }

    private static Component caption(String text) {
        return Component.literal("SFM Timeline — ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
