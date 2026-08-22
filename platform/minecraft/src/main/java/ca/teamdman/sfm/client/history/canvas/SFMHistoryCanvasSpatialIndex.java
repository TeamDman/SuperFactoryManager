package ca.teamdman.sfm.client.history.canvas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Bounded uniform-grid culling and hit-test index for one layout snapshot. */
public final class SFMHistoryCanvasSpatialIndex {
    public record Limits(
            int cellSize,
            int maxSubjects,
            int maxCells,
            int maxCellReferences,
            int maxCellsPerSubject,
            int maxGlobalSubjects,
            int maxQueryCells,
            int maxQueryResults,
            int curveHitSegments,
            int maxHitTolerance
    ) {
        public Limits {
            if (cellSize <= 0) throw new IllegalArgumentException("cellSize must be positive");
            if (maxSubjects <= 0) throw new IllegalArgumentException("maxSubjects must be positive");
            if (maxCells <= 0) throw new IllegalArgumentException("maxCells must be positive");
            if (maxCellReferences <= 0) throw new IllegalArgumentException("maxCellReferences must be positive");
            if (maxCellsPerSubject <= 0) throw new IllegalArgumentException("maxCellsPerSubject must be positive");
            if (maxGlobalSubjects < 0) throw new IllegalArgumentException("maxGlobalSubjects must not be negative");
            if (maxQueryCells <= 0) throw new IllegalArgumentException("maxQueryCells must be positive");
            if (maxQueryResults <= 0) throw new IllegalArgumentException("maxQueryResults must be positive");
            if (curveHitSegments < 2) throw new IllegalArgumentException("curveHitSegments must be at least 2");
            if (maxHitTolerance < 0) throw new IllegalArgumentException("maxHitTolerance must not be negative");
        }

        public static Limits defaults() {
            return new Limits(96, 16_384, 65_536, 1_000_000, 4_096, 2_048, 16_384, 4_096, 24, 64);
        }
    }

    public record Subject(SFMHistoryCanvasLayout.SubjectKind kind, String stableId)
            implements Comparable<Subject> {
        public Subject {
            Objects.requireNonNull(kind, "subject.kind");
            Objects.requireNonNull(stableId, "subject.stableId");
            if (stableId.isBlank()) throw new IllegalArgumentException("subject.stableId must not be blank");
        }

        @Override
        public int compareTo(Subject other) {
            int byKind = Integer.compare(kind.ordinal(), other.kind.ordinal());
            return byKind != 0 ? byKind : stableId.compareTo(other.stableId);
        }
    }

    public record Stats(
            int subjects,
            int cells,
            int cellReferences,
            int globalSubjects
    ) {
        public Stats {
            if (subjects < 0 || cells < 0 || cellReferences < 0 || globalSubjects < 0) {
                throw new IllegalArgumentException("Spatial-index statistics must not be negative");
            }
        }
    }

    public record CullingResult(
            List<SFMHistoryCanvasLayout.Node> nodes,
            List<SFMHistoryCanvasLayout.Edge> edges,
            int matchedNodes,
            int matchedEdges
    ) {
        public CullingResult {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
            if (matchedNodes < nodes.size() || matchedEdges < edges.size()) {
                throw new IllegalArgumentException("Matched counts cannot be smaller than returned counts");
            }
        }

        public boolean truncated() {
            return matchedNodes > nodes.size() || matchedEdges > edges.size();
        }
    }

