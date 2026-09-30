package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.Registry;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.resources.Identifier;
{% endcase %}
import net.minecraft.resources.ResourceKey;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
{% when '26.1.2' %}
import net.neoforged.neoforge.registries.DeferredHolder;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    private final @UnknownNullability RegistryObject<? extends T> inner;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    /// Because disabled objects are
    private final @UnknownNullability DeferredHolder<R, T> inner;
{% endcase %}

    public SFMRegistryObject(
            ResourceKey<? extends Registry<T>> registryKey,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
            @UnknownNullability RegistryObject<? extends T> object
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            @UnknownNullability DeferredHolder<R, T> object
{% endcase %}
    ) {
        this.registryKey = registryKey;
        this.inner = object;
    }

    public Optional<ResourceKey<T>> getId() {
        return getRegistry().getKey(get());
    }

    public @Nullable String getPath() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return getId().map(ResourceKey::location).map(ResourceLocation::getPath).orElse(null);
{% when '26.1.2' %}
        return getId().map(ResourceKey::identifier).map(Identifier::getPath).orElse(null);
{% endcase %}
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
