package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.StringUtil;
import ca.teamdman.sfml.ast.Program;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

@Desugar
public record ServerboundLabelInspectionRequestPacket(
        String label
) implements SFMPacket<ServerboundLabelInspectionRequestPacket> {
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
            EntityPlayerMP player = context.sender();
            if (player == null) return;
            SFM.LOGGER.info("Received label inspection request packet from player {}", player.getUniqueID().toString());
            LabelPositionHolder labelPositionHolder;
            if (player.openContainer instanceof ManagerContainerMenu mcm) {
                SFM.LOGGER.info("Player is using a manager container menu - will append additional info to payload");
                labelPositionHolder = LabelPositionHolder.from(mcm.getSlot(0).getStack());
            } else {
                if (player.getHeldItemMainhand().getItem() == SFMItems.DISK_ITEM) {
                    labelPositionHolder = LabelPositionHolder.from(player.getHeldItemMainhand());
                } else if (player.getHeldItemOffhand().getItem() == SFMItems.DISK_ITEM) {
                    labelPositionHolder = LabelPositionHolder.from(player.getHeldItemOffhand());
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
            labelPositionHolder.getPositions(msg.label()).blockPosIterator().forEach(pos -> {
                payload
                        .append(pos.getX())
                        .append(",")
                        .append(pos.getY())
                        .append(",")
                        .append(pos.getZ());
                if (player.getServerWorld().isBlockLoaded(pos)) {
                    payload
                            .append(" -- ")
                            .append(player.getServerWorld().getBlockState(pos).getBlock().getLocalizedName());
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
                if (player.getServerWorld().isBlockLoaded(pos)) {
                    payload
                            .append(" -- ")
                            .append(player.getServerWorld().getBlockState(pos).getBlock().getLocalizedName());

                    payload.append("\n").append(StringUtil.indentPonyfill(ServerboundContainerExportsInspectionRequestPacket
                                                        .buildInspectionResults(player.getServerWorld(), pos),
                                                        1));
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
                    player.getUniqueID().toString()
            );
            SFMPackets.sendToPlayer(player, new ClientboundLabelInspectionResultsPacket(
                    SFMPacketDaddy.truncate(
                            payload.toString(),
                            ServerboundLabelInspectionRequestPacket.MAX_RESULTS_LENGTH
                    )
            ));
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundLabelInspectionRequestPacket> {

        @Override
        SFMPacketDaddy<ServerboundLabelInspectionRequestPacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundLabelInspectionRequestPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
