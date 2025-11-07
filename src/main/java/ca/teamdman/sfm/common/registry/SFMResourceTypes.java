package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.resourcetype.*;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

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
        ITEM = prepareRegister(new ItemResourceType(), "item");
        FLUID = prepareRegister(new FluidResourceType(), "fluid");
        FORGE_ENERGY = prepareRegister(new ForgeEnergyResourceType(), "forge_energy");
        REDSTONE = prepareRegister(new RedstoneResourceType(), "redstone");

        // if (SFMModCompat.isMekanismLoaded()) {
        //     SFMMekanismCompat.registerResourceTypes();
        // }
    }

    private static <T extends ResourceType<?, ?, ?>> T prepareRegister(T resourceType, String name) {
        var container = new ResourceTypeContainer() {
            @Override
            public ResourceType<?, ?, ?> get() {
                return resourceType;
            }
        };
        container.setRegistryName(new ResourceLocation(SFM.MOD_ID, name));
        register(container);
        return resourceType;
    }

    private static <T extends ResourceTypeContainer> T register(T resourceType) {
        CommonProxy.registryPrimer.register(resourceType);
        return resourceType;
    }

    public static int getResourceTypeCount() {
        return registry().getValues().size();
    }

    public static @Nullable ResourceType<?, ?, ?> fastLookup(
            ResourceLocation resourceTypeId
    ) {
        ResourceTypeContainer container = registry().getValue(resourceTypeId);
        return container != null ? container.get() : null;
    }

    public static IForgeRegistry<ResourceTypeContainer> registry() {
        return SFMRegistries.RESOURCE_TYPE_REGISTRY;
    }


    /* TODO: add support for new resource types
     * - botania mana
     * - flux plugs
     */
}