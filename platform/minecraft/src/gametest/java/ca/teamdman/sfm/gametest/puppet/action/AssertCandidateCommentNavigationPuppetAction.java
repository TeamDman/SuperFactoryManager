package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionRuntime;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.screen.history.SFMCandidateHistoryPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMTimelinePanel;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Verifies that a reloaded candidate comment navigated to its immutable route frame. */
public record AssertCandidateCommentNavigationPuppetAction(
        CandidateCommentSessionPuppetAction.SessionRole role,
        String commentId,
        String artifactName
) implements SFMPuppetAction {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public AssertCandidateCommentNavigationPuppetAction {
        Objects.requireNonNull(role, "role");
        if (commentId == null || commentId.isBlank()) throw new IllegalArgumentException("commentId must not be blank");
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("artifactName must not be blank");
        }
    }

    @Override
    public String description() {
        return "assert reloaded navigation for " + role + "/" + commentId;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        String machineId = AssertCandidateCommentReviewPuppetAction.machineId(role);
        SFMReviewSessionV2.Comment comment = SFMReviewSessionRuntime.get().comment(machineId, commentId)
                .orElseThrow(() -> new IllegalStateException("Reloaded comment was not found: " + commentId));
        if (!(comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget target)) {
            throw new IllegalStateException("Navigation assertion requires a candidate target");
        }
        SFMCandidateHistoryPanel panel = focusedCandidatePanel();
        SFMCandidateHistoryContract.CandidateFrame frame = panel.currentFrame().orElseThrow();
        require(panel.pinnedMachineId().equals(java.util.Optional.of(machineId)), "navigation changed machine identity");
        require(panel.pinnedPlanRevisionId().equals(java.util.Optional.of(target.trajectoryPlanRevisionId())),
                "navigation changed plan identity");
        require(panel.pinnedRouteId().equals(java.util.Optional.of(target.routeId())),
                "navigation changed route identity");
        require(panel.currentPosition() == target.routeStepPosition(), "navigation changed frame position");
        require(frame.address().predictedStateId().equals(target.predictedStateId()),
                "navigation changed predicted state identity");
        require(frame.address().predictedStateHash().equals(target.predictedStateHash()),
                "navigation changed predicted state hash");
        require(frame.address().projectionStatus() == target.projectionStatus(),
                "navigation changed projection status");
        require(panel.currentComments().stream().anyMatch(value -> value.id().equals(commentId)),
                "navigated frame does not display the target comment");

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schema", "sfm.candidate-comment-navigation-puppet/1");
        evidence.put("session_role", role.name());
        evidence.put("comment_id", commentId);
        evidence.put("machine_id", machineId);
        evidence.put("plan_revision_id", target.trajectoryPlanRevisionId());
        evidence.put("route_id", target.routeId());
        evidence.put("frame", target.routeStepPosition());
        evidence.put("predicted_state_id", target.predictedStateId());
        evidence.put("predicted_state_hash", target.predictedStateHash().orElse(null));
        evidence.put("projection_status", target.projectionStatus().name());
        evidence.put("roundtrip", true);
        runtime.writeArtifact(artifactName, SFMGamePuppetArtifactFormat.JSON, GSON.toJson(evidence));
        AssertCandidateCommentReviewPuppetAction.recordNavigation(role, commentId, target);
        return true;
    }

    private static SFMCandidateHistoryPanel focusedCandidatePanel() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            throw new IllegalStateException("Expected an SFM workspace for candidate-comment navigation");
        }
        if (!(workspace.focusedPanelInstance() instanceof SFMTimelinePanel timeline)
                || !(timeline.child() instanceof SFMCandidateHistoryPanel candidate)) {
            throw new IllegalStateException("Focused panel is not a Candidate History timeline");
        }
        if (candidate.loadStatus() != SFMCandidateHistoryPanel.LoadStatus.READY) {
            throw new IllegalStateException("Navigated Candidate History panel is not ready");
        }
        return candidate;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
