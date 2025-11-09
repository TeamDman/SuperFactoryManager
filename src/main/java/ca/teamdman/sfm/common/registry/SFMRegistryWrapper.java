package ca.teamdman.sfm.common.registry;


import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.google.common.reflect.TypeToken;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.registries.RegistryManager;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/// Helps reduce {@link MCVersionDependentBehaviour}
@MCVersionDependentBehaviour
public final class SFMRegistryWrapper<T extends IForgeRegistryEntry<T>> implements Iterable<T> {
    private @Nullable IForgeRegistry<T> maybeInner;
    private TypeToken<T> token = new TypeToken<T>(getClass()){};
    private final Class<T> registryKey;

    public SFMRegistryWrapper(
            @MCVersionDependentBehaviour
            IForgeRegistry<T> inner
    ) {
        this.maybeInner = inner;
        this.registryKey = (Class<T>)token.getRawType();
    }

    public SFMRegistryWrapper(Class<T> registryKey) {
        this.registryKey = registryKey;
        this.maybeInner = null;
    }

    @MCVersionDependentBehaviour
    public @Nullable T get(ResourceLocation resourceTypeId) {
        return getInnerRegistry().getValue(resourceTypeId);
    }

    @MCVersionDependentBehaviour
    public Set<ResourceLocation> keys() {
        return getInnerRegistry().getKeys();
    }

    public Iterable<T> values() {
        return getInnerRegistry();
    }

    public Stream<T> stream() {
        return StreamSupport.stream(getInnerRegistry().spliterator(), false);
    }

    public @Nullable ResourceLocation getId(T value) {
        return getInnerRegistry().getKey(value);
    }

    public Optional<ResourceLocation> getKey(T value) {
        return Optional.ofNullable(getInnerRegistry().getKey(value));
    }

    @MCVersionDependentBehaviour
    public Set<Map.Entry<ResourceLocation, T>> entries() {
        return getInnerRegistry().getEntries();
    }

    @Override
    public Iterator<T> iterator() {
        return getInnerRegistry().iterator();
    }

    public boolean contains(ResourceLocation location) {
        return getInnerRegistry().containsKey(location);
    }

    /// If this is for a registry not enabled during creation
    /// then this method will probably throw.
    public @MCVersionDependentBehaviour IForgeRegistry<T> getInnerRegistry() {
        if (maybeInner == null) {
            maybeInner = RegistryManager.ACTIVE.getRegistry(registryKey);
        }
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

}
