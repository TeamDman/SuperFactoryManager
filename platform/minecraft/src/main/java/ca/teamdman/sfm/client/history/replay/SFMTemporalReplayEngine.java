package ca.teamdman.sfm.client.history.replay;

import ca.teamdman.sfm.client.history.chamber.SFMChamberDocumentState;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingChamber;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure, bounded replay kernel for the temporal decimal-numbering chamber.
 *
 * <p>The engine never publishes history records or mutates an archive. A
 * successful operation returns a complete immutable staging bundle; every
 * expected failure returns an empty bundle and a typed replay report. This
 * gives a controller one atomic publication boundary.</p>
 */
public final class SFMTemporalReplayEngine {
    public static final int MAX_SOURCE_TRANSITIONS = 2;
    private static final String EXACT_PARENT_MISMATCH =
            "replay.exact.target-parent-precondition-mismatch";
    private static final String SOURCE_ACTION_INELIGIBLE =
            "replay.source-route.ineligible-action";
    private static final String SOURCE_ROUTE_CONFLICT =
            "replay.source-route.conflict";
    private static final String TARGET_SCOPE_MISMATCH =
            "replay.target-parent.scope-mismatch";
    private static final String REBASE_TARGET_CONFLICT =
            "replay.semantic-rebase.target-conflict";
    private static final String EXECUTION_CONFLICT =
            "replay.execution.conflict";

    private final SFMDecimalNumberingChamber chamber;

    public SFMTemporalReplayEngine(SFMDecimalNumberingChamber chamber) {
        this.chamber = Objects.requireNonNull(chamber, "chamber");
    }

    /**
     * Reexecute a frozen semantic route against the exact state identity it was
     * recorded for. The source actions themselves carry the frozen witness and
     * decimal parameters, so exact replay does not consult current semantics.
     */
    public ReplayResult exactReplay(
            List<SFMDecimalNumberingChamber.Transition> sourceTransitions,
            SFMChamberDocumentState targetParent,
            long reportSequence
    ) {
        List<SFMDecimalNumberingChamber.Transition> source = sourceTransitions(sourceTransitions);
        targetParent = Objects.requireNonNull(targetParent, "targetParent");
        requirePositive(reportSequence, "reportSequence");

        SourceRouteValidation validation = validateSourceRoute(source);
        if (validation.route().isEmpty()) {
            return failure(
                    SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY,
                    validation.status(),
                    source,
                    targetParent,
                    reportSequence,
                    validation.diagnostics()
            );
        }
        SemanticSourceRoute route = validation.route().orElseThrow();
        if (!targetParent.equals(route.sourceBoundary())) {
            return failure(
                    SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY,
                    SFMTemporalReplayArchive.ReplayStatus.PRECONDITION_MISMATCH,
                    source,
                    targetParent,
                    reportSequence,
                    List.of(EXACT_PARENT_MISMATCH
                            + ": expected revision " + route.sourceBoundary().revisionId()
                            + " with state " + route.sourceBoundary().stateHash()
                            + " but received revision " + targetParent.revisionId()
                            + " with state " + targetParent.stateHash())
            );
        }

        ArrayList<SFMDecimalNumberingChamber.Transition> replayed = new ArrayList<>(source.size());
        SFMChamberDocumentState current = targetParent;
        try {
            for (SFMDecimalNumberingChamber.Transition recorded : source) {
                if (!current.equals(recorded.parent())) {
                    return failure(
                            SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY,
                            SFMTemporalReplayArchive.ReplayStatus.CONFLICT,
                            source,
                            targetParent,
                            reportSequence,
                            List.of(EXECUTION_CONFLICT
                                    + ": the replayed prefix no longer equals transition parent "
                                    + recorded.parent().revisionId())
                    );
                }
                SFMChamberDocumentState result = recorded.action().apply(current);
                if (!result.equals(recorded.result())) {
                    return failure(
                            SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY,
                            SFMTemporalReplayArchive.ReplayStatus.CONFLICT,
                            source,
                            targetParent,
                            reportSequence,
                            List.of(EXECUTION_CONFLICT
                                    + ": frozen action did not reproduce recorded result "
                                    + recorded.result().revisionId())
                    );
                }
                replayed.add(new SFMDecimalNumberingChamber.Transition(
                        current,
                        recorded.action(),
                        result
                ));
                current = result;
            }
        } catch (RuntimeException e) {
            return failure(
                    SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY,
                    SFMTemporalReplayArchive.ReplayStatus.CONFLICT,
                    source,
                    targetParent,
                    reportSequence,
                    List.of(EXECUTION_CONFLICT + ": " + diagnosticMessage(e))
            );
        }

        return success(
                SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY,
                source,
                targetParent,
                replayed,
                reportSequence,
                List.of("replay.exact.frozen-witness-reproduced")
        );
    }

