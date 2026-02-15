package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMEntityUtils;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;


@Desugar
public record ServerboundNetworkToolToggleOverlayPacket(
        EnumHand hand
) implements SFMPacket<ServerboundNetworkToolToggleOverlayPacket> {
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
            return new ServerboundNetworkToolToggleOverlayPacket(buf.readEnum(EnumHand.class));
        }

        @Override
        public void handle(
                ServerboundNetworkToolToggleOverlayPacket msg,
                SFMPacketHandlingContext context
        ) {
            EntityPlayerMP sender = context.sender();
            if (sender == null) return;
            ItemStack networkToolItemStack = sender.getHeldItem(msg.hand);
            if (networkToolItemStack.getItem() == SFMItems.NETWORK_TOOL) {
                NetworkToolItem.cycleOverlayMode(networkToolItemStack);
                NetworkToolItem.regenerateCablePositions(networkToolItemStack, SFMEntityUtils.getLevel(sender), sender);
            }
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundNetworkToolToggleOverlayPacket> {

        @Override
        SFMPacketDaddy<ServerboundNetworkToolToggleOverlayPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundNetworkToolToggleOverlayPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}

