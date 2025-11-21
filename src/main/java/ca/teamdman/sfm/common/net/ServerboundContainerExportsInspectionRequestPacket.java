package ca.teamdman.sfm.common.net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import ca.teamdman.sfm.common.util.SFMASTUtils;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfml.ast.*;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class ServerboundContainerExportsInspectionRequestPacket extends
                                                                SFMAdvancedPacket<ServerboundContainerExportsInspectionRequestPacket> {

    private int windowId;
    private BlockPos pos;

    public ServerboundContainerExportsInspectionRequestPacket(int windowId, BlockPos pos) {
        this.windowId = windowId;
        this.pos = pos;
    }

    public ServerboundContainerExportsInspectionRequestPacket() {}

    public static String buildInspectionResults(
                                                World world,
                                                BlockPos pos) {
        StringBuilder sb = new StringBuilder();
        for (EnumFacing direction : SFMDirections.DIRECTIONS_WITH_NULL) {
            sb.append("-- ").append(direction).append("\n");
            int len = sb.length();
            // noinspection unchecked,rawtypes
            SFMResourceTypes.registry().getEntries().stream().map(entry -> buildInspectionResults(
                    (ResourceLocation) entry.getKey(),
                    entry.getValue().get(),
                    world,
                    pos,
                    direction))
                    .filter(s -> !s.trim().isEmpty())
                    .forEach(results -> sb.append(results).append("\n"));
            if (sb.length() == len) {
                sb.append("No exports found");
            }
            sb.append("\n");
        }

        if (SFMModCompat.isMekanismLoaded()) {
            // TileEntity be = world.getTileEntity(pos);
            // if (be != null) {
            // sb.append(SFMMekanismCompat.gatherInspectionResults(be)).append("\n");
            // }
        }

        return sb.toString();
    }

    public static <STACK, ITEM, CAP> String buildInspectionResults(
                                                                   ResourceLocation resourceTypeResourceKey,
                                                                   ResourceType<STACK, ITEM, CAP> resourceType,
                                                                   World world,
                                                                   BlockPos pos,
                                                                   @Nullable EnumFacing direction) {
        StringBuilder sb = new StringBuilder();
        SFMBlockCapabilityResult<CAP> capResult = SFMBlockCapabilityDiscovery.discoverCapabilityFromLevel(
                world,
                resourceType.CAPABILITY_KIND,
                pos,
                direction);
        if (capResult.isPresent()) {
            CAP cap = capResult.unwrap();
            int slots = resourceType.getSlots(cap);
            Int2ObjectMap<STACK> slotContents = new Int2ObjectArrayMap<>(slots);
            for (int slot = 0; slot < slots; slot++) {
                STACK stack = resourceType.getStackInSlot(cap, slot);
                if (!resourceType.isEmpty(stack)) {
                    slotContents.put(slot, stack);
                }
            }

            if (!slotContents.isEmpty()) {
                slotContents.forEach((slot, stack) -> {
                    InputStatement inputStatement = SFMASTUtils.getInputStatementForStack(
                            resourceTypeResourceKey,
                            resourceType,
                            stack,
                            "target",
                            slot,
                            false,
                            direction);
                    sb.append(inputStatement.toStringPretty()).append("\n");
                });

                List<ResourceLimit> resourceLimitList = new ArrayList<>();
                slotContents.forEach((slot, stack) -> {
                    ResourceLocation stackId = resourceType.getRegistryKeyForStack(stack);
                    ResourceIdentifier<STACK, ITEM, CAP> resourceIdentifier = new ResourceIdentifier<>(
                            resourceTypeResourceKey,
                            stackId);
                    ResourceLimit resourceLimit = new ResourceLimit(
                            new ResourceIdSet(Arrays.asList(resourceIdentifier)),
                            Limit.MAX_QUANTITY_NO_RETENTION, With.ALWAYS_TRUE);
                    resourceLimitList.add(resourceLimit);
                });
                InputStatement inputStatement = new InputStatement(
                        new LabelAccess(
                                Arrays.asList(new Label("target")),
                                new DirectionQualifier(
                                        direction == null ? EnumSet.noneOf(EnumFacing.class) : EnumSet.of(direction)),
                                NumberRangeSet.MAX_RANGE,
                                RoundRobin.disabled()),
                        new ResourceLimits(
                                resourceLimitList.stream().distinct().collect(java.util.stream.Collectors.toList()),
                                ResourceIdSet.EMPTY),
                        false);
                sb.append(inputStatement.toStringPretty());
            }

        }
        String result = sb.toString();
        return result;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        windowId = buf.readInt();
        pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(windowId);
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
    }

    @Override
    public void handle(
                       ServerboundContainerExportsInspectionRequestPacket msg,
                       SFMPacketHandlingContext context) {
        context.handleServerboundContainerPacket(
                Container.class,
                TileEntity.class,
                msg.pos,
                msg.windowId,
                (menu, blockEntity) -> {
                    assert blockEntity.getWorld() != null;
                    String payload = buildInspectionResults(blockEntity.getWorld(), blockEntity.getPos());
                    var player = context.serverPlayer();

                    SFMPackets.sendToPlayer(
                            player, new ClientboundContainerExportsInspectionResultsPacket(
                                    msg.windowId,
                                    SFMAdvancedPacket.truncate(
                                            payload,
                                            ClientboundContainerExportsInspectionResultsPacket.MAX_RESULTS_LENGTH)));
                });
    }
}
