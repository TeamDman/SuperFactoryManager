package ca.teamdman.sfm.common.net;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Per-principal operation and byte limits in caller-selected time windows. */
public final class SFMBoundedEffectBudget {
    public static final int MAX_TRACKED_PRINCIPALS = 1_024;

    public enum Result { ALLOWED, OPERATIONS_EXHAUSTED, BYTES_EXHAUSTED, REQUEST_TOO_LARGE, PRINCIPAL_CAPACITY_EXHAUSTED }

    private record Window(long number, int operations, int bytes) {}

    private final int maxOperations;
    private final int maxBytes;
    private final Map<UUID, Window> windows = new HashMap<>();

    public SFMBoundedEffectBudget(int maxOperations, int maxBytes) {
        if (maxOperations <= 0 || maxBytes <= 0) {
            throw new IllegalArgumentException("Effect budget limits must be positive");
        }
        this.maxOperations = maxOperations;
        this.maxBytes = maxBytes;
    }

    /** Failed attempts consume no budget; admitted attempts do, even if downstream insertion fails. */
    public synchronized Result reserve(UUID principal, long window, int requestedBytes) {
        Objects.requireNonNull(principal, "principal");
        if (requestedBytes < 0 || requestedBytes > maxBytes) return Result.REQUEST_TOO_LARGE;
        Window current = windows.get(principal);
        if (current == null && windows.size() >= MAX_TRACKED_PRINCIPALS) {
            windows.values().removeIf(previous -> previous.number() < window);
            // Evicting an active principal would let identity churn reset its cap.
            if (windows.size() >= MAX_TRACKED_PRINCIPALS) return Result.PRINCIPAL_CAPACITY_EXHAUSTED;
        }
        if (current == null || current.number() < window) current = new Window(window, 0, 0);
        if (current.operations() >= maxOperations) return Result.OPERATIONS_EXHAUSTED;
        if (current.bytes() > maxBytes - requestedBytes) return Result.BYTES_EXHAUSTED;
        windows.put(principal, new Window(current.number(), current.operations() + 1, current.bytes() + requestedBytes));
        return Result.ALLOWED;
    }

    public synchronized void clear() {
        windows.clear();
    }

    public synchronized void remove(UUID principal) {
        windows.remove(Objects.requireNonNull(principal));
    }
}
