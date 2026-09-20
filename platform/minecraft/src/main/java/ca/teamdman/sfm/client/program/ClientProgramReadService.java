package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.client.net.SFMClientInbox;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Bounded live-program read sessions. Visibility is deliberately not part of their lifetime. */
public final class ClientProgramReadService<S> implements AutoCloseable {
    public static final int MAX_SESSIONS = 128;

    public record Context<S>(ClientProgramBlockReadSurface.Source<S> source, UUID recipient,
                             Set<BlockPos> boundPositions, Set<BlockPos> loadedPositions,
                             Set<ResourceLocation> channels, ClientProgramInboxReadSurface.InboxAccess inbox) {
        public Context {
            Objects.requireNonNull(source); Objects.requireNonNull(recipient); Objects.requireNonNull(inbox);
            boundPositions = immutablePositions(boundPositions);
            loadedPositions = immutablePositions(loadedPositions);
            channels = Set.copyOf(channels);
            if (boundPositions.size() + loadedPositions.size() > ClientProgramActionManifest.MAX_SCOPES
                    || channels.size() > SFMClientInbox.MAX_CHANNELS) {
                throw new IllegalArgumentException("Client read session exceeds its scope budget");
            }
        }
        private static Set<BlockPos> immutablePositions(Set<BlockPos> values) {
            Set<BlockPos> result = new HashSet<>();
            values.forEach(value -> result.add(value.immutable()));
            return Set.copyOf(result);
        }
    }

    public interface Environment<S> {
        Optional<Context<S>> open(ClientProgramIdentity identity);
        boolean isCurrent(ClientProgramIdentity identity, Context<S> context);
    }

    public record Observation(int cachedPositions, int subscriptions, long blockProjections, long inboxProjections) {}

    private final ClientProgramConsentGate consent;
    private final ClientProgramConsentGate.Policy policy;
    private final Environment<S> environment;
    private final Map<ClientProgramIdentity, Session> sessions = new HashMap<>();

    public ClientProgramReadService(ClientProgramConsentGate consent, ClientProgramConsentGate.Policy policy,
                                    Environment<S> environment) {
        this.consent = Objects.requireNonNull(consent); this.policy = Objects.requireNonNull(policy);
        this.environment = Objects.requireNonNull(environment);
    }

    public SFMValue block(ClientProgramIdentity identity, BlockPos position, boolean loadedScope, long frameEpoch) {
        Optional<Session> active = session(identity);
        if (active.isEmpty()) return blockResult("unavailable_no_session", Optional.empty());
        Session state = active.orElseThrow();
        if (!(loadedScope ? state.context.loadedPositions() : state.context.boundPositions()).contains(position)) {
            return blockResult("unavailable_out_of_scope", Optional.empty());
        }
        if (state.frameEpoch != frameEpoch) { state.frameEpoch = frameEpoch; state.blocks.beginFrame(); }
        var result = loadedScope ? state.blocks.readLoaded(position) : state.blocks.readBound(position);
        return blockResult(result.status().name().toLowerCase(Locale.ROOT), result.value());
    }

    /** Latest observed value, not a queue drain or delivery acknowledgement. Loss remains explicit. */
    public SFMValue latest(ClientProgramIdentity identity, ResourceLocation channel) {
        Optional<Session> active = session(identity);
        if (active.isEmpty()) return inboxUnavailable("unavailable_no_session");
        Session state = active.orElseThrow();
        if (!state.context.channels().contains(channel)) return inboxUnavailable("unavailable_out_of_scope");
        if (state.inbox == null) state.inbox = state.newInbox();
        Latest previous = state.latest.get(channel);
        Optional<SFMClientInbox.Cursor> cursor = previous == null ? Optional.empty() : Optional.of(previous.cursor);
        var read = state.inbox.page(channel, cursor, SFMClientInbox.MAX_PAGE_SIZE);
        if (read.page().isEmpty()) {
            // Do not expose a stale payload while disconnected. Retain the cursor only to report a session change.
            if (previous != null) state.latest.put(channel, new Latest(previous.cursor, null, 0, -1, -1, null));
            return inboxUnavailable(read.status().name().toLowerCase(Locale.ROOT));
        }
        var page = read.page().orElseThrow();
        if (previous != null && previous.result != null && previous.cursor.equals(page.nextCursor())
                && previous.oldest == page.oldestSequence() && previous.newest == page.newestSequence()) {
            return previous.result;
        }
        boolean sameStream = previous != null && previous.cursor.session().equals(page.nextCursor().session())
                && previous.cursor.stream().equals(page.nextCursor().stream());
        SFMValue value = sameStream ? previous.value : null;
        long sequence = sameStream ? previous.sequence : 0;
        if (!page.entries().isEmpty()) {
            var newest = page.entries().get(page.entries().size() - 1);
            value = newest.value(); sequence = newest.sequence();
        }
        SFMValue result = SFMValue.object(Map.of(
                "status", SFMValue.of(value == null ? "empty" : "value"),
                "value", value == null ? SFMValue.nullValue() : value,
                "sequence", SFMValue.of(sequence), "cursor", cursorValue(page.nextCursor()),
                "continuity", SFMValue.of(page.continuity().name().toLowerCase(Locale.ROOT)),
                "oldestSequence", SFMValue.of(page.oldestSequence()),
                "newestSequence", SFMValue.of(page.newestSequence()), "hasMore", SFMValue.of(page.hasMore())));
        state.latest.put(channel, new Latest(page.nextCursor(), value, sequence,
                page.oldestSequence(), page.newestSequence(), result));
        state.inboxProjections++;
        return result;
    }

