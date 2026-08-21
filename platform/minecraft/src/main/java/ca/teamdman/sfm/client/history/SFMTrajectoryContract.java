package ca.teamdman.sfm.client.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Immutable trajectory-machine and supervision contract for bounded planning. */
public final class SFMTrajectoryContract {
    public static final String SCHEMA = "sfm.trajectory-machine/1";

    private SFMTrajectoryContract() {
    }

    public enum SearchAlgorithm {
        DIJKSTRA,
        A_STAR
    }

    public enum PlanResult {
        FOUND,
        EXHAUSTED,
        BUDGET_EXHAUSTED,
        BLOCKED,
        CANCELLED
    }

    public enum OptimalityClaim {
        NONE,
        MINIMUM_COST_UNDER_DECLARED_GRAPH_AND_POLICY
    }

    public enum SearchCandidateStatus {
        OPEN,
        CLOSED,
        PRUNED,
        BARRIER
    }

    public enum MachineStatus {
        IDLE,
        PLANNING,
        READY,
        RUNNING,
        PAUSED,
        STALE_PRECONDITION,
        BLOCKED,
        COMPLETE
    }

    public enum SupervisionStatus {
        DRAFT,
        PLANNED,
        RUNNING,
        SUPERVISION_READY,
        APPROVED,
        REJECTED,
        BLOCKED
    }

    public enum ApprovalRequirement {
        NONE,
        HUMAN
    }

    public record SearchBudget(long maxExpanded, long maxGenerated, long maxElapsedMillis) {
        public SearchBudget {
            nonNegative(maxExpanded, "maxExpanded");
            nonNegative(maxGenerated, "maxGenerated");
            nonNegative(maxElapsedMillis, "maxElapsedMillis");
        }
    }

    public record RemainingBudget(long expansions, long generated, long elapsedMillis) {
        public RemainingBudget {
            nonNegative(expansions, "expansions");
            nonNegative(generated, "generated");
            nonNegative(elapsedMillis, "elapsedMillis");
        }
    }

    public record ActionGeneratorIdentity(String id, String revision, boolean finite) {
        public ActionGeneratorIdentity {
            id = SFMTrajectoryContract.id(id, "actionGenerator.id");
            revision = SFMTrajectoryContract.id(revision, "actionGenerator.revision");
        }
    }

    public record CostPolicyIdentity(String id, String revision) {
        public CostPolicyIdentity {
            id = SFMTrajectoryContract.id(id, "costPolicy.id");
            revision = SFMTrajectoryContract.id(revision, "costPolicy.revision");
        }
    }

    public record HeuristicIdentity(String id, String revision, boolean admissible) {
        public HeuristicIdentity {
            id = SFMTrajectoryContract.id(id, "heuristic.id");
            revision = SFMTrajectoryContract.id(revision, "heuristic.revision");
        }
    }

    public record Predicate(String id, String kind, String expectedValue) {
        public Predicate {
            id = SFMTrajectoryContract.id(id, "predicate.id");
            kind = SFMTrajectoryContract.id(kind, "predicate.kind");
            expectedValue = SFMTrajectoryContract.id(expectedValue, "predicate.expectedValue");
        }
    }

    public record EvidenceRequirement(String id, String kind, String description) {
        public EvidenceRequirement {
            id = SFMTrajectoryContract.id(id, "evidence.id");
            kind = SFMTrajectoryContract.id(kind, "evidence.kind");
            description = SFMTrajectoryContract.id(description, "evidence.description");
        }
    }

    public record Approval(String authorityId, String decisionId, String approvedStateHash) {
        public Approval {
            authorityId = SFMTrajectoryContract.id(authorityId, "approval.authorityId");
            decisionId = SFMTrajectoryContract.id(decisionId, "approval.decisionId");
            approvedStateHash = SFMTrajectoryContract.id(approvedStateHash, "approval.approvedStateHash");
        }
    }

