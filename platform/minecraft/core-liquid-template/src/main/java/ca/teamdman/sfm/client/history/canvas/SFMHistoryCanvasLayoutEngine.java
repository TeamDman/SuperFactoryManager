package ca.teamdman.sfm.client.history.canvas;

import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Deterministic layout and indexing facade consumed by a future runtime panel. */
public final class SFMHistoryCanvasLayoutEngine {
    private static final EnumSet<SFMHistoryGraphPresentationModel.NodeOrigin> STATE_ORIGINS = EnumSet.of(
            SFMHistoryGraphPresentationModel.NodeOrigin.HISTORY,
            SFMHistoryGraphPresentationModel.NodeOrigin.TRAJECTORY,
            SFMHistoryGraphPresentationModel.NodeOrigin.SEARCH,
            SFMHistoryGraphPresentationModel.NodeOrigin.SNAPSHOT_FRAME
    );

    private SFMHistoryCanvasLayoutEngine() {
    }

    public record Config(
            int margin,
            int markerDiameter,
            int markerLabelGap,
            int nodePadding,
            int estimatedGlyphWidth,
            int lineHeight,
            int maxLabelCodePointsPerLine,
            int maxLabelLines,
            int laneGap,
            int trackGap,
            int rankGap,
            int edgePadding,
            int curveOffset,
            int maxNodes,
            int maxEdges,
            int maxCanvasExtent
    ) {
        public Config {
            requirePositive(margin, "margin");
            requirePositive(markerDiameter, "markerDiameter");
            requirePositive(markerLabelGap, "markerLabelGap");
            requirePositive(nodePadding, "nodePadding");
            requirePositive(estimatedGlyphWidth, "estimatedGlyphWidth");
            requirePositive(lineHeight, "lineHeight");
            requirePositive(maxLabelCodePointsPerLine, "maxLabelCodePointsPerLine");
            requirePositive(maxLabelLines, "maxLabelLines");
            requirePositive(laneGap, "laneGap");
            requirePositive(trackGap, "trackGap");
            requirePositive(rankGap, "rankGap");
            requirePositive(edgePadding, "edgePadding");
            requirePositive(curveOffset, "curveOffset");
            requirePositive(maxNodes, "maxNodes");
            if (maxEdges < 0) throw new IllegalArgumentException("maxEdges must not be negative");
            requirePositive(maxCanvasExtent, "maxCanvasExtent");
            if (maximumNodeWidth() > maxCanvasExtent || maximumNodeHeight() > maxCanvasExtent) {
                throw new IllegalArgumentException("Configured node bounds exceed maxCanvasExtent");
            }
        }

        public static Config defaults() {
            return new Config(40, 16, 8, 6, 6, 10, 48, 3, 48, 16, 28, 3, 32,
                    4_096, 8_192, 4_000_000);
        }

        /**
         * Compact paired lanes for the narrow split-panel surface used by an
         * ordinary document history. Full labels remain available through the
         * transcript and details while the frontline canvas keeps action and
         * resulting-state labels readable at Minecraft GUI scales.
         */
        public static Config documentHistoryDefaults() {
            return new Config(24, 14, 6, 4, 6, 10, 20, 2, 24, 12, 20, 3, 24,
                    4_096, 8_192, 4_000_000);
        }

        public int maximumNodeWidth() {
            int textWidth = Math.multiplyExact(maxLabelCodePointsPerLine, estimatedGlyphWidth);
            return Math.addExact(
                    Math.multiplyExact(nodePadding, 2),
                    Math.addExact(markerDiameter, Math.addExact(markerLabelGap, textWidth))
            );
        }

        public int maximumNodeHeight() {
            int labelHeight = Math.multiplyExact(maxLabelLines, lineHeight);
            return Math.addExact(Math.multiplyExact(nodePadding, 2), Math.max(markerDiameter, labelHeight));
        }
    }

