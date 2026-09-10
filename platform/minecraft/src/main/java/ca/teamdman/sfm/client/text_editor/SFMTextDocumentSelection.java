package ca.teamdman.sfm.client.text_editor;

import java.util.Objects;

/** One exact directional text selection: anchor is fixed and active is the caret end. */
public record SFMTextDocumentSelection(
        String id,
        SFMTextDocumentPosition anchor,
        SFMTextDocumentPosition active,
        boolean primary
) {
    public SFMTextDocumentSelection {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Selection id must not be blank");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(active, "active");
    }

    public boolean collapsed() {
        return anchor.byteOffset() == active.byteOffset();
    }

    public SFMTextDocumentRange orderedRange() {
        return anchor.byteOffset() <= active.byteOffset()
                ? new SFMTextDocumentRange(anchor, active)
                : new SFMTextDocumentRange(active, anchor);
    }

    public void validateAgainst(String text) {
        orderedRange().validateAgainst(text);
    }

    /** Validate many selections with one source index instead of one document scan per endpoint. */
    public static void validateAllAgainst(String text, java.util.List<SFMTextDocumentSelection> selections) {
        var index = new ca.teamdman.sfm.client.context.SFMTextCoordinateIndex(text);
        for (var selection : selections) {
            index.utf16(selection.anchor());
            index.utf16(selection.active());
        }
    }
}
