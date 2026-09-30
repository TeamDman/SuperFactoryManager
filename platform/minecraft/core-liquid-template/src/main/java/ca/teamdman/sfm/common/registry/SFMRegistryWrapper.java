package ca.teamdman.sfm.common.registry;


import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.core.HolderLookup;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
{% case minecraft_version %}
{% when '1.19.2' %}
import net.minecraft.data.BuiltinRegistries;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.core.registries.BuiltInRegistries;
{% when '26.1.2' %}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
{% endcase %}
import net.minecraft.resources.ResourceKey;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
{% when '26.1.2' %}
{% endcase %}
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/// Helps reduce {@link MCVersionDependentBehaviour}
@MCVersionDependentBehaviour
public final class SFMRegistryWrapper<T> implements Iterable<T> {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}

{% endcase %}
    private final ResourceKey<? extends Registry<T>> registryKey;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    private @Nullable IForgeRegistry<T> maybeInner;
    private @Nullable Registry<T> maybeInnerVanilla;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    @MCVersionDependentBehaviour
    private @Nullable Registry<T> maybeInner;
{% endcase %}

    public SFMRegistryWrapper(
            @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            IForgeRegistry<T> inner
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            Registry<T> inner
{% endcase %}
    ) {

        this.maybeInner = inner;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        this.registryKey = inner.getRegistryKey();
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        this.registryKey = inner.key();
{% endcase %}
    }

    public SFMRegistryWrapper(ResourceKey<? extends Registry<T>> registryKey) {

        this.maybeInner = null;
        this.registryKey = registryKey;
    }

    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public @Nullable T get(ResourceLocation resourceTypeId) {
{% when '26.1.2' %}
    public Optional<Holder.Reference<T>> get(Identifier resourceTypeId) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        return getInnerRegistry().getValue(resourceTypeId);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        return getInnerRegistry().get(resourceTypeId);
{% endcase %}
    }

    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public Set<ResourceLocation> keys() {
{% when '26.1.2' %}
    public Set<Identifier> keys() {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        return getInnerRegistry().getKeys();
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        return getInnerRegistry().keySet();
{% endcase %}
    }

    public Iterable<T> values() {

        return getInnerRegistry();
    }

    public Stream<T> stream() {

        return StreamSupport.stream(getInnerRegistry().spliterator(), false);
    }

    public Stream<Holder.Reference<T>> holders() {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        if (getVanillaRegistry() instanceof MappedRegistry<T> mappedRegistry) {
            return mappedRegistry.holders();
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (getInnerRegistry() instanceof MappedRegistry<T> mappedRegistry) {
            return mappedRegistry.holders();
{% when '26.1.2' %}
        if (getInnerRegistry() instanceof MappedRegistry<T> mappedRegistry) {
            return mappedRegistry.listElements();
{% endcase %}
        } else {
            return Stream.empty();
        }
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public @Nullable ResourceLocation getId(T value) {
{% when '26.1.2' %}
    public @Nullable Identifier getId(T value) {
{% endcase %}

        return getInnerRegistry().getKey(value);
    }

    public Optional<ResourceKey<T>> getKey(T value) {

        return getInnerRegistry().getResourceKey(value);
    }

    @MCVersionDependentBehaviour
    public Set<Map.Entry<ResourceKey<T>, T>> entries() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        return getInnerRegistry().getEntries();
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}

        return getInnerRegistry().entrySet();
{% endcase %}
    }

    @Override
    public Iterator<T> iterator() {

        return getInnerRegistry().iterator();
    }

    public ResourceKey<? extends Registry<T>> registryKey() {

        return registryKey;
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public boolean contains(ResourceLocation location) {
{% when '26.1.2' %}
    public boolean contains(Identifier location) {
{% endcase %}

        return getInnerRegistry().containsKey(location);
    }

    /// If this is for a registry not enabled during creation via {@link SFMDeferredRegisterBuilder}
    /// then this method will probably throw.
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public @MCVersionDependentBehaviour IForgeRegistry<T> getInnerRegistry() {
        if (maybeInner == null) {
            maybeInner = RegistryManager.ACTIVE.getRegistry(registryKey);
        }
        return maybeInner;
    }


    /// If this is for a registry not enabled during creation via {@link SFMDeferredRegisterBuilder}
    /// then this method will probably throw.
    public @MCVersionDependentBehaviour Registry<T> getVanillaRegistry() {
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public @MCVersionDependentBehaviour Registry<T> getInnerRegistry() {
{% endcase %}

        // Use cached value if present
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        if (maybeInnerVanilla != null) {
            return maybeInnerVanilla;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        if (maybeInner != null) {
            return maybeInner;
{% endcase %}
        }

        // Look up the registry in the registry of registries
        //noinspection unchecked,rawtypes
{% case minecraft_version %}
{% when '1.19.2' %}
        maybeInnerVanilla = (Registry<T>) BuiltinRegistries.REGISTRY.get((ResourceKey) registryKey);
        if (maybeInnerVanilla != null) {
            return maybeInnerVanilla;
{% when '1.19.4', '1.20', '1.20.1' %}
        maybeInnerVanilla = (Registry<T>) BuiltInRegistries.REGISTRY.get((ResourceKey) registryKey);
        if (maybeInnerVanilla != null) {
            return maybeInnerVanilla;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        maybeInner = (Registry<T>) BuiltInRegistries.REGISTRY.get((ResourceKey) registryKey);
        if (maybeInner != null) {
            return maybeInner;
{% when '26.1.2' %}
        BuiltInRegistries.REGISTRY.get((ResourceKey) registryKey).ifPresent((registry) -> {
            // noinspection unchecked
            Holder<Registry<T>> regHolder = ((Holder<Registry<T>>) registry);
            maybeInner = regHolder.value();
        });
        if (maybeInner != null) {
            return maybeInner;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        maybeInnerVanilla = level.registryAccess().registryOrThrow(registryKey);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        maybeInner = level.registryAccess().registryOrThrow(registryKey);
{% when '26.1.2' %}
        maybeInner = level.registryAccess().lookupOrThrow(registryKey);
{% endcase %}

        // Return it
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        return maybeInnerVanilla;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        return maybeInner;
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}

{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public HolderLookup.RegistryLookup<T> asHolderLookup() {
{% when '26.1.2' %}
    // TODO: this has 0 usages so maybe its not needed?
/*    public HolderLookup.RegistryLookup<T> asHolderLookup() {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2' %}
        return new HolderLookup.RegistryLookup<>(getVanillaRegistry());
    }
{% when '1.19.4', '1.20', '1.20.1' %}
        return getVanillaRegistry().asLookup();
    }
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return getInnerRegistry().asLookup();
    }
{% when '26.1.2' %}
        return getInnerRegistry().lookup();
    }*/
{% endcase %}

}
