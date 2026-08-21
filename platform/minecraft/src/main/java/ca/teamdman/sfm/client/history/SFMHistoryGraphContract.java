package ca.teamdman.sfm.client.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;

/** Versioned Java-local action/history graph seam used by the TE-S1M chamber. */
public final class SFMHistoryGraphContract {
    public static final String SCHEMA = "sfm.history-graph/1";

    private SFMHistoryGraphContract() {
    }

    public enum EffectClass {
        PURE,
        SNAPSHOT_RESTORABLE,
        REEXECUTABLE_WITH_CAPABILITY,
        EXTERNAL_IRREVERSIBLE,
        UNKNOWN
    }

    public enum ProjectionStatus {
        UNREQUESTED,
        QUEUED,
        RUNNING,
        MATERIALIZED,
        CONFLICT,
        CANCELLED,
        BUDGET_EXHAUSTED,
        UNKNOWN,
        EXTERNAL_BARRIER
    }

    public enum EvaluationPolicy {
        REUSE_RECORDED_TRANSITION,
        FROZEN_WITNESS_REEXECUTION,
        INTENT_REEVALUATION
    }

    public enum OutcomeStatus {
        SUCCEEDED,
        REJECTED,
        CONFLICT,
        CANCELLED,
        BUDGET_EXHAUSTED,
        EXTERNAL_BARRIER
    }

    public enum UndoDomainKind {
        FOCUSED,
        DOCUMENT,
        SELECTION,
        WORKSPACE,
        EPISODE
    }

    public enum HeadMovementKind {
        UNDO,
        REDO,
        CHECKOUT,
        SELECT_BRANCH
    }

    public enum RetentionKind {
        COMMENT,
        RECIPE,
        NAMED_BRANCH,
        EXPORT,
        OPEN_INSPECTOR
    }

    public record UndoDomain(UndoDomainKind kind, String domainId) {
        public UndoDomain {
            Objects.requireNonNull(kind, "kind");
            domainId = SFMHistoryGraphContract.id(domainId, "domainId");
        }
    }

    public record ActionIntent(
            String id,
            String actionId,
            List<String> arguments,
            String intentHash
    ) {
        public ActionIntent {
            id = SFMHistoryGraphContract.id(id, "intent.id");
            actionId = SFMHistoryGraphContract.id(actionId, "intent.actionId");
            arguments = immutableText(arguments, "intent.arguments", false);
            intentHash = SFMHistoryGraphContract.id(intentHash, "intent.intentHash");
        }

        public String orderingKey() {
            return actionId + "\u0000" + String.join("\u0000", arguments) + "\u0000" + intentHash;
        }
    }

    public record DependencyWitness(String kind, String identity, String revision) {
        public DependencyWitness {
            kind = SFMHistoryGraphContract.id(kind, "witness.kind");
            identity = SFMHistoryGraphContract.id(identity, "witness.identity");
            revision = SFMHistoryGraphContract.id(revision, "witness.revision");
        }
    }

    public record ActionEvaluation(
            String id,
            String intentId,
            String expectedParentStateId,
            String evaluatorRevision,
            EvaluationPolicy policy,
            List<DependencyWitness> dependencyWitnesses,
            String predictedOutcomeId,
            ProjectionStatus status
    ) {
        public ActionEvaluation {
            id = SFMHistoryGraphContract.id(id, "evaluation.id");
            intentId = SFMHistoryGraphContract.id(intentId, "evaluation.intentId");
            expectedParentStateId = SFMHistoryGraphContract.id(expectedParentStateId, "evaluation.expectedParentStateId");
            evaluatorRevision = SFMHistoryGraphContract.id(evaluatorRevision, "evaluation.evaluatorRevision");
            Objects.requireNonNull(policy, "policy");
            Objects.requireNonNull(dependencyWitnesses, "dependencyWitnesses");
            dependencyWitnesses = dependencyWitnesses.stream()
                    .sorted(Comparator.comparing(DependencyWitness::kind)
                            .thenComparing(DependencyWitness::identity)
                            .thenComparing(DependencyWitness::revision))
                    .toList();
            predictedOutcomeId = SFMHistoryGraphContract.id(predictedOutcomeId, "evaluation.predictedOutcomeId");
            Objects.requireNonNull(status, "status");
        }
    }