    public record SupervisionContract(
            String id,
            String revision,
            String startDomainId,
            String startStateRevisionId,
            List<Predicate> goalPredicates,
            List<Predicate> hardInvariants,
            Set<SFMHistoryGraphContract.EffectClass> forbiddenEffectClasses,
            List<EvidenceRequirement> requiredEvidence,
            SearchBudget searchBudget,
            long maxExecutionSteps,
            ApprovalRequirement approvalRequirement,
            SupervisionStatus status,
            Optional<Approval> approval
    ) {
        public SupervisionContract {
            id = SFMTrajectoryContract.id(id, "supervision.id");
            revision = SFMTrajectoryContract.id(revision, "supervision.revision");
            startDomainId = SFMTrajectoryContract.id(startDomainId, "supervision.startDomainId");
            startStateRevisionId = SFMTrajectoryContract.id(startStateRevisionId, "supervision.startStateRevisionId");
            goalPredicates = canonical(goalPredicates, Predicate::id, "goalPredicates");
            if (goalPredicates.isEmpty()) {
                throw new IllegalArgumentException("Supervision requires at least one goal predicate");
            }
            hardInvariants = canonical(hardInvariants, Predicate::id, "hardInvariants");
            Objects.requireNonNull(forbiddenEffectClasses, "forbiddenEffectClasses");
            forbiddenEffectClasses = forbiddenEffectClasses.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(EnumSet.copyOf(forbiddenEffectClasses));
            requiredEvidence = canonical(requiredEvidence, EvidenceRequirement::id, "requiredEvidence");
            Objects.requireNonNull(searchBudget, "searchBudget");
            nonNegative(maxExecutionSteps, "maxExecutionSteps");
            Objects.requireNonNull(approvalRequirement, "approvalRequirement");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(approval, "approval");
            if (status == SupervisionStatus.APPROVED && approval.isEmpty()) {
                throw new IllegalArgumentException("Approved supervision requires an authority decision");
            }
            if (status != SupervisionStatus.APPROVED && approval.isPresent()) {
                throw new IllegalArgumentException("Approval evidence is valid only for APPROVED status");
            }
            if (status == SupervisionStatus.SUPERVISION_READY && approval.isPresent()) {
                throw new IllegalArgumentException("Automation readiness must not carry human approval");
            }
        }
    }

    public record TrajectoryStep(
            String id,
            String expectedParentStateId,
            SFMHistoryGraphContract.ActionIntent actionIntent,
            SFMHistoryGraphContract.EvaluationPolicy evaluationPolicy,
            String predictedOutcomeId,
            String predictedStateId,
            SFMHistoryGraphContract.EffectClass effectClass,
            long stepCost,
            long accumulatedCost,
            long estimatedRemainingCost
    ) {
        public TrajectoryStep {
            id = SFMTrajectoryContract.id(id, "step.id");
            expectedParentStateId = SFMTrajectoryContract.id(expectedParentStateId, "step.expectedParentStateId");
            Objects.requireNonNull(actionIntent, "actionIntent");
            Objects.requireNonNull(evaluationPolicy, "evaluationPolicy");
            predictedOutcomeId = SFMTrajectoryContract.id(predictedOutcomeId, "step.predictedOutcomeId");
            predictedStateId = SFMTrajectoryContract.id(predictedStateId, "step.predictedStateId");
            Objects.requireNonNull(effectClass, "effectClass");
            nonNegative(stepCost, "stepCost");
            nonNegative(accumulatedCost, "accumulatedCost");
            nonNegative(estimatedRemainingCost, "estimatedRemainingCost");
            if (accumulatedCost < stepCost) {
                throw new IllegalArgumentException("Accumulated cost cannot be less than step cost");
            }
            Math.addExact(accumulatedCost, estimatedRemainingCost);
        }

        public long estimatedTotalCost() {
            return Math.addExact(accumulatedCost, estimatedRemainingCost);
        }

        public String tieBreakKey() {
            return actionIntent.orderingKey() + "\u0000" + predictedStateId;
        }
    }

