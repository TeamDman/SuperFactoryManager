package ca.teamdman.sfm.client.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable spatial and textual projection of one canvas-model revision.
 *
 * <p>The editor reuses this object until text geometry changes. Queries visit
 * only rows intersecting the requested viewport instead of repeatedly sorting
 * or scanning the complete document.</p>
 */
public final class SFMDrawCanvasDocumentIndex {
    public record Viewport(double left, double top, double right, double bottom) {
        public Viewport {
            if (!Double.isFinite(left) || !Double.isFinite(top)
                    || !Double.isFinite(right) || !Double.isFinite(bottom)) {
                throw new IllegalArgumentException("Viewport coordinates must be finite");
            }
            if (right < left || bottom < top) {
                throw new IllegalArgumentException("Viewport bounds must be ordered");
            }
        }
    }

    public record VisibleSlice(
            List<SFMDrawCanvasModel.CanvasGlyph> glyphs,
            int rowsVisited,
            int glyphsVisited,
            int totalGlyphs
    ) {
        public VisibleSlice {
            glyphs = List.copyOf(glyphs);
            if (rowsVisited < 0 || glyphsVisited < 0 || totalGlyphs < 0) {
                throw new IllegalArgumentException("Visit counts must not be negative");
            }
        }
    }

    public record ContentBounds(double left, double top, double right, double bottom) {
        public ContentBounds {
            if (right < left || bottom < top) {
                throw new IllegalArgumentException("Content bounds must be ordered");
            }
        }
    }

    private record Row(double y, int maximumGlyphWidth, List<SFMDrawCanvasModel.CanvasGlyph> glyphs) {
        private Row {
            glyphs = List.copyOf(glyphs);
        }
    }

    private final int lineHeight;
    private final List<SFMDrawCanvasModel.CanvasGlyph> orderedGlyphs;
    private final List<Row> rows;
    private final SFMDrawCanvasSyntaxHighlightingHelper.CanvasDocumentProjection projection;
    private final Map<SFMDrawCanvasModel.CanvasGlyph, Integer> utf16Offsets;
    private final Map<SFMDrawCanvasModel.CanvasGlyph, Integer> glyphOrdinals;
    private final Optional<ContentBounds> bounds;

    private SFMDrawCanvasDocumentIndex(
            int lineHeight,
            List<SFMDrawCanvasModel.CanvasGlyph> orderedGlyphs,
            List<Row> rows,
            SFMDrawCanvasSyntaxHighlightingHelper.CanvasDocumentProjection projection,
            Map<SFMDrawCanvasModel.CanvasGlyph, Integer> utf16Offsets,
            Map<SFMDrawCanvasModel.CanvasGlyph, Integer> glyphOrdinals,
            Optional<ContentBounds> bounds
    ) {
        this.lineHeight = lineHeight;
        this.orderedGlyphs = List.copyOf(orderedGlyphs);
        this.rows = List.copyOf(rows);
        this.projection = projection;
        this.utf16Offsets = Collections.unmodifiableMap(new IdentityHashMap<>(utf16Offsets));
        this.glyphOrdinals = Collections.unmodifiableMap(new IdentityHashMap<>(glyphOrdinals));
        this.bounds = bounds;
    }

    public static SFMDrawCanvasDocumentIndex build(
            List<SFMDrawCanvasModel.CanvasGlyph> sourceGlyphs,
            int spaceWidth,
            int lineHeight
    ) {
        Objects.requireNonNull(sourceGlyphs, "sourceGlyphs");
        int safeSpaceWidth = Math.max(1, spaceWidth);
        int safeLineHeight = Math.max(1, lineHeight);
        ArrayList<SFMDrawCanvasModel.CanvasGlyph> ordered = new ArrayList<>(sourceGlyphs);
        ordered.sort(Comparator.comparingDouble(SFMDrawCanvasModel.CanvasGlyph::y)
                .thenComparingDouble(SFMDrawCanvasModel.CanvasGlyph::x));

        ArrayList<Row> rows = new ArrayList<>();
        ArrayList<SFMDrawCanvasModel.CanvasGlyph> currentRow = new ArrayList<>();
        Double currentY = null;
        int maximumWidth = 0;
        StringBuilder text = new StringBuilder();
        ArrayList<SFMDrawCanvasModel.CanvasGlyph> glyphsByCharIndex = new ArrayList<>();
        IdentityHashMap<SFMDrawCanvasModel.CanvasGlyph, Integer> offsets = new IdentityHashMap<>();
        IdentityHashMap<SFMDrawCanvasModel.CanvasGlyph, Integer> ordinals = new IdentityHashMap<>();
        double lineEndX = 0.0D;

        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (SFMDrawCanvasModel.CanvasGlyph glyph : ordered) {
            ordinals.put(glyph, ordinals.size());
            if (currentY == null || Double.compare(currentY, glyph.y()) != 0) {
                if (currentY != null) {
                    rows.add(new Row(currentY, maximumWidth, currentRow));
                    int newlineCount = Math.max(1, (int) Math.round((glyph.y() - currentY) / safeLineHeight));
                    for (int i = 0; i < newlineCount; i++) {
                        text.append('\n');
                        glyphsByCharIndex.add(null);
                    }
                }
                currentY = glyph.y();
                currentRow = new ArrayList<>();
                maximumWidth = 0;
                lineEndX = 0.0D;
            }
            currentRow.add(glyph);
            maximumWidth = Math.max(maximumWidth, Math.max(0, glyph.width()));
            int inferredSpaces = Math.max(0, (int) Math.floor((glyph.x() - lineEndX) / safeSpaceWidth));
            for (int i = 0; i < inferredSpaces; i++) {
                text.append(' ');
                glyphsByCharIndex.add(null);
            }
            offsets.put(glyph, text.length());
            text.append(glyph.text());
            for (int i = 0; i < glyph.text().length(); i++) glyphsByCharIndex.add(glyph);
            lineEndX = Math.max(lineEndX, glyph.x() + glyph.width());

            minimumX = Math.min(minimumX, glyph.x());
            minimumY = Math.min(minimumY, glyph.y());
            maximumX = Math.max(maximumX, glyph.x() + glyph.width());
            maximumY = Math.max(maximumY, glyph.y() + safeLineHeight);
        }
        if (currentY != null) rows.add(new Row(currentY, maximumWidth, currentRow));
        Optional<ContentBounds> bounds = ordered.isEmpty()
                ? Optional.empty()
                : Optional.of(new ContentBounds(minimumX, minimumY, maximumX, maximumY));
        return new SFMDrawCanvasDocumentIndex(
                safeLineHeight,
                ordered,
                rows,
                new SFMDrawCanvasSyntaxHighlightingHelper.CanvasDocumentProjection(
                        text.toString(),
                        Collections.unmodifiableList(new ArrayList<>(glyphsByCharIndex))
                ),
                offsets,
                ordinals,
                bounds
        );
    }

