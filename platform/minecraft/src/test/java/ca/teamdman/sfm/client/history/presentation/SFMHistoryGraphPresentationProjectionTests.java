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
                        "selected-executed-prefix",
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
        SFMHistoryGraphPresentationModel.Node frontier = nodeWithDetails(
                presentation,
                "search.plan-revision", "plan-1",
                "search.state-id", "frontier-open"
        );
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
        assertTrue(presentation.edges().stream().anyMatch(edge ->
                edge.sourceContractId().equals("step-prepare")
                        && edge.roles().contains(
                                SFMHistoryGraphPresentationModel.LegendRole.SELECTED_EXECUTED_PREFIX
                        )), "The selected route prefix must remain inspectable as machine progress");
        assertTrue(presentation.nodes().stream().allMatch(node -> !node.narration().isBlank()));
        assertTrue(presentation.edges().stream().allMatch(edge -> !edge.narration().isBlank()));
        assertTrue(presentation.markers().stream().allMatch(marker -> !marker.narration().isBlank()));
        SFMHistoryGraphPresentationModel.Edge nextStep = edge(presentation, pointer.subjectId());
        assertEquals("2", detail(nextStep.details(), "trajectory.g"));
        assertEquals("0", detail(nextStep.details(), "trajectory.h"));
        assertEquals("2", detail(nextStep.details(), "trajectory.f"));
        assertEquals("fixture-cost@v1", detail(nextStep.details(), "trajectory.cost-policy"));
        assertEquals("fixture-heuristic@v1", detail(nextStep.details(), "trajectory.heuristic"));
        assertEquals("fixture-generator@v1", detail(nextStep.details(), "trajectory.generator"));
        assertEquals("fixture candidate", detail(frontier.details(), "search.reason"));
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
    void retainedReplansKeepSharedSearchStateEvidencePlanScoped() {
        Fixture fixture = fixture();
        SFMTrajectoryContract.TrajectoryPlanRevision original = fixture.planBook().plans().get(0);
        SFMTrajectoryContract.TrajectoryPlanRevision first = new SFMTrajectoryContract.TrajectoryPlanRevision(
                original.id(),
                original.parentPlanRevisionId(),
                original.startStateId(),
                original.supervisionContractRevision(),
                original.actionGenerator(),
                original.costPolicy(),
                original.heuristic(),
                original.algorithm(),
                original.budget(),
                original.result(),
                original.optimalityClaim(),
                original.routes(),
                original.selectedRouteId(),
                append(original.exploredCandidates(), new SFMTrajectoryContract.SearchCandidate(
                        "shared-domain-state",
                        2,
                        5,
                        SFMTrajectoryContract.SearchCandidateStatus.OPEN,
                        Optional.of("plan-mid"),
                        Optional.of("intent-shared-first"),
                        "first-plan evidence"
                )),
                original.resultExplanation()
        );
        SFMTrajectoryContract.TrajectoryRoute secondRoute = new SFMTrajectoryContract.TrajectoryRoute(
                "route-second",
                "plan-start",
                List.of(step(
                        "step-second",
                        "plan-start",
                        "second-target",
                        intent("intent-second", "second"),
                        4
                )),
                4,
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
        );
        SFMTrajectoryContract.TrajectoryPlanRevision second = new SFMTrajectoryContract.TrajectoryPlanRevision(
                "plan-2",
                Optional.of(first.id()),
                "plan-start",
                "supervision-v2",
                new SFMTrajectoryContract.ActionGeneratorIdentity("other-generator", "v9", true),
                new SFMTrajectoryContract.CostPolicyIdentity("other-cost", "v4"),
                new SFMTrajectoryContract.HeuristicIdentity("other-heuristic", "v3", true),
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                new SFMTrajectoryContract.SearchBudget(64, 128, 2_000),
                SFMTrajectoryContract.PlanResult.FOUND,
                SFMTrajectoryContract.OptimalityClaim.NONE,
                List.of(secondRoute),
                Optional.of(secondRoute.id()),
                List.of(new SFMTrajectoryContract.SearchCandidate(
                        "shared-domain-state",
                        11,
                        0,
                        SFMTrajectoryContract.SearchCandidateStatus.CLOSED,
                        Optional.of("plan-mid"),
                        Optional.of("intent-shared-second"),
                        "second-plan evidence"
                )),
                "retained replan"
        );
        SFMTrajectoryContract.PlanBook retainedPlans = new SFMTrajectoryContract.PlanBook(
                SFMTrajectoryContract.SCHEMA,
                List.of(first, second),
                Optional.of(first.id())
        );

        SFMHistoryGraphPresentationModel.Presentation presentation =
                SFMHistoryGraphPresentationProjection.project(
                        fixture.history(),
                        retainedPlans,
                        fixture.machine()
                );
        List<SFMHistoryGraphPresentationModel.Node> shared = presentation.nodes().stream()
                .filter(node -> hasDetail(node.details(), "search.state-id", "shared-domain-state"))
                .toList();

        assertEquals(2, shared.size());
        assertNotEquals(shared.get(0).id(), shared.get(1).id());
        SFMHistoryGraphPresentationModel.Node firstEvidence = nodeWithDetails(
                presentation,
                "search.plan-revision", "plan-1",
                "search.state-id", "shared-domain-state"
        );
        SFMHistoryGraphPresentationModel.Node secondEvidence = nodeWithDetails(
                presentation,
                "search.plan-revision", "plan-2",
                "search.state-id", "shared-domain-state"
        );
        assertEquals("2", detail(firstEvidence.details(), "search.g"));
        assertEquals("5", detail(firstEvidence.details(), "search.h"));
        assertEquals("OPEN", detail(firstEvidence.details(), "search.status"));
        assertEquals("fixture-cost@v1", detail(firstEvidence.details(), "search.cost-policy"));
        assertEquals("fixture-generator@v1", detail(firstEvidence.details(), "search.generator"));
        assertEquals("fixture-heuristic@v1", detail(firstEvidence.details(), "search.heuristic"));
        assertEquals("A_STAR", detail(firstEvidence.details(), "search.algorithm"));
        assertEquals("11", detail(secondEvidence.details(), "search.g"));
        assertEquals("0", detail(secondEvidence.details(), "search.h"));
        assertEquals("CLOSED", detail(secondEvidence.details(), "search.status"));
        assertEquals("other-cost@v4", detail(secondEvidence.details(), "search.cost-policy"));
        assertEquals("other-generator@v9", detail(secondEvidence.details(), "search.generator"));
        assertEquals("other-heuristic@v3", detail(secondEvidence.details(), "search.heuristic"));
        assertEquals("DIJKSTRA", detail(secondEvidence.details(), "search.algorithm"));
        assertEquals(
                presentation,
                SFMHistoryGraphPresentationProjection.project(
                        fixture.history(),
                        retainedPlans,
                        fixture.machine()
                )
        );
    }

    @Test
    void executedPrefixAndSemanticEvidenceRemainDistinctAndInspectable() {
        Fixture fixture = fixture();
        SFMHistoryGraphPresentationModel.Presentation presentation =
                SFMHistoryGraphPresentationProjection.project(
                        fixture.history(),
                        fixture.planBook(),
                        fixture.machine()
                );

        SFMHistoryGraphPresentationModel.Edge executedPrefix = edgeBySource(presentation, "step-prepare");
        assertEquals(
                SFMHistoryGraphPresentationModel.EdgeCommitment.PROJECTED_CANDIDATE,
                executedPrefix.commitment()
        );
        assertTrue(executedPrefix.roles().contains(
                SFMHistoryGraphPresentationModel.LegendRole.SELECTED_EXECUTED_PREFIX));
        assertFalse(executedPrefix.roles().contains(
                SFMHistoryGraphPresentationModel.LegendRole.COMMITTED_EXECUTED));
        assertFalse(executedPrefix.narration().toLowerCase(java.util.Locale.ROOT).contains("committed"));
        assertEquals("EXECUTED_PREFIX", detail(executedPrefix.details(), "trajectory.machine-progress"));
        assertEquals("NOT_ASSERTED", detail(executedPrefix.details(), "trajectory.history-commitment"));
        assertEquals("plan-start", detail(executedPrefix.details(), "trajectory.pre-state"));
        assertEquals("plan-mid", detail(executedPrefix.details(), "trajectory.post-state"));

        SFMHistoryGraphPresentationModel.Edge projectedSuffix = edgeBySource(presentation, "step-finish");
        assertTrue(projectedSuffix.roles().contains(
                SFMHistoryGraphPresentationModel.LegendRole.SELECTED_PROJECTED_SUFFIX));
        assertEquals("PROJECTED_SUFFIX", detail(projectedSuffix.details(), "trajectory.machine-progress"));

        SFMHistoryGraphPresentationModel.Node intent = nodeWithDetail(
                presentation, "semantic.kind", "ACTION_INTENT");
        SFMHistoryGraphPresentationModel.Node evaluation = nodeWithDetail(
                presentation, "semantic.kind", "ACTION_EVALUATION");
        SFMHistoryGraphPresentationModel.Node outcome = nodeWithDetail(
                presentation, "semantic.kind", "ACTION_OUTCOME");
        assertEquals("sfm:fixture/history", detail(intent.details(), "intent.action-id"));
        assertEquals("alpha", detail(intent.details(), "intent.argument.000000"));
        assertEquals("two words", detail(intent.details(), "intent.argument.000001"));
        assertEquals(
                "sfm:fixture/history \"alpha\" \"two words\"",
                detail(intent.details(), "intent.query")
        );
        assertEquals("history.intents/history-intent", detail(intent.details(), "semantic.provenance"));
        assertEquals("fixture-evaluator@v7", detail(evaluation.details(), "evaluation.evaluator-revision"));
        assertEquals("state-root", detail(evaluation.details(), "evaluation.expected-parent-state"));
        assertEquals("FROZEN_WITNESS_REEXECUTION", detail(evaluation.details(), "evaluation.policy"));
        assertEquals("classpath", detail(evaluation.details(), "evaluation.witness.000000.kind"));
        assertEquals("fixture.jar", detail(evaluation.details(), "evaluation.witness.000000.identity"));
        assertEquals("sha256:fixture", detail(evaluation.details(), "evaluation.witness.000000.revision"));
        assertEquals("history-outcome", detail(evaluation.details(), "evaluation.predicted-outcome-id"));
        assertEquals("SUCCEEDED", detail(outcome.details(), "outcome.status"));
        assertEquals("state-live", detail(outcome.details(), "outcome.resulting-state"));
        assertEquals("alpha evidence", detail(outcome.details(), "outcome.evidence.000000"));
        assertEquals("zeta evidence", detail(outcome.details(), "outcome.evidence.000001"));

        List<SFMHistoryGraphPresentationModel.Edge> semanticRelations = presentation.edges().stream()
                .filter(edge -> edge.origin() == SFMHistoryGraphPresentationModel.EdgeOrigin.SEMANTIC)
                .filter(edge -> edge.sourceContractId().equals("edge-root-live"))
                .toList();
        assertEquals(4, semanticRelations.size());
        assertSemanticRelation(presentation, "state-root", intent.id());
        assertSemanticRelation(presentation, intent.id(), evaluation.id());
        assertSemanticRelation(presentation, evaluation.id(), outcome.id());
        SFMHistoryGraphPresentationModel.Edge finalRelation =
                assertSemanticRelation(presentation, outcome.id(), "state-live");
        assertEquals(
                SFMHistoryGraphPresentationModel.EdgeCommitment.SEMANTIC_RELATION,
                finalRelation.commitment()
        );
        assertEquals("state-root", detail(finalRelation.details(), "history.pre-state"));
        assertEquals("state-live", detail(finalRelation.details(), "history.post-state"));
        assertEquals("PURE", detail(finalRelation.details(), "history.effect-class"));
        assertEquals("history.edges/edge-root-live", detail(finalRelation.details(), "semantic.provenance"));
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
        SFMHistoryGraphContract.ActionIntent historyIntent = new SFMHistoryGraphContract.ActionIntent(
                "history-intent",
                "sfm:fixture/history",
                List.of("alpha", "two words"),
                "sha256:history-intent"
        );
        SFMHistoryGraphContract.ActionEvaluation historyEvaluation =
                new SFMHistoryGraphContract.ActionEvaluation(
                        "history-evaluation",
                        historyIntent.id(),
                        "state-root",
                        "fixture-evaluator@v7",
                        SFMHistoryGraphContract.EvaluationPolicy.FROZEN_WITNESS_REEXECUTION,
                        List.of(
                                new SFMHistoryGraphContract.DependencyWitness(
                                        "workspace",
                                        "fixture-workspace",
                                        "revision-42"
                                ),
                                new SFMHistoryGraphContract.DependencyWitness(
                                        "classpath",
                                        "fixture.jar",
                                        "sha256:fixture"
                                )
                        ),
                        "history-outcome",
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
                );
        SFMHistoryGraphContract.ActionOutcome historyOutcome = new SFMHistoryGraphContract.ActionOutcome(
                "history-outcome",
                historyEvaluation.id(),
                SFMHistoryGraphContract.OutcomeStatus.SUCCEEDED,
                Optional.of("state-live"),
                List.of("zeta evidence", "alpha evidence")
        );
        SFMHistoryGraphContract.Graph history = new SFMHistoryGraphContract.Graph(
                SFMHistoryGraphContract.SCHEMA,
                List.of(historyIntent),
                List.of(historyEvaluation),
                List.of(historyOutcome),
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
                        new SFMHistoryGraphContract.BranchEdge(
                                "edge-root-live",
                                "state-root",
                                "state-live",
                                Optional.of(historyIntent.id()),
                                Optional.of(historyEvaluation.id()),
                                Optional.of(historyOutcome.id()),
                                SFMHistoryGraphContract.EffectClass.PURE,
                                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                                true
                        ),
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

    private static <T> List<T> append(List<T> values, T value) {
        java.util.ArrayList<T> answer = new java.util.ArrayList<>(values);
        answer.add(value);
        return List.copyOf(answer);
    }

    private static SFMHistoryGraphPresentationModel.Node nodeWithDetail(
            SFMHistoryGraphPresentationModel.Presentation presentation,
            String key,
            String value
    ) {
        return presentation.nodes().stream()
                .filter(node -> hasDetail(node.details(), key, value))
                .findFirst()
                .orElseThrow();
    }

    private static SFMHistoryGraphPresentationModel.Node nodeWithDetails(
            SFMHistoryGraphPresentationModel.Presentation presentation,
            String firstKey,
            String firstValue,
            String secondKey,
            String secondValue
    ) {
        return presentation.nodes().stream()
                .filter(node -> hasDetail(node.details(), firstKey, firstValue))
                .filter(node -> hasDetail(node.details(), secondKey, secondValue))
                .findFirst()
                .orElseThrow();
    }

    private static SFMHistoryGraphPresentationModel.Edge edgeBySource(
            SFMHistoryGraphPresentationModel.Presentation presentation,
            String sourceContractId
    ) {
        return presentation.edges().stream()
                .filter(edge -> edge.sourceContractId().equals(sourceContractId))
                .filter(edge -> edge.origin() != SFMHistoryGraphPresentationModel.EdgeOrigin.SEMANTIC)
                .findFirst()
                .orElseThrow();
    }

    private static SFMHistoryGraphPresentationModel.Edge assertSemanticRelation(
            SFMHistoryGraphPresentationModel.Presentation presentation,
            String from,
            String to
    ) {
        SFMHistoryGraphPresentationModel.Edge answer = presentation.edges().stream()
                .filter(edge -> edge.origin() == SFMHistoryGraphPresentationModel.EdgeOrigin.SEMANTIC)
                .filter(edge -> edge.fromNodeId().equals(from) && edge.toNodeId().equals(to))
                .findFirst()
                .orElseThrow();
        assertEquals(SFMHistoryGraphPresentationModel.EdgeCommitment.SEMANTIC_RELATION, answer.commitment());
        return answer;
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

    private static String detail(List<SFMHistoryGraphPresentationModel.Detail> details, String key) {
        return details.stream()
                .filter(detail -> detail.key().equals(key))
                .map(SFMHistoryGraphPresentationModel.Detail::value)
                .findFirst()
                .orElseThrow();
    }

    private static boolean hasDetail(
            List<SFMHistoryGraphPresentationModel.Detail> details,
            String key,
            String value
    ) {
        return details.stream().anyMatch(detail -> detail.key().equals(key) && detail.value().equals(value));
    }

    private record Fixture(
            SFMHistoryGraphContract.Graph history,
            SFMTrajectoryContract.PlanBook planBook,
            SFMTrajectoryContract.TrajectoryMachineState machine
    ) {
    }
}
