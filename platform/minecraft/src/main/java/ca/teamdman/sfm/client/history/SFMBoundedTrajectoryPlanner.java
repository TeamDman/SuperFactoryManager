package ca.teamdman.sfm.client.history;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.ToLongFunction;

/**
 * Host-independent, bounded, deterministic trajectory search.
 *
 * <p>The planner only projects immutable states. It never commits a transition,
 * moves a history head, or advances an instruction pointer.</p>
 */
public final class SFMBoundedTrajectoryPlanner {
    public static final String REVISION = "sfm.bounded-trajectory-planner/1";

    private SFMBoundedTrajectoryPlanner() {
    }

    public record State<S>(String id, S value) {
        public State {
            id = requireText(id, "state.id");
            Objects.requireNonNull(value, "state.value");
        }
    }

    public record Transition<S>(
            String id,
            SFMHistoryGraphContract.ActionIntent actionIntent,
            SFMHistoryGraphContract.EvaluationPolicy evaluationPolicy,
            String predictedOutcomeId,
            State<S> resultingState,
            SFMHistoryGraphContract.EffectClass effectClass,
            long cost
    ) {
        public Transition {
            id = requireText(id, "transition.id");
            Objects.requireNonNull(actionIntent, "transition.actionIntent");
            Objects.requireNonNull(evaluationPolicy, "transition.evaluationPolicy");
            predictedOutcomeId = requireText(predictedOutcomeId, "transition.predictedOutcomeId");
            Objects.requireNonNull(resultingState, "transition.resultingState");
            Objects.requireNonNull(effectClass, "transition.effectClass");
            if (cost < 0) throw new IllegalArgumentException("Transition cost must not be negative");
        }

        public String orderingKey() {
            ArrayList<String> parts = new ArrayList<>();
            parts.add(actionIntent.actionId());
            parts.addAll(actionIntent.arguments());
            parts.add(resultingState.id());
            parts.add(actionIntent.intentHash());
            parts.add(actionIntent.id());
            parts.add(id);
            parts.add(predictedOutcomeId);
            parts.add(evaluationPolicy.name());
            parts.add(effectClass.name());
            parts.add(Long.toString(cost));
            return lengthPrefixed(parts);
        }
    }

    /**
     * Returns a finite iterator in strictly increasing {@link Transition#orderingKey()} order.
     * The planner advances it lazily so cancellation and generation/time budgets are observable.
     */
    @FunctionalInterface
    public interface SuccessorGenerator<S> {
        Iterator<Transition<S>> generate(State<S> state);
    }

    @FunctionalInterface
    public interface PredicateEvaluator<S> {
        PredicateResult evaluate(State<S> state, SFMTrajectoryContract.Predicate predicate);
    }

    public record PredicateResult(boolean satisfied, String evidence) {
        public PredicateResult {
            evidence = requireText(evidence, "predicateResult.evidence");
        }
    }

    @FunctionalInterface
    public interface CancellationToken {
        boolean isCancelled();

        static CancellationToken never() {
            return () -> false;
        }
    }

    @FunctionalInterface
    public interface NanoClock {
        long nanoTime();

        static NanoClock system() {
            return System::nanoTime;
        }
    }

    public record Request<S>(
            String planRevisionId,
            Optional<String> parentPlanRevisionId,
            String routeId,
            State<S> start,
            SFMTrajectoryContract.SupervisionContract supervision,
            SFMTrajectoryContract.ActionGeneratorIdentity actionGenerator,
            SFMTrajectoryContract.CostPolicyIdentity costPolicy,
            SFMTrajectoryContract.HeuristicIdentity heuristicIdentity,
            SFMTrajectoryContract.SearchAlgorithm algorithm,
            SuccessorGenerator<S> successorGenerator,
            PredicateEvaluator<S> predicateEvaluator,
            ToLongFunction<State<S>> heuristic,
            Set<SFMHistoryGraphContract.EffectClass> authorizedEffectClasses,
            CancellationToken cancellationToken,
            NanoClock clock
    ) {
        public Request {
            planRevisionId = requireText(planRevisionId, "request.planRevisionId");
            parentPlanRevisionId = requireOptionalText(parentPlanRevisionId, "request.parentPlanRevisionId");
            stringNotEqual(planRevisionId, parentPlanRevisionId, "A plan revision cannot parent itself");
            routeId = requireText(routeId, "request.routeId");
            Objects.requireNonNull(start, "request.start");
            Objects.requireNonNull(supervision, "request.supervision");
            Objects.requireNonNull(actionGenerator, "request.actionGenerator");
            Objects.requireNonNull(costPolicy, "request.costPolicy");
            Objects.requireNonNull(heuristicIdentity, "request.heuristicIdentity");
            Objects.requireNonNull(algorithm, "request.algorithm");
            Objects.requireNonNull(successorGenerator, "request.successorGenerator");
            Objects.requireNonNull(predicateEvaluator, "request.predicateEvaluator");
            Objects.requireNonNull(heuristic, "request.heuristic");
            Objects.requireNonNull(authorizedEffectClasses, "request.authorizedEffectClasses");
            authorizedEffectClasses = Set.copyOf(authorizedEffectClasses);
            Objects.requireNonNull(cancellationToken, "request.cancellationToken");
            Objects.requireNonNull(clock, "request.clock");
            if (!start.id().equals(supervision.startStateRevisionId())) {
                throw new IllegalArgumentException("Planner start must match the supervision start revision");
            }
        }
    }

    public enum TerminalReason {
        FOUND,
        GRAPH_EXHAUSTED,
        CANCELLED,
        EXPANSION_BUDGET_EXHAUSTED,
        GENERATION_BUDGET_EXHAUSTED,
        ELAPSED_TIME_BUDGET_EXHAUSTED,
        HARD_INVARIANT_FAILED,
        FORBIDDEN_EFFECT,
        EXTERNAL_IRREVERSIBLE_EFFECT,
        UNKNOWN_EFFECT,
        UNAUTHORIZED_EFFECT,
        EXECUTION_STEP_LIMIT,
        INVALID_HEURISTIC,
        UNBOUNDED_GENERATOR,
        GENERATOR_FAILURE,
        GENERATOR_ORDER_VIOLATION,
        STATE_ID_COLLISION,
        COST_OVERFLOW
    }

