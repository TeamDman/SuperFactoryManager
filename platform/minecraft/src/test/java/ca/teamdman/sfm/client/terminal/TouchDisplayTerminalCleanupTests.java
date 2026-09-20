package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class TouchDisplayTerminalCleanupTests {
    @Test void cleanupAttemptsLaterOwnedResourcesAfterEarlierFailures() {
        List<String> closed = new ArrayList<>();
        var inbox = new IllegalStateException("inbox");
        var target = new IllegalStateException("target");
        var failure = assertThrows(IllegalStateException.class, () -> TouchDisplayTerminalCleanup.runAll(
                () -> { closed.add("inbox"); throw inbox; },
                () -> { closed.add("target"); throw target; },
                () -> closed.add("transport")));
        assertSame(inbox, failure);
        assertEquals(List.of("inbox", "target", "transport"), closed);
        assertArrayEquals(new Throwable[]{target}, failure.getSuppressed());
    }

    @Test void rollbackPreservesOriginalFailureAndAttemptsEveryCleanup() {
        var startup = new IllegalStateException("startup");
        var target = new IllegalStateException("target");
        List<String> closed = new ArrayList<>();
        assertDoesNotThrow(() -> TouchDisplayTerminalCleanup.suppressOn(startup,
                () -> { closed.add("target"); throw target; },
                () -> closed.add("session"), () -> closed.add("transport")));
        assertEquals(List.of("target", "session", "transport"), closed);
        assertArrayEquals(new Throwable[]{target}, startup.getSuppressed());
    }

    @Test void repeatedSameFailureDoesNotSelfSuppressOrStopCleanup() {
        var shared = new IllegalStateException("same");
        List<String> closed = new ArrayList<>();
        assertDoesNotThrow(() -> TouchDisplayTerminalCleanup.suppressOn(shared,
                () -> { throw shared; }, () -> { throw shared; }, () -> closed.add("transport")));
        assertEquals(List.of("transport"), closed);
        assertEquals(0, shared.getSuppressed().length);
    }

    @Test void emptyAndSuccessfulCleanupPreserveOrdering() {
        assertDoesNotThrow(() -> { TouchDisplayTerminalCleanup.runAll(); });
        List<Integer> closed = new ArrayList<>();
        TouchDisplayTerminalCleanup.runAll(() -> closed.add(1), () -> closed.add(2));
        assertEquals(List.of(1, 2), closed);
    }
}
