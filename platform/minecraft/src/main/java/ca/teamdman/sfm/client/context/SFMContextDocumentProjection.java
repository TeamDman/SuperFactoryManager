package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Exact immutable editor document projection.
 *
 * <p>The baseline retains resolver provenance. {@code currentText} is a
 * separately hashed request overlay and may differ only when {@code dirty} is
 * explicit.</p>
 */
public record SFMContextDocumentProjection(
        String editorId,
        SFMTextDocumentSnapshot baseline,
        String currentText,
        String currentSha256,
        boolean dirty,
        boolean readOnly,
        List<SFMContextCursorProjection> cursors,
        List<SFMContextSelectionProjection> selections
) implements SFMContextProjection {
    private static final String COMPUTE_CURRENT_SHA256 = "\u0000compute-current-sha256";

    public SFMContextDocumentProjection {
        Objects.requireNonNull(editorId, "editorId");
        if (editorId.isBlank()) throw new IllegalArgumentException("Editor id must not be blank");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(currentText, "currentText");
        Objects.requireNonNull(currentSha256, "currentSha256");
        cursors = List.copyOf(cursors);
        selections = List.copyOf(selections);

        String actualHash = SFMContextTextCoordinates.sha256(currentText);
        if (COMPUTE_CURRENT_SHA256.equals(currentSha256)) {
            currentSha256 = actualHash;
        } else if (!actualHash.equals(currentSha256)) {
            throw new IllegalArgumentException("Current document hash does not match current text");
        }
        if (!dirty && baseline.ready()) {
            if (!baseline.text().equals(currentText)) {
                throw new IllegalArgumentException("A clean projection must preserve exact baseline text");
            }
            if (baseline.sha256().isPresent()
                    && !baseline.sha256().orElseThrow().equals(currentSha256)) {
                throw new IllegalArgumentException("A clean projection must preserve the baseline hash");
            }
        }
        HashSet<String> cursorIds = new HashSet<>();
        long primaryCursors = 0;
        for (SFMContextCursorProjection cursor : cursors) {
            if (!cursorIds.add(cursor.id())) throw new IllegalArgumentException("Duplicate cursor id " + cursor.id());
            if (cursor.primary()) primaryCursors++;
            validatePosition(cursor.position(), currentText);
        }
        if (!cursors.isEmpty() && primaryCursors != 1) {
            throw new IllegalArgumentException("A document with cursors requires exactly one primary cursor");
        }

        HashSet<String> selectionIds = new HashSet<>();
        long primarySelections = 0;
        for (SFMContextSelectionProjection selection : selections) {
            if (!selectionIds.add(selection.id())) {
                throw new IllegalArgumentException("Duplicate selection id " + selection.id());
            }
            if (selection.primary()) primarySelections++;
            for (SFMTextDocumentRange range : selection.ranges()) range.validateAgainst(currentText);
        }
        if (primarySelections > 1) {
            throw new IllegalArgumentException("A document can have at most one primary selection");
        }
    }

    public static SFMContextDocumentProjection capture(
            String editorId,
            SFMTextDocumentSnapshot baseline,
            String currentText,
            boolean dirty,
            boolean readOnly,
            List<SFMContextCursorProjection> cursors,
            List<SFMContextSelectionProjection> selections
    ) {
        return new SFMContextDocumentProjection(
                editorId,
                baseline,
                currentText,
                COMPUTE_CURRENT_SHA256,
                dirty,
                readOnly,
                cursors,
                selections
        );
    }

    private static void validatePosition(SFMContextPosition position, String text) {
        if (position instanceof SFMContextPosition.Text textPosition) {
            validateTextPosition(textPosition.position(), text);
        } else if (position instanceof SFMContextPosition.Canvas canvas) {
            canvas.textHit().ifPresent(value -> validateTextPosition(value, text));
        }
    }

    private static void validateTextPosition(
            ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition position,
            String text
    ) {
        ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition actual =
                SFMTextDocumentRange.positionAtByteOffset(text, position.byteOffset());
        if (!actual.equals(position)) {
            throw new IllegalArgumentException("Cursor position does not match current text");
        }
    }
}
