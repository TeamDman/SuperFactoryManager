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

import com.github.bsideup.jabel.Desugar;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Desugar
public record ServerboundNetworkToolUsePacket(
        BlockPos blockPosition,
        EnumFacing blockFace
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundNetworkToolUsePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundNetworkToolUsePacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeBlockPos(msg.blockPosition);
            friendlyByteBuf.writeEnum(msg.blockFace);
        }

        @Override
        public ServerboundNetworkToolUsePacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ServerboundNetworkToolUsePacket(
                    friendlyByteBuf.readBlockPos(),
                    friendlyByteBuf.readEnum(EnumFacing.class)
            );
        }

        @Override
        public void handle(
                ServerboundNetworkToolUsePacket msg,
                SFMPacketHandlingContext context
        ) {
            {
                // we don't know if the player has the program edit screen open from a manager or a disk in hand
                EntityPlayerMP player = context.sender();
                if (player == null) return;
                World level = player.getServerWorld();
                BlockPos pos = msg.blockPosition();
                if (!level.isBlockLoaded(pos)) return;
                StringBuilder payload = new StringBuilder()
                        .append("---- block position ----\n")
                        .append(pos)
                        .append("\n---- block state ----\n");
                IBlockState state = level.getBlockState(pos);
                payload.append(state).append("\n");

                List<CableNetwork> foundNetworks = new ArrayList<>();
                for (EnumFacing direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                    BlockPos cablePosition = pos.offset(direction);
                    CableNetworkManager
                            .getOrRegisterNetworkFromCablePosition(level, cablePosition)
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

                TileEntity entity = level.getTileEntity(pos);
                if (entity != null) {
                    if (SFMEnvironmentUtils.isInIDE()) {
                        payload.append("---- (dev only) block entity ----\n");
                        payload.append(entity).append("\n");
                    }
                }
                payload.append("---- capabilityKind directions ----\n");
                for (var cap : (Iterable<SFMBlockCapabilityKind<?>>) SFMWellKnownCapabilities.streamCapabilities()::iterator) {
                    String directions = DirectionQualifier.EVERY_DIRECTION
                            .stream()
                            .filter(dir -> SFMBlockCapabilityDiscovery
                                    .discoverCapabilityFromLevel(level, cap, pos, dir).isPresent())
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
                directions[0] = msg.blockFace;
                directions[1] = null;
                int assignmentIndex = 2;
                for (EnumFacing direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                    if (direction == msg.blockFace) continue;
                    directions[assignmentIndex++] = direction;
                }

                String[] messages = new String[directions.length];
                messages[0] = String.format("---- exports for selected face: %s ----", msg.blockFace);
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
                                    entry.getKey(),
                                    entry.getValue().get(),
                                    level,
                                    pos,
                                    directions[index]
                            ))
                            .filter(s -> !s.trim().isEmpty())
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
                        payload.append(entity.serializeNBT()).append("\n");
                    }
                }


                SFMPackets.sendToPlayer(player, new ClientboundInputInspectionResultsPacket(
                        SFMPacketDaddy.truncate(
                                payload.toString(),
                                ClientboundInputInspectionResultsPacket.MAX_RESULTS_LENGTH
                        )));
            }
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundNetworkToolUsePacket> {

        @Override
        SFMPacketDaddy<ServerboundNetworkToolUsePacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundNetworkToolUsePacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
