package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record ServerboundManagerRebuildPacket(
        int windowId,

        BlockPos pos
) implements SFMPacket {
    @SFMLocalizationDatagen
    public static final LocalizationEntry CHAT_MANAGER_CABLE_NETWORK_REBUILD_FAILED_FALLBACK = new LocalizationEntry(
            "chat.sfm.manager.cable_network_rebuild.failed_fallback",
            "Failed to rebuild cable network at manager %s; purged all cable networks instead"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry CHAT_MANAGER_CABLE_NETWORK_REBUILD_SUCCESS = new LocalizationEntry(
            "chat.sfm.manager.cable_network_rebuild.success",
            "Rebuilt cable network at manager %s"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_MANAGER_CABLE_NETWORK_REBUILD = new LocalizationEntry(
            "log.sfm.manager.cable_network_rebuild",
            "User performed cable network rebuild"
    );

    public static class Daddy implements SFMPacketDaddy<ServerboundManagerRebuildPacket> {
        @Override
        public PacketDirection getPacketDirection() {

            return PacketDirection.SERVERBOUND;
        }

        @Override
        public void encode(
                ServerboundManagerRebuildPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {

            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
        }

        @Override
        public ServerboundManagerRebuildPacket decode(FriendlyByteBuf friendlyByteBuf) {

            return new ServerboundManagerRebuildPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readBlockPos()
            );
        }

        @Override
        public void handle(
                ServerboundManagerRebuildPacket msg,
                SFMPacketHandlingContext context
        ) {

            context.handleServerboundContainerPacket(
                    ManagerContainerMenu.class,
                    ManagerBlockEntity.class,
                    msg.pos,
                    msg.windowId,
                    (menu, manager) -> {
                        ServerPlayer player = context.sender();
                        if (player == null) {
                            SFM.LOGGER.error("Received {} from null player", this.getPacketClass().getName());
                            return;
                        }
                        try {
                            // perform rebuild by unregistering the cable network
                            CableNetworkManager.purgeCableNetworkForManager(manager);
                            manager.logger.warn(x -> x.accept(LOG_MANAGER_CABLE_NETWORK_REBUILD.get()));
                            player.sendSystemMessage(
                                    CHAT_MANAGER_CABLE_NETWORK_REBUILD_SUCCESS.getComponent(msg.pos().toString())
                            );

                            // log it
                            SFM.LOGGER.debug(
                                    "{} performed rebuild for manager {} {}",
                                    player.getName().getString(),
                                    msg.pos(),
                                    manager.getLevel()
                            );
                        } catch (Exception e) {
                            SFM.LOGGER.warn(
                                    "Failed to rebuild network for manager {} {}; purging all cable networks instead",
                                    msg.pos(),
                                    manager.getLevel(),
                                    e
                            );
                            CableNetworkManager.clear();
                            player.sendSystemMessage(
                                    CHAT_MANAGER_CABLE_NETWORK_REBUILD_FAILED_FALLBACK.getComponent(msg.pos().toString())
                            );
                        }
                    }
            );
        }

        @Override
        public Class<ServerboundManagerRebuildPacket> getPacketClass() {

            return ServerboundManagerRebuildPacket.class;
        }

    }

}
