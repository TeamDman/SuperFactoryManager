package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.registry.SFMItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.item.ItemStack;

public record ServerboundNetworkToolToggleOverlayPacket(
        EnumHand hand
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundNetworkToolToggleOverlayPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundNetworkToolToggleOverlayPacket msg,
                ByteBuf buf
        ) {
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundNetworkToolToggleOverlayPacket decode(ByteBuf buf) {
            return new ServerboundNetworkToolToggleOverlayPacket(buf.readEnum(EnumHand.class));
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

