package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public abstract class RegistryBackedResourceType<STACK,ITEM,CAP> extends ResourceType<STACK,ITEM,CAP> {
    private final Map<ITEM, Identifier> registryKeyCache = new Object2ObjectOpenHashMap<>();
    public RegistryBackedResourceType(SFMBlockCapabilityKind<CAP> CAPABILITY_KIND) {
        super(CAPABILITY_KIND);
    }


    @Override
    public Identifier getRegistryKeyForStack(STACK stack) {
        ITEM item = getItem(stack);
        return getRegistryKeyForItem(item);
    }

    @Override
    public Identifier getRegistryKeyForItem(ITEM item) {
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
    public Set<Identifier> getRegistryKeys() {
        return getRegistry().keys();
    }

    @Override
    public Iterable<ITEM> getItems() {
        return getRegistry().values();
    }

    public abstract SFMRegistryWrapper<ITEM> getRegistry();

    @Override
    public @Nullable ITEM getItemFromRegistryKey(Identifier identifier) {
        return getRegistry().get(identifier).get().value();
    }

    @Override
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean registryKeyExists(Identifier identifier) {
        return getRegistry().contains(identifier);
    }

}