    public record Hit(Subject subject, double distanceSquared) {
        public Hit {
            Objects.requireNonNull(subject, "hit.subject");
            if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0) {
                throw new IllegalArgumentException("hit.distanceSquared must be finite and non-negative");
            }
        }
    }

    private record Cell(int x, int y) implements Comparable<Cell> {
        @Override
        public int compareTo(Cell other) {
            int byY = Integer.compare(y, other.y);
            return byY != 0 ? byY : Integer.compare(x, other.x);
        }
    }

    private final Limits limits;
    private final Map<Cell, List<Subject>> cells;
    private final List<Subject> globalSubjects;
    private final List<Subject> allSubjects;
    private final Map<String, SFMHistoryCanvasLayout.Node> nodes;
    private final Map<String, SFMHistoryCanvasLayout.Edge> edges;
    private final Stats stats;

    private SFMHistoryCanvasSpatialIndex(
            Limits limits,
            Map<Cell, List<Subject>> cells,
            List<Subject> globalSubjects,
            List<Subject> allSubjects,
            Map<String, SFMHistoryCanvasLayout.Node> nodes,
            Map<String, SFMHistoryCanvasLayout.Edge> edges,
            Stats stats
    ) {
        this.limits = limits;
        this.cells = cells;
        this.globalSubjects = globalSubjects;
        this.allSubjects = allSubjects;
        this.nodes = nodes;
        this.edges = edges;
        this.stats = stats;
    }

    public static SFMHistoryCanvasSpatialIndex build(SFMHistoryCanvasLayout.Snapshot snapshot) {
        return build(snapshot, Limits.defaults());
    }

    public static SFMHistoryCanvasSpatialIndex build(
            SFMHistoryCanvasLayout.Snapshot snapshot,
            Limits limits
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(limits, "limits");
        int subjectCount = Math.addExact(snapshot.nodes().size(), snapshot.edges().size());
        if (subjectCount > limits.maxSubjects()) {
            throw new IllegalArgumentException("Canvas has " + subjectCount
                    + " spatial subjects; limit is " + limits.maxSubjects());
        }

        TreeMap<String, SFMHistoryCanvasLayout.Node> nodes = new TreeMap<>();
        snapshot.nodes().forEach(node -> nodes.put(node.stableId(), node));
        TreeMap<String, SFMHistoryCanvasLayout.Edge> edges = new TreeMap<>();
        snapshot.edges().forEach(edge -> edges.put(edge.stableId(), edge));

        TreeMap<Cell, TreeSet<Subject>> mutableCells = new TreeMap<>();
        TreeSet<Subject> global = new TreeSet<>();
        int[] references = {0};
        nodes.values().forEach(node -> add(
                mutableCells,
                global,
                new Subject(SFMHistoryCanvasLayout.SubjectKind.NODE, node.stableId()),
                node.bounds(),
                limits,
                references
        ));
        edges.values().forEach(edge -> add(
                mutableCells,
                global,
                new Subject(SFMHistoryCanvasLayout.SubjectKind.EDGE, edge.stableId()),
                edge.bounds(),
                limits,
                references
        ));

        if (mutableCells.size() > limits.maxCells()) {
            throw new IllegalArgumentException("Spatial index requires " + mutableCells.size()
                    + " cells; limit is " + limits.maxCells());
        }
        if (references[0] > limits.maxCellReferences()) {
            throw new IllegalArgumentException("Spatial index requires " + references[0]
                    + " references; limit is " + limits.maxCellReferences());
        }
        if (global.size() > limits.maxGlobalSubjects()) {
            throw new IllegalArgumentException("Spatial index requires " + global.size()
                    + " global subjects; limit is " + limits.maxGlobalSubjects());
        }

        TreeMap<Cell, List<Subject>> frozenCells = new TreeMap<>();
        mutableCells.forEach((cell, subjects) -> frozenCells.put(cell, List.copyOf(subjects)));
        ArrayList<Subject> allSubjects = new ArrayList<>(subjectCount);
        nodes.keySet().forEach(id -> allSubjects.add(new Subject(SFMHistoryCanvasLayout.SubjectKind.NODE, id)));
        edges.keySet().forEach(id -> allSubjects.add(new Subject(SFMHistoryCanvasLayout.SubjectKind.EDGE, id)));
        allSubjects.sort(Subject::compareTo);

        return new SFMHistoryCanvasSpatialIndex(
                limits,
                Map.copyOf(frozenCells),
                List.copyOf(global),
                List.copyOf(allSubjects),
                Map.copyOf(nodes),
                Map.copyOf(edges),
                new Stats(subjectCount, frozenCells.size(), references[0], global.size())
        );
    }

    public Stats stats() {
        return stats;
    }

    public CullingResult query(SFMHistoryCanvasLayout.Rect viewport) {
        Objects.requireNonNull(viewport, "viewport");
        if (viewport.isEmpty()) return new CullingResult(List.of(), List.of(), 0, 0);
        TreeSet<Subject> candidates = candidates(viewport);
        ArrayList<SFMHistoryCanvasLayout.Node> visibleNodes = new ArrayList<>();
        ArrayList<SFMHistoryCanvasLayout.Edge> visibleEdges = new ArrayList<>();
        int matchedNodes = 0;
        int matchedEdges = 0;
        int emitted = 0;
        for (Subject subject : candidates) {
            if (!bounds(subject).intersects(viewport)) continue;
            if (subject.kind() == SFMHistoryCanvasLayout.SubjectKind.NODE) {
                matchedNodes++;
                if (emitted < limits.maxQueryResults()) {
                    visibleNodes.add(nodes.get(subject.stableId()));
                    emitted++;
                }
            } else {
                matchedEdges++;
                if (emitted < limits.maxQueryResults()) {
                    visibleEdges.add(edges.get(subject.stableId()));
                    emitted++;
                }
            }
        }
        return new CullingResult(visibleNodes, visibleEdges, matchedNodes, matchedEdges);
    }

    public Optional<Hit> hitTest(SFMHistoryCanvasLayout.Point point, int tolerance) {
        Objects.requireNonNull(point, "point");
        if (tolerance < 0 || tolerance > limits.maxHitTolerance()) {
            throw new IllegalArgumentException("Hit tolerance must be between 0 and " + limits.maxHitTolerance());
        }
        int diameter = Math.addExact(Math.multiplyExact(tolerance, 2), 1);
        SFMHistoryCanvasLayout.Rect query = new SFMHistoryCanvasLayout.Rect(
                Math.subtractExact(point.x(), tolerance),
                Math.subtractExact(point.y(), tolerance),
                diameter,
                diameter
        );
        TreeSet<Subject> candidates = candidates(query);

        Optional<Hit> nodeHit = candidates.stream()
                .filter(subject -> subject.kind() == SFMHistoryCanvasLayout.SubjectKind.NODE)
                .map(subject -> nodes.get(subject.stableId()))
                .filter(node -> node.bounds().expand(tolerance).contains(point))
                .sorted(Comparator.comparingLong((SFMHistoryCanvasLayout.Node node) -> node.bounds().area())
                        .thenComparing(SFMHistoryCanvasLayout.Node::stableId))
                .map(node -> new Hit(
                        new Subject(SFMHistoryCanvasLayout.SubjectKind.NODE, node.stableId()),
                        0.0
                ))
                .findFirst();
        if (nodeHit.isPresent()) return nodeHit;

        double maximumDistanceSquared = (double) tolerance * tolerance;
        return candidates.stream()
                .filter(subject -> subject.kind() == SFMHistoryCanvasLayout.SubjectKind.EDGE)
                .map(subject -> edges.get(subject.stableId()))
                .map(edge -> new Hit(
                        new Subject(SFMHistoryCanvasLayout.SubjectKind.EDGE, edge.stableId()),
                        distanceSquared(point, edge, limits.curveHitSegments())
                ))
                .filter(hit -> hit.distanceSquared() <= maximumDistanceSquared)
                .sorted(Comparator.comparingDouble(Hit::distanceSquared).thenComparing(Hit::subject))
                .findFirst();
    }

    public Optional<SFMHistoryCanvasLayout.Node> node(String stableId) {
        return Optional.ofNullable(nodes.get(stableId));
    }

    public Optional<SFMHistoryCanvasLayout.Edge> edge(String stableId) {
        return Optional.ofNullable(edges.get(stableId));
    }

    public boolean contains(Subject subject) {
        Objects.requireNonNull(subject, "subject");
        return subject.kind() == SFMHistoryCanvasLayout.SubjectKind.NODE
                ? nodes.containsKey(subject.stableId())
                : edges.containsKey(subject.stableId());
    }

    private TreeSet<Subject> candidates(SFMHistoryCanvasLayout.Rect viewport) {
        CellRange range = cellRange(viewport, limits.cellSize());
        TreeSet<Subject> answer = new TreeSet<>();
        if (range.cellCount() > limits.maxQueryCells()) {
            answer.addAll(allSubjects);
            return answer;
        }
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                answer.addAll(cells.getOrDefault(new Cell(x, y), List.of()));
            }
        }
        answer.addAll(globalSubjects);
        return answer;
    }

    private SFMHistoryCanvasLayout.Rect bounds(Subject subject) {
        return subject.kind() == SFMHistoryCanvasLayout.SubjectKind.NODE
                ? nodes.get(subject.stableId()).bounds()
                : edges.get(subject.stableId()).bounds();
    }

    private static void add(
            Map<Cell, TreeSet<Subject>> cells,
            Set<Subject> global,
            Subject subject,
            SFMHistoryCanvasLayout.Rect bounds,
            Limits limits,
            int[] references
    ) {
        CellRange range = cellRange(bounds, limits.cellSize());
        if (range.cellCount() > limits.maxCellsPerSubject()) {
            global.add(subject);
            return;
        }
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                if (cells.computeIfAbsent(new Cell(x, y), ignored -> new TreeSet<>()).add(subject)) {
                    references[0] = Math.addExact(references[0], 1);
                }
            }
        }
    }

    private record CellRange(int minX, int maxX, int minY, int maxY) {
        long cellCount() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1);
        }
    }

    private static CellRange cellRange(SFMHistoryCanvasLayout.Rect bounds, int cellSize) {
        if (bounds.isEmpty()) return new CellRange(0, 0, 0, 0);
        return new CellRange(
                Math.floorDiv(bounds.x(), cellSize),
                Math.floorDiv(bounds.right() - 1, cellSize),
                Math.floorDiv(bounds.y(), cellSize),
                Math.floorDiv(bounds.bottom() - 1, cellSize)
        );
    }

    private static double distanceSquared(
            SFMHistoryCanvasLayout.Point point,
            SFMHistoryCanvasLayout.Edge edge,
            int curveSegments
    ) {
        List<SFMHistoryCanvasLayout.Point> path = edge.points();
        if (!edge.curved()) return segmentDistanceSquared(point, path.get(0), path.get(1));
        SFMHistoryCanvasLayout.Point previous = path.get(0);
        double best = Double.POSITIVE_INFINITY;
        for (int index = 1; index <= curveSegments; index++) {
            double t = (double) index / curveSegments;
            double oneMinusT = 1.0 - t;
            SFMHistoryCanvasLayout.Point current = new SFMHistoryCanvasLayout.Point(
                    (int) Math.round(oneMinusT * oneMinusT * path.get(0).x()
                            + 2.0 * oneMinusT * t * path.get(1).x()
                            + t * t * path.get(2).x()),
                    (int) Math.round(oneMinusT * oneMinusT * path.get(0).y()
                            + 2.0 * oneMinusT * t * path.get(1).y()
                            + t * t * path.get(2).y())
            );
            best = Math.min(best, segmentDistanceSquared(point, previous, current));
            previous = current;
        }
        return best;
    }

    private static double segmentDistanceSquared(
            SFMHistoryCanvasLayout.Point point,
            SFMHistoryCanvasLayout.Point start,
            SFMHistoryCanvasLayout.Point end
    ) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        if (dx == 0.0 && dy == 0.0) {
            double px = point.x() - start.x();
            double py = point.y() - start.y();
            return px * px + py * py;
        }
        double t = ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t));
        double projectedX = start.x() + t * dx;
        double projectedY = start.y() + t * dy;
        double px = point.x() - projectedX;
        double py = point.y() - projectedY;
        return px * px + py * py;
    }
}
