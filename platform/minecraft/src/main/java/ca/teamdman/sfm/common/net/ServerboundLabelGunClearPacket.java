package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;

public record ServerboundLabelGunClearPacket(
        InteractionHand hand
) implements SFMPacket {
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
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundLabelGunClearPacket decode(FriendlyByteBuf buf) {
            return new ServerboundLabelGunClearPacket(buf.readEnum(InteractionHand.class));
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