    public record TrajectoryRoute(
            String id,
            String startStateId,
            List<TrajectoryStep> steps,
            long totalCost,
            SFMHistoryGraphContract.ProjectionStatus status
    ) {
        public TrajectoryRoute {
            id = SFMTrajectoryContract.id(id, "route.id");
            startStateId = SFMTrajectoryContract.id(startStateId, "route.startStateId");
            Objects.requireNonNull(steps, "steps");
            steps = List.copyOf(steps);
            nonNegative(totalCost, "totalCost");
            Objects.requireNonNull(status, "status");
            String expectedParent = startStateId;
            long previousCost = 0;
            for (TrajectoryStep step : steps) {
                Objects.requireNonNull(step, "route step");
                if (!step.expectedParentStateId().equals(expectedParent)) {
                    throw new IllegalArgumentException("Trajectory route has a discontinuous parent chain");
                }
                if (step.accumulatedCost() < previousCost) {
                    throw new IllegalArgumentException("Trajectory accumulated cost must be monotonic");
                }
                expectedParent = step.predictedStateId();
                previousCost = step.accumulatedCost();
            }
            long expectedCost = steps.isEmpty() ? 0 : steps.get(steps.size() - 1).accumulatedCost();
            if (totalCost != expectedCost) {
                throw new IllegalArgumentException("Route total cost must equal its final accumulated cost");
            }
        }
    }

    public record SearchCandidate(
            String stateId,
            long accumulatedCost,
            long estimatedRemainingCost,
            SearchCandidateStatus status,
            Optional<String> predecessorStateId,
            Optional<String> actionIntentId,
            String reason
    ) {
        public SearchCandidate {
            stateId = SFMTrajectoryContract.id(stateId, "candidate.stateId");
            nonNegative(accumulatedCost, "candidate.accumulatedCost");
            nonNegative(estimatedRemainingCost, "candidate.estimatedRemainingCost");
            Math.addExact(accumulatedCost, estimatedRemainingCost);
            Objects.requireNonNull(status, "status");
            predecessorStateId = optionalId(predecessorStateId, "candidate.predecessorStateId");
            actionIntentId = optionalId(actionIntentId, "candidate.actionIntentId");
            reason = SFMTrajectoryContract.id(reason, "candidate.reason");
        }
    }

