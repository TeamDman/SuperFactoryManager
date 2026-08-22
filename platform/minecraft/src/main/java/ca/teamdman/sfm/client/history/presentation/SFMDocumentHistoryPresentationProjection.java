package ca.teamdman.sfm.client.history.presentation;

import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.DocumentHeadMovement;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.DocumentRevision;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.Projection;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.SemanticTransaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Projects one immutable document history into the shared graph-presentation
 * contract used by both the accessible transcript and the 2D canvas.
 *
 * <p>The authoritative state DAG remains in {@link Projection}. This view is
 * deliberately chronological: semantic transactions and head movements are
 * ordered by their append-only sequence, while an undo/redo/checkout points
 * back to the referenced immutable state through a non-ranking relation.</p>
 */
public final class SFMDocumentHistoryPresentationProjection {
    private SFMDocumentHistoryPresentationProjection() {
    }

    public static SFMHistoryGraphPresentationModel.Presentation project(Projection projection) {
        Objects.requireNonNull(projection, "projection");
        Map<String, DocumentRevision> revisions = revisionsById(projection.revisions());
        DocumentRevision root = projection.revisions().stream()
                .filter(revision -> revision.parentRevisionId().isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Document projection has no root revision"));
        Set<String> currentLineage = currentLineage(projection.currentRevisionId(), revisions);

        LinkedHashMap<String, SFMHistoryGraphPresentationModel.Node> nodes = new LinkedHashMap<>();
        ArrayList<SFMHistoryGraphPresentationModel.Edge> edges = new ArrayList<>();
        addState(nodes, root, projection.currentRevisionId(), currentLineage);

        ArrayList<NarrativeEntry> narrative = new ArrayList<>();
        projection.semanticTransactions().forEach(transaction -> narrative.add(
                new NarrativeEntry(transaction.sequence(), transaction.id(), transaction, null)));
        projection.headMovements().forEach(movement -> narrative.add(
                new NarrativeEntry(movement.sequence(), movement.movement().id(), null, movement)));
        narrative.sort(Comparator.comparingLong(NarrativeEntry::sequence)
                .thenComparing(NarrativeEntry::stableId));

        String previousNarrativeNode = root.id();
        for (NarrativeEntry entry : narrative) {
            if (entry.transaction() != null) {
                SemanticTransaction transaction = entry.transaction();
                DocumentRevision resultingState = requireRevision(revisions, transaction.afterRevisionId());
                SFMHistoryGraphPresentationModel.Node action = transactionNode(
                        transaction,
                        currentLineage.contains(transaction.afterRevisionId())
                );
                nodes.put(action.id(), action);
                addState(nodes, resultingState, projection.currentRevisionId(), currentLineage);
                edges.add(edge(
                        projection,
                        "chronology-" + entry.sequence(),
                        previousNarrativeNode,
                        action.id(),
                        "Next experienced document action",
                        "Chronological action sequence " + entry.sequence()
                ));
                edges.add(edge(
                        projection,
                        "result-" + entry.sequence(),
                        action.id(),
                        resultingState.id(),
                        "Resulting document state",
                        "Semantic transaction produced revision " + resultingState.id()
                ));
                previousNarrativeNode = resultingState.id();
                continue;
            }

            DocumentHeadMovement movement = Objects.requireNonNull(entry.movement(), "movement");
            DocumentRevision target = requireRevision(revisions, movement.movement().toStateRevisionId());
            SFMHistoryGraphPresentationModel.Node action = movementNode(movement);
            nodes.put(action.id(), action);
            addState(nodes, target, projection.currentRevisionId(), currentLineage);
            edges.add(edge(
                    projection,
                    "chronology-" + entry.sequence(),
                    previousNarrativeNode,
                    action.id(),
                    "Next experienced head movement",
                    "Chronological head movement sequence " + entry.sequence()
            ));
            edges.add(edge(
                    projection,
                    "head-jump-" + entry.sequence(),
                    action.id(),
                    target.id(),
                    movement.movement().kind().name().toLowerCase(java.util.Locale.ROOT) + " jump",
                    "Head movement selected retained revision " + target.id()
            ));
            previousNarrativeNode = action.id();
        }

        // A checkout may address a revision that is not a semantic transaction
        // endpoint. Keep the authoritative head inspectable even in that case.
        addState(nodes, requireRevision(revisions, projection.currentRevisionId()),
                projection.currentRevisionId(), currentLineage);

        ArrayList<SFMHistoryGraphPresentationModel.Marker> markers = new ArrayList<>();
        markers.add(new SFMHistoryGraphPresentationModel.Marker(
                projection.identity().qualify("presentation-marker", "actual-head"),
                SFMHistoryGraphPresentationModel.MarkerKind.ACTUAL_HEAD,
                SFMHistoryGraphPresentationModel.MarkerSubjectKind.NODE,
                projection.currentRevisionId(),
                SFMHistoryGraphPresentationModel.LegendRole.ACTUAL_HEAD,
                "Current document head",
                "The document currently displays revision " + projection.currentRevisionId()
        ));

        List<SFMHistoryGraphPresentationModel.Node> nodeList = List.copyOf(nodes.values());
        return new SFMHistoryGraphPresentationModel.Presentation(
                SFMHistoryGraphPresentationModel.LegendRole.stableLegend(),
                nodeList,
                edges,
                markers,
                new SFMHistoryGraphPresentationModel.ProjectionSummary(
                        nodeList.size(), nodeList.size(), edges.size(), edges.size(),
                        markers.size(), markers.size()
                )
        );
    }

    private static void addState(
            Map<String, SFMHistoryGraphPresentationModel.Node> nodes,
            DocumentRevision revision,
            String currentRevisionId,
            Set<String> currentLineage
    ) {
        nodes.putIfAbsent(revision.id(), stateNode(revision, currentRevisionId, currentLineage));
    }

    private static SFMHistoryGraphPresentationModel.Node stateNode(
            DocumentRevision revision,
            String currentRevisionId,
            Set<String> currentLineage
    ) {
        ArrayList<SFMHistoryGraphPresentationModel.LegendRole> roles = new ArrayList<>();
        roles.add(SFMHistoryGraphPresentationModel.LegendRole.COMMITTED_EXECUTED);
        if (!currentLineage.contains(revision.id())) {
            roles.add(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE);
        }
        if (revision.id().equals(currentRevisionId)) {
            roles.add(SFMHistoryGraphPresentationModel.LegendRole.ACTUAL_HEAD);
        }
        String text = revision.state().text();
        return new SFMHistoryGraphPresentationModel.Node(
                revision.id(),
                List.of(SFMHistoryGraphPresentationModel.NodeOrigin.HISTORY),
                roles,
                stateLabel(text),
                "Immutable document revision " + revision.id() + " containing " + stateLabel(text),
                List.of(
                        detail("state.content", text.isEmpty() ? "<empty>" : text),
                        detail("state.hash", revision.stateHash()),
                        detail("state.parent-revision", revision.parentRevisionId().orElse("<root>")),
                        detail("state.revision-id", revision.id()),
                        detail("state.selection-count", Integer.toString(revision.state().selections().size())),
                        detail("state.sequence", Long.toString(revision.sequence()))
                )
        );
    }

    private static SFMHistoryGraphPresentationModel.Node transactionNode(
            SemanticTransaction transaction,
            boolean onCurrentLineage
    ) {
        ArrayList<SFMHistoryGraphPresentationModel.LegendRole> roles = new ArrayList<>();
        roles.add(SFMHistoryGraphPresentationModel.LegendRole.COMMITTED_EXECUTED);
        if (!onCurrentLineage) roles.add(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE);
        return new SFMHistoryGraphPresentationModel.Node(
                transaction.id(),
                List.of(SFMHistoryGraphPresentationModel.NodeOrigin.ACTION_INVOCATION),
                roles,
                transaction.label(),
                "Semantic document transaction " + transaction.label(),
                List.of(
                        detail("action.after-revision", transaction.afterRevisionId()),
                        detail("action.before-revision", transaction.beforeRevisionId()),
                        detail("action.kind", transaction.kind().name()),
                        detail("action.mutation-ids", joined(transaction.mutationIds())),
                        detail("action.raw-event-ids", joined(transaction.rawEventIds())),
                        detail("action.state-revisions", joined(transaction.stateRevisionIds())),
                        detail("action.transaction-id", transaction.id())
                )
        );
    }

    private static SFMHistoryGraphPresentationModel.Node movementNode(DocumentHeadMovement movement) {
        var value = movement.movement();
        return new SFMHistoryGraphPresentationModel.Node(
                value.id(),
                List.of(SFMHistoryGraphPresentationModel.NodeOrigin.HEAD_MOVEMENT),
                List.of(SFMHistoryGraphPresentationModel.LegendRole.COMMITTED_EXECUTED),
                capitalize(value.kind().name()) + " to " + shortIdentity(value.toStateRevisionId()),
                "Document head moved from " + value.fromStateRevisionId()
                        + " to " + value.toStateRevisionId(),
                List.of(
                        detail("head-movement.actor", value.actor()),
                        detail("head-movement.candidates", joined(value.candidateStateRevisionIds())),
                        detail("head-movement.from-revision", value.fromStateRevisionId()),
                        detail("head-movement.kind", value.kind().name()),
                        detail("head-movement.raw-event-ids", joined(movement.rawEventIds())),
                        detail("head-movement.request-id", value.requestId()),
                        detail("head-movement.to-revision", value.toStateRevisionId())
                )
        );
    }

    private static SFMHistoryGraphPresentationModel.Edge edge(
            Projection projection,
            String token,
            String from,
            String to,
            String label,
            String narration
    ) {
        String id = projection.identity().qualify("presentation-edge", token);
        return new SFMHistoryGraphPresentationModel.Edge(
                id,
                id,
                from,
                to,
                SFMHistoryGraphPresentationModel.EdgeOrigin.SEMANTIC,
                SFMHistoryGraphPresentationModel.EdgeCommitment.SEMANTIC_RELATION,
                List.of(),
                label,
                narration,
                List.of(
                        detail("semantic.from", from),
                        detail("semantic.to", to)
                )
        );
    }

    private static Map<String, DocumentRevision> revisionsById(List<DocumentRevision> revisions) {
        HashMap<String, DocumentRevision> result = new HashMap<>();
        for (DocumentRevision revision : revisions) {
            if (result.put(revision.id(), revision) != null) {
                throw new IllegalArgumentException("Duplicate document revision " + revision.id());
            }
        }
        return Map.copyOf(result);
    }

    private static Set<String> currentLineage(
            String currentRevisionId,
            Map<String, DocumentRevision> revisions
    ) {
        HashSet<String> result = new HashSet<>();
        String cursor = currentRevisionId;
        while (result.add(cursor)) {
            DocumentRevision revision = requireRevision(revisions, cursor);
            if (revision.parentRevisionId().isEmpty()) break;
            cursor = revision.parentRevisionId().orElseThrow();
        }
        return Set.copyOf(result);
    }

    private static DocumentRevision requireRevision(
            Map<String, DocumentRevision> revisions,
            String id
    ) {
        DocumentRevision revision = revisions.get(id);
        if (revision == null) throw new IllegalArgumentException("Unknown document revision " + id);
        return revision;
    }

    private static SFMHistoryGraphPresentationModel.Detail detail(String key, String value) {
        return new SFMHistoryGraphPresentationModel.Detail(key, value.isBlank() ? "<blank>" : value);
    }

    private static String joined(List<String> values) {
        return values.isEmpty() ? "<none>" : String.join(", ", values);
    }

    private static String stateLabel(String text) {
        if (text.isEmpty()) return "Empty document";
        String visible = text
                .replace("\r\n", "↵")
                .replace('\r', '↵')
                .replace('\n', '↵')
                .replace('\t', '⇥');
        return "“" + visible + "”";
    }

    private static String shortIdentity(String value) {
        int slash = value.lastIndexOf('/');
        String tail = slash < 0 ? value : value.substring(slash + 1);
        return tail.length() <= 12 ? tail : tail.substring(0, 12);
    }

    private static String capitalize(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private record NarrativeEntry(
            long sequence,
            String stableId,
            SemanticTransaction transaction,
            DocumentHeadMovement movement
    ) {
        private NarrativeEntry {
            if ((transaction == null) == (movement == null)) {
                throw new IllegalArgumentException("A narrative entry must contain exactly one event kind");
            }
        }
    }
}
