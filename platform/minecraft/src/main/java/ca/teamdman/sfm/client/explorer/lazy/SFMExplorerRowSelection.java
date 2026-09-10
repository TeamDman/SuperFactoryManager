package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;
import java.util.*;

/** Pure pointer/keyboard policy. Identity is canonical path; display order only defines ranges. */
public final class SFMExplorerRowSelection {
    public enum Gesture { REPLACE, TOGGLE, RANGE, TOGGLE_RANGE, CURSOR }
    public record Result(Set<SFMPath> members, Optional<SFMPath> anchor) {
        public Result { members = Set.copyOf(members); Objects.requireNonNull(anchor); }
    }
    private SFMExplorerRowSelection() { }

    public static Result apply(Set<SFMPath> previous, Optional<SFMPath> anchor, SFMPath endpoint,
                               List<SFMPath> order, Gesture gesture) {
        Objects.requireNonNull(endpoint);
        var next = new HashSet<>(previous);
        if (gesture == Gesture.CURSOR) return new Result(next, anchor);
        if (gesture == Gesture.REPLACE) return new Result(Set.of(endpoint), Optional.of(endpoint));
        if (gesture == Gesture.TOGGLE) {
            if (!next.add(endpoint)) next.remove(endpoint);
            return new Result(next, anchor.or(() -> Optional.of(endpoint)));
        }
        int end = order.indexOf(endpoint);
        if (end < 0) throw new IllegalArgumentException("Range endpoint is outside the displayed rows");
        int start = anchor.map(order::indexOf).orElse(-1);
        // Hidden/unloaded anchors do not imply an invisible range.
        Optional<SFMPath> nextAnchor = start < 0 ? Optional.of(endpoint) : anchor;
        if (start < 0) start = end;
        var range = order.subList(Math.min(start, end), Math.max(start, end) + 1);
        if (gesture == Gesture.RANGE) { next.clear(); next.addAll(range); }
        else if (previous.contains(endpoint)) next.removeAll(range);
        else next.addAll(range);
        return new Result(next, nextAnchor);
    }
}
