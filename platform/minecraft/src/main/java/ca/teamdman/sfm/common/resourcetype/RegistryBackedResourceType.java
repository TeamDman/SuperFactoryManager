package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public abstract class RegistryBackedResourceType<STACK,ITEM,CAP> extends ResourceType<STACK,ITEM,CAP> {
    private final Map<ITEM, ResourceLocation> registryKeyCache = new Object2ObjectOpenHashMap<>();
    public RegistryBackedResourceType(SFMBlockCapabilityKind<CAP> CAPABILITY_KIND) {
        super(CAPABILITY_KIND);
    }


    @Override
    public ResourceLocation getRegistryKeyForStack(STACK stack) {
        ITEM item = getItem(stack);
        return getRegistryKeyForItem(item);
    }

    @Override
    public ResourceLocation getRegistryKeyForItem(ITEM item) {
        var found = registryKeyCache.get(item);
        if (found != null) return found;
        found = getRegistry().getId(item);
        if (found == null) {
            throw new NullPointerException("Registry key not found for item: " + item);
        }
        registryKeyCache.put(item, found);
        return found;
    }

    @Override
    public Set<ResourceLocation> getRegistryKeys() {
        return getRegistry().keys();
    }

    @Override
    public Iterable<ITEM> getItems() {
        return getRegistry().values();
    }

    public abstract SFMRegistryWrapper<ITEM> getRegistry();

    @Override
    public @Nullable ITEM getItemFromRegistryKey(ResourceLocation location) {
        return getRegistry().get(location);
    }

    @Override
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean registryKeyExists(ResourceLocation location) {
        return getRegistry().contains(location);
    }

}
