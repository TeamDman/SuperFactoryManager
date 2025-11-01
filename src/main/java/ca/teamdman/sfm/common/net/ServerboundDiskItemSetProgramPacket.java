package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.DiskItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ServerboundDiskItemSetProgramPacket extends SFMPacket<ServerboundDiskItemSetProgramPacket> {
    private String programString;
    private EnumHand hand;

    public ServerboundDiskItemSetProgramPacket(String programString, EnumHand hand) {
        this.programString = programString;
        this.hand = hand;
    }

    public ServerboundDiskItemSetProgramPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        programString = ByteBufUtils.readUTF8String(buf);
        hand = EnumHand.values()[buf.readInt()];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, programString);
        buf.writeInt(hand.ordinal());
    }

    @Override
    public IMessage onMessage(ServerboundDiskItemSetProgramPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            ItemStack stack = player.getHeldItem(message.hand);
            if (stack.getItem() instanceof DiskItem) {
                DiskItem.setProgram(stack, message.programString);
                DiskItem.compileAndUpdateErrorsAndWarnings(stack, null);
                DiskItem.pruneIfDefault(stack);
            }
        });
        return null;
    }
}