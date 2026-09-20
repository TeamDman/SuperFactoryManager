package ca.teamdman.sfm.client.history.canvas;

import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Renderer-independent geometry produced for the experienced-history canvas.
 *
 * <p>The source presentation nodes and edges remain attached to every layout
 * entity. Layout therefore adds geometry without replacing or flattening the
 * intent, evaluation, outcome, state, role, marker, or evidence authority in
 * {@link SFMHistoryGraphPresentationModel}.</p>
 */
public final class SFMHistoryCanvasLayout {
    public static final String SCHEMA = "sfm.history-canvas-layout/1";

    private SFMHistoryCanvasLayout() {
    }

    public enum Orientation {
        TOP_DOWN,
        LEFT_RIGHT;

        public Orientation transposed() {
            return this == TOP_DOWN ? LEFT_RIGHT : TOP_DOWN;
        }
    }

    /** The two semantic lanes and their default renderer style tokens. */
    public enum Lane {
        ACTION("history-action-blue", 0xFF1F4F73),
        STATE("history-state-amber", 0xFF9A5A00);

        private final String styleId;
        private final int defaultFillArgb;

        Lane(String styleId, int defaultFillArgb) {
            this.styleId = styleId;
            this.defaultFillArgb = defaultFillArgb;
        }

        public String styleId() {
            return styleId;
        }

        public int defaultFillArgb() {
            return defaultFillArgb;
        }
    }

    public enum SubjectKind {
        NODE,
        EDGE
    }

    public enum EdgePathKind {
        RANKING(false, true),
        HEAD_MOVEMENT_JUMP(true, false),
        CYCLE_OVERLAY(true, false);

        private final boolean curved;
        private final boolean ranking;

        EdgePathKind(boolean curved, boolean ranking) {
            this.curved = curved;
            this.ranking = ranking;
        }

        public boolean curved() {
            return curved;
        }

        public boolean ranking() {
            return ranking;
        }
    }

    public record Point(int x, int y) {
        public Point transpose() {
            return new Point(y, x);
        }
    }

