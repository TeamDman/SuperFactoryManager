package ca.teamdman.sfm.common.net.multiplayer;

import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.net.SFMPacketInventoryInserter;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;

/**
 * Injectable server-side admission core. The adapter owns authenticated connection lookup and
 * server-thread dispatch; no client-supplied principal, permission manifest or callback is trusted.
 * This class does not register packets, enable remote worlds, load chunks or persist operator ACLs.
 */
public final class SFMMultiplayerPacketTransport {
    public static final int MAX_CONNECTIONS = 128;
    public static final int MAX_SUBSCRIPTIONS = 16;
    public static final long NEGOTIATION_TIMEOUT_MILLIS = 30_000;

    @FunctionalInterface
    public interface ManagerResolver {
        Optional<ObservedProgram> find(UUID authenticatedPlayer, ManagerAddress address);
    }

    @FunctionalInterface
    public interface InsertionEffect {
        SFMPacketInventoryInserter.Result insert(SFMPacketInventoryAddress target, SFMValue value);
    }

    @FunctionalInterface
    public interface DeliveryEffect { boolean offer(Delivery delivery); }

    public record Delivery(UUID session, UUID recipient, InboxScope inbox, SFMValue value) {}
    public record Diagnostics(Map<Status, Long> outcomes, int connections, int subscriptions) {}
    private record Subscription(Optional<ProgramClaim> program) {}

    /** Opaque server-owned handle. The player and identity originate from the actual connection. */
    public static final class Connection {
        private final UUID player;
        private final Object identity;
        private final UUID session;
        private final long createdAt;
        private final Map<InboxScope, Subscription> subscriptions = new HashMap<>();
        private boolean negotiated;
        private long lastSequence;

        private Connection(UUID player, Object identity, UUID session, long createdAt) {
            this.player = player;
            this.identity = identity;
            this.session = session;
            this.createdAt = createdAt;
        }

        public UUID player() { return player; }
        public UUID session() { return session; }
        public Offer offer() {
            return new Offer(VERSION, session, MAX_FRAME_BYTES, SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES);
        }
    }

    private final SFMMultiplayerPacketPolicy policy;
    private final SFMMultiplayerPacketBudgets budgets;
    private final ManagerResolver managers;
    private final LongSupplier serverTick;
    private final LongSupplier epochMillis;
    private final UUID sessionSeed;
    private final Map<UUID, Connection> players = new HashMap<>();
    private final Map<Object, Connection> connections = new IdentityHashMap<>();
    private final Map<Status, Long> outcomes = new EnumMap<>(Status.class);
    private long nextSession;
    private long highEpochMillis;

    public SFMMultiplayerPacketTransport(
            SFMMultiplayerPacketPolicy policy, SFMMultiplayerPacketBudgets budgets,
            ManagerResolver managers, LongSupplier serverTick, LongSupplier epochMillis, UUID sessionSeed
    ) {
        this.policy = Objects.requireNonNull(policy);
        this.budgets = Objects.requireNonNull(budgets);
        this.managers = Objects.requireNonNull(managers);
        this.serverTick = Objects.requireNonNull(serverTick);
        this.epochMillis = Objects.requireNonNull(epochMillis);
        this.sessionSeed = Objects.requireNonNull(sessionSeed);
    }

