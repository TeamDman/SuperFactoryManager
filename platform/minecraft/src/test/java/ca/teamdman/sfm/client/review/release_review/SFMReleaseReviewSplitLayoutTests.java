package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1.*;
import static ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1.SnapshotSide;
import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewSplitLayoutTests {
    @Test void refinedMappingsStayOnTheirOriginalSourceLine() {
        var layout = layout("@Override 😀old\r\nnext", "@Override 😀new\r\nnext", SurfaceKind.JAVA_STRUCTURED_DIFF, true);
        assertEquals(2, layout.rows().size());
        assertEquals("@Override 😀old", layout.rows().get(0).before().orElseThrow().displayText());
        assertEquals("@Override 😀new", layout.rows().get(0).after().orElseThrow().displayText());
        assertEquals("@Override 😀old\r\nnext", layout.selectAll(SnapshotSide.BEFORE).text());
        assertEquals("😀new", layout.select(SnapshotSide.AFTER, 0, 10, 0, 14).text());
    }
    @Test void keyboardMovementAndSelectAllStayOnExactSourceSide() {
        var layout = layout("A😀\r\nold_name:next\n", "new\nmore\nextra", SurfaceKind.TEXT_DIFF);
        var side = SnapshotSide.BEFORE;
        var origin = new SFMReleaseReviewSplitLayout.Position(0, 1);
        var right = layout.move(side, origin, SFMReleaseReviewSplitLayout.Motion.RIGHT, false).orElseThrow();
        assertEquals(new SFMReleaseReviewSplitLayout.Position(0, 2), right);
        assertEquals(new SFMReleaseReviewSplitLayout.Position(1, 0),
                layout.move(side, right, SFMReleaseReviewSplitLayout.Motion.RIGHT, false).orElseThrow());
        assertEquals(new SFMReleaseReviewSplitLayout.Position(1, 9), layout.move(side,
                new SFMReleaseReviewSplitLayout.Position(1, 0), SFMReleaseReviewSplitLayout.Motion.RIGHT, true).orElseThrow());
        assertEquals(new SFMReleaseReviewSplitLayout.Position(1, 13),
                layout.move(side, origin, SFMReleaseReviewSplitLayout.Motion.END, true).orElseThrow());
        assertEquals("A😀\r\nold_name:next\n", layout.selectAll(side).text());
        assertTrue(layout.selectAll(side).ranges().stream().allMatch(range -> range.documentRevisionId().equals("before")));
        assertTrue(layout(null, "new", SurfaceKind.TEXT_DIFF).move(side, null,
                SFMReleaseReviewSplitLayout.Motion.HOME, true).isEmpty());
    }
    @Test void columnsKeepIndependentUnicodeSourceRangesAndOriginalLineEndings() {
        for (SurfaceKind kind : SurfaceKind.values()) {
            var layout = layout("A😀\r\nold\n", "X\nnew\nextra", kind);
            assertEquals(3, layout.rows().size());
            assertEquals("A😀", layout.rows().get(0).before().orElseThrow().displayText());
            assertTrue(layout.rows().get(2).before().isEmpty());
            var selection = layout.select(SnapshotSide.BEFORE, 0, 1, 1, 2);
            assertEquals("😀\r\nol", selection.text());
            assertEquals(selection, layout.select(SnapshotSide.BEFORE, 1, 2, 0, 1));
            assertTrue(selection.ranges().stream().allMatch(range -> range.side() == SnapshotSide.BEFORE
                    && range.documentRevisionId().equals("before")));
            assertEquals(new Utf8Range(1, 7), selection.ranges().get(0).range());
            assertEquals("X\nne", layout.select(SnapshotSide.AFTER, 0, 0, 1, 2).text());
            assertEquals("old\n", layout.select(SnapshotSide.BEFORE, 1, 0, 2, 8).text());
        }
    }
    @Test void missingSidesHaveNoInventedSourceOrCommentTarget() {
        var added = layout(null, "new\n", SurfaceKind.TEXT_DIFF);
        assertTrue(added.rows().get(0).before().isEmpty());
        assertTrue(added.select(SnapshotSide.BEFORE, 0, 0, 0, 20).ranges().isEmpty());
        assertEquals("new", added.select(SnapshotSide.AFTER, 0, 0, 0, 20).text());
        var removed = layout("gone", null, SurfaceKind.JAVA_STRUCTURED_DIFF);
        assertTrue(removed.rows().get(0).after().isEmpty());
        assertEquals("gone", removed.select(SnapshotSide.BEFORE, 0, 0, 0, 20).text());
    }
    private static SFMReleaseReviewSplitLayout layout(String before, String after, SurfaceKind kind) {
        return layout(before, after, kind, false);
    }
    private static SFMReleaseReviewSplitLayout layout(String before, String after, SurfaceKind kind, boolean refine) {
        Optional<Source> left = Optional.ofNullable(before).map(text -> Source.fromCorpus("before", "a.java", "java", text));
        Optional<Source> right = Optional.ofNullable(after).map(text -> Source.fromCorpus("after", "a.java", "java", text));
        var operation = before == null ? SFMReleaseReviewV1.ChangeOperation.ADDED
                : after == null ? SFMReleaseReviewV1.ChangeOperation.DELETED : SFMReleaseReviewV1.ChangeOperation.MODIFIED;
        var pair = new FilePair("pair", "1.19.2", operation,
                List.of("unit"), left, right);
        var request = new Recipe(pair, kind).request(1, 1);
        StringBuilder text = new StringBuilder();
        var mappings = new ArrayList<Mapping>();
        for (SnapshotSide side : SnapshotSide.values()) pair.source(side).ifPresent(source -> {
            int start = text.toString().getBytes(StandardCharsets.UTF_8).length;
            text.append(source.text());
            int length = source.text().getBytes(StandardCharsets.UTF_8).length;
            if (refine) {
                int cursor = 0;
                for (int point : source.text().codePoints().toArray()) {
                    int size = new String(Character.toChars(point)).getBytes(StandardCharsets.UTF_8).length;
                    mappings.add(new Mapping(new Utf8Range(start + cursor, start + cursor + size),
                            cursor % 2 == 0 ? MappingKind.STRUCTURAL_CORRESPONDENCE
                                    : side == SnapshotSide.BEFORE ? MappingKind.STRUCTURAL_BEFORE : MappingKind.STRUCTURAL_AFTER,
                            List.of(new SourceRange(side, source.documentRevisionId(), source.sha256(), source.path(),
                                    new Utf8Range(cursor, cursor + size)))));
                    cursor += size;
                }
            } else if (length != 0) mappings.add(new Mapping(new Utf8Range(start, start + length),
                    side == SnapshotSide.BEFORE ? MappingKind.DELETION : MappingKind.ADDITION,
                    List.of(new SourceRange(side, source.documentRevisionId(), source.sha256(), source.path(),
                            new Utf8Range(0, length)))));
        });
        var surface = new Surface(SURFACE_SCHEMA, 1, 1, pair.id(), kind, "test", Outcome.PRODUCED, true,
                Optional.empty(), text.toString(), sha256(text.toString()), mappings, List.of(),
                new CorrespondenceReport(CORRESPONDENCE_SCHEMA, pair.id(), true, List.of(), List.of()), List.of());
        return SFMReleaseReviewSplitLayout.from(surface, request);
    }
}
