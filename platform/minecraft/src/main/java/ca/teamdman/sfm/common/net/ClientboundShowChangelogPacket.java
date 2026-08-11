package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record ClientboundShowChangelogPacket(
) implements SFMPacket {

    public static class Daddy implements SFMPacketDaddy<ClientboundShowChangelogPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }
        @Override
        public void encode(
                ClientboundShowChangelogPacket msg,
                RegistryFriendlyByteBuf friendlyByteBuf
        ) {
        }

        @Override
        public ClientboundShowChangelogPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
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
        public Class<ClientboundShowChangelogPacket> getPacketClass() {
            return ClientboundShowChangelogPacket.class;
        }
    }
}
