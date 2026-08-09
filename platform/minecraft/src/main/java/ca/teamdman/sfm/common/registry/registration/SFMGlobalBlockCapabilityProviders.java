package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.capability.BlockEntityCapabilityProvider;
import ca.teamdman.sfm.common.capability.CauldronBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.RedstoneSignalCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.ae2.EnergyAcceptorBlockCapabilityProvider;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

/// This class is used in doc comments because it's a plural lol.
/// Check out {@link SFMBlockCapabilityProvider} for more information about what a Block Capability Provider is.
@SuppressWarnings({"unused"})
public class SFMGlobalBlockCapabilityProviders {
    public static final ResourceKey<Registry<SFMBlockCapabilityProvider<?>>> REGISTRY_ID =
            SFMResourceLocation.createSFMRegistryKey("capability_provider_mappers");

    /// Conditionally present
    public static final SFMRegistryObject<SFMBlockCapabilityProvider<?>, EnergyAcceptorBlockCapabilityProvider>
            AE2_ENERGY_ACCEPTOR;

    private static final SFMDeferredRegister<SFMBlockCapabilityProvider<?>> REGISTERER =
            new SFMDeferredRegisterBuilder<SFMBlockCapabilityProvider<?>>()
                    .namespace(SFM.MOD_ID)
                    .registry(REGISTRY_ID)
                    .createNewRegistry()
                    .build();

    public static final SFMRegistryObject<SFMBlockCapabilityProvider<?>, CauldronBlockCapabilityProvider>
            CAULDRON_MAPPER = REGISTERER.register("cauldron", CauldronBlockCapabilityProvider::new);

    public static final SFMRegistryObject<SFMBlockCapabilityProvider<?>, BlockEntityCapabilityProvider>
            BLOCK_ENTITY = REGISTERER.register("block_entity", BlockEntityCapabilityProvider::new);

    public static final SFMRegistryObject<SFMBlockCapabilityProvider<?>, RedstoneSignalCapabilityProvider>
            REDSTONE = REGISTERER.register("redstone", RedstoneSignalCapabilityProvider::new);

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
