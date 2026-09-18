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
            inbox.endSession();
            return;
        }
        serverIdentity = server;
        levelIdentity = level;
        recipient = owner;
        this.dimension = dimension;
        session = UUID.randomUUID();
        references.clear();
        inbox.beginSession(session, recipient, dimension);
    }

    public synchronized Optional<Subscription> subscribe(
            SFMClientInboxAddress address,
            Consumer<ServerboundClientInboxSubscriptionPacket> sender
    ) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(sender, "sender");
        if (session == null || !address.recipient().equals(recipient)
            || !address.dimension().equals(dimension)) {
            return Optional.empty();
        }
        int count = references.getOrDefault(address.channel(), 0);
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
        return Optional.of(new Subscription(this, address.channel(), session, sender));
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
        private final Consumer<ServerboundClientInboxSubscriptionPacket> sender;
        private boolean closed;

        private Subscription(
                SFMClientInboxRuntime runtime,
                ResourceLocation channel,
                UUID session,
                Consumer<ServerboundClientInboxSubscriptionPacket> sender
        ) {
            this.runtime = runtime;
            this.channel = channel;
            this.session = session;
            this.sender = sender;
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
