package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.action.ReleaseReviewJourneyPuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Natural RCS-5 proof of a portable semantic comment, migration decision, and resumable work queue. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE,
        timeoutTicks = 20 * 15 * 60
)
public final class TitleScreenReleaseReviewJourneyGamePuppet {
    private TitleScreenReleaseReviewJourneyGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.releaseReviewJourney(ReleaseReviewJourneyPuppetAction.Operation.PREPARE);

        String stagedPath = ReleaseReviewJourneyPuppetAction.stagedPath().toString();
        palette(puppet, "sfm action invoke sfm:review/session/open " + stagedPath);
        palette(puppet,
                "sfm action invoke sfm:review/session/query/activate #approved intersect 1.19.2 HEAD");
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:explorer/release_review/query "
                        + "#approved intersect 1.19.2 HEAD",
                SFMScreenMultiplexer.class
        );

        // The activated queue selects Café. Resolve and reveal its exact pinned source through the
        // production generic-Explorer path, preview it, then focus the new editor panel.
        puppet.releaseReviewJourney(ReleaseReviewJourneyPuppetAction.Operation.OPEN_CAFE_SOURCE);
        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
        puppet.releaseReviewJourney(ReleaseReviewJourneyPuppetAction.Operation.CHOOSE_SEMANTIC_COMMENT);
        puppet.releaseReviewJourney(ReleaseReviewJourneyPuppetAction.Operation.ASSERT_COMMENT);
        puppet.capture(
                "release-review-semantic-comment",
                caption("A pinned method-body selection creates an ordinary #approved comment through Alt+Enter.")
        );

        palette(puppet,
                "sfm action invoke sfm:panel/open sfm:explorer/release_review/migrations");
        puppet.pressScreenKey(GLFW.GLFW_KEY_DOWN, 0);
        puppet.releaseReviewJourney(ReleaseReviewJourneyPuppetAction.Operation.CHOOSE_RELOCATION);
        puppet.releaseReviewJourney(ReleaseReviewJourneyPuppetAction.Operation.ASSERT_MIGRATION);
        puppet.capture(
                "release-review-migration-decision",
                caption("The migration queue records an explicit relocation decision while unresolved evidence remains visible.")
        );

        // Reopening takes a fresh writer/store over the canonical disk bytes;
        // the named work queue must resume at the sole remaining unit.
        palette(puppet, "sfm action invoke sfm:review/session/open " + stagedPath);
        palette(puppet, "sfm action invoke sfm:review/session/query/activate remaining-work");
        palette(puppet,
                "sfm action invoke sfm:panel/open sfm:explorer/release_review/query remaining-work");
        puppet.releaseReviewJourney(ReleaseReviewJourneyPuppetAction.Operation.ASSERT_REOPENED);
        puppet.capture(
                "release-review-resumed-remaining-work",
                caption("The repository-tracked document reopens in progress at its exact remaining-work cursor.")
        );
        puppet.releaseReviewJourney(ReleaseReviewJourneyPuppetAction.Operation.CLEANUP);
    }

    private static void palette(SFMGamePuppetHelper puppet, String command) {
        puppet.executeCommandPalette(command);
        puppet.executeCommandPalette("sfm action invoke sfm:palette/close");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
    }

    private static Component caption(String text) {
        return Component.literal("SFM Release Review — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
