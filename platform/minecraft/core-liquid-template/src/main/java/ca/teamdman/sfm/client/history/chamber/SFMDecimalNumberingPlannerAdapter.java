package ca.teamdman.sfm.client.history.chamber;

import ca.teamdman.sfm.client.history.SFMBoundedTrajectoryPlanner;
import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact chamber-to-planner adapter.
 *
 * <p>The generic trajectory contract intentionally contains presentation-safe
 * action intent rather than Java executable payloads. This adapter therefore
 * creates a plan-scoped typed manifest while candidates are generated and
 * proves a one-to-one correspondence with every selected route step before a
 * plan may be executed.</p>
 */
public final class SFMDecimalNumberingPlannerAdapter {
    public static final String ZERO_HEURISTIC_REVISION = "sfm.chamber-zero-heuristic/1";

    private SFMDecimalNumberingPlannerAdapter() {
    }

    public static PlanningBundle plan(
            SFMDecimalNumberingChamber chamber,
            SFMChamberDocumentState start,
            SFMDecimalNumberingChamber.Target target,
            SFMDecimalNumberingChamber.SupervisionDefinition supervision,
            SFMDecimalNumberingChamber.ExternalInvariantEvidence invariantEvidence,
            String planRevisionId,
            Optional<String> parentPlanRevisionId,
            String routeId,
            SFMTrajectoryContract.SearchAlgorithm algorithm,
            SFMBoundedTrajectoryPlanner.CancellationToken cancellationToken,
            SFMBoundedTrajectoryPlanner.NanoClock clock
    ) {
        Objects.requireNonNull(chamber, "chamber");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(supervision, "supervision");
        Objects.requireNonNull(invariantEvidence, "invariantEvidence");
        Objects.requireNonNull(parentPlanRevisionId, "parentPlanRevisionId");
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(clock, "clock");
        if (!chamber.scope().equals(start.scope())
                || !chamber.scope().equals(target.scope())
                || !chamber.scope().equals(supervision.scope())) {
            throw new IllegalArgumentException("Planner adapter inputs belong to different chamber scopes");
        }
        SFMDecimalNumberingChamber.Target canonicalTarget = chamber.requireTarget(start);
        SFMDecimalNumberingChamber.SupervisionDefinition canonicalSupervision =
                chamber.supervision(
                        start,
                        canonicalTarget,
                        supervision.contract().searchBudget()
                );
        if (!target.equals(canonicalTarget) || !supervision.equals(canonicalSupervision)) {
            throw new IllegalArgumentException("Planner adapter requires the canonical start and target supervision");
        }

        Map<TransitionKey, SFMDecimalNumberingChamber.Transition> generated = new HashMap<>();
        SFMBoundedTrajectoryPlanner.SuccessorGenerator<SFMChamberDocumentState> generator = state -> {
            List<SFMBoundedTrajectoryPlanner.Transition<SFMChamberDocumentState>> candidates = chamber
                    .transitions(state.value(), target)
                    .stream()
                    .map(transition -> {
                        SFMBoundedTrajectoryPlanner.Transition<SFMChamberDocumentState> plannerTransition =
                                transition.toPlannerTransition();
                        TransitionKey key = TransitionKey.from(plannerTransition, state.id());
                        SFMDecimalNumberingChamber.Transition previous = generated.putIfAbsent(key, transition);
                        if (previous != null && !previous.equals(transition)) {
                            throw new IllegalStateException("Two executable payloads share one planner transition key");
                        }
                        return plannerTransition;
                    })
                    .sorted(java.util.Comparator.comparing(
                            SFMBoundedTrajectoryPlanner.Transition::orderingKey))
                    .toList();
            return candidates.iterator();
        };

        SFMBoundedTrajectoryPlanner.Request<SFMChamberDocumentState> request =
                new SFMBoundedTrajectoryPlanner.Request<>(
                        planRevisionId,
                        parentPlanRevisionId,
                        routeId,
                        new SFMBoundedTrajectoryPlanner.State<>(start.revisionId(), start),
                        supervision.contract(),
                        chamber.actionGeneratorIdentity(),
                        chamber.costPolicyIdentity(),
                        new SFMTrajectoryContract.HeuristicIdentity(
                                chamber.scope().qualify("heuristic", "zero"),
                                ZERO_HEURISTIC_REVISION,
                                true
                        ),
                        algorithm,
                        generator,
                        (state, predicate) -> {
                            SFMDecimalNumberingChamber.PredicateEvaluation evaluation = chamber.evaluatePredicate(
                                    state.value(),
                                    predicate,
                                    Optional.of(invariantEvidence)
                            );
                            return new SFMBoundedTrajectoryPlanner.PredicateResult(
                                    evaluation.satisfied(),
                                    evaluation.evidence()
                            );
                        },
                        ignored -> 0L,
                        EnumSet.of(SFMHistoryGraphContract.EffectClass.PURE),
                        cancellationToken,
                        clock
                );
        SFMBoundedTrajectoryPlanner.PlanningResult<SFMChamberDocumentState> result =
                SFMBoundedTrajectoryPlanner.plan(request);
        ExecutionManifest manifest = ExecutionManifest.from(result.plan(), generated);
        return new PlanningBundle(result, manifest);
    }

    public record PlanningBundle(
            SFMBoundedTrajectoryPlanner.PlanningResult<SFMChamberDocumentState> result,
            ExecutionManifest manifest
    ) {
        public PlanningBundle {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(manifest, "manifest");
            if (!result.plan().id().equals(manifest.planRevisionId())) {
                throw new IllegalArgumentException("Planning result and execution manifest disagree");
            }
        }
    }

