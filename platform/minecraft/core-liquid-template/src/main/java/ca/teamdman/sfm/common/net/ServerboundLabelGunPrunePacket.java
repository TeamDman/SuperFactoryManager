package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}
import net.minecraft.world.InteractionHand;

public record ServerboundLabelGunPrunePacket(
        InteractionHand hand
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundLabelGunPrunePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundLabelGunPrunePacket msg,
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
        public ServerboundLabelGunPrunePacket decode(FriendlyByteBuf buf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundLabelGunPrunePacket decode(RegistryFriendlyByteBuf buf) {
{% endcase %}
            return new ServerboundLabelGunPrunePacket(buf.readEnum(InteractionHand.class));
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
