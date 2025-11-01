package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.label.LabelGunPlanner;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.EnumHand;

public record ServerboundLabelGunUsePacket(
        EnumHand hand,
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
                ByteBuf buf
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
        public ServerboundLabelGunUsePacket decode(ByteBuf buf) {
            return new ServerboundLabelGunUsePacket(
                    buf.readEnum(EnumHand.class),
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
