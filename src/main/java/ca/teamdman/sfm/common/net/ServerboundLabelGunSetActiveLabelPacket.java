package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ServerboundLabelGunSetActiveLabelPacket extends SFMPacket<ServerboundLabelGunSetActiveLabelPacket> {
    private String label;
    private EnumHand hand;

    public ServerboundLabelGunSetActiveLabelPacket(String label, EnumHand hand) {
        this.label = label;
        this.hand = hand;
    }

    public ServerboundLabelGunSetActiveLabelPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        try {
            label = packetBuffer.readString(256);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        hand = EnumHand.values()[packetBuffer.readInt()];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeString(label);
        packetBuffer.writeInt(hand.ordinal());
    }

    @Override
    public IMessage onMessage(ServerboundLabelGunSetActiveLabelPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            ItemStack stack = player.getHeldItem(message.hand);
            if (stack.getItem() instanceof LabelGunItem) {
                LabelGunItem.setActiveLabel(stack, message.label);
            }
        });
        return null;
    }
}