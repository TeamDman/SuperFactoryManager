package ca.teamdman.sfm.client.history;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMBoundedTrajectoryPlannerTests {
    private static final SFMTrajectoryContract.SearchBudget LARGE_BUDGET =
            new SFMTrajectoryContract.SearchBudget(1_000, 1_000, 1_000);

    @Test
    void astarAndDijkstraAgreeWithAnExhaustiveOracleAcrossBoundedFiniteGraphs() {
        List<String> nodes = List.of("s", "a", "b", "g");
        List<RawEdge> possibleEdges = List.of(
                new RawEdge("s", "a", 0, "a"),
                new RawEdge("s", "b", 2, "b"),
                new RawEdge("s", "g", 4, "c"),
                new RawEdge("a", "b", 1, "d"),
                new RawEdge("a", "g", 3, "e"),
                new RawEdge("b", "g", 1, "f")
        );

        for (int mask = 0; mask < (1 << possibleEdges.size()); mask++) {
            Map<String, List<RawEdge>> graph = new HashMap<>();
            for (int edgeIndex = 0; edgeIndex < possibleEdges.size(); edgeIndex++) {
                if ((mask & (1 << edgeIndex)) == 0) continue;
                RawEdge edge = possibleEdges.get(edgeIndex);
                graph.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
            }
            Optional<Long> expected = exhaustiveMinimumCost(graph, "s", "g", new HashSet<>());

            var dijkstra = SFMBoundedTrajectoryPlanner.plan(request(
                    "dijkstra-" + mask,
                    Optional.empty(),
                    graph,
                    "s",
                    "g",
                    SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                    ignored -> 0,
                    LARGE_BUDGET,
                    Set.of(),
                    Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                    SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                    () -> 0
            ));
            var astar = SFMBoundedTrajectoryPlanner.plan(request(
                    "astar-" + mask,
                    Optional.empty(),
                    graph,
                    "s",
                    "g",
                    SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                    ignored -> 0,
                    LARGE_BUDGET,
                    Set.of(),
                    Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                    SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                    () -> 0
            ));

            if (expected.isPresent()) {
                assertEquals(SFMTrajectoryContract.PlanResult.FOUND, dijkstra.plan().result(), "mask=" + mask);
                assertEquals(SFMTrajectoryContract.PlanResult.FOUND, astar.plan().result(), "mask=" + mask);
                assertEquals(expected.orElseThrow().longValue(), selectedCost(dijkstra), "mask=" + mask);
                assertEquals(expected.orElseThrow().longValue(), selectedCost(astar), "mask=" + mask);
                assertEquals(
                        SFMTrajectoryContract.OptimalityClaim.MINIMUM_COST_UNDER_DECLARED_GRAPH_AND_POLICY,
                        astar.plan().optimalityClaim()
                );
            } else {
                assertEquals(SFMTrajectoryContract.PlanResult.EXHAUSTED, dijkstra.plan().result(), "mask=" + mask);
                assertEquals(SFMTrajectoryContract.PlanResult.EXHAUSTED, astar.plan().result(), "mask=" + mask);
            }
        }
        assertEquals(List.of("s", "a", "b", "g"), nodes);
    }

    @Test
    void cyclicZeroCostGraphsAgreeWithIndependentBellmanFordOracleAndAdmissibleHeuristics() {
        List<String> nodes = List.of("s", "a", "b", "c", "g");
        boolean observedInconsistentHeuristic = false;
        for (int seed = 0; seed < 32; seed++) {
            Map<String, List<RawEdge>> graph = new HashMap<>();
            for (int from = 0; from < nodes.size(); from++) {
                for (int to = 0; to < nodes.size(); to++) {
                    if (from == to) continue;
                    int selector = seed * 37 + from * 11 + to * 17;
                    if (Math.floorMod(selector, 5) >= 2) continue;
                    RawEdge edge = new RawEdge(
                            nodes.get(from),
                            nodes.get(to),
                            Math.floorMod(seed + from * 3 + to * 5, 4),
                            "edge-" + from + "-" + to
                    );
                    graph.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
                }
            }
            Map<String, Long> trueDistances = bellmanFordDistancesToGoal(nodes, graph, "g");
            Map<String, Long> heuristic = new HashMap<>();
            for (int index = 0; index < nodes.size(); index++) {
                long distance = trueDistances.getOrDefault(nodes.get(index), 0L);
                heuristic.put(nodes.get(index), ((seed + index) & 1) == 0 ? distance : 0L);
            }
            observedInconsistentHeuristic |= isInconsistent(graph, heuristic);

            var dijkstra = SFMBoundedTrajectoryPlanner.plan(request(
                    "cyclic-dijkstra-" + seed,
                    Optional.empty(),
                    graph,
                    "s",
                    "g",
                    SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                    ignored -> 0,
                    LARGE_BUDGET,
                    Set.of(),
                    Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                    SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                    () -> 0
            ));
            var astar = SFMBoundedTrajectoryPlanner.plan(request(
                    "cyclic-astar-" + seed,
                    Optional.empty(),
                    graph,
                    "s",
                    "g",
                    SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                    state -> heuristic.getOrDefault(state.id(), 0L),
                    LARGE_BUDGET,
                    Set.of(),
                    Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                    SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                    () -> 0
            ));

            Long expected = trueDistances.get("s");
            if (expected == null) {
                assertEquals(SFMTrajectoryContract.PlanResult.EXHAUSTED, dijkstra.plan().result(), "seed=" + seed);
                assertEquals(SFMTrajectoryContract.PlanResult.EXHAUSTED, astar.plan().result(), "seed=" + seed);
            } else {
                assertEquals(expected.longValue(), selectedCost(dijkstra), "seed=" + seed);
                assertEquals(expected.longValue(), selectedCost(astar), "seed=" + seed);
            }
        }
        assertTrue(observedInconsistentHeuristic, "the bounded corpus must exercise an admissible inconsistent heuristic");
    }

    @Test
    void stableOrderingAndStepCountRejectZeroCostCycleDetours() {
        Map<String, List<RawEdge>> graph = Map.of(
                "s", List.of(
                        new RawEdge("s", "a", 0, "z-first-in-input"),
                        new RawEdge("s", "b", 0, "a-canonical")
                ),
                "a", List.of(new RawEdge("a", "g", 1, "a-finish")),
                "b", List.of(
                        new RawEdge("b", "b", 0, "0-loop"),
                        new RawEdge("b", "g", 1, "b-finish")
                )
        );

        var result = SFMBoundedTrajectoryPlanner.plan(request(
                "tie-plan",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));

        assertEquals(1, selectedCost(result));
        assertEquals(
                List.of("test:a-canonical", "test:b-finish"),
                selectedRoute(result).steps().stream().map(step -> step.actionIntent().actionId()).toList()
        );
        assertEquals(2, selectedRoute(result).steps().size());
    }

    @Test
    void admissibleNonZeroAStarRemainsOptimalForAnInconsistentHeuristic() {
        Map<String, List<RawEdge>> graph = Map.of(
                "s", List.of(
                        new RawEdge("s", "a", 2, "to-a"),
                        new RawEdge("s", "b", 1, "to-b")
                ),
                "a", List.of(new RawEdge("a", "g", 2, "a-to-g")),
                "b", List.of(
                        new RawEdge("b", "a", 0, "b-to-a"),
                        new RawEdge("b", "c", 1, "b-to-c")
                ),
                "c", List.of(new RawEdge("c", "g", 10, "c-to-g"))
        );
        Map<String, Long> admissibleButInconsistent = Map.of(
                "s", 0L,
                "a", 0L,
                "b", 2L,
                "c", 10L,
                "g", 0L
        );
        var astar = SFMBoundedTrajectoryPlanner.plan(request(
                "astar-non-zero",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                state -> admissibleButInconsistent.get(state.id()),
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));
        var dijkstra = SFMBoundedTrajectoryPlanner.plan(request(
                "dijkstra-comparison",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));

        assertEquals(3, selectedCost(astar));
        assertEquals(selectedCost(dijkstra), selectedCost(astar));
        assertEquals(
                List.of("test:to-b", "test:b-to-a", "test:a-to-g"),
                selectedRoute(astar).steps().stream().map(step -> step.actionIntent().actionId()).toList()
        );
        assertTrue(candidate(astar, "a").reason().contains("generated"));
    }

    @Test
    void stepConstrainedSearchRetainsNondominatedDepthLabelsForTheSameState() {
        Map<String, List<RawEdge>> graph = Map.of(
                "s", List.of(
                        new RawEdge("s", "a", 0, "to-a"),
                        new RawEdge("s", "x", 1, "to-x-shallow"),
                        new RawEdge("s", "g", 10, "direct")
                ),
                "a", List.of(new RawEdge("a", "x", 0, "to-x-deep")),
                "x", List.of(new RawEdge("x", "y", 0, "to-y")),
                "y", List.of(new RawEdge("y", "g", 0, "to-g"))
        );
        var base = request(
                "depth-labels",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        );
        var result = SFMBoundedTrajectoryPlanner.plan(withMaxExecutionSteps(base, 3));

        assertEquals(1L, boundedExhaustiveMinimumCost(graph, "s", "g", 3).orElseThrow());
        assertEquals(1, selectedCost(result));
        assertEquals(
                List.of("x", "y", "g"),
                selectedRoute(result).steps().stream().map(SFMTrajectoryContract.TrajectoryStep::predictedStateId).toList()
        );
        assertEquals(
                SFMTrajectoryContract.OptimalityClaim.MINIMUM_COST_UNDER_DECLARED_GRAPH_AND_POLICY,
                result.plan().optimalityClaim()
        );
    }

    @Test
    void budgetsAndCancellationProduceExactNonSuccessResults() {
        Map<String, List<RawEdge>> graph = Map.of(
                "s", List.of(new RawEdge("s", "a", 1, "to-a")),
                "a", List.of(new RawEdge("a", "g", 1, "to-g"))
        );
        var expansionLimited = SFMBoundedTrajectoryPlanner.plan(request(
                "expansion-limited",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                new SFMTrajectoryContract.SearchBudget(0, 100, 1_000),
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED, expansionLimited.plan().result());
        assertEquals(0, expansionLimited.expanded());

        var generationLimited = SFMBoundedTrajectoryPlanner.plan(request(
                "generation-limited",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                new SFMTrajectoryContract.SearchBudget(100, 0, 1_000),
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED, generationLimited.plan().result());
        assertEquals(0, generationLimited.generated());
        assertEquals(SFMTrajectoryContract.SearchCandidateStatus.OPEN, candidate(generationLimited, "s").status());

        AtomicInteger generatorCalls = new AtomicInteger();
        var zeroGenerationBase = request(
                "zero-does-not-invoke",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                new SFMTrajectoryContract.SearchBudget(100, 0, 1_000),
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        );
        var zeroGeneration = SFMBoundedTrajectoryPlanner.plan(withGenerator(
                zeroGenerationBase,
                state -> {
                    generatorCalls.incrementAndGet();
                    return zeroGenerationBase.successorGenerator().generate(state);
                }
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED, zeroGeneration.plan().result());
        assertEquals(0, generatorCalls.get());

        Map<String, List<RawEdge>> twoSuccessors = Map.of(
                "s", List.of(
                        new RawEdge("s", "a", 10, "a-first"),
                        new RawEdge("s", "g", 1, "z-goal")
                )
        );
        var exactLimit = SFMBoundedTrajectoryPlanner.plan(request(
                "generation-exact",
                Optional.empty(),
                twoSuccessors,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                new SFMTrajectoryContract.SearchBudget(100, 2, 1_000),
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.FOUND, exactLimit.plan().result());
        assertEquals(2, exactLimit.generated());

        var oneShort = SFMBoundedTrajectoryPlanner.plan(request(
                "generation-one-short",
                Optional.empty(),
                twoSuccessors,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                new SFMTrajectoryContract.SearchBudget(100, 1, 1_000),
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED, oneShort.plan().result());
        assertEquals(1, oneShort.generated());
        assertEquals(SFMTrajectoryContract.SearchCandidateStatus.OPEN, candidate(oneShort, "s").status());

        AtomicInteger cancellationChecks = new AtomicInteger();
        var cancelled = SFMBoundedTrajectoryPlanner.plan(request(
                "cancelled",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                () -> cancellationChecks.incrementAndGet() >= 3,
                () -> 0
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.CANCELLED, cancelled.plan().result());
        assertEquals(SFMTrajectoryContract.OptimalityClaim.NONE, cancelled.plan().optimalityClaim());

        java.util.concurrent.atomic.AtomicBoolean cancelDuringIteration = new java.util.concurrent.atomic.AtomicBoolean();
        var cancellationBase = request(
                "cancel-mid-list",
                Optional.empty(),
                twoSuccessors,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                cancelDuringIteration::get,
                () -> 0
        );
        var cancelledMidList = SFMBoundedTrajectoryPlanner.plan(withGenerator(
                cancellationBase,
                state -> togglingIterator(
                        cancellationBase.successorGenerator().generate(state),
                        () -> cancelDuringIteration.set(true)
                )
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.CANCELLED, cancelledMidList.plan().result());
        assertEquals(1, cancelledMidList.generated());
        assertEquals(SFMTrajectoryContract.SearchCandidateStatus.OPEN, candidate(cancelledMidList, "s").status());

        AtomicLong nanos = new AtomicLong();
        var elapsed = SFMBoundedTrajectoryPlanner.plan(request(
                "elapsed",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                new SFMTrajectoryContract.SearchBudget(100, 100, 1),
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> nanos.getAndAdd(2_000_000)
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED, elapsed.plan().result());

        AtomicLong generatorNanos = new AtomicLong();
        var elapsedInsideGeneratorBase = request(
                "elapsed-inside-generator",
                Optional.empty(),
                twoSuccessors,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                new SFMTrajectoryContract.SearchBudget(100, 100, 1),
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                generatorNanos::get
        );
        var elapsedInsideGenerator = SFMBoundedTrajectoryPlanner.plan(withGenerator(
                elapsedInsideGeneratorBase,
                state -> togglingIterator(
                        elapsedInsideGeneratorBase.successorGenerator().generate(state),
                        () -> generatorNanos.set(2_000_000)
                )
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED, elapsedInsideGenerator.plan().result());
        assertEquals(1, elapsedInsideGenerator.generated());
        assertTrue(elapsedInsideGenerator.plan().routes().isEmpty());

        AtomicLong goalEvaluationNanos = new AtomicLong();
        var goalDeadlineBase = request(
                "elapsed-before-goal-acceptance",
                Optional.empty(),
                Map.of("s", List.of(new RawEdge("s", "g", 1, "goal"))),
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                new SFMTrajectoryContract.SearchBudget(100, 100, 1),
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                goalEvaluationNanos::get
        );
        var goalDeadline = SFMBoundedTrajectoryPlanner.plan(withPredicateEvaluator(
                goalDeadlineBase,
                (state, predicate) -> {
                    var evaluation = goalDeadlineBase.predicateEvaluator().evaluate(state, predicate);
                    if (state.id().equals("g") && predicate.id().equals("goal")) {
                        goalEvaluationNanos.set(2_000_000);
                    }
                    return evaluation;
                }
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED, goalDeadline.plan().result());
        assertTrue(goalDeadline.plan().routes().isEmpty());
        assertTrue(goalDeadline.terminalReasons().contains(
                SFMBoundedTrajectoryPlanner.TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED
        ));
    }

    @Test
    void forbiddenUnknownAndIrreversibleEffectsRemainInspectableBarriers() {
        Map<String, List<RawEdge>> graph = Map.of(
                "s", List.of(
                        new RawEdge("s", "forbidden", 1, "forbidden", SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE),
                        new RawEdge("s", "external", 1, "external", SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE),
                        new RawEdge("s", "unknown", 1, "unknown", SFMHistoryGraphContract.EffectClass.UNKNOWN)
                )
        );
        var result = SFMBoundedTrajectoryPlanner.plan(request(
                "blocked",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                LARGE_BUDGET,
                Set.of(SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE),
                Set.of(
                        SFMHistoryGraphContract.EffectClass.PURE,
                        SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                        SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                        SFMHistoryGraphContract.EffectClass.UNKNOWN
                ),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));

        assertEquals(SFMTrajectoryContract.PlanResult.BLOCKED, result.plan().result());
        assertEquals(SFMTrajectoryContract.SearchCandidateStatus.PRUNED, candidate(result, "forbidden").status());
        assertEquals(SFMTrajectoryContract.SearchCandidateStatus.BARRIER, candidate(result, "external").status());
        assertEquals(SFMTrajectoryContract.SearchCandidateStatus.BARRIER, candidate(result, "unknown").status());
        assertTrue(candidate(result, "external").reason().contains("external irreversible"));
        assertEquals(
                SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER,
                result.projectionFrontier().status()
        );
    }

    @Test
    void rejectedStatesDoNotInvokeHeuristicsAndBlockCausesKeepTheirOwnProjectionStatus() {
        record EffectCase(
                String id,
                SFMHistoryGraphContract.EffectClass effect,
                Set<SFMHistoryGraphContract.EffectClass> forbidden,
                Set<SFMHistoryGraphContract.EffectClass> authorized,
                SFMBoundedTrajectoryPlanner.TerminalReason reason,
                SFMHistoryGraphContract.ProjectionStatus projectionStatus
        ) {
        }
        List<EffectCase> cases = List.of(
                new EffectCase(
                        "forbidden",
                        SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                        Set.of(SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE),
                        Set.of(SFMHistoryGraphContract.EffectClass.PURE, SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE),
                        SFMBoundedTrajectoryPlanner.TerminalReason.FORBIDDEN_EFFECT,
                        SFMHistoryGraphContract.ProjectionStatus.CONFLICT
                ),
                new EffectCase(
                        "external",
                        SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                        Set.of(),
                        Set.of(SFMHistoryGraphContract.EffectClass.PURE, SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE),
                        SFMBoundedTrajectoryPlanner.TerminalReason.EXTERNAL_IRREVERSIBLE_EFFECT,
                        SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER
                ),
                new EffectCase(
                        "unknown",
                        SFMHistoryGraphContract.EffectClass.UNKNOWN,
                        Set.of(),
                        Set.of(SFMHistoryGraphContract.EffectClass.PURE, SFMHistoryGraphContract.EffectClass.UNKNOWN),
                        SFMBoundedTrajectoryPlanner.TerminalReason.UNKNOWN_EFFECT,
                        SFMHistoryGraphContract.ProjectionStatus.UNKNOWN
                ),
                new EffectCase(
                        "unauthorized",
                        SFMHistoryGraphContract.EffectClass.REEXECUTABLE_WITH_CAPABILITY,
                        Set.of(),
                        Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                        SFMBoundedTrajectoryPlanner.TerminalReason.UNAUTHORIZED_EFFECT,
                        SFMHistoryGraphContract.ProjectionStatus.UNKNOWN
                )
        );

        for (EffectCase effectCase : cases) {
            Map<String, List<RawEdge>> graph = Map.of(
                    "s",
                    List.of(new RawEdge("s", "rejected", 1, effectCase.id(), effectCase.effect()))
            );
            var result = SFMBoundedTrajectoryPlanner.plan(request(
                    "typed-block-" + effectCase.id(),
                    Optional.empty(),
                    graph,
                    "s",
                    "g",
                    SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                    state -> {
                        if (!state.id().equals("s")) throw new AssertionError("rejected state reached heuristic");
                        return 0;
                    },
                    LARGE_BUDGET,
                    effectCase.forbidden(),
                    effectCase.authorized(),
                    SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                    () -> 0
            ));
            assertEquals(SFMTrajectoryContract.PlanResult.BLOCKED, result.plan().result());
            assertTrue(result.terminalReasons().contains(effectCase.reason()));
            assertFalse(result.terminalReasons().contains(SFMBoundedTrajectoryPlanner.TerminalReason.INVALID_HEURISTIC));
            assertEquals(effectCase.projectionStatus(), result.projectionFrontier().status());
        }
    }

    @Test
    void hardInvariantsPruneUnsafeStatesBeforeTheyCanSatisfyTheGoal() {
        Map<String, List<RawEdge>> graph = Map.of(
                "s", List.of(new RawEdge("s", "unsafe", 1, "unsafe")),
                "unsafe", List.of(new RawEdge("unsafe", "g", 1, "escape"))
        );
        var result = SFMBoundedTrajectoryPlanner.plan(request(
                "invariant",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));

        assertEquals(SFMTrajectoryContract.PlanResult.BLOCKED, result.plan().result());
        assertTrue(candidate(result, "unsafe").reason().contains("hard invariant failed"));
        assertFalse(result.discoveredStates().containsKey("g"));
        assertEquals(SFMHistoryGraphContract.ProjectionStatus.CONFLICT, result.projectionFrontier().status());
    }

    @Test
    void stateIdentityCollisionsAndNonCanonicalGeneratorsFailClosed() {
        var base = request(
                "identity-collision",
                Optional.empty(),
                Map.of(),
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        );
        List<SFMBoundedTrajectoryPlanner.Transition<String>> conflictingStates = List.of(
                new SFMBoundedTrajectoryPlanner.Transition<>(
                        "first",
                        intent("a-first"),
                        SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                        "outcome-first",
                        new SFMBoundedTrajectoryPlanner.State<>("same-id", "first-value"),
                        SFMHistoryGraphContract.EffectClass.PURE,
                        1
                ),
                new SFMBoundedTrajectoryPlanner.Transition<>(
                        "second",
                        intent("b-second"),
                        SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                        "outcome-second",
                        new SFMBoundedTrajectoryPlanner.State<>("same-id", "second-value"),
                        SFMHistoryGraphContract.EffectClass.PURE,
                        1
                )
        ).stream().sorted(java.util.Comparator.comparing(SFMBoundedTrajectoryPlanner.Transition::orderingKey)).toList();
        var collision = SFMBoundedTrajectoryPlanner.plan(withGenerator(base, ignored -> conflictingStates.iterator()));
        assertEquals(SFMTrajectoryContract.PlanResult.BLOCKED, collision.plan().result());
        assertTrue(collision.terminalReasons().contains(SFMBoundedTrajectoryPlanner.TerminalReason.STATE_ID_COLLISION));
        assertEquals("first-value", collision.discoveredStates().get("same-id").value());

        List<SFMBoundedTrajectoryPlanner.Transition<String>> reversed = new ArrayList<>(conflictingStates);
        java.util.Collections.reverse(reversed);
        var orderViolationBase = request(
                "order-violation",
                Optional.empty(),
                Map.of(),
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        );
        var orderViolation = SFMBoundedTrajectoryPlanner.plan(withGenerator(
                orderViolationBase,
                ignored -> reversed.iterator()
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.BLOCKED, orderViolation.plan().result());
        assertTrue(orderViolation.terminalReasons().contains(
                SFMBoundedTrajectoryPlanner.TerminalReason.GENERATOR_ORDER_VIOLATION
        ));

        var unbounded = SFMBoundedTrajectoryPlanner.plan(withActionGeneratorIdentity(
                base,
                new SFMTrajectoryContract.ActionGeneratorIdentity("unbounded", "1", false)
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.BLOCKED, unbounded.plan().result());
        assertEquals(
                List.of(SFMBoundedTrajectoryPlanner.TerminalReason.UNBOUNDED_GENERATOR),
                unbounded.terminalReasons()
        );
    }

    @Test
    void staleStepPausesWithoutMovingAnyPointerOrHead() {
        Map<String, List<RawEdge>> graph = Map.of(
                "s", List.of(new RawEdge("s", "g", 1, "finish"))
        );
        var result = SFMBoundedTrajectoryPlanner.plan(request(
                "guard-plan",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));

        var ready = SFMBoundedTrajectoryPlanner.validateNextStep(result.plan(), "route-guard-plan", 0, "s");
        assertEquals(SFMBoundedTrajectoryPlanner.StepReadiness.READY, ready.readiness());
        var stale = SFMBoundedTrajectoryPlanner.validateNextStep(result.plan(), "route-guard-plan", 0, "other");
        assertEquals(SFMBoundedTrajectoryPlanner.StepReadiness.STALE_PARENT, stale.readiness());

        var machine = new SFMTrajectoryContract.TrajectoryMachineState(
                "head-document",
                Optional.of(result.plan().id()),
                Optional.of(new SFMTrajectoryContract.InstructionPointer(result.plan().id(), "route-guard-plan", 0)),
                Optional.of(result.projectionFrontier()),
                result.plan().supervisionContractRevision(),
                SFMTrajectoryContract.MachineStatus.READY,
                new SFMTrajectoryContract.RemainingBudget(10, 10, 10)
        );
        var paused = SFMBoundedTrajectoryPlanner.pauseOnStaleParent(machine, stale, "other");
        assertEquals(SFMTrajectoryContract.MachineStatus.STALE_PRECONDITION, paused.status());
        assertEquals(machine.actualHistoryHeadId(), paused.actualHistoryHeadId());
        assertEquals(machine.instructionPointer(), paused.instructionPointer());
        assertEquals(machine.projectionFrontier(), paused.projectionFrontier());

        var otherResult = SFMBoundedTrajectoryPlanner.plan(request(
                "other-plan",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA,
                ignored -> 0,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));
        var otherMachine = new SFMTrajectoryContract.TrajectoryMachineState(
                "other-head",
                Optional.of(otherResult.plan().id()),
                Optional.of(new SFMTrajectoryContract.InstructionPointer(
                        otherResult.plan().id(),
                        "route-other-plan",
                        0
                )),
                Optional.of(otherResult.projectionFrontier()),
                otherResult.plan().supervisionContractRevision(),
                SFMTrajectoryContract.MachineStatus.READY,
                new SFMTrajectoryContract.RemainingBudget(10, 10, 10)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMBoundedTrajectoryPlanner.pauseOnStaleParent(otherMachine, stale, "other")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMBoundedTrajectoryPlanner.pauseOnStaleParent(machine, stale, "newer-actual-state")
        );
    }

    @Test
    void immutablePlanBookRetainsTwoReplansFromTheSameStart() {
        Map<String, List<RawEdge>> firstGraph = Map.of("s", List.of(new RawEdge("s", "g", 3, "literal")));
        Map<String, List<RawEdge>> secondGraph = Map.of("s", List.of(new RawEdge("s", "g", 1, "semantic")));
        var first = SFMBoundedTrajectoryPlanner.plan(request(
                "plan-1", Optional.empty(), firstGraph, "s", "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA, ignored -> 0, LARGE_BUDGET,
                Set.of(), Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(), () -> 0
        )).plan();
        var book = new SFMTrajectoryContract.PlanBook(
                SFMTrajectoryContract.SCHEMA,
                List.of(first),
                Optional.of(first.id())
        );
        var second = SFMBoundedTrajectoryPlanner.plan(request(
                "plan-2", Optional.of("plan-1"), secondGraph, "s", "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA, ignored -> 0, LARGE_BUDGET,
                Set.of(), Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(), () -> 0
        )).plan();
        var third = SFMBoundedTrajectoryPlanner.plan(request(
                "plan-3", Optional.of("plan-1"), firstGraph, "s", "g",
                SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA, ignored -> 0, LARGE_BUDGET,
                Set.of(), Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(), () -> 0
        )).plan();

        var afterSecond = SFMBoundedTrajectoryPlanner.appendPlanRevision(book, second, true);
        var afterThird = SFMBoundedTrajectoryPlanner.appendPlanRevision(afterSecond, third, true);
        assertEquals(1, book.plans().size());
        assertEquals(2, afterSecond.plans().size());
        assertEquals(3, afterThird.plans().size());
        assertEquals(Optional.of("plan-3"), afterThird.selectedPlanRevisionId());
        assertEquals(Set.of("plan-1", "plan-2", "plan-3"),
                afterThird.plans().stream().map(SFMTrajectoryContract.TrajectoryPlanRevision::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(3, selectedCost(new SFMBoundedTrajectoryPlanner.PlanningResult<>(
                third,
                0,
                0,
                0,
                List.of(SFMBoundedTrajectoryPlanner.TerminalReason.FOUND),
                List.of(),
                Map.of()
        )));
        assertEquals(1, second.routes().get(0).totalCost());
    }

    @Test
    void invalidInputsAndNonAdmissibleAStarFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new SFMBoundedTrajectoryPlanner.Transition<>(
                "negative",
                intent("negative"),
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                "outcome-negative",
                state("g"),
                SFMHistoryGraphContract.EffectClass.PURE,
                -1
        ));

        Map<String, List<RawEdge>> graph = Map.of("s", List.of(new RawEdge("s", "g", 1, "finish")));
        var request = request(
                "non-admissible", Optional.empty(), graph, "s", "g",
                SFMTrajectoryContract.SearchAlgorithm.A_STAR, ignored -> 7, LARGE_BUDGET,
                Set.of(), Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(), () -> 0,
                false
        );
        var result = SFMBoundedTrajectoryPlanner.plan(request);
        assertEquals(SFMTrajectoryContract.PlanResult.BLOCKED, result.plan().result());
        assertEquals(SFMTrajectoryContract.OptimalityClaim.NONE, result.plan().optimalityClaim());
        assertEquals(
                List.of(SFMBoundedTrajectoryPlanner.TerminalReason.INVALID_HEURISTIC),
                result.terminalReasons()
        );
        assertTrue(result.plan().routes().isEmpty());

        var impossibleGoalHeuristic = SFMBoundedTrajectoryPlanner.plan(request(
                "non-zero-goal-heuristic",
                Optional.empty(),
                graph,
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                state -> state.id().equals("g") ? 1 : 0,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.BLOCKED, impossibleGoalHeuristic.plan().result());
        assertTrue(impossibleGoalHeuristic.terminalReasons().contains(
                SFMBoundedTrajectoryPlanner.TerminalReason.INVALID_HEURISTIC
        ));
    }

    @Test
    void cancelledExpiredAndInvariantRejectedStartsNeverInvokeTheHeuristic() {
        AtomicInteger heuristicCalls = new AtomicInteger();
        java.util.function.ToLongFunction<SFMBoundedTrajectoryPlanner.State<String>> forbiddenHeuristic = state -> {
            heuristicCalls.incrementAndGet();
            throw new AssertionError("start heuristic should not run");
        };

        var cancelled = SFMBoundedTrajectoryPlanner.plan(request(
                "cancel-before-heuristic",
                Optional.empty(),
                Map.of(),
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                forbiddenHeuristic,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                () -> true,
                () -> 0
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.CANCELLED, cancelled.plan().result());

        var expired = SFMBoundedTrajectoryPlanner.plan(request(
                "expire-before-heuristic",
                Optional.empty(),
                Map.of(),
                "s",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                forbiddenHeuristic,
                new SFMTrajectoryContract.SearchBudget(100, 100, 0),
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED, expired.plan().result());

        var rejected = SFMBoundedTrajectoryPlanner.plan(request(
                "invariant-before-heuristic",
                Optional.empty(),
                Map.of(),
                "unsafe",
                "g",
                SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                forbiddenHeuristic,
                LARGE_BUDGET,
                Set.of(),
                Set.of(SFMHistoryGraphContract.EffectClass.PURE),
                SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                () -> 0
        ));
        assertEquals(SFMTrajectoryContract.PlanResult.BLOCKED, rejected.plan().result());
        assertEquals(0, heuristicCalls.get());
    }

    private static <S> SFMBoundedTrajectoryPlanner.Request<S> withGenerator(
            SFMBoundedTrajectoryPlanner.Request<S> base,
            SFMBoundedTrajectoryPlanner.SuccessorGenerator<S> generator
    ) {
        return new SFMBoundedTrajectoryPlanner.Request<>(
                base.planRevisionId(),
                base.parentPlanRevisionId(),
                base.routeId(),
                base.start(),
                base.supervision(),
                base.actionGenerator(),
                base.costPolicy(),
                base.heuristicIdentity(),
                base.algorithm(),
                generator,
                base.predicateEvaluator(),
                base.heuristic(),
                base.authorizedEffectClasses(),
                base.cancellationToken(),
                base.clock()
        );
    }

    private static <S> SFMBoundedTrajectoryPlanner.Request<S> withActionGeneratorIdentity(
            SFMBoundedTrajectoryPlanner.Request<S> base,
            SFMTrajectoryContract.ActionGeneratorIdentity identity
    ) {
        return new SFMBoundedTrajectoryPlanner.Request<>(
                base.planRevisionId(),
                base.parentPlanRevisionId(),
                base.routeId(),
                base.start(),
                base.supervision(),
                identity,
                base.costPolicy(),
                base.heuristicIdentity(),
                base.algorithm(),
                base.successorGenerator(),
                base.predicateEvaluator(),
                base.heuristic(),
                base.authorizedEffectClasses(),
                base.cancellationToken(),
                base.clock()
        );
    }

    private static <S> SFMBoundedTrajectoryPlanner.Request<S> withPredicateEvaluator(
            SFMBoundedTrajectoryPlanner.Request<S> base,
            SFMBoundedTrajectoryPlanner.PredicateEvaluator<S> evaluator
    ) {
        return new SFMBoundedTrajectoryPlanner.Request<>(
                base.planRevisionId(),
                base.parentPlanRevisionId(),
                base.routeId(),
                base.start(),
                base.supervision(),
                base.actionGenerator(),
                base.costPolicy(),
                base.heuristicIdentity(),
                base.algorithm(),
                base.successorGenerator(),
                evaluator,
                base.heuristic(),
                base.authorizedEffectClasses(),
                base.cancellationToken(),
                base.clock()
        );
    }

    private static <S> SFMBoundedTrajectoryPlanner.Request<S> withMaxExecutionSteps(
            SFMBoundedTrajectoryPlanner.Request<S> base,
            long maxExecutionSteps
    ) {
        SFMTrajectoryContract.SupervisionContract previous = base.supervision();
        SFMTrajectoryContract.SupervisionContract supervision = new SFMTrajectoryContract.SupervisionContract(
                previous.id(),
                previous.revision(),
                previous.startDomainId(),
                previous.startStateRevisionId(),
                previous.goalPredicates(),
                previous.hardInvariants(),
                previous.forbiddenEffectClasses(),
                previous.requiredEvidence(),
                previous.searchBudget(),
                maxExecutionSteps,
                previous.approvalRequirement(),
                previous.status(),
                previous.approval()
        );
        return new SFMBoundedTrajectoryPlanner.Request<>(
                base.planRevisionId(),
                base.parentPlanRevisionId(),
                base.routeId(),
                base.start(),
                supervision,
                base.actionGenerator(),
                base.costPolicy(),
                base.heuristicIdentity(),
                base.algorithm(),
                base.successorGenerator(),
                base.predicateEvaluator(),
                base.heuristic(),
                base.authorizedEffectClasses(),
                base.cancellationToken(),
                base.clock()
        );
    }

    private static <T> java.util.Iterator<T> togglingIterator(
            java.util.Iterator<T> delegate,
            Runnable afterNext
    ) {
        return new java.util.Iterator<>() {
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public T next() {
                T value = delegate.next();
                afterNext.run();
                return value;
            }
        };
    }

    private static SFMBoundedTrajectoryPlanner.Request<String> request(
            String planId,
            Optional<String> parentPlanId,
            Map<String, List<RawEdge>> graph,
            String start,
            String goal,
            SFMTrajectoryContract.SearchAlgorithm algorithm,
            java.util.function.ToLongFunction<SFMBoundedTrajectoryPlanner.State<String>> heuristic,
            SFMTrajectoryContract.SearchBudget budget,
            Set<SFMHistoryGraphContract.EffectClass> forbiddenEffects,
            Set<SFMHistoryGraphContract.EffectClass> authorizedEffects,
            SFMBoundedTrajectoryPlanner.CancellationToken cancellationToken,
            SFMBoundedTrajectoryPlanner.NanoClock clock
    ) {
        return request(
                planId, parentPlanId, graph, start, goal, algorithm, heuristic, budget,
                forbiddenEffects, authorizedEffects, cancellationToken, clock, true
        );
    }

    private static SFMBoundedTrajectoryPlanner.Request<String> request(
            String planId,
            Optional<String> parentPlanId,
            Map<String, List<RawEdge>> graph,
            String start,
            String goal,
            SFMTrajectoryContract.SearchAlgorithm algorithm,
            java.util.function.ToLongFunction<SFMBoundedTrajectoryPlanner.State<String>> heuristic,
            SFMTrajectoryContract.SearchBudget budget,
            Set<SFMHistoryGraphContract.EffectClass> forbiddenEffects,
            Set<SFMHistoryGraphContract.EffectClass> authorizedEffects,
            SFMBoundedTrajectoryPlanner.CancellationToken cancellationToken,
            SFMBoundedTrajectoryPlanner.NanoClock clock,
            boolean admissible
    ) {
        SFMTrajectoryContract.SupervisionContract supervision = new SFMTrajectoryContract.SupervisionContract(
                "supervision-" + planId,
                "supervision-revision-" + planId,
                "test-domain",
                start,
                List.of(new SFMTrajectoryContract.Predicate("goal", "state-id", goal)),
                List.of(new SFMTrajectoryContract.Predicate("safe", "not-state-id", "unsafe")),
                forbiddenEffects,
                List.of(),
                budget,
                20,
                SFMTrajectoryContract.ApprovalRequirement.HUMAN,
                SFMTrajectoryContract.SupervisionStatus.DRAFT,
                Optional.empty()
        );
        return new SFMBoundedTrajectoryPlanner.Request<>(
                planId,
                parentPlanId,
                "route-" + planId,
                state(start),
                supervision,
                new SFMTrajectoryContract.ActionGeneratorIdentity("test-generator", "1", true),
                new SFMTrajectoryContract.CostPolicyIdentity("test-cost", "1"),
                new SFMTrajectoryContract.HeuristicIdentity(
                        algorithm == SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA ? "zero" : "test-heuristic",
                        "1",
                        admissible
                ),
                algorithm,
                current -> graph.getOrDefault(current.id(), List.of()).stream()
                        .map(edge -> transition(edge, current.id()))
                        .sorted(java.util.Comparator.comparing(SFMBoundedTrajectoryPlanner.Transition::orderingKey))
                        .iterator(),
                (current, predicate) -> switch (predicate.kind()) {
                    case "state-id" -> new SFMBoundedTrajectoryPlanner.PredicateResult(
                            current.id().equals(predicate.expectedValue()),
                            "state=" + current.id()
                    );
                    case "not-state-id" -> new SFMBoundedTrajectoryPlanner.PredicateResult(
                            !current.id().equals(predicate.expectedValue()),
                            "state=" + current.id()
                    );
                    default -> throw new IllegalArgumentException("Unknown predicate kind: " + predicate.kind());
                },
                heuristic,
                authorizedEffects,
                cancellationToken,
                clock
        );
    }

    private static SFMBoundedTrajectoryPlanner.Transition<String> transition(RawEdge edge, String parent) {
        return new SFMBoundedTrajectoryPlanner.Transition<>(
                parent + "-" + edge.action() + "-" + edge.to(),
                intent(edge.action()),
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                "outcome-" + parent + "-" + edge.action() + "-" + edge.to(),
                state(edge.to()),
                edge.effectClass(),
                edge.cost()
        );
    }

    private static SFMHistoryGraphContract.ActionIntent intent(String action) {
        return new SFMHistoryGraphContract.ActionIntent(
                "intent-" + action,
                "test:" + action,
                List.of(),
                "hash-" + action
        );
    }

    private static SFMBoundedTrajectoryPlanner.State<String> state(String id) {
        return new SFMBoundedTrajectoryPlanner.State<>(id, id);
    }

    private static long selectedCost(SFMBoundedTrajectoryPlanner.PlanningResult<String> result) {
        return selectedRoute(result).totalCost();
    }

    private static SFMTrajectoryContract.TrajectoryRoute selectedRoute(
            SFMBoundedTrajectoryPlanner.PlanningResult<String> result
    ) {
        String selected = result.plan().selectedRouteId().orElseThrow();
        return result.plan().routes().stream().filter(route -> route.id().equals(selected)).findFirst().orElseThrow();
    }

    private static SFMTrajectoryContract.SearchCandidate candidate(
            SFMBoundedTrajectoryPlanner.PlanningResult<String> result,
            String stateId
    ) {
        return result.plan().exploredCandidates().stream()
                .filter(item -> item.stateId().equals(stateId))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Long> bellmanFordDistancesToGoal(
            List<String> nodes,
            Map<String, List<RawEdge>> graph,
            String goal
    ) {
        Map<String, Long> distances = new HashMap<>();
        distances.put(goal, 0L);
        for (int pass = 0; pass < nodes.size() - 1; pass++) {
            boolean changed = false;
            for (List<RawEdge> edges : graph.values()) {
                for (RawEdge edge : edges) {
                    Long suffix = distances.get(edge.to());
                    if (suffix == null) continue;
                    long candidate = Math.addExact(edge.cost(), suffix);
                    Long previous = distances.get(edge.from());
                    if (previous == null || candidate < previous) {
                        distances.put(edge.from(), candidate);
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        return Map.copyOf(distances);
    }

    private static boolean isInconsistent(
            Map<String, List<RawEdge>> graph,
            Map<String, Long> heuristic
    ) {
        for (List<RawEdge> edges : graph.values()) {
            for (RawEdge edge : edges) {
                long parent = heuristic.getOrDefault(edge.from(), 0L);
                long child = heuristic.getOrDefault(edge.to(), 0L);
                if (parent > Math.addExact(edge.cost(), child)) return true;
            }
        }
        return false;
    }

    private static Optional<Long> boundedExhaustiveMinimumCost(
            Map<String, List<RawEdge>> graph,
            String current,
            String goal,
            int remainingSteps
    ) {
        if (current.equals(goal)) return Optional.of(0L);
        if (remainingSteps == 0) return Optional.empty();
        Long best = null;
        for (RawEdge edge : graph.getOrDefault(current, List.of())) {
            Optional<Long> suffix = boundedExhaustiveMinimumCost(
                    graph,
                    edge.to(),
                    goal,
                    remainingSteps - 1
            );
            if (suffix.isEmpty()) continue;
            long candidate = Math.addExact(edge.cost(), suffix.orElseThrow());
            if (best == null || candidate < best) best = candidate;
        }
        return Optional.ofNullable(best);
    }

    private static Optional<Long> exhaustiveMinimumCost(
            Map<String, List<RawEdge>> graph,
            String current,
            String goal,
            Set<String> path
    ) {
        if (current.equals(goal)) return Optional.of(0L);
        if (!path.add(current)) return Optional.empty();
        Long best = null;
        for (RawEdge edge : graph.getOrDefault(current, List.of())) {
            Optional<Long> suffix = exhaustiveMinimumCost(graph, edge.to(), goal, path);
            if (suffix.isEmpty()) continue;
            long candidate = Math.addExact(edge.cost(), suffix.orElseThrow());
            if (best == null || candidate < best) best = candidate;
        }
        path.remove(current);
        return Optional.ofNullable(best);
    }

    private record RawEdge(
            String from,
            String to,
            long cost,
            String action,
            SFMHistoryGraphContract.EffectClass effectClass
    ) {
        private RawEdge(String from, String to, long cost, String action) {
            this(from, to, cost, action, SFMHistoryGraphContract.EffectClass.PURE);
        }
    }
}
