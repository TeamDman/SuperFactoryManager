package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMEntityUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
                RegistryFriendlyByteBuf buf
        ) {
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundNetworkToolToggleOverlayPacket decode(RegistryFriendlyByteBuf buf) {
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

