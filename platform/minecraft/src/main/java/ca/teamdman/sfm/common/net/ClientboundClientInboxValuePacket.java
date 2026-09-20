package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.net.SFMClientInboxTransport;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

/** One best-effort, addressed delivery; receipt does not acknowledge application processing. */
public record ClientboundClientInboxValuePacket(
        UUID session,
        SFMClientInboxAddress address,
        SFMPacketValueEnvelope value
) implements SFMPacket {
    public ClientboundClientInboxValuePacket {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(value, "value");
    }

    public static ClientboundClientInboxValuePacket fromValue(
            UUID session,
            SFMClientInboxAddress address,
            SFMValue value
    ) {
        return new ClientboundClientInboxValuePacket(session, address, SFMPacketValueEnvelope.fromValue(value));
    }

    public static class Daddy implements SFMPacketDaddy<ClientboundClientInboxValuePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<ClientboundClientInboxValuePacket> getPacketClass() {
            return ClientboundClientInboxValuePacket.class;
        }

        @Override
        public void encode(ClientboundClientInboxValuePacket msg, FriendlyByteBuf target) {
            target.writeUUID(msg.session);
            msg.address.encode(target);
            msg.value.encode(target);
        }

        @Override
        public ClientboundClientInboxValuePacket decode(FriendlyByteBuf source) {
            return new ClientboundClientInboxValuePacket(
                    source.readUUID(),
                    SFMClientInboxAddress.decode(source),
                    SFMPacketValueEnvelope.decode(source)
            );
        }

        @Override
        public void handle(ClientboundClientInboxValuePacket msg, SFMPacketHandlingContext context) {
            SFMClientInboxTransport.receive(msg);
        }
    }
}
