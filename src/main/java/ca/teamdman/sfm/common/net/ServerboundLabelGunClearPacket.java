package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.EnumHand;

public record ServerboundLabelGunClearPacket(
        EnumHand hand
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundLabelGunClearPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundLabelGunClearPacket msg,
                ByteBuf buf
        ) {
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundLabelGunClearPacket decode(ByteBuf buf) {
            return new ServerboundLabelGunClearPacket(buf.readEnum(EnumHand.class));
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
                var stack = sender.getItemInHand(msg.hand);
                if (stack.getItem() instanceof LabelGunItem) {
                    LabelGunItem.clearAll(stack);
                }
            }
        }

        @Override
        public Class<ServerboundLabelGunClearPacket> getPacketClass() {
            return ServerboundLabelGunClearPacket.class;
        }
    }
}