    public record Request(
            SFMHistoryCanvasLayout.Orientation orientation,
            Config config,
            SFMHistoryCanvasSpatialIndex.Limits spatialIndexLimits
    ) {
        public Request {
            Objects.requireNonNull(orientation, "orientation");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(spatialIndexLimits, "spatialIndexLimits");
        }

        public static Request defaults() {
            return new Request(
                    SFMHistoryCanvasLayout.Orientation.TOP_DOWN,
                    Config.defaults(),
                    SFMHistoryCanvasSpatialIndex.Limits.defaults()
            );
        }

        public Request withOrientation(SFMHistoryCanvasLayout.Orientation nextOrientation) {
            return new Request(nextOrientation, config, spatialIndexLimits);
        }
    }

    /** Complete pure output needed by a renderer/controller integration. */
    public record Result(
            SFMHistoryCanvasLayout.Snapshot snapshot,
            SFMHistoryCanvasSpatialIndex spatialIndex,
            SFMHistoryCanvasTranscript.Transcript transcript,
            SFMHistoryCanvasSpatialIndex.Limits spatialIndexLimits
    ) {
        public Result {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(spatialIndex, "spatialIndex");
            Objects.requireNonNull(transcript, "transcript");
            Objects.requireNonNull(spatialIndexLimits, "spatialIndexLimits");
        }

        /** Exact x/y and width/height transpose; source identities are unchanged. */
        public Result transpose() {
            SFMHistoryCanvasLayout.Snapshot transposed = snapshot.transpose();
            return finish(transposed, spatialIndexLimits);
        }

        public SFMHistoryCanvasSpatialIndex.CullingResult visible(SFMHistoryCanvasLayout.Rect viewport) {
            return spatialIndex.query(viewport);
        }

        public Optional<SFMHistoryCanvasSpatialIndex.Hit> hitTest(
                SFMHistoryCanvasLayout.Point canvasPoint,
                int tolerance
        ) {
            return spatialIndex.hitTest(canvasPoint, tolerance);
        }

        public SFMHistoryCanvasInteractionState retainInteraction(
                SFMHistoryCanvasInteractionState previous
        ) {
            return Objects.requireNonNull(previous, "previous").retain(spatialIndex);
        }
    }

    public static Result layout(SFMHistoryGraphPresentationModel.Presentation presentation) {
        return layout(presentation, Request.defaults());
    }

    public static Result layout(
            SFMHistoryGraphPresentationModel.Presentation presentation,
            Request request
    ) {
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(request, "request");
        Config config = request.config();
        if (presentation.nodes().size() > config.maxNodes()) {
            throw new IllegalArgumentException("Presentation has " + presentation.nodes().size()
                    + " nodes; canvas limit is " + config.maxNodes());
        }
        if (presentation.edges().size() > config.maxEdges()) {
            throw new IllegalArgumentException("Presentation has " + presentation.edges().size()
                    + " edges; canvas limit is " + config.maxEdges());
        }

        SFMHistoryCanvasLayout.Snapshot topDown = layoutTopDown(presentation, config);
        SFMHistoryCanvasLayout.Snapshot oriented = request.orientation() == SFMHistoryCanvasLayout.Orientation.TOP_DOWN
                ? topDown
                : topDown.transpose();
        return finish(oriented, request.spatialIndexLimits());
    }

    public static SFMHistoryCanvasLayout.Lane laneFor(SFMHistoryGraphPresentationModel.Node source) {
        Objects.requireNonNull(source, "source");
        return source.origins().stream().anyMatch(STATE_ORIGINS::contains)
                ? SFMHistoryCanvasLayout.Lane.STATE
                : SFMHistoryCanvasLayout.Lane.ACTION;
    }

    private static Result finish(
            SFMHistoryCanvasLayout.Snapshot snapshot,
            SFMHistoryCanvasSpatialIndex.Limits limits
    ) {
        SFMHistoryCanvasSpatialIndex index = SFMHistoryCanvasSpatialIndex.build(snapshot, limits);
        return new Result(snapshot, index, SFMHistoryCanvasTranscript.project(snapshot), limits);
    }

