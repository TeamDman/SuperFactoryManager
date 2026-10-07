package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasDocumentIndex;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel.CanvasGlyph;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Exact source-to-glyph adapter. Physical rectangles come from glyphs, never line-number cells. */
public final class SFMTextCanvasGeometry {
    public record Point(double x, double y) { }
    public record Rect(double left, double top, double right, double bottom) { }
    private record Line(int start, int end, double y) { }
    private final String text;
    private final CanvasGlyph[] glyphs;
    private final List<Line> lines;
    private final int spaceWidth;
    private final int lineHeight;
    private final ca.teamdman.sfm.client.context.SFMTextCoordinateIndex coordinates;

    public static SFMTextCanvasGeometry capture(String text, SFMDrawCanvasDocumentIndex index, int spaceWidth, int lineHeight) {
        return new SFMTextCanvasGeometry(text, index, spaceWidth, lineHeight);
    }
    private SFMTextCanvasGeometry(String text, SFMDrawCanvasDocumentIndex index, int spaceWidth, int lineHeight) {
        this.text = Objects.requireNonNull(text);
        this.coordinates = new ca.teamdman.sfm.client.context.SFMTextCoordinateIndex(text);
        this.spaceWidth = Math.max(1, spaceWidth);
        this.lineHeight = Math.max(1, lineHeight);
        this.glyphs = new CanvasGlyph[text.length()];
        String projected = index.projection().text();
        int p = 0;
        for (int s = 0; s < text.length();) {
            int point = text.codePointAt(s);
            int count = Character.charCount(point);
            if (point == '\r' || point == '\n') {
                if (point == '\r' && s + 1 < text.length() && text.charAt(s + 1) == '\n') count = 2;
                if (p < projected.length()) {
                    if (projected.charAt(p) == '\n') p++;
                    else if (p != 0) throw incompatible();
                }
            } else {
                if (p + count > projected.length() || !text.regionMatches(s, projected, p, count)) throw incompatible();
                for (int j = 0; j < count; j++) glyphs[s + j] = index.projection().glyphsByCharIndex().get(p + j);
                p += count;
            }
            s += count;
        }
        if (p != projected.length()) throw incompatible();
        var built = new ArrayList<Line>();
        int start = 0;
        double nextY = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\r' || text.charAt(i) == '\n') {
                double y = nextY;
                for (int j = start; j < i; j++) if (glyphs[j] != null) { y = glyphs[j].y(); break; }
                built.add(new Line(start, i, y));
                nextY = y + this.lineHeight;
                if (i < text.length() && text.charAt(i) == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                start = i + 1;
            }
        }
        lines = List.copyOf(built);
    }
    private static IllegalArgumentException incompatible() {
        return new IllegalArgumentException("Current glyph layout does not describe this exact document revision");
    }

    public record GlyphSpan(CanvasGlyph glyph, int start, int end) { }
    public List<GlyphSpan> glyphSpans() {
        var result = new ArrayList<GlyphSpan>();
        for (int i = 0; i < glyphs.length;) {
            CanvasGlyph glyph = glyphs[i];
            if (glyph == null) { i++; continue; }
            int end = i + 1;
            while (end < glyphs.length && glyphs[end] == glyph) end++;
            result.add(new GlyphSpan(glyph, i, end)); i = end;
        }
        return List.copyOf(result);
    }

    public Point point(SFMTextDocumentPosition position) {
        int offset = coordinates.utf16(position);
        return pointUnchecked(offset, position.line());
    }
    public Point pointAtUtf16(int offset) {
        var position = coordinates.atUtf16(offset);
        return pointUnchecked(offset, position.line());
    }
    private Point pointUnchecked(int offset, int lineNumber) {
        Line line = lines.get(lineNumber);
        if (offset < line.end && glyphs[offset] != null) {
            CanvasGlyph glyph = glyphs[offset];
            int first = offset;
            while (first > line.start && glyphs[first - 1] == glyph) first--;
            int scalars = glyph.text().codePointCount(0, glyph.text().length());
            double fraction = scalars == 0 ? 0 : (double) text.codePointCount(first, offset) / scalars;
            return new Point(glyph.x() + glyph.width() * fraction, glyph.y());
        }
        for (int i = Math.min(offset, line.end) - 1; i >= line.start; i--) {
            CanvasGlyph glyph = glyphs[i];
            if (glyph == null) continue;
            return new Point(glyph.x() + glyph.width() + text.codePointCount(i + 1, offset) * spaceWidth, glyph.y());
        }
        for (int i = offset; i < line.end; i++) {
            CanvasGlyph glyph = glyphs[i];
            if (glyph != null) return new Point(glyph.x() - text.codePointCount(offset, i) * spaceWidth, glyph.y());
        }
        return new Point(text.codePointCount(line.start, offset) * spaceWidth, line.y);
    }

    public List<Rect> rectangles(SFMTextDocumentRange range) {
        var offsets = List.of(coordinates.utf16(range.start()), coordinates.utf16(range.end()));
        Set<CanvasGlyph> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var result = new ArrayList<Rect>();
        int line = range.start().line();
        for (int i = offsets.get(0); i < offsets.get(1);) {
            CanvasGlyph glyph = glyphs[i];
            int point = text.codePointAt(i);
            int count = Character.charCount(point);
            if (point == '\r' || point == '\n') {
                if (point == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') count = 2;
                line++;
            } else if (glyph != null) {
                if (seen.add(glyph)) append(result, new Rect(glyph.x(), glyph.y(),
                        glyph.x() + Math.max(1, glyph.width()), glyph.y() + lineHeight));
            } else {
                Point start = pointUnchecked(i, line);
                Point end = pointUnchecked(i + count, line);
                append(result, new Rect(start.x(), start.y(), Math.max(start.x() + 1, end.x()), start.y() + lineHeight));
            }
            i += count;
        }
        return List.copyOf(result);
    }

    private static void append(ArrayList<Rect> result, Rect rectangle) {
        if (!result.isEmpty()) {
            Rect last = result.get(result.size() - 1);
            if (last.top == rectangle.top && last.bottom == rectangle.bottom && rectangle.left <= last.right + 0.001) {
                result.set(result.size() - 1, new Rect(Math.min(last.left, rectangle.left), last.top,
                        Math.max(last.right, rectangle.right), last.bottom));
                return;
            }
        }
        result.add(rectangle);
    }

    /** Nearest insertion boundary on the physical row, including fractional/moved glyph origins. */
    public SFMTextDocumentPosition positionAtCanvas(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Pointer coordinates must be finite");
        int chosen = 0;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            double dy = y < line.y ? line.y - y : Math.max(0, y - line.y - lineHeight);
            if (dy <= distance) { chosen = i; distance = dy; }
            if (y >= line.y && y < line.y + lineHeight) { chosen = i; break; }
        }
        Line line = lines.get(chosen);
        int offset = line.start;
        double dx = Double.POSITIVE_INFINITY;
        for (int i = line.start; i <= line.end;) {
            Point point = pointUnchecked(i, chosen);
            double candidate = Math.abs(x - point.x);
            if (candidate <= dx) { offset = i; dx = candidate; }
            if (i == line.end) break;
            i += Character.charCount(text.codePointAt(i));
        }
        return coordinates.atUtf16(offset);
    }
}
