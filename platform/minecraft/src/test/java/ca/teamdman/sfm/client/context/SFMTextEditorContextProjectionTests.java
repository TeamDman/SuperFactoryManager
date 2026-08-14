package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTextEditorContextProjectionTests {
    private static final String UNICODE_CRLF = "α\r\nβ😀 value\r\n";

    @Test
    void unicodeScalarAndCrLfCoordinatesProduceExactUtf8Offsets() {
        assertEquals(
                new SFMTextDocumentPosition(0, 1, 2),
                SFMContextTextCoordinates.atLineColumn(UNICODE_CRLF, 0, 1)
        );
        assertEquals(
                new SFMTextDocumentPosition(1, 2, 10),
                SFMContextTextCoordinates.atLineColumn(UNICODE_CRLF, 1, 2)
        );
        assertEquals(
                new SFMTextDocumentPosition(2, 0, 18),
                SFMContextTextCoordinates.atLineColumn(UNICODE_CRLF, 2, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMContextTextCoordinates.atUtf16Offset(UNICODE_CRLF, 2)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMContextTextCoordinates.atUtf16Offset(UNICODE_CRLF, 5)
        );
    }

    @Test
    void canvasWhitespaceKeepsTrueCoordinatesWithoutInventingATextHit() {
        SFMContextCanvasTextMap map = new SFMContextCanvasTextMap(List.of(
                new SFMContextCanvasTextMap.HitRegion(
                        10, 20, 6, 9,
                        SFMContextTextCoordinates.atLineColumn("ab", 0, 0)
                ),
                new SFMContextCanvasTextMap.HitRegion(
                        20, 20, 6, 9,
                        SFMContextTextCoordinates.atLineColumn("ab", 0, 1)
                )
        ));

        SFMContextPosition.Canvas glyph = map.canvasPosition(12, 24);
        SFMContextPosition.Canvas whitespace = map.canvasPosition(18, 24);

        assertEquals(12, glyph.x());
        assertEquals(Optional.of(new SFMTextDocumentPosition(0, 0, 0)), glyph.textHit());
        assertEquals(18, whitespace.x());
        assertTrue(whitespace.textHit().isEmpty());
    }

    @Test
    void documentPreservesMultipleCursorsSelectionsAndDeepImmutability() {
        String text = "one two\n";
        SFMTextDocumentSnapshot baseline = SFMTextDocumentSnapshot.literal(text);
        ArrayList<SFMContextCursorProjection> cursors = new ArrayList<>(List.of(
                new SFMContextCursorProjection(
                        "primary",
                        new SFMContextPosition.Canvas(
                                3,
                                4,
                                Optional.of(SFMContextTextCoordinates.atLineColumn(text, 0, 0))
                        ),
                        true,
                        true
                ),
                new SFMContextCursorProjection(
                        "supplemental-1",
                        new SFMContextPosition.Canvas(40, 4, Optional.empty()),
                        false,
                        true
                )
        ));
        ArrayList<SFMTextDocumentRange> ranges = new ArrayList<>(List.of(
                new SFMTextDocumentRange(
                        SFMContextTextCoordinates.atLineColumn(text, 0, 0),
                        SFMContextTextCoordinates.atLineColumn(text, 0, 3)
                )
        ));
        SFMContextSelectionProjection selection =
                new SFMContextSelectionProjection("selection-1", ranges, true);
        SFMContextDocumentProjection projection = SFMContextDocumentProjection.capture(
                "sfm:text_editor_v3",
                baseline,
                text,
                false,
                false,
                cursors,
                List.of(selection)
        );
        cursors.clear();
        ranges.clear();

        assertEquals(2, projection.cursors().size());
        assertEquals(1, projection.selections().get(0).ranges().size());
        assertThrows(UnsupportedOperationException.class, () -> projection.cursors().clear());
        assertThrows(UnsupportedOperationException.class, () -> projection.selections().get(0).ranges().clear());
    }

    @Test
    void dirtyAndReadOnlyStateRemainExplicitAndCleanStateRequiresExactBaseline() {
        SFMTextDocumentSnapshot editable = SFMTextDocumentSnapshot.literal("before\r\n");
        SFMContextDocumentProjection dirty = SFMContextDocumentProjection.capture(
                "sfm:text_editor_v3",
                editable,
                "after\n",
                true,
                false,
                List.of(),
                List.of()
        );
        SFMContextDocumentProjection cleanReadOnly = SFMContextDocumentProjection.capture(
                "sfm:text_editor_v3",
                readOnly("exact\r\n"),
                "exact\r\n",
                false,
                true,
                List.of(),
                List.of()
        );

        assertTrue(dirty.dirty());
        assertFalse(dirty.readOnly());
        assertFalse(cleanReadOnly.dirty());
        assertTrue(cleanReadOnly.readOnly());
        SFMContextDocumentProjection dirtyReadOnly = SFMContextDocumentProjection.capture(
                "sfm:text_editor_v3", editable, "changed", true, true, List.of(), List.of()
        );
        assertTrue(dirtyReadOnly.dirty());
        assertTrue(dirtyReadOnly.readOnly());
        assertThrows(IllegalArgumentException.class, () -> SFMContextDocumentProjection.capture(
                "sfm:text_editor_v3", editable, "normalized\n", false, false, List.of(), List.of()
        ));
    }

    @Test
    void addressedDocumentSeedsItsParentWhileLiteralDocumentHasNoInventedRoot() {
        String text = "class A {}\r\n";
        SFMPath root = SFMPath.parse("file:///D:/repo/");
        SFMPath path = SFMPath.parse("file:///D:/repo/src/A.java");
        SFMContextDocumentProjection addressed = SFMContextDocumentProjection.capture(
                "sfm:text_editor_v3",
                addressed(text, path, root),
                text,
                false,
                true,
                List.of(),
                List.of()
        );
        SFMContextDocumentProjection literal = SFMContextDocumentProjection.capture(
                "sfm:text_editor_v3",
                SFMTextDocumentSnapshot.literal(text),
                text,
                false,
                false,
                List.of(),
                List.of()
        );

        assertEquals(
                Optional.of(SFMPath.parse("file:///D:/repo/src")),
                SFMContextPathRootDeriver.derive(addressed)
        );
        assertTrue(SFMContextPathRootDeriver.derive(literal).isEmpty());
    }

    @Test
    void emptySelectionRemainsAnExplicitIndependentSelection() {
        SFMContextSelectionProjection selection =
                new SFMContextSelectionProjection("primary", List.of(), true);

        assertTrue(selection.ranges().isEmpty());
        assertTrue(selection.primary());
    }

    private static SFMTextDocumentSnapshot readOnly(String text) {
        SFMTextDocumentSnapshot literal = SFMTextDocumentSnapshot.literal(text);
        return new SFMTextDocumentSnapshot(
                literal.state(),
                literal.text(),
                SFMTextDocumentSnapshot.MutationCapability.READ_ONLY,
                literal.path(),
                literal.authorizedRoot(),
                literal.sha256(),
                literal.byteLength(),
                literal.lastModified(),
                literal.lineEndingKind(),
                literal.targetRange(),
                literal.diagnostics()
        );
    }

    private static SFMTextDocumentSnapshot addressed(String text, SFMPath path, SFMPath root) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new SFMTextDocumentSnapshot(
                SFMTextDocumentSnapshot.State.READY,
                text,
                SFMTextDocumentSnapshot.MutationCapability.READ_ONLY,
                Optional.of(path),
                Optional.of(root),
                Optional.of(SFMContextTextCoordinates.sha256(text)),
                OptionalLong.of(bytes.length),
                Optional.empty(),
                Optional.of(SFMResolverTextResult.LineEndingKind.CRLF),
                Optional.empty(),
                List.of()
        );
    }
}
