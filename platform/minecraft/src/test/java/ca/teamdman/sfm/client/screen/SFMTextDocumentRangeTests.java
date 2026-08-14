package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextRequest;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTextDocumentRangeTests {
    private static final String TEXT = "α\r\nβ😀\n";

    @Test
    void byteOffsetsResolveToUnicodeScalarColumnsAndTreatCrLfAsOneLineEnding() {
        assertEquals(new SFMTextDocumentPosition(0, 0, 0), position(0));
        assertEquals(new SFMTextDocumentPosition(0, 1, 2), position(2));
        assertEquals(new SFMTextDocumentPosition(1, 0, 4), position(4));
        assertEquals(new SFMTextDocumentPosition(1, 1, 6), position(6));
        assertEquals(new SFMTextDocumentPosition(1, 2, 10), position(10));
        assertEquals(new SFMTextDocumentPosition(2, 0, 11), position(11));

        assertThrows(IllegalArgumentException.class, () -> position(1));
        assertThrows(IllegalArgumentException.class, () -> position(3));
        assertThrows(IllegalArgumentException.class, () -> position(7));
        assertThrows(IllegalArgumentException.class, () -> position(12));
    }

    @Test
    void exactHalfOpenRangeValidatesWhileAStaleCoordinateIsRejected() {
        SFMTextDocumentRange exact = new SFMTextDocumentRange(position(4), position(10));
        exact.validateAgainst(TEXT);

        SFMTextDocumentRange staleColumn = new SFMTextDocumentRange(
                new SFMTextDocumentPosition(1, 1, 4),
                position(10)
        );
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> staleColumn.validateAgainst(TEXT)
        );
        assertTrue(failure.getMessage().contains("range start"));
    }

    @Test
    void addressedSnapshotsPreserveExactRangesAndTurnInvalidRangesIntoTypedDiagnostics() {
        SFMTextDocumentRange exact = new SFMTextDocumentRange(position(4), position(10));
        SFMTextDocumentSnapshot ready = SFMTextDocumentSnapshot.fromResolver(
                readyResult(TEXT),
                Optional.of(exact)
        );
        assertEquals(SFMTextDocumentSnapshot.State.READY, ready.state());
        assertEquals(Optional.of(exact), ready.targetRange());
        assertTrue(ready.readOnly());

        SFMTextDocumentRange stale = new SFMTextDocumentRange(
                new SFMTextDocumentPosition(0, 0, 4),
                position(10)
        );
        SFMTextDocumentSnapshot rejected = SFMTextDocumentSnapshot.fromResolver(
                readyResult(TEXT),
                Optional.of(stale)
        );
        assertEquals(SFMTextDocumentSnapshot.State.INVALID_RANGE, rejected.state());
        assertTrue(rejected.displayText().contains("invalid_range"));
        assertTrue(rejected.displayText().contains("range start"));
    }

    private static SFMTextDocumentPosition position(int byteOffset) {
        return SFMTextDocumentRange.positionAtByteOffset(TEXT, byteOffset);
    }

    private static SFMResolverTextResult readyResult(String text) {
        SFMPath root = SFMPath.parse("file:///D:/fixture");
        SFMResolverTextRequest request = new SFMResolverTextRequest(
                SFMPath.parse("file:///D:/fixture/A.java"),
                root,
                Optional.empty(),
                4096,
                1,
                new SFMExplorerCancellationToken()
        );
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return SFMResolverTextResult.ready(
                request,
                1,
                text,
                sha256(bytes),
                bytes.length,
                Optional.empty(),
                SFMResolverTextResult.LineEndingKind.MIXED
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
