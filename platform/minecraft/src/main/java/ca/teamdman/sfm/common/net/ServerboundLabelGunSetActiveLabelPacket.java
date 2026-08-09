package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;

public record ServerboundLabelGunSetActiveLabelPacket(
        String label,
        InteractionHand hand
) implements SFMPacket {
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
                    buf.readEnum(InteractionHand.class)
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
            var stack = sender.getItemInHand(msg.hand);
            if (stack.getItem() instanceof LabelGunItem) {
                LabelGunItem.setActiveLabel(stack, msg.label);
            }
        }

        @Override
        public Class<ServerboundLabelGunSetActiveLabelPacket> getPacketClass() {
            return ServerboundLabelGunSetActiveLabelPacket.class;
        }
    }

}
