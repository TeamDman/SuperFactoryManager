package ca.teamdman.sfm.client.history.replay;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Versioned Java-local causal archive for the bounded temporal-numbering chamber.
 *
 * <p>The archive deliberately retains complete text frames and every causal
 * layer. It is a truthful replay artifact, not an optimized persistence format:
 * controller/test operations are recorded as such and are never disguised as
 * physical keyboard input.</p>
 */
public final class SFMTemporalReplayArchive {
    public static final String SCHEMA = "sfm.temporal-replay-archive/1";
    public static final String BINDING_SNAPSHOT_SCHEMA = "sfm.effective-binding-snapshot/1";
    public static final String SELECTION_EXPRESSION_SCHEMA = "sfm.selection-expression/1";
    public static final int MAX_RECORDS_PER_KIND = 65_536;
    public static final int MAX_FRAME_UTF8_BYTES = 4 * 1024 * 1024;
    public static final int MAX_TOTAL_FRAME_UTF8_BYTES = 32 * 1024 * 1024;

    private SFMTemporalReplayArchive() {
    }

    public enum EventKind {
        KEY,
        CHARACTER,
        FOCUS_RESET,
        REGISTERED_ACTION_REQUEST,
        CONTROLLER_OPERATION,
        REPLAY_OPERATION
    }

    public enum EventOrigin {
        PHYSICAL_KEYBOARD,
        CHARACTER_INPUT,
        FOCUS_SYSTEM,
        DYNAMIC_KEY_BINDING,
        REGISTERED_ACTION_SURFACE,
        CONTROLLER_API,
        TEST_HARNESS,
        EXACT_REPLAY,
        SEMANTIC_REBASE
    }

    public enum BindingDecisionStatus {
        MATCHED,
        UNMATCHED,
        RESERVED_PREFIX,
        CONFLICT,
        SUSPENDED,
        NOT_APPLICABLE
    }

    public enum AvailabilityStatus {
        AVAILABLE,
        UNAVAILABLE,
        NOT_CHECKED
    }

    public enum AuthorizationStatus {
        AUTHORIZED,
        DENIED,
        NOT_APPLICABLE
    }

    public enum InvocationStatus {
        REQUESTED,
        DISPATCHED,
        REJECTED,
        CANCELLED
    }

    public enum TransitionStatus {
        SUCCEEDED,
        REJECTED,
        CONFLICT,
        CANCELLED,
        BUDGET_EXHAUSTED
    }

    public enum ReplayMode {
        EXACT_REPLAY,
        SEMANTIC_REBASE
    }

    public enum ReplayStatus {
        SUCCEEDED,
        PRECONDITION_MISMATCH,
        INELIGIBLE_ACTION,
        CONFLICT,
        CANCELLED,
        BUDGET_EXHAUSTED
    }

    public enum HeadMovementKind {
        UNDO,
        REDO,
        CHECKOUT,
        REPLAY_RESULT,
        REBASE_RESULT
    }

    public record KeyStroke(int keyCode, List<String> modifiers) {
        public KeyStroke {
            modifiers = sortedUniqueText(modifiers, "keyStroke.modifiers");
        }

        String canonicalForm() {
            return keyCode + ":" + String.join("+", modifiers);
        }
    }

    public record BindingDefinition(
            String bindingId,
            String actionId,
            String commandDraft,
            String situationId,
            List<KeyStroke> sequence,
            boolean enabled
    ) {
        public BindingDefinition {
            bindingId = text(bindingId, "binding.bindingId");
            actionId = text(actionId, "binding.actionId");
            commandDraft = text(commandDraft, "binding.commandDraft");
            situationId = text(situationId, "binding.situationId");
            sequence = immutable(sequence, "binding.sequence");
            if (sequence.isEmpty()) throw new IllegalArgumentException("Binding sequence must not be empty");
        }

        String canonicalForm() {
            return String.join("\u0000",
                    bindingId,
                    actionId,
                    commandDraft,
                    situationId,
                    Boolean.toString(enabled),
                    sequence.stream().map(KeyStroke::canonicalForm).reduce((left, right) -> left + "," + right)
                            .orElseThrow());
        }
    }

