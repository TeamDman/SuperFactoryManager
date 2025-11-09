package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;

public class ServerboundLabelGunClearPacket extends SFMPacket<ServerboundLabelGunClearPacket> {
    private EnumHand hand;

    public ServerboundLabelGunClearPacket(EnumHand hand) {
        this.hand = hand;
    }

    public ServerboundLabelGunClearPacket() {
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
    @Nullable
    public IMessage onMessage(ServerboundLabelGunClearPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            ItemStack stack = player.getHeldItem(message.hand);
            if (stack.getItem() instanceof LabelGunItem) {
                LabelGunItem.clearAll(stack);
            }
        });
        return null;
    }
}