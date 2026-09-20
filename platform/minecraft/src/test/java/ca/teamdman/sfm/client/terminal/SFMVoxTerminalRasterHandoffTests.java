package ca.teamdman.sfm.client.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SFMVoxTerminalRasterHandoffTests {
    @Test
    void activeStreamSurvivesPendingFailureUntilAValidFullResyncExists() {
        SFMVoxTerminalRasterHandoff<TestStream> handoff = handoff();
        TestStream active = new TestStream("active");
        activate(handoff, active);
        TestStream pending = new TestStream("pending");

        handoff.beginPending(pending);

        assertSame(active, handoff.active());
        assertSame(pending, handoff.pending());
        assertEquals(SFMVoxTerminalRasterHandoff.Role.ACTIVE, handoff.role(active));
        assertEquals(SFMVoxTerminalRasterHandoff.Role.PENDING, handoff.role(pending));
        assertFalse(handoff.promotePending(pending, false));
        assertSame(active, handoff.active());
        assertEquals(0, active.closeCount);

        assertTrue(handoff.failPending(pending));
        assertSame(active, handoff.active());
        assertNull(handoff.pending());
        assertEquals(0, active.closeCount);
        assertEquals(1, pending.closeCount);
    }

    @Test
    void firstValidFullResyncAtomicallyPromotesPendingAndRetiresOldActive() {
        SFMVoxTerminalRasterHandoff<TestStream> handoff = handoff();
        TestStream oldActive = new TestStream("old-active");
        activate(handoff, oldActive);
        TestStream replacement = new TestStream("replacement");
        handoff.beginPending(replacement);

        assertTrue(handoff.promotePending(replacement, true));

        assertSame(replacement, handoff.active());
        assertNull(handoff.pending());
        assertEquals(1, oldActive.closeCount);
        assertEquals(0, replacement.closeCount);
        assertEquals(SFMVoxTerminalRasterHandoff.Role.STALE, handoff.role(oldActive));
        assertEquals(SFMVoxTerminalRasterHandoff.Role.ACTIVE, handoff.role(replacement));
        assertFalse(handoff.promotePending(oldActive, true));
        assertEquals(1, oldActive.closeCount);
    }

    @Test
    void newerRequestsSupersedeOnlyPendingAttempts() {
        SFMVoxTerminalRasterHandoff<TestStream> handoff = handoff();
        TestStream active = new TestStream("active");
        activate(handoff, active);
        TestStream firstPending = new TestStream("first-pending");
        TestStream newestPending = new TestStream("newest-pending");

        handoff.beginPending(firstPending);
        handoff.beginPending(newestPending);

        assertSame(active, handoff.active());
        assertSame(newestPending, handoff.pending());
        assertEquals(0, active.closeCount);
        assertEquals(1, firstPending.closeCount);
        assertEquals(SFMVoxTerminalRasterHandoff.Role.STALE, handoff.role(firstPending));
    }

    @Test
    void activeFailureRetiresTheExactStreamAndAllowsAReplacementAttempt() {
        SFMVoxTerminalRasterHandoff<TestStream> handoff = handoff();
        TestStream failedActive = new TestStream("failed-active");
        activate(handoff, failedActive);

        assertTrue(handoff.failActive(failedActive));

        assertNull(handoff.active());
        assertNull(handoff.pending());
        assertEquals(1, failedActive.closeCount);
        assertEquals(SFMVoxTerminalRasterHandoff.Role.STALE, handoff.role(failedActive));
        assertFalse(handoff.failActive(failedActive));
        assertEquals(1, failedActive.closeCount);

        TestStream retry = new TestStream("retry");
        handoff.beginPending(retry);
        assertSame(retry, handoff.pending());
        assertTrue(handoff.promotePending(retry, true));
        assertSame(retry, handoff.active());
    }

    @Test
    void activeFailurePreservesAnIndependentPendingReplacement() {
        SFMVoxTerminalRasterHandoff<TestStream> handoff = handoff();
        TestStream failedActive = new TestStream("failed-active");
        activate(handoff, failedActive);
        TestStream pending = new TestStream("pending");
        handoff.beginPending(pending);

        assertTrue(handoff.failActive(failedActive));

        assertNull(handoff.active());
        assertSame(pending, handoff.pending());
        assertEquals(1, failedActive.closeCount);
        assertEquals(0, pending.closeCount);
        assertEquals(SFMVoxTerminalRasterHandoff.Role.PENDING, handoff.role(pending));
        assertTrue(handoff.promotePending(pending, true));
        assertSame(pending, handoff.active());
    }

    @Test
    void connectionCloseRetiresActiveAndPendingExactlyOnce() {
        SFMVoxTerminalRasterHandoff<TestStream> handoff = handoff();
        TestStream active = new TestStream("active");
        activate(handoff, active);
        TestStream pending = new TestStream("pending");
        handoff.beginPending(pending);

        handoff.closeAll();
        handoff.closeAll();
        handoff.failPending(pending);

        assertNull(handoff.active());
        assertNull(handoff.pending());
        assertEquals(1, active.closeCount);
        assertEquals(1, pending.closeCount);
    }

    @Test
    void independentPanelsDoNotShareActiveOrPendingState() {
        SFMVoxTerminalRasterHandoff<TestStream> left = handoff();
        SFMVoxTerminalRasterHandoff<TestStream> right = handoff();
        TestStream leftActive = new TestStream("left-active");
        TestStream rightActive = new TestStream("right-active");
        activate(left, leftActive);
        activate(right, rightActive);
        TestStream leftPending = new TestStream("left-pending");

        left.beginPending(leftPending);
        assertTrue(left.promotePending(leftPending, true));

        assertSame(leftPending, left.active());
        assertSame(rightActive, right.active());
        assertEquals(1, leftActive.closeCount);
        assertEquals(0, rightActive.closeCount);
    }

    private static SFMVoxTerminalRasterHandoff<TestStream> handoff() {
        return new SFMVoxTerminalRasterHandoff<>(TestStream::close);
    }

    private static void activate(
            SFMVoxTerminalRasterHandoff<TestStream> handoff,
            TestStream stream) {
        handoff.beginPending(stream);
        assertTrue(handoff.promotePending(stream, true));
    }

    private static final class TestStream {
        private final String name;
        private int closeCount;

        private TestStream(String name) {
            this.name = name;
        }

        private void close() {
            closeCount++;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
