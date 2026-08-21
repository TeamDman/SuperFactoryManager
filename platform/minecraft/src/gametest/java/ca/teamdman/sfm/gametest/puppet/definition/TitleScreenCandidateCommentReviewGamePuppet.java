package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.action.AssertCandidateCommentReviewPuppetAction;
import ca.teamdman.sfm.gametest.puppet.action.CandidateCommentSessionPuppetAction;
import ca.teamdman.sfm.gametest.puppet.action.CandidateHistoryStatusFixturePuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Natural X2 proof of persistent candidate comments, promotion, and exact-frame navigation. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO,
        timeoutTicks = 20 * 15 * 60
)
public final class TitleScreenCandidateCommentReviewGamePuppet {
    private TitleScreenCandidateCommentReviewGamePuppet() {
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
        palette(puppet, "sfm action invoke sfm:episode/trajectory/plan focused");
        palette(puppet, "sfm action invoke sfm:panel/open sfm:episode/candidate-history focused");
        puppet.waitTicks(20);
        puppet.resetCandidateCommentSession(CandidateCommentSessionPuppetAction.SessionRole.MAIN);

        palette(puppet, "sfm action invoke sfm:review/comment/create/candidate/route focused route-retained");
        palette(puppet, "sfm action invoke sfm:review/comment/edit focused candidate-human-1 route-retained-edited");
        checkpoint(
                puppet,
                AssertCandidateCommentReviewPuppetAction.Stage.ROUTE_LABEL,
                "candidate-comment-route-label",
                "candidate-comment-route-label",
                "The route comment is visibly labelled and ordinary editing preserves its pinned frame."
        );

        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        palette(puppet,
                "sfm action invoke sfm:review/comment/create/candidate/action focused action-select-hyphens");
        checkpoint(
                puppet,
                AssertCandidateCommentReviewPuppetAction.Stage.ACTION_LABEL,
                "candidate-comment-action-label",
                "candidate-comment-action-label",
                "The action comment visibly carries the candidate action label on frame one."
        );

        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        palette(puppet, "sfm action invoke sfm:review/comment/create/candidate/state focused state-numbered-two");
        palette(puppet,
                "sfm action invoke sfm:review/comment/create/candidate/glyph focused 3 9 glyph-apples-exact");
        palette(puppet,
                "sfm action invoke sfm:review/comment/create/candidate/glyph focused 13 20 glyph-bananas-divergence");
        checkpoint(
                puppet,
                AssertCandidateCommentReviewPuppetAction.Stage.CREATED_ALL,
                "candidate-comment-created-all",
                "candidate-comment-created-all",
                "Route, action, state, and exact UTF-8 glyph targets are persistent candidate discussion."
        );

        palette(puppet, "sfm action invoke sfm:episode/trajectory/run focused 32");
        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_HOME, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_END, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.typeScreenText("- apricots");
        palette(puppet, "sfm action invoke sfm:episode/trajectory/replan focused");
        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
        checkpoint(
                puppet,
                AssertCandidateCommentReviewPuppetAction.Stage.RETAINED_AFTER_REPLAN,
                "candidate-comment-retained-after-replan",
                "candidate-comment-retained-after-replan",
                "The three-item replan retains every old comment's original plan, route, frame, and hash."
        );

        palette(puppet, "sfm action invoke sfm:review/comment/navigate focused candidate-human-1");
        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        rewindNaturalEdits(puppet, 12);
        puppet.assertCandidateCommentReview(
                AssertCandidateCommentReviewPuppetAction.Stage.OLD_ROUTE_START_RESTORED,
                "candidate-comment-old-route-start-restored"
        );
        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
        puppet.executeCommandPalette("sfm action invoke sfm:episode/trajectory/route/select focused");
        puppet.clickCandidateCommentRouteChoice(AssertCandidateCommentReviewPuppetAction.ROUTE_COMMENT);
        // A constrained choice closes back to the palette that opened it. Close
        // that parent palette before issuing the next ordinary workspace action.
        puppet.executeCommandPalette("sfm action invoke sfm:palette/close");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        palette(puppet, "sfm action invoke sfm:episode/trajectory/run focused 32");
        palette(puppet,
                "sfm action invoke sfm:review/comment/promote/exact focused candidate-human-4 candidate-comment-exact");
        checkpoint(
                puppet,
                AssertCandidateCommentReviewPuppetAction.Stage.EXACT_PROMOTED,
                "candidate-comment-exact-promotion",
                "candidate-comment-exact-promotion",
                "Exact retained-route execution creates one explicit promotion link and no approval."
        );

        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_HOME, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_END, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.typeScreenText("- cherries");
        palette(puppet, "sfm action invoke sfm:episode/trajectory/replan focused");
        palette(puppet, "sfm action invoke sfm:episode/trajectory/run focused 32");
        // Promotion and migration resolve `focused` through the pinned
        // Candidate History panel, while the natural edit/replan happens in
        // the chamber panel.
        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
        palette(puppet,
                "sfm action invoke sfm:review/comment/promote/exact focused candidate-human-5 candidate-comment-divergent");
        checkpoint(
                puppet,
                AssertCandidateCommentReviewPuppetAction.Stage.DIVERGENCE_REJECTED,
                "candidate-comment-divergence-rejected",
                "candidate-comment-divergence-rejected",
                "Divergent execution creates no implicit link and transfers no approval."
        );
        palette(puppet,
                "sfm action invoke sfm:review/comment/migrate/witnessed focused candidate-human-5 25 32 "
                        + "candidate-comment-witnessed same-list-item-bananas-after-three-item-divergence");
        checkpoint(
                puppet,
                AssertCandidateCommentReviewPuppetAction.Stage.WITNESSED_MIGRATED,
                "candidate-comment-witnessed-migration",
                "candidate-comment-witnessed-migration",
                "Explicit witnessed migration links divergent bytes, retains the candidate, and grants no approval."
        );

        puppet.reloadCandidateCommentSession(CandidateCommentSessionPuppetAction.SessionRole.MAIN);
        navigateMain(puppet, AssertCandidateCommentReviewPuppetAction.ROUTE_COMMENT, "route");
        navigateMain(puppet, AssertCandidateCommentReviewPuppetAction.ACTION_COMMENT, "action");
        navigateMain(puppet, AssertCandidateCommentReviewPuppetAction.STATE_COMMENT, "state");
        navigateMain(puppet, AssertCandidateCommentReviewPuppetAction.EXACT_GLYPH_COMMENT, "glyph-exact");
        navigateMain(puppet, AssertCandidateCommentReviewPuppetAction.DIVERGENT_GLYPH_COMMENT, "glyph-divergent");

        puppet.registerCandidateHistoryStatusFixture();
        palette(puppet, "sfm action invoke sfm:panel/open sfm:episode/candidate-history focused");
        puppet.waitTicks(20);
        puppet.resetCandidateCommentSession(
                CandidateCommentSessionPuppetAction.SessionRole.UNAVAILABLE_FIXTURE);
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        puppet.assertCandidateHistoryStatus(
                CandidateHistoryStatusFixturePuppetAction.Stage.UNAVAILABLE,
                "candidate-comment-unavailable-frame"
        );
        palette(puppet,
                "sfm action invoke sfm:review/comment/create/candidate/route focused unavailable-route");
        palette(puppet,
                "sfm action invoke sfm:review/comment/create/candidate/action focused unavailable-action");
        palette(puppet,
                "sfm action invoke sfm:review/comment/create/candidate/glyph focused 0 1 unavailable-glyph-rejected");
        checkpoint(
                puppet,
                AssertCandidateCommentReviewPuppetAction.Stage.UNAVAILABLE_ACCEPTED,
                "candidate-comment-unavailable-acceptance",
                "candidate-comment-unavailable-acceptance",
                "Unavailable frames accept route/action discussion while rejecting fabricated glyph bounds."
        );

        puppet.reloadCandidateCommentSession(
                CandidateCommentSessionPuppetAction.SessionRole.UNAVAILABLE_FIXTURE);
        navigateUnavailable(puppet, AssertCandidateCommentReviewPuppetAction.ROUTE_COMMENT, "route");
        navigateUnavailable(puppet, AssertCandidateCommentReviewPuppetAction.ACTION_COMMENT, "action");
        checkpoint(
                puppet,
                AssertCandidateCommentReviewPuppetAction.Stage.FINAL_ROUNDTRIP,
                "candidate-comment-review",
                "candidate-comment-roundtrip",
                "Both persisted sessions reopen every candidate at its exact immutable route frame."
        );
        puppet.unregisterCandidateHistoryStatusFixture();
    }

