package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.common.capability.IRedstoneSignalStorage;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/// 1.19.2 -> RegisterCapabilitiesEvent event listener method
/// 1.20.3 -> BlockCapability.createSided static field
@MCVersionDependentBehaviour
public class SFMCapabilities {
    @SFMSubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IRedstoneSignalStorage.class);
    }
}
