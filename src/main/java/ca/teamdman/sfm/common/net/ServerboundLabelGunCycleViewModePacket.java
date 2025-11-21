package ca.teamdman.sfm.common.net;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ca.teamdman.sfm.common.item.LabelGunItem;
import io.netty.buffer.ByteBuf;

public class ServerboundLabelGunCycleViewModePacket extends SFMPacket<ServerboundLabelGunCycleViewModePacket> {

    private EnumHand hand;

    public ServerboundLabelGunCycleViewModePacket(EnumHand hand) {
        this.hand = hand;
    }

    public ServerboundLabelGunCycleViewModePacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        hand = EnumHand.values()[buf.readInt()];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(hand.ordinal());
    }

    @Override
    @Nullable
    public IMessage onMessage(ServerboundLabelGunCycleViewModePacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            ItemStack stack = player.getHeldItem(message.hand);
            if (stack.getItem() instanceof LabelGunItem) {
                LabelGunItem.cycleViewMode(stack);
            }
        });
        return null;
    }
}
