package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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
                        // perform rebuild by unregistering the cable network
                        CableNetworkManager.purgeCableNetworkForManager(manager);
                        manager.logger.warn(x -> x.accept(LocalizationKeys.LOG_MANAGER_CABLE_NETWORK_REBUILD.get()));

                        // log it
                        SFM.LOGGER.debug(
                                "{} performed rebuild for manager {} {}",
                                player.getName().getString(),
                                msg.pos(),
                                manager.getLevel()
                        );
                    }
            );
        }

        @Override
        public Class<ServerboundManagerRebuildPacket> getPacketClass() {
            return ServerboundManagerRebuildPacket.class;
        }
    }
}
