package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record ServerboundManagerRebuildPacket(
        int windowId,
        BlockPos pos
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundManagerRebuildPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundManagerRebuildPacket msg,
                RegistryFriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
        }

        @Override
        public ServerboundManagerRebuildPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
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
                            manager.logger.warn(x -> x.accept(LocalizationKeys.LOG_MANAGER_CABLE_NETWORK_REBUILD.get()));
                            player.sendSystemMessage(
                                LocalizationKeys.CHAT_MANAGER_CABLE_NETWORK_REBUILD_SUCCESS.getComponent(msg.pos())
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
                                LocalizationKeys.CHAT_MANAGER_CABLE_NETWORK_REBUILD_FAILED_FALLBACK.getComponent(msg.pos())
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
