package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.common.capability.IRedstoneSignalStorage;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class SFMCapabilities {
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IRedstoneSignalStorage.class);
    }
}
