package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMRouteComparisonRuntimeTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsMutationsReloadsWithoutRewritingAndResetsOnlyReviewState() throws Exception {
        SFMRouteComparisonRuntime runtime = runtime();
        SFMHistoryGraphRuntime.MachineSnapshot snapshot =
                SFMRouteComparisonIntegrationTestFixture.snapshot("episode-a", 7);
        SFMRouteComparisonSession opened = runtime.openLatest(snapshot);
        Path storePath = path(opened.id());

        SFMRouteComparisonSession mutated = runtime.setMode(
                opened.id(),
                SFMRouteComparisonSession.Mode.INDEPENDENT
        );
        mutated = runtime.seek(opened.id(), SFMRouteComparisonSession.Side.RIGHT, 1);
        mutated = runtime.setDisposition(
                opened.id(),
                SFMRouteComparisonSession.Side.LEFT,
                SFMRouteComparisonSession.Disposition.PREFERRED
        );
        byte[] beforeReload = Files.readAllBytes(storePath);

        SFMRouteComparisonSession reloaded = runtime.reload(opened.id());

        assertEquals(mutated, reloaded);
        assertEquals(java.util.List.of(), mismatch(beforeReload, Files.readAllBytes(storePath)));
        SFMRouteComparisonSession reset = runtime.reset(opened.id());
        assertEquals(opened.left(), reset.left());
        assertEquals(opened.right(), reset.right());
        assertEquals(opened.leftLastPosition(), reset.leftLastPosition());
        assertEquals(opened.rightLastPosition(), reset.rightLastPosition());
        assertEquals(SFMRouteComparisonSession.Mode.LOCKSTEP, reset.mode());
        assertEquals(0, reset.leftCursor());
        assertEquals(0, reset.rightCursor());
        assertEquals(SFMRouteComparisonSession.Disposition.UNDECIDED, reset.leftDisposition());
        assertEquals(SFMRouteComparisonSession.Disposition.UNDECIDED, reset.rightDisposition());
        assertNotEquals(mutated, reset);
    }

    @Test
    void resolvesFocusedRawAndWrappedExactSelectors() {
        SFMRouteComparisonRuntime runtime = runtime();
        SFMRouteComparisonSession opened = runtime.openLatest(
                SFMRouteComparisonIntegrationTestFixture.snapshot("episode-a", 0)
        );

        assertEquals(opened.id(), runtime.resolveSelector("focused", Optional.of(opened.id())));
        assertEquals(opened.id(), runtime.resolveSelector(opened.id(), Optional.empty()));
        assertEquals(opened.id(), runtime.resolveSelector("id(" + opened.id() + ")", Optional.empty()));
        assertTrue(runtime.loadedSessionIds().contains(opened.id()));
    }

    private SFMRouteComparisonRuntime runtime() {
        return new SFMRouteComparisonRuntime(id -> new SFMRouteComparisonStore(path(id)));
    }

    private Path path(String id) {
        return temporaryDirectory.resolve(id + ".route-comparison");
    }

    private static java.util.List<Integer> mismatch(byte[] left, byte[] right) {
        java.util.ArrayList<Integer> indexes = new java.util.ArrayList<>();
        int length = Math.max(left.length, right.length);
        for (int index = 0; index < length; index++) {
            if (index >= left.length || index >= right.length || left[index] != right[index]) indexes.add(index);
        }
        return java.util.List.copyOf(indexes);
    }
}
