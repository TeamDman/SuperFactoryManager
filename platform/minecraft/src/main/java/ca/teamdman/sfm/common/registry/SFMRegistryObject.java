package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Optional;
import java.util.function.Supplier;

/// A pointer to something that is registered in a registry.
/// Helps reduce {@link MCVersionDependentBehaviour}
@MCVersionDependentBehaviour
public class SFMRegistryObject<R, T extends R> implements Supplier<T> {
    /// The registry that this object is registered in
    private final ResourceKey<? extends Registry<T>> registryKey;

    /// This is null when this is an empty entry for a conditional registration in the not-enabled code path.
    private final @UnknownNullability RegistryObject<? extends T> inner;

    public SFMRegistryObject(
            ResourceKey<? extends Registry<T>> registryKey,
            @UnknownNullability RegistryObject<? extends T> object
    ) {
        this.registryKey = registryKey;
        this.inner = object;
    }

    public Optional<ResourceKey<T>> getId() {
        return getRegistry().getKey(get());
    }

    public @Nullable String getPath() {
        return getId().map(ResourceKey::location).map(ResourceLocation::getPath).orElse(null);
    }

    public SFMRegistryWrapper<T> getRegistry() {
        return new SFMRegistryWrapper<>(registryKey);
    }

    @Override
    public T get() {
        return inner.get();
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof SFMRegistryObject<?,?> that)) return false;

        return registryKey.equals(that.registryKey) && get().equals(that.get());
    }

    @Override
    public int hashCode() {
        int result = registryKey.hashCode();
        result = 31 * result + get().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SFMRegistryObject{" +
               "registryKey=" + registryKey +
               ", inner=" + inner +
               '}';
    }
}
