package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.action.SFMReleaseReviewAction;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.action.RealReleaseReviewJourneyPuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/**
 * Second JVM of the RCS-S1 proof: delete machine-local state, reopen only the
 * tracked bytes, and prove the exact comment, selector, work cursor, and status.
 */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE,
        timeoutTicks = 20 * 20 * 60
)
public final class TitleScreenRealReleaseReviewResumeGamePuppet {
    private TitleScreenRealReleaseReviewResumeGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.realReleaseReviewJourney(RealReleaseReviewJourneyPuppetAction.Operation.PREPARE_RESUME);

        String stagedPath = SFMReleaseReviewAction.greedyPathArgument(
                RealReleaseReviewJourneyPuppetAction.stagedPath());
        palette(puppet, "sfm action invoke sfm:review/session/open " + stagedPath);
        palette(puppet, "sfm action invoke sfm:panel/open sfm:explorer/release_review/status");
        puppet.realReleaseReviewJourney(RealReleaseReviewJourneyPuppetAction.Operation.ASSERT_RESUMED);
        puppet.capture(
                "real-release-review-resumed-status",
                caption("A fresh JVM rebuilt the exact in-progress status from tracked review bytes alone.")
        );
        puppet.realReleaseReviewJourney(RealReleaseReviewJourneyPuppetAction.Operation.CLEANUP);
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
