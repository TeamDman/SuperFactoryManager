package ca.teamdman.sfm.common.net;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ca.teamdman.sfm.common.item.LabelGunItem;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

public class ServerboundLabelGunSetActiveLabelPacket extends SFMPacket<ServerboundLabelGunSetActiveLabelPacket> {

    public static final int MAX_LABEL_LENGTH = 256;

    private String label;
    private EnumHand hand;

    public ServerboundLabelGunSetActiveLabelPacket(String label, EnumHand hand) {
        this.label = label;
        this.hand = hand;
    }

    public ServerboundLabelGunSetActiveLabelPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        try {
            label = packetBuffer.readString(MAX_LABEL_LENGTH);
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
        hand = packetBuffer.readEnumValue(EnumHand.class);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeString(label.length() > MAX_LABEL_LENGTH ? label.substring(0, MAX_LABEL_LENGTH) : label);
        packetBuffer.writeEnumValue(hand);
    }

    @Override
    @Nullable
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
