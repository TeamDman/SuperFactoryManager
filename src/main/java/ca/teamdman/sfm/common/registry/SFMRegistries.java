package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProviderContainer;
import ca.teamdman.sfm.common.program.linting.IProgramLinter;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID)
public class SFMRegistries {

    public static final ResourceLocation RESOURCE_TYPE_REGISTRY_NAME = new ResourceLocation(SFM.MOD_ID, "resource_type");
    public static final ResourceLocation PROGRAM_LINTER_REGISTRY_NAME = new ResourceLocation(SFM.MOD_ID, "program_linter");
    public static final ResourceLocation GLOBAL_BLOCK_CAPABILITY_PROVIDER_REGISTRY_NAME = new ResourceLocation(SFM.MOD_ID, "capability_provider_mappers");

    public static IForgeRegistry<ResourceTypeContainer> RESOURCE_TYPE_REGISTRY;
    public static IForgeRegistry<IProgramLinter> PROGRAM_LINTER_REGISTRY;
    public static IForgeRegistry<SFMBlockCapabilityProviderContainer> GLOBAL_BLOCK_CAPABILITY_PROVIDER_REGISTRY;


    @SubscribeEvent
    public static void onNewRegistry(RegistryEvent.NewRegistry event) {
        RESOURCE_TYPE_REGISTRY = new RegistryBuilder<ResourceTypeContainer>()
                .setName(RESOURCE_TYPE_REGISTRY_NAME)
                .setType(ResourceTypeContainer.class)
                .create();

        SFMWellKnownRegistries.RESOURCE_TYPES = new SFMRegistryWrapper<>(RESOURCE_TYPE_REGISTRY);

        PROGRAM_LINTER_REGISTRY = new RegistryBuilder<IProgramLinter>()
                .setName(PROGRAM_LINTER_REGISTRY_NAME)
                .setType(IProgramLinter.class)
                .create();

        SFMWellKnownRegistries.PROGRAM_LINTERS = new SFMRegistryWrapper<>(PROGRAM_LINTER_REGISTRY);


        GLOBAL_BLOCK_CAPABILITY_PROVIDER_REGISTRY = new RegistryBuilder<SFMBlockCapabilityProviderContainer>()
                .setName(GLOBAL_BLOCK_CAPABILITY_PROVIDER_REGISTRY_NAME)
                .setType(SFMBlockCapabilityProviderContainer.class)
                .create();
    }
}
