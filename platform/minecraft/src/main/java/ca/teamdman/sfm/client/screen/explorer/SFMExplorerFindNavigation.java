package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.*;

/** A query preview is not a selection. Only a generation-bound move commits a match. */
public final class SFMExplorerFindNavigation {
    public enum Outcome { IDLE, SEARCHING, PREVIEW, SELECTED, NO_MATCHES, INCOMPLETE, BOUNDARY, CANCELLED }
    public record Token(long query, long navigation) { }
    private record Move(int direction, boolean wrap) { }

    private long queryGeneration;
    private long navigationGeneration;
    private List<SFMPath> capturedVisible = List.of();
    private Optional<SFMPath> anchor = Optional.empty();
    private boolean anchorInclusive;
    private List<SFMPath> traversal = List.of();
    private Set<SFMPath> matches = Set.of();
    private Optional<SFMPath> preview = Optional.empty();
    private Optional<SFMPath> committed = Optional.empty();
    private Optional<SFMPath> pendingSelection = Optional.empty();
    private Move pendingMove;
    private boolean ready;
    private boolean complete;
    private Outcome outcome = Outcome.IDLE;

    /** Visible order, not set iteration order, decides which selected row is the anchor. */
    public void capture(List<SFMPath> visible, Set<SFMPath> selected) {
        capturedVisible = List.copyOf(visible);
        anchor = visible.stream().filter(selected::contains).findFirst();
        anchorInclusive = anchor.isEmpty();
        if (anchor.isEmpty()) anchor = visible.stream().findFirst();
        navigationGeneration++;
        reset();
    }

    /** Retains the capture while the user edits the query/options. */
    public Token beginQuery() {
        queryGeneration++;
        reset();
        outcome = Outcome.SEARCHING;
        return token();
    }

    public Token token() { return new Token(queryGeneration, navigationGeneration); }
    public Optional<SFMPath> preview() { return preview; }
    public Outcome outcome() { return outcome; }
    public boolean complete() { return ready && complete && outcome != Outcome.CANCELLED; }
    public List<SFMPath> orderedMatches() { return traversal.stream().filter(matches::contains).toList(); }

    /** Additive Find skips membership, not just the last navigation cursor. */
    public Outcome addNext(Set<SFMPath> alreadySelected) {
        if (!complete()) return Outcome.INCOMPLETE;
        var next = candidate(committed.isPresent() ? committed : anchor,
                committed.isEmpty() && anchorInclusive, 1, true, alreadySelected);
        if (next.isEmpty()) return Outcome.NO_MATCHES;
        preview = next;
        committed = next;
        pendingSelection = next;
        outcome = Outcome.SELECTED;
        return outcome;
    }

    public boolean publish(Token token, List<SFMPath> orderedDomain, Set<SFMPath> eligibleMatches,
                           boolean complete) {
        if (!token().equals(token) || outcome == Outcome.CANCELLED) return false;
        traversal = List.copyOf(new LinkedHashSet<>(orderedDomain));
        var known = new HashSet<>(traversal);
        var selected = new HashSet<>(eligibleMatches);
        selected.retainAll(known);
        matches = Set.copyOf(selected);
        ready = true;
        this.complete = complete;
        preview = candidate(anchor, anchorInclusive, 1, true);
        outcome = preview.isPresent() ? Outcome.PREVIEW : complete ? Outcome.NO_MATCHES : Outcome.INCOMPLETE;
        if (pendingMove != null) {
            var move = pendingMove;
            pendingMove = null;
            move(move.direction(), move.wrap());
        }
        return true;
    }

    public Outcome move(int direction, boolean wrap) {
        if (direction != 1 && direction != -1) throw new IllegalArgumentException("Find direction must be -1 or 1");
        if (outcome == Outcome.CANCELLED) return outcome;
        if (!ready) {
            // At most one intent; a query/navigation change clears it.
            pendingMove = new Move(direction, wrap);
            return Outcome.SEARCHING;
        }
        Optional<SFMPath> next = committed.isEmpty()
                ? candidate(anchor, anchorInclusive, direction, wrap)
                : candidate(committed, false, direction, wrap);
        if (next.isEmpty()) {
            outcome = !complete ? Outcome.INCOMPLETE : matches.isEmpty() ? Outcome.NO_MATCHES : Outcome.BOUNDARY;
            return outcome;
        }
        preview = next;
        committed = next;
        pendingSelection = next;
        outcome = Outcome.SELECTED;
        return outcome;
    }

    public Optional<SFMPath> takeSelection() {
        var answer = pendingSelection;
        pendingSelection = Optional.empty();
        return answer;
    }

    /** Stops late completions from moving the viewport after a human scroll/click/navigation. */
    public void userNavigated() {
        navigationGeneration++;
        reset();
        outcome = Outcome.CANCELLED;
    }

    public void clear() {
        queryGeneration++;
        reset();
    }

    private Optional<SFMPath> candidate(Optional<SFMPath> from, boolean inclusive, int direction, boolean wrap) {
        return candidate(from, inclusive, direction, wrap, Set.of());
    }

    private Optional<SFMPath> candidate(Optional<SFMPath> from, boolean inclusive, int direction, boolean wrap,
                                        Set<SFMPath> excluded) {
        if (traversal.isEmpty() || matches.isEmpty()) return Optional.empty();
        int index = from.map(traversal::indexOf).orElse(-1);
        if (index < 0) {
            // If the captured row disappeared, choose the first surviving viewport row.
            index = capturedVisible.stream().mapToInt(traversal::indexOf).filter(i -> i >= 0).findFirst().orElse(-1);
            inclusive = true;
        }
        if (index < 0) index = direction > 0 ? 0 : traversal.size() - 1;
        else if (!inclusive) index += direction;
        for (int checked = 0; checked < traversal.size(); checked++, index += direction) {
            if (index < 0 || index >= traversal.size()) {
                if (!wrap) return Optional.empty();
                index = Math.floorMod(index, traversal.size());
            }
            var path = traversal.get(index);
            if (matches.contains(path) && !excluded.contains(path)) return Optional.of(path);
        }
        return Optional.empty();
    }

    private void reset() {
        traversal = List.of();
        matches = Set.of();
        preview = Optional.empty();
        committed = Optional.empty();
        pendingSelection = Optional.empty();
        pendingMove = null;
        ready = false;
        complete = false;
        outcome = Outcome.IDLE;
    }
}
