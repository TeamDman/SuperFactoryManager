package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.cablenetwork.CableNetwork;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfml.ast.DirectionQualifier;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ServerboundNetworkToolUsePacket extends SFMPacket<ServerboundNetworkToolUsePacket> {
    private BlockPos blockPosition;
    private EnumFacing blockFace;

    public ServerboundNetworkToolUsePacket(BlockPos blockPosition, EnumFacing blockFace) {
        this.blockPosition = blockPosition;
        this.blockFace = blockFace;
    }

    public ServerboundNetworkToolUsePacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        blockPosition = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        blockFace = EnumFacing.values()[buf.readInt()];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(blockPosition.getX());
        buf.writeInt(blockPosition.getY());
        buf.writeInt(blockPosition.getZ());
        buf.writeInt(blockFace.ordinal());
    }

    @Override
    public IMessage onMessage(ServerboundNetworkToolUsePacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            World world = player.world;
            BlockPos pos = message.blockPosition;
            if (!world.isBlockLoaded(pos)) return;
            StringBuilder payload = new StringBuilder()
                    .append("---- block position ----\n")
                    .append(pos)
                    .append("\n---- block state ----\n");
            payload.append(world.getBlockState(pos)).append("\n");

            List<CableNetwork> foundNetworks = new ArrayList<>();
            for (EnumFacing direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                BlockPos cablePosition = pos.offset(direction);
                CableNetworkManager
                        .getOrRegisterNetworkFromCablePosition(world, cablePosition)
                        .ifPresent(foundNetworks::add);
            }
            payload.append("---- cable networks ----\n");
            if (foundNetworks.isEmpty()) {
                payload.append("No networks found\n");
            } else {
                for (CableNetwork network : foundNetworks) {
                    payload.append(network).append("\n");
                }
            }

            TileEntity entity = world.getTileEntity(pos);
            if (entity != null) {
                if (SFMEnvironmentUtils.isInIDE()) {
                    payload.append("---- (dev only) block entity ----\n");
                    payload.append(entity).append("\n");
                }
            }
            payload.append("---- capabilityKind directions ----\n");
            for (SFMBlockCapabilityKind<?> cap : SFMWellKnownCapabilities.getCapabilities()) {
                String directions = DirectionQualifier.EVERY_DIRECTION
                        .stream()
                        .filter(dir -> SFMBlockCapabilityDiscovery
                                .discoverCapabilityFromLevel(world, cap, pos, dir).isPresent())
                        .map(dir -> dir == null ? "NULL DIRECTION" : DirectionQualifier.directionToString(dir))
                        .collect(Collectors.joining(", ", "[", "]"));
                if (!directions.equals("[]")) {
                    payload
                            .append(cap.getName())
                            .append("\n")
                            .append(directions)
                            .append("\n");
                }
            }

            EnumFacing[] directions = new EnumFacing[SFMDirections.DIRECTIONS_WITHOUT_NULL.length + 1];
            directions[0] = message.blockFace;
            directions[1] = null;
            int assignmentIndex = 2;
            for (EnumFacing direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                if (direction == message.blockFace) continue;
                directions[assignmentIndex++] = direction;
            }

            String[] messages = new String[directions.length];
            messages[0] = String.format("---- exports for selected face: %s ----", message.blockFace);
            for (int i = 1; i < directions.length; i++) {
                messages[i] = String.format("---- exports for face: %s ----", directions[i]);
            }
            for (int i = 0; i < directions.length; i++) {
                int index = i;
                payload.append(messages[i]).append("\n");
                MutableBoolean foundExports = new MutableBoolean(false);
                //noinspection unchecked,rawtypes
                SFMResourceTypes.registry().getEntries()
                        .stream()
                        .map(entry -> ServerboundContainerExportsInspectionRequestPacket.buildInspectionResults(
                                (net.minecraft.util.ResourceKey) entry.getKey(),
                                entry.getValue(),
                                world,
                                pos,
                                directions[index]
                        ))
                        .filter(s -> !s.isBlank())
                        .forEach(results -> {
                            foundExports.setTrue();
                            payload.append(results).append("\n");
                        });
                if (foundExports.isFalse()) {
                    payload.append("No exports found");
                }
                payload.append("\n");
            }

            if (entity != null) {
                if (player.canUseCommand(2, "")) {
                    payload.append("---- (op only) nbt data ----\n");
                    payload.append(entity.writeToNBT(new NBTTagCompound())).append("\n");
                }
            }


            SFMPackets.SFM_CHANNEL.sendTo(new ClientboundInputInspectionResultsPacket(
                    payload.toString()
            ), player);
        });
        return null;
    }
}