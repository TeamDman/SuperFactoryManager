package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.net.ClientboundClientInboxValuePacket;
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.net.ServerboundClientInboxSubscriptionPacket;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Client-world lifetime and reference-counted channel subscriptions. */
public final class SFMClientInboxRuntime {
    private static final SFMClientInboxRuntime INSTANCE = new SFMClientInboxRuntime(new SFMClientInbox());

    private final SFMClientInbox inbox;
    private final Map<ResourceLocation, Integer> references = new HashMap<>();
    private final Map<ResourceLocation, Object> owners = new HashMap<>();
    private static final Object HUMAN_OWNER = new Object();
    private @Nullable Object serverIdentity;
    private @Nullable Object levelIdentity;
    private @Nullable UUID recipient;
    private @Nullable ResourceLocation dimension;
    private @Nullable UUID session;

    SFMClientInboxRuntime(SFMClientInbox inbox) {
        this.inbox = Objects.requireNonNull(inbox, "inbox");
    }

    public static SFMClientInboxRuntime get() {
        return INSTANCE;
    }

    public synchronized void observeSessionIdentity(
            @Nullable Object server,
            @Nullable Object level,
            @Nullable UUID owner,
            @Nullable ResourceLocation dimension,
            boolean effectsAllowed
    ) {
        if (effectsAllowed && server != null && level != null && owner != null && dimension != null
            && server == serverIdentity && level == levelIdentity
            && owner.equals(recipient) && dimension.equals(this.dimension)) {
            return;
        }
        if (!effectsAllowed || server == null || level == null || owner == null || dimension == null) {
            serverIdentity = null;
            levelIdentity = null;
            recipient = null;
            this.dimension = null;
            session = null;
            references.clear();
            owners.clear();
            inbox.endSession();
            return;
        }
        serverIdentity = server;
        levelIdentity = level;
        recipient = owner;
        this.dimension = dimension;
        session = UUID.randomUUID();
        references.clear();
        owners.clear();
        inbox.beginSession(session, recipient, dimension);
    }

    public synchronized Optional<Subscription> subscribe(
            SFMClientInboxAddress address,
            Consumer<ServerboundClientInboxSubscriptionPacket> sender
    ) {
        return subscribe(address, HUMAN_OWNER, sender);
    }

    /** Remote channels pin one exact caller; same-owner references share, cross-owner piggybacking is rejected. */
    public synchronized Optional<Subscription> subscribe(SFMClientInboxAddress address, Object owner,
            Consumer<ServerboundClientInboxSubscriptionPacket> sender) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(sender, "sender");
        if (session == null || !address.recipient().equals(recipient)
            || !address.dimension().equals(dimension)) {
            return Optional.empty();
        }
        int count = references.getOrDefault(address.channel(), 0);
        if (count > 0 && !owner.equals(owners.get(address.channel()))) return Optional.empty();
        if (count == 0) {
            if (!inbox.subscribe(address.channel())) {
                return Optional.empty();
            }
            try {
                sender.accept(new ServerboundClientInboxSubscriptionPacket(
                        session, address.dimension(), address.channel(), true
                ));
            } catch (RuntimeException failedSend) {
                inbox.unsubscribe(address.channel());
                throw failedSend;
            }
        }
        references.put(address.channel(), count + 1);
        owners.put(address.channel(), owner);
        return Optional.of(new Subscription(this, address.channel(), session, owner, sender));
    }

    public synchronized boolean receive(ClientboundClientInboxValuePacket packet) {
        return packet.value().currentValue()
                .map(value -> inbox.append(packet.session(), packet.address(), value))
                .orElse(false);
    }

    public synchronized Optional<SFMClientInbox.Page> page(
            ResourceLocation channel,
            Optional<SFMClientInbox.Cursor> cursor,
            int limit
    ) {
        return inbox.page(channel, cursor, limit);
    }

    public synchronized Optional<UUID> session() {
        return Optional.ofNullable(session);
    }

    private synchronized Optional<SFMClientInbox.Page> pageForSubscription(
            ResourceLocation channel, UUID subscriptionSession, Object owner,
            Optional<SFMClientInbox.Cursor> cursor, int limit
    ) {
        if (!subscriptionSession.equals(session) || !owner.equals(owners.get(channel))) return Optional.empty();
        return inbox.page(channel, cursor, limit);
    }

    private synchronized void close(
            ResourceLocation channel,
            UUID subscriptionSession,
            Consumer<ServerboundClientInboxSubscriptionPacket> sender
    ) {
        if (!subscriptionSession.equals(session)) {
            return;
        }
        int count = references.getOrDefault(channel, 0);
        if (count > 1) {
            references.put(channel, count - 1);
        } else if (count == 1) {
            references.remove(channel);
            owners.remove(channel);
            inbox.unsubscribe(channel);
            sender.accept(new ServerboundClientInboxSubscriptionPacket(
                    subscriptionSession, dimension, channel, false
            ));
        }
    }

    public static final class Subscription implements AutoCloseable {
        private final SFMClientInboxRuntime runtime;
        private final ResourceLocation channel;
        private final UUID session;
        private final Object owner;
        private final Consumer<ServerboundClientInboxSubscriptionPacket> sender;
        private boolean closed;

        private Subscription(
                SFMClientInboxRuntime runtime,
                ResourceLocation channel,
                UUID session,
                Object owner,
                Consumer<ServerboundClientInboxSubscriptionPacket> sender
        ) {
            this.runtime = runtime;
            this.channel = channel;
            this.session = session;
            this.owner = owner;
            this.sender = sender;
        }

        /** A retained handle cannot read a replacement stream after session rotation or caller handoff. */
        public synchronized Optional<SFMClientInbox.Page> page(Optional<SFMClientInbox.Cursor> cursor, int limit) {
            if (closed) return Optional.empty();
            return runtime.pageForSubscription(channel, session, owner, cursor, limit);
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            runtime.close(channel, session, sender);
        }
    }
}
