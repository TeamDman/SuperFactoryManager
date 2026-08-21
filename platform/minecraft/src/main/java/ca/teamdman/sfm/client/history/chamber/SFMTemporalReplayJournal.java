package ca.teamdman.sfm.client.history.chamber;

import ca.teamdman.sfm.client.action.SFMClientActionInvocationTrace;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive;
import ca.teamdman.sfm.client.keybinding.SFMKeyBinding;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingConflict;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingSnapshot;
import ca.teamdman.sfm.client.keybinding.SFMKeyInputEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Mutable recorder owned by one locked chamber controller. */
final class SFMTemporalReplayJournal {
    private static final String GEOMETRY_REVISION = "text-source-code-point-map/v1";
    private static final String EXPRESSION_KIND = "all-matching-hyphen-line-markers";

    private final SFMChamberDocumentState.IdentityScope scope;
    private final LinkedHashMap<String, SFMTemporalReplayArchive.BindingSnapshot> bindingSnapshots =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, SFMTemporalReplayArchive.SourceEvent> sourceEvents =
            new LinkedHashMap<>();
    private final ArrayList<SFMTemporalReplayArchive.BindingDecision> bindingDecisions = new ArrayList<>();
    private final ArrayList<SFMTemporalReplayArchive.ActionInvocation> invocations = new ArrayList<>();
    private final LinkedHashMap<String, SFMTemporalReplayArchive.SelectionExpression> expressions =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, SFMTemporalReplayArchive.SelectionWitness> witnesses =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, SFMTemporalReplayArchive.Frame> frames = new LinkedHashMap<>();
    private final ArrayList<SFMTemporalReplayArchive.SemanticTransition> transitions = new ArrayList<>();
    private final ArrayList<SFMTemporalReplayArchive.Observation> observations = new ArrayList<>();
    private final ArrayList<SFMTemporalReplayArchive.HeadMovement> headMovements = new ArrayList<>();
    private final ArrayList<SFMTemporalReplayArchive.ReplayReport> replayReports = new ArrayList<>();
    private final LinkedHashMap<Long, String> sourceEventIdsByRuntimeSequence = new LinkedHashMap<>();
    private long logicalSequence;
    private long generation;
    private String currentStateId;

    SFMTemporalReplayJournal(SFMChamberDocumentState initial) {
        Objects.requireNonNull(initial, "initial");
        scope = initial.scope();
        currentStateId = initial.revisionId();
        recordFrame(initial, Optional.empty());
    }

    /** Creates an isolated mutable staging journal from one already-validated immutable archive. */
    SFMTemporalReplayJournal(SFMTemporalReplayArchive.Archive archive) {
        Objects.requireNonNull(archive, "archive");
        scope = new SFMChamberDocumentState.IdentityScope(archive.episodeId(), archive.documentId());
        archive.bindingSnapshots().forEach(value -> bindingSnapshots.put(value.id(), value));
        archive.sourceEvents().forEach(value -> sourceEvents.put(value.id(), value));
        bindingDecisions.addAll(archive.bindingDecisions());
        invocations.addAll(archive.invocations());
        archive.selectionExpressions().forEach(value -> expressions.put(value.id(), value));
        archive.selectionWitnesses().forEach(value -> witnesses.put(value.id(), value));
        archive.frames().forEach(value -> frames.put(value.stateId(), value));
        transitions.addAll(archive.transitions());
        observations.addAll(archive.observations());
        headMovements.addAll(archive.headMovements());
        replayReports.addAll(archive.replayReports());
        logicalSequence = maximumSequence(archive);
        generation = archive.generation();
        currentStateId = archive.currentStateId();
    }

    String recordTransition(
            SFMDecimalNumberingChamber.Transition transition,
            String evaluationId,
            String edgeId,
            String evidence,
            String ambientCheckoutHash
    ) {
        return recordTransition(
                transition,
                evaluationId,
                edgeId,
                evidence,
                ambientCheckoutHash,
                Optional.empty()
        );
    }

