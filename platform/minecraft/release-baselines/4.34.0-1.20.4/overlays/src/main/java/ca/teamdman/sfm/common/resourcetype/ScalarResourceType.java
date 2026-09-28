package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public abstract class ScalarResourceType<STACK, CAP> extends ResourceType<STACK, Class<STACK>, CAP> {
    public final ResourceLocation registryKey;
    public final Class<STACK> item;

    public ScalarResourceType(
            SFMBlockCapabilityKind<CAP> capability,
            ResourceLocation registryKey,
            Class<STACK> item
    ) {
        super(capability);
        this.registryKey = registryKey;
        this.item = item;
    }

    @Override
    public ResourceLocation getRegistryKeyForStack(STACK stack) {
        return registryKey;
    }

    @Override
    public ResourceLocation getRegistryKeyForItem(Class<STACK> item) {
        return registryKey;
    }

    @Override
    public @Nullable Class<STACK> getItemFromRegistryKey(ResourceLocation location) {
        if (location.equals(registryKey)) {
            return item;
        }
        return null;
    }

    @Override
    public Set<ResourceLocation> getRegistryKeys() {
        return Set.of(registryKey);
    }

    @Override
    public Iterable<Class<STACK>> getItems() {
        return List.of(item);
    }

    @Override
    public boolean registryKeyExists(ResourceLocation location) {
        return location.equals(registryKey);
    }

    @Override
    public Class<STACK> getItem(STACK stack) {
        return item;
    }

    @Override
    public boolean matchesStackType(Object o) {
        return item.isInstance(o);
    }
}
