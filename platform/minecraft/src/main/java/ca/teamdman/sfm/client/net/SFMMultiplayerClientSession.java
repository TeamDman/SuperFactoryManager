package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.client.program.ClientProgramWorldIdentity;
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.net.SFMPacketInventoryInserter;
import ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol;
import ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketWire;
import ca.teamdman.sfm.common.program.signature.ClientManagerSigningSnapshot;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.LongSupplier;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;

/**
 * Unregistered, IO-free client protocol model. Constructing it never opens a connection,
 * changes a private-world gate, registers a receiver, grants permission or sends a packet.
 * A future adapter must separately enforce the shared local authorizer and exact live caller.
 */
public final class SFMMultiplayerClientSession {
    public static final int MAX_PENDING = 128;
    public static final int MAX_HISTORY = 128;
    public static final long ACK_TIMEOUT_MILLIS = 30_000;
    public enum State { ABSENT, OFFERED, NEGOTIATING, READY, FAILED }
    public record Outbound(long sequence, byte[] frame) {
        public Outbound { frame = frame.clone(); }
        @Override public byte[] frame() { return frame.clone(); }
    }
    public record Receipt(UUID session, long sequence, String operation, Status status,
                          Optional<SFMPacketInventoryInserter.Result> insertion) {}
    private record Pending(String operation, long createdAt) {}

    private final LongSupplier clock;
    private final Map<Long, Pending> pending = new LinkedHashMap<>();
    private final ArrayDeque<Receipt> history = new ArrayDeque<>();
    private Object connection;
    private String endpoint;
    private UUID player;
    private ResourceLocation dimension;
    private SFMMultiplayerPacketWire.SessionOffer offer;
    private State state = State.ABSENT;
    private String diagnostic = "No negotiated remote session";
    private long nextSequence = 1;
    private long highClock;
    private long negotiationStartedAt;

    public SFMMultiplayerClientSession(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }

    /** Connection comparison is reference identity, never equals(); dimensions have independent wire sessions. */
    public synchronized void bind(Object connection, String endpoint, UUID player, ResourceLocation dimension) {
        Objects.requireNonNull(connection);
        String normalized = new ClientProgramWorldIdentity(endpoint, new UUID(0, 0)).serverEndpoint();
        Objects.requireNonNull(player);
        Objects.requireNonNull(dimension);
        if (this.connection == connection && Objects.equals(this.endpoint, normalized)
                && Objects.equals(this.player, player) && Objects.equals(this.dimension, dimension)) return;
        clear();
        this.connection = connection;
        this.endpoint = normalized;
        this.player = player;
        this.dimension = dimension;
    }

    public synchronized void clear() {
        connection = null; endpoint = null; player = null; dimension = null; offer = null;
        state = State.ABSENT;
        diagnostic = "No negotiated remote session";
        nextSequence = 1;
        highClock = 0;
        pending.clear();
        history.clear();
    }

    public synchronized boolean acceptOffer(Object expectedConnection, SFMMultiplayerPacketWire.SessionOffer incoming) {
        Objects.requireNonNull(incoming);
        if (!current(expectedConnection)) return false;
        Offer advertised = incoming.offer();
        if (advertised.protocolVersion() != VERSION || advertised.maximumFrameBytes() < 64
                || advertised.maximumFrameBytes() > MAX_FRAME_BYTES || advertised.maximumValueBytes() <= 0
                || advertised.maximumValueBytes() > SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES
                || advertised.session() == null || incoming.world() == null) {
            fail("Unsupported remote protocol offer");
            return false;
        }
        if (offer != null && advertised.session().equals(offer.offer().session())) {
            // An already acknowledged nonce can never be reset to sequence one by a duplicate offer.
            return incoming.equals(offer) && state != State.FAILED;
        }
        offer = incoming;
        pending.clear();
        nextSequence = 1;
        state = State.OFFERED;
        diagnostic = "Remote offer awaits explicit negotiation";
        return true;
    }

