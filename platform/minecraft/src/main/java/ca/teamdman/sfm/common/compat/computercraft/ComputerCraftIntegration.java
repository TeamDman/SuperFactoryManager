package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import dan200.computercraft.api.ForgeComputerCraftAPI;
import dan200.computercraft.api.detail.VanillaDetailRegistries;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Registers SFM's public CC:Tweaked integration points once CC:Tweaked is known to be loaded.
 */
@Mod.EventBusSubscriber(modid = SFM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ComputerCraftIntegration {
    private static boolean registered;

    private ComputerCraftIntegration() {

    }

    public static void register() {

        if (registered) return;
        ForgeComputerCraftAPI.registerPeripheralProvider(new SFMNetworkPeripheralProvider());
        VanillaDetailRegistries.ITEM_STACK.addProvider(new SFMItemDetailProvider());
        registered = true;
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (SFMModCompat.isComputerCraftLoaded()) {
            event.enqueueWork(ComputerCraftIntegration::register);
        }
    }
}
