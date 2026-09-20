package ca.teamdman.sfm.common.program.signature;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** One server-session ledger, never one unbounded collection per loaded block and never persisted. */
public final class ClientManagerSigningSession {
    public static final int MAX_PENDING = 128;
    public static final int MAX_PENDING_PER_PLAYER = 8;
    public static final int MAX_PENDING_PER_MANAGER = 16;
    public static final int MAX_PENDING_BYTES = 2 * 1024 * 1024;
    public static final long CHALLENGE_TTL_TICKS = 2400;
    private record Key(UUID player, UUID incarnation) { }
    private record Pending(ClientManagerSigningAcknowledgement acknowledgement, int bytes, String address) { }
    private final Map<Key, Pending> pending = new HashMap<>();
    private final Supplier<UUID> challenges;

    public ClientManagerSigningSession() { this(UUID::randomUUID); }
    ClientManagerSigningSession(Supplier<UUID> challenges) { this.challenges = Objects.requireNonNull(challenges); }

    public synchronized Optional<ClientManagerSigningAcknowledgement> issue(
            UUID player, ClientManagerSigningSnapshot snapshot, ProgramSignatureDescriptor descriptor, long tick) {
        return issue(player, snapshot, descriptor, tick, "");
    }

    /** Address is server-derived; copied block NBT must not move a pending review to another location. */
    public synchronized Optional<ClientManagerSigningAcknowledgement> issue(
            UUID player, ClientManagerSigningSnapshot snapshot, ProgramSignatureDescriptor descriptor,
            long tick, String address) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(address);
        ProgramAttestationCodec.require(address.length() <= 512);
        if (tick < 0 || tick > Long.MAX_VALUE - CHALLENGE_TTL_TICKS) return Optional.empty();
        prune(tick);
        Key key = new Key(player, snapshot.incarnation());
        pending.remove(key); // Repeated review replaces this player's challenge, never accumulates prompts.
        var acknowledgement = new ClientManagerSigningAcknowledgement(snapshot, descriptor,
                Objects.requireNonNull(challenges.get()), tick + CHALLENGE_TTL_TICKS);
        int bytes = acknowledgement.chargedBytes();
        long totalBytes = pending.values().stream().mapToLong(Pending::bytes).sum();
        if (pending.size() >= MAX_PENDING || bytes > MAX_PENDING_BYTES - totalBytes
            || pending.keySet().stream().filter(entry -> entry.player().equals(player)).count() >= MAX_PENDING_PER_PLAYER
            || pending.keySet().stream().filter(entry -> entry.incarnation().equals(snapshot.incarnation())).count()
            >= MAX_PENDING_PER_MANAGER) return Optional.empty();
        pending.put(key, new Pending(acknowledgement, bytes, address));
        return Optional.of(acknowledgement);
    }

    /** Every submission consumes its own challenge, including malformed/stale/forged attempts. */
    public synchronized Optional<ClientManagerSigningAcknowledgement> take(UUID player, UUID incarnation,
                                                                           UUID challenge, long tick) {
        return take(player, incarnation, challenge, tick, "");
    }

    public synchronized Optional<ClientManagerSigningAcknowledgement> take(UUID player, UUID incarnation,
                                                                           UUID challenge, long tick, String address) {
        if (tick < 0) return Optional.empty();
        prune(tick);
        Pending found = pending.remove(new Key(player, incarnation));
        if (found == null || !found.address().equals(address)
            || !found.acknowledgement().challenge().equals(challenge)) return Optional.empty();
        return Optional.of(found.acknowledgement());
    }

    public synchronized void invalidate(UUID incarnation) {
        pending.keySet().removeIf(key -> key.incarnation().equals(incarnation));
    }
    public synchronized void forgetPlayer(UUID player) { pending.keySet().removeIf(key -> key.player().equals(player)); }
    public synchronized void clear() { pending.clear(); }
    public synchronized int pendingCount() { return pending.size(); }
    private void prune(long tick) { pending.values().removeIf(value -> tick >= value.acknowledgement().expiresAtTick()); }
}
