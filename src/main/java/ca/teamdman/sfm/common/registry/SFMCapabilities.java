package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.common.capability.IRedstoneSignalStorage;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

public class SFMCapabilities {
    @SFMSubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IRedstoneSignalStorage.class);
    }
}
