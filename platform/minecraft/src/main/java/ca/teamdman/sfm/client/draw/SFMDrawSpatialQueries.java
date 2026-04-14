package ca.teamdman.sfm.client.draw;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SFMDrawSpatialQueries {
    private static final double LINE_EPSILON = 0.0001D;

    private SFMDrawSpatialQueries() {
    }

    public static String concatenateFromRectPoint(
            List<RectangleRegion> rectangles,
            List<TextSurface> textSurfaces,
            TextMetrics textMetrics,
            double x,
            double y
    ) {
        List<CanvasRegion> selectedRegions = regionsContainingPoint(rectangles, x, y);
        if (selectedRegions.isEmpty()) {
            return "";
        }

        List<CapturedGlyph> capturedGlyphs = new ArrayList<>();
        int glyphOrder = 0;
        for (TextSurface textSurface : textSurfaces) {
            String[] lines = splitLines(textSurface.text());
            double scaledLineHeight = textMetrics.lineHeight() * textSurface.textScale();
            for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                String line = lines[lineIndex];
                double lineTop = textSurface.y() + lineIndex * scaledLineHeight;
                double lineBottom = lineTop + scaledLineHeight;
                for (int charIndex = 0; charIndex < line.length(); charIndex++) {
                    double glyphLeft = textSurface.x() + textMetrics.width(line.substring(0, charIndex)) * textSurface.textScale();
                    double glyphRight = textSurface.x() + textMetrics.width(line.substring(0, charIndex + 1)) * textSurface.textScale();
                    if (glyphRight <= glyphLeft) {
                        glyphRight = glyphLeft + Math.max(1.0D, textSurface.textScale());
                    }
                    CanvasRegion glyphRegion = CanvasRegion.of(glyphLeft, lineTop, glyphRight, lineBottom);
                    if (intersectsAny(selectedRegions, glyphRegion)) {
                        capturedGlyphs.add(new CapturedGlyph(line.charAt(charIndex), glyphLeft, lineTop, lineBottom, glyphOrder++));
                    }
                }
            }
        }

        capturedGlyphs.sort(Comparator
                .comparingDouble(CapturedGlyph::top)
                .thenComparingDouble(CapturedGlyph::left)
                .thenComparingInt(CapturedGlyph::order));

        StringBuilder result = new StringBuilder();
        Double activeLineTop = null;
        double activeLineBottom = Double.NEGATIVE_INFINITY;
        for (CapturedGlyph capturedGlyph : capturedGlyphs) {
            if (activeLineTop != null && capturedGlyph.top() > activeLineBottom - LINE_EPSILON && Math.abs(capturedGlyph.top() - activeLineTop) > LINE_EPSILON) {
                result.append('\n');
            }
            result.append(capturedGlyph.character());
            if (activeLineTop == null || capturedGlyph.top() > activeLineBottom - LINE_EPSILON) {
                activeLineTop = capturedGlyph.top();
                activeLineBottom = capturedGlyph.bottom();
            } else {
                activeLineBottom = Math.max(activeLineBottom, capturedGlyph.bottom());
            }
        }
        return result.toString();
    }

    public static List<CanvasRegion> regionsContainingPoint(
            List<RectangleRegion> rectangles,
            double x,
            double y
    ) {
        List<CanvasRegion> regions = new ArrayList<>();
        for (RectangleRegion rectangle : rectangles) {
            CanvasRegion region = CanvasRegion.of(rectangle.x1(), rectangle.y1(), rectangle.x2(), rectangle.y2());
            if (region.contains(x, y)) {
                regions.add(region);
            }
        }
        return regions;
    }

    private static boolean intersectsAny(
            List<CanvasRegion> regions,
            CanvasRegion candidate
    ) {
        for (CanvasRegion region : regions) {
            if (region.intersects(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String[] splitLines(String text) {
        return text == null || text.isEmpty() ? new String[]{""} : text.split("\\n", -1);
    }

    public interface TextMetrics {
        int lineHeight();

        int width(String text);
    }

    public record RectangleRegion(
            double x1,
            double y1,
            double x2,
            double y2
    ) {
    }

    public record TextSurface(
            double x,
            double y,
            String text,
            double textScale
    ) {
    }

    public record CanvasRegion(
            double minX,
            double minY,
            double maxX,
            double maxY
    ) {
        public static CanvasRegion of(
                double x1,
                double y1,
                double x2,
                double y2
        ) {
            return new CanvasRegion(
                    Math.min(x1, x2),
                    Math.min(y1, y2),
                    Math.max(x1, x2),
                    Math.max(y1, y2)
            );
        }

        public boolean contains(
                double x,
                double y
        ) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }

        public boolean intersects(CanvasRegion other) {
            return maxX > other.minX && minX < other.maxX && maxY > other.minY && minY < other.maxY;
        }
    }

    private record CapturedGlyph(
            char character,
            double left,
            double top,
            double bottom,
            int order
    ) {
    }
}