    public record ActionOutcome(
            String id,
            String evaluationId,
            OutcomeStatus status,
            Optional<String> resultingStateId,
            List<String> evidence
    ) {
        public ActionOutcome {
            id = SFMHistoryGraphContract.id(id, "outcome.id");
            evaluationId = SFMHistoryGraphContract.id(evaluationId, "outcome.evaluationId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(resultingStateId, "resultingStateId");
            resultingStateId = resultingStateId.map(value -> SFMHistoryGraphContract.id(value, "outcome.resultingStateId"));
            evidence = immutableText(evidence, "outcome.evidence", true);
            if (status == OutcomeStatus.SUCCEEDED && resultingStateId.isEmpty()) {
                throw new IllegalArgumentException("Successful outcomes require a resulting state");
            }
        }
    }

    public record StateRevision(
            String id,
            List<String> parentRevisionIds,
            String stateHash,
            boolean committed,
            ProjectionStatus status
    ) {
        public StateRevision {
            id = SFMHistoryGraphContract.id(id, "state.id");
            parentRevisionIds = immutableSortedIds(parentRevisionIds, "state.parentRevisionIds");
            stateHash = SFMHistoryGraphContract.id(stateHash, "state.stateHash");
            Objects.requireNonNull(status, "status");
            if (committed && status != ProjectionStatus.MATERIALIZED) {
                throw new IllegalArgumentException("Committed states must be materialized");
            }
        }
    }

    public record HistoryHead(
            String id,
            UndoDomain domain,
            String stateRevisionId,
            Optional<String> name
    ) {
        public HistoryHead {
            id = SFMHistoryGraphContract.id(id, "head.id");
            Objects.requireNonNull(domain, "domain");
            stateRevisionId = SFMHistoryGraphContract.id(stateRevisionId, "head.stateRevisionId");
            Objects.requireNonNull(name, "name");
            name = name.map(value -> SFMHistoryGraphContract.id(value, "head.name"));
        }
    }

    public record HeadMovement(
            String id,
            HeadMovementKind kind,
            String headId,
            String fromStateRevisionId,
            String toStateRevisionId,
            List<String> candidateStateRevisionIds,
            String actor,
            String requestId
    ) {
        public HeadMovement {
            id = SFMHistoryGraphContract.id(id, "headMovement.id");
            Objects.requireNonNull(kind, "kind");
            headId = SFMHistoryGraphContract.id(headId, "headMovement.headId");
            fromStateRevisionId = SFMHistoryGraphContract.id(fromStateRevisionId, "headMovement.fromStateRevisionId");
            toStateRevisionId = SFMHistoryGraphContract.id(toStateRevisionId, "headMovement.toStateRevisionId");
            candidateStateRevisionIds = immutableSortedIds(
                    candidateStateRevisionIds,
                    "headMovement.candidateStateRevisionIds"
            );
            actor = SFMHistoryGraphContract.id(actor, "headMovement.actor");
            requestId = SFMHistoryGraphContract.id(requestId, "headMovement.requestId");
        }
    }

    public record BranchEdge(
            String id,
            String parentStateRevisionId,
            String childStateRevisionId,
            Optional<String> intentId,
            Optional<String> evaluationId,
            Optional<String> outcomeId,
            EffectClass effectClass,
            ProjectionStatus status,
            boolean committed
    ) {
        public BranchEdge {
            id = SFMHistoryGraphContract.id(id, "edge.id");
            parentStateRevisionId = SFMHistoryGraphContract.id(parentStateRevisionId, "edge.parentStateRevisionId");
            childStateRevisionId = SFMHistoryGraphContract.id(childStateRevisionId, "edge.childStateRevisionId");
            intentId = optionalId(intentId, "edge.intentId");
            evaluationId = optionalId(evaluationId, "edge.evaluationId");
            outcomeId = optionalId(outcomeId, "edge.outcomeId");
            Objects.requireNonNull(effectClass, "effectClass");
            Objects.requireNonNull(status, "status");
            if (committed && status != ProjectionStatus.MATERIALIZED) {
                throw new IllegalArgumentException("Committed edges must be materialized");
            }
            if (!committed && effectClass == EffectClass.EXTERNAL_IRREVERSIBLE
                    && status != ProjectionStatus.EXTERNAL_BARRIER) {
                throw new IllegalArgumentException("Projected irreversible effects must be explicit barriers");
            }
        }
    }

    public record RetentionPin(String id, RetentionKind kind, String targetId, String owner) {
        public RetentionPin {
            id = SFMHistoryGraphContract.id(id, "pin.id");
            Objects.requireNonNull(kind, "kind");
            targetId = SFMHistoryGraphContract.id(targetId, "pin.targetId");
            owner = SFMHistoryGraphContract.id(owner, "pin.owner");
        }
    }

    public record Graph(
            String schema,
            List<ActionIntent> intents,
            List<ActionEvaluation> evaluations,
            List<ActionOutcome> outcomes,
            List<StateRevision> states,
            List<HistoryHead> heads,
            List<HeadMovement> headMovements,
            List<BranchEdge> edges,
            List<RetentionPin> retentionPins
    ) {
        public Graph {
            if (!SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("Unsupported history graph schema: " + schema);
            }
            intents = canonical(intents, ActionIntent::id, "intents");
            evaluations = canonical(evaluations, ActionEvaluation::id, "evaluations");
            outcomes = canonical(outcomes, ActionOutcome::id, "outcomes");
            states = canonical(states, StateRevision::id, "states");
            heads = canonical(heads, HistoryHead::id, "heads");
            headMovements = canonical(headMovements, HeadMovement::id, "headMovements");
            edges = canonical(edges, BranchEdge::id, "edges");
            retentionPins = canonical(retentionPins, RetentionPin::id, "retentionPins");

            Map<String, ActionIntent> intentIndex = index(intents, ActionIntent::id);
            Map<String, ActionEvaluation> evaluationIndex = index(evaluations, ActionEvaluation::id);
            Map<String, ActionOutcome> outcomeIndex = index(outcomes, ActionOutcome::id);
            Map<String, StateRevision> stateIndex = index(states, StateRevision::id);
            Map<String, HistoryHead> headIndex = index(heads, HistoryHead::id);

            for (StateRevision state : states) {
                state.parentRevisionIds().forEach(parent -> requireReference(stateIndex, parent, "state parent"));
            }
            for (ActionEvaluation evaluation : evaluations) {
                requireReference(intentIndex, evaluation.intentId(), "evaluation intent");
                requireReference(stateIndex, evaluation.expectedParentStateId(), "evaluation parent state");
                ActionOutcome outcome = requireReference(
                        outcomeIndex,
                        evaluation.predictedOutcomeId(),
                        "evaluation outcome"
                );
                if (!outcome.evaluationId().equals(evaluation.id())) {
                    throw new IllegalArgumentException("Evaluation and outcome identities disagree");
                }
            }
            for (ActionOutcome outcome : outcomes) {
                requireReference(evaluationIndex, outcome.evaluationId(), "outcome evaluation");
                outcome.resultingStateId().ifPresent(state -> requireReference(stateIndex, state, "outcome state"));
            }
            for (HistoryHead head : heads) requireReference(stateIndex, head.stateRevisionId(), "head state");
            for (HeadMovement movement : headMovements) {
                requireReference(headIndex, movement.headId(), "head movement head");
                requireReference(stateIndex, movement.fromStateRevisionId(), "head movement source");
                requireReference(stateIndex, movement.toStateRevisionId(), "head movement target");
                movement.candidateStateRevisionIds()
                        .forEach(candidate -> requireReference(stateIndex, candidate, "head movement candidate"));
            }
            for (BranchEdge edge : edges) {
                requireReference(stateIndex, edge.parentStateRevisionId(), "edge parent");
                requireReference(stateIndex, edge.childStateRevisionId(), "edge child");
                edge.intentId().ifPresent(intent -> requireReference(intentIndex, intent, "edge intent"));
                edge.evaluationId().ifPresent(evaluation ->
                        requireReference(evaluationIndex, evaluation, "edge evaluation"));
                edge.outcomeId().ifPresent(outcome -> requireReference(outcomeIndex, outcome, "edge outcome"));
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

    private static List<String> immutableText(List<String> values, String label, boolean sort) {
        Objects.requireNonNull(values, label);
        ArrayList<String> answer = new ArrayList<>();
        for (String value : values) answer.add(id(value, label));
        if (sort) answer.sort(String::compareTo);
        return List.copyOf(answer);
    }

    private static List<String> immutableSortedIds(List<String> values, String label) {
        List<String> answer = immutableText(values, label, false);
        TreeSet<String> unique = new TreeSet<>(answer);
        if (unique.size() != answer.size()) throw new IllegalArgumentException(label + " must be unique");
        return List.copyOf(unique);
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