    public record BindingSnapshot(
            String schema,
            String id,
            long revision,
            String digest,
            List<BindingDefinition> bindings
    ) {
        public BindingSnapshot {
            if (!BINDING_SNAPSHOT_SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("Unsupported binding snapshot schema: " + schema);
            }
            id = text(id, "bindingSnapshot.id");
            if (revision < 0) throw new IllegalArgumentException("Binding revision must not be negative");
            bindings = canonical(bindings, BindingDefinition::bindingId, "bindingSnapshot.bindings");
            String computed = bindingDigest(revision, bindings);
            digest = text(digest, "bindingSnapshot.digest");
            if (!digest.equals(computed)) {
                throw new IllegalArgumentException("Binding snapshot digest does not match its content");
            }
        }

        public static BindingSnapshot create(String id, long revision, List<BindingDefinition> bindings) {
            List<BindingDefinition> canonical = canonical(
                    bindings,
                    BindingDefinition::bindingId,
                    "bindingSnapshot.bindings"
            );
            return new BindingSnapshot(
                    BINDING_SNAPSHOT_SCHEMA,
                    id,
                    revision,
                    bindingDigest(revision, canonical),
                    canonical
            );
        }
    }

    public record SourceEvent(
            String id,
            long sequence,
            EventKind kind,
            EventOrigin origin,
            long deterministicTick,
            Optional<Integer> keyCode,
            Optional<String> keyEventType,
            Optional<Integer> codePoint,
            List<String> modifiers,
            Optional<String> payload
    ) {
        public SourceEvent {
            id = text(id, "sourceEvent.id");
            positive(sequence, "sourceEvent.sequence");
            Objects.requireNonNull(kind, "sourceEvent.kind");
            Objects.requireNonNull(origin, "sourceEvent.origin");
            if (deterministicTick < 0) {
                throw new IllegalArgumentException("Source event tick must not be negative");
            }
            keyCode = optionalInteger(keyCode, "sourceEvent.keyCode");
            keyEventType = optionalText(keyEventType, "sourceEvent.keyEventType");
            codePoint = optionalInteger(codePoint, "sourceEvent.codePoint");
            modifiers = sortedUniqueText(modifiers, "sourceEvent.modifiers");
            payload = optionalText(payload, "sourceEvent.payload");
            if (kind == EventKind.KEY) {
                if (keyCode.isEmpty() || keyEventType.isEmpty() || codePoint.isPresent()) {
                    throw new IllegalArgumentException("Key events require keyCode/keyEventType and no codePoint");
                }
            } else if (kind == EventKind.CHARACTER) {
                if (codePoint.isEmpty() || keyCode.isPresent() || keyEventType.isPresent()) {
                    throw new IllegalArgumentException("Character events require codePoint and no key fields");
                }
                int value = codePoint.orElseThrow();
                if (!Character.isValidCodePoint(value) || value >= 0xD800 && value <= 0xDFFF) {
                    throw new IllegalArgumentException("Character event contains an invalid Unicode scalar");
                }
            } else if (keyCode.isPresent() || keyEventType.isPresent() || codePoint.isPresent()) {
                throw new IllegalArgumentException("Non-key/character events must not carry key scalar fields");
            }
            if ((kind == EventKind.REGISTERED_ACTION_REQUEST
                    || kind == EventKind.CONTROLLER_OPERATION
                    || kind == EventKind.REPLAY_OPERATION)
                    && payload.isEmpty()) {
                throw new IllegalArgumentException(kind + " requires an explicit payload");
            }
        }
    }

    public record BindingDecision(
            String id,
            long sequence,
            List<String> sourceEventIds,
            BindingDecisionStatus status,
            Optional<String> bindingSnapshotId,
            Optional<String> matchedBindingId,
            List<String> activeSituationIds,
            List<String> invocationIds,
            boolean consumed,
            List<String> diagnostics
    ) {
        public BindingDecision {
            id = text(id, "bindingDecision.id");
            positive(sequence, "bindingDecision.sequence");
            sourceEventIds = orderedUniqueIds(sourceEventIds, "bindingDecision.sourceEventIds");
            Objects.requireNonNull(status, "bindingDecision.status");
            bindingSnapshotId = optionalText(bindingSnapshotId, "bindingDecision.bindingSnapshotId");
            matchedBindingId = optionalText(matchedBindingId, "bindingDecision.matchedBindingId");
            activeSituationIds = sortedUniqueText(activeSituationIds, "bindingDecision.activeSituationIds");
            invocationIds = orderedUniqueIds(invocationIds, "bindingDecision.invocationIds");
            diagnostics = immutableText(diagnostics, "bindingDecision.diagnostics", false, true);
            if (status == BindingDecisionStatus.NOT_APPLICABLE) {
                if (bindingSnapshotId.isPresent() || matchedBindingId.isPresent()) {
                    throw new IllegalArgumentException("Not-applicable binding decisions must not name a binding");
                }
            } else if (bindingSnapshotId.isEmpty()) {
                throw new IllegalArgumentException("Binding decisions require their effective snapshot");
            }
            if (status == BindingDecisionStatus.MATCHED && matchedBindingId.isEmpty()) {
                throw new IllegalArgumentException("Matched binding decisions require a binding id");
            }
        }
    }

