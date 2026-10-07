package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One independently named selection, including disjoint text ranges.
 *
 * <p>{@code directionalSelections} retains the exact anchor/active ordering
 * needed by durable review witnesses. The three-argument constructor remains
 * available for older providers whose coordinate contract exposes only
 * ordered ranges; those ranges are projected as forward selections.</p>
 */
public record SFMContextSelectionProjection(
        String id,
        List<SFMTextDocumentRange> ranges,
        boolean primary,
        List<SFMTextDocumentSelection> directionalSelections
) {
    public SFMContextSelectionProjection {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Selection id must not be blank");
        ranges = List.copyOf(ranges);
        directionalSelections = List.copyOf(directionalSelections);
        if (ranges.size() != directionalSelections.size()) {
            throw new IllegalArgumentException("Directional selections must correspond one-to-one with ranges");
        }
        int primaryCount = 0;
        for (int index = 0; index < ranges.size(); index++) {
            SFMTextDocumentSelection directional = directionalSelections.get(index);
            if (!ranges.get(index).equals(directional.orderedRange())) {
                throw new IllegalArgumentException("Directional selection does not match its ordered range");
            }
            if (directional.primary()) primaryCount++;
        }
        if (primary && !ranges.isEmpty() && primaryCount != 1) {
            throw new IllegalArgumentException("A primary selection projection requires one primary range");
        }
        if (!primary && primaryCount != 0) {
            throw new IllegalArgumentException("A non-primary selection projection cannot contain a primary range");
        }
    }

    public SFMContextSelectionProjection(
            String id,
            List<SFMTextDocumentRange> ranges,
            boolean primary
    ) {
        this(id, ranges, primary, forwardSelections(id, ranges, primary));
    }

    private static List<SFMTextDocumentSelection> forwardSelections(
            String id,
            List<SFMTextDocumentRange> ranges,
            boolean primary
    ) {
        Objects.requireNonNull(ranges, "ranges");
        ArrayList<SFMTextDocumentSelection> answer = new ArrayList<>();
        for (int index = 0; index < ranges.size(); index++) {
            SFMTextDocumentRange range = ranges.get(index);
            answer.add(new SFMTextDocumentSelection(
                    ranges.size() == 1 ? id : id + "-range-" + index,
                    range.start(),
                    range.end(),
                    primary && index == 0
            ));
        }
        return answer;
    }
}
