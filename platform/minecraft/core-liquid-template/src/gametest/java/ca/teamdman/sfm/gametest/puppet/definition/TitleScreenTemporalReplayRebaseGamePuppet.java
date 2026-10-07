package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.action.AssertTemporalReplayRebasePuppetAction;
import ca.teamdman.sfm.gametest.puppet.action.InvokeTemporalReplayPuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Natural X4 proof of raw input provenance, exact replay, and semantic rebase. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO,
        timeoutTicks = 20 * 15 * 60
)
public final class TitleScreenTemporalReplayRebaseGamePuppet {
    private TitleScreenTemporalReplayRebaseGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);

        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:chamber/temporal-decimal-numbering",
                SFMScreenMultiplexer.class
        );
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open/right sfm:episode/history",
                SFMScreenMultiplexer.class
        );
        puppet.waitTicks(20);
        checkpoint(
                puppet,
                AssertTemporalReplayRebasePuppetAction.Stage.INITIAL,
                "temporal-replay-initial",
                "The immutable two-item source begins with an empty causal archive."
        );

        // Focus the chamber and use the same physical screen-key seam as a user.
        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(
                GLFW.GLFW_KEY_J,
                GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT
        );
        checkpoint(
                puppet,
                AssertTemporalReplayRebasePuppetAction.Stage.DYNAMIC_SELECTED,
                "temporal-replay-dynamic-selected",
                "Ctrl+Alt+J records its raw key, effective binding, and two-region semantic witness."
        );

        palette(puppet, "sfm action invoke sfm:text/selection/replace/decimal_sequence focused");
        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
        checkpoint(
                puppet,
                AssertTemporalReplayRebasePuppetAction.Stage.NUMBERED_TWO,
                "temporal-replay-numbered-two",
                "The registered replacement creates exact 1/2 history and exposes its complete causal chain."
        );

        // The selector-only action deliberately presents the constrained choice
        // surface; the puppet then selects the runtime-addressed source/source replay.
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:episode/replay/exact focused");
        puppet.clickTemporalExactReplayChoice();
        puppet.executeCommandPalette("sfm action invoke sfm:palette/close");
        checkpoint(
                puppet,
                AssertTemporalReplayRebasePuppetAction.Stage.EXACT_REPLAYED,
                "temporal-replay-exact",
                "Exact replay verifies the retained two-item route without fabricating keystrokes."
        );

        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_HOME, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_END, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.typeScreenText("- apricots");
        checkpoint(
                puppet,
                AssertTemporalReplayRebasePuppetAction.Stage.THREE_ITEM_PARENT,
                "temporal-replay-three-item-parent",
                "Undo and ordinary editor input create a changed historical parent while retaining 1/2."
        );

        replay(
                puppet,
                SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY,
                InvokeTemporalReplayPuppetAction.Target.CURRENT_HEAD
        );
        checkpoint(
                puppet,
                AssertTemporalReplayRebasePuppetAction.Stage.EXACT_MISMATCH,
                "temporal-replay-exact-mismatch",
                "Frozen-witness exact replay rejects the changed prefix with no partial mutation."
        );

        replay(
                puppet,
                SFMTemporalReplayArchive.ReplayMode.SEMANTIC_REBASE,
                InvokeTemporalReplayPuppetAction.Target.CURRENT_HEAD
        );
        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
        checkpoint(
                puppet,
                AssertTemporalReplayRebasePuppetAction.Stage.SEMANTIC_REBASED,
                "temporal-replay-semantic-rebase",
                "Semantic rebase re-evaluates intent into 1/2/3 and retains both immutable siblings."
        );
    }

    private static void palette(SFMGamePuppetHelper puppet, String command) {
        puppet.executeCommandPalette(command);
        puppet.executeCommandPalette("sfm action invoke sfm:palette/close");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
    }

    private static void replay(
            SFMGamePuppetHelper puppet,
            SFMTemporalReplayArchive.ReplayMode mode,
            InvokeTemporalReplayPuppetAction.Target target
    ) {
        puppet.openCommandPalette();
        puppet.invokeTemporalReplay(mode, target);
        puppet.executeCommandPalette("sfm action invoke sfm:palette/close");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
    }

    private static void checkpoint(
            SFMGamePuppetHelper puppet,
            AssertTemporalReplayRebasePuppetAction.Stage stage,
            String artifactAndCapture,
            String caption
    ) {
        puppet.assertTemporalReplayRebase(stage, artifactAndCapture, artifactAndCapture);
        puppet.waitTicks(20);
        puppet.capture(artifactAndCapture, Component.literal("Temporal replay: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(caption).withStyle(ChatFormatting.BLACK)));
    }
}