    public record TrajectoryPlanRevision(
            String id,
            Optional<String> parentPlanRevisionId,
            String startStateId,
            String supervisionContractRevision,
            ActionGeneratorIdentity actionGenerator,
            CostPolicyIdentity costPolicy,
            HeuristicIdentity heuristic,
            SearchAlgorithm algorithm,
            SearchBudget budget,
            PlanResult result,
            OptimalityClaim optimalityClaim,
            List<TrajectoryRoute> routes,
            Optional<String> selectedRouteId,
            List<SearchCandidate> exploredCandidates,
            String resultExplanation
    ) {
        public TrajectoryPlanRevision {
            id = SFMTrajectoryContract.id(id, "plan.id");
            parentPlanRevisionId = optionalId(parentPlanRevisionId, "plan.parentPlanRevisionId");
            startStateId = SFMTrajectoryContract.id(startStateId, "plan.startStateId");
            supervisionContractRevision = SFMTrajectoryContract.id(supervisionContractRevision, "plan.supervisionContractRevision");
            Objects.requireNonNull(actionGenerator, "actionGenerator");
            Objects.requireNonNull(costPolicy, "costPolicy");
            Objects.requireNonNull(heuristic, "heuristic");
            Objects.requireNonNull(algorithm, "algorithm");
            Objects.requireNonNull(budget, "budget");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(optimalityClaim, "optimalityClaim");
            routes = canonical(routes, TrajectoryRoute::id, "routes");
            selectedRouteId = optionalId(selectedRouteId, "plan.selectedRouteId");
            exploredCandidates = canonical(
                    exploredCandidates,
                    SearchCandidate::stateId,
                    "exploredCandidates"
            );
            resultExplanation = SFMTrajectoryContract.id(resultExplanation, "plan.resultExplanation");
            if (result == PlanResult.FOUND && routes.isEmpty()) {
                throw new IllegalArgumentException("Found plans require a route");
            }
            if (result != PlanResult.FOUND && selectedRouteId.isPresent()) {
                throw new IllegalArgumentException("Non-found plans cannot select a route");
            }
            if (selectedRouteId.isPresent()) {
                String selected = selectedRouteId.orElseThrow();
                boolean found = false;
                for (TrajectoryRoute route : routes) {
                    if (route.id().equals(selected)) found = true;
                }
                if (!found) throw new IllegalArgumentException("Selected route does not belong to plan");
            }
            for (TrajectoryRoute route : routes) {
                if (!route.startStateId().equals(startStateId)) {
                    throw new IllegalArgumentException("Every route must start at the plan start state");
                }
            }
            if (optimalityClaim != OptimalityClaim.NONE) {
                if (result != PlanResult.FOUND || !actionGenerator.finite() || !heuristic.admissible()) {
                    throw new IllegalArgumentException(
                            "Minimum-cost claims require a found route, finite generator, and admissible heuristic"
                    );
                }
            }
        }
    }

    public record InstructionPointer(String planRevisionId, String routeId, int nextStepIndex) {
        public InstructionPointer {
            planRevisionId = SFMTrajectoryContract.id(planRevisionId, "instructionPointer.planRevisionId");
            routeId = SFMTrajectoryContract.id(routeId, "instructionPointer.routeId");
            if (nextStepIndex < 0) throw new IllegalArgumentException("Instruction pointer cannot be negative");
        }
    }

    public record ProjectionFrontier(
            String planRevisionId,
            long expanded,
            long generated,
            List<String> frontierStateIds,
            SFMHistoryGraphContract.ProjectionStatus status
    ) {
        public ProjectionFrontier {
            planRevisionId = SFMTrajectoryContract.id(planRevisionId, "frontier.planRevisionId");
            nonNegative(expanded, "frontier.expanded");
            nonNegative(generated, "frontier.generated");
            frontierStateIds = immutableSortedIds(frontierStateIds, "frontier.frontierStateIds");
            Objects.requireNonNull(status, "status");
        }
    }

    public record TrajectoryMachineState(
            String actualHistoryHeadId,
            Optional<String> selectedTrajectoryRevisionId,
            Optional<InstructionPointer> instructionPointer,
            Optional<ProjectionFrontier> projectionFrontier,
            String supervisionContractRevision,
            MachineStatus status,
            RemainingBudget remainingBudget
    ) {
        public TrajectoryMachineState {
            actualHistoryHeadId = SFMTrajectoryContract.id(actualHistoryHeadId, "machine.actualHistoryHeadId");
            selectedTrajectoryRevisionId = optionalId(
                    selectedTrajectoryRevisionId,
                    "machine.selectedTrajectoryRevisionId"
            );
            Objects.requireNonNull(instructionPointer, "instructionPointer");
            Objects.requireNonNull(projectionFrontier, "projectionFrontier");
            supervisionContractRevision = SFMTrajectoryContract.id(
                    supervisionContractRevision,
                    "machine.supervisionContractRevision"
            );
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(remainingBudget, "remainingBudget");
            if (instructionPointer.isPresent()) {
                InstructionPointer pointer = instructionPointer.orElseThrow();
                if (selectedTrajectoryRevisionId.isEmpty()
                        || !pointer.planRevisionId().equals(selectedTrajectoryRevisionId.orElseThrow())) {
                    throw new IllegalArgumentException("Instruction pointer must belong to the selected plan");
                }
            }
            if (projectionFrontier.isPresent()) {
                ProjectionFrontier frontier = projectionFrontier.orElseThrow();
                if (selectedTrajectoryRevisionId.isEmpty()
                        || !frontier.planRevisionId().equals(selectedTrajectoryRevisionId.orElseThrow())) {
                    throw new IllegalArgumentException("Projection frontier must belong to the selected plan");
                }
            }
        }
    }

