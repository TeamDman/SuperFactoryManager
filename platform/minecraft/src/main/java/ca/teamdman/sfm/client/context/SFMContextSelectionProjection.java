package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;

import java.util.List;
import java.util.Objects;

/** One independently named selection, including disjoint text ranges. */
public record SFMContextSelectionProjection(
        String id,
        List<SFMTextDocumentRange> ranges,
        boolean primary
) {
    public SFMContextSelectionProjection {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Selection id must not be blank");
        ranges = List.copyOf(ranges);
    }
}
