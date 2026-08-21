package ca.teamdman.sfm.client.history.presentation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure presentation data for a future History Graph panel.
 *
 * <p>The model intentionally contains no Minecraft rendering types. Stable role
 * identifiers are the seam between the history/trajectory contracts and later
 * visual, narrated, or alternate presentations.</p>
 */
public final class SFMHistoryGraphPresentationModel {
    private SFMHistoryGraphPresentationModel() {
    }

    public enum LegendRole {
        COMMITTED_EXECUTED(
                "committed-executed",
                "Committed / executed",
                "Materialized history that has been committed or executed."
        ),
        SELECTED_EXECUTED_PREFIX(
                "selected-executed-prefix",
                "Selected executed prefix",
                "A selected trajectory step before the machine instruction pointer."
        ),
        SELECTED_PROJECTED_SUFFIX(
                "selected-projected-suffix",
                "Selected projected suffix",
                "A future step on the selected trajectory, at or after its instruction pointer."
        ),
        RETAINED_ALTERNATIVE(
                "retained-alternative",
                "Retained alternative",
                "A preserved history branch or trajectory alternative that remains available."
        ),
        OPEN_FRONTIER(
                "open-frontier",
                "Open / frontier",
                "A generated search state that is waiting to be expanded or is on the published frontier."
        ),
        CLOSED(
                "closed",
                "Closed",
                "A search state that has already been expanded."
        ),
        BARRIER(
                "barrier",
                "Barrier",
                "A state or transition that projection cannot pass under the declared effect policy."
        ),
        TARGET(
                "target",
                "Target",
                "The destination state of the selected presentation trajectory."
        ),
        ACTUAL_HEAD(
                "actual-head",
                "Actual head",
                "The state selected by the authoritative history head."
        ),
        INSTRUCTION_POINTER(
                "instruction-pointer",
                "Instruction pointer",
                "The next trajectory step that execution would consider."
        );

        private final String styleId;
        private final String label;
        private final String narration;

        LegendRole(String styleId, String label, String narration) {
            this.styleId = styleId;
            this.label = label;
            this.narration = narration;
        }

        public String styleId() {
            return styleId;
        }

        public String label() {
            return label;
        }

        public String narration() {
            return narration;
        }

        public LegendEntry entry() {
            return new LegendEntry(styleId, label, narration);
        }

        public static List<LegendEntry> stableLegend() {
            return java.util.Arrays.stream(values()).map(LegendRole::entry).toList();
        }
    }

    public enum NodeOrigin {
        HISTORY,
        TRAJECTORY,
        SEARCH,
        ACTION_INTENT,
        ACTION_EVALUATION,
        ACTION_OUTCOME,
        RAW_EVENT,
        BINDING_DECISION,
        ACTION_INVOCATION,
        SELECTION_EXPRESSION,
        SELECTION_WITNESS,
        SNAPSHOT_FRAME,
        OBSERVATION,
        HEAD_MOVEMENT,
        REPLAY_REPORT
    }

    public enum EdgeOrigin {
        HISTORY,
        TRAJECTORY,
        SEARCH,
        SEMANTIC
    }

    public enum EdgeCommitment {
        COMMITTED_HISTORY,
        PROJECTED_CANDIDATE,
        SEMANTIC_RELATION
    }

    public enum MarkerKind {
        ACTUAL_HEAD,
        INSTRUCTION_POINTER
    }

    public enum MarkerSubjectKind {
        NODE,
        EDGE
    }

    public record Bounds(int maxNodes, int maxEdges) {
        public Bounds {
            if (maxNodes <= 0) throw new IllegalArgumentException("maxNodes must be positive");
            if (maxEdges < 0) throw new IllegalArgumentException("maxEdges must not be negative");
        }

        public static Bounds defaults() {
            return new Bounds(4_096, 8_192);
        }
    }

    public record LegendEntry(String styleId, String label, String narration) {
        public LegendEntry {
            styleId = text(styleId, "legend.styleId");
            label = text(label, "legend.label");
            narration = text(narration, "legend.narration");
        }
    }

