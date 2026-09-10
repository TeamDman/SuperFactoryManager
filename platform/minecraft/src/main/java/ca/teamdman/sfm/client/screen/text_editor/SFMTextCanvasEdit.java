package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.context.SFMTextCoordinateIndex;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasDocumentIndex;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel.CanvasGlyph;
import ca.teamdman.sfm.client.search.SFMMatchBudget;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Draft-only, bounded multi-range replacement. No live model is changed before all checks pass. */
public final class SFMTextCanvasEdit {
    private SFMTextCanvasEdit() { }
    public record Result(String text, List<CanvasGlyph> glyphs, List<SFMTextDocumentSelection> selections) {
        public Result { glyphs = List.copyOf(glyphs); selections = List.copyOf(selections); }
    }
    private record Patch(int start, int end, SFMTextDocumentSelection selection) { }
    private record Glyph(int start, int end, CanvasGlyph value) { }

    public static Result replace(String text, SFMDrawCanvasDocumentIndex index,
                                 List<SFMTextDocumentSelection> selections, String replacement,
                                 SFMDrawCanvasModel.GlyphWidthReader widths, int spaceWidth, int lineHeight) {
        if (text.length() > 32768 || selections.size() > 4096 || replacement.length() > 32768)
            throw new IllegalArgumentException("Exact range edit exceeds the 32K text / 4096 selection limit");
        var budget = new SFMMatchBudget(1_000_000);
        if (selections.stream().filter(SFMTextDocumentSelection::primary).count() > 1
                || selections.stream().map(SFMTextDocumentSelection::id).distinct().count() != selections.size())
            throw new IllegalArgumentException("Selection IDs must be unique with at most one primary");
        var coordinates = new SFMTextCoordinateIndex(text);
        var geometry = SFMTextCanvasGeometry.capture(text, index, spaceWidth, lineHeight);
        var patches = new ArrayList<Patch>();
        for (var selection : selections) patches.add(new Patch(coordinates.utf16(selection.orderedRange().start()),
                coordinates.utf16(selection.orderedRange().end()), selection));
        patches.sort(Comparator.comparingInt(Patch::start).thenComparingInt(Patch::end));
        for (int i = 1; i < patches.size(); i++) {
            if (patches.get(i).start < patches.get(i - 1).end || patches.get(i).start == patches.get(i - 1).start)
                throw new IllegalArgumentException("Overlapping selections cannot be edited atomically");
        }
        StringBuilder expected = new StringBuilder();
        var caretOffsets = new ArrayList<Integer>();
        int copied = 0;
        for (Patch patch : patches) {
            expected.append(text, copied, patch.start).append(replacement);
            if (expected.length() > 32768) throw new IllegalArgumentException("Result exceeds the 32K exact range edit limit");
            caretOffsets.add(expected.length()); copied = patch.end;
        }
        expected.append(text, copied, text.length());
        if (expected.length() > 32768) throw new IllegalArgumentException("Result exceeds the 32K exact range edit limit");
        String resultText = expected.toString();
        var draft = new ArrayList<Glyph>();
        for (var span : geometry.glyphSpans()) draft.add(new Glyph(span.start(), span.end(), span.glyph()));
        // Descending source order leaves each earlier anchor unchanged. Later glyph geometry
        // moves by the exact seam delta; fractional row origins and unrelated gaps survive.
        for (int i = patches.size() - 1; i >= 0; i--) {
            Patch patch = patches.get(i);
            var start = geometry.pointAtUtf16(patch.start);
            var end = geometry.pointAtUtf16(patch.end);
            double x = start.x(), y = start.y();
            var inserted = new ArrayList<Glyph>();
            for (int offset = 0; offset < replacement.length();) {
                budget.spend(1);
                int point = replacement.codePointAt(offset); offset += Character.charCount(point);
                if (point == '\r' && offset < replacement.length() && replacement.charAt(offset) == '\n') offset++;
                if (point == '\r' || point == '\n') { x = 0; y += lineHeight; continue; }
                String value = new String(Character.toChars(point));
                int width = widths.width(value);
                inserted.add(new Glyph(patch.start, patch.start, new CanvasGlyph(value, x, y, width))); x += width;
            }
            var next = new ArrayList<Glyph>();
            for (Glyph token : draft) {
                budget.spend(1);
                if (token.start < patch.end && patch.start < token.end) {
                    if (token.start < patch.start || token.end > patch.end)
                        throw new IllegalArgumentException("Range cuts inside a composite canvas glyph; no edit applied");
                    continue;
                }
                CanvasGlyph glyph = token.value;
                if (token.start >= patch.end) {
                    if (Double.compare(glyph.y(), end.y()) == 0)
                        glyph = new CanvasGlyph(glyph.text(), x + glyph.x() - end.x(), y, glyph.width());
                    else if (glyph.y() > end.y())
                        glyph = new CanvasGlyph(glyph.text(), glyph.x(), glyph.y() + y - end.y(), glyph.width());
                }
                next.add(new Glyph(token.start, token.end, glyph));
            }
            next.addAll(inserted); draft = next;
        }
        var glyphs = draft.stream().map(Glyph::value).toList();
        var resultIndex = SFMDrawCanvasDocumentIndex.build(glyphs, spaceWidth, lineHeight);
        // Reconcile exact CRLF/empty terminal lines with the display projection. A spatial
        // collision or changed inferred whitespace is an error, never a partial mutation.
        SFMTextCanvasGeometry.capture(resultText, resultIndex, spaceWidth, lineHeight);
        var resultCoordinates = new SFMTextCoordinateIndex(resultText);
        var carets = new ArrayList<SFMTextDocumentSelection>();
        for (int i = 0; i < patches.size(); i++) {
            var point = resultCoordinates.atUtf16(caretOffsets.get(i));
            var source = patches.get(i).selection;
            carets.add(new SFMTextDocumentSelection(source.id(), point, point, source.primary()));
        }
        return new Result(resultText, glyphs, carets);
    }
}
