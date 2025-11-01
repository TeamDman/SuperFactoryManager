package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.EnumHand;

public record ServerboundLabelGunPrunePacket(
        EnumHand hand
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundLabelGunPrunePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundLabelGunPrunePacket msg,
                ByteBuf buf
        ) {
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundLabelGunPrunePacket decode(ByteBuf buf) {
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
            var stack = sender.getItemInHand(msg.hand);
            if (stack.getItem() instanceof LabelGunItem) {
                LabelPositionHolder.from(stack).prune().save(stack);
            }
        }

        @Override
        public Class<ServerboundLabelGunPrunePacket> getPacketClass() {
            return ServerboundLabelGunPrunePacket.class;
        }
    }
}
