package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}

public record ServerboundManagerProgramPacket(
        int windowId,
        BlockPos pos,
        String program
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundManagerProgramPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundManagerProgramPacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
            friendlyByteBuf.writeUtf(msg.program(), Program.MAX_PROGRAM_LENGTH);
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundManagerProgramPacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundManagerProgramPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
            return new ServerboundManagerProgramPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readBlockPos(),
                    friendlyByteBuf.readUtf(Program.MAX_PROGRAM_LENGTH)
            );
        }

        @Override
        public void handle(
                ServerboundManagerProgramPacket msg,
                SFMPacketHandlingContext context
        ) {
            context.handleServerboundContainerPacket(
                    ManagerContainerMenu.class,
                    ManagerBlockEntity.class,
                    msg.pos,
                    msg.windowId,
                    (menu, manager) -> manager.setProgram(msg.program())
            );
        }

        @Override
        public Class<ServerboundManagerProgramPacket> getPacketClass() {
            return ServerboundManagerProgramPacket.class;
        }
    }
}
