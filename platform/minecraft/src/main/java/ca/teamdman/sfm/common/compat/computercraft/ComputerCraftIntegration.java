package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.detail.VanillaDetailRegistries;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * Registers SFM's public CC:Tweaked integration points once CC:Tweaked is known to be loaded.
 */
@MCVersionDependentBehaviour // CC:Tweaked 1.110.2+ uses NeoForge block capabilities
public final class ComputerCraftIntegration {
    private static boolean detailsRegistered;

    private ComputerCraftIntegration() {

    }

    private static void registerDetails() {

        if (detailsRegistered) return;
        VanillaDetailRegistries.ITEM_STACK.addProvider(new SFMItemDetailProvider());
        detailsRegistered = true;
    }

    @SFMSubscribeEvent
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        if (SFMModCompat.isComputerCraftLoaded()) {
            event.enqueueWork(ComputerCraftIntegration::registerDetails);
        }
    }

    @SFMSubscribeEvent
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {

        if (!SFMModCompat.isComputerCraftLoaded()) return;

        event.registerBlock(
                PeripheralCapability.get(),
                new SFMNetworkPeripheralProvider(),
                SFMBlocks.MANAGER.get(),
                SFMBlocks.TUNNELLED_MANAGER.get(),
                SFMBlocks.CABLE.get(),
                SFMBlocks.CABLE_FACADE.get(),
                SFMBlocks.FANCY_CABLE.get(),
                SFMBlocks.FANCY_CABLE_FACADE.get(),
                SFMBlocks.TOUGH_CABLE.get(),
                SFMBlocks.TOUGH_CABLE_FACADE.get(),
                SFMBlocks.TOUGH_FANCY_CABLE.get(),
                SFMBlocks.TOUGH_FANCY_CABLE_FACADE.get(),
                SFMBlocks.TUNNELLED_CABLE.get(),
                SFMBlocks.TUNNELLED_CABLE_FACADE.get(),
                SFMBlocks.TUNNELLED_FANCY_CABLE.get(),
                SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE.get()
        );

        registerTurtleInventoryCapabilities(event);
    }

    /**
     * CC:Tweaked 1.110.2 turtles implement Minecraft's public {@link Container}
     * contract but do not register NeoForge's item-handler capability themselves.
     * Publishing this narrow adapter is what lets SFM move items to and from a
     * turtle without using a CC:Tweaked internal type.
     */
    private static void registerTurtleInventoryCapabilities(RegisterCapabilitiesEvent event) {

        Block normalTurtle = BuiltInRegistries.BLOCK.get(
                new ResourceLocation(ComputerCraftAPI.MOD_ID, "turtle_normal")
        );
        Block advancedTurtle = BuiltInRegistries.BLOCK.get(
                new ResourceLocation(ComputerCraftAPI.MOD_ID, "turtle_advanced")
        );
        if (normalTurtle == Blocks.AIR || advancedTurtle == Blocks.AIR) return;

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> blockEntity instanceof Container inventory
                        ? new InvWrapper(inventory)
                        : null,
                normalTurtle,
                advancedTurtle
        );
    }
}
