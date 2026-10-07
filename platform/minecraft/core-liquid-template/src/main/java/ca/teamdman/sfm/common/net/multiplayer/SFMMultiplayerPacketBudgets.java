package ca.teamdman.sfm.common.net.multiplayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;

/** Fixed server-tick windows. No disconnect/reset method: reconnection cannot refill quotas. */
public final class SFMMultiplayerPacketBudgets {
    public record Limit(int operations, int bytes) {
        public Limit {
            if (operations <= 0 || bytes <= 0) throw new IllegalArgumentException("Non-positive quota");
        }
    }

    public record Limits(
            int windowTicks, int maximumKeys, Limit global, Limit player,
            Limit action, Limit program, Limit channel
    ) {
        public Limits {
            if (windowTicks <= 0 || maximumKeys < 2) throw new IllegalArgumentException("Invalid quota bounds");
            Objects.requireNonNull(global);
            Objects.requireNonNull(player);
            Objects.requireNonNull(action);
            Objects.requireNonNull(program);
            Objects.requireNonNull(channel);
        }

        public static Limits defaults() {
            return new Limits(20, 4_096,
                    new Limit(4_096, 1024 * 1024), new Limit(128, 128 * 1024),
                    new Limit(64, 64 * 1024), new Limit(64, 64 * 1024),
                    new Limit(32, 32 * 1024));
        }
    }

    private enum Kind { GLOBAL, PLAYER, ACTION, PROGRAM, CHANNEL }
    private record Key(Kind kind, UUID player, Object discriminator) {}
    private record Charge(Key key, Limit limit) {}
    private record Window(long number, int operations, int bytes) {}
    private final Limits limits;
    private final Map<Key, Window> windows = new HashMap<>();
    private long highWindow = -1;

    public SFMMultiplayerPacketBudgets(Limits limits) { this.limits = Objects.requireNonNull(limits); }

    /** Every valid-session attempt is charged, including denied ACLs or malformed value contents. */
    public synchronized boolean reserveAttempt(UUID player, long serverTick, int bytes) {
        Objects.requireNonNull(player);
        return reserve(List.of(
                new Charge(new Key(Kind.GLOBAL, null, null), limits.global()),
                new Charge(new Key(Kind.PLAYER, player, null), limits.player())
        ), serverTick, bytes);
    }

    /** Called only after authority checks, so arbitrary untrusted scope churn cannot allocate keys. */
    public synchronized boolean reserveOperation(
            UUID player, Action action, Optional<ManagerAddress> program,
            Optional<InboxScope> channel, long serverTick, int bytes
    ) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(action);
        List<Charge> charges = new ArrayList<>();
        charges.add(new Charge(new Key(Kind.ACTION, player, action), limits.action()));
        program.ifPresent(address -> charges.add(new Charge(new Key(Kind.PROGRAM, player, address), limits.program())));
        channel.ifPresent(address -> charges.add(new Charge(new Key(Kind.CHANNEL, player, address), limits.channel())));
        return reserve(charges, serverTick, bytes);
    }

    private boolean reserve(List<Charge> charges, long tick, int bytes) {
        if (tick < 0 || bytes < 0 || bytes > MAX_FRAME_BYTES) return false;
        long number = Math.max(highWindow, tick / limits.windowTicks());
        if (number > highWindow) {
            highWindow = number;
            windows.values().removeIf(window -> window.number() < number);
        }
        int newKeys = 0;
        for (Charge charge : charges) {
            Window current = windows.get(charge.key());
            if (current == null) newKeys++;
            if (bytes > charge.limit().bytes()
                || (current != null && (current.operations() >= charge.limit().operations()
                    || current.bytes() > charge.limit().bytes() - bytes))) return false;
        }
        if (windows.size() + newKeys > limits.maximumKeys()) return false;
        for (Charge charge : charges) {
            Window old = windows.getOrDefault(charge.key(), new Window(number, 0, 0));
            windows.put(charge.key(), new Window(number, old.operations() + 1, old.bytes() + bytes));
        }
        return true;
    }
}