    /** Stable key/value evidence shown only when a graph entity is inspected. */
    public record Detail(String key, String value) {
        public Detail {
            key = text(key, "detail.key");
            value = text(value, "detail.value");
        }
    }

    public record Node(
            String id,
            List<NodeOrigin> origins,
            List<LegendRole> roles,
            String label,
            String narration,
            List<Detail> details
    ) {
        public Node {
            id = text(id, "node.id");
            origins = canonicalEnums(origins, "node.origins");
            if (origins.isEmpty()) throw new IllegalArgumentException("A node requires an origin");
            roles = canonicalEnums(roles, "node.roles");
            label = text(label, "node.label");
            narration = text(narration, "node.narration");
            details = canonicalDetails(details, "node.details");
        }
    }

    public record Edge(
            String id,
            String sourceContractId,
            String fromNodeId,
            String toNodeId,
            EdgeOrigin origin,
            EdgeCommitment commitment,
            List<LegendRole> roles,
            String label,
            String narration,
            List<Detail> details
    ) {
        public Edge {
            id = text(id, "edge.id");
            sourceContractId = text(sourceContractId, "edge.sourceContractId");
            fromNodeId = text(fromNodeId, "edge.fromNodeId");
            toNodeId = text(toNodeId, "edge.toNodeId");
            Objects.requireNonNull(origin, "edge.origin");
            Objects.requireNonNull(commitment, "edge.commitment");
            roles = canonicalEnums(roles, "edge.roles");
            label = text(label, "edge.label");
            narration = text(narration, "edge.narration");
            details = canonicalDetails(details, "edge.details");
            switch (commitment) {
                case COMMITTED_HISTORY -> {
                    if (origin != EdgeOrigin.HISTORY || !roles.contains(LegendRole.COMMITTED_EXECUTED)) {
                        throw new IllegalArgumentException(
                                "Committed presentation edges must be committed history with the committed role"
                        );
                    }
                }
                case PROJECTED_CANDIDATE -> {
                    if (roles.contains(LegendRole.COMMITTED_EXECUTED)) {
                        throw new IllegalArgumentException("Projected candidate edges cannot carry the committed role");
                    }
                    String accessibleText = (label + " " + narration).toLowerCase(java.util.Locale.ROOT);
                    if (accessibleText.contains("committed")) {
                        throw new IllegalArgumentException("Projected candidate edges cannot be narrated as committed");
                    }
                }
                case SEMANTIC_RELATION -> {
                    if (origin != EdgeOrigin.SEMANTIC) {
                        throw new IllegalArgumentException("Semantic relation edges require the semantic origin");
                    }
                }
            }
        }
    }

    public record Marker(
            String id,
            MarkerKind kind,
            MarkerSubjectKind subjectKind,
            String subjectId,
            LegendRole role,
            String label,
            String narration
    ) {
        public Marker {
            id = text(id, "marker.id");
            Objects.requireNonNull(kind, "marker.kind");
            Objects.requireNonNull(subjectKind, "marker.subjectKind");
            subjectId = text(subjectId, "marker.subjectId");
            Objects.requireNonNull(role, "marker.role");
            label = text(label, "marker.label");
            narration = text(narration, "marker.narration");
            LegendRole expectedRole = switch (kind) {
                case ACTUAL_HEAD -> LegendRole.ACTUAL_HEAD;
                case INSTRUCTION_POINTER -> LegendRole.INSTRUCTION_POINTER;
            };
            if (role != expectedRole) throw new IllegalArgumentException("Marker kind and role must agree");
        }
    }

    public record ProjectionSummary(
            int totalNodes,
            int visibleNodes,
            int totalEdges,
            int visibleEdges,
            int totalMarkers,
            int visibleMarkers
    ) {
        public ProjectionSummary {
            requireCount(totalNodes, visibleNodes, "nodes");
            requireCount(totalEdges, visibleEdges, "edges");
            requireCount(totalMarkers, visibleMarkers, "markers");
        }

        public int omittedNodes() {
            return totalNodes - visibleNodes;
        }

        public int omittedEdges() {
            return totalEdges - visibleEdges;
        }

        public int omittedMarkers() {
            return totalMarkers - visibleMarkers;
        }

        public boolean truncated() {
            return omittedNodes() > 0 || omittedEdges() > 0 || omittedMarkers() > 0;
        }
    }

