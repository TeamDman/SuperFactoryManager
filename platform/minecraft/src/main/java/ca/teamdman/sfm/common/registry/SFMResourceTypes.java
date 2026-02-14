package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.resourcetype.*;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;

public class SFMResourceTypes {
    @SuppressWarnings("NotNullFieldNotInitialized") // set in initialize()
    public static ItemResourceType ITEM;
    @SuppressWarnings("NotNullFieldNotInitialized") // set in initialize()
    public static FluidResourceType FLUID;
    @SuppressWarnings("NotNullFieldNotInitialized") // set in initialize()
    public static ForgeEnergyResourceType FORGE_ENERGY;
    @SuppressWarnings("NotNullFieldNotInitialized") // set in initialize()
    public static RedstoneResourceType REDSTONE;

    public static void initialize() {
        ITEM = prepareRegister(ItemResourceType::new, "item");
        FLUID = prepareRegister(FluidResourceType::new, "fluid");
        FORGE_ENERGY = prepareRegister(ForgeEnergyResourceType::new, "forge_energy");
        REDSTONE = prepareRegister(RedstoneResourceType::new, "redstone");

        // if (SFMModCompat.isMekanismLoaded()) {
        //     SFMMekanismCompat.registerResourceTypes();
        // }
    }

    public interface ResourceTypeGenerator<T extends ResourceType<?, ?, ?>> {
        T generate(ResourceTypeContainer container);
    }

    private static <T extends ResourceType<?, ?, ?>> T prepareRegister(ResourceTypeGenerator<T> resourceType, String name) {
        var container = new ResourceTypeContainer() {
            @Nullable
            T resource;

            @Override
            public T get() {
                if (resource == null) {
                    resource = resourceType.generate(this);
                }
                return resource;
            }
        };
        container.setRegistryName(new ResourceLocation(SFM.RESOURCE_SHORT_ID, name));
        register(container);
        return container.get();
    }

    private static <T extends ResourceType<?, ?, ?>> T prepareRegister(ResourceTypeGenerator<T> resourceType,
                                                                       ResourceLocation name) {
        var container = new ResourceTypeContainer() {

            @Nullable
            T resource;

            @Override
            public T get() {
                if (resource == null) {
                    resource = resourceType.generate(this);
                }
                return resource;
            }
        };
        container.setRegistryName(name);
        register(container);
        return container.get();
    }

    private static <T extends ResourceTypeContainer> T register(T resourceType) {
        CommonProxy.registryPrimer.register(resourceType);
        return resourceType;
    }

    public static int getResourceTypeCount() {
        return registry().values().size();
    }

    public static @Nullable ResourceType<?, ?, ?> fastLookup(
            ResourceLocation resourceTypeId
    ) {
        ResourceTypeContainer container = registry().get(resourceTypeId);
        return container != null ? container.get() : null;
    }

    public static SFMRegistryWrapper<ResourceTypeContainer> registry() {
        return SFMWellKnownRegistries.RESOURCE_TYPES;
    }


    /* TODO: add support for new resource types
     * - botania mana
     * - flux plugs
     */
}