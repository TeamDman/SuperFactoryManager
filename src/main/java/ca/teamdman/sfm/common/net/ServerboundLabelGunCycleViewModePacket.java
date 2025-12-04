package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;

@Desugar
public record ServerboundLabelGunCycleViewModePacket(
        EnumHand hand
) implements SFMPacket<ServerboundLabelGunCycleViewModePacket> {
    public static class Daddy implements SFMPacketDaddy<ServerboundLabelGunCycleViewModePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundLabelGunCycleViewModePacket msg,
                FriendlyByteBuf buf
        ) {
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundLabelGunCycleViewModePacket decode(FriendlyByteBuf buf) {
            return new ServerboundLabelGunCycleViewModePacket(buf.readEnum(EnumHand.class));
        }

        @Override
        public void handle(
                ServerboundLabelGunCycleViewModePacket msg,
                SFMPacketHandlingContext context
        ) {
            EntityPlayerMP sender = context.sender();
            if (sender == null) return;

            var stack = sender.getHeldItem(msg.hand());
            if (!(stack.getItem() instanceof LabelGunItem)) return;

            LabelGunItem.cycleViewMode(stack);
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundLabelGunCycleViewModePacket> {

        @Override
        SFMPacketDaddy<ServerboundLabelGunCycleViewModePacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundLabelGunCycleViewModePacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}