    String recordReplayTransition(
            SFMDecimalNumberingChamber.Transition transition,
            String evaluationId,
            String edgeId,
            String evidence,
            String ambientCheckoutHash,
            SFMTemporalReplayArchive.ReplayMode mode
    ) {
        Objects.requireNonNull(mode, "mode");
        return recordTransition(
                transition,
                evaluationId,
                edgeId,
                evidence,
                ambientCheckoutHash,
                Optional.of(mode == SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY
                        ? SFMTemporalReplayArchive.EventOrigin.EXACT_REPLAY
                        : SFMTemporalReplayArchive.EventOrigin.SEMANTIC_REBASE)
        );
    }

    private String recordTransition(
            SFMDecimalNumberingChamber.Transition transition,
            String evaluationId,
            String edgeId,
            String evidence,
            String ambientCheckoutHash,
            Optional<SFMTemporalReplayArchive.EventOrigin> replayOrigin
    ) {
        Objects.requireNonNull(transition, "transition");
        Objects.requireNonNull(replayOrigin, "replayOrigin");
        if (!scope.equals(transition.parent().scope())) {
            throw new IllegalArgumentException("Transition belongs to another episode");
        }
        if (transitions.stream().anyMatch(value -> value.id().equals(edgeId))) {
            currentStateId = transition.result().revisionId();
            return edgeId;
        }
        InvocationCapture invocation = recordInvocation(transition.action(), replayOrigin);
        Optional<String> witnessId = witness(transition).map(value ->
                recordWitness(transition.parent(), value));
        recordFrame(transition.parent(), transition.parent().selection().flatMap(ignored -> witnessId));
        recordFrame(transition.result(), transition.result().selection().flatMap(ignored -> witnessId));
        SFMTemporalReplayArchive.SemanticTransition recorded = new SFMTemporalReplayArchive.SemanticTransition(
                edgeId,
                nextSequence(),
                invocation.invocationId(),
                transition.action().intent().id(),
                evaluationId,
                transition.outcomeId(),
                transition.parent().revisionId(),
                transition.result().revisionId(),
                transition.action().actionId(),
                actionArguments(transition.action(), witnessId),
                transition.action().evaluationPolicy(),
                transition.action().effectClass(),
                witnessId,
                SFMTemporalReplayArchive.TransitionStatus.SUCCEEDED,
                List.of(evidence, "result-state-hash=" + transition.result().stateHash())
        );
        transitions.add(recorded);
        currentStateId = transition.result().revisionId();
        observations.add(new SFMTemporalReplayArchive.Observation(
                scoped("observation", Long.toString(logicalSequence + 1)),
                nextSequence(),
                "ambient-checkout-baseline-hash",
                recorded.id(),
                requireText(ambientCheckoutHash, "ambientCheckoutHash")
        ));
        generation++;
        return recorded.id();
    }

    void recordHeadMovement(
            SFMTemporalReplayArchive.HeadMovementKind kind,
            String fromStateId,
            String toStateId,
            String actor,
            String requestId
    ) {
        headMovements.add(new SFMTemporalReplayArchive.HeadMovement(
                scoped("replay-head-movement", Long.toString(logicalSequence + 1)),
                nextSequence(),
                kind,
                fromStateId,
                toStateId,
                actor,
                requestId
        ));
        currentStateId = toStateId;
        generation++;
    }

    void recordReplay(
            SFMTemporalReplayArchive.ReplayMode mode,
            SFMTemporalReplayArchive.ReplayStatus status,
            String sourceBoundaryStateId,
            String targetParentStateId,
            List<String> sourceTransitionIds,
            List<String> resultingTransitionIds,
            List<SFMTemporalReplayArchive.ActionLineage> lineage,
            Optional<String> resultingStateId,
            List<String> diagnostics
    ) {
        replayReports.add(new SFMTemporalReplayArchive.ReplayReport(
                scoped("replay-report", Long.toString(logicalSequence + 1)),
                nextSequence(),
                mode,
                status,
                sourceBoundaryStateId,
                targetParentStateId,
                sourceTransitionIds,
                resultingTransitionIds,
                lineage,
                resultingStateId,
                diagnostics
        ));
        generation++;
    }

    void recordReplay(SFMTemporalReplayArchive.ReplayReport report) {
        Objects.requireNonNull(report, "report");
        recordReplay(
                report.mode(),
                report.status(),
                report.sourceBoundaryStateId(),
                report.targetParentStateId(),
                report.sourceTransitionIds(),
                report.resultingTransitionIds(),
                report.actionLineage(),
                report.resultingStateId(),
                report.diagnostics()
        );
    }

