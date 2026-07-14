package ca.teamdman.sfm.common.compat.computercraft;

import dan200.computercraft.api.ForgeComputerCraftAPI;
import dan200.computercraft.api.detail.VanillaDetailRegistries;

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
        VanillaDetailRegistries.ITEM_STACK.addProvider(new SFMItemDetailProvider());
        registered = true;
    }
}
