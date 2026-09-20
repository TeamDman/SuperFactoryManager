package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Thread-safe, in-memory observation log for one integrated-world session. */
public final class SFMPacketObservationLog {
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;

    private final Limits limits;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private SessionId sessionId;
    private long nextSequence = 1;
    private int retainedPayloadBytes;

    public SFMPacketObservationLog() {
        this(new Limits(
                MAX_ENTRIES,
                MAX_PAYLOAD_BYTES,
                DEFAULT_PAGE_SIZE,
                MAX_PAGE_SIZE
        ));
    }

    SFMPacketObservationLog(Limits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public synchronized SessionId beginSession() {
        clearEntries();
        sessionId = new SessionId(UUID.randomUUID());
        return sessionId;
    }

    public synchronized void endSession() {
        clearEntries();
        sessionId = null;
    }

    public synchronized Optional<SessionId> currentSessionId() {
        return Optional.ofNullable(sessionId);
    }

    public synchronized Entry append(SFMValue value) {
        if (sessionId == null) {
            throw new IllegalStateException("Cannot append a packet observation without an active session");
        }
        String canonicalJson = SFMValueJsonCodec.encode(value);
        int payloadBytes = canonicalJson.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > limits.maxPayloadBytes()) {
            throw new IllegalArgumentException("Packet observation exceeds the log byte capacity");
        }

        Entry entry = new Entry(nextSequence++, value, canonicalJson, payloadBytes);
        entries.addLast(entry);
        retainedPayloadBytes += payloadBytes;
        evictOldestUntilWithinLimits();
        return entry;
    }

    public synchronized Optional<Page> page(Optional<Cursor> cursor) {
        return page(cursor, limits.defaultPageSize());
    }

    public synchronized Optional<Page> page(
            Optional<Cursor> cursor,
            int requestedLimit
    ) {
        Objects.requireNonNull(cursor, "cursor");
        if (requestedLimit < 1 || requestedLimit > limits.maxPageSize()) {
            throw new IllegalArgumentException(
                    "Packet observation page size must be between 1 and " + limits.maxPageSize()
            );
        }
        if (sessionId == null) {
            return Optional.empty();
        }

        long oldestSequence = entries.isEmpty() ? nextSequence : entries.getFirst().sequence();
        long newestSequence = nextSequence - 1;
        Continuity continuity = Continuity.CONTIGUOUS;
        long afterSequence = oldestSequence - 1;

        if (cursor.isPresent()) {
            Cursor supplied = cursor.orElseThrow();
            if (!sessionId.equals(supplied.sessionId())) {
                continuity = Continuity.SESSION_CHANGED;
            } else if (supplied.afterSequence() < oldestSequence - 1) {
                continuity = Continuity.EVICTED_GAP;
            } else if (supplied.afterSequence() > newestSequence) {
                continuity = Continuity.CURSOR_AHEAD;
                afterSequence = newestSequence;
            } else {
                afterSequence = supplied.afterSequence();
            }
        }

        ArrayList<Entry> selected = new ArrayList<>(Math.min(requestedLimit, entries.size()));
        for (Entry entry : entries) {
            if (entry.sequence() > afterSequence) {
                selected.add(entry);
                if (selected.size() == requestedLimit) {
                    break;
                }
            }
        }
        long nextAfterSequence = selected.isEmpty()
                                 ? afterSequence
                                 : selected.get(selected.size() - 1).sequence();
        Cursor nextCursor = new Cursor(sessionId, nextAfterSequence);
        return Optional.of(new Page(
                sessionId,
                oldestSequence,
                newestSequence,
                entries.size(),
                retainedPayloadBytes,
                continuity,
                selected,
                nextCursor,
                nextAfterSequence < newestSequence
        ));
    }

    private void evictOldestUntilWithinLimits() {
        while (entries.size() > limits.maxEntries()
               || retainedPayloadBytes > limits.maxPayloadBytes()) {
            retainedPayloadBytes -= entries.removeFirst().payloadBytes();
        }
    }

    private void clearEntries() {
        entries.clear();
        retainedPayloadBytes = 0;
        nextSequence = 1;
    }

    record Limits(
            int maxEntries,
            int maxPayloadBytes,
            int defaultPageSize,
            int maxPageSize
    ) {
        Limits {
            if (maxEntries < 1 || maxPayloadBytes < 1) {
                throw new IllegalArgumentException("Packet observation limits must be positive");
            }
            if (defaultPageSize < 1 || defaultPageSize > maxPageSize) {
                throw new IllegalArgumentException("Invalid packet observation page limits");
            }
        }
    }

    public record SessionId(UUID value) {
        public SessionId {
            Objects.requireNonNull(value, "value");
        }
    }

    public record Cursor(
            SessionId sessionId,
            long afterSequence
    ) {
        public Cursor {
            Objects.requireNonNull(sessionId, "sessionId");
            if (afterSequence < 0) {
                throw new IllegalArgumentException("Packet observation cursor cannot be negative");
            }
        }
    }

    public record Entry(
            long sequence,
            SFMValue value,
            String canonicalJson,
            int payloadBytes
    ) {
        public Entry {
            if (sequence < 1 || payloadBytes < 1) {
                throw new IllegalArgumentException("Invalid packet observation entry metadata");
            }
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(canonicalJson, "canonicalJson");
            String expectedJson = SFMValueJsonCodec.encode(value);
            int expectedBytes = expectedJson.getBytes(StandardCharsets.UTF_8).length;
            if (!expectedJson.equals(canonicalJson) || expectedBytes != payloadBytes) {
                throw new IllegalArgumentException("Packet observation entry payload metadata is inconsistent");
            }
        }
    }

    public enum Continuity {
        CONTIGUOUS,
        EVICTED_GAP,
        SESSION_CHANGED,
        CURSOR_AHEAD
    }

    public record Page(
            SessionId sessionId,
            long oldestSequence,
            long newestSequence,
            int retainedEntryCount,
            int retainedPayloadBytes,
            Continuity continuity,
            List<Entry> entries,
            Cursor nextCursor,
            boolean hasMore
    ) {
        public Page {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(continuity, "continuity");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            Objects.requireNonNull(nextCursor, "nextCursor");
        }
    }
}
