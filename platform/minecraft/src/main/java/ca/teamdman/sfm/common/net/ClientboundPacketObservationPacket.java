package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.net.SFMClientPacketTransport;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/** Best-effort observation of one generic packet value by the local client. */
public record ClientboundPacketObservationPacket(
        SFMPacketValueEnvelope value
) implements SFMPacket {
    public ClientboundPacketObservationPacket {
        Objects.requireNonNull(value, "value");
    }

    public static ClientboundPacketObservationPacket fromValue(SFMValue value) {
        return new ClientboundPacketObservationPacket(SFMPacketValueEnvelope.fromValue(value));
    }

    public static class Daddy implements SFMPacketDaddy<ClientboundPacketObservationPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<ClientboundPacketObservationPacket> getPacketClass() {
            return ClientboundPacketObservationPacket.class;
        }

        @Override
        public void encode(
                ClientboundPacketObservationPacket msg,
                FriendlyByteBuf target
        ) {
            msg.value.encode(target);
        }

        @Override
        public ClientboundPacketObservationPacket decode(FriendlyByteBuf source) {
            return new ClientboundPacketObservationPacket(SFMPacketValueEnvelope.decode(source));
        }

        @Override
        public void handle(
                ClientboundPacketObservationPacket msg,
                SFMPacketHandlingContext context
        ) {
            SFMClientPacketTransport.receiveObservation(msg.value);
        }
    }
}
