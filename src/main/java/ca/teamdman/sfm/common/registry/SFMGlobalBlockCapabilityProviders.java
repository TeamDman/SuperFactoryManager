package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.capability.*;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/// This class is used in doc comments because it's a plural lol.
/// Check out {@link SFMBlockCapabilityProvider} for more information about what a Block Capability Provider is.
@SuppressWarnings({"unused"})
public class SFMGlobalBlockCapabilityProviders {
    public static final CauldronBlockCapabilityProvider CAULDRON_MAPPER = new CauldronBlockCapabilityProvider();
    public static final BlockEntityCapabilityProvider BLOCK_ENTITY = new BlockEntityCapabilityProvider();
    public static final RedstoneSignalCapabilityProvider REDSTONE = new RedstoneSignalCapabilityProvider();


    public static void initialize() {
       prepareRegister("cauldron", CAULDRON_MAPPER);
       prepareRegister("block_entity", BLOCK_ENTITY);
       prepareRegister("redstone", REDSTONE);
    }

//    static {
//        if (SFMModCompat.isAE2Loaded()) {
//
//            AE2_ENERGY_ACCEPTOR_CAPABILITY_PROVIDER_MAPPER = REGISTERER.register(
//                    "ae2/energy_acceptor",
//                    EnergyAcceptorBlockCapabilityProvider::new
//            );
//
    ////            MAPPERS.register("ae2/interface", InterfaceCapabilityProvider::new);
//
//        } else {
//
//            AE2_ENERGY_ACCEPTOR_CAPABILITY_PROVIDER_MAPPER = REGISTERER.registerEmpty(
//                    "ae2/energy_acceptor"
//            );
//
//        }
//    }


    /// Gets all registered Block Capability Providers, sorted by priority (the highest priority first).
    public static List<SFMBlockCapabilityProvider<?>> getAllProviders() {

        return SFMRegistries.GLOBAL_BLOCK_CAPABILITY_PROVIDER_REGISTRY.getValuesCollection()
                .stream()
                .map(SFMBlockCapabilityProviderContainer::get)
                .sorted(Comparator
                                .comparingInt((SFMBlockCapabilityProvider<?> provider) -> provider.priority())
                                .reversed())
                .collect(Collectors.toList());
    }

    private static <T extends SFMBlockCapabilityProvider<?>> T prepareRegister(String name,  T provider) {
        SFMBlockCapabilityProviderContainer container = new SFMBlockCapabilityProviderContainer() {
            @Override
            public SFMBlockCapabilityProvider<?> get() {
                return provider;
            }
        };
        container.setRegistryName(new ResourceLocation(SFM.MOD_ID, name));//.setTranslationKey(SFM.MOD_ID + "." + name);
        register(container);
        return provider;
    }

    private static <T extends SFMBlockCapabilityProviderContainer> T register(T item) {
        CommonProxy.registryPrimer.register(item);
        return item;
    }
}