    public record TypedArgument(String name, String type, String value) {
        public TypedArgument {
            name = text(name, "argument.name");
            type = text(type, "argument.type");
            value = nonNullText(value, "argument.value");
        }
    }

    public record ActionInvocation(
            String id,
            long sequence,
            EventOrigin origin,
            String actionId,
            String commandDraft,
            List<TypedArgument> arguments,
            Optional<String> bindingDecisionId,
            List<String> sourceEventIds,
            AvailabilityStatus availability,
            AuthorizationStatus authorization,
            InvocationStatus status,
            List<String> diagnostics
    ) {
        public ActionInvocation {
            id = text(id, "invocation.id");
            positive(sequence, "invocation.sequence");
            Objects.requireNonNull(origin, "invocation.origin");
            actionId = text(actionId, "invocation.actionId");
            commandDraft = text(commandDraft, "invocation.commandDraft");
            arguments = canonical(arguments, TypedArgument::name, "invocation.arguments");
            bindingDecisionId = optionalText(bindingDecisionId, "invocation.bindingDecisionId");
            sourceEventIds = orderedUniqueIds(sourceEventIds, "invocation.sourceEventIds");
            Objects.requireNonNull(availability, "invocation.availability");
            Objects.requireNonNull(authorization, "invocation.authorization");
            Objects.requireNonNull(status, "invocation.status");
            diagnostics = immutableText(diagnostics, "invocation.diagnostics", false, true);
            if (origin == EventOrigin.DYNAMIC_KEY_BINDING && bindingDecisionId.isEmpty()) {
                throw new IllegalArgumentException("Dynamic-binding invocations require a binding decision");
            }
        }
    }

    public record SelectionExpression(
            String schema,
            String id,
            String documentId,
            String kind,
            String seedText,
            String evaluatorId,
            String evaluatorRevision,
            String orderingPolicy,
            String geometryRevision
    ) {
        public SelectionExpression {
            if (!SELECTION_EXPRESSION_SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("Unsupported selection expression schema: " + schema);
            }
            id = text(id, "selectionExpression.id");
            documentId = text(documentId, "selectionExpression.documentId");
            kind = text(kind, "selectionExpression.kind");
            seedText = text(seedText, "selectionExpression.seedText");
            evaluatorId = text(evaluatorId, "selectionExpression.evaluatorId");
            evaluatorRevision = text(evaluatorRevision, "selectionExpression.evaluatorRevision");
            orderingPolicy = text(orderingPolicy, "selectionExpression.orderingPolicy");
            geometryRevision = text(geometryRevision, "selectionExpression.geometryRevision");
        }
    }

    public record WitnessRegion(
            int ordinal,
            int startCodePointOffset,
            int endCodePointOffset,
            int lineOneBased,
            int columnCodePointOneBased,
            String expectedText
    ) {
        public WitnessRegion {
            if (ordinal < 0) throw new IllegalArgumentException("Witness ordinal must not be negative");
            if (startCodePointOffset < 0 || endCodePointOffset <= startCodePointOffset) {
                throw new IllegalArgumentException("Witness region must be a non-empty forward range");
            }
            if (lineOneBased < 1 || columnCodePointOneBased < 1) {
                throw new IllegalArgumentException("Witness source coordinates are one-based");
            }
            expectedText = text(expectedText, "witnessRegion.expectedText");
            if (expectedText.codePointCount(0, expectedText.length())
                    != endCodePointOffset - startCodePointOffset) {
                throw new IllegalArgumentException("Witness region length disagrees with expected text");
            }
        }
    }

