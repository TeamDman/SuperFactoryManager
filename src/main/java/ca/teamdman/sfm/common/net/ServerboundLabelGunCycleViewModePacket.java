package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.EnumHand;

public record ServerboundLabelGunCycleViewModePacket(
        EnumHand hand
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundLabelGunCycleViewModePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundLabelGunCycleViewModePacket msg,
                ByteBuf buf
        ) {
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundLabelGunCycleViewModePacket decode(ByteBuf buf) {
            return new ServerboundLabelGunCycleViewModePacket(buf.readEnum(EnumHand.class));
        }

        @Override
        public void handle(
                ServerboundLabelGunCycleViewModePacket msg,
                SFMPacketHandlingContext context
        ) {
            ServerPlayer sender = context.sender();
            if (sender == null) return;

            var stack = sender.getItemInHand(msg.hand());
            if (!(stack.getItem() instanceof LabelGunItem)) return;

            LabelGunItem.cycleViewMode(stack);
        }

        @Override
        public Class<ServerboundLabelGunCycleViewModePacket> getPacketClass() {
            return ServerboundLabelGunCycleViewModePacket.class;
        }
    }
}

