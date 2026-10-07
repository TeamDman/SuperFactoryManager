package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
{% else %}
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.20.1", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
{% else %}
{% endcase %}
{% case minecraft_version %}
{% when "1.20.2", "1.20.3" %}
{% else %}
import ca.teamdman.sfm.common.compat.SFMModCompat;
{% endcase %}
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.resourcetype.*;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.core.Holder;
{% else %}
{% endcase %}
import net.minecraft.core.Registry;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
{% else %}
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.eventbus.api.IEventBus;
{% else %}
import net.neoforged.bus.api.IEventBus;
{% endcase %}
import org.jetbrains.annotations.Nullable;

{% case minecraft_version %}
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import java.util.stream.Stream;

{% else %}
{% endcase %}
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

{% if features.image_resources %}
    public static final SFMRegistryObject<ResourceType<?, ?, ?>, ImageResourceType> IMAGE
            = REGISTERER.register("image", ImageResourceType::new);

{% endif %}
{% case minecraft_version %}
{% when "26.1.2" %}
    private static final Object2ObjectOpenHashMap<Identifier, ResourceType<?, ?, ?>> DEFERRED_TYPES_BY_ID
{% else %}
    private static final Object2ObjectOpenHashMap<ResourceLocation, ResourceType<?, ?, ?>> DEFERRED_TYPES_BY_ID
{% endcase %}
            = new Object2ObjectOpenHashMap<>();

{% case minecraft_version %}
{% when "1.19.4", "1.20" %}
    static {
        if (SFMModCompat.isMekanismLoaded()) {
//            SFMMekanismCompat.registerResourceTypes(REGISTERER);
        }
    }
{% when "1.20.2", "1.20.3" %}
//    static {
//        if (SFMModCompat.isMekanismLoaded()) {
//            SFMMekanismCompat.registerResourceTypes(REGISTERER);
//        }
//    }
{% else %}
    static {
        if (SFMModCompat.isMekanismLoaded()) {
            SFMMekanismCompat.registerResourceTypes(REGISTERER);
        }
    }
{% endcase %}

    public static int getResourceTypeCount() {

        return REGISTERER.size();
    }

    public static @Nullable ResourceType<?, ?, ?> fastLookup(
{% case minecraft_version %}
{% when "26.1.2" %}
            Identifier resourceTypeId
{% else %}
            ResourceLocation resourceTypeId
{% endcase %}
    ) {

        return DEFERRED_TYPES_BY_ID.computeIfAbsent(
                resourceTypeId,
{% case minecraft_version %}
{% when "26.1.2" %}
                i -> registry().get(resourceTypeId).map(Holder.Reference::value).orElse(null)
{% else %}
                i -> registry().get(resourceTypeId)
{% endcase %}
        );
    }

{% case minecraft_version %}
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public static Stream<SFMBlockCapabilityKind<?>> getCapabilities() {
        return REGISTERER.registry()
                .stream()
                .map(resourceType -> resourceType.CAPABILITY_KIND);
    }

{% else %}
{% endcase %}
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