    /**
     * Reevaluate the source route's semantic selection against a changed
     * explicit parent, then reuse the recorded decimal replacement parameters
     * against the new ordered witness.
     */
    public ReplayResult semanticRebase(
            List<SFMDecimalNumberingChamber.Transition> sourceTransitions,
            SFMChamberDocumentState targetParent,
            long reportSequence
    ) {
        List<SFMDecimalNumberingChamber.Transition> source = sourceTransitions(sourceTransitions);
        targetParent = Objects.requireNonNull(targetParent, "targetParent");
        requirePositive(reportSequence, "reportSequence");

        SourceRouteValidation validation = validateSourceRoute(source);
        if (validation.route().isEmpty()) {
            return failure(
                    SFMTemporalReplayArchive.ReplayMode.SEMANTIC_REBASE,
                    validation.status(),
                    source,
                    targetParent,
                    reportSequence,
                    validation.diagnostics()
            );
        }
        SemanticSourceRoute route = validation.route().orElseThrow();
        if (!targetParent.scope().equals(chamber.scope())) {
            return failure(
                    SFMTemporalReplayArchive.ReplayMode.SEMANTIC_REBASE,
                    SFMTemporalReplayArchive.ReplayStatus.PRECONDITION_MISMATCH,
                    source,
                    targetParent,
                    reportSequence,
                    List.of(TARGET_SCOPE_MISMATCH
                            + ": expected " + chamber.scope()
                            + " but received " + targetParent.scope())
            );
        }
        if (targetParent.selection().isPresent()) {
            return failure(
                    SFMTemporalReplayArchive.ReplayMode.SEMANTIC_REBASE,
                    SFMTemporalReplayArchive.ReplayStatus.CONFLICT,
                    source,
                    targetParent,
                    reportSequence,
                    List.of(REBASE_TARGET_CONFLICT
                            + ": semantic rebase requires an unselected explicit parent")
            );
        }

        List<SFMDecimalNumberingChamber.Transition> replayed;
        try {
            SFMDecimalNumberingChamber.Target target = chamber.requireTarget(targetParent);
            SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction evaluatedSelection = chamber
                    .transitions(targetParent, target)
                    .stream()
                    .map(SFMDecimalNumberingChamber.Transition::action)
                    .filter(SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction.class::isInstance)
                    .map(SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction.class::cast)
                    .filter(candidate -> candidate.intent().equals(route.selectAction().intent()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "the recorded selection intent has no eligible evaluation on the target parent"
                    ));

            SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction rebasedSelection =
                    new SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction(
                            chamber.scope(),
                            targetParent.revisionId(),
                            targetParent.stateHash(),
                            evaluatedSelection.witness(),
                            route.selectAction().cost()
                    );
            SFMChamberDocumentState selected = rebasedSelection.apply(targetParent);
            SFMDecimalNumberingChamber.Transition selectTransition =
                    new SFMDecimalNumberingChamber.Transition(
                            targetParent,
                            rebasedSelection,
                            selected
                    );

            SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction sourceReplacement =
                    route.replaceAction();
            SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction rebasedReplacement =
                    new SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction(
                            chamber.scope(),
                            selected.revisionId(),
                            selected.stateHash(),
                            evaluatedSelection.witness(),
                            sourceReplacement.start(),
                            sourceReplacement.step(),
                            sourceReplacement.suffix(),
                            sourceReplacement.cost()
                    );
            SFMChamberDocumentState result = rebasedReplacement.apply(selected);
            SFMDecimalNumberingChamber.Transition replaceTransition =
                    new SFMDecimalNumberingChamber.Transition(
                            selected,
                            rebasedReplacement,
                            result
                    );
            if (!target.matches(result)) {
                return failure(
                        SFMTemporalReplayArchive.ReplayMode.SEMANTIC_REBASE,
                        SFMTemporalReplayArchive.ReplayStatus.CONFLICT,
                        source,
                        targetParent,
                        reportSequence,
                        List.of(REBASE_TARGET_CONFLICT
                                + ": rebased semantic actions did not reach the target document")
                );
            }
            replayed = List.of(selectTransition, replaceTransition);
        } catch (RuntimeException e) {
            return failure(
                    SFMTemporalReplayArchive.ReplayMode.SEMANTIC_REBASE,
                    SFMTemporalReplayArchive.ReplayStatus.CONFLICT,
                    source,
                    targetParent,
                    reportSequence,
                    List.of(REBASE_TARGET_CONFLICT + ": " + diagnosticMessage(e))
            );
        }
        return success(
                SFMTemporalReplayArchive.ReplayMode.SEMANTIC_REBASE,
                source,
                targetParent,
                replayed,
                reportSequence,
                List.of("replay.semantic-rebase.selection-reevaluated")
        );
    }

