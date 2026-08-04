package ca.teamdman.sfm.client.terminal;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Lock-owned make-before-break state for one active and one pending raster stream.
 *
 * <p>The caller validates a pending stream's first frame before asking for
 * promotion. Retired streams are handed to one cleanup callback exactly once.
 */
final class SFMVoxTerminalRasterHandoff<T> {
    enum Role {
        ACTIVE,
        PENDING,
        STALE
    }

    private final Consumer<T> retire;
    private T active;
    private T pending;

    SFMVoxTerminalRasterHandoff(Consumer<T> retire) {
        this.retire = Objects.requireNonNull(retire, "retire");
    }

    T active() {
        return active;
    }

    T pending() {
        return pending;
    }

    Role role(T candidate) {
        if (candidate != null && active == candidate) return Role.ACTIVE;
        if (candidate != null && pending == candidate) return Role.PENDING;
        return Role.STALE;
    }

    void beginPending(T candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (candidate == active) {
            throw new IllegalArgumentException("the active raster stream cannot also be pending");
        }
        if (pending == candidate) return;
        T superseded = pending;
        pending = candidate;
        retire(superseded);
    }

    boolean promotePending(T candidate, boolean validFullResync) {
        if (!validFullResync || candidate == null || pending != candidate) return false;
        T retiredActive = active;
        active = candidate;
        pending = null;
        retire(retiredActive);
        return true;
    }

    boolean failPending(T candidate) {
        if (candidate == null || pending != candidate) return false;
        pending = null;
        retire(candidate);
        return true;
    }

    boolean failActive(T candidate) {
        if (candidate == null || active != candidate) return false;
        active = null;
        retire(candidate);
        return true;
    }

    void closeAll() {
        T closingActive = active;
        T closingPending = pending;
        active = null;
        pending = null;
        retire(closingPending);
        if (closingActive != closingPending) retire(closingActive);
    }

    private void retire(T candidate) {
        if (candidate != null) retire.accept(candidate);
    }
}
