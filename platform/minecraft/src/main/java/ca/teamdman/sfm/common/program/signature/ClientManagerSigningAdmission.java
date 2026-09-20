package ca.teamdman.sfm.common.program.signature;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Aggregate server-session work/traffic budget, shared across every manager and operation. */
public final class ClientManagerSigningAdmission {
    public static final long WINDOW_TICKS = 20;
    public static final int MAX_OPERATIONS_PER_PLAYER = 4;
    public static final int MAX_OPERATIONS_GLOBAL = 16;
    public static final int MAX_BYTES_PER_PLAYER = 512 * 1024;
    public static final int MAX_BYTES_GLOBAL = 2 * 1024 * 1024;
    private record Usage(int operations, int bytes) { }
    private final Map<UUID, Usage> players = new HashMap<>();
    private long windowStart = -1;
    private int operations;
    private int bytes;

    /** Charge request bytes plus the maximum possible response before parsing/verifying public keys. */
    public synchronized boolean reserve(UUID player, long tick, int chargedBytes) {
        Objects.requireNonNull(player);
        if (tick < 0 || chargedBytes < 0 || chargedBytes > MAX_BYTES_PER_PLAYER) return false;
        if (windowStart < 0 || tick < windowStart || tick - windowStart >= WINDOW_TICKS) {
            windowStart = tick;
            operations = 0;
            bytes = 0;
            players.clear();
        }
        Usage current = players.getOrDefault(player, new Usage(0, 0));
        if (operations >= MAX_OPERATIONS_GLOBAL || current.operations >= MAX_OPERATIONS_PER_PLAYER
            || chargedBytes > MAX_BYTES_GLOBAL - bytes || chargedBytes > MAX_BYTES_PER_PLAYER - current.bytes) return false;
        players.put(player, new Usage(current.operations + 1, current.bytes + chargedBytes));
        operations++;
        bytes += chargedBytes;
        return true;
    }

    /** Connection churn does not replenish a player's budget inside the current window. */
    public synchronized void clear() {
        players.clear();
        windowStart = -1;
        operations = 0;
        bytes = 0;
    }
}