    void recordRestoredFrame(SFMChamberDocumentState state, Optional<String> witnessId) {
        recordFrame(state, witnessId);
    }

    SFMTemporalReplayArchive.Archive snapshot(String authoritativeCurrentStateId) {
        currentStateId = requireText(authoritativeCurrentStateId, "authoritativeCurrentStateId");
        return new SFMTemporalReplayArchive.Archive(
                SFMTemporalReplayArchive.SCHEMA,
                scope.episodeId(),
                scope.documentId(),
                generation,
                List.copyOf(bindingSnapshots.values()),
                List.copyOf(sourceEvents.values()),
                bindingDecisions,
                invocations,
                List.copyOf(expressions.values()),
                List.copyOf(witnesses.values()),
                List.copyOf(frames.values()),
                transitions,
                observations,
                headMovements,
                replayReports,
                currentStateId
        );
    }

    List<SFMTemporalReplayArchive.SemanticTransition> transitions() {
        return List.copyOf(transitions);
    }

    private InvocationCapture recordInvocation(
            SFMDecimalNumberingChamber.ChamberAction semanticAction,
            Optional<SFMTemporalReplayArchive.EventOrigin> replayOrigin
    ) {
        if (replayOrigin.isPresent()) {
            SFMTemporalReplayArchive.EventOrigin origin = replayOrigin.orElseThrow();
            String command = (origin == SFMTemporalReplayArchive.EventOrigin.EXACT_REPLAY
                    ? "replay exact "
                    : "replay semantic-rebase ") + semanticAction.actionId();
            return recordNonBindingInvocation(
                    semanticAction,
                    command,
                    SFMTemporalReplayArchive.EventKind.REPLAY_OPERATION,
                    origin
            );
        }
        Optional<SFMClientActionInvocationTrace.Provenance> current = SFMClientActionInvocationTrace.current();
        if (current.filter(SFMClientActionInvocationTrace.DynamicBindingProvenance.class::isInstance).isPresent()) {
            return recordDynamicInvocation(
                    semanticAction,
                    (SFMClientActionInvocationTrace.DynamicBindingProvenance) current.orElseThrow()
            );
        }
        String command = current.map(SFMClientActionInvocationTrace.Provenance::commandDraft)
                .orElse("controller " + semanticAction.actionId());
        SFMTemporalReplayArchive.EventOrigin origin = current.isPresent()
                ? SFMTemporalReplayArchive.EventOrigin.REGISTERED_ACTION_SURFACE
                : SFMTemporalReplayArchive.EventOrigin.CONTROLLER_API;
        SFMTemporalReplayArchive.EventKind kind = current.isPresent()
                ? SFMTemporalReplayArchive.EventKind.REGISTERED_ACTION_REQUEST
                : SFMTemporalReplayArchive.EventKind.CONTROLLER_OPERATION;
        return recordNonBindingInvocation(semanticAction, command, kind, origin);
    }

    private InvocationCapture recordNonBindingInvocation(
            SFMDecimalNumberingChamber.ChamberAction semanticAction,
            String command,
            SFMTemporalReplayArchive.EventKind kind,
            SFMTemporalReplayArchive.EventOrigin origin
    ) {
        String eventId = scoped("source-event", Long.toString(logicalSequence + 1));
        sourceEvents.put(eventId, new SFMTemporalReplayArchive.SourceEvent(
                eventId,
                nextSequence(),
                kind,
                origin,
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.of(command)
        ));
        String decisionId = scoped("binding-decision", Long.toString(logicalSequence + 1));
        String invocationId = scoped("invocation", Long.toString(logicalSequence + 2));
        bindingDecisions.add(new SFMTemporalReplayArchive.BindingDecision(
                decisionId,
                nextSequence(),
                List.of(eventId),
                SFMTemporalReplayArchive.BindingDecisionStatus.NOT_APPLICABLE,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(invocationId),
                true,
                List.of("binding-not-applicable: invocation used a non-binding action surface")
        ));
        invocations.add(new SFMTemporalReplayArchive.ActionInvocation(
                invocationId,
                nextSequence(),
                origin,
                actionIdFromCommand(command).orElse(semanticAction.actionId()),
                command,
                List.of(new SFMTemporalReplayArchive.TypedArgument(
                        "semantic-action-id",
                        "resource-location",
                        semanticAction.actionId()
                )),
                Optional.of(decisionId),
                List.of(eventId),
                SFMTemporalReplayArchive.AvailabilityStatus.AVAILABLE,
                SFMTemporalReplayArchive.AuthorizationStatus.AUTHORIZED,
                SFMTemporalReplayArchive.InvocationStatus.DISPATCHED,
                List.of()
        ));
        return new InvocationCapture(invocationId, List.of(eventId));
    }