    /**
     * Only the adapter's authenticated login/reconnect handler may call this. Session IDs are
     * correlation nonces, not bearer authority: every operation also requires this exact handle.
     * Use a fresh random seed for each transport lifetime; the counter never wraps or recycles.
     */
    public synchronized Optional<Connection> openConnection(UUID player, Object connectionIdentity) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(connectionIdentity);
        Connection existing = connections.get(connectionIdentity);
        if (existing != null) {
            if (!existing.player.equals(player)) throw new IllegalArgumentException("Connection player changed");
            return Optional.of(existing);
        }
        if (nextSession == Long.MAX_VALUE || (!players.containsKey(player) && players.size() >= MAX_CONNECTIONS)) {
            return Optional.empty();
        }
        Connection previous = players.get(player);
        if (previous != null) close(previous);
        UUID session = new UUID(sessionSeed.getMostSignificantBits(), sessionSeed.getLeastSignificantBits() ^ ++nextSession);
        Connection connection = new Connection(player, connectionIdentity, session, now());
        players.put(player, connection);
        connections.put(connectionIdentity, connection);
        return Optional.of(connection);
    }

    /** Logout and dimension transition invalidate subscriptions, but never replenish quotas. */
    public synchronized void close(Connection connection) {
        Objects.requireNonNull(connection);
        players.remove(connection.player, connection);
        connections.remove(connection.identity, connection);
        connection.subscriptions.clear();
        connection.negotiated = false;
    }

    public synchronized Status negotiate(Connection connection, int version, UUID offeredSession) {
        if (!live(connection)) return record(Status.STALE_SESSION);
        if (!budgets.reserveAttempt(connection.player, serverTick.getAsLong(), 64)) return record(Status.RATE_LIMITED);
        if (!connection.session.equals(offeredSession)) return record(Status.STALE_SESSION);
        if (!connection.negotiated && now() - connection.createdAt >= NEGOTIATION_TIMEOUT_MILLIS) {
            close(connection);
            return record(Status.STALE_SESSION);
        }
        if (version != VERSION) return record(Status.UNSUPPORTED_PROTOCOL);
        connection.negotiated = true;
        return record(Status.NEGOTIATED);
    }

    /** Charges a malformed bounded frame that could not yield a Request header. Never decodes it. */
    public synchronized Status rejectMalformedFrame(Connection connection, int receivedFrameBytes) {
        if (!live(connection)) return record(Status.STALE_SESSION);
        if (receivedFrameBytes <= 0 || receivedFrameBytes > MAX_FRAME_BYTES) return record(Status.FRAME_REJECTED);
        return record(budgets.reserveAttempt(connection.player, serverTick.getAsLong(), receivedFrameBytes)
                ? Status.FRAME_REJECTED : Status.RATE_LIMITED);
    }

    public synchronized Acknowledgement insert(
            Connection connection, Request request, InventoryScope target,
            Optional<ProgramClaim> program, PayloadSource payload, InsertionEffect effect
    ) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(program);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(effect);
        Status failure = begin(connection, request);
        if (failure == null) failure = payload.preflight(request.receivedFrameBytes());
        if (failure == null) failure = authorize(connection, Action.PACKET_SEND, target, program);
        if (failure == null && !operationBudget(connection, Action.PACKET_SEND, program.map(ProgramClaim::manager),
                Optional.empty(), request.receivedFrameBytes())) failure = Status.RATE_LIMITED;
        if (failure != null) return acknowledge(request, failure);
        SFMValue value;
        try { value = payload.decode(); }
        catch (RuntimeException invalid) { return acknowledge(request, Status.PAYLOAD_REJECTED); }
        // Injected readers/resolvers can run arbitrary server code. Recheck before the actual effect.
        failure = authorize(connection, Action.PACKET_SEND, target, program);
        if (failure != null) return acknowledge(request, failure);
        SFMPacketInventoryInserter.Result inserted;
        try { inserted = Objects.requireNonNull(effect.insert(target.target(), value)); }
        catch (RuntimeException failedInsertion) { inserted = SFMPacketInventoryInserter.Result.ITEM_HANDLER_ERROR; }
        record(Status.INSERTION_ATTEMPTED);
        return new Acknowledgement(request.session(), request.sequence(), Status.INSERTION_ATTEMPTED, Optional.of(inserted));
    }

    public synchronized Acknowledgement subscribe(
            Connection connection, Request request, UUID requestedRecipient,
            InboxScope inbox, Optional<ProgramClaim> program
    ) {
        Objects.requireNonNull(inbox);
        Objects.requireNonNull(program);
        Status failure = begin(connection, request);
        if (failure == null && !connection.player.equals(requestedRecipient)) failure = Status.RECIPIENT_REJECTED;
        if (failure == null) failure = authorize(connection, Action.INBOX_SUBSCRIBE, inbox, program);
        if (failure == null && !operationBudget(connection, Action.INBOX_SUBSCRIBE, program.map(ProgramClaim::manager),
                Optional.of(inbox), request.receivedFrameBytes())) failure = Status.RATE_LIMITED;
        if (failure == null && !connection.subscriptions.containsKey(inbox)
            && connection.subscriptions.size() >= MAX_SUBSCRIPTIONS) failure = Status.SUBSCRIPTION_CAPACITY;
        if (failure != null) return acknowledge(request, failure);
        connection.subscriptions.put(inbox, new Subscription(program));
        return acknowledge(request, Status.SUBSCRIBED);
    }

    /** Releasing one's own subscription remains possible after the grant is revoked. */
    public synchronized Acknowledgement unsubscribe(
            Connection connection, Request request, UUID requestedRecipient, InboxScope inbox
    ) {
        Objects.requireNonNull(inbox);
        Status failure = begin(connection, request);
        if (failure == null && !connection.player.equals(requestedRecipient)) failure = Status.RECIPIENT_REJECTED;
        if (failure != null) return acknowledge(request, failure);
        connection.subscriptions.remove(inbox);
        return acknowledge(request, Status.UNSUBSCRIBED);
    }

    /**
     * Called from trusted server SFML with its actual publisher position. A subscription alone
     * never authorizes publication. Success means handed to the transport, not client receipt.
     */
    public synchronized Status publish(
            Connection connection, UUID expectedSession, DeliveryScope source,
            int receivedFrameBytes, PayloadSource payload, DeliveryEffect effect
    ) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(effect);
        if (!live(connection) || !connection.session.equals(expectedSession)) return record(Status.STALE_SESSION);
        if (!connection.negotiated) return record(Status.NOT_NEGOTIATED);
        Status failure = payload.preflight(receivedFrameBytes);
        if (failure != null) return record(failure);
        if (!budgets.reserveAttempt(connection.player, serverTick.getAsLong(), receivedFrameBytes)) return record(Status.RATE_LIMITED);
        failure = authorizeDelivery(connection, source);
        if (failure == null && !operationBudget(connection, Action.INBOX_DELIVER, Optional.of(source.publisher()),
                Optional.of(source.inbox()), receivedFrameBytes)) failure = Status.RATE_LIMITED;
        if (failure != null) return record(failure);
        SFMValue value;
        try { value = payload.decode(); }
        catch (RuntimeException invalid) { return record(Status.PAYLOAD_REJECTED); }
        failure = authorizeDelivery(connection, source);
        if (failure != null) return record(failure);
        try {
            return record(effect.offer(new Delivery(connection.session, connection.player, source.inbox(), value))
                    ? Status.DELIVERED_TO_TRANSPORT : Status.DELIVERY_FAILED);
        } catch (RuntimeException failedSend) { return record(Status.DELIVERY_FAILED); }
    }

    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(Map.copyOf(outcomes), connections.size(),
                connections.values().stream().mapToInt(connection -> connection.subscriptions.size()).sum());
    }

    public synchronized boolean isActive(Connection connection) { return live(connection); }

    public synchronized boolean isNegotiated(Connection connection) {
        return live(connection) && connection.negotiated;
    }

    public synchronized java.util.Set<InboxScope> subscribedInboxes(Connection connection) {
        return live(connection) ? java.util.Set.copyOf(connection.subscriptions.keySet()) : java.util.Set.of();
    }

    private Status begin(Connection connection, Request request) {
        Objects.requireNonNull(request);
        if (!live(connection)) return Status.STALE_SESSION;
        if (request.receivedFrameBytes() <= 0 || request.receivedFrameBytes() > MAX_FRAME_BYTES) return Status.FRAME_REJECTED;
        boolean admitted = budgets.reserveAttempt(connection.player, serverTick.getAsLong(), request.receivedFrameBytes());
        if (!connection.session.equals(request.session())) return Status.STALE_SESSION;
        if (!connection.negotiated) return Status.NOT_NEGOTIATED;
        if (request.sequence() <= connection.lastSequence) return Status.REPLAYED_REQUEST;
        if (connection.lastSequence == Long.MAX_VALUE || request.sequence() != connection.lastSequence + 1) return Status.OUT_OF_ORDER;
        // Every correctly sequenced live request consumes its sequence, including either quota boundary.
        // A client may send a NEW operation next window, but never retry an uncertain prior operation.
        connection.lastSequence = request.sequence();
        return admitted ? null : Status.RATE_LIMITED;
    }

    private Status authorize(Connection connection, Action action, Scope scope, Optional<ProgramClaim> program) {
        if (!live(connection)) return Status.STALE_SESSION;
        if (!connection.negotiated) return Status.NOT_NEGOTIATED;
        if (!policy.allows(connection.player, action, scope, program, now())) return Status.AUTHORITY_DENIED;
        if (program.isEmpty()) return null;
        ProgramClaim claim = program.orElseThrow();
        try {
            Optional<ObservedProgram> observed = managers.find(connection.player, claim.manager());
            if (observed.isEmpty() || !observed.orElseThrow().identity().equals(claim)
                || !observed.orElseThrow().operations().contains(new ProgramOperation(action, scope))) {
                return Status.PROGRAM_REJECTED;
            }
        } catch (RuntimeException lookupFailed) { return Status.PROGRAM_REJECTED; }
        // A matching hash is not proof of execution and never substitutes for the player ACL.
        return live(connection) && policy.allows(connection.player, action, scope, program, now())
                ? null : Status.AUTHORITY_DENIED;
    }

    private Status authorizeDelivery(Connection connection, DeliveryScope source) {
        if (!live(connection)) return Status.STALE_SESSION;
        Subscription subscription = connection.subscriptions.get(source.inbox());
        if (subscription == null) return Status.NOT_SUBSCRIBED;
        Status failure = authorize(connection, Action.INBOX_SUBSCRIBE, source.inbox(), subscription.program());
        if (failure != null) {
            connection.subscriptions.remove(source.inbox());
            return failure;
        }
        return policy.allows(connection.player, Action.INBOX_DELIVER, source, Optional.empty(), now())
                ? null : Status.AUTHORITY_DENIED;
    }

    private boolean operationBudget(Connection connection, Action action, Optional<ManagerAddress> program,
                                    Optional<InboxScope> channel, int bytes) {
        return budgets.reserveOperation(connection.player, action, program, channel, serverTick.getAsLong(), bytes);
    }

    private boolean live(Connection connection) {
        return connection != null && players.get(connection.player) == connection
                && connections.get(connection.identity) == connection;
    }

    private long now() {
        long current = epochMillis.getAsLong();
        if (current < 0) throw new IllegalStateException("Negative server wall clock");
        highEpochMillis = Math.max(highEpochMillis, current);
        return highEpochMillis;
    }

    private Acknowledgement acknowledge(Request request, Status status) {
        return new Acknowledgement(request.session(), request.sequence(), record(status), Optional.empty());
    }

    private Status record(Status status) {
        long count = outcomes.getOrDefault(status, 0L);
        outcomes.put(status, count == Long.MAX_VALUE ? count : count + 1);
        return status;
    }
}
