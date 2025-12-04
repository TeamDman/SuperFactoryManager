package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.EnumHand;

@Desugar
public record ServerboundLabelGunPrunePacket(
        EnumHand hand
) implements SFMPacket<ServerboundLabelGunPrunePacket> {
    public static class Daddy implements SFMPacketDaddy<ServerboundLabelGunPrunePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundLabelGunPrunePacket msg,
                FriendlyByteBuf buf
        ) {
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundLabelGunPrunePacket decode(FriendlyByteBuf buf) {
            return new ServerboundLabelGunPrunePacket(buf.readEnum(EnumHand.class));
        }

        @Override
        public void handle(
                ServerboundLabelGunPrunePacket msg,
                SFMPacketHandlingContext context
        ) {
            var sender = context.sender();
            if (sender == null) {
                return;
            }
            var stack = sender.getHeldItem(msg.hand);
            if (stack.getItem() instanceof LabelGunItem) {
                LabelPositionHolder.from(stack).prune().save(stack);
            }
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundLabelGunPrunePacket> {

        @Override
        SFMPacketDaddy<ServerboundLabelGunPrunePacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundLabelGunPrunePacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
