package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.workspace.SFMClientScreenType;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMClientScreenTypes {
    public static final ResourceKey<Registry<SFMClientScreenType>> REGISTRY_ID =
            SFMResourceLocation.createSFMRegistryKey("client_screen_type");

    private static final SFMDeferredRegister<SFMClientScreenType> REGISTRY_CREATOR =
            new SFMDeferredRegisterBuilder<SFMClientScreenType>()
                    .namespace(SFM.MOD_ID)
                    .registry(REGISTRY_ID)
                    .onlyIf(SFMEnvironmentUtils::isClient)
                    .createNewRegistry()
                    .build();

    private SFMClientScreenTypes() {
    }

    public static SFMDeferredRegister<SFMClientScreenType> createContributor(String namespace) {
        return new SFMDeferredRegisterBuilder<SFMClientScreenType>()
                .namespace(namespace)
                .registry(REGISTRY_ID)
                .onlyIf(SFMEnvironmentUtils::isClient)
                .build();
    }

    public static void register(IEventBus bus) {
        REGISTRY_CREATOR.register(bus);
    }

    public static SFMRegistryWrapper<SFMClientScreenType> registry() {
        return REGISTRY_CREATOR.registry();
    }
}