    private static long maximumSequence(SFMTemporalReplayArchive.Archive archive) {
        long maximum = 0;
        for (SFMTemporalReplayArchive.SourceEvent value : archive.sourceEvents()) {
            maximum = Math.max(maximum, value.sequence());
        }
        for (SFMTemporalReplayArchive.BindingDecision value : archive.bindingDecisions()) {
            maximum = Math.max(maximum, value.sequence());
        }
        for (SFMTemporalReplayArchive.ActionInvocation value : archive.invocations()) {
            maximum = Math.max(maximum, value.sequence());
        }
        for (SFMTemporalReplayArchive.SemanticTransition value : archive.transitions()) {
            maximum = Math.max(maximum, value.sequence());
        }
        for (SFMTemporalReplayArchive.Observation value : archive.observations()) {
            maximum = Math.max(maximum, value.sequence());
        }
        for (SFMTemporalReplayArchive.HeadMovement value : archive.headMovements()) {
            maximum = Math.max(maximum, value.sequence());
        }
        for (SFMTemporalReplayArchive.ReplayReport value : archive.replayReports()) {
            maximum = Math.max(maximum, value.sequence());
        }
        return maximum;
    }

    private InvocationCapture recordDynamicInvocation(
            SFMDecimalNumberingChamber.ChamberAction semanticAction,
            SFMClientActionInvocationTrace.DynamicBindingProvenance provenance
    ) {
        SFMTemporalReplayArchive.BindingSnapshot snapshot = bindingSnapshot(provenance.effectiveSnapshot());
        bindingSnapshots.putIfAbsent(snapshot.id(), snapshot);
        ArrayList<String> eventIds = new ArrayList<>();
        for (SFMKeyInputEvent event : provenance.sourceEvents()) {
            String id = sourceEventIdsByRuntimeSequence.computeIfAbsent(event.sequenceNumber(), ignored -> {
                String eventId = scoped("source-event", "key-" + event.sequenceNumber());
                sourceEvents.put(eventId, new SFMTemporalReplayArchive.SourceEvent(
                        eventId,
                        nextSequence(),
                        SFMTemporalReplayArchive.EventKind.KEY,
                        SFMTemporalReplayArchive.EventOrigin.PHYSICAL_KEYBOARD,
                        event.tick(),
                        Optional.of(event.keyCode()),
                        Optional.of(event.type().name()),
                        Optional.empty(),
                        event.modifiers().stream().map(Enum::name).sorted().toList(),
                        Optional.empty()
                ));
                return eventId;
            });
            eventIds.add(id);
        }
        String decisionId = scoped("binding-decision", Long.toString(logicalSequence + 1));
        String invocationId = scoped("invocation", Long.toString(logicalSequence + 2));
        List<String> conflictDiagnostics = provenance.conflicts().stream()
                .map(SFMTemporalReplayJournal::conflictDiagnostic)
                .toList();
        SFMTemporalReplayArchive.BindingDecisionStatus status = provenance.conflicts().isEmpty()
                ? SFMTemporalReplayArchive.BindingDecisionStatus.MATCHED
                : SFMTemporalReplayArchive.BindingDecisionStatus.CONFLICT;
        bindingDecisions.add(new SFMTemporalReplayArchive.BindingDecision(
                decisionId,
                nextSequence(),
                eventIds,
                status,
                Optional.of(snapshot.id()),
                Optional.of(provenance.bindingId()),
                provenance.activeSituationIds(),
                List.of(invocationId),
                provenance.consumed(),
                conflictDiagnostics
        ));
        invocations.add(new SFMTemporalReplayArchive.ActionInvocation(
                invocationId,
                nextSequence(),
                SFMTemporalReplayArchive.EventOrigin.DYNAMIC_KEY_BINDING,
                provenance.actionId(),
                provenance.commandDraft(),
                List.of(new SFMTemporalReplayArchive.TypedArgument(
                        "semantic-action-id",
                        "resource-location",
                        semanticAction.actionId()
                )),
                Optional.of(decisionId),
                eventIds,
                SFMTemporalReplayArchive.AvailabilityStatus.AVAILABLE,
                SFMTemporalReplayArchive.AuthorizationStatus.AUTHORIZED,
                SFMTemporalReplayArchive.InvocationStatus.DISPATCHED,
                conflictDiagnostics
        ));
        return new InvocationCapture(invocationId, List.copyOf(eventIds));
    }

