package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.EnumHand;

@Desugar
public record ServerboundLabelGunSetActiveLabelPacket(
        String label,
        EnumHand hand
) implements SFMPacket<ServerboundLabelGunSetActiveLabelPacket> {
    public static final int MAX_LABEL_LENGTH = 256;

    public static class Daddy implements SFMPacketDaddy<ServerboundLabelGunSetActiveLabelPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundLabelGunSetActiveLabelPacket msg,
                FriendlyByteBuf buf
        ) {
            buf.writeUtf(msg.label, MAX_LABEL_LENGTH);
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundLabelGunSetActiveLabelPacket decode(FriendlyByteBuf buf) {
            return new ServerboundLabelGunSetActiveLabelPacket(
                    buf.readUtf(MAX_LABEL_LENGTH),
                    buf.readEnum(EnumHand.class)
            );
        }

        @Override
        public void handle(
                ServerboundLabelGunSetActiveLabelPacket msg,
                SFMPacketHandlingContext context
        ) {
            var sender = context.sender();
            if (sender == null) {
                return;
            }
            var stack = sender.getHeldItem(msg.hand);
            if (stack.getItem() instanceof LabelGunItem) {
                LabelGunItem.setActiveLabel(stack, msg.label);
            }
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundLabelGunSetActiveLabelPacket> {

        @Override
        SFMPacketDaddy<ServerboundLabelGunSetActiveLabelPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundLabelGunSetActiveLabelPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }

}
