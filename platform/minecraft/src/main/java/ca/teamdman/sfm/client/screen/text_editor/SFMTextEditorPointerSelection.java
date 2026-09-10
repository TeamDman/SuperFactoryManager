package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasDocumentIndex;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;

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
        private final SFMTextCanvasGeometry geometry;

        private Layout(String coordinateText, SFMDrawCanvasDocumentIndex documentIndex, int spaceWidth, int lineHeight) {
            this.coordinateText = Objects.requireNonNull(coordinateText, "coordinateText");
            geometry = SFMTextCanvasGeometry.capture(coordinateText, documentIndex, spaceWidth, lineHeight);
        }
        public String coordinateText() { return coordinateText; }
        public SFMTextDocumentPosition positionAtCanvas(double canvasX, double canvasY) {
            return geometry.positionAtCanvas(canvasX, canvasY);
        }
    }
}
