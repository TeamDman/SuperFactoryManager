package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.ClientLabelGunResponseChatHelper;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record ClientboundLabelGunUseResponsePacket(
        Behaviour behaviour
) implements SFMPacket {
    public enum Behaviour {
        Pushed,
        Pulled
    }

    public void sendToPlayer(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            SFMPackets.sendToPlayer(serverPlayer, this);
        }
    }
    public static class Daddy implements SFMPacketDaddy<ClientboundLabelGunUseResponsePacket> {

        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<ClientboundLabelGunUseResponsePacket> getPacketClass() {
            return ClientboundLabelGunUseResponsePacket.class;
        }

        @Override
        public void encode(
                ClientboundLabelGunUseResponsePacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeEnum(msg.behaviour());
        }

        @Override
        public ClientboundLabelGunUseResponsePacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundLabelGunUseResponsePacket(friendlyByteBuf.readEnum(Behaviour.class));
        }

        @Override
        public void handle(
                ClientboundLabelGunUseResponsePacket msg,
                SFMPacketHandlingContext context
        ) {
            ClientLabelGunResponseChatHelper.handle(msg, context);
        }
    }
}
