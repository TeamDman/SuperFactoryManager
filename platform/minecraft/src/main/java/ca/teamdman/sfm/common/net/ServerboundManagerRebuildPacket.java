package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

@Desugar
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
                        EntityPlayerMP player = context.sender();
                        if (player == null) {
                            SFM.LOGGER.error("Received {} from null player", this.getPacketClass().getName());
                            return;
                        }
                        try {
                            // perform rebuild by unregistering the cable network
                            CableNetworkManager.purgeCableNetworkForManager(manager);
                            manager.logger.warn(x -> x.accept(LocalizationKeys.LOG_MANAGER_CABLE_NETWORK_REBUILD.get()));
                            player.sendStatusMessage(
                                LocalizationKeys.CHAT_MANAGER_CABLE_NETWORK_REBUILD_SUCCESS.getComponent(msg.pos()),
                                false
                            );

                            // log it
                            SFM.LOGGER.debug(
                                "{} performed rebuild for manager {} {}",
                                player.getName(),
                                msg.pos(),
                                manager.getWorld()
                            );
                        } catch (Exception e) {
                            SFM.LOGGER.warn(
                                "Failed to rebuild network for manager {} {}; purging all cable networks instead",
                                msg.pos(),
                                manager.getLevel(),
                                e
                            );
                            CableNetworkManager.clear();
                            player.sendStatusMessage(
                                LocalizationKeys.CHAT_MANAGER_CABLE_NETWORK_REBUILD_FAILED_FALLBACK.getComponent(msg.pos()),
                                false
                            );
                        }
                    }
            );
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundManagerRebuildPacket> {

        @Override
        SFMPacketDaddy<ServerboundManagerRebuildPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundManagerRebuildPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
