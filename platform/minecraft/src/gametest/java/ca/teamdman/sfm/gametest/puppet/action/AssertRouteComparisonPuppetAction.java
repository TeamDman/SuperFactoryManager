package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMRouteComparisonRuntime;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonCodec;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionRuntime;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.screen.history.SFMCandidateHistoryPanel;
import ca.teamdman.sfm.client.screen.history.SFMRouteComparisonPanel;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Structured and visual-proof companion for the natural X3 comparison journey. */
public record AssertRouteComparisonPuppetAction(Stage stage, String artifactName) implements SFMPuppetAction {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Journey journey = new Journey();

    public enum Stage {
        INITIAL_LOCKSTEP,
        LOCKSTEP_SCRUBBED,
        INDEPENDENT_SCRUBBED,
        DISPOSITION_RECORDED,
        PERSISTENCE_RELOADED,
        EXPLICIT_SELECTION
    }

    public AssertRouteComparisonPuppetAction {
        Objects.requireNonNull(stage, "stage");
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("Route-comparison artifact name must not be blank");
        }
    }

    @Override
    public String description() {
        return "assert route-comparison stage " + stage;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime puppetRuntime) {
        SFMRouteComparisonPanel panel = RouteComparisonSessionPuppetAction.focusedPanel();
        if (panel.leftPanel().loadStatus() != SFMCandidateHistoryPanel.LoadStatus.READY
                || panel.rightPanel().loadStatus() != SFMCandidateHistoryPanel.LoadStatus.READY) {
            return false;
        }
        SFMRouteComparisonSession session = SFMRouteComparisonRuntime.get().require(panel.sessionId());
        SFMHistoryGraphRuntime.MachineSnapshot machine = machine(session);
        switch (stage) {
            case INITIAL_LOCKSTEP -> assertInitial(panel, session, machine);
            case LOCKSTEP_SCRUBBED -> {
                assertComparisonOnly(session, machine);
                require(session.mode() == SFMRouteComparisonSession.Mode.LOCKSTEP,
                        "lockstep stage lost lockstep mode");
                require(session.leftCursor() == 1 && session.rightCursor() == 1,
                        "lockstep stage did not seek both corresponding frames");
            }
            case INDEPENDENT_SCRUBBED -> {
                assertComparisonOnly(session, machine);
                require(session.mode() == SFMRouteComparisonSession.Mode.INDEPENDENT,
                        "independent stage did not retain independent mode");
                require(session.leftCursor() == 0 && session.rightCursor() == 2,
                        "independent cursors did not retain their distinct positions");
            }
            case DISPOSITION_RECORDED -> {
                assertComparisonOnly(session, machine);
                require(session.leftDisposition() == SFMRouteComparisonSession.Disposition.PREFERRED,
                        "left route was not preferred");
                require(session.rightDisposition() == SFMRouteComparisonSession.Disposition.REJECTED,
                        "right route was not rejected");
            }
            case PERSISTENCE_RELOADED -> {
                assertComparisonOnly(session, machine);
                require(journey.reloadObserved, "production-store reload was not observed");
                require(Objects.equals(journey.canonicalBeforeReload, journey.canonicalAfterReload),
                        "route-comparison bytes changed across reload");
                require(journey.canonicalAfterReload.equals(SFMRouteComparisonCodec.encode(session)),
                        "panel did not observe the exact reloaded comparison session");
            }
            case EXPLICIT_SELECTION -> assertExplicitSelection(session, machine);
        }
        assertRoutesAndCommentsRetained(panel, session, machine);
        Map<String, Object> evidence = evidence(panel, session, machine);
        puppetRuntime.writeArtifact(artifactName, SFMGamePuppetArtifactFormat.JSON, GSON.toJson(evidence));
        if (stage == Stage.EXPLICIT_SELECTION) {
            puppetRuntime.writeArtifact(
                    artifactName + "-summary",
                    SFMGamePuppetArtifactFormat.UTF8,
                    "Route comparison retained both routes and both comments; comparison-only stages "
                            + "left head/IP/selection unchanged, and explicit selection chose the left route.\n"
            );
        }
        return true;
    }

    static synchronized void beginJourney() {
        journey = new Journey();
    }

    static synchronized void recordReload(String before, String after) {
        journey.canonicalBeforeReload = Objects.requireNonNull(before, "before");
        journey.canonicalAfterReload = Objects.requireNonNull(after, "after");
        journey.reloadObserved = true;
    }

    private static synchronized void assertInitial(
            SFMRouteComparisonPanel panel,
            SFMRouteComparisonSession session,
            SFMHistoryGraphRuntime.MachineSnapshot machine
    ) {
        require(session.mode() == SFMRouteComparisonSession.Mode.LOCKSTEP,
                "comparison did not begin in lockstep mode");
        require(session.leftCursor() == 0 && session.rightCursor() == 0,
                "comparison did not begin at both route starts");
        require(session.leftDisposition() == SFMRouteComparisonSession.Disposition.UNDECIDED
                        && session.rightDisposition() == SFMRouteComparisonSession.Disposition.UNDECIDED,
                "reset comparison retained stale dispositions");
        journey.baseline = MachineObservation.capture(machine);
        journey.sessionId = session.id();
        journey.left = session.left();
        journey.right = session.right();
        journey.initialCanonical = SFMRouteComparisonCodec.encode(session);
        require(panel.openingInvariantStillHolds(), "panel opened after actual trajectory state changed");
        assertComparisonOnly(session, machine);
    }

    private static synchronized void assertComparisonOnly(
            SFMRouteComparisonSession session,
            SFMHistoryGraphRuntime.MachineSnapshot machine
    ) {
        require(journey.baseline != null, "initial comparison stage was not observed");
        require(session.id().equals(journey.sessionId), "comparison session identity changed");
        require(session.left().equals(journey.left) && session.right().equals(journey.right),
                "comparison route identities changed");
        require(MachineObservation.capture(machine).equals(journey.baseline),
                "comparison-only operation changed machine revision/head/IP/selection");
    }

    private static synchronized void assertExplicitSelection(
            SFMRouteComparisonSession session,
            SFMHistoryGraphRuntime.MachineSnapshot machine
    ) {
        require(journey.baseline != null, "initial comparison stage was not observed");
        MachineObservation current = MachineObservation.capture(machine);
        require(current.revision() > journey.baseline.revision(),
                "explicit selection did not publish a trajectory-machine revision");
        require(current.actualHistoryHeadId().equals(journey.baseline.actualHistoryHeadId()),
                "explicit route selection moved actual history head");
        require(current.currentStateId().equals(journey.baseline.currentStateId()),
                "explicit route selection changed document state");
        require(current.selectedPlanRevisionId().equals(Optional.of(session.left().planRevisionId())),
                "explicit selection did not choose the left plan revision");
        require(current.selectedRouteId().equals(Optional.of(session.left().routeId())),
                "explicit selection did not choose the left route");
        require(current.pointerPlanRevisionId().equals(Optional.of(session.left().planRevisionId()))
                        && current.pointerRouteId().equals(Optional.of(session.left().routeId())),
                "instruction pointer did not move onto the explicitly selected route");
    }

    private static void assertRoutesAndCommentsRetained(
            SFMRouteComparisonPanel panel,
            SFMRouteComparisonSession session,
            SFMHistoryGraphRuntime.MachineSnapshot machine
    ) {
        require(route(machine, session.left()).isPresent(), "left retained route disappeared");
        require(route(machine, session.right()).isPresent(), "right retained route disappeared");
        require(routeComments(session.left()).size() == 1, "left route comment was not retained exactly once");
        require(routeComments(session.right()).size() == 1, "right route comment was not retained exactly once");
        require(routeComments(session.left()).get(0).text().equals("left-route-review"),
                "left route comment text changed");
        require(routeComments(session.right()).get(0).text().equals("right-route-review"),
                "right route comment text changed");
        require(panel.leftPanel().projection().flatMap(AssertRouteComparisonPuppetAction::finalHash).isPresent(),
                "left route final predicted hash is unavailable");
        require(panel.rightPanel().projection().flatMap(AssertRouteComparisonPuppetAction::finalHash).isPresent(),
                "right route final predicted hash is unavailable");
        require(SFMReviewSessionRuntime.get().session(session.left().machineId()).comments().stream()
                        .allMatch(comment -> comment.target()
                                instanceof SFMReviewSessionV2.CandidateTrajectoryTarget),
                "review disposition or comparison created a committed comment/approval surrogate");
    }

    private Map<String, Object> evidence(
            SFMRouteComparisonPanel panel,
            SFMRouteComparisonSession session,
            SFMHistoryGraphRuntime.MachineSnapshot machine
    ) {
        LinkedHashMap<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", "sfm.route-comparison-puppet-stage/1");
        answer.put("stage", stage.name());
        answer.put("mandatory_screenshot_capture_id", artifactName);
        answer.put("session_id", session.id());
        answer.put("session_revision", session.revision());
        answer.put("mode", session.mode().name());
        answer.put("left", sideEvidence(panel.leftPanel(), session, machine, SFMRouteComparisonSession.Side.LEFT));
        answer.put("right", sideEvidence(panel.rightPanel(), session, machine, SFMRouteComparisonSession.Side.RIGHT));
        answer.put("machine_baseline", journey.baseline == null ? null : journey.baseline.toArtifactMap());
        answer.put("machine_current", MachineObservation.capture(machine).toArtifactMap());
        answer.put("comparison_only_machine_unchanged",
                stage == Stage.EXPLICIT_SELECTION ? null : MachineObservation.capture(machine).equals(journey.baseline));
        answer.put("reload_observed", journey.reloadObserved);
        answer.put("reload_canonical_equal", journey.reloadObserved
                && Objects.equals(journey.canonicalBeforeReload, journey.canonicalAfterReload));
        answer.put("both_routes_retained", route(machine, session.left()).isPresent()
                && route(machine, session.right()).isPresent());
        answer.put("both_comments_retained", routeComments(session.left()).size() == 1
                && routeComments(session.right()).size() == 1);
        answer.put("effective_approvals", 0);
        return answer;
    }

    private static Map<String, Object> sideEvidence(
            SFMCandidateHistoryPanel panel,
            SFMRouteComparisonSession session,
            SFMHistoryGraphRuntime.MachineSnapshot machine,
            SFMRouteComparisonSession.Side side
    ) {
        SFMRouteComparisonSession.RouteAddress address = session.address(side);
        SFMRouteComparisonRuntime.RouteDescriptor descriptor = SFMRouteComparisonRuntime
                .describe(machine, address).orElseThrow();
        LinkedHashMap<String, Object> answer = new LinkedHashMap<>();
        answer.put("machine_id", address.machineId());
        answer.put("plan_revision_id", address.planRevisionId());
        answer.put("route_id", address.routeId());
        answer.put("cursor", session.cursor(side));
        answer.put("last_position", descriptor.lastPosition());
        answer.put("total_cost", descriptor.totalCost());
        answer.put("outcome_status", descriptor.status().name());
        answer.put("final_state_id", descriptor.finalStateId());
        answer.put("final_state_hash", panel.projection()
                .flatMap(AssertRouteComparisonPuppetAction::finalHash).orElse(null));
        answer.put("comment_count", routeComments(address).size());
        answer.put("comment_texts", routeComments(address).stream()
                .map(SFMReviewSessionV2.Comment::text).toList());
        answer.put("disposition", session.disposition(side).name());
        return answer;
    }

    private static Optional<String> finalHash(SFMCandidateHistoryContract.CandidateRouteProjection route) {
        return route.frame(route.lastPosition()).address().predictedStateHash();
    }

    private static Optional<SFMTrajectoryContract.TrajectoryRoute> route(
            SFMHistoryGraphRuntime.MachineSnapshot machine,
            SFMRouteComparisonSession.RouteAddress address
    ) {
        return machine.planBook().plans().stream()
                .filter(plan -> plan.id().equals(address.planRevisionId()))
                .flatMap(plan -> plan.routes().stream())
                .filter(value -> value.id().equals(address.routeId()))
                .findFirst();
    }

    private static List<SFMReviewSessionV2.Comment> routeComments(
            SFMRouteComparisonSession.RouteAddress address
    ) {
        return SFMReviewSessionRuntime.get().session(address.machineId()).comments().stream()
                .filter(comment -> comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget)
                .filter(comment -> {
                    SFMReviewSessionV2.CandidateTrajectoryTarget target =
                            (SFMReviewSessionV2.CandidateTrajectoryTarget) comment.target();
                    return target.trajectoryPlanRevisionId().equals(address.planRevisionId())
                            && target.routeId().equals(address.routeId());
                })
                .toList();
    }

    private static SFMHistoryGraphRuntime.MachineSnapshot machine(SFMRouteComparisonSession session) {
        return SFMHistoryGraphRuntime.get().snapshotEvent().machine(session.left().machineId())
                .orElseThrow(() -> new IllegalStateException("Compared trajectory machine is unavailable"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    static record MachineObservation(
            long revision,
            String actualHistoryHeadId,
            String currentStateId,
            Optional<String> selectedPlanRevisionId,
            Optional<String> selectedRouteId,
            Optional<String> pointerPlanRevisionId,
            Optional<String> pointerRouteId,
            Optional<Integer> pointerPosition
    ) {
        Map<String, Object> toArtifactMap() {
            LinkedHashMap<String, Object> answer = new LinkedHashMap<>();
            answer.put("revision", revision);
            answer.put("actual_history_head_id", actualHistoryHeadId);
            answer.put("current_state_id", currentStateId);
            answer.put("selected_plan_revision_id", selectedPlanRevisionId.orElse(null));
            answer.put("selected_route_id", selectedRouteId.orElse(null));
            answer.put("pointer_plan_revision_id", pointerPlanRevisionId.orElse(null));
            answer.put("pointer_route_id", pointerRouteId.orElse(null));
            answer.put("pointer_position", pointerPosition.orElse(null));
            return answer;
        }

        private static MachineObservation capture(SFMHistoryGraphRuntime.MachineSnapshot snapshot) {
            Optional<String> selectedPlan = snapshot.planBook().selectedPlanRevisionId();
            Optional<String> selectedRoute = selectedPlan.flatMap(planId -> snapshot.planBook().plans().stream()
                    .filter(plan -> plan.id().equals(planId))
                    .findFirst()
                    .flatMap(SFMTrajectoryContract.TrajectoryPlanRevision::selectedRouteId));
            Optional<SFMTrajectoryContract.InstructionPointer> pointer = snapshot.machine().instructionPointer();
            return new MachineObservation(
                    snapshot.revision(),
                    snapshot.machine().actualHistoryHeadId(),
                    snapshot.history().heads().stream()
                            .filter(head -> head.id().equals(snapshot.machine().actualHistoryHeadId()))
                            .map(head -> head.stateRevisionId())
                            .findFirst()
                            .orElse(snapshot.machine().actualHistoryHeadId()),
                    selectedPlan,
                    selectedRoute,
                    pointer.map(SFMTrajectoryContract.InstructionPointer::planRevisionId),
                    pointer.map(SFMTrajectoryContract.InstructionPointer::routeId),
                    pointer.map(SFMTrajectoryContract.InstructionPointer::nextStepIndex)
            );
        }
    }

    private static final class Journey {
        private MachineObservation baseline;
        private String sessionId;
        private SFMRouteComparisonSession.RouteAddress left;
        private SFMRouteComparisonSession.RouteAddress right;
        private String initialCanonical;
        private boolean reloadObserved;
        private String canonicalBeforeReload;
        private String canonicalAfterReload;
    }
}
