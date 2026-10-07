package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.common.capability.IRedstoneSignalStorage;
{% if features.image_resources %}
import ca.teamdman.sfm.common.capability.IImageHandler;
{% endif %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
{% else %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% endcase %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
{% when "1.20.2" %}
import net.neoforged.neoforge.common.capabilities.RegisterCapabilitiesEvent;
{% else %}
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
{% endcase %}

/// 1.19.2 -> RegisterCapabilitiesEvent event listener method
/// 1.20.3 -> BlockCapability.createSided static field
@MCVersionDependentBehaviour
public class SFMCapabilities {
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public static final SFMBlockCapabilityKind<IRedstoneSignalStorage> REDSTONE_HANDLER = new SFMBlockCapabilityKind<>(
            BlockCapability.createSided(
                    SFMResourceLocation.fromSFMPath("redstone_storage"),
                    IRedstoneSignalStorage.class
            ));
{% else %}
    @SFMSubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IRedstoneSignalStorage.class);
{% if features.image_resources %}
        event.register(IImageHandler.class);
{% endif %}
    }
{% endcase %}
}
