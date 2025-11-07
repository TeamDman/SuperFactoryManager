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

    public static ItemResourceType ITEM;
    public static FluidResourceType FLUID;
    public static ForgeEnergyResourceType FORGE_ENERGY;
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

    private static <T extends ResourceTypeContainer> T prepareRegister(T resourceType, String name) {
        resourceType.setRegistryName(new ResourceLocation(SFM.MOD_ID, name));
        return register(resourceType);
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
     * - mekanism heat
     * - botania mana
     * - ars nouveau source
     * - flux plugs
     * - PNC pressure
     * - PNC heat
     * - nature's aura aura
     * - create rotation
     */
}