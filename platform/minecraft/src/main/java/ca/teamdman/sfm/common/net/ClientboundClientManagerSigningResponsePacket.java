package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.program.signature.ClientManagerSigningAcknowledgement;
import ca.teamdman.sfm.common.program.signature.ClientManagerSigningCodec;
import ca.teamdman.sfm.common.program.signature.ClientManagerSigningState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Client receiver must match its pending request/world before decoding untrusted server metadata. */
public record ClientboundClientManagerSigningResponsePacket(UUID requestId, ResourceLocation dimension, BlockPos position,
                                                            ClientManagerSigningState.Status status,
                                                            Optional<byte[]> acknowledgement) implements SFMPacket {
    public ClientboundClientManagerSigningResponsePacket {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(dimension);
        position = Objects.requireNonNull(position).immutable();
        Objects.requireNonNull(status);
        if (dimension.toString().length() > 256) throw new IllegalArgumentException("Invalid signing response");
        acknowledgement = acknowledgement.map(bytes -> {
            if (bytes.length == 0 || bytes.length > ClientManagerSigningCodec.MAX_ACKNOWLEDGEMENT_BYTES) {
                throw new IllegalArgumentException("Signing acknowledgement is too large");
            }
            return bytes.clone();
        });
    }
    @Override public Optional<byte[]> acknowledgement() { return acknowledgement.map(byte[]::clone); }
    public Optional<ClientManagerSigningAcknowledgement> decodeAcknowledgement() {
        return acknowledgement.map(ClientManagerSigningCodec::decodeAcknowledgement);
    }
    public static final class Daddy implements SFMPacketDaddy<ClientboundClientManagerSigningResponsePacket> {
        public PacketDirection getPacketDirection() { return PacketDirection.CLIENTBOUND; }
        public Class<ClientboundClientManagerSigningResponsePacket> getPacketClass() { return ClientboundClientManagerSigningResponsePacket.class; }
        public void encode(ClientboundClientManagerSigningResponsePacket value, FriendlyByteBuf target) {
            target.writeUUID(value.requestId).writeUtf(value.dimension.toString(), 256).writeBlockPos(value.position)
                    .writeVarInt(value.status.ordinal()).writeBoolean(value.acknowledgement.isPresent());
            value.acknowledgement.ifPresent(target::writeByteArray);
        }
        public ClientboundClientManagerSigningResponsePacket decode(FriendlyByteBuf source) {
            UUID request = source.readUUID();
            var dimension = new ResourceLocation(source.readUtf(256));
            var position = source.readBlockPos();
            int status = source.readVarInt();
            if (status < 0 || status >= ClientManagerSigningState.Status.values().length) throw new IllegalArgumentException("Invalid signing status");
            Optional<byte[]> ack = source.readBoolean() ? Optional.of(source.readByteArray(ClientManagerSigningCodec.MAX_ACKNOWLEDGEMENT_BYTES)) : Optional.empty();
            return new ClientboundClientManagerSigningResponsePacket(request, dimension, position,
                    ClientManagerSigningState.Status.values()[status], ack);
        }
        public void handle(ClientboundClientManagerSigningResponsePacket packet, SFMPacketHandlingContext context) {
            ClientManagerSigningResponses.receive(packet);
        }
    }
}
