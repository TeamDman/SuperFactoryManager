package ca.teamdman.sfm.common.net.multiplayer;

import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.net.SFMPacketInventoryInserter;
import ca.teamdman.sfm.common.net.SFMPacketValueEnvelope;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;

/** Bounded binary frames. Value parsing is deferred until server admission; headers contain no player authority. */
public final class SFMMultiplayerPacketWire {
    private SFMMultiplayerPacketWire() {}

    public sealed interface ClientMessage permits Negotiate, Insert, Subscription {}
    public record Negotiate(int version, UUID session) implements ClientMessage {}
    public record Insert(Request request, InventoryScope target, Optional<ProgramClaim> program,
                         PayloadSource payload) implements ClientMessage {}
    public record Subscription(Request request, UUID localInboxSession, UUID recipient, InboxScope inbox,
                               Optional<ProgramClaim> program, boolean subscribe) implements ClientMessage {}

    public sealed interface ServerMessage permits SessionOffer, Result, InboxValue {}
    public record SessionOffer(Offer offer, UUID world) implements ServerMessage {}
    public record Result(Acknowledgement acknowledgement) implements ServerMessage {}
    public record InboxValue(UUID session, UUID localInboxSession, SFMClientInboxAddress address,
                             SFMPacketValueEnvelope value) implements ServerMessage {}

    /** Network decoders call this BEFORE copying a frame or decoding any field. */
    public static byte[] copyFrame(FriendlyByteBuf buffer) {
        requireFrameSize(buffer.readableBytes());
        byte[] frame = new byte[buffer.readableBytes()];
        buffer.readBytes(frame);
        return frame;
    }

