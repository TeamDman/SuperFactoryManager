package ca.teamdman.sfm.client.history;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTrajectoryContractTests {
    @Test
    void languageNeutralFixtureKeepsIndependentPointersBranchesAndApproval() throws Exception {
        try (var stream = getClass().getResourceAsStream("trajectory-contract-v1.json")) {
            assertTrue(stream != null, "trajectory contract fixture must be packaged as a test resource");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            assertEquals(SFMTrajectoryContract.SCHEMA, root.get("schema").getAsString());
            assertEquals(SFMHistoryGraphContract.SCHEMA, root.get("history_schema").getAsString());
            assertEquals("head-document", root.get("actual_history_head").getAsString());
            assertEquals("plan-1", root.get("selected_trajectory_revision").getAsString());
            assertEquals("plan-1", root.getAsJsonObject("instruction_pointer")
                    .get("plan_revision").getAsString());
            assertEquals("plan-1", root.getAsJsonObject("projection_frontier")
                    .get("plan_revision").getAsString());
            assertEquals(2, root.getAsJsonObject("committed_fork").getAsJsonArray("children").size());
            assertEquals("ambiguous", root.getAsJsonObject("committed_fork")
                    .get("redo_status").getAsString());
            assertEquals("supervision-ready", root.getAsJsonObject("supervision")
                    .get("status").getAsString());
            assertTrue(root.getAsJsonObject("supervision").get("approval").isJsonNull());
            assertEquals("external-barrier", root.getAsJsonObject("external_barrier")
                    .get("projection_status").getAsString());
            assertEquals("plan-1", root.getAsJsonArray("plans").get(1).getAsJsonObject()
                    .get("parent_plan_revision").getAsString());
        }
    }

    @Test
    void fixtureSeparatesCommittedHistoryProjectedRoutesAndMachinePointers() {
        SFMHistoryGraphContract.Graph history = historyFixture();
        SFMTrajectoryContract.SupervisionContract supervision = supervisionReadyFixture();
        SFMTrajectoryContract.TrajectoryPlanRevision first = foundPlan("plan-1", Optional.empty());
        SFMTrajectoryContract.TrajectoryPlanRevision replan = new SFMTrajectoryContract.TrajectoryPlanRevision(
                "plan-2",
                Optional.of("plan-1"),
                "state-c",
                supervision.revision(),
                first.actionGenerator(),
                first.costPolicy(),
                first.heuristic(),
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                first.budget(),
                SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED,
                SFMTrajectoryContract.OptimalityClaim.NONE,
                List.of(),
                Optional.empty(),
                List.of(new SFMTrajectoryContract.SearchCandidate(
                        "state-c", 0, 0, SFMTrajectoryContract.SearchCandidateStatus.CLOSED,
                        Optional.empty(), Optional.empty(), "start expanded"
                )),
                "expansion budget exhausted"
        );
        SFMTrajectoryContract.PlanBook book = new SFMTrajectoryContract.PlanBook(
                SFMTrajectoryContract.SCHEMA,
                List.of(replan, first),
                Optional.of(first.id())
        );
        SFMTrajectoryContract.TrajectoryMachineState machine = new SFMTrajectoryContract.TrajectoryMachineState(
                "head-document",
                Optional.of(first.id()),
                Optional.of(new SFMTrajectoryContract.InstructionPointer(first.id(), "route-1", 0)),
                Optional.of(new SFMTrajectoryContract.ProjectionFrontier(
                        first.id(), 2, 4, List.of("state-projected"),
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
                )),
                supervision.revision(),
                SFMTrajectoryContract.MachineStatus.READY,
                new SFMTrajectoryContract.RemainingBudget(30, 60, 2_000)
        );

        assertEquals(List.of("plan-1", "plan-2"), book.plans().stream()
                .map(SFMTrajectoryContract.TrajectoryPlanRevision::id).toList());
        assertEquals("head-document", machine.actualHistoryHeadId());
        assertEquals("state-c", first.routes().get(0).startStateId());
        assertNotEquals(machine.actualHistoryHeadId(),
                machine.instructionPointer().orElseThrow().planRevisionId());
        assertEquals(SFMTrajectoryContract.SupervisionStatus.SUPERVISION_READY, supervision.status());
        assertTrue(supervision.approval().isEmpty(), "Machine readiness must not invent human approval");
        assertEquals(2, history.evaluations().stream()
                .filter(value -> value.intentId().equals("intent-number"))
                .count());
        assertTrue(history.edges().stream().anyMatch(edge -> !edge.committed()
                && edge.effectClass() == SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE
                && edge.status() == SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER));
    }

    @Test
    void staleActualHeadDoesNotAdvanceOrRewriteTheInstructionPointer() {
        SFMTrajectoryContract.TrajectoryPlanRevision plan = foundPlan("plan-stale", Optional.empty());
        SFMTrajectoryContract.InstructionPointer pointer = new SFMTrajectoryContract.InstructionPointer(
                plan.id(), "route-1", 0
        );
        SFMTrajectoryContract.TrajectoryMachineState stale = new SFMTrajectoryContract.TrajectoryMachineState(
                "head-moved-elsewhere",
                Optional.of(plan.id()),
                Optional.of(pointer),
                Optional.empty(),
                "supervision-v1",
                SFMTrajectoryContract.MachineStatus.STALE_PRECONDITION,
                new SFMTrajectoryContract.RemainingBudget(1, 1, 1)
        );

        assertEquals(0, stale.instructionPointer().orElseThrow().nextStepIndex());
        assertEquals("state-c", plan.routes().get(0).steps().get(0).expectedParentStateId());
        assertNotEquals("state-c", stale.actualHistoryHeadId());
    }

    @Test
    void invalidOptimalityAndApprovalClaimsFailClosed() {
        SFMTrajectoryContract.TrajectoryPlanRevision valid = foundPlan("plan-valid", Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> new SFMTrajectoryContract.TrajectoryPlanRevision(
                "plan-unbounded",
                Optional.empty(),
                valid.startStateId(),
                valid.supervisionContractRevision(),
                new SFMTrajectoryContract.ActionGeneratorIdentity("generator", "v1", false),
                valid.costPolicy(),
                valid.heuristic(),
                valid.algorithm(),
                valid.budget(),
                valid.result(),
                SFMTrajectoryContract.OptimalityClaim.MINIMUM_COST_UNDER_DECLARED_GRAPH_AND_POLICY,
                valid.routes(),
                valid.selectedRouteId(),
                valid.exploredCandidates(),
                valid.resultExplanation()
        ));
        assertThrows(IllegalArgumentException.class, () -> new SFMTrajectoryContract.SupervisionContract(
                "supervision",
                "supervision-v2",
                "document",
                "state-a",
                List.of(new SFMTrajectoryContract.Predicate("goal", "text-equals", "done")),
                List.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE),
                List.of(),
                new SFMTrajectoryContract.SearchBudget(10, 20, 1_000),
                5,
                SFMTrajectoryContract.ApprovalRequirement.HUMAN,
                SFMTrajectoryContract.SupervisionStatus.APPROVED,
                Optional.empty()
        ));
        assertThrows(IllegalArgumentException.class, () -> new SFMHistoryGraphContract.BranchEdge(
                "bad-edge",
                "state-a",
                "state-b",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                false
        ));
    }

    private static SFMHistoryGraphContract.Graph historyFixture() {
        SFMHistoryGraphContract.ActionIntent number = new SFMHistoryGraphContract.ActionIntent(
                "intent-number", "sfm:selection/replace/decimal-sequence",
                List.of("selection://hyphens"), "sha256:intent-number"
        );
        SFMHistoryGraphContract.ActionIntent external = new SFMHistoryGraphContract.ActionIntent(
                "intent-external", "sfm:process/launch", List.of("unsafe.exe"), "sha256:intent-external"
        );
        List<SFMHistoryGraphContract.StateRevision> states = List.of(
                state("state-a", List.of(), true, SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                state("state-b", List.of("state-a"), true, SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                state("state-c", List.of("state-a"), true, SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                state("state-projected", List.of("state-c"), false,
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                state("state-barrier", List.of("state-projected"), false,
                        SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER)
        );
        List<SFMHistoryGraphContract.ActionEvaluation> evaluations = List.of(
                evaluation("evaluation-b", number.id(), "state-a", "outcome-b",
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                evaluation("evaluation-projected", number.id(), "state-c", "outcome-projected",
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                evaluation("evaluation-external", external.id(), "state-projected", "outcome-external",
                        SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER)
        );
        List<SFMHistoryGraphContract.ActionOutcome> outcomes = List.of(
                outcome("outcome-b", "evaluation-b", Optional.of("state-b"),
                        SFMHistoryGraphContract.OutcomeStatus.SUCCEEDED),
                outcome("outcome-projected", "evaluation-projected", Optional.of("state-projected"),
                        SFMHistoryGraphContract.OutcomeStatus.SUCCEEDED),
                outcome("outcome-external", "evaluation-external", Optional.empty(),
                        SFMHistoryGraphContract.OutcomeStatus.EXTERNAL_BARRIER)
        );
        SFMHistoryGraphContract.HistoryHead head = new SFMHistoryGraphContract.HistoryHead(
                "head-document",
                new SFMHistoryGraphContract.UndoDomain(
                        SFMHistoryGraphContract.UndoDomainKind.DOCUMENT,
                        "document://numbering"
                ),
                "state-c",
                Optional.of("current")
        );
        return new SFMHistoryGraphContract.Graph(
                SFMHistoryGraphContract.SCHEMA,
                List.of(external, number),
                evaluations,
                outcomes,
                states,
                List.of(head),
                List.of(new SFMHistoryGraphContract.HeadMovement(
                        "movement-fork",
                        SFMHistoryGraphContract.HeadMovementKind.REDO,
                        head.id(),
                        "state-a",
                        "state-c",
                        List.of("state-b", "state-c"),
                        "fixture",
                        "choose-c"
                )),
                List.of(
                        edge("edge-a-b", "state-a", "state-b", Optional.of(number.id()),
                                Optional.of("evaluation-b"), Optional.of("outcome-b"),
                                SFMHistoryGraphContract.EffectClass.PURE,
                                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED, true),
                        edge("edge-a-c", "state-a", "state-c", Optional.empty(), Optional.empty(),
                                Optional.empty(), SFMHistoryGraphContract.EffectClass.PURE,
                                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED, true),
                        edge("edge-c-projected", "state-c", "state-projected", Optional.of(number.id()),
                                Optional.of("evaluation-projected"), Optional.of("outcome-projected"),
                                SFMHistoryGraphContract.EffectClass.PURE,
                                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED, false),
                        edge("edge-projected-barrier", "state-projected", "state-barrier",
                                Optional.of(external.id()), Optional.of("evaluation-external"),
                                Optional.of("outcome-external"),
                                SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                                SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER, false)
                ),
                List.of(new SFMHistoryGraphContract.RetentionPin(
                        "pin-comment", SFMHistoryGraphContract.RetentionKind.COMMENT,
                        "state-b", "comment-1"
                ))
        );
    }

    private static SFMTrajectoryContract.SupervisionContract supervisionReadyFixture() {
        return new SFMTrajectoryContract.SupervisionContract(
                "supervision-numbering",
                "supervision-v1",
                "document://numbering",
                "state-c",
                List.of(new SFMTrajectoryContract.Predicate(
                        "goal-text", "text-equals", "1. alpha\\n2. beta\\n"
                )),
                List.of(new SFMTrajectoryContract.Predicate(
                        "invariant-no-checkout-write", "checkout-unchanged", "true"
                )),
                Set.of(
                        SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                        SFMHistoryGraphContract.EffectClass.UNKNOWN
                ),
                List.of(new SFMTrajectoryContract.EvidenceRequirement(
                        "evidence-document", "document-hash", "record resulting document hash"
                )),
                new SFMTrajectoryContract.SearchBudget(32, 64, 2_000),
                8,
                SFMTrajectoryContract.ApprovalRequirement.HUMAN,
                SFMTrajectoryContract.SupervisionStatus.SUPERVISION_READY,
                Optional.empty()
        );
    }

    private static SFMTrajectoryContract.TrajectoryPlanRevision foundPlan(
            String id,
            Optional<String> parent
    ) {
        SFMHistoryGraphContract.ActionIntent intent = new SFMHistoryGraphContract.ActionIntent(
                "intent-route", "sfm:selection/replace/decimal-sequence",
                List.of("selection://hyphens"), "sha256:intent-route"
        );
        SFMTrajectoryContract.TrajectoryStep step = new SFMTrajectoryContract.TrajectoryStep(
                "step-1", "state-c", intent,
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                "outcome-projected", "state-projected",
                SFMHistoryGraphContract.EffectClass.PURE,
                2, 2, 0
        );
        SFMTrajectoryContract.TrajectoryRoute route = new SFMTrajectoryContract.TrajectoryRoute(
                "route-1", "state-c", List.of(step), 2,
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
        );
        return new SFMTrajectoryContract.TrajectoryPlanRevision(
                id,
                parent,
                "state-c",
                "supervision-v1",
                new SFMTrajectoryContract.ActionGeneratorIdentity("numbering-actions", "v1", true),
                new SFMTrajectoryContract.CostPolicyIdentity("semantic-first", "v1"),
                new SFMTrajectoryContract.HeuristicIdentity("zero", "v1", true),
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                new SFMTrajectoryContract.SearchBudget(32, 64, 2_000),
                SFMTrajectoryContract.PlanResult.FOUND,
                SFMTrajectoryContract.OptimalityClaim.MINIMUM_COST_UNDER_DECLARED_GRAPH_AND_POLICY,
                List.of(route),
                Optional.of(route.id()),
                List.of(
                        new SFMTrajectoryContract.SearchCandidate(
                                "state-c", 0, 0, SFMTrajectoryContract.SearchCandidateStatus.CLOSED,
                                Optional.empty(), Optional.empty(), "start expanded"
                        ),
                        new SFMTrajectoryContract.SearchCandidate(
                                "state-projected", 2, 0, SFMTrajectoryContract.SearchCandidateStatus.CLOSED,
                                Optional.of("state-c"), Optional.of(intent.id()), "goal reached"
                        )
                ),
                "minimum cost under finite generator numbering-actions@v1 and semantic-first@v1"
        );
    }

    private static SFMHistoryGraphContract.StateRevision state(
            String id,
            List<String> parents,
            boolean committed,
            SFMHistoryGraphContract.ProjectionStatus status
    ) {
        return new SFMHistoryGraphContract.StateRevision(
                id, parents, "sha256:" + id, committed, status
        );
    }

    private static SFMHistoryGraphContract.ActionEvaluation evaluation(
            String id,
            String intent,
            String parent,
            String outcome,
            SFMHistoryGraphContract.ProjectionStatus status
    ) {
        return new SFMHistoryGraphContract.ActionEvaluation(
                id, intent, parent, "fixture-evaluator-v1",
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                List.of(new SFMHistoryGraphContract.DependencyWitness(
                        "document", parent, "sha256:" + parent
                )),
                outcome,
                status
        );
    }

    private static SFMHistoryGraphContract.ActionOutcome outcome(
            String id,
            String evaluation,
            Optional<String> state,
            SFMHistoryGraphContract.OutcomeStatus status
    ) {
        return new SFMHistoryGraphContract.ActionOutcome(
                id, evaluation, status, state, List.of("fixture-evidence")
        );
    }

    private static SFMHistoryGraphContract.BranchEdge edge(
            String id,
            String parent,
            String child,
            Optional<String> intent,
            Optional<String> evaluation,
            Optional<String> outcome,
            SFMHistoryGraphContract.EffectClass effect,
            SFMHistoryGraphContract.ProjectionStatus status,
            boolean committed
    ) {
        return new SFMHistoryGraphContract.BranchEdge(
                id, parent, child, intent, evaluation, outcome, effect, status, committed
        );
    }
}
