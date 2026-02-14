package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.math.BlockPos;

@Desugar
public record ServerboundManagerClearLogsPacket(
        int windowId,
        BlockPos pos
) implements SFMPacket<ServerboundManagerClearLogsPacket> {
    public static class Daddy implements SFMPacketDaddy<ServerboundManagerClearLogsPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundManagerClearLogsPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
        }

        @Override
        public ServerboundManagerClearLogsPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ServerboundManagerClearLogsPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readBlockPos()
            );
        }

        @Override
        public void handle(
                ServerboundManagerClearLogsPacket msg,
                SFMPacketHandlingContext context
        ) {
            context.handleServerboundContainerPacket(
                    ManagerContainerMenu.class,
                    ManagerBlockEntity.class,
                    msg.pos,
                    msg.windowId,
                    (menu, manager) -> {
                        manager.logger.clear();
                        manager.logger.info(x -> x.accept(LocalizationKeys.LOGS_GUI_CLEAR_LOGS_BUTTON_PACKET_RECEIVED.get()));
                    }
            );
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundManagerClearLogsPacket> {

        @Override
        SFMPacketDaddy<ServerboundManagerClearLogsPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundManagerClearLogsPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