    /** Half-open integer rectangle in canvas coordinates. */
    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0) throw new IllegalArgumentException("width must not be negative");
            if (height < 0) throw new IllegalArgumentException("height must not be negative");
            Math.addExact(x, width);
            Math.addExact(y, height);
        }

        public int right() {
            return Math.addExact(x, width);
        }

        public int bottom() {
            return Math.addExact(y, height);
        }

        public long area() {
            return (long) width * height;
        }

        public boolean isEmpty() {
            return width == 0 || height == 0;
        }

        public boolean contains(Point point) {
            Objects.requireNonNull(point, "point");
            return !isEmpty()
                    && point.x() >= x
                    && point.x() < right()
                    && point.y() >= y
                    && point.y() < bottom();
        }

        public boolean contains(Rect other) {
            Objects.requireNonNull(other, "other");
            if (other.isEmpty()) return true;
            return other.x() >= x
                    && other.y() >= y
                    && other.right() <= right()
                    && other.bottom() <= bottom();
        }

        public boolean intersects(Rect other) {
            Objects.requireNonNull(other, "other");
            return !isEmpty()
                    && !other.isEmpty()
                    && x < other.right()
                    && other.x() < right()
                    && y < other.bottom()
                    && other.y() < bottom();
        }

        public Rect expand(int amount) {
            if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
            return new Rect(
                    Math.subtractExact(x, amount),
                    Math.subtractExact(y, amount),
                    Math.addExact(width, Math.multiplyExact(amount, 2)),
                    Math.addExact(height, Math.multiplyExact(amount, 2))
            );
        }

        public Rect union(Rect other) {
            Objects.requireNonNull(other, "other");
            if (isEmpty()) return other;
            if (other.isEmpty()) return this;
            int left = Math.min(x, other.x());
            int top = Math.min(y, other.y());
            int right = Math.max(right(), other.right());
            int bottom = Math.max(bottom(), other.bottom());
            return new Rect(left, top, Math.subtractExact(right, left), Math.subtractExact(bottom, top));
        }

        public Rect transpose() {
            return new Rect(y, x, height, width);
        }
    }

    /** Full source text plus the bounded lines used by a renderer. */
    public record Label(String fullText, List<String> lines, boolean truncated) {
        public Label {
            fullText = nonBlank(fullText, "label.fullText");
            Objects.requireNonNull(lines, "label.lines");
            if (lines.isEmpty()) throw new IllegalArgumentException("A label requires at least one display line");
            ArrayList<String> copied = new ArrayList<>(lines.size());
            for (String line : lines) copied.add(Objects.requireNonNull(line, "label line"));
            lines = List.copyOf(copied);
        }
    }

    public record Node(
            String stableId,
            Lane lane,
            int rank,
            int track,
            Rect bounds,
            Rect markerBounds,
            Rect labelBounds,
            Label label,
            List<SFMHistoryGraphPresentationModel.Marker> markers,
            SFMHistoryGraphPresentationModel.Node source
    ) {
        public Node {
            stableId = nonBlank(stableId, "node.stableId");
            Objects.requireNonNull(lane, "node.lane");
            if (rank < 0) throw new IllegalArgumentException("node.rank must not be negative");
            if (track < 0) throw new IllegalArgumentException("node.track must not be negative");
            Objects.requireNonNull(bounds, "node.bounds");
            Objects.requireNonNull(markerBounds, "node.markerBounds");
            Objects.requireNonNull(labelBounds, "node.labelBounds");
            if (bounds.isEmpty()) throw new IllegalArgumentException("node.bounds must not be empty");
            if (!bounds.contains(markerBounds) || !bounds.contains(labelBounds)) {
                throw new IllegalArgumentException("Node bounds must contain marker and label bounds");
            }
            Objects.requireNonNull(label, "node.label");
            markers = immutableMarkers(markers);
            Objects.requireNonNull(source, "node.source");
            if (!stableId.equals(source.id())) {
                throw new IllegalArgumentException("Node stable id must preserve the source node id");
            }
        }

        public String styleId() {
            return lane.styleId();
        }

        public Node transpose() {
            return new Node(
                    stableId,
                    lane,
                    rank,
                    track,
                    bounds.transpose(),
                    markerBounds.transpose(),
                    labelBounds.transpose(),
                    label,
                    markers,
                    source
            );
        }
    }

    /**
     * Directed path. Ranking paths have two points; curved overlays have a
     * quadratic start/control/end triplet. Point order is source to target.
     */
    public record Edge(
            String stableId,
            String fromNodeId,
            String toNodeId,
            int sourceRank,
            int targetRank,
            EdgePathKind pathKind,
            List<Point> points,
            Rect bounds,
            List<SFMHistoryGraphPresentationModel.Marker> markers,
            SFMHistoryGraphPresentationModel.Edge source
    ) {
        public Edge {
            stableId = nonBlank(stableId, "edge.stableId");
            fromNodeId = nonBlank(fromNodeId, "edge.fromNodeId");
            toNodeId = nonBlank(toNodeId, "edge.toNodeId");
            if (sourceRank < 0 || targetRank < 0) {
                throw new IllegalArgumentException("Edge ranks must not be negative");
            }
            Objects.requireNonNull(pathKind, "edge.pathKind");
            Objects.requireNonNull(points, "edge.points");
            points = List.copyOf(points);
            int expectedPoints = pathKind.curved() ? 3 : 2;
            if (points.size() != expectedPoints) {
                throw new IllegalArgumentException(pathKind + " paths require " + expectedPoints + " points");
            }
            Objects.requireNonNull(bounds, "edge.bounds");
            if (bounds.isEmpty()) throw new IllegalArgumentException("edge.bounds must not be empty");
            for (Point point : points) {
                if (!bounds.contains(point)) throw new IllegalArgumentException("Edge bounds must contain path points");
            }
            markers = immutableMarkers(markers);
            Objects.requireNonNull(source, "edge.source");
            if (!stableId.equals(source.id())
                    || !fromNodeId.equals(source.fromNodeId())
                    || !toNodeId.equals(source.toNodeId())) {
                throw new IllegalArgumentException("Edge geometry must preserve source edge identity and direction");
            }
        }

        public boolean ranking() {
            return pathKind.ranking();
        }

        public boolean curved() {
            return pathKind.curved();
        }

        public Edge transpose() {
            return new Edge(
                    stableId,
                    fromNodeId,
                    toNodeId,
                    sourceRank,
                    targetRank,
                    pathKind,
                    points.stream().map(Point::transpose).toList(),
                    bounds.transpose(),
                    markers,
                    source
            );
        }
    }

    /**
     * Machine-readable, renderer-independent layout snapshot. All collections
     * are canonicalized by stable source identity.
     */
    public record Snapshot(
            String schema,
            Orientation orientation,
            Rect contentBounds,
            List<Node> nodes,
            List<Edge> edges,
            List<SFMHistoryGraphPresentationModel.Marker> markers
    ) {
        public Snapshot {
            if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported canvas schema: " + schema);
            Objects.requireNonNull(orientation, "orientation");
            Objects.requireNonNull(contentBounds, "contentBounds");
            nodes = canonical(nodes, Node::stableId, "nodes");
            edges = canonical(edges, Edge::stableId, "edges");
            markers = immutableMarkers(markers);

            Set<String> nodeIds = nodes.stream().map(Node::stableId).collect(java.util.stream.Collectors.toSet());
            for (Node node : nodes) {
                if (!contentBounds.contains(node.bounds())) {
                    throw new IllegalArgumentException("Content bounds omit node " + node.stableId());
                }
            }
            for (Edge edge : edges) {
                if (!nodeIds.contains(edge.fromNodeId()) || !nodeIds.contains(edge.toNodeId())) {
                    throw new IllegalArgumentException("Canvas edge references an omitted node: " + edge.stableId());
                }
                if (!contentBounds.contains(edge.bounds())) {
                    throw new IllegalArgumentException("Content bounds omit edge " + edge.stableId());
                }
            }
        }

        public Snapshot transpose() {
            return new Snapshot(
                    schema,
                    orientation.transposed(),
                    contentBounds.transpose(),
                    nodes.stream().map(Node::transpose).toList(),
                    edges.stream().map(Edge::transpose).toList(),
                    markers
            );
        }

        public Set<String> nodeIds() {
            return nodes.stream().map(Node::stableId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        public Set<String> edgeIds() {
            return edges.stream().map(Edge::stableId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    private static List<SFMHistoryGraphPresentationModel.Marker> immutableMarkers(
            List<SFMHistoryGraphPresentationModel.Marker> markers
    ) {
        Objects.requireNonNull(markers, "markers");
        ArrayList<SFMHistoryGraphPresentationModel.Marker> answer = new ArrayList<>(markers);
        answer.sort(Comparator.comparing(SFMHistoryGraphPresentationModel.Marker::id));
        return List.copyOf(answer);
    }

    private static <T> List<T> canonical(
            List<T> values,
            java.util.function.Function<T, String> id,
            String label
    ) {
        Objects.requireNonNull(values, label);
        ArrayList<T> answer = new ArrayList<>(values.size());
        HashSet<String> ids = new HashSet<>();
        for (T value : values) {
            Objects.requireNonNull(value, label + " item");
            String stableId = nonBlank(id.apply(value), label + " identity");
            if (!ids.add(stableId)) throw new IllegalArgumentException("Duplicate " + label + " identity: " + stableId);
            answer.add(value);
        }
        answer.sort(Comparator.comparing(id));
        return List.copyOf(answer);
    }

    private static String nonBlank(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