    public record SelectionWitness(
            String id,
            String expressionId,
            String parentStateId,
            String sourceTextHash,
            String evaluatorId,
            String evaluatorRevision,
            String orderingPolicy,
            String geometryRevision,
            List<WitnessRegion> regions
    ) {
        public SelectionWitness {
            id = text(id, "selectionWitness.id");
            expressionId = text(expressionId, "selectionWitness.expressionId");
            parentStateId = text(parentStateId, "selectionWitness.parentStateId");
            sourceTextHash = text(sourceTextHash, "selectionWitness.sourceTextHash");
            evaluatorId = text(evaluatorId, "selectionWitness.evaluatorId");
            evaluatorRevision = text(evaluatorRevision, "selectionWitness.evaluatorRevision");
            orderingPolicy = text(orderingPolicy, "selectionWitness.orderingPolicy");
            geometryRevision = text(geometryRevision, "selectionWitness.geometryRevision");
            regions = immutable(regions, "selectionWitness.regions");
            if (regions.isEmpty()) throw new IllegalArgumentException("Selection witness must not be empty");
            int previousEnd = -1;
            for (int index = 0; index < regions.size(); index++) {
                WitnessRegion region = regions.get(index);
                if (region.ordinal() != index) {
                    throw new IllegalArgumentException("Witness region ordinals must be contiguous from zero");
                }
                if (region.startCodePointOffset() < previousEnd) {
                    throw new IllegalArgumentException("Witness regions must be non-overlapping in declared order");
                }
                previousEnd = region.endCodePointOffset();
            }
        }
    }

