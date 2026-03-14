package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfml.ast.SFMLProgram;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;

public record ServerboundDiskItemSetProgramPacket(
        String programString,
        InteractionHand hand
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundDiskItemSetProgramPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundDiskItemSetProgramPacket msg,
                FriendlyByteBuf buf
        ) {
            buf.writeUtf(msg.programString, SFMLProgram.MAX_PROGRAM_LENGTH);
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundDiskItemSetProgramPacket decode(FriendlyByteBuf buf) {
            return new ServerboundDiskItemSetProgramPacket(
                    buf.readUtf(SFMLProgram.MAX_PROGRAM_LENGTH),
                    buf.readEnum(InteractionHand.class)
            );
        }

        @Override
        public void handle(
                ServerboundDiskItemSetProgramPacket msg,
                SFMPacketHandlingContext context
        ) {
            var sender = context.sender();
            if (sender == null) {
                return;
            }
            var stack = sender.getItemInHand(msg.hand);
            if (stack.getItem() instanceof DiskItem) {
                DiskItem.setProgram(stack, msg.programString);
                DiskItem.rebuildSfmlProgram(stack, null, true);
            }
        }

        @Override
        public Class<ServerboundDiskItemSetProgramPacket> getPacketClass() {
            return ServerboundDiskItemSetProgramPacket.class;
        }
    }
}
