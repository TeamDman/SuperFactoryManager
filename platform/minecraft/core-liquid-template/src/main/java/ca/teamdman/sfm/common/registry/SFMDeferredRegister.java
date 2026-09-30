package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
{% endcase %}
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import java.util.function.Function;
{% endcase %}
import java.util.function.Supplier;

/// The thing that registers stuff to a registry.
/// Helps reduce {@link MCVersionDependentBehaviour}.
/// Can be acquired using {@link SFMDeferredRegisterBuilder}
@MCVersionDependentBehaviour
public class SFMDeferredRegister<T> {
    /// The registry that this is registering to
    private final ResourceKey<? extends Registry<T>> registryKey;

    /// The internal registration helper
    @MCVersionDependentBehaviour
    private final @Nullable DeferredRegister<T> inner;

    /// The mod that we are registering stuff for.
    private final String namespace;

    /// The registry we are registering to
    private final SFMRegistryWrapper<T> registryWrapper;

    public SFMDeferredRegister(
            ResourceKey<? extends Registry<T>> registryKey,
            String namespace,
            @Nullable DeferredRegister<T> inner
    ) {

        this.registryKey = registryKey;
        this.registryWrapper = new SFMRegistryWrapper<>(registryKey);
        this.namespace = namespace;
        this.inner = inner;
    }

    public void register(IEventBus bus) {

        if (inner == null) return;
        inner.register(bus);
    }

    public int size() {

        if (inner == null) {
            throw new IllegalStateException("Tried to get size of conditionally disabled SFMDeferredRegister " + this);
        }
        return inner.getEntries().size();
    }

    public String namespace() {

        return namespace;
    }

    @SuppressWarnings({"unchecked", "Convert2Diamond"})
    public <I extends T> SFMRegistryObject<T, I> register(
            String name,
            Supplier<? extends I> supplier
    ) {

        if (inner == null) {
            return registerEmpty(name);
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        RegistryObject<? extends I> object = inner.register(name, supplier);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        DeferredHolder<T, I> object = inner.register(name, supplier);
{% endcase %}
        return new SFMRegistryObject<T, I>(
                (ResourceKey<? extends Registry<I>>) registryKey,
                object
        );
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    @SuppressWarnings({"unchecked", "Convert2Diamond"})
    public <I extends T> SFMRegistryObject<T, I> register(
            String name,
            Function<Identifier, ? extends I> factory
    ) {

        if (inner == null) {
            return registerEmpty(name);
        }
        DeferredHolder<T, I> object = inner.register(name, factory);
        return new SFMRegistryObject<T, I>(
                (ResourceKey<? extends Registry<I>>) registryKey,
                object
        );
    }

{% endcase %}
    /// Used because we have static fields that expect values.
    /// This returns a registry object that should never be used beyond field initialization.
    @SuppressWarnings({"unchecked", "Convert2Diamond", "unused"})
    public <I extends T> SFMRegistryObject<T, I> registerEmpty(
            String name
    ) {
        return new SFMRegistryObject<T, I>(
                (ResourceKey<? extends Registry<I>>) registryKey,
                null
        ) {
            @Override
            public I get() {

                throw new IllegalStateException(
                        "Tried to get a registry object that was conditionally not registered - "
                        + registryKey
                        + " "
                        + namespace()
                        + ":"
                        + name);
            }
        };
    }

    public SFMRegistryWrapper<T> registry() {

        return registryWrapper;
    }

    /// Returns the things registered by this instance.
    /// Not to be confused with getting all entries in the entire registry.
    public ArrayList<SFMRegistryObject<T, ? extends T>> getOurEntries() {

        if (inner == null) {
            SFM.LOGGER.warn(
                    "Attempted to get entries from a registry that was not created via DeferredRegisterBuilder! namespace={}",
                    this.namespace()
            );
            return new ArrayList<>();
        }
        var entries = inner.getEntries();
        ArrayList<SFMRegistryObject<T, ? extends T>> rtn = new ArrayList<>(entries.size());
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        for (RegistryObject<T> entry : entries) {
            rtn.add(new SFMRegistryObject<>(registryKey, entry));
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        for (DeferredHolder<T, ? extends T> entry : entries) {
            if (entry.isBound()) {
                //noinspection rawtypes,unchecked
                rtn.add(new SFMRegistryObject<>((ResourceKey) registryKey, entry));
            }
{% endcase %}
        }
        return rtn;
    }

    @Override
    public String toString() {

        return "SFMDeferredRegister{" +
               "registryKey=" + registryKey +
               ", inner=" + inner +
               ", namespace='" + namespace + '\'' +
               ", registryWrapper=" + registryWrapper +
               '}';
    }

}
