package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/** Best-effort request to insert one packet item into an exact inventory target. */
public record ServerboundPacketInsertionPacket(
        SFMPacketInventoryAddress target,
        SFMPacketValueEnvelope value
) implements SFMPacket {
    public ServerboundPacketInsertionPacket {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(value, "value");
    }

    public static ServerboundPacketInsertionPacket fromValue(
            SFMPacketInventoryAddress target,
            SFMValue value
    ) {
        return new ServerboundPacketInsertionPacket(
                target,
                SFMPacketValueEnvelope.fromValue(value)
        );
    }

    public static class Daddy implements SFMPacketDaddy<ServerboundPacketInsertionPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }

        @Override
        public Class<ServerboundPacketInsertionPacket> getPacketClass() {
            return ServerboundPacketInsertionPacket.class;
        }

        @Override
        public void encode(
                ServerboundPacketInsertionPacket msg,
                FriendlyByteBuf target
        ) {
            target.writeVarInt(msg.value.codecVersion());
            msg.target.encode(target);
            msg.value.encodePayload(target);
        }

        @Override
        public ServerboundPacketInsertionPacket decode(FriendlyByteBuf source) {
            int codecVersion = source.readVarInt();
            SFMPacketInventoryAddress target = SFMPacketInventoryAddress.decode(source);
            SFMPacketValueEnvelope value = SFMPacketValueEnvelope.decodePayload(codecVersion, source);
            return new ServerboundPacketInsertionPacket(target, value);
        }

        @Override
        public void handle(
                ServerboundPacketInsertionPacket msg,
                SFMPacketHandlingContext context
        ) {
            SFMServerPacketTransport.receiveInsertionRequest(msg, context.sender());
        }
    }
}