    public record PlanningResult<S>(
            SFMTrajectoryContract.TrajectoryPlanRevision plan,
            long expanded,
            long generated,
            long elapsedMillis,
            List<TerminalReason> terminalReasons,
            List<String> frontierStateIds,
            Map<String, State<S>> discoveredStates
    ) {
        public PlanningResult {
            Objects.requireNonNull(plan, "plan");
            if (expanded < 0 || generated < 0 || elapsedMillis < 0) {
                throw new IllegalArgumentException("Planner metrics must not be negative");
            }
            terminalReasons = canonicalTerminalReasons(terminalReasons);
            if (terminalReasons.isEmpty()) {
                throw new IllegalArgumentException("Planner result requires a terminal reason");
            }
            frontierStateIds = sortedUnique(frontierStateIds, "frontierStateIds");
            Objects.requireNonNull(discoveredStates, "discoveredStates");
            discoveredStates = Collections.unmodifiableMap(new TreeMap<>(discoveredStates));
        }

        public SFMTrajectoryContract.ProjectionFrontier projectionFrontier() {
            SFMHistoryGraphContract.ProjectionStatus status = switch (plan.result()) {
                case FOUND, EXHAUSTED -> SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED;
                case BUDGET_EXHAUSTED -> SFMHistoryGraphContract.ProjectionStatus.BUDGET_EXHAUSTED;
                case CANCELLED -> SFMHistoryGraphContract.ProjectionStatus.CANCELLED;
                case BLOCKED -> {
                    if (terminalReasons.contains(TerminalReason.EXTERNAL_IRREVERSIBLE_EFFECT)) {
                        yield SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER;
                    }
                    if (terminalReasons.contains(TerminalReason.UNKNOWN_EFFECT)
                            || terminalReasons.contains(TerminalReason.UNAUTHORIZED_EFFECT)
                            || terminalReasons.contains(TerminalReason.INVALID_HEURISTIC)
                            || terminalReasons.contains(TerminalReason.UNBOUNDED_GENERATOR)
                            || terminalReasons.contains(TerminalReason.GENERATOR_FAILURE)
                            || terminalReasons.contains(TerminalReason.GENERATOR_ORDER_VIOLATION)
                            || terminalReasons.contains(TerminalReason.STATE_ID_COLLISION)
                            || terminalReasons.contains(TerminalReason.COST_OVERFLOW)) {
                        yield SFMHistoryGraphContract.ProjectionStatus.UNKNOWN;
                    }
                    yield SFMHistoryGraphContract.ProjectionStatus.CONFLICT;
                }
            };
            return new SFMTrajectoryContract.ProjectionFrontier(
                    plan.id(),
                    expanded,
                    generated,
                    frontierStateIds,
                    status
            );
        }
    }

    public enum StepReadiness {
        READY,
        ROUTE_COMPLETE,
        STALE_PARENT
    }

    public record StepValidation(
            String planRevisionId,
            String routeId,
            int nextStepIndex,
            StepReadiness readiness,
            String actualStateId,
            Optional<SFMTrajectoryContract.TrajectoryStep> step,
            String explanation
    ) {
        public StepValidation {
            planRevisionId = requireText(planRevisionId, "stepValidation.planRevisionId");
            routeId = requireText(routeId, "stepValidation.routeId");
            if (nextStepIndex < 0) throw new IllegalArgumentException("stepValidation.nextStepIndex must not be negative");
            Objects.requireNonNull(readiness, "readiness");
            actualStateId = requireText(actualStateId, "stepValidation.actualStateId");
            Objects.requireNonNull(step, "stepValidation.step");
            explanation = requireText(explanation, "stepValidation.explanation");
        }
    }

