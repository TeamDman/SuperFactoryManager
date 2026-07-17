package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.ForgeComputerCraftAPI;
import dan200.computercraft.api.client.ComputerCraftAPIClient;
import dan200.computercraft.api.client.turtle.TurtleUpgradeModeller;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Registers SFM's public CC:Tweaked integration points once CC:Tweaked is known to be loaded.
 */
public final class ComputerCraftIntegration {
    private static boolean registered;

    private ComputerCraftIntegration() {

    }

    public static void register() {

        if (registered) return;
        ForgeComputerCraftAPI.registerPeripheralProvider(new SFMNetworkPeripheralProvider());
        ComputerCraftAPI.registerGenericSource(new SFMInventoryMethods());
        registered = true;
    }

    @SFMSubscribeEvent(requiredModId = "computercraft")
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (SFMModCompat.isComputerCraftLoaded()) {
            event.enqueueWork(ComputerCraftIntegration::register);
        }
    }

    @SFMSubscribeEvent(requiredModId = "computercraft")
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (SFMModCompat.isComputerCraftLoaded()) {
            event.enqueueWork(() -> ComputerCraftAPIClient.registerTurtleUpgradeModeller(
                    SFMComputerCraftTurtleUpgrades.LABELER.get(),
                    TurtleUpgradeModeller.flatItem()
            ));
        }
    }
}
