package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% case minecraft_version %}
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;
{% endcase %}
import dan200.computercraft.api.ComputerCraftAPI;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
import dan200.computercraft.api.ForgeComputerCraftAPI;
import dan200.computercraft.api.client.ComputerCraftAPIClient;
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import dan200.computercraft.api.client.turtle.RegisterTurtleModellersEvent;
{% endcase %}
import dan200.computercraft.api.client.turtle.TurtleUpgradeModeller;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.core.registries.BuiltInRegistries;
{% endcase %}
{% case minecraft_version %}
{% when "1.20.4" %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
{% case minecraft_version %}
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
{% endcase %}

{% case minecraft_version %}
{% when "1.20.4" %}
/** Registers SFM's public CC:Tweaked integration points once CC:Tweaked is known to be loaded. */
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.21", "1.21.1", "26.1.2" %}
/**
 * Registers SFM's public CC:Tweaked integration points once CC:Tweaked is known to be loaded.
 */
{% endcase %}
{% case minecraft_version %}
{% when "1.20.4" %}
@MCVersionDependentBehaviour // CC:Tweaked 1.110.2+ uses NeoForge block capabilities and turtle modeller events
{% when "1.21" %}
@MCVersionDependentBehaviour // CC:Tweaked 1.111.0+ uses NeoForge block capabilities
{% when "1.21.1", "26.1.2" %}
@MCVersionDependentBehaviour // CC:Tweaked 1.113.1+ uses NeoForge block capabilities
{% endcase %}
public final class ComputerCraftIntegration {
    private static boolean registered;

    private ComputerCraftIntegration() {

    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
    public static void register() {
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private static void registerIntegration() {
{% endcase %}

        if (registered) return;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
        ForgeComputerCraftAPI.registerPeripheralProvider(new SFMNetworkPeripheralProvider());
{% endcase %}
        ComputerCraftAPI.registerGenericSource(new SFMInventoryMethods());
        registered = true;
    }

    @SFMSubscribeEvent(requiredModId = "computercraft")
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
    public static void onCommonSetup(FMLCommonSetupEvent event) {
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private static void onCommonSetup(FMLCommonSetupEvent event) {
{% endcase %}
        if (SFMModCompat.isComputerCraftLoaded()) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
            event.enqueueWork(ComputerCraftIntegration::register);
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            event.enqueueWork(ComputerCraftIntegration::registerIntegration);
{% endcase %}
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
    @SFMSubscribeEvent(requiredModId = "computercraft")
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (SFMModCompat.isComputerCraftLoaded()) {
            event.enqueueWork(() -> ComputerCraftAPIClient.registerTurtleUpgradeModeller(
                    SFMComputerCraftTurtleUpgrades.LABELER.get(),
                    TurtleUpgradeModeller.flatItem()
            ));
        }
    }
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @SFMSubscribeEvent(requiredModId = "computercraft")
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

{% case minecraft_version %}
{% when "1.20.4" %}
    /**
     * CC:Tweaked 1.110.2 turtles implement Minecraft's public {@link Container}
     * contract but do not register NeoForge's item-handler capability themselves.
     */
{% when "1.21", "1.21.1", "26.1.2" %}
    /**
     * CC:Tweaked turtles implement Minecraft's public {@link Container} contract
     * but do not register NeoForge's item-handler capability themselves. This
     * narrow adapter lets SFM transfer turtle inventory without a CC internal import.
     */
{% endcase %}
    private static void registerTurtleInventoryCapabilities(RegisterCapabilitiesEvent event) {

        Block normalTurtle = BuiltInRegistries.BLOCK.get(
{% case minecraft_version %}
{% when "1.20.4" %}
                new ResourceLocation(ComputerCraftAPI.MOD_ID, "turtle_normal")
{% when "1.21", "1.21.1", "26.1.2" %}
                SFMResourceLocation.fromNamespaceAndPath(ComputerCraftAPI.MOD_ID, "turtle_normal")
{% endcase %}
        );
        Block advancedTurtle = BuiltInRegistries.BLOCK.get(
{% case minecraft_version %}
{% when "1.20.4" %}
                new ResourceLocation(ComputerCraftAPI.MOD_ID, "turtle_advanced")
{% when "1.21", "1.21.1", "26.1.2" %}
                SFMResourceLocation.fromNamespaceAndPath(ComputerCraftAPI.MOD_ID, "turtle_advanced")
{% endcase %}
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

    @SFMSubscribeEvent(requiredModId = "computercraft")
    private static void registerTurtleModeller(RegisterTurtleModellersEvent event) {
        if (SFMModCompat.isComputerCraftLoaded()) {
            event.register(SFMComputerCraftTurtleUpgrades.LABELER.get(), TurtleUpgradeModeller.flatItem());
        }
    }
{% endcase %}
}
