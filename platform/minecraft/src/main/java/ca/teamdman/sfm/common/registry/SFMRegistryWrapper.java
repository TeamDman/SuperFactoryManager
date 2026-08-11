package ca.teamdman.sfm.common.registry;


import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/// Helps reduce {@link MCVersionDependentBehaviour}
@MCVersionDependentBehaviour
public final class SFMRegistryWrapper<T> implements Iterable<T> {

    private final ResourceKey<? extends Registry<T>> registryKey;

    @MCVersionDependentBehaviour
    private @Nullable Registry<T> maybeInner;

    public SFMRegistryWrapper(
            @MCVersionDependentBehaviour
            Registry<T> inner
    ) {

        this.maybeInner = inner;
        this.registryKey = inner.key();
    }

    public SFMRegistryWrapper(ResourceKey<? extends Registry<T>> registryKey) {

        this.maybeInner = null;
        this.registryKey = registryKey;
    }

    @MCVersionDependentBehaviour
    public @Nullable T get(ResourceLocation resourceTypeId) {

        return getInnerRegistry().get(resourceTypeId);
    }

    @MCVersionDependentBehaviour
    public Set<ResourceLocation> keys() {

        return getInnerRegistry().keySet();
    }

    public Iterable<T> values() {

        return getInnerRegistry();
    }

    public Stream<T> stream() {

        return StreamSupport.stream(getInnerRegistry().spliterator(), false);
    }

    public Stream<Holder.Reference<T>> holders() {

        if (getInnerRegistry() instanceof MappedRegistry<T> mappedRegistry) {
            return mappedRegistry.holders();
        } else {
            return Stream.empty();
        }
    }

    public @Nullable ResourceLocation getId(T value) {

        return getInnerRegistry().getKey(value);
    }

    public Optional<ResourceKey<T>> getKey(T value) {

        return getInnerRegistry().getResourceKey(value);
    }

    @MCVersionDependentBehaviour
    public Set<Map.Entry<ResourceKey<T>, T>> entries() {

        return getInnerRegistry().entrySet();
    }

    @Override
    public Iterator<T> iterator() {

        return getInnerRegistry().iterator();
    }

    public ResourceKey<? extends Registry<T>> registryKey() {

        return registryKey;
    }

    public boolean contains(ResourceLocation location) {

        return getInnerRegistry().containsKey(location);
    }

    /// If this is for a registry not enabled during creation via {@link SFMDeferredRegisterBuilder}
    /// then this method will probably throw.
    public @MCVersionDependentBehaviour Registry<T> getInnerRegistry() {

        // Use cached value if present
        if (maybeInner != null) {
            return maybeInner;
        }

        // Look up the registry in the registry of registries
        //noinspection unchecked,rawtypes
        maybeInner = (Registry<T>) BuiltInRegistries.REGISTRY.get((ResourceKey) registryKey);
        if (maybeInner != null) {
            return maybeInner;
        }

        // Couldn't find it, we can only proceed if we are on the client
        if (!SFMEnvironmentUtils.isClient()) {
            throw new IllegalStateException("Failed to acquire registry " + registryKey + " - not present in the registry registry, and we aren't on the client");
        }

        // Grab the level from the client
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            throw new IllegalStateException("Failed to acquire registry " + registryKey + " - client level is null?");
        }

        // Grab the registry from the client registry access and cache it
        maybeInner = level.registryAccess().registryOrThrow(registryKey);

        // Return it
        return maybeInner;
    }


    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object obj) {

        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (SFMRegistryWrapper) obj;
        return Objects.equals(this.getInnerRegistry(), that.getInnerRegistry());
    }

    @Override
    public int hashCode() {

        return Objects.hash(getInnerRegistry());
    }

    @Override
    public String toString() {

        return "SFMRegistryWrapper[" +
               "inner=" + maybeInner + ']';
    }

    public HolderLookup.RegistryLookup<T> asHolderLookup() {

        return getInnerRegistry().asLookup();
    }

}
