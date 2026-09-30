package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfml.ast.Program;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf buf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf buf
{% endcase %}
        ) {
            buf.writeUtf(msg.programString, Program.MAX_PROGRAM_LENGTH);
            buf.writeEnum(msg.hand);
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundDiskItemSetProgramPacket decode(FriendlyByteBuf buf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundDiskItemSetProgramPacket decode(RegistryFriendlyByteBuf buf) {
{% endcase %}
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
