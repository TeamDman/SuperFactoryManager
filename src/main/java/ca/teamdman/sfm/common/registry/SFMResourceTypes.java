package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.resourcetype.*;
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

    private static <T extends ResourceType<?, ?, ?>> T prepareRegister(T resourceType, String name) {
        resourceType.setRegistryName(new ResourceLocation(SFM.MOD_ID, name));
        return register(resourceType);
    }

    private static <T extends ResourceType<?, ?, ?>> T register(T resourceType) {
        CommonProxy.registryPrimer.register(resourceType);
        return resourceType;
    }

    public static int getResourceTypeCount() {
        return registry().getValues().size();
    }

    public static @Nullable ResourceType<?, ?, ?> fastLookup(
            ResourceLocation resourceTypeId
    ) {
        return registry().getValue(resourceTypeId);
    }

    public static IForgeRegistry<ResourceType<?,?,?>> registry() {
        return SFMRegistries.RESOURCE_TYPE_REGISTRY;
    }

    public static IForgeRegistry<What> what() {

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


class What<T> extends IForgeRegistryEntry.Impl<What<T>> {

}

