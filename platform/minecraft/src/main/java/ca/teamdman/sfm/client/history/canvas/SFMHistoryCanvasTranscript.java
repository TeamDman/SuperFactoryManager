package ca.teamdman.sfm.client.history.canvas;

import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Accessible chronological projection of one immutable canvas layout. */
public final class SFMHistoryCanvasTranscript {
    public static final String SCHEMA = "sfm.history-canvas-transcript/1";

    private SFMHistoryCanvasTranscript() {
    }

    public record Entry(
            int ordinal,
            String key,
            SFMHistoryCanvasLayout.SubjectKind subjectKind,
            int rank,
            String laneStyleId,
            String label,
            String narration,
            List<SFMHistoryGraphPresentationModel.Detail> details,
            List<SFMHistoryGraphPresentationModel.NodeOrigin> nodeOrigins,
            List<SFMHistoryGraphPresentationModel.LegendRole> roles,
            boolean ranking,
            boolean curved
    ) {
        public Entry {
            if (ordinal < 0) throw new IllegalArgumentException("ordinal must not be negative");
            key = nonBlank(key, "entry.key");
            Objects.requireNonNull(subjectKind, "entry.subjectKind");
            if (rank < 0) throw new IllegalArgumentException("rank must not be negative");
            laneStyleId = nonBlank(laneStyleId, "entry.laneStyleId");
            label = nonBlank(label, "entry.label");
            narration = nonBlank(narration, "entry.narration");
            details = List.copyOf(details);
            nodeOrigins = List.copyOf(nodeOrigins);
            roles = List.copyOf(roles);
        }

        public String accessibleText() {
            StringBuilder answer = new StringBuilder();
            answer.append("Rank ").append(rank).append(". ")
                    .append(subjectKind == SFMHistoryCanvasLayout.SubjectKind.NODE ? "Node " : "Edge ")
                    .append(label).append(". ").append(narration);
            for (SFMHistoryGraphPresentationModel.Detail detail : details) {
                answer.append(' ').append(detail.key()).append(' ').append(detail.value()).append('.');
            }
            if (curved) answer.append(" Curved non-ranking relation.");
            return answer.toString();
        }
    }

    public record Transcript(String schema, List<Entry> entries) {
        public Transcript {
            if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported transcript schema: " + schema);
            entries = List.copyOf(entries);
            for (int index = 0; index < entries.size(); index++) {
                if (entries.get(index).ordinal() != index) {
                    throw new IllegalArgumentException("Transcript ordinals must be contiguous");
                }
            }
        }

        public List<String> accessibleLines() {
            return entries.stream().map(Entry::accessibleText).toList();
        }
    }

    public static Transcript project(SFMHistoryCanvasLayout.Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<String, List<SFMHistoryCanvasLayout.Edge>> outgoing = new HashMap<>();
        for (SFMHistoryCanvasLayout.Edge edge : snapshot.edges()) {
            outgoing.computeIfAbsent(edge.fromNodeId(), ignored -> new ArrayList<>()).add(edge);
        }
        outgoing.values().forEach(edges -> edges.sort(Comparator
                .comparing((SFMHistoryCanvasLayout.Edge edge) -> edge.pathKind().ordinal())
                .thenComparing(SFMHistoryCanvasLayout.Edge::stableId)));

        ArrayList<SFMHistoryCanvasLayout.Node> orderedNodes = new ArrayList<>(snapshot.nodes());
        orderedNodes.sort(Comparator
                .comparingInt(SFMHistoryCanvasLayout.Node::rank)
                .thenComparing(node -> node.lane().ordinal())
                .thenComparingInt(SFMHistoryCanvasLayout.Node::track)
                .thenComparing(SFMHistoryCanvasLayout.Node::stableId));

        ArrayList<Entry> entries = new ArrayList<>();
        for (SFMHistoryCanvasLayout.Node node : orderedNodes) {
            SFMHistoryGraphPresentationModel.Node source = node.source();
            entries.add(new Entry(
                    entries.size(),
                    "node:" + node.stableId(),
                    SFMHistoryCanvasLayout.SubjectKind.NODE,
                    node.rank(),
                    node.styleId(),
                    source.label(),
                    source.narration(),
                    source.details(),
                    source.origins(),
                    source.roles(),
                    false,
                    false
            ));
            for (SFMHistoryCanvasLayout.Edge edge : outgoing.getOrDefault(node.stableId(), List.of())) {
                SFMHistoryGraphPresentationModel.Edge sourceEdge = edge.source();
                entries.add(new Entry(
                        entries.size(),
                        "edge:" + edge.stableId(),
                        SFMHistoryCanvasLayout.SubjectKind.EDGE,
                        node.rank(),
                        node.styleId(),
                        sourceEdge.label() + " → " + edge.toNodeId(),
                        sourceEdge.narration(),
                        sourceEdge.details(),
                        List.of(),
                        sourceEdge.roles(),
                        edge.ranking(),
                        edge.curved()
                ));
            }
        }
        return new Transcript(SCHEMA, entries);
    }

    private static String nonBlank(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
