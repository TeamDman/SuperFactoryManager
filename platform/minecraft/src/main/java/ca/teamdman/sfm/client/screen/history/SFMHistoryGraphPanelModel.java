package ca.teamdman.sfm.client.screen.history;

import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Pure, deterministic row/lane projection and keyboard selection for the live panel. */
public final class SFMHistoryGraphPanelModel {
    public enum RowKind {
        NODE,
        EDGE
    }

    public record Row(
            String key,
            RowKind kind,
            int depth,
            String label,
            String narration,
            List<SFMHistoryGraphPresentationModel.LegendRole> roles,
            List<SFMHistoryGraphPresentationModel.Detail> details,
            boolean actualHead,
            boolean instructionPointer
    ) {
        public Row {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(kind, "kind");
            if (depth < 0) throw new IllegalArgumentException("depth must not be negative");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(narration, "narration");
            roles = List.copyOf(roles);
            details = List.copyOf(details);
        }

        public String accessibleNarration() {
            StringBuilder answer = new StringBuilder(narration);
            for (SFMHistoryGraphPresentationModel.Detail detail : details) {
                answer.append(' ').append(detail.key()).append(' ').append(detail.value()).append('.');
            }
            return answer.toString();
        }
    }

    private List<Row> rows = List.of();
    private int selectedIndex = -1;

    public void update(SFMHistoryGraphPresentationModel.Presentation presentation) {
        Objects.requireNonNull(presentation, "presentation");
        String retainedKey = selected().map(Row::key).orElse(null);
        rows = projectRows(presentation);
        selectedIndex = retainedKey == null
                ? initialSelection(rows)
                : indexOf(retainedKey).orElseGet(() -> initialSelection(rows));
    }

    public void clear() {
        rows = List.of();
        selectedIndex = -1;
    }

