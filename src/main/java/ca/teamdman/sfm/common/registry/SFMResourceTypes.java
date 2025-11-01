package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.resourcetype.*;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import org.jetbrains.annotations.Nullable;

public class SFMResourceTypes {
    public static final ResourceKey<Registry<ResourceType<?, ?, ?>>> REGISTRY_ID
            = SFMResourceLocation.createSFMRegistryKey("resource_type");

    private static final SFMDeferredRegister<ResourceType<?, ?, ?>> REGISTERER =
            new SFMDeferredRegisterBuilder<ResourceType<?, ?, ?>>()
                    .namespace(SFM.MOD_ID)
                    .registry(REGISTRY_ID)
                    .createNewRegistry()
                    .build();

    public static final SFMRegistryObject<ResourceType<?, ?, ?>, ItemResourceType> ITEM
            = REGISTERER.register("item", ItemResourceType::new);

    public static final SFMRegistryObject<ResourceType<?, ?, ?>, FluidResourceType> FLUID
            = REGISTERER.register("fluid", FluidResourceType::new);

    public static final SFMRegistryObject<ResourceType<?, ?, ?>, ForgeEnergyResourceType> FORGE_ENERGY
            = REGISTERER.register("forge_energy", ForgeEnergyResourceType::new);

    public static final SFMRegistryObject<ResourceType<?, ?, ?>, RedstoneResourceType> REDSTONE
            = REGISTERER.register("redstone", RedstoneResourceType::new);

    private static final Object2ObjectOpenHashMap<ResourceLocation, ResourceType<?, ?, ?>> DEFERRED_TYPES_BY_ID
            = new Object2ObjectOpenHashMap<>();

    static {
        if (SFMModCompat.isMekanismLoaded()) {
//            SFMMekanismCompat.registerResourceTypes(REGISTERER);
        }
    }

    public static int getResourceTypeCount() {

        return REGISTERER.size();
    }

    public static @Nullable ResourceType<?, ?, ?> fastLookup(
            ResourceLocation resourceTypeId
    ) {

        return DEFERRED_TYPES_BY_ID.computeIfAbsent(
                resourceTypeId,
                i -> registry().get(resourceTypeId)
        );
    }

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

    public static SFMRegistryWrapper<ResourceType<?, ?, ?>> registry() {

        return REGISTERER.registry();
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