    private String recordWitness(
            SFMChamberDocumentState parent,
            SFMChamberDocumentState.SelectionWitness witness
    ) {
        String expressionId = scoped("selection-expression", token(
                scope.documentId(),
                witness.evaluatorId(),
                witness.evaluatorRevision(),
                witness.orderingPolicy().name(),
                "-"
        ));
        expressions.putIfAbsent(expressionId, new SFMTemporalReplayArchive.SelectionExpression(
                SFMTemporalReplayArchive.SELECTION_EXPRESSION_SCHEMA,
                expressionId,
                scope.documentId(),
                EXPRESSION_KIND,
                "-",
                witness.evaluatorId(),
                witness.evaluatorRevision(),
                witness.orderingPolicy().name().toLowerCase(java.util.Locale.ROOT),
                GEOMETRY_REVISION
        ));
        String witnessId = scoped("selection-witness", token(parent.revisionId(), witness.witnessHash()));
        ArrayList<SFMTemporalReplayArchive.WitnessRegion> regions = new ArrayList<>();
        for (int index = 0; index < witness.regions().size(); index++) {
            SFMChamberDocumentState.SourceRegion region = witness.regions().get(index);
            regions.add(new SFMTemporalReplayArchive.WitnessRegion(
                    index,
                    region.startCodePointOffset(),
                    region.endCodePointOffset(),
                    region.lineOneBased(),
                    region.columnCodePointOneBased(),
                    region.expectedText()
            ));
        }
        witnesses.putIfAbsent(witnessId, new SFMTemporalReplayArchive.SelectionWitness(
                witnessId,
                expressionId,
                parent.revisionId(),
                SFMTemporalReplayArchive.sha256(parent.text()),
                witness.evaluatorId(),
                witness.evaluatorRevision(),
                witness.orderingPolicy().name().toLowerCase(java.util.Locale.ROOT),
                GEOMETRY_REVISION,
                regions
        ));
        return witnessId;
    }

    private void recordFrame(SFMChamberDocumentState state, Optional<String> witnessId) {
        SFMTemporalReplayArchive.Frame frame = new SFMTemporalReplayArchive.Frame(
                state.revisionId(),
                state.parentRevisionId(),
                state.stateHash(),
                SFMTemporalReplayArchive.sha256(state.text()),
                state.text(),
                witnessId
        );
        SFMTemporalReplayArchive.Frame previous = frames.putIfAbsent(frame.stateId(), frame);
        if (previous != null) {
            boolean sameImmutableState = previous.stateId().equals(frame.stateId())
                    && previous.parentStateId().equals(frame.parentStateId())
                    && previous.stateHash().equals(frame.stateHash())
                    && previous.textHash().equals(frame.textHash())
                    && previous.text().equals(frame.text());
            if (!sameImmutableState) {
                throw new IllegalStateException("Replay frame identity collision");
            }
            if (previous.selectionWitnessId().isEmpty() && frame.selectionWitnessId().isPresent()) {
                frames.put(frame.stateId(), frame);
            }
        }
    }

    private static Optional<SFMChamberDocumentState.SelectionWitness> witness(
            SFMDecimalNumberingChamber.Transition transition
    ) {
        if (transition.action() instanceof SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction select) {
            return Optional.of(select.witness());
        }
        if (transition.action() instanceof SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction replace) {
            return Optional.of(replace.witness());
        }
        return Optional.empty();
    }

