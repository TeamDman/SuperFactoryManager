package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasDocumentIndex;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable pointer-to-document projection for one Text Editor V3 layout.
 *
 * <p>A layout is captured once on pointer press and reused for every drag
 * sample. This keeps drag cost local to the visual row instead of repeatedly
 * rescanning the complete document. The exact coordinate text is retained so
 * CRLF and UTF-8 byte witnesses remain lossless even though the canvas model
 * displays line endings as visual row changes.</p>
 */
public final class SFMTextEditorPointerSelection {
    private SFMTextEditorPointerSelection() {
    }

    public static Layout capture(
            String coordinateText,
            SFMDrawCanvasDocumentIndex documentIndex,
            int spaceWidth,
            int lineHeight
    ) {
        return new Layout(coordinateText, documentIndex, spaceWidth, lineHeight);
    }

    /** Right-click on either painted edge of a non-empty range retains it. */
    public static boolean containsNonEmptySelection(
            List<SFMTextDocumentSelection> selections,
            SFMTextDocumentPosition position
    ) {
        Objects.requireNonNull(selections, "selections");
        Objects.requireNonNull(position, "position");
        for (SFMTextDocumentSelection selection : selections) {
            Objects.requireNonNull(selection, "selections[]");
            if (selection.collapsed()) continue;
            var range = selection.orderedRange();
            if (position.byteOffset() >= range.start().byteOffset()
                    && position.byteOffset() <= range.end().byteOffset()) {
                return true;
            }
        }
        return false;
    }

    public static final class Layout {
        private final String coordinateText;
        private final SFMDrawCanvasDocumentIndex documentIndex;
        private final int spaceWidth;
        private final int lineHeight;
        private final List<Line> lines;

        private Layout(
                String coordinateText,
                SFMDrawCanvasDocumentIndex documentIndex,
                int spaceWidth,
                int lineHeight
        ) {
            this.coordinateText = Objects.requireNonNull(coordinateText, "coordinateText");
            this.documentIndex = Objects.requireNonNull(documentIndex, "documentIndex");
            this.spaceWidth = Math.max(1, spaceWidth);
            this.lineHeight = Math.max(1, lineHeight);
            this.lines = lines(coordinateText);
        }

        public String coordinateText() {
            return coordinateText;
        }

        public SFMTextDocumentPosition positionAtCanvas(double canvasX, double canvasY) {
            if (!Double.isFinite(canvasX) || !Double.isFinite(canvasY)) {
                throw new IllegalArgumentException("Pointer coordinates must be finite");
            }
            int lineIndex = clamp((int) Math.floor(canvasY / lineHeight), 0, lines.size() - 1);
            Line line = lines.get(lineIndex);
            if (line.scalarCount() == 0) return line.position(0);

            List<SFMDrawCanvasModel.CanvasGlyph> glyphs = glyphsOnLine(lineIndex);
            if (glyphs.isEmpty()) {
                return line.position(canvasX <= 0.0D ? 0 : line.scalarCount());
            }

            int column = 0;
            double lineEndX = 0.0D;
            for (SFMDrawCanvasModel.CanvasGlyph glyph : glyphs) {
                int inferredSpaces = Math.max(0, (int) Math.floor((glyph.x() - lineEndX) / spaceWidth));
                int glyphStartColumn = Math.min(line.scalarCount(), column + inferredSpaces);
                if (canvasX < glyph.x()) {
                    int gapColumn = nearestGapColumn(
                            canvasX,
                            lineEndX,
                            column,
                            glyphStartColumn
                    );
                    return line.position(gapColumn);
                }

                int glyphScalars = glyph.text().codePointCount(0, glyph.text().length());
                int glyphEndColumn = Math.min(line.scalarCount(), glyphStartColumn + glyphScalars);
                double glyphRight = glyph.x() + Math.max(0, glyph.width());
                if (canvasX < glyphRight || (glyph.width() <= 0 && Double.compare(canvasX, glyph.x()) == 0)) {
                    double midpoint = glyph.x() + Math.max(1, glyph.width()) / 2.0D;
                    return line.position(canvasX < midpoint ? glyphStartColumn : glyphEndColumn);
                }
                column = glyphEndColumn;
                lineEndX = Math.max(lineEndX, glyphRight);
            }
            return line.position(line.scalarCount());
        }

        private List<SFMDrawCanvasModel.CanvasGlyph> glyphsOnLine(int lineIndex) {
            double top = (double) lineIndex * lineHeight;
            return documentIndex.glyphsOnVisualRow(top);
        }

        private int nearestGapColumn(
                double canvasX,
                double gapLeft,
                int startColumn,
                int endColumn
        ) {
            if (endColumn <= startColumn || canvasX <= gapLeft) return startColumn;
            int steps = (int) Math.floor((canvasX - gapLeft) / spaceWidth + 0.5D);
            return clamp(startColumn + steps, startColumn, endColumn);
        }
    }

    private record Line(
            int number,
            int utf16Start,
            int utf16End,
            int utf8Start,
            int scalarCount,
            String source
    ) {
        private SFMTextDocumentPosition position(int requestedColumn) {
            int column = clamp(requestedColumn, 0, scalarCount);
            int utf16 = utf16Start;
            int bytes = utf8Start;
            for (int traversed = 0; traversed < column; traversed++) {
                int codePoint = source.codePointAt(utf16);
                utf16 += Character.charCount(codePoint);
                bytes += utf8Length(codePoint);
            }
            if (utf16 > utf16End) {
                throw new IllegalStateException("Pointer projection crossed a logical line ending");
            }
            return new SFMTextDocumentPosition(number, column, bytes);
        }
    }

    private static List<Line> lines(String source) {
        ArrayList<Line> answer = new ArrayList<>();
        int lineNumber = 0;
        int lineUtf16Start = 0;
        int lineUtf8Start = 0;
        int utf8Offset = 0;
        for (int utf16 = 0; utf16 < source.length();) {
            int codePoint = source.codePointAt(utf16);
            int charCount = Character.charCount(codePoint);
            if (codePoint == '\r' || codePoint == '\n') {
                int lineUtf16End = utf16;
                answer.add(new Line(
                        lineNumber++,
                        lineUtf16Start,
                        lineUtf16End,
                        lineUtf8Start,
                        source.codePointCount(lineUtf16Start, lineUtf16End),
                        source
                ));
                if (codePoint == '\r'
                        && utf16 + charCount < source.length()
                        && source.codePointAt(utf16 + charCount) == '\n') {
                    utf16 += charCount + 1;
                    utf8Offset += 2;
                } else {
                    utf16 += charCount;
                    utf8Offset += utf8Length(codePoint);
                }
                lineUtf16Start = utf16;
                lineUtf8Start = utf8Offset;
                continue;
            }
            utf16 += charCount;
            utf8Offset += utf8Length(codePoint);
        }
        answer.add(new Line(
                lineNumber,
                lineUtf16Start,
                source.length(),
                lineUtf8Start,
                source.codePointCount(lineUtf16Start, source.length()),
                source
        ));
        return List.copyOf(answer);
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) return 1;
        if (codePoint <= 0x7FF) return 2;
        if (codePoint <= 0xFFFF) return 3;
        return 4;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