    private static SFMHistoryCanvasLayout.Snapshot layoutTopDown(
            SFMHistoryGraphPresentationModel.Presentation presentation,
            Config config
    ) {
        TreeMap<String, SFMHistoryGraphPresentationModel.Node> sourceNodes = new TreeMap<>();
        presentation.nodes().forEach(node -> sourceNodes.put(node.id(), node));
        TreeMap<String, SFMHistoryGraphPresentationModel.Edge> sourceEdges = new TreeMap<>();
        presentation.edges().forEach(edge -> sourceEdges.put(edge.id(), edge));

        GraphAnalysis graph = analyzeGraph(sourceNodes, sourceEdges);
        Map<String, List<SFMHistoryGraphPresentationModel.Marker>> nodeMarkers = markers(
                presentation,
                SFMHistoryGraphPresentationModel.MarkerSubjectKind.NODE
        );
        Map<String, List<SFMHistoryGraphPresentationModel.Marker>> edgeMarkers = markers(
                presentation,
                SFMHistoryGraphPresentationModel.MarkerSubjectKind.EDGE
        );

        TreeMap<RankLane, List<SFMHistoryGraphPresentationModel.Node>> groups = new TreeMap<>();
        for (SFMHistoryGraphPresentationModel.Node node : sourceNodes.values()) {
            groups.computeIfAbsent(
                    new RankLane(graph.ranks().get(node.id()), laneFor(node)),
                    ignored -> new ArrayList<>()
            ).add(node);
        }
        groups.values().forEach(nodes -> nodes.sort(Comparator.comparing(
                SFMHistoryGraphPresentationModel.Node::id)));

        int actionTracks = maximumTracks(groups, SFMHistoryCanvasLayout.Lane.ACTION);
        int trackPitch = Math.addExact(config.maximumNodeWidth(), config.trackGap());
        int actionLaneWidth = laneWidth(actionTracks, trackPitch, config.trackGap());
        int stateLaneStart = Math.addExact(
                Math.addExact(config.margin(), actionLaneWidth),
                config.laneGap()
        );
        int rankPitch = Math.addExact(config.maximumNodeHeight(), config.rankGap());

        TreeMap<String, SFMHistoryCanvasLayout.Node> nodes = new TreeMap<>();
        for (Map.Entry<RankLane, List<SFMHistoryGraphPresentationModel.Node>> entry : groups.entrySet()) {
            RankLane group = entry.getKey();
            for (int track = 0; track < entry.getValue().size(); track++) {
                SFMHistoryGraphPresentationModel.Node source = entry.getValue().get(track);
                WrappedLabel wrapped = wrapLabel(source.label(), config);
                int x = group.lane() == SFMHistoryCanvasLayout.Lane.ACTION ? config.margin() : stateLaneStart;
                x = Math.addExact(x, Math.multiplyExact(track, trackPitch));
                int rowTop = Math.addExact(config.margin(), Math.multiplyExact(group.rank(), rankPitch));
                int nodeHeight = Math.addExact(
                        Math.multiplyExact(config.nodePadding(), 2),
                        Math.max(config.markerDiameter(), wrapped.height())
                );
                int y = Math.addExact(rowTop, (config.maximumNodeHeight() - nodeHeight) / 2);
                int nodeWidth = Math.addExact(
                        Math.multiplyExact(config.nodePadding(), 2),
                        Math.addExact(config.markerDiameter(), Math.addExact(config.markerLabelGap(), wrapped.width()))
                );
                SFMHistoryCanvasLayout.Rect bounds = new SFMHistoryCanvasLayout.Rect(x, y, nodeWidth, nodeHeight);
                int markerY = Math.addExact(y, (nodeHeight - config.markerDiameter()) / 2);
                SFMHistoryCanvasLayout.Rect markerBounds = new SFMHistoryCanvasLayout.Rect(
                        Math.addExact(x, config.nodePadding()),
                        markerY,
                        config.markerDiameter(),
                        config.markerDiameter()
                );
                int labelX = Math.addExact(markerBounds.right(), config.markerLabelGap());
                int labelY = Math.addExact(y, (nodeHeight - wrapped.height()) / 2);
                SFMHistoryCanvasLayout.Rect labelBounds = new SFMHistoryCanvasLayout.Rect(
                        labelX,
                        labelY,
                        wrapped.width(),
                        wrapped.height()
                );
                nodes.put(source.id(), new SFMHistoryCanvasLayout.Node(
                        source.id(),
                        group.lane(),
                        group.rank(),
                        track,
                        bounds,
                        markerBounds,
                        labelBounds,
                        wrapped.label(),
                        nodeMarkers.getOrDefault(source.id(), List.of()),
                        source
                ));
            }
        }

        ArrayList<SFMHistoryCanvasLayout.Edge> edges = new ArrayList<>();
        for (SFMHistoryGraphPresentationModel.Edge source : sourceEdges.values()) {
            SFMHistoryCanvasLayout.Node from = require(nodes, source.fromNodeId(), "edge source node");
            SFMHistoryCanvasLayout.Node to = require(nodes, source.toNodeId(), "edge target node");
            SFMHistoryCanvasLayout.EdgePathKind pathKind = graph.edgeKinds().get(source.id());
            List<SFMHistoryCanvasLayout.Point> points = path(
                    from.bounds(),
                    to.bounds(),
                    pathKind,
                    config.curveOffset()
            );
            SFMHistoryCanvasLayout.Rect edgeBounds = bounds(points).expand(config.edgePadding());
            edges.add(new SFMHistoryCanvasLayout.Edge(
                    source.id(),
                    source.fromNodeId(),
                    source.toNodeId(),
                    from.rank(),
                    to.rank(),
                    pathKind,
                    points,
                    edgeBounds,
                    edgeMarkers.getOrDefault(source.id(), List.of()),
                    source
            ));
        }

        SFMHistoryCanvasLayout.Rect contentBounds = contentBounds(nodes.values(), edges, config.margin());
        if (contentBounds.width() > config.maxCanvasExtent() || contentBounds.height() > config.maxCanvasExtent()) {
            throw new IllegalArgumentException("Canvas extent " + contentBounds.width() + "x" + contentBounds.height()
                    + " exceeds configured maximum " + config.maxCanvasExtent());
        }
        return new SFMHistoryCanvasLayout.Snapshot(
                SFMHistoryCanvasLayout.SCHEMA,
                SFMHistoryCanvasLayout.Orientation.TOP_DOWN,
                contentBounds,
                new ArrayList<>(nodes.values()),
                edges,
                presentation.markers()
        );
    }

