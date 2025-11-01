package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.registry.SFMItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ServerboundNetworkToolToggleOverlayPacket extends SFMPacket<ServerboundNetworkToolToggleOverlayPacket> {
    private EnumHand hand;

    public ServerboundNetworkToolToggleOverlayPacket(EnumHand hand) {
        this.hand = hand;
    }

    public ServerboundNetworkToolToggleOverlayPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hand = EnumHand.values()[buf.readInt()];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(hand.ordinal());
    }

    @Override
    public IMessage onMessage(ServerboundNetworkToolToggleOverlayPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            ItemStack networkToolItemStack = player.getHeldItem(message.hand);
            if (networkToolItemStack.getItem() == SFMItems.NETWORK_TOOL_ITEM) {
                boolean active = NetworkToolItem.getOverlayEnabled(networkToolItemStack);
                NetworkToolItem.setOverlayEnabled(networkToolItemStack, !active);
            }
        });
        return null;
    }
}