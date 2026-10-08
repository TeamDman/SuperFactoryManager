package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.ClientLabelGunResponseChatHelper;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {
            friendlyByteBuf.writeEnum(msg.behaviour());
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ClientboundLabelGunUseResponsePacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ClientboundLabelGunUseResponsePacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
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
