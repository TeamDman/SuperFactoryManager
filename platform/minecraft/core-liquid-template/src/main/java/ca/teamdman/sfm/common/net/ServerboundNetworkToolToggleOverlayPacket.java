package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMEntityUtils;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public record ServerboundNetworkToolToggleOverlayPacket(
        InteractionHand hand
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundNetworkToolToggleOverlayPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundNetworkToolToggleOverlayPacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf buf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf buf
{% endcase %}
        ) {
            buf.writeEnum(msg.hand);
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundNetworkToolToggleOverlayPacket decode(FriendlyByteBuf buf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundNetworkToolToggleOverlayPacket decode(RegistryFriendlyByteBuf buf) {
{% endcase %}
            return new ServerboundNetworkToolToggleOverlayPacket(buf.readEnum(InteractionHand.class));
        }

        @Override
        public void handle(
                ServerboundNetworkToolToggleOverlayPacket msg,
                SFMPacketHandlingContext context
        ) {
            ServerPlayer sender = context.sender();
            if (sender == null) return;
            ItemStack networkToolItemStack = sender.getItemInHand(msg.hand);
            if (networkToolItemStack.getItem() == SFMItems.NETWORK_TOOL.get()) {
                NetworkToolItem.cycleOverlayMode(networkToolItemStack);
                NetworkToolItem.regenerateCablePositions(networkToolItemStack, SFMEntityUtils.getLevel(sender), sender);
            }
        }

        @Override
        public Class<ServerboundNetworkToolToggleOverlayPacket> getPacketClass() {
            return ServerboundNetworkToolToggleOverlayPacket.class;
        }
    }
}

