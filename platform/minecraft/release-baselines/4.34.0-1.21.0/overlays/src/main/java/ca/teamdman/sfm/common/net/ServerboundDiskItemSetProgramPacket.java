package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
                RegistryFriendlyByteBuf buf
        ) {
            buf.writeUtf(msg.programString, Program.MAX_PROGRAM_LENGTH);
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundDiskItemSetProgramPacket decode(RegistryFriendlyByteBuf buf) {
            return new ServerboundDiskItemSetProgramPacket(
                    buf.readUtf(Program.MAX_PROGRAM_LENGTH),
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
                DiskItem.compileAndUpdateErrorsAndWarnings(stack, null, true);
                DiskItem.pruneIfDefault(stack);
            }
        }

        @Override
        public Class<ServerboundDiskItemSetProgramPacket> getPacketClass() {
            return ServerboundDiskItemSetProgramPacket.class;
        }
    }
}
