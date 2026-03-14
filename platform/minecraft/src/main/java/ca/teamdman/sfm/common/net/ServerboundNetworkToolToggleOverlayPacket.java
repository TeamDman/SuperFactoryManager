package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.registry.SFMItems;
import net.minecraft.network.FriendlyByteBuf;
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
                FriendlyByteBuf buf
        ) {
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundNetworkToolToggleOverlayPacket decode(FriendlyByteBuf buf) {
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
            if (networkToolItemStack.getItem() == SFMItems.NETWORK_TOOL_ITEM.get()) {
                boolean active = NetworkToolItem.getOverlayEnabled(networkToolItemStack);
                NetworkToolItem.setOverlayEnabled(networkToolItemStack, !active);
            }
        }

        @Override
        public Class<ServerboundNetworkToolToggleOverlayPacket> getPacketClass() {
            return ServerboundNetworkToolToggleOverlayPacket.class;
        }
    }
}

