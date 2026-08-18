package ca.teamdman.sfm.client.symbol;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

/** Bounded in-memory lease table which keeps palette actions tied to their capture. */
public final class SFMSymbolInspectionSessions {
    public static final int MAX_SESSIONS = 128;
    private static final SFMSymbolInspectionSessions SHARED = new SFMSymbolInspectionSessions();

    private final LinkedHashMap<Long, SFMSymbolInspectionSnapshot> snapshots = new LinkedHashMap<>();
    private long nextId = 1L;

    public static SFMSymbolInspectionSessions shared() {
        return SHARED;
    }

    public synchronized Session capture(SFMSymbolInspectionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        long id = nextId++;
        if (id <= 0) throw new IllegalStateException("Symbol inspection session ids are exhausted");
        snapshots.put(id, snapshot);
        while (snapshots.size() > MAX_SESSIONS) {
            Long oldest = snapshots.keySet().iterator().next();
            snapshots.remove(oldest);
        }
        return new Session(id, snapshot);
    }

    public synchronized Optional<SFMSymbolInspectionSnapshot> find(long id) {
        if (id <= 0) return Optional.empty();
        return Optional.ofNullable(snapshots.get(id));
    }

    public synchronized int size() {
        return snapshots.size();
    }

    public record Session(long id, SFMSymbolInspectionSnapshot snapshot) {
        public Session {
            if (id <= 0) throw new IllegalArgumentException("Symbol inspection session id must be positive");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }
}