    public synchronized Optional<Outbound> negotiation() {
        if (state != State.OFFERED || offer == null) return Optional.empty();
        state = State.NEGOTIATING;
        negotiationStartedAt = now();
        diagnostic = "Awaiting remote negotiation acknowledgement";
        return Optional.of(new Outbound(0, SFMMultiplayerPacketWire.negotiate(VERSION, offer.offer().session())));
    }

    public synchronized boolean acceptResult(Object expectedConnection, Acknowledgement acknowledgement) {
        Objects.requireNonNull(acknowledgement);
        expire();
        if (!current(expectedConnection) || offer == null || !offer.offer().session().equals(acknowledgement.session())) return false;
        if (acknowledgement.sequence() == 0) {
            if (state != State.NEGOTIATING) return false;
            remember(new Receipt(acknowledgement.session(), 0, "negotiate", acknowledgement.status(), acknowledgement.insertion()));
            if (acknowledgement.status() == Status.NEGOTIATED) {
                state = State.READY;
                diagnostic = "Negotiated; exact server ACL still required";
            } else fail("Remote negotiation rejected: " + acknowledgement.status());
            return true;
        }
        if (state != State.READY) return false;
        Pending attempt = pending.remove(acknowledgement.sequence());
        if (attempt == null) return false;
        boolean wrongSuccess = acknowledgement.status() == Status.NEGOTIATED
                || acknowledgement.status() == Status.INSERTION_ATTEMPTED && !attempt.operation().equals("insert")
                || acknowledgement.status() == Status.SUBSCRIBED && !attempt.operation().equals("subscribe")
                || acknowledgement.status() == Status.UNSUBSCRIBED && !attempt.operation().equals("unsubscribe")
                || acknowledgement.status() == Status.DELIVERED_TO_TRANSPORT;
        if (wrongSuccess) { fail("Remote acknowledgement has the wrong operation kind"); return false; }
        remember(new Receipt(acknowledgement.session(), acknowledgement.sequence(), attempt.operation(), acknowledgement.status(), acknowledgement.insertion()));
        diagnostic = acknowledgement.status() + "; transport acknowledgement is not downstream processing";
        // Valid quota-rejected requests consume their sequence; only a new explicit request may follow.
        if (Set.of(Status.OUT_OF_ORDER, Status.REPLAYED_REQUEST, Status.STALE_SESSION,
                Status.NOT_NEGOTIATED, Status.FRAME_REJECTED, Status.UNSUPPORTED_PROTOCOL).contains(acknowledgement.status())) {
            fail("Remote sequence cannot be confirmed after " + acknowledgement.status() + "; a fresh server session is required");
        }
        return true;
    }

    public synchronized Optional<Outbound> insertion(SFMPacketInventoryAddress target, SFMValue value,
                                                     Optional<ProgramClaim> caller) {
        Objects.requireNonNull(target); Objects.requireNonNull(value); Objects.requireNonNull(caller);
        if (!Objects.equals(target.dimension(), dimension) || !validCaller(caller)
                || !BlockPos.of(target.position().asLong()).equals(target.position())) return Optional.empty();
        if (!readyForAttempt()) return Optional.empty();
        try {
            if (SFMValueJsonCodec.encode(value).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    > offer.offer().maximumValueBytes()) return Optional.empty();
            return outgoing("insert", SFMMultiplayerPacketWire.insert(offer.offer().session(), nextSequence, target, caller, value));
        } catch (RuntimeException invalid) { diagnostic = "Remote insertion input rejected locally"; return Optional.empty(); }
    }

    public synchronized Optional<Outbound> subscription(UUID localInboxSession, SFMClientInboxAddress address,
                                                        Optional<ProgramClaim> caller, boolean subscribe) {
        Objects.requireNonNull(localInboxSession); Objects.requireNonNull(address); Objects.requireNonNull(caller);
        if (!address.recipient().equals(player) || !address.dimension().equals(dimension)
                || !validCaller(caller) || !readyForAttempt()) return Optional.empty();
        try {
            return outgoing(subscribe ? "subscribe" : "unsubscribe", SFMMultiplayerPacketWire.subscription(
                    offer.offer().session(), nextSequence, localInboxSession, address, caller, subscribe));
        } catch (RuntimeException invalid) { diagnostic = "Remote subscription input rejected locally"; return Optional.empty(); }
    }

