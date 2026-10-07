package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionRuntime;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionStore;
import ca.teamdman.sfm.client.screen.history.SFMCandidateHistoryPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMTimelinePanel;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

import java.util.Objects;

/** Test-only persistence setup around the production review-session store. */
public record CandidateCommentSessionPuppetAction(Operation operation, SessionRole role)
        implements SFMPuppetAction {
    public enum Operation {
        RESET,
        RELOAD
    }

    public enum SessionRole {
        MAIN,
        UNAVAILABLE_FIXTURE
    }

    public CandidateCommentSessionPuppetAction {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(role, "role");
    }

    @Override
    public String description() {
        return operation.name().toLowerCase(java.util.Locale.ROOT)
                + " candidate-comment session " + role.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMReviewSessionRuntime reviewRuntime = SFMReviewSessionRuntime.get();
        if (operation == Operation.RESET) {
            String machineId = focusedCandidatePanel().pinnedMachineId().orElseThrow();
            if (role == SessionRole.MAIN) {
                AssertCandidateCommentReviewPuppetAction.beginJourney(machineId);
            } else {
                AssertCandidateCommentReviewPuppetAction.bindUnavailableFixture(machineId);
            }
            reviewRuntime.resetForFixture(machineId);
            if (!reviewRuntime.session(machineId).comments().isEmpty()) {
                throw new IllegalStateException("Candidate-comment fixture reset did not produce an empty session");
            }
            return true;
        }

        String machineId = AssertCandidateCommentReviewPuppetAction.machineId(role);
        SFMReviewSessionStore.LoadResult loaded = reviewRuntime.reload(machineId);
        if (loaded.session().isEmpty()) {
            throw new IllegalStateException("Persisted candidate-comment session was not reloaded for " + role);
        }
        if (loaded.recoveredLastValid()) {
            throw new IllegalStateException("Candidate-comment reload unexpectedly needed last-valid recovery");
        }
        if (loaded.migratedV1()) {
            throw new IllegalStateException("Candidate-comment V2 journey unexpectedly loaded a V1 session");
        }
        AssertCandidateCommentReviewPuppetAction.recordReload(role, loaded.session().orElseThrow());
        return true;
    }

    private static SFMCandidateHistoryPanel focusedCandidatePanel() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            throw new IllegalStateException("Expected an SFM workspace for candidate-comment persistence setup");
        }
        if (!(workspace.focusedPanelInstance() instanceof SFMTimelinePanel timeline)
                || !(timeline.child() instanceof SFMCandidateHistoryPanel candidate)) {
            throw new IllegalStateException("Focused panel is not a Candidate History timeline");
        }
        if (candidate.loadStatus() != SFMCandidateHistoryPanel.LoadStatus.READY) {
            throw new IllegalStateException("Focused Candidate History panel is not ready");
        }
        return candidate;
    }
}