    public record ExecutionManifest(
            String id,
            String planRevisionId,
            Map<String, ManifestStep> stepsById
    ) {
        public ExecutionManifest {
            id = requireText(id, "manifest.id");
            planRevisionId = requireText(planRevisionId, "manifest.planRevisionId");
            Objects.requireNonNull(stepsById, "stepsById");
            stepsById = Map.copyOf(stepsById);
            stepsById.forEach((stepId, step) -> {
                requireText(stepId, "manifest step id");
                Objects.requireNonNull(step, "manifest step");
                if (!stepId.equals(step.trajectoryStep().id())) {
                    throw new IllegalArgumentException("Manifest step map key does not match its trajectory step");
                }
            });
        }

        static ExecutionManifest from(
                SFMTrajectoryContract.TrajectoryPlanRevision plan,
                Map<TransitionKey, SFMDecimalNumberingChamber.Transition> generated
        ) {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(generated, "generated");
            HashMap<String, ManifestStep> steps = new HashMap<>();
            for (SFMTrajectoryContract.TrajectoryRoute route : plan.routes()) {
                for (SFMTrajectoryContract.TrajectoryStep step : route.steps()) {
                    TransitionKey key = TransitionKey.from(step);
                    SFMDecimalNumberingChamber.Transition transition = generated.get(key);
                    if (transition == null) {
                        throw new IllegalStateException(
                                "No exact executable payload was retained for trajectory step " + step.id()
                        );
                    }
                    ManifestStep manifestStep = new ManifestStep(route.id(), step, transition);
                    if (steps.putIfAbsent(step.id(), manifestStep) != null) {
                        throw new IllegalStateException("Duplicate trajectory step identity in manifest");
                    }
                }
            }
            int plannedSteps = plan.routes().stream().mapToInt(route -> route.steps().size()).sum();
            if (steps.size() != plannedSteps) {
                throw new IllegalStateException("Execution manifest is not one-to-one with planned steps");
            }
            String manifestId = plan.id() + "/manifest/" + SFMChamberDocumentState.fingerprint(
                    plan.id(),
                    Integer.toString(plannedSteps),
                    String.join("\n", steps.keySet().stream().sorted().toList())
            ).substring("sha256:".length());
            return new ExecutionManifest(manifestId, plan.id(), steps);
        }

        public ManifestStep requireStep(SFMTrajectoryContract.TrajectoryStep step) {
            Objects.requireNonNull(step, "step");
            ManifestStep manifest = stepsById.get(step.id());
            if (manifest == null || !manifest.trajectoryStep().equals(step)) {
                throw new IllegalStateException("Plan-scoped execution manifest does not contain the exact step");
            }
            return manifest;
        }
    }

    public record ManifestStep(
            String routeId,
            SFMTrajectoryContract.TrajectoryStep trajectoryStep,
            SFMDecimalNumberingChamber.Transition executableTransition
    ) {
        public ManifestStep {
            routeId = requireText(routeId, "manifestStep.routeId");
            Objects.requireNonNull(trajectoryStep, "trajectoryStep");
            Objects.requireNonNull(executableTransition, "executableTransition");
            TransitionKey expected = TransitionKey.from(trajectoryStep);
            TransitionKey actual = TransitionKey.from(
                    executableTransition.toPlannerTransition(),
                    executableTransition.parent().revisionId()
            );
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException("Manifest executable payload does not match its trajectory step");
            }
        }
    }

    private record TransitionKey(
            String expectedParentStateId,
            String intentId,
            String predictedOutcomeId,
            String predictedStateId,
            SFMHistoryGraphContract.EvaluationPolicy evaluationPolicy,
            SFMHistoryGraphContract.EffectClass effectClass,
            long cost
    ) {
        private TransitionKey {
            expectedParentStateId = requireText(expectedParentStateId, "transitionKey.expectedParentStateId");
            intentId = requireText(intentId, "transitionKey.intentId");
            predictedOutcomeId = requireText(predictedOutcomeId, "transitionKey.predictedOutcomeId");
            predictedStateId = requireText(predictedStateId, "transitionKey.predictedStateId");
            Objects.requireNonNull(evaluationPolicy, "evaluationPolicy");
            Objects.requireNonNull(effectClass, "effectClass");
            if (cost < 0) throw new IllegalArgumentException("transition key cost must not be negative");
        }

        static TransitionKey from(
                SFMBoundedTrajectoryPlanner.Transition<SFMChamberDocumentState> transition,
                String expectedParentStateId
        ) {
            return new TransitionKey(
                    expectedParentStateId,
                    transition.actionIntent().id(),
                    transition.predictedOutcomeId(),
                    transition.resultingState().id(),
                    transition.evaluationPolicy(),
                    transition.effectClass(),
                    transition.cost()
            );
        }

        static TransitionKey from(SFMTrajectoryContract.TrajectoryStep step) {
            return new TransitionKey(
                    step.expectedParentStateId(),
                    step.actionIntent().id(),
                    step.predictedOutcomeId(),
                    step.predictedStateId(),
                    step.evaluationPolicy(),
                    step.effectClass(),
                    step.stepCost()
            );
        }
    }

    private static String requireText(String value, String label) {
        value = SFMChamberDocumentState.requireWellFormedUtf16(value, label);
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return value;
    }
}