    private boolean validCaller(Optional<ProgramClaim> caller) {
        return caller.isEmpty() || caller.orElseThrow().manager().dimension().equals(dimension)
                && BlockPos.of(caller.orElseThrow().manager().position().asLong()).equals(caller.orElseThrow().manager().position());
    }
    private boolean readyForAttempt() {
        expire();
        if (state != State.READY || offer == null) return false;
        if (pending.size() >= MAX_PENDING) { diagnostic = "Remote pending acknowledgement capacity reached"; return false; }
        if (nextSequence == Long.MAX_VALUE) { fail("Remote sequence exhausted; a fresh server session is required"); return false; }
        return true;
    }
    private Optional<Outbound> outgoing(String operation, byte[] frame) {
        if (frame.length > offer.offer().maximumFrameBytes()) { diagnostic = "Remote frame exceeds negotiated limit"; return Optional.empty(); }
        long sequence = nextSequence++;
        pending.put(sequence, new Pending(operation, now()));
        return Optional.of(new Outbound(sequence, frame));
    }

    /** A failed write has ambiguous server receipt. Never resend automatically or guess a sequence. */
    public synchronized void transportFailed(long sequence) {
        if (sequence == 0 && state == State.NEGOTIATING || pending.containsKey(sequence)) {
            fail("Remote transport write failed; a fresh server session is required");
        }
    }
    public synchronized boolean acceptsInbox(Object expectedConnection, SFMMultiplayerPacketWire.InboxValue incoming, UUID localInboxSession) {
        expire();
        return state == State.READY && current(expectedConnection) && offer != null
                && offer.offer().session().equals(incoming.session()) && localInboxSession.equals(incoming.localInboxSession())
                && incoming.address().recipient().equals(player) && incoming.address().dimension().equals(dimension);
    }
    public synchronized Optional<ClientProgramWorldIdentity> worldIdentity(UUID persistedWorldId) {
        return state == State.READY && offer != null && offer.world().equals(persistedWorldId)
                ? Optional.of(new ClientProgramWorldIdentity(endpoint, persistedWorldId)) : Optional.empty();
    }
    public synchronized Optional<UUID> session() {
        return state == State.READY && offer != null ? Optional.of(offer.offer().session()) : Optional.empty();
    }
    public synchronized State state() { expire(); return state; }
    public synchronized String diagnostic() { expire(); return diagnostic; }
    public synchronized int pendingCount() { expire(); return pending.size(); }
    public synchronized List<Receipt> acknowledgements() { return List.copyOf(history); }
    private boolean current(Object expected) { return expected != null && connection == expected; }
    private void remember(Receipt value) { if (history.size() == MAX_HISTORY) history.removeFirst(); history.addLast(value); }
    private void fail(String reason) { state = State.FAILED; diagnostic = reason; pending.clear(); }
    private long now() {
        long current = clock.getAsLong();
        if (current < 0) throw new IllegalStateException("Invalid client monotonic clock");
        highClock = Math.max(highClock, current);
        return highClock;
    }
    private void expire() {
        if (state == State.NEGOTIATING && now() - negotiationStartedAt >= ACK_TIMEOUT_MILLIS) {
            fail("Remote negotiation acknowledgement expired; no automatic retry");
        }
        if (pending.isEmpty()) return;
        long now = now();
        if (pending.values().stream().anyMatch(value -> now - value.createdAt() >= ACK_TIMEOUT_MILLIS)) {
            fail("Remote acknowledgement expired; no automatic retry; a fresh server session is required");
        }
    }

    /** All labels in the acknowledged body participate; referenced-label consent hashes are intentionally not accepted here. */
    public static ProgramClaim claimFromSnapshot(ResourceLocation dimension, BlockPos position, ClientManagerSigningSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        return new ProgramClaim(new ManagerAddress(dimension, position), snapshot.incarnation(), snapshot.revision(),
                snapshot.body().sourceSha256(), snapshot.body().bindingSha256());
    }
}