    public record Presentation(
            List<LegendEntry> legend,
            List<Node> nodes,
            List<Edge> edges,
            List<Marker> markers,
            ProjectionSummary summary
    ) {
        public Presentation {
            Objects.requireNonNull(legend, "legend");
            legend = List.copyOf(legend);
            if (!legend.equals(LegendRole.stableLegend())) {
                throw new IllegalArgumentException("Presentation legend must use the stable role order");
            }
            nodes = canonical(nodes, Node::id, "nodes");
            edges = canonical(edges, Edge::id, "edges");
            markers = canonical(markers, Marker::id, "markers");
            Objects.requireNonNull(summary, "summary");

            Set<String> nodeIds = nodes.stream().map(Node::id).collect(java.util.stream.Collectors.toSet());
            Set<String> edgeIds = edges.stream().map(Edge::id).collect(java.util.stream.Collectors.toSet());
            for (Edge edge : edges) {
                if (!nodeIds.contains(edge.fromNodeId()) || !nodeIds.contains(edge.toNodeId())) {
                    throw new IllegalArgumentException("Visible edges must not reference omitted nodes");
                }
            }
            for (Marker marker : markers) {
                boolean present = marker.subjectKind() == MarkerSubjectKind.NODE
                        ? nodeIds.contains(marker.subjectId())
                        : edgeIds.contains(marker.subjectId());
                if (!present) throw new IllegalArgumentException("Visible markers must reference visible subjects");
            }
            if (summary.visibleNodes() != nodes.size()
                    || summary.visibleEdges() != edges.size()
                    || summary.visibleMarkers() != markers.size()) {
                throw new IllegalArgumentException("Projection summary must match visible presentation entities");
            }
        }
    }

    static String describeRoles(List<LegendRole> roles) {
        if (roles.isEmpty()) return "";
        return roles.stream().map(LegendRole::narration).reduce((left, right) -> left + " " + right).orElse("");
    }

    private static void requireCount(int total, int visible, String label) {
        if (total < 0 || visible < 0 || visible > total) {
            throw new IllegalArgumentException("Invalid " + label + " projection counts");
        }
    }

    private static String text(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static <E extends Enum<E>> List<E> canonicalEnums(List<E> values, String label) {
        Objects.requireNonNull(values, label);
        ArrayList<E> answer = new ArrayList<>();
        HashSet<E> seen = new HashSet<>();
        for (E value : values) {
            Objects.requireNonNull(value, label + " item");
            if (seen.add(value)) answer.add(value);
        }
        answer.sort(Comparator.comparingInt(Enum::ordinal));
        return List.copyOf(answer);
    }

    private static <T> List<T> canonical(List<T> values, java.util.function.Function<T, String> id, String label) {
        Objects.requireNonNull(values, label);
        ArrayList<T> answer = new ArrayList<>();
        HashSet<String> identities = new HashSet<>();
        for (T value : values) {
            Objects.requireNonNull(value, label + " item");
            String identity = text(id.apply(value), label + " identity");
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("Duplicate " + label + " identity: " + identity);
            }
            answer.add(value);
        }
        answer.sort(Comparator.comparing(id));
        return List.copyOf(answer);
    }

    private static List<Detail> canonicalDetails(List<Detail> values, String label) {
        Objects.requireNonNull(values, label);
        ArrayList<Detail> answer = new ArrayList<>();
        HashSet<String> identities = new HashSet<>();
        for (Detail value : values) {
            Objects.requireNonNull(value, label + " item");
            if (!identities.add(value.key())) {
                throw new IllegalArgumentException("Duplicate " + label + " key: " + value.key());
            }
            answer.add(value);
        }
        answer.sort(Comparator.comparing(Detail::key));
        return List.copyOf(answer);
    }
}
