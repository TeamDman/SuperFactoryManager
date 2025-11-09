package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfml.ast.Program;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ServerboundLabelInspectionRequestPacket extends SFMPacket<ServerboundLabelInspectionRequestPacket> {
    private static final int MAX_RESULTS_LENGTH = 20480;

    private String label;

    public ServerboundLabelInspectionRequestPacket(String label) {
        this.label = label;
    }

    public ServerboundLabelInspectionRequestPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        try {
            label = packetBuffer.readString(Program.MAX_LABEL_LENGTH);
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeString(label);
    }

    @Override
    public IMessage onMessage(ServerboundLabelInspectionRequestPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            SFM.LOGGER.info("Received label inspection request packet from player {}", player.getUniqueID());
            LabelPositionHolder labelPositionHolder;
            if (player.openContainer instanceof ManagerContainerMenu) {
                ManagerContainerMenu mcm = (ManagerContainerMenu) player.openContainer;
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
            payload.append("-- Positions for label \"").append(message.label).append("\" --\n");
            payload.append(labelPositionHolder.getPositions(message.label).size()).append(" assignments\n");
            payload.append("-- Summary --\n");
            World world = player.getEntityWorld();
            labelPositionHolder.getPositions(message.label).forEach(pos -> {
                payload
                        .append(pos.getX())
                        .append(",")
                        .append(pos.getY())
                        .append(",")
                        .append(pos.getZ());
                if (world.isBlockLoaded(pos)) {
                    payload
                            .append(" -- ")
                            .append(world.getBlockState(pos).getBlock().getLocalizedName());
                } else {
                    payload
                            .append(" -- chunk not loaded");
                }
                payload
                        .append("\n");
            });

            payload.append("\n\n\n-- Detailed --\n");
            for (BlockPos pos : labelPositionHolder.getPositions(message.label)) {
                if (payload.length() > 20000) {
                    payload.append("... (truncated)");
                    break;
                }
                payload
                        .append(pos.getX())
                        .append(",")
                        .append(pos.getY())
                        .append(",")
                        .append(pos.getZ());
                if (world.isBlockLoaded(pos)) {
                    payload
                            .append(" -- ")
                            .append(world.getBlockState(pos).getBlock().getLocalizedName());

                    payload.append("\n").append(ServerboundContainerExportsInspectionRequestPacket
                            .buildInspectionResults(world, pos)
                            .indent(1));
                } else {
                    payload
                            .append(" -- chunk not loaded");
                }
                payload
                        .append("\n");
            }
            SFM.LOGGER.info(
                    "Sending payload response length={} to playerજી",
                    payload.length(),
                    player.getUniqueID()
            );
            SFMPackets.sendToPlayer(player, new ClientboundLabelInspectionResultsPacket(
                    SFMAdvancedPacket.truncate(
                            payload.toString(),
                            ServerboundLabelInspectionRequestPacket.MAX_RESULTS_LENGTH
                    )
            ));
        });
        return null;
    }
}