    public static ClientMessage decodeClient(byte[] frame) {
        requireFrameSize(frame.length);
        byte[] snapshot = frame.clone();
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(snapshot));
        try {
            int kind = buffer.readUnsignedByte();
            if (kind == 0) {
                var value = new Negotiate(buffer.readVarInt(), buffer.readUUID());
                end(buffer);
                return value;
            }
            Request request = new Request(buffer.readUUID(), buffer.readLong(), frame.length);
            if (kind == 1) {
                var target = new InventoryScope(SFMPacketInventoryAddress.decode(buffer));
                var program = readClaim(buffer);
                int version = buffer.readVarInt();
                int bytes = buffer.readVarInt();
                if (bytes <= 0 || bytes > SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES || bytes != buffer.readableBytes()) {
                    throw new IllegalArgumentException("Invalid multiplayer value length");
                }
                int start = buffer.readerIndex();
                // The raw frame is already bounded. Decode/copy the value only after ACL and quota checks.
                return new Insert(request, target, program, new PayloadSource(version, bytes,
                        exact -> {
                            if (exact != bytes) throw new IllegalArgumentException("Invalid payload read size");
                            return java.util.Arrays.copyOfRange(snapshot, start, start + exact);
                        }));
            }
            if (kind == 2 || kind == 3) {
                var value = new Subscription(request, buffer.readUUID(), buffer.readUUID(),
                        readInbox(buffer), readClaim(buffer), kind == 2);
                end(buffer);
                return value;
            }
            throw new IllegalArgumentException("Unknown multiplayer client frame");
        } finally { buffer.release(); }
    }

    public static ServerMessage decodeServer(byte[] frame) {
        requireFrameSize(frame.length);
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(frame));
        try {
            ServerMessage result = switch (buffer.readUnsignedByte()) {
                case 0 -> new SessionOffer(new Offer(buffer.readVarInt(), buffer.readUUID(),
                        buffer.readVarInt(), buffer.readVarInt()), buffer.readUUID());
                case 1 -> {
                    UUID session = buffer.readUUID();
                    long sequence = buffer.readLong();
                    Status status = readEnum(buffer, Status.values());
                    Optional<SFMPacketInventoryInserter.Result> insertion = buffer.readBoolean()
                            ? Optional.of(readEnum(buffer, SFMPacketInventoryInserter.Result.values())) : Optional.empty();
                    yield new Result(new Acknowledgement(session, sequence, status, insertion));
                }
                case 2 -> new InboxValue(buffer.readUUID(), buffer.readUUID(), SFMClientInboxAddress.decode(buffer),
                        SFMPacketValueEnvelope.decodePayload(buffer.readVarInt(), buffer));
                default -> throw new IllegalArgumentException("Unknown multiplayer server frame");
            };
            end(buffer);
            return result;
        } finally { buffer.release(); }
    }

    public static byte[] negotiate(int version, UUID session) {
        return frame(buffer -> { buffer.writeByte(0); buffer.writeVarInt(version); buffer.writeUUID(session); });
    }
    public static byte[] insert(UUID session, long sequence, SFMPacketInventoryAddress target,
                                Optional<ProgramClaim> program, SFMValue value) {
        var envelope = SFMPacketValueEnvelope.fromValue(value);
        byte[] json = envelope.canonicalJson().getBytes(StandardCharsets.UTF_8);
        return frame(buffer -> {
            header(buffer, 1, session, sequence);
            target.encode(buffer);
            writeClaim(buffer, program);
            buffer.writeVarInt(envelope.codecVersion());
            buffer.writeVarInt(json.length);
            buffer.writeBytes(json);
        });
    }
    public static byte[] subscription(UUID session, long sequence, UUID localInboxSession, SFMClientInboxAddress address,
                                     Optional<ProgramClaim> program, boolean subscribe) {
        return frame(buffer -> {
            header(buffer, subscribe ? 2 : 3, session, sequence);
            buffer.writeUUID(localInboxSession);
            buffer.writeUUID(address.recipient());
            writeInbox(buffer, new InboxScope(address.dimension(), address.channel()));
            writeClaim(buffer, program);
        });
    }
    public static byte[] offer(Offer offer, UUID world) {
        return frame(buffer -> {
            buffer.writeByte(0);
            buffer.writeVarInt(offer.protocolVersion());
            buffer.writeUUID(offer.session());
            buffer.writeVarInt(offer.maximumFrameBytes());
            buffer.writeVarInt(offer.maximumValueBytes());
            buffer.writeUUID(world);
        });
    }
    public static byte[] result(Acknowledgement acknowledgement) {
        return frame(buffer -> {
            header(buffer, 1, acknowledgement.session(), acknowledgement.sequence());
            buffer.writeVarInt(acknowledgement.status().ordinal());
            buffer.writeBoolean(acknowledgement.insertion().isPresent());
            acknowledgement.insertion().ifPresent(value -> buffer.writeVarInt(value.ordinal()));
        });
    }
    public static byte[] inbox(UUID session, UUID localInboxSession, SFMClientInboxAddress address, SFMValue value) {
        var envelope = SFMPacketValueEnvelope.fromValue(value);
        return frame(buffer -> {
            buffer.writeByte(2);
            buffer.writeUUID(session);
            buffer.writeUUID(localInboxSession);
            address.encode(buffer);
            buffer.writeVarInt(envelope.codecVersion());
            envelope.encodePayload(buffer);
        });
    }
    private static void header(FriendlyByteBuf buffer, int kind, UUID session, long sequence) {
        buffer.writeByte(kind);
        buffer.writeUUID(session);
        buffer.writeLong(sequence);
    }
    private static Optional<ProgramClaim> readClaim(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return Optional.empty();
        return Optional.of(new ProgramClaim(new ManagerAddress(readIdentifier(buffer), buffer.readBlockPos()),
                buffer.readUUID(), buffer.readLong(), buffer.readUtf(64), buffer.readUtf(64)));
    }
    private static void writeClaim(FriendlyByteBuf buffer, Optional<ProgramClaim> program) {
        buffer.writeBoolean(program.isPresent());
        program.ifPresent(claim -> {
            writeIdentifier(buffer, claim.manager().dimension());
            buffer.writeBlockPos(claim.manager().position());
            buffer.writeUUID(claim.incarnation());
            buffer.writeLong(claim.revision());
            buffer.writeUtf(claim.sourceSha256(), 64);
            buffer.writeUtf(claim.bindingsSha256(), 64);
        });
    }
    private static InboxScope readInbox(FriendlyByteBuf buffer) { return new InboxScope(readIdentifier(buffer), readIdentifier(buffer)); }
    private static void writeInbox(FriendlyByteBuf buffer, InboxScope value) {
        writeIdentifier(buffer, value.dimension());
        writeIdentifier(buffer, value.channel());
    }
    private static ResourceLocation readIdentifier(FriendlyByteBuf buffer) {
        return new ResourceLocation(buffer.readUtf(MAX_IDENTIFIER_CHARACTERS));
    }
    private static void writeIdentifier(FriendlyByteBuf buffer, ResourceLocation value) {
        buffer.writeUtf(value.toString(), MAX_IDENTIFIER_CHARACTERS);
    }
    private static <E> E readEnum(FriendlyByteBuf buffer, E[] values) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Invalid multiplayer enum");
        return values[ordinal];
    }
    private static byte[] frame(Consumer<FriendlyByteBuf> encoder) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer(128, MAX_FRAME_BYTES));
        try {
            Objects.requireNonNull(encoder).accept(buffer);
            return copyFrame(buffer);
        } finally { buffer.release(); }
    }
    private static void end(FriendlyByteBuf buffer) {
        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing multiplayer frame data");
    }
}