    private static List<SFMTemporalReplayArchive.TypedArgument> actionArguments(
            SFMDecimalNumberingChamber.ChamberAction action,
            Optional<String> witnessId
    ) {
        ArrayList<SFMTemporalReplayArchive.TypedArgument> answer = new ArrayList<>();
        answer.add(argument("expected-parent-revision-id", "state-id", action.expectedParentRevisionId()));
        answer.add(argument("expected-parent-state-hash", "sha256", action.expectedParentStateHash()));
        answer.add(argument("cost", "i64", Long.toString(action.cost())));
        witnessId.ifPresent(value -> answer.add(argument("selection-witness-id", "witness-id", value)));
        if (action instanceof SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction) {
            answer.add(argument("selection-expression", "expression-kind", EXPRESSION_KIND));
        } else if (action instanceof SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction replace) {
            answer.add(argument("start", "i32", Integer.toString(replace.start())));
            answer.add(argument("step", "i32", Integer.toString(replace.step())));
            answer.add(argument("suffix", "string", replace.suffix()));
        } else if (action instanceof SFMDecimalNumberingChamber.LiteralInsertAction insert) {
            answer.add(argument("code-point-offset", "i32", Integer.toString(insert.codePointOffset())));
            answer.add(argument("inserted-text", "string", insert.insertedText()));
        } else if (action instanceof SFMDecimalNumberingChamber.LiteralReplaceAction replace) {
            answer.add(argument("start-code-point-offset", "i32", Integer.toString(
                    replace.region().startCodePointOffset())));
            answer.add(argument("end-code-point-offset", "i32", Integer.toString(
                    replace.region().endCodePointOffset())));
            answer.add(argument("line", "i32", Integer.toString(replace.region().lineOneBased())));
            answer.add(argument("column", "i32", Integer.toString(replace.region().columnCodePointOneBased())));
            answer.add(argument("expected-text", "string", replace.region().expectedText()));
            answer.add(argument("replacement", "string", replace.replacement()));
            answer.add(argument("item-ordinal", "i32", Integer.toString(replace.itemOrdinalOneBased())));
        }
        return List.copyOf(answer);
    }

    private static SFMTemporalReplayArchive.TypedArgument argument(String name, String type, String value) {
        return new SFMTemporalReplayArchive.TypedArgument(name, type, value);
    }

    private SFMTemporalReplayArchive.BindingSnapshot bindingSnapshot(SFMKeyBindingSnapshot source) {
        List<SFMTemporalReplayArchive.BindingDefinition> definitions = source.bindings().stream()
                .map(SFMTemporalReplayJournal::bindingDefinition)
                .toList();
        String provisional = scoped("binding-snapshot", Long.toString(source.revision()));
        SFMTemporalReplayArchive.BindingSnapshot created =
                SFMTemporalReplayArchive.BindingSnapshot.create(provisional, source.revision(), definitions);
        String stableId = scoped("binding-snapshot", token(created.digest()));
        return SFMTemporalReplayArchive.BindingSnapshot.create(stableId, source.revision(), definitions);
    }

    private static SFMTemporalReplayArchive.BindingDefinition bindingDefinition(SFMKeyBinding source) {
        return new SFMTemporalReplayArchive.BindingDefinition(
                source.bindingId(),
                source.actionId(),
                source.commandDraft(),
                source.situationId().toString(),
                source.sequence().strokes().stream()
                        .map(stroke -> new SFMTemporalReplayArchive.KeyStroke(
                                stroke.keyCode(),
                                stroke.modifiers().stream().map(Enum::name).sorted().toList()
                        ))
                        .toList(),
                source.enabled()
        );
    }

    private static String conflictDiagnostic(SFMKeyBindingConflict conflict) {
        return "situation=" + conflict.situationId()
                + " bindings=" + String.join(",", conflict.bindingIds());
    }

    private static Optional<String> actionIdFromCommand(String command) {
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        String prefix = "sfm action invoke ";
        if (!normalized.startsWith(prefix)) return Optional.empty();
        String suffix = normalized.substring(prefix.length()).stripLeading();
        int separator = suffix.indexOf(' ');
        String value = separator < 0 ? suffix : suffix.substring(0, separator);
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private long nextSequence() {
        return ++logicalSequence;
    }

    private String scoped(String kind, String token) {
        return scope.qualify(kind, token);
    }

    private static String token(String... values) {
        return SFMChamberDocumentState.fingerprint(values).substring("sha256:".length());
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return value;
    }

    private record InvocationCapture(String invocationId, List<String> sourceEventIds) {
        private InvocationCapture {
            invocationId = requireText(invocationId, "invocationId");
            sourceEventIds = List.copyOf(sourceEventIds);
        }
    }
}