    public List<Row> rows() {
        return rows;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public Optional<Row> selected() {
        return selectedIndex < 0 || selectedIndex >= rows.size()
                ? Optional.empty()
                : Optional.of(rows.get(selectedIndex));
    }

    public boolean selectNext() {
        return selectIndex(selectedIndex < 0 ? 0 : Math.min(rows.size() - 1, selectedIndex + 1));
    }

    public boolean selectPrevious() {
        return selectIndex(selectedIndex < 0 ? 0 : Math.max(0, selectedIndex - 1));
    }

    public boolean selectFirst() {
        return selectIndex(0);
    }

    public boolean selectLast() {
        return selectIndex(rows.size() - 1);
    }

    public boolean selectIndex(int requested) {
        if (rows.isEmpty()) {
            selectedIndex = -1;
            return false;
        }
        int next = Math.max(0, Math.min(rows.size() - 1, requested));
        boolean changed = next != selectedIndex;
        selectedIndex = next;
        return changed;
    }

    public int firstVisibleIndex(int capacity) {
        if (capacity <= 0 || rows.isEmpty()) return 0;
        if (selectedIndex < 0) return 0;
        int before = Math.max(1, capacity / 3);
        return Math.max(0, Math.min(selectedIndex - before, rows.size() - capacity));
    }

    public List<Row> visibleRows(int capacity) {
        if (capacity <= 0 || rows.isEmpty()) return List.of();
        int first = firstVisibleIndex(capacity);
        return rows.subList(first, Math.min(rows.size(), first + capacity));
    }

    public static List<Row> projectRows(SFMHistoryGraphPresentationModel.Presentation presentation) {
        Map<String, SFMHistoryGraphPresentationModel.Node> nodes = new HashMap<>();
        presentation.nodes().forEach(node -> nodes.put(node.id(), node));
        Map<String, List<SFMHistoryGraphPresentationModel.Edge>> outgoing = new HashMap<>();
        Set<String> hasIncoming = new HashSet<>();
        for (SFMHistoryGraphPresentationModel.Edge edge : presentation.edges()) {
            outgoing.computeIfAbsent(edge.fromNodeId(), ignored -> new ArrayList<>()).add(edge);
            hasIncoming.add(edge.toNodeId());
        }
        outgoing.values().forEach(edges -> edges.sort(
                Comparator.comparing(SFMHistoryGraphPresentationModel.Edge::id)));

        Map<String, Integer> depth = new HashMap<>();
        nodes.keySet().stream().filter(id -> !hasIncoming.contains(id)).sorted().forEach(id -> depth.put(id, 0));
        if (depth.isEmpty()) nodes.keySet().stream().sorted().findFirst().ifPresent(id -> depth.put(id, 0));
        // At most N rounds: deterministic and cycle-safe. Longest acyclic prefix
        // controls indentation; a cycle never drives depth without bound.
        for (int round = 0; round < nodes.size(); round++) {
            boolean changed = false;
            for (SFMHistoryGraphPresentationModel.Edge edge : presentation.edges()) {
                Integer parentDepth = depth.get(edge.fromNodeId());
                if (parentDepth == null) continue;
                int proposed = Math.min(nodes.size(), parentDepth + 1);
                Integer previous = depth.putIfAbsent(edge.toNodeId(), proposed);
                if (previous == null) changed = true;
            }
            if (!changed) break;
        }
        nodes.keySet().forEach(id -> depth.putIfAbsent(id, 0));

        Map<String, List<SFMHistoryGraphPresentationModel.MarkerKind>> nodeMarkers = new HashMap<>();
        Map<String, List<SFMHistoryGraphPresentationModel.MarkerKind>> edgeMarkers = new HashMap<>();
        for (SFMHistoryGraphPresentationModel.Marker marker : presentation.markers()) {
            (marker.subjectKind() == SFMHistoryGraphPresentationModel.MarkerSubjectKind.NODE
                    ? nodeMarkers : edgeMarkers)
                    .computeIfAbsent(marker.subjectId(), ignored -> new ArrayList<>())
                    .add(marker.kind());
        }

        ArrayList<Row> answer = new ArrayList<>();
        nodes.values().stream()
                .sorted(Comparator
                        .comparingInt((SFMHistoryGraphPresentationModel.Node node) -> depth.get(node.id()))
                        .thenComparing(SFMHistoryGraphPresentationModel.Node::id))
                .forEach(node -> {
                    List<SFMHistoryGraphPresentationModel.MarkerKind> markers =
                            nodeMarkers.getOrDefault(node.id(), List.of());
                    answer.add(new Row(
                            "node:" + node.id(),
                            RowKind.NODE,
                            depth.get(node.id()),
                            node.label(),
                            node.narration(),
                            node.roles(),
                            node.details(),
                            markers.contains(SFMHistoryGraphPresentationModel.MarkerKind.ACTUAL_HEAD),
                            markers.contains(SFMHistoryGraphPresentationModel.MarkerKind.INSTRUCTION_POINTER)
                    ));
                    for (SFMHistoryGraphPresentationModel.Edge edge : outgoing.getOrDefault(node.id(), List.of())) {
                        List<SFMHistoryGraphPresentationModel.MarkerKind> edgeMarkerKinds =
                                edgeMarkers.getOrDefault(edge.id(), List.of());
                        answer.add(new Row(
                                "edge:" + edge.id(),
                                RowKind.EDGE,
                                depth.get(node.id()) + 1,
                                edge.label() + " → " + edge.toNodeId(),
                                edge.narration(),
                                edge.roles(),
                                edge.details(),
                                false,
                                edgeMarkerKinds.contains(
                                        SFMHistoryGraphPresentationModel.MarkerKind.INSTRUCTION_POINTER)
                        ));
                    }
                });
        return List.copyOf(answer);
    }

    private Optional<Integer> indexOf(String key) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).key().equals(key)) return Optional.of(index);
        }
        return Optional.empty();
    }

    private static int initialSelection(List<Row> rows) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).instructionPointer()) return index;
        }
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).actualHead()) return index;
        }
        return rows.isEmpty() ? -1 : 0;
    }
}
