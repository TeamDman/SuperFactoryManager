package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.common.capability.BufferBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.CauldronBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@MCVersionDependentBehaviour // 1.20.3+
public class SFMBlockCapabilities {

    @SuppressWarnings("Convert2Lambda")
    @SFMSubscribeEvent
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SFMBlockEntities.PRINTING_PRESS.get(),
                (blockEntity, direction) -> blockEntity.INVENTORY
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                SFMBlockEntities.WATER_TANK.get(),
                (blockEntity, direction) -> blockEntity.TANK
        );

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                new IBlockCapabilityProvider<>() {
                    @Override
                    public @Nullable IItemHandler getCapability(
                            Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable BlockEntity blockEntity,
                            Direction context
                    ) {

                        if (blockEntity instanceof BarrelBlockEntity bbe) {
                            return new InvWrapper(bbe);
                        }
                        return null;
                    }
                },
                SFMBlocks.TEST_BARREL.get()
        );

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                new IBlockCapabilityProvider<>() {
                    @Override
                    public @Nullable IItemHandler getCapability(
                            Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable BlockEntity blockEntity,
                            Direction context
                    ) {

                        if (blockEntity instanceof BarrelBlockEntity bbe) {
                            return new InvWrapper(bbe);
                        }
                        return null;
                    }
                },
                SFMBlocks.TEST_BARREL.get()
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                SFMBlockEntities.TEST_BARREL_TANK.get(),
                (blockEntity, direction) -> blockEntity.getTank()
        );

        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                new CauldronBlockCapabilityProvider(),
                Blocks.CAULDRON,
                Blocks.LAVA_CAULDRON,
                Blocks.WATER_CAULDRON
        );

        event.registerBlockEntity(
                SFMWellKnownCapabilities.ITEM_HANDLER.capabilityKind(),
                SFMBlockEntities.MANAGER.get(),
                (manager, direction) -> manager.invWrapper
        );

        SFMResourceTypes.registry().values().forEach(resourceType -> {
            registerCapabilitiesForResourceType(event, resourceType);
        });

    }

    private static <STACK, ITEM, CAP> void registerCapabilitiesForResourceType(
            RegisterCapabilitiesEvent event,
            ResourceType<STACK, ITEM, CAP> resourceType
    ) {

        BlockCapability<CAP, @Nullable Direction> resourceTypeBlockCapability = resourceType
                .capabilityKind()
                .capabilityKind();

        IBlockCapabilityProvider<CAP, @Nullable Direction> capabilityProxy = createCapabilityProxy(
                resourceTypeBlockCapability);

        for (SFMRegistryObject<Block, ?> tunnelledBlock : List.of(
                SFMBlocks.TUNNELLED_MANAGER,
                SFMBlocks.TUNNELLED_CABLE,
                SFMBlocks.TUNNELLED_CABLE_FACADE,
                SFMBlocks.TUNNELLED_FANCY_CABLE,
                SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE
        )) {

            event.registerBlock(
                    resourceTypeBlockCapability,
                    capabilityProxy,
                    tunnelledBlock.get()
            );
        }

        BufferBlockCapabilityProvider bufferCapabilityProvider = new BufferBlockCapabilityProvider();
        @SuppressWarnings({"unchecked", "rawtypes"})
        IBlockCapabilityProvider<?, @Nullable Direction> providerForKind = bufferCapabilityProvider
                .specialize((SFMBlockCapabilityKind) resourceType.capabilityKind());

        event.registerBlockEntity(
                resourceTypeBlockCapability,
                SFMBlockEntities.BUFFER.get(),
                (blockEntity, direction) -> {
                    //noinspection unchecked
                    return (CAP) providerForKind.getCapability(
                            blockEntity.getLevel(),
                            blockEntity.getBlockPos(),
                            blockEntity.getBlockState(),
                            blockEntity,
                            direction
                    );
                }
        );
    }

    @SuppressWarnings("Convert2Lambda")
    private static <T> IBlockCapabilityProvider<T, @Nullable Direction> createCapabilityProxy(BlockCapability<?, @Nullable Direction> cap) {

        return new IBlockCapabilityProvider<>() {
            @Override
            public @Nullable T getCapability(
                    Level level,
                    BlockPos blockPos,
                    BlockState blockState,
                    @Nullable BlockEntity blockEntity,
                    @Nullable Direction side
            ) {

                if (side == null) return null;
                var offset = blockPos.relative(side.getOpposite());
                //noinspection unchecked
                return (T) level.getCapability(cap, offset, side);
            }
        };
    }

}
