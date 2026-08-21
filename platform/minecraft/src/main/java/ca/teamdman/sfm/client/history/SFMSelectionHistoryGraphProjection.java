package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelection;
import ca.teamdman.sfm.client.explorer.SFMSelectionHeadEvent;
import ca.teamdman.sfm.client.explorer.SFMSelectionId;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.SFMSelectionRevision;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Projects the selection repository's own topology into the shared history graph. */
public final class SFMSelectionHistoryGraphProjection {
    private SFMSelectionHistoryGraphProjection() {
    }

    public static SFMHistoryGraphContract.Graph project(
            SFMSelectionRepository.Archive archive,
            SFMSelectionId selectionId
    ) {
        SFMSelection selection = archive.selections().stream()
                .filter(value -> value.id().equals(selectionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown selection: " + selectionId.value()));
        List<SFMSelectionRevision> revisions = archive.revisions().stream()
                .filter(value -> value.selectionId().equals(selectionId))
                .sorted(Comparator.comparingLong(SFMSelectionRevision::id))
                .toList();
        Map<Long, SFMSelectionRevision> allRevisions = new HashMap<>();
        archive.revisions().forEach(value -> allRevisions.put(value.id(), value));

        ArrayList<SFMHistoryGraphContract.ActionIntent> intents = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.ActionEvaluation> evaluations = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.ActionOutcome> outcomes = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.StateRevision> states = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.BranchEdge> edges = new ArrayList<>();
        for (SFMSelectionRevision revision : revisions) {
            String intentId = intentId(revision.id());
            List<String> arguments = operationArguments(revision);
            intents.add(new SFMHistoryGraphContract.ActionIntent(
                    intentId,
                    actionId(revision.operation().kind()),
                    arguments,
                    hash(String.join("\u0000", arguments))
            ));
            List<Long> parents = historyParents(revision, allRevisions);
            states.add(new SFMHistoryGraphContract.StateRevision(
                    stateId(selectionId, revision.id()),
                    parents.stream().map(parent -> stateId(selectionId, parent)).toList(),
                    membersHash(revision.members()),
                    true,
                    SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
            ));
            for (long parent : parents) {
                String suffix = parent + "-" + revision.id();
                String evaluationId = "selection-evaluation-" + suffix;
                String outcomeId = "selection-outcome-" + suffix;
                String childState = stateId(selectionId, revision.id());
                evaluations.add(new SFMHistoryGraphContract.ActionEvaluation(
                        evaluationId,
                        intentId,
                        stateId(selectionId, parent),
                        "sfm.selection-history-projection/1",
                        SFMHistoryGraphContract.EvaluationPolicy.REUSE_RECORDED_TRANSITION,
                        List.of(new SFMHistoryGraphContract.DependencyWitness(
                                "selection-revision",
                                selectionId.value(),
                                Long.toString(parent)
                        )),
                        outcomeId,
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
                ));
                outcomes.add(new SFMHistoryGraphContract.ActionOutcome(
                        outcomeId,
                        evaluationId,
                        SFMHistoryGraphContract.OutcomeStatus.SUCCEEDED,
                        Optional.of(childState),
                        List.of("recorded selection revision " + revision.id())
                ));
                edges.add(new SFMHistoryGraphContract.BranchEdge(
                        "selection-edge-" + suffix,
                        stateId(selectionId, parent),
                        childState,
                        Optional.of(intentId),
                        Optional.of(evaluationId),
                        Optional.of(outcomeId),
                        SFMHistoryGraphContract.EffectClass.PURE,
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                        true
                ));
            }
        }

        String currentHeadId = headId(selectionId, "current");
        ArrayList<SFMHistoryGraphContract.HistoryHead> heads = new ArrayList<>();
        heads.add(new SFMHistoryGraphContract.HistoryHead(
                currentHeadId,
                domain(selectionId),
                stateId(selectionId, selection.headRevisionId()),
                Optional.of("current")
        ));
        selection.namedHeadRevisionIds().forEach((name, revisionId) -> heads.add(
                new SFMHistoryGraphContract.HistoryHead(
                        headId(selectionId, name),
                        domain(selectionId),
                        stateId(selectionId, revisionId),
                        Optional.of(name)
                )
        ));

        ArrayList<SFMHistoryGraphContract.HeadMovement> movements = new ArrayList<>();
        for (SFMSelectionHeadEvent event : archive.headEvents()) {
            if (!event.selectionId().equals(selectionId) || event.kind() == SFMSelectionHeadEvent.Kind.NAME_HEAD) {
                continue;
            }
            movements.add(new SFMHistoryGraphContract.HeadMovement(
                    "selection-head-movement-" + event.id(),
                    movementKind(event.kind()),
                    currentHeadId,
                    stateId(selectionId, event.fromRevisionId()),
                    stateId(selectionId, event.toRevisionId()),
                    movementCandidates(event, revisions, allRevisions).stream()
                            .map(revisionId -> stateId(selectionId, revisionId))
                            .toList(),
                    event.actor(),
                    event.requestId()
            ));
        }

        ArrayList<SFMHistoryGraphContract.RetentionPin> pins = new ArrayList<>();
        selection.namedHeadRevisionIds().forEach((name, revisionId) -> pins.add(
                new SFMHistoryGraphContract.RetentionPin(
                        "selection-head-pin-" + hash(name),
                        SFMHistoryGraphContract.RetentionKind.NAMED_BRANCH,
                        stateId(selectionId, revisionId),
                        headId(selectionId, name)
                )
        ));
        return new SFMHistoryGraphContract.Graph(
                SFMHistoryGraphContract.SCHEMA,
                intents,
                evaluations,
                outcomes,
                states,
                heads,
                movements,
                edges,
                pins
        );
    }

    private static List<Long> movementCandidates(
            SFMSelectionHeadEvent event,
            List<SFMSelectionRevision> revisions,
            Map<Long, SFMSelectionRevision> allRevisions
    ) {
        return switch (event.kind()) {
            case UNDO -> historyParents(allRevisions.get(event.fromRevisionId()), allRevisions);
            case REDO -> revisions.stream()
                    .filter(revision -> historyParents(revision, allRevisions).contains(event.fromRevisionId()))
                    .map(SFMSelectionRevision::id)
                    .sorted()
                    .toList();
            case CHECKOUT -> List.of(event.toRevisionId());
            case NAME_HEAD -> List.of();
        };
    }

    private static List<Long> historyParents(
            SFMSelectionRevision revision,
            Map<Long, SFMSelectionRevision> allRevisions
    ) {
        return revision.parentRevisionIds().stream()
                .filter(parent -> allRevisions.get(parent).selectionId().equals(revision.selectionId()))
                .sorted()
                .toList();
    }

    private static List<String> operationArguments(SFMSelectionRevision revision) {
        ArrayList<String> answer = new ArrayList<>();
        revision.operation().sourceSelections().stream()
                .map(SFMSelectionId::value)
                .sorted()
                .forEach(value -> answer.add("selection=" + value));
        revision.operation().operandPaths().stream()
                .map(SFMPath::canonical)
                .sorted()
                .forEach(value -> answer.add("path=" + value));
        return List.copyOf(answer);
    }

    private static String membersHash(Iterable<SFMPath> members) {
        ArrayList<String> canonical = new ArrayList<>();
        members.forEach(path -> canonical.add(path.canonical()));
        canonical.sort(String::compareTo);
        return hash(String.join("\n", canonical));
    }

    private static String actionId(SFMSelectionRevision.OperationKind kind) {
        return "sfm:selection/" + kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', '/');
    }

    private static SFMHistoryGraphContract.HeadMovementKind movementKind(SFMSelectionHeadEvent.Kind kind) {
        return switch (kind) {
            case UNDO -> SFMHistoryGraphContract.HeadMovementKind.UNDO;
            case REDO -> SFMHistoryGraphContract.HeadMovementKind.REDO;
            case CHECKOUT -> SFMHistoryGraphContract.HeadMovementKind.CHECKOUT;
            case NAME_HEAD -> SFMHistoryGraphContract.HeadMovementKind.SELECT_BRANCH;
        };
    }

    private static SFMHistoryGraphContract.UndoDomain domain(SFMSelectionId id) {
        return new SFMHistoryGraphContract.UndoDomain(
                SFMHistoryGraphContract.UndoDomainKind.SELECTION,
                "selection://" + id.value()
        );
    }

    private static String stateId(SFMSelectionId id, long revisionId) {
        return "selection:" + id.value() + ":revision:" + revisionId;
    }

    private static String intentId(long revisionId) {
        return "selection-intent-revision-" + revisionId;
    }

    private static String headId(SFMSelectionId id, String name) {
        return "selection-head:" + id.value() + ":" + name;
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
