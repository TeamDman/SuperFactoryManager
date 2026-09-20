package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.value.SFMValue;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** Owns observation-log state across client world transitions. */
public final class SFMPacketObservationRuntime {
    private static final SFMPacketObservationRuntime INSTANCE =
            new SFMPacketObservationRuntime(new SFMPacketObservationLog());

    private final SFMPacketObservationLog log;
    private @Nullable Object sessionIdentity;

    SFMPacketObservationRuntime(SFMPacketObservationLog log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    public static SFMPacketObservationRuntime get() {
        return INSTANCE;
    }

    synchronized void observeSessionIdentity(@Nullable Object identity) {
        if (identity == sessionIdentity) {
            return;
        }
        sessionIdentity = identity;
        if (identity == null) {
            log.endSession();
        } else {
            log.beginSession();
        }
    }

    synchronized SFMPacketObservationLog.Entry append(
            Object identity,
            SFMValue value
    ) {
        Objects.requireNonNull(identity, "identity");
        observeSessionIdentity(identity);
        return log.append(value);
    }

    public synchronized Optional<SFMPacketObservationLog.SessionId> currentSessionId() {
        return log.currentSessionId();
    }

    public synchronized Optional<SFMPacketObservationLog.Page> page(
            Optional<SFMPacketObservationLog.Cursor> cursor
    ) {
        return log.page(cursor);
    }

    public synchronized Optional<SFMPacketObservationLog.Page> page(
            Optional<SFMPacketObservationLog.Cursor> cursor,
            int limit
    ) {
        return log.page(cursor, limit);
    }
}
