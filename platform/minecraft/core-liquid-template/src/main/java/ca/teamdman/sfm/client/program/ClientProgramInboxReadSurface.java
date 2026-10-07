package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.client.net.SFMClientInbox;
{% if features.client_frame_language %}
import ca.teamdman.sfm.client.net.SFMClientInboxRuntime;
import ca.teamdman.sfm.client.net.SFMClientInboxTransport;
{% endif %}
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-program, consent-gated view of the explicitly addressed P4A inbox.
 * The durable transport owns values; this surface owns only subscriptions and
 * cursors supplied by its caller. It does not replicate unopened inventories.
 */
public final class ClientProgramInboxReadSurface implements AutoCloseable {
    public static final ResourceLocation READ = new ResourceLocation("sfm", "client_inbox/read");

    public enum Status {
        PAGE,
        UNAVAILABLE_UNDECLARED,
        UNAVAILABLE_AWAITING_CONSENT,
        UNAVAILABLE_DENIED_BY_USER,
        UNAVAILABLE_BLOCKED_BY_POLICY,
        UNAVAILABLE_OUT_OF_SCOPE,
        UNAVAILABLE_NO_SESSION
    }

    public record Result(Status status, Optional<SFMClientInbox.Page> page) {
        public Result {
            Objects.requireNonNull(status, "status");
            page = Objects.requireNonNull(page, "page");
            if ((status == Status.PAGE) != page.isPresent()) {
                throw new IllegalArgumentException("Only PAGE carries inbox entries");
            }
        }

        static Result unavailable(Status status) {
            return new Result(status, Optional.empty());
        }
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    public interface InboxAccess {
        Optional<Subscription> subscribe(SFMClientInboxAddress address);

        Optional<SFMClientInbox.Page> page(
                ResourceLocation channel,
                Optional<SFMClientInbox.Cursor> cursor,
                int limit
        );
    }

{% if features.client_frame_language %}
    public static InboxAccess minecraftInbox() {
        return minecraftInbox(Optional.empty());
    }

    public static InboxAccess minecraftInbox(ClientProgramIdentity program) {
        return minecraftInbox(Optional.of(program));
    }

    private static InboxAccess minecraftInbox(Optional<ClientProgramIdentity> caller) {
        return new InboxAccess() {
            private final Map<ResourceLocation, SFMClientInboxRuntime.Subscription> leases = new HashMap<>();

            @Override
            public Optional<Subscription> subscribe(SFMClientInboxAddress address) {
                return SFMClientInboxTransport.subscribe(address, caller)
                        .map(active -> {
                            leases.put(address.channel(), active);
                            return () -> {
                                leases.remove(address.channel(), active);
                                active.close();
                            };
                        });
            }

            @Override
            public Optional<SFMClientInbox.Page> page(
                    ResourceLocation channel,
                    Optional<SFMClientInbox.Cursor> cursor,
                    int limit
            ) {
                SFMClientInboxTransport.observeCurrentWorld();
                if (caller.isPresent() && ClientManagerFrameRuntime.liveIdentityFor(caller.orElseThrow()).isEmpty()) return Optional.empty();
                var lease = leases.get(channel);
                return lease == null ? Optional.empty() : lease.page(cursor, limit);
            }
        };
    }

{% endif %}
    private final ClientProgramIdentity program;
    private final ClientProgramConsentGate consent;
    private final ClientProgramConsentGate.Policy policy;
    private final UUID recipient;
    private final Set<ResourceLocation> allowedChannels;
    private final InboxAccess inbox;
    private final Map<ResourceLocation, Subscription> subscriptions = new HashMap<>();
    private boolean closed;

    public ClientProgramInboxReadSurface(
            ClientProgramIdentity program,
            ClientProgramConsentGate consent,
            ClientProgramConsentGate.Policy policy,
            UUID recipient,
            Set<ResourceLocation> allowedChannels,
            InboxAccess inbox
    ) {
        this.program = Objects.requireNonNull(program, "program");
        this.consent = Objects.requireNonNull(consent, "consent");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        Objects.requireNonNull(allowedChannels, "allowedChannels");
        if (allowedChannels.size() > SFMClientInbox.MAX_CHANNELS) {
            throw new IllegalArgumentException("Too many allowed client inbox channels");
        }
        this.allowedChannels = Set.copyOf(allowedChannels);
    }

    public Result page(
            ResourceLocation channel,
            Optional<SFMClientInbox.Cursor> cursor,
            int limit
    ) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(cursor, "cursor");
        if (closed) return Result.unavailable(Status.UNAVAILABLE_NO_SESSION);
        if (!program.requestedCapabilities().contains(READ)) {
            releaseSubscriptions();
            return Result.unavailable(Status.UNAVAILABLE_UNDECLARED);
        }
        ClientProgramConsentGate.Evaluation execution = consent.execution(program, policy);
        if (!execution.allowed()) {
            releaseSubscriptions();
            return Result.unavailable(deniedStatus(execution));
        }
        ClientProgramConsentGate.Evaluation read = consent.evaluate(program, READ, policy);
        if (!read.allowed()) {
            releaseSubscriptions();
            return Result.unavailable(deniedStatus(read));
        }
        if (!allowedChannels.contains(channel) || limit < 1 || limit > SFMClientInbox.MAX_PAGE_SIZE) {
            return Result.unavailable(Status.UNAVAILABLE_OUT_OF_SCOPE);
        }
        if (!subscriptions.containsKey(channel)) {
            SFMClientInboxAddress address = new SFMClientInboxAddress(recipient, program.dimension(), channel);
            Optional<Subscription> opened = inbox.subscribe(address);
            if (opened.isEmpty()) return Result.unavailable(Status.UNAVAILABLE_NO_SESSION);
            subscriptions.put(channel, opened.orElseThrow());
        }
        return inbox.page(channel, cursor, limit)
                .map(page -> new Result(Status.PAGE, Optional.of(page)))
                .orElseGet(() -> {
                    Subscription stale = subscriptions.remove(channel);
                    if (stale != null) releaseSubscription(stale);
                    return Result.unavailable(Status.UNAVAILABLE_NO_SESSION);
                });
    }

    public int activeSubscriptions() {
        return subscriptions.size();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        releaseSubscriptions();
    }

    private void releaseSubscriptions() {
        // Detach first: an unavailable transport must not strand other local leases,
        // and callbacks cannot observe or close the same subscriptions a second time.
        List<Subscription> releasing = List.copyOf(subscriptions.values());
        subscriptions.clear();
        releasing.forEach(ClientProgramInboxReadSurface::releaseSubscription);
    }

    private static void releaseSubscription(Subscription subscription) {
        try {
            subscription.close();
        } catch (RuntimeException unavailableTransport) {
            // Local ownership is already relinquished. Disconnect/revocation cleanup
            // does not retry network effects or prevent the remaining leases closing.
        }
    }

    private static Status deniedStatus(ClientProgramConsentGate.Evaluation evaluation) {
        return switch (evaluation.effective()) {
            case ALLOWED -> throw new IllegalArgumentException("Allowed consent is not a denial");
            case AWAITING_CONSENT -> Status.UNAVAILABLE_AWAITING_CONSENT;
            case DENIED_BY_USER -> Status.UNAVAILABLE_DENIED_BY_USER;
            case BLOCKED_BY_POLICY -> Status.UNAVAILABLE_BLOCKED_BY_POLICY;
        };
    }
}
