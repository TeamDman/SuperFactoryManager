package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import com.github.bsideup.jabel.Desugar;

@Desugar
public record ClientboundShowChangelogPacket(
) implements SFMPacket<ClientboundShowChangelogPacket> {

    public static class Daddy implements SFMPacketDaddy<ClientboundShowChangelogPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }
        @Override
        public void encode(
                ClientboundShowChangelogPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
        }

        @Override
        public ClientboundShowChangelogPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundShowChangelogPacket(
            );
        }

        @Override
        public void handle(
                ClientboundShowChangelogPacket msg,
                SFMPacketHandlingContext context
        ) {
            SFMScreenChangeHelpers.showChangelog();
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ClientboundShowChangelogPacket> {

        @Override
        SFMPacketDaddy<ClientboundShowChangelogPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ClientboundShowChangelogPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