    /** Stable identity used by the archive/controller adapter for source transitions. */
    public static String chamberTransitionId(SFMDecimalNumberingChamber.Transition transition) {
        Objects.requireNonNull(transition, "transition");
        return transition.parent().scope().qualify("edge", digest(
                transition.parent().revisionId(),
                transition.result().revisionId(),
                transition.action().intent().id()
        ));
    }

    /** Stable identity used by the archive/controller adapter for chamber witnesses. */
    public static String chamberWitnessId(
            SFMChamberDocumentState parent,
            SFMChamberDocumentState.SelectionWitness witness
    ) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(witness, "witness");
        return parent.scope().qualify("selection-witness", digest(
                parent.revisionId(),
                witness.witnessHash()
        ));
    }

    private ReplayResult success(
            SFMTemporalReplayArchive.ReplayMode mode,
            List<SFMDecimalNumberingChamber.Transition> source,
            SFMChamberDocumentState targetParent,
            List<SFMDecimalNumberingChamber.Transition> replayed,
            long reportSequence,
            List<String> diagnostics
    ) {
        List<String> sourceIds = source.stream()
                .map(SFMTemporalReplayEngine::chamberTransitionId)
                .toList();
        String reportId = reportId(
                mode,
                SFMTemporalReplayArchive.ReplayStatus.SUCCEEDED,
                reportSequence,
                source.get(0).parent().revisionId(),
                targetParent.revisionId(),
                sourceIds,
                diagnostics
        );
        ArrayList<StagedTransition> staged = new ArrayList<>(replayed.size());
        ArrayList<SFMTemporalReplayArchive.ActionLineage> lineage = new ArrayList<>(replayed.size());
        for (int index = 0; index < replayed.size(); index++) {
            SFMDecimalNumberingChamber.Transition sourceTransition = source.get(index);
            SFMDecimalNumberingChamber.Transition resultingTransition = replayed.get(index);
            Optional<String> sourceWitness = witnessId(sourceTransition);
            Optional<String> resultingWitness = witnessId(resultingTransition);
            String stagedId = chamberTransitionId(resultingTransition);
            staged.add(new StagedTransition(
                    stagedId,
                    sourceIds.get(index),
                    resultingTransition,
                    sourceWitness,
                    resultingWitness
            ));
            lineage.add(new SFMTemporalReplayArchive.ActionLineage(
                    sourceIds.get(index),
                    sourceWitness,
                    Optional.of(stagedId),
                    resultingWitness,
                    SFMTemporalReplayArchive.ReplayStatus.SUCCEEDED,
                    List.of(mode == SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY
                            ? "replay.lineage.frozen-witness"
                            : "replay.lineage.semantic-witness-reevaluated")
            ));
        }
        List<String> resultingIds = staged.stream().map(StagedTransition::id).toList();
        SFMChamberDocumentState resultingState = replayed.get(replayed.size() - 1).result();
        SFMTemporalReplayArchive.ReplayReport report = new SFMTemporalReplayArchive.ReplayReport(
                reportId,
                reportSequence,
                mode,
                SFMTemporalReplayArchive.ReplayStatus.SUCCEEDED,
                source.get(0).parent().revisionId(),
                targetParent.revisionId(),
                sourceIds,
                resultingIds,
                lineage,
                Optional.of(resultingState.revisionId()),
                diagnostics
        );
        return new ReplayResult(report, targetParent, staged, Optional.of(resultingState));
    }

    private ReplayResult failure(
            SFMTemporalReplayArchive.ReplayMode mode,
            SFMTemporalReplayArchive.ReplayStatus status,
            List<SFMDecimalNumberingChamber.Transition> source,
            SFMChamberDocumentState targetParent,
            long reportSequence,
            List<String> diagnostics
    ) {
        List<String> sourceIds = source.stream()
                .map(SFMTemporalReplayEngine::chamberTransitionId)
                .toList();
        ArrayList<SFMTemporalReplayArchive.ActionLineage> lineage = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            lineage.add(new SFMTemporalReplayArchive.ActionLineage(
                    sourceIds.get(index),
                    witnessId(source.get(index)),
                    Optional.empty(),
                    Optional.empty(),
                    status,
                    diagnostics
            ));
        }
        String sourceBoundary = source.get(0).parent().revisionId();
        SFMTemporalReplayArchive.ReplayReport report = new SFMTemporalReplayArchive.ReplayReport(
                reportId(
                        mode,
                        status,
                        reportSequence,
                        sourceBoundary,
                        targetParent.revisionId(),
                        sourceIds,
                        diagnostics
                ),
                reportSequence,
                mode,
                status,
                sourceBoundary,
                targetParent.revisionId(),
                sourceIds,
                List.of(),
                lineage,
                Optional.empty(),
                diagnostics
        );
        return new ReplayResult(report, targetParent, List.of(), Optional.empty());
    }

    private SourceRouteValidation validateSourceRoute(
            List<SFMDecimalNumberingChamber.Transition> source
    ) {
        if (source.size() != 2
                || !(source.get(0).action()
                instanceof SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction select)
                || !(source.get(1).action()
                instanceof SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction replace)) {
            return SourceRouteValidation.failure(
                    SFMTemporalReplayArchive.ReplayStatus.INELIGIBLE_ACTION,
                    SOURCE_ACTION_INELIGIBLE
                            + ": expected select-all-hyphen-markers followed by replace-decimal-sequence"
            );
        }
        SFMDecimalNumberingChamber.Transition selectTransition = source.get(0);
        SFMDecimalNumberingChamber.Transition replaceTransition = source.get(1);
        if (!selectTransition.parent().scope().equals(chamber.scope())
                || !replaceTransition.parent().scope().equals(chamber.scope())) {
            return SourceRouteValidation.failure(
                    SFMTemporalReplayArchive.ReplayStatus.INELIGIBLE_ACTION,
                    SOURCE_ACTION_INELIGIBLE + ": source route belongs to another chamber scope"
            );
        }
        if (!selectTransition.result().equals(replaceTransition.parent())
                || !select.witness().equals(replace.witness())) {
            return SourceRouteValidation.failure(
                    SFMTemporalReplayArchive.ReplayStatus.CONFLICT,
                    SOURCE_ROUTE_CONFLICT + ": source transitions are not one exact witness-linked chain"
            );
        }
        try {
            SFMChamberDocumentState selected = select.apply(selectTransition.parent());
            SFMChamberDocumentState numbered = replace.apply(replaceTransition.parent());
            SFMDecimalNumberingChamber.Target target = chamber.requireTarget(selectTransition.parent());
            if (!selected.equals(selectTransition.result())
                    || !numbered.equals(replaceTransition.result())
                    || !target.matches(numbered)) {
                return SourceRouteValidation.failure(
                        SFMTemporalReplayArchive.ReplayStatus.CONFLICT,
                        SOURCE_ROUTE_CONFLICT + ": source route does not reproduce its recorded two-item result"
                );
            }
        } catch (RuntimeException e) {
            return SourceRouteValidation.failure(
                    SFMTemporalReplayArchive.ReplayStatus.CONFLICT,
                    SOURCE_ROUTE_CONFLICT + ": " + diagnosticMessage(e)
            );
        }
        return SourceRouteValidation.success(new SemanticSourceRoute(
                selectTransition.parent(),
                selectTransition,
                select,
                replaceTransition,
                replace
        ));
    }

    private static List<SFMDecimalNumberingChamber.Transition> sourceTransitions(
            List<SFMDecimalNumberingChamber.Transition> sourceTransitions
    ) {
        Objects.requireNonNull(sourceTransitions, "sourceTransitions");
        if (sourceTransitions.size() > MAX_SOURCE_TRANSITIONS) {
            throw new IllegalArgumentException(
                    "sourceTransitions exceeds the bounded maximum of " + MAX_SOURCE_TRANSITIONS
            );
        }
        List<SFMDecimalNumberingChamber.Transition> source = List.copyOf(sourceTransitions);
        if (source.isEmpty()) {
            throw new IllegalArgumentException("sourceTransitions must not be empty");
        }
        HashSet<String> ids = new HashSet<>();
        for (SFMDecimalNumberingChamber.Transition transition : source) {
            Objects.requireNonNull(transition, "source transition");
            if (!ids.add(chamberTransitionId(transition))) {
                throw new IllegalArgumentException("sourceTransitions must have unique transition identities");
            }
        }
        return source;
    }

    private static Optional<String> witnessId(SFMDecimalNumberingChamber.Transition transition) {
        SFMDecimalNumberingChamber.ChamberAction action = transition.action();
        if (action instanceof SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction select) {
            return Optional.of(chamberWitnessId(transition.parent(), select.witness()));
        }
        if (action instanceof SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction replace) {
            return Optional.of(chamberWitnessId(transition.parent(), replace.witness()));
        }
        return Optional.empty();
    }

    private static String reportId(
            SFMTemporalReplayArchive.ReplayMode mode,
            SFMTemporalReplayArchive.ReplayStatus status,
            long sequence,
            String sourceBoundary,
            String targetParent,
            List<String> sourceIds,
            List<String> diagnostics
    ) {
        ArrayList<String> parts = new ArrayList<>();
        parts.add("sfm.temporal-replay-report/1");
        parts.add(mode.name());
        parts.add(status.name());
        parts.add(Long.toString(sequence));
        parts.add(sourceBoundary);
        parts.add(targetParent);
        parts.addAll(sourceIds);
        parts.addAll(diagnostics);
        return "sfm:temporal-replay/report/" + digest(parts.toArray(String[]::new));
    }

    private static String digest(String... parts) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
        for (String part : parts) {
            byte[] bytes = Objects.requireNonNull(part, "digest part").getBytes(StandardCharsets.UTF_8);
            digest.update((byte) (bytes.length >>> 24));
            digest.update((byte) (bytes.length >>> 16));
            digest.update((byte) (bytes.length >>> 8));
            digest.update((byte) bytes.length);
            digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String diagnosticMessage(RuntimeException error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static Optional<String> requireOptionalText(Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(item -> requireText(item, label));
    }

    private static void requirePositive(long value, String label) {
        if (value <= 0) throw new IllegalArgumentException(label + " must be positive");
    }

    /** A replay transition that a controller may atomically publish later. */
    public record StagedTransition(
            String id,
            String sourceTransitionId,
            SFMDecimalNumberingChamber.Transition transition,
            Optional<String> sourceWitnessId,
            Optional<String> resultingWitnessId
    ) {
        public StagedTransition {
            id = requireText(id, "stagedTransition.id");
            sourceTransitionId = requireText(sourceTransitionId, "stagedTransition.sourceTransitionId");
            Objects.requireNonNull(transition, "stagedTransition.transition");
            sourceWitnessId = requireOptionalText(sourceWitnessId, "stagedTransition.sourceWitnessId");
            resultingWitnessId = requireOptionalText(resultingWitnessId, "stagedTransition.resultingWitnessId");
        }
    }

    /** Complete atomic staging result. Failed results can never publish a prefix. */
    public record ReplayResult(
            SFMTemporalReplayArchive.ReplayReport report,
            SFMChamberDocumentState targetParent,
            List<StagedTransition> stagedTransitions,
            Optional<SFMChamberDocumentState> resultingState
    ) {
        public ReplayResult {
            Objects.requireNonNull(report, "report");
            Objects.requireNonNull(targetParent, "targetParent");
            stagedTransitions = List.copyOf(Objects.requireNonNull(stagedTransitions, "stagedTransitions"));
            resultingState = Objects.requireNonNull(resultingState, "resultingState");
            List<String> stagedIds = stagedTransitions.stream().map(StagedTransition::id).toList();
            if (!stagedIds.equals(report.resultingTransitionIds())) {
                throw new IllegalArgumentException("Staged transition ids must equal report result ids");
            }
            if (report.status() == SFMTemporalReplayArchive.ReplayStatus.SUCCEEDED) {
                if (stagedTransitions.isEmpty() || resultingState.isEmpty()) {
                    throw new IllegalArgumentException("Successful replay requires a complete staged chain");
                }
                SFMChamberDocumentState current = targetParent;
                for (StagedTransition staged : stagedTransitions) {
                    if (!staged.transition().parent().equals(current)) {
                        throw new IllegalArgumentException("Staged replay chain is discontinuous");
                    }
                    current = staged.transition().result();
                }
                if (!resultingState.orElseThrow().equals(current)
                        || !report.resultingStateId().orElseThrow().equals(current.revisionId())) {
                    throw new IllegalArgumentException("Staged replay result disagrees with its report");
                }
            } else if (!stagedTransitions.isEmpty() || resultingState.isPresent()) {
                throw new IllegalArgumentException("Failed replay must not expose partial staging");
            }
        }

        public boolean succeeded() {
            return report.status() == SFMTemporalReplayArchive.ReplayStatus.SUCCEEDED;
        }
    }

    private record SemanticSourceRoute(
            SFMChamberDocumentState sourceBoundary,
            SFMDecimalNumberingChamber.Transition selectTransition,
            SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction selectAction,
            SFMDecimalNumberingChamber.Transition replaceTransition,
            SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction replaceAction
    ) {
    }

    private record SourceRouteValidation(
            Optional<SemanticSourceRoute> route,
            SFMTemporalReplayArchive.ReplayStatus status,
            List<String> diagnostics
    ) {
        private SourceRouteValidation {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(status, "status");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }

        static SourceRouteValidation success(SemanticSourceRoute route) {
            return new SourceRouteValidation(
                    Optional.of(route),
                    SFMTemporalReplayArchive.ReplayStatus.SUCCEEDED,
                    List.of()
            );
        }

        static SourceRouteValidation failure(
                SFMTemporalReplayArchive.ReplayStatus status,
                String diagnostic
        ) {
            return new SourceRouteValidation(Optional.empty(), status, List.of(diagnostic));
        }
    }
}
