package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.SFMEntityUtils;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record ServerboundLabelInspectionRequestPacket(
        String label
) implements SFMPacket {
    private static final int MAX_RESULTS_LENGTH = 20480;

    public static class Daddy implements SFMPacketDaddy<ServerboundLabelInspectionRequestPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundLabelInspectionRequestPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeUtf(msg.label(), Program.MAX_LABEL_LENGTH);
        }

        @Override
        public ServerboundLabelInspectionRequestPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ServerboundLabelInspectionRequestPacket(
                    friendlyByteBuf.readUtf(Program.MAX_LABEL_LENGTH)
            );
        }

        @Override
        public void handle(
                ServerboundLabelInspectionRequestPacket msg,
                SFMPacketHandlingContext context
        ) {
            // we don't know if the player has the program edit screen open from a manager or a disk in hand
            ServerPlayer player = context.sender();
            if (player == null) return;
            SFM.LOGGER.info("Received label inspection request packet from player {}", player.getStringUUID());
            LabelPositionHolder labelPositionHolder;
            if (player.containerMenu instanceof ManagerContainerMenu mcm) {
                SFM.LOGGER.info("Player is using a manager container menu - will append additional info to payload");
                labelPositionHolder = LabelPositionHolder.from(mcm.CONTAINER.getItem(0));
            } else {
                if (player.getMainHandItem().is(SFMItems.DISK.get())) {
                    labelPositionHolder = LabelPositionHolder.from(player.getMainHandItem());
                } else if (player.getOffhandItem().is(SFMItems.DISK.get())) {
                    labelPositionHolder = LabelPositionHolder.from(player.getOffhandItem());
                } else {
                    labelPositionHolder = null;
                }
            }
            if (labelPositionHolder == null) {
                SFM.LOGGER.info("Label holder wasn't found - aborting");
                return;
            }
            SFM.LOGGER.info("building payload");
            StringBuilder payload = new StringBuilder();
            payload.append("-- Positions for label \"").append(msg.label()).append("\" --\n");
            payload.append(labelPositionHolder.getPositions(msg.label()).size()).append(" assignments\n");
            payload.append("-- Summary --\n");
            ServerLevel level = SFMEntityUtils.getLevel(player);
            labelPositionHolder.getPositions(msg.label()).blockPosIterator().forEach(pos -> {
                payload
                        .append(pos.getX())
                        .append(",")
                        .append(pos.getY())
                        .append(",")
                        .append(pos.getZ());
                if (level.isLoaded(pos)) {
                    payload
                            .append(" -- ")
                            .append(level.getBlockState(pos).getBlock().getName().getString());
                } else {
                    payload
                            .append(" -- chunk not loaded");
                }
                payload
                        .append("\n");
            });

            payload.append("\n\n\n-- Detailed --\n");
            for (BlockPos.MutableBlockPos pos : labelPositionHolder.getPositions(msg.label()).blockPosIterator()) {
                if (payload.length() > 20_000) {
                    payload.append("... (truncated)");
                    break;
                }
                payload
                        .append(pos.getX())
                        .append(",")
                        .append(pos.getY())
                        .append(",")
                        .append(pos.getZ());
                if (level.isLoaded(pos)) {
                    payload
                            .append(" -- ")
                            .append(level.getBlockState(pos).getBlock().getName().getString());

                    payload.append("\n").append(ServerboundContainerExportsInspectionRequestPacket
                                                        .buildInspectionResults(level, pos)
                                                        .indent(1));
                } else {
                    payload
                            .append(" -- chunk not loaded");
                }
                payload
                        .append("\n");
            }
            SFM.LOGGER.info(
                    "Sending payload response length={} to player {}",
                    payload.length(),
                    player.getStringUUID()
            );
            SFMPackets.sendToPlayer(() -> player, new ClientboundLabelInspectionResultsPacket(
                    SFMPacketDaddy.truncate(
                            payload.toString(),
                            ServerboundLabelInspectionRequestPacket.MAX_RESULTS_LENGTH
                    )
            ));
        }

        @Override
        public Class<ServerboundLabelInspectionRequestPacket> getPacketClass() {
            return ServerboundLabelInspectionRequestPacket.class;
        }
    }

}
