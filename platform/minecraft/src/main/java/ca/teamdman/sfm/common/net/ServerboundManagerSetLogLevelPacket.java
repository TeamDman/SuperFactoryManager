package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
            friendlyByteBuf.writeUtf(msg.logLevel(), MAX_LOG_LEVEL_NAME_LENGTH);
        }

        @Override
        public ServerboundManagerSetLogLevelPacket decode(FriendlyByteBuf friendlyByteBuf) {
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
                        manager.logger.info(x -> x.accept(LocalizationKeys.LOG_LEVEL_UPDATED.get(
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