    public record Frame(
            String stateId,
            Optional<String> parentStateId,
            String stateHash,
            String textHash,
            String text,
            Optional<String> selectionWitnessId
    ) {
        public Frame {
            stateId = SFMTemporalReplayArchive.text(stateId, "frame.stateId");
            parentStateId = optionalText(parentStateId, "frame.parentStateId");
            stateHash = SFMTemporalReplayArchive.text(stateHash, "frame.stateHash");
            textHash = SFMTemporalReplayArchive.text(textHash, "frame.textHash");
            text = nonNullText(text, "frame.text");
            selectionWitnessId = optionalText(selectionWitnessId, "frame.selectionWitnessId");
            int bytes = text.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_FRAME_UTF8_BYTES) {
                throw new IllegalArgumentException("Frame text exceeds the bounded UTF-8 size");
            }
            if (!textHash.equals(sha256(text))) {
                throw new IllegalArgumentException("Frame text hash does not match its content");
            }
        }
    }

    public record SemanticTransition(
            String id,
            long sequence,
            String invocationId,
            String intentId,
            String evaluationId,
            String outcomeId,
            String parentStateId,
            String resultStateId,
            String actionId,
            List<TypedArgument> arguments,
            SFMHistoryGraphContract.EvaluationPolicy evaluationPolicy,
            SFMHistoryGraphContract.EffectClass effectClass,
            Optional<String> witnessId,
            TransitionStatus status,
            List<String> evidence
    ) {
        public SemanticTransition {
            id = text(id, "transition.id");
            positive(sequence, "transition.sequence");
            invocationId = text(invocationId, "transition.invocationId");
            intentId = text(intentId, "transition.intentId");
            evaluationId = text(evaluationId, "transition.evaluationId");
            outcomeId = text(outcomeId, "transition.outcomeId");
            parentStateId = text(parentStateId, "transition.parentStateId");
            resultStateId = text(resultStateId, "transition.resultStateId");
            actionId = text(actionId, "transition.actionId");
            arguments = canonical(arguments, TypedArgument::name, "transition.arguments");
            Objects.requireNonNull(evaluationPolicy, "transition.evaluationPolicy");
            Objects.requireNonNull(effectClass, "transition.effectClass");
            witnessId = optionalText(witnessId, "transition.witnessId");
            Objects.requireNonNull(status, "transition.status");
            evidence = immutableText(evidence, "transition.evidence", false, true);
        }
    }

    public record Observation(
            String id,
            long sequence,
            String kind,
            String relatedRecordId,
            String value
    ) {
        public Observation {
            id = text(id, "observation.id");
            positive(sequence, "observation.sequence");
            kind = text(kind, "observation.kind");
            relatedRecordId = text(relatedRecordId, "observation.relatedRecordId");
            value = nonNullText(value, "observation.value");
        }
    }

    public record HeadMovement(
            String id,
            long sequence,
            HeadMovementKind kind,
            String fromStateId,
            String toStateId,
            String actor,
            String requestId
    ) {
        public HeadMovement {
            id = text(id, "headMovement.id");
            positive(sequence, "headMovement.sequence");
            Objects.requireNonNull(kind, "headMovement.kind");
            fromStateId = text(fromStateId, "headMovement.fromStateId");
            toStateId = text(toStateId, "headMovement.toStateId");
            actor = text(actor, "headMovement.actor");
            requestId = text(requestId, "headMovement.requestId");
        }
    }

    public record ActionLineage(
            String sourceTransitionId,
            Optional<String> sourceWitnessId,
            Optional<String> resultingTransitionId,
            Optional<String> resultingWitnessId,
            ReplayStatus status,
            List<String> diagnostics
    ) {
        public ActionLineage {
            sourceTransitionId = text(sourceTransitionId, "lineage.sourceTransitionId");
            sourceWitnessId = optionalText(sourceWitnessId, "lineage.sourceWitnessId");
            resultingTransitionId = optionalText(resultingTransitionId, "lineage.resultingTransitionId");
            resultingWitnessId = optionalText(resultingWitnessId, "lineage.resultingWitnessId");
            Objects.requireNonNull(status, "lineage.status");
            diagnostics = immutableText(diagnostics, "lineage.diagnostics", false, true);
            if (status == ReplayStatus.SUCCEEDED && resultingTransitionId.isEmpty()) {
                throw new IllegalArgumentException("Successful action lineage requires a resulting transition");
            }
        }
    }

    public record ReplayReport(
            String id,
            long sequence,
            ReplayMode mode,
            ReplayStatus status,
            String sourceBoundaryStateId,
            String targetParentStateId,
            List<String> sourceTransitionIds,
            List<String> resultingTransitionIds,
            List<ActionLineage> actionLineage,
            Optional<String> resultingStateId,
            List<String> diagnostics
    ) {
        public ReplayReport {
            id = text(id, "replayReport.id");
            positive(sequence, "replayReport.sequence");
            Objects.requireNonNull(mode, "replayReport.mode");
            Objects.requireNonNull(status, "replayReport.status");
            sourceBoundaryStateId = text(sourceBoundaryStateId, "replayReport.sourceBoundaryStateId");
            targetParentStateId = text(targetParentStateId, "replayReport.targetParentStateId");
            sourceTransitionIds = orderedUniqueIds(sourceTransitionIds, "replayReport.sourceTransitionIds");
            resultingTransitionIds = orderedUniqueIds(
                    resultingTransitionIds,
                    "replayReport.resultingTransitionIds"
            );
            actionLineage = immutable(actionLineage, "replayReport.actionLineage");
            resultingStateId = optionalText(resultingStateId, "replayReport.resultingStateId");
            diagnostics = immutableText(diagnostics, "replayReport.diagnostics", false, true);
            if (sourceTransitionIds.size() != actionLineage.size()) {
                throw new IllegalArgumentException("Replay report needs one lineage record per source transition");
            }
            if (status == ReplayStatus.SUCCEEDED) {
                if (resultingStateId.isEmpty() || resultingTransitionIds.size() != sourceTransitionIds.size()) {
                    throw new IllegalArgumentException("Successful replay reports require a complete result chain");
                }
            }
        }
    }

    public record Archive(
            String schema,
            String episodeId,
            String documentId,
            long generation,
            List<BindingSnapshot> bindingSnapshots,
            List<SourceEvent> sourceEvents,
            List<BindingDecision> bindingDecisions,
            List<ActionInvocation> invocations,
            List<SelectionExpression> selectionExpressions,
            List<SelectionWitness> selectionWitnesses,
            List<Frame> frames,
            List<SemanticTransition> transitions,
            List<Observation> observations,
            List<HeadMovement> headMovements,
            List<ReplayReport> replayReports,
            String currentStateId
    ) {
        public Archive {
            if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported replay archive schema: " + schema);
            episodeId = text(episodeId, "archive.episodeId");
            documentId = text(documentId, "archive.documentId");
            if (generation < 0) throw new IllegalArgumentException("Archive generation must not be negative");
            bindingSnapshots = canonical(bindingSnapshots, BindingSnapshot::id, "archive.bindingSnapshots");
            sourceEvents = sequenced(sourceEvents, SourceEvent::id, SourceEvent::sequence, "archive.sourceEvents");
            bindingDecisions = sequenced(
                    bindingDecisions,
                    BindingDecision::id,
                    BindingDecision::sequence,
                    "archive.bindingDecisions"
            );
            invocations = sequenced(invocations, ActionInvocation::id, ActionInvocation::sequence, "archive.invocations");
            selectionExpressions = canonical(
                    selectionExpressions,
                    SelectionExpression::id,
                    "archive.selectionExpressions"
            );
            selectionWitnesses = canonical(
                    selectionWitnesses,
                    SelectionWitness::id,
                    "archive.selectionWitnesses"
            );
            frames = canonical(frames, Frame::stateId, "archive.frames");
            transitions = sequenced(
                    transitions,
                    SemanticTransition::id,
                    SemanticTransition::sequence,
                    "archive.transitions"
            );
            observations = sequenced(observations, Observation::id, Observation::sequence, "archive.observations");
            headMovements = sequenced(
                    headMovements,
                    HeadMovement::id,
                    HeadMovement::sequence,
                    "archive.headMovements"
            );
            replayReports = sequenced(
                    replayReports,
                    ReplayReport::id,
                    ReplayReport::sequence,
                    "archive.replayReports"
            );
            currentStateId = text(currentStateId, "archive.currentStateId");
            bound(bindingSnapshots, "binding snapshots");
            bound(sourceEvents, "source events");
            bound(bindingDecisions, "binding decisions");
            bound(invocations, "invocations");
            bound(selectionExpressions, "selection expressions");
            bound(selectionWitnesses, "selection witnesses");
            bound(frames, "frames");
            bound(transitions, "transitions");
            bound(observations, "observations");
            bound(headMovements, "head movements");
            bound(replayReports, "replay reports");
            validateReferences(
                    bindingSnapshots,
                    sourceEvents,
                    bindingDecisions,
                    invocations,
                    selectionExpressions,
                    selectionWitnesses,
                    frames,
                    transitions,
                    observations,
                    headMovements,
                    replayReports,
                    currentStateId
            );
        }

        public Frame requireFrame(String stateId) {
            return frames.stream().filter(frame -> frame.stateId().equals(stateId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown replay frame " + stateId));
        }

        public SemanticTransition requireTransition(String id) {
            return transitions.stream().filter(transition -> transition.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown replay transition " + id));
        }
    }

    private static void validateReferences(
            List<BindingSnapshot> bindingSnapshots,
            List<SourceEvent> sourceEvents,
            List<BindingDecision> decisions,
            List<ActionInvocation> invocations,
            List<SelectionExpression> expressions,
            List<SelectionWitness> witnesses,
            List<Frame> frames,
            List<SemanticTransition> transitions,
            List<Observation> observations,
            List<HeadMovement> movements,
            List<ReplayReport> reports,
            String currentStateId
    ) {
        Map<String, BindingSnapshot> snapshots = index(bindingSnapshots, BindingSnapshot::id);
        Map<String, SourceEvent> events = index(sourceEvents, SourceEvent::id);
        Map<String, BindingDecision> decisionIndex = index(decisions, BindingDecision::id);
        Map<String, ActionInvocation> invocationIndex = index(invocations, ActionInvocation::id);
        Map<String, SelectionExpression> expressionIndex = index(expressions, SelectionExpression::id);
        Map<String, SelectionWitness> witnessIndex = index(witnesses, SelectionWitness::id);
        Map<String, Frame> frameIndex = index(frames, Frame::stateId);
        Map<String, SemanticTransition> transitionIndex = index(transitions, SemanticTransition::id);

        requireReference(frameIndex, currentStateId, "current state");
        long totalBytes = 0;
        for (Frame frame : frames) {
            totalBytes = Math.addExact(totalBytes, frame.text().getBytes(StandardCharsets.UTF_8).length);
            frame.parentStateId().ifPresent(parent -> requireReference(frameIndex, parent, "frame parent"));
            frame.selectionWitnessId().ifPresent(witness -> requireReference(witnessIndex, witness, "frame witness"));
        }
        if (totalBytes > MAX_TOTAL_FRAME_UTF8_BYTES) {
            throw new IllegalArgumentException("Replay archive frame bytes exceed the bounded total");
        }
        for (SelectionWitness witness : witnesses) {
            SelectionExpression expression = requireReference(
                    expressionIndex,
                    witness.expressionId(),
                    "witness expression"
            );
            Frame parent = requireReference(frameIndex, witness.parentStateId(), "witness parent frame");
            if (!expression.evaluatorId().equals(witness.evaluatorId())
                    || !expression.evaluatorRevision().equals(witness.evaluatorRevision())
                    || !expression.orderingPolicy().equals(witness.orderingPolicy())
                    || !expression.geometryRevision().equals(witness.geometryRevision())) {
                throw new IllegalArgumentException("Selection expression and witness revisions disagree");
            }
            if (!parent.textHash().equals(witness.sourceTextHash())) {
                throw new IllegalArgumentException("Selection witness source hash disagrees with its parent frame");
            }
            validateWitnessText(witness, parent.text());
        }
        for (BindingDecision decision : decisions) {
            decision.sourceEventIds().forEach(id -> requireReference(events, id, "binding source event"));
            decision.bindingSnapshotId().ifPresent(id -> requireReference(snapshots, id, "binding snapshot"));
            decision.invocationIds().forEach(id -> requireReference(invocationIndex, id, "binding invocation"));
            decision.matchedBindingId().ifPresent(bindingId -> {
                BindingSnapshot snapshot = requireReference(
                        snapshots,
                        decision.bindingSnapshotId().orElseThrow(),
                        "matched binding snapshot"
                );
                if (snapshot.bindings().stream().noneMatch(binding -> binding.bindingId().equals(bindingId))) {
                    throw new IllegalArgumentException("Binding decision names a binding absent from its snapshot");
                }
            });
        }
        for (ActionInvocation invocation : invocations) {
            invocation.bindingDecisionId().ifPresent(id -> requireReference(decisionIndex, id, "invocation decision"));
            invocation.sourceEventIds().forEach(id -> requireReference(events, id, "invocation source event"));
        }
        for (SemanticTransition transition : transitions) {
            requireReference(invocationIndex, transition.invocationId(), "transition invocation");
            Frame parent = requireReference(frameIndex, transition.parentStateId(), "transition parent frame");
            Frame result = requireReference(frameIndex, transition.resultStateId(), "transition result frame");
            if (result.parentStateId().filter(parent.stateId()::equals).isEmpty()) {
                throw new IllegalArgumentException("Transition result does not descend from its declared parent");
            }
            transition.witnessId().ifPresent(id -> requireReference(witnessIndex, id, "transition witness"));
        }
        for (Observation observation : observations) {
            if (!events.containsKey(observation.relatedRecordId())
                    && !decisionIndex.containsKey(observation.relatedRecordId())
                    && !invocationIndex.containsKey(observation.relatedRecordId())
                    && !transitionIndex.containsKey(observation.relatedRecordId())
                    && reports.stream().noneMatch(report -> report.id().equals(observation.relatedRecordId()))) {
                throw new IllegalArgumentException("Observation relates to an unknown causal record");
            }
        }
        for (HeadMovement movement : movements) {
            requireReference(frameIndex, movement.fromStateId(), "head movement source");
            requireReference(frameIndex, movement.toStateId(), "head movement target");
        }
        for (ReplayReport report : reports) {
            requireReference(frameIndex, report.sourceBoundaryStateId(), "replay source boundary");
            requireReference(frameIndex, report.targetParentStateId(), "replay target parent");
            report.sourceTransitionIds().forEach(id -> requireReference(transitionIndex, id, "replay source transition"));
            report.resultingTransitionIds().forEach(id -> requireReference(
                    transitionIndex,
                    id,
                    "replay resulting transition"
            ));
            report.resultingStateId().ifPresent(id -> requireReference(frameIndex, id, "replay resulting state"));
            for (ActionLineage lineage : report.actionLineage()) {
                requireReference(transitionIndex, lineage.sourceTransitionId(), "lineage source transition");
                lineage.sourceWitnessId().ifPresent(id -> requireReference(witnessIndex, id, "lineage source witness"));
                lineage.resultingTransitionId().ifPresent(id -> requireReference(
                        transitionIndex,
                        id,
                        "lineage resulting transition"
                ));
                lineage.resultingWitnessId().ifPresent(id -> requireReference(
                        witnessIndex,
                        id,
                        "lineage resulting witness"
                ));
            }
        }
        HashSet<Long> logicalSequences = new HashSet<>();
        sourceEvents.forEach(event -> uniqueSequence(logicalSequences, event.sequence()));
        decisions.forEach(decision -> uniqueSequence(logicalSequences, decision.sequence()));
        invocations.forEach(invocation -> uniqueSequence(logicalSequences, invocation.sequence()));
        transitions.forEach(transition -> uniqueSequence(logicalSequences, transition.sequence()));
        observations.forEach(observation -> uniqueSequence(logicalSequences, observation.sequence()));
        movements.forEach(movement -> uniqueSequence(logicalSequences, movement.sequence()));
        reports.forEach(report -> uniqueSequence(logicalSequences, report.sequence()));
    }

    private static void validateWitnessText(SelectionWitness witness, String source) {
        int length = source.codePointCount(0, source.length());
        for (WitnessRegion region : witness.regions()) {
            if (region.endCodePointOffset() > length) {
                throw new IllegalArgumentException("Witness region extends beyond its source frame");
            }
            int start = source.offsetByCodePoints(0, region.startCodePointOffset());
            int end = source.offsetByCodePoints(0, region.endCodePointOffset());
            if (!source.substring(start, end).equals(region.expectedText())) {
                throw new IllegalArgumentException("Witness region text disagrees with its source frame");
            }
        }
    }

    private static void uniqueSequence(Set<Long> sequences, long sequence) {
        if (!sequences.add(sequence)) throw new IllegalArgumentException("Duplicate logical sequence " + sequence);
    }

    private static String bindingDigest(long revision, List<BindingDefinition> bindings) {
        ArrayList<String> parts = new ArrayList<>();
        parts.add(BINDING_SNAPSHOT_SCHEMA);
        parts.add(Long.toString(revision));
        bindings.forEach(binding -> parts.add(binding.canonicalForm()));
        return sha256(String.join("\n", parts));
    }

    public static String sha256(String value) {
        Objects.requireNonNull(value, "value");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder answer = new StringBuilder("sha256:");
            for (byte item : digest) answer.append(String.format("%02x", item & 0xff));
            return answer.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static <T> List<T> immutable(List<T> values, String label) {
        Objects.requireNonNull(values, label);
        ArrayList<T> answer = new ArrayList<>(values.size());
        for (T value : values) answer.add(Objects.requireNonNull(value, label + " item"));
        return List.copyOf(answer);
    }

    private static List<String> orderedUniqueIds(List<String> values, String label) {
        List<String> answer = immutableText(values, label, false, false);
        if (new HashSet<>(answer).size() != answer.size()) {
            throw new IllegalArgumentException(label + " must be unique");
        }
        return answer;
    }

    private static List<String> sortedUniqueText(List<String> values, String label) {
        List<String> answer = immutableText(values, label, true, false);
        if (new HashSet<>(answer).size() != answer.size()) {
            throw new IllegalArgumentException(label + " must be unique");
        }
        return answer;
    }

    private static List<String> immutableText(
            List<String> values,
            String label,
            boolean sort,
            boolean allowEmpty
    ) {
        Objects.requireNonNull(values, label);
        ArrayList<String> answer = new ArrayList<>(values.size());
        for (String value : values) answer.add(allowEmpty ? nonNullText(value, label) : text(value, label));
        if (sort) answer.sort(String::compareTo);
        return List.copyOf(answer);
    }

    private static <T> List<T> canonical(List<T> values, Function<T, String> id, String label) {
        List<T> answer = immutable(values, label);
        if (answer.size() > MAX_RECORDS_PER_KIND) {
            throw new IllegalArgumentException(label + " exceeds the bounded record count");
        }
        HashSet<String> identities = new HashSet<>();
        ArrayList<T> sorted = new ArrayList<>(answer);
        for (T value : sorted) {
            String identity = text(id.apply(value), label + ".id");
            if (!identities.add(identity)) throw new IllegalArgumentException("Duplicate " + label + " id " + identity);
        }
        sorted.sort(Comparator.comparing(id));
        return List.copyOf(sorted);
    }

    private static <T> List<T> sequenced(
            List<T> values,
            Function<T, String> id,
            java.util.function.ToLongFunction<T> sequence,
            String label
    ) {
        List<T> canonical = canonical(values, id, label);
        ArrayList<T> sorted = new ArrayList<>(canonical);
        sorted.sort(Comparator.comparingLong(sequence).thenComparing(id));
        long previous = 0;
        for (T value : sorted) {
            long current = sequence.applyAsLong(value);
            if (current <= previous) throw new IllegalArgumentException(label + " sequence must be strictly increasing");
            previous = current;
        }
        return List.copyOf(sorted);
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> id) {
        HashMap<String, T> answer = new HashMap<>();
        values.forEach(value -> answer.put(id.apply(value), value));
        return Map.copyOf(answer);
    }

    private static <T> T requireReference(Map<String, T> index, String id, String label) {
        T value = index.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown " + label + ": " + id);
        return value;
    }

    private static void bound(List<?> values, String label) {
        if (values.size() > MAX_RECORDS_PER_KIND) {
            throw new IllegalArgumentException("Too many " + label);
        }
    }

    private static long positive(long value, String label) {
        if (value <= 0) throw new IllegalArgumentException(label + " must be positive");
        return value;
    }

    private static String text(String value, String label) {
        value = nonNullText(value, label);
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return value;
    }

    private static String nonNullText(String value, String label) {
        return Objects.requireNonNull(value, label);
    }

    private static Optional<String> optionalText(Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(item -> text(item, label));
    }

    private static Optional<Integer> optionalInteger(Optional<Integer> value, String label) {
        Objects.requireNonNull(value, label);
        value.ifPresent(item -> Objects.requireNonNull(item, label));
        return value;
    }
}
