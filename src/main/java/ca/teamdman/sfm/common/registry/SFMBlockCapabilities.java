package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockcapabilityprovider.CauldronBlockCapabilityProvider;
import ca.teamdman.sfm.common.blockentity.ProxyBlockEntity;
import ca.teamdman.sfm.common.compat.SFMCompat;
import com.google.common.collect.Maps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@EventBusSubscriber(modid = SFM.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class SFMBlockCapabilities {

    @SubscribeEvent
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SFMBlockEntities.PRINTING_PRESS_BLOCK_ENTITY.get(),
                (blockEntity, direction) -> blockEntity.INVENTORY
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                SFMBlockEntities.WATER_TANK_BLOCK_ENTITY.get(),
                (blockEntity, direction) -> blockEntity.TANK
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                SFMBlockEntities.BATTERY_BLOCK_ENTITY.get(),
                (blockEntity, direction) -> blockEntity.CONTAINER
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
                SFMBlocks.TEST_BARREL_BLOCK.get()
        );
        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                new CauldronBlockCapabilityProvider(),
                Blocks.CAULDRON,
                Blocks.LAVA_CAULDRON,
                Blocks.WATER_CAULDRON
        );

        handleCapabilityProxyRegistration(event);
    }

    /*
     * https://github.com/CyclopsMC/CapabilityProxy/blob/master-1.21/loader-neoforge/src/main/java/org/cyclops/capabilityproxy/blockentity/BlockEntityItemCapabilityProxyNeoForgeConfig.java
     */
    private static void handleCapabilityProxyRegistration(RegisterCapabilitiesEvent event) {
        ProxyBlockEntity.BLOCK_TO_ITEM_CAPABILITIES = SFMCompat.getCapabilityMap();

        for (BlockCapability<?, ?> blockCapability : SFMCompat.getCapabilities()) {
            event.registerBlockEntity(
                    (BlockCapability) blockCapability,
                    SFMBlockEntities.PROXY_BLOCK_ENTITY.get(),
                    (object, context) -> object.getCapability((BlockCapability) blockCapability, context)
            );
        }
    }
}
