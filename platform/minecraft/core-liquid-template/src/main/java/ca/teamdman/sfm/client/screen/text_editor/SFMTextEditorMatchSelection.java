package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.search.SFMTextMatchOptions;
import ca.teamdman.sfm.client.search.SFMTextMatcher;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure bounded query -> exact selection transaction. Publishes nothing on failure. */
public final class SFMTextEditorMatchSelection {
    private SFMTextEditorMatchSelection() { }
    public record Result(String query, SFMTextMatchOptions options,
                         List<SFMTextDocumentSelection> selections, int matchCount, boolean approximate) {
        public Result { selections = List.copyOf(selections); }
    }

    public static Result select(String text, String query, SFMTextMatchOptions options,
                                List<SFMTextDocumentSelection> current, boolean all) {
        return select(text, query, options, current, all,
                new ca.teamdman.sfm.client.search.SFMMatchBudget(SFMTextMatcher.MAX_COMPARISONS));
    }
    public static Result select(String text, String query, SFMTextMatchOptions options,
                                List<SFMTextDocumentSelection> current, boolean all,
                                ca.teamdman.sfm.client.search.SFMMatchBudget budget) {
        budget.spend(0);
        if (text.length() > SFMTextMatcher.MAX_CANDIDATE_CODEPOINTS * 2
                || text.codePointCount(0, text.length()) > SFMTextMatcher.MAX_CANDIDATE_CODEPOINTS
                || current.size() > SFMTextMatcher.MAX_FRAGMENTS)
            throw new SFMTextMatcher.LimitExceeded("Document or selection exceeds bounded Find limits");
        var coordinates = new ca.teamdman.sfm.client.context.SFMTextCoordinateIndex(text);
        current.forEach(selection -> { coordinates.utf16(selection.anchor()); coordinates.utf16(selection.active()); });
        String prefix = "find-" + SFMContextTextCoordinates.sha256(text) + "-";
        if (query.isEmpty()) {
            var seed = current.stream().filter(SFMTextDocumentSelection::primary)
                    .filter(selection -> !selection.collapsed()).findFirst().orElseThrow(
                            () -> new IllegalArgumentException("Select text first, or set this editor's Find query"));
            var range = seed.orderedRange();
            var ends = SFMContextTextCoordinates.utf16OffsetsAtUtf8Bytes(text,
                    List.of(range.start().byteOffset(), range.end().byteOffset()));
            query = text.substring(ends.get(0), ends.get(1));
            options = options.withMode(SFMTextMatchOptions.Mode.LITERAL);
        }
        var matches = SFMTextMatcher.compile(query, options).match(text, budget);
        var found = new ArrayList<SFMTextDocumentSelection>();
        for (var fragment : matches.fragments()) {
            budget.spend(0);
            found.add(selection(coordinates, prefix, fragment.start(), fragment.end(), false));
        }
        for (int offset : matches.zeroWidthOffsets()) {
            budget.spend(0);
            found.add(selection(coordinates, prefix, offset, offset, false));
        }
        found.sort(Comparator.comparingInt(value -> value.orderedRange().start().byteOffset()));
        if (all) {
            if (found.isEmpty()) return new Result(query, options, current, 0, matches.approximate());
            found.set(0, primary(found.get(0), true));
            return new Result(query, options, found, found.size(), matches.approximate());
        }
        int after = current.stream().filter(SFMTextDocumentSelection::primary)
                .mapToInt(value -> value.active().byteOffset()).findFirst().orElse(0);
        var available = new ArrayList<SFMTextDocumentSelection>();
        for (var candidate : found) {
            boolean intersects = false;
            for (var selected : current) {
                budget.spend(1);
                if (intersects(selected, candidate)) { intersects = true; break; }
            }
            if (!intersects) available.add(candidate);
        }
        var next = available.stream().filter(value -> value.orderedRange().start().byteOffset() >= after)
                .findFirst().or(() -> available.stream().findFirst());
        if (next.isEmpty()) return new Result(query, options, current, found.size(), matches.approximate());
        if (current.size() >= SFMTextMatcher.MAX_FRAGMENTS)
            throw new SFMTextMatcher.LimitExceeded("Adding the next match exceeds the selection limit");
        var selected = new ArrayList<SFMTextDocumentSelection>();
        current.forEach(value -> selected.add(primary(value, false)));
        selected.add(primary(next.orElseThrow(), true));
        return new Result(query, options, selected, found.size(), matches.approximate());
    }

    private static SFMTextDocumentSelection selection(ca.teamdman.sfm.client.context.SFMTextCoordinateIndex coordinates,
                                                      String prefix, int start, int end, boolean primary) {
        return new SFMTextDocumentSelection(prefix + start + "-" + end,
                coordinates.atUtf16(start), coordinates.atUtf16(end), primary);
    }
    private static SFMTextDocumentSelection primary(SFMTextDocumentSelection selection, boolean primary) {
        return new SFMTextDocumentSelection(selection.id(), selection.anchor(), selection.active(), primary);
    }
    private static boolean intersects(SFMTextDocumentSelection left, SFMTextDocumentSelection right) {
        var a = left.orderedRange();
        var b = right.orderedRange();
        if (left.collapsed() || right.collapsed()) {
            return a.start().byteOffset() <= b.end().byteOffset() && b.start().byteOffset() <= a.end().byteOffset();
        }
        return a.start().byteOffset() < b.end().byteOffset() && b.start().byteOffset() < a.end().byteOffset();
    }
}