    public SFMDrawCanvasSyntaxHighlightingHelper.CanvasDocumentProjection projection() {
        return projection;
    }

    public List<SFMDrawCanvasModel.CanvasGlyph> orderedGlyphs() {
        return orderedGlyphs;
    }

    public Optional<ContentBounds> bounds() {
        return bounds;
    }

    public Optional<Integer> utf16OffsetOf(SFMDrawCanvasModel.CanvasGlyph glyph) {
        return Optional.ofNullable(utf16Offsets.get(glyph));
    }

    public Optional<Integer> glyphOrdinalOf(SFMDrawCanvasModel.CanvasGlyph glyph) {
        return Optional.ofNullable(glyphOrdinals.get(glyph));
    }

    /** Allocation-free hot-path lookup for rendering and pointer handling. */
    public int glyphOrdinalOrMinusOne(SFMDrawCanvasModel.CanvasGlyph glyph) {
        Integer ordinal = glyphOrdinals.get(glyph);
        return ordinal == null ? -1 : ordinal;
    }

    public VisibleSlice visible(Viewport viewport) {
        Objects.requireNonNull(viewport, "viewport");
        if (rows.isEmpty()) return new VisibleSlice(List.of(), 0, 0, 0);
        int rowIndex = firstPotentialRow(viewport.top());
        ArrayList<SFMDrawCanvasModel.CanvasGlyph> visible = new ArrayList<>();
        int rowsVisited = 0;
        int glyphsVisited = 0;
        for (int index = rowIndex; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (row.y() >= viewport.bottom()) break;
            rowsVisited++;
            List<SFMDrawCanvasModel.CanvasGlyph> glyphs = row.glyphs();
            int glyphIndex = firstPotentialGlyph(glyphs, viewport.left() - row.maximumGlyphWidth());
            for (int i = glyphIndex; i < glyphs.size(); i++) {
                SFMDrawCanvasModel.CanvasGlyph glyph = glyphs.get(i);
                if (glyph.x() >= viewport.right()) break;
                glyphsVisited++;
                if (glyph.x() + glyph.width() > viewport.left()
                        && glyph.y() + lineHeight > viewport.top()) {
                    visible.add(glyph);
                }
            }
        }
        return new VisibleSlice(visible, rowsVisited, glyphsVisited, orderedGlyphs.size());
    }

    public Optional<SFMDrawCanvasModel.CanvasGlyph> glyphAt(double x, double y) {
        VisibleSlice candidates = visible(new Viewport(x, y, Math.nextUp(x), Math.nextUp(y)));
        for (SFMDrawCanvasModel.CanvasGlyph glyph : candidates.glyphs()) {
            if (x >= glyph.x() && x < glyph.x() + glyph.width()
                    && y >= glyph.y() && y < glyph.y() + lineHeight) {
                return Optional.of(glyph);
            }
        }
        return Optional.empty();
    }

    public Optional<SFMDrawCanvasModel.CanvasGlyph> finalGlyphOnVisualRow(double y) {
        int index = firstPotentialRow(y);
        for (; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (row.y() > y) break;
            if (y >= row.y() && y < row.y() + lineHeight && !row.glyphs().isEmpty()) {
                return Optional.of(row.glyphs().get(row.glyphs().size() - 1));
            }
        }
        return Optional.empty();
    }

    /** Complete immutable row lookup, including zero-width Unicode glyphs. */
    public List<SFMDrawCanvasModel.CanvasGlyph> glyphsOnVisualRow(double y) {
        int index = firstPotentialRow(y);
        for (; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (row.y() > y) break;
            if (y >= row.y() && y < row.y() + lineHeight) return row.glyphs();
        }
        return List.of();
    }

    private int firstPotentialRow(double top) {
        int low = 0;
        int high = rows.size();
        double threshold = top - lineHeight;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (rows.get(middle).y() <= threshold) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static int firstPotentialGlyph(List<SFMDrawCanvasModel.CanvasGlyph> glyphs, double minimumX) {
        int low = 0;
        int high = glyphs.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (glyphs.get(middle).x() < minimumX) low = middle + 1;
            else high = middle;
        }
        return low;
    }
}