    public record PlanBook(
            String schema,
            List<TrajectoryPlanRevision> plans,
            Optional<String> selectedPlanRevisionId
    ) {
        public PlanBook {
            if (!SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("Unsupported trajectory schema: " + schema);
            }
            plans = canonical(plans, TrajectoryPlanRevision::id, "plans");
            selectedPlanRevisionId = optionalId(selectedPlanRevisionId, "selectedPlanRevisionId");
            Map<String, TrajectoryPlanRevision> index = index(plans, TrajectoryPlanRevision::id);
            for (TrajectoryPlanRevision plan : plans) {
                plan.parentPlanRevisionId().ifPresent(parent -> {
                    TrajectoryPlanRevision parentPlan = requireReference(index, parent, "parent plan");
                    if (parentPlan.id().compareTo(plan.id()) == 0) {
                        throw new IllegalArgumentException("A plan cannot parent itself");
                    }
                    if (!parentPlan.startStateId().equals(plan.startStateId())) {
                        throw new IllegalArgumentException("Sibling replans must preserve their declared start state");
                    }
                });
            }
            selectedPlanRevisionId.ifPresent(selected -> requireReference(index, selected, "selected plan"));
            rejectPlanCycles(index);
        }
    }

    private static void rejectPlanCycles(Map<String, TrajectoryPlanRevision> plans) {
        for (TrajectoryPlanRevision plan : plans.values()) {
            HashSet<String> visited = new HashSet<>();
            Optional<String> cursor = Optional.of(plan.id());
            while (cursor.isPresent()) {
                String id = cursor.orElseThrow();
                if (!visited.add(id)) throw new IllegalArgumentException("Trajectory plan ancestry contains a cycle");
                TrajectoryPlanRevision current = plans.get(id);
                cursor = current == null ? Optional.empty() : current.parentPlanRevisionId();
            }
        }
    }

    private static Optional<String> optionalId(Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(item -> id(item, label));
    }

    private static String id(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return value;
    }

    private static void nonNegative(long value, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must not be negative");
    }

    private static List<String> immutableSortedIds(List<String> values, String label) {
        Objects.requireNonNull(values, label);
        ArrayList<String> answer = new ArrayList<>();
        for (String value : values) answer.add(id(value, label));
        answer.sort(String::compareTo);
        for (int i = 1; i < answer.size(); i++) {
            if (answer.get(i - 1).equals(answer.get(i))) {
                throw new IllegalArgumentException(label + " must be unique");
            }
        }
        return List.copyOf(answer);
    }

    private static <T> List<T> canonical(List<T> values, Function<T, String> key, String label) {
        Objects.requireNonNull(values, label);
        ArrayList<T> answer = new ArrayList<>();
        HashSet<String> identities = new HashSet<>();
        for (T value : values) {
            Objects.requireNonNull(value, label + " item");
            if (!identities.add(key.apply(value))) {
                throw new IllegalArgumentException("Duplicate " + label + " identity: " + key.apply(value));
            }
            answer.add(value);
        }
        answer.sort(Comparator.comparing(key));
        return List.copyOf(answer);
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> key) {
        HashMap<String, T> answer = new HashMap<>();
        values.forEach(value -> answer.put(key.apply(value), value));
        return Collections.unmodifiableMap(answer);
    }

    private static <T> T requireReference(Map<String, T> index, String id, String label) {
        T value = index.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown " + label + ": " + id);
        return value;
    }
}
