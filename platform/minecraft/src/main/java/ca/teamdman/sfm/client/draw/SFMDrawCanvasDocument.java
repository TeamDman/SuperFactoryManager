package ca.teamdman.sfm.client.draw;

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
        int nextElementId,
        int nextGroupId,
        List<Element> elements
) {
    public static final int CURRENT_VERSION = 1;

    public SFMDrawCanvasDocument {
        activeLayer = Objects.requireNonNullElse(activeLayer, "ELEMENTS");
        elements = elements == null ? List.of() : List.copyOf(elements);
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
                1,
                1,
                List.of()
        );
    }

    public record Point(double x, double y) {
    }

    public record Element(
            String type,
            int id,
            String layer,
            boolean hidden,
            boolean locked,
            Integer commandSourceElementId,
            List<Integer> groupIds,
            Double x1,
            Double y1,
            Double x2,
            Double y2,
            Integer fillColor,
            Integer strokeColor,
            List<Point> points,
            List<Integer> hiddenAnchorIndexes,
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
            return new Element(
                    "rectangle",
                    id,
                    layer,
                    hidden,
                    locked,
                    commandSourceElementId,
                    groupIds,
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
                int color
        ) {
            return new Element(
                    "arrow",
                    id,
                    layer,
                    hidden,
                    locked,
                    commandSourceElementId,
                    groupIds,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    points,
                    hiddenAnchorIndexes,
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
            return new Element(
                    "text",
                    id,
                    layer,
                    hidden,
                    locked,
                    commandSourceElementId,
                    groupIds,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
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
            return new Element(
                    "freehand",
                    id,
                    layer,
                    hidden,
                    locked,
                    commandSourceElementId,
                    groupIds,
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
                    "",
                    color,
                    null
            );
        }
    }
}