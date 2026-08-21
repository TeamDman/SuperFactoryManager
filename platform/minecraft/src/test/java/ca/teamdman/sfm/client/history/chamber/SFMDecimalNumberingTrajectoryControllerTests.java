package ca.teamdman.sfm.client.history.chamber;

import ca.teamdman.sfm.client.history.SFMBoundedTrajectoryPlanner;
import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDecimalNumberingTrajectoryControllerTests {
    @Test
    void candidateFramesAreRandomSeekableReadOnlyAndOldPlanFramesSurviveReplan() {
        SFMDecimalNumberingTrajectoryController controller =
                controller("sfm:test/candidate-history");
        controller.apply(new SFMHistoryGraphRuntime.Plan());
        SFMHistoryGraphRuntime.MachineSnapshot planned = controller.snapshot();
        String firstPlan = planned.machine().selectedTrajectoryRevisionId().orElseThrow();
        SFMTrajectoryContract.TrajectoryRoute firstRoute = selectedRoute(planned, firstPlan);
        long revisionBeforeProjection = controller.revision();
        String headBeforeProjection = controller.currentState().revisionId();
        SFMTrajectoryContract.InstructionPointer pointerBeforeProjection =
                planned.machine().instructionPointer().orElseThrow();

        SFMCandidateHistoryContract.CandidateRouteProjection firstProjection =
                controller.projectCandidateRoute(firstPlan, firstRoute.id());
        assertEquals(List.of(
                        "1. apples\n2. bananas\n",
                        SFMDecimalNumberingTrajectoryController.INITIAL_TEXT,
                        SFMDecimalNumberingTrajectoryController.INITIAL_TEXT
                ),
                List.of(2, 0, 1).stream()
                        .map(firstProjection::frame)
                        .map(frame -> frame.document().orElseThrow().text())
                        .toList());
        assertEquals(List.of(0, 1, 2), firstProjection.frames().stream()
                .map(frame -> frame.address().routeStepPosition())
                .toList());
        assertEquals(revisionBeforeProjection, controller.revision());
        assertEquals(headBeforeProjection, controller.currentState().revisionId());
        assertEquals(pointerBeforeProjection, controller.snapshot().machine().instructionPointer().orElseThrow());

        controller.apply(new SFMHistoryGraphRuntime.Run(8));
        controller.undo("test", "candidate-undo");
        int insertionOffset = "- apples\n".codePointCount(0, "- apples\n".length());
        controller.insertText(
                insertionOffset,
                SFMDecimalNumberingTrajectoryController.THIRD_ITEM_TEXT,
                "test"
        );
        controller.apply(new SFMHistoryGraphRuntime.Replan());
        String secondPlan = controller.snapshot().machine().selectedTrajectoryRevisionId().orElseThrow();
        assertNotEquals(firstPlan, secondPlan);

        SFMCandidateHistoryContract.CandidateRouteProjection retainedFirstProjection =
                controller.projectCandidateRoute(firstPlan, firstRoute.id());
        assertEquals(firstProjection.frames(), retainedFirstProjection.frames(),
                "replanning must not rewrite or discard old candidate frames");
        SFMTrajectoryContract.TrajectoryRoute secondRoute = selectedRoute(controller.snapshot(), secondPlan);
        SFMCandidateHistoryContract.CandidateRouteProjection secondProjection =
                controller.projectCandidateRoute(secondPlan, secondRoute.id());
        assertEquals("1. apples\n2. apricots\n3. bananas\n",
                secondProjection.frame(secondProjection.lastPosition()).document().orElseThrow().text());
        assertEquals(2, secondProjection.lastPosition());
    }

    @Test
    void aStarAndDijkstraAgreeWithExplicitCatalogProofAndBoundedGraphOracle() {
        Fixture fixture = fixture("oracle");
        SFMDecimalNumberingPlannerAdapter.PlanningBundle aStar = plan(
                fixture,
                "a-star",
                SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                fixture.supervision()
        );
        SFMDecimalNumberingPlannerAdapter.PlanningBundle dijkstra = plan(
                fixture,
                "dijkstra",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                fixture.supervision()
        );

        assertEquals(SFMTrajectoryContract.PlanResult.FOUND, aStar.result().plan().result());
        assertEquals(SFMTrajectoryContract.PlanResult.FOUND, dijkstra.result().plan().result());
        long explicitSemanticRouteCost = Math.addExact(
                fixture.chamber().costPolicy().semanticSelectionCost(),
                fixture.chamber().costPolicy().semanticDecimalReplacementCost()
        );
        assertEquals(2, explicitSemanticRouteCost);
        assertTrue(fixture.chamber().costPolicy().literalBaseCost() > explicitSemanticRouteCost,
                "the independently declared cost policy proves any literal-first route is more expensive");
        assertEquals(explicitSemanticRouteCost, selectedRoute(aStar).totalCost());
        assertEquals(selectedRoute(aStar).totalCost(), selectedRoute(dijkstra).totalCost());
        assertEquals(
                exhaustiveMinimumCost(
                        fixture.chamber(),
                        fixture.initial(),
                        fixture.target(),
                        Math.toIntExact(fixture.supervision().contract().maxExecutionSteps())
                ),
                selectedRoute(aStar).totalCost()
        );
        assertEquals(2, selectedRoute(aStar).steps().size());
        assertEquals(List.of(
                        SFMDecimalNumberingChamber.SELECT_ALL_HYPHENS_ACTION_ID,
                        SFMDecimalNumberingChamber.REPLACE_DECIMAL_SEQUENCE_ACTION_ID
                ),
                selectedRoute(aStar).steps().stream()
                        .map(step -> step.actionIntent().actionId())
                        .toList(),
                "the oracle names the expected semantic actions rather than deriving them from the generator");
        assertEquals(2, aStar.manifest().stepsById().size());
        selectedRoute(aStar).steps().forEach(step -> aStar.manifest().requireStep(step));
        SFMDecimalNumberingPlannerAdapter.ManifestStep first = aStar.manifest()
                .requireStep(selectedRoute(aStar).steps().get(0));
        SFMDecimalNumberingPlannerAdapter.ManifestStep second = aStar.manifest()
                .requireStep(selectedRoute(aStar).steps().get(1));
        assertTrue(first.executableTransition().action()
                instanceof SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction);
        assertEquals(SFMDecimalNumberingTrajectoryController.INITIAL_TEXT,
                first.executableTransition().result().text());
        assertEquals(2, first.executableTransition().result().selection().orElseThrow().regions().size());
        assertTrue(second.executableTransition().action()
                instanceof SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction);
        assertEquals("1. apples\n2. bananas\n", second.executableTransition().result().text());
        assertTrue(second.executableTransition().result().selection().isEmpty());
        assertThrows(IllegalStateException.class,
                () -> aStar.manifest().requireStep(selectedRoute(dijkstra).steps().get(0)),
                "a manifest must not execute a foreign plan's step");
        assertTrue(aStar.result().plan().optimalityClaim()
                == SFMTrajectoryContract.OptimalityClaim.MINIMUM_COST_UNDER_DECLARED_GRAPH_AND_POLICY);
    }

    @Test
    void cancellationAndBudgetExhaustionRemainInspectableNonSuccesses() {
        Fixture fixture = fixture("boundaries");
        SFMDecimalNumberingPlannerAdapter.PlanningBundle cancelled = plan(
                fixture,
                "cancelled",
                SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                () -> true,
                fixture.supervision()
        );
        assertEquals(SFMTrajectoryContract.PlanResult.CANCELLED, cancelled.result().plan().result());
        assertTrue(cancelled.result().terminalReasons().contains(
                SFMBoundedTrajectoryPlanner.TerminalReason.CANCELLED));
        assertTrue(cancelled.manifest().stepsById().isEmpty());

        SFMTrajectoryContract.SupervisionContract original = fixture.supervision().contract();
        SFMTrajectoryContract.SupervisionContract boundedContract = new SFMTrajectoryContract.SupervisionContract(
                original.id(),
                original.revision() + "/zero-expansion-budget",
                original.startDomainId(),
                original.startStateRevisionId(),
                original.goalPredicates(),
                original.hardInvariants(),
                original.forbiddenEffectClasses(),
                original.requiredEvidence(),
                new SFMTrajectoryContract.SearchBudget(0, 64, 5_000),
                original.maxExecutionSteps(),
                original.approvalRequirement(),
                original.status(),
                Optional.empty()
        );
        SFMDecimalNumberingChamber.SupervisionDefinition boundedDefinition =
                fixture.chamber().supervision(
                        fixture.initial(),
                        fixture.target(),
                        boundedContract.searchBudget()
                );
        SFMDecimalNumberingPlannerAdapter.PlanningBundle exhausted = plan(
                fixture,
                "exhausted",
                SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                boundedDefinition
        );
        assertEquals(SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED, exhausted.result().plan().result());
        assertTrue(exhausted.result().terminalReasons().contains(
                SFMBoundedTrajectoryPlanner.TerminalReason.EXPANSION_BUDGET_EXHAUSTED));
        assertTrue(exhausted.manifest().stepsById().isEmpty());
    }

    @Test
    void exactTwoBranchJourneyRetainsOldPlanAndReachesThreeItemSupervisionReady() {
        SFMDecimalNumberingTrajectoryController controller =
                controller("sfm:test/episode-journey");

        assertEquals(SFMHistoryGraphRuntime.OperationStatus.APPLIED,
                controller.apply(new SFMHistoryGraphRuntime.Plan()).status());
        String firstPlan = controller.snapshot().machine().selectedTrajectoryRevisionId().orElseThrow();
        assertEquals(2, selectedRoute(controller.snapshot(), firstPlan).totalCost());

        controller.apply(new SFMHistoryGraphRuntime.Step());
        SFMChamberDocumentState selected = controller.currentState();
        assertTrue(selected.selection().isPresent());
        assertEquals(1, controller.snapshot().machine().instructionPointer().orElseThrow().nextStepIndex());

        controller.apply(new SFMHistoryGraphRuntime.Run(8));
        SFMChamberDocumentState numbered = controller.currentState();
        assertEquals("1. apples\n2. bananas\n", numbered.text());
        assertEquals(SFMTrajectoryContract.MachineStatus.COMPLETE, controller.snapshot().machine().status());
        assertEquals(SFMTrajectoryContract.SupervisionStatus.SUPERVISION_READY,
                controller.snapshot().supervision().orElseThrow().status());
        assertTrue(controller.snapshot().supervision().orElseThrow().approval().isEmpty());

        controller.undo("test", "undo-numbered");
        assertEquals(selected.revisionId(), controller.currentState().revisionId());
        int insertionOffset = "- apples\n".codePointCount(0, "- apples\n".length());
        controller.insertText(insertionOffset, SFMDecimalNumberingTrajectoryController.THIRD_ITEM_TEXT, "test");
        SFMChamberDocumentState inserted = controller.currentState();
        assertEquals(selected.revisionId(), inserted.parentRevisionId().orElseThrow());
        assertEquals(List.of(inserted.revisionId(), numbered.revisionId()).stream().sorted().toList(),
                controller.childRevisionIds(selected.revisionId()));

        controller.apply(new SFMHistoryGraphRuntime.Replan());
        String secondPlan = controller.snapshot().machine().selectedTrajectoryRevisionId().orElseThrow();
        assertNotEquals(firstPlan, secondPlan);
        assertEquals(2, controller.snapshot().planBook().plans().size());
        assertTrue(controller.snapshot().planBook().plans().stream().anyMatch(plan -> plan.id().equals(firstPlan)));
        assertEquals(firstPlan, controller.replanLineage().get(0).sourcePlanRevisionId());
        assertEquals(secondPlan, controller.replanLineage().get(0).resultingPlanRevisionId());

        controller.apply(new SFMHistoryGraphRuntime.Run(8));
        assertEquals("1. apples\n2. apricots\n3. bananas\n", controller.currentText());
        assertEquals(SFMTrajectoryContract.SupervisionStatus.SUPERVISION_READY,
                controller.snapshot().supervision().orElseThrow().status());
        assertTrue(controller.snapshot().history().states().stream()
                .anyMatch(state -> state.id().equals(numbered.revisionId())));
        assertTrue(controller.snapshot().history().edges().stream()
                .anyMatch(edge -> edge.parentStateRevisionId().equals(selected.revisionId())
                        && edge.childStateRevisionId().equals(numbered.revisionId())));
        assertEquals(controller.currentState().revisionId(), controller.snapshot().history().heads().get(0).stateRevisionId());

        String firstRoute = selectedRoute(controller.snapshot(), firstPlan).id();
        controller.undo("test", "undo-three-item-numbered");
        controller.undo("test", "undo-three-item-selection");
        assertEquals(SFMHistoryGraphRuntime.OperationStatus.REJECTED,
                controller.apply(new SFMHistoryGraphRuntime.SelectRoute(firstPlan, firstRoute)).status(),
                "an unrelated retained route must not be exposed as executable");
        controller.undo("test", "undo-third-item-insert");
        assertEquals(selected.revisionId(), controller.currentState().revisionId());
        assertEquals(SFMHistoryGraphRuntime.OperationStatus.APPLIED,
                controller.apply(new SFMHistoryGraphRuntime.SelectRoute(firstPlan, firstRoute)).status());
        controller.apply(new SFMHistoryGraphRuntime.Run(8));
        assertEquals("1. apples\n2. bananas\n", controller.currentText());
        assertEquals(SFMTrajectoryContract.SupervisionStatus.SUPERVISION_READY,
                controller.snapshot().supervision().orElseThrow().status(),
                "a retained plan must recover its own supervision target and contract");
    }

    @Test
    void failingPostStepMeasurementCannotPartiallyCommitTheAggregate() {
        AtomicInteger measurements = new AtomicInteger();
        SFMDecimalNumberingTrajectoryController controller = new SFMDecimalNumberingTrajectoryController(
                "sfm:test/episode-atomic",
                "document-1",
                SFMDecimalNumberingTrajectoryController.INITIAL_TEXT,
                () -> {
                    if (measurements.incrementAndGet() >= 3) {
                        throw new IllegalStateException("ambient probe failed");
                    }
                    return "sha256:test-owned-ambient-checkout";
                }
        );
        controller.apply(new SFMHistoryGraphRuntime.Plan());
        controller.apply(new SFMHistoryGraphRuntime.Step());
        SFMChamberDocumentState before = controller.currentState();
        SFMHistoryGraphRuntime.MachineSnapshot snapshotBefore = controller.snapshot();

        assertThrows(IllegalStateException.class,
                () -> controller.apply(new SFMHistoryGraphRuntime.Step()));
        assertEquals(before, controller.currentState());
        assertEquals(snapshotBefore.revision(), controller.snapshot().revision());
        assertEquals(snapshotBefore.machine(), controller.snapshot().machine());
        assertEquals(snapshotBefore.history(), controller.snapshot().history());
    }

    @Test
    void staleStepPausesWithoutExecutingAndReportsRecoveryChoices() {
        SFMDecimalNumberingTrajectoryController controller =
                controller("sfm:test/episode-stale");
        controller.apply(new SFMHistoryGraphRuntime.Plan());
        int offset = "- apples\n".codePointCount(0, "- apples\n".length());
        controller.insertText(offset, SFMDecimalNumberingTrajectoryController.THIRD_ITEM_TEXT, "test");
        String changedState = controller.currentState().revisionId();
        int stateCount = controller.snapshot().history().states().size();

        SFMHistoryGraphRuntime.OperationResult result = controller.apply(new SFMHistoryGraphRuntime.Step());
        assertEquals(SFMHistoryGraphRuntime.OperationStatus.APPLIED, result.status());
        assertTrue(result.message().contains("frozen witness"));
        assertTrue(result.message().contains("semantic re-evaluation"));
        assertTrue(result.message().contains("Replan"));
        assertEquals(SFMTrajectoryContract.MachineStatus.STALE_PRECONDITION,
                controller.snapshot().machine().status());
        assertEquals(changedState, controller.currentState().revisionId());
        assertEquals(stateCount, controller.snapshot().history().states().size(),
                "stale validation must not materialize a projected transition");
    }

    @Test
    void changedAmbientMeasurementBlocksPlanningAndCanonicalSupervisionRejectsForgedTarget() {
        AtomicReference<String> ambient = new AtomicReference<>("sha256:checkout-a");
        SFMDecimalNumberingTrajectoryController controller = new SFMDecimalNumberingTrajectoryController(
                "sfm:test/episode-ambient",
                "document-1",
                SFMDecimalNumberingTrajectoryController.INITIAL_TEXT,
                ambient::get
        );
        assertTrue(controller.measureCheckoutEvidence().unchanged());
        ambient.set("sha256:checkout-b");
        SFMDecimalNumberingTrajectoryController.CheckoutEvidence changed =
                controller.measureCheckoutEvidence();
        assertFalse(changed.unchanged());
        assertNotEquals(changed.beforeHash(), changed.afterHash());
        assertEquals(SFMHistoryGraphRuntime.OperationStatus.REJECTED,
                controller.apply(new SFMHistoryGraphRuntime.Plan()).status());
        assertEquals(SFMTrajectoryContract.MachineStatus.BLOCKED, controller.snapshot().machine().status());

        Fixture fixture = fixture("forged-target");
        SFMDecimalNumberingChamber.Target canonical = fixture.target();
        SFMDecimalNumberingChamber.Target forged = new SFMDecimalNumberingChamber.Target(
                canonical.scope(),
                canonical.id() + "/forged",
                canonical.sourceRevisionId(),
                canonical.sourceStateHash(),
                canonical.sourceTextHash(),
                canonical.expectedText(),
                canonical.expectedTextHash(),
                List.of(
                        new SFMDecimalNumberingChamber.TargetReplacement(
                                1,
                                canonical.replacements().get(0).sourceRegion(),
                                "99."
                        ),
                        canonical.replacements().get(1)
                )
        );
        assertThrows(IllegalArgumentException.class,
                () -> fixture.chamber().supervision(fixture.initial(), forged));
    }

    private static SFMDecimalNumberingPlannerAdapter.PlanningBundle plan(
            Fixture fixture,
            String suffix,
            SFMTrajectoryContract.SearchAlgorithm algorithm,
            SFMBoundedTrajectoryPlanner.CancellationToken cancellation,
            SFMDecimalNumberingChamber.SupervisionDefinition supervision
    ) {
        return SFMDecimalNumberingPlannerAdapter.plan(
                fixture.chamber(),
                fixture.initial(),
                fixture.target(),
                supervision,
                invariantEvidence(fixture.initial()),
                fixture.scope().qualify("plan", suffix),
                Optional.empty(),
                fixture.scope().qualify("route", suffix),
                algorithm,
                cancellation,
                SFMBoundedTrajectoryPlanner.NanoClock.system()
        );
    }

    private static long exhaustiveMinimumCost(
            SFMDecimalNumberingChamber chamber,
            SFMChamberDocumentState start,
            SFMDecimalNumberingChamber.Target target,
            int maxDepth
    ) {
        record Node(SFMChamberDocumentState state, long cost, int depth) {
        }
        ArrayDeque<Node> pending = new ArrayDeque<>();
        pending.add(new Node(start, 0, 0));
        long best = Long.MAX_VALUE;
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            if (target.matches(node.state())) {
                best = Math.min(best, node.cost());
                continue;
            }
            if (node.depth() >= maxDepth || node.cost() >= best) continue;
            for (SFMDecimalNumberingChamber.Transition transition : chamber.transitions(node.state(), target)) {
                pending.addLast(new Node(
                        transition.result(),
                        Math.addExact(node.cost(), transition.action().cost()),
                        node.depth() + 1
                ));
            }
        }
        if (best == Long.MAX_VALUE) throw new AssertionError("bounded exhaustive oracle found no route");
        return best;
    }

    private static SFMTrajectoryContract.TrajectoryRoute selectedRoute(
            SFMDecimalNumberingPlannerAdapter.PlanningBundle bundle
    ) {
        SFMTrajectoryContract.TrajectoryPlanRevision plan = bundle.result().plan();
        String routeId = plan.selectedRouteId().orElseThrow();
        return plan.routes().stream().filter(route -> route.id().equals(routeId)).findFirst().orElseThrow();
    }

    private static SFMTrajectoryContract.TrajectoryRoute selectedRoute(
            SFMHistoryGraphRuntime.MachineSnapshot snapshot,
            String planId
    ) {
        SFMTrajectoryContract.TrajectoryPlanRevision plan = snapshot.planBook().plans().stream()
                .filter(value -> value.id().equals(planId))
                .findFirst()
                .orElseThrow();
        String routeId = plan.selectedRouteId().orElseThrow();
        return plan.routes().stream().filter(route -> route.id().equals(routeId)).findFirst().orElseThrow();
    }

    private static Fixture fixture(String suffix) {
        SFMChamberDocumentState.IdentityScope scope = new SFMChamberDocumentState.IdentityScope(
                "sfm:test/episode-" + suffix,
                "document-1"
        );
        SFMDecimalNumberingChamber chamber = new SFMDecimalNumberingChamber(scope);
        SFMChamberDocumentState initial = SFMChamberDocumentState.root(
                scope,
                SFMDecimalNumberingTrajectoryController.INITIAL_TEXT
        );
        SFMDecimalNumberingChamber.Target target = chamber.requireTarget(initial);
        return new Fixture(scope, chamber, initial, target, chamber.supervision(initial, target));
    }

    private static SFMDecimalNumberingTrajectoryController controller(String episodeId) {
        return new SFMDecimalNumberingTrajectoryController(
                episodeId,
                "document-1",
                SFMDecimalNumberingTrajectoryController.INITIAL_TEXT,
                () -> "sha256:test-owned-ambient-checkout"
        );
    }

    private static SFMDecimalNumberingChamber.ExternalInvariantEvidence invariantEvidence(
            SFMChamberDocumentState... states
    ) {
        Map<String, String> retained = new HashMap<>();
        for (SFMChamberDocumentState state : states) retained.put(state.revisionId(), state.stateHash());
        return new SFMDecimalNumberingChamber.ExternalInvariantEvidence(
                "sha256:checkout",
                "sha256:checkout",
                retained,
                retained
        );
    }

    private record Fixture(
            SFMChamberDocumentState.IdentityScope scope,
            SFMDecimalNumberingChamber chamber,
            SFMChamberDocumentState initial,
            SFMDecimalNumberingChamber.Target target,
            SFMDecimalNumberingChamber.SupervisionDefinition supervision
    ) {
    }
}
