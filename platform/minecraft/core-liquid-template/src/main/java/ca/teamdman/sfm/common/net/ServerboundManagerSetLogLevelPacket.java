package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Level;

public record ServerboundManagerSetLogLevelPacket(
        int windowId,

        BlockPos pos,

        String logLevel
) implements SFMPacket {
    public static final int MAX_LOG_LEVEL_NAME_LENGTH = 64;

    public static class Daddy implements SFMPacketDaddy<ServerboundManagerSetLogLevelPacket> {
        @Override
        public PacketDirection getPacketDirection() {

            return PacketDirection.SERVERBOUND;
        }

        @Override
        public void encode(
                ServerboundManagerSetLogLevelPacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {

            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
            friendlyByteBuf.writeUtf(msg.logLevel(), MAX_LOG_LEVEL_NAME_LENGTH);
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundManagerSetLogLevelPacket decode(FriendlyByteBuf friendlyByteBuf) {

{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundManagerSetLogLevelPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
            return new ServerboundManagerSetLogLevelPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readBlockPos(),
                    friendlyByteBuf.readUtf(MAX_LOG_LEVEL_NAME_LENGTH)
            );
        }

        @Override
        public void handle(
                ServerboundManagerSetLogLevelPacket msg,
                SFMPacketHandlingContext context
        ) {

            context.handleServerboundContainerPacket(
                    ManagerContainerMenu.class,
                    ManagerBlockEntity.class,
                    msg.pos,
                    msg.windowId,
                    (menu, manager) -> {
                        // get the level
                        Level logLevelObj = Level.getLevel(msg.logLevel());

                        // set the level
                        manager.setLogLevel(logLevelObj);

                        // log in manager
                        manager.logger.info(x -> x.accept(ManagerBlockEntity.LOG_LEVEL_UPDATED.get(
                                msg.logLevel())));

                        // log in server console
                        String sender = "UNKNOWN SENDER";
                        ServerPlayer player = context.sender();
                        if (player != null) {
                            sender = player.getName().getString();
                        }
                        SFM.LOGGER.debug(
                                "{} updated manager {} {} log level to {}",
                                sender,
                                msg.pos(),
                                manager.getLevel(),
                                msg.logLevel()
                        );
                    }
            );
        }

        @Override
        public Class<ServerboundManagerSetLogLevelPacket> getPacketClass() {

            return ServerboundManagerSetLogLevelPacket.class;
        }

    }

}
