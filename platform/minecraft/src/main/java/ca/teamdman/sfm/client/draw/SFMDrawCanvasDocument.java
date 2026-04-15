package ca.teamdman.sfm.client.draw;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record SFMDrawCanvasDocument(
        int version,
        double cameraX,
        double cameraY,
        double zoom,
        String activeLayer,
        boolean elementsLayerMuted,
        boolean chromeLayerMuted,
        boolean historyLayerMuted,
        List<LayerOrigin> layerOrigins,
        int nextElementId,
        int nextGroupId,
        List<Element> elements,
        UndoTree undoTree
) {
    public static final int CURRENT_VERSION = 3;

    public SFMDrawCanvasDocument {
        activeLayer = Objects.requireNonNullElse(activeLayer, "ELEMENTS");
        layerOrigins = normalizeLayerOrigins(layerOrigins);
        elements = copyElements(elements);
        undoTree = undoTree == null
                ? UndoTree.blank(new SceneSnapshot(
                        cameraX,
                        cameraY,
                        zoom,
                        activeLayer,
                        elementsLayerMuted,
                        chromeLayerMuted,
                        historyLayerMuted,
                        layerOrigins,
                        nextElementId,
                        nextGroupId,
                        elements
                ))
                : undoTree;
    }

    public static SFMDrawCanvasDocument blank() {
        return new SFMDrawCanvasDocument(
                CURRENT_VERSION,
                0.0D,
                0.0D,
                1.0D,
                "ELEMENTS",
                false,
                false,
                true,
                defaultLayerOrigins(),
                1,
                1,
                List.of(),
                null
        );
    }

    public static SFMDrawCanvasDocument clipboard(
            String activeLayer,
            List<Element> elements
    ) {
        return new SFMDrawCanvasDocument(
                CURRENT_VERSION,
                0.0D,
                0.0D,
                1.0D,
                activeLayer,
                false,
                false,
                true,
                defaultLayerOrigins(),
                1,
                1,
                elements,
                null
        );
    }

    public record Point(
            double x,
            double y
    ) {
    }

    public record LayerOrigin(
            String layer,
            double x,
            double y
    ) {
        public LayerOrigin {
            layer = Objects.requireNonNullElse(layer, "ELEMENTS");
        }
    }

    public record EndpointBinding(
            int targetElementId,
            double focusX,
            double focusY
    ) {
    }

    public record SceneSnapshot(
            double cameraX,
            double cameraY,
            double zoom,
            String activeLayer,
            boolean elementsLayerMuted,
            boolean chromeLayerMuted,
            boolean historyLayerMuted,
            List<LayerOrigin> layerOrigins,
            int nextElementId,
            int nextGroupId,
            List<Element> elements
    ) {
        public SceneSnapshot {
            activeLayer = Objects.requireNonNullElse(activeLayer, "ELEMENTS");
            layerOrigins = normalizeLayerOrigins(layerOrigins);
            elements = copyElements(elements);
        }
    }

    public record UndoNode(
            int id,
            int parentId,
            String label,
            List<Integer> childIds,
            SceneSnapshot snapshot
    ) {
        public UndoNode {
            label = Objects.requireNonNullElse(label, "Action");
            childIds = childIds == null ? List.of() : List.copyOf(childIds);
            snapshot = snapshot == null ? blank().undoTree().nodes().get(0).snapshot() : snapshot;
        }
    }

    public record UndoTree(
            int nextNodeId,
            int currentNodeId,
            List<UndoNode> nodes
    ) {
        public UndoTree {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }

        public static UndoTree blank(SceneSnapshot rootSnapshot) {
            return new UndoTree(
                    1,
                    0,
                    List.of(new UndoNode(0, -1, "Root", List.of(), rootSnapshot))
            );
        }
    }

    public record Element(
            String type,
            int id,
            String layer,
            boolean hidden,
            boolean locked,
            Integer commandSourceElementId,
            List<Integer> groupIds,
            String name,
            Double x1,
            Double y1,
            Double x2,
            Double y2,
            Integer fillColor,
            Integer strokeColor,
            List<Point> points,
            List<Integer> hiddenAnchorIndexes,
            EndpointBinding startBinding,
            EndpointBinding endBinding,
            Double x,
            Double y,
            String text,
            Integer color,
            Double textScale
    ) {
        public Element {
            type = Objects.requireNonNullElse(type, "");
            layer = Objects.requireNonNullElse(layer, "ELEMENTS");
            commandSourceElementId = commandSourceElementId == null ? -1 : commandSourceElementId;
            groupIds = groupIds == null ? List.of() : List.copyOf(groupIds);
            name = Objects.requireNonNullElse(name, "");
            points = points == null ? List.of() : List.copyOf(points);
            hiddenAnchorIndexes = hiddenAnchorIndexes == null ? List.of() : List.copyOf(hiddenAnchorIndexes);
            text = Objects.requireNonNullElse(text, "");
        }

        public static Element rectangle(
                int id,
                String layer,
                boolean hidden,
                boolean locked,
                int commandSourceElementId,
                List<Integer> groupIds,
                double x1,
                double y1,
                double x2,
                double y2,
                int fillColor,
                int strokeColor
        ) {
            return rectangle(id, layer, hidden, locked, commandSourceElementId, groupIds, "", x1, y1, x2, y2, fillColor, strokeColor);
        }

        public static Element rectangle(
            int id,
            String layer,
            boolean hidden,
            boolean locked,
            int commandSourceElementId,
            List<Integer> groupIds,
            String name,
            double x1,
            double y1,
            double x2,
            double y2,
            int fillColor,
            int strokeColor
        ) {
            return new Element(
                    "rectangle",
                    id,
                    layer,
                    hidden,
                    locked,
                    commandSourceElementId,
                    groupIds,
                name,
                    x1,
                    y1,
                    x2,
                    y2,
                    fillColor,
                    strokeColor,
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    "",
                    null,
                    null
            );
        }

        public static Element arrow(
                int id,
                String layer,
                boolean hidden,
                boolean locked,
                int commandSourceElementId,
                List<Integer> groupIds,
                List<Point> points,
                List<Integer> hiddenAnchorIndexes,
                int color,
                EndpointBinding startBinding,
                EndpointBinding endBinding
        ) {
                return arrow(id, layer, hidden, locked, commandSourceElementId, groupIds, "", points, hiddenAnchorIndexes, color, startBinding, endBinding);
            }

            public static Element arrow(
                int id,
                String layer,
                boolean hidden,
                boolean locked,
                int commandSourceElementId,
                List<Integer> groupIds,
                String name,
                List<Point> points,
                List<Integer> hiddenAnchorIndexes,
                int color,
                EndpointBinding startBinding,
                EndpointBinding endBinding
            ) {
            return new Element(
                    "arrow",
                    id,
                    layer,
                    hidden,
                    locked,
                    commandSourceElementId,
                    groupIds,
                    name,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    points,
                    hiddenAnchorIndexes,
                    startBinding,
                    endBinding,
                    null,
                    null,
                    "",
                    color,
                    null
            );
        }

        public static Element text(
                int id,
                String layer,
                boolean hidden,
                boolean locked,
                int commandSourceElementId,
                List<Integer> groupIds,
                double x,
                double y,
                String text,
                int color,
                double textScale
        ) {
                return text(id, layer, hidden, locked, commandSourceElementId, groupIds, "", x, y, text, color, textScale);
            }

            public static Element text(
                int id,
                String layer,
                boolean hidden,
                boolean locked,
                int commandSourceElementId,
                List<Integer> groupIds,
                String name,
                double x,
                double y,
                String text,
                int color,
                double textScale
            ) {
            return new Element(
                    "text",
                    id,
                    layer,
                    hidden,
                    locked,
                    commandSourceElementId,
                    groupIds,
                    name,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    null,
                    null,
                    x,
                    y,
                    text,
                    color,
                    textScale
            );
        }

        public static Element freehand(
                int id,
                String layer,
                boolean hidden,
                boolean locked,
                int commandSourceElementId,
                List<Integer> groupIds,
                List<Point> points,
                int color
        ) {
                return freehand(id, layer, hidden, locked, commandSourceElementId, groupIds, "", points, color);
            }

            public static Element freehand(
                int id,
                String layer,
                boolean hidden,
                boolean locked,
                int commandSourceElementId,
                List<Integer> groupIds,
                String name,
                List<Point> points,
                int color
            ) {
            return new Element(
                    "freehand",
                    id,
                    layer,
                    hidden,
                    locked,
                    commandSourceElementId,
                    groupIds,
                    name,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    points,
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    "",
                    color,
                    null
            );
        }
    }

    private static List<Element> copyElements(List<Element> elements) {
        return elements == null ? List.of() : List.copyOf(elements);
    }

    private static List<LayerOrigin> defaultLayerOrigins() {
        return List.of(
                new LayerOrigin("ELEMENTS", 0.0D, 0.0D),
                new LayerOrigin("CHROME", 0.0D, 0.0D),
                new LayerOrigin("HISTORY", 0.0D, 0.0D)
        );
    }

    private static List<LayerOrigin> normalizeLayerOrigins(List<LayerOrigin> layerOrigins) {
        List<LayerOrigin> normalized = new ArrayList<>();
        List<LayerOrigin> provided = layerOrigins == null ? List.of() : layerOrigins;
        for (LayerOrigin defaultOrigin : defaultLayerOrigins()) {
            LayerOrigin match = null;
            for (LayerOrigin candidate : provided) {
                if (defaultOrigin.layer().equals(candidate.layer())) {
                    match = candidate;
                    break;
                }
            }
            normalized.add(match == null ? defaultOrigin : match);
        }
        for (LayerOrigin candidate : provided) {
            boolean alreadyPresent = false;
            for (LayerOrigin existing : normalized) {
                if (existing.layer().equals(candidate.layer())) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }
}