    public static <S> PlanningResult<S> plan(Request<S> request) {
        Objects.requireNonNull(request, "request");
        long startedAt = request.clock().nanoTime();
        long expanded = 0;
        long generated = 0;
        EnumSet<TerminalReason> terminalReasons = EnumSet.noneOf(TerminalReason.class);

        Comparator<Node<S>> nodeOrder = Comparator
                .comparingLong(Node<S>::estimatedTotalCost)
                .thenComparingLong(Node::accumulatedCost)
                .thenComparingInt(Node::depth)
                .thenComparing(Node::pathOrderingKey)
                .thenComparing(node -> node.state().id());
        PriorityQueue<Node<S>> open = new PriorityQueue<>(nodeOrder);
        Map<LabelKey, Node<S>> best = new HashMap<>();
        Set<LabelKey> closedLabels = new HashSet<>();
        Map<String, State<S>> discoveredStates = new LinkedHashMap<>();
        Map<String, CandidateEvidence> evidence = new HashMap<>();

        discoveredStates.put(request.start().id(), request.start());
        CandidateEvidence startEvidence = new CandidateEvidence(
                request.start().id(),
                0,
                0,
                Optional.empty(),
                Optional.empty()
        );
        evidence.put(request.start().id(), startEvidence);
        startEvidence.addReason("start state generated");

        SFMTrajectoryContract.PlanResult terminalResult = null;
        Node<S> goal = null;

        if (!request.actionGenerator().finite()) {
            startEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.PRUNED);
            startEvidence.addReason("successor generator is not declared finite");
            terminalReasons.add(TerminalReason.UNBOUNDED_GENERATOR);
            terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
        } else if (!request.heuristicIdentity().admissible()) {
            startEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.PRUNED);
            startEvidence.addReason("heuristic is not declared admissible");
            terminalReasons.add(TerminalReason.INVALID_HEURISTIC);
            terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
        } else if (request.cancellationToken().isCancelled()) {
            startEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.OPEN);
            terminalReasons.add(TerminalReason.CANCELLED);
            terminalResult = SFMTrajectoryContract.PlanResult.CANCELLED;
        } else if (timeBudgetExhausted(request, startedAt)) {
            startEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.OPEN);
            startEvidence.addReason("elapsed-time budget exhausted before start evaluation");
            terminalReasons.add(TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED);
            terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
        } else {
            List<String> startInvariantFailures = failedPredicates(
                    request,
                    request.start(),
                    request.supervision().hardInvariants()
            );
            if (request.cancellationToken().isCancelled()) {
                startEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.OPEN);
                startEvidence.addReason("search cancelled during start invariant evaluation");
                terminalReasons.add(TerminalReason.CANCELLED);
                terminalResult = SFMTrajectoryContract.PlanResult.CANCELLED;
            } else if (timeBudgetExhausted(request, startedAt)) {
                startEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.OPEN);
                startEvidence.addReason("elapsed-time budget exhausted during start invariant evaluation");
                terminalReasons.add(TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED);
                terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
            } else if (!startInvariantFailures.isEmpty()) {
                startEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.PRUNED);
                startEvidence.addReason("start violates hard invariants: " + String.join(", ", startInvariantFailures));
                terminalReasons.add(TerminalReason.HARD_INVARIANT_FAILED);
                terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
            } else {
                HeuristicEvaluation startHeuristicEvaluation = evaluateHeuristic(request, request.start());
                if (!startHeuristicEvaluation.valid()) {
                    startEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.PRUNED);
                    startEvidence.addReason(startHeuristicEvaluation.failure().orElseThrow());
                    terminalReasons.add(TerminalReason.INVALID_HEURISTIC);
                    terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
                } else if (request.cancellationToken().isCancelled()) {
                    startEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.OPEN);
                    startEvidence.addReason("search cancelled during start heuristic evaluation");
                    terminalReasons.add(TerminalReason.CANCELLED);
                    terminalResult = SFMTrajectoryContract.PlanResult.CANCELLED;
                } else if (timeBudgetExhausted(request, startedAt)) {
                    startEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.OPEN);
                    startEvidence.addReason("elapsed-time budget exhausted during start heuristic evaluation");
                    terminalReasons.add(TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED);
                    terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                } else {
                    Node<S> start = Node.start(request.start(), startHeuristicEvaluation.value());
                    open.add(start);
                    best.put(start.labelKey(), start);
                    startEvidence.replaceBest(start);
                    boolean startMatchesGoal = matchesGoal(request, request.start());
                    if (request.cancellationToken().isCancelled()) {
                        startEvidence.markOpen();
                        startEvidence.addReason("search cancelled during start goal evaluation");
                        terminalReasons.add(TerminalReason.CANCELLED);
                        terminalResult = SFMTrajectoryContract.PlanResult.CANCELLED;
                    } else if (timeBudgetExhausted(request, startedAt)) {
                        startEvidence.markOpen();
                        startEvidence.addReason("elapsed-time budget exhausted during start goal evaluation");
                        terminalReasons.add(TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED);
                        terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                    } else if (startMatchesGoal && start.heuristic() != 0) {
                        startEvidence.markBarrier();
                        startEvidence.addReason("declared-admissible heuristic is non-zero at a goal state");
                        terminalReasons.add(TerminalReason.INVALID_HEURISTIC);
                        terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
                    } else if (startMatchesGoal) {
                        closedLabels.add(start.labelKey());
                        startEvidence.addReason("all goal predicates satisfied");
                        goal = start;
                        terminalReasons.add(TerminalReason.FOUND);
                        terminalResult = SFMTrajectoryContract.PlanResult.FOUND;
                    }
                }
            }
        }

        while (terminalResult == null && !open.isEmpty()) {
            if (request.cancellationToken().isCancelled()) {
                terminalReasons.add(TerminalReason.CANCELLED);
                terminalResult = SFMTrajectoryContract.PlanResult.CANCELLED;
                break;
            }
            if (timeBudgetExhausted(request, startedAt)) {
                terminalReasons.add(TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED);
                terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                break;
            }
            Node<S> current = open.peek();
            if (best.get(current.labelKey()) != current) {
                open.remove();
                continue;
            }

            boolean currentMatchesGoal = matchesGoal(request, current.state());
            if (request.cancellationToken().isCancelled()) {
                evidenceFor(evidence, current).addReason("search cancelled during goal evaluation");
                terminalReasons.add(TerminalReason.CANCELLED);
                terminalResult = SFMTrajectoryContract.PlanResult.CANCELLED;
                break;
            }
            if (timeBudgetExhausted(request, startedAt)) {
                evidenceFor(evidence, current).addReason("elapsed-time budget exhausted during goal evaluation");
                terminalReasons.add(TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED);
                terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                break;
            }
            if (currentMatchesGoal && current.heuristic() != 0) {
                CandidateEvidence currentEvidence = evidenceFor(evidence, current);
                currentEvidence.markBarrier();
                currentEvidence.addReason("declared-admissible heuristic is non-zero at a goal state");
                terminalReasons.add(TerminalReason.INVALID_HEURISTIC);
                terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
                break;
            }
            if (currentMatchesGoal) {
                open.remove();
                CandidateEvidence currentEvidence = evidenceFor(evidence, current);
                closedLabels.add(current.labelKey());
                currentEvidence.addReason("all goal predicates satisfied");
                goal = current;
                terminalReasons.add(TerminalReason.FOUND);
                terminalResult = SFMTrajectoryContract.PlanResult.FOUND;
                break;
            }

            if (expanded >= request.supervision().searchBudget().maxExpanded()) {
                evidenceFor(evidence, current).addReason("expansion budget exhausted before successor generation");
                terminalReasons.add(TerminalReason.EXPANSION_BUDGET_EXHAUSTED);
                terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                break;
            }
            CandidateEvidence currentEvidence = evidenceFor(evidence, current);

            if (current.depth() >= request.supervision().maxExecutionSteps()) {
                open.remove();
                closedLabels.add(current.labelKey());
                currentEvidence.addReason("execution-step limit prevents further expansion");
                terminalReasons.add(TerminalReason.EXECUTION_STEP_LIMIT);
                continue;
            }

            if (generated >= request.supervision().searchBudget().maxGenerated()) {
                currentEvidence.addReason("generation budget exhausted before invoking successor generator");
                terminalReasons.add(TerminalReason.GENERATION_BUDGET_EXHAUSTED);
                terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                break;
            }

            open.remove();
            expanded++;
            Iterator<Transition<S>> transitions;
            try {
                transitions = Objects.requireNonNull(
                        request.successorGenerator().generate(current.state()),
                        "successor generator result"
                );
            } catch (RuntimeException failure) {
                currentEvidence.markOpen();
                currentEvidence.addReason("successor generator failed: " + failureDescription(failure));
                terminalReasons.add(TerminalReason.GENERATOR_FAILURE);
                terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
                break;
            }
            if (timeBudgetExhausted(request, startedAt)) {
                currentEvidence.markOpen();
                currentEvidence.addReason("elapsed-time budget exhausted while creating successor iterator");
                terminalReasons.add(TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED);
                terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                break;
            }

            String previousOrderingKey = null;
            boolean completeExpansion = false;
            while (terminalResult == null) {
                if (request.cancellationToken().isCancelled()) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("search cancelled while enumerating successors");
                    terminalReasons.add(TerminalReason.CANCELLED);
                    terminalResult = SFMTrajectoryContract.PlanResult.CANCELLED;
                    break;
                }
                if (timeBudgetExhausted(request, startedAt)) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("elapsed-time budget exhausted while enumerating successors");
                    terminalReasons.add(TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED);
                    terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                    break;
                }
                boolean hasNext;
                try {
                    hasNext = transitions.hasNext();
                } catch (RuntimeException failure) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("successor iterator failed: " + failureDescription(failure));
                    terminalReasons.add(TerminalReason.GENERATOR_FAILURE);
                    terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
                    break;
                }
                if (timeBudgetExhausted(request, startedAt)) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("elapsed-time budget exhausted while checking successor iterator");
                    terminalReasons.add(TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED);
                    terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                    break;
                }
                if (!hasNext) {
                    completeExpansion = true;
                    break;
                }
                if (generated >= request.supervision().searchBudget().maxGenerated()) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("generation budget exhausted while enumerating successors");
                    terminalReasons.add(TerminalReason.GENERATION_BUDGET_EXHAUSTED);
                    terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                    break;
                }
                Transition<S> transition;
                try {
                    transition = Objects.requireNonNull(transitions.next(), "generated transition");
                } catch (RuntimeException failure) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("successor iterator failed: " + failureDescription(failure));
                    terminalReasons.add(TerminalReason.GENERATOR_FAILURE);
                    terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
                    break;
                }
                generated++;
                if (timeBudgetExhausted(request, startedAt)) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("elapsed-time budget exhausted while generating a successor");
                    terminalReasons.add(TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED);
                    terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                    break;
                }

                String orderingKey = transition.orderingKey();
                if (previousOrderingKey != null && previousOrderingKey.compareTo(orderingKey) >= 0) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("successor generator violated strict canonical ordering");
                    terminalReasons.add(TerminalReason.GENERATOR_ORDER_VIOLATION);
                    terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
                    break;
                }
                previousOrderingKey = orderingKey;

                State<S> resultingState = transition.resultingState();
                State<S> previousState = discoveredStates.putIfAbsent(resultingState.id(), resultingState);
                if (previousState != null && !previousState.equals(resultingState)) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("state identity collision for " + resultingState.id());
                    terminalReasons.add(TerminalReason.STATE_ID_COLLISION);
                    terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
                    break;
                }
                long resultingCost;
                try {
                    resultingCost = Math.addExact(current.accumulatedCost(), transition.cost());
                } catch (ArithmeticException overflow) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("cost overflow via " + transition.actionIntent().actionId());
                    terminalReasons.add(TerminalReason.COST_OVERFLOW);
                    terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
                    break;
                }
                CandidateEvidence resultingEvidence = evidence.computeIfAbsent(
                        resultingState.id(),
                        ignored -> new CandidateEvidence(
                                resultingState.id(),
                                resultingCost,
                                0,
                                Optional.of(current.state().id()),
                                Optional.of(transition.actionIntent().id())
                        )
                );

                EffectDecision effectDecision = effectDecision(request, transition.effectClass());
                if (effectDecision != EffectDecision.ALLOW) {
                    resultingEvidence.promote(effectDecision.status);
                    resultingEvidence.addReason(effectDecision.reason + " via " + transition.actionIntent().actionId());
                    terminalReasons.add(effectDecision.terminalReason);
                    continue;
                }

                List<String> invariantFailures = failedPredicates(
                        request,
                        resultingState,
                        request.supervision().hardInvariants()
                );
                if (request.cancellationToken().isCancelled()) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("search cancelled during invariant evaluation");
                    terminalReasons.add(TerminalReason.CANCELLED);
                    terminalResult = SFMTrajectoryContract.PlanResult.CANCELLED;
                    break;
                }
                if (timeBudgetExhausted(request, startedAt)) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("elapsed-time budget exhausted during invariant evaluation");
                    terminalReasons.add(TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED);
                    terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                    break;
                }
                if (!invariantFailures.isEmpty()) {
                    resultingEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.PRUNED);
                    resultingEvidence.addReason("hard invariant failed: " + String.join(", ", invariantFailures));
                    terminalReasons.add(TerminalReason.HARD_INVARIANT_FAILED);
                    continue;
                }

                HeuristicEvaluation heuristic = evaluateHeuristic(request, resultingState);
                if (!heuristic.valid()) {
                    resultingEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.BARRIER);
                    resultingEvidence.addReason(heuristic.failure().orElseThrow());
                    currentEvidence.markOpen();
                    terminalReasons.add(TerminalReason.INVALID_HEURISTIC);
                    terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
                    break;
                }
                if (timeBudgetExhausted(request, startedAt)) {
                    currentEvidence.markOpen();
                    currentEvidence.addReason("elapsed-time budget exhausted during heuristic evaluation");
                    terminalReasons.add(TerminalReason.ELAPSED_TIME_BUDGET_EXHAUSTED);
                    terminalResult = SFMTrajectoryContract.PlanResult.BUDGET_EXHAUSTED;
                    break;
                }
                long resultingHeuristic = heuristic.value();
                try {
                    Math.addExact(resultingCost, resultingHeuristic);
                } catch (ArithmeticException overflow) {
                    resultingEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.BARRIER);
                    resultingEvidence.addReason("estimated total cost overflow");
                    currentEvidence.markOpen();
                    terminalReasons.add(TerminalReason.COST_OVERFLOW);
                    terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
                    break;
                }

                String pathOrderingKey = current.pathOrderingKey().isEmpty()
                        ? orderingKey
                        : current.pathOrderingKey() + "\u0001" + orderingKey;
                Node<S> candidate = new Node<>(
                        resultingState,
                        resultingCost,
                        resultingHeuristic,
                        current.depth() + 1,
                        pathOrderingKey,
                        current,
                        transition
                );
                LabelKey candidateKey = candidate.labelKey();
                Node<S> previous = best.get(candidateKey);
                if (isBetter(candidate, previous)) {
                    best.put(candidateKey, candidate);
                    closedLabels.remove(candidateKey);
                    open.add(candidate);
                    resultingEvidence.replaceBest(candidate);
                    resultingEvidence.promote(SFMTrajectoryContract.SearchCandidateStatus.OPEN);
                    resultingEvidence.addReason(previous == null
                            ? "generated"
                            : "reopened with a better deterministic path");
                } else {
                    resultingEvidence.addReason("discarded non-improving alternative from " + current.state().id());
                }
            }
            if (completeExpansion) {
                closedLabels.add(current.labelKey());
                currentEvidence.addReason("expanded at cost " + current.accumulatedCost());
            }
        }

        if (terminalResult == null) {
            boolean hasBlockingReason = terminalReasons.stream().anyMatch(SFMBoundedTrajectoryPlanner::isBlockingReason);
            if (hasBlockingReason) {
                terminalResult = SFMTrajectoryContract.PlanResult.BLOCKED;
            } else {
                terminalReasons.add(TerminalReason.GRAPH_EXHAUSTED);
                terminalResult = SFMTrajectoryContract.PlanResult.EXHAUSTED;
            }
        }

        Map<String, Boolean> openStateLabels = new HashMap<>();
        for (Map.Entry<LabelKey, Node<S>> entry : best.entrySet()) {
            boolean labelOpen = !closedLabels.contains(entry.getKey());
            openStateLabels.merge(entry.getValue().state().id(), labelOpen, Boolean::logicalOr);
        }
        for (Map.Entry<String, Boolean> entry : openStateLabels.entrySet()) {
            CandidateEvidence item = evidence.get(entry.getKey());
            if (item == null || (item.status != null
                    && item.status != SFMTrajectoryContract.SearchCandidateStatus.OPEN
                    && item.status != SFMTrajectoryContract.SearchCandidateStatus.CLOSED)) {
                continue;
            }
            if (entry.getValue()) item.markOpen();
            else item.markClosed();
        }

        long elapsedMillis = elapsedMillis(startedAt, request.clock().nanoTime());
        List<SFMTrajectoryContract.SearchCandidate> candidates = evidence.values().stream()
                .map(CandidateEvidence::toContract)
                .sorted(Comparator.comparing(SFMTrajectoryContract.SearchCandidate::stateId))
                .toList();
        List<SFMTrajectoryContract.TrajectoryRoute> routes = goal == null
                ? List.of()
                : List.of(toRoute(request, goal));
        Optional<String> selectedRouteId = goal == null
                ? Optional.empty()
                : Optional.of(request.routeId());
        SFMTrajectoryContract.OptimalityClaim claim = goal != null && request.heuristicIdentity().admissible()
                ? SFMTrajectoryContract.OptimalityClaim.MINIMUM_COST_UNDER_DECLARED_GRAPH_AND_POLICY
                : SFMTrajectoryContract.OptimalityClaim.NONE;

        SFMTrajectoryContract.TrajectoryPlanRevision plan = new SFMTrajectoryContract.TrajectoryPlanRevision(
                request.planRevisionId(),
                request.parentPlanRevisionId(),
                request.start().id(),
                request.supervision().revision(),
                request.actionGenerator(),
                request.costPolicy(),
                request.heuristicIdentity(),
                request.algorithm(),
                request.supervision().searchBudget(),
                terminalResult,
                claim,
                routes,
                selectedRouteId,
                candidates,
                explanation(terminalResult, claim, expanded, generated, terminalReasons)
        );

        List<String> frontier = best.entrySet().stream()
                .filter(entry -> !closedLabels.contains(entry.getKey()))
                .map(entry -> entry.getValue().state().id())
                .distinct()
                .sorted()
                .toList();
        return new PlanningResult<>(
                plan,
                expanded,
                generated,
                elapsedMillis,
                List.copyOf(terminalReasons),
                frontier,
                discoveredStates
        );
    }

    public static StepValidation validateNextStep(
            SFMTrajectoryContract.TrajectoryPlanRevision plan,
            String routeId,
            int nextStepIndex,
            String actualStateId
    ) {
        Objects.requireNonNull(plan, "plan");
        String canonicalRouteId = requireText(routeId, "routeId");
        actualStateId = requireText(actualStateId, "actualStateId");
        if (nextStepIndex < 0) throw new IllegalArgumentException("nextStepIndex must not be negative");
        SFMTrajectoryContract.TrajectoryRoute route = plan.routes().stream()
                .filter(candidate -> candidate.id().equals(canonicalRouteId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown route: " + canonicalRouteId));
        if (nextStepIndex > route.steps().size()) {
            throw new IllegalArgumentException("Instruction pointer exceeds the route length");
        }
        if (nextStepIndex == route.steps().size()) {
            String expectedFinalState = route.steps().isEmpty()
                    ? route.startStateId()
                    : route.steps().get(route.steps().size() - 1).predictedStateId();
            if (!expectedFinalState.equals(actualStateId)) {
                return new StepValidation(
                        plan.id(),
                        canonicalRouteId,
                        nextStepIndex,
                        StepReadiness.STALE_PARENT,
                        actualStateId,
                        Optional.empty(),
                        "route completed against a different state; expected " + expectedFinalState
                );
            }
            return new StepValidation(
                    plan.id(),
                    canonicalRouteId,
                    nextStepIndex,
                    StepReadiness.ROUTE_COMPLETE,
                    actualStateId,
                    Optional.empty(),
                    "instruction pointer is at the end of the route"
            );
        }
        SFMTrajectoryContract.TrajectoryStep step = route.steps().get(nextStepIndex);
        if (!step.expectedParentStateId().equals(actualStateId)) {
            return new StepValidation(
                    plan.id(),
                    canonicalRouteId,
                    nextStepIndex,
                    StepReadiness.STALE_PARENT,
                    actualStateId,
                    Optional.of(step),
                    "stale parent; expected " + step.expectedParentStateId() + " but found " + actualStateId
            );
        }
        return new StepValidation(
                plan.id(),
                canonicalRouteId,
                nextStepIndex,
                StepReadiness.READY,
                actualStateId,
                Optional.of(step),
                "parent precondition matches"
        );
    }

    public static SFMTrajectoryContract.TrajectoryMachineState pauseOnStaleParent(
            SFMTrajectoryContract.TrajectoryMachineState machine,
            StepValidation validation,
            String authoritativeActualStateId
    ) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(validation, "validation");
        authoritativeActualStateId = requireText(
                authoritativeActualStateId,
                "authoritativeActualStateId"
        );
        if (validation.readiness() != StepReadiness.STALE_PARENT) return machine;
        if (!validation.actualStateId().equals(authoritativeActualStateId)) {
            throw new IllegalArgumentException("Stale validation no longer describes the authoritative actual state");
        }
        String selectedPlan = machine.selectedTrajectoryRevisionId()
                .orElseThrow(() -> new IllegalArgumentException("A stale validation requires a selected plan"));
        SFMTrajectoryContract.InstructionPointer pointer = machine.instructionPointer()
                .orElseThrow(() -> new IllegalArgumentException("A stale validation requires an instruction pointer"));
        if (!selectedPlan.equals(validation.planRevisionId())
                || !pointer.planRevisionId().equals(validation.planRevisionId())
                || !pointer.routeId().equals(validation.routeId())
                || pointer.nextStepIndex() != validation.nextStepIndex()) {
            throw new IllegalArgumentException("Stale validation does not belong to the machine instruction pointer");
        }
        return new SFMTrajectoryContract.TrajectoryMachineState(
                machine.actualHistoryHeadId(),
                machine.selectedTrajectoryRevisionId(),
                machine.instructionPointer(),
                machine.projectionFrontier(),
                machine.supervisionContractRevision(),
                SFMTrajectoryContract.MachineStatus.STALE_PRECONDITION,
                machine.remainingBudget()
        );
    }

    public static SFMTrajectoryContract.PlanBook appendPlanRevision(
            SFMTrajectoryContract.PlanBook book,
            SFMTrajectoryContract.TrajectoryPlanRevision revision,
            boolean select
    ) {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(revision, "revision");
        ArrayList<SFMTrajectoryContract.TrajectoryPlanRevision> plans = new ArrayList<>(book.plans());
        plans.add(revision);
        return new SFMTrajectoryContract.PlanBook(
                SFMTrajectoryContract.SCHEMA,
                plans,
                select ? Optional.of(revision.id()) : book.selectedPlanRevisionId()
        );
    }

    private static <S> List<String> failedPredicates(
            Request<S> request,
            State<S> state,
            List<SFMTrajectoryContract.Predicate> predicates
    ) {
        ArrayList<String> failures = new ArrayList<>();
        for (SFMTrajectoryContract.Predicate predicate : predicates) {
            PredicateResult result = Objects.requireNonNull(
                    request.predicateEvaluator().evaluate(state, predicate),
                    "predicate evaluation result"
            );
            if (!result.satisfied()) failures.add(predicate.id() + " (" + result.evidence() + ")");
        }
        return List.copyOf(failures);
    }

    private static <S> boolean matchesGoal(Request<S> request, State<S> state) {
        return failedPredicates(request, state, request.supervision().goalPredicates()).isEmpty();
    }

    private static <S> HeuristicEvaluation evaluateHeuristic(Request<S> request, State<S> state) {
        if (request.algorithm() == SFMTrajectoryContract.SearchAlgorithm.DIJKSTRA) {
            return HeuristicEvaluation.valid(0);
        }
        try {
            long value = request.heuristic().applyAsLong(state);
            if (value < 0) {
                return HeuristicEvaluation.invalid("heuristic returned a negative value for " + state.id());
            }
            return HeuristicEvaluation.valid(value);
        } catch (RuntimeException failure) {
            return HeuristicEvaluation.invalid(
                    "heuristic failed for " + state.id() + ": " + failureDescription(failure)
            );
        }
    }

    private record HeuristicEvaluation(long value, Optional<String> failure) {
        private HeuristicEvaluation {
            if (value < 0) throw new IllegalArgumentException("heuristic value must not be negative");
            Objects.requireNonNull(failure, "heuristic failure");
            if (failure.isPresent() && value != 0) {
                throw new IllegalArgumentException("invalid heuristic evaluations cannot carry a value");
            }
        }

        private static HeuristicEvaluation valid(long value) {
            return new HeuristicEvaluation(value, Optional.empty());
        }

        private static HeuristicEvaluation invalid(String failure) {
            return new HeuristicEvaluation(0, Optional.of(requireText(failure, "heuristic failure")));
        }

        private boolean valid() {
            return failure.isEmpty();
        }
    }

    private static <S> EffectDecision effectDecision(
            Request<S> request,
            SFMHistoryGraphContract.EffectClass effectClass
    ) {
        if (request.supervision().forbiddenEffectClasses().contains(effectClass)) {
            return EffectDecision.FORBIDDEN;
        }
        if (effectClass == SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE) {
            return EffectDecision.EXTERNAL_BARRIER;
        }
        if (effectClass == SFMHistoryGraphContract.EffectClass.UNKNOWN) {
            return EffectDecision.UNKNOWN_BARRIER;
        }
        if (!request.authorizedEffectClasses().contains(effectClass)) {
            return EffectDecision.UNAUTHORIZED_BARRIER;
        }
        return EffectDecision.ALLOW;
    }

    private enum EffectDecision {
        ALLOW(null, null, "allowed"),
        FORBIDDEN(
                SFMTrajectoryContract.SearchCandidateStatus.PRUNED,
                TerminalReason.FORBIDDEN_EFFECT,
                "forbidden effect class"
        ),
        EXTERNAL_BARRIER(
                SFMTrajectoryContract.SearchCandidateStatus.BARRIER,
                TerminalReason.EXTERNAL_IRREVERSIBLE_EFFECT,
                "external irreversible effect barrier"
        ),
        UNKNOWN_BARRIER(
                SFMTrajectoryContract.SearchCandidateStatus.BARRIER,
                TerminalReason.UNKNOWN_EFFECT,
                "unknown effect barrier"
        ),
        UNAUTHORIZED_BARRIER(
                SFMTrajectoryContract.SearchCandidateStatus.BARRIER,
                TerminalReason.UNAUTHORIZED_EFFECT,
                "effect capability was not authorized"
        );

        private final SFMTrajectoryContract.SearchCandidateStatus status;
        private final TerminalReason terminalReason;
        private final String reason;

        EffectDecision(
                SFMTrajectoryContract.SearchCandidateStatus status,
                TerminalReason terminalReason,
                String reason
        ) {
            this.status = status;
            this.terminalReason = terminalReason;
            this.reason = reason;
        }
    }

    private static <S> boolean isBetter(Node<S> candidate, Node<S> previous) {
        if (previous == null) return true;
        int cost = Long.compare(candidate.accumulatedCost(), previous.accumulatedCost());
        if (cost != 0) return cost < 0;
        int depth = Integer.compare(candidate.depth(), previous.depth());
        if (depth != 0) return depth < 0;
        return candidate.pathOrderingKey().compareTo(previous.pathOrderingKey()) < 0;
    }

    private static <S> SFMTrajectoryContract.TrajectoryRoute toRoute(Request<S> request, Node<S> goal) {
        ArrayDeque<Node<S>> path = new ArrayDeque<>();
        Node<S> cursor = goal;
        while (cursor.parent() != null) {
            path.addFirst(cursor);
            cursor = cursor.parent();
        }
        ArrayList<SFMTrajectoryContract.TrajectoryStep> steps = new ArrayList<>();
        int index = 0;
        for (Node<S> node : path) {
            Transition<S> transition = Objects.requireNonNull(node.incoming(), "path transition");
            steps.add(new SFMTrajectoryContract.TrajectoryStep(
                    request.planRevisionId() + "/step/" + index + "/" + transition.id(),
                    Objects.requireNonNull(node.parent(), "path parent").state().id(),
                    transition.actionIntent(),
                    transition.evaluationPolicy(),
                    transition.predictedOutcomeId(),
                    node.state().id(),
                    transition.effectClass(),
                    transition.cost(),
                    node.accumulatedCost(),
                    node.heuristic()
            ));
            index++;
        }
        return new SFMTrajectoryContract.TrajectoryRoute(
                request.routeId(),
                request.start().id(),
                steps,
                goal.accumulatedCost(),
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
        );
    }

    private static String explanation(
            SFMTrajectoryContract.PlanResult result,
            SFMTrajectoryContract.OptimalityClaim claim,
            long expanded,
            long generated,
            Set<TerminalReason> terminalReasons
    ) {
        String metrics = " after " + expanded + " expansions and " + generated + " generated transitions";
        String causes = "; terminal reasons=" + terminalReasons.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Enum::name)
                .toList();
        return switch (result) {
            case FOUND -> (claim == SFMTrajectoryContract.OptimalityClaim.NONE
                    ? "route found without a minimum-cost claim"
                    : "minimum-cost route found under the declared finite generator and cost policy") + metrics + causes;
            case EXHAUSTED -> "finite generated graph exhausted without a matching goal" + metrics + causes;
            case BUDGET_EXHAUSTED -> "search budget exhausted" + metrics + causes;
            case BLOCKED -> "all discovered routes were blocked or pruned by declared policy" + metrics + causes;
            case CANCELLED -> "search cancelled" + metrics + causes;
        };
    }

    private static boolean timeBudgetExhausted(Request<?> request, long startedAt) {
        return elapsedMillis(startedAt, request.clock().nanoTime())
                >= request.supervision().searchBudget().maxElapsedMillis();
    }

    private static boolean isBlockingReason(TerminalReason reason) {
        return switch (reason) {
            case HARD_INVARIANT_FAILED,
                    FORBIDDEN_EFFECT,
                    EXTERNAL_IRREVERSIBLE_EFFECT,
                    UNKNOWN_EFFECT,
                    UNAUTHORIZED_EFFECT,
                    EXECUTION_STEP_LIMIT,
                    INVALID_HEURISTIC,
                    UNBOUNDED_GENERATOR,
                    GENERATOR_FAILURE,
                    GENERATOR_ORDER_VIOLATION,
                    STATE_ID_COLLISION,
                    COST_OVERFLOW -> true;
            case FOUND,
                    GRAPH_EXHAUSTED,
                    CANCELLED,
                    EXPANSION_BUDGET_EXHAUSTED,
                    GENERATION_BUDGET_EXHAUSTED,
                    ELAPSED_TIME_BUDGET_EXHAUSTED -> false;
        };
    }

    private static String failureDescription(RuntimeException failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static long elapsedMillis(long startedAt, long now) {
        if (now <= startedAt) return 0;
        return TimeUnit.NANOSECONDS.toMillis(now - startedAt);
    }

    private static <S> CandidateEvidence evidenceFor(Map<String, CandidateEvidence> evidence, Node<S> node) {
        return evidence.computeIfAbsent(
                node.state().id(),
                ignored -> new CandidateEvidence(
                        node.state().id(),
                        node.accumulatedCost(),
                        node.heuristic(),
                        node.parent() == null ? Optional.empty() : Optional.of(node.parent().state().id()),
                        node.incoming() == null
                                ? Optional.empty()
                                : Optional.of(node.incoming().actionIntent().id())
                )
        );
    }

    private static final class CandidateEvidence {
        private final String stateId;
        private long accumulatedCost;
        private long estimatedRemainingCost;
        private SFMTrajectoryContract.SearchCandidateStatus status;
        private Optional<String> predecessorStateId;
        private Optional<String> actionIntentId;
        private int evidenceDepth = Integer.MAX_VALUE;
        private String evidencePathOrderingKey;
        private final TreeSet<String> reasons = new TreeSet<>();

        private CandidateEvidence(
                String stateId,
                long accumulatedCost,
                long estimatedRemainingCost,
                Optional<String> predecessorStateId,
                Optional<String> actionIntentId
        ) {
            this.stateId = stateId;
            this.accumulatedCost = accumulatedCost;
            this.estimatedRemainingCost = estimatedRemainingCost;
            this.predecessorStateId = predecessorStateId;
            this.actionIntentId = actionIntentId;
        }

        private <S> void replaceBest(Node<S> node) {
            if (evidencePathOrderingKey != null) {
                int costComparison = Long.compare(node.accumulatedCost(), accumulatedCost);
                if (costComparison > 0) return;
                if (costComparison == 0) {
                    int depthComparison = Integer.compare(node.depth(), evidenceDepth);
                    if (depthComparison > 0) return;
                    if (depthComparison == 0
                            && node.pathOrderingKey().compareTo(evidencePathOrderingKey) >= 0) {
                        return;
                    }
                }
            }
            accumulatedCost = node.accumulatedCost();
            estimatedRemainingCost = node.heuristic();
            evidenceDepth = node.depth();
            evidencePathOrderingKey = node.pathOrderingKey();
            predecessorStateId = node.parent() == null
                    ? Optional.empty()
                    : Optional.of(node.parent().state().id());
            actionIntentId = node.incoming() == null
                    ? Optional.empty()
                    : Optional.of(node.incoming().actionIntent().id());
            status = SFMTrajectoryContract.SearchCandidateStatus.OPEN;
        }

        private void addReason(String reason) {
            reasons.add(requireText(reason, "candidate reason"));
        }

        private void promote(SFMTrajectoryContract.SearchCandidateStatus candidate) {
            if (candidate == null) return;
            if (status == null || statusRank(candidate) > statusRank(status)) status = candidate;
        }

        private void markOpen() {
            status = SFMTrajectoryContract.SearchCandidateStatus.OPEN;
        }

        private void markClosed() {
            status = SFMTrajectoryContract.SearchCandidateStatus.CLOSED;
        }

        private void markBarrier() {
            status = SFMTrajectoryContract.SearchCandidateStatus.BARRIER;
        }

        private SFMTrajectoryContract.SearchCandidate toContract() {
            SFMTrajectoryContract.SearchCandidateStatus finalStatus = status == null
                    ? SFMTrajectoryContract.SearchCandidateStatus.OPEN
                    : status;
            String reason = reasons.isEmpty() ? "no additional evidence" : String.join("; ", reasons);
            return new SFMTrajectoryContract.SearchCandidate(
                    stateId,
                    accumulatedCost,
                    estimatedRemainingCost,
                    finalStatus,
                    predecessorStateId,
                    actionIntentId,
                    reason
            );
        }

        private static int statusRank(SFMTrajectoryContract.SearchCandidateStatus status) {
            return switch (status) {
                case PRUNED -> 0;
                case BARRIER -> 1;
                case OPEN -> 2;
                case CLOSED -> 3;
            };
        }
    }

    private record Node<S>(
            State<S> state,
            long accumulatedCost,
            long heuristic,
            int depth,
            String pathOrderingKey,
            Node<S> parent,
            Transition<S> incoming
    ) {
        private Node {
            Objects.requireNonNull(state, "node.state");
            if (accumulatedCost < 0 || heuristic < 0 || depth < 0) {
                throw new IllegalArgumentException("Node metrics must not be negative");
            }
            pathOrderingKey = Objects.requireNonNull(pathOrderingKey, "node.pathOrderingKey");
            Math.addExact(accumulatedCost, heuristic);
        }

        private static <S> Node<S> start(State<S> state, long heuristic) {
            return new Node<>(state, 0, heuristic, 0, "", null, null);
        }

        private long estimatedTotalCost() {
            return Math.addExact(accumulatedCost, heuristic);
        }

        private LabelKey labelKey() {
            return new LabelKey(state.id(), depth);
        }
    }

    /** A bounded-step search label; state identity alone is insufficient under a path-length constraint. */
    private record LabelKey(String stateId, int depth) {
        private LabelKey {
            stateId = requireText(stateId, "label.stateId");
            if (depth < 0) throw new IllegalArgumentException("label.depth must not be negative");
        }
    }

    private static Optional<String> requireOptionalText(Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(item -> requireText(item, label));
    }

    private static void stringNotEqual(String value, Optional<String> other, String message) {
        if (other.isPresent() && value.equals(other.orElseThrow())) throw new IllegalArgumentException(message);
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return value;
    }

    private static String lengthPrefixed(List<String> parts) {
        StringBuilder answer = new StringBuilder();
        for (String part : parts) {
            part = requireText(part, "ordering-key part");
            answer.append(part.length()).append(':').append(part);
        }
        return answer.toString();
    }

    private static List<TerminalReason> canonicalTerminalReasons(List<TerminalReason> values) {
        Objects.requireNonNull(values, "terminalReasons");
        EnumSet<TerminalReason> unique = EnumSet.noneOf(TerminalReason.class);
        for (TerminalReason value : values) {
            Objects.requireNonNull(value, "terminal reason");
            if (!unique.add(value)) throw new IllegalArgumentException("terminalReasons must be unique");
        }
        return List.copyOf(unique);
    }

    private static List<String> sortedUnique(List<String> values, String label) {
        Objects.requireNonNull(values, label);
        HashSet<String> unique = new HashSet<>();
        ArrayList<String> result = new ArrayList<>();
        for (String value : values) {
            value = requireText(value, label);
            if (!unique.add(value)) throw new IllegalArgumentException(label + " must be unique");
            result.add(value);
        }
        result.sort(String::compareTo);
        return List.copyOf(result);
    }
}
