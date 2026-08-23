package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.action.SFMReleaseReviewAction;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.action.RealReleaseReviewJourneyPuppetAction;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * First JVM of the RCS-S1 proof: review one real Java hunk through ordinary
 * explorer, editor, contextual palette, action, query, and persistence paths.
 */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE,
        timeoutTicks = 20 * 30 * 60
)
public final class TitleScreenRealReleaseReviewStageGamePuppet {
    private TitleScreenRealReleaseReviewStageGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.realReleaseReviewJourney(RealReleaseReviewJourneyPuppetAction.Operation.PREPARE_STAGE);

        String stagedPath = SFMReleaseReviewAction.greedyPathArgument(
                RealReleaseReviewJourneyPuppetAction.stagedPath());
        String targetUnit = StringArgumentType.escapeIfRequired(
                RealReleaseReviewJourneyPuppetAction.targetUnitId());
        palette(puppet, "sfm action invoke sfm:review/session/open " + stagedPath);
        palette(puppet,
                "sfm action invoke sfm:review/session/query/activate remaining intersect 1.19.2 HEAD");
        palette(puppet, "sfm action invoke sfm:review/session/work/select " + targetUnit);

        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:explorer/release_review/query "
                        + "remaining intersect 1.19.2 HEAD",
                SFMScreenMultiplexer.class
        );

        // Expand the exact selected unit, choose its candidate-side leaf, open
        // it naturally through the explorer, and focus the resulting editor.
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_DOWN, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_DOWN, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_SPACE, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
        puppet.realReleaseReviewJourney(
                RealReleaseReviewJourneyPuppetAction.Operation.CHOOSE_STRUCTURAL_NEEDS_CHANGE);

        // Exercise the ordinary palette query surface before asserting the
        // equivalent persisted sets in the staged document.
        palette(puppet, "sfm action invoke sfm:review/session/query "
                + "#approved intersect 1.19.2 HEAD");
        palette(puppet, "sfm action invoke sfm:review/session/query "
                + "effective(#approved) intersect 1.19.2 HEAD");
        palette(puppet, "sfm action invoke sfm:review/session/query "
                + "remaining intersect 1.19.2 HEAD");
        palette(puppet, "sfm action invoke sfm:review/session/query "
                + "blocking intersect 1.19.2 HEAD");
        puppet.realReleaseReviewJourney(RealReleaseReviewJourneyPuppetAction.Operation.ASSERT_STAGED);
        puppet.capture(
                "real-release-review-structural-blocker",
                caption("A real candidate hunk now carries a Java-structural #needs-change blocker.")
        );
    }

    private static void palette(SFMGamePuppetHelper puppet, String command) {
        puppet.executeCommandPalette(command);
        puppet.executeCommandPalette("sfm action invoke sfm:palette/close");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
    }

    private static Component caption(String text) {
        return Component.literal("SFM Real Release Review — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