    private static void navigateMain(SFMGamePuppetHelper puppet, String commentId, String suffix) {
        palette(puppet, "sfm action invoke sfm:review/comment/navigate focused " + commentId);
        puppet.assertCandidateCommentNavigation(
                CandidateCommentSessionPuppetAction.SessionRole.MAIN,
                commentId,
                "candidate-comment-navigate-main-" + suffix
        );
    }

    private static void navigateUnavailable(SFMGamePuppetHelper puppet, String commentId, String suffix) {
        palette(puppet, "sfm action invoke sfm:review/comment/navigate focused " + commentId);
        puppet.assertCandidateCommentNavigation(
                CandidateCommentSessionPuppetAction.SessionRole.UNAVAILABLE_FIXTURE,
                commentId,
                "candidate-comment-navigate-unavailable-" + suffix
        );
    }

    private static void palette(SFMGamePuppetHelper puppet, String command) {
        puppet.executeCommandPalette(command);
        puppet.executeCommandPalette("sfm action invoke sfm:palette/close");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
    }

    private static void rewindNaturalEdits(SFMGamePuppetHelper puppet, int count) {
        for (int index = 0; index < count; index++) {
            puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        }
    }

    private static void checkpoint(
            SFMGamePuppetHelper puppet,
            AssertCandidateCommentReviewPuppetAction.Stage stage,
            String artifact,
            String capture,
            String caption
    ) {
        puppet.assertCandidateCommentReview(stage, artifact);
        puppet.capture(capture, Component.literal("Candidate comments: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(caption).withStyle(ChatFormatting.BLACK)));
    }
}
