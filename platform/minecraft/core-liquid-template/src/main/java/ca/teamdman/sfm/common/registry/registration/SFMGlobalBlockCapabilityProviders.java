package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.capability.BlockEntityCapabilityProvider;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import ca.teamdman.sfm.common.capability.CauldronBlockCapabilityProvider;
{% when '26.1.2' %}
//import ca.teamdman.sfm.common.capability.CauldronBlockCapabilityProvider;
{% endcase %}
import ca.teamdman.sfm.common.capability.RedstoneSignalCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProvider;
{% case minecraft_version %}
{% when '1.19.2', '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.common.capability.ae2.EnergyAcceptorBlockCapabilityProvider;
import ca.teamdman.sfm.common.compat.SFMModCompat;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
{% endcase %}
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.eventbus.api.IEventBus;
{% when '1.20.2' %}
import net.neoforged.bus.api.IEventBus;
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
{% endcase %}
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

/// This class is used in doc comments because it's a plural lol.
/// Check out {@link SFMBlockCapabilityProvider} for more information about what a Block Capability Provider is.
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
///
/// These are the {@link IBlockCapabilityProvider} that SFM registers to the world, which lets other mods see them.
{% endcase %}
@SuppressWarnings({"unused"})
public class SFMGlobalBlockCapabilityProviders {
    public static final ResourceKey<Registry<SFMBlockCapabilityProvider<?>>> REGISTRY_ID =
            SFMResourceLocation.createSFMRegistryKey("capability_provider_mappers");

{% case minecraft_version %}
{% when '1.19.2', '1.21.1', '26.1.2' %}
    /// Conditionally present
    public static final SFMRegistryObject<SFMBlockCapabilityProvider<?>, EnergyAcceptorBlockCapabilityProvider>
            AE2_ENERGY_ACCEPTOR;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
//    /// Conditionally present
//    public static final SFMRegistryObject<SFMBlockCapabilityProvider<?>, EnergyAcceptorBlockCapabilityProvider>
//            AE2_ENERGY_ACCEPTOR;
{% endcase %}

    private static final SFMDeferredRegister<SFMBlockCapabilityProvider<?>> REGISTERER =
            new SFMDeferredRegisterBuilder<SFMBlockCapabilityProvider<?>>()
                    .namespace(SFM.MOD_ID)
                    .registry(REGISTRY_ID)
                    .createNewRegistry()
                    .build();

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public static final SFMRegistryObject<SFMBlockCapabilityProvider<?>, CauldronBlockCapabilityProvider>
            CAULDRON_MAPPER = REGISTERER.register("cauldron", CauldronBlockCapabilityProvider::new);
{% when '26.1.2' %}
//    public static final SFMRegistryObject<SFMBlockCapabilityProvider<?>, CauldronBlockCapabilityProvider>
//            CAULDRON_MAPPER = REGISTERER.register("cauldron", CauldronBlockCapabilityProvider::new);
{% endcase %}

    public static final SFMRegistryObject<SFMBlockCapabilityProvider<?>, BlockEntityCapabilityProvider>
            BLOCK_ENTITY = REGISTERER.register("block_entity", BlockEntityCapabilityProvider::new);

    public static final SFMRegistryObject<SFMBlockCapabilityProvider<?>, RedstoneSignalCapabilityProvider>
            REDSTONE = REGISTERER.register("redstone", RedstoneSignalCapabilityProvider::new);

{% case minecraft_version %}
{% when '1.19.2', '1.21.1', '26.1.2' %}
    static {
        if (SFMModCompat.isAE2Loaded()) {

            AE2_ENERGY_ACCEPTOR = REGISTERER.register(
                    "ae2/energy_acceptor",
                    EnergyAcceptorBlockCapabilityProvider::new
            );

//            MAPPERS.register("ae2/interface", InterfaceCapabilityProvider::new);

        } else {

            AE2_ENERGY_ACCEPTOR = REGISTERER.registerEmpty(
                    "ae2/energy_acceptor"
            );

        }
    }
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
//    static {
//        if (SFMModCompat.isAE2Loaded()) {
//
//            AE2_ENERGY_ACCEPTOR = REGISTERER.register(
//                    "ae2/energy_acceptor",
//                    EnergyAcceptorBlockCapabilityProvider::new
//            );
//
////            MAPPERS.register("ae2/interface", InterfaceCapabilityProvider::new);
//
//        } else {
//
//            AE2_ENERGY_ACCEPTOR = REGISTERER.registerEmpty(
//                    "ae2/energy_acceptor"
//            );
//
//        }
//    }
{% endcase %}

    /// Gets all registered Block Capability Providers, sorted by priority (the highest priority first).
    public static ArrayList<SFMBlockCapabilityProvider<?>> getAllProviders() {

        return REGISTERER
                .registry()
                .stream()
                .sorted(Comparator
                                .comparingInt((SFMBlockCapabilityProvider<?> provider) -> provider.priority())
                                .reversed())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static SFMRegistryWrapper<SFMBlockCapabilityProvider<?>> registry() {

        return REGISTERER.registry();
    }

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

}
