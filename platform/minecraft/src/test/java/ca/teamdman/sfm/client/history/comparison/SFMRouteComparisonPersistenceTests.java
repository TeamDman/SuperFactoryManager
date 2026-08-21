package ca.teamdman.sfm.client.history.comparison;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession.Disposition;
import static ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession.Mode;
import static ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession.Side;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMRouteComparisonPersistenceTests {
    @Test
    void codecRoundTripsUnicodeAndControlCharactersDeterministically() {
        SFMRouteComparisonSession original = new SFMRouteComparisonSession(
                SFMRouteComparisonSession.SCHEMA,
                "review\n雪",
                new SFMRouteComparisonSession.RouteAddress("machine\tleft", "plan/α", "route-left"),
                new SFMRouteComparisonSession.RouteAddress("machine\tleft", "plan/β", "route-right"),
                Mode.INDEPENDENT,
                2,
                7,
                2,
                7,
                Disposition.PREFERRED,
                Disposition.REJECTED,
                42
        );

        String encoded = SFMRouteComparisonCodec.encode(original);
        SFMRouteComparisonSession decoded = SFMRouteComparisonCodec.decode(encoded);

        assertEquals(original, decoded);
        assertEquals(encoded, SFMRouteComparisonCodec.encode(decoded));
        assertFalse(encoded.contains("review\n雪"), "free-form text must not break the line-oriented codec");
    }

    @Test
    void codecRejectsUnknownVersionMalformedShapeAndInvalidInvariant() {
        String encoded = SFMRouteComparisonCodec.encode(session());
        assertThrows(IllegalArgumentException.class, () -> SFMRouteComparisonCodec.decode(
                encoded.replace("sfm.route-comparison-codec/1", "sfm.route-comparison-codec/2")));
        assertThrows(IllegalArgumentException.class, () -> SFMRouteComparisonCodec.decode(
                encoded.replace("left.cursor\t0", "left.cursor\t-1")));
        assertThrows(IllegalArgumentException.class, () -> SFMRouteComparisonCodec.decode(
                encoded.replace("right.disposition\tUNDECIDED", "right.disposition\tPREFERRED")
                        .replace("left.disposition\tUNDECIDED", "left.disposition\tPREFERRED")));
        assertThrows(IllegalArgumentException.class, () -> SFMRouteComparisonCodec.decode(
                encoded.substring(0, encoded.length() - 1)));
    }

    @Test
    void storePersistsCanonicalBytesAndReloadsWithoutMutatingPriorState(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("nested/comparison.session");
        SFMRouteComparisonStore store = new SFMRouteComparisonStore(path);
        assertEquals(path.toAbsolutePath().normalize(), store.path());
        assertEquals(path.toAbsolutePath().normalize().resolveSibling("comparison.session.last-valid"),
                store.lastValidPath());
        assertEquals(java.util.Optional.empty(), store.load());

        SFMRouteComparisonSession original = session();
        store.save(original);
        byte[] firstBytes = Files.readAllBytes(path);
        assertEquals(original, store.load().orElseThrow());

        store.save(store.load().orElseThrow());
        assertArrayEquals(firstBytes, Files.readAllBytes(path));
        assertEquals(SFMRouteComparisonCodec.encode(original), Files.readString(path, StandardCharsets.UTF_8));

        SFMRouteComparisonSession changed = SFMRouteComparisonKernel.setDisposition(
                original, Side.RIGHT, Disposition.REJECTED);
        changed = SFMRouteComparisonKernel.setMode(changed, Mode.INDEPENDENT);
        changed = SFMRouteComparisonKernel.seek(changed, Side.LEFT, 2);
        changed = SFMRouteComparisonKernel.seek(changed, Side.RIGHT, 4);
        store.save(changed);

        assertEquals(changed, store.load().orElseThrow());
        assertEquals(Disposition.UNDECIDED, original.rightDisposition());
        assertEquals(0, original.leftCursor());
        assertEquals(0, original.rightCursor());
        assertEquals(0, original.revision());
    }

    @Test
    void storeRecoversLastValidAfterTheActiveBytesBecomeInvalid(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("comparison.session");
        SFMRouteComparisonStore store = new SFMRouteComparisonStore(path);
        SFMRouteComparisonSession original = session();
        store.save(original);

        Files.writeString(path, "not a comparison", StandardCharsets.UTF_8);
        SFMRouteComparisonStore.LoadResult result = store.loadResult();

        assertEquals(original, result.session().orElseThrow());
        assertTrue(result.recoveredLastValid());
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.contains("Invalid active")));
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.contains("Recovered")));
    }

    private static SFMRouteComparisonSession session() {
        return SFMRouteComparisonSession.create(
                "comparison-1",
                new SFMRouteComparisonSession.RouteAddress("machine-1", "plan-left", "route-left"),
                new SFMRouteComparisonSession.RouteAddress("machine-1", "plan-right", "route-right"),
                2,
                5
        );
    }
}
