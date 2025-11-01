package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfml.ast.Program;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.ByteBufUtils;

public record ServerboundDiskItemSetProgramPacket(
        String programString,
        EnumHand hand
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundDiskItemSetProgramPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }

        @Override
        public void encode(
                ServerboundDiskItemSetProgramPacket msg,
                ByteBuf buf
        ) {
            ByteBufUtils.writeUTF8String(buf, msg.programString);
            buf.writeInt(msg.hand.ordinal());
        }

        @Override
        public ServerboundDiskItemSetProgramPacket decode(ByteBuf buf) {
            return new ServerboundDiskItemSetProgramPacket(
                    ByteBufUtils.readUTF8String(buf),
                    EnumHand.class.getEnumConstants()[buf.readInt()]
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
                DiskItem.compileAndUpdateErrorsAndWarnings(stack, null);
                DiskItem.pruneIfDefault(stack);
            }
        }

        @Override
        public Class<ServerboundDiskItemSetProgramPacket> getPacketClass() {
            return ServerboundDiskItemSetProgramPacket.class;
        }
    }
}
