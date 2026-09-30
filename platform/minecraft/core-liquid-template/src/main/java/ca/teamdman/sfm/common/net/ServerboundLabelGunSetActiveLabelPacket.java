package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf buf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf buf
{% endcase %}
        ) {
            buf.writeUtf(msg.label, MAX_LABEL_LENGTH);
            buf.writeEnum(msg.hand);
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundLabelGunSetActiveLabelPacket decode(FriendlyByteBuf buf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundLabelGunSetActiveLabelPacket decode(RegistryFriendlyByteBuf buf) {
{% endcase %}
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
