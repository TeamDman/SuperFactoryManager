package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded local inbox. Its cursors report loss; they do not request retransmission. */
public final class SFMClientInbox {
    public static final int MAX_CHANNELS = 16;
    public static final int MAX_ENTRIES_PER_CHANNEL = 64;
    public static final int MAX_ENTRIES_TOTAL = 256;
    public static final int MAX_BYTES_TOTAL = 512 * 1024;
    public static final int MAX_PAGE_SIZE = 100;

    private final Map<ResourceLocation, Stream> streams = new HashMap<>();
    private final Deque<Retained> arrivalOrder = new ArrayDeque<>();
    private UUID session;
    private UUID recipient;
    private ResourceLocation dimension;
    private int retainedBytes;

    public synchronized void beginSession(UUID session, UUID recipient, ResourceLocation dimension) {
        clear();
        this.session = Objects.requireNonNull(session, "session");
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
    }

    public synchronized void endSession() {
        clear();
        session = null;
        recipient = null;
        dimension = null;
    }

    public synchronized Optional<UUID> session() {
        return Optional.ofNullable(session);
    }

    public synchronized boolean subscribe(ResourceLocation channel) {
        Objects.requireNonNull(channel, "channel");
        if (session == null) {
            return false;
        }
        if (streams.containsKey(channel)) {
            return true;
        }
        if (streams.size() >= MAX_CHANNELS) {
            return false;
        }
        streams.put(channel, new Stream());
        return true;
    }

    public synchronized void unsubscribe(ResourceLocation channel) {
        Stream removed = streams.remove(channel);
        if (removed != null) {
            for (Entry entry : removed.entries) {
                arrivalOrder.remove(new Retained(channel, entry));
                retainedBytes -= entry.payloadBytes;
            }
        }
    }

    public synchronized boolean isSubscribed(ResourceLocation channel) {
        return streams.containsKey(channel);
    }

    public synchronized boolean append(UUID incomingSession, SFMClientInboxAddress address, SFMValue value) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(value, "value");
        Stream stream = streams.get(address.channel());
        if (session == null || !session.equals(incomingSession)
            || !recipient.equals(address.recipient()) || !dimension.equals(address.dimension())
            || stream == null) {
            return false;
        }
        String json;
        try {
            json = SFMValueJsonCodec.encode(value);
        } catch (IllegalArgumentException invalidValue) {
            return false;
        }
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES) {
            return false;
        }
        Entry entry = new Entry(stream.nextSequence++, value, bytes);
        stream.entries.addLast(entry);
        arrivalOrder.addLast(new Retained(address.channel(), entry));
        retainedBytes += bytes;
        while (stream.entries.size() > MAX_ENTRIES_PER_CHANNEL) {
            Entry evicted = stream.entries.removeFirst();
            arrivalOrder.remove(new Retained(address.channel(), evicted));
            retainedBytes -= evicted.payloadBytes;
        }
        while (arrivalOrder.size() > MAX_ENTRIES_TOTAL || retainedBytes > MAX_BYTES_TOTAL) {
            Retained evicted = arrivalOrder.removeFirst();
            Stream owner = streams.get(evicted.channel);
            if (owner != null) {
                owner.entries.remove(evicted.entry);
            }
            retainedBytes -= evicted.entry.payloadBytes;
        }
        return true;
    }

    public synchronized Optional<Page> page(ResourceLocation channel, Optional<Cursor> cursor, int limit) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(cursor, "cursor");
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid inbox page size");
        }
        Stream stream = streams.get(channel);
        if (session == null || stream == null) {
            return Optional.empty();
        }
        long oldest = stream.entries.isEmpty() ? stream.nextSequence : stream.entries.getFirst().sequence;
        long newest = stream.nextSequence - 1;
        long after = oldest - 1;
        Continuity continuity = Continuity.CONTIGUOUS;
        if (cursor.isPresent()) {
            Cursor supplied = cursor.orElseThrow();
            if (!session.equals(supplied.session) || !stream.id.equals(supplied.stream)) {
                continuity = Continuity.SESSION_CHANGED;
            } else if (supplied.afterSequence < oldest - 1) {
                continuity = Continuity.EVICTED_GAP;
            } else if (supplied.afterSequence > newest) {
                continuity = Continuity.CURSOR_AHEAD;
                after = newest;
            } else {
                after = supplied.afterSequence;
            }
        }
        ArrayList<Entry> entries = new ArrayList<>();
        for (Entry entry : stream.entries) {
            if (entry.sequence > after) {
                entries.add(entry);
                if (entries.size() == limit) {
                    break;
                }
            }
        }
        long next = entries.isEmpty() ? after : entries.get(entries.size() - 1).sequence;
        return Optional.of(new Page(
                continuity,
                List.copyOf(entries),
                new Cursor(session, stream.id, next),
                oldest,
                newest,
                next < newest,
                retainedBytes
        ));
    }

    private void clear() {
        streams.clear();
        arrivalOrder.clear();
        retainedBytes = 0;
    }

    private static final class Stream {
        private final UUID id = UUID.randomUUID();
        private final Deque<Entry> entries = new ArrayDeque<>();
        private long nextSequence = 1;
    }

    private record Retained(ResourceLocation channel, Entry entry) {
    }

    public record Entry(long sequence, SFMValue value, int payloadBytes) {
    }

    public record Cursor(UUID session, UUID stream, long afterSequence) {
        public Cursor {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(stream, "stream");
            if (afterSequence < 0) {
                throw new IllegalArgumentException("Inbox cursor cannot be negative");
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
            Continuity continuity,
            List<Entry> entries,
            Cursor nextCursor,
            long oldestSequence,
            long newestSequence,
            boolean hasMore,
            int retainedPayloadBytes
    ) {
    }
}
