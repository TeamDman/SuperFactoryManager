package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.program.signature.ProgramAttestationCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** Only bounded public attestation bytes; cryptographic parsing waits for server admission. */
public record ServerboundClientManagerSignaturePacket(UUID requestId, ResourceLocation dimension, BlockPos position,
                                                       UUID incarnation, long revision, UUID challenge, byte[] attestation)
        implements SFMPacket {
    public ServerboundClientManagerSignaturePacket {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(dimension);
        position = Objects.requireNonNull(position).immutable();
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(challenge);
        if (revision < 0 || dimension.toString().length() > 256 || attestation.length == 0
            || attestation.length > ProgramAttestationCodec.MAX_ATTESTATION_BYTES) throw new IllegalArgumentException("Invalid signature request");
        attestation = attestation.clone();
    }
    @Override public byte[] attestation() { return attestation.clone(); }
    public int chargedBytes() { return attestation.length + 128; }
    public static final class Daddy implements SFMPacketDaddy<ServerboundClientManagerSignaturePacket> {
        public PacketDirection getPacketDirection() { return PacketDirection.SERVERBOUND; }
        public Class<ServerboundClientManagerSignaturePacket> getPacketClass() { return ServerboundClientManagerSignaturePacket.class; }
        public void encode(ServerboundClientManagerSignaturePacket value, FriendlyByteBuf target) {
            target.writeUUID(value.requestId).writeUtf(value.dimension.toString(), 256).writeBlockPos(value.position)
                    .writeUUID(value.incarnation);
            target.writeLong(value.revision);
            target.writeUUID(value.challenge).writeByteArray(value.attestation);
        }
        public ServerboundClientManagerSignaturePacket decode(FriendlyByteBuf source) {
            return new ServerboundClientManagerSignaturePacket(source.readUUID(), new ResourceLocation(source.readUtf(256)),
                    source.readBlockPos(), source.readUUID(), source.readLong(), source.readUUID(),
                    source.readByteArray(ProgramAttestationCodec.MAX_ATTESTATION_BYTES));
        }
        public void handle(ServerboundClientManagerSignaturePacket packet, SFMPacketHandlingContext context) {
            SFMServerClientManagerSigningTransport.receive(packet, context.sender());
        }
    }
}
