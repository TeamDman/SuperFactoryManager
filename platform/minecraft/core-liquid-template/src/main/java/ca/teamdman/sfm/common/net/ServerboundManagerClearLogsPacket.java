package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}

public record ServerboundManagerClearLogsPacket(
        int windowId,
        BlockPos pos
) implements SFMPacket {
    @SFMLocalizationDatagen
    public static final LocalizationEntry LOGS_GUI_CLEAR_LOGS_BUTTON_PACKET_RECEIVED = new LocalizationEntry(
            "gui.sfm.logs.button.clear_logs.packet_received",
            "Cleared logs"
    );

    public static class Daddy implements SFMPacketDaddy<ServerboundManagerClearLogsPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundManagerClearLogsPacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundManagerClearLogsPacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundManagerClearLogsPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
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
                        manager.logger.info(x -> x.accept(LOGS_GUI_CLEAR_LOGS_BUTTON_PACKET_RECEIVED.get()));
                    }
            );
        }

        @Override
        public Class<ServerboundManagerClearLogsPacket> getPacketClass() {
            return ServerboundManagerClearLogsPacket.class;
        }
    }
}
