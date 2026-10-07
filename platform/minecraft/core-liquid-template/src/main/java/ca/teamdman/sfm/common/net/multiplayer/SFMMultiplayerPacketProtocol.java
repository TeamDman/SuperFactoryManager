package ca.teamdman.sfm.common.net.multiplayer;

import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.net.SFMPacketInventoryInserter;
import ca.teamdman.sfm.common.net.SFMPacketValueEnvelope;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Versioned, bounded contracts; no channel registration or private-world gate changes. */
public final class SFMMultiplayerPacketProtocol {
    public static final int VERSION = 1;
    public static final int MAX_FRAME_BYTES = 8_192;
    public static final int MAX_IDENTIFIER_CHARACTERS = 256;
    public static final int MAX_PROGRAM_SCOPES = 64;

    private SFMMultiplayerPacketProtocol() {}

    public enum Action { PACKET_SEND, INBOX_SUBSCRIBE, INBOX_DELIVER }

    public enum Status {
        NEGOTIATED, INSERTION_ATTEMPTED, SUBSCRIBED, UNSUBSCRIBED, DELIVERED_TO_TRANSPORT,
        UNSUPPORTED_PROTOCOL, NOT_NEGOTIATED, STALE_SESSION, REPLAYED_REQUEST, OUT_OF_ORDER,
        FRAME_REJECTED, PAYLOAD_REJECTED, UNSUPPORTED_CODEC, RECIPIENT_REJECTED,
        AUTHORITY_DENIED, PROGRAM_REJECTED, NOT_SUBSCRIBED, SUBSCRIPTION_CAPACITY,
        RATE_LIMITED, DELIVERY_FAILED
    }

    /** Exact address of a server-observed manager, not a client-selected principal. */
    public record ManagerAddress(ResourceLocation dimension, BlockPos position) {
        public ManagerAddress {
            dimension = identifier(dimension);
            position = Objects.requireNonNull(position).immutable();
        }
    }

    public sealed interface Scope permits InventoryScope, InboxScope, DeliveryScope {}

    public record InventoryScope(SFMPacketInventoryAddress target) implements Scope {
        public InventoryScope { Objects.requireNonNull(target); }
    }

    public record InboxScope(ResourceLocation dimension, ResourceLocation channel) implements Scope {
        public InboxScope {
            dimension = identifier(dimension);
            channel = identifier(channel);
        }
    }

    /** Publishing requires its own exact publisher-to-recipient/channel grant. */
    public record DeliveryScope(ManagerAddress publisher, InboxScope inbox) implements Scope {
        public DeliveryScope {
            Objects.requireNonNull(publisher);
            Objects.requireNonNull(inbox);
            if (!publisher.dimension().equals(inbox.dimension())) {
                throw new IllegalArgumentException("Publisher and inbox dimensions differ");
            }
        }
    }

    public record ProgramClaim(
            ManagerAddress manager, UUID incarnation, long revision,
            String sourceSha256, String bindingsSha256
    ) {
        public ProgramClaim {
            Objects.requireNonNull(manager);
            Objects.requireNonNull(incarnation);
            if (revision < 0) throw new IllegalArgumentException("Negative program revision");
            sourceSha256 = digest(sourceSha256);
            bindingsSha256 = digest(bindingsSha256);
        }
    }

    public record ProgramOperation(Action action, Scope scope) {
        public ProgramOperation { requireScope(action, scope); }
    }

    /** Produced by a trusted server lookup/compiler, never decoded from a client's manifest. */
    public record ObservedProgram(ProgramClaim identity, Set<ProgramOperation> operations) {
        public ObservedProgram {
            Objects.requireNonNull(identity);
            if (Objects.requireNonNull(operations).size() > MAX_PROGRAM_SCOPES) {
                throw new IllegalArgumentException("Too many program operation scopes");
            }
            operations = Set.copyOf(operations);
        }
    }

    public record Offer(int protocolVersion, UUID session, int maximumFrameBytes, int maximumValueBytes) {}

    /** receivedFrameBytes is the adapter's actual buffer length, NOT a client length declaration. */
    public record Request(UUID session, long sequence, int receivedFrameBytes) {
        public Request { Objects.requireNonNull(session); }
    }

    /** The adapter must check the full frame length before allocating or decoding any fields. */
    public static void requireFrameSize(int receivedBytes) {
        if (receivedBytes <= 0 || receivedBytes > MAX_FRAME_BYTES) {
            throw new IllegalArgumentException("Multiplayer packet frame exceeds its boundary");
        }
    }

    @FunctionalInterface
    public interface PayloadReader { byte[] read(int exactByteCount); }

    /**
     * The reader is called only after session, ACL and budget admission. Network adapters must
     * bound the whole frame first and retain/copy at most that bounded frame until server dispatch.
     */
    public record PayloadSource(int codecVersion, int declaredBytes, PayloadReader reader) {
        public PayloadSource { Objects.requireNonNull(reader); }

        public Status preflight(int receivedFrameBytes) {
            if (receivedFrameBytes <= 0 || receivedFrameBytes > MAX_FRAME_BYTES
                || declaredBytes <= 0 || declaredBytes > SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES
                || declaredBytes > receivedFrameBytes) return Status.FRAME_REJECTED;
            return SFMValueJsonCodec.isReadableVersion(codecVersion) ? null : Status.UNSUPPORTED_CODEC;
        }

        SFMValue decode() {
            byte[] bytes = Objects.requireNonNull(reader.read(declaredBytes));
            if (bytes.length != declaredBytes) throw new IllegalArgumentException("Payload length mismatch");
            try {
                String json = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
                return new SFMPacketValueEnvelope(codecVersion, json).currentValue().orElseThrow();
            } catch (java.nio.charset.CharacterCodingException invalidUtf8) {
                throw new IllegalArgumentException("Invalid payload UTF-8", invalidUtf8);
            }
        }
    }

    /** No status in this protocol promises that a downstream manager processed an item. */
    public record Acknowledgement(
            UUID session, long sequence, Status status,
            Optional<SFMPacketInventoryInserter.Result> insertion
    ) {
        public Acknowledgement {
            Objects.requireNonNull(session);
            Objects.requireNonNull(status);
            insertion = Objects.requireNonNull(insertion);
            if ((status == Status.INSERTION_ATTEMPTED) != insertion.isPresent()) {
                throw new IllegalArgumentException("Only an actual insertion attempt has an insertion result");
            }
        }
    }

    static void requireScope(Action action, Scope scope) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(scope);
        boolean valid = switch (action) {
            case PACKET_SEND -> scope instanceof InventoryScope;
            case INBOX_SUBSCRIBE -> scope instanceof InboxScope;
            case INBOX_DELIVER -> scope instanceof DeliveryScope;
        };
        if (!valid) throw new IllegalArgumentException("Action/scope mismatch");
    }

    private static ResourceLocation identifier(ResourceLocation value) {
        Objects.requireNonNull(value);
        if (value.toString().length() > MAX_IDENTIFIER_CHARACTERS) {
            throw new IllegalArgumentException("Identifier is too long");
        }
        return value;
    }

    private static String digest(String value) {
        Objects.requireNonNull(value);
        if (value.length() != 64 || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected lowercase SHA-256 hex");
        }
        return value;
    }
}
