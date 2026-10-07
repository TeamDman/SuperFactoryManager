package ca.teamdman.sfm.client.history.canvas;

import java.util.Objects;
import java.util.Optional;

/** Stable-id hover and selection state retained across pushed layout revisions. */
public record SFMHistoryCanvasInteractionState(
        Optional<SFMHistoryCanvasSpatialIndex.Subject> hovered,
        Optional<SFMHistoryCanvasSpatialIndex.Subject> selected
) {
    public SFMHistoryCanvasInteractionState {
        hovered = Objects.requireNonNull(hovered, "hovered");
        selected = Objects.requireNonNull(selected, "selected");
    }

    public static SFMHistoryCanvasInteractionState empty() {
        return new SFMHistoryCanvasInteractionState(Optional.empty(), Optional.empty());
    }

    public SFMHistoryCanvasInteractionState hover(Optional<SFMHistoryCanvasSpatialIndex.Hit> hit) {
        Objects.requireNonNull(hit, "hit");
        return new SFMHistoryCanvasInteractionState(hit.map(SFMHistoryCanvasSpatialIndex.Hit::subject), selected);
    }

    public SFMHistoryCanvasInteractionState select(Optional<SFMHistoryCanvasSpatialIndex.Hit> hit) {
        Objects.requireNonNull(hit, "hit");
        return new SFMHistoryCanvasInteractionState(hovered, hit.map(SFMHistoryCanvasSpatialIndex.Hit::subject));
    }

    public SFMHistoryCanvasInteractionState select(SFMHistoryCanvasSpatialIndex.Subject subject) {
        return new SFMHistoryCanvasInteractionState(hovered, Optional.of(Objects.requireNonNull(subject, "subject")));
    }

    public SFMHistoryCanvasInteractionState clearHover() {
        return new SFMHistoryCanvasInteractionState(Optional.empty(), selected);
    }

    public SFMHistoryCanvasInteractionState clearSelection() {
        return new SFMHistoryCanvasInteractionState(hovered, Optional.empty());
    }

    /**
     * Retains subjects still present in a pushed revision and drops only those
     * whose stable source identity disappeared.
     */
    public SFMHistoryCanvasInteractionState retain(SFMHistoryCanvasSpatialIndex nextIndex) {
        Objects.requireNonNull(nextIndex, "nextIndex");
        return new SFMHistoryCanvasInteractionState(
                hovered.filter(nextIndex::contains),
                selected.filter(nextIndex::contains)
        );
    }
}
