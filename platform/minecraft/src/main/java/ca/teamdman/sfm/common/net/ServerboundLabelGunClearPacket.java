package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.EnumHand;

@Desugar
public record ServerboundLabelGunClearPacket(
        EnumHand hand
) implements SFMPacket<ServerboundLabelGunClearPacket> {
    public static class Daddy implements SFMPacketDaddy<ServerboundLabelGunClearPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundLabelGunClearPacket msg,
                FriendlyByteBuf buf
        ) {
            buf.writeEnumValue(msg.hand);
        }

        @Override
        public ServerboundLabelGunClearPacket decode(FriendlyByteBuf buf) {
            return new ServerboundLabelGunClearPacket(buf.readEnumValue(EnumHand.class));
        }

        @Override
        public void handle(
                ServerboundLabelGunClearPacket msg,
                SFMPacketHandlingContext context
        ) {
            {
                var sender = context.sender();
                if (sender == null) {
                    return;
                }
                var stack = sender.getHeldItem(msg.hand);
                if (stack.getItem() instanceof LabelGunItem) {
                    LabelGunItem.clearAll(stack);
                }
            }
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundLabelGunClearPacket> {

        @Override
        SFMPacketDaddy<ServerboundLabelGunClearPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundLabelGunClearPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
