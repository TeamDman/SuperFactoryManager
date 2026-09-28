package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** No client-supplied player identity; the handler uses Forge's actual sender. */
public record ServerboundManagerShowPacket(
        UUID requestId, ResourceLocation dimension, BlockPos position
) implements SFMPacket {
    public ServerboundManagerShowPacket {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(dimension);
        position = requireWireRepresentable(position);
        if (dimension.toString().length() > 256) throw new IllegalArgumentException("Manager dimension is too long");
    }

    /** BlockPos' 26/12/26-bit wire form must not silently alias a different manager. */
    public static BlockPos requireWireRepresentable(BlockPos position) {
        Objects.requireNonNull(position);
        if (!BlockPos.of(position.asLong()).equals(position)) {
            throw new IllegalArgumentException("Manager position is outside the BlockPos wire range");
        }
        return position.immutable();
    }

    public static final class Daddy implements SFMPacketDaddy<ServerboundManagerShowPacket> {
        @Override public PacketDirection getPacketDirection() { return PacketDirection.SERVERBOUND; }
        @Override public Class<ServerboundManagerShowPacket> getPacketClass() { return ServerboundManagerShowPacket.class; }

        @Override
        public void encode(ServerboundManagerShowPacket value, FriendlyByteBuf target) {
            target.writeUUID(value.requestId()).writeUtf(value.dimension().toString(), 256)
                    .writeBlockPos(value.position());
        }

        @Override
        public ServerboundManagerShowPacket decode(FriendlyByteBuf source) {
            return new ServerboundManagerShowPacket(source.readUUID(),
                    new ResourceLocation(source.readUtf(256)), source.readBlockPos());
        }

        @Override
        public void handle(ServerboundManagerShowPacket packet, SFMPacketHandlingContext context) {
            var sender = context.sender();
            if (sender == null) return;
            SFMPackets.sendToPlayer(sender, SFMManagerShowQuery.evaluate(
                    context, packet.requestId(), packet.dimension(), packet.position()));
        }
    }
}
