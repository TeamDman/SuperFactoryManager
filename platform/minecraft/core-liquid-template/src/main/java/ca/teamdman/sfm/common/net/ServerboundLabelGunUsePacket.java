package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.label.LabelGunPlanner;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}
import net.minecraft.world.InteractionHand;

public record ServerboundLabelGunUsePacket(
        InteractionHand hand,
        BlockPos pos,
        boolean isContiguousModifierActive,
        boolean isPickBlockModifierActive,
        boolean isClearModifierActive,
        boolean isPullModifierActive,
        boolean isTargetManagerModifierActive
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundLabelGunUsePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }

        @Override
        public void encode(
                ServerboundLabelGunUsePacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf buf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf buf
{% endcase %}
        ) {
            buf.writeEnum(msg.hand);
            buf.writeBlockPos(msg.pos);
            buf.writeBoolean(msg.isContiguousModifierActive);
            buf.writeBoolean(msg.isPickBlockModifierActive);
            buf.writeBoolean(msg.isClearModifierActive);
            buf.writeBoolean(msg.isPullModifierActive);
            buf.writeBoolean(msg.isTargetManagerModifierActive);
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundLabelGunUsePacket decode(FriendlyByteBuf buf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundLabelGunUsePacket decode(RegistryFriendlyByteBuf buf) {
{% endcase %}
            return new ServerboundLabelGunUsePacket(
                    buf.readEnum(InteractionHand.class),
                    buf.readBlockPos(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean()
            );
        }

        @Override
        public void handle(
                ServerboundLabelGunUsePacket msg,
                SFMPacketHandlingContext context
        ) {
            var player = context.sender();
            if (player == null) {
                return;
            }
            var plan = LabelGunPlanner.getLabelGunPlan(player, msg, true);
            if (plan != null) {
                plan.run();
            }
        }

        @Override
        public Class<ServerboundLabelGunUsePacket> getPacketClass() {
            return ServerboundLabelGunUsePacket.class;
        }
    }

    @Override
    public String toString() {
        return "ServerboundLabelGunUsePacket{" +
               "hand=" + hand +
               ", pos=" + pos +
               ", isContiguousModifierActive=" + isContiguousModifierActive +
               ", isPickBlockModifierActive=" + isPickBlockModifierActive +
               ", isClearModifierActive=" + isClearModifierActive +
               ", isPullModifierActive=" + isPullModifierActive +
               ", isTargetManagerModifierActive=" + isTargetManagerModifierActive +
               '}';
    }
}