    /** Called on client ticks, including when all bound displays are hidden. */
    public void maintain() {
        Iterator<Map.Entry<ClientProgramIdentity, Session>> entries = sessions.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (!current(entry.getKey(), entry.getValue())) { entry.getValue().close(); entries.remove(); }
            else trimRevoked(entry.getKey(), entry.getValue());
        }
    }

    public Optional<Observation> observation(ClientProgramIdentity identity) {
        Session state = sessions.get(identity);
        return state == null ? Optional.empty() : Optional.of(new Observation(state.blocks.cachedPositions(),
                state.inbox == null ? 0 : state.inbox.activeSubscriptions(), state.blockProjections, state.inboxProjections));
    }

    public int activeSessions() { return sessions.size(); }

    @Override public void close() { sessions.values().forEach(Session::close); sessions.clear(); }

    private Optional<Session> session(ClientProgramIdentity identity) {
        Session existing = sessions.get(identity);
        if (existing != null) {
            if (!current(identity, existing)) { existing.close(); sessions.remove(identity); return Optional.empty(); }
            trimRevoked(identity, existing);
            return Optional.of(existing);
        }
        if (!consent.execution(identity, policy).allowed() || sessions.size() >= MAX_SESSIONS) return Optional.empty();
        Optional<Context<S>> context = environment.open(identity);
        if (context.isEmpty() || !environment.isCurrent(identity, context.orElseThrow())) return Optional.empty();
        Session created = new Session(identity, context.orElseThrow());
        if (!current(identity, created)) { created.close(); return Optional.empty(); }
        sessions.put(identity, created);
        return Optional.of(created);
    }

    private boolean current(ClientProgramIdentity identity, Session session) {
        return consent.execution(identity, policy).allowed() && environment.isCurrent(identity, session.context)
                && session.context.source().isActive()
                && session.context.source().worldIdentity() == session.worldIdentity
                && session.context.source().dimension().equals(identity.dimension());
    }

    private void trimRevoked(ClientProgramIdentity identity, Session session) {
        if (!consent.evaluate(identity, ClientProgramInboxReadSurface.READ, policy).allowed()) session.closeInbox();
        for (ResourceLocation capability : List.of(ClientProgramBlockReadSurface.READ_BOUND, ClientProgramBlockReadSurface.READ_LOADED)) {
            if (identity.requestedCapabilities().contains(capability) && !consent.evaluate(identity, capability, policy).allowed()) {
                session.blocks.clear(); break;
            }
        }
    }

    private final class Session {
        final ClientProgramIdentity identity;
        final Context<S> context;
        final Object worldIdentity;
        final ClientProgramBlockReadSurface<S> blocks;
        final Map<ResourceLocation, Latest> latest = new HashMap<>();
        ClientProgramInboxReadSurface inbox;
        long frameEpoch = Long.MIN_VALUE;
        long blockProjections;
        long inboxProjections;

        Session(ClientProgramIdentity identity, Context<S> context) {
            this.identity = identity; this.context = context; worldIdentity = context.source().worldIdentity();
            var source = context.source();
            ClientProgramBlockReadSurface.Source<S> counted = new ClientProgramBlockReadSurface.Source<>() {
                @Override public Object worldIdentity() { return source.worldIdentity(); }
                @Override public ResourceLocation dimension() { return source.dimension(); }
                @Override public boolean isActive() { return source.isActive(); }
                @Override public boolean isLoaded(BlockPos position) { return source.isLoaded(position); }
                @Override public Optional<ClientProgramBlockReadSurface.Sample<S>> sample(BlockPos position) { return source.sample(position); }
                @Override public SFMValue project(S state) { blockProjections++; return source.project(state); }
            };
            Set<BlockPos> inRadius = new HashSet<>();
            for (BlockPos position : context.boundPositions()) {
                long dx = (long) position.getX() - identity.managerPosition().getX();
                long dy = (long) position.getY() - identity.managerPosition().getY();
                long dz = (long) position.getZ() - identity.managerPosition().getZ();
                int radius = ClientProgramBlockReadSurface.MAX_RADIUS;
                if (Math.abs(dx) <= radius && Math.abs(dy) <= radius && Math.abs(dz) <= radius
                        && dx * dx + dy * dy + dz * dz <= (long) radius * radius) inRadius.add(position);
            }
            blocks = new ClientProgramBlockReadSurface<>(identity, consent, policy, counted, inRadius,
                    ClientProgramBlockReadSurface.MAX_RADIUS);
        }
        ClientProgramInboxReadSurface newInbox() {
            return new ClientProgramInboxReadSurface(identity, consent, policy, context.recipient(), context.channels(), context.inbox());
        }
        void closeInbox() { if (inbox != null) inbox.close(); inbox = null; latest.clear(); }
        void close() { closeInbox(); blocks.clear(); }
    }

    private record Latest(SFMClientInbox.Cursor cursor, SFMValue value, long sequence, long oldest, long newest, SFMValue result) {}

    private static SFMValue blockResult(String status, Optional<SFMValue> value) {
        return SFMValue.object(Map.of("status", SFMValue.of(status), "value", value.orElse(SFMValue.nullValue())));
    }
    private static SFMValue inboxUnavailable(String status) {
        return SFMValue.object(Map.of("status", SFMValue.of(status), "value", SFMValue.nullValue(),
                "sequence", SFMValue.of(0L), "cursor", SFMValue.nullValue(), "continuity", SFMValue.nullValue(),
                "oldestSequence", SFMValue.of(0L), "newestSequence", SFMValue.of(0L), "hasMore", SFMValue.of(false)));
    }
    private static SFMValue cursorValue(SFMClientInbox.Cursor cursor) {
        return SFMValue.object(Map.of("session", SFMValue.of(cursor.session().toString()),
                "stream", SFMValue.of(cursor.stream().toString()), "after", SFMValue.of(cursor.afterSequence())));
    }
}
