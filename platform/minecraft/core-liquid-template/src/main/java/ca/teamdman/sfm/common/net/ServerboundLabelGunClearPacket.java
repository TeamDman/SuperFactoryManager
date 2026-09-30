package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf buf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf buf
{% endcase %}
        ) {
            buf.writeEnum(msg.hand);
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundLabelGunClearPacket decode(FriendlyByteBuf buf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundLabelGunClearPacket decode(RegistryFriendlyByteBuf buf) {
{% endcase %}
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
