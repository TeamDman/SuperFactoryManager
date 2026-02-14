package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfml.ast.Program;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.EnumHand;

@Desugar
public record ServerboundDiskItemSetProgramPacket(
        String programString,
        EnumHand hand
) implements SFMPacket<ServerboundDiskItemSetProgramPacket> {
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
            buf.writeString(SFMPacketDaddy.truncate(msg.programString, Program.MAX_PROGRAM_LENGTH));
            buf.writeEnumValue(msg.hand);
        }

        @Override
        public ServerboundDiskItemSetProgramPacket decode(FriendlyByteBuf buf) {
            return new ServerboundDiskItemSetProgramPacket(
                    buf.readString(Program.MAX_PROGRAM_LENGTH),
                    buf.readEnumValue(EnumHand.class)
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
            var stack = sender.getHeldItem(msg.hand);
            if (stack.getItem() instanceof DiskItem) {
                DiskItem.setProgram(stack, msg.programString);
                DiskItem.compileAndUpdateErrorsAndWarnings(stack, null, true);
                DiskItem.pruneIfDefault(stack);
            }
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundDiskItemSetProgramPacket> {

        @Override
        SFMPacketDaddy<ServerboundDiskItemSetProgramPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundDiskItemSetProgramPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