    private static GraphAnalysis analyzeGraph(
            Map<String, SFMHistoryGraphPresentationModel.Node> nodes,
            Map<String, SFMHistoryGraphPresentationModel.Edge> edges
    ) {
        HashSet<String> jumps = new HashSet<>();
        TreeMap<String, TreeSet<String>> adjacency = new TreeMap<>();
        nodes.keySet().forEach(id -> adjacency.put(id, new TreeSet<>()));
        for (SFMHistoryGraphPresentationModel.Edge edge : edges.values()) {
            SFMHistoryGraphPresentationModel.Node from = require(nodes, edge.fromNodeId(), "edge source");
            SFMHistoryGraphPresentationModel.Node to = require(nodes, edge.toNodeId(), "edge target");
            if (isHeadMovement(from) && laneFor(to) == SFMHistoryCanvasLayout.Lane.STATE) {
                jumps.add(edge.id());
            } else {
                adjacency.get(edge.fromNodeId()).add(edge.toNodeId());
            }
        }

        List<List<String>> discoveredComponents = new StronglyConnectedComponents(adjacency).components();
        List<List<String>> components = discoveredComponents.stream()
                .map(component -> component.stream().sorted().toList())
                .sorted(Comparator.comparing(component -> component.get(0)))
                .toList();
        HashMap<String, Integer> componentByNode = new HashMap<>();
        for (int component = 0; component < components.size(); component++) {
            for (String node : components.get(component)) componentByNode.put(node, component);
        }

        TreeMap<String, SFMHistoryCanvasLayout.EdgePathKind> edgeKinds = new TreeMap<>();
        ArrayList<TreeSet<Integer>> componentOutgoing = new ArrayList<>();
        int[] indegree = new int[components.size()];
        for (int index = 0; index < components.size(); index++) componentOutgoing.add(new TreeSet<>());
        for (SFMHistoryGraphPresentationModel.Edge edge : edges.values()) {
            if (jumps.contains(edge.id())) {
                edgeKinds.put(edge.id(), SFMHistoryCanvasLayout.EdgePathKind.HEAD_MOVEMENT_JUMP);
                continue;
            }
            int from = componentByNode.get(edge.fromNodeId());
            int to = componentByNode.get(edge.toNodeId());
            if (from == to) {
                edgeKinds.put(edge.id(), SFMHistoryCanvasLayout.EdgePathKind.CYCLE_OVERLAY);
            } else {
                edgeKinds.put(edge.id(), SFMHistoryCanvasLayout.EdgePathKind.RANKING);
                if (componentOutgoing.get(from).add(to)) indegree[to]++;
            }
        }

        int[] componentRanks = new int[components.size()];
        PriorityQueue<Integer> ready = new PriorityQueue<>(Comparator.comparing(index -> components.get(index).get(0)));
        for (int component = 0; component < components.size(); component++) {
            if (indegree[component] == 0) ready.add(component);
        }
        int visited = 0;
        while (!ready.isEmpty()) {
            int component = ready.remove();
            visited++;
            for (int target : componentOutgoing.get(component)) {
                componentRanks[target] = Math.max(componentRanks[target], Math.addExact(componentRanks[component], 1));
                if (--indegree[target] == 0) ready.add(target);
            }
        }
        if (visited != components.size()) throw new IllegalStateException("Condensed presentation graph must be acyclic");

        TreeMap<String, Integer> ranks = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : componentByNode.entrySet()) {
            ranks.put(entry.getKey(), componentRanks[entry.getValue()]);
        }
        return new GraphAnalysis(Map.copyOf(ranks), Map.copyOf(edgeKinds));
    }

    private static boolean isHeadMovement(SFMHistoryGraphPresentationModel.Node node) {
        return node.origins().contains(SFMHistoryGraphPresentationModel.NodeOrigin.HEAD_MOVEMENT);
    }

    private static Map<String, List<SFMHistoryGraphPresentationModel.Marker>> markers(
            SFMHistoryGraphPresentationModel.Presentation presentation,
            SFMHistoryGraphPresentationModel.MarkerSubjectKind kind
    ) {
        TreeMap<String, List<SFMHistoryGraphPresentationModel.Marker>> answer = new TreeMap<>();
        for (SFMHistoryGraphPresentationModel.Marker marker : presentation.markers()) {
            if (marker.subjectKind() != kind) continue;
            answer.computeIfAbsent(marker.subjectId(), ignored -> new ArrayList<>()).add(marker);
        }
        answer.replaceAll((ignored, values) -> values.stream()
                .sorted(Comparator.comparing(SFMHistoryGraphPresentationModel.Marker::id))
                .toList());
        return Map.copyOf(answer);
    }

    private static int maximumTracks(
            Map<RankLane, List<SFMHistoryGraphPresentationModel.Node>> groups,
            SFMHistoryCanvasLayout.Lane lane
    ) {
        return Math.max(1, groups.entrySet().stream()
                .filter(entry -> entry.getKey().lane() == lane)
                .mapToInt(entry -> entry.getValue().size())
                .max()
                .orElse(0));
    }

    private static int laneWidth(int tracks, int pitch, int gap) {
        return Math.subtractExact(Math.multiplyExact(tracks, pitch), gap);
    }

    private static WrappedLabel wrapLabel(String source, Config config) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        ArrayList<String> allLines = new ArrayList<>();
        for (String physicalLine : normalized.split("\n", -1)) {
            int codePoints = physicalLine.codePointCount(0, physicalLine.length());
            if (codePoints == 0) {
                allLines.add("");
                continue;
            }
            int start = 0;
            while (start < physicalLine.length()) {
                int remaining = physicalLine.codePointCount(start, physicalLine.length());
                int take = Math.min(config.maxLabelCodePointsPerLine(), remaining);
                int end = physicalLine.offsetByCodePoints(start, take);
                allLines.add(physicalLine.substring(start, end));
                start = end;
            }
        }
        boolean truncated = allLines.size() > config.maxLabelLines();
        ArrayList<String> displayed = new ArrayList<>(allLines.subList(0, Math.min(allLines.size(), config.maxLabelLines())));
        if (truncated) {
            int last = displayed.size() - 1;
            displayed.set(last, appendEllipsis(displayed.get(last), config.maxLabelCodePointsPerLine()));
        }
        int widest = displayed.stream().mapToInt(line -> line.codePointCount(0, line.length())).max().orElse(1);
        widest = Math.max(1, widest);
        int width = Math.multiplyExact(widest, config.estimatedGlyphWidth());
        int height = Math.multiplyExact(displayed.size(), config.lineHeight());
        return new WrappedLabel(
                new SFMHistoryCanvasLayout.Label(source, displayed, truncated),
                width,
                height
        );
    }

    private static String appendEllipsis(String value, int maximumCodePoints) {
        if (maximumCodePoints == 1) return "…";
        int codePoints = value.codePointCount(0, value.length());
        int keep = Math.min(codePoints, maximumCodePoints - 1);
        return value.substring(0, value.offsetByCodePoints(0, keep)) + "…";
    }

    private static List<SFMHistoryCanvasLayout.Point> path(
            SFMHistoryCanvasLayout.Rect from,
            SFMHistoryCanvasLayout.Rect to,
            SFMHistoryCanvasLayout.EdgePathKind kind,
            int curveOffset
    ) {
        if (kind == SFMHistoryCanvasLayout.EdgePathKind.RANKING) {
            return List.of(
                    new SFMHistoryCanvasLayout.Point(centerX(from), from.bottom()),
                    new SFMHistoryCanvasLayout.Point(centerX(to), to.y())
            );
        }
        boolean jump = kind == SFMHistoryCanvasLayout.EdgePathKind.HEAD_MOVEMENT_JUMP;
        int startX = jump ? from.x() : from.right();
        int targetX = jump ? to.x() : to.right();
        int controlX = jump
                ? Math.subtractExact(Math.min(startX, targetX), curveOffset)
                : Math.addExact(Math.max(startX, targetX), curveOffset);
        int startY = centerY(from);
        int targetY = centerY(to);
        return List.of(
                new SFMHistoryCanvasLayout.Point(startX, startY),
                new SFMHistoryCanvasLayout.Point(controlX, midpoint(startY, targetY)),
                new SFMHistoryCanvasLayout.Point(targetX, targetY)
        );
    }

    private static SFMHistoryCanvasLayout.Rect bounds(List<SFMHistoryCanvasLayout.Point> points) {
        int minX = points.stream().mapToInt(SFMHistoryCanvasLayout.Point::x).min().orElseThrow();
        int maxX = points.stream().mapToInt(SFMHistoryCanvasLayout.Point::x).max().orElseThrow();
        int minY = points.stream().mapToInt(SFMHistoryCanvasLayout.Point::y).min().orElseThrow();
        int maxY = points.stream().mapToInt(SFMHistoryCanvasLayout.Point::y).max().orElseThrow();
        return new SFMHistoryCanvasLayout.Rect(
                minX,
                minY,
                Math.addExact(Math.subtractExact(maxX, minX), 1),
                Math.addExact(Math.subtractExact(maxY, minY), 1)
        );
    }

    private static SFMHistoryCanvasLayout.Rect contentBounds(
            java.util.Collection<SFMHistoryCanvasLayout.Node> nodes,
            List<SFMHistoryCanvasLayout.Edge> edges,
            int padding
    ) {
        SFMHistoryCanvasLayout.Rect answer = null;
        for (SFMHistoryCanvasLayout.Node node : nodes) {
            answer = answer == null ? node.bounds() : answer.union(node.bounds());
        }
        for (SFMHistoryCanvasLayout.Edge edge : edges) {
            answer = answer == null ? edge.bounds() : answer.union(edge.bounds());
        }
        return answer == null ? new SFMHistoryCanvasLayout.Rect(0, 0, 0, 0) : answer.expand(padding);
    }

    private static int centerX(SFMHistoryCanvasLayout.Rect bounds) {
        return Math.addExact(bounds.x(), bounds.width() / 2);
    }

    private static int centerY(SFMHistoryCanvasLayout.Rect bounds) {
        return Math.addExact(bounds.y(), bounds.height() / 2);
    }

    private static int midpoint(int first, int second) {
        return (int) (((long) first + second) / 2L);
    }

    private static void requirePositive(int value, String label) {
        if (value <= 0) throw new IllegalArgumentException(label + " must be positive");
    }

    private static <T> T require(Map<String, T> values, String id, String label) {
        T answer = values.get(id);
        if (answer == null) throw new IllegalArgumentException("Unknown " + label + ": " + id);
        return answer;
    }

    private record RankLane(int rank, SFMHistoryCanvasLayout.Lane lane) implements Comparable<RankLane> {
        private RankLane {
            if (rank < 0) throw new IllegalArgumentException("rank must not be negative");
            Objects.requireNonNull(lane, "lane");
        }

        @Override
        public int compareTo(RankLane other) {
            int byRank = Integer.compare(rank, other.rank);
            return byRank != 0 ? byRank : Integer.compare(lane.ordinal(), other.lane.ordinal());
        }
    }

    private record WrappedLabel(SFMHistoryCanvasLayout.Label label, int width, int height) {
    }

    private record GraphAnalysis(
            Map<String, Integer> ranks,
            Map<String, SFMHistoryCanvasLayout.EdgePathKind> edgeKinds
    ) {
    }

    /** Iterative Kosaraju pass so the configured 4K-node bound never consumes the Java call stack. */
    private static final class StronglyConnectedComponents {
        private final Map<String, ? extends Set<String>> adjacency;

        private StronglyConnectedComponents(Map<String, ? extends Set<String>> adjacency) {
            this.adjacency = adjacency;
        }

        private List<List<String>> components() {
            ArrayList<String> finishOrder = new ArrayList<>(adjacency.size());
            HashSet<String> visited = new HashSet<>();
            for (String root : adjacency.keySet().stream().sorted().toList()) {
                if (!visited.add(root)) continue;
                ArrayDeque<TraversalFrame> stack = new ArrayDeque<>();
                stack.push(new TraversalFrame(root, sortedTargets(root)));
                while (!stack.isEmpty()) {
                    TraversalFrame frame = stack.peek();
                    Optional<String> next = frame.next();
                    if (next.isPresent()) {
                        if (visited.add(next.orElseThrow())) {
                            stack.push(new TraversalFrame(next.orElseThrow(), sortedTargets(next.orElseThrow())));
                        }
                    } else {
                        finishOrder.add(stack.pop().node());
                    }
                }
            }

            TreeMap<String, TreeSet<String>> reverse = new TreeMap<>();
            adjacency.keySet().forEach(node -> reverse.put(node, new TreeSet<>()));
            adjacency.forEach((source, targets) -> targets.forEach(target -> reverse.get(target).add(source)));

            visited.clear();
            ArrayList<List<String>> answer = new ArrayList<>();
            for (int index = finishOrder.size() - 1; index >= 0; index--) {
                String root = finishOrder.get(index);
                if (!visited.add(root)) continue;
                ArrayList<String> component = new ArrayList<>();
                ArrayDeque<String> stack = new ArrayDeque<>();
                stack.push(root);
                while (!stack.isEmpty()) {
                    String node = stack.pop();
                    component.add(node);
                    for (String target : reverse.get(node).descendingSet()) {
                        if (visited.add(target)) stack.push(target);
                    }
                }
                answer.add(List.copyOf(component));
            }
            return List.copyOf(answer);
        }

        private List<String> sortedTargets(String node) {
            Set<String> targets = adjacency.get(node);
            return targets == null ? List.of() : targets.stream().sorted().toList();
        }

        private static final class TraversalFrame {
            private final String node;
            private final List<String> targets;
            private int nextIndex;

            private TraversalFrame(String node, List<String> targets) {
                this.node = node;
                this.targets = targets;
            }

            private String node() {
                return node;
            }

            private Optional<String> next() {
                return nextIndex >= targets.size() ? Optional.empty() : Optional.of(targets.get(nextIndex++));
            }
        }
    }
}
