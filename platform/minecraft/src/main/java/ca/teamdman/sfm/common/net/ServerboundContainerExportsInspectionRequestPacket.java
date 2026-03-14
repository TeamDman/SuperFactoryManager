package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.SFMASTUtils;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfml.ast.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record ServerboundContainerExportsInspectionRequestPacket(
        int windowId,
        BlockPos pos
) implements SFMPacket {
    public static String buildInspectionResults(
            Level level,
            BlockPos pos
    ) {
        StringBuilder sb = new StringBuilder();
        for (Direction direction : SFMDirections.DIRECTIONS_WITH_NULL) {
            sb.append("-- ").append(direction).append("\n");
            int len = sb.length();
            //noinspection unchecked,rawtypes
            SFMResourceTypes.registry().entries().stream().map(entry -> buildInspectionResults(
                            (ResourceKey) entry.getKey(),
                            entry.getValue(),
                            level,
                            pos,
                            direction
                    ))
                    .filter(s -> !s.isBlank())
                    .forEach(results -> sb.append(results).append("\n"));
            if (sb.length() == len) {
                sb.append("No exports found");
            }
            sb.append("\n");
        }

        if (SFMModCompat.isMekanismLoaded()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                sb.append(SFMMekanismCompat.gatherInspectionResults(be)).append("\n");
            }
        }

        return sb.toString();
    }

    public static <STACK, ITEM, CAP> String buildInspectionResults(
            ResourceKey<ResourceType<STACK, ITEM, CAP>> resourceTypeResourceKey,
            ResourceType<STACK, ITEM, CAP> resourceType,
            Level level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        StringBuilder sb = new StringBuilder();
        SFMBlockCapabilityResult<CAP> capResult = SFMBlockCapabilityDiscovery.discoverCapabilityFromLevel(
                level,
                resourceType.CAPABILITY_KIND,
                pos,
                direction
        );
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
                            direction
                    );
                    sb.append(inputStatement.toStringPretty()).append("\n");
                });

                List<ResourceLimit> resourceLimitList = new ArrayList<>();
                slotContents.forEach((slot, stack) -> {
                    ResourceLocation stackId = resourceType.getRegistryKeyForStack(stack);
                    ResourceIdentifier<STACK, ITEM, CAP> resourceIdentifier = new ResourceIdentifier<>(
                            resourceTypeResourceKey,
                            stackId
                    );
                    ResourceLimit resourceLimit = new ResourceLimit(
                            new ResourceIdSet(List.of(resourceIdentifier)),
                            Limit.MAX_QUANTITY_NO_RETENTION, With.ALWAYS_TRUE
                    );
                    resourceLimitList.add(resourceLimit);
                });
                InputStatement inputStatement = new InputStatement(
                        new ResourceAccess(
                                List.of(new LabelExpressionSingle( new Label("target"))),
                                new RoundRobin(RoundRobinBehaviour.UNMODIFIED), new SideQualifier(List.of(Side.fromDirection(direction))),
                                SlotQualifier.DEFAULT
                        ),
                        new ResourceLimits(
                                resourceLimitList.stream().distinct().toList(),
                                ResourceIdSet.EMPTY
                        ),
                        false
                );
                sb.append(inputStatement.toStringPretty());
            }

        }
        String result = sb.toString();
        if (!result.isBlank()) {
            BlockEntity be = level.getBlockEntity(pos);
            //noinspection DataFlowIssue
            if (be != null && direction == null && SFMWellKnownRegistries.BLOCK_ENTITY_TYPES
                    .getId(be.getType())
                    .getNamespace()
                    .equals("mekanism")) {
                return "-- "
                       + LocalizationKeys.CONTAINER_INSPECTOR_MEKANISM_NULL_DIRECTION_WARNING.getStub()
                       + "\n"
                       + result;
            }
        }
        return result;
    }

    public static class Daddy implements SFMPacketDaddy<ServerboundContainerExportsInspectionRequestPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }

        @Override
        public void encode(
                ServerboundContainerExportsInspectionRequestPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
        }

        @Override
        public ServerboundContainerExportsInspectionRequestPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ServerboundContainerExportsInspectionRequestPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readBlockPos()
            );
        }

        @Override
        public void handle(
                ServerboundContainerExportsInspectionRequestPacket msg,
                SFMPacketHandlingContext context
        ) {
            context.handleServerboundContainerPacket(
                    AbstractContainerMenu.class,
                    BlockEntity.class,
                    msg.pos,
                    msg.windowId,
                    (menu, blockEntity) -> {
                        assert blockEntity.getLevel() != null;
                        String payload = buildInspectionResults(blockEntity.getLevel(), blockEntity.getBlockPos());
                        var player = context.sender();

                        SFMPackets.sendToPlayer(
                                () -> player, new ClientboundContainerExportsInspectionResultsPacket(
                                        msg.windowId,
                                        SFMPacketDaddy.truncate(
                                                payload,
                                                ClientboundContainerExportsInspectionResultsPacket.MAX_RESULTS_LENGTH
                                        )
                                )
                        );
                    }
            );
        }

        @Override
        public Class<ServerboundContainerExportsInspectionRequestPacket> getPacketClass() {
            return ServerboundContainerExportsInspectionRequestPacket.class;
        }
    }

}
