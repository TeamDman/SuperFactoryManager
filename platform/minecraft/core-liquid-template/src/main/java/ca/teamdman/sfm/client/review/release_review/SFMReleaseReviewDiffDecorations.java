package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentDecoration;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import java.util.*;

/** Pure linear text scan shared by immutable review and inspection presentations. */
public final class SFMReleaseReviewDiffDecorations {
    private SFMReleaseReviewDiffDecorations() {}
    public static List<SFMTextDocumentDecoration> create(SFMReleaseReviewSurfaceV1.Surface surface) {
        var changed = surface.mappings().stream().filter(m -> colour(m.kind()) != null).toList();
        var offsets = new TreeSet<Integer>();
        changed.forEach(m -> { offsets.add(m.surfaceRange().startByte()); offsets.add(m.surfaceRange().endByte()); });
        var positions = positions(surface.text(), offsets);
        var answer = new ArrayList<SFMTextDocumentDecoration>(changed.size());
        for (var mapping : changed) {
            int start = mapping.surfaceRange().startByte(), end = mapping.surfaceRange().endByte();
            boolean added = mapping.kind() == SFMReleaseReviewSurfaceV1.MappingKind.ADDITION
                    || mapping.kind() == SFMReleaseReviewSurfaceV1.MappingKind.STRUCTURAL_AFTER;
            answer.add(new SFMTextDocumentDecoration("diff:" + start,
                    new SFMTextDocumentRange(positions.get(start), positions.get(end)),
                    OptionalInt.of(colour(mapping.kind())), OptionalInt.empty(), Optional.empty(),
                    added ? "Added source" : "Removed source", Optional.empty()));
        }
        return List.copyOf(answer);
    }
    private static Integer colour(SFMReleaseReviewSurfaceV1.MappingKind kind) {
        return switch (kind) {
            case ADDITION, STRUCTURAL_AFTER -> 0x704FAF69;
            case DELETION, STRUCTURAL_BEFORE -> 0x70D66B6B;
            default -> null;
        };
    }
    static Map<Integer, SFMTextDocumentPosition> positions(String text, SortedSet<Integer> requested) {
        var answer = new HashMap<Integer, SFMTextDocumentPosition>();
        var offsets = requested.iterator();
        Integer next = offsets.hasNext() ? offsets.next() : null;
        int line = 0, column = 0, bytes = 0;
        for (int index = 0; next != null;) {
            if (next < bytes) throw new IllegalArgumentException("Diff coordinate splits a Unicode scalar or CRLF");
            if (next == bytes) {
                answer.put(next, new SFMTextDocumentPosition(line, column, bytes));
                next = offsets.hasNext() ? offsets.next() : null;
                continue;
            }
            if (index == text.length()) throw new IllegalArgumentException("Diff coordinate exceeds text");
            int point = text.codePointAt(index), chars = Character.charCount(point);
            if (point >= 0xD800 && point <= 0xDFFF) throw new IllegalArgumentException("Unpaired surrogate in diff text");
            bytes += point < 0x80 ? 1 : point < 0x800 ? 2 : point < 0x10000 ? 3 : 4;
            index += chars;
            if (point == '\r') {
                if (index < text.length() && text.charAt(index) == '\n') { index++; bytes++; }
                line++; column = 0;
            } else if (point == '\n') { line++; column = 0; }
            else column++;
        }
        return Map.copyOf(answer);
    }
}
