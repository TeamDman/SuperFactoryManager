package ca.teamdman.sfm.client.history.presentation;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMHistoryGraphPresentationProjectionTests {
    @Test
    void legendIdentifiersAndNarrationAreStable() {
        assertEquals(
                List.of(
                        "committed-executed",
                        "selected-projected-suffix",
                        "retained-alternative",
                        "open-frontier",
                        "closed",
                        "barrier",
                        "target",
                        "actual-head",
                        "instruction-pointer"
                ),
                SFMHistoryGraphPresentationModel.LegendRole.stableLegend().stream()
                        .map(SFMHistoryGraphPresentationModel.LegendEntry::styleId)
                        .toList()
        );
        assertTrue(SFMHistoryGraphPresentationModel.LegendRole.stableLegend().stream()
                .allMatch(entry -> !entry.label().isBlank() && !entry.narration().isBlank()));
    }

    @Test
    void actualHeadInstructionPointerAndFrontierRemainIndependent() {
        Fixture fixture = fixture();
        SFMHistoryGraphPresentationModel.Presentation presentation =
                SFMHistoryGraphPresentationProjection.project(
                        fixture.history(),
                        fixture.planBook(),
                        fixture.machine()
                );

        SFMHistoryGraphPresentationModel.Marker actual = marker(
                presentation,
                SFMHistoryGraphPresentationModel.MarkerKind.ACTUAL_HEAD
        );
        SFMHistoryGraphPresentationModel.Marker pointer = marker(
                presentation,
                SFMHistoryGraphPresentationModel.MarkerKind.INSTRUCTION_POINTER
        );
        SFMHistoryGraphPresentationModel.Node frontier = node(presentation, "frontier-open");
        SFMHistoryGraphPresentationModel.Node target = node(presentation, "plan-target");

        assertEquals(SFMHistoryGraphPresentationModel.MarkerSubjectKind.NODE, actual.subjectKind());
        assertEquals("state-live", actual.subjectId());
        assertEquals(SFMHistoryGraphPresentationModel.MarkerSubjectKind.EDGE, pointer.subjectKind());
        assertEquals("step-finish", edge(presentation, pointer.subjectId()).sourceContractId());
        assertNotEquals(actual.subjectId(), pointer.subjectId());
        assertNotEquals(actual.subjectId(), frontier.id());
        assertNotEquals(pointer.subjectId(), frontier.id());
        assertTrue(frontier.roles().contains(SFMHistoryGraphPresentationModel.LegendRole.OPEN_FRONTIER));
        assertFalse(frontier.roles().contains(SFMHistoryGraphPresentationModel.LegendRole.ACTUAL_HEAD));
        assertFalse(frontier.roles().contains(SFMHistoryGraphPresentationModel.LegendRole.INSTRUCTION_POINTER));
        assertTrue(target.roles().contains(SFMHistoryGraphPresentationModel.LegendRole.TARGET));
        assertTrue(edge(presentation, pointer.subjectId()).roles().contains(
                SFMHistoryGraphPresentationModel.LegendRole.INSTRUCTION_POINTER
        ));
        assertTrue(presentation.edges().stream().anyMatch(edge ->
                edge.sourceContractId().equals("step-finish")
                        && edge.roles().contains(
                                SFMHistoryGraphPresentationModel.LegendRole.SELECTED_PROJECTED_SUFFIX
                        )));
        assertFalse(presentation.edges().stream().anyMatch(edge ->
                edge.sourceContractId().equals("step-prepare")),
                "The selected route prefix before the instruction pointer is represented by history, not as a candidate");
        assertTrue(presentation.nodes().stream().allMatch(node -> !node.narration().isBlank()));
        assertTrue(presentation.edges().stream().allMatch(edge -> !edge.narration().isBlank()));
        assertTrue(presentation.markers().stream().allMatch(marker -> !marker.narration().isBlank()));
    }

    @Test
    void candidateEdgesAreNeverPresentedAsCommittedHistory() {
        Fixture fixture = fixture();
        SFMHistoryGraphPresentationModel.Presentation presentation =
                SFMHistoryGraphPresentationProjection.project(
                        fixture.history(),
                        fixture.planBook(),
                        fixture.machine()
                );

        List<SFMHistoryGraphPresentationModel.Edge> candidates = presentation.edges().stream()
                .filter(edge -> edge.commitment()
                        == SFMHistoryGraphPresentationModel.EdgeCommitment.PROJECTED_CANDIDATE)
                .toList();
        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().allMatch(edge ->
                !edge.roles().contains(SFMHistoryGraphPresentationModel.LegendRole.COMMITTED_EXECUTED)));
        assertTrue(candidates.stream().allMatch(edge ->
                !edge.narration().toLowerCase(java.util.Locale.ROOT).contains("committed")));

        SFMHistoryGraphPresentationModel.Edge committed = presentation.edges().stream()
                .filter(edge -> edge.sourceContractId().equals("edge-root-live"))
                .findFirst()
                .orElseThrow();
        assertEquals(SFMHistoryGraphPresentationModel.EdgeCommitment.COMMITTED_HISTORY,
                committed.commitment());
        assertTrue(committed.roles().contains(SFMHistoryGraphPresentationModel.LegendRole.COMMITTED_EXECUTED));
    }

    @Test
    void liveMachineSelectionOutranksStalePlanBookSelection() {
        Fixture fixture = fixture();
        SFMTrajectoryContract.TrajectoryPlanRevision stale = new SFMTrajectoryContract.TrajectoryPlanRevision(
                "plan-stale",
                Optional.empty(),
                "stale-start",
                "supervision-v1",
                new SFMTrajectoryContract.ActionGeneratorIdentity("fixture-generator", "v1", true),
                new SFMTrajectoryContract.CostPolicyIdentity("fixture-cost", "v1"),
                new SFMTrajectoryContract.HeuristicIdentity("fixture-heuristic", "v1", true),
                SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                new SFMTrajectoryContract.SearchBudget(32, 64, 1_000),
                SFMTrajectoryContract.PlanResult.FOUND,
                SFMTrajectoryContract.OptimalityClaim.MINIMUM_COST_UNDER_DECLARED_GRAPH_AND_POLICY,
                List.of(new SFMTrajectoryContract.TrajectoryRoute(
                        "route-stale",
                        "stale-start",
                        List.of(step(
                                "step-stale",
                                "stale-start",
                                "stale-target",
                                intent("intent-stale", "stale"),
                                1
                        )),
                        1,
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
                )),
                Optional.of("route-stale"),
                List.of(),
                "stale plan-book selection"
        );
        SFMTrajectoryContract.PlanBook stalePlanBookSelection = new SFMTrajectoryContract.PlanBook(
                SFMTrajectoryContract.SCHEMA,
                List.of(fixture.planBook().plans().get(0), stale),
                Optional.of(stale.id())
        );

        SFMHistoryGraphPresentationModel.Presentation presentation =
                SFMHistoryGraphPresentationProjection.project(
                        fixture.history(),
                        stalePlanBookSelection,
                        fixture.machine()
                );

        assertTrue(node(presentation, "plan-target").roles().contains(
                SFMHistoryGraphPresentationModel.LegendRole.TARGET
        ));
        assertFalse(node(presentation, "stale-target").roles().contains(
                SFMHistoryGraphPresentationModel.LegendRole.TARGET
        ));
        assertTrue(presentation.edges().stream()
                .filter(edge -> edge.sourceContractId().equals("step-finish"))
                .anyMatch(edge -> edge.roles().contains(
                        SFMHistoryGraphPresentationModel.LegendRole.SELECTED_PROJECTED_SUFFIX
                )));
        assertTrue(presentation.edges().stream()
                .filter(edge -> edge.sourceContractId().equals("step-stale"))
                .noneMatch(edge -> edge.roles().contains(
                        SFMHistoryGraphPresentationModel.LegendRole.SELECTED_PROJECTED_SUFFIX
                )));
    }

    @Test
    void boundedProjectionIsDeterministicAndNeverLeavesDanglingVisibleEntities() {
        Fixture fixture = fixture();
        SFMHistoryGraphPresentationModel.Bounds bounds = new SFMHistoryGraphPresentationModel.Bounds(5, 2);
        SFMHistoryGraphPresentationModel.Presentation first = SFMHistoryGraphPresentationProjection.project(
                fixture.history(), fixture.planBook(), fixture.machine(), bounds
        );
        SFMHistoryGraphPresentationModel.Presentation second = SFMHistoryGraphPresentationProjection.project(
                fixture.history(), fixture.planBook(), fixture.machine(), bounds
        );

        assertEquals(first, second);
        assertEquals(5, first.nodes().size());
        assertTrue(first.edges().size() <= 2);
        assertTrue(first.summary().truncated());
        assertEquals(first.nodes().size(), first.summary().visibleNodes());
        assertEquals(first.edges().size(), first.summary().visibleEdges());
        assertTrue(first.edges().stream().allMatch(edge ->
                nodeExists(first, edge.fromNodeId()) && nodeExists(first, edge.toNodeId())));
        assertTrue(first.markers().stream().allMatch(marker -> marker.subjectKind()
                == SFMHistoryGraphPresentationModel.MarkerSubjectKind.NODE
                ? nodeExists(first, marker.subjectId())
                : edgeExists(first, marker.subjectId())));
    }

    private static Fixture fixture() {
        SFMHistoryGraphContract.Graph history = new SFMHistoryGraphContract.Graph(
                SFMHistoryGraphContract.SCHEMA,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        state("state-root", List.of(), true,
                                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                        state("state-live", List.of("state-root"), true,
                                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                        state("state-alternative", List.of("state-root"), true,
                                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                        state("state-barrier", List.of("state-live"), false,
                                SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER)
                ),
                List.of(new SFMHistoryGraphContract.HistoryHead(
                        "head-actual",
                        new SFMHistoryGraphContract.UndoDomain(
                                SFMHistoryGraphContract.UndoDomainKind.DOCUMENT,
                                "document://fixture"
                        ),
                        "state-live",
                        Optional.of("current")
                )),
                List.of(),
                List.of(
                        edge("edge-root-live", "state-root", "state-live",
                                SFMHistoryGraphContract.EffectClass.PURE,
                                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED, true),
                        edge("edge-root-alternative", "state-root", "state-alternative",
                                SFMHistoryGraphContract.EffectClass.PURE,
                                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED, true),
                        edge("edge-live-barrier", "state-live", "state-barrier",
                                SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                                SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER, false)
                ),
                List.of(new SFMHistoryGraphContract.RetentionPin(
                        "pin-alternative",
                        SFMHistoryGraphContract.RetentionKind.NAMED_BRANCH,
                        "state-alternative",
                        "fixture"
                ))
        );

        SFMHistoryGraphContract.ActionIntent prepare = intent("intent-prepare", "prepare");
        SFMHistoryGraphContract.ActionIntent finish = intent("intent-finish", "finish");
        SFMHistoryGraphContract.ActionIntent alternative = intent("intent-alternative", "alternative");
        SFMTrajectoryContract.TrajectoryRoute selectedRoute = new SFMTrajectoryContract.TrajectoryRoute(
                "route-selected",
                "plan-start",
                List.of(
                        step("step-prepare", "plan-start", "plan-mid", prepare, 1),
                        step("step-finish", "plan-mid", "plan-target", finish, 2)
                ),
                2,
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
        );
        SFMTrajectoryContract.TrajectoryRoute alternativeRoute = new SFMTrajectoryContract.TrajectoryRoute(
                "route-alternative",
                "plan-start",
                List.of(step("step-alternative", "plan-start", "plan-other", alternative, 3)),
                3,
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
        );
        SFMTrajectoryContract.TrajectoryPlanRevision plan = new SFMTrajectoryContract.TrajectoryPlanRevision(
                "plan-1",
                Optional.empty(),
                "plan-start",
                "supervision-v1",
                new SFMTrajectoryContract.ActionGeneratorIdentity("fixture-generator", "v1", true),
                new SFMTrajectoryContract.CostPolicyIdentity("fixture-cost", "v1"),
                new SFMTrajectoryContract.HeuristicIdentity("fixture-heuristic", "v1", true),
                SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                new SFMTrajectoryContract.SearchBudget(32, 64, 1_000),
                SFMTrajectoryContract.PlanResult.FOUND,
                SFMTrajectoryContract.OptimalityClaim.MINIMUM_COST_UNDER_DECLARED_GRAPH_AND_POLICY,
                List.of(alternativeRoute, selectedRoute),
                Optional.of(selectedRoute.id()),
                List.of(
                        candidate("closed-state", SFMTrajectoryContract.SearchCandidateStatus.CLOSED,
                                Optional.of("plan-start")),
                        candidate("frontier-open", SFMTrajectoryContract.SearchCandidateStatus.OPEN,
                                Optional.of("plan-mid")),
                        candidate("search-barrier", SFMTrajectoryContract.SearchCandidateStatus.BARRIER,
                                Optional.of("plan-mid"))
                ),
                "fixture plan"
        );
        SFMTrajectoryContract.PlanBook planBook = new SFMTrajectoryContract.PlanBook(
                SFMTrajectoryContract.SCHEMA,
                List.of(plan),
                Optional.of(plan.id())
        );
        SFMTrajectoryContract.TrajectoryMachineState machine =
                new SFMTrajectoryContract.TrajectoryMachineState(
                        "head-actual",
                        Optional.of(plan.id()),
                        Optional.of(new SFMTrajectoryContract.InstructionPointer(
                                plan.id(), selectedRoute.id(), 1
                        )),
                        Optional.of(new SFMTrajectoryContract.ProjectionFrontier(
                                plan.id(), 4, 8, List.of("frontier-open"),
                                SFMHistoryGraphContract.ProjectionStatus.RUNNING
                        )),
                        "supervision-v1",
                        SFMTrajectoryContract.MachineStatus.READY,
                        new SFMTrajectoryContract.RemainingBudget(28, 56, 900)
                );
        return new Fixture(history, planBook, machine);
    }

    private static SFMHistoryGraphContract.StateRevision state(
            String id,
            List<String> parents,
            boolean committed,
            SFMHistoryGraphContract.ProjectionStatus status
    ) {
        return new SFMHistoryGraphContract.StateRevision(id, parents, "sha256:" + id, committed, status);
    }

    private static SFMHistoryGraphContract.BranchEdge edge(
            String id,
            String parent,
            String child,
            SFMHistoryGraphContract.EffectClass effectClass,
            SFMHistoryGraphContract.ProjectionStatus status,
            boolean committed
    ) {
        return new SFMHistoryGraphContract.BranchEdge(
                id,
                parent,
                child,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                effectClass,
                status,
                committed
        );
    }

    private static SFMHistoryGraphContract.ActionIntent intent(String id, String action) {
        return new SFMHistoryGraphContract.ActionIntent(
                id,
                "sfm:fixture/" + action,
                List.of(),
                "sha256:" + id
        );
    }

    private static SFMTrajectoryContract.TrajectoryStep step(
            String id,
            String parent,
            String child,
            SFMHistoryGraphContract.ActionIntent intent,
            long cost
    ) {
        return new SFMTrajectoryContract.TrajectoryStep(
                id,
                parent,
                intent,
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                "outcome-" + id,
                child,
                SFMHistoryGraphContract.EffectClass.PURE,
                cost,
                cost,
                0
        );
    }

    private static SFMTrajectoryContract.SearchCandidate candidate(
            String state,
            SFMTrajectoryContract.SearchCandidateStatus status,
            Optional<String> predecessor
    ) {
        return new SFMTrajectoryContract.SearchCandidate(
                state,
                1,
                1,
                status,
                predecessor,
                Optional.of("intent-" + state),
                "fixture candidate"
        );
    }

    private static SFMHistoryGraphPresentationModel.Marker marker(
            SFMHistoryGraphPresentationModel.Presentation presentation,
            SFMHistoryGraphPresentationModel.MarkerKind kind
    ) {
        return presentation.markers().stream().filter(marker -> marker.kind() == kind).findFirst().orElseThrow();
    }

    private static SFMHistoryGraphPresentationModel.Node node(
            SFMHistoryGraphPresentationModel.Presentation presentation,
            String id
    ) {
        return presentation.nodes().stream().filter(node -> node.id().equals(id)).findFirst().orElseThrow();
    }

    private static SFMHistoryGraphPresentationModel.Edge edge(
            SFMHistoryGraphPresentationModel.Presentation presentation,
            String id
    ) {
        return presentation.edges().stream().filter(edge -> edge.id().equals(id)).findFirst().orElseThrow();
    }

    private static boolean nodeExists(SFMHistoryGraphPresentationModel.Presentation presentation, String id) {
        return presentation.nodes().stream().anyMatch(node -> node.id().equals(id));
    }

    private static boolean edgeExists(SFMHistoryGraphPresentationModel.Presentation presentation, String id) {
        return presentation.edges().stream().anyMatch(edge -> edge.id().equals(id));
    }

    private record Fixture(
            SFMHistoryGraphContract.Graph history,
            SFMTrajectoryContract.PlanBook planBook,
            SFMTrajectoryContract.TrajectoryMachineState machine
    ) {
    }
}
