package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.value.SFMValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMPacketObservationLogTests {
    @Test
    void pagesOldestFirstWithoutConsumingOrDeduplicating() {
        SFMPacketObservationLog log = logWithLimits(10, 1_000, 2, 3);
        log.beginSession();
        SFMValue duplicate = SFMValue.of("same");
        log.append(duplicate);
        log.append(duplicate);
        log.append(SFMValue.of("last"));

        SFMPacketObservationLog.Page first = log.page(Optional.empty()).orElseThrow();
        SFMPacketObservationLog.Page repeated = log.page(Optional.empty()).orElseThrow();

        assertEquals(first, repeated);
        assertEquals(List.of(1L, 2L), sequences(first));
        assertEquals(List.of(duplicate, duplicate), first.entries().stream().map(
                SFMPacketObservationLog.Entry::value
        ).toList());
        assertTrue(first.hasMore());
        assertThrows(UnsupportedOperationException.class, () -> first.entries().clear());

        SFMPacketObservationLog.Page second = log.page(Optional.of(first.nextCursor()), 3).orElseThrow();
        assertEquals(List.of(3L), sequences(second));
        assertFalse(second.hasMore());
        assertEquals(SFMPacketObservationLog.Continuity.CONTIGUOUS, second.continuity());
    }

    @Test
    void evictsOldestForEntryAndByteCapsAndReportsCursorGaps() {
        SFMPacketObservationLog countBound = logWithLimits(3, 1_000, 3, 3);
        SFMPacketObservationLog.SessionId session = countBound.beginSession();
        IntStream.rangeClosed(1, 4).forEach(i -> countBound.append(SFMValue.of(i)));

        SFMPacketObservationLog.Page countPage = countBound.page(
                Optional.of(new SFMPacketObservationLog.Cursor(session, 0)),
                3
        ).orElseThrow();
        assertEquals(List.of(2L, 3L, 4L), sequences(countPage));
        assertEquals(2, countPage.oldestSequence());
        assertEquals(SFMPacketObservationLog.Continuity.EVICTED_GAP, countPage.continuity());

        SFMPacketObservationLog byteBound = logWithLimits(10, 8, 3, 10);
        SFMPacketObservationLog.SessionId byteSession = byteBound.beginSession();
        byteBound.append(SFMValue.of("a"));
        byteBound.append(SFMValue.of("b"));
        byteBound.append(SFMValue.of("c"));

        SFMPacketObservationLog.Page bytePage = byteBound.page(
                Optional.of(new SFMPacketObservationLog.Cursor(byteSession, 0)),
                10
        ).orElseThrow();
        assertEquals(List.of(2L, 3L), sequences(bytePage));
        assertEquals(6, bytePage.retainedPayloadBytes());
        assertEquals(SFMPacketObservationLog.Continuity.EVICTED_GAP, bytePage.continuity());
    }

    @Test
    void reportsSessionChangesAndClearsAtSessionEnd() {
        SFMPacketObservationLog log = logWithLimits(10, 1_000, 10, 10);
        SFMPacketObservationLog.SessionId firstSession = log.beginSession();
        log.append(SFMValue.of("old"));
        SFMPacketObservationLog.Cursor oldCursor = new SFMPacketObservationLog.Cursor(firstSession, 1);

        SFMPacketObservationLog.SessionId secondSession = log.beginSession();
        log.append(SFMValue.of("new"));
        SFMPacketObservationLog.Page page = log.page(Optional.of(oldCursor), 10).orElseThrow();

        assertNotEquals(firstSession, secondSession);
        assertEquals(SFMPacketObservationLog.Continuity.SESSION_CHANGED, page.continuity());
        assertEquals(List.of(1L), sequences(page));
        assertEquals(SFMValue.of("new"), page.entries().get(0).value());

        log.endSession();
        assertTrue(log.currentSessionId().isEmpty());
        assertTrue(log.page(Optional.empty()).isEmpty());
    }

    @Test
    void validatesPageBoundsAndNormalizesFutureCursors() {
        SFMPacketObservationLog log = logWithLimits(10, 1_000, 2, 3);
        SFMPacketObservationLog.SessionId session = log.beginSession();
        log.append(SFMValue.of(true));

        assertThrows(IllegalArgumentException.class, () -> log.page(Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class, () -> log.page(Optional.empty(), 4));

        SFMPacketObservationLog.Page page = log.page(
                Optional.of(new SFMPacketObservationLog.Cursor(session, 99)),
                3
        ).orElseThrow();
        assertEquals(SFMPacketObservationLog.Continuity.CURSOR_AHEAD, page.continuity());
        assertTrue(page.entries().isEmpty());
        assertEquals(1, page.nextCursor().afterSequence());
    }

    @Test
    void serializesConcurrentAppendsIntoUniqueSequences() {
        SFMPacketObservationLog log = logWithLimits(1_024, 1_048_576, 50, 1_024);
        log.beginSession();

        IntStream.range(0, 512).parallel().forEach(i -> log.append(SFMValue.of(i)));

        SFMPacketObservationLog.Page page = log.page(Optional.empty(), 1_024).orElseThrow();
        assertEquals(512, page.entries().size());
        assertEquals(
                IntStream.rangeClosed(1, 512).mapToObj(i -> (long) i).toList(),
                sequences(page)
        );
    }

    private static SFMPacketObservationLog logWithLimits(
            int maxEntries,
            int maxBytes,
            int defaultPageSize,
            int maxPageSize
    ) {
        return new SFMPacketObservationLog(new SFMPacketObservationLog.Limits(
                maxEntries,
                maxBytes,
                defaultPageSize,
                maxPageSize
        ));
    }

    private static List<Long> sequences(SFMPacketObservationLog.Page page) {
        return page.entries().stream().map(SFMPacketObservationLog.Entry::sequence).toList();
    }
}
