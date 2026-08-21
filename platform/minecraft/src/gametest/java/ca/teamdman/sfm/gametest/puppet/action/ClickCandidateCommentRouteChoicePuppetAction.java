package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionRuntime;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Clicks the exact retained-route choice addressed by a persisted candidate comment. */
public record ClickCandidateCommentRouteChoicePuppetAction(String commentId) implements SFMPuppetAction {
    public ClickCandidateCommentRouteChoicePuppetAction {
        if (commentId == null || commentId.isBlank()) {
            throw new IllegalArgumentException("Candidate route comment id must not be blank");
        }
    }

    @Override
    public String description() {
        return "click retained route choice for " + commentId;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        String machineId = AssertCandidateCommentReviewPuppetAction.machineId(
                CandidateCommentSessionPuppetAction.SessionRole.MAIN);
        SFMReviewSessionV2.Comment comment = SFMReviewSessionRuntime.get().comment(machineId, commentId)
                .orElseThrow(() -> new IllegalStateException("Missing candidate route comment " + commentId));
        if (!(comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget target)) {
            throw new IllegalStateException("Retained route choice requires a candidate target");
        }
        String selector = SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, machineId).canonical();
        ResourceLocation actionId = new ResourceLocation("sfm", "episode/trajectory/route/select");
        String command = SFMActionChoice.invoke(
                actionId,
                selector + " " + target.trajectoryPlanRevisionId() + " " + target.routeId()
        ).command();
        runtime.clickActionChoice(command);
        return true;
    }
}
