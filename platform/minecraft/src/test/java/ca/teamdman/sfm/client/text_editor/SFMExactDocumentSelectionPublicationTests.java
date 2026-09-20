package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExactDocumentSelectionPublicationTests {
    @Test
    public void publicationAtomicallyRetainsCoordinateTextIdentityAndSelections() {
        String source = "first\r\nsecond\r\n";
        ArrayList<SFMTextDocumentSelection> supplied = new ArrayList<>();
        supplied.add(fullDocumentSelection(source));
        SFMExactDocumentSelectionPublication.Identity identity = identity(source, 7L, 11L, 13L);

        SFMExactDocumentSelectionPublication.State state =
                SFMExactDocumentSelectionPublication.State.publish(identity, source, supplied);
        supplied.clear();

        SFMExactDocumentSelectionPublication publication = state.publication().orElseThrow();
        assertEquals(identity, publication.identity());
        assertEquals(source, publication.coordinateText());
        assertEquals(1, publication.selections().size());
        assertTrue(state.diagnostic().isEmpty());
    }

    @Test
    public void publicationAcceptsHalfOpenEofAfterLfAndCrLf() {
        for (String source : List.of("line\n", "line\r\n")) {
            SFMExactDocumentSelectionPublication.State state =
                    SFMExactDocumentSelectionPublication.State.publish(
                            identity(source, 1L, 1L, 1L),
                            source,
                            List.of(fullDocumentSelection(source))
                    );

            assertTrue(state.publication().isPresent(), () -> "Expected valid publication for " + printable(source));
            assertTrue(state.diagnostic().isEmpty());
        }
    }

    @Test
    public void publicationRetainsReversePointerSelectionDirection() {
        String source = "a😀bc\r\n";
        SFMTextDocumentPosition start = SFMContextTextCoordinates.atLineColumn(source, 0, 1);
        SFMTextDocumentPosition end = SFMContextTextCoordinates.atLineColumn(source, 0, 4);
        SFMTextDocumentSelection reverse = new SFMTextDocumentSelection(
                "pointer-primary",
                end,
                start,
                true
        );

        SFMExactDocumentSelectionPublication.State state =
                SFMExactDocumentSelectionPublication.State.publish(
                        identity(source, 1L, 2L, 3L),
                        source,
                        List.of(reverse)
                );

        SFMTextDocumentSelection retained = state.publication().orElseThrow().selections().get(0);
        assertEquals(end, retained.anchor());
        assertEquals(start, retained.active());
        assertEquals(start, retained.orderedRange().start());
        assertEquals(end, retained.orderedRange().end());
    }

    @Test
    public void documentAddressReplacementFailsClosedAndRetainsDiagnostic() {
        String source = "same bytes";
        SFMExactDocumentSelectionPublication.State published = publish(source, 2L, 3L, 5L);
        SFMExactDocumentSelectionPublication.Identity replacement = new SFMExactDocumentSelectionPublication.Identity(
                "file:///replacement.java",
                published.publication().orElseThrow().identity().contentSha256(),
                2L,
                3L,
                5L
        );

        assertRetainedFailure(
                published.resolve(replacement),
                SFMExactDocumentSelectionPublication.DiagnosticCode.DOCUMENT_ADDRESS_MISMATCH,
                identity(source, 2L, 3L, 5L)
        );
    }

    @Test
    public void sameAddressContentReplacementFailsClosedOnHash() {
        String source = "before";
        SFMExactDocumentSelectionPublication.State published = publish(source, 2L, 3L, 5L);

        assertRetainedFailure(
                published.resolve(identity("after", 2L, 3L, 5L)),
                SFMExactDocumentSelectionPublication.DiagnosticCode.CONTENT_HASH_MISMATCH,
                identity(source, 2L, 3L, 5L)
        );
    }

    @Test
    public void documentGenerationChangeFailsClosedAndRetainsDiagnostic() {
        String source = "content";
        SFMExactDocumentSelectionPublication.State published = publish(source, 2L, 3L, 5L);

        assertRetainedFailure(
                published.resolve(identity(source, 3L, 3L, 5L)),
                SFMExactDocumentSelectionPublication.DiagnosticCode.DOCUMENT_GENERATION_MISMATCH,
                identity(source, 2L, 3L, 5L)
        );
    }

    @Test
    public void modelContentRevisionChangeFailsClosedAndRetainsDiagnostic() {
        String source = "content";
        SFMExactDocumentSelectionPublication.State published = publish(source, 2L, 3L, 5L);

        assertRetainedFailure(
                published.resolve(identity(source, 2L, 4L, 5L)),
                SFMExactDocumentSelectionPublication.DiagnosticCode.MODEL_CONTENT_REVISION_MISMATCH,
                identity(source, 2L, 3L, 5L)
        );
    }

    @Test
    public void cursorFingerprintChangeFailsClosedAndRetainsDiagnostic() {
        String source = "content";
        SFMExactDocumentSelectionPublication.State published = publish(source, 2L, 3L, 5L);

        assertRetainedFailure(
                published.resolve(identity(source, 2L, 3L, 8L)),
                SFMExactDocumentSelectionPublication.DiagnosticCode.CURSOR_FINGERPRINT_MISMATCH,
                identity(source, 2L, 3L, 5L)
        );
    }

    @Test
    public void mismatchedCoordinateTextHashIsRejectedAsMalformedPublication() {
        String source = "content";
        SFMExactDocumentSelectionPublication.Identity wrongHash = new SFMExactDocumentSelectionPublication.Identity(
                "file:///document.java",
                SFMContextTextCoordinates.sha256("different"),
                1L,
                1L,
                1L
        );

        SFMExactDocumentSelectionPublication.State state =
                SFMExactDocumentSelectionPublication.State.publish(
                        wrongHash,
                        source,
                        List.of(fullDocumentSelection(source))
                );

        assertMalformed(state, "hash");
    }

    @Test
    public void rangeFromAnotherDocumentIsRejectedAsMalformedPublication() {
        String original = "first\nsecond\n";
        String replacement = "first";

        SFMExactDocumentSelectionPublication.State state =
                SFMExactDocumentSelectionPublication.State.publish(
                        identity(replacement, 1L, 1L, 1L),
                        replacement,
                        List.of(fullDocumentSelection(original))
                );

        assertMalformed(state, "outside");
    }

    private static SFMExactDocumentSelectionPublication.State publish(
            String source,
            long documentGeneration,
            long modelContentRevision,
            long cursorFingerprint
    ) {
        return SFMExactDocumentSelectionPublication.State.publish(
                identity(source, documentGeneration, modelContentRevision, cursorFingerprint),
                source,
                List.of(fullDocumentSelection(source))
        );
    }

    private static SFMExactDocumentSelectionPublication.Identity identity(
            String source,
            long documentGeneration,
            long modelContentRevision,
            long cursorFingerprint
    ) {
        return SFMExactDocumentSelectionPublication.Identity.forText(
                "file:///document.java",
                source,
                documentGeneration,
                modelContentRevision,
                cursorFingerprint
        );
    }

    private static SFMTextDocumentSelection fullDocumentSelection(String source) {
        return new SFMTextDocumentSelection(
                "selection",
                SFMContextTextCoordinates.atUtf16Offset(source, 0),
                SFMContextTextCoordinates.atUtf16Offset(source, source.length()),
                true
        );
    }

    private static void assertRetainedFailure(
            SFMExactDocumentSelectionPublication.State failed,
            SFMExactDocumentSelectionPublication.DiagnosticCode expectedCode,
            SFMExactDocumentSelectionPublication.Identity formerlyValidIdentity
    ) {
        assertTrue(failed.publication().isEmpty());
        assertEquals(expectedCode, failed.diagnostic().orElseThrow().code());

        SFMExactDocumentSelectionPublication.State revisited = failed.resolve(formerlyValidIdentity);
        assertTrue(revisited.publication().isEmpty());
        assertEquals(failed.diagnostic(), revisited.diagnostic());
    }

    private static void assertMalformed(
            SFMExactDocumentSelectionPublication.State state,
            String expectedMessagePart
    ) {
        assertTrue(state.publication().isEmpty());
        SFMExactDocumentSelectionPublication.Diagnostic diagnostic = state.diagnostic().orElseThrow();
        assertEquals(SFMExactDocumentSelectionPublication.DiagnosticCode.MALFORMED_PUBLICATION, diagnostic.code());
        assertTrue(diagnostic.message().contains(expectedMessagePart));
        assertFalse(diagnostic.message().isBlank());
    }

    private static String printable(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
