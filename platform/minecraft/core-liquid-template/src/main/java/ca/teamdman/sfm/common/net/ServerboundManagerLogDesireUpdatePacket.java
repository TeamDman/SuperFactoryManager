package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}

public record ServerboundManagerLogDesireUpdatePacket(
        int windowId,
        BlockPos pos,
        boolean isLogScreenOpen
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundManagerLogDesireUpdatePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundManagerLogDesireUpdatePacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
            friendlyByteBuf.writeBoolean(msg.isLogScreenOpen());
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundManagerLogDesireUpdatePacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundManagerLogDesireUpdatePacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
            return new ServerboundManagerLogDesireUpdatePacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readBlockPos(),
                    friendlyByteBuf.readBoolean()
            );
        }

        @Override
        public void handle(
                ServerboundManagerLogDesireUpdatePacket msg,
                SFMPacketHandlingContext context
        ) {
            context.handleServerboundContainerPacket(
                    ManagerContainerMenu.class,
                    ManagerBlockEntity.class,
                    msg.pos,
                    msg.windowId,
                    (menu, manager) -> {
                        menu.isLogScreenOpen = msg.isLogScreenOpen();
                        manager.sendUpdatePacket();
                    }
            );
        }

        @Override
        public Class<ServerboundManagerLogDesireUpdatePacket> getPacketClass() {
            return ServerboundManagerLogDesireUpdatePacket.class;
        }
    }
}
